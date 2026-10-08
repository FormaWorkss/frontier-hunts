package com.formaworks.frontierhunts.client.tree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Whole realistic trees. Instead of reading a tree log by log (which turns block-shaped branches
 * into fat, kinked limbs), the world's tree is used only as a blueprint: where it stands, how tall
 * the trunk is, how wide the base is and where its leaves are. A new tree is grown into that space:
 * <ul>
 * <li>a trunk along the log column(s) that tapers like a real one (conifers to a point, broadleaves
 * into their limbs), with a flared base and buttress roots;</li>
 * <li>species-shaped, recursively forked limbs carrying ragged foliage sprays from {@link TreeCrown};</li>
 * <li>branch thickness tapers from the supporting stem down to the terminal twigs.</li>
 * </ul>
 * The finished mesh is split among the tree's own log and leaf blocks (each block draws the pieces
 * that fall in or next to it), so chunk sections draw their own part of the tree. Wood is always
 * hosted by logs and foliage by the tree's own leaves (shader packs sway geometry by its hosting
 * block). Grown trees are cached within a memory budget and re-checked against the live world on
 * each section rebuild, so a tree is grown again only when it changed. Pure Java (no Minecraft
 * types) so the world harness runs it on real saves.
 *
 * <p>Distance level of detail (.63): besides the full tree (NEAR) a growth holds a reduced one (FAR,
 * about 28% of the quads): the same trunk radius and taper with fewer sides and rings, no twigs,
 * thinner-sided boughs, and 30% of the foliage cards enlarged around their centres so the crown keeps
 * its outline and leaf coverage. A tree seen only from afar is grown with its FAR level alone. The
 * NEAR geometry is exactly what it was before.
 */
public final class TreeGrowth {
   public interface World {
      /** TreeShape kinds: AIR, LOG, LEAVES, GROUND, SOLID. */
      int kind(int x, int y, int z);

      /** Axis of the log here: 0 x, 1 y, 2 z. */
      int axis(int x, int y, int z);

      /** Packed light (block << 4 | sky << 20). */
      int light(int x, int y, int z);

      /** Needle tree (by the log's species). */
      boolean conifer(int x, int y, int z);

      /** The log block's identity (for its bark texture). */
      Object species(int x, int y, int z);
      /** 0 broad crown, 1 slender birch/aspen, 2 spreading blossom crown. */
      default int crownForm(int x,int y,int z){return 0;}
      default boolean construction(int x,int y,int z){return false;}
      /** [trees2] Leaves grown by nature (not placed by a player); logs count as natural. */
      default boolean natural(int x,int y,int z){return true;}

      /**
       * Whether a tree first grown for the block at x, z by a lookup that did not say which levels it
       * draws is only seen from afar (it is then grown with its distant level alone).
       */
      default boolean distant(int x, int z) { return false; }

      /** Identity of the loaded world, never just a dimension name or seed. */
      default Object cacheIdentity() { return this; }

      /** Rendered ground at a root sample; the game adapter also follows smoothed slopes. */
      default float groundHeight(float x, float z, float nearY) {
         int bx=(int)Math.floor(x), bz=(int)Math.floor(z);
         for(int y=(int)Math.floor(nearY)+2;y>=(int)Math.floor(nearY)-5;y--) {
            if(kind(bx,y,bz)==GROUND) return y+1;
         }
         return nearY;
      }
   }

   /** Distance level of detail: full geometry near the camera, a reduced crown and wood further away. */
   public static final int LOD_NEAR = 1, LOD_FAR = 2, LOD_ALL = LOD_NEAR | LOD_FAR;
   /** [perf] The most distant level: a handful of crossed cutout cards ({@link TreeImpostor}). Every growth holds it. */
   public static final int LOD_IMPOSTOR = 4, LOD_EVERY = LOD_ALL | LOD_IMPOSTOR;
   /**
    * Distant foliage cards are this much larger around their own centres. With TreeCrown.FAR_KEEP of the
    * cards this keeps the crown's projected leaf coverage (0.96-1.01 of the full crown from ten views,
    * TreeForestBench lod) and its outline (within about 3%).
    */
   static float farCardScale = 1.55F;
   /** Limbs thinner than this at their base (twigs, needle-fan shoots) are not drawn at distance. */
   static float farMinLimb = 0.035F;


   /** A grown tree: its quads, sorted into the blocks that draw them. */
   public static final class Tree {
      final long stamp = System.nanoTime();
      final boolean none;
      /** Full-detail (NEAR) quads per holder block; empty when the tree was grown for distance only. */
      final Map<Long, List<TreeShape.Quad>> cells = new HashMap<>();
      /** Reduced (FAR) quads per holder block. Quads both levels draw are the same objects in both maps. */
      final Map<Long, List<TreeShape.Quad>> far = new HashMap<>();
      /** [perf] Impostor quads per holder block (a few holders per tree). */
      final Map<Long, List<TreeShape.Quad>> impostor = new HashMap<>();
      final Map<Long, float[]> sockets = new HashMap<>();
      /** LOD_* bits of the geometry this growth holds. */
      public int lods = LOD_ALL;
      /** Foot of the lowest stem (packed) and its centre: the whole tree switches detail as one. */
      public long anchor;
      public float anchorX, anchorZ;
      Object species;
      Object foliageSpecies;
      long foliagePosition;
      boolean conifer;
      /** [trees2] Crown form of the blueprint's root (for ghosts of broken-out members). */
      int form;
      /** [trees2] Until then (nanoTime) leaf relighting near a fresh break is accepted instead of regrowing the tree. */
      volatile long lightGrace = System.nanoTime();
      long[] members = new long[0];
      /** Per member: the kind it had when grown, and (leaves) its light, to notice edits and relights. */
      int[] expect = new int[0];
      int quadCount;
      Object world;
      volatile long lastUse = System.nanoTime();
      volatile long checkedEpoch;
      volatile boolean retired;
      /** Model-side cache of baked quads (Minecraft types live in the models, not in this pure class). */
      public volatile Object baked;
      /** [seasons] SeasonalFoliage's cache of this tree's seasonal cells for the current season stage. */
      public volatile Object seasonal;
      /** [1.2.0] frosted foliage cells (SeasonalFoliage), per frost level */
      public volatile Object frost;
      /** Some cells' float quads were dropped after baking; a re-bake (shader switch) must regrow. */
      public volatile boolean released;
      /** Cells whose float quads were dropped after baking (an empty list there is not an empty cell). */
      public final Set<Long> releasedCells = ConcurrentHashMap.newKeySet();

      Tree(boolean none) {
         this.none = none;
      }

      /** Quads (in block-local coordinates) for the block at x, y, z - empty when it draws none. */
      public List<TreeShape.Quad> quadsAt(int x, int y, int z) {
         List<TreeShape.Quad> q = this.cells.get(pack(x, y, z));
         return q == null ? List.of() : q;
      }

      public Object species() {
         return this.species;
      }

      /** Distance-level quads for the block at x, y, z (block-local), empty when it draws none there. */
      public List<TreeShape.Quad> farAt(int x, int y, int z) {
         List<TreeShape.Quad> q = this.far.get(pack(x, y, z));
         return q == null ? List.of() : q;
      }

      /** [perf] Impostor quads for the block at x, y, z (block-local), empty when it draws none there. */
      public List<TreeShape.Quad> impostorAt(int x, int y, int z) {
         List<TreeShape.Quad> q = this.impostor.get(pack(x, y, z));
         return q == null ? List.of() : q;
      }

      /** Whether this growth holds every level in {@code lodBits}. */
      public boolean has(int lodBits) {
         return (this.lods & lodBits) == lodBits;
      }

      public Map<Long, List<TreeShape.Quad>> farCells() {
         return this.far;
      }

      /** Every block that draws part of this tree, and the quads each draws (for baking once). */
      public Map<Long, List<TreeShape.Quad>> allCells() {
         return this.cells;
      }

      public int quadCount() {
         return this.quadCount;
      }
   }

   private static final class CacheKey { // [perf3] was a record; Probe below looks entries up without allocating one
      final Object world;
      final long cell;

      CacheKey(Object world, long cell) {
         this.world = world;
         this.cell = cell;
      }

      @Override public boolean equals(Object other) {
         return other instanceof CacheKey k && world == k.world && cell == k.cell;
      }
      @Override public int hashCode() {
         return hash(world, cell);
      }

      static int hash(Object world, long cell) {
         return 31 * System.identityHashCode(world) + LongMap.hash(cell);
      }
   }

   /**
    * [perf3] A reusable stand-in for a {@link CacheKey} in map reads and removals (never stored as a key): every log and
    * leaves block of every section rebuild looks its tree up, and each lookup allocated a key. ConcurrentHashMap
    * compares a probe as {@code probe.equals(storedKey)}, which matches exactly the CacheKey of the same world and cell.
    */
   private static final class Probe {
      Object world;
      long cell;

      Probe at(Object world, long cell) {
         this.world = world;
         this.cell = cell;
         return this;
      }

      @Override public boolean equals(Object other) {
         return other instanceof CacheKey k && world == k.world && cell == k.cell;
      }
      @Override public int hashCode() {
         return CacheKey.hash(world, cell);
      }
   }

   private static final ThreadLocal<Probe> PROBE = ThreadLocal.withInitial(Probe::new); // [perf3]
   private static final ConcurrentHashMap<CacheKey, Tree> BY_CELL = new ConcurrentHashMap<>();
   /** [perf2] Every cached (published, not retired) tree once: trimming walks trees, not their member entries. */
   private static final Set<Tree> LIVE = ConcurrentHashMap.newKeySet();
   /**
    * [perf2] Growths in progress, by log component (world, lowest log): a second chunk-mesh worker that needs the same
    * tree (its other sections are often rebuilt in the same tick, on other workers) waits for the first growth
    * instead of growing the tree a second time.
    */
   private static final ConcurrentHashMap<CacheKey, java.util.concurrent.CompletableFuture<Tree>> GROWING = new ConcurrentHashMap<>();
   /** [perf2] Longest a worker waits for another worker's growth of the same tree before growing it itself. */
   private static final long SHARE_WAIT_MS = 250;
   /**
    * A grown tree is kept until the memory budget needs its room. Every chunk-section rebuild
    * re-checks each tree it draws against the live world (its blocks' kinds and the light of its
    * leaves, well under a millisecond) instead of regrowing it. Before .62 a tree older than 2.5 s
    * was regrown for every section rebuild that touched it, which dominated chunk-build time.
    */
   private static final long NEGATIVE_TTL = 10_000_000_000L;
   /**
    * Cached geometry budget: about 180 bytes per (baked) quad. [perf2] Set by {@link #configureBudget} from the tree
    * detail and the heap (TreeLod): 700k (~125 MB) was a fraction of what the Ultra bands keep in view (a full tree is
    * ~6-8k quads, a dense forest has hundreds inside the full-detail band), so trees were evicted and grown again on
    * every rebuild of their sections.
    */
   static volatile long quadBudget = 700_000L;
   private static final AtomicLong CACHED_QUADS = new AtomicLong();
   /**
    * [perf] Cached trees are also bounded by their member blocks (each is a cache entry): impostor-only
    * trees cost few quads, so the quad budget alone would let their entries grow past the map's limit.
    */
   static volatile long memberBudget = 120_000L;
   private static final AtomicLong CACHED_MEMBERS = new AtomicLong();
   /** [perf2] Room for negative entries (loose leaves, stumps, timber) besides the members, before they are purged. */
   private static final long NEGATIVE_ROOM = 100_000L;
   private static final AtomicLong LAST_PURGE = new AtomicLong();
   private static final java.util.concurrent.atomic.AtomicBoolean TRIMMING = new java.util.concurrent.atomic.AtomicBoolean();
   /**
    * [perf2] Where the camera is (published by TreeLod every tick): eviction drops the trees furthest from it first,
    * keeping the full-detail band (where rebuilds are frequent and regrowth is dearest) cached.
    */
   private static volatile double focusX, focusZ;
   private static volatile boolean focusValid;
   /** [perf2] A tree drawn this recently is never evicted for room (its other sections are probably being built). */
   private static final long RECENT_NS = 1_500_000_000L;
   private static final AtomicLong EPOCH = new AtomicLong(1);
   /** Per builder thread: the chunk section it is currently building and that build's epoch. */
   /** Per builder thread: {section, epoch, build token, end of the last lookup (ns)}. */
   private static final ThreadLocal<long[]> SECTION = ThreadLocal.withInitial(() -> new long[]{Long.MIN_VALUE, 0, 0, 0});
   /** A pause this long between two lookups on one thread means a new section build started. */
   private static final long BUILD_GAP = 2_000_000L;
   private static volatile java.lang.ref.WeakReference<Object> lastWorld = new java.lang.ref.WeakReference<>(null);

   private static final int AIR = TreeShape.AIR, LOG = TreeShape.LOG, LEAVES = TreeShape.LEAVES, GROUND = TreeShape.GROUND, SOLID = TreeShape.SOLID;

   static long pack(int x, int y, int z) {
      return ((long)x & 0x3FFFFFF) << 38 | ((long)z & 0x3FFFFFF) << 12 | (long)(y & 0xFFF);
   }

   private static int ux(long p) {
      return (int)(p >> 38);
   }

   private static int uy(long p) {
      return (int)(p << 52 >> 52);
   }

   private static int uz(long p) {
      return (int)(p << 26 >> 38);
   }

   /** [perf2] Sets the cache budgets (quads, member blocks); a lower budget trims on the next growth. */
   static void configureBudget(long quads, long members) {
      quadBudget = Math.max(100_000L, quads);
      memberBudget = Math.max(20_000L, members);
   }

   /** [perf2] The camera position eviction measures from (horizontal), or none. */
   static void focus(double x, double z, boolean valid) {
      focusX = x;
      focusZ = z;
      focusValid = valid;
   }

   /** [perf3] Horizontal distance from the focus beyond which a cached tree is outside the drawn world (0: unknown). */
   private static volatile double focusRange;

