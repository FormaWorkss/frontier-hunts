package com.formaworks.frontierhunts.clothing;

import com.formaworks.frontierhunts.expedition.ScentControl;
import com.formaworks.frontierhunts.hunting.HuntPerception;
import com.formaworks.frontierhunts.journal.HunterSkills;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * [clothing] THE scent function: how much of a hunter's body odour reaches the air, as a multiplier on the scent plume
 * every animal smells (whitetail, elk, moose and the other deer through {@code Whitetail.perceive}; bears, wolves,
 * coyotes, cougars, boar, bison and the rest through {@link Noses}; the wind checker readout and its scent cone). 1.0 is
 * a hunter in ordinary clothes; nothing ever makes it zero.
 *
 * <pre>
 *   carbon   = sum over worn base-layer pieces of share x (0.25 + 0.75 x charge)       (hood .20, top .45, trousers .35, suit 1)
 *   cut      = 0.88 x carbon x motion x (1 - 0.45 sweat) x (1 - 0.40 wet)             motion: still 1, walking .85, sprinting .60
 *   emitted  = (1 - cut) x (1 + 0.45 sweat)
 *   spray    = 1 - 0.55 x min(1, 1.5 r)       r = remaining share of the 2-minute scent-cover spray (full for the first 40 s)
 *   factor   = emitted x spray x journal/meal perks, clamped 0.03 .. 1.5
 * </pre>
 * A full fresh set, standing still and dry: 0.12 (88% less scent); plus a fresh spray: 0.054. Sprinting soaked in sweat
 * in the same suit: about 0.9. No carbon, sweating hard: up to 1.45.
 *
 * <p>Server results are cached per player per game tick (deer call it once per player per perceive pass). Client: the
 * local player's sweat / wet / spray come from {@link BaseLayerView}; no allocation (the wind checker's scent cone reads it
 * every frame).
 */
public final class Scent {
   /** Scent cut of a full, fresh carbon layer while still and dry. */
   public static final double FULL_CUT = 0.88;
   /** A spent carbon layer still works as a tight weave: this share of its effect stays. */
   public static final double SPENT_FLOOR = 0.25;
   public static final double SPRINT = 0.60, WALK = 0.85, SNEAK_WALK = 0.95;
   public static final double SWEAT_CUT = 0.45, SWEAT_EMIT = 0.45, WET_CUT = 0.40;
   /** Scent-cover spray: 2 minutes, -55% at full strength. The key is the one the spray item writes. */
   public static final long SPRAY_TICKS = 2400L;
   public static final double SPRAY_CUT = 0.55;
   public static final String SPRAY_KEY = "frontier_scent_cover";
   public static final double MIN = 0.03, MAX = 1.5;

   /** Breakdown for the Layers panel and tests. */
   public record Reading(double coverage, double carbon, double motion, double sweat, double wet, double spray, double perks, double cut,
      double factor) {
   }

   private Scent() {
   }

   public static double factor(Player p) {
      if (p == null) {
         return 1.0;
      }
      if (!p.level().isClientSide) {
         BaseLayer b = BaseLayer.of(p);
         long now = p.level().getGameTime();
         if (b.factorTick != now) {
            b.factor = compute(p, null);
            b.factorTick = now;
         }
         return b.factor;
      }
      return compute(p, null);
   }

   public static Reading read(Player p) {
      double[] o = new double[8];
      double f = compute(p, o);
      return new Reading(o[0], o[1], o[2], o[3], o[4], o[5], o[6], o[7], f);
   }

   /** Pure form (offline tables and tests): carbon effectiveness 0..1, motion, sweat, wet, spray remaining 0..1, perks. */
   public static double factor(double carbon, double motion, double sweat, double wet, double sprayLeft, double perks) {
      double cut = FULL_CUT * carbon * motion * (1.0 - SWEAT_CUT * sweat) * (1.0 - WET_CUT * wet);
      double emitted = (1.0 - cut) * (1.0 + SWEAT_EMIT * sweat);
      return clamp(emitted * spray(sprayLeft) * perks, MIN, MAX);
   }

   public static double spray(double left) {
      return left <= 0.0 ? 1.0 : 1.0 - SPRAY_CUT * Math.min(1.0, left * 1.5);
   }

   private static double compute(Player p, double[] out) {
      ItemStack[] s = BaseLayer.worn(p);
      double coverage = 0.0, carbon = 0.0;
      for (ItemStack st : s) {
         if (st.getItem() instanceof ScentControl c) {
            coverage += c.piece.share;
            carbon += c.piece.share * (SPENT_FLOOR + (1.0 - SPENT_FLOOR) * ScentControl.charge(st));
         }
      }
      coverage = Math.min(1.0, coverage);
      carbon = Math.min(1.0, carbon);
      boolean client = p.level().isClientSide;
      double speed = client ? p.getDeltaMovement().horizontalDistance() * 20.0 : HuntPerception.speed(p.getUUID());
      double motion = p.isSprinting() ? SPRINT : (speed > 0.1 ? (p.isCrouching() ? SNEAK_WALK : WALK) : 1.0);
      double sweat, wet;
      long spray;
      if (client) {
         boolean local = p.isLocalPlayer();
         sweat = local ? BaseLayerView.sweat : 0.0;
         wet = local ? BaseLayerView.wet : (p.isInWaterOrRain() ? 0.6 : 0.0);
         spray = local ? BaseLayerView.sprayUntil : 0L;
      } else {
         BaseLayer b = BaseLayer.of(p);
         sweat = b.sweat;
         wet = b.wet;
         spray = p.getPersistentData().getLong(SPRAY_KEY);
      }
      double left = clamp((spray - p.level().getGameTime()) / (double) SPRAY_TICKS, 0.0, 1.0);
      double perks = HunterSkills.scent(p);
      double cut = FULL_CUT * carbon * motion * (1.0 - SWEAT_CUT * sweat) * (1.0 - WET_CUT * wet);
      double f = factor(carbon, motion, sweat, wet, left, perks);
      if (out != null) {
         out[0] = coverage;
         out[1] = carbon;
         out[2] = motion;
         out[3] = sweat;
         out[4] = wet;
         out[5] = left;
         out[6] = perks;
         out[7] = cut;
      }
      return f;
   }

   static double clamp(double v, double lo, double hi) {
      return v < lo ? lo : (v > hi ? hi : v);
   }
}
