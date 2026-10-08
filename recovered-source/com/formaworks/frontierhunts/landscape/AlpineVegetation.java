package com.formaworks.frontierhunts.landscape;

import com.formaworks.frontierhunts.environment.Terrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.PinkPetalsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;

final class AlpineVegetation {
   static void ground(WorldGenLevel var0, AlpineLayout var1, int var2, int var3, AlpineLayout.Sample var4, BlockPos var5) {
      ground(var0, var1, var2, var3, var4, var5, Double.NaN);
   }

   static void ground(WorldGenLevel var0, AlpineLayout var1, int var2, int var3, AlpineLayout.Sample var4, BlockPos var5, double var6) {
      BlockState var8 = var0.getBlockState(var5.below());
      if (var0.isEmptyBlock(var5) && (var8.is(BlockTags.DIRT) || var8.is(Blocks.MOSS_BLOCK)) && !(var4.ground() > var4.snowLine() - 12.0)) {
         if (var1.version() < 20 || (var4.fallStyle() & 7) != 0 || !(var1.trail((double)var2, (double)var3) > 0.45) || !Double.isNaN(var6) && !(var6 < 0.65)) {
            double var9 = var1.noise((double)var2 / 95.0, (double)var3 / 95.0, 8201L);
            double var11 = var1.noise((double)var2 / 23.0, (double)var3 / 23.0, 8203L);
            double var13 = var1.variation(var2, var3, 8205L);
            boolean var15 = var4.forest() > 0.45;
            if (var1.version() >= 14) {
               wild(var0, var1, var2, var3, var4, var5, var8, var9, var11, var13, var6);
            } else if (var1.version() >= 8) {
               mosaic(var0, var1, var2, var3, var4, var5, var9, var11, var13, var15);
            } else if (var1.version() >= 5) {
               full(var0, var1, var2, var3, var4, var5, var9, var11, var13, var15);
            } else if (var15) {
               if (var13 < 0.34) {
                  Terrain.set(var0, var5, (var4.moisture() > 0.48 ? Blocks.FERN : Blocks.SHORT_GRASS).defaultBlockState());
               } else if (var13 < 0.48) {
                  Terrain.set(var0, var5, Blocks.MOSS_CARPET.defaultBlockState());
               }
            } else {
               double var16 = AlpineLayout.smooth(-0.35, 0.4, var9 + var11 * 0.35);
               if (var13 < 0.13 + var16 * 0.13) {
                  int var18 = var16 < 0.3 ? 0 : (var16 < 0.66 ? 1 : 2);
                  if (var18 < 2 || var0.isEmptyBlock(var5.above())) {
                     Terrain.set(
                        var0, var5, (BlockState)((AlpinePasture)AlpineRegistration.PASTURE.get()).defaultBlockState().setValue(AlpinePasture.HEIGHT, var18)
                     );
                  }
               } else if (var13 < 0.5 + var16 * 0.27) {
                  if (var16 > 0.52 && var1.variation(var2, var3, 8209L) < var16 * 0.52 && var0.isEmptyBlock(var5.above())) {
                     Terrain.set(var0, var5, (BlockState)Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
                     Terrain.set(var0, var5.above(), (BlockState)Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
                  } else {
                     Terrain.set(var0, var5, Blocks.SHORT_GRASS.defaultBlockState());
                  }
               } else if (var13 > 0.987 && var4.moisture() > 0.43) {
                  Terrain.set(var0, var5, (var9 > 0.1 ? Blocks.CORNFLOWER : Blocks.OXEYE_DAISY).defaultBlockState());
               }
            }
         } else if (var1.variation(var2, var3, 8219L) < 0.06) {
            Terrain.set(var0, var5, Blocks.SHORT_GRASS.defaultBlockState());
         }
      }
   }

   private static void mosaic(
      WorldGenLevel var0, AlpineLayout var1, int var2, int var3, AlpineLayout.Sample var4, BlockPos var5, double var6, double var8, double var10, boolean var12
   ) {
      double var13 = AlpineLayout.smooth(-0.15, 0.35, var1.noise((double)var2 / 18.0, (double)var3 / 18.0, 8251L));
      double var15 = var12 ? 0.04 + var13 * 0.15 : 0.008 + var13 * 0.018;
      if (var10 < var15) {
         if (var1.version() >= 9) {
            String var17 = var1.variation(var2, var3, 8253L) < 0.38
               ? "spreading_fern"
               : (var4.distance() < var4.width() + 20.0 ? "river_brush" : "woodland_bush");
            int var18 = var0.isEmptyBlock(var5.above()) ? (var1.variation(var2, var3, 8259L) < 0.65 ? 2 : 1) : 0;
            if (var1.version() >= 10 && var1.variation(var2, var3, 8425L) < 0.48) {
               var17 = var12 ? (var1.variation(var2, var3, 8427L) < 0.45 ? "arching_fern" : "broadleaf_thicket") : "flowering_bramble";
            }

            Terrain.set(var0, var5, (BlockState)AlpineRegistration.prop(var17).defaultBlockState().setValue(AlpineThicket.SIZE, var18));
         } else {
            Terrain.set(var0, var5, (var1.variation(var2, var3, 8253L) < 0.18 ? Blocks.FLOWERING_AZALEA : Blocks.AZALEA).defaultBlockState());
         }
      } else if (var12) {
         if (var10 < 0.42 && var0.isEmptyBlock(var5.above()) && var4.moisture() > 0.45) {
            doublePlant(var0, var5, Blocks.LARGE_FERN);
         } else if (var10 < 0.7) {
            Terrain.set(var0, var5, (var4.moisture() > 0.45 ? Blocks.FERN : Blocks.SHORT_GRASS).defaultBlockState());
         } else if (var10 < 0.86) {
            Terrain.set(var0, var5, Blocks.MOSS_CARPET.defaultBlockState());
         } else if (var10 > 0.975) {
            Terrain.set(var0, var5, (var8 > 0.1 ? Blocks.LILY_OF_THE_VALLEY : Blocks.BLUE_ORCHID).defaultBlockState());
         }
      } else {
         double var21 = AlpineLayout.smooth(-0.08, 0.24, var1.noise((double)var2 / 130.0, (double)var3 / 130.0, 8255L));
         if (var1.variation(var2, var3, 8257L) < var21 * 0.86) {
            full(var0, var1, var2, var3, var4, var5, var6, var8, var10, false);
         } else if (var10 > 0.97) {
            Terrain.set(var0, var5, (var8 > 0.16 ? Blocks.CORNFLOWER : Blocks.OXEYE_DAISY).defaultBlockState());
         } else {
            double var19 = AlpineLayout.smooth(-0.3, 0.4, var6 + var8 * 0.3);
            if (var10 < 0.2 + var19 * 0.45 && var0.isEmptyBlock(var5.above())) {
               doublePlant(var0, var5, Blocks.TALL_GRASS);
            } else if (var10 < 0.89) {
               Terrain.set(var0, var5, Blocks.SHORT_GRASS.defaultBlockState());
            }
         }
      }
   }

   private static void wild(
      WorldGenLevel var0,
      AlpineLayout var1,
      int var2,
      int var3,
      AlpineLayout.Sample var4,
      BlockPos var5,
      BlockState var6,
      double var7,
      double var9,
      double var11,
      double var13
   ) {
      double var15 = var4.forest();
      boolean var17 = var1.version() >= 21;
      boolean var18 = var6.is(Blocks.PODZOL)
         || var6.is(Blocks.COARSE_DIRT)
         || var17 && (var6.is(Blocks.ROOTED_DIRT) || var6.is(AlpineRegistration.prop("forest_duff")));
      boolean var19 = var4.distance() < var4.width() + 18.0;
      int var20 = var4.biome();
      boolean var21 = var20 == 6 || var20 == 7 || var20 == 8 || var20 == 24 || var20 == 25;
      boolean var22 = var20 == 10 || var20 == 12 || var20 == 16 || var20 == 17 || var19;
      boolean var23 = var4.ground() > var4.snowLine() - 110.0;
      if (var1.version() >= 15 && AlpineLayout.soil(var20)) {
         districtCover(var0, var1, var2, var3, var4, var5, var20);
      } else {
         double var24 = var1.noise((double)var2 / 13.0, (double)var3 / 13.0, 8461L) + 0.45 * var1.noise((double)var2 / 5.0, (double)var3 / 5.0, 8463L);
         double var26 = AlpineLayout.smooth(0.28, 0.8, var15) * AlpineLayout.smooth(-0.18, 0.5, var24);
         if (var1.version() >= 21) {
            var26 *= 1.0 - 0.45 * AlpineLayout.smooth(0.7, 0.95, var15);
         }

         double var28 = AlpineLayout.smooth(0.1, 0.32, var15) * (1.0 - AlpineLayout.smooth(0.42, 0.66, var15));
         double var30 = var1.variation(var2, var3, 8465L);
         boolean var32 = var0.isEmptyBlock(var5.above());
         boolean var33 = var20 == 27;
         if (var33 || var1.version() >= 15 && var28 > 0.35 && var1.noise((double)var2 / 23.0, (double)var3 / 23.0, 8481L) > 0.52) {
            double var34 = var1.noise((double)var2 / 9.0, (double)var3 / 9.0, 8483L) + 0.5 * var1.noise((double)var2 / 4.0, (double)var3 / 4.0, 8485L);
            if (var34 > (var33 ? -0.1 : 0.3) && var1.variation(var2, var3, 8487L) < (var33 ? 0.5 : 0.34)) {
               Terrain.set(
                  var0,
                  var5,
                  (BlockState)AlpineRegistration.prop("fireweed")
                     .defaultBlockState()
                     .setValue(AlpineThicket.SIZE, var32 ? (var1.variation(var2, var3, 8489L) < 0.6 ? 2 : 1) : 0)
               );
               return;
            }
         }

         if (var30 < var26 * 0.58 + var28 * 0.16) {
            double var41 = var1.variation(var2, var3, 8469L);
            String var36;
            if (var28 > var26) {
               var36 = var41 < 0.35 ? "flowering_bramble" : (var41 < 0.65 ? "woodland_bush" : (var41 < 0.82 ? "juniper_shrub" : "spreading_fern"));
            } else if (var22) {
               var36 = var41 < 0.38 ? "river_brush" : (var41 < 0.62 ? "broadleaf_thicket" : (var41 < 0.84 ? "arching_fern" : "woodland_bush"));
            } else if (var21) {
               var36 = var41 < 0.32
                  ? "woodland_bush"
                  : (var41 < 0.52 ? "broadleaf_thicket" : (var41 < 0.68 ? "flowering_bramble" : (var41 < 0.86 ? "spreading_fern" : "huckleberry_shrub")));
            } else if (var23) {
               var36 = var41 < 0.42 ? "juniper_shrub" : (var41 < 0.74 ? "huckleberry_shrub" : "spruce_seedling");
            } else {
               var36 = var41 < 0.3
                  ? "huckleberry_shrub"
                  : (var41 < 0.48 ? "spruce_seedling" : (var41 < 0.68 ? "arching_fern" : (var41 < 0.84 ? "woodland_bush" : "spreading_fern")));
            }

            double var37 = var1.variation(var2, var3, 8471L);
            int var39 = var32 ? (var37 < 0.25 + var26 * 0.4 ? 2 : 1) : (var37 < 0.5 ? 1 : 0);
            Terrain.set(var0, var5, (BlockState)AlpineRegistration.prop(var36).defaultBlockState().setValue(AlpineThicket.SIZE, var39));
         } else {
            if (var17 && var15 > 0.22 && !var22) {
               double var42 = 0.012 + 0.1 * AlpineLayout.smooth(0.4, 0.9, var15) * (var18 ? 1.25 : 0.8);
               if (var1.variation(var2, var3, 8491L) < var42) {
                  double var45 = var1.variation(var2, var3, 8493L);
                  int var50 = var21 ? (int)(var45 * 4.0) : (var45 < 0.62 ? (int)(var45 / 0.62 * 4.0) : (var45 < 0.82 ? 4 : 5));
                  Direction var51 = Plane.HORIZONTAL.getRandomDirection(RandomSource.create(var1.hash(var2, var3, 8495L)));
                  Terrain.set(
                     var0,
                     var5,
                     (BlockState)((BlockState)AlpineRegistration.prop("forest_sticks").defaultBlockState().setValue(AlpineSticks.VARIANT, Math.min(5, var50)))
                        .setValue(AlpineSticks.FACING, var51)
                  );
                  return;
               }
            }

            if (!(var15 > 0.4)) {
               if (var1.version() >= 18 && !Double.isNaN(var13)) {
                  meadow(var0, var1, var2, var3, var4, var5, var7, var9, var11, var13);
               } else {
                  full(var0, var1, var2, var3, var4, var5, var7, var9, var11, false);
               }
            } else {
               double var43 = var1.variation(var2, var3, 8467L);
               double var44 = var4.moisture();
               if (var17) {
                  if (var43 < 0.09 + 0.09 * var44) {
                     String var38 = !(var44 > 0.5) && var21 ? "spreading_fern" : "arching_fern";
                     if (!var21 && var1.noise((double)var2 / 31.0, (double)var3 / 31.0, 8473L) > 0.25) {
                        var38 = "spreading_fern";
                     }

                     Terrain.set(
                        var0,
                        var5,
                        (BlockState)AlpineRegistration.prop(var38)
                           .defaultBlockState()
                           .setValue(AlpineThicket.SIZE, var11 < 0.4 ? 0 : (var11 < 0.8 ? 1 : (var32 ? 2 : 1)))
                     );
                  } else if (var43 < 0.15) {
                     BlockState var46 = AlpineRegistration.prop("fallen_branch").defaultBlockState();
                     var46 = with(var46, "facing", Plane.HORIZONTAL.getRandomDirection(RandomSource.create(var1.hash(var2, var3, 8475L))));
                     var46 = with(var46, "snapped", var1.variation(var2, var3, 8477L) < 0.35);
                     Terrain.set(var0, var5, var46);
                  } else if (var43 < 0.19 && var44 > 0.45 && !var18) {
                     Terrain.set(var0, var5, Blocks.MOSS_CARPET.defaultBlockState());
                  } else if (!var18 && var43 < 0.19 + 0.36 * (1.0 - AlpineLayout.smooth(0.45, 0.9, var15)) + 0.1) {
                     woodlandSward(var0, var1, var2, var3, var5, var32);
                  } else if (var43 > 0.992) {
                     Terrain.set(var0, var5, (var9 > 0.1 ? Blocks.LILY_OF_THE_VALLEY : Blocks.FERN).defaultBlockState());
                  }
               } else {
                  double var49 = 1.0 - AlpineLayout.smooth(0.45, 0.9, var15);
                  if (var43 < 0.16 + 0.14 * var44) {
                     String var40 = !(var44 > 0.5) && var21 ? "spreading_fern" : "arching_fern";
                     if (!var21 && var1.noise((double)var2 / 31.0, (double)var3 / 31.0, 8473L) > 0.25) {
                        var40 = "spreading_fern";
                     }

                     Terrain.set(
                        var0,
                        var5,
                        (BlockState)AlpineRegistration.prop(var40)
                           .defaultBlockState()
                           .setValue(AlpineThicket.SIZE, var11 < 0.4 ? 0 : (var11 < 0.8 ? 1 : (var32 ? 2 : 1)))
                     );
                  } else if (var43 < 0.21) {
                     BlockState var52 = AlpineRegistration.prop("fallen_branch").defaultBlockState();
                     var52 = with(var52, "facing", Plane.HORIZONTAL.getRandomDirection(RandomSource.create(var1.hash(var2, var3, 8475L))));
                     var52 = with(var52, "snapped", var1.variation(var2, var3, 8477L) < 0.35);
                     Terrain.set(var0, var5, var52);
                  } else if (var43 < 0.31 && var44 > 0.4) {
                     Terrain.set(var0, var5, Blocks.MOSS_CARPET.defaultBlockState());
                  } else if (!var18 && var43 < 0.31 + 0.42 * var49 + 0.12) {
                     if (var1.version() >= 15) {
                        woodlandSward(var0, var1, var2, var3, var5, var32);
                     } else {
                        Terrain.set(
                           var0,
                           var5,
                           (BlockState)((AlpinePasture)AlpineRegistration.PASTURE.get())
                              .defaultBlockState()
                              .setValue(AlpinePasture.HEIGHT, var11 < 0.55 ? 0 : 1)
                        );
                     }
                  } else if (var43 > 0.988) {
                     Terrain.set(var0, var5, (var9 > 0.1 ? Blocks.LILY_OF_THE_VALLEY : Blocks.FERN).defaultBlockState());
                  }
               }
            }
         }
      }
   }

   private static void meadow(
      WorldGenLevel var0, AlpineLayout var1, int var2, int var3, AlpineLayout.Sample var4, BlockPos var5, double var6, double var8, double var10, double var12
   ) {
      double var14 = AlpineLayout.smooth(-0.35, 0.4, var6 + var8 * 0.35);
      int var16 = var14 < 0.22 ? 0 : (var14 < 0.5 ? 1 : 2);
      double var17 = var1.noise((double)var2 / 5.0, (double)var3 / 5.0, 8211L) + 0.5 * var1.noise((double)var2 / 2.1, (double)var3 / 2.1, 8213L);
      if (var17 < -0.42 && var16 > 0) {
         var16--;
      } else if (var17 > 0.5 && var16 < 2) {
         var16++;
      }

      BlockPos var19 = var5.above();
      int var20 = 0;
      if (var12 < 1.2
         && var5.getY() == var4.floor() + 1
         && var4.distance() > var4.width() + 6.0
         && var0.getBlockState(var5.below()).is(Blocks.GRASS_BLOCK)
         && var0.isEmptyBlock(var19)) {
         var20 = Math.min(7, (int)Math.floor((var4.ground() - (double)var4.floor()) * 8.0));
      }

      boolean var21 = openField(var1, var2, var3, var4);
      BlockState var22 = ((AlpinePasture)AlpineRegistration.PASTURE.get()).defaultBlockState();
      if (var20 > 0) {
         Terrain.set(var0, var5, (BlockState)((AlpineTurf)AlpineRegistration.TURF.get()).defaultBlockState().setValue(AlpineTurf.LAYERS, var20));
         if (var21) {
            if (var16 > 1 && !var0.isEmptyBlock(var19.above())) {
               var16 = 1;
            }

            Terrain.set(var0, var19, (BlockState)((BlockState)var22.setValue(AlpinePasture.HEIGHT, var16)).setValue(AlpinePasture.SINK, 8 - var20));
         } else if (var10 < 0.55) {
            Terrain.set(var0, var19, (BlockState)((BlockState)var22.setValue(AlpinePasture.HEIGHT, 0)).setValue(AlpinePasture.SINK, 8 - var20));
         }
      } else {
         if (var1.version() >= 19) {
            if (var4.biome() == 15) {
               if (pinkField(var0, var1, var2, var3, var5, var19)) {
                  return;
               }

               var16 = 0;
            }

            if ((var4.fallStyle() & 7) != 0 && (var4.fallStyle() >> 3 & 63) > 12) {
               if (var10 > 0.6) {
                  if (var10 > 0.975) {
                     Terrain.set(var0, var5, (var6 > 0.0 ? Blocks.ALLIUM : Blocks.AZURE_BLUET).defaultBlockState());
                  } else if (var10 > 0.93) {
                     Terrain.set(var0, var5, Blocks.MOSS_CARPET.defaultBlockState());
                  }

                  return;
               }

               var16 = 0;
            }
         }

         if (var10 > 0.982 && var4.moisture() > 0.43) {
            Terrain.set(var0, var5, (var6 > 0.1 ? Blocks.CORNFLOWER : Blocks.OXEYE_DAISY).defaultBlockState());
         } else if (!var21) {
            plainGrass(var0, var1, var2, var3, var5);
         } else {
            if (var16 > 1 && !var0.isEmptyBlock(var19)) {
               var16 = 1;
            }

            Terrain.set(var0, var5, (BlockState)var22.setValue(AlpinePasture.HEIGHT, var16));
         }
      }
   }

   private static boolean pinkField(WorldGenLevel var0, AlpineLayout var1, int var2, int var3, BlockPos var4, BlockPos var5) {
      double var6 = var1.noise((double)var2 / 1100.0, (double)var3 / 1100.0, 7661L) + 0.3 * var1.noise((double)var2 / 320.0, (double)var3 / 320.0, 7663L);
      double var8 = AlpineLayout.smooth(0.12, 0.4, var6);
      double var10 = var1.noise((double)var2 / 9.0, (double)var3 / 9.0, 8611L) + 0.5 * var1.noise((double)var2 / 4.0, (double)var3 / 4.0, 8613L);
      double var12 = var1.variation(var2, var3, 8615L);
      if (!(var10 < 0.08 - 0.6 * var8) && !(var12 > 0.34 + 0.52 * var8)) {
         boolean var14 = var0.isEmptyBlock(var5);
         double var15 = var1.variation(var2, var3, 8617L);
         if (var15 < 0.45) {
            Terrain.set(
               var0,
               var4,
               (BlockState)AlpineRegistration.prop("fireweed")
                  .defaultBlockState()
                  .setValue(AlpineThicket.SIZE, var14 ? (var1.variation(var2, var3, 8619L) < 0.6 ? 2 : 1) : 0)
            );
         } else if (var15 < 0.65) {
            Direction var17 = Plane.HORIZONTAL.getRandomDirection(RandomSource.create(var1.hash(var2, var3, 8621L)));
            Terrain.set(
               var0,
               var4,
               (BlockState)((BlockState)Blocks.PINK_PETALS
                     .defaultBlockState()
                     .setValue(PinkPetalsBlock.AMOUNT, 1 + (int)(var1.variation(var2, var3, 8623L) * 3.99)))
                  .setValue(PinkPetalsBlock.FACING, var17)
            );
         } else if (var15 < 0.8) {
            Terrain.set(var0, var4, Blocks.PINK_TULIP.defaultBlockState());
         } else if (var15 < 0.91) {
            Terrain.set(var0, var4, Blocks.ALLIUM.defaultBlockState());
         } else if (var14) {
            doublePlant(var0, var4, Blocks.PEONY);
         } else {
            Terrain.set(var0, var4, Blocks.PINK_TULIP.defaultBlockState());
         }

         return true;
      } else {
         return false;
      }
   }

   private static void wornField(
      WorldGenLevel var0, AlpineLayout var1, int var2, int var3, AlpineLayout.Sample var4, BlockPos var5, double var6, double var8, int var10, boolean var11
   ) {
      BlockState var12 = var0.getBlockState(var5.below());
      boolean var13 = var12.is(Blocks.COARSE_DIRT) || var12.is(Blocks.GRAVEL);
      if (!var13) {
         if (var11) {
            if (var6 > -0.05 && var8 < 0.4) {
               set(var0, var5, "sagebrush", var10);
            } else if (var8 < 0.64) {
               sward(var0, var1, var2, var3, var4, var5, 0);
            } else if (var8 < 0.74) {
               Terrain.set(var0, var5, Blocks.SHORT_GRASS.defaultBlockState());
            } else if (var8 < 0.77) {
               set(var0, var5, "juniper_shrub", Math.min(1, var10));
            } else if (var8 > 0.982) {
               Terrain.set(var0, var5, Blocks.DANDELION.defaultBlockState());
            }
         }
      } else if (var8 < 0.07 && var11) {
         Terrain.set(var0, var5, Blocks.DEAD_BUSH.defaultBlockState());
      } else if (var8 < 0.1) {
         Terrain.set(
            var0,
            var5,
            (BlockState)AlpineRegistration.prop("river_pebbles").defaultBlockState().setValue(AlpineBoulder.FORM, (int)(var1.hash(var2, var3, 8631L) & 3L))
         );
      } else if (var8 < 0.22 && var6 > 0.2) {
         set(var0, var5, "sagebrush", Math.min(1, var10));
      }
   }

   private static void districtCover(WorldGenLevel var0, AlpineLayout var1, int var2, int var3, AlpineLayout.Sample var4, BlockPos var5, int var6) {
      double var7 = var1.noise((double)var2 / 11.0, (double)var3 / 11.0, 8501L) + 0.45 * var1.noise((double)var2 / 4.0, (double)var3 / 4.0, 8503L);
      double var9 = var1.variation(var2, var3, 8505L);
      boolean var11 = var0.isEmptyBlock(var5.above());
      BlockState var12 = var0.getBlockState(var5.below());
      boolean var13 = var12.is(BlockTags.DIRT) || var12.is(Blocks.MOSS_BLOCK);
      int var14 = var11 ? (var9 < 0.45 ? 2 : 1) : (var9 < 0.6 ? 1 : 0);
      switch (var6) {
         case 28:
            if (!var13 && var9 < 0.8) {
               return;
            }

            if (var7 > -0.1 && var9 < 0.55) {
               set(var0, var5, "heather", var14);
               return;
            }

            if (var9 < 0.62) {
               Terrain.set(var0, var5, Blocks.SHORT_GRASS.defaultBlockState());
            } else if (var9 < 0.7) {
               set(var0, var5, "juniper_shrub", Math.min(1, var14));
            } else if (var9 < 0.76) {
               set(var0, var5, "huckleberry_shrub", Math.min(1, var14));
            } else if (var9 < 0.82) {
               Terrain.set(var0, var5, Blocks.MOSS_CARPET.defaultBlockState());
            } else if (var9 < 0.86) {
               Terrain.set(var0, var5, Blocks.FERN.defaultBlockState());
            } else if (var9 > 0.992) {
               Terrain.set(var0, var5, Blocks.ALLIUM.defaultBlockState());
            }
            break;
         case 29:
            if (var1.version() >= 19) {
               wornField(var0, var1, var2, var3, var4, var5, var7, var9, var14, var13);
               return;
            }

            if (var7 > 0.05 && var9 < 0.45) {
               set(var0, var5, "sagebrush", var14);
               return;
            }

            if (!var13 && var9 < 0.7) {
               return;
            }

            if (var9 < 0.58) {
               Terrain.set(var0, var5, Blocks.SHORT_GRASS.defaultBlockState());
            } else if (var9 < 0.72) {
               sward(var0, var1, var2, var3, var4, var5, 0);
            } else if (var9 < 0.79) {
               set(var0, var5, "juniper_shrub", Math.min(1, var14));
            } else if (var9 > 0.988) {
               Terrain.set(var0, var5, Blocks.OXEYE_DAISY.defaultBlockState());
            }
            break;
         case 30:
            if (var9 < 0.1) {
               Terrain.set(var0, var5, Blocks.MOSS_CARPET.defaultBlockState());
            } else if (var9 < 0.14 && var13) {
               set(var0, var5, "juniper_shrub", 0);
            } else if (var9 < 0.17 && var13) {
               set(var0, var5, "spruce_seedling", Math.min(1, var14));
            } else if (var9 < 0.2) {
               Terrain.set(var0, var5, Blocks.FERN.defaultBlockState());
            }
            break;
         case 31:
         default:
            if (var7 > -0.18 && var9 < 0.52) {
               set(var0, var5, "cottongrass", var14);
               return;
            }

            if (var9 < 0.62) {
               Terrain.set(var0, var5, Blocks.MOSS_CARPET.defaultBlockState());
            } else if (var9 < 0.74) {
               set(var0, var5, var1.variation(var2, var3, 8507L) < 0.5 ? "spreading_fern" : "arching_fern", var14);
            } else if (var9 < 0.82) {
               set(var0, var5, "huckleberry_shrub", Math.min(1, var14));
            } else if (var9 < 0.9 && var13) {
               Terrain.set(var0, var5, Blocks.SHORT_GRASS.defaultBlockState());
            } else if (var9 > 0.99) {
               Terrain.set(var0, var5, Blocks.BLUE_ORCHID.defaultBlockState());
            }
            break;
         case 32:
            if (var7 > 0.1 && var9 < 0.3) {
               set(var0, var5, "heather", Math.min(1, var14));
               return;
            }

            if (var9 < 0.16) {
               Terrain.set(var0, var5, Blocks.MOSS_CARPET.defaultBlockState());
            } else if (var9 < 0.26 && var13) {
               Terrain.set(var0, var5, Blocks.SHORT_GRASS.defaultBlockState());
            } else if (var9 < 0.3 && var13) {
               set(var0, var5, "juniper_shrub", 0);
            } else if (var9 > 0.985) {
               Terrain.set(var0, var5, Blocks.ALLIUM.defaultBlockState());
            }
            break;
         case 33:
            if (!var13 && var9 < 0.85) {
               return;
            }

            if (var9 < 0.46) {
               sward(var0, var1, var2, var3, var4, var5, var7 > 0.15 ? 1 : 0);
            } else if (var9 < 0.66) {
               Terrain.set(var0, var5, Blocks.SHORT_GRASS.defaultBlockState());
            } else if (var9 < 0.74) {
               Terrain.set(var0, var5, Blocks.FERN.defaultBlockState());
            } else if (var9 < 0.78) {
               set(var0, var5, "woodland_bush", Math.min(1, var14));
            } else if (var9 < 0.8) {
               Terrain.set(var0, var5, Blocks.OXEYE_DAISY.defaultBlockState());
            } else if (var9 > 0.988) {
               Terrain.set(var0, var5, Blocks.CORNFLOWER.defaultBlockState());
            }
            break;
         case 34:
            if (var7 > 0.22 && var9 < 0.34) {
               set(var0, var5, "sagebrush", Math.min(1, var14));
               return;
            }

            if (!var13 && var9 < 0.78) {
               return;
            }

            if (var9 < 0.52) {
               Terrain.set(var0, var5, Blocks.SHORT_GRASS.defaultBlockState());
            } else if (var9 < 0.62) {
               sward(var0, var1, var2, var3, var4, var5, 0);
            } else if (var9 < 0.68) {
               set(var0, var5, "juniper_shrub", Math.min(1, var14));
            } else if (var9 < 0.71) {
               Terrain.set(var0, var5, Blocks.FERN.defaultBlockState());
            } else if (var9 > 0.99) {
               Terrain.set(var0, var5, Blocks.OXEYE_DAISY.defaultBlockState());
            }
            break;
         case 35:
            if (var7 > -0.05 && var9 < 0.44) {
               set(var0, var5, var1.variation(var2, var3, 8509L) < 0.5 ? "river_brush" : "broadleaf_thicket", var14);
               return;
            }

            if (var9 < 0.56) {
               set(var0, var5, var1.variation(var2, var3, 8507L) < 0.5 ? "spreading_fern" : "arching_fern", var14);
            } else if (var9 < 0.66) {
               Terrain.set(var0, var5, Blocks.MOSS_CARPET.defaultBlockState());
            } else if (var9 < 0.74) {
               set(var0, var5, "flowering_bramble", Math.min(1, var14));
            } else if (var9 < 0.84 && var13) {
               Terrain.set(var0, var5, Blocks.SHORT_GRASS.defaultBlockState());
            } else if (var9 > 0.985) {
               Terrain.set(var0, var5, Blocks.BLUE_ORCHID.defaultBlockState());
            }
            break;
         case 36:
            if (!var13 && var9 < 0.82) {
               return;
            }

            if (var9 < 0.5) {
               Terrain.set(var0, var5, Blocks.SHORT_GRASS.defaultBlockState());
            } else if (var9 < 0.64) {
               sward(var0, var1, var2, var3, var4, var5, var7 > 0.1 ? 1 : 0);
            } else if (var9 < 0.72) {
               Terrain.set(var0, var5, Blocks.FERN.defaultBlockState());
            } else if (var9 < 0.78) {
               set(var0, var5, "huckleberry_shrub", Math.min(1, var14));
            } else if (var9 > 0.985) {
               Terrain.set(var0, var5, Blocks.OXEYE_DAISY.defaultBlockState());
            }
            break;
         case 37:
            if (!var13 && var9 < 0.8) {
               return;
            }

            if (var7 > 0.05 && var9 < 0.34) {
               set(var0, var5, "broadleaf_thicket", var14);
               return;
            }

            if (var9 < 0.54) {
               set(var0, var5, var1.variation(var2, var3, 8507L) < 0.5 ? "spreading_fern" : "arching_fern", var14);
            } else if (var9 < 0.7) {
               Terrain.set(var0, var5, Blocks.SHORT_GRASS.defaultBlockState());
            } else if (var9 < 0.78) {
               Terrain.set(var0, var5, Blocks.FERN.defaultBlockState());
            } else if (var9 > 0.99) {
               Terrain.set(var0, var5, Blocks.LILY_OF_THE_VALLEY.defaultBlockState());
            }
            break;
         case 38:
            if (var7 > -0.02 && var9 < 0.48) {
               set(var0, var5, "heather", var14);
               return;
            }

            if (!var13 && var9 < 0.8) {
               return;
            }

            if (var9 < 0.6) {
               Terrain.set(var0, var5, Blocks.SHORT_GRASS.defaultBlockState());
            } else if (var9 < 0.7) {
               set(var0, var5, "juniper_shrub", Math.min(1, var14));
            } else if (var9 < 0.76) {
               Terrain.set(var0, var5, Blocks.MOSS_CARPET.defaultBlockState());
            } else if (var9 > 0.99) {
               Terrain.set(var0, var5, Blocks.ALLIUM.defaultBlockState());
            }
      }
   }

   private static void set(WorldGenLevel var0, BlockPos var1, String var2, int var3) {
      Block var4 = AlpineRegistration.prop(var2);
      if (var4 != Blocks.AIR) {
         Terrain.set(var0, var1, (BlockState)var4.defaultBlockState().setValue(AlpineThicket.SIZE, var3));
      }
   }

   static boolean openField(AlpineLayout var0, int var1, int var2, AlpineLayout.Sample var3) {
      int var4 = var3.biome();
      return var4 != 33 && var4 != 36 ? var3.forest() < 0.26 + 0.06 * var0.variation(var1, var2, 8531L) : false;
   }

   static BlockState plainGrassState(AlpineLayout var0, int var1, int var2) {
      return (var0.variation(var1, var2, 8533L) < 0.8 ? Blocks.SHORT_GRASS : Blocks.FERN).defaultBlockState();
   }

   private static void plainGrass(WorldGenLevel var0, AlpineLayout var1, int var2, int var3, BlockPos var4) {
      Terrain.set(var0, var4, plainGrassState(var1, var2, var3));
   }

   private static void sward(WorldGenLevel var0, AlpineLayout var1, int var2, int var3, AlpineLayout.Sample var4, BlockPos var5, int var6) {
      if (!openField(var1, var2, var3, var4)) {
         plainGrass(var0, var1, var2, var3, var5);
      } else {
         Terrain.set(var0, var5, (BlockState)((AlpinePasture)AlpineRegistration.PASTURE.get()).defaultBlockState().setValue(AlpinePasture.HEIGHT, var6));
      }
   }

   private static void woodlandSward(WorldGenLevel var0, AlpineLayout var1, int var2, int var3, BlockPos var4, boolean var5) {
      double var6 = var1.variation(var2, var3, 8491L);
      if (var6 < 0.46) {
         Terrain.set(var0, var4, Blocks.SHORT_GRASS.defaultBlockState());
      } else if (var6 < 0.74) {
         Terrain.set(var0, var4, Blocks.FERN.defaultBlockState());
      } else if (var6 < 0.88 && var5) {
         doublePlant(var0, var4, var1.variation(var2, var3, 8493L) < 0.5 ? Blocks.TALL_GRASS : Blocks.LARGE_FERN);
      } else if (var6 < 0.95) {
         Terrain.set(var0, var4, Blocks.SHORT_GRASS.defaultBlockState());
      }
   }

   private static BlockState with(BlockState var0, String var1, Object var2) {
      Property var3 = var0.getBlock().getStateDefinition().getProperty(var1);
      if (var3 == null) {
         return var0;
      } else {
         try {
            return (BlockState)var0.setValue(var3, (Comparable)var2);
         } catch (IllegalArgumentException var5) {
            return var0;
         }
      }
   }

   private static void doublePlant(WorldGenLevel var0, BlockPos var1, Block var2) {
      Terrain.set(var0, var1, (BlockState)var2.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
      Terrain.set(var0, var1.above(), (BlockState)var2.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
   }

   private static void full(
      WorldGenLevel var0, AlpineLayout var1, int var2, int var3, AlpineLayout.Sample var4, BlockPos var5, double var6, double var8, double var10, boolean var12
   ) {
      double var13 = AlpineLayout.smooth(-0.35, 0.4, var6 + var8 * 0.35);
      if (var10 > 0.982 && var4.moisture() > 0.43 && !var12) {
         Terrain.set(var0, var5, (var6 > 0.1 ? Blocks.CORNFLOWER : Blocks.OXEYE_DAISY).defaultBlockState());
      } else if (var12) {
         if (var10 < 0.26) {
            Terrain.set(var0, var5, (var4.moisture() > 0.48 ? Blocks.FERN : Blocks.SHORT_GRASS).defaultBlockState());
         } else if (var10 < 0.52) {
            Terrain.set(var0, var5, Blocks.MOSS_CARPET.defaultBlockState());
         } else if (var1.version() >= 15) {
            woodlandSward(var0, var1, var2, var3, var5, var0.isEmptyBlock(var5.above()));
         } else {
            Terrain.set(
               var0,
               var5,
               (BlockState)((AlpinePasture)AlpineRegistration.PASTURE.get())
                  .defaultBlockState()
                  .setValue(AlpinePasture.HEIGHT, var10 < 0.72 ? 0 : (var10 < 0.92 ? 1 : 2))
            );
         }
      } else if (var1.version() < 6 && var10 > 0.74 + var13 * 0.12) {
         if (var13 > 0.55 && var1.variation(var2, var3, 8209L) < var13 * 0.5 && var0.isEmptyBlock(var5.above())) {
            Terrain.set(var0, var5, (BlockState)Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
            Terrain.set(var0, var5.above(), (BlockState)Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
         } else {
            Terrain.set(var0, var5, Blocks.SHORT_GRASS.defaultBlockState());
         }
      } else {
         int var15 = var13 < 0.26 ? 0 : (var13 < 0.58 ? 1 : 2);
         double var16 = var1.noise((double)var2 / 5.0, (double)var3 / 5.0, 8211L) + 0.5 * var1.noise((double)var2 / 2.1, (double)var3 / 2.1, 8213L);
         if (var16 < -0.42 && var15 > 0) {
            var15--;
         } else if (var16 > 0.5 && var15 < 2) {
            var15++;
         }

         if (var15 > 1 && !var0.isEmptyBlock(var5.above())) {
            var15 = 1;
         }

         sward(var0, var1, var2, var3, var4, var5, var15);
      }
   }

   static void drape(WorldGenLevel var0, AlpineLayout var1, AlpineTile var2, int var3, int var4) {
      int var5 = var1.version() >= 5 ? 112 : 160;

      for (int var6 = 0; var6 < 16 && var5 > 0; var6++) {
         for (int var7 = 0; var7 < 16 && var5 > 0; var7++) {
            int var8 = var3 + var6;
            int var9 = var4 + var7;
            AlpineLayout.Sample var10 = var2.sample(var6, var7);
            if (!var10.wet() && !(var10.ground() > var10.snowLine() - 18.0) && !(var10.moisture() < 0.4)) {
               double var11 = var1.noise((double)var8 / 19.0, (double)var9 / 19.0, 8221L) + var1.noise((double)var8 / 67.0, (double)var9 / 67.0, 8223L) * 0.5;
               if (!(var11 < -0.12)) {
                  int var13 = Math.min(18, 2 + (int)(var2.slope(var6, var7) * 5.0));
                  int var14 = var10.forest() > 0.45 ? 6 : 0;

                  for (int var15 = var10.floor() + var14; var15 >= var10.floor() - var13 && var5 > 0; var15--) {
                     BlockPos var16 = new BlockPos(var8, var15, var9);
                     BlockState var17 = var0.getBlockState(var16);
                     if ((var15 <= var10.floor() || var17.is(BlockTags.LOGS))
                        && (var15 <= var10.floor() + 2 || !(var1.variation(var8 + var15, var9, 8231L) > 0.6))) {
                        double var18 = var1.noise(((double)var8 + (double)var15 * 0.37) / 9.0, ((double)var9 + (double)var15 * 0.61) / 9.0, 8227L);
                        if (!(var18 + var11 * 0.5 < -0.08)
                           && !(var1.variation(var8 + var15 * 31, var9 - var15 * 17, 8225L) > (var1.version() >= 5 ? 0.38 : 0.48))
                           && var17.isSolid()
                           && !var17.is(Blocks.SAND)
                           && !var17.is(Blocks.SNOW_BLOCK)) {
                           for (Direction var23 : Direction.values()) {
                              if (var23 != Direction.DOWN) {
                                 if (var1.version() >= 17) {
                                    break;
                                 }

                                 BlockPos var24 = var16.relative(var23);
                                 if (var24.getX() >> 4 == var3 >> 4 && var24.getZ() >> 4 == var4 >> 4) {
                                    BlockState var25 = var0.getBlockState(var24);
                                    if ((var25.isAir() || var25.is((Block)AlpineRegistration.OVERGROWTH.get())) && var17.isFaceSturdy(var0, var16, var23)) {
                                       int var26 = 1 << var23.getOpposite().ordinal();
                                       if (var25.is((Block)AlpineRegistration.OVERGROWTH.get())) {
                                          var26 |= var25.getValue(AlpineOvergrowth.FACES);
                                       }

                                       Terrain.set(
                                          var0,
                                          var24,
                                          (BlockState)((AlpineOvergrowth)AlpineRegistration.OVERGROWTH.get())
                                             .defaultBlockState()
                                             .setValue(AlpineOvergrowth.FACES, var26)
                                       );
                                       if (--var5 <= 0) {
                                          break;
                                       }
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }
}
