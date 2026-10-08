package com.formaworks.frontierhunts.landscape;

public final class AlpineHydraulics {
   public static double energy(double var0, double var2) {
      return Math.clamp((var0 - 0.016) * 18.0 + var2 * 0.16, 0.0, 1.0);
   }

   public static AlpineHydraulics.Reach at(AlpineLayout var0, double var1, double var3, double var5) {
      AlpineWatershed.Water var7 = var0.watershed().water(var1, var3);
      if (!var7.lake() && !(var7.width() <= 0.0) && !(var7.distance() > var7.width() + 1.0)) {
         double var8 = var7.dx();
         double var10 = var7.dz();
         double var12 = level(var0, var1, var3);
         double var14 = level(var0, var1 - var8 * 4.0, var3 - var10 * 4.0);
         double var16 = level(var0, var1 + var8 * 4.0, var3 + var10 * 4.0);
         if (var16 > var12 + 0.02) {
            double var18 = var12;
            double var20 = 0.0;
            double var22 = 0.0;

            for (int var24 = -1; var24 <= 1; var24++) {
               for (int var25 = -1; var25 <= 1; var25++) {
                  if (var24 != 0 || var25 != 0) {
                     double var26 = Math.hypot((double)var24, (double)var25);
                     double var28 = var1 + (double)var24 / var26 * 4.0;
                     double var30 = var3 + (double)var25 / var26 * 4.0;
                     AlpineWatershed.Water var32 = var0.watershed().water(var28, var30);
                     double var33 = level(var0, var28, var30);
                     if (var32.distance() <= var32.width() + 1.0 && var33 < var18) {
                        var18 = var33;
                        var20 = (double)var24 / var26;
                        var22 = (double)var25 / var26;
                     }
                  }
               }
            }

            if (var20 == 0.0 && var22 == 0.0) {
               return new AlpineHydraulics.Reach(0.0, 0.0, 0.0, 0.0, false);
            }

            var8 = var20;
            var10 = var22;
            var16 = var18;
            var14 = level(var0, var1 - var20 * 4.0, var3 - var22 * 4.0);
         }

         double var35 = Math.max(0.0, var14 - var16);
         double var36 = energy(var7.speed(), var35);
         if (var0.version() >= 8 && var35 > 0.4) {
            var36 = Math.sqrt(var36);
         }

         if (var0.version() >= 10) {
            double var37 = 1.0 - AlpineLayout.smooth(var7.width() * 0.45, var7.width() + 1.0, var7.distance());
            var36 = Math.max(var36, (0.3 + 0.38 * AlpineLayout.smooth(0.02, 0.8, var35)) * (0.65 + 0.35 * var37));
         }

         return new AlpineHydraulics.Reach(var8, var10, var36, var35, var5 > var16 + 1.2 && var35 > 1.5);
      } else {
         return new AlpineHydraulics.Reach(0.0, 0.0, 0.0, 0.0, false);
      }
   }

   private static double level(AlpineLayout var0, double var1, double var3) {
      double var5 = AlpineLayout.smooth(-0.08, 0.13, var0.watershed().continental(var1, var3));
      return AlpineLayout.lerp(64.0, var0.watershed().water(var1, var3).level(), var5);
   }

   public static double speed(double var0) {
      return 0.035 + 0.78 * var0 * var0;
   }

   public static double lateral(double var0, double var2, double var4, double var6) {
      return var0 * 0.09 * (Math.sin(var2 * 0.72 + var4 * 0.39 - var6 * 0.18) + 0.4 * Math.sin(var4 * 1.21 + var6 * 0.31));
   }

   public static record Reach(double dx, double dz, double energy, double drop, boolean falling) {
   }
}
