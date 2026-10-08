package com.formaworks.frontierhunts.hunts;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * [hunts] Species hunts settings, defined inside HuntConfig's server spec (one hook line, see HuntConfig "[hunts]").
 * Getters are safe before the config loads.
 */
public final class HuntsConfig {
   public static ModConfigSpec.BooleanValue ENABLED;
   public static ModConfigSpec.BooleanValue LURES;
   public static ModConfigSpec.BooleanValue REWARDS;
   public static ModConfigSpec.DoubleValue TOKEN_MULTIPLIER;
   public static ModConfigSpec.BooleanValue ANNOUNCE_MASTERS;

   private HuntsConfig() {
   }

   /** Called from HuntConfig's server builder inside push("hunts"). */
   public static void server(ModConfigSpec.Builder b) {
      ENABLED = b.comment("Species hunts: per-species milestones in the Hunter's Journal (scout, clean take, technique, quality animal, master), tracked from real hunts.")
         .define("enabled", true);
      LURES = b.comment("Predators answer the predator call, bears / hogs / big cats come to bait piles, ducks answer the duck call and decoys.")
         .define("lures", true);
      REWARDS = b.comment("Pay hunt milestone rewards: tokens and gear (journal XP is paid by the journal checklist either way).")
         .define("rewards", true);
      TOKEN_MULTIPLIER = b.comment("Multiplier for hunt milestone token rewards.")
         .defineInRange("tokenMultiplier", 1.0, 0.0, 10.0);
      ANNOUNCE_MASTERS = b.comment("Tell everyone in chat when a hunter masters a species hunt.")
         .define("announceMasters", true);
   }

   public static boolean enabled() {
      return bool(ENABLED, true);
   }

   public static boolean lures() {
      return enabled() && bool(LURES, true);
   }

   public static boolean rewards() {
      return bool(REWARDS, true);
   }

   public static double tokenMultiplier() {
      try {
         return TOKEN_MULTIPLIER == null ? 1.0 : TOKEN_MULTIPLIER.get();
      } catch (Throwable t) {
         return 1.0;
      }
   }

   public static boolean announceMasters() {
      return bool(ANNOUNCE_MASTERS, true);
   }

   private static boolean bool(ModConfigSpec.BooleanValue v, boolean def) {
      try {
         return v == null ? def : v.get();
      } catch (Throwable t) {
         return def;
      }
   }
}
