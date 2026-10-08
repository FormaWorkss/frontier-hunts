package com.formaworks.frontierhunts.journal;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * [journal] Perk effects, queried from small hook lines in other systems (all marked {@code // [journal]}). Server
 * queries read a per-player active-perk mask kept here (filled at login and on every level-up), so the hot paths
 * (deer perception, every few ticks per deer) are one map lookup. Unknown players / perks off -> neutral 1.0.
 * The client copy of the owner's mask (for the rifle sway) is set by the client from the {@code Perks} payload.
 *
 * <p>Survival hooks for the {@code survival} workstream: {@link #coldExposure(Player)} and {@link #nutrition(Player)}.
 */
public final class HunterSkills {
   private static final Map<UUID, Integer> ACTIVE = new ConcurrentHashMap<>();
   private static volatile float strength = 1.0F;
   /** client-side copy for the local player (set from the Perks payload) */
   public static volatile int clientMask;
   public static volatile float clientStrength = 1.0F;

   private HunterSkills() {
   }

   static void set(UUID id, int mask) {
      if (mask == 0) {
         ACTIVE.remove(id);
      } else {
         ACTIVE.put(id, mask);
      }
   }

   static void strength(float s) {
      strength = s;
   }

   static void clear() {
      ACTIVE.clear();
   }

   public static int mask(Player p) {
      if (p == null) {
         return 0;
      }
      if (p.level().isClientSide) {
         return clientMask;
      }
      Integer m = ACTIVE.get(p.getUUID());
      return m == null ? 0 : m;
   }

   public static boolean has(Player p, Perk perk) {
      return perk.in(mask(p));
   }

   /** 1 - (1 - base) * strength, clamped to 0.05..1.5 */
   static double scaled(double base, float s) {
      return Math.clamp(1.0 - (1.0 - base) * s, 0.05, 1.5);
   }

   private static double f(Player p, Perk perk, double base) {
      return perk.in(mask(p)) ? scaled(base, p.level().isClientSide ? clientStrength : strength) : 1.0;
   }

   // ------------------------------------------------------------------------------------------------ marksmanship (client: ScopeZoom)

   /** Rifle hold drift multiplier for the local player (Steady Hands, Controlled Breath). */
   public static double swayDrift(int mask, float s) {
      double m = 1.0;
      if (Perk.STEADY_HANDS.in(mask)) {
         m *= scaled(0.80, s);
      }
      if (Perk.CONTROLLED_BREATH.in(mask)) {
         m *= scaled(0.85, s);
      }
      return m;
   }

   /** Breathing sway multiplier (Controlled Breath). */
   public static double swayBreath(int mask, float s) {
      return Perk.CONTROLLED_BREATH.in(mask) ? scaled(0.65, s) : 1.0;
   }

   /** Multiplier on the extra "just raised" wobble (Quick Settle). */
   public static double settle(int mask, float s) {
      return Perk.QUICK_SETTLE.in(mask) ? scaled(0.5, s) : 1.0;
   }

   // ------------------------------------------------------------------------------------------------ stalking (server: Whitetail.perceive, WildlifeMob)

   /** Distance at which deer hear this hunter moving (Soft Steps, Ghost). */
   public static double hearing(Player p) {
      return f(p, Perk.SOFT_STEPS, 0.80) * f(p, Perk.GHOST, 0.80) * com.formaworks.frontierhunts.campcook.MealBuffs.hearing(p); // [licence] Quiet Step meal
   }

   /** How noticeable this hunter is moving in the open (Low Profile). */
   public static double sight(Player p) {
      return f(p, Perk.LOW_PROFILE, 0.85);
   }

   /** Scent strength (Ghost). */
   public static double scent(Player p) {
      return f(p, Perk.GHOST, 0.75) * com.formaworks.frontierhunts.campcook.MealBuffs.scent(p); // [licence] Smoke-Masked meal
   }

   /** Alarm radius of the 2026 wildlife (Soft Steps half strength, Ghost). */
   public static double wildlifeAlarm(Player p) {
      return f(p, Perk.SOFT_STEPS, 0.90) * f(p, Perk.GHOST, 0.85) * com.formaworks.frontierhunts.campcook.MealBuffs.alarm(p); // [licence] Quiet Step meal
   }

   // ------------------------------------------------------------------------------------------------ tracking (server: TrailService)

   /** Radius multiplier for which sign is sent to this hunter (Keen Eye). */
   public static double signRadius(Player p) {
      return (Perk.KEEN_EYE.in(mask(p)) ? Math.clamp(1.0 + 0.25 * strength, 1.0, 1.5) : 1.0) * com.formaworks.frontierhunts.campcook.MealBuffs.signRadius(p); // [licence] Keen Tracker meal
   }

   // ------------------------------------------------------------------------------------------------ butchery (server: Whitetail.harvest)

   public static int meat(ServerPlayer p, int count) {
      if (!Perk.CLEAN_CUTS.in(mask(p)) || strength <= 0.0F) {
         return count;
      }
      int extra = Math.max(1, Math.round(count * 0.25F * strength));
      return Math.min(64, count + extra);
   }

   public static int quarters(ServerPlayer p, int count) {
      return Perk.MASTER_SKINNER.in(mask(p)) && strength > 0.0F ? Math.min(64, count + 1) : count;
   }

   public static int hides(ServerPlayer p, int count) {
      return Perk.MASTER_SKINNER.in(mask(p)) && strength > 0.0F ? Math.min(64, count + 1) : count;
   }

   /** Field dressing duration in ticks (Quick Knife), never below 60. */
   public static int dressTicks(ServerPlayer p, int ticks) {
      return Perk.QUICK_KNIFE.in(mask(p)) ? Math.max(60, (int)Math.round(ticks * scaled(0.70, strength))) : ticks;
   }

   // ------------------------------------------------------------------------------------------------ woodcraft

   /** Hunger cost multiplier for moving outdoors (Trail Legs). */
   public static double hunger(Player p) {
      return f(p, Perk.TRAIL_LEGS, 0.85);
   }

   /** Survival API: multiplier on how fast cold exposure builds for this hunter (Thick Skin). 1 = normal. */
   public static double coldExposure(Player p) {
      return f(p, Perk.THICK_SKIN, 0.70);
   }

   /** Survival API: multiplier on how much nutrition wild game gives this hunter (Provider). 1 = normal. */
   public static double nutrition(Player p) {
      return Perk.PROVIDER.in(mask(p)) ? 1.0 + 0.25 * (p.level().isClientSide ? clientStrength : strength) : 1.0;
   }

   /** Fraction of vanilla freezing that still builds up (Thick Skin). */
   static double freezing(Player p) {
      return f(p, Perk.THICK_SKIN, 0.60);
   }
}
