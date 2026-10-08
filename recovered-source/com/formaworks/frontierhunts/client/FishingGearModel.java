package com.formaworks.frontierhunts.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.world.item.ItemDisplayContext;

final class FishingGearModel {
   static boolean draw(String var0, ItemDisplayContext var1, PoseStack var2, VertexConsumer var3, int var4) {
      if (!var0.equals("fish_finder") && !var0.equals("landing_net") && !var0.equals("chum_bucket")) {
         return false;
      } else {
         VertexConsumer var5 = FieldMaterials.flat(var3);
         VertexConsumer var6 = FieldMaterials.tile(var3, 2);
         var2.pushPose();

         float var7 = switch (var0) {
            case "fish_finder" -> 2.0F;
            case "chum_bucket" -> 1.35F;
            default -> 0.72F;
         };
         if (var1 == ItemDisplayContext.GUI) {
            var7 *= 1.5F;
         }

         var2.scale(var7, var7, var7);
         switch (var0) {
            case "fish_finder":
               ArtMesh.box(var2, var6, var4, 2830896, 0.0, 0.0, 0.0, 0.15, 0.23, 0.05);
               ArtMesh.box(var2, var5, 15728880, 997939, 0.0, 0.035, -0.026, 0.12, 0.11, 0.003);

               for (int var39 = 0; var39 < 3; var39++) {
                  HuntMesh.tube(
                     var2,
                     var5,
                     15728880,
                     5232304,
                     -0.04 + (double)var39 * 0.04,
                     0.035,
                     -0.0285,
                     -0.04 + (double)var39 * 0.04,
                     0.035,
                     -0.0295,
                     0.004 + (double)var39 * 0.002,
                     0.004 + (double)var39 * 0.002,
                     8
                  );
               }

               HuntMesh.tube(var2, var5, 15728880, 14284263, 0.0, 0.035, -0.0285, 0.0, 0.075, -0.0295, 0.0015, 0.0015, 4);

               for (int var40 = 0; var40 < 4; var40++) {
                  ArtMesh.box(var2, var5, var4, var40 == 0 ? 12597547 : 6976624, -0.045 + (double)var40 * 0.03, -0.06, -0.027, 0.018, 0.012, 0.006);
               }

               HuntMesh.tube(var2, var5, var4, 1777438, 0.0, -0.115, 0.0, 0.02, -0.155, 0.02, 0.006, 0.006, 8);
               HuntMesh.tube(var2, var5, var4, 1777438, 0.02, -0.155, 0.02, 0.06, -0.175, 0.012, 0.006, 0.006, 8);
               HuntMesh.ellipsoid(var2, var5, var4, 3819076, 1, 0.075, -0.185, 0.014, 0.028, 0.014, 0.04);
               break;
            case "landing_net":
               var2.mulPose(Axis.ZP.rotationDegrees(-34.0F));
               HuntMesh.tube(var2, var5, var4, 5857884, 0.0, -0.44, 0.0, 0.0, 0.02, 0.0, 0.0135, 0.0125, 12);
               HuntMesh.tube(var2, var6, var4, 1776926, 0.0, -0.455, 0.0, 0.0, -0.27, 0.0, 0.021, 0.021, 12);
               HuntMesh.tube(var2, var5, var4, 2764078, 0.0, -0.462, 0.0, 0.0, -0.452, 0.0, 0.017, 0.017, 12);

               for (int var37 = 0; var37 < 3; var37++) {
                  HuntMesh.tube(var2, var5, var4, 1118995, 0.0, -0.43 + (double)var37 * 0.06, 0.0, 0.0, -0.425 + (double)var37 * 0.06, 0.0, 0.0225, 0.0225, 12);
               }

               HuntMesh.tube(var2, var5, var4, 9344666, 0.0, 0.0, 0.0, 0.0, 0.055, 0.0, 0.019, 0.016, 12);
               byte var38 = 32;
               double[] var41 = new double[var38 + 1];
               double[] var12 = new double[var38 + 1];

               for (int var42 = 0; var42 <= var38; var42++) {
                  double var14 = (double)var42 * Math.PI * 2.0 / (double)var38;
                  double var16 = 0.55 + 0.45 * (1.0 - Math.cos(var14)) * 0.5;
                  var41[var42] = Math.sin(var14) * 0.215 * var16;
                  var12[var42] = 0.055 + 0.25 * (1.0 - Math.cos(var14));
               }

               for (int var43 = 0; var43 < var38; var43++) {
                  HuntMesh.tube(var2, var5, var4, 3889726, var41[var43], var12[var43], 0.0, var41[var43 + 1], var12[var43 + 1], 0.0, 0.0125, 0.0125, 8);
               }

               double var44 = 0.36;
               double var15 = 0.305;
               byte var17 = 6;
               byte var18 = 22;
               double[][] var19 = new double[var17 + 1][var18 + 1];
               double[][] var20 = new double[var17 + 1][var18 + 1];
               double[][] var21 = new double[var17 + 1][var18 + 1];

               for (int var22 = 0; var22 <= var17; var22++) {
                  double var23 = (double)var22 / (double)var17;
                  double var25 = 1.0 - 0.82 * Math.pow(var23, 1.35);

                  for (int var27 = 0; var27 <= var18; var27++) {
                     double var28 = (double)var27 * Math.PI * 2.0 / (double)var18;
                     double var30 = 0.55 + 0.45 * (1.0 - Math.cos(var28)) * 0.5;
                     double var32 = Math.sin(var28) * 0.215 * var30;
                     double var34 = 0.055 + 0.25 * (1.0 - Math.cos(var28));
                     var19[var22][var27] = var32 * var25;
                     var20[var22][var27] = var15 + (var34 - var15) * var25 - 0.13 * var23 * var23;
                     var21[var22][var27] = var44 * (var23 * 0.9 + var23 * var23 * 0.1);
                  }
               }

               int var45 = 12170132;

               for (int var46 = 0; var46 < var17; var46++) {
                  for (int var24 = 0; var24 < var18; var24++) {
                     HuntMesh.tube(
                        var2,
                        var5,
                        var4,
                        var45,
                        var19[var46][var24],
                        var20[var46][var24],
                        var21[var46][var24],
                        var19[var46 + 1][var24],
                        var20[var46 + 1][var24],
                        var21[var46 + 1][var24],
                        0.0022,
                        0.0022,
                        4
                     );
                  }
               }

               for (int var47 = 1; var47 <= var17; var47++) {
                  for (int var48 = 0; var48 < var18; var48++) {
                     HuntMesh.tube(
                        var2,
                        var5,
                        var4,
                        var45,
                        var19[var47][var48],
                        var20[var47][var48],
                        var21[var47][var48],
                        var19[var47][var48 + 1],
                        var20[var47][var48 + 1],
                        var21[var47][var48 + 1],
                        0.0021,
                        0.0021,
                        4
                     );
                  }
               }
               break;
            default:
               HuntMesh.tube(var2, var5, var4, 10135204, 0.0, -0.16, 0.0, 0.0, 0.1, 0.0, 0.1, 0.125, 18);
               HuntMesh.tube(var2, var5, var4, 5976866, 0.0, 0.095, 0.0, 0.0, 0.1, 0.0, 0.118, 0.118, 18);
               HuntMesh.tube(var2, var5, var4, 13226191, 0.0, 0.09, 0.0, 0.0, 0.112, 0.0, 0.128, 0.128, 18);

               for (int var10 = 0; var10 < 12; var10++) {
                  double var11 = Math.PI * (double)var10 / 12.0;
                  double var13 = Math.PI * (double)(var10 + 1) / 12.0;
                  HuntMesh.tube(
                     var2,
                     var5,
                     var4,
                     11581622,
                     Math.cos(var11) * 0.128,
                     0.1 + Math.sin(var11) * 0.12,
                     0.0,
                     Math.cos(var13) * 0.128,
                     0.1 + Math.sin(var13) * 0.12,
                     0.0,
                     0.004,
                     0.004,
                     5
                  );
               }

               HuntMesh.tube(var2, var5, var4, 8161898, 0.02, 0.1, 0.02, 0.07, 0.18, 0.04, 0.012, 0.002, 6);
               HuntMesh.tube(var2, var5, var4, 8161898, 0.07, 0.18, 0.04, 0.1, 0.2, 0.02, 0.002, 0.02, 6);
               ArtMesh.box(var2, var5, var4, 14206880, 0.0, -0.03, -0.116, 0.09, 0.07, 0.004);
         }

         var2.popPose();
         return true;
      }
   }

   private FishingGearModel() {
   }
}
