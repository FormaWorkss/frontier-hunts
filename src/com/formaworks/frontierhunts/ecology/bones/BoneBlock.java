package com.formaworks.frontierhunts.ecology.bones;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * [ecology] Bones lying on the ground: weathered finds from worldgen ({@code natural=true}, permanent) or the remains
 * of a predator kill ({@code natural=false}, these slowly disappear over a few weeks of random ticks). No collision,
 * like a flower; right-click with an empty hand reads them.
 */
public abstract class BoneBlock extends HorizontalDirectionalBlock {
   public static final BooleanProperty MOSSY = BooleanProperty.create("mossy");
   public static final BooleanProperty NATURAL = BooleanProperty.create("natural");

   protected BoneBlock(Properties p) {
      super(p);
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
      b.add(FACING, MOSSY, NATURAL);
   }

   protected BlockState defaults(BlockState s) {
      return s.setValue(FACING, Direction.NORTH).setValue(MOSSY, false).setValue(NATURAL, true);
   }

   @Override
   public BlockState getStateForPlacement(BlockPlaceContext ctx) {
      return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite()).setValue(NATURAL, true);
   }

   protected abstract VoxelShape outline();

   @Override
   protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
      return this.outline();
   }

   @Override
   protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
      BlockPos below = pos.below();
      return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
   }

   @Override
   protected BlockState updateShape(BlockState state, Direction dir, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos) {
      return dir == Direction.DOWN && !state.canSurvive(level, pos)
         ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
         : super.updateShape(state, dir, other, level, pos, otherPos);
   }

   @Override
   protected boolean isRandomlyTicking(BlockState state) {
      return !state.getValue(NATURAL);
   }

   /** kill remains crumble away: ~1 in 260 random ticks, a few weeks at the default tick speed */
   @Override
   protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
      if (!state.getValue(NATURAL) && random.nextInt(260) == 0) {
         level.removeBlock(pos, false);
      }
   }

   @Override
   protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
      if (!level.isClientSide) {
         player.displayClientMessage(Component.literal(this.reading(state)).withStyle(ChatFormatting.GRAY), true);
      }
      return InteractionResult.sidedSuccess(level.isClientSide);
   }

   /** what a hunter reads from these bones (action bar) */
   protected abstract String reading(BlockState state);

   protected static String age(BlockState s) {
      if (!s.getValue(NATURAL)) {
         return "picked clean, still greasy · a predator kill, a few days old";
      }
      return s.getValue(MOSSY) ? "bleached, moss creeping over them · years old" : "sun-bleached and cracked · a season or two old";
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
   protected boolean propagatesSkylightDown(BlockState s, BlockGetter level, BlockPos pos) {
      return true;
   }
}
