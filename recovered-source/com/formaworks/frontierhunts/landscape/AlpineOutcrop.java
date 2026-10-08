package com.formaworks.frontierhunts.landscape;

import com.formaworks.frontierhunts.environment.Terrain;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class AlpineOutcrop extends Block implements SimpleWaterloggedBlock {
   public static final IntegerProperty PART = IntegerProperty.create("part", 0, 7);
   public static final IntegerProperty FORM = IntegerProperty.create("form", 0, 3);
   public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
   private static final VoxelShape[] SHAPES = new VoxelShape[8];

   public AlpineOutcrop(Properties var1) {
      super(var1);
      this.registerDefaultState(
         (BlockState)((BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(PART, 0)).setValue(FORM, 0)).setValue(WATERLOGGED, false)
      );
   }

   protected MapCodec<? extends Block> codec() {
      return simpleCodec(AlpineOutcrop::new);
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> var1) {
      var1.add(new Property[]{PART, FORM, WATERLOGGED});
   }

   protected VoxelShape getShape(BlockState var1, BlockGetter var2, BlockPos var3, CollisionContext var4) {
      return SHAPES[var1.getValue(PART)];
   }

   protected FluidState getFluidState(BlockState var1) {
      return var1.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(var1);
   }

   protected BlockState updateShape(BlockState var1, Direction var2, BlockState var3, LevelAccessor var4, BlockPos var5, BlockPos var6) {
      if ((Boolean)var1.getValue(WATERLOGGED)) {
         var4.scheduleTick(var5, Fluids.WATER, Fluids.WATER.getTickDelay(var4));
      }

      int var7 = (Integer)var1.getValue(PART);
      BlockPos var8 = var5.offset(-(var7 & 1), -(var7 >> 2), -(var7 >> 1 & 1));
      if (var7 != 0 && !var4.getBlockState(var8).is(this)) {
         return var1.getValue(WATERLOGGED) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
      } else {
         return var1;
      }
   }

   protected void onRemove(BlockState var1, Level var2, BlockPos var3, BlockState var4, boolean var5) {
      if (!var4.is(this) && !var2.isClientSide) {
         int var6 = (Integer)var1.getValue(PART);
         BlockPos var7 = var3.offset(-(var6 & 1), -(var6 >> 2), -(var6 >> 1 & 1));
         if (var6 != 0 && var2.getBlockState(var7).is(this)) {
            var2.removeBlock(var7, false);
         }

         if (var6 == 0) {
            for (int var8 = 1; var8 < 8; var8++) {
               BlockPos var9 = var7.offset(var8 & 1, var8 >> 2, var8 >> 1 & 1);
               BlockState var10 = var2.getBlockState(var9);
               if (var10.is(this)) {
                  var2.setBlock(var9, var10.getValue(WATERLOGGED) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
               }
            }
         }
      }

      super.onRemove(var1, var2, var3, var4, var5);
   }

   public static boolean place(WorldGenLevel var0, BlockPos var1, int var2, boolean var3) {
      Block var4 = AlpineRegistration.prop(var3 ? "mossy_outcrop" : "river_outcrop");

      for (int var5 = 0; var5 < 8; var5++) {
         BlockPos var6 = var1.offset(var5 & 1, var5 >> 2, var5 >> 1 & 1);
         BlockState var7 = var0.getBlockState(var6);
         if (!var7.isAir() && !var7.is(Blocks.WATER)) {
            return false;
         }
      }

      for (int var8 = 0; var8 < 4; var8++) {
         BlockPos var10 = var1.offset(var8 & 1, -1, var8 >> 1 & 1);
         if (var0.getBlockState(var10).getCollisionShape(var0, var10).isEmpty()) {
            return false;
         }
      }

      for (int var9 = 0; var9 < 8; var9++) {
         BlockPos var11 = var1.offset(var9 & 1, var9 >> 2, var9 >> 1 & 1);
         Terrain.set(
            var0,
            var11,
            (BlockState)((BlockState)((BlockState)var4.defaultBlockState().setValue(PART, var9)).setValue(FORM, var2))
               .setValue(WATERLOGGED, var0.getFluidState(var11).getType() == Fluids.WATER)
         );
      }

      return true;
   }

   static {
      for (int var0 = 0; var0 < 8; var0++) {
         VoxelShape var1 = Shapes.empty();
         int var2 = var0 & 1;
         int var3 = var0 >> 1 & 1;
         int var4 = var0 >> 2;

         for (int var5 = 0; var5 < 4; var5++) {
            for (int var6 = 0; var6 < 4; var6++) {
               for (int var7 = 0; var7 < 4; var7++) {
                  double var8 = (double)var2 + ((double)var5 + 0.5) / 4.0 - 1.0;
                  double var10 = (double)var3 + ((double)var7 + 0.5) / 4.0 - 1.0;
                  double var12 = (double)var4 + ((double)var6 + 0.5) / 4.0;
                  double var14 = var12 < 0.5 ? 0.94 : (var12 < 1.1 ? 0.88 : 0.58);
                  if (var12 < 1.75 && var8 * var8 + var10 * var10 < var14 * var14) {
                     var1 = Shapes.or(
                        var1,
                        Block.box(
                           (double)(var5 * 4), (double)(var6 * 4), (double)(var7 * 4), (double)(var5 * 4 + 4), (double)(var6 * 4 + 4), (double)(var7 * 4 + 4)
                        )
                     );
                  }
               }
            }
         }

         SHAPES[var0] = var1.optimize();
      }
   }
}
