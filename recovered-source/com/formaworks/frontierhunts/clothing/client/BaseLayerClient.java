package com.formaworks.frontierhunts.clothing.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.clothing.BaseLayerService;
import com.formaworks.frontierhunts.clothing.BaseLayerView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * [clothing] Client wiring of the base layer: the sync receivers (every visible player's pieces, the local player's
 * sweat / wet / spray into {@link BaseLayerView}), clearing them when the world changes, and the render layer.
 */
public final class BaseLayerClient {
   private static Object level;

   private BaseLayerClient() {
   }

   static void fresh() {
      Minecraft mc = Minecraft.getInstance();
      if (level != mc.level) {
         BaseLayerView.clear();
         level = mc.level;
      }
   }

   static void receive(BaseLayerService.Sync s) {
      fresh();
      BaseLayerView.put(s.entity(), s.hood(), s.top(), s.trousers());
      LayersScreen.refresh();
   }

   static void receive(BaseLayerService.State s) {
      fresh();
      BaseLayerView.sweat = clamp(s.sweat());
      BaseLayerView.wet = clamp(s.wet());
      BaseLayerView.sprayUntil = s.sprayUntil();
   }

   private static float clamp(float v) {
      return Float.isFinite(v) ? Math.max(0F, Math.min(1F, v)) : 0F;
   }

   @EventBusSubscriber(modid = FrontierHunts.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
   public static final class Setup {
      private Setup() {
      }

      @SubscribeEvent
      public static void setup(FMLClientSetupEvent e) {
         BaseLayerService.syncReceiver = BaseLayerClient::receive;
         BaseLayerService.stateReceiver = BaseLayerClient::receive;
      }

      @SubscribeEvent
      public static void layers(EntityRenderersEvent.AddLayers e) {
         for (PlayerSkin.Model skin : e.getSkins()) {
            if (e.getSkin(skin) instanceof PlayerRenderer r) {
               r.addLayer(new BaseLayerRender(r, skin == PlayerSkin.Model.SLIM));
            }
         }
      }
   }

   @EventBusSubscriber(modid = FrontierHunts.ID, value = Dist.CLIENT)
   public static final class Events {
      private Events() {
      }

      @SubscribeEvent
      public static void out(ClientPlayerNetworkEvent.LoggingOut e) {
         BaseLayerView.clear();
         level = null;
      }
   }
}
