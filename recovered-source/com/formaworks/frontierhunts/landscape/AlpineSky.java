package com.formaworks.frontierhunts.landscape;

import com.formaworks.frontierhunts.FrontierHunts;
import net.minecraft.client.renderer.DimensionSpecialEffects.OverworldEffects;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;

@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT},
   bus = Bus.MOD
)
public final class AlpineSky {
   @SubscribeEvent
   public static void register(RegisterDimensionSpecialEffectsEvent var0) {
      var0.register(FrontierHunts.id("alpine"), new OverworldEffects() {
         public float getCloudHeight() {
            return 912.0F;
         }
      });
   }
}
