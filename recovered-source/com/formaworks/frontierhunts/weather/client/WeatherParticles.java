package com.formaworks.frontierhunts.weather.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.weather.WeatherRegistry;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Arrays;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import org.joml.Vector3f;

/**
 * Storm particles. They are created directly by {@link WeatherEffects} (no registry lookups per spawn) and use the
 * sprite sets of the four registered weather particle types. Rendering writes the quad corners straight into the
 * vertex consumer: no Quaternionf/Vector3f allocation per particle per frame, light sampled once at spawn.
 *
 * Live counts per role are gathered every tick (each particle counts itself while ticking), which keeps the spawn
 * budget exact even when the particle engine drops or clears particles on its own.
 */
public final class WeatherParticles {
    public static final int STREAK = 0, VEIL = 1, FLAKE = 2, LEAF = 3, DRIFT = 4, RAIN = 5, SPARKLE = 6;
    /** [blizzard2] Big close-range snow streaks driven past (and at) the camera in a blizzard. */
    public static final int NEAR = 7;
    static final int ROLES = 8;
    static final int[] LIVE = new int[ROLES];
    private static final int[] COUNTING = new int[ROLES];

    static SpriteSet streakSprites, veilSprites, flakeSprites, leafSprites;

    private WeatherParticles() {}

    /** Called once per unpaused client tick after the particle engine ticked. */
    static void endTick() {
        System.arraycopy(COUNTING, 0, LIVE, 0, ROLES);
        Arrays.fill(COUNTING, 0);
    }

    static void resetCounts() {
        Arrays.fill(COUNTING, 0);
        Arrays.fill(LIVE, 0);
    }

    static boolean ready() {
        return streakSprites != null && veilSprites != null && flakeSprites != null && leafSprites != null;
    }

