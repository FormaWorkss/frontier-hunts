package com.formaworks.frontierhunts.landscape.ride.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.client.FrontierGraphics;
import com.formaworks.frontierhunts.client.HuntRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

final class RealisticAtv {
   static final ResourceLocation MESH = FrontierHunts.id("models/entity/atv_real.fhvm");
   static final ResourceLocation TEXTURE = FrontierHunts.id("textures/entity/atv_real.png");
   static final ResourceLocation WHEEL_TEXTURE = FrontierHunts.id("textures/entity/atv_wheel.png");
   private static float[][] parts;
   private static int[] textures;
   private static float[][] pivots;
   private static boolean failed;

   private RealisticAtv() {
   }

   static boolean active() {
      return FrontierGraphics.realisticWorld() && load();
   }

   private static boolean load() {
      if (parts != null) {
         return true;
      } else if (failed) {
         return false;
      } else {
         try {
            Resource var0 = (Resource)Minecraft.getInstance().getResourceManager().getResource(MESH).orElse(null);
            if (var0 == null) {
               failed = true;
               return false;
            } else {
               boolean var18;
               try (
                  InputStream var1 = var0.open();
                  DataInputStream var2 = new DataInputStream(new BufferedInputStream(var1));
               ) {
                  byte[] var3 = new byte[4];
                  var2.readFully(var3);
                  int var4 = var2.readInt();
                  int var5 = var2.readInt();
                  float[][] var6 = new float[var5][];
                  float[][] var7 = new float[var5][3];
                  int[] var8 = new int[var5];

                  for (int var9 = 0; var9 < var5; var9++) {
                     for (int var10 = 0; var10 < 3; var10++) {
                        var7[var9][var10] = var2.readFloat();
                     }

                     if (var4 >= 2) {
                        var8[var9] = var2.readInt();
                     }

                     int var19 = var2.readInt();
                     float[] var11 = new float[var19 * 24];

                     for (int var12 = 0; var12 < var11.length; var12++) {
                        var11[var12] = var2.readFloat();
                     }

                     var6[var9] = var11;
                  }

                  pivots = var7;
                  textures = var8;
                  parts = var6;
                  var18 = true;
               }

               return var18;
            }
         } catch (Exception var17) {
            failed = true;
            return false;
         }
      }
   }

   static void render(PoseStack var0, MultiBufferSource var1, int var2, float var3, float var4) {
      render(var0, var1, var2, var3, var4, -1);
   }

   /** [atv2] same, with a vertex colour (ARGB) multiplied into the paint: the wet-darkening tint from AtvGrimeRender. */
   static void render(PoseStack var0, MultiBufferSource var1, int var2, float var3, float var4, int tint) {
      float tr = (tint >> 16 & 255) / 255.0F, tg = (tint >> 8 & 255) / 255.0F, tb = (tint & 255) / 255.0F; // [atv2]
      float var5 = var3 * 0.55F;

      for (int var6 = 0; var6 < parts.length; var6++) {
         VertexConsumer var7 = var1.getBuffer(HuntRenderTypes.sculpt(textures[var6] == 1 ? WHEEL_TEXTURE : TEXTURE));
         var0.pushPose();
         float[] var8 = pivots[var6];
         if (var6 >= 1) {
            float var9 = var6 == 1 ? var5 * 0.8F : (var6 < 4 ? var5 : 0.0F);
            float var10 = var6 >= 2 ? var4 : 0.0F;
            var0.translate(var8[0] / 16.0F, var8[1] / 16.0F, var8[2] / 16.0F);
            var0.mulPose(new Quaternionf().rotationZYX(0.0F, var9, var10));
            var0.translate(-var8[0] / 16.0F, -var8[1] / 16.0F, -var8[2] / 16.0F);
         }

         Pose var13 = var0.last();
         Matrix4f var14 = var13.pose();
         float[] var11 = parts[var6];

         for (int var12 = 0; var12 < var11.length; var12 += 8) { // [atv2] int (decompiled as byte)
            var7.addVertex(var14, var11[var12] / 16.0F, var11[var12 + 1] / 16.0F, var11[var12 + 2] / 16.0F)
               .setColor(tr, tg, tb, 1.0F) // [atv2] wet tint
               .setUv(var11[var12 + 3], var11[var12 + 4])
               .setOverlay(OverlayTexture.NO_OVERLAY)
               .setLight(var2)
               .setNormal(var13, var11[var12 + 5], var11[var12 + 6], var11[var12 + 7]);
         }

         var0.popPose();
      }
   }
}
