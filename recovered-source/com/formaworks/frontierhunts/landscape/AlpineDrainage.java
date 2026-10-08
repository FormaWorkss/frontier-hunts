package com.formaworks.frontierhunts.landscape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicReferenceArray;

public final class AlpineDrainage {
   private static final double CELL = 432.0;
   private static final int[] DX = new int[]{1, -1, 0, 0};
   private static final int[] DZ = new int[]{0, 0, 1, -1};
   private final AlpineLayout noise;
   private final AtomicReferenceArray<AlpineDrainage.Node> nodes = new AtomicReferenceArray<>(2048);
   private final AtomicReferenceArray<AlpineDrainage.Region> regions = new AtomicReferenceArray<>(256);
   private final AtomicReferenceArray<AlpineDrainage.Runoff> flows = new AtomicReferenceArray<>(4096);
   private final AtomicReferenceArray<AlpineDrainage.Spill> spills = new AtomicReferenceArray<>(512);
   private final AlpineFalls falls;
   private final AlpineMountains mountains;
   static final int PEAK_CELL = 1150;

   private AlpineDrainage.Reach reach(AlpineDrainage.Node var1, AlpineDrainage.Node var2, double var3, double var5) {
      AlpineDrainage.Reach var7 = new AlpineDrainage.Reach(var1, var2, var3, var5, this.noise.version() >= 9);
      var7.natural = this.noise.version() >= 8;
      var7.blended = this.noise.version() >= 10;
      return var7;
   }

   private AlpineDrainage.Spill spill(AlpineDrainage.Node var1) {
      int var2 = (int)var1.hash & 511;
      AlpineDrainage.Spill var3 = this.spills.get(var2);
      if (var3 != null && var3.x == var1.x && var3.z == var1.z) {
         return var3;
      } else {
         record Step(AlpineDrainage.Node node, double cost, Step previous, int distance) {
         }

         PriorityQueue var4 = new PriorityQueue<>(Comparator.comparingDouble(Step::cost));
         HashSet var5 = new HashSet();
         var4.add(new Step(var1, 0.0, null, 0));
         Step var6 = null;

         while (!var4.isEmpty()) {
            Step var7 = (Step)var4.remove();
            AlpineDrainage.Node var8 = var7.node;
            long var9 = (long)var8.x << 32 ^ (long)var8.z & 4294967295L;
            if (var5.add(var9)) {
               if (var8.level < var1.level - 0.5 && (var8.level <= 64.0 || this.runoff(var8) >= 0.38)) {
                  var6 = var7;
                  break;
               }

               for (int var11 = 0; var11 < 4; var11++) {
                  int var12 = var8.x + DX[var11];
                  int var13 = var8.z + DZ[var11];
                  if (Math.abs(var12 - var1.x) <= 4 && Math.abs(var13 - var1.z) <= 4) {
                     AlpineDrainage.Node var14 = this.node(var12, var13);
                     double var15 = Math.max(0.0, var14.level - var1.level);
                     var4.add(new Step(var14, var7.cost + 1.0 + var15 * 0.22, var7, var7.distance + 1));
                  }
               }
            }
         }

         ArrayList var17 = new ArrayList();
         if (var6 != null) {
            ArrayList var18 = new ArrayList();

            for (Step var20 = var6; var20 != null; var20 = var20.previous) {
               var18.add(var20.node);
            }

            Collections.reverse(var18);
            double var21 = this.runoff(var1);
            AlpineDrainage.Node var22 = var1;

            for (int var23 = 1; var23 < var18.size(); var23++) {
               AlpineDrainage.Node var24 = (AlpineDrainage.Node)var18.get(var23);
               double var25 = AlpineLayout.lerp(var1.level, var6.node.level, (double)var23 / (double)(var18.size() - 1));
               AlpineDrainage.Node var16 = new AlpineDrainage.Node(var24.x, var24.z, var24.px, var24.pz, var25, var24.hash);
               var17.add(this.reach(var22, var16, var21, var21));
               var22 = var16;
            }
         }

         AlpineDrainage.Spill var19 = new AlpineDrainage.Spill(var1.x, var1.z, var17.toArray(AlpineDrainage.Reach[]::new));
         this.spills.set(var2, var19);
         return var19;
      }
   }

   public AlpineDrainage(AlpineLayout var1) {
      this.noise = var1;
      this.mountains = var1.version() >= 20 ? new AlpineMountains(var1, this) : null;
      this.falls = var1.version() >= 13 ? new AlpineFalls(var1, this::natural) : null;
   }

   AlpineMountains mountains() {
      return this.mountains;
   }

   public AlpineFalls falls() {
      return this.falls;
   }

   private static double unit(long var0) {
      return (double)(var0 >>> 11) * 1.110223E-16F;
   }

   private static double junctionRamp(double var0) {
      double var2 = Math.max(0.0, var0);
      return var2 >= 96.0 ? Double.POSITIVE_INFINITY : var2 * 0.24 + 0.015 * var2 * var2 / (1.0 - var2 / 96.0);
   }

   private long key(int var1, int var2, long var3) {
      return AlpineLayout.mix(
         this.noise.seed()
            ^ AlpineLayout.mix((long)var1 * 7146057691288625177L)
            ^ Long.rotateLeft(AlpineLayout.mix((long)var2 * -7046029254386353131L), 31)
            ^ var3
      );
   }

   private AlpineDrainage.Node node(int var1, int var2) {
      long var3 = this.key(var1, var2, 7019L);
      int var5 = (int)var3 & 2047;
      AlpineDrainage.Node var6 = this.nodes.get(var5);
      if (var6 != null && var6.x == var1 && var6.z == var2) {
         return var6;
      } else {
         double var7 = ((double)var1 + 0.22 + 0.56 * unit(AlpineLayout.mix(var3 + 1L))) * 432.0;
         double var9 = ((double)var2 + 0.22 + 0.56 * unit(AlpineLayout.mix(var3 + 2L))) * 432.0;
         var6 = new AlpineDrainage.Node(var1, var2, var7, var9, Math.max(64.0, this.potential(var7, var9) - 12.0), var3);
         this.nodes.set(var5, var6);
         return var6;
      }
   }

   public double continental(double var1, double var3) {
      double var5 = var1 + 540.0 * this.noise.noise(var1 / 1420.0, var3 / 1420.0, 7005L);
      double var7 = var3 + 540.0 * this.noise.noise(var1 / 1420.0, var3 / 1420.0, 7007L);
      return this.noise.noise(var5 / 3500.0, var7 / 3500.0, 7009L) + 0.12 * this.noise.noise(var1 / 750.0, var3 / 750.0, 7011L);
   }

   public double potential(double var1, double var3) {
      double var5 = 135.0 + 67.0 * this.noise.noise(var1 / 1420.0, var3 / 1420.0, 7001L) + 24.0 * this.noise.noise(var1 / 2900.0, var3 / 2900.0, 7003L);
      if (this.noise.version() >= 9) {
         double var7 = AlpineLayout.smooth(0.02, 0.49, this.noise.noise(var1 / 970.0, var3 / 970.0, 7201L));
         var5 += var7 * (18.0 + 58.0 * AlpineLayout.smooth(-0.15, 0.46, this.noise.noise(var1 / 510.0, var3 / 510.0, 7203L)));
      }

      return AlpineLayout.lerp(76.0, var5, AlpineLayout.smooth(-0.18, 0.14, this.continental(var1, var3)));
   }

   private AlpineDrainage.Node outlet(AlpineDrainage.Node var1) {
      AlpineDrainage.Node var2 = var1;

      for (int var3 = 0; var3 < 4; var3++) {
         AlpineDrainage.Node var4 = this.node(var1.x + DX[var3], var1.z + DZ[var3]);
         if (var4.level < var2.level) {
            var2 = var4;
         }
      }

      return var2;
   }

   private double runoff(AlpineDrainage.Node var1) {
      int var2 = (int)var1.hash & 4095;
      AlpineDrainage.Runoff var3 = this.flows.get(var2);
      if (var3 != null && var3.x == var1.x && var3.z == var1.z) {
         return var3.amount;
      } else {
         double var4 = AlpineLayout.smooth(-0.14, 0.42, this.noise.noise(var1.px / 1550.0, var1.pz / 1550.0, 7041L));
         double var6 = 0.025 + var4 * var4 * (0.2 + unit(AlpineLayout.mix(var1.hash + 79L)) * 0.85);

         for (int var8 = 0; var8 < 4; var8++) {
            AlpineDrainage.Node var9 = this.node(var1.x + DX[var8], var1.z + DZ[var8]);
            if (!(var9.level <= var1.level)) {
               AlpineDrainage.Node var10 = this.outlet(var9);
               if (var10.x == var1.x && var10.z == var1.z) {
                  var6 += this.runoff(var9);
               }
            }
         }

         this.flows.set(var2, new AlpineDrainage.Runoff(var1.x, var1.z, var6));
         return var6;
      }
   }

