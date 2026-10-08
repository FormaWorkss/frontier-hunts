package com.formaworks.frontierhunts.seating;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * [onboard2] A seat: right-click to sit (a {@link SeatEntity} carries the hunter), sneak to stand. Faces the player
 * who placed it. Subclasses add the bench's joins and the swivel chairs' state.
 */
public class SeatBlock extends HorizontalDirectionalBlock {
   public static final MapCodec<SeatBlock> CODEC = simpleCodec(p -> new SeatBlock(SeatKind.LOG_STUMP, p));
   public final SeatKind kind;
   private final VoxelShape[] shapes;

   public SeatBlock(SeatKind kind, Properties props) {
      super(props.noOcclusion());
      this.kind = kind;
      this.shapes = SeatShapes.of(boxes(kind));
      this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
   }

   static double[][] boxes(SeatKind kind) {
      return switch (kind) {
         case LOG_STUMP -> new double[][]{{2, 0, 2, 14, 7.3, 14}};
         case CAMP_CHAIR -> new double[][]{{1, 0, 2.4, 15, 7.4, 14}, {1, 7.4, 12, 15, 16, 14}, {0.5, 7.4, 6.8, 2.5, 11, 14}, {13.5, 7.4, 6.8, 15.5, 11, 14}};
         case TRAIL_BENCH -> new double[][]{{0.6, 0, 3.5, 15.4, 7.4, 12}, {0.6, 0, 11.9, 15.4, 15.4, 14}};
         case BLIND_CHAIR -> new double[][]{{2.5, 0, 2.5, 13.5, 7.4, 13.5}, {3, 7.4, 10.6, 13, 13, 13.4}};
         case TOWER_CHAIR -> new double[][]{{2.5, 0, 2.5, 13.5, 8, 13.5}, {3, 8, 10.6, 13, 14.4, 13.4}};
      };
   }

   @Override
   protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
      return CODEC;
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
      b.add(FACING);
   }

   @Override
   public BlockState getStateForPlacement(BlockPlaceContext ctx) {
      return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
   }

   @Override
   protected BlockState rotate(BlockState s, Rotation r) {
      return s.setValue(FACING, r.rotate(s.getValue(FACING)));
   }

   @Override
   protected BlockState mirror(BlockState s, Mirror m) {
      return s.rotate(m.getRotation(s.getValue(FACING)));
   }

   @Override
   protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
      return this.shapes[s.getValue(FACING).get2DDataValue()];
   }

   @Override
   protected boolean isPathfindable(BlockState s, PathComputationType type) {
      return false;
   }

   /** Holding a block: place it (a row of benches, a table beside the chair) instead of sitting down. */
   @Override
   protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState s, Level level, BlockPos pos,
      Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
      return stack.getItem() instanceof net.minecraft.world.item.BlockItem
         ? net.minecraft.world.ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION
         : net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
   }

   @Override
   protected InteractionResult useWithoutItem(BlockState s, Level level, BlockPos pos, Player player, BlockHitResult hit) {
      return Seats.use(this, s, level, pos, player);
   }

   /** Seat point height above the block bottom (blocks) for this state. */
   public double seatY(BlockState s) {
      return this.kind.seatY(false, false);
   }

   /** Where the hunter's hip goes, world coordinates. */
   public Vec3 seatPoint(BlockState s, BlockPos pos) {
      return local(pos, s.getValue(FACING), 0.0, this.kind.hipPx / 16.0, this.seatY(s));
   }

   /** Block-local offset (x to the sitter's right, z toward the back, from the block centre) to world. */
   public static Vec3 local(BlockPos pos, Direction facing, double lx, double lz, double y) {
      return SeatShapes.local(pos, facing, lx, lz, y);
   }
}
