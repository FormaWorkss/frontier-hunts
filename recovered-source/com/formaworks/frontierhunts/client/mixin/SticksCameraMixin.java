package com.formaworks.frontierhunts.client.mixin;

import com.formaworks.frontierhunts.sticks.client.SticksClient;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** [sticks] The eye rises smoothly when a kneeling or sitting shooter comes off the sticks; see {@link SticksClient#eye}. */
@Mixin({Camera.class})
public abstract class SticksCameraMixin {
   @Shadow
   private Entity entity;

   @Shadow
   private float eyeHeight;

   @Shadow
   private float eyeHeightOld;

   @Inject(method = {"tick"}, at = {@At("TAIL")})
   private void frontierhunts$sticksEye(CallbackInfo ci) {
      float[] eye = SticksClient.eye(this.entity, this.eyeHeight);
      if (eye != null) {
         this.eyeHeightOld = eye[0];
         this.eyeHeight = eye[1];
      }
   }
}
