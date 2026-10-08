package com.formaworks.frontierhunts.client.mixin;

import com.formaworks.frontierhunts.client.prone.ProneCamera;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** [rifle] Smooth eye-height transition when the local player goes prone or gets up; see {@link ProneCamera}. */
@Mixin({Camera.class})
public abstract class ProneCameraMixin {
   @Shadow
   private Entity entity;

   @Shadow
   private float eyeHeight;

   @Shadow
   private float eyeHeightOld;

   @Inject(method = {"tick"}, at = {@At("TAIL")})
   private void frontierhunts$proneEye(CallbackInfo ci) {
      float eye = ProneCamera.eye(this.entity, this.eyeHeightOld, this.eyeHeight);
      if (!Float.isNaN(eye)) {
         this.eyeHeight = eye;
      }
   }
}
