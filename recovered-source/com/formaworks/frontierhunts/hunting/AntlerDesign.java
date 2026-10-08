package com.formaworks.frontierhunts.hunting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SplittableRandom;
import java.util.Map.Entry;

public final class AntlerDesign {
   public final List<AntlerDesign.Branch> branches = new ArrayList<>();
   public final float[] burrs = new float[8];
   private float scoreInches;
   private static final int CACHE_SIZE = 64;
   private static final LinkedHashMap<DeerTraits, AntlerDesign> CACHE = new LinkedHashMap<DeerTraits, AntlerDesign>(16, 0.75F, true) {
      @Override
      protected boolean removeEldestEntry(Entry<DeerTraits, AntlerDesign> var1) {
         return this.size() > 64;
      }
   };

   public static AntlerDesign of(DeerTraits var0) {
      synchronized (CACHE) {
         return CACHE.computeIfAbsent(var0, AntlerDesign::new);
      }
   }

   public float grossScoreInches() {
      return this.scoreInches;
   }

   private AntlerDesign(DeerTraits var1) {
      if (var1.buck()) {
         float var2 = var1.rackScale();
         float var3 = var1.rackMass();
         float var4 = 0.21F * var1.spreadScale() * (float)Math.pow((double)Math.max(0.2F, var2), 0.7F);
         float var5 = 0.0F;

         for (int var6 = 0; var6 < 2; var6++) {
            boolean var7 = var6 == 1;
            float var8 = var7 ? 1.0F : -1.0F;
            SplittableRandom var9 = new SplittableRandom((long)var1.seed() * -7046029254386353131L + (long)(var7 ? 17 : 91));
            float var10 = var1.sideScale(var7) * var2;
            int var11 = var1.typicalPoints(var7);
            float var12 = 0.0165F * Math.max(0.35F, var3) * var1.sideScale(var7);
            float var13 = var8 * 0.072F;
            this.burrs[var6 * 4] = var13;
            this.burrs[var6 * 4 + 1] = 0.004F;
            this.burrs[var6 * 4 + 2] = 0.0F;
            this.burrs[var6 * 4 + 3] = var12 * 1.45F;
            byte var16 = 16;
            float var14;
            float[] var15;
            if (var11 == 1) {
               float var17 = Math.max(0.09F, 0.34F * var10);
               var15 = new float[var16 * 3];

               for (int var18 = 0; var18 < var16; var18++) {
                  float var19 = (float)var18 / ((float)var16 - 1.0F);
                  var15[var18 * 3] = var13 + var8 * 0.025F * var19;
                  var15[var18 * 3 + 1] = var17 * var19;
                  var15[var18 * 3 + 2] = 0.03F * var19 - 0.02F * var19 * var19;
               }

               var14 = var17;
            } else {
               float var43 = var4 * (1.0F + (float)var9.nextDouble(-0.05, 0.05)) / 0.24F;
               float var45 = var1.rackHeightScale() * var10;
               float var48 = var1.rackDepthScale() * var10;
               float var20 = (float)var9.nextDouble(0.45, 0.65);
               float[][] var21 = new float[][]{
                  {0.072F, 0.0F, 0.0F},
                  {0.12F, 0.06F, 0.06F},
                  {0.19F, 0.11F, 0.05F},
                  {0.235F, 0.14F, -0.02F},
                  {0.23F, 0.15F, -0.1F},
                  {0.19F, 0.16F, -0.17F},
                  {0.125F, 0.17F, -0.215F},
                  {0.072F, 0.18F, -0.235F}
               };
               float var22 = var20;
               float[][] var23 = new float[var21.length][3];

               for (int var24 = 0; var24 < var21.length; var24++) {
                  float var25 = (var21[var24][0] - 0.072F) * var43;
                  if (var24 >= 5) {
                     var25 *= 0.55F + 0.45F * (1.0F - (var22 - 0.45F) / 0.2F * 0.5F);
                  }

                  var23[var24][0] = var13 + var8 * var25;
                  var23[var24][1] = var21[var24][1] * var45;
                  var23[var24][2] = var21[var24][2] * var48;
               }

               var15 = new float[var16 * 3];

               for (int var57 = 0; var57 < var16; var57++) {
                  float var60 = (float)var57 / ((float)var16 - 1.0F) * (float)(var21.length - 1);
                  int var26 = Math.min(var21.length - 2, (int)Math.floor((double)var60));
                  float var27 = var60 - (float)var26;
                  float[] var28 = var23[Math.max(0, var26 - 1)];
                  float[] var29 = var23[var26];
                  float[] var30 = var23[var26 + 1];
                  float[] var31 = var23[Math.min(var21.length - 1, var26 + 2)];

                  for (int var32 = 0; var32 < 3; var32++) {
                     var15[var57 * 3 + var32] = 0.5F
                        * (
                           2.0F * var29[var32]
                              + (-var28[var32] + var30[var32]) * var27
                              + (2.0F * var28[var32] - 5.0F * var29[var32] + 4.0F * var30[var32] - var31[var32]) * var27 * var27
                              + (-var28[var32] + 3.0F * var29[var32] - 3.0F * var30[var32] + var31[var32]) * var27 * var27 * var27
                        );
                  }

                  var15[var57 * 3] = var15[var57 * 3] + var8 * (float)Math.sin((double)var57 * 0.9 + (double)var1.seed() * 0.001) * 0.003F * var10;
               }

               var14 = pathLength(var15);
            }

            float[] var44 = new float[var16];

            for (int var46 = 0; var46 < var16; var46++) {
               float var49 = (float)var46 / ((float)var16 - 1.0F);
               float var52 = var12 * (1.0F - 0.45F * var49);
               if (var49 > 0.82F) {
                  var52 *= 1.0F - (var49 - 0.82F) / 0.18F * 0.82F;
               }

               var44[var46] = Math.max(0.0012F, var52);
            }

            boolean var47 = var1.abnormal() >= 14 && var9.nextDouble() < 0.6;
            this.branches.add(new AntlerDesign.Branch(AntlerDesign.Kind.BEAM, var15, var44, 0.0F, var47));
            float var50 = var14;
            float[] var53 = new float[]{0.07F, 0.33F, 0.52F, 0.68F, 0.8F, 0.89F};
            float[] var54 = new float[]{0.1F, 0.22F, 0.2F, 0.13F, 0.08F, 0.055F};
            ArrayList var55 = new ArrayList();
            int var56 = var11 <= 1 ? 0 : (var11 == 2 ? 1 : var11 - 1);

            for (int var58 = 0; var58 < var56; var58++) {
               int var61 = var11 == 2 ? 2 : var58;
               float var64 = var53[var61] + (float)var9.nextDouble(-0.02, 0.02);
               float var66 = var54[var61] * var10 * (0.85F + (float)var9.nextDouble(0.3)) * (1.0F + (float)(var1.rackGenes() - 70) * 0.002F);
               if (var11 == 2) {
                  var66 *= 0.6F;
               }

               float[] var67 = pointAt(var15, var64);
               float var69 = radiusAt(var44, var64) * 0.9F;
               var67[1] += radiusAt(var44, var64) * 0.4F;
               float var71 = -var8 * (var61 == 0 ? 0.2F : 0.1F);
               float var73 = 1.0F;
               float var75 = var61 == 0 ? -0.3F : (float)var9.nextDouble(-0.02, 0.14);
               float var33 = -var8 * 0.16F;
               float var34 = -0.14F;
               float[] var35 = tube(var67, var71, var73, var75, var33, var34, var66, 7);
               this.branches.add(new AntlerDesign.Branch(AntlerDesign.Kind.TINE, var35, taper(var69, 7), 0.25F + var64 * 0.3F, var47));
               var55.add(new float[]{var67[0], var67[1], var67[2], var66, var64});
               var50 += var66;
            }

            int var59 = var1.abnormalPoints(var7);

            for (int var62 = 0; var62 < var59; var62++) {
               double var65 = var9.nextDouble();
               float[] var68;
               float var70;
               float var72;
               float var74;
               float var76;
               float var78;
               if (var65 < 0.3) {
                  float var79 = (float)var9.nextDouble(0.3, 0.62);
                  var68 = pointAt(var15, var79);
                  var78 = radiusAt(var44, var79) * 0.75F;
                  var70 = var8 * (float)var9.nextDouble(0.15, 0.45);
                  var72 = -1.0F;
                  var74 = (float)var9.nextDouble(-0.25, 0.25);
                  var76 = (float)var9.nextDouble(0.05, 0.14) * var10;
               } else if (var65 < 0.55 && !var55.isEmpty()) {
                  float[] var82 = (float[])var55.get(var9.nextInt(var55.size()));
                  float var84 = (float)var9.nextDouble(0.25, 0.75) * var82[3];
                  var68 = new float[]{var82[0] - var8 * 0.01F * var84, var82[1] + var84, var82[2]};
                  var78 = radiusAt(var44, var82[4]) * 0.5F;
                  var70 = var8 * (float)var9.nextDouble(0.5, 1.0);
                  var72 = (float)var9.nextDouble(0.1, 0.8);
                  var74 = (float)var9.nextDouble(-0.4, 0.4);
                  var76 = (float)var9.nextDouble(0.025, 0.07) * var10;
               } else if (var65 < 0.75) {
                  float var80 = (float)var9.nextDouble(0.1, 0.85);
                  var68 = pointAt(var15, var80);
                  var78 = radiusAt(var44, var80) * 0.6F;
                  var70 = var8 * (float)var9.nextDouble(0.3, 1.0);
                  var72 = (float)var9.nextDouble(0.0, 0.9);
                  var74 = (float)var9.nextDouble(-0.5, 0.5);
                  var76 = (float)var9.nextDouble(0.02, 0.06) * var10;
               } else if (var65 < 0.88) {
                  var68 = pointAt(var15, (float)var9.nextDouble(0.0, 0.1));
                  var78 = var12 * 0.6F;
                  var70 = var8 * (float)var9.nextDouble(0.4, 1.0);
                  var72 = (float)var9.nextDouble(0.2, 0.9);
                  var74 = (float)var9.nextDouble(-0.6, 0.3);
                  var76 = (float)var9.nextDouble(0.02, 0.06) * var10;
               } else {
                  float var81 = (float)var9.nextDouble(0.25, 0.9);
                  var68 = pointAt(var15, var81);
                  var78 = radiusAt(var44, var81) * 0.7F;
                  var70 = -var8 * 0.08F;
                  var72 = 1.0F;
                  var74 = (float)var9.nextDouble(-0.2, 0.3);
                  var76 = (float)var9.nextDouble(0.05, 0.15) * var10;
               }

               var76 = Math.max(0.02F, var76);
               float[] var83 = tube(
                  var68, var70, var72, var74, var8 * 0.05F * (float)var9.nextDouble(-1.0, 1.0), 0.05F * (float)var9.nextDouble(-1.0, 1.0), var76, 5
               );
               this.branches.add(new AntlerDesign.Branch(AntlerDesign.Kind.ABNORMAL, var83, taper(Math.max(0.002F, var78), 5), 0.35F, false));
               var50 += var76;
            }

            float var63 = (float)((Math.PI * 2) * (double)var12);
            var50 += var63 * 3.29F;
            this.scoreInches += var50 / 0.0254F;
            var5 = Math.max(var5, 0.0F);
         }

         float var37 = 0.0F;
         float var38 = 0.0F;

         for (AntlerDesign.Branch var40 : this.branches) {
            if (var40.kind == AntlerDesign.Kind.BEAM) {
               for (int var41 = 0; var41 < var40.count(); var41++) {
                  float var42 = var40.points[var41 * 3];
                  if (var42 < 0.0F) {
                     var37 = Math.max(var37, -var42);
                  } else {
                     var38 = Math.max(var38, var42);
                  }
               }
            }
         }

         var5 = Math.max(0.0F, var37 + var38 - 0.04F);
         this.scoreInches += var5 / 0.0254F;
      }
   }

