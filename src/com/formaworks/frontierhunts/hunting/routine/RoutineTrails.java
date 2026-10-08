package com.formaworks.frontierhunts.hunting.routine;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Game trail geometry: simplifying a walked breadcrumb line, picking the edge-hugging line for a first trip, joining. */
public final class RoutineTrails {
   public static final int MAX_POINTS = 96;
   /** waypoints are kept at most this far apart so reused trails stay on the worn line */
   static final double MAX_SPACING = 7.0;

   private RoutineTrails() {
   }

   /**
    * Drops breadcrumbs that lie on a straight line (within 0.9 blocks) while keeping waypoints no more than
    * {@link #MAX_SPACING} apart. Pure; the ends are always kept.
    */
   public static long[] simplify(List<BlockPos> crumbs) {
      if (crumbs.size() < 2) {
         return null;
      } else {
         List<BlockPos> out = new ArrayList<>();
         out.add(crumbs.get(0));
         int anchor = 0;

         for (int i = 2; i < crumbs.size(); i++) {
            BlockPos a = crumbs.get(anchor);
            BlockPos c = crumbs.get(i);
            boolean straight = true;

            for (int j = anchor + 1; j < i && straight; j++) {
               straight = lineDistance(a, c, crumbs.get(j)) <= 0.9 && Math.abs(crumbs.get(j).getY() - a.getY()) <= 2;
            }

            if (!straight || Math.sqrt(horizontal(a, c)) > MAX_SPACING) {
               anchor = i - 1;
               out.add(crumbs.get(anchor));
            }
         }

         out.add(crumbs.get(crumbs.size() - 1));
         if (out.size() > MAX_POINTS) {
            List<BlockPos> thin = new ArrayList<>();
            double stepf = (out.size() - 1) / (double)(MAX_POINTS - 1);

            for (int k = 0; k < MAX_POINTS; k++) {
               thin.add(out.get((int)Math.round(k * stepf)));
            }

            out = thin;
         }

         long[] packed = new long[out.size()];

         for (int k = 0; k < packed.length; k++) {
            packed[k] = out.get(k).asLong();
         }

         return packed;
      }
   }

   static double horizontal(BlockPos a, BlockPos b) {
      double dx = a.getX() - b.getX();
      double dz = a.getZ() - b.getZ();
      return dx * dx + dz * dz;
   }

   /** Horizontal distance of p from segment a-c. */
   static double lineDistance(BlockPos a, BlockPos c, BlockPos p) {
      double ax = a.getX();
      double az = a.getZ();
      double vx = c.getX() - ax;
      double vz = c.getZ() - az;
      double len = vx * vx + vz * vz;
      double t = len < 1.0E-6 ? 0.0 : Math.max(0.0, Math.min(1.0, ((p.getX() - ax) * vx + (p.getZ() - az) * vz) / len));
      double dx = ax + vx * t - p.getX();
      double dz = az + vz * t - p.getZ();
      return Math.sqrt(dx * dx + dz * dz);
   }

   public static double length(long[] trail) {
      double l = 0.0;

      for (int i = 1; i < trail.length; i++) {
         l += Math.sqrt(horizontal(BlockPos.of(trail[i - 1]), BlockPos.of(trail[i])));
      }

      return l;
   }

   public static long[] reverse(long[] trail) {
      long[] r = new long[trail.length];

      for (int i = 0; i < trail.length; i++) {
         r[i] = trail[trail.length - 1 - i];
      }

      return r;
   }

   /** Index of the waypoint to walk to next when joining a trail from {@code at}: the nearest one, or the one after it
    *  when the nearest lies behind (already between it and the next). -1 when no waypoint is within {@code maxDist}. */
   public static int join(long[] trail, double x, double z, double maxDist) {
      int best = -1;
      double bestD = maxDist * maxDist;

      for (int i = 0; i < trail.length; i++) {
         BlockPos p = BlockPos.of(trail[i]);
         double dx = p.getX() + 0.5 - x;
         double dz = p.getZ() + 0.5 - z;
         double d = dx * dx + dz * dz;
         if (d < bestD) {
            bestD = d;
            best = i;
         }
      }

      if (best >= 0 && best + 1 < trail.length) {
         BlockPos a = BlockPos.of(trail[best]);
         BlockPos b = BlockPos.of(trail[best + 1]);
         double vx = b.getX() - a.getX();
         double vz = b.getZ() - a.getZ();
         // past the nearest point already (moving toward the next): skip it
         if ((x - a.getX() - 0.5) * vx + (z - a.getZ() - 0.5) * vz > 0.0) {
            best++;
         }
      }

      return best;
   }

   /**
    * Via points for the first trip between two anchors: at a third and two thirds of the way, the column within 14
    * blocks either side of the straight line that best follows an edge or a line of cover (canopy there, open ground
    * close by). Deer travel edges and cover lines rather than crossing open ground; the path finder then threads the
    * funnels between obstacles on its own. Checks 2 x 5 columns.
    */
   static List<BlockPos> viaPoints(ServerLevel level, BlockPos from, BlockPos to) {
      List<BlockPos> vias = new ArrayList<>();
      double dx = to.getX() - from.getX();
      double dz = to.getZ() - from.getZ();
      double len = Math.sqrt(dx * dx + dz * dz);
      if (len < 20.0) {
         return vias;
      } else {
         double nx = -dz / len;
         double nz = dx / len;

         for (double f : new double[]{0.33, 0.67}) {
            BlockPos best = null;
            float bestScore = -100.0F;

            for (int off : new int[]{0, -7, 7, -14, 14}) {
               int x = (int)Math.round(from.getX() + dx * f + nx * off);
               int z = (int)Math.round(from.getZ() + dz * f + nz * off);
               BlockPos feet = Habitat.surface(level, x, z);
               if (feet != null && Math.abs(feet.getY() - from.getY()) < 24 && Habitat.standable(level, feet)) {
                  boolean canopy = Habitat.canopy(level, feet);
                  int side1 = Habitat.columnCanopy(level, (int)Math.round(x + nx * 5), (int)Math.round(z + nz * 5));
                  int side2 = Habitat.columnCanopy(level, (int)Math.round(x - nx * 5), (int)Math.round(z - nz * 5));
                  float s = (canopy ? 1.0F : 0.0F) + (side1 != side2 ? 1.2F : 0.0F) + Habitat.coverAround(level, feet) * 0.3F - Math.abs(off) * 0.04F;
                  if (s > bestScore) {
                     bestScore = s;
                     best = feet;
                  }
               }
            }

            if (best != null) {
               vias.add(best);
            }
         }

         return vias;
      }
   }
}
