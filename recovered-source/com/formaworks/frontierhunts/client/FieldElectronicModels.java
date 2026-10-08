package com.formaworks.frontierhunts.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;

final class FieldElectronicModels {
   static boolean draw(String var0, ItemDisplayContext var1, PoseStack var2, MultiBufferSource var3, int var4) {
      if (!var0.equals("field_battery_pack") && !var0.equals("night_vision_binoculars") && !var0.equals("field_camera")) {
         return false;
      } else {
         if (var1 == ItemDisplayContext.GUI) {
            var2.scale(
               var0.equals("field_battery_pack") ? 4.0F : 2.1F,
               var0.equals("field_battery_pack") ? 4.0F : 2.1F,
               var0.equals("field_battery_pack") ? 4.0F : 2.1F
            );
         }

         VertexConsumer var5 = var3.getBuffer(RenderType.entityCutoutNoCull(FieldMaterials.ATLAS));
         VertexConsumer var6 = FieldMaterials.tile(var5, 0);
         VertexConsumer var7 = FieldMaterials.tile(var5, 2);
         VertexConsumer var8 = FieldMaterials.flat(var5);
         if (var0.equals("field_camera")) {
            ArtMesh.profile(
               var2,
               var6,
               var4,
               9477514,
               0.094,
               0.009,
               new double[][]{{-0.055, -0.043}, {0.038, -0.043}, {0.06, -0.026}, {0.06, 0.026}, {0.04, 0.043}, {-0.055, 0.043}}
            );
            ArtMesh.box(var2, var7, var4, 12173997, 0.075, -0.005, -0.013, 0.051, 0.105, 0.096);
            ArtMesh.box(var2, var8, var4, 1121818, -0.014, -0.003, 0.045, 0.112, 0.068, 0.005);
            ArtMesh.box(var2, var8, var4, 3691856, -0.014, -0.003, 0.048, 0.098, 0.055, 0.002);

            for (int var9 = 0; var9 < 3; var9++) {
               HuntMesh.tube(var2, var8, var4, 9477767, 0.064, 0.016 - (double)var9 * 0.019, 0.044, 0.064, 0.016 - (double)var9 * 0.019, 0.05, 0.005, 0.005, 12);
            }

            FieldMountedOptic.sleeve(var2, var6, var4, 11121564, -0.125, -0.038, 0.043, 0.048, 0.034);

            for (int var17 = 0; var17 < 7; var17++) {
               FieldMountedOptic.sleeve(var2, var7, var4, 8096628, -0.111 + (double)var17 * 0.007, -0.107 + (double)var17 * 0.007, 0.047, 0.047, 0.038);
            }

            FieldMountedOptic.sleeve(var2, var8, var4, 5003846, -0.143, -0.122, 0.049, 0.047, 0.035);
            HuntMesh.tube(var2, var6, var4, 11976618, -0.062, 0.061, 0.0, -0.062, 0.074, 0.0, 0.019, 0.019, 24);
            HuntMesh.tube(var2, var8, var4, 9674631, 0.075, 0.05, -0.027, 0.075, 0.062, -0.027, 0.012, 0.012, 20);
            ArtMesh.box(var2, var7, var4, 10661784, 0.0, 0.071, 0.02, 0.043, 0.029, 0.043);
            ArtMesh.box(var2, var8, var4, 2571319, 0.0, 0.071, 0.043, 0.028, 0.016, 0.004);

            for (int var12 : new int[]{-1, 1}) {
               HuntMesh.tube(var2, var6, var4, 12699830, (double)var12 * 0.101, 0.03, -0.006, (double)var12 * 0.101, 0.03, 0.014, 0.006, 0.006, 12);
            }

            FieldMountedOptic.glass(var2, var3, var4, -0.139, 0.034, -1);
         } else if (var0.equals("field_battery_pack")) {
            for (int var26 : new int[]{-1, 1}) {
               double var13 = (double)var26 * 0.022;
               HuntMesh.tube(var2, var8, var4, 7638886, var13, -0.06, 0.0, var13, 0.06, 0.0, 0.02, 0.02, 20);
               HuntMesh.tube(var2, FieldMaterials.tile(var5, 3), var4, 13750973, var13, 0.06, 0.0, var13, 0.064, 0.0, 0.019, 0.019, 20);
               HuntMesh.tube(var2, var6, var4, 12240045, var13, 0.064, 0.0, var13, 0.069, 0.0, 0.008, 0.008, 12);
               ArtMesh.box(var2, var8, var4, 13685436, var13, 0.025, -0.02, 0.018, 0.002, 0.001);
               ArtMesh.box(var2, var8, var4, 13685436, var13, 0.025, -0.02, 0.002, 0.018, 0.001);

               for (int var15 = 0; var15 < 5; var15++) {
                  ArtMesh.box(var2, var8, var4, 3425074, var13 - 0.013 + (double)var15 * 0.006, -0.032, -0.02, 0.002, 0.019, 0.001);
               }
            }

            ArtMesh.box(var2, var7, var4, 13686978, 0.0, -0.004, 0.0, 0.089, 0.024, 0.043);
         } else {
            ArtMesh.box(var2, var6, var4, 12831672, 0.0, 0.055, -0.018, 0.16, 0.034, 0.074);

            for (int var27 : new int[]{-1, 1}) {
               var2.pushPose();
               var2.translate((double)var27 * 0.092, 0.0, 0.0);
               FieldMountedOptic.sleeve(var2, var6, var4, 11911336, -0.14, 0.1, 0.041, 0.035, 0.029);
               FieldMountedOptic.sleeve(var2, var7, var4, 12108715, 0.095, 0.155, 0.04, 0.043, 0.03);
               FieldMountedOptic.sleeve(var2, var6, var4, 12962228, -0.158, -0.136, 0.046, 0.042, 0.034);

               for (int var28 = 0; var28 < 6; var28++) {
                  FieldMountedOptic.sleeve(var2, var8, var4, 3689270, 0.105 + (double)var28 * 0.006, 0.108 + (double)var28 * 0.006, 0.043, 0.043, 0.041);
               }

               HuntMesh.tube(var2, var6, var4, 12896952, 0.0, 0.04, -0.012, 0.0, 0.065, -0.012, 0.018, 0.018, 20);
               FieldMountedOptic.glass(var2, var3, var4, -0.153, 0.032, -1);
               FieldMountedOptic.glass(var2, var3, var4, 0.148, 0.029, 1);
               var2.popPose();
               var5 = var3.getBuffer(RenderType.entityCutoutNoCull(FieldMaterials.ATLAS));
               var6 = FieldMaterials.tile(var5, 0);
               var7 = FieldMaterials.tile(var5, 2);
               var8 = FieldMaterials.flat(var5);
               HuntMesh.tube(var2, var6, var4, 12700082, (double)var27 * 0.058, 0.068, -0.018, (double)var27 * 0.088, 0.068, -0.018, 0.014, 0.014, 16);
            }

            ArtMesh.box(var2, var7, var4, 12109224, 0.0, 0.074, -0.02, 0.055, 0.024, 0.055);
            ArtMesh.box(var2, var8, var4, 9878135, 0.015, 0.087, -0.023, 0.009, 0.001, 0.005);

            for (int var21 = 0; var21 < 3; var21++) {
               ArtMesh.box(var2, var8, var4, 13883324, -0.014 + (double)var21 * 0.007, 0.087, 0.002, 0.004, 0.001, 0.008);
            }
         }

         return true;
      }
   }

   private FieldElectronicModels() {
   }
}
