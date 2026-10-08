package com.formaworks.frontierhunts.hunting;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

public final class AntlerGeometry {
   public final float[] pos;
   public final float[] nrm;
   public final float[] uv;
   public final int[] tris;
   private static final Map<Long, AntlerGeometry> CACHE = new LinkedHashMap<Long, AntlerGeometry>(16, 0.75F, true) {
      @Override
      protected boolean removeEldestEntry(Entry<Long, AntlerGeometry> var1) {
         return this.size() > 96;
      }
   };
   private float[] p = new float[4096];
   private float[] n = new float[4096];
   private float[] t = new float[2048];
   private int[] f = new int[8192];
   private int vc;
   private int fc;

   public static AntlerGeometry of(DeerTraits var0, int var1) {
      long var2 = (long)var0.hashCode() << 8 ^ (long)var1;
      synchronized (CACHE) {
         AntlerGeometry var5 = CACHE.get(var2);
         if (var5 == null) {
            var5 = new AntlerGeometry(AntlerDesign.of(var0), var1);
            CACHE.put(var2, var5);
         }

         return var5;
      }
   }

   public int vertexCount() {
      return this.pos.length / 3;
   }

   private AntlerGeometry(AntlerDesign var1, int var2) {
      for (AntlerDesign.Branch var4 : var1.branches) {
         this.tube(var4, var2);
      }

      for (int var5 = 0; var5 < 2; var5++) {
         this.burr(var1.burrs[var5 * 4], var1.burrs[var5 * 4 + 1], var1.burrs[var5 * 4 + 2], var1.burrs[var5 * 4 + 3], var2, var5);
         this.plug(var1.burrs[var5 * 4], var1.burrs[var5 * 4 + 1], var1.burrs[var5 * 4 + 2], var1.burrs[var5 * 4 + 3], var2);
      }

      this.pos = Arrays.copyOf(this.p, this.vc * 3);
      this.nrm = Arrays.copyOf(this.n, this.vc * 3);
      this.uv = Arrays.copyOf(this.t, this.vc * 2);
      this.tris = Arrays.copyOf(this.f, this.fc);
      this.p = null;
      this.n = null;
      this.t = null;
      this.f = null;
   }

   private int vertex(float var1, float var2, float var3, float var4, float var5, float var6, float var7, float var8) {
      if ((this.vc + 1) * 3 > this.p.length || (this.vc + 1) * 2 > this.t.length) {
         this.p = Arrays.copyOf(this.p, this.p.length * 2);
         this.n = Arrays.copyOf(this.n, this.n.length * 2);
         this.t = Arrays.copyOf(this.t, this.t.length * 2);
      }

      this.p[this.vc * 3] = var1;
      this.p[this.vc * 3 + 1] = var2;
      this.p[this.vc * 3 + 2] = var3;
      this.n[this.vc * 3] = var4;
      this.n[this.vc * 3 + 1] = var5;
      this.n[this.vc * 3 + 2] = var6;
      this.t[this.vc * 2] = var7;
      this.t[this.vc * 2 + 1] = var8;
      return this.vc++;
   }

   private void tri(int var1, int var2, int var3) {
      if (this.fc + 3 > this.f.length) {
         this.f = Arrays.copyOf(this.f, this.f.length * 2);
      }

      this.f[this.fc++] = var1;
      this.f[this.fc++] = var3;
      this.f[this.fc++] = var2;
   }

