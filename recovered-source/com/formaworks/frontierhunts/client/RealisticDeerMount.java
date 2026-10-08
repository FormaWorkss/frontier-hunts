package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.hunting.AntlerGeometry;
import com.formaworks.frontierhunts.hunting.DeerAnimator;
import com.formaworks.frontierhunts.hunting.DeerMeshData;
import com.formaworks.frontierhunts.hunting.DeerSkeleton;
import com.formaworks.frontierhunts.hunting.DeerTraits;
import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

final class RealisticDeerMount {
   static final float BACK = -0.1F;
   static final float Y0 = 0.6F;
   static final float Z0 = -0.45F;
   static final float SLOPE = 0.514F;
   private static final Map<List<Object>, RealisticDeerMount.Mount> CACHE = new LinkedHashMap<List<Object>, RealisticDeerMount.Mount>(16, 0.75F, true) {
      @Override
      protected boolean removeEldestEntry(Entry<List<Object>, RealisticDeerMount.Mount> var1) {
         return this.size() > 24;
      }
   };

   static void clear() {
      synchronized (CACHE) {
         CACHE.clear();
      }
   }

   static void draw(PoseStack var0, MultiBufferSource var1, int var2, DeerTraits var3, boolean var4) {
      DeerDraw.ensureTextures(var3.species());
      int var5 = var4 ? 1 : 0;
      RealisticDeerMount.Mount var6;
      synchronized (CACHE) {
         var6 = CACHE.computeIfAbsent(List.of(var3, var5), var2x -> build(var3, var5));
      }

      var0.pushPose();
      var0.scale(var6.scale, var6.scale, var6.scale);
      var0.translate(-var6.cx, -var6.cy + 0.045F / var6.scale, -var6.back - 0.055F / var6.scale);
      Pose var22 = var0.last();
      VertexConsumer var8 = var1.getBuffer(HuntRenderTypes.sculpt(RealisticCoats.coat(var3)));
      float var9 = var3.coatWarmth();
      float var10 = var3.coatShade();
      float var11 = Math.min(1.0F, var10 * (1.0F + var9 * 0.06F));
      float var12 = Math.min(1.0F, var10);
      float var13 = Math.min(1.0F, var10 * (1.0F - var9 * 0.08F));
      float[] var14 = var6.pos;
      float[] var15 = var6.nrm;
      float[] var16 = var6.uv;

      for (int var20 : var6.tris) {
         var8.addVertex(var22.pose(), var14[var20 * 3], var14[var20 * 3 + 1], var14[var20 * 3 + 2])
            .setColor(var11, var12, var13, 1.0F)
            .setUv(var16[var20 * 2], var16[var20 * 2 + 1])
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(var2)
            .setNormal(var22, var15[var20 * 3], var15[var20 * 3 + 1], var15[var20 * 3 + 2]);
      }

      VertexConsumer var23 = var1.getBuffer(HuntRenderTypes.sculpt(DeerDraw.MATERIAL));
      float[] var24 = var6.cap;

      for (byte var25 = 0; var25 + 5 < var24.length; var25 += 6) {
         var23.addVertex(var22.pose(), var24[var25], var24[var25 + 1], var24[var25 + 2])
            .setColor(0.3F, 0.23F, 0.17F, 1.0F)
            .setUv(0.5F, 0.5F)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(var2)
            .setNormal(var22, var24[var25 + 3], var24[var25 + 4], var24[var25 + 5]);
      }

      if (var3.buck()) {
         var0.pushPose();
         var0.last().pose().mul(var6.antler);
         var0.last().normal().mul(new Matrix3f(var6.antler));
         DeerDraw.rack(var0, var1.getBuffer(HuntRenderTypes.sculpt(RealisticCoats.antler())), var2, var3, var4 ? 6 : 8);
         var0.popPose();
      }

      var0.popPose();
   }

