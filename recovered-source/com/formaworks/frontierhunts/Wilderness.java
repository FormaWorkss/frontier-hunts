package com.formaworks.frontierhunts;

public final class Wilderness {
   public static Wilderness.Wind wind(long var0, long var2, boolean var4, boolean var5) {
      double var6 = (double)(mix(var0) & 65535L) * 9.587379924285257E-5;
      double var8 = (double)Math.max(0L, var2) / 20.0;
      double var10 = var6 + 0.65 * Math.sin(var8 / 197.0 + var6) + 0.23 * Math.sin(var8 / 61.0);
      double var12 = 1.4 + 0.9 * (1.0 + Math.sin(var8 / 113.0 + var6)) + (var4 ? 1.6 : 0.0) + (var5 ? 2.2 : 0.0);
      return new Wilderness.Wind(Math.sin(var10) * var12, -Math.cos(var10) * var12);
   }

   public static double thermal(long var0, boolean var2, float var3) {
      double var4 = (double)Math.floorMod(var0, 24000L);
      double var6;
      if (var4 < 1200.0) {
         var6 = -1.0;
      } else if (var4 < 5000.0) {
         var6 = -1.0 + 2.0 * ease((var4 - 1200.0) / 3800.0);
      } else if (var4 < 10500.0) {
         var6 = 1.0;
      } else if (var4 < 13200.0) {
         var6 = 1.0 - 2.0 * ease((var4 - 10500.0) / 2700.0);
      } else {
         var6 = -1.0;
      }

      double var8 = var2 ? 0.3 : 1.0 - (double)Math.clamp(var3, 0.0F, 1.0F) * 0.55;
      return Math.clamp(var6 * var8, -1.0, 1.0);
   }

   public static String thermalNote(double var0) {
      if (var0 > 0.45) {
         return "thermals rising";
      } else {
         return var0 < -0.45 ? "thermals falling" : "thermals slack";
      }
   }

   public static double scent(Wilderness.Wind var0, double var1, double var3, boolean var5, boolean var6) {
      return scent(var0, var1, var3, 0.0, var5, var6, 0.0);
   }

   public static double scent(Wilderness.Wind var0, double var1, double var3, double var5, boolean var7, boolean var8, double var9) {
      if (Double.isFinite(var1) && Double.isFinite(var3) && Double.isFinite(var0.speed())) {
         double var11 = Math.hypot(var1, var3);
         if (var11 > 160.0) {
            return 0.0;
         } else {
            double var13 = var0.speed();
            double var15 = var13 < 0.01 ? 0.0 : (var1 * var0.east + var3 * var0.south) / var13;
            double var17 = var13 < 0.01 ? var11 : Math.abs(var1 * var0.south - var3 * var0.east) / var13;
            double var19 = 4.0 + Math.max(0.0, var15) * 0.18;
            double var21 = Math.exp(-var17 * var17 / (2.0 * var19 * var19)) * Math.exp(-var11 / 65.0);
            var21 *= (var15 < -3.0 ? 0.06 : 1.0) * (var7 ? 0.55 : 1.0) * (var8 ? 0.3 : 1.0);
            if (Double.isFinite(var5) && Double.isFinite(var9) && var9 != 0.0 && var11 < 96.0) {
               double var23 = Math.clamp(var5 / 14.0, -1.0, 1.0);
               double var25 = Math.clamp(var9, -1.0, 1.0) * var23;
               if (var25 > 0.0) {
                  double var27 = var25 * Math.exp(-var11 / 44.0) * (var7 ? 0.5 : 1.0) * (var8 ? 0.3 : 1.0) * Math.max(0.22, 1.0 - var13 / 5.5);
                  var21 = Math.max(var21, var27 * 0.92);
               } else {
                  var21 *= 1.0 + var25 * 0.45;
               }
            }

            return Math.max(0.0, Math.min(1.0, var21));
         }
      } else {
         return 0.0;
      }
   }

   private static double ease(double var0) {
      var0 = Math.clamp(var0, 0.0, 1.0);
      return var0 * var0 * (3.0 - 2.0 * var0);
   }

   private static long mix(long var0) {
      var0 = (var0 ^ var0 >>> 30) * -4658895280553007687L;
      var0 = (var0 ^ var0 >>> 27) * -7723592293110705685L;
      return var0 ^ var0 >>> 31;
   }

   private Wilderness() {
   }

   public static record Wind(double east, double south) {
      public double speed() {
         return Math.hypot(this.east, this.south);
      }

      public double bearingTo() {
         return (Math.toDegrees(Math.atan2(this.east, -this.south)) + 360.0) % 360.0;
      }

      public String directionTo() {
         return new String[]{"N", "NE", "E", "SE", "S", "SW", "W", "NW"}[(int)Math.round(this.bearingTo() / 45.0) % 8];
      }
   }
}
