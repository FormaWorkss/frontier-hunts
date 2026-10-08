package com.formaworks.frontierhunts.hunting.routine;

import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.formaworks.frontierhunts.hunting.RoutineAccess;
import com.formaworks.frontierhunts.hunting.Rut;
import com.formaworks.frontierhunts.hunting.TrackClue;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.hunting.herd.HerdMember;
import com.formaworks.frontierhunts.hunting.herd.HerdService;
import com.formaworks.frontierhunts.hunting.herd.HerdTuning;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Per-animal routine state for a deer, elk or moose: which herd home range it belongs to, what the schedule wants
 * right now, and the task it is carrying out (walking a game trail, a feeding step, drinking, lying up). The heavy
 * pieces (anchor survey, trail recording) are shared through the herd's {@link HomeRange}.
 *
 * Everything is driven by game time, not by counting ticks, so the logic tolerates being ticked less often when the
 * animal is far from players. The existing alarm / flee / call / rut systems interrupt it simply by taking the
 * animal out of the calm state; the routine picks up again from wherever the animal ends up.
 */
public final class DeerRoutine {
   static final int T_NONE = 0;
   static final int T_TRAVEL = 1;
   static final int T_STEP = 2;
   static final int T_DRINK = 3;
   static final int T_BEDSPOT = 4;
   static final int T_FOLLOW = 5; // [herds] walking behind the group's leader (or its mother)
   static final String[] TASKS = {"idle", "walking a trail", "feeding step", "drinking", "going to bed", "following the group"};
   private static final String HERD_KEY = "fh_herd";
   final Whitetail deer;
   public final RutEngine.State rut = new RutEngine.State();
   private UUID herdId;
   HomeRange range;
   private long nextHerdCheck;
   private long nextPlanAt;
   private long nextSearchAt;
   Schedule.Plan plan;
   private int lastActivity = -1;
   int at = -1;
   int task;
   private long nextTaskAt;
   private long taskStart;
   private int legTo = -2;
   private int legFrom = -1;
   private long departAt;
   private final List<BlockPos> route = new ArrayList<>();
   private int wp;
   private boolean onTrail;
   private int trailIndex = -1;
   private boolean recording;
   private final List<BlockPos> crumbs = new ArrayList<>();
   private BlockPos lastCrumb;
   private BlockPos recordFrom;
   private BlockPos recordTo;
   private long wpSince;
   private double wpBest;
   private int fails;
   private long pauseUntil;
   private boolean pendingGraze;
   private boolean edgeDone;
   private long lastMoveAt;
   private double travelSpeed;
   private BlockPos stepTarget;
   private int stretchSteps;
   private BlockPos bedSpot;
   private BlockPos bedSpotFor;
   private long wateredDay = Long.MIN_VALUE;
   private int drinkFails;
   private boolean drinking;
   private long drinkUntil;
   private int drinkFx;
   private BlockPos cruiseTarget;
   private long cruiseUntil;
   private boolean wasBedded;
   float unitsHere;
   float unitsFeed;
   float unitsBed;
   private long pressureAt = Long.MIN_VALUE;
   boolean scentBust;
   int pose;
   /** [herds] social state: group, the animal followed, breadcrumbs (hunting.herd) */
   public final HerdMember social;
   // [herds] following
   private boolean followerMode;
   private Whitetail followed;
   private long followRepath;
   private Vec3 followNav;
   private double followLastX;
   private double followLastZ;
   private long followMovedAt;
   private BlockPos debugGoal;

   public DeerRoutine(Whitetail deer) {
      this.deer = deer;
      this.social = HerdMember.create(deer); // [herds]
   }

   /** [herds] On a routine leg (trail walk, rut cruise, a commanded walk). */
   public boolean travelling() {
      return this.task == T_TRAVEL && !this.followerMode;
   }

   /** [herds] Busy with something on the spot (a feeding step, drinking, settling into a bed). */
   public boolean localTask() {
      return this.task == T_STEP || this.task == T_DRINK || this.task == T_BEDSPOT;
   }

   // ------------------------------------------------------------------ queries used by hooks and commands

   public boolean active() {
      return this.range != null && this.range.ready() && RoutineConfig.routines();
   }

   public HomeRange range() {
      return this.range;
   }

   public Schedule.Plan plan() {
      return this.plan;
   }

   public String taskName() {
      return TASKS[this.task];
   }

   public boolean onTrail() {
      return this.task == T_TRAVEL && this.onTrail;
   }

   public float pressureLevel() {
      return PressureStore.level01(Math.max(this.unitsHere, this.at == HomeRange.FEED ? this.unitsFeed : 0.0F));
   }

   /** Multiplier on everything that raises alertness: hunting pressure makes animals warier, a fight distracts them. */
   public float wariness() {
      float w = 1.0F + 0.25F * PressureStore.level01(this.unitsHere);
      if (this.rut.fight != null && this.rut.fight.locked()) {
         w *= 0.55F;
      } else if (this.rut.tendDoe != null || this.rut.tendedBy != null) {
         w *= 0.8F;
      }

      return w;
   }

   /** Pressure widens the distance at which hunters are seen and heard (up to half again). */
   public float alertRange() {
      return 1.0F + 0.5F * PressureStore.level01(this.unitsHere);
   }

   boolean calm() {
      Whitetail d = this.deer;
      return d.behavior() == 0
         && !d.downed()
         && !d.sedated()
         && !d.waking()
         && !d.bleeding()
         && d.alertness() < 0.25F
         && !d.approachingCall()
         && !d.respondingToCall()
         && !d.legsFolded()
         && !RoutineAccess.rising(d);
   }

   // ------------------------------------------------------------------ server tick

   public void serverTick() {
      if (this.deer.level() instanceof ServerLevel level) {
         long now = level.getGameTime();
         if (this.deer.downed()) {
            this.pose = 0;
            this.applyPose();
         } else {
            if (this.deer.alertness() < 0.2F) {
               this.scentBust = false;
            }

            boolean bedded = this.deer.behavior() == 1 || this.deer.behavior() == 2;
            if (this.wasBedded && !bedded && this.deer.behavior() == 0 && this.plan != null && this.plan.activity() == Schedule.BED) {
               // got up from the bed mid-day: stretch, browse a few steps, lie back down
               this.stretchSteps = 1 + this.deer.getRandom().nextInt(3);
               this.nextTaskAt = now + 30L;
            }

            this.wasBedded = bedded;
            if (now - this.pressureAt >= 200L || now < this.pressureAt) {
               this.pressureAt = now;
               this.refreshPressure(level, now);
            }

            HerdService.tick(this.deer, level, now); // [herds] social group: membership, breadcrumbs, waking with the group
            if (RoutineConfig.routines()) {
               if (now >= this.nextHerdCheck) {
                  this.nextHerdCheck = now + 200L + this.deer.getRandom().nextInt(40);
                  this.herd(level, now);
               }

               if (this.range != null && now >= this.nextSearchAt) {
                  this.nextSearchAt = now + 5L;
                  this.survey(level, now);
               }

               if (this.active() && (now >= this.nextPlanAt || this.plan == null)) {
                  this.nextPlanAt = now + 20L;
                  this.replan(level, now);
               }
            } else {
               this.range = null;
            }

            RutEngine.tick(this, level, now);
            this.idleTick();
            this.applyPose();
         }
      }
   }

