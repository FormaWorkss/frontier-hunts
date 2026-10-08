package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.workshop.WorkshopContent;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

public final class WorkshopItemRenderer extends BlockEntityWithoutLevelRenderer {
   private static WorkshopItemRenderer instance;

   public WorkshopItemRenderer() {
      super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
   }

   public void renderByItem(ItemStack var1, ItemDisplayContext var2, PoseStack var3, MultiBufferSource var4, int var5, int var6) {
      if (!var1.is(WorkshopContent.BENCH.asItem()) && !var1.is(WorkshopContent.ATTACHMENT_BENCH.asItem())) {
         VertexConsumer var7 = var4.getBuffer(RenderType.entityCutout(WhitetailRenderer.MATERIAL));
         var3.pushPose();
         var3.translate(0.5, 0.5, 0.5);
         if (var1.is((Item)WorkshopContent.ROD.get())) {
            float var8 = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
            float var9 = var2.firstPerson() ? FishingClient.flex(var8) : 0.0F;
            rod(var3, var7, var5, var9);
            FishingClient.capture(var3, var2, var9);
         } else {
            FieldMountedOptic.loose(var3, var4, var5, "four_power_optic");
         }

         var3.popPose();
      } else {
         WorkbenchModel.draw(var3, var4, var5);
         if (var1.is(WorkshopContent.ATTACHMENT_BENCH.asItem())) {
            WorkbenchModel.attachmentTools(var3, var4, var5);
         }
      }
   }

   private static void rod(PoseStack var0, VertexConsumer var1, int var2, float var3) {
      for (int var4 = 0; var4 < 30; var4++) {
         double var5 = (double)var4 / 30.0;
         double var7 = (double)(var4 + 1) / 30.0;
         double var9 = -0.7 + 1.8 * var5;
         double var11 = -0.7 + 1.8 * var7;
         HuntMesh.tube(var0, var1, var2, 2436649, 0.0, var9, curve(var9, var3), 0.0, var11, curve(var11, var3), 0.012 - 0.01 * var5, 0.012 - 0.01 * var7, 12);
      }

      for (double[] var24 : new double[][]{{-0.7, -0.49}, {-0.35, -0.17}}) {
         HuntMesh.tube(var0, var1, var2, 11836273, 0.0, var24[0], 0.0, 0.0, var24[1], 0.0, 0.025, 0.023, 20);

         for (double var8 = var24[0] + 0.014; var8 < var24[1]; var8 += 0.025) {
            ring(var0, var1, var2, 0.0, var8, 0.0, 0.024, 7.0E-4, 8943704);
         }
      }

      HuntMesh.tube(var0, var1, var2, 3161142, 0.0, -0.49, 0.0, 0.0, -0.35, 0.0, 0.017, 0.017, 20);

      for (double var25 : new double[]{-0.69, -0.485, -0.358, -0.175}) {
         ring(var0, var1, var2, 0.0, var25, 0.0, 0.025, 0.002, 6845035);
      }

      HuntMesh.tube(var0, var1, var2, 4147781, 0.0, -0.42, 0.0, 0.0, -0.46, 0.082, 0.01, 0.014, 12);
      HuntMesh.ellipsoid(var0, var1, var2, 3424831, 2, 0.0, -0.46, 0.087, 0.036, 0.048, 0.029);
      HuntMesh.tube(var0, var1, var2, 9606795, 0.0, -0.44, 0.09, 0.0, -0.37, 0.09, 0.034, 0.034, 24);

      for (int var15 = 0; var15 < 12; var15++) {
         ring(var0, var1, var2, 0.0, -0.433 + (double)var15 * 0.0045, 0.09, 0.033, 0.001, 11905408);
      }

      ring(var0, var1, var2, 0.0, -0.37, 0.09, 0.038, 0.0025, 7965826);

      for (int var16 = 0; var16 < 22; var16++) {
         double var20 = (double)var16 * Math.PI / 22.0;
         double var26 = (double)(var16 + 1) * Math.PI / 22.0;
         HuntMesh.tube(
            var0,
            var1,
            var2,
            10332833,
            Math.cos(var20) * 0.045,
            -0.404 + Math.sin(var20) * 0.058,
            0.09,
            Math.cos(var26) * 0.045,
            -0.404 + Math.sin(var26) * 0.058,
            0.09,
            0.0015,
            0.0015,
            6
         );
      }

      HuntMesh.tube(var0, var1, var2, 7043696, -0.026, -0.46, 0.087, -0.073, -0.475, 0.095, 0.004, 0.004, 8);
      HuntMesh.tube(var0, var1, var2, 2699307, -0.073, -0.475, 0.095, -0.073, -0.49, 0.12, 0.009, 0.009, 12);

      for (double var27 : new double[]{-0.05, 0.19, 0.43, 0.68, 0.91, 1.1}) {
         double var28 = 0.018 - (var27 + 0.05) * 0.011;
         double var29 = curve(var27, var3);
         HuntMesh.tube(var0, var1, var2, 5792860, 0.0, var27 - 0.027, curve(var27 - 0.027, var3), 0.0, var27, var29 + 0.019, 0.002, 0.002, 6);
         ring(var0, var1, var2, 0.0, var27, var29 + 0.019, var28, 0.0015, 7569011);
         ring(var0, var1, var2, 0.0, var27 - 0.025, curve(var27 - 0.025, var3), 0.011 - (var27 + 0.05) * 0.007, 0.0015, 5326909);
      }
   }

   private static double curve(double var0, float var2) {
      return (double)var2 * Math.pow(Math.max(0.0, var0 / 1.1), 2.0);
   }

   private static void ring(PoseStack var0, VertexConsumer var1, int var2, double var3, double var5, double var7, double var9, double var11, int var13) {
      for (int var14 = 0; var14 < 24; var14++) {
         double var15 = (double)var14 * Math.PI / 12.0;
         double var17 = (double)(var14 + 1) * Math.PI / 12.0;
         HuntMesh.tube(
            var0,
            var1,
            var2,
            var13,
            var3 + Math.cos(var15) * var9,
            var5,
            var7 + Math.sin(var15) * var9,
            var3 + Math.cos(var17) * var9,
            var5,
            var7 + Math.sin(var17) * var9,
            var11,
            var11,
            6
         );
      }
   }

   public static final class Extensions implements IClientItemExtensions {
      public BlockEntityWithoutLevelRenderer getCustomRenderer() {
         if (WorkshopItemRenderer.instance == null) {
            WorkshopItemRenderer.instance = new WorkshopItemRenderer();
         }

         return WorkshopItemRenderer.instance;
      }
   }
}
