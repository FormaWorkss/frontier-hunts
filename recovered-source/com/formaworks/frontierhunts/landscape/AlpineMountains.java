package com.formaworks.frontierhunts.landscape;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Supplier;

final class AlpineMountains {
   static final int CELL = 1500;
   private static final double TAU = Math.PI * 2;
   private final AlpineLayout noise;
   private final AlpineDrainage land;
   private final boolean v21;
   private final AtomicReferenceArray<AlpineMountains.SpecSlot> specs = new AtomicReferenceArray<>(4096);
   private static final AlpineMountains.Lake NO_LAKE = new AlpineMountains.Lake(0.0, 0.0, 0.0, 0, false, 0.0, 0.0);
   private final ConcurrentHashMap<Long, AlpineMountains.Lake> lakes = new ConcurrentHashMap<>();
   static final int POND_CELL = 640;

   AlpineMountains(AlpineLayout var1, AlpineDrainage var2) {
      this.noise = var1;
      this.land = var2;
      this.v21 = var1.version() >= 21;
   }

   private static double unit(long var0) {
      return (double)(var0 >>> 11) * 1.110223E-16F;
   }

   private static long mix(long var0) {
      return AlpineLayout.mix(var0);
   }

   double[] body(double var1, double var3) {
      double var5 = var1 + 230.0 * this.noise.noise(var1 / 1080.0, var3 / 1080.0, 7091L);
      double var7 = var3 + 230.0 * this.noise.noise(var1 / 1080.0, var3 / 1080.0, 7093L);
      double var9 = AlpineLayout.smooth(0.48, 0.76, this.noise.noise(var5 / 3200.0, var7 / 3200.0, 7199L));
      double var11 = AlpineLayout.smooth(
         -0.3, 0.34, this.noise.noise(var5 / 1180.0, var7 / 1180.0, 7101L) + 0.3 * this.noise.noise(var5 / 2700.0, var7 / 2700.0, 7301L)
      );
      double var13 = var1 + 185.0 * this.noise.noise(var1 / 1250.0, var3 / 1250.0, 7319L);
      double var15 = var3 + 185.0 * this.noise.noise(var1 / 1250.0 + 71.3, var3 / 1250.0 - 33.7, 7321L);
      double var17 = Math.max(0.0, 1.0 - Math.abs(this.noise.noise(var13 / 560.0, var15 / 560.0, 7105L)));
      double var19 = Math.max(0.0, 1.0 - Math.abs(this.noise.noise(var13 / 238.0, var15 / 238.0, 7303L)));
      double var21 = Math.max(0.0, 1.0 - Math.abs(this.noise.noise(var13 / 103.0, var15 / 103.0, 7305L)));
      double var23 = 0.7 + 0.3 * AlpineLayout.smooth(-0.35, 0.4, this.noise.noise(var1 / 210.0, var3 / 210.0, 7221L));
      double var25 = AlpineLayout.smooth(-0.45, 0.55, this.noise.noise(var13 / 900.0, var15 / 900.0, 7331L));
      double var27 = Math.clamp(Math.pow(var17, 1.35) * (0.62 + 0.38 * var19) * (0.82 + 0.18 * var21), 0.0, 1.0);
      double var29 = var27 * var27 * (3.0 - 2.0 * var27);
      double var31 = this.noise.noise(var5 / 2600.0, var7 / 2600.0, 7341L) + 0.35 * this.noise.noise(var5 / 900.0, var7 / 900.0, 7343L);
      double var33 = AlpineLayout.smooth(-0.3, 0.45, var31);
      double var35 = var11 * (40.0 + 58.0 * var25 + (52.0 + 60.0 * var33) * var29) * var23 * (1.0 + var9 * 0.3);
      return new double[]{var35, var11, var33};
   }

   private AlpineMountains.Spec spec(int var1, int var2) {
      int var3 = (int)(mix((long)var1 << 32 ^ (long)var2 & 4294967295L) & 4095L);
      AlpineMountains.SpecSlot var4 = this.specs.get(var3);
      if (var4 != null && var4.cx == var1 && var4.cz == var2) {
         return var4.spec;
      } else {
         AlpineMountains.Spec var5 = this.make(var1, var2);
         this.specs.set(var3, new AlpineMountains.SpecSlot(var1, var2, var5));
         return var5;
      }
   }