   private AlpineDrainage.Region region(int var1, int var2) {
      int var3 = (int)this.key(var1, var2, 7021L) & 0xFF;
      AlpineDrainage.Region var4 = this.regions.get(var3);
      if (var4 != null && var4.x == var1 && var4.z == var2) {
         return var4;
      } else {
         ArrayList var5 = new ArrayList();
         ArrayList var6 = new ArrayList();
         int var7 = this.noise.version() >= 8 ? 7 : 3;

         for (int var8 = -var7; var8 <= var7; var8++) {
            for (int var9 = -var7; var9 <= var7; var9++) {
               AlpineDrainage.Node var10 = this.node(var1 + var8, var2 + var9);
               AlpineDrainage.Node var11 = this.outlet(var10);
               double var12 = this.runoff(var10);
               if (!(var12 < (this.noise.version() >= 9 ? 0.18 : 0.38))) {
                  if (var11 == var10) {
                     if (var10.level > 64.0) {
                        var6.add(var10);
                        if (this.noise.version() >= 8) {
                           for (AlpineDrainage.Reach var30 : this.spill(var10).reaches) {
                              double var18 = Double.MAX_VALUE;
                              double var20 = var18;
                              double var22 = -var18;
                              double var24 = -var18;

                              for (int var26 = 0; var26 < 17; var26++) {
                                 var18 = Math.min(var18, var30.x[var26]);
                                 var22 = Math.max(var22, var30.x[var26]);
                                 var20 = Math.min(var20, var30.z[var26]);
                                 var24 = Math.max(var24, var30.z[var26]);
                              }

                              if (var22 + 260.0 >= (double)var1 * 432.0
                                 && var18 - 260.0 <= (double)(var1 + 1) * 432.0
                                 && var24 + 260.0 >= (double)var2 * 432.0
                                 && var20 - 260.0 <= (double)(var2 + 1) * 432.0) {
                                 var5.add(var30);
                              }
                           }
                        }
                     }
                  } else {
                     AlpineDrainage.Reach var14 = this.reach(var10, var11, var12, this.runoff(var11));
                     double var15 = Double.MAX_VALUE;
                     double var17 = var15;
                     double var19 = -var15;
                     double var21 = -var15;

                     for (int var23 = 0; var23 < 17; var23++) {
                        var15 = Math.min(var15, var14.x[var23]);
                        var19 = Math.max(var19, var14.x[var23]);
                        var17 = Math.min(var17, var14.z[var23]);
                        var21 = Math.max(var21, var14.z[var23]);
                     }

                     if (var19 + 260.0 >= (double)var1 * 432.0
                        && var15 - 260.0 <= (double)(var1 + 1) * 432.0
                        && var21 + 260.0 >= (double)var2 * 432.0
                        && var17 - 260.0 <= (double)(var2 + 1) * 432.0) {
                        var5.add(var14);
                     }
                  }
               }
            }
         }

         AlpineDrainage.Region var27 = new AlpineDrainage.Region(
            var1, var2, var5.toArray(AlpineDrainage.Reach[]::new), var6.toArray(AlpineDrainage.Node[]::new)
         );
         this.regions.set(var3, var27);
         return var27;
      }
   }

   public AlpineWatershed.Water water(double var1, double var3) {
      AlpineDrainage.Region var5 = this.region((int)Math.floor(var1 / 432.0), (int)Math.floor(var3 / 432.0));
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
      double var30 = Double.POSITIVE_INFINITY;

      for (AlpineDrainage.Reach var35 : var5.reaches) {
         double var36 = Double.MAX_VALUE;
         double var38 = 0.0;
         double var40 = 0.0;
         double var42 = 0.0;

         for (int var44 = 0; var44 < 16; var44++) {
            double var45 = var35.x[var44 + 1] - var35.x[var44];
            double var47 = var35.z[var44 + 1] - var35.z[var44];
            double var49 = var45 * var45 + var47 * var47;
            double var51 = Math.clamp(((var1 - var35.x[var44]) * var45 + (var3 - var35.z[var44]) * var47) / var49, 0.0, 1.0);
            double var53 = var1 - var35.x[var44] - var45 * var51;
            double var55 = var3 - var35.z[var44] - var47 * var51;
            double var57 = var53 * var53 + var55 * var55;
            if (var57 < var36) {
               var36 = var57;
               var38 = (var35.length[var44] + var51 * Math.sqrt(var49)) / var35.length[16];
               var40 = var45 / Math.sqrt(var49);
               var42 = var47 / Math.sqrt(var49);
            }
         }

         var38 = Math.clamp(
            var38
               + (7.0 * this.noise.noise(var1 / 43.0, var3 / 43.0, 7027L) + 2.0 * this.noise.noise(var1 / 19.0, var3 / 19.0, 7025L))
                  * Math.sin(Math.PI * var38)
                  / var35.length[16],
            0.0,
            1.0
         );
         double var74 = AlpineLayout.lerp(var35.widthA, var35.widthB, var38) * (1.0 + 0.5 * this.noise.noise(var1 / 85.0, var3 / 85.0, 7029L));
         if (var35.style == 4) {
            var74 *= 1.0 + (var35.scenic ? 0.5 : 1.4) * AlpineLayout.smooth(0.22, 0.32, var38) * (1.0 - AlpineLayout.smooth(0.71, 0.83, var38));
         }

         var74 += (3.0 + unit(AlpineLayout.mix(var35.a.hash + 89L)) * 10.0)
            * AlpineLayout.smooth(var35.lip - 0.03, var35.lip + 0.02, var38)
            * (1.0 - AlpineLayout.smooth(var35.lip + 0.03, var35.lip + 0.12, var38))
            * (var35.scenic ? Math.min(1.0, var74 / 12.0) : 1.0);
         double var46 = Math.sqrt(var36);
         double var48 = var46 - var74;
         double var50 = 1.0 - AlpineLayout.smooth(var74 + 24.0, var74 + 115.0, var46);
         double var52 = var35.level(var38);
         if (this.noise.version() >= 9) {
            var30 = Math.min(var30, var52 + junctionRamp(var48));
         }

         var28 = Math.max(var28, var50);
         var50 *= var50;
         var24 += (this.noise.version() >= 10 ? AlpineLayout.lerp(var35.a.level, var35.b.level, var38) : var52) * var50;
         var26 += var50;
         if (var48 < var6) {
            var6 = var48;
            var8 = var46;
            var10 = var74;
            var12 = var52;
            double var54 = 2.0 / var35.length[16];
            var14 = var35.level(Math.max(0.0, var38 - var54)) - var35.level(Math.min(1.0, var38 + var54));
            var16 = var40;
            var18 = var42;
            var20 = 0.012 + Math.min(0.048, var14 * 0.004 + var35.drop / var35.length[16] * 0.045);
            var22 = var35.style;
            var23 = false;
         }
      }

      for (AlpineDrainage.Node var68 : var5.lakes) {
         double var69 = unit(AlpineLayout.mix(var68.hash + 55L)) * Math.PI * 2.0;
         double var71 = var1 - var68.px;
         double var72 = var3 - var68.pz;
         double var73 = var71 * Math.cos(var69) - var72 * Math.sin(var69);
         double var76 = var71 * Math.sin(var69) + var72 * Math.cos(var69);
         double var77 = 0.6 + 1.4 * AlpineLayout.smooth(-0.1, 0.4, this.noise.noise(var68.px / 1420.0, var68.pz / 1420.0, 7043L));
         double var78 = Math.min(90.0, 16.0 + Math.sqrt(this.runoff(var68)) * 20.0) * var77;
         double var80 = var78 * (0.6 + unit(AlpineLayout.mix(var68.hash + 56L)));
         double var81 = var78 * (0.6 + unit(AlpineLayout.mix(var68.hash + 57L)));
         double var82 = Math.sqrt(var73 * var73 / (var80 * var80) + var76 * var76 / (var81 * var81));
         double var56 = (var80 + var81) * 0.5 * (1.0 + 0.24 * this.noise.noise(var1 / 70.0, var3 / 70.0, 7031L));
         double var58 = var82 * (var80 + var81) * 0.5;
         double var60 = var58 - var56;
         if (this.noise.version() >= 9) {
            var30 = Math.min(var30, var68.level + junctionRamp(var60));
         }

         double var62 = 1.0 - AlpineLayout.smooth(var56 + 24.0, var56 + 115.0, var58);
         var28 = Math.max(var28, var62);
         var62 *= var62;
         var24 += var68.level * var62;
         var26 += var62;
         if (var60 < var6) {
            var6 = var60;
            var8 = var58;
            var10 = var56;
            var12 = var68.level;
            var14 = 0.0;
            var20 = 0.0;
            var18 = 0.0;
            var16 = 0.0;
            var22 = 6;
            var23 = true;
         }
      }

      if (this.noise.version() >= 9 && Double.isFinite(var30)) {
         double var65 = Math.max(0.0, var12 - var30);
         var12 -= var65;
         if (var65 > 1.0) {
            var14 = 0.0;
         }
      }

      return new AlpineWatershed.Water(
         var8,
         var10,
         var12,
         var14,
         var16,
         var18,
         var20,
         var22,
         var23,
         var26 > 0.0 ? Math.min(var24 / var26, this.noise.version() == 9 ? var12 + 8.0 : Double.POSITIVE_INFINITY) : var12,
         var28
      );
   }

