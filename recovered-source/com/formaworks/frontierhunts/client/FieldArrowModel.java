package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.hunting.ArrowSupply;
import com.formaworks.frontierhunts.hunting.ArrowTip;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

final class FieldArrowModel {
   static final int GLOW = 15728880;

   static void draw(PoseStack var0, VertexConsumer var1, int var2, boolean var3, boolean var4) {
      draw(var0, var1, var2, var3, var4, ArrowSupply.Shot.DEFAULT, false);
   }

   static ArrowSupply.Shot shotFor(LivingEntity var0) {
      return var0 instanceof Player var1 ? WornGearCache.shot(var1) : ArrowSupply.Shot.DEFAULT;
   }

   static void draw(PoseStack var0, VertexConsumer var1, int var2, boolean var3, boolean var4, ArrowSupply.Shot var5, boolean var6) {
      VertexConsumer var7 = FieldMaterials.flat(var1);
      double var8 = var4 ? 0.26 : 0.38;
      double var10 = var4 ? -0.26 : -0.38;
      ArrowTip var12 = var3 ? null : var5.tip();
      boolean var13 = !var3 && !var4 && var5.primitive();
      if (!var3) {
         int var18 = var12.glow;
         if (var13) {
            cedar(var0, var1, var7, var2, var10, var8);
         } else {
            carbon(var0, var7, var2, var10, var8, var4, var18);
         }

         head(var0, var7, var2, var12, var10, var13, var6);
         nock(var0, var7, var2, var8, var13 ? 13616035 : (var18 != 0 ? var18 : 15262934), var13, var18);
      } else {
         HuntMesh.tube(var0, var7, var2, 14210232, 0.0, 0.0, var10, 0.0, 0.0, var8, 0.0036, 0.0036, 12);
         HuntMesh.tube(var0, var7, var2, 8886166, 0.0, 0.0, var10 + 0.012, 0.0, 0.0, var10 - 0.029, 0.006, 0.004, 12);
         HuntMesh.tube(var0, var7, var2, 13226443, 0.0, 0.0, var10 - 0.027, 0.0, 0.0, var10 - 0.073, 0.004, 0.0, 12);

         for (int var17 : new int[]{-1, 1}) {
            HuntMesh.tube(var0, var7, var2, 11648181, 0.0, 0.0, var10 - 0.035, (double)var17 * 0.015, 0.0, var10 + 0.008, 0.0022, 0.0012, 8);
         }

         HuntMesh.tube(var0, var7, var2, 12949062, 0.0, 0.0, var8 - 0.16, 0.0, 0.0, var8 - 0.14, 0.0085, 0.0085, 12);
         nock(var0, var7, var2, var8, 12697507, false, 0);
      }
   }

   private static void carbon(PoseStack var0, VertexConsumer var1, int var2, double var3, double var5, boolean var7, int var8) {
      HuntMesh.tube(var0, var1, var2, 2764336, 0.0, 0.0, var3, 0.0, 0.0, var5, 0.0036, 0.0036, 12);
      HuntMesh.tube(var0, var1, var2, 14275780, 0.0, 0.0, var5 - (var7 ? 0.1 : 0.15), 0.0, 0.0, var5 - 0.012, 0.00385, 0.00385, 12);
      HuntMesh.tube(var0, var1, var2, 7174750, 0.0, 0.0, var5 - (var7 ? 0.07 : 0.115), 0.0, 0.0, var5 - (var7 ? 0.06 : 0.1), 0.0039, 0.0039, 12);
      double var9 = var7 ? 0.045 : 0.058;
      double var11 = var7 ? 0.009 : 0.0115;

      for (int var13 = 0; var13 < 3; var13++) {
         var0.pushPose();
         var0.mulPose(Axis.ZP.rotationDegrees((float)(var13 * 120 + 8)));
         int var14 = var13 == 0 ? (var8 != 0 ? var8 : 16738842) : 15526626;
         ArtMesh.profile(
            var0,
            var1,
            var13 == 0 && var8 != 0 ? 15728880 : var2,
            var14,
            5.5E-4,
            1.5E-4,
            new double[][]{
               {0.0034, var5 - 0.03 - var9},
               {0.0034 + var11 * 0.35, var5 - 0.03 - var9 * 0.72},
               {0.0034 + var11, var5 - 0.034},
               {0.0034 + var11 * 0.92, var5 - 0.024},
               {0.0034, var5 - 0.022}
            }
         );
         var0.popPose();
      }
   }

