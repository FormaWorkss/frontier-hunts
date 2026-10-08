package com.formaworks.frontierhunts.seating;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** [onboard2] Offline checks: seat heights / eye levels vs the measured blind windows, shape rotation vs sit point. */
public final class SeatHarness {
   static int checks, fails;

   static void check(boolean ok, String what) {
      checks++;
      if (!ok) {
         fails++;
         System.out.println("FAIL " + what);
      }
   }

   static double eye(SeatKind k, boolean raised, boolean sunk, double floor) {
      return k.seatY(raised, sunk) - 0.6 + 1.62 - floor; // vehicle attachment 0.6, standing eye 1.62
   }

   public static void main(String[] a) {
      // windows measured from the blind shapes (tools/onboard2/blind_windows.py): centre above the floor
      double ground = 1.50, small = 1.535, big = 1.7025;
      check(Math.abs(eye(SeatKind.BLIND_CHAIR, false, false, 0) - ground) < 0.005, "blind chair eye at the ground blind window centre");
      for (SeatKind k : new SeatKind[]{SeatKind.LOG_STUMP, SeatKind.CAMP_CHAIR, SeatKind.TRAIL_BENCH}) {
         double e = eye(k, false, false, 0);
         check(e > 1.2 + 0.15 && e < 1.8 - 0.15, k.id + " eye inside a ground blind window band too");
      }
      check(Math.abs(eye(SeatKind.TOWER_CHAIR, false, true, -0.4) - small) < 0.005, "tower chair, sunk, at the small tower window centre");
      check(Math.abs(eye(SeatKind.TOWER_CHAIR, true, false, 0) - big) < 0.01, "tower chair, raised, at the big tower window centre");
      check(Math.abs(eye(SeatKind.TOWER_CHAIR, false, false, 0) - small) < 0.005, "tower chair on a plain floor: the small tower height");
      double e = eye(SeatKind.TOWER_CHAIR, true, true, -0.4);
      check(e > 1.205 + 0.1 && e < 1.865 - 0.1, "raised in the small tower: still inside its windows");
      // seat points stay inside their block cell (the seat entity lives there; sunk is the only one below 0.3)
      for (SeatKind k : SeatKind.values()) {
         check(k.seatY(false, false) > 0.3 && k.seatY(true, false) < 1.0, k.id + " seat point inside the cell");
      }
      check(SeatKind.TOWER_CHAIR.seatY(false, true) > 0.0, "sunk seat point still above the cell bottom");
      // shape rotation (outline / collision) agrees with where the sitter is put, for every facing
      BlockPos pos = new BlockPos(10, 64, -7);
      for (Direction f : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
         double lx = 3.0, lz = 5.0; // px right / back of centre
         double[] b = SeatShapes.rotate(new double[]{8 + lx, 0, 8 + lz, 8 + lx, 1, 8 + lz}, f);
         Vec3 w = SeatShapes.local(pos, f, lx / 16.0, lz / 16.0, 0.0);
         check(Math.abs(pos.getX() + b[0] / 16.0 - w.x) < 1e-9 && Math.abs(pos.getZ() + b[2] / 16.0 - w.z) < 1e-9, "shape rotation = sit point mapping, " + f);
         // the back is behind the sitter: facing direction points away from it
         Vec3 back = SeatShapes.local(pos, f, 0, 0.4, 0);
         Vec3 c = Vec3.atBottomCenterOf(pos);
         check(Math.abs((c.x - back.x) - f.getStepX() * 0.4) < 1e-9 && Math.abs((c.z - back.z) - f.getStepZ() * 0.4) < 1e-9, "back opposite the facing, " + f);
      }
      System.out.println(fails == 0 ? "SEATS ALL PASS (" + checks + " checks)" : fails + " FAILED of " + checks);
      System.exit(fails == 0 ? 0 : 1);
   }
}