   private AlpineMountains.Spec make(int var1, int var2) {
      if (this.v21) {
         return this.make21(var1, var2);
      } else {
         long var3 = this.noise.hash(var1, var2, 7721L);
         if (unit(var3) > 0.6) {
            return null;
         } else {
            double var5 = ((double)var1 + 0.16 + 0.68 * unit(mix(var3 + 1L))) * 1500.0;
            double var7 = ((double)var2 + 0.16 + 0.68 * unit(mix(var3 + 2L))) * 1500.0;
            double var9 = unit(mix(var3 + 3L));
            double var11 = unit(mix(var3 + 4L));
            double var13 = unit(mix(var3 + 5L));
            int var15 = var9 < 0.3 ? 1 : (var9 < 0.55 ? 2 : (var9 < 0.82 ? 3 : 4));

            double var16 = switch (var15) {
               case 1 -> 270.0 + 220.0 * var11;
               case 2 -> 430.0 + 290.0 * var11;
               case 3 -> 380.0 + 270.0 * var11;
               default -> 320.0 + 220.0 * var11;
            };

            double var18 = switch (var15) {
               case 1 -> 2.6 + 0.6 * var13;
               case 2 -> 2.0 + 0.5 * var13;
               case 3 -> 1.75 + 0.45 * var13;
               default -> 2.1 + 0.5 * var13;
            };
            double var20 = 1.0 + (var15 == 1 ? 0.9 : 0.55) * unit(mix(var3 + 9L));
            double var22 = Math.min(var16 * var18, 1650.0 / var20);
            int var24 = var15 == 1 ? 2 + (int)(unit(mix(var3 + 6L)) * 2.0) : 3 + (int)(unit(mix(var3 + 6L)) * 3.0);
            double var25 = var15 == 1 ? 0.46 : (var15 == 3 ? 0.27 : 0.33);
            return new AlpineMountains.Spec(
               var5,
               var7,
               var15,
               var16,
               var22,
               var20,
               unit(mix(var3 + 10L)) * Math.PI,
               var24,
               unit(mix(var3 + 7L)) * (Math.PI * 2),
               var25,
               var3,
               this.land.continental(var5, var7) >= 0.14
            );
         }
      }
   }

   private AlpineMountains.Spec make21(int var1, int var2) {
      long var3 = this.noise.hash(var1, var2, 7721L);
      if (unit(var3) > 0.68) {
         return null;
      } else {
         double var5 = ((double)var1 + 0.16 + 0.68 * unit(mix(var3 + 1L))) * 1500.0;
         double var7 = ((double)var2 + 0.16 + 0.68 * unit(mix(var3 + 2L))) * 1500.0;
         double var9 = unit(mix(var3 + 3L));
         double var11 = unit(mix(var3 + 4L));
         double var13 = unit(mix(var3 + 5L));
         int var15 = var9 < 0.3 ? 1 : (var9 < 0.55 ? 2 : (var9 < 0.82 ? 3 : 4));

         double var16 = switch (var15) {
            case 1 -> 330.0 + 250.0 * var11;
            case 2 -> 480.0 + 320.0 * var11;
            case 3 -> 430.0 + 300.0 * var11;
            default -> 370.0 + 250.0 * var11;
         };

         double var18 = switch (var15) {
            case 1 -> 1.9 + 0.45 * var13;
            case 2 -> 1.8 + 0.45 * var13;
            case 3 -> 1.6 + 0.4 * var13;
            default -> 1.9 + 0.45 * var13;
         };
         double var20 = 1.25 + (var15 == 1 ? 0.85 : 0.7) * unit(mix(var3 + 9L));
         double var22 = Math.min(var16 * var18, 1800.0 / var20);
         int var24 = 3 + (int)(unit(mix(var3 + 6L)) * 2.0);
         double var25 = var15 == 1 ? 0.36 : (var15 == 3 ? 0.22 : 0.26);
         double var27 = Math.PI * this.noise.noise(var5 / 7200.0, var7 / 7200.0, 7771L) + (unit(mix(var3 + 10L)) - 0.5) * 0.55;
         return new AlpineMountains.Spec(
            var5, var7, var15, var16, var22, var20, var27, var24, unit(mix(var3 + 7L)) * (Math.PI * 2), var25, var3, this.land.continental(var5, var7) >= 0.14
         );
      }
   }

   private static double[] frame(AlpineMountains.Spec var0, double var1, double var3) {
      double var5 = var1 - var0.px;
      double var7 = var3 - var0.pz;
      double var9 = Math.cos(var0.axis);
      double var11 = Math.sin(var0.axis);
      double var13 = (var5 * var9 + var7 * var11) / var0.elong;
      double var15 = -var5 * var11 + var7 * var9;
      return new double[]{Math.hypot(var13, var15), Math.atan2(var15, var13)};
   }