   public AlpineLayout.Sample natural(double var1, double var3) {
      return this.natural(var1, var3, true);
   }

   AlpineLayout.Sample naturalDry(double var1, double var3) {
      return this.natural(var1, var3, false);
   }

   private AlpineLayout.Sample natural(double var1, double var3, boolean var5) {
      boolean var6 = this.noise.version() >= 5;
      double var7 = this.continental(var1, var3);
      double var9 = AlpineLayout.smooth(-0.24, 0.1, var7);
      double var11 = var6 ? 1080.0 : 1750.0;
      double var13 = var1 + 230.0 * this.noise.noise(var1 / var11, var3 / var11, 7091L);
      double var15 = var3 + 230.0 * this.noise.noise(var1 / var11, var3 / var11, 7093L);
      double var17 = AlpineLayout.smooth(0.14, 0.48, this.noise.noise(var13 / (double)(var6 ? 1050 : 1580), var15 / (double)(var6 ? 1050 : 1580), 7101L));
      double var19 = AlpineLayout.smooth(0.02, 0.38, this.noise.noise(var1 / (double)(var6 ? 410 : 570), var3 / (double)(var6 ? 410 : 570), 7103L));
      double var21 = Math.pow(1.0 - Math.abs(this.noise.fbm(var1 / (double)(var6 ? 330 : 435), var3 / (double)(var6 ? 330 : 435), 7105L, 3)), var6 ? 2.35 : 2.8);
      double var23 = AlpineLayout.smooth(0.48, 0.76, this.noise.noise(var13 / 3200.0, var15 / 3200.0, 7199L));
      double var25 = var17 * ((double)(var6 ? 98 : 145) + (double)(var6 ? 205 : 285) * var21) * (1.0 + var23 * (var6 ? 1.05 : 0.65));
      if (this.noise.version() >= 10) {
         double var27 = this.noise.fbm(var1 / 330.0, var3 / 330.0, 7105L, 3);
         double var29 = Math.pow(Math.max(0.0, 1.0 - var27 * var27), 2.8);
         double var31 = 0.72 + 0.28 * AlpineLayout.smooth(-0.35, 0.4, this.noise.noise(var1 / 210.0, var3 / 210.0, 7221L));
         var25 = var17 * (62.0 + 142.0 * var29) * var31 * (1.0 + var23 * 0.85);
      }

      double var88 = 0.0;
      double var89 = 0.0;
      double var90 = 0.0;
      double var33 = 0.0;
      double var35 = 0.0;
      double var37 = 0.0;
      AlpineDrainage.Peak var39 = AlpineDrainage.Peak.NONE;
      if (this.noise.version() >= 16) {
         var89 = AlpineLayout.smooth(
            -0.3, 0.34, this.noise.noise(var13 / 1180.0, var15 / 1180.0, 7101L) + 0.3 * this.noise.noise(var13 / 2700.0, var15 / 2700.0, 7301L)
         );
         double var40 = var1 + 185.0 * this.noise.noise(var1 / 1250.0, var3 / 1250.0, 7319L);
         double var42 = var3 + 185.0 * this.noise.noise(var1 / 1250.0 + 71.3, var3 / 1250.0 - 33.7, 7321L);
         double var44 = 1.0 - Math.abs(this.noise.noise(var40 / 560.0, var42 / 560.0, 7105L));
         double var46 = 1.0 - Math.abs(this.noise.noise(var40 / 238.0, var42 / 238.0, 7303L));
         double var48 = 1.0 - Math.abs(this.noise.noise(var40 / 103.0, var42 / 103.0, 7305L));
         double var50 = Math.pow(Math.clamp(var44 * var44 * (0.6 + 0.4 * var46) * (0.8 + 0.2 * var48), 0.0, 1.0), 1.55);
         double var52 = 0.7 + 0.3 * AlpineLayout.smooth(-0.35, 0.4, this.noise.noise(var1 / 210.0, var3 / 210.0, 7221L));
         double var54 = AlpineLayout.smooth(0.0, 0.4, this.noise.noise(var13 / 3400.0, var15 / 3400.0, 7313L));
         double var56 = var54 * AlpineLayout.smooth(0.44, 0.88, var50);
         var25 = var89 * (70.0 + 250.0 * var50) * var52 * (1.0 + var23 * 0.55 + var56 * 1.05);
         if (this.noise.version() >= 17) {
            double var58 = AlpineLayout.smooth(-0.45, 0.55, this.noise.noise(var40 / 900.0, var42 / 900.0, 7331L));
            double var60 = Math.clamp(Math.pow(var44, 1.35) * (0.62 + 0.38 * var46) * (0.82 + 0.18 * var48), 0.0, 1.0);
            var50 = var60 * var60 * (3.0 - 2.0 * var60);
            var56 = var54 * AlpineLayout.smooth(0.35, 0.8, var58);
            double var62 = this.noise.noise(var13 / 2600.0, var15 / 2600.0, 7341L) + 0.35 * this.noise.noise(var13 / 900.0, var15 / 900.0, 7343L);
            double var64 = AlpineLayout.smooth(-0.3, 0.45, var62);
            var25 = var89 * (66.0 + 132.0 * (1.35 - 0.55 * var64) * var58 + 112.0 * (0.45 + 1.05 * var64) * var50) * var52 * (1.0 + var23 * 0.45 + var56 * 0.9);
            var90 = (var64 - 0.5) * 90.0;
            if (this.noise.version() >= 20) {
               double[] var66 = this.mountains.body(var1, var3);
               boolean var67 = this.noise.version() >= 21;
               var39 = var67 ? this.mountains.peak21(var1, var3) : this.mountains.peak(var1, var3);
               double var68 = var66[0] + var39.height;
               double var70 = this.mountains.body(var1 + 3.0, var3)[0]
                  + (var67 ? this.mountains.peak21(var1 + 3.0, var3) : this.mountains.peak(var1 + 3.0, var3)).height;
               double var72 = this.mountains.body(var1, var3 + 3.0)[0]
                  + (var67 ? this.mountains.peak21(var1, var3 + 3.0) : this.mountains.peak(var1, var3 + 3.0)).height;
               double var74 = (var70 - var68) / 3.0;
               double var76 = (var72 - var68) / 3.0;
               double[] var78 = this.noise.version() >= 21
                  ? this.mountains.erode21(var1, var3, var74, var76, var68, var39.up(), var39.arm())
                  : this.mountains.erode(var1, var3, var74, var76, var68, var39.up());
               var25 = var68 + var78[0];
               var33 = var78[1];
               double var79 = Math.hypot(var74, var76);
               var37 = var79;
               var35 = var79 > 0.02 ? AlpineLayout.smooth(-0.25, 0.85, var76 / var79) * AlpineLayout.smooth(0.02, 0.2, var79) : 0.0;
               var90 = (var64 - 0.5) * 40.0;
            } else if (this.noise.version() >= 19) {
               var25 = var89 * (46.0 + 64.0 * var58 + (58.0 + 64.0 * var64) * var50) * var52 * (1.0 + var23 * 0.3);
               var90 = (var64 - 0.5) * 40.0;
               var39 = this.peak(var1, var3);
               var25 += var39.height;
            }
         }

         var88 = AlpineLayout.smooth(-0.34, 0.44, this.noise.noise(var13 / 1650.0, var15 / 1650.0, 7307L));
      }

      double var91 = (double)(var6 ? 9 : 12)
         + (double)(var6 ? 27 : 36)
            * AlpineLayout.smooth(-0.15, 0.45, this.noise.noise(var1 / (double)(var6 ? 620 : 980), var3 / (double)(var6 ? 620 : 980), 7095L));
      double var92 = this.potential(var1, var3) + 16.0 + var91 * this.noise.fbm(var1 / 180.0, var3 / 180.0, 7107L, 3) + var25;
      if (this.noise.version() >= 16) {
         var92 += var88 * (34.0 + 58.0 * AlpineLayout.smooth(-0.4, 0.5, this.noise.noise(var1 / 430.0, var3 / 430.0, 7309L))) * (1.0 - 0.52 * var89);
         var92 += Math.max(var17, var89)
            * var19
            * var19
            * (this.noise.version() >= 20 ? 1.0 - 0.75 * AlpineLayout.smooth(20.0, 200.0, var39.height) : 1.0)
            * (
               27.0 * Math.pow(Math.max(0.0, 1.0 - Math.abs(this.noise.noise(var1 / 69.0, var3 / 69.0, 7109L))), 2.6)
                  - 16.0 * Math.abs(this.noise.noise(var1 / 31.0, var3 / 31.0, 7111L))
            );
         var17 = Math.max(var17, var89);
      } else {
         var92 += var17
            * var19
            * (
               38.0 * Math.pow(1.0 - Math.abs(this.noise.noise(var1 / 69.0, var3 / 69.0, 7109L)), 5.0)
                  - 12.0 * Math.abs(this.noise.noise(var1 / 31.0, var3 / 31.0, 7111L))
            );
      }

      double var99 = AlpineLayout.smooth(0.23, 0.51, this.noise.noise(var13 / (double)(var6 ? 820 : 1280), var15 / (double)(var6 ? 820 : 1280), 7113L))
         * (1.0 - var17);
      double var100 = 0.0;
      if (this.noise.version() >= 19) {
         double var102 = this.noise.noise(var1 / 700.0, var3 / 700.0, 7651L) + 0.35 * this.noise.noise(var1 / 260.0, var3 / 260.0, 7653L);
         double var105 = this.noise.noise(var1 / 170.0, var3 / 170.0, 7655L) + 0.3 * this.noise.noise(var1 / 70.0, var3 / 70.0, 7657L);
         var100 = Math.max(AlpineLayout.smooth(0.26, 0.42, var102), AlpineLayout.smooth(0.5, 0.64, var105));
         var100 *= (1.0 - var99) * (1.0 - 0.92 * AlpineLayout.smooth(40.0, 170.0, var39.height));
      }

      var92 += var99 * 120.0 * Math.pow(AlpineLayout.smooth(-0.2, 0.55, this.noise.noise(var1 / 140.0, var3 / 140.0, 7115L)), 2.0);
      double var103 = AlpineLayout.smooth(-0.04, 0.31, this.noise.noise(var1 / (double)(var6 ? 510 : 820), var3 / (double)(var6 ? 510 : 820), 7117L))
         * (1.0 - var17)
         * (1.0 - var99);
      if (this.noise.version() >= 16) {
         var103 = AlpineLayout.smooth(-0.04, 0.31, this.noise.noise(var1 / 510.0, var3 / 510.0, 7117L)) * (1.0 - var17 * 0.7) * (1.0 - var99);
         var92 = AlpineLayout.lerp(
            var92, this.potential(var1, var3) + 12.0 + 9.0 * this.noise.noise(var1 / 185.0, var3 / 185.0, 7119L), var103 * 0.65 * (1.0 - 0.55 * var17)
         );
      } else {
         var92 = AlpineLayout.lerp(var92, this.potential(var1, var3) + 12.0 + 9.0 * this.noise.noise(var1 / 185.0, var3 / 185.0, 7119L), var103 * 0.65);
      }

      if (var6) {
         double var106 = this.noise.version() >= 19 ? Math.max(var103, var100 * 0.8) : var103;
         double var110 = (1.0 - var106 * 0.82) * (0.45 + 0.55 * var19);
         var92 += var110 * (5.5 * this.noise.noise(var1 / 46.0, var3 / 46.0, 7161L) + 2.2 * this.noise.noise(var1 / 17.0, var3 / 17.0, 7163L));
      }

      if (this.noise.version() >= 16) {
         double var107 = var89
            * var19
            * AlpineLayout.smooth(205.0, 395.0, var92)
            * (1.0 - (this.noise.version() >= 19 ? Math.max(var103, var100) : var103) * 0.9);
         double var111 = 12.5 + 7.5 * this.noise.noise(var1 / 900.0, var3 / 900.0, 7315L);
         double var114 = (var92 + 11.0 * this.noise.noise(var1 / 260.0, var3 / 260.0, 7317L)) / var111;
         var92 += var107 * 4.2 * (2.0 * Math.abs(var114 - Math.floor(var114) - 0.5) - 0.5);
         var92 += (1.0 - (this.noise.version() >= 19 ? Math.max(var103, var100 * 0.8) : var103) * 0.85)
            * (0.5 + 0.5 * var19)
            * 1.45
            * this.noise.noise(var1 / 8.5, var3 / 8.5, 7311L);
      }

      if (this.noise.version() >= 20 && var39.height > 20.0) {
         double var108 = AlpineLayout.smooth(0.75, 1.45, var37)
            * AlpineLayout.smooth(20.0, 150.0, var39.height)
            * AlpineLayout.smooth(0.28, 0.5, var39.up())
            * (this.noise.version() >= 21 ? 1.0 - 0.78 * var39.green() : (var39.type == 1 ? 0.4 : 1.0));
         double var112 = 10.5 + 4.5 * this.noise.noise(var1 / 700.0, var3 / 700.0, 7761L);
         double var115 = (var92 + 8.0 * this.noise.noise(var1 / 190.0, var3 / 190.0, 7763L)) / var112;
         var92 += var108 * 4.2 * (2.0 * Math.abs(var115 - Math.floor(var115) - 0.5) - 0.5);
      }

      if (this.noise.version() >= 21) {
         if (var92 > 880.0) {
            var92 = 880.0 + 100.0 * Math.tanh((var92 - 880.0) / 100.0);
         }
      } else if (this.noise.version() >= 20) {
         if (var92 > 820.0) {
            var92 = 820.0 + 100.0 * Math.tanh((var92 - 820.0) / 100.0);
         }
      } else if (var92 > 780.0) {
         var92 = 780.0 + 100.0 * Math.tanh((var92 - 780.0) / 100.0);
      }

      double var109 = AlpineLayout.lerp(
         -38.0 + 12.0 * this.noise.noise(var1 / 310.0, var3 / 310.0, 7131L),
         46.0 + 6.0 * this.noise.noise(var1 / 130.0, var3 / 130.0, 7133L),
         AlpineLayout.smooth(-0.65, -0.22, var7)
      );
      var92 = AlpineLayout.lerp(var109, var92, var9);
      int var113 = Integer.MIN_VALUE;
      byte var53 = 0;
      if (var5 && this.mountains != null && var7 > -0.04) {
         AlpineMountains.Lake var116 = this.mountains.lakeAt(var1, var3);
         if (var116 != null) {
            double var55 = var1 - var116.cx();
            double var57 = var3 - var116.cz();
            double var59 = Math.hypot(var55, var57);
            double var61 = Math.atan2(var57, var55);
            double var63 = var116.radius()
               * (
                  1.0
                     + 0.24 * this.noise.noise(Math.cos(var61) * 1.6 + var116.shoreSeed(), Math.sin(var61) * 1.6, 7751L)
                     + 0.08 * this.noise.noise(Math.cos(var61) * 5.0 + var116.shoreSeed(), Math.sin(var61) * 5.0, 7753L)
               );
            double var65 = var59 / var63;
            if (var65 < 1.0) {
               double var127 = var116.pond()
                  ? 2.5 + 1.5 * this.noise.noise(var1 / 9.0, var3 / 9.0, 7755L)
                  : 5.0 + 7.0 * AlpineLayout.smooth(24.0, 50.0, var116.radius());
               var92 = Math.min(var92, (double)var116.level() - 0.6 - var127 * Math.pow(1.0 - var65 * var65, 0.8));
               var113 = var116.level();
               var53 |= 1;
               if (var65 > 0.72) {
                  var53 |= 2;
               }
            } else if (this.noise.version() >= 21) {
               double var128 = (var65 - 1.0) * var63;
               double var69 = Math.min(var92, (double)var116.level() + 1.05 + var128 * 1.15);
               var69 = Math.max(var69, (double)var116.level() + 1.05 - Math.max(0.0, var128 - 2.5) * 0.85);
               var92 += (var69 - var92) * (1.0 - AlpineLayout.smooth(0.0, 20.0, var128));
               if (var128 < 4.5) {
                  var53 |= 2;
               }

               if (var116.pond() && var128 < 3.5 && Math.abs(Math.IEEEremainder(var61 - var116.lowAngle(), Math.PI * 2)) < 0.55) {
                  var53 |= 4;
               }
            } else {
               double var129 = (var65 - 1.0) * var63;
               var92 = Math.max(var92, (double)var116.level() + 1.05 - Math.max(0.0, var129 - 2.5) * 0.85);
               if (var129 < 4.5) {
                  var53 |= 2;
               }

               if (var116.pond() && var129 < 3.5 && Math.abs(Math.IEEEremainder(var61 - var116.lowAngle(), Math.PI * 2)) < 0.55) {
                  var53 |= 4;
               }
            }

            if (var116.pond()) {
               var53 |= 8;
            }
         }
      }

      boolean var117 = var7 < 0.1 && var92 < 64.0;
      int var118 = var117 ? 64 : var113;
      double var120 = 0.5 + 0.5 * this.noise.noise(var1 / 410.0, var3 / 410.0, 7151L);
      double var121 = this.noise.version() >= 17
         ? 372.0 + 66.0 * this.noise.noise(var1 / 350.0, var3 / 350.0, 7153L) - var90
         : 335.0 + 60.0 * this.noise.noise(var1 / 350.0, var3 / 350.0, 7153L);
      double var122 = 0.0;
      double var123 = var121;
      if (this.noise.version() >= 19 && var39.height > 1.0) {
         var122 = AlpineLayout.smooth(20.0, 150.0, var39.height);
         if (this.noise.version() >= 21) {
            var121 += var122 * switch (var39.type) {
               case 1 -> 430.0;
               case 2 -> -60.0;
               case 3 -> 70.0;
               default -> 90.0;
            };
         } else if (this.noise.version() >= 20) {
            var121 += var122 * switch (var39.type) {
               case 1 -> 250.0;
               case 2 -> -40.0;
               case 3 -> 60.0;
               default -> 80.0;
            };
         } else {
            var121 += var122 * switch (var39.type) {
               case 1 -> 110.0;
               case 2 -> -30.0;
               case 3 -> 45.0;
               default -> 70.0;
            };
         }
      }

      double var124 = AlpineLayout.smooth(-0.3, 0.18, this.noise.noise(var1 / 295.0, var3 / 295.0, 7155L));
      double var125 = (0.52 + 0.46 * var124) * (1.0 - var103 * 0.88) * (1.0 - AlpineLayout.smooth(var121 + 10.0, var121 + 75.0, var92));
      if (this.noise.version() >= 19) {
         var123 = var121 - 85.0 + 35.0 * this.noise.noise(var1 / 520.0, var3 / 520.0, 7641L);
         var125 *= 1.0 - AlpineLayout.smooth(var123 - 55.0, var123 + 15.0, var92);
         if (var122 > 0.0) {
            double var130 = AlpineLayout.smooth(
               -0.18, 0.22, this.noise.noise(var1 / 160.0, var3 / 160.0, 7643L) + 0.4 * this.noise.noise(var1 / 55.0, var3 / 55.0, 7645L)
            );
            double var133 = 1.0 - AlpineLayout.smooth(0.22, var39.type == 1 ? 0.66 : 0.5, var39.up());
            var125 *= AlpineLayout.lerp(1.0, var130 * var133, var122);
            if (this.noise.version() >= 20) {
               var125 *= 1.0
                  - var122
                     * AlpineLayout.smooth(-0.3, -0.55, var33)
                     * AlpineLayout.smooth(0.15, 0.35, var39.up())
                     * (1.0 - AlpineLayout.smooth(0.8, 0.95, var39.up()));
            }
         }

         var125 *= 1.0 - 0.97 * var100;
         if (var53 != 0) {
            var125 *= (var53 & 1) != 0 ? 0.0 : 0.35;
         }
      }

      double var131 = this.noise.version() >= 16 ? this.noise.weave(var1, var3) : 0.0;
      double var134 = var120 + var131;
      double var135 = var125 + var131 * 0.9;
      double var136 = var99 + var131 * 0.7;
      double var137 = var103 + var131 * 1.1;
      double var138 = var19 + var131;
      double var80 = var121 + var131 * 70.0;
      int var82;
      if (var117) {
         var82 = var92 < 25.0 ? 19 : 18;
      } else if (var7 < -0.04 && var92 < 72.0) {
         var82 = 20;
      } else if (var92 > var80) {
         var82 = var135 > 0.3 && var92 < var80 + 65.0 ? 9 : 5;
      } else if (var136 > 0.42) {
         var82 = 14;
      } else if (var92 > var80 - 60.0) {
         var82 = var138 > 0.55 ? 11 : 4;
      } else if (var137 > 0.62) {
         var82 = var134 > 0.65 ? 10 : (var134 > 0.5 ? 15 : 1);
      } else if (var134 > 0.6 && var17 < 0.28) {
         var82 = 16;
      } else if (var134 > 0.53 && var135 > 0.58) {
         var82 = 13;
      } else if (var135 > 0.55) {
         var82 = switch (this.noise.woodland((int)var1, (int)var3)) {
            case 0 -> 7;
            case 1 -> 8;
            case 2 -> 6;
            default -> 2;
         };
      } else {
         var82 = var17 > 0.35 ? 3 : 1;
      }

      if (var117 || var82 == 20) {
         var125 = 0.0;
      }

      if (this.noise.version() >= 15 && !var117) {
         int var83 = this.noise.version() >= 16 ? this.extra(var1, var3, var82, var92, var121, var134, var138, var17, var131) : var82;
         if (var83 != var82) {
            var82 = var83;

            var125 = Math.min(var125, switch (var83) {
               case 32 -> 0.04;
               default -> 0.34;
               case 34 -> 0.11;
               case 35 -> 0.32;
               case 36 -> 0.26;
               case 37 -> 0.52;
               case 38 -> 0.22;
            });
         } else {
            var82 = this.district(var1, var3, var82, var92, var121, var120, var19, var17);
            if (AlpineLayout.ground(var82)) {
               var125 = Math.min(var125, switch (var82) {
                  case 28 -> 0.09;
                  default -> 0.07;
                  case 30 -> 0.05;
                  case 31 -> 0.2;
               });
            } else {
               var82 = this.accent(var1, var3, var82, var92, var121);
            }
         }
      }

      int var139 = 0;
      if (this.noise.version() >= 19 && !var117) {
         boolean var84 = var82 == 5 || var82 == 9 || var82 == 14 || var82 == 20 || AlpineLayout.sea(var82);
         if (var100 > 0.5 && !var84 && var92 < var121 - 40.0 && var122 < 0.35) {
            double var85 = this.fieldKind(var1, var3) + var131 * 0.6;
            var82 = var85 < -0.22 ? 29 : (var85 < 0.12 ? (var92 > var121 - 190.0 ? 4 : 1) : 15);
            var125 = Math.min(var125, 0.04);
         } else if (var122 > 0.3 && var92 > var123 - 10.0 && !var84 && var82 != 32 && var82 != 30) {
            var82 = var92 > var121 ? var82 : (var39.type == 1 ? 4 : 11);
         }

         if (var122 > 0.0) {
            var139 = var39.type | (int)Math.round(var122 * 63.0) << 3 | (int)Math.round(var39.arm * 63.0) << 9 | (int)Math.round(var39.up() * 63.0) << 15;
         }

         if (this.noise.version() >= 20) {
            var139 |= (int)Math.round((var33 + 1.0) * 15.5) << 21 | (int)Math.round(var35 * 3.0) << 26 | var53 << 28;
         }
      }

      return new AlpineLayout.Sample(var92, var118, var117 ? 64.0 : var92, 9999.0, 0.0, var120, var125, var121, 0.0, var82, var139, 0.0);
   }