   private void applyPose() {
      int old = this.deer.rutPose();
      if (old != this.pose) {
         this.deer.setRutPose(this.pose);
         if (this.pose == RutEngine.POSE_DISPLAY) {
            RoutineAccess.cue(this.deer, 6);
         } else if (this.pose == RutEngine.POSE_SPAR && this.deer.cue() != 7) {
            RoutineAccess.cue(this.deer, 7);
         }
      }
   }

   private void refreshPressure(ServerLevel level, long now) {
      if (!RoutineConfig.pressure()) {
         this.unitsHere = this.unitsFeed = this.unitsBed = 0.0F;
      } else {
         PressureStore p = PressureStore.of(level);
         this.unitsHere = p.value(now, this.deer.blockPosition());
         if (this.range != null && this.range.ready()) {
            this.unitsFeed = p.value(now, this.range.anchors[HomeRange.FEED]);
            this.unitsBed = p.value(now, this.range.anchors[HomeRange.BED]);
         }
      }
   }

   // ------------------------------------------------------------------ herd + range

   private void herd(ServerLevel level, long now) {
      RoutineStore store = RoutineStore.of(level);
      CompoundTag data = this.deer.getPersistentData();
      if (this.herdId == null && data.hasUUID(HERD_KEY)) {
         this.herdId = data.getUUID(HERD_KEY);
      }

      if (HerdService.settling(this.deer, now)) {
         this.nextHerdCheck = now + 40L; // [herds] wait until the animal knows its group
         return;
      }

      // [herds] a group member lives in its leader's home range
      UUID groupRange = HerdService.rangeFor(this.deer);
      if (groupRange != null && !groupRange.equals(this.herdId) && store.get(groupRange) != null) {
         this.herdId = groupRange;
         data.putUUID(HERD_KEY, groupRange);
         this.range = null;
         this.plan = null;
         if (this.task != T_FOLLOW) {
            this.task = T_NONE;
         }
      }

      if (this.herdId != null) {
         this.range = store.get(this.herdId);
      }

      boolean grouped = HerdService.grouped(this.deer); // [herds]
      if (this.range == null && grouped && groupRange == null && !HerdService.keepsRange(this.deer)) {
         return; // [herds] a follower waits for its leader's range instead of surveying one of its own
      }

      if (this.range == null) {
         // join a nearby herd mate's range, or found a new one here
         HomeRange join = null;
         float radius = RoutineAccess.herdRadius(this.deer);
         if (radius > 0.0F && !grouped) { // [herds] grouped animals keep their group's range, not whoever stands near
            double best = Double.MAX_VALUE;

            for (Whitetail other : level.getEntitiesOfClass(
               Whitetail.class, this.deer.getBoundingBox().inflate(Math.max(16.0F, radius)), e -> e != this.deer && e.species() == this.deer.species() && !e.downed()
            )) {
               HomeRange r = other.routine().range;
               if (r == null && other.getPersistentData().hasUUID(HERD_KEY)) {
                  r = store.get(other.getPersistentData().getUUID(HERD_KEY));
               }

               double d = other.distanceToSqr(this.deer);
               if (r != null && d < best) {
                  best = d;
                  join = r;
               }
            }
         }

         this.range = join != null ? join : store.create(this.deer.species(), this.deer.blockPosition(), now);
         this.herdId = this.range.id;
         data.putUUID(HERD_KEY, this.herdId);
         this.plan = null;
         this.task = T_NONE;
      }

      this.range.lastSeen = now;
      if (!this.deer.traits().buck()) {
         this.range.hasDoes = true;
      }

      HerdService.leaderRange(this.deer, this.range.id); // [herds]
   }

   /** Leave the herd and found a fresh range here (debug command). */
   public void resetRange(ServerLevel level) {
      this.deer.getPersistentData().remove(HERD_KEY);
      this.herdId = null;
      this.range = null;
      this.plan = null;
      this.task = T_NONE;
      this.nextHerdCheck = 0L;
   }

   private void survey(ServerLevel level, long now) {
      HomeRange r = this.range;
      if (r.search == null && (r.status == 0 || r.status == 3 && now >= r.retryAt)) {
         r.status = 1;
         r.search = new AnchorSearch(r, r.origin, AnchorSearch.radiusFor(r.species), true, true, true, false, this.deer.getRandom());
      } else if (r.search == null && r.status == 2 && r.anchors[HomeRange.WATER] == null && now >= r.retryAt && r.retryAt != 0L) {
         r.retryAt = now + 24000L;
         r.search = new AnchorSearch(r, r.origin, AnchorSearch.radiusFor(r.species), false, false, true, false, this.deer.getRandom());
      }

      if (r.search != null) {
         if (r.search.step(level)) {
            RoutineStore.of(level).setDirty();
            this.plan = null;
            this.nextPlanAt = now;
            this.refreshPressure(level, now);
         }
      }
   }

   // ------------------------------------------------------------------ schedule

   private boolean rutBuck() {
      if (this.deer.traits().buck() && this.deer.traits().ageMonths() >= 30) {
         Rut.Phase p = this.deer.rut();
         return p == Rut.Phase.SEEKING || p == Rut.Phase.PEAK;
      } else {
         return false;
      }
   }

   private void replan(ServerLevel level, long now) {
      float shift = RoutineConfig.pressure() ? PressureStore.level01(Math.max(this.unitsFeed, this.unitsHere * 0.8F)) * (float)RoutineConfig.nightShift() : 0.0F;
      long dayTime = level.getDayTime();
      boolean watered = this.wateredDay == Schedule.routineDay(dayTime);
      Schedule.Plan p = Schedule.plan(this.deer.species(), dayTime, shift, this.rutBuck(), watered, this.range.anchors[HomeRange.WATER] != null);
      if (this.lastActivity == Schedule.FEED && p.activity() != Schedule.FEED) {
         this.maybeRelocate(level, now);
      }

      this.lastActivity = p.activity();
      this.plan = p;
      this.at = this.nearestAnchor(1.0);
   }

