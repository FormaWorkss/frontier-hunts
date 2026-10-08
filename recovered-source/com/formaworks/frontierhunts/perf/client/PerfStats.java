package com.formaworks.frontierhunts.perf.client;

import java.util.concurrent.atomic.AtomicLongArray;

/**
 * [perf2] Running counters of the expensive things the Ultra preset turns on, for {@link HitchLogger} and its F3
 * line. Counters are monotonic totals; the logger snapshots them per frame / second / window and reports deltas.
 *
 * <p>Always counting, never allocating: one atomic add per event (events are per tree growth, per section dirtied,
 * per animal skinned, never per block or per vertex), so this costs nothing measurable whether the logger is on
 * or off. Safe from any thread (chunk-mesh workers grow trees).
 */
public final class PerfStats {
   private PerfStats() {
   }

   /** Realistic trees grown (worker threads), and the time spent growing them (microseconds). */
   public static final int TREES_GROWN = 0, TREE_GROW_US = 1;
   /** Lookups that waited for another worker already growing the same tree instead of growing it twice. */
   public static final int TREES_SHARED = 2;
   /** Cached trees dropped for room, and the time spent choosing them (microseconds). */
   public static final int TREES_EVICTED = 3, TREE_TRIM_US = 4;
   /** Cached trees grown again because a section needed a level of detail the cached growth lacked. */
   public static final int TREES_REGROWN_LEVEL = 5;
   /** Seasonal foliage cells derived (fall colour / leaf drop) on mesh workers. */
   public static final int SEASON_CELLS = 6;
   /** Chunk sections marked dirty by the tree level-of-detail scheduler / the season stage sweep. */
   public static final int LOD_DIRTY = 7, SEASON_DIRTY = 8;
   /** Every chunk section marked dirty for any reason (block, light and chunk updates included). */
   public static final int SECTIONS_DIRTY = 9;
   /** Sculpted wildlife meshes skinned on the CPU, and whitetail-family animators posed. */
   public static final int WILDLIFE_SKINNED = 10, DEER_POSED = 11;
   /** Large animal textures decoded (off the render thread) and uploaded (render thread, microseconds). */
   public static final int TEXTURES_DECODED = 12, TEXTURE_UPLOAD_US = 13;
   /** Whole-world renderer reloads (LevelRenderer.allChanged: shader toggle, settings, first alpine biome). */
   public static final int ALL_CHANGED = 14;
   /** Time the tree level-of-detail client tick spent (microseconds). */
   public static final int LOD_TICK_US = 15;
   /** Section rebuild requests held back this tick because frames were spiking ({@link FrameGovernor}). */
   public static final int DIRTY_DEFERRED = 16;
   /** Cube models baked for logs / leaves that are not part of a grown tree (cached per block state). */
   public static final int CUBES_BAKED = 17;
   /**
    * [perf3] Float quads tree growths made, and how many of them had to be allocated new because their thread's pool of
    * baked quads was empty (TreeGrowth.newQuad): the rest were reused.
    */
   public static final int TREE_QUADS = 18, TREE_QUADS_NEW = 19;
   public static final int COUNT = 20;

   static final String[] NAMES = {
      "treesGrown", "treeGrowMs", "treesShared", "treesEvicted", "treeTrimMs", "treesRegrownForLod",
      "seasonCells", "lodDirty", "seasonDirty", "sectionsDirty", "wildlifeSkinned", "deerPosed",
      "texDecoded", "texUploadMs", "allChanged", "lodTickMs", "dirtyDeferred", "cubesBaked", "treeQuads", "treeQuadsNew"
   };
   /** Counters measured in microseconds (reported as milliseconds). */
   static boolean micros(int i) {
      return i == TREE_GROW_US || i == TREE_TRIM_US || i == TEXTURE_UPLOAD_US || i == LOD_TICK_US;
   }

   private static final AtomicLongArray C = new AtomicLongArray(COUNT);

   public static void add(int counter, long amount) {
      C.getAndAdd(counter, amount);
   }

   public static void inc(int counter) {
      C.getAndIncrement(counter);
   }

   /** Adds the microseconds elapsed since {@code startNanos} (a {@link System#nanoTime()} value). */
   public static void since(int counter, long startNanos) {
      C.getAndAdd(counter, (System.nanoTime() - startNanos) / 1000L);
   }

   /** Set once Sodium's rebuild hook has fired: from then on it alone counts {@link #SECTIONS_DIRTY}. */
   private static volatile boolean sodiumCounts;

   /** A section rebuild request seen by Sodium's RenderSectionManager (PerfSodiumSectionsMixin). */
   public static void sodiumSection() {
      sodiumCounts = true;
      C.getAndIncrement(SECTIONS_DIRTY);
   }

   /** A section marked dirty through vanilla's LevelRenderer (PerfLevelRendererMixin): counted unless Sodium counts. */
   public static void vanillaSection() {
      if (!sodiumCounts) C.getAndIncrement(SECTIONS_DIRTY);
   }

   /** Copies every counter into {@code into} (length {@link #COUNT}). */
   static void snapshot(long[] into) {
      for (int i = 0; i < COUNT; i++) into[i] = C.get(i);
   }
}
