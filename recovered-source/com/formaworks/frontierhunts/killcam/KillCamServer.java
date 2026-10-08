package com.formaworks.frontierhunts.killcam;

import com.formaworks.frontierhunts.expedition.HuntProjectile;
import com.formaworks.frontierhunts.hunting.DeerAnatomy;
import com.formaworks.frontierhunts.hunting.DeerOrgan;
import com.formaworks.frontierhunts.hunting.DeerTraits;
import com.formaworks.frontierhunts.hunting.FieldArrow;
import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.rifle.RifleBullet;
import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import com.formaworks.frontierhunts.wildlife2026.WildlifeSpecies;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Server half of the kill cam. Tracks every projectile a real player fires (origin + per-tick samples), predicts lethal
 * shots for the mod's own rifle bullets and arrows the tick they spawn, and confirms or cancels on the real hit. All of
 * it is bookkeeping around the existing hit code; it never changes damage, hit regions or timing.
 */
@EventBusSubscriber(modid = "frontierhunts")
public final class KillCamServer {
   private static final int MAX_TRACKS = 256;
   private static final int MAX_SAMPLES = 400;
   private static final double MIN_DISTANCE = 6.0;
   private static final Set<WildlifeSpecies> TROPHY_WILDLIFE = EnumSet.of(
      WildlifeSpecies.GRIZZLY, WildlifeSpecies.POLAR_BEAR, WildlifeSpecies.BLACK_BEAR, WildlifeSpecies.COUGAR, WildlifeSpecies.LION,
      WildlifeSpecies.PANTHER, WildlifeSpecies.BISON, WildlifeSpecies.ELK, WildlifeSpecies.MOOSE
   );
   private static final Map<Integer, Track> TRACKS = new HashMap<>();
   private static final Map<UUID, Integer> PREFS = new HashMap<>();
   private static final Map<UUID, Long> PREFS_AT = new HashMap<>();
   private static int nextShot = 1;

   private KillCamServer() {
   }

   private static final class Track {
      final int projectile;
      final UUID shooter;
      final ServerLevel level;
      final Vec3 origin;
      final long born;
      final byte weapon;
      final byte tip;
      final boolean primitive;
      final List<Vec3> samples = new ArrayList<>();
      int shotId;
      boolean predicted;
      boolean resolved;

      Track(Projectile p, ServerPlayer shooter, ServerLevel level) {
         this.projectile = p.getId();
         this.shooter = shooter.getUUID();
         this.level = level;
         this.origin = p.position();
         this.born = level.getGameTime();
         this.weapon = weapon(p);
         this.tip = p instanceof FieldArrow a ? (byte)a.tip().ordinal() : (p instanceof HuntProjectile h && h.tip() != null ? (byte)h.tip().ordinal() : -1);
         this.primitive = p instanceof FieldArrow a ? a.primitive() : p instanceof HuntProjectile h && h.primitive();
         this.samples.add(this.origin);
      }
   }

   /** Snapshot taken just before the hit is applied, while the animal still stands in its pre-impact pose. */
   private record Pending(
      LivingEntity target, ServerPlayer shooter, Entity projectile, Vec3 impact, Vec3 dir, Vec3 exit, byte region, byte organ,
      Vec3 pose, Vec3 poseVel, float bodyYaw, float headYaw, float pitch
   ) {
   }

   static byte weapon(Entity p) {
      if (p instanceof RifleBullet) {
         return KillCamNetwork.RIFLE;
      } else if (p instanceof FieldArrow) {
         return KillCamNetwork.ARROW;
      } else if (p instanceof HuntProjectile h) {
         return h.kind().bow ? KillCamNetwork.BOLT : KillCamNetwork.FIREARM;
      } else {
         String path = p == null ? "" : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(p.getType()).getPath();
         return path.contains("arrow") || path.contains("bolt") ? KillCamNetwork.ARROW : KillCamNetwork.FIREARM;
      }
   }

   static void prefs(ServerPlayer player, int mode) {
      long now = player.serverLevel().getGameTime();
      Long last = PREFS_AT.get(player.getUUID());
      if (last != null && now - last < 5L && now >= last) {
         return;
      }
      PREFS_AT.put(player.getUUID(), now);
      PREFS.put(player.getUUID(), Math.clamp(mode, 0, KillCamMode.values().length - 1));
   }

   private static KillCamMode mode(ServerPlayer player) {
      Integer m = PREFS.get(player.getUUID());
      return m == null ? KillCamMode.LETHAL : KillCamMode.byOrdinal(m);
   }

