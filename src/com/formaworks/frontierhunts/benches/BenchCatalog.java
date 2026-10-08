package com.formaworks.frontierhunts.benches;

import com.formaworks.frontierhunts.expedition.ExpeditionWeapon;
import com.formaworks.frontierhunts.hunting.ArrowTip;
import com.formaworks.frontierhunts.hunting.ArrowTipItem;
import com.formaworks.frontierhunts.recipes.ClothingTable;
import com.formaworks.frontierhunts.recipes.ClothingTableRecipe;
import com.formaworks.frontierhunts.workshop.EquipmentCatalog;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * [benches] Which bench, tab and section makes every mod item, and the per-bench recipe lists the menu and the screen
 * share. The placement table below is the reviewed, explicit mapping (result item id -> tab + section, in display
 * order); an item that is not in it falls back by kind (ammunition -> Reloading Bench, attachment / weapon -> Gunsmith's
 * Bench, anything else -> Frontier Workbench "Misc"), so a recipe added later is never lost.
 *
 * <p>Both sides build the same lists from the same (synced) recipe manager, sorted the same way, so a list index is a
 * safe button id for the server. Cached per recipe manager; cleared on data pack sync (server) and recipe updates
 * (client, {@code benches/client/BenchClient}).
 */
@EventBusSubscriber(modid = "frontierhunts")
public final class BenchCatalog {
   /** Where an item is made: tab, section (lang key part) and its place in the tab. */
   public record Place(BenchTab tab, String section, int order, boolean explicit) {
   }

   /** One craftable line of a bench. */
   public static final class Entry {
      public final int index;
      public final ResourceLocation id;
      public final CraftingRecipe recipe;
      public final RecipeHolder<?> holder;
      public final Place place;
      /** what the tile shows (for sewing: the lining / mittens with the "Sew ..." name) */
      public final ItemStack display;
      /** what the bench hands out (empty for sewing recipes, which change the held garment) */
      public final ItemStack product;
      /** 0, or the fur-lining bit a sewing recipe sews into the held garment */
      public final int sewBit;
      /** the non-empty ingredients, one per item consumed */
      public final List<Ingredient> ingredients;
      /** ingredients grouped (same choices), with how many of each */
      public final List<Material> materials;
      /** 1-based number among recipes of this bench that make the same item, and how many there are */
      public int variant = 1, variants = 1;
      /** also craftable at a crafting table (a vanilla-typed mod recipe that was not converted: a data pack) */
      public final boolean table;
      /**
       * [smalls] the head a "fitted arrow" recipe puts on its shafts (stick + feather + heads: "Hunting Arrow · Field
       * Point"), null for every other recipe. Missing heads are made first with {@link #headEntry}.
       */
      public final ArrowTip fitTip;
      /** [smalls] the same bench's recipe that makes {@link #fitTip}'s heads (null if there is none) */
      public Entry headEntry;

      Entry(int index, RecipeHolder<?> holder, CraftingRecipe recipe, Place place, ItemStack display, ItemStack product, int sewBit,
            boolean table) {
         this.index = index;
         this.holder = holder;
         this.id = holder.id();
         this.recipe = recipe;
         this.place = place;
         this.display = display;
         this.product = product;
         this.sewBit = sewBit;
         this.table = table;
         List<Ingredient> ings = new ArrayList<>();
         for (Ingredient i : recipe.getIngredients()) {
            if (!i.isEmpty()) {
               ings.add(i);
            }
         }
         this.ingredients = List.copyOf(ings);
         this.materials = List.copyOf(group(ings));
         this.fitTip = fitTip(product);
      }

      /** [smalls] how many heads one craft of a fitted-arrow recipe takes (0 for other recipes) */
      public int headsPerCraft() {
         if (this.fitTip == null) {
            return 0;
         }
         int n = 0;
         for (Ingredient i : this.ingredients) {
            for (ItemStack s : i.getItems()) {
               if (s.getItem() instanceof ArrowTipItem t && t.tip == this.fitTip) {
                  n++;
                  break;
               }
            }
         }
         return n;
      }

      public BenchTab tab() {
         return this.place.tab;
      }

      public boolean sewing() {
         return this.sewBit != 0;
      }
   }

