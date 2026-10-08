package com.formaworks.frontierhunts.wingshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * [wingshot] Offline checks of the flight model/pilot on a synthetic world (rolling hills, two lakes, a forest of
 * trunks under a leaf canopy, a cliff): no NaN, no collisions with the ground or trunks, no altitude overshoot or
 * heading oscillation in cruise, landings succeed where they should (on the spread, into the wind), flocks keep their
 * spacing, grouse flushes stay low and weave through the trunks, dead birds fall like real ones.
 *
 * <p>Run: tools/wingshot/harness/run.sh  (prints PASS/FAIL lines and writes flight tracks as CSV for plotting)
 */
public final class FlightHarness {
   static int fails, checks;

   static void check(boolean ok, String what) {
      checks++;
      if (!ok) {
         fails++;
         System.out.println("FAIL " + what);
      }
   }

   // ================================================================ synthetic world
   static final class Synth implements Flight.World {
      static final double LAKE1X = 0, LAKE1Z = 0, LAKE2X = 260, LAKE2Z = 90, LAKER = 34;
      final List<double[]> trunks = new ArrayList<>();

      Synth() {
         // forest: x in [-200, -60], z in [-80, 80], a trunk roughly every 5.5 blocks (a dense Minecraft wood)
         Flight.Rng r = new Flight.Rng(7);
         for (double x = -200; x <= -60; x += 5.5) {
            for (double z = -80; z <= 80; z += 5.5) {
               trunks.add(new double[]{x + r.range(-1.5, 1.5), z + r.range(-1.5, 1.5)});
            }
         }
      }

      boolean forest(double x, double z) {
         return x >= -202 && x <= -58 && z >= -82 && z <= 82;
      }

      double ground(double x, double z) {
         double g = 64 + 3.0 * Math.sin(x / 23.0) + 2.0 * Math.cos(z / 17.0);
         // a cliff band to the north east
         if (x > 80 && x < 140 && z < -40) {
            g += 22;
         }
         return g;
      }

      boolean lake(double x, double z) {
         return Math.hypot(x - LAKE1X, z - LAKE1Z) < LAKER || Math.hypot(x - LAKE2X, z - LAKE2Z) < LAKER;
      }

      @Override
      public double floor(double x, double z, boolean canopy) {
         if (Math.abs(x) > 2000 || Math.abs(z) > 2000) {
            return Double.NaN;
         }
         if (lake(x, z)) {
            return 61.9;
         }
         double g = ground(x, z);
         if (canopy && forest(x, z)) {
            return g + 9.0;
         }
         return g;
      }

      boolean solid(double x, double y, double z) {
         if (lake(x, z)) {
            return y < 61.9;
         }
         double g = ground(x, z);
         if (y < g) {
            return true;
         }
         if (forest(x, z)) {
            if (y > g + 5.0 && y < g + 9.0) {
               return true; // leaf canopy
            }
            if (y < g + 9.0) {
               for (double[] t : trunks) {
                  double dx = x - t[0], dz = z - t[1];
                  if (dx * dx + dz * dz < 0.5 * 0.5) {
                     return true;
                  }
               }
            }
         }
         return false;
      }

      @Override
      public double clear(double x, double y, double z, double dx, double dy, double dz, double max) {
         for (double s = 0.25; s <= max; s += 0.25) {
            if (solid(x + dx * s, y + dy * s, z + dz * s)) {
               return s - 0.25;
            }
         }
         return max;
      }

      /** distance from (x, z) to the nearest trunk axis */
      double trunkDist(double x, double z) {
         double best = 99;
         for (double[] t : trunks) {
            best = Math.min(best, Math.hypot(x - t[0], z - t[1]));
         }
         return best;
      }
   }

   static final Synth W = new Synth();

