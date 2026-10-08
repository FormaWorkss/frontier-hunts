package com.formaworks.frontierhunts.landscape;

import java.util.concurrent.atomic.AtomicReferenceArray;

public final class AlpineLayout {
   public static final int MIN_Y = -64;
   public static final int HEIGHT = 1024;
   private final long seed;
   private final int version;
   private final AlpineWatershed watershed;
   private final AtomicReferenceArray<AlpineLayout.CachedColumn> columnCache = new AtomicReferenceArray<>(16384);
   private final double sin;
   private final double cos;
   private final double offsetX;
   private final double offsetZ;
   private final AtomicReferenceArray<AlpineLayout.Drainage> drainageCache = new AtomicReferenceArray<>(256);
   public static final int GOLDEN_ASPEN = 24;
   public static final int AUTUMN_MAPLE = 25;
   public static final int LARCH = 26;
   public static final int BURN = 27;
   public static final int MOOR = 28;
   public static final int BENCH = 29;
   public static final int TALUS = 30;
   public static final int MUSKEG = 31;
   public static final double ACCENT = 0.3;
   public static final int FELLFIELD = 32;
   public static final int ASPEN_PARK = 33;
   public static final int BLUFF = 34;
   public static final int ALDER_CARR = 35;
   public static final int ROWAN_SLOPE = 36;
   public static final int COTTONWOOD_BOTTOM = 37;
   public static final int BIRCH_HEATH = 38;

   public double trail(double var1, double var3) {
      double var5 = smooth(-0.1, 0.3, this.noise(var1 / 1300.0, var3 / 1300.0, 7789L));
      if (var5 <= 0.0) {
         return 0.0;
      } else {
         double var7 = var1 + 45.0 * this.noise(var1 / 160.0, var3 / 160.0, 7781L);
         double var9 = var3 + 45.0 * this.noise(var1 / 160.0 + 9.1, var3 / 160.0 - 3.3, 7783L);
         double var11 = Math.abs(this.noise(var7 / 360.0, var9 / 360.0, 7785L)) / 0.008;
         double var13 = Math.abs(this.noise(var9 / 560.0 + 17.0, var7 / 560.0 - 5.0, 7787L)) / 0.0062;
         double var15 = 1.0 - Math.min(var11, var13);
         return var15 <= 0.0 ? 0.0 : Math.min(1.0, var15 * 1.6) * var5;
      }
   }

   public static boolean ground(int var0) {
      return var0 >= 28 && var0 <= 31;
   }

   public static boolean sea(int var0) {
      return var0 >= 18 && var0 < 24;
   }

   public double accent(double var1, double var3) {
      return this.noise(var1 / 620.0, var3 / 620.0, 7401L) + 0.25 * this.noise(var1 / 170.0, var3 / 170.0, 7405L);
   }

   public double district(double var1, double var3) {
      return this.noise(var1 / 540.0, var3 / 540.0, 7411L) + 0.3 * this.noise(var1 / 150.0, var3 / 150.0, 7413L);
   }

   public static boolean extra(int var0) {
      return var0 >= 32 && var0 <= 38;
   }

   public static boolean soil(int var0) {
      return ground(var0) || extra(var0);
   }

   public double region(double var1, double var3) {
      return this.noise(var1 / 700.0, var3 / 700.0, 7421L) + 0.28 * this.noise(var1 / 190.0, var3 / 190.0, 7423L);
   }

   public double weave(double var1, double var3) {
      return 0.052 * this.noise(var1 / 37.0, var3 / 37.0, 7431L) + 0.021 * this.noise(var1 / 12.5, var3 / 12.5, 7433L);
   }

   public AlpineLayout(long var1) {
      this(var1, 1);
   }

   public AlpineLayout(long var1, int var3) {
      if (var3 >= 1 && var3 <= 21) {
         this.version = var3;
         this.seed = var1;
         this.watershed = var3 >= 3 ? new AlpineWatershed(this) : null;
         double var4 = unit(mix(var1 ^ 2015341625L)) * Math.PI * 2.0;
         this.sin = Math.sin(var4);
         this.cos = Math.cos(var4);
         this.offsetX = unit(mix(var1 ^ 1143594L)) * 24000.0;
         this.offsetZ = unit(mix(var1 ^ 11255833L)) * 24000.0;
      } else {
         throw new IllegalArgumentException("Unsupported Alpine terrain revision: " + var3);
      }
   }

