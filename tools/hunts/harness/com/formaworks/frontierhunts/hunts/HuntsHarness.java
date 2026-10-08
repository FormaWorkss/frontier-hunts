package com.formaworks.frontierhunts.hunts;

import com.formaworks.frontierhunts.camps.Quarry;
import com.formaworks.frontierhunts.expedition.Campaign;
import com.formaworks.frontierhunts.journal.Checklist;
import com.formaworks.frontierhunts.journal.HunterRecord;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.FriendlyByteBuf;

/**
 * [hunts] Offline checks of the species hunts: the book's shape, every milestone reachable by a field event and not by
 * a plain one, synthetic hunting seasons fed through the tracker (counters, shared counters, bag limits, conditions),
 * condition tags, contracts and their monthly rotation, ranger assignment specs, the carry-over of old progress, the
 * journal checklist integration and the lang keys the code builds. Also writes docs/ws/hunts/milestones.json for the
 * jar audit (tools/hunts/audit.py).
 *
 * <p>Run: tools/hunts/harness/run.sh
 */
public final class HuntsHarness {
   static int fails;
   static int passes;

   static void check(boolean ok, String what) {
      if (!ok) {
         System.out.println("FAIL " + what);
         fails++;
      } else {
         passes++;
         if (System.getenv("VERBOSE") != null) {
            System.out.println("PASS " + what);
         }
      }
   }

   /** A hunter's counters + checklist done set, updated like the journal does (done when counter >= target). */
   static final class Hunter implements HuntTracker.Counters {
      final Map<String, Integer> counters = new HashMap<>();
      final Set<String> done = new HashSet<>();
      final List<String> completed = new ArrayList<>();
      final List<String> tags = new ArrayList<>();

      @Override
      public int get(String key) {
         return this.counters.getOrDefault(key, 0);
      }

      void feed(HuntEvent e) {
         HuntTracker.Result r = HuntTracker.evaluate(e, this);
         for (HuntTracker.Update u : r.updates()) {
            this.counters.put(u.key(), u.value());
            for (Checklist.Entry ce : Checklist.watching(u.key())) {
               if (u.value() >= ce.target() && this.done.add(ce.id())) {
                  this.completed.add(ce.id());
               }
            }
         }
         this.tags.addAll(r.tags());
      }

      boolean has(String id) {
         return this.done.contains(id);
      }
   }

   static HuntEvent.Builder take(String sp) {
      return HuntEvent.of(HuntEvent.Kind.TAKE, sp).gun(HuntEvent.Gun.RIFLE).distance(40).month(9).time(3000).day(10);
   }

