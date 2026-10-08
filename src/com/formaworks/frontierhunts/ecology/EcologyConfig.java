package com.formaworks.frontierhunts.ecology;

import java.util.function.Supplier;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * [ecology] Predation and bone-find options, built into HuntConfig's SERVER spec by one hook line
 * ({@code <world>/serverconfig/frontierhunts-server.toml}, section {@code [ecology]}). Reads are safe before the
 * config has loaded (defaults).
 */
public final class EcologyConfig {
   public static ModConfigSpec.BooleanValue PREDATION;
   public static ModConfigSpec.DoubleValue HUNT_RATE;
   public static ModConfigSpec.IntValue KILLS_PER_AREA_PER_DAY;
   public static ModConfigSpec.DoubleValue CARCASS_DAYS;
   public static ModConfigSpec.BooleanValue SCAVENGERS;
   public static ModConfigSpec.BooleanValue HOWLS;
   public static ModConfigSpec.BooleanValue BONE_SITES;
   public static ModConfigSpec.DoubleValue BONE_SITE_FREQUENCY;
   public static ModConfigSpec.BooleanValue KILL_BONES;

   private EcologyConfig() {
   }

   /** Called from HuntConfig while it builds the SERVER spec. */
   public static void server(ModConfigSpec.Builder b) {
      b.push("ecology");
      PREDATION = b.comment(
            "Wolves, coyotes, cougars, panthers, lions, cheetahs and bears hunt prey near players: packs test and chase deer, elk, pronghorn and weak bison, cats stalk and ambush, coyotes take small game. Hunts are infrequent, mostly at dawn, dusk and night, and most chases fail. A kill leaves a carcass, blood, scuffle marks, prints and fur you can read.")
         .define("predation", true);
      HUNT_RATE = b.comment("Scales how often a hungry predator starts a hunt (1.0 = intended; 0 = never on their own, /frontierhunts ecology hunt still works).")
         .defineInRange("huntRate", 1.0, 0.0, 5.0);
      KILLS_PER_AREA_PER_DAY = b.comment("At most this many predator kills per 128x128 block area per in-game day, so herds are never wiped out.")
         .defineInRange("killsPerAreaPerDay", 2, 0, 20);
      CARCASS_DAYS = b.comment("In-game days a predator kill's carcass lies before only bones are left.")
         .defineInRange("carcassDays", 2.0, 0.25, 10.0);
      SCAVENGERS = b.comment("Bears, coyotes and hungry wolves find carcasses (predator kills and animals left in the field) and feed on them.")
         .define("scavengers", true);
      HOWLS = b.comment("Wolf packs howl before setting out on a dusk or night hunt.")
         .define("howls", true);
      BONE_SITES = b.comment("Very rarely, the weathered bones of a long-dead deer, elk, moose or bison lie in new forest, plains and tundra chunks.")
         .define("boneSites", true);
      BONE_SITE_FREQUENCY = b.comment("Multiplier on how often bone sites generate (1.0 = about one per 320 suitable chunks).")
         .defineInRange("boneSiteFrequency", 1.0, 0.0, 4.0);
      KILL_BONES = b.comment("A predator kill's carcass turns into bones (skull, ribs, leg bones) when it has rotted away; these slowly disappear over a few weeks.")
         .define("killBones", true);
      b.pop();
   }

   private static <T> T get(Supplier<T> s, T fallback) {
      try {
         T v = s.get();
         return v == null ? fallback : v;
      } catch (RuntimeException e) {
         return fallback;
      }
   }

   public static boolean predation() {
      return PREDATION == null || get(PREDATION::get, true);
   }

   public static double huntRate() {
      return HUNT_RATE == null ? 1.0 : get(HUNT_RATE::get, 1.0);
   }

   public static int killsPerAreaPerDay() {
      return KILLS_PER_AREA_PER_DAY == null ? 2 : get(KILLS_PER_AREA_PER_DAY::get, 2);
   }

   public static long carcassTicks() {
      double d = CARCASS_DAYS == null ? 2.0 : get(CARCASS_DAYS::get, 2.0);
      return (long)(Math.clamp(d, 0.25, 10.0) * 24000.0);
   }

   public static boolean scavengers() {
      return SCAVENGERS == null || get(SCAVENGERS::get, true);
   }

   public static boolean howls() {
      return HOWLS == null || get(HOWLS::get, true);
   }

   public static boolean boneSites() {
      return BONE_SITES == null || get(BONE_SITES::get, true);
   }

   public static double boneSiteFrequency() {
      return BONE_SITE_FREQUENCY == null ? 1.0 : get(BONE_SITE_FREQUENCY::get, 1.0);
   }

   public static boolean killBones() {
      return KILL_BONES == null || get(KILL_BONES::get, true);
   }
}
