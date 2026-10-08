package com.formaworks.frontierhunts.client.mixin;

import com.formaworks.frontierhunts.client.KillCamClient;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** [killcam] Places the camera exactly on the kill cam's per-frame pose (no eye-height easing, no F5 offset). */
@Mixin({Camera.class})
public abstract class KillCamCameraMixin {
   @Shadow
   protected abstract void setPosition(Vec3 pos);

   @Shadow
   protected abstract void setRotation(float yRot, float xRot);

   @Inject(method = {"setup"}, at = {@At("TAIL")})
   private void frontierhunts$killCamPose(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partial, CallbackInfo ci) {
      if (KillCamClient.drivesCamera(entity)) {
         this.setRotation(KillCamClient.cameraYaw(), KillCamClient.cameraPitch());
         this.setPosition(KillCamClient.cameraPosition());
      }
   }
}