   public long seed() {
      return this.seed;
   }

   public int version() {
      return this.version;
   }

   public AlpineWatershed watershed() {
      return this.watershed;
   }

   public AlpineFalls falls() {
      return this.watershed == null ? null : this.watershed.falls();
   }

   public AlpineLayout.Sample sample(double var1, double var3) {
      if (this.watershed != null) {
         if (this.version >= 10 && var1 == Math.floor(var1) && var3 == Math.floor(var3)) {
            long var98 = (long)((int)var1) << 32 ^ (long)((int)var3) & 4294967295L;
            int var99 = (int)mix(var98) & 16383;
            AlpineLayout.CachedColumn var8 = this.columnCache.get(var99);
            if (var8 != null && var8.key == var98) {
               return var8.sample;
            } else {
               AlpineLayout.Sample var100 = this.watershed.sample(var1, var3);
               this.columnCache.set(var99, new AlpineLayout.CachedColumn(var98, var100));
               return var100;
            }
         } else {
            return this.watershed.sample(var1, var3);
         }
      } else {
         double var5 = this.version >= 2 ? 1.35 : 1.0;
         double var7 = (var1 * this.cos - var3 * this.sin) * var5 + this.offsetX;
         double var9 = (var1 * this.sin + var3 * this.cos) * var5 + this.offsetZ;
         double var11 = var7 + this.fbm(var7 / 1800.0, var9 / 1800.0, 31L, 3) * 740.0 + Math.sin(var9 / 740.0) * 260.0;
         double var13 = var9 + this.fbm(var7 / 2400.0, var9 / 2400.0, 71L, 3) * 210.0;
         var11 += 65.0 * this.noise(var7 / 310.0, var9 / 310.0, 351L) + 17.0 * Math.sin(var13 / 180.0 + this.noise(var7 / 1100.0, var9 / 1100.0, 353L) * 3.0);
         long var15 = (long)Math.floor(var11 / 4096.0 + 0.5);
         long var17 = (long)Math.floor(var13 / 8192.0);
         double var19 = Math.abs(var11 - (double)(var15 * 4096L));
         AlpineLayout.Drainage var21 = this.drainage(var15, var17);
         double var22 = var13 / 8192.0;
         double var24 = var22 - Math.floor(var22);
         double var26 = Math.abs(var24 * 2.0 - 1.0);
         boolean var28 = var24 >= 0.5;
         double var29 = smooth(70.0, 1370.0, var19);
         double var31 = (12.0 + 9.0 * (0.5 + 0.5 * this.noise(var7 / 330.0, var9 / 330.0, 134L))) * var21.channelFactor;
         var31 += var21.lakeWidth * (1.0 - smooth(0.04, 0.17, var26));
         double var33 = this.noise(var7 / 140.0, var9 / 140.0, 176L) * (this.version >= 2 ? 0.012 : 0.0035)
            + this.noise(var7 / 48.0, var9 / 48.0, 177L) * (this.version >= 2 ? 0.0024 : 9.0E-4);
         double var35 = 0.0;

         for (double var40 : var21.lips) {
            double var42 = (var26 + var33 - (var40 - 0.008)) / 0.005;
            var31 += var21.poolFlare * Math.exp(-var42 * var42);
            double var44 = (var26 + var33 - var40) / 0.0018;
            var35 = Math.max(var35, Math.exp(-var44 * var44));
         }

         var31 *= 1.0 - 0.36 * var35;
         double var104 = var21.level(var26, var21.softness, var33, var28);
         double var105 = var21.level(Math.min(1.0, var26 + 7.5E-4), var21.softness, var33, var28);
         double var41 = var105 - var104;
         double var43 = this.noise(var7 / 125.0, var9 / 125.0, 178L) * 0.017 * smooth(var31 + 3.0, var31 + 55.0, var19);
         double var45 = var21.level(var26, var21.softness + 0.2 * smooth(var31 + 12.0, 350.0, var19), var33 + var43, var28);
         var45 = lerp(var45, 105.0 + (double)(this.version >= 2 ? 220 : 331) * var26, smooth(650.0, 1550.0, var19));
         double var47 = 1.0 - Math.abs(this.fbm(var7 / 790.0, var9 / 790.0, 42L, 4));
         double var49 = 280.0 + 220.0 * (0.5 + 0.5 * this.noise(var7 / 2900.0, var9 / 2900.0, 22L));
         double var51 = var29 * (var49 * Math.pow(var47, 1.7) + 75.0 * this.fbm(var7 / 360.0, var9 / 360.0, 39L, 4));
         double var53 = 1.0;
         double var55 = 0.0;
         if (this.version >= 2) {
            double var57 = smooth(-0.35, 0.36, this.noise(var7 / 2100.0, var9 / 2100.0, 1005L));
            double var59 = smooth(0.2, 0.54, this.noise(var7 / 4800.0, var9 / 4800.0, 1007L));
            var53 = 0.22 + 0.53 * var57 + 0.66 * var59;
            var51 *= var53;
            var55 = smooth(0.05, 0.43, this.noise(var7 / 720.0, var9 / 720.0, 1011L));
         }

         double var107 = smooth(-0.3, 0.32, this.fbm(var7 / 230.0, var9 / 230.0, 331L, 3));
         double var108 = smooth(var31 + 12.0, var31 + 220.0 + 190.0 * var107, var19) * (65.0 + 150.0 * var107);
         var108 += smooth(var31 + 35.0, var31 + 160.0, var19) * (1.0 - smooth(460.0, 850.0, var19)) * 19.0 * this.fbm(var7 / 64.0, var9 / 64.0, 337L, 3);
         if (this.version >= 2) {
            var108 *= 0.34 + 0.66 * var53;
         }

         double var61 = 11.0 * this.fbm(var7 / 160.0, var9 / 160.0, 92L, 3) * smooth(var31 + 5.0, 230.0, var19);
         double var63 = var45 + var51 + var108 + var61;
         if (this.version >= 2) {
            double var65 = Math.pow(1.0 - Math.abs(this.noise(var7 / 105.0, var9 / 105.0, 1013L)), 5.0);
            double var67 = Math.abs(this.noise(var7 / 43.0, var9 / 43.0, 1015L));
            var63 += var55 * smooth(var31 + 55.0, var31 + 230.0, var19) * (62.0 * var65 - 28.0 * var67);
         }

         double var114 = 0.0;
         if (this.version >= 2) {
            var114 = smooth(-0.08, 0.32, this.noise(var7 / 1050.0, var9 / 1050.0, 1301L))
               * (1.0 - smooth(0.42, 0.9, var53))
               * smooth(var31 + 40.0, var31 + 125.0, var19)
               * (1.0 - smooth(650.0, 1200.0, var19));
            double var115 = var45 + 15.0 + 19.0 * this.noise(var7 / 430.0, var9 / 430.0, 1303L) + var51 * 0.12;
            var63 = lerp(var63, var115, var114);
         }

         double var116 = 35.0 - 21.0 * var35;
         double var69 = 1.0 - smooth(var31 - 3.0, var31 + var116, var19);
         double var71 = 6.0 + Math.min(8.0, var41 * 0.2);
         double var73 = smooth(3.0, 20.0, var41) * smooth(var31 + 6.0, var31 + 48.0, var19) * (1.0 - smooth(160.0, 390.0, var19));
         var63 += var73 * (19.0 * this.fbm(var7 / 53.0, var9 / 53.0, 307L, 3) + 9.0 * this.noise(var7 / 21.0, var9 / 21.0, 309L));
         double var75 = this.fbm(var1 / 27.0, var3 / 27.0, 303L, 3);
         var63 += var75 * (0.6 + var29 * 5.0) * (1.0 - var69);
         double var77 = (
               9.0 + 31.0 * (0.5 + 0.5 * this.fbm(var7 / 85.0, var9 / 85.0, 321L, 3)) + 14.0 * (0.5 + 0.5 * this.noise(var7 / 31.0, var9 / 31.0, 327L))
            )
            * smooth(var31 - 3.0, var31 + 65.0, var19);
         double var79 = 0.22 + 0.78 * smooth(1.0, 12.0, var41);
         double var81 = 1.0 - smooth(var31 + var116, var31 + var116 + 85.0, var19);
         double var83 = lerp(var63, Math.max(var104 + 2.0 + var77 * var79, var63), var81);
         var63 = lerp(var83, var104 - var71, var69);
         if (this.version >= 2 && var21.style == 5) {
            double var85 = (var11 - (double)(var15 * 4096L)) / (var31 * 0.23);
            double var87 = Math.exp(-var85 * var85);
            double var89 = 0.0;

            for (double var94 : var21.lips) {
               double var96 = (var26 + var33 - (var94 + 0.0015)) / 0.004;
               var89 = Math.max(var89, Math.exp(-var96 * var96));
            }

            var63 = lerp(var63, Math.max(var63, var104 + 5.0 + 3.0 * this.noise(var7 / 13.0, var9 / 13.0, 1231L)), var87 * var89);
         }

         if (var63 > 780.0) {
            var63 = 780.0 + 100.0 * Math.tanh((var63 - 780.0) / 100.0);
         }

         var63 = Math.max(72.0, var63);
         int var117 = (int)Math.floor(var104);
         int var86 = var19 < var31 + var116 && var63 < (double)var117 ? var117 : Integer.MIN_VALUE;
         double var118 = 0.5 + 0.5 * this.fbm(var7 / 970.0, var9 / 970.0, 201L, 3);
         double var119 = 0.5 + 0.5 * this.noise(var7 / 1600.0, var9 / 1600.0, 403L);
         double var120 = var118 * 0.76 + var119 * 0.24 + 0.15 * smooth(100.0, 480.0, var19) + 0.08 * var119 * (1.0 - smooth(var31 + 20.0, 300.0, var19));
         double var121 = this.version >= 2
            ? 510.0 + 210.0 * this.noise(var1 / 620.0, var3 / 620.0, 1211L)
            : 600.0 + 60.0 * this.noise(var7 / 1300.0, var9 / 1300.0, 29L);
         double var95 = smooth(0.43, 0.64, var120);
         var95 *= 1.0 - 0.95 * var114;
         var95 *= smooth(var31 + 4.0, var31 + 40.0, var19)
            * (1.0 - smooth(var121 + (double)(this.version >= 2 ? 15 : -110), var121 + (double)(this.version >= 2 ? 110 : -30), var63));
         int var97 = -1;
         if (this.version >= 2) {
            if (var86 > (int)Math.floor(var63)) {
               var97 = 0;
            } else if (var63 > var121) {
               var97 = var95 > 0.32 && var63 < var121 + 85.0 ? 9 : 5;
            } else if (var114 > 0.46) {
               var97 = 1;
            } else if (var55 > 0.66 && var63 > var121 - 125.0) {
               var97 = 11;
            } else if (var95 > 0.48) {
               if (var63 > var121 - 70.0) {
                  var97 = 3;
               } else {
                  var97 = switch (this.woodland((int)var1, (int)var3)) {
                     case 0 -> 7;
                     case 1 -> 8;
                     case 2 -> 6;
                     default -> 2;
                  };
               }
            } else if (var118 > 0.56 && var19 < var31 + 120.0 && var63 < 270.0) {
               var97 = 10;
            } else {
               var97 = var63 > var121 - 65.0 ? 4 : 1;
            }
         }

         return new AlpineLayout.Sample(var63, var86, var104, var19, var31, var118, var95, var121, var41, var97, var21.style);
      }
   }

