package com.formaworks.frontierhunts.hunting.routine;

import com.formaworks.frontierhunts.hunting.DeerTraits;
import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.formaworks.frontierhunts.hunting.HuntSounds;
import com.formaworks.frontierhunts.hunting.RoutineAccess;
import com.formaworks.frontierhunts.hunting.Rut;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.hunting.rutfight.FightFit;
import com.formaworks.frontierhunts.hunting.rutfight.FightFits;
import com.formaworks.frontierhunts.hunting.rutfight.RutFightSounds;
import com.formaworks.frontierhunts.hunting.rutfight.RutFightState;
import com.formaworks.frontierhunts.hunting.rutfight.UltraFightModels;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Rut behaviour between animals: does coming into estrus, bucks trailing, chasing and tending them, and two mature
 * bucks (or bulls) that meet squaring off, circling and fighting. Server-side; clients see it through the synced
 * RUT_POSE byte (display / spar / tending head posture) and the shared cue timestamp, so both fighters' spar loops
 * start on the same tick and stay in phase.
 */
public final class RutEngine {
   public static final int POSE_NONE = 0;
   public static final int POSE_DISPLAY = 1;
   public static final int POSE_SPAR = 2;
   public static final int POSE_TEND = 3;
   private static final String ESTRUS_KEY = "fh_estrus_until";

   private RutEngine() {
   }

   /** Per-animal rut state (lives in {@link DeerRoutine}). */
   public static final class State {
      Engagement fight;
      Whitetail tendDoe;
      Whitetail tendedBy;
      long tendUntil;
      boolean running;
      long runUntil;
      long nextRunAt;
      long runRetarget;
      long nextScanAt;
      long fightCooldown;
      long chaseCooldown;
      long retreatUntil;
      Vec3 retreatFrom;
      Vec3 retreatTarget;
      long displayUntil;
      Vec3 displayAt;
      boolean callDisplay;
      long callAt;
      long nextVocal;
      long lastRepath;

      /** Busy with another animal: the routine, calls and the random rut cues keep out of the way. */
      public boolean busy() {
         return this.fight != null && !this.fight.done || this.tendDoe != null || this.running || this.retreatUntil > 0L;
      }
   }

   // ------------------------------------------------------------------ eligibility

   static boolean mature(Whitetail d) {
      DeerTraits t = d.traits();
      return t.buck() && !t.yearling() && t.ageMonths() >= 30;
   }

   static boolean calmFor(Whitetail d) {
      return d.behavior() == 0
         && !d.downed()
         && !d.sedated()
         && !d.waking()
         && !d.bleeding()
         && d.alertness() < 0.3F
         && !d.legsFolded()
         && !d.respondingToCall()
         && !RoutineAccess.rising(d);
   }

   static boolean canFight(Whitetail d, long now) {
      DeerRoutine r = d.routine();
      return mature(d) && calmFor(d) && r.rut.fight == null && r.rut.retreatUntil == 0L && now >= r.rut.fightCooldown && d.getHealth() > d.getMaxHealth() * 0.5F;
   }

   /** Seeking-to-post-rut window (month position) in which does cycle, per species. */
   private static double[] estrusWindow(GameSpecies s) {
      return switch (s) {
         case ELK -> new double[]{8.3, 9.0};
         case MOOSE -> new double[]{8.8, 9.47};
         default -> new double[]{10.0, 10.67};
      };
   }

   public static boolean estrus(Whitetail doe, ServerLevel level) {
      DeerTraits t = doe.traits();
      if (t.buck() || t.ageMonths() < 14 || doe.downed()) {
         return false;
      } else if (doe.getPersistentData().getLong(ESTRUS_KEY) > level.getGameTime()) {
         return true;
      } else {
         Rut.Phase p = doe.rut();
         if (p != Rut.Phase.SEEKING && p != Rut.Phase.PEAK) {
            return false;
         } else {
            double[] w = estrusWindow(doe.species());
            UUID id = doe.getUUID();
            double frac = Math.floorMod(id.getMostSignificantBits() * 31L + id.getLeastSignificantBits(), 10007L) / 10007.0;
            double span = w[1] - w[0];
            double centre = w[0] + 0.08 * span + frac * 0.84 * span;
            return Math.abs(Rut.yearPosition(level) - centre) <= 0.06;
         }
      }
   }

   public static void forceEstrus(Whitetail doe, long until) {
      doe.getPersistentData().putLong(ESTRUS_KEY, until);
   }

   // ------------------------------------------------------------------ per-tick (from DeerRoutine.serverTick)

