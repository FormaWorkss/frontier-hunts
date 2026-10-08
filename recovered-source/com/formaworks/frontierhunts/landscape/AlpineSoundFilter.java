package com.formaworks.frontierhunts.landscape;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
public final class AlpineSoundFilter {
   @SubscribeEvent
   public static void sound(PlaySoundEvent var0) {
      ResourceLocation var1 = var0.getOriginalSound().getLocation();
      if (var1.getNamespace().equals("frontierhunts") && var1.getPath().equals("brush_rustle")) {
         var0.setSound(null);
      }
   }
}