   public double fieldKind(double var1, double var3) {
      return this.noise.noise(var1 / 1100.0, var3 / 1100.0, 7661L) + 0.3 * this.noise.noise(var1 / 320.0, var3 / 320.0, 7663L);
   }

   AlpineDrainage.Peak peak(double var1, double var3) {
      double var5 = var1 + 70.0 * this.noise.noise(var1 / 410.0, var3 / 410.0, 7611L);
      double var7 = var3 + 70.0 * this.noise.noise(var1 / 410.0 + 17.1, var3 / 410.0 - 9.3, 7613L);
      int var9 = Math.floorDiv((int)Math.floor(var5), 1150);
      int var10 = Math.floorDiv((int)Math.floor(var7), 1150);
      double var11 = 0.0;
      double var13 = 0.0;
      double var15 = 0.0;
      double var17 = 1.0;
      int var19 = 0;
      boolean var20 = this.noise.version() >= 20;
      int var21 = var20 ? 2 : 1;

      for (int var22 = -var21; var22 <= var21; var22++) {
         for (int var23 = -var21; var23 <= var21; var23++) {
            long var24 = this.noise.hash(var9 + var22, var10 + var23, 7621L);
            if (!(unit(var24) > 0.52)) {
               double var26 = ((double)(var9 + var22) + 0.16 + 0.68 * unit(AlpineLayout.mix(var24 + 1L))) * 1150.0;
               double var28 = ((double)(var10 + var23) + 0.16 + 0.68 * unit(AlpineLayout.mix(var24 + 2L))) * 1150.0;
               if (var20 || !(this.continental(var26, var28) < 0.14)) {
                  double var30 = unit(AlpineLayout.mix(var24 + 3L));
                  double var32 = unit(AlpineLayout.mix(var24 + 4L));
                  int var34 = var30 < 0.34 ? 1 : (var30 < 0.58 ? 2 : (var30 < 0.8 ? 3 : 4));

                  double var35 = switch (var34) {
                     case 1 -> 150.0 + 120.0 * var32;
                     case 2 -> 320.0 + 170.0 * var32;
                     case 3 -> 230.0 + 140.0 * var32;
                     default -> 200.0 + 140.0 * var32;
                  };
                  double var37 = var35 * ((var34 != 2 && var34 != 3 ? 2.0 : 1.7) + 0.8 * unit(AlpineLayout.mix(var24 + 5L)));
                  double var39 = var5 - var26;
                  double var41 = var7 - var28;
                  double var43 = Math.hypot(var39, var41);
                  if (!(var43 > var37 * (var20 ? 1.44 : 1.3)) && (!var20 || !(this.continental(var26, var28) < 0.14))) {
                     double var45 = Math.atan2(var41, var39);
                     int var47 = 3 + (int)(unit(AlpineLayout.mix(var24 + 6L)) * 3.0);
                     double var48 = unit(AlpineLayout.mix(var24 + 7L)) * Math.PI * 2.0;
                     double var50 = 0.0;

                     for (int var52 = 0; var52 < var47; var52++) {
                        double var53 = var48 + (double)var52 * Math.PI * 2.0 / (double)var47 + (unit(AlpineLayout.mix(var24 + 8L + (long)var52)) - 0.5) * 0.9;
                        double var55 = Math.abs(Math.IEEEremainder(var45 - var53, Math.PI * 2));
                        var50 = Math.max(var50, Math.exp(-(var55 / 0.3) * (var55 / 0.3)));
                     }

                     double var68 = Math.cos(var45);
                     double var54 = Math.sin(var45);
                     double var56 = var43
                        / (
                           var37
                              * (0.5 + 0.8 * var50)
                              * (
                                 1.0
                                    + 0.1
                                       * (
                                          var20
                                             ? this.noise.noise(var43 / 260.0 + unit(AlpineLayout.mix(var24 + 13L)) * 50.0 + 1.3 * var68, 1.3 * var54, 7625L)
                                             : this.noise.noise(var43 / 260.0 + unit(AlpineLayout.mix(var24 + 13L)) * 50.0, var45 * 1.3, 7625L)
                                       )
                              )
                        );
                     if (!(var56 >= 1.0)) {
                        double var58 = Math.pow(1.0 - var56, 2.0);
                        var58 += var50
                           * var50
                           * 0.16
                           * Math.exp(-((var56 - 0.42) / 0.11) * ((var56 - 0.42) / 0.11))
                           * (0.6 + 0.8 * unit(AlpineLayout.mix(var24 + 15L)));
                        double var60 = 1.0
                           - Math.abs(
                              var20
                                 ? this.noise
                                    .noise(9.0 * var68 + unit(AlpineLayout.mix(var24 + 14L)) * 40.0 + var43 / 340.0, 9.0 * var54 + var43 / 120.0, 7631L)
                                 : this.noise.noise(var45 * 9.0 + unit(AlpineLayout.mix(var24 + 14L)) * 40.0, var43 / 120.0, 7631L)
                           );
                        double var62 = (1.0 - var50) * 4.0 * var56 * (1.0 - var56) * var60 * var60;
                        double var64 = var35 * (var58 - 0.08 * var62);
                        if (var64 > var11) {
                           var13 = var11;
                           var11 = var64;
                           var19 = var34;
                           var15 = var50;
                           var17 = var56;
                        } else if (var64 > var13) {
                           var13 = var64;
                        }
                     }
                  }
               }
            }
         }
      }

      if (var11 <= 0.0) {
         return AlpineDrainage.Peak.NONE;
      } else {
         double var66 = var20 ? Math.min(45.0, 4.0 * var13) : 45.0;
         if (var66 <= 0.0) {
            return new AlpineDrainage.Peak(var11, var19, var15, var17);
         } else {
            double var67 = Math.max(0.0, var66 - (var11 - var13));
            return new AlpineDrainage.Peak(var11 + var67 * var67 / (4.0 * var66), var19, var15, var17);
         }
      }
   }

