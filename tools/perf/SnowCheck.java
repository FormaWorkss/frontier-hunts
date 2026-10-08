package com.formaworks.frontierhunts.season.client;
// [perf3] Smooth-snow quads from pooled quads are identical to fresh ones, and what a snow block's mesh allocates.
// A synthetic snowfield (random layers 1..8 on a rolling floor, with full snow blocks, walls and gaps; snowy crowns),
// every block built fresh, then built again from quads handed back to the pool, field by field compared.
// javac -d /tmp/b -cp <classes>:<cp62> tools/perf/SnowCheck.java
// java -cp /tmp/b:<classes>:<cp62> com.formaworks.frontierhunts.season.client.SnowCheck
import java.util.*;

public final class SnowCheck {
   static long h;

   static void mix(long v) {
      h ^= v;
      h *= 0x100000001B3L;
      h ^= h >>> 29;
   }

   static long hash(List<SnowField.Quad> qs) {
      h = 0xCBF29CE484222325L;
      for (SnowField.Quad q : qs) {
         mix(q.face);
         for (float[] a : new float[][]{q.x, q.y, q.z, q.u, q.v, q.nx, q.ny, q.nz}) for (float f : a) mix(Float.floatToRawIntBits(f));
         for (int c : q.rgb) mix(c);
      }
      return h;
   }

   static int ground(int x, int z) {
      return 64 + (int)Math.floor(3 * Math.sin(x * 0.21) + 2 * Math.cos(z * 0.17));
   }

   static long cell(int x, int z) {
      long v = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL;
      v ^= v >>> 31;
      v *= 0xBF58476D1CE4E5B9L;
      return v ^ v >>> 29;
   }

   static final SnowField.Probe FIELD = new SnowField.Probe() {
      public int snow(int x, int y, int z) {
         int g = ground(x, z);
         long c = cell(x, z);
         if (y == g + 1) return 1 + (int)((c >>> 8) & 7); // the snow layer
         if (y == g && (c & 15) == 0) return 8; // a full block of snow under it
         return 0;
      }

      public boolean open(int x, int y, int z) {
         int g = ground(x, z);
         return y > g + 1 || y == g + 1 && snow(x, y, z) == 0;
      }

      public boolean full(int x, int y, int z) {
         int g = ground(x, z);
         return y <= g || (cell(x, z) & 31) == 3 && y <= g + 2; // ground, and the odd wall
      }
   };

   static final CrownSnow.Probe CROWNS = new CrownSnow.Probe() {
      public boolean snowyCrown(int x, int y, int z) {
         return (cell(x >> 1, z >> 1) & 3) != 0;
      }

      public boolean filled(int x, int y, int z) {
         return (cell(x, z + y) & 7) == 0;
      }
   };

   static void release(java.lang.reflect.Method rel, List<SnowField.Quad> q) {
      try {
         rel.invoke(null, q);
      } catch (ReflectiveOperationException e) {
         throw new RuntimeException(e);
      }
   }

   public static void main(String[] a) {
      com.sun.management.ThreadMXBean tb = (com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
      java.lang.reflect.Method rel;
      try {
         rel = SnowField.class.getDeclaredMethod("release", List.class);
      } catch (NoSuchMethodException e) {
         rel = null;
      }
      boolean pool = rel != null;
      long all = 0;
      int blocks = 0, quads = 0, diff = 0;
      long bytes = 0;
      for (int round = 0; round < 3; round++) {
         for (int x = -40; x < 40; x++) {
            for (int z = -40; z < 40; z++) {
               int y = ground(x, z) + 1;
               int layers = FIELD.snow(x, y, z);
               long s = tb.getCurrentThreadAllocatedBytes();
               List<SnowField.Quad> fresh = SnowField.build(FIELD, x, y, z, layers, null, (x & 1) == 0);
               List<SnowField.Quad> crown = CrownSnow.build(CROWNS, x, y, z, layers);
               long e = tb.getCurrentThreadAllocatedBytes() - s;
               long h1 = hash(fresh), h2 = hash(crown);
               if (pool) {
                  // what SmoothSnowModel does after baking: the quads go back, and the next blocks reuse them
                  release(rel, fresh);
                  release(rel, crown);
                  List<SnowField.Quad> again = SnowField.build(FIELD, x, y, z, layers, null, (x & 1) == 0);
                  List<SnowField.Quad> crown2 = CrownSnow.build(CROWNS, x, y, z, layers);
                  if (hash(again) != h1 || hash(crown2) != h2) diff++;
                  release(rel, again);
                  release(rel, crown2);
               }
               if (round == 0) all = all * 31 + h1 * 7 + h2;
               if (round == 2) {
                  bytes += e;
                  blocks++;
                  quads += fresh.size() + crown.size();
               }
            }
         }
      }
      System.out.printf(Locale.ROOT, "pool %s: %d snow blocks, %.1f quads/block (field + crown), %.0f B allocated per block, %d blocks differ, all-blocks hash %016x%n", pool, blocks,
         quads / (double)blocks, bytes / (double)blocks, diff, all);
   }
}
