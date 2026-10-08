package com.formaworks.frontierhunts.client.tree;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Distance level of detail for realistic trees (and the forest floor under them).
 *
 * <p>Each grown tree is one LOD subject (its anchor, the foot of its lowest stem), so a whole tree
 * switches at once whichever sections it spans; forest-floor dressing uses one subject per section
 * column. Chunk-section builders ask {@link #treeLod} / {@link #floorLod} which level to draw, using a
 * camera position the client thread publishes every tick (builders never touch the camera), and record
 * the level each section was built with. Every tick the client thread re-targets subjects whose camera
 * distance crossed the band (with hysteresis: NEAR inside {@link #NEAR_IN} blocks, FAR beyond
 * {@link #NEAR_OUT}), and marks the sections built with a stale level dirty, a few per tick, those
 * that gain detail first and nearest first, so fast travel never floods the section builder.
 *
 * <p>[perf] A third level, {@link #IMPOSTOR}: beyond {@link #impOut} blocks a tree is drawn as a handful of
 * crossed cutout cards ({@link TreeImpostor}) and the forest floor under it is not dressed. The band
 * distances come from the tree-detail setting ({@link #configure}); transitions keep their hysteresis
 * and the per-tick rebuild budget.
 */
@EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT)
public final class TreeLod {
   public static final int NEAR = 0, FAR = 1;
   /** [perf] Flat crossed cards per tree, and no forest-floor dressing. */
   public static final int IMPOSTOR = 2;
   /** A subject enters the NEAR band inside this horizontal distance and leaves it beyond NEAR_OUT. */
   static volatile float NEAR_IN = 32, NEAR_OUT = 40; // [perf] set by configure()
   /** [perf] A subject becomes an impostor beyond impOut and is drawn in 3D again inside impIn. */
   static volatile float impIn = 88, impOut = 96;
   /** First decision for a subject (no history): the middle of the band. */
   static float nearNew() { return (NEAR_IN + NEAR_OUT) / 2; } // [perf] was the constant NEAR_NEW
   /**
    * Trees first grown within this distance of the camera are grown with both levels. [perf2] Just past the full-detail
    * band's outer edge (was NEAR_OUT + 16): at Ultra that ring held a few hundred full trees (~1.4M quads) drawn only
    * at the distant level, which pushed the cache past its budget; a tree approaching from further out is grown again
    * at the band edge either way.
    */
   static float growBoth() { return NEAR_OUT + 4; } // [perf] was the constant 56
   /** Section rebuilds requested per client tick at most. */
   static volatile int REBUILDS_PER_TICK = 4; // [perf] set by configure()

   /**
    * [perf] Sets the level-of-detail bands (horizontal blocks from the camera): full detail inside
    * {@code near}, impostors beyond {@code impostor} (0 or less: never), each with hysteresis. Subjects
    * move to the new bands on the next ticks, within the rebuild budget.
    */
   public static void configure(float near, float impostor, int rebuildsPerTick) {
      near = Math.max(8, near);
      NEAR_IN = near;
      NEAR_OUT = near + Math.max(8, near * 0.25F);
      if (impostor <= 0) {
         impIn = impOut = Float.MAX_VALUE;
      } else {
         impostor = Math.max(NEAR_OUT + 16, impostor);
         impOut = impostor;
         impIn = impostor - Math.max(8, impostor * 0.08F);
      }
      REBUILDS_PER_TICK = Math.max(1, rebuildsPerTick);
   }

   private static com.formaworks.frontierhunts.perf.PerfConfig.TreeDetail applied;

   /** [perf] Applies the configured tree detail when it changed (subjects retarget on the next ticks). */
   static void applyDetail() {
      var detail = com.formaworks.frontierhunts.perf.client.ShaderPerf.treeDetail(); // [shaderperf] one step lighter under a shader pack
      if (detail != applied) {
         applied = detail;
         configure(detail.near, detail.impostor, detail.rebuildsPerTick);
         budget(detail); // [perf2]
      }
   }

   /**
    * [shaderperf] The shader pack was switched on or off (client thread, just before the world re-meshes for it): apply
    * the tree detail now and move the subjects to the new bands, so that re-mesh already draws them at their new level
    * instead of re-meshing everything again over the next seconds.
    */
   public static void followShaderPack() {
      var was = applied;
      applyDetail();
      if (applied != was && camValid) {
         retarget(camX, camZ);
      }
   }

   /**
    * [perf2] Tree cache budgets from the tree detail and the heap: a share of the maximum heap (8% at Ultra and Maximum,
    * 5% below; about 190 bytes per cached quad and 100 per member block), never below the old fixed budgets.
    */
   static void budget(com.formaworks.frontierhunts.perf.PerfConfig.TreeDetail detail) {
      long heap = Runtime.getRuntime().maxMemory();
      if (heap <= 0 || heap == Long.MAX_VALUE) heap = 4L << 30;
      boolean high = detail == com.formaworks.frontierhunts.perf.PerfConfig.TreeDetail.ULTRA
         || detail == com.formaworks.frontierhunts.perf.PerfConfig.TreeDetail.MAXIMUM;
      long quads = Math.max(700_000L, Math.min(high ? 4_000_000L : 2_500_000L, (long)(heap * (high ? 0.08 : 0.05) / 190)));
      long members = Math.max(120_000L, Math.min(1_000_000L, (long)(heap * 0.02 / 100)));
      TreeGrowth.configureBudget(quads, members);
   }

   /** [perf] The TreeGrowth LOD_* bit of a level. */
   static int bits(int lod) {
      return lod == NEAR ? TreeGrowth.LOD_NEAR : lod == FAR ? TreeGrowth.LOD_FAR : TreeGrowth.LOD_IMPOSTOR;
   }

   private static volatile double camX, camZ;
   private static volatile boolean camValid;
   /** [perf3] Camera velocity (blocks/s, horizontal, smoothed over a few ticks; 0 after a teleport). */
   private static volatile double velX, velZ;
   private static double lastCamX = Double.NaN, lastCamZ;

   private static final class Subject {
      final float x, z;
      volatile int target;

      Subject(float x, float z, int target) {
         this.x = x;
         this.z = z;
         this.target = target;
      }
   }

   /** LOD subjects by key (tree anchor, or a floor column key). */
   private static final ConcurrentHashMap<Long, Subject> SUBJECTS = new ConcurrentHashMap<>();
   /** Built tree sections: section key -> (subject key -> the level that build drew it at). */
   private static final ConcurrentHashMap<Long, ConcurrentHashMap<Long, Integer>> SECTIONS = new ConcurrentHashMap<>();
   /** Bumped by clear(): builder threads' memos of what they last recorded become stale. */
   private static final AtomicInteger GENERATION = new AtomicInteger();
   /** Per builder thread: {section, subject, level, generation} last recorded (most blocks repeat it). */
   private static final ThreadLocal<long[]> LAST = ThreadLocal.withInitial(() -> new long[]{Long.MIN_VALUE, Long.MIN_VALUE, -1, -1});

   /** The client level the state belongs to (checked by builders and the tick: whoever sees a new one resets). */
   private static volatile java.lang.ref.WeakReference<Object> owner = new java.lang.ref.WeakReference<>(null);

   // client-thread state
   private static double scanX = Double.NaN, scanZ = Double.NaN;
   private static long ticks;
   private static boolean backlog;

   private TreeLod() {
   }

   // ------------------------------------------------------------------ builder threads

   /** LOD_* bits a lookup from the block at x, z needs: both levels near the camera, else the distant one. */
   static int growNeed(int x, int z) {
      if (!camValid) return TreeGrowth.LOD_NEAR;
      return growNeed(x + 0.5, z + 0.5, camX, camZ, velX, velZ); // [perf3] and where the camera is heading
   }

   /**
    * [perf3] Seconds of travel a growth looks ahead along the camera's velocity, and the speed (blocks/s) from which it
    * does: faster than sprinting (5.6), so walking and sprinting grow exactly as before.
    */
   static final double LOOKAHEAD_S = 6.0, LOOKAHEAD_SPEED = 8.0;

   /**
    * [perf3] The levels to grow a tree at (bx, bz) with, for a camera at (cx, cz) moving at (vx, vz) blocks/s. As before
    * by distance: both levels just past the full-detail band, the distant crown out to the impostor band, the impostor
    * alone beyond. New: travelling fast (flying, elytra, a fast ride), a tree the camera will pass within the
    * full-detail band in the next {@link #LOOKAHEAD_S} seconds at this velocity is grown with both levels at once, and
    * one it will see in 3D is not grown as an impostor first. At speed every tree near the path used to be grown two
    * or three times in as many seconds (impostor, then distant, then again with full detail: treesRegrownForLod 40/s in
    * the 1.2.8 log's flight). What is drawn is unchanged: the level drawn is still {@link #treeLod}'s, by distance.
    */
   static int growNeed(double bx, double bz, double cx, double cz, double vx, double vz) {
      double dx = bx - cx, dz = bz - cz, d2 = dx * dx + dz * dz;
      // [perf] impostor-only growth beyond the impostor band; [perf2] just past its outer edge (was impOut + 8: the
      // trees first seen in that ring are drawn as cutouts, yet were grown with a distant crown they never drew)
      float both = growBoth(), card = impOut == Float.MAX_VALUE ? Float.MAX_VALUE : impOut + 2;
      if (d2 < both * both) return TreeGrowth.LOD_NEAR;
      double v2 = vx * vx + vz * vz;
      if (v2 > LOOKAHEAD_SPEED * LOOKAHEAD_SPEED) {
         // closest approach of the camera's path over the look-ahead to this tree
         double t = Math.max(0, Math.min(LOOKAHEAD_S, (dx * vx + dz * vz) / v2));
         double ex = dx - vx * t, ez = dz - vz * t, e2 = ex * ex + ez * ez;
         if (e2 < (double)NEAR_IN * NEAR_IN) return TreeGrowth.LOD_NEAR;
         if (d2 >= (double)card * card && e2 < (double)impIn * impIn) return TreeGrowth.LOD_FAR;
      }
      return d2 < (double)card * card ? TreeGrowth.LOD_FAR : TreeGrowth.LOD_IMPOSTOR;
   }

   /** The level a tree is drawn at in the section being built at (x, y, z); records it for that section. */
   static int treeLod(TreeGrowth.Tree tree, int x, int y, int z) {
      return decide(tree.anchor, tree.anchorX, tree.anchorZ, x, y, z);
   }

   /** The level of forest-floor dressing at (x, y, z); records it for that section. */
   static int floorLod(int x, int y, int z) {
      int sx = x >> 4, sz = z >> 4;
      // y -2048 is below every build height: never a tree anchor
      return decide(TreeGrowth.pack(sx << 4 | 8, -2048, sz << 4 | 8), (sx << 4) + 8, (sz << 4) + 8, x, y, z);
   }

   private static int decide(long key, float ax, float az, int x, int y, int z) {
      follow(Minecraft.getInstance().level);
      Subject subject = SUBJECTS.get(key);
      if (subject == null) {
         // with no camera yet (joining a world) draw in full; the first ticks re-target and rebuild
         double dx = ax - camX, dz = az - camZ, d2 = dx * dx + dz * dz;
         float nearNew = nearNew(), cardNew = (impIn + impOut) / 2; // [perf] impostor band too
         int first = !camValid || d2 < nearNew * nearNew ? NEAR : d2 < (double)cardNew * cardNew ? FAR : IMPOSTOR;
         subject = SUBJECTS.computeIfAbsent(key, k -> new Subject(ax, az, first));
      }
      int lod = subject.target;
      long section = TreeGrowth.pack(x >> 4, y >> 4, z >> 4);
      long[] last = LAST.get();
      int generation = GENERATION.get();
      if (last[0] != section || last[1] != key || last[2] != lod || last[3] != generation) {
         last[0] = section;
         last[1] = key;
         last[2] = lod;
         last[3] = generation;
         SECTIONS.computeIfAbsent(section, s -> new ConcurrentHashMap<>(8)).put(key, lod);
      }
      return lod;
   }

   /** Resets the state when the client level changed (a builder of the new level may see it before the tick). */
   private static void follow(Object current) {
      if (current == owner.get()) return;
      synchronized (TreeLod.class) {
         if (current == owner.get()) return;
         camValid = false; // the published camera belongs to the old level
         clear();
         owner = new java.lang.ref.WeakReference<>(current);
      }
   }

   /** Forgets every subject and section (world change, resource reload: every section is rebuilt anyway). */
   public static void clear() {
      GENERATION.incrementAndGet();
      SUBJECTS.clear();
      SECTIONS.clear();
   }

   // ------------------------------------------------------------------ client thread

   @SubscribeEvent
   public static void tick(ClientTickEvent.Post event) {
      try {
         tick();
      } catch (RuntimeException e) {
         // never let level of detail break the client tick; builders fall back to what they last saw
      }
   }

   private static void tick() {
      Minecraft mc = Minecraft.getInstance();
      Object current = mc.level;
      if (current != owner.get()) {
         scanX = Double.NaN;
         lastCamX = Double.NaN; // [perf3] no velocity across worlds
         velX = velZ = 0;
      }
      follow(current);
      if (current == null || mc.levelRenderer == null) {
         camValid = false;
         return;
      }
      Vec3 at = null;
      var camera = mc.gameRenderer == null ? null : mc.gameRenderer.getMainCamera();
      if (camera != null && camera.isInitialized()) at = camera.getPosition();
      else if (mc.player != null) at = mc.player.position();
      if (at == null) {
         camValid = false;
         return;
      }
      // [perf3] velocity for the growth look-ahead: per-tick movement, smoothed; a jump (teleport, respawn) resets it
      double mx = Double.isNaN(lastCamX) ? 0 : at.x - lastCamX, mz = Double.isNaN(lastCamX) ? 0 : at.z - lastCamZ;
      if (mx * mx + mz * mz > 32.0 * 32.0) {
         velX = velZ = 0;
      } else {
         velX += (mx * 20.0 - velX) * 0.5;
         velZ += (mz * 20.0 - velZ) * 0.5;
      }
      lastCamX = at.x;
      lastCamZ = at.z;
      camX = at.x;
      camZ = at.z;
      camValid = true;
      TreeGrowth.focus(at.x, at.z, true); // [perf2] eviction keeps the trees nearest the camera
      TreeGrowth.focusRange((mc.options.getEffectiveRenderDistance() + 2) * 16.0); // [perf3] beyond: not drawn, evicted first
      ticks++;
      if (ticks % 20 == 1) applyDetail(); // [perf] follow the tree-detail setting
      if (ticks % 20 == 7) { // [trees2] forget broken-out tree blocks once nothing of their tree is left
         var level = mc.level;
         var probe = new net.minecraft.core.BlockPos.MutableBlockPos();
         TreeScars.sweep(LiveWorld.INSTANCE, (x, y, z) -> level.isLoaded(probe.set(x, y, z)), 2048);
      }
      if (SUBJECTS.isEmpty()) return;
      boolean changed = false;
      double moved = Double.isNaN(scanX) ? Double.MAX_VALUE : (at.x - scanX) * (at.x - scanX) + (at.z - scanZ) * (at.z - scanZ);
      if (moved > 0.25 || ticks % 20 == 0) {
         scanX = at.x;
         scanZ = at.z;
         changed = retarget(at.x, at.z);
      }
      if (ticks % 200 == 0) prune(at.x, at.z, mc.options.getEffectiveRenderDistance());
      if (changed || backlog || ticks % 20 == 0) schedule(mc, at.x, at.z);
   }

   /** Moves subjects across the band edges (hysteresis); true when any changed level. */
   private static boolean retarget(double cx, double cz) {
      boolean changed = false;
      // [perf] two band edges: NEAR|FAR and FAR|IMPOSTOR, each entered further out than it is left
      double[] out2 = {(double)NEAR_OUT * NEAR_OUT, (double)impOut * impOut};
      double[] in2 = {(double)NEAR_IN * NEAR_IN, (double)impIn * impIn};
      for (Subject s : SUBJECTS.values()) {
         double dx = s.x - cx, dz = s.z - cz, d2 = dx * dx + dz * dz;
         int target = s.target;
         int want = target;
         while (want < IMPOSTOR && d2 > out2[want]) want++;
         while (want > NEAR && d2 < in2[want - 1]) want--;
         if (want != target) {
            s.target = want;
            changed = true;
         }
      }
      return changed;
   }

   // [perf2] schedule() scratch: candidates as parallel primitive arrays, scanned without per-entry allocation
   private static long[] staleSections = new long[256];
   private static double[] staleScores = new double[256];
   private static int staleCount;
   private static double scanCx, scanCz;
   private static int sectionRank;
   private static final java.util.function.BiConsumer<Long, Integer> RANK = (subject, was) -> {
      Subject s = SUBJECTS.get(subject);
      if (s == null) return;
      int target = s.target;
      // [perf] gaining full detail first, then 3D over impostor, then losing detail
      if (target != was) sectionRank = Math.min(sectionRank, target < was ? target : 2);
   };
   private static final java.util.function.BiConsumer<Long, ConcurrentHashMap<Long, Integer>> STALE = (section, built) -> {
      sectionRank = Integer.MAX_VALUE;
      built.forEach(RANK);
      if (sectionRank == Integer.MAX_VALUE) return;
      long p = section;
      double dx = ((int)(p >> 38) << 4) + 8 - scanCx, dz = ((int)(p << 26 >> 38) << 4) + 8 - scanCz;
      if (staleCount == staleSections.length) {
         staleSections = java.util.Arrays.copyOf(staleSections, staleCount * 2);
         staleScores = java.util.Arrays.copyOf(staleScores, staleCount * 2);
      }
      // rank first (0 gaining full detail, 1 gaining 3D, 2 losing detail), then nearest
      staleSections[staleCount] = p;
      staleScores[staleCount++] = sectionRank * 1.0e12 + dx * dx + dz * dz;
   };

   /**
    * Marks the most urgent sections drawn at a stale level dirty (gaining detail first, nearest first). [perf2] The
    * number per tick follows the frame-time governor (fewer while frames spike, see FrameGovernor), and the scan no
    * longer allocates per section.
    */
   private static void schedule(Minecraft mc, double cx, double cz) {
      long started = System.nanoTime();
      scanCx = cx;
      scanCz = cz;
      staleCount = 0;
      SECTIONS.forEach(STALE);
      int n = staleCount;
      int budget = com.formaworks.frontierhunts.perf.client.FrameGovernor.budget(REBUILDS_PER_TICK);
      backlog = n > budget;
      if (n > budget) {
         com.formaworks.frontierhunts.perf.client.PerfStats.add(com.formaworks.frontierhunts.perf.client.PerfStats.DIRTY_DEFERRED,
            Math.min(n, REBUILDS_PER_TICK) - budget);
      }
      int take = Math.min(budget, n);
      for (int i = 0; i < take; i++) {
         // partial selection: the i-th most urgent to slot i
         int best = i;
         for (int j = i + 1; j < n; j++) if (staleScores[j] < staleScores[best]) best = j;
         long section = staleSections[best];
         staleSections[best] = staleSections[i];
         staleScores[best] = staleScores[i];
         staleSections[i] = section;
         ConcurrentHashMap<Long, Integer> built = SECTIONS.get(section);
         if (built != null) {
            // Assume the rebuild draws the current targets; the build records what it really drew, and
            // a subject that has vanished from the section (a felled tree) is not rebuilt forever.
            for (Map.Entry<Long, Integer> b : built.entrySet()) {
               Subject s = SUBJECTS.get(b.getKey());
               if (s != null) b.setValue(s.target);
            }
         }
         int sx = (int)(section >> 38), sy = (int)(section << 52 >> 52), sz = (int)(section << 26 >> 38);
         mc.levelRenderer.setSectionDirty(sx, sy, sz);
      }
      // [perf2] builders skip re-recording what they recorded last (a per-thread memo); the levels assumed above can
      // differ from what a rebuild really draws (the target moved again before it ran), so invalidate the memos: a
      // section must never stay recorded at an assumed level it was not drawn at (it would be rebuilt every tick)
      if (take > 0) GENERATION.incrementAndGet();
      com.formaworks.frontierhunts.perf.client.PerfStats.add(com.formaworks.frontierhunts.perf.client.PerfStats.LOD_DIRTY, take);
      com.formaworks.frontierhunts.perf.client.PerfStats.since(com.formaworks.frontierhunts.perf.client.PerfStats.LOD_TICK_US, started);
   }

   /** Drops subjects and sections well outside the render distance (their sections were unloaded). */
   private static void prune(double cx, double cz, int renderDistance) {
      double limit = (renderDistance + 3) * 16.0, limit2 = limit * limit;
      SUBJECTS.values().removeIf(s -> (s.x - cx) * (s.x - cx) + (s.z - cz) * (s.z - cz) > limit2);
      SECTIONS.keySet().removeIf(p -> {
         double dx = ((int)(p >> 38) << 4) + 8 - cx, dz = ((int)(p << 26 >> 38) << 4) + 8 - cz;
         return dx * dx + dz * dz > limit2;
      });
   }

   /** [perf] F3 line: trees and floor columns per level, tracked sections, cached tree geometry. */
   public static String debugLine() {
      if (SUBJECTS.isEmpty()) return null;
      int[] c = counts();
      return "Frontier trees+floor: " + c[2] + " full, " + (c[0] - c[2] - c[3]) + " 3D-far, " + c[3] + " cutout (" + c[1] + " sections, "
         + TreeGrowth.cachedQuads() / 1000 + "k cached quads, cutouts beyond " + (impOut > 1e6F ? "never" : (int)impOut + "") + ")"
         + (com.formaworks.frontierhunts.perf.client.ShaderPerf.lighter() ? ", lighter for shader pack: " + applied : "") // [shaderperf]
         + (TreeScars.count() > 0 ? ", " + TreeScars.count() + " broken-out blocks remembered" : ""); // [trees2]
   }

   /** [perf2] One line on the tree cache for the hitch log: trees, quads and member entries against their budgets. */
   public static String cacheLine() {
      long[] c = TreeGrowth.cacheStats();
      int[] l = counts();
      return "tree cache: " + c[0] + " trees, " + c[1] / 1000 + "k / " + c[2] / 1000 + "k quads, " + c[3] / 1000 + "k / " + c[4] / 1000
         + "k members, " + c[5] / 1000 + "k cells; LOD subjects " + l[0] + " (" + l[2] + " full, " + l[3] + " cutout), " + l[1] + " sections, cutouts beyond "
         + (impOut > 1e6F ? "never" : (int)impOut + "") + ", full detail inside " + (int)NEAR_IN
         + ", drawn as " + applied; // [shaderperf] the applied detail (lighter than the setting under a shader pack)
   }

   /** Diagnostics: {subjects, sections, near subjects, [perf] impostor subjects}. */
   static int[] counts() {
      int near = 0, cards = 0;
      for (Subject s : SUBJECTS.values()) {
         if (s.target == NEAR) near++;
         else if (s.target == IMPOSTOR) cards++;
      }
      return new int[]{SUBJECTS.size(), SECTIONS.size(), near, cards};
   }
}
