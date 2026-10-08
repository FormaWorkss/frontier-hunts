package com.formaworks.frontierhunts.hunting.routine;

import com.formaworks.frontierhunts.HuntConfig;
import java.util.function.Supplier;

/** Safe reads of the [routines] server config: falls back to the defaults before the server config has loaded. */
public final class RoutineConfig {
   private RoutineConfig() {
   }

   private static <T> T get(Supplier<T> value, T fallback) {
      try {
         T v = value.get();
         return v == null ? fallback : v;
      } catch (RuntimeException e) {
         return fallback;
      }
   }

   public static boolean routines() {
      return get(HuntConfig.ROUTINES_ENABLED::get, true);
   }

   public static int homeRange() {
      return get(HuntConfig.ROUTINE_HOME_RANGE::get, 64);
   }

   public static double scentBust() {
      return get(HuntConfig.SCENT_BUST_THRESHOLD::get, 0.35);
   }

   public static boolean pressure() {
      return get(HuntConfig.PRESSURE_ENABLED::get, true);
   }

   public static double halfLifeDays() {
      return get(HuntConfig.PRESSURE_HALF_LIFE_DAYS::get, 3.0);
   }

   public static double shotWeight() {
      return get(HuntConfig.PRESSURE_SHOT_WEIGHT::get, 1.0);
   }

   public static double nightShift() {
      return get(HuntConfig.PRESSURE_NIGHT_SHIFT::get, 1.0);
   }

   public static double relocateAt() {
      return get(HuntConfig.PRESSURE_RELOCATE::get, 6.0);
   }

   public static boolean fights() {
      return get(HuntConfig.RUT_FIGHTS::get, true);
   }

   public static boolean chases() {
      return get(HuntConfig.RUT_CHASES::get, true);
   }
}
