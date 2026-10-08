package com.formaworks.frontierhunts.hunts;

import com.formaworks.frontierhunts.Wilderness;
import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import com.formaworks.frontierhunts.wildlife2026.WildlifeSpecies;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

/**
 * [hunts] How wildlife answers the hunter's lures (added to every wildlife mob at priority 2 by {@link HuntEvents}):
 * <ul>
 * <li><b>Predator call</b> (the mod's Predator Locator Call): coyotes and wolves come in at a trot, cats slowly, bears
 * sometimes; most come at dusk, night and dawn. They swing to the downwind side of the call and hang up 8-18 blocks out
 * to scent-check it, stand and look, then drift off - so the hunter must be still, hidden and watching downwind.</li>
 * <li><b>Bait</b> (the mod's Bait item, used on the ground): bears, hogs, panthers and lions find a bait pile within
 * 64 blocks, mostly from dusk to dawn, walk in cautiously and feed on it.</li>
 * <li><b>Duck call and decoys</b>: mallards that hear the call (or see a decoy spread) lift off, swing in over the
 * spread at height, drop and land on the water among the decoys, and loaf there. A duck that is spooked off the
 * water flares (HuntEvents) - the classic passing shot.</li>
 * </ul>
 * The animal's own senses stay in charge: anything that alarms it (sight, sound, scent) ends the goal at once and the
 * normal flee/defend response takes over. Cheap gates first; at most one world query every 1-2 seconds per animal.
 */
public final class LureGoal extends Goal {
   static final int CALLER = 1, BAITER = 2, DUCK = 4;

   private enum Mode {
      CALL,
      BAIT,
      FLY,
      LOAF
   }

   private final WildlifeMob mob;
   private int role = -2;
   private int nextCheck;
   private long nextBait;
   private long nextDecoy;
   private long answered;
   private Mode mode;
   private Vec3 target;
   private Vec3 lookAt;
   private int ticks;
   private int limit;
   private int hold;
   private int repath;
   private double speed;
   private UUID caller;
   private Vec3 baitPos;

   public LureGoal(WildlifeMob mob) {
      this.mob = mob;
      this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
   }

   static int role(WildlifeSpecies s) {
      if (s == null) {
         return 0;
      }
      return switch (s) {
         case COYOTE, WOLF, COUGAR, CHEETAH -> CALLER;
         case BLACK_BEAR, GRIZZLY, PANTHER, LION -> CALLER | BAITER;
         case BOAR -> BAITER;
         case DUCK -> DUCK;
         default -> 0;
      };
   }

   /** How far this species hears a predator call, and how likely it answers by day / after dark. */
   private static double hearing(WildlifeSpecies s) {
      return switch (s) {
         case COYOTE, WOLF -> 112.0;
         case COUGAR, PANTHER, LION, CHEETAH -> 80.0;
         default -> 72.0;
      };
   }

   private static float answerChance(WildlifeSpecies s, boolean dark) {
      return switch (s) {
         case COYOTE, WOLF -> dark ? 0.85F : 0.45F;
         case COUGAR, PANTHER, LION -> dark ? 0.6F : 0.25F;
         case CHEETAH -> dark ? 0.1F : 0.4F; // hunts by day
         default -> 0.35F; // bears come to a distress call at any hour
      };
   }

   private static boolean dark(ServerLevel level) {
      int t = (int)Math.floorMod(level.getDayTime(), 24000L);
      return t >= 11500 || t < 1000;
   }

   private boolean calm() {
      int b = this.mob.behavior();
      return (b == WildlifeMob.IDLE || b == WildlifeMob.FEED || b == WildlifeMob.REST) && !this.mob.hunting() && !this.mob.isPassenger()
         && !this.mob.isLeashed() && this.mob.getTarget() == null && this.mob.isAlive();
   }

