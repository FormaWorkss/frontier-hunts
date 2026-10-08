package com.formaworks.frontierhunts.season.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * One falling leaf: a small flat leaf (block-atlas sprite, cutout like block-break particles) that tumbles and
 * flutters down in real 3D - it swings side to side like a pendulum, rocks and spins, sinks at a leaf's slow terminal
 * speed and drifts with the wind - then lies flat on the ground (or floats on water) for a while and shrinks away.
 * Client-only cosmetic, never synced; the live count is bounded by {@link LeafFall}.
 */
final class LeafParticle extends TextureSheetParticle {
    static final AtomicInteger LIVE = new AtomicInteger();

    private final double windX, windZ;
    private final float fall, swayAmp, rate, spin, size;
    private final float swayDirX, swayDirZ;
    private float phase;
    private float yaw, oYaw, pitch, oPitch, tilt, oTilt;
    private boolean landed;
    private int restLeft;
    private boolean counted = true;

    LeafParticle(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite, float r, float g, float b,
                 double windX, double windZ, float size) {
        super(level, x, y, z);
        LIVE.incrementAndGet();
        this.setSprite(sprite);
        this.rCol = r;
        this.gCol = g;
        this.bCol = b;
        this.alpha = 1F;
        this.size = size;
        this.quadSize = size;
        this.hasPhysics = true;
        this.gravity = 0F;
        this.friction = 1F;
        this.setSize(0.08F, 0.02F);
        this.lifetime = 520 + this.random.nextInt(240);
        this.windX = windX;
        this.windZ = windZ;
        this.fall = 0.024F + this.random.nextFloat() * 0.014F;
        this.swayAmp = 0.018F + this.random.nextFloat() * 0.03F;
        this.rate = 0.10F + this.random.nextFloat() * 0.09F;
        this.spin = (this.random.nextFloat() - 0.5F) * 0.12F;
        float a = this.random.nextFloat() * Mth.TWO_PI;
        this.swayDirX = Mth.cos(a);
        this.swayDirZ = Mth.sin(a);
        this.phase = this.random.nextFloat() * Mth.TWO_PI;
        this.yaw = this.oYaw = this.random.nextFloat() * Mth.TWO_PI;
        this.xd = windX * 0.3;
        this.zd = windZ * 0.3;
        this.yd = -0.01;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.TERRAIN_SHEET;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.oYaw = this.yaw;
        this.oPitch = this.pitch;
        this.oTilt = this.tilt;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        if (this.landed) {
            if (--this.restLeft <= 0) {
                this.remove();
                return;
            }
            if (this.restLeft < 16) this.quadSize = this.size * this.restLeft / 16F;
            return;
        }
        this.phase += this.rate;
        float swing = Mth.sin(this.phase);
        // pendulum sway across the fall line, drifting with the wind (weaker under the canopy near the ground)
        double sway = this.swayAmp * Mth.cos(this.phase);
        this.xd += (this.windX + sway * this.swayDirX - this.xd) * 0.08;
        this.zd += (this.windZ + sway * this.swayDirZ - this.zd) * 0.08;
        // a leaf sinks fastest mid-swing and almost stops (even lifts) at the ends of each swing
        double targetY = -this.fall * (0.55 + 0.9 * swing * swing) + 0.006 * Mth.cos(this.phase * 2);
        this.yd += (targetY - this.yd) * 0.2;
        this.yaw += this.spin;
        this.pitch = 0.95F * swing;
        this.tilt = 0.35F * Mth.cos(this.phase * 0.7F + 1.1F);
        this.move(this.xd, this.yd, this.zd);
        BlockPos at = BlockPos.containing(this.x, this.y, this.z);
        if (this.level.getFluidState(at).is(FluidTags.WATER)) {
            // floats on the surface
            this.y = at.getY() + this.level.getFluidState(at).getHeight(this.level, at) + 0.01;
            this.land(140 + this.random.nextInt(120));
        } else if (this.onGround || this.xd == 0 && this.zd == 0 && this.yd == 0) {
            this.land(70 + this.random.nextInt(110));
        }
    }

    private void land(int ticks) {
        this.landed = true;
        this.xd = this.yd = this.zd = 0;
        this.pitch = this.oPitch = 0;
        this.tilt = this.oTilt = (this.random.nextFloat() - 0.5F) * 0.12F;
        this.y += 0.012;
        this.yo = this.y;
        this.restLeft = Math.min(ticks, Math.max(20, this.lifetime - this.age));
    }

    @Override
    public void remove() {
        if (this.counted) {
            this.counted = false;
            LIVE.decrementAndGet();
        }
        super.remove();
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        Vec3 cam = camera.getPosition();
        float px = (float) (Mth.lerp(partialTicks, this.xo, this.x) - cam.x());
        float py = (float) (Mth.lerp(partialTicks, this.yo, this.y) - cam.y());
        float pz = (float) (Mth.lerp(partialTicks, this.zo, this.z) - cam.z());
        Quaternionf q = new Quaternionf()
            .rotationY(Mth.lerp(partialTicks, this.oYaw, this.yaw))
            .rotateX(Mth.lerp(partialTicks, this.oPitch, this.pitch))
            .rotateZ(Mth.lerp(partialTicks, this.oTilt, this.tilt));
        float s = this.getQuadSize(partialTicks);
        // the leaf lies in its local XZ plane (flat on the ground when landed), stem toward -Z
        Vector3f a = q.transform(new Vector3f(s, 0, 0)), b = q.transform(new Vector3f(0, 0, s));
        float u0 = this.getU0(), u1 = this.getU1(), v0 = this.getV0(), v1 = this.getV1();
        int light = this.getLightColor(partialTicks);
        float[][] c = {
            {px - a.x - b.x, py - a.y - b.y, pz - a.z - b.z, u0, v1},
            {px - a.x + b.x, py - a.y + b.y, pz - a.z + b.z, u0, v0},
            {px + a.x + b.x, py + a.y + b.y, pz + a.z + b.z, u1, v0},
            {px + a.x - b.x, py + a.y - b.y, pz + a.z - b.z, u1, v1}};
        for (int side = 0; side < 2; side++) {
            for (int i = 0; i < 4; i++) {
                float[] v = c[side == 0 ? i : 3 - i];
                buffer.addVertex(v[0], v[1], v[2]).setUv(v[3], v[4]).setColor(this.rCol, this.gCol, this.bCol, this.alpha).setLight(light);
            }
        }
    }
}
