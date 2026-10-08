package com.formaworks.frontierhunts.client.tree;
// [perf3] Offline model of fast travel over a dense forest with the real TreeGrowth cache (real growths of the 56
// fixture trees, tiled on a 14-block grid) and TreeLod's Ultra bands: a flight at a given speed, then hovering where
// it landed. It counts growths, growth CPU, regrowths for a level of detail, evictions and bytes allocated, per phase.
// Model (per tick, 20/s): a tree's sections are built when it enters the render distance (160), when its level of
// detail changes (TreeLod's hysteresis bands, at most 6 trees per tick, nearest first) and at random (light and block
// updates: 5% of the trees in range per second). A build does what TrunkModel.treeCell does: look the tree up with
// TreeLod.growNeed, regrow it when the cached growth lacks the level drawn, then "bake" it (float quads released, and
// handed to the pool when the build has one). Budget scaled to this forest's density (410 trees in range vs 1,114 in
// the 1.2.8 log): 1.33M quads.
// javac -d /tmp/b -cp <classes>:<cp62> tools/perf/TreeLodBench.java tools/perf/FlightSim.java
// java -Xmx1500m -cp /tmp/b:<classes>:<cp62> com.formaworks.frontierhunts.client.tree.FlightSim [speed b/s] [flight s] [hover s]
import java.util.*;

public final class FlightSim {
   static final int SP = 14;
   static final double RANGE = 160;

   static final class Forest implements TreeGrowth.World {
      final List<TreeLodBench.Blue> worlds = new ArrayList<>();
      final List<int[]> at = new ArrayList<>();

      int pick(int gx, int gz) {
         long h = TreeShape.mix(gx * 0x9E3779B97F4A7C15L ^ gz * 0xC2B2AE3D27D4EB4FL);
         return (int)Math.floorMod(h, (long)worlds.size());
      }

      private TreeLodBench.Blue w;
      private int lx, lz;

      private void map(int x, int z) {
         int gx = Math.floorDiv(x, SP), gz = Math.floorDiv(z, SP);
         int i = pick(gx, gz);
         this.w = worlds.get(i);
         this.lx = x - (gx * SP + SP / 2) + at.get(i)[0];
         this.lz = z - (gz * SP + SP / 2) + at.get(i)[1];
      }

      public synchronized int kind(int x, int y, int z) {
         if (y <= 0) return TreeShape.GROUND;
         map(x, z);
         return w.kind(lx, y, lz);
      }

      public synchronized int axis(int x, int y, int z) {
         map(x, z);
         return w.axis(lx, y, lz);
      }

      public int light(int x, int y, int z) {
         return 15 << 20;
      }

      public synchronized boolean conifer(int x, int y, int z) {
         map(x, z);
         return w.conifer(lx, y, lz);
      }

      public synchronized Object species(int x, int y, int z) {
         map(x, z);
         return w.species(lx, y, lz);
      }

      public synchronized int crownForm(int x, int y, int z) {
         map(x, z);
         return w.crownForm(lx, y, lz);
      }
   }

   static final class Cell {
      final int gx, gz;
      final double x, z;
      int target = -1;
      boolean dirty;
      int grown; // growths of this tree so far (a second one after its first is waste: evicted, or regrown for a level)

      Cell(int gx, int gz) {
         this.gx = gx;
         this.gz = gz;
         this.x = gx * SP + SP / 2 + 0.5;
         this.z = gz * SP + SP / 2 + 0.5;
      }
   }

   static java.lang.reflect.Method pure; // TreeLod.growNeed(double...) when this build has it
   static java.lang.reflect.Method recycle;

   static float f(String name) throws Exception {
      java.lang.reflect.Field fl = TreeLod.class.getDeclaredField(name);
      fl.setAccessible(true);
      return fl.getFloat(null);
   }

   static float nearIn, nearOut, impIn, impOut;

   static int growNeed(double bx, double bz, double cx, double cz, double vx, double vz) throws Exception {
      if (pure != null) return (int)pure.invoke(null, bx, bz, cx, cz, vx, vz);
      double dx = bx - cx, dz = bz - cz, d2 = dx * dx + dz * dz; // the pre-perf3 rule
      float both = nearOut + 4, card = impOut + 2;
      return d2 < both * both ? TreeGrowth.LOD_NEAR : d2 < (double)card * card ? TreeGrowth.LOD_FAR : TreeGrowth.LOD_IMPOSTOR;
   }

   static int bits(int lod) {
      return lod == 0 ? TreeGrowth.LOD_NEAR : lod == 1 ? TreeGrowth.LOD_FAR : TreeGrowth.LOD_IMPOSTOR;
   }

