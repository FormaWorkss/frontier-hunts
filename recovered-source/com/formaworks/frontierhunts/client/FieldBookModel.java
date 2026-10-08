package com.formaworks.frontierhunts.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

final class FieldBookModel {
   static void draw(boolean var0, PoseStack var1, MultiBufferSource var2, int var3) {
      if (var0) {
         FieldGuideModel.draw(var1, var2, var3);
      } else {
         VertexConsumer var4 = var2.getBuffer(RenderType.entityCutoutNoCull(FieldMaterials.ATLAS));
         VertexConsumer var5 = FieldMaterials.tile(var4, 7);
         VertexConsumer var6 = FieldMaterials.flat(var4);
         VertexConsumer var7 = FieldMaterials.tile(var4, 3);
         int var8 = 12362112;
         double var9 = 0.32;
         double var11 = 0.44;
         ArtMesh.box(var1, var6, var3, 14603445, 0.006, 0.0, 0.0, var9 - 0.035, var11 - 0.031, 0.044);

         for (int var13 = 0; var13 < 11; var13++) {
            ArtMesh.box(var1, var6, var3, var13 % 3 == 0 ? 12497038 : 15393482, 0.006, 0.0, -0.021 + (double)var13 * 0.004, var9 - 0.03, var11 - 0.03, 0.001);
         }

         for (double var16 : new double[]{-0.029, 0.029}) {
            ArtMesh.box(var1, var5, var3, var8, 0.0, 0.0, var16, var9, var11, 0.013);
         }

         ArtMesh.box(var1, var5, var3, var8, -var9 * 0.48, 0.0, 0.0, 0.025, var11, 0.068);

         for (int var22 = 0; var22 < 4; var22++) {
            ArtMesh.box(var1, var5, var3, var8, -var9 * 0.485, -0.165 + (double)var22 * 0.11, 0.0, 0.03, 0.025, 0.073);
         }

         for (int var23 = 0; var23 < 28; var23++) {
            double var28 = -0.198 + (double)var23 * 0.014;

            for (int var19 : new int[]{-1, 1}) {
               HuntMesh.tube(var1, var6, var3, 11908250, (double)var19 * 0.144, var28, -0.036, (double)var19 * 0.144, var28 + 0.006, -0.036, 6.5E-4, 6.5E-4, 4);
            }
         }

         for (int var24 = 0; var24 < 20; var24++) {
            double var29 = -0.132 + (double)var24 * 0.014;

            for (int var45 : new int[]{-1, 1}) {
               HuntMesh.tube(var1, var6, var3, 11908250, var29, (double)var45 * 0.207, -0.036, var29 + 0.006, (double)var45 * 0.207, -0.036, 6.5E-4, 6.5E-4, 4);
            }
         }

         ArtMesh.box(var1, var6, var3, var0 ? 7889473 : 5795138, 0.085, -0.222, 0.0, 0.02, 0.055, 0.002);

         for (int var37 : new int[]{-1, 1}) {
            for (int var20 : new int[]{-1, 1}) {
               ArtMesh.box(var1, var7, var3, 11312246, (double)var37 * 0.142, (double)var20 * 0.21, -0.036, 0.027, 0.008, 0.003);
               ArtMesh.box(var1, var7, var3, 11312246, (double)var37 * 0.152, (double)var20 * 0.2, -0.036, 0.007, 0.026, 0.003);
            }
         }

         for (int var26 = 0; var26 < 48; var26++) {
            double var31 = (double)var26 * Math.PI / 24.0;
            double var38 = (double)(var26 + 1) * Math.PI / 24.0;
            HuntMesh.tube(
               var1,
               var6,
               var3,
               13154441,
               Math.cos(var31) * 0.058,
               -0.018 + Math.sin(var31) * 0.058,
               -0.037,
               Math.cos(var38) * 0.058,
               -0.018 + Math.sin(var38) * 0.058,
               -0.037,
               0.001,
               0.001,
               4
            );
         }

         for (int var39 : new int[]{-1, 1}) {
            HuntMesh.tube(var1, var6, var3, 13548944, (double)var39 * 0.01, -0.055, -0.038, (double)var39 * 0.042, 0.014, -0.038, 0.0018, 0.0018, 5);

            for (int var42 = 0; var42 < 3; var42++) {
               HuntMesh.tube(
                  var1,
                  var6,
                  var3,
                  13548944,
                  (double)var39 * (0.018 + (double)var42 * 0.009),
                  -0.035 + (double)var42 * 0.021,
                  -0.038,
                  (double)var39 * (0.034 + (double)var42 * 0.01),
                  -0.03 + (double)var42 * 0.026,
                  -0.038,
                  0.0013,
                  5.0E-4,
                  5
               );
            }
         }

         ArtMesh.box(var1, var5, var3, 10453860, 0.132, -0.08, -0.044, 0.022, 0.2, 0.01);
         HuntMesh.tube(var1, var7, var3, 12690287, 0.132, -0.07, -0.051, 0.132, -0.07, -0.054, 0.008, 0.008, 16);
         text(var1, var2, var3, "FRONTIER HUNTS", 0.139, 0.00185F, -2833520);
         text(var1, var2, var3, var0 ? "EXPEDITION" : "HUNTER'S", 0.103, 0.0024F, -1122121);
         text(var1, var2, var3, var0 ? "FIELD GUIDE" : "JOURNAL", 0.076, 0.0023F, -1122121);
         text(var1, var2, var3, "RESERVE FIELD OFFICE", -0.128, 0.00155F, -4017782);
         text(var1, var2, var3, "FIELD NOTES  /  TROPHY RECORD", -0.169, 0.00115F, -3624566);
      }
   }

   private static void text(PoseStack var0, MultiBufferSource var1, int var2, String var3, double var4, float var6, int var7) {
      Font var8 = Minecraft.getInstance().font;
      var6 = Math.min(var6, 0.263F / (float)Math.max(1, var8.width(var3)));
      var0.pushPose();
      var0.translate(0.0, var4, -0.038);
      var0.mulPose(Axis.YP.rotationDegrees(180.0F));
      var0.scale(var6, -var6, var6);
      var8.drawInBatch(var3, (float)(-var8.width(var3)) / 2.0F, 0.0F, var7, false, var0.last().pose(), var1, DisplayMode.NORMAL, 0, var2);
      var0.popPose();
   }

   private FieldBookModel() {
   }
}
