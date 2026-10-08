package com.formaworks.frontierhunts.client.mixin;

import com.formaworks.frontierhunts.client.trailcam.Darkroom;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * [trailcam] A developing trail camera frame shows only what was recorded: every other living thing (the player
 * standing at the camera, animals there now) is skipped, together with its shadow, in the main and shadow passes.
 */
@Mixin({EntityRenderDispatcher.class})
public abstract class TrailcamEntityMixin {
   @Inject(method = {"shouldRender"}, at = {@At("HEAD")}, cancellable = true, require = 0)
   private <E extends Entity> void frontierhunts$trailcamHide(E entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
      if (Darkroom.hides(entity)) {
         cir.setReturnValue(false);
      }
   }
}
