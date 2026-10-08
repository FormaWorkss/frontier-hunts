package com.formaworks.frontierhunts.client.mixin;

import com.formaworks.frontierhunts.hunting.Whitetail;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Whitetail.class})
public abstract class WhitetailKillMixin {
   @Inject(
      method = {"hurt"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void frontierhunts$kill(DamageSource var1, float var2, CallbackInfoReturnable<Boolean> var3) {
      if (var1.is(DamageTypes.GENERIC_KILL) || var1.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
         Whitetail var4 = (Whitetail)this;
         if (!var4.level().isClientSide) {
            var4.discard();
         }

         var3.setReturnValue(true);
      }
   }
}
