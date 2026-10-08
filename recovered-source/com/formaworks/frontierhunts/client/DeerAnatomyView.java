package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.hunting.DeerAnatomy;
import com.formaworks.frontierhunts.hunting.DeerAnatomyMesh;
import com.formaworks.frontierhunts.hunting.DeerAnimator;
import com.formaworks.frontierhunts.hunting.DeerMeshData;
import com.formaworks.frontierhunts.hunting.DeerOrgan;
import com.formaworks.frontierhunts.hunting.DeerTraits;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

final class DeerAnatomyView {
   private static float[] pos;
   private static float[] nrm;
   private static int posedDeer = -1;
   private static float posedTime = Float.NaN;
   private static DeerOrgan focused;
   private static int focusDeer = -1;
   private static int focusTick = -1;
   private static float focusYaw;
   private static float focusPitch;
   private static final float[] EMPTY = new float[0];

   static DeerOrgan focused(int var0) {
      return var0 == focusDeer ? focused : null;
   }

   static void clear() {
      focused = null;
      focusDeer = -1;
      posedDeer = -1;
      posedTime = Float.NaN;
   }

   static void draw(Whitetail var0, float var1, PoseStack var2, MultiBufferSource var3, Vec3 var4) {
      Minecraft var5 = Minecraft.getInstance();
      DeerAnatomyMesh var6 = DeerAnatomyMesh.of(var0.species());
      DeerTraits var7 = var0.traits();
      DeerAnimator var8 = WhitetailRenderer.pose(var0, var1);
      float var9 = ((float)var0.tickCount + var1) / 20.0F;
      if (pos == null || pos.length != var6.vertices * 3) {
         pos = new float[var6.vertices * 3];
         nrm = new float[var6.vertices * 3];
         posedDeer = -1;
      }

      if (posedDeer != var0.getId() || posedTime != var9) {
         var6.skin(var8.skin, pos, nrm);
         posedDeer = var0.getId();
         posedTime = var9;
      }

      float var10 = var7.frameWidth();
      float var11 = var7.frameHeight();
      float var12 = var7.frameLength();
      Vec3 var13 = new Vec3(
         Mth.lerp((double)var1, var0.xo, var0.getX()), Mth.lerp((double)var1, var0.yo, var0.getY()), Mth.lerp((double)var1, var0.zo, var0.getZ())
      );
      float var14 = Mth.rotLerp(var1, var0.yBodyRotO, var0.yBodyRot);
      Vec3 var15 = scaled(DeerAnatomy.local(var4, var13, var14), var10, var11, var12);
      if (focusDeer != var0.getId()
         || focusTick != var5.player.tickCount
         || Math.abs(var5.player.getYRot() - focusYaw) > 0.15F
         || Math.abs(var5.player.getXRot() - focusPitch) > 0.15F) {
         Vec3 var16 = scaled(DeerAnatomy.local(var4.add(var5.player.getLookAngle().scale(160.0)), var13, var14), var10, var11, var12);
         focused = pick(var6, var15, var16);
         focusDeer = var0.getId();
         focusTick = var5.player.tickCount;
         focusYaw = var5.player.getYRot();
         focusPitch = var5.player.getXRot();
      }

      var2.pushPose();
      var2.scale(var10, var11, var12);
      VertexConsumer var20 = var3.getBuffer(OpticsRenderType.ORGANS);
      float[][] var17 = DeerDraw.skin(var8, var7, 2);
      shell(var2, var20, DeerMeshData.of(var0.species()), var17, var15, var0.traits().buck());

      byte var18 = switch ((HuntConfig.Quality)HuntConfig.QUALITY.get()) {
         case CINEMATIC -> 0;
         case BALANCED -> 1;
         default -> 2;
      };

      for (int var19 = 0; var19 < var6.parts; var19++) {
         emit(var2, var20, var6, var19, var18, var15);
      }

      var2.popPose();
   }

   private static Vec3 scaled(Vec3 var0, float var1, float var2, float var3) {
      return new Vec3(var0.x / (double)var1, var0.y / (double)var2, var0.z / (double)var3);
   }

   private static DeerOrgan pick(DeerAnatomyMesh var0, Vec3 var1, Vec3 var2) {
      double var3 = Double.POSITIVE_INFINITY;
      DeerOrgan var5 = null;
      double var6 = Double.POSITIVE_INFINITY;
      DeerOrgan var8 = null;

      for (int var9 = 0; var9 < var0.parts; var9++) {
         DeerOrgan var10 = var0.partOrgan[var9];
         if (var10 != DeerOrgan.DIAPHRAGM) {
            double var11 = var0.raycast(pos, var9, 1, var1.x, var1.y, var1.z, var2.x, var2.y, var2.z);
            if (var10.bone) {
               if (var11 < var6) {
                  var6 = var11;
                  var8 = var10;
               }
            } else if (var11 < var3) {
               var3 = var11;
               var5 = var10;
            }
         }
      }

      if (var5 == null || var8 != null && var8 != DeerOrgan.RIBS && !(var3 <= var6 + 0.004)) {
         return var8 != null ? var8 : var5;
      } else {
         return var5;
      }
   }

