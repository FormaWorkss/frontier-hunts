package com.formaworks.frontierhunts.regions;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongPredicate;

/** [regions] Offline tests of the arrival-card trigger (hysteresis, cooldowns, suppression, modes) with a simulated walker. */
public final class ArrivalHarness {
   static int fails, checks;
   static final long REPEAT = 6000L; // 5 min

   static void check(boolean ok, String what) {
      checks++;
      if (!ok) {
         fails++;
         System.out.println("FAIL " + what);
      } else {
         System.out.println("ok   " + what);
      }
   }

   /** A walker sampled every 5 ticks like ArrivalCards. */
   static final class Walk {
      final ArrivalTrigger t = new ArrivalTrigger();
      static final long T0 = 1000; // join card shown at tick 0, walk starts after the 12 s card gap
      long tick = T0;
      double x;
      boolean regions = true, biomes = true;
      LongPredicate suppressed = k -> false;
      final List<String> fired = new ArrayList<>();
      final List<Long> firedAt = new ArrayList<>();

      Walk(String biome, String region) {
         this.t.seed(biome, region, 0, true); // join card showed it
      }

      /** Spend {@code seconds} in {@code biome}, moving {@code speed} blocks per second. */
      Walk stay(double seconds, String biome, String region, double speed) {
         long end = this.tick + Math.round(seconds * 20.0);
         while (this.tick < end) {
            this.tick += 5;
            this.x += speed * 0.25;
            ArrivalTrigger.Fire f = this.t.update(this.tick, biome, region, this.x, 0.0, this.suppressed.test(this.tick - T0), this.regions, this.biomes, REPEAT);
            if (f != null) {
               this.fired.add(f.kind() + ":" + f.biome());
               this.firedAt.add(this.tick - T0);
            }
         }
         return this;
      }

      Walk teleport(double dx) {
         this.x += dx;
         return this;
      }

      int count() {
         return this.fired.size();
      }

      String last() {
         return this.fired.isEmpty() ? "" : this.fired.get(this.fired.size() - 1);
      }
   }