   private void tube(AntlerDesign.Branch var1, int var2) {
      int var3 = var1.count();
      float[] var4 = var1.points();
      float[] var5 = new float[3];
      float[] var6 = new float[3];
      float[] var7 = new float[3];
      tangent(var4, 0, var3, var5);
      perpendicular(var5, var6);
      cross(var5, var6, var7);
      int[] var8 = null;
      int[] var9 = null;
      float var10 = 0.0F;
      float var11 = 0.0F;

      for (int var12 = 1; var12 < var3; var12++) {
         var11 += dist(var4, var12 - 1, var12);
      }

      for (int var31 = 0; var31 < var3; var31++) {
         if (var31 > 0) {
            float[] var13 = new float[3];
            tangent(var4, var31, var3, var13);
            transport(var5, var13, var6);
            transport(var5, var13, var7);
            var5 = var13;
            var10 += dist(var4, var31 - 1, var31);
         }

         float var33 = var1.radii()[var31];
         float var14 = var1.vStart() + (1.0F - var1.vStart()) * (var11 > 0.0F ? var10 / var11 : 0.0F);
         int[] var15 = new int[var2 + 1];

         for (int var16 = 0; var16 <= var2; var16++) {
            double var17 = (double)(var16 * 2) * Math.PI / (double)var2;
            float var19 = (float)Math.cos(var17);
            float var20 = (float)Math.sin(var17);
            float var21 = var33;
            float var22 = var33;
            if (var1.flat() && var1.kind() == AntlerDesign.Kind.BEAM && var31 > var3 / 4) {
               var21 = var33 * 1.5F;
               var22 = var33 * 0.55F;
            }

            float var23 = var6[0] * var19 * var21 + var7[0] * var20 * var22;
            float var24 = var6[1] * var19 * var21 + var7[1] * var20 * var22;
            float var25 = var6[2] * var19 * var21 + var7[2] * var20 * var22;
            float var26 = var6[0] * var19 * var22 + var7[0] * var20 * var21;
            float var27 = var6[1] * var19 * var22 + var7[1] * var20 * var21;
            float var28 = var6[2] * var19 * var22 + var7[2] * var20 * var21;
            float var29 = (float)Math.sqrt((double)(var26 * var26 + var27 * var27 + var28 * var28));
            float var30 = 1.0F + 0.06F * (float)Math.sin(var17 * 5.0 + (double)var31 * 0.7);
            var15[var16] = this.vertex(
               var4[var31 * 3] + var23 * var30,
               var4[var31 * 3 + 1] + var24 * var30,
               var4[var31 * 3 + 2] + var25 * var30,
               var26 / var29,
               var27 / var29,
               var28 / var29,
               (float)var16 / (float)var2,
               var14
            );
         }

         if (var9 != null) {
            for (int var38 = 0; var38 < var2; var38++) {
               this.tri(var9[var38], var15[var38], var15[var38 + 1]);
               this.tri(var9[var38], var15[var38 + 1], var9[var38 + 1]);
            }
         } else {
            var8 = var15;
         }

         var9 = var15;
      }

      int var32 = var3 - 1;
      int var34 = this.vertex(
         var4[var32 * 3] + var5[0] * var1.radii()[var32],
         var4[var32 * 3 + 1] + var5[1] * var1.radii()[var32],
         var4[var32 * 3 + 2] + var5[2] * var1.radii()[var32],
         var5[0],
         var5[1],
         var5[2],
         0.5F,
         1.0F
      );

      for (int var35 = 0; var35 < var2; var35++) {
         this.tri(var9[var35], var34, var9[var35 + 1]);
      }

      float[] var36 = new float[3];
      tangent(var4, 0, var3, var36);
      int var37 = this.vertex(
         var4[0] - var36[0] * 0.002F, var4[1] - var36[1] * 0.002F, var4[2] - var36[2] * 0.002F, -var36[0], -var36[1], -var36[2], 0.5F, var1.vStart()
      );

      for (int var39 = 0; var39 < var2; var39++) {
         this.tri(var8[var39 + 1], var37, var8[var39]);
      }
   }

   private void burr(float var1, float var2, float var3, float var4, int var5, int var6) {
      int var7 = Math.max(18, var5 * 3);
      int[] var8 = new int[var7 + 1];
      int[] var9 = new int[var7 + 1];
      int[] var10 = new int[var7 + 1];

      for (int var11 = 0; var11 <= var7; var11++) {
         double var12 = (double)(var11 * 2) * Math.PI / (double)var7;
         float var14 = (float)Math.cos(var12);
         float var15 = (float)Math.sin(var12);
         float var16 = 1.0F + 0.09F * (float)Math.sin(var12 * 5.0 + (double)var6 * 2.1) + 0.05F * (float)Math.sin(var12 * 9.0 + (double)var6);
         float var17 = var4 * var16;
         var8[var11] = this.vertex(
            var1 + var14 * var4 * 0.72F, var2 - 0.008F, var3 + var15 * var4 * 0.72F, var14 * 0.3F, -0.95F, var15 * 0.3F, (float)var11 / (float)var7, 0.02F
         );
         var9[var11] = this.vertex(var1 + var14 * var17, var2 + 0.004F, var3 + var15 * var17, var14, 0.0F, var15, (float)var11 / (float)var7, 0.04F);
         var10[var11] = this.vertex(
            var1 + var14 * var4 * 0.7F, var2 + 0.016F, var3 + var15 * var4 * 0.7F, var14 * 0.3F, 0.95F, var15 * 0.3F, (float)var11 / (float)var7, 0.07F
         );
      }

      for (int var18 = 0; var18 < var7; var18++) {
         this.tri(var8[var18], var8[var18 + 1], var9[var18 + 1]);
         this.tri(var8[var18], var9[var18 + 1], var9[var18]);
         this.tri(var9[var18], var9[var18 + 1], var10[var18 + 1]);
         this.tri(var9[var18], var10[var18 + 1], var10[var18]);
      }
   }

