package com.formaworks.frontierhunts.campcook;

import com.formaworks.frontierhunts.FrontierHunts;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * [licence] Camp-meal buffs: modest, themed, one at a time. A camp meal replaces any other camp-meal buff and never
 * extends one past the dish's own duration (vanilla keeps the longer of two equal effects), so eating three stews gives
 * eight minutes of warmth, not twenty-four. The effects are real mob effects (inventory icon, timer, synced, cleared by
 * milk) and act through small hooks in the systems they touch:
 * <ul>
 * <li>{@link Buff#WARMTH} Camp Warmth: body heat lost 30 % slower (Frontier Survival cold);</li>
 * <li>{@link Buff#STAMINA} Trail Stamina: +4 % movement speed, 20 % less hunger from exertion;</li>
 * <li>{@link Buff#STEADY} Steady Aim: scope and bow sway and bow hold strain x0.75;</li>
 * <li>{@link Buff#HEARTY} Hearty: heals half a heart every 12 s;</li>
 * <li>{@link Buff#KEEN} Keen Tracker: tracks and blood seen from 30 % further;</li>
 * <li>{@link Buff#QUIET} Quiet Step: deer hear you 20 % closer, wildlife alarm radius x0.9;</li>
 * <li>{@link Buff#MASKED} Smoke-Masked: your scent 20 % weaker.</li>
 * </ul>
 */
@EventBusSubscriber(modid = FrontierHunts.ID)
public final class MealBuffs {
   public enum Buff {
      WARMTH(0xFFE07A3A),
      STAMINA(0xFFD8B04A),
      STEADY(0xFF6A8AB0),
      HEARTY(0xFFC0404A),
      KEEN(0xFF5AA05A),
      QUIET(0xFF8A7AA8),
      MASKED(0xFF8A8A7A);

      public final int color;

      Buff(int color) {
         this.color = color;
      }

      public String id() {
         return "meal_" + this.name().toLowerCase(Locale.ROOT);
      }

      public Holder<MobEffect> effect() {
         return CampCookContent.EFFECTS.get(this);
      }
   }

   /** Steady Aim for the local player (set by the client tick; 1 on a server). */
   public static volatile float clientAim = 1.0F;
   private static final Map<UUID, Float> LAST_EXHAUSTION = new HashMap<>();

   private MealBuffs() {
   }

   /** The camp-meal buff this entity has, or null. */
   public static Buff active(LivingEntity e) {
      if (e == null) {
         return null;
      }
      for (Buff b : Buff.values()) {
         Holder<MobEffect> h = CampCookContent.EFFECTS.get(b);
         if (h != null && e.hasEffect(h)) {
            return b;
         }
      }
      return null;
   }

   static boolean has(LivingEntity e, Buff b) {
      Holder<MobEffect> h = CampCookContent.EFFECTS.get(b);
      return e != null && h != null && e.hasEffect(h);
   }

   /** Eat a dish: replace any other camp-meal buff with this one. */
   public static void apply(LivingEntity e, Dish dish) {
      for (Buff b : Buff.values()) {
         if (b != dish.buff && has(e, b)) {
            e.removeEffect(CampCookContent.EFFECTS.get(b));
         }
      }
      e.addEffect(new MobEffectInstance(dish.buff.effect(), dish.ticks(), 0, false, true, true));
   }

   // ============================================================================================ hooks (both sides)

   /** Survival cold: multiplier on body heat loss. */
   public static float cold(Player p) {
      return has(p, Buff.WARMTH) ? 0.7F : 1.0F;
   }

   /** Bow hold strain (BowHold.strain). */
   public static float aim(LivingEntity p) {
      return has(p, Buff.STEADY) ? 0.75F : 1.0F;
   }

   /** Deer hearing distance (HunterSkills.hearing). */
   public static double hearing(Player p) {
      return has(p, Buff.QUIET) ? 0.8 : 1.0;
   }

   /** Wildlife alarm radius (HunterSkills.wildlifeAlarm). */
   public static double alarm(Player p) {
      return has(p, Buff.QUIET) ? 0.9 : 1.0;
   }

   /** Scent strength (HunterSkills.scent). */
   public static double scent(Player p) {
      return has(p, Buff.MASKED) ? 0.8 : 1.0;
   }

   /** Sign view radius (HunterSkills.signRadius). */
   public static double signRadius(Player p) {
      return has(p, Buff.KEEN) ? 1.3 : 1.0;
   }

   // ============================================================================================ Trail Stamina

   /** Trail Stamina: a fifth of the exhaustion gained this tick is given back. */
   @SubscribeEvent
   public static void tick(PlayerTickEvent.Post e) {
      if (!(e.getEntity() instanceof ServerPlayer p)) {
         return;
      }
      if (!has(p, Buff.STAMINA)) {
         if (!LAST_EXHAUSTION.isEmpty()) {
            LAST_EXHAUSTION.remove(p.getUUID());
         }
         return;
      }
      FoodData f = p.getFoodData();
      float now = f.getExhaustionLevel();
      Float last = LAST_EXHAUSTION.put(p.getUUID(), now);
      if (last != null && now > last) {
         float back = (now - last) * 0.2F;
         f.setExhaustion(now - back);
         LAST_EXHAUSTION.put(p.getUUID(), now - back);
      }
   }

   @SubscribeEvent
   public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
      LAST_EXHAUSTION.remove(e.getEntity().getUUID());
   }
}
