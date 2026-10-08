package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.HuntConfig;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

public final class WaterBloodParticle extends TextureSheetParticle {
   private static final Map<ClientLevel, Integer> COUNTS = new WeakHashMap<>();
   private static long generation;
   private final long born;
   private final float startSize;
   private boolean released;

   private WaterBloodParticle(ClientLevel var1, double var2, double var4, double var6, double var8, double var10, double var12, SpriteSet var14) {
      super(var1, var2, var4, var6);
      this.born = generation;
      this.xd = var8 * 0.25;
      this.yd = var10 * 0.15;
      this.zd = var12 * 0.25;
      this.gravity = 0.0F;
      this.friction = 0.89F;
      this.hasPhysics = true;
      this.setSize(0.015F, 0.015F);
      this.startSize = 0.025F + this.random.nextFloat() * 0.017F;
      this.quadSize = this.startSize;
      this.lifetime = 34 + this.random.nextInt(19);
      this.setColor(0.4F, 0.065F, 0.045F);
      this.alpha = 0.36F;
      this.roll = this.oRoll = this.random.nextFloat() * (float) Math.PI * 2.0F;
      this.pickSprite(var14);
      COUNTS.merge(var1, 1, Integer::sum);
   }

   public static void clear() {
      generation++;
      COUNTS.clear();
   }

   public ParticleRenderType getRenderType() {
      return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
   }

   public void tick() {
      BlockPos var1 = BlockPos.containing(this.x, this.y, this.z);
      FluidState var2 = this.level.getFluidState(var1);
      if (!var2.is(FluidTags.WATER)) {
         this.remove();
      } else {
         Vec3 var3 = var2.getFlow(this.level, var1);
         this.xd = this.xd + var3.x * 0.008;
         this.yd = this.yd + (var3.y * 0.006 - 3.0E-4);
         this.zd = this.zd + var3.z * 0.008;
         super.tick();
         float var4 = (float)this.age / (float)this.lifetime;
         this.quadSize = this.startSize * (1.0F + var4 * 2.4F);
         this.alpha = 0.36F * (1.0F - var4) * (1.0F - var4);
      }
   }

   public void remove() {
      super.remove();
      if (!this.released) {
         this.released = true;
         if (this.born == generation) {
            COUNTS.computeIfPresent(this.level, (var0, var1) -> Math.max(0, var1 - 1));
         }
      }
   }

   public static final class Provider implements ParticleProvider<SimpleParticleType> {
      private final SpriteSet sprites;

      public Provider(SpriteSet var1) {
         this.sprites = var1;
      }

      public Particle createParticle(SimpleParticleType var1, ClientLevel var2, double var3, double var5, double var7, double var9, double var11, double var13) {
         short var15 = switch ((HuntConfig.Quality)HuntConfig.QUALITY.get()) {
            case PERFORMANCE -> 64;
            case BALANCED -> 128;
            case CINEMATIC -> 192;
         };
         return WaterBloodParticle.COUNTS.getOrDefault(var2, 0) < var15
               && var2.getFluidState(BlockPos.containing(var3, var5, var7)).is(FluidTags.WATER)
               && !(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(new Vec3(var3, var5, var7)) > 2304.0)
            ? new WaterBloodParticle(var2, var3, var5, var7, var9, var11, var13, this.sprites)
            : null;
      }
   }
}