   private static boolean wants(ServerPlayer player) {
      return player != null && !(player instanceof FakePlayer) && mode(player) != KillCamMode.OFF && player.connection != null
         && player.connection.hasChannel(KillCamNetwork.Shot.TYPE);
   }

   static boolean trophy(Entity e) {
      if (e instanceof Whitetail deer) {
         DeerTraits t = deer.traits();
         if (!t.buck() || deer.species().antlers == GameSpecies.Antlers.NONE) {
            return false;
         }
         String grade = t.trophyGrade();
         return grade.equals("Silver") || grade.equals("Gold") || grade.equals("Exceptional");
      }
      return e instanceof WildlifeMob mob && TROPHY_WILDLIFE.contains(mob.species);
   }

   // ------------------------------------------------------------------ projectile tracking

   @SubscribeEvent(priority = EventPriority.LOW)
   public static void join(EntityJoinLevelEvent event) {
      if (event.isCanceled() || event.loadedFromDisk() || !(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Projectile p)) {
         return;
      }
      if (!(p.getOwner() instanceof ServerPlayer shooter) || !wants(shooter)) {
         return;
      }
      try {
         if (TRACKS.size() >= MAX_TRACKS) {
            Iterator<Track> it = TRACKS.values().iterator();
            it.next();
            it.remove();
         }
         Track track = new Track(p, shooter, level);
         TRACKS.put(track.projectile, track);
         KillCamPredictor.Result r = KillCamPredictor.predict(level, p);
         if (r != null && r.lethal() && r.target() instanceof LivingEntity target && KillCamPredictor.eligible(target)
            && (mode(shooter) != KillCamMode.TROPHY || trophy(target))) {
            Vec3 dir = r.velocity().normalize();
            Vec3 exit = null;
            if (target instanceof Whitetail deer && track.weapon == KillCamNetwork.RIFLE && r.energy() >= 0.3F) {
               exit = DeerAnatomy.exit(deer, r.impact(), r.velocity(), 3.0);
            } else if (target instanceof WildlifeMob && track.weapon == KillCamNetwork.RIFLE) {
               exit = boxExit(target, r.impact(), dir);
            }
            byte organ = (byte)(r.organ() != null ? r.organ().ordinal() : (target instanceof Whitetail ? organFor(r.region()) : -1)); // [vital] wildlife: no deer organ
            track.shotId = nextShot++;
            track.predicted = true;
            Pending snap = snapshot(target, shooter, p, r.impact(), dir, exit, r.region() == null ? -1 : (byte)r.region().ordinal(), organ);
            send(shooter, track.shotId, false, track, snap, r.samples(), r.ticks());
         }
      } catch (RuntimeException ex) {
         // presentation only: never let a prediction problem touch the actual shot
         LogUtils.getLogger().warn("Frontier Hunts kill cam: prediction skipped", ex);
      }
   }

   @SubscribeEvent
   public static void tick(LevelTickEvent.Post event) {
      if (!(event.getLevel() instanceof ServerLevel level) || TRACKS.isEmpty()) {
         return;
      }
      long now = level.getGameTime();
      Iterator<Track> it = TRACKS.values().iterator();
      while (it.hasNext()) {
         Track t = it.next();
         if (t.level != level) {
            continue;
         }
         Entity e = level.getEntity(t.projectile);
         boolean gone = e == null || e.isRemoved();
         boolean stale = now - t.born > 200L || now < t.born || e instanceof FieldArrow a && a.stuck()
            || e != null && e.getDeltaMovement().lengthSqr() < 1.0E-6 && now - t.born > 2L;
         if (gone || stale || t.resolved) {
            finish(t);
            it.remove();
            continue;
         }
         Vec3 p = e.position();
         if (t.samples.size() < MAX_SAMPLES && p.distanceToSqr(t.samples.get(t.samples.size() - 1)) > 0.0025) {
            t.samples.add(p);
         }
      }
   }

   /**
    * [killcam2] One extra flight sample for a projectile that moved outside its own tick: past the simulation distance
    * entities do not tick, so bullets and arrows fast-forward themselves there and report each step here, and the
    * replayed path stays the real one at any range.
    */
   public static void sample(Entity e) {
      if (TRACKS.isEmpty()) {
         return;
      }
      Track t = TRACKS.get(e.getId());
      if (t == null || t.resolved || t.samples.size() >= MAX_SAMPLES || e.isRemoved()) {
         return;
      }
      Vec3 p = e.position();
      if (p.distanceToSqr(t.samples.get(t.samples.size() - 1)) > 0.0025) {
         t.samples.add(p);
      }
   }