   private AlpineLayout.Drainage drainage(long var1, long var3) {
      long var5 = this.version == 1
         ? mix(this.seed ^ var1 * 7146057691288625177L ^ var3 * -7046029254386353131L ^ 29155L)
         : mix(this.seed ^ mix(var1 * 7146057691288625177L) ^ Long.rotateLeft(mix(var3 * -7046029254386353131L), 29) ^ 29155L);
      int var7 = (int)var5 & 0xFF;
      AlpineLayout.Drainage var8 = this.drainageCache.get(var7);
      if (var8 != null && var8.valley == var1 && var8.reach == var3) {
         return var8;
      } else {
         double var9 = 0.19 + 0.1 * unit(mix(var5 + 1L));
         double var11 = 0.44 + 0.11 * unit(mix(var5 + 2L));
         double var13 = 0.72 + 0.075 * unit(mix(var5 + 3L));
         double[] var15 = new double[]{
            var9, var9 + 0.014 + 0.015 * unit(mix(var5 + 4L)), var11, var11 + 0.02 + 0.014 * unit(mix(var5 + 5L)), var13, var13 + 0.024, var13 + 0.045
         };
         double var16 = 18.0 + 45.0 * unit(mix(var5 + 6L));
         double var18 = 30.0 + 60.0 * unit(mix(var5 + 7L));
         double var20 = 36.0 + 50.0 * unit(mix(var5 + 8L));
         double[] var22 = new double[]{var16, 72.0 - var16, var18, 105.0 - var18, var20, 27.0, 108.0 - var20};
         double var23 = 105.0;
         double var25 = 436.0;
         double var27 = 436.0;
         double var29 = 1.6E-4;
         double var31 = 78.0;
         double var33 = 19.0;
         double var35 = 1.0;
         double var37 = 24.0;
         int var39 = -1;
         if (this.version >= 2) {
            var23 = 92.0 + 38.0 * unit(mix(var5 + 10L));
            var25 = this.headwater(var1, var3);
            var27 = this.headwater(var1, var3 + 1L);
            double var40 = unit(mix(var5 + 11L));
            var39 = (int)(var40 * 6.0);
            var31 = 18.0 + 72.0 * unit(mix(var5 + 13L));
            if (var40 > 0.91) {
               var31 += 65.0;
            }
            var33 = switch (var39) {
               case 0 -> 205.0;
               case 1 -> 90.0;
               case 2 -> 65.0;
               case 3 -> 115.0;
               case 4 -> 125.0;
               default -> 90.0;
            };

            var35 = switch (var39) {
               case 0 -> 0.75;
               case 1 -> 0.55;
               case 2 -> 0.85;
               case 3 -> 1.05;
               case 4 -> 2.1;
               default -> 1.45;
            };
            var35 *= 0.85 + 0.3 * unit(mix(var5 + 18L));
            var37 = 10.0 + 30.0 * unit(mix(var5 + 19L));
            double var42 = unit(mix(var5 + 12L));

            var29 = switch (var39) {
               case 0 -> 0.004 + 0.006 * var42;
               case 1 -> 2.5E-4 + 4.0E-4 * var42;
               case 2 -> 5.0E-4 + 7.0E-4 * var42;
               case 3 -> 0.0012 + 0.0013 * var42;
               case 4 -> 8.0E-4 + 0.001 * var42;
               default -> 5.5E-4 + 7.0E-4 * var42;
            };

            int var44 = switch (var39) {
               case 0 -> 6 + (int)(unit(mix(var5 + 20L)) * 4.0);
               case 1, 4 -> 1;
               case 2 -> 2;
               case 3 -> 3 + (int)(unit(mix(var5 + 20L)) * 4.0);
               default -> 2 + (int)(unit(mix(var5 + 20L)) * 2.0);
            };
            var15 = new double[var44];
            var22 = new double[var44];
            double var45 = 0.22 + 0.31 * unit(mix(var5 + 21L));
            double var47 = var45;
            double var49 = 0.0;

            for (int var51 = 0; var51 < var44; var51++) {
               if (var39 == 0) {
                  var15[var51] = 0.1 + (double)var51 * 0.8 / (double)var44 + 0.035 * unit(mix(var5 + 30L + (long)var51));
               } else {
                  var15[var51] = var47;
                  var47 += 0.008 + 0.026 * unit(mix(var5 + 30L + (long)var51));
               }

               var22[var51] = 0.35 + unit(mix(var5 + 60L + (long)var51));
               var49 += var22[var51];
            }

            for (int var54 = 0; var54 < var44; var54++) {
               var22[var54] *= (331.0 - var33) / var49;
            }
         }

         AlpineLayout.Drainage var53 = new AlpineLayout.Drainage(
            var1, var3, var15, var22, var23, var25, var27, var29, var31, var33, var35, var37, var39, this.version == 1
         );
         this.drainageCache.set(var7, var53);
         return var53;
      }
   }