   /** [perf3] Sets {@link #focusRange} (TreeLod: render distance plus a margin). */
   static void focusRange(double blocks) {
      focusRange = blocks;
   }

   /**
    * [sign] The drawn stem socket {centre x, y, z, radius} of an already grown (cached) tree at this log, without growing
    * anything (safe to call every frame from the render thread); null when no grown tree is cached there.
    */
   static float[] cachedSocket(int x, int y, int z) {
      Object world = LiveWorld.INSTANCE.cacheIdentity();
      if (world == null) return null;
      Tree t = BY_CELL.get(PROBE.get().at(world, pack(x, y, z))); // [perf3] no key allocated
      if (t == null || t.none || t.retired) return null;
      float[] socket = t.sockets.get(pack(x, y, z));
      return socket == null || socket.length < 4 ? null : socket;
   }

   /** The grown tree this log or leaves block belongs to, or null (fallen logs, stumps, cabins, loose leaves). */
   public static Tree lookup(World w, int x, int y, int z) {
      return lookup(w, x, y, z, null);
   }

   /**
    * @param build the block getter of the chunk-section build asking (a new one, or a pause, starts a new
    *              validation epoch, so a section rebuilt after an edit never reuses a stale tree)
    */
   public static Tree lookup(World w, int x, int y, int z, Object build) {
      return lookup(w, x, y, z, build, 0);
   }

   /**
    * @param need LOD_* bits the caller will draw: a cached growth lacking one is grown again (a tree
    *             needed only at distance is grown with its distant geometry alone). 0 accepts any cached
    *             growth and grows both levels, or the distant one when {@link World#distant} says so.
    */
   public static Tree lookup(World w, int x, int y, int z, Object build, int need) {
      return lookup(w, x, y, z, build, need, false);
   }

   /**
    * [perf2] @param anyCached return a valid cached growth even when it lacks some of {@code need} (the caller checks
    *        the level it really draws and asks again with exactly that); {@code need} then only shapes a new growth.
    *        Without this, a lookup's distance guess dropped cached trees that the section did not need regrown.
    */
   public static Tree lookup(World w, int x, int y, int z, Object build, int need, boolean anyCached) {
      long[] current = SECTION.get();
      try {
         return lookup0(w, x, y, z, build, current, need & LOD_EVERY, anyCached); // [perf] impostor bit
      } finally {
         current[3] = System.nanoTime();
      }
   }

   private static Tree lookup0(World w, int x, int y, int z, Object build, long[] current, int need, boolean anyCached) {
      long k = pack(x, y, z);
      w = TreeScars.over(w); // [trees2] broken-out members stand in for the air they left
      Object world = w.cacheIdentity();
      if (world != lastWorld.get()) {
         synchronized (TreeGrowth.class) {
            if (world != lastWorld.get()) {
               clear();
               lastWorld = new java.lang.ref.WeakReference<>(world);
            }
         }
      }
      // A section build is one pass on one thread: a new section, a new build getter, or a pause
      // between lookups starts a new check epoch.
      long section = pack(x >> 4, y >> 4, z >> 4);
      long token = build == null ? 0 : System.identityHashCode(build);
      if (current[0] != section || current[2] != token || System.nanoTime() - current[3] > BUILD_GAP) {
         current[0] = section;
         current[2] = token;
         current[1] = EPOCH.incrementAndGet();
      }
      long epoch = current[1];
      long now = System.nanoTime();
      bound(now);
      for (int round = 0; ; round++) {
         Tree t = BY_CELL.get(PROBE.get().at(world, k)); // [perf3] a probe, not a new key per lookup (re-set each round)
         if (t != null) {
            if (t.none) {
               if (now - t.stamp < NEGATIVE_TTL) return null;
            } else if (!t.retired && !t.has(need) && !anyCached) {
               forget(t); // grown for distance only; this block is now drawn in full
               t = null;
            } else if (!t.retired) {
               if (t.checkedEpoch != epoch) {
                  if (valid(w, t, x, y, z)) {
                     t.checkedEpoch = epoch;
                  } else {
                     forget(t);
                     t = null;
                  }
               }
               if (t != null) {
                  t.lastUse = now;
                  return t;
               }
            }
         }
         int growMask = growMask(w, x, z, need); // [perf]
         TreeGrowth growth = new TreeGrowth(w, growMask, scratch(growMask)); // [perf3] per-thread scratch
         growth.world = world;
         growth.noClaim = round > 0; // [perf2] after one wait, grow it here whatever happens
         Tree built = null;
         long started = System.nanoTime();
         try {
            try {
               built = growth.grow(x, y, z);
            } catch (RuntimeException e) {
               built = null;
            }
            if (growth.shared != null || growth.adopted != null) {
               // [perf2] another worker is growing (or just grew) this very tree: wait for it, then take it from the cache
               if (growth.adopted != null || awaitShared(growth.shared) != null) {
                  com.formaworks.frontierhunts.perf.client.PerfStats.inc(com.formaworks.frontierhunts.perf.client.PerfStats.TREES_SHARED);
               }
               continue;
            }
            com.formaworks.frontierhunts.perf.client.PerfStats.inc(com.formaworks.frontierhunts.perf.client.PerfStats.TREES_GROWN);
            com.formaworks.frontierhunts.perf.client.PerfStats.since(com.formaworks.frontierhunts.perf.client.PerfStats.TREE_GROW_US, started);
            if (built == null) {
               // Every block of a rejected log component would be rejected the same way.
               Tree no = new Tree(true);
               BY_CELL.put(new CacheKey(world, k), no);
               if (growth.componentRejected) for (long c : growth.comp.keys()) BY_CELL.put(new CacheKey(world, c), no);
               return null;
            }
            built.world = world;
            built.checkedEpoch = epoch;
            built.lastUse = now;
            boolean mine = false;
            for (long m : built.members) {
               Tree old = BY_CELL.put(new CacheKey(world, m), built);
               if (old != null && old != built && !old.none) forget(old);
               mine |= m == k;
            }
            LIVE.add(built); // [perf2]
            CACHED_QUADS.addAndGet(built.quadCount);
            CACHED_MEMBERS.addAndGet(built.members.length); // [perf]
            if (built.retired && LIVE.remove(built)) {
               // [perf2] cleared (world change, reload) while it grew: never count or keep it
               CACHED_QUADS.addAndGet(-built.quadCount);
               CACHED_MEMBERS.addAndGet(-built.members.length);
            }
            if (!mine) {
               BY_CELL.put(new CacheKey(world, k), new Tree(true));
               trim();
               return null;
            }
            trim();
            return built;
         } finally {
            growth.release(built); // [perf2] published (or failed): wake the workers waiting for it
            if (growth.out.size() > 0) {
               com.formaworks.frontierhunts.perf.client.PerfStats.add(com.formaworks.frontierhunts.perf.client.PerfStats.TREE_QUADS, growth.out.size()); // [perf3]
            }
            growth.scratch.end(); // [perf3] the thread's scratch is free for its next growth
            QuadPool pool = POOL.get();
            if (pool.misses > 0) {
               com.formaworks.frontierhunts.perf.client.PerfStats.add(com.formaworks.frontierhunts.perf.client.PerfStats.TREE_QUADS_NEW, pool.misses);
               pool.misses = 0;
            }
         }
      }
   }

   /** [perf2] Waits (bounded) for another worker's growth of the same tree; null when it failed or took too long. */
   private static Tree awaitShared(java.util.concurrent.CompletableFuture<Tree> other) {
      try {
         return other.get(SHARE_WAIT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         return null;
      } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException | RuntimeException e) {
         return null;
      }
   }

   /** [perf2] Claims this growth's log component (lowest log); false when another worker is growing it already. */
   private boolean claim(long lowestLog) {
      if (this.noClaim || this.world == null) return true;
      CacheKey ck = new CacheKey(this.world, lowestLog);
      java.util.concurrent.CompletableFuture<Tree> mine = new java.util.concurrent.CompletableFuture<>();
      java.util.concurrent.CompletableFuture<Tree> other = GROWING.putIfAbsent(ck, mine);
      if (other != null) {
         this.shared = other;
         return false;
      }
      this.claim = ck;
      this.future = mine;
      return true;
   }

   /** [perf2] Hands the finished growth (or null) to every worker waiting for it and drops the claim. */
   private void release(Tree result) {
      if (this.claim == null) return;
      GROWING.remove(this.claim, this.future);
      this.future.complete(result);
      this.claim = null;
   }

   /**
    * [perf2] Keeps the cell map bounded without ever clearing the whole cache in one go (the old 200k cliff dropped
    * every cached tree at once, and every section then regrew its trees): expired negative entries are purged at most
    * four times a second; only a map past twice its budget is cleared.
    */
   private static void bound(long now) {
      long members = memberBudget;
      int size = BY_CELL.size();
      if (size <= members + NEGATIVE_ROOM) return;
      long last = LAST_PURGE.get();
      if (now - last > 250_000_000L && LAST_PURGE.compareAndSet(last, now)) {
         // Negative entries (loose leaves, stumps, structures) have no quad cost; bound their count.
         BY_CELL.entrySet().removeIf(e -> e.getValue().none && now - e.getValue().stamp > NEGATIVE_TTL);
         if (BY_CELL.size() > members + NEGATIVE_ROOM) {
            // still over: young negatives too (they are cheap to re-derive), then the trees themselves
            BY_CELL.entrySet().removeIf(e -> e.getValue().none);
            trim();
         }
      }
      if (BY_CELL.size() > members * 2 + NEGATIVE_ROOM * 2) clear();
   }

   /**
    * [perf] Levels a growth builds for a lookup needing {@code need}: the impostor always (a dozen quads),
    * the distant crown unless only the impostor is needed, the full tree when it is needed.
    */
   static int growMask(World w, int x, int z, int need) {
      if ((need & LOD_NEAR) != 0) return LOD_EVERY;
      if ((need & LOD_FAR) != 0) return LOD_FAR | LOD_IMPOSTOR;
      if (need == LOD_IMPOSTOR) return LOD_IMPOSTOR;
      return w.distant(x, z) ? LOD_FAR | LOD_IMPOSTOR : LOD_EVERY;
   }

   /** [trees2] How long after a break leaf relighting is accepted without regrowing (the light engine catches up). */
   private static final long LIGHT_GRACE = 4_000_000_000L;

   /**
    * Whether a cached tree still matches the live world (no member changed kind; leaves kept their light). [trees2] A
    * member broken out of the tree (now air) does not change it: it is remembered as a ghost ({@link TreeScars}) and the
    * tree keeps drawing the rest of itself, so breaking a tree never regrows it or turns it into block models.
    */
   private static boolean valid(World w, Tree t, int ax, int ay, int az) {
      long[] members = t.members;
      int[] expect = t.expect;
      long now = 0;
      boolean standing = false; // [trees2] a log of the tree itself still stands (not a new block where one was broken)
      for (int i = 0; i < members.length; i++) {
         long m = members[i];
         int x = ux(m), y = uy(m), z = uz(m);
         int kind = w.kind(x, y, z);
         if (kind != (expect[i] & 7)) {
            if (kind == AIR && TreeScars.record(t, i, m, t.world, w)) {
               t.lightGrace = System.nanoTime() + LIGHT_GRACE;
               continue;
            }
            return false;
         }
         if (kind == LOG && !standing && !TreeScars.broken(t.anchor, m)) standing = true;
         if (kind == LEAVES && w.light(x, y, z) >>> 4 != expect[i] >>> 3) {
            if (TreeScars.ghost(m)) continue; // a broken-out leaf's cell is lit like the open air it now is
            if (now == 0) now = System.nanoTime();
            if (now - t.lightGrace < 0) {
               expect[i] = LEAVES | w.light(x, y, z) >>> 4 << 3; // light settling after a break: keep the tree
               continue;
            }
            return false;
         }
      }
      // [trees2] a felled tree keeps drawing its lingering leaves, but a log set where its trunk was (a replanted
      // sapling grown up, a placed log) is a new tree: grow that instead of handing it the old trunk's share
      return standing || w.kind(ax, ay, az) != LOG;
   }

   /** Drops one tree from the cache (it changed, or its room is needed). */
   public static void forget(Tree t) {
      synchronized (t) {
         if (t.retired) return;
         t.retired = true;
      }
      Probe probe = PROBE.get(); // [perf3]
      for (long m : t.members) BY_CELL.remove(probe.at(t.world, m), t);
      if (LIVE.remove(t)) { // [perf2] counted only while it was in the live set
         CACHED_QUADS.addAndGet(-t.quadCount);
         CACHED_MEMBERS.addAndGet(-t.members.length); // [perf]
      }
   }

