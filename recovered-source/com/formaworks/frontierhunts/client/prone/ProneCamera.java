package com.formaworks.frontierhunts.client.prone;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.prone.Prone;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * [rifle] Eye-height transition for going prone / getting up. Vanilla halves the gap every tick (a quick drop);
 * here the eye follows a smootherstep over ~0.45 s down / ~0.55 s up, evaluated per tick and interpolated per frame
 * by the camera as usual, plus a slight head nod while the body moves (camera only, off with Reduced Motion).
 * Only for the local player and only for prone changes; vanilla crawling under blocks is untouched.
 */
public final class ProneCamera {
   private static final int DOWN_TICKS = 9;
   private static final int UP_TICKS = 11;

   private static boolean lastProne;
   private static int age = -1;
   private static int duration = 1;
   private static float from;
   private static boolean down;

   private ProneCamera() {
   }

   /**
    * Called at the end of Camera#tick with the camera's previous and vanilla-updated eye height.
    * Returns the eye height to use instead, or NaN to keep vanilla's.
    */
   public static float eye(Entity entity, float previous, float vanilla) {
      Minecraft mc = Minecraft.getInstance();
      if (entity == null || entity != mc.player) {
         return Float.NaN;
      }
      boolean prone = Prone.isProne(entity);
      if (prone != lastProne) {
         lastProne = prone;
         down = prone;
         from = previous;
         duration = prone ? DOWN_TICKS : UP_TICKS;
         age = 0;
      }
      if (age < 0) {
         return Float.NaN;
      }
      age++;
      float target = entity.getEyeHeight();
      if (age >= duration) { // the pose itself may change a tick after the flag (camera ticks before the player)
         age = -1;
         return Float.NaN;
      }
      return from + (target - from) * smoother(age / (float)duration);
   }

   /** Extra camera pitch (degrees) while the body moves; 0 when idle. */
   static float nod(float partial) {
      if (age < 0 || HuntConfig.REDUCED_MOTION.get()) {
         return 0.0F;
      }
      float k = Math.clamp((age + partial) / (float)duration, 0.0F, 1.0F);
      float bump = (float)Math.sin(Math.PI * k);
      return (down ? 2.2F : -1.4F) * bump * bump;
   }

   static void reset() {
      lastProne = false;
      age = -1;
   }

   private static float smoother(float t) {
      t = Math.clamp(t, 0.0F, 1.0F);
      return t * t * t * (t * (t * 6.0F - 15.0F) + 10.0F);
   }
}