   /** [smalls] the head on an arrow recipe's product when it is not the shaft's stock head, else null */
   static ArrowTip fitTip(ItemStack product) {
      try {
         return product != null && !product.isEmpty() && ArrowTip.arrow(product) && ArrowTip.fitted(product) ? ArrowTip.of(product) : null;
      } catch (RuntimeException e) {
         return null; // registries not ready
      }
   }

   /** One material line: an ingredient and how many of it a craft takes. */
   public record Material(Ingredient ingredient, int count) {
   }

   private static final Map<String, Place> PLACES = new HashMap<>();
   private static int order;
   /** Mod recipes that stay at the vanilla crafting table (never listed at a bench). */
   public static final Set<String> TABLE_ONLY = Set.of(
      "frontierhunts:frontier_handbook", "frontierhunts:frontier_workbench", "frontierhunts:gunsmith_bench", "frontierhunts:reloading_bench");
   private static final Map<RecipeManager, Map<Bench, List<Entry>>> CACHE = new WeakHashMap<>();

   static {
      // ---------------------------------------------------------------- Frontier Workbench
      put(BenchTab.HUNTING, "calls", "bleat_call", "deer_call", "grunt_tube", "rattling_antlers", "predator_call", "duck_call", "horse_whistle");
      put(BenchTab.HUNTING, "scent", "wind_checker", "whitetail_scent_decoy", "mallard_decoy"); // [clothing] scent_cover -> SCENT tab
      put(BenchTab.HUNTING, "optics", "binoculars", "rangefinder", "thermal_binoculars", "night_vision_binoculars");
      put(BenchTab.HUNTING, "cameras", "trail_camera", "field_phone", "field_flashlight", "field_spotlight",
         "field_battery_pack"); // [phone] field_phone replaces the retired camera_base_station
      put(BenchTab.HUNTING, "kit", "skinning_tool", "medkit", "wingsuit"); // [clothing] pack, quiver -> SCENT tab
      put(BenchTab.HUNTING, "stands", "field_blind", "tree_stand", "tower_blind", "blind_chair", "tower_chair");
      put(BenchTab.HUNTING, "rests", "shooting_sticks"); // [sticks]

      put(BenchTab.CLOTHING, "ghillie", "ghillie_hood", "ghillie_jacket", "ghillie_trousers", "ghillie_grassland_hood", "ghillie_grassland_jacket",
         "ghillie_grassland_trousers", "ghillie_wetland_hood", "ghillie_wetland_jacket", "ghillie_wetland_trousers", "ghillie_snow_hood",
         "ghillie_snow_jacket", "ghillie_snow_trousers");
      put(BenchTab.CLOTHING, "camo", "timber_camo_coveralls", "autumn_camo_coveralls", "marsh_camo_coveralls", "prairie_camo_coveralls",
         "snow_camo_coveralls", "digital_camo_coveralls", "blaze_camo_coveralls");
      put(BenchTab.CLOTHING, "furs", "fur_hat", "buckskin_coat", "buckskin_leggings", "fur_mukluks", "hide_robe", "bear_fur_coat", "fur_lining",
         "fur_mittens");

      // [clothing] "Scent & packs": what you wear under your clothes against scent, the spray, and what you carry on your back
      put(BenchTab.SCENT, "base_layer", "carbon_hood", "carbon_jacket", "carbon_trousers", "scent_suit");
      put(BenchTab.SCENT, "scent_spray", "scent_cover");
      put(BenchTab.SCENT, "packs", "hunter_pack", "hunters_quiver");

      put(BenchTab.CAMP, "tents", "trail_dome_tent", "pup_tent", "solo_ridge_tent", "backpacker_dome_tent", "woodland_camp_tent",
         "hunters_canvas_tent", "canvas_wall_tent", "bell_tent", "family_cabin_tent");
      put(BenchTab.CAMP, "camp_gear", "camp_post", "camp_chair", "camp_cot", "hide_bedroll", "log_stump_seat", "trail_bench", "stacked_firewood");
      put(BenchTab.CAMP, "cooking", "dutch_oven", "smokehouse", "drying_rack", "tanning_rack", "game_pole");
      put(BenchTab.CAMP, "lodge", "lodge_stores", "gun_rack", "bow_stand", "trophy_plinth", "big_buck_board", "contract_board", "expedition_board",
         "lodge_table", "lit_lodge_table", "lodge_chair", "lodge_stove", "stove_flue", "stove_roof_flashing");

      put(BenchTab.FISHING, "tackle", "field_fishing_rod", "fishing_drag_kit", "glow_lure", "chum_bucket", "landing_net", "fish_finder");

      put(BenchTab.VEHICLES, "atv", "atv", "atv_cargo_box", "atv_can_carrier", "jerry_can");
      put(BenchTab.VEHICLES, "snow", "sled", "snowmobile");
      put(BenchTab.VEHICLES, "boats", "jon_boat", "rowboat");

      put(BenchTab.BUILDING, "timber", "weathered_planks", "pine_slab", "pine_stairs", "pine_fence", "timber_brace", "timber_cross_brace",
         "timber_stair_guard", "lookout_brace", "lookout_cross_brace", "lookout_deck_rail", "lookout_stair_rail",
         "frontierstructures:lookout_brace", "frontierstructures:lookout_cross_brace", "frontierstructures:lookout_deck_rail",
         "frontierstructures:lookout_stair_rail");
      put(BenchTab.BUILDING, "roofing", "roof_shingles", "roof_slab", "roof_stairs", "roof_slope_low", "roof_slope_high", "roof_gable_low",
         "roof_gable_high", "roof_ridge_low", "roof_ridge_high");
      put(BenchTab.BUILDING, "canvas", "canvas_wall", "tent_slope", "tent_gable", "tent_ridge", "tent_end_ridge");
      put(BenchTab.BUILDING, "stone", "fieldstone", "fieldstone_stairs", "reserve_granite");
      put(BenchTab.BUILDING, "ground", "forest_loam", "moss_floor", "alpine_pasture", "fallen_branch");
      put(BenchTab.BUILDING, "fixtures", "cabin_door", "ranger_window", "frontierstructures:spruce_casement_window",
         "frontierstructures:spruce_picture_window", "frontierstructures:dark_oak_lattice_window", "rope_ladder", "cabin_lantern", "trail_sign");

      put(BenchTab.RANGE, "targets", "shooting_target", "foam_deer_target", "steel_deer_target", "steel_gong", "steel_popper", "steel_spinner",
         "range_marker");

      put(BenchTab.FOOD, "trail_food", "pemmican", "honey_pemmican", "raw_game_sausage");

      // ---------------------------------------------------------------- Gunsmith's Bench
      put(BenchTab.RIFLES, "rifles", "ridgeline_rifle", "lever_rifle", "semi_auto_rifle", "tranquilizer_rifle");
      put(BenchTab.SHOTGUNS, "shotguns", "pump_shotgun", "semi_auto_shotgun", "double_barrel");
      put(BenchTab.HANDGUNS, "handguns", "field_pistol", "revolver", "flare_gun");
      put(BenchTab.BOWS, "bows", "field_bow", "recurve_bow", "compound_bow", "crossbow");
      put(BenchTab.BOWS, "specialty", "bowfishing_bow", "bait_launcher");
      put(BenchTab.BLADES, "blades", "field_knife", "hunting_spear");
      put(BenchTab.ATTACHMENTS, "sights", "reflex_sight", "micro_red_dot", "holographic_sight", "two_power_prism");
      put(BenchTab.ATTACHMENTS, "scopes", "four_power_optic", "ridgeline_scope", "six_power_scope", "eight_power_scope", "twelve_power_scope",
         "thermal_scope");
      put(BenchTab.ATTACHMENTS, "magazines", "sniper_magazine", "pistol_magazine", "extended_magazine");
      put(BenchTab.ATTACHMENTS, "muzzle", "suppressor", "muzzle_brake");
      put(BenchTab.ATTACHMENTS, "furniture", "angled_foregrip", "steady_stock", "bipod", "attachment_tool");

      // ---------------------------------------------------------------- Reloading Bench
      put(BenchTab.CARTRIDGES, "rifle", "rifle_round", "reserve_308");
      put(BenchTab.CARTRIDGES, "pistol", "pistol_round");
      put(BenchTab.CARTRIDGES, "shotgun", "shotgun_shell");
      // [smalls] each shaft is its own section: the stock arrow first, then the same shaft made with every other head
      put(BenchTab.ARROWS, "hunting_arrows", "field_arrow");
      put(BenchTab.ARROWS, "primitive_arrows", "primitive_arrow");
      put(BenchTab.ARROWS, "bowfishing", "bowfishing_arrow");
      put(BenchTab.TIPS, "broadheads", "fixed_broadhead", "mechanical_broadhead", "cut_on_contact_broadhead");
      put(BenchTab.TIPS, "points", "field_point", "judo_point");
      put(BenchTab.TIPS, "primitive", "flint_point", "obsidian_point", "bone_point");
      put(BenchTab.TIPS, "tracers", "tracer_broadhead", "tracer_field_point", "tracer_ice_broadhead");
      put(BenchTab.SPECIAL, "special", "tranquilizer_dart", "flare_round", "bait");
   }

