package com.formaworks.frontierhunts.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional;

@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT},
   bus = Bus.MOD
)
public final class TentClientModels {
   @SubscribeEvent
   public static void models(RegisterAdditional var0) {
      for (String var4 : new String[]{"canvas_wall_tent", "bell_tent", "family_cabin_tent", "pup_tent"}) {
         var0.register(CampingTentRenderer.setupModel(var4));
      }
   }

   private TentClientModels() {
   }
}
