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
public final class AlpineSprayParticle extends TextureSheetParticle {
   private final float opacity;
   private final SpriteSet sprites;

   private AlpineSprayParticle(ClientLevel var1, double var2, double var4, double var6, double var8, double var10, double var12, SpriteSet var14) {
      super(var1, var2, var4, var6, 0.0, 0.0, 0.0);
      this.sprites = var14;
      this.xd = var8;
      this.yd = var10;
      this.zd = var12;
      this.hasPhysics = true;
      boolean var15 = var10 < -0.15;
      boolean var16 = var10 > 0.2;
      this.gravity = var16 ? 0.65F : (var15 ? 0.55F : 0.025F);
      this.friction = !var16 && !var15 ? 0.97F : 0.995F;
      this.quadSize = var16
         ? 0.08F + this.random.nextFloat() * 0.1F
         : (var15 ? 0.25F + this.random.nextFloat() * 0.28F : 0.45F + this.random.nextFloat() * 0.55F);
      this.lifetime = var16 ? 20 + this.random.nextInt(12) : (var15 ? 24 + this.random.nextInt(18) : 35 + this.random.nextInt(35));
      this.opacity = var16 ? 0.72F : (var15 ? 0.62F : 0.23F);
      this.rCol = 0.85F;
      this.gCol = 0.94F;
      this.bCol = 0.97F;
      this.alpha = 0.0F;
      this.setSpriteFromAge(var14);
   }

   public ParticleRenderType getRenderType() {
      return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
   }

   public void tick() {
      super.tick();
      this.setSpriteFromAge(this.sprites);
      float var1 = (float)this.age / (float)this.lifetime;
      this.alpha = this.opacity * Math.min(1.0F, var1 * 8.0F) * Math.min(1.0F, (1.0F - var1) * 4.0F);
      if (this.onGround) {
         this.lifetime = Math.min(this.lifetime, this.age + 6);
      }
   }

   @SubscribeEvent
   public static void register(RegisterParticleProvidersEvent var0) {
      var0.registerSpriteSet(
         (ParticleType)AlpineRegistration.SPRAY_PARTICLE.get(),
         var0x -> (var1, var2, var3, var5, var7, var9, var11, var13) -> new AlpineSprayParticle(var2, var3, var5, var7, var9, var11, var13, var0x)
      );
   }
}
