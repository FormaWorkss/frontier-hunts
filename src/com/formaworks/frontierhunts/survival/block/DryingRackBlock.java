package com.formaworks.frontierhunts.survival.block;

import com.formaworks.frontierhunts.survival.SurvivalContent;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * [survival] Drying rack: hang up to four strips of raw meat; in sun and wind (or by a fire) they dry into jerky in about
 * a day. Rain soaks them and sets them back; humid country dries slowly. Use with meat to hang (sneak: as many as fit),
 * empty-handed to take a strip down (finished ones first). LOAD = strips hanging, STAGE = how far the least-dry strip is
 * (0 raw, 1 drying, 2 dry) for the model.
 */
public final class DryingRackBlock extends BaseEntityBlock {
   public static final MapCodec<DryingRackBlock> CODEC = simpleCodec(DryingRackBlock::new);
   public static final IntegerProperty LOAD = IntegerProperty.create("load", 0, 4);
   public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 2);
   private static final VoxelShape X = Shapes.or(Block.box(0, 0, 6, 2, 15, 10), Block.box(14, 0, 6, 16, 15, 10), Block.box(0, 12, 7, 16, 14, 9));
   private static final VoxelShape Z = Shapes.or(Block.box(6, 0, 0, 10, 15, 2), Block.box(6, 0, 14, 10, 15, 16), Block.box(7, 12, 0, 9, 14, 16));

   public DryingRackBlock(Properties p) {
      super(p);
      this.registerDefaultState(this.stateDefinition.any().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH).setValue(LOAD, 0).setValue(STAGE, 0));
   }

   @Override
   protected MapCodec<? extends BaseEntityBlock> codec() {
      return CODEC;
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
      b.add(HorizontalDirectionalBlock.FACING, LOAD, STAGE);
   }

   @Override
   public BlockState getStateForPlacement(BlockPlaceContext ctx) {
      return this.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, ctx.getHorizontalDirection().getClockWise());
   }

   @Override
   protected RenderShape getRenderShape(BlockState state) {
      return RenderShape.MODEL;
   }

   @Override
   protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
      return state.getValue(HorizontalDirectionalBlock.FACING).getAxis() == Direction.Axis.X ? X : Z;
   }

   @Override
   protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
      return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
   }

   @Override
   public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return new DryingRackEntity(pos, state);
   }

   @Override
   public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
      return level.isClientSide ? null : createTickerHelper(type, SurvivalContent.DRYING_RACK_ENTITY.get(), DryingRackEntity::tick);
   }

   @Override
   protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
      if (!DryingRackEntity.dryable(stack)) {
         return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
      }
      if (level.isClientSide) {
         return ItemInteractionResult.SUCCESS;
      }
      if (level.getBlockEntity(pos) instanceof DryingRackEntity rack) {
         int hung = rack.hang(stack, player.isSecondaryUseActive() ? 4 : 1, player.hasInfiniteMaterials());
         if (hung > 0) {
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.7F, 0.8F);
            player.displayClientMessage(Component.translatable("survival.frontierhunts.rack.hung", rack.count(), Component.translatable(rack.conditionKey())), true);
            return ItemInteractionResult.SUCCESS;
         }
         player.displayClientMessage(Component.translatable("survival.frontierhunts.rack.full").withStyle(ChatFormatting.GRAY), true);
      }
      return ItemInteractionResult.CONSUME;
   }

   @Override
   protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
      if (level.isClientSide) {
         return InteractionResult.SUCCESS;
      }
      if (level.getBlockEntity(pos) instanceof DryingRackEntity rack) {
         if (rack.count() == 0) {
            player.displayClientMessage(Component.translatable("survival.frontierhunts.rack.empty").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
         }
         ItemStack out = rack.take();
         if (!out.isEmpty() && out.is(com.formaworks.frontierhunts.survival.SurvivalContent.JERKY.get())
            && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            com.formaworks.frontierhunts.survival.SurvivalService.state(sp).preserved += out.getCount(); // [integ4] dried meat counts as preserved
         }
         if (!out.isEmpty()) {
            if (!player.getInventory().add(out)) {
               player.drop(out, false);
            }
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.7F, 0.9F);
            player.displayClientMessage(rack.statusLine(), true);
         }
      }
      return InteractionResult.CONSUME;
   }

   @Override
   protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
      if (!state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof DryingRackEntity rack) {
         rack.dropAll();
      }
      super.onRemove(state, level, pos, next, moved);
   }
}
