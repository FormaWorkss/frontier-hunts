package com.formaworks.frontierhunts.seating;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** [onboard2] Outline / collision shapes of the seats, authored facing north (back to the south) in px, rotated per facing. */
final class SeatShapes {
   private SeatShapes() {
   }

   /** boxes: {x0, y0, z0, x1, y1, z1} in px, north-facing. Index = Direction.get2DDataValue(). */
   static VoxelShape[] of(double[]... boxes) {
      VoxelShape[] out = new VoxelShape[4];
      for (Direction d : new Direction[]{Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST}) {
         VoxelShape s = Shapes.empty();
         for (double[] b : boxes) {
            double[] r = rotate(b, d);
            s = Shapes.or(s, Shapes.box(clamp(r[0]), clamp(r[1]), clamp(r[2]), clamp(r[3]), clamp(r[4]), clamp(r[5])));
         }
         out[d.get2DDataValue()] = s.optimize();
      }
      return out;
   }

   private static double clamp(double px) {
      return Math.max(0.0, Math.min(16.0, px)) / 16.0;
   }

   /** Rotates a north-facing box like the blockstate's y rotation does (east 90, south 180, west 270, clockwise from above). */
   static double[] rotate(double[] b, Direction facing) {
      double x0 = b[0], z0 = b[2], x1 = b[3], z1 = b[5];
      return switch (facing) {
         case EAST -> new double[]{16 - z1, b[1], x0, 16 - z0, b[4], x1};
         case SOUTH -> new double[]{16 - x1, b[1], 16 - z1, 16 - x0, b[4], 16 - z0};
         case WEST -> new double[]{z0, b[1], 16 - x1, z1, b[4], 16 - x0};
         default -> b.clone();
      };
   }

   /** Block-local offset (x to the sitter's right, z toward the back, from the block centre) to world (same turn as {@link #rotate}). */
   static Vec3 local(BlockPos pos, Direction facing, double lx, double lz, double y) {
      double x, z;
      switch (facing) {
         case EAST -> {
            x = -lz;
            z = lx;
         }
         case SOUTH -> {
            x = -lx;
            z = -lz;
         }
         case WEST -> {
            x = lz;
            z = -lx;
         }
         default -> {
            x = lx;
            z = lz;
         }
      }
      return new Vec3(pos.getX() + 0.5 + x, pos.getY() + y, pos.getZ() + 0.5 + z);
   }
}
