package com.formaworks.frontierhunts.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

final class FieldScopeView {
   static void draw(GuiGraphics var0, double var1, boolean var3) {
      Minecraft var4 = Minecraft.getInstance();
      // [scope] the held optic's live power (variable scopes), its SFP mil-scale power and its caption
      double var13 = ScopeZoom.power();
      if (!var3 && var13 > 0.0) {
         var1 = var13;
      }
      double var14 = var3 ? var1 : ScopeZoom.reticlePower(var1);
      String var15 = var3 ? null : ScopeZoom.caption();
      double var5 = var4.getWindow().getGuiScale();
      int var7 = (int)Math.round((double)var0.guiWidth() * var5);
      int var8 = (int)Math.round((double)var0.guiHeight() * var5);
      int var9 = var7 / 2;
      int var10 = var8 / 2;
      int var11 = (int)((double)var8 * 0.43);
      var0.pose().pushPose();
      var0.pose().scale((float)(1.0 / var5), (float)(1.0 / var5), 1.0F);
      float var12 = (float)Math.hypot((double)var7, (double)var8);
      OpticMask.draw(var0, (float)var9, (float)var10, (float)var11, (float)(var11 + 10), var12, -16315637, -13616328, -14669273, 12.0F, 1679041054);
      var0.drawManaged(() -> reticle(var0, var4, var14, var3, var7, var8, var9, var10, var11));
      var0.pose().popPose();
      var0.drawCenteredString(
         var4.font, var15 != null ? var15 : (int)var1 + "x  /  " + (var3 ? "PRISM" : "FIELD PRECISION"), var0.guiWidth() / 2, var0.guiHeight() - 18, -5918038
      );
      if (!var3) {
         ScopeZoom.readout(var0, var0.guiWidth() / 2.0F, var0.guiHeight() / 2.0F, (float)(var11 / var5));
      }
   }

   private static void reticle(GuiGraphics var0, Minecraft var1, double var2, boolean var4, int var5, int var6, int var7, int var8, int var9) {
      int var10 = -451206881;
      int var11 = OpticInput.reticleColor();
      byte var12 = 3;
      if (var4) {
         for (int var13 = -11; var13 <= 11; var13++) {
            int var14 = Math.abs(var13);
            var0.fill(var7 + var13, var8 + var14, var7 + var13 + 1, var8 + var14 + 2, var11);
         }

         line(var0, var7, var8 + 20, var7, var8 + var9 - 20, var10);

         for (int var19 = 1; var19 <= 3; var19++) {
            int var21 = var8 + 22 + var19 * 18;
            line(var0, var7 - 9 + var19 * 2, var21, var7 + 9 - var19 * 2, var21, var10);
         }
      } else {
         line(var0, var7 - var9 + 18, var8, var7 - var12, var8, var10);
         line(var0, var7 + var12, var8, var7 + var9 - 18, var8, var10);
         line(var0, var7, var8 - var9 + 18, var7, var8 - var12, var10);
         line(var0, var7, var8 + var12, var7, var8 + var9 - 18, var10);
         double var20 = (double)var6 * 0.5 * var2 / Math.tan(Math.toRadians((double)((Integer)var1.options.fov().get()).intValue()) * 0.5) * 0.001;
         int var15 = Math.min(20, (int)((double)var9 * 0.68 / Math.max(var20, 1.0)));

         for (int var16 = 1; var16 <= var15; var16 += Math.max(1, (int)Math.ceil(5.0 / var20))) {
            int var17 = (int)Math.round(var20 * (double)var16);
            if (var17 >= 7) {
               int var18 = var16 % 5 == 0 ? 6 : 3;
               line(var0, var7 - var18, var8 + var17, var7 + var18, var8 + var17, var10);
               line(var0, var7 - var17, var8 - var18, var7 - var17, var8 + var18, var10);
               line(var0, var7 + var17, var8 - var18, var7 + var17, var8 + var18, var10);
            }
         }

         var0.fill(var7, var8, var7 + 2, var8 + 2, var11);
         int var22 = (int)((double)var9 * 0.76);
         var0.fill(var7 - var9 + 18, var8 - 1, var7 - var22, var8 + 2, var10);
         var0.fill(var7 + var22, var8 - 1, var7 + var9 - 18, var8 + 2, var10);
         var0.fill(var7 - 1, var8 + var22, var7 + 2, var8 + var9 - 18, var10);
      }
   }

   private static int half(int var0, int var1) {
      return Math.abs(var1) >= var0 ? 0 : (int)Math.sqrt((double)(var0 * var0 - var1 * var1));
   }

   private static void line(GuiGraphics var0, int var1, int var2, int var3, int var4, int var5) {
      var0.fill(Math.min(var1, var3), Math.min(var2, var4), Math.max(var1, var3) + 1, Math.max(var2, var4) + 1, var5);
   }

   private FieldScopeView() {
   }
}