   /** Lakes as landing answers: the other lake, else the start one. */
   static void answer(Flight f) {
      if (f.needLanding) {
         double d1 = Math.hypot(f.x - Synth.LAKE1X, f.z - Synth.LAKE1Z), d2 = Math.hypot(f.x - Synth.LAKE2X, f.z - Synth.LAKE2Z);
         if (d2 < d1) {
            f.land(Synth.LAKE2X + 5, 61.9, Synth.LAKE2Z - 4, true);
         } else {
            f.land(Synth.LAKE1X - 6, 61.9, Synth.LAKE1Z + 3, true);
         }
      }
   }

   static final class Track {
      final StringBuilder csv = new StringBuilder("t,x,y,z,speed,heading,vy,phase,stage,floor\n");
      int collisions, hardHits, nan, belowFloor;
      double maxAboveFloor, minTrunk = 99;
      int omegaFlips;
      double lastOmega;
   }

   /** Fly one bird to the end (or tmax); collisions are checked like Minecraft's move() would stop it. */
   static Track run(Flight f, int tmax, String name, List<Flight> others) {
      Track tr = new Track();
      for (int t = 0; t < tmax && !(f.done || f.landed); t++) {
         answer(f);
         double px = f.x, py = f.y, pz = f.z;
         f.tick(W);
         if (others != null) {
            for (Flight o : others) {
               if (!(o.done || o.landed)) {
                  answer(o);
                  o.tick(W);
               }
            }
         }
         record(f, tr, px, py, pz, t);
      }
      if (name != null) {
         try {
            java.nio.file.Files.writeString(java.nio.file.Path.of(System.getProperty("out", "/tmp"), name + ".csv"), tr.csv.toString());
         } catch (Exception ignored) {
         }
      }
      return tr;
   }

   static void record(Flight f, Track tr, double px, double py, double pz, int t) {
      if (!Double.isFinite(f.x + f.y + f.z + f.speed + f.heading + f.vy)) {
         tr.nan++;
      }
      double fl = W.floor(f.x, f.z, false);
      if (W.solid(f.x, f.y + 0.1, f.z) && f.mission != Flight.Mission.FALL) {
         tr.collisions++;
         if (f.speed > 0.35) {
            tr.hardHits++;
         }
         // Minecraft's move() slides along what it hits: keep whichever axis is free
         double nx = f.x, ny = f.y, nz = f.z;
         f.x = px;
         f.y = py;
         f.z = pz;
         if (!W.solid(nx, py + 0.1, pz)) {
            f.x = nx;
         } else if (!W.solid(px, py + 0.1, nz)) {
            f.z = nz;
         }
         if (!W.solid(f.x, ny + 0.1, f.z)) {
            f.y = ny;
         }
         f.speed *= 0.6;
      }
      if (f.y < fl - 0.15) {
         tr.belowFloor++;
      }
      if (f.mission != Flight.Mission.FALL && !f.landed) {
         tr.maxAboveFloor = Math.max(tr.maxAboveFloor, f.y - fl);
      }
      if (W.forest(f.x, f.z) && f.y < W.ground(f.x, f.z) + 9) {
         tr.minTrunk = Math.min(tr.minTrunk, W.trunkDist(f.x, f.z));
      }
      if (t % 2 == 0) {
         tr.csv.append(String.format(Locale.ROOT, "%d,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%d,%d,%.2f\n", t, f.x, f.y, f.z, f.speed, f.heading, f.vy, f.phase, f.stage(), fl));
      }
   }

