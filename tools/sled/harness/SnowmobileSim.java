import com.formaworks.frontierhunts.sled.SledPhysics;

/** [1.2.5] Offline test of the snowmobile (SledPhysics in motor mode) on the same made-up hills as SledSim. */
public class SnowmobileSim {
   static int fails;

   static void check(boolean ok, String what) {
      System.out.println((ok ? "  ok   " : "  FAIL ") + what);
      if (!ok) fails++;
   }

   static SledPhysics mobile(SledSim.World w, double x, double z, float yaw) {
      SledPhysics s = SledSim.sled(w, x, z, yaw);
      s.motor = true;
      return s;
   }

   /** full throttle for {@code ticks}; returns {speed, blocks up, blocks along, hits, stuck ticks} */
   static double[] run(SledSim.World w, SledPhysics s, int ticks, float thr, float side) {
      double y0 = s.y, d = 0;
      int hits = 0, stuck = 0;
      for (int t = 0; t < ticks; t++) {
         double px = s.x, pz = s.z;
         s.throttle = thr;
         s.tick(w, thr, side, true);
         double step = Math.hypot(s.x - px, s.z - pz);
         d += step;
         if (s.impact > 0.05) hits++;
         if (t > 40 && step < 0.01) stuck++;
         if (s.z < 15 || s.z > SledSim.N - 15 || s.x < 15 || s.x > SledSim.N - 15) break;
      }
      return new double[]{s.speed(), s.y - y0, d, hits, stuck};
   }

