package com.formaworks.frontierhunts.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;

final class FieldMountedOptic {
   static void loose(PoseStack var0, MultiBufferSource var1, int var2, String var3) {
      var0.pushPose();
      var0.translate(0.0, -0.14, 0.0);
      draw(var0, var1, var2, var3);
      var0.popPose();
   }

   static void draw(PoseStack var0, MultiBufferSource var1, int var2, String var3) {
      if (!var3.equals("four_power_optic") && !var3.isEmpty()) {
         boolean var4 = var3.equals("thermal_scope");
         double var5 = var3.equals("twelve_power_scope") ? -0.305 : (var3.equals("eight_power_scope") ? -0.265 : (var4 ? -0.185 : -0.22));
         double var7 = var3.equals("twelve_power_scope") ? 0.037 : (var3.equals("eight_power_scope") ? 0.032 : (var4 ? 0.031 : 0.027));

         String var9 = switch (var3) {
            case "eight_power_scope", "twelve_power_scope", "thermal_scope" -> var3;
            default -> "six_power_scope";
         };
         FieldWeaponMesh.part("optic_" + var9, var0, var1, var2);
         var0.pushPose();
         var0.translate(0.0, 0.146, 0.0);
         glass(var0, var1, var2, var5 + 0.008, var7 - 0.004, -1);
         glass(var0, var1, var2, 0.175, 0.018, 1);
         var0.popPose();
      } else {
         draw(var0, var1, var2, false);
      }
   }

   static void draw(PoseStack var0, MultiBufferSource var1, int var2, boolean var3) {
      FieldWeaponMesh.part("optic_fixed_scope", var0, var1, var2);
      var0.pushPose();
      var0.translate(0.0, 0.14, -0.008);
      // [gunsmith] rebuilt 4-12x (25.4 mm tube): lenses sized to the new eyepiece / objective bell, same planes
      glass(var0, var1, var2, 0.192, 0.0185, 1);
      glass(var0, var1, var2, -0.255, 0.0255, -1);
      var0.popPose();
   }

   static void sleeve(PoseStack var0, VertexConsumer var1, int var2, int var3, double var4, double var6, double var8, double var10, double var12) {
      byte var14 = 32;
      Pose var15 = var0.last();

      for (int var16 = 0; var16 < var14; var16++) {
         double var17 = (double)var16 * Math.PI * 2.0 / (double)var14;
         double var19 = (double)(var16 + 1) * Math.PI * 2.0 / (double)var14;

         for (int var21 = 0; var21 < 4; var21++) {
            for (int var22 = 0; var22 < 4; var22++) {
               boolean var23 = var22 >= 2;
               boolean var24 = var22 == 1 || var22 == 2;
               double var25 = var23 ? var19 : var17;
               double var27;
               double var29;
               double var31;
               double var33;
               double var35;
               if (var21 < 2) {
                  var27 = var21 == 0 ? (var24 ? var10 : var8) : var12;
                  var29 = var24 ? var6 : var4;
                  var31 = Math.cos(var25) * (double)(var21 == 0 ? 1 : -1);
                  var33 = Math.sin(var25) * (double)(var21 == 0 ? 1 : -1);
                  var35 = 0.0;
               } else {
                  var29 = var21 == 2 ? var4 : var6;
                  var27 = var24 ? var12 : (var21 == 2 ? var8 : var10);
                  var33 = 0.0;
                  var31 = 0.0;
                  var35 = var21 == 2 ? -1.0 : 1.0;
               }

               HuntMesh.vertex(
                  var1,
                  var15,
                  var2,
                  var3,
                  (float)(Math.cos(var25) * var27),
                  (float)(Math.sin(var25) * var27),
                  (float)var29,
                  (float)var16 / (float)var14,
                  var24 ? 1.0F : 0.0F,
                  (float)var31,
                  (float)var33,
                  (float)var35
               );
            }
         }
      }
   }

   /** [gunsmith] Coated lens (see {@link OpticGlass#lens}); same placement and winding as before. */
   static void glass(PoseStack var0, MultiBufferSource var1, int var2, double var3, double var5, int var7) {
      OpticGlass.lens(var0, var1, var2, var3, var5, var7);
   }

   private FieldMountedOptic() {
   }
}
