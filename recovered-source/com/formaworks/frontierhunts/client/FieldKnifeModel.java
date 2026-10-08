package com.formaworks.frontierhunts.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3f;

public final class FieldKnifeModel {
   private static final FieldKnifeModel.Vertex[] MESH = create();

   public static void draw(PoseStack var0, VertexConsumer var1, int var2) {
      Pose var3 = var0.last();

      for (FieldKnifeModel.Vertex var7 : MESH) {
         HuntMesh.vertex(var1, var3, var2, var7.color, var7.x, var7.y, var7.z, 0.5F, 0.5F, var7.nx, var7.ny, var7.nz);
      }
   }

   private static FieldKnifeModel.Vertex[] create() {
      ArrayList var0 = new ArrayList();
      float[][] var1 = new float[][]{
         {-0.004F, -0.015F, 0.016F, 0.0022F},
         {0.004F, -0.015F, 0.016F, 0.0022F},
         {0.012F, -0.015F, 0.018F, 0.0022F},
         {0.024F, -0.015F, 0.0188F, 0.0022F},
         {0.047F, -0.0148F, 0.0192F, 0.00205F},
         {0.069F, -0.0143F, 0.018F, 0.00185F},
         {0.087F, -0.0133F, 0.0138F, 0.0015F},
         {0.101F, -0.0112F, 0.0065F, 0.001F},
         {0.111F, -0.0088F, -0.0018F, 5.0E-4F},
         {0.116F, -0.007F, -0.007F, 6.0E-5F}
      };
      var1 = refine(var1, 6);

      for (int var2 = 0; var2 < var1.length - 1; var2++) {
         float[] var3 = var1[var2];
         float[] var4 = var1[var2 + 1];

         for (int var8 : new int[]{-1, 1}) {
            float[][] var9 = bladeRing(var3, var8);
            float[][] var10 = bladeRing(var4, var8);

            for (int var11 = 0; var11 < 3; var11++) {
               int var12 = var2 == 0 ? 8752783 : (var11 == 0 ? 9081749 : (var11 == 1 ? 10989488 : 12962503));
               face(var0, var9[var11], var10[var11], var10[var11 + 1], var9[var11 + 1], var12, 0.0F, 0.0F, (float)var8);
            }
         }

         face(
            var0,
            v(var3[1], var3[0], -var3[3]),
            v(var4[1], var4[0], -var4[3]),
            v(var4[1], var4[0], var4[3]),
            v(var3[1], var3[0], var3[3]),
            7042939,
            -1.0F,
            0.0F,
            0.0F
         );
         float var21 = var3[0] <= 0.004F ? var3[3] : 6.0E-5F;
         float var24 = var4[0] <= 0.004F ? var4[3] : 6.0E-5F;
         face(
            var0, v(var3[2], var3[0], -var21), v(var4[2], var4[0], -var24), v(var4[2], var4[0], var24), v(var3[2], var3[0], var21), 13817553, 1.0F, 0.0F, 0.0F
         );
      }

      float[] var17 = var1[0];
      face(
         var0,
         v(var17[1], var17[0], -var17[3]),
         v(var17[2], var17[0], -var17[3]),
         v(var17[2], var17[0], var17[3]),
         v(var17[1], var17[0], var17[3]),
         7042939,
         0.0F,
         -1.0F,
         0.0F
      );
      float[][] var18 = new float[][]{
         {-0.119F, -0.006F, 0.004F},
         {-0.116F, -0.013F, 0.011F},
         {-0.104F, -0.0155F, 0.014F},
         {-0.086F, -0.015F, 0.016F},
         {-0.064F, -0.0138F, 0.017F},
         {-0.042F, -0.012F, 0.0145F},
         {-0.023F, -0.011F, 0.01F},
         {-0.015F, -0.013F, 0.013F},
         {-0.009F, -0.015F, 0.02F},
         {-0.004F, -0.015F, 0.016F}
      };

      for (int var19 = 0; var19 < var18.length - 1; var19++) {
         float[] var22 = var18[var19];
         float[] var25 = var18[var19 + 1];

         for (int var34 : new int[]{-1, 1}) {
            face(
               var0,
               v(var22[1], var22[0], (float)var34 * 0.0022F),
               v(var22[2], var22[0], (float)var34 * 0.0022F),
               v(var25[2], var25[0], (float)var34 * 0.0022F),
               v(var25[1], var25[0], (float)var34 * 0.0022F),
               10133403,
               0.0F,
               0.0F,
               (float)var34
            );
         }

         face(
            var0,
            v(var22[1], var22[0], -0.0022F),
            v(var25[1], var25[0], -0.0022F),
            v(var25[1], var25[0], 0.0022F),
            v(var22[1], var22[0], 0.0022F),
            10923690,
            -1.0F,
            0.0F,
            0.0F
         );
         face(
            var0,
            v(var22[2], var22[0], -0.0022F),
            v(var25[2], var25[0], -0.0022F),
            v(var25[2], var25[0], 0.0022F),
            v(var22[2], var22[0], 0.0022F),
            10923690,
            1.0F,
            0.0F,
            0.0F
         );
      }

      float[] var20 = var18[0];
      face(
         var0,
         v(var20[1], var20[0], -0.0022F),
         v(var20[2], var20[0], -0.0022F),
         v(var20[2], var20[0], 0.0022F),
         v(var20[1], var20[0], 0.0022F),
         9805208,
         0.0F,
         -1.0F,
         0.0F
      );

      for (int var30 : new int[]{-1, 1}) {
         for (int var32 = 0; var32 < 80; var32++) {
            for (int var35 = 0; var35 < 20; var35++) {
               float var37 = -0.114F + (float)var32 * 0.104F / 80.0F;
               float var39 = -0.114F + (float)(var32 + 1) * 0.104F / 80.0F;
               float var13 = (float)var35 / 20.0F;
               float var14 = (float)(var35 + 1) / 20.0F;
               int var15 = (var32 / 2 + var35 / 2 & 1) == 0 ? 6644552 : 6118723;
               face(
                  var0,
                  scale(var18, var37, var13, var30),
                  scale(var18, var39, var13, var30),
                  scale(var18, var39, var14, var30),
                  scale(var18, var37, var14, var30),
                  var15,
                  0.0F,
                  0.0F,
                  (float)var30
               );
            }
         }

         for (float var40 : new float[]{-0.026F, -0.066F, -0.103F}) {
            screw(var0, var40, var30, scale(var18, var40, 0.5F, var30)[2]);
         }
      }

      return var0.toArray(FieldKnifeModel.Vertex[]::new);
   }

