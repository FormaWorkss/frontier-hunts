package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.workshop.WorkbenchEntity;
import com.formaworks.frontierhunts.workshop.WorkshopContent;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

public final class WorkbenchModel implements BlockEntityRenderer<WorkbenchEntity> {
   private static final List<WorkbenchModel.Box> PARTS = build();
   private static final ResourceLocation WOOD = FrontierHunts.id("textures/equipment/workshop/wood_v1.png");

   public WorkbenchModel(Context var1) {
   }

   public void render(WorkbenchEntity var1, float var2, PoseStack var3, MultiBufferSource var4, int var5, int var6) {
      if (!var1.getBlockState().is((Block)WorkshopContent.ATTACHMENT_BENCH.get())) {
         var3.pushPose();
         var3.translate(0.5, 0.0, 0.5);
         var3.mulPose(Axis.YP.rotationDegrees(180.0F - ((Direction)var1.getBlockState().getValue(HorizontalDirectionalBlock.FACING)).toYRot()));
         var3.translate(-0.5, 0.0, -0.5);
         draw(var3, var4, var5);
         if (var1.getBlockState().is((Block)WorkshopContent.ATTACHMENT_BENCH.get())) {
            attachmentTools(var3, var4, var5);
         }

         var3.popPose();
      }
   }

   public static void attachmentTools(PoseStack var0, MultiBufferSource var1, int var2) {
      VertexConsumer var3 = var1.getBuffer(RenderType.entityCutoutNoCull(FieldMaterials.ATLAS));
      VertexConsumer var4 = FieldMaterials.tile(var3, 0);
      VertexConsumer var5 = FieldMaterials.flat(var3);
      ArtMesh.box(var0, var5, var2, 3166048, 0.38, 0.885, 0.54, 0.52, 0.012, 0.37);

      for (int var6 = 0; var6 < 3; var6++) {
         HuntMesh.tube(var0, var4, var2, 12963525, 0.2 + (double)var6 * 0.115, 0.9, 0.4, 0.2 + (double)var6 * 0.115, 0.9, 0.57, 0.024, 0.024, 20);
      }

      var0.pushPose();
      var0.translate(0.38, 0.93, 0.61);
      var0.scale(0.68F, 0.68F, 0.68F);
      var0.mulPose(Axis.YP.rotationDegrees(90.0F));
      FieldEquipmentModel.optic(var0, var3, var2, false);
      var0.popPose();
      ArtMesh.box(var0, var5, var2, 4482936, 0.47, 0.66, 0.029, 0.49, 0.078, 0.015);

      for (double var9 : new double[]{0.24, 0.7}) {
         HuntMesh.tube(var0, var4, var2, 13357509, var9, 0.66, 0.016, var9, 0.66, 0.02, 0.008, 0.008, 8);
      }
   }

   public static void draw(PoseStack var0, MultiBufferSource var1, int var2) {
      for (boolean var6 : new boolean[]{true, false}) {
         VertexConsumer var7 = var1.getBuffer(RenderType.entityCutout(var6 ? WOOD : WhitetailRenderer.MATERIAL));

         for (WorkbenchModel.Box var9 : PARTS) {
            if (var9.wood == var6) {
               drawBox(var0, var7, var2, var9);
            }
         }
      }
   }

