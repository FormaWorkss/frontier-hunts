package com.formaworks.frontierhunts.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

final class FieldGuideModel {
   static void draw(PoseStack var0, MultiBufferSource var1, int var2) {
      VertexConsumer var3 = var1.getBuffer(RenderType.entityCutoutNoCull(FieldMaterials.ATLAS));
      VertexConsumer var4 = FieldMaterials.flat(var3);
      VertexConsumer var5 = FieldMaterials.tile(var3, 6);
      VertexConsumer var6 = FieldMaterials.tile(var3, 3);
      ArtMesh.box(var0, var4, var2, 14736583, 0.012, 0.0, 0.0, 0.349, 0.425, 0.054);

      for (int var7 = 0; var7 < 13; var7++) {
         ArtMesh.box(var0, var4, var2, var7 % 3 == 0 ? 12434857 : 15788761, 0.012, 0.0, -0.025 + (double)var7 * 0.004, 0.35, 0.424, 8.0E-4);
      }

      for (double var10 : new double[]{-0.037, 0.037}) {
         ArtMesh.box(var0, var5, var2, 11979701, 0.0, 0.0, var10, 0.403, 0.478, 0.014);
      }

      ArtMesh.box(var0, var5, var2, 8887696, -0.19, 0.0, 0.0, 0.032, 0.48, 0.094);

      for (double var37 : new double[]{-0.162, 0.0, 0.162}) {
         for (int var12 = 0; var12 < 24; var12++) {
            double var13 = (double)var12 * Math.PI / 12.0;
            double var15 = (double)(var12 + 1) * Math.PI / 12.0;
            HuntMesh.tube(
               var0,
               var6,
               var2,
               12699327,
               -0.185 + Math.cos(var13) * 0.026,
               var37,
               Math.sin(var13) * 0.052,
               -0.185 + Math.cos(var15) * 0.026,
               var37,
               Math.sin(var15) * 0.052,
               0.0024,
               0.0024,
               6
            );
         }
      }

      ArtMesh.box(var0, var4, var2, 14145207, 0.024, -0.008, -0.046, 0.287, 0.349, 0.003);

      for (int var21 = 0; var21 < 10; var21++) {
         double var28 = -0.164 + (double)var21 * 0.034;
         HuntMesh.tube(var0, var4, var2, 12239011, -0.109, var28, -0.048, 0.162, var28, -0.048, 4.0E-4, 4.0E-4, 4);
      }

      for (int var22 = 0; var22 < 9; var22++) {
         double var29 = -0.108 + (double)var22 * 0.034;
         HuntMesh.tube(var0, var4, var2, 12239011, var29, -0.164, -0.048, var29, 0.154, -0.048, 4.0E-4, 4.0E-4, 4);
      }

      for (int var23 = 0; var23 < 8; var23++) {
         for (int var30 = 0; var30 < 56; var30++) {
            double var35 = (double)var30 * Math.PI / 28.0;
            double var11 = (double)(var30 + 1) * Math.PI / 28.0;
            double var41 = 0.019 + (double)var23 * 0.01;
            double var42 = var41 * (1.0 + 0.12 * Math.sin(var35 * 3.0) + 0.08 * Math.cos(var35 * 5.0));
            double var17 = var41 * (1.0 + 0.12 * Math.sin(var11 * 3.0) + 0.08 * Math.cos(var11 * 5.0));
            HuntMesh.tube(
               var0,
               var4,
               var2,
               var23 % 3 == 0 ? 8689017 : 10792329,
               0.03 + Math.cos(var35) * var42,
               -0.025 + Math.sin(var35) * var42 * 0.7,
               -0.0488,
               0.03 + Math.cos(var11) * var17,
               -0.025 + Math.sin(var11) * var17 * 0.7,
               -0.0488,
               var23 % 3 == 0 ? 7.0E-4 : 4.0E-4,
               var23 % 3 == 0 ? 7.0E-4 : 4.0E-4,
               4
            );
         }
      }

      for (int var24 = 0; var24 < 34; var24++) {
         double var31 = -0.158 + (double)var24 * 0.007;
         double var38 = var31 + 0.007;
         HuntMesh.tube(
            var0,
            var4,
            var2,
            6327700,
            0.109 + Math.sin(var31 * 27.0) * 0.021,
            var31,
            -0.049,
            0.109 + Math.sin(var38 * 27.0) * 0.021,
            var38,
            -0.049,
            0.0018,
            0.0018,
            5
         );
      }

      for (int var25 = 0; var25 < 4; var25++) {
         double var32 = 0.12 - (double)var25 * 0.075;
         ArtMesh.box(var0, var4, var2, new int[]{12889444, 7575682, 11106136, 7771810}[var25], 0.204, var32, 0.008 - (double)var25 * 0.011, 0.041, 0.035, 0.003);
      }

      ArtMesh.box(var0, var5, var2, 4744278, 0.176, -0.072, -0.053, 0.023, 0.221, 0.008);
      ArtMesh.box(var0, var6, var2, 10924962, 0.176, -0.08, -0.06, 0.031, 0.046, 0.006);
      ArtMesh.box(var0, var5, var2, 4940377, 0.176, -0.08, -0.064, 0.019, 0.03, 0.002);
      HuntMesh.tube(var0, var6, var2, 12428383, 0.217, -0.13, 0.024, 0.217, 0.158, 0.024, 0.005, 0.005, 8);

      for (int var26 = 0; var26 < 31; var26++) {
         for (double var40 : new double[]{-0.178, 0.186}) {
            ArtMesh.box(var0, var4, var2, 10794140, var40, -0.225 + (double)var26 * 0.015, -0.046, 0.0015, 0.006, 0.001);
         }
      }

      text(var0, var1, var2, "FRONTIER", 0.222, 0.0022F, -1581637);
      text(var0, var1, var2, "EXPEDITION ATLAS", 0.184, 0.00172F, -1581637);
      text(var0, var1, var2, "RESERVE  /  FIELD GUIDE", -0.185, 0.00143F, -1581637);
      text(var0, var1, var2, "TRAILS · TERRAIN · EQUIPMENT", -0.214, 0.00105F, -2761025);
   }

   private static void text(PoseStack var0, MultiBufferSource var1, int var2, String var3, double var4, float var6, int var7) {
      Font var8 = Minecraft.getInstance().font;
      var6 = Math.min(var6, 0.32F / (float)Math.max(1, var8.width(var3)));
      var0.pushPose();
      var0.translate(0.014, var4, -0.066);
      var0.mulPose(Axis.YP.rotationDegrees(180.0F));
      var0.scale(var6, -var6, var6);
      var8.drawInBatch(var3, (float)(-var8.width(var3)) / 2.0F, 0.0F, var7, false, var0.last().pose(), var1, DisplayMode.NORMAL, 0, var2);
      var0.popPose();
   }

   private FieldGuideModel() {
   }
}
