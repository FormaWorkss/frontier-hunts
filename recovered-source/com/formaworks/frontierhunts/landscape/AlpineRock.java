package com.formaworks.frontierhunts.landscape;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

public final class AlpineRock extends Block {
   public static final MapCodec<AlpineRock> CODEC = simpleCodec(AlpineRock::new);
   public static final IntegerProperty X = IntegerProperty.create("tile_x", 0, 3);
   public static final IntegerProperty Y = IntegerProperty.create("tile_y", 0, 3);
   public static final IntegerProperty Z = IntegerProperty.create("tile_z", 0, 3);
   private final BlockState[] tiles = new BlockState[64];

   public AlpineRock(Properties var1) {
      super(var1);
      this.registerDefaultState((BlockState)((BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(X, 0)).setValue(Y, 0)).setValue(Z, 0));

      for (int var2 = 0; var2 < 4; var2++) {
         for (int var3 = 0; var3 < 4; var3++) {
            for (int var4 = 0; var4 < 4; var4++) {
               this.tiles[var2 << 4 | var3 << 2 | var4] = (BlockState)((BlockState)((BlockState)this.defaultBlockState().setValue(X, var2)).setValue(Y, var3))
                  .setValue(Z, var4);
            }
         }
      }
   }

   protected MapCodec<? extends Block> codec() {
      return CODEC;
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> var1) {
      var1.add(new Property[]{X, Y, Z});
   }

   public BlockState at(int var1, int var2, int var3) {
      return this.tiles[(var1 & 3) << 4 | (var2 & 3) << 2 | var3 & 3];
   }

   public BlockState getStateForPlacement(BlockPlaceContext var1) {
      BlockPos var2 = var1.getClickedPos();
      return this.at(var2.getX(), var2.getY(), var2.getZ());
   }
}
