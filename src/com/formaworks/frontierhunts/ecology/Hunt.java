package com.formaworks.frontierhunts.ecology;

import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.formaworks.frontierhunts.hunting.HuntSounds;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.tracking.KillSign;
import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * [ecology] One hunt (or one scavenging visit): a leader and its pack mates, a prey animal, and the phases a real
 * hunt goes through. Ticked once per server tick by {@link PredationService} while it lasts; every phase has a time
 * limit, so a hunt always ends. All movement goes through the members' own navigation (walk cycles match the ground
 * speed, no sliding); leaps are a single physical impulse.
 *
 * <pre>
 * RALLY     wolves at dusk: the pack gathers and howls (4 s)
 * APPROACH  canids and bears walk up openly, heads low, testing the herd; prey notice, watch, then bolt
 * STALK     cats, cheetahs, and anything after small game: a slow crouched approach; detection depends on distance,
 *           wind, cover and whether the prey is feeding; detected too early = the prey runs and the cat gives up
 * RUN       the chase / rush: outcome rolled at the start (most fail); the pursuer matches the prey's speed and gains
 *           (success) or tires and drops back (failure)
 * STANDOFF  a healthy bull moose or elk turns and stands; the wolves circle, test it and leave
 * STRIKE    the pounce / leap / lunge, then the kill
 * FEED      everyone eats at the carcass, then they leave
 * </pre>
 */
final class Hunt {
   enum Phase {
      RALLY, APPROACH, STALK, RUN, STANDOFF, STRIKE, FEED, DONE
   }

   final Predator kind;
   final ServerLevel level;
   final List<WildlifeMob> members = new ArrayList<>(6);
   final RandomSource random;
   LivingEntity prey;
   Prey info;
   /** forced from the debug command: no hunger, cap or randomness gate; {@code forceSuccess} also wins the roll */
   boolean forced, forceSuccess, forceFail;
   boolean scavenging;
   Phase phase = Phase.APPROACH;
   int phaseTicks;
   int ticks;
   boolean success;
   int noticed;
   double mod = 1.0;
   Vec3 caught;
   WildlifeMob striker;
   /** the body being fed on (Whitetail downed carcass or KillCarcass); null for small game eaten on the spot */
   Entity carcass;
   KillRecord record;
   Vec3 eatAt;
   private final int[] feedLeft = new int[8];
   private final boolean[] seated = new boolean[8];
   private double preySpeed, predSpeed;
   private Vec3 preyLast, predLast;
   private int waterTicks;
   private float circleAngle;

   Hunt(Predator kind, ServerLevel level, WildlifeMob leader) {
      this.kind = kind;
      this.level = level;
      this.members.add(leader);
      this.random = level.random;
   }

   WildlifeMob leader() {
      return this.members.isEmpty() ? null : this.members.get(0);
   }

   boolean done() {
      return this.phase == Phase.DONE;
   }

   int pack() {
      return Math.max(1, this.members.size());
   }

   // ------------------------------------------------------------------ phases

   void begin() {
      if (this.scavenging) {
         this.phase(Phase.FEED);
         return;
      }
      long day = Math.floorMod(this.level.getDayTime(), 24000L);
      boolean dusk = day >= 11800L && day < 15000L;
      if (this.kind == Predator.WOLF && this.members.size() >= 2 && EcologyConfig.howls() && (dusk || this.forced && this.random.nextBoolean())) {
         this.phase(Phase.RALLY);
      } else if (this.info.kind().small() || this.kind.style == Predator.Style.AMBUSH || this.kind.style == Predator.Style.SPRINT) {
         this.phase(Phase.STALK);
      } else {
         this.phase(Phase.APPROACH);
      }
   }

   /** debug: start with the howl whatever the time */
   void forceHowl() {
      if (this.phase == Phase.APPROACH || this.phase == Phase.STALK) {
         this.phase(Phase.RALLY);
      }
   }

