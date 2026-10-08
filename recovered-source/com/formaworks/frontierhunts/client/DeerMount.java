package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.hunting.DeerAnimator;
import com.formaworks.frontierhunts.hunting.DeerSkeleton;
import com.formaworks.frontierhunts.hunting.DeerTraits;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;
import org.joml.Vector3f;

final class DeerMount {
   static final float BACK = -0.1F;
   static final float Y0 = 0.6F;
   static final float Z0 = -0.45F;
   static final float SLOPE = 0.514F;
   private static final Map<List<Object>, DeerMount.Mount> CACHE = new LinkedHashMap<List<Object>, DeerMount.Mount>(16, 0.75F, true) {
      @Override
      protected boolean removeEldestEntry(Entry<List<Object>, DeerMount.Mount> var1) {
         return this.size() > 24;
      }
   };

   static void clear() {
      RealisticDeerMount.clear();
      synchronized (CACHE) {
         CACHE.clear();
      }
   }

   static void draw(PoseStack var0, MultiBufferSource var1, int var2, DeerTraits var3, boolean var4) {
      if (FrontierGraphics.realisticAnimals()) {
         RealisticDeerMount.draw(var0, var1, var2, var3, var4);
      } else {
         DeerDraw.ensureTextures(var3.species());
         DeerMount.Mount var5;
         synchronized (CACHE) {
            boolean var7 = FrontierGraphics.vanillaAnimals();
            int var8 = var7 ? 1 : (StylizedAnimal.active(var3.species()) != null ? 2 : 0);
            var5 = CACHE.computeIfAbsent(List.of(var3, var4 ? 1 : 0, var8), var2x -> build(var3, var7));
         }

         StylizedAnimal var16 = StylizedAnimal.active(var3.species());
         boolean var17 = FrontierGraphics.vanillaAnimals();
         CubeAnimalData var18 = CubeAnimalData.of(var3.species());
         float var9 = var3.coatWarmth();
         float var10 = var3.coatShade();
         float var11 = Math.min(1.0F, var10 * (1.0F + var9 * 0.06F));
         float var12 = Math.min(1.0F, var10);
         float var13 = Math.min(1.0F, var10 * (1.0F - var9 * 0.08F));
         var0.pushPose();
         var0.scale(var5.scale, var5.scale, var5.scale);
         var0.translate(-var5.cx, -var5.cy + 0.045F / var5.scale, -var5.cz - 0.055F / var5.scale);
         if (var16 != null) {
            var16.draw(
               var0.last(),
               var1.getBuffer(HuntRenderTypes.sculpt(StylizedAnimal.coat(var3))),
               var2,
               CubeAnimal.OVERLAY,
               var5.skin,
               var11,
               var12,
               var13,
               var16.mountStart,
               var16.triangles
            );
         } else {
            CubeAnimal.draw(
               var0.last(),
               var1.getBuffer(HuntRenderTypes.sculpt(BlockyCoats.coat(var3))),
               var2,
               CubeAnimal.OVERLAY,
               var18.bones,
               var18.data,
               var18.stride,
               var5.skin,
               var11,
               var12,
               var13,
               1.0F,
               ~var5.keep | ClassicAntlers.antlerBits(var3.species())
            );
            ClassicAntlers.draw(
               var0.last(), var1.getBuffer(HuntRenderTypes.sculpt(BlockyCoats.coat(var3))), var2, CubeAnimal.OVERLAY, var3, var5.skin, 1.0F, 1.0F, 1.0F
            );
         }

         VertexConsumer var14 = var1.getBuffer(HuntRenderTypes.sculpt(DeerDraw.MATERIAL));
         CubeAnimal.quadBox(
            var0.last(),
            var14,
            var2,
            CubeAnimal.OVERLAY,
            new Vector3f(var5.capX - var5.capW, var5.capY - var5.capH, var5.capZ),
            new Vector3f(var5.capW * 2.0F, 0.0F, 0.0F),
            new Vector3f(0.0F, var5.capH * 2.0F, 0.0F),
            new Vector3f(0.0F, 0.0F, 0.012F),
            0.46F,
            0.46F,
            0.54F,
            0.54F,
            0.3F,
            0.23F,
            0.17F
         );
         if (var3.buck() && var16 != null) {
            WhitetailRenderer.stylizedAntlers(var0, var1, var2, var3, var5.head, var5.skin);
         }

         var0.popPose();
      }
   }

