package com.formaworks.frontierhunts.hunting.routine;

import com.formaworks.frontierhunts.expedition.ExpeditionWeapon;
import com.formaworks.frontierhunts.expedition.HuntProjectile;
import com.formaworks.frontierhunts.expedition.Weapon;
import com.formaworks.frontierhunts.hunting.FieldArrow;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Feeds the {@link PressureStore}: gunshots and bow shots (where the hunter stands), hits and kills on game animals
 * (where the animal was), explosions, and animals a hunter busted ({@link #spooked}). Mirrors the projectile set that
 * WhitetailHearing uses for its shot broadcast, without touching that class.
 */
@EventBusSubscriber(modid = "frontierhunts")
public final class PressureEvents {
   private static long lastShotTick = Long.MIN_VALUE;
   private static Vec3 lastShotAt = Vec3.ZERO;

   private PressureEvents() {
   }

   public static void add(ServerLevel level, Vec3 at, double units) {
      if (RoutineConfig.pressure() && level != null && at != null && units > 0.0) {
         PressureStore.of(level).add(level.getGameTime(), at, (float)units);
      }
   }

   public static float units(ServerLevel level, Vec3 at) {
      return !RoutineConfig.pressure() || level == null ? 0.0F : PressureStore.of(level).value(level.getGameTime(), PressureStore.cell(at.x), PressureStore.cell(at.z));
   }

   @SubscribeEvent
   public static void shot(EntityJoinLevelEvent event) {
      if (event.getLevel() instanceof ServerLevel level && !event.loadedFromDisk() && RoutineConfig.pressure()) {
         Entity e = event.getEntity();
         double units;
         Entity owner = e instanceof Projectile p ? p.getOwner() : null;
         if (e instanceof FieldArrow) {
            units = 0.35;
         } else {
            if (!(e instanceof HuntProjectile hp)) {
               return;
            }

            Weapon w = hp.kind();
            if (w == null || w == Weapon.BAIT_LAUNCHER) {
               return;
            }

            if (w.bow) {
               units = 0.35;
            } else if (w == Weapon.TRANQUILIZER_RIFLE) {
               units = 0.3;
            } else if (w == Weapon.FLARE_GUN) {
               units = 0.6;
            } else {
               units = 1.0;
               if (owner instanceof LivingEntity le && ExpeditionWeapon.attachment(le.getMainHandItem(), "suppressor")) {
                  units = 0.6;
               }
            }
         }

         if (!(owner instanceof Player player) || player.isCreative() || player.isSpectator()) {
            return;
         }

         Vec3 at = player.position();
         long now = level.getGameTime();
         // shotgun pellets and multi-projectile volleys are one shot
         if (now == lastShotTick && at.distanceToSqr(lastShotAt) < 4.0) {
            return;
         }

         lastShotTick = now;
         lastShotAt = at;
         add(level, at, units * RoutineConfig.shotWeight());
      }
   }

   @SubscribeEvent
   public static void explosion(ExplosionEvent.Detonate event) {
      if (event.getLevel() instanceof ServerLevel level) {
         add(level, event.getExplosion().center(), 1.5 * RoutineConfig.shotWeight());
      }
   }

   @SubscribeEvent
   public static void hit(LivingDamageEvent.Post event) {
      LivingEntity victim = event.getEntity();
      if (victim.level() instanceof ServerLevel level
         && (victim instanceof Whitetail || victim instanceof WildlifeMob)
         && event.getSource().getEntity() instanceof Player player
         && !player.isCreative()
         && !player.isSpectator()
         && event.getNewDamage() > 0.0F) {
         boolean kill = victim.isDeadOrDying() || victim instanceof Whitetail deer && deer.downed();
         add(level, victim.position(), (kill ? 3.0 : 1.5) * RoutineConfig.shotWeight());
      }
   }

   /** An animal broke and ran from a hunter: light pressure, at most once per animal per 30 s. */
   public static void spooked(LivingEntity animal, Vec3 threat) {
      if (animal.level() instanceof ServerLevel level && threat != null && RoutineConfig.pressure()) {
         long now = level.getGameTime();
         long last = animal.getPersistentData().getLong("fh_spook_at");
         if (now - last >= 600L || now < last) {
            Player p = level.getNearestPlayer(threat.x, threat.y, threat.z, 48.0, false);
            if (p != null && !p.isCreative() && !p.isSpectator()) {
               animal.getPersistentData().putLong("fh_spook_at", now);
               add(level, animal.position(), 0.25 * RoutineConfig.shotWeight());
            }
         }
      }
   }

   @SubscribeEvent
   public static void housekeeping(LevelTickEvent.Post event) {
      if (event.getLevel() instanceof ServerLevel level && level.getGameTime() % 6000L == 17L) {
         PressureStore.of(level).housekeeping(level.getGameTime());
         RoutineStore.of(level).housekeeping(level.getGameTime());
      }
   }
}
