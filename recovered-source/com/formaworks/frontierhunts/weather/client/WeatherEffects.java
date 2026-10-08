package com.formaworks.frontierhunts.weather.client;

import com.formaworks.frontierhunts.season.SeasonClock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Camera-relative storm particle volumes with hard budgets. Each tick tops every role up to its target count (which
 * scales with severity, shelter and the quality setting) and never beyond its absolute cap. Spawns are rejected under
 * roofs and inside blocks, so nothing snows indoors, while you still see the storm through a window.
 */
final class WeatherEffects {
    private static final RandomSource R = RandomSource.create();
    private static final BlockPos.MutableBlockPos M = new BlockPos.MutableBlockPos();

    // absolute caps per role (Cinematic ceiling)
    // [blizzard2] streak 1100 -> 1300, veil 150 -> 170, flake 160 -> 180, drift 240 -> 300, + near streaks 260
    private static final int CAP_STREAK = 1300, CAP_VEIL = 170, CAP_FLAKE = 180, CAP_LEAF = 220, CAP_DRIFT = 300, CAP_RAIN = 600, CAP_SPARKLE = 140, CAP_NEAR = 260;

    private static final float[][] FALL_LEAVES = {{0.88f, 0.44f, 0.10f}, {0.78f, 0.22f, 0.08f}, {0.94f, 0.72f, 0.18f}, {0.62f, 0.38f, 0.16f}, {0.84f, 0.56f, 0.12f}, {0.70f, 0.16f, 0.10f}};
    private static final float[][] DEAD_LEAVES = {{0.52f, 0.38f, 0.22f}, {0.60f, 0.47f, 0.29f}, {0.44f, 0.32f, 0.20f}};
    private static final float[][] GREEN_LEAVES = {{0.36f, 0.58f, 0.20f}, {0.30f, 0.50f, 0.16f}, {0.46f, 0.62f, 0.24f}, {0.56f, 0.46f, 0.22f}};

    private static float q, qr;

    private WeatherEffects() {}

