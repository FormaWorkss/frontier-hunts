package com.formaworks.frontierhunts.survival.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.outfitter.client.OutfitClient;
import com.formaworks.frontierhunts.survival.SurvivalContent;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * [survival] 3D look of the warm hide garments. [outfitter] The models now come from the shared worn-gear tables
 * ({@code outfitter/client/OutfitGeometry}, generated with the textures by tools/outfitter/outfit.py): coat hems ride
 * on the thighs, wide and slim arms, Vanilla/Ultra textures; first-person sleeves are drawn by {@code OutfitClient.Arms}.
 * This item set (the six garments) stays disjoint from every other IClientItemExtensions registration.
 */
public final class GarmentModels {
   private GarmentModels() {
   }

   @EventBusSubscriber(modid = FrontierHunts.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
   public static final class Setup {
      private Setup() {
      }

      @SubscribeEvent
      public static void extensions(RegisterClientExtensionsEvent e) {
         Item[] items = SurvivalContent.garments().toArray(Item[]::new);
         if (items.length > 0) {
            e.registerItem(new OutfitClient.Armour(), items);
         }
      }
   }
}
