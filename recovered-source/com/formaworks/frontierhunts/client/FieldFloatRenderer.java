package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.workshop.FieldFloat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class FieldFloatRenderer extends EntityRenderer<FieldFloat> {
   public FieldFloatRenderer(Context var1) {
      super(var1);
   }

   public ResourceLocation getTextureLocation(FieldFloat var1) {
      return WhitetailRenderer.MATERIAL;
   }

   public void render(FieldFloat var1, float var2, float var3, PoseStack var4, MultiBufferSource var5, int var6) {
      VertexConsumer var7 = var5.getBuffer(RenderType.entityCutoutNoCull(WhitetailRenderer.MATERIAL));
      var4.pushPose();
      var4.mulPose(Axis.ZP.rotationDegrees((float)Math.clamp(var1.getDeltaMovement().x * 100.0, -18.0, 18.0)));
      HuntMesh.ellipsoid(var4, var7, var6, 12564891, 2, 0.0, -0.005, 0.0, 0.024, 0.07, 0.024);
      HuntMesh.tube(var4, var7, var6, 7367245, 0.0, -0.125, 0.0, 0.0, -0.06, 0.0, 0.006, 0.006, 12);
      HuntMesh.tube(var4, var7, var6, 11684133, 0.0, 0.06, 0.0, 0.0, 0.185, 0.0, 0.0045, 0.0045, 12);
      HuntMesh.tube(var4, var7, var6, 14734244, 0.0, 0.087, 0.0, 0.0, 0.117, 0.0, 0.0047, 0.0047, 12);
      var4.popPose();
      Entity var8 = var1.owner();
      if (var8 != null) {
         Vec3 var9 = var1.getPosition(var3);
         Vec3 var10 = FishingClient.tip(var8, var3).subtract(var9);
         double var11 = Math.min(1.2, Math.max(0.015, (double)var1.line() - var10.length()) * 0.22);
         double var13 = 9.0E-4;

         for (int var15 = 0; var15 < 36; var15++) {
            double var16 = (double)var15 / 36.0;
            double var18 = (double)(var15 + 1) / 36.0;
            Vec3 var20 = var10.scale(var16).add(0.0, -0.12 * (1.0 - var16) - 4.0 * var11 * var16 * (1.0 - var16), 0.0);
            Vec3 var21 = var10.scale(var18).add(0.0, -0.12 * (1.0 - var18) - 4.0 * var11 * var18 * (1.0 - var18), 0.0);
            HuntMesh.tube(var4, var7, var6, 9148046, var20.x, var20.y, var20.z, var21.x, var21.y, var21.z, var13, var13, 4);
         }
      }

      super.render(var1, var2, var3, var4, var5, var6);
   }

   public boolean shouldRender(FieldFloat var1, Frustum var2, double var3, double var5, double var7) {
      return var1.distanceToSqr(Minecraft.getInstance().player) < 4096.0;
   }
}
