package com.formaworks.frontierhunts.livingworld.plan;

/**
 * [livingworld] Writes a {@link Plan} into a {@link World}, limited to one chunk's columns (x0..x1, z0..z1 inclusive).
 * Order: terrain shaping, blocks, block-entity data, entities. Every decision reads only the column it writes,
 * so a structure spanning several chunks comes out the same whatever order the chunks generate in.
 */
public final class Executor {
   private Executor() {
   }

   public static int run(Plan plan, World w, int x0, int z0, int x1, int z1) {
      int written = 0;
      for (Plan.Shape s : plan.shapes()) {
         if (s.x >= x0 && s.x <= x1 && s.z >= z0 && s.z <= z1) {
            shape(s, w);
         }
      }
      java.util.List<Plan.Block> withNbt = new java.util.ArrayList<>();
      for (Plan.Block b : plan.blocks()) {
         if (b.x < x0 || b.x > x1 || b.z < z0 || b.z > z1 || b.y <= w.minY() || b.y >= w.maxY()) {
            continue;
         }
         int y = b.y;
         if ((b.flags & Plan.ON_GROUND) != 0) {
            y = drop(w, b.x, b.y, b.z);
            if (y == Integer.MIN_VALUE) {
               continue;
            }
         }
         if ((b.flags & (Plan.IF_FREE | Plan.ON_GROUND)) != 0) {
            int k = w.kind(b.x, y, b.z);
            if (k != World.AIR && k != World.PLANT) {
               continue;
            }
         }
         if ((b.flags & Plan.SUPPORTED) != 0) {
            int k = w.kind(b.x, y - 1, b.z);
            if (k == World.AIR || k == World.WATER || k == World.LAVA || k == World.PLANT || k == World.LEAVES) {
               continue;
            }
         }
         w.set(b.x, y, b.z, b.spec);
         written++;
         if (b.nbt != null) {
            withNbt.add(y == b.y ? b : moved(b, y));
         }
      }
      for (Plan.Block b : withNbt) {
         w.nbt(b.x, b.y, b.z, b.nbt);
      }
      for (Plan.Entity e : plan.entities()) {
         int bx = (int)Math.floor(e.x);
         int bz = (int)Math.floor(e.z);
         if (bx >= x0 && bx <= x1 && bz >= z0 && bz <= z1) {
            w.entity(e.x, e.y, e.z, e.yaw, e.snbt);
         }
      }
      return written;
   }

   private static Plan.Block moved(Plan.Block b, int y) {
      Plan.Block m = new Plan.Block(b.x, y, b.z, b.spec);
      m.nbt = b.nbt;
      return m;
   }

   /** first free cell standing on natural ground, searching around the hint height */
   static int drop(World w, int x, int hint, int z) {
      for (int y = Math.min(w.maxY() - 1, hint + 6); y >= Math.max(w.minY() + 1, hint - 14); y--) {
         int below = w.kind(x, y - 1, z);
         if (below == World.WATER || below == World.LAVA) {
            return Integer.MIN_VALUE;
         }
         if (below == World.GROUND) {
            int here = w.kind(x, y, z);
            return here == World.AIR || here == World.PLANT ? y : Integer.MIN_VALUE;
         }
      }
      return Integer.MIN_VALUE;
   }

   /** natural ground top (first y above the highest ground block) at or below {@code from}; MIN_VALUE when water covers it */
   public static int ground(World w, int x, int z, int from, int to) {
      boolean water = false;
      for (int y = from; y >= to; y--) {
         int k = w.kind(x, y, z);
         if (k == World.WATER || k == World.LAVA) {
            water = true;
         } else if (k == World.GROUND) {
            return water ? -(y + 1) - 1_000_000 : y + 1;
         }
      }
      return Integer.MIN_VALUE;
   }

   static void shape(Plan.Shape s, World w) {
      int from = Math.min(w.maxY() - 1, s.top + Math.max(s.maxCut, 6) + 2);
      int g = ground(w, s.x, s.z, from, Math.max(w.minY() + 1, s.top - s.maxFill - 10));
      if (g == Integer.MIN_VALUE) {
         return;
      }
      if (g < -999_000) {
         if (s.dryOnly) {
            return;
         }
         g = -(g + 1_000_000) - 1;
      }
      int target = (int)Math.round(g + (s.top - g) * s.weight);
      if (target < g) {
         target = Math.max(target, g - s.maxCut);
         w.copy(s.x, g - 1, s.z, target - 1);
         for (int y = target; y < g; y++) {
            w.setAir(s.x, y, s.z);
         }
      } else if (target > g) {
         target = Math.min(target, g + s.maxFill);
         w.copy(s.x, g - 1, s.z, target - 1);
         String fill = s.fill == null ? "minecraft:dirt" : s.fill;
         for (int y = g - 1; y <= target - 2; y++) {
            w.set(s.x, y, s.z, fill);
         }
      }
      if (s.surface != null) {
         w.set(s.x, target - 1, s.z, s.surface);
      }
      int clearTop = Math.min(w.maxY() - 1, target + s.clear);
      for (int y = target; y < clearTop; y++) {
         int k = w.kind(s.x, y, s.z);
         if (k != World.AIR && k != World.WATER) {
            w.setAir(s.x, y, s.z);
         }
      }
      int canopyTop = Math.min(w.maxY() - 1, target + s.canopy);
      for (int y = clearTop; y < canopyTop; y++) {
         int k = w.kind(s.x, y, s.z);
         if (k == World.LOG || k == World.LEAVES) {
            w.setAir(s.x, y, s.z);
         }
      }
   }
}
