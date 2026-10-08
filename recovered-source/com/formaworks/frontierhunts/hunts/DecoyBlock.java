package com.formaworks.frontierhunts.hunts;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * [hunts] A carved and painted mallard drake decoy floating on still water (sits in the air block above a water source,
 * like a lily pad; the hull is modelled down into the water). No collision; pops off when the water under it goes.
 * Every placed decoy is registered in {@link HuntStore} so passing ducks can find the spread and a duck taken near one
 * counts as "over the decoys". Right-click shows how many decoys are in the spread.
 */
public final class DecoyBlock extends HorizontalDirectionalBlock {
   public static final MapCodec<DecoyBlock> CODEC = simpleCodec(DecoyBlock::new);
   private static final VoxelShape SHAPE_NS = Block.box(4.0, 0.0, 1.5, 12.0, 7.5, 14.5);
   private static final VoxelShape SHAPE_EW = Block.box(1.5, 0.0, 4.0, 14.5, 7.5, 12.0);

   public DecoyBlock(Properties p) {
      super(p);
      this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
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
   protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
      return s.getValue(FACING).getAxis() == Direction.Axis.Z ? SHAPE_NS : SHAPE_EW;
   }

   @Override
   protected VoxelShape getCollisionShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
      return net.minecraft.world.phys.shapes.Shapes.empty();
   }

   @Override
   protected boolean canSurvive(BlockState s, LevelReader level, BlockPos pos) {
      FluidState below = level.getFluidState(pos.below());
      return below.getType() == Fluids.WATER && level.getFluidState(pos).isEmpty();
   }

   @Override
   protected BlockState updateShape(BlockState s, Direction dir, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos) {
      return dir == Direction.DOWN && !this.canSurvive(s, level, pos) ? Blocks.AIR.defaultBlockState() : super.updateShape(s, dir, other, level, pos, otherPos);
   }

   @Override
   protected void onPlace(BlockState s, Level level, BlockPos pos, BlockState old, boolean moved) {
      super.onPlace(s, level, pos, old, moved);
      if (level instanceof ServerLevel sl && !old.is(this)) {
         HuntStore.get(sl.getServer()).decoyPlaced(sl.dimension().location().toString(), pos);
      }
   }

   @Override
   protected void onRemove(BlockState s, Level level, BlockPos pos, BlockState now, boolean moved) {
      if (level instanceof ServerLevel sl && !now.is(this)) {
         HuntStore.get(sl.getServer()).decoyRemoved(sl.dimension().location().toString(), pos);
      }
      super.onRemove(s, level, pos, now, moved);
   }

   @Override
   protected InteractionResult useWithoutItem(BlockState s, Level level, BlockPos pos, Player player, BlockHitResult hit) {
      if (player instanceof ServerPlayer sp && level instanceof ServerLevel sl) {
         int n = HuntContent.liveDecoys(sl, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 24.0, 64).size();
         sp.displayClientMessage(Component.translatable("hunts.frontierhunts.msg.decoys", n), true);
      }
      return InteractionResult.sidedSuccess(level.isClientSide);
   }
}