   private static float[][] bladeRing(float[] var0, int var1) {
      float var2 = var0[2] - var0[1];
      float var3 = var0[1] + var2 * 0.36F;
      float var4 = var0[2] - Math.min(7.5E-4F, var2 * 0.15F);
      boolean var5 = var0[0] <= 0.004F;
      return new float[][]{
         v(var0[1], var0[0], (float)var1 * var0[3]),
         v(var3, var0[0], (float)var1 * var0[3]),
         v(var4, var0[0], (float)var1 * (var5 ? var0[3] : Math.min(2.8E-4F, var0[3]))),
         v(var0[2], var0[0], (float)var1 * (var5 ? var0[3] : 6.0E-5F))
      };
   }

   private static float[][] refine(float[][] var0, int var1) {
      float[][] var2 = new float[(var0.length - 1) * var1 + 1][4];

      for (int var3 = 0; var3 < var0.length - 1; var3++) {
         for (int var4 = 0; var4 < var1; var4++) {
            float[] var5 = var0[Math.max(0, var3 - 1)];
            float[] var6 = var0[var3];
            float[] var7 = var0[var3 + 1];
            float[] var8 = var0[Math.min(var0.length - 1, var3 + 2)];
            float var9 = (float)var4 / (float)var1;
            float var10 = var9 * var9;
            float var11 = var10 * var9;
            float var12 = var7[0] - var6[0];
            float[] var13 = var2[var3 * var1 + var4];
            var13[0] = var6[0] + var12 * var9;

            for (int var14 = 1; var14 < 4; var14++) {
               float var15 = (var7[var14] - var5[var14]) / (var7[0] - var5[0]) * var12;
               float var16 = (var8[var14] - var6[var14]) / (var8[0] - var6[0]) * var12;
               var13[var14] = (2.0F * var11 - 3.0F * var10 + 1.0F) * var6[var14]
                  + (var11 - 2.0F * var10 + var9) * var15
                  + (-2.0F * var11 + 3.0F * var10) * var7[var14]
                  + (var11 - var10) * var16;
            }

            var13[2] = Math.max(var13[1], var13[2]);
            var13[3] = Math.max(6.0E-5F, var13[3]);
         }
      }

      var2[var2.length - 1] = var0[var0.length - 1];
      return var2;
   }

