package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.FrontierHunts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

public final class FrontierUi {
   /** [1.2.9] nesting depth of {@link #batch}: only the outermost call batches */
   private static int batchDepth;

   private FrontierUi() {
   }

   /**
    * [1.2.9] Draws {@code body} as one batch: every fill and line of text inside it goes to the GPU together when it
    * ends, instead of one draw call each. A rounded rectangle is drawn row by row at full screen resolution, so at a 4K
    * GUI scale one card was ~200 separate draws and the Handbook ran at 15 fps. Only for fills and text: anything that
    * draws straight away (blit, items) would land under the batched fills.
    */
   public static void batch(GuiGraphics g, Runnable body) {
      if (batchDepth > 0) {
         body.run();
         return;
      }
      batchDepth++;
      try {
         g.drawManaged(body);
      } finally {
         batchDepth--;
      }
   }

   static Font font() {
      return Minecraft.getInstance().font;
   }

   public static MutableComponent c(String var0, FrontierUi.Size var1) {
      return Component.literal(var0 == null ? "" : var0).withStyle(var1x -> var1x.withFont(var1.font));
   }

   public static int width(String var0, FrontierUi.Size var1) {
      return font().width(c(var0, var1));
   }

   public static float text(GuiGraphics var0, String var1, float var2, float var3, int var4, FrontierUi.Size var5) {
      FormattedCharSequence var6 = c(var1, var5).getVisualOrderText();
      var0.drawString(font(), var6, var2, var3, var4, false);
      return var2 + (float)font().width(var6);
   }

   public static void center(GuiGraphics var0, String var1, float var2, float var3, int var4, FrontierUi.Size var5) {
      text(var0, var1, var2 - (float)width(var1, var5) / 2.0F, var3, var4, var5);
   }

   public static void right(GuiGraphics var0, String var1, float var2, float var3, int var4, FrontierUi.Size var5) {
      text(var0, var1, var2 - (float)width(var1, var5), var3, var4, var5);
   }

   public static String fit(String var0, int var1, FrontierUi.Size var2) {
      if (var0 == null) {
         return "";
      } else if (width(var0, var2) <= var1) {
         return var0;
      } else {
         String var3 = "…";
         int var4 = width(var3, var2);
         int var5 = 0;
         int var6 = var0.length();

         while (var5 < var6) {
            int var7 = (var5 + var6 + 1) / 2;
            if (width(var0.substring(0, var7), var2) + var4 <= var1) {
               var5 = var7;
            } else {
               var6 = var7 - 1;
            }
         }

         return var0.substring(0, var5).stripTrailing() + var3;
      }
   }

   public static int wrap(GuiGraphics var0, String var1, int var2, int var3, int var4, int var5, FrontierUi.Size var6) {
      MutableComponent var7 = c(var1, var6);
      int var8 = lineHeight(var6);
      int var9 = var3;

      for (FormattedCharSequence var11 : font().split(var7, var4)) {
         var0.drawString(font(), var11, var2, var9, var5, false);
         var9 += var8;
      }

      return var9 - var3;
   }

   public static int wrapHeight(String var0, int var1, FrontierUi.Size var2) {
      return font().split(c(var0, var2), var1).size() * lineHeight(var2);
   }

   public static int lineHeight(FrontierUi.Size var0) {
      return switch (var0) {
         case SMALL -> 8;
         default -> 10;
         case TITLE -> 14;
         case BRAND -> 12;
      };
   }

   static double scale() {
      return Minecraft.getInstance().getWindow().getGuiScale();
   }

