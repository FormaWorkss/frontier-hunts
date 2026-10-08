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
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Big-Buck Board: a free-standing 3 x 2 block trophy wall. PART = row * 3 + column; column 0/1/2 sits at offset
 * -1/0/+1 along the facing's clockwise side, row 0 is the bottom. The bottom-centre part (PART 1) is the origin and holds
 * the block entity; its renderer draws the season's top racks, brass name plates and the honour roll.
 */
public final class TrophyBoardBlock extends HorizontalDirectionalBlock implements EntityBlock {
   public static final MapCodec<TrophyBoardBlock> CODEC = simpleCodec(TrophyBoardBlock::new);
   public static final IntegerProperty PART = IntegerProperty.create("part", 0, 5);
   public static final int ORIGIN = 1;
   private static final VoxelShape[] SHAPES = new VoxelShape[4];

   public TrophyBoardBlock(Properties props) {
      super(props);
      this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, ORIGIN));
   }

   @Override
   protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
      return CODEC;
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
      b.add(FACING, PART);
   }

   /** World position of a part given the origin and facing. */
   public static BlockPos partPos(BlockPos origin, Direction facing, int part) {
      int col = part % 3 - 1;
      int row = part / 3;
      return origin.relative(facing.getClockWise(), col).above(row);
   }

   public static BlockPos origin(BlockPos pos, BlockState state) {
      int part = state.getValue(PART);
      int col = part % 3 - 1;
      int row = part / 3;
      return pos.relative(state.getValue(FACING).getClockWise(), -col).below(row);
   }

   @Override
   public BlockState getStateForPlacement(BlockPlaceContext ctx) {
      Direction facing = ctx.getHorizontalDirection().getOpposite();
      BlockPos origin = ctx.getClickedPos();
      Level level = ctx.getLevel();
      for (int p = 0; p < 6; p++) {
         BlockPos at = partPos(origin, facing, p);
         if (p != ORIGIN
            && (!level.hasChunkAt(at) || !level.getWorldBorder().isWithinBounds(at) || at.getY() >= level.getMaxBuildHeight() || !level.getBlockState(at).canBeReplaced(ctx))) {
            return null;
         }
      }
      return this.defaultBlockState().setValue(FACING, facing).setValue(PART, ORIGIN);
   }

   @Override
   public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
      super.setPlacedBy(level, pos, state, placer, stack);
      if (!level.isClientSide) {
         placeParts(level, pos, state.getValue(FACING));
      }
   }

   /** Fills the five non-origin parts (used by placement and by {@code /records board}). */
   public static void placeParts(Level level, BlockPos origin, Direction facing) {
      BlockState base = CampsContent.TROPHY_BOARD.get().defaultBlockState().setValue(FACING, facing);
      for (int p = 0; p < 6; p++) {
         if (p != ORIGIN) {
            level.setBlock(partPos(origin, facing, p), base.setValue(PART, p), 3);
         }
      }
   }

   public static boolean intact(LevelAccessor level, BlockPos origin, Direction facing) {
      for (int p = 0; p < 6; p++) {
         BlockState s = level.getBlockState(partPos(origin, facing, p));
         if (!s.is(CampsContent.TROPHY_BOARD.get()) || s.getValue(FACING) != facing || s.getValue(PART) != p) {
            return false;
         }
      }
      return true;
   }

   @Override
   protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
      super.onRemove(state, level, pos, newState, moved);
      if (!level.isClientSide && !newState.is(this)) {
         Direction facing = state.getValue(FACING);
         BlockPos origin = origin(pos, state);
         for (int p = 0; p < 6; p++) {
            BlockPos at = partPos(origin, facing, p);
            if (!at.equals(pos)) {
               BlockState s = level.getBlockState(at);
               if (s.is(this) && s.getValue(FACING) == facing && s.getValue(PART) == p) {
                  level.setBlock(at, Blocks.AIR.defaultBlockState(), 3 | Block.UPDATE_SUPPRESS_DROPS);
               }
            }
         }
      }
   }

   @Override
   protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
      if (!level.isClientSide && player instanceof ServerPlayer sp) {
         BlockPos origin = origin(pos, state);
         if (level.getBlockEntity(origin) instanceof TrophyBoardBlockEntity be) {
            if (sp.isShiftKeyDown()) {
               be.cycle();
               sp.displayClientMessage(net.minecraft.network.chat.Component.literal("Board now shows: " + be.category().title), true);
            } else {
               RecordService.openRecords(sp, be.category(), origin);
            }
         }
      }
      return InteractionResult.sidedSuccess(level.isClientSide);
   }

   @Override
   protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
      int i = state.getValue(FACING).get2DDataValue();
      VoxelShape s = SHAPES[i];
      if (s == null) {
         s = SHAPES[i] = shape(state.getValue(FACING));
      }
      return s;
   }

   private static VoxelShape shape(Direction facing) {
      // panel + posts occupy z 6..12 for a north-facing board (front = north); rotate for the others
      return switch (facing) {
         case SOUTH -> Block.box(0.0, 0.0, 4.0, 16.0, 16.0, 10.0);
         case EAST -> Block.box(4.0, 0.0, 0.0, 10.0, 16.0, 16.0);
         case WEST -> Block.box(6.0, 0.0, 0.0, 12.0, 16.0, 16.0);
         default -> Block.box(0.0, 0.0, 6.0, 16.0, 16.0, 12.0);
      };
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
      return state.getValue(PART) == ORIGIN ? new TrophyBoardBlockEntity(pos, state) : null;
   }
}
