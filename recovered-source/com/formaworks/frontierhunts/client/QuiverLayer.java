package com.formaworks.frontierhunts.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

final class QuiverLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
   static final float WORN_SCALE = 1.15F;
   private static final float TILT = -6.0F;
   private static final float SLANT = 30.0F;

   QuiverLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> var1) {
      super(var1);
   }

   public void render(
      PoseStack var1, MultiBufferSource var2, int var3, AbstractClientPlayer var4, float var5, float var6, float var7, float var8, float var9, float var10
   ) {
      if (!var4.isInvisible()) {
         ItemStack var11 = WornGearCache.quiver(var4);
         if (!var11.isEmpty()) {
            boolean var12 = !WornGearCache.pack(var4).isEmpty();
            // [outfitter] rest on the pack (same offset as PackLayer) or on the worn chest layer, whatever its thickness
            double var13 = var12 ? PackLayer.offset(var4) : Math.max(0.0, (com.formaworks.frontierhunts.outfitter.client.Outfits.backDepth(var4) - 0.15F) / 16.0);
            var1.pushPose();
            ((PlayerModel)this.getParentModel()).body.translateAndRotate(var1);
            worn(var1, var2, var3, var12, var13, () -> QuiverModel.draw(var11, var1, var2, var3));
            var1.popPose();
         }
      }
   }

   static void worn(PoseStack var0, MultiBufferSource var1, int var2, boolean var3, double var4, Runnable var6) {
      double var7 = 0.3449999928474426;
      double var9 = 0.052025998921394354;
      double var11 = var3 ? 0.34160000157356263 + var4 : 0.125 + var4;
      double var13 = var11 + var9 + 0.004 + var7 * Math.sin(Math.toRadians(6.0));
      var0.pushPose();
      VertexConsumer var15 = var1.getBuffer(RenderType.entityCutoutNoCull(FieldMaterials.ATLAS));
      VertexConsumer var16 = FieldMaterials.tile(var15, 7);
      VertexConsumer var17 = FieldMaterials.flat(var15);
      if (!var3) {
         sling(var0, var16, var17, var2, var4);
      }

      var0.translate(0.07, 0.36, var13);
      var0.mulPose(Axis.XP.rotationDegrees(-6.0F));
      var0.mulPose(Axis.XP.rotationDegrees(180.0F));
      var0.mulPose(Axis.ZP.rotationDegrees(30.0F));
      var0.translate(0.0, -var7, 0.0);
      var0.scale(1.15F, 1.15F, 1.15F);
      if (var3) {
         for (double var21 : new double[]{0.3, 0.74}) {
            double var23 = 0.6 * var21;
            HuntMesh.tube(var0, var16, var2, 3876374, 0.0, var23, 0.0, 0.0, var23 + 0.026, 0.0, 0.05974, 0.05974, 16);
            ArtMesh.box(var0, var16, var2, 3876374, 0.0, var23 + 0.013, 0.055240000000000004, 0.026, 0.026, 0.03);
            ArtMesh.box(var0, var17, var2, 10130308, 0.0, var23 + 0.013, 0.07124, 0.022, 0.03, 0.006);
         }
      }

      var6.run();
      var0.popPose();
   }

   private static void sling(PoseStack var0, VertexConsumer var1, VertexConsumer var2, int var3, double var4) {
      double var6 = -0.125 - var4 - 0.005;
      double var8 = 0.125 + var4 + 0.005;
      double var10 = 0.25 + var4 + 0.005;
      strap(var0, var1, var3, -0.185, -0.004, 0.215, 0.6, var6, 0.045, 0.008);
      var0.pushPose();
      var0.translate(-0.185, -0.005 - var4, 0.0);
      ArtMesh.box(var0, var1, var3, 4927517, 0.0, 0.0, 0.0, 0.045, 0.008, var8 - var6);
      var0.popPose();
      strap(var0, var1, var3, -0.185, -0.004, -0.105, 0.1, var8, 0.045, 0.008);
      var0.pushPose();
      var0.translate(var10, 0.6, (var6 + var8) * 0.5);
      ArtMesh.box(var0, var1, var3, 4927517, 0.0, 0.0, 0.0, 0.008, 0.045, var8 - var6);
      var0.popPose();
      var0.pushPose();
      var0.translate(0.215, 0.6, var6 - 0.004);
      ArtMesh.box(var0, var2, var3, 9209722, 0.0, 0.0, 0.0, 0.03, 0.034, 0.006);
      var0.popPose();
   }

   private static void strap(
      PoseStack var0, VertexConsumer var1, int var2, double var3, double var5, double var7, double var9, double var11, double var13, double var15
   ) {
      double var17 = var7 - var3;
      double var19 = var9 - var5;
      double var21 = Math.sqrt(var17 * var17 + var19 * var19);
      var0.pushPose();
      var0.translate((var3 + var7) * 0.5, (var5 + var9) * 0.5, var11);
      var0.mulPose(Axis.ZP.rotation((float)Math.atan2(-var17, var19)));
      ArtMesh.box(var0, var1, var2, 4927517, 0.0, 0.0, 0.0, var13, var21, var15);
      var0.popPose();
   }
}
