package com.formaworks.frontierhunts.client.tree;
// [perf3] Per grown tree: bytes allocated (ThreadMXBean), CPU ms and quads, for impostor-only, far and near growths of
// the 56 TreeLodBench fixtures. 'output' is the part of the allocation that is the quads themselves (TreeShape.Quad +
// its float[32] = 176 B each, kept until baked); 'scratch' is everything else (garbage once the growth returns).
// javac -d /tmp/b -cp <classes>:<cp62> tools/perf/TreeLodBench.java tools/perf/GrowBench.java
// java -Xmx1g -cp /tmp/b:<classes>:<cp62> com.formaworks.frontierhunts.client.tree.GrowBench [rounds] [recycle]
// 'recycle' hands every grown quad back to TreeGrowth.recycle (what TrunkModel does once a cell is baked) when the
// pool exists, to measure the steady state in game.
import java.util.*;

public final class GrowBench {
   public static void main(String[] a) throws Exception {
      int rounds = a.length > 0 ? Integer.parseInt(a[0]) : 12;
      boolean recycle = a.length > 1 && a[1].equals("recycle");
      java.lang.reflect.Method m = TreeLodBench.class.getDeclaredMethod("fixtures");
      m.setAccessible(true);
      List<?> fx = (List<?>)m.invoke(null);
      com.sun.management.ThreadMXBean tb = (com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
      java.lang.reflect.Field fw = fx.get(0).getClass().getDeclaredField("world");
      java.lang.reflect.Field fxx = fx.get(0).getClass().getDeclaredField("x");
      java.lang.reflect.Field fz = fx.get(0).getClass().getDeclaredField("z");
      fw.setAccessible(true);
      fxx.setAccessible(true);
      fz.setAccessible(true);
      java.lang.reflect.Method rec = null;
      if (recycle) {
         try {
            rec = TreeGrowth.class.getDeclaredMethod("recycle", Collection.class);
            rec.setAccessible(true);
         } catch (NoSuchMethodException e) {
            System.out.println("(no TreeGrowth.recycle: pool not present)");
         }
      }
      System.out.println("level    MB/tree  scratch MB/tree  B/quad  scratch B/quad  CPU ms/tree  quads/tree");
      for (String k : new String[]{"imp", "far", "near"}) {
         int need = k.equals("imp") ? TreeGrowth.LOD_IMPOSTOR : k.equals("far") ? TreeGrowth.LOD_FAR : TreeGrowth.LOD_NEAR;
         long bytes = 0, cpu = 0, quads = 0, n = 0;
         for (int r = 0; r < rounds; r++) {
            for (Object f : fx) {
               TreeGrowth.clear();
               long s = tb.getCurrentThreadAllocatedBytes(), c = tb.getCurrentThreadCpuTime();
               TreeGrowth.Tree t = TreeGrowth.lookup((TreeGrowth.World)fw.get(f), fxx.getInt(f), 1, fz.getInt(f), null, need);
               long e = tb.getCurrentThreadAllocatedBytes() - s, ce = tb.getCurrentThreadCpuTime() - c;
               if (r >= rounds / 2) {
                  bytes += e;
                  cpu += ce;
                  quads += t == null ? 0 : t.quadCount;
                  n++;
               }
               if (rec != null && t != null) {
                  IdentityHashMap<TreeShape.Quad, Boolean> all = new IdentityHashMap<>();
                  for (var l : t.cells.values()) for (var q : l) all.put(q, true);
                  for (var l : t.far.values()) for (var q : l) all.put(q, true);
                  for (var l : t.impostor.values()) for (var q : l) all.put(q, true);
                  for (var l : t.cells.values()) l.clear();
                  for (var l : t.far.values()) l.clear();
                  for (var l : t.impostor.values()) l.clear();
                  rec.invoke(null, all.keySet());
               }
            }
         }
         double perTree = bytes / (double)n, q = quads / (double)n, out = q * 176;
         System.out.printf(Locale.ROOT, "%-6s %9.3f %16.3f %7.0f %15.0f %12.3f %11.0f%n", k, perTree / 1e6, (perTree - out) / 1e6, perTree / q,
            (perTree - out) / q, cpu / 1e6 / n, q);
      }
      // [perf3] the cache-hit path: every log and leaves block of a rebuilt section looks its (cached) tree up
      long calls = 0, lb = 0, lc = 0;
      Object build = new Object();
      for (int r = 0; r < 4; r++) {
         for (Object f : fx) {
            TreeGrowth.clear();
            TreeGrowth.World w = (TreeGrowth.World)fw.get(f);
            TreeGrowth.Tree t = TreeGrowth.lookup(w, fxx.getInt(f), 1, fz.getInt(f), null, TreeGrowth.LOD_NEAR);
            if (t == null) continue;
            long s = tb.getCurrentThreadAllocatedBytes(), c = tb.getCurrentThreadCpuTime();
            for (long mm : t.members) {
               TreeGrowth.lookup(w, (int)(mm >> 38), (int)(mm << 52 >> 52), (int)(mm << 26 >> 38), build, TreeGrowth.LOD_NEAR, true);
            }
            if (r >= 2) {
               lb += tb.getCurrentThreadAllocatedBytes() - s;
               lc += tb.getCurrentThreadCpuTime() - c;
               calls += t.members.length;
            }
         }
      }
      System.out.printf(Locale.ROOT, "cached lookup (per block of a rebuilt section): %.1f B and %.0f ns per call%n", lb / (double)calls, lc / (double)calls);
   }
}
