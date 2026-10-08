package com.formaworks.frontierhunts.hunting;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumMap;
import org.joml.Matrix4f;

public final class DeerAnatomyMesh {
   public static final String RESOURCE = GameSpecies.WHITETAIL.anatomyResource();
   private static final EnumMap<GameSpecies, DeerAnatomyMesh> INSTANCES = new EnumMap<>(GameSpecies.class);
   public final GameSpecies species;
   public final DeerAnatomy.Region[] partRegion;
   public final int parts;
   public final int vertices;
   public final DeerOrgan[] partOrgan;
   public final int[] partStart;
   public final int[] partCount;
   public final float[] pos;
   public final float[] nrm;
   public final int[] color;
   public final byte[] bones;
   public final byte[] weights;
   public final int[][][] lods = new int[3][][];

   public static DeerAnatomyMesh get() {
      return of(GameSpecies.WHITETAIL);
   }

   public static synchronized DeerAnatomyMesh of(GameSpecies var0) {
      DeerAnatomyMesh var1 = INSTANCES.get(var0);
      if (var1 == null) {
         String var2 = var0.anatomyResource();

         try {
            var1 = new DeerAnatomyMesh(var0, HuntResources.read(var2));
         } catch (IOException var4) {
            throw new IllegalStateException("Cannot load " + var0.id + " anatomy", var4);
         }

         INSTANCES.put(var0, var1);
      }

      return var1;
   }

   private DeerAnatomyMesh(GameSpecies var1, byte[] var2) throws IOException {
      this.species = var1;
      DataInputStream var3 = new DataInputStream(new ByteArrayInputStream(var2));
      String var4 = new String(var3.readNBytes(8), StandardCharsets.US_ASCII);
      boolean var5 = var4.equals("FHAN0003");
      if (!var5 && !var4.equals("FHAN0002")) {
         throw new IOException("Bad anatomy header");
      } else {
         int var6 = var3.readInt();
         DeerOrgan[] var7 = new DeerOrgan[var6];

         for (int var8 = 0; var8 < var6; var8++) {
            var7[var8] = DeerOrgan.valueOf(var3.readUTF());
         }

         this.parts = var3.readInt();
         if (this.parts >= 1 && this.parts <= 4096) {
            this.partOrgan = new DeerOrgan[this.parts];
            this.partStart = new int[this.parts];
            this.partCount = new int[this.parts];
            this.partRegion = new DeerAnatomy.Region[this.parts];
            DeerAnatomy.Region[] var24 = DeerAnatomy.Region.values();
            float[] var9 = new float[65536];
            float[] var10 = new float[65536];
            int[] var11 = new int[32768];
            byte[] var12 = new byte[131072];
            byte[] var13 = new byte[131072];

            for (int var14 = 0; var14 < 3; var14++) {
               this.lods[var14] = new int[this.parts][];
            }

            int var25 = 0;
            int var15 = 0;

            while (var15 < this.parts) {
               int var16 = var3.readInt();
               if (var16 >= 0 && var16 < var6) {
                  this.partOrgan[var15] = var7[var16];
                  if (var5) {
                     int var17 = var3.readUnsignedByte();
                     this.partRegion[var15] = var17 == 255 ? this.partOrgan[var15].region : (var17 < var24.length ? var24[var17] : null);
                     if (this.partRegion[var15] == null) {
                        throw new IOException("Region index");
                     }
                  }

                  int var29 = var3.readInt();
                  if (var29 >= 0 && var29 <= 200000) {
                     this.partStart[var15] = var25;
                     this.partCount[var15] = var29;
                     int var18 = var25 + var29;
                     if (var18 * 3 > var9.length) {
                        int var19 = Math.max(var9.length * 2, var18 * 3);
                        var9 = Arrays.copyOf(var9, var19);
                        var10 = Arrays.copyOf(var10, var19);
                     }

                     if (var18 > var11.length) {
                        var11 = Arrays.copyOf(var11, Math.max(var11.length * 2, var18));
                     }

                     if (var18 * 4 > var12.length) {
                        int var31 = Math.max(var12.length * 2, var18 * 4);
                        var12 = Arrays.copyOf(var12, var31);
                        var13 = Arrays.copyOf(var13, var31);
                     }

                     for (int var32 = 0; var32 < var29; var32++) {
                        int var20 = var25 + var32;

                        for (int var21 = 0; var21 < 3; var21++) {
                           var9[var20 * 3 + var21] = finite(var3.readFloat());
                        }

                        for (int var36 = 0; var36 < 3; var36++) {
                           var10[var20 * 3 + var36] = finite(var3.readFloat());
                        }

                        var11[var20] = var3.readUnsignedByte() << 16 | var3.readUnsignedByte() << 8 | var3.readUnsignedByte();

                        for (int var37 = 0; var37 < 4; var37++) {
                           var12[var20 * 4 + var37] = var3.readByte();
                        }

                        for (int var38 = 0; var38 < 4; var38++) {
                           var13[var20 * 4 + var38] = var3.readByte();
                        }
                     }

                     for (int var33 = 0; var33 < 3; var33++) {
                        int var35 = var3.readInt();
                        if (var35 < 0 || var35 > 400000) {
                           throw new IOException("Triangle budget");
                        }

                        int[] var39 = new int[var35 * 3];

                        for (int var22 = 0; var22 < var39.length; var22++) {
                           int var23 = var3.readInt();
                           if (var23 < 0 || var23 >= var29) {
                              throw new IOException("Index range");
                           }

                           var39[var22] = var25 + var23;
                        }

                        this.lods[var33][var15] = var39;
                     }

                     var25 = var18;
                     var15++;
                     continue;
                  }

                  throw new IOException("Vertex budget");
               }

               throw new IOException("Organ index");
            }

            this.vertices = var25;
            this.pos = Arrays.copyOf(var9, var25 * 3);
            this.nrm = Arrays.copyOf(var10, var25 * 3);
            this.color = Arrays.copyOf(var11, var25);
            this.bones = Arrays.copyOf(var12, var25 * 4);
            this.weights = Arrays.copyOf(var13, var25 * 4);
            var15 = DeerSkeleton.of(var1).count();

            for (int var27 = 0; var27 < var25 * 4; var27++) {
               if ((this.bones[var27] & 255) >= var15) {
                  throw new IOException("Bone index");
               }
            }

            if (!var5) {
               for (int var28 = 0; var28 < this.parts; var28++) {
                  double var30 = 0.0;
                  double var34 = 0.0;
                  int var40 = this.partCount[var28];

                  for (int var41 = this.partStart[var28]; var41 < this.partStart[var28] + var40; var41++) {
                     var30 += (double)this.pos[var41 * 3 + 1];
                     var34 += (double)this.pos[var41 * 3 + 2];
                  }

                  this.partRegion[var28] = this.partOrgan[var28]
                     .regionAt((float)(var30 / (double)Math.max(1, var40)), (float)(var34 / (double)Math.max(1, var40)));
               }
            }
         } else {
            throw new IOException("Anatomy part budget");
         }
      }
   }

