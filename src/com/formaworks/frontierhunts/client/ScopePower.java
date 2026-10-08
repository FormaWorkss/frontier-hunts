package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.expedition.OpticTuning;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * [scope] Pure maths of the variable-power riflescopes (no Minecraft types, unit-testable offline):
 * the power range of every optic in the lineup, the detent grid the power ring clicks through, eased
 * transitions, field-of-view and turn-rate laws.
 */
public final class ScopePower {
   /** Above this power the ring clicks in whole steps, below it in half steps. */
   static final float HALF_STEP_LIMIT = 10.0F;

   /**
    * One optic. {@code calibrated} is the power at which the second-focal-plane reticle's mil marks are true
    * (the reticle keeps its apparent size at every power, so holds are exact only there).
    */
   public record Profile(String id, String name, float min, float max, float initial, float calibrated, boolean digital) {
      public boolean variable() {
         return this.max > this.min + 1.0E-3F;
      }

      public float clamp(float power) {
         return Float.isFinite(power) ? Math.clamp(power, this.min, this.max) : this.initial;
      }

      /** Every detent of the power ring, ascending, always including min and max. */
      public float[] grid() {
         List<Float> values = new ArrayList<>();
         float v = this.min;
         while (v < this.max - 1.0E-3F && values.size() < 128) {
            values.add(v);
            float step = this.digital ? 1.0F : (v < HALF_STEP_LIMIT - 1.0E-3F ? 0.5F : 1.0F);
            // land on the step lattice (a 2.5x minimum goes 2.5, 3.0, ... ; an 11x one 11, 12, ...)
            v = (float)(Math.floor(v / step + 1.0E-4) * step + step);
         }
         values.add(this.max);
         float[] out = new float[values.size()];
         for (int i = 0; i < out.length; i++) {
            out[i] = values.get(i);
         }
         return out;
      }

      /** The next detent from {@code current} in direction {@code dir} (+1 up, -1 down); unchanged at an end stop. */
      public float step(float current, int dir) {
         float[] g = this.grid();
         float c = this.clamp(current);
         if (dir > 0) {
            for (float v : g) {
               if (v > c + 1.0E-3F) {
                  return v;
               }
            }
            return g[g.length - 1];
         } else if (dir < 0) {
            for (int i = g.length - 1; i >= 0; i--) {
               if (g[i] < c - 1.0E-3F) {
                  return g[i];
               }
            }
            return g[0];
         }
         return c;
      }

      /** "5-25×" for a variable optic, "6×" for a fixed one. */
      public String range() {
         return this.variable() ? fmt(this.min) + "-" + fmt(this.max) + "×" : fmt(this.max) + "×";
      }
   }

   public static final Profile RED_DOT = new Profile("red_dot", "RED DOT", 1.0F, 1.0F, 1.0F, 1.0F, false);
   public static final Profile IRON = new Profile("iron_sights", "IRON SIGHTS", 1.5F, 1.5F, 1.5F, 1.5F, false);
   public static final Profile PRISM = new Profile("two_power_prism", "PRISM", 2.0F, 2.0F, 2.0F, 2.0F, false);
   public static final Profile TRANQ = new Profile("tranquilizer_scope", "FIELD PRECISION", 3.0F, 3.0F, 3.0F, 3.0F, false);
   public static final Profile SIX = new Profile("six_power_scope", "FIELD PRECISION", 3.0F, 9.0F, 6.0F, 9.0F, false);
   public static final Profile EIGHT = new Profile("eight_power_scope", "FIELD PRECISION", 4.0F, 16.0F, 8.0F, 16.0F, false);
   public static final Profile TWELVE = new Profile("twelve_power_scope", "LONG RANGE", 5.0F, 25.0F, 12.0F, 25.0F, false);
   public static final Profile FIELD_OPTIC = new Profile("four_power_optic", "FIELD OPTIC", 4.0F, 12.0F, 4.0F, 12.0F, false);
   public static final Profile THERMAL = new Profile("thermal_scope", "THERMAL", 2.0F, 8.0F, 4.0F, 4.0F, true);
   public static final Profile RIDGELINE = new Profile("ridgeline_scope", "RIDGELINE HUNTING SCOPE", 3.0F, 9.0F, 3.0F, 9.0F, false);

