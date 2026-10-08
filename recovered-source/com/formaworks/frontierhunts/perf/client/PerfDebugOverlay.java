package com.formaworks.frontierhunts.perf.client;

import com.formaworks.frontierhunts.perf.AiThrottle;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

/**
 * [perf] F3 lines: tree levels of detail, animal animation rate, and (singleplayer) wildlife AI tiers.
 */
@EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT)
public final class PerfDebugOverlay {
   private PerfDebugOverlay() {
   }

   @SubscribeEvent
   public static void debug(CustomizeGuiOverlayEvent.DebugText event) {
      try {
         Minecraft mc = Minecraft.getInstance();
         if (mc.getDebugOverlay() == null || !mc.getDebugOverlay().showDebugScreen()) return;
         var right = event.getRight();
         right.add("");
         String trees = com.formaworks.frontierhunts.client.tree.TreeLod.debugLine();
         if (trees != null) right.add(trees);
         int[] a = AnimationLod.lastFrame();
         right.add("Frontier animals: " + a[0] + " posed, " + a[1] + " holding (frame)");
         if (mc.hasSingleplayerServer()) {
            int[] t = AiThrottle.lastSecond();
            right.add("Frontier AI ticks/s: " + t[0] + " full, " + t[1] + " reduced, " + t[2] + " minimal");
         }
         HitchLogger.debugLines(right); // [perf2] only while the hitch logger runs
      } catch (RuntimeException e) {
         // diagnostics only
      }
   }
}
