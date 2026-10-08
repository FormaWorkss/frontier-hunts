package com.formaworks.frontierhunts.landscape;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReferenceArray;

public final class AlpineWatershed {
   private static final double CELL = 640.0;
   private static final int[] DX = new int[]{1, -1, 0, 0};
   private static final int[] DZ = new int[]{0, 0, 1, -1};
   private final AlpineLayout noise;
   private final AlpineDrainage modern;
   private final AtomicReferenceArray<AlpineWatershed.Node> nodes = new AtomicReferenceArray<>(2048);
   private final AtomicReferenceArray<AlpineWatershed.Region> regions = new AtomicReferenceArray<>(256);
   private final AtomicReferenceArray<AlpineWatershed.Runoff> flows = new AtomicReferenceArray<>(4096);

   public AlpineWatershed(AlpineLayout var1) {
      this.noise = var1;
      this.modern = var1.version() >= 4 ? new AlpineDrainage(var1) : null;
   }

   public AlpineFalls falls() {
      return this.modern == null ? null : this.modern.falls();
   }

   public AlpineFalls designer() {
      return this.modern != null && this.modern.falls() != null ? new AlpineFalls(this.noise, this.modern::natural) : null;
   }

   private static double unit(long var0) {
      return (double)(var0 >>> 11) * 1.110223E-16F;
   }

   private long key(int var1, int var2, long var3) {
      return AlpineLayout.mix(
         this.noise.seed()
            ^ AlpineLayout.mix((long)var1 * 7146057691288625177L)
            ^ Long.rotateLeft(AlpineLayout.mix((long)var2 * -7046029254386353131L), 31)
            ^ var3
      );
   }

   private AlpineWatershed.Node node(int var1, int var2) {
      long var3 = this.key(var1, var2, 7019L);
      int var5 = (int)var3 & 2047;
      AlpineWatershed.Node var6 = this.nodes.get(var5);
      if (var6 != null && var6.x == var1 && var6.z == var2) {
         return var6;
      } else {
         double var7 = ((double)var1 + 0.22 + 0.56 * unit(AlpineLayout.mix(var3 + 1L))) * 640.0;
         double var9 = ((double)var2 + 0.22 + 0.56 * unit(AlpineLayout.mix(var3 + 2L))) * 640.0;
         var6 = new AlpineWatershed.Node(var1, var2, var7, var9, Math.max(64.0, this.potential(var7, var9) - 12.0), var3);
         this.nodes.set(var5, var6);
         return var6;
      }
   }

   public double continental(double var1, double var3) {
      if (this.modern != null) {
         return this.modern.continental(var1, var3);
      } else {
         double var5 = var1 + 800.0 * this.noise.noise(var1 / 2100.0, var3 / 2100.0, 7005L);
         double var7 = var3 + 800.0 * this.noise.noise(var1 / 2100.0, var3 / 2100.0, 7007L);
         return this.noise.noise(var5 / 5200.0, var7 / 5200.0, 7009L) + 0.12 * this.noise.noise(var1 / 1100.0, var3 / 1100.0, 7011L);
      }
   }

   public double potential(double var1, double var3) {
      if (this.modern != null) {
         return this.modern.potential(var1, var3);
      } else {
         double var5 = 168.0 + 100.0 * this.noise.noise(var1 / 2100.0, var3 / 2100.0, 7001L) + 36.0 * this.noise.noise(var1 / 4300.0, var3 / 4300.0, 7003L);
         return AlpineLayout.lerp(76.0, var5, AlpineLayout.smooth(-0.18, 0.14, this.continental(var1, var3)));
      }
   }

   private AlpineWatershed.Node outlet(AlpineWatershed.Node var1) {
      AlpineWatershed.Node var2 = var1;

      for (int var3 = 0; var3 < 4; var3++) {
         AlpineWatershed.Node var4 = this.node(var1.x + DX[var3], var1.z + DZ[var3]);
         if (var4.level < var2.level) {
            var2 = var4;
         }
      }

      return var2;
   }