   private void plug(float var1, float var2, float var3, float var4, int var5) {
      int var6 = Math.max(8, var5 * 2);
      float var7 = var4 * 0.78F;
      int[] var8 = new int[var6 + 1];
      int[] var9 = new int[var6 + 1];

      for (int var10 = 0; var10 <= var6; var10++) {
         double var11 = (double)(var10 * 2) * Math.PI / (double)var6;
         float var13 = (float)Math.cos(var11);
         float var14 = (float)Math.sin(var11);
         var8[var10] = this.vertex(
            var1 + var13 * var7 * 1.08F, var2 - 0.022F, var3 + var14 * var7 * 1.08F, var13, 0.0F, var14, (float)var10 / (float)var6, 0.0F
         );
         var9[var10] = this.vertex(var1 + var13 * var7, var2 + 0.006F, var3 + var14 * var7, var13, 0.0F, var14, (float)var10 / (float)var6, 0.02F);
      }

      for (int var15 = 0; var15 < var6; var15++) {
         this.tri(var8[var15], var8[var15 + 1], var9[var15 + 1]);
         this.tri(var8[var15], var9[var15 + 1], var9[var15]);
      }
   }

   private static void tangent(float[] var0, int var1, int var2, float[] var3) {
      int var4 = Math.max(0, var1 - 1);
      int var5 = Math.min(var2 - 1, var1 + 1);
      var3[0] = var0[var5 * 3] - var0[var4 * 3];
      var3[1] = var0[var5 * 3 + 1] - var0[var4 * 3 + 1];
      var3[2] = var0[var5 * 3 + 2] - var0[var4 * 3 + 2];
      normalize(var3);
   }

   private static float dist(float[] var0, int var1, int var2) {
      float var3 = var0[var2 * 3] - var0[var1 * 3];
      float var4 = var0[var2 * 3 + 1] - var0[var1 * 3 + 1];
      float var5 = var0[var2 * 3 + 2] - var0[var1 * 3 + 2];
      return (float)Math.sqrt((double)(var3 * var3 + var4 * var4 + var5 * var5));
   }

   private static void normalize(float[] var0) {
      float var1 = (float)Math.sqrt((double)(var0[0] * var0[0] + var0[1] * var0[1] + var0[2] * var0[2]));
      if (var1 < 1.0E-9F) {
         var0[0] = 0.0F;
         var0[1] = 1.0F;
         var0[2] = 0.0F;
      } else {
         var0[0] /= var1;
         var0[1] /= var1;
         var0[2] /= var1;
      }
   }

   private static void cross(float[] var0, float[] var1, float[] var2) {
      var2[0] = var0[1] * var1[2] - var0[2] * var1[1];
      var2[1] = var0[2] * var1[0] - var0[0] * var1[2];
      var2[2] = var0[0] * var1[1] - var0[1] * var1[0];
      normalize(var2);
   }

   private static void perpendicular(float[] var0, float[] var1) {
      float[] var2 = Math.abs(var0[2]) < 0.9F ? new float[]{0.0F, 0.0F, 1.0F} : new float[]{1.0F, 0.0F, 0.0F};
      float var3 = var2[0] * var0[0] + var2[1] * var0[1] + var2[2] * var0[2];
      var1[0] = var2[0] - var0[0] * var3;
      var1[1] = var2[1] - var0[1] * var3;
      var1[2] = var2[2] - var0[2] * var3;
      normalize(var1);
   }

   private static void transport(float[] var0, float[] var1, float[] var2) {
      float var3 = var0[1] * var1[2] - var0[2] * var1[1];
      float var4 = var0[2] * var1[0] - var0[0] * var1[2];
      float var5 = var0[0] * var1[1] - var0[1] * var1[0];
      float var6 = (float)Math.sqrt((double)(var3 * var3 + var4 * var4 + var5 * var5));
      float var7 = var0[0] * var1[0] + var0[1] * var1[1] + var0[2] * var1[2];
      if (!(var6 < 1.0E-7F)) {
         var3 /= var6;
         var4 /= var6;
         var5 /= var6;
         float var8 = (float)Math.atan2((double)var6, (double)var7);
         float var9 = (float)Math.cos((double)var8);
         float var10 = (float)Math.sin((double)var8);
         float var11 = var3 * var2[0] + var4 * var2[1] + var5 * var2[2];
         float var12 = var4 * var2[2] - var5 * var2[1];
         float var13 = var5 * var2[0] - var3 * var2[2];
         float var14 = var3 * var2[1] - var4 * var2[0];
         var2[0] = var2[0] * var9 + var12 * var10 + var3 * var11 * (1.0F - var9);
         var2[1] = var2[1] * var9 + var13 * var10 + var4 * var11 * (1.0F - var9);
         var2[2] = var2[2] * var9 + var14 * var10 + var5 * var11 * (1.0F - var9);
         normalize(var2);
      }
   }
}
