package com.formaworks.frontierhunts.client.tree;
// [perf3] Bit-exact fingerprint of every grown tree (all 56 TreeLodBench fixtures, grown for NEAR, FAR and IMPOSTOR):
// every quad's 32 floats, texture, light and normal, in holder/list order, plus members, expected kinds, sockets and
// the tree header. Run before and after a change and diff the output: any geometry difference shows up.
// javac -d /tmp/b -cp <classes>:<cp62> tools/perf/TreeLodBench.java tools/perf/GrowHash.java
// java -cp /tmp/b:<classes>:<cp62> com.formaworks.frontierhunts.client.tree.GrowHash [rounds]
// rounds > 1 grows every fixture again (warm thread-local scratch / pools) and checks it hashes the same.
import java.util.*;

public final class GrowHash {
   static long h;

   static void mix(long v) {
      h ^= v;
      h *= 0x100000001B3L;
      h ^= h >>> 29;
   }

   static void quads(Map<Long, List<TreeShape.Quad>> m) {
      List<Long> keys = new ArrayList<>(m.keySet());
      Collections.sort(keys);
      mix(keys.size());
      for (long k : keys) {
         mix(k);
         List<TreeShape.Quad> l = m.get(k);
         mix(l.size());
         for (TreeShape.Quad q : l) {
            mix(q.texture);
            mix(q.light);
            mix(Float.floatToRawIntBits(q.nx));
            mix(Float.floatToRawIntBits(q.ny));
            mix(Float.floatToRawIntBits(q.nz));
            for (float f : q.v) mix(Float.floatToRawIntBits(f));
         }
      }
   }

   public static long tree(TreeGrowth.Tree t) {
      h = 0xCBF29CE484222325L;
      if (t == null) return 0;
      quads(t.cells);
      quads(t.far);
      quads(t.impostor);
      // NEAR and FAR share quad objects where both levels draw the same mesh: the sharing must be kept too
      IdentityHashMap<TreeShape.Quad, Boolean> near = new IdentityHashMap<>();
      for (var l : t.cells.values()) for (var q : l) near.put(q, true);
      int shared = 0;
      for (var l : t.far.values()) for (var q : l) if (near.containsKey(q)) shared++;
      mix(shared);
      mix(t.lods);
      mix(t.anchor);
      mix(Float.floatToRawIntBits(t.anchorX));
      mix(Float.floatToRawIntBits(t.anchorZ));
      mix(t.quadCount);
      mix(t.conifer ? 1 : 0);
      mix(t.form);
      mix(t.foliagePosition);
      mix(Objects.hashCode(t.species));
      mix(Objects.hashCode(t.foliageSpecies));
      for (long m : t.members) mix(m);
      for (int e : t.expect) mix(e);
      List<Long> sk = new ArrayList<>(t.sockets.keySet());
      Collections.sort(sk);
      for (long k : sk) {
         mix(k);
         for (float f : t.sockets.get(k)) mix(Float.floatToRawIntBits(f));
      }
      return h;
   }

   static boolean recycle;

   static void recycleCell(TreeGrowth.Tree t, long k) {
      List<TreeShape.Quad> e = List.of();
      List<TreeShape.Quad> n = t.cells.getOrDefault(k, e), f = t.far.getOrDefault(k, e), i = t.impostor.getOrDefault(k, e);
      try {
         java.lang.reflect.Method m = TreeGrowth.class.getDeclaredMethod("recycle", List[].class);
         m.invoke(null, (Object)new List[]{n, f, i});
      } catch (NoSuchMethodException x) {
         return; // no pool in this build
      } catch (Exception x) {
         throw new RuntimeException(x);
      }
      for (List<TreeShape.Quad> l : List.of(n, f, i)) if (l instanceof ArrayList<TreeShape.Quad> al) al.clear();
   }

   public static void main(String[] a) throws Exception {
      int rounds = a.length > 0 ? Integer.parseInt(a[0]) : 1;
      recycle = a.length > 1 && a[1].equals("recycle");
      java.lang.reflect.Method m = TreeLodBench.class.getDeclaredMethod("fixtures");
      m.setAccessible(true);
      List<?> fx = (List<?>)m.invoke(null);
      java.lang.reflect.Field fw = fx.get(0).getClass().getDeclaredField("world"), fn = fx.get(0).getClass().getDeclaredField("name");
      java.lang.reflect.Field fxx = fx.get(0).getClass().getDeclaredField("x"), fz = fx.get(0).getClass().getDeclaredField("z");
      fw.setAccessible(true);
      fn.setAccessible(true);
      fxx.setAccessible(true);
      fz.setAccessible(true);
      long all = 0;
      long quads = 0;
      Map<String, Long> first = new HashMap<>();
      int mismatches = 0;
      for (int r = 0; r < rounds; r++) {
         for (int need : new int[]{TreeGrowth.LOD_NEAR, TreeGrowth.LOD_FAR, TreeGrowth.LOD_IMPOSTOR}) {
            for (Object f : fx) {
               TreeGrowth.clear();
               TreeGrowth.World w = (TreeGrowth.World)fw.get(f);
               // ask from the foot log and from a leaf high in the crown (a different first block, the same tree)
               TreeGrowth.Tree t = TreeGrowth.lookup(w, fxx.getInt(f), 1, fz.getInt(f), null, need);
               long hv = tree(t);
               if (recycle && t != null) {
                  // what TrunkModel does once a cell is baked: the cell's float quads go back to the pool, so the
                  // next growths on this thread are made of reused quads (round 2+ must still hash the same)
                  for (long k : new ArrayList<>(t.cells.keySet())) recycleCell(t, k);
                  for (long k : new ArrayList<>(t.far.keySet())) recycleCell(t, k);
                  for (long k : new ArrayList<>(t.impostor.keySet())) recycleCell(t, k);
               }
               String key = fn.get(f) + "@" + fxx.getInt(f) + "," + fz.getInt(f) + " need=" + need;
               if (r == 0) {
                  first.put(key, hv);
                  System.out.printf("%-34s %016x quads=%d%n", key, hv, t == null ? 0 : t.quadCount);
                  all = all * 31 + hv;
                  quads += t == null ? 0 : t.quadCount;
               } else if (first.get(key) != hv) {
                  mismatches++;
                  System.out.println("ROUND " + r + " MISMATCH " + key);
               }
            }
         }
      }
      System.out.printf("ALL %016x quads=%d rounds=%d mismatches=%d%n", all, quads, rounds, mismatches);
   }
}