   public static void rect(GuiGraphics var0, float var1, float var2, float var3, float var4, float var5, int var6) {
      if (!(var3 <= 0.0F) && !(var4 <= 0.0F) && var6 >>> 24 != 0) {
         if (var5 * scale() >= 1.0 && batchDepth == 0) { // [1.2.9] a rounded one is many rows: one batch
            batch(var0, () -> rect(var0, var1, var2, var3, var4, var5, var6));
            return;
         }
         double var7 = scale();
         var0.pose().pushPose();
         var0.pose().scale((float)(1.0 / var7), (float)(1.0 / var7), 1.0F);
         int var9 = (int)Math.round((double)var1 * var7);
         int var10 = (int)Math.round((double)var2 * var7);
         int var11 = (int)Math.round((double)(var1 + var3) * var7);
         int var12 = (int)Math.round((double)(var2 + var4) * var7);
         double var13 = Math.min((double)var5 * var7, (double)Math.min(var11 - var9, var12 - var10) / 2.0);
         int var15 = (int)Math.ceil(var13);
         if (var15 <= 0) {
            var0.fill(var9, var10, var11, var12, var6);
         } else {
            var0.fill(var9, var10 + var15, var11, var12 - var15, var6);

            for (int var16 = 0; var16 < var15; var16++) {
               double var17 = var13 - (double)var16 - 0.5;
               double var19 = var13 - Math.sqrt(Math.max(0.0, var13 * var13 - var17 * var17));
               int var21 = (int)Math.ceil(var19);
               float var22 = (float)((double)var21 - var19);
               int var23 = var10 + var16;
               int var24 = var12 - 1 - var16;
               var0.fill(var9 + var21, var23, var11 - var21, var23 + 1, var6);
               var0.fill(var9 + var21, var24, var11 - var21, var24 + 1, var6);
               if (var21 > 0 && var22 > 0.02F) {
                  int var25 = fade(var6, var22);
                  var0.fill(var9 + var21 - 1, var23, var9 + var21, var23 + 1, var25);
                  var0.fill(var11 - var21, var23, var11 - var21 + 1, var23 + 1, var25);
                  var0.fill(var9 + var21 - 1, var24, var9 + var21, var24 + 1, var25);
                  var0.fill(var11 - var21, var24, var11 - var21 + 1, var24 + 1, var25);
               }
            }
         }

         var0.pose().popPose();
      }
   }

   public static void outline(GuiGraphics var0, float var1, float var2, float var3, float var4, float var5, int var6, int var7) {
      if (batchDepth == 0) { // [1.2.9]
         batch(var0, () -> outline(var0, var1, var2, var3, var4, var5, var6, var7));
         return;
      }
      double var8 = scale();
      float var10 = (float)(1.0 / var8);
      rect(var0, var1, var2, var3, var4, var5, var6);
      rect(var0, var1 + var10, var2 + var10, var3 - 2.0F * var10, var4 - 2.0F * var10, Math.max(0.0F, var5 - var10), var7);
   }

   public static void circle(GuiGraphics var0, float var1, float var2, float var3, int var4) {
      rect(var0, var1 - var3, var2 - var3, var3 * 2.0F, var3 * 2.0F, var3, var4);
   }

   public static void shadow(GuiGraphics var0, float var1, float var2, float var3, float var4, float var5, float var6) {
      if (batchDepth == 0) { // [1.2.9]
         batch(var0, () -> shadow(var0, var1, var2, var3, var4, var5, var6));
         return;
      }
      byte var7 = 6;

      for (int var8 = var7; var8 >= 1; var8--) {
         float var9 = var6 * (float)var8 / (float)var7;
         int var10 = (int)(34.0F * (1.0F - (float)(var8 - 1) / (float)var7));
         rect(var0, var1 - var9, var2 - var9 + var6 * 0.35F, var3 + 2.0F * var9, var4 + 2.0F * var9, var5 + var9, var10 << 24);
      }
   }

   static int fade(int var0, float var1) {
      int var2 = (int)((float)(var0 >>> 24) * Math.max(0.0F, Math.min(1.0F, var1)));
      return var2 << 24 | var0 & 16777215;
   }

   public static enum Size {
      SMALL("ui_small"),
      BODY("ui"),
      STRONG("ui_bold"),
      TITLE("ui_title"),
      BRAND("ui_brand");

      final ResourceLocation font;

      private Size(String nullxx) {
         this.font = FrontierHunts.id(nullxx);
      }
   }
}