   private double runoff(AlpineWatershed.Node var1) {
      int var2 = (int)var1.hash & 4095;
      AlpineWatershed.Runoff var3 = this.flows.get(var2);
      if (var3 != null && var3.x == var1.x && var3.z == var1.z) {
         return var3.amount;
      } else {
         double var4 = AlpineLayout.smooth(-0.14, 0.42, this.noise.noise(var1.px / 2300.0, var1.pz / 2300.0, 7041L));
         double var6 = 0.025 + var4 * var4 * (0.2 + unit(AlpineLayout.mix(var1.hash + 79L)) * 0.85);

         for (int var8 = 0; var8 < 4; var8++) {
            AlpineWatershed.Node var9 = this.node(var1.x + DX[var8], var1.z + DZ[var8]);
            if (!(var9.level <= var1.level)) {
               AlpineWatershed.Node var10 = this.outlet(var9);
               if (var10.x == var1.x && var10.z == var1.z) {
                  var6 += this.runoff(var9);
               }
            }
         }

         this.flows.set(var2, new AlpineWatershed.Runoff(var1.x, var1.z, var6));
         return var6;
      }
   }

   private AlpineWatershed.Region region(int var1, int var2) {
      int var3 = (int)this.key(var1, var2, 7021L) & 0xFF;
      AlpineWatershed.Region var4 = this.regions.get(var3);
      if (var4 != null && var4.x == var1 && var4.z == var2) {
         return var4;
      } else {
         ArrayList var5 = new ArrayList();
         ArrayList var6 = new ArrayList();

         for (int var7 = -3; var7 <= 3; var7++) {
            for (int var8 = -3; var8 <= 3; var8++) {
               AlpineWatershed.Node var9 = this.node(var1 + var7, var2 + var8);
               AlpineWatershed.Node var10 = this.outlet(var9);
               double var11 = this.runoff(var9);
               if (!(var11 < 1.15)) {
                  if (var10 == var9) {
                     if (var9.level > 64.0) {
                        var6.add(var9);
                     }
                  } else {
                     AlpineWatershed.Reach var13 = new AlpineWatershed.Reach(var9, var10, var11, this.runoff(var10));
                     double var14 = Double.MAX_VALUE;
                     double var16 = var14;
                     double var18 = -var14;
                     double var20 = -var14;

                     for (int var22 = 0; var22 < 17; var22++) {
                        var14 = Math.min(var14, var13.x[var22]);
                        var18 = Math.max(var18, var13.x[var22]);
                        var16 = Math.min(var16, var13.z[var22]);
                        var20 = Math.max(var20, var13.z[var22]);
                     }

                     if (var18 + 480.0 >= (double)var1 * 640.0
                        && var14 - 480.0 <= (double)(var1 + 1) * 640.0
                        && var20 + 480.0 >= (double)var2 * 640.0
                        && var16 - 480.0 <= (double)(var2 + 1) * 640.0) {
                        var5.add(var13);
                     }
                  }
               }
            }
         }

         AlpineWatershed.Region var23 = new AlpineWatershed.Region(
            var1, var2, var5.toArray(AlpineWatershed.Reach[]::new), var6.toArray(AlpineWatershed.Node[]::new)
         );
         this.regions.set(var3, var23);
         return var23;
      }
   }

