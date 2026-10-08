package com.formaworks.frontierhunts.hunts;

/**
 * [hunts] The simplest field event that meets a milestone, found by search over the event's conditions (fewest
 * conditions first). Used by the offline harness (every milestone must be reachable by some real event) and by the
 * {@code /frontierhunts hunts test} command for in-game checks of rewards, toasts and the journal card. Pure Java.
 */
public final class HuntExamples {
   private static final int[] EXTRAS = {HuntEvent.CLEAN, HuntEvent.MALE, HuntEvent.UNAWARE, HuntEvent.ON_FOOT, HuntEvent.CALLED, HuntEvent.RUT,
      HuntEvent.GLASSED, HuntEvent.WATER, HuntEvent.SNOW, HuntEvent.SNOW_CAMO, HuntEvent.HOUND, HuntEvent.BAIT, HuntEvent.DECOY, HuntEvent.FLYING,
      HuntEvent.HUNTING, HuntEvent.STAND, HuntEvent.BLIND, HuntEvent.CHARGING, HuntEvent.ONE_SHOT};
   private static final double[] DISTANCES = {30.0, 120.0, 12.0};
   private static final int[] TIMES = {3000, 18000};
   private static final HuntEvent.Gun[] GUNS = {HuntEvent.Gun.RIFLE, HuntEvent.Gun.BOW, HuntEvent.Gun.SHOTGUN};
   private static final int[] MONTHS = {9, 0};

   private HuntExamples() {
   }

   /** An event that satisfies {@code m} with as few conditions as possible, or null if none of the searched events does. */
   public static HuntEvent example(HuntBook.Milestone m, long day) {
      int n = EXTRAS.length;
      for (int size = 0; size <= 6; size++) {
         HuntEvent e = search(m, 0, 0, size, n, day);
         if (e != null) {
            return e;
         }
      }
      return null;
   }

   private static HuntEvent search(HuntBook.Milestone m, int start, int flags, int left, int n, long day) {
      if (left == 0) {
         return tryAll(m, flags, day);
      }
      for (int i = start; i <= n - left; i++) {
         HuntEvent e = search(m, i + 1, flags | EXTRAS[i], left - 1, n, day);
         if (e != null) {
            return e;
         }
      }
      return null;
   }

   private static HuntEvent tryAll(HuntBook.Milestone m, int flags, long day) {
      for (int mo : MONTHS) {
         for (int tod : TIMES) {
            for (HuntEvent.Gun g : GUNS) {
               for (double d : DISTANCES) {
                  HuntEvent e = HuntEvent.of(m.kind(), m.species()).flag(flags).gun(g).distance(d).kg(100000.0).points(20).herd(5).month(mo).time(tod)
                     .day(day).build();
                  if (m.matches(e)) {
                     return e;
                  }
               }
            }
         }
      }
      return null;
   }
}
