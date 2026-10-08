package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.camp.CampingTent;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CampingTentRenderer implements BlockEntityRenderer<CampingTent.Anchor> {
   static final ResourceLocation FABRICS = FrontierHunts.id("textures/equipment/shelters/camp_fabrics_v1.png");
   private static final ResourceLocation CAMO = FrontierHunts.id("textures/equipment/shelters/camouflage_v1.png");
   private static final float[] DUCK = new float[]{0.009765625F, 0.009765625F, 0.49023438F, 0.49023438F};
   private static final float[] NYLON = new float[]{0.5097656F, 0.009765625F, 0.9902344F, 0.49023438F};
   private static final float[] FULL = new float[]{0.0F, 0.0F, 1.0F, 1.0F};
   private static final double FLAP_WIDEN = 1.04;
   private static final double FLAP_INSET = 0.012;
   private static final double WINDOW_OVERLAP_LOW = 0.03;
   private static final double WINDOW_OVERLAP_HIGH = 0.05;
   private static final double[][] WOOD_RINGS = new double[][]{{0.03, 2.25, 2.25}, {1.15, 2.2, 2.2}, {2.06, 2.07, 2.07}};
   private static final double[][] DOME_RINGS = new double[][]{
      {0.03, 2.25, 2.25}, {0.8, 2.23, 2.23}, {1.4, 2.12, 2.12}, {1.85, 1.98, 1.98}, {2.06, 1.91, 1.91}
   };
   private static final CampingTentRenderer.Style WOODLAND = new CampingTentRenderer.Style(
      CAMO, FULL, 13093819, 10793108, false, WOOD_RINGS, WOOD_RINGS, WOOD_RINGS
   );
   private static final CampingTentRenderer.Style DOME = new CampingTentRenderer.Style(null, FULL, 12371885, 10793108, true, DOME_RINGS, DOME_RINGS, DOME_RINGS);

   static CampingTentRenderer.Style style(String var0) {
      return switch (var0) {
         case "canvas_wall_tent" -> new CampingTentRenderer.Style(
         FABRICS,
         DUCK,
         15853778,
         14273712,
         false,
         new double[][]{{0.03, 2.3, 2.3}, {2.06, 2.3, 2.3}},
         new double[][]{{1.35, 2.3, 2.3}, {1.85, 2.3, 2.3}},
         new double[][]{{1.35, 2.3, 2.3}, {1.85, 2.3, 2.3}}
      );
         case "bell_tent" -> new CampingTentRenderer.Style(
         FABRICS,
         DUCK,
         15985366,
         14471348,
         true,
         new double[][]{{0.03, 2.44, 2.44}, {0.62, 2.44, 2.44}, {0.95, 2.29, 2.29}, {1.35, 2.1, 2.1}, {1.85, 1.88, 1.88}, {2.06, 1.78, 1.78}},
         new double[][]{{0.03, 2.44, 2.44}, {0.62, 2.44, 2.44}, {0.95, 2.29, 2.29}, {1.35, 2.1, 2.1}, {1.85, 1.88, 1.88}, {2.06, 1.78, 1.78}},
         new double[][]{{0.03, 2.44, 2.44}, {0.62, 2.44, 2.44}, {0.95, 2.29, 2.29}, {1.35, 2.1, 2.1}, {1.85, 1.88, 1.88}, {2.06, 1.78, 1.78}}
      );
         case "family_cabin_tent" -> new CampingTentRenderer.Style(
         FABRICS,
         NYLON,
         12830924,
         9080981,
         false,
         new double[][]{{0.03, 2.42, 2.42}, {0.45, 2.4, 2.4}, {1.35, 2.35, 2.35}, {1.85, 2.32, 2.32}, {2.06, 2.3, 2.3}},
         new double[][]{{0.03, 2.42, 2.42}, {0.45, 2.4, 2.4}, {1.35, 2.35, 2.35}, {1.85, 2.32, 2.32}, {2.06, 2.3, 2.3}},
         new double[][]{{0.03, 2.42, 2.42}, {0.45, 2.4, 2.4}, {1.35, 2.35, 2.35}, {1.85, 2.32, 2.32}, {2.06, 2.3, 2.3}}
      );
         case "pup_tent" -> new CampingTentRenderer.Style(
         FABRICS,
         DUCK,
         13219467,
         11574642,
         false,
         new double[][]{{0.03, 2.3, 2.3}, {2.06, 2.3, 2.3}},
         new double[][]{{0.03, 2.47, 2.3}, {0.65, 2.26, 2.3}, {1.35, 1.96, 2.3}, {1.85, 1.74, 2.3}, {2.06, 1.64, 2.3}},
         new double[][]{{1.35, 2.3, 2.3}, {1.85, 2.3, 2.3}}
      );
         case "trail_dome_tent" -> DOME;
         default -> WOODLAND;
      };
   }

   public CampingTentRenderer(Context var1) {
   }

   public static ModelResourceLocation setupModel(boolean var0) {
      return setupModel(var0 ? "trail_dome_tent" : "woodland_camp_tent");
   }

   public static ModelResourceLocation setupModel(String var0) {
      return ModelResourceLocation.standalone(FrontierHunts.id("block/" + var0 + "_setup"));
   }

   @Override
   public int getViewDistance() {
      return 64;
   }

   public AABB getRenderBoundingBox(CampingTent.Anchor var1) {
      BlockPos var2 = CampingTent.origin(var1.getBlockPos(), var1.getBlockState());
      return new AABB(Vec3.atLowerCornerOf(var2.offset(-2, 0, -2)), Vec3.atLowerCornerOf(var2.offset(3, 3, 3)));
   }

   public void render(CampingTent.Anchor var1, float var2, PoseStack var3, MultiBufferSource var4, int var5, int var6) {
      BlockState var7 = var1.getBlockState();
      if (var7.getBlock() instanceof CampingTent var8) {
         Direction var39 = var7.getValue(CampingTent.FACING);
         BlockPos var10 = CampingTent.offset(0, var39);
         var3.pushPose();
         var3.translate((double)(-var10.getX()) + 0.5, 0.0, (double)(-var10.getZ()) + 0.5);
         var3.mulPose(Axis.YP.rotationDegrees(-var39.toYRot() + 180.0F));
         var3.translate(-0.5, 0.0, -0.5);
         if (!var7.getValue(CampingTent.READY)) {
            float var11 = var1.setup(var2);
            float var12 = FieldWeaponMesh.smooth(((double)var11 - 0.12) / 0.72);
            float var13 = FieldWeaponMesh.smooth((double)var11 / 0.35);
            var3.pushPose();
            var3.translate(0.5, 0.0, 0.5);
            var3.scale(0.35F + 0.65F * var13, 0.035F + 0.965F * var12, 0.35F + 0.65F * var13);
            var3.translate(-0.5, 0.0, -0.5);
            Minecraft var14 = Minecraft.getInstance();
            BakedModel var15 = var14.getModelManager().getModel(setupModel(var8.design));
            var14.getBlockRenderer()
               .getModelRenderer()
               .renderModel(var3.last(), var4.getBuffer(RenderType.cutout()), var7, var15, 1.0F, 1.0F, 1.0F, var5, var6);
            var3.popPose();
         } else {
            CampingTentRenderer.Style var40 = style(var8.design);
            float var41 = var1.open(var2);
            double var42 = 0.03 + 2.03 * (double)var41;
            VertexConsumer var43 = var40.texture() == null
               ? FieldMaterials.tile(var4.getBuffer(RenderType.entityCutoutNoCull(FieldMaterials.ATLAS)), 6)
               : var4.getBuffer(RenderType.entityCutoutNoCull(var40.texture()));
            float[] var16 = var40.region();
            double var17 = var40.round() ? Math.sqrt(0.75) : 1.0;
            double var19 = var40.round() ? Math.sqrt(0.9744) : 1.0;
            double[][] var21 = var40.front();

            for (int var22 = 0; var22 < var21.length - 1; var22++) {
               double var23 = Math.max(var42, var21[var22][0]);
               double var25 = var21[var22 + 1][0];
               if (!(var23 >= var25)) {
                  quad(var3, var43, var5, var40.cloth(), var16, var21, var23, var25, var17, var19, 0.0F, 1.0F, 0.03, 2.03);
               }
            }

            double var44 = value(var21, var42, 1);
            double var24 = value(var21, var42, 2);
            double var26 = 0.013 + 0.04 * (double)var41;
            HuntMesh.tube(
               var3,
               var43,
               var5,
               var40.roll(),
               0.5 - var24 * 0.32 * var17,
               var42,
               0.5 - var44 * var19 - 0.025,
               0.5 + var24 * 0.32 * var17,
               var42,
               0.5 - var44 * var19 - 0.025,
               var26,
               var26,
               16
            );

            for (int var28 = 0; var28 < 3; var28++) {
               var3.pushPose();
               var3.translate(0.5, 0.0, 0.5);
               var3.mulPose(Axis.YP.rotationDegrees((float)(-90 * (var28 + 1))));
               var3.translate(-0.5, 0.0, -0.5);
               float var29 = var1.windowOpen(var28, var2);
               double var30 = 1.35 + 0.5 * (double)var29;
               double[][] var32 = var40.window(var28);

               // Flaps overlap the opening a little on every side so no sky shows along the seams from inside.
               double lo = var30 - WINDOW_OVERLAP_LOW;
               double hi = 1.85 + WINDOW_OVERLAP_HIGH;
               double from = lo;
               for (int var33 = 0; var33 <= var32.length; var33++) {
                  double to = var33 < var32.length ? var32[var33][0] : hi;
                  if (to <= from) {
                     continue;
                  }

                  to = Math.min(to, hi);
                  quad(var3, var43, var5, var40.cloth(), var16, var32, from, to, var17, var19, 0.34F, 0.66F, 0.0, 2.94);
                  from = to;
                  if (from >= hi) {
                     break;
                  }
               }

               double var45 = value(var32, var30, 1);
               double var35 = value(var32, var30, 2);
               double var37 = 0.012 + 0.026 * (double)var29;
               HuntMesh.tube(
                  var3,
                  var43,
                  var5,
                  var40.roll(),
                  0.5 - var35 * 0.32 * var17,
                  var30,
                  0.5 - var45 * var19 - 0.025,
                  0.5 + var35 * 0.32 * var17,
                  var30,
                  0.5 - var45 * var19 - 0.025,
                  var37,
                  var37,
                  12
               );
               var3.popPose();
            }
         }

         var3.popPose();
      }
   }

   private static void quad(
      PoseStack var0,
      VertexConsumer var1,
      int var2,
      int var3,
      float[] var4,
      double[][] var5,
      double var6,
      double var8,
      double var10,
      double var12,
      float var14,
      float var15,
      double var16,
      double var18
   ) {
      double var20 = value(var5, var6, 1);
      double var22 = value(var5, var8, 1);
      double var24 = value(var5, var6, 2);
      double var26 = value(var5, var8, 2);
      double wide = 0.32 * FLAP_WIDEN;
      double in = -0.025 + FLAP_INSET;
      double[][] var28 = new double[][]{
         {0.5 - var24 * wide * var10, var6, 0.5 - var20 * var12 + in},
         {0.5 + var24 * wide * var10, var6, 0.5 - var20 * var12 + in},
         {0.5 + var26 * wide * var10, var8, 0.5 - var22 * var12 + in},
         {0.5 - var26 * wide * var10, var8, 0.5 - var22 * var12 + in}
      };

      for (int var29 = 0; var29 < 4; var29++) {
         float var30 = var29 != 1 && var29 != 2 ? var14 : var15;
         float var31 = (float)Math.clamp(1.0 - (var28[var29][1] - var16) / var18, 0.0, 1.0);
         HuntMesh.vertex(
            var1,
            var0.last(),
            var2,
            var3,
            (float)var28[var29][0],
            (float)var28[var29][1],
            (float)var28[var29][2],
            var4[0] + (var4[2] - var4[0]) * var30,
            var4[1] + (var4[3] - var4[1]) * var31,
            0.0F,
            0.0F,
            -1.0F
         );
      }
   }

   static double value(double[][] var0, double var1, int var3) {
      if (var1 <= var0[0][0]) {
         return var0[0][var3];
      } else {
         for (int var4 = 1; var4 < var0.length; var4++) {
            if (var1 <= var0[var4][0]) {
               double var5 = (var1 - var0[var4 - 1][0]) / Math.max(1.0E-9, var0[var4][0] - var0[var4 - 1][0]);
               return var0[var4 - 1][var3] + var5 * (var0[var4][var3] - var0[var4 - 1][var3]);
            }
         }

         return var0[var0.length - 1][var3];
      }
   }

   static record Style(ResourceLocation texture, float[] region, int cloth, int roll, boolean round, double[][] front, double[][] side, double[][] back) {
      double[][] window(int var1) {
         return var1 == 1 ? this.back : this.side;
      }
   }
}
