package com.formaworks.frontierhunts.landscape;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class AlpineBoulder extends Block implements SimpleWaterloggedBlock {
   public static final IntegerProperty FORM = IntegerProperty.create("form", 0, 3);
   public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
   private final VoxelShape shape;

   public AlpineBoulder(Properties var1, int var2) {
      super(var1);
      double var3 = new double[]{5.0, 10.0, 15.0}[var2];
      double var5 = new double[]{3.0, 7.0, 12.0}[var2];
      this.shape = Shapes.or(
         box(8.0 - var3 * 0.46, 0.0, 8.0 - var3 * 0.4, 8.0 + var3 * 0.46, var5 * 0.55, 8.0 + var3 * 0.4),
         new VoxelShape[]{
            box(8.0 - var3 * 0.34, var5 * 0.55, 8.0 - var3 * 0.3, 8.0 + var3 * 0.34, var5 * 0.86, 8.0 + var3 * 0.3),
            box(8.0 - var3 * 0.2, var5 * 0.86, 8.0 - var3 * 0.18, 8.0 + var3 * 0.2, var5, 8.0 + var3 * 0.18)
         }
      );
      this.registerDefaultState((BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(FORM, 0)).setValue(WATERLOGGED, false));
   }

   protected MapCodec<? extends Block> codec() {
      return simpleCodec(var0 -> new AlpineBoulder(var0, 1));
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> var1) {
      var1.add(new Property[]{FORM, WATERLOGGED});
   }

   protected VoxelShape getShape(BlockState var1, BlockGetter var2, BlockPos var3, CollisionContext var4) {
      return this.shape;
   }

   public BlockState getStateForPlacement(BlockPlaceContext var1) {
      return (BlockState)((BlockState)this.defaultBlockState().setValue(FORM, Math.floorMod(var1.getClickedPos().hashCode(), 4)))
         .setValue(WATERLOGGED, var1.getLevel().getFluidState(var1.getClickedPos()).getType() == Fluids.WATER);
   }

   protected FluidState getFluidState(BlockState var1) {
      return var1.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(var1);
   }

   protected BlockState updateShape(BlockState var1, Direction var2, BlockState var3, LevelAccessor var4, BlockPos var5, BlockPos var6) {
      if ((Boolean)var1.getValue(WATERLOGGED)) {
         var4.scheduleTick(var5, Fluids.WATER, Fluids.WATER.getTickDelay(var4));
      }

      return super.updateShape(var1, var2, var3, var4, var5, var6);
   }
}
