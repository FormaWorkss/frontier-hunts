package com.formaworks.frontierhunts.client.mixin;

import com.formaworks.frontierhunts.phone.client.SelfiePose;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** [1.4.0] Selfie poses from the phone's front camera (yours, and the ones hunters nearby strike). */
@Mixin({PlayerModel.class})
public abstract class PhonePoseMixin {
   @Inject(method = {"setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V"}, at = {@At("TAIL")})
   private void frontierhunts$selfiePose(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch,
      CallbackInfo ci) {
      SelfiePose.apply((PlayerModel<?>)(Object)this, entity, ageInTicks);
   }
}
