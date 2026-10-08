package com.formaworks.frontierhunts.killcam;

import com.formaworks.frontierhunts.Wilderness;
import com.formaworks.frontierhunts.expedition.FirearmDamage;
import com.formaworks.frontierhunts.hunting.ArrowTip;
import com.formaworks.frontierhunts.hunting.DeerAnatomy;
import com.formaworks.frontierhunts.hunting.DeerOrgan;
import com.formaworks.frontierhunts.hunting.FieldArrow;
import com.formaworks.frontierhunts.hunting.TrackClue;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.rifle.RifleBullet;
import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side, zero side effect look-ahead of the mod's own projectiles: runs the same per-tick integration and the same
 * skinned-anatomy intersection the real {@link RifleBullet} / {@link FieldArrow} tick uses, against the animals' current
 * pose, so a lethal shot can be announced the tick it is fired. It is only a hint: the replay can only finish on the
 * real hit result.
 */
final class KillCamPredictor {
   static final double MAX_RANGE = 640.0; // [killcam2] long shots (was 420)

   private KillCamPredictor() {
   }

   record Result(Entity target, DeerAnatomy.Region region, DeerOrgan organ, Vec3 impact, Vec3 velocity, float energy, int ticks, List<Vec3> samples, boolean lethal) {
   }

   static Result predict(ServerLevel level, Projectile projectile) {
      boolean rifle = projectile instanceof RifleBullet;
      if (!rifle && !(projectile instanceof FieldArrow)) {
         return null;
      }
      ArrowTip tip = projectile instanceof FieldArrow arrow ? arrow.tip() : null;
      Entity owner = projectile.getOwner();
      Vec3 launch = projectile.position();
      Vec3 pos = launch;
      Vec3 vel = projectile.getDeltaMovement();
      if (!Double.isFinite(vel.lengthSqr()) || vel.lengthSqr() < 1.0E-6) {
         return null;
      }
      List<Vec3> samples = new ArrayList<>();
      samples.add(pos);
      int maxTicks = rifle ? 80 : 120;
      double inflate = rifle ? 1.3 : 4.0;
      long time = level.getGameTime();
      for (int tick = 1; tick <= maxTicks; tick++) {
         Vec3 to = pos.add(vel);
         if (!Double.isFinite(to.lengthSqr()) || vel.lengthSqr() > 10000.0 || to.distanceTo(launch) > MAX_RANGE) {
            return null;
         }
         if (!level.hasChunkAt(BlockPos.containing(pos)) || !level.hasChunkAt(BlockPos.containing(to))) {
            return null;
         }
         BlockHitResult block = level.clip(new ClipContext(pos, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, projectile));
         if (block.getType() != HitResult.Type.MISS) {
            to = block.getLocation();
         }
         Entity best = null;
         Vec3 bestPoint = null;
         DeerAnatomy.Contact bestContact = null;
         double bestDist = pos.distanceToSqr(to);
         final Vec3 from = pos;
         for (Entity e : level.getEntities(projectile, new AABB(pos, to).inflate(inflate), e -> e != owner && e.canBeHitByProjectile() && !(e instanceof TrackClue))) {
            if (e instanceof Player p && owner instanceof Player o && !o.canHarmPlayer(p)) {
               continue;
            }
            Vec3 point;
            DeerAnatomy.Contact contact = null;
            if (e instanceof Whitetail deer) {
               if (deer.downed()) {
                  continue;
               }
               contact = DeerAnatomy.intersect(from, to, deer);
               if (contact == null) {
                  continue;
               }
               point = from.lerp(to, contact.fraction());
            } else {
               Optional<Vec3> clip = e.getBoundingBox().inflate(rifle ? 0.008 : 0.03).clip(from, to);
               if (clip.isEmpty()) {
                  continue;
               }
               point = clip.get();
            }
            double d = from.distanceToSqr(point);
            if (d < bestDist) {
               bestDist = d;
               best = e;
               bestPoint = point;
               bestContact = contact;
            }
         }
         if (best != null) {
            samples.add(bestPoint);
            float energy = rifle ? (float)Math.clamp(vel.lengthSqr() / 1444.0, 0.05, 1.2) : (float)Math.clamp(vel.lengthSqr() / 7.5625, 0.04, 1.2);
            return outcome(best, bestContact, bestPoint, vel, energy, tip, rifle, tick, samples);
         }
         if (block.getType() != HitResult.Type.MISS) {
            return null;
         }
         pos = to;
         samples.add(pos);
         boolean water = !level.getFluidState(BlockPos.containing(pos)).isEmpty();
         Wilderness.Wind wind = Wilderness.wind(level.getSeed(), time + tick, level.isRaining(), level.isThundering());
         vel = rifle ? RifleBullet.integrate(vel, wind, water) : FieldArrow.integrate(vel, wind, water);
      }
      return null;
   }

   private static Result outcome(Entity target, DeerAnatomy.Contact contact, Vec3 point, Vec3 vel, float energy, ArrowTip tip, boolean rifle, int ticks, List<Vec3> samples) {
      if (target instanceof Whitetail) {
         DeerAnatomy.Region region = contact.region();
         float e = energy;
         if (tip != null) {
            e = Math.clamp(e * tip.penetration, 0.01F, 1.2F);
            if (!tip.lethal) {
               return new Result(target, DeerAnatomy.Region.BODY, null, point, vel, e, ticks, samples, false);
            }
         }
         boolean lethal = com.formaworks.frontierhunts.vital.ShotVitals.deerDrops(region) && e >= 0.08F; // [vital] heart / either lung
         return new Result(target, region, contact.organ(), point, vel, e, ticks, samples, lethal);
      }
      if (target instanceof WildlifeMob mob) {
         // [vital] same anatomy trace and energy scale ShotVitals applies to the real hit: only heart / lungs drop it
         float e = rifle ? energy : Math.clamp(energy * (tip == null ? 1.0F : tip.penetration), 0.01F, 1.2F);
         com.formaworks.frontierhunts.vital.WildlifeVitals.Hit hit = com.formaworks.frontierhunts.vital.WildlifeVitals.classify(mob, point, vel, e, tip);
         DeerAnatomy.Region region = hit.drops() ? hit.zone().region() : null;
         return new Result(target, region, null, hit.entry() != null ? hit.entry() : point, vel, e, ticks, samples, hit.drops());
      }
      return new Result(target, null, null, point, vel, energy, ticks, samples, false);
   }

   static boolean eligible(Entity e) {
      return (e instanceof Whitetail || e instanceof WildlifeMob) && e instanceof LivingEntity
         && !(e instanceof WildlifeMob bird && bird.species.bird); // [wingshot] birds get the wing-shot moment instead of the kill cam
   }
}
