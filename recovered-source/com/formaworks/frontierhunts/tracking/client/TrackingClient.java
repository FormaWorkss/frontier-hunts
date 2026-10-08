package com.formaworks.frontierhunts.tracking.client;

import com.formaworks.frontierhunts.client.TrailClient;
import com.formaworks.frontierhunts.tracking.TrailNetwork;
import com.formaworks.frontierhunts.tracking.hound.HoundContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** [tracking] Client wiring: incremental trail sync receiver and the hound's model / renderer. */
@EventBusSubscriber(modid = "frontierhunts", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TrackingClient {
   private TrackingClient() {
   }

   @SubscribeEvent
   public static void setup(FMLClientSetupEvent e) {
      TrailNetwork.delta = TrailClient::delta;
   }

   @SubscribeEvent
   public static void layers(EntityRenderersEvent.RegisterLayerDefinitions e) {
      e.registerLayerDefinition(HoundRenderer.LAYER, HoundModel::create);
   }

   @SubscribeEvent
   public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
      e.registerEntityRenderer(HoundContent.HOUND.get(), HoundRenderer::new);
   }
}
