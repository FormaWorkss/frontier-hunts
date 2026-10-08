package com.formaworks.frontierhunts.hunting;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class DeerAnatomy {
   static final int HIT_LOD = 2;
   public static final double QUERY_MARGIN = 4.0;
   static final double PENETRATION = 0.62;

   public static Vec3 local(Vec3 var0, Vec3 var1, float var2) {
      Vec3 var3 = var0.subtract(var1);
      double var4 = Math.toRadians((double)(180.0F - var2));
      double var6 = Math.cos(var4);
      double var8 = Math.sin(var4);
      return new Vec3(var6 * var3.x - var8 * var3.z, var3.y, var8 * var3.x + var6 * var3.z);
   }

   public static DeerAnatomy.Contact intersect(Vec3 var0, Vec3 var1, Whitetail var2) {
      DeerTraits var3 = var2.traits();
      DeerMeshData var4 = DeerMeshData.of(var2.species());
      double var5 = Math.max(0.45, (double)(var4.halfLength * var3.frameLength()) - (double)var2.getBbWidth() * 0.5 + 0.25);
      double var7 = Math.max(0.45, (double)(var4.height * var3.frameHeight() - var2.getBbHeight()) + 0.2);
      AABB var9 = var2.getBoundingBox().inflate(var5, var7, var5);
      if (var9.clip(var0, var1).isEmpty() && !var9.contains(var0)) {
         return null;
      } else {
         float var10 = var3.frameWidth();
         float var11 = var3.frameHeight();
         float var12 = var3.frameLength();
         Vec3 var13 = local(var0, var2.position(), var2.yBodyRot);
         Vec3 var14 = local(var1, var2.position(), var2.yBodyRot);
         double var15 = var13.x / (double)var10;
         double var17 = var13.y / (double)var11;
         double var19 = var13.z / (double)var12;
         double var21 = var14.x / (double)var10;
         double var23 = var14.y / (double)var11;
         double var25 = var14.z / (double)var12;
         return intersectLocal(var2.species(), var15, var17, var19, var21, var23, var25, var2.hitSurface(), var2.hitOrgans(), var2.bodyPose().skin);
      }
   }

   public static DeerAnatomy.Contact intersectLocal(
      GameSpecies var0, double var1, double var3, double var5, double var7, double var9, double var11, float[] var13, float[] var14, Matrix4f[] var15
   ) {
      DeerMeshData var16 = DeerMeshData.of(var0);
      int[] var17 = var16.hitFaces;
      double var18 = Double.POSITIVE_INFINITY;
      byte var20 = -1;
      double var21 = var7 - var1;
      double var23 = var9 - var3;
      double var25 = var11 - var5;

      for (byte var27 = 0; var27 < var17.length; var27 += 3) {
         double var28 = DeerAnatomyMesh.triangle(var13, var17[var27], var17[var27 + 1], var17[var27 + 2], var1, var3, var5, var21, var23, var25);
         if (var28 < var18) {
            var18 = var28;
            var20 = var27;
         }
      }

      if (var20 < 0) {
         return null;
      } else {
         int var44 = dominant(var16, var17[var20]);
         DeerSkeleton var45 = DeerSkeleton.of(var0);
         DeerAnatomy.Region var29 = var45.isHead(var44)
            ? DeerAnatomy.Region.HEAD
            : (var45.isNeck(var44) ? DeerAnatomy.Region.NECK : (isLimb(var45, var44) ? DeerAnatomy.Region.LEG : DeerAnatomy.Region.BODY));
         if (var29 == DeerAnatomy.Region.NECK) {
            var29 = DeerAnatomy.Region.BODY;
         }

         DeerAnatomyMesh var30 = DeerAnatomyMesh.of(var0);
         double var31 = Math.sqrt(var21 * var21 + var23 * var23 + var25 * var25);
         double var33 = Math.min(1.0, var18 + 0.62 * (double)var16.widthScale / Math.max(1.0E-6, var31));
         DeerOrgan var35 = null;

         for (int var36 = 0; var36 < var30.parts; var36++) {
            DeerOrgan var37 = var30.partOrgan[var36];
            if (var37 != DeerOrgan.DIAPHRAGM && var37 != DeerOrgan.RIBS && var37 != DeerOrgan.ESOPHAGUS) {
               DeerAnatomy.Region var38 = var30.partRegion[var36];
               int var39 = var38.severity();
               if ((var35 != null ? var39 > var29.severity() : var39 >= var29.severity())
                  && var30.raycast(var14, var36, 1, var1, var3, var5, var7, var9, var11) <= var33) {
                  var29 = var38;
                  var35 = var37;
               }
            }
         }

         if (var35 == DeerOrgan.LUNGS && var29 == DeerAnatomy.Region.LUNG) {
            for (int var46 = 0; var46 < var30.parts; var46++) {
               if (var30.partOrgan[var46] == DeerOrgan.LUNGS && var30.sides(var14, var46, 1, var1, var3, var5, var7, var9, var11, var33) == 3) {
                  var29 = DeerAnatomy.Region.DOUBLE_LUNG;
                  break;
               }
            }
         }

         double var47 = var1 + var21 * var18;
         double var48 = var3 + var23 * var18;
         double var40 = var5 + var25 * var18;
         Matrix4f var42 = new Matrix4f(var15[var44]).invert();
         Vector3f var43 = var42.transformPosition(new Vector3f((float)var47, (float)var48, (float)var40));
         return new DeerAnatomy.Contact(var18, var29, var35, var44, new Vec3((double)var43.x, (double)var43.y, (double)var43.z));
      }
   }

   private static int dominant(DeerMeshData var0, int var1) {
      int var2 = 0;
      float var3 = -1.0F;

      for (int var4 = 0; var4 < 4; var4++) {
         if (var0.weights[var1 * 4 + var4] > var3) {
            var3 = var0.weights[var1 * 4 + var4];
            var2 = var0.joints[var1 * 4 + var4] & 255;
         }
      }

      return var2;
   }

   private static boolean isLimb(DeerSkeleton var0, int var1) {
      for (int var2 = 0; var2 < 4; var2++) {
         int[] var3 = var0.legs[var2];

         for (int var4 = var2 < 2 ? 1 : 1; var4 < var3.length; var4++) {
            if (var3[var4] == var1) {
               return true;
            }
         }
      }

      return false;
   }

   public static Vec3 world(Whitetail var0, int var1, Vec3 var2) {
      DeerTraits var3 = var0.traits();
      Vector3f var4 = new Vector3f((float)var2.x, (float)var2.y, (float)var2.z);
      var0.bodyPose().skin[Math.clamp((long)var1, 0, DeerSkeleton.of(var0.species()).count() - 1)].transformPosition(var4);
      double var5 = Math.toRadians((double)(180.0F - var0.yBodyRot));
      double var7 = Math.cos(var5);
      double var9 = Math.sin(var5);
      double var11 = (double)(var4.x * var3.frameWidth());
      double var13 = (double)(var4.z * var3.frameLength());
      return var0.position().add(var7 * var11 + var9 * var13, (double)(var4.y * var3.frameHeight()), -var9 * var11 + var7 * var13);
   }

   public static Vec3 exit(Whitetail var0, Vec3 var1, Vec3 var2, double var3) {
      if (var2.lengthSqr() < 1.0E-8) {
         return null;
      } else {
         Vec3 var5 = var2.normalize();
         Vec3 var6 = var1.add(var5.scale(Math.clamp(var3, 0.25, 5.0)));
         DeerAnatomy.Contact var7 = intersect(var6, var1.add(var5.scale(0.012)), var0);
         if (var7 == null) {
            return null;
         } else {
            Vec3 var8 = var6.lerp(var1.add(var5.scale(0.012)), var7.fraction());
            return var8.distanceToSqr(var1) > 0.0016 ? var8 : null;
         }
      }
   }

   private DeerAnatomy() {
   }

   public static record Contact(double fraction, DeerAnatomy.Region region, DeerOrgan organ, int bone, Vec3 point) {
      public Contact(double var1, DeerAnatomy.Region var3) {
         this(var1, var3, null, -1, null);
      }
   }

   public static enum Region {
      CHEST,
      BODY,
      NECK,
      HEAD,
      LEG,
      HEART,
      LUNG,
      SPINE,
      BRAIN,
      LIVER,
      GUT,
      SHOULDER,
      DOUBLE_LUNG;

      public boolean vital() {
         return this == HEART || this == DOUBLE_LUNG;
      }

      public boolean chest() {
         return this == HEART || this == DOUBLE_LUNG || this == LUNG || this == CHEST;
      }

      public int severity() {
         return switch (this) {
            case CHEST, LUNG -> 7;
            case BODY -> 0;
            case NECK -> 5;
            case HEAD -> 2;
            case LEG -> 1;
            case HEART, DOUBLE_LUNG -> 8;
            case SPINE -> 9;
            case BRAIN -> 10;
            case LIVER -> 4;
            case GUT -> 3;
            case SHOULDER -> 2;
         };
      }
   }
}