   private static List<WorkbenchModel.Box> build() {
      ArrayList var0 = new ArrayList();

      for (int var1 = 0; var1 < 5; var1++) {
         box(var0, 0.0F, 12.0F, (float)var1 * 3.2F, 16.0F, 14.0F, (float)var1 * 3.2F + 3.13F, 13351058, true);
      }

      for (int var4 : new int[]{1, 13}) {
         for (int var8 : new int[]{1, 13}) {
            box(var0, (float)var4, 0.0F, (float)var8, (float)(var4 + 2), 12.0F, (float)(var8 + 2), 10982774, true);
            box(var0, (float)var4 - 0.15F, 0.0F, (float)var8 - 0.15F, (float)var4 + 2.15F, 0.55F, (float)var8 + 2.15F, 3423543, false);

            for (int var12 : new int[]{4, 10}) {
               box(var0, (float)var4 + 0.67F, (float)var12, (float)var8 - 0.07F, (float)var4 + 1.33F, (float)var12 + 0.5F, (float)var8 + 0.03F, 9015427, false);
            }
         }
      }

      for (int var38 : new int[]{2, 12}) {
         box(var0, 2.0F, 3.0F, (float)var38, 14.0F, 4.0F, (float)(var38 + 1), 11245174, true);
      }

      for (int var15 = 0; var15 < 5; var15++) {
         box(var0, 2.0F + (float)var15 * 2.4F, 4.0F, 2.0F, 4.3F + (float)var15 * 2.4F, 4.5F, 14.0F, 11574140, true);
      }

      box(var0, 1.0F, 10.0F, 1.0F, 15.0F, 12.0F, 2.0F, 10190947, true);
      box(var0, 1.0F, 10.0F, 14.0F, 15.0F, 12.0F, 15.0F, 10190947, true);

      for (int var39 : new int[]{1, 14}) {
         box(var0, (float)var39, 9.0F, 2.0F, (float)(var39 + 1), 12.0F, 14.0F, 9863268, true);
      }

      for (int var40 : new int[]{2, 8}) {
         box(var0, (float)var40, 9.2F, 2.0F, (float)var40 + 5.7F, 11.8F, 13.0F, 8546377, true);
         box(var0, (float)var40, 9.2F, 0.6F, (float)var40 + 5.7F, 11.8F, 1.2F, 11836018, true);
         box(var0, (float)(var40 + 2), 10.2F, 0.22F, (float)var40 + 3.7F, 10.55F, 0.55F, 8884609, false);

         for (float var51 : new float[]{(float)(var40 + 2), (float)var40 + 3.4F}) {
            box(var0, var51, 10.1F, 0.5F, var51 + 0.3F, 10.7F, 0.75F, 3752762, false);
         }
      }

      box(var0, 1.0F, 14.01F, 5.0F, 10.8F, 14.1F, 12.0F, 3426620, false);

      for (int var41 : new int[]{1, 10}) {
         box(var0, (float)var41, 14.11F, 5.2F, (float)var41 + 0.07F, 14.13F, 11.8F, 9144171, false);
      }

      box(var0, 11.0F, 14.0F, 2.0F, 15.0F, 14.5F, 6.8F, 4279364, false);
      box(var0, 12.0F, 14.5F, 2.7F, 14.0F, 16.5F, 5.5F, 5661779, false);
      box(var0, 11.6F, 15.1F, 1.5F, 14.4F, 15.6F, 7.0F, 8950150, false);
      box(var0, 11.6F, 15.6F, 2.0F, 14.4F, 17.3F, 2.8F, 5399631, false);
      box(var0, 11.6F, 15.6F, 4.5F, 14.4F, 17.3F, 5.3F, 5399631, false);
      box(var0, 11.5F, 17.0F, 2.7F, 14.5F, 17.5F, 2.94F, 10791841, false);
      box(var0, 11.5F, 17.0F, 4.3F, 14.5F, 17.5F, 4.55F, 10791841, false);
      box(var0, 12.82F, 15.2F, 0.8F, 13.18F, 15.6F, 2.4F, 10199705, false);
      box(var0, 11.4F, 15.2F, 0.65F, 14.6F, 15.5F, 0.95F, 10199705, false);

      for (int var42 : new int[]{11, 14}) {
         for (int var52 : new int[]{2, 6}) {
            box(var0, (float)var42 + 0.15F, 14.5F, (float)var52 + 0.15F, (float)var42 + 0.55F, 14.7F, (float)var52 + 0.55F, 10594201, false);
         }
      }

      box(var0, 2.0F, 14.0F, 1.2F, 6.4F, 14.6F, 3.7F, 11836276, true);

      for (int var20 = 0; var20 < 5; var20++) {
         for (int var28 = 0; var28 < 2; var28++) {
            box(var0, 2.45F + (float)var20 * 0.75F, 14.6F, 1.65F + (float)var28, 2.65F + (float)var20 * 0.75F, 15.15F, 1.85F + (float)var28, 11835737, false);
            box(var0, 2.47F + (float)var20 * 0.75F, 15.15F, 1.67F + (float)var28, 2.63F + (float)var20 * 0.75F, 15.5F, 1.83F + (float)var28, 9007444, false);
         }
      }

      box(var0, 2.0F, 14.1F, 12.5F, 10.0F, 14.25F, 12.65F, 9081737, false);
      box(var0, 1.0F, 14.05F, 12.3F, 2.5F, 14.4F, 12.9F, 5721143, false);
      box(var0, 11.5F, 14.0F, 9.0F, 15.0F, 14.3F, 12.7F, 7502448, false);

      for (float var43 : new float[]{11.5F, 14.8F}) {
         box(var0, var43, 14.3F, 9.0F, var43 + 0.2F, 14.8F, 12.7F, 9213323, false);
      }

      for (float var44 : new float[]{9.0F, 12.5F}) {
         box(var0, 11.5F, 14.3F, var44, 15.0F, 14.8F, var44 + 0.2F, 9213323, false);
      }

      return List.copyOf(var0);
   }

   private static void box(List<WorkbenchModel.Box> var0, float var1, float var2, float var3, float var4, float var5, float var6, int var7, boolean var8) {
      var0.add(new WorkbenchModel.Box(var1 / 16.0F, var2 / 16.0F, var3 / 16.0F, var4 / 16.0F, var5 / 16.0F, var6 / 16.0F, var7, var8));
   }

   private static void drawBox(PoseStack var0, VertexConsumer var1, int var2, WorkbenchModel.Box var3) {
      float[][] var4 = new float[][]{
         {var3.x, var3.y, var3.z},
         {var3.xx, var3.y, var3.z},
         {var3.xx, var3.yy, var3.z},
         {var3.x, var3.yy, var3.z},
         {var3.x, var3.y, var3.zz},
         {var3.xx, var3.y, var3.zz},
         {var3.xx, var3.yy, var3.zz},
         {var3.x, var3.yy, var3.zz}
      };
      int[][] var5 = new int[][]{{1, 0, 3, 2}, {4, 5, 6, 7}, {0, 4, 7, 3}, {5, 1, 2, 6}, {3, 7, 6, 2}, {0, 1, 5, 4}};
      float[][] var6 = new float[][]{{0.0F, 0.0F, -1.0F}, {0.0F, 0.0F, 1.0F}, {-1.0F, 0.0F, 0.0F}, {1.0F, 0.0F, 0.0F}, {0.0F, 1.0F, 0.0F}, {0.0F, -1.0F, 0.0F}};

      for (int var7 = 0; var7 < 6; var7++) {
         for (int var11 : var5[var7]) {
            float[] var12 = var4[var11];
            float var13 = var3.wood ? (var7 < 2 ? var12[0] : (var7 < 4 ? var12[2] : var12[0])) : 0.5F;
            float var14 = var3.wood ? (var7 < 4 ? var12[1] : var12[2]) : 0.5F;
            HuntMesh.vertex(var1, var0.last(), var2, var3.color, var12[0], var12[1], var12[2], var13, var14, var6[var7][0], var6[var7][1], var6[var7][2]);
         }
      }
   }

   private static record Box(float x, float y, float z, float xx, float yy, float zz, int color, boolean wood) {
   }
}
