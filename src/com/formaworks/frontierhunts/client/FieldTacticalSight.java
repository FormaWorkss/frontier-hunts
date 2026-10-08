package com.formaworks.frontierhunts.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;

final class FieldTacticalSight {
   static void draw(String var0, PoseStack var1, MultiBufferSource var2, int var3) {
      if (var0.equals("reflex_sight")) {
         FieldReflexSight.draw(var1, var2, var3);
      } else {
         FieldWeaponMesh.part("optic_" + var0, var1, var2, var3);
         boolean var4 = var0.equals("two_power_prism");
         boolean var5 = var0.equals("micro_red_dot");
         double var6 = var4 ? 0.095 : (var5 ? 0.064 : 0.081);
         if (!var5 && !var4) {
            double var28 = 0.0203;
            double var29 = 0.0508;
            double var30 = -0.03;
            // [gunsmith] coated holographic window (was a single faint quad): same rectangle, depth and facing
            OpticGlass.window(var1, var2, var3, -(var28 - 0.002), var28 - 0.002, 0.0212, var29 - 0.004, var30);
         } else {
            var1.pushPose();
            var1.translate(0.0, var4 ? 0.044 : 0.036, 0.0);
            double var8 = var4 ? 0.022 : 0.017;
            double var10 = -var6 * 0.54;
            double var12 = var6 * 0.48;
            if (!var5) {
               FieldMountedOptic.glass(var1, var2, var3, var10 + 0.004, var8 - 0.0035, -1);
               FieldMountedOptic.glass(var1, var2, var3, var12 - 0.004, var8 - 0.0035, 1);
            } else {
               // [gunsmith] coated objective window (was an 18-segment faint disc)
               OpticGlass.dotWindow(var1, var2, var3, var10 + 0.006, var8 - 0.0035);
            }

            var1.popPose();
         }
      }
   }

   private FieldTacticalSight() {
   }
}