   /** Sustained pressure on the bedding or feeding area: move the whole home range to quieter ground. */
   private void maybeRelocate(ServerLevel level, long now) {
      HomeRange r = this.range;
      double limit = RoutineConfig.relocateAt();
      if (RoutineConfig.pressure() && r.search == null && now - r.relocatedAt > 48000L && Math.max(this.unitsFeed, this.unitsBed) >= limit) {
         PressureStore store = PressureStore.of(level);
         int radius = AnchorSearch.radiusFor(r.species);
         BlockPos from = r.anchors[HomeRange.BED];
         BlockPos best = null;
         float bestP = Float.MAX_VALUE;
         RandomSource random = this.deer.getRandom();

         for (int i = 0; i < 8; i++) {
            double a = (i + random.nextDouble() * 0.4) / 8.0 * Math.PI * 2.0;
            BlockPos c = from.offset((int)Math.round(Math.cos(a) * radius * 1.3), 0, (int)Math.round(Math.sin(a) * radius * 1.3));
            float sum = 0.0F;
            int cx = c.getX() >> PressureStore.CELL_SHIFT;
            int cz = c.getZ() >> PressureStore.CELL_SHIFT;

            for (int dx = -1; dx <= 1; dx++) {
               for (int dz = -1; dz <= 1; dz++) {
                  sum += store.value(now, cx + dx, cz + dz);
               }
            }

            if (Habitat.loaded(level, c.getX(), c.getZ()) && sum < bestP) {
               bestP = sum;
               best = c;
            }
         }

         if (best != null) {
            BlockPos feet = Habitat.surface(level, best.getX(), best.getZ());
            r.search = new AnchorSearch(r, feet == null ? best : feet, radius, true, true, true, true, random);
         }
      }
   }

   int nearestAnchor(double scale) {
      int best = -1;
      double bestD = Double.MAX_VALUE;

      for (int i = 0; i < 3; i++) {
         BlockPos a = this.range.anchors[i];
         if (a != null) {
            double r = anchorRadius(i) * scale;
            double d = this.horizontal(a);
            if (d <= r * r && d < bestD) {
               bestD = d;
               best = i;
            }
         }
      }

      return best;
   }

   static double anchorRadius(int which) {
      return switch (which) {
         case HomeRange.BED -> 7.0;
         case HomeRange.WATER -> 4.0;
         default -> 11.0;
      };
   }

   private double horizontal(BlockPos p) {
      double dx = p.getX() + 0.5 - this.deer.getX();
      double dz = p.getZ() + 0.5 - this.deer.getZ();
      return dx * dx + dz * dz;
   }

   private int stagger() {
      UUID id = this.deer.getUUID();
      return (int)Math.floorMod(id.getLeastSignificantBits() ^ id.getMostSignificantBits(), 6L) * 70 + this.deer.getRandom().nextInt(40);
   }

   /** The routine owns bedding decisions (the old random-bedding roll is skipped). */
   public boolean ownsBedding() {
      return this.active();
   }

   // ------------------------------------------------------------------ goal interface (RoutineGoal)

   boolean goalCanUse() {
      Whitetail lead = HerdService.followTarget(this.deer); // [herds] a group member goes where its leader goes
      if (lead != null && this.debugGoal == null) {
         return this.followerCanUse(lead);
      }

      this.followerMode = false;
      if (this.debugGoal != null) {
         return this.task == T_TRAVEL && this.calm(); // [herds] a commanded walk
      }

      if (this.active() && this.plan != null && this.calm() && !this.rut.busy() && this.deer.onGround() && !(this.deer.graze(1.0F) > 0.25F)) {
         ServerLevel level = (ServerLevel)this.deer.level();
         long now = level.getGameTime();
         return now >= this.nextTaskAt && this.chooseTask(level, now);
      } else {
         return false;
      }
   }

   boolean goalCanContinue() {
      if (this.followerMode) { // [herds]
         return this.followerCanContinue();
      }

      return this.task != T_NONE && (this.active() || this.debugGoal != null) && this.calm() && !this.rut.busy() && this.travelStillWanted();
   }

   /** A schedule change mid-walk (dusk comes while still heading to bed...) turns the animal toward the new target. */
   private boolean travelStillWanted() {
      if (this.task != T_TRAVEL || this.plan == null || this.debugGoal != null) {
         return true;
      } else if (this.plan.activity() == Schedule.CRUISE) {
         return this.legTo == -2;
      } else {
         int target = this.plan.anchor();
         if (target == HomeRange.WATER && this.range.anchors[HomeRange.WATER] == null) {
            target = HomeRange.FEED;
         }

         return target == this.legTo;
      }
   }

   void goalStop() {
      if (this.task == T_TRAVEL) {
         // interrupted (alarm, call, rut): drop a half-walked recording; the next trip re-joins the trail
         this.recording = false;
         this.crumbs.clear();
      }

      if (this.drinking) {
         RoutineAccess.headDown(this.deer, 0);
         this.drinking = false;
      }

      if (this.task == T_TRAVEL && this.debugGoal != null) {
         this.debugGoal = null; // [herds]
      }

      this.followed = null; // [herds]
      this.task = T_NONE;
      this.deer.getNavigation().stop();
   }

   private boolean chooseTask(ServerLevel level, long now) {
      Schedule.Plan p = this.plan;
      if (p.activity() == Schedule.CRUISE) {
         return this.chooseCruise(level, now);
      } else {
         int target = p.anchor();
         if (target == HomeRange.WATER && this.range.anchors[HomeRange.WATER] == null) {
            target = HomeRange.FEED;
         }

         this.at = this.nearestAnchor(1.0);
         BlockPos goal = this.range.anchors[target];
         if (this.at != target && !Habitat.loaded(level, goal.getX(), goal.getZ())) {
            // the anchor's chunk is not loaded (no player near it): wait rather than path into the void
            this.nextTaskAt = now + 200L;
            return false;
         } else if (this.at != target) {
            if (this.legTo != target) {
               this.legTo = target;
               this.departAt = now + (long)this.stagger();
            }

            if (now < this.departAt) {
               this.nextTaskAt = this.departAt;
               return false;
            } else {
               return this.startTravel(level, now, target);
            }
         } else {
            if (this.legTo == target) {
               this.legTo = -2;
               this.validateAnchor(level, target);
               if (!this.active()) {
                  return false;
               }
            }

            RandomSource random = this.deer.getRandom();
            switch (p.activity()) {
               case Schedule.BED:
                  if (this.stretchSteps > 0 || p.ticksLeft() < 300) {
                     this.stretchSteps = Math.max(0, this.stretchSteps - 1);
                     return this.startStep(level, now, this.range.anchors[HomeRange.BED], 5);
                  }

                  return this.startBedSpot(level, now);
               case Schedule.STRETCH:
                  return this.startStep(level, now, this.range.anchors[HomeRange.BED], 7);
               case Schedule.WATER:
                  return this.startDrink(level, now);
               default:
                  if (p.restOk() && p.ticksLeft() > 900 && random.nextFloat() < 0.16F && Habitat.standable(level, this.deer.blockPosition())) {
                     // night: lie down out in the feeding area for a while
                     int ticks = Math.min(p.ticksLeft(), 900 + random.nextInt(1300));
                     this.deer.routineBed(ticks, random.nextFloat() < 0.25F);
                     this.nextTaskAt = now + 40L;
                     return false;
                  }

                  return this.startStep(level, now, this.range.anchors[HomeRange.FEED], 11);
            }
         }
      }
   }

