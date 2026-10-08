package com.formaworks.frontierhunts.licence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * [licence] The reserve's hunting regulations as plain data (no Minecraft classes, so the offline harness can check them):
 * which species need what, their open seasons (reserve months, 0 = January) and bag limits, and what licences, tags and
 * stamps cost.
 *
 * <p>The rules follow a real North American licence system, kept simple:
 * <ul>
 * <li>a <b>Hunting Licence</b> for the current reserve season (spring/summer/fall/winter) is needed for anything but
 *     varmints;</li>
 * <li><b>big game</b> also needs a species <b>tag</b> (one tag = one animal, auto-attached when you claim it); the number
 *     of tags a hunter may buy per season is the seasonal bag limit;</li>
 * <li><b>game birds</b> need a <b>stamp</b> (upland or waterfowl) and have a daily bag limit;</li>
 * <li><b>predators and varmints</b> (coyote, gray wolf, wild boar) need nothing and are open all year.</li>
 * </ul>
 */
public final class Regulations {
   /** Licence season length in reserve months. */
   public static final int SEASON_MONTHS = 3;

   public enum Group {
      BIG_GAME, BIRD, VARMINT
   }

   /** Big-game tags. {@code bag} = tags one hunter may buy per licence season. */
   public enum TagKind {
      // [1.1.6] tags cost real tokens now (a tag is earned, not a formality); deer bag 3 a season (+1 with a tier-4 camp)
      DEER("deer_tag", 3, 12, 1, 0xFFE8B23A),
      ELK("elk_tag", 1, 25, 2, 0xFFD8663A),
      MOOSE("moose_tag", 1, 30, 2, 0xFF7A9A4A),
      PRONGHORN("pronghorn_tag", 1, 10, 1, 0xFFE0D2A0),
      BISON("bison_tag", 1, 18, 3, 0xFF8A5A3A),
      BEAR("bear_tag", 1, 15, 2, 0xFF3A6AB0),
      CAT("cat_tag", 1, 18, 3, 0xFFB04040);

      public final String item;
      public final int bag;
      public final int tokens;
      public final int emeralds;
      /** plastic colour of the tag (ARGB), also used on the journal page */
      public final int color;

      TagKind(String item, int bag, int tokens, int emeralds, int color) {
         this.item = item;
         this.bag = bag;
         this.tokens = tokens;
         this.emeralds = emeralds;
         this.color = color;
      }

      public String key() {
         return this.name().toLowerCase(Locale.ROOT);
      }

      public static TagKind byItem(String path) {
         for (TagKind k : values()) {
            if (k.item.equals(path)) {
               return k;
            }
         }
         return null;
      }

      public static TagKind byKey(String key) {
         for (TagKind k : values()) {
            if (k.key().equals(key)) {
               return k;
            }
         }
         return null;
      }
   }

   /** Bird stamps. {@code daily} = birds per reserve day. */
   public enum Stamp {
      UPLAND("upland_stamp", 4, 8, 1, 0xFFB0743A),
      WATERFOWL("waterfowl_stamp", 6, 10, 1, 0xFF3A7A8A);

      public final String item;
      public final int daily;
      public final int tokens;
      public final int emeralds;
      public final int color;

      Stamp(String item, int daily, int tokens, int emeralds, int color) {
         this.item = item;
         this.daily = daily;
         this.tokens = tokens;
         this.emeralds = emeralds;
         this.color = color;
      }

      public String key() {
         return this.name().toLowerCase(Locale.ROOT);
      }

      public static Stamp byItem(String path) {
         for (Stamp s : values()) {
            if (s.item.equals(path)) {
               return s;
            }
         }
         return null;
      }
   }

   /** The Hunting Licence. */
   public static final String LICENCE_ITEM = "hunting_licence";
   public static final int LICENCE_TOKENS = 15; // [1.1.6] was 6
   public static final int LICENCE_EMERALDS = 1;

   /** One species' rule. {@code months}: bit i = open in reserve month i. */
   public record Rule(String species, String title, Group group, TagKind tag, Stamp stamp, int months) {
      public boolean open(int month) {
         return (this.months >> Math.floorMod(month, 12) & 1) != 0;
      }

      public boolean regulated() {
         return this.group != Group.VARMINT;
      }

      /** "Sep - Jan" style list of the open months, or "All year". */
      public String season() {
         return Regulations.months(this.months);
      }
   }

   private static final Map<String, Rule> RULES = new LinkedHashMap<>();
   private static final String[] MONTHS = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