   private static float[] scale(float[][] var0, float var1, float var2, int var3) {
      int var4 = 0;

      while (var4 < var0.length - 2 && var0[var4 + 1][0] < var1) {
         var4++;
      }

      float[] var5 = var0[var4];
      float[] var6 = var0[var4 + 1];
      float var7 = (var1 - var5[0]) / (var6[0] - var5[0]);
      float var8 = var5[1] + (var6[1] - var5[1]) * var7 + 8.0E-4F;
      float var9 = var5[2] + (var6[2] - var5[2]) * var7 - 8.0E-4F;
      float var10 = Math.min(1.0F, Math.min((var1 + 0.114F) / 0.006F, (-0.01F - var1) / 0.006F));
      float var11 = (float)Math.pow(Math.max(0.0, Math.sin(Math.PI * (double)var2)), 0.32) * Math.max(0.0F, var10);
      return v(var8 + (var9 - var8) * var2, var1, (float)var3 * (0.0022F + 0.0074F * var11));
   }

   private static void screw(List<FieldKnifeModel.Vertex> var0, float var1, int var2, float var3) {
      float var4 = 0.0028F;
      float var5 = 0.00112F;
      float var6 = var3 + (float)var2 * 1.6E-4F;
      float var7 = var6 - (float)var2 * 0.001F;

      for (int var8 = 0; var8 < 24; var8++) {
         double var9 = (double)var8 * Math.PI / 12.0;
         double var11 = (double)(var8 + 1) * Math.PI / 12.0;
         float[] var13 = v((float)Math.cos(var9) * var4, var1 + (float)Math.sin(var9) * var4, var6);
         float[] var14 = v((float)Math.cos(var11) * var4, var1 + (float)Math.sin(var11) * var4, var6);
         float[] var15 = v((float)Math.cos(var9) * var5, var1 + (float)Math.sin(var9) * var5, var6);
         float[] var16 = v((float)Math.cos(var11) * var5, var1 + (float)Math.sin(var11) * var5, var6);
         face(var0, var13, var14, var16, var15, 4541515, 0.0F, 0.0F, (float)var2);
         face(var0, var15, var16, v(var16[0], var16[1], var7), v(var15[0], var15[1], var7), 2435881, -((float)Math.cos(var9)), -((float)Math.sin(var9)), 0.0F);
         face(var0, v(0.0F, var1, var7), v(var15[0], var15[1], var7), v(var16[0], var16[1], var7), v(0.0F, var1, var7), 1580572, 0.0F, 0.0F, (float)var2);
      }
   }

   private static float[] v(float var0, float var1, float var2) {
      return new float[]{var0, var1, var2};
   }

   private static void face(
      List<FieldKnifeModel.Vertex> var0, float[] var1, float[] var2, float[] var3, float[] var4, int var5, float var6, float var7, float var8
   ) {
      Vector3f var9 = new Vector3f(var2[0] - var1[0], var2[1] - var1[1], var2[2] - var1[2]).cross(var3[0] - var1[0], var3[1] - var1[1], var3[2] - var1[2]);
      if (var9.lengthSquared() < 1.0E-16F) {
         var9.set(var2[0] - var1[0], var2[1] - var1[1], var2[2] - var1[2]).cross(var4[0] - var1[0], var4[1] - var1[1], var4[2] - var1[2]);
      }

      if (!(var9.lengthSquared() < 1.0E-16F)) {
         if (var9.dot(var6, var7, var8) < 0.0F) {
            float[] var10 = var2;
            var2 = var4;
            var4 = var10;
            var9.negate();
         }

         var9.normalize();

         for (float[] var13 : new float[][]{var1, var2, var3, var4}) {
            var0.add(new FieldKnifeModel.Vertex(var13[0], var13[1], var13[2], var9.x, var9.y, var9.z, var5));
         }
      }
   }

   private FieldKnifeModel() {
   }

   private static record Vertex(float x, float y, float z, float nx, float ny, float nz, int color) {
   }
}
