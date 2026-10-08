package com.formaworks.frontierhunts.client.mixin;

import com.formaworks.frontierhunts.sticks.client.SticksPose;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** [sticks] A player resting a gun on shooting sticks: arms laid along the gun to the yoke, a kneel or a seat. */
@Mixin({PlayerModel.class})
public abstract class SticksPlayerModelMixin {
   @Inject(method = {"setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V"}, at = {@At("TAIL")})
   private void frontierhunts$sticksPose(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch,
      CallbackInfo ci) {
      SticksPose.apply((PlayerModel<?>)(Object)this, entity, ageInTicks);
   }
}
