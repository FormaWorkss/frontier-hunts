package com.formaworks.frontierhunts.landscape;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.ParticleType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT},
   bus = Bus.MOD
)
public final class AlpineMistParticle extends TextureSheetParticle {
   private final float grow;
   private final float peak;

   private AlpineMistParticle(ClientLevel var1, double var2, double var4, double var6, double var8, double var10, double var12, SpriteSet var14) {
      super(var1, var2, var4, var6, 0.0, 0.0, 0.0);
      this.xd = var8;
      this.yd = var10;
      this.zd = var12;
      this.hasPhysics = false;
      this.gravity = -0.0055F;
      this.friction = 0.968F;
      this.quadSize = 1.2F + this.random.nextFloat() * 1.5F;
      this.grow = 1.012F + this.random.nextFloat() * 0.012F;
      this.lifetime = 90 + this.random.nextInt(80);
      this.peak = 0.12F + this.random.nextFloat() * 0.12F;
      float var15 = 0.92F + this.random.nextFloat() * 0.06F;
      this.rCol = var15;
      this.gCol = var15;
      this.bCol = Math.min(1.0F, var15 + 0.03F);
      this.alpha = 0.0F;
      this.roll = this.oRoll = this.random.nextFloat() * 6.283F;
      this.pickSprite(var14);
   }

   public ParticleRenderType getRenderType() {
      return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
   }

   public void tick() {
      super.tick();
      this.quadSize = this.quadSize * this.grow;
      this.oRoll = this.roll;
      this.roll += 0.004F;
      float var1 = (float)this.age / (float)this.lifetime;
      this.alpha = this.peak * Math.min(1.0F, var1 * 5.0F) * (1.0F - var1) * (1.0F - var1);
   }

   protected int getLightColor(float var1) {
      return super.getLightColor(var1);
   }

   @SubscribeEvent
   public static void register(RegisterParticleProvidersEvent var0) {
      var0.registerSpriteSet(
         (ParticleType)AlpineRegistration.MIST_PARTICLE.get(),
         var0x -> (var1, var2, var3, var5, var7, var9, var11, var13) -> new AlpineMistParticle(var2, var3, var5, var7, var9, var11, var13, var0x)
      );
   }
}
