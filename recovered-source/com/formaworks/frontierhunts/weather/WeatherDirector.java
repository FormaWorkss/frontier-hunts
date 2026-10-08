package com.formaworks.frontierhunts.weather;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.Wilderness;
import com.formaworks.frontierhunts.season.SeasonClock;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

/**
 * Server-authoritative seasonal weather director.
 *
 * Every weather-capable level (sky light, no ceiling) keeps a short list of regional {@link Storm}s. Around each player
 * the director rolls, once per weather "episode" and 640-block cell, whether a region-appropriate event happens:
 * <ul>
 *   <li>vanilla snow in a snowy region (winter widens it to taiga/high ridges; frozen peaks all year) -> blizzard, likely
 *   when the vanilla weather turns to thunder, only occasionally from quiet snowfall ([shelter])</li>
 *   <li>vanilla thunder over rain -> thunderstorm (summer/spring), plain rain -> short squalls (spring/fall)</li>
 *   <li>deserts/badlands -> dust storms (summer, and when a weather front passes)</li>
 *   <li>valleys/rivers/wetlands in the small hours -> morning fog (freezing fog in winter lowlands)</li>
 *   <li>daytime gales -> wind storms (mostly fall)</li>
 * </ul>
 * Rolls are deterministic from the world seed, so a region's weather does not change by relogging. Storms drift with
 * the shared wilderness wind, ease out early when the vanilla weather they need ends, and are synced to nearby players
 * every two seconds (a few dozen bytes). Nothing here runs per entity or per block.
 */
@EventBusSubscriber(modid = FrontierHunts.ID)
public final class WeatherDirector {
    static final Logger LOG = LogUtils.getLogger();
    static final int CELL = 640;
    static final int MAX_STORMS = 16;
    static final double SYNC_MARGIN = 320.0;

    static final class LevelState {
        final List<Storm> storms = new ArrayList<>();
        final List<Storm> view = Collections.unmodifiableList(storms);
        final LongOpenHashSet rolled = new LongOpenHashSet();
        final Long2LongOpenHashMap bedCache = new Long2LongOpenHashMap();
        long rainEpisode = Long.MIN_VALUE, thunderEpisode = Long.MIN_VALUE;
        boolean wasRaining, wasThundering;
        long calmUntil;
        int nextId = 1;
        boolean dirty;
    }

    private static final Map<ResourceKey<Level>, LevelState> STATES = new HashMap<>();
    private static int ticks;

    private WeatherDirector() {}

    static boolean weatherLevel(Level level) {
        return level != null && level.dimensionType().hasSkyLight() && !level.dimensionType().hasCeiling()
            && !com.formaworks.frontierhunts.academy.Academy.weatherBlocked(level); // [academy] no storms in the training grounds
    }

    static LevelState state(ServerLevel level) {
        return STATES.computeIfAbsent(level.dimension(), k -> new LevelState());
    }

    static List<Storm> storms(ServerLevel level) {
        LevelState st = STATES.get(level.dimension());
        return st == null ? List.of() : st.view;
    }

    static float windEast(Level level) {
        return level instanceof ServerLevel sl ? (float) wind(sl).east() : 0f;
    }

    static float windSouth(Level level) {
        return level instanceof ServerLevel sl ? (float) wind(sl).south() : 0f;
    }

    static Wilderness.Wind wind(ServerLevel l) {
        return Wilderness.wind(l.getSeed(), l.getGameTime(), l.isRaining(), l.isThundering());
    }

