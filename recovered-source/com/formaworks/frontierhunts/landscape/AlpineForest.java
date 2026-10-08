package com.formaworks.frontierhunts.landscape;

import com.formaworks.frontierhunts.environment.Terrain;
import com.formaworks.frontierhunts.environment.WildLeaves;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongBidirectionalIterator;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectBidirectionalIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.Plane;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

final class AlpineForest {
   private static final int CELL = 5;

   static void stand(WorldGenLevel var0, AlpineLayout var1, RandomSource var2, int var3, int var4) {
      boolean var5 = var1.version() >= 21;
      int var6 = Math.floorDiv(var3, 5);
      int var7 = Math.floorDiv(var3 + 15, 5);
      int var8 = Math.floorDiv(var4, 5);
      int var9 = Math.floorDiv(var4 + 15, 5);

      for (int var10 = var6; var10 <= var7; var10++) {
         for (int var11 = var8; var11 <= var9; var11++) {
            long var12 = var1.hash(var10, var11, 9301L);
            int var14 = var10 * 5 + (int)(var12 >>> 8 & 65535L) % 5;
            int var15 = var11 * 5 + (int)(var12 >>> 32 & 65535L) % 5;
            if (var14 >= var3 && var14 <= var3 + 15 && var15 >= var4 && var15 <= var4 + 15) {
               AlpineLayout.Sample var16 = var1.sample((double)var14, (double)var15);
               if (!var16.wet()
                  && !(var16.rock() > 0.35)
                  && (var16.biome() < 18 || var16.biome() > 20)
                  && (!var16.scenic() || AlpineFalls.kind(var16.site()) != 4 && !(var1.slope(var14, var15) > 0.9))) {
                  double var17 = var1.slope(var14, var15);
                  if (!(var17 > 1.7)
                     && !(var16.ground() > var16.snowLine() + 45.0)
                     && (var1.version() < 20 || !(var1.trail((double)var14, (double)var15) > 0.2))) {
                     double var19 = AlpineLayout.smooth(var16.snowLine() - 130.0, var16.snowLine() + 40.0, var16.ground());
                     double var21 = AlpineLayout.smooth(0.1, 0.68, var16.forest()) * (1.0 - 0.65 * var19) * (var17 > 1.1 ? 0.55 : 1.0);
                     boolean var23 = var16.biome() == 27;
                     if (var23) {
                        var21 *= 0.62;
                     }

                     if (var16.biome() == 26) {
                        var21 = Math.max(var21, 0.42 * (1.0 - 0.5 * var19));
                     }

                     double var24 = AlpineLayout.smooth(
                        0.28,
                        0.58,
                        var1.noise((double)var14 / 46.0, (double)var15 / 46.0, 9333L) + 0.35 * var1.noise((double)var14 / 17.0, (double)var15 / 17.0, 9335L)
                     );
                     var21 *= 1.0 - 0.8 * var24;
                     double var26 = var1.variation(var14, var15, 9303L);
                     float var28 = (float)((0.22 + 0.78 * Math.pow(var1.variation(var14, var15, 9309L), 0.75)) * (0.55 + 0.45 * var21) * (1.0 - 0.55 * var19));
                     if (var16.biome() == 13 || var16.biome() >= 21 && var16.biome() <= 23) {
                        var28 = Math.min(1.0F, var28 + 0.12F);
                     }

                     if (AlpineLayout.soil(var16.biome())) {
                        var28 *= switch (var16.biome()) {
                           case 31 -> 0.55F;
                           case 32 -> 0.5F;
                           case 33 -> 1.0F;
                           default -> 0.72F;
                           case 35 -> 0.62F;
                           case 36 -> 0.7F;
                           case 37 -> 1.15F;
                           case 38 -> 0.66F;
                        };
                     }

                     AlpineTrees.Species var29 = null;
                     if (var26 < var21 * 0.66) {
                        var29 = var1.variation(var14, var15, 9305L) < (var23 ? 0.42 : 0.055) ? AlpineTrees.Species.SNAG : canopy(var1, var16, var14, var15);
                     } else if (var26 < var21 * 0.8) {
                        var29 = understory(var1, var16, var14, var15);
                        var28 = (float)var1.variation(var14, var15, 9313L);
                     } else if (var16.forest() < 0.12 && var26 > 0.9955 && !var16.wet() && var17 < 0.8) {
                        double var30 = var1.variation(var14, var15, 9315L);
                        var29 = var19 > 0.3
                           ? AlpineTrees.Species.FIR
                           : (var30 < 0.45 ? AlpineTrees.Species.SPRUCE : (var30 < 0.75 ? AlpineTrees.Species.ASPEN : AlpineTrees.Species.PINE));
                        var28 = 0.55F + 0.45F * (float)var1.variation(var14, var15, 9317L);
                     }

                     if (var29 != null) {
                        BlockPos var43 = new BlockPos(var14, var16.floor() + 1, var15);
                        plant(var0, var43, var29, RandomSource.create(var1.hash(var14, var15, 9319L)), var28, var5);
                     }
                  }
               }
            }
         }
      }

      int var32 = var3 + 2 + (int)(var1.hash(var3, var4, 9321L) & 7L);
      int var34 = var4 + 2 + (int)(var1.hash(var3, var4, 9321L) >>> 8 & 7L);
      AlpineLayout.Sample var36 = var1.sample((double)var32, (double)var34);
      if (!var5) {
         if (!var36.wet() && var36.forest() > 0.35 && var1.variation(var3, var4, 9323L) < var36.forest() * 0.55 && var1.slope(var32, var34) < 0.6) {
            plant(
               var0,
               new BlockPos(var32, var36.floor() + 1, var34),
               AlpineTrees.Species.DEADFALL,
               RandomSource.create(var1.hash(var32, var34, 9325L)),
               (float)var1.variation(var32, var34, 9327L)
            );
         }
      } else {
         for (int var13 = 0; var13 < 3; var13++) {
            long var38 = var1.hash(var3 + var13 * 977, var4, 9321L);
            var32 = var3 + 1 + (int)(var38 & 15L) % 14;
            var34 = var4 + 1 + (int)(var38 >>> 8 & 15L) % 14;
            var36 = var1.sample((double)var32, (double)var34);
            if (!var36.wet()
               && !(var36.forest() < 0.3)
               && !(var1.slope(var32, var34) > 0.75)
               && !(var1.variation(var32, var34, (long)(9323 + var13)) > var36.forest() * (var13 == 0 ? 0.85 : (var13 == 1 ? 0.55 : 0.3)))) {
               AlpineTrees.Species var39 = var1.variation(var32, var34, 9329L) < 0.45 ? AlpineTrees.Species.DEADFALL_PINE : AlpineTrees.Species.DEADFALL;

               for (int var40 = 0; var40 < 5; var40++) {
                  int var18 = var40 == 0 ? 0 : (int)(var1.hash(var32, var34, (long)(9331 + var40)) & 3L) - 1;
                  int var41 = var40 == 0 ? 0 : (int)(var1.hash(var32, var34, (long)(9331 + var40)) >>> 4 & 3L) - 1;
                  BlockPos var20 = new BlockPos(var32 + var18, var1.sample((double)(var32 + var18), (double)(var34 + var41)).floor() + 1, var34 + var41);
                  if (plant(
                     var0,
                     var20,
                     var39,
                     RandomSource.create(var1.hash(var32, var34, 9325L) + (long)(var40 * 7919)),
                     (float)(var1.variation(var32, var34, 9327L) * (1.0 - 0.15 * (double)var40)),
                     true
                  )) {
                     break;
                  }
               }
            }
         }

         poles(var0, var1, var3, var4);
      }
   }

