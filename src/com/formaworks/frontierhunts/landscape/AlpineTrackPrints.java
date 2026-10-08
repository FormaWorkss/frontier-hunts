package com.formaworks.frontierhunts.landscape;

import com.formaworks.frontierhunts.tracking.TrackPrints;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent.Post;

/**
 * Footprints. [tracking] The block prints (AlpineTracks, kinds 0-5) are no longer placed: every print is now a
 * TrailMark made by {@link TrackPrints} (all species, vanilla snow layers, ageing with time and weather, readable on
 * inspection, followed by the hound). Existing track blocks in old worlds still age out through their random ticks.
 */
@EventBusSubscriber(
   modid = "frontierhunts"
)
public final class AlpineTrackPrints {
   @SubscribeEvent
   public static void tick(Post var0) {
      Entity var1 = var0.getEntity();
      // [tracking] per-entity filter: every 4th tick, server only; TrackPrints.kindOf rejects non-print makers cheaply
      // [perf] cheapest rejections first (this runs for every entity on both sides): three of four ticks, client-side
      // entities and every entity class that never makes prints leave before any map lookup or block read
      if ((var1.tickCount & 3) != 0 || var1.level().isClientSide() || !TrackPrints.maker(var1)) {
         return;
      }
      if (var1.level() instanceof ServerLevel var2) {
         TrackPrints.tick(var1, var2); // [tracking]
      }
   }

   private AlpineTrackPrints() {
   }
}
