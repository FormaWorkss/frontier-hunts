package com.formaworks.frontierhunts.environment;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.material.Fluids;

public final class Terrain {
   private static final int DESCENT = 48;

   public static boolean grown(BlockState state) {
      return state.is(BlockTags.LEAVES)
         || state.is(BlockTags.LOGS)
         || state.is(BlockTags.REPLACEABLE_BY_TREES)
         || state.getBlock() instanceof ForestFloor
         || state.getBlock() instanceof DeadfallLog
         || state.is(Blocks.SNOW)
         || state.is(BlockTags.FLOWERS)
         || state.is(BlockTags.SAPLINGS)
         || state.is(Blocks.VINE)
         || state.is(Blocks.MOSS_CARPET)
         || state.is(Blocks.HANGING_ROOTS);
   }

   public static BlockPos surface(WorldGenLevel level, BlockPos near) {
      if (!writable(level, near)) {
         return null;
      } else {
         BlockPos at = level.getHeightmapPos(level instanceof WorldGenRegion ? Types.WORLD_SURFACE_WG : Types.WORLD_SURFACE, near);
         int floor = level.getMinBuildHeight() + 2;

         for (int i = 0; i < 48 && at.getY() > floor; i++) {
            BlockState here = level.getBlockState(at);
            BlockState below = level.getBlockState(at.below());
            if ((here.isAir() || grown(here)) && below.isSolid() && !grown(below) && below.getFluidState().isEmpty()) {
               return at;
            }

            at = at.below();
         }

         return null;
      }
   }

   public static BlockPos soil(WorldGenLevel level, BlockPos near) {
      BlockPos at = surface(level, near);
      return at != null && level.getBlockState(at.below()).is(BlockTags.DIRT) ? at : null;
   }

   public static boolean writable(WorldGenLevel level, BlockPos at) {
      if (!level.isOutsideBuildHeight(at) && level.getLevel().getWorldBorder().isWithinBounds(at)) {
         if (level instanceof WorldGenRegion region) {
            ChunkPos c = region.getCenter();
            if (Math.abs((at.getX() >> 4) - c.x) > 1 || Math.abs((at.getZ() >> 4) - c.z) > 1) {
               return false;
            }
         }

         return level.hasChunkAt(at) && level.ensureCanWrite(at);
      } else {
         return false;
      }
   }

   public static boolean open(BlockState state) {
      return state.isAir() || grown(state);
   }

   public static boolean isWater(WorldGenLevel level, BlockPos at) {
      return level.getBlockState(at).getFluidState().is(Fluids.WATER);
   }

   public static void set(WorldGenLevel level, BlockPos at, BlockState state) {
      if (writable(level, at)) {
         level.setBlock(at, state, 4);
      }
   }

   private Terrain() {
   }
}
