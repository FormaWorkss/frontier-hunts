package com.formaworks.frontierhunts.seating;

import com.formaworks.frontierhunts.expedition.TowerBlind;
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
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * [onboard2] Tower swivel chair. It fits itself to the elevated tower blind it stands in, so the seated eye is at the
 * middle of the windows: in the small tower (floor 0.4 below the cabin cells) it is SUNK; in the big tower (windows
 * 1.335-2.07 above the floor) it is RAISED. Sneak + use with an empty hand works the gas lift by hand.
 */
public class TowerChairBlock extends SwivelChairBlock {
   public static final MapCodec<TowerChairBlock> CODEC = simpleCodec(TowerChairBlock::new);
   public static final BooleanProperty RAISED = BooleanProperty.create("raised");
   public static final BooleanProperty SUNK = BooleanProperty.create("sunk");
   private final VoxelShape[][] shapes = new VoxelShape[4][];

   public TowerChairBlock(Properties props) {
      super(SeatKind.TOWER_CHAIR, props);
      for (int i = 0; i < 4; i++) {
         boolean raised = (i & 1) != 0, sunk = (i & 2) != 0;
         double dy = (raised ? SeatKind.TOWER_RAISE : 0) + (sunk ? SeatKind.SUNK : 0);
         this.shapes[i] = SeatShapes.of(new double[]{2.5, 0, 2.5, 13.5, 8 + dy, 13.5}, new double[]{3, 8 + dy, 10.6, 13, 14.4 + dy, 13.4});
      }
      this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OCCUPIED, false).setValue(RAISED, false)
         .setValue(SUNK, false));
   }

   @Override
   protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
      return CODEC;
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
      b.add(FACING, OCCUPIED, RAISED, SUNK);
   }

   /** 0 = no tower blind floor below, 1 = small tower, 2 = big tower. */
   static int tower(BlockState below) {
      if (!(below.getBlock() instanceof TowerBlind) || !below.hasProperty(TowerBlind.PART)) {
         return 0;
      }
      return below.getValue(TowerBlind.PART) < TowerBlind.firstPart() ? 1 : 2;
   }

   @Override
   public BlockState getStateForPlacement(BlockPlaceContext ctx) {
      BlockState s = super.getStateForPlacement(ctx);
      if (s == null) {
         return null;
      }
      int t = tower(ctx.getLevel().getBlockState(ctx.getClickedPos().below()));
      return s.setValue(SUNK, t == 1).setValue(RAISED, t == 2);
   }

   @Override
   protected BlockState updateShape(BlockState s, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos npos) {
      if (dir == Direction.DOWN) {
         return s.setValue(SUNK, tower(neighbor) == 1); // the small tower packed up: stand on the ground again
      }
      return s;
   }

   @Override
   protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
      int i = (s.getValue(RAISED) ? 1 : 0) | (s.getValue(SUNK) ? 2 : 0);
      return this.shapes[i][s.getValue(FACING).get2DDataValue()];
   }

   @Override
   public double seatY(BlockState s) {
      return this.kind.seatY(s.getValue(RAISED), s.getValue(SUNK));
   }
}
