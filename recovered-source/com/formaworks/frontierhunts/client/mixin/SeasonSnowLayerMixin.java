package com.formaworks.frontierhunts.client.mixin;

import com.formaworks.frontierhunts.season.SeasonState;
import com.formaworks.frontierhunts.season.SeasonalSnow;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * [seasons] Common: with seasonal snow on, snow does not rest on natural deciduous leaves (their crowns are bare in
 * winter; the snow falls through to the ground - SeasonalSnow). Covers weather, world generation (freeze_top_layer)
 * and neighbour updates alike. Conifer crowns and player-placed leaves still hold snow.
 */
@Mixin(SnowLayerBlock.class)
public abstract class SeasonSnowLayerMixin {
   @Inject(method = "canSurvive(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z",
      at = @At("HEAD"), cancellable = true)
   private void frontierhunts$noSnowOnBareCrowns(BlockState state, LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
      if (SeasonState.snow() && SeasonalSnow.deciduousCanopy(level.getBlockState(pos.below()))) {
         cir.setReturnValue(false);
      }
   }
}
