package com.formaworks.frontierhunts.perf.client;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * [1.1.5] A repeatable FPS measurement for the performance guide: {@code /frontierperf benchmark [seconds]}.
 *
 * <p>The view turns one full circle at the horizon over the run (default 60 s) so every run looks at the same scene
 * from where you stand; then frame times, FPS, 1% / 0.1% lows, memory and garbage collection are written to
 * {@code logs/frontierhunts-benchmark-<time>.txt} together with the settings that matter (preset, render distance,
 * Sodium / Iris / Distant Horizons, shader pack on or off, GPU) and summarised in chat. Stand still while it runs;
 * Escape or opening any screen stops it.
 */
@EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT)
public final class Benchmark {
   private Benchmark() {
   }

   private static boolean running;
   private static long startNs, lastNs, durationNs;
   private static float startYaw;
   private static float[] frames = new float[0];
   private static int count;
   private static long heapPeak, heapSum;
   private static long gcStartMs, gcStartCount;

   static boolean running() {
      return running;
   }

   static String start(int seconds) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.level == null) {
         return "Join a world first.";
      }
      if (running) {
         return "A benchmark is already running.";
      }
      durationNs = seconds * 1_000_000_000L;
      frames = new float[Math.max(1024, seconds * 400)];
      count = 0;
      heapPeak = heapSum = 0;
      startYaw = mc.player.getYRot();
      long[] gc = gc();
      gcStartMs = gc[0];
      gcStartCount = gc[1];
      startNs = lastNs = System.nanoTime();
      running = true;
      return "Benchmark started: " + seconds + " s, the view turns once around. Stand still; Escape stops it.";
   }

   @SubscribeEvent
   public static void frame(RenderFrameEvent.Pre e) {
      if (!running) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.screen != null) {
         running = false;
         if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("Frontier benchmark stopped (a screen was opened)."), false);
         }
         return;
      }
      long now = System.nanoTime();
      if (now != lastNs && count < frames.length && now - startNs > 0) {
         frames[count++] = (now - lastNs) / 1.0E6F;
      }
      lastNs = now;
      Runtime rt = Runtime.getRuntime();
      long used = rt.totalMemory() - rt.freeMemory();
      heapPeak = Math.max(heapPeak, used);
      heapSum += used >> 20;
      float t = Math.min(1.0F, (now - startNs) / (float)durationNs);
      mc.player.setYRot(startYaw + 360.0F * t);
      mc.player.setXRot(0.0F);
      mc.player.yRotO = mc.player.getYRot();
      mc.player.xRotO = 0.0F;
      if (now - startNs >= durationNs) {
         running = false;
         finish(mc);
      }
   }

   private static long[] gc() {
      long ms = 0, n = 0;
      for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) {
         ms += Math.max(0, b.getCollectionTime());
         n += Math.max(0, b.getCollectionCount());
      }
      return new long[]{ms, n};
   }

   private static void finish(Minecraft mc) {
      int n = count;
      if (n < 10) {
         mc.player.displayClientMessage(Component.literal("Frontier benchmark: too few frames recorded."), false);
         return;
      }
      float[] f = Arrays.copyOf(frames, n);
      double total = 0;
      for (float v : f) {
         total += v;
      }
      Arrays.sort(f);
      double avgFps = 1000.0 * n / total;
      double low1 = lowFps(f, 0.01), low01 = lowFps(f, 0.001);
      long[] gc = gc();
      long heapMax = Runtime.getRuntime().maxMemory() >> 20;
      String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
      StringBuilder sb = new StringBuilder(1024);
      sb.append("FrontierHunts benchmark ").append(time).append('\n');
      try {
         sb.append("mod version ").append(net.neoforged.fml.ModList.get().getModContainerById("frontierhunts").map(c -> c.getModInfo().getVersion().toString()).orElse("?")).append('\n');
      } catch (RuntimeException e) {
         // version not readable
      }
      sb.append(HitchLogger.header().trim()).append('\n');
      sb.append(String.format(Locale.ROOT, "position %.0f %.0f %.0f in %s, %s%n", mc.player.getX(), mc.player.getY(), mc.player.getZ(),
         mc.level.dimension().location(), mc.level.isRaining() ? "raining" : "dry"));
      sb.append(String.format(Locale.ROOT, "frames %d over %.1f s%n", n, total / 1000.0));
      sb.append(String.format(Locale.ROOT, "average %.1f fps, 1%% low %.1f fps, 0.1%% low %.1f fps%n", avgFps, low1, low01));
      sb.append(String.format(Locale.ROOT, "frame time p50 %.1f ms, p95 %.1f ms, p99 %.1f ms, worst %.1f ms%n", pct(f, 0.5), pct(f, 0.95), pct(f, 0.99), f[n - 1]));
      sb.append(String.format(Locale.ROOT, "heap average %d MB, peak %d MB of %d MB; GC %d collections, %d ms%n", heapSum / n, heapPeak >> 20, heapMax,
         gc[1] - gcStartCount, gc[0] - gcStartMs));
      sb.append(String.format(Locale.ROOT, "entities %d, particles %s%n", mc.level.getEntityCount(), mc.particleEngine.countParticles()));
      String text = sb.toString();
      Path file = mc.gameDirectory.toPath().resolve("logs").resolve("frontierhunts-benchmark-" + time + ".txt");
      try {
         Files.createDirectories(file.getParent());
         Files.writeString(file, text, StandardCharsets.UTF_8);
      } catch (Exception e) {
         file = null;
      }
      mc.player.displayClientMessage(Component.literal(String.format(Locale.ROOT, "Frontier benchmark: %.0f fps average, 1%% low %.0f, worst frame %.0f ms.%s",
         avgFps, low1, f[n - 1], file == null ? "" : " Saved to logs/" + file.getFileName())), false);
   }

   /** average FPS over the slowest fraction of frames */
   private static double lowFps(float[] sorted, double fraction) {
      int k = Math.max(1, (int)Math.round(sorted.length * fraction));
      double sum = 0;
      for (int i = sorted.length - k; i < sorted.length; i++) {
         sum += sorted[i];
      }
      return 1000.0 * k / sum;
   }

   private static float pct(float[] sorted, double p) {
      return sorted[Math.min(sorted.length - 1, (int)Math.floor(sorted.length * p))];
   }
}
