package com.formaworks.frontierhunts.environment;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.expedition.ExpeditionContent;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public final class GroundCover extends Feature<NoneFeatureConfiguration> {
   private static final int BRACKEN = 0;
   private static final int SWORD_FERN = 1;
   private static final int LOW_BRUSH = 2;
   private static final int BRAMBLE = 3;
   private static final int HUCKLEBERRY = 4;
   private static final int MEADOW_GRASS = 5;
   private static final int FINE_GRASS = 6;

   public GroundCover() {
      super(NoneFeatureConfiguration.CODEC);
   }

   public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> var1) {
      if (!(Boolean)HuntConfig.GENERATE_HUNTING_FORESTS.get()) {
         return false;
      } else {
         WorldGenLevel var2 = var1.level();
         RandomSource var3 = var1.random();
         BlockPos var4 = var1.origin();
         BlockState var5 = ((ForestFloor.Litter)ExpeditionContent.FOREST_LITTER.get()).defaultBlockState();
         BlockState var6 = ((ForestFloor.Undergrowth)ExpeditionContent.UNDERGROWTH.get()).defaultBlockState();
         BlockState var7 = ((Block)ExpeditionContent.FOREST_DUFF.get()).defaultBlockState();
         BlockState var8 = Blocks.MOSS_BLOCK.defaultBlockState();
         int var9 = var4.getX() & -16;
         int var10 = var4.getZ() & -16;
         boolean var11 = false;

         for (int var12 = 0; var12 < 16; var12++) {
            for (int var13 = 0; var13 < 16; var13++) {
               BlockPos var14 = Terrain.soil(var2, new BlockPos(var9 + var12, var4.getY(), var10 + var13));
               if (var14 != null) {
                  BlockState var15 = var2.getBlockState(var14);
                  if (var15.isAir() || var15.getBlock() instanceof ForestFloor) {
                     float var16 = Wildness.thickness(var2, var14.getX(), var14.getZ());
                     BlockState var17 = var2.getBlockState(var14.below());
                     if ((var17.is(Blocks.GRASS_BLOCK) || var17.is(Blocks.DIRT) || var17.is(Blocks.COARSE_DIRT)) && var3.nextFloat() < 0.18F + var16 * 0.62F) {
                        Terrain.set(var2, var14.below(), var3.nextFloat() < 0.22F ? var8 : var7);
                     }

                     float var18 = var3.nextFloat();
                     float var19 = 0.02F + var16 * var16 * 0.22F;
                     float var20 = Math.min(0.48F, 0.22F + var16 * 0.28F);
                     if (var18 < var19) {
                        int var21 = var16 > 0.7F && var3.nextFloat() < 0.42F ? 3 : (var3.nextFloat() < 0.5F ? 2 : 4);
                        Terrain.set(var2, var14, (BlockState)var6.setValue(ForestFloor.Undergrowth.VARIANT, var21));
                     } else if (var18 < var19 + var20) {
                        int var22 = var3.nextFloat() < 0.3F ? (var3.nextBoolean() ? 0 : 1) : (var3.nextFloat() < 0.55F ? 5 : 6);
                        Terrain.set(var2, var14, (BlockState)var6.setValue(ForestFloor.Undergrowth.VARIANT, var22));
                     } else {
                        if (!(var18 < 0.86F)) {
                           continue;
                        }

                        Terrain.set(var2, var14, (BlockState)var5.setValue(ForestFloor.Litter.VARIANT, var3.nextInt(3)));
                     }

                     var11 = true;
                  }
               }
            }
         }

         return var11;
      }
   }

   public static BlockState plant(WorldGenLevel var0, BlockPos var1, RandomSource var2, float var3) {
      float var4 = Mth.clamp(Wildness.thickness(var0, var1.getX(), var1.getZ()) + var3, 0.0F, 1.0F);
      BlockState var5 = ((ForestFloor.Undergrowth)ExpeditionContent.UNDERGROWTH.get()).defaultBlockState();
      if (var2.nextFloat() < 0.06F + var4 * 0.34F) {
         return (BlockState)var5.setValue(ForestFloor.Undergrowth.VARIANT, var2.nextFloat() < 0.5F ? 2 : 4);
      } else {
         return var2.nextFloat() < 0.55F
            ? (BlockState)var5.setValue(ForestFloor.Undergrowth.VARIANT, var2.nextFloat() < 0.4F ? (var2.nextBoolean() ? 0 : 1) : (var2.nextBoolean() ? 5 : 6))
            : (BlockState)((ForestFloor.Litter)ExpeditionContent.FOREST_LITTER.get()).defaultBlockState().setValue(ForestFloor.Litter.VARIANT, var2.nextInt(3));
      }
   }

   public static boolean plantable(WorldGenLevel var0, BlockPos var1) {
      return var0.getBlockState(var1).isAir() && var0.getBlockState(var1.below()).is(BlockTags.DIRT);
   }
}