   private double armStrength(AlpineMountains.Spec var1, double var2, double var4) {
      double var6 = 1.0;

      for (int var8 = 0; var8 < var1.arms; var8++) {
         double var9 = var1.a0
            + (double)var8 * (Math.PI * 2) / (double)var1.arms
            + (unit(mix(var1.h + 8L + (long)var8)) - 0.5) * 0.8
            + 0.34 * this.noise.noise(var4 / 210.0 + (double)var8 * 13.7, unit(mix(var1.h + 20L + (long)var8)) * 40.0, 7729L) * this.wander(var4);
         double var11 = Math.abs(Math.IEEEremainder(var2 - var9, Math.PI * 2));
         var6 *= 1.0 - Math.exp(-(var11 / var1.armWidth) * (var11 / var1.armWidth));
      }

      return 1.0 - var6;
   }

   private double facet(AlpineMountains.Spec var1, double var2, double var4) {
      int var6 = var1.arms;
      double[] var7 = new double[var6];

      for (int var8 = 0; var8 < var6; var8++) {
         double var9 = var1.a0
            + (double)var8 * (Math.PI * 2) / (double)var6
            + (unit(mix(var1.h + 8L + (long)var8)) - 0.5) * 0.8
            + 0.34 * this.noise.noise(var4 / 210.0 + (double)var8 * 13.7, unit(mix(var1.h + 20L + (long)var8)) * 40.0, 7729L) * this.wander(var4);
         var7[var8] = var9 - (Math.PI * 2) * Math.floor(var9 / (Math.PI * 2));
      }

      Arrays.sort(var7);
      double var18 = var2 - (Math.PI * 2) * Math.floor(var2 / (Math.PI * 2));
      double var10;
      double var12;
      if (var18 < var7[0]) {
         var10 = var7[var6 - 1] - (Math.PI * 2);
         var12 = var7[0];
      } else {
         int var14 = 0;

         while (var14 + 1 < var6 && var7[var14 + 1] <= var18) {
            var14++;
         }

         var10 = var7[var14];
         var12 = var14 + 1 < var6 ? var7[var14 + 1] : var7[0] + (Math.PI * 2);
      }

      double var19 = Math.max(1.0E-6, var12 - var10);
      double var16 = (var18 - (var10 + var19 / 2.0)) / (var19 / 2.0) * (Math.PI / (double)var6);
      return Math.cos(var16);
   }

   private double wander(double var1) {
      return this.v21 ? AlpineLayout.smooth(80.0, 450.0, var1) : Math.min(1.0, var1 / 150.0);
   }

   private double reach(AlpineMountains.Spec var1, double var2, double var4, double var6) {
      double var8 = Math.cos(var4);
      double var10 = Math.sin(var4);
      double var12 = this.v21 ? 0.08 * AlpineLayout.smooth(0.1 * var1.R, 0.35 * var1.R, var2) : 0.08;
      return var1.R * (0.62 + 0.6 * var6) * (1.0 + var12 * this.noise.noise(var2 / 300.0 + unit(mix(var1.h + 13L)) * 50.0 + 1.2 * var8, 1.2 * var10, 7725L));
   }

