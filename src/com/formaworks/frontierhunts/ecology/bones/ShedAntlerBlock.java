package com.formaworks.frontierhunts.ecology.bones;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/** [ecology] A whitetail shed antler lying in the leaves; break it to take it home. */
public final class ShedAntlerBlock extends BoneBlock {
   private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 4.0, 14.0);

   public ShedAntlerBlock(Properties p) {
      super(p);
      this.registerDefaultState(this.defaults(this.stateDefinition.any()));
   }

   @Override
   protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
      return simpleCodec(ShedAntlerBlock::new);
   }

   @Override
   protected VoxelShape outline() {
      return SHAPE;
   }

   @Override
   protected String reading(BlockState s) {
      return "A whitetail shed antler · dropped by a living buck at the end of winter"
         + (s.getValue(MOSSY) ? " · chalky and gnawed by mice, several years old" : " · still brown, last winter's") + " · break it to take it";
   }
}
