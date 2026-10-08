package com.formaworks.frontierhunts.landscape;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class AlpineFalls {
   public static final int CELL = 384;
   public static final int MARGIN = 126;
   public static final int REACH = 118;
   static final boolean DEBUG = Boolean.getBoolean("frontier.fallsDebug");
   public static final int POOL = 1;
   public static final int CHANNEL = 2;
   public static final int FLOOR = 3;
   public static final int WALL = 4;
   public static final int LIP = 5;
   public static final int RIB = 6;
   public static final int BANK = 7;
   public static final int CAVE = 8;
   public static final int OUTFLOW = 9;
   public static final int SPRING = 10;
   public static final int TERRACE = 11;
   public static final int CHUTE = 12;
   public static final int RIM = 13;
   public static final int FRINGE = 14;
   static final int F_SPILL = 1;
   static final int F_BIOME = 2;
   static final int F_OVERHANG = 4;
   static final int F_ISLAND = 8;
   static final int F_PLUNGE = 16;
   static final short DRY = -32768;
   public static final int BIOME_GORGE = 21;
   public static final int BIOME_MISTY = 22;
   public static final int BIOME_GROTTO = 23;
   private final AlpineLayout layout;
   private final AlpineFalls.Natural natural;
   private final ConcurrentHashMap<Long, Optional<AlpineFalls.Site>> sites = new ConcurrentHashMap<>();
   private final ConcurrentLinkedQueue<Long> order = new ConcurrentLinkedQueue<>();
   private final boolean bounded;
   private AlpineFalls.Type forcedType;
   private AlpineFalls.Palette forcedPalette;
   private static final double ATTEMPT = 0.82;
   private static final int CANDIDATES = 22;

   public static int kind(int var0) {
      return var0 & 15;
   }

   public static int palette(int var0) {
      return var0 >> 4 & 7;
   }

   public static int wet(int var0) {
      return var0 >> 8 & 0xFF;
   }

   public static boolean spill(int var0) {
      return (var0 & 65536) != 0;
   }

   public static boolean island(int var0) {
      return (var0 & 131072) != 0;
   }

   public static boolean overhang(int var0) {
      return (var0 & 262144) != 0;
   }

   public static int type(int var0) {
      return var0 >> 19 & 15;
   }

   public static boolean hasCavity(long var0) {
      return (var0 >>> 48 & 1L) != 0L;
   }

   public static int cavityBottom(long var0) {
      return (int)(var0 & 65535L) - 2048;
   }

   public static int cavityTop(long var0) {
      return (int)(var0 >>> 16 & 65535L) - 2048;
   }

   public static int cavityWater(long var0) {
      int var2 = (int)(var0 >>> 32 & 65535L);
      return var2 == 65535 ? Integer.MIN_VALUE : var2 - 2048;
   }

   static long cavity(int var0, int var1, int var2) {
      return (long)var0 + 2048L | (long)var1 + 2048L << 16 | (var2 == Integer.MIN_VALUE ? 65535L : (long)var2 + 2048L) << 32 | 281474976710656L;
   }

   public AlpineFalls(AlpineLayout var1, AlpineFalls.Natural var2) {
      this(var1, var2, true);
   }

   public AlpineFalls(AlpineLayout var1, AlpineFalls.Natural var2, boolean var3) {
      this.layout = var1;
      this.natural = var2;
      this.bounded = var3;
   }

   public AlpineFalls.Site designAt(double var1, double var3, double var5, double var7, AlpineFalls.Type var9, AlpineFalls.Palette var10, long var11) {
      AlpineFalls.Candidate var13 = null;
      double var14 = Math.hypot(var5, var7);
      var5 /= var14;
      var7 /= var14;

      for (int var16 = 0; var16 < 25; var16++) {
         double var17 = Math.floor(var1 + (var16 == 0 ? 0.0 : (unit(mix(var11 + (long)var16 * 3L)) - 0.5) * 14.0)) + 0.5;
         double var19 = Math.floor(var3 + (var16 == 0 ? 0.0 : (unit(mix(var11 + (long)var16 * 3L + 1L)) - 0.5) * 14.0)) + 0.5;
         AlpineFalls.Candidate var21 = this.evaluateDirection(var17, var19, var5, var7, var11 + (long)var16 * 131L);
         if (var21 != null && (var13 == null || var21.score > var13.score)) {
            var13 = var21;
         }
      }

      if (var13 == null) {
         return null;
      } else {
         this.forcedType = var9;
         this.forcedPalette = var10;

         Object var30;
         try {
            return this.build((int)Math.floor(var13.x / 384.0), (int)Math.floor(var13.z / 384.0), var13, mix(var11 ^ 31249L));
         } catch (RuntimeException var25) {
            var30 = null;
         } finally {
            this.forcedType = null;
            this.forcedPalette = null;
         }

         return (AlpineFalls.Site)var30;
      }
   }

   public AlpineFalls.Site site(int var1, int var2) {
      long var3 = (long)var1 << 32 ^ (long)var2 & 4294967295L;
      Optional var5 = this.sites.get(var3);
      if (var5 != null) {
         return (AlpineFalls.Site)var5.orElse(null);
      } else {
         Optional var6 = this.sites.computeIfAbsent(var3, var3x -> {
            this.order.add(var3x);
            return Optional.ofNullable(this.plan(var1, var2));
         });

         while (this.sites.size() > 40) {
            Long var7 = this.order.poll();
            if (var7 == null) {
               break;
            }

            if (var7 != var3) {
               this.sites.remove(var7);
            }
         }

         return (AlpineFalls.Site)var6.orElse(null);
      }
   }

   public AlpineFalls.Site siteAt(int var1, int var2) {
      AlpineFalls.Site var3 = this.site(Math.floorDiv(var1, 384), Math.floorDiv(var2, 384));
      return var3 != null && var3.contains(var1, var2) ? var3 : null;
   }

   public AlpineLayout.Sample apply(double var1, double var3, AlpineLayout.Sample var5) {
      int var6 = (int)Math.floor(var1);
      int var7 = (int)Math.floor(var3);
      AlpineFalls.Site var8 = this.siteAt(var6, var7);
      return var8 == null ? var5 : this.applySite(var8, var6, var7, var5);
   }

   AlpineLayout.Sample applySite(AlpineFalls.Site var1, int var2, int var3, AlpineLayout.Sample var4) {
      int var5 = var1.index(var2, var3);
      byte var6 = var1.kind[var5];
      byte var7 = var1.flags[var5];
      if (var6 == 0 && (var7 & 2) == 0) {
         return var4;
      } else {
         double var8 = Float.isNaN(var1.ground[var5]) ? var4.ground() : (double)var1.ground[var5];
         int var10 = var1.water[var5] == -32768 ? var4.water() : var1.water[var5];
         int var11 = var1.wet[var5] & 255;
         double var12 = var4.forest();
         switch (var6) {
            case 1:
            case 2:
            case 4:
            case 5:
            case 8:
            case 9:
            case 10:
            case 11:
            case 12:
               var12 = 0.0;
               break;
            case 3:
               var12 = Math.min(var12, 0.08);
               break;
            case 6:
               var12 = (var7 & 8) != 0 ? 0.55 : 0.0;
               break;
            case 7:
            case 13:
               var12 = var11 > 90 ? Math.min(var12, 0.12) : Math.max(var12, 0.62);
               break;
            case 14:
               var12 = Math.max(var12, 0.7);
         }
         double var14 = switch (var6) {
            case 4, 5, 8 -> 1.0;
            case 6 -> (var7 & 8) != 0 ? 0.0 : 1.0;
            default -> 0.0;
            case 10 -> 0.6;
         };
         int var16 = (var7 & 2) != 0 ? var1.biome : var4.district();
         int var17 = var6 == 0
            ? 0
            : var6
               | var1.palette.ordinal() + 1 << 4
               | var11 << 8
               | ((var7 & 1) != 0 ? 65536 : 0)
               | ((var7 & 8) != 0 ? 131072 : 0)
               | ((var7 & 4) != 0 ? 262144 : 0)
               | var1.type.ordinal() + 1 << 19;
         long var18 = var1.cavT[var5] > var1.cavB[var5]
            ? cavity(var1.cavB[var5], var1.cavT[var5], var1.cavW[var5] == -32768 ? Integer.MIN_VALUE : var1.cavW[var5])
            : 0L;
         return new AlpineLayout.Sample(
            var8, var10, var8, var4.distance(), var4.width(), Math.max(var4.moisture(), 0.74), var12, var4.snowLine(), 0.0, var16, 0, var14, var17, var18
         );
      }
   }

   public AlpineLayout.Sample edit(AlpineFalls.Site var1, int var2, int var3, AlpineLayout.Sample var4) {
      if (!var1.contains(var2, var3)) {
         return null;
      } else {
         int var5 = var1.index(var2, var3);
         if (var1.kind[var5] != 0 && var1.kind[var5] != 14) {
            AlpineLayout.Sample var6 = this.applySite(var1, var2, var3, var4);
            return var6 == var4 ? null : var6;
         } else {
            return null;
         }
      }
   }

   public static boolean footprint(AlpineFalls.Site var0, int var1, int var2) {
      return var0.contains(var1, var2) && var0.kind[var0.index(var1, var2)] != 0;
   }

   private double terrain(double var1, double var3) {
      return this.natural.sample(var1, var3).ground();
   }

   private AlpineFalls.Site plan(int var1, int var2) {
      long var3 = this.layout.hash(var1, var2, 1554098974L);
      if (unit(var3) > 0.82) {
         return null;
      } else {
         ArrayList var5 = new ArrayList();

         for (int var6 = 0; var6 < 22; var6++) {
            double var7 = (double)var1 * 384.0 + 126.0 + unit(mix(var3 + 11L + (long)var6 * 2L)) * 132.0;
            double var9 = (double)var2 * 384.0 + 126.0 + unit(mix(var3 + 12L + (long)var6 * 2L)) * 132.0;
            AlpineFalls.Candidate var11 = this.evaluate(Math.floor(var7) + 0.5, Math.floor(var9) + 0.5, var3 + (long)var6 * 131L);
            if (var11 != null) {
               var5.add(var11);
            }
         }

         var5.sort((var0, var1x) -> Double.compare(var1x.score, var0.score));

         for (int var13 = 0; var13 < Math.min(this.layout.version() >= 14 ? 4 : 1, var5.size()); var13++) {
            try {
               return this.build(var1, var2, (AlpineFalls.Candidate)var5.get(var13), mix(var3 ^ 31249L ^ (long)var13));
            } catch (RuntimeException var12) {
            }
         }

         return null;
      }
   }

   private AlpineFalls.Candidate evaluateDirection(double var1, double var3, double var5, double var7, long var9) {
      return this.evaluate(var1, var3, var9, Math.atan2(var7, var5), Math.toRadians(40.0));
   }

   private AlpineFalls.Candidate evaluate(double var1, double var3, long var5) {
      return this.evaluate(var1, var3, var5, 0.0, 4.141592653589793);
   }

   private AlpineFalls.Candidate evaluate(double var1, double var3, long var5, double var7, double var9) {
      AlpineLayout.Sample var11 = this.natural.sample(var1, var3);
      if (var11.district() < 18 && var11.district() != 5 && var11.water() <= var11.floor()) {
         double var12 = var11.ground();
         if (!(var12 < (double)(this.bounded ? 73 : -60)) && !(var12 > var11.snowLine() - 10.0)) {
            AlpineFalls.Candidate var14 = null;

            for (int var15 = 0; var15 < 16; var15++) {
               double var16 = (double)var15 * Math.PI / 8.0 + 0.3 * (unit(mix(var5 + (long)var15)) - 0.5);
               if (!(Math.abs(Math.IEEEremainder(var16 - var7, Math.PI * 2)) > var9)) {
                  double var18 = Math.cos(var16);
                  double var20 = Math.sin(var16);
                  double var22 = -var20;
                  double var26 = -1.0E9;

                  for (byte var28 = 10; var28 <= 42; var28 += 8) {
                     var26 = Math.max(var26, this.terrain(var1 + var18 * (double)var28, var3 + var20 * (double)var28) - var12);
                  }

                  if (!(var26 > 3.5)) {
                     double var43 = Math.min(
                           Math.min(this.terrain(var1 + var22 * 11.0, var3 + var18 * 11.0), this.terrain(var1 - var22 * 11.0, var3 - var18 * 11.0)),
                           Math.min(
                              this.terrain(var1 + var22 * 17.0 - var18 * 4.0, var3 + var18 * 17.0 - var20 * 4.0),
                              this.terrain(var1 - var22 * 17.0 - var18 * 4.0, var3 - var18 * 17.0 - var20 * 4.0)
                           )
                        )
                        - var12;
                     if (!(var43 < -4.5)) {
                        double var30 = -1.0E9;
                        double var32 = 0.0;
                        byte var34 = 0;
                        double[] var35 = new double[13];
                        byte var36 = 8;

                        for (int var37 = 0; var36 <= 56; var37++) {
                           var35[var37] = this.terrain(var1 - var18 * (double)var36, var3 - var20 * (double)var36) - var12;
                           var36 += 4;
                        }

                        var36 = 8;

                        for (int var46 = 0; var36 <= 40; var46++) {
                           double var38 = var35[var46];
                           double var40 = var38 - 0.3 * (double)var36;
                           if (var40 > var30) {
                              var30 = var40;
                              var32 = var38;
                              var34 = var36;
                           }

                           var36 += 4;
                        }

                        if (!(var32 < 7.0)) {
                           var36 = 0;
                           byte var47 = 8;

                           for (int var49 = 0; var47 <= 44; var49++) {
                              if (!(var35[var49] < 7.0)) {
                                 double var39 = (var35[var49 + 3] - var35[var49]) / 12.0;
                                 if (var39 < 0.75) {
                                    var36 = var47;
                                    break;
                                 }
                              }

                              var47 += 4;
                           }

                           double var48 = var36 > 0 ? var35[(var36 - 8) / 4] : var32;
                           double var50 = this.terrain(var1 - var18 * (double)(var34 + 18), var3 - var20 * (double)(var34 + 18)) - var12;
                           if (!(var50 < var32 - 5.0)) {
                              double var41 = Math.max(var32, var48) * (1.0 + 0.01 * Math.max(var32, var48))
                                 + Math.min(0.0, var43) * 2.2
                                 - Math.max(0.0, var26) * 3.0
                                 + var11.moisture() * 5.0
                                 + var11.forest() * 3.0
                                 + Math.min(6.0, var50 - var32) * 0.5
                                 + unit(mix(var5 + 40L + (long)var15)) * 2.5
                                 + (double)(var36 > 0 ? 7 : 0);
                              if (var36 > 0) {
                                 var32 = var48;
                              }

                              if (var14 == null || var41 > var14.score) {
                                 var14 = new AlpineFalls.Candidate(
                                    var1, var3, var18, var20, var32, var36 > 0 ? var36 : var34, var36, var43, var26, var50, var41, var11
                                 );
                              }
                           }
                        }
                     }
                  }
               }
            }

            return var14;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private AlpineFalls.Site build(int var1, int var2, AlpineFalls.Candidate var3, long var4) {
      Random var6 = new Random(var4);
      int var7 = (int)Math.floor(var3.at.ground()) - 1;
      double var8 = var3.rise;
      double var10 = var8 / (double)var3.reach;
      double var13 = var6.nextDouble();
      int var15 = (int)Math.round(var8);
      AlpineFalls.Type var12;
      if (var15 >= 36 && var10 > 0.75) {
         var12 = pick(
            var13,
            new AlpineFalls.Type[]{
               AlpineFalls.Type.RIBBON, AlpineFalls.Type.PLUNGE, AlpineFalls.Type.BASALT, AlpineFalls.Type.GROTTO, AlpineFalls.Type.CURTAIN
            },
            new double[]{0.22, 0.34, 0.16, 0.12, 0.16}
         );
      } else if (var15 >= 19) {
         var12 = pick(
            var13,
            new AlpineFalls.Type[]{
               AlpineFalls.Type.PLUNGE,
               AlpineFalls.Type.CURTAIN,
               AlpineFalls.Type.TWIN,
               AlpineFalls.Type.PUNCHBOWL,
               AlpineFalls.Type.GROTTO,
               AlpineFalls.Type.BASALT,
               AlpineFalls.Type.TIERED
            },
            new double[]{0.22, 0.18, 0.14, 0.13, 0.14, 0.08, 0.11}
         );
      } else if (var15 >= 10) {
         var12 = pick(
            var13,
            new AlpineFalls.Type[]{
               AlpineFalls.Type.TIERED,
               AlpineFalls.Type.CURTAIN,
               AlpineFalls.Type.SLIDE,
               AlpineFalls.Type.TWIN,
               AlpineFalls.Type.PUNCHBOWL,
               AlpineFalls.Type.PLUNGE
            },
            new double[]{0.34, 0.18, 0.18, 0.1, 0.12, 0.08}
         );
      } else {
         var12 = pick(var13, new AlpineFalls.Type[]{AlpineFalls.Type.TIERED, AlpineFalls.Type.SLIDE, AlpineFalls.Type.PUNCHBOWL}, new double[]{0.5, 0.35, 0.15});
      }

      if (var12 == AlpineFalls.Type.TIERED && var10 > 1.3) {
         var12 = var15 >= 19 ? AlpineFalls.Type.CURTAIN : AlpineFalls.Type.SLIDE;
      }

      if (this.forcedType == null && var15 < 10 && var6.nextDouble() < 0.45) {
         throw new IllegalStateException("minor site skipped");
      } else {
         if (this.forcedType != null) {
            var12 = this.forcedType;
         }
         AlpineFalls.Palette var16 = switch (var12) {
            case PUNCHBOWL, GROTTO -> var6.nextDouble() < 0.75 ? AlpineFalls.Palette.MOSSY : AlpineFalls.Palette.GRANITE;
            case BASALT -> AlpineFalls.Palette.BASALT;
            case TIERED -> var6.nextDouble() < 0.8 ? AlpineFalls.Palette.TRAVERTINE : AlpineFalls.Palette.MOSSY;
            default -> var3.at.moisture() > 0.58 && var3.at.ground() < var3.at.snowLine() - 80.0
            ? (var6.nextDouble() < 0.55 ? AlpineFalls.Palette.MOSSY : AlpineFalls.Palette.GRANITE)
            : (var6.nextDouble() < 0.18 ? AlpineFalls.Palette.TRAVERTINE : AlpineFalls.Palette.GRANITE);
         };
         if (this.forcedPalette != null) {
            var16 = this.forcedPalette;
         }
         int var17 = switch (var16) {
            case MOSSY -> var12 != AlpineFalls.Type.GROTTO && var12 != AlpineFalls.Type.PUNCHBOWL ? (var6.nextBoolean() ? 23 : 22) : 23;
            case TRAVERTINE -> 21;
            default -> 22;
         };

         return switch (var12) {
            case TIERED -> this.tiered(var1, var2, var3, var12, var16, var17, var7, var6);
            case SLIDE -> this.slide(var1, var2, var3, var12, var16, var17, var7, var6);
            default -> this.bowl(var1, var2, var3, var12, var16, var17, var7, var6);
         };
      }
   }

   private static AlpineFalls.Type pick(double var0, AlpineFalls.Type[] var2, double[] var3) {
      double var4 = 0.0;

      for (double var9 : var3) {
         var4 += var9;
      }

      double var11 = var0 * var4;

      for (int var12 = 0; var12 < var2.length; var12++) {
         var11 -= var3[var12];
         if (var11 <= 0.0) {
            return var2[var12];
         }
      }

      return var2[var2.length - 1];
   }

   private AlpineFalls.Site allocate(
      int var1,
      int var2,
      AlpineFalls.Candidate var3,
      AlpineFalls.Type var4,
      AlpineFalls.Palette var5,
      int var6,
      int var7,
      int var8,
      int var9,
      double var10,
      double var12,
      double var14,
      double var16
   ) {
      double var18 = var3.x;
      double var20 = var3.z;
      double var22 = var3.dirX;
      double var24 = var3.dirZ;
      double var26 = -var24;
      double var28 = var22;
      double var30 = var18 + var22 * var10;
      double var32 = var20 + var24 * var10;
      double var34 = -Math.min(118.0, -var10 + var12 + 14.0);
      double var36 = Math.min(118.0, var14 + 14.0);
      double[] var38 = new double[]{var34, var36};
      double[] var39 = new double[]{-var16, var16};
      double var40 = 1.0E9;
      double var42 = -1.0E9;
      double var44 = 1.0E9;
      double var46 = -1.0E9;

      for (double var51 : var38) {
         for (double var56 : var39) {
            double var58 = var18 + var22 * var51 + var26 * var56;
            double var60 = var20 + var24 * var51 + var28 * var56;
            var40 = Math.min(var40, var58);
            var42 = Math.max(var42, var58);
            var44 = Math.min(var44, var60);
            var46 = Math.max(var46, var60);
         }
      }

      double var62 = this.bounded ? (double)var1 * 384.0 + 2.0 : -3.0E7;
      double var63 = this.bounded ? (double)var2 * 384.0 + 2.0 : -3.0E7;
      double var52 = this.bounded ? (double)(var1 + 1) * 384.0 - 3.0 : 3.0E7;
      double var64 = this.bounded ? (double)(var2 + 1) * 384.0 - 3.0 : 3.0E7;
      int var65 = (int)Math.floor(Math.max(var62, var40 - 26.0));
      int var57 = (int)Math.floor(Math.max(var63, var44 - 26.0));
      int var66 = (int)Math.ceil(Math.min(var52, var42 + 26.0));
      int var59 = (int)Math.ceil(Math.min(var64, var46 + 26.0));
      return new AlpineFalls.Site(
         var1,
         var2,
         var4,
         var5,
         var6,
         var18,
         var20,
         var22,
         var24,
         var7,
         var8,
         var9,
         (int)Math.floor(var30),
         (int)Math.floor(var32),
         var65,
         var57,
         var66 - var65,
         var59 - var57
      );
   }

   private AlpineFalls.Site bowl(
      int var1, int var2, AlpineFalls.Candidate var3, AlpineFalls.Type var4, AlpineFalls.Palette var5, int var6, int var7, Random var8
   ) {
      double var9 = var3.dirX;
      double var11 = var3.dirZ;
      double var13 = -var11;
      double var15 = var9;

      int var17 = switch (var4) {
         case PLUNGE, BASALT -> 3 + var8.nextInt(5);
         case RIBBON -> 1 + var8.nextInt(2);
         default -> 10 + var8.nextInt(11);
         case TWIN -> 9 + var8.nextInt(6);
         case PUNCHBOWL -> 2 + var8.nextInt(2);
         case GROTTO -> 2 + var8.nextInt(3);
      };

      double var18 = switch (var4) {
         case RIBBON -> 4.5 + var8.nextDouble() * 2.5;
         case CURTAIN, TWIN -> Math.max(9.0, (double)var17 * 0.62 + 3.0 + var8.nextDouble() * 3.0);
         case PUNCHBOWL -> 7.0 + var8.nextDouble() * 4.0;
         case GROTTO -> 7.5 + var8.nextDouble() * 3.5;
         default -> 7.0 + var8.nextDouble() * 5.0 + (double)var17 * 0.35;
      };
      var18 = Math.clamp((double)var3.reach / 1.18, var18 * 0.8, Math.max(var18, 24.0));
      double var20 = var18 * 0.18;
      double var22 = -(var18 + var20);
      double var24 = 1.0E9;

      for (double var26 = -((double)var17 / 2.0 + 2.5); var26 <= (double)var17 / 2.0 + 2.5; var26++) {
         for (double var28 = 0.0; var28 <= 3.0; var28++) {
            var24 = Math.min(var24, this.terrain(var3.x + var9 * (var22 - var28) + var13 * var26, var3.z + var11 * (var22 - var28) + var15 * var26));
         }
      }

      int var110 = (int)Math.floor(var24) - 1 - (var4 != AlpineFalls.Type.PUNCHBOWL && var4 != AlpineFalls.Type.GROTTO ? var8.nextInt(2) : 0);
      int var27 = var110 - var7;
      if (var27 < 6) {
         throw new IllegalStateException("insufficient drop");
      } else {
         if (var4 == AlpineFalls.Type.RIBBON && var27 < 22) {
            var4 = AlpineFalls.Type.PLUNGE;
         }
         boolean var111 = switch (var4) {
            case PLUNGE, BASALT -> var27 >= 14 && var8.nextDouble() < 0.55;
            default -> false;
            case CURTAIN, TWIN -> var27 >= 12 && var8.nextDouble() < 0.35;
            case GROTTO -> true;
         };
         int var29 = var111
            ? (var4 == AlpineFalls.Type.BASALT ? 2 : (var8.nextDouble() < 0.5 ? 1 : 2))
            : (var4 == AlpineFalls.Type.PLUNGE && var8.nextDouble() < 0.3 ? 1 : 0);
         if (var27 < 10) {
            var29 = 0;
         }

         double var30 = Math.min(104.0 + var22, 22.0 + var8.nextDouble() * 38.0 + (double)var17 * 1.4);
         double var32 = Math.min(104.0, 34.0 + var8.nextDouble() * 46.0);
         AlpineFalls.Site var34 = this.allocate(
            var1, var2, var3, var4, var5, var6, var7, var110, var17, var22, var30, var32, Math.max(var18 + 30.0, (double)var17 / 2.0 + 30.0)
         );
         AlpineFalls.Work var35 = new AlpineFalls.Work(var34, var8.nextLong());
         double var36 = 0.85 + var8.nextDouble() * 0.4;

         double var38 = switch (var4) {
            case PLUNGE -> 9.0;
            case RIBBON, BASALT -> 14.0;
            default -> 6.5;
            case PUNCHBOWL, GROTTO -> 40.0;
         };
         double var40 = var4 != AlpineFalls.Type.PUNCHBOWL && var4 != AlpineFalls.Type.GROTTO ? 2.5 + var8.nextDouble() * 3.0 : 1.2;
         double var42 = Math.min(9.0, 4.0 + (double)var27 * 0.12) + (double)(var4 == AlpineFalls.Type.PUNCHBOWL ? 2 : 0);
         int var44 = 2 + var8.nextInt(2);
         double var45 = var111 ? 3.0 + var8.nextDouble() * (double)(var4 == AlpineFalls.Type.GROTTO ? 5 : 4) : 0.0;
         long var47 = var8.nextLong();

         int var49 = switch (var4) {
            case CURTAIN -> var8.nextInt(4);
            case TWIN -> 1;
            default -> 0;
         };
         double[] var50 = new double[var49];
         double[] var51 = new double[var49];

         for (int var52 = 0; var52 < var49; var52++) {
            var50[var52] = var4 == AlpineFalls.Type.TWIN ? (var8.nextDouble() - 0.5) * (double)var17 * 0.2 : (var8.nextDouble() - 0.5) * (double)(var17 - 4);
            var51[var52] = var4 == AlpineFalls.Type.TWIN ? 1.2 + var8.nextDouble() * 1.6 : 0.6 + var8.nextDouble() * 1.2;
         }

         double var112 = var22 + (double)var29 + 0.5;
         double var54 = var4 != AlpineFalls.Type.PUNCHBOWL && var4 != AlpineFalls.Type.GROTTO ? var8.nextDouble() * 14.0 : 6.0 + var8.nextDouble() * 10.0;
         double var56 = (double)var17 / 2.0 + 2.6;
         double var58 = 0.0;

         for (int var60 = 0; var60 < var34.h; var60++) {
            for (int var61 = 0; var61 < var34.w; var61++) {
               int var62 = var34.x0 + var61;
               int var63 = var34.z0 + var60;
               int var64 = var60 * var34.w + var61;
               double var65 = var35.u(var62, var63);
               double var67 = var35.v(var62, var63);
               double var69 = var65 + var20;
               double var71 = Math.sqrt(var69 * var69 * var36 * var36 + var67 * var67);
               if (!(var71 > var18 + 46.0)) {
                  double var73 = Math.atan2(var67, -var69);
                  double var75 = Math.cos(var73);
                  double var77 = Math.sin(var73);
                  double var79 = this.layout.noise(var75 * 1.4 + (double)(var47 % 97L), var77 * 1.4, 9001L) * 0.16
                     + this.layout.noise(var75 * 3.3, var77 * 3.3 + (double)(var47 % 89L), 9003L) * 0.08;
                  double var81 = AlpineLayout.smooth(0.22, 0.62, Math.abs(var73));
                  double var83 = var18 * (1.0 + var79 * var81);
                  double var85 = var71 - var83;
                  double var87 = 1.0 - AlpineLayout.smooth(var56, var56 + 5.0, Math.abs(var67));
                  if (var69 < 0.0) {
                     var85 = AlpineLayout.lerp(var85, -var69 - var18, var87);
                  }

                  if (var4 == AlpineFalls.Type.BASALT && var85 > -0.5) {
                     int var89 = hex(var62, var63);
                     var85 -= (double)(var89 % 3) * 0.45;
                  }

                  double var125 = var40
                     * AlpineLayout.smooth(0.85, 1.9, Math.abs(var73))
                     * (0.7 + 0.3 * this.layout.noise((double)var62 / 9.0, (double)var63 / 9.0, 9005L));
                  if (var4 == AlpineFalls.Type.PUNCHBOWL || var4 == AlpineFalls.Type.GROTTO) {
                     var125 = Math.min(var125, 0.9);
                  }

                  double var91 = var35.nat(var64);
                  boolean var96 = Math.abs(var67) < var56 && var69 < 0.0 && var85 >= 0.0;
                  double var93;
                  int var95;
                  if (var85 < 0.0) {
                     if (var85 < -var125) {
                        double var127 = Math.hypot(var65 - var112, var67 * 0.8);
                        double var131 = var42 * (1.0 - AlpineLayout.smooth(1.5, var18 * 1.15, var127));
                        double var135 = AlpineLayout.smooth(0.0, 2.2, -var85 - var125);
                        double var138 = Math.max(1.0, (1.2 + var131) * var135 + 0.6 * this.layout.noise((double)var62 / 5.0, (double)var63 / 5.0, 9007L));
                        var93 = (double)((long)var7 - Math.round(var138));
                        var95 = 1;
                        var34.water[var64] = (short)var7;
                     } else {
                        double var128 = -var85 / Math.max(0.01, var125);
                        var93 = (double)(var7 + 1) + (1.0 - var128) * 1.4 + 0.5 * this.layout.noise((double)var62 / 6.0, (double)var63 / 6.0, 9009L);
                        var95 = 3;
                     }
                  } else if (var96) {
                     var93 = var91;
                     var95 = 0;
                  } else if (this.layout.version() < 14) {
                     double var126 = var4 != AlpineFalls.Type.PUNCHBOWL && var4 != AlpineFalls.Type.GROTTO ? this.benches(var85, var38, var62, var63) : 0.0;
                     double var130 = var4 != AlpineFalls.Type.PUNCHBOWL && var4 != AlpineFalls.Type.GROTTO
                        ? AlpineLayout.lerp(0.45, 1.0, AlpineLayout.smooth(-0.55, 0.35, Math.cos(var73)))
                        : 1.0;
                     var93 = (double)(var7 + 1) + (var85 * var38 + var126) * var130;
                     var95 = 4;
                     double var134 = AlpineLayout.smooth(0.55, 1.55, Math.abs(var73));
                     double var137 = (double)(var110 + 2) + var54 * var134 + 2.0 * this.layout.noise((double)var62 / 11.0, (double)var63 / 11.0, 9013L);
                     if (var93 > var137) {
                        double var139 = Math.max(0.0, (var137 - (double)var7 - 1.0) / Math.max(0.5, var38 * var130));
                        double var141 = AlpineLayout.lerp(
                           0.9 + 0.5 * this.layout.noise((double)var62 / 19.0, (double)var63 / 19.0, 9015L), var38 * var130, var134 * var134
                        );
                        var93 = Math.min(var93, var137 + Math.max(0.0, var85 - var139) * var141);
                        if (var93 > var137 + 0.5) {
                           var95 = 7;
                        }
                     }

                     double var140 = AlpineLayout.smooth(var18 + 24.0, var18 + 46.0, var71);
                     if (var140 > 0.0 && var93 < var91) {
                        var93 = AlpineLayout.lerp(var93, var91, var140);
                     }

                     if (var93 >= var91 - 0.02) {
                        var93 = var91;
                        var95 = var85 < 3.0 && var91 >= (double)(var7 - 1) ? 4 : 0;
                     }
                  } else {
                     double var97 = this.layout.noise(var75 * 2.3 + (double)(var47 % 83L), var77 * 2.3, 9017L) * 1.7
                        + this.layout.noise((double)var62 / 4.3, (double)var63 / 4.3, 9019L) * 0.75
                        + this.layout.noise((double)var62 / 1.9, (double)var63 / 1.9, 9027L) * 0.35;
                     double var99 = Math.max(0.0, var85 + var97 * 0.6);
                     double var101 = var4 != AlpineFalls.Type.PUNCHBOWL && var4 != AlpineFalls.Type.GROTTO ? this.benches(var99, var38, var62, var63) : 0.0;
                     double var103 = var4 != AlpineFalls.Type.PUNCHBOWL && var4 != AlpineFalls.Type.GROTTO
                        ? AlpineLayout.lerp(0.45, 1.0, AlpineLayout.smooth(-0.55, 0.35, Math.cos(var73)))
                        : 1.0;
                     double var105 = var38 * var103 * (0.72 + 0.56 * (0.5 + 0.5 * this.layout.noise((double)var62 / 13.0, (double)var63 / 13.0, 9025L)));
                     var93 = (double)(var7 + 1) + var99 * var105 + var101 * var103;
                     var95 = 4;
                     double var107 = AlpineLayout.smooth(var18 + 24.0, var18 + 46.0, var71);
                     if (var107 > 0.0 && var93 < var91) {
                        var93 = AlpineLayout.lerp(var93, var91, var107);
                     }

                     if (!(var93 >= var91 - 0.02)) {
                        if (var93 < var91 - 0.5) {
                           var58 = Math.max(var58, var91 - var93);
                        }
                     } else {
                        var93 = var91;
                        var95 = var85 < 3.0 && var91 >= (double)(var7 - 1) ? 4 : 0;
                     }
                  }

                  if (var95 != 0 || var96) {
                     if ((var95 == 1 || var95 == 3) && var91 < (double)var7 - 1.5) {
                        var95 = 7;
                        var93 = var91;
                        var34.water[var64] = -32768;
                     }

                     if (var95 != 0) {
                        var34.ground[var64] = (float)(var95 == 1 ? var93 : Math.min(var91, var93));
                        var34.kind[var64] = (byte)var95;
                     }

                     if (Math.abs(var67) < (double)var17 / 2.0 + (var111 ? 1.8 : 0.0) && var69 < 0.0) {
                        double var129 = -var69 - var18;
                        if (var129 < 0.0 && var129 >= (double)(-var29) && Math.abs(var67) < (double)var17 / 2.0 + 0.2) {
                           int var133 = var34.water[var64] != -32768 ? (int)Math.floor((double)var34.ground[var64]) : var7 - 2;
                           int var100 = var110 - 1 - var44 + (var4 == AlpineFalls.Type.BASALT ? -(hex(var62, var63) % 3) : 0);
                           var34.cavB[var64] = (short)var133;
                           var34.cavT[var64] = (short)var100;
                           var34.cavW[var64] = (short)var7;
                           var34.ground[var64] = (float)(var110 - 2);
                           var34.water[var64] = (short)var110;
                           var34.kind[var64] = 5;
                           var34.flags[var64] = (byte)(var34.flags[var64] | 4);
                        } else if (var111 && var129 >= 0.0 && var129 < var45) {
                           double var132 = var129 / var45;
                           int var136 = var110 - 1 - var44 - (int)Math.round(var132 * var132 * (double)Math.max(0, var110 - 1 - var44 - (var7 + 3)));
                           if (var4 == AlpineFalls.Type.BASALT) {
                              var136 -= hex(var62, var63) % 2;
                           }

                           int var102 = var7 + (this.layout.noise((double)var62 / 3.0, (double)var63 / 3.0, 9011L) > 0.35 ? 1 : 0);
                           if (var136 > var102 + 2) {
                              var34.cavB[var64] = (short)var102;
                              var34.cavT[var64] = (short)var136;
                              var34.cavW[var64] = -32768;
                              if (var34.kind[var64] == 0) {
                                 var34.kind[var64] = 8;
                              }
                           }
                        }
                     }
                  }
               }
            }
         }

         if (this.layout.version() >= 14 && var58 > Math.max(9.0, Math.min(20.0, (double)var27 * 0.42))) {
            throw new IllegalStateException("cirque would scar the slope");
         } else {
            AlpineFalls.Path var113 = new AlpineFalls.Path();
            this.trace(
               var35,
               var113,
               var34.x0,
               var34.z0,
               var3.x + var9 * var22,
               var3.z + var11 * var22,
               -var9,
               -var11,
               var110,
               (double)var17,
               Math.max(2.0, Math.min(6.0, (double)var17 * 0.55)),
               var30,
               true,
               var8
            );
            this.carveChannel(var35, var113, 2, true, var110);

            for (int var114 = 0; var114 < var49; var114++) {
               double var116 = 3.0 + var8.nextDouble() * 6.0;

               for (int var118 = 0; var118 < var34.h; var118++) {
                  for (int var119 = 0; var119 < var34.w; var119++) {
                     int var66 = var34.x0 + var119;
                     int var120 = var34.z0 + var118;
                     int var68 = var118 * var34.w + var119;
                     double var121 = var35.u(var66, var120);
                     double var122 = var35.v(var66, var120);
                     double var123 = -(var121 + var20) - var18;
                     if (!(var123 < (double)(-var29) - 0.5) && !(var123 > var116)) {
                        double var124 = var51[var114] * (1.0 - 0.5 * AlpineLayout.smooth(var116 * 0.5, var116, var123));
                        if (!(Math.abs(var122 - var50[var114]) > var124)) {
                           var34.ground[var68] = Math.max(
                              Float.isNaN(var34.ground[var68]) ? 0.0F : var34.ground[var68],
                              (float)(var110 + 1 + (var4 == AlpineFalls.Type.TWIN ? var8.nextInt(3) : 0))
                           );
                           var34.water[var68] = -32768;
                           var34.kind[var68] = 6;
                           var34.cavT[var68] = var34.cavB[var68] = 0;
                           var34.cavW[var68] = -32768;
                           if (var4 == AlpineFalls.Type.TWIN && var123 > 1.5) {
                              var34.flags[var68] = (byte)(var34.flags[var68] | 8);
                           }
                        }
                     }
                  }
               }
            }

            AlpineFalls.Path var115 = new AlpineFalls.Path();
            double var117 = -var20 + var18 * 0.35;
            this.trace(
               var35,
               var115,
               var34.x0,
               var34.z0,
               var3.x + var9 * var117,
               var3.z + var11 * var117,
               var9,
               var11,
               var7,
               var4 == AlpineFalls.Type.PUNCHBOWL ? 2.0 : Math.max(2.5, Math.min(6.0, (double)var17 * 0.6)),
               Math.max(2.5, Math.min(5.5, (double)var17 * 0.5)),
               var32,
               false,
               var8
            );
            this.carveChannel(var35, var115, 9, false, var7);
            this.finish(var35, var8, var3.x + var9 * var112, var3.z + var11 * var112, var27);
            return var34;
         }
      }
   }

   private static int hex(int var0, int var1) {
      double var2 = ((double)var0 * 0.57735 - (double)var1 / 3.0) / 1.1;
      double var4 = (double)(var1 * 2) / 3.0 / 1.1;
      double var6 = -var2 - var4;
      long var8 = Math.round(var2);
      long var10 = Math.round(var4);
      long var12 = Math.round(var6);
      double var14 = Math.abs((double)var8 - var2);
      double var16 = Math.abs((double)var10 - var4);
      double var18 = Math.abs((double)var12 - var6);
      if (var14 > var16 && var14 > var18) {
         var8 = -var10 - var12;
      } else if (var16 > var18) {
         var10 = -var8 - var12;
      }

      return (int)(mix(var8 * -7046029254386353131L ^ var10 * -4417276706812531889L) >>> 40) & 0xFF;
   }

   private double benches(double var1, double var3, int var5, int var6) {
      double var7 = 6.5 + 2.0 * this.layout.noise((double)var5 / 23.0, (double)var6 / 23.0, 9021L);
      double var9 = 1.2 + 0.9 * this.layout.noise((double)var5 / 17.0, (double)var6 / 17.0, 9023L);
      double var11 = var7 / var3 + var9;
      double var13 = Math.floor(var1 / var11);
      double var15 = var1 - var13 * var11;
      double var17 = var13 * var7 + Math.min(var7, var15 * var3);
      return var17 - var1 * var3;
   }

   private AlpineFalls.Site tiered(
      int var1, int var2, AlpineFalls.Candidate var3, AlpineFalls.Type var4, AlpineFalls.Palette var5, int var6, int var7, Random var8
   ) {
      double var9 = var3.dirX;
      double var11 = var3.dirZ;
      double var13 = -var11;
      double var15 = var9;
      double var17 = var3.rise;
      int var19 = Math.max(2, Math.min(6, (int)Math.round(var17 / (3.2 + var8.nextDouble() * 2.0))));
      double var20 = (double)var3.reach * (1.05 + var8.nextDouble() * 0.35);
      int var22 = 14 + var8.nextInt(14);
      double var23 = 1.0E9;

      for (double var25 = -((double)var22 / 2.0 + 2.0); var25 <= (double)var22 / 2.0 + 2.0; var25++) {
         var23 = Math.min(var23, this.terrain(var3.x - var9 * var20 + var13 * var25, var3.z - var11 * var20 + var15 * var25));
      }

      int var68 = (int)Math.floor(var23) - 1;
      int var26 = var68 - var7;
      if (var26 < 5) {
         throw new IllegalStateException("flat");
      } else {
         int[] var27 = new int[var19 + 1];
         double[] var28 = new double[var19 + 1];
         var27[0] = var68;
         var28[0] = -var20;
         double[] var29 = new double[var19];
         double var30 = 0.0;

         for (int var32 = 0; var32 < var19; var32++) {
            var29[var32] = 0.6 + var8.nextDouble();
            var30 += var29[var32];
         }

         double var69 = 0.0;

         for (int var34 = 1; var34 <= var19; var34++) {
            var69 += var29[var34 - 1];
            var27[var34] = var34 == var19 ? var7 : var68 - (int)Math.round((double)var26 * var69 / var30);
            var28[var34] = -var20 + var20 * var69 / var30;
         }

         double var70 = Math.min(104.0 - var20, 18.0 + var8.nextDouble() * 30.0);
         double var36 = Math.min(104.0, 30.0 + var8.nextDouble() * 40.0);
         AlpineFalls.Site var38 = this.allocate(var1, var2, var3, var4, var5, var6, var7, var68, var22, -var20, var70, var36, (double)var22 / 2.0 + 30.0);
         AlpineFalls.Work var39 = new AlpineFalls.Work(var38, var8.nextLong());
         long var40 = var8.nextLong();
         double var42 = 7.0 + var8.nextDouble() * 4.0;

         for (int var44 = 0; var44 < var38.h; var44++) {
            for (int var45 = 0; var45 < var38.w; var45++) {
               int var46 = var38.x0 + var45;
               int var47 = var38.z0 + var44;
               int var48 = var44 * var38.w + var45;
               double var49 = var39.u(var46, var47);
               double var51 = var39.v(var46, var47);
               if (!(var49 < -var20 - 4.0) && !(var49 > var42 + 8.0)) {
                  double var53 = (double)var22 / 2.0 * (1.0 + 0.22 * this.layout.noise(var49 / 13.0 + (double)(var40 % 31L), 1.7, 9101L))
                     + (var49 > 0.0 ? var42 * 0.4 : 0.0);
                  if (!(Math.abs(var51) > var53 + 14.0)) {
                     int var55 = var19;

                     for (int var56 = 1; var56 <= var19; var56++) {
                        double var57 = var28[var56]
                           + 2.4 * this.layout.noise(var51 / 6.5, (double)var56 * 3.1 + (double)(var40 % 17L), 9103L)
                           + 1.1 * this.layout.noise(var51 / 2.3, (double)var56 * 1.7, 9105L);
                        if (var49 < var57) {
                           var55 = var56 - 1;
                           break;
                        }
                     }

                     int var73 = var55 == var19 ? var7 : var27[var55];
                     double var74 = var39.nat(var48);
                     double var59 = Math.abs(var51) - var53;
                     if (var55 == var19) {
                        double var61 = Math.hypot(var49 * 0.9, var51);
                        double var63 = var42 * (1.0 + 0.15 * this.layout.noise(Math.atan2(var51, var49) * 1.3, 5.2, 9107L));
                        var59 = Math.max(var59, var61 - var63);
                     }

                     if (var59 < 0.0) {
                        double var76 = var55 < var19
                           ? var28[var55 + 1] + 2.4 * this.layout.noise(var51 / 6.5, (double)(var55 + 1) * 3.1 + (double)(var40 % 17L), 9103L) - var49
                           : 99.0;
                        double var78 = this.layout.noise((double)var46 / 5.5, (double)var47 / 5.5, 9109L) + (var76 < 1.6 ? 0.28 : 0.0);
                        boolean var65 = var78 > 0.52 && var59 < -1.5 && var55 < var19;
                        if (var65) {
                           var38.ground[var48] = (float)(var73 + 1 + (var78 > 0.7 ? 1 : 0));
                           var38.kind[var48] = 6;
                           var38.flags[var48] = (byte)(var38.flags[var48] | 8);
                        } else {
                           double var66 = var55 == var19
                              ? 1.0 + Math.min(5.0, (double)var26 * 0.15) * (1.0 - AlpineLayout.smooth(0.0, var42, Math.hypot(var49, var51)))
                              : 1.0
                                 + 0.8 * AlpineLayout.smooth(0.5, 3.0, Math.min(var76, var49 - var28[var55]))
                                 + 0.4 * this.layout.noise((double)var46 / 4.0, (double)var47 / 4.0, 9111L);
                           var38.ground[var48] = (float)((long)var73 - Math.max(1L, Math.round(var66)));
                           var38.water[var48] = (short)var73;
                           var38.kind[var48] = (byte)(var55 == var19 ? 1 : 11);
                        }
                     } else {
                        double var75 = (double)(var73 + 1) + var59 * 0.75 + 0.6 * this.layout.noise((double)var46 / 4.0, (double)var47 / 4.0, 9113L);
                        double var77 = Math.max((double)(var73 + 1), Math.min(var74, var75));
                        if (var59 < 1.5) {
                           var77 = Math.max(var77, (double)(var73 + 1));
                        }

                        if (!(var77 >= var74 - 0.01) || !(var59 > 3.0)) {
                           var38.ground[var48] = (float)var77;
                           var38.kind[var48] = (byte)(var59 < 2.5 ? 13 : 7);
                        }
                     }
                  }
               }
            }
         }

         AlpineFalls.Path var71 = new AlpineFalls.Path();
         this.trace(
            var39,
            var71,
            var38.x0,
            var38.z0,
            var3.x - var9 * (var20 - 0.5),
            var3.z - var11 * (var20 - 0.5),
            -var9,
            -var11,
            var68,
            Math.max(3.0, (double)var22 * 0.35),
            Math.max(2.5, (double)var22 * 0.25),
            var70,
            true,
            var8
         );
         this.carveChannel(var39, var71, 2, true, var68);
         AlpineFalls.Path var72 = new AlpineFalls.Path();
         this.trace(
            var39,
            var72,
            var38.x0,
            var38.z0,
            var3.x,
            var3.z,
            var9,
            var11,
            var7,
            Math.max(3.0, (double)var22 * 0.3),
            Math.max(2.5, (double)var22 * 0.25),
            var36,
            false,
            var8
         );
         this.carveChannel(var39, var72, 9, false, var7);
         this.finish(var39, var8, var3.x, var3.z, var26);
         return var38;
      }
   }

   private AlpineFalls.Site slide(
      int var1, int var2, AlpineFalls.Candidate var3, AlpineFalls.Type var4, AlpineFalls.Palette var5, int var6, int var7, Random var8
   ) {
      double var9 = var3.dirX;
      double var11 = var3.dirZ;
      double var13 = -var11;
      double var15 = var9;
      int var17 = 4 + var8.nextInt(5);
      double var18 = 5.5 + var8.nextDouble() * 3.0;
      double var20 = Math.max(6.0, (double)var3.reach - var18 * 0.5);
      double var22 = 1.0E9;

      for (double var24 = -((double)var17 / 2.0 + 2.0); var24 <= (double)var17 / 2.0 + 2.0; var24++) {
         var22 = Math.min(var22, this.terrain(var3.x - var9 * (var20 + var18) + var13 * var24, var3.z - var11 * (var20 + var18) + var15 * var24));
      }

      int var58 = (int)Math.floor(var22) - 1;
      int var25 = var58 - var7;
      if (var25 < 5) {
         throw new IllegalStateException("flat");
      } else {
         double var26 = Math.min(104.0 - var20 - var18, 20.0 + var8.nextDouble() * 30.0);
         double var28 = Math.min(104.0, 30.0 + var8.nextDouble() * 40.0);
         AlpineFalls.Site var30 = this.allocate(
            var1, var2, var3, var4, var5, var6, var7, var58, var17, -(var20 + var18), var26, var28, (double)var17 / 2.0 + 28.0
         );
         AlpineFalls.Work var31 = new AlpineFalls.Work(var30, var8.nextLong());
         double var32 = Math.max(1.0, var20 / (double)Math.max(1, var25));

         for (int var34 = 0; var34 < var30.h; var34++) {
            for (int var35 = 0; var35 < var30.w; var35++) {
               int var36 = var30.x0 + var35;
               int var37 = var30.z0 + var34;
               int var38 = var34 * var30.w + var35;
               double var39 = var31.u(var36, var37);
               double var41 = var31.v(var36, var37);
               double var45 = Math.hypot(var39 * 0.95, var41) - var18 * (1.0 + 0.14 * this.layout.noise(Math.atan2(var41, var39), 3.3, 9201L));
               if (var45 < 0.0) {
                  double var43 = var31.nat(var38);
                  double var47 = 1.0 + Math.min(4.0, (double)var25 * 0.2) * (1.0 - AlpineLayout.smooth(0.0, var18, Math.hypot(var39 + var18 * 0.4, var41)));
                  var30.ground[var38] = (float)((long)var7 - Math.round(var47));
                  var30.water[var38] = (short)var7;
                  var30.kind[var38] = 1;
               } else {
                  double var63 = -var39 - var18 * 0.6;
                  if (!(var63 < -1.0) && !(var63 > var20 + 1.0) && !(Math.abs(var41) > (double)var17 / 2.0 + 10.0)) {
                     double var62 = var31.nat(var38);
                     double var49 = (double)var17 / 2.0 * (1.0 + 0.2 * this.layout.noise(var63 / 7.0, 4.4, 9203L));
                     double var51 = Math.abs(var41) - var49;
                     int var53 = (int)Math.floor(Math.max(0.0, var63) / var32);
                     double var54 = (double)(var7 + Math.min(var25 - 1, var53) + (var63 < 0.0 ? 0 : 0));
                     if (var51 < 0.0) {
                        var30.ground[var38] = (float)Math.min(var62, var54);
                        var30.kind[var38] = 12;
                     } else {
                        double var56 = Math.max(var54 + 2.0 + var51 * 1.4, Math.min(var62, var54 + 2.0 + var51 * 3.0));
                        if (var51 < 2.0) {
                           var56 = Math.max(var56, var54 + 2.0);
                        }

                        if (!(var56 >= var62) || !(var51 > 2.0)) {
                           var30.ground[var38] = (float)var56;
                           var30.kind[var38] = (byte)(var51 < 2.0 ? 4 : 7);
                        }
                     }
                  }
               }
            }
         }

         AlpineFalls.Path var59 = new AlpineFalls.Path();
         double var60 = -(var20 + var18 * 0.6);
         this.trace(
            var31,
            var59,
            var30.x0,
            var30.z0,
            var3.x + var9 * var60,
            var3.z + var11 * var60,
            -var9,
            -var11,
            var58,
            (double)var17,
            Math.max(2.5, (double)var17 * 0.55),
            var26,
            true,
            var8
         );
         this.carveChannel(var31, var59, 2, true, var58);
         AlpineFalls.Path var61 = new AlpineFalls.Path();
         this.trace(
            var31,
            var61,
            var30.x0,
            var30.z0,
            var3.x,
            var3.z,
            var9,
            var11,
            var7,
            Math.max(2.5, (double)var17 * 0.55),
            Math.max(2.5, (double)var17 * 0.5),
            var28,
            false,
            var8
         );
         this.carveChannel(var31, var61, 9, false, var7);
         this.finish(var31, var8, var3.x - var9 * var18 * 0.4, var3.z - var11 * var18 * 0.4, var25);
         return var30;
      }
   }

   private void trace(
      AlpineFalls.Work var1,
      AlpineFalls.Path var2,
      int var3,
      int var4,
      double var5,
      double var7,
      double var9,
      double var11,
      int var13,
      double var14,
      double var16,
      double var18,
      boolean var20,
      Random var21
   ) {
      AlpineFalls.Site var22 = var1.site;
      double var23 = Math.atan2(var11, var9);
      double var25 = var23;
      double var27 = var5;
      double var29 = var7;
      double var31 = 0.0;
      double var33 = -99.0;
      int var35 = var13;
      double var36 = 18.0 + var21.nextDouble() * 18.0;
      long var38 = var21.nextLong();
      double var40 = var20 ? 1.15 : 0.55;

      for (int var42 = 18 + var21.nextInt(22); var31 <= var18; var31++) {
         double var43 = AlpineLayout.lerp(var14, var16, AlpineLayout.smooth(0.0, 22.0, var31))
            * (1.0 + 0.12 * this.layout.noise(var31 / 9.0, (double)(var38 % 53L), 9301L));
         double var45 = this.terrain(var27, var29);
         if (var20 && var31 > 10.0) {
            double var47 = this.terrain(var27 + Math.cos(var23 + (Math.PI / 2)) * 8.0, var29 + Math.sin(var23 + (Math.PI / 2)) * 8.0);
            double var49 = this.terrain(var27 + Math.cos(var23 - (Math.PI / 2)) * 8.0, var29 + Math.sin(var23 - (Math.PI / 2)) * 8.0);
            if ((var47 + var49) * 0.5 < var45 - 2.5 || var35 - var13 > var42) {
               break;
            }
         }

         if (var20) {
            int var62 = (int)Math.floor(var45) - 2;
            int var48 = var62 - var35;
            double var66 = var48 > 10 ? 3.0 : (var48 > 6 ? 3.2 : (var48 > 3 ? 4.0 : 5.0));
            int var51 = (var48 > 10 ? 8 : (var48 > 6 ? 6 : 4)) - var21.nextInt(3);
            if (var48 >= 2 && var31 - var33 >= var66 && var31 >= (var48 > 6 ? 2.5 : 6.0)) {
               var35 += Math.min(var51, var48);
               var33 = var31;
            }

            if (var45 - (double)var35 > 18.0 && var31 > 8.0) {
               break;
            }
         } else {
            int var63 = (int)Math.floor(var45) - 1;
            int var65 = var35 - var63;
            if (var65 >= 2 && var31 - var33 >= (double)(var65 > 6 ? 3 : 4) && var31 >= 5.0) {
               var35 -= Math.min(var65 > 6 ? 5 : 3, var65);
               var33 = var31;
            }

            if (var35 <= 64 || var45 - (double)var35 > 10.0 && var31 > 10.0) {
               break;
            }
         }

         var2.add(var27, var29, var31, var35, var43);
         if (DEBUG) {
            System.out.printf(Locale.ROOT, "trace %s along=%.1f t=%.1f cur=%d%n", var20 ? "up" : "down", var31, var45, var35);
         }

         double var64 = 1.0E9;
         double var67 = var23;

         for (int var68 = -2; var68 <= 2; var68++) {
            double var52 = var23 + (double)var68 * 0.32;
            if (!(Math.abs(var52 - var25) > 1.0)) {
               double var54 = 3.0;
               double var56 = this.terrain(var27 + Math.cos(var52) * var54, var29 + Math.sin(var52) * var54);
               double var58 = (var20 ? var56 - var45 : var45 - var56) / var54;
               double var60 = Math.abs(var58 - var40)
                  + 0.55 * Math.abs(var52 - var25)
                  + 0.9 * Math.abs(var52 - var23)
                  + 0.25 * this.layout.noise(var31 / var36 + (double)var68 * 0.7, (double)(var38 % 71L), 9303L);
               if (!var20 && var58 < -0.2) {
                  var60 += 3.0;
               }

               if (var60 < var64) {
                  var64 = var60;
                  var67 = var52;
               }
            }
         }

         var23 += (var67 - var23) * 0.55;
         double var69 = var27 + Math.cos(var23) * 1.4;
         double var53 = var29 + Math.sin(var23) * 1.4;
         if (Math.hypot(var69 - var22.originX, var53 - var22.originZ) > 106.0
            || var69 < (double)(var22.x0 + 8)
            || var53 < (double)(var22.z0 + 8)
            || var69 > (double)(var22.x0 + var22.w - 9)
            || var53 > (double)(var22.z0 + var22.h - 9)) {
            break;
         }

         var27 = var69;
         var29 = var53;
      }

      if (var2.size() == 0) {
         var2.add(var5, var7, 0.0, var13, var14);
      }

      var2.heading = var23;
   }

   private void carveChannel(AlpineFalls.Work var1, AlpineFalls.Path var2, int var3, boolean var4, int var5) {
      AlpineFalls.Site var6 = var1.site;
      double var7 = 1.0E9;
      double var9 = -1.0E9;
      double var11 = 1.0E9;
      double var13 = -1.0E9;

      for (double[] var16 : var2.points) {
         var7 = Math.min(var7, var16[0]);
         var9 = Math.max(var9, var16[0]);
         var11 = Math.min(var11, var16[1]);
         var13 = Math.max(var13, var16[1]);
      }

      double[] var52 = var2.last();
      double var53 = var4 ? 2.6 + Math.min(2.2, var52[4] * 0.4) : 3.2 + Math.min(3.5, var52[4] * 0.8);
      int var18 = (int)var52[3];
      if (!var4) {
         while (true) {
            double var19 = 1.0E9;

            for (int var21 = 0; var21 < 20; var21++) {
               double var22 = (double)var21 * Math.PI / 10.0;
               double var24 = var53 * 1.2 + 1.5;
               var19 = Math.min(var19, this.terrain(var52[0] + Math.cos(var22) * var24, var52[1] + Math.sin(var22) * var24));
            }

            var18 = Math.min((int)var52[3], (int)Math.floor(var19) - 1);
            if ((int)var52[3] - var18 <= (this.layout.version() >= 15 ? 2 : 4) || var53 <= 2.2) {
               var18 = Math.max(var18, (int)var52[3] - 6);
               break;
            }

            var53 -= 0.6;
         }
      }

      int var55 = Math.max(var6.x0, (int)Math.floor(var7 - 14.0));
      int var20 = Math.min(var6.x0 + var6.w - 1, (int)Math.ceil(var9 + 14.0));
      int var56 = Math.max(var6.z0, (int)Math.floor(var11 - 14.0));
      int var57 = Math.min(var6.z0 + var6.h - 1, (int)Math.ceil(var13 + 14.0));
      int var23 = var2.size();

      for (int var58 = var56; var58 <= var57; var58++) {
         for (int var25 = var55; var25 <= var20; var25++) {
            double var26 = (double)var25 + 0.5;
            double var28 = (double)var58 + 0.5;
            double var30 = 1.0E18;
            int var32 = 0;

            for (int var33 = 0; var33 < var23; var33++) {
               double[] var34 = var2.get(var33);
               double var35 = (var34[0] - var26) * (var34[0] - var26) + (var34[1] - var28) * (var34[1] - var28);
               if (var35 < var30) {
                  var30 = var35;
                  var32 = var33;
               }
            }

            double var59 = Math.sqrt(var30);
            if (var32 < var23 - 1) {
               double[] var60 = var2.get(var32);
               double[] var36 = var2.get(var32 + 1);
               double var37 = var36[0] - var60[0];
               double var39 = var36[1] - var60[1];
               double var41 = var37 * var37 + var39 * var39;
               double var43 = Math.clamp(((var26 - var60[0]) * var37 + (var28 - var60[1]) * var39) / var41, 0.0, 1.0);
               var59 = Math.min(var59, Math.hypot(var26 - var60[0] - var37 * var43, var28 - var60[1] - var39 * var43));
            }

            if (var32 > 0) {
               double[] var61 = var2.get(var32 - 1);
               double[] var63 = var2.get(var32);
               double var65 = var63[0] - var61[0];
               double var67 = var63[1] - var61[1];
               double var69 = var65 * var65 + var67 * var67;
               double var71 = Math.clamp(((var26 - var61[0]) * var65 + (var28 - var61[1]) * var67) / var69, 0.0, 1.0);
               var59 = Math.min(var59, Math.hypot(var26 - var61[0] - var65 * var71, var28 - var61[1] - var67 * var71));
            }

            double[] var62 = var2.get(var32);
            int var64 = (int)var62[3];
            double var66 = var62[4] / 2.0;
            double var68 = Math.hypot(var52[0] - var26, var52[1] - var28) - var53 * (1.0 + 0.16 * this.layout.noise(var26 / 3.0, var28 / 3.0, 9401L));
            boolean var70 = var68 < 0.0 && var32 >= var23 - 4;
            int var42 = var6.index(var25, var58);
            byte var72 = var6.kind[var42];
            if (var72 != 1 && var72 != 5 && var72 != 6 && var72 != 3 && var72 != 11 && var72 != 12 && (var72 != 4 || var4)) {
               double var44 = Float.isNaN(var6.ground[var42]) ? var1.nat(var42) : Math.min(var1.nat(var42), (double)var6.ground[var42]);
               if (!(var59 < var66) && !var70) {
                  double var73 = var70 ? var68 : var59 - var66;
                  if (!(var73 > 30.0)) {
                     double var74 = (double)(var64 + 1) + var73 * 0.45 + var73 * var73 * 0.05 + 0.4 * this.layout.noise(var26 / 4.0, var28 / 4.0, 9405L);
                     double var75 = Math.min(var44, var74);
                     if (var73 < 2.2) {
                        var75 = Math.max(var75, (double)(var64 + 1));
                     }

                     if ((!(var75 >= var44 - 0.01) || !(var73 > 2.2)) && var6.water[var42] == -32768) {
                        var6.ground[var42] = (float)var75;
                        if (var72 == 0 || var72 == 14) {
                           var6.kind[var42] = 7;
                        }
                     }
                  }
               } else {
                  double var46 = var70
                     ? 1.0 - AlpineLayout.smooth(0.0, var53, Math.hypot(var52[0] - var26, var52[1] - var28))
                     : 1.0 - Math.min(1.0, var59 / Math.max(0.5, var66));
                  double var48 = (double)(1L + Math.round(var46 * (var70 ? 1.6 : 1.2) + 0.35 * this.layout.noise(var26 / 3.0, var28 / 3.0, 9403L)));
                  if (var4 && var62[2] < 3.0) {
                     var48 = 1.0;
                  }

                  int var50 = var70 && !var4 ? var18 : var64;
                  int var51 = var50 - (int)Math.max(1.0, var48);
                  var6.ground[var42] = (float)Math.min(var44, (double)var51);
                  var6.water[var42] = (short)var50;
                  var6.kind[var42] = (byte)(var70 && var4 ? 10 : var3);
                  if (var72 == 8) {
                     var6.kind[var42] = (byte)var3;
                  }
               }
            }
         }
      }
   }

   private void finish(AlpineFalls.Work var1, Random var2, double var3, double var5, int var7) {
      AlpineFalls.Site var8 = var1.site;
      int var9 = var8.w;
      int var10 = var8.h;

      for (int var11 = 0; var11 < 4; var11++) {
         boolean var12 = false;

         for (int var13 = 1; var13 < var10 - 1; var13++) {
            for (int var14 = 1; var14 < var9 - 1; var14++) {
               int var15 = var13 * var9 + var14;
               if (var8.water[var15] != -32768) {
                  short var16 = var8.water[var15];

                  for (int var17 = 0; var17 < 4; var17++) {
                     int var18 = var15 + (var17 == 0 ? 1 : (var17 == 1 ? -1 : (var17 == 2 ? var9 : -var9)));
                     if (var8.water[var18] == -32768 && var8.kind[var18] != 12) {
                        double var19 = Float.isNaN(var8.ground[var18]) ? var1.nat(var18) : (double)var8.ground[var18];
                        if (Math.floor(var19) < (double)var16) {
                           var8.ground[var18] = (float)var16;
                           if (var8.kind[var18] == 0 || var8.kind[var18] == 3) {
                              var8.kind[var18] = 13;
                           }

                           var12 = true;
                        }
                     }
                  }

                  if (var8.cavT[var15] > var8.cavB[var15]
                     && var8.cavW[var15] == -32768
                     && var8.cavB[var15] < var16
                     && Math.floor((double)var8.ground[var15]) >= (double)var8.cavT[var15]) {
                  }
               }
            }
         }

         if (!var12) {
            break;
         }
      }

      for (int var23 = 1; var23 < var10 - 1; var23++) {
         for (int var25 = 1; var25 < var9 - 1; var25++) {
            int var26 = var23 * var9 + var25;
            if (var8.water[var26] != -32768) {
               short var29 = var8.water[var26];

               for (int var33 = 0; var33 < 4; var33++) {
                  int var38 = var26 + (var33 == 0 ? 1 : (var33 == 1 ? -1 : (var33 == 2 ? var9 : -var9)));
                  boolean var42 = var8.water[var38] != -32768 && var8.water[var38] < var29;
                  boolean var44 = var8.water[var38] == -32768 && var8.kind[var38] == 12 && Math.floor((double)var8.ground[var38]) < (double)var29;
                  if ((var8.flags[var38] & 4) != 0 && var8.water[var38] != -32768 && var8.water[var38] == var29) {
                     boolean var48 = true;
                  } else {
                     boolean var10000 = false;
                  }

                  if (var42 || var44) {
                     var8.flags[var26] = (byte)(var8.flags[var26] | 1);
                     if (var29 - var8.water[var38] >= 3 || var44) {
                        var8.flags[var26] = (byte)(var8.flags[var26] | 16);
                     }
                  }
               }
            }
         }
      }

      double var24 = 8.0 + Math.min(26.0, (double)var7 * 0.7);

      for (int var27 = 0; var27 < var10; var27++) {
         for (int var30 = 0; var30 < var9; var30++) {
            int var34 = var27 * var9 + var30;
            double var39 = Math.hypot((double)(var8.x0 + var30) + 0.5 - var3, (double)(var8.z0 + var27) + 0.5 - var5);
            double var45 = Math.max(0.0, 1.0 - var39 / var24);
            if (var8.kind[var34] == 2 || var8.kind[var34] == 9 || var8.kind[var34] == 11) {
               var45 = Math.max(var45, 0.45);
            }

            if ((var8.flags[var34] & 1) != 0) {
               var45 = 1.0;
            }

            var8.wet[var34] = (byte)((int)Math.round(var45 * var45 * 255.0));
         }
      }

      boolean[] var28 = new boolean[var9 * var10];

      for (int var31 = 0; var31 < var9 * var10; var31++) {
         var28[var31] = var8.kind[var31] != 0;
      }

      byte var32 = 5;

      for (int var35 = 0; var35 < var10; var35++) {
         for (int var40 = 0; var40 < var9; var40++) {
            int var43 = var35 * var9 + var40;
            if (var28[var43]) {
               for (int var46 = -var32; var46 <= var32; var46++) {
                  for (int var47 = -var32; var47 <= var32; var47++) {
                     int var20 = var40 + var47;
                     int var21 = var35 + var46;
                     if (var20 >= 0 && var21 >= 0 && var20 < var9 && var21 < var10 && var47 * var47 + var46 * var46 <= var32 * var32) {
                        int var22 = var21 * var9 + var20;
                        var8.flags[var22] = (byte)(var8.flags[var22] | 2);
                        if (var8.kind[var22] == 0) {
                           var8.kind[var22] = 14;
                        }
                     }
                  }
               }
            }
         }
      }

      for (int var36 = 0; var36 < var9 * var10; var36++) {
         if (var8.kind[var36] == 14) {
            var8.ground[var36] = Float.NaN;
         }
      }

      for (int var37 = 0; var37 < var10; var37++) {
         for (int var41 = 0; var41 < var9; var41++) {
            if ((var37 <= 0 || var37 >= var10 - 1 || var41 <= 0 || var41 >= var9 - 1) && var8.water[var37 * var9 + var41] != -32768) {
               throw new IllegalStateException("water at raster edge");
            }
         }
      }
   }

   static long mix(long var0) {
      return AlpineLayout.mix(var0);
   }

   static double unit(long var0) {
      return (double)(var0 >>> 11) * 1.110223E-16F;
   }

   private static record Candidate(
      double x,
      double z,
      double dirX,
      double dirZ,
      double rise,
      int reach,
      int brink,
      double lateral,
      double descent,
      double beyond,
      double score,
      AlpineLayout.Sample at
   ) {
   }

   public interface Natural {
      AlpineLayout.Sample sample(double var1, double var3);
   }

   public static enum Palette {
      GRANITE,
      MOSSY,
      TRAVERTINE,
      BASALT;
   }

   private static final class Path {
      final ArrayList<double[]> points = new ArrayList<>();
      double heading;

      void add(double var1, double var3, double var5, int var7, double var8) {
         this.points.add(new double[]{var1, var3, var5, (double)var7, var8});
      }

      int size() {
         return this.points.size();
      }

      double[] get(int var1) {
         return this.points.get(var1);
      }

      double[] last() {
         return this.points.get(this.points.size() - 1);
      }
   }

   public static final class Site {
      public final int cellX;
      public final int cellZ;
      public final AlpineFalls.Type type;
      public final AlpineFalls.Palette palette;
      public final int biome;
      public final double originX;
      public final double originZ;
      public final double dirX;
      public final double dirZ;
      public final int poolLevel;
      public final int lipLevel;
      public final int lipWidth;
      public final int lipX;
      public final int lipZ;
      final int x0;
      final int z0;
      final int w;
      final int h;
      final float[] ground;
      final short[] water;
      final short[] cavB;
      final short[] cavT;
      final short[] cavW;
      final byte[] kind;
      final byte[] wet;
      final byte[] flags;

      Site(
         int var1,
         int var2,
         AlpineFalls.Type var3,
         AlpineFalls.Palette var4,
         int var5,
         double var6,
         double var8,
         double var10,
         double var12,
         int var14,
         int var15,
         int var16,
         int var17,
         int var18,
         int var19,
         int var20,
         int var21,
         int var22
      ) {
         this.cellX = var1;
         this.cellZ = var2;
         this.type = var3;
         this.palette = var4;
         this.biome = var5;
         this.originX = var6;
         this.originZ = var8;
         this.dirX = var10;
         this.dirZ = var12;
         this.poolLevel = var14;
         this.lipLevel = var15;
         this.lipWidth = var16;
         this.lipX = var17;
         this.lipZ = var18;
         this.x0 = var19;
         this.z0 = var20;
         this.w = var21;
         this.h = var22;
         int var23 = var21 * var22;
         this.ground = new float[var23];
         Arrays.fill(this.ground, Float.NaN);
         this.water = new short[var23];
         Arrays.fill(this.water, (short)-32768);
         this.cavB = new short[var23];
         this.cavT = new short[var23];
         this.cavW = new short[var23];
         Arrays.fill(this.cavW, (short)-32768);
         this.kind = new byte[var23];
         this.wet = new byte[var23];
         this.flags = new byte[var23];
      }

      public boolean contains(int var1, int var2) {
         return var1 >= this.x0 && var2 >= this.z0 && var1 < this.x0 + this.w && var2 < this.z0 + this.h;
      }

      int index(int var1, int var2) {
         return (var2 - this.z0) * this.w + (var1 - this.x0);
      }

      public int drop() {
         return this.lipLevel - this.poolLevel;
      }

      public String describe() {
         return String.format(
            Locale.ROOT,
            "%s %s drop=%d lip=%d pool=%d width=%d at %d %d %d",
            this.type,
            this.palette,
            this.drop(),
            this.lipLevel,
            this.poolLevel,
            this.lipWidth,
            this.lipX,
            this.lipLevel,
            this.lipZ
         );
      }
   }

   public static enum Type {
      PLUNGE,
      RIBBON,
      CURTAIN,
      TWIN,
      PUNCHBOWL,
      GROTTO,
      BASALT,
      TIERED,
      SLIDE;
   }

   private final class Work {
      final AlpineFalls.Site site;
      final Random random;
      final double ox;
      final double oz;
      final double dx;
      final double dz;
      final double nx;
      final double nz;
      final float[] nat;

      Work(AlpineFalls.Site nullx, long nullxx) {
         this.site = nullx;
         this.random = new Random(nullxx);
         this.ox = nullx.originX;
         this.oz = nullx.originZ;
         this.dx = nullx.dirX;
         this.dz = nullx.dirZ;
         this.nx = -this.dz;
         this.nz = this.dx;
         this.nat = new float[nullx.w * nullx.h];
         Arrays.fill(this.nat, Float.NaN);
      }

      double nat(int var1) {
         float var2 = this.nat[var1];
         if (Float.isNaN(var2)) {
            int var3 = this.site.x0 + var1 % this.site.w;
            int var4 = this.site.z0 + var1 / this.site.w;
            var2 = (float)AlpineFalls.this.terrain((double)var3, (double)var4);
            this.nat[var1] = var2;
         }

         return (double)var2;
      }

      double u(int var1, int var2) {
         return ((double)var1 + 0.5 - this.ox) * this.dx + ((double)var2 + 0.5 - this.oz) * this.dz;
      }

      double v(int var1, int var2) {
         return ((double)var1 + 0.5 - this.ox) * this.nx + ((double)var2 + 0.5 - this.oz) * this.nz;
      }

      double wx(double var1, double var3) {
         return this.ox + this.dx * var1 + this.nx * var3;
      }

      double wz(double var1, double var3) {
         return this.oz + this.dz * var1 + this.nz * var3;
      }
   }
}
