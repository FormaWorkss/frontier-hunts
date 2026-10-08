package com.formaworks.frontierhunts.client.terrain;

import net.neoforged.neoforge.client.event.ModelEvent;

/** Retired. Smooth terrain no longer wraps any block model; this does nothing and is not called. */
@Deprecated
public final class TerrainModels {
   private TerrainModels() {
   }

   public static void wrap(ModelEvent.ModifyBakingResult event) {
   }
}
