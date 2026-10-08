package com.formaworks.frontierhunts.weather.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.season.SeasonClock;
import com.formaworks.frontierhunts.weather.Climate;
import com.formaworks.frontierhunts.weather.SeasonalWeather;
import com.formaworks.frontierhunts.weather.Storm;
import com.formaworks.frontierhunts.weather.WeatherConfig;
import com.formaworks.frontierhunts.weather.WeatherKind;
import com.mojang.blaze3d.shaders.FogShape;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Client side of seasonal weather: turns the storms the server synced into what this player sees and hears at the
 * camera. Per-kind severities are smoothed over a few seconds (no pops when a storm arrives, when walking across a
 * biome edge or stepping indoors), then drive:
 * <ul>
 *   <li>vanilla fog distance, shape and colour (terrain, entities and sky) - {@link #fog}, {@link #fogColor}</li>
 *   <li>particles ({@link WeatherEffects}), the shader-independent screen layer ({@link WeatherOverlay}) and sound
 *   ({@link WeatherAudio})</li>
 * </ul>
 * All state is a handful of floats; nothing here allocates per frame.
 */
@EventBusSubscriber(modid = FrontierHunts.ID, value = Dist.CLIENT)
public final class WeatherClient {
    static final int K = WeatherKind.VALUES.length;
    static final int BLIZZARD = WeatherKind.BLIZZARD.ordinal(), THUNDER = WeatherKind.THUNDERSTORM.ordinal(), SQUALL = WeatherKind.SQUALL.ordinal(),
            DUST = WeatherKind.DUST_STORM.ordinal(), FOG = WeatherKind.FOG.ordinal(), FFOG = WeatherKind.FREEZING_FOG.ordinal(), WIND = WeatherKind.WIND_STORM.ordinal();

    /** Day colours of each event's air (index = kind ordinal). */
    static final float[][] COLOR = {
            {0f, 0f, 0f},
            {0.74f, 0.77f, 0.82f}, // blizzard: cold grey-white ([blizzard2] darker; darkens further with strength)
            {0.30f, 0.33f, 0.38f}, // thunderstorm: slate
            {0.46f, 0.49f, 0.53f}, // squall: grey rain curtain
            {0.74f, 0.53f, 0.32f}, // dust storm: orange-brown
            {0.76f, 0.78f, 0.80f}, // fog
            {0.79f, 0.83f, 0.88f}, // freezing fog: cold blue-white
            {0.70f, 0.68f, 0.63f}, // wind storm: faint dusty haze
    };
    /** How much each kind is allowed to recolour the fog. */
    static final float[] COLOR_WEIGHT = {0f, 1f, 0.9f, 0.85f, 1f, 1f, 1f, 0.3f};

    // smoothed per-kind severity at the camera (already includes climate applicability, before shelter gates)
    static final float[] sev = new float[K], prevSev = new float[K];
    private static final float[] target = new float[K];
    /** Fog bank (possibly below the camera) for fog veils when looking down on a foggy valley. */
    static float fogLayer, fogTop = Float.NaN;
    /**
     * 0..1 open sky around the camera (1 outdoors, ~0 under a roof). Leaves do not count as a roof.
     * [shelter] Now 1 - enclosure from the shared shelter detector ({@code shelter.client.ShelterClient}): tents, blinds,
     * cabins and caves, doorways partial, eased over ~1-2 s.
     */
    static float exposure = 1f;
    /** [shelter] 1 - exposure, read by particles while rendering (near-camera fade inside shelter). */
    static volatile float nearShelter = 0f;
    /** 0..1 sky light gate (0 deep in caves). */
    static float underground = 1f;
    static float fogGate = 1f, prevFogGate = 1f;
    /** Read by veil particles while rendering. */
    static volatile float veilGate = 1f;
    static float windE, windS, windUnitX = 1f, windUnitZ = 0f, windPresent = 2f;
    static float gust, prevGust;
    static float leafPush;
    static float colR, colG, colB, colW, prevColR, prevColG, prevColB, prevColW;
    static float frost, prevFrost;
    /** [blizzard2] 0..1 whiteout gust: a brief wall of driven snow that drops blizzard visibility to ~2 blocks. */
    static float whiteout, prevWhiteout;
    /** [blizzard2] Set on the tick a whiteout gust begins (audio plays the big howl). */
    static boolean whiteoutStarted;
    private static int woTicks, woLen, woCooldown;
    /** [blizzard2] Frost never goes past this (it was ~0.85); the vignette is a hint at the edges, not a wall. */
    static final float FROST_MAX = 0.5f;
    static float eyeHeight = 1.62f;
    static float minVis = 5f;
    static boolean active;
    static int quality = 1; // 0 performance, 1 balanced, 2 cinematic
    static SeasonClock.Season season = SeasonClock.Season.SUMMER;

    private static ClientLevel lastLevel;
    private static int ticks;
    private static final Climate CLIMATE = new Climate();
    private static final BlockPos.MutableBlockPos PROBE = new BlockPos.MutableBlockPos();

    private WeatherClient() {}

    static void reset() {
        Arrays.fill(sev, 0f);
        Arrays.fill(prevSev, 0f);
        Arrays.fill(target, 0f);
        fogLayer = 0f;
        fogTop = Float.NaN;
        frost = prevFrost = 0f;
        whiteout = prevWhiteout = 0f;
        woTicks = woLen = woCooldown = 0;
        whiteoutStarted = false;
        colW = prevColW = 0f;
        active = false;
        exposure = 1f;
        underground = 1f;
    }

    // ================================================================== tick
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            if (lastLevel != null) {
                lastLevel = null;
                reset();
                SeasonalWeather.clearClient();
                WeatherAudio.stopAll(mc);
                WeatherParticles.resetCounts();
            }
            return;
        }
        if (level != lastLevel) {
            lastLevel = level;
            reset();
            WeatherParticles.resetCounts();
            WeatherAudio.stopAll(mc);
        }
        if (mc.isPaused()) return;
        // [integration] never smooth toward the darkroom's borrowed time/weather (it only lives inside a render frame)
        if (com.formaworks.frontierhunts.client.trailcam.Darkroom.active()) return;
        ticks++;
        WeatherParticles.endTick();
        long t = level.getGameTime();
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 cp = cam.isInitialized() ? cam.getPosition() : mc.player.getEyePosition();
        BlockPos bp = BlockPos.containing(cp);
        minVis = SeasonalWeather.blizzardVisibility(level);

        // ---- targets from the synced storms
        Arrays.fill(target, 0f);
        float layer = 0f, top = Float.NaN;
        List<Storm> storms = SeasonalWeather.storms(level);
        if (!storms.isEmpty() && level.dimensionType().hasSkyLight()) {
            boolean sampled = false;
            float w = 0f, rain = level.getRainLevel(1f);
            for (int i = 0; i < storms.size(); i++) {
                Storm s = storms.get(i);
                float raw = s.raw(t, cp.x, cp.z);
                if (raw <= 0.001f) continue;
                if (!sampled) {
                    CLIMATE.sample(level, bp);
                    w = SeasonClock.winterness(level);
                    sampled = true;
                }
                float v = SeasonalWeather.severity(s, level, bp, CLIMATE, w, t);
                int k = s.kind.ordinal();
                if (v > target[k]) target[k] = v;
                if (!Float.isNaN(s.top)) {
                    float l = raw * (s.forced ? 1f : CLIMATE.applicability(s.kind, w, rain));
                    if (l > layer) { layer = l; top = s.top; }
                }
            }
        }
        boolean any = false;
        for (int k = 1; k < K; k++) {
            prevSev[k] = sev[k];
            float d = target[k] - sev[k];
            sev[k] += d * (d > 0f ? 0.022f : 0.016f); // ~2.3 s in, ~3 s out
            if (target[k] == 0f && sev[k] < 0.002f) sev[k] = 0f;
            if (sev[k] > 0f) any = true;
        }
        fogLayer += (layer - fogLayer) * 0.02f;
        if (!Float.isNaN(top)) fogTop = top;
        if (fogLayer < 0.003f && layer == 0f) fogLayer = 0f;
        active = any || fogLayer > 0f;

        // [shelter] shelter from the shared detector, every tick (also while calm, so a storm starting finds it right)
        exposure = com.formaworks.frontierhunts.shelter.client.ShelterClient.open();
        nearShelter = 1f - exposure;

        prevFogGate = fogGate;
        prevGust = gust;
        prevColR = colR; prevColG = colG; prevColB = colB; prevColW = colW;
        prevFrost = frost;
        prevWhiteout = whiteout;
        whiteoutStarted = false;
        if (!active) {
            frost = Math.max(0f, frost - 0.01f);
            whiteout = Math.max(0f, whiteout - 0.05f);
            woTicks = 0;
            veilGate = 1f;
            WeatherOverlay.tick(false);
            WeatherAudio.tick(mc, level, cp);
            return;
        }
        season = SeasonClock.season(level);
        quality = qualityLevel();

        // ---- shelter
        if (ticks % 4 == 0) shelter(level, cp, bp);
        fogGate = underground * (0.3f + 0.7f * exposure); // [shelter] indoors the storm fog relaxes further (was 0.45 + 0.55 x)
        veilGate = Math.max(0.05f, underground * (0.25f + 0.75f * exposure));

        // ---- wind and gusts
        float we = SeasonalWeather.windEast(level), ws = SeasonalWeather.windSouth(level);
        windE += (we - windE) * 0.05f;
        windS += (ws - windS) * 0.05f;
        float wl = (float) Math.sqrt(windE * windE + windS * windS);
        if (wl > 0.05f) { windUnitX = windE / wl; windUnitZ = windS / wl; }
        double sec = t / 20.0;
        float g = 0.5f + 0.3f * (float) Math.sin(sec * 0.6756 + 0.7)
                + 0.2f * (float) Math.sin(sec * 2.027 + 2.1) * (0.5f + 0.5f * (float) Math.sin(sec * 0.3696))
                + 0.16f * (float) Math.sin(sec * 0.2327 + 0.4);
        // [blizzard2] blizzards gust harder and more erratically: faster, deeper swings on top of the shared rhythm
        float bz = drive(sev[BLIZZARD]) * underground;
        if (bz > 0.01f) {
            g += bz * (0.16f * (float) Math.sin(sec * 3.13 + 1.3) * (float) Math.sin(sec * 0.517 + 0.2)
                    + 0.12f * (float) Math.sin(sec * 1.21 + 4.0));
            g = 0.5f + (g - 0.5f) * (1f + 0.35f * bz);
        }
        gust = Math.max(0f, Math.min(1f, g));
        whiteoutTick(bz * exposure);
        windPresent = Math.max(wl, Math.max(Math.max(4f + 7f * sev[BLIZZARD] + 3f * whiteout, 3.5f + 4f * sev[DUST]), Math.max(3f + 6f * sev[WIND], 2f + 3f * Math.max(sev[SQUALL], sev[THUNDER]))));
        leafPush = (0.06f + 0.035f * windPresent) * (0.45f + 0.75f * gust) * Math.min(1f, sev[WIND] * 1.5f + 0.2f);

        // ---- colour of the storm air
        float sky = level.getSkyDarken(1f);
        float day = Math.max(0f, Math.min(1f, (sky - 0.2f) / 0.8f));
        float lum = 0.05f + 0.95f * (float) Math.pow(day, 0.9);
        float flash = level.getSkyFlashTime() > 0 ? 0.35f : 0f;
        float r = 0f, gg = 0f, b = 0f, tot = 0f, maxW = 0f, wB = 0f;
        for (int k = 1; k < K; k++) {
            float e = sev[k] * fogGate;
            if (e <= 0.001f) continue;
            float wgt = (float) Math.pow(e, 0.7) * COLOR_WEIGHT[k];
            if (k == BLIZZARD) wB = wgt;
            r += COLOR[k][0] * wgt;
            gg += COLOR[k][1] * wgt;
            b += COLOR[k][2] * wgt;
            tot += wgt;
            maxW = Math.max(maxW, wgt);
        }
        if (tot > 0f) {
            // [blizzard2] a full blizzard swallows the daylight: up to ~20% darker air (a little brighter in a whiteout gust)
            float dark = 1f - (0.2f - 0.06f * whiteout) * (wB / tot) * drive(sev[BLIZZARD] * fogGate);
            colR = Math.min(1f, r / tot * lum * dark + 0.012f + flash * 0.8f);
            colG = Math.min(1f, gg / tot * lum * dark + 0.016f + flash * 0.82f);
            colB = Math.min(1f, b / tot * lum * dark + 0.03f + flash * 0.9f);
        }
        colW = Math.min(1f, maxW);

        // ---- frost builds on the edge of your vision while you stand in a blizzard
        // [blizzard2] toned down: max 0.5 (was ~0.85), slower to build (~35 s time constant, was 18 s), melts faster indoors
        // [shelter] blizzard only: freezing fog no longer frosts the screen (it is a calm morning event, not a storm)
        float ft = Math.min(FROST_MAX, drive(sev[BLIZZARD]) * FROST_MAX) * underground * exposure;
        frost += (ft - frost) * (ft > frost ? 1f / 700f : 1f / 120f);

        WeatherOverlay.tick(true);
        WeatherEffects.tick(mc, level, cp, t);
        WeatherAudio.tick(mc, level, cp);
    }

    /**
     * [shelter] 0..1 strength of a blizzard at the camera right now (smoothed, before shelter): lets other screen effects
     * (the survival frost) appear only in a real storm.
     */
    public static float blizzardAtCamera() {
        return drive(sev[BLIZZARD]) * underground;
    }

    /** [blizzard2] Blizzard presentation strength (see {@link SeasonalWeather#blizzardDrive}). */
    static float drive(float s) {
        return SeasonalWeather.blizzardDrive(s);
    }

    /**
     * [blizzard2] Whiteout gusts: on the rising edge of a strong gust during a heavy blizzard (outdoors), now and then
     * a wall of driven snow sweeps through - visibility drops to about 2 blocks for 2-4.5 s, then recovers. At most
     * one every 10-25 s. Purely presentation (client-side randomness is fine here).
     */
    private static void whiteoutTick(float strength) {
        if (woCooldown > 0) woCooldown--;
        float target = 0f;
        if (woTicks > 0) {
            woTicks--;
            int age = woLen - woTicks;
            target = Math.min(1f, age / 12f) * (woTicks > 25 ? 1f : woTicks / 25f);
            target *= Math.min(1f, strength * 1.4f);
        } else if (strength > 0.55f && woCooldown == 0 && gust > 0.72f && prevGust <= 0.72f
                && RNG.nextFloat() < 0.35f + 0.45f * strength) {
            woLen = woTicks = 40 + RNG.nextInt(51);
            woCooldown = woLen + 200 + RNG.nextInt(300);
            whiteoutStarted = true;
        }
        whiteout += (target - whiteout) * (target > whiteout ? 0.25f : 0.12f);
        if (whiteout < 0.002f && target == 0f) whiteout = 0f;
    }

    private static final java.util.Random RNG = new java.util.Random();

    private static int qualityLevel() {
        WeatherConfig.Quality q = WeatherConfig.quality();
        if (q == WeatherConfig.Quality.AUTO) {
            try {
                HuntConfig.Quality hq = HuntConfig.QUALITY.get();
                return hq == HuntConfig.Quality.PERFORMANCE ? 0 : hq == HuntConfig.Quality.CINEMATIC ? 2 : 1;
            } catch (Throwable ex) {
                return 1;
            }
        }
        return q == WeatherConfig.Quality.PERFORMANCE ? 0 : q == WeatherConfig.Quality.CINEMATIC ? 2 : 1;
    }

    /** Sky-light gate and eye height above the ground ([shelter] exposure: shared detector, see tick). */
    private static void shelter(ClientLevel level, Vec3 cp, BlockPos bp) {
        int cx = bp.getX(), cy = bp.getY(), cz = bp.getZ();
        // [shelter] exposure comes from the shared shelter detector now (see tick); the old 5-column open-sky test missed
        // tents (their canvas is at eye level, nothing above it) and partial shelter
        int skyLight = level.getBrightness(LightLayer.SKY, bp);
        float ut = Math.max(0f, Math.min(1f, (skyLight - 3) / 8f));
        underground += (ut - underground) * 0.3f;
        // eye height above ground (for the overlay's ground-distance profile)
        int ground = cy;
        for (int i = 0; i < 48; i++) {
            PROBE.set(cx, cy - i, cz);
            if (!level.getBlockState(PROBE).getCollisionShape(level, PROBE).isEmpty()) { ground = cy - i + 1; break; }
            ground = cy - i;
        }
        eyeHeight = (float) Math.max(1.0, Math.min(64.0, cp.y - ground));
    }

    static boolean openSky(ClientLevel level, int x, int y, int z) {
        int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        if (y >= h) return true;
        int stop = Math.min(h, y + 40);
        for (int yy = y + 1; yy < stop; yy++) {
            PROBE.set(x, yy, z);
            BlockState st = level.getBlockState(PROBE);
            if (st.blocksMotion() && !st.is(BlockTags.LEAVES)) return false;
        }
        return true;
    }

    // ================================================================== per-frame helpers
    static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    /** Severity of a kind at the camera this frame, gated by shelter for fog purposes. */
    static float fogAmount(int k, float pt) {
        return lerp(prevSev[k], sev[k], pt) * lerp(prevFogGate, fogGate, pt);
    }

    static float floorFor(int k) {
        return k == BLIZZARD ? minVis : WeatherKind.VALUES[k].peakVisibility;
    }

    private static boolean effectFog(Entity e) {
        return e instanceof LivingEntity le && (le.hasEffect(MobEffects.BLINDNESS) || le.hasEffect(MobEffects.DARKNESS));
    }

    // ================================================================== fog
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void fog(ViewportEvent.RenderFog event) {
        if (!active || event.getType() != FogType.NONE) return;
        // [integration] a trail-camera darkroom frame renders another place and moment (with its recorded vanilla
        // weather): the storm around the player must not fog the photo
        if (com.formaworks.frontierhunts.client.trailcam.Darkroom.active()) return;
        if (effectFog(event.getCamera().getEntity())) return;
        float pt = (float) event.getPartialTick();
        float vf = event.getFarPlaneDistance();
        float vn = event.getNearPlaneDistance();
        if (!(vf > 1f)) return;
        float far = vf, amount = 0f;
        double lnVf = Math.log(vf);
        for (int k = 1; k < K; k++) {
            float e = fogAmount(k, pt);
            if (e <= 0.002f) continue;
            // fog end a little past the "barely visible" distance, so things at the floor distance are faint silhouettes
            float floor = floorFor(k) * 1.3f;
            if (k == BLIZZARD) {
                // [blizzard2] fog end 1.1x the floor at the peak (5 -> 5.5 blocks: 3 blocks hazy, 5 nearly gone), halved
                // in a whiteout gust (~2.8 blocks); severity 0.87+ already counts as full strength
                e = drive(lerp(prevSev[k], sev[k], pt)) * lerp(prevFogGate, fogGate, pt);
                float wo = lerp(prevWhiteout, whiteout, pt) * lerp(prevFogGate, fogGate, pt);
                floor = floorFor(k) * 1.1f * (1f - 0.5f * wo);
                if (e <= 0.002f) continue;
            }
            if (floor >= vf) continue;
            float f = (float) Math.pow(Math.min(1f, e), 0.6);
            float fk = (float) Math.exp(lnVf + (Math.log(floor) - lnVf) * f);
            if (fk < far) far = fk;
            if (f > amount) amount = f;
        }
        if (far >= vf - 0.05f) return;
        float near = lerp(vn, -far * 0.05f, Math.min(1f, amount * 1.2f));
        near = Math.min(near, far * 0.85f);
        event.setFarPlaneDistance(far);
        event.setNearPlaneDistance(near);
        if (amount > 0.3f) event.setFogShape(FogShape.SPHERE);
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void fogColor(ViewportEvent.ComputeFogColor event) {
        if (!active || com.formaworks.frontierhunts.client.trailcam.Darkroom.active()) return; // [integration] darkroom frame
        Camera cam = event.getCamera();
        if (cam.getFluidInCamera() != FogType.NONE || effectFog(cam.getEntity())) return;
        float pt = (float) event.getPartialTick();
        float w = lerp(prevColW, colW, pt);
        if (w <= 0.002f) return;
        event.setRed(lerp(event.getRed(), lerp(prevColR, colR, pt), w));
        event.setGreen(lerp(event.getGreen(), lerp(prevColG, colG, pt), w));
        event.setBlue(lerp(event.getBlue(), lerp(prevColB, colB, pt), w));
    }

    /** Last world FOV (degrees) from the viewport event, for the overlay's screen-to-ground mapping. */
    static volatile double fov = 70.0;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void fov(ViewportEvent.ComputeFov event) {
        if (event.usedConfiguredFov()) fov = event.getFOV();
    }
}
