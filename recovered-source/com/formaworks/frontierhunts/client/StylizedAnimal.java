package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.hunting.DeerTraits;
import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.formaworks.frontierhunts.hunting.HuntResources;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.logging.LogUtils;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.slf4j.Logger;

public final class StylizedAnimal {
   private static final Logger LOG = LogUtils.getLogger();
   private static final StylizedAnimal[] BY = new StylizedAnimal[GameSpecies.values().length];
   private static final boolean[] FAILED = new boolean[GameSpecies.values().length];
   private static final ResourceLocation[] COATS = new ResourceLocation[GameSpecies.values().length * 2];
   final int vertices;
   final int triangles;
   final int mountStart;
   final float[] pos;
   final float[] uv;
   final int[] bone;
   final float[] weight;
   final int[] index;
   final float[] nrm;
   private float[] skinned = new float[0];
   private float[] skinnedN = new float[0];
   private float[] dq = new float[0];
   private final Quaternionf q = new Quaternionf();

   private StylizedAnimal(byte[] var1) throws IOException {
      DataInputStream var2 = new DataInputStream(new ByteArrayInputStream(var1));
      if (var2.readInt() != 1179143248) {
         throw new IOException("not a stylized animal");
      } else {
         int var3 = var2.readInt();
         this.vertices = var2.readInt();
         this.triangles = var2.readInt();
         this.mountStart = var2.readInt();
         this.pos = new float[this.vertices * 3];
         this.uv = new float[this.vertices * 2];
         this.bone = new int[this.vertices * 4];
         this.weight = new float[this.vertices * 4];
         this.nrm = var3 >= 2 ? new float[this.vertices * 3] : null;
         byte[] var4 = new byte[8];

         for (int var5 = 0; var5 < this.vertices; var5++) {
            this.pos[var5 * 3] = var2.readFloat();
            this.pos[var5 * 3 + 1] = var2.readFloat();
            this.pos[var5 * 3 + 2] = var2.readFloat();
            this.uv[var5 * 2] = var2.readFloat();
            this.uv[var5 * 2 + 1] = var2.readFloat();
            if (this.nrm != null) {
               this.nrm[var5 * 3] = var2.readFloat();
               this.nrm[var5 * 3 + 1] = var2.readFloat();
               this.nrm[var5 * 3 + 2] = var2.readFloat();
            }

            var2.readFully(var4);

            for (int var6 = 0; var6 < 4; var6++) {
               this.bone[var5 * 4 + var6] = var4[var6] & 255;
               this.weight[var5 * 4 + var6] = (float)(var4[4 + var6] & 255) / 255.0F;
            }
         }

         this.index = new int[this.triangles * 3];

         for (int var7 = 0; var7 < this.index.length; var7++) {
            this.index[var7] = var2.readUnsignedShort();
         }
      }
   }

   public static StylizedAnimal of(GameSpecies var0) {
      int var1 = var0.ordinal();
      StylizedAnimal var2 = BY[var1];
      if (var2 == null && !FAILED[var1]) {
         try {
            var2 = BY[var1] = new StylizedAnimal(HuntResources.read("/assets/frontierhunts/models/entity/" + var0.id + "_stylized.fhlp"));
         } catch (RuntimeException | IOException var4) {
            FAILED[var1] = true;
            LOG.warn("Frontier Hunts: no Balanced model for " + var0.id + " (" + var4 + ")");
         }
      }

      return var2;
   }

   public static ResourceLocation coat(DeerTraits var0) {
      int var1 = var0.species().ordinal() * 2 + (var0.greyCoat() ? 1 : 0);
      ResourceLocation var2 = COATS[var1];
      if (var2 == null) {
         var2 = COATS[var1] = FrontierHunts.id("textures/entity/stylized/" + var0.species().id + "_" + (var0.greyCoat() ? "winter" : "summer") + ".png");
         Minecraft var3 = Minecraft.getInstance();
         if (var3 != null) {
            var3.getTextureManager().register(var2, new MipmappedHuntTexture(var2));
         }
      }

      return var2;
   }

