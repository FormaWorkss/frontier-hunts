package com.formaworks.frontierhunts.landscape;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.tags.FluidTags;
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
public final class AlpineBubbleParticle extends TextureSheetParticle {
   private final float opacity;

   private AlpineBubbleParticle(ClientLevel var1, double var2, double var4, double var6, double var8, double var10, double var12, SpriteSet var14) {
      super(var1, var2, var4, var6, 0.0, 0.0, 0.0);
      this.xd = var8;
      this.yd = var10;
      this.zd = var12;
      this.gravity = 0.0F;
      this.friction = 0.985F;
      this.hasPhysics = true;
      boolean var15 = this.random.nextFloat() < 0.4F;
      this.lifetime = 60 + this.random.nextInt(35);
      this.quadSize = var15 ? 0.28F + this.random.nextFloat() * 0.36F : 0.025F + this.random.nextFloat() * 0.065F;
      this.opacity = var15 ? 0.16F : 0.6F;
      this.rCol = 0.79F;
      this.gCol = 0.94F;
      this.bCol = 1.0F;
      this.alpha = this.opacity;
      this.setSprite(var14.get(var15 ? 1 : 0, 1));
   }

   public ParticleRenderType getRenderType() {
      return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
   }

   public void tick() {
      super.tick();
      if (!this.level.getFluidState(BlockPos.containing(this.x, this.y, this.z)).is(FluidTags.WATER)) {
         this.remove();
      } else {
         this.yd = Math.min(0.065, this.yd + 0.009);
         this.xd *= 0.98;
         this.zd *= 0.98;
         this.alpha = this.opacity * Math.min(1.0F, (float)(this.lifetime - this.age) / 12.0F);
      }
   }

   @SubscribeEvent
   public static void register(RegisterParticleProvidersEvent var0) {
      var0.registerSpriteSet(
         (ParticleType)AlpineRegistration.BUBBLE_PARTICLE.get(),
         var0x -> (var1, var2, var3, var5, var7, var9, var11, var13) -> new AlpineBubbleParticle(var2, var3, var5, var7, var9, var11, var13, var0x)
      );
   }
}