   /** The optic of an expedition (field) gun with this sight installed ("" = none). */
   public static Profile field(String sight, boolean tranquilizer) {
      return switch (sight == null ? "" : sight) {
         case "reflex_sight", "micro_red_dot", "holographic_sight" -> RED_DOT;
         case "two_power_prism" -> PRISM;
         case "six_power_scope" -> SIX;
         case "eight_power_scope" -> EIGHT;
         case "twelve_power_scope" -> TWELVE;
         case "four_power_optic" -> FIELD_OPTIC;
         case "thermal_scope" -> THERMAL;
         default -> tranquilizer ? TRANQ : IRON;
      };
   }

   /** The optic on the Ridgeline bolt rifle. */
   public static Profile ridgeline(boolean fieldOptic, boolean thermal) {
      return thermal ? THERMAL : (fieldOptic ? FIELD_OPTIC : RIDGELINE);
   }

   /** Vertical field of view (degrees) of {@code fov} seen through {@code power} magnification. */
   public static double scopedFov(double fov, double power) {
      if (!(power > 1.0001) || !Double.isFinite(fov)) {
         return fov;
      }
      double half = Math.toRadians(Math.clamp(fov, 1.0, 170.0)) * 0.5;
      return Math.toDegrees(2.0 * Math.atan(Math.tan(half) / power));
   }

   /** Power while the gun is being raised: interpolated in log space, so the zoom feels even all the way in. */
   public static double raised(double power, double progress) {
      return Math.pow(Math.max(1.0, power), Math.clamp(progress, 0.0, 1.0));
   }

   /**
    * Turn-rate multiplier. Fixed optics keep the mod's established feel; a variable optic starts from that same
    * feel at its lowest power and then scales strictly in proportion to magnification (same screen-space speed
    * at every power: dialling 5x to 25x makes the mouse exactly 5x finer).
    */
   public static double turnScale(Profile profile, double power) {
      if (profile == null || !profile.variable()) {
         return OpticTuning.turnScale(power);
      }
      double low = Math.max(1.0, profile.min());
      return Math.clamp(OpticTuning.turnScale(low) * low / Math.max(low, power), 0.01, 0.84);
   }

   /** The option-style sensitivity that makes vanilla's cubic turn curve come out {@code turn} times slower. */
   public static double eventSensitivity(double sensitivity, double turn) {
      double s = Double.isFinite(sensitivity) ? sensitivity : 0.5;
      double base = s * 0.6 + 0.2;
      double scaled = Math.max(0.025, base * Math.cbrt(Math.clamp(turn, 1.0E-4, 1.0)));
      return Math.clamp((scaled - 0.2) / 0.6, -0.2916666667, 1.0);
   }

   /** Critically damped approach in log space (equal speed per doubling of power), {@code dt} in seconds. */
   public static float ease(float shown, float target, double dt) {
      if (!(shown > 0.0F) || !Float.isFinite(shown)) {
         return target;
      }
      double k = 1.0 - Math.exp(-Math.max(0.0, dt) * 12.0);
      double out = Math.exp(Math.log(shown) + (Math.log(target) - Math.log(shown)) * k);
      return Math.abs(out - target) < target * 2.0E-4 ? target : (float)out;
   }

   // [rifle] the hold-sway waveform moved to HoldSway (continuous real-time clock, stance envelopes, no pulse twitch)

   /** Readout opacity {@code seconds} after the power was last changed or the scope was raised. */
   public static float readoutAlpha(double seconds) {
      if (!(seconds >= 0.0)) {
         return 0.0F;
      }
      return seconds < 1.4 ? 1.0F : (float)Math.clamp(1.0 - (seconds - 1.4) / 0.9, 0.0, 1.0);
   }

   /** "12.0×" */
   public static String power(float power) {
      return String.format(Locale.ROOT, "%.1f×", power);
   }

   static String fmt(float v) {
      return Math.abs(v - Math.round(v)) < 0.01F ? Integer.toString(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v);
   }

   private ScopePower() {
   }
}
