package com.formaworks.frontierhunts.hunting;

import java.io.IOException;
import java.util.Arrays;
import java.util.EnumMap;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class DeerMeshData {
   public static final int REGION_EAR = 2;
   public static final int REGION_TAIL = 4;
   public static final int REGION_CAP = 8;
   public static final int REGION_ANTLER = 16;
   public static final int REGION_FEMALE_CAP = 32;
   public static final int REGION_MALE_ONLY = 64;
   static final float WHITETAIL_WIDTH = 0.6863163F;
   static final float WHITETAIL_HEIGHT = 1.5862875F;
   static final float WHITETAIL_LENGTH = 1.8699081F;
   private static final EnumMap<GameSpecies, DeerMeshData> INSTANCES = new EnumMap<>(GameSpecies.class);
   public final int vertices;
   public final float[] pos;
   public final float[] nrm;
   public final float[] uv;
   public final float[] col;
   public final byte[] joints;
   public final float[] weights;
   public final byte[] region;
   public final int[][] lods;
   public final int[] lodMode;
   public final int[][] lodVertices;
   public final int[][] lodsMale;
   public final int[][] lodsFemale;
   public final int[] hitFaces;
   public final boolean hasMeshAntlers;
   public final Vector3f pedicleLeft = new Vector3f();
   public final Vector3f pedicleRight = new Vector3f();
   public final float[] neckWeight;
   public final float[] headWeight;
   public final Vector3f neckA = new Vector3f();
   public final Vector3f neckB = new Vector3f();
   public final Vector3f headCentre = new Vector3f();
   public final float widthScale;
   public final float heightScale;
   public final float lengthScale;
   public final float halfLength;
   public final float halfWidth;
   public final float height;
   public final float[] cut;
   private static final ThreadLocal<float[]> BONES = ThreadLocal.withInitial(() -> new float[768]);
   private int[][] localMale;
   private int[][] localFemale;

   public static DeerMeshData get() {
      return of(GameSpecies.WHITETAIL);
   }

   public static synchronized DeerMeshData of(GameSpecies var0) {
      DeerMeshData var1 = INSTANCES.get(var0);
      if (var1 == null) {
         try {
            var1 = new DeerMeshData(DeerSkeleton.resourceBytes(var0), DeerSkeleton.of(var0));
         } catch (IOException var3) {
            throw new IllegalStateException("Cannot load " + var0.id + " mesh", var3);
         }

         INSTANCES.put(var0, var1);
      }

      return var1;
   }

   DeerMeshData(byte[] var1, DeerSkeleton var2) throws IOException {
      DeerSkeleton.CountingInput var3 = new DeerSkeleton.CountingInput(var1);
      var3.data.skipNBytes((long)var2.meshOffset);
      this.vertices = var3.data.readInt();
      this.pos = new float[this.vertices * 3];
      this.nrm = new float[this.vertices * 3];
      this.uv = new float[this.vertices * 2];
      this.col = new float[this.vertices * 3];
      this.joints = new byte[this.vertices * 4];
      this.weights = new float[this.vertices * 4];
      this.region = new byte[this.vertices];
      int var4 = var2.count();

      for (int var5 = 0; var5 < this.vertices; var5++) {
         for (int var6 = 0; var6 < 3; var6++) {
            this.pos[var5 * 3 + var6] = var3.data.readFloat();
         }

         for (int var30 = 0; var30 < 3; var30++) {
            this.nrm[var5 * 3 + var30] = var3.data.readFloat();
         }

         for (int var31 = 0; var31 < 2; var31++) {
            this.uv[var5 * 2 + var31] = var3.data.readFloat();
         }

         for (int var32 = 0; var32 < 3; var32++) {
            this.col[var5 * 3 + var32] = var3.data.readFloat();
         }

         int var33 = 0;

         for (int var7 = 0; var7 < 4; var7++) {
            int var8 = var3.data.readUnsignedByte();
            if (var8 >= var4) {
               throw new IOException("Bad joint index");
            }

            this.joints[var5 * 4 + var7] = (byte)var8;
         }

         for (int var36 = 0; var36 < 4; var36++) {
            int var42 = var3.data.readUnsignedByte();
            var33 += var42;
            this.weights[var5 * 4 + var36] = (float)var42 / 255.0F;
         }

         if (var33 != 255) {
            throw new IOException("Unnormalised weights");
         }

         this.region[var5] = var3.data.readByte();
      }

      int var29 = var3.data.readInt();
      this.lods = new int[var29][];
      this.lodMode = new int[var29];
      this.lodVertices = new int[var29][];

      for (int var34 = 0; var34 < var29; var34++) {
         this.lodMode[var34] = var3.data.readInt();
         int var37 = var3.data.readInt();
         int[] var43 = new int[var37 * 3];
         boolean[] var9 = new boolean[this.vertices];

         for (int var10 = 0; var10 < var43.length; var10++) {
            var43[var10] = var3.data.readInt();
            if (var43[var10] < 0 || var43[var10] >= this.vertices) {
               throw new IOException("Bad face");
            }

            var9[var43[var10]] = true;
         }

         this.lods[var34] = var43;
         int var48 = 0;

         for (boolean var14 : var9) {
            if (var14) {
               var48++;
            }
         }

         int[] var51 = new int[var48];
         var48 = 0;

         for (int var53 = 0; var53 < this.vertices; var53++) {
            if (var9[var53]) {
               var51[var48++] = var53;
            }
         }

         this.lodVertices[var34] = var51;
      }

      this.lodsMale = new int[var29][];
      this.lodsFemale = new int[var29][];
      boolean var35 = false;

      for (int var38 = 0; var38 < this.vertices; var38++) {
         if ((this.region[var38] & 48) != 0) {
            var35 = true;
            break;
         }
      }

      this.hasMeshAntlers = var35;

      for (int var39 = 0; var39 < var29; var39++) {
         if (!var35) {
            this.lodsMale[var39] = this.lods[var39];
            this.lodsFemale[var39] = this.lods[var39];
         } else {
            this.lodsMale[var39] = this.filter(this.lods[var39], 32);
            this.lodsFemale[var39] = this.filter(this.lods[var39], 80);
         }
      }

      this.hitFaces = var35 ? this.filter(this.lods[Math.min(2, var29 - 1)], 80) : this.lods[Math.min(2, var29 - 1)];
      Vector3f var40 = new Matrix4f(var2.inverseBind[var2.head]).invert().getTranslation(new Vector3f());
      Vector3f var44 = new Vector3f(var40).add(var2.antlerFrame.getTranslation(new Vector3f()));
      float var46 = var2.pedicleHalfSpacing;
      this.pedicleLeft.set(var44.x - var46, var44.y, var44.z);
      this.pedicleRight.set(var44.x + var46, var44.y, var44.z);
      var40 = new Matrix4f(var2.inverseBind[var2.neck0]).invert().getTranslation(new Vector3f());
      var44 = new Matrix4f(var2.inverseBind[var2.head]).invert().getTranslation(new Vector3f());
      this.neckA.set(var40);
      this.neckB.set(var44);
      this.headCentre.set(var44);
      this.neckWeight = new float[this.vertices];
      this.headWeight = new float[this.vertices];
      this.cut = new float[this.vertices];
      var46 = Float.MAX_VALUE;
      float var50 = -Float.MAX_VALUE;
      float var52 = Float.MAX_VALUE;
      float var54 = -Float.MAX_VALUE;
      float var55 = Float.MAX_VALUE;
      float var56 = -Float.MAX_VALUE;

      for (int var15 = 0; var15 < this.vertices; var15++) {
         if ((this.region[var15] & 16) == 0) {
            var46 = Math.min(var46, this.pos[var15 * 3]);
            var50 = Math.max(var50, this.pos[var15 * 3]);
            var52 = Math.min(var52, this.pos[var15 * 3 + 1]);
            var54 = Math.max(var54, this.pos[var15 * 3 + 1]);
            var55 = Math.min(var55, this.pos[var15 * 3 + 2]);
            var56 = Math.max(var56, this.pos[var15 * 3 + 2]);
         }
      }

      float var57 = (var50 - var46) / 0.6863163F;
      float var16 = (var54 - var52) / 1.5862875F;
      float var17 = (var56 - var55) / 1.8699081F;
      float var18 = var56 - 0.02332F * var17;
      float var19 = var52 + 0.31593F * var16;
      this.widthScale = var57;
      this.heightScale = var16;
      this.lengthScale = var17;
      this.halfLength = Math.max(Math.abs(var55), Math.abs(var56));
      this.halfWidth = Math.max(Math.abs(var46), Math.abs(var50));
      this.height = var54;

      for (int var20 = 0; var20 < this.vertices; var20++) {
         float var21 = 0.0F;
         float var22 = 0.0F;

         for (int var23 = 0; var23 < 4; var23++) {
            int var24 = this.joints[var20 * 4 + var23] & 255;
            if (var2.isNeck(var24)) {
               var21 += this.weights[var20 * 4 + var23];
            }

            if (var2.isHead(var24)) {
               var22 += this.weights[var20 * 4 + var23];
            }
         }

         this.neckWeight[var20] = var21;
         this.headWeight[var20] = var22;
         float var58 = this.pos[var20 * 3];
         float var59 = this.pos[var20 * 3 + 1];
         float var25 = this.pos[var20 * 3 + 2];
         float var26 = clamp((var18 - var25) / (1.25F * var17), 0.0F, 1.0F);
         float var27 = clamp((var59 - var19) / (0.75F * var16), 0.0F, 1.0F);
         float var28 = 0.035F + var26 * 0.68F + var27 * 0.2F + (1.0F - Math.min(1.0F, Math.abs(var58) / (0.26F * var57))) * 0.07F;
         this.cut[var20] = var21 + var22 > 0.42F ? 1.08F : Math.min(0.97F, var28);
      }
   }

   private static float clamp(float var0, float var1, float var2) {
      return var0 < var1 ? var1 : (var0 > var2 ? var2 : var0);
   }

   private int[] filter(int[] var1, int var2) {
      int var3 = 0;
      int[] var4 = new int[var1.length];

      for (byte var5 = 0; var5 + 2 < var1.length; var5 += 3) {
         if (((this.region[var1[var5]] | this.region[var1[var5 + 1]] | this.region[var1[var5 + 2]]) & var2) == 0) {
            var4[var3++] = var1[var5];
            var4[var3++] = var1[var5 + 1];
            var4[var3++] = var1[var5 + 2];
         }
      }

      return Arrays.copyOf(var4, var3);
   }

   public int[] faces(int var1, boolean var2) {
      return var2 ? this.lodsMale[var1] : this.lodsFemale[var1];
   }

   public void skin(int var1, Matrix4f[] var2, float var3, float var4, float[] var5, float[] var6) {
      this.skin(var1, var2, var3, var4, 1.0F, var5, var6);
   }

   public void skin(int var1, Matrix4f[] var2, float var3, float var4, float var5, float[] var6, float[] var7) {
      this.skinInto(var1, var2, var3, var4, var5, var6, var7, false);
   }

   public void skinCompact(int var1, Matrix4f[] var2, float var3, float var4, float var5, float[] var6, float[] var7) {
      this.skinInto(var1, var2, var3, var4, var5, var6, var7, true);
   }

   private void skinInto(int var1, Matrix4f[] var2, float var3, float var4, float var5, float[] var6, float[] var7, boolean var8) {
      int[] var9 = this.lodVertices[var1];
      float[] var10 = BONES.get();
      int var11 = Math.min(var2.length, 64);

      for (int var12 = 0; var12 < var11; var12++) {
         Matrix4f var13 = var2[var12];
         int var14 = var12 * 12;
         var10[var14] = var13.m00();
         var10[var14 + 1] = var13.m01();
         var10[var14 + 2] = var13.m02();
         var10[var14 + 3] = var13.m10();
         var10[var14 + 4] = var13.m11();
         var10[var14 + 5] = var13.m12();
         var10[var14 + 6] = var13.m20();
         var10[var14 + 7] = var13.m21();
         var10[var14 + 8] = var13.m22();
         var10[var14 + 9] = var13.m30();
         var10[var14 + 10] = var13.m31();
         var10[var14 + 11] = var13.m32();
      }

      boolean var58 = this.hasMeshAntlers && var5 > 0.0F && var5 != 1.0F;
      boolean var59 = var3 != 1.0F;
      boolean var60 = var4 != 1.0F;
      float var15 = this.neckA.x;
      float var16 = this.neckA.y;
      float var17 = this.neckA.z;
      float var18 = this.neckB.x - var15;
      float var19 = this.neckB.y - var16;
      float var20 = this.neckB.z - var17;
      float var21 = var18 * var18 + var19 * var19 + var20 * var20;
      float var22 = (this.pedicleLeft.x + this.pedicleRight.x) * 0.5F;
      float[] var23 = this.pos;
      float[] var24 = this.nrm;
      float[] var25 = this.weights;
      byte[] var26 = this.joints;
      byte[] var27 = this.region;
      float[] var28 = this.neckWeight;
      float[] var29 = this.headWeight;

      for (int var30 = 0; var30 < var9.length; var30++) {
         int var31 = var9[var30];
         int var32 = var31 * 3;
         float var33 = var23[var32];
         float var34 = var23[var32 + 1];
         float var35 = var23[var32 + 2];
         if (var58 && (var27[var31] & 16) != 0) {
            Vector3f var36 = var33 < var22 ? this.pedicleLeft : this.pedicleRight;
            var33 = var36.x + (var33 - var36.x) * var5;
            var34 = var36.y + (var34 - var36.y) * var5;
            var35 = var36.z + (var35 - var36.z) * var5;
         }

         if (var59) {
            float var61 = var28[var31];
            if (var61 > 0.0F) {
               float var37 = ((var33 - var15) * var18 + (var34 - var16) * var19 + (var35 - var17) * var20) / var21;
               var37 = var37 < 0.0F ? 0.0F : (var37 > 1.0F ? 1.0F : var37);
               float var38 = var15 + var18 * var37;
               float var39 = var16 + var19 * var37;
               float var40 = var17 + var20 * var37;
               float var41 = 1.0F + var61 * (var3 - 1.0F);
               var33 = var38 + (var33 - var38) * var41;
               var34 = var39 + (var34 - var39) * var41;
               var35 = var40 + (var35 - var40) * var41;
            }
         }

         if (var60) {
            float var62 = var29[var31];
            if (var62 > 0.0F) {
               float var65 = 1.0F + var62 * (var4 - 1.0F);
               var33 = this.headCentre.x + (var33 - this.headCentre.x) * var65;
               var34 = this.headCentre.y + (var34 - this.headCentre.y) * var65;
               var35 = this.headCentre.z + (var35 - this.headCentre.z) * var65;
            }
         }

         float var63 = 0.0F;
         float var66 = 0.0F;
         float var67 = 0.0F;
         float var68 = 0.0F;
         float var69 = 0.0F;
         float var70 = 0.0F;
         float var42 = var24[var32];
         float var43 = var24[var32 + 1];
         float var44 = var24[var32 + 2];
         int var45 = var31 * 4;

         for (int var46 = 0; var46 < 4; var46++) {
            float var47 = var25[var45 + var46];
            if (var47 != 0.0F) {
               int var48 = (var26[var45 + var46] & 255) * 12;
               float var49 = var10[var48];
               float var50 = var10[var48 + 1];
               float var51 = var10[var48 + 2];
               float var52 = var10[var48 + 3];
               float var53 = var10[var48 + 4];
               float var54 = var10[var48 + 5];
               float var55 = var10[var48 + 6];
               float var56 = var10[var48 + 7];
               float var57 = var10[var48 + 8];
               var63 += var47 * (var49 * var33 + var52 * var34 + var55 * var35 + var10[var48 + 9]);
               var66 += var47 * (var50 * var33 + var53 * var34 + var56 * var35 + var10[var48 + 10]);
               var67 += var47 * (var51 * var33 + var54 * var34 + var57 * var35 + var10[var48 + 11]);
               var68 += var47 * (var49 * var42 + var52 * var43 + var55 * var44);
               var69 += var47 * (var50 * var42 + var53 * var43 + var56 * var44);
               var70 += var47 * (var51 * var42 + var54 * var43 + var57 * var44);
            }
         }

         int var71 = (var8 ? var30 : var31) * 3;
         var6[var71] = var63;
         var6[var71 + 1] = var66;
         var6[var71 + 2] = var67;
         if (var7 != null) {
            float var72 = (float)Math.sqrt((double)(var68 * var68 + var69 * var69 + var70 * var70));
            if (var72 < 1.0E-6F) {
               var68 = 0.0F;
               var69 = 1.0F;
               var70 = 0.0F;
               var72 = 1.0F;
            }

            float var73 = 1.0F / var72;
            var7[var71] = var68 * var73;
            var7[var71 + 1] = var69 * var73;
            var7[var71 + 2] = var70 * var73;
         }
      }
   }

   public synchronized int[] localFaces(int var1, boolean var2) {
      if (this.localMale == null) {
         this.localMale = new int[this.lods.length][];
         this.localFemale = new int[this.lods.length][];
      }

      int[][] var3 = var2 ? this.localMale : this.localFemale;
      if (var3[var1] == null) {
         int[] var4 = new int[this.vertices];
         int[] var5 = this.lodVertices[var1];
         int var6 = 0;

         while (var6 < var5.length) {
            var4[var5[var6]] = var6++;
         }

         int[] var9 = this.faces(var1, var2);
         int[] var7 = new int[var9.length];

         for (int var8 = 0; var8 < var9.length; var8++) {
            var7[var8] = var4[var9[var8]];
         }

         var3[var1] = var7;
      }

      return var3[var1];
   }
}
