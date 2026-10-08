package com.formaworks.frontierhunts.survival.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.survival.SurvivalConfig;
import com.formaworks.frontierhunts.survival.SurvivalMath;
import com.formaworks.frontierhunts.survival.SurvivalNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * [survival] Client glue: forget the synced state when leaving a world, and the shiver - a small, fast tremble of the
 * first-person view while shivering or worse (off with Reduced motion or the frost option).
 */
@EventBusSubscriber(modid = FrontierHunts.ID, value = Dist.CLIENT)
public final class SurvivalClient {
   private static float shiver;

   private SurvivalClient() {
   }

   @SubscribeEvent
   public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut e) {
      SurvivalNetwork.clearClient();
   }

   @SubscribeEvent
   public static void camera(ViewportEvent.ComputeCameraAngles e) {
      Minecraft mc = Minecraft.getInstance();
      SurvivalNetwork.State s = SurvivalNetwork.clientState();
      float target = 0F;
      if (mc.player != null && s.mode() != 0 && s.has(SurvivalNetwork.State.F_TEMPERATURE) && !s.has(SurvivalNetwork.State.F_EXEMPT)
         && mc.options.getCameraType().isFirstPerson() && SurvivalConfig.frost() && !reducedMotion() && !mc.player.isSleeping()) {
         target = Mth.clamp((SurvivalMath.SHIVER - s.heat()) / 40F + (s.heat() <= SurvivalMath.SHIVER ? 0.35F : 0F), 0F, 1F);
         // [shelter] warming up in shelter (tent, cabin, by the fire): the shivering eases at once, not only once the
         // body has climbed past the threshold
         if (s.trend() >= 0.08F) {
            target *= 1F - 0.45F * com.formaworks.frontierhunts.shelter.client.ShelterClient.enclosure();
         }
      }
      shiver += (target - shiver) * 0.05F;
      if (shiver < 0.01F) {
         return;
      }
      double t = (mc.player.tickCount + e.getPartialTick()) / 20.0;
      float k = shiver * (s.has(SurvivalNetwork.State.F_MITTENS) ? 0.7F : 1F) * com.formaworks.frontierhunts.HuntConfig.shake(); // [1.1.5] camera shake setting
      e.setRoll(e.getRoll() + (float) (Math.sin(t * 37.0) * 0.35 + Math.sin(t * 23.0) * 0.2) * k);
      e.setPitch(e.getPitch() + (float) (Math.sin(t * 41.0 + 1.3) * 0.18) * k);
   }

   private static boolean reducedMotion() {
      try {
         return HuntConfig.REDUCED_MOTION.get();
      } catch (RuntimeException ex) {
         return false;
      }
   }
}