   static void tick(DeerRoutine r, ServerLevel level, long now) {
      Whitetail d = r.deer;
      State s = r.rut;
      RandomSource random = d.getRandom();
      if (s.fight != null && s.fight.done) {
         s.fight = null;
      }

      // [rutfight] the rival's id goes to clients for the contact solve
      int partner = s.fight != null ? s.fight.other(d).getId() : -1;
      if (d.rutPartner() != partner) {
         d.setRutPartner(partner);
      }

      if (s.tendDoe != null) {
         Whitetail doe = s.tendDoe;
         if (doe.isRemoved() || doe.downed() || doe.level() != d.level() || now > s.tendUntil || doe.distanceToSqr(d) > 4096.0 || !estrus(doe, level)
            || doe.behavior() == 4 || d.behavior() == 4 || !RoutineConfig.chases()) {
            release(r, now);
         }
      }

      if (s.tendedBy != null && (s.tendedBy.isRemoved() || s.tendedBy.downed() || s.tendedBy.routine().rut.tendDoe != d)) {
         s.tendedBy = null;
         s.running = false;
      }

      if (s.retreatUntil > 0L && (now >= s.retreatUntil || d.behavior() != 0)) {
         s.retreatUntil = 0L;
      }

      // a tended doe breaks into a run when the buck crowds her, then stops and lets him catch up
      if (s.tendedBy != null && !s.running && now >= s.nextRunAt && calmFor(d)) {
         if (s.tendedBy.distanceToSqr(d) < 49.0 && random.nextFloat() < 0.45F) {
            s.running = true;
            s.runUntil = now + 60L + random.nextInt(110);
            s.runRetarget = 0L;
         } else {
            s.nextRunAt = now + 40L + random.nextInt(120);
         }
      }

      if (s.running && (now >= s.runUntil || s.tendedBy == null || d.behavior() != 0)) {
         s.running = false;
         s.nextRunAt = now + 160L + random.nextInt(360);
         d.getNavigation().stop();
      }

      if (s.callDisplay && !d.approachingCall()) {
         s.callDisplay = false;
      }

      if (now >= s.nextScanAt) {
         s.nextScanAt = now + 40L + random.nextInt(20);
         scan(r, level, now);
      }

      if (s.callDisplay && d.approachingCall() && d.getNavigation().isDone() && now >= s.nextVocal && random.nextFloat() < 0.35F) {
         // came in to the rattling and found nobody: stands and snort-wheezes / grunts looking for the fight
         s.nextVocal = now + 120L + random.nextInt(120);
         vocal(d, d.species() == GameSpecies.WHITETAIL ? HuntSounds.DEER_WHEEZE.get() : challenge(d), 1.0F);
      }

      r.pose = pose(r, now);
   }

   private static int pose(DeerRoutine r, long now) {
      State s = r.rut;
      Whitetail d = r.deer;
      if (d.behavior() != 0 && d.behavior() != 3) {
         return POSE_NONE;
      } else if (s.fight != null && !s.fight.done) {
         return s.fight.poseFor(d);
      } else if (s.displayUntil > now) {
         return POSE_DISPLAY;
      } else if (s.tendDoe != null && d.getDeltaMovement().horizontalDistanceSqr() > 0.004) {
         return POSE_TEND;
      } else {
         return s.callDisplay && d.approachingCall() && now - s.callAt > 40L ? POSE_DISPLAY : POSE_NONE;
      }
   }

   private static void scan(DeerRoutine r, ServerLevel level, long now) {
      Whitetail d = r.deer;
      State s = r.rut;
      if (!s.busy() && calmFor(d) && !d.approachingCall()) {
         Rut.Phase phase = d.rut();
         if (phase.active() && d.traits().buck()) {
            RandomSource random = d.getRandom();
            boolean seeking = phase == Rut.Phase.SEEKING || phase == Rut.Phase.PEAK;
            List<Whitetail> near = level.getEntitiesOfClass(
               Whitetail.class, d.getBoundingBox().inflate(28.0), e -> e != d && e.species() == d.species() && !e.downed() && !e.sedated()
            );
            if (seeking && RoutineConfig.chases() && d.traits().ageMonths() >= 18 && now >= s.chaseCooldown) {
               Whitetail doe = null;
               double best = Double.MAX_VALUE;

               for (Whitetail e : near) {
                  if (!e.traits().buck() && e.routine().rut.tendedBy != d && e.behavior() == 0 && estrus(e, level)) {
                     double dist = e.distanceToSqr(d);
                     if (dist < best) {
                        best = dist;
                        doe = e;
                     }
                  }
               }

               if (doe != null) {
                  Whitetail tender = doe.routine().rut.tendedBy;
                  if (tender == null) {
                     startTend(r, doe, now);
                     return;
                  } else if (RoutineConfig.fights() && canFight(d, now) && canFight(tender, now) && d.hasLineOfSight(tender)) {
                     // a satellite buck challenges the tending buck
                     startFight(d, tender, false, level, now);
                     return;
                  }
               }
            }

            if (RoutineConfig.fights() && canFight(d, now)) {
               float chance = switch (phase) {
                  case PEAK -> 0.12F;
                  case SEEKING -> 0.08F;
                  case PRE_RUT -> 0.04F;
                  default -> 0.0F;
               };
               Whitetail rival = null;
               double best = 324.0;

               for (Whitetail e : near) {
                  double dist = e.distanceToSqr(d);
                  if (dist < best && canFight(e, now) && !e.routine().rut.busy() && !e.approachingCall()) {
                     best = dist;
                     rival = e;
                  }
               }

               if (rival != null && random.nextFloat() < chance && d.hasLineOfSight(rival)) {
                  startFight(d, rival, phase == Rut.Phase.PRE_RUT, level, now);
               }
            }
         }
      }
   }

   static void startTend(DeerRoutine r, Whitetail doe, long now) {
      State s = r.rut;
      RandomSource random = r.deer.getRandom();
      s.tendDoe = doe;
      s.tendUntil = now + 3600L + random.nextInt(2400);
      State ds = doe.routine().rut;
      ds.tendedBy = r.deer;
      ds.nextRunAt = now + 20L + random.nextInt(60);
      s.nextVocal = now;
      r.deer.getNavigation().stop();
   }

   static void release(DeerRoutine r, long now) {
      State s = r.rut;
      if (s.tendDoe != null) {
         State ds = s.tendDoe.routine().rut;
         if (ds.tendedBy == r.deer) {
            ds.tendedBy = null;
            ds.running = false;
         }
      }

      s.tendDoe = null;
      s.chaseCooldown = now + 600L;
   }

   public static boolean startFight(Whitetail a, Whitetail b, boolean sparring, ServerLevel level, long now) {
      return startFight(a, b, sparring, false, level, now);
   }