   public static void main(String[] a) throws Exception {
      String repo = a.length > 0 ? a[0] : ".";
      // ------------------------------------------------------------------------------------------ book shape
      List<HuntBook.Hunt> hunts = HuntBook.hunts();
      check(hunts.size() == Quarry.values().length, "a hunt for every record-book species (" + hunts.size() + "/" + Quarry.values().length + ")");
      Set<String> ids = new HashSet<>();
      for (Quarry q : Quarry.values()) {
         HuntBook.Hunt h = HuntBook.of(q.id);
         check(h != null, "hunt for " + q.id);
         if (h == null) {
            continue;
         }
         check(h.milestones().size() >= 3 && h.milestones().size() <= 5, q.id + ": 3-5 milestones");
         check(h.master().tier() == HuntBook.Tier.MASTER, q.id + ": ends with a master milestone");
         Set<HuntBook.Tier> tiers = new HashSet<>();
         for (HuntBook.Milestone m : h.milestones()) {
            tiers.add(m.tier());
            check(ids.add(m.id()), "unique id " + m.id());
            check(m.id().matches("[a-z0-9_]{1,40}") && m.id().equals("hunt_" + q.id + "_" + m.key()), "id format " + m.id());
            check(m.counter().startsWith("hunt." + q.id + ".") && m.counter().length() <= 64, "counter " + m.counter());
            check(m.target() >= 1 && m.target() <= 10 && m.xp() > 0 && m.xp() <= 1000 && m.tokens() >= 0 && m.tokens() <= 100, "numbers " + m.id());
            check(m.item().isEmpty() || m.item().startsWith("frontierhunts:") && m.count() >= 1 && m.count() <= 64, "reward item " + m.id());
            check(HuntIconsNames.has(m.icon()), "icon exists for " + m.id() + " (" + m.icon() + ")");
         }
         check(tiers.contains(HuntBook.Tier.SCOUT) && tiers.contains(HuntBook.Tier.TAKE) && tiers.contains(HuntBook.Tier.TECHNIQUE), q.id + ": scout, take and technique tiers");
         check(h.contract() != null && h.contract().months() != 0 && HuntTracker.TAGS.containsKey(h.contract().tag()), q.id + ": a season contract");
      }
      int total = HuntBook.milestones().size();
      check(total == 85, "85 milestones (" + total + ")");

      // ------------------------------------------------------------------------------------------ reachability
      for (HuntBook.Milestone m : HuntBook.milestones()) {
         HuntEvent ex = HuntExamples.example(m, 5);
         check(ex != null && m.matches(ex), "reachable by a field event: " + m.id() + (ex == null ? "" : "  <- " + ex));
         HuntEvent plain = HuntEvent.of(m.kind(), m.species()).gun(HuntEvent.Gun.RIFLE).distance(30).month(9).time(3000).build();
         if (m.tier() == HuntBook.Tier.TECHNIQUE || m.tier() == HuntBook.Tier.QUALITY || m.tier() == HuntBook.Tier.MASTER) {
            if (m.mode() != HuntBook.Mode.DAY && !(m.species().equals("grouse") && m.key().equals("master"))) {
               check(!m.matches(plain), "not met by a plain " + m.kind() + ": " + m.id());
            }
         }
         if (m.tier() == HuntBook.Tier.TAKE && !m.species().equals("grouse") && !m.species().equals("duck")) {
            check(!m.matches(plain), "first take needs a clean shot: " + m.id());
            check(m.matches(take(m.species()).flag(HuntEvent.CLEAN).build()), "clean take counts: " + m.id());
         }
      }

      // ------------------------------------------------------------------------------------------ seasons through the tracker
      Hunter h = new Hunter();
      h.feed(HuntEvent.of(HuntEvent.Kind.PHOTO, "whitetail").build());
      check(h.has("hunt_whitetail_cam"), "whitetail: camera photo");
      h.feed(take("whitetail").flag(HuntEvent.CLEAN).build());
      check(h.has("hunt_whitetail_clean") && !h.has("hunt_whitetail_mature"), "whitetail: clean doe = clean take only");
      h.feed(take("whitetail").flag(HuntEvent.MALE | HuntEvent.CALLED).points(6).build());
      check(h.has("hunt_whitetail_called") && !h.has("hunt_whitetail_mature"), "whitetail: called 6-pointer = called, not mature");
      h.feed(take("whitetail").flag(HuntEvent.MALE | HuntEvent.CLEAN | HuntEvent.RUT).points(10).build());
      check(h.has("hunt_whitetail_mature") && !h.has("hunt_whitetail_master"), "whitetail: rut 10-pointer on the ground = mature, not master");
      h.feed(take("whitetail").flag(HuntEvent.MALE | HuntEvent.CLEAN | HuntEvent.RUT | HuntEvent.STAND).points(8).build());
      check(h.has("hunt_whitetail_master"), "whitetail: rut 8-pointer from a stand, clean = master");
      h.feed(take("whitetail").flag(HuntEvent.MALE | HuntEvent.RUT | HuntEvent.BLIND).points(9).build());
      check(h.completed.stream().filter(s -> s.startsWith("hunt_whitetail")).count() == 5, "whitetail: five milestones, each once");

      Hunter g = new Hunter();
      g.feed(HuntEvent.of(HuntEvent.Kind.FLUSH, "grouse").distance(6).build());
      check(g.has("hunt_grouse_flush"), "grouse: flush");
      for (int i = 0; i < 2; i++) {
         g.feed(take("grouse").gun(HuntEvent.Gun.SHOTGUN).day(3).build());
      }
      check(!g.has("hunt_grouse_limit") && g.has("hunt_grouse_take"), "grouse: 2 in a day is no limit");
      g.feed(take("grouse").gun(HuntEvent.Gun.SHOTGUN).day(4).build());
      g.feed(take("grouse").gun(HuntEvent.Gun.SHOTGUN).day(4).build());
      check(!g.has("hunt_grouse_limit"), "grouse: the day count starts again on a new day");
      g.feed(take("grouse").gun(HuntEvent.Gun.SHOTGUN).flag(HuntEvent.FLYING).day(4).build());
      check(g.has("hunt_grouse_limit") && g.has("hunt_grouse_wing") && !g.has("hunt_grouse_master"), "grouse: 3rd bird of the day on the wing = limit + wing");
      for (int i = 0; i < 4; i++) {
         g.feed(take("grouse").flag(HuntEvent.FLYING).day(5 + i).build());
      }
      check(g.has("hunt_grouse_master") && g.get("hunt.grouse.wing") == 5, "grouse: 5 on the wing = wingshooter; shared counter moved once per bird (" + g.get("hunt.grouse.wing") + ")");

      Hunter d = new Hunter();
      for (int i = 0; i < 3; i++) {
         d.feed(take("duck").flag(HuntEvent.DECOY).day(7).build());
      }
      d.feed(take("duck").day(7).build());
      check(d.has("hunt_duck_spread") && !d.has("hunt_duck_master"), "duck: 3 over decoys + 1 without is not a limit over the spread");
      for (int i = 0; i < 4; i++) {
         d.feed(take("duck").flag(i % 2 == 0 ? HuntEvent.DECOY : HuntEvent.CALLED).day(8).build());
      }
      check(d.has("hunt_duck_master"), "duck: 4 over decoys / to the call in one day = master");
      check(!d.has("hunt_duck_wing"), "duck: none on the wing yet");

      Hunter c = new Hunter();
      c.feed(take("coyote").flag(HuntEvent.CALLED).time(3000).build());
      c.feed(take("coyote").flag(HuntEvent.CALLED).time(14000).build());
      c.feed(take("coyote").flag(HuntEvent.CALLED).time(23500).build());
      check(c.has("hunt_coyote_called") && !c.has("hunt_coyote_master"), "coyote: a daylight call does not count for the night stand");
      c.feed(take("coyote").flag(HuntEvent.CALLED).time(12000).build());
      check(c.has("hunt_coyote_master"), "coyote: 3 called between dusk and dawn = night stand");
      check(!c.has("hunt_coyote_prime"), "coyote: October is not prime fur");
      c.feed(take("coyote").month(0).build());
      check(c.has("hunt_coyote_prime"), "coyote: January pelt is prime");

      Hunter b = new Hunter();
      b.feed(take("grizzly").flag(HuntEvent.CHARGING | HuntEvent.CLEAN).distance(25).build());
      check(!b.has("hunt_grizzly_master"), "grizzly: a charge stopped at 25 m is not within 20");
      b.feed(take("grizzly").flag(HuntEvent.CHARGING | HuntEvent.CLEAN).distance(14).build());
      check(b.has("hunt_grizzly_master"), "grizzly: charge stopped at 14 m, clean = master");
      b.feed(take("black_bear").gun(HuntEvent.Gun.RIFLE).flag(HuntEvent.CLEAN | HuntEvent.BAIT).build());
      check(b.has("hunt_black_bear_bait") && !b.has("hunt_black_bear_master"), "black bear: rifle over bait = technique only");
      b.feed(take("black_bear").gun(HuntEvent.Gun.BOW).flag(HuntEvent.CLEAN | HuntEvent.BAIT).build());
      check(b.has("hunt_black_bear_master"), "black bear: bow over bait, clean = master");
      b.feed(take("bison").flag(HuntEvent.ON_FOOT | HuntEvent.UNAWARE | HuntEvent.BLIND).herd(5).build());
      check(!b.has("hunt_bison_fair_chase"), "bison: from a blind is not fair chase");
      b.feed(take("bison").flag(HuntEvent.ON_FOOT | HuntEvent.UNAWARE).herd(2).build());
      check(!b.has("hunt_bison_fair_chase"), "bison: two animals are not a herd");
      b.feed(take("bison").flag(HuntEvent.ON_FOOT | HuntEvent.UNAWARE | HuntEvent.ONE_SHOT | HuntEvent.CLEAN).herd(4).kg(750).build());
      check(b.has("hunt_bison_fair_chase") && b.has("hunt_bison_master") && b.has("hunt_bison_bull"), "bison: one shot on foot into a herd of 4 = fair chase + master (+ 750 kg bull)");
      b.feed(HuntEvent.of(HuntEvent.Kind.GLASS, "cheetah").distance(90).time(16000).build());
      check(!b.has("hunt_cheetah_glass"), "cheetah: glassing at night does not count");
      b.feed(HuntEvent.of(HuntEvent.Kind.GLASS, "cheetah").distance(90).time(4000).flag(HuntEvent.HUNTING).build());
      check(b.has("hunt_cheetah_glass") && b.has("hunt_cheetah_chase"), "cheetah: glassed by day while it hunts = glass + chase");
      b.feed(take("pronghorn").flag(HuntEvent.CLEAN | HuntEvent.GLASSED | HuntEvent.ON_FOOT | HuntEvent.BLIND).distance(140).build());
      check(b.has("hunt_pronghorn_blind") && !b.has("hunt_pronghorn_master"), "pronghorn: from the blind = technique, not the open-country stalk");
      b.feed(take("pronghorn").flag(HuntEvent.CLEAN | HuntEvent.GLASSED | HuntEvent.ON_FOOT).distance(99).build());
      check(!b.has("hunt_pronghorn_master"), "pronghorn: 99 m is short of 100");
      b.feed(take("elk").flag(HuntEvent.MALE | HuntEvent.RUT | HuntEvent.CALLED | HuntEvent.CLEAN).points(10).kg(250).build());
      check(b.has("hunt_elk_master") && !b.has("hunt_elk_herd_bull"), "elk: called rut 5x5 = master; 250 kg is not a herd bull");
      b.feed(take("moose").flag(HuntEvent.MALE | HuntEvent.RUT | HuntEvent.CALLED | HuntEvent.CLEAN).points(12).build());
      check(!b.has("hunt_moose_master"), "moose: rut bull away from water is not the master");
      b.feed(take("cougar").flag(HuntEvent.HOUND).build());
      check(b.has("hunt_cougar_hound") && !b.has("hunt_cougar_master"), "cougar: hound without snow");
      b.feed(take("cougar").flag(HuntEvent.HOUND | HuntEvent.SNOW).build());
      check(b.has("hunt_cougar_master"), "cougar: hound + snow");
      b.feed(take("boar").flag(HuntEvent.HOUND).time(5000).build());
      check(!b.has("hunt_boar_master"), "boar: hound by day is not the night hunt");
      b.feed(take("boar").flag(HuntEvent.HOUND).time(19000).build());
      check(b.has("hunt_boar_master") && b.has("hunt_boar_night"), "boar: hound at night = master + night");
      b.feed(take("panther").flag(HuntEvent.BAIT | HuntEvent.CLEAN | HuntEvent.STAND).time(19000).build());
      check(b.has("hunt_panther_master") && b.has("hunt_panther_hide"), "panther: bait, stand, night, clean = master + hide");
      b.feed(take("polar_bear").flag(HuntEvent.GLASSED | HuntEvent.UNAWARE | HuntEvent.CLEAN).build());
      check(!b.has("hunt_polar_bear_master"), "polar bear: no snow camo, no master");
      b.feed(take("wolf").flag(HuntEvent.CLEAN | HuntEvent.GLASSED).distance(120).month(5).build());
      check(!b.has("hunt_wolf_master"), "wolf: a June wolf is not a winter wolf");
      b.feed(take("lion").flag(HuntEvent.GLASSED | HuntEvent.UNAWARE).build());
      check(b.has("hunt_lion_stalk"), "lion: spot and stalk");

      // ------------------------------------------------------------------------------------------ tags, contracts, assignments
      Set<String> t = HuntTracker.tags(take("coyote").flag(HuntEvent.CALLED).time(14000).build());
      check(t.contains("coyote:called") && t.contains("*:called") && t.contains("coyote:take") && t.contains("coyote:night") && !t.contains("coyote:clean"),
         "tags of a called night coyote " + t);
      check(HuntTracker.tags(take("whitetail").flag(HuntEvent.STAND).build()).contains("whitetail:standblind"), "stand take tag");
      check(HuntTracker.tags(HuntEvent.of(HuntEvent.Kind.GLASS, "elk").distance(150).build()).contains("*:glass100"), "glass100 tag");
      check(Campaign.CONTRACTS.size() == HuntContracts.BASE + hunts.size() + HuntContracts.EXTRA.size(), "contracts: 5 standing + " + hunts.size() + " species + " + HuntContracts.EXTRA.size() + " extra (" + Campaign.CONTRACTS.size() + ")");
      for (int i = HuntContracts.BASE; i < Campaign.CONTRACTS.size(); i++) {
         HuntBook.Contract k = HuntContracts.at(i);
         Campaign.Mission mi = Campaign.CONTRACTS.get(i);
         check(k != null && mi.event().equals(k.event()) && mi.species().equals(k.species()) && Campaign.matches(mi, k.event(), k.species()),
            "contract " + i + " matches its hunt event " + (k == null ? "" : k.event()));
         // a matching hunt event really carries the contract's tag
         HuntEvent ev = null;
         for (HuntBook.Milestone m : HuntBook.of(k.species()).milestones()) {
            HuntEvent x = HuntExamples.example(m, 1);
            if (x != null && HuntTracker.tags(x).contains(k.species() + ":" + k.tag())) {
               ev = x;
               break;
            }
         }
         if (ev == null && k.tag().equals("take")) {
            ev = take(k.species()).build();
         }
         if (ev == null && k.tag().equals("long100")) {
            ev = take(k.species()).distance(120).build();
         }
         if (ev == null && k.tag().equals("onfoot")) {
            ev = take(k.species()).flag(HuntEvent.ON_FOOT).build();
         }
         if (ev == null && k.tag().equals("night")) {
            ev = take(k.species()).time(15000).build();
         }
         // [1.1.6] the extra contracts' conditions
         if (ev == null) {
            ev = switch (k.tag()) {
               case "doe" -> take(k.species()).build();
               case "rutbuck" -> take(k.species()).flag(HuntEvent.MALE | HuntEvent.RUT).points(8).build();
               case "stalk" -> take(k.species()).flag(HuntEvent.UNAWARE | HuntEvent.ON_FOOT).build();
               case "bow" -> take(k.species()).gun(HuntEvent.Gun.BOW).build();
               case "trailed" -> take(k.species()).flag(HuntEvent.RECOVERED).trail(40).build();
               case "buckcam" -> HuntEvent.of(HuntEvent.Kind.PHOTO, k.species()).flag(HuntEvent.MALE).build();
               case "glass150" -> HuntEvent.of(HuntEvent.Kind.GLASS, k.species()).distance(160).build();
               case "water" -> take(k.species()).flag(HuntEvent.WATER).build();
               case "spread" -> take(k.species()).flag(HuntEvent.DECOY).build();
               default -> null;
            };
         }
         check(ev != null && HuntTracker.tags(ev).contains(k.species() + ":" + k.tag()), "contract " + k.species() + " can be met by a hunt event");
      }
      Map<String, Integer> posted = new HashMap<>();
      for (int mo = 0; mo < 12; mo++) {
         Set<Integer> inSeason = new HashSet<>();
         for (int i = HuntContracts.BASE; i < Campaign.CONTRACTS.size(); i++) {
            if (HuntContracts.at(i).open(mo) && !com.formaworks.frontierhunts.wildlife2026.Beta.animal(HuntContracts.at(i).species())) {
               inSeason.add(i);
            }
         }
         Set<Integer> monthPosted = new HashSet<>();
         for (int half = 0; half < 2; half++) {
            List<Integer> board = HuntContracts.board(mo, half);
            check(board.subList(0, 5).equals(List.of(0, 1, 2, 3, 4)) && board.size() <= 5 + HuntContracts.PER_HALF, "board month " + mo + "/" + half + ": " + board);
            for (int i : HuntContracts.seasonal(mo, half)) {
               check(HuntContracts.at(i).open(mo), "posted contract is in season, month " + mo);
               monthPosted.add(i);
               check(HuntContracts.offered(i, mo, half, false), "posted contract can be taken");
            }
            for (int i = HuntContracts.BASE; i < Campaign.CONTRACTS.size(); i++) {
               if (!HuntContracts.seasonal(mo, half).contains(i)) {
                  check(!HuntContracts.offered(i, mo, half, false), "unposted contract refused, month " + mo + "/" + half + " #" + i);
               }
            }
         }
         // shortest seasons first: everything in season with 8 or fewer candidates is posted within the month
         if (inSeason.size() <= 2 * HuntContracts.PER_HALF) {
            check(monthPosted.equals(inSeason), "month " + mo + ": every in-season species contract posted (" + monthPosted.size() + "/" + inSeason.size() + ")");
         }
         monthPosted.forEach(i -> posted.merge(HuntContracts.at(i).species(), 1, Integer::sum));
      }
      for (HuntBook.Hunt hh : hunts) {
         HuntBook.Contract k = hh.contract();
         int open = Integer.bitCount(k.months());
         if (com.formaworks.frontierhunts.wildlife2026.Beta.animal(hh.species())) {
            // [gear20] beta animals do not spawn in the wild: their contracts are never posted
            check(!posted.containsKey(hh.species()), "beta contract of " + hh.species() + " never posted");
            continue;
         }
         check(posted.getOrDefault(hh.species(), 0) >= Math.min(open, 2), "contract of " + hh.species() + " posted in " + posted.getOrDefault(hh.species(), 0)
            + " of its " + open + " open months");
      }
      check(HuntContracts.half(1, 7) == 0 && HuntContracts.half(4, 7) == 0 && HuntContracts.half(5, 7) == 1 && HuntContracts.half(7, 7) == 1 && HuntContracts.half(1, 1) == 0,
         "half-month split of a 7-day month");
      int fromFirst = HuntContracts.seasonal(9, 0).get(0);
      check(HuntContracts.offered(fromFirst, 9, 1, true), "first day of the second half honours the first half's board");
      File dir = new File(repo, "patch/data/frontierhunts/frontierhunts/assignment");
      int specs = 0;
      for (File f : dir.listFiles()) {
         JsonObject o = JsonParser.parseString(Files.readString(f.toPath(), StandardCharsets.UTF_8)).getAsJsonObject();
         if (!o.has("hunt")) {
            continue;
         }
         specs++;
         String spec = o.get("hunt").getAsString();
         int target = o.get("target").getAsInt();
         check(HuntTracker.validSpec(spec), "assignment " + f.getName() + " spec " + spec);
         String instr = HuntAssignments.instruction(spec, target);
         check(!instr.isEmpty() && !instr.startsWith("Complete"), "assignment " + f.getName() + ": " + instr);
         System.out.println("     " + f.getName() + ": " + instr);
         // some real milestone example satisfies it
         boolean any = false;
         for (String part : spec.split(",")) {
            String sp = part.split(":")[0];
            for (HuntBook.Milestone m : HuntBook.milestones()) {
               if (sp.equals("*") || m.species().equals(sp)) {
                  HuntEvent x = HuntExamples.example(m, 1);
                  any |= x != null && HuntTracker.accepts(spec, HuntTracker.tags(x));
               }
            }
         }
         check(any, "assignment " + f.getName() + " is met by a hunt event");
      }
      check(specs == 3, "three species-hunt assignments, beta-animal ones removed [gear20] (" + specs + ")");
      check(HuntTracker.accepts("coyote:called,wolf:called", HuntTracker.tags(take("wolf").flag(HuntEvent.CALLED).build())), "accepts a wolf for predator control");
      check(!HuntTracker.accepts("boar:night", HuntTracker.tags(take("boar").time(4000).build())), "a daylight hog is not night hog control");
      check(!HuntTracker.validSpec("moose:nonsense") && !HuntTracker.validSpec("unicorn:take"), "bad specs rejected");

      // ------------------------------------------------------------------------------------------ carry-over
      Hunter old = new Hunter();
      List<HuntTracker.Update> ups = HuntTracker.carryOver(HuntBook.of("black_bear"), 3, 1, 2, 1450, old);
      Set<String> keys = new HashSet<>();
      ups.forEach(u -> keys.add(u.key()));
      check(keys.equals(Set.of("hunt.black_bear.cam", "hunt.black_bear.clean", "hunt.black_bear.big")), "carry-over: bear photo + take + 145 kg " + keys);
      check(HuntTracker.carryOver(HuntBook.of("black_bear"), 3, 1, 2, 1200, old).stream().noneMatch(u -> u.key().endsWith(".big")), "carry-over: 120 kg bear is not big");
      check(HuntTracker.carryOver(HuntBook.of("whitetail"), 5, 0, 3, 900, old).stream().map(HuntTracker.Update::key).toList().equals(List.of("hunt.whitetail.clean")),
         "carry-over: whitetail take only (no points recorded)");
      check(HuntTracker.carryOver(HuntBook.of("moose"), 1, 0, 0, 0, old).stream().map(HuntTracker.Update::key).toList().equals(List.of("hunt.moose.seen")), "carry-over: moose seen");
      check(HuntTracker.carryOver(HuntBook.of("elk"), 9, 0, 0, 0, old).isEmpty(), "carry-over: seen elk does not count for glassing");
      old.counters.put("hunt.black_bear.cam", 1);
      check(HuntTracker.carryOver(HuntBook.of("black_bear"), 3, 1, 0, 0, old).isEmpty(), "carry-over: done milestones skipped");

      // ------------------------------------------------------------------------------------------ journal integration
      int inCat = 0;
      for (Checklist.Entry e : Checklist.all()) {
         if (e.category() == Checklist.Category.HUNTS) {
            inCat++;
            HuntBook.Milestone m = HuntBook.byId(e.id());
            check(m != null && e.counter().equals(m.counter()) && e.target() == m.target() && e.xp() == m.xp() && m.species().equals(e.species())
               && e.icon().equals("hunt:" + m.icon()), "checklist entry " + e.id());
         }
      }
      check(inCat == total, "every milestone is a checklist entry (" + inCat + ")");
      check(Checklist.watching("hunt.grouse.wing").size() == 2, "grouse wing counter feeds 2 entries");
      check(Checklist.byId("sp_whitetail") != null && Checklist.byId("clean_1") != null, "existing checklist entries untouched");
      // a fully progressed hunter still syncs compactly
      HunterRecord r = new HunterRecord();
      for (HuntBook.Milestone m : HuntBook.milestones()) {
         r.counters.put(m.counter(), m.target());
         r.done.add(m.id());
      }
      for (int i = 0; i < 120; i++) {
         r.counters.put("other_" + i, i);
      }
      FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
      r.writeSync(buf);
      int bytes = buf.readableBytes();
      HunterRecord back = HunterRecord.readSync(buf);
      check(back.done.containsAll(r.done) && back.get("hunt.duck.master") == 4, "sync round trip with all hunt progress");
      check(bytes < 9000 && r.counters.size() < HunterRecord.MAX_COUNTERS, "sync size " + bytes + " bytes, " + r.counters.size() + " counters");

      // ------------------------------------------------------------------------------------------ lang keys the code builds
      Map<String, String> lang = new HashMap<>();
      JsonObject frag = JsonParser.parseString(Files.readString(Path.of(repo, "patch/_merge/assets/frontierhunts/lang/en_us.json/hunts.json"))).getAsJsonObject();
      frag.entrySet().forEach(en -> lang.put(en.getKey(), en.getValue().getAsString()));
      List<String> need = new ArrayList<>();
      for (HuntBook.Milestone m : HuntBook.milestones()) {
         need.add("journal.frontierhunts.check." + m.id());
         need.add("journal.frontierhunts.check." + m.id() + ".hint");
      }
      for (HuntBook.Hunt hh : hunts) {
         for (String k : new String[]{"tagline", "brief", "where", "title"}) {
            need.add("hunts.frontierhunts." + hh.species() + "." + k);
         }
         need.add("hunts.frontierhunts.contract.how." + hh.contract().tag());
      }
      for (HuntBook.Tier tier : HuntBook.Tier.values()) {
         need.add("hunts.frontierhunts.tier." + tier.key());
      }
      need.addAll(List.of("journal.frontierhunts.cat.hunts", "hunts.frontierhunts.reward.xp", "hunts.frontierhunts.reward.tokens", "hunts.frontierhunts.reward.title",
         "hunts.frontierhunts.card.back", "hunts.frontierhunts.card.progress", "hunts.frontierhunts.card.title_locked", "hunts.frontierhunts.card.stats",
         "hunts.frontierhunts.card.how", "hunts.frontierhunts.card.where", "hunts.frontierhunts.card.contract", "hunts.frontierhunts.card.milestones",
         "hunts.frontierhunts.card.needs", "hunts.frontierhunts.card.in_a_day", "hunts.frontierhunts.card.footer", "hunts.frontierhunts.note.reward",
         "hunts.frontierhunts.note.reward_tokens", "hunts.frontierhunts.note.master", "hunts.frontierhunts.note.migrated", "hunts.frontierhunts.chat.master",
         "hunts.frontierhunts.msg.delivered", "hunts.frontierhunts.msg.bait", "hunts.frontierhunts.msg.decoys", "hunts.frontierhunts.msg.duck_call",
         "hunts.frontierhunts.msg.duck_call_nodecoys", "hunts.frontierhunts.contract.board", "hunts.frontierhunts.contract.board_none",
         "hunts.frontierhunts.assign.how", "item.frontierhunts.duck_call", "block.frontierhunts.mallard_decoy", "subtitles.frontierhunts.duck_call",
         "hunts.frontierhunts.species.intro", "hunts.frontierhunts.species.tip_hunt", "hunts.frontierhunts.check.open_card", "hunts.frontierhunts.sub.card",
         "hunts.frontierhunts.or", "hunts.frontierhunts.year_round"));
      int missing = 0;
      for (String k : need) {
         if (!lang.containsKey(k)) {
            System.out.println("     missing lang " + k);
            missing++;
         }
      }
      check(missing == 0, "lang: " + need.size() + " keys the code builds are in the fragment");
      long plain = lang.values().stream().filter(v -> v.length() > 240).count();
      check(plain == 0, "lang: no text longer than 240 characters");

      // ------------------------------------------------------------------------------------------ audit dump
      JsonArray arr = new JsonArray();
      for (HuntBook.Milestone m : HuntBook.milestones()) {
         JsonObject o = new JsonObject();
         o.addProperty("id", m.id());
         o.addProperty("species", m.species());
         o.addProperty("tier", m.tier().key());
         o.addProperty("kind", m.kind().name());
         o.addProperty("hook", m.hook());
         o.addProperty("target", m.target());
         o.addProperty("mode", m.mode().name());
         o.addProperty("xp", m.xp());
         o.addProperty("tokens", m.tokens());
         o.addProperty("item", m.item());
         o.addProperty("icon", m.icon());
         o.addProperty("count", m.count());
         JsonArray needs = new JsonArray();
         m.needs().forEach(needs::add);
         o.add("needs", needs);
         o.addProperty("title", lang.getOrDefault("journal.frontierhunts.check." + m.id(), ""));
         o.addProperty("rule", lang.getOrDefault("journal.frontierhunts.check." + m.id() + ".hint", ""));
         HuntEvent ex = HuntExamples.example(m, 1);
         o.addProperty("example", ex == null ? "" : ex.toString());
         o.addProperty("spawn", HuntBook.of(m.species()).spawnTag());
         arr.add(o);
      }
      Path out = Path.of(repo, "docs/ws/hunts/milestones.json");
      Files.createDirectories(out.getParent());
      Files.writeString(out, arr.toString());

      System.out.println(fails == 0 ? "ALL PASS (" + passes + " checks)" : fails + " FAILED, " + passes + " passed");
      System.exit(fails == 0 ? 0 : 1);
   }

   /** The icon names the client atlas knows (kept in step with hunts/client/HuntIcons by reading its source). */
   static final class HuntIconsNames {
      static Set<String> names;

      static boolean has(String n) {
         if (names == null) {
            names = new HashSet<>(List.of("reticle", "glass", "antlers", "boot", "eye", "stalk"));
            try {
               String src = Files.readString(Path.of(System.getProperty("hunts.repo", "."), "src/com/formaworks/frontierhunts/hunts/client/HuntIcons.java"));
               String body = src.substring(src.indexOf("GENERATED-NAMES-BEGIN"), src.indexOf("GENERATED-NAMES-END"));
               java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"([a-z_]+)\"").matcher(body);
               while (m.find()) {
                  names.add(m.group(1));
               }
            } catch (Exception e) {
               throw new RuntimeException(e);
            }
         }
         return names.contains(n);
      }
   }
}
