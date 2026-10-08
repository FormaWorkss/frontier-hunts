package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.expedition.ScoutingNetwork;
import com.formaworks.frontierhunts.hunting.Whitetail;
import net.minecraft.client.gui.GuiGraphics;

/**
 * [trailcam] Access to package-private client helpers the trail camera darkroom needs: the whitetail pose animator (so
 * a freshly built stand-in settles into its walking/grazing pose before the shutter) and the old composite frame,
 * which stays as the fallback when a real photo cannot be developed.
 */
public final class TrailcamBridge {
   private TrailcamBridge() {
   }

   /**
    * Runs the whitetail animator over the two seconds before the recorded moment so blend weights, gait phase and head
    * pose converge instead of starting from the rest pose.
    */
   public static void preroll(Whitetail deer) {
      int end = deer.tickCount;
      for (int i = 40; i >= 0; i--) {
         deer.tickCount = end - i;
         WhitetailRenderer.pose(deer, 0.0F);
      }
      deer.tickCount = end;
   }

   public static void composite(GuiGraphics g, int x, int y, int w, int h, ScoutingNetwork.Frame frame, long seed, float time) {
      CameraPhoto.draw(g, x, y, w, h, frame, seed, new byte[0], time);
   }

   public static void empty(GuiGraphics g, int x, int y, int w, int h, byte sky, long seed, String text, float time) {
      CameraPhoto.empty(g, x, y, w, h, sky, seed, new byte[0], text, time);
   }
}
