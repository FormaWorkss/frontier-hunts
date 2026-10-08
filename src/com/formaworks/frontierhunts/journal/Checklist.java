package com.formaworks.frontierhunts.journal;

import com.formaworks.frontierhunts.camps.Quarry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * [journal] Everything there is to do in Frontier Hunts, as one checklist. Each entry watches one counter of the
 * hunter's record and is done when it reaches {@code target}; completing it pays {@code xp} journal XP (rank only)
 * once. Shared by both sides (the client shows progress from the synced counters, the server awards completions).
 * Other workstreams can add entries with {@link #register} during common setup (same order on both sides).
 */
/* [ledger] icons: "icon:<name>" = journal atlas art (journal.client.JournalIcons); item ids render the item (gear entries). */
public final class Checklist {
   public enum Category {
      SPECIES("species", "icon:species"),
      HUNTING("hunting", "icon:hunting"),
      GEAR("gear", "icon:equipment"),
      WORLD("world", "icon:world"),
      CAMP("camp", "icon:camp"),
      SURVIVAL("survival", "icon:survival"),
      SCHOOL("school", "icon:school"),
      /** [hunts] per-species hunt milestones (hunts.HuntBook), grouped by species on the page */
      HUNTS("hunts", "icon:hunting");

      public final String key;
      public final String icon;

      Category(String key, String icon) {
         this.key = key;
         this.icon = icon;
      }

      public String lang() {
         return "journal.frontierhunts.cat." + this.key;
      }
   }

   /** How a counter is shown: plain count, metres shown as km, metres, minutes. */
   public enum Unit {
      COUNT, KM, METRES, INCHES
   }

   public record Entry(String id, Category category, String counter, int target, int xp, String icon, Unit unit, String species) {
      public String titleKey() {
         return "journal.frontierhunts.check." + this.id;
      }

      public String hintKey() {
         return "journal.frontierhunts.check." + this.id + ".hint";
      }

      public boolean done(int value) {
         return value >= this.target;
      }
   }

   /** [gear20] species that spawn in the wild: whitetail, elk, moose, grouse, mallard */
   public static final int FINISHED_SPECIES = 5;
   private static final List<Entry> ENTRIES = new ArrayList<>();
   private static final Map<String, Entry> BY_ID = new HashMap<>();
   private static final Map<String, List<Entry>> BY_COUNTER = new HashMap<>();
   /** item id -> gear key ({@code gear.<key>} becomes 1 when the hunter carries / wears it) */
   public static final Map<String, String> GEAR_ITEMS = new LinkedHashMap<>();
   /** block id -> placed key ({@code placed.<key>} counts placements) */
   public static final Map<String, String> PLACED_BLOCKS = new LinkedHashMap<>();
   /** foods that count as wild game */
   public static final List<String> GAME_FOODS = List.of("frontierhunts:venison", "frontierhunts:cooked_venison", "frontierhunts:backstrap",
      "frontierhunts:cooked_backstrap", "frontierhunts:aged_venison", "frontierhunts:venison_quarter");

   static {
      // --------------------------------------------------------------------------------------------- species
      for (Quarry q : Quarry.values()) {
         int xp = switch (q) {
            case WHITETAIL, BOAR -> 40;
            case GROUSE, DUCK -> 20;
            case COYOTE -> 30;
            case PRONGHORN -> 50;
            case ELK, WOLF -> 60;
            case MOOSE -> 70;
            case BLACK_BEAR, BISON, COUGAR, PANTHER, CHEETAH -> 80;
            case GRIZZLY, LION -> 90;
            case POLAR_BEAR -> 100;
         };
         // [gear20] beta animals do not spawn in the wild: no checklist goals for them (yet)
         if (!com.formaworks.frontierhunts.wildlife2026.Beta.animal(q.id)) {
            add(new Entry("sp_" + q.id, Category.SPECIES, Stat.species(q.id, "h"), 1, xp, "", Unit.COUNT, q.id));
         }
      }
      e("species_8", Category.SPECIES, Stat.SPECIES_TAKEN, 3, 150, "icon:species");
      e("species_all", Category.SPECIES, Stat.SPECIES_TAKEN, FINISHED_SPECIES, 400, "icon:records");
      e("species_photo5", Category.SPECIES, Stat.SPECIES_PHOTOGRAPHED, 5, 80, "frontierhunts:trail_camera");

      // --------------------------------------------------------------------------------------------- hunting
      e("clean_1", Category.HUNTING, Stat.CLEAN_KILLS, 1, 40, "icon:marksmanship");
      e("clean_10", Category.HUNTING, Stat.CLEAN_KILLS, 10, 100, "icon:marksmanship");
      e("clean_50", Category.HUNTING, Stat.CLEAN_KILLS, 50, 250, "icon:marksmanship");
      u("shot_50", Category.HUNTING, Stat.LONGEST_SHOT, 50, 30, "icon:marksmanship", Unit.METRES);
      u("shot_100", Category.HUNTING, Stat.LONGEST_SHOT, 100, 60, "icon:marksmanship", Unit.METRES);
      u("shot_200", Category.HUNTING, Stat.LONGEST_SHOT, 200, 120, "icon:marksmanship", Unit.METRES);
      u("shot_300", Category.HUNTING, Stat.LONGEST_SHOT, 300, 200, "icon:marksmanship", Unit.METRES);
      e("bow_1", Category.HUNTING, Stat.BOW_KILLS, 1, 50, "icon:hunting");
      e("bow_30", Category.HUNTING, Stat.BOW_LONG, 1, 80, "icon:hunting");
      e("recover_1", Category.HUNTING, Stat.RECOVERIES, 1, 40, "icon:blood");
      e("recover_10", Category.HUNTING, Stat.RECOVERIES, 10, 120, "icon:blood");
      u("trail_75", Category.HUNTING, Stat.LONGEST_TRAIL, 75, 80, "icon:tracking", Unit.METRES);
      e("signs_25", Category.HUNTING, Stat.SIGNS, 25, 60, "icon:tracking");
      e("stalk_12", Category.HUNTING, Stat.STALKS, 1, 30, "icon:stalking");
      e("stalk_6", Category.HUNTING, Stat.STALK_CLOSE, 1, 60, "icon:stalking");
      e("unaware", Category.HUNTING, Stat.UNAWARE_KILLS, 1, 50, "icon:stalking");
      e("call", Category.HUNTING, Stat.CALLS, 1, 40, "frontierhunts:grunt_tube");
      e("called_kill", Category.HUNTING, Stat.CALLED_KILLS, 1, 60, "icon:antlers");
      u("trophy_130", Category.HUNTING, Stat.BEST_WT_SCORE, 130, 100, "icon:records", Unit.INCHES);
      u("trophy_160", Category.HUNTING, Stat.BEST_WT_SCORE, 160, 200, "icon:records", Unit.INCHES);
      e("harvest_25", Category.HUNTING, Stat.HARVESTS, 25, 150, "icon:butchery");

      // --------------------------------------------------------------------------------------------- gear
      gear("bow", 15, "frontierhunts:field_bow", "frontierhunts:field_bow", "frontierhunts:recurve_bow", "frontierhunts:compound_bow", "frontierhunts:crossbow");
      gear("rifle", 25, "frontierhunts:ridgeline_rifle", "frontierhunts:ridgeline_rifle", "frontierhunts:lever_rifle", "frontierhunts:semi_auto_rifle");
      gear("scope", 25, "frontierhunts:six_power_scope", "frontierhunts:ridgeline_scope", "frontierhunts:four_power_optic", "frontierhunts:six_power_scope",
         "frontierhunts:eight_power_scope", "frontierhunts:twelve_power_scope", "frontierhunts:thermal_scope", "frontierhunts:two_power_prism");
      e("gear_prone", Category.GEAR, Stat.PRONE_SHOTS, 1, 30, "frontierhunts:bipod");
      gear("glass", 15, "frontierhunts:binoculars", "frontierhunts:binoculars", "frontierhunts:rangefinder", "frontierhunts:thermal_binoculars",
         "icon:marksmanship", "frontierhunts:night_vision_binoculars");
      gear("wind", 10, "frontierhunts:wind_checker", "frontierhunts:wind_checker");
      gear("call", 15, "frontierhunts:grunt_tube", "frontierhunts:grunt_tube", "frontierhunts:deer_call", "frontierhunts:bleat_call",
         "frontierhunts:rattling_antlers", "frontierhunts:predator_call");
      gear("ghillie", 25, "frontierhunts:ghillie_jacket", "frontierhunts:ghillie_hood", "frontierhunts:ghillie_jacket", "frontierhunts:ghillie_trousers",
         "frontierhunts:ghillie_grassland_hood", "frontierhunts:ghillie_grassland_jacket", "frontierhunts:ghillie_grassland_trousers",
         "frontierhunts:ghillie_wetland_hood", "frontierhunts:ghillie_wetland_jacket", "frontierhunts:ghillie_wetland_trousers",
         "frontierhunts:ghillie_snow_hood", "frontierhunts:ghillie_snow_jacket", "frontierhunts:ghillie_snow_trousers", "frontierhunts:carbon_hood",
         "frontierhunts:carbon_jacket", "frontierhunts:carbon_trousers");
      gear("knife", 10, "frontierhunts:skinning_tool", "frontierhunts:skinning_tool");
      placed("trail_camera", 30, "frontierhunts:trail_camera", "frontierhunts:trail_camera");
      e("gear_stand", Category.GEAR, Stat.STAND_SITS, 1, 30, "frontierhunts:tree_stand");
      placed("blind", 25, "frontierhunts:field_blind", "frontierhunts:hub_ground_blind", "frontierhunts:tower_blind");
      placed("tent", 25, "frontierhunts:trail_dome_tent", "frontierhunts:trail_dome_tent", "frontierhunts:woodland_camp_tent",
         "frontierhunts:canvas_wall_tent", "frontierhunts:bell_tent", "frontierhunts:family_cabin_tent", "frontierhunts:pup_tent");
      e("gear_atv", Category.GEAR, Stat.ATV_RIDES, 1, 30, "frontierhunts:jerry_can");
      gear("jerry_can", 15, "frontierhunts:jerry_can", "frontierhunts:jerry_can");
      gear("cargo_box", 20, "frontierhunts:atv_cargo_box", "frontierhunts:atv_cargo_box", "frontierhunts:atv_can_carrier");
      gear("hound", 40, "frontierhunts:hound_lead", "frontierhunts:hound_lead");
      gear("pack", 15, "frontierhunts:hunter_pack", "frontierhunts:hunter_pack", "frontierhunts:hunters_quiver");

      // --------------------------------------------------------------------------------------------- world
      e("biomes_10", Category.WORLD, Stat.BIOMES, 10, 60, "icon:reserve");
      e("bone_site", Category.WORLD, Stat.BONES, 1, 50, "icon:skull");
      e("shed", Category.WORLD, Stat.SHEDS, 1, 60, "icon:antlers");
      e("predator_kill", Category.WORLD, Stat.PREDATOR_SITES, 1, 50, "icon:predator");
      e("rub", Category.WORLD, Stat.RUBS, 1, 30, "icon:rub"); // [integ4] natural rub/scrape read by eye (mock scrape kit removed)
      e("seasons", Category.WORLD, Stat.SEASONS, 4, 150, "icon:leaf");
      e("blizzard", Category.WORLD, Stat.BLIZZARDS, 1, 80, "icon:snowflake");
      e("winter", Category.WORLD, Stat.WINTER_MIN, 30, 150, "icon:winter");
      u("walk_10", Category.WORLD, Stat.WALK_M, 10000, 80, "icon:boot", Unit.KM);
      e("photos_50", Category.WORLD, Stat.PHOTOS, 50, 60, "frontierhunts:field_phone"); // [phone] the phone replaced the base station

      // --------------------------------------------------------------------------------------------- camp
      e("camp_join", Category.CAMP, Stat.CAMP, 1, 40, "icon:camp");
      e("camp_outfit", Category.CAMP, Stat.CAMP_RANK, 1, 60, "frontierhunts:lodge_stores");
      e("guided", Category.CAMP, Stat.GUIDED, 1, 100, "icon:school");
      e("board", Category.CAMP, Stat.BOARD, 1, 100, "frontierhunts:big_buck_board");
      e("event", Category.CAMP, Stat.EVENT_PODIUMS, 1, 120, "icon:podium");
      e("assist", Category.CAMP, Stat.ASSISTS, 1, 40, "icon:party");
      placed("plinth", 40, "frontierhunts:trophy_plinth", "frontierhunts:trophy_plinth");
      e("provisions_5", Category.CAMP, Stat.PROVISIONS, 5, 50, "icon:contracts");

      // --------------------------------------------------------------------------------------------- survival
      e("eat_game", Category.SURVIVAL, Stat.EATEN, 1, 20, "frontierhunts:cooked_backstrap");
      gearIn(Category.SURVIVAL, "cooked", 20, "frontierhunts:cooked_venison", "frontierhunts:cooked_venison", "frontierhunts:cooked_backstrap");
      gearIn(Category.SURVIVAL, "tanned", 30, "frontierhunts:tanned_hide", "frontierhunts:tanned_hide");
      gearIn(Category.SURVIVAL, "aged", 30, "frontierhunts:aged_venison", "frontierhunts:aged_venison");
      // [integ4] Frontier Survival: dressed for the cold, meat put by, a winter night seen through (survival.SurvivalJournal)
      e("wear_furs", Category.SURVIVAL, Stat.SURV_FURS, 1, 30, "frontierhunts:fur_hat");
      e("preserve_8", Category.SURVIVAL, Stat.SURV_PRESERVED, 8, 60, "frontierhunts:jerky");
      e("winter_night", Category.SURVIVAL, Stat.SURV_WINTER_NIGHTS, 1, 80, "frontierhunts:hide_bedroll");

      // --------------------------------------------------------------------------------------------- field school / expedition
      e("fieldschool", Category.SCHOOL, Stat.FIELD_SCHOOL, 7, 100, "icon:school");
      e("campaign", Category.SCHOOL, Stat.CAMPAIGN, 9, 200, "icon:campaign");
      e("contracts_3", Category.SCHOOL, Stat.CONTRACTS, 3, 90, "icon:contracts");

      // --------------------------------------------------------------------------------------------- [hunts] species hunts
      for (com.formaworks.frontierhunts.hunts.HuntBook.Milestone m : com.formaworks.frontierhunts.hunts.HuntBook.milestones()) {
         add(new Entry(m.id(), Category.HUNTS, m.counter(), m.target(), m.xp(), "hunt:" + m.icon(), Unit.COUNT, m.species()));
      }
   }

   private Checklist() {
   }

   private static void e(String id, Category c, String counter, int target, int xp, String icon) {
      add(new Entry(id, c, counter, target, xp, icon, Unit.COUNT, null));
   }

   private static void u(String id, Category c, String counter, int target, int xp, String icon, Unit unit) {
      add(new Entry(id, c, counter, target, xp, icon, unit, null));
   }

   private static void gear(String key, int xp, String icon, String... items) {
      gearIn(Category.GEAR, key, xp, icon, items);
   }

   private static void gearIn(Category c, String key, int xp, String icon, String... items) {
      for (String i : items) {
         GEAR_ITEMS.put(i, key);
      }
      add(new Entry("gear_" + key, c, "gear." + key, 1, xp, icon, Unit.COUNT, null));
   }

   private static void placed(String key, int xp, String icon, String... blocks) {
      for (String b : blocks) {
         PLACED_BLOCKS.put(b, key);
      }
      add(new Entry("placed_" + key, key.equals("plinth") ? Category.CAMP : Category.GEAR, "placed." + key, 1, xp, icon, Unit.COUNT, null));
   }

   private static synchronized void add(Entry e) {
      if (BY_ID.containsKey(e.id())) {
         throw new IllegalStateException("Duplicate journal checklist entry " + e.id());
      }
      ENTRIES.add(e);
      BY_ID.put(e.id(), e);
      BY_COUNTER.computeIfAbsent(e.counter(), k -> new ArrayList<>()).add(e);
   }

   /**
    * API for other workstreams (call during common setup so both sides agree): a new entry that is done when the
    * counter {@code counter} (bumped with {@link JournalApi#count}) reaches {@code target}. Title/hint lang keys are
    * {@code journal.frontierhunts.check.<id>[.hint]}.
    */
   public static void register(String id, Category category, String counter, int target, int xp, String icon) {
      if (id == null || !id.matches("[a-z0-9_]{1,40}") || counter == null || counter.isEmpty() || counter.length() > 64 || target < 1) {
         throw new IllegalArgumentException("Bad journal checklist entry " + id);
      }
      add(new Entry(id, category, counter, target, Math.clamp(xp, 0, 1000), icon == null ? "" : icon, Unit.COUNT, null));
   }

   public static List<Entry> all() {
      return Collections.unmodifiableList(ENTRIES);
   }

   /** [1.1.7] what the journal shows: no challenges for animals that aren't in the wild yet (wildlife2026 previews) */
   public static List<Entry> visible() {
      List<Entry> out = new java.util.ArrayList<>(ENTRIES.size());
      for (Entry e : ENTRIES) {
         if (e.species() == null || !com.formaworks.frontierhunts.wildlife2026.Beta.animal(e.species())) {
            out.add(e);
         }
      }
      return out;
   }

   public static Entry byId(String id) {
      return BY_ID.get(id);
   }

   public static List<Entry> watching(String counter) {
      List<Entry> l = BY_COUNTER.get(counter);
      return l == null ? List.of() : l;
   }
}
