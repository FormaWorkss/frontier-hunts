package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.expedition.ScoutingNetwork;
import com.formaworks.frontierhunts.expedition.Skyline;
import com.formaworks.frontierhunts.hunting.DeerAnimator;
import com.formaworks.frontierhunts.hunting.DeerSkeleton;
import com.formaworks.frontierhunts.hunting.DeerTraits;
import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;

final class CameraPhoto {
   private static final float FOV_H = 110.0F;
   private static final float FOV_V = 74.0F;
   private static final float LENS_HEIGHT = 1.15F;
   private static final DeerAnimator[] RIGS = new DeerAnimator[GameSpecies.values().length];
   private static final int OVERLAY_Z = 320;
   private static boolean warned;

   static void draw(GuiGraphics var0, int var1, int var2, int var3, int var4, ScoutingNetwork.Frame var5, long var6, byte[] var8, float var9) {
      var0.enableScissor(var1, var2, var1 + var3, var2 + var4);
      byte var10 = (byte)var5.sky();
      backdrop(var0, var1, var2, var3, var4, var10, var6, var8);
      try {
         deer(var0, var1, var2, var3, var4, var5, var9);
      } catch (Throwable var12) {
         // A model/render-type failure (e.g. a shader mod refusing the GUI pass) must not take the camera screen down:
         // the frame still shows the scene, stamp and data without the animal.
         if (!warned) {
            warned = true;
            org.slf4j.LoggerFactory.getLogger(CameraPhoto.class).warn("Trail camera frame could not draw the animal model", var12);
         }
      }

      var0.pose().pushPose();
      var0.pose().translate(0.0F, 0.0F, 320.0F);
      grain(var0, var1, var2, var3, var4, var10, var6, var9);
      var0.disableScissor();
      stamp(var0, var1, var2, var3, var4, var5);
      frameEdge(var0, var1, var2, var3, var4);
      var0.pose().popPose();
   }

   static void empty(GuiGraphics var0, int var1, int var2, int var3, int var4, byte var5, long var6, byte[] var8, String var9, float var10) {
      var0.enableScissor(var1, var2, var1 + var3, var2 + var4);
      backdrop(var0, var1, var2, var3, var4, var5, var6, var8);
      grain(var0, var1, var2, var3, var4, var5, var6, var10);
      var0.disableScissor();
      Font var11 = Minecraft.getInstance().font;
      var0.drawString(var11, var9, var1 + var3 / 2 - var11.width(var9) / 2, var2 + var4 / 2 - 4, -4603218, true);
      frameEdge(var0, var1, var2, var3, var4);
   }

   private static void backdrop(GuiGraphics var0, int var1, int var2, int var3, int var4, byte var5, long var6, byte[] var8) {
      int[] var9 = palette(var5);
      if (Skyline.valid(var8)) {
         measured(var0, var1, var2, var3, var4, var5, var6, var8, var9);
      } else {
         int var10 = var2 + (int)((float)var4 * 0.46F);
         var0.fillGradient(var1, var2, var1 + var3, var10, var9[0], var9[1]);
         RandomSource var11 = RandomSource.create(var6);
         int var12 = 9 + var11.nextInt(6);

         for (int var13 = 0; var13 < var12; var13++) {
            int var14 = var1 + var11.nextInt(Math.max(1, var3));
            int var15 = var10 - (int)((float)var4 * (0.1F + var11.nextFloat() * 0.3F));
            int var16 = 2 + var11.nextInt(5);
            var0.fill(var14 - var16, var15, var14 + var16, var10 + 2, var9[2]);
            if (var16 > 3) {
               var0.fill(var14 - var16 * 3, var15 - 3, var14 + var16 * 3, var15 + 5, var9[2]);
            }
         }

         var0.fill(var1, var10 - 2, var1 + var3, var10 + 1, var9[3]);
         var0.fillGradient(var1, var10, var1 + var3, var2 + var4, var9[4], var9[5]);
         int var18 = var10 + 2;
         int var19 = var1 + var3 / 2 + (int)(var11.nextFloat() * (float)var3 * 0.18F - (float)var3 * 0.09F);

         for (int var20 = var18; var20 < var2 + var4; var20 += 3) {
            float var21 = (float)(var20 - var18) / (float)Math.max(1, var2 + var4 - var18);
            int var17 = (int)((float)var3 * (0.035F + var21 * var21 * 0.26F));
            var0.fill(var19 - var17, var20, var19 + var17, var20 + 3, var9[6]);
         }
      }
   }

