package com.formaworks.frontierhunts.landscape.ride;

/**
 * [polish] Offline check of the ATV reverse gear: replays the ground branch of Atv.drive() (grip clamp, slope, rolling
 * resistance, drag, the stop snap that now spares reverse drive, the clamp) with RevGear.push for 6 s of holding S.
 *   tools/polish/rev_harness.sh
 */
public final class RevHarness {
   static double sim(double grip, double rolling, double pitchDeg, int ticks) {
      double v = 0.0;
      double p = Math.toRadians(pitchDeg);
      for (int t = 0; t < ticks; t++) {
         double cap = grip * 0.0245 * 1.9 * Math.cos(p);
         double a = -RevGear.push(v, 1.0F, rolling, Atv.DRAG, -0.0245 * Math.sin(p) * 1.6);
         a = Math.max(-cap, Math.min(cap, a));
         a -= 0.0245 * Math.sin(p) * 1.6;
         double res = rolling * (Math.abs(v) > 0.005 ? 1 : 0) + Atv.DRAG * v * v;
         v -= Math.signum(v) * Math.min(Math.abs(v), res); // no stop snap while reversing under power
         v += a;
         v = Math.max(-Atv.V_REV * 1.6, Math.min(Atv.V_MAX * 1.05, v));
      }
      return -v * 72.0; // km/h backwards
   }

   public static void main(String[] args) {
      Object[][] cases = {
         {"grass, flat", 0.92, 0.009, 0.0, 9.0, 15.0},
         {"gravel", 0.7, 0.016, 0.0, 9.0, 15.0},
         {"deep snow", 0.52, 0.012, 0.0, 9.0, 15.0},
         {"mud", 0.58, 0.013, 0.0, 9.0, 15.0},
         {"grass, backing up a 20 deg slope", 0.92, 0.009, -20.0, 9.0, 15.0},
         {"grass, backing down a 15 deg slope", 0.92, 0.009, 15.0, 9.0, 16.0},
      };
      boolean ok = true;
      for (Object[] c : cases) {
         double s1 = sim((double)c[1], (double)c[2], (double)c[3], 20);
         double s6 = sim((double)c[1], (double)c[2], (double)c[3], 120);
         boolean pass = s6 >= (double)c[4] && s6 <= (double)c[5] && s1 > 3.0;
         ok &= pass;
         System.out.printf("%-36s after 1 s %5.1f km/h, after 6 s %5.1f km/h  %s%n", c[0], s1, s6, pass ? "ok" : "FAIL");
      }
      System.out.println(ok ? "ALL PASS" : "FAILED");
      System.exit(ok ? 0 : 1);
   }
}