    // ------------------------------------------------------------------ events
    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        int tk = ++ticks;
        boolean on = WeatherConfig.enabled();
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (!weatherLevel(level)) continue;
            LevelState st = STATES.get(level.dimension());
            if (st == null) {
                if (!on || level.players().isEmpty()) continue;
                st = state(level);
            }
            long now = level.getGameTime();
            if (tk % 20 == 0) maintain(level, st, now, on);
            if (on && tk % 100 == 37) {
                List<ServerPlayer> players = level.players();
                for (int i = 0; i < players.size(); i++) {
                    ServerPlayer p = players.get(i);
                    if (!p.isSpectator()) consider(level, st, p, now);
                }
            }
            if (st.dirty || tk % 40 == 0) {
                st.dirty = false;
                sync(level, st, now);
            }
        }
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp && sp.level() instanceof ServerLevel sl) sendTo(sp, sl);
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp && sp.level() instanceof ServerLevel sl) sendTo(sp, sl);
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp && sp.level() instanceof ServerLevel sl) sendTo(sp, sl);
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel sl) STATES.remove(sl.dimension());
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        STATES.clear();
    }

    // ------------------------------------------------------------------ upkeep
    private static void maintain(ServerLevel level, LevelState st, long now, boolean on) {
        boolean raining = level.isRaining(), thunder = level.isThundering();
        if (raining && (!st.wasRaining || st.rainEpisode == Long.MIN_VALUE)) st.rainEpisode = now;
        if (thunder && (!st.wasThundering || st.thunderEpisode == Long.MIN_VALUE)) st.thunderEpisode = now;
        st.wasRaining = raining;
        st.wasThundering = thunder;
        for (Iterator<Storm> it = st.storms.iterator(); it.hasNext(); ) {
            Storm s = it.next();
            if (s.expired(now)) {
                it.remove();
                st.dirty = true;
                continue;
            }
            if (!on && !s.forced) {
                st.dirty |= s.easeOut(now, 600);
                continue;
            }
            if (now < s.start + 200) continue; // let vanilla rain ramp in after a forced event
            if ((s.kind == WeatherKind.BLIZZARD || s.kind == WeatherKind.SQUALL) && !raining) {
                st.dirty |= s.easeOut(now, s.kind == WeatherKind.SQUALL ? 600 : 1600);
            } else if (s.kind == WeatherKind.THUNDERSTORM && !thunder) {
                st.dirty |= s.easeOut(now, raining ? 1400 : 900);
            }
        }
        if (st.rolled.size() > 20000) st.rolled.clear();
        if (st.bedCache.size() > 4096) st.bedCache.clear();
    }

    private static boolean covered(LevelState st, WeatherKind.Family family, long now, double x, double z) {
        for (Storm s : st.storms) {
            if (s.kind.family == family && !s.easing(now) && s.covers(now, x, z, -s.radius * 0.25)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ natural events
    private static void consider(ServerLevel level, LevelState st, ServerPlayer player, long now) {
        if (now < st.calmUntil || st.storms.size() >= MAX_STORMS) return;
        float freq = WeatherConfig.frequency();
        if (freq <= 0f) return;
        BlockPos pos = player.blockPosition();
        if (!level.hasChunkAt(pos)) return;
        double x = player.getX(), z = player.getZ();
        Climate c = new Climate().sample(level, pos);
        float w = SeasonClock.winterness(level);
        SeasonClock.Season season = SeasonClock.season(level);
        int cx = Math.floorDiv(pos.getX(), CELL), cz = Math.floorDiv(pos.getZ(), CELL);
        long day = now / 24000L;
        long tod = Math.floorMod(level.getDayTime(), 24000L);
        boolean raining = level.isRaining();

        // precipitation family: blizzards, thunderstorms, squalls
        if (raining && st.rainEpisode != Long.MIN_VALUE && !covered(st, WeatherKind.Family.PRECIPITATION, now, x, z)) {
            if (c.precip == Biome.Precipitation.SNOW && (c.snowyRegion(w) || c.deepCold())) {
                // [shelter] a blizzard is a storm, not every snowfall: when the vanilla weather turns stormy (thunder) a
                // blizzard is likely (winter: snowy 85%, high snowy mountains 95%); quiet snowfall only now and then
                // becomes one (winter: snowy 22%, high snowy mountains 35%). Was 65% / 87% of every winter snowfall
                // ([blizzard2]), so most snowy winter days turned into whiteouts.
                if (level.isThundering() && st.thunderEpisode != Long.MIN_VALUE) {
                    float ch = c.highSnow() ? 0.6f + 0.35f * w : 0.45f + 0.4f * w;
                    if (c.deepCold()) ch = Math.max(ch, 0.7f);
                    roll(level, st, player, c, WeatherKind.BLIZZARD, st.thunderEpisode, 1, cx, cz, ch * freq, now);
                } else {
                    float ch = c.highSnow() ? 0.12f + 0.23f * w : 0.06f + 0.16f * w;
                    if (c.deepCold()) ch = Math.max(ch, c.highSnow() ? 0.2f : 0.12f);
                    roll(level, st, player, c, WeatherKind.BLIZZARD, st.rainEpisode, 0, cx, cz, ch * freq, now);
                }
            } else if (c.precip == Biome.Precipitation.RAIN) {
                if (level.isThundering() && st.thunderEpisode != Long.MIN_VALUE) {
                    float ch = switch (season) { case SUMMER -> 0.85f; case SPRING -> 0.7f; case FALL -> 0.45f; case WINTER -> 0.15f; };
                    roll(level, st, player, c, WeatherKind.THUNDERSTORM, st.thunderEpisode, 0, cx, cz, ch * freq, now);
                } else {
                    float ch = switch (season) { case SPRING -> 0.28f; case FALL -> 0.24f; case SUMMER -> 0.18f; case WINTER -> 0.08f; };
                    int slot = (int) Math.max(0L, (now - st.rainEpisode) / 4800L);
                    roll(level, st, player, c, WeatherKind.SQUALL, st.rainEpisode, slot, cx, cz, ch * freq, now);
                }
            }
        }
        // dry family: dust storms (a passing front over the desert makes them far more likely)
        if (c.dryness > 0.3f && !covered(st, WeatherKind.Family.DRY, now, x, z)) {
            float ch = switch (season) { case SUMMER -> 0.4f; case SPRING -> 0.2f; case FALL -> 0.15f; case WINTER -> 0.06f; };
            if (raining) ch += 0.35f;
            roll(level, st, player, c, WeatherKind.DUST_STORM, day * 2 + (raining ? 1 : 0), 0, cx, cz, ch * c.dryness * freq, now);
        }
        // fog family: forms in the small hours, burns off in the morning
        if ((tod >= 20500 || tod < 1200) && c.dryness < 0.9f && !c.ocean && !covered(st, WeatherKind.Family.FOG, now, x, z)) {
            boolean freezing = season == SeasonClock.Season.WINTER;
            if (!(freezing && c.snowyRegion(w))) {
                // [1.1.8] about half as often: a foggy morning is an event, not most mornings
                float base = switch (season) { case FALL -> 0.3f; case SPRING -> 0.22f; case WINTER -> 0.18f; case SUMMER -> 0.08f; };
                float site = c.wet ? 1f : valley(level, pos) > 5 ? 0.7f : c.forest ? 0.35f : 0.15f;
                if (raining) base *= 0.5f;
                long morning = day + (tod >= 20500 ? 1 : 0);
                roll(level, st, player, c, freezing ? WeatherKind.FREEZING_FOG : WeatherKind.FOG, morning, 0, cx, cz, base * site * freq, now);
            }
        }
        // wind family: daytime gales
        if (tod < 12500 && c.dryness < 0.9f && !covered(st, WeatherKind.Family.WIND, now, x, z)) {
            float ch = switch (season) { case FALL -> 0.35f; case SPRING -> 0.22f; case WINTER -> 0.15f; case SUMMER -> 0.1f; };
            roll(level, st, player, c, WeatherKind.WIND_STORM, day, 0, cx, cz, ch * freq, now);
        }
    }

    private static void roll(ServerLevel level, LevelState st, ServerPlayer player, Climate c, WeatherKind kind, long episode, int slot,
                             int cx, int cz, float chance, long now) {
        long key = mix(mix(mix(mix(kind.ordinal() * 0x632BE59BD9B4E019L + episode) + slot * 0x85157AF5L) + cx) * 31L + cz);
        if (!st.rolled.add(key)) return;
        Dice d = new Dice(level.getSeed() ^ key);
        if (d.next() >= chance) return;
        spawn(level, st, kind, player.getX(), player.getZ(), player.blockPosition(), c, d, now, false, -1);
    }

    /** Deterministic parameter source for one storm. */
    static final class Dice {
        long s;
        Dice(long seed) { s = seed; }
        double next() { s += 0x9E3779B97F4A7C15L; return (mix(s) >>> 11) * 0x1.0p-53; }
        int range(int lo, int hi) { return lo + (int) Math.floor(next() * (hi - lo + 1)); }
        float rangeF(float lo, float hi) { return lo + (float) next() * (hi - lo); }
    }

    static Storm spawn(ServerLevel level, LevelState st, WeatherKind kind, double px, double pz, BlockPos pos, Climate c, Dice d, long now,
                       boolean forced, int forcedTicks) {
        Wilderness.Wind wind = wind(level);
        double ws = wind.speed();
        double ux = ws < 0.01 ? 1.0 : wind.east() / ws, uz = ws < 0.01 ? 0.0 : wind.south() / ws;
        boolean high = c != null && c.highSnow();
        int rampIn, hold, rampOut;
        float radius, peak, driftScale;
        switch (kind) {
            case BLIZZARD -> {
                // [blizzard2] the peak arrives faster (25-45 s) and holds longer, longest on high snowy ground in deep winter
                float wn = SeasonClock.winterness(level);
                rampIn = d.range(500, 900);
                hold = (int) (d.range(4800, 10800) * (high ? 1.6 : 1.0) * (1.0 + 0.3 * wn));
                rampOut = d.range(1200, 2400);
                radius = d.rangeF(400, 640) * (high ? 1.15f : 1f); peak = high ? 1f : d.rangeF(0.95f, 1f); driftScale = 0.004f;
            }
            case THUNDERSTORM -> {
                rampIn = d.range(600, 1000); hold = d.range(2400, 6000); rampOut = d.range(900, 1500);
                radius = d.rangeF(260, 420); peak = d.rangeF(0.75f, 1f); driftScale = 0.006f;
            }
            case SQUALL -> {
                rampIn = d.range(300, 500); hold = d.range(1200, 3000); rampOut = d.range(500, 900);
                radius = d.rangeF(200, 320); peak = d.rangeF(0.7f, 1f); driftScale = 0.008f;
            }
            case DUST_STORM -> {
                rampIn = d.range(900, 1600); hold = d.range(3000, 7200); rampOut = d.range(1200, 2400);
                radius = d.rangeF(320, 520); peak = d.rangeF(0.75f, 1f); driftScale = 0.005f;
            }
            case FOG, FREEZING_FOG -> {
                rampIn = d.range(900, 1800);
                long tod = Math.floorMod(level.getDayTime(), 24000L);
                long burn = 3000 + d.range(0, 2500);
                long until = Math.floorMod(burn - tod, 24000L);
                hold = (int) Math.max(1200L, until - rampIn);
                rampOut = d.range(1500, 2500);
                radius = d.rangeF(220, 360); peak = d.rangeF(0.7f, 1f); driftScale = 0.0015f;
            }
            case WIND_STORM -> {
                rampIn = d.range(600, 1200); hold = d.range(2400, 6000); rampOut = d.range(900, 1800);
                radius = d.rangeF(380, 600); peak = d.rangeF(0.7f, 1f); driftScale = 0.006f;
            }
            default -> { return null; }
        }
        double cxw, czw;
        long start;
        if (forced) {
            int total = Math.max(200, forcedTicks);
            // [blizzard2] forced events reach full strength quickly (blizzard <= 12 s) and hold it
            rampIn = kind == WeatherKind.BLIZZARD ? Math.min(240, total / 8) : Math.min(400, total / 6);
            rampOut = Math.min(600, total / 5);
            hold = Math.max(0, total - rampIn - rampOut);
            radius = Math.max(radius, 480f);
            peak = 1f;
            driftScale = 0f;
            cxw = px;
            czw = pz;
            start = now;
        } else {
            peak = Math.max(0.05f, Math.min(1f, peak * WeatherConfig.intensity()));
            cxw = px - ux * radius * 0.3 + (d.next() - 0.5) * radius * 0.3;
            czw = pz - uz * radius * 0.3 + (d.next() - 0.5) * radius * 0.3;
            start = now + d.range(0, 400);
        }
        float top = Float.NaN;
        if (kind == WeatherKind.FOG || kind == WeatherKind.FREEZING_FOG) top = valleyFloor(level, pos) + (forced ? 18f : d.rangeF(10f, 22f));
        Storm s = new Storm(st.nextId++, kind, cxw, czw, (float) (wind.east() * driftScale), (float) (wind.south() * driftScale), radius, start,
                rampIn, hold, rampOut, peak, forced, top);
        st.storms.add(s);
        st.dirty = true;
        LOG.debug("Frontier weather: {} #{} at {},{} r={} peak={} for {} ticks{}", kind.id, s.id, (int) cxw, (int) czw, (int) radius, peak,
                s.naturalEnd() - start, forced ? " (forced)" : "");
        return s;
    }

    // ------------------------------------------------------------------ terrain helpers (loaded chunks only)
    private static int surface(ServerLevel level, int x, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) return Integer.MIN_VALUE;
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    /** How far (blocks) the surroundings rise above this spot on average: > 5 means a valley or hollow. */
    static float valley(ServerLevel level, BlockPos pos) {
        int h0 = surface(level, pos.getX(), pos.getZ());
        if (h0 == Integer.MIN_VALUE) return 0f;
        float sum = 0f;
        int n = 0;
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0;
            int h = surface(level, pos.getX() + (int) Math.round(Math.cos(a) * 48), pos.getZ() + (int) Math.round(Math.sin(a) * 48));
            if (h == Integer.MIN_VALUE) continue;
            sum += h;
            n++;
        }
        return n == 0 ? 0f : sum / n - h0;
    }

    /** The lowest ground near this spot; fog pools from here up to the fog ceiling. */
    static float valleyFloor(ServerLevel level, BlockPos pos) {
        int h0 = surface(level, pos.getX(), pos.getZ());
        if (h0 == Integer.MIN_VALUE) h0 = pos.getY();
        int low = h0;
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0;
            int h = surface(level, pos.getX() + (int) Math.round(Math.cos(a) * 24), pos.getZ() + (int) Math.round(Math.sin(a) * 24));
            if (h != Integer.MIN_VALUE) low = Math.min(low, h);
        }
        return Math.max(low, Math.min(h0, pos.getY()) - 12);
    }

    // ------------------------------------------------------------------ wildlife hook support
    static boolean beddingCached(ServerLevel level, BlockPos pos, long t) {
        LevelState st = STATES.get(level.dimension());
        if (st == null) return false;
        long key = ((long) (pos.getX() >> 4) & 0x3FFFFFL) | (((long) (pos.getZ() >> 4) & 0x3FFFFFL) << 22) | (((long) (pos.getY() >> 4) & 0xFFFFL) << 44);
        long stamp = t / 40L;
        long v = st.bedCache.getOrDefault(key, -1L);
        if (v >= 0 && (v >> 1) == stamp) return (v & 1L) != 0;
        SeasonalWeather.Sample s = SeasonalWeather.sample(level, pos);
        boolean bed = switch (s.kind()) {
            case BLIZZARD, DUST_STORM -> s.severity() > 0.45f;
            case THUNDERSTORM, SQUALL -> s.severity() > 0.6f;
            case WIND_STORM -> s.severity() > 0.85f;
            default -> false;
        };
        st.bedCache.put(key, (stamp << 1) | (bed ? 1L : 0L));
        return bed;
    }

    // ------------------------------------------------------------------ sync
    private static void sync(ServerLevel level, LevelState st, long now) {
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return;
        Wilderness.Wind wind = wind(level);
        float vis = WeatherConfig.blizzardVisibility();
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer p = players.get(i);
            send(p, level, st, now, wind, vis);
        }
    }

    static void sendTo(ServerPlayer p, ServerLevel level) {
        LevelState st = weatherLevel(level) ? STATES.get(level.dimension()) : null;
        if (st == null) {
            if (p.connection != null && p.connection.hasChannel(WeatherNetwork.Sync.TYPE)) {
                PacketDistributor.sendToPlayer(p, new WeatherNetwork.Sync(level.dimension().location(), 0f, 0f, WeatherConfig.blizzardVisibility(), List.of()));
            }
            return;
        }
        send(p, level, st, level.getGameTime(), wind(level), WeatherConfig.blizzardVisibility());
    }

    private static void send(ServerPlayer p, ServerLevel level, LevelState st, long now, Wilderness.Wind wind, float vis) {
        if (p.connection == null || !p.connection.hasChannel(WeatherNetwork.Sync.TYPE)) return;
        List<Storm> near = List.of();
        for (Storm s : st.storms) {
            if (!s.covers(now, p.getX(), p.getZ(), SYNC_MARGIN)) continue;
            if (near.isEmpty()) near = new ArrayList<>(4);
            if (near.size() < WeatherNetwork.MAX_STORMS) near.add(s);
        }
        PacketDistributor.sendToPlayer(p, new WeatherNetwork.Sync(level.dimension().location(), (float) wind.east(), (float) wind.south(), vis, near));
    }

    // ------------------------------------------------------------------ commands support
    static Storm force(ServerLevel level, ServerPlayer at, double x, double z, BlockPos pos, WeatherKind kind, int seconds) {
        LevelState st = state(level);
        long now = level.getGameTime();
        // replace whatever of the same family is overhead
        for (Iterator<Storm> it = st.storms.iterator(); it.hasNext(); ) {
            Storm s = it.next();
            if (s.kind.family == kind.family && s.covers(now, x, z, 0)) it.remove();
        }
        while (st.storms.size() >= MAX_STORMS) st.storms.remove(0);
        int ticks = Math.max(10, seconds) * 20;
        if (kind == WeatherKind.BLIZZARD || kind == WeatherKind.SQUALL) {
            level.setWeatherParameters(0, ticks + 1200, true, false);
        } else if (kind == WeatherKind.THUNDERSTORM) {
            level.setWeatherParameters(0, ticks + 1200, true, true);
        }
        st.calmUntil = 0L;
        Climate c = new Climate().sample(level, pos);
        Storm s = spawn(level, st, kind, x, z, pos, c, new Dice(level.getSeed() ^ now ^ kind.ordinal()), now, true, ticks);
        sync(level, st, now);
        return s;
    }

    /** Ease every storm out over ~10 s and keep natural events away for {@code seconds}. */
    static int clear(ServerLevel level, int seconds) {
        LevelState st = state(level);
        long now = level.getGameTime();
        int n = 0;
        for (Storm s : st.storms) if (s.easeOut(now, 200)) n++;
        st.calmUntil = now + Math.max(0, seconds) * 20L;
        st.dirty = true;
        return n;
    }

    static List<Storm> list(ServerLevel level) {
        LevelState st = STATES.get(level.dimension());
        return st == null ? List.of() : st.view;
    }

    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
