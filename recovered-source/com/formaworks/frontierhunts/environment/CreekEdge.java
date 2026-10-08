package com.formaworks.frontierhunts.environment;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.expedition.ExpeditionContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.Fluids;

public final class CreekEdge extends Feature<NoneFeatureConfiguration> {
   public CreekEdge() {
      super(NoneFeatureConfiguration.CODEC);
   }

   public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> var1) {
      WorldGenLevel var2 = var1.level();
      RandomSource var3 = var1.random();
      if (!(Boolean)HuntConfig.GENERATE_HUNTING_FORESTS.get()) {
         return false;
      } else {
         BlockPos var4 = findShore(var2, var1.origin(), var3);
         if (var4 == null) {
            return false;
         } else {
            boolean var5 = false;
            int var6 = 5 + var3.nextInt(4);
            CreekEdge.WaterMap var7 = new CreekEdge.WaterMap(var2, var4, var6 + 3);
            BlockState var8 = ((ForestFloor.Litter)ExpeditionContent.FOREST_LITTER.get()).defaultBlockState();
            BlockState var9 = ((ForestFloor.Undergrowth)ExpeditionContent.UNDERGROWTH.get()).defaultBlockState();

            for (int var10 = 0; var10 < var6 * var6 * 2; var10++) {
               int var11 = var3.nextInt(var6 * 2 + 1) - var6;
               int var12 = var3.nextInt(var6 * 2 + 1) - var6;
               BlockPos var13 = Terrain.surface(var2, var4.offset(var11, 0, var12));
               if (var13 != null) {
                  BlockState var14 = var2.getBlockState(var13.below());
                  int var15 = var7.distance(var13, 3);
                  if (var15 != 0) {
                     BlockState var16 = var2.getBlockState(var13);
                     if (!var14.getFluidState().is(Fluids.WATER) && !var16.getFluidState().is(Fluids.WATER)) {
                        if (passable(var16) && var14.is(BlockTags.DIRT)) {
                           float var17 = var3.nextFloat();
                           if (var15 <= 1) {
                              if (var17 < 0.34F) {
                                 set(var2, var13, Blocks.AIR.defaultBlockState());
                                 set(var2, var13.below(), Blocks.GRAVEL.defaultBlockState());
                                 var5 = true;
                              } else if (var17 < 0.48F) {
                                 set(var2, var13.below(), Blocks.COARSE_DIRT.defaultBlockState());
                                 var5 = true;
                              } else if (var17 < 0.62F) {
                                 set(var2, var13, Blocks.ROOTED_DIRT.defaultBlockState());
                                 var5 = true;
                              } else if (var17 < 0.86F) {
                                 set(var2, var13, (BlockState)var9.setValue(ForestFloor.Undergrowth.VARIANT, var3.nextInt(2)));
                                 var5 = true;
                              }
                           } else {
                              if (var17 < 0.3F) {
                                 set(var2, var13.below(), Blocks.MOSS_BLOCK.defaultBlockState());
                                 var5 = true;
                              }

                              if (var17 < 0.66F) {
                                 set(var2, var13, (BlockState)var9.setValue(ForestFloor.Undergrowth.VARIANT, var3.nextInt(5)));
                                 var5 = true;
                              } else if (var17 < 0.92F) {
                                 set(var2, var13, (BlockState)var8.setValue(ForestFloor.Litter.VARIANT, var3.nextInt(3)));
                                 var5 = true;
                              }
                           }
                        }
                     } else if (var3.nextFloat() < 0.16F) {
                        set(var2, var13.below(), Blocks.COBBLESTONE.defaultBlockState());
                        var5 = true;
                     }
                  }
               }
            }

            for (int var18 = 0; var18 < 3 + var3.nextInt(4); var18++) {
               BlockPos var21 = Terrain.surface(var2, var4.offset(var3.nextInt(11) - 5, 0, var3.nextInt(11) - 5));
               if (var21 != null) {
                  BlockPos var23 = var21.below();
                  if (var7.distance(var23, 2) != 0 && var2.getBlockState(var23).is(BlockTags.DIRT)) {
                     set(var2, var23, Blocks.ROOTED_DIRT.defaultBlockState());
                     BlockPos var24 = var23.below();
                     if (ForestStand.writable(var2, var24) && passable(var2.getBlockState(var24))) {
                        set(var2, var24, Blocks.HANGING_ROOTS.defaultBlockState());
                     }

                     var5 = true;
                  }
               }
            }

            if (var3.nextFloat() < 0.38F) {
               BlockPos var19 = Terrain.soil(var2, var4);
               if (var19 != null && ForestStand.plant(var2, var19, WildTrees.Kind.LEANER, var3, 0.7F)) {
                  var5 = true;
               }
            }

            for (int var20 = 0; var20 < var3.nextInt(3); var20++) {
               BlockPos var22 = Terrain.surface(var2, var4.offset(var3.nextInt(9) - 4, 0, var3.nextInt(9) - 4));
               if (var22 != null && passable(var2.getBlockState(var22))) {
                  set(var2, var22, ((ForestFloor.Boulder)ExpeditionContent.MOSSY_STONE.get()).defaultBlockState());
                  var5 = true;
               }
            }

            return var5;
         }
      }
   }

   private static BlockPos findShore(WorldGenLevel var0, BlockPos var1, RandomSource var2) {
      BlockPos var3 = null;

      for (byte var4 = -9; var4 <= 9 && var3 == null; var4 += 3) {
         for (byte var5 = -9; var5 <= 9 && var3 == null; var5 += 3) {
            BlockPos var6 = var1.offset(var4, 0, var5);
            if (Terrain.writable(var0, var6)) {
               BlockPos var7 = var0.getHeightmapPos(Types.OCEAN_FLOOR_WG, var6);
               if (Terrain.isWater(var0, var7) || Terrain.isWater(var0, var7.above())) {
                  var3 = var7;
               }
            }
         }
      }

      if (var3 == null) {
         return null;
      } else {
         for (int var8 = 0; var8 < 8; var8++) {
            BlockPos var9 = Terrain.surface(var0, var3.offset(var2.nextInt(9) - 4, 0, var2.nextInt(9) - 4));
            if (var9 != null && !Terrain.isWater(var0, var9) && !Terrain.isWater(var0, var9.below())) {
               return var9;
            }
         }

         return Terrain.surface(var0, var3);
      }
   }

   private static boolean passable(BlockState var0) {
      return Terrain.open(var0);
   }

   private static void set(WorldGenLevel var0, BlockPos var1, BlockState var2) {
      Terrain.set(var0, var1, var2);
   }

   private static final class WaterMap {
      private final BlockPos centre;
      private final int reach;
      private final boolean[] wet;

      WaterMap(WorldGenLevel var1, BlockPos var2, int var3) {
         this.centre = var2;
         this.reach = var3;
         int var4 = var3 * 2 + 1;
         this.wet = new boolean[var4 * var4];
         MutableBlockPos var5 = new MutableBlockPos();

         for (int var6 = -var3; var6 <= var3; var6++) {
            for (int var7 = -var3; var7 <= var3; var7++) {
               BlockPos var8 = var2.offset(var6, 0, var7);
               if (Terrain.writable(var1, var8)) {
                  BlockPos var9 = var1.getHeightmapPos(Types.OCEAN_FLOOR_WG, var8);

                  for (int var10 = 0; var10 <= 4; var10++) {
                     var5.set(var9.getX(), var9.getY() + var10, var9.getZ());
                     if (!var1.hasChunkAt(var5)) {
                        break;
                     }

                     if (var1.getBlockState(var5).getFluidState().is(Fluids.WATER)) {
                        this.wet[(var6 + var3) * var4 + var7 + var3] = true;
                        break;
                     }
                  }
               }
            }
         }
      }

      boolean at(int var1, int var2) {
         return Math.abs(var1) <= this.reach && Math.abs(var2) <= this.reach ? this.wet[(var1 + this.reach) * (this.reach * 2 + 1) + var2 + this.reach] : false;
      }

      int distance(BlockPos var1, int var2) {
         int var3 = var1.getX() - this.centre.getX();
         int var4 = var1.getZ() - this.centre.getZ();

         for (int var5 = 1; var5 <= var2; var5++) {
            for (int var6 = -var5; var6 <= var5; var6++) {
               for (int var7 = -var5; var7 <= var5; var7++) {
                  if (Math.max(Math.abs(var6), Math.abs(var7)) == var5 && this.at(var3 + var6, var4 + var7)) {
                     return var5;
                  }
               }
            }
         }

         return 0;
      }
   }
}
