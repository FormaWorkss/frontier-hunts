package com.formaworks.frontierhunts.client.tree;
// [perf2] offline harness: javac -d /tmp/b -cp <compiled classes>:/home/claude/fh/orig62.jar tools/perf/TreeLodBench.java tools/perf/<this>.java; java -cp /tmp/b:<classes>:/home/claude/fh/orig62.jar com.formaworks.frontierhunts.client.tree.<this> [args]
import java.util.*;
public final class GrowProf {
   public static void main(String[] a) throws Exception {
      java.lang.reflect.Method m = TreeLodBench.class.getDeclaredMethod("fixtures"); m.setAccessible(true);
      List<?> fx = (List<?>) m.invoke(null);
      int need = a[0].equals("imp") ? TreeGrowth.LOD_IMPOSTOR : a[0].equals("far") ? TreeGrowth.LOD_FAR : TreeGrowth.LOD_NEAR;
      int rounds = Integer.parseInt(a[1]);
      java.lang.reflect.Field fw = fx.get(0).getClass().getDeclaredField("world"); fw.setAccessible(true);
      java.lang.reflect.Field fxx = fx.get(0).getClass().getDeclaredField("x"); fxx.setAccessible(true);
      java.lang.reflect.Field fz = fx.get(0).getClass().getDeclaredField("z"); fz.setAccessible(true);
      long total = 0; int n = 0;
      for (int r = 0; r < rounds; r++) {
         long s = java.lang.management.ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();
         for (Object f : fx) { TreeGrowth.clear(); TreeGrowth.lookup((TreeGrowth.World) fw.get(f), fxx.getInt(f), 1, fz.getInt(f), null, need); n++; }
         long e = java.lang.management.ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime() - s;
         if (r >= rounds / 2) total += e;
         if (r % 5 == 0) System.out.printf("round %d: %.2f ms/tree%n", r, e / 1e6 / fx.size());
      }
      System.out.printf("steady %.3f ms/tree%n", total / 1e6 / (fx.size() * (rounds - rounds / 2)));
   }
}