   private double one(AlpineMountains.Spec var1, double var2, double var4, double[] var6) {
      double[] var7 = frame(var1, var2, var4);
      double var8 = var7[0];
      double var10 = var7[1];
      if (var8 > var1.R * 1.32) {
         return 0.0;
      } else {
         double var12 = this.armStrength(var1, var10, var8);
         double var14 = var8 / this.reach(var1, var8, var10, var12);
         if (var14 >= 1.0) {
            return 0.0;
         } else {
            if (this.v21) {
               double var16 = (var1.kind == 1 ? 0.55 : 0.85) * (1.0 - AlpineLayout.smooth(0.45, 0.9, var14));
               if (var16 > 0.0) {
                  var14 *= 1.0 - var16 * (1.0 - this.facet(var1, var10, var8));
               }
            }

            double var22 = Math.sqrt(var14 * var14 + 0.0016) - 0.04;
            if (this.v21) {
               var22 /= Math.sqrt(1.0016) - 0.04;
               if (var6 != null) {
                  var6[0] = var14;
                  var6[1] = var12;
               }

               return Math.max(0.0, var1.H * this.shape21(var1, var22, var14, var10, var8, var12));
            } else {
               double var18 = switch (var1.kind) {
                  case 1 -> (1.0 - var22) * (1.0 - var22) * (1.0 + 1.25 * var22);
                  case 2 -> Math.pow(1.0 - var22, 1.75);
                  case 3 -> Math.pow(1.0 - var22, 1.55);
                  default -> Math.pow(1.0 - var22, 1.85);
               };
               if (var1.kind != 1) {
                  var18 += var12 * var12 * 0.14 * Math.exp(-((var14 - 0.4) / 0.11) * ((var14 - 0.4) / 0.11)) * (0.6 + 0.8 * unit(mix(var1.h + 15L)));
               }

               if (var1.kind != 1) {
                  double var20 = (var1.kind == 3 ? 0.16 : 0.13)
                     * (0.55 + 0.45 * this.noise.noise(Math.cos(var10) * 2.3 + unit(mix(var1.h + 17L)) * 40.0, Math.sin(var10) * 2.3, 7727L));
                  var18 -= var20 * Math.pow(1.0 - var12, 1.6) * Math.exp(-((var14 - 0.48) / 0.21) * ((var14 - 0.48) / 0.21));
               } else {
                  var18 += 0.05 * Math.sin(var14 * 9.4 + unit(mix(var1.h + 16L)) * 6.0) * var14 * (1.0 - var14);
               }

               if (var6 != null) {
                  var6[0] = var14;
                  var6[1] = var12;
               }

               return Math.max(0.0, var1.H * var18);
            }
         }
      }
   }

   private double shape21(AlpineMountains.Spec var1, double var2, double var4, double var6, double var8, double var10) {
      double var12 = switch (var1.kind) {
         case 1 -> Math.pow(1.0 - var2, 1.35) * (1.0 + 0.3 * var2) + 0.035 * Math.sin(var4 * 9.4 + unit(mix(var1.h + 16L)) * 6.0) * var4 * (1.0 - var4);
         case 2 -> Math.pow(1.0 - var2, 1.7);
         case 3 -> Math.pow(1.0 - var2, 1.6);
         default -> Math.pow(1.0 - var2, 1.8);
      };
      var12 += var10
         * var10
         * (var1.kind == 1 ? 0.08 : 0.14)
         * Math.exp(-((var4 - 0.4) / 0.11) * ((var4 - 0.4) / 0.11))
         * (0.6 + 0.8 * unit(mix(var1.h + 15L)));
      double var14 = (var1.kind == 3 ? 0.17 : (var1.kind == 1 ? 0.08 : 0.14))
         * (0.55 + 0.45 * this.noise.noise(Math.cos(var6) * 2.3 + unit(mix(var1.h + 17L)) * 40.0, Math.sin(var6) * 2.3, 7727L));
      var12 -= var14 * Math.pow(1.0 - var10, 1.6) * Math.exp(-((var4 - 0.48) / 0.21) * ((var4 - 0.48) / 0.21));
      if (var1.kind != 1 && var10 > 0.35 && var4 < 0.85) {
         double var16 = 1.0 - Math.abs(this.noise.noise(var8 / 88.0 + unit(mix(var1.h + 18L)) * 60.0, var6 * 2.1, 7773L));
         var12 += (var16 - 0.55)
            * 0.045
            * AlpineLayout.smooth(0.35, 0.85, var10)
            * AlpineLayout.smooth(0.1, 0.3, var4)
            * (1.0 - AlpineLayout.smooth(0.6, 0.85, var4));
      }

      return var12;
   }

   AlpineDrainage.Peak peak(double var1, double var3) {
      double var5 = var1 + 60.0 * this.noise.noise(var1 / 460.0, var3 / 460.0, 7711L);
      double var7 = var3 + 60.0 * this.noise.noise(var1 / 460.0 + 17.1, var3 / 460.0 - 9.3, 7713L);
      int var9 = Math.floorDiv((int)Math.floor(var5), 1500);
      int var10 = Math.floorDiv((int)Math.floor(var7), 1500);
      double var11 = 0.0;
      double var13 = 0.0;
      double var15 = 0.0;
      double var17 = 1.0;
      int var19 = 0;
      double[] var20 = new double[2];

      for (int var21 = -2; var21 <= 2; var21++) {
         for (int var22 = -2; var22 <= 2; var22++) {
            AlpineMountains.Spec var23 = this.spec(var9 + var21, var10 + var22);
            if (var23 != null) {
               double var24 = var5 - var23.px;
               double var26 = var7 - var23.pz;
               if (var23.land && !(var24 * var24 + var26 * var26 > var23.R * 1.32 * var23.elong * var23.R * 1.32 * var23.elong)) {
                  double var28 = this.one(var23, var5, var7, var20);
                  if (var28 > var11) {
                     var13 = var11;
                     var11 = var28;
                     var19 = var23.kind;
                     var15 = var20[1];
                     var17 = var20[0];
                  } else if (var28 > var13) {
                     var13 = var28;
                  }
               }
            }
         }
      }

      if (var11 <= 0.0) {
         return AlpineDrainage.Peak.NONE;
      } else {
         double var30 = Math.min(60.0, 4.0 * var13);
         if (var30 <= 0.0) {
            return new AlpineDrainage.Peak(var11, var19, var15, var17);
         } else {
            double var31 = Math.max(0.0, var30 - (var11 - var13));
            return new AlpineDrainage.Peak(var11 + var31 * var31 / (4.0 * var30), var19, var15, var17);
         }
      }
   }