   /**
    * Keeps the cached geometry within its budget. [perf2] One worker trims at a time (the others carry on; the budget
    * may be exceeded for a moment), it walks the live trees instead of every member entry, and it drops the trees
    * furthest from the camera first: the quad budget among trees holding real geometry (full or distant crowns), the
    * member budget among all. Least recently drawn first without a camera.
    *
    * <p>[perf3] Trees still in the drawn world are no longer evicted just to be grown again. Before, every trim cut the
    * cache to 80% of its budget, furthest first. At Ultra a dense forest keeps more than that inside the render
    * distance (the 1.2.8 log: 3.0-3.3M quads of a 3.6M budget), so each trim evicted visible trees, and the next rebuild
    * of their sections (a level-of-detail swap, a light or block update) grew them again: 23 evictions and ~90 growths a
    * second for 20 s after a flight, with the GC pauses that come with them. Now:
    * <ul>
    * <li>trees beyond the drawn world ({@link #focusRange}: the render distance and a margin) go first, down to 95% of
    * the budget;</li>
    * <li>the trees in range may then use up to {@link #HARD_CAP} times the budget (about 10% of the heap at Ultra
    * instead of 8%) before any of them is evicted, and then only down to 95% of that cap, furthest and least
    * recently built first;</li>
    * <li>a trim that cannot free anything (all in range, under the cap) is not retried for 250 ms.</li>
    * </ul>
    */
   private static void trim() {
      long quadBudget = TreeGrowth.quadBudget, memberBudget = TreeGrowth.memberBudget;
      if (CACHED_QUADS.get() <= quadBudget && CACHED_MEMBERS.get() <= memberBudget) return; // [perf] members too
      long now0 = System.nanoTime();
      long hardQuads = (long)(quadBudget * HARD_CAP), hardMembers = (long)(memberBudget * HARD_CAP);
      // [perf3] in the soft zone (over budget, under the hard cap) a trim found nothing out of range lately: wait
      if (CACHED_QUADS.get() <= hardQuads && CACHED_MEMBERS.get() <= hardMembers && now0 - LAST_IDLE_TRIM.get() < 250_000_000L) return;
      if (!TRIMMING.compareAndSet(false, true)) return;
      long started = System.nanoTime();
      try {
         List<Tree> trees = new ArrayList<>(LIVE.size() + 16);
         long live = 0, members = 0;
         for (Tree t : LIVE) {
            if (t.retired) continue;
            trees.add(t);
            live += t.quadCount;
            members += t.members.length;
         }
         CACHED_QUADS.set(live); // re-sync: clear() and forget() may race with growth
         CACHED_MEMBERS.set(members);
         if (live <= quadBudget && members <= memberBudget) return;
         long target = quadBudget * 95 / 100, memberTarget = memberBudget * 95 / 100;
         long now = System.nanoTime();
         boolean focus = focusValid;
         double fx = focusX, fz = focusZ, range = focusRange;
         double range2 = focus && range > 0 ? range * range : Double.MAX_VALUE;
         int n = trees.size();
         long[] order = new long[n];
         int out = 0; // trees beyond the drawn world
         for (int i = 0; i < n; i++) {
            Tree t = trees.get(i);
            long rank;
            if (focus) {
               double dx = t.anchorX - fx, dz = t.anchorZ - fz, d2 = dx * dx + dz * dz;
               rank = (long)Math.min(Integer.MAX_VALUE, d2); // further = dropped first
               if (d2 > range2) out++;
            } else {
               rank = Math.min(Integer.MAX_VALUE, Math.max(0, (now - t.lastUse) / 1_000_000L)); // older = dropped first
            }
            order[i] = rank << 32 | i;
         }
         java.util.Arrays.sort(order); // ascending: walk from the end
         int evicted = 0;
         // 1. out of range: any of them, furthest first, down to 95% of the budget
         if (out > 0) {
            for (int j = n - 1; j >= n - out; j--) {
               if (CACHED_QUADS.get() <= target && CACHED_MEMBERS.get() <= memberTarget) break;
               Tree t = trees.get((int)order[j]);
               if (t.retired) continue;
               boolean quads = CACHED_QUADS.get() > target && t.quadCount >= 64, room = CACHED_MEMBERS.get() > memberTarget;
               if (!quads && !room) continue;
               forget(t);
               evicted++;
            }
         }
         // 2. in range: only past the hard cap, and only down to 95% of it (never a cliff of visible trees)
         long capQuads = hardQuads * 95 / 100, capMembers = hardMembers * 95 / 100;
         if (CACHED_QUADS.get() > hardQuads || CACHED_MEMBERS.get() > hardMembers) {
            for (int pass = 0; pass < 2; pass++) {
               for (int j = n - 1; j >= 0; j--) {
                  boolean quadsOver = CACHED_QUADS.get() > capQuads, membersOver = CACHED_MEMBERS.get() > capMembers;
                  if (!quadsOver && !membersOver) break;
                  Tree t = trees.get((int)order[j]);
                  if (t.retired) continue;
                  if (!membersOver && t.quadCount < 64) continue; // quad pressure: trees that hold real geometry
                  if (pass == 0 && now - t.lastUse < RECENT_NS) continue; // second pass: recent ones too, if it must
                  forget(t);
                  evicted++;
               }
            }
         }
         if (evicted == 0) LAST_IDLE_TRIM.set(now0);
         com.formaworks.frontierhunts.perf.client.PerfStats.add(com.formaworks.frontierhunts.perf.client.PerfStats.TREES_EVICTED, evicted);
      } finally {
         com.formaworks.frontierhunts.perf.client.PerfStats.since(com.formaworks.frontierhunts.perf.client.PerfStats.TREE_TRIM_US, started);
         TRIMMING.set(false);
      }
   }

   /** [perf3] How far past its budget the cache may grow before trees still in range are evicted. */
   static final double HARD_CAP = 1.25;
   private static final AtomicLong LAST_IDLE_TRIM = new AtomicLong(Long.MIN_VALUE / 2);

   /** Drops every cached tree (a setting or resource change). */
   public static void clear() {
      for (Tree t : BY_CELL.values()) t.retired = true;
      for (Tree t : LIVE) t.retired = true; // [perf2]
      BY_CELL.clear();
      LIVE.clear(); // [perf2]
      CACHED_QUADS.set(0);
      CACHED_MEMBERS.set(0); // [perf]
   }

   /** Number of distinct cached trees and their quads (diagnostics and tests). */
   static long cachedQuads() {
      return CACHED_QUADS.get();
   }

   /** [perf2] Diagnostics: {cached trees, cached quads, quad budget, cached members, member budget, cell entries}. */
   static long[] cacheStats() {
      return new long[]{LIVE.size(), CACHED_QUADS.get(), quadBudget, CACHED_MEMBERS.get(), memberBudget, BY_CELL.size()};
   }

   /** Small open-addressing long map (TreeGrowth's packed cells hash poorly as boxed Longs). */
   static final class LongMap {
      static final long EMPTY = Long.MIN_VALUE;
      long[] keys;
      long[] vals;
      int size;

      LongMap(int capacity) {
         int n = 16;
         while (n < capacity * 2) n <<= 1;
         this.keys = new long[n];
         java.util.Arrays.fill(this.keys, EMPTY);
         this.vals = new long[n];
      }

      static int hash(long key) {
         // murmur3 finaliser: every coordinate bit reaches the low (index) bits
         long h = key ^ key >>> 33;
         h *= 0xFF51AFD7ED558CCDL;
         h ^= h >>> 33;
         h *= 0xC4CEB9FE1A85EC53L;
         return (int)(h ^ h >>> 33);
      }

      private int slot(long key) {
         int mask = this.keys.length - 1;
         int i = hash(key) & mask;
         while (this.keys[i] != EMPTY && this.keys[i] != key) i = i + 1 & mask;
         return i;
      }

      boolean contains(long key) {
         return this.keys[this.slot(key)] == key;
      }

      long get(long key, long otherwise) {
         int i = this.slot(key);
         return this.keys[i] == key ? this.vals[i] : otherwise;
      }

      /** Stores the value; true when the key is new. */
      boolean put(long key, long value) {
         int i = this.slot(key);
         if (this.keys[i] == key) {
            this.vals[i] = value;
            return false;
         }
         this.keys[i] = key;
         this.vals[i] = value;
         if (++this.size * 2 > this.keys.length) this.rehash();
         return true;
      }

      private void rehash() {
         long[] k = this.keys, v = this.vals;
         this.keys = new long[k.length * 2];
         java.util.Arrays.fill(this.keys, EMPTY);
         this.vals = new long[k.length * 2];
         this.size = 0;
         for (int i = 0; i < k.length; i++) if (k[i] != EMPTY) this.put(k[i], v[i]);
      }

      /** The keys, sorted (deterministic iteration). */
      long[] keys() {
         long[] out = new long[this.size];
         int n = 0;
         for (long key : this.keys) if (key != EMPTY) out[n++] = key;
         java.util.Arrays.sort(out);
         return out;
      }

      /** [perf3] The keys, sorted, into {@code out} (grown when too small; the result holds {@link #size} keys). */
      long[] keys(long[] out) {
         if (out.length < this.size) out = new long[Math.max(this.size, out.length * 2)];
         int n = 0;
         for (long key : this.keys) if (key != EMPTY) out[n++] = key;
         java.util.Arrays.sort(out, 0, n);
         return out;
      }

      /**
       * [perf3] Empties the map for reuse (a growth's scratch maps are kept per thread). Lookups never depend on the
       * capacity, so a reused map answers exactly as a new one; one grown huge by a giant tree is shrunk back.
       */
      void clear(int capacity) {
         int n = 16;
         while (n < capacity * 2) n <<= 1;
         if (this.keys.length > Math.max(n, 1 << 14)) {
            this.keys = new long[n];
            this.vals = new long[n];
            java.util.Arrays.fill(this.keys, EMPTY);
         } else if (this.size > 0) {
            java.util.Arrays.fill(this.keys, EMPTY);
         }
         this.size = 0;
      }

      int size() {
         return this.size;
      }
   }

   // ------------------------------------------------------------------ growing
   private final World w;
   private final LongMap comp;        // the tree's connected logs
   private final LongMap leafDist;    // leaves near them -> steps from a log
   private final LongMap own;         // leaves whose quads this tree may place
   private final List<TreeShape.Quad> out;
   /** LOD_* bits of each quad in {@link #out}. */
   private int[] outLod;
   private final LongMap lightCache;
   private boolean conifer;
   private long seed;
   /** Levels this growth builds, and the level(s) the geometry being emitted belongs to. */
   private final int mask;
   private int emit = LOD_ALL;

   /** [perf] Measures the crown's cards for the impostor level. */
   private final TreeImpostor impostor;
   /** [perf] Light the impostor's foliage is drawn with (sampled above the crown). */
   private int impostorLight = 15 << 20;

   /** The whole log component is not a living tree (so each of its logs can be answered at once). */
   boolean componentRejected;
   /** [perf2] In-flight growth sharing: the cache identity, this growth's claim, and another worker's growth to wait for. */
   private Object world;
   private CacheKey claim;
   private java.util.concurrent.CompletableFuture<Tree> future;
   private java.util.concurrent.CompletableFuture<Tree> shared;
   private Tree adopted;
   private boolean noClaim;
   /** [perf3] This growth's working memory (per thread, reused by the next growth on the thread). */
   private final Scratch scratch;

   /**
    * [perf3] A growth's working memory: its block maps, queues, holder indexes, quad list, impostor sampler and geometry
    * buffers. They used to be allocated for every growth (about 0.6 MB for an impostor-only tree, 1.4 MB besides the
    * quads for a full one); now each mesh-worker thread keeps one set and clears it for its next growth. A lookup
    * that finds the thread's scratch still in use (never expected: growths do not nest) takes a fresh one, as before.
    * Every map answers independently of its capacity and every buffer is overwritten before it is read, so a growth
    * from reused scratch is bit-identical to one from new scratch (tools/perf/GrowHash).
    */
   static final class Scratch {
      boolean busy;
      final LongMap comp = new LongMap(64), leafDist = new LongMap(256), own = new LongMap(256), lightCache = new LongMap(1024);
      final LongMap groundCache = new LongMap(64), foreign = new LongMap(16), fdist = new LongMap(64), seen = new LongMap(64);
      final ArrayList<TreeShape.Quad> out = new ArrayList<>(256);
      int[] outLod = new int[256];
      /** Holder cell of each quad in out (placement pass). */
      long[] cellOf = new long[256];
      long[] queue = new long[64], queue2 = new long[256], logs = new long[64], leaves = new long[256], ownLeaves = new long[256], keys = new long[64];
      long[] findQueue = new long[128];
      final TreeImpostor impostor = new TreeImpostor();
      final Holders wood = new Holders(), leaf = new Holders();
      /** Polyline (x, y, z per point) and radii of the limb or trunk being emitted, and a thinned copy for distance. */
      float[] pts = new float[96], radii = new float[32], farPts = new float[96], farRadii = new float[32];
      /** Two rings (x, y, z per side) of the tube being emitted, and per-tube direction scratch. */
      float[] ringA = new float[48], ringB = new float[48];
      final float[] vec = new float[12];

      void begin(int mask) {
         this.busy = true;
         this.comp.clear(64);
         this.leafDist.clear(256);
         this.own.clear(256);
         this.lightCache.clear(1024);
         this.groundCache.clear(64);
         this.out.clear();
         this.impostor.reset();
      }

      void end() {
         this.out.clear(); // never hold on to a finished tree's quads
         this.busy = false;
      }
   }

   private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

   /** [perf3] The calling thread's scratch for a new growth (a fresh one if it is somehow still in use). */
   private static Scratch scratch(int mask) {
      Scratch s = SCRATCH.get();
      if (s.busy) s = new Scratch();
      s.begin(mask);
      return s;
   }

   private TreeGrowth(World w, int mask, Scratch scratch) {
      this.w = w;
      this.mask = mask;
      this.scratch = scratch;
      this.comp = scratch.comp;
      this.leafDist = scratch.leafDist;
      this.own = scratch.own;
      this.lightCache = scratch.lightCache;
      this.groundCache = scratch.groundCache;
      this.impostor = scratch.impostor;
      this.out = scratch.out;
      this.outLod = scratch.outLod;
   }

   private void add(TreeShape.Quad q) {
      int i = this.out.size();
      if (i == this.outLod.length) this.outLod = this.scratch.outLod = java.util.Arrays.copyOf(this.outLod, i * 2); // [perf3] kept in scratch
      this.outLod[i] = this.emit;
      this.out.add(q);
   }

   // ------------------------------------------------------------------ [perf3] float quad pool
   /**
    * [perf3] Float quads handed back by {@link #recycle} once their cell is baked, per thread, for the next growths on
    * that thread. A grown tree's float quads (a Quad and its float[32], 176 bytes each, ~8,000 for a full tree) only live
    * until TrunkModel bakes their cell, then they were garbage: half of all a growth allocated. A pooled quad is reset to
    * exactly the state of a new one before it is used, so the geometry is unchanged.
    */
   private static final class QuadPool {
      final TreeShape.Quad[] items = new TreeShape.Quad[POOL_MAX];
      int size;
      /** Quads allocated new since the last growth reported them (one PerfStats add per growth, not per quad). */
      int misses;
   }

