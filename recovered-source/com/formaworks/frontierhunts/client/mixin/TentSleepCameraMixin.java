package com.formaworks.frontierhunts.client.mixin;

import com.formaworks.frontierhunts.landscape.tent.TentSleepView;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.ClientHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reframes the sleeping camera in tents; see {@link TentSleepView}. */
@Mixin({Camera.class})
public abstract class TentSleepCameraMixin {
   @Shadow
   protected abstract void setPosition(Vec3 pos);

   @Shadow
   protected abstract void setRotation(float yRot, float xRot);

   @Shadow
   protected abstract void move(float zoom, float dy, float dx);

   @Shadow
   private float getMaxZoom(float maxZoom) {
      throw new AssertionError();
   }

   @Inject(method = {"setup"}, at = {@At("TAIL")})
   private void frontierhunts$tentSleepView(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partial, CallbackInfo ci) {
      if (!(entity instanceof LivingEntity living) || !living.isSleeping()) {
         return;
      }

      if (!detached) {
         TentSleepView.View view = TentSleepView.firstPerson(living);
         if (view != null) {
            this.setRotation(view.yaw(), view.pitch());
            this.setPosition(view.eye());
         }
      } else {
         // third person orbits the body on the pad (vanilla orbits the live position, sunk into the ground)
         Vec3 pivot = TentSleepView.thirdPersonPivot(living);
         if (pivot != null) {
            float scale = living.getScale();
            this.setPosition(pivot);
            this.move(-this.getMaxZoom(ClientHooks.getDetachedCameraDistance((Camera)(Object)this, mirrored, scale, 4.0F) * scale), 0.0F, 0.0F);
         }
      }
   }
}