   /** [rutfight] {@code direct}: skip the posturing / circling and go straight to closing in (debug command). */
   public static boolean startFight(Whitetail a, Whitetail b, boolean sparring, boolean direct, ServerLevel level, long now) {
      if (a == b || a.species() != b.species()) {
         return false;
      } else {
         DeerRoutine ra = a.routine();
         DeerRoutine rb = b.routine();
         if (ra.rut.tendDoe != null) {
            release(ra, now);
         }

         if (rb.rut.tendDoe != null) {
            // the tending buck turns to face the challenger; the doe stays put for now
            rb.rut.tendUntil = Math.max(rb.rut.tendUntil, now + 400L);
         }

         Engagement e = new Engagement(a, b, sparring, now);
         e.skipPosture = direct;
         ra.rut.fight = e;
         rb.rut.fight = e;
         a.routineCancelCall();
         b.routineCancelCall();
         a.getNavigation().stop();
         b.getNavigation().stop();
         return true;
      }
   }

   // ------------------------------------------------------------------ goal (RutGoal)

   static boolean goalWanted(DeerRoutine r, long now) {
      State s = r.rut;
      Whitetail d = r.deer;
      if (d.behavior() != 0 || d.downed() || d.sedated() || d.waking() || d.legsFolded() || RoutineAccess.rising(d)) {
         return false;
      } else if (s.fight != null && !s.fight.done || s.retreatUntil > now) {
         return true;
      } else if (d.bleeding() || d.alertness() > 0.45F) {
         return false;
      } else {
         return s.tendDoe != null || s.running || s.displayUntil > now;
      }
   }

   static void goalStop(DeerRoutine r) {
      State s = r.rut;
      if (s.fight != null && !s.fight.done) {
         s.fight.abort();
      }

      r.deer.getNavigation().stop();
   }

   static void goalTick(DeerRoutine r, long now) {
      State s = r.rut;
      Whitetail d = r.deer;
      ServerLevel level = (ServerLevel)d.level();
      RandomSource random = d.getRandom();
      if (d.graze(1.0F) > 0.0F) {
         RoutineAccess.headDown(d, 0);
      }

      if (s.fight != null && !s.fight.done) {
         s.fight.update(level, now);
         if (!s.fight.done) {
            s.fight.apply(d, level, now);
         }
      } else if (s.retreatUntil > now) {
         if (s.retreatTarget == null || d.getNavigation().isDone() && now - s.lastRepath > 20L) {
            Vec3 away = d.position().subtract(s.retreatFrom).multiply(1.0, 0.0, 1.0);
            if (away.lengthSqr() < 1.0E-4) {
               away = new Vec3(random.nextDouble() - 0.5, 0.0, random.nextDouble() - 0.5);
            }

            Vec3 aim = d.position().add(away.normalize().yRot((random.nextFloat() - 0.5F) * 0.8F).scale(22.0));
            s.retreatTarget = LandRandomPos.getPosTowards(d, 22, 7, aim);
            s.lastRepath = now;
            if (s.retreatTarget != null) {
               d.getNavigation().moveTo(s.retreatTarget.x, s.retreatTarget.y, s.retreatTarget.z, runSpeed(d));
            }
         }
      } else if (s.running && s.tendedBy != null) {
         // the doe runs ahead of the buck through the woods, curving away from him
         if (now >= s.runRetarget || d.getNavigation().isDone()) {
            s.runRetarget = now + 30L + random.nextInt(20);
            Vec3 away = d.position().subtract(s.tendedBy.position()).multiply(1.0, 0.0, 1.0);
            if (away.lengthSqr() < 1.0E-4) {
               away = d.getLookAngle().multiply(1.0, 0.0, 1.0);
            }

            Vec3 aim = d.position().add(away.normalize().yRot((random.nextFloat() - 0.5F) * 2.1F).scale(16.0));
            Vec3 pos = LandRandomPos.getPosTowards(d, 16, 6, aim);
            if (pos != null) {
               d.getNavigation().moveTo(pos.x, pos.y, pos.z, runSpeed(d) * 0.88);
            }
         }
      } else if (s.tendDoe != null) {
         Whitetail doe = s.tendDoe;
         State ds = doe.routine().rut;
         double dist = d.distanceToSqr(doe);
         PathNavigation nav = d.getNavigation();
         if (ds.running) {
            if (now - s.lastRepath >= 6L || nav.isDone()) {
               s.lastRepath = now;
               nav.moveTo(doe, runSpeed(d) * 0.9);
            }

            if (now >= s.nextVocal) {
               // whitetail bucks grunt with nearly every bound; bulls call far less often
               s.nextVocal = now + (d.species() == GameSpecies.WHITETAIL ? 20L + random.nextInt(26) : 140L + random.nextInt(160));
               vocal(d, tendingCall(d), d.species() == GameSpecies.ELK ? 0.6F : 0.9F);
            }
         } else if (dist > 20.0) {
            if (now - s.lastRepath >= 10L || nav.isDone()) {
               s.lastRepath = now;
               nav.moveTo(doe, RoutineAccess.walkSpeed(d) * (dist > 144.0 ? 1.4 : 1.05));
            }
         } else {
            nav.stop();
            d.getLookControl().setLookAt(doe, 20.0F, 20.0F);
            if (now >= s.nextVocal) {
               s.nextVocal = now + (d.species() == GameSpecies.WHITETAIL ? 80L + random.nextInt(100) : 300L + random.nextInt(300));
               if (random.nextFloat() < 0.6F) {
                  vocal(d, tendingCall(d), 0.75F);
               }
            }
         }
      } else if (s.displayUntil > now) {
         d.getNavigation().stop();
         if (s.displayAt != null) {
            d.getLookControl().setLookAt(s.displayAt.x, s.displayAt.y + 1.2, s.displayAt.z, 20.0F, 20.0F);
         }
      }
   }

   // ------------------------------------------------------------------ helpers

   static double runSpeed(Whitetail d) {
      return d.species() == GameSpecies.WHITETAIL ? 0.9 * 1.45 : RoutineAccess.speedFor(d, d.species().behavior.warySpeed() * 1.15);
   }