   @SubscribeEvent
   public static void leave(EntityLeaveLevelEvent event) {
      if (!(event.getLevel() instanceof ServerLevel) || TRACKS.isEmpty()) {
         return;
      }
      Track t = TRACKS.remove(event.getEntity().getId());
      if (t != null) {
         finish(t);
      }
   }

   private static void finish(Track t) {
      if (t.predicted && !t.resolved) {
         t.resolved = true;
         ServerPlayer shooter = t.level.getServer().getPlayerList().getPlayer(t.shooter);
         if (shooter != null) {
            KillCamNetwork.send(shooter, new KillCamNetwork.Cancel(t.shotId));
         }
      }
   }

   // ------------------------------------------------------------------ hit hooks (called from Whitetail.projectileHit)

   /** [killcam] hook: called right before the projectile damage is applied. Returns an opaque token (or null). */
   public static Object beforeDeerHit(Whitetail deer, DamageSource source, Entity projectile, Vec3 point, DeerAnatomy.Region region, float energy) {
      try {
         if (!(deer.level() instanceof ServerLevel) || region == null || point == null) {
            return null;
         }
         ServerPlayer shooter = source.getEntity() instanceof ServerPlayer sp ? sp : (projectile instanceof Projectile p && p.getOwner() instanceof ServerPlayer o ? o : null);
         Track track = projectile == null ? null : TRACKS.get(projectile.getId());
         if (!wants(shooter) && (track == null || !track.predicted)) {
            return null;
         }
         Vec3 v = projectile != null ? projectile.getDeltaMovement() : Vec3.ZERO;
         if (v.lengthSqr() < 1.0E-6 && source.getSourcePosition() != null) {
            v = point.subtract(source.getSourcePosition());
         }
         if (v.lengthSqr() < 1.0E-6 && shooter != null) {
            v = point.subtract(shooter.getEyePosition());
         }
         Vec3 dir = v.lengthSqr() < 1.0E-8 ? deer.getLookAngle().scale(-1.0) : v.normalize();
         if (!finite(dir) || !finite(point)) {
            return null; // [bugs] broken projectile state: no replay, the hit itself is untouched
         }
         DeerAnatomy.Contact c = DeerAnatomy.intersect(point.subtract(dir.scale(1.5)), point.add(dir.scale(1.5)), deer);
         byte organ = (byte)(c != null && c.organ() != null && c.region() == region ? c.organ().ordinal() : organFor(region));
         byte weapon = track != null ? track.weapon : weapon(projectile);
         Vec3 exit = null;
         if (weapon != KillCamNetwork.ARROW && weapon != KillCamNetwork.BOLT && energy >= 0.3F) {
            exit = DeerAnatomy.exit(deer, point, dir, 3.0);
         }
         return snapshot(deer, shooter, projectile, point, dir, exit, (byte)region.ordinal(), organ);
      } catch (RuntimeException ex) {
         LogUtils.getLogger().warn("Frontier Hunts kill cam: hit snapshot skipped", ex);
         return null;
      }
   }

