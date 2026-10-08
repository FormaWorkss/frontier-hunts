package com.formaworks.frontierhunts.wildlife2026.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Tracks the final field of view so scopes and binoculars keep the full-detail animal mesh at long range. */
@EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT)
public final class WildlifeFov {
   private static volatile double fov = 70.0;

   private WildlifeFov() {
   }

   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void onFov(ViewportEvent.ComputeFov event) {
      if (event.usedConfiguredFov()) {
         fov = event.getFOV();
      }
   }

   /** LOD distance multiplier: 1 at the player's normal FOV, larger when zoomed in. [scope] up to 30 (25x riflescopes). */
   public static double zoom() {
      double base;
      try {
         base = Minecraft.getInstance().options.fov().get();
      } catch (RuntimeException e) {
         base = 70.0;
      }
      double f = Math.max(1.0, Math.min(170.0, fov));
      return Math.max(1.0, Math.min(30.0, Math.tan(Math.toRadians(base) / 2.0) / Math.tan(Math.toRadians(f) / 2.0)));
   }
}
