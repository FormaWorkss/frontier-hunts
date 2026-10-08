package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.hunting.AntlerGeometry;
import com.formaworks.frontierhunts.hunting.DeerAnimator;
import com.formaworks.frontierhunts.hunting.DeerMeshData;
import com.formaworks.frontierhunts.hunting.DeerSkeleton;
import com.formaworks.frontierhunts.hunting.DeerTraits;
import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import java.util.EnumSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class DeerDraw {
   public static final ResourceLocation COAT_SUMMER = coat(GameSpecies.WHITETAIL, false);
   public static final ResourceLocation COAT_WINTER = coat(GameSpecies.WHITETAIL, true);
   public static final ResourceLocation ANTLER = FrontierHunts.id("textures/entity/whitetail_antler.png");
   public static final ResourceLocation MATERIAL = FrontierHunts.id("textures/entity/material.png");
   private static final EnumSet<GameSpecies> REGISTERED = EnumSet.noneOf(GameSpecies.class);
   private static boolean antlerRegistered;
   private static float[] skinnedPos;
   private static float[] skinnedNrm;
   private static final Vector3f P = new Vector3f();
   private static final Vector3f N = new Vector3f();
   private static final Matrix4f A = new Matrix4f();
   private static final Matrix3f AN = new Matrix3f();
   private static final float[][] TRI = new float[3][13];
   private static final float[][] FIRST = new float[6][13];
   private static final float[][] SECOND = new float[6][13];

   static void ensureTextures() {
      ensureTextures(GameSpecies.WHITETAIL);
   }

   static void ensureTextures(GameSpecies var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (var1 != null) {
         TextureManager var2 = var1.getTextureManager();
         if (!antlerRegistered) {
            antlerRegistered = true;
            var2.register(ANTLER, new MipmappedHuntTexture(ANTLER));
         }

         if (REGISTERED.add(var0)) {
            for (boolean var6 : new boolean[]{false, true}) {
               var2.register(coat(var0, var6), new MipmappedHuntTexture(coat(var0, var6)));
            }
         }
      }
   }

   static void reset() {
      skinnedPos = null;
      skinnedNrm = null;
      REGISTERED.clear();
      antlerRegistered = false;
   }

   static ResourceLocation coat(GameSpecies var0, boolean var1) {
      return FrontierHunts.id(var0.coatTexture(var1));
   }

   public static ResourceLocation coat(DeerTraits var0) {
      return coat(var0.species(), var0.greyCoat());
   }

   static float[][] skin(DeerAnimator var0, DeerTraits var1, int var2) {
      DeerMeshData var3 = DeerMeshData.of(var1.species());
      if (skinnedPos == null || skinnedPos.length < var3.vertices * 3) {
         skinnedPos = new float[var3.vertices * 3];
         skinnedNrm = new float[var3.vertices * 3];
      }

      var3.skin(var2, var0.skin, var1.neckGirth(), var1.headScale(), meshRackScale(var1), skinnedPos, skinnedNrm);
      return new float[][]{skinnedPos, skinnedNrm};
   }

   static float meshRackScale(DeerTraits var0) {
      return var0.species().meshAntlers() && var0.buck() ? Math.max(0.55F, Math.min(1.12F, var0.rackScale())) : 1.0F;
   }

   static float[][] skinCompact(DeerAnimator var0, DeerTraits var1, int var2, float[][] var3) {
      DeerMeshData var4 = DeerMeshData.of(var1.species());
      int var5 = var4.lodVertices[var2].length * 3;
      if (var3 == null || var3[0].length < var5) {
         var3 = new float[][]{new float[var5], new float[var5]};
      }

      var4.skinCompact(var2, var0.skin, var1.neckGirth(), var1.headScale(), meshRackScale(var1), var3[0], var3[1]);
      return var3;
   }

   static void body(PoseStack var0, VertexConsumer var1, int var2, DeerTraits var3, int var4, float[][] var5, float[] var6) {
      body(var0, var1, var2, var3, var4, var5, var6, false);
   }

   static void bodyFast(PoseStack var0, VertexConsumer var1, int var2, DeerTraits var3, int var4, float[][] var5, float[] var6) {
      DeerMeshData var7 = DeerMeshData.of(var3.species());
      Pose var8 = var0.last();
      int[] var9 = var7.lodVertices[var4];
      int var10 = var9.length;
      FastEmit.ensure(var10);
      FastEmit.transform(var8.pose(), var8.normal(), var5[0], var5[1], var10);
      boolean var11 = var7.lodMode[var4] == 1;
      float var12 = var3.coatWarmth();
      float var13 = var3.coatShade();
      boolean var14 = var3.greyCoat();
      if (var6 != null) {
         FastEmit.fillColour(FastEmit.argb(var6[0], var6[1], var6[2]), var10);
         FastEmit.fillUv(0.5F, 0.5F, var10);
      } else if (var11) {
         FastEmit.fillUv(0.5F, 0.5F, var10);

         for (int var15 = 0; var15 < var10; var15++) {
            int var16 = var9[var15];
            float var17 = var7.col[var16 * 3];
            float var18 = var7.col[var16 * 3 + 1];
            float var19 = var7.col[var16 * 3 + 2];
            if (var14) {
               float var20 = var17 * 0.3F + var18 * 0.59F + var19 * 0.11F;
               var17 += (var20 - var17) * 0.5F;
               var18 += (var20 - var18) * 0.5F;
               var19 += (var20 - var19) * 0.45F;
               var17 *= 0.93F;
               var18 *= 0.94F;
               var19 *= 0.95F;
            }

            FastEmit.colour(var15, FastEmit.argb(var17 * var13 * (1.0F + var12 * 0.06F), var18 * var13, var19 * var13 * (1.0F - var12 * 0.08F)));
         }
      } else {
         FastEmit.fillColour(FastEmit.argb(var13 * (1.0F + var12 * 0.06F), var13, var13 * (1.0F - var12 * 0.08F)), var10);

         for (int var21 = 0; var21 < var10; var21++) {
            int var22 = var9[var21];
            FastEmit.uv(var21, var7.uv[var22 * 2], var7.uv[var22 * 2 + 1]);
         }
      }

      FastEmit.tris(var1, var7.localFaces(var4, var3.buck()), var2);
   }

   static void body(PoseStack var0, VertexConsumer var1, int var2, DeerTraits var3, int var4, float[][] var5, float[] var6, boolean var7) {
      DeerMeshData var8 = DeerMeshData.of(var3.species());
      Pose var9 = var0.last();
      Matrix4f var10 = var9.pose();
      float var11 = var3.coatWarmth();
      float var12 = var3.coatShade();
      boolean var13 = var8.lodMode[var4] == 1;
      boolean var14 = var3.greyCoat();
      int[] var15 = var7 ? var8.localFaces(var4, var3.buck()) : var8.faces(var4, var3.buck());
      int[] var16 = var7 ? var8.lodVertices[var4] : null;
      float[] var17 = var5[0];
      float[] var18 = var5[1];

      for (int var19 = 0; var19 < var15.length; var19++) {
         int var20 = var15[var19];
         int var21 = var7 ? var16[var20] : var20;
         int var22 = var20 * 3;
         float var23;
         float var24;
         float var25;
         if (var6 != null) {
            var23 = var6[0];
            var24 = var6[1];
            var25 = var6[2];
         } else if (var13) {
            var23 = var8.col[var21 * 3];
            var24 = var8.col[var21 * 3 + 1];
            var25 = var8.col[var21 * 3 + 2];
            if (var14) {
               float var26 = var23 * 0.3F + var24 * 0.59F + var25 * 0.11F;
               var23 += (var26 - var23) * 0.5F;
               var24 += (var26 - var24) * 0.5F;
               var25 += (var26 - var25) * 0.45F;
               var23 *= 0.93F;
               var24 *= 0.94F;
               var25 *= 0.95F;
            }

            var23 *= var12 * (1.0F + var11 * 0.06F);
            var24 *= var12;
            var25 *= var12 * (1.0F - var11 * 0.08F);
         } else {
            var23 = var12 * (1.0F + var11 * 0.06F);
            var24 = var12;
            var25 = var12 * (1.0F - var11 * 0.08F);
         }

         var1.addVertex(var10, var17[var22], var17[var22 + 1], var17[var22 + 2])
            .setColor(Math.min(1.0F, var23), Math.min(1.0F, var24), Math.min(1.0F, var25), 1.0F)
            .setUv(!var13 && var6 == null ? var8.uv[var21 * 2] : 0.5F, !var13 && var6 == null ? var8.uv[var21 * 2 + 1] : 0.5F)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(var2)
            .setNormal(var9, var18[var22], var18[var22 + 1], var18[var22 + 2]);
      }
   }

   static void antlers(PoseStack var0, VertexConsumer var1, int var2, DeerTraits var3, Matrix4f var4, int var5, float[] var6) {
      if (var3.buck() && var3.species().antlers == GameSpecies.Antlers.PROCEDURAL_WHITETAIL) {
         AntlerGeometry var7 = AntlerGeometry.of(var3, var5);
         DeerSkeleton var8 = DeerSkeleton.of(var3.species());
         Pose var9 = var0.last();
         A.set(var9.pose()).mul(var4).mul(var8.antlerFrame);
         AN.set(var9.normal()).mul(new Matrix3f(var4).mul(new Matrix3f(var8.antlerFrame)));
         float var10 = var6 == null ? 1.0F : var6[0];
         float var11 = var6 == null ? 1.0F : var6[1];
         float var12 = var6 == null ? 1.0F : var6[2];
         float[] var13 = var7.pos;
         float[] var14 = var7.nrm;
         float[] var15 = var7.uv;

         for (int var19 : var7.tris) {
            A.transformPosition(var13[var19 * 3], var13[var19 * 3 + 1], var13[var19 * 3 + 2], P);
            AN.transform(N.set(var14[var19 * 3], var14[var19 * 3 + 1], var14[var19 * 3 + 2])).normalize();
            var1.addVertex(P.x, P.y, P.z)
               .setColor(var10, var11, var12, 1.0F)
               .setUv(var6 == null ? var15[var19 * 2] : 0.5F, var6 == null ? var15[var19 * 2 + 1] : 0.5F)
               .setOverlay(OverlayTexture.NO_OVERLAY)
               .setLight(var2)
               .setNormal(N.x, N.y, N.z);
         }
      }
   }

   static void rack(PoseStack var0, VertexConsumer var1, int var2, DeerTraits var3, int var4) {
      if (var3.buck() && var3.species().antlers == GameSpecies.Antlers.PROCEDURAL_WHITETAIL) {
         AntlerGeometry var5 = AntlerGeometry.of(var3, var4);
         Pose var6 = var0.last();
         float[] var7 = var5.pos;
         float[] var8 = var5.nrm;
         float[] var9 = var5.uv;

         for (int var13 : var5.tris) {
            var1.addVertex(var6.pose(), var7[var13 * 3], var7[var13 * 3 + 1], var7[var13 * 3 + 2])
               .setColor(1.0F, 1.0F, 1.0F, 1.0F)
               .setUv(var9[var13 * 2], var9[var13 * 2 + 1])
               .setOverlay(OverlayTexture.NO_OVERLAY)
               .setLight(var2)
               .setNormal(var6, var8[var13 * 3], var8[var13 * 3 + 1], var8[var13 * 3 + 2]);
         }
      }
   }

   static void carcass(PoseStack var0, VertexConsumer var1, VertexConsumer var2, int var3, DeerTraits var4, float[][] var5, float var6) {
      DeerMeshData var7 = DeerMeshData.of(var4.species());
      int var8 = carcassLod(var7);
      int[] var9 = var7.faces(var8, var4.buck());
      float var10 = var4.coatWarmth();
      float var11 = var4.coatShade();
      float var12 = Math.min(1.0F, var11 * (1.0F + var10 * 0.06F));
      float var13 = Math.min(1.0F, var11);
      float var14 = Math.min(1.0F, var11 * (1.0F - var10 * 0.08F));
      int var15 = FastEmit.argb(var12, var13, var14);
      int var16 = FastEmit.argb(0.92F, 0.86F, 0.83F);
      Pose var17 = var0.last();
      float var18 = var6 - 0.038F;

      for (byte var19 = 0; var19 + 2 < var9.length; var19 += 3) {
         int var20 = var9[var19];
         int var21 = var9[var19 + 1];
         int var22 = var9[var19 + 2];
         float var23 = var7.cut[var20];
         float var24 = var7.cut[var21];
         float var25 = var7.cut[var22];
         float var26 = Math.min(var23, Math.min(var24, var25));
         float var27 = Math.max(var23, Math.max(var24, var25));
         if (var26 >= var6) {
            straight(var17, var1, var3, var7, var5, var20, var21, var22, var15, false, 0.0F);
         } else if (var27 <= var18) {
            straight(var17, var2, var3, var7, var5, var20, var21, var22, var16, true, -0.004F);
         } else {
            for (int var28 = 0; var28 < 3; var28++) {
               int var29 = var28 == 0 ? var20 : (var28 == 1 ? var21 : var22);
               float[] var30 = TRI[var28];
               var30[0] = var5[0][var29 * 3];
               var30[1] = var5[0][var29 * 3 + 1];
               var30[2] = var5[0][var29 * 3 + 2];
               var30[3] = var5[1][var29 * 3];
               var30[4] = var5[1][var29 * 3 + 1];
               var30[5] = var5[1][var29 * 3 + 2];
               var30[6] = var7.uv[var29 * 2];
               var30[7] = var7.uv[var29 * 2 + 1];
               var30[8] = var12;
               var30[9] = var13;
               var30[10] = var14;
               var30[11] = var7.cut[var29];
               var30[12] = var7.pos[var29 * 3];
            }

            int var31 = clip(TRI, 3, FIRST, var6, false);
            emit(FIRST, var31, var0, var1, var3, false, 0.0F, var6);
            var31 = clip(TRI, 3, FIRST, var6, true);
            emit(FIRST, var31, var0, var2, var3, true, -0.004F, var6);
            if (var6 > 0.035F && var6 < 0.985F) {
               var31 = clip(TRI, 3, FIRST, var6, true);
               var31 = clip(FIRST, var31, SECOND, var18, false);
               emit(SECOND, var31, var0, var1, var3, false, 0.055F, var6);
            }
         }
      }
   }

   static int carcassLod(DeerMeshData var0) {
      return var0.lods[0].length > 60000 ? 1 : 0;
   }

   private static void straight(
      Pose var0, VertexConsumer var1, int var2, DeerMeshData var3, float[][] var4, int var5, int var6, int var7, int var8, boolean var9, float var10
   ) {
      Matrix4f var11 = var0.pose();

      for (int var12 = 0; var12 < 3; var12++) {
         int var13 = var12 == 0 ? var5 : (var12 == 1 ? var6 : var7);
         int var14 = var13 * 3;
         float var15 = var4[1][var14];
         float var16 = var4[1][var14 + 1];
         float var17 = var4[1][var14 + 2];
         float var18 = var4[0][var14] + var15 * var10;
         float var19 = var4[0][var14 + 1] + var16 * var10;
         float var20 = var4[0][var14 + 2] + var17 * var10;
         float var21 = var11.m00() * var18 + var11.m10() * var19 + var11.m20() * var20 + var11.m30();
         float var22 = var11.m01() * var18 + var11.m11() * var19 + var11.m21() * var20 + var11.m31();
         float var23 = var11.m02() * var18 + var11.m12() * var19 + var11.m22() * var20 + var11.m32();
         Matrix3f var24 = var0.normal();
         float var25 = var24.m00() * var15 + var24.m10() * var16 + var24.m20() * var17;
         float var26 = var24.m01() * var15 + var24.m11() * var16 + var24.m21() * var17;
         float var27 = var24.m02() * var15 + var24.m12() * var16 + var24.m22() * var17;
         float var28 = (float)Math.sqrt((double)(var25 * var25 + var26 * var26 + var27 * var27));
         if (var28 > 1.0E-6F) {
            float var29 = 1.0F / var28;
            var25 *= var29;
            var26 *= var29;
            var27 *= var29;
         } else {
            var25 = 0.0F;
            var26 = 1.0F;
            var27 = 0.0F;
         }

         var1.addVertex(
            var21,
            var22,
            var23,
            var8,
            var9 ? var3.uv[var13 * 2] * 0.39F : var3.uv[var13 * 2],
            var9 ? var3.uv[var13 * 2 + 1] * 0.39F : var3.uv[var13 * 2 + 1],
            OverlayTexture.NO_OVERLAY,
            var2,
            var25,
            var26,
            var27
         );
      }
   }

   private static int clip(float[][] var0, int var1, float[][] var2, float var3, boolean var4) {
      int var5 = 0;
      if (var1 == 0) {
         return 0;
      } else {
         for (int var6 = 0; var6 < var1; var6++) {
            float[] var7 = var0[var6];
            float[] var8 = var0[(var6 + 1) % var1];
            boolean var9 = var4 ? var7[11] <= var3 : var7[11] >= var3;
            boolean var10 = var4 ? var8[11] <= var3 : var8[11] >= var3;
            if (var9) {
               System.arraycopy(var7, 0, var2[var5++], 0, 13);
            }

            if (var9 != var10) {
               float var11 = (var3 - var7[11]) / (var8[11] - var7[11]);
               float[] var12 = var2[var5++];

               for (int var13 = 0; var13 < 13; var13++) {
                  var12[var13] = var7[var13] + (var8[var13] - var7[var13]) * var11;
               }
            }
         }

         return var5;
      }
   }

   private static void emit(float[][] var0, int var1, PoseStack var2, VertexConsumer var3, int var4, boolean var5, float var6, float var7) {
      Pose var8 = var2.last();

      for (int var9 = 1; var9 < var1 - 1; var9++) {
         for (int var10 = 0; var10 < 3; var10++) {
            float[] var11 = var0[var10 == 0 ? 0 : (var10 == 1 ? var9 : var9 + 1)];
            float var12 = var6;
            if (var6 > 0.0F) {
               var12 = 0.006F + (float)Math.sin((double)Math.clamp((var7 - var11[11]) / 0.038F, 0.0F, 1.0F) * Math.PI * 0.88) * var6;
            }

            float var13 = var5 ? 0.92F : Math.min(1.0F, var11[8]);
            float var14 = var5 ? 0.86F : Math.min(1.0F, var11[9]);
            float var15 = var5 ? 0.83F : Math.min(1.0F, var11[10]);
            var3.addVertex(var8.pose(), var11[0] + var11[3] * var12, var11[1] + var11[4] * var12, var11[2] + var11[5] * var12)
               .setColor(var13, var14, var15, 1.0F)
               .setUv(var5 ? var11[6] * 0.39F : var11[6], var5 ? var11[7] * 0.39F : var11[7])
               .setOverlay(OverlayTexture.NO_OVERLAY)
               .setLight(var4)
               .setNormal(var8, var11[3], var11[4], var11[5]);
         }
      }
   }

   private DeerDraw() {
   }
}
