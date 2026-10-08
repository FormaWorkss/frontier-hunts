package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.hunting.TrackClue;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.resources.ResourceLocation;

public final class TrackClueRenderer extends EntityRenderer<TrackClue> {
   public TrackClueRenderer(Context var1) {
      super(var1);
   }

   public ResourceLocation getTextureLocation(TrackClue var1) {
      return WhitetailRenderer.MATERIAL;
   }

   public void render(TrackClue var1, float var2, float var3, PoseStack var4, MultiBufferSource var5, int var6) {
      if (!(this.entityRenderDispatcher.distanceToSqr(var1) > 784.0)) {
         VertexConsumer var7 = var5.getBuffer(RenderType.entityCutout(this.getTextureLocation(var1)));
         var4.pushPose();
         var4.mulPose(Axis.YP.rotationDegrees(180.0F - var1.getYRot()));
         if (var1.blood()) {
            for (int var8 = 0; var8 < 5; var8++) {
               HuntMesh.ellipsoid(
                  var4,
                  var7,
                  var6,
                  5384996,
                  0,
                  Math.sin((double)(var8 * 17)) * 0.13,
                  0.0,
                  Math.cos((double)(var8 * 7)) * 0.13,
                  0.016 + (double)var8 * 0.003,
                  0.002,
                  0.025
               );
            }
         } else {
            for (int var11 : new int[]{-1, 1}) {
               for (int var15 : new int[]{-1, 1}) {
                  HuntMesh.ellipsoid(var4, var7, var6, 4472365, 0, (double)var11 * 0.1 + (double)var15 * 0.022, 0.0, (double)var11 * 0.065, 0.019, 0.002, 0.064);
               }
            }
         }

         var4.popPose();
      }
   }
}