   /** At most this many pooled quads per thread (~2.8 MB): enough for a full tree and its distant level. */
   static final int POOL_MAX = 16_384;
   private static final ThreadLocal<QuadPool> POOL = ThreadLocal.withInitial(QuadPool::new);
   /** Marks a quad already handed back by this {@link #recycle} call (light is reset when the quad is reused). */
   private static final int RECYCLED = Integer.MIN_VALUE;

   /** A quad in the state {@code new TreeShape.Quad()} has: from this thread's pool when it has one. */
   static TreeShape.Quad newQuad() {
      QuadPool pool = POOL.get();
      if (pool.size == 0) {
         pool.misses++;
         return new TreeShape.Quad();
      }
      TreeShape.Quad q = pool.items[--pool.size];
      pool.items[pool.size] = null;
      java.util.Arrays.fill(q.v, 0.0F);
      q.texture = 0;
      q.light = 0;
      q.nx = 0;
      q.ny = 0;
      q.nz = 0;
      return q;
   }

   /**
    * [perf3] Hands one baked cell's float quads back (TrunkModel, right before it clears the cell's lists): nothing may
    * reference them afterwards. A quad both detail levels draw is in two of the lists; it is pooled once.
    */
   @SafeVarargs
   public static void recycle(List<TreeShape.Quad>... lists) {
      QuadPool pool = POOL.get();
      for (List<TreeShape.Quad> list : lists) {
         for (int i = 0, n = list.size(); i < n; i++) {
            TreeShape.Quad q = list.get(i);
            if (q.light == RECYCLED) continue;
            q.light = RECYCLED;
            if (pool.size < POOL_MAX) pool.items[pool.size++] = q;
         }
      }
   }

   /** [perf3] Test hook (tools/perf): every quad of the collection back to this thread's pool. */
   static void recycle(java.util.Collection<TreeShape.Quad> quads) {
      QuadPool pool = POOL.get();
      for (TreeShape.Quad q : quads) {
         if (q.light == RECYCLED) continue;
         q.light = RECYCLED;
         if (pool.size < POOL_MAX) pool.items[pool.size++] = q;
      }
   }

   private static boolean firm(int k) {
      return k == GROUND || k == SOLID;
   }

   private static final int[][] N6 = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

