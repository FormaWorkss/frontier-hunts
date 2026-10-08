package com.formaworks.frontierhunts.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

final class FieldPrimitiveBow {
   /**
    * [archery2] Arrow line in model coordinates (the model is built with the arrow on +x; the first-person view mirrors it
    * to the left of the riser for a right-handed archer): resting on the shelf just above the grip wrap, against the
    * leather strike plate on the riser's flank. The string's nocking point is on the same line.
    */
   static final double RISER_HALF = 0.029;
   static final double SHELF_Y = 0.064;
   static final double ARROW_X = RISER_HALF + 0.0012 + 0.0041;
   static final double ARROW_Y = SHELF_Y + 0.0016 + 0.0041;

   static double nockZ(float var0) {
      return 0.09 + (double)var0 * 0.66;
   }

   static void draw(PoseStack var0, MultiBufferSource var1, int var2, float var3) {
      draw(var0, var1, var2, var3, 0.0F);
   }

   static void draw(PoseStack var0, MultiBufferSource var1, int var2, float var3, float var4) {
      FilteredFieldTexture.ensure(FieldMaterials.ATLAS);
      draw(var0, var1.getBuffer(RenderType.entityCutoutNoCull(FieldMaterials.ATLAS)), var2, var3, var4);
   }

   /** [archery2] The mesh itself, into an atlas consumer (also used by the offline view harness). */
   static void draw(PoseStack var0, VertexConsumer var9, int var2, float var3, float var4) {
      double var5 = FieldBows.cut(var4);
      double var7 = nockZ(var3);
      VertexConsumer var10 = FieldMaterials.tile(var9, 1);
      VertexConsumer var11 = FieldMaterials.tile(var9, 7);
      VertexConsumer var12 = FieldMaterials.flat(var9);
      ArtMesh.profile(
         var0,
         var10,
         var2,
         14076586,
         0.029,
         0.006,
         new double[][]{
            {-0.14, -0.018},
            {-0.085, -0.026},
            {-0.018, -0.022},
            {0.04, -0.032},
            {0.14, -0.015},
            {0.135, 0.021},
            {0.04, 0.03},
            {-0.02, 0.016},
            {-0.085, 0.027},
            {-0.14, 0.018}
         }
      );

      for (int var16 : new int[]{-1, 1}) {
         HuntMesh.bowLimb(var0, var10, var2, var16, var3);
         double var17 = (double)var16 * (0.74 - (double)var3 * 0.065);
         double var19 = 0.035 + (double)var3 * 0.21;
         HuntMesh.tube(var0, var12, var2, 5327670, -0.009, var17, var19, 0.009, var17, var19, 0.007, 0.007, 16);
         double var21 = var7 > var5 ? Math.max(0.0, (var5 - var19) / (var7 - var19)) : 1.0;
         HuntMesh.tube(
            var0,
            var12,
            var2,
            12894115,
            0.0,
            var17,
            var19,
            ARROW_X * var21,
            var17 + (ARROW_Y - var17) * var21,
            var19 + (var7 - var19) * var21,
            0.0013,
            var21 < 1.0 ? 0.0 : 0.0013,
            8
         );

         for (int var23 = 0; var23 < 5; var23++) {
            double var24 = (double)(var23 + 1) * 0.006;
            double var26 = var17 + (ARROW_Y - var17) * var24;
            double var28 = var19 + (var7 - var19) * var24;
            HuntMesh.tube(var0, var12, var2, 3423280, -0.002, var26, var28, 0.002, var26, var28, 0.0025, 0.0025, 8);
         }

         for (int var35 = 0; var35 < 6; var35++) {
            HuntMesh.tube(
               var0,
               var12,
               var2,
               10323288,
               -0.024,
               (double)var16 * (0.113 + (double)var35 * 0.004),
               -0.009,
               0.024,
               (double)var16 * (0.113 + (double)var35 * 0.004),
               -0.009,
               8.0E-4,
               8.0E-4,
               6
            );
         }
      }

      for (int var30 = 0; var30 < 13; var30++) {
         for (int var31 = 0; var31 < 14; var31++) {
            double var32 = (double)var31 * Math.PI / 7.0;
            double var33 = (double)(var31 + 1) * Math.PI / 7.0;
            double var34 = -0.078 + (double)var30 * 0.0115;
            HuntMesh.tube(
               var0,
               var11,
               var2,
               var30 % 2 == 0 ? 13223347 : 11448987,
               0.03 * Math.cos(var32),
               var34,
               0.028 * Math.sin(var32),
               0.03 * Math.cos(var33),
               var34 + 0.001,
               0.028 * Math.sin(var33),
               0.0017,
               0.0017,
               5
            );
         }
      }

      // [archery2] arrow shelf: a leather-padded ledge at the top of the grip, and the strike plate the arrow rides against
      ArtMesh.box(var0, var11, var2, 9073218, RISER_HALF + 0.0055, SHELF_Y - 0.0035, -0.006, 0.011, 0.007, 0.052);
      ArtMesh.box(var0, var11, var2, 11112546, RISER_HALF + 0.0055, SHELF_Y + 0.0006, -0.006, 0.0105, 0.0016, 0.046);
      ArtMesh.box(var0, var11, var2, 7887940, RISER_HALF + 0.0006, SHELF_Y + 0.019, -0.008, 0.0012, 0.034, 0.05);
      if (var7 < var5) {
         // brass nocking point just above the nock on the string
         HuntMesh.tube(var0, var12, var2, 12362604, ARROW_X, ARROW_Y + 0.0052, var7, ARROW_X, ARROW_Y + 0.0112, var7, 0.0024, 0.0024, 10);
      }
   }

   private FieldPrimitiveBow() {
   }
}
