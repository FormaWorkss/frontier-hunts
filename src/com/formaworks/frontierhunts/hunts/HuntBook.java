package com.formaworks.frontierhunts.hunts;

import com.formaworks.frontierhunts.camps.Quarry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * [hunts] The species hunts: for every animal in the record book a short progression grounded in how that animal is
 * really hunted - scout it, take one cleanly, use the right technique (calls, decoys, bait, stands, blinds, hounds,
 * glassing, the wing shot), take a quality animal, then a master challenge that puts it all together.
 *
 * <p>Pure data and rules (no Minecraft classes) so the harness can feed synthetic events through it. Every milestone
 * is a journal checklist entry ({@code Checklist.Category.HUNTS}) watching the counter {@code hunt.<species>.<key>};
 * {@link HuntTracker} turns events into counter updates, the journal pays the XP, {@code HuntRewards} the tokens, gear
 * and master titles. Text lives in the lang fragment: {@code journal.frontierhunts.check.<id>[.hint]} and
 * {@code hunts.frontierhunts.<species>.(brief|where|title)}.
 */
public final class HuntBook {
   /** Where a milestone sits in a species' progression. */
   public enum Tier {
      SCOUT,
      TAKE,
      TECHNIQUE,
      QUALITY,
      MASTER;

      public String key() {
         return this.name().toLowerCase(java.util.Locale.ROOT);
      }
   }

   /** How a milestone counts: once, a running total, or a total within one game day (a bag limit). */
   public enum Mode {
      COUNT,
      DAY
   }

   /**
    * One step of a species hunt.
    *
    * @param id       checklist id {@code hunt_<species>_<key>}
    * @param counter  journal counter {@code hunt.<species>.<counterKey>}
    * @param target   count needed
    * @param icon     hunt icon atlas name (hunts/client/HuntIcons)
    * @param item     reward item id (or "") and {@code count}
    * @param needs    items the milestone needs beyond a weapon (for the obtainability audit and the card)
    * @param hook     which gameplay hook reports the event (for the audit)
    */
   public record Milestone(String id, String species, String key, Tier tier, HuntEvent.Kind kind, Predicate<HuntEvent> test, String counter,
      int target, Mode mode, int xp, int tokens, String item, int count, String icon, List<String> needs, String hook) {
      public boolean matches(HuntEvent e) {
         return e != null && e.kind == this.kind && e.species.equals(this.species) && this.test.test(e);
      }

      public boolean master() {
         return this.tier == Tier.MASTER;
      }
   }

   /** A species' hunt: its milestones in order, where it lives (biome tag of its spawn rule) and its season contract. */
   public record Hunt(Quarry quarry, String spawnTag, List<Milestone> milestones, Contract contract) {
      public String species() {
         return this.quarry.id;
      }

      public Milestone master() {
         return this.milestones.get(this.milestones.size() - 1);
      }
   }

   /**
    * A rotating expedition contract: open in the given reserve months (bit i = month i), done after {@code amount}
    * events that match the named condition {@code tag} (see {@link HuntTracker#TAGS}).
    */
   public record Contract(String species, String key, String tag, int amount, int tokens, int months, String title, String story) {
      public String event() {
         return "hunt:" + this.species + ":" + this.tag;
      }

      public boolean open(int month) {
         return (this.months >> Math.floorMod(month, 12) & 1) != 0;
      }
   }

   private static final List<Hunt> HUNTS = new ArrayList<>();
   private static final List<Milestone> ALL = new ArrayList<>();
   private static final Map<String, Milestone> BY_ID = new HashMap<>();
   private static final Map<String, Hunt> BY_SPECIES = new LinkedHashMap<>();

