package com.formaworks.frontierhunts.expedition;

import com.formaworks.frontierhunts.terrain.TerrainKinds;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Server-side reading of the tree a stand is mounted on: which upright log column it bolts to, how
 * wide the trunk is (1x1 or 2x2), where firm ground for the ladder is, and how thick the trunk is
 * drawn by the realistic graphics preset at a given height.
 * <p>
 * The realistic preset exists only on clients, so the radius here mirrors the client stem fit
 * ({@code client.tree.TreeGrowth#stems/radius}): straight or single-step leaning log levels from the
 * rooted foot, r0 from the foot count and trunk height, broadleaves tapering almost to nothing at the
 * top and conifers to a point. Every player sees the same stand whatever preset they use, so a trunk
 * that is drawn as a thin stem there is refused for everyone.
 */
final class StandTrunk {
   /** Thinnest drawn trunk radius (blocks) a stand may be strapped to. */
   static final float MIN_RADIUS = 0.13F;
   /** Fewest straight upright trunk logs a stand tree needs. */
   static final int MIN_LOGS = 4;

   private StandTrunk() {
   }

   /** An upright log (a trunk piece, not a branch or a felled log). */
   static boolean upright(BlockGetter level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      return state.is(BlockTags.LOGS)
         && (!state.hasProperty(BlockStateProperties.AXIS) || state.getValue(BlockStateProperties.AXIS) == Direction.Axis.Y);
   }

   private static boolean loadedUpright(LevelReader level, BlockPos pos) {
      return level.hasChunkAt(pos) && upright(level, pos);
   }

   /** Saplings, fences, walls, bamboo and other posts: too thin to carry a stand. */
   static boolean thinPost(BlockGetter level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      if (state.is(BlockTags.SAPLINGS) || state.is(BlockTags.FENCES) || state.is(BlockTags.WALLS) || state.is(Blocks.BAMBOO)
         || state.is(Blocks.BAMBOO_SAPLING) || state.is(BlockTags.FENCE_GATES)) {
         return true;
      }
      if (state.is(BlockTags.LOGS) || state.isAir()) {
         return false;
      }
      try {
         VoxelShape shape = state.getCollisionShape(level, pos);
         if (shape.isEmpty()) {
            return false;
         }
         var box = shape.bounds();
         return box.getYsize() > 0.5 && Math.max(box.getXsize(), box.getZsize()) < 0.7;
      } catch (RuntimeException e) {
         return false;
      }
   }

   /**
    * The upright trunk log used at or near a click: the clicked log itself, else the nearest upright
    * log around the clicked block (a click on the ground, leaves or a branch next to a trunk).
    */
   static BlockPos find(LevelReader level, BlockPos clicked, Vec3 hit) {
      if (loadedUpright(level, clicked)) {
         return clicked;
      }
      BlockPos best = null;
      double bestScore = Double.MAX_VALUE;
      for (int dy = -1; dy <= 3; dy++) {
         for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
               BlockPos p = clicked.offset(dx, dy, dz);
               if (!loadedUpright(level, p)) {
                  continue;
               }
               double ex = p.getX() + 0.5 - hit.x, ez = p.getZ() + 0.5 - hit.z;
               double score = ex * ex + ez * ez + 0.05 * Math.abs(dy);
               if (score < bestScore) {
                  bestScore = score;
                  best = p.immutable();
               }
            }
         }
      }
      return best;
   }

   /** Lowest upright log of the column through pos. */
   static int foot(LevelReader level, BlockPos pos) {
      BlockPos p = pos;
      int steps = 0;
      while (steps++ < 256 && p.getY() > level.getMinBuildHeight() && loadedUpright(level, p.below())) {
         p = p.below();
      }
      return p.getY();
   }

   /** Highest upright log of the column through pos. */
   static int top(LevelReader level, BlockPos pos) {
      BlockPos p = pos;
      int steps = 0;
      while (steps++ < 256 && p.getY() < level.getMaxBuildHeight() - 1 && loadedUpright(level, p.above())) {
         p = p.above();
      }
      return p.getY();
   }

   /**
    * South-west corner (min x, min z) of a 2x2 trunk the column belongs to, or null for a single
    * column. All four columns must be upright logs over the three levels from y.
    */
   static BlockPos square(LevelReader level, BlockPos column, int y) {
      for (int ox = -1; ox <= 0; ox++) {
         for (int oz = -1; oz <= 0; oz++) {
            boolean all = true;
            for (int i = 0; i <= 1 && all; i++) {
               for (int k = 0; k <= 1 && all; k++) {
                  for (int dy = 0; dy < 3 && all; dy++) {
                     all = loadedUpright(level, new BlockPos(column.getX() + ox + i, y + dy, column.getZ() + oz + k));
                  }
               }
            }
            if (all) {
               return new BlockPos(column.getX() + ox, y, column.getZ() + oz);
            }
         }
      }
      return null;
   }

   /**
    * Firm ground for the ladder in column (x, z): the highest block, from a few blocks above the
    * trunk foot downwards, whose top is sturdy and has open space above it. Leaves are not ground.
    * Returns Integer.MIN_VALUE when there is none (or it is not loaded).
    */
   static int ground(LevelReader level, int x, int z, int footY) {
      BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
      for (int y = footY + 3; y >= Math.max(level.getMinBuildHeight(), footY - 10); y--) {
         p.set(x, y, z);
         if (!level.hasChunkAt(p)) {
            return Integer.MIN_VALUE;
         }
         BlockState state = level.getBlockState(p);
         if (state.is(BlockTags.LEAVES) || !state.isFaceSturdy(level, p, Direction.UP)) {
            continue;
         }
         BlockPos above = p.above();
         BlockState up = level.getBlockState(above);
         if (up.canBeReplaced() && up.getFluidState().isEmpty()) {
            return y;
         }
      }
      return Integer.MIN_VALUE;
   }

   // ------------------------------------------------------------------ drawn trunk radius

   /** A fitted stem as the realistic preset draws it. */
   static final class Stem {
      int y0;
      int foot;
      int height;
      boolean conifer;

      float r0() {
         float h = this.height;
         return this.foot == 1 ? clamp(0.2F + 0.017F * h, 0.3F, 0.48F) : (float)Math.sqrt(this.foot / Math.PI) * 0.9F;
      }

      /** Drawn trunk radius at world height y (mirrors TreeGrowth#radius). */
      float radius(double y) {
         float h = (float)Math.max(0.0, y - this.y0);
         float H = this.height;
         float r;
         if (this.conifer) {
            float top = H + 0.9F;
            r = this.r0() * Math.max(0.02F, 1.0F - 0.9F * (float)Math.pow(h / top, 0.95));
         } else {
            r = this.r0() * (1.0F - 0.96F * Math.min(1.0F, h / H));
         }
         return r * (1.0F + (this.foot > 1 ? 0.22F : 0.38F) * (float)Math.exp(-h / 0.40F)); // [trees2] mirrors TreeGrowth.flare
      }
   }

   private static boolean ground(LevelReader level, BlockPos pos) {
      return level.hasChunkAt(pos) && TerrainKinds.naturalKind(level.getBlockState(pos).getBlock()) != 0;
   }

   private static boolean rooted(LevelReader level, BlockPos pos) {
      return loadedUpright(level, pos) && ground(level, pos.below());
   }

   /**
    * The stem the realistic preset grows through this trunk column, or null when that preset draws
    * the column as plain blocks (not rooted in natural ground, a dead snag without a crown, a wall of
    * posts) - such trunks keep their full block faces and are never too thin.
    */
   static Stem stem(LevelReader level, BlockPos column) {
      try {
         int footY = foot(level, column);
         BlockPos foot = new BlockPos(column.getX(), footY, column.getZ());
         if (!rooted(level, foot)) {
            return null;
         }
         // a living crown: the realistic trees leave leafless snags and stumps as blocks
         int top = top(level, column);
         int leaves = 0;
         BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
         outer:
         for (int y = top - 3; y <= top + 4; y++) {
            for (int dx = -4; dx <= 4; dx++) {
               for (int dz = -4; dz <= 4; dz++) {
                  m.set(column.getX() + dx, y, column.getZ() + dz);
                  if (level.hasChunkAt(m) && level.getBlockState(m).is(BlockTags.LEAVES) && ++leaves >= 6) {
                     break outer;
                  }
               }
            }
         }
         if (leaves < 6) {
            return null;
         }
         // the rooted base cluster at the foot level
         List<BlockPos> base = new ArrayList<>();
         Set<BlockPos> seen = new HashSet<>();
         ArrayDeque<BlockPos> queue = new ArrayDeque<>();
         queue.add(foot);
         seen.add(foot);
         while (!queue.isEmpty() && base.size() <= 9) {
            BlockPos c = queue.poll();
            base.add(c);
            for (Direction d : Direction.Plane.HORIZONTAL) {
               BlockPos n = c.relative(d);
               if (seen.add(n) && rooted(level, n)) {
                  queue.add(n);
               }
            }
         }
         if (base.size() > 9) {
            return null;
         }
         if (base.size() < 4) {
            base = List.of(foot); // two or three rooted columns grow as separate stems
         }
         Stem s = new Stem();
         s.y0 = footY;
         s.foot = base.size();
         List<BlockPos> row = base;
         int y = footY;
         int levels = 0;
         while (!row.isEmpty() && levels < 96) {
            levels++;
            List<BlockPos> next = new ArrayList<>();
            for (BlockPos c : row) {
               BlockPos up = c.above();
               if (loadedUpright(level, up)) {
                  next.add(up);
               }
            }
            if (next.isEmpty() && row.size() == 1 && levels >= 3) {
               BlockPos c = row.get(0);
               BlockPos only = null;
               int n = 0;
               for (int dx = -1; dx <= 1; dx++) {
                  for (int dz = -1; dz <= 1; dz++) {
                     if ((dx | dz) == 0) {
                        continue;
                     }
                     BlockPos p = new BlockPos(c.getX() + dx, y + 1, c.getZ() + dz);
                     if (loadedUpright(level, p) && !level.getBlockState(p.below()).is(BlockTags.LOGS)) {
                        only = p;
                        n++;
                     }
                  }
               }
               if (n == 1) {
                  next.add(only);
               }
            }
            row = next;
            y++;
         }
         s.height = levels;
         if (s.height < 2) {
            return null;
         }
         s.conifer = conifer(BuiltInRegistries.BLOCK.getKey(level.getBlockState(foot).getBlock()).getPath());
         return s;
      } catch (RuntimeException e) {
         return null;
      }
   }

   /** Needle trees (same rule as the client's TrunkModel#isConifer). */
   static boolean conifer(String path) {
      path = path.replace("alpine", "");
      return path.contains("pine") || path.contains("spruce") || path.contains("cedar") || path.contains("fir")
         || path.contains("hemlock") || path.contains("larch") || path.contains("juniper") || path.contains("redwood");
   }

   private static float clamp(float v, float lo, float hi) {
      return v < lo ? lo : (v > hi ? hi : v);
   }
}
