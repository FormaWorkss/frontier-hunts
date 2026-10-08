package com.formaworks.frontierhunts.weather;

import com.formaworks.frontierhunts.season.SeasonClock;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Public query API for seasonal severe weather. Works on both logical sides: on the server it reads the
 * {@link WeatherDirector}'s storms, on the client the storms the server last synced to this player.
 *
 * <pre>
 *   SeasonalWeather.event(level, pos)          // dominant event here (NONE when calm)
 *   SeasonalWeather.severity(level, pos)       // 0..1 strength of that event here
 *   SeasonalWeather.severity(level, pos, kind) // 0..1 for one specific kind
 *   SeasonalWeather.visibility(level, pos)     // rough visibility in blocks (9999 when calm)
 *   SeasonalWeather.beddingWeather(level, pos) // wildlife should bed down / stay bedded
 *   SeasonalWeather.trackFillRate(level, pos)  // multiplier for how fast tracks/sign get filled in (1 = normal)
 * </pre>
 */
public final class SeasonalWeather {
    private SeasonalWeather() {}

    /** Immutable result of a sample. */
    public record Sample(WeatherKind kind, float severity, Storm storm) {
        public static final Sample CALM = new Sample(WeatherKind.NONE, 0f, null);
        public boolean calm() { return kind == WeatherKind.NONE; }
    }

    // ------------------------------------------------------------------ client-synced copy (plain data, no client classes)
    static volatile List<Storm> clientStorms = List.of();
    static volatile ResourceLocation clientDimension;
    static volatile float clientWindEast, clientWindSouth, clientMinVisibility = 5f;
    static volatile long clientSyncedAt;

    static void acceptSync(WeatherNetwork.Sync s) {
        clientStorms = List.copyOf(s.storms());
        clientDimension = s.dimension();
        clientWindEast = s.windEast();
        clientWindSouth = s.windSouth();
        clientMinVisibility = s.minVisibility();
        clientSyncedAt = System.nanoTime();
    }

    public static void clearClient() {
        clientStorms = List.of();
        clientDimension = null;
    }

    /** Storms known for this level (server: live list; client: the synced list, empty for other dimensions). */
    public static List<Storm> storms(Level level) {
        if (level == null) return List.of();
        if (level.isClientSide) {
            ResourceLocation d = clientDimension;
            return d != null && d.equals(level.dimension().location()) ? clientStorms : List.of();
        }
        return level instanceof ServerLevel sl ? WeatherDirector.storms(sl) : List.of();
    }

    /** Wind the storm presentation uses (client: synced; server: the shared Wilderness wind). East/south, "m/s". */
    public static float windEast(Level level) { return level != null && level.isClientSide ? clientWindEast : WeatherDirector.windEast(level); }

    public static float windSouth(Level level) { return level != null && level.isClientSide ? clientWindSouth : WeatherDirector.windSouth(level); }

    /** Blizzard visibility floor in blocks (server config, synced). */
    public static float blizzardVisibility(Level level) {
        return level != null && level.isClientSide ? clientMinVisibility : WeatherConfig.blizzardVisibility();
    }

    // ------------------------------------------------------------------ evaluation
    /** Severity of one storm at a place, including where the event actually applies (climate, rain, fog ceiling). */
    public static float severity(Storm s, Level level, BlockPos pos, Climate c, float winterness, long t) {
        float raw = s.raw(t, pos.getX() + 0.5, pos.getZ() + 0.5);
        if (raw <= 0.001f) return 0f;
        float app;
        if (s.forced) {
            app = 1f;
        } else {
            app = c.applicability(s.kind, winterness, level.getRainLevel(1f));
        }
        if (!Float.isNaN(s.top)) {
            float over = (pos.getY() - (s.top - 3f)) / 10f; // full below the ceiling, gone ~7 blocks above it
            app *= 1f - Storm.smooth(over);
        }
        return raw * app;
    }

