package com.formaworks.frontierhunts.workshop;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class WorkbenchBlock extends WideStationBlock implements EntityBlock {
   public static final MapCodec<WorkbenchBlock> CODEC = simpleCodec(WorkbenchBlock::new);
   private static final VoxelShape FRAME = Shapes.or(
      box(0.0, 12.0, 0.0, 16.0, 14.0, 16.0),
      new VoxelShape[]{
         box(1.0, 0.0, 1.0, 3.0, 12.0, 3.0),
         box(13.0, 0.0, 1.0, 15.0, 12.0, 3.0),
         box(1.0, 0.0, 13.0, 3.0, 12.0, 15.0),
         box(13.0, 0.0, 13.0, 15.0, 12.0, 15.0),
         box(2.0, 3.0, 2.0, 14.0, 4.0, 14.0),
         box(1.0, 9.0, 2.0, 15.0, 12.0, 14.0)
      }
   );
   private static final VoxelShape CABINET = Shapes.or(box(1.0, 0.0, 1.0, 15.0, 13.0, 15.0), box(0.0, 13.0, 0.0, 16.0, 14.0, 16.0));

   public WorkbenchBlock(Properties var1) {
      super(var1);
      this.registerDefaultState((BlockState)((BlockState)this.stateDefinition.any()).setValue(FACING, Direction.NORTH));
   }

   protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
      return CODEC;
   }

   @Override
   protected void createBlockStateDefinition(Builder<Block, BlockState> var1) {
      super.createBlockStateDefinition(var1);
   }

   @Override
   public BlockState getStateForPlacement(BlockPlaceContext var1) {
      return super.getStateForPlacement(var1);
   }

   @Override
   protected BlockState rotate(BlockState var1, Rotation var2) {
      return (BlockState)var1.setValue(FACING, var2.rotate((Direction)var1.getValue(FACING)));
   }

   @Override
   protected BlockState mirror(BlockState var1, Mirror var2) {
      return super.mirror(var1, var2);
   }

   protected VoxelShape getShape(BlockState var1, BlockGetter var2, BlockPos var3, CollisionContext var4) {
      return StationShapes.get(var1);
   }

   public BlockEntity newBlockEntity(BlockPos var1, BlockState var2) {
      return WideStationBlock.secondary(var2) ? null : new WorkbenchEntity(var1, var2);
   }

   protected RenderShape getRenderShape(BlockState var1) {
      return RenderShape.MODEL;
   }

   protected MenuProvider getMenuProvider(BlockState var1, Level var2, BlockPos var3) {
      return new SimpleMenuProvider(
         (var3x, var4, var5) -> new WorkbenchMenu(var3x, var4, var2, WideStationBlock.origin(var3, var1)),
         Component.translatable(
            var1.is((Block)WorkshopContent.ATTACHMENT_BENCH.get()) ? "block.frontierhunts.attachment_workbench" : "block.frontierhunts.weapons_workbench"
         )
      );
   }

   protected InteractionResult useWithoutItem(BlockState var1, Level var2, BlockPos var3, Player var4, BlockHitResult var5) {
      if (!var2.isClientSide) {
         var4.openMenu(this.getMenuProvider(var1, var2, var3));
      }

      return InteractionResult.sidedSuccess(var2.isClientSide);
   }
}