   private Tree grow(int x, int y, int z) {
      int k0 = this.w.kind(x, y, z);
      long start;
      if (k0 == LOG) {
         start = pack(x, y, z);
      } else if (k0 == LEAVES) {
         start = this.findLog(x, y, z);
         if (start == Long.MIN_VALUE) {
            return null;
         }
      } else {
         return null;
      }
      // Beyond this point a rejection belongs to the log component, not to the queried block.
      this.componentRejected = true;
      Scratch sc = this.scratch; // [perf3] queues, key lists and holder indexes below are the thread's reused scratch
      // ---- the connected logs (26-neighbourhood)
      long[] queue = sc.queue;
      int head = 0, tail = 0;
      queue[tail++] = start;
      this.comp.put(start, 0);
      while (head < tail) {
         long c = queue[head++];
         int cx = ux(c), cy = uy(c), cz = uz(c);
         if(this.w.construction(cx,cy,cz))return null;
         for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
               for (int dz = -1; dz <= 1; dz++) {
                  if ((dx | dy | dz) == 0) {
                     continue;
                  }
                  long n = pack(cx + dx, cy + dy, cz + dz);
                  if (!this.comp.contains(n) && this.w.kind(cx + dx, cy + dy, cz + dz) == LOG) {
                     this.comp.put(n, 0);
                     if (this.comp.size() > 900) {
                        return null; // a log building, not a tree
                     }
                     if (tail == queue.length) queue = sc.queue = java.util.Arrays.copyOf(queue, tail * 2);
                     queue[tail++] = n;
                  }
               }
            }
         }
      }
      long[] logs = sc.logs = this.comp.keys(sc.logs); // sorted
      int nLogs = this.comp.size();
      // [perf2] one growth per tree at a time: another worker growing this component already? wait for it instead
      if (!this.claim(logs[0])) {
         this.componentRejected = false;
         return null;
      }
      if (!this.noClaim) {
         // [perf2] another worker may have published this tree between our cache miss and our claim: take it
         Tree done = BY_CELL.get(PROBE.get().at(this.world, logs[0])); // [perf3]
         if (done != null && !done.none && !done.retired && done.has(this.mask)) {
            this.adopted = done;
            this.componentRejected = false;
            return null;
         }
      }
      // ---- rooted upright logs: the foot of each stem
      List<long[]> bottoms = new ArrayList<>();
      for (int i = 0; i < nLogs; i++) {
         long c = logs[i];
         int cx = ux(c), cy = uy(c), cz = uz(c);
         if (this.w.axis(cx, cy, cz) == 1 && this.w.kind(cx, cy - 1, cz) == GROUND) {
            bottoms.add(new long[]{c});
         }
      }
      boolean cut = false;
      if (bottoms.isEmpty()) {
         // [trees2] the remnant of a tree felled in an earlier session (its broken-out foot was not remembered): its
         // lowest upright logs, standing over air, are cut stems; a natural crown is checked below
         for (int i = 0; i < nLogs; i++) {
            long c = logs[i];
            int cx = ux(c), cy = uy(c), cz = uz(c);
            if (this.w.axis(cx, cy, cz) == 1 && this.w.kind(cx, cy - 1, cz) == AIR && this.comp.contains(pack(cx, cy + 1, cz))
                && this.w.axis(cx, cy + 1, cz) == 1) {
               bottoms.add(new long[]{c});
            }
         }
         if (bottoms.isEmpty()) {
            return null;
         }
         cut = true;
      }
      // ---- the leaves it holds: nearest to this tree's logs (not a neighbour's)
      LongMap foreign = sc.foreign;
      foreign.clear(16);
      long[] q = sc.queue2; // [perf3] FIFO as an array (was an ArrayDeque of boxed Longs)
      int qh = 0, qt = 0;
      for (int i = 0; i < nLogs; i++) {
         long c = logs[i];
         this.leafDist.put(c, 0);
         if (qt == q.length) q = sc.queue2 = java.util.Arrays.copyOf(q, qt * 2);
         q[qt++] = c;
      }
      while (qh < qt) {
         long c = q[qh++];
         int d = (int)this.leafDist.get(c, 0);
         if (d >= 6) {
            continue;
         }
         int cx = ux(c), cy = uy(c), cz = uz(c);
         for (int[] o : N6) {
            int nx = cx + o[0], ny = cy + o[1], nz = cz + o[2];
            long n = pack(nx, ny, nz);
            if (this.leafDist.contains(n)) {
               continue;
            }
            int k = this.w.kind(nx, ny, nz);
            if (k == LEAVES) {
               this.leafDist.put(n, d + 1);
               if (qt == q.length) q = sc.queue2 = java.util.Arrays.copyOf(q, qt * 2);
               q[qt++] = n;
            } else if (k == LOG && !this.comp.contains(n)) {
               foreign.put(n, 0);
            }
         }
      }
      long[] near = sc.keys = this.leafDist.keys(sc.keys); // sorted
      int nNear = this.leafDist.size();
      long[] leaves = sc.leaves;
      if (leaves.length < nNear) leaves = sc.leaves = new long[Math.max(nNear, leaves.length * 2)];
      int nLeaves = 0;
      for (int i = 0; i < nNear; i++) if (!this.comp.contains(near[i])) leaves[nLeaves++] = near[i];
      if (nLeaves < 6) {
         return null; // a dead snag or stump: the plain log shapes are right for it
      }
      LongMap fdist = sc.fdist;
      fdist.clear(64);
      long[] foreignKeys = sc.keys = foreign.keys(sc.keys);
      int nForeign = foreign.size();
      qh = qt = 0;
      for (int i = 0; i < nForeign; i++) {
         long f = foreignKeys[i];
         fdist.put(f, 0);
         if (qt == q.length) q = sc.queue2 = java.util.Arrays.copyOf(q, qt * 2);
         q[qt++] = f;
      }
      while (qh < qt) {
         long c = q[qh++];
         long d = fdist.get(c, 0);
         int cx = ux(c), cy = uy(c), cz = uz(c);
         for (int[] o : N6) {
            long n = pack(cx + o[0], cy + o[1], cz + o[2]);
            if (this.leafDist.contains(n) && !this.comp.contains(n) && !fdist.contains(n)) {
               fdist.put(n, d + 1);
               if (qt == q.length) q = sc.queue2 = java.util.Arrays.copyOf(q, qt * 2);
               q[qt++] = n;
            }
         }
      }
      int kept = 0; // [perf3] compacted in place (kept <= i), the same sorted order
      for (int i = 0; i < nLeaves; i++) {
         long leaf = leaves[i];
         long mine = this.leafDist.get(leaf, 0), fd = fdist.get(leaf, Long.MAX_VALUE);
         if (mine <= fd) {
            leaves[kept++] = leaf;
            // At a shared crown boundary choose the same nearest log from either side.
            // A shared leaf must never publish two trees into the same cache slot.
            if (mine < fd || this.comp.contains(this.findLog(ux(leaf), uy(leaf), uz(leaf)))) {
               this.own.put(leaf, 0);
            }
         }
      }
      nLeaves = kept; // sorted: the same tree grows the same whichever block asked first
      if (cut) {
         // [trees2] only a natural crown makes a remnant a tree (player-placed leaves on floating logs stay blocks)
         int natural = 0;
         for (int i = 0; i < nLeaves; i++) if (this.w.natural(ux(leaves[i]), uy(leaves[i]), uz(leaves[i]))) natural++;
         if (natural < 6 || natural < nLeaves * 0.8F) return null;
      }
      // ---- species and seed from the lowest stem
      long root = Long.MAX_VALUE;
      for (long[] b : bottoms) {
         if (root == Long.MAX_VALUE || uy(b[0]) < uy(root) || uy(b[0]) == uy(root) && b[0] < root) {
            root = b[0];
         }
      }
      int rx = ux(root), ry = uy(root), rz = uz(root);
      this.conifer = this.w.conifer(rx, ry, rz);
      this.seed = TreeShape.mix(root * 0x9E3779B97F4A7C15L + 12345);
      // ---- stems (clusters of rooted logs side by side at one level: 2x2 trunks, clumps)
      List<Stem> stems = this.stems(bottoms);
      if (stems.isEmpty()) {
         return null;
      }
      for (Stem st : stems) st.cut = cut; // [trees2]
      if (stems.size() > 1) {
         // stems of one clump grow apart a little (parallel stems side by side read as a notched post)
         float mx = 0, mz = 0;
         for (Stem st : stems) {
            mx += st.centre.get(0)[0];
            mz += st.centre.get(0)[1];
         }
         mx /= stems.size();
         mz /= stems.size();
         for (Stem st : stems) {
            float dx = st.centre.get(0)[0] - mx, dz = st.centre.get(0)[1] - mz;
            float l = (float)Math.sqrt(dx * dx + dz * dz);
            if (l < 1e-3F) {
               continue;
            }
            for (int i = 0; i < st.centre.size(); i++) {
               float k = 0.07F * Math.min(i, 8);
               st.centre.get(i)[0] += dx / l * k;
               st.centre.get(i)[1] += dz / l * k;
            }
         }
      }
      this.crown(stems, leaves, nLeaves);
      for (Stem s : stems) {
         this.trunk(s); // [trees2] the root flare is part of the trunk (lobed, ground-following); no hidden root tubes
      }
      if ((this.mask & LOD_IMPOSTOR) != 0) this.impostor(stems); // [perf]
      // ---- hand the quads to the blocks that draw them
      Tree t = new Tree(false);
      t.species = this.w.species(rx, ry, rz);
      t.conifer = this.conifer;
      t.form = this.w.crownForm(rx, ry, rz); // [trees2]
      t.lods = this.mask;
      t.anchor = root;
      t.anchorX = rx + 0.5F;
      t.anchorZ = rz + 0.5F;
      // the majority foliage material (the first to lead wins ties, as before) [perf3] counted in two small arrays
      Object[] materials = new Object[4];
      int[] counts = new int[4];
      int kinds = 0;
      int majority=0;
      for (int i = 0; i < nLeaves; i++) {
         long leaf = leaves[i];
         if (!this.own.contains(leaf)) continue;
         Object material=this.w.species(ux(leaf),uy(leaf),uz(leaf));
         int m = 0;
         while (m < kinds && !java.util.Objects.equals(materials[m], material)) m++;
         if (m == kinds) {
            if (kinds == materials.length) {
               materials = java.util.Arrays.copyOf(materials, kinds * 2);
               counts = java.util.Arrays.copyOf(counts, kinds * 2);
            }
            materials[kinds++] = material;
         }
         int count = ++counts[m];
         if(count>majority){majority=count;t.foliagePosition=leaf;t.foliageSpecies=material;}
      }
      // [trees2] the foliage tint is read from a real leaves block, never from a broken-out one (air)
      if (t.foliageSpecies != null && TreeScars.ghost(t.foliagePosition)) {
         for (int i = 0; i < nLeaves; i++) {
            long leaf = leaves[i];
            if (this.own.contains(leaf) && !TreeScars.ghost(leaf) && t.foliageSpecies.equals(this.w.species(ux(leaf), uy(leaf), uz(leaf)))) {
               t.foliagePosition = leaf;
               break;
            }
         }
      }
      // Rendering attachment contract for separate dead-branch blocks: connect to
      // the fitted stem axis, not the old cubic log's centre.
      float[] axis = sc.vec;
      for (int i = 0; i < nLogs; i++) {
         long log = logs[i];
         float[] best = null;
         float distance = Float.MAX_VALUE;
         for (int si = 0; si < stems.size(); si++) {
            Stem stem = stems.get(si);
            float h = uy(log) + 0.5F - stem.y0;
            if (h < 0 || h > stem.height) continue;
            this.axis(stem, h, axis, 0); // [perf3] into scratch
            float dx = axis[0] - ux(log) - 0.5F, dz = axis[2] - uz(log) - 0.5F;
            float d = dx * dx + dz * dz;
            if (d < distance) { distance = d; best = new float[]{axis[0],axis[1],axis[2],this.radius(stem,h)}; }
         }
         if (best != null && distance < 2.25F) t.sockets.put(log, best);
      }
      long[] ownLeaves = sc.ownLeaves = this.own.keys(sc.ownLeaves); // sorted
      int nOwn = this.own.size();
      Holders woodHolders = sc.wood.reset(logs, nLogs), leafHolders = sc.leaf.reset(ownLeaves, nOwn);
      // [perf3] two passes: first every quad's holder (exactly as before, quad by quad), counting each holder's quads per
      // level; then the quads go into lists sized for them (the lists used to grow from 10, copying as they went)
      int total = this.out.size();
      long[] cellOf = sc.cellOf;
      if (cellOf.length < total) cellOf = sc.cellOf = new long[Math.max(total, cellOf.length * 2)];
      LongMap perCell = sc.seen; // free now (findLog is done); holder -> quads per level (21 bits each)
      perCell.clear(256);
      for (int index = 0; index < total; index++) {
         TreeShape.Quad qd = this.out.get(index);
         int lod = this.outLod[index] & this.mask;
         cellOf[index] = Long.MIN_VALUE;
         if (lod == 0) continue;
         float cx = 0, cy = 0, cz = 0;
         for (int k = 0; k < 4; k++) {
            cx += qd.v[k * 8] * 0.25F;
            cy += qd.v[k * 8 + 1] * 0.25F;
            cz += qd.v[k * 8 + 2] * 0.25F;
         }
         // Shader packs classify by the hosting block, not by the quad's texture: leaf blocks wave,
         // logs stay still. Wood on a leaf block waves independently and tears joints, so wood is
         // always log-hosted. Foliage is hosted by the tree's own nearest leaf block, so it sways;
         // it falls back to a log only when no leaf section can represent it.
         long cell;
         if (qd.texture == 2) {
            cell = leafHolders.holder(qd, cx, cy, cz);
            if (cell == Long.MIN_VALUE) cell = woodHolders.holder(qd, cx, cy, cz);
         } else {
            cell = woodHolders.holder(qd, cx, cy, cz);
         }
         // Never upload a mesh that can wrap the renderer's packed coordinates.
         // Unsupported extreme blueprints keep their ordinary block models whole.
         if(cell==Long.MIN_VALUE) {
            // [perf] (was lod == LOD_FAR) a distant-only card with no room: the others cover it
            if ((lod & LOD_NEAR) == 0) continue;
            return null;
         }
         int hx = ux(cell), hy = uy(cell), hz = uz(cell);
         for (int k = 0; k < 4; k++) {
            qd.v[k * 8] -= hx;
            qd.v[k * 8 + 1] -= hy;
            qd.v[k * 8 + 2] -= hz;
         }
         // [perf] impostor foliage spans the crown: lit like its sunlit top, not its shaded heart
         qd.light = lod == LOD_IMPOSTOR && qd.texture == 2 ? this.impostorLight : this.lightFor(qd, cx, cy, cz);
         cellOf[index] = cell;
         perCell.put(cell, perCell.get(cell, 0L) + ((lod & LOD_NEAR) != 0 ? 1L : 0L) + ((lod & LOD_FAR) != 0 ? 1L << 21 : 0L)
            + ((lod & LOD_IMPOSTOR) != 0 ? 1L << 42 : 0L));
      }
      int placed = 0;
      long runCell = Long.MIN_VALUE;
      Long runKey = null;
      long runCounts = 0;
      List<TreeShape.Quad> runNear = null, runFar = null, runImpostor = null;
      for (int index = 0; index < total; index++) {
         long cell = cellOf[index];
         if (cell == Long.MIN_VALUE) continue;
         TreeShape.Quad qd = this.out.get(index);
         int lod = this.outLod[index] & this.mask;
         // [perf2] consecutive quads mostly share their holder: box its key and look its lists up once per run
         if (cell != runCell) {
            runCell = cell;
            runKey = cell;
            runCounts = perCell.get(cell, 0L);
            runNear = runFar = runImpostor = null;
         }
         if ((lod & LOD_NEAR) != 0) {
            if (runNear == null) {
               runNear = t.cells.computeIfAbsent(runKey, NEW_CELL);
               ((ArrayList<TreeShape.Quad>)runNear).ensureCapacity((int)(runCounts & 0x1FFFFF));
            }
            runNear.add(qd);
         }
         if ((lod & LOD_FAR) != 0) {
            if (runFar == null) {
               runFar = t.far.computeIfAbsent(runKey, NEW_CELL);
               ((ArrayList<TreeShape.Quad>)runFar).ensureCapacity((int)(runCounts >>> 21 & 0x1FFFFF));
            }
            runFar.add(qd);
         }
         if ((lod & LOD_IMPOSTOR) != 0) { // [perf]
            if (runImpostor == null) {
               runImpostor = t.impostor.computeIfAbsent(runKey, NEW_CELL);
               ((ArrayList<TreeShape.Quad>)runImpostor).ensureCapacity((int)(runCounts >>> 42 & 0x1FFFFF));
            }
            runImpostor.add(qd);
         }
         placed++;
      }
      t.quadCount = placed;
      // its blocks and the leaves it holds point to it, so none of them grows it again
      long[] mem = new long[nLogs + nOwn];
      int[] expect = new int[mem.length];
      int i = 0;
      for (int j = 0; j < nLogs; j++) {
         long h = logs[j];
         expect[i] = LOG | (this.w.axis(ux(h), uy(h), uz(h)) & 3) << 3; // [trees2] the axis, for a ghost of this log
         mem[i++] = h;
      }
      for (int j = 0; j < nOwn; j++) {
         long h = ownLeaves[j];
         expect[i] = LEAVES | this.w.light(ux(h), uy(h), uz(h)) >>> 4 << 3;
         mem[i++] = h;
      }
      t.members = mem;
      t.expect = expect;
      this.componentRejected = false;
      return t;
   }

   private static final java.util.function.Function<Long, List<TreeShape.Quad>> NEW_CELL = c -> new ArrayList<>(); // [perf2]

   /** Nearest log through leaves (at most six steps), or MIN_VALUE. [perf3] Allocation-free (scratch queue and set). */
   private long findLog(int x, int y, int z) {
      Scratch sc = this.scratch;
      long[] q = sc.findQueue; // pairs: cell, depth
      int head = 0, tail = 0;
      LongMap seen = sc.seen;
      seen.clear(64);
      q[tail++] = pack(x, y, z);
      q[tail++] = 0;
      seen.put(pack(x, y, z), 0);
      long nearest = Long.MIN_VALUE;
      long nearestDepth = Long.MAX_VALUE;
      while (head < tail) {
         long c = q[head], depth = q[head + 1];
         head += 2;
         if (depth > nearestDepth) break;
         int cx = ux(c), cy = uy(c), cz = uz(c);
         for (int[] o : N6) {
            int nx = cx + o[0], ny = cy + o[1], nz = cz + o[2];
            long n = pack(nx, ny, nz);
            if (!seen.put(n, 0)) {
               continue;
            }
            int k = this.w.kind(nx, ny, nz);
            if (k == LOG) {
               if (depth < nearestDepth || nearest == Long.MIN_VALUE || n < nearest) {
                  nearest = n;
                  nearestDepth = depth;
               }
            }
            if (k == LEAVES && depth < 6) {
               if (tail + 2 > q.length) q = sc.findQueue = java.util.Arrays.copyOf(q, q.length * 2);
               q[tail++] = n;
               q[tail++] = depth + 1;
            }
         }
      }
      return nearest;
   }

   /**
    * Candidate host blocks of one material. A quad is drawn by the block it is centred in when
    * that block is a candidate, else by the nearest candidate (per cell, cached) whose chunk
    * section can represent every vertex. [perf3] Reused per thread ({@link #reset}).
    */
   private static final class Holders {
      long[] cells;
      int count;
      final LongMap set = new LongMap(64);
      final LongMap nearest = new LongMap(512);

      /** The first n of sorted are the candidates (sorted). */
      Holders reset(long[] sorted, int n) {
         this.cells = sorted;
         this.count = n;
         this.set.clear(n);
         this.nearest.clear(512);
         for (int i = 0; i < n; i++) this.set.put(sorted[i], 0);
         return this;
      }

      long holder(TreeShape.Quad quad, float x, float y, float z) {
         if (this.count == 0) return Long.MIN_VALUE;
         int bx = (int)Math.floor(x), by = (int)Math.floor(y), bz = (int)Math.floor(z);
         long cell = pack(bx, by, bz);
         if (this.set.contains(cell) && fitsSection(quad, cell)) return cell;
         long near = this.nearest.get(cell, Long.MIN_VALUE);
         if (near == Long.MIN_VALUE) {
            // nearest to the cell centre: independent of which quad asked first
            near = this.count > 48 ? this.shells(bx, by, bz) : Long.MIN_VALUE;
            if (near == Long.MIN_VALUE) near = this.closest(null, bx + .5F, by + .5F, bz + .5F);
            this.nearest.put(cell, near);
         }
         if (fitsSection(quad, near)) return near;
         return this.closest(quad, x, y, z); // rare: a quad reaching past its neighbour's section
      }

      /** Nearest candidate by growing cubic shells (exact: stops once no nearer shell remains), or MIN_VALUE past 2. */
      private long shells(int bx, int by, int bz) {
         long best = Long.MIN_VALUE;
         int bestD = Integer.MAX_VALUE;
         for (int r = 0; r <= 2; r++) {
            if (best != Long.MIN_VALUE && r * r > bestD) break;
            for (int dx = -r; dx <= r; dx++) for (int dy = -r; dy <= r; dy++) {
               boolean face = Math.abs(dx) == r || Math.abs(dy) == r;
               for (int dz = -r; dz <= r; dz += face || r == 0 ? 1 : 2 * r) {
                  long c = pack(bx + dx, by + dy, bz + dz);
                  if (!this.set.contains(c)) continue;
                  int d = dx * dx + dy * dy + dz * dz;
                  if (d < bestD || d == bestD && c < best) {
                     best = c;
                     bestD = d;
                  }
               }
            }
         }
         return best != Long.MIN_VALUE && 9 > bestD ? best : Long.MIN_VALUE;
      }

      private long closest(TreeShape.Quad quad, float x, float y, float z) {
         long best = Long.MIN_VALUE;
         float distance = Float.MAX_VALUE;
         for (int i = 0; i < this.count; i++) {
            long candidate = this.cells[i];
            float dx = ux(candidate) + .5F - x, dy = uy(candidate) + .5F - y, dz = uz(candidate) + .5F - z;
            float d = dx * dx + dy * dy + dz * dz;
            if (d < distance && (quad == null || fitsSection(quad, candidate))) {
               best = candidate; // sorted candidates: ties keep the lowest cell
               distance = d;
            }
         }
         return best;
      }
   }

   /** Sodium compact positions encode section-local [-8,24). Leave a margin. */
   private static boolean fitsSection(TreeShape.Quad quad,long holder) {
      int ox=ux(holder)&~15,oy=uy(holder)&~15,oz=uz(holder)&~15;
      for(int vertex=0;vertex<4;vertex++)for(int axis=0;axis<3;axis++) {
         float p=quad.v[vertex*8+axis]-(axis==0?ox:axis==1?oy:oz);
         if(!Float.isFinite(p)||p< -7.5F||p>23.5F)return false;
      }
      return true;
   }

   private int lightFor(TreeShape.Quad qd, float cx, float cy, float cz) {
      int lx = (int)Math.floor(cx + qd.nx * 0.6F), ly = (int)Math.floor(cy + qd.ny * 0.6F), lz = (int)Math.floor(cz + qd.nz * 0.6F);
      // Many quads sample the same cell; read the world once per cell.
      long key = pack(lx, ly, lz);
      long cached = this.lightCache.get(key, Long.MIN_VALUE);
      if (cached != Long.MIN_VALUE) return (int)cached;
      int result = this.lightAt(lx, ly, lz);
      this.lightCache.put(key, result);
      return result;
   }

   private int lightAt(int lx, int ly, int lz) {
      int k = this.w.kind(lx, ly, lz);
      if (k != LOG && k != SOLID && k != GROUND) {
         return this.w.light(lx, ly, lz);
      }
      int best = 0, bs = -1;
      for (int[] o : N6) {
         int kk = this.w.kind(lx + o[0], ly + o[1], lz + o[2]);
         if (kk == LOG || kk == SOLID || kk == GROUND) {
            continue;
         }
         int l = this.w.light(lx + o[0], ly + o[1], lz + o[2]);
         int s = (l >> 20 & 15) * 16 + (l >> 4 & 15);
         if (s > bs) {
            bs = s;
            best = l;
         }
      }
      return bs < 0 ? this.w.light(lx, ly + 1, lz) : best;
   }

   // ------------------------------------------------------------------ stems
   static final class Stem {
      int y0;
      int foot;                                         // logs across the base
      final List<Long> cells = new ArrayList<>();       // one log per level (for holding quads)
      final List<float[]> centre = new ArrayList<>();   // x, z per level
      float r0;
      float height;                                     // trunk length in blocks (to the top of the last log)
      Foot footShape;                                   // [trees2] how the ground meets this stem (shared by NEAR / FAR)
      boolean cut;                                      // [trees2] a felled remnant: no foot, the trunk starts at its lowest log
      int minX, maxX, minZ, maxZ;                       // [trees2] the foot's block columns (bounds)
   }

   private List<Stem> stems(List<long[]> bottoms) {
      Set<Long> left = new HashSet<>();
      for (long[] b : bottoms) {
         left.add(b[0]);
      }
      List<Stem> out = new ArrayList<>();
      Map<Long,float[]> clumpLean = new HashMap<>();
      Set<Long> used = new HashSet<>();
      List<Long> order = new ArrayList<>(left);
      java.util.Collections.sort(order);
      for (long b : order) {
         if (!left.contains(b)) {
            continue;
         }
         // the base cluster: rooted logs side by side at this level
         List<Long> base = new ArrayList<>();
         ArrayDeque<Long> q = new ArrayDeque<>();
         q.add(b);
         left.remove(b);
         while (!q.isEmpty()) {
            long c = q.poll();
            base.add(c);
            int cx = ux(c), cy = uy(c), cz = uz(c);
            for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
               long n = pack(cx + o[0], cy, cz + o[1]);
               if (left.remove(n)) {
                  q.add(n);
               }
            }
         }
         if (base.size() > 9) {
            continue; // a wall of posts
         }
         if (base.size() > 1 && base.size() < 4) {
            if(!this.conifer) {
               float cx=0,cz=0;
               for(long foot:base){cx+=ux(foot)+.5F;cz+=uz(foot)+.5F;}
               cx/=base.size();cz/=base.size();
               for(long foot:base){
                  float dx=ux(foot)+.5F-cx,dz=uz(foot)+.5F-cz;
                  float length=(float)Math.sqrt(dx*dx+dz*dz);
                  if(length>.001F)clumpLean.putIfAbsent(foot,new float[]{dx/length*.035F,dz/length*.035F});
               }
            }
            // Two or three rooted columns are a clump of individual stems, not a
            // thick notched post. A complete 2x2 giant continues through the shared
            // trunk path below. Put the remaining feet back into the sorted worklist.
            for (long foot : base) if (foot != b) left.add(foot);
            base = new ArrayList<>(List.of(b));
         }
         Stem s = new Stem();
         s.y0 = uy(b);
         s.foot = base.size();
         s.minX = s.minZ = Integer.MAX_VALUE;
         s.maxX = s.maxZ = Integer.MIN_VALUE;
         for (long c : base) {
            s.minX = Math.min(s.minX, ux(c));
            s.maxX = Math.max(s.maxX, ux(c));
            s.minZ = Math.min(s.minZ, uz(c));
            s.maxZ = Math.max(s.maxZ, uz(c));
         }
         List<Long> level = base;
         int y = s.y0;
         while (!level.isEmpty() && s.cells.size() < 96) {
            float sx = 0, sz = 0;
            long hold = level.get(0);
            for (long c : level) {
               sx += ux(c) + 0.5F;
               sz += uz(c) + 0.5F;
               used.add(c);
               if (c < hold) {
                  hold = c;
               }
            }
            s.centre.add(new float[]{sx / level.size(), sz / level.size()});
            s.cells.add(hold);
            // the next level: upright logs straight above; else one leaning step to the side
            List<Long> next = new ArrayList<>();
            for (long c : level) {
               int cx = ux(c), cz = uz(c);
               if (this.comp.contains(pack(cx, y + 1, cz)) && this.w.axis(cx, y + 1, cz) == 1) {
                  next.add(pack(cx, y + 1, cz));
               }
            }
            if (next.isEmpty() && level.size() == 1) {
               int cx = ux(level.get(0)), cz = uz(level.get(0));
               long only = Long.MIN_VALUE;
               int n = 0;
               for (int dx = -1; dx <= 1; dx++) {
                  for (int dz = -1; dz <= 1; dz++) {
                     long c = pack(cx + dx, y + 1, cz + dz);
                     if ((dx | dz) != 0 && this.comp.contains(c) && this.w.axis(cx + dx, y + 1, cz + dz) == 1 && !this.comp.contains(pack(cx + dx, y, cz + dz))) {
                        only = c;
                        n++;
                     }
                  }
               }
               if (n == 1 && s.cells.size() >= 3) {
                  next.add(only);
               }
            }
            level = next;
            y++;
         }
         s.height = s.cells.size();
         if (s.height < 2) {
            continue;
         }
         // a straight axis through the log centres (a least-squares line): block-sized jogs and a 2x2
         // base narrowing to one column become a gentle, even lean instead of an S-bend
         float n = s.centre.size(), mh = 0, mx = 0, mz = 0;
         for (int i = 0; i < n; i++) {
            mh += i;
            mx += s.centre.get(i)[0];
            mz += s.centre.get(i)[1];
         }
         mh /= n;
         mx /= n;
         mz /= n;
         float shh = 0, shx = 0, shz = 0;
         for (int i = 0; i < n; i++) {
            shh += (i - mh) * (i - mh);
            shx += (i - mh) * (s.centre.get(i)[0] - mx);
            shz += (i - mh) * (s.centre.get(i)[1] - mz);
         }
         float kx = shh > 0 ? shx / shh : 0, kz = shh > 0 ? shz / shh : 0;
         // never lean more than the logs allow (about 1 in 6)
         kx = clamp(kx, -0.18F, 0.18F);
         kz = clamp(kz, -0.18F, 0.18F);
         List<float[]> sm = new ArrayList<>();
         float[] apart=clumpLean.getOrDefault(b,new float[2]);
         for (int i = 0; i < n; i++) {
            sm.add(new float[]{mx + kx * (i - mh)+apart[0]*i, mz + kz * (i - mh)+apart[1]*i});
         }
         s.centre.clear();
         s.centre.addAll(sm);
         float h = s.height;
         s.r0 = s.foot == 1 ? clamp(0.2F + 0.017F * h, 0.3F, 0.48F) : (float)Math.sqrt(s.foot / Math.PI) * 0.9F;
         out.add(s);
      }
      return out;
   }

   /** Centre along the fitted straight or gently leaning stem axis. */
   private float[] axis(Stem s, float h) {
      float[] out = new float[3];
      this.axis(s, h, out, 0);
      return out;
   }

   /** [perf3] {@link #axis(Stem, float)} into out[o..o+2] (the same values, no array per call). */
   private void axis(Stem s, float h, float[] out, int o) {
      float f = Math.max(0, Math.min(s.centre.size() - 1.0F, h - 0.5F));
      int i = (int)Math.floor(f);
      int j = Math.min(s.centre.size() - 1, i + 1);
      float t = f - i;
      float[] a = s.centre.get(i), b = s.centre.get(j);
      out[o] = a[0] + (b[0] - a[0]) * t;
      out[o + 1] = s.y0 + h;
      out[o + 2] = a[1] + (b[1] - a[1]) * t;
   }

   /** Trunk radius of stem s at height h (nominal: level ground, no buttress lobes). */
   private float radius(Stem s, float h) {
      return this.core(s, h) * flare(s, h);
   }

   /** [trees2] Trunk radius without the root flare. */
   private float core(Stem s, float h) {
      float H = s.height;
      if (this.conifer) {
         float top = H + 0.9F;
         return s.r0 * Math.max(0.02F, 1.0F - 0.9F * (float)Math.pow(Math.max(0, h) / top, 0.95));
      }
      return s.r0 * (1.0F - 0.96F * Math.min(1.0F, Math.max(0, h) / H));
   }

   /**
    * [trees2] Root flare: the trunk widens into the ground over its lowest half block. It was 1.55x over 0.45 blocks,
    * a bulb about 1.6 blocks across under a single log (3.1 under a 2x2 trunk); now 1.38x (1.22x for wide trunks)
    * plus buttress lobes ({@link Foot}). SignTrunkProbe.flare and StandTrunk mirror this.
    */
   static final float FLARE = 0.38F, FLARE_WIDE = 0.22F, FLARE_LEN = 0.40F, LOBE = 0.10F, LOBE_LEN = 0.22F;

   private static float flare(Stem s, float h) {
      return 1.0F + flareAmp(s) * (float)Math.exp(-Math.max(0, h) / FLARE_LEN);
   }

   // ------------------------------------------------------------------ whole-tree crown
   private void crown(List<Stem> stems,long[] leaves,int leafCount) {
      // [perf3] each stem's foot centre once (was recomputed for every leaf and every other stem)
      float[] feet=new float[stems.size()*3];
      for(int s=0;s<stems.size();s++)axis(stems.get(s),0,feet,s*3);
      for(int si=0;si<stems.size();si++) {
         Stem stem=stems.get(si);
         float[] centre=axis(stem,0);
         float spread=0;
         for(int li=0;li<leafCount;li++) {
            long leaf=leaves[li];
            float dx=ux(leaf)+.5F-centre[0],dz=uz(leaf)+.5F-centre[2];
            spread=Math.max(spread,(float)Math.sqrt(dx*dx+dz*dz));
         }
         // Leaf blocks supply scale, not the outline. Each stem receives a botanical crown.
         spread=conifer?clamp(spread*.98F,stem.height*.27F,stem.height*.38F)
                       :clamp(spread*.95F,stem.height*.48F,stem.height*.65F);
         long foot=stem.cells.get(0);
         int form=w.crownForm(ux(foot),uy(foot),uz(foot));
         // The blueprint's lowest leaves near this stem: needle tiers start there.
         float leafBase=Float.NaN;
         for(int li=0;li<leafCount;li++) {
            long leaf=leaves[li];
            float dx=ux(leaf)+.5F-centre[0],dz=uz(leaf)+.5F-centre[2],own=dx*dx+dz*dz;
            boolean nearest=true;
            for(int oi=0;oi<stems.size();oi++) if(oi!=si) {
               float ox=ux(leaf)+.5F-feet[oi*3],oz=uz(leaf)+.5F-feet[oi*3+2];
               if(ox*ox+oz*oz<own){nearest=false;break;}
            }
            if(nearest){float h=uy(leaf)-stem.y0;if(!(h>=leafBase))leafBase=h;}
         }
         if(form==1)spread*=.82F;
         if(form==2)spread*=1.13F;
         // Broadening tall crowns without limit exceeds chunk mesh coordinates.
         // Reserve room for terminal shoots and leaves beyond the main bough.
         spread=Math.min(spread,5.8F);
         int first=out.size();
         this.impostor.beginCrown(); // [perf]
         TreeCrown.build(TreeShape.mix(seed+stem.cells.get(0)),centre[0],stem.y0,centre[2],stem.height,
            spread,stem.r0,conifer,form,leafBase,out,this.crownSink);
         float[] upper=axis(stem,stem.height);
         float leanX=(upper[0]-centre[0])/stem.height,leanZ=(upper[2]-centre[2])/stem.height;
         for(int i=first;i<out.size();i++) {
            var quad=out.get(i);
            for(int vertex=0;vertex<4;vertex++) {
               int offset=vertex*8;float height=quad.v[offset+1]-stem.y0;
               quad.v[offset]+=leanX*height;quad.v[offset+2]+=leanZ*height;
               float nx=quad.v[offset+5],ny=quad.v[offset+6]-leanX*quad.v[offset+5]-leanZ*quad.v[offset+7],nz=quad.v[offset+7];
               float length=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
               quad.v[offset+5]=nx/length;quad.v[offset+6]=ny/length;quad.v[offset+7]=nz/length;
            }
            quad.nx=quad.v[5];quad.ny=quad.v[6];quad.nz=quad.v[7];
         }
         this.impostor.endCrown(leanX,leanZ,stem.y0); // [perf]
      }
   }

   /** Crown limbs and foliage cards, each emitted at the detail levels this growth builds. */
   private final TreeCrown.Sink crownSink = new TreeCrown.Sink() {
      @Override public void add(float[] points, float[] radii, int n) {
         TreeGrowth.this.limb(points, radii, n);
      }

      @Override public void card(float[] c, float[] u, float[] v, float width, float height, boolean far) {
         TreeGrowth.this.card(c, u, v, width, height, far);
      }
   };

   /**
    * A double-sided foliage card (the same two quads the full crown always had). A card kept for the
    * distant crown is also emitted enlarged around its centre: fewer, larger cards keep the crown's
    * outline and leaf density while drawing a third of the quads.
    */
   private void card(float[] c, float[] u, float[] v, float width, float height, boolean far) {
      this.impostor.card(c, width, height); // [perf] every card shapes the impostor
      if ((this.mask & LOD_NEAR) != 0) {
         this.emit = LOD_NEAR;
         this.cardQuads(c, u, v, width, height);
      }
      if (far && (this.mask & LOD_FAR) != 0) {
         this.emit = LOD_FAR;
         // Still two-sided: drawing only the outward side saved a sixth of the distant foliage but
         // lost 8-14% of the crown's coverage (the far half shows through the gaps).
         this.cardQuads(c, u, v, width * farCardScale, height * farCardScale);
      }
      this.emit = LOD_ALL;
   }

   private void cardQuads(float[] c, float[] u, float[] v, float width, float height) {
      float nx = u[1] * v[2] - u[2] * v[1], ny = u[2] * v[0] - u[0] * v[2], nz = u[0] * v[1] - u[1] * v[0];
      float length = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
      nx /= length;
      ny /= length;
      nz /= length;
      for (int reverse = 0; reverse < 2; reverse++) {
         TreeShape.Quad q = newQuad(); // [perf3] pooled
         q.texture = 2;
         q.nx = reverse == 0 ? nx : -nx;
         q.ny = reverse == 0 ? ny : -ny;
         q.nz = reverse == 0 ? nz : -nz;
         for (int i = 0; i < 4; i++) {
            int corner = reverse == 0 ? i : 3 - i;
            float a = corner == 0 || corner == 3 ? -.5F : .5F, b = corner < 2 ? -.5F : .5F;
            int o = i * 8;
            q.v[o] = c[0] + u[0] * a * width + v[0] * b * height;
            q.v[o + 1] = c[1] + u[1] * a * width + v[1] * b * height;
            q.v[o + 2] = c[2] + u[2] * a * width + v[2] * b * height;
            q.v[o + 3] = a + .5F;
            q.v[o + 4] = .5F - b;
            q.v[o + 5] = q.nx;
            q.v[o + 6] = q.ny;
            q.v[o + 7] = q.nz;
         }
         this.add(q);
      }
   }

   /** Mean width of a regular n-gon tube relative to its circumradius (a round limb is 2). */
   private static float polygonWidth(int sides) {
      return (float)(2 * sides * Math.sin(Math.PI / sides) / Math.PI);
   }

   /**
    * A tapered limb through pts, rings carried along the curve so it never twists. At distance twigs
    * are left out (sub-pixel there, and inside the foliage), and other limbs use fewer ring sides (with
    * the radius widened so the tube keeps its apparent thickness) and every other segment.
    * [perf3] pts holds n points (x, y, z), rs their radii.
    */
   private void limb(float[] pts, float[] rs, int n) {
      if (n < 2) {
         return;
      }
      float r0 = rs[0];
      // Ring sides follow what a limb's width can show: thin boughs and twigs are far more
      // numerous than leaders, and each extra side is a quad on every segment.
      int sides = r0 >= 0.2F ? 8 : r0 >= 0.12F ? 6 : r0 >= 0.05F ? 5 : 3;
      // Bark on thin limbs may stretch along the limb (one band instead of two per segment).
      float vScale = r0 < 0.15F ? 0.6F : 1.0F;
      boolean farKeep = r0 >= farMinLimb;
      int farSides = sides >= 8 ? 5 : sides >= 6 ? 4 : 3;
      // one mesh serves both levels when the distant one would be identical
      boolean same = farKeep && farSides == sides && n < 4;
      if ((this.mask & LOD_NEAR) != 0) {
         this.emit = same ? LOD_ALL : LOD_NEAR;
         // Child sockets are on these exact segments; simplifying them moves the
         // visible parent away from its attachments and opens gaps.
         this.tube(pts, rs, n, sides, vScale, 1.0F);
      }
      if ((this.mask & LOD_FAR) != 0 && farKeep && !(same && (this.mask & LOD_NEAR) != 0)) {
         this.emit = same ? LOD_ALL : LOD_FAR;
         float[] P = pts;
         float[] R = rs;
         int m = n;
         if (n >= 4) {
            // every other point (and the last) [perf3] into scratch
            Scratch sc = this.scratch;
            if (sc.farPts.length < n * 3) {
               sc.farPts = new float[n * 3];
               sc.farRadii = new float[n];
            }
            P = sc.farPts;
            R = sc.farRadii;
            m = 0;
            int last = n - 1;
            for (int i = 0; i < last; i += 2) {
               P[m * 3] = pts[i * 3];
               P[m * 3 + 1] = pts[i * 3 + 1];
               P[m * 3 + 2] = pts[i * 3 + 2];
               R[m++] = rs[i];
            }
            P[m * 3] = pts[last * 3];
            P[m * 3 + 1] = pts[last * 3 + 1];
            P[m * 3 + 2] = pts[last * 3 + 2];
            R[m++] = rs[last];
         }
         this.tube(P, R, m, farSides, vScale, farSides == sides ? 1.0F : polygonWidth(sides) / polygonWidth(farSides));
      }
      this.emit = LOD_ALL;
   }

   /**
    * [perf3] Writes norm({x, y, z}) to out[o..o+2] with exactly the old {@code norm(float[])} arithmetic ({0, 1, 0} for a
    * near-zero vector). The tube code below is the old vector code (sub/add/scale/cross/norm, a new float[3] per step)
    * written out on floats, operation for operation, so it produces the same bits without the arrays.
    */
   private static void norm(float x, float y, float z, float[] out, int o) {
      float l = (float)Math.sqrt(x * x + y * y + z * z);
      if (l < 1e-7F) {
         out[o] = 0;
         out[o + 1] = 1;
         out[o + 2] = 0;
         return;
      }
      float s = 1 / l;
      out[o] = x * s;
      out[o + 1] = y * s;
      out[o + 2] = z * s;
   }

   /** [perf3] dist(P[a], P[b]) as before: sqrt of the dot product of their difference with itself. */
   private static float dist(float[] P, int a, int b) {
      float x = P[a] - P[b], y = P[a + 1] - P[b + 1], z = P[a + 2] - P[b + 2];
      return (float)Math.sqrt(x * x + y * y + z * z);
   }

   /** [perf3] Room for a ring of the given sides in both ring buffers. */
   private void rings(int sides) {
      Scratch sc = this.scratch;
      if (sc.ringA.length < sides * 3) {
         sc.ringA = new float[sides * 3];
         sc.ringB = new float[sides * 3];
      }
   }

   private void tube(float[] P, float[] R, int n, int sides, float vScale, float widen) {
      float[] t = this.scratch.vec; // 0..2 dir0, 3..5 u, 6..8 d, 9..11 second direction
      norm(P[3] - P[0], P[4] - P[1], P[5] - P[2], t, 0); // dir0
      // frame(dir0)[0]: norm(cross(ref, dir0))
      float rx, ry, rz;
      if (Math.abs(t[1]) < 0.9F) {
         rx = 0;
         ry = 1;
         rz = 0;
      } else {
         rx = 1;
         ry = 0;
         rz = 0;
      }
      norm(ry * t[2] - rz * t[1], rz * t[0] - rx * t[2], rx * t[1] - ry * t[0], t, 3);
      float ux = t[3], uy = t[4], uz = t[5];
      float vAcc = TreeShape.mix(this.seed + (long)(P[1] * 1000)) % 7 / 7.0F;
      this.rings(sides);
      float[] ring = this.scratch.ringA, prevRing = null;
      for (int i = 0; i < n; i++) {
         int o = i * 3;
         float dx, dy, dz;
         if (i == 0) {
            dx = t[0];
            dy = t[1];
            dz = t[2];
         } else if (i == n - 1) {
            norm(P[o] - P[o - 3], P[o + 1] - P[o - 2], P[o + 2] - P[o - 1], t, 6);
            dx = t[6];
            dy = t[7];
            dz = t[8];
         } else {
            norm(P[o] - P[o - 3], P[o + 1] - P[o - 2], P[o + 2] - P[o - 1], t, 6);
            norm(P[o + 3] - P[o], P[o + 4] - P[o + 1], P[o + 5] - P[o + 2], t, 9);
            norm(t[6] + t[9], t[7] + t[10], t[8] + t[11], t, 6);
            dx = t[6];
            dy = t[7];
            dz = t[8];
         }
         // parallel transport of the ring's reference vector: u = norm(u - d * dot(u, d))
         float dot = ux * dx + uy * dy + uz * dz;
         norm(ux - dx * dot, uy - dy * dot, uz - dz * dot, t, 3);
         ux = t[3];
         uy = t[4];
         uz = t[5];
         float vx = dy * uz - dz * uy, vy = dz * ux - dx * uz, vz = dx * uy - dy * ux; // cross(d, u)
         float r = R[i] * widen;
         for (int k = 0; k < sides; k++) {
            double a = Math.PI * 2 * k / sides;
            float c = (float)Math.cos(a), sn = (float)Math.sin(a);
            float nx = ux * c + vx * sn, ny = uy * c + vy * sn, nz = uz * c + vz * sn;
            ring[k * 3] = P[o] + nx * r;
            ring[k * 3 + 1] = P[o + 1] + ny * r;
            ring[k * 3 + 2] = P[o + 2] + nz * r;
         }
         if (prevRing != null) {
            float len = dist(P, o - 3, o) * vScale;
            this.band(prevRing, ring, sides, P, o - 3, o, vAcc, vAcc + len, R[i - 1] * widen, r);
            vAcc += len;
         }
         // swap the two ring buffers
         float[] next = prevRing == null ? this.scratch.ringB : prevRing;
         prevRing = ring;
         ring = next;
      }
   }

   /**
    * Quads between two rings of equal side count (scalar maths: this runs for every wood quad). [perf3] Rings are flat
    * (x, y, z per side); the piece runs from the centre point C[ca] to C[cb].
    */
   private void band(float[] ra, float[] rb, int sides, float[] C, int ca, int cb, float v0, float v1, float r0, float r1) {
      float circ = (float)Math.PI * (r0 + r1);
      float w = Math.min(1.0F, circ / sides);
      // split long pieces so the bark texture never stretches past one tile (at distance the
      // stretch cannot be seen, and the extra rings would only cost quads)
      int pieces = this.emit == LOD_FAR ? 1 : Math.max(1, (int)Math.ceil(v1 - v0));
      for (int s = 0; s < pieces; s++) {
         float t0 = s / (float)pieces, t1 = (s + 1) / (float)pieces, tm = (t0 + t1) * 0.5F;
         float va = v0 + (v1 - v0) * t0, vb = v0 + (v1 - v0) * t1;
         // centre of the piece: midpoint of lerp(ca,cb,t0) and lerp(ca,cb,t1)
         float mx = C[ca] + (C[cb] - C[ca]) * tm, my = C[ca + 1] + (C[cb + 1] - C[ca + 1]) * tm, mz = C[ca + 2] + (C[cb + 2] - C[ca + 2]) * tm;
         for (int k = 0; k < sides; k++) {
            int k1 = (k + 1) % sides;
            int pk = k * 3, p1 = k1 * 3; // ra[k], rb[k], ra[k1], rb[k1]
            float u0 = (k * w) % 1.0F;
            if (u0 + w > 1.0F) {
               u0 = 1.0F - w;
            }
            TreeShape.Quad q = newQuad(); // [perf3] pooled
            float[] v = q.v;
            // corner 0: ring k at t0; 1: ring k at t1; 2: ring k1 at t1; 3: ring k1 at t0
            for (int axis = 0; axis < 3; axis++) {
               v[axis] = ra[pk + axis] + (rb[pk + axis] - ra[pk + axis]) * t0;
               v[8 + axis] = ra[pk + axis] + (rb[pk + axis] - ra[pk + axis]) * t1;
               v[16 + axis] = ra[p1 + axis] + (rb[p1 + axis] - ra[p1 + axis]) * t1;
               v[24 + axis] = ra[p1 + axis] + (rb[p1 + axis] - ra[p1 + axis]) * t0;
            }
            float n0x = ra[pk] + (rb[pk] - ra[pk]) * tm - mx, n0y = ra[pk + 1] + (rb[pk + 1] - ra[pk + 1]) * tm - my, n0z = ra[pk + 2] + (rb[pk + 2] - ra[pk + 2]) * tm - mz;
            float n1x = ra[p1] + (rb[p1] - ra[p1]) * tm - mx, n1y = ra[p1 + 1] + (rb[p1 + 1] - ra[p1 + 1]) * tm - my, n1z = ra[p1 + 2] + (rb[p1 + 2] - ra[p1 + 2]) * tm - mz;
            float l0 = (float)Math.sqrt(n0x * n0x + n0y * n0y + n0z * n0z), l1 = (float)Math.sqrt(n1x * n1x + n1y * n1y + n1z * n1z);
            if (l0 < 1e-7F) { n0x = 0; n0y = 1; n0z = 0; } else { n0x /= l0; n0y /= l0; n0z /= l0; }
            if (l1 < 1e-7F) { n1x = 0; n1y = 1; n1z = 0; } else { n1x /= l1; n1y /= l1; n1z /= l1; }
            float uA = clamp(u0, 0, 1), uB = clamp(u0 + w, 0, 1);
            v[3] = uA; v[4] = va; v[5] = n0x; v[6] = n0y; v[7] = n0z;
            v[11] = uA; v[12] = vb; v[13] = n0x; v[14] = n0y; v[15] = n0z;
            v[19] = uB; v[20] = vb; v[21] = n1x; v[22] = n1y; v[23] = n1z;
            v[27] = uB; v[28] = va; v[29] = n1x; v[30] = n1y; v[31] = n1z;
            this.finish(q, 0);
         }
      }
   }

   // ------------------------------------------------------------------ [perf] impostor
   /** The impostor level: crossed crown cards fitted to the grown crown, and a bark prism per stem. */
   private void impostor(List<Stem> stems) {
      List<TreeImpostor.Trunk> trunks = new ArrayList<>();
      float crownTop = -Float.MAX_VALUE;
      float cx = 0, cz = 0;
      for (Stem s : stems) {
         // the prism rises into the crown (a conifer's leader, a broadleaf's fork), not to its top
         float h = this.conifer ? s.height * 0.72F : s.height * 0.9F;
         float[] a = this.axis(s, -0.3F), b = this.axis(s, h);
         float r0 = s.foot == 1 ? s.r0 : s.r0 * 0.9F;
         trunks.add(new TreeImpostor.Trunk(a[0], a[1], a[2], r0 * 1.08F, b[0], b[1], b[2], Math.max(0.05F, this.radius(s, h))));
         crownTop = Math.max(crownTop, s.y0 + s.height);
         cx += b[0] / stems.size();
         cz += b[2] / stems.size();
      }
      // sky light over the crown (nothing above a tree's own top but its leaves)
      int lx = (int)Math.floor(cx), lz = (int)Math.floor(cz);
      int best = -1;
      for (int dy = 3; dy >= 0; dy--) {
         int k = this.w.kind(lx, (int)crownTop + dy, lz);
         if (k == LOG || k == SOLID || k == GROUND) continue;
         int l = this.w.light(lx, (int)crownTop + dy, lz);
         if ((l >> 20 & 15) * 16 + (l >> 4 & 15) > best) {
            best = (l >> 20 & 15) * 16 + (l >> 4 & 15);
            this.impostorLight = l;
         }
      }
      this.emit = LOD_IMPOSTOR;
      this.impostor.build(this.seed, this.conifer, trunks, this::add);
      this.emit = LOD_ALL;
   }

   // ------------------------------------------------------------------ trunk and roots
   /** The trunk: full detail near, fewer sides and rings at distance (same radius and taper). */
   private void trunk(Stem s) {
      int sides = s.foot > 1 ? 16 : s.r0 > 0.4F ? 12 : 10;
      if ((this.mask & LOD_NEAR) != 0) {
         this.emit = LOD_NEAR;
         this.trunk(s, sides, 0.25F, 0.5F);
      }
      if ((this.mask & LOD_FAR) != 0) {
         this.emit = LOD_FAR;
         this.trunk(s, s.foot > 1 ? 10 : 8, 0.5F, 1.0F);
      }
      this.emit = LOD_ALL;
   }

   private void trunk(Stem s, int sides, float lowStep, float highStep) {
      float top = this.conifer ? s.height + 0.9F : s.height + 0.15F;
      // [perf3] the axis points and radii in reused flat buffers (were lists of new arrays and boxed floats)
      Scratch sc = this.scratch;
      float[] P = sc.pts, R = sc.radii;
      int n = 0;
      for (float h = s.cut ? 0 : -0.3F; h < top - 0.01F; h += h < 1.5F ? lowStep : highStep) { // [trees2] a cut stem starts at its log
         if ((n + 4) * 3 > P.length) {
            P = sc.pts = java.util.Arrays.copyOf(P, P.length * 2);
            R = sc.radii = java.util.Arrays.copyOf(R, R.length * 2);
         }
         this.axis(s, h, P, n * 3);
         R[n++] = this.radius(s, h);
      }
      if ((n + 4) * 3 > P.length) {
         P = sc.pts = java.util.Arrays.copyOf(P, P.length * 2);
         R = sc.radii = java.util.Arrays.copyOf(R, R.length * 2);
      }
      int plain = n; // [trees2] rings of the trunk proper (the tip / rounded top follow)
      if (this.conifer) {
         this.axis(s, top, P, n * 3);
         R[n++] = 0.015F;
      } else {
         // the trunk rounds off where the limbs take over
         float[] c = this.axis(s, s.height);
         float rt = this.radius(s, s.height);
         this.axis(s, top, P, n * 3);
         R[n++] = rt * 0.85F;
         P[n * 3] = c[0];
         P[n * 3 + 1] = c[1] + 0.45F;
         P[n * 3 + 2] = c[2];
         R[n++] = rt * 0.45F;
         P[n * 3] = c[0];
         P[n * 3 + 1] = c[1] + 0.62F;
         P[n * 3 + 2] = c[2];
         R[n++] = Math.min(0.02F, rt * 0.2F); // [trees2] a tip, never wider than the ring below it (was a floating knob)
      }
      Foot foot = this.foot(s);
      float v = 0;
      this.rings(sides);
      float[] ring = sc.ringA, prev = null;
      for (int i = 0; i < n; i++) {
         float r = R[i];
         int o = i * 3;
         float h = P[o + 1] - s.y0;
         // [trees2] above the flare the ring is the plain trunk (r); near the ground each side follows its own soil
         boolean low = h < 4.0F && i < plain && !s.cut;
         float core = low ? this.core(s, h) : 0;
         for (int k = 0; k < sides; k++) {
            double a = Math.PI * 2 * k / sides;
            long hash = TreeShape.mix(this.seed + k * 131L + s.y0);
            float rk = low ? foot.radius(core, h, (float)a, flareAmp(s), (hash & 255) / 255.0F - 0.5F)
               // bark ridges: the ring is not quite round
               : r * (1.0F + ((hash & 255) / 255.0F - 0.5F) * 0.06F);
            ring[k * 3] = P[o] + (float)Math.cos(a) * rk;
            ring[k * 3 + 1] = P[o + 1];
            ring[k * 3 + 2] = P[o + 2] + (float)Math.sin(a) * rk;
         }
         if (prev != null) {
            float len = dist(P, o - 3, o);
            this.band(prev, ring, sides, P, o - 3, o, v, v + len, R[i - 1], r);
            v += len;
         }
         float[] next = prev == null ? sc.ringB : prev;
         prev = ring;
         ring = next;
      }
   }

   private static float flareAmp(Stem s) {
      return s.cut ? 0 : s.foot > 1 ? FLARE_WIDE : FLARE;
   }

   /**
    * [trees2] Where a stem meets the ground, sampled in 32 directions around its foot. Minecraft ground is stepped, so
    * each side of a trunk on a slope meets the soil at its own height:
    * <ul>
    * <li>{@code lift}: how far the soil rises above the foot block on that side (uphill). The flare starts there, so
    * the trunk widens into the uphill soil instead of entering it as a plain post (it used to look sunk 1-2 blocks).</li>
    * <li>{@code cap}: the furthest supported reach of the foot on that side. Where the ground drops (downhill, a ledge)
    * the flare stops at the foot block's edge. It used to hang over the drop and its lowest ring was dragged down the
    * block face to the lower ground: a bark curtain up to 1.1 blocks deep below the foot.</li>
    * <li>a few buttress lobes (5, 7 under a wide trunk) instead of the root tubes, which mostly hid inside the flare.</li>
    * </ul>
    */
   static final class Foot {
      static final int N = 32;
      final float[] lift = new float[N];
      final float[] cap = new float[N];
      /** Distance to the edge of the foot's own block columns: a trunk standing lower than the soil around it fills them. */
      final float[] cover = new float[N];
      int lobes;
      float phase, lobe;

      /** Ring radius at height h above the foot block, direction a, for a trunk of radius core there. */
      float radius(float core, float h, float a, float flareAmp, float rnd) {
         float f = (float)(a / (Math.PI * 2) * N);
         int i = Math.floorMod((int)Math.floor(f), N), j = (i + 1) % N;
         float t = f - (float)Math.floor(f);
         float lift = this.lift[i] + (this.lift[j] - this.lift[i]) * t;
         float hl = Math.max(0, h - lift); // height above the soil on this side
         float decay = (float)Math.exp(-hl / FLARE_LEN);
         float ridge = (float)Math.max(0, Math.cos(this.lobes * (a - this.phase)));
         float r = core * (1.0F + flareAmp * decay) * (1.0F + this.lobe * ridge * ridge * (float)Math.exp(-hl / LOBE_LEN));
         // bark ridges, stronger at the foot (continuous: the old step at one block left a ledge in every ridge)
         r *= 1.0F + rnd * 2.0F * (0.03F + 0.05F * (float)Math.exp(-hl / 0.5F));
         if (lift > 0.3F && h < lift + 0.05F) {
            // the soil stands higher on this side: up to the soil the trunk fills its own block column, so no square pit
            // shows around it (a trunk set in a hollow, or soil built up around it); above, the flare as usual
            float cover = this.cover[i] + (this.cover[j] - this.cover[i]) * t;
            float w = Math.min(1.0F, (lift - 0.3F) / 0.5F);
            r = Math.max(r, r + (cover - r) * w);
         }
         float cap = Math.min(this.cap[i], this.cap[j]);
         if (cap < Float.MAX_VALUE && h < 1.2F) {
            // never past a drop's edge near the foot (above it the trunk is free again)
            float limit = cap + Math.max(0, h - 0.35F) * 1.4F;
            r = Math.min(r, Math.max(limit, core * (1.0F - 0.05F)));
         }
         return r;
      }
   }

   /** [trees2] The stem's foot (lazily, once per growth: NEAR and FAR share it). */
   private Foot foot(Stem s) {
      if (s.footShape != null) return s.footShape;
      Foot f = new Foot();
      float[] c = this.axis(s, 0);
      float y0 = s.y0;
      float core = this.core(s, 0);
      float reach = core * (1.0F + flareAmp(s)) * (1.0F + LOBE) * 1.09F;
      float[] raw = new float[Foot.N];
      for (int k = 0; k < Foot.N; k++) {
         double a = Math.PI * 2 * k / Foot.N;
         float dx = (float)Math.cos(a), dz = (float)Math.sin(a);
         // ray from the axis to the edge of the foot's own columns
         float tx = dx > 1e-4F ? (s.maxX + 1 - c[0]) / dx : dx < -1e-4F ? (s.minX - c[0]) / dx : Float.MAX_VALUE;
         float tz = dz > 1e-4F ? (s.maxZ + 1 - c[2]) / dz : dz < -1e-4F ? (s.minZ - c[2]) / dz : Float.MAX_VALUE;
         float edge = Math.min(tx, tz);
         f.cover[k] = Math.max(core, edge + 0.16F); // past the corners between ring vertices
         // the soil just beyond the foot's columns on this side
         raw[k] = clamp(this.groundAt(c[0] + dx * (edge + 0.3F), c[2] + dz * (edge + 0.3F), y0) - y0, 0, 1.5F);
         float cap = Float.MAX_VALUE;
         for (float r = core * 0.9F; r <= reach + 0.001F; r += 0.04F) {
            if (this.groundAt(c[0] + dx * r, c[2] + dz * r, y0) < y0 - 0.05F) {
               cap = Math.max(core * 0.9F, r - 0.03F);
               break;
            }
         }
         f.cap[k] = cap;
      }
      // the foot narrows towards a drop gradually (a sample-to-sample step left a notch in the flare)
      float[] capRaw = f.cap.clone();
      for (int k = 0; k < Foot.N; k++) {
         float m = capRaw[k];
         for (int d = 1; d <= 4; d++) {
            m = Math.min(m, Math.min(capRaw[Math.floorMod(k + d, Foot.N)], capRaw[Math.floorMod(k - d, Foot.N)]) + d * 0.05F);
         }
         f.cap[k] = m;
      }
      // lifted only well inside an uphill side (erosion), so a raised flare never stands over lower ground beside it;
      // then a short soft ramp between sides (ground samples are whole blocks)
      float[] eroded = new float[Foot.N];
      for (int k = 0; k < Foot.N; k++) {
         float m = raw[k];
         m = Math.min(m, Math.min(raw[Math.floorMod(k + 1, Foot.N)], raw[Math.floorMod(k - 1, Foot.N)]));
         eroded[k] = m;
      }
      for (int k = 0; k < Foot.N; k++) {
         f.lift[k] = (eroded[Math.floorMod(k - 1, Foot.N)] + 2 * eroded[k] + eroded[Math.floorMod(k + 1, Foot.N)]) / 4.0F;
      }
      long h = TreeShape.mix(this.seed + s.y0 * 7919L + 77);
      f.lobes = s.foot > 1 ? 7 : 5;
      f.phase = (h & 1023) / 1023.0F * 6.2831855F;
      f.lobe = LOBE * clamp(0.55F + (s.r0 - 0.3F) * 2.5F, 0.55F, 1.0F); // young thin trunks: gentler buttresses
      s.footShape = f;
      return f;
   }

   /** [trees2] Ground height (top of the natural soil) at a point near the foot level, cached per block column. */
   private float groundAt(float x, float z, float nearY) {
      int bx = (int)Math.floor(x), bz = (int)Math.floor(z);
      long key = pack(bx, (int)nearY, bz);
      long cached = this.groundCache.get(key, Long.MIN_VALUE);
      if (cached != Long.MIN_VALUE) return Float.intBitsToFloat((int)cached);
      float g = this.w.groundHeight(bx + 0.5F, bz + 0.5F, nearY);
      this.groundCache.put(key, Float.floatToRawIntBits(g));
      return g;
   }

   private final LongMap groundCache; // [perf3] from scratch

   // ------------------------------------------------------------------ quad helpers
   private static void put(TreeShape.Quad q, int k, float[] pos, float u, float v, float[] n) {
      int o = k * 8;
      q.v[o] = pos[0];
      q.v[o + 1] = pos[1];
      q.v[o + 2] = pos[2];
      q.v[o + 3] = clamp(u, 0, 1);
      q.v[o + 4] = v;
      q.v[o + 5] = n[0];
      q.v[o + 6] = n[1];
      q.v[o + 7] = n[2];
   }

   private void finish(TreeShape.Quad q, int tex) {
      float vmin = Float.MAX_VALUE, vmax = -Float.MAX_VALUE;
      for (int k = 0; k < 4; k++) {
         vmin = Math.min(vmin, q.v[k * 8 + 4]);
         vmax = Math.max(vmax, q.v[k * 8 + 4]);
      }
      float shift = (float)Math.floor(vmin);
      if (vmax - shift > 1.0F) {
         shift = vmax - 1.0F;
      }
      for (int k = 0; k < 4; k++) {
         q.v[k * 8 + 4] = clamp(q.v[k * 8 + 4] - shift, 0, 1);
      }
      float[] v = q.v;
      float ex = v[16] - v[0], ey = v[17] - v[1], ez = v[18] - v[2];   // c - a
      float fx = v[24] - v[8], fy = v[25] - v[9], fz = v[26] - v[10];  // d - b
      float nx = ey * fz - ez * fy, ny = ez * fx - ex * fz, nz = ex * fy - ey * fx;
      float ln = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
      if (ln < 1e-9F) {
         return;
      }
      nx /= ln;
      ny /= ln;
      nz /= ln;
      float avg0 = v[5] + v[13] + v[21] + v[29], avg1 = v[6] + v[14] + v[22] + v[30], avg2 = v[7] + v[15] + v[23] + v[31];
      if (nx * avg0 + ny * avg1 + nz * avg2 < 0) {
         for (int i = 0; i < 8; i++) {
            float tmp = v[8 + i];
            v[8 + i] = v[24 + i];
            v[24 + i] = tmp;
         }
         nx = -nx;
         ny = -ny;
         nz = -nz;
      }
      q.nx = nx;
      q.ny = ny;
      q.nz = nz;
      q.texture = tex;
      this.add(q);
   }

   private static float clamp(float v, float lo, float hi) {
      return v < lo ? lo : Math.min(v, hi);
   }

   private static float[] sub(float[] a, float[] b) {
      return new float[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
   }

   private static float[] add(float[] a, float[] b) {
      return new float[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]};
   }

   private static float[] scale(float[] a, float s) {
      return new float[]{a[0] * s, a[1] * s, a[2] * s};
   }

   private static float dot(float[] a, float[] b) {
      return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
   }

   private static float[] cross(float[] a, float[] b) {
      return new float[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
   }

   private static float[] norm(float[] a) {
      float l = (float)Math.sqrt(dot(a, a));
      return l < 1e-7F ? new float[]{0, 1, 0} : scale(a, 1 / l);
   }

   private static float dist(float[] a, float[] b) {
      return (float)Math.sqrt(dot(sub(a, b), sub(a, b)));
   }

   private static float[] lerp(float[] a, float[] b, float t) {
      return new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
   }

   private static float[][] frame(float[] d) {
      float[] ref = Math.abs(d[1]) < 0.9F ? new float[]{0, 1, 0} : new float[]{1, 0, 0};
      float[] u = norm(cross(ref, d));
      return new float[][]{u, cross(d, u)};
   }
}