   // ---------------------------------------------------------------------------------------------- shorthands
   private static final HuntEvent.Kind SEEN = HuntEvent.Kind.SEEN, FLUSH = HuntEvent.Kind.FLUSH, GLASS = HuntEvent.Kind.GLASS,
      PHOTO = HuntEvent.Kind.PHOTO, TAKE = HuntEvent.Kind.TAKE;
   private static final int CLEAN = HuntEvent.CLEAN, UNAWARE = HuntEvent.UNAWARE, CALLED = HuntEvent.CALLED, DECOY = HuntEvent.DECOY,
      BAIT = HuntEvent.BAIT, STAND = HuntEvent.STAND, BLIND = HuntEvent.BLIND, HOUND = HuntEvent.HOUND, GLASSED = HuntEvent.GLASSED,
      FLYING = HuntEvent.FLYING, CHARGING = HuntEvent.CHARGING, ON_FOOT = HuntEvent.ON_FOOT, RUT = HuntEvent.RUT, SNOW = HuntEvent.SNOW,
      WATER = HuntEvent.WATER, SNOW_CAMO = HuntEvent.SNOW_CAMO, ONE_SHOT = HuntEvent.ONE_SHOT, HUNTING = HuntEvent.HUNTING, MALE = HuntEvent.MALE;

   private static final String GLASSES = "frontierhunts:binoculars";
   private static final String CAMERA = "frontierhunts:trail_camera";
   private static final String STAND_OR_BLIND = "frontierhunts:tree_stand|frontierhunts:field_blind|frontierhunts:tower_blind";
   private static final String DEER_CALLS = "frontierhunts:grunt_tube|frontierhunts:bleat_call|frontierhunts:rattling_antlers";
   /** elk and moose answer a cow call (the doe bleat call) or a bull grunt; nobody rattles for them */
   private static final String BULL_CALLS = "frontierhunts:bleat_call|frontierhunts:grunt_tube";
   private static final String PREDATOR_CALL = "frontierhunts:predator_call";
   private static final String BAIT_ITEM = "frontierhunts:bait";
   private static final String HOUND_LEAD = "frontierhunts:hound_lead";
   private static final String SHOTGUN = "frontierhunts:pump_shotgun|frontierhunts:double_barrel|frontierhunts:semi_auto_shotgun";
   private static final String BOW = "frontierhunts:field_bow|frontierhunts:recurve_bow|frontierhunts:compound_bow|frontierhunts:crossbow";
   private static final String SNOW_CAMO_ITEMS = "frontierhunts:ghillie_snow_jacket|frontierhunts:ghillie_snow_hood|frontierhunts:ghillie_snow_trousers|frontierhunts:snow_camo_coveralls";
   private static final String DUCK_GEAR = "frontierhunts:mallard_decoy|frontierhunts:duck_call";

   /** Months as a bit mask (0 = January). */
   private static int months(int... m) {
      int mask = 0;
      for (int i : m) {
         mask |= 1 << i;
      }
      return mask;
   }

   private static final int ALL_YEAR = 0xFFF;

