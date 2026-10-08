package com.formaworks.frontierhunts.recipes;

import java.util.Set;

/**
 * [recipes] Items that are made only at the Clothing Table and are therefore kept out of the creative tabs (the same
 * convention as ghillie suits, camo coveralls and the carbon layer, see {@code EquipmentCatalog.creative}). They stay
 * registered: {@code /give}, loot, old worlds and the Clothing Table all work. The hide bedroll (camp gear), the
 * drying rack and the raw / tanned materials stay in the tab and in normal crafting.
 */
public final class ClothingTableItems {
   private static final Set<String> ONLY = Set.of(
      "fur_hat", "buckskin_coat", "bear_fur_coat", "hide_robe", "buckskin_leggings", "fur_mukluks", "fur_lining", "fur_mittens"
   );

   private ClothingTableItems() {
   }

   /** {@code path} = item id path in the frontierhunts namespace. */
   public static boolean only(String path) {
      return ONLY.contains(path);
   }
}