   @Override
   public boolean canUse() {
      if (!(this.mob.level() instanceof ServerLevel level) || this.mob.tickCount < this.nextCheck) {
         return false;
      }
      RandomSource rnd = this.mob.getRandom();
      this.nextCheck = this.mob.tickCount + 20 + rnd.nextInt(20);
      if (this.role == -2) {
         this.role = role(this.mob.species);
      }
      if (this.role == 0 || !HuntsConfig.lures() || !this.calm()) {
         return false;
      }
      long now = level.getGameTime();
      String dim = level.dimension().location().toString();
      Vec3 at = this.mob.position();
      // ---- calls
      if ((this.role & (CALLER | DUCK)) != 0 && Lures.any(now)) {
         Lures.Kind kind = (this.role & DUCK) != 0 ? Lures.Kind.DUCK : Lures.Kind.PREDATOR;
         Lures.Call c = Lures.heard(kind, dim, at, now, this.answered);
         if (c != null) {
            this.answered = c.id();
            if (kind == Lures.Kind.PREDATOR) {
               double r = Math.min(c.radius(), hearing(this.mob.species));
               float chance = answerChance(this.mob.species, dark(level));
               CompoundTag prev = this.mob.getPersistentData().getCompound(HuntContext.CALLED);
               if (prev.contains("t") && now - prev.getLong("t") < 24000L && now >= prev.getLong("t")) {
                  chance *= 0.35F; // call-shy: it came in once today and found nothing
               }
               if (c.pos().distanceToSqr(at) <= r * r && rnd.nextFloat() < chance) {
                  return this.startCall(level, c, now);
               }
            } else {
               List<BlockPos> decoys = HuntContent.liveDecoys(level, c.pos().x, c.pos().y, c.pos().z, 32.0, 12);
               if (rnd.nextFloat() < (decoys.isEmpty() ? 0.45F : 0.85F)) {
                  return this.startFlight(level, decoys, c.pos(), c.caller(), now);
               }
            }
         }
      }
      // ---- bait piles
      if ((this.role & BAITER) != 0 && now >= this.nextBait) {
         this.nextBait = now + 200 + rnd.nextInt(200);
         HuntStore store = HuntStore.get(level.getServer());
         if (store.anyBait()) {
            HuntStore.Bait best = null;
            double bd = Double.MAX_VALUE;
            for (HuntStore.Bait b : store.baitsNear(dim, at.x, at.z, 64.0, now)) {
               double d = at.distanceToSqr(Vec3.atBottomCenterOf(b.pos));
               if (d < bd) {
                  bd = d;
                  best = b;
               }
            }
            if (best != null) {
               Vec3 bp = Vec3.atBottomCenterOf(best.pos);
               CompoundTag t = this.mob.getPersistentData().getCompound(HuntContext.BAITED);
               boolean fed = t.contains("p") && t.getLong("p") == best.pos.asLong() && now - t.getLong("t") < 6000L && now >= t.getLong("t");
               if (fed) {
                  // still at the pile: keep feeding a while
                  if (bd < 9.0 && now - t.getLong("t") < 1600L && this.mob.behavior() == WildlifeMob.IDLE) {
                     this.mob.ecoState(WildlifeMob.FEED);
                  }
               } else if (rnd.nextFloat() < (dark(level) ? 0.45F : 0.12F)) {
                  this.mode = Mode.BAIT;
                  this.baitPos = bp;
                  this.target = bp;
                  this.lookAt = bp;
                  this.caller = best.owner;
                  this.speed = 0.75;
                  this.limit = 2400;
                  return true;
               }
            }
         }
      }
      // ---- a decoy spread seen from the air or the water
      if ((this.role & DUCK) != 0 && now >= this.nextDecoy) {
         this.nextDecoy = now + 300 + rnd.nextInt(300);
         HuntStore store = HuntStore.get(level.getServer());
         if (store.anyDecoys(dim)) {
            List<BlockPos> decoys = HuntContent.liveDecoys(level, at.x, at.y, at.z, 40.0, 12);
            if (!decoys.isEmpty() && rnd.nextFloat() < 0.25F) {
               return this.startFlight(level, decoys, null, null, now);
            }
         }
      }
      return false;
   }

   // ---------------------------------------------------------------------------------------------- predator call

