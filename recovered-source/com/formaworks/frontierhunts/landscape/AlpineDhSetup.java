package com.formaworks.frontierhunts.landscape;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@EventBusSubscriber(
   modid = "frontierhunts",
   bus = Bus.MOD
)
public final class AlpineDhSetup {
   @SubscribeEvent
   public static void setup(FMLCommonSetupEvent var0) {
      if (ModList.get().isLoaded("distanthorizons")) {
         var0.enqueueWork(() -> {
            try {
               Method var0x = Class.forName("com.formaworks.frontierhunts.landscape.AlpineDhBridge").getDeclaredMethod("register");
               var0x.setAccessible(true);
               var0x.invoke(null);
            } catch (Throwable var1) {
               LogUtils.getLogger().warn("Frontier landscape: Distant Horizons LOD adapter not registered ({})", var1.toString());
            }
         });
      }
   }

   private AlpineDhSetup() {
   }
}