   private void phase(Phase p) {
      this.phase = p;
      this.phaseTicks = 0;
      int state = switch (p) {
         case RALLY -> WildlifeMob.HOWL;
         case APPROACH -> this.kind.bear() ? WildlifeMob.TRAVEL : WildlifeMob.STALK;
         case STALK, STANDOFF -> WildlifeMob.STALK;
         case RUN, STRIKE -> WildlifeMob.CHASE;
         case FEED -> WildlifeMob.TRAVEL;
         case DONE -> WildlifeMob.IDLE;
      };
      for (WildlifeMob m : this.members) {
         if (p != Phase.DONE || m.hunting()) {
            m.ecoState(state);
         }
      }
   }

   void tick() {
      this.ticks++;
      this.phaseTicks++;
      this.members.removeIf(m -> m.isRemoved() || !m.isAlive() || m.level() != this.level || !PredationService.mayContinue(m));
      if (this.members.isEmpty() || this.ticks > 12000) {
         this.end(false);
         return;
      }
      if (this.phase != Phase.FEED && (this.prey == null || this.prey.isRemoved() || !this.prey.isAlive() || this.prey.level() != this.level
         || this.prey instanceof Whitetail w && w.downed())) {
         // shot by a hunter, despawned, or killed by someone else
         this.end(false);
         return;
      }
      if (this.ticks % 20 == 0) {
         this.reassert();
      }
      switch (this.phase) {
         case RALLY -> this.rally();
         case APPROACH -> this.approach();
         case STALK -> this.stalk();
         case RUN -> this.run();
         case STANDOFF -> this.standoff();
         case STRIKE -> this.strike();
         case FEED -> this.feed();
         default -> {
         }
      }
   }

   /** keep each member in the phase's state (something else may have reset it) */
   private void reassert() {
      for (int i = 0; i < this.members.size(); i++) {
         WildlifeMob m = this.members.get(i);
         if (!m.hunting()) {
            m.ecoState(this.phase == Phase.RALLY ? WildlifeMob.HOWL
               : this.phase == Phase.RUN || this.phase == Phase.STRIKE ? WildlifeMob.CHASE
               : this.phase == Phase.FEED ? (this.seated[Math.min(i, 7)] ? WildlifeMob.TEAR : WildlifeMob.TRAVEL)
               : this.phase == Phase.APPROACH && this.kind.bear() ? WildlifeMob.TRAVEL : WildlifeMob.STALK);
         }
      }
   }

   private void rally() {
      WildlifeMob lead = this.leader();
      if (this.phaseTicks == 1) {
         for (WildlifeMob m : this.members) {
            m.getNavigation().stop();
         }
         SoundEvent s = this.members.size() >= 3 ? EcologyContent.WOLF_CHORUS.get() : EcologyContent.WOLF_HOWL.get();
         this.level.playSound(null, lead.getX(), lead.getEyeY(), lead.getZ(), s, SoundSource.NEUTRAL, 1.0F, 0.94F + this.random.nextFloat() * 0.12F);
      } else if (this.phaseTicks == 18 && this.members.size() == 2) {
         WildlifeMob m = this.members.get(1);
         this.level.playSound(null, m.getX(), m.getEyeY(), m.getZ(), EcologyContent.WOLF_HOWL.get(), SoundSource.NEUTRAL, 1.0F, 1.08F); // [1.1.6] heard within its 72-block range
      }
      // (muzzles up come from the HOWL pose; the look control is left level so the two don't stack)
      if (this.phaseTicks > 85) {
         this.phase(Phase.APPROACH);
      }
   }