   private int district(double var1, double var3, int var5, double var6, double var8, double var10, double var12, double var14) {
      double var16 = this.noise.district(var1, var3);
      boolean var18 = var6 < var8 - 20.0;
      if (!(var12 > 0.6) || !(var16 > 0.2) || !var18 || var5 != 11 && var5 != 4 && var5 != 3) {
         if (!(var16 > 0.33) || !var18 || !(var6 > var8 - 260.0) || !(var10 > 0.34) || !(var10 < 0.76) || var5 != 4 && var5 != 11 && var5 != 1 && var5 != 3) {
            if (!(var16 < -0.33) || !(var10 < 0.47) || !(var6 < var8 - 150.0) || var5 != 1 && var5 != 15 && var5 != 3 && var5 != 2) {
               return !(var16 < -0.2) || !(var10 > 0.69) || !(var14 < 0.24) || var5 != 16 && var5 != 10 && var5 != 13 && var5 != 12 ? var5 : 31;
            } else {
               return 29;
            }
         } else {
            return 28;
         }
      } else {
         return 30;
      }
   }

   private int extra(double var1, double var3, int var5, double var6, double var8, double var10, double var12, double var14, double var16) {
      double var18 = this.noise.region(var1, var3) + var16 * 2.2;
      if (!(var18 > 0.24) || !(var6 > var8 - 115.0) || !(var6 < var8 + 8.0) || !(var12 < 0.72) || var5 != 4 && var5 != 11 && var5 != 3 && var5 != 9) {
         if (!(var18 > 0.33) || !(var12 > 0.42) || !(var6 < var8 - 80.0) || var5 != 1 && var5 != 3 && var5 != 15 && var5 != 14) {
            if (!(var18 < -0.27) || !(var10 > 0.42) || !(var10 < 0.73) || !(var6 < var8 - 170.0) || var5 != 1 && var5 != 2 && var5 != 8 && var5 != 15) {
               if (!(var18 < -0.35)
                  || !(var10 > 0.62)
                  || !(var14 < 0.3)
                  || !(var6 < var8 - 220.0)
                  || var5 != 16 && var5 != 10 && var5 != 13 && var5 != 12 && var5 != 1) {
                  if (this.noise.version() >= 17) {
                     if (var18 > 0.1 && var18 < 0.3 && var6 > var8 - 300.0 && var6 < var8 - 90.0 && (var5 == 3 || var5 == 4 || var5 == 1 || var5 == 2)) {
                        return 36;
                     }

                     if (var18 < -0.14
                        && var18 > -0.32
                        && var10 > 0.52
                        && var14 < 0.4
                        && var6 < var8 - 260.0
                        && (var5 == 12 || var5 == 13 || var5 == 7 || var5 == 16)) {
                        return 37;
                     }

                     if (var18 > 0.3 && var10 < 0.58 && var6 > var8 - 260.0 && var6 < var8 - 70.0 && (var5 == 6 || var5 == 2 || var5 == 1 || var5 == 3)) {
                        return 38;
                     }
                  }

                  return var5;
               } else {
                  return 35;
               }
            } else {
               return 33;
            }
         } else {
            return 34;
         }
      } else {
         return 32;
      }
   }

