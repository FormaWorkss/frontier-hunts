package com.formaworks.frontierhunts.perf.client;

import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;

/**
 * [perf2] Client hitch logger for profiling the Ultra preset. Off by default: {@code performance.hitchLogger = true}
 * in {@code config/frontierhunts-client.toml}, or {@code /frontierperf on} (until {@code /frontierperf off} or the
 * game restarts).
 *
 * <p>While on, every frame longer than {@code performance.hitchThresholdMs} (25 ms) is recorded with what happened
 * in that frame and in the half second before it: the {@link PerfStats} counters (trees grown and how long that
 * took, trees shared between workers / evicted / regrown for a level, seasonal cells, sections dirtied by tree LOD,
 * by the season sweep and in total, wildlife meshes skinned, deer posed, textures decoded, whole-world reloads),
 * the client tick time, garbage-collector pauses (GarbageCollectorMXBean time and count), heap use and the particle
 * count. Every 10 s a summary (fps, p50/p95/p99/worst frame time, hitch count, tick time, GC, counter rates) plus
 * the hitches of that window is appended to {@code logs/frontierhunts-perf.log} on a background thread. While on,
 * F3 shows two running lines.
 *
 * <p>Off: one boolean test per frame; no allocation, no MXBean calls, no file. On: no allocation per frame either
 * (preallocated arrays); text is only built once per 10 s window and when F3 is open.
 */
public final class HitchLogger {
   private HitchLogger() {
   }

   private static final int MAX_SPIKES = 96;
   private static final int HIST = 1200; // quarter-millisecond buckets up to 300 ms
   private static final long WINDOW_NS = 10_000_000_000L;

   /** Command override: null = follow the config. */
   private static volatile Boolean command;
   private static boolean on;
   private static long configCheck;
   private static float threshold = 25;

   // counters: current, last frame, half-second ring, last second, window start
   private static final long[] cur = new long[PerfStats.COUNT], prevFrame = new long[PerfStats.COUNT];
   private static final long[][] ring = new long[4][PerfStats.COUNT];
   private static final long[] ringTime = new long[4];
   private static int ringAt;
   private static final long[] secondSnap = new long[PerfStats.COUNT], lastSecond = new long[PerfStats.COUNT], windowSnap = new long[PerfStats.COUNT];
   private static long secondStart, windowStart;
   private static float lastSecondGcMs;
   private static int lastSecondFrames;

   // GC
   private static GarbageCollectorMXBean[] gcs = new GarbageCollectorMXBean[0];
   private static long gcTimePrev, gcCountPrev, gcTimeSecond, gcTimeWindow, gcCountWindow;

   // window
   private static final int[] hist = new int[HIST + 1];
   private static int frames, hitches, framesSecond;
   private static double sumMs, sumTickMs;
   private static float worstMs, worstTickMs, minShare = 1;

   // hitches of the window
   private static int spikes;
   private static final long[] spikeAt = new long[MAX_SPIKES];
   private static final float[] spikeMs = new float[MAX_SPIKES], spikeTick = new float[MAX_SPIKES], spikeGcMs = new float[MAX_SPIKES];
   private static final int[] spikeGcCount = new int[MAX_SPIKES], spikeParticles = new int[MAX_SPIKES], spikeHeapMb = new int[MAX_SPIKES];
   private static final boolean[] spikeScreen = new boolean[MAX_SPIKES];
   private static final long[][] spikeFrame = new long[MAX_SPIKES][PerfStats.COUNT], spikeBefore = new long[MAX_SPIKES][PerfStats.COUNT];
   private static final float[] spikeBeforeSec = new float[MAX_SPIKES];

   private static volatile String pendingNote;
   private static volatile boolean flushRequested;

   private static ExecutorService writer;
   private static Path file;

   // ------------------------------------------------------------------ control

   /** {@code /frontierperf on|off}; null returns to the config setting. */
   public static void command(Boolean enabled) {
      command = enabled;
      configCheck = 0;
   }

   public static boolean enabled() {
      return on;
   }

   /** Writes the current window now (and a note line, when given). */
   public static void flush(String note) {
      if (note != null) pendingNote = note;
      flushRequested = true;
   }

   public static Path file() {
      return logFile();
   }

   // ------------------------------------------------------------------ per frame (render thread)

   /** Called by {@link FrameGovernor} at the start of every frame with the previous frame's length. */
   static void frame(long now, float ms, long tickNanos) {
      if ((configCheck++ & 127) == 0) {
         boolean want = command != null ? command : com.formaworks.frontierhunts.perf.PerfConfig.hitchLogger();
         threshold = com.formaworks.frontierhunts.perf.PerfConfig.hitchThresholdMs();
         if (want != on) {
            if (want) start(now);
            else stop();
         }
      }
      if (!on) return;
      try {
         record(now, ms, tickNanos / 1.0e6F);
      } catch (RuntimeException e) {
         on = false; // diagnostics must never break the game
         command = false;
      }
   }