    static void tick(Minecraft mc, ClientLevel level, Vec3 cp, long t) {
        if (!WeatherParticles.ready() || !level.dimensionType().hasSkyLight()) return;
        int ql = WeatherClient.quality;
        q = ql == 0 ? 0.42f : ql == 2 ? 1.7f : 1f;
        qr = ql == 0 ? 0.8f : ql == 2 ? 1.25f : 1f;
        float[] s = WeatherClient.sev;
        float under = WeatherClient.underground;
        // [shelter] inside a tent / cabin / cave the close-range particle volume thins out (what is left blows past the
        // windows and the door; particles near the camera also fade out at render time, see WeatherParticles.shelterFade)
        float ex = WeatherClient.exposure, out = 0.25f + 0.75f * ex;
        ParticleEngine pe = mc.particleEngine;
        float sB = s[WeatherClient.BLIZZARD] * under;
        float sR = Math.max(s[WeatherClient.THUNDER], s[WeatherClient.SQUALL]) * under;
        float sD = s[WeatherClient.DUST] * under;
        float sF = Math.max(s[WeatherClient.FOG], s[WeatherClient.FFOG]) * under;
        float sW = s[WeatherClient.WIND] * under;
        float g = WeatherClient.gust;
        int streakT = 0, driftT = 0, veilT = 0;

        if (sB > 0.01f) {
            // [blizzard2] brutal peak: denser, faster wind-driven snow with bigger gust swings, a close-range layer of
            // large streaks driven past the camera, heavier ground drift and closer, thicker veils. Budgets at full
            // strength (Performance / Balanced / Cinematic): streaks 269/640/1088 (was 220/520/880), near 50/120/204
            // (new), drift 63/150/255 (was 46/110/187), flakes 0/90/153 (was 0/70/119), veils 52/96/139 (was 39/72/104).
            float dB = WeatherClient.drive(sB);
            float wo = WeatherClient.whiteout * WeatherClient.exposure;
            int n = cap((int) (640 * q * dB * (0.7f + 0.3f * g) * (1f + 0.3f * wo) * out), CAP_STREAK);
            streakT += n;
            float speed = (0.42f + 1.0f * dB * (0.5f + 0.75f * g)) * (1f + 0.25f * wo); // ~1.3-1.9 blocks/tick at the peak
            // spawn upwind by half a streak's flight so the volume is centred on the camera, not blown past it
            float up = Math.min(12f, speed * 7f);
            streaks(pe, level, cp, WeatherParticles.STREAK, n, 10f * qr, up, -3f, 7f, speed, 0.96f, 0.97f, 1f, 0.78f, 0.05f, 1.2f, 10, 20, -0.06f, 0.09f, 0.35f);
            int nn = cap((int) (120 * q * dB * (0.6f + 0.6f * g) * (1f + 0.5f * wo) * WeatherClient.exposure), CAP_NEAR);
            nearStreaks(pe, level, cp, nn, speed * 1.1f);
            if (ql > 0) flakes(pe, level, cp, cap((int) (90 * q * dB * ex), CAP_FLAKE), 4.5f, speed * 0.6f, dB);
            int d = cap((int) (150 * q * dB * (0.5f + 0.8f * g) * out), CAP_DRIFT);
            driftT += d;
            drift(pe, level, cp, d, 13f * qr, speed * 0.8f, 0.97f, 0.98f, 1f, 0.42f, true, 0.6f + 0.8f * g);
            int v = cap((int) (96 * (float) Math.pow(q, 0.7) * dB), CAP_VEIL);
            veilT += v;
            veils(pe, level, cp, v, 0.84f, 0.86f, 0.9f, (ql == 0 ? 0.46f : 0.4f) * Math.min(1f, dB * 1.1f) * (1f + 0.25f * wo), 2.6f, 22f, -20f, 35f,
                    0.1f * (0.6f + g), 0.8f, 0.21f, 50, 90, Float.NaN);
        }
        if (sR > 0.01f) {
            int n = cap((int) (320 * q * sR * out), CAP_RAIN);
            float drift = 0.05f + 0.14f * sR * g;
            rain(pe, level, cp, n, 9f * qr, drift);
            int splash = (int) (8 * q * sR * ex);
            for (int i = 0; i < splash; i++) {
                double x = cp.x + (R.nextDouble() - 0.5) * 14.0, z = cp.z + (R.nextDouble() - 0.5) * 14.0;
                int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
                if (Math.abs(h - cp.y) < 8.0) level.addParticle(ParticleTypes.RAIN, x, h + 0.02, z, 0, 0, 0);
            }
            int v = cap((int) (26 * (float) Math.pow(q, 0.7) * sR), CAP_VEIL - veilT);
            veilT += v;
            veils(pe, level, cp, v, 0.62f, 0.66f, 0.72f, 0.13f * sR, 6f, 28f, -10f, 20f, 0.08f, 1.2f, 0.25f, 60, 100, Float.NaN);
        }
        if (sD > 0.01f) {
            int n = cap((int) (420 * q * sD * out), CAP_STREAK - streakT);
            streakT += n;
            float speed = 0.28f + 0.6f * sD * (0.6f + 0.6f * g);
            streaks(pe, level, cp, WeatherParticles.STREAK, n, 11f * qr, 11f * qr * 0.35f, -2f, 6f, speed, 0.84f, 0.68f, 0.47f, 0.55f, 0.03f, 1.4f, 14, 24, -0.01f, 0.06f, 0.25f);
            int d = cap((int) (110 * q * sD * out), CAP_DRIFT - driftT);
            driftT += d;
            drift(pe, level, cp, d, 12f * qr, speed * 0.85f, 0.80f, 0.64f, 0.44f, 0.3f, false, 0.6f);
            int v = cap((int) (80 * (float) Math.pow(q, 0.7) * sD), CAP_VEIL - veilT);
            veilT += v;
            veils(pe, level, cp, v, 0.78f, 0.58f, 0.38f, 0.34f * Math.min(1f, sD * 1.1f), 3.5f, 26f, -20f, 30f, 0.1f * (0.6f + g), 0.8f, 0.21f, 50, 90, Float.NaN);
        }
        float bank = WeatherClient.fogLayer * under;
        if (sF > 0.01f || bank > 0.02f) {
            boolean freezing = s[WeatherClient.FFOG] > s[WeatherClient.FOG];
            float amt = Math.max(sF, bank * 0.8f);
            boolean above = sF < 0.1f;
            int v = cap((int) (60 * (float) Math.pow(q, 0.7) * amt), CAP_VEIL - veilT);
            veilT += v;
            float top = Float.isNaN(WeatherClient.fogTop) ? Float.NaN : WeatherClient.fogTop - 1f;
            veils(pe, level, cp, v, freezing ? 0.84f : 0.82f, freezing ? 0.88f : 0.84f, freezing ? 0.94f : 0.86f, 0.22f * amt,
                    above ? 8f : 3f, above ? 44f : 30f, -35f, 15f, 0.02f, 1.4f, 0.28f, 90, 150, top);
            if (freezing && ql > 0) sparkles(pe, level, cp, cap((int) (90 * q * s[WeatherClient.FFOG] * under * ex), CAP_SPARKLE));
        }
        if (sW > 0.01f) {
            leaves(pe, level, cp, cap((int) (120 * q * sW * out), CAP_LEAF), sW);
            int d = cap((int) (60 * q * sW * (0.4f + 0.8f * g) * out), CAP_DRIFT - driftT);
            float speed = 0.2f + 0.45f * sW * (0.5f + 0.7f * g);
            dustOrSnowDrift(pe, level, cp, d, 13f * qr, speed);
        }
    }