    /** Dominant event at a place. Allocates one {@link Climate}; fine for occasional calls, avoid per-entity per-tick use. */
    public static Sample sample(Level level, BlockPos pos) {
        List<Storm> list = storms(level);
        if (list.isEmpty()) return Sample.CALM;
        long t = level.getGameTime();
        boolean any = false;
        for (Storm s : list) if (s.raw(t, pos.getX() + 0.5, pos.getZ() + 0.5) > 0.001f) { any = true; break; }
        if (!any) return Sample.CALM;
        Climate c = new Climate().sample(level, pos);
        float w = SeasonClock.winterness(level);
        Storm best = null;
        float bestSev = 0f, bestWeight = 0f;
        for (Storm s : list) {
            float v = severity(s, level, pos, c, w, t);
            if (v <= 0.02f) continue;
            // blizzards / dust / fog / rain dominate a mere wind storm of similar strength
            float weight = v * (s.kind == WeatherKind.WIND_STORM ? 0.6f : 1f);
            if (weight > bestWeight) {
                best = s;
                bestSev = v;
                bestWeight = weight;
            }
        }
        return best == null ? Sample.CALM : new Sample(best.kind, bestSev, best);
    }

    public static WeatherKind event(Level level, BlockPos pos) { return sample(level, pos).kind(); }

    public static float severity(Level level, BlockPos pos) { return sample(level, pos).severity(); }

    public static float severity(Level level, BlockPos pos, WeatherKind kind) {
        List<Storm> list = storms(level);
        if (list.isEmpty()) return 0f;
        long t = level.getGameTime();
        Climate c = null;
        float w = 0f, best = 0f;
        for (Storm s : list) {
            if (s.kind != kind || s.raw(t, pos.getX() + 0.5, pos.getZ() + 0.5) <= 0.001f) continue;
            if (c == null) { c = new Climate().sample(level, pos); w = SeasonClock.winterness(level); }
            best = Math.max(best, severity(s, level, pos, c, w, t));
        }
        return best;
    }

    /** Visibility in blocks for a kind at a severity (log-space blend from open air to the kind's floor). */
    public static float visibilityFor(WeatherKind kind, float severity, float blizzardFloor) {
        if (kind == WeatherKind.NONE || severity <= 0f) return 9999f;
        float floor = kind == WeatherKind.BLIZZARD ? blizzardFloor : kind.peakVisibility;
        if (kind == WeatherKind.BLIZZARD) severity = blizzardDrive(severity);
        float f = (float) Math.pow(Math.min(1f, severity), 0.6);
        double open = Math.log(256.0), shut = Math.log(Math.max(2.0, floor));
        return (float) Math.exp(open + (shut - open) * f);
    }

    /**
     * [blizzard2] Presentation strength of a blizzard: severity 0.87+ counts as full, so the storm's slow banding and
     * a natural peak just under 1 still collapse visibility to the floor at the peak.
     */
    public static float blizzardDrive(float severity) {
        return Math.max(0f, Math.min(1f, severity * 1.15f));
    }

    public static float visibility(Level level, BlockPos pos) {
        Sample s = sample(level, pos);
        return visibilityFor(s.kind(), s.severity(), blizzardVisibility(level));
    }

    /**
     * True when wildlife should bed down (or stay bedded) here: a blizzard, dust storm, thunderstorm or squall at more
     * than moderate strength. Cheap when calm (no climate sampling unless a storm is actually overhead).
     */
    public static boolean beddingWeather(Level level, BlockPos pos) {
        if (level == null || level.isClientSide) return false;
        List<Storm> list = storms(level);
        if (list.isEmpty()) return false;
        long t = level.getGameTime();
        boolean candidate = false;
        for (Storm s : list) {
            if (s.kind == WeatherKind.FOG || s.kind == WeatherKind.FREEZING_FOG) continue;
            if (s.raw(t, pos.getX() + 0.5, pos.getZ() + 0.5) > 0.45f) { candidate = true; break; }
        }
        if (!candidate) return false;
        return WeatherDirector.beddingCached((ServerLevel) level, pos, t);
    }

    /** Multiplier for how fast tracks and sign fill in: blowing snow and sand bury them, downpours wash them out. */
    public static float trackFillRate(Level level, BlockPos pos) {
        Sample s = sample(level, pos);
        return switch (s.kind()) {
            case BLIZZARD -> 1f + 8f * s.severity();
            case DUST_STORM -> 1f + 5f * s.severity();
            case THUNDERSTORM, SQUALL -> 1f + 3f * s.severity();
            case WIND_STORM -> 1f + 1.5f * s.severity();
            default -> 1f;
        };
    }
}
