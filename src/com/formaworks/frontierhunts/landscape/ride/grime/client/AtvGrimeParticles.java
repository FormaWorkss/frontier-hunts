package com.formaworks.frontierhunts.landscape.ride.grime.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.landscape.ride.grime.AtvGrimeNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/**
 * Wheel spray particles: mud / snow clods (opaque cutout chunks with gravity, block collision and spin that settle and
 * shrink away), mist and powder puffs (soft translucent, growing and fading) and water drops (translucent, gravity,
 * gone on impact). Created directly from {@link AtvGrimeClient} with captured sprite sets; live counts are kept per
 * kind every tick so the budget stays exact.
 */
public final class AtvGrimeParticles {
    public static final int CLOD = 0, MIST = 1, DROP = 2, SPRAY = 3; // [atv2] + SPRAY
    static final int[] LIVE = new int[4];
    private static final int[] COUNTING = new int[4];
    static SpriteSet clodSprites, mistSprites, dropSprites, spraySprites;

    private AtvGrimeParticles() {}

    static boolean ready() {
        return clodSprites != null && mistSprites != null && dropSprites != null && spraySprites != null;
    }

    static void endTick() {
        System.arraycopy(COUNTING, 0, LIVE, 0, 4);
        java.util.Arrays.fill(COUNTING, 0);
    }

    static void reset() {
        java.util.Arrays.fill(LIVE, 0);
        java.util.Arrays.fill(COUNTING, 0);
    }

    /** Cosmetic budget factor: Effects quality x Minecraft's Particles setting. */
    static float quality() {
        float q;
        try {
            HuntConfig.Quality v = HuntConfig.QUALITY.get();
            q = v == HuntConfig.Quality.PERFORMANCE ? 0.4f : v == HuntConfig.Quality.CINEMATIC ? 1.7f : 1f;
        } catch (Throwable t) {
            q = 1f;
        }
        ParticleStatus ps = Minecraft.getInstance().options.particles().get();
        if (ps == ParticleStatus.DECREASED) q *= 0.5f;
        else if (ps == ParticleStatus.MINIMAL) q *= 0.15f;
        return q;
    }

    /** Live particle cap per kind for the current quality. */
    static int cap(int kind, float q) {
        int base = kind == CLOD ? 140 : kind == MIST ? 110 : kind == DROP ? 160 : 60; // [atv2] more room for water (mist/drops), spray sheets
        return Math.max(8, (int) (base * Math.min(q, 1.7f)));
    }

    static boolean room(int kind, float q) {
        return LIVE[kind] < cap(kind, q);
    }

    static void add(TextureSheetParticle p, int kind) {
        Minecraft.getInstance().particleEngine.add(p);
        LIVE[kind]++;
    }

    // ================================================================== clod
    static final class Clod extends TextureSheetParticle {
        private final float spin0;
        private float spin;
        private final float baseSize;
        private boolean landed;

        Clod(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, float r, float g, float b, float size, int life) {
            super(level, x, y, z);
            this.xd = vx;
            this.yd = vy;
            this.zd = vz;
            this.gravity = 1.0f;
            this.friction = 0.985f;
            this.hasPhysics = true;
            this.lifetime = life;
            this.quadSize = size;
            this.baseSize = size;
            this.rCol = r;
            this.gCol = g;
            this.bCol = b;
            this.roll = this.random.nextFloat() * 6.2832f;
            this.oRoll = this.roll;
            this.spin0 = (this.random.nextFloat() - 0.5f) * 0.7f;
            this.spin = spin0;
            this.pickSprite(clodSprites);
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
        }

        @Override
        public void tick() {
            COUNTING[CLOD]++;
            super.tick();
            if (this.removed) return;
            this.oRoll = this.roll;
            if (this.onGround) {
                if (!landed) {
                    landed = true;
                    // splat and stay a moment, then crumble away
                    this.lifetime = Math.min(this.lifetime, this.age + 18 + this.random.nextInt(22));
                    this.xd *= 0.2;
                    this.zd *= 0.2;
                }
                spin *= 0.5f;
            } else if (this.y < this.yo - 1e-4 && this.level.getFluidState(BlockPos.containing(this.x, this.y, this.z)).isSource()) {
                this.remove();
                return;
            }
            this.roll += spin;
            int left = this.lifetime - this.age;
            this.quadSize = baseSize * Math.min(1f, left / 8f) * (landed ? 0.85f : 1f);
        }
    }

    // ================================================================== mist / powder
    static final class Mist extends TextureSheetParticle {
        private final float a0, s0, grow, rollSpeed;

        Mist(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, float r, float g, float b, float alpha, float size,
             float grow, int life, float gravity) {
            super(level, x, y, z);
            this.xd = vx;
            this.yd = vy;
            this.zd = vz;
            this.gravity = gravity;
            this.friction = 0.9f;
            this.hasPhysics = false;
            this.lifetime = life;
            this.rCol = r;
            this.gCol = g;
            this.bCol = b;
            this.a0 = alpha;
            this.alpha = 0f;
            this.s0 = size;
            this.quadSize = size;
            this.grow = grow;
            this.roll = this.random.nextFloat() * 6.2832f;
            this.oRoll = this.roll;
            this.rollSpeed = (this.random.nextFloat() - 0.5f) * 0.06f;
            this.pickSprite(mistSprites);
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        @Override
        public void tick() {
            COUNTING[MIST]++;
            super.tick();
            if (this.removed) return;
            this.oRoll = this.roll;
            this.roll += rollSpeed;
            float t = (float) this.age / this.lifetime;
            this.quadSize = s0 * (1f + grow * (float) Math.sqrt(t));
            float in = Math.min(1f, this.age / 3f);
            float out = (float) Math.pow(1f - t, 1.4);
            this.alpha = a0 * in * out;
        }
    }

