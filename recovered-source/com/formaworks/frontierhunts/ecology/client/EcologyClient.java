package com.formaworks.frontierhunts.ecology.client;

import com.formaworks.frontierhunts.ecology.EcologyContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** [ecology] Client registration: the kill-carcass renderer. */
@EventBusSubscriber(modid = "frontierhunts", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class EcologyClient {
   private EcologyClient() {
   }

   @SubscribeEvent
   public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
      e.registerEntityRenderer(EcologyContent.KILL_CARCASS.get(), KillCarcassRenderer::new);
   }
}