   private static void measured(GuiGraphics var0, int var1, int var2, int var3, int var4, byte var5, long var6, byte[] var8, int[] var9) {
      var0.fillGradient(var1, var2, var1 + var3, var2 + var4, var9[0], var9[1]);
      RandomSource var10 = RandomSource.create(var6);
      float var11 = var5 == 2 ? 1.0F : 0.0F;

      for (int var12 = 0; var12 < var3; var12++) {
         float var13 = ((float)var12 + 0.5F) / (float)var3 * 63.0F;
         int var14 = (int)var13;
         int var15 = Math.min(63, var14 + 1);
         float var16 = var13 - (float)var14;
         int var17 = var2 + Math.round(Mth.lerp(var16, Skyline.groundRow(var8, var14), Skyline.groundRow(var8, var15)) * (float)var4);
         int var18 = var2 + Math.round(Mth.lerp(var16, Skyline.canopyRow(var8, var14), Skyline.canopyRow(var8, var15)) * (float)var4);
         int var19 = Skyline.groundColour(var8, var16 < 0.5F ? var14 : var15);
         int var20 = Skyline.canopyColour(var8, var16 < 0.5F ? var14 : var15);
         if (var18 < var17) {
            var0.fill(var1 + var12, var18, var1 + var12 + 1, var17, shade(var20, 0.62F, var9[1], 0.34F, var11));
         }

         int var21 = var2 + var4 - var17;
         if (var21 > 0) {
            int var22 = Math.min(var21, 10);

            for (int var23 = 0; var23 < var22; var23++) {
               int var24 = var17 + var21 * var23 / var22;
               int var25 = var17 + var21 * (var23 + 1) / var22;
               float var26 = (float)var23 / (float)var22;
               var0.fill(var1 + var12, var24, var1 + var12 + 1, var25, shade(var19, 0.5F + var26 * 0.55F, var9[1], 0.3F * (1.0F - var26), var11));
            }
         }

         if (var18 < var17 && var10.nextInt(9) == 0) {
            var0.fill(var1 + var12, var18, var1 + var12 + 1, var18 + 1 + var10.nextInt(3), shade(var20, 0.42F, var9[1], 0.1F, var11));
         }
      }

      int var27 = var2 + Math.round(Skyline.groundRow(var8, 32) * (float)var4);
      int var28 = var1 + var3 / 2 + (int)(var10.nextFloat() * (float)var3 * 0.12F - (float)var3 * 0.06F);

      for (int var29 = var27 + 2; var29 < var2 + var4; var29 += 3) {
         float var30 = (float)(var29 - var27) / (float)Math.max(1, var2 + var4 - var27);
         int var31 = (int)((float)var3 * (0.02F + var30 * var30 * 0.24F));
         var0.fill(var28 - var31, var29, var28 + var31, var29 + 3, var9[6]);
      }
   }

   private static int shade(int var0, float var1, int var2, float var3, float var4) {
      float var5 = (float)(var0 >> 16 & 0xFF) * var1;
      float var6 = (float)(var0 >> 8 & 0xFF) * var1;
      float var7 = (float)(var0 & 0xFF) * var1;
      float var8 = (float)(var2 >> 16 & 0xFF);
      float var9 = (float)(var2 >> 8 & 0xFF);
      float var10 = (float)(var2 & 0xFF);
      var5 = var5 * (1.0F - var3) + var8 * var3;
      var6 = var6 * (1.0F - var3) + var9 * var3;
      var7 = var7 * (1.0F - var3) + var10 * var3;
      if (var4 > 0.0F) {
         float var11 = (var5 * 0.3F + var6 * 0.6F + var7 * 0.1F) * 0.62F + 14.0F;
         var5 = Mth.lerp(var4, var5, var11);
         var6 = Mth.lerp(var4, var6, var11);
         var7 = Mth.lerp(var4, var7, var11);
      }

      return 0xFF000000 | Mth.clamp((int)var5, 0, 255) << 16 | Mth.clamp((int)var6, 0, 255) << 8 | Mth.clamp((int)var7, 0, 255);
   }

   private static int[] palette(byte var0) {
      return switch (var0) {
         case 1 -> new int[]{-9741764, -4617652, -14869484, -13949671, -11911642, -13489894, -12241120};
         case 2 -> new int[]{-15658223, -14868195, -16184567, -15460332, -13749460, -12828102, -11906746};
         case 3 -> new int[]{-10788000, -8880006, -14999268, -14275547, -13419730, -14012122, -12893905};
         case 4 -> new int[]{-7497829, -4603454, -14472929, -13419984, -4801098, -3288373, -5656408};
         default -> new int[]{-9467286, -6311548, -14734823, -13879006, -11903949, -12957400, -11250129};
      };
   }