   public static void main(String[] a) {
      System.out.println("== flat, full throttle");
      SledSim.World f = SledSim.flat();
      SledPhysics s = mobile(f, 600, 30, 0);
      StringBuilder sb = new StringBuilder();
      int to100 = -1;
      for (int t = 1; t <= 600; t++) {
         s.throttle = 1;
         s.tick(f, 1, 0, true);
         if (to100 < 0 && s.speed() * 72 >= 100) to100 = t;
         if (t == 20 || t == 40 || t == 60 || t == 100 || t == 200 || t == 600) sb.append(" t").append(t / 20).append("s=").append(SledSim.kmh(s.speed()));
      }
      double top = s.speed() * 72;
      System.out.println("  " + sb + String.format(" | 0-100 km/h in %.1f s", to100 / 20.0));
      check(to100 > 0 && to100 < 160, "reaches 100 km/h on the flat within 8 s");
      check(top > 100 && top < 135, String.format("tops out around 100-135 km/h on the flat (%.0f)", top));
      // brake
      int t = 0;
      while (s.speed() > 0.03 && t < 400) { s.throttle = -1; s.tick(f, -1, 0, true); t++; }
      check(t < 100, String.format("brakes to a stop from top speed in %.1f s", t / 20.0));
      // reverse
      for (int i = 0; i < 60; i++) { s.throttle = -1; s.tick(f, -1, 0, true); }
      check(s.along() < -0.05 && s.along() > -0.2, String.format("reverses at a walk (%.0f km/h)", -s.along() * 72));
      // parked
      s = mobile(f, 600, 300, 0);
      for (int i = 0; i < 100; i++) { s.throttle = 0; s.tick(f, 0, 0, true); }
      check(s.speed() == 0.0, "parked with a rider on the flat it stays put");
      // turning circle at about 50 km/h
      s = mobile(f, 600, 300, 0);
      while (s.speed() * 72 < 50) { s.throttle = 1; s.tick(f, 1, 0, true); }
      float yaw0 = s.yaw;
      double minX = 1e9, maxX = -1e9;
      for (int i = 0; i < 400 && Math.abs(yaw0 - s.yaw) < 360; i++) {
         s.throttle = 0.45F;
         s.tick(f, 0.45F, 1, true);
         minX = Math.min(minX, s.x); maxX = Math.max(maxX, s.x);
      }
      double circle = maxX - minX;
      check(circle > 8 && circle < 40, String.format("full lock at %.0f km/h: a %.0f-block circle", s.speed() * 72, circle));
      // standing still it can't turn
      s = mobile(f, 600, 300, 0);
      for (int i = 0; i < 40; i++) { s.throttle = 0; s.tick(f, 0, 1, true); }
      check(Math.abs(s.yaw) < 1, "standing still the skis don't turn it");

      System.out.println("== climbing (full throttle straight up the fall line, 10 s)");
      Object[][] climbs = {
         {"9 deg", 6.0, 0.0}, {"18 deg", 3.0, 0.0}, {"27 deg", 2.0, 0.0}, {"34 deg", 1.5, 0.0}, {"45 deg stairs", 1.0, 0.0},
         {"rough 27 deg (bumps +-2)", 2.0, 2.0}, {"rough 34 deg (bumps +-2)", 1.5, 2.0}, {"50 deg jagged face", 0.83, 2.0}};
      double up27 = 0, up45 = 0;
      int k = 0;
      for (Object[] c : climbs) {
         SledSim.World w = SledSim.hill((Double)c[1], (Double)c[2], 40 + k++, 0);
         s = mobile(w, 600, SledSim.N - 40.5, 180);
         double[] r = run(w, s, 200, 1, 0);
         System.out.printf("  %-28s climbed %5.1f blocks over %5.0f, at %s km/h, hits %.0f, stuck %.0f%n", c[0], r[1], r[2], SledSim.kmh(r[0]), r[3], r[4]);
         if (c[0].equals("27 deg")) up27 = r[1];
         if (c[0].equals("45 deg stairs")) up45 = r[1];
      }
      check(up27 > 25, "climbs a 27 degree mountainside without stopping");
      check(up45 > 10, "claws its way up a 45 degree face");

      System.out.println("== downhill and across");
      SledSim.World h = SledSim.hill(2, 1.0, 60, 0);
      s = mobile(h, 600, 20.5, 0);
      double[] r = run(h, s, 200, 1, 0);
      System.out.printf("  27 deg downhill, throttle on: %s km/h after 10 s, hits %.0f%n", SledSim.kmh(r[0]), r[3]);
      check(r[0] <= SledPhysics.MAX + 1e-6, "never past the top speed");
      s = mobile(h, 600, 20.5, 0);
      r = run(h, s, 200, 0, 0);
      System.out.printf("  27 deg downhill, coasting: %s km/h after 10 s%n", SledSim.kmh(r[0]));
      s = mobile(h, 600, 300.5, -90);
      double z0 = s.z;
      r = run(h, s, 200, 0.7F, 0);
      System.out.printf("  traverse 27 deg slope at 70%% throttle: %.0f blocks, slid %.1f down, %s km/h%n", r[2], s.z - z0, SledSim.kmh(r[0]));
      check(r[2] > 60 && Math.abs(s.z - z0) < r[2] * 0.6, "holds a line across a slope");
      SledSim.World forest = SledSim.hill(2, 1.0, 61, 1.0 / 60);
      s = mobile(forest, 600, SledSim.N - 40.5, 180);
      r = run(forest, s, 400, 1, 0);
      System.out.printf("  27 deg forest climb (1 trunk / 60 cols): climbed %.1f, hits %.0f, stuck ticks %.0f%n", r[1], r[3], r[4]);

      System.out.println("== bare earth");
      SledSim.World e = SledSim.flat();
      e.grip = 1.0;
      s = mobile(e, 600, 30, 0);
      r = run(e, s, 200, 1, 0);
      check(r[0] * 72 > 4 && r[0] * 72 < 10, String.format("[1.4.0] on bare ground it crawls, like a boat on land (%.1f km/h)", r[0] * 72));
      double back0 = s.z;
      for (int i = 0; i < 100; i++) { s.throttle = -1; s.tick(e, -1, 0, true); }
      check(s.z < back0 - 3, String.format("[1.4.0] on bare ground it can back up (%.1f blocks)", back0 - s.z));
      for (int i = 0; i < 100; i++) { s.throttle = 0; s.tick(e, 0, 0, true); }
      check(s.speed() < 0.001, "on bare ground, throttle off, it stops");
      SledSim.World wet = SledSim.flat();
      wet.grip = 1.5;
      s = mobile(wet, 600, 30, 0);
      r = run(wet, s, 200, 1, 0);
      check(r[0] * 72 < 1, String.format("on water it doesn't go (%.1f km/h)", r[0] * 72));
      // and arriving fast it drags to a stop within a few dozen blocks
      SledSim.World mix = SledSim.flat();
      s = mobile(mix, 600, 30, 0);
      for (int i = 0; i < 300; i++) { s.throttle = 1; s.tick(mix, 1, 0, true); }
      double z0b = s.z;
      mix.grip = 1.0;
      for (int i = 0; i < 400 && s.speed() > SledPhysics.OFF_SNOW_CRAWL + 0.01; i++) { s.throttle = 1; s.tick(mix, 1, 0, true); }
      check(s.z - z0b < 80, String.format("from %s km/h onto bare ground it slows to a crawl in %.0f blocks", "110", s.z - z0b));

      System.out.println("== a crest at speed");
      SledSim.World c = SledSim.crest();
      s = mobile(c, 600, 20.5, 0);
      int air = 0;
      for (int i = 0; i < 300; i++) { s.throttle = 1; s.tick(c, 1, 0, true); if (!s.ground) air++; }
      System.out.printf("  gentle run onto a 40 deg face: %d ticks in the air, %s km/h%n", air, SledSim.kmh(s.speed()));
      System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILED");
   }
}
