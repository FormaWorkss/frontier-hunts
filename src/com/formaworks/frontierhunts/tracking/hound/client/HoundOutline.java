package com.formaworks.frontierhunts.tracking.hound.client;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.tracking.hound.TrackingHound;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * [1.1.6] Finding your dog in thick brush: your own tracking hound is outlined (blaze orange, like its collar) when it
 * is more than 4 blocks from you, so you can follow it through grass, ferns and timber. Other players' hounds are not
 * outlined. Setting: Interface &amp; HUD &rarr; Comfort &rarr; Hound outline.
 */
public final class HoundOutline {
   private HoundOutline() {
   }

   public static boolean show(Entity e) {
      if (!(e instanceof TrackingHound h)) {
         return false;
      }
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || !h.isOwnedBy(mc.player) || h.isInSittingPose() && h.distanceToSqr(mc.player) < 64.0) {
         return false;
      }
      try {
         if (!HuntConfig.HOUND_OUTLINE.get()) {
            return false;
         }
      } catch (RuntimeException ex) {
         return false;
      }
      return h.distanceToSqr(mc.player) > 16.0;
   }
}
