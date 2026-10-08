package com.formaworks.frontierhunts.landscape;

import com.formaworks.frontierhunts.FrontierHunts;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * [1.2.5] Registers the wrapping biome source's codec, so that anything that writes a world's biome source out (some
 * map and performance mods do) finds it. Frontier's own world settings save the reserve's source, never this.
 */
@EventBusSubscriber(modid = FrontierHunts.ID, bus = EventBusSubscriber.Bus.MOD)
public final class SnowBiomeSourceType {
   private SnowBiomeSourceType() {
   }

   @SubscribeEvent
   public static void register(RegisterEvent e) {
      e.register(Registries.BIOME_SOURCE, h -> h.register(FrontierHunts.id("frontier_view"), SnowBiomeSource.CODEC));
   }
}
