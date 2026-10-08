package com.formaworks.frontierhunts.economy;

import com.formaworks.frontierhunts.expedition.ExpeditionService;
import java.util.LinkedHashMap;
import java.util.Map;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * [economy] Expedition store prices (reserve tokens), rebalanced against the crafting value model
 * (tools/economy/value_model.py, docs/ws/economy.md).
 *
 * <p>Token income: a typical hunt pays ~80 tokens (assignment, contract share, provisions), about 3 hunts an hour, and an
 * hour of material gathering is ~600 effort points, so 1 token ~ 2.5 pt. Price = crafted value / 2.5 x a convenience
 * premium that grows with tier, because tokens skip the material gate: consumables x1.2, mid gear x1.4, late x1.6,
 * endgame x2. Ammunition packs hold 12 (ExpeditionService.buy), everything else 1.
 *
 * <p>Applied to {@link ExpeditionService#SHOP} (the map both the store screen and the server's purchase check read)
 * during common setup, on the client and the dedicated server alike, so the price shown is the price charged. Only
 * existing entries are changed; the list and its order stay as they are.
 */
@EventBusSubscriber(modid = "frontierhunts", bus = EventBusSubscriber.Bus.MOD)
public final class ShopPrices {
   static final Map<String, Integer> PRICES = new LinkedHashMap<>();

   static {
      price("rifle_round", 8);         // x12: 17 pt crafted
      price("shotgun_shell", 6);       // x12: 12 pt (was 8)
      price("pistol_round", 8);        // x12: 17 pt (was 6)
      price("tranquilizer_dart", 12);  // x12: 26 pt
      price("bowfishing_arrow", 3);    // x12: 5 pt (was 8)
      price("medkit", 4);              // 8 pt (was 10)
      price("bait", 1);                // 1.5 pt (was 4)
      price("scent_cover", 2);         // 4 pt (was 8)
      price("suppressor", 36);         // 64 pt (was 45)
      price("extended_magazine", 20);  // 35 pt (was 35)
      price("steady_stock", 14);       // 25 pt (was 30)
      price("bipod", 18);              // 32 pt (was 25)
      price("six_power_scope", 22);    // 40 pt (was 40)
      price("eight_power_scope", 40);  // 63 pt, late (was 55)
      price("twelve_power_scope", 80); // 123 pt, late (was 70)
      price("thermal_scope", 105);     // 131 pt, endgame (was 85)
      price("fishing_drag_kit", 7);    // 15 pt (was 20)
   }

   private ShopPrices() {
   }

   private static void price(String id, int tokens) {
      PRICES.put(id, tokens);
   }

   /**
    * [1.1.6] The store is an outfitter's counter, not a second crafting menu: optics, magazines, suppressors and other
    * parts you build at the attachment bench are no longer sold. It sells what a hunter actually buys in town -
    * ammunition, batteries, scent cover, wind powder, first aid - and the ranger's services moved to the licence counter.
    */
   static final java.util.Set<String> BENCH_ONLY = java.util.Set.of("suppressor", "extended_magazine", "steady_stock", "bipod", "six_power_scope",
      "eight_power_scope", "twelve_power_scope", "thermal_scope", "fishing_drag_kit", "sniper_magazine", "pistol_magazine", "reflex_sight",
      "micro_red_dot", "holographic_sight", "two_power_prism", "muzzle_brake", "angled_foregrip");
   /**
    * [1.1.7] What the counter sells: only things nobody can make. Everything that has a recipe (ammunition, batteries, wind
    * powder, scent cover, bait, first aid) left the counter; this is gear you can't build and targets for a range.
    * Priced against a typical hunt (~80 tokens): useful, never a shortcut past the hunt itself.
    */
   static final Map<String, Integer> ADDED = new LinkedHashMap<>();

   static {
      ADDED.put("hound_lead", 150);       // [1.1.8] only from the counter; [1.1.9] the dearest thing on it: a trained dog, about two hunts
      ADDED.put("tracking_lamp", 45);     // finds blood within 20 m; not the deer
      // [sticks] shooting sticks left the counter: they are made at the Frontier Workbench now
      ADDED.put("estrus_lure", 18);       // one wick; rut only; bucks may come, downwind matters
      ADDED.put("milkweed_pods", 4);      // [1.1.8] 12 puffs: read the wind 30 m out
      ADDED.put("flagging_tape", 3);      // [1.1.8] 24 ties: mark the last blood
      ADDED.put("coffee_thermos", 10);    // 3 cups
      ADDED.put("hand_warmers", 6);       // 4 packs
   }

   /** Rebuilds the store map: only the counter exclusives, in order. Entries whose item is missing are skipped. */
   public static void apply(Map<String, Integer> shop) {
      com.formaworks.frontierhunts.range.RangeContent.listForStore();
      shop.clear();
      for (Map.Entry<String, Integer> e : ADDED.entrySet()) {
         try {
            if (com.formaworks.frontierhunts.expedition.ExpeditionContent.ITEMS.containsKey(e.getKey())) {
               shop.put(e.getKey(), e.getValue());
            }
         } catch (RuntimeException ignored) {
         }
      }
   }

   @SubscribeEvent
   public static void setup(FMLCommonSetupEvent event) {
      event.enqueueWork(() -> apply(ExpeditionService.SHOP));
   }
}