   private static Vector3f joint(DeerSkeleton var0, int var1) {
      return new Matrix4f(var0.inverseBind[var1]).invert().getTranslation(new Vector3f());
   }

   private static RealisticDeerMount.Mount build(DeerTraits var0, int var1) {
      DeerSkeleton var2 = DeerSkeleton.of(var0.species());
      DeerMeshData var3 = DeerMeshData.of(var0.species());
      DeerAnimator var4 = new DeerAnimator(var0.species());
      DeerAnimator.Input var5 = new DeerAnimator.Input();
      var5.buck = var0.buck();
      var5.headYaw = 0.24F;
      var5.headPitch = 0.1F;
      var5.alert = 0.35F;
      var4.update(var5);
      float[] var6 = new float[var3.vertices * 3];
      float[] var7 = new float[var3.vertices * 3];
      var3.skin(var1, var4.skin, var0.neckGirth(), var0.headScale(), DeerDraw.meshRackScale(var0), var6, var7);
      RealisticDeerMount.Cut var8 = RealisticDeerMount.Cut.of(var2);
      RealisticDeerMount.FloatList var9 = new RealisticDeerMount.FloatList();
      RealisticDeerMount.FloatList var10 = new RealisticDeerMount.FloatList();
      RealisticDeerMount.FloatList var11 = new RealisticDeerMount.FloatList();
      RealisticDeerMount.FloatList var12 = new RealisticDeerMount.FloatList();
      RealisticDeerMount.FloatList var13 = new RealisticDeerMount.FloatList();
      int[] var14 = var3.faces(var1, var0.buck());
      float[][] var15 = new float[3][];
      float[][] var16 = new float[8][];
      float[][] var17 = new float[8][];
      RealisticDeerMount.Plane var18 = var8::keepBottom;
      RealisticDeerMount.Plane var19 = var8::keepBack;
      RealisticDeerMount.Plane var20 = var0x -> 1.0F;

      for (byte var21 = 0; var21 + 2 < var14.length; var21 += 3) {
         for (int var22 = 0; var22 < 3; var22++) {
            int var23 = var14[var21 + var22];
            var15[var22] = new float[]{
               var6[var23 * 3],
               var6[var23 * 3 + 1],
               var6[var23 * 3 + 2],
               var7[var23 * 3],
               var7[var23 * 3 + 1],
               var7[var23 * 3 + 2],
               var3.uv[var23 * 2],
               var3.uv[var23 * 2 + 1]
            };
         }

         boolean var35 = var3.hasMeshAntlers && ((var3.region[var14[var21]] | var3.region[var14[var21 + 1]] | var3.region[var14[var21 + 2]]) & 16) != 0;
         int var37 = clip(var15, 3, var16, var35 ? var20 : var18, var35 ? new RealisticDeerMount.FloatList() : var13);
         if (var37 >= 3) {
            var37 = clip(var16, var37, var17, var35 ? var20 : var19, var35 ? new RealisticDeerMount.FloatList() : var12);

            for (int var24 = 1; var24 + 1 < var37; var24++) {
               for (float[] var28 : new float[][]{var17[0], var17[var24], var17[var24 + 1]}) {
                  var9.add(var28[0], var28[1], var28[2]);
                  var10.add(var28[3], var28[4], var28[5]);
                  var11.add(var28[6], var28[7]);
               }
            }
         }
      }

      RealisticDeerMount.Mount var34 = new RealisticDeerMount.Mount();
      int var36 = var9.size / 3;
      var34.pos = var9.array();
      var34.nrm = var10.array();
      var34.uv = var11.array();
      var34.tris = new int[var36];
      int var39 = 0;

      while (var39 < var36) {
         var34.tris[var39] = var39++;
      }

      RealisticDeerMount.FloatList var40 = new RealisticDeerMount.FloatList();
      float[] var41 = var13.array();

      for (byte var42 = 0; var42 + 5 < var41.length; var42 += 6) {
         float var44 = var8.back - var41[var42 + 2];
         float var46 = var8.back - var41[var42 + 5];
         if (!(var44 < 0.0F) || !(var46 < 0.0F)) {
            float[] var29 = new float[]{var41[var42], var41[var42 + 1], var41[var42 + 2]};
            float[] var30 = new float[]{var41[var42 + 3], var41[var42 + 4], var41[var42 + 5]};
            if (var44 < 0.0F) {
               lerpInto(var29, var30, var44 / (var44 - var46));
            } else if (var46 < 0.0F) {
               lerpInto(var30, var29, var46 / (var46 - var44));
            }

            var40.add(var29[0], var29[1], var29[2]);
            var40.add(var30[0], var30[1], var30[2]);
         }
      }

      RealisticDeerMount.FloatList var43 = new RealisticDeerMount.FloatList();
      fan(var12.array(), 0.0F, 0.0F, 1.0F, var43);
      float var45 = 1.0F / (float)Math.sqrt((double)(1.0F + var8.slope * var8.slope));
      fan(var40.array(), 0.0F, -var45, var8.slope * var45, var43);
      var34.cap = var43.array();
      float[] var47 = new float[]{Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
      float[] var48 = new float[]{-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};

      for (int var49 = 0; var49 < var36; var49++) {
         for (int var31 = 0; var31 < 3; var31++) {
            var47[var31] = Math.min(var47[var31], var34.pos[var49 * 3 + var31]);
            var48[var31] = Math.max(var48[var31], var34.pos[var49 * 3 + var31]);
         }
      }

      if (var36 == 0) {
         var47 = new float[]{-0.2F, 0.6F, -0.8F};
         var48 = new float[]{0.2F, 1.2F, var8.back};
      }

      float var50 = Math.max(var48[0] - var47[0], var48[1] - var47[1]);
      var34.antler.set(var4.model[var2.head]).mul(var2.antlerFrame);
      if (var0.buck() && !var0.species().meshAntlers()) {
         AntlerGeometry var51 = AntlerGeometry.of(var0, 4);
         Vector3f var32 = new Vector3f();

         for (int var33 = 0; var33 < var51.vertexCount(); var33++) {
            var34.antler.transformPosition(var51.pos[var33 * 3], var51.pos[var33 * 3 + 1], var51.pos[var33 * 3 + 2], var32);
            var47[0] = Math.min(var47[0], var32.x);
            var48[0] = Math.max(var48[0], var32.x);
            var47[1] = Math.min(var47[1], var32.y);
            var48[1] = Math.max(var48[1], var32.y);
         }
      }

      float var52 = Math.max(var48[0] - var47[0], var48[1] - var47[1]);
      var34.scale = Math.max(0.96F / Math.max(0.05F, var52), 0.55679995F / Math.max(0.05F, var50));
      var34.cx = (var47[0] + var48[0]) * 0.5F;
      var34.cy = (var47[1] + var48[1]) * 0.5F;
      var34.back = var8.back;
      return var34;
   }

   private static void lerpInto(float[] var0, float[] var1, float var2) {
      for (int var3 = 0; var3 < 3; var3++) {
         var0[var3] += (var1[var3] - var0[var3]) * var2;
      }
   }

   private static int clip(float[][] var0, int var1, float[][] var2, RealisticDeerMount.Plane var3, RealisticDeerMount.FloatList var4) {
      int var5 = 0;
      float[] var6 = null;

      for (int var7 = 0; var7 < var1; var7++) {
         float[] var8 = var0[var7];
         float[] var9 = var0[(var7 + 1) % var1];
         float var10 = var3.eval(var8);
         float var11 = var3.eval(var9);
         if (var10 >= 0.0F) {
            var2[var5++] = var8;
         }

         if (var10 >= 0.0F != var11 >= 0.0F) {
            float var12 = var10 / (var10 - var11);
            float[] var13 = new float[8];

            for (int var14 = 0; var14 < 8; var14++) {
               var13[var14] = var8[var14] + (var9[var14] - var8[var14]) * var12;
            }

            var2[var5++] = var13;
            if (var6 == null) {
               var6 = var13;
            } else {
               var4.add(var6[0], var6[1], var6[2], var13[0], var13[1], var13[2]);
            }
         }
      }

      return var5;
   }

   private static void fan(float[] var0, float var1, float var2, float var3, RealisticDeerMount.FloatList var4) {
      int var5 = var0.length / 6;
      if (var5 >= 3) {
         float var6 = 0.0F;
         float var7 = 0.0F;
         float var8 = 0.0F;

         for (byte var9 = 0; var9 < var0.length; var9 += 3) {
            var6 += var0[var9];
            var7 += var0[var9 + 1];
            var8 += var0[var9 + 2];
         }

         var6 /= (float)(var5 * 2);
         var7 /= (float)(var5 * 2);
         var8 /= (float)(var5 * 2);

         for (byte var23 = 0; var23 < var0.length; var23 += 6) {
            float var10 = var0[var23] - var6;
            float var11 = var0[var23 + 1] - var7;
            float var12 = var0[var23 + 2] - var8;
            float var13 = var0[var23 + 3] - var6;
            float var14 = var0[var23 + 4] - var7;
            float var15 = var0[var23 + 5] - var8;
            float var16 = var11 * var15 - var12 * var14;
            float var17 = var12 * var13 - var10 * var15;
            float var18 = var10 * var14 - var11 * var13;
            boolean var19 = var16 * var1 + var17 * var2 + var18 * var3 < 0.0F;
            var4.add(var6, var7, var8, var1, var2, var3);
            if (!var19) {
               var4.add(var0[var23], var0[var23 + 1], var0[var23 + 2], var1, var2, var3);
               var4.add(var0[var23 + 3], var0[var23 + 4], var0[var23 + 5], var1, var2, var3);
            } else {
               var4.add(var0[var23 + 3], var0[var23 + 4], var0[var23 + 5], var1, var2, var3);
               var4.add(var0[var23], var0[var23 + 1], var0[var23 + 2], var1, var2, var3);
            }
         }
      }
   }

   private RealisticDeerMount() {
   }

   private static record Cut(float back, float y0, float z0, float slope) {
      static RealisticDeerMount.Cut of(DeerSkeleton var0) {
         if (var0.species == GameSpecies.WHITETAIL) {
            return new RealisticDeerMount.Cut(-0.1F, 0.6F, -0.45F, 0.514F);
         } else {
            Vector3f var1 = RealisticDeerMount.joint(var0, var0.legs[0][0]);
            Vector3f var2 = RealisticDeerMount.joint(var0, var0.legs[0][1]);
            Vector3f var3 = RealisticDeerMount.joint(var0, var0.legs[0][2]);
            return new RealisticDeerMount.Cut(var1.z + 0.03F, var3.y + 0.06F, var2.z - 0.16F, 0.514F);
         }
      }

      float keepBack(float[] var1) {
         return this.back - var1[2];
      }

      float keepBottom(float[] var1) {
         return var1[1] - (this.y0 + (var1[2] - this.z0) * this.slope);
      }
   }

   private static final class FloatList {
      float[] data = new float[1024];
      int size;

      void add(float... var1) {
         if (this.size + var1.length > this.data.length) {
            this.data = Arrays.copyOf(this.data, Math.max(this.data.length * 2, this.size + var1.length));
         }

         System.arraycopy(var1, 0, this.data, this.size, var1.length);
         this.size += var1.length;
      }

      float[] array() {
         return Arrays.copyOf(this.data, this.size);
      }
   }

   private static final class Mount {
      float[] pos;
      float[] nrm;
      float[] uv;
      int[] tris;
      float[] cap;
      float scale;
      float cx;
      float cy;
      float back;
      final Matrix4f antler = new Matrix4f();
   }

   private interface Plane {
      float eval(float[] var1);
   }
}