   private static void cedar(PoseStack var0, VertexConsumer var1, VertexConsumer var2, int var3, double var4, double var6) {
      VertexConsumer var8 = FieldMaterials.tile(var1, 4);
      HuntMesh.tube(var0, var8, var3, 12619356, 0.0, 0.0, var4, 0.0, 0.0, var6, 0.004, 0.004, 10);
      HuntMesh.tube(var0, var2, var3, 5909792, 0.0, 0.0, var6 - 0.225, 0.0, 0.0, var6 - 0.205, 0.0042, 0.0042, 10);
      HuntMesh.tube(var0, var2, var3, 2042419, 0.0, 0.0, var6 - 0.2, 0.0, 0.0, var6 - 0.195, 0.0042, 0.0042, 10);
      HuntMesh.tube(var0, var2, var3, 14207912, 0.0, 0.0, var6 - 0.19, 0.0, 0.0, var6 - 0.175, 0.0043, 0.0043, 10);
      HuntMesh.tube(var0, var2, var3, 14207912, 0.0, 0.0, var6 - 0.035, 0.0, 0.0, var6 - 0.022, 0.0043, 0.0043, 10);
      VertexConsumer var9 = FieldMaterials.tile(var1, 14);

      for (int var10 = 0; var10 < 3; var10++) {
         var0.pushPose();
         var0.mulPose(Axis.ZP.rotationDegrees((float)(var10 * 120)));
         ArtMesh.profile(
            var0,
            var9,
            var3,
            var10 == 0 ? 14866624 : 8018490,
            5.0E-4,
            2.0E-4,
            new double[][]{
               {0.0038, var6 - 0.175},
               {0.0078, var6 - 0.165},
               {0.0165, var6 - 0.128},
               {0.0172, var6 - 0.092},
               {0.0112, var6 - 0.078},
               {0.0158, var6 - 0.062},
               {0.015, var6 - 0.04},
               {0.0038, var6 - 0.034}
            }
         );
         var0.popPose();
      }
   }

   private static void nock(PoseStack var0, VertexConsumer var1, int var2, double var3, int var5, boolean var6, int var7) {
      if (var6) {
         for (int var16 : new int[]{-1, 1}) {
            ArtMesh.box(var0, var1, var2, 11567184, (double)var16 * 0.0026, 0.0, var3 + 0.004, 0.0022, 0.0074, 0.012);
         }
      } else {
         int var8 = var7 != 0 ? 15728880 : var2;
         HuntMesh.tube(var0, var1, var8, var5, 0.0, 0.0, var3 - 0.008, 0.0, 0.0, var3 + 0.01, 0.0046, 0.0046, 12);

         for (int var12 : new int[]{-1, 1}) {
            ArtMesh.box(var0, var1, var8, var5, (double)var12 * 0.0034, 0.0, var3 + 0.014, 0.0025, 0.006, 0.009);
         }

         if (var7 != 0) {
            HuntMesh.tube(var0, var1, 15728880, 16777215, 0.0, 0.0, var3 - 0.002, 0.0, 0.0, var3 + 0.006, 0.003, 0.003, 8);
         }
      }
   }

   private static void blades(PoseStack var0, VertexConsumer var1, int var2, int var3, int var4, double var5, double[][] var7) {
      for (int var8 = 0; var8 < var4; var8++) {
         var0.pushPose();
         var0.mulPose(Axis.ZP.rotationDegrees((float)(var5 + (double)var8 * 360.0 / (double)var4)));
         ArtMesh.profile(var0, var1, var2, var3, 8.0E-4, 6.5E-4, var7);
         var0.popPose();
      }
   }

