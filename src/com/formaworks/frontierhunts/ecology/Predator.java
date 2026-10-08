package com.formaworks.frontierhunts.ecology;

import com.formaworks.frontierhunts.wildlife2026.WildlifeSpecies;

/**
 * [ecology] How each predator hunts. Numbers are tuned so hunts stay rare and most fail: a chase only starts after a
 * hunger timer, a time-of-day roll and the area kill cap, and the outcome odds below apply once it has started.
 */
public enum Predator {
   /** packs test a herd, pick the weak or young one and run it down; long chases, low odds */
   WOLF(WildlifeSpecies.WOLF, Style.CHASE, 40.0, 1.6, 0.30F, Time.NIGHT, 26.0, 0.0, 380, 0.20F, 2600, true),
   /** small game by stalk and mouse-pounce; now and then a pair runs a yearling deer */
   COYOTE(WildlifeSpecies.COYOTE, Style.CHASE, 30.0, 1.0, 0.30F, Time.TWILIGHT, 16.0, 5.0, 220, 0.12F, 900, true),
   /** ambush: a low stalk through cover, a short explosive rush, a leap onto the back and a neck bite */
   COUGAR(WildlifeSpecies.COUGAR, Style.AMBUSH, 36.0, 2.5, 0.28F, Time.NIGHT, 0.0, 8.0, 70, 0.55F, 2400, false),
   PANTHER(WildlifeSpecies.PANTHER, Style.AMBUSH, 34.0, 2.5, 0.28F, Time.NIGHT, 0.0, 7.5, 70, 0.50F, 2400, false),
   /** stalk-and-rush like a cougar but from further out, often two together */
   LION(WildlifeSpecies.LION, Style.AMBUSH, 40.0, 2.0, 0.26F, Time.NIGHT, 0.0, 12.0, 110, 0.35F, 2800, true),
   /** daylight sprinter: walks close in the open, then one very fast, short chase that trips the prey */
   CHEETAH(WildlifeSpecies.CHEETAH, Style.SPRINT, 44.0, 1.5, 0.32F, Time.DAY, 0.0, 30.0, 150, 0.50F, 1500, false),
   /** mostly scavengers; occasionally run down a yearling or calf */
   GRIZZLY(WildlifeSpecies.GRIZZLY, Style.CHASE, 30.0, 2.5, 0.10F, Time.ANY, 14.0, 0.0, 120, 0.18F, 3000, false),
   BLACK_BEAR(WildlifeSpecies.BLACK_BEAR, Style.CHASE, 26.0, 2.5, 0.06F, Time.ANY, 12.0, 0.0, 100, 0.15F, 2400, false),
   POLAR_BEAR(WildlifeSpecies.POLAR_BEAR, Style.SCAVENGE, 30.0, 3.0, 0.0F, Time.ANY, 0.0, 0.0, 0, 0.0F, 3000, false);

   public enum Style {
      CHASE, AMBUSH, SPRINT, SCAVENGE
   }

   public enum Time {
      NIGHT, TWILIGHT, DAY, ANY;

      /** Hunting mood by time of day (0..1): mostly dawn, dusk and night for the night hunters. */
      public float weight(long dayTime) {
         long t = Math.floorMod(dayTime, 24000L);
         boolean dawn = t >= 22500L || t < 1500L;
         boolean dusk = t >= 11500L && t < 14000L;
         boolean night = t >= 13000L && t < 23000L;
         return switch (this) {
            case NIGHT -> dawn || dusk ? 1.0F : night ? 0.8F : 0.12F;
            case TWILIGHT -> dawn || dusk ? 1.0F : night ? 0.55F : 0.2F;
            case DAY -> t >= 1000L && t < 11000L ? 1.0F : dawn || dusk ? 0.4F : 0.05F;
            case ANY -> 0.6F;
         };
      }
   }

   public final WildlifeSpecies species;
   public final Style style;
   /** how far it looks for prey (blocks) */
   public final double search;
   /** a full meal lasts this many in-game days */
   public final double mealDays;
   /** chance per 10-15 s check, when hungry and the time is right, that it starts a hunt */
   public final float rate;
   public final Time time;
   /** CHASE: distance at which the walk-up turns into a run */
   public final double chaseStart;
   /** AMBUSH / SPRINT: distance at which the stalk turns into the rush (or the sprint) */
   public final double rushDistance;
   /** ticks the run may last before the predator gives up */
   public final int maxRun;
   /** odds that the run ends in a kill (before pack and prey adjustments) */
   public final float success;
   /** ticks spent feeding on a kill before leaving */
   public final int feedTicks;
   /** hunts and feeds with others of its kind */
   public final boolean social;

   Predator(WildlifeSpecies species, Style style, double search, double mealDays, float rate, Time time, double chaseStart, double rushDistance,
      int maxRun, float success, int feedTicks, boolean social) {
      this.species = species;
      this.style = style;
      this.search = search;
      this.mealDays = mealDays;
      this.rate = rate;
      this.time = time;
      this.chaseStart = chaseStart;
      this.rushDistance = rushDistance;
      this.maxRun = maxRun;
      this.success = success;
      this.feedTicks = feedTicks;
      this.social = social;
   }

