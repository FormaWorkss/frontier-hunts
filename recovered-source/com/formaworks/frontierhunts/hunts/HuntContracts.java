package com.formaworks.frontierhunts.hunts;

import com.formaworks.frontierhunts.expedition.Campaign;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * [hunts] Species contracts for the expedition contract board. They are appended to {@code Campaign.CONTRACTS} after the
 * five standing contracts (so saved contract indices stay valid) and rotate with the reserve calendar: the board shows
 * the standing five plus up to {@link #PER_HALF} species contracts whose season is open, changing at each half of a
 * reserve month. Species with short seasons come first, so every species is posted while it is in season; only posted
 * contracts can be taken. Progress comes from hunt events ({@code ExpeditionService.record(player,
 * "hunt:<species>:<tag>", ...)}), so party members nearby share them like the standing contracts.
 */
public final class HuntContracts {
   public static final int PER_HALF = 5; // [1.1.6] was 4: more variety on the board
   /** Contracts listed by Campaign before ours. */
   public static final int BASE = 5;
   private static final List<HuntBook.Contract> CONTRACTS = new ArrayList<>();
   public static final List<Campaign.Mission> MISSIONS;

   /**
    * [1.1.6] more contract variants: appended after the one-per-species contracts so saved contract indices stay valid.
    * Each needs a real technique, season or kind of animal, not just "kill one more deer".
    */
   static final List<HuntBook.Contract> EXTRA = List.of(
      new HuntBook.Contract("whitetail", "doe_tags", "doe", 2, 40, mask(9, 10, 11, 0), "Doe management",
         "The herd is heavy on does this year. Take two antlerless deer for the lodge freezer."),
      new HuntBook.Contract("whitetail", "rut_ambush", "rutbuck", 1, 70, mask(10, 11), "Rut ambush",
         "While the bucks chase does, take a mature buck with eight points or more."),
      new HuntBook.Contract("whitetail", "still_hunt", "stalk", 1, 55, mask(9, 10, 11, 0), "Still-hunter",
         "No stand, no blind: slip in on foot and take a deer that never knew you were there."),
      new HuntBook.Contract("whitetail", "bow_opener", "bow", 1, 55, mask(8, 9, 10), "Bow season opener",
         "Take a whitetail with a bow before the rifle season opens."),
      new HuntBook.Contract("whitetail", "blood_trail", "trailed", 1, 45, mask(9, 10, 11, 0, 1), "Follow it home",
         "Recover a deer you had to follow at least 30 metres along its blood trail. Wait, read the blood, then go."),
      new HuntBook.Contract("whitetail", "buck_census", "buckcam", 3, 25, mask(7, 8, 9), "Buck census",
         "Get three trail-camera photos of bucks for the reserve's pre-season count."),
      new HuntBook.Contract("elk", "high_park", "glass150", 3, 30, mask(6, 7, 8, 9), "High-park count",
         "Glass three elk from at least 150 metres away for the herd survey. No shooting needed."),
      new HuntBook.Contract("elk", "bow_bull", "bow", 1, 70, mask(8, 9), "Bow bull",
         "Take an elk with a bow during the rut. Get close: call him in or work the wind."),
      new HuntBook.Contract("moose", "water_bull", "water", 1, 65, mask(8, 9), "Bull by the water",
         "Take a moose standing in or beside water, where they feed and wallow in the fall."),
      new HuntBook.Contract("duck", "decoy_limit", "spread", 3, 40, mask(9, 10, 11, 0), "Over the decoys",
         "Take three ducks over your decoys or with the duck call.")
   );

   static {
      List<Campaign.Mission> m = new ArrayList<>();
      for (HuntBook.Hunt h : HuntBook.hunts()) {
         HuntBook.Contract c = h.contract();
         if (c != null) {
            CONTRACTS.add(c);
            m.add(new Campaign.Mission(c.title(), c.story(), c.event(), c.species(), c.amount(), c.tokens()));
         }
      }
      for (HuntBook.Contract c : EXTRA) {
         CONTRACTS.add(c);
         m.add(new Campaign.Mission(c.title(), c.story(), c.event(), c.species(), c.amount(), c.tokens()));
      }
      MISSIONS = List.copyOf(m);
   }

   private static int mask(int... months) {
      int b = 0;
      for (int i : months) {
         b |= 1 << i;
      }
      return b;
   }

   private HuntContracts() {
   }

   /** The species contract at a {@code Campaign.CONTRACTS} index, or null for a standing contract. */
   public static HuntBook.Contract at(int index) {
      int i = index - BASE;
      return i >= 0 && i < CONTRACTS.size() ? CONTRACTS.get(i) : null;
   }

   public static List<HuntBook.Contract> all() {
      return List.copyOf(CONTRACTS);
   }

   /** Which half of the reserve month a day falls in (0 = first half, 1 = second). */
   public static int half(int day, int daysPerMonth) {
      int dpm = Math.max(1, daysPerMonth);
      return Math.clamp((long)(Math.max(1, day) - 1) * 2 / dpm, 0, 1);
   }

   /** Contract board indices: the standing contracts, then the species contracts posted for this month and half. */
   public static List<Integer> board(int month, int half) {
      List<Integer> out = new ArrayList<>();
      for (int i = 0; i < BASE; i++) {
         out.add(i);
      }
      out.addAll(seasonal(month, half));
      return out;
   }

   /**
    * The species contracts posted (indices into {@code Campaign.CONTRACTS}): those in season this month, shortest
    * season first (a stable shuffle among equals), the first {@link #PER_HALF} in the first half of the month and the
    * next ones in the second half.
    */
   public static List<Integer> seasonal(int month, int half) {
      int mo = Math.floorMod(month, 12);
      List<Integer> open = new ArrayList<>();
      for (int i = 0; i < CONTRACTS.size(); i++) {
         // [gear20] beta animals do not spawn in the wild, so their contracts are not posted (indices stay the same)
         if (CONTRACTS.get(i).open(mo) && !com.formaworks.frontierhunts.wildlife2026.Beta.animal(CONTRACTS.get(i).species())) {
            open.add(i);
         }
      }
      open.sort(Comparator.<Integer>comparingInt(i -> Integer.bitCount(CONTRACTS.get(i).months())).thenComparingInt(i -> mix(mo * 131 + i * 37)));
      List<Integer> out = new ArrayList<>();
      int n = open.size();
      if (n == 0) {
         return out;
      }
      int start = Math.floorMod(half, 2) == 0 ? 0 : (n > PER_HALF ? PER_HALF : 0);
      for (int k = 0; k < Math.min(PER_HALF, n); k++) {
         out.add(BASE + open.get((start + k) % n));
      }
      out.sort(Integer::compare);
      return out;
   }

   /**
    * Can contract {@code index} be taken now? Standing contracts always; species contracts while posted, or on the
    * first day of a new half-month ({@code grace}) also those posted in the half before, so a board read just before
    * it turned is still honoured.
    */
   public static boolean offered(int index, int month, int half, boolean grace) {
      if (index >= 0 && index < BASE) {
         return true;
      }
      if (seasonal(month, half).contains(index)) {
         return true;
      }
      if (!grace) {
         return false;
      }
      return half == 1 ? seasonal(month, 0).contains(index) : seasonal(month - 1, 1).contains(index);
   }

   private static int mix(int x) {
      x ^= x >>> 16;
      x *= 0x7feb352d;
      x ^= x >>> 15;
      x *= 0x846ca68b;
      x ^= x >>> 16;
      return x;
   }
}
