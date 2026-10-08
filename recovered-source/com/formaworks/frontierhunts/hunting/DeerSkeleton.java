package com.formaworks.frontierhunts.hunting;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class DeerSkeleton {
   public static final String RESOURCE = GameSpecies.WHITETAIL.skeletonResource();
   private static final EnumMap<GameSpecies, DeerSkeleton> INSTANCES = new EnumMap<>(GameSpecies.class);
   public final GameSpecies species;
   public final String[] names;
   public final int[] parents;
   public final float[] restT;
   public final float[] restQ;
   public final Matrix4f[] inverseBind;
   public final Matrix4f antlerFrame;
   public final float pedicleHalfSpacing;
   public final Map<String, DeerSkeleton.Clip> clips = new HashMap<>();
   final int meshOffset;
   public final int head;
   public final int neck0;
   public final int neck1;
   public final int body;
   public final int bodyTop0;
   public final int bodyTop1;
   public final int bodyBot;
   public final int earL;
   public final int earR;
   public final int tail;
   public final int neck2;
   public final int jaw;
   public final int[][] legs;
   public final float legLength;
   public static final float WHITETAIL_LEG = 0.5225816F;
   public final int[] order;

   public static DeerSkeleton get() {
      return of(GameSpecies.WHITETAIL);
   }

   public static synchronized DeerSkeleton of(GameSpecies var0) {
      DeerSkeleton var1 = INSTANCES.get(var0);
      if (var1 == null) {
         try {
            var1 = new DeerSkeleton(var0, resourceBytes(var0));
         } catch (IOException var3) {
            throw new IllegalStateException("Cannot load " + var0.id + " skeleton", var3);
         }

         INSTANCES.put(var0, var1);
      }

      return var1;
   }

   public static byte[] resourceBytes(GameSpecies var0) throws IOException {
      return HuntResources.read(var0.skeletonResource());
   }

   DeerSkeleton(GameSpecies var1, byte[] var2) throws IOException {
      this.species = var1;
      DeerSkeleton.CountingInput var3 = new DeerSkeleton.CountingInput(var2);
      if (!new String(var3.data.readNBytes(8), StandardCharsets.US_ASCII).equals("FHSK0001")) {
         throw new IOException("Bad " + var1.id + " skeleton header");
      } else {
         int var4 = var3.data.readInt();
         if (var4 >= 8 && var4 <= 64) {
            this.names = new String[var4];
            this.parents = new int[var4];
            this.restT = new float[var4 * 3];
            this.restQ = new float[var4 * 4];
            this.inverseBind = new Matrix4f[var4];

            for (int var5 = 0; var5 < var4; var5++) {
               this.names[var5] = var3.data.readUTF();
               this.parents[var5] = var3.data.readInt();
               if (this.parents[var5] >= var5 && this.parents[var5] != -1) {
               }

               for (int var6 = 0; var6 < 3; var6++) {
                  this.restT[var5 * 3 + var6] = finite(var3.data.readFloat());
               }

               for (int var17 = 0; var17 < 4; var17++) {
                  this.restQ[var5 * 4 + var17] = finite(var3.data.readFloat());
               }

               float[] var18 = new float[16];

               for (int var7 = 0; var7 < 16; var7++) {
                  var18[var7] = finite(var3.data.readFloat());
               }

               this.inverseBind[var5] = new Matrix4f().set(var18);
            }

            float[] var16 = new float[16];

            for (int var19 = 0; var19 < 16; var19++) {
               var16[var19] = finite(var3.data.readFloat());
            }

            this.antlerFrame = new Matrix4f().set(var16);
            this.pedicleHalfSpacing = finite(var3.data.readFloat());
            this.meshOffset = var3.position();
            int var20 = var3.data.readInt();
            if (var20 >= 1 && var20 <= 100000) {
               var3.data.skipNBytes((long)(var20 * 11) * 4L + (long)var20 * 9L);
               int var21 = var3.data.readInt();

               for (int var8 = 0; var8 < var21; var8++) {
                  var3.data.readInt();
                  int var9 = var3.data.readInt();
                  var3.data.skipNBytes((long)var9 * 12L);
               }

               int var22 = var3.data.readInt();

               for (int var23 = 0; var23 < var22; var23++) {
                  String var26 = var3.data.readUTF();
                  float var28 = var3.data.readFloat();
                  boolean var30 = var3.data.readUnsignedByte() != 0;
                  int var32 = var3.data.readInt();
                  if (var32 < 1 || var32 > 1000 || !(var28 > 0.0F)) {
                     throw new IOException("Clip budget " + var26);
                  }

                  float[] var33 = new float[var32 * var4 * 7];

                  for (int var15 = 0; var15 < var33.length; var15++) {
                     var33[var15] = finite(var3.data.readFloat());
                  }

                  this.clips.put(var26, new DeerSkeleton.Clip(var26, var28, var30, var32, var33));
               }

               if (var3.data.read() != -1) {
                  throw new IOException("Trailing skeleton data");
               } else {
                  this.head = this.index("head0");
                  this.neck0 = this.index("neck0");
                  this.neck1 = this.index("neck1");
                  this.body = this.index("body");
                  this.bodyTop0 = this.index("body_top0");
                  this.bodyTop1 = this.index("body_top1");
                  this.bodyBot = this.index("body_bot");
                  this.earL = this.index("ear_l");
                  this.earR = this.index("ear_r");
                  this.tail = this.index("tail");
                  this.neck2 = this.find("neck2");
                  this.jaw = this.find("jaw");
                  String[] var24 = new String[]{"leg_front_left_", "leg_front_right_", "leg_hind_left_", "leg_hind_right_"};
                  this.legs = new int[4][];

                  for (int var10 = 0; var10 < 4; var10++) {
                     this.legs[var10] = new int[]{
                        this.index(var24[var10] + "top0"),
                        this.index(var24[var10] + "top1"),
                        this.index(var24[var10] + "top2"),
                        this.index(var24[var10] + "bot0"),
                        this.index(var24[var10] + "hoof")
                     };
                  }

                  float var25 = 0.0F;

                  for (int var11 = 0; var11 < 4; var11++) {
                     Vector3f var12 = new Matrix4f(this.inverseBind[this.legs[var11][1]]).invert().getTranslation(new Vector3f());
                     Vector3f var13 = new Matrix4f(this.inverseBind[this.legs[var11][4]]).invert().getTranslation(new Vector3f());
                     var25 += var12.distance(var13);
                  }

                  this.legLength = var25 / 4.0F;
                  this.order = new int[var4];
                  boolean[] var27 = new boolean[var4];
                  int var29 = 0;

                  while (var29 < var4) {
                     boolean var31 = false;

                     for (int var14 = 0; var14 < var4; var14++) {
                        if (!var27[var14] && (this.parents[var14] < 0 || var27[this.parents[var14]])) {
                           this.order[var29++] = var14;
                           var27[var14] = true;
                           var31 = true;
                        }
                     }

                     if (!var31) {
                        throw new IOException("Cyclic skeleton");
                     }
                  }

                  return;
               }
            } else {
               throw new IOException("Vertex budget");
            }
         } else {
            throw new IOException("Bone budget");
         }
      }
   }

   public float gaitScale() {
      return this.legLength / 0.5225816F;
   }

   public int count() {
      return this.names.length;
   }

   public int bodyTop1() {
      return this.bodyTop1;
   }

   public int find(String var1) {
      for (int var2 = 0; var2 < this.names.length; var2++) {
         if (this.names[var2].equals(var1)) {
            return var2;
         }
      }

      return -1;
   }

   public boolean isNeck(int var1) {
      return var1 >= 0 && (var1 == this.neck0 || var1 == this.neck1 || var1 == this.neck2);
   }

   public boolean isHead(int var1) {
      return var1 >= 0 && (var1 == this.head || var1 == this.earL || var1 == this.earR || var1 == this.jaw);
   }

   public int index(String var1) {
      for (int var2 = 0; var2 < this.names.length; var2++) {
         if (this.names[var2].equals(var1)) {
            return var2;
         }
      }

      throw new IllegalStateException("Missing bone " + var1);
   }

   public DeerSkeleton.Clip clip(String var1) {
      return this.clips.get(var1);
   }

   public void sample(DeerSkeleton.Clip var1, float var2, float[] var3, float[] var4) {
      int var5 = this.names.length;
      float var6 = var2 * var1.fps;
      int var7 = var1.frames;
      int var8;
      int var9;
      float var10;
      if (var7 == 1) {
         var9 = 0;
         var8 = 0;
         var10 = 0.0F;
      } else if (var1.loop) {
         var6 %= (float)var7;
         if (var6 < 0.0F) {
            var6 += (float)var7;
         }

         var8 = (int)Math.floor((double)var6);
         var9 = (var8 + 1) % var7;
         var10 = var6 - (float)var8;
      } else {
         var6 = Math.max(0.0F, Math.min((float)(var7 - 1), var6));
         var8 = (int)Math.floor((double)var6);
         var9 = Math.min(var7 - 1, var8 + 1);
         var10 = var6 - (float)var8;
      }

      float[] var11 = var1.data;

      for (int var12 = 0; var12 < var5; var12++) {
         int var13 = (var8 * var5 + var12) * 7;
         int var14 = (var9 * var5 + var12) * 7;

         for (int var15 = 0; var15 < 3; var15++) {
            var3[var12 * 3 + var15] = var11[var13 + var15] + (var11[var14 + var15] - var11[var13 + var15]) * var10;
         }

         nlerpInto(var11, var13 + 3, var11, var14 + 3, var10, var4, var12 * 4);
      }
   }

   public static void blend(float[] var0, float[] var1, float[] var2, float[] var3, float var4, boolean[] var5) {
      if (!(var4 <= 0.0F)) {
         int var6 = var0.length / 3;

         for (int var7 = 0; var7 < var6; var7++) {
            if (var5 == null || var5[var7]) {
               for (int var8 = 0; var8 < 3; var8++) {
                  var0[var7 * 3 + var8] = var0[var7 * 3 + var8] + (var2[var7 * 3 + var8] - var0[var7 * 3 + var8]) * var4;
               }

               nlerpInto(var1, var7 * 4, var3, var7 * 4, var4, var1, var7 * 4);
            }
         }
      }
   }

   static void nlerpInto(float[] var0, int var1, float[] var2, int var3, float var4, float[] var5, int var6) {
      float var7 = var0[var1];
      float var8 = var0[var1 + 1];
      float var9 = var0[var1 + 2];
      float var10 = var0[var1 + 3];
      float var11 = var2[var3];
      float var12 = var2[var3 + 1];
      float var13 = var2[var3 + 2];
      float var14 = var2[var3 + 3];
      if (var7 * var11 + var8 * var12 + var9 * var13 + var10 * var14 < 0.0F) {
         var11 = -var11;
         var12 = -var12;
         var13 = -var13;
         var14 = -var14;
      }

      float var15 = var7 + (var11 - var7) * var4;
      float var16 = var8 + (var12 - var8) * var4;
      float var17 = var9 + (var13 - var9) * var4;
      float var18 = var10 + (var14 - var10) * var4;
      float var19 = (float)Math.sqrt((double)(var15 * var15 + var16 * var16 + var17 * var17 + var18 * var18));
      if (var19 < 1.0E-8F) {
         var15 = 0.0F;
         var16 = 0.0F;
         var17 = 0.0F;
         var18 = 1.0F;
         var19 = 1.0F;
      }

      var5[var6] = var15 / var19;
      var5[var6 + 1] = var16 / var19;
      var5[var6 + 2] = var17 / var19;
      var5[var6 + 3] = var18 / var19;
   }

   public void pose(float[] var1, float[] var2, float[] var3, Matrix4f[] var4) {
      Quaternionf var5 = new Quaternionf();

      for (int var6 = 0; var6 < this.order.length; var6++) {
         int var7 = this.order[var6];
         var5.set(var2[var7 * 4], var2[var7 * 4 + 1], var2[var7 * 4 + 2], var2[var7 * 4 + 3]);
         Matrix4f var8 = var4[var7];
         if (this.parents[var7] >= 0) {
            var8.set(var4[this.parents[var7]]);
         } else {
            var8.identity();
         }

         var8.translate(var1[var7 * 3], var1[var7 * 3 + 1], var1[var7 * 3 + 2]).rotate(var5);
         if (var3 != null && var3[var7] != 1.0F) {
            var8.scale(var3[var7]);
         }
      }
   }

   public void rest(float[] var1, float[] var2) {
      System.arraycopy(this.restT, 0, var1, 0, this.restT.length);
      System.arraycopy(this.restQ, 0, var2, 0, this.restQ.length);
   }

   public static void preRotateLocal(float[] var0, int var1, float var2, float var3, float var4, float var5) {
      if (var5 != 0.0F) {
         float var6 = (float)Math.sin((double)(var5 * 0.5F));
         float var7 = (float)Math.cos((double)(var5 * 0.5F));
         float var8 = var2 * var6;
         float var9 = var3 * var6;
         float var10 = var4 * var6;
         float var12 = var0[var1 * 4];
         float var13 = var0[var1 * 4 + 1];
         float var14 = var0[var1 * 4 + 2];
         float var15 = var0[var1 * 4 + 3];
         var0[var1 * 4] = var7 * var12 + var8 * var15 + var9 * var14 - var10 * var13;
         var0[var1 * 4 + 1] = var7 * var13 - var8 * var14 + var9 * var15 + var10 * var12;
         var0[var1 * 4 + 2] = var7 * var14 + var8 * var13 - var9 * var12 + var10 * var15;
         var0[var1 * 4 + 3] = var7 * var15 - var8 * var12 - var9 * var13 - var10 * var14;
      }
   }

   private static float finite(float var0) throws IOException {
      if (!Float.isFinite(var0)) {
         throw new IOException("Nonfinite skeleton data");
      } else {
         return var0;
      }
   }

   public static Vector3f parentLocalAxis(Matrix4f var0, float var1, float var2, float var3, Vector3f var4) {
      Matrix4f var5 = new Matrix4f(var0).invert();
      return var5.transformDirection(var4.set(var1, var2, var3)).normalize();
   }

   public static record Clip(String name, float fps, boolean loop, int frames, float[] data) {
      public float duration() {
         return (float)this.frames / this.fps;
      }
   }

   static final class CountingInput {
      final byte[] bytes;
      final ByteArrayInputStream stream;
      final DataInputStream data;

      CountingInput(byte[] var1) {
         this.bytes = var1;
         this.stream = new ByteArrayInputStream(var1);
         this.data = new DataInputStream(this.stream);
      }

      int position() {
         return this.bytes.length - this.stream.available();
      }
   }
}
