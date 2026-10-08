package com.formaworks.frontierhunts.livingworld.plan;

import java.util.HashSet;
import java.util.Set;

/** [livingworld] Occupancy grid in local coordinates so camp pieces never overlap, plus small layout helpers. */
public final class Layout {
   private final Set<Long> used = new HashSet<>();

   static long k(int x, int z) {
      return ((long)x << 32) ^ (z & 0xFFFFFFFFL);
   }

   public boolean free(int x0, int z0, int x1, int z1) {
      for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
         for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
            if (this.used.contains(k(x, z))) {
               return false;
            }
         }
      }
      return true;
   }

   public void reserve(int x0, int z0, int x1, int z1) {
      for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
         for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
            this.used.add(k(x, z));
         }
      }
   }

   public boolean taken(int x, int z) {
      return this.used.contains(k(x, z));
   }

   /** the horizontal direction from (x, z) toward (tx, tz) */
   public static Dir toward(int x, int z, int tx, int tz) {
      int dx = tx - x;
      int dz = tz - z;
      if (Math.abs(dx) >= Math.abs(dz)) {
         return dx >= 0 ? Dir.EAST : Dir.WEST;
      }
      return dz >= 0 ? Dir.SOUTH : Dir.NORTH;
   }

   /** footprint rect {x0,z0,x1,z1} of a north-facing box (x from bx0..bx1, z from bz0..bz1 relative to an origin) turned to f */
   public static int[] rect(int x, int z, int bx0, int bz0, int bx1, int bz1, Dir f) {
      int[] a = Kit.off(bx0, bz0, f);
      int[] b = Kit.off(bx1, bz1, f);
      return new int[]{x + Math.min(a[0], b[0]), z + Math.min(a[1], b[1]), x + Math.max(a[0], b[0]), z + Math.max(a[1], b[1])};
   }

   public boolean fits(int[] r, int margin) {
      return this.free(r[0] - margin, r[1] - margin, r[2] + margin, r[3] + margin);
   }

   public void take(int[] r) {
      this.reserve(r[0], r[1], r[2], r[3]);
   }
}