    // ================================================================== water drop
    static final class Drop extends TextureSheetParticle {
        Drop(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, float size, int life) {
            super(level, x, y, z);
            this.xd = vx;
            this.yd = vy;
            this.zd = vz;
            this.gravity = 1.1f;
            this.friction = 0.99f;
            this.hasPhysics = true;
            this.lifetime = life;
            this.quadSize = size;
            this.rCol = 0.86f;
            this.gCol = 0.9f;
            this.bCol = 0.96f;
            this.alpha = 0.75f;
            this.pickSprite(dropSprites);
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        @Override
        public void tick() {
            COUNTING[DROP]++;
            super.tick();
            if (this.removed) return;
            if (this.onGround) {
                if (this.random.nextInt(4) == 0) this.level.addParticle(ParticleTypes.SPLASH, this.x, this.y + 0.02, this.z, 0, 0, 0);
                this.remove();
                return;
            }
            if (this.yd < 0) {
                FluidState fs = this.level.getFluidState(BlockPos.containing(this.x, this.y, this.z));
                if (!fs.isEmpty()) {
                    if (this.random.nextInt(3) == 0) this.level.addParticle(ParticleTypes.SPLASH, this.x, this.y, this.z, 0, 0, 0);
                    this.remove();
                    return;
                }
            }
            int left = this.lifetime - this.age;
            if (left < 5) this.alpha = 0.75f * left / 5f;
        }
    }

    // ================================================================== spray plume [atv2]
    /**
     * A plume of spray thrown off a wheel or the bow wave: a translucent fan sprite whose top is its direction of travel.
     * Every tick it rolls itself so that top follows its velocity as seen from the camera (a streak that flies the way
     * it points, not a sideways slash), grows as it spreads and fades fast. Dies when it falls back into water.
     */
    static final class Spray extends TextureSheetParticle {
        private final float a0, s0, grow;

        Spray(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, float size, float grow, float alpha, int life, float gravity) {
            super(level, x, y, z);
            this.xd = vx;
            this.yd = vy;
            this.zd = vz;
            this.gravity = gravity;
            this.friction = 0.93f;
            this.hasPhysics = false;
            this.lifetime = life;
            this.rCol = 1f;
            this.gCol = 1f;
            this.bCol = 1f;
            this.a0 = alpha;
            this.alpha = alpha * 0.6f;
            this.s0 = size;
            this.quadSize = size * 0.7f;
            this.grow = grow;
            this.pickSprite(spraySprites);
            align();
            this.oRoll = this.roll;
        }

        private void align() {
            net.minecraft.client.Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
            org.joml.Vector3f l = cam.getLeftVector(), u = cam.getUpVector();
            double vl = this.xd * l.x() + this.yd * l.y() + this.zd * l.z();
            double vu = this.xd * u.x() + this.yd * u.y() + this.zd * u.z();
            if (vl * vl + vu * vu < 1e-7) return;
            float r = (float) Math.atan2(vl, vu);
            if (r == 0f) r = 1e-4f; // roll 0 skips the rotation entirely
            if (Math.abs(r - this.oRoll) > Math.PI) this.oRoll = r; // no spin through ±pi when interpolating
            this.roll = r;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        @Override
        public void tick() {
            COUNTING[SPRAY]++;
            super.tick();
            if (this.removed) return;
            this.oRoll = this.roll;
            align();
            float t = (float) this.age / this.lifetime;
            this.quadSize = s0 * (0.7f + 0.3f * Math.min(1f, this.age / 2f) + grow * (float) Math.sqrt(t));
            this.alpha = a0 * Math.min(1f, (this.age + 1) / 2f) * (float) Math.pow(1f - t, 1.6);
            if (this.yd < 0 && this.age > 3) {
                FluidState fs = this.level.getFluidState(BlockPos.containing(this.x, this.y, this.z));
                if (!fs.isEmpty()) this.remove();
            }
        }
    }

    // ================================================================== registration
    @EventBusSubscriber(modid = FrontierHunts.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void register(RegisterParticleProvidersEvent event) {
            event.registerSpriteSet(AtvGrimeNet.CLOD.get(), sprites -> {
                clodSprites = sprites;
                return (type, level, x, y, z, dx, dy, dz) -> new Clod(level, x, y, z, dx, dy, dz, 0.42f, 0.34f, 0.26f, 0.08f, 40);
            });
            event.registerSpriteSet(AtvGrimeNet.MIST.get(), sprites -> {
                mistSprites = sprites;
                return (type, level, x, y, z, dx, dy, dz) -> new Mist(level, x, y, z, dx, dy, dz, 0.5f, 0.42f, 0.34f, 0.35f, 0.25f, 1.5f, 24, 0.02f);
            });
            event.registerSpriteSet(AtvGrimeNet.DROP.get(), sprites -> {
                dropSprites = sprites;
                return (type, level, x, y, z, dx, dy, dz) -> new Drop(level, x, y, z, dx, dy, dz, 0.05f, 30);
            });
            event.registerSpriteSet(AtvGrimeNet.SPRAY.get(), sprites -> { // [atv2]
                spraySprites = sprites;
                return (type, level, x, y, z, dx, dy, dz) -> new Spray(level, x, y, z, dx, dy, dz, 0.4f, 1.2f, 0.5f, 12, 0.5f);
            });
        }
    }
}
