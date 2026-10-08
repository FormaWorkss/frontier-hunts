package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.hunting.ArrowSupply;
import com.formaworks.frontierhunts.hunting.ArrowTip;
import com.formaworks.frontierhunts.hunting.QuiverItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

final class QuiverModel {
   static final double LENGTH = 0.6;
   static final double RADIUS = 0.058;

   static void item(ItemStack var0, PoseStack var1, MultiBufferSource var2, int var3, boolean var4) {
      item(var1, var2, var3, var4, () -> draw(var0, var1, var2, var3));
   }

   static void item(PoseStack var0, MultiBufferSource var1, int var2, boolean var3, Runnable var4) {
      var0.pushPose();
      if (var3) {
         var0.translate(0.045, -0.045, 0.0);
         var0.scale(1.05F, 1.05F, 1.05F);
         var0.mulPose(Axis.ZP.rotationDegrees(-38.0F));
         var0.translate(0.0, -0.4, 0.0);
      } else {
         var0.mulPose(Axis.ZP.rotationDegrees(-20.0F));
         var0.translate(0.0, -0.34, 0.0);
      }

      var4.run();
      var0.popPose();
   }

   static void draw(ItemStack var0, PoseStack var1, MultiBufferSource var2, int var3) {
      NonNullList var4 = QuiverItem.read(var0);
      ArrayList var5 = new ArrayList();
      ArrayList var6 = new ArrayList();

      for (ItemStack var8 : var4) {
         if (!var8.isEmpty()) {
            var5.add(new ArrowSupply.Shot(ArrowTip.of(var8), ArrowTip.primitiveShaft(var8)));
            var6.add(var8.getCount());
         }
      }

      draw(var5, var6, var1, var2, var3);
   }

   static void draw(List<ArrowSupply.Shot> var0, List<Integer> var1, PoseStack var2, MultiBufferSource var3, int var4) {
      VertexConsumer var5 = var3.getBuffer(RenderType.entityCutoutNoCull(FieldMaterials.ATLAS));
      VertexConsumer var6 = FieldMaterials.tile(var5, 7);
      VertexConsumer var7 = FieldMaterials.flat(var5);
      var2.pushPose();
      var2.scale(1.0F, 1.0F, 0.78F);
      HuntMesh.tube(var2, var6, var4, 6964264, 0.0, 0.012, 0.0, 0.0, 0.33, 0.0, 0.04988, 0.05394000000000001, 16);
      HuntMesh.tube(var2, var6, var4, 7424042, 0.0, 0.33, 0.0, 0.0, 0.58, 0.0, 0.05394000000000001, 0.058, 16);
      HuntMesh.tube(var2, var7, var4, 920587, 0.0, 0.579, 0.0, 0.0, 0.581, 0.0, 0.057420000000000006, 0.057420000000000006, 16);

      for (int var8 = 0; var8 < 20; var8++) {
         double var9 = (double)var8 * Math.PI * 2.0 / 20.0;
         double var11 = (double)(var8 + 1) * Math.PI * 2.0 / 20.0;
         double var13 = 0.060320000000000006;
         HuntMesh.tube(
            var2,
            var7,
            var4,
            2892832,
            Math.cos(var9) * var13,
            0.598,
            Math.sin(var9) * var13,
            Math.cos(var11) * var13,
            0.598,
            Math.sin(var11) * var13,
            0.0065,
            0.0065,
            6
         );
      }

      HuntMesh.tube(var2, var7, var4, 2892832, 0.0, 0.57, 0.0, 0.0, 0.588, 0.0, 0.0609, 0.06148000000000001, 16);
      HuntMesh.tube(var2, var7, var4, 3812386, 0.0, -0.004, 0.0, 0.0, 0.03, 0.0, 0.046400000000000004, 0.05104, 16);

      for (double var18 : new double[]{0.138, 0.348}) {
         HuntMesh.tube(var2, var7, var4, 4861724, 0.0, var18, 0.0, 0.0, var18 + 0.018, 0.0, 0.05594000000000001, 0.057100000000000005, 16);
      }

      for (int var16 = 0; var16 < 20; var16++) {
         ArtMesh.box(var2, var7, var4, 13481354, 0.0, 0.6 * (0.1 + (double)var16 * 0.041), -0.0551, 0.0022, 0.012, 0.003);
      }

      var2.popPose();
      ArtMesh.box(var2, var7, var4, 2040614, 0.0, 0.432, 0.05124, 0.026, 0.09, 0.008);
      ArtMesh.box(var2, var6, var4, 4927517, 0.0, 0.516, 0.05724, 0.032, 0.14, 0.006);
      ArtMesh.box(var2, var7, var4, 10130308, 0.0, 0.558, 0.06124, 0.036, 0.012, 0.01);
      arrows(var0, var1, var2, var5, var4);
   }

   private static void arrows(List<ArrowSupply.Shot> var0, List<Integer> var1, PoseStack var2, VertexConsumer var3, int var4) {
      int var5 = 0;

      for (int var7 : var1) {
         var5 += var7;
      }

      if (var5 != 0) {
         int var21 = Math.min(18, var5);
         int[] var22 = new int[var1.size()];
         int var8 = 0;

         for (int var9 = 0; var9 < var1.size(); var9++) {
            var22[var9] = (int)Math.floor((double)(var21 * (Integer)var1.get(var9)) / (double)var5);
            var8 += var22[var9];
         }

         for (int var23 = 0; var8 < var21; var23 = (var23 + 1) % var1.size()) {
            if ((Integer)var1.get(var23) > 0) {
               var22[var23]++;
               var8++;
            }
         }

         int var24 = 0;

         for (int var10 = 0; var10 < var1.size(); var10++) {
            ArrowSupply.Shot var11 = (ArrowSupply.Shot)var0.get(var10);

            for (int var12 = 0; var12 < var22[var10]; var24++) {
               double var13 = 0.04176 * Math.sqrt(((double)var24 + 0.5) / (double)var21);
               double var15 = (double)var24 * 2.39996;
               double var17 = Math.cos(var15) * var13;
               double var19 = Math.sin(var15) * var13 * 0.78;
               var2.pushPose();
               var2.translate(var17, 0.53 + (double)(var24 % 3) * 0.01, var19);
               var2.mulPose(Axis.ZP.rotationDegrees((float)(-var17 / 0.058 * 6.0)));
               var2.mulPose(Axis.XP.rotationDegrees((float)(-90.0 + var19 / 0.058 * 6.0)));
               var2.mulPose(Axis.ZP.rotationDegrees((float)(var24 * 47)));
               FieldArrowModel.draw(var2, var3, var4, false, false, var11, false);
               var2.popPose();
               var12++;
            }
         }
      }
   }

   private QuiverModel() {
   }
}
