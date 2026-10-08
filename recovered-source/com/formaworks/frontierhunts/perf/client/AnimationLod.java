package com.formaworks.frontierhunts.perf.client;

import com.formaworks.frontierhunts.perf.PerfConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * [perf] Animation and skinning rate for animals by how big they are on screen.
 *
 * <p>The measure is the camera distance divided by the animal's height and by the zoom (a scope or
 * binoculars make a far animal big again): "effective distance". Up close (under 20) an animal is
 * animated every frame, as before. Further out its pose is updated at most 60, 30 or 20 times a second;
 * between updates it keeps its last pose while its body still moves smoothly with the entity (only the
 * limbs' phase waits for the next update, and the animator is given the whole elapsed time then, so the
 * gait never slows down). At high frame rates this skips most far updates; at 40 FPS or less almost none.
 *
 * <p>Also holds the per-frame budgets of the animal renderers ({@link RenderBudget}).
 */
@EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT)
public final class AnimationLod {
   /** Effective distances (blocks per block of height, at the normal field of view) of the rate steps. */
   static final double FULL = 20, HALF = 45, THIRD = 90;

   private static int updated, held, lastUpdated, lastHeld;
   private static boolean enabled = true;
   private static long frames;

   private AnimationLod() {
   }

   /** Minimum seconds between two pose updates at this effective distance (0: every frame). */
   public static float interval(double effective) {
      if (!enabled || effective < FULL) return 0;
      if (effective < HALF) return 1 / 60.0F;
      if (effective < THIRD) return 1 / 30.0F;
      return 1 / 20.0F;
   }

   /**
    * Whether an animal last posed {@code sinceLast} seconds ago is due for a new pose at this effective
    * distance (counts both outcomes for the F3 screen).
    */
   public static boolean due(float sinceLast, double effective) {
      boolean due = sinceLast >= interval(effective) - 1e-4F;
      if (due) updated++;
      else held++;
      return due;
   }

   /**
    * Camera distance per block of height, corrected for zoom: {@code distance} blocks away, {@code height}
    * tall, {@code zoom} the magnification (1 at the normal field of view).
    */
   public static double effective(double distance, double height, double zoom) {
      return distance / Math.max(0.4, height) / Math.max(1.0, zoom);
   }

   @SubscribeEvent
   public static void frame(RenderFrameEvent.Pre event) {
      frames++;
      if ((frames & 63) == 1) enabled = PerfConfig.animationLod();
      lastUpdated = updated;
      lastHeld = held;
      updated = held = 0;
      RenderBudget.beginFrame();
   }

   /** Diagnostics: {animals posed, animals holding their pose} in the last frame. */
   public static int[] lastFrame() {
      return new int[]{lastUpdated, lastHeld};
   }
}