   private void skin(Matrix4f[] var1) {
      if (this.skinned.length != this.pos.length) {
         this.skinned = new float[this.pos.length];
         this.skinnedN = new float[this.pos.length];
      }

      int var2 = var1.length;
      if (this.dq.length != var2 * 8) {
         this.dq = new float[var2 * 8];
      }

      float[] var3 = this.dq;

      for (int var4 = 0; var4 < var2; var4++) {
         Matrix4f var5 = var1[var4];
         var5.getNormalizedRotation(this.q);
         float var6 = this.q.w;
         float var7 = this.q.x;
         float var8 = this.q.y;
         float var9 = this.q.z;
         float var10 = var5.m30();
         float var11 = var5.m31();
         float var12 = var5.m32();
         var3[var4 * 8] = var6;
         var3[var4 * 8 + 1] = var7;
         var3[var4 * 8 + 2] = var8;
         var3[var4 * 8 + 3] = var9;
         var3[var4 * 8 + 4] = -0.5F * (var10 * var7 + var11 * var8 + var12 * var9);
         var3[var4 * 8 + 5] = 0.5F * (var10 * var6 + var11 * var9 - var12 * var8);
         var3[var4 * 8 + 6] = 0.5F * (-var10 * var9 + var11 * var6 + var12 * var7);
         var3[var4 * 8 + 7] = 0.5F * (var10 * var8 - var11 * var7 + var12 * var6);
      }

      float[] var36 = this.pos;
      float[] var37 = this.skinned;

      for (int var38 = 0; var38 < this.vertices; var38++) {
         float var39 = 0.0F;
         float var41 = 0.0F;
         float var43 = 0.0F;
         float var45 = 0.0F;
         float var47 = 0.0F;
         float var49 = 0.0F;
         float var13 = 0.0F;
         float var14 = 0.0F;
         int var15 = -1;

         for (int var16 = 0; var16 < 4; var16++) {
            float var17 = this.weight[var38 * 4 + var16];
            int var18 = this.bone[var38 * 4 + var16];
            if (!(var17 <= 0.0F) && var18 < var2) {
               int var19 = var18 * 8;
               if (var15 < 0) {
                  var15 = var19;
               } else if (var3[var19] * var3[var15] + var3[var19 + 1] * var3[var15 + 1] + var3[var19 + 2] * var3[var15 + 2] + var3[var19 + 3] * var3[var15 + 3]
                  < 0.0F) {
                  var17 = -var17;
               }

               var39 += var17 * var3[var19];
               var41 += var17 * var3[var19 + 1];
               var43 += var17 * var3[var19 + 2];
               var45 += var17 * var3[var19 + 3];
               var47 += var17 * var3[var19 + 4];
               var49 += var17 * var3[var19 + 5];
               var13 += var17 * var3[var19 + 6];
               var14 += var17 * var3[var19 + 7];
            }
         }

         float var53 = var36[var38 * 3];
         float var54 = var36[var38 * 3 + 1];
         float var55 = var36[var38 * 3 + 2];
         float var56 = (float)Math.sqrt((double)(var39 * var39 + var41 * var41 + var43 * var43 + var45 * var45));
         if (var15 >= 0 && !(var56 < 1.0E-6F)) {
            float var20 = 1.0F / var56;
            var39 *= var20;
            var41 *= var20;
            var43 *= var20;
            var45 *= var20;
            var47 *= var20;
            var49 *= var20;
            var13 *= var20;
            var14 *= var20;
            float var21 = var43 * var55 - var45 * var54 + var39 * var53;
            float var22 = var45 * var53 - var41 * var55 + var39 * var54;
            float var23 = var41 * var54 - var43 * var53 + var39 * var55;
            float var24 = var53 + 2.0F * (var43 * var23 - var45 * var22);
            float var25 = var54 + 2.0F * (var45 * var21 - var41 * var23);
            float var26 = var55 + 2.0F * (var41 * var22 - var43 * var21);
            float var27 = 2.0F * (-var47 * var41 + var49 * var39 - var13 * var45 + var14 * var43);
            float var28 = 2.0F * (-var47 * var43 + var49 * var45 + var13 * var39 - var14 * var41);
            float var29 = 2.0F * (-var47 * var45 - var49 * var43 + var13 * var41 + var14 * var39);
            var37[var38 * 3] = var24 + var27;
            var37[var38 * 3 + 1] = var25 + var28;
            var37[var38 * 3 + 2] = var26 + var29;
            if (this.nrm != null) {
               float var30 = this.nrm[var38 * 3];
               float var31 = this.nrm[var38 * 3 + 1];
               float var32 = this.nrm[var38 * 3 + 2];
               float var33 = var43 * var32 - var45 * var31 + var39 * var30;
               float var34 = var45 * var30 - var41 * var32 + var39 * var31;
               float var35 = var41 * var31 - var43 * var30 + var39 * var32;
               this.skinnedN[var38 * 3] = var30 + 2.0F * (var43 * var35 - var45 * var34);
               this.skinnedN[var38 * 3 + 1] = var31 + 2.0F * (var45 * var33 - var41 * var35);
               this.skinnedN[var38 * 3 + 2] = var32 + 2.0F * (var41 * var34 - var43 * var33);
            }
         } else {
            var37[var38 * 3] = var53;
            var37[var38 * 3 + 1] = var54;
            var37[var38 * 3 + 2] = var55;
         }
      }
   }

