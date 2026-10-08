package com.formaworks.frontierhunts.landscape;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.environment.CascadeMist;
import com.formaworks.frontierhunts.environment.ForestFloor;
import com.formaworks.frontierhunts.environment.ForestStand;
import com.formaworks.frontierhunts.environment.Terrain;
import com.formaworks.frontierhunts.environment.WildTrees;
import com.formaworks.frontierhunts.expedition.ExpeditionContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.Plane;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public final class AlpineEcology extends Feature<NoneFeatureConfiguration> {
   private static final Direction[] SIDES = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

   public AlpineEcology() {
      super(NoneFeatureConfiguration.CODEC);
   }

   public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> var1) {
      if (var1.chunkGenerator() instanceof AlpineGenerator var2) {
         decorate(
            var1.level(),
            var2.layout(),
            var1.random(),
            var1.origin().getX() & -16,
            var1.origin().getZ() & -16,
            (Boolean)HuntConfig.GENERATE_HUNTING_FORESTS.get()
         );
         return true;
      } else {
         return false;
      }
   }

   public static void decorate(WorldGenLevel var0, AlpineLayout var1, RandomSource var2, int var3, int var4, boolean var5) {
      if (var1.version() >= 18) {
         AlpineCascade.place(var0, var1, var3, var4);
      }

      if (var5 && var1.version() >= 14) {
         AlpineForest.stand(var0, var1, var2, var3, var4);
      } else if (var5) {
         int var6 = var1.version() == 3 ? 3 : 2;
         int var7 = var1.version() == 3 ? 5 : 8;

         for (int var8 = 0; var8 < var6; var8++) {
            for (int var9 = 0; var9 < var6; var9++) {
               int var10 = var1.version() == 3 ? 1 : 2;
               int var11 = var3 + var8 * var7 + var10 + var2.nextInt(var1.version() >= 3 ? 3 : 4);
               int var12 = var4 + var9 * var7 + var10 + var2.nextInt(var1.version() >= 3 ? 3 : 4);
               AlpineLayout.Sample var13 = var1.sample((double)var11, (double)var12);
               if ((
                     var1.version() >= 4
                        ? !(var13.forest() < 0.16)
                        : var1.version() < 2 || var13.biome() != 1 && var13.biome() != 4 && var13.biome() != 10 && var13.biome() != 15
                  )
                  && !(var13.rock() > 0.35)
                  && (!var13.scenic() || AlpineFalls.kind(var13.site()) != 4 && !(var1.slope(var11, var12) > 0.9))
                  && !var13.wet()
                  && !(var2.nextDouble() > (var1.version() >= 4 ? var13.forest() * var13.forest() * 0.9 : var13.forest() * 0.64))
                  && !(var1.slope(var11, var12) > (var1.version() >= 4 ? 2.1 : 1.05))) {
                  WildTrees.Kind var14 = !(var13.ground() > 320.0) && !(var13.moisture() < 0.48) ? WildTrees.Kind.FIR : WildTrees.Kind.PINE;
                  if (var13.ground() < 290.0 && (double)var2.nextFloat() < 0.08) {
                     var14 = WildTrees.Kind.BIRCH;
                  }

                  if (var1.version() >= 2 && var13.biome() != 3 && var13.biome() != 9 && (double)var2.nextFloat() < 0.78) {
                     var14 = switch (var1.woodland(var11, var12)) {
                        case 0 -> WildTrees.Kind.MAPLE;
                        case 1 -> WildTrees.Kind.ASPEN;
                        case 2 -> WildTrees.Kind.BIRCH;
                        default -> WildTrees.Kind.FIR;
                     };
                  }

                  if (var13.biome() == 9) {
                     var14 = WildTrees.Kind.PINE;
                  }

                  if (var1.version() >= 3) {
                     if (var13.biome() == 12 || var13.biome() == 13 || var13.biome() == 16) {
                        var14 = (double)var2.nextFloat() < 0.55 ? WildTrees.Kind.FIR : WildTrees.Kind.BIRCH;
                     }

                     if (var13.biome() == 14) {
                        var14 = (double)var2.nextFloat() < 0.75 ? WildTrees.Kind.WILLOW : WildTrees.Kind.FIR;
                     }
                  }

                  float var15 = var1.version() >= 4
                     ? 0.18F + var2.nextFloat() * 0.4F
                     : (var1.version() >= 3 ? 0.22F + var2.nextFloat() * 0.46F : 0.38F + var2.nextFloat() * 0.6F);
                  ForestStand.plant(var0, new BlockPos(var11, var13.floor() + 1, var12), var14, var2, var15, (double)var2.nextFloat() < 0.07);
               }
            }
         }
      }

      AlpineTile var36 = var1.version() >= 3 ? new AlpineTile(var1, var3, var4) : null;
      MutableBlockPos var37 = new MutableBlockPos();
      int var38 = 1;

      for (int var39 = 0; var39 < 16; var39++) {
         for (int var41 = 0; var41 < 16; var41++) {
            int var43 = var3 + var39;
            int var45 = var4 + var41;
            AlpineLayout.Sample var46 = var36 == null ? var1.sample((double)var43, (double)var45) : var36.sample(var39, var41);
            if ((!var46.scenic() || !var46.wet() && !(var46.rock() > 0.35))
               && (var1.version() < 12 || !AlpineLayout.sea(var46.biome()))
               && (var1.version() < 20 || !AlpineLakes.dress(var0, var1, var46, var43, var45, var37))) {
               if (var1.version() >= 10 && var46.wet()) {
                  AlpineWatershed.Water var47 = var1.watershed().water((double)var43, (double)var45);
                  if (AlpineLayout.sea(var46.biome()) || var47.lake() && var46.distance() < var46.width() - 5.0) {
                     continue;
                  }
               }

               if (var1.version() >= 9
                  && var38 > 0
                  && var39 > 0
                  && var39 < 14
                  && var41 > 0
                  && var41 < 14
                  && !AlpineLayout.sea(var46.biome())
                  && (var1.version() < 10 || !var46.wet() || var46.distance() > var46.width() * 0.55)
                  && var46.fall() < 1.0
                  && var1.variation(var43, var45, 8389L) < (var46.distance() < var46.width() + 35.0 ? 0.003 : 6.5E-4)
                  && AlpineOutcrop.place(
                     var0,
                     new BlockPos(var43, var46.floor() + 1, var45),
                     (int)(var1.hash(var43, var45, 8391L) & 3L),
                     var46.moisture() > 0.42 || var46.distance() < var46.width() + 12.0
                  )) {
                  var38--;
               }

               if (var1.version() >= 10
                  && var38 > 0
                  && var39 > 0
                  && var39 < 12
                  && var41 > 0
                  && var41 < 12
                  && !AlpineLayout.sea(var46.biome())
                  && (!var46.wet() || var46.distance() > var46.width() - 2.0)
                  && var46.fall() < 0.4
                  && var1.variation(var43, var45, 8421L) < 4.0E-4
                  && var36.slope(var39, var41) < 0.8
                  && AlpineGiantBoulder.place(
                     var0, new BlockPos(var43, var46.floor() + 1, var45), (int)(var1.hash(var43, var45, 8423L) & 3L), var46.moisture() > 0.45
                  )) {
                  var38--;
               }

               boolean var48 = var36 != null && !var46.wet() && (var36.slope(var39, var41) > 1.15 || var46.rock() > 0.35);
               if (var1.version() >= 18 && var36 != null && !var48) {
                  AlpineColumn var49 = new AlpineColumn(var1, var46, var36.slope(var39, var41), var43, var45, var36.lowestNeighbour(var39, var41));

                  for (int var16 = var46.floor(); var16 >= var46.floor() - 4; var16--) {
                     var37.set(var43, var16, var45);
                     BlockState var17 = var0.getBlockState(var37);
                     if (var17.is(Blocks.GRANITE) || var17.is(Blocks.DIORITE)) {
                        BlockState var18 = var49.at(var16);
                        if (!var18.is(Blocks.GRANITE) && !var18.is(Blocks.DIORITE) && !var18.isAir()) {
                           boolean var19 = var0.isEmptyBlock(var37.above()) || !var0.getFluidState(var37.above()).isEmpty();

                           for (Direction var23 : SIDES) {
                              if (!var19) {
                                 BlockState var24 = var0.getBlockState(var37.relative(var23));
                                 var19 = var24.isAir() || !var24.getFluidState().isEmpty();
                              }
                           }

                           if (var19) {
                              Terrain.set(var0, var37, var18);
                           }
                        }
                     }
                  }
               }

               if (var48) {
                  double var50 = var36.slope(var39, var41);
                  AlpineColumn var55 = new AlpineColumn(var1, var46, var50, var43, var45, var36.lowestNeighbour(var39, var41));
                  int var56 = Math.max(0, var46.floor() - Math.min(128, Math.max(5, (int)Math.ceil(var50 * 8.0) + 2)));

                  for (int var59 = var46.floor(); var59 >= var56; var59--) {
                     var37.set(var43, var59, var45);
                     BlockState var62 = var0.getBlockState(var37);
                     if (var62.is(Blocks.GRANITE) || var62.is(Blocks.DIORITE)) {
                        BlockState var65 = var55.at(var59);
                        if (var65.is(Blocks.STONE)
                           || var65.is(Blocks.COBBLESTONE)
                           || var65.is(Blocks.MOSS_BLOCK)
                           || var65.is(Blocks.MOSSY_COBBLESTONE)
                           || var65.getBlock() instanceof AlpineRock
                           || var65.getBlock() instanceof AlpineWeatheredRock) {
                           boolean var70 = var0.isEmptyBlock(var37.above());

                           for (Direction var26 : SIDES) {
                              if (!var70) {
                                 BlockState var27 = var0.getBlockState(var37.relative(var26));
                                 var70 = var27.isAir() || !var27.getFluidState().isEmpty();
                              }
                           }

                           if (var70) {
                              Terrain.set(var0, var37, var65);
                           }
                        }
                     }
                  }
               }

               var37.set(var43, var46.floor() + 1, var45);
               if (var46.wet()) {
                  int var53 = 0;
                  double var54 = var46.fall() * 5.0;
                  if (var1.version() >= 3 && !AlpineLayout.sea(var46.biome())) {
                     AlpineWatershed.Water var57 = var1.watershed().water((double)var43, (double)var45);
                     var54 = Math.max(
                        0.0,
                        var1.sample((double)var43 - var57.dx() * 32.0, (double)var45 - var57.dz() * 32.0).riverLevel()
                           - var1.sample((double)var43 + var57.dx() * 32.0, (double)var45 + var57.dz() * 32.0).riverLevel()
                     );
                  }

                  int var58 = var54 * var46.width() < 90.0 ? 0 : (var54 * var46.width() < 550.0 ? 1 : 2);
                  if (var1.version() >= 9 && (var54 < 8.0 || var46.fall() < 3.0)) {
                     var58 = 0;
                  }

                  if (var46.fall() > 1.0) {
                     int[] var60 = new int[4];

                     for (int var63 = 0; var63 < 4; var63++) {
                        Direction var66 = SIDES[var63];
                        AlpineLayout.Sample var71 = var1.sample((double)(var43 + var66.getStepX()), (double)(var45 + var66.getStepZ()));
                        int var79 = var63 ^ 1;
                        var60[var79] = var71.wet() ? Math.max(0, var71.water() - var46.water()) : 0;
                        var53 = Math.max(var53, var60[var79]);
                     }

                     boolean var64 = var1.version() >= 5;
                     int var67 = Math.min(var0.getMaxBuildHeight() - 1, var46.water() + var53);
                     int var72 = var67;
                     double var80 = 0.0;
                     if (var64) {
                        var80 = Math.min(1.0, (double)var53 * Math.max(4.0, var46.width()) / 1200.0);
                        double var87 = var1.version() >= 6 ? 1.8 + 4.2 * (1.0 - var80) : 1.1 + 2.7 * (1.0 - var80);
                        var72 = Math.max(
                           var46.water() + 1, var67 - (int)Math.round(var87 * (0.5 + 0.5 * var1.noise((double)var43 / 3.2, (double)var45 / 3.2, 8415L)))
                        );
                        if (var1.version() >= 7) {
                           var72 = var67;
                        }
                     }

                     for (int var88 = var46.water() + 1; var88 <= var72; var88++) {
                        double var91 = 1.0;
                        double var28 = 0.0;
                        if (var64) {
                           double var30 = (double)Math.max(1, var72 - var46.water());
                           double var32 = (double)(var72 - var88) / var30;
                           var91 = Math.abs(var1.noise((double)var43 / 2.6 + (double)var88 * 0.012, (double)var45 / 2.6 - (double)var88 * 0.012, 8411L));
                           var28 = 0.03 + var32 * ((var1.version() >= 6 ? 0.29 : 0.181) - (var1.version() >= 6 ? 0.17 : 0.123) * var80);
                           if (var1.version() < 7 && var91 < var28) {
                              continue;
                           }
                        }

                        var37.setY(var88);
                        if (var0.isEmptyBlock(var37) || var0.getBlockState(var37).is((Block)AlpineRegistration.FOAM.get())) {
                           Terrain.set(var0, var37, (BlockState)Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 8));
                        }

                        if (var0.getFluidState(var37).is(FluidTags.WATER)) {
                           boolean var97 = !var64 || var88 <= var46.water() + 2 || var88 >= var72 - 1 || var91 < var28 + 0.115;

                           for (int var31 = 0; var31 < 4; var31++) {
                              if (var60[var31] >= var88 - var46.water()
                                 && (
                                    var1.version() >= 7
                                       || var97
                                       || !(var1.noise((double)var43 / 2.2, (double)var45 / 2.2 + (double)var88 * 0.31, 8417L) < 0.18)
                                 )) {
                                 BlockPos var100 = var37.relative(SIDES[var31]);
                                 BlockState var33 = var0.getBlockState(var100);
                                 if (var33.isAir() || var33.is((Block)AlpineRegistration.FOAM.get())) {
                                    int var34 = 1 << var31;
                                    if (var33.is((Block)AlpineRegistration.FOAM.get())) {
                                       var34 |= var33.getValue(AlpineFoam.FACES);
                                    }

                                    Terrain.set(
                                       var0,
                                       var100,
                                       (BlockState)((BlockState)((BlockState)((BlockState)((AlpineFoam)AlpineRegistration.FOAM.get())
                                                   .defaultBlockState()
                                                   .setValue(AlpineFoam.FACES, var34))
                                                .setValue(AlpineFoam.ROW, Math.floorMod(var88, 16)))
                                             .setValue(AlpineFoam.BASE, var88 == var46.water() + 1))
                                          .setValue(AlpineFoam.COLUMN, Math.floorMod(var100.getX() + var100.getZ(), 8))
                                    );
                                    if (var1.version() >= 3 && Math.floorMod(var88, 4) == 0 && Math.floorMod(var43 + var45, 3) == 0) {
                                       BlockPos var35 = var100.relative(SIDES[var31]);
                                       if (var0.isEmptyBlock(var35)) {
                                          Terrain.set(
                                             var0,
                                             var35,
                                             (BlockState)((BlockState)((AlpineSpray)AlpineRegistration.SPRAY.get())
                                                   .defaultBlockState()
                                                   .setValue(AlpineSpray.KIND, 1))
                                                .setValue(AlpineSpray.POWER, var58)
                                          );
                                       }
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }

                  double var61 = var46.riverLevel();
                  if (var46.fall() > 3.0) {
                     for (Direction var73 : Plane.HORIZONTAL) {
                        var61 = Math.min(var61, var1.sample((double)(var43 + var73.getStepX() * 3), (double)(var45 + var73.getStepZ() * 3)).riverLevel());
                     }
                  }

                  boolean var69 = var46.riverLevel() - var61 < 2.5;
                  if (var46.fall() > (var1.version() >= 3 ? 0.6 : 3.0) && (var1.hash(var43, var45, 303L) & (long)(var69 ? 7 : 31)) == 0L) {
                     var37.setY(var46.water() + 1);
                     BlockPos var74 = var37.immutable();
                     if (!var0.getFluidState(var74).isEmpty()) {
                        var74 = null;

                        for (Direction var92 : SIDES) {
                           BlockPos var94 = var37.relative(var92);
                           BlockState var95 = var0.getBlockState(var94);
                           if (var95.isAir() || var95.is((Block)AlpineRegistration.FOAM.get())) {
                              var74 = var94;
                              break;
                           }
                        }
                     }

                     if (var74 != null && (var0.isEmptyBlock(var74) || var0.getBlockState(var74).is((Block)AlpineRegistration.FOAM.get()))) {
                        if (var1.version() >= 3) {
                           Terrain.set(
                              var0,
                              var74,
                              (BlockState)((BlockState)((AlpineSpray)AlpineRegistration.SPRAY.get())
                                    .defaultBlockState()
                                    .setValue(AlpineSpray.KIND, var69 ? 2 : (var53 > 2 ? 1 : 0)))
                                 .setValue(AlpineSpray.POWER, var58)
                           );
                        } else {
                           Terrain.set(
                              var0,
                              var74,
                              (BlockState)((BlockState)((CascadeMist)ExpeditionContent.CASCADE_MIST.get())
                                    .defaultBlockState()
                                    .setValue(CascadeMist.WHERE, var69 ? 2 : (var53 > 2 ? 1 : 0)))
                                 .setValue(CascadeMist.SIZE, 2)
                           );
                        }
                     }
                  }

                  if (var1.version() >= 4 && !AlpineLayout.sea(var46.biome()) && var46.distance() < var46.width() && var46.fall() < 1.2) {
                     AlpineWatershed.Water var75 = var1.watershed().water((double)var43, (double)var45);
                     double var82 = var1.sample((double)var43 - var75.dx() * 18.0, (double)var45 - var75.dz() * 18.0).riverLevel() - var46.riverLevel();
                     AlpineHydraulics.Reach var90 = var1.version() >= 7 ? AlpineHydraulics.at(var1, (double)var43, (double)var45, (double)var46.water()) : null;
                     double var93 = var90 == null ? 0.0 : var90.energy();
                     boolean var96 = var1.version() >= 6 ? var82 > 0.45 || var46.fall() > 0.035 || var75.speed() > 0.024 : var82 > 1.4 || var46.fall() > 0.09;
                     double var29 = var1.noise((double)var43 / 6.0, (double)var45 / 6.0, 8301L);
                     var37.setY(var46.water() + 1);
                     if (var96 && var29 > (var1.version() >= 6 ? -0.48 : -0.08) && var0.isEmptyBlock(var37) && !var75.lake()) {
                        double var98 = var90 == null ? var75.dx() : var90.dx();
                        double var101 = var90 == null ? var75.dz() : var90.dz();
                        if (var98 == 0.0 && var101 == 0.0) {
                           continue;
                        }

                        Direction var102 = Math.abs(var98) > Math.abs(var101)
                           ? (var98 > 0.0 ? Direction.EAST : Direction.WEST)
                           : (var101 > 0.0 ? Direction.SOUTH : Direction.NORTH);
                        Terrain.set(
                           var0,
                           var37,
                           (BlockState)((BlockState)((BlockState)((BlockState)((AlpineFlow)AlpineRegistration.FLOW.get())
                                       .defaultBlockState()
                                       .setValue(AlpineFlow.FACING, var102))
                                    .setValue(
                                       AlpineFlow.STRENGTH,
                                       var1.version() >= 7
                                          ? (var93 > 0.65 ? 2 : (var93 > 0.25 ? 1 : 0))
                                          : (var82 > (var1.version() >= 6 ? 3.5 : 7.0) ? 2 : (var82 > (var1.version() >= 6 ? 0.8 : 2.0) ? 1 : 0))
                                    ))
                                 .setValue(
                                    AlpineFlow.X,
                                    Math.floorMod(
                                       var102 == Direction.NORTH
                                          ? var43
                                          : (var102 == Direction.SOUTH ? -var43 - 1 : (var102 == Direction.EAST ? var45 : -var45 - 1)),
                                       4
                                    )
                                 ))
                              .setValue(
                                 AlpineFlow.Z,
                                 Math.floorMod(
                                    var102 == Direction.NORTH
                                       ? var45
                                       : (var102 == Direction.SOUTH ? -var45 - 1 : (var102 == Direction.EAST ? -var43 - 1 : var43)),
                                    4
                                 )
                              )
                        );
                     }

                     if (var1.version() >= 6
                        && var96
                        && !var75.lake()
                        && var46.distance() < var46.width() * 0.82
                        && var1.variation(var43, var45, 8341L) < 0.018 + Math.min(0.045, var82 * 0.008 + var46.fall() * 0.003)) {
                        var37.setY(var46.floor() + 1);
                        if (var0.getFluidState(var37).is(FluidTags.WATER)) {
                           double var99 = var1.variation(var43, var45, 8343L);
                           Terrain.set(
                              var0,
                              var37,
                              var1.version() >= 9
                                 ? (BlockState)((BlockState)AlpineRegistration.prop(var99 < 0.5 ? "mossy_river_stone" : "river_boulder")
                                       .defaultBlockState()
                                       .setValue(AlpineBoulder.WATERLOGGED, true))
                                    .setValue(AlpineBoulder.FORM, (int)(var1.hash(var43, var45, 8345L) & 3L))
                                 : (
                                    var1.version() >= 8
                                       ? (BlockState)((AlpineWeatheredRock)AlpineRegistration.WEATHERED_ROCK.get())
                                          .defaultBlockState()
                                          .setValue(AlpineWeatheredRock.WEATHER, var99 < 0.45 ? 3 : (var99 < 0.75 ? 2 : 1))
                                       : (
                                          var99 < 0.4
                                             ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
                                             : (var99 < 0.73 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.STONE.defaultBlockState())
                                       )
                                 )
                           );
                        }
                     }
                  }

                  if (var1.version() >= 8
                     && !AlpineLayout.sea(var46.biome())
                     && var46.distance() > var46.width() * 0.65
                     && var46.fall() < 0.3
                     && var1.variation(var43, var45, 8361L) < 0.007
                     && var39 > 2
                     && var39 < 13
                     && var41 > 2
                     && var41 < 13) {
                     int var76 = var46.floor() + 1;

                     for (int var83 = -1; var83 <= 1; var83++) {
                        BlockPos var86 = new BlockPos(var43 + var83, var76, var45);
                        if (var0.getFluidState(var86).is(FluidTags.WATER)
                           && !var0.getBlockState(var86.below()).getCollisionShape(var0, var86.below()).isEmpty()) {
                           Terrain.set(var0, var86, (BlockState)Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Axis.X));
                        }
                     }
                  }

                  if (var1.version() >= 9 && !AlpineLayout.sea(var46.biome())) {
                     double var77 = var1.variation(var43, var45, 8371L);
                     if (var46.fall() < 0.018
                        && var46.water() - var46.floor() <= 4
                        && var46.distance() > var46.width() * 0.55
                        && var1.noise((double)var43 / 17.0, (double)var45 / 17.0, 8373L) > 0.12
                        && var77 < 0.075) {
                        var37.setY(var46.water() + 1);
                        if (var0.isEmptyBlock(var37) && var0.getBlockState(var37.below()).is(Blocks.WATER)) {
                           Terrain.set(var0, var37, Blocks.LILY_PAD.defaultBlockState());
                        }
                     }

                     if (var77 > 0.985 && var46.water() - var46.floor() < 5) {
                        var37.setY(var46.floor() + 1);
                        if (var0.getBlockState(var37).is(Blocks.WATER)) {
                           Terrain.set(
                              var0,
                              var37,
                              (BlockState)((BlockState)AlpineRegistration.prop("mossy_river_pebbles")
                                    .defaultBlockState()
                                    .setValue(AlpineBoulder.WATERLOGGED, true))
                                 .setValue(AlpineBoulder.FORM, (int)(var1.hash(var43, var45, 8375L) & 3L))
                           );
                        }
                     }
                  }
               } else if (var1.version() >= 9
                  && var0.isEmptyBlock(var37)
                  && var46.ground() < var46.snowLine()
                  && (
                     var1.version() < 12
                        || var0.getFluidState(var37).isEmpty() && var0.getBlockState(var37.below()).isFaceSturdy(var0, var37.below(), Direction.UP)
                  )
                  && var1.variation(var43, var45, 8381L) < (var46.distance() < var46.width() + 24.0 ? 0.026 : 0.005)) {
                  String var52 = AlpineRegistration.BOULDER_IDS[(int)(var1.hash(var43, var45, 8383L) & 2147483647L) % 6];
                  Terrain.set(
                     var0,
                     var37,
                     (BlockState)AlpineRegistration.prop(var52).defaultBlockState().setValue(AlpineBoulder.FORM, (int)(var1.hash(var43, var45, 8385L) & 3L))
                  );
               } else if (var1.version() >= 4) {
                  AlpineVegetation.ground(var0, var1, var43, var45, var46, var37, var36 != null ? var36.slope(var39, var41) : Double.NaN);
               } else if (var0.isEmptyBlock(var37) && var0.getBlockState(var37.below()).is(BlockTags.DIRT)) {
                  if (var1.version() >= 2 && var46.biome() == 9) {
                     Terrain.set(var0, var37, Blocks.SNOW.defaultBlockState());
                  } else if (!(var46.ground() > var46.snowLine() - 15.0)) {
                     double var51 = var1.variation(var43, var45, 80L);
                     if (var51 < 0.018) {
                        Terrain.set(var0, var37, ((ForestFloor.Boulder)ExpeditionContent.MOSSY_STONE.get()).defaultBlockState());
                     } else if (var1.variation(var43, var45, 86L) < var46.forest()) {
                        if (var51 < 0.31) {
                           Terrain.set(
                              var0,
                              var37,
                              (BlockState)((ForestFloor.Undergrowth)ExpeditionContent.UNDERGROWTH.get())
                                 .defaultBlockState()
                                 .setValue(ForestFloor.Undergrowth.VARIANT, var51 < 0.14 ? 0 : 1)
                           );
                        } else if (var51 < 0.77) {
                           Terrain.set(
                              var0,
                              var37,
                              (BlockState)((ForestFloor.Litter)ExpeditionContent.FOREST_LITTER.get())
                                 .defaultBlockState()
                                 .setValue(ForestFloor.Litter.VARIANT, (int)(var1.hash(var43, var45, 81L) & 2147483647L) % 3)
                           );
                        }
                     } else if (var51 < (var1.version() >= 3 ? 0.78 : 0.6)) {
                        Terrain.set(
                           var0,
                           var37,
                           (BlockState)((ForestFloor.Undergrowth)ExpeditionContent.UNDERGROWTH.get())
                              .defaultBlockState()
                              .setValue(ForestFloor.Undergrowth.VARIANT, var51 < (var1.version() >= 3 ? 0.6 : 0.2) ? 5 : 6)
                        );
                     } else if (var51 < (var1.version() >= 3 ? 0.81 : 0.624)) {
                        Terrain.set(var0, var37, (var51 < (var1.version() >= 3 ? 0.795 : 0.608) ? Blocks.OXEYE_DAISY : Blocks.AZURE_BLUET).defaultBlockState());
                     }
                  }
               }
            }
         }
      }

      if (var1.version() >= 4) {
         AlpineVegetation.drape(var0, var1, var36, var3, var4);
      }

      if (var5 && var1.version() < 14 && var2.nextInt(14) == 0) {
         int var40 = var3 + 8;
         int var42 = var4 + 8;
         AlpineLayout.Sample var44 = var1.sample((double)var40, (double)var42);
         if (var44.forest() > 0.6 && !var44.wet() && var1.slope(var40, var42) < 0.25) {
            ForestStand.plant(var0, new BlockPos(var40, var44.floor() + 1, var42), WildTrees.Kind.LEANER, var2, 0.4F);
         }
      }
   }
}
