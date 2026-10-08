package com.formaworks.frontierhunts.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;
import com.formaworks.frontierhunts.outfitter.client.OutfitModel;
import com.formaworks.frontierhunts.outfitter.client.Outfits;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;

final class PackLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
   private static final float SX = 1.16F;
   private static final float SY = 1.17F;
   private static final float SZ = 0.82F;
   private static final double DEPTH_OUT = 0.13529999881982804;
   private static final double CENTRE = 0.272299998819828;
   static final double OUTER = 0.40759999763965604;

   /**
    * [outfitter] How far (blocks) the pack moves back so its frame rests on the worn chest layer plus the harness straps
    * instead of sinking into a fur coat or floating off bare skin (was 0 bare / 0.06 with anything in the chest slot).
    */
   static double offset(AbstractClientPlayer p) {
      return Math.max(0.0, (Outfits.backDepth(p) - 0.1F) / 16.0);
   }

   private static final float[] HARNESS_DEPTHS = {0.3F, 0.5F, 0.7F, 0.9F, 1.1F};

   /** [outfitter] Shoulder straps, sternum strap and padded hip belt over whatever is worn (rigid on the body). */
   private void harness(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p) {
      float depth = Outfits.backDepth(p);
      int i = 0;
      while (i < HARNESS_DEPTHS.length - 1 && HARNESS_DEPTHS[i] < depth - 0.001F) {
         i++;
      }
      OutfitModel m = Outfits.model("extra/harness_" + i, false);
      m.follow(this.getParentModel());
      m.setAllVisible(false);
      m.body.visible = true;
      m.showAll(); // [clothing]
      m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(Outfits.texture(m.outfit))), light,
         LivingEntityRenderer.getOverlayCoords(p, 0.0F), -1);
   }

   PackLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> var1) {
      super(var1);
   }

   public void render(
      PoseStack var1, MultiBufferSource var2, int var3, AbstractClientPlayer var4, float var5, float var6, float var7, float var8, float var9, float var10
   ) {
      if (!var4.isInvisible()) {
         if (!WornGearCache.pack(var4).isEmpty()) {
            double var11 = offset(var4);
            harness(var1, var2, var3, var4);
            var1.pushPose();
            ((PlayerModel)this.getParentModel()).body.translateAndRotate(var1);
            var1.translate(0.0, 0.335, 0.272299998819828 + var11);
            var1.mulPose(Axis.XP.rotationDegrees(180.0F));
            var1.scale(1.16F, 1.17F, 0.82F);
            FieldPackModel.draw("hunter_pack", ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, var1, var2, var3);
            var1.popPose();
         }
      }
   }
}
