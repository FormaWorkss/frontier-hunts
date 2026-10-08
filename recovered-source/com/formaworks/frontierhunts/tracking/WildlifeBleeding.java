package com.formaworks.frontierhunts.tracking;

import com.formaworks.frontierhunts.tracking.BloodTrail.BloodType;
import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * [tracking] The 2026 wildlife bleed too. They have no organ anatomy, so the hit is placed on the body from where the
 * projectile struck (front-high: lungs/heart, middle: liver/gut, low or rear: flesh) and bleeds like a deer's wound
 * of that kind: same blood types, cadence and taper. Vital wounds keep costing health until the animal drops.
 */
@EventBusSubscriber(modid = "frontierhunts")
public final class WildlifeBleeding {
   private static final String KEY = "frontierhunts_wound";
   private static final Map<LivingEntity, Wound> WOUNDS = new WeakHashMap<>();

   private static final class Wound {
      BloodType type = BloodType.GENERIC;
      int ticks;
      int initial;
      float loss;
      int steps;
      double lx = Double.NaN, lz;
      long lastDrop;

      CompoundTag save() {
         CompoundTag t = new CompoundTag();
         t.putByte("type", (byte)this.type.ordinal());
         t.putInt("ticks", this.ticks);
         t.putInt("initial", this.initial);
         t.putFloat("loss", this.loss);
         return t;
      }

      static Wound load(CompoundTag t) {
         Wound w = new Wound();
         w.type = BloodType.of(t.getByte("type"));
         w.ticks = Math.clamp(t.getInt("ticks"), 0, 6000);
         w.initial = Math.clamp(t.getInt("initial"), w.ticks, 6000);
         w.loss = Math.clamp(t.getFloat("loss"), 0.0F, 40.0F);
         return w;
      }
   }

   private WildlifeBleeding() {
   }

   public static BloodType type(LivingEntity e) {
      Wound w = WOUNDS.get(e);
      return w == null ? null : w.type;
   }

   /** where on the body a hit landed */
   static BloodType classify(WildlifeMob m, DamageSource source) {
      // [vital] a projectile hit is traced through the animal's real anatomy (heart/lung/liver/gut/flesh...)
      BloodType traced = com.formaworks.frontierhunts.vital.ShotVitals.blood(m);
      if (traced != null) {
         return traced;
      }
      if (m.species.bird) {
         return BloodType.GENERIC;
      }
      Entity direct = source.getDirectEntity();
      Entity attacker = source.getEntity();
      if (direct == null || direct == attacker) {
         // melee or hitscan without a projectile: judge by the attacker's height against the body, else flesh
         if (direct == null && attacker == null) {
            return BloodType.GENERIC;
         }
         return m.getRandom().nextFloat() < 0.6F ? BloodType.MUSCLE : BloodType.GENERIC;
      }
      Vec3 rel = direct.position().subtract(m.position());
      double yaw = Math.toRadians(m.yBodyRot);
      double fwd = -Math.sin(yaw) * rel.x + Math.cos(yaw) * rel.z;
      double len = Math.max(0.6, m.getBbWidth() * 1.7);
      double along = fwd / len;
      double h = rel.y / Math.max(0.3, m.getBbHeight());
      if (h < 0.32) {
         return BloodType.MUSCLE;
      }
      if (along > 0.42 && h > 0.72) {
         return BloodType.ARTERIAL;
      }
      if (along > 0.14) {
         return h < 0.58 && m.getRandom().nextFloat() < 0.4F ? BloodType.HEART : BloodType.LUNG;
      }
      if (along > -0.05) {
         return BloodType.LIVER;
      }
      if (along > -0.28) {
         return BloodType.GUT;
      }
      return BloodType.MUSCLE;
   }

   /**
    * [vital] An animal hit again when already badly hurt: whatever the wound, it bleeds out within {@code ticks}
    * (a short run, not a drop on the spot).
    */
   public static void hasten(WildlifeMob m, int ticks) {
      Wound w = WOUNDS.get(m);
      if (w == null) {
         w = new Wound();
         w.type = BloodType.MUSCLE;
         WOUNDS.put(m, w);
      }
      int t = Math.clamp(ticks, 80, 1200);
      w.ticks = Math.max(w.ticks, t);
      w.initial = Math.max(w.initial, w.ticks);
      w.loss = Math.max(w.loss, m.getMaxHealth() * 40.0F / (t - 40.0F));
      m.getPersistentData().put(KEY, w.save());
   }

   private static int severity(BloodType t) {
      return switch (t) {
         case HEART -> 6;
         case ARTERIAL -> 5;
         case LUNG -> 4;
         case LIVER -> 3;
         case GUT -> 2;
         case MUSCLE -> 1;
         default -> 0;
      };
   }

