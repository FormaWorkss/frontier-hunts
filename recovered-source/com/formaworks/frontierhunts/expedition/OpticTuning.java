package com.formaworks.frontierhunts.expedition;

public final class OpticTuning {
   public static double turnScale(double var0) {
      double var2 = Math.max(1.0, Double.isFinite(var0) ? var0 : 1.0);
      return Math.clamp(0.9 / Math.pow(var2, 0.34), 0.36, 0.84);
   }

   public static double eventSensitivity(double var0, double var2) {
      double var4 = Double.isFinite(var0) ? var0 : 0.5;
      double var6 = var4 * 0.6 + 0.2;
      double var8 = Math.max(0.025, var6 * Math.cbrt(turnScale(var2)));
      return Math.clamp((var8 - 0.2) / 0.6, -0.2916666667, 1.0);
   }

   public static float bobScale(double var0) {
      double var2 = Math.max(1.0, Double.isFinite(var0) ? var0 : 1.0);
      return (float)Math.clamp(0.065 / var2, 0.008, 0.055);
   }

   private OpticTuning() {
   }
}