   private double headwater(long var1, long var3) {
      long var5 = mix(this.seed ^ mix(var1 * 7146057691288625177L) ^ Long.rotateLeft(mix(var3 * -7046029254386353131L), 29) ^ 37697L);
      double var7 = unit(mix(var5 + 1L));
      return var7 > 0.88 ? 430.0 + 145.0 * unit(mix(var5 + 2L)) : 190.0 + 155.0 * unit(mix(var5 + 3L));
   }

   public int woodland(int var1, int var2) {
      double var3 = this.noise((double)var1 / 670.0, (double)var2 / 670.0, 1201L);
      return var3 < -0.22 ? 0 : (var3 < 0.12 ? 1 : (var3 < 0.34 ? 2 : 3));
   }

   public boolean cave(int var1, int var2, int var3) {
      if (var2 >= -51 && var2 <= 54) {
         double var4 = this.noise((double)var1 / 63.0 + (double)var2 * 0.018, (double)var3 / 63.0, 809L);
         double var6 = -4.0 + 32.0 * this.noise((double)var1 / 137.0, (double)var3 / 137.0, 811L);
         return Math.abs(var4) < 0.046 && Math.abs((double)var2 - var6) < 3.2 + 2.0 * this.noise((double)var1 / 41.0, (double)var3 / 41.0, 813L);
      } else {
         return false;
      }
   }