   @SubscribeEvent
   public static void damaged(LivingDamageEvent.Post e) {
      if (!(e.getEntity() instanceof WildlifeMob m) || !(m.level() instanceof ServerLevel level) || e.getNewDamage() <= 0.0F) {
         return;
      }
      DamageSource src = e.getSource();
      if (src.getEntity() == null && src.getDirectEntity() == null) {
         return; // our own bleed-out, falls, fire ...
      }
      if (src.is(net.minecraft.tags.DamageTypeTags.IS_FIRE) || src.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) {
         return;
      }
      BloodType t = classify(m, src);
      Wound w = WOUNDS.get(m);
      if (w == null) {
         w = new Wound();
         WOUNDS.put(m, w);
      }
      if (severity(t) >= severity(w.type)) {
         w.type = t;
      }
      float hp = m.getMaxHealth();
      int dur = switch (w.type) {
         case HEART -> 150;
         case ARTERIAL -> 300;
         case LUNG -> 420;
         case LIVER -> 900;
         case GUT -> 2400;
         case MUSCLE -> 700;
         default -> 400;
      };
      float loss = switch (w.type) {
         case HEART -> hp / 3.0F;
         case ARTERIAL -> hp / 6.0F;
         case LUNG -> hp / 8.0F;
         case LIVER -> hp / 18.0F;
         case GUT -> hp / 50.0F;
         default -> 0.0F;
      };
      w.ticks = Math.max(w.ticks, dur);
      w.initial = Math.max(w.initial, w.ticks);
      w.loss = Math.max(w.loss, loss);
      m.getPersistentData().put(KEY, w.save());
      Vec3 p = woundPos(m, w);
      TrailService.blood(m, p, true);
      if (w.type == BloodType.HEART || w.type == BloodType.LUNG) {
         Vec3 c = m.position();
         TrailService.mark(m, new Vec3(2.0 * c.x - p.x, p.y, 2.0 * c.z - p.z), TrailMark.IMPACT, w.type);
      }
   }

   private static Vec3 woundPos(WildlifeMob m, Wound w) {
      double h = switch (w.type) {
         case MUSCLE -> 0.3;
         case GUT, LIVER -> 0.5;
         case ARTERIAL -> 0.8;
         default -> 0.62;
      };
      Vec3 side = BloodTrail.side(m).scale(m.getBbWidth() * 0.45);
      return m.position().add(side).add(0.0, m.getBbHeight() * h, 0.0);
   }

   /** every 4th tick from TrackPrints (server) */
   static void tick(WildlifeMob m, ServerLevel level) {
      Wound w = WOUNDS.get(m);
      if (w == null || !m.isAlive()) {
         return;
      }
      w.ticks -= 4;
      if (w.ticks <= 0) {
         WOUNDS.remove(m);
         m.getPersistentData().remove(KEY);
         return;
      }
      long now = level.getGameTime();
      if (w.loss > 0.0F && m.tickCount % 40 < 4) {
         m.hurt(level.damageSources().generic(), w.loss);
         if (!m.isAlive()) {
            return;
         }
      }
      if (m.tickCount % 40 < 4) {
         m.getPersistentData().put(KEY, w.save());
      }
      if (m.isInWaterOrBubble()) {
         return;
      }
      boolean moved = Double.isNaN(w.lx) || (m.getX() - w.lx) * (m.getX() - w.lx) + (m.getZ() - w.lz) * (m.getZ() - w.lz) > 1.44;
      if (!moved && now - w.lastDrop < 60L) {
         return;
      }
      w.lx = m.getX();
      w.lz = m.getZ();
      w.lastDrop = now;
      w.steps++;
      float f = w.initial <= 0 ? 0.0F : Math.clamp(1.0F - (float)w.ticks / w.initial, 0.0F, 1.0F);
      Vec3 p = woundPos(m, w);
      Vec3 side = BloodTrail.side(m);
      switch (w.type) {
         case HEART, ARTERIAL -> {
            double s = m.getBbWidth() * 0.5 + 0.1;
            TrailService.mark(m, m.position().add(side.scale(s)).add(0, p.y - m.getY(), 0), TrailMark.DENSE, w.type);
            TrailService.mark(m, m.position().add(side.scale(-s)).add(0, p.y - m.getY(), 0), TrailMark.DENSE, w.type);
            TrailService.brushAt(m, p, w.type);
         }
         case LUNG -> {
            TrailService.mark(m, p, f < 0.35F ? TrailMark.DENSE : TrailMark.DRIP, w.type);
            if (f < 0.6F) {
               TrailService.brushAt(m, p, w.type);
            }
         }
         case LIVER -> TrailService.mark(m, p, w.steps % 5 == 0 ? TrailMark.DENSE : TrailMark.DRIP, w.type);
         case GUT -> {
            if (w.steps % 3 == 0) {
               TrailService.mark(m, p, TrailMark.DRIP, w.type);
            }
         }
         case MUSCLE -> {
            if (m.getRandom().nextFloat() < Math.clamp(1.0F - f / 0.55F, 0.0F, 1.0F)) {
               TrailService.mark(m, p, TrailMark.DRIP, w.type);
            }
         }
         default -> TrailService.mark(m, p, TrailMark.DRIP, w.type);
      }
   }

   @SubscribeEvent
   public static void died(LivingDeathEvent e) {
      if (!(e.getEntity() instanceof WildlifeMob m) || !(m.level() instanceof ServerLevel level)) {
         return;
      }
      ScentLedger.get(level).end(m.getUUID(), m.position(), level.getGameTime());
      Wound w = WOUNDS.get(m);
      if (w != null || e.getSource().getEntity() != null) {
         if (w == null) {
            w = new Wound();
            WOUNDS.put(m, w);
         }
         TrailService.pool(m, m.position().add(0.0, m.getBbHeight() * 0.4, 0.0), 0);
      }
   }

   @SubscribeEvent
   public static void joined(EntityJoinLevelEvent e) {
      if (!e.getLevel().isClientSide() && e.getEntity() instanceof WildlifeMob m && m.getPersistentData().contains(KEY)) {
         Wound w = Wound.load(m.getPersistentData().getCompound(KEY));
         if (w.ticks > 0) {
            WOUNDS.put(m, w);
         }
      }
   }
}
