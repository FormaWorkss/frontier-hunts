package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.outfitter.client.OutfitClient;

/**
 * [outfitter] Ghillie hood / jacket / trousers. Each piece is now its own outfit model (shell + rigid jute and leaf
 * tufts that never hang past the part they are tied to) from the shared worn-gear tables, so the trousers no longer
 * draw the jacket's body, and slim skins get slim sleeves. FrontierClient still registers {@link Extensions} for the
 * ghillie item set (unchanged, disjoint from the other client extensions).
 */
public final class GhillieModel {
   private GhillieModel() {
   }

   public static final class Extensions extends OutfitClient.Armour {
      public Extensions() {
      }
   }
}
