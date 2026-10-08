package com.formaworks.frontierhunts.hunting.herd;

import com.formaworks.frontierhunts.hunting.GameSpecies;
import java.util.UUID;

/**
 * [herds] Every number of the social model in one place: group sizes, who stays alone, the seasonal calendar of the
 * social year, travel spacing and the alarm radii. Distances are blocks (= metres); dates are calendar month
 * positions (0 = 1 January, 8 = 1 September, see {@code SeasonClock}); windows may wrap past the new year.
 *
 * Sources for the shape of it (whitetail): matriarchal doe groups of a doe, her yearling daughters and the year's
 * young; bucks in loose bachelor groups from spring to late summer that break up as the pre-rut starts; yearling
 * bucks dispersing from their mother's group from late spring to fall; groups gathering in sheltered "yards" through
 * deep winter and splitting again in spring. Elk: cow/calf herds led by an old cow, bachelor bands, a herd bull holding
 * a harem through the rut, big winter herds. Moose: solitary, a cow with her calf until she drives it off before the
 * next calving.
 */
public final class HerdTuning {
   private HerdTuning() {
   }

   /** Per-species social numbers. */
   public record Social(
      /** most animals a family group (whitetail doe group, elk cow herd, moose cow + calf) holds outside the winter */
      int familyMax,
      int bachelorMin,
      int bachelorMax,
      /** most animals gathered under one winter yard / winter herd (0 = no yarding) */
      int yardMax,
      /** share of adult females that live alone (loners, never take a group) */
      float loneFemale,
      /** share of adult males that stay solitary even in the bachelor season */
      float soloMale,
      /** same for mature males (54 months and older), which are more often alone */
      float soloMatureMale,
      /** share of young males (yearlings) that leave their mother's group in the dispersal window */
      float youngMaleDisperse,
      /** travel: distance along the leader's path between consecutive animals (x the animal's own 0.8-1.3 factor) */
      float spacing,
      /** travel: how far an animal drifts to either side of the leader's line at the back of the group (and its cap) */
      float lateral,
      float lateralMax,
      /** young of the year following their mother: gap behind her */
      float youngGap,
      /** stationary: feeding animals stay within this of the leader; bedded ones lie within this of the leader's bed */
      float feedSpread,
      float bedSpread,
      /** a follower further than (its slot + this) behind makes a travelling leader stop and wait */
      float straggle,
      /** adoption: an ungrouped animal looks this far for a group to join */
      float joinRadius,
      /** yards: families and lone males this far from a yard's centre are drawn in */
      float yardRadius,
      /** alarm: a stomp/snort puts group mates within this on alert; a bolt sends mates within boltRadius running */
      float alertRadius,
      float boltRadius,
      /** calendar windows (month positions) */
      Window bachelorSeason,
      Window bachelorSplit,
      Window youngDispersal,
      Window yardSeason,
      Window harem
   ) {
   }

   /** A calendar window [from, to) in month positions; wraps when to &lt; from. */
   public record Window(double from, double to) {
      public static final Window NEVER = new Window(-1.0, -1.0);

      public boolean contains(double pos) {
         if (this.from < 0.0) {
            return false;
         }

         double p = wrap(pos);
         return this.from <= this.to ? p >= this.from && p < this.to : p >= this.from || p < this.to;
      }

      /** Position in the window 0..1, or -1 when outside it. */
      public double progress(double pos) {
         if (!this.contains(pos)) {
            return -1.0;
         }

         double span = wrap(this.to - this.from);
         double into = wrap(wrap(pos) - this.from);
         return span <= 0.0 ? 0.0 : into / span;
      }
   }

   public static final Social WHITETAIL = new Social(
      6, 2, 4, 20,
      0.10F, 0.35F, 0.55F, 0.80F,
      5.0F, 1.4F, 3.0F, 2.6F,
      12.0F, 6.0F, 26.0F,
      48.0F, 96.0F,
      40.0F, 64.0F,
      new Window(3.5, 9.0),   // bachelor groups: mid-April to September
      new Window(9.0, 9.9),   // they break up through the pre-rut (October)
      new Window(4.5, 9.5),   // yearling bucks leave their mothers: mid-May to mid-October
      new Window(11.6, 2.6),  // yards: mid-December to mid-March
      Window.NEVER
   );

   public static final Social ELK = new Social(
      30, 2, 6, 45,
      0.04F, 0.25F, 0.40F, 0.50F,
      4.0F, 3.5F, 12.0F, 3.0F,
      22.0F, 10.0F, 40.0F,
      80.0F, 128.0F,
      64.0F, 96.0F,
      new Window(9.7, 7.7),   // bachelor bands from the end of the rut to late August
      new Window(7.7, 8.3),   // they split as the bulls start bugling
      new Window(7.7, 8.6),   // half the spike bulls are pushed out of the cow herds by the herd bulls
      new Window(11.0, 3.0),  // big winter herds: December to March
      new Window(8.3, 9.4)    // herd bulls hold harems: September to early October
   );

