package com.formaworks.frontierhunts.landscape.mapsync;

import java.util.function.BiConsumer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class MapSyncBridge {
   static volatile BiConsumer<MapSyncPayloads.Batch, IPayloadContext> handler;

   private MapSyncBridge() {
   }

   static void batch(MapSyncPayloads.Batch var0, IPayloadContext var1) {
      BiConsumer var2 = handler;
      if (var2 != null) {
         var2.accept(var0, var1);
      }
   }
}
