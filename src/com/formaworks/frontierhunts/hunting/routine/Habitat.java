package com.formaworks.frontierhunts.hunting.routine;

import com.formaworks.frontierhunts.hunting.GameSpecies;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Reads the ground for home-range anchors: dense bedding cover, open feeding ground, browse and water. Only ever
 * touches loaded chunks and a few dozen blocks per candidate; callers budget how many candidates run per tick.
 */
final class Habitat {
   static final int NONE = 0;
   static final int COVER = 1;
   static final int FORAGE = 2;
   static final int CROP = 3;
   static final int REEDS = 4;
   private static final Map<Block, Integer> KINDS = new IdentityHashMap<>();
   static final float REJECT = -1000.0F;
   private static final int[][] RING1 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}};
   private static final int[][] DIRS8 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

   private Habitat() {
   }

   static int kind(BlockState state) {
      Block b = state.getBlock();
      Integer k = KINDS.get(b);
      if (k == null) {
         k = classify(state);
         KINDS.put(b, k);
      }

      return k;
   }

   private static int classify(BlockState s) {
      if (s.isAir()) {
         return NONE;
      } else if (s.is(BlockTags.CROPS) || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.PUMPKIN) || s.is(Blocks.MELON)) {
         return CROP;
      } else if (s.is(Blocks.TALL_GRASS) || s.is(Blocks.LARGE_FERN) || s.is(Blocks.FERN) || s.is(Blocks.AZALEA) || s.is(Blocks.FLOWERING_AZALEA)
         || s.is(BlockTags.LOGS) || s.is(BlockTags.SAPLINGS) || s.is(Blocks.MANGROVE_ROOTS)) {
         return COVER;
      } else if (s.is(Blocks.SUGAR_CANE)) {
         return REEDS;
      } else if (s.is(Blocks.SHORT_GRASS) || s.is(BlockTags.FLOWERS) || s.is(Blocks.MOSS_CARPET)) {
         return FORAGE;
      } else {
         String path = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
         if (path.contains("reed") || path.contains("cattail") || path.contains("rush")) {
            return REEDS;
         } else if (path.contains("thicket") || path.contains("undergrowth") || path.contains("bush") || path.contains("shrub") || path.contains("bramble")
            || path.contains("fern") || path.contains("brush") || path.contains("deadfall") || path.contains("fallen_branch") || path.contains("seedling")
            || path.contains("sagebrush") || path.contains("juniper")) {
            return COVER;
         } else {
            return !path.contains("fireweed") && !path.contains("heather") && !path.contains("cottongrass") && !path.contains("clover")
                  && !path.contains("pasture") && !path.contains("turf") && !path.contains("wildflower")
               ? NONE
               : FORAGE;
         }
      }
   }

   static boolean loaded(ServerLevel level, int x, int z) {
      return level.getChunkSource().hasChunk(x >> 4, z >> 4);
   }

   /** Feet position (first non-solid block) of the column, or null when unloaded. */
   static BlockPos surface(ServerLevel level, int x, int z) {
      if (!loaded(level, x, z)) {
         return null;
      } else {
         int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
         return y <= level.getMinBuildHeight() ? null : new BlockPos(x, y, z);
      }
   }

   static boolean standable(ServerLevel level, BlockPos feet) {
      BlockPos below = feet.below();
      BlockState ground = level.getBlockState(below);
      if (!ground.getFluidState().isEmpty() || !ground.isFaceSturdy(level, below, Direction.UP) && !ground.is(BlockTags.DIRT) || ground.is(BlockTags.LOGS) || ground.is(BlockTags.LEAVES)) {
         return false;
      } else if (!level.getFluidState(feet).isEmpty()) {
         return false;
      } else {
         BlockState body = level.getBlockState(feet);
         BlockState head = level.getBlockState(feet.above());
         return body.getCollisionShape(level, feet).isEmpty() && head.getCollisionShape(level, feet.above()).isEmpty() && !ground.is(Blocks.MAGMA_BLOCK);
      }
   }

   /** Leaves somewhere 2..10 blocks above the feet. */
   static boolean canopy(ServerLevel level, BlockPos feet) {
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

      for (int dy = 2; dy <= 10; dy++) {
         if (level.getBlockState(m.set(feet.getX(), feet.getY() + dy, feet.getZ())).is(BlockTags.LEAVES)) {
            return true;
         }
      }

      return false;
   }

   /** Canopy test for a column that may be at a different height (uses its own surface). */
   static int columnCanopy(ServerLevel level, int x, int z) {
      BlockPos s = surface(level, x, z);
      if (s == null) {
         return -1;
      } else {
         return canopy(level, s) ? 1 : 0;
      }
   }

   static boolean surfaceWater(ServerLevel level, int x, int z) {
      if (!loaded(level, x, z)) {
         return false;
      } else {
         int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
         return level.getFluidState(new BlockPos(x, y, z)).is(FluidTags.WATER);
      }
   }

   /** Dense low cover right around a spot (feet and head height): 0..~6. */
   static float coverAround(ServerLevel level, BlockPos feet) {
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      float c = 0.0F;

      for (int[] d : RING1) {
         for (int dy = 0; dy <= 1; dy++) {
            BlockState s = level.getBlockState(m.set(feet.getX() + d[0], feet.getY() + dy, feet.getZ() + d[1]));
            int k = kind(s);
            if (k == COVER || k == REEDS || dy == 1 && s.is(BlockTags.LEAVES)) {
               c += 0.45F;
            }
         }
      }

      int k0 = kind(level.getBlockState(feet));
      if (k0 == COVER) {
         c += 0.6F;
      }

      return Math.min(6.0F, c);
   }

   /** Height range of the ground at distance 4 around (for benches and slopes). */
   static int relief(ServerLevel level, BlockPos feet) {
      int lo = feet.getY();
      int hi = feet.getY();

      for (int i = 0; i < 4; i++) {
         int[] d = DIRS8[i];
         if (!loaded(level, feet.getX() + d[0] * 4, feet.getZ() + d[1] * 4)) {
            return 0;
         }

         int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, feet.getX() + d[0] * 4, feet.getZ() + d[1] * 4);
         lo = Math.min(lo, y);
         hi = Math.max(hi, y);
      }

      return hi - lo;
   }

   /** Score of a bedding spot; {@link #REJECT} when the animal cannot or would not lie there. */
   static float bedScore(ServerLevel level, GameSpecies species, BlockPos feet) {
      if (!standable(level, feet)) {
         return REJECT;
      } else {
         boolean canopy = canopy(level, feet);
         float cover = coverAround(level, feet);
         if (!canopy && cover < 1.3F) {
            return REJECT;
         } else {
            float s = (canopy ? 2.5F : 0.0F) + cover;
            if (species == GameSpecies.ELK && canopy) {
               s += 0.6F;
            }

            int relief = relief(level, feet);
            if (relief >= 1 && relief <= 5) {
               s += 0.8F;
            } else if (relief > 8) {
               s -= 1.5F;
            }

            int open = 0;

            for (int i = 0; i < 4; i++) {
               int c = columnCanopy(level, feet.getX() + DIRS8[i][0] * 10, feet.getZ() + DIRS8[i][1] * 10);
               if (c == 0) {
                  open++;
               }
            }

            if (open >= 1 && open <= 2) {
               s += 0.7F;
            }

            return s;
         }
      }
   }

   /** Quick re-check that a stored bed is still cover (forest not cut down). */
   static boolean bedStillGood(ServerLevel level, BlockPos feet) {
      return standable(level, feet) && (canopy(level, feet) || coverAround(level, feet) >= 1.0F);
   }

   /** Score of a feeding spot. */
   static float feedScore(ServerLevel level, GameSpecies species, BlockPos feet) {
      if (!standable(level, feet)) {
         return REJECT;
      } else {
         boolean moose = species == GameSpecies.MOOSE;
         boolean canopy = canopy(level, feet);
         if (canopy && !moose) {
            return REJECT;
         } else {
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            float s = canopy ? -1.0F : 0.0F;

            for (int dx = -2; dx <= 2; dx += 2) {
               for (int dz = -2; dz <= 2; dz += 2) {
                  BlockPos col = surface(level, feet.getX() + dx, feet.getZ() + dz);
                  if (col != null && Math.abs(col.getY() - feet.getY()) <= 2) {
                     BlockState ground = level.getBlockState(m.set(col.getX(), col.getY() - 1, col.getZ()));
                     int k = kind(level.getBlockState(col));
                     if (k == CROP) {
                        s += 0.8F;
                     } else if (k == FORAGE) {
                        s += 0.4F;
                     } else if (ground.is(Blocks.GRASS_BLOCK)) {
                        s += 0.2F;
                     } else if (ground.is(Blocks.FARMLAND)) {
                        s += 0.3F;
                     }
                  }
               }
            }

            int open = 0;
            int edge = 0;
            int water = 0;

            for (int[] d : DIRS8) {
               int near = columnCanopy(level, feet.getX() + d[0] * 6, feet.getZ() + d[1] * 6);
               if (near == 0) {
                  open++;
               }

               int far = columnCanopy(level, feet.getX() + d[0] * 13, feet.getZ() + d[1] * 13);
               if (far == 1) {
                  edge++;
               }

               if (moose && (surfaceWater(level, feet.getX() + d[0] * 3, feet.getZ() + d[1] * 3) || surfaceWater(level, feet.getX() + d[0] * 6, feet.getZ() + d[1] * 6))) {
                  water++;
               }
            }

            if (species == GameSpecies.ELK) {
               s += open * 0.4F + (edge >= 1 && edge <= 4 ? 0.5F : 0.0F);
            } else if (moose) {
               s += Math.min(3.0F, water * 0.9F) + (edge >= 2 ? 0.8F : 0.0F);

               for (int[] d : DIRS8) {
                  if (level.getBlockState(m.set(feet.getX() + d[0] * 2, feet.getY() + 2, feet.getZ() + d[1] * 2)).is(BlockTags.LEAVES)) {
                     s += 0.3F;
                  }
               }
            } else {
               s += open * 0.25F + (edge >= 1 && edge <= 5 ? 1.0F : 0.0F);
            }

            return s;
         }
      }
   }

   static boolean feedStillGood(ServerLevel level, GameSpecies species, BlockPos feet) {
      return standable(level, feet) && (species == GameSpecies.MOOSE || !canopy(level, feet));
   }

   /**
    * A drinking spot at a surface-water column: returns {stand feet position, water block} or null. The stand is the
    * dry, sturdy bank block next to the water with room above it.
    */
   static BlockPos[] drinkSpot(ServerLevel level, int x, int z) {
      if (!loaded(level, x, z)) {
         return null;
      } else {
         int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
         BlockPos water = new BlockPos(x, y, z);
         if (!level.getFluidState(water).is(FluidTags.WATER) || !level.getBlockState(water.above()).isAir()) {
            return null;
         } else {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
               BlockPos bank = water.relative(dir);
               if (!loaded(level, bank.getX(), bank.getZ())) {
                  continue;
               }

               BlockState b = level.getBlockState(bank);
               if (level.getFluidState(bank).isEmpty() && b.isFaceSturdy(level, bank, Direction.UP) && standable(level, bank.above())) {
                  return new BlockPos[]{bank.above(), water};
               }
            }

            return null;
         }
      }
   }

   static boolean waterStillGood(ServerLevel level, BlockPos stand, BlockPos water) {
      return stand != null && water != null && level.getFluidState(water).is(FluidTags.WATER) && standable(level, stand);
   }
}