   private static void head(PoseStack var0, VertexConsumer var1, int var2, ArrowTip var3, double var4, boolean var6, boolean var7) {
      int var8 = var3.metal;
      int var9 = var3.glow;
      switch (var3.shape()) {
         case FIELD_POINT:
            HuntMesh.tube(var0, var1, var2, 10259034, 0.0, 0.0, var4 + 0.01, 0.0, 0.0, var4 - 0.002, 0.0041, 0.0041, 12);
            HuntMesh.tube(var0, var1, var2, var8, 0.0, 0.0, var4 - 0.002, 0.0, 0.0, var4 - 0.02, 0.0041, 0.004, 12);
            HuntMesh.tube(var0, var1, var2, var8, 0.0, 0.0, var4 - 0.02, 0.0, 0.0, var4 - 0.034, 0.004, 4.0E-4, 12);
            break;
         case FIXED_BROADHEAD:
            HuntMesh.tube(var0, var1, var2, 8886166, 0.0, 0.0, var4 + 0.012, 0.0, 0.0, var4 - 0.029, 0.006, 0.004, 12);
            blades(var0, var1, var2, var8, 3, 0.0, new double[][]{{0.0, var4 - 0.071}, {0.015, var4 - 0.017}, {0.011, var4 + 0.002}, {0.0, var4 - 0.005}});
            HuntMesh.tube(var0, var1, var2, 6120806, 0.0, 0.0, var4 - 0.06, 0.0, 0.0, var4 - 0.074, 0.0026, 0.0, 8);
            break;
         case MECHANICAL_BROADHEAD:
            HuntMesh.tube(var0, var1, var2, 4015175, 0.0, 0.0, var4 + 0.012, 0.0, 0.0, var4 - 0.04, 0.0058, 0.0042, 12);
            blades(var0, var1, var2, 13949398, 2, 90.0, new double[][]{{0.0, var4 - 0.066}, {0.0065, var4 - 0.048}, {0.004, var4 - 0.038}, {0.0, var4 - 0.04}});
            double[][] var18 = var7
               ? new double[][]{{0.004, var4 - 0.036}, {0.03, var4 + 0.004}, {0.026, var4 + 0.009}, {0.004, var4 - 0.02}}
               : new double[][]{{0.0045, var4 - 0.036}, {0.0095, var4 - 0.004}, {0.0078, var4 + 0.004}, {0.0045, var4 - 0.018}};
            blades(var0, var1, var2, var8, 2, 0.0, var18);
            HuntMesh.tube(var0, var1, var2, 13120810, 0.0, 0.0, var4 - 0.012, 0.0, 0.0, var4 - 0.016, 0.0061, 0.0061, 12);
            break;
         case CUT_ON_CONTACT:
            HuntMesh.tube(var0, var1, var2, 3093558, 0.0, 0.0, var4 + 0.012, 0.0, 0.0, var4 - 0.018, 0.0056, 0.0045, 12);
            blades(
               var0,
               var1,
               var2,
               var8,
               2,
               0.0,
               new double[][]{{0.0, var4 - 0.092}, {0.0175, var4 - 0.02}, {0.0165, var4 - 0.006}, {0.0045, var4 + 0.004}, {0.0, var4 - 0.002}}
            );
            blades(
               var0, var1, var2, 9344665, 2, 90.0, new double[][]{{0.004, var4 - 0.03}, {0.0105, var4 - 0.01}, {0.009, var4 - 0.003}, {0.004, var4 - 0.006}}
            );
            break;
         case JUDO_POINT:
            HuntMesh.tube(var0, var1, var2, 7304560, 0.0, 0.0, var4 + 0.01, 0.0, 0.0, var4 - 0.03, 0.005, 0.0055, 12);
            HuntMesh.ellipsoid(var0, var1, var2, 8225662, 1, 0.0, 0.0, var4 - 0.031, 0.0056, 0.0056, 0.0036);

            for (int var17 = 0; var17 < 4; var17++) {
               double var19 = (Math.PI / 4) + (double)var17 * Math.PI / 2.0;
               double var20 = Math.cos(var19);
               double var21 = Math.sin(var19);
               HuntMesh.tube(
                  var0, var1, var2, 4212037, var20 * 0.005, var21 * 0.005, var4 - 0.02, var20 * 0.018, var21 * 0.018, var4 - 0.012, 0.0011, 0.0011, 5
               );
               HuntMesh.tube(
                  var0, var1, var2, 4212037, var20 * 0.018, var21 * 0.018, var4 - 0.012, var20 * 0.028, var21 * 0.028, var4 + 0.006, 0.0011, 9.0E-4, 5
               );
            }
            break;
         case FLINT_POINT:
         case OBSIDIAN_POINT:
            boolean var10 = var3 == ArrowTip.OBSIDIAN_POINT;
            double var11 = var10 ? 0.066 : 0.058;
            double var13 = var10 ? 0.0125 : 0.0135;
            var0.pushPose();
            ArtMesh.profile(
               var0,
               var1,
               var2,
               var8,
               0.0021,
               0.0014,
               new double[][]{
                  {0.0, var4 - var11},
                  {var13 * 0.55, var4 - var11 * 0.72},
                  {var13, var4 - var11 * 0.35},
                  {var13 * 0.92, var4 - var11 * 0.1},
                  {var13 * 0.45, var4 - 0.004},
                  {var13 * 0.55, var4 + 0.004},
                  {0.0, var4 + 0.01}
               }
            );
            ArtMesh.profile(
               var0,
               var1,
               var2,
               var8,
               0.0021,
               0.0014,
               new double[][]{
                  {0.0, var4 + 0.01},
                  {-var13 * 0.55, var4 + 0.004},
                  {-var13 * 0.45, var4 - 0.004},
                  {-var13 * 0.92, var4 - var11 * 0.1},
                  {-var13, var4 - var11 * 0.35},
                  {-var13 * 0.55, var4 - var11 * 0.72},
                  {0.0, var4 - var11}
               }
            );
            var0.popPose();
            int var15 = var10 ? 4931420 : 9276034;
            HuntMesh.tube(var0, var1, var2, var15, 0.0, 0.0, var4 - var11 * 0.2, 0.0, 0.0, var4 - var11 * 0.98, 0.0012, 2.0E-4, 6);
            HuntMesh.tube(var0, var1, var2, 14207912, 0.0, 0.0, var4 + 0.004, 0.0, 0.0, var4 + 0.02, 0.0046, 0.0044, 10);
            break;
         case BONE_POINT:
            HuntMesh.tube(var0, var1, var2, var8, 0.0, 0.0, var4 + 0.006, 0.0, 0.0, var4 - 0.02, 0.0046, 0.004, 10);
            HuntMesh.tube(var0, var1, var2, var8, 0.0, 0.0, var4 - 0.02, 0.0, 0.0, var4 - 0.058, 0.004, 3.0E-4, 10);
            HuntMesh.tube(var0, var1, var2, 14207912, 0.0, 0.0, var4 + 0.004, 0.0, 0.0, var4 + 0.02, 0.0048, 0.0046, 10);
      }

      if (var9 != 0) {
         HuntMesh.tube(var0, var1, 15728880, var9, 0.0, 0.0, var4 + 0.02, 0.0, 0.0, var4 + 0.008, 0.005, 0.005, 12);
         HuntMesh.tube(var0, var1, 15728880, 16777215, 0.0, 0.0, var4 + 0.017, 0.0, 0.0, var4 + 0.011, 0.0053, 0.0053, 12);
      }
   }