   static {
      // ============================================================================================ deer
      hunt(Quarry.WHITETAIL, "frontierhunts:whitetail_habitat", new Contract("whitetail", "stand", "standblind", 1, 40, months(9, 10, 11),
            "Stand hunt", "The lodge wants a whitetail taken the old way: from a tree stand or a blind over a trail."),
         ms("cam", Tier.SCOUT, PHOTO, e -> true, 30, 5, "", 0, "camera", "trailcam", CAMERA),
         ms("clean", Tier.TAKE, TAKE, e -> e.has(CLEAN), 50, 10, "frontierhunts:reserve_308", 10, "reticle", "take"),
         ms("called", Tier.TECHNIQUE, TAKE, e -> e.has(CALLED), 90, 20, "frontierhunts:scent_cover", 3, "call", "deercall", DEER_CALLS),
         ms("mature", Tier.QUALITY, TAKE, e -> e.has(MALE) && e.points >= 8, 110, 25, "", 0, "antlers", "take"),
         ms("master", Tier.MASTER, TAKE, e -> e.has(MALE | RUT | CLEAN) && e.points >= 8 && e.any(STAND | BLIND), 250, 60,
            "frontierhunts:eight_power_scope", 1, "medal", "take", STAND_OR_BLIND));
      hunt(Quarry.ELK, "frontierhunts:elk_habitat", new Contract("elk", "bugle", "called", 1, 50, months(7, 8, 9),
            "Bugle season", "Bulls are answering in the high parks. Call one in - cow call or bull grunt - and take him."),
         ms("glass", Tier.SCOUT, GLASS, e -> e.distance >= 100.0, 30, 5, "", 0, "glass", "glass", GLASSES),
         ms("clean", Tier.TAKE, TAKE, e -> e.has(CLEAN), 60, 10, "frontierhunts:reserve_308", 10, "reticle", "take"),
         ms("called", Tier.TECHNIQUE, TAKE, e -> e.has(CALLED), 100, 20, "frontierhunts:scent_cover", 3, "call", "deercall", BULL_CALLS),
         ms("herd_bull", Tier.QUALITY, TAKE, e -> e.has(MALE) && e.points >= 10 && e.kg >= 270.0, 120, 25, "", 0, "antlers", "take"),
         ms("master", Tier.MASTER, TAKE, e -> e.has(MALE | RUT | CALLED | CLEAN) && e.points >= 10, 260, 60, "frontierhunts:rangefinder", 1,
            "medal", "take", BULL_CALLS));
      hunt(Quarry.MOOSE, "frontierhunts:moose_habitat", new Contract("moose", "camp", "clean", 1, 45, months(8, 9),
            "Moose camp", "Fill the meat pole for the lodge: one moose, taken with a clean heart or lung shot."),
         ms("seen", Tier.SCOUT, SEEN, e -> true, 25, 5, "", 0, "eye", "seen"),
         ms("clean", Tier.TAKE, TAKE, e -> e.has(CLEAN), 60, 10, "frontierhunts:reserve_308", 10, "reticle", "take"),
         ms("called", Tier.TECHNIQUE, TAKE, e -> e.has(CALLED), 100, 20, "frontierhunts:fixed_broadhead", 4, "call", "deercall", BULL_CALLS),
         ms("big_bull", Tier.QUALITY, TAKE, e -> e.has(MALE) && e.points >= 14, 120, 25, "", 0, "antlers", "take"),
         ms("master", Tier.MASTER, TAKE, e -> e.has(MALE | RUT | CALLED | WATER | CLEAN), 260, 60, "frontierhunts:game_pole", 1, "medal", "take",
            BULL_CALLS));

      // ============================================================================================ open-country game
      hunt(Quarry.PRONGHORN, "frontierhunts:wildlife2026/pronghorn", new Contract("pronghorn", "speed_goat", "long100", 1, 45, months(7, 8, 9),
            "Speed goat", "Pronghorn see everything on the flats. Take one from a hundred metres or more."),
         ms("glass", Tier.SCOUT, GLASS, e -> e.distance >= 100.0, 30, 5, "", 0, "glass", "glass", GLASSES),
         ms("clean", Tier.TAKE, TAKE, e -> e.has(CLEAN), 50, 10, "frontierhunts:reserve_308", 10, "reticle", "take"),
         ms("blind", Tier.TECHNIQUE, TAKE, e -> e.has(BLIND), 90, 20, "frontierhunts:reserve_308", 10, "blind", "take",
            "frontierhunts:field_blind|frontierhunts:tower_blind"),
         ms("heavy", Tier.QUALITY, TAKE, e -> e.kg >= 54.0, 100, 25, "", 0, "scale", "take"),
         ms("master", Tier.MASTER, TAKE, e -> e.has(CLEAN | GLASSED | ON_FOOT) && !e.any(BLIND | STAND) && e.distance >= 100.0, 240, 60,
            "frontierhunts:twelve_power_scope", 1, "medal", "take", GLASSES));
      hunt(Quarry.BISON, "frontierhunts:wildlife2026/bison", new Contract("bison", "winter_meat", "onfoot", 1, 50, months(10, 11, 0, 1),
            "Winter meat", "Walk in on the herd - no machines - and bring back one bison."),
         ms("glass", Tier.SCOUT, GLASS, e -> e.herd >= 3, 30, 5, "", 0, "glass", "glass", GLASSES),
         ms("clean", Tier.TAKE, TAKE, e -> e.has(CLEAN), 70, 10, "frontierhunts:reserve_308", 10, "reticle", "take"),
         ms("fair_chase", Tier.TECHNIQUE, TAKE, e -> e.has(ON_FOOT | UNAWARE) && !e.any(BLIND | STAND) && e.herd >= 3, 100, 20,
            "frontierhunts:reserve_308", 10, "boot", "take"),
         ms("bull", Tier.QUALITY, TAKE, e -> e.kg >= 730.0, 120, 25, "", 0, "scale", "take"),
         ms("master", Tier.MASTER, TAKE, e -> e.has(ONE_SHOT | ON_FOOT | UNAWARE) && !e.any(BLIND | STAND) && e.herd >= 3, 260, 60,
            "frontierhunts:bipod", 1, "medal", "take"));
      hunt(Quarry.BOAR, "frontierhunts:wildlife2026/boar", new Contract("boar", "hog_control", "take", 2, 35, ALL_YEAR,
            "Hog control", "Wild hogs are rooting up the creek bottoms. Take two."),
         ms("cam", Tier.SCOUT, PHOTO, e -> true, 25, 5, "", 0, "camera", "trailcam", CAMERA),
         ms("clean", Tier.TAKE, TAKE, e -> e.has(CLEAN), 50, 10, "frontierhunts:reserve_308", 10, "reticle", "take"),
         ms("night", Tier.TECHNIQUE, TAKE, HuntEvent::dark, 90, 20, "frontierhunts:field_flashlight", 1, "moon", "take"),
         ms("big", Tier.QUALITY, TAKE, e -> e.kg >= 92.0, 100, 25, "", 0, "scale", "take"),
         ms("master", Tier.MASTER, TAKE, e -> e.has(HOUND) && e.dark(), 240, 60, "frontierhunts:night_vision_binoculars", 1, "medal", "hound",
            HOUND_LEAD));

      // ============================================================================================ bears
      hunt(Quarry.BLACK_BEAR, "frontierhunts:wildlife2026/black_bear", new Contract("black_bear", "bear_season", "take", 1, 45, months(4, 5, 8, 9),
            "Bear season", "The bears are on the move. Take one black bear for the lodge."),
         ms("cam", Tier.SCOUT, PHOTO, e -> true, 30, 5, "", 0, "camera", "trailcam", CAMERA),
         ms("clean", Tier.TAKE, TAKE, e -> e.has(CLEAN), 70, 10, "frontierhunts:reserve_308", 10, "reticle", "take"),
         ms("bait", Tier.TECHNIQUE, TAKE, e -> e.has(BAIT), 100, 20, "frontierhunts:bait", 4, "bait", "bait", BAIT_ITEM),
         ms("big", Tier.QUALITY, TAKE, e -> e.kg >= 130.0, 120, 25, "", 0, "scale", "take"),
         ms("master", Tier.MASTER, TAKE, e -> e.gun == HuntEvent.Gun.BOW && e.has(CLEAN | BAIT), 270, 60, "frontierhunts:compound_bow", 1, "medal",
            "bait", BAIT_ITEM, BOW));
      hunt(Quarry.GRIZZLY, "frontierhunts:wildlife2026/grizzly", new Contract("grizzly", "high_bear", "take", 1, 60, months(3, 4, 8, 9),
            "High-country bear", "A grizzly is working the slides above the river. Take it - and keep your distance until you shoot."),
         ms("glass", Tier.SCOUT, GLASS, e -> e.distance >= 80.0, 35, 5, "", 0, "glass", "glass", GLASSES),
         ms("clean", Tier.TAKE, TAKE, e -> e.has(CLEAN), 80, 10, "frontierhunts:reserve_308", 10, "reticle", "take"),
         ms("stalk", Tier.TECHNIQUE, TAKE, e -> e.has(GLASSED | UNAWARE), 110, 20, "frontierhunts:medkit", 2, "stalk", "take", GLASSES),
         ms("big", Tier.QUALITY, TAKE, e -> e.kg >= 290.0, 130, 25, "", 0, "scale", "take"),
         ms("master", Tier.MASTER, TAKE, e -> e.has(CHARGING | CLEAN) && e.distance <= 20.0, 300, 70, "frontierhunts:steady_stock", 1, "medal", "take"));
      hunt(Quarry.POLAR_BEAR, "frontierhunts:wildlife2026/polar_bear", new Contract("polar_bear", "ice_patrol", "take", 1, 70, months(11, 0, 1, 2),
            "Ice patrol", "A white bear is hunting the coast. Travel north and take it."),
         ms("glass", Tier.SCOUT, GLASS, e -> e.distance >= 80.0, 35, 5, "", 0, "glass", "glass", GLASSES),
         ms("clean", Tier.TAKE, TAKE, e -> e.has(CLEAN), 90, 10, "frontierhunts:reserve_308", 10, "reticle", "take"),
         ms("camo", Tier.TECHNIQUE, TAKE, e -> e.has(SNOW_CAMO), 110, 20, "frontierhunts:fur_mittens", 1, "camo", "take", SNOW_CAMO_ITEMS),
         ms("big", Tier.QUALITY, TAKE, e -> e.kg >= 470.0, 130, 25, "", 0, "scale", "take"),
         ms("master", Tier.MASTER, TAKE, e -> e.has(GLASSED | UNAWARE | SNOW_CAMO | CLEAN), 300, 70, "frontierhunts:thermal_binoculars", 1, "medal", "take",
            GLASSES, SNOW_CAMO_ITEMS));

      // ============================================================================================ predators
      hunt(Quarry.COYOTE, "frontierhunts:wildlife2026/coyote", new Contract("coyote", "calling", "called", 1, 40, months(11, 0, 1, 2),
            "Calling season", "Ranchers are losing calves. Call a coyote in with the predator call and take it."),
         ms("seen", Tier.SCOUT, SEEN, e -> true, 25, 5, "", 0, "eye", "seen"),
         ms("clean", Tier.TAKE, TAKE, e -> e.has(CLEAN), 50, 10, "frontierhunts:reserve_308", 10, "reticle", "take"),
         ms("called", Tier.TECHNIQUE, TAKE, e -> e.has(CALLED), 90, 20, "frontierhunts:reserve_308", 10, "howl", "predatorcall", PREDATOR_CALL),
         ms("prime", Tier.QUALITY, TAKE, HuntEvent::winter, 100, 25, "", 0, "pelt", "take"),
         msN("master", Tier.MASTER, TAKE, e -> e.has(CALLED) && e.dark(), 3, HuntBook.Mode.COUNT, 240, 60, "frontierhunts:thermal_scope", 1, "medal",
            "predatorcall", PREDATOR_CALL));
      hunt(Quarry.WOLF, "frontierhunts:wildlife2026/wolf", new Contract("wolf", "pack_check", "take", 1, 50, months(11, 0, 1, 2),
            "Pack check", "A pack has moved onto the winter range. Take one gray wolf."),
         ms("glass", Tier.SCOUT, GLASS, e -> e.distance >= 60.0, 30, 5, "", 0, "glass", "glass", GLASSES),
         ms("clean", Tier.TAKE, TAKE, e -> e.has(CLEAN), 70, 10, "frontierhunts:reserve_308", 10, "reticle", "take"),
         ms("called", Tier.TECHNIQUE, TAKE, e -> e.has(CALLED), 100, 20, "frontierhunts:reserve_308", 10, "howl", "predatorcall", PREDATOR_CALL),
         ms("prime", Tier.QUALITY, TAKE, HuntEvent::winter, 110, 25, "", 0, "pelt", "take"),
         ms("master", Tier.MASTER, TAKE, e -> e.winter() && e.has(CLEAN | GLASSED) && e.distance >= 100.0, 260, 60, "frontierhunts:muzzle_brake", 1,
            "medal", "take", GLASSES));
      hunt(Quarry.COUGAR, "frontierhunts:wildlife2026/cougar", new Contract("cougar", "cat_tracks", "take", 1, 60, months(11, 0, 1, 2),
            "Cat tracks", "A lion has been killing deer near the ridge. Find it and take it."),
         ms("cam", Tier.SCOUT, PHOTO, e -> true, 35, 5, "", 0, "camera", "trailcam", CAMERA),
         ms("clean", Tier.TAKE, TAKE, e -> e.has(CLEAN), 80, 10, "frontierhunts:reserve_308", 10, "reticle", "take"),
         ms("hound", Tier.TECHNIQUE, TAKE, e -> e.has(HOUND), 110, 20, "frontierhunts:medkit", 2, "hound", "hound", HOUND_LEAD),
         ms("tom", Tier.QUALITY, TAKE, e -> e.kg >= 65.0, 120, 25, "", 0, "scale", "take"),
         ms("master", Tier.MASTER, TAKE, e -> e.has(HOUND | SNOW), 280, 70, "frontierhunts:fur_hat", 1, "medal", "hound", HOUND_LEAD));
      hunt(Quarry.LION, "frontierhunts:wildlife2026/lion", new Contract("lion", "pride_watch", "take", 1, 70, months(5, 6, 7, 8),
            "Pride watch", "A pride has turned to the cattle posts. Take one lion."),
         ms("glass", Tier.SCOUT, GLASS, e -> e.distance >= 80.0, 35, 5, "", 0, "glass", "glass", GLASSES),
         ms("clean", Tier.TAKE, TAKE, e -> e.has(CLEAN), 90, 10, "frontierhunts:reserve_308", 10, "reticle", "take"),
         ms("stalk", Tier.TECHNIQUE, TAKE, e -> e.has(GLASSED | UNAWARE), 110, 20, "frontierhunts:reserve_308", 10, "stalk", "take", GLASSES),
         ms("maned", Tier.QUALITY, TAKE, e -> e.kg >= 190.0, 130, 25, "", 0, "scale", "take"),
         ms("master", Tier.MASTER, TAKE, e -> e.has(CHARGING | CLEAN) && e.distance <= 20.0, 300, 70, "frontierhunts:holographic_sight", 1, "medal",
            "take"));
      hunt(Quarry.PANTHER, "frontierhunts:wildlife2026/panther", new Contract("panther", "night_watch", "night", 1, 60, months(5, 6, 7, 8),
            "Night watch", "The cat comes after dark. Take a panther between dusk and dawn."),
         ms("cam", Tier.SCOUT, PHOTO, e -> true, 35, 5, "", 0, "camera", "trailcam", CAMERA),
         ms("clean", Tier.TAKE, TAKE, e -> e.has(CLEAN), 80, 10, "frontierhunts:reserve_308", 10, "reticle", "take"),
         ms("hide", Tier.TECHNIQUE, TAKE, e -> e.any(BLIND | STAND) && e.dark(), 110, 20, "frontierhunts:bait", 4, "blind", "take", STAND_OR_BLIND),
         ms("big", Tier.QUALITY, TAKE, e -> e.kg >= 58.0, 120, 25, "", 0, "scale", "take"),
         ms("master", Tier.MASTER, TAKE, e -> e.has(BAIT | CLEAN) && e.any(BLIND | STAND) && e.dark(), 280, 70, "frontierhunts:suppressor", 1, "medal",
            "bait", BAIT_ITEM, STAND_OR_BLIND));
      hunt(Quarry.CHEETAH, "frontierhunts:wildlife2026/cheetah", new Contract("cheetah", "safari", "photo", 1, 40, months(5, 6, 7, 8),
            "Camera safari", "The lodge's guests want pictures. Get a cheetah on a trail camera."),
         ms("glass", Tier.SCOUT, GLASS, e -> e.distance >= 80.0 && e.daylight(), 30, 5, "", 0, "glass", "glass", GLASSES),
         ms("cam", Tier.SCOUT, PHOTO, e -> true, 40, 10, "", 0, "camera", "trailcam", CAMERA),
         ms("chase", Tier.TECHNIQUE, GLASS, e -> e.has(HUNTING), 120, 25, "frontierhunts:trail_camera", 1, "stalk", "glass", GLASSES),
         ms("clean", Tier.TAKE, TAKE, e -> e.has(CLEAN), 80, 10, "frontierhunts:reserve_308", 10, "reticle", "take"),
         ms("master", Tier.MASTER, TAKE, e -> e.has(CLEAN) && e.kg >= 50.0 && e.distance >= 100.0, 260, 60, "frontierhunts:trail_camera", 2, "medal",
            "take"));

      // ============================================================================================ birds
      hunt(Quarry.GROUSE, "frontierhunts:wildlife2026/grouse", new Contract("grouse", "upland", "take", 2, 30, months(8, 9, 10, 11),
            "Upland walk", "Walk the aspen edges and bring two grouse back for supper."),
         ms("flush", Tier.SCOUT, FLUSH, e -> true, 20, 5, "", 0, "flush", "flush"),
         ms("take", Tier.TAKE, TAKE, e -> true, 40, 10, "frontierhunts:shotgun_shell", 12, "reticle", "take"),
         msC("wing", "wing", Tier.TECHNIQUE, TAKE, e -> e.has(FLYING), 1, Mode.COUNT, 80, 20, "frontierhunts:shotgun_shell", 16, "wing", "take", SHOTGUN),
         msN("limit", Tier.QUALITY, TAKE, e -> true, 3, Mode.DAY, 90, 25, "", 0, "limit", "take"),
         msC("master", "wing", Tier.MASTER, TAKE, e -> e.has(FLYING), 5, Mode.COUNT, 200, 50, "frontierhunts:double_barrel", 1, "medal", "take", SHOTGUN));
      hunt(Quarry.DUCK, "frontierhunts:wildlife2026/duck", new Contract("duck", "opening", "take", 2, 35, months(9, 10, 11, 0),
            "Opening morning", "Set out decoys on the marsh at first light and bring back two ducks."),
         ms("seen", Tier.SCOUT, SEEN, e -> true, 20, 5, "", 0, "eye", "seen"),
         ms("take", Tier.TAKE, TAKE, e -> true, 40, 10, "frontierhunts:shotgun_shell", 12, "reticle", "take"),
         ms("spread", Tier.TECHNIQUE, TAKE, e -> e.any(DECOY | CALLED), 90, 20, "frontierhunts:mallard_decoy", 2, "decoy", "decoy", DUCK_GEAR),
         ms("wing", Tier.QUALITY, TAKE, e -> e.has(FLYING), 90, 25, "", 0, "wing", "take", SHOTGUN),
         msN("master", Tier.MASTER, TAKE, e -> e.any(DECOY | CALLED), 4, Mode.DAY, 220, 50, "frontierhunts:semi_auto_shotgun", 1, "medal", "decoy",
            DUCK_GEAR));
   }