   private int accent(double var1, double var3, int var5, double var6, double var8) {
      if (this.noise.accent(var1, var3) < 0.3) {
         return var5;
      } else {
         double var10 = this.noise.noise(var1 / 1700.0 + 31.7, var3 / 1700.0 - 17.3, 7403L);

         return switch (var5) {
            case 1 -> var10 < -0.25 ? 27 : var5;
            case 2 -> var10 > 0.2 ? 27 : (var10 > -0.3 ? 24 : 25);
            case 3 -> var6 > var8 - 230.0 && var6 < var8 - 6.0 ? 26 : (var10 < -0.05 ? 27 : var5);
            case 4 -> var6 > var8 - 230.0 && var6 < var8 - 6.0 ? 26 : var5;
            default -> var5;
            case 6 -> var10 > 0.05 ? 24 : 25;
            case 7, 13 -> var10 > -0.15 ? 25 : 24;
            case 8 -> var10 > -0.35 ? 24 : 27;
            case 12, 16 -> var10 > 0.25 ? 25 : var5;
         };
      }
   }

   public AlpineLayout.Sample sample(double var1, double var3) {
      if (this.noise.version() >= 12) {
         AlpineLayout.Sample var79 = this.natural(var1, var3);
         return this.falls == null ? var79 : this.falls.apply(var1, var3, var79);
      } else {
         AlpineWatershed.Water var5 = this.water(var1, var3);
         boolean var6 = this.noise.version() >= 5;
         double var7 = this.continental(var1, var3);
         double var9 = AlpineLayout.smooth(-0.24, 0.1, var7);
         double var11 = AlpineLayout.smooth(-0.08, 0.13, var7);
         double var13 = AlpineLayout.lerp(64.0, var5.level(), var11);
         double var15 = var6 ? 1080.0 : 1750.0;
         double var17 = var1 + 230.0 * this.noise.noise(var1 / var15, var3 / var15, 7091L);
         double var19 = var3 + 230.0 * this.noise.noise(var1 / var15, var3 / var15, 7093L);
         double var21 = AlpineLayout.smooth(0.14, 0.48, this.noise.noise(var17 / (double)(var6 ? 1050 : 1580), var19 / (double)(var6 ? 1050 : 1580), 7101L));
         double var23 = AlpineLayout.smooth(0.02, 0.38, this.noise.noise(var1 / (double)(var6 ? 410 : 570), var3 / (double)(var6 ? 410 : 570), 7103L));
         double var25 = Math.pow(
            1.0 - Math.abs(this.noise.fbm(var1 / (double)(var6 ? 330 : 435), var3 / (double)(var6 ? 330 : 435), 7105L, 3)), var6 ? 2.35 : 2.8
         );
         double var27 = AlpineLayout.smooth(0.48, 0.76, this.noise.noise(var17 / 3200.0, var19 / 3200.0, 7199L));
         double var29 = var21 * ((double)(var6 ? 98 : 145) + (double)(var6 ? 205 : 285) * var25) * (1.0 + var27 * (var6 ? 1.05 : 0.65));
         if (this.noise.version() >= 10) {
            double var31 = this.noise.fbm(var1 / 330.0, var3 / 330.0, 7105L, 3);
            double var33 = Math.pow(Math.max(0.0, 1.0 - var31 * var31), 2.8);
            double var35 = 0.72 + 0.28 * AlpineLayout.smooth(-0.35, 0.4, this.noise.noise(var1 / 210.0, var3 / 210.0, 7221L));
            var29 = var21 * (62.0 + 142.0 * var33) * var35 * (1.0 + var27 * 0.85);
         }

         double var80 = (double)(var6 ? 9 : 12)
            + (double)(var6 ? 27 : 36)
               * AlpineLayout.smooth(-0.15, 0.45, this.noise.noise(var1 / (double)(var6 ? 620 : 980), var3 / (double)(var6 ? 620 : 980), 7095L));
         double var81 = this.potential(var1, var3) + 16.0 + var80 * this.noise.fbm(var1 / 180.0, var3 / 180.0, 7107L, 3) + var29;
         var81 += var21
            * var23
            * (
               38.0 * Math.pow(1.0 - Math.abs(this.noise.noise(var1 / 69.0, var3 / 69.0, 7109L)), 5.0)
                  - 12.0 * Math.abs(this.noise.noise(var1 / 31.0, var3 / 31.0, 7111L))
            );
         double var88 = AlpineLayout.smooth(0.23, 0.51, this.noise.noise(var17 / (double)(var6 ? 820 : 1280), var19 / (double)(var6 ? 820 : 1280), 7113L))
            * (1.0 - var21);
         var81 += var88 * 120.0 * Math.pow(AlpineLayout.smooth(-0.2, 0.55, this.noise.noise(var1 / 140.0, var3 / 140.0, 7115L)), 2.0);
         double var37 = AlpineLayout.smooth(-0.04, 0.31, this.noise.noise(var1 / (double)(var6 ? 510 : 820), var3 / (double)(var6 ? 510 : 820), 7117L))
            * (1.0 - var21)
            * (1.0 - var88);
         var81 = AlpineLayout.lerp(var81, this.potential(var1, var3) + 12.0 + 9.0 * this.noise.noise(var1 / 185.0, var3 / 185.0, 7119L), var37 * 0.65);
         if (var6) {
            double var39 = (1.0 - var37 * 0.82) * (0.45 + 0.55 * var23);
            var81 += var39 * (5.5 * this.noise.noise(var1 / 46.0, var3 / 46.0, 7161L) + 2.2 * this.noise.noise(var1 / 17.0, var3 / 17.0, 7163L));
         }

         if (var81 > 780.0) {
            var81 = 780.0 + 100.0 * Math.tanh((var81 - 780.0) / 100.0);
         }

         double var89 = AlpineLayout.lerp(
            -38.0 + 12.0 * this.noise.noise(var1 / 310.0, var3 / 310.0, 7131L),
            46.0 + 6.0 * this.noise.noise(var1 / 130.0, var3 / 130.0, 7133L),
            AlpineLayout.smooth(-0.65, -0.22, var7)
         );
         var81 = AlpineLayout.lerp(var89, var81, var9);
         boolean var43 = this.noise.version() >= 7;
         double var44 = 4.0 + Math.min(9.0, var5.width() * 0.28) + 3.0 * (0.5 + 0.5 * this.noise.noise(var1 / 95.0, var3 / 95.0, 7121L));
         if (var43) {
            var44 += 5.0 + 5.0 * AlpineLayout.smooth(-0.3, 0.4, this.noise.noise(var1 / 57.0, var3 / 57.0, 7171L));
         }

         double var46 = AlpineLayout.smooth(var5.width(), var5.width() + var44, var5.distance());
         double var48 = 1.25 + 1.75 * AlpineLayout.smooth(-0.3, 0.4, this.noise.noise(var1 / 170.0, var3 / 170.0, 7123L));
         double var50 = AlpineLayout.lerp(64.0, var5.valleyLevel(), var11) + var48 + 2.0 * this.noise.noise(var1 / 78.0, var3 / 78.0, 7124L);
         var81 = AlpineLayout.lerp(var81, Math.min(var81, var50), var5.influence() * (1.0 - var21 * 0.35) * var11);
         if (var43) {
            double var52 = Math.max(0.0, var5.distance() - var5.width());
            double var54 = AlpineLayout.smooth(0.52, 0.75, this.noise.noise(var1 / 640.0, var3 / 640.0, 7173L)) * var21;
            double var56 = this.noise.version() >= 10
               ? AlpineLayout.lerp(var13, Math.max(var13, var5.valleyLevel()), AlpineLayout.smooth(3.0, 38.0, var52))
               : var13;
            if (this.noise.version() >= 11) {
               double var58 = Math.min(Math.max(0.0, var5.valleyLevel() - var13), var52 * 0.18);
               var56 = var13 + var58 * AlpineLayout.smooth(3.0, 38.0, var52);
            }

            double var93 = var56 + 1.4 + var52 * (0.24 + var54 * 0.65) + var52 * var52 * 0.0018;
            double var60 = 1.0 - AlpineLayout.smooth(85.0, 220.0, var52);
            var81 = AlpineLayout.lerp(var81, Math.min(var81, var93), var60 * var11);
         }

         double var90 = 1.0 - AlpineLayout.smooth(var5.width() + var44, var5.width() + var44 + 9.0, var5.distance());
         double var91 = AlpineLayout.lerp(var81, Math.max(var81, var13 + 1.0), var90 * var11);
         double var92 = (var5.lake() ? 7.0 : 1.8 + Math.min(3.0, var5.width() * 0.1)) + 1.0 * this.noise.noise(var1 / 17.0, var3 / 17.0, 7125L);
         if (this.noise.version() >= 9 && var5.lake()) {
            double var94 = 1.0 - AlpineLayout.smooth(var5.width() * 0.12, var5.width(), var5.distance());
            var92 = 1.2 + var94 * (1.5 + Math.min(1.4, var5.width() * 0.014)) + 0.35 * this.noise.noise(var1 / 21.0, var3 / 21.0, 7205L);
         }

         if (var43 && !var5.lake()) {
            double var95 = 1.0 - AlpineLayout.smooth(var5.width() * 0.2, var5.width(), var5.distance());
            var92 = 1.1 + var95 * (1.1 + Math.min(2.0, var5.width() * 0.05)) + this.noise.noise(var1 / 11.0, var3 / 11.0, 7175L) * 0.55;
         }

         double var96 = AlpineLayout.lerp(var13 - var92, var91, var46);
         var81 = AlpineLayout.lerp(Math.min(var81, var96), var96, var11);
         int var97 = (int)Math.floor(var1 / 40.0);
         int var61 = (int)Math.floor(var3 / 40.0);
         long var62 = this.key(var97, var61, 7141L);
         double var64 = 0.0;
         double var66 = var5.distance() < var5.width() + 50.0 ? 0.26 : 0.055;
         if (unit(var62) < var66 && (this.noise.version() < 10 || !var5.lake() || var5.distance() > var5.width() - 4.0)) {
            double var68 = (double)(var97 * 40 + 10) + 20.0 * unit(AlpineLayout.mix(var62 + 1L));
            double var70 = (double)(var61 * 40 + 10) + 20.0 * unit(AlpineLayout.mix(var62 + 2L));
            double var72 = var43 ? 1.2 + 2.0 * unit(AlpineLayout.mix(var62 + 3L)) : 3.0 + 5.0 * unit(AlpineLayout.mix(var62 + 3L));
            double var74 = var43 ? 1.1 + 2.0 * unit(AlpineLayout.mix(var62 + 4L)) : 3.0 + 5.0 * unit(AlpineLayout.mix(var62 + 4L));
            if (this.noise.version() >= 8 && unit(AlpineLayout.mix(var62 + 11L)) < 0.025) {
               var72 *= 2.3;
               var74 *= 2.1;
            }

            double var76 = (var1 - var68) * (var1 - var68) / (var72 * var72) + (var3 - var70) * (var3 - var70) / (var74 * var74);
            if (this.noise.version() >= 8) {
               var76 *= 1.0 + 0.2 * this.noise.noise(var1 / 2.1, var3 / 2.1, 7181L);
            }

            if (var76 < 1.0) {
               var64 = Math.pow(1.0 - var76, 0.6) * (var43 ? 1.2 + 2.6 * unit(AlpineLayout.mix(var62 + 5L)) : 4.0 + 9.0 * unit(AlpineLayout.mix(var62 + 5L)));
               var81 += var64;
            }
         }

         if (var5.style() == 5
            && var5.distance() < var5.width()
            && this.noise.noise(var1 / 29.0, var3 / 29.0, 7143L) > 0.4
            && (this.noise.version() < 10 || var5.distance() > var5.width() * 0.35)) {
            double var98 = AlpineLayout.smooth(0.4, 0.64, this.noise.noise(var1 / 29.0, var3 / 29.0, 7143L)) * (var43 ? 2.6 : 8.0);
            var81 += var98;
            var64 += var98;
         }

         if (this.noise.version() >= 10 && !var5.lake() && var5.distance() < Math.max(1.0, var5.width() * 0.55)) {
            var81 = Math.min(var81, var13 - 1.6);
         }

         int var99 = var5.distance() < var5.width() + var44 && var81 < Math.floor(var13) ? (int)Math.floor(var13) : Integer.MIN_VALUE;
         boolean var69 = var7 < 0.1 && var81 < 64.0;
         if (var69) {
            var99 = 64;
         }

         double var100 = 0.5 + 0.5 * this.noise.noise(var1 / 410.0, var3 / 410.0, 7151L);
         double var101 = 335.0 + 60.0 * this.noise.noise(var1 / 350.0, var3 / 350.0, 7153L);
         double var102 = AlpineLayout.smooth(-0.3, 0.18, this.noise.noise(var1 / 295.0, var3 / 295.0, 7155L));
         double var103 = (0.52 + 0.46 * var102) * (1.0 - var37 * 0.88);
         var103 *= AlpineLayout.smooth(var5.width() - 1.0, var5.width() + 13.0, var5.distance())
            * (1.0 - AlpineLayout.smooth(var101 + 10.0, var101 + 75.0, var81));
         int var78;
         if (var69) {
            var78 = var81 < 25.0 ? 19 : 18;
         } else if (var7 < -0.04 && var81 < 72.0) {
            var78 = 20;
         } else if (var99 > (int)Math.floor(var81)) {
            var78 = 0;
         } else if (var81 > var101) {
            var78 = var103 > 0.3 && var81 < var101 + 65.0 ? 9 : 5;
         } else if (var88 > 0.42) {
            var78 = 14;
         } else if (var81 > var101 - 60.0) {
            var78 = var23 > 0.55 ? 11 : 4;
         } else if (var5.lake() && var5.distance() < var5.width() + 36.0) {
            var78 = 17;
         } else if (var37 > 0.62) {
            var78 = var100 > 0.65 ? 10 : (var100 > 0.5 ? 15 : 1);
         } else if (var100 > 0.6 && var21 < 0.28) {
            var78 = 16;
         } else if (var5.distance() < var5.width() + 100.0 && var103 > 0.5) {
            var78 = 12;
         } else if (var100 > 0.53 && var103 > 0.58) {
            var78 = 13;
         } else if (var103 > 0.55) {
            var78 = switch (this.noise.woodland((int)var1, (int)var3)) {
               case 0 -> 7;
               case 1 -> 8;
               case 2 -> 6;
               default -> 2;
            };
         } else {
            var78 = var21 > 0.35 ? 3 : 1;
         }

         if (var69 || var78 == 20) {
            var103 = 0.0;
         }

         return new AlpineLayout.Sample(
            var81,
            var99,
            var69 ? 64.0 : var13,
            var69 ? 0.0 : var5.distance(),
            var69 ? 9999.0 : var5.width(),
            var100,
            var103,
            var101,
            var69 ? 0.0 : var5.fall(),
            var78,
            var5.style(),
            var64
         );
      }
   }

