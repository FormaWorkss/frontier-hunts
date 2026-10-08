package com.formaworks.frontierhunts.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import java.util.ArrayList;
import java.util.List;

final class ArtMesh {
   static void sweep(PoseStack var0, VertexConsumer var1, int var2, int var3, double[][] var4, int var5) {
      Pose var6 = var0.last();
      int var7 = (var4.length - 1) * 4;
      double[][] var8 = new double[var7 + 1][];
      double[] var9 = new double[var7 + 1];
      double[] var10 = new double[var7 + 1];

      for (int var11 = 0; var11 <= var7; var11++) {
         double var12 = (double)var11 / (double)var7;
         var8[var11] = sample(var4, var12);
         double[] var14 = sample(var4, Math.max(0.0, var12 - 0.001));
         double[] var15 = sample(var4, Math.min(1.0, var12 + 0.001));
         double var16 = var15[1] - var14[1];
         double var18 = var15[2] - var14[2];
         double var20 = Math.max(1.0E-4, Math.hypot(var16, var18));
         var9[var11] = -var18 / var20;
         var10[var11] = var16 / var20;
      }

      for (int var36 = 0; var36 < var7; var36++) {
         for (int var37 = 0; var37 < var5; var37++) {
            for (int var13 = 0; var13 < 4; var13++) {
               double var38 = (double)(var36 + (var13 != 1 && var13 != 2 ? 0 : 1)) / (double)var7;
               double var39 = (double)(var37 + (var13 >= 2 ? 1 : 0)) * Math.PI * 2.0 / (double)var5;
               int var40 = var36 + (var13 != 1 && var13 != 2 ? 0 : 1);
               double[] var19 = var8[var40];
               double var41 = var9[var40];
               double var22 = var10[var40];
               double var24 = Math.cos(var39);
               double var26 = Math.sin(var39);
               double var28 = var24 / Math.max(0.005, var19[3]);
               double var30 = var26 * var41 / Math.max(0.005, var19[4]);
               double var32 = var26 * var22 / Math.max(0.005, var19[4]);
               double var34 = Math.sqrt(var28 * var28 + var30 * var30 + var32 * var32);
               HuntMesh.vertex(
                  var1,
                  var6,
                  var2,
                  var3,
                  (float)(var19[0] + var24 * var19[3]),
                  (float)(var19[1] + var26 * var19[4] * var41),
                  (float)(var19[2] + var26 * var19[4] * var22),
                  (float)var38,
                  (float)((double)(var37 + (var13 >= 2 ? 1 : 0)) / (double)var5),
                  (float)(var28 / var34),
                  (float)(var30 / var34),
                  (float)(var32 / var34)
               );
            }
         }
      }
   }

   private static double[] sample(double[][] var0, double var1) {
      double var3 = var1 * (double)(var0.length - 1);
      int var5 = Math.min(var0.length - 2, (int)var3);
      double var6 = var3 - (double)var5;
      double[] var8 = new double[5];

      for (int var9 = 0; var9 < 5; var9++) {
         double var10 = var0[Math.max(0, var5 - 1)][var9];
         double var12 = var0[var5][var9];
         double var14 = var0[var5 + 1][var9];
         double var16 = var0[Math.min(var0.length - 1, var5 + 2)][var9];
         var8[var9] = 0.5
            * (
               2.0 * var12
                  + (-var10 + var14) * var6
                  + (2.0 * var10 - 5.0 * var12 + 4.0 * var14 - var16) * var6 * var6
                  + (-var10 + 3.0 * var12 - 3.0 * var14 + var16) * var6 * var6 * var6
            );
         if (var9 >= 3) {
            var8[var9] = Math.max(1.0E-4, var8[var9]);
         }
      }

      return var8;
   }