   public void draw(Pose var1, VertexConsumer var2, int var3, int var4, Matrix4f[] var5, float var6, float var7, float var8) {
      this.draw(var1, var2, var3, var4, var5, var6, var7, var8, 0, this.triangles);
   }

   public void draw(Pose var1, VertexConsumer var2, int var3, int var4, Matrix4f[] var5, float var6, float var7, float var8, int var9, int var10) {
      var9 = Math.max(0, var9);
      var10 = Math.min(this.triangles, var10);
      if (var9 < var10) {
         this.skin(var5);
         Matrix4f var11 = var1.pose();
         Matrix3f var12 = var1.normal();
         float[] var13 = this.skinned;
         int[] var14 = this.index;

         for (int var15 = var9; var15 < var10; var15++) {
            int var16 = var14[var15 * 3] * 3;
            int var17 = var14[var15 * 3 + 1] * 3;
            int var18 = var14[var15 * 3 + 2] * 3;
            float var19 = var13[var17] - var13[var16];
            float var20 = var13[var17 + 1] - var13[var16 + 1];
            float var21 = var13[var17 + 2] - var13[var16 + 2];
            float var22 = var13[var18] - var13[var16];
            float var23 = var13[var18 + 1] - var13[var16 + 1];
            float var24 = var13[var18 + 2] - var13[var16 + 2];
            float var25 = var20 * var24 - var21 * var23;
            float var26 = var21 * var22 - var19 * var24;
            float var27 = var19 * var23 - var20 * var22;
            float var28 = (float)Math.sqrt((double)(var25 * var25 + var26 * var26 + var27 * var27));
            if (!(var28 < 1.0E-12F)) {
               var25 /= var28;
               var26 /= var28;
               var27 /= var28;
               float var29 = var12.m00() * var25 + var12.m10() * var26 + var12.m20() * var27;
               float var30 = var12.m01() * var25 + var12.m11() * var26 + var12.m21() * var27;
               float var31 = var12.m02() * var25 + var12.m12() * var26 + var12.m22() * var27;

               for (int var32 = 0; var32 < 3; var32++) {
                  int var33 = var14[var15 * 3 + var32];
                  if (this.nrm != null) {
                     float[] var34 = this.skinnedN;
                     float var35 = var34[var33 * 3];
                     float var36 = var34[var33 * 3 + 1];
                     float var37 = var34[var33 * 3 + 2];
                     var29 = var12.m00() * var35 + var12.m10() * var36 + var12.m20() * var37;
                     var30 = var12.m01() * var35 + var12.m11() * var36 + var12.m21() * var37;
                     var31 = var12.m02() * var35 + var12.m12() * var36 + var12.m22() * var37;
                  }

                  var2.addVertex(var11, var13[var33 * 3], var13[var33 * 3 + 1], var13[var33 * 3 + 2])
                     .setColor(var6, var7, var8, 1.0F)
                     .setUv(this.uv[var33 * 2], this.uv[var33 * 2 + 1])
                     .setOverlay(var4)
                     .setLight(var3)
                     .setNormal(var29, var30, var31);
               }
            }
         }
      }
   }

   public float[] mountBounds(Matrix4f[] var1) {
      this.skin(var1);
      float[] var2 = new float[]{Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};

      for (int var3 = this.mountStart * 3; var3 < this.index.length; var3++) {
         int var4 = this.index[var3] * 3;

         for (int var5 = 0; var5 < 3; var5++) {
            var2[var5] = Math.min(var2[var5], this.skinned[var4 + var5]);
            var2[var5 + 3] = Math.max(var2[var5 + 3], this.skinned[var4 + var5]);
         }
      }

      return var2;
   }

   static void clear() {
      for (int var0 = 0; var0 < BY.length; var0++) {
         BY[var0] = null;
         FAILED[var0] = false;
      }

      Arrays.fill(COATS, null);
   }

   static StylizedAnimal active(GameSpecies var0) {
      return null;
   }
}
