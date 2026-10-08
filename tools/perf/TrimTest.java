package com.formaworks.frontierhunts.client.tree;
// [perf2] offline harness: javac -d /tmp/b -cp <compiled classes>:/home/claude/fh/orig62.jar tools/perf/TreeLodBench.java tools/perf/<this>.java; java -cp /tmp/b:<classes>:/home/claude/fh/orig62.jar com.formaworks.frontierhunts.client.tree.<this> [args]
import java.util.*;
import java.util.concurrent.*;
public final class TrimTest {
   public static void main(String[] a) throws Exception {
      java.lang.reflect.Method m = TreeLodBench.class.getDeclaredMethod("fixtures"); m.setAccessible(true);
      List<?> fx = (List<?>) m.invoke(null);
      java.lang.reflect.Field fw = fx.get(0).getClass().getDeclaredField("world"); fw.setAccessible(true);
      java.lang.reflect.Field fxx = fx.get(0).getClass().getDeclaredField("x"); fxx.setAccessible(true);
      java.lang.reflect.Field fz = fx.get(0).getClass().getDeclaredField("z"); fz.setAccessible(true);
      TreeLodBench.Blue all = new TreeLodBench.Blue();
      int[][] at = new int[fx.size()][2];
      for (int i = 0; i < fx.size(); i++) {
         TreeLodBench.Blue w = (TreeLodBench.Blue) fw.get(fx.get(i));
         int ox = i * 48 - fxx.getInt(fx.get(i)), oz = -fz.getInt(fx.get(i));
         for (var e : w.kind.entrySet()) { long p = e.getKey(); int x=(int)(p>>38), y=(int)(p<<52>>52), z=(int)(p<<26>>38);
            long q = TreeGrowth.pack(x+ox,y,z+oz); all.kind.put(q, e.getValue()); all.axis.put(q, w.axis.getOrDefault(p,1)); all.name.put(q, w.name.getOrDefault(p,"oak_log")); }
         at[i][0] = i * 48; at[i][1] = 0;
      }
      TreeGrowth.clear();
      TreeGrowth.configureBudget(150_000, 20_000);
      TreeGrowth.focus(0, 0, true);
      java.lang.reflect.Method snap = com.formaworks.frontierhunts.perf.client.PerfStats.class.getDeclaredMethod("snapshot", long[].class); snap.setAccessible(true);
      long[] s0 = new long[com.formaworks.frontierhunts.perf.client.PerfStats.COUNT]; snap.invoke(null, (Object) s0);
      ExecutorService ex = Executors.newFixedThreadPool(4);
      List<Future<?>> fs = new ArrayList<>();
      int nulls[] = {0};
      for (int r = 0; r < 4; r++) for (int[] p : at) fs.add(ex.submit(() -> { if (TreeGrowth.lookup(all, p[0], 1, p[1], null, TreeGrowth.LOD_NEAR, true) == null) synchronized (nulls) { nulls[0]++; } }));
      for (Future<?> f : fs) f.get(60, TimeUnit.SECONDS);
      ex.shutdown();
      long[] s1 = new long[s0.length]; snap.invoke(null, (Object) s1);
      long[] c = TreeGrowth.cacheStats();
      System.out.printf("grown=%d shared=%d evicted=%d trimMs=%.1f nulls=%d cache=%s%n", s1[0]-s0[0], s1[2]-s0[2], s1[3]-s0[3], (s1[4]-s0[4])/1000.0, nulls[0], Arrays.toString(c));
      // recount from scratch: trim re-syncs counters; check they match the live set
      TreeGrowth.configureBudget(1, 1); TreeGrowth.lookup(all, at[0][0], 1, at[0][1], null, TreeGrowth.LOD_NEAR, true);
      System.out.println("after tiny budget: " + Arrays.toString(TreeGrowth.cacheStats()));
   }
}
