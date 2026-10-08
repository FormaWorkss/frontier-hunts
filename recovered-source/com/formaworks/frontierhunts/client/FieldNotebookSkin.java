package com.formaworks.frontierhunts.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

final class FieldNotebookSkin {
   static void atlas(GuiGraphics var0, int var1, int var2, int var3, int var4, int var5) {
      var0.fill(var1 - 4, var2 - 4, var1 + var3 + 4, var2 + var4 + 4, -13351349);
      var0.fill(var1 - 2, var2 - 2, var1 + var3 + 2, var2 + var4 + 2, -8218745);
      var0.fill(var1, var2, var1 + var3, var2 + var4, -1513771);
      var0.fill(var1, var2, var1 + var5, var2 + var4, -14072755);
      var0.fill(var1 + var5, var2, var1 + var5 + 4, var2 + var4, -5589854);

      for (int var6 = 0; var6 < 3; var6++) {
         int var7 = var2 + 24 + (var4 - 48) * var6 / 2;
         var0.fill(var1 + var5 - 5, var7 - 4, var1 + var5 + 9, var7 + 4, -8416625);
         var0.fill(var1 + var5 - 4, var7 - 3, var1 + var5 + 8, var7 - 1, -3091009);
         var0.fill(var1 + var5 - 4, var7 + 2, var1 + var5 + 8, var7 + 3, -12757671);
      }

      for (int var16 = 0; var16 < 4; var16++) {
         var0.fill(var1 + var3 - var16 - 1, var2 + var16 + 3, var1 + var3 - var16, var2 + var4 - var16, -4801627);
      }

      if (var4 > 350) {
         int var17 = var1 + var5 / 2;
         int var18 = var2 + var4 - 58;

         for (int var8 = 0; var8 < 5; var8++) {
            for (byte var9 = 0; var9 < 360; var9 += 4) {
               double var10 = Math.toRadians((double)var9);
               double var12 = (double)(9 + var8 * 5) * (1.0 + 0.14 * Math.sin(3.0 * var10));
               int var14 = var17 + (int)(Math.cos(var10) * var12);
               int var15 = var18 + (int)(Math.sin(var10) * var12 * 0.7);
               var0.fill(var14, var15, var14 + 1, var15 + 1, var8 % 2 == 0 ? -9927307 : -12032161);
            }
         }

         var0.drawCenteredString(Minecraft.getInstance().font, "FIELD ATLAS", var17, var18 + 30, -5325915);
      }
   }

   static void draw(GuiGraphics var0, int var1, int var2, int var3, int var4, int var5) {
      var0.fill(var1 - 3, var2 - 3, var1 + var3 + 3, var2 + var4 + 3, -12503770);
      var0.fill(var1 - 2, var2 - 2, var1 + var3 + 2, var2 + var4 + 2, -7835574);
      var0.fill(var1, var2, var1 + var3, var2 + var4, -1383472);
      var0.fill(var1, var2, var1 + var5, var2 + var4, -11978965);

      for (int var6 = 0; var6 < 4; var6++) {
         var0.fill(var1 + var3 - 2 - var6, var2 + 2 + var6, var1 + var3 - 1 - var6, var2 + var4 - var6, -3884646);
         var0.fill(var1 + var5 + 3, var2 + var4 - 2 - var6, var1 + var3 - var6, var2 + var4 - 1 - var6, -3884646);
      }

      var0.fillGradient(var1 + var5, var2, var1 + var5 + 12, var2 + var4, -4609660, -2042181);
      var0.fill(var1 + var5, var2, var1 + var5 + 2, var2 + var4, -6127541);

      for (byte var14 = 8; var14 < var4 - 5; var14 += 7) {
         var0.fill(var1 + 3, var2 + var14, var1 + 4, var2 + var14 + 3, -6519193);
         var0.fill(var1 + var5 - 5, var2 + var14, var1 + var5 - 4, var2 + var14 + 3, -6519193);
      }

      for (byte var15 = 8; var15 < var5 - 6; var15 += 7) {
         var0.fill(var1 + var15, var2 + 4, var1 + var15 + 3, var2 + 5, -6519193);
         var0.fill(var1 + var15, var2 + var4 - 5, var1 + var15 + 3, var2 + var4 - 4, -6519193);
      }

      if (var4 > 355) {
         int var16 = var1 + var5 / 2;
         int var7 = var2 + var4 - 67;
         int var8 = Math.min(27, var5 / 3);

         for (byte var9 = 0; var9 < 360; var9 += 3) {
            double var10 = Math.toRadians((double)var9);
            int var12 = var16 + (int)(Math.cos(var10) * (double)var8);
            int var13 = var7 + (int)(Math.sin(var10) * (double)var8);
            var0.fill(var12, var13, var12 + 1, var13 + 1, -9666459);
         }

         var0.fill(var16 - 1, var7 - var8 + 4, var16 + 1, var7 + var8 - 4, -6056087);
         var0.fill(var16 - var8 + 4, var7 - 1, var16 + var8 - 4, var7 + 1, -8219530);

         for (int var17 = 0; var17 < 12; var17++) {
            int var18 = (12 - var17) / 3;
            var0.fill(var16 - var18, var7 - var17, var16 + var18 + 1, var7 - var17 + 1, -3819648);
         }
      }
   }

   private FieldNotebookSkin() {
   }
}
