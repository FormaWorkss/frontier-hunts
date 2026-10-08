package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.expedition.TrophyMount;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.resources.ResourceLocation;

public final class TrophyMountRenderer extends EntityRenderer<TrophyMount> {
   public TrophyMountRenderer(Context var1) {
      super(var1);
   }

   public ResourceLocation getTextureLocation(TrophyMount var1) {
      return FieldMaterials.ATLAS;
   }

   public void render(TrophyMount var1, float var2, float var3, PoseStack var4, MultiBufferSource var5, int var6) {
      if (!var1.trophy().isEmpty()) {
         var4.pushPose();
         var4.mulPose(Axis.YP.rotationDegrees(180.0F - var1.getDirection().toYRot()));
         TrophyDisplay.draw(var1.trophy(), var4, var5, var6, false);
         var4.popPose();
         super.render(var1, var2, var3, var4, var5, var6);
      }
   }
}
