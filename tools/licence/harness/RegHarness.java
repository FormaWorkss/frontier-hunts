import com.formaworks.frontierhunts.licence.Regulations;

/** [licence] Offline checks of the regulation tables and the licence-season calendar maths. */
public class RegHarness {
   static int fails = 0, checks = 0;

   static void check(boolean ok, String what) {
      checks++;
      if (!ok) {
         fails++;
         System.out.println("FAIL " + what);
      }
   }

   public static void main(String[] a) {
      String[] quarry = {"whitetail", "elk", "moose", "black_bear", "grizzly", "polar_bear", "boar", "bison", "pronghorn", "coyote", "wolf",
         "cougar", "lion", "panther", "cheetah", "grouse", "duck"};
      for (String q : quarry) {
         Regulations.Rule r = Regulations.of(q);
         check(r != null, "rule for " + q);
         if (r == null) continue;
         check(Regulations.of("frontierhunts:" + q) == r, "namespaced lookup " + q);
         switch (r.group()) {
            case BIG_GAME -> check(r.tag() != null && r.stamp() == null, q + " big game has a tag");
            case BIRD -> check(r.stamp() != null && r.tag() == null, q + " bird has a stamp");
            case VARMINT -> check((r.months() & 0xFFF) == 0xFFF, q + " varmint open all year");
         }
         check(Integer.bitCount(r.months() & 0xFFF) >= 2, q + " open at least 2 months");
      }
      check(!Regulations.of("coyote").regulated() && !Regulations.of("boar").regulated() && !Regulations.of("wolf").regulated(), "coyote/boar/wolf need nothing");
      for (Regulations.TagKind k : Regulations.TagKind.values()) {
         check(!Regulations.covered(k).isEmpty(), k + " covers a species");
         check(k.bag >= 1 && k.tokens > 0 && k.emeralds > 0, k + " prices");
         // every tag kind can be used in at least two licence seasons a year
         int seasons = 0;
         for (int p = 0; p < 4; p++) {
            int m = 0;
            for (int i = 0; i < 3; i++) m |= 1 << Math.floorMod(Regulations.firstMonth(p) + i, 12);
            if ((m & Regulations.months(k)) != 0) seasons++;
         }
         check(seasons >= 1, k + " usable in some season");
      }
      // calendar: serial 8 = September year 1 -> fall of year 1; December belongs to the winter starting that year
      check(Regulations.period(8) == 2 && Regulations.seasonOf(2) == 2, "Sep yr1 = fall");
      check(Regulations.yearOf(Regulations.period(8)) == 1, "fall yr1 year");
      check(Regulations.period(10) == 2, "Nov yr1 = fall");
      check(Regulations.period(11) == 3 && Regulations.period(13) == 3, "Dec yr1 .. Feb yr2 = winter");
      check(Regulations.yearOf(3) == 1, "winter starting Dec yr1 is year 1");
      check(Regulations.period(14) == 4 && Regulations.seasonOf(4) == 0 && Regulations.yearOf(4) == 2, "Mar yr2 = spring yr2");
      for (long s = 0; s < 120; s++) {
         int p = Regulations.period(s);
         check(Regulations.firstMonth(p) <= s && s < Regulations.firstMonth(p) + 3, "serial " + s + " inside its season");
      }
      check(Regulations.months(Regulations.mask(8, 9, 10, 11, 0)).equals("Sep - Jan"), "wrap text: " + Regulations.months(Regulations.mask(8, 9, 10, 11, 0)));
      check(Regulations.months(Regulations.mask(3, 4, 8, 9)).equals("Apr - May, Sep - Oct"), "split text: " + Regulations.months(Regulations.mask(3, 4, 8, 9)));
      check(Regulations.months(0xFFF).equals("All year"), "all year");
      System.out.println((fails == 0 ? "ALL PASS" : fails + " FAILED") + " (" + checks + " checks)");
      System.exit(fails == 0 ? 0 : 1);
   }
}
