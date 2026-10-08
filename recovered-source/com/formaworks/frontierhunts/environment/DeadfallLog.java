package com.formaworks.frontierhunts.environment;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class DeadfallLog extends HorizontalDirectionalBlock {
   public static final MapCodec<DeadfallLog> CODEC = simpleCodec(DeadfallLog::new);
   private static final VoxelShape NS = Block.box(4.0, 0.0, 0.0, 12.0, 8.0, 16.0);
   private static final VoxelShape EW = Block.box(0.0, 0.0, 4.0, 16.0, 8.0, 12.0);

   public DeadfallLog(Properties var1) {
      super(var1);
      this.registerDefaultState((BlockState)((BlockState)this.stateDefinition.any()).setValue(FACING, Direction.NORTH));
   }

   protected MapCodec<DeadfallLog> codec() {
      return CODEC;
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> var1) {
      var1.add(new Property[]{FACING});
   }

   public BlockState getStateForPlacement(BlockPlaceContext var1) {
      return (BlockState)this.defaultBlockState().setValue(FACING, var1.getHorizontalDirection());
   }

   protected VoxelShape getShape(BlockState var1, BlockGetter var2, BlockPos var3, CollisionContext var4) {
      return ((Direction)var1.getValue(FACING)).getAxis() == Axis.Z ? NS : EW;
   }
}