   private static void poles(WorldGenLevel var0, AlpineLayout var1, int var2, int var3) {
      int var4 = Math.floorDiv(var2, 3);
      int var5 = Math.floorDiv(var2 + 15, 3);
      int var6 = Math.floorDiv(var3, 3);
      int var7 = Math.floorDiv(var3 + 15, 3);
      Block var8 = AlpineRegistration.prop("sapling_pole");
      if (var8 != Blocks.AIR) {
         MutableBlockPos var9 = new MutableBlockPos();

         for (int var10 = var4; var10 <= var5; var10++) {
            for (int var11 = var6; var11 <= var7; var11++) {
               long var12 = var1.hash(var10, var11, 9401L);
               int var14 = var10 * 3 + (int)(var12 >>> 8 & 65535L) % 3;
               int var15 = var11 * 3 + (int)(var12 >>> 32 & 65535L) % 3;
               if (var14 >= var2 && var14 <= var2 + 15 && var15 >= var3 && var15 <= var3 + 15) {
                  AlpineLayout.Sample var16 = var1.sample((double)var14, (double)var15);
                  if (!var16.wet()
                     && !(var16.rock() > 0.35)
                     && !var16.scenic()
                     && !AlpineLayout.sea(var16.biome())
                     && !(var16.ground() > var16.snowLine() - 40.0)) {
                     double var17 = var16.forest();
                     if (!(var17 < 0.36) && !(var1.slope(var14, var15) > 1.1) && !(var1.trail((double)var14, (double)var15) > 0.2)) {
                        double var19 = AlpineLayout.smooth(
                           -0.1,
                           0.4,
                           var1.noise((double)var14 / 19.0, (double)var15 / 19.0, 9403L) + 0.4 * var1.noise((double)var14 / 7.0, (double)var15 / 7.0, 9405L)
                        );
                        if (!(var1.variation(var14, var15, 9407L) > 0.6 * AlpineLayout.smooth(0.36, 0.8, var17) * var19)) {
                           int var21 = var16.biome();
                           boolean var22 = var21 == 6 || var21 == 7 || var21 == 8 || var21 == 24 || var21 == 25 || var21 == 33 || var21 == 38 || var21 == 36;
                           double var23 = var1.variation(var14, var15, 9409L);
                           int var25 = var23 < 0.16 ? 2 : (var22 ? (var23 < 0.8 ? 1 : 0) : (var23 < 0.93 ? 0 : 1));
                           int var26 = var25 == 2 ? 2 + (int)(var1.variation(var14, var15, 9411L) * 3.0) : 3 + (int)(var1.variation(var14, var15, 9411L) * 4.0);
                           BlockPos var27 = new BlockPos(var14, var16.floor() + 1, var15);
                           if (Terrain.writable(var0, var27) && var0.getBlockState(var27.below()).is(BlockTags.DIRT)) {
                              int var28 = 0;

                              for (int var29 = 0; var29 < var26; var29++) {
                                 var9.set(var14, var27.getY() + var29, var15);
                                 if (!Terrain.writable(var0, var9)) {
                                    break;
                                 }

                                 BlockState var30 = var0.getBlockState(var9);
                                 if (!var30.isAir() && (!var30.canBeReplaced() || var30.is(BlockTags.LEAVES)) || !var30.getFluidState().isEmpty()) {
                                    break;
                                 }

                                 var28++;
                              }

                              if (var28 >= 2) {
                                 for (int var31 = 0; var31 < var28; var31++) {
                                    var9.set(var14, var27.getY() + var31, var15);
                                    var0.setBlock(
                                       var9,
                                       (BlockState)((BlockState)var8.defaultBlockState().setValue(AlpinePole.KIND, var25))
                                          .setValue(AlpinePole.TOP, var31 == var28 - 1),
                                       4
                                    );
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

   static AlpineTrees.Species canopy(AlpineLayout var0, AlpineLayout.Sample var1, int var2, int var3) {
      double var4 = var0.variation(var2, var3, 9311L);
      boolean var6 = var1.distance() < var1.width() + 14.0;
      boolean var7 = var1.ground() > var1.snowLine() - 95.0;

      AlpineTrees.Species var8 = switch (var1.biome()) {
         case 3 -> var4 < 0.45 ? AlpineTrees.Species.PINE : (var4 < 0.8 ? AlpineTrees.Species.FIR : AlpineTrees.Species.SPRUCE);
         default -> var4 < 0.5
         ? AlpineTrees.Species.SPRUCE
         : (var4 < 0.78 ? AlpineTrees.Species.FIR : (var4 < 0.9 ? AlpineTrees.Species.PINE : AlpineTrees.Species.ASPEN));
         case 6 -> var4 < 0.62 ? AlpineTrees.Species.BIRCH : (var4 < 0.84 ? AlpineTrees.Species.SPRUCE : AlpineTrees.Species.ASPEN);
         case 7 -> var4 < 0.45
         ? AlpineTrees.Species.MAPLE
         : (var4 < 0.6 ? AlpineTrees.Species.BIRCH : (var4 < 0.8 ? AlpineTrees.Species.FIR : AlpineTrees.Species.SPRUCE));
         case 8 -> var4 < 0.68 ? AlpineTrees.Species.ASPEN : (var4 < 0.88 ? AlpineTrees.Species.SPRUCE : AlpineTrees.Species.FIR);
         case 9 -> var4 < 0.5 ? AlpineTrees.Species.FIR : (var4 < 0.86 ? AlpineTrees.Species.SPRUCE : AlpineTrees.Species.PINE);
         case 11, 14 -> var4 < 0.6 ? AlpineTrees.Species.PINE : AlpineTrees.Species.FIR;
         case 12 -> var6 && var4 < 0.5
         ? AlpineTrees.Species.WILLOW
         : (
            var4 < 0.38
               ? AlpineTrees.Species.SPRUCE
               : (
                  var4 < 0.58
                     ? AlpineTrees.Species.MAPLE
                     : (var4 < 0.76 ? AlpineTrees.Species.BIRCH : (var4 < 0.88 ? AlpineTrees.Species.FIR : AlpineTrees.Species.ASPEN))
               )
         );
         case 13 -> var4 < 0.46
         ? AlpineTrees.Species.SPRUCE
         : (var4 < 0.72 ? AlpineTrees.Species.MAPLE : (var4 < 0.9 ? AlpineTrees.Species.FIR : AlpineTrees.Species.BIRCH));
         case 16 -> var6 && var4 < 0.45
         ? AlpineTrees.Species.WILLOW
         : (
            var4 < 0.34
               ? AlpineTrees.Species.BIRCH
               : (var4 < 0.52 ? AlpineTrees.Species.MAPLE : (var4 < 0.8 ? AlpineTrees.Species.SPRUCE : AlpineTrees.Species.FIR))
         );
         case 17 -> var6 && var4 < 0.3
         ? AlpineTrees.Species.WILLOW
         : (var4 < 0.45 ? AlpineTrees.Species.SPRUCE : (var4 < 0.74 ? AlpineTrees.Species.BIRCH : AlpineTrees.Species.FIR));
         case 24 -> var4 < 0.7
         ? (var0.variation(var2, var3, 9343L) < 0.15 + 0.62 * core(var0, var2, var3) ? AlpineTrees.Species.GOLDEN_ASPEN : AlpineTrees.Species.ASPEN)
         : (var4 < 0.84 ? AlpineTrees.Species.SPRUCE : (var4 < 0.93 ? AlpineTrees.Species.FIR : AlpineTrees.Species.BIRCH));
         case 25 -> var4 < 0.52
         ? (var0.variation(var2, var3, 9343L) < 0.2 + 0.72 * core(var0, var2, var3) ? AlpineTrees.Species.AUTUMN_MAPLE : AlpineTrees.Species.MAPLE)
         : (
            var4 < 0.62
               ? AlpineTrees.Species.BIRCH
               : (var4 < 0.7 ? AlpineTrees.Species.GOLDEN_ASPEN : (var4 < 0.88 ? AlpineTrees.Species.SPRUCE : AlpineTrees.Species.FIR))
         );
         case 26 -> var4 < 0.5
         ? AlpineTrees.Species.LARCH
         : (var4 < 0.64 ? AlpineTrees.Species.BLUE_SPRUCE : (var4 < 0.86 ? AlpineTrees.Species.FIR : AlpineTrees.Species.PINE));
         case 27 -> var4 < 0.34
         ? AlpineTrees.Species.ASPEN
         : (var4 < 0.5 ? AlpineTrees.Species.GOLDEN_ASPEN : (var4 < 0.72 ? AlpineTrees.Species.PINE : AlpineTrees.Species.SPRUCE));
         case 28 -> var4 < 0.55 ? AlpineTrees.Species.PINE : (var4 < 0.85 ? AlpineTrees.Species.FIR : AlpineTrees.Species.SPRUCE);
         case 29 -> var4 < 0.7 ? AlpineTrees.Species.PINE : AlpineTrees.Species.ASPEN;
         case 30 -> var4 < 0.6 ? AlpineTrees.Species.YOUNG_FIR : AlpineTrees.Species.FIR;
         case 31 -> var4 < 0.45 ? AlpineTrees.Species.YOUNG_SPRUCE : (var4 < 0.8 ? AlpineTrees.Species.SPRUCE : AlpineTrees.Species.SNAG);
         case 32 -> var4 < 0.7 ? AlpineTrees.Species.YOUNG_FIR : AlpineTrees.Species.FIR;
         case 33 -> var4 < 0.86 ? AlpineTrees.Species.ASPEN : AlpineTrees.Species.SPRUCE;
         case 34 -> var4 < 0.75 ? AlpineTrees.Species.PINE : AlpineTrees.Species.YOUNG_FIR;
         case 35 -> var4 < 0.52 ? AlpineTrees.Species.ALDER : (var4 < 0.8 ? AlpineTrees.Species.WILLOW : AlpineTrees.Species.YOUNG_SPRUCE);
         case 36 -> var4 < 0.62 ? AlpineTrees.Species.ROWAN : (var4 < 0.86 ? AlpineTrees.Species.BIRCH : AlpineTrees.Species.YOUNG_FIR);
         case 37 -> var4 < 0.58 ? AlpineTrees.Species.COTTONWOOD : (var4 < 0.82 ? AlpineTrees.Species.ALDER : AlpineTrees.Species.WILLOW);
         case 38 -> var4 < 0.78 ? AlpineTrees.Species.BIRCH : (var4 < 0.92 ? AlpineTrees.Species.ROWAN : AlpineTrees.Species.YOUNG_FIR);
      };
      if (var7
         && (var8 == AlpineTrees.Species.MAPLE || var8 == AlpineTrees.Species.WILLOW || var8 == AlpineTrees.Species.BIRCH)
         && var0.variation(var2, var3, 9329L) < 0.75) {
         var8 = var4 < 0.5 ? AlpineTrees.Species.FIR : AlpineTrees.Species.PINE;
      }

      if (var0.version() >= 15) {
         double var9 = var0.variation(var2, var3, 9341L);
         if (var8 == AlpineTrees.Species.MAPLE && var9 < 0.12) {
            var8 = AlpineTrees.Species.AUTUMN_MAPLE;
         } else if (var8 == AlpineTrees.Species.ASPEN && var9 < 0.14) {
            var8 = AlpineTrees.Species.GOLDEN_ASPEN;
         } else if (var8 == AlpineTrees.Species.SPRUCE && var9 < (var7 ? 0.12 : 0.05)) {
            var8 = AlpineTrees.Species.BLUE_SPRUCE;
         }
      }

      return var8;
   }

   static double core(AlpineLayout var0, int var1, int var2) {
      return AlpineLayout.smooth(0.3, 0.5800000000000001, var0.accent((double)var1, (double)var2));
   }

   static AlpineTrees.Species understory(AlpineLayout var0, AlpineLayout.Sample var1, int var2, int var3) {
      double var4 = var0.variation(var2, var3, 9331L);
      boolean var6 = var1.biome() == 6 || var1.biome() == 7 || var1.biome() == 8 || var1.biome() == 24 || var1.biome() == 25;
      if (var6 && var4 > 0.35) {
         return null;
      } else if (var1.biome() == 27) {
         return var4 < 0.45 ? AlpineTrees.Species.YOUNG_SPRUCE : (var4 < 0.62 ? AlpineTrees.Species.YOUNG_FIR : null);
      } else if (AlpineLayout.soil(var1.biome())) {
         return var4 < 0.3 ? AlpineTrees.Species.YOUNG_FIR : (var4 < 0.48 ? AlpineTrees.Species.YOUNG_SPRUCE : null);
      } else {
         return var4 < 0.58 ? AlpineTrees.Species.YOUNG_SPRUCE : AlpineTrees.Species.YOUNG_FIR;
      }
   }

   static boolean soft(BlockState var0) {
      return var0.isAir() || var0.canBeReplaced() || var0.is(BlockTags.REPLACEABLE_BY_TREES) || var0.is(BlockTags.LEAVES);
   }

   static boolean plant(WorldGenLevel var0, BlockPos var1, AlpineTrees.Species var2, RandomSource var3, float var4) {
      return plant(var0, var1, var2, var3, var4, false);
   }

   static boolean plant(WorldGenLevel var0, BlockPos var1, AlpineTrees.Species var2, RandomSource var3, float var4, boolean var5) {
      if (Terrain.writable(var0, var1) && var0.getBlockState(var1.below()).is(BlockTags.DIRT)) {
         if (soft(var0.getBlockState(var1)) && var0.getFluidState(var1).isEmpty()) {
            boolean var6 = var2 == AlpineTrees.Species.DEADFALL || var2 == AlpineTrees.Species.DEADFALL_PINE;
            if (!var6) {
               int var7 = 0;

               for (Direction var9 : Plane.HORIZONTAL) {
                  if (var0.getBlockState(var1.offset(var9.getStepX(), -1, var9.getStepZ())).isSolid()) {
                     var7++;
                  }
               }

               if (var7 < 2) {
                  return false;
               }

               for (int var22 = 1; var22 <= 4; var22++) {
                  BlockPos var24 = var1.above(var22);
                  if (!Terrain.writable(var0, var24) || !soft(var0.getBlockState(var24))) {
                     return false;
                  }
               }
            }

            AlpineTrees.Growth var21 = AlpineTrees.grow(var2, var3, var4);
            if (var5 && var6) {
               return lay(var0, var1, var21, var3);
            } else {
               MutableBlockPos var23 = new MutableBlockPos();
               LongBidirectionalIterator var25 = var21.logs.keySet().iterator();

               while (var25.hasNext()) {
                  long var30 = (Long)var25.next();
                  var23.set(var1.getX() + BlockPos.getX(var30), var1.getY() + BlockPos.getY(var30), var1.getZ() + BlockPos.getZ(var30));
                  if (!Terrain.writable(var0, var23)) {
                     return false;
                  }

                  BlockState var37 = var0.getBlockState(var23);
                  if (!soft(var37) || !var37.getFluidState().isEmpty()) {
                     return false;
                  }

                  if (var6 && BlockPos.getY(var30) == 0 && !var0.getBlockState(var23.below()).isSolid()) {
                     return false;
                  }
               }

               ObjectBidirectionalIterator var26 = var21.logs.long2ObjectEntrySet().iterator();

               while (var26.hasNext()) {
                  Entry var10 = (Entry)var26.next();
                  long var11 = var10.getLongKey();
                  var23.set(var1.getX() + BlockPos.getX(var11), var1.getY() + BlockPos.getY(var11), var1.getZ() + BlockPos.getZ(var11));
                  var0.setBlock(var23, (BlockState)var10.getValue(), 4);
               }

               LongArrayList var27 = new LongArrayList();
               ObjectBidirectionalIterator var28 = var21.leaves.long2ObjectEntrySet().iterator();

               while (var28.hasNext()) {
                  Entry var31 = (Entry)var28.next();
                  long var12 = var31.getLongKey();
                  var23.set(var1.getX() + BlockPos.getX(var12), var1.getY() + BlockPos.getY(var12), var1.getZ() + BlockPos.getZ(var12));
                  if (Terrain.writable(var0, var23)) {
                     BlockState var14 = var0.getBlockState(var23);
                     if ((var14.isAir() || var14.canBeReplaced() && !var14.is(BlockTags.LEAVES)) && var14.getFluidState().isEmpty()) {
                        var0.setBlock(var23, (BlockState)var31.getValue(), 4);
                        var27.add(var23.asLong());
                     }
                  }
               }

               MutableBlockPos var29 = new MutableBlockPos();

               for (int var32 = 0; var32 < var27.size(); var32++) {
                  long var35 = var27.getLong(var32);
                  var23.set(var35);
                  BlockState var38 = var0.getBlockState(var23);
                  if (var38.hasProperty(WildLeaves.EDGE)) {
                     boolean var15 = false;

                     for (Direction var19 : Direction.values()) {
                        if (var19 != Direction.DOWN) {
                           var29.setWithOffset(var23, var19);
                           if (!Terrain.writable(var0, var29)) {
                              var15 = true;
                              break;
                           }

                           BlockState var20 = var0.getBlockState(var29);
                           if (!var20.is(var38.getBlock()) && !var20.is(BlockTags.LOGS)) {
                              var15 = true;
                              break;
                           }
                        }
                     }

                     if (!var15) {
                        var0.setBlock(var23, (BlockState)var38.setValue(WildLeaves.EDGE, false), 4);
                     }
                  }
               }

               ObjectBidirectionalIterator var33 = var21.cover.long2ObjectEntrySet().iterator();

               while (var33.hasNext()) {
                  Entry var36 = (Entry)var33.next();
                  long var13 = var36.getLongKey();
                  var23.set(var1.getX() + BlockPos.getX(var13), var1.getY() + BlockPos.getY(var13), var1.getZ() + BlockPos.getZ(var13));
                  if (Terrain.writable(var0, var23) && var0.getBlockState(var23).isAir()) {
                     var0.setBlock(var23, (BlockState)var36.getValue(), 4);
                  }
               }

               if (var21.litter) {
                  litter(var0, var1, Math.min(3, Math.max(1, var21.radius - 1)), var3);
               }

               if (var21.broadleaf) {
                  leafFall(var0, var1, Math.min(4, Math.max(2, var21.radius - 1)), var3);
               }

               if (var5) {
                  RandomSource var34 = RandomSource.create(var1.asLong() * 31L + (long)var2.ordinal());
                  if (var6) {
                     scatterSticks(var0, var1, 2 + var34.nextInt(3), 3, var34);
                  } else {
                     stubs(var0, var1, var2, var21, var34);
                  }
               }

               return true;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static boolean lay(WorldGenLevel var0, BlockPos var1, AlpineTrees.Growth var2, RandomSource var3) {
      byte var4 = 0;
      byte var5 = 0;
      LongBidirectionalIterator var6 = var2.logs.keySet().iterator();

      while (var6.hasNext()) {
         long var7 = (Long)var6.next();
         if (BlockPos.getY(var7) == 0 && (BlockPos.getX(var7) > 0 || BlockPos.getZ(var7) > 0)) {
            if (BlockPos.getZ(var7) == 0 && BlockPos.getX(var7) > 0) {
               var4 = 1;
               break;
            }

            if (BlockPos.getX(var7) == 0 && BlockPos.getZ(var7) > 0) {
               var5 = 1;
               break;
            }
         }
      }

      if (var4 == 0 && var5 == 0) {
         return false;
      } else {
         MutableBlockPos var16 = new MutableBlockPos();
         int var17 = 0;
         int var8 = 0;
         int var9 = 0;

         while (true) {
            BlockState var10 = (BlockState)var2.logs.get(BlockPos.asLong(var9 * var4, 0, var9 * var5));
            if (var10 == null) {
               break;
            }

            var16.set(var1.getX() + var9 * var4, var1.getY(), var1.getZ() + var9 * var5);
            if (!Terrain.writable(var0, var16)) {
               break;
            }

            BlockState var11 = var0.getBlockState(var16);
            if (!soft(var11) || var11.is(BlockTags.LEAVES) || !var11.getFluidState().isEmpty()) {
               break;
            }

            boolean var12 = var0.getBlockState(var16.below()).isSolid();
            if (!var12) {
               if (var9 == 0 || ++var8 > 1) {
                  break;
               }
            }

            if (var12) {
               var8 = 0;
            }

            var17 = var9 + 1;
            var9++;
         }

         while (var17 > 0 && !var0.getBlockState(new BlockPos(var1.getX() + (var17 - 1) * var4, var1.getY() - 1, var1.getZ() + (var17 - 1) * var5)).isSolid()) {
            var17--;
         }

         if (var17 < 3) {
            return false;
         } else {
            for (int var18 = 0; var18 < var17; var18++) {
               var16.set(var1.getX() + var18 * var4, var1.getY(), var1.getZ() + var18 * var5);
               var0.setBlock(var16, (BlockState)var2.logs.get(BlockPos.asLong(var18 * var4, 0, var18 * var5)), 4);
               BlockState var21 = (BlockState)var2.cover.get(BlockPos.asLong(var18 * var4, 1, var18 * var5));
               BlockPos var23 = var16.above();
               if (var21 != null && Terrain.writable(var0, var23) && var0.getBlockState(var23).isAir()) {
                  var0.setBlock(var23, var21, 4);
               }
            }

            ObjectBidirectionalIterator var19 = var2.logs.long2ObjectEntrySet().iterator();

            while (var19.hasNext()) {
               Entry var22 = (Entry)var19.next();
               long var24 = var22.getLongKey();
               int var13 = BlockPos.getX(var24);
               int var14 = BlockPos.getZ(var24);
               if (BlockPos.getY(var24) == 0 && (var13 * var5 == 0 || var14 * var4 == 0) && (var13 != 0 && var14 != 0 || (var4 == 1 ? var14 != 0 : var13 != 0))
                  )
                {
                  int var15 = var4 == 1 ? var13 : var14;
                  if (var15 >= 0 && var15 < var17) {
                     var16.set(var1.getX() + var13, var1.getY(), var1.getZ() + var14);
                     if (Terrain.writable(var0, var16)
                        && soft(var0.getBlockState(var16))
                        && !var0.getBlockState(var16).is(BlockTags.LEAVES)
                        && var0.getBlockState(var16.below()).isSolid()) {
                        var0.setBlock(var16, (BlockState)var22.getValue(), 4);
                     }
                  }
               }
            }

            RandomSource var20 = RandomSource.create(var1.asLong() * 31L + 7L);
            scatterSticks(var0, var1.offset(var4 * var17 / 2, 0, var5 * var17 / 2), 2 + var20.nextInt(3), 3, var20);
            return true;
         }
      }
   }

   private static void stubs(WorldGenLevel var0, BlockPos var1, AlpineTrees.Species var2, AlpineTrees.Growth var3, RandomSource var4) {
      if (var2 != AlpineTrees.Species.YOUNG_SPRUCE && var2 != AlpineTrees.Species.YOUNG_FIR) {
         Block var5 = AlpineRegistration.prop("branch_stub");
         if (var5 != Blocks.AIR) {
            byte var6 = switch (var2) {
               case PINE, LARCH -> 1;
               case ASPEN, BIRCH, GOLDEN_ASPEN, ALDER, ROWAN -> 2;
               case MAPLE, AUTUMN_MAPLE, COTTONWOOD, WILLOW -> 3;
               default -> 0;
            };
            double var7 = var2 == AlpineTrees.Species.SNAG ? 0.3 : (var6 <= 1 ? 0.22 : 0.08);
            int var9 = Integer.MAX_VALUE;
            LongBidirectionalIterator var10 = var3.leaves.keySet().iterator();

            while (var10.hasNext()) {
               long var11 = (Long)var10.next();
               if (Math.abs(BlockPos.getX(var11)) <= 1 && Math.abs(BlockPos.getZ(var11)) <= 1) {
                  var9 = Math.min(var9, BlockPos.getY(var11));
               }
            }

            MutableBlockPos var18 = new MutableBlockPos();
            int var19 = 2;

            while (true) {
               BlockState var12 = (BlockState)var3.logs.get(BlockPos.asLong(0, var19, 0));
               if (var12 == null || !var12.hasProperty(RotatedPillarBlock.AXIS) || var12.getValue(RotatedPillarBlock.AXIS) != Axis.Y) {
                  return;
               }

               double var13 = var19 < var9 - 1 ? var7 : var7 * 0.2;

               for (Direction var16 : Plane.HORIZONTAL) {
                  if (!((double)var4.nextFloat() >= var13)) {
                     var18.set(var1.getX() + var16.getStepX(), var1.getY() + var19, var1.getZ() + var16.getStepZ());
                     if (Terrain.writable(var0, var18) && var0.getBlockState(var18).isAir()) {
                        int var17 = var2 == AlpineTrees.Species.SNAG ? var4.nextInt(2) : (var19 < var9 - 3 ? var4.nextInt(3) : (var4.nextInt(2) == 0 ? 0 : 2));
                        var0.setBlock(
                           var18,
                           (BlockState)((BlockState)((BlockState)var5.defaultBlockState().setValue(AlpineBranchStub.FACING, var16))
                                 .setValue(AlpineBranchStub.WOOD, Integer.valueOf(var6)))
                              .setValue(AlpineBranchStub.VARIANT, var17),
                           4
                        );
                     }
                  }
               }

               var19++;
            }
         }
      }
   }

   static void scatterSticks(WorldGenLevel var0, BlockPos var1, int var2, int var3, RandomSource var4) {
      Block var5 = AlpineRegistration.prop("forest_sticks");
      if (var5 != Blocks.AIR) {
         MutableBlockPos var6 = new MutableBlockPos();

         for (int var7 = 0; var7 < var2; var7++) {
            int var8 = var4.nextInt(var3 * 2 + 1) - var3;
            int var9 = var4.nextInt(var3 * 2 + 1) - var3;

            for (int var10 = 2; var10 >= -3; var10--) {
               var6.set(var1.getX() + var8, var1.getY() + var10, var1.getZ() + var9);
               if (!Terrain.writable(var0, var6)) {
                  break;
               }

               BlockState var11 = var0.getBlockState(var6);
               if (!var11.isAir()) {
                  if (var10 < 2) {
                     break;
                  }
               } else {
                  BlockState var12 = var0.getBlockState(var6.below());
                  if (!var12.isAir()) {
                     if (var12.is(BlockTags.DIRT) && var0.getFluidState(var6).isEmpty()) {
                        var0.setBlock(
                           var6,
                           (BlockState)((BlockState)var5.defaultBlockState().setValue(AlpineSticks.VARIANT, var4.nextInt(4)))
                              .setValue(AlpineSticks.FACING, Plane.HORIZONTAL.getRandomDirection(var4)),
                           4
                        );
                     }
                     break;
                  }
               }
            }
         }
      }
   }

   private static void litter(WorldGenLevel var0, BlockPos var1, int var2, RandomSource var3) {
      MutableBlockPos var4 = new MutableBlockPos();

      for (int var5 = -var2; var5 <= var2; var5++) {
         for (int var6 = -var2; var6 <= var2; var6++) {
            if (var5 * var5 + var6 * var6 <= var2 * var2 + 1 && !(var3.nextFloat() < 0.35F)) {
               for (int var7 = 1; var7 >= -3; var7--) {
                  var4.set(var1.getX() + var5, var1.getY() - 1 + var7, var1.getZ() + var6);
                  if (!Terrain.writable(var0, var4)) {
                     break;
                  }

                  BlockState var8 = var0.getBlockState(var4);
                  if (var8.is(Blocks.GRASS_BLOCK) && var0.getBlockState(var4.above()).getFluidState().isEmpty()) {
                     var0.setBlock(var4, (var3.nextFloat() < 0.8F ? Blocks.PODZOL : Blocks.COARSE_DIRT).defaultBlockState(), 4);
                     break;
                  }

                  if (var8.isSolid()) {
                     break;
                  }
               }
            }
         }
      }
   }

   private static void leafFall(WorldGenLevel var0, BlockPos var1, int var2, RandomSource var3) {
      Block var4 = AlpineRegistration.prop("forest_litter");
      if (var4 != Blocks.AIR) {
         Property var5 = var4.getStateDefinition().getProperty("variant");
         MutableBlockPos var6 = new MutableBlockPos();

         for (int var7 = -var2; var7 <= var2; var7++) {
            for (int var8 = -var2; var8 <= var2; var8++) {
               if (var7 * var7 + var8 * var8 <= var2 * var2 + 1 && !(var3.nextFloat() < 0.62F)) {
                  for (int var9 = 2; var9 >= -3; var9--) {
                     var6.set(var1.getX() + var7, var1.getY() + var9, var1.getZ() + var8);
                     if (!Terrain.writable(var0, var6)) {
                        break;
                     }

                     BlockState var10 = var0.getBlockState(var6.below());
                     if (var0.getBlockState(var6).isAir() && var10.is(BlockTags.DIRT) && var0.getFluidState(var6).isEmpty()) {
                        BlockState var11 = var4.defaultBlockState();
                        if (var5 instanceof IntegerProperty var12) {
                           var11 = (BlockState)var11.setValue(
                              var12, var12.getPossibleValues().stream().skip((long)var3.nextInt(var12.getPossibleValues().size())).findFirst().orElse(0)
                           );
                        }

                        var0.setBlock(var6, var11, 4);
                        break;
                     }

                     if (!var0.getBlockState(var6).isAir()) {
                        break;
                     }
                  }
               }
            }
         }
      }
   }

   private AlpineForest() {
   }
}
