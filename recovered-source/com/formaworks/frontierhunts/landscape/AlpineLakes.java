package com.formaworks.frontierhunts.landscape;

import com.formaworks.frontierhunts.environment.Terrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

final class AlpineLakes {
   static boolean water(int var0) {
      return (var0 >>> 28 & 1) != 0;
   }

   static boolean shore(int var0) {
      return (var0 >>> 29 & 1) != 0;
   }

   static boolean dam(int var0) {
      return (var0 >>> 30 & 1) != 0;
   }

   static boolean pond(int var0) {
      return (var0 >>> 31 & 1) != 0;
   }

   static double erosion(int var0) {
      return (double)(var0 >> 21 & 31) / 15.5 - 1.0;
   }

   static boolean dress(WorldGenLevel var0, AlpineLayout var1, AlpineLayout.Sample var2, int var3, int var4, MutableBlockPos var5) {
      int var6 = var2.fallStyle();
      if (water(var6) && var2.wet()) {
         return lake(var0, var1, var2, var3, var4, var5, pond(var6), shore(var6));
      } else if (dam(var6)) {
         return beaverDam(var0, var1, var2, var3, var4, var5);
      } else if (shore(var6)) {
         return margin(var0, var1, var2, var3, var4, var5, pond(var6));
      } else {
         return (var6 & 7) != 0 && (var6 >> 3 & 63) > 8 ? chute(var0, var1, var2, var3, var4, var5, var6) : false;
      }
   }

   private static boolean lake(
      WorldGenLevel var0, AlpineLayout var1, AlpineLayout.Sample var2, int var3, int var4, MutableBlockPos var5, boolean var6, boolean var7
   ) {
      int var8 = var2.water() - var2.floor();
      double var9 = var1.variation(var3, var4, 7801L);
      double var11 = var1.noise((double)var3 / 9.0, (double)var4 / 9.0, 7803L);
      var5.set(var3, var2.floor() + 1, var4);
      if (!var0.getBlockState(var5).is(Blocks.WATER)) {
         return true;
      } else if (var6) {
         if (var8 <= 2 && var11 > -0.15 && var9 < 0.62) {
            Terrain.set(
               var0,
               var5,
               (BlockState)((BlockState)AlpineRegistration.prop("reeds").defaultBlockState().setValue(AlpineReeds.SIZE, var9 < 0.25 ? 2 : (var9 < 0.5 ? 1 : 0)))
                  .setValue(AlpineReeds.WATERLOGGED, true)
            );
            return true;
         } else if (var8 >= 2 && var8 <= 4 && !var7 && var11 < 0.1 && var9 < 0.1) {
            var5.setY(var2.water() + 1);
            if (var0.isEmptyBlock(var5)) {
               Terrain.set(var0, var5, Blocks.LILY_PAD.defaultBlockState());
            }

            return true;
         } else {
            if (var9 > 0.9965) {
               AlpineForest.plant(
                  var0, new BlockPos(var3, var2.floor() + 1, var4), AlpineTrees.Species.SNAG, RandomSource.create(var1.hash(var3, var4, 7805L)), 0.55F
               );
            }

            return true;
         }
      } else {
         if (var8 <= 1 && var7 && var9 < 0.1) {
            Terrain.set(
               var0,
               var5,
               (BlockState)((BlockState)AlpineRegistration.prop("reeds").defaultBlockState().setValue(AlpineReeds.SIZE, 0))
                  .setValue(AlpineReeds.WATERLOGGED, true)
            );
         } else if (var9 > 0.93 && var8 <= 4) {
            Terrain.set(
               var0,
               var5,
               (BlockState)((BlockState)AlpineRegistration.prop(var9 > 0.975 ? "mossy_river_stone" : "river_pebbles")
                     .defaultBlockState()
                     .setValue(AlpineBoulder.WATERLOGGED, true))
                  .setValue(AlpineBoulder.FORM, (int)(var1.hash(var3, var4, 7807L) & 3L))
            );
         }

         return true;
      }
   }

