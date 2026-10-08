package com.formaworks.frontierhunts.client.mixin;

import com.formaworks.frontierhunts.season.SeasonalSnow;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * [seasons] Common (server only in practice): the vanilla per-chunk precipitation sample also drives seasonal snow
 * - it falls through bare deciduous crowns to the ground, buries short grass, and melts again (with lake ice) in
 * spring. See {@link SeasonalSnow#precipitation}.
 */
@Mixin(ServerLevel.class)
public abstract class SeasonPrecipitationMixin {
   @Inject(method = "tickPrecipitation(Lnet/minecraft/core/BlockPos;)V", at = @At("HEAD"), cancellable = true)
   private void frontierhunts$seasonalSnow(BlockPos pos, CallbackInfo ci) {
      if (SeasonalSnow.precipitation((ServerLevel)(Object)this, pos)) {
         ci.cancel();
      }
   }
}