   AlpineDrainage.Peak peak21(double var1, double var3) {
      double var5 = var1 + 60.0 * this.noise.noise(var1 / 460.0, var3 / 460.0, 7711L);
      double var7 = var3 + 60.0 * this.noise.noise(var1 / 460.0 + 17.1, var3 / 460.0 - 9.3, 7713L);
      int var9 = Math.floorDiv((int)Math.floor(var5), 1500);
      int var10 = Math.floorDiv((int)Math.floor(var7), 1500);
      double var11 = 0.0;
      double var13 = 0.0;
      double var15 = 0.0;
      double var17 = 1.0;
      double var19 = 0.0;
      double var21 = 1.0;
      double var23 = 0.0;
      double var25 = 0.0;
      int var27 = 0;
      double[] var28 = new double[2];

      for (int var29 = -2; var29 <= 2; var29++) {
         for (int var30 = -2; var30 <= 2; var30++) {
            AlpineMountains.Spec var31 = this.spec(var9 + var29, var10 + var30);
            if (var31 != null) {
               double var32 = var5 - var31.px;
               double var34 = var7 - var31.pz;
               if (var31.land && !(var32 * var32 + var34 * var34 > var31.R * 1.32 * var31.elong * var31.R * 1.32 * var31.elong)) {
                  double var36 = this.one(var31, var5, var7, var28);
                  double var38 = var31.kind == 1 ? 1.0 : 0.0;
                  if (var36 > var11) {
                     var13 = var11;
                     var19 = var15;
                     var21 = var17;
                     var25 = var23;
                     var11 = var36;
                     var27 = var31.kind;
                     var15 = var28[1];
                     var17 = var28[0];
                     var23 = var38;
                  } else if (var36 > var13) {
                     var13 = var36;
                     var19 = var28[1];
                     var21 = var28[0];
                     var25 = var38;
                  }
               }
            }
         }
      }

      if (var11 <= 0.0) {
         return AlpineDrainage.Peak.NONE;
      } else {
         double var41 = 0.5 + 0.5 * AlpineLayout.smooth(0.0, 40.0, var11 - var13);
         double var42 = var15 * var41 + var19 * (1.0 - var41);
         double var33 = var17 * var41 + var21 * (1.0 - var41);
         double var35 = var23 * var41 + var25 * (1.0 - var41);
         double var37 = Math.min(60.0, 4.0 * var13);
         if (var37 <= 0.0) {
            return new AlpineDrainage.Peak(var11, var27, var42, var33, var35);
         } else {
            double var39 = Math.max(0.0, var37 - (var11 - var13));
            return new AlpineDrainage.Peak(var11 + var39 * var39 / (4.0 * var37), var27, var42, var33, var35);
         }
      }
   }