   static void nocked(PoseStack var0, VertexConsumer var1, int var2, double var3, double var5, double var7, boolean var9, boolean var10) {
      nocked(var0, var1, var2, var3, var5, var7, var9, var10, ArrowSupply.Shot.DEFAULT);
   }

   static void nocked(PoseStack var0, VertexConsumer var1, int var2, double var3, double var5, double var7, boolean var9, boolean var10, ArrowSupply.Shot var11) {
      var0.pushPose();
      var0.translate(var3, var5, var7 - (var10 ? 0.274 : 0.394));
      draw(var0, var1, var2, var9, var10, var11, false);
      var0.popPose();
   }

   static void nocked(PoseStack var0, VertexConsumer var1, int var2, double var3, double var5, double var7, boolean var9, ArrowSupply.Shot var10, double var11) {
      double var13 = var7 - (var9 ? 0.274 : 0.394);
      double var15 = var9 ? 0.26 : 0.38;
      double var17 = var9 ? -0.26 : -0.38;
      double var19 = Math.min(var15, var11 - var13);
      if (!(var19 <= var17 + 0.03)) {
         if (var19 >= var15 - 1.0E-6) {
            nocked(var0, var1, var2, var3, var5, var7, false, var9, var10);
         } else {
            VertexConsumer var21 = FieldMaterials.flat(var1);
            boolean var22 = !var9 && var10.primitive();
            var0.pushPose();
            var0.translate(var3, var5, var13);
            if (var22) {
               HuntMesh.tube(var0, FieldMaterials.tile(var1, 4), var2, 12619356, 0.0, 0.0, var17, 0.0, 0.0, var19, 0.004, 0.0, 10);
            } else {
               HuntMesh.tube(var0, var21, var2, 2764336, 0.0, 0.0, var17, 0.0, 0.0, var19, 0.0036, 0.0, 12);
            }

            head(var0, var21, var2, var10.tip(), var17, var22, false);
            var0.popPose();
         }
      }
   }

   static void loose(PoseStack var0, VertexConsumer var1, int var2, ArrowTip var3, boolean var4) {
      VertexConsumer var5 = FieldMaterials.flat(var1);
      var0.pushPose();
      float var6 = var4 ? 5.2F : 2.6F;
      var0.scale(var6, var6, var6);
      var0.mulPose(Axis.XP.rotationDegrees(90.0F));
      var0.mulPose(Axis.ZP.rotationDegrees(20.0F));
      var0.translate(0.0, 0.0, 0.035);
      head(var0, var5, var2, var3, 0.0, var3.primitive(), false);
      HuntMesh.tube(var0, var5, var2, var3.primitive() ? 12619356 : 2764336, 0.0, 0.0, 0.004, 0.0, 0.0, 0.03, 0.0036, 0.0036, 10);
      var0.popPose();
   }

   private FieldArrowModel() {
   }
}
