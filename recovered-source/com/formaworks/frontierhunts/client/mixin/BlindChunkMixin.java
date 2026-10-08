package com.formaworks.frontierhunts.client.mixin;

import com.formaworks.frontierhunts.expedition.BlindVolumes;
import com.formaworks.frontierhunts.expedition.HubGroundBlind;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Common (both sides): keeps {@link BlindVolumes} in step with ground-blind part blocks being set or removed. */
@Mixin(LevelChunk.class)
public abstract class BlindChunkMixin {
   @Inject(
      method = "setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",
      at = @At("RETURN")
   )
   private void frontierhunts$blindParts(BlockPos pos, BlockState state, boolean moving, CallbackInfoReturnable<BlockState> cir) {
      BlockState old = cir.getReturnValue();
      if (state != null && state.getBlock() instanceof HubGroundBlind || old != null && old.getBlock() instanceof HubGroundBlind) {
         BlindVolumes.partChanged((LevelChunk)(Object)this, pos, old, state);
      }
   }
}
