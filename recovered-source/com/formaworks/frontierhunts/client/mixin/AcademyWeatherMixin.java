package com.formaworks.frontierhunts.client.mixin;

import com.formaworks.frontierhunts.academy.Academy;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * [academy] Common (server only in practice): the training grounds share the overworld's weather data like every
 * extra dimension; their weather cycle is skipped so they never rain, thunder or darken - the golden-hour sky holds.
 */
@Mixin(ServerLevel.class)
public abstract class AcademyWeatherMixin {
   @Inject(method = "advanceWeatherCycle()V", at = @At("HEAD"), cancellable = true)
   private void frontierhunts$academyCalm(CallbackInfo ci) {
      ServerLevel self = (ServerLevel)(Object)this;
      if (Academy.isTrainingLevel(self)) {
         if (self.rainLevel != 0.0F || self.thunderLevel != 0.0F) {
            self.setRainLevel(0.0F);
            self.setThunderLevel(0.0F);
         }
         ci.cancel();
      }
   }
}