   public AlpineWatershed.Water water(double var1, double var3) {
      if (this.modern != null) {
         return this.modern.water(var1, var3);
      } else {
         AlpineWatershed.Region var5 = this.region((int)Math.floor(var1 / 640.0), (int)Math.floor(var3 / 640.0));
         double var6 = Double.MAX_VALUE;
         double var8 = 9999.0;
         double var10 = 0.0;
         double var12 = 100.0;
         double var14 = 0.0;
         double var16 = 0.0;
         double var18 = 0.0;
         double var20 = 0.0;
         int var22 = 0;
         boolean var23 = false;
         double var24 = 0.0;
         double var26 = 0.0;
         double var28 = 0.0;

         for (AlpineWatershed.Reach var33 : var5.reaches) {
            double var34 = Double.MAX_VALUE;
            double var36 = 0.0;
            double var38 = 0.0;
            double var40 = 0.0;

            for (int var42 = 0; var42 < 16; var42++) {
               double var43 = var33.x[var42 + 1] - var33.x[var42];
               double var45 = var33.z[var42 + 1] - var33.z[var42];
               double var47 = var43 * var43 + var45 * var45;
               double var49 = Math.clamp(((var1 - var33.x[var42]) * var43 + (var3 - var33.z[var42]) * var45) / var47, 0.0, 1.0);
               double var51 = var1 - var33.x[var42] - var43 * var49;
               double var53 = var3 - var33.z[var42] - var45 * var49;
               double var55 = var51 * var51 + var53 * var53;
               if (var55 < var34) {
                  var34 = var55;
                  var36 = (var33.length[var42] + var49 * Math.sqrt(var47)) / var33.length[16];
                  var38 = var43 / Math.sqrt(var47);
                  var40 = var45 / Math.sqrt(var47);
               }
            }

            var36 = Math.clamp(
               var36
                  + (7.0 * this.noise.noise(var1 / 43.0, var3 / 43.0, 7027L) + 2.0 * this.noise.noise(var1 / 19.0, var3 / 19.0, 7025L))
                     * Math.sin(Math.PI * var36)
                     / var33.length[16],
               0.0,
               1.0
            );
            double var71 = AlpineLayout.lerp(var33.widthA, var33.widthB, var36) * (1.0 + 0.5 * this.noise.noise(var1 / 85.0, var3 / 85.0, 7029L));
            if (var33.style == 4) {
               var71 *= 1.0 + 1.4 * AlpineLayout.smooth(0.22, 0.32, var36) * (1.0 - AlpineLayout.smooth(0.71, 0.83, var36));
            }

            var71 += 8.0 * AlpineLayout.smooth(0.28, 0.36, var36) * (1.0 - AlpineLayout.smooth(0.36, 0.43, var36));
            double var44 = Math.sqrt(var34);
            double var46 = var44 - var71;
            double var48 = 1.0 - AlpineLayout.smooth(var71 + 24.0, var71 + 170.0, var44);
            double var50 = var33.level(var36);
            var28 = Math.max(var28, var48);
            var48 *= var48;
            var24 += var50 * var48;
            var26 += var48;
            if (var46 < var6) {
               var6 = var46;
               var8 = var44;
               var10 = var71;
               var12 = var50;
               double var52 = 2.0 / var33.length[16];
               var14 = var33.level(Math.max(0.0, var36 - var52)) - var33.level(Math.min(1.0, var36 + var52));
               var16 = var38;
               var18 = var40;
               var20 = 0.012 + Math.min(0.048, var14 * 0.004 + var33.drop / var33.length[16] * 0.045);
               var22 = var33.style;
               var23 = false;
            }
         }

         for (AlpineWatershed.Node var65 : var5.lakes) {
            double var66 = unit(AlpineLayout.mix(var65.hash + 55L)) * Math.PI * 2.0;
            double var68 = var1 - var65.px;
            double var69 = var3 - var65.pz;
            double var70 = var68 * Math.cos(var66) - var69 * Math.sin(var66);
            double var73 = var68 * Math.sin(var66) + var69 * Math.cos(var66);
            double var74 = 0.6 + 1.4 * AlpineLayout.smooth(-0.1, 0.4, this.noise.noise(var65.px / 2100.0, var65.pz / 2100.0, 7043L));
            double var75 = Math.min(130.0, 22.0 + Math.sqrt(this.runoff(var65)) * 26.0) * var74;
            double var77 = var75 * (0.6 + unit(AlpineLayout.mix(var65.hash + 56L)));
            double var78 = var75 * (0.6 + unit(AlpineLayout.mix(var65.hash + 57L)));
            double var79 = Math.sqrt(var70 * var70 / (var77 * var77) + var73 * var73 / (var78 * var78));
            double var54 = (var77 + var78) * 0.5 * (1.0 + 0.24 * this.noise.noise(var1 / 70.0, var3 / 70.0, 7031L));
            double var56 = var79 * (var77 + var78) * 0.5;
            double var58 = var56 - var54;
            double var60 = 1.0 - AlpineLayout.smooth(var54 + 24.0, var54 + 170.0, var56);
            var28 = Math.max(var28, var60);
            var60 *= var60;
            var24 += var65.level * var60;
            var26 += var60;
            if (var58 < var6) {
               var6 = var58;
               var8 = var56;
               var10 = var54;
               var12 = var65.level;
               var14 = 0.0;
               var20 = 0.0;
               var18 = 0.0;
               var16 = 0.0;
               var22 = 6;
               var23 = true;
            }
         }

         return new AlpineWatershed.Water(var8, var10, var12, var14, var16, var18, var20, var22, var23, var26 > 0.0 ? var24 / var26 : var12, var28);
      }
   }

