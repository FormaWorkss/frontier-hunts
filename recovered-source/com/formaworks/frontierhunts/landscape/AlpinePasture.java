package com.formaworks.frontierhunts.landscape;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

public final class AlpinePasture extends BushBlock {
   public static final MapCodec<AlpinePasture> CODEC = simpleCodec(AlpinePasture::new);
   public static final IntegerProperty HEIGHT = IntegerProperty.create("height", 0, 2);
   public static final IntegerProperty SINK = IntegerProperty.create("sink", 0, 7);

   public AlpinePasture(Properties var1) {
      super(var1);
      this.registerDefaultState((BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(HEIGHT, 0)).setValue(SINK, 0));
   }

   protected MapCodec<? extends BushBlock> codec() {
      return CODEC;
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> var1) {
      var1.add(new Property[]{HEIGHT, SINK});
   }

   protected boolean mayPlaceOn(BlockState var1, BlockGetter var2, BlockPos var3) {
      return var1.getBlock() instanceof AlpineTurf || super.mayPlaceOn(var1, var2, var3);
   }

   public static int sinkOver(BlockState var0) {
      return var0.getBlock() instanceof AlpineTurf ? 8 - (Integer)var0.getValue(AlpineTurf.LAYERS) : 0;
   }

   public BlockState getStateForPlacement(BlockPlaceContext var1) {
      BlockState var2 = super.getStateForPlacement(var1);
      return var2 == null ? null : (BlockState)var2.setValue(SINK, sinkOver(var1.getLevel().getBlockState(var1.getClickedPos().below())));
   }
}
