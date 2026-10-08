package com.formaworks.frontierhunts.landscape;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

public final class AlpineWeatheredRock extends Block {
   public static final IntegerProperty WEATHER = IntegerProperty.create("weather", 0, 3);
   public static final MapCodec<AlpineWeatheredRock> CODEC = simpleCodec(AlpineWeatheredRock::new);

   public AlpineWeatheredRock(Properties var1) {
      super(var1);
      this.registerDefaultState((BlockState)((BlockState)this.stateDefinition.any()).setValue(WEATHER, 0));
   }

   protected MapCodec<? extends Block> codec() {
      return CODEC;
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> var1) {
      var1.add(new Property[]{WEATHER});
   }
}
