package com.formaworks.frontierhunts.landscape;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
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
import net.minecraft.world.phys.shapes.VoxelShape;

public final class AlpineReeds extends BushBlock implements SimpleWaterloggedBlock {
   public static final IntegerProperty SIZE = AlpineThicket.SIZE;
   public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

   public AlpineReeds(Properties var1) {
      super(var1);
      this.registerDefaultState((BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(SIZE, 1)).setValue(WATERLOGGED, false));
   }

   protected MapCodec<? extends BushBlock> codec() {
      return simpleCodec(AlpineReeds::new);
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> var1) {
      var1.add(new Property[]{SIZE, WATERLOGGED});
   }

   protected VoxelShape getShape(BlockState var1, BlockGetter var2, BlockPos var3, CollisionContext var4) {
      return Block.box(2.0, 0.0, 2.0, 14.0, (double)Math.min(16, 12 + (Integer)var1.getValue(SIZE) * 2), 14.0);
   }

   protected boolean mayPlaceOn(BlockState var1, BlockGetter var2, BlockPos var3) {
      return var1.is(BlockTags.DIRT)
         || var1.is(Blocks.MUD)
         || var1.is(Blocks.CLAY)
         || var1.is(Blocks.GRAVEL)
         || var1.is(Blocks.SAND)
         || var1.is(Blocks.MOSS_BLOCK)
         || var1.is(Blocks.MOSSY_COBBLESTONE)
         || var1.is(Blocks.STONE);
   }

   public BlockState getStateForPlacement(BlockPlaceContext var1) {
      FluidState var2 = var1.getLevel().getFluidState(var1.getClickedPos());
      return (BlockState)this.defaultBlockState().setValue(WATERLOGGED, var2.getType() == Fluids.WATER && var2.isSource());
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
