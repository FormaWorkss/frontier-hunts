package com.formaworks.frontierhunts.client.tree;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [trees2] Blocks broken out of grown realistic trees ("scars").
 *
 * <p>A realistic tree is grown from its blocks as a blueprint. Before, breaking any of its logs or leaves made the cached
 * tree invalid and the tree was grown again from what was left: without its rooted foot log (or split in two by a
 * missing trunk log, or with too few leaves left on a part) the blueprint was rejected and every block of the tree fell
 * back to the plain cube models, the whole tree at once ("trees turn blocky when you break them"). Even when the regrowth
 * worked it cost a full growth (5-7 ms) in the player's important section rebuild for every broken block.
 *
 * <p>Now a member of a cached tree that turns into air (broken, decayed, burnt) is remembered as a <b>ghost</b> with the
 * kind, axis and species it had. The tree stays valid: nothing is regrown, the broken block draws nothing (it is air)
 * and every other block keeps drawing exactly its baked share of the tree, so the rest of the tree stays as it was,
 * floating parts included, like vanilla. Any later growth of the tree (cache eviction, shader switch with released
 * quads, level-of-detail regrowth) reads the world through {@link #over}, which shows the ghosts in place of the air,
 * so the same tree grows again and the ghosts' shares are simply not drawn.
 *
 * <p>A ghost is ignored once a real block stands in its cell again, and the ghosts of a tree are dropped once none of
 * its blocks is left ({@link #sweep}). Ghosts live for the session of one client world. Pure Java (no Minecraft types).
 */
public final class TreeScars {
   /** One broken-out member: what the tree's blueprint had in this cell. */
   static final class Ghost {
      final int kind, axis, form;
      final boolean conifer;
      final Object species;
      final Scar scar;

      Ghost(int kind, int axis, Object species, boolean conifer, int form, Scar scar) {
         this.kind = kind;
         this.axis = axis;
         this.species = species;
         this.conifer = conifer;
         this.form = form;
         this.scar = scar;
      }
   }

   /** The broken-out cells of one tree (by its anchor) and the tree's members, to notice when nothing is left of it. */
   static final class Scar {
      final long anchor;
      final long[] members;
      final int[] kinds;
      /** Every member cell broken out of the tree (its ghost may be gone: a new block stands there, not this tree's). */
      final Set<Long> ghosts = ConcurrentHashMap.newKeySet();
      /**
       * Some log of the tree still stands. Once the last one is gone (felled) the ghosts no longer shape any growth:
       * a sapling replanted (and bonemealed) at the stump grows its own tree, not the old one. The felled tree's cached
       * growth keeps drawing its lingering leaves until they decay.
       */
      volatile boolean logsAlive = true;
      volatile long deadSince;

      Scar(long anchor, long[] members, int[] expect) {
         this.anchor = anchor;
         this.members = members;
         this.kinds = new int[expect.length];
         for (int i = 0; i < expect.length; i++) this.kinds[i] = expect[i] & 7;
      }

      /** Whether any log of the tree stands (not broken out) in world w. */
      boolean anyLog(TreeGrowth.World w) {
         for (int i = 0; i < this.members.length; i++) {
            if (this.kinds[i] != TreeShape.LOG) continue;
            long m = this.members[i];
            if (this.ghosts.contains(m)) continue;
            if (w.kind((int)(m >> 38), (int)(m << 52 >> 52), (int)(m << 26 >> 38)) == TreeShape.LOG) return true;
         }
         return false;
      }
   }

   /** Ghosts kept at most (about 100 bytes each); past it a broken tree is regrown from what is left, as before. */
   static int maxGhosts = 120_000;
   private static final ConcurrentHashMap<Long, Ghost> GHOSTS = new ConcurrentHashMap<>();
   private static final ConcurrentHashMap<Long, Scar> SCARS = new ConcurrentHashMap<>();
   /** Cheap pre-filter for the hot path (most cells asked about are plain air): bits are only ever set until clear(). */
   private static final int FILTER_BITS = 1 << 18;
   private static final java.util.concurrent.atomic.AtomicLongArray FILTER = new java.util.concurrent.atomic.AtomicLongArray(FILTER_BITS / 64);
   private static volatile java.lang.ref.WeakReference<Object> owner = new java.lang.ref.WeakReference<>(null);
   private static volatile Overlay overlay;

   private TreeScars() {
   }

   private static int bit(long cell) {
      return TreeGrowth.LongMap.hash(cell) & (FILTER_BITS - 1);
   }

   private static boolean maybe(long cell) {
      int b = bit(cell);
      return (FILTER.get(b >>> 6) & 1L << (b & 63)) != 0;
   }

   /** Whether a ghost is remembered in this cell (whatever stands there now). */
   static boolean ghost(long cell) {
      return maybe(cell) && GHOSTS.containsKey(cell);
   }

   static int count() {
      return GHOSTS.size();
   }

   /** Forgets every ghost (another world). */
   public static void clear() {
      GHOSTS.clear();
      SCARS.clear();
      for (int i = 0; i < FILTER.length(); i++) FILTER.set(i, 0);
   }

   /** Follows the world: ghosts belong to one loaded world. */
   private static void follow(Object world) {
      if (world == owner.get()) return;
      synchronized (TreeScars.class) {
         if (world == owner.get()) return;
         clear();
         owner = new java.lang.ref.WeakReference<>(world);
      }
   }

   /**
    * Remembers that member {@code index} of a cached tree is now air. False when the ghost store is full (the caller
    * then treats the tree as changed, as before).
    */
   static boolean record(TreeGrowth.Tree tree, int index, long cell, Object world, TreeGrowth.World w) {
      follow(world);
      if (GHOSTS.containsKey(cell)) return true;
      if (GHOSTS.size() >= maxGhosts) return false;
      int expect = tree.expect[index];
      int kind = expect & 7;
      if (kind != TreeShape.LOG && kind != TreeShape.LEAVES) return false;
      Scar scar = SCARS.computeIfAbsent(tree.anchor, a -> new Scar(a, tree.members, tree.expect));
      Ghost g = new Ghost(kind, kind == TreeShape.LOG ? expect >>> 3 & 3 : 1,
         kind == TreeShape.LOG ? tree.species : tree.foliageSpecies != null ? tree.foliageSpecies : tree.species, tree.conifer, tree.form, scar);
      int b = bit(cell);
      long mask = 1L << (b & 63);
      while (true) {
         long old = FILTER.get(b >>> 6);
         if ((old & mask) != 0 || FILTER.compareAndSet(b >>> 6, old, old | mask)) break;
      }
      GHOSTS.put(cell, g);
      scar.ghosts.add(cell);
      if (kind == TreeShape.LOG && scar.logsAlive && !scar.anyLog(w)) {
         scar.logsAlive = false; // the last log of the tree: felled
         scar.deadSince = System.nanoTime();
      }
      return true;
   }

   /** Whether this member cell of the tree anchored at {@code anchor} has been broken out of it (ever, this session). */
   static boolean broken(long anchor, long cell) {
      if (!maybe(cell)) return false;
      Scar scar = SCARS.get(anchor);
      return scar != null && scar.ghosts.contains(cell);
   }

   /** Whether the tree whose member this ghost cell is still has a standing log (its ghosts shape growths). */
   static boolean standingTree(long cell) {
      if (!maybe(cell)) return true;
      Ghost g = GHOSTS.get(cell);
      return g == null || g.scar.logsAlive;
   }

   /** The world TreeGrowth reads: the live world with the ghosts standing in for the air they left. */
   static TreeGrowth.World over(TreeGrowth.World w) {
      follow(w.cacheIdentity());
      if (GHOSTS.isEmpty()) return w;
      Overlay o = overlay;
      if (o == null || o.base != w) overlay = o = new Overlay(w);
      return o;
   }

   /** The ghost standing in this cell now (the real block there is air), or null. */
   private static Ghost standing(TreeGrowth.World base, int x, int y, int z) {
      long cell = TreeGrowth.pack(x, y, z);
      if (!maybe(cell)) return null;
      Ghost g = GHOSTS.get(cell);
      return g != null && g.scar.logsAlive && base.kind(x, y, z) == TreeShape.AIR ? g : null;
   }

   private static final class Overlay implements TreeGrowth.World {
      final TreeGrowth.World base;

      Overlay(TreeGrowth.World base) {
         this.base = base;
      }

      @Override public int kind(int x, int y, int z) {
         int k = this.base.kind(x, y, z);
         if (k != TreeShape.AIR) return k;
         long cell = TreeGrowth.pack(x, y, z);
         if (!maybe(cell)) return k;
         Ghost g = GHOSTS.get(cell);
         return g == null || !g.scar.logsAlive ? k : g.kind;
      }

      @Override public int axis(int x, int y, int z) {
         Ghost g = standing(this.base, x, y, z);
         return g != null ? g.axis : this.base.axis(x, y, z);
      }

      @Override public int light(int x, int y, int z) {
         return this.base.light(x, y, z);
      }

      @Override public boolean conifer(int x, int y, int z) {
         Ghost g = standing(this.base, x, y, z);
         return g != null ? g.conifer : this.base.conifer(x, y, z);
      }

      @Override public Object species(int x, int y, int z) {
         Ghost g = standing(this.base, x, y, z);
         return g != null ? g.species : this.base.species(x, y, z);
      }

      @Override public int crownForm(int x, int y, int z) {
         Ghost g = standing(this.base, x, y, z);
         return g != null ? g.form : this.base.crownForm(x, y, z);
      }

      @Override public boolean construction(int x, int y, int z) {
         return standing(this.base, x, y, z) == null && this.base.construction(x, y, z);
      }

      @Override public boolean natural(int x, int y, int z) {
         Ghost g = standing(this.base, x, y, z);
         return g != null || this.base.natural(x, y, z);
      }

      @Override public boolean distant(int x, int z) {
         return this.base.distant(x, z);
      }

      @Override public Object cacheIdentity() {
         return this.base.cacheIdentity();
      }

      @Override public float groundHeight(float x, float z, float nearY) {
         return this.base.groundHeight(x, z, nearY);
      }
   }

   /** Whether a world block is loaded (unloaded cells read as air and must not count as broken). */
   @FunctionalInterface
   public interface Loaded {
      boolean at(int x, int y, int z);
   }

   private static Iterator<Scar> cursor;

   /**
    * Drops ghosts whose cell holds a real block again, and the whole scar of a tree none of whose blocks is left (all
    * broken or decayed). At most {@code budget} block reads per call (client thread, a little every second).
    */
   public static void sweep(TreeGrowth.World base, Loaded loaded, int budget) {
      follow(base.cacheIdentity());
      if (SCARS.isEmpty()) return;
      if (cursor == null || !cursor.hasNext()) cursor = SCARS.values().iterator();
      while (budget > 0 && cursor.hasNext()) {
         Scar scar = cursor.next();
         boolean alive = false, unknown = false;
         for (Long cell : scar.ghosts) {
            long c = cell;
            int x = (int)(c >> 38), y = (int)(c << 52 >> 52), z = (int)(c << 26 >> 38);
            budget--;
            if (!loaded.at(x, y, z)) {
               unknown = true;
               continue;
            }
            if (base.kind(x, y, z) != TreeShape.AIR) {
               // a real block stands there again: it is what growths see now (the cell stays broken for this tree)
               GHOSTS.remove(c);
            }
         }
         boolean log = false;
         for (int i = 0; i < scar.members.length && !log; i++) {
            long m = scar.members[i];
            if (scar.ghosts.contains(m)) continue;
            int x = (int)(m >> 38), y = (int)(m << 52 >> 52), z = (int)(m << 26 >> 38);
            budget--;
            if (!loaded.at(x, y, z)) {
               unknown = true;
               break;
            }
            int k = base.kind(x, y, z);
            if (k == TreeShape.LOG || k == TreeShape.LEAVES) {
               alive = true;
               log |= k == TreeShape.LOG && scar.kinds[i] == TreeShape.LOG;
            }
         }
         if (!unknown) {
            if (log != scar.logsAlive) {
               scar.logsAlive = log;
               scar.deadSince = System.nanoTime();
            }
         }
         // nothing of the tree left, or felled long ago (its last leaves have decayed or joined another tree)
         if (!alive && !unknown || !scar.logsAlive && System.nanoTime() - scar.deadSince > 600_000_000_000L) {
            for (Long cell : scar.ghosts) GHOSTS.remove(cell);
            SCARS.remove(scar.anchor, scar);
         }
      }
   }
}