   static SoundEvent tendingCall(Whitetail d) {
      return switch (d.species()) {
         case ELK -> HuntSounds.ELK_BUGLE.get();
         case MOOSE -> HuntSounds.MOOSE_GRUNT.get();
         default -> HuntSounds.DEER_GRUNT.get();
      };
   }

   static SoundEvent challenge(Whitetail d) {
      return switch (d.species()) {
         case ELK -> HuntSounds.ELK_BUGLE.get();
         case MOOSE -> HuntSounds.MOOSE_GRUNT.get();
         default -> HuntSounds.DEER_WHEEZE.get();
      };
   }

   /** Same voice shaping as Whitetail#vocal (pitch from sex and frame, species voice pitch for shared deer sounds). */
   static void vocal(Whitetail d, SoundEvent sound, float volume) {
      RandomSource random = d.getRandom();
      float pitch = (d.traits().buck() ? 0.92F : 1.04F) + random.nextFloat() * 0.08F - (d.traits().naturalHeight() - 1.0F) * 0.3F;
      boolean own = sound == HuntSounds.ELK_BUGLE.get()
         || sound == HuntSounds.ELK_MEW.get()
         || sound == HuntSounds.ELK_BARK.get()
         || sound == HuntSounds.MOOSE_GRUNT.get()
         || sound == HuntSounds.MOOSE_CALL.get()
         || sound == HuntSounds.MOOSE_THREAT.get();
      float vol = volume * (sound == HuntSounds.ELK_BUGLE.get() ? 2.2F : (d.species() == GameSpecies.WHITETAIL ? 1.0F : 1.3F));
      d.level().playSound(null, d.blockPosition(), sound, SoundSource.NEUTRAL, Math.min(1.0F, vol), own ? pitch : pitch * d.species().behavior.voicePitch());
   }

   /** [rutfight] One antler-on-antler crack (single clacks cut from the rattling recordings), deeper for bigger racks. */
   static void clash(ServerLevel level, Vec3 at, GameSpecies species, RandomSource random, float volume) {
      float pitch = species == GameSpecies.WHITETAIL ? 0.95F + random.nextFloat() * 0.15F : (species == GameSpecies.ELK ? 0.72F : 0.62F) + random.nextFloat() * 0.1F;
      level.playSound(null, at.x, at.y, at.z, RutFightSounds.CLASH, SoundSource.NEUTRAL, Math.min(1.0F, volume * 1.6F), pitch); // [1.1.6] range from sounds.json
   }

   /** [rutfight] Locked racks grinding and ticking while the bulls twist and shove. */
   static void grind(ServerLevel level, Vec3 at, GameSpecies species, RandomSource random, float volume) {
      float pitch = species == GameSpecies.WHITETAIL ? 0.9F + random.nextFloat() * 0.2F : 0.65F + random.nextFloat() * 0.12F;
      level.playSound(null, at.x, at.y, at.z, RutFightSounds.GRIND, SoundSource.NEUTRAL, volume, pitch);
   }

   /** [rutfight] A watching client's locked distance for its graphics preset (see RutFightNet). */
   public static void report(Whitetail a, Whitetail b, net.minecraft.server.level.ServerPlayer player, float distance) {
      Engagement e = a.routine().rut.fight;
      if (e != null && !e.done && b.routine().rut.fight == e) {
         e.report(player, distance, a.level().getGameTime());
      }
   }

   /** [rutfight] Debug: the fight this animal is in, or null. */
   public static String fightInfo(Whitetail d) {
      Engagement e = d.routine().rut.fight;
      return e == null || e.done ? null : e.describe();
   }

