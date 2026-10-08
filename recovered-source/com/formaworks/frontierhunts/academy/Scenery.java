package com.formaworks.frontierhunts.academy;

import com.formaworks.frontierhunts.environment.WildTrees;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * [academy] Natural dressing for the plots, built from the mod's own landscape blocks (meadow grass, shrubs, ferns,
 * boulders, deadfall) and its tree generator, so the training grounds look like the reserve. Keep-clear rectangles
 * protect shooting lanes, paths and stations.
 */
final class Scenery {
   private final Plot plot;
   private final List<int[]> clear = new ArrayList<>();

   Scenery(Plot plot) {
      this.plot = plot;
   }

   /** Nothing grows inside this rectangle (local, inclusive). */
   Scenery keepClear(int x0, int z0, int x1, int z1) {
      this.clear.add(new int[]{Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1)});
      return this;
   }

   boolean blocked(int x, int z) {
      for (int[] r : this.clear) {
         if (x >= r[0] && x <= r[2] && z >= r[1] && z <= r[3]) {
            return true;
         }
      }
      return false;
   }

   static BlockState grass() {
      return Plot.block("alpine_pasture", Blocks.SHORT_GRASS.defaultBlockState());
   }

   BlockState meadowGrass() {
      return Plot.withInt(grass(), "height", this.plot.rnd(0, 2));
   }

   static BlockState shrub(String id) {
      return Plot.block(id, Blocks.FERN.defaultBlockState());
   }

   /** Scattered meadow plants over an area: grass tufts, flowers and the odd shrub. */
   void meadow(int x0, int z0, int x1, int z1, float grass, float flowers, float shrubs) {
      for (int x = x0; x <= x1; x++) {
         for (int z = z0; z <= z1; z++) {
            if (this.blocked(x, z)) {
               continue;
            }
            float r = this.plot.rndf();
            double n = noise(x, z);
            if (r < grass * (0.55 + n)) {
               this.plot.plant(x, z, this.meadowGrass());
            } else if (r < grass * (0.55 + n) + flowers * n) {
               this.plot.plant(x, z, Plot.block(this.plot.rndf() < 0.55F ? "fireweed" : "heather", Blocks.DANDELION.defaultBlockState()));
            } else if (r < grass * (0.55 + n) + flowers * n + shrubs) {
               this.clump(x, z, 1);
            }
         }
      }
   }

   /** Smooth 0..1 pattern so grass and flowers come in drifts rather than salt-and-pepper. */
   static double noise(int x, int z) {
      double v = Math.sin(x * 0.11 + z * 0.07) * 0.5 + Math.sin(x * 0.043 - z * 0.13 + 1.7) * 0.35 + Math.sin((x + z) * 0.21) * 0.15;
      return 0.5 + 0.5 * v;
   }

   /** A shrub clump: a juniper / hazel / dogwood / huckleberry knot with ferns at its foot. */
   void clump(int x, int z, int r) {
      String[] kinds = {"juniper_shrub", "woodland_bush", "broadleaf_thicket", "huckleberry_shrub"};
      String main = kinds[this.plot.random.nextInt(kinds.length)];
      for (int dx = -r; dx <= r; dx++) {
         for (int dz = -r; dz <= r; dz++) {
            if (dx * dx + dz * dz > r * r + 1 || this.blocked(x + dx, z + dz)) {
               continue;
            }
            if (this.plot.rndf() < 0.75F) {
               this.plot.plant(x + dx, z + dz, Plot.withInt(shrub(main), "size", this.plot.rnd(0, 2)));
            } else {
               this.plot.plant(x + dx, z + dz, shrub(this.plot.rndf() < 0.5F ? "spreading_fern" : "arching_fern"));
            }
         }
      }
   }

   /** A row/belt of trees (conifer-heavy) between two points, with jitter. */
   void treeBelt(int x0, int z0, int x1, int z1, int spacing, float pineShare) {
      double len = Math.hypot(x1 - x0, z1 - z0);
      int n = Math.max(1, (int)(len / spacing));
      for (int i = 0; i <= n; i++) {
         double t = n == 0 ? 0 : (double)i / n;
         int x = (int)Math.round(x0 + (x1 - x0) * t) + this.plot.rnd(-2, 2);
         int z = (int)Math.round(z0 + (z1 - z0) * t) + this.plot.rnd(-2, 2);
         this.tree(x, z, pineShare);
      }
   }

   void tree(int x, int z, float pineShare) {
      if (this.blocked(x, z)) {
         return;
      }
      float r = this.plot.rndf();
      WildTrees.Kind kind = r < pineShare ? (this.plot.rndf() < 0.6F ? WildTrees.Kind.PINE : WildTrees.Kind.FIR)
         : (this.plot.rndf() < 0.6F ? WildTrees.Kind.ASPEN : WildTrees.Kind.BIRCH);
      this.plot.tree(x, z, kind, 0.35F + this.plot.rndf() * 0.55F);
   }

   /** Forest floor: duff and litter patches, undergrowth and ferns in the shade. */
   void forestFloor(int x0, int z0, int x1, int z1, float duff, float plants) {
      BlockState duffBlock = Plot.block("forest_duff", Blocks.PODZOL.defaultBlockState());
      for (int x = x0; x <= x1; x++) {
         for (int z = z0; z <= z1; z++) {
            double n = noise(x * 2, z * 2 + 31);
            if (n < duff) {
               this.plot.ground(x, z, n < duff * 0.35 ? Blocks.COARSE_DIRT.defaultBlockState() : duffBlock);
            }
            if (this.blocked(x, z)) {
               continue;
            }
            float r = this.plot.rndf();
            if (r < plants * 0.45F) {
               this.plot.plant(x, z, Plot.withInt(Plot.block("forest_litter", Blocks.AIR.defaultBlockState()), "variant", this.plot.rnd(0, 2)));
            } else if (r < plants * 0.8F) {
               this.plot.plant(x, z, Plot.withInt(Plot.block("undergrowth", Blocks.FERN.defaultBlockState()), "variant", this.plot.rnd(0, 6)));
            } else if (r < plants) {
               this.plot.plant(x, z, shrub(this.plot.rndf() < 0.5F ? "spreading_fern" : "arching_fern"));
            }
         }
      }
   }

   void boulder(int x, int z) {
      if (!this.blocked(x, z)) {
         this.plot.plant(x, z, Plot.block(this.plot.rndf() < 0.5F ? "river_boulder" : "mossy_stone", Blocks.MOSSY_COBBLESTONE.defaultBlockState()));
      }
   }

   void deadfall(int x, int z) {
      if (!this.blocked(x, z)) {
         this.plot.plant(x, z, Plot.facing(Plot.block("deadfall_log", Blocks.SPRUCE_LOG.defaultBlockState()),
            this.plot.rndf() < 0.5F ? net.minecraft.core.Direction.NORTH : net.minecraft.core.Direction.EAST));
      }
   }
}
