package com.formaworks.frontierhunts.landscape.tent;

import com.formaworks.frontierhunts.landscape.AlpineTurf;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class TentPitch {
   private TentPitch() {
   }

   public static TentPitch.Plan plan(ServerLevel var0, Collection<BlockPos> var1, Collection<BlockPos> var2, int var3) {
      TentPitch.Plan var4 = null;
      double var5 = Double.MAX_VALUE;

      for (int var10 : new int[]{0, -1, 1}) {
         int var11 = var3 + var10;
         ArrayList var12 = new ArrayList();
         double var13 = level(var0, var1, var11, var12);
         if (!(var13 < 0.0) && clear(var0, var2, var11, var12)) {
            var13 += (double)Math.abs(var10) * 0.05;
            if (var13 < var5 - 1.0E-9) {
               var5 = var13;
               var4 = new TentPitch.Plan(var11, var12);
            }
         }
      }

      return var4;
   }

   public static boolean occupied(ServerLevel var0, TentPitch.Plan var1) {
      for (TentPitch.Change var3 : var1.changes()) {
         if (!var3.state().isAir() && !var0.getEntitiesOfClass(Entity.class, new AABB(var3.pos())).isEmpty()) {
            return true;
         }
      }

      return false;
   }

   public static void apply(ServerLevel var0, TentPitch.Plan var1) {
      for (TentPitch.Change var3 : var1.changes()) {
         var0.setBlock(var3.pos(), var3.state(), 3);
      }
   }

   private static double level(ServerLevel var0, Collection<BlockPos> var1, int var2, List<TentPitch.Change> var3) {
      double var4 = 0.0;

      for (BlockPos var7 : var1) {
         int var8 = var7.getX();
         int var9 = var7.getZ();
         BlockPos var10 = new BlockPos(var8, var2, var9);
         BlockPos var11 = var10.below();
         BlockPos var12 = var11.below();
         BlockState var13 = var0.getBlockState(var10);
         BlockState var14 = var0.getBlockState(var11);
         if (!var0.getFluidState(var10).isEmpty()) {
            return -1.0;
         }

         if (!cover(var13)) {
            if (!soil(var13)) {
               return -1.0;
            }

            double var18 = top(var0, var10, var13);
            if (!sturdy(var0, var11, var14)) {
               return -1.0;
            }

            var3.add(new TentPitch.Change(var10, Blocks.AIR.defaultBlockState()));
            if (var14.is(Blocks.DIRT) && (var13.is(Blocks.GRASS_BLOCK) || var13.getBlock() instanceof AlpineTurf)) {
               var3.add(new TentPitch.Change(var11, Blocks.GRASS_BLOCK.defaultBlockState()));
            }

            var4 += var18;
         } else if (!sturdy(var0, var11, var14)) {
            if (!cover(var14)) {
               BlockState var15 = whole(var14);
               if (var15 == null) {
                  return -1.0;
               }

               var3.add(new TentPitch.Change(var11, var15));
               var4 += 1.0 - top(var0, var11, var14);
            } else {
               BlockState var17 = var0.getBlockState(var12);
               if (!var0.getFluidState(var11).isEmpty()) {
                  return -1.0;
               }

               if (!sturdy(var0, var12, var17)) {
                  BlockState var16 = cover(var17) ? null : whole(var17);
                  if (var16 == null) {
                     return -1.0;
                  }

                  var3.add(new TentPitch.Change(var12, var16));
                  var4 += 1.0 - top(var0, var12, var17);
               }

               BlockState var19 = fillFor(var17);
               if (var19 == null) {
                  return -1.0;
               }

               var3.add(new TentPitch.Change(var11, var19));
               if (var17.is(Blocks.GRASS_BLOCK)) {
                  var3.add(new TentPitch.Change(var12, Blocks.DIRT.defaultBlockState()));
               }

               var4++;
            }
         }
      }

      return var4;
   }

   private static boolean clear(ServerLevel var0, Collection<BlockPos> var1, int var2, List<TentPitch.Change> var3) {
      LinkedHashMap var4 = new LinkedHashMap();

      for (TentPitch.Change var6 : var3) {
         var4.put(var6.pos(), var6.state());
      }

      for (BlockPos var10 : var1) {
         BlockPos var7 = new BlockPos(var10.getX(), var2 + var10.getY(), var10.getZ());
         BlockState var8 = var4.getOrDefault(var7, var0.getBlockState(var7));
         if (!var8.canBeReplaced() && !var8.isAir()) {
            return false;
         }

         if (!var4.containsKey(var7) && !var0.getFluidState(var7).isEmpty()) {
            return false;
         }
      }

      return true;
   }

   static boolean cover(BlockState var0) {
      return var0.isAir() || var0.canBeReplaced();
   }

   static boolean soil(BlockState var0) {
      Block var1 = var0.getBlock();
      return var1 instanceof AlpineTurf
         || var1 instanceof SnowLayerBlock
         || var0.is(Blocks.SNOW_BLOCK)
         || var0.is(Blocks.POWDER_SNOW)
         || var0.is(BlockTags.DIRT)
         || var0.is(Blocks.DIRT_PATH)
         || var0.is(Blocks.FARMLAND)
         || var0.is(Blocks.SAND)
         || var0.is(Blocks.RED_SAND)
         || var0.is(Blocks.GRAVEL)
         || var0.is(Blocks.CLAY)
         || var0.is(Blocks.MUD)
         || var0.is(Blocks.MOSS_BLOCK)
         || var0.is(Blocks.MOSS_CARPET)
         || var0.is(Blocks.PACKED_MUD)
         || var0.is(Blocks.SOUL_SOIL)
         || BuiltInRegistries.BLOCK.getKey(var1).getPath().equals("forest_duff");
   }

   private static boolean sturdy(ServerLevel var0, BlockPos var1, BlockState var2) {
      return var2.isFaceSturdy(var0, var1, Direction.UP) && topOf(var2.getBlockSupportShape(var0, var1)) >= 0.999;
   }

   private static double top(ServerLevel var0, BlockPos var1, BlockState var2) {
      double var3 = topOf(var2.getBlockSupportShape(var0, var1));
      if (var3 <= 0.0) {
         var3 = topOf(var2.getCollisionShape(var0, var1));
      }

      return Math.max(0.0, Math.min(1.0, var3));
   }

   private static double topOf(VoxelShape var0) {
      return var0.isEmpty() ? 0.0 : var0.max(Axis.Y);
   }

   private static BlockState whole(BlockState var0) {
      Block var1 = var0.getBlock();
      if (var1 instanceof AlpineTurf) {
         return Blocks.GRASS_BLOCK.defaultBlockState();
      } else if (var1 instanceof SnowLayerBlock) {
         return Blocks.SNOW_BLOCK.defaultBlockState();
      } else if (var0.is(Blocks.DIRT_PATH) || var0.is(Blocks.FARMLAND)) {
         return Blocks.DIRT.defaultBlockState();
      } else if (var0.is(Blocks.MOSS_CARPET)) {
         return Blocks.MOSS_BLOCK.defaultBlockState();
      } else if (var0.is(Blocks.SOUL_SAND)) {
         return Blocks.SOUL_SOIL.defaultBlockState();
      } else {
         return var0.is(Blocks.POWDER_SNOW) ? Blocks.SNOW_BLOCK.defaultBlockState() : null;
      }
   }

   private static BlockState fillFor(BlockState var0) {
      if (var0.is(Blocks.GRASS_BLOCK) || var0.getBlock() instanceof AlpineTurf || var0.is(Blocks.PODZOL) || var0.is(Blocks.MYCELIUM)) {
         return Blocks.GRASS_BLOCK.defaultBlockState();
      } else if (var0.is(Blocks.SNOW_BLOCK) || var0.getBlock() instanceof SnowLayerBlock || var0.is(Blocks.POWDER_SNOW)) {
         return Blocks.SNOW_BLOCK.defaultBlockState();
      } else if (var0.is(Blocks.SAND)) {
         return Blocks.SAND.defaultBlockState();
      } else if (var0.is(Blocks.RED_SAND)) {
         return Blocks.RED_SAND.defaultBlockState();
      } else if (var0.is(Blocks.GRAVEL)) {
         return Blocks.GRAVEL.defaultBlockState();
      } else if (var0.is(Blocks.MUD)) {
         return Blocks.MUD.defaultBlockState();
      } else if (soil(var0)) {
         return Blocks.DIRT.defaultBlockState();
      } else {
         return !var0.is(BlockTags.BASE_STONE_OVERWORLD) && !var0.is(Blocks.COBBLESTONE) && !var0.is(Blocks.MOSSY_COBBLESTONE)
            ? null
            : Blocks.GRAVEL.defaultBlockState();
      }
   }

   public static record Change(BlockPos pos, BlockState state) {
   }

   public static record Plan(int floorY, List<TentPitch.Change> changes) {
   }
}
