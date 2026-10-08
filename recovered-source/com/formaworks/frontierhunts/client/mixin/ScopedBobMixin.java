package com.formaworks.frontierhunts.client.mixin;

import com.formaworks.frontierhunts.client.OpticInput;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({GameRenderer.class})
public abstract class ScopedBobMixin {
   @ModifyExpressionValue(
      method = {"bobView"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/util/Mth;lerp(FFF)F"
      )}
   )
   private float frontierhunts$steadyAim(float amplitude) {
      return amplitude * OpticInput.movementBobScale();
   }
}
