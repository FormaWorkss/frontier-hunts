package com.formaworks.frontierhunts.client.mixin;

import com.formaworks.frontierhunts.tracking.hound.client.HoundOutline;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** [1.1.6] Your own tracking hound shows an outline through brush once it is a few blocks away (only on your screen). */
@Mixin({Minecraft.class})
public abstract class HoundGlowMixin {
   @Inject(method = {"shouldEntityAppearGlowing"}, at = {@At("HEAD")}, cancellable = true)
   private void frontierhunts$houndOutline(Entity entity, CallbackInfoReturnable<Boolean> cir) {
      if (HoundOutline.show(entity)) {
         cir.setReturnValue(true);
      }
   }
}
