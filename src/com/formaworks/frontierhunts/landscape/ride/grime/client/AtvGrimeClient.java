package com.formaworks.frontierhunts.landscape.ride.grime.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.landscape.ride.Atv;
import com.formaworks.frontierhunts.landscape.ride.grime.AtvGrime;
import com.formaworks.frontierhunts.landscape.ride.grime.AtvGrimeConfig;
import com.formaworks.frontierhunts.landscape.ride.grime.AtvGrimeNet;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Client side of ATV grime: receives the server snapshots, smooths the wet/dry look, and per ATV tick throws the wheel
 * spray (mud clods + mist, snow chunks + powder, water drops + spray mist) and feeds the driver's screen splatter.
 * Everything here is cosmetic and derived from what this client sees; nothing is sent back.
 */
public final class AtvGrimeClient {
    /** Snapshots that arrived before their ATV existed on this client (rare; consumed on the ATV's first tick). */
    private static final Int2ObjectOpenHashMap<AtvGrimeNet.Sync> PENDING = new Int2ObjectOpenHashMap<>();
    private static final AtvGrime.Sample SAMPLE = new AtvGrime.Sample();
    private static final RandomSource RND = RandomSource.create();

    private AtvGrimeClient() {}

    static void sync(AtvGrimeNet.Sync s) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Entity e = level.getEntity(s.entity());
        if (e instanceof Atv atv) {
            atv.grime.accept(s);
        } else if (PENDING.size() < 256) {
            PENDING.put(s.entity(), s);
        }
    }

    static void tick(Atv atv) {
        AtvGrime g = atv.grime;
        if (g.clientAge == 0 && !PENDING.isEmpty()) {
            AtvGrimeNet.Sync s = PENDING.remove(atv.getId());
            if (s != null) g.accept(s);
        }
        g.clientAge++;
        g.shownMoist += (g.moist - g.shownMoist) * 0.04f;
        g.shownWet += (g.wet - g.shownWet) * 0.08f;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || atv.level() != level) return;
        boolean driver = atv.getControllingPassenger() == player;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double d2 = cam.distanceToSqr(atv.getX(), atv.getY(), atv.getZ());
        if (!driver && d2 > 56.0 * 56.0) {
            g.lastDepth = 0f;
            return;
        }
        g.refreshClimate(level, atv);
        AtvGrime.Sample s = SAMPLE;
        AtvGrime.sample(level, atv, g.wetGround(), g.wetBiome(), s);
        float sp = (float) Math.abs(atv.shownSpeed);
        boolean sprayOn = AtvGrimeConfig.spray() && AtvGrimeParticles.ready();
        float dist = d2 < 16 * 16 ? 1f : d2 < 32 * 32 ? 0.5f : 0.25f;
        float q = sprayOn ? AtvGrimeParticles.quality() * dist : 0f;
        if (sprayOn) {
            spray(level, atv, g, s, sp, q);
        }
        AtvWaterFx.tick(level, atv, g, s, sp, q, sprayOn, d2); // [atv2] wheel fans, bow wave, wake, entry splash, water sounds
        if (driver) {
            ScreenSplatter.feed(s, sp, g.lastDepth, (float) atv.shownSpeed);
        }
        g.lastDepth = s.depth;
    }

    private static float smooth(float e0, float e1, float x) {
        float t = Mth.clamp((x - e0) / (e1 - e0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    // ================================================================== wheel spray
    private static void spray(ClientLevel level, Atv atv, AtvGrime g, AtvGrime.Sample s, float sp, float q) {
        float dir = atv.shownSpeed >= 0 ? 1f : -1f;
        double fx = s.fx, fz = s.fz, rx = s.rx, rz = s.rz;
        double vfx = fx * atv.shownSpeed, vfz = fz * atv.shownSpeed; // the ATV's own velocity
        double y = atv.getY();
        float move = s.depth < 0.3f ? smooth(0.06f, 0.7f, sp) : 0f; // nothing flies off a tyre under water

        for (int i = 0; i < 4; i++) {
            boolean front = i < 2;
            float side = (i & 1) == 0 ? -1f : 1f;
            double wx = s.wx[i], wz = s.wz[i];
            // thrown off the trailing edge of the tyre
            double bx = -fx * dir, bz = -fz * dir;

            // ---------------------------------------------------------------- mud
            float m = s.mud[i];
            if (m > 0.04f && move > 0f) {
                g.emitMud += m * move * (front ? 0.75f : 1f) * 1.1f * q;
                while (g.emitMud >= 1f) {
                    g.emitMud -= 1f;
                    if (!AtvGrimeParticles.room(AtvGrimeParticles.CLOD, q)) {
                        g.emitMud = 0f;
                        break;
                    }
                    float out = (front ? 0.45f : 0.2f) * RND.nextFloat();
                    double px = wx + bx * 0.3 + rx * side * 0.1 + (RND.nextFloat() - 0.5) * 0.18;
                    double pz = wz + bz * 0.3 + rz * side * 0.1 + (RND.nextFloat() - 0.5) * 0.18;
                    double py = y + 0.22 + RND.nextFloat() * 0.3;
                    double back = (0.06 + 0.24 * RND.nextFloat()) * (0.5 + sp);
                    double up = (0.10 + 0.24 * RND.nextFloat()) * (0.55 + 0.6 * sp);
                    double vx = bx * back + rx * side * out * 0.25 + vfx * 0.4;
                    double vz = bz * back + rz * side * out * 0.25 + vfz * 0.4;
                    float shade = 0.85f + RND.nextFloat() * 0.3f;
                    float wetness = 0.55f + 0.45f * g.shownMoist;
                    float r = (0.46f - 0.08f * wetness) * shade, gg = (0.38f - 0.08f * wetness) * shade, b = (0.30f - 0.07f * wetness) * shade;
                    float size = 0.035f + RND.nextFloat() * RND.nextFloat() * 0.08f;
                    AtvGrimeParticles.add(new AtvGrimeParticles.Clod(level, px, py, pz, vx, up, vz, r, gg, b, size, 40 + RND.nextInt(30)), AtvGrimeParticles.CLOD);
                    if (RND.nextFloat() < 0.35f && AtvGrimeParticles.room(AtvGrimeParticles.MIST, q)) {
                        AtvGrimeParticles.add(new AtvGrimeParticles.Mist(level, px, py - 0.1, pz, vx * 0.5, up * 0.25, vz * 0.5,
                                0.42f, 0.35f, 0.28f, 0.28f, 0.18f + RND.nextFloat() * 0.12f, 1.6f, 14 + RND.nextInt(10), 0.01f), AtvGrimeParticles.MIST);
                    }
                }
            }

            // ---------------------------------------------------------------- snow: more powder than chunks
            float sn = s.snow[i];
            if (sn > 0.04f && move > 0f) {
                g.emitSnow += sn * move * (front ? 0.8f : 1f) * 1.4f * q;
                while (g.emitSnow >= 1f) {
                    g.emitSnow -= 1f;
                    if (!AtvGrimeParticles.room(AtvGrimeParticles.MIST, q)) {
                        g.emitSnow = 0f;
                        break;
                    }
                    double px = wx + bx * 0.3 + (RND.nextFloat() - 0.5) * 0.25;
                    double pz = wz + bz * 0.3 + (RND.nextFloat() - 0.5) * 0.25;
                    double py = y + 0.15 + RND.nextFloat() * 0.3;
                    double back = (0.05 + 0.18 * RND.nextFloat()) * (0.5 + sp);
                    double up = (0.06 + 0.16 * RND.nextFloat()) * (0.5 + 0.6 * sp);
                    double vx = bx * back + vfx * 0.45 + rx * side * 0.03, vz = bz * back + vfz * 0.45 + rz * side * 0.03;
                    AtvGrimeParticles.add(new AtvGrimeParticles.Mist(level, px, py, pz, vx, up * 0.6, vz, 0.97f, 0.98f, 1f,
                            0.5f + 0.25f * RND.nextFloat(), 0.2f + RND.nextFloat() * 0.18f, 2.2f, 22 + RND.nextInt(18), 0.012f), AtvGrimeParticles.MIST);
                    if (RND.nextFloat() < 0.45f && AtvGrimeParticles.room(AtvGrimeParticles.CLOD, q)) {
                        float size = 0.03f + RND.nextFloat() * 0.05f;
                        AtvGrimeParticles.add(new AtvGrimeParticles.Clod(level, px, py, pz, vx, up, vz, 1f, 1f, 1f, size, 25 + RND.nextInt(20)), AtvGrimeParticles.CLOD);
                    }
                }
            }
        }

        // ---------------------------------------------------------------- [atv2] water spray lives in AtvWaterFx; a wet ATV drips
        if (s.depth < 0.04f && g.shownWet > 0.35f && sp < 0.3f && RND.nextFloat() < 0.12f * g.shownWet * Math.min(q, 1f)
                && AtvGrimeParticles.room(AtvGrimeParticles.DROP, q)) {
            // a wet ATV drips from its fenders for a while
            int i = RND.nextInt(4);
            AtvGrimeParticles.add(new AtvGrimeParticles.Drop(level, s.wx[i] + (RND.nextFloat() - 0.5) * 0.3, y + 0.55, s.wz[i] + (RND.nextFloat() - 0.5) * 0.3,
                    0, -0.02, 0, 0.025f, 25), AtvGrimeParticles.DROP);
        }
    }

    // ================================================================== registration
    @EventBusSubscriber(modid = FrontierHunts.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        private Setup() {}

        @SubscribeEvent
        public static void setup(FMLClientSetupEvent event) {
            AtvGrime.clientTick = AtvGrimeClient::tick;
            AtvGrime.clientSync = AtvGrimeClient::sync;
        }
    }

    @EventBusSubscriber(modid = FrontierHunts.ID, value = Dist.CLIENT)
    public static final class GameEvents {
        private GameEvents() {}

        @SubscribeEvent
        public static void tick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                if (!PENDING.isEmpty()) PENDING.clear();
                return;
            }
            if (!mc.isPaused()) {
                AtvGrimeParticles.endTick();
                ScreenSplatter.tick();
            }
            if (!PENDING.isEmpty() && mc.level.getGameTime() % 200 == 0) PENDING.clear();
        }

        @SubscribeEvent
        public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
            PENDING.clear();
            AtvGrimeParticles.reset();
            ScreenSplatter.clear();
            AtvWaterFx.clear(); // [atv2]
        }
    }
}