   private boolean startCall(ServerLevel level, Lures.Call c, long now) {
      Vec3 at = this.mob.position();
      Vec3 from = new Vec3(at.x - c.pos().x, 0.0, at.z - c.pos().z);
      from = from.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : from.normalize();
      Wilderness.Wind w = Wilderness.wind(level.getSeed(), now, level.isRaining(), level.isThundering());
      Vec3 down = w.speed() < 0.3 ? from : new Vec3(w.east(), 0.0, w.south()).normalize();
      // swing to the downwind side of the call to scent-check it
      Vec3 dir = from.scale(0.4).add(down.scale(0.6));
      dir = dir.lengthSqr() < 1.0E-4 ? from : dir.normalize();
      double hang = switch (this.mob.species) {
         case COYOTE, WOLF -> 8.0 + this.mob.getRandom().nextDouble() * 4.0;
         case COUGAR, PANTHER, LION, CHEETAH -> 13.0 + this.mob.getRandom().nextDouble() * 5.0;
         default -> 10.0 + this.mob.getRandom().nextDouble() * 4.0;
      };
      this.target = c.pos().add(dir.scale(hang));
      this.lookAt = c.pos();
      this.caller = c.caller();
      this.speed = switch (this.mob.species) {
         case COYOTE, WOLF -> 1.2;
         case COUGAR, PANTHER, LION, CHEETAH -> 0.8;
         default -> 0.9;
      };
      this.mode = Mode.CALL;
      this.limit = 1800;
      CompoundTag t = new CompoundTag();
      t.putUUID("by", c.caller());
      t.putLong("t", now);
      t.putString("k", "predator");
      this.mob.getPersistentData().put(HuntContext.CALLED, t);
      return true;
   }

   // ---------------------------------------------------------------------------------------------- ducks

   private boolean startFlight(ServerLevel level, List<BlockPos> decoys, Vec3 callAt, UUID caller, long now) {
      RandomSource rnd = this.mob.getRandom();
      Vec3 center;
      if (!decoys.isEmpty()) {
         double x = 0, y = 0, z = 0;
         for (BlockPos p : decoys) {
            x += p.getX() + 0.5;
            y += p.getY();
            z += p.getZ() + 0.5;
         }
         center = new Vec3(x / decoys.size(), y / decoys.size(), z / decoys.size());
      } else if (callAt != null) {
         center = callAt;
      } else {
         return false;
      }
      Vec3 land = null;
      for (int k = 0; k < 10 && land == null; k++) {
         double a = rnd.nextDouble() * Math.PI * 2.0;
         double r = decoys.isEmpty() ? 12.0 + rnd.nextDouble() * 10.0 : 2.5 + rnd.nextDouble() * 5.0;
         land = water(level, BlockPos.containing(center.x + Math.cos(a) * r, center.y, center.z + Math.sin(a) * r));
      }
      if (land == null) {
         land = water(level, BlockPos.containing(center));
      }
      if (land == null) {
         if (decoys.isEmpty()) {
            return false; // a call with no water near it: they won't sit on dry ground
         }
         land = center;
      }
      if (land.distanceToSqr(this.mob.position()) < 9.0) {
         return false; // already there
      }
      this.target = land;
      this.lookAt = land;
      this.caller = caller;
      this.mode = Mode.FLY;
      this.limit = 900;
      this.hold = 0;
      if (caller != null) {
         CompoundTag t = new CompoundTag();
         t.putUUID("by", caller);
         t.putLong("t", now);
         t.putString("k", "duck");
         this.mob.getPersistentData().put(HuntContext.CALLED, t);
      }
      return true;
   }

   /** Top of open water at (x, ~y, z): a water block with air above, searching a few blocks up and down. */
   private static Vec3 water(ServerLevel level, BlockPos around) {
      if (!level.isLoaded(around)) {
         return null;
      }
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      for (int dy = 3; dy >= -4; dy--) {
         m.set(around.getX(), around.getY() + dy, around.getZ());
         if (level.getFluidState(m).is(Fluids.WATER)) {
            BlockState above = level.getBlockState(m.above());
            if (above.isAir()) {
               return new Vec3(m.getX() + 0.5, m.getY() + 0.9, m.getZ() + 0.5);
            }
         }
      }
      return null;
   }

