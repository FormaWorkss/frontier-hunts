package com.formaworks.frontierhunts.hunting.herd;

import com.formaworks.frontierhunts.hunting.DeerTraits;
import com.formaworks.frontierhunts.hunting.GameSpecies;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;

/**
 * [herds] Offline checks of the pure parts of the social model (no server): calendar windows, travel spacing, the spawn
 * mix and the places in each pack, group ranking, the store's bookkeeping and its save/load.
 *
 *   tools/herds/offline.sh   (compiles against the .infra classpath + the compiled mod classes, runs, prints PASS/FAIL)
 */
public final class HerdOffline {
   static int fails;

   static void check(boolean ok, String what) {
      System.out.println((ok ? "PASS " : "FAIL ") + what);
      if (!ok) {
         fails++;
      }
   }

   public static void main(String[] args) {
      windows();
      spacing();
      spawnMix();
      ranking();
      store();
      System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILED");
      System.exit(fails == 0 ? 0 : 1);
   }

   static void windows() {
      HerdTuning.Window yard = HerdTuning.WHITETAIL.yardSeason();
      check(yard.contains(0.5) && yard.contains(11.8) && !yard.contains(6.0) && !yard.contains(3.0), "yard window wraps the new year (Dec-Mar)");
      check(Math.abs(yard.progress(11.6)) < 1e-9 && Math.abs(yard.progress(1.1) - 0.5) < 1e-9, "window progress 0 at the start, 0.5 halfway across the new year");
      HerdTuning.Window bach = HerdTuning.ELK.bachelorSeason();
      check(bach.contains(10.0) && bach.contains(3.0) && !bach.contains(8.0), "elk bachelor season wraps (Oct-Aug)");
      check(!HerdTuning.Window.NEVER.contains(5.0) && HerdTuning.Window.NEVER.progress(5.0) < 0.0, "NEVER is never");
      double sum = 0;
      for (int i = 0; i < 20000; i++) {
         sum += HerdTuning.frac(new UUID(i * 7919L, i * 104729L), 3);
      }
      check(Math.abs(sum / 20000 - 0.5) < 0.01, "per-animal fractions are uniform (mean " + String.format("%.4f", sum / 20000) + ")");
   }

   static void spacing() {
      StringBuilder b = new StringBuilder("whitetail travel line (m behind the leader, side drift):");
      for (int r = 1; r <= 5; r++) {
         b.append(String.format(" r%d %.1f/%.1f", r, HerdTuning.along(HerdTuning.WHITETAIL, r, 1.05), HerdTuning.lateral(HerdTuning.WHITETAIL, r)));
      }
      System.out.println(b);
      b = new StringBuilder("elk travel line:");
      for (int r : new int[]{1, 3, 6, 10, 20, 29}) {
         b.append(String.format(" r%d %.1f/%.1f", r, HerdTuning.along(HerdTuning.ELK, r, 1.05), HerdTuning.lateral(HerdTuning.ELK, r)));
      }
      System.out.println(b);
      check(HerdTuning.along(HerdTuning.WHITETAIL, 1, 0.8) >= 3.5 && HerdTuning.along(HerdTuning.WHITETAIL, 5, 1.3) <= 30.0,
         "whitetail family strings out 4-30 m (first " + HerdTuning.along(HerdTuning.WHITETAIL, 1, 0.8) + ", last of six " + HerdTuning.along(HerdTuning.WHITETAIL, 5, 1.3) + ")");
      check(HerdTuning.along(HerdTuning.ELK, 29, 1.3) <= 90.0, "a 30-elk herd stays within ~90 m front to back");
   }

