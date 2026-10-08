package com.formaworks.frontierhunts.client.mixin;

import com.formaworks.frontierhunts.expedition.BlindVolumes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Common (both sides): leaves and plants standing inside a deployed ground blind lose their collision (or take the
 * blind frame's in a frame cell), and on the client can no longer be targeted. Logs keep theirs. Fast path: a single
 * volatile read while no blind is deployed anywhere. See {@link BlindVolumes}.
 */
@Mixin(BlockStateBase.class)
public abstract class BlindCollisionMixin {
   @Inject(
      method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
      at = @At("HEAD"),
      cancellable = true
   )
   private void frontierhunts$blindCollision(BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
      if (BlindVolumes.active() && level != null && pos != null) {
         VoxelShape shape = BlindVolumes.collision((BlockState)(Object)this, level, pos, context);
         if (shape != null) {
            cir.setReturnValue(shape);
         }
      }
   }

   @Inject(
      method = "getShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
      at = @At("HEAD"),
      cancellable = true
   )
   private void frontierhunts$blindOutline(BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
      // CollisionContext.empty() is what the default Block#getCollisionShape passes when it falls back to the outline:
      // leave that alone so a hidden trunk stays solid on the client exactly as on the server. Picking/ray casts
      // carry the entity's own context.
      if (BlindVolumes.active() && level != null && pos != null && context != CollisionContext.empty()) {
         VoxelShape shape = BlindVolumes.outline((BlockState)(Object)this, level, pos);
         if (shape != null) {
            cir.setReturnValue(shape);
         }
      }
   }
}
