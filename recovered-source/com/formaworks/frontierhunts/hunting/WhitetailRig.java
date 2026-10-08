package com.formaworks.frontierhunts.hunting;

import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class WhitetailRig {
   public static final float CYCLE_DISTANCE = 0.96F;
   public static final int COLLAPSE_TICKS = 36;
   public final Matrix4f[] bones = new Matrix4f[WhitetailBindPose.POINTS.length];
   public final Vector3f[][] joints = new Vector3f[4][4];
   private final Vector3f a = new Vector3f();
   private final Vector3f b = new Vector3f();
   private final Vector3f c = new Vector3f();
   private final Vector3f d = new Vector3f();
   private final Vector3f walkTarget = new Vector3f();
   private final Vector3f runTarget = new Vector3f();
   private final Matrix4f legParent = new Matrix4f();
   private final Matrix4f inverseRoot = new Matrix4f();
   private final Matrix4f groundRoot = new Matrix4f();

   public WhitetailRig() {
      for (int var1 = 0; var1 < this.bones.length; var1++) {
         this.bones[var1] = new Matrix4f();
      }

      for (int var3 = 0; var3 < 4; var3++) {
         for (int var2 = 0; var2 < 4; var2++) {
            this.joints[var3][var2] = new Vector3f();
         }
      }
   }

   public void pose(float var1, float var2, float var3, float var4, float var5, float var6, float var7, float var8) {
      this.pose(var1, var2, var3, var4, var5, var6, var7, var8, 1000.0F, 0, 1.0F, 0.0F);
   }

   public void pose(
      float var1, float var2, float var3, float var4, float var5, float var6, float var7, float var8, float var9, int var10, float var11, float var12
   ) {
      this.pose(var1, var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, null);
   }

   public void pose(
      float var1,
      float var2,
      float var3,
      float var4,
      float var5,
      float var6,
      float var7,
      float var8,
      float var9,
      int var10,
      float var11,
      float var12,
      float[] var13
   ) {
      boolean var14 = var13 != null && var8 == 0.0F;
      var11 = var11 < 0.0F ? -1.0F : 1.0F;
      float var15 = smooth(0.0F, 0.12F, var8);
      float var16 = pulse(var9, 0.0F, 3.0F, 15.0F) * (1.0F - var15);
      float var17 = pulse(var9, 2.0F, 6.0F, 18.0F) * (float)(var10 == 2 ? 1 : 0) * (1.0F - var15);
      var4 *= 1.0F - Math.max(var15, var16);
      var3 = Mth.lerp(smooth(0.0F, 0.3F, var8), Math.max(var3, var12 * (1.0F - var15)), 0.0F);
      float var18 = Mth.clamp(var3 / 0.055F, 0.0F, 1.0F);
      float var19 = smooth(0.11F, 0.24F, var3);
      float var20 = var2 / 0.96F;
      float var21 = (float)Math.sin((double)var20 * Math.PI * 4.0) * 0.009F * var18 * (1.0F - var19);
      var21 += Math.max(0.0F, (float)Math.sin((double)var20 * Math.PI * 2.0)) * 0.032F * var19;
      var21 -= 0.045F * var18;
      float var22 = smooth(0.01F, 0.35F, var8);
      float var23 = smooth(0.08F, 0.43F, var8);
      float var24 = smooth(0.39F, 0.92F, var8);
      float var25 = pulse(var8, 0.36F, 0.45F, 0.72F);
      float var26 = var11 * (var23 * 1.49F + 0.035F * var25);
      float var27 = -0.16F * pulse(var8, 0.0F, 0.18F, 0.58F) - var17 * 0.13F;
      float var28 = Math.max(0.0F, var8 * 36.0F / 20.0F - 0.15F);
      float var29 = 0.055F * smooth(0.0F, 0.11F, var8) + 4.905F * var28 * var28;
      float var30 = Math.max(0.235F, 1.02F - var29);
      float var31 = (float)Math.sqrt(
         Math.pow(0.225F * Math.sin((double)var26), 2.0) + Math.pow(0.255F * Math.cos((double)var26), 2.0) + Math.pow(0.68F * Math.sin((double)var27), 2.0)
      );
      var30 = Math.max(var30, var31 + 0.006F);
      var21 *= 1.0F - var15;
      this.bones[0].identity().translate(-var11 * 0.11F * var23, var30 + var21, 0.04F).rotateZ(var26).rotateX(var27).translate(0.0F, -1.02F, -0.04F);
      float var32 = (float)Math.sin((double)(var1 * 0.082F)) * 0.0022F * (1.0F - var8);
      this.bones[0].translate(0.0F, var32, 0.0F);
      float var33 = 0.0F;
      if (var14) {
         float var34 = (var13[0] + var13[1]) * 0.5F;
         float var35 = (var13[2] + var13[3]) * 0.5F;
         var33 = Mth.clamp((float)Math.atan2((double)(var34 - var35), 0.933F), -0.5F, 0.5F);
         float var36 = (var34 + var35) * 0.5F + (float)Math.sin((double)var33) * 0.0395F;
         this.groundRoot.identity().translate(0.0F, var36, -((float)Math.sin((double)var33)) * 0.85F).rotateX(var33);
         this.bones[0].mulLocal(this.groundRoot);
         this.inverseRoot.set(this.bones[0]).invert();
      }

      float var67 = pulse(var8, 0.05F, 0.28F, 0.66F);
      hinge(
         this.bones[1],
         this.bones[0],
         1,
         -var4 * 1.23F - 0.17F * var67 - 0.18F * var22 + var16 * 0.12F - var33 * 0.6F,
         var6 * 0.32F * (1.0F - var15),
         -var11 * 0.18F * var23
      );
      hinge(
         this.bones[2],
         this.bones[1],
         2,
         -var4 * 0.17F + var7 * (1.0F - var4) * (1.0F - var15) - 0.18F * var67 + 0.08F * var23 - var33 * 0.4F,
         var6 * 0.68F * (1.0F - var15),
         -var11 * 0.4F * var23
      );

      for (int var68 = 0; var68 < 2; var68++) {
         float var71 = var68 == 0 ? -1.0F : 1.0F;
         float var37 = (float)Math.pow(Math.max(0.0, Math.sin((double)(var1 * 0.049F) + (double)var71 * 1.9)), 18.0) * 0.16F;
         hinge(
            this.bones[3 + var68],
            this.bones[2],
            3 + var68,
            0.0F,
            var71 * (0.08F * (float)Math.sin((double)var1 * 0.027 + (double)var71) + var37) * (1.0F - var8),
            var71 * 0.05F * var5
         );
      }

      hinge(
         this.bones[5],
         this.bones[0],
         5,
         -2.55F * smooth(0.4F, 0.85F, var5) * (1.0F - var8),
         0.0F,
         0.08F * (float)Math.sin((double)var1 * 0.16) * (1.0F - var8)
      );

      for (int var69 = 0; var69 < 4; var69++) {
         int var72 = 6 + var69 * 4;
         boolean var74 = var69 >= 2;
         float var38 = var69 % 2 == 0 ? -1.0F : 1.0F;
         float var39 = var38 < 0.0F ? 0.0F : 0.5F;
         var39 += var74 ? 0.75F : 0.0F;
         float var40 = fraction(var20 + var39);
         float var41 = fraction(var20 + (var74 ? 0.48F : 0.0F) + (var38 < 0.0F ? 0.0F : 0.08F));
         target(this.walkTarget, var40, 0.64F, 0.1F);
         target(this.runTarget, var41, 0.4F, 0.27F);
         float var42 = Mth.lerp(var19, this.walkTarget.z, this.runTarget.z);
         float var43 = Mth.lerp(var19, this.walkTarget.y, this.runTarget.y);
         float[] var44 = WhitetailBindPose.POINTS[var72];
         float[] var45 = WhitetailBindPose.POINTS[var72 + 1];
         float[] var46 = WhitetailBindPose.POINTS[var72 + 2];
         float[] var47 = WhitetailBindPose.POINTS[var72 + 3];
         this.a.set(var44);
         this.b.set(var45);
         this.c.set(var46);
         this.d.set(var47);
         float var48 = Mth.lerp(var19, (float)Math.sin((double)var40 * Math.PI * 2.0), (float)Math.sin((double)var41 * Math.PI * 2.0))
            * var18
            * (var74 ? 0.19F : 0.12F);
         boolean var49 = var38 == -var11;
         float var50 = smooth(var74 ? 0.12F : 0.005F, var74 ? 0.4F : 0.27F, var8);
         var48 = Mth.lerp(var50, var48, Mth.lerp(var24, var74 ? -0.48F : 0.45F, var74 ? -0.14F : 0.15F));
         var48 += var17 * (var74 ? -0.26F : 0.1F);
         this.d.y += var43 * var18 - var21 - var32;
         this.d.z += var42 * var18;
         this.d.y += var17 * (var74 ? 0.16F : 0.035F);
         this.d.z += var17 * (var74 ? 0.2F : -0.025F);
         if (var14) {
            this.d.y = this.d.y + var13[var69] + var21 + var32;
            this.inverseRoot.transformPosition(this.d);
         }

         float var51 = var74 ? (var49 ? 0.43F : 0.5F) : (var49 ? 0.48F : 0.55F);
         float var52 = var74 ? (var49 ? 0.31F : 0.17F) : (var49 ? -0.58F : -0.69F);
         this.d.y = Mth.lerp(var50, this.d.y, Mth.lerp(var24, var51, var74 ? 0.18F : 0.14F));
         this.d.z = Mth.lerp(var50, this.d.z, Mth.lerp(var24, var52, var74 ? (var49 ? 0.62F : 0.48F) : (var49 ? -0.59F : -0.71F)));
         if (var8 > 0.0F && var8 < 0.2F) {
            float var53 = smooth(0.11F, 0.2F, var8);
            this.d.y = Mth.lerp(var53, var47[1] + 1.02F - var30, this.d.y);
         }

         var48 = reachableAngle(this.a, this.b, this.d, var48, distance(var45, var46) + distance(var46, var47) - 0.003F);
         this.b.sub(this.a).rotateX(var48).add(this.a);
         solveKnee(this.b, this.c, this.d, distance(var45, var46), distance(var46, var47), var74 ? -1 : 1);
         this.joints[var69][0].set(this.a);
         this.joints[var69][1].set(this.b);
         this.joints[var69][2].set(this.c);
         this.joints[var69][3].set(this.d);
         float var90 = -var11 * 0.24F * var24 * (var49 ? 0.08F : 1.0F);
         this.legParent.set(this.bones[0]).translate(this.a).rotateZ(var90).translate(-this.a.x, -this.a.y, -this.a.z);

         for (int var54 = 0; var54 < 3; var54++) {
            Vector3f var55 = this.joints[var69][var54];
            Vector3f var56 = this.joints[var69][var54 + 1];
            float[] var57 = WhitetailBindPose.POINTS[var72 + var54];
            float[] var58 = WhitetailBindPose.POINTS[var72 + var54 + 1];
            float var59 = (float)(
               Math.atan2((double)(var56.z - var55.z), (double)(var56.y - var55.y)) - Math.atan2((double)(var58[2] - var57[2]), (double)(var58[1] - var57[1]))
            );
            this.bones[var72 + var54].set(this.legParent).translate(var55).rotateX(var59).translate(-var57[0], -var57[1], -var57[2]);
         }

         this.bones[var72 + 3]
            .set(this.legParent)
            .translate(this.d)
            .rotateX(-var43 * 0.8F + var50 * (var74 ? -0.2F : 0.17F))
            .translate(-var47[0], -var47[1], -var47[2]);
         if (var14) {
            this.legParent.transformPosition(this.a.set(this.d));
            this.bones[var72 + 3].identity().translate(this.a).rotateX(-var43 * 0.8F).translate(-var47[0], -var47[1], -var47[2]);
            this.a.set(var44);
         }

         for (int var91 = 1; var91 < 4; var91++) {
            this.joints[var69][var91].sub(this.a).rotateZ(var90).add(this.a);
         }
      }

      if (var8 > 0.0F) {
         this.inverseRoot.set(this.bones[0]).invert();

         for (int var70 = 0; var70 < 4; var70++) {
            float var73 = 0.0F;
            int var75 = 9 + var70 * 4;
            float[] var76 = WhitetailBindPose.POINTS[var75];

            for (int var78 = 0; var78 < 8; var78++) {
               float var81 = (var78 & 1) == 0 ? -0.04F : 0.04F;
               float var83 = (var78 & 2) == 0 ? 0.009F : 0.096F;
               float var85 = (var78 & 4) == 0 ? -0.055F : 0.037F;
               this.bones[var75].transformPosition(this.a.set(var76[0] + var81, var83, var76[2] + var85));
               var73 = Math.min(var73, this.a.y - 0.004F);
            }

            if (var73 < 0.0F) {
               for (int var79 = var75 - 3; var79 <= var75; var79++) {
                  this.bones[var79].m31(this.bones[var79].m31() - var73);
               }

               this.inverseRoot.transformDirection(this.a.set(0.0F, -var73, 0.0F));

               for (Vector3f var86 : this.joints[var70]) {
                  var86.add(this.a);
               }
            }
         }
      }
   }

   private static float pulse(float var0, float var1, float var2, float var3) {
      return smooth(var1, var2, var0) * (1.0F - smooth(var2, var3, var0));
   }

   private static void hinge(Matrix4f var0, Matrix4f var1, int var2, float var3, float var4, float var5) {
      float[] var6 = WhitetailBindPose.POINTS[var2];
      var0.set(var1).translate(var6[0], var6[1], var6[2]).rotateY(var4).rotateX(var3).rotateZ(var5).translate(-var6[0], -var6[1], -var6[2]);
   }

   private static float distance(float[] var0, float[] var1) {
      return (float)Math.hypot((double)(var1[1] - var0[1]), (double)(var1[2] - var0[2]));
   }

   private static float fraction(float var0) {
      return var0 - (float)Math.floor((double)var0);
   }

   private static void target(Vector3f var0, float var1, float var2, float var3) {
      float var4 = 0.96F * var2;
      if (var1 < var2) {
         var0.set(0.0F, 0.0F, (-0.5F + var1 / var2) * var4);
      } else {
         float var5 = (var1 - var2) / (1.0F - var2);
         var0.set(0.0F, (float)Math.sin(Math.PI * (double)var5) * var3, (0.5F - smooth(0.0F, 1.0F, var5)) * var4);
      }
   }

   private static float smooth(float var0, float var1, float var2) {
      var2 = Mth.clamp((var2 - var0) / (var1 - var0), 0.0F, 1.0F);
      return var2 * var2 * (3.0F - 2.0F * var2);
   }

   private static float reachableAngle(Vector3f var0, Vector3f var1, Vector3f var2, float var3, float var4) {
      float var5 = var1.y - var0.y;
      float var6 = var1.z - var0.z;
      float var7 = var2.y - var0.y;
      float var8 = var2.z - var0.z;
      float var9 = (float)Math.hypot((double)var5, (double)var6);
      float var10 = (float)Math.hypot((double)var7, (double)var8);
      float var11 = (float)Math.atan2((double)var8, (double)var7) - (float)Math.atan2((double)var6, (double)var5);
      var11 = (float)Math.atan2(Math.sin((double)var11), Math.cos((double)var11));
      float var12 = (float)Math.acos((double)Mth.clamp((var9 * var9 + var10 * var10 - var4 * var4) / (2.0F * var9 * Math.max(1.0E-4F, var10)), -1.0F, 1.0F));
      return Mth.clamp(var3, var11 - var12, var11 + var12);
   }

   private static void solveKnee(Vector3f var0, Vector3f var1, Vector3f var2, float var3, float var4, int var5) {
      float var6 = var2.y - var0.y;
      float var7 = var2.z - var0.z;
      float var8 = (float)Math.hypot((double)var6, (double)var7);
      float var9 = Mth.clamp(var8, Math.abs(var3 - var4) + 1.0E-4F, var3 + var4 - 1.0E-4F);
      float var10 = var6 / Math.max(var8, 1.0E-4F);
      float var11 = var7 / Math.max(var8, 1.0E-4F);
      var2.y = var0.y + var10 * var9;
      var2.z = var0.z + var11 * var9;
      float var12 = (var3 * var3 - var4 * var4 + var9 * var9) / (2.0F * var9);
      float var13 = (float)Math.sqrt((double)Math.max(0.0F, var3 * var3 - var12 * var12));
      var1.set(var0.x, var0.y + var10 * var12 - var11 * var13 * (float)var5, var0.z + var11 * var12 + var10 * var13 * (float)var5);
   }
}
