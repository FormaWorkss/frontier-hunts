package com.formaworks.frontierhunts.survival;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * [survival] Frontier Survival settings, defined inside HuntConfig's server and client specs (one hook line each, see
 * HuntConfig "[survival]"). Getters are safe before the configs load. Clients read the server values from the state
 * payload ({@link SurvivalSync}), never from their own copy.
 */
public final class SurvivalConfig {
   /** Difficulty of the hunt-to-live layer. */
   public enum Difficulty {
      OFF, LIGHT, BALANCED, HARDCORE;

      public SurvivalMath.Mode mode() {
         return SurvivalMath.Mode.values()[this.ordinal()];
      }
   }

   public enum Units {
      CELSIUS, FAHRENHEIT
   }

   // server (world)
   public static ModConfigSpec.EnumValue<Difficulty> DIFFICULTY;
   public static ModConfigSpec.BooleanValue SPOILAGE;
   public static ModConfigSpec.BooleanValue TEMPERATURE;
   public static ModConfigSpec.BooleanValue LEAN_SEASONS;
   public static ModConfigSpec.IntValue DRAIN_PERCENT;
   // client
   public static ModConfigSpec.BooleanValue HUD;
   public static ModConfigSpec.EnumValue<Units> UNITS;
   public static ModConfigSpec.BooleanValue FROST;

   private SurvivalConfig() {
   }

   /** Called from HuntConfig's server builder inside push("survival"). */
   public static void server(ModConfigSpec.Builder b) {
      DIFFICULTY = b.comment(
            "Frontier Survival: you hunt to live. OFF = vanilla hunger only. LIGHT = nutrition and warmth are tracked, no health loss, eating wild game gives Hunter's Vigor. BALANCED = a farm diet keeps you alive but weaker, neglect costs health slowly and winter cold can kill. HARDCORE = farm food barely sustains, without game you starve, winter is deadly."
         )
         .defineEnum("difficulty", Difficulty.BALANCED);
      SPOILAGE = b.comment("Meat and fish spoil over a few in-game days (faster in heat, slower in the cold). Smoke, dry, salt or keep them cold.")
         .define("spoilage", true);
      TEMPERATURE = b.comment("Body temperature: biome, season, altitude, weather, wind and wetness against clothing, shelter and fire. Hides keep you warm.")
         .define("bodyTemperature", true);
      LEAN_SEASONS = b.comment("Late winter thins the herds and leaves animals lean (less meat, no fat); fall animals are fattest. Stock up in the fall.")
         .define("leanSeasons", true);
      DRAIN_PERCENT = b.comment("Scales how fast protein, fat and energy run down (100 = the difficulty's own pace).")
         .defineInRange("drainPercent", 100, 25, 300);
   }

   /** Called from HuntConfig's client builder inside push("survival"). */
   public static void client(ModConfigSpec.Builder b) {
      HUD = b.comment("Show the survival HUD: protein, fat and energy bars above the hunger bar and the body-temperature gauge above the armour bar.")
         .define("survivalHud", true);
      UNITS = b.comment("Units for temperatures in the HUD and tooltips.").defineEnum("temperatureUnits", Units.CELSIUS);
      FROST = b.comment("Frost creeps in at the screen edges and the view trembles while you are freezing.").define("frostOverlay", true);
   }

   public static Difficulty difficulty() {
      try {
         return DIFFICULTY == null ? Difficulty.BALANCED : DIFFICULTY.get();
      } catch (Throwable t) {
         return Difficulty.BALANCED;
      }
   }

   public static SurvivalMath.Mode mode() {
      return difficulty().mode();
   }

   public static boolean spoilage() {
      return get(SPOILAGE, true) && mode().on();
   }

   public static boolean temperature() {
      return get(TEMPERATURE, true) && mode().on();
   }

   public static boolean leanSeasons() {
      return get(LEAN_SEASONS, true) && mode().on();
   }

   public static float drainScale() {
      try {
         return DRAIN_PERCENT == null ? 1F : DRAIN_PERCENT.get() / 100F;
      } catch (Throwable t) {
         return 1F;
      }
   }

   public static boolean hud() {
      return get(HUD, true);
   }

   public static boolean frost() {
      return get(FROST, true);
   }

   public static Units units() {
      try {
         return UNITS == null ? Units.CELSIUS : UNITS.get();
      } catch (Throwable t) {
         return Units.CELSIUS;
      }
   }

   private static boolean get(ModConfigSpec.BooleanValue v, boolean def) {
      try {
         return v == null ? def : v.get();
      } catch (Throwable t) {
         return def;
      }
   }
}
