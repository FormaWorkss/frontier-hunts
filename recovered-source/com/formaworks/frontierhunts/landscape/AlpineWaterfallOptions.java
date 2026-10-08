package com.formaworks.frontierhunts.landscape;

import com.formaworks.frontierhunts.client.FrontierGraphics;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Type;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;

@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
public final class AlpineWaterfallOptions {
   private static final KeyMapping TOGGLE = new KeyMapping(
      "key.frontierhunts.waterfall_visuals", Type.KEYSYM, InputConstants.UNKNOWN.getValue(), "key.categories.frontierhunts"
   );
   private static boolean visuals = true;

   public static boolean visuals() {
      return visuals && FrontierGraphics.waterfallScale() > 0.0;
   }

   public static int budget(int var0) {
      double var1 = FrontierGraphics.waterfallScale();
      return var1 <= 0.0 ? 0 : Math.max(1, (int)Math.round((double)var0 * var1));
   }

   @SubscribeEvent
   public static void tick(Post var0) {
      while (TOGGLE.consumeClick()) {
         visuals = !visuals;
         LocalPlayer var1 = Minecraft.getInstance().player;
         if (var1 != null) {
            var1.displayClientMessage(
               Component.translatable(visuals ? "message.frontierhunts.waterfall_visuals_on" : "message.frontierhunts.waterfall_visuals_off"), true
            );
         }
      }
   }

   @EventBusSubscriber(
      modid = "frontierhunts",
      value = {Dist.CLIENT},
      bus = Bus.MOD
   )
   public static final class Registration {
      @SubscribeEvent
      public static void keys(RegisterKeyMappingsEvent var0) {
         var0.register(AlpineWaterfallOptions.TOGGLE);
      }
   }
}
