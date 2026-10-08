package com.formaworks.frontierhunts.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class HuntMesh {
   private static final HuntMesh.Vertex[][] SPHERES = new HuntMesh.Vertex[][]{sphere(8, 6), sphere(12, 8), sphere(18, 12)};

   private static HuntMesh.Vertex[] sphere(int var0, int var1) {
      HuntMesh.Vertex[] var2 = new HuntMesh.Vertex[var0 * var1 * 4];
      int var3 = 0;

      for (int var4 = 0; var4 < var1; var4++) {
         for (int var5 = 0; var5 < var0; var5++) {
            var2[var3++] = spherical(var5, var4, var0, var1);
            var2[var3++] = spherical(var5 + 1, var4, var0, var1);
            var2[var3++] = spherical(var5 + 1, var4 + 1, var0, var1);
            var2[var3++] = spherical(var5, var4 + 1, var0, var1);
         }
      }

      return var2;
   }

   private static HuntMesh.Vertex spherical(int var0, int var1, int var2, int var3) {
      double var4 = (Math.PI * 2) * (double)var0 / (double)var2;
      double var6 = Math.PI * (double)var1 / (double)var3;
      return new HuntMesh.Vertex(
         (float)(Math.sin(var6) * Math.cos(var4)),
         (float)Math.cos(var6),
         (float)(Math.sin(var6) * Math.sin(var4)),
         (float)var0 / (float)var2,
         (float)var1 / (float)var3
      );
   }

   public static void ell(
      PoseStack var0, VertexConsumer var1, int var2, int var3, double var4, double var6, double var8, double var10, double var12, double var14
   ) {
      ellipsoid(var0, var1, var2, var3, 1, var4, var6, var8, var10, var12, var14);
   }

   public static void ellipsoid(
      PoseStack var0, VertexConsumer var1, int var2, int var3, int var4, double var5, double var7, double var9, double var11, double var13, double var15
   ) {
      var0.pushPose();
      var0.translate(var5, var7, var9);
      var0.scale((float)var11, (float)var13, (float)var15);
      Pose var17 = var0.last();

      for (HuntMesh.Vertex var21 : SPHERES[var4]) {
         vertex(var1, var17, var2, var3, var21.x, var21.y, var21.z, var21.u, var21.v, var21.x, var21.y, var21.z);
      }

      var0.popPose();
   }

   public static void tube(
      PoseStack var0,
      VertexConsumer var1,
      int var2,
      int var3,
      double var4,
      double var6,
      double var8,
      double var10,
      double var12,
      double var14,
      double var16,
      double var18,
      int var20
   ) {
      double var21 = var10 - var4;
      double var23 = var12 - var6;
      double var25 = var14 - var8;
      double var27 = Math.sqrt(var21 * var21 + var23 * var23 + var25 * var25);
      if (!(var27 < 1.0E-6)) {
         var0.pushPose();
         var0.translate(var4, var6, var8);
         var0.mulPose(
            new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), new Vector3f((float)(var21 / var27), (float)(var23 / var27), (float)(var25 / var27)))
         );
         Pose var29 = var0.last();
         float var30 = (float)((var16 - var18) / var27);

         for (int var31 = 0; var31 < var20; var31++) {
            double var32 = (Math.PI * 2) * (double)var31 / (double)var20;
            double var34 = (Math.PI * 2) * (double)(var31 + 1) / (double)var20;
            float var36 = (float)Math.cos(var32);
            float var37 = (float)Math.sin(var32);
            float var38 = (float)Math.cos(var34);
            float var39 = (float)Math.sin(var34);
            float var40 = (float)var31 / (float)var20;
            float var41 = (float)(var31 + 1) / (float)var20;
            vertex(var1, var29, var2, var3, var36 * (float)var16, 0.0F, var37 * (float)var16, var40, 0.0F, var36, var30, var37);
            vertex(var1, var29, var2, var3, var36 * (float)var18, (float)var27, var37 * (float)var18, var40, 1.0F, var36, var30, var37);
            vertex(var1, var29, var2, var3, var38 * (float)var18, (float)var27, var39 * (float)var18, var41, 1.0F, var38, var30, var39);
            vertex(var1, var29, var2, var3, var38 * (float)var16, 0.0F, var39 * (float)var16, var41, 0.0F, var38, var30, var39);
            vertex(var1, var29, var2, var3, 0.0F, 0.0F, 0.0F, 0.5F, 0.5F, 0.0F, -1.0F, 0.0F);
            vertex(var1, var29, var2, var3, var36 * (float)var16, 0.0F, var37 * (float)var16, var40, 0.0F, 0.0F, -1.0F, 0.0F);
            vertex(var1, var29, var2, var3, var38 * (float)var16, 0.0F, var39 * (float)var16, var41, 0.0F, 0.0F, -1.0F, 0.0F);
            vertex(var1, var29, var2, var3, 0.0F, 0.0F, 0.0F, 0.5F, 0.5F, 0.0F, -1.0F, 0.0F);
            vertex(var1, var29, var2, var3, 0.0F, (float)var27, 0.0F, 0.5F, 0.5F, 0.0F, 1.0F, 0.0F);
            vertex(var1, var29, var2, var3, var38 * (float)var18, (float)var27, var39 * (float)var18, var41, 1.0F, 0.0F, 1.0F, 0.0F);
            vertex(var1, var29, var2, var3, var36 * (float)var18, (float)var27, var37 * (float)var18, var40, 1.0F, 0.0F, 1.0F, 0.0F);
            vertex(var1, var29, var2, var3, 0.0F, (float)var27, 0.0F, 0.5F, 0.5F, 0.0F, 1.0F, 0.0F);
         }

         var0.popPose();
      }
   }

   public static void vertex(
      VertexConsumer var0, Pose var1, int var2, int var3, float var4, float var5, float var6, float var7, float var8, float var9, float var10, float var11
   ) {
      var0.addVertex(var1.pose(), var4, var5, var6)
         .setColor(0xFF000000 | var3)
         .setUv(var7, var8)
         .setOverlay(OverlayTexture.NO_OVERLAY)
         .setLight(var2)
         .setNormal(var1, var9, var10, var11);
   }

   public static void leaf(PoseStack var0, VertexConsumer var1, int var2, int var3, boolean var4) {
      byte var5 = 12;
      byte var6 = 12;
      Pose var7 = var0.last();

      for (int var8 = 0; var8 < var5; var8++) {
         for (int var9 = 0; var9 < var6; var9++) {
            boolean var10 = var9 >= 7 && var9 <= 10 && var8 >= 2 && var8 <= 10;
            if (var10 == var4) {
               for (int var14 : new int[]{0, 1, 2, 3}) {
                  float var15 = (float)(var8 + (var14 != 1 && var14 != 2 ? 0 : 1)) / (float)var5;
                  double var16 = (double)((var9 + (var14 >= 2 ? 1 : 0)) * 2) * Math.PI / (double)var6;
                  float var18 = (float)Math.pow(Math.sin(Math.PI * (double)var15), 0.72);
                  float var19 = (float)Math.cos(var16) * 0.061F * var18;
                  float var20 = (float)Math.sin(var16) * 0.014F * var18 - 0.015F * var15;
                  float var21 = (float)Math.cos(var16) * 0.24F;
                  float var22 = (float)Math.sin(var16);
                  float var23 = -((float)Math.cos(Math.PI * (double)var15)) * 0.35F;
                  vertex(var1, var7, var2, var3, var19, var15 * 0.23F, var20, (float)var9 / (float)var6, var15, var21, var23, var22);
               }
            }
         }
      }
   }

   public static void hoof(PoseStack var0, VertexConsumer var1, int var2) {
      float[][] var3 = new float[][]{
         {-0.022F, -0.043F}, {-0.015F, -0.064F}, {0.008F, -0.068F}, {0.022F, -0.043F}, {0.021F, 0.019F}, {0.0F, 0.032F}, {-0.021F, 0.019F}
      };
      Pose var4 = var0.last();

      for (int var8 : new int[]{-1, 1}) {
         for (int var9 = 0; var9 < var3.length; var9++) {
            float[] var10 = var3[var9];
            float[] var11 = var3[(var9 + 1) % var3.length];
            float var12 = var11[1] - var10[1];
            float var13 = var10[0] - var11[0];
            float var14 = (float)Math.hypot((double)var12, (double)var13);
            var12 /= var14;
            var13 /= var14;

            for (int var18 : new int[]{0, 1, 2, 3}) {
               float[] var19 = var18 >= 2 ? var11 : var10;
               boolean var20 = var18 == 1 || var18 == 2;
               vertex(
                  var1,
                  var4,
                  var2,
                  3750707,
                  (float)var8 * 0.026F + var19[0] * (var20 ? 0.8F : 1.0F),
                  var20 ? 0.105F : 0.008F,
                  var19[1] * (var20 ? 0.78F : 1.0F),
                  0.5F,
                  0.5F,
                  var12,
                  0.12F,
                  var13
               );
            }

            for (boolean var27 : new boolean[]{false, true}) {
               float var28 = var27 ? 0.105F : 0.008F;
               float var29 = var27 ? 0.8F : 1.0F;
               float var21 = var27 ? 1.0F : -1.0F;
               vertex(var1, var4, var2, 3750707, (float)var8 * 0.026F, var28, 0.0F, 0.5F, 0.5F, 0.0F, var21, 0.0F);
               vertex(var1, var4, var2, 3750707, (float)var8 * 0.026F + var10[0] * var29, var28, var10[1] * var29, 0.5F, 0.5F, 0.0F, var21, 0.0F);
               vertex(var1, var4, var2, 3750707, (float)var8 * 0.026F + var11[0] * var29, var28, var11[1] * var29, 0.5F, 0.5F, 0.0F, var21, 0.0F);
               vertex(var1, var4, var2, 3750707, (float)var8 * 0.026F, var28, 0.0F, 0.5F, 0.5F, 0.0F, var21, 0.0F);
            }
         }
      }
   }

   public static void bowLimb(PoseStack var0, VertexConsumer var1, int var2, int var3, float var4) {
      byte var5 = 28;
      byte var6 = 8;
      Pose var7 = var0.last();

      for (int var8 = 0; var8 < var5; var8++) {
         for (int var9 = 0; var9 < var6; var9++) {
            for (int var13 : new int[]{0, 1, 2, 3}) {
               float var14 = (float)(var8 + (var13 != 1 && var13 != 2 ? 0 : 1)) / (float)var5;
               float var15 = 1.0F - var14;
               double var16 = (double)((var9 + (var13 >= 2 ? 1 : 0)) * 2) * Math.PI / (double)var6;
               float var18 = (float)var3
                  * (
                     var15 * var15 * var15 * 0.1F
                        + 3.0F * var15 * var15 * var14 * 0.4F
                        + 3.0F * var15 * var14 * var14 * 0.69F
                        + var14 * var14 * var14 * (0.74F - var4 * 0.065F)
                  );
               float var19 = 3.0F * var15 * var15 * var14 * (-0.08F + var4 * 0.025F)
                  + 3.0F * var15 * var14 * var14 * (-0.19F + var4 * 0.18F)
                  + var14 * var14 * var14 * (0.035F + var4 * 0.21F);
               float var20 = (float)var3 * (3.0F * var15 * var15 * 0.3F + 6.0F * var15 * var14 * 0.29F + 3.0F * var14 * var14 * (0.05F - var4 * 0.065F));
               float var21 = 3.0F * var15 * var15 * (-0.08F + var4 * 0.025F)
                  + 6.0F * var15 * var14 * (-0.11F + var4 * 0.155F)
                  + 3.0F * var14 * var14 * (0.225F + var4 * 0.03F);
               float var22 = (float)Math.hypot((double)var20, (double)var21);
               var20 /= var22;
               var21 /= var22;
               float var23 = 0.026F * (1.0F - 0.68F * var14);
               float var24 = 0.006F * (1.0F - 0.4F * var14);
               float var25 = (float)Math.cos(var16) * var23;
               float var26 = (float)Math.sin(var16) * var24;
               float var27 = (float)Math.cos(var16) * 0.23F;
               float var28 = -((float)Math.sin(var16)) * var21;
               float var29 = (float)Math.sin(var16) * var20;
               vertex(
                  var1,
                  var7,
                  var2,
                  var9 != 0 && var9 != 3 && var9 != 4 && var9 != 7 ? 6705209 : 10320719,
                  var25,
                  var18 - var21 * var26,
                  var19 + var20 * var26,
                  var14,
                  (float)var9 / (float)var6,
                  var27,
                  var28,
                  var29
               );
            }
         }
      }
   }

   private HuntMesh() {
   }

   private static record Vertex(float x, float y, float z, float u, float v) {
   }
}
