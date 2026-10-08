package com.formaworks.frontierhunts.camload;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;

/**
 * [camload] Offline check of the camera footprint: every camera position inside a chunk x every facing x ranges 15-32.
 * Asserts: own chunk first, at most 4 chunks, no duplicates, every chunk touches the cone, and reports how much of the
 * cone's ground area the kept chunks cover (worst and mean).
 * javac -d /tmp/fc -cp <classes>:<cp> tools/camload/FootprintCheck.java && java -cp /tmp/fc:<classes>:<cp> com.formaworks.frontierhunts.camload.FootprintCheck
 */
public final class FootprintCheck {
   public static void main(String[] args) {
      Direction[] dirs = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
      int cases = 0;
      int fail = 0;
      double worst = 1.0;
      double sum = 0.0;
      int[] sizes = new int[5];
      for (double range : new double[]{15, 20, 32}) {
         double rWorst = 1.0, rSum = 0.0;
         int rCases = 0;
         for (int x = -16; x < 16; x++) {
            for (int z = -16; z < 16; z++) {
               for (Direction f : dirs) {
                  BlockPos pos = new BlockPos(x, 70, z);
                  long[] c = CameraFootprint.chunks(pos, f, range, 4);
                  cases++;
                  sizes[c.length]++;
                  if (c.length > 4 || c[0] != ChunkPos.asLong(x >> 4, z >> 4)) fail++;
                  for (int i = 0; i < c.length; i++) for (int j = i + 1; j < c.length; j++) if (c[i] == c[j]) fail++;
                  // coverage of the cone (fine sampling)
                  double in = 0, all = 0;
                  double lx = x + 0.5 + f.getStepX() * 0.4, lz = z + 0.5 + f.getStepZ() * 0.4;
                  for (double d = 0.25; d <= range; d += 0.5) {
                     for (int i = 0; i < 41; i++) {
                        double a = Math.toRadians(-35 + 70.0 * i / 40);
                        double px = lx + (f.getStepX() * Math.cos(a) - f.getStepZ() * Math.sin(a)) * d;
                        double pz = lz + (f.getStepZ() * Math.cos(a) + f.getStepX() * Math.sin(a)) * d;
                        long k = ChunkPos.asLong((int)Math.floor(px) >> 4, (int)Math.floor(pz) >> 4);
                        all += d;
                        for (long cc : c) if (cc == k) { in += d; break; }
                     }
                  }
                  double share = in / all;
                  worst = Math.min(worst, share);
                  sum += share;
                  rWorst = Math.min(rWorst, share);
                  rSum += share;
                  rCases++;
               }
            }
         }
         System.out.printf("range %.0f: coverage worst=%.1f%% mean=%.1f%%%n", range, rWorst * 100, rSum / rCases * 100);
      }
      System.out.printf("cases=%d fail=%d sizes(1..4)=%d/%d/%d/%d coverage worst=%.1f%% mean=%.1f%%%n",
         cases, fail, sizes[1], sizes[2], sizes[3], sizes[4], worst * 100, sum / cases * 100);
      if (fail > 0) System.exit(1);
   }
}
