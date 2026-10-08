package com.formaworks.frontierhunts.landscape.ride.grime;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.landscape.ride.Atv;
import com.formaworks.frontierhunts.weather.Climate;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * Mud / snow / water grime on one ATV ({@code Atv.grime}).
 *
 * <p>Server authoritative: the server integrates the state from what the wheels roll over (sampled under each of the
 * four wheels every tick), the ATV's measured speed, water depth, rain, snowfall and the biome climate, persists it in
 * the entity's NeoForge persistent data and syncs a 5-byte snapshot to every client that sees the ATV. Clients only
 * render it (body overlays, wet look) and spawn cosmetic spray / driver screen splatter from their own view of the
 * wheels.
 *
 * <ul>
 *   <li>{@link #mudLow}: tyres, wheel wells, footwells, lower body. Fills first, washes first in water.</li>
 *   <li>{@link #mudHigh}: spray thrown onto fenders, body, racks, seat; only builds at speed.</li>
 *   <li>{@link #moist}: 1 = freshly wet dark mud, 0 = light dried crust. The crust flakes off: a heavy coat is mostly
 *   gone after 8-10 minutes parked (faster on the move), 60-90 s of rain under open sky rinses it, hub-deep water in a
 *   few seconds. [atv2]</li>
 *   <li>{@link #snow}: powder and packed snow; melts over minutes (faster when warm, never while it is snowing in a
 *   snowy biome), cleared by water.</li>
 *   <li>{@link #wet}: wet paint after fording or in rain (rendered as a darker tint), dries over about a minute.</li>
 * </ul>
 */
public final class AtvGrime {
    // ------------------------------------------------------------------ synced state (0..1)
    public float mudLow, mudHigh, moist, snow, wet;

    // ------------------------------------------------------------------ client presentation (smoothed; written by client code only)
    public float shownMoist, shownWet;
    public float emitMud, emitSnow, emitWater, lastDepth;
    public float emitFan, emitBow, emitFoam; // [atv2] water fx accumulators (client)
    public int sloshCooldown; // [atv2]
    public int clientAge;

    // ------------------------------------------------------------------ server bookkeeping
    private boolean loaded;
    private int syncCooldown, heartbeat, saveCooldown, climateAge;
    private final byte[] sent = new byte[5];
    private boolean dirtySave;
    private Climate climate; // created on first use (keeps the entity constructor light)
    private float temp = 0.8f, winter;
    private boolean coldBiome, snowingHere, rainingHere, wetBiome, wetGround;

    /** Client hooks, installed by the client setup; no-ops on a dedicated server. */
    public static volatile Consumer<Atv> clientTick = a -> {};
    public static volatile Consumer<AtvGrimeNet.Sync> clientSync = s -> {};

    public static final String NBT = FrontierHunts.ID + ":atv_grime";

    // ================================================================== entry point (one hook line in Atv.tick)
    public static void tick(Atv atv) {
        try {
            if (atv.level().isClientSide) {
                clientTick.accept(atv);
            } else {
                atv.grime.serverTick(atv);
            }
        } catch (RuntimeException e) {
            // cosmetic system: never take the vehicle down with it
            if (!loggedFailure) {
                loggedFailure = true;
                LOG.warn("[atvgrime] grime tick failed, continuing without it", e);
            }
        }
    }

    private static boolean loggedFailure;
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();

    // ================================================================== wheel sampling (shared by server simulation and client effects)
    /** Wheel contact offsets in blocks (forward, right): FL, FR, RL, RR. Matches the rendered wheel positions. */
    public static final float WHEEL_FWD = 0.72f, WHEEL_SIDE = 0.62f;

    /** Reusable per-caller scratch for {@link #sample}. */
    public static final class Sample {
        public final float[] mud = new float[4], snow = new float[4];
        public final double[] wx = new double[4], wz = new double[4];
        public float mudAvg, snowAvg, depth;
        public int contact;
        public double fx, fz, rx, rz;
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    }

    /** Samples the ground under all four wheels and the water depth at the ATV. No allocation. */
    public static void sample(Level level, Atv atv, boolean wetGround, boolean wetBiome, Sample s) {
        float yaw = atv.getYRot() * Mth.DEG_TO_RAD;
        s.fx = -Mth.sin(yaw);
        s.fz = Mth.cos(yaw);
        s.rx = -s.fz;
        s.rz = s.fx;
        double x = atv.getX(), y = atv.getY(), z = atv.getZ();
        float mud = 0f, snw = 0f;
        int contact = 0;
        for (int i = 0; i < 4; i++) {
            double f = i < 2 ? WHEEL_FWD : -WHEEL_FWD;
            double r = (i & 1) == 0 ? -WHEEL_SIDE : WHEEL_SIDE;
            double wx = x + s.fx * f + s.rx * r, wz = z + s.fz * f + s.rz * r;
            s.wx[i] = wx;
            s.wz[i] = wz;
            BlockPos.MutableBlockPos p = s.pos;
            p.set(Mth.floor(wx), Mth.floor(y + 0.05), Mth.floor(wz));
            BlockState at = level.getBlockState(p);
            p.set(Mth.floor(wx), Mth.floor(y - 0.2), Mth.floor(wz));
            BlockState below = level.getBlockState(p);
            float sn = snowOf(at);
            if (sn == 0f) sn = snowOf(below);
            float md = mudOf(below, wetGround, wetBiome);
            if (md == 0f && !at.isAir()) md = mudOf(at, wetGround, wetBiome);
            if (!below.isAir() || sn > 0f) contact++;
            s.mud[i] = md;
            s.snow[i] = sn;
            mud += md;
            snw += sn;
        }
        s.mudAvg = mud * 0.25f;
        s.snowAvg = snw * 0.25f;
        s.contact = contact;
        s.depth = waterDepth(level, atv, s.pos);
    }

    static float snowOf(BlockState st) {
        Block b = st.getBlock();
        if (b instanceof SnowLayerBlock) {
            int layers = st.getValue(SnowLayerBlock.LAYERS);
            return 0.3f + 0.7f * Math.min(layers, 6) / 6f;
        }
        if (b == Blocks.POWDER_SNOW) return 1f;
        if (b == Blocks.SNOW_BLOCK) return 0.85f;
        return 0f;
    }

    private static volatile Map<Block, Float> modMud;

    private static Map<Block, Float> modMud() {
        Map<Block, Float> m = modMud;
        if (m == null) {
            m = new IdentityHashMap<>();
            put(m, "forest_loam", 0.4f);
            put(m, "forest_duff", 0.22f);
            put(m, "moss_floor", 0.12f);
            modMud = m;
        }
        return m;
    }

    private static void put(Map<Block, Float> m, String id, float v) {
        BuiltInRegistries.BLOCK.getOptional(FrontierHunts.id(id)).ifPresent(b -> m.put(b, v));
    }

    /** How muddy a block is to drive on (0 = clean ground, 1 = deep mud). Dirt and grass turn to mud in rain and wetlands. */
    public static float mudOf(BlockState st, boolean wetGround, boolean wetBiome) {
        if (st.isAir()) return 0f;
        Block b = st.getBlock();
        if (b == Blocks.MUD) return 1f;
        if (b == Blocks.MUDDY_MANGROVE_ROOTS) return 0.85f;
        if (b == Blocks.CLAY) return 0.7f;
        if (b == Blocks.SOUL_SOIL) return 0.45f;
        Float mod = modMud().get(b);
        float base = mod != null ? mod : 0f;
        boolean dirt = st.is(BlockTags.DIRT) || b == Blocks.FARMLAND || b == Blocks.DIRT_PATH;
        if (dirt) {
            boolean grassy = b == Blocks.GRASS_BLOCK || b == Blocks.MOSS_BLOCK || b == Blocks.MYCELIUM;
            if (wetGround) base = Math.max(base, grassy ? 0.3f : 0.55f);
            else if (wetBiome) base = Math.max(base, grassy ? 0.12f : 0.35f);
            if (b == Blocks.FARMLAND && st.getValue(FarmBlock.MOISTURE) >= 7) base = Math.max(base, 0.35f);
        } else if (mod != null && wetGround) {
            base = Math.min(1f, base + 0.2f);
        }
        return base;
    }

    /** Water depth above the ATV's wheels in blocks (0..3), same rule as the ATV's own wading check. */
    public static float waterDepth(Level level, Atv atv, BlockPos.MutableBlockPos p) {
        BlockPos base = atv.blockPosition();
        float d = 0f;
        double y0 = atv.getY() - base.getY();
        for (int i = 0; i < 3; i++) {
            p.set(base.getX(), base.getY() + i, base.getZ());
            FluidState fs = level.getFluidState(p);
            if (fs.isEmpty() || !fs.is(Fluids.WATER) && !fs.is(Fluids.FLOWING_WATER)) break;
            d = i + fs.getHeight(level, p);
        }
        return (float) Math.max(0.0, d - y0);
    }

    // ================================================================== server simulation
    private static final Sample SERVER = new Sample();

    private static float smooth(float e0, float e1, float x) {
        float t = Mth.clamp((x - e0) / (e1 - e0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    void ensureLoaded(Atv atv) {
        if (loaded) return;
        loaded = true;
        CompoundTag root = atv.getPersistentData();
        if (root.contains(NBT)) {
            CompoundTag t = root.getCompound(NBT);
            mudLow = clamp01(t.getFloat("mudLow"));
            mudHigh = clamp01(t.getFloat("mudHigh"));
            moist = clamp01(t.getFloat("moist"));
            snow = clamp01(t.getFloat("snow"));
            wet = clamp01(t.getFloat("wet"));
        }
    }

    private void serverTick(Atv atv) {
        Level level = atv.level();
        ensureLoaded(atv);
        if (--climateAge <= 0) {
            climateAge = 40;
            sampleClimate(level, atv);
        }
        Sample s = SERVER;
        sample(level, atv, wetGround, wetBiome, s);
        simulate(s, (float) Math.abs(atv.shownSpeed));
        sync(atv);
    }

    /** Client effects: refresh the cached climate every two seconds and report whether the ground is rain-wet. */
    public void refreshClimate(Level level, Atv atv) {
        if (--climateAge <= 0) {
            climateAge = 40;
            sampleClimate(level, atv);
        }
    }

    public boolean wetGround() {
        return wetGround;
    }

    public boolean wetBiome() {
        return wetBiome;
    }

    public boolean raining() {
        return rainingHere;
    }

    private void sampleClimate(Level level, Atv atv) {
        BlockPos top = atv.blockPosition().above(2);
        if (climate == null) climate = new Climate();
        Climate climate = this.climate;
        climate.sample(level, top);
        temp = climate.rawTemp;
        boolean sky = level.canSeeSky(top);
        boolean precipitating = level.isRaining() && sky;
        coldBiome = climate.snowTag || climate.precip == Biome.Precipitation.SNOW || climate.cold < 0.15f;
        snowingHere = precipitating && climate.precip == Biome.Precipitation.SNOW;
        rainingHere = precipitating && climate.precip == Biome.Precipitation.RAIN;
        wetBiome = climate.wet;
        wetGround = level.isRaining() && climate.precip == Biome.Precipitation.RAIN;
        try {
            winter = com.formaworks.frontierhunts.season.SeasonClock.winterness(level);
        } catch (Throwable t) {
            winter = 0f;
        }
    }

    /** One server tick of grime physics. Rates are per tick (20/s). */
    void simulate(Sample s, float speed) {
        float sp = Math.min(speed, 1.3f);
        float splash = smooth(0.22f, 0.85f, sp);
        // under water the riverbed does not cake anything on: the water washes faster than it can stick
        boolean grounded = s.contact >= 2 && s.depth < 0.3f;

        // ---- mud: tyres and lower body first, spray reaches the upper body only at speed
        if (grounded && s.mudAvg > 0f && sp > 0.005f) {
            // wet dirt only gets an ATV so dirty; real mud cakes it completely
            float src = s.mudAvg * (float) Math.sqrt(s.mudAvg);
            float ceil = 0.35f + 0.7f * s.mudAvg;
            float gain = src * sp;
            mudLow += gain * 0.013f * Math.max(0f, ceil - mudLow);
            mudHigh += gain * 0.010f * splash * Math.max(0f, ceil - 0.05f - mudHigh);
            moist = Math.min(1f, moist + gain * 0.6f);
        } else if (grounded && s.mudAvg > 0.6f && mudLow < 0.18f) {
            mudLow += 0.0006f; // parked in deep mud: the tyres sink in a little
            moist = Math.min(1f, moist + 0.01f);
        }

        // ---- snow: packed into treads, powder thrown up at speed, and snowfall settling on top
        if (grounded && s.snowAvg > 0f && sp > 0.005f) {
            float gain = s.snowAvg * sp;
            snow += gain * 0.014f * (0.35f + 0.65f * splash) * (1.1f - snow);
        }
        if (snowingHere) snow += 0.00018f * (1.05f - snow);

        // ---- water [atv2]: wheel-hub-deep water rinses the tyres and lower body in a few seconds (faster when moving),
        //      the spray thrown up by the wheels and the bow wave sluice the body; deeper water washes it directly
        float d = s.depth;
        boolean inWater = d > 0.05f;
        if (inWater) {
            float mv = Math.min(sp, 1f);
            float lowW = smooth(0.05f, 0.35f, d), highW = smooth(0.35f, 0.95f, d);
            float spray = lowW * smooth(0.06f, 0.5f, mv);
            mudLow -= lowW * (0.008f + 0.011f * mv);                  // hub-deep: ~6 s parked, ~3-4 s driving
            mudHigh -= highW * (0.006f + 0.008f * mv) + spray * 0.0075f; // spray at speed: ~6 s
            snow -= lowW * 0.045f + spray * 0.02f + 0.002f;
            wet = Math.max(wet, 0.35f + 0.65f * Math.max(highW, spray));
            moist = 1f;
        }

        // ---- rain [atv2]: under open sky rain rinses the ATV noticeably: a heavy coat is gone in about 60-90 s (the
        //      exposed top first, the sheltered wheel wells last); it keeps what is left wet and wets the paint
        float warmth = Mth.clamp(0.4f + temp * 0.8f, 0.25f, 2f) * (1f - 0.5f * winter);
        if (rainingHere) {
            mudHigh -= 0.0016f * mudHigh + 0.00025f;
            mudLow -= 0.0011f * mudLow + 0.0002f;
            if (wet < 0.55f) wet = Math.min(0.55f, wet + 0.004f);
            if (moist < 0.85f) moist = Math.min(0.85f, moist + 0.002f);
        } else if (!inWater) {
            // drying: fresh mud sets to a crust in about 2.5 minutes in a temperate climate (slower when cold)
            moist -= (1f / (20f * 150f)) * warmth;
        }

        // ---- natural fade [atv2]: the drying crust cracks and flakes off (a little more when the ATV shakes over clean
        //      ground); wet mud only slumps off slowly. A heavy coat is mostly gone after 8-10 minutes, never at once.
        if (!rainingHere && !inWater) {
            float dryF = smooth(0.6f, 0.15f, moist);
            float vib = 1f + Math.min(sp, 1f) * 0.8f;
            mudHigh -= dryF * vib * (0.00027f * mudHigh + 0.000024f) + (1f - dryF) * 0.00003f;
            mudLow -= dryF * vib * (0.00022f * mudLow + 0.00002f) + (1f - dryF) * 0.00002f;
        }

        // ---- snow melt: minutes, faster when warm; never while it is snowing in a snowy biome
        if (snow > 0f && !(coldBiome && snowingHere)) {
            float melt;
            if (coldBiome) melt = 1f / (20f * 60f * 10f);
            else if (temp < 0.5f) melt = 1f / (20f * 60f * 4f);
            else if (temp < 1.0f) melt = 1f / (20f * 60f * 2.5f);
            else melt = 1f / (20f * 60f);
            if (!coldBiome) melt *= 1f - 0.6f * winter;
            if (rainingHere) melt *= 2.5f;
            melt += sp * 0.0004f; // blown off at speed
            snow -= melt;
            if (!coldBiome && snow > 0.05f) wet = Math.max(wet, Math.min(0.3f, snow * 0.4f)); // meltwater
        }

        // ---- wet film dries in about a minute (held while it rains)
        if (!(rainingHere && wet <= 0.55f) && !inWater) wet -= (1f / (20f * 70f)) * warmth;

        mudLow = clamp01(mudLow);
        mudHigh = clamp01(Math.min(mudHigh, mudLow + 0.6f));
        moist = clamp01(moist);
        snow = clamp01(snow);
        wet = clamp01(wet);
        if (mudLow + mudHigh < 0.002f) moist = 0f;
    }

    static float clamp01(float v) {
        return v != v ? 0f : Math.max(0f, Math.min(1f, v));
    }

    static byte q(float v) {
        return (byte) Math.round(clamp01(v) * 255f);
    }

    public static float dq(byte b) {
        return (b & 255) / 255f;
    }

    AtvGrimeNet.Sync snapshot(Atv atv) {
        return new AtvGrimeNet.Sync(atv.getId(), q(mudLow), q(mudHigh), q(moist), q(snow), q(wet));
    }

    private void sync(Atv atv) {
        if (syncCooldown > 0) syncCooldown--;
        if (saveCooldown > 0) saveCooldown--;
        heartbeat++;
        byte a = q(mudLow), b = q(mudHigh), c = q(moist), e = q(snow), f = q(wet);
        boolean changed = moved(a, sent[0], 2) || moved(b, sent[1], 2) || moved(c, sent[2], 5) || moved(e, sent[3], 2) || moved(f, sent[4], 5);
        if (changed) dirtySave = true;
        boolean any = a != 0 || b != 0 || e != 0 || f != 0;
        if (changed && syncCooldown <= 0 || any && heartbeat >= 200) {
            sent[0] = a; sent[1] = b; sent[2] = c; sent[3] = e; sent[4] = f;
            syncCooldown = 10;
            heartbeat = 0;
            AtvGrimeNet.sendTracking(atv, new AtvGrimeNet.Sync(atv.getId(), a, b, c, e, f));
        }
        if (dirtySave && saveCooldown <= 0) {
            dirtySave = false;
            saveCooldown = 40;
            save(atv);
        }
    }

    /** True when the quantised value moved by at least {@code step}, or crossed zero either way. */
    private static boolean moved(byte now, byte was, int step) {
        int n = now & 255, w = was & 255;
        return Math.abs(n - w) >= step || (n == 0) != (w == 0);
    }

    void save(Atv atv) {
        CompoundTag t = new CompoundTag();
        t.putFloat("mudLow", mudLow);
        t.putFloat("mudHigh", mudHigh);
        t.putFloat("moist", moist);
        t.putFloat("snow", snow);
        t.putFloat("wet", wet);
        atv.getPersistentData().put(NBT, t);
    }

    /** Commands / tests: set values directly and push them out right away. */
    public void force(Atv atv, float mudLow, float mudHigh, float moist, float snow, float wet) {
        ensureLoaded(atv);
        this.mudLow = clamp01(mudLow);
        this.mudHigh = clamp01(mudHigh);
        this.moist = clamp01(moist);
        this.snow = clamp01(snow);
        this.wet = clamp01(wet);
        save(atv);
        AtvGrimeNet.Sync snap = snapshot(atv);
        sent[0] = snap.mudLow(); sent[1] = snap.mudHigh(); sent[2] = snap.moist(); sent[3] = snap.snow(); sent[4] = snap.wet();
        syncCooldown = 10;
        heartbeat = 0;
        AtvGrimeNet.sendTracking(atv, snap);
    }

    /** Client: apply a server snapshot. */
    public void accept(AtvGrimeNet.Sync s) {
        mudLow = dq(s.mudLow());
        mudHigh = dq(s.mudHigh());
        moist = dq(s.moist());
        snow = dq(s.snow());
        wet = dq(s.wet());
        if (clientAge == 0) {
            shownMoist = moist;
            shownWet = wet;
        }
    }

    // ================================================================== render levels
    public static final int MUD_LEVELS = 5, SNOW_LEVELS = 3;

    public static int mudLevel(float v) {
        return v < 0.03f ? 0 : Math.min(MUD_LEVELS, 1 + (int) ((v - 0.0001f) * MUD_LEVELS));
    }

    public static int snowLevel(float v) {
        return v < 0.04f ? 0 : Math.min(SNOW_LEVELS, 1 + (int) ((v - 0.0001f) * SNOW_LEVELS));
    }

    static Iterable<ServerPlayer> watchers(Atv atv) {
        if (atv.level() instanceof ServerLevel sl) {
            return sl.getChunkSource().chunkMap.getPlayers(new ChunkPos(atv.blockPosition()), false);
        }
        return java.util.List.of();
    }
}