   static void profile(PoseStack var0, VertexConsumer var1, int var2, int var3, double var4, double var6, double[][] var8) {
      Pose var9 = var0.last();
      double var10 = 0.0;
      double var12 = 0.0;

      for (double[] var17 : var8) {
         var10 += var17[0];
         var12 += var17[1];
      }

      var10 /= (double)var8.length;
      var12 /= (double)var8.length;
      double var49 = Double.POSITIVE_INFINITY;
      double var50 = Double.NEGATIVE_INFINITY;
      double var18 = Double.POSITIVE_INFINITY;
      double var20 = Double.NEGATIVE_INFINITY;

      for (double[] var25 : var8) {
         var49 = Math.min(var49, var25[0]);
         var50 = Math.max(var50, var25[0]);
         var18 = Math.min(var18, var25[1]);
         var20 = Math.max(var20, var25[1]);
      }

      double var51 = Math.max(0.001, var50 - var49);
      double var52 = Math.max(0.001, var20 - var18);

      for (int var29 : new int[]{-1, 1}) {
         for (int[] var31 : triangulate(var8)) {
            for (int var35 : new int[]{0, 1, 2, 2}) {
               double[] var36 = var8[var31[var35]];
               double var37 = var10 + (var36[0] - var10) * 0.93;
               double var39 = var12 + (var36[1] - var12) * 0.93;
               point(
                  var1, var9, var2, var3, (double)var29 * var4, var37, var39, (var36[1] - var18) / var52, (var36[0] - var49) / var51, (double)var29, 0.0, 0.0
               );
            }
         }
      }

      for (int var56 : new int[]{-1, 1}) {
         for (int var57 = 0; var57 < var8.length; var57++) {
            double[] var58 = var8[var57];
            double[] var59 = var8[(var57 + 1) % var8.length];
            double var60 = var10 + (var58[0] - var10) * 0.93;
            double var61 = var12 + (var58[1] - var12) * 0.93;
            double var62 = var10 + (var59[0] - var10) * 0.93;
            double var63 = var12 + (var59[1] - var12) * 0.93;
            double var41 = var59[0] - var58[0];
            double var43 = var59[1] - var58[1];
            double var45 = Math.max(0.001, Math.hypot(var41, var43));
            point(var1, var9, var2, var3, (double)var56 * var4, var60, var61, 0.0, 0.0, (double)var56 * 0.5, var43 / var45, -var41 / var45);
            point(var1, var9, var2, var3, (double)var56 * (var4 - var6), var58[0], var58[1], 0.0, 1.0, (double)var56 * 0.5, var43 / var45, -var41 / var45);
            point(var1, var9, var2, var3, (double)var56 * (var4 - var6), var59[0], var59[1], 1.0, 1.0, (double)var56 * 0.5, var43 / var45, -var41 / var45);
            point(var1, var9, var2, var3, (double)var56 * var4, var62, var63, 1.0, 0.0, (double)var56 * 0.5, var43 / var45, -var41 / var45);
            if (var56 == 1) {
               point(var1, var9, var2, var3, var4 - var6, var58[0], var58[1], 0.0, 0.0, 0.0, var43 / var45, -var41 / var45);
               point(var1, var9, var2, var3, -var4 + var6, var58[0], var58[1], 0.0, 1.0, 0.0, var43 / var45, -var41 / var45);
               point(var1, var9, var2, var3, -var4 + var6, var59[0], var59[1], 1.0, 1.0, 0.0, var43 / var45, -var41 / var45);
               point(var1, var9, var2, var3, var4 - var6, var59[0], var59[1], 1.0, 0.0, 0.0, var43 / var45, -var41 / var45);
            }
         }
      }
   }

   static void box(PoseStack var0, VertexConsumer var1, int var2, int var3, double var4, double var6, double var8, double var10, double var12, double var14) {
      var0.pushPose();
      var0.translate(var4, var6, var8);
      profile(
         var0,
         var1,
         var2,
         var3,
         var10 / 2.0,
         Math.min(var10, var12) * 0.08,
         new double[][]{{-var12 / 2.0, -var14 / 2.0}, {var12 / 2.0, -var14 / 2.0}, {var12 / 2.0, var14 / 2.0}, {-var12 / 2.0, var14 / 2.0}}
      );
      var0.popPose();
   }

   private static void point(
      VertexConsumer var0,
      Pose var1,
      int var2,
      int var3,
      double var4,
      double var6,
      double var8,
      double var10,
      double var12,
      double var14,
      double var16,
      double var18
   ) {
      HuntMesh.vertex(var0, var1, var2, var3, (float)var4, (float)var6, (float)var8, (float)var10, (float)var12, (float)var14, (float)var16, (float)var18);
   }

   private static List<int[]> triangulate(double[][] var0) {
      ArrayList var1 = new ArrayList();

      for (int var2 = 0; var2 < var0.length; var2++) {
         var1.add(var2);
      }

      double var16 = 0.0;

      for (int var4 = 0; var4 < var0.length; var4++) {
         double[] var5 = var0[var4];
         double[] var6 = var0[(var4 + 1) % var0.length];
         var16 += var5[0] * var6[1] - var6[0] * var5[1];
      }

      double var17 = Math.signum(var16);
      ArrayList var18 = new ArrayList();

      for (int var7 = 0; var1.size() > 3 && var7 < var0.length * var0.length; var7++) {
         boolean var8 = false;

         for (int var9 = 0; var9 < var1.size(); var9++) {
            int var10 = (Integer)var1.get((var9 + var1.size() - 1) % var1.size());
            int var11 = (Integer)var1.get(var9);
            int var12 = (Integer)var1.get((var9 + 1) % var1.size());
            if (!(cross(var0[var10], var0[var11], var0[var12]) * var17 <= 1.0E-9)) {
               boolean var13 = false;

               for (int var15 : var1) {
                  if (var15 != var10
                     && var15 != var11
                     && var15 != var12
                     && cross(var0[var10], var0[var11], var0[var15]) * var17 >= 0.0
                     && cross(var0[var11], var0[var12], var0[var15]) * var17 >= 0.0
                     && cross(var0[var12], var0[var10], var0[var15]) * var17 >= 0.0) {
                     var13 = true;
                     break;
                  }
               }

               if (!var13) {
                  var18.add(new int[]{var10, var11, var12});
                  var1.remove(var9);
                  var8 = true;
                  break;
               }
            }
         }

         if (!var8) {
            break;
         }
      }

      if (var1.size() == 3) {
         var18.add(new int[]{(Integer)var1.get(0), (Integer)var1.get(1), (Integer)var1.get(2)});
      }

      return var18;
   }

   private static double cross(double[] var0, double[] var1, double[] var2) {
      return (var1[0] - var0[0]) * (var2[1] - var0[1]) - (var1[1] - var0[1]) * (var2[0] - var0[0]);
   }

   private ArtMesh() {
   }
}
