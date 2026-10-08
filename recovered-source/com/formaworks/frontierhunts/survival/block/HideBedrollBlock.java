package com.formaworks.frontierhunts.survival.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * [survival] Hide bedroll: a fur bedroll on a spruce-bough mattress, two blocks long like a bed. Sleeping in it keeps you
 * warm through a cold night (+18 deg felt, see SurvivalService) and skips the night like a bed, but it never sets your
 * spawn (PlayerSetSpawnEvent is refused for it). The sleeping surface is at bed height (9 px), so the sleeper lies on it.
 */
public final class HideBedrollBlock extends HorizontalDirectionalBlock {
   public static final MapCodec<HideBedrollBlock> CODEC = simpleCodec(HideBedrollBlock::new);
   public static final EnumProperty<BedPart> PART = BlockStateProperties.BED_PART;
   public static final BooleanProperty OCCUPIED = BlockStateProperties.OCCUPIED;
   private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 9.0, 16.0);

   public HideBedrollBlock(Properties p) {
      super(p);
      this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, BedPart.FOOT).setValue(OCCUPIED, false));
   }

   @Override
   protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
      return CODEC;
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
      b.add(FACING, PART, OCCUPIED);
   }

   private static Direction toOther(BedPart part, Direction facing) {
      return part == BedPart.FOOT ? facing : facing.getOpposite();
   }

   @Override
   protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
      if (level.isClientSide) {
         return InteractionResult.CONSUME;
      }
      if (state.getValue(PART) != BedPart.HEAD) {
         pos = pos.relative(state.getValue(FACING));
         state = level.getBlockState(pos);
         if (!state.is(this)) {
            return InteractionResult.CONSUME;
         }
      }
      if (!level.dimensionType().natural()) {
         player.displayClientMessage(Component.translatable("survival.frontierhunts.bedroll.unnatural").withStyle(ChatFormatting.GOLD), true);
         return InteractionResult.SUCCESS;
      }
      if (state.getValue(OCCUPIED)) {
         player.displayClientMessage(Component.translatable("block.minecraft.bed.occupied"), true);
         return InteractionResult.SUCCESS;
      }
      player.startSleepInBed(pos).ifLeft(problem -> {
         if (problem.getMessage() != null) {
            player.displayClientMessage(problem.getMessage(), true);
         }
      });
      return InteractionResult.SUCCESS;
   }

   @Override
   public boolean isBed(BlockState state, BlockGetter level, BlockPos pos, LivingEntity sleeper) {
      return true;
   }

   @Override
   public void setBedOccupied(BlockState state, Level level, BlockPos pos, LivingEntity sleeper, boolean occupied) {
      if (state.is(this)) {
         level.setBlock(pos, state.setValue(OCCUPIED, occupied), 3);
      }
   }

   @Override
   public Direction getBedDirection(BlockState state, LevelReader level, BlockPos pos) {
      return state.getValue(FACING);
   }

   @Override
   protected BlockState updateShape(BlockState state, Direction dir, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos) {
      if (dir == toOther(state.getValue(PART), state.getValue(FACING))) {
         return other.is(this) && other.getValue(PART) != state.getValue(PART) ? state.setValue(OCCUPIED, other.getValue(OCCUPIED)) : Blocks.AIR.defaultBlockState();
      }
      return super.updateShape(state, dir, other, level, pos, otherPos);
   }

   @Override
   public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
      if (!level.isClientSide && player.isCreative() && state.getValue(PART) == BedPart.FOOT) {
         BlockPos head = pos.relative(state.getValue(FACING));
         BlockState hs = level.getBlockState(head);
         if (hs.is(this) && hs.getValue(PART) == BedPart.HEAD) {
            level.setBlock(head, Blocks.AIR.defaultBlockState(), 35);
            level.levelEvent(player, 2001, head, Block.getId(hs));
         }
      }
      return super.playerWillDestroy(level, pos, state, player);
   }

   @Override
   public BlockState getStateForPlacement(BlockPlaceContext ctx) {
      Direction facing = ctx.getHorizontalDirection();
      BlockPos head = ctx.getClickedPos().relative(facing);
      Level level = ctx.getLevel();
      return level.getBlockState(head).canBeReplaced(ctx) && level.getWorldBorder().isWithinBounds(head) && Block.canSupportRigidBlock(level, head.below())
         ? this.defaultBlockState().setValue(FACING, facing)
         : null;
   }

   @Override
   protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
      return Block.canSupportRigidBlock(level, pos.below()) || level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
   }

   @Override
   public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
      super.setPlacedBy(level, pos, state, placer, stack);
      if (!level.isClientSide) {
         BlockPos head = pos.relative(state.getValue(FACING));
         level.setBlock(head, state.setValue(PART, BedPart.HEAD), 3);
         level.blockUpdated(pos, Blocks.AIR);
         state.updateNeighbourShapes(level, pos, 3);
      }
   }

   @Override
   protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
      return SHAPE;
   }

   @Override
   protected boolean isPathfindable(BlockState state, PathComputationType type) {
      return false;
   }

   @Override
   protected long getSeed(BlockState state, BlockPos pos) {
      BlockPos p = state.getValue(PART) == BedPart.HEAD ? pos : pos.relative(state.getValue(FACING));
      return net.minecraft.util.Mth.getSeed(p.getX(), pos.getY(), p.getZ());
   }
}
