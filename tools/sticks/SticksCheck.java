// [sticks] Offline check: soft arc limit, leg lengths, hold-sway amplitude per stance (standing / crouched / on the sticks / prone).
// javac -cp "$(cat /home/claude/fh2/.infra/cp62.txt):<compiled classes>" -d /tmp/sc tools/sticks/SticksCheck.java && java -cp "/tmp/sc:<compiled classes>:$(cat /home/claude/fh2/.infra/cp62.txt)" com.formaworks.frontierhunts.client.SticksCheck
package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.sticks.ShootingSticks;

public class SticksCheck {
   public static void main(String[] a) {
      float p = 0;
      for (int i = 0; i < 200; i++) p = ShootingSticks.soft(p, p + 1, -40, 40);
      System.out.println("soft push 200x1deg -> " + p);
      float q = 0; int n = 0;
      while (q < 39 && n < 10000) { q = ShootingSticks.soft(q, q + 0.2f, -40, 40); n++; }
      System.out.println("steps of 0.2 to reach 39: " + n);
      for (ShootingSticks.Height h : ShootingSticks.Height.values()) System.out.printf("%s leg %.3f%n", h, h.legLength());
      // hold sway: standing vs rested amplitude at the same clock, 60 fps, 30 s
      for (double[] st : new double[][]{{1, 1, 0}, {0.55, 0.6, 0}, {0.05, 0.2, 1}, {0, 0.06, 0}}) {
         HoldSway s = new HoldSway(12345L);
         if (st[2] > 0) s.pulse(1.0);
         double minP = 1e9, maxP = -1e9, minY = 1e9, maxY = -1e9, maxAcc = 0, pv = 0, pd = 0;
         for (int i = 0; i < 60 * 30; i++) {
            s.advance(1 / 60.0, st[0], st[1]);
            if (i > 120) {
               minP = Math.min(minP, s.pitch()); maxP = Math.max(maxP, s.pitch());
               minY = Math.min(minY, s.yaw()); maxY = Math.max(maxY, s.yaw());
               double d = s.pitch() - pv; maxAcc = Math.max(maxAcc, Math.abs(d - pd)); pd = d;
            }
            pv = s.pitch();
         }
         System.out.printf("drift %.2f breath %.2f pulse %.0f: pitch p-p %.4f deg (%.2f MOA), yaw p-p %.4f deg (%.2f MOA), max frame accel %.6f%n", st[0], st[1], st[2],
            maxP - minP, (maxP - minP) * 60, maxY - minY, (maxY - minY) * 60, maxAcc);
      }
   }
}
