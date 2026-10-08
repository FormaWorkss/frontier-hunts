package com.formaworks.frontierhunts.landscape;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class AlpineTurf extends Block {
   public static final MapCodec<AlpineTurf> CODEC = simpleCodec(AlpineTurf::new);
   public static final IntegerProperty LAYERS = IntegerProperty.create("layers", 1, 7);
   private static final VoxelShape[] SHAPES = new VoxelShape[8];

   public AlpineTurf(Properties var1) {
      super(var1);
      this.registerDefaultState((BlockState)((BlockState)this.stateDefinition.any()).setValue(LAYERS, 4));
   }

   protected MapCodec<? extends Block> codec() {
      return CODEC;
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> var1) {
      var1.add(new Property[]{LAYERS});
   }

   private static VoxelShape shape(BlockState var0) {
      return SHAPES[var0.getValue(LAYERS)];
   }

   protected VoxelShape getShape(BlockState var1, BlockGetter var2, BlockPos var3, CollisionContext var4) {
      return shape(var1);
   }

   protected VoxelShape getCollisionShape(BlockState var1, BlockGetter var2, BlockPos var3, CollisionContext var4) {
      return shape(var1);
   }

   protected VoxelShape getBlockSupportShape(BlockState var1, BlockGetter var2, BlockPos var3) {
      return shape(var1);
   }

   protected VoxelShape getVisualShape(BlockState var1, BlockGetter var2, BlockPos var3, CollisionContext var4) {
      return shape(var1);
   }

   protected boolean useShapeForLightOcclusion(BlockState var1) {
      return false;
   }

   protected boolean propagatesSkylightDown(BlockState var1, BlockGetter var2, BlockPos var3) {
      return true;
   }

   protected float getShadeBrightness(BlockState var1, BlockGetter var2, BlockPos var3) {
      return 1.0F;
   }

   protected boolean isPathfindable(BlockState var1, PathComputationType var2) {
      return var2 == PathComputationType.LAND;
   }

   protected boolean canBeReplaced(BlockState var1, BlockPlaceContext var2) {
      return true;
   }

   protected boolean canSurvive(BlockState var1, LevelReader var2, BlockPos var3) {
      BlockState var4 = var2.getBlockState(var3.below());
      return Block.isFaceFull(var4.getCollisionShape(var2, var3.below()), Direction.UP) && !var4.is(Blocks.BARRIER);
   }

   protected BlockState updateShape(BlockState var1, Direction var2, BlockState var3, LevelAccessor var4, BlockPos var5, BlockPos var6) {
      return !var1.canSurvive(var4, var5) ? Blocks.AIR.defaultBlockState() : super.updateShape(var1, var2, var3, var4, var5, var6);
   }

   static {
      for (int var0 = 1; var0 < 8; var0++) {
         SHAPES[var0] = Block.box(0.0, 0.0, 0.0, 16.0, (double)(var0 * 2), 16.0);
      }
   }
}
