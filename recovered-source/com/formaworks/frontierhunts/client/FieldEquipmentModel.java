package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.expedition.ExpeditionWeapon;
import com.formaworks.frontierhunts.expedition.Weapon;
import com.formaworks.frontierhunts.expedition.WeaponAction;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.item.ItemStack;

final class FieldEquipmentModel {
   static void attachments(Weapon var0, ItemStack var1, PoseStack var2, VertexConsumer var3, int var4) {
      VertexConsumer var5 = FieldMaterials.tile(var3, 0);
      double var6 = FieldWeaponSockets.muzzleZ(var0);
      double var8 = FieldWeaponSockets.muzzleY(var0);
      if (ExpeditionWeapon.attachment(var1, "suppressor") && WeaponAction.supports(var0, "suppressor")) {
         HuntMesh.tube(var2, var5, var4, 16777215, 0.0, var8, var6 + 0.01, 0.0, var8, var6 - 0.17, 0.026, 0.026, 28);
      }

      if (ExpeditionWeapon.attachment(var1, "bipod") && WeaponAction.supports(var0, "bipod")) {
         for (int var13 : new int[]{-1, 1}) {
            HuntMesh.tube(var2, var5, var4, 16777215, (double)var13 * 0.025, -0.022, -0.49, (double)var13 * 0.1, -0.2, -0.51, 0.007, 0.01, 12);
         }
      }
   }

   static void optic(PoseStack var0, VertexConsumer var1, int var2, boolean var3) {
      VertexConsumer var4 = FieldMaterials.tile(var1, 0);
      VertexConsumer var5 = FieldMaterials.tile(var1, 2);
      VertexConsumer var6 = FieldMaterials.flat(var1);
      HuntMesh.tube(var0, var4, var2, 16777215, 0.0, 0.0, -0.21, 0.0, 0.0, 0.2, 0.035, 0.03, 28);
      HuntMesh.tube(var0, var5, var2, 16777215, 0.0, 0.0, -0.28, 0.0, 0.0, -0.18, 0.058, 0.035, 28);
      HuntMesh.tube(var0, var5, var2, 16777215, 0.0, 0.0, 0.14, 0.0, 0.0, 0.23, 0.033, 0.038, 28);

      for (double var10 : new double[]{-0.2, 0.16}) {
         for (int var12 = 0; var12 < 4; var12++) {
            HuntMesh.tube(
               var0,
               var6,
               var2,
               3423797,
               0.0,
               0.0,
               var10 + (double)var12 * 0.006,
               0.0,
               0.0,
               var10 + (double)var12 * 0.006 + 0.003,
               var10 < 0.0 ? 0.048 : 0.037,
               var10 < 0.0 ? 0.048 : 0.037,
               24
            );
         }
      }

      HuntMesh.tube(var0, var4, var2, 16777215, 0.0, 0.015, -0.01, 0.0, 0.065, -0.01, 0.025, 0.025, 18);
      HuntMesh.tube(var0, var4, var2, 16777215, 0.015, 0.0, -0.01, 0.06, 0.0, -0.01, 0.02, 0.02, 18);
      lens(var0, var6, var2, 0.0, 0.0, -0.281, 0.052);
      lens(var0, var6, var2, 0.0, 0.0, 0.231, 0.033);
      if (var3) {
         ArtMesh.box(var0, var5, var2, 16777215, 0.044, 0.026, 0.025, 0.05, 0.04, 0.08);
      }
   }

   static void lens(PoseStack var0, VertexConsumer var1, int var2, double var3, double var5, double var7, double var9) {
      HuntMesh.ell(var0, var1, var2, 2506312, var3, var5, var7, var9, var9, 0.006);
      HuntMesh.ell(var0, var1, var2, 7770250, var3 - var9 * 0.26, var5 + var9 * 0.25, var7 + (var7 < 0.0 ? -0.005 : 0.005), var9 * 0.53, var9 * 0.16, 0.001);
      HuntMesh.ell(var0, var1, var2, 12107697, var3 - var9 * 0.28, var5 + var9 * 0.29, var7 + (var7 < 0.0 ? -0.007 : 0.007), var9 * 0.25, var9 * 0.035, 6.0E-4);
   }

   static void bow(Weapon var0, PoseStack var1, VertexConsumer var2, int var3, float var4) {
      bow(var0, var1, var2, var3, var4, 0.0F);
   }

   static void bow(Weapon var0, PoseStack var1, VertexConsumer var2, int var3, float var4, float var5) {
      VertexConsumer var6 = FieldMaterials.tile(var2, 1);
      VertexConsumer var7 = FieldMaterials.tile(var2, 0);
      VertexConsumer var8 = FieldMaterials.tile(var2, 2);
      VertexConsumer var9 = FieldMaterials.flat(var2);
      if (var0 != Weapon.HUNTING_SPEAR) {
         FieldBows.draw(var0, var1, var2, var3, var4, var5);
      } else {
         HuntMesh.tube(var1, var6, var3, 16777215, 0.0, -0.76, 0.0, 0.0, 0.6, 0.0, 0.015, 0.013, 18);
         ArtMesh.profile(
            var1,
            FieldMaterials.tile(var2, 3),
            var3,
            16777215,
            0.006,
            0.002,
            new double[][]{{0.52, -0.016}, {0.63, -0.055}, {0.88, 0.0}, {0.63, 0.055}, {0.52, 0.016}}
         );

         for (int var10 = 0; var10 < 9; var10++) {
            HuntMesh.tube(var1, var8, var3, 16777215, 0.0, 0.46 + (double)var10 * 0.009, 0.0, 0.0, 0.466 + (double)var10 * 0.009, 0.0, 0.017, 0.017, 16);
         }
      }
   }

   static void loop(PoseStack var0, VertexConsumer var1, int var2, int var3, double var4, double var6, double var8, double var10, double var12) {
      for (int var14 = 0; var14 < 24; var14++) {
         double var15 = (double)var14 * Math.PI / 12.0;
         double var17 = (double)(var14 + 1) * Math.PI / 12.0;
         HuntMesh.tube(
            var0,
            var1,
            var2,
            var3,
            var4,
            var6 + Math.sin(var15) * var10,
            var8 + Math.cos(var15) * var12,
            var4,
            var6 + Math.sin(var17) * var10,
            var8 + Math.cos(var17) * var12,
            0.005,
            0.005,
            6
         );
      }
   }

   private FieldEquipmentModel() {
   }
}
