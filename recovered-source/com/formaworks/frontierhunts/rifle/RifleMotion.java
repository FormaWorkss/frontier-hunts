package com.formaworks.frontierhunts.rifle;

public final class RifleMotion {
   public static float smooth(float var0, float var1, float var2) {
      float var3 = Math.clamp((var2 - var0) / (var1 - var0), 0.0F, 1.0F);
      return var3 * var3 * (3.0F - 2.0F * var3);
   }

   public static float progress(RifleState var0, double var1) {
      return var0.action() == 0 ? 0.0F : (float)Math.clamp((var1 - (double)var0.started()) / (double)var0.duration(), 0.0, 1.0);
   }

   public static float lift(float var0) {
      return 68.0F * smooth(0.0F, 0.22F, var0) * (1.0F - smooth(0.79F, 1.0F, var0));
   }

   public static float pull(float var0) {
      return 0.092F * (smooth(0.21F, 0.48F, var0) - smooth(0.53F, 0.8F, var0));
   }

   public static float magazine(float var0) {
      return 0.2F * smooth(0.0F, 0.3F, var0) * (1.0F - smooth(0.69F, 0.96F, var0));
   }

   public static float recoil(double var0) {
      return !(var0 < 0.0) && !(var0 > 13.0) ? (float)(Math.sin(Math.min(1.0, var0 / 2.0) * Math.PI / 2.0) * Math.exp(-Math.max(0.0, var0 - 2.0) / 2.6)) : 0.0F;
   }

   private RifleMotion() {
   }
}