   private static void deer(GuiGraphics var0, int var1, int var2, int var3, int var4, ScoutingNetwork.Frame var5, float var6) {
      DeerTraits var7 = var5.traits();
      GameSpecies var8 = var7.species();
      DeerDraw.ensureTextures(var8);
      DeerAnimator var9 = RIGS[var8.ordinal()];
      if (var9 == null) {
         RIGS[var8.ordinal()] = var9 = new DeerAnimator(var8);
      }

      DeerAnimator.Input var10 = input(var5, var6);
      var9.update(var10);
      Matrix4f[] var11 = var9.skin;
      Matrix4f var12 = var9.model[DeerSkeleton.of(var8).head];
      if (FrontierGraphics.vanillaAnimals()) {
         McAnimalPose var13 = McAnimalPose.still(var8, var10, var7.frameLength());
         var11 = var13.skin;
         var12 = var13.head;
      }

      float var24 = Math.max(1.7F, var5.distance());
      float var14 = (float)var3 / 110.0F;
      float var15 = (float)var4 / (2.0F * (float)Math.tan(Math.toRadians(37.0)) * var24);
      float var16 = (float)var1 + (float)var3 / 2.0F + var5.bearing() * var14;
      float var17 = (float)var2 + (float)var4 * 0.46F + var15 * 1.15F;
      PoseStack var18 = var0.pose();
      var0.flush();
      var18.pushPose();
      BufferSource var21 = Minecraft.getInstance().renderBuffers().bufferSource();
      try {
         deerModel(var18, var21, var5, var7, var8, var9, var11, var12, var15, var16, var17);
      } finally {
         try {
            var21.endBatch();
         } catch (Throwable var27) {
         }

         Lighting.setupFor3DItems();
         var18.popPose();
      }
   }

   private static void deerModel(
      PoseStack var18,
      BufferSource var21,
      ScoutingNetwork.Frame var5,
      DeerTraits var7,
      GameSpecies var8,
      DeerAnimator var9,
      Matrix4f[] var11,
      Matrix4f var12,
      float var15,
      float var16,
      float var17
   ) {
      var18.translate(var16, var17, 90.0F);
      var18.scale(var15, -var15, var15);
      var18.mulPose(Axis.YP.rotationDegrees(-var5.bodyYaw()));
      var18.scale(var7.frameWidth(), var7.frameHeight(), var7.frameLength());
      float[] var19 = tint(var5);
      float[] var20 = var19 != null ? var19 : new float[]{1.0F, 1.0F, 1.0F};
      Lighting.setupForEntityInInventory();
      if (FrontierGraphics.realisticAnimals()) {
         WhitetailRenderer.realScale(var18, var8);
         float[][] var22 = DeerDraw.skin(var9, var7, 1);
         DeerDraw.body(var18, var21.getBuffer(HuntRenderTypes.sculpt(RealisticCoats.coat(var7))), 15728880, var7, 1, var22, var19);
         if (var7.buck()) {
            if (!SculptRack.draw(var18, var21, null, 15728880, var7, var9, 1, var19)) { // [1.1.9]
               com.formaworks.frontierhunts.client.rack.RackDraw.draw( // [1.1.8]
                  var18, var21.getBuffer(HuntRenderTypes.sculpt(RealisticCoats.antler())), 15728880, var7, var9.model[DeerSkeleton.of(var8).head], 6, var19
               );
            }
         }
      } else if (StylizedAnimal.active(var8) != null) {
         StylizedAnimal var25 = StylizedAnimal.active(var8);
         var25.draw(
            var18.last(), var21.getBuffer(HuntRenderTypes.sculpt(StylizedAnimal.coat(var7))), 15728880, CubeAnimal.OVERLAY, var11, var20[0], var20[1], var20[2]
         );
         if (var7.buck()) {
            WhitetailRenderer.stylizedAntlers(var18, var21, 15728880, var7, var12, var11);
         }
      } else {
         CubeAnimalData var26 = CubeAnimalData.of(var8);
         boolean var23 = FrontierGraphics.vanillaAnimals();
         CubeAnimal.draw(
            var18.last(),
            var21.getBuffer(HuntRenderTypes.sculpt(BlockyCoats.coat(var7))),
            15728880,
            CubeAnimal.OVERLAY,
            var26.bones,
            var26.data,
            var26.stride,
            var11,
            var20[0],
            var20[1],
            var20[2],
            1.0F,
            ClassicAntlers.antlerBits(var8)
         );
         ClassicAntlers.draw(
            var18.last(),
            var21.getBuffer(HuntRenderTypes.sculpt(BlockyCoats.coat(var7))),
            15728880,
            CubeAnimal.OVERLAY,
            var7,
            var11,
            var20[0],
            var20[1],
            var20[2]
         );
      }
   }