   private BenchCatalog() {
   }

   private static void put(BenchTab tab, String section, String... ids) {
      for (String id : ids) {
         String full = id.contains(":") ? id : "frontierhunts:" + id;
         if (PLACES.putIfAbsent(full, new Place(tab, section, order++, true)) != null) {
            throw new IllegalStateException("[benches] " + full + " placed twice");
         }
      }
   }

   /** Every explicit placement (item id -> place), for the QA harness and the docs table. */
   public static Map<String, Place> places() {
      return Map.copyOf(PLACES);
   }

   /** Where an item is made. Never null: unknown items fall back by kind (see the class comment). */
   public static Place place(Item item) {
      String id = BuiltInRegistries.ITEM.getKey(item).toString();
      Place p = PLACES.get(id);
      if (p != null) {
         return p;
      }
      try {
         if (EquipmentCatalog.ammunition(item)) {
            return new Place(BenchTab.SPECIAL, "other", 100000, false);
         }
         if (EquipmentCatalog.attachment(item)) {
            return new Place(BenchTab.ATTACHMENTS, "other", 100000, false);
         }
         if (item instanceof ExpeditionWeapon w && w.weapon.bow) {
            return new Place(BenchTab.BOWS, "other", 100000, false);
         }
         if (EquipmentCatalog.weapon(item)) {
            return new Place(BenchTab.RIFLES, "other", 100000, false);
         }
      } catch (RuntimeException ignored) {
         // registries not ready: fall through
      }
      return new Place(BenchTab.MISC, "other", 100000, false);
   }

