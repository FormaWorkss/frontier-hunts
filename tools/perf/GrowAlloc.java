package com.formaworks.frontierhunts.client.tree;
// [perf2] offline harness: javac -d /tmp/b -cp <compiled classes>:/home/claude/fh/orig62.jar tools/perf/TreeLodBench.java tools/perf/<this>.java; java -cp /tmp/b:<classes>:/home/claude/fh/orig62.jar com.formaworks.frontierhunts.client.tree.<this> [args]
import java.util.*;
public final class GrowAlloc {
   public static void main(String[] a) throws Exception {
      java.lang.reflect.Method m = TreeLodBench.class.getDeclaredMethod("fixtures"); m.setAccessible(true);
      List<?> fx = (List<?>) m.invoke(null);
      com.sun.management.ThreadMXBean tb=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
      java.lang.reflect.Field fw = fx.get(0).getClass().getDeclaredField("world"); fw.setAccessible(true);
      java.lang.reflect.Field fxx = fx.get(0).getClass().getDeclaredField("x"); fxx.setAccessible(true);
      java.lang.reflect.Field fz = fx.get(0).getClass().getDeclaredField("z"); fz.setAccessible(true);
      for (String k : new String[]{"imp","far","near"}) {
      int need = k.equals("imp") ? TreeGrowth.LOD_IMPOSTOR : k.equals("far") ? TreeGrowth.LOD_FAR : TreeGrowth.LOD_NEAR;
      for (int r = 0; r < 6; r++) {
         long s = tb.getCurrentThreadAllocatedBytes();
         for (Object f : fx) { TreeGrowth.clear(); TreeGrowth.lookup((TreeGrowth.World) fw.get(f), fxx.getInt(f), 1, fz.getInt(f), null, need); }
         long e = tb.getCurrentThreadAllocatedBytes() - s;
         if (r==5) System.out.printf("%s: %.2f MB allocated per tree%n", k, e / 1e6 / fx.size());
      }}
   }
}