   private static float[] tint(ScoutingNetwork.Frame var0) {
      if (var0.sky() == 2) {
         float var1 = Mth.clamp(1.25F - var0.distance() / 15.0F, 0.3F, 1.05F);
         return new float[]{var1 * 0.96F, var1, var1 * 0.92F};
      } else if (var0.sky() == 1) {
         return new float[]{1.1F, 0.92F, 0.72F};
      } else if (var0.sky() == 3) {
         return new float[]{0.8F, 0.84F, 0.8F};
      } else {
         return var0.sky() == 4 ? new float[]{0.94F, 0.97F, 1.02F} : null;
      }
   }

   private static DeerAnimator.Input input(ScoutingNetwork.Frame var0, float var1) {
      DeerAnimator.Input var2 = new DeerAnimator.Input();
      var2.time = var1;
      var2.buck = var0.traits().buck();
      var2.seed = var0.traits().seed();
      var2.hitAge = 99.0F;
      switch (var0.stance()) {
         case 0:
            var2.speed = 1.15F;
            var2.alert = 0.12F;
            break;
         case 1:
            var2.graze = 1.0F;
            var2.alert = 0.08F;
            break;
         case 2:
            var2.alert = 0.95F;
            var2.stance = 1.0F;
            var2.tailRaise = 0.35F;
            var2.headYaw = Mth.clamp(Mth.wrapDegrees(180.0F - var0.bodyYaw()) * 0.0175F, -0.85F, 0.85F);
            var2.earYaw = var2.headYaw * 0.4F;
            break;
         default:
            var2.alert = 0.25F;
            var2.headYaw = Mth.clamp(Mth.wrapDegrees(180.0F - var0.bodyYaw()) * 0.0075F, -0.5F, 0.5F);
      }

      return var2;
   }

   private static void grain(GuiGraphics var0, int var1, int var2, int var3, int var4, byte var5, long var6, float var8) {
      int var9 = var5 == 2 ? 150 : 34;
      RandomSource var10 = RandomSource.create(var6 ^ (long)(var8 * 4.0F) * 2654435769L);
      int var11 = var5 == 2 ? 654311423 : 352321535;

      for (int var12 = 0; var12 < var9; var12++) {
         int var13 = var1 + var10.nextInt(Math.max(1, var3));
         int var14 = var2 + var10.nextInt(Math.max(1, var4));
         var0.fill(var13, var14, var13 + 1, var14 + 1, var11);
      }

      if (var5 == 2) {
         for (int var15 = 0; var15 < 10; var15++) {
            int var16 = (10 - var15) * 6;
            var0.fill(var1, var2 + var15, var1 + var3, var2 + var15 + 1, var16 << 24);
            var0.fill(var1, var2 + var4 - var15 - 1, var1 + var3, var2 + var4 - var15, var16 << 24);
            var0.fill(var1 + var15, var2, var1 + var15 + 1, var2 + var4, var16 << 24);
            var0.fill(var1 + var3 - var15 - 1, var2, var1 + var3 - var15, var2 + var4, var16 << 24);
         }
      }
   }

   private static void stamp(GuiGraphics var0, int var1, int var2, int var3, int var4, ScoutingNetwork.Frame var5) {
      Font var6 = Minecraft.getInstance().font;
      int var7 = var2 + var4 - 12;
      var0.fill(var1, var7, var1 + var3, var2 + var4, -1342177280);
      String var8 = var5.date().isEmpty() ? var5.clock() : shortDate(var5.date()) + "  " + var5.clock();
      var0.drawString(var6, var8, var1 + 4, var7 + 2, -2567491, false);
      String var9 = String.format(Locale.ROOT, "%.0f m  ·  %s", var5.distance(), var5.over());
      var0.drawString(var6, var9, var1 + var3 - 4 - var6.width(var9), var7 + 2, -5198954, false);
      if (var5.sky() == 2) {
         String var10 = "IR";
         var0.drawString(var6, var10, var1 + var3 / 2 - var6.width(var10) / 2, var7 + 2, -7363128, false);
      }
   }

   static String shortDate(String var0) {
      int var1 = var0.indexOf(183);
      return var1 > 0 ? var0.substring(0, var1).trim() : var0;
   }

   private static void frameEdge(GuiGraphics var0, int var1, int var2, int var3, int var4) {
      var0.fill(var1, var2, var1 + var3, var2 + 1, -15921138);
      var0.fill(var1, var2 + var4 - 1, var1 + var3, var2 + var4, -15921138);
      var0.fill(var1, var2, var1 + 1, var2 + var4, -15921138);
      var0.fill(var1 + var3 - 1, var2, var1 + var3, var2 + var4, -15921138);
   }

   private CameraPhoto() {
   }
}