   private static record Node(int x, int z, double px, double pz, double level, long hash) {
   }

   public static record Peak(double height, int type, double arm, double t, double green) {
      public static final int GREEN = 1;
      public static final int SNOW = 2;
      public static final int STONE = 3;
      public static final int ALPINE = 4;
      public static final AlpineDrainage.Peak NONE = new AlpineDrainage.Peak(0.0, 0, 0.0, 1.0);

      public Peak(double var1, int var3, double var4, double var6) {
         this(var1, var3, var4, var6, var3 == 1 ? 1.0 : 0.0);
      }

      public double up() {
         return 1.0 - Math.min(1.0, this.t);
      }
   }

   private static final class Reach {
      final AlpineDrainage.Node a;
      final AlpineDrainage.Node b;
      final double[] x = new double[17];
      final double[] z = new double[17];
      final double[] length = new double[17];
      final double widthA;
      final double widthB;
      final double drop;
      final double lip;
      final double spread;
      final int style;
      boolean natural;
      boolean scenic;
      boolean blended;

      Reach(AlpineDrainage.Node var1, AlpineDrainage.Node var2, double var3, double var5, boolean var7) {
         this.scenic = var7;
         this.a = var1;
         this.b = var2;
         this.lip = 0.27 + AlpineDrainage.unit(AlpineLayout.mix(var1.hash + 81L)) * 0.35;
         this.spread = 0.12 + AlpineDrainage.unit(AlpineLayout.mix(var1.hash + 82L)) * 0.18;
         this.drop = var1.level - var2.level;
         this.style = (int)(AlpineDrainage.unit(AlpineLayout.mix(var1.hash + 19L)) * 6.0);
         this.widthA = Math.min(48.0, 1.5 + Math.sqrt(var3) * 5.0)
            * (0.7 + AlpineDrainage.unit(AlpineLayout.mix(var1.hash + 21L)) * 0.6)
            * (var7 ? 0.55 + 0.45 * AlpineLayout.smooth(0.3, 2.0, var3) : 1.0);
         this.widthB = Math.min(54.0, 1.5 + Math.sqrt(var5) * 5.0)
            * (0.7 + AlpineDrainage.unit(AlpineLayout.mix(var2.hash + 21L)) * 0.6)
            * (var7 ? 0.55 + 0.45 * AlpineLayout.smooth(0.3, 2.0, var5) : 1.0);
         double var8 = var2.px - var1.px;
         double var10 = var2.pz - var1.pz;
         double var12 = Math.hypot(var8, var10);
         double var14 = (AlpineDrainage.unit(AlpineLayout.mix(var1.hash + 23L)) - 0.5)
            * var12
            * (0.3 + 0.55 * AlpineDrainage.unit(AlpineLayout.mix(var1.hash + 24L)));

         for (int var16 = 0; var16 <= 16; var16++) {
            double var17 = (double)var16 / 16.0;
            double var19 = Math.sin(var17 * Math.PI) * var14
               + Math.sin(var17 * Math.PI * 2.0) * var12 * (0.02 + 0.12 * AlpineDrainage.unit(AlpineLayout.mix(var1.hash + 25L)));
            this.x[var16] = AlpineLayout.lerp(var1.px, var2.px, var17) - var10 / var12 * var19;
            this.z[var16] = AlpineLayout.lerp(var1.pz, var2.pz, var17) + var8 / var12 * var19;
            if (var16 > 0) {
               this.length[var16] = this.length[var16 - 1] + Math.hypot(this.x[var16] - this.x[var16 - 1], this.z[var16] - this.z[var16 - 1]);
            }
         }
      }