   private static DeerMount.Mount build(DeerTraits var0, boolean var1) {
      DeerSkeleton var2 = DeerSkeleton.of(var0.species());
      DeerAnimator var3 = new DeerAnimator(var0.species());
      DeerAnimator.Input var4 = new DeerAnimator.Input();
      var4.buck = var0.buck();
      var4.headYaw = 0.24F;
      var4.headPitch = 0.1F;
      var4.alert = 0.35F;
      var3.update(var4);
      CubeAnimalData var5 = CubeAnimalData.of(var0.species());
      long var6 = 0L;
      int[] var8 = new int[]{var2.neck0, var2.neck1, var2.neck2, var2.head, var2.earL, var2.earR, var2.jaw};

      for (int var9 = 0; var9 < var5.bones.length && var9 < 64; var9++) {
         for (int var13 : var8) {
            if (var13 >= 0 && var5.bones[var9] == var13) {
               var6 |= 1L << var9;
               break;
            }
         }
      }

      DeerMount.Mount var24 = new DeerMount.Mount();
      var24.skin = var3.skin;
      var24.head = var3.model[var2.head];
      if (var1) {
         McAnimalPose var25 = McAnimalPose.still(var0.species(), var4, var0.frameLength());
         var24.skin = var25.skin;
         var24.head = var25.head;
      }

      var24.keep = var6;
      float var26 = Float.MAX_VALUE;
      float var27 = Float.MAX_VALUE;
      float var28 = Float.MAX_VALUE;
      float var31 = -Float.MAX_VALUE;
      float var14 = -Float.MAX_VALUE;
      float var15 = -Float.MAX_VALUE;
      Vector3f var16 = new Vector3f();

      for (int var17 = 0; var17 < var5.bones.length && var17 < 64; var17++) {
         if ((var6 & 1L << var17) != 0L) {
            int var18 = var17 * var5.stride;
            Matrix4f var19 = var24.skin[var5.bones[var17]];

            for (int var20 = 0; var20 < 8; var20++) {
               float var21 = var20 != 1 && var20 != 2 && var20 != 5 && var20 != 6 ? 0.0F : 1.0F;
               float var22 = var20 != 2 && var20 != 3 && var20 != 6 && var20 != 7 ? 0.0F : 1.0F;
               float var23 = var20 >= 4 ? 1.0F : 0.0F;
               var16.set(
                  var5.data[var18] + var5.data[var18 + 3] * var21 + var5.data[var18 + 6] * var22 + var5.data[var18 + 9] * var23,
                  var5.data[var18 + 1] + var5.data[var18 + 4] * var21 + var5.data[var18 + 7] * var22 + var5.data[var18 + 10] * var23,
                  var5.data[var18 + 2] + var5.data[var18 + 5] * var21 + var5.data[var18 + 8] * var22 + var5.data[var18 + 11] * var23
               );
               var19.transformPosition(var16);
               var26 = Math.min(var26, var16.x);
               var31 = Math.max(var31, var16.x);
               var27 = Math.min(var27, var16.y);
               var14 = Math.max(var14, var16.y);
               var28 = Math.min(var28, var16.z);
               var15 = Math.max(var15, var16.z);
            }
         }
      }

      if (var26 > var31) {
         var28 = -0.1F;
         var27 = -0.1F;
         var26 = -0.1F;
         var15 = 0.1F;
         var14 = 0.1F;
         var31 = 0.1F;
      }

      StylizedAnimal var32 = var1 ? null : StylizedAnimal.active(var0.species());
      if (var32 != null) {
         float[] var33 = var32.mountBounds(var24.skin);
         var26 = var33[0];
         var27 = var33[1];
         var28 = var33[2];
         var31 = var33[3];
         var14 = var33[4];
         var15 = var33[5];
      }

      float var34 = Math.max(0.05F, var14 - var27);
      var24.scale = 0.6F / var34;
      var24.cx = (var26 + var31) * 0.5F;
      var24.cy = (var27 + var14) * 0.5F;
      var24.cz = var15;
      var24.capX = var24.cx;
      var24.capY = (var27 + var14) * 0.5F + (var14 - var27) * 0.18F;
      var24.capZ = var15 - 0.004F;
      var24.capW = Math.max(0.03F, (var31 - var26) * 0.46F);
      var24.capH = Math.max(0.03F, (var14 - var27) * 0.3F);
      return var24;
   }

   private DeerMount() {
   }

   static final class Mount {
      Matrix4f[] skin;
      Matrix4f head;
      long keep;
      float scale = 1.0F;
      float cx;
      float cy;
      float cz;
      float capX;
      float capY;
      float capZ;
      float capW;
      float capH;
   }
}
