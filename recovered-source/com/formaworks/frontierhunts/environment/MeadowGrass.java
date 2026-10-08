package com.formaworks.frontierhunts.environment;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.expedition.ExpeditionContent;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public final class MeadowGrass extends Feature<NoneFeatureConfiguration> {
   private static final int MEADOW = 5;
   private static final int FINE = 6;
   private static final int BRACKEN = 0;
   private static final int SWORD_FERN = 1;

   public MeadowGrass() {
      super(NoneFeatureConfiguration.CODEC);
   }

   public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> var1) {
      if (!(Boolean)HuntConfig.GENERATE_HUNTING_FORESTS.get()) {
         return false;
      } else {
         WorldGenLevel var2 = var1.level();
         RandomSource var3 = var1.random();
         BlockPos var4 = var1.origin();
         BlockState var5 = ((ForestFloor.Undergrowth)ExpeditionContent.UNDERGROWTH.get()).defaultBlockState();
         int var6 = var4.getX() & -16;
         int var7 = var4.getZ() & -16;
         boolean var8 = false;

         for (int var9 = 0; var9 < 16; var9++) {
            for (int var10 = 0; var10 < 16; var10++) {
               int var11 = var6 + var9;
               int var12 = var7 + var10;
               float var13 = Wildness.lush(var2, var11, var12);
               if (!(var13 < 0.06F) && !(var3.nextFloat() > var13)) {
                  BlockPos var14 = Terrain.soil(var2, new BlockPos(var11, var4.getY(), var12));
                  if (var14 != null) {
                     BlockState var15 = var2.getBlockState(var14);
                     if (var15.isAir() || var15.getBlock() instanceof ForestFloor) {
                        BlockState var16 = var2.getBlockState(var14.below());
                        if (var16.is(Blocks.GRASS_BLOCK)
                           || var16.is(Blocks.DIRT)
                           || var16.is(Blocks.COARSE_DIRT)
                           || var16.is(Blocks.PODZOL)
                           || var16.is((Block)ExpeditionContent.FOREST_DUFF.get())) {
                           float var17 = var3.nextFloat();
                           int var18 = var17 < Mth.clamp(var13 * 1.15F, 0.0F, 0.88F) ? 5 : (var17 < 0.94F ? 6 : (var3.nextBoolean() ? 0 : 1));
                           Terrain.set(var2, var14, (BlockState)var5.setValue(ForestFloor.Undergrowth.VARIANT, var18));
                           var8 = true;
                        }
                     }
                  }
               }
            }
         }

         return var8;
      }
   }
}
