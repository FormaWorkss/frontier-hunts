package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.hunting.DeerMeshData;
import com.formaworks.frontierhunts.hunting.DeerTraits;
import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import java.util.Arrays;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;

final class MeshAntlers {
   private static final int LOD = 1;
   private static final int[][] FACES = new int[GameSpecies.values().length][];
   private static float[] pos = new float[0];
   private static float[] nrm = new float[0];

   private MeshAntlers() {
   }

   static boolean has(GameSpecies var0) {
      return var0.meshAntlers();
   }

   private static int[] faces(GameSpecies var0, DeerMeshData var1) {
      int[] var2 = FACES[var0.ordinal()];
      if (var2 == null) {
         int var3 = Math.min(1, var1.lodsMale.length - 1);
         int[] var4 = var1.faces(var3, true);
         int[] var5 = new int[var4.length];
         int var6 = 0;

         for (byte var7 = 0; var7 + 2 < var4.length; var7 += 3) {
            if (((var1.region[var4[var7]] | var1.region[var4[var7 + 1]] | var1.region[var4[var7 + 2]]) & 48) != 0) {
               var5[var6++] = var4[var7];
               var5[var6++] = var4[var7 + 1];
               var5[var6++] = var4[var7 + 2];
            }
         }

         var2 = FACES[var0.ordinal()] = Arrays.copyOf(var5, var6);
      }

      return var2;
   }

   static void draw(PoseStack var0, MultiBufferSource var1, int var2, DeerTraits var3, Matrix4f[] var4, float[] var5) {
      if (var3.buck() && var3.species().meshAntlers()) {
         drawInto(var0, var1.getBuffer(HuntRenderTypes.sculpt(RealisticCoats.coat(var3))), var2, var3, var4, var5);
      }
   }

   static void drawInto(PoseStack var0, VertexConsumer var1, int var2, DeerTraits var3, Matrix4f[] var4, float[] var5) {
      GameSpecies var6 = var3.species();
      if (var3.buck() && var6.meshAntlers()) {
         DeerMeshData var7 = DeerMeshData.of(var6);
         int[] var8 = faces(var6, var7);
         if (var8.length != 0) {
            if (pos.length < var7.vertices * 3) {
               pos = new float[var7.vertices * 3];
               nrm = new float[var7.vertices * 3];
            }

            int var9 = Math.min(1, var7.lodsMale.length - 1);
            var7.skin(var9, var4, var3.neckGirth(), var3.headScale(), DeerDraw.meshRackScale(var3), pos, nrm);
            Pose var10 = var0.last();
            Matrix4f var11 = var10.pose();
            boolean var12 = var5 != null || var7.lodMode[var9] == 1;
            float var13 = var5 == null ? 1.0F : var5[0];
            float var14 = var5 == null ? 1.0F : var5[1];
            float var15 = var5 == null ? 1.0F : var5[2];

            for (int var19 : var8) {
               int var20 = var19 * 3;
               float var21 = var13;
               float var22 = var14;
               float var23 = var15;
               if (var5 == null && var7.lodMode[var9] == 1) {
                  var21 = var7.col[var19 * 3];
                  var22 = var7.col[var19 * 3 + 1];
                  var23 = var7.col[var19 * 3 + 2];
               }

               var1.addVertex(var11, pos[var20], pos[var20 + 1], pos[var20 + 2])
                  .setColor(var21, var22, var23, 1.0F)
                  .setUv(var12 ? 0.5F : var7.uv[var19 * 2], var12 ? 0.5F : var7.uv[var19 * 2 + 1])
                  .setOverlay(OverlayTexture.NO_OVERLAY)
                  .setLight(var2)
                  .setNormal(var10, nrm[var20], nrm[var20 + 1], nrm[var20 + 2]);
            }
         }
      }
   }
}
