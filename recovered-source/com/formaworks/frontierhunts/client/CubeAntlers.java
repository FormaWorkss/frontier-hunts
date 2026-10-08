package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.hunting.AntlerDesign;
import com.formaworks.frontierhunts.hunting.DeerSkeleton;
import com.formaworks.frontierhunts.hunting.DeerTraits;
import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class CubeAntlers {
   private static final Vector3f A = new Vector3f();
   private static final Vector3f B = new Vector3f();
   private static final Vector3f AX = new Vector3f();
   private static final Vector3f AY = new Vector3f();
   private static final Vector3f AZ = new Vector3f();
   private static final Vector3f TMP = new Vector3f();
   private static final float[] ELK_BEAM = new float[]{
      0.077F,
      0.0F,
      0.0F,
      0.125F,
      0.075F,
      -0.04F,
      0.165F,
      0.16F,
      -0.03F,
      0.195F,
      0.25F,
      0.035F,
      0.205F,
      0.32F,
      0.14F,
      0.195F,
      0.36F,
      0.255F,
      0.165F,
      0.375F,
      0.355F
   };

   private static int steps(int var0, int var1) {
      int var2 = var0 >= 8 ? 7 : (var0 >= 6 ? 6 : (var0 >= 5 ? 5 : 4));
      return Math.max(2, Math.min(var1 - 1, var2));
   }

   public static void draw(PoseStack var0, VertexConsumer var1, int var2, DeerTraits var3, Matrix4f var4, int var5, float[] var6) {
      if (var3.buck() && var3.species().antlers != GameSpecies.Antlers.NONE) {
         if (var3.species().antlers == GameSpecies.Antlers.MESH) {
            grown(var0, var1, var2, var3, var4, var6);
         } else {
            AntlerDesign var7 = AntlerDesign.of(var3);
            DeerSkeleton var8 = DeerSkeleton.of(var3.species());
            float var9 = var6 == null ? 1.0F : var6[0];
            float var10 = var6 == null ? 1.0F : var6[1];
            float var11 = var6 == null ? 1.0F : var6[2];
            var0.pushPose();
            var0.mulPose(var4);
            var0.mulPose(var8.antlerFrame);
            Pose var12 = var0.last();
            int var13 = 0;

            for (AntlerDesign.Branch var15 : var7.branches) {
               float[] var16 = var15.points();
               float[] var17 = var15.radii();
               int var18 = var15.count();
               if (var18 < 2) {
                  var13++;
               } else {
                  int var19 = steps(var5, var18);

                  for (int var20 = 0; var20 < var19; var20++) {
                     int var21 = (int)Math.floor((double)var20 * (double)(var18 - 1) / (double)var19);
                     int var22 = (int)Math.floor((double)(var20 + 1) * (double)(var18 - 1) / (double)var19);
                     if (var22 <= var21) {
                        var22 = Math.min(var18 - 1, var21 + 1);
                     }

                     A.set(var16[var21 * 3], var16[var21 * 3 + 1], var16[var21 * 3 + 2]);
                     B.set(var16[var22 * 3], var16[var22 * 3 + 1], var16[var22 * 3 + 2]);
                     float var23 = Math.max(0.004F, (var17[var21] + var17[var22]) * 0.5F);
                     if (var20 == var19 - 1) {
                        var23 *= 0.78F;
                     }

                     segment(var12, var1, var2, A, B, var23, var13 * 3 + var20, var9, var10, var11);
                  }

                  var13++;
               }
            }

            float[] var24 = var7.burrs;

            for (int var25 = 0; var25 < 2; var25++) {
               float var26 = var24[var25 * 4];
               float var27 = var24[var25 * 4 + 1];
               float var28 = var24[var25 * 4 + 2];
               float var29 = var24[var25 * 4 + 3];
               if (!(var29 <= 0.0F)) {
                  A.set(var26, var27 - var29 * 0.35F, var28);
                  B.set(var26, var27 + var29 * 0.65F, var28);
                  segment(var12, var1, var2, A, B, var29 * 0.72F, 40 + var25, var9, var10, var11);
               }
            }

            var0.popPose();
         }
      }
   }

   private static void segment(Pose var0, VertexConsumer var1, int var2, Vector3f var3, Vector3f var4, float var5, int var6, float var7, float var8, float var9) {
      AZ.set(var4).sub(var3);
      float var10 = AZ.length();
      if (!(var10 < 1.0E-5F)) {
         AZ.div(var10);
         TMP.set(Math.abs(AZ.y) > 0.92F ? 0.0F : 0.0F, Math.abs(AZ.y) > 0.92F ? 0.0F : 1.0F, Math.abs(AZ.y) > 0.92F ? 1.0F : 0.0F);
         AX.set(TMP).cross(AZ);
         if (AX.lengthSquared() < 1.0E-8F) {
            TMP.set(1.0F, 0.0F, 0.0F);
            AX.set(TMP).cross(AZ);
         }

         AX.normalize();
         AY.set(AZ).cross(AX).normalize();
         TMP.set(var3).sub(AX.x * var5 + AY.x * var5, AX.y * var5 + AY.y * var5, AX.z * var5 + AY.z * var5);
         float var11 = (float)(var6 * 5 % 20) / 32.0F;
         float var12 = (float)(var6 * 7 % 18) / 32.0F;
         CubeAnimal.quadBox(
            var0,
            var1,
            var2,
            OverlayTexture.NO_OVERLAY,
            TMP,
            AX.mul(var5 * 2.0F, new Vector3f()),
            AY.mul(var5 * 2.0F, new Vector3f()),
            AZ.mul(var10, new Vector3f()),
            var11,
            var12,
            var11 + 0.375F,
            var12 + 0.4375F,
            var7,
            var8,
            var9
         );
      }
   }

   private static void grown(PoseStack var0, VertexConsumer var1, int var2, DeerTraits var3, Matrix4f var4, float[] var5) {
      DeerSkeleton var6 = DeerSkeleton.of(var3.species());
      boolean var7 = var3.species() == GameSpecies.MOOSE;
      float var8 = var7 ? 0.96F : 0.74F;
      float var9 = var7 ? 0.94F : 0.62F;
      float var10 = var7 ? 0.88F : 0.44F;
      float var11 = (var5 == null ? 1.0F : var5[0]) * var8;
      float var12 = (var5 == null ? 1.0F : var5[1]) * var9;
      float var13 = (var5 == null ? 1.0F : var5[2]) * var10;
      float var14 = Math.max(0.35F, var3.rackScale());
      float var15 = var3.spreadScale();
      float var16 = var3.rackHeightScale() * var14;
      float var17 = var3.rackDepthScale() * var14;
      boolean var18 = var3.species() == GameSpecies.MOOSE;
      var0.pushPose();
      var0.mulPose(var4);
      var0.mulPose(var6.antlerFrame);
      Pose var19 = var0.last();
      int var20 = 0;

      for (int var21 = 0; var21 < 2; var21++) {
         float var22 = var21 == 0 ? -1.0F : 1.0F;
         float var23 = var3.sideScale(var21 == 1);
         int var24 = Math.max(2, Math.min(9, var3.points(var21 == 1)));
         float var25 = (var18 ? 0.026F : 0.021F) * Math.max(0.4F, var3.rackMass()) * var23;
         float var26 = var22 * (var18 ? 0.083F : 0.077F);
         if (var18) {
            var20 = moosePalm(var19, var1, var2, var22, var26, var15 * var23, var16 * var23, var17 * var23, var25, var24, var20, var11, var12, var13);
         } else {
            var20 = elkBeam(var19, var1, var2, var22, var26, var15 * var23, var16 * var23, var17 * var23, var25, var24, var20, var11, var12, var13);
         }
      }

      var0.popPose();
   }

   private static int elkBeam(
      Pose var0,
      VertexConsumer var1,
      int var2,
      float var3,
      float var4,
      float var5,
      float var6,
      float var7,
      float var8,
      int var9,
      int var10,
      float var11,
      float var12,
      float var13
   ) {
      int var14 = ELK_BEAM.length / 3;
      float[] var15 = new float[var14];
      float[] var16 = new float[var14];
      float[] var17 = new float[var14];

      for (int var18 = 0; var18 < var14; var18++) {
         var15[var18] = var3 * (0.077F + (ELK_BEAM[var18 * 3] - 0.077F) * var5);
         var16[var18] = ELK_BEAM[var18 * 3 + 1] * var6;
         var17[var18] = ELK_BEAM[var18 * 3 + 2] * var7;
      }

      for (int var30 = 0; var30 < var14 - 1; var30++) {
         A.set(var15[var30], var16[var30], var17[var30]);
         B.set(var15[var30 + 1], var16[var30 + 1], var17[var30 + 1]);
         segment(var0, var1, var2, A, B, var8 * (1.25F - 0.5F * (float)var30 / ((float)var14 - 2.0F)), var10++, var11, var12, var13);
      }

      A.set(var15[0], var16[0] + 0.014F, var17[0]);
      B.set(var15[0] + var3 * 0.03F, var16[0] + 0.055F * var6, var17[0] - 0.23F * var7);
      segment(var0, var1, var2, A, B, var8 * 0.72F, var10++, var11, var12, var13);
      A.set(var15[1], var16[1], var17[1]);
      B.set(var15[1] + var3 * 0.032F, var16[1] + 0.07F * var6, var17[1] - 0.215F * var7);
      segment(var0, var1, var2, A, B, var8 * 0.68F, var10++, var11, var12, var13);
      int var31 = Math.max(0, var9 - 2);

      for (int var19 = 0; var19 < var31; var19++) {
         float var20 = 0.42F + 0.5F * (var31 == 1 ? 0.5F : (float)var19 / (float)(var31 - 1));
         float var21 = var20 * (float)(var14 - 1);
         int var22 = Math.min(var14 - 2, (int)var21);
         float var23 = var21 - (float)var22;
         float var24 = var15[var22] * (1.0F - var23) + var15[var22 + 1] * var23;
         float var25 = var16[var22] * (1.0F - var23) + var16[var22 + 1] * var23;
         float var26 = var17[var22] * (1.0F - var23) + var17[var22 + 1] * var23;
         float var27 = (0.155F - 0.022F * (float)var19) * var6;
         A.set(var24, var25, var26);
         B.set(var24 + var3 * 0.014F, var25 + var27, var26 + 0.028F * var7);
         segment(var0, var1, var2, A, B, var8 * Math.max(0.4F, 0.74F - 0.05F * (float)var19), var10++, var11, var12, var13);
      }

      return var10;
   }

   private static int moosePalm(
      Pose var0,
      VertexConsumer var1,
      int var2,
      float var3,
      float var4,
      float var5,
      float var6,
      float var7,
      float var8,
      int var9,
      int var10,
      float var11,
      float var12,
      float var13
   ) {
      float var15 = 0.01F;
      float var16 = 0.0F;
      float var17 = var3 * (0.083F + 0.115F * var5);
      float var18 = 0.085F * var6;
      float var19 = -0.01F * var7;
      A.set(var4, var15, var16);
      B.set(var17, var18, var19);
      segment(var0, var1, var2, A, B, var8 * 1.25F, var10++, var11, var12, var13);
      float var20 = var3 * (0.083F + 0.345F * var5);
      float var21 = 0.14F * var6;
      float var22 = -0.02F * var7;

      for (int var23 = 0; var23 < 3; var23++) {
         float var24 = (float)var23 / 3.0F;
         float var25 = (float)(var23 + 1) / 3.0F;
         A.set(var17 * (1.0F - var24) + var20 * var24, var18 * (1.0F - var24) + var21 * var24, var19 * (1.0F - var24) + var22 * var24 - 0.19F * var7);
         B.set(var17 * (1.0F - var25) + var20 * var25, var18 * (1.0F - var25) + var21 * var25, var19 * (1.0F - var25) + var22 * var25 + 0.165F * var7);
         segment(var0, var1, var2, A, B, var8 * (1.15F - 0.1F * (float)var23), var10++, var11, var12, var13);
      }

      for (int var29 = 0; var29 < var9; var29++) {
         float var30 = var9 == 1 ? 0.5F : (float)var29 / (float)(var9 - 1);
         float var31 = var17 * (1.0F - var30) + var20 * var30;
         float var26 = var18 * (1.0F - var30) + var21 * var30;
         float var27 = var19 * (1.0F - var30) + var22 * var30;
         A.set(var31, var26 + 0.03F * var6, var27 - 0.135F * var7);
         B.set(var31 + var3 * 0.014F, var26 + 0.105F * var6, var27 - 0.205F * var7);
         segment(var0, var1, var2, A, B, var8 * 0.55F, var10++, var11, var12, var13);
         if (var29 % 2 == 0) {
            A.set(var31, var26 + 0.028F * var6, var27 + 0.112F * var7);
            B.set(var31 + var3 * 0.012F, var26 + 0.088F * var6, var27 + 0.17F * var7);
            segment(var0, var1, var2, A, B, var8 * 0.5F, var10++, var11, var12, var13);
         }
      }

      return var10;
   }

   private CubeAntlers() {
   }
}