   private void approach() {
      WildlifeMob lead = this.leader();
      double d = Math.sqrt(lead.distanceToSqr(this.prey));
      if (this.phaseTicks % 10 == 1) {
         double speed = this.kind.bear() ? 0.8 : d > 30.0 ? 1.15 : 0.95;
         for (int i = 0; i < this.members.size(); i++) {
            WildlifeMob m = this.members.get(i);
            Vec3 t = this.flank(i, Math.min(8.0, d * 0.4));
            go(m, t, speed * (i == 0 ? 1.0 : 1.04));
         }
         // prey see predators walking up in the open: first they watch, a second sighting and they run
         if (PredationService.notices(this, lead, d, 0.035)) {
            this.noticed++;
            PreyReaction.watch(this.prey, lead);
            if (this.noticed >= 2 || d < this.kind.chaseStart * 0.75) {
               this.phase(this.standsGround() ? Phase.STANDOFF : Phase.RUN);
               return;
            }
         }
      }
      if (d <= this.kind.chaseStart) {
         this.phase(this.standsGround() ? Phase.STANDOFF : Phase.RUN);
      } else if (this.phaseTicks > 700 || d > this.kind.search * 1.8) {
         this.end(false);
      }
   }

   /** a healthy bull moose or elk, or an adult bison among its herd, turns to face wolves instead of running */
   private boolean standsGround() {
      if (this.forceSuccess || this.info.young() || this.info.weak()) {
         return false;
      }
      return switch (this.info.kind()) {
         case MOOSE -> this.random.nextFloat() < 0.7F;
         case ELK -> this.info.male() && this.random.nextFloat() < 0.4F;
         default -> false;
      };
   }

   private void stalk() {
      WildlifeMob lead = this.leader();
      double d = Math.sqrt(lead.distanceToSqr(this.prey));
      boolean small = this.info.kind().small();
      if (this.phaseTicks % 8 == 1) {
         double speed = this.kind == Predator.CHEETAH ? 0.75 : 0.55;
         for (int i = 0; i < this.members.size(); i++) {
            WildlifeMob m = this.members.get(i);
            Vec3 t = i == 0 ? this.prey.position() : this.flank(i, Math.min(6.0, d * 0.5));
            go(m, t, speed);
         }
      }
      if (this.phaseTicks % 10 == 5) {
         double base = this.kind == Predator.CHEETAH ? 0.016 : small ? 0.012 : 0.014;
         if (PredationService.notices(this, lead, d, base)) {
            double spring = small ? 6.0 : this.kind.rushDistance * 1.4;
            if (d <= spring) {
               // seen at the last moment: go now
               this.phase(small ? Phase.STRIKE : Phase.RUN);
               this.prepareStrike(lead);
            } else {
               // busted: the prey runs, the cat lets it go
               PreyReaction.flee(this.level, this.prey, lead, true);
               this.end(false);
            }
            return;
         }
      }
      if (small && d <= 4.5) {
         this.prepareStrike(lead);
         this.phase(Phase.STRIKE);
      } else if (!small && d <= this.kind.rushDistance) {
         this.phase(Phase.RUN);
      } else if (this.phaseTicks > 900 || d > this.kind.search * 1.6) {
         this.end(false);
      }
   }

   private void prepareStrike(WildlifeMob who) {
      this.striker = who;
      this.success = !this.forceFail && (this.forceSuccess || this.random.nextFloat() < this.kind.odds(this.info, this.pack()));
   }