   private static float pathLength(float[] var0) {
      float var1 = 0.0F;

      for (byte var2 = 3; var2 < var0.length; var2 += 3) {
         float var3 = var0[var2] - var0[var2 - 3];
         float var4 = var0[var2 + 1] - var0[var2 - 2];
         float var5 = var0[var2 + 2] - var0[var2 - 1];
         var1 += (float)Math.sqrt((double)(var3 * var3 + var4 * var4 + var5 * var5));
      }

      return var1;
   }

   private static float[] pointAt(float[] var0, float var1) {
      int var2 = var0.length / 3;
      float var3 = Math.max(0.0F, Math.min((float)(var2 - 1), var1 * (float)(var2 - 1)));
      int var4 = (int)Math.floor((double)var3);
      int var5 = Math.min(var2 - 1, var4 + 1);
      float var6 = var3 - (float)var4;
      return new float[]{
         var0[var4 * 3] + (var0[var5 * 3] - var0[var4 * 3]) * var6,
         var0[var4 * 3 + 1] + (var0[var5 * 3 + 1] - var0[var4 * 3 + 1]) * var6,
         var0[var4 * 3 + 2] + (var0[var5 * 3 + 2] - var0[var4 * 3 + 2]) * var6
      };
   }

   private static float radiusAt(float[] var0, float var1) {
      int var2 = var0.length;
      float var3 = Math.max(0.0F, Math.min((float)(var2 - 1), var1 * (float)(var2 - 1)));
      int var4 = (int)Math.floor((double)var3);
      int var5 = Math.min(var2 - 1, var4 + 1);
      return var0[var4] + (var0[var5] - var0[var4]) * (var3 - (float)var4);
   }

