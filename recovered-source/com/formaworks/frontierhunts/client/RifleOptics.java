package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.HuntConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class RifleOptics {
   private static final float X = -7.5E-4F;
   private static final float Y = 0.147F;
   private static final Vector3f HIGHLIGHT_DIRECTION = new Vector3f(-0.2F, 0.22F, 0.95F).normalize();
   private static final float[][] PATCHES = new float[][]{patch(32, 6), patch(64, 10), patch(80, 12)};

   private static float[] patch(int var0, int var1) {
      float[] var2 = new float[var0 * var1 * 4 * 2];
      int var3 = 0;

      for (int var4 = 0; var4 < var1; var4++) {
         for (int var5 = 0; var5 < var0; var5++) {
            for (int var6 = 0; var6 < 4; var6++) {
               double var7 = (double)(var5 + (var6 >= 2 ? 1 : 0)) * Math.PI * 2.0 / (double)var0;
               float var9 = (float)(var4 + (var6 != 1 && var6 != 2 ? 0 : 1)) / (float)var1;
               var2[var3++] = (float)Math.cos(var7) * var9;
               var2[var3++] = (float)Math.sin(var7) * var9;
            }
         }
      }

      return var2;
   }

   public static void draw(PoseStack var0, MultiBufferSource var1, int var2) {
      if (!HuntShaderCompat.shadowPass()) {
         byte var3 = switch ((HuntConfig.Quality)HuntConfig.QUALITY.get()) {
            case PERFORMANCE -> 0;
            case BALANCED -> 1;
            case CINEMATIC -> 2;
         };
         lens(var0, var1, var2, 0.22545F, 0.0195F, 1, PATCHES[var3]);
         lens(var0, var1, var2, -0.1136F, 0.0205F, -1, PATCHES[var3]);
      }
   }

   public static void itemLens(PoseStack var0, MultiBufferSource var1, int var2, float var3, int var4) {
      if (!HuntShaderCompat.shadowPass()) {
         var0.pushPose();
         var0.translate(7.5E-4F, -0.147F, 0.0F);
         lens(var0, var1, var2, 0.0F, var3, var4, PATCHES[HuntConfig.QUALITY.get() == HuntConfig.Quality.PERFORMANCE ? 0 : 1]);
         var0.popPose();
      }
   }

   private static void lens(PoseStack var0, MultiBufferSource var1, int var2, float var3, float var4, int var5, float[] var6) {
      Pose var7 = var0.last();
      Vector3f var8 = var7.normal().transform(new Vector3f(0.0F, 0.0F, (float)var5)).normalize();
      Vector3f var9 = new Vector3f(0.0F, 0.0F, 1.0F);
      if (var7.pose().getColumn(0, new Vector4f()).length() < 4.0F) {
         var7.pose().transformPosition(new Vector3f(-7.5E-4F, 0.147F, var3), var9);
         var9.negate().normalize();
      }

      if (!(var8.dot(var9) <= 0.015F)) {
         VertexConsumer var10 = var1.getBuffer(RenderType.entityCutoutNoCull(WhitetailRenderer.MATERIAL));

         for (byte var11 = 0; var11 < var6.length; var11 += 2) {
            float var12 = var6[var11];
            float var13 = var6[var11 + 1];
            HuntMesh.vertex(var10, var7, var2, 1055005, -7.5E-4F + var12 * var4, 0.147F + var13 * var4, var3, 0.5F, 0.5F, 0.0F, 0.0F, (float)var5);
         }

         for (int var34 = 0; var34 < 64; var34++) {
            for (int var36 = 0; var36 < 4; var36++) {
               double var38 = (double)(var34 + (var36 >= 2 ? 1 : 0)) * Math.PI * 2.0 / 64.0;
               boolean var15 = var36 == 1 || var36 == 2;
               float var16 = var4 + (var15 ? 1.0E-4F : 0.00115F);
               float var17 = (float)Math.cos(var38);
               float var18 = (float)Math.sin(var38);
               HuntMesh.vertex(
                  var10,
                  var7,
                  var2,
                  var15 ? 1714219 : 3292222,
                  -7.5E-4F + var17 * var16,
                  0.147F + var18 * var16,
                  var3 + (float)var5 * (var15 ? 1.0E-4F : 4.5E-4F),
                  0.5F,
                  0.5F,
                  var17 * 0.25F,
                  var18 * 0.25F,
                  (float)var5 * 0.94F
               );
            }
         }

         VertexConsumer var35 = var1.getBuffer(RenderType.entityTranslucent(WhitetailRenderer.MATERIAL));
         Minecraft var37 = Minecraft.getInstance();
         Vec3 var39 = var37.level == null ? new Vec3(0.5, 0.65, 0.78) : var37.level.getSkyColor(var37.gameRenderer.getMainCamera().getPosition(), 0.0F);
         Vector3f var14 = new Vector3f();
         Vector3f var40 = new Vector3f();
         Vector3f var41 = new Vector3f();

         for (byte var42 = 0; var42 < var6.length; var42 += 2) {
            float var43 = var6[var42];
            float var19 = var6[var42 + 1];
            float var20 = var43 * var43 + var19 * var19;
            float var21 = var43 * 0.15F;
            float var22 = var19 * 0.15F;
            float var23 = (float)var5;
            var14.set(var21, var22, var23).normalize();
            var7.normal().transform(var14).normalize();
            float var24 = Math.max(0.0F, var14.dot(var9));
            float var25 = 0.045F + 0.48F * (float)Math.pow((double)(1.0F - var24), 5.0);
            var40.set(var14).mul(2.0F * var24).sub(var9);
            var41.set(var40);
            var37.gameRenderer.getMainCamera().rotation().transform(var41);
            float var26 = smooth(-0.18F, 0.42F, var41.y);
            float var27 = (float)Math.pow((double)Math.max(0.0F, var40.dot(HIGHLIGHT_DIRECTION)), 38.0) * 1.45F;
            float var28 = smooth(0.6F, 1.0F, var20);
            float var29 = 0.09F + var25;
            float var30 = 0.034F + (float)var39.x * var26 * 0.23F + var29 * 0.1F + var27;
            float var31 = 0.073F + (float)var39.y * var26 * 0.29F + var29 * 0.2F + var27;
            float var32 = 0.09F + (float)var39.z * var26 * 0.36F + var29 * 0.32F + var27;
            var30 += var28 * var25 * 0.14F;
            var31 *= 1.0F - var28 * 0.11F;
            int var33 = (int)(255.0F * (0.76F + var25 * 0.3F));
            var35.addVertex(var7.pose(), -7.5E-4F + var43 * var4, 0.147F + var19 * var4, var3 + (float)var5 * (2.0E-4F + 0.00115F * (1.0F - var20)))
               .setColor(channel(var30), channel(var31), channel(var32), Math.min(255, var33))
               .setUv(0.5F, 0.5F)
               .setOverlay(OverlayTexture.NO_OVERLAY)
               .setLight(var2)
               .setNormal(var7, var21, var22, var23);
         }
      }
   }

   private static int channel(float var0) {
      return (int)(Math.clamp(var0, 0.0F, 1.0F) * 255.0F);
   }

   private static float smooth(float var0, float var1, float var2) {
      float var3 = Math.clamp((var2 - var0) / (var1 - var0), 0.0F, 1.0F);
      return var3 * var3 * (3.0F - 2.0F * var3);
   }

   private RifleOptics() {
   }
}
