package com.formaworks.frontierhunts.academy;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * [academy] Ranger Academy settings, defined inside HuntConfig's server and client specs (one hook line each, marked
 * {@code // [academy]}). Getters are safe before the configs load.
 */
public final class AcademyConfig {
   public static ModConfigSpec.BooleanValue ENABLED;
   public static ModConfigSpec.IntValue MAX_SESSIONS;
   public static ModConfigSpec.IntValue REPEAT_HOURS;
   public static ModConfigSpec.BooleanValue CINEMATIC;
   public static ModConfigSpec.BooleanValue HUD;

   private AcademyConfig() {
   }

   /** Called from HuntConfig's server builder inside push("academy"). */
   public static void server(ModConfigSpec.Builder b) {
      ENABLED = b.comment("Ranger Academy: training courses in the training grounds dimension, started from the Ranger assignments dossier.")
         .define("trainingGrounds", true);
      MAX_SESSIONS = b.comment("How many hunters may train at the same time (each gets their own plot).")
         .defineInRange("maxSessions", 24, 1, TrainingStore.MAX_SLOTS);
      REPEAT_HOURS = b.comment("In-game hours before passing the same course again pays a (smaller) reward. Practice runs are always allowed.")
         .defineInRange("repeatRewardHours", 24, 1, 24 * 30);
   }

   /** Called from HuntConfig's client builder inside push("academy"). */
   public static void client(ModConfigSpec.Builder b) {
      CINEMATIC = b.comment("Arrival title card, fades and the course-passed moment.").define("cinematic", true);
      HUD = b.comment("Training card (objectives, timer, wind) while in the training grounds.").define("trainingHud", true);
   }

   public static boolean enabled() {
      try {
         return ENABLED == null || ENABLED.get();
      } catch (RuntimeException ex) {
         return true;
      }
   }

   public static int maxSessions() {
      try {
         return MAX_SESSIONS == null ? 24 : MAX_SESSIONS.get();
      } catch (RuntimeException ex) {
         return 24;
      }
   }

   public static long repeatTicks() {
      try {
         return (REPEAT_HOURS == null ? 24 : REPEAT_HOURS.get()) * 1000L;
      } catch (RuntimeException ex) {
         return 24000L;
      }
   }

   public static boolean cinematic() {
      try {
         return CINEMATIC == null || CINEMATIC.get();
      } catch (RuntimeException ex) {
         return true;
      }
   }

   public static boolean hud() {
      try {
         return HUD == null || HUD.get();
      } catch (RuntimeException ex) {
         return true;
      }
   }
}
