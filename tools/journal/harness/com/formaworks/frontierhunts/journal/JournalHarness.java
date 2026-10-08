package com.formaworks.frontierhunts.journal;

import io.netty.buffer.Unpooled;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * [journal] Offline checks of the journal's pure logic: skill curve, perk unlocks, ranks, perk maths, checklist
 * definitions, record NBT + sync round trips, season summary text, compass headings.
 * Run: tools/journal/harness/run.sh
 */
public final class JournalHarness {
   static int fails;

   static void check(boolean ok, String what) {
      System.out.println((ok ? "PASS " : "FAIL ") + what);
      if (!ok) {
         fails++;
      }
   }

   public static void main(String[] a) {
      // skill curve
      check(Skill.xpFor(0) == 0 && Skill.xpFor(1) == 60 && Skill.xpFor(2) == 160 && Skill.xpFor(5) == 700 && Skill.xpFor(8) == 1600 && Skill.xpFor(10) == 2400,
         "skill curve 60/160/700/1600/2400");
      check(Skill.level(0) == 0 && Skill.level(59) == 0 && Skill.level(60) == 1 && Skill.level(1599) == 7 && Skill.level(1600) == 8 && Skill.level(99999) == 10,
         "level from XP, capped at 10");
      // perks
      int[] lv = new int[]{2, 5, 8, 0, 10};
      int m = Perk.unlocked(lv);
      check(Perk.STEADY_HANDS.in(m) && !Perk.QUICK_SETTLE.in(m), "marksmanship 2 -> Steady Hands only");
      check(Perk.SOFT_STEPS.in(m) && Perk.LOW_PROFILE.in(m) && !Perk.GHOST.in(m), "stalking 5 -> two perks");
      check(Perk.KEEN_EYE.in(m) && Perk.TRAIL_SENSE.in(m) && Perk.BLOODHOUND.in(m), "tracking 8 -> all three");
      check(!Perk.CLEAN_CUTS.in(m), "butchery 0 -> none");
      check(Perk.TRAIL_LEGS.in(m) && Perk.PROVIDER.in(m) && Perk.THICK_SKIN.in(m), "woodcraft 10 -> all three");
      check(Perk.values().length == 15 && Perk.values().length < 31, "15 perks fit the mask");
      for (Skill s : Skill.values()) {
         int n = 0;
         for (Perk p : Perk.values()) {
            if (p.skill == s) {
               n++;
            }
         }
         check(n == 3, "three perks for " + s.key);
      }
      // ranks
      check(Rank.of(0) == Rank.GREENHORN && Rank.of(399) == Rank.GREENHORN && Rank.of(400) == Rank.WOODSMAN && Rank.of(22000) == Rank.LEGEND,
         "rank thresholds");
      check(Rank.LEGEND.next() == null && Rank.GREENHORN.next() == Rank.WOODSMAN, "rank chain");
      // perk maths
      int all = (1 << Perk.values().length) - 1;
      check(Math.abs(HunterSkills.swayDrift(Perk.STEADY_HANDS.bit(), 1.0F) - 0.80) < 1e-9, "Steady Hands drift x0.80");
      check(Math.abs(HunterSkills.swayDrift(all, 1.0F) - 0.80 * 0.85) < 1e-9, "Steady Hands + Controlled Breath drift x0.68");
      check(Math.abs(HunterSkills.swayBreath(all, 1.0F) - 0.65) < 1e-9 && HunterSkills.swayBreath(0, 1.0F) == 1.0, "Controlled Breath breath x0.65");
      check(Math.abs(HunterSkills.settle(all, 0.5F) - 0.75) < 1e-9, "perk strength 0.5 halves the effect");
      check(HunterSkills.swayDrift(all, 0.0F) == 1.0 && HunterSkills.settle(all, 0.0F) == 1.0, "perk strength 0 = no effect");
      check(HunterSkills.scaled(0.5, 2.0F) == 0.05, "strength 2 clamps to 0.05");
      // checklist
      List<Checklist.Entry> entries = Checklist.all();
      Set<String> ids = new HashSet<>();
      boolean unique = true, sane = true;
      int[] per = new int[Checklist.Category.values().length];
      for (Checklist.Entry e : entries) {
         unique &= ids.add(e.id());
         sane &= e.target() >= 1 && e.xp() >= 0 && !e.counter().isEmpty() && e.id().matches("[a-z0-9_]{1,40}");
         per[e.category().ordinal()]++;
      }
      check(unique, "checklist ids unique (" + entries.size() + " entries)");
      check(sane, "checklist entries sane");
      StringBuilder b = new StringBuilder();
      for (Checklist.Category c : Checklist.Category.values()) {
         b.append(c.key).append('=').append(per[c.ordinal()]).append(' ');
         check(per[c.ordinal()] > 0, "category " + c.key + " has entries");
      }
      System.out.println("     " + b);
      check(Checklist.byId("sp_whitetail") != null && Checklist.byId("sp_whitetail").species().equals("whitetail"), "species entries");
      check(Checklist.watching(Stat.LONGEST_SHOT).size() == 4, "longest-shot tiers watch one counter");
      check("bow".equals(Checklist.GEAR_ITEMS.get("frontierhunts:compound_bow")) && "tent".equals(Checklist.PLACED_BLOCKS.get("frontierhunts:pup_tent")),
         "gear / placed maps");
      int total = 0;
      for (Checklist.Entry e : entries) {
         total += e.xp();
      }
      System.out.println("     checklist XP total " + total);
      // record round trips
      HunterRecord r = new HunterRecord();
      r.counters.put(Stat.SHOTS, 42);
      r.counters.put("gear.bow", 1);
      r.skillXp[0] = 1234;
      r.skillXp[4] = 77;
      r.bonusXp = 500;
      r.done.add("clean_1");
      HunterRecord.SpeciesLog s = r.species("elk");
      s.seen = 3;
      s.harvested = 1;
      s.bestScore = 312;
      s.bestWeight = 3205;
      s.longestShot = 2124;
      s.firstDate = "Oct 12 · Yr 1";
      for (int i = 0; i < 60; i++) {
         r.note("Oct " + i + ", 6:42 AM", "note " + i, (byte)(i % 7));
      }
      r.summaries.add(new HunterRecord.Summary("Fall · Year 1", List.of("Animals taken: 2", "Shots: 3")));
      HunterRecord.Wound w = new HunterRecord.Wound();
      w.x = 1.5;
      w.time = 100L;
      w.species = "whitetail";
      r.wounds.put(java.util.UUID.randomUUID(), w);
      r.seasonKey = 2;
      r.seasonStart.put(Stat.KILLS, 1);
      CompoundTag t = r.save();
      HunterRecord back = HunterRecord.load(t);
      check(back.get(Stat.SHOTS) == 42 && back.skillXp[0] == 1234 && back.bonusXp == 500 && back.totalXp() == 1234 + 77 + 500, "NBT: counters and XP");
      check(back.done.contains("clean_1") && back.species.get("elk").bestScore == 312 && back.species.get("elk").firstDate.equals("Oct 12 · Yr 1"), "NBT: checklist + species");
      check(back.notes.size() == HunterRecord.MAX_NOTES && back.notes.peekFirst().text().equals("note 59"), "NBT: notes capped, newest first");
      check(back.wounds.size() == 1 && back.seasonKey == 2 && back.seasonStart.get(Stat.KILLS) == 1 && back.summaries.size() == 1, "NBT: wounds, season state");
      FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
      r.writeSync(buf);
      int bytes = buf.readableBytes();
      HunterRecord c = HunterRecord.readSync(buf);
      check(c.get("gear.bow") == 1 && c.skillXp[0] == 1234 && c.species.get("elk").longestShot == 2124 && c.notes.size() == 48
         && c.summaries.peekFirst().lines().size() == 2 && c.wounds.isEmpty(), "sync round trip (no server-only parts), " + bytes + " bytes");
      check(buf.readableBytes() == 0, "sync consumed exactly");
      HunterRecord cp = r.syncCopy();
      r.counters.put(Stat.SHOTS, 43);
      r.notes.clear();
      check(cp.get(Stat.SHOTS) == 42 && cp.notes.size() == 48, "syncCopy is detached from the live record");
      FriendlyByteBuf bad = new FriendlyByteBuf(Unpooled.buffer());
      for (int i = 0; i < 6; i++) {
         bad.writeVarInt(0);
      }
      bad.writeVarInt(100000);
      boolean threw = false;
      try {
         HunterRecord.readSync(bad);
      } catch (IllegalArgumentException e) {
         threw = true;
      }
      check(threw, "oversized sync rejected");
      // damaged NBT clamps
      CompoundTag dmg = new CompoundTag();
      dmg.putIntArray("skills", new int[]{-5, 999999999});
      dmg.putInt("rank_shown", 99);
      HunterRecord dr = HunterRecord.load(dmg);
      check(dr.skillXp[0] == 0 && dr.skillXp[1] == 10_000_000 && dr.rankShown == Rank.values().length - 1, "damaged NBT clamped");
      // season summary
      HunterRecord q = new HunterRecord();
      JournalService.snapshot(q, 2);
      check(JournalService.summary(q, 2) == null, "no summary without activity");
      q.counters.put(Stat.KILLS, 3);
      q.counters.put(Stat.CLEAN_KILLS, 2);
      q.counters.put(Stat.SHOTS, 5);
      q.counters.put(Stat.HITS, 4);
      q.counters.put(Stat.WALK_M, 4200);
      q.seasonStart.put("~shot", 212);
      q.seasonStart.put("~score", 142);
      q.skillXp[0] = 300;
      HunterRecord.Summary sum = JournalService.summary(q, 2);
      check(sum != null && sum.title().equals("Fall · Year 1"), "summary title " + (sum == null ? "-" : sum.title()));
      if (sum != null) {
         sum.lines().forEach(l -> System.out.println("     | " + l));
         check(sum.lines().contains("Shots: 5 · hits: 4 (80%)") && sum.lines().contains("Longest shot: 212 m"), "summary lines");
      }
      check(JournalService.seasonTitle(3).equals("Winter · Year 1") && JournalService.seasonTitle(4).equals("Spring · Year 2"), "season titles across the year");
      // compass
      check(JournalHooks.compass(0, -1).equals("N") && JournalHooks.compass(1, 0).equals("E") && JournalHooks.compass(0, 1).equals("S")
         && JournalHooks.compass(-1, 0).equals("W") && JournalHooks.compass(1, -1).equals("NE"), "compass headings (north = -z)");
      System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILED");
      System.exit(fails == 0 ? 0 : 1);
   }
}
