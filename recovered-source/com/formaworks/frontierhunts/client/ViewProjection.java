package com.formaworks.frontierhunts.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.neoforged.neoforge.client.event.ViewportEvent.ComputeFov;

@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
final class ViewProjection {
   private static double worldFov = 70.0;

   static double scale() {
      return Math.tan(Math.toRadians(worldFov) * 0.5) / Math.tan(Math.toRadians(30.0));
   }

   @SubscribeEvent(
      priority = EventPriority.LOWEST
   )
   public static void fov(ComputeFov var0) {
      if (var0.usedConfiguredFov() && Double.isFinite(var0.getFOV())) {
         worldFov = Math.clamp(var0.getFOV(), 1.0, 170.0);
      }
   }

   @SubscribeEvent
   public static void logout(LoggingOut var0) {
      worldFov = 70.0;
      FieldEntityLight.clear();
   }

   private ViewProjection() {
   }
}
