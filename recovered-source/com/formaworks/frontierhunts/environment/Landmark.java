package com.formaworks.frontierhunts.environment;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.expedition.ExpeditionContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public final class Landmark extends Feature<NoneFeatureConfiguration> {
   public Landmark() {
      super(NoneFeatureConfiguration.CODEC);
   }

   public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> var1) {
      WorldGenLevel var2 = var1.level();
      RandomSource var3 = var1.random();
      if (!(Boolean)HuntConfig.GENERATE_HUNTING_FORESTS.get()) {
         return false;
      } else {
         BlockPos var4 = Terrain.soil(var2, var1.origin());
         if (var4 == null) {
            return false;
         } else {
            return switch (var3.nextInt(6)) {
               case 0 -> boulders(var2, var4, var3);
               case 1 -> blowdown(var2, var4, var3);
               case 2 -> glade(var2, var4, var3);
               case 3 -> seep(var2, var4, var3);
               case 4 -> bluff(var2, var4, var3);
               default -> thicket(var2, var4, var3);
            };
         }
      }
   }

   private static boolean boulders(WorldGenLevel var0, BlockPos var1, RandomSource var2) {
      int var3 = 4 + var2.nextInt(5);
      BlockState var4 = ((ForestFloor.Boulder)ExpeditionContent.MOSSY_STONE.get()).defaultBlockState();
      int var5 = 0;

      for (int var6 = 0; var6 < 8 + var2.nextInt(10); var6++) {
         int var7 = var2.nextInt(var3 * 2 + 1) - var3;
         int var8 = var2.nextInt(var3 * 2 + 1) - var3;
         if (var7 * var7 + var8 * var8 <= var3 * var3) {
            BlockPos var9 = Terrain.surface(var0, var1.offset(var7, 0, var8));
            if (var9 != null && soft(var0.getBlockState(var9))) {
               set(var0, var9, var4);
               if (var2.nextFloat() < 0.3F) {
                  set(var0, var9.below(), Blocks.MOSSY_COBBLESTONE.defaultBlockState());
                  if (soft(var0.getBlockState(var9.above()))) {
                     set(var0, var9.above(), var4);
                  }
               }

               var5++;
            }
         }
      }

      dress(var0, var1, var3 + 1, var2, 0.55F);
      return var5 > 0;
   }

   private static boolean blowdown(WorldGenLevel var0, BlockPos var1, RandomSource var2) {
      int var3 = 5 + var2.nextInt(4);
      int var4 = 0;

      for (int var5 = 0; var5 < 5 + var2.nextInt(6); var5++) {
         int var6 = var2.nextInt(var3 * 2 + 1) - var3;
         int var7 = var2.nextInt(var3 * 2 + 1) - var3;
         BlockPos var8 = Terrain.soil(var0, var1.offset(var6, 0, var7));
         if (var8 != null
            && ForestStand.plant(var0, var8, var2.nextFloat() < 0.75F ? WildTrees.Kind.LEANER : WildTrees.Kind.SNAG, var2, 0.4F + var2.nextFloat() * 0.6F)) {
            var4++;
         }
      }

      BlockState var10 = ((ForestFloor.Undergrowth)ExpeditionContent.UNDERGROWTH.get()).defaultBlockState();

      for (int var11 = 0; var11 < var3 * var3 * 3; var11++) {
         int var12 = var2.nextInt(var3 * 2 + 1) - var3;
         int var13 = var2.nextInt(var3 * 2 + 1) - var3;
         BlockPos var9 = Terrain.soil(var0, var1.offset(var12, 0, var13));
         if (var9 != null && soft(var0.getBlockState(var9))) {
            set(var0, var9, (BlockState)var10.setValue(ForestFloor.Undergrowth.VARIANT, var2.nextFloat() < 0.6F ? 3 : 2));
         }
      }

      return var4 > 0;
   }

   private static boolean glade(WorldGenLevel var0, BlockPos var1, RandomSource var2) {
      int var3 = 4 + var2.nextInt(4);
      BlockState var4 = ((ForestFloor.Undergrowth)ExpeditionContent.UNDERGROWTH.get()).defaultBlockState();
      BlockState var5 = ((ForestFloor.Litter)ExpeditionContent.FOREST_LITTER.get()).defaultBlockState();
      int var6 = 0;

      for (int var7 = -var3; var7 <= var3; var7++) {
         for (int var8 = -var3; var8 <= var3; var8++) {
            double var9 = Math.sqrt((double)(var7 * var7 + var8 * var8));
            if (!(var9 > (double)var3)) {
               BlockPos var11 = Terrain.soil(var0, var1.offset(var7, 0, var8));
               if (var11 != null && soft(var0.getBlockState(var11))) {
                  if (var2.nextFloat() < 0.32F) {
                     set(var0, var11.below(), Blocks.MOSS_BLOCK.defaultBlockState());
                  }

                  float var12 = (float)(1.0 - var9 / ((double)var3 + 1.5));
                  if (var2.nextFloat() < var12 * 0.92F) {
                     set(var0, var11, (BlockState)var4.setValue(ForestFloor.Undergrowth.VARIANT, var2.nextInt(2)));
                  } else {
                     set(var0, var11, (BlockState)var5.setValue(ForestFloor.Litter.VARIANT, var2.nextInt(3)));
                  }

                  var6++;
               }
            }
         }
      }

      BlockPos var13 = Terrain.soil(var0, var1.offset(var2.nextInt(5) - 2, 0, var2.nextInt(5) - 2));
      if (var13 != null) {
         ForestStand.plant(var0, var13, WildTrees.Kind.LEANER, var2, 0.9F);
      }

      return var6 > 0;
   }

   private static boolean seep(WorldGenLevel var0, BlockPos var1, RandomSource var2) {
      Direction var3 = Plane.HORIZONTAL.getRandomDirection(var2);
      BlockPos var4 = var1;
      int var5 = 6 + var2.nextInt(8);
      int var6 = 0;
      BlockState var7 = ((ForestFloor.Undergrowth)ExpeditionContent.UNDERGROWTH.get()).defaultBlockState();

      for (int var8 = 0; var8 < var5; var8++) {
         BlockPos var9 = Terrain.soil(var0, var4.relative(var3));
         if (var9 == null) {
            break;
         }

         var4 = var9;
         set(
            var0,
            var9.below(),
            var8 < 2 ? Blocks.MUD.defaultBlockState() : (var2.nextFloat() < 0.5F ? Blocks.MUD.defaultBlockState() : Blocks.MOSS_BLOCK.defaultBlockState())
         );

         for (int var10 = -2; var10 <= 2; var10++) {
            if (var10 != 0) {
               BlockPos var11 = Terrain.soil(var0, var4.relative(var3.getClockWise(), var10));
               if (var11 != null && soft(var0.getBlockState(var11)) && var2.nextFloat() < 0.7F - (float)Math.abs(var10) * 0.18F) {
                  set(var0, var11, (BlockState)var7.setValue(ForestFloor.Undergrowth.VARIANT, var2.nextInt(2)));
               }
            }
         }

         var6++;
      }

      return var6 > 0;
   }

   private static boolean bluff(WorldGenLevel var0, BlockPos var1, RandomSource var2) {
      Direction var3 = null;
      int var4 = 0;

      for (Direction var6 : Plane.HORIZONTAL) {
         BlockPos var7 = Terrain.surface(var0, var1.relative(var6, 7));
         if (var7 != null) {
            int var8 = var1.getY() - var7.getY();
            if (var8 > var4) {
               var4 = var8;
               var3 = var6;
            }
         }
      }

      if (var3 != null && var4 >= 4) {
         Direction var23 = var3.getClockWise();
         int var24 = 4 + var2.nextInt(5);
         int var25 = Math.min(3 + var4 + var2.nextInt(5), 18);
         BlockState var26 = Blocks.STONE.defaultBlockState();
         BlockState var9 = Blocks.COBBLESTONE.defaultBlockState();
         BlockState var10 = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
         int var11 = 0;

         for (int var12 = -var24; var12 <= var24; var12++) {
            double var13 = (double)Math.abs(var12) / (double)var24;
            int var15 = (int)Math.round((double)var25 * Math.sqrt(Math.max(0.0, 1.0 - var13 * var13)));
            if (var15 > 1) {
               BlockPos var16 = var1.relative(var23, var12);

               for (int var17 = 0; var17 <= 2; var17++) {
                  BlockPos var18 = var16.relative(var3, var17);
                  BlockPos var19 = Terrain.surface(var0, var18);
                  if (var19 != null) {
                     for (int var20 = 0; var20 < var15 - var17 * 2; var20++) {
                        BlockPos var21 = var19.above(var20);
                        if (Terrain.writable(var0, var21) && soft(var0.getBlockState(var21))) {
                           float var22 = var2.nextFloat();
                           set(var0, var21, var22 < 0.22F ? var10 : (var22 < 0.5F ? var9 : var26));
                           var11++;
                        }
                     }
                  }
               }

               for (int var29 = 3; var29 <= 3 + var2.nextInt(4); var29++) {
                  BlockPos var30 = Terrain.surface(var0, var16.relative(var3, var29));
                  if (var30 != null && !(var2.nextFloat() < 0.45F)) {
                     set(var0, var30, var2.nextFloat() < 0.5F ? ((ForestFloor.Boulder)ExpeditionContent.MOSSY_STONE.get()).defaultBlockState() : var9);
                  }
               }
            }
         }

         if (var11 == 0) {
            return false;
         } else {
            for (int var27 = 0; var27 < 6 + var2.nextInt(6); var27++) {
               BlockPos var28 = Terrain.soil(var0, var1.relative(var23, var2.nextInt(var24 * 2 + 1) - var24).relative(var3.getOpposite(), var2.nextInt(4)));
               if (var28 != null) {
                  if (var2.nextFloat() < 0.35F) {
                     ForestStand.plant(var0, var28, WildTrees.Kind.PINE, var2, 0.6F);
                  } else if (soft(var0.getBlockState(var28))) {
                     set(var0, var28, GroundCover.plant(var0, var28, var2, 0.2F));
                  }
               }
            }

            dress(var0, var1.relative(var3, 6), var24, var2, 0.5F);
            return true;
         }
      } else {
         return false;
      }
   }

   private static boolean thicket(WorldGenLevel var0, BlockPos var1, RandomSource var2) {
      int var3 = 4 + var2.nextInt(5);
      BlockState var4 = ((ForestFloor.Undergrowth)ExpeditionContent.UNDERGROWTH.get()).defaultBlockState();
      int var5 = 0;

      for (int var6 = -var3; var6 <= var3; var6++) {
         for (int var7 = -var3; var7 <= var3; var7++) {
            double var8 = Math.sqrt((double)(var6 * var6 + var7 * var7));
            if (!(var8 > (double)var3) && !((double)var2.nextFloat() < var8 / ((double)var3 + 2.0))) {
               BlockPos var10 = Terrain.soil(var0, var1.offset(var6, 0, var7));
               if (var10 != null && soft(var0.getBlockState(var10))) {
                  set(var0, var10, (BlockState)var4.setValue(ForestFloor.Undergrowth.VARIANT, var2.nextFloat() < 0.45F ? 3 : 2 + var2.nextInt(3)));
                  var5++;
               }
            }
         }
      }

      return var5 > 0;
   }

   private static void dress(WorldGenLevel var0, BlockPos var1, int var2, RandomSource var3, float var4) {
      BlockState var5 = ((ForestFloor.Undergrowth)ExpeditionContent.UNDERGROWTH.get()).defaultBlockState();
      BlockState var6 = ((ForestFloor.Litter)ExpeditionContent.FOREST_LITTER.get()).defaultBlockState();

      for (int var7 = 0; var7 < var2 * var2 * 3; var7++) {
         int var8 = var3.nextInt(var2 * 2 + 1) - var2;
         int var9 = var3.nextInt(var2 * 2 + 1) - var2;
         BlockPos var10 = Terrain.soil(var0, var1.offset(var8, 0, var9));
         if (var10 != null && soft(var0.getBlockState(var10))) {
            set(
               var0,
               var10,
               var3.nextFloat() < var4
                  ? (BlockState)var5.setValue(ForestFloor.Undergrowth.VARIANT, var3.nextInt(5))
                  : (BlockState)var6.setValue(ForestFloor.Litter.VARIANT, var3.nextInt(3))
            );
         }
      }
   }

   private static boolean soft(BlockState var0) {
      return var0.isAir() || var0.is(BlockTags.REPLACEABLE_BY_TREES) || var0.getBlock() instanceof ForestFloor;
   }

   private static void set(WorldGenLevel var0, BlockPos var1, BlockState var2) {
      Terrain.set(var0, var1, var2);
   }
}
