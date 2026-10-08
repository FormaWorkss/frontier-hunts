package com.formaworks.frontierhunts.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class CubeAnimal {
   private static final int[] TRI_ORDER = new int[]{0, 1, 2, 0, 2, 3};
   private static final int[][] FACE_CORNERS = new int[][]{{0, 3, 2, 1}, {5, 6, 7, 4}, {4, 7, 3, 0}, {1, 2, 6, 5}, {3, 7, 6, 2}, {4, 0, 1, 5}};
   private static final Vector3f[] C = new Vector3f[8];
   private static final Vector3f E0 = new Vector3f();
   private static final Vector3f E1 = new Vector3f();
   private static final Vector3f N = new Vector3f();
   public static final int OVERLAY;

   public static void draw(
      Pose var0,
      VertexConsumer var1,
      int var2,
      int var3,
      int[] var4,
      float[] var5,
      int var6,
      Matrix4f[] var7,
      float var8,
      float var9,
      float var10,
      float var11,
      long var12
   ) {
      Matrix4f var14 = var0.pose();
      Matrix3f var15 = var0.normal();
      int var16 = var4.length;

      for (int var17 = 0; var17 < var16; var17++) {
         if (var17 >= 64 || (var12 & 1L << var17) == 0L) {
            int var18 = var4[var17];
            if (var18 >= 0 && var18 < var7.length) {
               Matrix4f var19 = var7[var18];
               int var20 = var17 * var6;
               float var21 = var5[var20];
               float var22 = var5[var20 + 1];
               float var23 = var5[var20 + 2];
               float var24 = var5[var20 + 3];
               float var25 = var5[var20 + 4];
               float var26 = var5[var20 + 5];
               float var27 = var5[var20 + 6];
               float var28 = var5[var20 + 7];
               float var29 = var5[var20 + 8];
               float var30 = var5[var20 + 9];
               float var31 = var5[var20 + 10];
               float var32 = var5[var20 + 11];

               for (int var33 = 0; var33 < 8; var33++) {
                  float var34 = (float)(var33 != 1 && var33 != 2 && var33 != 5 && var33 != 6 ? 0 : 1);
                  float var35 = (float)(var33 != 2 && var33 != 3 && var33 != 6 && var33 != 7 ? 0 : 1);
                  float var36 = var33 >= 4 ? 1.0F : 0.0F;
                  float var37 = var21 + var24 * var34 + var27 * var35 + var30 * var36;
                  float var38 = var22 + var25 * var34 + var28 * var35 + var31 * var36;
                  float var39 = var23 + var26 * var34 + var29 * var35 + var32 * var36;
                  C[var33]
                     .set(
                        var19.m00() * var37 + var19.m10() * var38 + var19.m20() * var39 + var19.m30(),
                        var19.m01() * var37 + var19.m11() * var38 + var19.m21() * var39 + var19.m31(),
                        var19.m02() * var37 + var19.m12() * var38 + var19.m22() * var39 + var19.m32()
                     );
               }

               int var42 = var20 + 12;

               for (int var43 = 0; var43 < 6; var43++) {
                  int[] var44 = FACE_CORNERS[var43];
                  Vector3f var45 = C[var44[0]];
                  Vector3f var46 = C[var44[1]];
                  Vector3f var47 = C[var44[2]];
                  E0.set(var46).sub(var45);
                  E1.set(var47).sub(var45);
                  N.set(E0).cross(E1);
                  if (!(N.lengthSquared() < 1.0E-12F)) {
                     N.normalize();

                     for (int var48 = 0; var48 < 6; var48++) {
                        int var40 = TRI_ORDER[var48];
                        Vector3f var41 = C[var44[var40]];
                        var1.addVertex(var14, var41.x, var41.y, var41.z)
                           .setColor(var8, var9, var10, var11)
                           .setUv(var5[var42 + var43 * 8 + var40 * 2], var5[var42 + var43 * 8 + var40 * 2 + 1])
                           .setOverlay(var3)
                           .setLight(var2)
                           .setNormal(
                              var15.m00() * N.x + var15.m10() * N.y + var15.m20() * N.z,
                              var15.m01() * N.x + var15.m11() * N.y + var15.m21() * N.z,
                              var15.m02() * N.x + var15.m12() * N.y + var15.m22() * N.z
                           );
                     }
                  }
               }
            }
         }
      }
   }

   public static void draw(
      Pose var0, VertexConsumer var1, int var2, int var3, int[] var4, float[] var5, int var6, Matrix4f[] var7, float var8, float var9, float var10
   ) {
      draw(var0, var1, var2, var3, var4, var5, var6, var7, var8, var9, var10, 1.0F, 0L);
   }

   public static void quadBox(
      Pose var0,
      VertexConsumer var1,
      int var2,
      int var3,
      Vector3f var4,
      Vector3f var5,
      Vector3f var6,
      Vector3f var7,
      float var8,
      float var9,
      float var10,
      float var11,
      float var12,
      float var13,
      float var14
   ) {
      Matrix4f var15 = var0.pose();
      Matrix3f var16 = var0.normal();

      for (int var17 = 0; var17 < 8; var17++) {
         float var18 = (float)(var17 != 1 && var17 != 2 && var17 != 5 && var17 != 6 ? 0 : 1);
         float var19 = (float)(var17 != 2 && var17 != 3 && var17 != 6 && var17 != 7 ? 0 : 1);
         float var20 = var17 >= 4 ? 1.0F : 0.0F;
         C[var17]
            .set(
               var4.x + var5.x * var18 + var6.x * var19 + var7.x * var20,
               var4.y + var5.y * var18 + var6.y * var19 + var7.y * var20,
               var4.z + var5.z * var18 + var6.z * var19 + var7.z * var20
            );
      }

      for (int var27 = 0; var27 < 6; var27++) {
         int[] var28 = FACE_CORNERS[var27];
         Vector3f var29 = C[var28[0]];
         Vector3f var30 = C[var28[1]];
         Vector3f var21 = C[var28[2]];
         E0.set(var30).sub(var29);
         E1.set(var21).sub(var29);
         N.set(E0).cross(E1);
         if (!(N.lengthSquared() < 1.0E-12F)) {
            N.normalize();
            float[] var22 = new float[]{var8, var8, var10, var10};
            float[] var23 = new float[]{var11, var9, var9, var11};

            for (int var24 = 0; var24 < 6; var24++) {
               int var25 = TRI_ORDER[var24];
               Vector3f var26 = C[var28[var25]];
               var1.addVertex(var15, var26.x, var26.y, var26.z)
                  .setColor(var12, var13, var14, 1.0F)
                  .setUv(var22[var25], var23[var25])
                  .setOverlay(var3)
                  .setLight(var2)
                  .setNormal(
                     var16.m00() * N.x + var16.m10() * N.y + var16.m20() * N.z,
                     var16.m01() * N.x + var16.m11() * N.y + var16.m21() * N.z,
                     var16.m02() * N.x + var16.m12() * N.y + var16.m22() * N.z
                  );
            }
         }
      }
   }

   private CubeAnimal() {
   }

   static {
      for (int var0 = 0; var0 < 8; var0++) {
         C[var0] = new Vector3f();
      }

      OVERLAY = OverlayTexture.NO_OVERLAY;
   }
}
