package com.formaworks.frontierhunts.perf.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * [perf2] Frame-time feedback for the mod's own chunk-section rebuild requests (tree level-of-detail swaps, season
 * stage sweeps). A rebuild costs nothing on the render thread when it is requested; it costs later, when Sodium's
 * workers grow and mesh the section and the render thread uploads the (for realistic trees, large) mesh. So the
 * budget is steered by what the frames actually cost: every frame that spikes well above the running typical frame
 * time halves the share of the per-tick rebuild budget the mod may use (down to a quarter), and smooth ticks give
 * it back (+5% per tick, about 0.75 s from a quarter to full). Rebuilds held back stay queued and go out on the next
 * ticks, nearest first, so nothing is lost; only a burst is spread out.
 *
 * <p>Also the one place that times frames and client ticks, for {@link HitchLogger}. Two nanoTime reads per frame
 * and two per tick; no allocation.
 */
@EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT)
public final class FrameGovernor {
   private FrameGovernor() {
   }

   private static long lastFrame;
   /** The last frame's length (ms) and a slow running typical frame time (ms, spikes clamped out). */
   private static float lastMs = 16, typicalMs = 16;
   /** Share of the rebuild budgets the mod may use right now (0.25 .. 1). */
   private static volatile float share = 1;
   private static boolean spikedSinceTick;
   private static long tickStart;
   /** Client tick time accumulated since the last frame (ns): handed to the hitch logger. */
   private static long tickNanos;

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void frame(RenderFrameEvent.Pre event) {
      long now = System.nanoTime();
      if (lastFrame != 0) {
         float ms = (now - lastFrame) / 1.0e6F;
         lastMs = ms;
         // a spike: well above the typical frame and long enough to see (vsync-limited frames never count)
         if (ms > typicalMs * 1.6F + 4.0F && ms > 12.0F) {
            spikedSinceTick = true;
            share = Math.max(0.25F, share * 0.5F);
         }
         // typical frame time: slow average with spikes clamped, so one hitch does not raise the bar
         typicalMs += (Math.min(ms, typicalMs * 2.0F) - typicalMs) * 0.04F;
         HitchLogger.frame(now, ms, tickNanos);
      }
      tickNanos = 0;
      lastFrame = now;
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void tickStart(ClientTickEvent.Pre event) {
      tickStart = System.nanoTime();
   }

   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void tickEnd(ClientTickEvent.Post event) {
      if (tickStart != 0) tickNanos += System.nanoTime() - tickStart;
      tickStart = 0;
      if (!spikedSinceTick) share = Math.min(1.0F, share + 0.05F);
      spikedSinceTick = false;
   }

   /** The part of a per-tick rebuild budget the mod may use this tick (at least 1 while {@code base} > 0). */
   public static int budget(int base) {
      if (base <= 0) return 0;
      return Math.max(1, Math.round(base * share));
   }

   /** Share of the rebuild budgets in use (diagnostics). */
   public static float share() {
      return share;
   }

   public static float lastFrameMs() {
      return lastMs;
   }

   public static float typicalFrameMs() {
      return typicalMs;
   }
}