   private void run() {
      WildlifeMob lead = this.leader();
      if (this.phaseTicks == 1) {
         this.success = !this.forceFail && (this.forceSuccess || this.random.nextFloat() < this.kind.odds(this.info, this.pack()));
         this.mod = switch (this.kind.style) {
            case AMBUSH -> 2.4;
            case SPRINT -> 2.9;
            default -> this.kind.bear() ? 1.7 : 1.9;
         };
         this.preyLast = this.prey.position();
         this.predLast = lead.position();
         PreyReaction.flee(this.level, this.prey, lead, false);
         PreyReaction.herd(this.level, this.prey, lead, this.info);
         if (this.kind == Predator.WOLF || this.kind == Predator.COYOTE) {
            this.level.playSound(null, lead.blockPosition(), SoundEvents.WOLF_GROWL, SoundSource.NEUTRAL, 0.8F, this.kind == Predator.COYOTE ? 1.3F : 0.85F);
         }
      }
      if (this.phaseTicks % 4 == 0) {
         this.measure(lead);
      }
      if (this.phaseTicks % 10 == 0) {
         PreyReaction.flee(this.level, this.prey, lead, false);
      }
      int maxRun = this.kind.maxRun;
      if (this.phaseTicks % 5 == 0) {
         // pursuit speed: match the prey and gain (a kill) or fade after a burst (most chases)
         double ratio = this.success ? 1.16 : this.phaseTicks < maxRun * 0.45 ? 1.0 : 0.72;
         double want = Math.max(0.14, this.preySpeed * ratio);
         if (this.predSpeed > 0.01) {
            this.mod *= Math.clamp(want / this.predSpeed, 0.86, 1.18);
         }
         this.mod = Math.clamp(this.mod, 0.8, 4.2);
         for (int i = 0; i < this.members.size(); i++) {
            WildlifeMob m = this.members.get(i);
            double sp = this.mod * (i == 0 ? 1.0 : 0.96 - 0.02 * i);
            if (m.distanceToSqr(this.prey) > 26.0 * 26.0) {
               go(m, this.prey.position(), sp);
            } else {
               m.getNavigation().moveTo(this.prey, sp);
            }
         }
      }
      // contact: the first one close enough strikes
      if (this.success) {
         double reach = this.kind.cat() ? 2.6 : 1.6;
         for (WildlifeMob m : this.members) {
            double r = (m.getBbWidth() + this.prey.getBbWidth()) * 0.5 + reach;
            if (m.distanceToSqr(this.prey) < r * r) {
               this.striker = m;
               this.phase(Phase.STRIKE);
               return;
            }
         }
      }
      double d = Math.sqrt(lead.distanceToSqr(this.prey));
      double giveUp = Math.max(this.kind.chaseStart, Math.max(this.kind.rushDistance, 10.0)) * 2.2;
      this.waterTicks = this.prey.isInWater() ? this.waterTicks + 1 : 0;
      if (this.phaseTicks > (this.success ? maxRun * 1.7 : maxRun) || d > giveUp || this.waterTicks > 40) {
         this.end(false);
      }
   }

   private void measure(WildlifeMob lead) {
      Vec3 p = this.prey.position(), q = lead.position();
      if (this.preyLast != null && this.predLast != null) {
         double ps = Math.sqrt(sq(p.x - this.preyLast.x) + sq(p.z - this.preyLast.z)) / 4.0;
         double qs = Math.sqrt(sq(q.x - this.predLast.x) + sq(q.z - this.predLast.z)) / 4.0;
         if (Double.isFinite(ps) && ps < 2.0) {
            this.preySpeed += (ps - this.preySpeed) * 0.5;
         }
         if (Double.isFinite(qs) && qs < 2.0) {
            this.predSpeed += (qs - this.predSpeed) * 0.5;
         }
      }
      this.preyLast = p;
      this.predLast = q;
   }

   private static double sq(double v) {
      return v * v;
   }

   private void standoff() {
      int limit = 160 + (this.ticks & 63);
      if (this.phaseTicks % 10 == 1) {
         PreyReaction.standGround(this.level, this.prey, this.leader());
         this.circleAngle += 0.35F;
         double r = this.prey.getBbWidth() * 0.5 + 5.0;
         for (int i = 0; i < this.members.size(); i++) {
            float a = this.circleAngle + i * Mth.TWO_PI / this.members.size();
            Vec3 c = this.prey.position();
            go(this.members.get(i), new Vec3(c.x + Mth.cos(a) * r, c.y, c.z + Mth.sin(a) * r), 1.05);
         }
      }
      if (this.phaseTicks > limit) {
         // the big animal won: the pack drifts off
         this.end(false);
      }
   }

