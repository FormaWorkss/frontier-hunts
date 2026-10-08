package com.formaworks.frontierhunts.seating;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * [onboard2] Trail bench: benches side by side (same facing) join into one long bench. LEFT / RIGHT = joined on the
 * sitter's left / right: the joined ends lose their posts and the boards run through; long runs get a centre trestle.
 */
public class BenchBlock extends SeatBlock {
   public static final MapCodec<BenchBlock> CODEC = simpleCodec(p -> new BenchBlock(p));
   public static final BooleanProperty LEFT = BooleanProperty.create("left");
   public static final BooleanProperty RIGHT = BooleanProperty.create("right");
   private final VoxelShape[][] joined = new VoxelShape[4][];

   public BenchBlock(Properties props) {
      super(SeatKind.TRAIL_BENCH, props);
      for (int i = 0; i < 4; i++) {
         boolean l = (i & 1) != 0, r = (i & 2) != 0;
         double x0 = l ? 0 : 0.6, x1 = r ? 16 : 15.4;
         this.joined[i] = SeatShapes.of(new double[]{x0, 0, 3.5, x1, 7.4, 12}, new double[]{x0, 0, 11.9, x1, 15.4, 14});
      }
      this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LEFT, false).setValue(RIGHT, false));
   }

   @Override
   protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
      return CODEC;
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
      b.add(FACING, LEFT, RIGHT);
   }

   private boolean joins(BlockState self, BlockState other) {
      return other.getBlock() == this && other.getValue(FACING) == self.getValue(FACING);
   }

   private BlockState connect(BlockState s, LevelAccessor level, BlockPos pos) {
      Direction f = s.getValue(FACING);
      // facing north the sitter's left is west (counter-clockwise), the right is east
      return s.setValue(LEFT, this.joins(s, level.getBlockState(pos.relative(f.getCounterClockWise()))))
         .setValue(RIGHT, this.joins(s, level.getBlockState(pos.relative(f.getClockWise()))));
   }

   @Override
   public BlockState getStateForPlacement(BlockPlaceContext ctx) {
      BlockState s = super.getStateForPlacement(ctx);
      return s == null ? null : this.connect(s, ctx.getLevel(), ctx.getClickedPos());
   }

   @Override
   protected BlockState updateShape(BlockState s, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos npos) {
      if (dir.getAxis().isHorizontal()) {
         return this.connect(s, level, pos);
      }
      return s;
   }

   @Override
   protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
      int i = (s.getValue(LEFT) ? 1 : 0) | (s.getValue(RIGHT) ? 2 : 0);
      VoxelShape v = this.joined[i][s.getValue(FACING).get2DDataValue()];
      return v == null ? Shapes.block() : v;
   }
}