   static void scuff(ServerLevel level, Whitetail d) {
      BlockPos below = d.blockPosition().below();
      BlockState ground = level.getBlockState(below);
      if (!ground.isAir()) {
         level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), d.getX(), d.getY() + 0.08, d.getZ(), 5, 0.3, 0.02, 0.3, 0.06);
         level.playSound(null, d.blockPosition(), ground.getSoundType().getStepSound(), SoundSource.NEUTRAL, 0.55F, ground.getSoundType().getPitch() * 0.7F);
      }
   }

   static String describe(DeerRoutine r, ServerLevel level) {
      State s = r.rut;
      Whitetail d = r.deer;
      if (s.fight != null && !s.fight.done) {
         return s.fight.describe();
      } else if (s.tendDoe != null) {
         return "tending a doe" + (s.tendDoe.routine().rut.running ? " (chasing)" : "");
      } else if (s.tendedBy != null) {
         return "in estrus, tended" + (s.running ? " (running)" : "");
      } else if (!d.traits().buck()) {
         return estrus(d, level) ? "in estrus" : "not in estrus";
      } else {
         return s.retreatUntil > 0L ? "beaten, retreating" : (mature(d) ? "mature" : "young") + (s.callDisplay ? ", coming to rattling" : "");
      }
   }

   /**
    * Two animals fighting: posture, circle, close (heads coming down), clash, locked shoving and twisting, short
    * disengages and re-clashes, resolve.
    *
    * <p>[rutfight] Once the heads are down the server drives both bodies itself every tick: the pair shares a contact
    * point and an axis, A stands {@code reachA} behind the contact and B {@code reachB} in front, so their feet are
    * exactly the locked distance apart - the distance where the two racks meet, fitted from the actual antler geometry
    * ({@link FightFit}: the realistic meshes here, or the box models of the nearest watching player's preset, see
    * {@link RutFightNet}). Shoving moves the contact point, twisting bouts swing the axis about it, positions and yaws
    * go out through the normal entity sync, and each client puts the head exactly on the contact from the synced fight
    * state.</p>
    */
   public static final class Engagement {
      static final int POSTURE = 0;
      static final int CIRCLE = 1;
      static final int CLOSE = 2;
      static final int LOCK = 3;
      static final int BREAK = 4;
      static final String[] PHASES = {"squaring off", "circling", "closing", "locked antlers", "backed off"};
      final Whitetail a;
      final Whitetail b;
      final boolean sparring;
      boolean done;
      int phase;
      long phaseAt;
      long phaseEnd;
      long lastUpdate = Long.MIN_VALUE;
      Vec3 mid;
      Vec3 axis;
      /** Locked feet-to-feet distance in use, and the one it is easing towards. */
      double contact;
      double contactTarget;
      /** Share of the locked distance in front of A (A's feet to the contact point), from the fit. */
      double shareA = 0.5;
      double push;
      double pushVel;
      double pushTarget;
      long nextPushChange;
      long nextClash;
      long nextGrind;
      long nextBreak;
      long breakAt;
      double sep;
      double spin;
      long nextSpin;
      float staminaA;
      float staminaB;
      final float strengthA;
      final float strengthB;
      int circleDir;
      long nextCircle;
      long fitWait;
      FightFit fit;
      final java.util.Map<java.util.UUID, double[]> reports = new java.util.HashMap<>();
      long lastReportCheck;
      boolean skipPosture;

      Engagement(Whitetail a, Whitetail b, boolean sparring, long now) {
         // A is always the lower entity id: clients order the pair the same way
         if (b.getId() < a.getId()) {
            Whitetail t = a;
            a = b;
            b = t;
         }

         this.a = a;
         this.b = b;
         this.sparring = sparring;
         RandomSource random = a.getRandom();
         this.strengthA = strength(a, random);
         this.strengthB = strength(b, random);
         float pool = sparring ? 120.0F : 320.0F;
         this.staminaA = pool * this.strengthA / Math.max(this.strengthA, this.strengthB) + random.nextInt(80);
         this.staminaB = pool * this.strengthB / Math.max(this.strengthA, this.strengthB) + random.nextInt(80);
         this.phase = POSTURE;
         this.phaseAt = now;
         this.phaseEnd = now + 40L + random.nextInt(50);
         this.circleDir = random.nextBoolean() ? 1 : -1;
         this.contact = this.contactTarget = estimate(a) + estimate(b);
         this.serverFit();
         vocal(a, challenge(a), 1.0F);
      }

      boolean locked() {
         return !this.done && (this.phase == LOCK || this.phase == BREAK);
      }

      String describe() {
         String s = (this.sparring ? "sparring: " : "fighting: ") + PHASES[this.phase];
         if (this.phase >= CLOSE) {
            s = s + String.format(" (locked distance %.2f%s)", this.contactTarget, this.fit == null ? ", fitting" : (this.reports.isEmpty() ? ", mesh fit" : ", player preset fit"));
         }

         return s;
      }

      static float strength(Whitetail d, RandomSource random) {
         DeerTraits t = d.traits();
         return (float)t.massKg() * (0.6F + 0.4F * t.maturity()) * (1.0F + t.totalPoints() * 0.03F) * (d.getHealth() / d.getMaxHealth()) * (0.85F + random.nextFloat() * 0.3F);
      }

      /** Rough rack reach before the geometry fit is in (only used for the first second or two of posturing). */
      static double estimate(Whitetail d) {
         double head = switch (d.species()) {
            case ELK -> 1.5;
            case MOOSE -> 1.25;
            default -> 0.5;
         };
         return head * d.traits().frameLength();
      }

      /** Locked distance from the realistic meshes (async; null until ready). */
      private FightFit serverFit() {
         if (this.fit == null) {
            DeerTraits ta = this.a.traits();
            DeerTraits tb = this.b.traits();
            this.fit = FightFits.get(FightFits.key(2, ta, tb), () -> UltraFightModels.of(ta), () -> UltraFightModels.of(tb));
            if (this.fit != null) {
               this.shareA = Mth.clamp(this.fit.contact.z / Math.max(0.1F, this.fit.distance), 0.2, 0.8);
            }
         }

         return this.fit;
      }

      void report(net.minecraft.server.level.ServerPlayer player, float distance, long now) {
         FightFit f = this.serverFit();
         double base = f != null ? f.distance : this.contact;
         if (distance < base * 0.5 || distance > base * 2.6 + 1.0) {
            return;
         }

         double[] old = this.reports.get(player.getUUID());
         if (old != null && now - (long)old[1] < 10L) {
            return;
         }

         if (this.reports.size() < 16 || old != null) {
            this.reports.put(player.getUUID(), new double[]{distance, now});
         }
      }

      /** The nearest watching player's preset fit, else the realistic mesh fit. */
      private double chooseContact(ServerLevel level, long now) {
         Vec3 at = this.mid != null ? this.mid : this.a.position().add(this.b.position()).scale(0.5);
         double best = Double.MAX_VALUE;
         double pick = -1.0;
         java.util.Iterator<java.util.Map.Entry<java.util.UUID, double[]>> it = this.reports.entrySet().iterator();

         while (it.hasNext()) {
            java.util.Map.Entry<java.util.UUID, double[]> e = it.next();
            net.minecraft.world.entity.player.Player p = level.getPlayerByUUID(e.getKey());
            if (p == null || now - (long)e.getValue()[1] > 2400L) {
               it.remove();
            } else {
               double dist = p.distanceToSqr(at);
               if (dist < 64.0 * 64.0 && dist < best) {
                  best = dist;
                  pick = e.getValue()[0];
               }
            }
         }

         FightFit f = this.serverFit();
         return pick > 0.0 ? pick : (f != null ? f.distance : this.contact);
      }

      int poseFor(Whitetail d) {
         return switch (this.phase) {
            case LOCK -> RutFightState.POSE_LOCK;
            case BREAK -> RutFightState.POSE_BREAK;
            case CLOSE -> Math.sqrt(this.a.distanceToSqr(this.b)) < this.contact + 2.2 + this.contact * 0.35 ? RutFightState.POSE_CLOSE : POSE_DISPLAY;
            default -> POSE_DISPLAY;
         };
      }

      Whitetail other(Whitetail d) {
         return d == this.a ? this.b : this.a;
      }

      void abort() {
         if (!this.done) {
            this.done = true;
            long now = this.a.level().getGameTime();
            this.a.routine().rut.fightCooldown = now + 600L;
            this.b.routine().rut.fightCooldown = now + 600L;
         }
      }

      private boolean ok(Whitetail d) {
         return !d.isRemoved() && !d.downed() && !d.sedated() && d.behavior() == 0 && !d.legsFolded() && d.alertness() < 0.5F && d.routine().rut.fight == this;
      }

      /** Advances the shared state once per game tick (either fighter may call it first) and moves both when engaged. */
      void update(ServerLevel level, long now) {
         if (this.lastUpdate != now) {
            this.lastUpdate = now;
            if (!this.ok(this.a) || !this.ok(this.b) || this.a.level() != this.b.level() || this.a.distanceToSqr(this.b) > 900.0 || !RoutineConfig.fights()) {
               this.abort();
            } else {
               RandomSource random = this.a.getRandom();
               double dist = Math.sqrt(this.a.distanceToSqr(this.b));
               this.serverFit();
               if (now - this.lastReportCheck >= 10L) {
                  this.lastReportCheck = now;
                  this.contactTarget = this.chooseContact(level, now);
               }

               switch (this.phase) {
                  case POSTURE:
                     if ((now >= this.phaseEnd || this.skipPosture) && dist < 12.0) {
                        float ratio = Math.max(this.strengthA, this.strengthB) / Math.max(1.0F, Math.min(this.strengthA, this.strengthB));
                        if (ratio > 1.6F && !this.sparring && !this.skipPosture) {
                           // badly outclassed: the smaller buck backs down without a fight
                           this.resolve(level, now, this.strengthA >= this.strengthB ? this.a : this.b, false);
                           return;
                        }

                        this.enter(CIRCLE, now, this.skipPosture ? 1 : 60 + random.nextInt(80));
                        this.mid = this.a.position().add(this.b.position()).scale(0.5);
                     } else if (now - this.phaseAt > 400L) {
                        this.abort();
                     }
                     break;
                  case CIRCLE:
                     // wait (still circling) for the contact fit, at most 5 s more
                     if (now >= this.phaseEnd && (this.fit != null || now - this.phaseEnd > 100L)) {
                        this.contact = this.contactTarget;
                        this.enter(CLOSE, now, 240);
                        this.mid = this.a.position().add(this.b.position()).scale(0.5);
                        Vec3 ab = this.b.position().subtract(this.a.position()).multiply(1.0, 0.0, 1.0);
                        this.axis = ab.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : ab.normalize();
                     }
                     break;
                  case CLOSE:
                     this.close(level, now, dist);
                     break;
                  case LOCK:
                  case BREAK:
                     this.shove(level, now, random);
               }
            }
         }
      }

      private void enter(int phase, long now, int ticks) {
         this.phase = phase;
         this.phaseAt = now;
         this.phaseEnd = now + ticks;
      }

      /** Walk in along the shared line; the last stretch is a lunge, and touching racks start the lock with a clash. */
      private void close(ServerLevel level, long now, double dist) {
         this.contact += Mth.clamp(this.contactTarget - this.contact, -0.05, 0.05);
         double gap = dist - this.contact;
         if (gap <= 0.03) {
            this.lock(level, now);
            return;
         } else if (now >= this.phaseEnd) {
            this.abort();
            return;
         }

         // re-aim the line at each other and close on the middle; nav until close, then straight steps
         Vec3 ab = this.b.position().subtract(this.a.position()).multiply(1.0, 0.0, 1.0);
         if (ab.lengthSqr() > 1.0E-4) {
            this.axis = ab.normalize();
         }

         this.mid = this.a.position().add(this.b.position()).scale(0.5);
         if (gap < 3.0) {
            double step = gap < 0.7 ? 0.11 : 0.055;
            double each = Math.min(step, gap * 0.5);
            this.step(this.a, this.axis.scale(each));
            this.step(this.b, this.axis.scale(-each));
            this.faceAxis(this.a, this.axis, 14.0F);
            this.faceAxis(this.b, this.axis.reverse(), 14.0F);
         }
      }

      private void lock(ServerLevel level, long now) {
         RandomSource random = this.a.getRandom();
         this.enter(LOCK, now, 100000);
         Vec3 ab = this.b.position().subtract(this.a.position()).multiply(1.0, 0.0, 1.0);
         this.axis = ab.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : ab.normalize();
         // contact point: A's share of the locked distance in front of A
         this.mid = this.a.position().add(this.axis.scale(this.contact * this.shareA));
         this.push = 0.0;
         this.pushVel = 0.0;
         this.pushTarget = 0.0;
         this.sep = 0.0;
         this.nextPushChange = now + 10L;
         this.nextClash = now + 90L + random.nextInt(40);
         this.nextGrind = now + 25L + random.nextInt(20);
         this.nextBreak = now + 120L + random.nextInt(160);
         this.nextSpin = now + 40L + random.nextInt(60);
         this.place(level);
         this.clashHit(level, now, this.sparring ? 1.0F : 1.4F);
         if (!this.sparring) {
            // the sound of a real fight draws other bucks in, like rattling does
            for (Whitetail e : level.getEntitiesOfClass(Whitetail.class, this.a.getBoundingBox().inflate(48.0), x -> x != this.a && x != this.b && x.species() == this.a.species())) {
               if (mature(e) && e.alertness() < 0.3F && !e.routine().rut.busy()) {
                  float answer = Rut.answer(Rut.Call.RATTLE, e.rut(), true, true) * 0.6F;
                  if (answer > 0.2F) {
                     e.approachCall(this.mid, answer, true);
                  }
               }
            }
         }
      }

      /** Contact: the clack, a puff of dust, and the shared clash cue that jolts both heads on every client. */
      private void clashHit(ServerLevel level, long now, float volume) {
         RoutineAccess.cue(this.a, RutFightState.CUE_CLASH);
         RoutineAccess.cue(this.b, RutFightState.CUE_CLASH);
         Vec3 at = this.mid.add(0.0, this.a.getBbHeight() * 0.45, 0.0);
         clash(level, at, this.a.species(), this.a.getRandom(), volume);
         scuff(level, this.a);
         scuff(level, this.b);
      }

      private void shove(ServerLevel level, long now, RandomSource random) {
         this.contact += Mth.clamp(this.contactTarget - this.contact, -0.02, 0.02);
         // push > 0: a drives b back along the axis. The stronger animal tends to win ground.
         if (now >= this.nextPushChange) {
            this.nextPushChange = now + 20L + random.nextInt(30);
            float bias = (this.strengthA - this.strengthB) / Math.max(this.strengthA, this.strengthB);
            this.pushTarget = Mth.clamp((random.nextDouble() * 2.0 - 1.0) * 0.9 + bias * 0.8, -1.3, 1.3);
         }

         double before = this.push;
         this.pushVel = this.phase == BREAK ? 0.0 : Mth.clamp((this.pushTarget - this.push) * 0.08, -0.03, 0.03);
         this.push += this.pushVel;
         this.mid = this.mid.add(this.axis.scale(this.pushVel));
         // twisting bouts swing the locked pair round the contact point
         if (now >= this.nextSpin) {
            this.spin = this.spin == 0.0 ? (random.nextBoolean() ? 1.0 : -1.0) * (0.006 + random.nextDouble() * 0.008) * (this.sparring ? 0.6 : 1.0) : 0.0;
            this.nextSpin = now + (this.spin == 0.0 ? 40L + random.nextInt(80) : 20L + random.nextInt(30));
         }

         if (this.spin != 0.0 && this.phase == LOCK) {
            this.axis = this.axis.yRot((float)this.spin);
         }

         float drainA = 1.0F + (float)Math.max(0.0, -this.pushVel) * 60.0F;
         float drainB = 1.0F + (float)Math.max(0.0, this.pushVel) * 60.0F;
         this.staminaA -= drainA;
         this.staminaB -= drainB;
         if (this.phase == LOCK && now >= this.nextBreak) {
            // break contact, back off a step, and crash back together
            this.enter(BREAK, now, 100000);
            this.breakAt = now;
            this.nextBreak = now + 110L + random.nextInt(170);
            this.spin = 0.0;
         }

         if (this.phase == BREAK) {
            long t = now - this.breakAt;
            double out = 0.22 + this.contact * 0.05;
            if (t < 10L) {
               this.sep = out * (t + 1) / 10.0;
            } else if (t < 20L) {
               this.sep = out;
            } else {
               this.sep = Math.max(0.0, this.sep - 0.12);
               if (this.sep <= 0.0) {
                  this.enter(LOCK, now, 100000);
                  this.clashHit(level, now, this.sparring ? 1.0F : 1.5F);
                  this.nextClash = now + 90L + random.nextInt(40);
               }
            }
         } else if (now >= this.nextClash) {
            this.nextClash = now + 95L + random.nextInt(35);
            clash(level, this.mid.add(0.0, this.a.getBbHeight() * 0.45, 0.0), this.a.species(), random, this.sparring ? 0.8F : 1.1F);
         } else if (now >= this.nextGrind) {
            this.nextGrind = now + 22L + random.nextInt(30);
            grind(level, this.mid.add(0.0, this.a.getBbHeight() * 0.45, 0.0), this.a.species(), random, this.spin != 0.0 ? 0.9F : 0.55F);
         }

         this.place(level);
         if (Math.abs(this.push - before) > 0.012 && now % 10L == 0L) {
            scuff(level, this.pushVel > 0.0 ? this.b : this.a);
         }

         if (now % 70L == 0L && random.nextFloat() < 0.4F) {
            Whitetail v = random.nextBoolean() ? this.a : this.b;
            vocal(v, v.species() == GameSpecies.ELK ? HuntSounds.ELK_BARK.get() : tendingCall(v), 0.8F);
         }

         if (this.phase == LOCK && (this.staminaA <= 0.0F || this.staminaB <= 0.0F || now - this.phaseAt > 1400L)) {
            Whitetail winner = this.staminaA > this.staminaB ? this.a : this.b;
            this.resolve(level, now, winner, true);
         }
      }

      /**
       * Puts both bodies exactly on the locked line: A behind the contact by its share of the distance, B in front by
       * the rest (plus the disengage separation each). If terrain stops one of them, the contact point follows the
       * stopped animal and the other is placed against it, so the distance stays exact.
       */
      private void place(ServerLevel level) {
         double ra = this.contact * this.shareA + this.sep;
         double rb = this.contact * (1.0 - this.shareA) + this.sep;
         Vec3 ta = this.mid.subtract(this.axis.scale(ra));
         Vec3 tb = this.mid.add(this.axis.scale(rb));
         boolean okA = this.moveTo(this.a, ta);
         boolean okB = this.moveTo(this.b, tb);
         if (!okA || !okB) {
            Whitetail stuck = okA ? this.b : this.a;
            this.mid = stuck == this.a ? flat(this.a.position(), this.mid).add(this.axis.scale(ra)) : flat(this.b.position(), this.mid).subtract(this.axis.scale(rb));
            this.moveTo(this.a, this.mid.subtract(this.axis.scale(ra)));
            this.moveTo(this.b, this.mid.add(this.axis.scale(rb)));
            this.pushTarget = -this.pushTarget * 0.5;
         }

         this.faceAxis(this.a, this.axis, 360.0F);
         this.faceAxis(this.b, this.axis.reverse(), 360.0F);
      }

      private static Vec3 flat(Vec3 p, Vec3 keepY) {
         return new Vec3(p.x, keepY.y, p.z);
      }

      /** Horizontal move with collisions (step-up, walls); true when it got there. */
      private boolean moveTo(Whitetail d, Vec3 target) {
         Vec3 delta = new Vec3(target.x - d.getX(), 0.0, target.z - d.getZ());
         if (delta.lengthSqr() > 1.0E-8) {
            d.move(net.minecraft.world.entity.MoverType.SELF, delta);
         }

         d.setDeltaMovement(0.0, d.getDeltaMovement().y, 0.0);
         d.getNavigation().stop();
         double ex = target.x - d.getX();
         double ez = target.z - d.getZ();
         return ex * ex + ez * ez < 0.0025;
      }

      private void step(Whitetail d, Vec3 delta) {
         d.getNavigation().stop();
         d.move(net.minecraft.world.entity.MoverType.SELF, delta);
         d.setDeltaMovement(0.0, d.getDeltaMovement().y, 0.0);
      }

      private void faceAxis(Whitetail d, Vec3 dir, float maxTurn) {
         float want = (float)(Math.atan2(dir.z, dir.x) * 180.0F / (float)Math.PI) - 90.0F;
         float yaw = Mth.approachDegrees(d.getYRot(), want, maxTurn);
         d.setYRot(yaw);
         d.yBodyRot = yaw;
         d.yHeadRot = yaw;
      }

      private void resolve(ServerLevel level, long now, Whitetail winner, boolean fought) {
         Whitetail loser = this.other(winner);
         RandomSource random = winner.getRandom();
         this.done = true;
         State ws = winner.routine().rut;
         State ls = loser.routine().rut;
         ws.fightCooldown = now + (this.sparring ? 1800L : 2400L);
         ls.fightCooldown = now + (this.sparring ? 3600L : 7200L);
         ws.displayUntil = now + 70L + random.nextInt(50);
         ws.displayAt = loser.position();
         ls.retreatUntil = now + 100L + random.nextInt(60);
         ls.retreatFrom = winner.position();
         ls.retreatTarget = null;
         if (fought) {
            // the loser wrenches free: a last clack as the racks part
            clash(level, (this.mid != null ? this.mid : loser.position()).add(0.0, loser.getBbHeight() * 0.45, 0.0), loser.species(), random, 1.0F);
         }

         if (ls.tendDoe != null) {
            // the beaten buck loses the doe; the winner takes over
            Whitetail doe = ls.tendDoe;
            release(loser.routine(), now);
            if (ws.tendDoe == null && estrus(doe, level)) {
               startTend(winner.routine(), doe, now);
            }
         }

         vocal(winner, challenge(winner), 1.0F);
         if (fought && !this.sparring && random.nextFloat() < 0.12F && loser.getHealth() > loser.getMaxHealth() * 0.6F) {
            // an antler tine found its mark
            loser.hurt(level.damageSources().generic(), 1.5F + random.nextFloat() * 1.5F);
         }
      }

      /** Turn the body toward the rival a little per tick (never a snap spin). */
      private void face(Whitetail d, Whitetail o) {
         float want = (float)(Math.atan2(o.getZ() - d.getZ(), o.getX() - d.getX()) * 180.0F / (float)Math.PI) - 90.0F;
         float yaw = Mth.approachDegrees(d.getYRot(), want, 8.0F);
         d.setYRot(yaw);
         d.yBodyRot = yaw;
      }

      /** Movement for one fighter this tick (the engaged phases are moved by {@link #update}). */
      void apply(Whitetail d, ServerLevel level, long now) {
         Whitetail o = this.other(d);
         PathNavigation nav = d.getNavigation();
         switch (this.phase) {
            case POSTURE: {
               double dist = Math.sqrt(d.distanceToSqr(o));
               if (dist > 9.0) {
                  if (nav.isDone() || now % 20L == 0L) {
                     nav.moveTo(o, RoutineAccess.walkSpeed(d) * 0.55);
                  }
               } else {
                  nav.stop();
               }

               d.getLookControl().setLookAt(o, 30.0F, 30.0F);
               break;
            }
            case CIRCLE: {
               // both walk the same way round the midpoint, staying opposite each other, heads turned to the rival
               if (now >= d.routine().rut.lastRepath + 12L || nav.isDone()) {
                  d.routine().rut.lastRepath = now;
                  Vec3 off = d.position().subtract(this.mid).multiply(1.0, 0.0, 1.0);
                  double r = Mth.clamp(off.length(), this.contact * 0.5 + 1.8, 6.0 + this.contact * 0.3);
                  double ang = Math.atan2(off.z, off.x) + this.circleDir * 0.55;
                  Vec3 t = this.mid.add(Math.cos(ang) * r, 0.0, Math.sin(ang) * r);
                  nav.moveTo(t.x, d.getY(), t.z, RoutineAccess.walkSpeed(d) * 0.5);
               }

               d.getLookControl().setLookAt(o, 30.0F, 30.0F);
               break;
            }
            case CLOSE: {
               double gap = Math.sqrt(d.distanceToSqr(o)) - this.contact;
               if (gap >= 3.0) {
                  Vec3 goal = this.mid.add(d.position().subtract(this.mid).multiply(1.0, 0.0, 1.0).normalize().scale(this.contact * 0.5 + 1.0));
                  if (nav.isDone() || now % 15L == 0L) {
                     nav.moveTo(goal.x, d.getY(), goal.z, RoutineAccess.walkSpeed(d) * 0.55);
                  }

                  this.face(d, o);
               }

               d.getLookControl().setLookAt(o.getX(), o.getEyeY() - 0.6, o.getZ(), 30.0F, 30.0F);
               break;
            }
            default:
               // LOCK / BREAK: update() placed both bodies this tick
               nav.stop();
               d.setDeltaMovement(0.0, d.getDeltaMovement().y, 0.0);
         }
      }
   }
}
