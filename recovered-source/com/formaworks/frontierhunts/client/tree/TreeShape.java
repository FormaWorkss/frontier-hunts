package com.formaworks.frontierhunts.client.tree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public final class TreeShape {
   public static final int AIR = 0;
   public static final int LOG = 1;
   public static final int LEAVES = 2;
   public static final int GROUND = 3;
   public static final int SOLID = 4;
   private static final int SIDES = 8;
   private static final int BIG_SIDES = 14;
   private static final int SCAN = 12;
   private final TreeShape.Probe p;
   private final List<TreeShape.Quad> out = new ArrayList<>();
   private static final TreeShape.Blob SINGLE = new TreeShape.Blob(new int[0][], 0.0F, 0.0F, 0, 0, false);
   private final Map<Long, TreeShape.Blob> blobs = new HashMap<>();
   private static final float[] ZERO2 = new float[]{0.0F, 0.0F};
   private static final int LOFT = 12;

   private TreeShape(TreeShape.Probe var1) {
      this.p = var1;
   }

   public static List<TreeShape.Quad> build(TreeShape.Probe var0) {
      TreeShape var1 = new TreeShape(var0);
      return var1.run() ? var1.out : null;
   }

   private boolean log(int var1, int var2, int var3) {
      return this.p.kind(var1, var2, var3) == 1;
   }

   private static long key(int var0, int var1, int var2) {
      return (long)var0 + 512L << 40 | (long)var1 + 512L << 20 | (long)var2 + 512L;
   }

   private TreeShape.Blob blob(int var1, int var2, int var3) {
      TreeShape.Blob var4 = this.blobs.get(key(var1, var2, var3));
      if (var4 != null) {
         return var4 == SINGLE ? null : var4;
      } else if (this.log(var1, var2, var3) && this.p.axis(var1, var2, var3) == 1) {
         ArrayList var5 = new ArrayList();
         ArrayDeque var6 = new ArrayDeque();
         HashSet var7 = new HashSet();
         var6.add(new int[]{var1, var3});
         var7.add(key(var1, var2, var3));
         boolean var8 = false;

         while (!var6.isEmpty()) {
            int[] var9 = (int[])var6.poll();
            var5.add(var9);
            if (var5.size() > 24) {
               var8 = true;
               break;
            }

            int[][] var10 = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

            for (int[] var14 : var10) {
               int var15 = var9[0] + var14[0];
               int var16 = var9[1] + var14[1];
               if (var7.add(key(var15, var2, var16)) && this.log(var15, var2, var16) && this.p.axis(var15, var2, var16) == 1) {
                  var6.add(new int[]{var15, var16});
               }
            }
         }

         if (!var8 && var5.size() < 4) {
            for (int[] var25 : var5) {
               this.blobs.put(key(var25[0], var2, var25[1]), SINGLE);
            }

            return null;
         } else {
            int var22 = Integer.MAX_VALUE;
            int var24 = Integer.MIN_VALUE;
            int var26 = Integer.MAX_VALUE;
            int var27 = Integer.MIN_VALUE;
            float var28 = 0.0F;
            float var29 = 0.0F;
            int[] var30 = null;

            for (int[] var17 : var5) {
               var22 = Math.min(var22, var17[0]);
               var24 = Math.max(var24, var17[0]);
               var26 = Math.min(var26, var17[1]);
               var27 = Math.max(var27, var17[1]);
               var28 += (float)var17[0];
               var29 += (float)var17[1];
               if (var30 == null || var17[0] < var30[0] || var17[0] == var30[0] && var17[1] < var30[1]) {
                  var30 = var17;
               }
            }

            int var32 = var24 - var22 + 1;
            int var33 = var27 - var26 + 1;
            boolean var18 = var8 || Math.min(var32, var33) == 1 && Math.max(var32, var33) >= 3 || (float)var5.size() < (float)(var32 * var33) * 0.5F;
            TreeShape.Blob var19 = new TreeShape.Blob(
               var5.toArray(new int[0][]), var28 / (float)var5.size() + 0.5F, var29 / (float)var5.size() + 0.5F, var30[0], var30[1], var18
            );

            for (int[] var21 : var5) {
               this.blobs.put(key(var21[0], var2, var21[1]), SINGLE);
            }

            if (var18 ? this.onGround(var19, var2) : this.rooted(var19, var2)) {
               for (int[] var35 : var5) {
                  this.blobs.put(key(var35[0], var2, var35[1]), var19);
               }

               return var19;
            } else {
               return null;
            }
         }
      } else {
         this.blobs.put(key(var1, var2, var3), SINGLE);
         return null;
      }
   }

   private boolean leafless(int var1, int var2, int var3) {
      for (int var4 = -2; var4 <= 2; var4++) {
         for (int var5 = -1; var5 <= 2; var5++) {
            for (int var6 = -2; var6 <= 2; var6++) {
               if (this.p.kind(var1 + var4, var2 + var5, var3 + var6) == 2) {
                  return false;
               }
            }
         }
      }

      return true;
   }

   private boolean onGround(TreeShape.Blob var1, int var2) {
      for (int[] var6 : var1.cells) {
         if (firm(this.p.kind(var6[0], var2 - 1, var6[1]))) {
            return true;
         }
      }

      return false;
   }

   private boolean rooted(TreeShape.Blob var1, int var2) {
      for (int[] var6 : var1.cells) {
         if (firm(this.p.kind(var6[0], var2 - 1, var6[1]))) {
            return true;
         }
      }

      for (int[] var11 : var1.cells) {
         if (this.log(var11[0], var2 - 1, var11[1])) {
            TreeShape.Blob var7 = this.blob(var11[0], var2 - 1, var11[1]);
            if (var7 != null && !var7.wall) {
               return true;
            }
         }
      }

      return false;
   }

   private static float junction(float var0, float var1) {
      return Math.min((var0 + var1) * 0.5F, Math.min(var0, var1) * 1.25F + 0.02F);
   }

   private int run(int var1, int var2, int var3, int var4) {
      int var5 = 0;

      for (int var6 = 1; var6 <= 12 && this.log(var1, var2 + var4 * var6, var3); var6++) {
         var5++;
      }

      return var5;
   }

   private float radius(int var1, int var2, int var3) {
      TreeShape.Blob var4 = this.blob(var1, var2, var3);
      if (var4 != null) {
         int var15 = var4.dx0;
         int var16 = var4.dz0;
         int var17 = this.run(var15, var2, var16, -1);
         int var18 = this.run(var15, var2, var16, 1);
         int var19 = var17 + var18 + 1;
         float var21 = 1.0F - 0.22F * Math.min(1.0F, ((float)var17 + 0.5F) / (float)Math.max(4, var19));
         float var23 = var17 == 0 ? 1.1F : 1.0F;
         float var24 = (float)Math.sqrt((double)var4.cells.length / Math.PI) * 1.08F * var21 * var23;
         int var25 = 0;

         while (var25 < 4 && this.blob(var15, var2 + var25 + 1, var16) != null) {
            var25++;
         }

         if (var25 < 4 && this.log(var15, var2 + var25 + 1, var16) || var25 < 4 && this.anyLogAbove(var4, var2 + var25 + 1)) {
            float var14 = Math.min(1.0F, ((float)var25 + 0.5F) / 3.5F);
            var24 = 0.46F + (var24 - 0.46F) * var14;
         }

         return var24;
      } else {
         int var5 = this.run(var1, var2, var3, -1);
         int var6 = this.run(var1, var2, var3, 1);
         int var7 = var5 + var6 + 1;
         boolean var8 = var7 >= 2 && (var5 > 0 || firm(this.p.kind(var1, var2 - 1, var3)));
         float var9 = var5 == 0 ? 1.22F : (var5 == 1 ? 1.07F : 1.0F);
         if (var8) {
            float var20 = clamp(0.28F + 0.021F * (float)var7, 0.34F, 0.56F);
            return var9 * var20 * (1.0F - 0.52F * Math.min(1.0F, ((float)var5 + 0.5F) / (float)Math.max(3, var7)));
         } else {
            int var10 = 0;

            for (int var11 = -1; var11 <= 1; var11++) {
               for (int var12 = -1; var12 <= 1; var12++) {
                  for (int var13 = -1; var13 <= 1; var13++) {
                     if ((var11 | var12 | var13) != 0 && this.log(var1 + var11, var2 + var12, var3 + var13)) {
                        var10++;
                     }
                  }
               }
            }

            boolean var22 = this.p.axis(var1, var2, var3) != 1
               && (
                  firm(this.p.kind(var1, var2 - 1, var3))
                     || this.log(var1, var2 - 1, var3) && this.p.axis(var1, var2 - 1, var3) != 1
                     || this.log(var1, var2 + 1, var3) && this.p.axis(var1, var2 + 1, var3) != 1
                     || this.leafless(var1, var2, var3)
               );
            return var22 ? 0.4F : clamp(0.14F + 0.03F * (float)var10, 0.15F, 0.24F);
         }
      }
   }

   private boolean anyLogAbove(TreeShape.Blob var1, int var2) {
      for (int[] var6 : var1.cells) {
         if (this.log(var6[0], var2, var6[1])) {
            return true;
         }
      }

      return false;
   }

   private boolean connected(int var1, int var2, int var3, TreeShape.Blob var4) {
      if (!this.log(var1, var2, var3)) {
         return false;
      } else {
         int var5 = (var1 != 0 ? 1 : 0) + (var2 != 0 ? 1 : 0) + (var3 != 0 ? 1 : 0);
         if (var2 == 0 && var4 != null && var4.contains(var1, var3)) {
            return false;
         } else if (var5 == 1) {
            int var9 = this.p.axis(0, 0, 0);
            int var10 = this.p.axis(var1, var2, var3);
            int var11 = var1 != 0 ? 0 : (var2 != 0 ? 1 : 2);
            if (var11 != 1 && var9 == 1 && var10 == 1) {
               return this.stubJoin(var1, var3);
            } else {
               return var9 == 1 || var11 == var9 || var11 == 1 && var10 == 1 ? var10 == 1 || var11 == var10 || var11 == 1 && var9 == 1 : false;
            }
         } else if (var5 == 2 && var2 != 0 && this.fork(var1, var2, var3)) {
            return true;
         } else if (var2 == 0 && this.p.axis(0, 0, 0) == 1 && this.p.axis(var1, 0, var3) == 1 && !this.stubJoin(var1, var3)) {
            return false;
         } else if (var1 == 0 || !this.log(var1, 0, 0) && (var5 != 3 || !this.log(var1, var2, 0) && !this.log(var1, 0, var3))) {
            if (var2 == 0 || !this.log(0, var2, 0) && (var5 != 3 || !this.log(var1, var2, 0) && !this.log(0, var2, var3))) {
               if (var3 == 0 || !this.log(0, 0, var3) && (var5 != 3 || !this.log(0, var2, var3) && !this.log(var1, 0, var3))) {
                  if (var5 == 2) {
                     int var6 = var1 != 0 ? var1 : 0;
                     int var7 = var2 != 0 ? var2 : 0;
                     int var8 = var3 != 0 ? var3 : 0;
                     if (var6 != 0 && this.log(var6, 0, 0) || var7 != 0 && this.log(0, var7, 0) || var8 != 0 && this.log(0, 0, var8)) {
                        return false;
                     }
                  }

                  return true;
               } else {
                  return false;
               }
            } else {
               return false;
            }
         } else {
            return false;
         }
      }
   }

   private boolean stub(int var1, int var2, int var3) {
      return this.log(var1, var2, var3)
         && this.p.axis(var1, var2, var3) == 1
         && !this.log(var1, var2 - 1, var3)
         && !firm(this.p.kind(var1, var2 - 1, var3))
         && this.run(var1, var2, var3, 1) < 4;
   }

   private boolean stem(int var1, int var2, int var3) {
      return this.log(var1, var2, var3) && this.p.axis(var1, var2, var3) == 1 && (this.log(var1, var2 - 1, var3) || firm(this.p.kind(var1, var2 - 1, var3)));
   }

   private boolean stubJoin(int var1, int var2) {
      return this.stub(0, 0, 0) && this.stem(var1, 0, var2) || this.stub(var1, 0, var2) && this.stem(0, 0, 0);
   }

   private boolean fork(int var1, int var2, int var3) {
      int var6 = var2 > 0 ? var1 : 0;
      int var7 = var2 > 0 ? var2 : 0;
      int var8 = var2 > 0 ? var3 : 0;
      int var9 = var2 > 0 ? 0 : var1;
      int var10 = var2 > 0 ? 0 : var2;
      int var11 = var2 > 0 ? 0 : var3;
      if (this.p.axis(var6, var7, var8) == 1 && this.p.axis(var9, var10, var11) == 1) {
         boolean var12 = this.log(var6, var7 - 1, var8);
         boolean var13 = this.log(var9, var10 + 1, var11) && this.p.axis(var9, var10 + 1, var11) == 1;
         return !var12 && var13 && var1 != 0 ^ var3 != 0;
      } else {
         return false;
      }
   }

   private boolean built() {
      int var1 = this.p.axis(0, 0, 0);
      int var2 = (this.log(1, 0, 0) ? 1 : 0) + (this.log(-1, 0, 0) ? 1 : 0) + (this.log(0, 0, 1) ? 1 : 0) + (this.log(0, 0, -1) ? 1 : 0);
      if (var1 == 1 && var2 >= 1) {
         TreeShape.Blob var3 = this.blob(0, 0, 0);
         if (var3 != null && var3.wall) {
            return true;
         }
      }

      return false;
   }

   private boolean run() {
      if (this.built()) {
         return false;
      } else {
         TreeShape.Blob var1 = this.blob(0, 0, 0);
         boolean var2 = var1 != null;
         boolean var3 = var2 && var1.dx0 == 0 && var1.dz0 == 0;
         float var4 = this.radius(0, 0, 0);
         float[] var5 = var2 ? new float[]{var1.cx, 0.5F, var1.cz} : this.smoothCentre(0, 0, 0);
         ArrayList var6 = new ArrayList();

         for (int var7 = -1; var7 <= 1; var7++) {
            for (int var8 = -1; var8 <= 1; var8++) {
               for (int var9 = -1; var9 <= 1; var9++) {
                  if ((var7 | var8 | var9) != 0 && this.connected(var7, var8, var9, var1)) {
                     var6.add(new int[]{var7, var8, var9});
                  }
               }
            }
         }

         boolean var18 = false;
         boolean var19 = false;

         for (int[] var10 : var6) {
            if (var10[0] == 0 && var10[2] == 0) {
               if (var10[1] > 0) {
                  var18 = true;
               } else {
                  var19 = true;
               }
            }
         }

         for (int[] var24 : var6) {
            boolean var11 = var24[0] == 0 && var24[2] == 0;
            if (var2) {
               if (var24[1] == 0 || !var11 && this.blob(var24[0], var24[1], var24[2]) == null) {
                  float[] var35 = new float[]{0.5F, 0.5F, 0.5F};
                  float[] var41 = this.centreOf(var24[0], var24[1], var24[2]);
                  float var47 = this.radiusOf(var24[0], var24[1], var24[2]);
                  float var15 = junction(Math.min(var4, 0.3F), var47);
                  this.tube(var35, lerp(var35, var41, 0.5F), Math.min(0.32F, var15 * 1.2F), var15, 8, 0, true);
               }
            } else {
               float var12 = this.radiusOf(var24[0], var24[1], var24[2]);
               float var13 = junction(var4, var12);
               float[] var14 = this.centreOf(var24[0], var24[1], var24[2]);
               if (var11) {
                  this.loft(var5, var4, lerp(var5, var14, 0.5F), var13, 0);
               } else {
                  this.tube(var5, lerp(var5, var14, 0.5F), var4, var13, 8, 0, true);
               }
            }
         }

         if (var2 && !var3) {
            return true;
         } else if (var3) {
            boolean var23 = false;

            for (byte var26 = 1; var26 >= -1; var26 -= 2) {
               List var31 = this.trunkEnds(var1, var26);

               for (float[] var44 : var31) {
                  float var50 = junction(var4, var44[3]);
                  float[] var55 = lerp(var5, new float[]{var44[0], var44[1], var44[2]}, 0.5F);
                  if (var50 < var4 * 0.65F) {
                     float[] var56 = lerp(var5, var55, 0.45F);
                     float var17 = var4 * 0.55F + var50 * 0.45F;
                     this.loft(var5, var4, var56, var17, 0);
                     this.loft(var56, var17, var55, var50, 0);
                  } else {
                     this.loft(var5, var4, var55, var50, 0);
                  }
               }

               if (var26 > 0) {
                  var18 = !var31.isEmpty();
               } else {
                  var19 = !var31.isEmpty();
               }
            }

            boolean var27 = false;

            for (int[] var51 : var1.cells) {
               var27 |= firm(this.p.kind(var51[0], -1, var51[1]));
            }

            if (!var19 && var27) {
               float var33 = var4 * 1.22F;
               this.loft(var5, var4, new float[]{var5[0], -0.04F, var5[2]}, var33, 0);
               this.roots(var5, var33, Math.min(12, 6 + var1.cells.length / 2), true);
            }

            if (!var18) {
               float[] var34 = new float[]{var5[0], 0.8F, var5[2]};
               float[] var40 = new float[]{var5[0], 1.05F, var5[2]};
               float[] var46 = new float[]{var5[0], 1.22F, var5[2]};
               this.loft(var5, var4, var34, var4 * 0.82F, 0);
               this.loft(var34, var4 * 0.82F, var40, var4 * 0.5F, 0);
               this.loft(var40, var4 * 0.5F, var46, 0.03F, 0);
            }

            if (var18 || var19) {
               if (this.p.conifer()) {
                  this.whorl(var5, var4, true, var18);
               } else {
                  this.crown(var5, var4, true, var18);
               }
            }

            return true;
         } else {
            boolean var22 = firm(this.p.kind(0, -1, 0));
            boolean var25 = var18 || var6.isEmpty() && this.p.axis(0, 0, 0) == 1;
            if (!var19 && var22 && var25) {
               float var28 = var4 * 1.45F;
               this.loft(var5, var4, new float[]{var5[0], -0.04F, var5[2]}, var28, 0);
               this.roots(var5, var28, 6, false);
            }

            if (var18 || var19) {
               if (this.p.conifer()) {
                  this.whorl(var5, var4, false, var18);
               } else {
                  this.crown(var5, var4, false, var18);
               }
            }

            if (var6.isEmpty()) {
               int var29 = this.p.axis(0, 0, 0);
               float[] var36 = var29 == 0 ? new float[]{1.0F, 0.0F, 0.0F} : (var29 == 2 ? new float[]{0.0F, 0.0F, 1.0F} : new float[]{0.0F, 1.0F, 0.0F});
               float var42 = var29 == 1 ? 0.36F : 0.46F;
               float[] var48 = new float[]{0.5F - var36[0] * 0.5F, 0.5F - var36[1] * 0.5F, 0.5F - var36[2] * 0.5F};
               float[] var52 = new float[]{0.5F + var36[0] * 0.5F, 0.5F + var36[1] * 0.5F, 0.5F + var36[2] * 0.5F};
               if (var22 && var29 == 1) {
                  this.tube(var5, var52, var42, var42, 8, 0, true);
               } else {
                  this.tube(var48, var52, var42, var42, 8, 0, true);
                  this.disc(var48, var36, var42, true);
               }

               this.disc(var52, var36, var42, false);
            } else if (var6.size() == 1) {
               int[] var30 = (int[])var6.get(0);
               float[] var37 = norm(new float[]{(float)(-var30[0]), (float)(-var30[1]), (float)(-var30[2])});
               boolean var43 = var30[0] == 0 && var30[2] == 0;
               boolean var49 = this.p.axis(0, 0, 0) != 1
                  && !var43
                  && (firm(this.p.kind(0, -1, 0)) || this.log(0, -1, 0) || this.log(0, 1, 0) || this.leafless(0, 0, 0));
               if (var49) {
                  float[] var53 = new float[]{var5[0] + var37[0] * 0.45F, var5[1] + var37[1] * 0.45F, var5[2] + var37[2] * 0.45F};
                  this.tube(var5, var53, var4, var4, 8, 0, true);
                  this.disc(var53, var37, var4, false);
               } else if (!(var37[1] < -0.5F) || !var22) {
                  float var54 = var43 && var30[1] < 0 ? 0.55F : 0.4F;
                  float[] var16 = new float[]{var5[0] + var37[0] * var54, var5[1] + var37[1] * var54, var5[2] + var37[2] * var54};
                  if (var43) {
                     this.loft(var5, var4, var16, var4 * 0.12F, 0);
                  } else {
                     this.tube(var5, var16, var4, var4 * 0.25F, 8, 0, true);
                  }
               }
            } else if (var6.size() != 2 || !collinear((int[])var6.get(0), (int[])var6.get(1))) {
               this.ball(var5, var4 * 1.02F);
            }

            return true;
         }
      }
   }

   private List<float[]> trunkEnds(TreeShape.Blob var1, int var2) {
      ArrayList var3 = new ArrayList();
      HashSet var4 = new HashSet();

      for (int[] var8 : var1.cells) {
         if (this.log(var8[0], var2, var8[1])) {
            TreeShape.Blob var9 = this.blob(var8[0], var2, var8[1]);
            if (var9 != null) {
               if (var4.add(var9)) {
                  var3.add(new float[]{var9.cx, (float)var2 + 0.5F, var9.cz, this.radiusAt(var8[0], var2, var8[1])});
               }
            } else {
               float[] var10 = this.centreOf(var8[0], var2, var8[1]);
               var3.add(new float[]{var10[0], var10[1], var10[2], this.radiusAt(var8[0], var2, var8[1])});
            }
         }
      }

      return var3;
   }

   private static boolean firm(int var0) {
      return var0 == 3 || var0 == 4;
   }

   private static boolean collinear(int[] var0, int[] var1) {
      return var0[0] == -var1[0] && var0[1] == -var1[1] && var0[2] == -var1[2];
   }

   private float radiusOf(int var1, int var2, int var3) {
      return this.radiusAt(var1, var2, var3);
   }

   private float radiusAt(int var1, int var2, int var3) {
      return new TreeShape(this.shifted(var1, var2, var3)).radius(0, 0, 0);
   }

   private TreeShape.Probe shifted(final int var1, final int var2, final int var3) {
      return new TreeShape.Probe() {
         @Override
         public int kind(int var1x, int var2x, int var3x) {
            return TreeShape.this.p.kind(var1x + var1, var2x + var2, var3x + var3);
         }

         @Override
         public int axis(int var1x, int var2x, int var3x) {
            return TreeShape.this.p.axis(var1x + var1, var2x + var2, var3x + var3);
         }

         @Override
         public int light(int var1x, int var2x, int var3x) {
            return TreeShape.this.p.light(var1x + var1, var2x + var2, var3x + var3);
         }

         @Override
         public long seed() {
            return TreeShape.this.p.seed();
         }

         @Override
         public long hash(int var1x, int var2x, int var3x) {
            return TreeShape.this.p.hash(var1x + var1, var2x + var2, var3x + var3);
         }

         @Override
         public int worldY() {
            return TreeShape.this.p.worldY() + var2;
         }

         @Override
         public boolean conifer() {
            return TreeShape.this.p.conifer();
         }
      };
   }

   private float[] centreOf(int var1, int var2, int var3) {
      TreeShape.Blob var4 = var1 == 0 && var3 == 0 ? this.blob(var1, var2, var3) : null;
      return var4 != null ? new float[]{var4.cx, (float)var2 + 0.5F, var4.cz} : this.smoothCentre(var1, var2, var3);
   }

   private float[] rawCentre(int var1, int var2, int var3) {
      TreeShape.Blob var4 = this.blob(var1, var2, var3);
      if (var4 != null) {
         return new float[]{var4.cx, (float)var2 + 0.5F, var4.cz};
      } else {
         float[] var5 = this.wob(var1, var2, var3);
         float var6 = (float)var1 + 0.5F + var5[0];
         float var7 = (float)var3 + 0.5F + var5[1];
         if (this.p.axis(var1, var2, var3) == 1) {
            for (int var8 = 1; var8 <= 9 && this.log(var1, var2 - var8, var3) && this.p.axis(var1, var2 - var8, var3) == 1; var8++) {
               TreeShape.Blob var9 = this.blob(var1, var2 - var8, var3);
               if (var9 != null) {
                  if (!var9.wall) {
                     float var10 = 1.0F - (float)var8 / 10.0F;
                     var6 += (var9.cx - var6) * var10;
                     var7 += (var9.cz - var7) * var10;
                  }
                  break;
               }
            }
         }

         return new float[]{var6, (float)var2 + 0.5F, var7};
      }
   }

   private float[] smoothCentre(int var1, int var2, int var3) {
      float[] var4 = this.rawCentre(var1, var2, var3);
      if (this.blob(var1, var2, var3) != null) {
         return var4;
      } else {
         TreeShape.Probe var5 = this.shifted(var1, var2, var3);
         TreeShape var6 = new TreeShape(var5);
         Object var7 = null;
         int[] var8 = null;
         int[] var9 = null;
         int var10 = 0;

         for (int var11 = -1; var11 <= 1 && var10 <= 2; var11++) {
            for (int var12 = -1; var12 <= 1 && var10 <= 2; var12++) {
               for (int var13 = -1; var13 <= 1 && var10 <= 2; var13++) {
                  if ((var11 | var12 | var13) != 0 && var6.connected(var11, var12, var13, (TreeShape.Blob)var7)) {
                     if (var10 == 0) {
                        var8 = new int[]{var11, var12, var13};
                     } else if (var10 == 1) {
                        var9 = new int[]{var11, var12, var13};
                     }

                     var10++;
                  }
               }
            }
         }

         if (var10 == 2 && !firm(this.p.kind(var1, var2 - 1, var3))) {
            float[] var14 = this.rawCentre(var1 + var8[0], var2 + var8[1], var3 + var8[2]);
            float[] var15 = this.rawCentre(var1 + var9[0], var2 + var9[1], var3 + var9[2]);
            float[] var16 = new float[]{(var14[0] + var15[0]) * 0.5F, (var14[1] + var15[1]) * 0.5F, (var14[2] + var15[2]) * 0.5F};
            return new float[]{var4[0] + (var16[0] - var4[0]) * 0.5F, var4[1] + (var16[1] - var4[1]) * 0.5F, var4[2] + (var16[2] - var4[2]) * 0.5F};
         } else {
            return var4;
         }
      }
   }

   private float[] wob(int var1, int var2, int var3) {
      if (this.log(var1, var2, var3)
         && this.p.axis(var1, var2, var3) == 1
         && (this.log(var1, var2 + 1, var3) || this.log(var1, var2 - 1, var3))
         && this.blob(var1, var2, var3) == null) {
         long var4 = mix(this.p.hash(var1, -this.p.worldY(), var3));
         double var6 = (double)(this.p.worldY() + var2);
         double var8 = (double)(var4 & 1023L) / 1023.0 * 6.283;
         double var10 = (double)(var4 >>> 10 & 1023L) / 1023.0 * 6.283;
         double var12 = (double)(var4 >>> 20 & 1023L) / 1023.0 * 6.283;
         double var14 = (double)(var4 >>> 30 & 1023L) / 1023.0 * 6.283;
         float var16 = 0.075F;
         return new float[]{
            var16 * (float)(0.65 * Math.sin(var6 * 0.52 + var8) + 0.35 * Math.sin(var6 * 1.23 + var10)),
            var16 * (float)(0.65 * Math.sin(var6 * 0.47 + var12) + 0.35 * Math.sin(var6 * 1.11 + var14))
         };
      } else {
         return ZERO2;
      }
   }

   static long mix(long var0) {
      var0 = (var0 ^ var0 >>> 30) * -4658895280553007687L;
      var0 = (var0 ^ var0 >>> 27) * -7723592293110705685L;
      return var0 ^ var0 >>> 31;
   }

   private static float rnd(long var0, int var2) {
      return (float)(mix(var0 + (long)var2 * -7046029254386353131L) >>> 40 & 16777215L) / 1.6777216E7F;
   }

   private int leafCount(int var1, int var2, int var3) {
      int var4 = 0;

      for (int var5 = var1; var5 <= var2; var5++) {
         for (int var6 = -var3; var6 <= var3; var6++) {
            for (int var7 = -var3; var7 <= var3; var7++) {
               if (this.p.kind(var6, var5, var7) == 2) {
                  var4++;
               }
            }
         }
      }

      return var4;
   }

   private int kindAt(float[] var1) {
      return this.p.kind((int)Math.floor((double)var1[0]), (int)Math.floor((double)var1[1]), (int)Math.floor((double)var1[2]));
   }

   private float trace(float[] var1, float[] var2, float var3) {
      boolean var4 = this.kindAt(var1) == 2;

      for (float var5 = 0.3F; var5 <= var3 + 0.001F; var5 += 0.3F) {
         int var6 = this.kindAt(add(var1, scale(var2, var5)));
         if (var6 == 4 || var6 == 3) {
            return var5 - 0.3F;
         }

         if (var6 == 2) {
            var4 = true;
         } else if (var4 && var6 == 0) {
            return Math.min(var3, var5 - 0.05F);
         }
      }

      return var3;
   }

   private boolean leafy(float[] var1, float[] var2, float var3) {
      for (float var4 = var3 * 0.4F; var4 <= var3 + 0.001F; var4 += 0.35F) {
         float[] var5 = add(var1, scale(var2, var4));
         if (this.kindAt(var5) == 2 || this.kindAt(add(var5, new float[]{0.0F, 1.0F, 0.0F})) == 2) {
            return true;
         }
      }

      return false;
   }

   private double phase() {
      long var1 = mix(this.p.hash(0, -this.p.worldY(), 0));
      return (double)(var1 & 4095L) / 4096.0 * Math.PI * 2.0 + (double)this.p.worldY() * 2.39996;
   }

   private void crown(float[] var1, float var2, boolean var3, boolean var4) {
      int var5 = var3 ? 4 : 3;
      int var6 = this.run(0, 0, 0, -1);
      int var7 = this.leafCount(-1, 2, var5);
      long var8 = mix(this.p.seed() ^ 25214903917L);
      double var10 = this.phase();
      if (var6 >= 1 && var7 >= 4 || !var4 && var7 > 0) {
         int var23 = (var3 ? 4 : 2) + (!var4 ? 1 : 0) + (var7 > 20 ? 1 : 0);

         for (int var13 = 0; var13 < var23; var13++) {
            long var24 = mix(var8 + (long)var13 * 40503L);
            if (!var4 || !(rnd(var24, 9) < 0.2F)) {
               double var25 = var10 + (double)var13 * Math.PI * 2.0 / (double)var23 + (double)((rnd(var24, 0) - 0.5F) * 0.9F);
               float var26 = !var4 && var13 == 0 ? 2.2F + rnd(var24, 1) : (!var4 ? 0.9F + rnd(var24, 1) * 0.9F : 0.4F + rnd(var24, 1) * 0.75F);
               float[] var27 = norm(new float[]{(float)Math.cos(var25), var26, (float)Math.sin(var25)});
               float var28 = (var3 ? 2.4F : 1.4F) + rnd(var24, 2) * (var3 ? 1.4F : 0.9F);
               float var21 = var3 ? 0.22F + rnd(var24, 3) * 0.06F : Math.min(0.15F, var2 * 0.5F);
               float[] var22 = add(var1, scale(var27, var2 * 0.45F));
               this.limb(var22, var27, var28, var21, 0, var24, true);
            }
         }
      } else if (var6 >= 2 && rnd(var8, 4) < 0.3F) {
         double var12 = var10 + (double)(rnd(var8, 5) * 6.0F);
         float[] var14 = norm(new float[]{(float)Math.cos(var12), 0.2F + rnd(var8, 6) * 0.3F, (float)Math.sin(var12)});
         float[] var15 = add(var1, scale(var14, var2 * 0.6F));
         float var16 = var2 * 0.5F + 0.25F + rnd(var8, 7) * 0.25F;
         float[] var17 = add(var15, scale(var14, var16));
         float var18 = var3 ? 0.12F : 0.075F;
         this.tube(var15, var17, var18, var18 * 0.7F, 5, 0, true);
         float[] var19 = norm(add(var14, new float[]{rnd(var8, 8) - 0.5F, 0.5F, rnd(var8, 10) - 0.5F}));
         float[] var20 = add(var17, scale(var19, 0.18F + rnd(var8, 11) * 0.2F));
         this.tube(var17, var20, var18 * 0.7F, var18 * 0.25F, 4, 0, true);
      }
   }

   private void limb(float[] var1, float[] var2, float var3, float var4, int var5, long var6, boolean var8) {
      float var9 = this.trace(var1, var2, var3);
      if (!(var9 < 0.3F) && (!var8 || this.leafy(var1, var2, var9))) {
         float[] var10 = add(var1, scale(var2, var9));
         float[][] var11 = frame(var2);
         float var12 = (rnd(var6, 3) - 0.5F) * 0.22F * var9;
         float[] var13 = add(lerp(var1, var10, 0.5F), add(scale(var11[0], var12), new float[]{0.0F, var5 == 0 ? 0.07F * var9 : -0.03F * var9, 0.0F}));
         float var14 = var5 >= 2 ? Math.max(0.008F, var4 * 0.3F) : var4 * (var5 == 0 ? 0.62F : 0.55F);
         float var15 = (var4 + var14) * 0.5F;
         int var16 = var5 == 0 ? (var4 > 0.18F ? 6 : 5) : (var5 == 1 ? 4 : 3);
         if (var5 >= 2) {
            this.tube(var1, var10, var4, var14, 3, 0, true);
            this.sprig(var10, 1.1F + rnd(var6, 6) * 0.5F, var2, var6, false);
         } else {
            this.tube(var1, var13, var4, var15, var16, 0, true);
            this.tube(var13, var10, var15, var14, var16, 0, true);
            if (var5 == 1 && rnd(var6, 5) < 0.6F) {
               this.sprig(var10, 1.2F + rnd(var6, 6) * 0.4F, var2, var6 ^ 119L, false);
            }

            double var17 = (double)rnd(var6, 4) * Math.PI * 2.0;

            for (int var19 = 0; var19 < 2; var19++) {
               long var20 = mix(var6 ^ (long)(var19 + 1) * 7146057691288625177L);
               double var22 = var17 + (double)var19 * Math.PI + (double)((rnd(var20, 0) - 0.5F) * 0.8F);
               float[] var24 = add(scale(var11[0], (float)Math.cos(var22)), scale(var11[1], (float)Math.sin(var22)));
               float var25 = 0.5F + rnd(var20, 1) * 0.45F;
               float[] var26 = norm(add(add(var2, scale(var24, var25)), new float[]{0.0F, 0.25F, 0.0F}));
               float var27 = var3 * (0.5F + rnd(var20, 2) * 0.3F);
               this.limb(var10, var26, Math.max(0.45F, var27), var14 * 0.92F, var5 + 1, var20, false);
            }

            if (var5 == 0 && var9 > 1.0F) {
               long var28 = mix(var6 ^ 2685821657736338717L);
               double var21 = (double)rnd(var28, 0) * Math.PI * 2.0;
               float[] var23 = add(scale(var11[0], (float)Math.cos(var21)), scale(var11[1], (float)Math.sin(var21)));
               float[] var29 = norm(add(add(var2, scale(var23, 0.9F)), new float[]{0.0F, 0.2F, 0.0F}));
               this.limb(var13, var29, 0.6F + rnd(var28, 1) * 0.5F, var15 * 0.5F, 2, var28, false);
            }
         }
      }
   }

   private void whorl(float[] var1, float var2, boolean var3, boolean var4) {
      int var5 = this.leafCount(-1, 1, 2);
      int var6 = this.run(0, 0, 0, -1);
      long var7 = mix(this.p.seed() ^ 461845907L);
      double var9 = this.phase();
      if (var5 >= 3) {
         int var11 = var3 ? 6 : 4;

         for (int var12 = 0; var12 < var11; var12++) {
            long var13 = mix(var7 + (long)var12 * 2135587861L);
            double var15 = var9 + (double)var12 * Math.PI * 2.0 / (double)var11 + (double)((rnd(var13, 0) - 0.5F) * 0.7F);
            float[] var17 = new float[]{(float)Math.cos(var15), 0.0F, (float)Math.sin(var15)};
            float var18 = 0.5F;
            int var19 = 0;

            for (float var20 = 0.8F; var20 <= (var3 ? 4.0F : 3.2F); var20 += 0.4F) {
               int var21 = this.kindAt(add(var1, scale(var17, var20)));
               if (var21 == 2) {
                  var18 = var20;
                  var19 = 0;
               } else if (var21 != 0 || ++var19 > 1) {
                  break;
               }
            }

            float var40 = Math.min(var3 ? 4.0F : 3.0F, var18 + 0.15F);
            float[] var41 = norm(new float[]{var17[0], -0.08F - rnd(var13, 1) * 0.12F, var17[2]});
            float var22 = var3 ? 0.085F : 0.06F;
            float[] var23 = add(var1, scale(var17, var2 * 0.5F));
            float var24 = this.trace(var23, var41, var40);
            if (!(var24 < 0.3F)) {
               float[] var25 = add(var23, scale(var41, var24 * 0.55F));
               float[] var26 = add(var25, scale(norm(new float[]{var17[0], -0.4F, var17[2]}), var24 * 0.45F));
               this.tube(var23, var25, var22, var22 * 0.6F, var3 ? 4 : 3, 0, true);
               this.tube(var25, var26, var22 * 0.6F, 0.012F, 3, 0, true);
               this.sprig(var26, 1.2F + rnd(var13, 8) * 0.4F, var17, var13, true);
               if (var24 > 1.1F) {
                  this.sprig(add(var25, new float[]{0.0F, -0.05F, 0.0F}), 1.05F, var17, var13 ^ 85L, true);
               }

               if (var24 > 1.5F) {
                  int var27 = rnd(var13, 7) < 0.5F ? -1 : 1;

                  for (int var28 = 0; var28 < 1; var28++) {
                     double var29 = var15 + (double)((float)var27 * (0.7F + rnd(var13, 2 + var27 + 1) * 0.3F));
                     float[] var31 = norm(new float[]{(float)Math.cos(var29), -0.2F, (float)Math.sin(var29)});
                     float[] var32 = add(var25, scale(var31, 0.4F + rnd(var13, 5) * 0.35F));
                     this.tube(var25, var32, var22 * 0.4F, 0.008F, 3, 0, true);
                     this.sprig(var32, 0.8F, var31, var13 ^ 153L, true);
                  }
               }
            }
         }
      } else if (var6 >= 2 && this.leafCount(1, 8, 2) > 0) {
         int var33 = rnd(var7, 1) < 0.75F ? 2 + (rnd(var7, 2) < 0.45F ? 1 : 0) : 0;

         for (int var34 = 0; var34 < var33; var34++) {
            long var35 = mix(var7 + (long)var34 * 1013904242L);
            double var36 = var9 + (double)var34 * 2.4 + (double)(rnd(var35, 0) * 1.2F);
            float[] var37 = norm(new float[]{(float)Math.cos(var36), -0.45F + rnd(var35, 1) * 0.5F, (float)Math.sin(var36)});
            float[] var38 = add(var1, scale(var37, var2 * 0.5F));
            var38[1] += (rnd(var35, 2) - 0.5F) * 0.6F;
            float var39 = 0.35F + rnd(var35, 3) * 0.6F;
            this.tube(var38, add(var38, scale(var37, var39)), var3 ? 0.06F : 0.042F, 0.008F, 3, 0, true);
         }
      }
   }

   private void sprig(float[] var1, float var2, float[] var3, long var4, boolean var6) {
      int var7 = this.kindAt(var1);
      if (var7 != 4 && var7 != 3 && var7 != 1) {
         if (var7 == 2) {
            boolean var8 = false;
            float[][] var9 = new float[][]{
               {0.45F, 0.0F, 0.0F}, {-0.45F, 0.0F, 0.0F}, {0.0F, 0.45F, 0.0F}, {0.0F, -0.45F, 0.0F}, {0.0F, 0.0F, 0.45F}, {0.0F, 0.0F, -0.45F}
            };

            for (float[] var13 : var9) {
               if (this.kindAt(add(var1, var13)) == 0) {
                  var8 = true;
                  break;
               }
            }

            if (!var8) {
               return;
            }
         }

         float var20 = var2 * 0.5F;
         float[] var21 = norm(new float[]{var3[0], 0.0F, var3[2]});
         if (Math.abs(var3[0]) + Math.abs(var3[2]) < 0.001F) {
            var21 = new float[]{1.0F, 0.0F, 0.0F};
         }

         double var22 = Math.atan2((double)var21[2], (double)var21[0]) + (double)((rnd(var4, 11) - 0.5F) * 0.8F);
         float[] var23 = add(var1, scale(norm(var3), var20 * 0.35F));
         if (!var6) {
            for (int var24 = 0; var24 < 2; var24++) {
               double var14 = var22 + (double)var24 * Math.PI / 2.0 + (Math.PI / 4);
               float[] var16 = new float[]{(float)Math.cos(var14) * var20, 0.0F, (float)Math.sin(var14) * var20};
               float[] var17 = new float[]{0.0F, var20 * 0.85F, 0.0F};
               this.card(var23, var16, var17);
            }
         }

         if (var6) {
            if (!var6) {
               float var26 = -0.12F;
               float[] var28 = new float[]{(float)Math.cos(var22) * var20, var26 * var20, (float)Math.sin(var22) * var20};
               float[] var15 = new float[]{
                  (float)(-Math.sin(var22)) * var20 * (var6 ? 0.8F : 1.0F),
                  (rnd(var4, 13) - 0.5F) * 0.3F * var20,
                  (float)Math.cos(var22) * var20 * (var6 ? 0.8F : 1.0F)
               };
               this.card(var23, var28, var15);
               if (var6) {
                  double var30 = var22 + 0.5;
                  float[] var18 = new float[]{(float)Math.cos(var30) * var20 * 0.75F, 0.1F * var20, (float)Math.sin(var30) * var20 * 0.75F};
                  float[] var19 = new float[]{(float)(-Math.sin(var30)) * var20 * 0.6F, 0.25F * var20, (float)Math.cos(var30) * var20 * 0.6F};
                  this.card(add(var23, new float[]{0.0F, 0.08F, 0.0F}), var18, var19);
               }
            } else {
               for (int var25 = 0; var25 < 2; var25++) {
                  double var27 = var22 + (double)var25 * Math.PI / 2.0 + (Math.PI / 4);
                  float[] var29 = new float[]{(float)Math.cos(var27) * var20, 0.0F, (float)Math.sin(var27) * var20};
                  this.card(add(var23, new float[]{0.0F, -0.1F * var20, 0.0F}), var29, new float[]{0.0F, var20 * 0.7F, 0.0F});
               }
            }
         }
      }
   }

   private void card(float[] var1, float[] var2, float[] var3) {
      float[] var4 = norm(cross(var3, var2));
      float[] var5 = sub(sub(var1, var2), var3);
      float[] var6 = sub(add(var1, var2), var3);
      float[] var7 = add(add(var1, var2), var3);
      float[] var8 = add(sub(var1, var2), var3);

      for (int var9 = 0; var9 < 2; var9++) {
         float[] var10 = var9 == 0 ? var4 : scale(var4, -1.0F);
         TreeShape.Quad var11 = new TreeShape.Quad();
         this.put(var11, 0, var8, 0.0F, 0.0F, var10);
         this.put(var11, 1, var5, 0.0F, 1.0F, var10);
         this.put(var11, 2, var6, 1.0F, 1.0F, var10);
         this.put(var11, 3, var7, 1.0F, 0.0F, var10);
         this.finish(var11, 2);
      }
   }

   private void roots(float[] var1, float var2, int var3, boolean var4) {
      long var5 = this.p.seed();
      float var7 = (float)((double)(var5 & 1023L) / 1024.0 * Math.PI * 2.0);

      for (int var8 = 0; var8 < var3; var8++) {
         float var9 = (float)((double)(var5 >>> 10 + var8 * 5 & 31L) / 31.0 - 0.5) * 0.7F;
         float var10 = var7 + (float)((Math.PI * 2) * (double)var8 / (double)var3) + var9;
         float var11 = (float)Math.cos((double)var10);
         float var12 = (float)Math.sin((double)var10);
         float var13 = var2 + (var4 ? 1.4F : 0.7F) + (float)((double)(var5 >>> 3 + var8 * 7 & 15L) / 15.0) * (var4 ? 1.3F : 0.7F);
         float var14 = var1[0] + var11 * var13;
         float var15 = var1[2] + var12 * var13;
         int var16 = (int)Math.floor((double)var14);
         int var17 = (int)Math.floor((double)var15);
         if (!firm(this.p.kind(var16, -1, var17)) || firm(this.p.kind(var16, 0, var17))) {
            var13 = Math.min(var13, var2 + 0.35F);
         }

         float var18 = (var4 ? 0.42F : 0.26F) + var2 * 0.16F;
         float var19 = var4 ? 0.95F : 0.55F;
         float[] var20 = new float[]{var1[0] + var11 * var2 * 0.55F, 0.0F, var1[2] + var12 * var2 * 0.55F};
         float[] var21 = new float[]{var1[0] + var11 * var13, 0.0F, var1[2] + var12 * var13};
         this.root(var20, var21, var18, var19, var11, var12);
      }
   }

   private void root(float[] var1, float[] var2, float var3, float var4, float var5, float var6) {
      float var7 = -var6;
      float var8 = var5;
      byte var9 = 5;
      byte var10 = 4;
      long var11 = mix(this.p.seed() ^ (long)(var5 * 1000.0F) * 31L ^ (long)(var6 * 1000.0F));
      float var13 = (rnd(var11, 1) - 0.5F) * 0.5F;
      float var14 = dist(var1, var2);
      float[][] var15 = null;

      for (int var16 = 0; var16 <= var9; var16++) {
         float var17 = (float)var16 / (float)var9;
         float[] var18 = lerp(var1, var2, var17);
         float var19 = (float)Math.sin((double)var17 * Math.PI) * var13 * var14 * 0.25F;
         var18[0] += var7 * var19;
         var18[2] += var8 * var19;
         float var20 = var3 * (1.0F - var17 * 0.78F);
         float var21 = var4 * (1.0F - var17) * (1.0F - var17 * 0.5F) + 0.015F;
         float var22 = -0.03F - var17 * 0.06F;
         float[][] var23 = new float[var10 + 1][];

         for (int var24 = 0; var24 <= var10; var24++) {
            double var25 = Math.PI * (double)var24 / (double)var10;
            float var27 = (float)Math.cos(var25);
            float var28 = (float)Math.sin(var25);
            var23[var24] = new float[]{
               var18[0] + var7 * var27 * var20,
               var22 + var28 * (var21 - var22),
               var18[2] + var8 * var27 * var20,
               var27 / Math.max(var20, 0.05F),
               var28 / Math.max(var21, 0.05F)
            };
         }

         if (var15 != null) {
            for (int var33 = 0; var33 < var10; var33++) {
               TreeShape.Quad var34 = new TreeShape.Quad();
               float var26 = (float)(var16 - 1) / (float)var9 * var14;
               float var35 = (float)var16 / (float)var9 * var14;
               float[][] var36 = new float[][]{var15[var33], var23[var33], var23[var33 + 1], var15[var33 + 1]};
               float[][] var29 = new float[][]{
                  {(float)var33 / (float)var10 * 0.5F, var26},
                  {(float)var33 / (float)var10 * 0.5F, var35},
                  {(float)(var33 + 1) / (float)var10 * 0.5F, var35},
                  {(float)(var33 + 1) / (float)var10 * 0.5F, var26}
               };

               for (int var30 = 0; var30 < 4; var30++) {
                  float[] var31 = var36[var30];
                  float[] var32 = norm(new float[]{var7 * var31[3], var31[4], var8 * var31[3]});
                  this.put(var34, var30, new float[]{var31[0], var31[1], var31[2]}, var29[var30][0], var29[var30][1], var32);
               }

               this.finish(var34, 0);
            }
         }

         var15 = var23;
      }
   }

   private static float[] canon(float[] var0) {
      float[] var1 = norm(var0);
      if (var1[0] < -1.0E-4F || Math.abs(var1[0]) < 1.0E-4F && (var1[1] < -1.0E-4F || Math.abs(var1[1]) < 1.0E-4F && var1[2] < 0.0F)) {
         var1 = new float[]{-var1[0], -var1[1], -var1[2]};
      }

      return var1;
   }

   private static float[][] frame(float[] var0) {
      float[] var1 = norm(var0);
      if (var1[0] < -1.0E-4F || Math.abs(var1[0]) < 1.0E-4F && (var1[1] < -1.0E-4F || Math.abs(var1[1]) < 1.0E-4F && var1[2] < 0.0F)) {
         var1 = new float[]{-var1[0], -var1[1], -var1[2]};
      }

      float[] var2 = Math.abs(var1[1]) > 0.9F ? new float[]{1.0F, 0.0F, 0.0F} : new float[]{0.0F, 1.0F, 0.0F};
      float[] var3 = norm(cross(var2, var1));
      float[] var4 = cross(var1, var3);
      return new float[][]{var3, var4};
   }

   private void tube(float[] var1, float[] var2, float var3, float var4, int var5, int var6, boolean var7) {
      float[] var8 = sub(var2, var1);
      float var9 = (float)Math.sqrt((double)dot(var8, var8));
      if (!(var9 < 1.0E-4F)) {
         float[][] var10 = frame(var8);
         float var11 = (float)(Math.PI * (double)(var3 + var4));
         float var12 = var11 / (float)var5;
         float[] var13 = canon(var8);
         float var14 = dot(var1, var13);
         float var15 = dot(var2, var13);

         for (int var16 = 0; var16 < var5; var16++) {
            double var17 = (Math.PI * 2) * (double)var16 / (double)var5;
            double var19 = (Math.PI * 2) * (double)(var16 + 1) / (double)var5;
            float[] var21 = add(scale(var10[0], (float)Math.cos(var17)), scale(var10[1], (float)Math.sin(var17)));
            float[] var22 = add(scale(var10[0], (float)Math.cos(var19)), scale(var10[1], (float)Math.sin(var19)));
            float[] var23 = add(var1, scale(var21, var3));
            float[] var24 = add(var1, scale(var22, var3));
            float[] var25 = add(var2, scale(var21, var4));
            float[] var26 = add(var2, scale(var22, var4));
            float var27 = (var3 - var4) / var9;
            float[] var28 = norm(var8);
            float[] var29 = norm(add(var21, scale(var28, var27)));
            float[] var30 = norm(add(var22, scale(var28, var27)));
            float var31 = (float)var16 * var12 % 1.0F;
            if (var31 + var12 > 1.0F) {
               var31 = Math.max(0.0F, 1.0F - var12);
            }

            TreeShape.Quad var32 = new TreeShape.Quad();
            this.put(var32, 0, var23, var31, var14, var29);
            this.put(var32, 1, var25, var31, var15, var29);
            this.put(var32, 2, var26, var31 + Math.min(var12, 1.0F), var15, var30);
            this.put(var32, 3, var24, var31 + Math.min(var12, 1.0F), var14, var30);
            this.finish(var32, var6);
         }
      }
   }

   private void loft(float[] var1, float var2, float[] var3, float var4, int var5) {
      float var6 = var3[1] - var1[1];
      if (!(Math.abs(var6) < 1.0E-4F)) {
         float var7 = (float)(Math.PI * (double)(var2 + var4));
         float var8 = var7 / 12.0F;
         float var9 = (var2 - var4) / var6;

         for (int var10 = 0; var10 < 12; var10++) {
            double var11 = (Math.PI * 2) * (double)var10 / 12.0;
            double var13 = (Math.PI * 2) * (double)(var10 + 1) / 12.0;
            float var15 = (float)Math.cos(var11);
            float var16 = (float)Math.sin(var11);
            float var17 = (float)Math.cos(var13);
            float var18 = (float)Math.sin(var13);
            float[] var19 = new float[]{var1[0] + var15 * var2, var1[1], var1[2] + var16 * var2};
            float[] var20 = new float[]{var1[0] + var17 * var2, var1[1], var1[2] + var18 * var2};
            float[] var21 = new float[]{var3[0] + var15 * var4, var3[1], var3[2] + var16 * var4};
            float[] var22 = new float[]{var3[0] + var17 * var4, var3[1], var3[2] + var18 * var4};
            float[] var23 = norm(new float[]{var15, var9, var16});
            float[] var24 = norm(new float[]{var17, var9, var18});
            float var25 = (float)var10 * var8 % 1.0F;
            if (var25 + var8 > 1.0F) {
               var25 = Math.max(0.0F, 1.0F - var8);
            }

            TreeShape.Quad var26 = new TreeShape.Quad();
            this.put(var26, 0, var19, var25, var1[1], var23);
            this.put(var26, 1, var21, var25, var3[1], var23);
            this.put(var26, 2, var22, var25 + Math.min(var8, 1.0F), var3[1], var24);
            this.put(var26, 3, var20, var25 + Math.min(var8, 1.0F), var1[1], var24);
            this.finish(var26, var5);
         }
      }
   }

   private void disc(float[] var1, float[] var2, float var3, boolean var4) {
      float[] var5 = norm(var2);
      if (var4) {
         var5 = scale(var5, -1.0F);
      }

      float[][] var6 = frame(var5);
      byte var7 = 8;

      for (byte var8 = 0; var8 < var7; var8 += 2) {
         float[][] var9 = new float[3][];

         for (int var10 = 0; var10 < 3; var10++) {
            double var11 = (Math.PI * 2) * (double)(var8 + var10) / (double)var7;
            var9[var10] = add(var1, add(scale(var6[0], (float)Math.cos(var11) * var3), scale(var6[1], (float)Math.sin(var11) * var3)));
         }

         TreeShape.Quad var17 = new TreeShape.Quad();
         float[][] var18 = new float[4][];
         float[][] var12 = new float[][]{var1, var9[0], var9[1], var9[2]};

         for (int var13 = 0; var13 < 4; var13++) {
            float[] var14 = sub(var12[var13], var1);
            float var15 = 0.5F + dot(var14, var6[0]) / (2.0F * var3) * 0.98F;
            float var16 = 0.5F + dot(var14, var6[1]) / (2.0F * var3) * 0.98F;
            var18[var13] = new float[]{var15, var16};
         }

         boolean var19 = dot(cross(var6[0], var6[1]), var5) < 0.0F;
         int[] var20 = var19 ? new int[]{0, 3, 2, 1} : new int[]{0, 1, 2, 3};

         for (int var21 = 0; var21 < 4; var21++) {
            int var22 = var20[var21];
            this.put(var17, var21, var12[var22], var18[var22][0], var18[var22][1], var5);
         }

         this.finish(var17, 1);
      }
   }

   private void ball(float[] var1, float var2) {
      byte var3 = 8;
      float[] var10000 = new float[]{0.0F, 1.0F, 0.0F};

      for (int var5 = 0; var5 < 2; var5++) {
         double var6 = var5 == 0 ? -Math.PI / 2 : 0.0;
         double var8 = var5 == 0 ? 0.0 : Math.PI / 2;

         for (int var10 = 0; var10 < var3; var10++) {
            double var11 = (Math.PI * 2) * (double)var10 / (double)var3;
            double var13 = (Math.PI * 2) * (double)(var10 + 1) / (double)var3;
            float[] var15 = sph(var1, var2, var6, var11);
            float[] var16 = sph(var1, var2, var6, var13);
            float[] var17 = sph(var1, var2, var8, var13);
            float[] var18 = sph(var1, var2, var8, var11);
            TreeShape.Quad var19 = new TreeShape.Quad();
            float var20 = (float)var10 / (float)var3;
            float var21 = (float)(var10 + 1) / (float)var3;
            this.put(var19, 0, var15, var20, 0.5F + (float)var6 * 0.3F, norm(sub(var15, var1)));
            this.put(var19, 1, var18, var20, 0.5F + (float)var8 * 0.3F, norm(sub(var18, var1)));
            this.put(var19, 2, var17, var21, 0.5F + (float)var8 * 0.3F, norm(sub(var17, var1)));
            this.put(var19, 3, var16, var21, 0.5F + (float)var6 * 0.3F, norm(sub(var16, var1)));
            this.finish(var19, 0);
         }
      }
   }

   private static float[] sph(float[] var0, float var1, double var2, double var4) {
      float var6 = (float)Math.cos(var2);
      return new float[]{var0[0] + (float)Math.cos(var4) * var6 * var1, var0[1] + (float)Math.sin(var2) * var1, var0[2] + (float)Math.sin(var4) * var6 * var1};
   }

   private void quad(float[] var1, float[] var2, float[] var3, float[] var4, int var5, float var6, float var7, float var8, float var9) {
      float[] var10 = norm(cross(sub(var2, var1), sub(var4, var1)));
      TreeShape.Quad var11 = new TreeShape.Quad();
      this.put(var11, 0, var1, 0.2F, var6 % 1.0F, var10);
      this.put(var11, 1, var2, 0.2F, var6 % 1.0F + (var7 - var6), var10);
      this.put(var11, 2, var3, 0.2F + Math.min(0.6F, var9 * 2.0F), var6 % 1.0F + (var7 - var6), var10);
      this.put(var11, 3, var4, 0.2F + Math.min(0.6F, var8 * 2.0F), var6 % 1.0F, var10);
      this.finish(var11, var5);
   }

   private float along(float[] var1, float[] var2) {
      float[] var3 = norm(var2);
      float var4 = dot(var1, var3);
      return var4 - (float)Math.floor((double)var4);
   }

   private void put(TreeShape.Quad var1, int var2, float[] var3, float var4, float var5, float[] var6) {
      int var7 = var2 * 8;
      var1.v[var7] = var3[0];
      var1.v[var7 + 1] = var3[1];
      var1.v[var7 + 2] = var3[2];
      var1.v[var7 + 3] = clamp(var4, 0.0F, 1.0F);
      var1.v[var7 + 4] = var5;
      var1.v[var7 + 5] = var6[0];
      var1.v[var7 + 6] = var6[1];
      var1.v[var7 + 7] = var6[2];
   }

   private void finish(TreeShape.Quad var1, int var2) {
      float var3 = Float.MAX_VALUE;
      float var4 = -Float.MAX_VALUE;

      for (int var5 = 0; var5 < 4; var5++) {
         var3 = Math.min(var3, var1.v[var5 * 8 + 4]);
         var4 = Math.max(var4, var1.v[var5 * 8 + 4]);
      }

      float var21 = (float)Math.floor((double)var3);
      if (var4 - var21 > 1.0F) {
         var21 = var4 - 1.0F;
      }

      for (int var6 = 0; var6 < 4; var6++) {
         var1.v[var6 * 8 + 4] = clamp(var1.v[var6 * 8 + 4] - var21, 0.0F, 1.0F);
      }

      float[] var22 = new float[]{var1.v[0], var1.v[1], var1.v[2]};
      float[] var7 = new float[]{var1.v[8], var1.v[9], var1.v[10]};
      float[] var8 = new float[]{var1.v[24], var1.v[25], var1.v[26]};
      float[] var9 = cross(sub(var7, var22), sub(var8, var22));
      float var10 = (float)Math.sqrt((double)dot(var9, var9));
      if (!(var10 < 1.0E-7F)) {
         var9 = scale(var9, 1.0F / var10);
         float var11 = var1.v[5] + var1.v[13] + var1.v[21] + var1.v[29];
         float var12 = var1.v[6] + var1.v[14] + var1.v[22] + var1.v[30];
         float var13 = var1.v[7] + var1.v[15] + var1.v[23] + var1.v[31];
         if (var9[0] * var11 + var9[1] * var12 + var9[2] * var13 < 0.0F) {
            float[] var14 = new float[8];
            System.arraycopy(var1.v, 8, var14, 0, 8);
            System.arraycopy(var1.v, 24, var1.v, 8, 8);
            System.arraycopy(var14, 0, var1.v, 24, 8);
            var9 = scale(var9, -1.0F);
         }

         var1.nx = var9[0];
         var1.ny = var9[1];
         var1.nz = var9[2];
         var1.texture = var2;
         float var24 = (var1.v[0] + var1.v[8] + var1.v[16] + var1.v[24]) / 4.0F + var9[0] * 0.6F;
         float var15 = (var1.v[1] + var1.v[9] + var1.v[17] + var1.v[25]) / 4.0F + var9[1] * 0.6F;
         float var16 = (var1.v[2] + var1.v[10] + var1.v[18] + var1.v[26]) / 4.0F + var9[2] * 0.6F;
         int var17 = (int)Math.floor((double)var24);
         int var18 = (int)Math.floor((double)var15);
         int var19 = (int)Math.floor((double)var16);
         int var20 = this.p.kind(var17, var18, var19) != 1 && this.p.kind(var17, var18, var19) != 4 && this.p.kind(var17, var18, var19) != 3
            ? this.p.light(var17, var18, var19)
            : 0;
         if (var20 == 0) {
            var20 = Math.max(
               Math.max(this.p.light(0, 1, 0), this.p.light(1, 0, 0)),
               Math.max(this.p.light(-1, 0, 0), Math.max(this.p.light(0, 0, 1), this.p.light(0, 0, -1)))
            );
            var20 = Math.max(var20, this.p.light(var17, var18 + 1, var19));
         }

         var1.light = var20;
         this.out.add(var1);
      }
   }

   private static float clamp(float var0, float var1, float var2) {
      return var0 < var1 ? var1 : Math.min(var0, var2);
   }

   private static float[] sub(float[] var0, float[] var1) {
      return new float[]{var0[0] - var1[0], var0[1] - var1[1], var0[2] - var1[2]};
   }

   private static float[] add(float[] var0, float[] var1) {
      return new float[]{var0[0] + var1[0], var0[1] + var1[1], var0[2] + var1[2]};
   }

   private static float[] scale(float[] var0, float var1) {
      return new float[]{var0[0] * var1, var0[1] * var1, var0[2] * var1};
   }

   private static float dot(float[] var0, float[] var1) {
      return var0[0] * var1[0] + var0[1] * var1[1] + var0[2] * var1[2];
   }

   private static float[] cross(float[] var0, float[] var1) {
      return new float[]{var0[1] * var1[2] - var0[2] * var1[1], var0[2] * var1[0] - var0[0] * var1[2], var0[0] * var1[1] - var0[1] * var1[0]};
   }

   private static float[] norm(float[] var0) {
      float var1 = (float)Math.sqrt((double)dot(var0, var0));
      return var1 < 1.0E-9F ? new float[]{0.0F, 1.0F, 0.0F} : scale(var0, 1.0F / var1);
   }

   private static float dist(float[] var0, float[] var1) {
      float[] var2 = sub(var0, var1);
      return (float)Math.sqrt((double)dot(var2, var2));
   }

   private static float[] lerp(float[] var0, float[] var1, float var2) {
      return new float[]{var0[0] + (var1[0] - var0[0]) * var2, var0[1] + (var1[1] - var0[1]) * var2, var0[2] + (var1[2] - var0[2]) * var2};
   }

   static final class Blob {
      final int[][] cells;
      final float cx;
      final float cz;
      final int dx0;
      final int dz0;
      final boolean wall;

      Blob(int[][] var1, float var2, float var3, int var4, int var5, boolean var6) {
         this.cells = var1;
         this.cx = var2;
         this.cz = var3;
         this.dx0 = var4;
         this.dz0 = var5;
         this.wall = var6;
      }

      boolean contains(int var1, int var2) {
         for (int[] var6 : this.cells) {
            if (var6[0] == var1 && var6[1] == var2) {
               return true;
            }
         }

         return false;
      }
   }

   public interface Probe {
      int kind(int var1, int var2, int var3);

      int axis(int var1, int var2, int var3);

      int light(int var1, int var2, int var3);

      long seed();

      long hash(int var1, int var2, int var3);

      int worldY();

      default boolean conifer() {
         return false;
      }
   }

   public static final class Quad {
      public final float[] v = new float[32];
      public int texture;
      public int light;
      public float nx;
      public float ny;
      public float nz;
   }
}
