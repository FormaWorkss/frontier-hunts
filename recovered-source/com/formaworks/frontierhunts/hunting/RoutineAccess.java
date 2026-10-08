package com.formaworks.frontierhunts.hunting;

/**
 * [routines] Public bridge to the package-private parts of {@link Whitetail} that the routine / rut logic in
 * {@code hunting.routine} drives (cues, gait speeds, thirst, head-down pose). Keeps the Whitetail edits to a few hooks.
 */
public final class RoutineAccess {
   private RoutineAccess() {
   }

   public static void cue(Whitetail deer, int cue) {
      deer.cue(cue);
   }

   public static boolean rising(Whitetail deer) {
      return deer.isRising();
   }

   /** The calm walking speed modifier the existing forage/stroll goals use. */
   public static double walkSpeed(Whitetail deer) {
      return deer.forageSpeed();
   }

   /** Navigation speed modifier for a gait speed in species units (see {@code GameSpecies.Behavior}). */
   public static double speedFor(Whitetail deer, double gait) {
      return deer.speedFor(gait);
   }

   public static int thirst(Whitetail deer) {
      return deer.thirst();
   }

   public static void quench(Whitetail deer) {
      deer.quench();
   }

   /** Head-down (graze/drink) pose for the given ticks; 0 lifts the head. */
   public static void headDown(Whitetail deer, int ticks) {
      deer.forageHeadDown(ticks);
   }

   public static boolean activeHours(Whitetail deer) {
      return deer.activeHours();
   }

   public static float herdRadius(Whitetail deer) {
      return deer.species().behavior.herdRadius();
   }
}