   public static Predator of(WildlifeSpecies s) {
      for (Predator p : values()) {
         if (p.species == s) {
            return p;
         }
      }
      return null;
   }

   public boolean bear() {
      return this == GRIZZLY || this == BLACK_BEAR || this == POLAR_BEAR;
   }

   public boolean cat() {
      return this == COUGAR || this == PANTHER || this == LION || this == CHEETAH;
   }

   public String title() {
      return switch (this) {
         case WOLF -> "Wolf";
         case COYOTE -> "Coyote";
         case COUGAR -> "Cougar";
         case PANTHER -> "Panther";
         case LION -> "Lion";
         case CHEETAH -> "Cheetah";
         case GRIZZLY -> "Grizzly";
         case BLACK_BEAR -> "Black bear";
         case POLAR_BEAR -> "Polar bear";
      };
   }

   /**
    * Appetite for this prey (0 = never). {@code pack} is how many hunt together. Wolves need a pack for elk, moose
    * and bison and still mostly test the young, old and weak; cats take what they can ambush; coyotes and bears only
    * the small, the young and the weak.
    */
   public float appetite(Prey p, int pack) {
      boolean easy = p.young() || p.weak();
      return switch (this) {
         case WOLF -> switch (p.kind()) {
            case WHITETAIL -> p.young() ? 3.0F : p.male() ? 1.0F : 2.0F;
            case ELK -> pack < 2 ? 0.0F : p.young() ? 2.4F : p.male() ? (p.weak() ? 1.0F : 0.25F) : 1.4F;
            case MOOSE -> pack < 3 ? 0.0F : p.young() ? 0.8F : easy ? 0.4F : 0.08F;
            case BISON -> pack < 3 || !easy ? 0.0F : 1.0F;
            case PRONGHORN -> 1.2F;
            case BOAR -> pack < 2 ? 0.0F : 0.3F;
            default -> 0.0F;
         };
         case COYOTE -> switch (p.kind()) {
            case RABBIT -> 3.0F;
            case GROUSE -> 2.0F;
            case DUCK -> 1.4F;
            case WHITETAIL -> pack >= 2 && easy && !p.male() ? (p.young() ? 0.35F : 0.12F) : 0.0F;
            case PRONGHORN -> pack >= 2 && easy ? 0.2F : 0.0F;
            default -> 0.0F;
         };
         case COUGAR, PANTHER -> switch (p.kind()) {
            case WHITETAIL -> 3.0F;
            case ELK -> p.young() ? 1.5F : p.male() ? 0.15F : 0.6F;
            case MOOSE -> p.young() ? 0.3F : 0.0F;
            case PRONGHORN -> 1.0F;
            case BOAR -> this == PANTHER ? 1.6F : 0.8F;
            case RABBIT, GROUSE -> 0.25F;
            default -> 0.0F;
         };
         case LION -> switch (p.kind()) {
            case BISON -> pack >= 2 ? (easy ? 1.4F : 0.35F) : 0.0F;
            case BOAR -> 1.2F;
            case PRONGHORN -> 1.5F;
            case WHITETAIL -> 1.5F;
            case ELK -> p.male() ? 0.2F : 0.8F;
            default -> 0.0F;
         };
         case CHEETAH -> switch (p.kind()) {
            case PRONGHORN -> 3.0F;
            case WHITETAIL -> p.male() && !p.young() ? 0.4F : 1.5F;
            case RABBIT, GROUSE -> 0.5F;
            default -> 0.0F;
         };
         case GRIZZLY -> switch (p.kind()) {
            case WHITETAIL, ELK, MOOSE -> p.young() ? 0.8F : 0.0F;
            case BISON -> p.weak() ? 0.2F : 0.0F;
            case BOAR -> 0.3F;
            default -> 0.0F;
         };
         case BLACK_BEAR -> switch (p.kind()) {
            case WHITETAIL -> p.young() ? 0.5F : 0.0F;
            case ELK -> p.young() ? 0.2F : 0.0F;
            default -> 0.0F;
         };
         case POLAR_BEAR -> 0.0F;
      };
   }

   /** Odds that a run at this prey ends in a kill. */
   public float odds(Prey p, int pack) {
      float o = this.success;
      if (this == WOLF) {
         o += 0.05F * Math.min(3, pack - 1);
      }
      if (p.young()) {
         o += 0.1F;
      }
      if (p.weak()) {
         o += 0.1F;
      }
      if (p.kind() == Prey.Kind.MOOSE && !p.young()) {
         o *= 0.25F;
      }
      if (p.kind() == Prey.Kind.BISON && !p.weak()) {
         o *= 0.3F;
      }
      if (p.kind().small()) {
         o = this == COYOTE ? 0.42F : 0.35F;
      }
      return Math.clamp(o, 0.0F, 0.75F);
   }
}