   double[] erode(double var1, double var3, double var5, double var7, double var9, double var11) {
      double var13 = Math.hypot(var5, var7);
      double var15 = var9
         * 0.085
         * AlpineLayout.smooth(0.05, 0.32, var13)
         * (1.0 + 0.5 * AlpineLayout.smooth(0.8, 1.8, var13))
         * (1.0 - AlpineLayout.smooth(0.78, 0.97, var11));
      if (var15 < 0.4) {
         return new double[]{0.0, 0.0};
      } else {
         double var17 = 0.0;
         double var19 = 0.0;
         double var21 = var15;
         double var23 = 0.006060606060606061;
         double var25 = 0.0;
         double var27 = 0.0;
         double var29 = 0.0;

         for (int var31 = 0; var31 < 4; var31++) {
            double var32 = var5 + var25 * 0.55;
            double var34 = var7 + var27 * 0.55;
            double var36 = Math.hypot(var32, var34);
            if (var36 < 1.0E-6) {
               break;
            }

            double var38 = var34 / var36;
            double var40 = -var32 / var36;
            double[] var42 = this.gully(var1 * var23, var3 * var23, var38, var40, (long)(7731 + var31 * 17));
            var17 += var42[0] * var21;
            var19 += var21;
            var25 += var42[1] * var21 * var23;
            var27 += var42[2] * var21 * var23;
            if (var31 == 0) {
               var29 = var42[0];
            }

            var21 *= 0.38;
            var23 *= 2.03;
         }

         double var43 = var19 > 0.0 ? var17 / var19 : 0.0;
         return new double[]{var17 - 0.22 * var15, Math.clamp(var43 * 0.7 + var29 * 0.3, -1.0, 1.0)};
      }
   }

   double[] erode21(double var1, double var3, double var5, double var7, double var9, double var11, double var13) {
      double var15 = Math.hypot(var5, var7);
      double var17 = var9
         * 0.115
         * AlpineLayout.smooth(0.05, 0.3, var15)
         * (1.0 + 0.45 * AlpineLayout.smooth(0.8, 1.8, var15))
         * (1.0 - AlpineLayout.smooth(0.7, 0.92, var11))
         * (1.0 - 0.55 * AlpineLayout.smooth(0.72, 0.95, var13) * AlpineLayout.smooth(0.3, 0.6, var11));
      if (var17 < 0.4) {
         return new double[]{0.0, 0.0};
      } else {
         double var19 = 0.0;
         double var21 = 0.0;
         double var23 = var17;
         double var25 = 0.004166666666666667;
         double var27 = 0.0;
         double var29 = 0.0;
         double var31 = 0.0;

         for (int var33 = 0; var33 < 5; var33++) {
            double var34 = var5 + var27 * 0.6;
            double var36 = var7 + var29 * 0.6;
            double var38 = Math.hypot(var34, var36);
            if (var38 < 1.0E-6) {
               break;
            }

            double var40 = var36 / var38;
            double var42 = -var34 / var38;
            double[] var44 = this.vee(var1 * var25, var3 * var25, var40, var42, (long)(7781 + var33 * 17));
            var19 += var44[0] * var23;
            var21 += var23;
            var27 += var44[1] * var23 * var25;
            var29 += var44[2] * var23 * var25;
            if (var33 == 0) {
               var31 = var44[0];
            }

            var23 *= 0.42;
            var25 *= 2.07;
         }

         double var45 = var21 > 0.0 ? var19 / var21 : 0.0;
         return new double[]{var19 - 0.25 * var17, Math.clamp(var45 * 0.7 + var31 * 0.3, -1.0, 1.0)};
      }
   }

   private double[] vee(double var1, double var3, double var5, double var7, long var9) {
      double var11 = Math.floor(var1);
      double var13 = Math.floor(var3);
      double var15 = var1 - var11;
      double var17 = var3 - var13;
      double var19 = 0.0;
      double var21 = 0.0;
      double var23 = 0.0;
      double var25 = 0.0;

      for (int var27 = -1; var27 <= 1; var27++) {
         for (int var28 = -1; var28 <= 1; var28++) {
            long var29 = this.noise.hash((int)var11 + var27, (int)var13 + var28, var9);
            double var31 = unit(var29) - 0.5;
            double var33 = unit(mix(var29)) - 0.5;
            double var35 = var15 - (double)var27 - 0.5 - var31;
            double var37 = var17 - (double)var28 - 0.5 - var33;
            double var39 = Math.exp(-(var35 * var35 + var37 * var37) * 2.0);
            double var41 = (var35 * var5 + var37 * var7) * (Math.PI * 2);
            double var43 = Math.cos(var41 * 0.5);
            double var45 = Math.sin(var41 * 0.5);
            double var47 = 2.0 * Math.abs(var43) - 1.25;
            double var49 = (double)(-(var43 >= 0.0 ? 1 : -1)) * var45 * (Math.PI * 2) * Math.min(1.0, Math.abs(var43) * 8.0);
            var19 += var47 * var39;
            var21 += var49 * var5 * var39;
            var23 += var49 * var7 * var39;
            var25 += var39;
         }
      }

      return new double[]{var19 / var25, var21 / var25, var23 / var25};
   }

