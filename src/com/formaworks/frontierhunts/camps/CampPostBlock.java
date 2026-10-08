package com.formaworks.frontierhunts.camps;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Camp Post: a two-block cairn-and-pole that carries the camp's carved name plaque and flag.
 * The lower half holds the block entity (camp id, name, colour). Using it opens the camp screen.
 */
public final class CampPostBlock extends HorizontalDirectionalBlock implements EntityBlock {
   public static final MapCodec<CampPostBlock> CODEC = simpleCodec(CampPostBlock::new);
   public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
   private static final VoxelShape BASE = Shapes.or(Block.box(3.0, 0.0, 3.0, 13.0, 5.0, 13.0), Block.box(6.0, 5.0, 6.0, 10.0, 16.0, 10.0));
   /** lower half incl. the name plaque on the front face, per facing (north, east, south, west) */
   private static final VoxelShape[] LOWER = new VoxelShape[]{
      Shapes.or(BASE, Block.box(1.0, 7.0, 4.7, 15.0, 15.2, 6.6)),
      Shapes.or(BASE, Block.box(9.4, 7.0, 1.0, 11.3, 15.2, 15.0)),
      Shapes.or(BASE, Block.box(1.0, 7.0, 9.4, 15.0, 15.2, 11.3)),
      Shapes.or(BASE, Block.box(4.7, 7.0, 1.0, 6.6, 15.2, 15.0))
   };
   private static final VoxelShape UPPER = Block.box(6.0, 0.0, 6.0, 10.0, 16.0, 10.0);

   public CampPostBlock(Properties props) {
      super(props);
      this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER));
   }

   @Override
   protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
      return CODEC;
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
      b.add(FACING, HALF);
   }

   @Override
   public BlockState getStateForPlacement(BlockPlaceContext ctx) {
      BlockPos pos = ctx.getClickedPos();
      Level level = ctx.getLevel();
      if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(ctx)) {
         return null;
      }
      return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite()).setValue(HALF, DoubleBlockHalf.LOWER);
   }

   @Override
   public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
      super.setPlacedBy(level, pos, state, placer, stack);
      if (!level.isClientSide) {
         level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
         if (placer instanceof ServerPlayer player) {
            CampService.postPlaced(player, pos);
         }
      }
   }

   @Override
   protected BlockState updateShape(BlockState state, Direction dir, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos) {
      DoubleBlockHalf half = state.getValue(HALF);
      if (dir.getAxis() == Direction.Axis.Y && half == DoubleBlockHalf.LOWER == (dir == Direction.UP)) {
         boolean paired = other.is(this) && other.getValue(HALF) != half && other.getValue(FACING) == state.getValue(FACING);
         return paired ? state : Blocks.AIR.defaultBlockState();
      }
      return state;
   }

   /** Creative break of the upper half: remove the lower half without dropping the post (vanilla door behaviour). */
   @Override
   public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
      if (!level.isClientSide && player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
         BlockPos below = pos.below();
         BlockState lower = level.getBlockState(below);
         if (lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER) {
            level.setBlock(below, Blocks.AIR.defaultBlockState(), 35);
            level.levelEvent(player, 2001, below, Block.getId(lower));
         }
      }
      return super.playerWillDestroy(level, pos, state, player);
   }

   @Override
   protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
      if (!level.isClientSide && !newState.is(this) && state.getValue(HALF) == DoubleBlockHalf.LOWER) {
         CampService.postRemoved(level, pos);
      }
      super.onRemove(state, level, pos, newState, moved);
   }

   @Override
   protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
      if (!level.isClientSide && player instanceof ServerPlayer sp) {
         BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
         CampService.openAtPost(sp, lower);
      }
      return InteractionResult.sidedSuccess(level.isClientSide);
   }

   @Override
   protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
      Direction f = state.getValue(FACING);
      if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
         return LOWER[switch (f) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
         }];
      }
      return UPPER;
   }

   @Override
   protected RenderShape getRenderShape(BlockState state) {
      return RenderShape.MODEL;
   }

   @Override
   protected BlockState rotate(BlockState state, Rotation rot) {
      return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
   }

   @Override
   protected BlockState mirror(BlockState state, Mirror mirror) {
      return state.rotate(mirror.getRotation(state.getValue(FACING)));
   }

   @Override
   public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new CampPostBlockEntity(pos, state) : null;
   }
}
