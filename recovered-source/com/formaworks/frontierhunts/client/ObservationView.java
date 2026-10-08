package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.expedition.ExpeditionGear;
import com.formaworks.frontierhunts.expedition.ObservationVantage;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles;
import net.neoforged.neoforge.client.event.ViewportEvent.ComputeFov;

@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
public final class ObservationView {
   private static double elevation;

   public static boolean observing() {
      Minecraft var0 = Minecraft.getInstance();
      return var0.player != null
         && var0.screen == null
         && var0.options.getCameraType().isFirstPerson()
         && var0.player.isUsingItem()
         && var0.player.getUseItem().getItem() instanceof ExpeditionGear var1
         && (var1.id.contains("binoculars") || var1.id.equals("rangefinder"));
   }

   @SubscribeEvent
   public static void tick(Post var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (observing() && var1.level != null) {
         if (elevation == 0.0 || var1.player.tickCount % 5 == 0) {
            elevation = ObservationVantage.elevation(var1.player);
         }
      } else {
         elevation = 0.0;
      }
   }

   public static boolean elevated() {
      return observing() && elevation >= 3.0;
   }

   public static double range() {
      return elevated() ? ObservationVantage.range(elevation) : 192.0;
   }

   @SubscribeEvent
   public static void fov(ComputeFov var0) {
      if (elevated()) {
         var0.setFOV(var0.getFOV() / 1.12);
      }
   }

   @SubscribeEvent
   public static void camera(ComputeCameraAngles var0) {
      if (elevated() && !(Boolean)HuntConfig.REDUCED_MOTION.get()) {
         Minecraft var1 = Minecraft.getInstance();
         if (!var1.player.isCrouching()) {
            double var2 = ((double)var1.level.getGameTime() + var0.getPartialTick()) / 20.0;
            double var4 = var1.level.isThundering() ? 0.055 : (var1.level.isRaining() ? 0.035 : 0.018);
            var0.setRoll(var0.getRoll() + (float)(Math.sin(var2 * 0.58) * var4));
         }
      }
   }

   private ObservationView() {
   }
}
