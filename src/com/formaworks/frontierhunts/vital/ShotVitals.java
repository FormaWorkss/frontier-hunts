package com.formaworks.frontierhunts.vital;

import com.formaworks.frontierhunts.expedition.FirearmDamage;
import com.formaworks.frontierhunts.expedition.HuntProjectile;
import com.formaworks.frontierhunts.hunting.ArrowTip;
import com.formaworks.frontierhunts.hunting.DeerAnatomy;
import com.formaworks.frontierhunts.hunting.FieldArrow;
import com.formaworks.frontierhunts.rifle.RifleBullet;
import com.formaworks.frontierhunts.tracking.BloodTrail.BloodType;
import com.formaworks.frontierhunts.tracking.WildlifeBleeding;
import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * [vital] The one shot-placement rule for every huntable animal (user decision):
 * <b>a heart or lung hit drops the animal on the spot and plays the kill cam; any other hit does not kill it on the
 * spot</b> — it runs, bleeds by its wound, and dies after the run if the wound is fatal or lives if it is not.
 * <ul>
 * <li>Deer / elk / moose: {@link #deerDrops(DeerAnatomy.Region)} is the drop test in {@code Whitetail.projectileHit}
 * (HEART, DOUBLE_LUNG, single LUNG); every other region is already capped below the animal's health there.</li>
 * <li>2026 wildlife: every projectile hit is traced through {@link WildlifeVitals}; heart / lungs => lethal damage,
 * anything else => damage capped so it survives the hit (bleeding by tracking's {@code WildlifeBleeding}, fatal
 * wounds bleed out, flesh and leg wounds heal). Melee and other non-projectile damage is untouched.</li>
 * </ul>
 * Server-side only; the client never decides anything.
 */
@EventBusSubscriber(modid = "frontierhunts")
public final class ShotVitals {
   /** same floor Whitetail uses for a vital hit */
   public static final float MIN_VITAL_ENERGY = 0.08F;
   private static final String HEAL = "frontierhunts_vital_heal";
   private static final Map<LivingEntity, Stamp> LAST = new WeakHashMap<>();

   private record Stamp(WildlifeVitals.Hit hit, long time) {
   }

   private ShotVitals() {
   }

   // ------------------------------------------------------------------------------------------------ deer

   /** Heart, both lungs or one lung: the deer drops on the spot (given a lethal tip and enough energy). */
   public static boolean deerDrops(DeerAnatomy.Region region) {
      return region == DeerAnatomy.Region.HEART || region == DeerAnatomy.Region.DOUBLE_LUNG || region == DeerAnatomy.Region.LUNG;
   }

   /** Same, for the region byte the kill cam carries. */
   public static boolean deerDrops(byte region) {
      DeerAnatomy.Region[] all = DeerAnatomy.Region.values();
      return region >= 0 && region < all.length && deerDrops(all[region]);
   }

   // ------------------------------------------------------------------------------------------------ wildlife

   /** Energy of a projectile at impact on the same 0..1.2 scale the deer hit code uses. */
   public static float energy(Entity projectile, float damage) {
      Vec3 v = projectile.getDeltaMovement();
      if (projectile instanceof RifleBullet) {
         return (float)Math.clamp(v.lengthSqr() / 1444.0, 0.05, 1.2);
      }
      if (projectile instanceof FieldArrow arrow) {
         return Math.clamp((float)Math.clamp(v.lengthSqr() / 7.5625, 0.04, 1.2) * arrow.tip().penetration, 0.01F, 1.2F);
      }
      float e = Math.clamp(damage / 18.0F, 0.05F, 1.2F);
      ArrowTip tip = projectile instanceof HuntProjectile hp ? hp.tip() : null;
      return tip == null ? e : Math.clamp(e * tip.penetration, 0.01F, 1.2F);
   }

   public static ArrowTip tip(Entity projectile) {
      if (projectile instanceof FieldArrow arrow) {
         return arrow.tip();
      }
      return projectile instanceof HuntProjectile hp ? hp.tip() : null;
   }

   /** The hit recorded for this animal during the current tick (the damage call that is running), or null. */
   public static WildlifeVitals.Hit lastHit(LivingEntity e) {
      Stamp s = LAST.get(e);
      return s != null && s.time == e.level().getGameTime() ? s.hit : null;
   }

   /** [tracking hook] blood type of the projectile hit being applied right now, or null (not a traced projectile hit) */
   public static BloodType blood(LivingEntity e) {
      WildlifeVitals.Hit h = lastHit(e);
      return h == null ? null : h.zone().blood();
   }

   /** Traces a projectile hit on a wildlife animal, or null if this isn't a real projectile hit. */
   static WildlifeVitals.Hit trace(WildlifeMob mob, DamageSource source, float damage) {
      if (!(source.getDirectEntity() instanceof Projectile projectile) || projectile == source.getEntity()) {
         return null;
      }
      if (source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.IS_FIRE) || FirearmDamage.hitId(source) == null) {
         return null; // blasts, flaming arrows' burn, tranquilizer darts, flares, bait, fishing hooks
      }
      Vec3 point = projectile.position();
      Vec3 dir = projectile.getDeltaMovement();
      Entity owner = source.getEntity() != null ? source.getEntity() : projectile.getOwner();
      if (!Double.isFinite(dir.lengthSqr()) || dir.lengthSqr() < 1.0E-6) {
         // a projectile that already stopped (or reports no velocity): the shooter's line to where it struck
         if (owner == null || owner.level() != mob.level()) {
            return new WildlifeVitals.Hit(WildlifeVitals.Zone.FLESH, null, 0.0F, false);
         }
         dir = point.subtract(owner.getEyePosition());
         if (dir.lengthSqr() < 1.0E-6) {
            dir = mob.getBoundingBox().getCenter().subtract(owner.getEyePosition());
         }
      }
      return WildlifeVitals.classify(mob, point, dir, energy(projectile, damage), tip(projectile));
   }

   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void damage(LivingDamageEvent.Pre e) {
      if (!(e.getEntity() instanceof WildlifeMob mob) || !(mob.level() instanceof ServerLevel level) || !mob.isAlive()) {
         return;
      }
      float dmg = e.getNewDamage();
      if (!(dmg > 0.0F) || !Float.isFinite(dmg)) {
         return;
      }
      WildlifeVitals.Hit hit;
      try {
         hit = trace(mob, e.getSource(), dmg);
      } catch (RuntimeException ex) {
         return; // never let the anatomy trace break a hit
      }
      if (hit == null) {
         return; // melee, falls, blasts ... unchanged
      }
      LAST.put(mob, new Stamp(hit, level.getGameTime()));
      float hp = mob.getHealth();
      if (hit.drops()) {
         // heart / lungs: down on the spot
         e.setNewDamage(Math.max(dmg, hp + mob.getAbsorptionAmount() + 1.0F));
         mob.getPersistentData().remove(HEAL);
         return;
      }
      // anything else: it takes the wound, not the life; it runs
      float max = mob.getMaxHealth();
      float capped = Math.min(dmg, max * hit.zone().impactShare());
      capped = Math.max(0.0F, Math.min(capped, hp - 1.0F));
      e.setNewDamage(capped);
      if (hit.zone() != WildlifeVitals.Zone.GRAZE && hp - capped <= max * 0.25F) {
         // an animal already this badly hurt doesn't survive another bullet: it goes down after a short run
         WildlifeBleeding.hasten(mob, 200);
         mob.getPersistentData().remove(HEAL);
      } else if (hit.zone().fatal()) {
         mob.getPersistentData().remove(HEAL);
      } else if (!mob.getPersistentData().contains(HEAL)) {
         mob.getPersistentData().putBoolean(HEAL, true);
      }
   }

   /** Flesh and leg wounds heal: 1 health every 10 s once the bleeding has stopped, only for animals that were shot. */
   @SubscribeEvent
   public static void tick(EntityTickEvent.Post e) {
      if (e.getEntity() instanceof WildlifeMob mob && (mob.tickCount + mob.getId()) % 200 == 0 && mob.level() instanceof ServerLevel && mob.isAlive()
         && mob.getPersistentData().getBoolean(HEAL)) {
         BloodType t = WildlifeBleeding.type(mob);
         if (t != null && t != BloodType.MUSCLE && t != BloodType.GENERIC) {
            return; // a fatal wound from another hit is bleeding: no healing
         }
         if (mob.getHealth() < mob.getMaxHealth()) {
            mob.heal(1.0F);
         } else {
            mob.getPersistentData().remove(HEAL);
         }
      }
   }
}
