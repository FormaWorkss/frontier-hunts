package com.formaworks.frontierhunts.client.mixin;

import com.formaworks.frontierhunts.season.DeepSnow;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * [1.1.0] Common: snow is soft. A snow layer carries you only part of its height (a full block packs down to under half)
 * and snow lying on a full block of snow carries nothing, so you sink in and wade (DeepSnow). Snow also no longer hides
 * the faces of the blocks next to it: its surface is drawn smooth (SmoothSnowModel) and may dip below a full block.
 */
@Mixin(SnowLayerBlock.class)
public abstract class SnowCollisionMixin extends Block {
   protected SnowCollisionMixin(Properties properties) {
      super(properties);
   }

   @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
   private void frontierhunts$softSnow(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx, CallbackInfoReturnable<VoxelShape> cir) {
      // [1.2.0] a toboggan rides on the snow, it doesn't sink in: its runners meet the surface as it shows
      if (ctx instanceof net.minecraft.world.phys.shapes.EntityCollisionContext ec && ec.getEntity() instanceof com.formaworks.frontierhunts.sled.SledEntity) {
         cir.setReturnValue(DeepSnow.surfaceShape(state));
         return;
      }
      cir.setReturnValue(DeepSnow.collision(state, level, pos));
   }

   @Override
   protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
      return Shapes.empty();
   }
}