   public double slope(int var1, int var2) {
      if (this.version >= 2) {
         double var3 = this.sample((double)var1, (double)var2).ground;
         return Math.max(
               Math.max(
                  Math.abs(this.sample((double)(var1 + 4), (double)var2).ground - var3), Math.abs(this.sample((double)(var1 - 4), (double)var2).ground - var3)
               ),
               Math.max(
                  Math.abs(this.sample((double)var1, (double)(var2 + 4)).ground - var3), Math.abs(this.sample((double)var1, (double)(var2 - 4)).ground - var3)
               )
            )
            / 4.0;
      } else {
         return Math.max(
               Math.abs(this.sample((double)(var1 + 4), (double)var2).ground - this.sample((double)(var1 - 4), (double)var2).ground),
               Math.abs(this.sample((double)var1, (double)(var2 + 4)).ground - this.sample((double)var1, (double)(var2 - 4)).ground)
            )
            / 8.0;
      }
   }

   public long hash(int var1, int var2, long var3) {
      return mix(this.seed ^ (long)var1 * 7146057691288625177L ^ (long)var2 * -7046029254386353131L ^ var3);
   }

   public double variation(int var1, int var2, long var3) {
      return unit(this.hash(var1, var2, var3));
   }

   public double noise(double var1, double var3, long var5) {
      long var7 = (long)Math.floor(var1);
      long var9 = (long)Math.floor(var3);
      double var11 = var1 - (double)var7;
      double var13 = var3 - (double)var9;
      double var15 = this.gradient(var7, var9, var11, var13, var5);
      double var17 = this.gradient(var7 + 1L, var9, var11 - 1.0, var13, var5);
      double var19 = this.gradient(var7, var9 + 1L, var11, var13 - 1.0, var5);
      double var21 = this.gradient(var7 + 1L, var9 + 1L, var11 - 1.0, var13 - 1.0, var5);
      return lerp(lerp(var15, var17, fade(var11)), lerp(var19, var21, fade(var11)), fade(var13)) * 1.42;
   }