   public static Bench benchFor(Item item) {
      return place(item).tab.bench;
   }

   // ============================================================================================ lists

   public static synchronized void clear() {
      CACHE.clear();
   }

   @SubscribeEvent
   public static void sync(OnDatapackSyncEvent e) {
      clear();
   }

   @SubscribeEvent
   public static void stopped(ServerStoppedEvent e) {
      clear();
   }

   /** The bench's recipes, in display order (tab, section, item, recipe id). Index = position in this list. */
   public static synchronized List<Entry> entries(RecipeManager rm, HolderLookup.Provider regs, Bench bench) {
      return CACHE.computeIfAbsent(rm, m -> build(m, regs)).get(bench);
   }

   /** Every entry of all three benches (QA, docs). */
   public static synchronized List<Entry> all(RecipeManager rm, HolderLookup.Provider regs) {
      List<Entry> out = new ArrayList<>();
      for (Bench b : Bench.values()) {
         out.addAll(entries(rm, regs, b));
      }
      return out;
   }

   private record Raw(RecipeHolder<?> holder, CraftingRecipe recipe, Place place, ItemStack display, ItemStack product, int sew, boolean table) {
      /** [smalls] after the place: the stock arrow before the fitted ones, those in head order */
      int sub() {
         ArrowTip t = fitTip(this.product);
         return t == null ? -1 : t.ordinal();
      }
   }

