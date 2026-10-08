package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.rifle.RidgelineOptics;
import com.formaworks.frontierhunts.rifle.RifleMotion;
import com.formaworks.frontierhunts.rifle.RifleState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;

public final class RidgelineModel {
   public static final float BIPOD_Y = 0.044F;
   public static final float BIPOD_Z = -0.215F;

   public static void draw(PoseStack var0, MultiBufferSource var1, int var2, RifleState var3, double var4) {
      draw(var0, var1, var2, var3, var4, false, 0.0F);
   }

   public static void draw(PoseStack var0, MultiBufferSource var1, int var2, RifleState var3, double var4, boolean var6, float var7) {
      draw(var0, var1, var2, var3, var4, var6, var7, RidgelineOptics.STOCK);
   }

   /** [rifle] {@code sight}: the fitted sight ({@link RidgelineOptics#sight}); factory scope, field optic or irons. */
   public static void draw(PoseStack var0, MultiBufferSource var1, int var2, RifleState var3, double var4, boolean var6, float var7, String sight) {
      CamoRifleModel.draw(var0, var1, var2, var3, var4, RidgelineOptics.STOCK.equals(sight));
      RidgelineSights.draw(var0, var1, var2, sight);
      if (var6) {
         var0.pushPose();
         var0.translate(0.0F, 0.044F, -0.215F);
         String var8 = FieldWeaponMesh.lod;
         FieldWeaponMesh.lod = "close";
         FieldAttachmentHardware.bipod(var0, var1, var2, var7);
         FieldWeaponMesh.lod = var8;
         var0.popPose();
      }

      if (var3.limit() > 3) {
         var0.pushPose();
         if (var3.action() == 2) {
            float var9 = RifleMotion.magazine(RifleMotion.progress(var3, var4));
            var0.translate(0.0F, -var9, 0.051F);
            var0.mulPose(Axis.XP.rotationDegrees(Math.max(0.0F, var9 - 0.07F) * 75.0F));
            var0.translate(0.0F, 0.0F, -0.051F);
         }

         FieldAttachmentHardware.sniperMagazine(var0, var1, var2, false);
         var0.popPose();
      }
   }

   public static void cartridge(PoseStack var0, VertexConsumer var1, int var2, boolean var3) {
      HuntMesh.tube(var0, var1, var2, 11967063, 0.0, 0.0, -0.02, 0.0, 0.0, 0.02, 0.0058, 0.0058, 16);
      HuntMesh.tube(var0, var1, var2, 13743993, 0.0, 0.0, 0.019, 0.0, 0.0, 0.022, 0.0065, 0.0065, 16);
      if (!var3) {
         HuntMesh.tube(var0, var1, var2, 11433806, 0.0, 0.0, -0.02, 0.0, 0.0, -0.03, 0.0039, 0.0039, 16);
         HuntMesh.tube(var0, var1, var2, 12487009, 0.0, 0.0, -0.03, 0.0, 0.0, -0.037, 0.0039, 1.0E-4, 16);
      }
   }
}
