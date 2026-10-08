package com.formaworks.frontierhunts.client.tree;
// [perf2] offline harness: javac -d /tmp/b -cp <compiled classes>:/home/claude/fh/orig62.jar tools/perf/TreeLodBench.java tools/perf/<this>.java; java -cp /tmp/b:<classes>:/home/claude/fh/orig62.jar com.formaworks.frontierhunts.client.tree.<this> [args]
import java.util.*;
import java.util.concurrent.*;
public final class ConcTest {
   public static void main(String[] a) throws Exception {
      java.lang.reflect.Method m = TreeLodBench.class.getDeclaredMethod("fixtures"); m.setAccessible(true);
      List<?> fx = (List<?>) m.invoke(null);
      java.lang.reflect.Field fw = fx.get(0).getClass().getDeclaredField("world"); fw.setAccessible(true);
      java.lang.reflect.Field fxx = fx.get(0).getClass().getDeclaredField("x"); fxx.setAccessible(true);
      java.lang.reflect.Field fz = fx.get(0).getClass().getDeclaredField("z"); fz.setAccessible(true);
      // one world identity for all fixtures would mix worlds; use each fixture's own world (cache identity = world)
      ExecutorService ex = Executors.newFixedThreadPool(6);
      long[] before = new long[com.formaworks.frontierhunts.perf.client.PerfStats.COUNT];
      java.lang.reflect.Method snap = com.formaworks.frontierhunts.perf.client.PerfStats.class.getDeclaredMethod("snapshot", long[].class); snap.setAccessible(true);
      for (int budget = 0; budget < 2; budget++) {
         if (budget == 1) { TreeGrowth.configureBudget(100_000, 20_000); TreeGrowth.focus(0, 0, true); }
         snap.invoke(null, (Object) before);
         int mismatches = 0, nulls = 0;
         for (int round = 0; round < 3; round++) {
            for (Object f : fx) {
               TreeGrowth.World w = (TreeGrowth.World) fw.get(f); int x = fxx.getInt(f), z = fz.getInt(f);
               if (round == 0) TreeGrowth.clear();
               List<Future<TreeGrowth.Tree>> res = new ArrayList<>();
               for (int t = 0; t < 6; t++) { final int dy = t % 3; res.add(ex.submit(() -> TreeGrowth.lookup(w, x, 1 + dy, z, null, TreeGrowth.LOD_NEAR, true))); }
               TreeGrowth.Tree first = null;
               for (Future<TreeGrowth.Tree> r : res) { TreeGrowth.Tree t = r.get(30, TimeUnit.SECONDS); if (t == null) { nulls++; continue; } if (first == null) first = t; else if (first != t) mismatches++; }
            }
         }
         long[] after = new long[before.length]; snap.invoke(null, (Object) after);
         System.out.printf("budget=%d grown=%d shared=%d evicted=%d distinct-tree-mismatches=%d nulls=%d stats=%s%n", budget,
            after[0]-before[0], after[2]-before[2], after[3]-before[3], mismatches, nulls, Arrays.toString(TreeGrowth.cacheStats()));
      }
      ex.shutdown();
   }
}
