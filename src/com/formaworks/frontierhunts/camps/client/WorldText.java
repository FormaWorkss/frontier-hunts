package com.formaworks.frontierhunts.camps.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * Flat text painted onto a block face that looks toward -z in the renderer's local frame (the front of our north-facing
 * models). Alignment: -1 left, 0 centre, 1 right, measured from the viewer's point of view.
 */
final class WorldText {
   private WorldText() {
   }

   static void draw(PoseStack pose, MultiBufferSource buf, Font font, String text, double x, double y, double z, float scale, int argb, int light, int align) {
      if (text == null || text.isEmpty()) {
         return;
      }
      pose.pushPose();
      pose.translate(x, y, z);
      pose.mulPose(Axis.YP.rotationDegrees(180.0F));
      pose.scale(scale, -scale, scale);
      float w = font.width(text);
      float x0 = align == 0 ? -w / 2.0F : (align < 0 ? 0.0F : -w);
      font.drawInBatch(text, x0, -font.lineHeight / 2.0F, argb, false, pose.last().pose(), buf, Font.DisplayMode.POLYGON_OFFSET, 0, light);
      pose.popPose();
   }

   /** Scale that fits {@code text} into {@code maxWidth} metres, never larger than {@code preferred}. */
   static float fit(Font font, String text, float preferred, double maxWidth) {
      int w = Math.max(1, font.width(text));
      return (float)Math.min(preferred, maxWidth / w);
   }

   static String clip(Font font, String text, int maxPx) {
      if (font.width(text) <= maxPx) {
         return text;
      }
      return font.plainSubstrByWidth(text, Math.max(0, maxPx - font.width("…"))) + "…";
   }
}