   private double[] gully(double var1, double var3, double var5, double var7, long var9) {
      double var11 = Math.floor(var1);
      double var13 = Math.floor(var3);
      double var15 = var1 - var11;
      double var17 = var3 - var13;
      double var19 = 0.0;
      double var21 = 0.0;
      double var23 = 0.0;
      double var25 = 0.0;

      for (int var27 = -1; var27 <= 1; var27++) {
         for (int var28 = -1; var28 <= 1; var28++) {
            long var29 = this.noise.hash((int)var11 + var27, (int)var13 + var28, var9);
            double var31 = unit(var29) - 0.5;
            double var33 = unit(mix(var29)) - 0.5;
            double var35 = var15 - (double)var27 - 0.5 - var31;
            double var37 = var17 - (double)var28 - 0.5 - var33;
            double var39 = Math.exp(-(var35 * var35 + var37 * var37) * 2.0);
            double var41 = (var35 * var5 + var37 * var7) * (Math.PI * 2);
            double var43 = Math.cos(var41);
            double var45 = Math.sin(var41);
            var19 += var43 * var39;
            var21 -= var45 * (Math.PI * 2) * var5 * var39;
            var23 -= var45 * (Math.PI * 2) * var7 * var39;
            var25 += var39;
         }
      }

      return new double[]{var19 / var25, var21 / var25, var23 / var25};
   }

   private int reachExtra() {
      return this.v21 ? 25 : 14;
   }

   AlpineMountains.Lake lakeAt(double var1, double var3) {
      int var5 = this.reachExtra();
      AlpineMountains.Lake var6 = null;
      double var7 = Double.MAX_VALUE;
      double var9 = var1 + 60.0 * this.noise.noise(var1 / 460.0, var3 / 460.0, 7711L);
      double var11 = var3 + 60.0 * this.noise.noise(var1 / 460.0 + 17.1, var3 / 460.0 - 9.3, 7713L);
      int var13 = Math.floorDiv((int)Math.floor(var9), 1500);
      int var14 = Math.floorDiv((int)Math.floor(var11), 1500);

      for (int var15 = -1; var15 <= 1; var15++) {
         for (int var16 = -1; var16 <= 1; var16++) {
            AlpineMountains.Lake var17 = this.tarn(var13 + var15, var14 + var16);
            if (var17 != null) {
               double var18 = Math.hypot(var1 - var17.cx, var3 - var17.cz) / (var17.radius * 1.35 + (double)var5);
               if (var18 < 1.0 && var18 < var7) {
                  var7 = var18;
                  var6 = var17;
               }
            }
         }
      }

      int var22 = Math.floorDiv((int)Math.floor(var1), 640);
      int var23 = Math.floorDiv((int)Math.floor(var3), 640);

      for (int var24 = -1; var24 <= 1; var24++) {
         for (int var25 = -1; var25 <= 1; var25++) {
            AlpineMountains.Lake var19 = this.pond(var22 + var24, var23 + var25);
            if (var19 != null) {
               double var20 = Math.hypot(var1 - var19.cx, var3 - var19.cz) / (var19.radius * 1.35 + (double)var5);
               if (var20 < 1.0 && var20 < var7) {
                  var7 = var20;
                  var6 = var19;
               }
            }
         }
      }

      return var6;
   }

   private AlpineMountains.Lake cached(long var1, Supplier<AlpineMountains.Lake> var3) {
      AlpineMountains.Lake var4 = this.lakes.get(var1);
      if (var4 == null) {
         if (this.lakes.size() > 60000) {
            this.lakes.clear();
         }

         var4 = (AlpineMountains.Lake)var3.get();
         if (var4 == null) {
            var4 = NO_LAKE;
         }

         this.lakes.put(var1, var4);
      }

      return var4 == NO_LAKE ? null : var4;
   }