   // ---------------------------------------------------------------------------------------------- running

   @Override
   public void start() {
      this.ticks = 0;
      this.repath = 0;
      this.hold = 0;
      this.mob.getNavigation().stop();
      if (this.mode == Mode.FLY && com.formaworks.frontierhunts.wingshot.BirdFlight.lure(this.mob, this.target, true)) {
         this.wingshot = true; // [wingshot] flown in by the flight pilot: circles the spread, lands into the wind
         this.limit = 2400;
         return;
      }
      this.wingshot = false;
      if (this.mode == Mode.FLY && (this.mob.onGround() || this.mob.isInWater())) {
         this.mob.setDeltaMovement(this.mob.getDeltaMovement().add(0.0, 0.42, 0.0));
      }
   }

   /** [wingshot] this flight is flown by wingshot.BirdFlight */
   private boolean wingshot;

   @Override
   public boolean canContinueToUse() {
      if (this.mode == null || this.ticks > this.limit || !this.mob.isAlive() || this.mob.hunting() || this.mob.getTarget() != null) {
         return false;
      }
      int b = this.mob.behavior();
      if (b == WildlifeMob.FLEE || b == WildlifeMob.WARN || b == WildlifeMob.ALERT) {
         return false;
      }
      return this.mode != Mode.LOAF || this.hold > 0;
   }

   @Override
   public boolean requiresUpdateEveryTick() {
      return true;
   }

   @Override
   public void tick() {
      this.ticks++;
      switch (this.mode) {
         case CALL -> this.tickWalk(true);
         case BAIT -> this.tickWalk(false);
         case FLY -> this.tickFly();
         case LOAF -> this.tickLoaf();
      }
   }

   private void tickWalk(boolean call) {
      double d2 = this.mob.position().distanceToSqr(this.target.x, this.mob.getY(), this.target.z);
      if (this.hold > 0) {
         this.hold--;
         this.mob.getNavigation().stop();
         if (this.hold % 50 == 0) {
            // stands and looks, now and then glancing off to the side
            double a = (this.mob.getRandom().nextDouble() - 0.5) * 1.4;
            Vec3 l = this.lookAt.subtract(this.mob.position());
            double c = Math.cos(a), s = Math.sin(a);
            this.mob.getLookControl().setLookAt(this.mob.getX() + l.x * c - l.z * s, this.mob.getEyeY(), this.mob.getZ() + l.x * s + l.z * c, 20.0F, 20.0F);
         }
         if (this.hold == 0) {
            this.mode = null; // done: drift off
         }
         return;
      }
      if (d2 < (call ? 4.0 : 5.0)) {
         this.mob.getNavigation().stop();
         this.mob.getLookControl().setLookAt(this.lookAt.x, this.lookAt.y + 0.5, this.lookAt.z, 30.0F, 30.0F);
         if (call) {
            this.hold = 200 + this.mob.getRandom().nextInt(240);
         } else {
            this.arriveAtBait();
            this.mode = null;
         }
         return;
      }
      if (--this.repath <= 0) {
         this.repath = 40;
         boolean ok = this.mob.getNavigation().moveTo(this.target.x, this.target.y, this.target.z, this.speed);
         if (!ok && call) {
            ok = this.mob.getNavigation().moveTo(this.lookAt.x, this.lookAt.y, this.lookAt.z, this.speed);
         }
         if (!ok && this.ticks > 80) {
            this.mode = null; // no way there
         }
      }
      if (call && this.ticks % 60 == 30 && this.mob.getRandom().nextInt(3) == 0) {
         // pause to test the wind and look toward the sound
         this.mob.getNavigation().stop();
         this.repath = 20;
         this.mob.getLookControl().setLookAt(this.lookAt.x, this.lookAt.y + 0.5, this.lookAt.z, 30.0F, 30.0F);
      }
   }