   public AlpineLayout.Sample sample(double var1, double var3) {
      if (this.modern != null) {
         return this.modern.sample(var1, var3);
      } else {
         AlpineWatershed.Water var5 = this.water(var1, var3);
         double var6 = this.continental(var1, var3);
         double var8 = AlpineLayout.smooth(-0.24, 0.1, var6);
         double var10 = AlpineLayout.lerp(64.0, var5.level, AlpineLayout.smooth(-0.24, -0.08, var6));
         double var12 = var1 + 340.0 * this.noise.noise(var1 / 2600.0, var3 / 2600.0, 7091L);
         double var14 = var3 + 340.0 * this.noise.noise(var1 / 2600.0, var3 / 2600.0, 7093L);
         double var16 = AlpineLayout.smooth(0.14, 0.48, this.noise.noise(var12 / 2350.0, var14 / 2350.0, 7101L));
         double var18 = AlpineLayout.smooth(0.02, 0.38, this.noise.noise(var1 / 570.0, var3 / 570.0, 7103L));
         double var20 = Math.pow(1.0 - Math.abs(this.noise.fbm(var1 / 640.0, var3 / 640.0, 7105L, 3)), 2.8);
         double var22 = var16 * (220.0 + 430.0 * var20);
         double var24 = 12.0 + 36.0 * AlpineLayout.smooth(-0.15, 0.45, this.noise.noise(var1 / 1450.0, var3 / 1450.0, 7095L));
         double var26 = this.potential(var1, var3) + 16.0 + var24 * this.noise.fbm(var1 / 260.0, var3 / 260.0, 7107L, 3) + var22;
         var26 += var16
            * var18
            * (
               38.0 * Math.pow(1.0 - Math.abs(this.noise.noise(var1 / 69.0, var3 / 69.0, 7109L)), 5.0)
                  - 12.0 * Math.abs(this.noise.noise(var1 / 31.0, var3 / 31.0, 7111L))
            );
         double var28 = AlpineLayout.smooth(0.23, 0.51, this.noise.noise(var12 / 1900.0, var14 / 1900.0, 7113L)) * (1.0 - var16);
         var26 += var28 * 180.0 * Math.pow(AlpineLayout.smooth(-0.2, 0.55, this.noise.noise(var1 / 140.0, var3 / 140.0, 7115L)), 2.0);
         double var30 = AlpineLayout.smooth(-0.04, 0.31, this.noise.noise(var1 / 1500.0, var3 / 1500.0, 7117L)) * (1.0 - var16) * (1.0 - var28);
         var26 = AlpineLayout.lerp(var26, this.potential(var1, var3) + 12.0 + 9.0 * this.noise.noise(var1 / 185.0, var3 / 185.0, 7119L), var30 * 0.65);
         if (var26 > 780.0) {
            var26 = 780.0 + 100.0 * Math.tanh((var26 - 780.0) / 100.0);
         }

         double var32 = AlpineLayout.lerp(
            -38.0 + 12.0 * this.noise.noise(var1 / 310.0, var3 / 310.0, 7131L),
            46.0 + 6.0 * this.noise.noise(var1 / 130.0, var3 / 130.0, 7133L),
            AlpineLayout.smooth(-0.65, -0.22, var6)
         );
         var26 = AlpineLayout.lerp(var32, var26, var8);
         double var34 = 10.0 + Math.min(16.0, var5.width * 0.5) + 8.0 * (0.5 + 0.5 * this.noise.noise(var1 / 140.0, var3 / 140.0, 7121L));
         double var36 = AlpineLayout.smooth(var5.width, var5.width + var34, var5.distance);
         double var38 = 1.0 - AlpineLayout.smooth(var5.width + var34, var5.width + var34 + 14.0, var5.distance);
         double var40 = var5.valleyLevel + 12.0 + 8.0 * this.noise.noise(var1 / 110.0, var3 / 110.0, 7123L);
         var26 = AlpineLayout.lerp(var26, var40, var5.influence * (1.0 - var16 * 0.3) * AlpineLayout.smooth(-0.23, -0.08, var6));
         double var42 = AlpineLayout.lerp(var26, Math.max(var26, var10 + 2.0), var38);
         double var44 = (var5.lake ? 9.0 : 3.5 + Math.min(4.0, var5.width * 0.13)) + 1.5 * this.noise.noise(var1 / 17.0, var3 / 17.0, 7125L);
         if (var6 > -0.24) {
            var26 = AlpineLayout.lerp(var10 - var44, var42, var36);
         }

         int var46 = (int)Math.floor(var1 / 40.0);
         int var47 = (int)Math.floor(var3 / 40.0);
         long var48 = this.key(var46, var47, 7141L);
         double var50 = 0.0;
         double var52 = var5.distance < var5.width + 50.0 ? 0.26 : 0.055;
         if (unit(var48) < var52) {
            double var54 = (double)(var46 * 40 + 10) + 20.0 * unit(AlpineLayout.mix(var48 + 1L));
            double var56 = (double)(var47 * 40 + 10) + 20.0 * unit(AlpineLayout.mix(var48 + 2L));
            double var58 = 3.0 + 5.0 * unit(AlpineLayout.mix(var48 + 3L));
            double var60 = 3.0 + 5.0 * unit(AlpineLayout.mix(var48 + 4L));
            double var62 = (var1 - var54) * (var1 - var54) / (var58 * var58) + (var3 - var56) * (var3 - var56) / (var60 * var60);
            if (var62 < 1.0) {
               var50 = Math.pow(1.0 - var62, 0.6) * (4.0 + 9.0 * unit(AlpineLayout.mix(var48 + 5L)));
               var26 += var50;
            }
         }

         if (var5.style == 5 && var5.distance < var5.width && this.noise.noise(var1 / 29.0, var3 / 29.0, 7143L) > 0.4) {
            double var70 = AlpineLayout.smooth(0.4, 0.64, this.noise.noise(var1 / 29.0, var3 / 29.0, 7143L)) * 8.0;
            var26 += var70;
            var50 += var70;
         }

         int var71 = var6 > -0.24 && var5.distance < var5.width + var34 && var26 < Math.floor(var10) ? (int)Math.floor(var10) : Integer.MIN_VALUE;
         boolean var55 = var6 < 0.1 && var26 < 64.0;
         if (var55) {
            var71 = 64;
         }

         double var72 = 0.5 + 0.5 * this.noise.noise(var1 / 600.0, var3 / 600.0, 7151L);
         double var73 = 470.0 + 90.0 * this.noise.noise(var1 / 520.0, var3 / 520.0, 7153L);
         double var74 = AlpineLayout.smooth(-0.3, 0.18, this.noise.noise(var1 / 430.0, var3 / 430.0, 7155L));
         double var75 = (0.52 + 0.46 * var74) * (1.0 - var30 * 0.88);
         var75 *= AlpineLayout.smooth(var5.width - 1.0, var5.width + 13.0, var5.distance) * (1.0 - AlpineLayout.smooth(var73 + 10.0, var73 + 75.0, var26));
         int var64;
         if (var55) {
            var64 = var26 < 25.0 ? 19 : 18;
         } else if (var6 < -0.04 && var26 < 72.0) {
            var64 = 20;
         } else if (var71 > (int)Math.floor(var26)) {
            var64 = 0;
         } else if (var26 > var73) {
            var64 = var75 > 0.3 && var26 < var73 + 65.0 ? 9 : 5;
         } else if (var28 > 0.58) {
            var64 = 14;
         } else if (var26 > var73 - 60.0) {
            var64 = var18 > 0.55 ? 11 : 4;
         } else if (var5.lake && var5.distance < var5.width + 36.0) {
            var64 = 17;
         } else if (var30 > 0.62) {
            var64 = var72 > 0.65 ? 10 : (var72 > 0.5 ? 15 : 1);
         } else if (var72 > 0.66 && var16 < 0.15) {
            var64 = 16;
         } else if (var5.distance < var5.width + 100.0 && var75 > 0.5) {
            var64 = 12;
         } else if (var72 > 0.53 && var75 > 0.58) {
            var64 = 13;
         } else if (var75 > 0.55) {
            var64 = switch (this.noise.woodland((int)var1, (int)var3)) {
               case 0 -> 7;
               case 1 -> 8;
               case 2 -> 6;
               default -> 2;
            };
         } else {
            var64 = var16 > 0.35 ? 3 : 1;
         }

         if (var55 || var64 == 20) {
            var75 = 0.0;
         }

         if (var64 == 1 || var64 == 10 || var64 == 15) {
            var75 = Math.min(var75, 0.08);
         }

         return new AlpineLayout.Sample(
            var26,
            var71,
            var55 ? 64.0 : var10,
            var55 ? 0.0 : var5.distance,
            var55 ? 9999.0 : var5.width,
            var72,
            var75,
            var73,
            var55 ? 0.0 : var5.fall,
            var64,
            var5.style,
            var50
         );
      }
   }