   public static final Social MOOSE = new Social(
      2, 0, 0, 0,
      1.0F, 1.0F, 1.0F, 1.0F,
      3.0F, 0.8F, 1.5F, 3.0F,
      7.0F, 4.0F, 20.0F,
      48.0F, 0.0F,
      32.0F, 48.0F,
      Window.NEVER,
      Window.NEVER,
      new Window(4.5, 5.5),   // the cow drives last year's calf off before she calves (May)
      Window.NEVER,
      Window.NEVER
   );

   public static Social of(GameSpecies species) {
      return switch (species) {
         case ELK -> ELK;
         case MOOSE -> MOOSE;
         default -> WHITETAIL;
      };
   }

   /** Natural spawns: the share of packs that start as a family, a bachelor group, a lone male, a lone female. */
   public record Mix(float family, float bachelor, float soloMale, float soloFemale) {
   }

   public static Mix spawnMix(GameSpecies species, double pos) {
      Social s = of(species);
      return switch (species) {
         case ELK -> s.bachelorSeason().contains(pos) ? new Mix(0.72F, 0.20F, 0.08F, 0.0F) : new Mix(0.72F, 0.0F, 0.28F, 0.0F);
         case MOOSE -> s.youngDispersal().contains(pos) ? new Mix(0.0F, 0.0F, 0.40F, 0.60F) : new Mix(0.40F, 0.0F, 0.35F, 0.25F);
         default -> s.bachelorSeason().contains(pos)
            ? new Mix(0.58F, 0.18F, 0.14F, 0.10F)
            : new Mix(0.68F, 0.0F, 0.26F, 0.06F);
      };
   }

   /** a mature male (prime or older) is more often solitary */
   public static final int MATURE_MONTHS = 54;
   /** animals younger than this are "young": they follow their mother (yearlings; the mod's youngest age class) */
   public static final int YOUNG_MONTHS = 24;
   /** moose calves stay with the cow only while younger than this */
   public static final int MOOSE_CALF_MONTHS = 18;
   /** elk bulls that can hold a harem */
   public static final int HERD_BULL_MONTHS = 36;

   /** how often (ticks) each animal re-checks its membership against the season (staggered) */
   public static final int MEMBER_CHECK = 100;
   /** how often a group does its housekeeping (present members, leader, straggler wait) */
   public static final int GROUP_UPDATE = 20;
   /** how often a group runs the slower social rules (yards, harems, fusion) */
   public static final int SOCIAL_UPDATE = 400;
   /** an ungrouped animal first looks for a group this many ticks after it is loaded (plus up to 400 more) */
   public static final int ADOPT_DELAY = 100;
   /** at most this many adoption searches per server tick for the whole server */
   public static final int ADOPT_BUDGET = 4;
   /** a leader not seen for this long (while others are) hands the group to the next animal */
   public static final long LEADER_ABSENT = 6000L;
   /** a member not seen for this long while the rest of its group is (3 in-game days) is dropped */
   public static final long MEMBER_FORGET = 72000L;
   /** a group nobody has seen for this long is forgotten (20 in-game days) */
   public static final long GROUP_FORGET = 480000L;
   /** a follower further than this from the animal it follows acts on its own until it is close again */
   public static final double FOLLOW_MAX = 112.0;
   /** leader breadcrumbs: one every this many blocks, this many kept */
   public static final double CRUMB_STEP = 1.5;
   public static final int CRUMBS = 72;
   /** the leader is "travelling" once it is this far from where it last stood still */
   public static final double TRAVEL_START = 6.0;
   /** a flight heading is shared by the group for this long */
   public static final long FLIGHT_SHARE = 240L;
   /** how strongly a fleeing group mate steers onto the group's flight heading (0..1) */
   public static final float FLIGHT_BLEND = 0.68F;
   /** chance that a grazing animal in a group keeps its head up while every mate near it is grazing */
   public static final float SENTINEL = 0.7F;
   /** a leader waits for stragglers at most this long per leg */
   public static final long WAIT_BUDGET = 900L;

   /** Stable 0..1 value per animal and purpose (spacing factor, split date, loner...). */
   public static double frac(UUID id, int salt) {
      long h = id.getMostSignificantBits() * 0x9E3779B97F4A7C15L ^ id.getLeastSignificantBits() * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
      h ^= h >>> 31;
      h *= 0xBF58476D1CE4E5B9L;
      h ^= h >>> 29;
      return (h >>> 11) * 0x1.0p-53;
   }

   public static final int SALT_LONER = 1;
   public static final int SALT_SPLIT = 2;
   public static final int SALT_DISPERSE = 3;
   public static final int SALT_SPACING = 4;
   public static final int SALT_LATERAL = 5;
   public static final int SALT_BED = 6;
   public static final int SALT_SOLO = 7;

   static double wrap(double m) {
      double r = m % 12.0;
      return r < 0.0 ? r + 12.0 : r;
   }

   /** Distance behind the leader along its path for the animal at this rank (1 = first behind the leader). */
   public static double along(Social s, int rank, double personal) {
      double f = rank <= 3 ? rank : 3.0 + (rank - 3) * 0.5;
      return s.spacing() * personal * f;
   }

   /** Side drift amplitude for the animal at this rank. */
   public static double lateral(Social s, int rank) {
      return Math.min(s.lateralMax(), s.lateral() * (1.0 + 0.15 * Math.max(0, rank - 1)));
   }
}