    static float smooth(float e0, float e1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - e0) / (e1 - e0)));
        return t * t * (3f - 2f * t);
    }

    /**
     * [shelter] Inside a tent, a cabin or a cave nothing blows past your face: storm particles closer than ~3.5 blocks
     * fade out (canvas and some modded walls do not stop particles), those 3.5-7 blocks away partly, while the storm
     * beyond a window or the door keeps blowing. {@link WeatherClient#nearShelter} eases in/out over ~1-2 s.
     */
    static float shelterFade(float dist) {
        float s = WeatherClient.nearShelter;
        return s <= 0.002f ? 1f : 1f - s * (1f - smooth(3.5f, 7f, dist));
    }

    /** Camera-facing quad with in-plane roll, vanilla winding and UV layout. */
    static void billboard(VertexConsumer vc, Camera cam, float px, float py, float pz, float size, float roll,
                          float u0, float u1, float v0, float v1, float r, float g, float b, float a, int light) {
        Vector3f left = cam.getLeftVector(), up = cam.getUpVector();
        float rx = -left.x(), ry = -left.y(), rz = -left.z();
        float ux = up.x(), uy = up.y(), uz = up.z();
        if (roll != 0f) {
            float c = (float) Math.cos(roll), s = (float) Math.sin(roll);
            float nrx = rx * c + ux * s, nry = ry * c + uy * s, nrz = rz * c + uz * s;
            float nux = -rx * s + ux * c, nuy = -ry * s + uy * c, nuz = -rz * s + uz * c;
            rx = nrx; ry = nry; rz = nrz; ux = nux; uy = nuy; uz = nuz;
        }
        rx *= size; ry *= size; rz *= size;
        ux *= size; uy *= size; uz *= size;
        vc.addVertex(px + rx - ux, py + ry - uy, pz + rz - uz).setUv(u1, v1).setColor(r, g, b, a).setLight(light);
        vc.addVertex(px + rx + ux, py + ry + uy, pz + rz + uz).setUv(u1, v0).setColor(r, g, b, a).setLight(light);
        vc.addVertex(px - rx + ux, py - ry + uy, pz - rz + uz).setUv(u0, v0).setColor(r, g, b, a).setLight(light);
        vc.addVertex(px - rx - ux, py - ry - uy, pz - rz - uz).setUv(u0, v1).setColor(r, g, b, a).setLight(light);
    }

    // ================================================================== streaks (snow, sand, rain, ground drift)
    /** A short motion-blurred streak aligned with its own velocity: wind-driven snow, sand grains, rain, ground drift. */
    static final class Streak extends TextureSheetParticle {
        final int role;
        final float baseAlpha, width, blur;
        final int light;
        final float wobble, phase;

        Streak(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, int role,
               float r, float g, float b, float alpha, float width, float blur, int life, int light) {
            super(level, x, y, z);
            this.xd = vx;
            this.yd = vy;
            this.zd = vz;
            this.role = role;
            this.hasPhysics = false;
            this.gravity = 0f;
            this.friction = 1f;
            this.lifetime = Math.max(4, life);
            this.rCol = r;
            this.gCol = g;
            this.bCol = b;
            this.baseAlpha = alpha;
            this.alpha = 0f;
            this.width = width;
            this.blur = blur;
            this.light = light;
            this.wobble = role == DRIFT ? 0.012f : role == RAIN ? 0f : 0.02f;
            this.phase = this.random.nextFloat() * 6.283f;
            this.pickSprite(streakSprites);
        }

        @Override
        public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }

        @Override
        protected int getLightColor(float partialTick) { return light; }

        @Override
        public void tick() {
            COUNTING[role]++;
            this.xo = this.x;
            this.yo = this.y;
            this.zo = this.z;
            if (this.age++ >= this.lifetime) {
                this.remove();
                return;
            }
            if (wobble != 0f) {
                // small turbulent swirl so the snow does not move like a rigid sheet
                float s = (float) Math.sin(this.age * 0.55f + phase);
                this.yd += s * wobble * 0.35f;
                this.xd += Math.cos(this.age * 0.41f + phase) * wobble * 0.25f;
            }
            this.x += this.xd;
            this.y += this.yd;
            this.z += this.zd;
            if ((this.age & 1) == 0 || role == NEAR) { // [blizzard2] near streaks are fast and close: test every tick
                int bx = (int) Math.floor(this.x), bz = (int) Math.floor(this.z);
                if (role == RAIN) {
                    int h = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
                    if (this.y < h) {
                        if (this.random.nextFloat() < 0.4f) this.level.addParticle(ParticleTypes.RAIN, this.x, h + 0.02, this.z, 0, 0, 0);
                        this.remove();
                        return;
                    }
                } else {
                    BlockPos p = BlockPos.containing(this.x, this.y, this.z);
                    BlockState st = this.level.getBlockState(p);
                    if (st.canOcclude()) {
                        this.remove();
                        return;
                    }
                }
            }
            float life = (float) this.age / this.lifetime;
            float fade = Math.min(1f, this.age / 3f) * Math.min(1f, (1f - life) * 5f);
            this.alpha = baseAlpha * fade;
        }

        @Override
        public void render(VertexConsumer vc, Camera cam, float pt) {
            if (this.alpha <= 0.004f) return;
            Vec3 c = cam.getPosition();
            float px = (float) (this.xo + (this.x - this.xo) * pt - c.x);
            float py = (float) (this.yo + (this.y - this.yo) * pt - c.y);
            float pz = (float) (this.zo + (this.z - this.zo) * pt - c.z);
            float dist = (float) Math.sqrt(px * px + py * py + pz * pz);
            float a = this.alpha * smooth(0.3f, 1.4f, dist) * shelterFade(dist); // [shelter] none inside a tent / cabin
            if (a <= 0.004f) return;
            float vx = (float) this.xd, vy = (float) this.yd, vz = (float) this.zd;
            float sp = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
            float ax, ay, az;
            if (sp < 1e-4f) { ax = 0f; ay = 1f; az = 0f; sp = 0f; } else { ax = vx / sp; ay = vy / sp; az = vz / sp; }
            float hl = Math.max(width, sp * blur) * 0.5f;
            // side vector: perpendicular to the streak and to the view ray
            float sx = ay * pz - az * py, sy = az * px - ax * pz, sz = ax * py - ay * px;
            float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (sl < 1e-5f) {
                Vector3f left = cam.getLeftVector();
                sx = left.x(); sy = left.y(); sz = left.z();
            } else {
                sx /= sl; sy /= sl; sz /= sl;
            }
            // winding: front face must point at the camera (right x up . -p > 0)
            float nx = sy * az - sz * ay, ny = sz * ax - sx * az, nz = sx * ay - sy * ax;
            if (nx * px + ny * py + nz * pz > 0f) { sx = -sx; sy = -sy; sz = -sz; }
            float hw = width * 0.5f;
            sx *= hw; sy *= hw; sz *= hw;
            ax *= hl; ay *= hl; az *= hl;
            float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
            vc.addVertex(px + sx - ax, py + sy - ay, pz + sz - az).setUv(u1, v1).setColor(rCol, gCol, bCol, a).setLight(light);
            vc.addVertex(px + sx + ax, py + sy + ay, pz + sz + az).setUv(u1, v0).setColor(rCol, gCol, bCol, a).setLight(light);
            vc.addVertex(px - sx + ax, py - sy + ay, pz - sz + az).setUv(u0, v0).setColor(rCol, gCol, bCol, a).setLight(light);
            vc.addVertex(px - sx - ax, py - sy - ay, pz - sz - az).setUv(u0, v1).setColor(rCol, gCol, bCol, a).setLight(light);
        }
    }

    // ================================================================== veils (depth-tested whiteout / haze volume)
    /**
     * Large, very soft billboard of snow/dust/fog. Many of them at 3-30 blocks form a depth-tested volume, so near
     * things stay visible while distant terrain disappears - this works even when a shader pack ignores vanilla fog.
     */
    static final class Veil extends TextureSheetParticle {
        final float peakAlpha, grow, spin;
        final int light;

        Veil(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, float r, float g, float b,
             float alpha, float size, int life, int light) {
            super(level, x, y, z);
            this.xd = vx;
            this.yd = vy;
            this.zd = vz;
            this.hasPhysics = false;
            this.lifetime = Math.max(10, life);
            this.quadSize = size;
            this.grow = 1.0f + 0.0025f + this.random.nextFloat() * 0.003f;
            this.spin = (this.random.nextFloat() - 0.5f) * 0.01f;
            this.rCol = r;
            this.gCol = g;
            this.bCol = b;
            this.peakAlpha = alpha;
            this.alpha = 0f;
            this.light = light;
            this.roll = this.oRoll = this.random.nextFloat() * 6.283f;
            this.pickSprite(veilSprites);
        }

        @Override
        public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }

        @Override
        protected int getLightColor(float partialTick) { return light; }

        @Override
        public void tick() {
            COUNTING[VEIL]++;
            this.xo = this.x;
            this.yo = this.y;
            this.zo = this.z;
            this.oRoll = this.roll;
            if (this.age++ >= this.lifetime) {
                this.remove();
                return;
            }
            this.x += this.xd;
            this.y += this.yd;
            this.z += this.zd;
            this.roll += spin;
            this.quadSize *= grow;
            float life = (float) this.age / this.lifetime;
            this.alpha = peakAlpha * smooth(0f, 0.22f, life) * (1f - smooth(0.6f, 1f, life));
        }

        @Override
        public void render(VertexConsumer vc, Camera cam, float pt) {
            if (this.alpha <= 0.003f) return;
            Vec3 c = cam.getPosition();
            float px = (float) (this.xo + (this.x - this.xo) * pt - c.x);
            float py = (float) (this.yo + (this.y - this.yo) * pt - c.y);
            float pz = (float) (this.zo + (this.z - this.zo) * pt - c.z);
            float dist = (float) Math.sqrt(px * px + py * py + pz * pz);
            // fade out as the camera walks into it (a veil must never pop or cover the whole screen)
            float a = this.alpha * smooth(1.6f, 4.2f, dist) * WeatherClient.veilGate;
            if (a <= 0.003f) return;
            float roll = this.oRoll + (this.roll - this.oRoll) * pt;
            billboard(vc, cam, px, py, pz, this.quadSize, roll, getU0(), getU1(), getV0(), getV1(), rCol, gCol, bCol, a, light);
        }
    }

    // ================================================================== flakes / ice crystals
    static final class Flake extends TextureSheetParticle {
        final int role;
        final float baseAlpha, spin, twinkle;
        final int light;

        Flake(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, int role, float r, float g, float b,
              float alpha, float size, int life, int light) {
            super(level, x, y, z);
            this.xd = vx;
            this.yd = vy;
            this.zd = vz;
            this.role = role;
            this.hasPhysics = false;
            this.lifetime = Math.max(6, life);
            this.quadSize = size;
            this.rCol = r;
            this.gCol = g;
            this.bCol = b;
            this.baseAlpha = alpha;
            this.alpha = 0f;
            this.light = light;
            this.spin = (this.random.nextFloat() - 0.5f) * 0.5f;
            this.twinkle = 0.25f + this.random.nextFloat() * 0.4f;
            this.roll = this.oRoll = this.random.nextFloat() * 6.283f;
            this.pickSprite(flakeSprites);
        }

        @Override
        public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }

        @Override
        protected int getLightColor(float partialTick) { return role == SPARKLE ? 0xF000F0 : light; }

        @Override
        public void tick() {
            COUNTING[role]++;
            this.xo = this.x;
            this.yo = this.y;
            this.zo = this.z;
            this.oRoll = this.roll;
            if (this.age++ >= this.lifetime) {
                this.remove();
                return;
            }
            this.x += this.xd;
            this.y += this.yd;
            this.z += this.zd;
            this.roll += spin;
            if ((this.age & 3) == 0 && this.level.getBlockState(BlockPos.containing(this.x, this.y, this.z)).canOcclude()) {
                this.remove();
                return;
            }
            float life = (float) this.age / this.lifetime;
            float fade = Math.min(1f, this.age / 4f) * Math.min(1f, (1f - life) * 4f);
            if (role == SPARKLE) {
                float tw = 0.5f + 0.5f * (float) Math.sin(this.age * twinkle + spin * 40f);
                fade *= tw * tw;
            }
            this.alpha = baseAlpha * fade;
        }

        @Override
        public void render(VertexConsumer vc, Camera cam, float pt) {
            if (this.alpha <= 0.004f) return;
            Vec3 c = cam.getPosition();
            float px = (float) (this.xo + (this.x - this.xo) * pt - c.x);
            float py = (float) (this.yo + (this.y - this.yo) * pt - c.y);
            float pz = (float) (this.zo + (this.z - this.zo) * pt - c.z);
            float d2 = px * px + py * py + pz * pz;
            float a = this.alpha * smooth(0.04f, 0.5f, d2) * shelterFade((float) Math.sqrt(d2)); // [shelter]
            if (a <= 0.004f) return;
            float roll = this.oRoll + (this.roll - this.oRoll) * pt;
            billboard(vc, cam, px, py, pz, this.quadSize, roll, getU0(), getU1(), getV0(), getV1(), rCol, gCol, bCol, a, getLightColor(pt));
        }
    }

    // ================================================================== leaves in a gale
    static final class Leaf extends TextureSheetParticle {
        final float spinRate, flutter, phase;
        float spin;
        final int light;
        int grounded;

        Leaf(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, float r, float g, float b, float size, int life, int light) {
            super(level, x, y, z);
            this.xd = vx;
            this.yd = vy;
            this.zd = vz;
            this.hasPhysics = true;
            this.gravity = 0f;
            this.friction = 1f;
            this.lifetime = Math.max(20, life);
            this.quadSize = size;
            this.rCol = r;
            this.gCol = g;
            this.bCol = b;
            this.alpha = 0f;
            this.light = light;
            this.spinRate = (this.random.nextBoolean() ? 1f : -1f) * (0.12f + this.random.nextFloat() * 0.25f);
            this.spin = spinRate;
            this.flutter = 0.02f + this.random.nextFloat() * 0.03f;
            this.phase = this.random.nextFloat() * 6.283f;
            this.roll = this.oRoll = this.random.nextFloat() * 6.283f;
            this.setSize(0.1f, 0.1f);
            this.pickSprite(leafSprites);
        }

        @Override
        public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }

        @Override
        protected int getLightColor(float partialTick) { return light; }

        @Override
        public void tick() {
            COUNTING[LEAF]++;
            this.xo = this.x;
            this.yo = this.y;
            this.zo = this.z;
            this.oRoll = this.roll;
            if (this.age++ >= this.lifetime || grounded > 30) {
                this.remove();
                return;
            }
            float wx = WeatherClient.windUnitX, wz = WeatherClient.windUnitZ;
            float push = WeatherClient.leafPush;
            if (this.onGround) {
                grounded++;
                this.xd *= 0.6;
                this.zd *= 0.6;
                spin *= 0.7f;
                if (push > 0.25f && this.random.nextFloat() < 0.08f * push) {
                    // a gust picks it back up
                    this.yd = 0.08 + this.random.nextFloat() * 0.1;
                    grounded = 0;
                    spin = spinRate;
                }
            } else {
                double tx = wx * push, tz = wz * push;
                this.xd += (tx - this.xd) * 0.12;
                this.zd += (tz - this.zd) * 0.12;
                float f = (float) Math.sin(this.age * 0.35f + phase);
                this.xd += -wz * f * flutter;
                this.zd += wx * f * flutter;
                this.yd += -0.012 + Math.cos(this.age * 0.5f + phase) * 0.006 * (0.4 + push);
                this.yd *= 0.94;
            }
            this.roll += spin;
            this.move(this.xd, this.yd, this.zd);
            float life = (float) this.age / this.lifetime;
            this.alpha = Math.min(1f, this.age / 5f) * Math.min(1f, (1f - life) * 6f) * (grounded > 0 ? 1f - grounded / 32f : 1f);
        }

        @Override
        public void render(VertexConsumer vc, Camera cam, float pt) {
            if (this.alpha <= 0.01f) return;
            Vec3 c = cam.getPosition();
            float px = (float) (this.xo + (this.x - this.xo) * pt - c.x);
            float py = (float) (this.yo + (this.y - this.yo) * pt - c.y);
            float pz = (float) (this.zo + (this.z - this.zo) * pt - c.z);
            float roll = this.oRoll + (this.roll - this.oRoll) * pt;
            float la = this.alpha * shelterFade((float) Math.sqrt(px * px + py * py + pz * pz)); // [shelter]
            if (la <= 0.01f) return;
            // tumble: the apparent size pulses as the leaf turns edge-on
            float edge = 0.45f + 0.55f * Math.abs((float) Math.cos(roll * 1.7f + phase));
            billboard(vc, cam, px, py, pz, this.quadSize * edge, roll, getU0(), getU1(), getV0(), getV1(), rCol, gCol, bCol, la, light);
        }
    }

    // ================================================================== registration
    @EventBusSubscriber(modid = FrontierHunts.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Providers {
        private Providers() {}

        @SubscribeEvent
        public static void register(RegisterParticleProvidersEvent event) {
            event.registerSpriteSet(WeatherRegistry.STREAK.get(), sprites -> {
                streakSprites = sprites;
                return (type, level, x, y, z, dx, dy, dz) -> new Streak(level, x, y, z, dx, dy, dz, STREAK, 1f, 1f, 1f, 0.8f, 0.05f, 1.6f, 20, 0xF000F0);
            });
            event.registerSpriteSet(WeatherRegistry.VEIL.get(), sprites -> {
                veilSprites = sprites;
                return (type, level, x, y, z, dx, dy, dz) -> new Veil(level, x, y, z, dx, dy, dz, 0.9f, 0.9f, 0.92f, 0.3f, 3f, 80, 0xF000F0);
            });
            event.registerSpriteSet(WeatherRegistry.FLAKE.get(), sprites -> {
                flakeSprites = sprites;
                return (type, level, x, y, z, dx, dy, dz) -> new Flake(level, x, y, z, dx, dy, dz, FLAKE, 1f, 1f, 1f, 0.9f, 0.05f, 30, 0xF000F0);
            });
            event.registerSpriteSet(WeatherRegistry.LEAF.get(), sprites -> {
                leafSprites = sprites;
                return (type, level, x, y, z, dx, dy, dz) -> new Leaf(level, x, y, z, dx, dy, dz, 0.85f, 0.5f, 0.2f, 0.1f, 80, 0xF000F0);
            });
        }
    }
}
