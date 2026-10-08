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

public final class BloodParticle extends TextureSheetParticle {
   private static final Map<ClientLevel, Integer> COUNTS = new WeakHashMap<>();
   private static long generation;
   private final long bornGeneration;
   private boolean released;
   private int grounded;

   private BloodParticle(ClientLevel var1, double var2, double var4, double var6, double var8, double var10, double var12, SpriteSet var14) {
      super(var1, var2, var4, var6);
      this.bornGeneration = generation;
      this.xd = var8;
      this.yd = var10 - 0.025;
      this.zd = var12;
      this.gravity = 0.61F;
      this.friction = 0.985F;
      this.hasPhysics = true;
      this.setSize(0.012F, 0.012F);
      this.quadSize = 0.014F + this.random.nextFloat() * 0.01F;
      this.lifetime = 28 + this.random.nextInt(9);
      float var15 = 0.82F + this.random.nextFloat() * 0.18F;
      this.setColor(0.78F * var15, 0.09F * var15, 0.06F * var15);
      this.pickSprite(var14);
      COUNTS.merge(var1, 1, Integer::sum);
   }

   protected int getLightColor(float var1) {
      return FieldEntityLight.point(new Vec3(this.x, this.y, this.z), super.getLightColor(var1), false);
   }

   public ParticleRenderType getRenderType() {
      return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
   }

   public void tick() {
      super.tick();
      if (this.onGround) {
         this.xd *= 0.25;
         this.zd *= 0.25;
         if (++this.grounded >= 3) {
            this.remove();
         }
      }

      if (!this.level.getFluidState(BlockPos.containing(this.x, this.y, this.z)).isEmpty()) {
         this.remove();
      }
   }

   public void remove() {
      super.remove();
      if (!this.released) {
         this.released = true;
         if (this.bornGeneration == generation) {
            COUNTS.computeIfPresent(this.level, (var0, var1) -> Math.max(0, var1 - 1));
         }
      }
   }

   public static int activeCount(ClientLevel var0) {
      return COUNTS.getOrDefault(var0, 0);
   }

   public static void clear() {
      generation++;
      COUNTS.clear();
   }

   public static final class Provider implements ParticleProvider<SimpleParticleType> {
      private final SpriteSet sprites;

      public Provider(SpriteSet var1) {
         this.sprites = var1;
      }

      public Particle createParticle(SimpleParticleType var1, ClientLevel var2, double var3, double var5, double var7, double var9, double var11, double var13) {
         short var15 = switch ((HuntConfig.Quality)HuntConfig.QUALITY.get()) {
            case PERFORMANCE -> 128;
            case BALANCED -> 256;
            case CINEMATIC -> 384;
         };
         return BloodParticle.activeCount(var2) < var15
               && !(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(new Vec3(var3, var5, var7)) > 4096.0)
            ? new BloodParticle(var2, var3, var5, var7, var9, var11, var13, this.sprites)
            : null;
      }
   }
}