   private static void shell(PoseStack var0, VertexConsumer var1, DeerMeshData var2, float[][] var3, Vec3 var4, boolean var5) {
      int[] var6 = var2.faces(2, var5);
      float[] var7 = var3[0];
      float[] var8 = var3[1];
      Pose var9 = var0.last();

      for (byte var10 = 0; var10 < var6.length; var10 += 3) {
         int var11 = var6[var10];
         int var12 = var6[var10 + 1];
         int var13 = var6[var10 + 2];
         double var14 = (double)((var7[var11 * 3] + var7[var12 * 3] + var7[var13 * 3]) / 3.0F);
         double var16 = (double)((var7[var11 * 3 + 1] + var7[var12 * 3 + 1] + var7[var13 * 3 + 1]) / 3.0F);
         double var18 = (double)((var7[var11 * 3 + 2] + var7[var12 * 3 + 2] + var7[var13 * 3 + 2]) / 3.0F);
         double var20 = (double)(var8[var11 * 3] + var8[var12 * 3] + var8[var13 * 3]);
         double var22 = (double)(var8[var11 * 3 + 1] + var8[var12 * 3 + 1] + var8[var13 * 3 + 1]);
         double var24 = (double)(var8[var11 * 3 + 2] + var8[var12 * 3 + 2] + var8[var13 * 3 + 2]);
         double var26 = var20 * (var4.x - var14) + var22 * (var4.y - var16) + var24 * (var4.z - var18);
         if (!(var26 < 0.0)) {
            double var28 = Math.sqrt(var20 * var20 + var22 * var22 + var24 * var24)
               * Math.sqrt((var4.x - var14) * (var4.x - var14) + (var4.y - var16) * (var4.y - var16) + (var4.z - var18) * (var4.z - var18));
            float var30 = (float)(1.0 - var26 / Math.max(1.0E-6, var28));
            int var31 = (int)(26.0F + 70.0F * var30 * var30);

            for (int var35 : new int[]{var11, var12, var13, var13}) {
               var1.addVertex(var9.pose(), var7[var35 * 3], var7[var35 * 3 + 1], var7[var35 * 3 + 2])
                  .setColor(150, 172, 176, var31)
                  .setUv(0.5F, 0.5F)
                  .setOverlay(OverlayTexture.NO_OVERLAY)
                  .setLight(15728880)
                  .setNormal(var9, var8[var35 * 3], var8[var35 * 3 + 1], var8[var35 * 3 + 2]);
            }
         }
      }
   }

   private static void emit(PoseStack var0, VertexConsumer var1, DeerAnatomyMesh var2, int var3, int var4, Vec3 var5) {
      DeerOrgan var6 = var2.partOrgan[var3];
      boolean var7 = var6 == focused;
      boolean var8 = var6 == DeerOrgan.DIAPHRAGM;
      int var9 = var8 ? (var7 ? 170 : 70) : (var6.bone ? (var7 ? 255 : (var6 == DeerOrgan.RIBS ? 150 : 205)) : 255);
      float var10 = var7 ? 0.3F : 0.0F;
      float var11 = focused != null && !var7 && !var6.bone ? 0.12F : 0.0F;
      float var12 = 1.0F;
      int[] var13 = var2.lods[var4][var3];
      Pose var14 = var0.last();

      for (byte var15 = 0; var15 < var13.length; var15 += 3) {
         int var16 = var13[var15];
         int var17 = var13[var15 + 1];
         int var18 = var13[var15 + 2];
         if (!var8) {
            double var19 = (double)pos[var16 * 3];
            double var21 = (double)pos[var16 * 3 + 1];
            double var23 = (double)pos[var16 * 3 + 2];
            double var25 = (double)(nrm[var16 * 3] + nrm[var17 * 3] + nrm[var18 * 3]) * (var5.x - var19)
               + (double)(nrm[var16 * 3 + 1] + nrm[var17 * 3 + 1] + nrm[var18 * 3 + 1]) * (var5.y - var21)
               + (double)(nrm[var16 * 3 + 2] + nrm[var17 * 3 + 2] + nrm[var18 * 3 + 2]) * (var5.z - var23);
            if (var25 < -0.004) {
               continue;
            }
         }

         for (int var22 : new int[]{var16, var17, var18, var18}) {
            int var30 = var2.color[var22];
            int var24 = var30 >> 16 & 0xFF;
            int var31 = var30 >> 8 & 0xFF;
            int var26 = var30 & 0xFF;
            if (var10 > 0.0F) {
               var24 += (int)((float)(255 - var24) * var10);
               var31 += (int)((float)(236 - var31) * var10);
               var26 += (int)((float)(200 - var26) * var10);
            } else if (var11 > 0.0F) {
               int var27 = (var24 * 3 + var31 * 5 + var26 * 2) / 10;
               var24 += (int)((float)(var27 - var24) * var11);
               var31 += (int)((float)(var27 - var31) * var11);
               var26 += (int)((float)(var27 - var26) * var11);
            }

            var1.addVertex(var14.pose(), pos[var22 * 3] * var12, pos[var22 * 3 + 1] * var12, pos[var22 * 3 + 2] * var12)
               .setColor(Math.min(255, var24), Math.min(255, var31), Math.min(255, var26), var9)
               .setUv(0.5F, 0.5F)
               .setOverlay(OverlayTexture.NO_OVERLAY)
               .setLight(15728880)
               .setNormal(var14, nrm[var22 * 3], nrm[var22 * 3 + 1], nrm[var22 * 3 + 2]);
         }
      }
   }

   private DeerAnatomyView() {
   }
}
