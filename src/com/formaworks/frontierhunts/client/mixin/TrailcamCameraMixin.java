package com.formaworks.frontierhunts.client.mixin;

import com.formaworks.frontierhunts.client.trailcam.Darkroom;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** [trailcam] While a trail camera photo develops, the render camera sits exactly at the recorded lens pose. */
@Mixin({Camera.class})
public abstract class TrailcamCameraMixin {
   @Shadow
   protected abstract void setPosition(Vec3 pos);

   @Shadow
   protected abstract void setRotation(float yRot, float xRot, float roll);

   @Inject(method = {"setup"}, at = {@At("TAIL")}, require = 0)
   private void frontierhunts$trailcamLens(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partial, CallbackInfo ci) {
      if (Darkroom.active()) {
         this.setRotation(Darkroom.lensYaw(), Darkroom.lensPitch(), 0.0F);
         this.setPosition(Darkroom.lensPos());
      }
   }
}