   @SuppressWarnings({"unchecked", "rawtypes"})
   private static Map<Bench, List<Entry>> build(RecipeManager rm, HolderLookup.Provider regs) {
      List<Raw> raws = new ArrayList<>();
      for (RecipeHolder<CraftingRecipe> h : rm.getAllRecipesFor(BenchRecipes.TYPE.get())) {
         add(raws, h, h.value(), regs, false);
      }
      // the Clothing Table's station recipes (furs, hides, sewing) are made at the Frontier Workbench
      for (RecipeHolder<CraftingRecipe> h : ClothingTable.recipes(rm)) {
         add(raws, h, h.value(), regs, false);
      }
      // safety net: a mod recipe still typed as crafting (a data pack, a branch merged after the conversion) is listed too
      for (RecipeHolder<CraftingRecipe> h : rm.getAllRecipesFor(RecipeType.CRAFTING)) {
         String ns = h.id().getNamespace();
         if ((ns.equals("frontierhunts") || ns.equals("frontierstructures")) && !h.value().isSpecial()) {
            ItemStack out = h.value().getResultItem(regs);
            ResourceLocation rid = BuiltInRegistries.ITEM.getKey(out.getItem());
            if (!out.isEmpty() && !rid.getNamespace().equals("minecraft") && !TABLE_ONLY.contains(rid.toString())
               && !OldBenches.retired(out.getItem())) {
               add(raws, h, h.value(), regs, true);
            }
         }
      }
      raws.sort(Comparator.<Raw>comparingInt(r -> r.place.tab.ordinal())
         .thenComparingInt(r -> r.place.order)
         .thenComparingInt(r -> r.sew)
         .thenComparingInt(Raw::sub)
         .thenComparing(r -> r.holder.id().toString()));
      Map<Bench, List<Entry>> out = new EnumMap<>(Bench.class);
      for (Bench b : Bench.values()) {
         List<Entry> list = new ArrayList<>();
         for (Raw r : raws) {
            if (r.place.tab.bench == b) {
               list.add(new Entry(list.size(), r.holder, r.recipe, r.place, r.display, r.product, r.sew, r.table));
            }
         }
         // number recipes that make the same item ("recipe 2 of 2"); [smalls] an arrow with another head is another item
         Map<String, List<Entry>> same = new HashMap<>();
         for (Entry e : list) {
            if (!e.sewing()) {
               same.computeIfAbsent(BuiltInRegistries.ITEM.getKey(e.display.getItem()) + (e.fitTip == null ? "" : "#" + e.fitTip.id),
                  k -> new ArrayList<>()).add(e);
            }
         }
         // [smalls] a fitted-arrow recipe makes its missing heads with this bench's head recipe
         for (Entry e : list) {
            if (e.fitTip != null) {
               for (Entry h : list) {
                  if (h.fitTip == null && h.product.getItem() instanceof ArrowTipItem t && t.tip == e.fitTip) {
                     e.headEntry = h;
                     break;
                  }
               }
            }
         }
         for (List<Entry> s : same.values()) {
            for (int i = 0; i < s.size(); i++) {
               s.get(i).variant = i + 1;
               s.get(i).variants = s.size();
            }
         }
         out.put(b, List.copyOf(list));
      }
      return out;
   }

   private static void add(List<Raw> raws, RecipeHolder<?> h, CraftingRecipe r, HolderLookup.Provider regs, boolean table) {
      if (r.getIngredients().stream().allMatch(Ingredient::isEmpty)) {
         return;
      }
      ItemStack display = r.getResultItem(regs);
      if (display == null || display.isEmpty()) {
         return;
      }
      display = display.copy();
      ItemStack product = display.copy();
      int sew = 0;
      Place place;
      if (r instanceof ClothingTableRecipe ct) {
         product = ct.product();
         sew = ct.sew().bit();
         place = ct.sewing() ? new Place(BenchTab.CLOTHING, "sewing", 50000, true) : place(product.getItem());
      } else {
         place = place(display.getItem());
      }
      raws.add(new Raw(h, r, place, display, product, sew, table));
   }

   /** Groups equal ingredients ("2 x Iron Ingot") keeping first-seen order. */
   static List<Material> group(List<Ingredient> ings) {
      List<Ingredient> keys = new ArrayList<>();
      List<Integer> counts = new ArrayList<>();
      for (Ingredient ing : ings) {
         int at = -1;
         for (int i = 0; i < keys.size() && at < 0; i++) {
            if (same(keys.get(i), ing)) {
               at = i;
            }
         }
         if (at < 0) {
            keys.add(ing);
            counts.add(1);
         } else {
            counts.set(at, counts.get(at) + 1);
         }
      }
      List<Material> out = new ArrayList<>();
      for (int i = 0; i < keys.size(); i++) {
         out.add(new Material(keys.get(i), counts.get(i)));
      }
      return out;
   }

   private static boolean same(Ingredient a, Ingredient b) {
      if (a == b) {
         return true;
      }
      ItemStack[] x = a.getItems(), y = b.getItems();
      if (x.length != y.length) {
         return false;
      }
      for (int i = 0; i < x.length; i++) {
         if (!ItemStack.isSameItemSameComponents(x[i], y[i])) {
            return false;
         }
      }
      return x.length > 0 || Arrays.equals(x, y);
   }
}
