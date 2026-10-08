package com.formaworks.frontierhunts.seating.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.seating.Seats;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

/** [onboard2] Client registration: the seat entity's renderer and the swivel chairs' turning seat tops. */
@EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT, bus = Bus.MOD)
public final class SeatClient {
   static final ModelResourceLocation BLIND_TOP = ModelResourceLocation.standalone(FrontierHunts.id("block/seat/blind_chair_top"));
   static final ModelResourceLocation TOWER_TOP = ModelResourceLocation.standalone(FrontierHunts.id("block/seat/tower_chair_top"));

   private SeatClient() {
   }

   @SubscribeEvent
   public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
      e.registerEntityRenderer(Seats.SEAT, SeatRenderer::new);
   }

   @SubscribeEvent
   public static void models(ModelEvent.RegisterAdditional e) {
      e.register(BLIND_TOP);
      e.register(TOWER_TOP);
   }
}