   public static void main(String[] a) {
      String out = System.getProperty("out", "/tmp");
      // ------------------------------------------------ 1. duck spooked off a lake, flies to the other lake and lands
      int landedFlee = 0;
      double worstOvershoot = 0;
      int worstFlips = 0;
      for (int i = 0; i < 40; i++) {
         Flight f = new Flight(Flight.Kind.DUCK, 1000 + i).at(Synth.LAKE1X + 3, 61.9, Synth.LAKE1Z + 2, 0.0, 0.0, 0.0).wind(1.5, -0.8);
         // threat to the west so the duck heads east, toward the other lake
         f.flee(Synth.LAKE1X - 12 + (i % 5), Synth.LAKE1Z - 6 + (i % 7), false);
         Track tr = run(f, 3000, i == 0 ? "duck_flee" : null, null);
         check(tr.nan == 0, "duck flee " + i + " NaN");
         check(tr.collisions == 0, "duck flee " + i + " collisions " + tr.collisions);
         check(tr.belowFloor == 0, "duck flee " + i + " below floor " + tr.belowFloor);
         if (f.landed) {
            landedFlee++;
         }
         check(f.landed && f.onWaterAtEnd, "duck flee " + i + " landed on water (landed=" + f.landed + " stage " + f.stage() + ")");
         check(tr.maxAboveFloor < 45, "duck flee " + i + " max height " + tr.maxAboveFloor);
         check(tr.maxAboveFloor > 14, "duck flee " + i + " got up to cruising height " + tr.maxAboveFloor);
      }
      // cruise smoothness: terrain-following cruise toward a far target over rolling hills
      {
         Flight f = new Flight(Flight.Kind.DUCK, 5).at(-20, 90, 300, 0.0, 0.95, 0.0);
         int turning = 0, signChanges = 0;
         double lastVy = 0, maxDev = 0;
         for (int t = 0; t < 900; t++) {
            double cruise = W.floor(f.x, f.z, true) + 22.0;
            f.fly(W, 5000, 300, 0.95, cruise, true);
            if (t > 250) {
               maxDev = Math.max(maxDev, Math.abs(f.y - f.dbgAlt));
               if (Math.signum(f.vy) != Math.signum(lastVy) && Math.abs(f.vy) > 0.004) {
                  signChanges++;
               }
               lastVy = f.vy;
               if (Math.abs(f.omega) > 0.002) {
                  turning++;
               }
            }
         }
         check(maxDev < 2.0, String.format(Locale.ROOT, "cruise altitude hold within 2 blocks of the terrain-following target (worst %.2f)", maxDev));
         check(signChanges < 40, "cruise vertical speed follows the hills without dithering (" + signChanges + " sign changes over 650 ticks)");
         check(turning < 10, "straight cruise keeps a steady heading (" + turning + " turning ticks)");
         worstOvershoot = maxDev;
      }
      // altitude step response: climb to +20 and stop without overshoot
      {
         Flight f = new Flight(Flight.Kind.DUCK, 9).at(-20, 90, 400, 0.0, 0.95, 0.0);
         double base = W.floor(-20, 400, true);
         double peak = -1e9;
         for (int t = 0; t < 400; t++) {
            f.fly(W, 3000, 400, 0.95, 110, false);
            peak = Math.max(peak, f.y);
         }
         check(peak < 110.6, String.format(Locale.ROOT, "altitude step (+20): no overshoot (peak %.2f vs 110)", peak));
         check(Math.abs(f.y - 110) < 0.5, String.format(Locale.ROOT, "altitude step settles (%.2f)", f.y));
      }
      // turn response: 180 degree turn, no heading overshoot beyond 15 degrees
      {
         Flight f = new Flight(Flight.Kind.DUCK, 11).at(0, 100, 600, 0.0, 0.95, 0.0);
         double worst = 0;
         int t180 = -1;
         for (int t = 0; t < 400; t++) {
            f.fly(W, -5000, 600, 0.95, 100, false);
            double err = Flight.wrap(Math.PI - f.heading);
            if (t180 < 0 && Math.abs(err) < 0.05) {
               t180 = t;
            }
            if (t180 >= 0) {
               worst = Math.max(worst, Math.abs(err));
            }
         }
         check(t180 > 0 && t180 < 160, "duck reverses course in " + t180 + " ticks");
         check(worst < 0.26, String.format(Locale.ROOT, "turn overshoot %.1f deg", Math.toDegrees(worst)));
         double r = 0.95 / (Flight.G * 2.6 / 0.95);
         check(r > 8 && r < 30, String.format(Locale.ROOT, "cruise turn radius %.1f blocks (real mallard ~10-25 m)", r));
      }
      // ------------------------------------------------ 2. lured onto a decoy spread: circles, finals into the wind, lands on the spot
      int lureOk = 0;
      double worstMiss = 0, worstWindErr = 0;
      for (int i = 0; i < 40; i++) {
         double ang = i * 0.7;
         double wx = Math.cos(ang * 1.3) * 2.0, wz = Math.sin(ang * 1.3) * 2.0;
         Flight f = new Flight(Flight.Kind.DUCK, 2000 + i).at(Synth.LAKE2X + Math.cos(ang) * 28, 61.9, Synth.LAKE2Z + Math.sin(ang) * 28, ang + 2, 0, 0).wind(wx, wz);
         double lx = Synth.LAKE1X + 4, lz = Synth.LAKE1Z - 3;
         f.lure(lx, 61.9, lz, true, false);
         Track tr = run(f, 4000, i == 0 ? "duck_lure" : null, null);
         check(tr.nan == 0 && tr.collisions == 0 && tr.belowFloor == 0, "lure " + i + " clean flight (nan " + tr.nan + " coll " + tr.collisions + " below " + tr.belowFloor + ")");
         double miss = Math.hypot(f.x - lx, f.z - lz);
         worstMiss = Math.max(worstMiss, miss);
         // touchdown heading vs into-the-wind heading
         double into = Math.atan2(-wz, -wx);
         double werr = Math.abs(Flight.wrap(into - f.heading));
         if (f.landed && miss < 4.0) {
            lureOk++;
            worstWindErr = Math.max(worstWindErr, werr);
         }
         check(f.landed && miss < 4.0, String.format(Locale.ROOT, "lure %d lands on the spread (landed %s, %.1f blocks off, stage %d)", i, f.landed, miss, f.stage()));
      }
      check(worstWindErr < Math.toRadians(75), String.format(Locale.ROOT, "lured ducks land roughly into the wind (worst %.0f deg)", Math.toDegrees(worstWindErr)));
      // ------------------------------------------------ 3. a flock of five: leader + four mates
      for (int k = 0; k < 6; k++) {
         Flight lead = new Flight(Flight.Kind.DUCK, 3000 + k).at(Synth.LAKE1X, 61.9, Synth.LAKE1Z, 0.3, 0, 0).wind(-1.0, 1.0);
         lead.flee(Synth.LAKE1X - 10, Synth.LAKE1Z - 3, false);
         List<Flight> mates = new ArrayList<>();
         for (int i = 1; i <= 4; i++) {
            Flight m = new Flight(Flight.Kind.DUCK, 3100 + k * 10 + i).at(Synth.LAKE1X + i * 1.3, 61.9, Synth.LAKE1Z - i, 0.3, 0, 0).wind(-1.0, 1.0);
            m.follow(lead, i, k % 2 == 1, 2 + i * 2, false);
            mates.add(m);
         }
         double minSep = 99;
         int steps = 0;
         for (int t = 0; t < 3500; t++) {
            boolean all = lead.landed || lead.done;
            if (!all) {
               answer(lead);
               lead.tick(W);
            }
            for (Flight m : mates) {
               if (!(m.landed || m.done)) {
                  all = false;
                  answer(m);
                  double px = m.x, py = m.y, pz = m.z;
                  m.tick(W);
                  if (W.solid(m.x, m.y + 0.1, m.z)) {
                     check(false, "flock " + k + " mate collided");
                     m.x = px;
                     m.y = py;
                     m.z = pz;
                  }
               }
            }
            if (all) {
               break;
            }
            // spacing in the air (after everyone is up)
            if (t > 60 && t % 2 == 0) {
               List<Flight> air = new ArrayList<>();
               if (!lead.landed && lead.y - W.floor(lead.x, lead.z, false) > 3) air.add(lead);
               for (Flight m : mates) if (!m.landed && m.y - W.floor(m.x, m.z, false) > 3) air.add(m);
               for (int i = 0; i < air.size(); i++) {
                  for (int j = i + 1; j < air.size(); j++) {
                     Flight p = air.get(i), q = air.get(j);
                     minSep = Math.min(minSep, Math.sqrt(Flight.sq(p.x - q.x) + Flight.sq(p.y - q.y) + Flight.sq(p.z - q.z)));
                  }
               }
               steps++;
            }
         }
         check(lead.landed, "flock " + k + " leader landed");
         int landedMates = 0;
         double spread = 0;
         for (Flight m : mates) {
            if (m.landed) landedMates++;
            spread = Math.max(spread, Math.hypot(m.x - lead.x, m.z - lead.z));
         }
         check(landedMates == 4, "flock " + k + " all mates landed (" + landedMates + "/4)");
         check(minSep > 0.7, String.format(Locale.ROOT, "flock %d mates never closer than 0.7 blocks in the air (min %.2f)", k, minSep));
         check(spread < 30, String.format(Locale.ROOT, "flock %d lands together (%.1f blocks apart)", k, spread));
      }
      // leader shot mid-air: mates flare and carry on, then land
      {
         Flight lead = new Flight(Flight.Kind.DUCK, 77).at(0, 61.9, 0, 0, 0, 0);
         lead.flee(-10, 0, false);
         List<Flight> mates = new ArrayList<>();
         for (int i = 1; i <= 3; i++) {
            Flight m = new Flight(Flight.Kind.DUCK, 770 + i).at(i, 61.9, -i, 0, 0, 0);
            m.follow(lead, i, false, i * 2, false);
            mates.add(m);
         }
         for (int t = 0; t < 4000; t++) {
            if (t == 120) lead.fall(0.05, 0.02, 0.0);
            lead.tick(W);
            for (Flight m : mates) {
               if (!(m.landed || m.done)) {
                  answer(m);
                  m.tick(W);
               }
            }
         }
         check(lead.landed && lead.mission == Flight.Mission.FALL, "shot leader fell to the ground");
         for (Flight m : mates) {
            check(m.mission == Flight.Mission.FLEE && m.landed, "mate flared after the leader was shot and landed later (mission " + m.mission + " landed " + m.landed + ")");
         }
      }
      // ------------------------------------------------ 4. grouse flushes in the forest
      int gLanded = 0, gColl = 0, gTrunkHits = 0, gHard = 0;
      double gMinDist = 1e9, gMaxDist = 0, gMaxHeight = 0, gMinTrunk = 99;
      int gTimeMax = 0;
      for (int i = 0; i < 200; i++) {
         Flight.Rng r = new Flight.Rng(i);
         double x0 = r.range(-180, -90), z0 = r.range(-60, 60);
         while (W.trunkDist(x0, z0) < 1.0) x0 += 1.1;
         double g = W.ground(x0, z0);
         Flight f = new Flight(Flight.Kind.GROUSE, 4000 + i).at(x0, g, z0, r.range(-3, 3), 0, 0);
         double ta = r.range(0, 6.28);
         f.flee(x0 + Math.cos(ta) * 4, z0 + Math.sin(ta) * 4, false);
         Track tr = run(f, 800, i == 0 ? "grouse_flush" : null, null);
         if (f.landed) gLanded++;
         gColl += tr.collisions;
         gHard += tr.hardHits;
         check(tr.nan == 0, "grouse " + i + " NaN");
         double flown = Math.hypot(f.x - x0, f.z - z0);
         gMinDist = Math.min(gMinDist, flown);
         gMaxDist = Math.max(gMaxDist, flown);
         gMaxHeight = Math.max(gMaxHeight, tr.maxAboveFloor);
         gMinTrunk = Math.min(gMinTrunk, tr.minTrunk);
         gTimeMax = Math.max(gTimeMax, f.age);
         if (tr.minTrunk < 0.5) gTrunkHits++;
      }
      check(gLanded == 200, "grouse: all 200 flushes landed (" + gLanded + ")");
      check(gHard <= 10, "grouse: at most 10 hard hits (>7 m/s) in 200 flushes through a dense trunk forest (" + gHard + ")");
      check(gColl <= 200, "grouse: on average at most one glancing contact per flush (" + gColl + ")");
      check(gMinDist > 12 && gMaxDist < 75, String.format(Locale.ROOT, "grouse flush distance %.0f..%.0f m (real 20-60 m)", gMinDist, gMaxDist));
      check(gMaxHeight < 6.5, String.format(Locale.ROOT, "grouse stay under the canopy (max %.1f above ground)", gMaxHeight));
      // ------------------------------------------------ 5. dead fall from 30 m
      {
         Flight f = new Flight(Flight.Kind.DUCK, 5).at(400, 64 + 30, 400, 0.5, 0.95, 0.0);
         f.fall(0.06, 0.03, -0.02);
         int t = 0;
         double vmax = 0;
         while (!f.landed && t < 400) {
            f.tick(W);
            vmax = Math.max(vmax, Math.sqrt(f.fx * f.fx + f.fy * f.fy + f.fz * f.fz));
            t++;
         }
         check(f.landed, "dead duck reached the ground");
         check(t > 40 && t < 80, "30 m fall takes " + t + " ticks (real 2.5-3.5 s with drag)");
         check(vmax < 1.25, String.format(Locale.ROOT, "fall speed stays under terminal (%.2f b/t)", vmax));
         double drift = Math.hypot(f.x - 400, f.z - 400);
         check(drift > 6 && drift < 40, String.format(Locale.ROOT, "falls in an arc with forward momentum (%.1f blocks)", drift));
      }
      // ------------------------------------------------ 6. settle from the sky (reloaded chunk, wounded)
      for (int i = 0; i < 30; i++) {
         Flight f = new Flight(i % 2 == 0 ? Flight.Kind.DUCK : Flight.Kind.GROUSE, 6000 + i).at(-40 + i * 13, 64 + 20 + i % 7, 150 - i * 9, i, 0.4, 0);
         f.settle();
         Track tr = run(f, 1200, null, null);
         check(f.landed && tr.nan == 0 && tr.belowFloor == 0, "settle " + i + " lands cleanly (landed " + f.landed + ")");
      }
      // ------------------------------------------------ 7. fuzz: hostile inputs never make NaN
      for (int i = 0; i < 300; i++) {
         Flight.Rng r = new Flight.Rng(9000 + i);
         Flight f = new Flight(r.next() < 0.5 ? Flight.Kind.DUCK : Flight.Kind.GROUSE, i)
            .at(r.range(-500, 500), r.range(40, 200), r.range(-500, 500), i % 13 == 0 ? Double.NaN : r.range(-9, 9), r.range(0, 2), i % 17 == 0 ? Double.POSITIVE_INFINITY : r.range(-1, 1));
         int m = i % 4;
         if (m == 0) f.flee(i % 11 == 0 ? Double.NaN : f.x + 1, f.z, r.next() < 0.5);
         else if (m == 1) f.lure(f.x + r.range(-200, 200), 61.9, f.z + r.range(-200, 200), true, r.next() < 0.5);
         else if (m == 2) f.settle();
         else f.fall(r.range(-1, 1), r.range(-1, 1), i % 23 == 0 ? Double.NaN : r.range(-1, 1));
         boolean nan = false;
         for (int t = 0; t < 600; t++) {
            answer(f);
            f.tick(W);
            if (!Double.isFinite(f.x + f.y + f.z + f.vx() + f.vyNow() + f.vz() + f.bank())) {
               nan = true;
               break;
            }
         }
         check(!nan, "fuzz " + i + " stays finite");
      }
      System.out.printf(Locale.ROOT, "duck flee landed %d/40, lure on the spread %d/40 (worst miss %.1f, worst wind error %.0f deg), cruise worst altitude dev %.2f%n",
         landedFlee, lureOk, worstMiss, Math.toDegrees(worstWindErr), worstOvershoot);
      System.out.printf(Locale.ROOT, "grouse: landed %d/200, contacts %d (hard %d), flush %.0f-%.0f m, max height %.1f, closest trunk %.2f, longest %d ticks%n",
         gLanded, gColl, gHard, gMinDist, gMaxDist, gMaxHeight, gMinTrunk, gTimeMax);
      System.out.println(fails == 0 ? "ALL PASS (" + checks + " checks)" : fails + " FAILED of " + checks);
      System.exit(fails == 0 ? 0 : 1);
   }
}