   private static record Node(int x, int z, double px, double pz, double level, long hash) {
   }

   private static final class Reach {
      final AlpineWatershed.Node a;
      final AlpineWatershed.Node b;
      final double[] x = new double[17];
      final double[] z = new double[17];
      final double[] length = new double[17];
      final double widthA;
      final double widthB;
      final double drop;
      final int style;

      Reach(AlpineWatershed.Node var1, AlpineWatershed.Node var2, double var3, double var5) {
         this.a = var1;
         this.b = var2;
         this.drop = var1.level - var2.level;
         this.style = (int)(AlpineWatershed.unit(AlpineLayout.mix(var1.hash + 19L)) * 6.0);
         this.widthA = Math.min(48.0, 1.5 + Math.sqrt(var3) * 5.0) * (0.7 + AlpineWatershed.unit(AlpineLayout.mix(var1.hash + 21L)) * 0.6);
         this.widthB = Math.min(54.0, 1.5 + Math.sqrt(var5) * 5.0) * (0.7 + AlpineWatershed.unit(AlpineLayout.mix(var2.hash + 21L)) * 0.6);
         double var7 = var2.px - var1.px;
         double var9 = var2.pz - var1.pz;
         double var11 = Math.hypot(var7, var9);
         double var13 = (AlpineWatershed.unit(AlpineLayout.mix(var1.hash + 23L)) - 0.5)
            * var11
            * (0.3 + 0.55 * AlpineWatershed.unit(AlpineLayout.mix(var1.hash + 24L)));

         for (int var15 = 0; var15 <= 16; var15++) {
            double var16 = (double)var15 / 16.0;
            double var18 = Math.sin(var16 * Math.PI) * var13
               + Math.sin(var16 * Math.PI * 2.0) * var11 * (0.02 + 0.12 * AlpineWatershed.unit(AlpineLayout.mix(var1.hash + 25L)));
            this.x[var15] = AlpineLayout.lerp(var1.px, var2.px, var16) - var9 / var11 * var18;
            this.z[var15] = AlpineLayout.lerp(var1.pz, var2.pz, var16) + var7 / var11 * var18;
            if (var15 > 0) {
               this.length[var15] = this.length[var15 - 1] + Math.hypot(this.x[var15] - this.x[var15 - 1], this.z[var15] - this.z[var15 - 1]);
            }
         }
      }