   private void validateAnchor(ServerLevel level, int which) {
      HomeRange r = this.range;
      BlockPos a = r.anchors[which];
      if (a != null && Habitat.loaded(level, a.getX(), a.getZ())) {
         boolean ok = switch (which) {
            case HomeRange.BED -> Habitat.bedStillGood(level, a);
            case HomeRange.FEED -> Habitat.feedStillGood(level, r.species, a);
            default -> Habitat.waterStillGood(level, a, r.waterBlock);
         };
         if (!ok && r.search == null) {
            r.invalidateAnchor(which);
            if (which != HomeRange.WATER) {
               r.status = 1;
            }

            r.search = new AnchorSearch(
               r, r.origin, AnchorSearch.radiusFor(r.species), which == HomeRange.BED, which == HomeRange.FEED, which == HomeRange.WATER, false, this.deer.getRandom()
            );
            RoutineStore.of(level).setDirty();
         }
      }
   }

   void goalTick() {
      ServerLevel level = (ServerLevel)this.deer.level();
      long now = level.getGameTime();
      switch (this.task) {
         case T_TRAVEL -> this.tickTravel(level, now);
         case T_STEP -> this.tickStep(level, now);
         case T_DRINK -> this.tickDrink(level, now);
         case T_BEDSPOT -> this.tickBedSpot(level, now);
         case T_FOLLOW -> this.tickFollow(level, now); // [herds]
         default -> {
         }
      }
   }

   private double walk() {
      return RoutineAccess.walkSpeed(this.deer);
   }