   private void strike() {
      WildlifeMob s = this.striker != null && this.members.contains(this.striker) ? this.striker : this.leader();
      if (this.phaseTicks == 1) {
         s.ecoState(WildlifeMob.POUNCE);
         Vec3 dir = this.prey.position().subtract(s.position()).multiply(1.0, 0.0, 1.0);
         double len = dir.length();
         if (len > 1.0E-4 && Double.isFinite(len)) {
            dir = dir.scale(1.0 / len);
            boolean small = this.info.kind().small();
            double h = small ? 0.32 : this.kind.cat() ? 0.62 : this.kind.bear() ? 0.32 : 0.46;
            double v = small ? 0.55 : this.kind.cat() ? 0.36 : this.kind.bear() ? 0.12 : 0.24;
            s.setDeltaMovement(dir.x * Math.min(h, len * 0.35 + 0.1), v, dir.z * Math.min(h, len * 0.35 + 0.1));
            s.hasImpulse = true;
            s.setYRot((float)(Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F);
            s.yBodyRot = s.getYRot();
         }
         s.getNavigation().stop();
         if (this.kind.cat()) {
            this.level.playSound(null, s.blockPosition(), SoundEvents.CAT_HISS, SoundSource.NEUTRAL, 0.9F, 0.55F);
         }
      }
      double r = (s.getBbWidth() + this.prey.getBbWidth()) * 0.5 + 0.9;
      boolean contact = s.distanceToSqr(this.prey) < r * r;
      if (this.phaseTicks >= 6 && (contact || this.phaseTicks >= 12)) {
         if (this.success && (contact || this.phaseTicks >= 12 && s.distanceToSqr(this.prey) < (r + 1.5) * (r + 1.5))) {
            this.kill(s);
         } else {
            PreyReaction.flee(this.level, this.prey, s, true);
            this.end(false);
         }
      }
   }

   private void kill(WildlifeMob s) {
      Vec3 at = this.prey.position();
      this.caught = at;
      this.eatAt = at;
      Entity body = makeKill(this.level, this.kind, this.pack(), this.prey, this.info, s, this.random, false);
      if (body == null && !this.info.kind().small()) {
         this.end(false);
         return;
      }
      this.carcass = body;
      this.record = body instanceof KillCarcass c ? c.record() : body instanceof Whitetail w ? KillSites.record(w) : null;
      if (body != null) {
         KillSiteStore.Site site = KillSiteStore.of(this.level).site(body.getUUID());
         if (site != null) {
            site.busyUntil = this.level.getGameTime() + this.kind.feedTicks + 600L;
         }
      }
      this.phase(Phase.FEED);
   }

   /**
    * The kill itself (also used by the debug command): the prey dies, its body becomes a carcass that carries the
    * {@link KillRecord}, the area's kill count goes up and the site is remembered for scavengers. Small game is
    * taken whole (returns null). {@code signNow}: lay the scuffle/drag sign immediately (a deer's body otherwise first
    * slides to rest and the hunt lays it a moment later).
    */
   static Entity makeKill(ServerLevel level, Predator kind, int pack, LivingEntity prey, Prey info, LivingEntity killer, RandomSource random, boolean signNow) {
      long now = level.getGameTime();
      Vec3 at = prey.position();
      String label = info.label();
      KillSiteStore store = KillSiteStore.of(level);
      if (!info.kind().small()) {
         store.countKill(at, now); // the daily cap protects herds; small game does not count
      }
      boolean antlers = info.kind() == Prey.Kind.BISON;
      if (prey instanceof Whitetail w) {
         antlers = w.traits().buck() && (w.species() != GameSpecies.WHITETAIL || w.traits().totalPoints() > 0) && !w.traits().yearling();
      }
      KillRecord record = new KillRecord(kind.name(), pack, label, info.kind().name(), antlers, now);
      String tuft = KillSites.tuft(kind, pack, label);
      boolean neck = kind.cat() || kind == Predator.COYOTE;
      preyCry(level, prey, info);
      if (info.kind().small()) {
         KillSign.smallKill(level, prey, at, tuft, random);
         ItemStack bits = new ItemStack(prey instanceof Rabbit ? Items.RABBIT_HIDE : Items.FEATHER);
         level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, bits), at.x, at.y + 0.3, at.z, 12, 0.25, 0.2, 0.25, 0.06);
         prey.discard();
         return null;
      }
      Entity body;
      if (prey instanceof Whitetail w) {
         w.die(killer != null ? level.damageSources().mobAttack(killer) : level.damageSources().generic());
         KillSites.record(w, record);
         if (signNow) {
            KillSign.killed(level, w, at, w.position(), w.getYRot(), tuft, neck, random);
         }
         body = w;
      } else if (prey instanceof WildlifeMob m) {
         Vec3 v = m.getDeltaMovement();
         Vec3 to = at.add(Math.clamp(v.x * 3.0, -1.5, 1.5), 0.0, Math.clamp(v.z * 3.0, -1.5, 1.5));
         if (!Double.isFinite(to.lengthSqr()) || !level.noCollision(m, m.getBoundingBox().move(to.subtract(at)))) {
            to = at;
         }
         KillCarcass c = KillCarcass.create(level, m.species, to, m.yBodyRot, record);
         KillSign.killed(level, m, at, to, m.yBodyRot, tuft, neck, random);
         m.discard();
         level.addFreshEntity(c);
         body = c;
      } else {
         return null;
      }
      store.addSite(body.getUUID(), body.position(), now, kind.name(), label);
      dust(level, at);
      return body;
   }

   private static void preyCry(ServerLevel level, LivingEntity prey, Prey info) {
      Vec3 p = prey.position();
      SoundEvent s;
      float pitch = 1.0F;
      if (prey instanceof Whitetail w) {
         s = w.species() == GameSpecies.ELK ? HuntSounds.ELK_MEW.get() : w.species() == GameSpecies.MOOSE ? HuntSounds.MOOSE_GRUNT.get() : HuntSounds.DEER_BLEAT.get();
         pitch = 1.15F;
      } else {
         s = switch (info.kind()) {
            case BISON -> SoundEvents.COW_HURT;
            case BOAR -> SoundEvents.HOGLIN_HURT;
            case PRONGHORN -> SoundEvents.GOAT_SCREAMING_HURT;
            case RABBIT -> SoundEvents.RABBIT_DEATH;
            default -> SoundEvents.CHICKEN_HURT;
         };
         pitch = info.kind() == Prey.Kind.BISON ? 0.6F : info.kind() == Prey.Kind.PRONGHORN ? 1.2F : 0.8F;
      }
      level.playSound(null, p.x, p.y + 1.0, p.z, s, SoundSource.NEUTRAL, 1.4F, pitch);
   }

   private static void dust(ServerLevel level, Vec3 at) {
      BlockPos below = BlockPos.containing(at.x, at.y - 0.2, at.z);
      BlockState g = level.getBlockState(below);
      if (!g.isAir()) {
         level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, g), at.x, at.y + 0.1, at.z, 14, 0.5, 0.05, 0.5, 0.04);
      }
   }

   private void feed() {
      long now = this.level.getGameTime();
      Vec3 body;
      if (this.carcass != null) {
         if (this.carcass.isRemoved() || this.carcass.level() != this.level) {
            this.end(true);
            return;
         }
         body = this.carcass.position();
      } else if (this.eatAt != null) {
         body = this.eatAt;
      } else {
         this.end(true);
         return;
      }
      if (this.phaseTicks == 1) {
         for (int i = 0; i < this.members.size() && i < 8; i++) {
            float spread = this.carcass == null ? 0.0F : 1.0F;
            this.feedLeft[i] = (int)((this.carcass == null ? (i == 0 ? 260 : 0) : this.kind.feedTicks) * (0.8F + 0.4F * this.random.nextFloat() * spread));
            this.seated[i] = false;
         }
      }
      // the deer's body has slid to rest: now the scuffle / drag line and hair can be read between the two
      if (this.phaseTicks == 30 && this.caught != null && this.carcass instanceof Whitetail w && !this.scavenging) {
         KillSign.killed(this.level, w, this.caught, w.position(), w.getYRot(), KillSites.tuft(this.kind, this.record.pack, this.record.prey),
            this.kind.cat() || this.kind == Predator.COYOTE, this.random);
         KillSiteStore.Site site = KillSiteStore.of(this.level).site(w.getUUID());
         if (site == null) {
            KillSiteStore.of(this.level).addSite(w.getUUID(), w.position(), this.record.killedAt, this.kind.name(), this.record.prey);
         }
      }
      boolean anyLeft = false;
      double ring = this.carcass == null ? 0.9 : this.carcass.getBbWidth() * 0.5 + 0.9;
      for (int i = 0; i < this.members.size() && i < 8; i++) {
         WildlifeMob m = this.members.get(i);
         if (this.feedLeft[i] <= 0) {
            if (m.hunting() && m.behavior() != WildlifeMob.TRAVEL && this.carcass != null) {
               m.ecoState(WildlifeMob.TRAVEL);
            }
            if (this.carcass == null && i > 0 && this.phaseTicks % 40 == 1) {
               m.getLookControl().setLookAt(body.x, body.y, body.z);
            }
            continue;
         }
         anyLeft = true;
         float a = i * Mth.TWO_PI / Math.max(1, Math.min(8, this.members.size())) + (this.carcass == null ? 0 : this.carcass.getId() % 7);
         double r = ring + m.getBbWidth() * 0.5;
         Vec3 slot = this.carcass == null && i == 0 ? m.position() : body.add(Mth.cos(a) * r, 0.0, Mth.sin(a) * r);
         double dx = slot.x - m.getX(), dz = slot.z - m.getZ();
         boolean there = dx * dx + dz * dz < 1.2;
         if (!this.seated[i]) {
            if (there || this.phaseTicks > 400) {
               this.seated[i] = true;
               m.getNavigation().stop();
               m.ecoState(WildlifeMob.TEAR);
            } else if (this.phaseTicks % 20 == 1) {
               go(m, slot, 0.95);
            }
            continue;
         }
         m.getLookControl().setLookAt(body.x, body.y + 0.2, body.z, 20.0F, 30.0F);
         this.feedLeft[i]--;
         if (this.record != null) {
            float share = switch (this.kind) {
               case WOLF -> 0.2F;
               case COYOTE -> 0.1F;
               case GRIZZLY, BLACK_BEAR, POLAR_BEAR -> 0.3F;
               default -> 0.33F;
            };
            this.record.feed(share / Math.max(1, this.kind.feedTicks));
         }
         if ((this.phaseTicks + i * 17) % 60 == 0) {
            this.level.playSound(null, m.getX(), m.getY() + 0.4, m.getZ(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.45F, 0.55F + this.random.nextFloat() * 0.15F);
         }
         if ((this.phaseTicks + i * 41) % 220 == 0) {
            SoundEvent g = switch (this.kind) {
               case WOLF, COYOTE -> SoundEvents.WOLF_GROWL;
               case GRIZZLY, BLACK_BEAR, POLAR_BEAR -> SoundEvents.POLAR_BEAR_AMBIENT;
               default -> SoundEvents.CAT_HISS;
            };
            this.level.playSound(null, m.getX(), m.getEyeY(), m.getZ(), g, SoundSource.NEUTRAL, 0.55F, this.kind == Predator.COYOTE ? 1.3F : 0.7F);
         }
         if (this.carcass != null && (this.phaseTicks + i * 29) % 300 == 0) {
            KillSign.feeding(this.level, m, body, this.carcass.getUUID(), KillSites.tuft(this.kind, this.record == null ? 1 : this.record.pack,
               this.record == null ? "animal" : this.record.prey), this.random, this.random.nextInt(3) == 0);
         }
      }
      if (this.ticks % 200 == 0) {
         this.persist();
      }
      if (!anyLeft || this.phaseTicks > 6000) {
         this.end(true);
      }
   }

   /** write the record back to the carcass (KillCarcass shares the object; a deer keeps a copy in its persistent data) */
   private void persist() {
      if (this.record != null && this.carcass instanceof Whitetail w && !w.isRemoved()) {
         // merge into what is stored (a hunter may have salvaged it meanwhile)
         KillRecord stored = KillSites.record(w);
         if (stored == null) {
            KillSites.record(w, this.record);
         } else {
            stored.fed = Math.max(stored.fed, this.record.fed);
            for (String v : this.record.visitors.split(", ")) {
               stored.visited(v);
            }
            KillSites.record(w, stored);
         }
      }
   }

   /**
    * Walk toward {@code target}; a target further than the path search reaches (follow range 32) is approached by a
    * waypoint 24 blocks along the way, re-aimed on the next call.
    */
   private static void go(WildlifeMob m, Vec3 target, double speed) {
      if (target == null || !Double.isFinite(target.x) || !Double.isFinite(target.y) || !Double.isFinite(target.z)) {
         return;
      }
      Vec3 from = m.position();
      Vec3 d = target.subtract(from);
      double len = Math.sqrt(d.x * d.x + d.z * d.z);
      if (len > 24.0) {
         double k = 24.0 / len;
         target = new Vec3(from.x + d.x * k, from.y + d.y * k, from.z + d.z * k);
      }
      m.getNavigation().moveTo(target.x, target.y, target.z, speed);
   }

   /** a spot beside the prey for pack member {@code i}: the leader goes straight in, the others swing out to the sides */
   private Vec3 flank(int i, double out) {
      Vec3 p = this.prey.position();
      if (i == 0) {
         return p;
      }
      WildlifeMob lead = this.leader();
      Vec3 dir = p.subtract(lead.position()).multiply(1.0, 0.0, 1.0);
      double len = dir.length();
      if (len < 1.0E-3 || !Double.isFinite(len)) {
         return p;
      }
      dir = dir.scale(1.0 / len);
      double side = (i % 2 == 1 ? 1.0 : -1.0) * out * (1 + (i - 1) / 2);
      return p.add(-dir.z * side, 0.0, dir.x * side);
   }

   /** Hunt over: members walk off (fed or not); hungry ones try again later. */
   void end(boolean fed) {
      if (this.phase == Phase.DONE) {
         return;
      }
      this.persist();
      long now = this.level.getGameTime();
      if (this.carcass != null) {
         KillSiteStore.Site site = KillSiteStore.of(this.level).site(this.carcass.getUUID());
         if (site != null) {
            site.busyUntil = now;
         }
      }
      Vec3 from = this.carcass != null ? this.carcass.position() : this.prey != null ? this.prey.position() : null;
      for (WildlifeMob m : this.members) {
         PredationService.ended(m, fed, now, this.info != null && this.info.kind().small());
         if (m.isAlive() && from != null && m.hunting()) {
            Vec3 away = DefaultRandomPos.getPosAway(m, 20, 6, from);
            if (away != null) {
               m.getNavigation().moveTo(away.x, away.y, away.z, 0.9);
            } else {
               m.getNavigation().stop();
            }
         }
      }
      this.phase(Phase.DONE);
   }

   String describe() {
      String p = this.prey == null ? "-" : this.info == null ? this.prey.getName().getString() : this.info.label();
      return this.kind.title() + " x" + this.members.size() + " · " + this.phase.name().toLowerCase(java.util.Locale.ROOT) + " " + this.phaseTicks / 20 + "s · "
         + (this.scavenging ? "scavenging" : p) + (this.phase == Phase.RUN || this.phase == Phase.STRIKE ? (this.success ? " · closing" : " · will fail") : "");
   }
}