   private static float finite(float var0) throws IOException {
      if (!Float.isFinite(var0)) {
         throw new IOException("Non-finite anatomy");
      } else {
         return var0;
      }
   }

   public void skin(Matrix4f[] var1, float[] var2, float[] var3) {
      for (int var4 = 0; var4 < this.vertices; var4++) {
         float var5 = this.pos[var4 * 3];
         float var6 = this.pos[var4 * 3 + 1];
         float var7 = this.pos[var4 * 3 + 2];
         float var8 = this.nrm[var4 * 3];
         float var9 = this.nrm[var4 * 3 + 1];
         float var10 = this.nrm[var4 * 3 + 2];
         float var11 = 0.0F;
         float var12 = 0.0F;
         float var13 = 0.0F;
         float var14 = 0.0F;
         float var15 = 0.0F;
         float var16 = 0.0F;

         for (int var17 = 0; var17 < 4; var17++) {
            int var18 = this.weights[var4 * 4 + var17] & 255;
            if (var18 != 0) {
               float var19 = (float)var18 / 255.0F;
               Matrix4f var20 = var1[this.bones[var4 * 4 + var17] & 255];
               var11 += var19 * (var20.m00() * var5 + var20.m10() * var6 + var20.m20() * var7 + var20.m30());
               var12 += var19 * (var20.m01() * var5 + var20.m11() * var6 + var20.m21() * var7 + var20.m31());
               var13 += var19 * (var20.m02() * var5 + var20.m12() * var6 + var20.m22() * var7 + var20.m32());
               if (var3 != null) {
                  var14 += var19 * (var20.m00() * var8 + var20.m10() * var9 + var20.m20() * var10);
                  var15 += var19 * (var20.m01() * var8 + var20.m11() * var9 + var20.m21() * var10);
                  var16 += var19 * (var20.m02() * var8 + var20.m12() * var9 + var20.m22() * var10);
               }
            }
         }

         var2[var4 * 3] = var11;
         var2[var4 * 3 + 1] = var12;
         var2[var4 * 3 + 2] = var13;
         if (var3 != null) {
            float var21 = (float)Math.sqrt((double)(var14 * var14 + var15 * var15 + var16 * var16));
            if (var21 < 1.0E-6F) {
               var21 = 1.0F;
            }

            var3[var4 * 3] = var14 / var21;
            var3[var4 * 3 + 1] = var15 / var21;
            var3[var4 * 3 + 2] = var16 / var21;
         }
      }
   }

