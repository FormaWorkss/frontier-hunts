package com.formaworks.frontierhunts.landscape;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class AlpineFlow extends Block {
   public static final MapCodec<AlpineFlow> CODEC = simpleCodec(AlpineFlow::new);
   public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
   public static final IntegerProperty STRENGTH = IntegerProperty.create("strength", 0, 2);
   public static final IntegerProperty X = IntegerProperty.create("tile_x", 0, 3);
   public static final IntegerProperty Z = IntegerProperty.create("tile_z", 0, 3);

   public AlpineFlow(Properties var1) {
      super(var1);
      this.registerDefaultState(
         (BlockState)((BlockState)((BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(FACING, Direction.NORTH)).setValue(STRENGTH, 0))
               .setValue(X, 0))
            .setValue(Z, 0)
      );
   }

   protected MapCodec<? extends Block> codec() {
      return CODEC;
   }

   protected RenderShape getRenderShape(BlockState var1) {
      return RenderShape.INVISIBLE;
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> var1) {
      var1.add(new Property[]{FACING, STRENGTH, X, Z});
   }

   protected VoxelShape getShape(BlockState var1, BlockGetter var2, BlockPos var3, CollisionContext var4) {
      return Shapes.empty();
   }
}
