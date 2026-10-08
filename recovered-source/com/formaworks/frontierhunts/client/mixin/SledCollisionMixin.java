package com.formaworks.frontierhunts.client.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * [1.2.3] Common (both sides): no block collides with a toboggan. The sled finds its own way over the snow
 * (SledPhysics), so Minecraft's block collision has nothing to add - and on a server it did harm: every move the rider's
 * game sends is checked for the sled's box overlapping a block, and the sled rides a smoothed surface that dips into
 * the corners of the steps under the snow, so the server kept throwing the sled back where it was (the "stuck and
 * glitchy" ride on a server or in single player, which runs the same check).
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class SledCollisionMixin {
   @Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
      at = @At("HEAD"), cancellable = true)
   private void frontierhunts$sledPassesThrough(BlockGetter level, BlockPos pos, CollisionContext ctx, CallbackInfoReturnable<VoxelShape> cir) {
      if (ctx instanceof EntityCollisionContext ec && ec.getEntity() instanceof com.formaworks.frontierhunts.sled.SledEntity) {
         cir.setReturnValue(Shapes.empty());
      }
   }
}