    private static int cap(int n, int max) {
        return Math.max(0, Math.min(n, max));
    }

    private static int light(ClientLevel level, double x, double y, double z) {
        M.set(x, y, z);
        return LightTexture.pack(level.getBrightness(LightLayer.BLOCK, M), level.getBrightness(LightLayer.SKY, M));
    }

    /** Spawn point is under open sky (or only under leaves, with a chance), and not inside a block. */
    private static boolean open(ClientLevel level, double x, double y, double z, float canopyChance) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        if (!level.hasChunk(bx >> 4, bz >> 4)) return false;
        int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
        if (y < h) {
            if (canopyChance <= 0f || R.nextFloat() > canopyChance) return false;
            M.set(bx, h - 1, bz);
            if (!level.getBlockState(M).is(BlockTags.LEAVES)) return false;
        }
        M.set(x, y, z);
        return !level.getBlockState(M).canOcclude();
    }

    private static void streaks(ParticleEngine pe, ClientLevel level, Vec3 cp, int role, int target, float radius, float upwind, float yLo, float yHi, float speed,
                                float r, float g, float b, float alpha, float width, float blur, int lifeLo, int lifeHi, float yVel, float yJit, float canopy) {
        int live = WeatherParticles.LIVE[role];
        if (live >= target) return;
        int n = Math.min(target - live, Math.max(3, target / 7));
        float wx = WeatherClient.windUnitX, wz = WeatherClient.windUnitZ;
        double baseAng = Math.atan2(wz, wx);
        for (int i = 0; i < n; i++) {
            double a = R.nextDouble() * Math.PI * 2.0, rad = radius * Math.sqrt(R.nextDouble());
            double x = cp.x + Math.cos(a) * rad - wx * upwind, z = cp.z + Math.sin(a) * rad - wz * upwind;
            double y = cp.y + yLo + R.nextDouble() * (yHi - yLo);
            if (!open(level, x, y, z, canopy)) continue;
            double ang = baseAng + (R.nextDouble() - 0.5) * 0.42;
            double sp = speed * (0.8 + 0.4 * R.nextDouble());
            pe.add(new WeatherParticles.Streak(level, x, y, z, Math.cos(ang) * sp, yVel + (R.nextDouble() - 0.5) * yJit, Math.sin(ang) * sp, role,
                    r, g, b, alpha * (0.7f + 0.3f * R.nextFloat()), width * (0.75f + 0.5f * R.nextFloat()), blur, lifeLo + R.nextInt(Math.max(1, lifeHi - lifeLo)),
                    light(level, x, y, z)));
        }
    }

    /**
     * [blizzard2] Snow driving at the player's face: a few large, long streaks spawned 2.5-7 blocks upwind of the camera
     * (within +-3 blocks across the wind, -1..+2 around the eyes) that blow past the camera. Few, big sprites instead
     * of many tiny ones; they fade within ~1 block of the eye so none ever fills the screen.
     */
    private static void nearStreaks(ParticleEngine pe, ClientLevel level, Vec3 cp, int target, float speed) {
        int live = WeatherParticles.LIVE[WeatherParticles.NEAR];
        if (target <= 0 || live >= target) return;
        int n = Math.min(target - live, Math.max(2, target / 4));
        float wx = WeatherClient.windUnitX, wz = WeatherClient.windUnitZ;
        for (int i = 0; i < n; i++) {
            double back = 2.5 + R.nextDouble() * 4.5, side = (R.nextDouble() - 0.5) * 6.0;
            double x = cp.x - wx * back - wz * side, z = cp.z - wz * back + wx * side;
            double y = cp.y - 1.0 + R.nextDouble() * 3.0;
            if (!open(level, x, y, z, 0f)) continue;
            double ang = Math.atan2(wz, wx) + (R.nextDouble() - 0.5) * 0.3;
            double sp = speed * (0.85 + 0.3 * R.nextDouble());
            int life = (int) Math.max(4, Math.min(16, (back * 2.0) / Math.max(0.3, sp)));
            pe.add(new WeatherParticles.Streak(level, x, y, z, Math.cos(ang) * sp, -0.05 + (R.nextDouble() - 0.5) * 0.1, Math.sin(ang) * sp, WeatherParticles.NEAR,
                    0.97f, 0.98f, 1f, 0.5f + 0.2f * R.nextFloat(), 0.07f + 0.05f * R.nextFloat(), 1.15f, life, light(level, x, y, z)));
        }
    }

    private static void rain(ParticleEngine pe, ClientLevel level, Vec3 cp, int target, float radius, float drift) {
        int live = WeatherParticles.LIVE[WeatherParticles.RAIN];
        if (live >= target) return;
        int n = Math.min(target - live, Math.max(3, target / 6));
        float wx = WeatherClient.windUnitX, wz = WeatherClient.windUnitZ;
        for (int i = 0; i < n; i++) {
            double a = R.nextDouble() * Math.PI * 2.0, rad = radius * Math.sqrt(R.nextDouble());
            double x = cp.x + Math.cos(a) * rad - wx * 2.0, z = cp.z + Math.sin(a) * rad - wz * 2.0;
            double y = cp.y + 3.0 + R.nextDouble() * 10.0;
            if (!open(level, x, y, z, 0f)) continue;
            double vy = -(1.0 + 0.3 * R.nextDouble());
            pe.add(new WeatherParticles.Streak(level, x, y, z, wx * drift, vy, wz * drift, WeatherParticles.RAIN, 0.68f, 0.74f, 0.84f,
                    0.2f + 0.12f * R.nextFloat(), 0.018f + 0.01f * R.nextFloat(), 1.25f, 14 + R.nextInt(8), light(level, x, y, z)));
        }
    }

    private static void flakes(ParticleEngine pe, ClientLevel level, Vec3 cp, int target, float radius, float speed, float s) {
        int live = WeatherParticles.LIVE[WeatherParticles.FLAKE];
        if (live >= target) return;
        int n = Math.min(target - live, Math.max(2, target / 8));
        float wx = WeatherClient.windUnitX, wz = WeatherClient.windUnitZ;
        for (int i = 0; i < n; i++) {
            double x = cp.x + (R.nextDouble() - 0.5) * 2 * radius - wx * radius * 0.5;
            double z = cp.z + (R.nextDouble() - 0.5) * 2 * radius - wz * radius * 0.5;
            double y = cp.y + (R.nextDouble() - 0.4) * 4.0;
            if (!open(level, x, y, z, 0.4f)) continue;
            double sp = speed * (0.7 + 0.6 * R.nextDouble());
            pe.add(new WeatherParticles.Flake(level, x, y, z, wx * sp + (R.nextDouble() - 0.5) * 0.08, -0.04 - R.nextDouble() * 0.05,
                    wz * sp + (R.nextDouble() - 0.5) * 0.08, WeatherParticles.FLAKE, 0.97f, 0.98f, 1f, 0.85f, 0.03f + R.nextFloat() * 0.035f,
                    14 + R.nextInt(14), light(level, x, y, z)));
        }
    }

    private static boolean snowSurface(BlockState st) {
        return st.is(Blocks.SNOW) || st.is(Blocks.SNOW_BLOCK) || st.is(Blocks.POWDER_SNOW);
    }

    /** Blowing ground snow / sand: low, long, soft streams hugging the surface. */
    private static void drift(ParticleEngine pe, ClientLevel level, Vec3 cp, int target, float radius, float speed, float r, float g, float b, float alpha, boolean snowOnly,
                              float lift) {
        int live = WeatherParticles.LIVE[WeatherParticles.DRIFT];
        if (live >= target) return;
        int n = Math.min(target - live, Math.max(2, target / 8));
        float wx = WeatherClient.windUnitX, wz = WeatherClient.windUnitZ;
        for (int i = 0; i < n; i++) {
            double a = R.nextDouble() * Math.PI * 2.0, rad = radius * Math.sqrt(R.nextDouble());
            double x = cp.x + Math.cos(a) * rad - wx * radius * 0.3, z = cp.z + Math.sin(a) * rad - wz * radius * 0.3;
            int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
            if (!level.hasChunk(bx >> 4, bz >> 4)) continue;
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
            if (Math.abs(h - cp.y) > 7.0) continue;
            M.set(bx, h - 1, bz);
            BlockState top = level.getBlockState(M);
            if (snowOnly && !snowSurface(top)) continue;
            if (!snowOnly && top.is(BlockTags.LEAVES)) continue;
            double y = (top.is(Blocks.SNOW) ? h - 1 + 0.15 : h) + 0.04 + R.nextDouble() * R.nextDouble() * lift;
            double sp = speed * (0.75 + 0.5 * R.nextDouble());
            double ang = Math.atan2(wz, wx) + (R.nextDouble() - 0.5) * 0.3;
            float wide = snowOnly ? 1.25f : 1f; // [blizzard2] heavier, wider snow drift streams
            pe.add(new WeatherParticles.Streak(level, x, y, z, Math.cos(ang) * sp, 0.004 + R.nextDouble() * 0.012 * lift / 0.6, Math.sin(ang) * sp, WeatherParticles.DRIFT,
                    r, g, b, alpha * (0.6f + 0.4f * R.nextFloat()), (0.16f + 0.14f * R.nextFloat()) * wide, 2.2f, 16 + R.nextInt(16), light(level, x, h + 0.5, z)));
        }
    }

    /** Wind storm ground layer: snow streams on snow, dust on bare dry ground, nothing on grass. */
    private static void dustOrSnowDrift(ParticleEngine pe, ClientLevel level, Vec3 cp, int target, float radius, float speed) {
        int live = WeatherParticles.LIVE[WeatherParticles.DRIFT];
        if (live >= target) return;
        int n = Math.min(target - live, 3);
        float wx = WeatherClient.windUnitX, wz = WeatherClient.windUnitZ;
        for (int i = 0; i < n; i++) {
            double a = R.nextDouble() * Math.PI * 2.0, rad = radius * Math.sqrt(R.nextDouble());
            double x = cp.x + Math.cos(a) * rad - wx * radius * 0.3, z = cp.z + Math.sin(a) * rad - wz * radius * 0.3;
            int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
            if (!level.hasChunk(bx >> 4, bz >> 4)) continue;
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
            if (Math.abs(h - cp.y) > 7.0) continue;
            M.set(bx, h - 1, bz);
            BlockState top = level.getBlockState(M);
            boolean snow = snowSurface(top);
            boolean dusty = top.is(BlockTags.SAND) || top.is(Blocks.GRAVEL) || top.is(Blocks.COARSE_DIRT) || top.is(Blocks.DIRT) || top.is(Blocks.DIRT_PATH)
                    || top.is(Blocks.FARMLAND) || top.is(Blocks.ROOTED_DIRT) || top.is(BlockTags.TERRACOTTA);
            if (!snow && !dusty) continue;
            double y = (top.is(Blocks.SNOW) ? h - 1 + 0.15 : h) + 0.04 + R.nextDouble() * R.nextDouble() * 0.7;
            double sp = speed * (0.75 + 0.5 * R.nextDouble());
            double ang = Math.atan2(wz, wx) + (R.nextDouble() - 0.5) * 0.3;
            float r = snow ? 0.97f : 0.72f, g = snow ? 0.98f : 0.64f, b = snow ? 1f : 0.52f;
            pe.add(new WeatherParticles.Streak(level, x, y, z, Math.cos(ang) * sp, 0.006 + R.nextDouble() * 0.014, Math.sin(ang) * sp, WeatherParticles.DRIFT,
                    r, g, b, (snow ? 0.26f : 0.18f) * (0.6f + 0.4f * R.nextFloat()), 0.18f + 0.16f * R.nextFloat(), 2.2f, 16 + R.nextInt(18), light(level, x, h + 0.5, z)));
        }
    }

    /**
     * Veils: big soft billboards spread through a shell 3-30 blocks around the camera. {@code maxY} keeps fog veils
     * inside the fog bank (NaN = no ceiling).
     */
    private static void veils(ParticleEngine pe, ClientLevel level, Vec3 cp, int target, float r, float g, float b, float alpha,
                              float dMin, float dMax, float elevLoDeg, float elevHiDeg, float driftScale, float sizeBase, float sizeK,
                              int lifeLo, int lifeHi, float maxY) {
        if (target <= 0 || alpha <= 0.005f) return;
        int live = WeatherParticles.LIVE[WeatherParticles.VEIL];
        if (live >= target) return;
        int n = Math.min(target - live, 3);
        float wx = WeatherClient.windUnitX, wz = WeatherClient.windUnitZ;
        float drift = driftScale * WeatherClient.windPresent * 0.25f;
        for (int i = 0; i < n; i++) {
            double yaw = R.nextDouble() * Math.PI * 2.0;
            double el = Math.toRadians(elevLoDeg + R.nextDouble() * (elevHiDeg - elevLoDeg));
            double d = dMin + (dMax - dMin) * Math.pow(R.nextDouble(), 1.4);
            double x = cp.x + Math.cos(el) * Math.cos(yaw) * d, z = cp.z + Math.cos(el) * Math.sin(yaw) * d;
            double y = cp.y + Math.sin(el) * d;
            if (!Float.isNaN(maxY) && y > maxY) y = maxY - R.nextDouble() * 5.0;
            int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
            if (!level.hasChunk(bx >> 4, bz >> 4)) continue;
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
            if (y < h - 2.0) {
                M.set(bx, h - 1, bz);
                if (!level.getBlockState(M).is(BlockTags.LEAVES)) continue;
            }
            M.set(x, y, z);
            if (level.getBlockState(M).canOcclude()) continue;
            float size = (float) (sizeBase + sizeK * d) * (0.8f + 0.4f * R.nextFloat());
            pe.add(new WeatherParticles.Veil(level, x, y, z, wx * drift + (R.nextDouble() - 0.5) * 0.01, (R.nextDouble() - 0.5) * 0.006,
                    wz * drift + (R.nextDouble() - 0.5) * 0.01, r, g, b, alpha * (0.75f + 0.5f * R.nextFloat()), size,
                    lifeLo + R.nextInt(Math.max(1, lifeHi - lifeLo)), light(level, x, Math.max(y, h), z)));
        }
    }

    /** Diamond dust: tiny ice crystals glinting in freezing fog. */
    private static void sparkles(ParticleEngine pe, ClientLevel level, Vec3 cp, int target) {
        int live = WeatherParticles.LIVE[WeatherParticles.SPARKLE];
        if (live >= target) return;
        int n = Math.min(target - live, 4);
        float wx = WeatherClient.windUnitX, wz = WeatherClient.windUnitZ;
        for (int i = 0; i < n; i++) {
            double x = cp.x + (R.nextDouble() - 0.5) * 12.0, z = cp.z + (R.nextDouble() - 0.5) * 12.0, y = cp.y + (R.nextDouble() - 0.35) * 5.0;
            if (!open(level, x, y, z, 0.5f)) continue;
            pe.add(new WeatherParticles.Flake(level, x, y, z, wx * 0.012, -0.004 - R.nextDouble() * 0.004, wz * 0.012, WeatherParticles.SPARKLE,
                    0.92f, 0.96f, 1f, 0.9f, 0.018f + R.nextFloat() * 0.018f, 40 + R.nextInt(40), 0xF000F0));
        }
    }

    /** Leaves torn off the trees (or lifted off the forest floor in fall) and carried downwind, tumbling. */
    private static void leaves(ParticleEngine pe, ClientLevel level, Vec3 cp, int target, float s) {
        int live = WeatherParticles.LIVE[WeatherParticles.LEAF];
        if (live >= target) return;
        int budget = Math.min(target - live, 4);
        float wx = WeatherClient.windUnitX, wz = WeatherClient.windUnitZ;
        float[][] palette = switch (WeatherClient.season) {
            case FALL -> FALL_LEAVES;
            case WINTER -> DEAD_LEAVES;
            default -> GREEN_LEAVES;
        };
        boolean litter = WeatherClient.season == SeasonClock.Season.FALL || WeatherClient.season == SeasonClock.Season.WINTER;
        for (int tries = 0; tries < 10 && budget > 0; tries++) {
            double a = R.nextDouble() * Math.PI * 2.0, rad = 16.0 * Math.sqrt(R.nextDouble());
            double x = cp.x + Math.cos(a) * rad - wx * 8.0, z = cp.z + Math.sin(a) * rad - wz * 8.0;
            double y = cp.y - 2.0 + R.nextDouble() * 12.0;
            int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
            if (!level.hasChunk(bx >> 4, bz >> 4)) continue;
            M.set(x, y, z);
            BlockState st = level.getBlockState(M);
            double sx = x, sy = y, sz = z;
            if (st.is(BlockTags.LEAVES)) {
                // step out of the canopy on the downwind side so the leaf does not spawn inside a block
                sx += wx * 0.8;
                sz += wz * 0.8;
                sy -= 0.4;
            } else if (litter && R.nextFloat() < 0.35f) {
                int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
                M.set(bx, h - 1, bz);
                BlockState top = level.getBlockState(M);
                if (!(top.is(Blocks.GRASS_BLOCK) || top.is(Blocks.PODZOL) || top.is(Blocks.DIRT) || top.is(Blocks.COARSE_DIRT) || top.is(Blocks.MOSS_BLOCK))) continue;
                if (Math.abs(h - cp.y) > 8.0 || !WeatherClient.openSky(level, bx, h, bz)) continue;
                sy = h + 0.1;
            } else {
                continue;
            }
            M.set(sx, sy, sz);
            if (level.getBlockState(M).canOcclude()) continue;
            float[] c = palette[R.nextInt(palette.length)];
            float shade = 0.85f + 0.15f * R.nextFloat();
            double push = WeatherClient.leafPush;
            pe.add(new WeatherParticles.Leaf(level, sx, sy, sz, wx * push * 0.6, 0.04 + R.nextDouble() * 0.06, wz * push * 0.6,
                    c[0] * shade, c[1] * shade, c[2] * shade, 0.06f + R.nextFloat() * 0.05f, 90 + R.nextInt(90), light(level, sx, sy, sz)));
            budget--;
        }
    }
}
