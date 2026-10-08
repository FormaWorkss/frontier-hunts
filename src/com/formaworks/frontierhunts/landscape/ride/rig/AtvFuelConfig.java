package com.formaworks.frontierhunts.landscape.ride.rig;

import net.neoforged.neoforge.common.ModConfigSpec;

/** [atvfuel] Server options, pushed into HuntConfig's SERVER spec under [atvFuel] by a one-line hook. */
public final class AtvFuelConfig {
   static ModConfigSpec.BooleanValue REQUIRE_FUEL;
   static ModConfigSpec.DoubleValue FUEL_USE;
   static ModConfigSpec.IntValue START_PERCENT;

   private AtvFuelConfig() {
   }

   public static void server(ModConfigSpec.Builder b) {
      REQUIRE_FUEL = b.comment("ATVs need gasoline to run (creative-mode drivers never do). false = every ATV runs on an endless tank.")
         .define("requireFuel", true);
      FUEL_USE = b.comment("Fuel consumption multiplier. 1.0 = a full 15 L tank lasts about 35 minutes of mixed driving.")
         .defineInRange("fuelUseMultiplier", 1.0, 0.0, 10.0);
      START_PERCENT = b.comment("Tank level (percent) of a brand-new ATV placed from a crafted item.")
         .defineInRange("newAtvFuelPercent", 25, 0, 100);
   }

   public static boolean requireFuel() {
      try {
         return REQUIRE_FUEL == null || REQUIRE_FUEL.get();
      } catch (RuntimeException e) {
         return true;
      }
   }

   public static double fuelUse() {
      try {
         return FUEL_USE == null ? 1.0 : FUEL_USE.get();
      } catch (RuntimeException e) {
         return 1.0;
      }
   }

   public static float startFraction() {
      try {
         return START_PERCENT == null ? 0.25F : START_PERCENT.get() / 100.0F;
      } catch (RuntimeException e) {
         return 0.25F;
      }
   }
}