   public double raycast(float[] var1, int var2, int var3, double var4, double var6, double var8, double var10, double var12, double var14) {
      int[] var16 = this.lods[var3][var2];
      double var17 = var10 - var4;
      double var19 = var12 - var6;
      double var21 = var14 - var8;
      double var23 = Double.POSITIVE_INFINITY;

      for (byte var25 = 0; var25 < var16.length; var25 += 3) {
         double var26 = triangle(var1, var16[var25], var16[var25 + 1], var16[var25 + 2], var4, var6, var8, var17, var19, var21);
         if (var26 < var23) {
            var23 = var26;
         }
      }

      return var23;
   }

   public int sides(float[] var1, int var2, int var3, double var4, double var6, double var8, double var10, double var12, double var14, double var16) {
      int[] var18 = this.lods[var3][var2];
      double var19 = 0.0;
      double var21 = Double.MAX_VALUE;
      double var23 = -Double.MAX_VALUE;

      for (int var25 = this.partStart[var2]; var25 < this.partStart[var2] + this.partCount[var2]; var25++) {
         double var26 = (double)this.pos[var25 * 3];
         var19 += var26;
         var21 = Math.min(var21, var26);
         var23 = Math.max(var23, var26);
      }

      var19 /= (double)Math.max(1, this.partCount[var2]);
      double var40 = (var23 - var21) * 0.06;
      double var27 = var10 - var4;
      double var29 = var12 - var6;
      double var31 = var14 - var8;
      byte var33 = 0;

      for (byte var34 = 0; var34 < var18.length; var34 += 3) {
         double var35 = triangle(var1, var18[var34], var18[var34 + 1], var18[var34 + 2], var4, var6, var8, var27, var29, var31);
         if (!(var35 > var16)) {
            double var37 = (double)((this.pos[var18[var34] * 3] + this.pos[var18[var34 + 1] * 3] + this.pos[var18[var34 + 2] * 3]) / 3.0F);
            if (var37 < var19 - var40) {
               var33 |= 1;
            } else if (var37 > var19 + var40) {
               var33 |= 2;
            }

            if (var33 == 3) {
               break;
            }
         }
      }

      return var33;
   }

   public static double triangle(float[] var0, int var1, int var2, int var3, double var4, double var6, double var8, double var10, double var12, double var14) {
      double var16 = (double)var0[var1 * 3];
      double var18 = (double)var0[var1 * 3 + 1];
      double var20 = (double)var0[var1 * 3 + 2];
      double var22 = (double)var0[var2 * 3] - var16;
      double var24 = (double)var0[var2 * 3 + 1] - var18;
      double var26 = (double)var0[var2 * 3 + 2] - var20;
      double var28 = (double)var0[var3 * 3] - var16;
      double var30 = (double)var0[var3 * 3 + 1] - var18;
      double var32 = (double)var0[var3 * 3 + 2] - var20;
      double var34 = var12 * var32 - var14 * var30;
      double var36 = var14 * var28 - var10 * var32;
      double var38 = var10 * var30 - var12 * var28;
      double var40 = var22 * var34 + var24 * var36 + var26 * var38;
      if (Math.abs(var40) < 1.0E-12) {
         return Double.POSITIVE_INFINITY;
      } else {
         double var42 = 1.0 / var40;
         double var44 = var4 - var16;
         double var46 = var6 - var18;
         double var48 = var8 - var20;
         double var50 = (var44 * var34 + var46 * var36 + var48 * var38) * var42;
         if (!(var50 < 0.0) && !(var50 > 1.0)) {
            double var52 = var46 * var26 - var48 * var24;
            double var54 = var48 * var22 - var44 * var26;
            double var56 = var44 * var24 - var46 * var22;
            double var58 = (var10 * var52 + var12 * var54 + var14 * var56) * var42;
            if (!(var58 < 0.0) && !(var50 + var58 > 1.0)) {
               double var60 = (var28 * var52 + var30 * var54 + var32 * var56) * var42;
               return var60 >= 0.0 && var60 <= 1.0 ? var60 : Double.POSITIVE_INFINITY;
            } else {
               return Double.POSITIVE_INFINITY;
            }
         } else {
            return Double.POSITIVE_INFINITY;
         }
      }
   }
}
