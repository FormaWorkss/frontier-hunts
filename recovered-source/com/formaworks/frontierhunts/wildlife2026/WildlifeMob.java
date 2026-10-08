package com.formaworks.frontierhunts.wildlife2026;

import com.mojang.logging.LogUtils;
import java.util.EnumSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.Goal.Flag;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class WildlifeMob extends PathfinderMob {
   public static final int IDLE = 0;
   public static final int FEED = 1;
   public static final int REST = 2;
   public static final int ALERT = 3;
   public static final int CURIOUS = 4;
   public static final int FLEE = 5;
   public static final int WARN = 6;
   /** [ecology] predation states, driven by ecology.Hunt: crouched stalk, chase, pounce/leap, feeding on a kill, howl, walking with the hunt */
   public static final int STALK = 7, CHASE = 8, POUNCE = 9, TEAR = 10, HOWL = 11, TRAVEL = 12;
   private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(
      WildlifeMob.class, EntityDataSerializers.INT
   );
   /** [wingshot] game-bird flight phase (wingshot.Flight NONE..DRUM_B), drives the wing animation on every client */
   private static final EntityDataAccessor<Byte> FLIGHT = SynchedEntityData.defineId(WildlifeMob.class, EntityDataSerializers.BYTE);
   /** [wingshot] per-bird flight / fall state, owned by wingshot.BirdFlight (server) */
   public Object wingshot;
   public final WildlifeSpecies species;
   private int calmTicks;
   private int threatTicks;
   private int attackCooldown;
   private int flightTicks;
   private Vec3 threat;
   /** [perf] Distance-based thinking rate (AiThrottle). */
   public final com.formaworks.frontierhunts.perf.AiThrottle.State perfAi = new com.formaworks.frontierhunts.perf.AiThrottle.State();

   public WildlifeMob(
      EntityType<? extends WildlifeMob> type,
      Level level,
      WildlifeSpecies species
   ) {
      super(type, level);
      this.species = species;
      this.xpReward = species.bird ? 1 : 3;
   }

   protected void defineSynchedData(Builder b) {
      super.defineSynchedData(b);
      b.define(STATE, 0);
      b.define(FLIGHT, (byte)0); // [wingshot]
   }

   protected void registerGoals() {
      this.goalSelector.addGoal(0, new FloatGoal(this));
      this.goalSelector.addGoal(1, new WildlifeMob.ResponseGoal());
      this.goalSelector.addGoal(2, new com.formaworks.frontierhunts.ecology.HuntGoal(this)); // [ecology] predators hunt prey
      this.goalSelector.addGoal(3, new com.formaworks.frontierhunts.hunting.routine.PressureAvoidGoal(this)); // [routines] prey leave pressured ground
      this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.7) {
         public boolean canUse() {
            return WildlifeMob.this.behavior() == 0 && super.canUse();
         }
      });
      this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 12.0F));
      this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
   }

   public int behavior() {
      return (Integer)this.entityData.get(STATE);
   }

   /** [wingshot] synced flight phase (0 = not flying) */
   public byte flightPhase() {
      return this.entityData.get(FLIGHT);
   }

   /** [wingshot] server: set by wingshot.BirdFlight */
   public void flightPhase(byte phase) {
      if (this.entityData.get(FLIGHT) != phase) {
         this.entityData.set(FLIGHT, phase);
      }
   }

   /** [wingshot] spooked by something at {@code from} (flock mates, a grouse running on after landing): flee state without a new take-off */
   public void wingshotThreat(Vec3 from, int ticks) {
      if (from == null || !Double.isFinite(from.x + from.y + from.z)) {
         return;
      }
      this.threat = from;
      this.threatTicks = Math.max(this.threatTicks, ticks);
      this.calmTicks = 0;
      if (this.behavior() != WARN) {
         this.state(FLEE);
      }
   }

   // ---------------------------------------------------------------- [ecology] predation hooks

   /** [ecology] in one of the hunt states (STALK..TRAVEL) */
   public boolean hunting() {
      int b = this.behavior();
      return b >= STALK && b <= TRAVEL;
   }

   /** [ecology] set by ecology.Hunt: a hunt state, or IDLE when the hunt is over */
   public void ecoState(int s) {
      this.calmTicks = 0;
      this.state(Math.clamp(s, IDLE, TRAVEL));
   }

   /** [ecology] a predator is after this animal (or its herd): run, flush, or (defend) turn and charge it */
   public void ecoScare(Vec3 from, LivingEntity predator, boolean defend) {
      if (this.level().isClientSide || from == null || !Double.isFinite(from.lengthSqr())) {
         return;
      }
      this.threat = from;
      this.threatTicks = Math.max(this.threatTicks, 100);
      this.calmTicks = 0;
      if (defend && predator != null && predator.isAlive() && !this.species.bird) {
         this.setTarget(predator);
         this.state(WARN);
         return;
      }
      if (this.behavior() != WARN) {
         this.state(FLEE);
      }
      if (this.species.bird && com.formaworks.frontierhunts.wingshot.BirdFlight.startle(this, from)) { // [wingshot] real flush / take-off
      } else if (this.species.bird && this.onGround() && this.flightTicks == 0) {
         this.flightTicks = 35;
         this.setDeltaMovement(this.getDeltaMovement().add(0.0, 0.32, 0.0));
      }
   }

   private void state(int s) {
      int old = this.behavior();
      if (old == REST && s != REST) {
         // stand up before walking off: the legs unfold while the body stays put
         this.riseTicks = riseTime(s);
      }
      this.entityData.set(STATE, s);
   }

   private static int riseTime(int s) {
      return s == FLEE || s == WARN ? 6 : 14;
   }

   private int riseTicks;
   /** client only: last synced behaviour, to start the same stand-up window the server runs (riseTicks isn't synced) */
   private int clientState;

   /** True while bedded or still getting up: no sliding along the ground, no body spin. */
   public boolean legsFolded() {
      return this.behavior() == REST || this.riseTicks > 0;
   }

   @Override
   public void tick() {
      super.tick();
      int b = this.behavior();
      if (this.level().isClientSide) {
         if (this.clientState == REST && b != REST) {
            this.riseTicks = riseTime(b);
         }
         this.clientState = b;
      }
      if (this.riseTicks > 0) {
         this.riseTicks--;
      }
      if (this.legsFolded() && this.onGround()) {
         // lock the body where it lay down; the head may still look around
         this.yBodyRot = this.yBodyRotO;
         float d = net.minecraft.util.Mth.wrapDegrees(this.yHeadRot - this.yBodyRot);
         this.yHeadRot = this.yBodyRot + net.minecraft.util.Mth.clamp(d, -50.0F, 50.0F);
         if (!this.level().isClientSide) {
            // only while bedded: a flee/defend path requested during the stand-up must survive it (travel() already
            // holds the body still), otherwise ResponseGoal only re-paths up to 10 ticks after the animal is up
            if (b == REST) {
               this.navigation.stop();
            }
            Vec3 v = this.getDeltaMovement();
            this.setDeltaMovement(0.0, v.y, 0.0);
         }
      }
   }

   @Override
   public void travel(Vec3 input) {
      if (this.species.bird && com.formaworks.frontierhunts.wingshot.BirdFlight.travel(this)) { // [wingshot] flown / falling bird
         return;
      }
      if (this.legsFolded() && this.onGround()) {
         Vec3 v = this.getDeltaMovement();
         this.setDeltaMovement(0.0, v.y, 0.0);
         super.travel(Vec3.ZERO);
         return;
      }
      super.travel(input);
   }

   @Override
   public boolean isPushable() {
      return !this.legsFolded() && super.isPushable();
   }

   public void aiStep() {
      if (this.species.bird) {
         com.formaworks.frontierhunts.wingshot.BirdFlight.think(this); // [wingshot] flight pilot, drumming, ambient flights
      }
      super.aiStep();
      if (!this.level().isClientSide) {
         if (this.attackCooldown > 0) {
            this.attackCooldown--;
         }

         if (this.threatTicks > 0) {
            this.threatTicks--;
         }

         if (this.tickCount % 10 == this.getId() % 10 && com.formaworks.frontierhunts.perf.AiThrottle.alarmScan(this)) { // [perf]
            Player p = this.level().getNearestPlayer(this, 24.0 * com.formaworks.frontierhunts.hunting.routine.RoutineHooks.preyWariness(this)); // [routines]
            if (p != null && !p.isCreative() && !p.isSpectator()) {
               double d = this.distanceToSqr(p);
               boolean visible = this.hasLineOfSight(p);
               double alarm = (p.isSprinting() ? 18.0 : (p.isCrouching() ? 5.0 : 10.0))
                  * com.formaworks.frontierhunts.hunting.routine.RoutineHooks.preyWariness(this); // [routines] warier where hunted
               alarm *= com.formaworks.frontierhunts.journal.HunterSkills.wildlifeAlarm(p); // [journal] Soft Steps / Ghost
               if (d < alarm * alarm && (visible || p.isSprinting() && d < 100.0)) {
                  this.threat = p.position();
                  this.threatTicks = Math.max(this.threatTicks, 100);
                  this.calmTicks = 0;
                  if (this.behavior() != 6) {
                     if (this.behavior() != 5) {
                        com.formaworks.frontierhunts.hunting.routine.RoutineHooks.preySpooked(this, p.position()); // [routines] pressure
                     }

                     this.state(5);
                  }

                  if (this.species.bird && com.formaworks.frontierhunts.wingshot.BirdFlight.startle(this, this.threat)) { // [wingshot]
                  } else if (this.species.bird && this.onGround() && this.flightTicks == 0) {
                     this.flightTicks = 35;
                     this.setDeltaMovement(this.getDeltaMovement().add(0.0, 0.32, 0.0));
                  }
               } else if (visible && this.threatTicks == 0 && d < 400.0 && !this.hunting()) { // [ecology] a hunting predator ignores a watcher
                  this.state(d < 196.0 ? 3 : 4);
                  this.calmTicks = 30;
                  this.getLookControl().setLookAt(p, 25.0F, 20.0F);
               }
            }
            if (this.threatTicks == 0 && this.tickCount % 20 == this.getId() % 20 && !this.hunting()) { // [clothing] winded downwind (clothing.Noses: carbon base layer, spray, sweat)
               Player w = com.formaworks.frontierhunts.clothing.Noses.winded(this);
               if (w != null) {
                  this.threat = w.position();
                  this.threatTicks = 100;
                  this.calmTicks = 0;
                  if (this.behavior() != 6) {
                     if (this.behavior() != 5) {
                        com.formaworks.frontierhunts.hunting.routine.RoutineHooks.preySpooked(this, w.position());
                     }
                     this.state(5);
                  }
               }
            }
         }

         if (this.flightTicks > 0) {
            this.flightTicks--;
            if (this.threat != null && !this.horizontalCollision) {
               Vec3 away = this.position().subtract(this.threat).multiply(1.0, 0.0, 1.0).normalize().scale(0.36);
               this.setDeltaMovement(away.x, Math.max(this.getDeltaMovement().y, this.flightTicks > 12 ? 0.1 : -0.08), away.z);
            }

            this.fallDistance = 0.0F;
         } else if (this.species.bird && !this.onGround() && this.getDeltaMovement().y < 0.0 && !com.formaworks.frontierhunts.wingshot.BirdFlight.active(this)) { // [wingshot]
            this.setDeltaMovement(this.getDeltaMovement().multiply(1.0, 0.65, 1.0));
            this.fallDistance = 0.0F;
         }

         if (this.threatTicks == 0) {
            if (this.behavior() == 5 || this.behavior() == 6) {
               this.state(3);
               this.calmTicks = 50;
               this.setTarget(null);
            }

            if (this.calmTicks > 0) {
               this.calmTicks--;
               if (this.calmTicks == 0) {
                  this.state(0);
               }
            } else if (this.tickCount % 60 == this.getId() % 60 && this.navigation.isDone() && this.random.nextInt(4) == 0 && !this.hunting()) { // [ecology]
               boolean storm = com.formaworks.frontierhunts.weather.SeasonalWeather.beddingWeather(this.level(), this.blockPosition()); // [weather] bed down in storms
               this.state(storm || this.random.nextInt(5) == 0 ? 2 : 1);
               this.calmTicks = (storm ? 400 : 80) + this.random.nextInt(100);
            }
         }
      }
   }

   public boolean hurt(DamageSource source, float amount) {
      Object wsHit = this.species.bird ? com.formaworks.frontierhunts.wingshot.BirdFlight.beforeHurt(this, source) : null; // [wingshot]
      boolean hit = super.hurt(source, amount);
      if (wsHit != null) {
         com.formaworks.frontierhunts.wingshot.BirdFlight.afterHurt(this, source, amount, hit, wsHit); // [wingshot] feather burst, wing shot, fall
      }
      if (hit && !this.level().isClientSide && this.behavior() == REST) {
         // any injury (fire, cactus, stray arrow) gets a bedded animal back on its feet
         this.state(ALERT);
         this.calmTicks = 60;
      }
      if (hit && !this.level().isClientSide && source.getEntity() instanceof LivingEntity attacker
         && Double.isFinite(attacker.getX() + attacker.getY() + attacker.getZ())) { // [bugs] never flee from a NaN point
         this.threat = attacker.position();
         this.threatTicks = 180;
         this.calmTicks = 0;
         boolean defend = this.species.defensive && (!(attacker instanceof Player p) || !p.isCreative() && !p.isSpectator());
         if (defend && com.formaworks.frontierhunts.ecology.EcologyHooks.yields(this, attacker)) {
            defend = false; // [ecology] a predator hurt by its prey (charging bison, boar) backs off
         }
         if (defend) {
            this.setTarget(attacker);
            this.state(6);
         } else {
            this.state(5);
         }
      }

      return hit;
   }

   // ---------------------------------------------------------------- [wingshot] flight hitbox, shot birds fall before they are taken

   @Override
   protected net.minecraft.world.entity.EntityDimensions getDefaultDimensions(net.minecraft.world.entity.Pose pose) {
      net.minecraft.world.entity.EntityDimensions d = super.getDefaultDimensions(pose);
      return pose == net.minecraft.world.entity.Pose.FALL_FLYING && this.species != null && this.species.bird
         ? com.formaworks.frontierhunts.wingshot.BirdFlight.flyingSize(this.species, d) : d;
   }

   @Override
   protected void dropAllDeathLoot(net.minecraft.server.level.ServerLevel level, DamageSource source) {
      if (this.species.bird && com.formaworks.frontierhunts.wingshot.BirdFlight.deferLoot(this, source)) {
         return; // dropped where the bird comes down
      }
      super.dropAllDeathLoot(level, source);
   }

   /** [wingshot] the loot of a bird shot in the air, dropped once it is down */
   public void wingshotLoot(net.minecraft.server.level.ServerLevel level, DamageSource source) {
      super.dropAllDeathLoot(level, source);
   }

   @Override
   protected void tickDeath() {
      if (this.species.bird && com.formaworks.frontierhunts.wingshot.BirdFlight.holdDeath(this)) {
         return; // still tumbling down, or lying where it fell
      }
      super.tickDeath();
   }

   // ---------------------------------------------------------------- voices (vanilla sounds, pitched per species)

   @Override
   protected SoundEvent getAmbientSound() {
      return switch (this.species) {
         case WOLF, COYOTE -> this.behavior() == WARN ? SoundEvents.WOLF_GROWL : SoundEvents.WOLF_AMBIENT;
         case GRIZZLY, BLACK_BEAR, POLAR_BEAR -> this.behavior() == WARN ? SoundEvents.POLAR_BEAR_WARNING : SoundEvents.POLAR_BEAR_AMBIENT;
         case BISON -> SoundEvents.COW_AMBIENT;
         case BOAR -> SoundEvents.HOGLIN_AMBIENT;
         case PRONGHORN -> SoundEvents.GOAT_AMBIENT;
         case GROUSE -> SoundEvents.CHICKEN_AMBIENT;
         case DUCK -> SoundEvents.PARROT_AMBIENT;
         case LION -> this.random.nextInt(3) == 0 ? SoundEvents.RAVAGER_ROAR : null;
         default -> this.behavior() == WARN ? SoundEvents.CAT_HISS : null;
      };
   }

   @Override
   protected SoundEvent getHurtSound(DamageSource source) {
      return switch (this.species) {
         case WOLF, COYOTE -> SoundEvents.WOLF_HURT;
         case GRIZZLY, BLACK_BEAR, POLAR_BEAR -> SoundEvents.POLAR_BEAR_HURT;
         case BISON -> SoundEvents.COW_HURT;
         case BOAR -> SoundEvents.HOGLIN_HURT;
         case PRONGHORN -> SoundEvents.GOAT_HURT;
         case GROUSE, DUCK -> SoundEvents.CHICKEN_HURT;
         default -> SoundEvents.OCELOT_HURT;
      };
   }

   @Override
   protected SoundEvent getDeathSound() {
      return switch (this.species) {
         case WOLF, COYOTE -> SoundEvents.WOLF_DEATH;
         case GRIZZLY, BLACK_BEAR, POLAR_BEAR -> SoundEvents.POLAR_BEAR_DEATH;
         case BISON -> SoundEvents.COW_DEATH;
         case BOAR -> SoundEvents.HOGLIN_DEATH;
         case PRONGHORN -> SoundEvents.GOAT_DEATH;
         case GROUSE, DUCK -> SoundEvents.CHICKEN_DEATH;
         default -> SoundEvents.OCELOT_DEATH;
      };
   }

   @Override
   public float getVoicePitch() {
      float base = switch (this.species) {
         case COYOTE -> 1.3F;
         case WOLF -> 0.85F;
         case GRIZZLY -> 0.8F;
         case BLACK_BEAR -> 0.95F;
         case POLAR_BEAR -> 0.85F;
         case BISON -> 0.55F;
         case BOAR -> 1.15F;
         case PRONGHORN -> 1.25F;
         case GROUSE -> 0.75F;
         case DUCK -> 0.7F;
         case LION -> 1.35F;
         case COUGAR, PANTHER -> 0.55F;
         case CHEETAH -> 0.7F;
         default -> 1.0F;
      };
      return base * (0.92F + this.random.nextFloat() * 0.16F);
   }

   // [calls] real recorded voices (RealVoices) for bears, bison, cougars, pronghorn, ducks, grouse, wolves and coyotes;
   // species/moods it does not cover keep the vanilla voice below
   @Override
   public void playAmbientSound() {
      RealVoices.Voice rv = RealVoices.pick(this.species, this.behavior() == WARN, this.random);
      if (rv == null) {
         super.playAmbientSound();
      } else if (!rv.silent()) {
         this.playSound(rv.sound(), Math.min(1.0F, rv.volume()), // [1.1.6] range from sounds.json
             (this.species == WildlifeSpecies.BLACK_BEAR ? 1.08F : 1.0F) * (0.94F + this.random.nextFloat() * 0.12F));
      }
   }

   @Override
   public int getAmbientSoundInterval() {
      return this.species == WildlifeSpecies.LION ? 600 : this.species.bird ? 160 : 240;
   }

   public void addAdditionalSaveData(CompoundTag n) {
      super.addAdditionalSaveData(n);
      n.putInt("WildlifeThreatTicks", this.threatTicks);
      if (this.threat != null) {
         n.putDouble("WildlifeThreatX", this.threat.x);
         n.putDouble("WildlifeThreatY", this.threat.y);
         n.putDouble("WildlifeThreatZ", this.threat.z);
      }
   }

   public void readAdditionalSaveData(CompoundTag n) {
      super.readAdditionalSaveData(n);
      this.threatTicks = Math.min(200, Math.max(0, n.getInt("WildlifeThreatTicks")));
      if (this.threatTicks > 0) {
         this.threat = new Vec3(n.getDouble("WildlifeThreatX"), n.getDouble("WildlifeThreatY"), n.getDouble("WildlifeThreatZ"));
         if (!Double.isFinite(this.threat.x + this.threat.y + this.threat.z)) { // [bugs] corrupt save: forget the threat
            this.threat = null;
            this.threatTicks = 0;
            return;
         }
         this.state(5);
      }
   }

   private final class ResponseGoal extends Goal {
      ResponseGoal() {
         this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
      }

      public boolean canUse() {
         return WildlifeMob.this.behavior() != 0 && !WildlifeMob.this.hunting(); // [ecology] hunt states belong to HuntGoal
      }

      public boolean canContinueToUse() {
         return this.canUse();
      }

      public boolean requiresUpdateEveryTick() {
         return true;
      }

      public void start() {
         WildlifeMob.this.navigation.stop();
      }

      public void tick() {
         if (WildlifeMob.this.behavior() == 6 && WildlifeMob.this.getTarget() != null && WildlifeMob.this.getTarget().isAlive()) {
            LivingEntity target = WildlifeMob.this.getTarget();
            WildlifeMob.this.getLookControl().setLookAt(target, 30.0F, 25.0F);
            if (WildlifeMob.this.tickCount % 10 == 0) {
               WildlifeMob.this.navigation.moveTo(target, 1.2);
            }

            if (WildlifeMob.this.distanceToSqr(target) < Math.pow((double)(WildlifeMob.this.getBbWidth() + target.getBbWidth()) + 0.4, 2.0)
               && WildlifeMob.this.attackCooldown == 0) {
               WildlifeMob.this.doHurtTarget(target);
               WildlifeMob.this.attackCooldown = 25;
            }
         } else {
            if (WildlifeMob.this.behavior() == 5 && WildlifeMob.this.threat != null && WildlifeMob.this.tickCount % 10 == 0) {
               Vec3 pos = DefaultRandomPos.getPosAway(WildlifeMob.this, 12, 4, WildlifeMob.this.threat);
               boolean path = pos != null && WildlifeMob.this.navigation.moveTo(pos.x, pos.y, pos.z, 1.6);
               if (Boolean.getBoolean("frontier.wildlifeReview")) {
                  LogUtils.getLogger()
                     .info(
                        "WILDLIFE_PATH tick={} pos={} destination={} path={} ground={} speed={}",
                        new Object[]{
                           WildlifeMob.this.tickCount, WildlifeMob.this.position(), pos, path, WildlifeMob.this.onGround(), WildlifeMob.this.getSpeed()
                        }
                     );
               }
            }
         }
      }

      public void stop() {
         WildlifeMob.this.navigation.stop();
      }
   }
}