   private AlpineMountains.Lake tarn(int var1, int var2) {
      long var3 = (long)var1 << 32 ^ (long)var2 & 4294967295L;
      return this.cached(
         var3,
         () -> {
            AlpineMountains.Spec var3x = this.spec(var1, var2);
            if (var3x != null && var3x.land && var3x.kind != 1 && !(unit(mix(var3x.h + 31L)) > 0.7)) {
               int var4 = (int)(unit(mix(var3x.h + 33L)) * (double)var3x.arms);
               double var5 = var3x.a0 + (double)var4 * (Math.PI * 2) / (double)var3x.arms + (unit(mix(var3x.h + 8L + (long)var4)) - 0.5) * 0.8;
               double var7 = Math.IEEEremainder(
                  var3x.a0
                     + (double)(var4 + 1) * (Math.PI * 2) / (double)var3x.arms
                     + (unit(mix(var3x.h + 8L + (long)((var4 + 1) % var3x.arms))) - 0.5) * 0.8
                     - var5,
                  Math.PI * 2
               );
               double var9 = 18.0 + 24.0 * unit(mix(var3x.h + 32L));
               double var11 = Math.cos(var3x.axis);
               double var13 = Math.sin(var3x.axis);

               for (double var18 : new double[]{0.6, 0.52, 0.68, 0.44, 0.76}) {
                  for (double var23 : new double[]{0.5, 0.38, 0.62}) {
                     double var25 = var5 + var7 * var23;
                     double var27 = var18 * var3x.R * (0.62 + 0.6 * this.armStrength(var3x, var25, var18 * var3x.R * 0.7));
                     double var29 = var27 * Math.cos(var25) * var3x.elong;
                     double var31 = var27 * Math.sin(var25);
                     AlpineMountains.Lake var33 = this.settle(
                        var3x.px + var29 * var11 - var31 * var13, var3x.pz + var29 * var13 + var31 * var11, var9, false, var3x.h
                     );
                     if (var33 != null) {
                        return var33;
                     }
                  }
               }

               return null;
            } else {
               return null;
            }
         }
      );
   }

   private AlpineMountains.Lake pond(int var1, int var2) {
      long var3 = (long)(var1 + 1073741824) << 32 ^ (long)var2 & 4294967295L ^ 99344109427290L;
      return this.cached(var3, () -> {
         long var3x = this.noise.hash(var1, var2, 7741L);
         if (unit(var3x) > 0.36) {
            return null;
         } else {
            double var5 = 11.0 + 15.0 * unit(mix(var3x + 3L));

            for (int var7 = 0; var7 < 4; var7++) {
               double var8 = ((double)var1 + 0.2 + 0.6 * unit(mix(var3x + 1L + (long)(var7 * 7)))) * 640.0;
               double var10 = ((double)var2 + 0.2 + 0.6 * unit(mix(var3x + 2L + (long)(var7 * 7)))) * 640.0;
               AlpineMountains.Lake var12 = this.settle(var8, var10, var5, true, var3x);
               if (var12 != null) {
                  return var12;
               }
            }

            return null;
         }
      });
   }

   private AlpineMountains.Lake settle(double var1, double var3, double var5, boolean var7, long var8) {
      AlpineLayout.Sample var10 = this.land.naturalDry(var1, var3);
      if (var10.water() <= Integer.MIN_VALUE && !AlpineLayout.sea(var10.biome())) {
         if (var7) {
            if (var10.ground() > 300.0 || var10.ground() > var10.snowLine() - 110.0) {
               return null;
            }

            if ((var10.fallStyle() & 7) != 0 && (var10.fallStyle() >> 3 & 63) > 20) {
               return null;
            }
         } else if (var10.ground() < var10.snowLine() - 460.0) {
            return null;
         }

         double var11 = Double.MAX_VALUE;
         double var13 = -Double.MAX_VALUE;
         double var15 = 0.0;

         for (int var17 = 0; var17 < 12; var17++) {
            double var18 = (double)var17 * (Math.PI * 2) / 12.0;
            double var20 = var1 + Math.cos(var18) * (var5 + 6.0);
            double var22 = var3 + Math.sin(var18) * (var5 + 6.0);
            double var24 = this.land.naturalDry(var20, var22).ground();
            if (var24 < var11) {
               var11 = var24;
               var15 = var18;
            }

            var13 = Math.max(var13, var24);
         }

         int var26 = (int)Math.floor(Math.min(var11 + (var7 ? 1.2 : 0.6), var10.ground() - 1.0));
         if (var10.ground() - (double)var26 > (double)(var7 ? 5 : 15)) {
            return null;
         } else {
            return var13 - var11 > (double)(var7 ? 16 : 70)
               ? null
               : new AlpineMountains.Lake(var1, var3, var5, var26, var7, var15, unit(mix(var8 + 40L)) * 50.0);
         }
      } else {
         return null;
      }
   }

   static record Lake(double cx, double cz, double radius, int level, boolean pond, double lowAngle, double shoreSeed) {
   }

   private static record Spec(
      double px, double pz, int kind, double H, double R, double elong, double axis, int arms, double a0, double armWidth, long h, boolean land
   ) {
   }

   private static record SpecSlot(int cx, int cz, AlpineMountains.Spec spec) {
   }
}
