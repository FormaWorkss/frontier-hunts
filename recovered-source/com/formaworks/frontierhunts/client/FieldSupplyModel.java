package com.formaworks.frontierhunts.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.world.item.ItemStack;

final class FieldSupplyModel {
   static void draw(String var0, ItemStack var1, PoseStack var2, VertexConsumer var3, int var4) {
      VertexConsumer var5 = FieldMaterials.tile(var3, 0);
      VertexConsumer var6 = FieldMaterials.tile(var3, 1);
      VertexConsumer var7 = FieldMaterials.tile(var3, 2);
      VertexConsumer var8 = FieldMaterials.tile(var3, 3);
      VertexConsumer var9 = FieldMaterials.tile(var3, 6);
      VertexConsumer var10 = FieldMaterials.tile(var3, 7);
      VertexConsumer var11 = FieldMaterials.flat(var3);
      if (var0.equals("grunt_tube")) {
         HuntMesh.tube(var2, var6, var4, 16777215, 0.0, -0.19, 0.0, 0.0, 0.07, 0.0, 0.042, 0.033, 24);
         HuntMesh.tube(var2, var7, var4, 16777215, 0.0, 0.05, 0.0, 0.0, 0.2, 0.0, 0.036, 0.021, 20);

         for (int var37 = 0; var37 < 4; var37++) {
            HuntMesh.tube(var2, var5, var4, 16777215, 0.0, -0.16 + (double)var37 * 0.03, 0.0, 0.0, -0.15 + (double)var37 * 0.03, 0.0, 0.044, 0.044, 20);
         }

         HuntMesh.tube(var2, var7, var4, 3816243, 0.0, -0.2, 0.0, 0.0, -0.235, 0.0, 0.03, 0.024, 16);
         FieldEquipmentModel.loop(var2, var10, var4, 16777215, 0.0, -0.03, 0.07, 0.12, 0.06);
      } else if (var0.equals("bleat_call")) {
         HuntMesh.tube(var2, var5, var4, 12171174, 0.0, -0.09, 0.0, 0.0, 0.07, 0.0, 0.062, 0.062, 24);

         for (int var36 = 0; var36 < 6; var36++) {
            HuntMesh.tube(var2, var5, var4, 9276542, 0.0, -0.072 + (double)var36 * 0.028, 0.0, 0.0, -0.064 + (double)var36 * 0.028, 0.0, 0.065, 0.065, 24);
         }

         HuntMesh.ell(var2, var7, var4, 3027504, 0.0, 0.076, 0.0, 0.062, 0.01, 0.062);
         HuntMesh.ell(var2, var5, var4, 11052693, 0.0, -0.094, 0.0, 0.062, 0.01, 0.062);
         ArtMesh.box(var2, var11, var4, 4149314, 0.0, -0.01, -0.062, 0.07, 0.046, 0.004);
      } else if (var0.equals("rattling_antlers")) {
         double[][] var35 = new double[][]{
            {0.0, -0.25, 0.0}, {0.012, -0.155, 0.022}, {0.035, -0.055, 0.048}, {0.068, 0.045, 0.058}, {0.105, 0.135, 0.042}, {0.132, 0.205, 0.006}
         };
         double[] var45 = new double[]{0.026, 0.023, 0.02, 0.016, 0.013, 0.009};
         double[][] var52 = new double[][]{{0.03, -0.062, 0.046}, {0.064, 0.036, 0.057}, {0.101, 0.126, 0.044}};
         double[][] var59 = new double[][]{{0.054, 0.058, 0.018}, {0.094, 0.152, 0.01}, {0.13, 0.216, 0.002}};
         double[] var65 = new double[]{0.013, 0.011, 0.009};

         for (int var77 : new int[]{-1, 1}) {
            var2.pushPose();
            var2.translate((double)var77 * 0.052, 0.0, 0.0);

            for (int var21 = 0; var21 + 1 < var35.length; var21++) {
               HuntMesh.tube(
                  var2,
                  var8,
                  var4,
                  14273969,
                  (double)var77 * var35[var21][0],
                  var35[var21][1],
                  var35[var21][2],
                  (double)var77 * var35[var21 + 1][0],
                  var35[var21 + 1][1],
                  var35[var21 + 1][2],
                  var45[var21],
                  var45[var21 + 1],
                  14
               );
            }

            HuntMesh.tube(var2, var8, var4, 13615266, 0.0, -0.262, 0.0, 0.0, -0.238, 0.0, 0.034, 0.028, 16);

            for (int var78 = 0; var78 < 8; var78++) {
               double var22 = (double)var78 * 0.86;
               HuntMesh.ell(
                  var2,
                  var8,
                  var4,
                  13615266,
                  (double)var77 * Math.cos(var22) * 0.024,
                  -0.225 + (double)var78 * 0.013,
                  Math.sin(var22) * 0.024,
                  0.006,
                  0.005,
                  0.006
               );
            }

            for (int var79 = 0; var79 < 3; var79++) {
               HuntMesh.tube(
                  var2,
                  var8,
                  var4,
                  14273969,
                  (double)var77 * var52[var79][0],
                  var52[var79][1],
                  var52[var79][2],
                  (double)var77 * var59[var79][0],
                  var59[var79][1],
                  var59[var79][2],
                  var65[var79],
                  0.004,
                  10
               );
            }

            var2.popPose();
         }

         FieldEquipmentModel.loop(var2, var10, var4, 16777215, 0.0, -0.16, 0.024, 0.085, 0.05);
      } else if (var0.equals("wind_checker")) {
         HuntMesh.tube(var2, var7, var4, 14210244, 0.0, -0.16, 0.0, 0.0, 0.06, 0.0, 0.058, 0.05, 24);
         HuntMesh.tube(var2, var5, var4, 16777215, 0.0, 0.06, 0.0, 0.0, 0.13, 0.0, 0.028, 0.024, 18);
         HuntMesh.tube(var2, var5, var4, 16777215, 0.0, 0.13, 0.0, 0.0, 0.175, 0.0, 0.013, 0.009, 14);
         ArtMesh.box(var2, var11, var4, 3095089, 0.0, -0.04, -0.052, 0.036, 0.1, 0.006);

         for (int var34 = 0; var34 < 3; var34++) {
            HuntMesh.ell(var2, var11, var4, 15921382, -0.01 + (double)var34 * 0.009, 0.196 + (double)var34 * 0.013, 0.003 * (double)var34, 0.008, 0.006, 0.008);
         }
      } else if (var0.equals("mock_scrape_kit")) {
         ArtMesh.box(var2, var9, var4, 11840914, -0.035, -0.04, 0.0, 0.085, 0.095, 0.055);
         ArtMesh.box(var2, var10, var4, 16777215, -0.035, 0.062, 0.0, 0.09, 0.02, 0.058);
         HuntMesh.tube(var2, var6, var4, 16777215, 0.085, -0.03, 0.0, 0.085, 0.235, 0.0, 0.016, 0.013, 12);
         ArtMesh.box(var2, var5, var4, 16777215, 0.085, -0.055, 0.0, 0.05, 0.016, 0.026);

         for (int var33 = 0; var33 < 3; var33++) {
            HuntMesh.tube(var2, var5, var4, 16777215, 0.045 + (double)var33 * 0.04, -0.068, 0.0, 0.04 + (double)var33 * 0.044, -0.145, -0.018, 0.006, 0.003, 8);
         }

         FieldEquipmentModel.loop(var2, var10, var4, 16777215, -0.035, 0.02, 0.05, 0.085, 0.045);
      } else if (!var0.contains("binoculars") && !var0.equals("rangefinder")) {
         if (var0.contains("scope")) {
            FieldEquipmentModel.optic(var2, var3, var4, var0.startsWith("thermal"));
         } else if (var0.endsWith("call")) {
            HuntMesh.tube(var2, var6, var4, 16777215, 0.0, -0.16, 0.0, 0.0, 0.09, 0.0, 0.048, 0.035, 28);
            HuntMesh.tube(var2, var7, var4, 16777215, 0.0, 0.06, 0.0, 0.0, 0.18, 0.0, 0.034, 0.025, 24);

            for (int var32 = 0; var32 < 5; var32++) {
               HuntMesh.tube(var2, var5, var4, 16777215, 0.0, -0.13 + (double)var32 * 0.025, 0.0, 0.0, -0.119 + (double)var32 * 0.025, 0.0, 0.049, 0.049, 24);
            }

            FieldEquipmentModel.loop(var2, var10, var4, 16777215, 0.0, -0.02, 0.07, 0.11, 0.065);
         } else if (var0.endsWith("round") || var0.endsWith("shell") || var0.endsWith("dart")) {
            boolean var31 = var0.contains("shell");
            boolean var44 = var0.contains("dart");
            HuntMesh.tube(var2, var8, var4, var31 ? 12756584 : 13350792, 0.0, -0.12, 0.0, 0.0, var31 ? -0.06 : 0.095, 0.0, 0.038, 0.035, 24);
            HuntMesh.tube(
               var2,
               var31 ? var7 : var8,
               var4,
               var31 ? 12019790 : 11903104,
               0.0,
               var31 ? -0.06 : 0.08,
               0.0,
               0.0,
               0.15,
               0.0,
               var31 ? 0.035 : 0.027,
               var31 ? 0.035 : 0.001,
               24
            );
            HuntMesh.tube(var2, var8, var4, 13612930, 0.0, -0.125, 0.0, 0.0, -0.112, 0.0, 0.043, 0.043, 24);
            if (var44) {
               for (int var69 : new int[]{-1, 1}) {
                  ArtMesh.box(var2, var9, var4, 12503478, (double)var69 * 0.037, -0.085, 0.0, 0.045, 0.07, 0.008);
               }
            }
         } else if (var0.contains("arrow")) {
            HuntMesh.tube(var2, var6, var4, 16777215, 0.0, -0.38, 0.0, 0.0, 0.31, 0.0, 0.005, 0.005, 12);
            HuntMesh.tube(var2, var8, var4, 16777215, 0.0, 0.3, 0.0, 0.0, 0.42, 0.0, 0.02, 0.001, 12);

            for (int var30 = 0; var30 < 3; var30++) {
               var2.pushPose();
               var2.mulPose(Axis.YP.rotationDegrees((float)(var30 * 120)));
               ArtMesh.box(var2, var9, var4, 16777215, 0.018, -0.29, 0.0, 0.035, 0.09, 0.002);
               var2.popPose();
            }
         } else if (var0.equals("suppressor")) {
            HuntMesh.tube(var2, var5, var4, 16777215, 0.0, 0.0, -0.2, 0.0, 0.0, 0.2, 0.05, 0.05, 28);
            HuntMesh.tube(var2, var11, var4, 1581341, 0.0, 0.0, -0.201, 0.0, 0.0, -0.204, 0.017, 0.017, 20);
         } else if (var0.equals("extended_magazine")) {
            ArtMesh.profile(var2, var5, var4, 16777215, 0.055, 0.006, new double[][]{{0.2, -0.09}, {0.2, 0.06}, {-0.2, 0.1}, {-0.22, -0.06}});

            for (int var29 = 0; var29 < 6; var29++) {
               ArtMesh.box(var2, var11, var4, 2700844, 0.056, 0.14 - (double)var29 * 0.05, 0.01, 0.004, 0.008, 0.11);
            }
         } else if (var0.equals("bipod")) {
            for (int var57 : new int[]{-1, 1}) {
               HuntMesh.tube(var2, var5, var4, 16777215, (double)var57 * 0.04, 0.16, 0.0, (double)var57 * 0.18, -0.22, 0.0, 0.015, 0.012, 16);
               ArtMesh.box(var2, var7, var4, 16777215, (double)var57 * 0.18, -0.22, 0.0, 0.06, 0.035, 0.06);
            }

            ArtMesh.box(var2, var5, var4, 16777215, 0.0, 0.14, 0.0, 0.14, 0.035, 0.045);
         } else if (var0.equals("reflex_sight")) {
            ArtMesh.box(var2, var5, var4, 16777215, 0.0, -0.055, 0.0, 0.17, 0.05, 0.19);

            for (int var56 : new int[]{-1, 1}) {
               ArtMesh.box(var2, var5, var4, 16777215, (double)var56 * 0.07, 0.025, -0.03, 0.017, 0.14, 0.03);
            }

            ArtMesh.box(var2, var5, var4, 16777215, 0.0, 0.09, -0.03, 0.16, 0.017, 0.03);
            FieldEquipmentModel.lens(var2, var11, var4, 0.0, 0.022, -0.03, 0.063);
         } else if (var0.equals("steady_stock")) {
            ArtMesh.profile(var2, var7, var4, 16777215, 0.065, 0.006, new double[][]{{0.1, -0.2}, {0.13, 0.27}, {-0.18, 0.29}, {-0.08, -0.18}});
            ArtMesh.box(var2, var5, var4, 16777215, 0.0, 0.065, -0.22, 0.05, 0.05, 0.15);
         } else if (var0.equals("attachment_tool")) {
            HuntMesh.tube(var2, var5, var4, 16777215, 0.0, -0.12, 0.0, 0.0, 0.15, 0.0, 0.026, 0.023, 12);

            for (int var55 : new int[]{-1, 1}) {
               HuntMesh.tube(var2, var8, var4, 16777215, (double)var55 * 0.021, 0.12, 0.0, (double)var55 * 0.056, 0.22, 0.0, 0.015, 0.012, 10);
            }

            ArtMesh.box(var2, var7, var4, 16777215, 0.0, -0.06, 0.0, 0.055, 0.19, 0.05);
         } else if (var0.contains("lure") || var0.equals("fishing_drag_kit")) {
            HuntMesh.ell(var2, var8, var4, var0.startsWith("glow") ? 12967344 : 12299878, 0.0, 0.0, 0.0, 0.035, 0.12, 0.018);
            FieldEquipmentModel.loop(var2, var5, var4, 16777215, 0.0, -0.14, 0.0, 0.05, 0.03);
            HuntMesh.ell(var2, var11, var4, 1780001, 0.0, 0.07, -0.018, 0.009, 0.01, 0.002);
         } else if (var0.equals("expedition_guide")) {
            ArtMesh.box(var2, var10, var4, 16777215, 0.0, 0.0, 0.0, 0.32, 0.06, 0.43);
            ArtMesh.box(var2, var11, var4, 14274484, 0.009, 0.0, -0.006, 0.29, 0.039, 0.4);

            for (double var54 : new double[]{-0.027, 0.027}) {
               ArtMesh.box(var2, var10, var4, 16777215, 0.0, var54, 0.0, 0.33, 0.013, 0.44);
            }

            ArtMesh.box(var2, var5, var4, 12169882, 0.13, 0.025, 0.0, 0.055, 0.03, 0.065);
         } else if (var0.equals("scent_cover")) {
            HuntMesh.tube(var2, var7, var4, 16777215, 0.0, -0.15, 0.0, 0.0, 0.11, 0.0, 0.075, 0.055, 24);
            ArtMesh.box(var2, var5, var4, 16777215, 0.0, 0.15, 0.0, 0.1, 0.06, 0.055);
            ArtMesh.box(var2, var11, var4, 12172449, 0.0, 0.0, -0.069, 0.09, 0.13, 0.008);
         } else {
            boolean var24 = var0.equals("medkit");
            boolean var39 = var0.equals("hunter_pack");
            boolean var46 = var0.equals("bait");
            VertexConsumer var53 = var24 ? var7 : var9;
            ArtMesh.box(var2, var53, var4, var24 ? 8627329 : 16777215, 0.0, 0.0, 0.0, var39 ? 0.33 : 0.35, var39 ? 0.43 : 0.24, var39 ? 0.17 : 0.24);

            for (int var74 : new int[]{-1, 1}) {
               ArtMesh.box(var2, var10, var4, 16777215, (double)var74 * 0.105, 0.0, -0.127, 0.027, var39 ? 0.43 : 0.24, 0.013);
               ArtMesh.box(var2, var8, var4, 12697002, (double)var74 * 0.105, -0.035, -0.14, 0.038, 0.032, 0.016);
            }

            ArtMesh.box(var2, var53, var4, var24 ? 10269332 : 16777215, 0.0, -0.07, -0.14, 0.17, 0.14, 0.055);
            if (var24) {
               ArtMesh.box(var2, var11, var4, 14539711, 0.0, 0.03, -0.134, 0.025, 0.09, 0.006);
               ArtMesh.box(var2, var11, var4, 14539711, 0.0, 0.03, -0.136, 0.085, 0.025, 0.006);
            } else if (var39) {
               for (int var75 : new int[]{-1, 1}) {
                  FieldEquipmentModel.loop(var2, var10, var4, 16777215, (double)var75 * 0.1, 0.03, 0.12, 0.18, 0.055);
               }
            } else if (var0.equals("tree_stand")) {
               for (int var62 = 0; var62 < 5; var62++) {
                  ArtMesh.box(var2, var5, var4, 16777215, 0.0, 0.14, -0.1 + (double)var62 * 0.045, 0.38, 0.018, 0.024);
               }
            } else if (var46) {
               for (int var63 = 0; var63 < 6; var63++) {
                  HuntMesh.ell(var2, var6, var4, 14271393, -0.08 + (double)(var63 % 3) * 0.08, 0.125, -0.04 + (double)(var63 / 3) * 0.08, 0.023, 0.012, 0.022);
               }
            }
         }
      } else {
         boolean var12 = var0.contains("binoculars");

         for (int var16 : var12 ? new int[]{-1, 1} : new int[]{0}) {
            var2.pushPose();
            var2.translate((double)var16 * 0.095, 0.0, 0.0);
            HuntMesh.tube(var2, var7, var4, 16777215, 0.0, 0.0, -0.19, 0.0, 0.0, 0.15, 0.066, 0.046, 28);

            for (double var20 : new double[]{-0.18, -0.13, 0.13}) {
               HuntMesh.tube(var2, var5, var4, 16777215, 0.0, 0.0, var20, 0.0, 0.0, var20 + 0.018, var20 < 0.0 ? 0.068 : 0.049, var20 < 0.0 ? 0.068 : 0.049, 24);
            }

            FieldEquipmentModel.lens(var2, var11, var4, 0.0, 0.0, -0.191, 0.059);
            FieldEquipmentModel.lens(var2, var11, var4, 0.0, 0.0, 0.151, 0.041);

            for (int var66 = 0; var66 < 8; var66++) {
               HuntMesh.tube(var2, var11, var4, 3358002, 0.0, 0.0, 0.08 + (double)var66 * 0.007, 0.0, 0.0, 0.083 + (double)var66 * 0.007, 0.048, 0.048, 20);
            }

            var2.popPose();
         }

         ArtMesh.box(var2, var5, var4, 16777215, 0.0, 0.016, -0.005, var12 ? 0.18 : 0.11, 0.05, 0.15);
         HuntMesh.tube(var2, var7, var4, 16777215, 0.0, 0.035, 0.013, 0.0, 0.078, 0.013, 0.028, 0.028, 18);
         if (var0.startsWith("anatomy") || var0.startsWith("thermal")) {
            ArtMesh.box(var2, var7, var4, 16777215, 0.0, 0.035, -0.075, 0.09, 0.035, 0.07);

            for (int var38 = 0; var38 < 3; var38++) {
               HuntMesh.ell(var2, var11, var4, 7769206, -0.025 + (double)var38 * 0.025, 0.056, -0.078, 0.007, 0.003, 0.01);
            }
         }
      }
   }

   private FieldSupplyModel() {
   }
}