   private boolean moveTo(BlockPos p, double speed) {
      this.lastMoveAt = this.deer.level().getGameTime();
      return this.deer.getNavigation().moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, speed);
   }

   // ------------------------------------------------------------------ [herds] following the group

   /**
    * A group member with its leader (or mother) at hand: when the leader walks, fall in behind it on its own line;
    * when it has stopped, feed around it, bed near it, take a turn at the water - never far from it.
    */
   private boolean followerCanUse(Whitetail lead) {
      this.followerMode = true;
      if (!this.calm() || this.rut.busy() || !this.deer.onGround() || this.deer.graze(1.0F) > 0.25F) {
         return false;
      }

      ServerLevel level = (ServerLevel)this.deer.level();
      long now = level.getGameTime();
      HerdTuning.Social s = HerdService.social(this.deer);
      boolean bedded = lead.behavior() == Whitetail.BEHAVIOR_BEDDED || lead.behavior() == Whitetail.BEHAVIOR_SLEEPING;
      double leash = bedded ? s.bedSpread() + 3.0 : s.feedSpread();
      if (HerdService.moving(lead) && now >= this.pauseUntil || this.deer.distanceToSqr(lead) > leash * leash) {
         return this.startFollow(level, now, lead);
      } else if (now < this.nextTaskAt) {
         return false;
      } else if (bedded) {
         return this.startBedNear(level, now, lead, s);
      } else {
         BlockPos water = this.range == null ? null : this.range.anchors[HomeRange.WATER];
         if (this.plan != null && this.plan.activity() == Schedule.WATER && water != null && this.wateredDay != Schedule.routineDay(level.getDayTime())
            && lead.distanceToSqr(Vec3.atCenterOf(water)) < 256.0 && this.standFree(level, water)) {
            return this.startDrink(level, now);
         }

         return this.startStep(level, now, lead.blockPosition(), Math.max(4, (int)s.feedSpread()));
      }
   }

   private boolean followerCanContinue() {
      if (this.task == T_NONE || !this.calm() || this.rut.busy()) {
         return false;
      }

      Whitetail lead = HerdService.followTarget(this.deer);
      if (lead == null) {
         return false;
      } else if (this.task == T_FOLLOW) {
         return lead == this.followed;
      } else {
         // the leader set off: drop what it was doing here and go along (a drink is finished first)
         return !HerdService.moving(lead) || this.task == T_DRINK && this.drinking;
      }
   }

   /** Nobody else is drinking at the shore stand. */
   private boolean standFree(ServerLevel level, BlockPos stand) {
      for (Whitetail o : level.getEntitiesOfClass(Whitetail.class, new net.minecraft.world.phys.AABB(stand).inflate(1.6, 1.0, 1.6), e -> e != this.deer)) {
         if (!o.downed()) {
            return false;
         }
      }

      return true;
   }

   private boolean startBedNear(ServerLevel level, long now, Whitetail lead, HerdTuning.Social s) {
      UUID id = this.deer.getUUID();
      double a = HerdTuning.frac(id, HerdTuning.SALT_BED) * Math.PI * 2.0;
      double r = 2.2 + HerdTuning.frac(id, HerdTuning.SALT_BED + 1) * (s.bedSpread() - 2.2);
      BlockPos spot = null;
      for (int i = 0; i < 3 && spot == null; i++) {
         double aa = a + i * 0.9;
         BlockPos feet = Habitat.surface(level, (int)Math.floor(lead.getX() + Math.cos(aa) * r), (int)Math.floor(lead.getZ() + Math.sin(aa) * r));
         if (feet != null && Math.abs(feet.getY() - lead.getBlockY()) <= 2 && Habitat.standable(level, feet)) {
            spot = feet;
         }
      }

      if (spot == null) {
         this.nextTaskAt = now + 60L;
         return false;
      } else {
         this.stepTarget = spot;
         this.task = T_BEDSPOT;
         this.taskStart = now;
         if (this.horizontal(spot) >= 1.7) {
            this.moveTo(spot, this.walk() * 0.65);
         }

         return true;
      }
   }

   private boolean startFollow(ServerLevel level, long now, Whitetail lead) {
      this.followed = lead;
      this.task = T_FOLLOW;
      this.taskStart = now;
      this.followRepath = 0L;
      this.followNav = null;
      this.followLastX = this.deer.getX();
      this.followLastZ = this.deer.getZ();
      this.followMovedAt = now;
      this.pendingGraze = false;
      this.tickFollow(level, now);
      return this.task == T_FOLLOW;
   }

   private void tickFollow(ServerLevel level, long now) {
      Whitetail lead = this.followed;
      PathNavigation nav = this.deer.getNavigation();
      if (lead == null || lead.isRemoved() || lead.downed()) {
         this.task = T_NONE;
         nav.stop();
         return;
      }

      HerdTuning.Social s = HerdService.social(this.deer);
      boolean moving = HerdService.moving(lead);
      double l2 = this.deer.distanceToSqr(lead);
      if (!moving) {
         boolean bedded = lead.behavior() == Whitetail.BEHAVIOR_BEDDED || lead.behavior() == Whitetail.BEHAVIOR_SLEEPING;
         double settle = (bedded ? s.bedSpread() + 1.5 : s.feedSpread()) * 0.8;
         if (l2 <= settle * settle) {
            // the group has stopped and this one has caught up: feed (or bed) around the leader
            this.task = T_NONE;
            nav.stop();
            this.nextTaskAt = now + 10L + this.deer.getRandom().nextInt(50);
            this.pendingGraze = !bedded && this.deer.getRandom().nextFloat() < 0.5F;
            return;
         }
      }

      if (now < this.pauseUntil) {
         // a browse on the way: head down for a moment, then catch up
         nav.stop();
         if (this.pendingGraze && this.deer.startGrazing()) {
            this.pendingGraze = false;
         }

         if (l2 > Math.pow(HerdService.gap(this.deer) + 10.0, 2.0)) {
            this.pauseUntil = 0L;
         }

         return;
      }

      Vec3 slot = HerdService.slot(this.deer, lead, now);
      double dx = slot.x - this.deer.getX();
      double dz = slot.z - this.deer.getZ();
      double d2 = dx * dx + dz * dz;
      double gap = HerdService.gap(this.deer);
      if (moving && l2 < gap * gap * 0.3) {
         // right on the heels of the one ahead: hold back and let it draw away
         nav.stop();
         this.deer.getLookControl().setLookAt(lead, 10.0F, 10.0F);
         this.followMovedAt = now;
         return;
      }

      RandomSource random = this.deer.getRandom();
      if (moving && d2 < 9.0 && random.nextInt(240) == 0) {
         this.pause(now, 30 + random.nextInt(70), true);
         return;
      }

      double speed = this.walk() * 0.92;
      if (d2 > 400.0) {
         speed = this.walk() * 1.55; // well behind: trot to catch up
      } else if (d2 > 64.0) {
         speed = this.walk() * 1.18;
      } else if (d2 < 6.0) {
         speed = this.walk() * 0.8;
      }

      if (nav.isDone() || now >= this.followRepath && (this.followNav == null || this.followNav.distanceToSqr(slot) > 12.0)) {
         this.followRepath = now + 16L; // short paths, re-planned only when the place has moved on (~every 1 s on the move)
         this.followNav = slot;
         this.lastMoveAt = now;
         boolean ok = false;
         if (d2 > 676.0) {
            Vec3 hop = LandRandomPos.getPosTowards(this.deer, 20, 6, slot);
            ok = hop != null && nav.moveTo(hop.x, hop.y, hop.z, speed);
         }

         if (!ok && !nav.moveTo(slot.x, slot.y, slot.z, speed)) {
            // the side-step put the place in a trunk or a bank: walk the leader's own line instead
            nav.moveTo(lead, speed);
         }
      } else {
         nav.setSpeedModifier(speed);
      }

      // stuck (fence, cliff, water): hop round it toward the leader
      double mx = this.deer.getX() - this.followLastX;
      double mz = this.deer.getZ() - this.followLastZ;
      if (mx * mx + mz * mz > 1.0) {
         this.followLastX = this.deer.getX();
         this.followLastZ = this.deer.getZ();
         this.followMovedAt = now;
      } else if (now - this.followMovedAt > 100L && d2 > 16.0) {
         this.followMovedAt = now;
         Vec3 hop = LandRandomPos.getPosTowards(this.deer, 10, 5, lead.position());
         if (hop != null) {
            nav.moveTo(hop.x, hop.y, hop.z, speed);
            this.followRepath = now + 40L;
         }
      }
   }

   /** [herds] Op command / QA: walk straight to a spot (a leader with its group following) as a routine leg. */
   public boolean debugWalkTo(BlockPos goal) {
      if (!(this.deer.level() instanceof ServerLevel level) || !this.calm()) {
         return false;
      }

      long now = level.getGameTime();
      this.debugGoal = goal.immutable();
      this.followerMode = false;
      this.route.clear();
      this.route.add(this.debugGoal);
      this.onTrail = false;
      this.recording = false;
      this.trailIndex = -1;
      this.legTo = -2;
      this.wp = 0;
      this.fails = 0;
      this.wpSince = now;
      this.wpBest = Double.MAX_VALUE;
      this.edgeDone = true;
      this.pauseUntil = 0L;
      this.travelSpeed = this.walk() * 0.88;
      this.task = T_TRAVEL;
      this.taskStart = now;
      this.moveToward(this.debugGoal);
      return true;
   }

   // ------------------------------------------------------------------ travel along game trails

   private boolean startTravel(ServerLevel level, long now, int target) {
      HomeRange r = this.range;
      BlockPos goal = r.anchors[target];
      this.route.clear();
      this.onTrail = false;
      this.recording = false;
      this.crumbs.clear();
      this.trailIndex = -1;
      int from = this.nearestAnchor(1.6);
      if (from >= 0 && from != target) {
         int t = HomeRange.trailFor(from, target);
         long[] trail = r.trails[t];
         if (trail != null) {
            long[] walk = HomeRange.reversed(from, target) ? RoutineTrails.reverse(trail) : trail;
            int idx = RoutineTrails.join(walk, this.deer.getX(), this.deer.getZ(), 24.0);
            if (idx >= 0) {
               for (int i = idx; i < walk.length; i++) {
                  this.route.add(BlockPos.of(walk[i]));
               }

               this.onTrail = true;
               this.trailIndex = t;
            }
         } else {
            // first trip on this leg: walk it along edges / cover lines and record the line we actually take
            this.route.addAll(RoutineTrails.viaPoints(level, r.anchors[from], goal));
            this.recording = true;
            this.legFrom = from;
            this.recordFrom = r.anchors[from];
            this.recordTo = goal;
            this.trailIndex = t;
            this.crumbs.add(this.deer.blockPosition());
            this.lastCrumb = this.deer.blockPosition();
         }
      }

      if (this.route.isEmpty()) {
         // away from the anchors (after a flight, a call, a chase): pick up the nearest trail that leads home
         double best = 40.0;

         for (int other = 0; other < 3; other++) {
            int t = HomeRange.trailFor(other, target);
            if (t >= 0 && r.trails[t] != null) {
               long[] walk = HomeRange.reversed(other, target) ? RoutineTrails.reverse(r.trails[t]) : r.trails[t];
               int idx = RoutineTrails.join(walk, this.deer.getX(), this.deer.getZ(), best);
               if (idx >= 0) {
                  BlockPos p = BlockPos.of(walk[idx]);
                  best = Math.sqrt(this.horizontal(p));
                  this.route.clear();

                  for (int i = idx; i < walk.length; i++) {
                     this.route.add(BlockPos.of(walk[i]));
                  }

                  this.onTrail = true;
                  this.trailIndex = t;
               }
            }
         }
      }

      if (this.route.isEmpty() || !this.route.get(this.route.size() - 1).closerThan(goal, 2.5)) {
         this.route.add(goal);
      }

      this.wp = 0;
      this.fails = 0;
      this.wpSince = now;
      this.wpBest = Double.MAX_VALUE;
      this.edgeDone = false;
      this.pauseUntil = 0L;
      this.pendingGraze = false;
      this.travelSpeed = this.walk() * (this.rutBuck() ? 1.0 : 0.88);
      this.task = T_TRAVEL;
      this.taskStart = now;
      this.moveToward(this.route.get(0));
      return true;
   }

   /** Navigate toward a waypoint; far waypoints (off-trail travel) are approached in hops the path finder can plan. */
   private void moveToward(BlockPos w) {
      double d2 = this.horizontal(w);
      if (d2 > 676.0) {
         Vec3 hop = LandRandomPos.getPosTowards(this.deer, 20, 6, Vec3.atBottomCenterOf(w));
         if (hop != null) {
            this.lastMoveAt = this.deer.level().getGameTime();
            if (this.deer.getNavigation().moveTo(hop.x, hop.y, hop.z, this.travelSpeed)) {
               return;
            }
         }
      }

      if (!this.moveTo(w, this.travelSpeed)) {
         // unreachable waypoint: skip it (three in a row and the trail is given up)
         this.fails++;
         if (this.wp < this.route.size() - 1) {
            this.wp++;
            this.wpSince = this.deer.level().getGameTime();
            this.wpBest = Double.MAX_VALUE;
         }
      }
   }

   private void tickTravel(ServerLevel level, long now) {
      PathNavigation nav = this.deer.getNavigation();
      if (now < this.pauseUntil) {
         nav.stop();
         if (this.pendingGraze && this.deer.startGrazing()) {
            this.pendingGraze = false;
         } else if (!this.pendingGraze && this.deer.getRandom().nextInt(24) == 0) {
            // standing still, testing the air and looking the ground over
            RandomSource random = this.deer.getRandom();
            double a = random.nextDouble() * Math.PI * 2.0;
            this.deer.getLookControl().setLookAt(this.deer.getX() + Math.cos(a) * 10.0, this.deer.getEyeY() + random.nextDouble() - 0.3, this.deer.getZ() + Math.sin(a) * 10.0, 12.0F, 12.0F);
         }
      } else if (HerdService.leaderShouldWait(this.deer)) {
         // [herds] the lead doe stops and looks back until the stragglers close up
         this.pause(now, 40 + this.deer.getRandom().nextInt(60), false);
      } else {
         this.pendingGraze = false;
         if (this.recording) {
            BlockPos here = this.deer.blockPosition();
            if (RoutineTrails.horizontal(here, this.lastCrumb) >= 9.0 && this.crumbs.size() < 400) {
               this.crumbs.add(here);
               this.lastCrumb = here;
            }
         }

         BlockPos w = this.route.get(this.wp);
         double d2 = this.horizontal(w);
         boolean last = this.wp == this.route.size() - 1;
         double reach = last ? 2.0 : 2.8;
         if (d2 < reach * reach && Math.abs(w.getY() - this.deer.getY()) < 3.0) {
            this.advance(level, now);
         } else {
            if (d2 < this.wpBest - 1.0) {
               this.wpBest = d2;
               this.wpSince = now;
            }

            if (nav.isDone() || now - this.lastMoveAt > 80L) {
               this.moveToward(w);
            }

            if (now - this.wpSince > 120L) {
               this.fails++;
               this.wpSince = now;
               this.wpBest = Double.MAX_VALUE;
               if (!last) {
                  this.wp++;
               }
            }

            if (this.fails >= 3) {
               // the trail has become impassable (blocked, flooded, built over): forget it so the next trip re-wears one
               if (this.onTrail && this.trailIndex >= 0) {
                  this.range.trails[this.trailIndex] = null;
                  RoutineStore.of(level).setDirty();
               }

               this.task = T_NONE;
               this.nextTaskAt = now + 100L;
               nav.stop();
            }
         }
      }
   }

   private void advance(ServerLevel level, long now) {
      RandomSource random = this.deer.getRandom();
      if (this.onTrail && random.nextFloat() < 0.55F) {
         // [integration] since the tracking merge this places nothing (TrailService.leave only drops blood): TrackPrints
         // already presses a print group on every stride, so worn trails read in the tracking system without it
         TrackClue.leave(this.deer, false);
      }

      this.wp++;
      this.wpSince = now;
      this.wpBest = Double.MAX_VALUE;
      this.fails = 0;
      if (this.wp >= this.route.size()) {
         this.arrive(level, now);
      } else {
         BlockPos feed = this.range == null ? null : this.range.anchors[HomeRange.FEED];
         if (this.legTo == HomeRange.FEED && !this.edgeDone && feed != null && this.horizontal(feed) < 484.0) {
            // hang up at the edge of the field and look it over before stepping out
            this.edgeDone = true;
            this.pause(now, 80 + random.nextInt(140), false);
         } else if (this.wp < this.route.size() - 1 && random.nextFloat() < 0.1F) {
            this.pause(now, 30 + random.nextInt(80), random.nextFloat() < 0.35F);
         } else {
            this.moveToward(this.route.get(this.wp));
         }
      }
   }

   private void pause(long now, int ticks, boolean graze) {
      this.pauseUntil = now + ticks;
      this.deer.getNavigation().stop();
      this.pendingGraze = graze && this.deer.getRandom().nextFloat() < 0.6F;
      if (!this.pendingGraze) {
         RoutineAccess.cue(this.deer, this.deer.getRandom().nextBoolean() ? 3 : 4);
      }
   }

   private void arrive(ServerLevel level, long now) {
      HomeRange r = this.range;
      if (this.recording
         && this.trailIndex >= 0
         && this.legTo >= 0
         && this.legFrom >= 0
         && this.recordFrom.equals(r.anchors[this.legFrom])
         && this.recordTo.equals(r.anchors[this.legTo])) {
         this.crumbs.add(r.anchors[this.legTo]);
         long[] trail = RoutineTrails.simplify(this.crumbs);
         double straight = Math.sqrt(RoutineTrails.horizontal(r.anchors[this.legFrom], r.anchors[this.legTo]));
         if (trail != null && trail.length >= 2) {
            double len = RoutineTrails.length(trail);
            if (len >= 8.0 && len <= straight * 3.2 + 12.0) {
               r.trails[this.trailIndex] = HomeRange.reversed(this.legFrom, this.legTo) ? RoutineTrails.reverse(trail) : trail;
               RoutineStore.of(level).setDirty();
            }
         }
      }

      this.recording = false;
      this.crumbs.clear();
      this.debugGoal = null; // [herds]
      this.task = T_NONE;
      this.at = this.range == null ? -1 : this.nearestAnchor(1.0);
      this.nextTaskAt = now + 20L + this.deer.getRandom().nextInt(40);
      this.deer.getNavigation().stop();
   }

   // ------------------------------------------------------------------ cruising (rut bucks, midday)

   private boolean chooseCruise(ServerLevel level, long now) {
      RandomSource random = this.deer.getRandom();
      if (this.cruiseTarget == null || now >= this.cruiseUntil || this.horizontal(this.cruiseTarget) < 81.0) {
         if (this.cruiseTarget != null && this.horizontal(this.cruiseTarget) < 81.0 && now < this.cruiseUntil) {
            // at a doe area: poke around, scent-checking
            return this.startStep(level, now, this.cruiseTarget, 10);
         }

         BlockPos pick = null;
         List<HomeRange> ranges = RoutineStore.of(level).near(this.deer.species(), this.deer.blockPosition(), 160.0);
         ranges.removeIf(x -> x == this.range || !x.hasDoes);
         if (!ranges.isEmpty()) {
            HomeRange other = ranges.get(random.nextInt(ranges.size()));
            pick = other.anchors[random.nextFloat() < 0.6F ? HomeRange.BED : HomeRange.FEED];
         }

         if (pick == null) {
            int a = random.nextInt(3);
            pick = this.range.anchors[a] != null ? this.range.anchors[a] : this.range.anchors[HomeRange.FEED];
         }

         this.cruiseTarget = pick;
         this.cruiseUntil = now + 1400L + random.nextInt(1400);
      }

      this.route.clear();
      this.route.add(this.cruiseTarget);
      this.onTrail = false;
      this.recording = false;
      this.trailIndex = -1;
      this.legTo = -2;
      this.wp = 0;
      this.fails = 0;
      this.wpSince = now;
      this.wpBest = Double.MAX_VALUE;
      this.edgeDone = true;
      this.travelSpeed = this.walk() * 1.05;
      this.task = T_TRAVEL;
      this.taskStart = now;
      this.moveToward(this.cruiseTarget);
      return true;
   }

   // ------------------------------------------------------------------ local activity at an anchor

   private boolean startStep(ServerLevel level, long now, BlockPos around, int radius) {
      RandomSource random = this.deer.getRandom();
      BlockPos pick = null;

      for (int i = 0; i < 4 && pick == null; i++) {
         double a = random.nextDouble() * Math.PI * 2.0;
         double d = 2.0 + random.nextDouble() * (radius - 2);
         int x = around.getX() + (int)Math.round(Math.cos(a) * d);
         int z = around.getZ() + (int)Math.round(Math.sin(a) * d);
         BlockPos feet = Habitat.surface(level, x, z);
         if (feet != null && Math.abs(feet.getY() - this.deer.getBlockY()) <= 4 && Habitat.standable(level, feet) && this.horizontal(feet) > 4.0) {
            pick = feet;
         }
      }

      if (pick == null) {
         this.nextTaskAt = now + 60L;
         return false;
      } else {
         this.stepTarget = pick;
         this.task = T_STEP;
         this.taskStart = now;
         this.moveTo(pick, this.walk() * 0.7);
         return true;
      }
   }

   private void tickStep(ServerLevel level, long now) {
      PathNavigation nav = this.deer.getNavigation();
      double d2 = this.horizontal(this.stepTarget);
      if (d2 < 2.25 || nav.isDone() || now - this.taskStart > 160L) {
         nav.stop();
         this.task = T_NONE;
         RandomSource random = this.deer.getRandom();
         int activity = this.plan == null ? Schedule.FEED : this.plan.activity();
         int idle = switch (activity) {
            case Schedule.BED -> 60 + random.nextInt(100);
            case Schedule.STRETCH -> 100 + random.nextInt(200);
            default -> 60 + random.nextInt(180);
         };
         this.nextTaskAt = now + idle;
         if (random.nextFloat() < (activity == Schedule.FEED || activity == Schedule.CRUISE ? 0.7F : 0.35F)) {
            this.pendingGraze = true;
         }
      }
   }

   /** After a feeding step ends the goal releases control; the deer drops its head to graze on the next ticks. */
   void idleTick() {
      if (this.pendingGraze && this.task == T_NONE && this.calm()) {
         if (this.deer.startGrazing()) {
            this.pendingGraze = false;
         } else if (this.deer.getRandom().nextInt(40) == 0) {
            this.pendingGraze = false;
         }
      }
   }

   private BlockPos bedSpot(ServerLevel level) {
      BlockPos anchor = this.range.anchors[HomeRange.BED];
      if (this.bedSpot == null || !anchor.equals(this.bedSpotFor)) {
         this.bedSpotFor = anchor;
         this.bedSpot = anchor;
         UUID id = this.deer.getUUID();
         double a = Math.floorMod(id.getMostSignificantBits(), 360L) * Math.PI / 180.0;
         double d = 1.5 + Math.floorMod(id.getLeastSignificantBits(), 20L) * 0.1;
         BlockPos feet = Habitat.surface(level, anchor.getX() + (int)Math.round(Math.cos(a) * d), anchor.getZ() + (int)Math.round(Math.sin(a) * d));
         if (feet != null && Math.abs(feet.getY() - anchor.getY()) <= 2 && Habitat.standable(level, feet)) {
            this.bedSpot = feet;
         }
      }

      return this.bedSpot;
   }

   private boolean startBedSpot(ServerLevel level, long now) {
      BlockPos spot = this.bedSpot(level);
      this.stepTarget = spot;
      this.task = T_BEDSPOT;
      this.taskStart = now;
      if (this.horizontal(spot) >= 1.7) {
         this.moveTo(spot, this.walk() * 0.65);
      }

      return true;
   }

   private void tickBedSpot(ServerLevel level, long now) {
      PathNavigation nav = this.deer.getNavigation();
      double d2 = this.horizontal(this.stepTarget);
      if (d2 < 1.7 || now - this.taskStart > 200L || nav.isDone() && d2 < 9.0) {
         nav.stop();
         if (this.deer.getDeltaMovement().horizontalDistanceSqr() > 0.0025) {
            return;
         }

         RandomSource random = this.deer.getRandom();
         int left = this.plan == null ? 2000 : this.plan.ticksLeft();
         int ticks = Math.max(400, Math.min(left + random.nextInt(200), 1600 + random.nextInt(2000)));
         this.task = T_NONE;
         this.nextTaskAt = now + 40L;
         this.deer.routineBed(ticks, random.nextFloat() < 0.3F);
      } else if (nav.isDone()) {
         this.moveTo(this.stepTarget, this.walk() * 0.65);
      }
   }

   private boolean startDrink(ServerLevel level, long now) {
      BlockPos stand = this.range.anchors[HomeRange.WATER];
      if (stand == null || this.range.waterBlock == null) {
         this.wateredDay = Schedule.routineDay(level.getDayTime());
         return false;
      } else {
         this.stepTarget = stand;
         this.task = T_DRINK;
         this.taskStart = now;
         this.drinking = false;
         this.moveTo(stand, this.walk() * 0.7);
         return true;
      }
   }

   private void tickDrink(ServerLevel level, long now) {
      PathNavigation nav = this.deer.getNavigation();
      BlockPos water = this.range.waterBlock;
      if (water == null) {
         this.task = T_NONE;
      } else {
         Vec3 standV = Vec3.atBottomCenterOf(this.stepTarget);
         Vec3 toWater = Vec3.atCenterOf(water).subtract(standV).multiply(1.0, 0.0, 1.0);
         Vec3 aim = Vec3.atCenterOf(water).add(0.0, 0.38, 0.0).subtract(toWater.normalize().scale(0.2));
         if (!this.drinking) {
            Vec3 stand = standV.add(toWater.normalize().scale(0.28));
            double dx = stand.x - this.deer.getX();
            double dz = stand.z - this.deer.getZ();
            double d2 = dx * dx + dz * dz;
            if (d2 < 0.2) {
               nav.stop();
               this.drinking = true;
               this.drinkUntil = now + 100L + this.deer.getRandom().nextInt(90);
            } else if (d2 < 3.2) {
               nav.stop();
               this.deer.getMoveControl().setWantedPosition(stand.x, stand.y, stand.z, this.walk() * 0.6);
            } else if (nav.isDone()) {
               this.moveTo(this.stepTarget, this.walk() * 0.7);
            }

            this.deer.getLookControl().setLookAt(aim.x, aim.y, aim.z, 10.0F, 10.0F);
            if (now - this.taskStart > 400L) {
               // cannot get onto the bank: skip today, and after repeated failures find another spot
               this.task = T_NONE;
               this.wateredDay = Schedule.routineDay(level.getDayTime());
               if (++this.drinkFails >= 3 && this.range.search == null) {
                  this.drinkFails = 0;
                  this.range.invalidateAnchor(HomeRange.WATER);
                  this.range.retryAt = now + 1200L;
                  RoutineStore.of(level).setDirty();
               }
            }
         } else {
            float want = (float)(Math.atan2(aim.z - this.deer.getZ(), aim.x - this.deer.getX()) * 180.0F / (float)Math.PI) - 90.0F;
            float yaw = Mth.approachDegrees(this.deer.getYRot(), want, 5.0F);
            this.deer.setYRot(yaw);
            this.deer.yBodyRot = yaw;
            this.deer.setDeltaMovement(this.deer.getDeltaMovement().multiply(0.4, 1.0, 0.4));
            boolean facing = Math.abs(Mth.wrapDegrees(want - yaw)) < 25.0F;
            this.deer.getLookControl().setLookAt(aim.x, aim.y, aim.z, 10.0F, 10.0F);
            if (facing) {
               RoutineAccess.headDown(this.deer, (int)Math.max(2L, this.drinkUntil - now));
            }

            if (this.deer.graze(1.0F) > 0.6F && ++this.drinkFx % 12 == 0) {
               level.sendParticles(ParticleTypes.SPLASH, aim.x, aim.y + 0.05, aim.z, 4, 0.18, 0.02, 0.18, 0.05);
               level.playSound(null, this.deer.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.NEUTRAL, 0.3F, 0.5F + this.deer.getRandom().nextFloat() * 0.15F);
            }

            if (now >= this.drinkUntil) {
               RoutineAccess.quench(this.deer);
               RoutineAccess.headDown(this.deer, 0);
               this.drinking = false;
               this.drinkFails = 0;
               this.wateredDay = Schedule.routineDay(level.getDayTime());
               this.task = T_NONE;
               this.nextTaskAt = now + 40L;
               this.nextPlanAt = now;
            }
         }
      }
   }

   // ------------------------------------------------------------------ scent

   /** Called after perception: returns the (possibly raised) alertness. See {@link RoutineHooks#afterPerceive}. */
   float afterPerceive(Player source, float scent, float other, float before, float after) {
      if (source != null && scent > 0.0F && scent > other) {
         float raw = scent / 0.16F * this.deer.species().behavior.spook();
         if (raw >= (float)RoutineConfig.scentBust()) {
            this.scentBust = true;
            return 1.0F;
         }

         if (before < 0.62F && after >= 0.62F) {
            this.scentBust = true;
         }
      }

      return after;
   }

   /** Debug view for the command. */
   public List<String> describe(ServerLevel level) {
      List<String> out = new ArrayList<>();
      if (this.range == null) {
         out.add("no herd yet");
         return out;
      } else {
         HomeRange r = this.range;
         String status = switch (r.status) {
            case 1 -> "surveying";
            case 2 -> "ready";
            case 3 -> "no usable cover here (retry later)";
            default -> "new";
         };
         out.add("herd " + r.id.toString().substring(0, 8) + " · " + status + (r.relocations > 0 ? " · moved " + r.relocations + "x" : ""));

         for (int i = 0; i < 3; i++) {
            BlockPos a = r.anchors[i];
            out.add(HomeRange.NAMES[i] + ": " + (a == null ? "-" : a.getX() + " " + a.getY() + " " + a.getZ() + " (" + Math.round(Math.sqrt(this.horizontal(a))) + "m)"));
         }

         out.add("trails: bed-feed " + trailInfo(r.trails[0]) + " · bed-water " + trailInfo(r.trails[1]) + " · water-feed " + trailInfo(r.trails[2]));
         out.add("plan: " + (this.plan == null ? "-" : this.plan.describe()) + " · task: " + this.taskName() + (this.recording ? " (wearing a new trail)" : ""));
         out.add(String.format("pressure here %.1f · feed %.1f · bed %.1f · wariness x%.2f", this.unitsHere, this.unitsFeed, this.unitsBed, this.wariness()));
         out.add("rut: " + this.deer.rut().title + " · " + RutEngine.describe(this, level));
         return out;
      }
   }

   private static String trailInfo(long[] t) {
      return t == null ? "none" : t.length + " pts/" + Math.round(RoutineTrails.length(t)) + "m";
   }

   public List<BlockPos> debugTrailPoints() {
      List<BlockPos> pts = new ArrayList<>();
      if (this.range != null) {
         for (long[] t : this.range.trails) {
            if (t != null) {
               for (long l : t) {
                  pts.add(BlockPos.of(l));
               }
            }
         }
      }

      return pts;
   }

   GameSpecies species() {
      return this.deer.species();
   }
}