   private HuntBook() {
   }

   // ---------------------------------------------------------------------------------------------- builders

   private record Spec(String key, String counterKey, Tier tier, HuntEvent.Kind kind, Predicate<HuntEvent> test, int target, Mode mode, int xp, int tokens,
      String item, int count, String icon, String hook, List<String> needs) {
   }

   private static Spec ms(String key, Tier tier, HuntEvent.Kind kind, Predicate<HuntEvent> test, int xp, int tokens, String item, int count, String icon,
      String hook, String... needs) {
      return new Spec(key, key, tier, kind, test, 1, Mode.COUNT, xp, tokens, item, count, icon, hook, List.of(needs));
   }

   private static Spec msN(String key, Tier tier, HuntEvent.Kind kind, Predicate<HuntEvent> test, int target, Mode mode, int xp, int tokens, String item,
      int count, String icon, String hook, String... needs) {
      return new Spec(key, key, tier, kind, test, target, mode, xp, tokens, item, count, icon, hook, List.of(needs));
   }

   private static Spec msC(String key, String counterKey, Tier tier, HuntEvent.Kind kind, Predicate<HuntEvent> test, int target, Mode mode, int xp,
      int tokens, String item, int count, String icon, String hook, String... needs) {
      return new Spec(key, counterKey, tier, kind, test, target, mode, xp, tokens, item, count, icon, hook, List.of(needs));
   }

