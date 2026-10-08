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
import net.minecraft.world.phys.Vec3;

public final class FlareParticle extends TextureSheetParticle {
   private static final Map<ClientLevel, Integer> COUNTS = new WeakHashMap<>();
   private static long generation;
   private final long born;
   private final boolean smoke;
   private final float initialSize;
   private boolean released;

   public static void clear() {
      generation++;
      COUNTS.clear();
   }

   public static int activeCount(ClientLevel var0) {
      return COUNTS.getOrDefault(var0, 0);
   }

   private FlareParticle(ClientLevel var1, double var2, double var4, double var6, double var8, double var10, double var12, SpriteSet var14, boolean var15) {
      super(var1, var2, var4, var6);
      this.born = generation;
      this.smoke = var15;
      this.xd = var8;
      this.yd = var10;
      this.zd = var12;
      this.hasPhysics = true;
      this.friction = 0.97F;
      boolean var16 = !var15 && var8 * var8 + var10 * var10 + var12 * var12 > 0.008;
      this.gravity = var15 ? -0.015F : (var16 ? 0.07F : 0.1F);
      this.lifetime = var15 ? 28 + this.random.nextInt(14) : (var16 ? 30 + this.random.nextInt(15) : 9 + this.random.nextInt(7));
      this.initialSize = var15
         ? 0.075F + this.random.nextFloat() * 0.025F
         : (var16 ? 0.067F + this.random.nextFloat() * 0.025F : 0.043F + this.random.nextFloat() * 0.015F);
      this.quadSize = this.initialSize;
      this.setSize(0.01F, 0.01F);
      if (var15) {
         this.setColor(0.57F, 0.49F, 0.45F);
         this.alpha = 0.38F;
      } else {
         this.setColor(1.0F, 0.12F, 0.035F);
         this.alpha = 0.9F;
      }

      this.roll = this.oRoll = this.random.nextFloat() * (float) Math.PI * 2.0F;
      this.pickSprite(var14);
      COUNTS.merge(var1, 1, Integer::sum);
   }

   public ParticleRenderType getRenderType() {
      return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
   }

   protected int getLightColor(float var1) {
      return this.smoke ? super.getLightColor(var1) : 15728880;
   }

   public void tick() {
      super.tick();
      float var1 = (float)this.age / (float)this.lifetime;
      this.quadSize = this.initialSize * (this.smoke ? 1.0F + 2.0F * var1 : 1.0F - var1 * 0.4F);
      this.alpha = (this.smoke ? 0.38F : 0.9F) * (1.0F - var1) * (1.0F - var1);
      if (!this.level.getFluidState(BlockPos.containing(this.x, this.y, this.z)).isEmpty()) {
         this.remove();
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
      private final boolean smoke;

      public Provider(SpriteSet var1, boolean var2) {
         this.sprites = var1;
         this.smoke = var2;
      }

      public Particle createParticle(SimpleParticleType var1, ClientLevel var2, double var3, double var5, double var7, double var9, double var11, double var13) {
         short var15 = switch ((HuntConfig.Quality)HuntConfig.QUALITY.get()) {
            case PERFORMANCE -> 128;
            case BALANCED -> 256;
            case CINEMATIC -> 384;
         };
         double var16 = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(new Vec3(var3, var5, var7));
         return FlareParticle.COUNTS.getOrDefault(var2, 0) < var15 && !(var16 < 0.42250000000000004) && !(var16 > 16384.0)
            ? new FlareParticle(var2, var3, var5, var7, var9, var11, var13, this.sprites, this.smoke)
            : null;
      }
   }
}