   static void spawnMix() {
      RandomSource random = RandomSource.create(1234L);
      for (GameSpecies sp : GameSpecies.values()) {
         for (double pos : new double[]{6.5, 10.4, 0.6}) {
            Map<String, Integer> kinds = new TreeMap<>();
            Map<String, Integer> first4 = new TreeMap<>();
            int n = 20000;
            for (int i = 0; i < n; i++) {
               HerdSpawn.Pack p = HerdSpawn.plan(sp, random, pos, new UUID(random.nextLong(), random.nextLong()));
               kinds.merge(p.kind.name(), 1, Integer::sum);
               if (p.kind == HerdKind.FAMILY) {
                  StringBuilder s = new StringBuilder();
                  for (int k = 0; k < Math.min(4, p.slots.size()); k++) {
                     HerdSpawn.Slot sl = p.slots.get(k);
                     s.append(sl.male() ? 'M' : 'F').append(sl.role().name().charAt(0));
                  }
                  first4.merge(s.toString(), 1, Integer::sum);
               }
            }
            StringBuilder b = new StringBuilder(String.format("spawn mix %-9s @%4.1f:", sp.id, pos));
            for (Map.Entry<String, Integer> e : kinds.entrySet()) {
               b.append(String.format(" %s %.0f%%", e.getKey(), 100.0 * e.getValue() / n));
            }
            System.out.println(b);
            if (sp == GameSpecies.WHITETAIL && pos == 6.5) {
               check(kinds.getOrDefault("BACHELOR", 0) > n * 0.15 && kinds.getOrDefault("FAMILY", 0) > n * 0.5, "summer whitetail packs: families and bachelor groups");
               System.out.println("  family packs, first four places: " + first4);
            }
            if (sp == GameSpecies.WHITETAIL && pos == 10.4) {
               check(!kinds.containsKey("BACHELOR"), "no bachelor groups spawn in the rut");
            }
            if (sp == GameSpecies.MOOSE) {
               check(!kinds.containsKey("BACHELOR"), "moose never come as bachelor groups");
            }
         }
      }
      // ages land in the planned places
      boolean ok = true;
      for (int i = 0; i < 2000; i++) {
         HerdSpawn.Slot young = new HerdSpawn.Slot(false, 12, 23, HerdRole.YOUNG);
         DeerTraits t = HerdSpawn.traits(GameSpecies.WHITETAIL, young, random);
         HerdSpawn.Slot lead = new HerdSpawn.Slot(false, 36, 108, HerdRole.LEADER);
         DeerTraits l = HerdSpawn.traits(GameSpecies.WHITETAIL, lead, random);
         HerdSpawn.Slot bull = new HerdSpawn.Slot(true, 12, 23, HerdRole.ADULT);
         DeerTraits b = HerdSpawn.traits(GameSpecies.ELK, bull, random);
         ok &= t.ageMonths() >= 12 && t.ageMonths() <= 23 && !t.buck() && l.ageMonths() >= 36 && l.ageMonths() <= 108 && b.ageMonths() >= 26 && b.buck();
      }
      check(ok, "planned places get their ages (young 12-23 mo, lead does 36-108 mo, elk bulls never under 26 mo)");
   }

   static HerdGroup.Member add(HerdGroup g, boolean male, int age, HerdRole role) {
      HerdGroup.Member m = new HerdGroup.Member(UUID.randomUUID(), role, male, age);
      g.members.add(m);
      return m;
   }

