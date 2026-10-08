package com.formaworks.frontierhunts.client.mixin;

import com.formaworks.frontierhunts.expedition.BlindVolumes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Client, vanilla chunk renderer: draw faces next to a block hidden inside a ground blind (before the state-pair cache). */
@Mixin(Block.class)
public abstract class BlindFaceCullMixin {
   @Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true, require = 0)
   private static void frontierhunts$blindNeighbour(
      BlockState state, BlockGetter level, BlockPos self, Direction face, BlockPos neighbour, CallbackInfoReturnable<Boolean> cir
   ) {
      if (BlindVolumes.clientActive() && neighbour != null && BlindVolumes.clientVolumeAt(neighbour) != null
         && BlindVolumes.clientHides(level.getBlockState(neighbour), neighbour)) {
         cir.setReturnValue(true);
      }
   }
}