   /** [killcam] hook: called when {@code projectileHit} returns. Confirms only a hit that actually dropped the animal. */
   public static void afterDeerHit(Object token, boolean accepted) {
      if (!(token instanceof Pending p)) {
         return;
      }
      try {
         Whitetail deer = (Whitetail)p.target;
         Track track = p.projectile == null ? null : TRACKS.get(p.projectile.getId());
         if (accepted && deer.downed() && com.formaworks.frontierhunts.vital.ShotVitals.deerDrops(p.region)) { // [vital] heart / lung only
            confirm(p, track);
         } else if (track != null) {
            finish(track);
         }
         if (track != null) {
            track.resolved = true;
         }
      } catch (RuntimeException ex) {
         LogUtils.getLogger().warn("Frontier Hunts kill cam: confirm skipped", ex);
      }
   }

   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void death(LivingDeathEvent event) {
      if (event.isCanceled() || !(event.getEntity() instanceof WildlifeMob mob) || !(mob.level() instanceof ServerLevel)) {
         return;
      }
      DamageSource source = event.getSource();
      if (!(source.getDirectEntity() instanceof Projectile projectile)) {
         return;
      }
      // [vital] only a heart / lung hit kills a wildlife animal on the spot; anything else (bleed-out etc.) gets no kill cam
      com.formaworks.frontierhunts.vital.WildlifeVitals.Hit vital = com.formaworks.frontierhunts.vital.ShotVitals.lastHit(mob);
      if (vital == null || !vital.drops()) {
         return;
      }
      try {
         ServerPlayer shooter = source.getEntity() instanceof ServerPlayer sp ? sp : (projectile.getOwner() instanceof ServerPlayer o ? o : null);
         Track track = TRACKS.get(projectile.getId());
         if (!wants(shooter) && (track == null || !track.predicted)) {
            return;
         }
         Vec3 v = projectile.getDeltaMovement();
         Vec3 dir = !(v.lengthSqr() >= 1.0E-8) || !finite(v) // [bugs] NaN velocity counts as "no velocity"
            ? mob.position().subtract(shooter == null ? projectile.position() : shooter.getEyePosition()).normalize()
            : v.normalize();
         Vec3 point = projectile.position();
         if (!finite(dir) || !finite(point) || !finite(mob.position())) {
            return; // [bugs] nothing sane to replay
         }
         AABB box = mob.getBoundingBox().inflate(0.05);
         if (!box.contains(point) || point.distanceToSqr(box.getCenter()) > 9.0) {
            Optional<Vec3> hit = box.clip(point.subtract(dir.scale(6.0)), point.add(dir.scale(6.0)));
            point = hit.orElse(box.getCenter());
         }
         if (vital.entry() != null && vital.entry().distanceToSqr(box.getCenter()) < 9.0) {
            point = vital.entry(); // [vital] where it actually entered the body
         }
         byte weapon = track != null ? track.weapon : weapon(projectile);
         Vec3 exit = weapon == KillCamNetwork.ARROW || weapon == KillCamNetwork.BOLT ? null : boxExit(mob, point, dir);
         Pending p = snapshot(mob, shooter, projectile, point, dir, exit, (byte)vital.zone().region().ordinal(), (byte)-1); // [vital] card names the organ
         confirm(p, track);
         if (track != null) {
            track.resolved = true;
         }
      } catch (RuntimeException ex) {
         LogUtils.getLogger().warn("Frontier Hunts kill cam: wildlife confirm skipped", ex);
      }
   }

   // ------------------------------------------------------------------ helpers

   private static boolean finite(Vec3 v) {
      return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
   }

   private static Vec3 boxExit(LivingEntity e, Vec3 entry, Vec3 dir) {
      AABB box = e.getBoundingBox();
      Vec3 far = entry.add(dir.scale(box.getSize() * 2.0 + 1.0));
      Optional<Vec3> back = box.clip(far, entry.add(dir.scale(0.02)));
      return back.filter(x -> x.distanceToSqr(entry) > 0.01).orElse(null);
   }

   private static int organFor(DeerAnatomy.Region region) {
      if (region == null) {
         return -1;
      }
      return switch (region) {
         case HEART -> DeerOrgan.HEART.ordinal();
         case LUNG, DOUBLE_LUNG, CHEST -> DeerOrgan.LUNGS.ordinal();
         case SPINE -> DeerOrgan.SPINE.ordinal();
         case BRAIN, HEAD -> DeerOrgan.SKULL.ordinal();
         case LIVER -> DeerOrgan.LIVER.ordinal();
         case GUT -> DeerOrgan.RUMEN.ordinal();
         case SHOULDER -> DeerOrgan.SHOULDER.ordinal();
         case LEG -> DeerOrgan.LEG_BONES.ordinal();
         default -> -1;
      };
   }

   private static Pending snapshot(LivingEntity target, ServerPlayer shooter, Entity projectile, Vec3 impact, Vec3 dir, Vec3 exit, byte region, byte organ) {
      Vec3 vel = new Vec3(target.getX() - target.xo, target.getY() - target.yo, target.getZ() - target.zo);
      if (!Double.isFinite(vel.lengthSqr()) || vel.lengthSqr() > 4.0) {
         vel = Vec3.ZERO;
      }
      return new Pending(target, shooter, projectile, impact, dir, exit, region, organ, target.position(), vel, target.yBodyRot, target.yHeadRot, target.getXRot());
   }

