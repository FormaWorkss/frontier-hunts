package com.formaworks.frontierhunts.benches;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * [benches] The tabs of the three benches, in rail order. A RECIPES tab lists bench recipes ({@link BenchCatalog}); FIT
 * is the Gunsmith's "fit parts to the weapon in your hand" page and REFIT the Reloading Bench's "change the heads on your
 * arrows" page. {@link #MISC} only shows when a recipe has no explicit place (a data pack or a future item).
 */
public enum BenchTab {
   // Frontier Workbench
   HUNTING(Bench.FRONTIER, "hunting", "frontierhunts:deer_call", Kind.RECIPES),
   CLOTHING(Bench.FRONTIER, "clothing", "frontierhunts:ghillie_jacket", Kind.RECIPES),
   SCENT(Bench.FRONTIER, "scent", "frontierhunts:scent_suit", Kind.RECIPES), // [clothing] carbon base layer, scent cover, pack and quiver
   CAMP(Bench.FRONTIER, "camp", "frontierhunts:trail_dome_tent", Kind.RECIPES),
   FISHING(Bench.FRONTIER, "fishing", "frontierhunts:field_fishing_rod", Kind.RECIPES),
   VEHICLES(Bench.FRONTIER, "vehicles", "frontierhunts:atv", Kind.RECIPES),
   BUILDING(Bench.FRONTIER, "building", "frontierhunts:roof_shingles", Kind.RECIPES),
   RANGE(Bench.FRONTIER, "range", "frontierhunts:shooting_target", Kind.RECIPES),
   FOOD(Bench.FRONTIER, "food", "frontierhunts:pemmican", Kind.RECIPES),
   MISC(Bench.FRONTIER, "misc", "minecraft:chest", Kind.RECIPES),
   // Gunsmith's Bench
   RIFLES(Bench.GUNSMITH, "rifles", "frontierhunts:ridgeline_rifle", Kind.RECIPES),
   SHOTGUNS(Bench.GUNSMITH, "shotguns", "frontierhunts:pump_shotgun", Kind.RECIPES),
   HANDGUNS(Bench.GUNSMITH, "handguns", "frontierhunts:revolver", Kind.RECIPES),
   BOWS(Bench.GUNSMITH, "bows", "frontierhunts:field_bow", Kind.RECIPES),
   BLADES(Bench.GUNSMITH, "blades", "frontierhunts:field_knife", Kind.RECIPES),
   ATTACHMENTS(Bench.GUNSMITH, "attachments", "frontierhunts:six_power_scope", Kind.RECIPES),
   FIT(Bench.GUNSMITH, "fit", "frontierhunts:attachment_tool", Kind.FIT),
   // Reloading Bench
   CARTRIDGES(Bench.RELOADING, "cartridges", "frontierhunts:rifle_round", Kind.RECIPES),
   ARROWS(Bench.RELOADING, "arrows", "frontierhunts:field_arrow", Kind.RECIPES),
   TIPS(Bench.RELOADING, "tips", "frontierhunts:fixed_broadhead", Kind.RECIPES),
   REFIT(Bench.RELOADING, "refit", "frontierhunts:mechanical_broadhead", Kind.REFIT),
   SPECIAL(Bench.RELOADING, "special", "frontierhunts:flare_round", Kind.RECIPES);

   public enum Kind {
      RECIPES, FIT, REFIT
   }

   private static final Map<Bench, List<BenchTab>> BY_BENCH = new EnumMap<>(Bench.class);

   static {
      for (Bench b : Bench.values()) {
         BY_BENCH.put(b, new ArrayList<>());
      }
      for (BenchTab t : values()) {
         BY_BENCH.get(t.bench).add(t);
      }
      BY_BENCH.replaceAll((b, l) -> List.copyOf(l));
   }

   public final Bench bench;
   public final String key;
   /** Item drawn on the tab rail. */
   public final String icon;
   public final Kind kind;

   BenchTab(Bench bench, String key, String icon, Kind kind) {
      this.bench = bench;
      this.key = key;
      this.icon = icon;
      this.kind = kind;
   }

   public String langKey() {
      return "bench.frontierhunts.tab." + this.key;
   }

   public String hintKey() {
      return "bench.frontierhunts.tab." + this.key + ".hint";
   }

   public boolean recipes() {
      return this.kind == Kind.RECIPES;
   }

   /** The tabs of a bench, in rail order (MISC included: the screen hides it while empty). */
   public static List<BenchTab> of(Bench bench) {
      return BY_BENCH.get(bench);
   }

   public String id() {
      return this.name().toLowerCase(Locale.ROOT);
   }
}