   private static boolean margin(WorldGenLevel var0, AlpineLayout var1, AlpineLayout.Sample var2, int var3, int var4, MutableBlockPos var5, boolean var6) {
      var5.set(var3, var2.floor() + 1, var4);
      if (!var0.isEmptyBlock(var5)) {
         return true;
      } else {
         BlockState var7 = var0.getBlockState(var5.below());
         if (!var7.is(BlockTags.DIRT) && !var7.is(Blocks.MOSS_BLOCK)) {
            return false;
         } else {
            double var8 = var1.variation(var3, var4, 7811L);
            double var10 = var1.noise((double)var3 / 8.0, (double)var4 / 8.0, 7813L);
            if (var6) {
               if (var10 > 0.05 && var8 < 0.55) {
                  Terrain.set(var0, var5, (BlockState)AlpineRegistration.prop("reeds").defaultBlockState().setValue(AlpineReeds.SIZE, var8 < 0.3 ? 2 : 1));
                  return true;
               } else if (var8 < 0.7) {
                  Terrain.set(
                     var0, var5, (BlockState)AlpineRegistration.prop("river_brush").defaultBlockState().setValue(AlpineThicket.SIZE, var8 < 0.62 ? 1 : 2)
                  );
                  return true;
               } else {
                  return false;
               }
            } else if (var10 > 0.1 && var8 < 0.4) {
               Terrain.set(var0, var5, (BlockState)AlpineRegistration.prop("cottongrass").defaultBlockState().setValue(AlpineThicket.SIZE, var8 < 0.2 ? 1 : 0));
               return true;
            } else {
               return false;
            }
         }
      }
   }

   private static boolean beaverDam(WorldGenLevel var0, AlpineLayout var1, AlpineLayout.Sample var2, int var3, int var4, MutableBlockPos var5) {
      double var6 = var1.variation(var3, var4, 7821L);
      var5.set(var3, var2.floor(), var4);
      Terrain.set(var0, var5, (var6 < 0.45 ? Blocks.MUD : Blocks.PACKED_MUD).defaultBlockState());
      var5.setY(var2.floor() + 1);
      if (!var0.isEmptyBlock(var5)) {
         return true;
      } else {
         if (var6 < 0.72) {
            Axis var8 = var1.variation(var3, var4, 7823L) < 0.5 ? Axis.X : Axis.Z;
            Terrain.set(var0, var5, (BlockState)Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, var8));
            if (var6 < 0.22) {
               var5.setY(var2.floor() + 2);
               if (var0.isEmptyBlock(var5)) {
                  Terrain.set(var0, var5, branch(var1, var3, var4));
               }
            }
         } else {
            Terrain.set(var0, var5, branch(var1, var3, var4));
         }

         return true;
      }
   }

   private static BlockState branch(AlpineLayout var0, int var1, int var2) {
      BlockState var3 = AlpineRegistration.prop("fallen_branch").defaultBlockState();
      DirectionProperty var4 = BlockStateProperties.HORIZONTAL_FACING;
      return var3.hasProperty(var4) ? (BlockState)var3.setValue(var4, Direction.from2DDataValue((int)(var0.hash(var1, var2, 7825L) & 3L))) : var3;
   }

   private static boolean chute(WorldGenLevel var0, AlpineLayout var1, AlpineLayout.Sample var2, int var3, int var4, MutableBlockPos var5, int var6) {
      double var7 = erosion(var6);
      double var9 = (double)(var6 >> 15 & 63) / 63.0;
      if (!(var7 > -0.3) && !(var9 < 0.12) && !(var9 > 0.8) && !(var2.ground() > var2.snowLine() - 40.0)) {
         var5.set(var3, var2.floor() + 1, var4);
         if (!var0.isEmptyBlock(var5)) {
            return false;
         } else {
            BlockState var11 = var0.getBlockState(var5.below());
            if (!var11.is(BlockTags.DIRT) && !var11.is(Blocks.MOSS_BLOCK)) {
               return false;
            } else {
               double var12 = var1.variation(var3, var4, 7831L);
               double var14 = var1.noise((double)var3 / 7.0, (double)var4 / 7.0, 7833L);
               if (var12 < 0.01) {
                  AlpineForest.plant(
                     var0,
                     new BlockPos(var3, var2.floor() + 1, var4),
                     AlpineTrees.Species.DEADFALL,
                     RandomSource.create(var1.hash(var3, var4, 7835L)),
                     (float)var1.variation(var3, var4, 7837L)
                  );
                  return true;
               } else if (var14 > 0.0 && var12 < 0.3) {
                  String var16 = var12 < 0.12 ? "river_brush" : (var12 < 0.2 ? "huckleberry_shrub" : (var12 < 0.26 ? "juniper_shrub" : "broadleaf_thicket"));
                  Terrain.set(var0, var5, (BlockState)AlpineRegistration.prop(var16).defaultBlockState().setValue(AlpineThicket.SIZE, var12 < 0.18 ? 2 : 1));
                  return true;
               } else {
                  return false;
               }
            }
         }
      } else {
         return false;
      }
   }

   private AlpineLakes() {
   }
}
