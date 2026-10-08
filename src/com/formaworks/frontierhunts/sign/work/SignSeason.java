package com.formaworks.frontierhunts.sign.work;

import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.formaworks.frontierhunts.hunting.Rut;
import com.formaworks.frontierhunts.hunting.Whitetail;
import net.minecraft.server.level.ServerLevel;

/**
 * [deersign] When bucks make sign, by the reserve calendar (year position: 8.0 = September 1, 9.0 = October 1 ...).
 *
 * <p>Whitetail: rubbing starts as the velvet comes off in early September (a few rubs on small saplings), builds through
 * late September, peaks in the October pre-rut, stays high into the seeking phase and tails off through the peak and
 * post-rut into mid December. Scrapes start in late September, peak in late October / early November just before
 * the peak rut, drop off sharply once bucks are with does, and a few are reopened in the post-rut. Elk and moose
 * thrash and rub saplings from their own velvet shed (mid/late August) through their rut; they do not make
 * licking-branch scrapes.</p>
 *
 * <p>Maturity: mature bucks (3.5 years and older) make most sign; 2.5-year-olds about 60 %, yearlings a third.</p>
 */
public final class SignSeason {
   private SignSeason() {
   }

   private static float ramp(double x, double a, double b) {
      return (float)Math.clamp((x - a) / (b - a), 0.0, 1.0);
   }

   /** Rub rate 0..1 at a year position. */
   public static float rubRate(GameSpecies species, double y) {
      return switch (species) {
         case ELK -> curve(y, 7.55, 7.85, 8.6, 9.2, 9.6, 0.5F);
         case MOOSE -> curve(y, 7.85, 8.15, 9.0, 9.45, 9.95, 0.5F);
         default -> {
            // velvet off ~Sep 3 -> builds through September -> peak October -> seeking -> peak rut -> post-rut
            if (y < 8.08 || y >= 11.55) {
               yield 0.0F;
            } else if (y < 8.5) {
               yield 0.3F + 0.35F * ramp(y, 8.08, 8.5);
            } else if (y < 9.0) {
               yield 0.65F + 0.35F * ramp(y, 8.5, 9.0);
            } else if (y < 10.0) {
               yield 1.0F;
            } else if (y < 10.33) {
               yield 1.0F - 0.3F * ramp(y, 10.0, 10.33);
            } else if (y < 10.67) {
               yield 0.7F - 0.35F * ramp(y, 10.33, 10.67);
            } else {
               yield 0.35F - 0.25F * ramp(y, 10.67, 11.55);
            }
         }
      };
   }

   /** Scrape rate 0..1 at a year position (whitetail only). */
   public static float scrapeRate(GameSpecies species, double y) {
      if (species != GameSpecies.WHITETAIL) {
         return 0.0F;
      }
      if (y < 8.6 || y >= 11.55) {
         return 0.0F;
      } else if (y < 9.0) {
         return 0.25F * ramp(y, 8.6, 9.0);
      } else if (y < 9.6) {
         return 0.25F + 0.55F * ramp(y, 9.0, 9.6);
      } else if (y < 10.33) {
         return 0.8F + 0.2F * ramp(y, 9.6, 10.0);
      } else if (y < 10.67) {
         return 1.0F - 0.65F * ramp(y, 10.33, 10.67);
      } else {
         return 0.35F - 0.2F * ramp(y, 10.67, 11.55);
      }
   }

   /** A rise from {@code a} to full at {@code b}, full until {@code c}, falling to {@code tail} at {@code d}, gone at {@code e}. */
   private static float curve(double y, double a, double b, double c, double d, double e, float tail) {
      if (y < a || y >= e) {
         return 0.0F;
      } else if (y < b) {
         return 0.3F + 0.7F * ramp(y, a, b);
      } else if (y < c) {
         return 1.0F;
      } else if (y < d) {
         return 1.0F - (1.0F - tail) * ramp(y, c, d);
      } else {
         return tail * (1.0F - ramp(y, d, e));
      }
   }

   /** Share of sign a buck of this age makes. */
   public static float maturity(int ageMonths) {
      if (ageMonths >= 40) {
         return 1.0F;
      } else if (ageMonths >= 28) {
         return 0.6F;
      } else if (ageMonths >= 16) {
         return 0.33F;
      } else {
         return 0.0F; // fawns / button bucks
      }
   }

   public static double yearPosition(ServerLevel level) {
      return Rut.yearPosition(level);
   }
}
