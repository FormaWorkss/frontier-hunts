package com.formaworks.frontierhunts.landscape.ride.rig.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.landscape.ride.rig.RigContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** [atvfuel] Client registration: rig menu screen and the fuel gauge HUD layer. */
@EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class RigClient {
   private RigClient() {
   }

   @SubscribeEvent
   public static void screens(RegisterMenuScreensEvent event) {
      event.register(RigContent.MENU.get(), RigScreen::new);
   }

   @SubscribeEvent
   public static void hud(RegisterGuiLayersEvent event) {
      event.registerAbove(VanillaGuiLayers.HOTBAR, FrontierHunts.id("atv_fuel_gauge"), AtvFuelHud::render);
   }
}
