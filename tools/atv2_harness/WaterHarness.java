// [atv2] Offline check of the water-depth driving rules (uses the real AtvWater.thrust/drag with Atv.drive's
// propulsion terms on flat ground, full throttle). No Minecraft runtime needed.
// javac -proc:none -cp "<compiled src>:$(cat /home/claude/fh/cp62.txt)" -d /tmp/h tools/atv2_harness/WaterHarness.java
// java -cp "/tmp/h:<compiled src>:$(cat /home/claude/fh/cp62.txt)" com.formaworks.frontierhunts.landscape.ride.wade.WaterHarness
package com.formaworks.frontierhunts.landscape.ride.wade;

public class WaterHarness {
    static double step(double v, double d, float throttle) {
        double accel = 0.03 * throttle * AtvWater.thrust(d) * (1 - Math.pow(Math.min(1, Math.max(0, v / 1.25)), 1.7));
        accel = Math.min(accel, 0.92 * 0.0245 * 1.9);
        double roll = 0.009 + 0.006 * v * v;
        v -= Math.signum(v) * Math.min(Math.abs(v), roll);
        v += accel;
        return AtvWater.slow(v, d, 1.0);
    }

    public static void main(String[] a) {
        System.out.println("depth  top b/t  km/h   0->90% top   (FLOOD at " + AtvWater.FLOOD + ": no throttle)");
        for (double d : new double[]{0, 0.3, 0.6, 0.89, 1.2, 1.5, 1.89}) {
            double v = 0;
            double[] h = new double[600];
            for (int t = 0; t < 600; t++) h[t] = v = step(v, d, 1f);
            int t90 = 0;
            while (h[t90] < 0.9 * v) t90++;
            System.out.printf("%5.2f  %6.3f  %5.1f  %5.1f s%n", d, v, v * 72, t90 / 20.0);
        }
        for (double d : new double[]{0.89, 1.89, 2.89}) {
            double v = 1.0;
            StringBuilder sb = new StringBuilder();
            for (int t = 1; t <= 60; t++) {
                v = step(v, d, d >= AtvWater.FLOOD ? 0f : 1f);
                if (t % 10 == 0) sb.append(String.format(" %.2f", v));
            }
            System.out.printf("enter %.2f deep at 1.0 b/t, speed every 0.5 s:%s%n", d, sb);
        }
    }
}
