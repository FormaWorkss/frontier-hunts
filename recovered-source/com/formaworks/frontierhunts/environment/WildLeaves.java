package com.formaworks.frontierhunts.environment;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;

public final class WildLeaves extends LeavesBlock {
   public static final BooleanProperty EDGE = BooleanProperty.create("edge");
   public static final MapCodec<WildLeaves> CODEC = simpleCodec(WildLeaves::new);

   public WildLeaves(Properties var1) {
      super(var1);
      this.registerDefaultState((BlockState)this.defaultBlockState().setValue(EDGE, true));
   }

   public MapCodec<WildLeaves> codec() {
      return CODEC;
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> var1) {
      super.createBlockStateDefinition(var1);
      var1.add(new Property[]{EDGE});
   }

   protected boolean skipRendering(BlockState var1, BlockState var2, Direction var3) {
      return var2.is(this) || super.skipRendering(var1, var2, var3);
   }

   protected BlockState updateShape(BlockState var1, Direction var2, BlockState var3, LevelAccessor var4, BlockPos var5, BlockPos var6) {
      BlockState var7 = super.updateShape(var1, var2, var3, var4, var5, var6);
      if (var7.hasProperty(EDGE) && !(Boolean)var7.getValue(EDGE) && !var3.is(this)) {
         var7 = (BlockState)var7.setValue(EDGE, true);
      }

      return var7;
   }
}
