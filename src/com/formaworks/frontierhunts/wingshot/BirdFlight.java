package com.formaworks.frontierhunts.wingshot;

import com.formaworks.frontierhunts.Wilderness;
import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import com.formaworks.frontierhunts.wildlife2026.WildlifeSpecies;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * [wingshot] Server side of the game birds' flight: owns one {@link Flight} per flying duck/grouse, feeds it the world
 * (heightmaps, short ray probes, loaded-chunk checks), moves the entity along the flight's velocity with Minecraft's
 * own collision ({@code move}), turns the body to the flight heading and syncs the flight phase (one byte) for the wing
 * animation. Also the shot bird: invulnerability between pellets of one shot, the dead fall (it keeps tumbling down,
 * lies where it lands, and its loot drops there), and the hit event for the feather burst.
 *
 * <p>Hooks (all in WildlifeMob, marked [wingshot]): {@link #think} before aiStep, {@link #travel}, {@link #startle}
 * in place of the old hop, {@link #beforeHurt}/{@link #afterHurt}, {@link #deferLoot}, {@link #holdDeath},
 * {@link #flyingSize}. LureGoal calls {@link #lure}/{@link #flying}; HuntEvents.flare calls {@link #flare}.
 */
public final class BirdFlight {
   /** dead birds lie this long where they came down before they are taken (loot + vanilla death puff) */
   static final int LIE_TICKS = 70;

   private BirdFlight() {
   }

   /** Per-bird state, kept on the mob ({@code WildlifeMob.wingshot}). */
   static final class Ctl {
      Flight f;
      int landedAt = -1000;
      Flight.Mission lastMission;
      boolean lastLanded;
      int nextLandingSearch;
      // shot / dead
      Vec3 push = Vec3.ZERO;
      DamageSource deferred;
      int deadTicks, downAt = -1;
      // ambient life
      int nextAmbient;
      BirdLife.Drum drum;
   }

   static Ctl ctl(WildlifeMob m) {
      if (m.wingshot instanceof Ctl c) {
         return c;
      }
      Ctl c = new Ctl();
      c.nextAmbient = m.tickCount + 200 + m.getRandom().nextInt(600);
      m.wingshot = c;
      return c;
   }

   static Flight.Kind kind(WildlifeSpecies s) {
      return s == WildlifeSpecies.GROUSE ? Flight.Kind.GROUSE : Flight.Kind.DUCK;
   }

   /** Under control of the flight pilot right now (flying, taking off, landing). */
   public static boolean active(WildlifeMob m) {
      return m.wingshot instanceof Ctl c && c.f != null;
   }

   /** Alias for callers outside the package (LureGoal): still on its way. */
   public static boolean flying(WildlifeMob m) {
      return active(m);
   }

   /** Wider, flatter box in flight: the spread wings can be hit (and the pellets of a shotgun pattern do hit them). */
   public static EntityDimensions flyingSize(WildlifeSpecies s, EntityDimensions standing) {
      float w = s == WildlifeSpecies.GROUSE ? 0.55F : 0.72F;
      float h = s == WildlifeSpecies.GROUSE ? 0.28F : 0.32F;
      return EntityDimensions.scalable(w, h).withEyeHeight(h * 0.6F);
   }

   // ================================================================== starting flights

   /**
    * Spooked by something at {@code threat}: a duck jumps off the water (its flock mates with it), a grouse flushes.
    * Returns false where the old behaviour should run (config off, client, riding, already dead).
    */
   public static boolean startle(WildlifeMob m, Vec3 threat) {
      if (!(m.level() instanceof ServerLevel level) || !WingshotConfig.flight() || !m.isAlive() || m.isPassenger() || m.isLeashed()) {
         return false;
      }
      Ctl c = ctl(m);
      if (c.drum != null) {
         BirdLife.stop(m, c); // off the drumming log
      }
      if (threat == null || !finite(threat)) {
         Player p = level.getNearestPlayer(m, 32.0);
         threat = p != null ? p.position() : m.position().add(m.getLookAngle().scale(-4.0));
      }
      if (c.f != null) {
         Flight f = c.f;
         if (f.mission != Flight.Mission.FLEE && f.mission != Flight.Mission.FALL && f.stage() != Flight.WAIT && f.stage() != Flight.LIFT) {
            f.flee(threat.x, threat.z, true); // flare: turn away and climb
            f.vy = Math.max(f.vy, 0.2);
            if (f.leader == null) {
               flareSound(level, m);
            }
         }
         return true;
      }
      if (m.tickCount - c.landedAt < 30) {
         return true; // just came down: it runs (the flee state) instead of bursting straight back up
      }
      Flight f = newFlight(m, level);
      f.flee(threat.x, threat.z, false);
      begin(m, c, f);
      if (m.species == WildlifeSpecies.GROUSE) {
         level.playSound(null, m.getX(), m.getY() + 0.2, m.getZ(), WingshotContent.FLUSH.get(), SoundSource.NEUTRAL, 1.6F, 0.92F + m.getRandom().nextFloat() * 0.16F);
      } else {
         takeoffSound(level, m, true);
         recruitFlock(level, m, f, threat);
      }
      return true;
   }

   /** LureGoal: a spooked duck off the water or out of its approach flares away. */
   public static boolean flare(WildlifeMob m) {
      if (!(m.level() instanceof ServerLevel level) || !WingshotConfig.flight()) {
         return false;
      }
      Player p = level.getNearestPlayer(m, 40.0);
      Vec3 threat = p != null ? p.position() : m.position().add(m.getLookAngle().scale(-4.0));
      return startle(m, threat);
   }

   /** LureGoal: fly in to {@code land} (decoys, call): at height, circle, final into the wind, cupped wings, splash down. */
   public static boolean lure(WildlifeMob m, Vec3 land, boolean water) {
      if (!(m.level() instanceof ServerLevel level) || !WingshotConfig.flight() || land == null || !finite(land) || !m.isAlive()) {
         return false;
      }
      Ctl c = ctl(m);
      if (c.f != null && c.f.mission != Flight.Mission.LURE && c.f.mission != Flight.Mission.RELOCATE) {
         return true; // busy (fleeing / falling)
      }
      boolean airborne = !m.onGround() && !m.isInWater();
      // join a flock already coming in to the same spread
      Flight lead = null;
      int taken = 0;
      for (WildlifeMob o : level.getEntitiesOfClass(WildlifeMob.class, m.getBoundingBox().inflate(24.0, 16.0, 24.0),
         o -> o != m && o.species == m.species && o.isAlive() && o.wingshot instanceof Ctl oc && oc.f != null)) {
         Flight of = ((Ctl)o.wingshot).f;
         if (of.mission == Flight.Mission.LURE && of.leader == null && of.landSet && sq(of.lx - land.x) + sq(of.lz - land.z) < 14.0 * 14.0
            && of.stage() != Flight.FINAL && !of.landed) {
            lead = of;
         } else if (of.mission == Flight.Mission.FOLLOW && of.leader != null && of.leader.mission == Flight.Mission.LURE) {
            taken++;
         }
      }
      Flight f = c.f != null ? c.f : newFlight(m, level);
      if (lead != null && lead != f && taken < 7) {
         f.follow(lead, taken + 1, false, airborne ? 0 : 1 + m.getRandom().nextInt(4), airborne);
      } else {
         f.lure(land.x, land.y, land.z, water, airborne);
      }
      if (c.f == null) {
         begin(m, c, f);
         if (!airborne) {
            takeoffSound(level, m, false);
         }
      }
      return true;
   }

   /** An unhurried flight to other water (ambient life, commands). */
   static boolean relocate(WildlifeMob m, ServerLevel level, Vec3 land, boolean water) {
      Ctl c = ctl(m);
      if (c.f != null || !m.isAlive()) {
         return false;
      }
      Flight f = newFlight(m, level);
      f.relocate(land.x, land.y, land.z, water);
      begin(m, c, f);
      takeoffSound(level, m, false);
      recruitFlock(level, m, f, null);
      return true;
   }

   /** A bird put into the air already flying along (tx, tz) - the passing flock of the test command. */
   static Flight pass(WildlifeMob m, ServerLevel level, double heading, double tx, double tz) {
      Ctl c = ctl(m);
      Flight f = newFlight(m, level);
      f.heading = heading;
      f.speed = f.kind.cruise;
      f.flee(m.getX() - Math.cos(heading) * 30.0, m.getZ() - Math.sin(heading) * 30.0, true);
      f.alarmed = false;
      f.tx = tx;
      f.tz = tz;
      begin(m, c, f);
      return f;
   }

   static Flight newFlight(WildlifeMob m, ServerLevel level) {
      long seed = m.getUUID().getLeastSignificantBits() ^ (long)m.tickCount * 0x9E3779B97F4A7C15L;
      Flight f = new Flight(kind(m.species), seed);
      double heading = Math.toRadians(m.getYRot() + 90.0F);
      Vec3 v = m.getDeltaMovement();
      f.at(m.getX(), m.getY(), m.getZ(), heading, Math.hypot(v.x, v.z), v.y);
      Wilderness.Wind w = Wilderness.wind(level.getSeed(), level.getGameTime(), level.isRaining(), level.isThundering());
      f.wind(w.east(), w.south());
      return f;
   }

   static void begin(WildlifeMob m, Ctl c, Flight f) {
      c.f = f;
      c.nextLandingSearch = 0;
      m.getNavigation().stop();
      m.setPose(Pose.FALL_FLYING);
      m.flightPhase(f.phase);
   }

   /** Ducks within reach jump with the first one and hold loose V / echelon slots on it. */
   private static void recruitFlock(ServerLevel level, WildlifeMob m, Flight lead, Vec3 threat) {
      List<WildlifeMob> mates = level.getEntitiesOfClass(WildlifeMob.class, m.getBoundingBox().inflate(13.0, 5.0, 13.0),
         o -> o != m && o.species == m.species && o.isAlive() && !o.isPassenger() && !active(o) && (o.onGround() || o.isInWater()));
      mates.sort((a, b) -> Double.compare(a.distanceToSqr(m), b.distanceToSqr(m)));
      boolean echelon = m.getRandom().nextInt(3) == 0;
      int i = 0;
      for (WildlifeMob o : mates) {
         if (++i > 7) {
            break;
         }
         Ctl oc = ctl(o);
         Flight f = newFlight(o, level);
         // they go up a beat after the first, one after another (the startle ripples through the flock)
         f.follow(lead, i, echelon, 1 + i + o.getRandom().nextInt(3), false);
         begin(o, oc, f);
         if (threat != null) {
            o.wingshotThreat(threat, 120);
         }
      }
   }

   private static void takeoffSound(ServerLevel level, WildlifeMob m, boolean alarmed) {
      level.playSound(null, m.getX(), m.getY() + 0.2, m.getZ(), WingshotContent.DUCK_TAKEOFF.get(), SoundSource.NEUTRAL, alarmed ? 1.3F : 0.9F,
         0.94F + m.getRandom().nextFloat() * 0.12F);
      if (alarmed && m.getRandom().nextInt(3) != 0) {
         // the hen's alarm quacks as she jumps
         level.playSound(null, m.getX(), m.getY() + 0.3, m.getZ(), com.formaworks.frontierhunts.wildlife2026.RealVoices.DUCK_QUACK.get(), SoundSource.NEUTRAL, 1.1F,
            1.02F + m.getRandom().nextFloat() * 0.1F);
      }
   }

   private static void flareSound(ServerLevel level, WildlifeMob m) {
      level.playSound(null, m.getX(), m.getY(), m.getZ(), WingshotContent.DUCK_TAKEOFF.get(), SoundSource.NEUTRAL, 0.8F, 1.1F);
   }

   // ================================================================== per tick (before aiStep)

   public static void think(WildlifeMob m) {
      if (!(m.level() instanceof ServerLevel level) || !m.isAlive()) {
         return;
      }
      Ctl c = ctl(m);
      Flight f = c.f;
      if (f == null) {
         if (!WingshotConfig.flight()) {
            return;
         }
         // up in the air without a pilot (chunk reloaded mid-flight, knocked up, spawned high): glide down
         if (!m.onGround() && !m.isInWater() && !m.isPassenger() && m.tickCount > 3 && m.fallDistance > 1.2F && m.getDeltaMovement().y < -0.1) {
            Flight s = newFlight(m, level);
            s.settle();
            begin(m, c, s);
            return;
         }
         BirdLife.tick(m, c, level);
         return;
      }
      if (m.isPassenger() || m.isLeashed()) {
         end(m, c, level, false);
         return;
      }
      // the pilot works from where Minecraft's collision actually left the bird
      f.x = m.getX();
      f.y = m.getY();
      f.z = m.getZ();
      if (f.leader != null && f.leader.done && !f.leader.landed && f.leader.mission != Flight.Mission.FALL) {
         f.leader = null; // the leader was removed: flies on alone (Flight handles a null leader)
      }
      World w = World.of(level, m);
      // don't fly off into unloaded country: turn back toward where it came from
      if (f.mission == Flight.Mission.FLEE && f.stage() == Flight.OUT && m.tickCount % 10 == 0) {
         double ax = m.getX() + Math.cos(f.heading) * 40.0, az = m.getZ() + Math.sin(f.heading) * 40.0;
         if (!level.isLoaded(BlockPos.containing(ax, m.getY(), az))) {
            f.tx = f.ox + (m.getRandom().nextDouble() - 0.5) * 40.0;
            f.tz = f.oz + (m.getRandom().nextDouble() - 0.5) * 40.0;
            if (!f.landSet) {
               f.land(f.ox, f.oy, f.oz, true);
            }
         }
      }
      f.tick(w);
      if (f.needLanding && m.tickCount >= c.nextLandingSearch) {
         c.nextLandingSearch = m.tickCount + 20;
         Vec3 spot = findLanding(level, m, f);
         if (spot != null) {
            f.land(spot.x, spot.y, spot.z, isWater(level, spot));
         }
      }
      // the body follows the flight: heading -> yaw, climb angle -> head pitch
      float yaw = (float)Math.toDegrees(f.heading) - 90.0F;
      if (f.phase != Flight.NONE || f.speed > 0.02) {
         m.setYRot(yaw);
         m.yBodyRot = yaw;
         m.yHeadRot = yaw;
      }
      float pitch = (float)Math.toDegrees(-Math.atan2(f.vyNow(), Math.max(0.1, f.speed)));
      m.setXRot(Mth.clamp(pitch * 0.5F, -30.0F, 30.0F));
      m.getNavigation().stop();
      m.flightPhase(f.phase);
      if (f.landed || f.done) {
         end(m, c, level, f.landed);
      }
   }

   private static void end(WildlifeMob m, Ctl c, ServerLevel level, boolean landed) {
      Flight f = c.f;
      c.f = null;
      c.landedAt = m.tickCount;
      c.lastMission = f == null ? null : f.mission;
      c.lastLanded = landed;
      m.flightPhase(Flight.NONE);
      if (m.getPose() == Pose.FALL_FLYING) {
         m.setPose(Pose.STANDING);
      }
      if (f != null) {
         // carry a little of the touchdown speed: a skid on the water, a few running steps on the ground
         m.setDeltaMovement(f.vx() * 0.5, Math.min(0.0, f.vyNow()) * 0.2, f.vz() * 0.5);
         if (m.species == WildlifeSpecies.GROUSE && f.hasThreat) {
            // lands running and keeps going on foot
            m.wingshotThreat(new Vec3(f.thX, m.getY(), f.thZ), 70);
         }
      }
      m.fallDistance = 0.0F;
   }

   /** The flight moves the bird (and the dead fall drops it). True when handled. */
   public static boolean travel(WildlifeMob m) {
      if (m.level().isClientSide || !(m.wingshot instanceof Ctl c)) {
         return false;
      }
      if (!m.isAlive()) {
         return fallTravel(m, c);
      }
      Flight f = c.f;
      if (f == null) {
         return false;
      }
      Vec3 v = new Vec3(f.vx(), f.vyNow(), f.vz());
      if (!finite(v)) {
         v = Vec3.ZERO;
      }
      if (f.stage() == Flight.WAIT) {
         return false; // a flock mate waiting its beat: still on the water
      }
      m.move(MoverType.SELF, v);
      if (m.horizontalCollision) {
         f.speed *= 0.6;
      }
      if (m.verticalCollisionBelow && v.y < 0.0 && (f.stage() == Flight.FINAL || f.stage() == Flight.GLIDEIN)) {
         f.landed = true;
      }
      m.setDeltaMovement(v);
      m.fallDistance = 0.0F;
      m.calculateEntityAnimation(true);
      return true;
   }

   // ================================================================== shot birds

   /** What the bird was doing the moment before a hit. */
   record Before(boolean flying, float health, Vec3 velocity) {
   }

   /** Every pellet that reaches a bird is its own wound: no shared invulnerability window between the pellets of one shot. */
   public static Object beforeHurt(WildlifeMob m, DamageSource source) {
      if (m.level().isClientSide) {
         return null;
      }
      if (source.getDirectEntity() instanceof Projectile) {
         m.invulnerableTime = 0;
      }
      boolean flying = active(m) || !m.onGround() && !m.isInWater();
      return new Before(flying, m.getHealth(), m.getDeltaMovement());
   }

   public static void afterHurt(WildlifeMob m, DamageSource source, float amount, boolean accepted, Object token) {
      if (!accepted || !(token instanceof Before b) || !(m.level() instanceof ServerLevel level)) {
         return;
      }
      Ctl c = ctl(m);
      Entity direct = source.getDirectEntity();
      Entity attacker = source.getEntity();
      Vec3 dir;
      if (direct instanceof Projectile p && p.getDeltaMovement().lengthSqr() > 1.0E-6) {
         dir = p.getDeltaMovement().normalize();
      } else if (attacker != null) {
         dir = m.position().subtract(attacker.position());
         dir = dir.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.2, 0.0) : dir.normalize();
      } else {
         dir = new Vec3(0.0, 0.3, 0.0);
      }
      float energy = Mth.clamp(amount / Math.max(1.0F, m.getMaxHealth()), 0.1F, 2.0F);
      // the shot shoves the body: a pellet strike or bullet knocks a bird sideways and down
      c.push = dir.scale(0.05 + 0.05 * Math.min(1.0F, energy)).add(0.0, -0.02, 0.0);
      boolean killed = !m.isAlive();
      if (!killed) {
         if (c.f != null) {
            c.f.hit(m.getHealth() < m.getMaxHealth() * 0.5F);
         } else if (attacker != null) {
            startle(m, attacker.position());
         }
      }
      Vec3 at = direct != null && direct.position().distanceToSqr(m.position()) < 4.0 ? direct.position() : m.position().add(0.0, m.getBbHeight() * 0.5, 0.0);
      ServerPlayer shooter = attacker instanceof ServerPlayer sp ? sp : null;
      boolean clean = killed && b.flying() && b.health() >= m.getMaxHealth() - 0.01F;
      BirdHits.record(m, at, dir, energy, shotKind(direct), killed, b.flying(), m.isInWater(), shooter, clean);
   }

   /** Which kind of shot: the mod's own weapons by their stats, other mods' projectiles by what they are. */
   static byte shotKind(Entity direct) {
      if (direct instanceof com.formaworks.frontierhunts.expedition.HuntProjectile hp) {
         com.formaworks.frontierhunts.expedition.Weapon w = hp.kind();
         return w == null ? BirdNet.SHOT_BULLET : w.pellets > 0 ? BirdNet.SHOT_PELLETS : w.bow ? BirdNet.SHOT_ARROW : BirdNet.SHOT_BULLET;
      }
      if (direct instanceof com.formaworks.frontierhunts.hunting.FieldArrow || direct instanceof net.minecraft.world.entity.projectile.AbstractArrow) {
         return BirdNet.SHOT_ARROW;
      }
      if (direct instanceof Projectile) {
         return BirdNet.SHOT_BULLET; // RifleBullet, TACZ and other guns' bullets, snowballs alike: a fast small projectile
      }
      return BirdNet.SHOT_OTHER;
   }

   /** Airborne at death: the loot waits until the bird is down (dropped there by {@link #holdDeath}). */
   public static boolean deferLoot(WildlifeMob m, DamageSource source) {
      if (m.level().isClientSide || !WingshotConfig.flight()) {
         return false;
      }
      Ctl c = ctl(m);
      boolean airborne = c.f != null || !m.onGround() && !m.isInWater();
      if (!airborne) {
         return false;
      }
      c.deferred = source;
      return true;
   }

   /**
    * Death ticks: a shot bird tumbles down (moved by {@link #travel}), then lies a while where it fell; then the vanilla
    * death (puff, removal) runs. On clients the body is held until the server removes it, so it never tips over.
    */
   public static boolean holdDeath(WildlifeMob m) {
      if (!WingshotConfig.flight()) {
         return false;
      }
      if (m.level().isClientSide) {
         return true;
      }
      Ctl c = ctl(m);
      c.deadTicks++;
      boolean down = m.onGround() || m.isInWater() || (c.f != null && c.f.landed) || m.isPassenger();
      if (!down && c.deadTicks < 220) {
         return true;
      }
      if (c.downAt < 0) {
         c.downAt = c.deadTicks;
         m.flightPhase(Flight.NONE);
         if (m.level() instanceof ServerLevel level) {
            if (c.deferred != null) {
               DamageSource s = c.deferred;
               c.deferred = null;
               try {
                  m.wingshotLoot(level, s);
               } catch (RuntimeException e) {
                  com.mojang.logging.LogUtils.getLogger().warn("Frontier Hunts wingshot: deferred loot failed", e);
               }
            }
            if (!m.isInWater()) {
               // a splash of blood where it hit the ground (tracking sign, same rules as every wound)
               try {
                  com.formaworks.frontierhunts.tracking.TrailService.pool(m, m.position().add(0.0, 0.1, 0.0), 0);
               } catch (RuntimeException ignored) {
               }
            }
         }
      }
      return c.deadTicks - c.downAt < LIE_TICKS;
   }

   private static boolean fallTravel(WildlifeMob m, Ctl c) {
      if (!WingshotConfig.flight()) {
         return false;
      }
      Flight f = c.f;
      if (f == null) {
         if (c.downAt >= 0 || m.onGround() || m.isInWater() || !(m.level() instanceof ServerLevel level)) {
            return m.isInWater() && floatOnWater(m);
         }
         // killed in the air without a pilot (knocked up, falling): the same ballistic fall
         f = c.f = newFlight(m, level);
      }
      if (f.mission != Flight.Mission.FALL) {
         f.x = m.getX();
         f.y = m.getY();
         f.z = m.getZ();
         f.fall(c.push.x, c.push.y, c.push.z);
         m.flightPhase(Flight.FALL);
      }
      if (f.landed || m.onGround()) {
         f.landed = true;
         if (m.isInWater()) {
            return floatOnWater(m);
         }
         return false; // lies there: vanilla friction
      }
      if (m.isInWater()) {
         f.landed = true;
         m.setDeltaMovement(m.getDeltaMovement().multiply(0.3, 0.0, 0.3));
         return floatOnWater(m);
      }
      double px = m.getX(), py = m.getY(), pz = m.getZ();
      f.x = px;
      f.y = py;
      f.z = pz;
      f.tick(World.of((ServerLevel)m.level(), m));
      Vec3 v = new Vec3(f.fx, f.fy, f.fz);
      if (!finite(v)) {
         v = new Vec3(0.0, -0.3, 0.0);
      }
      m.move(MoverType.SELF, v);
      if (m.horizontalCollision) {
         f.fx *= -0.25; // glances off a trunk or wall
         f.fz *= -0.25;
      }
      if (m.onGround()) {
         f.landed = true;
         m.setDeltaMovement(v.x * 0.25, 0.0, v.z * 0.25);
      } else {
         m.setDeltaMovement(v);
      }
      m.fallDistance = 0.0F;
      return true;
   }

   /** a dead bird floats, drifting slowly to rest on the surface */
   private static boolean floatOnWater(WildlifeMob m) {
      BlockPos p = m.blockPosition();
      FluidState fs = m.level().getFluidState(p);
      double surface = fs.isEmpty() ? m.getY() : p.getY() + fs.getHeight(m.level(), p);
      FluidState above = m.level().getFluidState(p.above());
      if (!above.isEmpty()) {
         surface = p.getY() + 1.0 + above.getHeight(m.level(), p.above());
      }
      Vec3 v = m.getDeltaMovement();
      double sink = 0.06; // floats low in the water
      m.setDeltaMovement(v.x * 0.86, Mth.clamp((surface - sink - m.getY()) * 0.3, -0.08, 0.08), v.z * 0.86);
      m.move(MoverType.SELF, m.getDeltaMovement());
      m.fallDistance = 0.0F;
      return true;
   }

   // ================================================================== landing sites

   /** Open water near where the flight is headed (ducks), else open ground; null if none loaded. */
   static Vec3 findLanding(ServerLevel level, WildlifeMob m, Flight f) {
      double cx = m.getX() + Math.cos(f.heading) * 30.0, cz = m.getZ() + Math.sin(f.heading) * 30.0;
      Vec3 ground = null;
      for (int ring = 0; ring <= 6; ring++) {
         double r = ring * 9.0;
         int n = ring == 0 ? 1 : 8;
         double a0 = m.getRandom().nextDouble() * Math.PI * 2.0;
         for (int k = 0; k < n; k++) {
            double a = a0 + k * Math.PI * 2.0 / n;
            int x = Mth.floor(cx + Math.cos(a) * r), z = Mth.floor(cz + Math.sin(a) * r);
            Vec3 w = openWater(level, x, z);
            if (w != null) {
               return w;
            }
            if (ground == null && f.kind == Flight.Kind.DUCK) {
               ground = openGround(level, x, z);
            }
         }
      }
      return ground;
   }

   /** the surface of a stretch of open water at (x, z): water with water around it and sky above */
   static Vec3 openWater(ServerLevel level, int x, int z) {
      if (!level.isLoaded(new BlockPos(x, 0, z))) {
         return null;
      }
      int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
      BlockPos top = new BlockPos(x, h - 1, z);
      if (!level.getFluidState(top).is(Fluids.WATER) || !level.getBlockState(top.above()).isAir()) {
         return null;
      }
      int wet = 0;
      for (Direction d : Direction.Plane.HORIZONTAL) {
         BlockPos n = top.relative(d, 2);
         if (level.isLoaded(n) && level.getFluidState(n).is(Fluids.WATER) && level.getBlockState(n.above()).isAir()) {
            wet++;
         }
      }
      if (wet < 3) {
         return null;
      }
      FluidState fs = level.getFluidState(top);
      return new Vec3(x + 0.5, top.getY() + fs.getHeight(level, top), z + 0.5);
   }

   /** open ground with no trees overhead (a field), for ducks that find no water */
   static Vec3 openGround(ServerLevel level, int x, int z) {
      if (!level.isLoaded(new BlockPos(x, 0, z))) {
         return null;
      }
      int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
      int g = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
      if (h != g) {
         return null;
      }
      BlockPos top = new BlockPos(x, g - 1, z);
      if (!level.getFluidState(top).isEmpty() || level.getBlockState(top).getCollisionShape(level, top).isEmpty()) {
         return null;
      }
      return new Vec3(x + 0.5, g, z + 0.5);
   }

   static boolean isWater(ServerLevel level, Vec3 p) {
      BlockPos b = BlockPos.containing(p.x, p.y - 0.2, p.z);
      return level.isLoaded(b) && level.getFluidState(b).is(Fluids.WATER);
   }

   // ================================================================== the world as the flight sees it

   static final class World implements Flight.World {
      private static final World INSTANCE = new World();
      ServerLevel level;
      Entity mob;
      private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

      static World of(ServerLevel level, Entity mob) {
         World w = INSTANCE; // server thread only
         w.level = level;
         w.mob = mob;
         return w;
      }

      @Override
      public double floor(double x, double z, boolean canopy) {
         int ix = Mth.floor(x), iz = Mth.floor(z);
         this.pos.set(ix, 0, iz);
         if (!this.level.isLoaded(this.pos)) {
            return Double.NaN;
         }
         int h = this.level.getHeight(canopy ? Heightmap.Types.MOTION_BLOCKING : Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ix, iz);
         this.pos.set(ix, h - 1, iz);
         FluidState fs = this.level.getFluidState(this.pos);
         if (!fs.isEmpty()) {
            return h - 1 + fs.getHeight(this.level, this.pos);
         }
         VoxelShape shape = this.level.getBlockState(this.pos).getCollisionShape(this.level, this.pos);
         return shape.isEmpty() ? h - 1 : h - 1 + shape.max(Direction.Axis.Y);
      }

      @Override
      public double clear(double x, double y, double z, double dx, double dy, double dz, double max) {
         if (!(max > 0.0) || !Double.isFinite(x + y + z + dx + dy + dz)) {
            return 0.0;
         }
         Vec3 a = new Vec3(x, y, z);
         Vec3 b = new Vec3(x + dx * max, y + dy * max, z + dz * max);
         if (!this.level.isLoaded(BlockPos.containing(a)) || !this.level.isLoaded(BlockPos.containing(b))) {
            return 0.0; // unloaded ahead counts as a wall
         }
         BlockHitResult r = this.level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this.mob));
         return r.getType() == HitResult.Type.MISS ? max : r.getLocation().distanceTo(a);
      }
   }

   // ================================================================== commands / helpers

   /** Every bird within {@code r} of {@code at}. */
   static List<WildlifeMob> birds(ServerLevel level, Vec3 at, double r) {
      return new ArrayList<>(level.getEntitiesOfClass(WildlifeMob.class, new AABB(at, at).inflate(r), o -> o.species.bird && o.isAlive()));
   }

   static boolean finite(Vec3 v) {
      return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
   }

   static double sq(double v) {
      return v * v;
   }

   static Flight flightOf(LivingEntity e) {
      return e instanceof WildlifeMob m && m.wingshot instanceof Ctl c ? c.f : null;
   }
}