   private static float[] taper(float var0, int var1) {
      float[] var2 = new float[var1];

      for (int var3 = 0; var3 < var1; var3++) {
         float var4 = (float)var3 / ((float)var1 - 1.0F);
         var2[var3] = Math.max(0.001F, var0 * (1.0F - 0.45F * var4) * (var4 > 0.72F ? 1.0F - (var4 - 0.72F) / 0.28F * 0.85F : 1.0F));
      }

      return var2;
   }

   private static float[] tube(float[] var0, float var1, float var2, float var3, float var4, float var5, float var6, int var7) {
      float var8 = (float)Math.sqrt((double)(var1 * var1 + var2 * var2 + var3 * var3));
      var1 /= var8;
      var2 /= var8;
      var3 /= var8;
      float[] var9 = new float[var7 * 3];
      float var10 = var6 / (float)(var7 - 1);
      float var11 = var0[0];
      float var12 = var0[1];
      float var13 = var0[2];

      for (int var14 = 0; var14 < var7; var14++) {
         var9[var14 * 3] = var11;
         var9[var14 * 3 + 1] = var12;
         var9[var14 * 3 + 2] = var13;
         float var15 = (float)var14 / ((float)var7 - 1.0F);
         float var16 = var1 + var4 * var15;
         float var18 = var3 + var5 * var15;
         float var19 = (float)Math.sqrt((double)(var16 * var16 + var2 * var2 + var18 * var18));
         var11 += var16 / var19 * var10;
         var12 += var2 / var19 * var10;
         var13 += var18 / var19 * var10;
      }

      return var9;
   }

   public static record Branch(AntlerDesign.Kind kind, float[] points, float[] radii, float vStart, boolean flat) {
      public int count() {
         return this.radii.length;
      }
   }

   public static enum Kind {
      BEAM,
      TINE,
      ABNORMAL;
   }
}
