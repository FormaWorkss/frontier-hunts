package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.expedition.AttachmentSpec;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Set;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;

final class FieldSupplyMesh {
   private static final Set<String> IDS = Set.of("rifle_round", "pistol_round", "shotgun_shell", "flare_round", "tranquilizer_dart", "bait");

   static boolean draw(String var0, ItemDisplayContext var1, PoseStack var2, MultiBufferSource var3, int var4) {
      if (FieldElectronicModels.draw(var0, var1, var2, var3, var4)) {
         return true;
      } else if (FieldAttachmentHardware.loose(var0, var1, var2, var3, var4)) {
         return true;
      } else {
         FieldWeaponMesh.lod = var1 == ItemDisplayContext.GROUND ? "field" : "close";
         if (var0.endsWith("scope")) {
            if (var1 == ItemDisplayContext.GUI) {
               var2.scale(2.2F, 2.2F, 2.2F);
            }

            FieldMountedOptic.loose(var2, var3, var4, var0);
            return true;
         } else if (AttachmentSpec.unmagnified(var0) || var0.equals("two_power_prism")) {
            float var6 = var1 == ItemDisplayContext.GUI ? 8.0F : 2.5F;
            var2.scale(var6, var6, var6);
            var2.translate(0.0, -0.033, 0.0);
            FieldTacticalSight.draw(var0, var2, var3, var4);
            return true;
         } else if (var0.equals("bowfishing_arrow")) {
            if (var1 == ItemDisplayContext.GUI) {
               var2.mulPose(Axis.ZP.rotationDegrees(-45.0F));
               var2.mulPose(Axis.XP.rotationDegrees(90.0F));
               var2.scale(6.0F, 6.0F, 1.0F);
            } else {
               var2.mulPose(Axis.XP.rotationDegrees(70.0F));
            }

            FieldArrowModel.draw(var2, var3.getBuffer(RenderType.entityCutoutNoCull(FieldMaterials.ATLAS)), var4, true, false);
            return true;
         } else if (!IDS.contains(var0)) {
            return false;
         } else {
            String var5 = var1 == ItemDisplayContext.GROUND
               ? "distant"
               : (!var1.firstPerson() && var1 != ItemDisplayContext.GUI && var1 != ItemDisplayContext.FIXED ? "field" : "close");
            if (var1 == ItemDisplayContext.GUI) {
               var2.scale(2.25F, 2.25F, 2.25F);
            }

            FieldWeaponMesh.drawStatic(var0 + "_" + var5, FieldWeaponMesh.TEXTURE, var2, var3, var4);
            return true;
         }
      }
   }

   private FieldSupplyMesh() {
   }
}
