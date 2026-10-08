package com.formaworks.frontierhunts.hunting.routine;

import com.formaworks.frontierhunts.hunting.GameSpecies;

/**
 * The daily routine as a pure function of the time of day.
 *
 * Times are "routine ticks" u = (dayTime + 6000) mod 24000, so u = 0 is midnight, u = 5000 first light
 * (dayTime 23000), u = 6000 sunrise, u = 12000 noon, u = 18000 sunset and u = 19000 dark. Over one routine day an
 * animal feeds through the night and at dawn, walks to its bed in the morning, lies up through the middle of the day
 * (getting up now and then to stretch and browse), rises in the late afternoon, waters, and walks out to feed at dusk.
 *
 * Hunting pressure (level 0..1) pulls the morning departure earlier - out of the field before light - and the
 * evening rise later - not moving until after dark - so a hard-hunted herd shows almost no daylight movement.
 */
public final class Schedule {
   public static final int FEED = 0;
   public static final int BED = 1;
   public static final int WATER = 2;
   public static final int STRETCH = 3;
   public static final int CRUISE = 4;
   public static final String[] NAMES = {"feeding", "bedded", "watering", "midday stretch", "cruising (rut)"};

   private Schedule() {
   }

   /** Per-species timing in routine ticks. */
   record Timing(int leave, int leaveShift, int rise, int riseShift, int stretchFrom, int stretchTo, int water, int cruiseFrom) {
   }

   static final Timing WHITETAIL = new Timing(7300, 3500, 15600, 3800, 11400, 12400, 1500, 11000);
   static final Timing ELK = new Timing(8200, 3500, 15000, 4200, 11500, 12300, 1500, 11200);
   static final Timing MOOSE = new Timing(9000, 3000, 14200, 4400, 11000, 12400, 2000, 11000);

   static Timing timing(GameSpecies species) {
      return switch (species) {
         case ELK -> ELK;
         case MOOSE -> MOOSE;
         default -> WHITETAIL;
      };
   }

   public static int routineTime(long dayTime) {
      return (int)Math.floorMod(dayTime + 6000L, 24000L);
   }

   /** Which routine day a day time belongs to (changes at midnight), for once-a-day events like the water visit. */
   public static long routineDay(long dayTime) {
      return Math.floorDiv(dayTime + 6000L, 24000L);
   }

   /**
    * @param shift     0..1 how far pressure has pushed this herd nocturnal
    * @param rutBuck   a mature buck in the seeking or peak rut (cruises in daylight)
    * @param watered   already drank this routine day
    * @param hasWater  the range has a water anchor
    */
   public static Plan plan(GameSpecies species, long dayTime, float shift, boolean rutBuck, boolean watered, boolean hasWater) {
      Timing t = timing(species);
      int u = routineTime(dayTime);
      float s = Math.max(0.0F, Math.min(1.0F, shift));
      int leave = Math.round((float)t.leave - (float)t.leaveShift * s);
      int rise = Math.round((float)t.rise + (float)t.riseShift * s);
      int waterEnd = Math.min(23500, rise + t.water);
      if (u < leave) {
         return new Plan(FEED, leave - u, u < 4000);
      } else if (u < rise) {
         if (rutBuck && s < 0.6F && u >= t.cruiseFrom) {
            return new Plan(CRUISE, rise - u, false);
         } else if (s < 0.5F && u >= t.stretchFrom && u < t.stretchTo) {
            return new Plan(STRETCH, t.stretchTo - u, false);
         } else {
            int until = rutBuck && s < 0.6F && u < t.cruiseFrom ? t.cruiseFrom : (s < 0.5F && u < t.stretchFrom ? t.stretchFrom : rise);
            return new Plan(BED, until - u, false);
         }
      } else if (hasWater && !watered && u < waterEnd) {
         return new Plan(WATER, waterEnd - u, false);
      } else {
         return new Plan(FEED, 24000 - u + leave, u >= 20500);
      }
   }

   /**
    * @param activity   one of FEED/BED/WATER/STRETCH/CRUISE
    * @param ticksLeft  ticks until the schedule moves on
    * @param restOk     night hours: short rests in the feeding area are part of the routine
    */
   public record Plan(int activity, int ticksLeft, boolean restOk) {
      public int anchor() {
         return switch (this.activity) {
            case BED, STRETCH -> HomeRange.BED;
            case WATER -> HomeRange.WATER;
            case CRUISE -> -1;
            default -> HomeRange.FEED;
         };
      }

      public String describe() {
         return NAMES[this.activity] + " (" + this.ticksLeft / 20 + "s left" + (this.restOk ? ", night rests" : "") + ")";
      }
   }
}
