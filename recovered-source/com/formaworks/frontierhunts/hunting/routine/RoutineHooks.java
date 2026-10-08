package com.formaworks.frontierhunts.hunting.routine;

import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import com.formaworks.frontierhunts.wildlife2026.WildlifeSpecies;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * [routines] The one-line hooks called from Whitetail / WildlifeMob. Everything the hooks decide lives here or in
 * {@link DeerRoutine}, so the shared entity classes only carry short, marked call sites.
 */
public final class RoutineHooks {
   /** per prey animal: {pressure units, game time read} - refreshed every 10 s */
   private static final java.util.Map<WildlifeMob, double[]> PREY = new java.util.WeakHashMap<>();
   private RoutineHooks() {
   }

   // ---------------------------------------------------------------- Whitetail (deer, elk, moose)

   /** customServerAiStep: herd, range survey, schedule, rut state, synced rut pose. */
   public static void serverTick(Whitetail deer) {
      deer.routine().serverTick();
   }

   /** Multiplier on the alertness gain from sight, sound and scent (pressure, rut distraction). */
   public static float wariness(Whitetail deer) {
      return deer.level().isClientSide ? 1.0F : deer.routine().wariness();
   }

   /** Hunters are noticed from further away in pressured country: distances are divided by this (1.0 .. 1.5). */
   public static float alertRange(Whitetail deer) {
      return deer.level().isClientSide ? 1.0F : deer.routine().alertRange();
   }

   /**
    * After perceive(): when the strongest stimulus was scent, a strong enough whiff busts the animal at once (alertness
    * jumps to 1, it blows and bolts even if it never saw the hunter), and any scent-driven bust is flagged so the flight
    * starts with the blow instead of a random call.
    *
    * @param scent  scent contribution this pass (the 0.16-scaled value from perceive)
    * @param other  sound + sight contribution of the same hunter
    */
   public static float afterPerceive(Whitetail deer, Player source, float scent, float other, float before, float after) {
      return deer.routine().afterPerceive(source, scent, other, before, after);
   }

   /** A scent bust is under way (skip the snort; the blow comes with the flight). */
   public static boolean scentBusted(Whitetail deer) {
      return deer.routine().scentBust;
   }

   /**
    * The animal breaks into flight from a threat: records hunting pressure (if a hunter caused it) and returns true when
    * the flight was caused by scent, so it always blows.
    */
   public static boolean bolted(Whitetail deer, Vec3 threat) {
      PressureEvents.spooked(deer, threat);
      DeerRoutine r = deer.routine();
      boolean scent = r.scentBust;
      r.scentBust = false;
      return scent;
   }

   /** The routine decides when and where to bed; the old random bedding roll is skipped. */
   public static boolean ownsBedding(Whitetail deer) {
      return deer.routine().ownsBedding() || com.formaworks.frontierhunts.hunting.herd.HerdService.followTarget(deer) != null; // [herds] followers bed with their leader
   }

   /** Random strolling would pull the animal off its routine: movement comes from the routine instead. */
   public static boolean suppressStroll(Whitetail deer) {
      return deer.routine().active() || com.formaworks.frontierhunts.hunting.herd.HerdService.followTarget(deer) != null; // [herds] followers go where the leader goes
   }

   /** Fighting, tending, chasing or retreating: calls, rubs/scrapes and idle cues stay out of the way. */
   public static boolean rutBusy(Whitetail deer) {
      return !deer.level().isClientSide && deer.routine().rut.busy();
   }

   /** The new rut interactions replace the old solo "rival display" cue. */
   public static boolean rutFightsEnabled() {
      return RoutineConfig.fights();
   }

   /** Called when the animal accepts a call; aggressive calls (rattling, snort-wheeze) make mature bucks come in bristling. */
   public static void onCall(Whitetail deer, boolean aggressive) {
      if (!deer.level().isClientSide) {
         RutEngine.State s = deer.routine().rut;
         s.callDisplay = aggressive && RutEngine.mature(deer) && deer.rut().active();
         s.callAt = deer.level().getGameTime();
      }
   }

   // ---------------------------------------------------------------- WildlifeMob prey (pronghorn, bison, boar, grouse, duck)

   public static boolean prey(WildlifeSpecies s) {
      return s == WildlifeSpecies.PRONGHORN || s == WildlifeSpecies.BISON || s == WildlifeSpecies.BOAR || s == WildlifeSpecies.GROUSE || s == WildlifeSpecies.DUCK;
   }

   /** Alarm distance multiplier for prey in pressured country (1.0 .. 1.5). */
   public static double preyWariness(WildlifeMob mob) {
      if (mob.species != null && prey(mob.species) && mob.level() instanceof ServerLevel level && RoutineConfig.pressure()) {
         long now = level.getGameTime();
         double[] cache = PREY.computeIfAbsent(mob, m -> new double[]{0.0, Double.NEGATIVE_INFINITY});
         if (now - cache[1] >= 200.0 || now < cache[1]) {
            cache[0] = PressureEvents.units(level, mob.position());
            cache[1] = now;
         }

         return 1.0 + 0.5 * PressureStore.level01((float)cache[0]);
      } else {
         return 1.0;
      }
   }

   public static void preySpooked(WildlifeMob mob, Vec3 threat) {
      if (mob.species != null && prey(mob.species)) {
         PressureEvents.spooked(mob, threat);
      }
   }
}
