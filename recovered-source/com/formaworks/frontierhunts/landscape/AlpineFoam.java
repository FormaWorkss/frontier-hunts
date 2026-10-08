package com.formaworks.frontierhunts.landscape;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class AlpineFoam extends Block {
   public static final MapCodec<AlpineFoam> CODEC = simpleCodec(AlpineFoam::new);
   public static final IntegerProperty COLUMN = IntegerProperty.create("column", 0, 7);
   public static final IntegerProperty ROW = IntegerProperty.create("row", 0, 15);
   public static final IntegerProperty FACES = IntegerProperty.create("faces", 1, 15);
   public static final BooleanProperty BASE = BooleanProperty.create("base");

   public AlpineFoam(Properties var1) {
      super(var1);
      this.registerDefaultState(
         (BlockState)((BlockState)((BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(FACES, 15)).setValue(BASE, false))
               .setValue(COLUMN, 0))
            .setValue(ROW, 0)
      );
   }

   protected MapCodec<? extends Block> codec() {
      return CODEC;
   }

   protected RenderShape getRenderShape(BlockState var1) {
      return RenderShape.INVISIBLE;
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> var1) {
      var1.add(new Property[]{FACES, BASE, COLUMN, ROW});
   }

   protected VoxelShape getShape(BlockState var1, BlockGetter var2, BlockPos var3, CollisionContext var4) {
      return Shapes.empty();
   }
}