   private double gradient(long var1, long var3, double var5, double var7, long var9) {
      return switch ((int)(mix(this.seed ^ var1 * 7146057691288625177L ^ var3 * -7046029254386353131L ^ var9) & 7L)) {
         case 0 -> var5;
         case 1 -> -var5;
         case 2 -> var7;
         case 3 -> -var7;
         case 4 -> (var5 + var7) * 0.70710678;
         case 5 -> (var5 - var7) * 0.70710678;
         case 6 -> (-var5 + var7) * 0.70710678;
         default -> (-var5 - var7) * 0.70710678;
      };
   }

   public double fbm(double var1, double var3, long var5, int var7) {
      double var8 = 0.0;
      double var10 = 1.0;
      double var12 = 0.0;

      for (int var14 = 0; var14 < var7; var14++) {
         var8 += this.noise(var1, var3, var5 + (long)(var14 * 163)) * var10;
         var12 += var10;
         var10 *= 0.48;
         var1 *= 2.03;
         var3 *= 2.03;
      }

      return var8 / var12;
   }

   public static long mix(long var0) {
      var0 = (var0 ^ var0 >>> 30) * -4658895280553007687L;
      var0 = (var0 ^ var0 >>> 27) * -7723592293110705685L;
      return var0 ^ var0 >>> 31;
   }