      double level(double var1) {
         if (this.natural) {
            double var3 = AlpineDrainage.unit(AlpineLayout.mix(this.a.hash + 917L));
            if (var3 < (this.blended ? 0.2 : (this.scenic ? 0.34 : 0.52))) {
               return AlpineLayout.lerp(this.a.level, this.b.level, var1);
            }

            if (!(var3 > (this.blended ? 0.52 : (this.scenic ? 0.67 : 0.9))) || !(this.drop > (double)(this.scenic ? 12 : 14))) {
               double var5 = Math.min(this.drop * 0.8, 6.0);
               double var7 = 0.38 * AlpineLayout.smooth(this.lip - 0.002, this.lip + 0.002, var1)
                  + 0.34 * AlpineLayout.smooth(this.lip + 0.088, this.lip + 0.092, var1)
                  + 0.28 * AlpineLayout.smooth(this.lip + 0.188, this.lip + 0.192, var1);
               return this.a.level - (this.drop - var5) * var1 - var5 * var7;
            }
         }
         double var9 = switch (this.style) {
            case 0 -> AlpineLayout.smooth(0.05, 0.95, var1);
            case 1 -> this.scenic && this.drop > 30.0
            ? 0.56 * AlpineLayout.smooth(this.lip - 0.004, this.lip + 0.004, var1) + 0.44 * AlpineLayout.smooth(this.lip + 0.125, this.lip + 0.135, var1)
            : AlpineLayout.smooth(this.lip - 0.003, this.lip + 0.003, var1);
            case 2 -> 0.48 * AlpineLayout.smooth(this.lip - 0.005, this.lip + 0.005, var1)
            + 0.52 * AlpineLayout.smooth(this.lip + this.spread - 0.005, this.lip + this.spread + 0.005, var1);
            case 3, 4 -> 0.22 * AlpineLayout.smooth(0.3, 0.325, var1)
            + 0.34 * AlpineLayout.smooth(0.43, 0.45, var1)
            + 0.26 * AlpineLayout.smooth(0.57, 0.59, var1)
            + 0.18 * AlpineLayout.smooth(0.69, 0.71, var1);
            default -> 0.4 * AlpineLayout.smooth(0.39, 0.415, var1) + 0.6 * AlpineLayout.smooth(0.57, 0.59, var1);
         };
         return this.a.level - this.drop * (var1 * 0.22 + var9 * 0.78);
      }
   }

   private static record Region(int x, int z, AlpineDrainage.Reach[] reaches, AlpineDrainage.Node[] lakes) {
   }

   private static record Runoff(int x, int z, double amount) {
   }

   private static record Spill(int x, int z, AlpineDrainage.Reach[] reaches) {
   }
}
