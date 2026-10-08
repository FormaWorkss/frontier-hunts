package com.formaworks.frontierhunts.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

final class OpticMask {
   private static final int SEGMENTS = 128;
   private static final double[] COS = new double[129];
   private static final double[] SIN = new double[129];

   static void draw(GuiGraphics var0, float var1, float var2, float var3, float var4, float var5, int var6, int var7, int var8, float var9, int var10) {
      VertexConsumer var11 = var0.bufferSource().getBuffer(RenderType.gui());
      Matrix4f var12 = var0.pose().last().pose();
      ring(var11, var12, var1, var2, var4, var5, var6, var6);
      if (var4 > var3) {
         ring(var11, var12, var1, var2, var3, var4, var8, var7);
      }

      if (var9 > 0.0F) {
         ring(var11, var12, var1, var2, var3 - var9, var3, var10 & 16777215, var10);
      }

      var0.flush();
   }

   static void ring(VertexConsumer var0, Matrix4f var1, float var2, float var3, float var4, float var5, int var6, int var7) {
      for (int var8 = 0; var8 < 128; var8++) {
         float var9 = var2 + (float)COS[var8] * var4;
         float var10 = var3 + (float)SIN[var8] * var4;
         float var11 = var2 + (float)COS[var8 + 1] * var4;
         float var12 = var3 + (float)SIN[var8 + 1] * var4;
         float var13 = var2 + (float)COS[var8 + 1] * var5;
         float var14 = var3 + (float)SIN[var8 + 1] * var5;
         float var15 = var2 + (float)COS[var8] * var5;
         float var16 = var3 + (float)SIN[var8] * var5;
         var0.addVertex(var1, var9, var10, 0.0F).setColor(var6);
         var0.addVertex(var1, var11, var12, 0.0F).setColor(var6);
         var0.addVertex(var1, var13, var14, 0.0F).setColor(var7);
         var0.addVertex(var1, var15, var16, 0.0F).setColor(var7);
      }
   }

   static void rect(VertexConsumer var0, Matrix4f var1, float var2, float var3, float var4, float var5, int var6) {
      var0.addVertex(var1, var2, var5, 0.0F).setColor(var6);
      var0.addVertex(var1, var4, var5, 0.0F).setColor(var6);
      var0.addVertex(var1, var4, var3, 0.0F).setColor(var6);
      var0.addVertex(var1, var2, var3, 0.0F).setColor(var6);
   }

   private OpticMask() {
   }

   static {
      for (int var0 = 0; var0 <= 128; var0++) {
         double var1 = (double)var0 * Math.PI * 2.0 / 128.0;
         COS[var0] = Math.cos(var1);
         SIN[var0] = Math.sin(var1);
      }
   }
}
