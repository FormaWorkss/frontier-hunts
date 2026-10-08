package com.formaworks.frontierhunts.client.tree;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

/**
 * Once the rebaked models are live (new atlas UVs), trees baked by the previous models - including any grown
 * while the reload was running - are dropped and regrown on their next section build.
 */
@EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class TreeModelsReload {
   private TreeModelsReload() {
   }

   @SubscribeEvent
   public static void baked(ModelEvent.BakingCompleted event) {
      TrunkModel.GENERATION++;
      TreeGrowth.clear();
      TreeLod.clear();
   }
}