   private static double unit(long var0) {
      return (double)(var0 >>> 11) * 1.110223E-16F;
   }

   private static double fade(double var0) {
      return var0 * var0 * var0 * (var0 * (var0 * 6.0 - 15.0) + 10.0);
   }

   public static double smooth(double var0, double var2, double var4) {
      double var6 = Math.max(0.0, Math.min(1.0, (var4 - var0) / (var2 - var0)));
      return var6 * var6 * (3.0 - 2.0 * var6);
   }

   public static double lerp(double var0, double var2, double var4) {
      return var0 + (var2 - var0) * var4;
   }

   private static record CachedColumn(long key, AlpineLayout.Sample sample) {
   }

   private static record Drainage(
      long valley,
      long reach,
      double[] lips,
      double[] rises,
      double lake,
      double leftHead,
      double rightHead,
      double softness,
      double lakeWidth,
      double grade,
      double channelFactor,
      double poolFlare,
      int style,
      boolean legacy
   ) {
      double level(double var1, double var3, double var5, boolean var7) {
         double var8 = 105.0 + this.grade * var1;
         double var10 = var1 + var5;

         for (int var12 = 0; var12 < this.lips.length; var12++) {
            double var13 = this.lips[var12] - var3;
            double var15 = this.lips[var12] + var3;
            double var17 = AlpineLayout.smooth(var13, var15, var5);
            double var19 = AlpineLayout.smooth(var13, var15, 1.0 + var5);
            var8 += this.rises[var12] * (AlpineLayout.smooth(var13, var15, var10) - var17) / (var19 - var17);
         }

         return this.legacy ? var8 : AlpineLayout.lerp(this.lake, var7 ? this.rightHead : this.leftHead, (var8 - 105.0) / 331.0);
      }
   }

   public static record Sample(
      double ground,
      int water,
      double riverLevel,
      double distance,
      double width,
      double moisture,
      double forest,
      double snowLine,
      double fall,
      int district,
      int fallStyle,
      double rock,
      int site,
      long cavity
   ) {
      public Sample(double var1, int var3, double var4, double var6, double var8, double var10, double var12, double var14, double var16, int var18, int var19) {
         this(var1, var3, var4, var6, var8, var10, var12, var14, var16, var18, var19, 0.0, 0, 0L);
      }

      public Sample(
         double var1,
         int var3,
         double var4,
         double var6,
         double var8,
         double var10,
         double var12,
         double var14,
         double var16,
         int var18,
         int var19,
         double var20
      ) {
         this(var1, var3, var4, var6, var8, var10, var12, var14, var16, var18, var19, var20, 0, 0L);
      }

      public boolean scenic() {
         return this.site != 0;
      }

      public int floor() {
         return (int)Math.floor(this.ground);
      }

      public boolean wet() {
         return this.water > this.floor();
      }

      public int biome() {
         if (this.district >= 0) {
            return this.district;
         } else if (this.wet()) {
            return 0;
         } else if (this.ground > this.snowLine) {
            return 5;
         } else if (this.ground > 490.0) {
            return 4;
         } else if (this.forest > 0.48 && this.ground > 285.0) {
            return 3;
         } else {
            return this.forest > 0.48 ? 2 : 1;
         }
      }
   }
}
