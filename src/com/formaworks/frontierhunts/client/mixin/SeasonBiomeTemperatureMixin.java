package com.formaworks.frontierhunts.client.mixin;

import com.formaworks.frontierhunts.season.SeasonState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * [seasons] Common (both sides): temperate biomes cool down in winter, so snow falls instead of rain, it settles and
 * lakes and rivers freeze; it warms up again in spring. Everything that asks for weather (coldEnoughToSnow,
 * warmEnoughToRain, shouldSnow, shouldFreeze, getPrecipitationAt) goes through this one temperature. Server and
 * clients compute the same offset from the synced calendar (SeasonState), so snowfall rendering and snow
 * accumulation agree. Hot and ocean biomes never change (SeasonState.seasonal).
 */
@Mixin(Biome.class)
public abstract class SeasonBiomeTemperatureMixin {
   @Inject(method = "getTemperature(Lnet/minecraft/core/BlockPos;)F", at = @At("RETURN"), cancellable = true)
   private void frontierhunts$seasonalTemperature(BlockPos pos, CallbackInfoReturnable<Float> cir) {
      if (SeasonState.winterCold() != 0F) {
         cir.setReturnValue(SeasonState.adjust((Biome)(Object)this, cir.getReturnValueF()));
      }
   }
}
