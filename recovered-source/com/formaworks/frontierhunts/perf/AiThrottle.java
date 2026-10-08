package com.formaworks.frontierhunts.perf;

import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * [perf] Distance-based thinking rate for Frontier wildlife on the server.
 *
 * <p>Every animal caches the distance to its nearest player (re-measured every 20 ticks, every 5 when a
 * player is within reach of the full-rate band, staggered by entity id) and gets a tier: FULL inside
 * {@link PerfConfig#fullRateDistance()} blocks, REDUCED beyond it, MINIMAL beyond
 * {@link PerfConfig#minimalDistance()}. Only a <em>calm</em> animal is ever throttled: one that is hurt,
 * bleeding, downed, sedated, alarmed, fleeing, answering a call or has a target always runs at FULL,
 * the same tick it stops being calm. What a tier reduces:
 * <ul>
 * <li>goal selection (which goal to start or stop): every 4 ticks at REDUCED and 8 at MINIMAL instead
 * of every 2; running goals keep ticking exactly as before, and move, look, jump control and
 * navigation are never touched, so motion stays smooth ({@code PerfMobAiMixin}, {@code PerfGoalSelectorMixin});</li>
 * <li>perception scans that cannot find anyone: a whitetail's player scan (reach 72 blocks) while no
 * player is within 80, a wildlife mob's alarm scan (reach 24) while outside the full-rate band.</li>
 * </ul>
 * Nothing that leaves sign for hunters (blood trails, tracks, rut scrapes and rubs) is thinned: those
 * run from the animals' tick and rut work at their normal cadence.
 */
public final class AiThrottle {
   public static final int FULL = 0, REDUCED = 1, MINIMAL = 2;
   /** A whitetail notices players up to 72 blocks away; beyond this its scan finds no one. */
   static final double PERCEPTION_CLEAR = 80;

   /** Per-animal state: one field on each throttled animal. */
   public static final class State {
      long nextCheck = Long.MIN_VALUE;
      double nearest = 0;
      int tier;
      int stamp = Integer.MIN_VALUE;
   }

   // goal-selection skip for the mob whose serverAiStep is running (server thread)
   private static boolean skip;

   /** Diagnostics: animal-ticks per tier over the last full second, and the running count. */
   private static final int[] COUNT = new int[3], LAST = new int[3];
   private static long countSecond = Long.MIN_VALUE;

   private AiThrottle() {
   }

   private static State state(Mob mob) {
      if (mob instanceof Whitetail w) return w.perfAi;
      if (mob instanceof WildlifeMob m) return m.perfAi;
      return null;
   }

   /** Whether the animal may think less often right now. */
   static boolean calm(Mob mob) {
      if (mob.hurtTime > 0 || mob.isDeadOrDying() || mob.getTarget() != null || mob.isInWater() || mob.isPassenger()) return false;
      if (mob instanceof Whitetail w) {
         int b = w.behavior();
         return (b == 0 || b == 1 || b == 2) && !w.downed() && !w.bleeding() && !w.sedated() && !w.waking()
            && w.alertness() < 0.02F && !w.respondingToCall() && !w.approachingCall();
      }
      if (mob instanceof WildlifeMob m) {
         int b = m.behavior();
         return b == WildlifeMob.IDLE || b == WildlifeMob.FEED || b == WildlifeMob.REST;
      }
      return false;
   }

   /** The animal's tier this tick (computed once per tick). */
   public static int tier(Mob mob) {
      State s = state(mob);
      if (s == null || mob.level().isClientSide) return FULL;
      if (s.stamp == mob.tickCount) return s.tier;
      s.stamp = mob.tickCount;
      int tier = FULL;
      if (PerfConfig.aiThrottle() && calm(mob)) {
         long now = mob.level().getGameTime();
         int full = PerfConfig.fullRateDistance();
         if (now >= s.nextCheck) {
            s.nearest = nearestPlayer(mob);
            // near the band's edge look again soon: a player walking in gets full rate within a few ticks
            s.nextCheck = now + (s.nearest < full + 32 ? 5 : 16 + (mob.getId() & 7));
         }
         tier = s.nearest < full ? FULL : s.nearest < Math.max(full, PerfConfig.minimalDistance()) ? REDUCED : MINIMAL;
      } else {
         s.nextCheck = Long.MIN_VALUE; // when it calms down, measure afresh
      }
      s.tier = tier;
      count(mob, tier);
      return tier;
   }

   private static double nearestPlayer(Mob mob) {
      double best = Double.MAX_VALUE;
      List<? extends Player> players = mob.level().players();
      for (int i = 0; i < players.size(); i++) {
         // spectators count too: whoever can see the animal sees it at full rate
         best = Math.min(best, players.get(i).distanceToSqr(mob));
      }
      return Math.sqrt(best);
   }

   private static void count(Mob mob, int tier) {
      if (!(mob.level() instanceof ServerLevel level)) return;
      long second = level.getGameTime() / 20;
      if (second != countSecond) {
         if (second == countSecond + 1) System.arraycopy(COUNT, 0, LAST, 0, 3);
         else java.util.Arrays.fill(LAST, 0);
         java.util.Arrays.fill(COUNT, 0);
         countSecond = second;
      }
      COUNT[tier]++;
   }

   /** Diagnostics for the F3 screen (integrated server): animal-ticks per tier in the last second. */
   public static int[] lastSecond() {
      return LAST.clone();
   }

   // ------------------------------------------------------------------ hooks

   /** Mob.serverAiStep head (mixin): decides whether this tick's goal selection is skipped. */
   public static void enter(Mob mob) {
      skip = false;
      if (!(mob instanceof Whitetail) && !(mob instanceof WildlifeMob)) return;
      int tier = tier(mob);
      if (tier != FULL) {
         int period = tier == REDUCED ? 4 : 8;
         skip = (mob.tickCount + mob.getId() & period - 1) != 0;
      }
   }

   /** Mob.serverAiStep return (mixin). */
   public static void exit() {
      skip = false;
   }

   /** GoalSelector.tick head (mixin): true when this call should only tick the running goals. */
   public static boolean skipGoalSelection() {
      return skip;
   }

   /**
    * Whitetail: whether its every-5-ticks player scan runs this time. A calm deer with no player within
    * {@link #PERCEPTION_CLEAR} blocks (its senses reach 72) scans every 20 ticks instead: the scan could
    * find no one, and a calm deer's alertness is already at rest.
    */
   public static boolean perceive(Whitetail deer) {
      if (tier(deer) == FULL) return true;
      return deer.perfAi.nearest < PERCEPTION_CLEAR || deer.tickCount % 20 == 0;
   }

   /**
    * Wildlife mob: whether its every-10-ticks alarm scan runs this time. The scan reaches 24 blocks, up to
    * 36 for prey in pressured country ([routines] preyWariness 1.0-1.5), so within 40 it always runs; beyond
    * that and outside the full-rate band it cannot find anyone.
    */
   public static boolean alarmScan(WildlifeMob mob) {
      return tier(mob) == FULL || mob.perfAi.nearest < 40; // [integration] 40 > 24 x 1.5 (routines prey wariness)
   }
}
