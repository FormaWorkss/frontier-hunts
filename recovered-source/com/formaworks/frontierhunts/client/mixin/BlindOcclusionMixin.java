package com.formaworks.frontierhunts.client.mixin;

import com.formaworks.frontierhunts.expedition.BlindVolumes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client: a block hidden inside a ground blind (snow layer, interior trunk...) must not cull the faces next to it,
 * or the floor under it would show a hole. Sodium asks each neighbour for this per position, so this covers it;
 * the vanilla renderer caches by state pair, which BlindFaceCullMixin handles. Only render regions are affected
 * (never a Level, so lighting and server logic keep the real shape).
 */
@Mixin(BlockStateBase.class)
public abstract class BlindOcclusionMixin {
   @Inject(method = "getFaceOcclusionShape", at = @At("HEAD"), cancellable = true, require = 0)
   private void frontierhunts$blindFaces(BlockGetter level, BlockPos pos, Direction direction, CallbackInfoReturnable<VoxelShape> cir) {
      if (BlindVolumes.clientActive()
         && pos != null
         && !(level instanceof Level)
         && !(level instanceof EmptyBlockGetter)
         && BlindVolumes.clientHides((BlockState)(Object)this, pos)) {
         cir.setReturnValue(Shapes.empty());
      }
   }
}