   static {
      big("whitetail", "Whitetail deer", TagKind.DEER, 8, 9, 10, 11, 0);
      big("elk", "Elk", TagKind.ELK, 8, 9, 10);
      big("moose", "Moose", TagKind.MOOSE, 8, 9);
      big("pronghorn", "Pronghorn", TagKind.PRONGHORN, 7, 8, 9);
      big("bison", "Bison", TagKind.BISON, 10, 11, 0, 1);
      big("black_bear", "Black bear", TagKind.BEAR, 3, 4, 8, 9);
      big("grizzly", "Grizzly", TagKind.BEAR, 3, 4, 8, 9);
      big("polar_bear", "Polar bear", TagKind.BEAR, 11, 0, 1, 2);
      big("cougar", "Cougar", TagKind.CAT, 11, 0, 1, 2);
      big("lion", "Lion", TagKind.CAT, 5, 6, 7, 8);
      big("panther", "Panther", TagKind.CAT, 5, 6, 7, 8);
      big("cheetah", "Cheetah", TagKind.CAT, 5, 6, 7, 8);
      bird("grouse", "Ruffed grouse", Stamp.UPLAND, 8, 9, 10, 11);
      bird("duck", "Mallard", Stamp.WATERFOWL, 9, 10, 11, 0);
      put(new Rule("coyote", "Coyote", Group.VARMINT, null, null, 0xFFF));
      put(new Rule("boar", "Wild boar", Group.VARMINT, null, null, 0xFFF));
      put(new Rule("wolf", "Gray wolf", Group.VARMINT, null, null, 0xFFF)); // predator control: no licence or tag
   }

   private Regulations() {
   }

   private static void big(String id, String title, TagKind tag, int... months) {
      put(new Rule(id, title, Group.BIG_GAME, tag, null, mask(months)));
   }

   private static void bird(String id, String title, Stamp stamp, int... months) {
      put(new Rule(id, title, Group.BIRD, null, stamp, mask(months)));
   }

   private static void put(Rule r) {
      RULES.put(r.species(), r);
   }

   public static int mask(int... months) {
      int m = 0;
      for (int i : months) {
         m |= 1 << Math.floorMod(i, 12);
      }
      return m;
   }

   /** The rule for a species id ("whitetail", "frontierhunts:black_bear"), or null when the species is not covered. */
   public static Rule of(String species) {
      if (species == null) {
         return null;
      }
      String s = species.toLowerCase(Locale.ROOT);
      int c = s.indexOf(':');
      return RULES.get(c >= 0 ? s.substring(c + 1) : s);
   }

   public static List<Rule> all() {
      return Collections.unmodifiableList(new ArrayList<>(RULES.values()));
   }

   /** Species a tag kind covers. */
   public static List<Rule> covered(TagKind k) {
      List<Rule> out = new ArrayList<>();
      for (Rule r : RULES.values()) {
         if (r.tag() == k) {
            out.add(r);
         }
      }
      return out;
   }

   /** Species a stamp covers. */
   public static List<Rule> covered(Stamp s) {
      List<Rule> out = new ArrayList<>();
      for (Rule r : RULES.values()) {
         if (r.stamp() == s) {
            out.add(r);
         }
      }
      return out;
   }

   /** Union of the open months of the species a tag covers. */
   public static int months(TagKind k) {
      int m = 0;
      for (Rule r : covered(k)) {
         m |= r.months();
      }
      return m;
   }

   public static int months(Stamp s) {
      int m = 0;
      for (Rule r : covered(s)) {
         m |= r.months();
      }
      return m;
   }

   // ============================================================================================ calendar

   /**
    * Licence season of a reserve month serial (HuntingCalendar.Date.serial: months since the calendar began, 8 =
    * September of year 1). Seasons start in March, June, September and December, so December belongs to the winter
    * that runs into the next year.
    */
   public static int period(long monthSerial) {
      return (int)Math.floorDiv(monthSerial - 2L, (long)SEASON_MONTHS);
   }

   /** 0 spring, 1 summer, 2 fall, 3 winter. */
   public static int seasonOf(int period) {
      return Math.floorMod(period, 4);
   }

   /** Reserve year (1-based) a licence season starts in. */
   public static int yearOf(int period) {
      return (int)Math.floorDiv((long)period * SEASON_MONTHS + 2L, 12L) + 1;
   }

   /** First reserve month serial of a licence season. */
   public static long firstMonth(int period) {
      return (long)period * SEASON_MONTHS + 2L;
   }

   public static String seasonName(int period) {
      return switch (seasonOf(period)) {
         case 0 -> "Spring";
         case 1 -> "Summer";
         case 2 -> "Fall";
         default -> "Winter";
      };
   }

   /** "Fall season, reserve year 1". */
   public static String periodTitle(int period) {
      return seasonName(period) + " season, reserve year " + yearOf(period);
   }

   public static String months(int mask) {
      if ((mask & 0xFFF) == 0xFFF) {
         return "All year";
      }
      if (mask == 0) {
         return "Closed";
      }
      // runs of consecutive months, wrapping December -> January
      int start = 0;
      while ((mask >> start & 1) != 0 && start < 12) {
         start++;
      }
      List<String> parts = new ArrayList<>();
      int i = 0;
      while (i < 12) {
         int m = (start + i) % 12;
         if ((mask >> m & 1) == 0) {
            i++;
            continue;
         }
         int j = i;
         while (j + 1 < 12 && (mask >> ((start + j + 1) % 12) & 1) != 0) {
            j++;
         }
         int a = m, b = (start + j) % 12;
         parts.add(a == b ? MONTHS[a] : MONTHS[a] + " - " + MONTHS[b]);
         i = j + 1;
      }
      return String.join(", ", parts);
   }

   public static String monthName(int month) {
      return MONTHS[Math.floorMod(month, 12)];
   }
}
