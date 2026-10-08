package com.formaworks.frontierhunts.landscape;

import com.formaworks.frontierhunts.environment.ForestStand;
import com.formaworks.frontierhunts.environment.Terrain;
import com.formaworks.frontierhunts.environment.WildTrees;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public final class AlpineFallsDressing extends Feature<NoneFeatureConfiguration> {
   private static final Direction[] SIDES = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

   public AlpineFallsDressing() {
      super(NoneFeatureConfiguration.CODEC);
   }

   public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> var1) {
      if (var1.chunkGenerator() instanceof AlpineGenerator var2) {
         AlpineLayout var8 = var2.layout();
         AlpineFalls var4 = var8.falls();
         if (var4 == null) {
            return false;
         } else {
            int var5 = var1.origin().getX() & -16;
            int var6 = var1.origin().getZ() & -16;
            AlpineFalls.Site var7 = var4.site(Math.floorDiv(var5, 384), Math.floorDiv(var6, 384));
            if (var7 != null
               && (var7.contains(var5, var6) || var7.contains(var5 + 15, var6 + 15) || var7.contains(var5, var6 + 15) || var7.contains(var5 + 15, var6))) {
               dress(var1.level(), (var1x, var2x) -> var8.sample((double)var1x, (double)var2x), var8, var7, var5, var6, var1.random());
               return true;
            } else {
               return false;
            }
         }
      } else {
         return false;
      }
   }

   public static void dress(
      WorldGenLevel var0, AlpineFallsDressing.Columns var1, AlpineLayout var2, AlpineFalls.Site var3, int var4, int var5, RandomSource var6
   ) {
      if (var2.version() >= 15) {
         meetLakes(var0, var1, var4, var5);
      }

      int var7 = var3.palette.ordinal();
      MutableBlockPos var8 = new MutableBlockPos();
      int var9 = 0;

      for (int var10 = 0; var10 < 16; var10++) {
         for (int var11 = 0; var11 < 16; var11++) {
            int var12 = var4 + var10;
            int var13 = var5 + var11;
            AlpineLayout.Sample var14 = var1.at(var12, var13);
            if (var14.scenic()) {
               int var15 = AlpineFalls.kind(var14.site());
               double var16 = (double)AlpineFalls.wet(var14.site()) / 255.0;
               double var18 = var2.variation(var12, var13, 9601L);
               double var20 = var2.noise((double)var12 / 7.0, (double)var13 / 7.0, 9603L);
               int var22 = var14.floor();
               boolean var23 = var14.water() > var22;
               if (var23) {
                  int var35 = var14.water() - var22;
                  boolean var25 = AlpineFalls.spill(var14.site());
                  var8.set(var12, var22 + 1, var13);
                  if (!var25 && var35 <= 2 && var18 < (var15 == 1 ? 0.05 : 0.035) && isWater(var0, var8)) {
                     String var37 = var18 < 0.018 ? "mossy_river_boulder" : (var18 < 0.034 ? "mossy_river_stone" : "river_pebbles");
                     set(
                        var0,
                        var8,
                        var4,
                        var5,
                        (BlockState)((BlockState)AlpineRegistration.prop(var37).defaultBlockState().setValue(AlpineBoulder.WATERLOGGED, true))
                           .setValue(AlpineBoulder.FORM, (int)(var2.hash(var12, var13, 9605L) & 3L))
                     );
                  }

                  if ((var15 == 1 || var15 == 11 || var15 == 10) && var16 < 0.3 && var35 <= 3 && var20 > 0.28 && var18 > 0.55 && var18 < 0.72) {
                     var8.set(var12, var14.water() + 1, var13);
                     if (var0.isEmptyBlock(var8) && isWater(var0, var8.below())) {
                        set(var0, var8, var4, var5, Blocks.LILY_PAD.defaultBlockState());
                     }
                  }
               } else {
                  long var24 = var14.cavity();
                  if (AlpineFalls.hasCavity(var24)) {
                     int var26 = AlpineFalls.cavityTop(var24);
                     int var27 = AlpineFalls.cavityBottom(var24);
                     int var28 = AlpineFalls.cavityWater(var24);
                     var8.set(var12, var26, var13);
                     if (var26 > var28 + 1 && var0.isEmptyBlock(var8) && var18 < 0.45) {
                        set(var0, var8, var4, var5, Blocks.HANGING_ROOTS.defaultBlockState());
                     }

                     var8.set(var12, var27 + 1, var13);
                     if (var28 < var27 + 1
                        && var0.isEmptyBlock(var8)
                        && var0.getBlockState(var8.below()).isFaceSturdy(var0, var8.below(), Direction.UP)
                        && var18 > 0.55) {
                        set(var0, var8, var4, var5, Blocks.MOSS_CARPET.defaultBlockState());
                     }

                     for (int var29 = var27 + 1; var29 < var26; var29++) {
                        var8.set(var12, var29, var13);
                        if (var0.isEmptyBlock(var8) && !(var2.variation(var12 + var29 * 13, var13, 9607L) > 0.14)) {
                           for (Direction var33 : SIDES) {
                              BlockPos var34 = var8.relative(var33);
                              if (var0.getBlockState(var34).isFaceSturdy(var0, var34, var33.getOpposite())) {
                                 set(
                                    var0,
                                    var8,
                                    var4,
                                    var5,
                                    (BlockState)Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(var33), true)
                                 );
                                 break;
                              }
                           }
                        }
                     }
                  }

                  boolean var36 = var15 == 4 || var15 == 5 || var15 == 8 || var15 == 6 && !AlpineFalls.island(var14.site());
                  if (var36) {
                     vines(var0, var1, var2, var12, var13, var14, var4, var5, var16, var6);
                  }

                  var8.set(var12, var22 + 1, var13);
                  BlockState var38 = var0.getBlockState(var8);
                  boolean var39 = var38.isAir()
                     || var38.is((Block)AlpineRegistration.PASTURE.get()) && var16 > 0.3
                     || var38.is(Blocks.SHORT_GRASS) && var16 > 0.2;
                  if (var39) {
                     BlockState var40 = var0.getBlockState(var8.below());
                     boolean var41 = var40.is(BlockTags.DIRT) || var40.is(Blocks.MOSS_BLOCK) || var40.is(Blocks.GRAVEL) && var15 == 3;
                     if (var2.version() >= 16 && !var41 && var0.getFluidState(var8).isEmpty()) {
                        boolean var44 = var40.is(BlockTags.BASE_STONE_OVERWORLD)
                           || var40.is(Blocks.GRAVEL)
                           || var40.is(Blocks.COBBLESTONE)
                           || var40.is(Blocks.MOSSY_COBBLESTONE)
                           || var40.is(Blocks.CALCITE)
                           || var40.is(Blocks.TUFF)
                           || var40.is(Blocks.ANDESITE);
                        if (var44 && var16 > 0.16) {
                           double var46 = 0.26 + var16 * 0.42;
                           if (var18 < var46 * 0.55) {
                              set(var0, var8.below(), var4, var5, Blocks.MOSS_BLOCK.defaultBlockState());
                              set(var0, var8, var4, var5, var20 > 0.05 ? Blocks.FERN.defaultBlockState() : Blocks.MOSS_CARPET.defaultBlockState());
                           } else if (var18 < var46) {
                              set(var0, var8, var4, var5, Blocks.MOSS_CARPET.defaultBlockState());
                           } else if (var18 < var46 + 0.05 && var15 == 3) {
                              set(
                                 var0,
                                 var8,
                                 var4,
                                 var5,
                                 (BlockState)AlpineRegistration.prop(var18 < var46 + 0.022 ? "mossy_river_boulder" : "mossy_river_stone")
                                    .defaultBlockState()
                                    .setValue(AlpineBoulder.FORM, (int)(var2.hash(var12, var13, 9615L) & 3L))
                              );
                           } else if (var18 > 0.994) {
                              set(var0, var8, var4, var5, Blocks.LILY_OF_THE_VALLEY.defaultBlockState());
                           }
                        }
                     } else if (var41 && var0.getFluidState(var8).isEmpty()) {
                        if (var9 < 2
                           && (var15 == 7 || var15 == 14 || AlpineFalls.island(var14.site()))
                           && var16 < 0.7
                           && var18 > (var16 > 0.08 ? 0.975 : 0.993)
                           && var40.is(BlockTags.DIRT)
                           && gentle(var1, var12, var13)) {
                           WildTrees.Kind var42 = switch (var7) {
                              case 1 -> var18 > 0.992 ? WildTrees.Kind.WILLOW : WildTrees.Kind.BIRCH;
                              case 2 -> var18 > 0.993 ? WildTrees.Kind.WILLOW : WildTrees.Kind.MAPLE;
                              case 3 -> WildTrees.Kind.BIRCH;
                              default -> var14.ground() > var14.snowLine() - 90.0 ? WildTrees.Kind.FIR : WildTrees.Kind.ASPEN;
                           };
                           if (var2.version() >= 14) {
                              AlpineTrees.Species var45 = switch (var7) {
                                 case 1 -> var18 > 0.992 ? AlpineTrees.Species.WILLOW : AlpineTrees.Species.BIRCH;
                                 case 2 -> var18 > 0.993 ? AlpineTrees.Species.WILLOW : AlpineTrees.Species.SPRUCE;
                                 case 3 -> AlpineTrees.Species.BIRCH;
                                 default -> var14.ground() > var14.snowLine() - 90.0 ? AlpineTrees.Species.FIR : AlpineTrees.Species.SPRUCE;
                              };
                              if (AlpineForest.plant(var0, var8.immutable(), var45, var6, 0.4F + var6.nextFloat() * 0.5F)) {
                                 var9++;
                                 continue;
                              }
                           } else if (ForestStand.plant(var0, var8.immutable(), var42, var6, 0.35F + var6.nextFloat() * 0.45F, var6.nextFloat() < 0.25F)) {
                              var9++;
                              continue;
                           }
                        }

                        if (var15 != 3 || !var40.is(Blocks.GRAVEL)) {
                           double var43 = Math.min(1.0, 0.35 + var16 * 1.2);
                           if (var18 < 0.1 * var43) {
                              String var47 = var18 < 0.035
                                 ? "arching_fern"
                                 : (
                                    var18 < 0.06
                                       ? (var16 > 0.3 ? "river_brush" : "spreading_fern")
                                       : (var18 < 0.08 ? "broadleaf_thicket" : "flowering_bramble")
                                 );
                              int var48 = var0.isEmptyBlock(var8.above()) ? (var2.variation(var12, var13, 9613L) < 0.6 ? 2 : 1) : 0;
                              set(var0, var8, var4, var5, (BlockState)AlpineRegistration.prop(var47).defaultBlockState().setValue(AlpineThicket.SIZE, var48));
                           } else if (var18 < 0.3 * var43) {
                              if (var0.isEmptyBlock(var8.above()) && var18 < 0.22 * var43) {
                                 doublePlant(var0, var8, var4, var5, Blocks.LARGE_FERN);
                              } else {
                                 set(var0, var8, var4, var5, Blocks.FERN.defaultBlockState());
                              }
                           } else if (var18 < 0.46 * var43) {
                              set(var0, var8, var4, var5, Blocks.MOSS_CARPET.defaultBlockState());
                           } else if (var18 < 0.5 * var43 && var7 != 3) {
                              set(var0, var8, var4, var5, (var20 > 0.1 ? Blocks.FLOWERING_AZALEA : Blocks.AZALEA).defaultBlockState());
                           } else if (var18 > 0.975) {
                              set(
                                 var0,
                                 var8,
                                 var4,
                                 var5,
                                 (var7 == 2
                                       ? (var20 > 0.0 ? Blocks.BLUE_ORCHID : Blocks.LILY_OF_THE_VALLEY)
                                       : (var20 > 0.0 ? Blocks.LILY_OF_THE_VALLEY : Blocks.ALLIUM))
                                    .defaultBlockState()
                              );
                           }
                        } else if (var18 < 0.06) {
                           set(
                              var0,
                              var8,
                              var4,
                              var5,
                              (BlockState)AlpineRegistration.prop(var18 < 0.03 ? "mossy_river_stone" : "river_pebbles")
                                 .defaultBlockState()
                                 .setValue(AlpineBoulder.FORM, (int)(var2.hash(var12, var13, 9611L) & 3L))
                           );
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static void meetLakes(WorldGenLevel var0, AlpineFallsDressing.Columns var1, int var2, int var3) {
      Object var4 = AlpineStreams.engine(var0);
      if (var4 != null) {
         MutableBlockPos var5 = new MutableBlockPos();
         BlockState var6 = Blocks.WATER.defaultBlockState();
         int[] var7 = new int[16];

         for (int var8 = 0; var8 < 16; var8++) {
            var7[var8] = AlpineStreams.lake(var4, var2 + (var8 & 3) * 4 + 2, var3 + (var8 >> 2) * 4 + 2, 6);
         }

         for (int var19 = 0; var19 < 16; var19++) {
            for (int var9 = 0; var9 < 16; var9++) {
               int var10 = var7[(var9 >> 2) * 4 + (var19 >> 2)];
               if (var10 != Integer.MIN_VALUE) {
                  int var11 = var2 + var19;
                  int var12 = var3 + var9;
                  AlpineLayout.Sample var13 = var1.at(var11, var12);
                  if (var13.scenic()) {
                     int var14 = AlpineFalls.kind(var13.site());
                     if (var14 != 4 && var14 != 5 && var14 != 8 && var14 != 12 && !AlpineFalls.spill(var13.site())) {
                        int var15 = var13.floor();
                        int var16 = Math.max(var15, var13.water());
                        if (var16 < var10 && var10 - var15 <= 10) {
                           for (int var17 = var15 + 1; var17 <= var10; var17++) {
                              var5.set(var11, var17, var12);
                              BlockState var18 = var0.getBlockState(var5);
                              if (!var18.is(Blocks.WATER)) {
                                 if (!var18.isAir() && (!var18.canBeReplaced() || !var18.getFluidState().isEmpty())) {
                                    break;
                                 }

                                 set(var0, var5, var2, var3, var6);
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

   private static void vines(
      WorldGenLevel var0,
      AlpineFallsDressing.Columns var1,
      AlpineLayout var2,
      int var3,
      int var4,
      AlpineLayout.Sample var5,
      int var6,
      int var7,
      double var8,
      RandomSource var10
   ) {
      int var11 = var5.floor();

      for (Direction var15 : SIDES) {
         int var16 = var3 + var15.getStepX();
         int var17 = var4 + var15.getStepZ();
         if (var16 >> 4 == var6 >> 4 && var17 >> 4 == var7 >> 4) {
            AlpineLayout.Sample var18 = var1.at(var16, var17);
            int var19 = var11 - Math.max(var18.floor(), var18.water());
            if (var19 >= 3) {
               boolean var20 = AlpineFalls.spill(var18.site());

               for (Direction var24 : SIDES) {
                  AlpineLayout.Sample var25 = var1.at(var16 + var24.getStepX(), var17 + var24.getStepZ());
                  if (AlpineFalls.spill(var25.site()) && var25.water() >= var18.water()) {
                     var20 = true;
                     break;
                  }
               }

               if (!var20) {
                  double var29 = 0.1 + var8 * 0.55;
                  if (!(var2.variation(var3 * 3 + var15.ordinal(), var4, 9621L) > var29)) {
                     int var30 = var11 - (int)(var2.variation(var3, var4 + var15.ordinal(), 9623L) * Math.min(8.0, (double)var19 * 0.4));
                     int var31 = 2 + (int)(var2.variation(var3 + var15.ordinal(), var4, 9625L) * Math.min(14.0, (double)var19 * 0.6));
                     BooleanProperty var32 = VineBlock.getPropertyForFace(var15.getOpposite());

                     for (int var26 = var30; var26 > var30 - var31 && var26 > Math.max(var18.floor(), var18.water()) + 1; var26--) {
                        BlockPos var27 = new BlockPos(var16, var26, var17);
                        if (!var0.isEmptyBlock(var27)) {
                           break;
                        }

                        BlockPos var28 = new BlockPos(var3, var26, var4);
                        if (!var0.getBlockState(var28).isFaceSturdy(var0, var28, var15)) {
                           break;
                        }

                        set(var0, var27, var6, var7, (BlockState)Blocks.VINE.defaultBlockState().setValue(var32, true));
                     }
                  }
               }
            }
         }
      }
   }

   private static boolean isWater(WorldGenLevel var0, BlockPos var1) {
      BlockState var2 = var0.getBlockState(var1);
      return var2.is(Blocks.WATER) && var2.getFluidState().isSource();
   }

   private static void doublePlant(WorldGenLevel var0, BlockPos var1, int var2, int var3, Block var4) {
      set(var0, var1, var2, var3, (BlockState)var4.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
      set(var0, var1.above(), var2, var3, (BlockState)var4.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
   }

   private static boolean gentle(AlpineFallsDressing.Columns var0, int var1, int var2) {
      int var3 = var0.at(var1, var2).floor();
      return Math.abs(var0.at(var1 + 2, var2).floor() - var3) <= 2
         && Math.abs(var0.at(var1 - 2, var2).floor() - var3) <= 2
         && Math.abs(var0.at(var1, var2 + 2).floor() - var3) <= 2
         && Math.abs(var0.at(var1, var2 - 2).floor() - var3) <= 2;
   }

   private static void set(WorldGenLevel var0, BlockPos var1, int var2, int var3, BlockState var4) {
      if (var1.getX() >> 4 == var2 >> 4 && var1.getZ() >> 4 == var3 >> 4) {
         Terrain.set(var0, var1, var4);
      }
   }

   public interface Columns {
      AlpineLayout.Sample at(int var1, int var2);
   }
}
