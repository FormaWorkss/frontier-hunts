package com.formaworks.frontierhunts.client;

public final class HoldSwayCheck {
   public static void main(String[] a) {
      // 1) same curve at any frame rate: compare 30, 60, 144, 360 fps samples at shared instants
      double[] fps = {30, 60, 144, 360};
      double[][] at = new double[fps.length][];
      for (int i = 0; i < fps.length; i++) {
         HoldSway s = new HoldSway(42L);
         double dt = 1.0 / fps[i];
         int n = (int)Math.round(20.0 / dt);
         double maxStep = 0, maxAcc = 0, prev = 0, prevV = 0, maxAbs = 0;
         for (int k = 0; k < n; k++) {
            s.advance(dt, k * dt > 10 ? 0.18 : 1.0, k * dt > 10 ? 0.25 : 1.0); // stance change at 10 s
            double p = s.pitch();
            double v = (p - prev) / dt;
            if (k > 1) { maxStep = Math.max(maxStep, Math.abs(v)); maxAcc = Math.max(maxAcc, Math.abs(v - prevV) / dt); }
            prev = p; prevV = v; maxAbs = Math.max(maxAbs, Math.abs(p));
            if (!Double.isFinite(p + s.yaw())) throw new AssertionError("nan");
         }
         at[i] = new double[]{prev, s.yaw()};
         System.out.printf("fps %3.0f: max |pitch| %.4f deg, max rate %.4f deg/s, max accel %.4f deg/s^2%n", fps[i], maxAbs, maxStep, maxAcc);
      }
      for (int i = 1; i < fps.length; i++) {
         System.out.printf("end-state diff vs 30fps: %.6f deg%n", Math.abs(at[i][0] - at[0][0]));
      }
      // 2) prone target: drift 0, breath 0.06 -> tiny bob
      HoldSway s = new HoldSway(7L);
      double m = 0;
      for (int k = 0; k < 144 * 30; k++) { s.advance(1 / 144.0, 0.0, 0.06); if (k > 144 * 5) m = Math.max(m, Math.hypot(s.pitch(), s.yaw())); }
      System.out.printf("prone max offset %.5f deg (%.2f MOA)%n", m, m * 60);
      // 3) standing magnitude
      s = new HoldSway(9L); m = 0;
      for (int k = 0; k < 144 * 60; k++) { s.advance(1 / 144.0, 1.0, 1.0); if (k > 144 * 5) m = Math.max(m, Math.hypot(s.pitch(), s.yaw())); }
      System.out.printf("standing max offset %.4f deg (%.1f MOA)%n", m, m * 60);
   }
}
