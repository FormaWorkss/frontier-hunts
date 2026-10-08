package com.formaworks.frontierhunts.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;

/** First-person skinning pose: the sculpted Contour Skinning Knife at the scale of the old in-hand model. */
final class FieldSkinnerModel {
   private static final float SCALE = 0.2F / 1.2F;

   static void draw(PoseStack var0, MultiBufferSource var1, int var2) {
      var0.pushPose();
      var0.mulPose(Axis.YP.rotationDegrees(90.0F));
      var0.scale(SCALE, SCALE, SCALE);
      KnifeModels.drawNative(true, var0, var1, var2);
      var0.popPose();
   }

   private FieldSkinnerModel() {
   }
}