   static void ranking() {
      HerdGroup g = new HerdGroup(UUID.randomUUID(), GameSpecies.WHITETAIL, HerdKind.FAMILY);
      add(g, false, 14, HerdRole.YOUNG);
      HerdGroup.Member old = add(g, false, 80, HerdRole.ADULT);
      add(g, true, 16, HerdRole.YOUNG);
      HerdGroup.Member daughter = add(g, false, 30, HerdRole.ADULT);
      g.rank();
      check(g.members.get(0) == old && old.role == HerdRole.LEADER, "a family is led by its oldest doe");
      check(g.members.get(1) == daughter, "grown daughters walk right behind her, the young after");
      old.away = true;
      g.rank();
      check(g.members.get(0) == daughter && old.role == HerdRole.ADULT, "a long-absent matriarch hands the lead to the next doe");
      HerdGroup elk = new HerdGroup(UUID.randomUUID(), GameSpecies.ELK, HerdKind.FAMILY);
      HerdGroup.Member bull = add(elk, true, 90, HerdRole.HERD_BULL);
      HerdGroup.Member cow = add(elk, false, 70, HerdRole.ADULT);
      add(elk, false, 40, HerdRole.ADULT);
      elk.rank();
      check(elk.members.get(0) == cow && elk.members.get(elk.size() - 1) == bull && bull.role == HerdRole.HERD_BULL, "a herd bull never leads; he trails the cow herd");
      HerdGroup bach = new HerdGroup(UUID.randomUUID(), GameSpecies.WHITETAIL, HerdKind.BACHELOR);
      add(bach, true, 30, HerdRole.ADULT);
      HerdGroup.Member big = add(bach, true, 66, HerdRole.ADULT);
      bach.rank();
      check(bach.members.get(0) == big, "the oldest buck leads a bachelor group");
   }

   static void store() {
      HerdStore s = new HerdStore();
      HerdGroup fam = s.create(UUID.randomUUID(), GameSpecies.WHITETAIL, HerdKind.FAMILY, new BlockPos(10, 64, 10), 100L);
      UUID doe = UUID.randomUUID();
      UUID fawn = UUID.randomUUID();
      UUID other = UUID.randomUUID();
      s.add(fam, doe, HerdRole.LEADER, false, 60, 100L);
      HerdGroup.Member f = s.add(fam, fawn, HerdRole.YOUNG, false, 14, 100L);
      f.mother = doe;
      s.add(fam, other, HerdRole.ADULT, false, 40, 100L);
      check(s.groupOf(fawn) == fam && fam.size() == 3, "members are indexed to their group");
      HerdGroup yard = s.create(UUID.randomUUID(), GameSpecies.WHITETAIL, HerdKind.FAMILY, new BlockPos(30, 64, 10), 100L);
      s.add(yard, UUID.randomUUID(), HerdRole.LEADER, false, 90, 100L);
      fam.parent = yard.id;
      check(s.children(yard).size() == 1 && s.near(GameSpecies.WHITETAIL, new BlockPos(0, 64, 0), 40.0, null).size() == 2, "yards know their families; groups are found by place");
      s.remove(fam, doe);
      check(f.mother == null && fam.size() == 2 && s.groupOf(doe) == null, "a doe that dies leaves her young to the group's next leader");
      CompoundTag tag = s.save(new CompoundTag(), null);
      HerdStore back = HerdStore.load(tag, null);
      HerdGroup fb = back.get(fam.id);
      check(fb != null && fb.size() == 2 && yard.id.equals(fb.parent) && back.groupOf(fawn) == fb && fb.kind == HerdKind.FAMILY, "groups, members, yards survive save/load");
      // a member listed in two groups after a crash keeps the group seen last
      HerdGroup dup = s.create(UUID.randomUUID(), GameSpecies.WHITETAIL, HerdKind.BACHELOR, BlockPos.ZERO, 200L);
      HerdGroup.Member ghost = new HerdGroup.Member(other, HerdRole.ADULT, false, 40);
      dup.members.add(ghost);
      dup.lastSeen = 300L;
      fam.lastSeen = 250L;
      HerdStore back2 = HerdStore.load(s.save(new CompoundTag(), null), null);
      check(back2.groupOf(other) != null && back2.groupOf(other).id.equals(dup.id) && back2.get(fam.id).member(other) == null, "an animal in two groups (crash) keeps the newer one");
      s.redirect(fam.id, yard.id);
      HerdStore back3 = HerdStore.load(s.save(new CompoundTag(), null), null);
      check(yard.id.equals(back3.redirected(fam.id)) && back3.redirected(yard.id) == null, "merged groups leave a redirect for members that were not loaded");
      s.remove(yard, yard.members.get(0).id);
      check(s.get(yard.id) == null && fam.parent == null, "an emptied yard root is deleted and its families stand alone again");
   }
}