   public static void main(String[] args) {
      // 1. staying put never fires
      Walk w = new Walk("forest", "Pinewood").stay(120, "forest", "Pinewood", 4);
      check(w.count() == 0, "same biome for 2 min: no card");

      // 2. border flicker (alternating samples for a minute) never fires
      w = new Walk("forest", "Pinewood");
      for (int i = 0; i < 120; i++) {
         w.stay(0.25, i % 2 == 0 ? "taiga" : "forest", "Pinewood", 4);
      }
      check(w.count() == 0, "border flicker for 30 s: no card");

      // 3. walking into a new biome: card after the 3 s dwell (with >= 16 blocks walked)
      w = new Walk("forest", "Pinewood").stay(6, "taiga", "Pinewood", 5);
      check(w.count() == 1 && w.last().equals("BIOME:taiga"), "walk into taiga: one BIOME card (" + w.fired + ")");
      check(w.firedAt.get(0) >= 60 && w.firedAt.get(0) <= 80, "card after ~3 s dwell (at tick " + w.firedAt.get(0) + ")");

      // 4. standing just over a border: distance not met -> waits for the long dwell (10 s)
      w = new Walk("forest", "Pinewood").stay(9, "taiga", "Pinewood", 0);
      check(w.count() == 0, "standing still 9 s inside a new biome: not yet");
      w.stay(2, "taiga", "Pinewood", 0);
      check(w.count() == 1, "standing still: card after the 10 s long dwell");

      // 5. short detour (< dwell) does nothing and does not reset the current biome
      w = new Walk("forest", "Pinewood").stay(2, "river", "Pinewood", 6).stay(20, "forest", "Pinewood", 6);
      check(w.count() == 0, "2 s river crossing: no card");
      check("forest".equals(w.t.biome()), "current biome still forest after detour");

      // 6. a new region fires a REGION card
      w = new Walk("forest", "Pinewood").stay(5, "plains", "Open Plains", 6);
      check(w.last().equals("REGION:plains"), "new region: REGION card (" + w.fired + ")");

      // 7. biome repeat cooldown: back to the joined biome inside 5 min is silent, after 5 min it shows again
      w = new Walk("forest", "Pinewood").stay(20, "taiga", "Pinewood", 5).stay(20, "forest", "Pinewood", 5);
      check(w.fired.equals(List.of("BIOME:taiga")), "forest -> taiga -> forest within 5 min: only taiga (" + w.fired + ")");
      w.stay(300, "forest", "Pinewood", 3).stay(20, "taiga", "Pinewood", 5);
      check(w.count() == 2 && w.last().equals("BIOME:taiga"), "taiga again after > 5 min: shown again (" + w.fired + ")");

      // 8. a different region bypasses the biome cooldown
      w = new Walk("forest", "Pinewood").stay(20, "plains", "Open Plains", 5).stay(20, "forest", "Pinewood", 5);
      check(w.fired.equals(List.of("REGION:plains")), "back into Pinewood within 3 min: region cooldown -> silent (" + w.fired + ")");
      w.stay(200, "forest", "Pinewood", 2).stay(20, "plains", "Open Plains", 5);
      // plains biome shown < 5 min ago, but region change after 90 s region cooldown -> REGION card
      check(w.count() == 2 && w.last().equals("REGION:plains"), "region change shows even though the biome was shown 4 min ago (" + w.fired + ")");

      // 9. region ping-pong along a border stays quiet
      w = new Walk("forest", "Pinewood");
      for (int i = 0; i < 8; i++) {
         w.stay(15, i % 2 == 0 ? "plains" : "forest", i % 2 == 0 ? "Open Plains" : "Pinewood", 4);
      }
      check(w.count() == 1, "8 region crossings in 2 min: one card (" + w.fired + " at " + w.firedAt + ")");
      // ...but a third, genuinely new region still shows right away
      w.stay(20, "snowy_plains", "Frozen Tundra", 5);
      check(w.count() == 2 && w.last().equals("REGION:snowy_plains"), "a new third region still shows (" + w.fired + ")");

      // 10. global gap: two biomes 5 s apart -> second held and shown when the 12 s gap ends
      w = new Walk("forest", "Pinewood").stay(5, "taiga", "Pinewood", 6).stay(20, "snowy_taiga", "Pinewood", 6);
      check(w.count() == 2, "two quick biomes: both shown (" + w.fired + ")");
      check(w.count() == 2 && w.firedAt.get(1) - w.firedAt.get(0) >= 240, "second card waits for the 12 s gap (" + w.firedAt + ")");

      // 11. suppression (menu/combat) holds the card and shows it right after
      Walk s = new Walk("forest", "Pinewood");
      s.suppressed = k -> k < 200;
      s.stay(15, "taiga", "Pinewood", 5);
      check(s.count() == 1 && s.firedAt.get(0) >= 200 && s.firedAt.get(0) <= 205, "held during combat, shown when it ends (" + s.firedAt + ")");

      // 12. suppressed for too long: dropped silently, never shown late
      s = new Walk("forest", "Pinewood");
      s.suppressed = k -> k < 60 * 20;
      s.stay(90, "taiga", "Pinewood", 5);
      check(s.count() == 0, "suppressed for 60 s: stale card dropped");
      check("taiga".equals(s.t.biome()), "...but the biome is accepted as current");

      // 13. modes
      Walk off = new Walk("forest", "Pinewood");
      off.regions = false;
      off.biomes = false;
      off.stay(20, "plains", "Open Plains", 5).stay(20, "taiga", "Pinewood", 5);
      check(off.count() == 0 && "taiga".equals(off.t.biome()), "Off: no cards, tracking continues");
      Walk reg = new Walk("forest", "Pinewood");
      reg.biomes = false;
      reg.stay(20, "taiga", "Pinewood", 5).stay(20, "plains", "Open Plains", 5);
      check(reg.fired.equals(List.of("REGION:plains")), "Regions only: biome change silent, region shown (" + reg.fired + ")");

      // 14. teleport into a new biome counts as distance (no 10 s wait)
      w = new Walk("forest", "Pinewood").stay(1, "forest", "Pinewood", 0);
      w.stay(0.25, "desert", "Uncharted country", 0);
      w.teleport(500).stay(4, "desert", "Uncharted country", 0);
      check(w.count() == 1 && w.firedAt.get(0) <= 90, "teleport into a desert: card after the normal dwell (" + w.firedAt + ")");

      // 15. returning to the current biome while a candidate is held clears it
      w = new Walk("forest", "Pinewood");
      w.suppressed = k -> true;
      w.stay(5, "taiga", "Pinewood", 5).stay(1, "forest", "Pinewood", 5);
      check(w.t.candidate() == null && "forest".equals(w.t.biome()), "back to forest clears the held candidate");

      // 16. reset(false) keeps cooldown memory; reset(true) clears it
      w = new Walk("forest", "Pinewood").stay(10, "taiga", "Pinewood", 5);
      w.t.reset(false);
      w.t.seed("forest", "Pinewood", w.tick, false);
      w.stay(20, "forest", "Pinewood", 0).stay(10, "taiga", "Pinewood", 5);
      check(w.count() == 1, "dimension round trip keeps the repeat memory (" + w.fired + ")");
      w.t.reset(true);
      w.t.seed("forest", "Pinewood", w.tick, false);
      w.stay(10, "taiga", "Pinewood", 5);
      check(w.count() == 2, "reset(true) forgets it");

      System.out.println(fails == 0 ? "ALL " + checks + " CHECKS PASSED" : fails + " of " + checks + " CHECKS FAILED");
      System.exit(fails == 0 ? 0 : 1);
   }
}