   private static void hunt(Quarry q, String spawnTag, Contract contract, Spec... specs) {
      List<Milestone> list = new ArrayList<>();
      for (Spec s : specs) {
         Milestone m = new Milestone("hunt_" + q.id + "_" + s.key, q.id, s.key, s.tier, s.kind, s.test, "hunt." + q.id + "." + s.counterKey, s.target, s.mode,
            s.xp, s.tokens, s.item, s.count, s.icon, s.needs, s.hook);
         if (BY_ID.put(m.id(), m) != null) {
            throw new IllegalStateException("Duplicate hunt milestone " + m.id());
         }
         list.add(m);
         ALL.add(m);
      }
      if (list.isEmpty() || !list.get(list.size() - 1).master()) {
         throw new IllegalStateException("A species hunt must end with its master milestone: " + q.id);
      }
      Hunt h = new Hunt(q, spawnTag, List.copyOf(list), contract);
      HUNTS.add(h);
      BY_SPECIES.put(q.id, h);
   }

   // ---------------------------------------------------------------------------------------------- queries

   public static List<Hunt> hunts() {
      return Collections.unmodifiableList(HUNTS);
   }

   public static List<Milestone> milestones() {
      return Collections.unmodifiableList(ALL);
   }

   public static Milestone byId(String id) {
      return BY_ID.get(id);
   }

   public static Hunt of(String species) {
      return species == null ? null : BY_SPECIES.get(species);
   }

   /** The master title's lang key for a species. */
   public static String titleKey(String species) {
      return "hunts.frontierhunts." + species + ".title";
   }
}