      double level(double var1) {
         double var3 = switch (this.style) {
            case 0 -> AlpineLayout.smooth(0.05, 0.95, var1);
            case 1 -> AlpineLayout.smooth(0.447, 0.453, var1);
            case 2 -> 0.48 * AlpineLayout.smooth(0.356, 0.364, var1) + 0.52 * AlpineLayout.smooth(0.599, 0.607, var1);
            case 3, 4 -> 0.22 * AlpineLayout.smooth(0.3, 0.325, var1)
            + 0.34 * AlpineLayout.smooth(0.43, 0.45, var1)
            + 0.26 * AlpineLayout.smooth(0.57, 0.59, var1)
            + 0.18 * AlpineLayout.smooth(0.69, 0.71, var1);
            default -> 0.4 * AlpineLayout.smooth(0.39, 0.415, var1) + 0.6 * AlpineLayout.smooth(0.57, 0.59, var1);
         };
         return this.a.level - this.drop * (var1 * 0.22 + var3 * 0.78);
      }
   }

   private static record Region(int x, int z, AlpineWatershed.Reach[] reaches, AlpineWatershed.Node[] lakes) {
   }

   private static record Runoff(int x, int z, double amount) {
   }

   public static record Water(
      double distance,
      double width,
      double level,
      double fall,
      double dx,
      double dz,
      double speed,
      int style,
      boolean lake,
      double valleyLevel,
      double influence
   ) {
   }
}
