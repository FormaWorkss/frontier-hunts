package com.formaworks.frontierhunts.environment;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class WhiteWater extends Block {
   public static final MapCodec<WhiteWater> CODEC = simpleCodec(WhiteWater::new);

   public WhiteWater(Properties var1) {
      super(var1);
   }

   protected MapCodec<WhiteWater> codec() {
      return CODEC;
   }

   protected VoxelShape getShape(BlockState var1, BlockGetter var2, BlockPos var3, CollisionContext var4) {
      return Shapes.empty();
   }

   protected VoxelShape getCollisionShape(BlockState var1, BlockGetter var2, BlockPos var3, CollisionContext var4) {
      return Shapes.empty();
   }

   protected boolean propagatesSkylightDown(BlockState var1, BlockGetter var2, BlockPos var3) {
      return true;
   }

   protected boolean isPathfindable(BlockState var1, PathComputationType var2) {
      return true;
   }

   protected float getShadeBrightness(BlockState var1, BlockGetter var2, BlockPos var3) {
      return 1.0F;
   }
}
