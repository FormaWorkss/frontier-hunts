package com.formaworks.frontierhunts.client.mixin;

import com.formaworks.frontierhunts.client.KillCamClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * [killcam] Entity + particle hooks for the kill cam: hides the real target while its client double plays the shot,
 * renders the doubles on the replay's slow-motion clock, and holds back the server's hit particles at the animal until
 * the replayed projectile actually gets there. All checks are a single null test when no kill cam runs.
 */
@Mixin({LevelRenderer.class})
public abstract class KillCamLevelRendererMixin {
   @Unique
   private static boolean frontierhunts$killCamReentry;

   @Shadow
   private void renderEntity(Entity entity, double camX, double camY, double camZ, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource) {
      throw new AssertionError();
   }

   @Inject(method = {"renderEntity"}, at = {@At("HEAD")}, cancellable = true)
   private void frontierhunts$killCamEntity(
      Entity entity, double camX, double camY, double camZ, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, CallbackInfo ci
   ) {
      if (frontierhunts$killCamReentry || !KillCamClient.active()) {
         return;
      }
      if (KillCamClient.hidden(entity)) {
         ci.cancel();
         return;
      }
      float slow = KillCamClient.partialFor(entity);
      if (!Float.isNaN(slow) && slow != partialTick) {
         frontierhunts$killCamReentry = true;
         try {
            this.renderEntity(entity, camX, camY, camZ, slow, poseStack, bufferSource);
         } finally {
            frontierhunts$killCamReentry = false;
         }
         ci.cancel();
      }
   }

   @Inject(
      method = {"addParticleInternal(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDDDD)Lnet/minecraft/client/particle/Particle;"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void frontierhunts$killCamParticle(
      ParticleOptions options, boolean force, boolean decreased, double x, double y, double z, double dx, double dy, double dz,
      CallbackInfoReturnable<Particle> cir
   ) {
      if (KillCamClient.active() && KillCamClient.suppressParticle(x, y, z)) {
         cir.setReturnValue(null);
      }
   }
}
