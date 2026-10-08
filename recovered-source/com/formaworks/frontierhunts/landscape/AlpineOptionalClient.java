package com.formaworks.frontierhunts.landscape;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT},
   bus = Bus.MOD
)
public final class AlpineOptionalClient {
   @SubscribeEvent
   public static void setup(FMLClientSetupEvent var0) {
   }
}