   private static void confirm(Pending p, Track track) {
      ServerPlayer shooter = p.shooter;
      if (shooter == null && track != null) {
         shooter = track.level.getServer().getPlayerList().getPlayer(track.shooter);
      }
      if (shooter == null || shooter.level() != p.target.level() || p.target instanceof Player || !KillCamPredictor.eligible(p.target)) {
         return;
      }
      if (!wants(shooter) || mode(shooter) == KillCamMode.TROPHY && !trophy(p.target)) {
         if (track != null) {
            finish(track);
         }
         return;
      }
      Vec3 origin = track != null ? track.origin : shooter.getEyePosition();
      double distance = origin.distanceTo(p.impact);
      // [bugs] NaN-safe range test (a NaN distance used to pass both comparisons) and no non-finite path to the client
      if (!(distance >= MIN_DISTANCE && distance <= KillCamPredictor.MAX_RANGE) || !finite(p.dir) || p.dir.lengthSqr() < 1.0E-8
         || p.exit != null && !finite(p.exit)) {
         if (track != null) {
            finish(track);
         }
         return;
      }
      List<Vec3> samples = new ArrayList<>(track != null ? track.samples : List.of(origin));
      samples.removeIf(v -> !finite(v)); // [bugs]
      if (samples.isEmpty()) {
         samples.add(origin);
      }
      // drop samples that went past the impact along the flight line (projectile position is set to the hit point)
      while (samples.size() > 1 && samples.get(samples.size() - 1).subtract(p.impact).dot(p.dir) > -0.01) {
         samples.remove(samples.size() - 1);
      }
      samples.add(p.impact);
      int ticks = track != null ? (int)Math.max(1L, p.target.level().getGameTime() - track.born + 1L) : Math.max(1, (int)Math.round(distance / 30.0));
      int id = track != null && track.predicted ? track.shotId : nextShot++;
      if (track != null) {
         track.shotId = id;
         track.resolved = true;
      }
      send(shooter, id, true, track, p, samples, ticks);
   }

   private static void send(ServerPlayer shooter, int id, boolean confirmed, Track track, Pending p, List<Vec3> samples, int ticks) {
      Vec3 origin = track != null ? track.origin : samples.get(0);
      List<Vec3> path = samples;
      if (path.size() > KillCamNetwork.MAX_PATH) {
         List<Vec3> thin = new ArrayList<>(KillCamNetwork.MAX_PATH);
         for (int i = 0; i < KillCamNetwork.MAX_PATH - 1; i++) {
            thin.add(path.get((int)Math.round((double)i * (path.size() - 1) / (KillCamNetwork.MAX_PATH - 1))));
         }
         thin.add(path.get(path.size() - 1));
         path = thin;
      }
      float[] rel = new float[path.size() * 3];
      for (int i = 0; i < path.size(); i++) {
         Vec3 v = path.get(i).subtract(origin);
         rel[i * 3] = (float)v.x;
         rel[i * 3 + 1] = (float)v.y;
         rel[i * 3 + 2] = (float)v.z;
      }
      // [killcam2] a target beyond the shooter's entity tracking range was never sent to the client: send what it
      // looks like (first, so it is there when the Shot arrives) and the client films a stand-in
      if (shooter.distanceToSqr(p.target) > 48.0 * 48.0) {
         try {
            CompoundTag data = new CompoundTag();
            p.target.saveWithoutId(data);
            data.remove("Passengers");
            data.remove("Brain");
            ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(p.target.getType());
            if (data.sizeInBytes() < 200000) {
               KillCamNetwork.send(shooter, new KillCamNetwork.Appearance(p.target.getId(), type, data));
            }
         } catch (RuntimeException ignored) {
            // no stand-in: the client skips a shot whose target it cannot see, as before
         }
      }
      KillCamNetwork.send(
         shooter,
         new KillCamNetwork.Shot(
            id, confirmed, track != null ? track.weapon : weapon(p.projectile), track != null ? track.tip : -1, track != null && track.primitive,
            p.target.getId(), trophy(p.target), p.region, p.organ, (float)origin.distanceTo(p.impact), ticks, origin, rel, p.impact, p.dir, p.exit,
            p.pose, p.poseVel, p.bodyYaw, p.headYaw, p.pitch
         )
      );
   }

   @SubscribeEvent
   public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
      PREFS.remove(event.getEntity().getUUID());
      PREFS_AT.remove(event.getEntity().getUUID());
   }

   @SubscribeEvent
   public static void stopped(ServerStoppedEvent event) {
      TRACKS.clear();
      PREFS.clear();
      PREFS_AT.clear();
   }
}
