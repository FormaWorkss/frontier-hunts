package com.formaworks.frontierhunts.landscape;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class AlpineOvergrowth extends Block {
   public static final MapCodec<AlpineOvergrowth> CODEC = simpleCodec(AlpineOvergrowth::new);
   public static final IntegerProperty FACES = IntegerProperty.create("faces", 1, 63);

   public AlpineOvergrowth(Properties var1) {
      super(var1);
      this.registerDefaultState((BlockState)((BlockState)this.stateDefinition.any()).setValue(FACES, 1));
   }

   protected MapCodec<? extends Block> codec() {
      return CODEC;
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> var1) {
      var1.add(new Property[]{FACES});
   }

   protected VoxelShape getShape(BlockState var1, BlockGetter var2, BlockPos var3, CollisionContext var4) {
      return Shapes.empty();
   }

   protected BlockState updateShape(BlockState var1, Direction var2, BlockState var3, LevelAccessor var4, BlockPos var5, BlockPos var6) {
      int var7 = 1 << var2.ordinal();
      int var8 = (Integer)var1.getValue(FACES);
      if ((var8 & var7) != 0 && !var3.isFaceSturdy(var4, var6, var2.getOpposite())) {
         var8 &= ~var7;
      }

      return var8 == 0 ? Blocks.AIR.defaultBlockState() : (BlockState)var1.setValue(FACES, var8);
   }
}
