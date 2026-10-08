package com.formaworks.frontierhunts.outfitter;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * [1.1.8] A strip of blaze-orange flagging tape tied to a twig, from the Roll of Flagging Tape: trackers tie one at the
 * last blood they found so they can get back to it. No collision; breaks with a touch and drops nothing.
 */
public final class TrailFlagBlock extends Block {
   public static final MapCodec<TrailFlagBlock> CODEC = simpleCodec(TrailFlagBlock::new);
   private static final VoxelShape SHAPE = Block.box(6.0, 0.0, 6.0, 10.0, 12.0, 10.0);

   public TrailFlagBlock(Properties p) {
      super(p);
   }

   @Override
   protected MapCodec<? extends Block> codec() {
      return CODEC;
   }

   @Override
   protected VoxelShape getShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) {
      return SHAPE;
   }

   @Override
   protected boolean canSurvive(BlockState s, LevelReader level, BlockPos pos) {
      return Block.canSupportCenter(level, pos.below(), Direction.UP) || level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
   }

   @Override
   protected BlockState updateShape(BlockState s, Direction dir, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos) {
      return dir == Direction.DOWN && !this.canSurvive(s, level, pos) ? Blocks.AIR.defaultBlockState() : s;
   }
}
