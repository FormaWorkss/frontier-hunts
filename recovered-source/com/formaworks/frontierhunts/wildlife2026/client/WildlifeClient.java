package com.formaworks.frontierhunts.wildlife2026.client;

import com.formaworks.frontierhunts.wildlife2026.WildlifeContent;
import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import com.formaworks.frontierhunts.wildlife2026.WildlifeSpecies;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

/** Registers the Classic box models and renderers of the Frontier wildlife. */
@EventBusSubscriber(modid = "frontierhunts", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WildlifeClient {
   private static final Map<String, ModelLayerLocation> LAYERS = new HashMap<>();

   private WildlifeClient() {
   }

   static synchronized ModelLayerLocation layer(String id) {
      return LAYERS.computeIfAbsent(id, k -> new ModelLayerLocation(WildlifeContent.id("wildlife/" + k), "main"));
   }

   @SubscribeEvent
   public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
      for (WildlifeSpecies s : WildlifeContent.TYPES.keySet()) {
         event.registerLayerDefinition(layer(s.id), () -> WildlifeModels.create(s.id));
      }
   }

   /** Drop cached .fhsk meshes on F3+T / resource-pack changes so edited or newly enabled meshes are picked up. */
   @SubscribeEvent
   public static void reloadListeners(RegisterClientReloadListenersEvent event) {
      event.registerReloadListener((ResourceManagerReloadListener)manager -> SkinnedMesh.clear());
   }

   @SubscribeEvent
   public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
      WildlifeContent.TYPES.forEach((s, holder) -> {
         @SuppressWarnings("unchecked")
         EntityType<WildlifeMob> type = (EntityType<WildlifeMob>)holder.get();
         event.registerEntityRenderer(type, context -> new WildlifeRenderer(context, s));
      });
   }
}