   private void arriveAtBait() {
      if (!(this.mob.level() instanceof ServerLevel level) || this.baitPos == null) {
         return;
      }
      long now = level.getGameTime();
      HuntStore store = HuntStore.get(level.getServer());
      BlockPos bp = BlockPos.containing(this.baitPos);
      for (HuntStore.Bait b : store.baitsNear(level.dimension().location().toString(), this.baitPos.x, this.baitPos.z, 1.5, now)) {
         if (b.pos.equals(bp) || b.pos.distSqr(bp) < 2.0) {
            b.left--;
            store.setDirty();
            CompoundTag t = new CompoundTag();
            t.putUUID("by", b.owner);
            t.putLong("t", now);
            t.putLong("p", b.pos.asLong());
            this.mob.getPersistentData().put(HuntContext.BAITED, t);
            break;
         }
      }
      this.mob.ecoState(WildlifeMob.FEED);
   }

   private void tickFly() {
      if (this.wingshot) { // [wingshot] wait for the flight pilot to put the duck down, then loaf
         if (!com.formaworks.frontierhunts.wingshot.BirdFlight.flying(this.mob)) {
            this.mode = Mode.LOAF;
            this.hold = 400 + this.mob.getRandom().nextInt(600);
            this.target = this.mob.position();
         }
         return;
      }
      this.mob.getNavigation().stop();
      Vec3 pos = this.mob.position();
      double dx = this.target.x - pos.x, dz = this.target.z - pos.z;
      double hd = Math.sqrt(dx * dx + dz * dz);
      if (hd < 1.4 && pos.y - this.target.y < 1.6) {
         // settled on the water among the decoys
         this.mode = Mode.LOAF;
         this.hold = 400 + this.mob.getRandom().nextInt(600);
         this.mob.setDeltaMovement(this.mob.getDeltaMovement().multiply(0.3, 0.5, 0.3));
         return;
      }
      // fly in high, set the wings and drop into the spread on the last stretch
      double cruise = this.target.y + Mth.clamp(hd * 0.22, 1.2, 9.0);
      double vy = Mth.clamp((cruise - pos.y) * 0.09, -0.24, 0.24);
      if (this.mob.horizontalCollision) {
         vy = 0.32;
      }
      double sp = Math.min(0.46, 0.1 + hd * 0.035);
      double vx = hd < 1.0E-4 ? 0.0 : dx / hd * sp, vz = hd < 1.0E-4 ? 0.0 : dz / hd * sp;
      Vec3 old = this.mob.getDeltaMovement();
      // ease into the new velocity so the turn and descent look like flight, not teleporting
      this.mob.setDeltaMovement(old.x + (vx - old.x) * 0.35, old.y + (vy - old.y) * 0.5, old.z + (vz - old.z) * 0.35);
      if (hd > 0.5) {
         float yaw = (float)(Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
         float turned = Mth.approachDegrees(this.mob.getYRot(), yaw, 12.0F);
         this.mob.setYRot(turned);
         this.mob.yBodyRot = turned;
         this.mob.yHeadRot = turned;
      }
      this.mob.fallDistance = 0.0F;
   }

   private void tickLoaf() {
      this.hold--;
      if (this.hold % 90 == 0 && this.target != null) {
         double a = this.mob.getRandom().nextDouble() * Math.PI * 2.0;
         double r = 1.0 + this.mob.getRandom().nextDouble() * 3.0;
         this.mob.getNavigation().moveTo(this.target.x + Math.cos(a) * r, this.target.y, this.target.z + Math.sin(a) * r, 0.45);
      }
   }

   @Override
   public void stop() {
      boolean spooked = this.mob.behavior() == WildlifeMob.FLEE;
      if ((this.mode == Mode.LOAF || this.mode == Mode.FLY) && spooked && this.mob.species.bird) {
         HuntEvents.flare(this.mob); // lift off the water and away: the passing shot
      }
      this.mode = null;
      this.hold = 0;
      this.mob.getNavigation().stop();
   }

   @Override
   public boolean isInterruptable() {
      return true;
   }
}
