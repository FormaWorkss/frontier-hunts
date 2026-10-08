package com.formaworks.frontierhunts.licence;

import java.util.Locale;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * [licence] Hunting regulations settings, defined inside HuntConfig's server spec (one hook line, see HuntConfig
 * "[licence]"). Server configs are synced to clients, so the journal page shows the server's mode. Getters are safe
 * before the config loads.
 */
public final class LicenceConfig {
   public enum Mode {
      /** no licences needed, nothing is checked (tags you carry are not filled) */
      OFF("Off"),
      /** a warning for the first violation in a season, then small fines; nothing is taken */
      RELAXED("Relaxed"),
      /** fines, a strike on your warden record, half the meat and the trophy confiscated; 3 strikes suspend the licence */
      STRICT("Strict");

      public final String title;

      Mode(String title) {
         this.title = title;
      }

      public boolean on() {
         return this != OFF;
      }

      public String key() {
         return this.name().toLowerCase(Locale.ROOT);
      }
   }

   public static ModConfigSpec.EnumValue<Mode> MODE;
   public static ModConfigSpec.BooleanValue EMERALDS;
   public static ModConfigSpec.DoubleValue FINE_MULTIPLIER;

   private LicenceConfig() {
   }

   /** Called from HuntConfig's server builder inside push("licence"). */
   public static void server(ModConfigSpec.Builder b) {
      MODE = b.comment(
            "Hunting regulations: OFF (no licences or tags), RELAXED (default: a warning, then small token fines for hunting without a licence/tag,",
            "out of season or over the bag limit), STRICT (fines, strikes, half the meat and the trophy confiscated; 3 strikes suspend the licence",
            "for the rest of the season). Coyotes, gray wolves and wild boar never need a licence.")
         .defineEnum("huntingRegulations", Mode.RELAXED);
      EMERALDS = b.comment("Licence counters also take emeralds (in addition to reserve tokens).").define("acceptEmeralds", true);
      FINE_MULTIPLIER = b.comment("Multiplier for regulation fines (tokens).").defineInRange("fineMultiplier", 1.0, 0.0, 10.0);
   }

   public static Mode mode() {
      try {
         return MODE == null ? Mode.RELAXED : MODE.get();
      } catch (Throwable t) {
         return Mode.RELAXED;
      }
   }

   public static boolean emeralds() {
      try {
         return EMERALDS == null || EMERALDS.get();
      } catch (Throwable t) {
         return true;
      }
   }

   public static double fineMultiplier() {
      try {
         return FINE_MULTIPLIER == null ? 1.0 : FINE_MULTIPLIER.get();
      } catch (Throwable t) {
         return 1.0;
      }
   }

   /** Saves a new mode (op command). */
   public static boolean set(Mode m) {
      try {
         MODE.set(m);
         MODE.save();
         return true;
      } catch (Throwable t) {
         return false;
      }
   }
}