   private static void start(long now) {
      on = true;
      List<GarbageCollectorMXBean> list = new ArrayList<>();
      for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) {
         String n = b.getName();
         // pauses only: concurrent cycles (G1 Concurrent GC, ZGC Cycles) run beside the game
         if (n.contains("Concurrent") || n.contains("Cycles")) continue;
         list.add(b);
      }
      gcs = list.toArray(new GarbageCollectorMXBean[0]);
      gcTimePrev = gcTime();
      gcCountPrev = gcCount();
      PerfStats.snapshot(cur);
      System.arraycopy(cur, 0, prevFrame, 0, cur.length);
      for (int i = 0; i < 4; i++) {
         System.arraycopy(cur, 0, ring[i], 0, cur.length);
         ringTime[i] = now;
      }
      System.arraycopy(cur, 0, secondSnap, 0, cur.length);
      secondStart = now;
      resetWindow(now);
      write(header());
   }

   private static void stop() {
      on = false;
      write(summary(System.nanoTime()) + "logger off\n");
   }

   private static void resetWindow(long now) {
      windowStart = now;
      System.arraycopy(cur, 0, windowSnap, 0, cur.length);
      java.util.Arrays.fill(hist, 0);
      frames = hitches = spikes = 0;
      sumMs = sumTickMs = 0;
      worstMs = worstTickMs = 0;
      minShare = 1;
      gcTimeWindow = gcCountWindow = 0;
   }

   private static long gcTime() {
      long t = 0;
      for (GarbageCollectorMXBean b : gcs) t += Math.max(0, b.getCollectionTime());
      return t;
   }

   private static long gcCount() {
      long c = 0;
      for (GarbageCollectorMXBean b : gcs) c += Math.max(0, b.getCollectionCount());
      return c;
   }

   private static void record(long now, float ms, float tickMs) {
      PerfStats.snapshot(cur);
      long gt = gcTime(), gc = gcCount();
      long dGcMs = gt - gcTimePrev, dGcCount = gc - gcCountPrev;
      gcTimePrev = gt;
      gcCountPrev = gc;
      gcTimeWindow += dGcMs;
      gcCountWindow += dGcCount;
      gcTimeSecond += dGcMs;

      frames++;
      framesSecond++;
      sumMs += ms;
      sumTickMs += tickMs;
      hist[Math.min(HIST, (int)(ms * 4))]++;
      worstMs = Math.max(worstMs, ms);
      worstTickMs = Math.max(worstTickMs, tickMs);
      minShare = Math.min(minShare, FrameGovernor.share());

      Minecraft mc = Minecraft.getInstance();
      if (ms > threshold) {
         hitches++;
         if (spikes < MAX_SPIKES) {
            int s = spikes++;
            spikeAt[s] = now - windowStart;
            spikeMs[s] = ms;
            spikeTick[s] = tickMs;
            spikeGcMs[s] = dGcMs;
            spikeGcCount[s] = (int)dGcCount;
            Runtime rt = Runtime.getRuntime();
            spikeHeapMb[s] = (int)((rt.totalMemory() - rt.freeMemory()) >> 20);
            spikeScreen[s] = mc.screen != null || mc.level == null;
            spikeParticles[s] = particles(mc);
            long[] f = spikeFrame[s], b = spikeBefore[s];
            // the oldest half-second snapshot in the ring: what led up to this frame
            int oldest = (ringAt + 1) & 3;
            long[] before = ring[oldest];
            for (int i = 0; i < PerfStats.COUNT; i++) {
               f[i] = cur[i] - prevFrame[i];
               b[i] = prevFrame[i] - before[i];
            }
            spikeBeforeSec[s] = (now - ringTime[oldest]) / 1.0e9F;
         }
      }
      System.arraycopy(cur, 0, prevFrame, 0, cur.length);
      // a snapshot every ~160 ms: the ring spans about half a second before any frame
      if (now - ringTime[ringAt] > 160_000_000L) {
         ringAt = (ringAt + 1) & 3;
         System.arraycopy(cur, 0, ring[ringAt], 0, cur.length);
         ringTime[ringAt] = now;
      }
      if (now - secondStart >= 1_000_000_000L) {
         for (int i = 0; i < PerfStats.COUNT; i++) {
            lastSecond[i] = cur[i] - secondSnap[i];
            secondSnap[i] = cur[i];
         }
         float seconds = (now - secondStart) / 1.0e9F;
         lastSecondGcMs = gcTimeSecond / seconds;
         lastSecondFrames = Math.round(framesSecond / seconds);
         gcTimeSecond = 0;
         framesSecond = 0;
         secondStart = now;
      }
      if (now - windowStart >= WINDOW_NS || flushRequested) {
         flushRequested = false;
         String note = pendingNote;
         pendingNote = null;
         write(summary(now) + (note != null ? "note: " + note + "\n" : ""));
         resetWindow(now);
      }
   }

   private static int particles(Minecraft mc) {
      try {
         return mc.particleEngine == null ? -1 : Integer.parseInt(mc.particleEngine.countParticles());
      } catch (RuntimeException e) {
         return -1;
      }
   }

   // ------------------------------------------------------------------ text

   private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

   static String header() {
      Minecraft mc = Minecraft.getInstance();
      StringBuilder sb = new StringBuilder(512);
      sb.append("\n==== Frontier Hunts hitch logger started ").append(LocalDateTime.now().format(TIME)).append(" ====\n");
      try {
         sb.append("preset ").append(com.formaworks.frontierhunts.HuntConfig.GRAPHICS_PRESET.get())
            .append(", world look ").append(com.formaworks.frontierhunts.HuntConfig.WORLD_LOOK.get())
            .append(", tree detail ").append(com.formaworks.frontierhunts.perf.PerfConfig.treeDetail())
            .append(", animals ").append(com.formaworks.frontierhunts.HuntConfig.ANIMAL_STYLE.get())
            .append('/').append(com.formaworks.frontierhunts.HuntConfig.ANIMAL_DETAIL.get())
            .append(", effects ").append(com.formaworks.frontierhunts.HuntConfig.QUALITY.get()).append('\n');
      } catch (RuntimeException e) {
         sb.append("(config not readable)\n");
      }
      try {
         sb.append("render distance ").append(mc.options.getEffectiveRenderDistance())
            .append(", shader pack ").append(com.formaworks.frontierhunts.client.ShaderState.on ? "on" : "off")
            .append(" [").append(ShaderPerf.line()).append(']') // [shaderperf]
            .append(", sodium ").append(net.neoforged.fml.ModList.get().isLoaded("sodium"))
            .append(", iris ").append(net.neoforged.fml.ModList.get().isLoaded("iris"))
            .append(", distanthorizons ").append(net.neoforged.fml.ModList.get().isLoaded("distanthorizons")).append('\n');
         sb.append("GPU ").append(com.mojang.blaze3d.platform.GlUtil.getRenderer()).append(" / ").append(com.mojang.blaze3d.platform.GlUtil.getVendor()).append('\n');
      } catch (RuntimeException e) {
         sb.append("(renderer info not readable)\n");
      }
      Runtime rt = Runtime.getRuntime();
      sb.append("heap max ").append(rt.maxMemory() >> 20).append(" MB, cores ").append(rt.availableProcessors())
         .append(", java ").append(System.getProperty("java.version")).append(", GC");
      for (GarbageCollectorMXBean b : gcs) sb.append(" [").append(b.getName()).append(']');
      sb.append("\nhitch threshold ").append(threshold).append(" ms. Counters per hitch: 'frame' = during that frame, 'before' = the ~0.5 s before it.\n");
      sb.append(com.formaworks.frontierhunts.client.tree.TreeLod.cacheLine()).append('\n');
      return sb.toString();
   }

   private static float percentile(float p) {
      int target = (int)Math.ceil(frames * p), n = 0;
      for (int i = 0; i <= HIST; i++) {
         n += hist[i];
         if (n >= target) return (i + 1) / 4.0F;
      }
      return HIST / 4.0F;
   }

   private static String summary(long now) {
      float seconds = Math.max(0.001F, (now - windowStart) / 1.0e9F);
      StringBuilder sb = new StringBuilder(2048);
      sb.append('[').append(LocalDateTime.now().format(TIME)).append("] ");
      if (frames == 0) return sb.append("no frames\n").toString();
      sb.append(String.format(Locale.ROOT,
         "%.1f s: %d frames, %.1f fps avg, frame p50 %.2f / p95 %.2f / p99 %.2f / worst %.1f ms, %d hitches > %.0f ms; tick avg %.2f max %.1f ms; GC %d pauses %d ms; rebuild share min %.0f%%%n",
         seconds, frames, frames / seconds, percentile(0.5F), percentile(0.95F), percentile(0.99F), worstMs, hitches, threshold,
         sumTickMs / frames, worstTickMs, gcCountWindow, gcTimeWindow, minShare * 100));
      sb.append("  per second:");
      counters(sb, cur, windowSnap, 1 / seconds);
      sb.append('\n');
      sb.append("  ").append(com.formaworks.frontierhunts.client.tree.TreeLod.cacheLine()).append('\n');
      for (int s = 0; s < spikes; s++) {
         sb.append(String.format(Locale.ROOT, "  hitch +%.2fs %.1f ms (tick %.1f ms, GC %d x %.0f ms, heap %d MB, particles %d%s)%n    frame:",
            spikeAt[s] / 1.0e9F, spikeMs[s], spikeTick[s], spikeGcCount[s], spikeGcMs[s], spikeHeapMb[s], spikeParticles[s],
            spikeScreen[s] ? ", menu/loading" : ""));
         counters(sb, spikeFrame[s], null, 1);
         sb.append(String.format(Locale.ROOT, "%n    before (%.2f s):", spikeBeforeSec[s]));
         counters(sb, spikeBefore[s], null, 1);
         sb.append('\n');
      }
      if (hitches > spikes) sb.append("  (+").append(hitches - spikes).append(" more hitches not itemised)\n");
      return sb.toString();
   }

   /** Appends the non-zero counters (a - b, or a alone when b is null) times {@code scale}. */
   private static void counters(StringBuilder sb, long[] a, long[] b, float scale) {
      boolean any = false;
      for (int i = 0; i < PerfStats.COUNT; i++) {
         long v = b == null ? a[i] : a[i] - b[i];
         if (v == 0) continue;
         any = true;
         float x = v * scale;
         if (PerfStats.micros(i)) x /= 1000F;
         sb.append(' ').append(PerfStats.NAMES[i]).append('=');
         if (x == Math.rint(x) && Math.abs(x) < 1e9) sb.append((long)x);
         else sb.append(String.format(Locale.ROOT, "%.1f", x));
      }
      if (!any) sb.append(" -");
   }

   /** F3 lines while the logger runs (built only while F3 is open). */
   public static void debugLines(List<String> right) {
      if (!on) return;
      right.add(String.format(Locale.ROOT, "Frontier perf log: %d hitches this window (worst %.0f ms), frame %.1f / typical %.1f ms, %d fps, rebuild share %.0f%%",
         hitches, worstMs, FrameGovernor.lastFrameMs(), FrameGovernor.typicalFrameMs(), lastSecondFrames, FrameGovernor.share() * 100));
      long[] s = lastSecond;
      right.add(String.format(Locale.ROOT, "  /s: trees %d grown (%.0f ms) %d shared %d evicted %d relevel | dirty lod %d season %d all %d (held %d)",
         s[PerfStats.TREES_GROWN], s[PerfStats.TREE_GROW_US] / 1000F, s[PerfStats.TREES_SHARED], s[PerfStats.TREES_EVICTED],
         s[PerfStats.TREES_REGROWN_LEVEL], s[PerfStats.LOD_DIRTY], s[PerfStats.SEASON_DIRTY], s[PerfStats.SECTIONS_DIRTY], s[PerfStats.DIRTY_DEFERRED]));
      right.add(String.format(Locale.ROOT, "  /s: season cells %d, skinned %d, deer posed %d, textures %d, reloads %d, GC %.0f ms",
         s[PerfStats.SEASON_CELLS], s[PerfStats.WILDLIFE_SKINNED], s[PerfStats.DEER_POSED], s[PerfStats.TEXTURES_DECODED],
         s[PerfStats.ALL_CHANGED], lastSecondGcMs));
   }

   // ------------------------------------------------------------------ file

   private static Path logFile() {
      if (file == null) {
         file = Minecraft.getInstance().gameDirectory.toPath().resolve("logs").resolve("frontierhunts-perf.log");
      }
      return file;
   }

   private static void write(String text) {
      Path path = logFile();
      if (writer == null) {
         writer = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Frontier Hunts perf log");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
         });
      }
      writer.execute(() -> {
         try {
            Files.createDirectories(path.getParent());
            // keep the log bounded: start over past 16 MB
            boolean big = Files.exists(path) && Files.size(path) > 16L << 20;
            Files.writeString(path, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
               big ? StandardOpenOption.TRUNCATE_EXISTING : StandardOpenOption.APPEND, StandardOpenOption.WRITE);
         } catch (IOException | RuntimeException e) {
            // diagnostics only
         }
      });
   }

   /** Records a whole-world renderer reload and (logger on) who asked for it. */
   public static void allChanged() {
      PerfStats.inc(PerfStats.ALL_CHANGED);
      if (!on) return;
      String caller = StackWalker.getInstance().walk(s -> s
         .filter(f -> !f.getClassName().startsWith("net.minecraft.client.renderer.LevelRenderer")
            && !f.getClassName().contains("perf.client") && !f.getClassName().contains("mixin"))
         .limit(3).map(f -> f.getClassName().substring(f.getClassName().lastIndexOf('.') + 1) + "." + f.getMethodName())
         .reduce((x, y) -> x + " < " + y).orElse("?"));
      write("[" + LocalDateTime.now().format(TIME) + "] world renderer reload (allChanged) from " + caller + "\n");
   }
}
