package com.formaworks.frontierhunts.client.tree;

import java.util.ArrayList;
import java.util.List;

public final class LeafShape {
   private static final int[][] DIRS = new int[][]{{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
   private static final int[][][] FACE = new int[][][]{
      {{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}},
      {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}},
      {{0, 0, 0}, {0, 1, 0}, {1, 1, 0}, {1, 0, 0}},
      {{0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}},
      {{0, 0, 0}, {0, 0, 1}, {0, 1, 1}, {0, 1, 0}},
      {{1, 0, 0}, {1, 1, 0}, {1, 1, 1}, {1, 0, 1}}
   };
   private static final float[] PULL = new float[]{0.0F, 0.72F, 0.58F, 0.46F, 0.34F, 0.24F, 0.16F, 0.08F, 0.0F};
   private final LeafShape.Probe p;
   private final boolean[] leaf = new boolean[27];
   private final List<LeafShape.Quad> out = new ArrayList<>();

   private LeafShape(LeafShape.Probe var1) {
      this.p = var1;

      for (int var2 = -1; var2 <= 1; var2++) {
         for (int var3 = -1; var3 <= 1; var3++) {
            for (int var4 = -1; var4 <= 1; var4++) {
               this.leaf[idx(var2, var3, var4)] = var1.kind(var2, var3, var4) == 2;
            }
         }
      }
   }

   private static int idx(int var0, int var1, int var2) {
      return (var0 + 1) * 9 + (var1 + 1) * 3 + var2 + 1;
   }

   public static List<LeafShape.Quad> build(LeafShape.Probe var0) {
      LeafShape var1 = new LeafShape(var0);
      var1.run();
      return var1.out;
   }

   private boolean open(int var1) {
      int var2 = this.p.kind(DIRS[var1][0], DIRS[var1][1], DIRS[var1][2]);
      return var2 == 0 || var2 == 1;
   }

   private float[] lattice(int var1, int var2, int var3) {
      float var4 = 0.0F;
      float var5 = 0.0F;
      float var6 = 0.0F;
      int var7 = 0;

      for (int var8 = -1; var8 <= 0; var8++) {
         for (int var9 = -1; var9 <= 0; var9++) {
            for (int var10 = -1; var10 <= 0; var10++) {
               if (this.leaf[idx(var1 + var8, var2 + var9, var3 + var10)]) {
                  var4 += (float)(var1 + var8) + 0.5F;
                  var5 += (float)(var2 + var9) + 0.5F;
                  var6 += (float)(var3 + var10) + 0.5F;
                  var7++;
               }
            }
         }
      }

      float[] var12 = new float[]{(float)var1, (float)var2, (float)var3};
      if (var7 > 0 && var7 < 8) {
         float var13 = PULL[var7];
         var12[0] += (var4 / (float)var7 - (float)var1) * var13;
         var12[1] += (var5 / (float)var7 - (float)var2) * var13;
         var12[2] += (var6 / (float)var7 - (float)var3) * var13;
      }

      long var14 = TreeShape.mix(this.p.hash(var1, var2, var3));
      float var11 = 0.13F;
      var12[0] += ((float)(var14 & 1023L) / 1023.0F - 0.5F) * 2.0F * var11;
      var12[1] += ((float)(var14 >>> 10 & 1023L) / 1023.0F - 0.5F) * 2.0F * var11 * 0.8F;
      var12[2] += ((float)(var14 >>> 20 & 1023L) / 1023.0F - 0.5F) * 2.0F * var11;
      return var12;
   }

   private void run() {
      boolean var1 = false;

      for (int var2 = 0; var2 < 6; var2++) {
         var1 |= this.open(var2);
      }

      if (var1) {
         float[] var24 = new float[]{0.5F, 0.5F, 0.5F};
         long var3 = TreeShape.mix(this.p.hash(0, 0, 0));
         int var5 = 0;

         for (int var6 = 0; var6 < 6; var6++) {
            if (this.open(var6)) {
               float[][] var7 = new float[4][];

               for (int var8 = 0; var8 < 4; var8++) {
                  int[] var9 = FACE[var6][var8];
                  var7[var8] = this.lattice(var9[0], var9[1], var9[2]);
               }

               float[] var26 = new float[]{(float)DIRS[var6][0], (float)DIRS[var6][1], (float)DIRS[var6][2]};
               float[] var27 = scale(add(add(var7[0], var7[1]), add(var7[2], var7[3])), 0.25F);
               long var10 = TreeShape.mix(var3 + (long)var6 * 5369127L);
               float var12 = 0.1F + (float)(var10 & 255L) / 255.0F * 0.1F;
               var27 = add(var27, scale(var26, var12));
               float[][] var13 = new float[4][];

               for (int var14 = 0; var14 < 4; var14++) {
                  var13[var14] = scale(add(var7[var14], var7[(var14 + 1) % 4]), 0.5F);
               }

               for (int var29 = 0; var29 < 4; var29++) {
                  float[][] var15 = new float[][]{var7[var29], var13[var29], var27, var13[(var29 + 3) % 4]};
                  float[][] var16 = new float[4][];

                  for (int var17 = 0; var17 < 4; var17++) {
                     var16[var17] = this.faceUv(var6, var15[var17]);
                  }

                  LeafShape.Quad var33 = new LeafShape.Quad();
                  var33.texture = 0;
                  var33.shade = true;

                  for (int var18 = 0; var18 < 4; var18++) {
                     float[] var19 = norm(add(scale(var26, 0.6F), norm(sub(var15[var18], var24))));
                     put(var33, var18, var15[var18], var16[var18], var19);
                  }

                  this.out.add(var33);
               }

               boolean var30 = var6 == 0;
               boolean var31 = var6 == 1;
               if (!var30 || (var10 >>> 12 & 3L) == 0L) {
                  float var32 = rnd(var10, 1);
                  float var34 = rnd(var10, 2);
                  float var35 = rnd(var10, 3);
                  if (this.p.conifer() && !var31 && !var30) {
                     float[] var38 = add(var27, add(scale(var26, 0.28F + 0.12F * var32), new float[]{0.0F, -0.08F - 0.1F * var34, 0.0F}));
                     float[] var40 = norm(add(new float[]{0.0F, 1.0F, 0.0F}, scale(var26, 0.55F + 0.3F * var35)));
                     this.card(var38, var40, 0.78F + 0.18F * var34, var32 * 6.283F, var10);
                     if (var35 > 0.55F) {
                        float[] var41 = add(var27, scale(var26, 0.18F));
                        float[] var22 = new float[]{-var26[2], 0.0F, var26[0]};
                        float[] var23 = norm(add(var26, scale(var22, var32 - 0.5F)));
                        this.card(var41, var23, 0.62F, var34 * 6.283F, var10 >>> 7);
                     }
                  } else if (this.p.conifer() && var31) {
                     float[] var37 = add(var27, new float[]{0.0F, 0.12F, 0.0F});
                     float[] var39 = norm(new float[]{var32 - 0.5F, 0.35F, var34 - 0.5F});
                     this.card(var37, norm(new float[]{var39[0], 0.15F, var39[2] + 0.01F}), 0.6F, 0.0F, var10);
                  } else {
                     float[] var36 = new float[]{var32 - 0.5F, var34 - 0.5F, var35 - 0.5F};
                     float[] var20 = norm(add(var26, scale(var36, 1.1F)));
                     float[] var21 = add(var27, scale(var26, 0.16F + 0.14F * var34));
                     this.card(var21, var20, 0.7F + 0.2F * var35, var32 * 6.283F, var10);
                  }

                  var5++;
               }
            }
         }

         if (var5 == 0 || !this.p.conifer() && (var3 & 3L) == 0L) {
            float[] var25 = norm(new float[]{rnd(var3, 4) - 0.5F, 0.6F, rnd(var3, 5) - 0.5F});
            this.card(add(var24, new float[]{0.0F, 0.1F, 0.0F}), var25, 0.75F, rnd(var3, 6) * 6.283F, var3);
         }
      }
   }

   private float[] faceUv(int var1, float[] var2) {
      switch (var1) {
         case 0:
         case 1:
            return new float[]{var2[0], var2[2]};
         case 2:
         case 3:
            return new float[]{var2[0], 1.0F - var2[1]};
         default:
            return new float[]{var2[2], 1.0F - var2[1]};
      }
   }

   private void card(float[] var1, float[] var2, float var3, float var4, long var5) {
      float[] var7 = Math.abs(var2[1]) < 0.9F ? new float[]{0.0F, 1.0F, 0.0F} : new float[]{1.0F, 0.0F, 0.0F};
      float[] var8 = norm(cross(var7, var2));
      float[] var9 = cross(var2, var8);
      float var10 = (float)Math.cos((double)var4);
      float var11 = (float)Math.sin((double)var4);
      float[] var12 = add(scale(var8, var10), scale(var9, var11));
      float[] var13 = add(scale(var8, -var11), scale(var9, var10));
      float[][] var14 = new float[][]{
         add(var1, add(scale(var12, -var3), scale(var13, -var3))),
         add(var1, add(scale(var12, var3), scale(var13, -var3))),
         add(var1, add(scale(var12, var3), scale(var13, var3))),
         add(var1, add(scale(var12, -var3), scale(var13, var3)))
      };
      boolean var15 = (var5 & 1L) != 0L;
      float[][] var16 = new float[][]{{var15 ? 1.0F : 0.0F, 1.0F}, {var15 ? 0.0F : 1.0F, 1.0F}, {var15 ? 0.0F : 1.0F, 0.0F}, {var15 ? 1.0F : 0.0F, 0.0F}};
      float[] var17 = norm(add(var2, new float[]{0.0F, 0.8F, 0.0F}));
      LeafShape.Quad var18 = new LeafShape.Quad();
      var18.texture = 1;

      for (int var19 = 0; var19 < 4; var19++) {
         put(var18, var19, var14[var19], var16[var19], var17);
      }

      this.out.add(var18);
      LeafShape.Quad var21 = new LeafShape.Quad();
      var21.texture = 1;

      for (int var20 = 0; var20 < 4; var20++) {
         put(var21, var20, var14[3 - var20], var16[3 - var20], var17);
      }

      this.out.add(var21);
   }

   private static void put(LeafShape.Quad var0, int var1, float[] var2, float[] var3, float[] var4) {
      int var5 = var1 * 8;
      var0.v[var5] = var2[0];
      var0.v[var5 + 1] = var2[1];
      var0.v[var5 + 2] = var2[2];
      var0.v[var5 + 3] = var3[0];
      var0.v[var5 + 4] = var3[1];
      var0.v[var5 + 5] = var4[0];
      var0.v[var5 + 6] = var4[1];
      var0.v[var5 + 7] = var4[2];
   }

   private static float rnd(long var0, int var2) {
      return (float)(TreeShape.mix(var0 + (long)var2 * -7046029254386353131L) & 65535L) / 65535.0F;
   }

   private static float[] add(float[] var0, float[] var1) {
      return new float[]{var0[0] + var1[0], var0[1] + var1[1], var0[2] + var1[2]};
   }

   private static float[] sub(float[] var0, float[] var1) {
      return new float[]{var0[0] - var1[0], var0[1] - var1[1], var0[2] - var1[2]};
   }

   private static float[] scale(float[] var0, float var1) {
      return new float[]{var0[0] * var1, var0[1] * var1, var0[2] * var1};
   }

   private static float[] cross(float[] var0, float[] var1) {
      return new float[]{var0[1] * var1[2] - var0[2] * var1[1], var0[2] * var1[0] - var0[0] * var1[2], var0[0] * var1[1] - var0[1] * var1[0]};
   }

   private static float[] norm(float[] var0) {
      float var1 = (float)Math.sqrt((double)(var0[0] * var0[0] + var0[1] * var0[1] + var0[2] * var0[2]));
      return var1 < 1.0E-6F ? new float[]{0.0F, 1.0F, 0.0F} : new float[]{var0[0] / var1, var0[1] / var1, var0[2] / var1};
   }

   public interface Probe {
      int kind(int var1, int var2, int var3);

      long hash(int var1, int var2, int var3);

      boolean conifer();
   }

   public static final class Quad {
      public final float[] v = new float[32];
      public int texture;
      public boolean shade;
   }
}
