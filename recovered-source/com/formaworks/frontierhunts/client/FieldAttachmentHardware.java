package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.expedition.AttachmentSpec;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

final class FieldAttachmentHardware {
   private static final Map<Integer, double[]> BIPODS = new HashMap<>();

   static boolean loose(String var0, ItemDisplayContext var1, PoseStack var2, MultiBufferSource var3, int var4) {
      if (!Set.of("extended_magazine", "pistol_magazine", "sniper_magazine", "suppressor", "muzzle_brake", "bipod", "steady_stock", "angled_foregrip")
         .contains(var0)) {
         return false;
      } else {
         float var5 = var1 == ItemDisplayContext.GUI ? 2.4F : 1.3F;
         var2.scale(var5, var5, var5);
         FieldWeaponMesh.lod = "close";
         if (var0.equals("extended_magazine")) {
            var2.translate(0.0, 0.07, 0.07);
            magazine(false, var2, var3, var4);
         } else if (var0.equals("pistol_magazine")) {
            var2.translate(0.0, 0.03, -0.06);
            magazine(true, var2, var3, var4);
         } else if (var0.equals("sniper_magazine")) {
            var2.scale(1.8F, 1.8F, 1.8F);
            var2.translate(0.0, -0.025, -0.052);
            sniperMagazine(var2, var3, var4, true);
         } else if (var0.equals("suppressor") || var0.equals("muzzle_brake")) {
            var2.translate(0.0, 0.0, var0.equals("suppressor") ? 0.07 : 0.015);
            muzzle(var0, var2, var3, var4);
         } else if (var0.equals("bipod")) {
            var2.translate(0.0, 0.1, 0.0);
            bipod(var2, var3, var4, 1.0F);
         } else if (var0.equals("steady_stock")) {
            var2.translate(0.0F, 0.0F, 0.0F);
            stock(var2, var3, var4);
         } else {
            grip(var2, var3, var4);
         }

         return true;
      }
   }

   static void sniperMagazine(PoseStack var0, MultiBufferSource var1, int var2, boolean var3) {
      VertexConsumer var4 = var1.getBuffer(RenderType.entityCutoutNoCull(FieldMaterials.ATLAS));
      VertexConsumer var5 = FieldMaterials.flat(var4);
      ArtMesh.profile(
         var0,
         var5,
         var2,
         6647914,
         0.0135,
         0.0012,
         new double[][]{{0.085, 0.004}, {0.085, 0.098}, {0.05, 0.098}, {0.012, 0.094}, {0.009, 0.011}, {0.05, 0.004}}
      );
      ArtMesh.box(var0, var5, var2, 2239015, 0.0, 0.008, 0.052, 0.031, 0.004, 0.089);

      for (int var9 : new int[]{-1, 1}) {
         for (double var13 : new double[]{0.025, 0.075}) {
            ArtMesh.box(var0, var5, var2, 3160374, (double)var9 * 0.0136, 0.03, var13, 6.0E-4, 0.026, 0.003);
         }
      }

      ArtMesh.box(var0, var5, var2, 7963002, 0.0, 0.0055, 0.052, 0.017, 5.0E-4, 0.012);

      for (int var18 : new int[]{-1, 1}) {
         ArtMesh.box(var0, var5, var2, 2107683, (double)var18 * 0.01, 0.086, 0.052, 0.004, 0.003, 0.083);
      }

      if (var3) {
         var0.pushPose();
         var0.translate(0.0, 0.088, 0.052);
         RidgelineModel.cartridge(var0, FieldMaterials.tile(var4, 3), var2, false);
         var0.popPose();
      }
   }

   static void magazine(boolean var0, PoseStack var1, MultiBufferSource var2, int var3) {
      FieldWeaponMesh.part(var0 ? "att_pistol_mag_extended" : "att_rifle_mag_extended", var1, var2, var3);
   }

   static void muzzle(String var0, PoseStack var1, MultiBufferSource var2, int var3) {
      FieldWeaponMesh.part(var0.equals("muzzle_brake") ? "att_muzzle_brake" : "att_suppressor", var1, var2, var3);
   }

   static void bipod(PoseStack var0, MultiBufferSource var1, int var2, float var3) {
      FieldWeaponMesh.part("att_bipod_body", var0, var1, var2);
      float var4 = var3 * var3 * (3.0F - 2.0F * var3);

      for (int var8 : new int[]{-1, 1}) {
         var0.pushPose();
         var0.translate((double)var8 * 0.028, -0.013, 0.0);
         var0.mulPose(Axis.ZP.rotationDegrees((float)var8 * (3.0F + 16.0F * var4)));
         var0.mulPose(Axis.XP.rotationDegrees(83.0F * (1.0F - var4)));
         FieldWeaponMesh.part("att_bipod_leg", var0, var1, var2);
         var0.popPose();
      }
   }

   static float bipodDeploy(Entity var0, boolean crouching, double var2) {
      boolean var1 = crouching || com.formaworks.frontierhunts.prone.Prone.isProne(var0); // [rifle] lying prone always rests the gun on its bipod
      if (var0 == null) {
         return var1 ? 1.0F : 0.0F;
      } else {
         double[] var4 = BIPODS.computeIfAbsent(var0.getId(), var3 -> new double[]{var1 ? 1.0 : 0.0, var2});
         double var5 = Math.clamp(var2 - var4[1], 0.0, 4.0);
         var4[1] = var2;
         double var7 = var1 ? 1.0 : 0.0;
         double var9 = var5 * 0.14;
         var4[0] = var4[0] < var7 ? Math.min(var7, var4[0] + var9) : Math.max(var7, var4[0] - var9);
         if (BIPODS.size() > 64) {
            BIPODS.clear();
         }

         return (float)var4[0];
      }
   }

   static float ironsFolded(ItemStack var0, double var1) {
      return AttachmentSpec.installedSight(var0).isEmpty() ? 0.0F : 1.0F;
   }

   static void grip(PoseStack var0, MultiBufferSource var1, int var2) {
      FieldWeaponMesh.part("att_foregrip", var0, var1, var2);
   }

   static void stock(PoseStack var0, MultiBufferSource var1, int var2) {
      FieldWeaponMesh.part("att_cheek_riser", var0, var1, var2);
   }

   private FieldAttachmentHardware() {
   }
}