   static long[] g0 = new long[com.formaworks.frontierhunts.perf.client.PerfStats.COUNT], g1 = new long[g0.length];
   static int repeats;

   static TreeGrowth.Tree lookupCounted(Forest forest, int x, int z, int need, boolean any, java.lang.reflect.Method snap, long[] before, Cell c) throws Exception {
      snap.invoke(null, (Object)before);
      TreeGrowth.Tree t = TreeGrowth.lookup(forest, x, 1, z, null, need, any);
      snap.invoke(null, (Object)g1);
      if (g1[0] > before[0]) {
         if (c.grown > 0) repeats++;
         c.grown++;
      }
      return t;
   }

   public static void main(String[] a) throws Exception {
      double speed = a.length > 0 ? Double.parseDouble(a[0]) : 54.6;
      double flight = a.length > 1 ? Double.parseDouble(a[1]) : 20, hover = a.length > 2 ? Double.parseDouble(a[2]) : 20;
      java.lang.reflect.Method fxm = TreeLodBench.class.getDeclaredMethod("fixtures");
      fxm.setAccessible(true);
      List<?> fx = (List<?>)fxm.invoke(null);
      java.lang.reflect.Field fw = fx.get(0).getClass().getDeclaredField("world"), fxx = fx.get(0).getClass().getDeclaredField("x"), fz = fx.get(0).getClass().getDeclaredField("z");
      fw.setAccessible(true);
      fxx.setAccessible(true);
      fz.setAccessible(true);
      Forest forest = new Forest();
      for (Object o : fx) {
         forest.worlds.add((TreeLodBench.Blue)fw.get(o));
         forest.at.add(new int[]{fxx.getInt(o), fz.getInt(o)});
      }
      java.lang.reflect.Method conf = TreeLod.class.getDeclaredMethod("configure", float.class, float.class, int.class);
      conf.setAccessible(true);
      conf.invoke(null, 48F, 160F, 6); // Ultra
      nearIn = f("NEAR_IN");
      nearOut = f("NEAR_OUT");
      impIn = f("impIn");
      impOut = f("impOut");
      try {
         pure = TreeLod.class.getDeclaredMethod("growNeed", double.class, double.class, double.class, double.class, double.class, double.class);
         pure.setAccessible(true);
      } catch (NoSuchMethodException e) {
         pure = null;
      }
      try {
         recycle = TreeGrowth.class.getDeclaredMethod("recycle", List[].class);
         recycle.setAccessible(true);
      } catch (NoSuchMethodException e) {
         recycle = null;
      }
      java.lang.reflect.Method range = null;
      try {
         range = TreeGrowth.class.getDeclaredMethod("focusRange", double.class);
         range.setAccessible(true);
      } catch (NoSuchMethodException e) {
         range = null;
      }
      System.out.printf(Locale.ROOT, "bands: full %.0f/%.0f, cutout %.0f/%.0f; look-ahead %s, pool %s, range-aware trim %s; speed %.1f b/s%n", nearIn, nearOut,
         impIn, impOut, pure != null, recycle != null, range != null, speed);
      TreeGrowth.clear();
      TreeGrowth.configureBudget(1_330_000, 370_000);
      if (range != null) range.invoke(null, RANGE + 32);
      java.lang.reflect.Method snap = com.formaworks.frontierhunts.perf.client.PerfStats.class.getDeclaredMethod("snapshot", long[].class);
      snap.setAccessible(true);
      com.sun.management.ThreadMXBean tb = (com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
      Map<Long, Cell> cells = new HashMap<>();
      Random rnd = new Random(42);
      double cx = 0, cz = 0;
      int flightTicks = (int)(flight * 20), hoverTicks = (int)(hover * 20);
      long[] s0 = new long[com.formaworks.frontierhunts.perf.client.PerfStats.COUNT];
      long[] s1 = new long[s0.length];
      String[] phase = {"flight", "hover (landed)"};
      int regrown = 0;
      long builds = 0;
      Set<TreeGrowth.Tree> baked = Collections.newSetFromMap(new IdentityHashMap<>());
      for (int p = 0; p < 2; p++) {
         snap.invoke(null, (Object)s0);
         long alloc0 = tb.getCurrentThreadAllocatedBytes(), cpu0 = tb.getCurrentThreadCpuTime();
         int regrown0 = regrown, repeats0 = repeats;
         long builds0 = builds;
         int ticks = p == 0 ? flightTicks : hoverTicks;
         long peakQuads = 0;
         for (int tick = 0; tick < ticks; tick++) {
            double vx = p == 0 ? speed : 0, vz = 0;
            cx += vx / 20;
            TreeGrowth.focus(cx, cz, true);
            // cells in range: load new, drop far
            int ga = (int)Math.floor((cx - RANGE) / SP), gb = (int)Math.floor((cx + RANGE) / SP), h0 = (int)Math.floor((cz - RANGE) / SP), h1 = (int)Math.floor((cz + RANGE) / SP);
            List<Cell> build = new ArrayList<>();
            for (int gx = ga; gx <= gb; gx++) {
               for (int gz = h0; gz <= h1; gz++) {
                  long key = (long)gx << 32 | gz & 0xFFFFFFFFL;
                  Cell c = cells.get(key);
                  double dx = gx * SP + SP / 2 + 0.5 - cx, dz = gz * SP + SP / 2 + 0.5 - cz;
                  if (dx * dx + dz * dz > RANGE * RANGE) continue;
                  if (c == null) {
                     cells.put(key, c = new Cell(gx, gz));
                     double d = Math.sqrt(dx * dx + dz * dz);
                     c.target = d < (nearIn + nearOut) / 2 ? 0 : d < (impIn + impOut) / 2 ? 1 : 2;
                     build.add(c);
                  }
               }
            }
            final double fcx = cx, fcz = cz;
            cells.values().removeIf(c -> (c.x - fcx) * (c.x - fcx) + (c.z - fcz) * (c.z - fcz) > (RANGE + 48) * (RANGE + 48));
            // retarget (TreeLod's hysteresis) and random rebuilds
            List<Cell> stale = new ArrayList<>();
            for (Cell c : cells.values()) {
               double dx = c.x - cx, dz = c.z - cz, d2 = dx * dx + dz * dz;
               if (d2 > RANGE * RANGE) continue;
               int want = c.target;
               double[] out2 = {nearOut * nearOut, impOut * impOut}, in2 = {nearIn * nearIn, impIn * impIn};
               while (want < 2 && d2 > out2[want]) want++;
               while (want > 0 && d2 < in2[want - 1]) want--;
               if (want != c.target) {
                  c.target = want;
                  c.dirty = true;
               }
               if (c.dirty) stale.add(c);
               else if (rnd.nextDouble() < 0.05 / 20) build.add(c);
            }
            stale.sort(Comparator.comparingDouble(c -> (c.x - fcx) * (c.x - fcx) + (c.z - fcz) * (c.z - fcz)));
            for (int i = 0; i < Math.min(6, stale.size()); i++) {
               stale.get(i).dirty = false;
               build.add(stale.get(i));
            }
            for (Cell c : build) {
               builds++;
               int x = c.gx * SP + SP / 2, z = c.gz * SP + SP / 2;
               int need = growNeed(x + 0.5, z + 0.5, cx, cz, vx, vz);
               TreeGrowth.Tree t = lookupCounted(forest, x, z, need, true, snap, g0, c);
               if (t == null) continue;
               if (!t.has(bits(c.target))) {
                  TreeGrowth.forget(t);
                  regrown++;
                  t = lookupCounted(forest, x, z, bits(c.target) | need, false, snap, g0, c);
                  if (t == null) continue;
               }
               if (baked.add(t)) {
                  // bake: the cells' float quads are released (and pooled when this build has the pool)
                  for (var m : List.of(t.cells, t.far, t.impostor)) {
                     for (var l : m.values()) {
                        if (recycle != null) recycle.invoke(null, (Object)new List[]{l});
                        l.clear();
                     }
                  }
               }
            }
            peakQuads = Math.max(peakQuads, TreeGrowth.cacheStats()[1]);
         }
         snap.invoke(null, (Object)s1);
         long alloc = tb.getCurrentThreadAllocatedBytes() - alloc0, cpu = tb.getCurrentThreadCpuTime() - cpu0;
         double secs = ticks / 20.0;
         long grown = s1[0] - s0[0];
         System.out.printf(Locale.ROOT, "%-15s %4.0f s: grown %5d (%5.1f/s, %4d of them a tree grown before), grow CPU %6.0f ms/s, regrown for level %4d, evicted %4d, builds %6d, "
               + "allocated %6.1f MB/s, sim CPU %5.0f ms/s, cache peak %4dk quads, now %s%n",
            phase[p], secs, grown, grown / secs, repeats - repeats0, (s1[1] - s0[1]) / 1000.0 / secs, regrown - regrown0, s1[3] - s0[3], builds - builds0, alloc / 1e6 / secs,
            cpu / 1e6 / secs, peakQuads / 1000, Arrays.toString(TreeGrowth.cacheStats()));
      }
   }
}
