package com.formaworks.frontierhunts.sled.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.sled.SledContent;
import com.formaworks.frontierhunts.sled.SledEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * [1.1.8, 1.1.9 the real model] The toboggan in the world is the same 3D model as the item: birch slats, a front
 * steamed round into a curl and lashed back, crossbars, side rails on posts with a rope run along them. It lies along
 * the snow - pitched down the fall line, rolled across it - and rides the eased height, so it glides over the block
 * steps instead of dropping down them.
 */
public final class SledRenderer extends EntityRenderer<SledEntity> {
   static final ResourceLocation TEX = FrontierHunts.id("textures/entity/sled.png");

   @EventBusSubscriber(modid = FrontierHunts.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
   public static final class Setup {
      private Setup() {
      }

      @SubscribeEvent
      public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
         e.registerEntityRenderer(SledContent.SLED.get(), SledRenderer::new);
      }
   }

   private final ItemRenderer items;
   private ItemStack stack;

   public SledRenderer(EntityRendererProvider.Context ctx) {
      super(ctx);
      this.items = ctx.getItemRenderer();
      this.shadowRadius = 0.8F;
   }

   @Override
   public ResourceLocation getTextureLocation(SledEntity e) {
      return TEX;
   }

   @Override
   public void render(SledEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
      if (this.stack == null) {
         this.stack = new ItemStack(SledContent.SLED_ITEM.get());
      }
      ps.pushPose();
      ps.translate(0.0, e.visY(pt) - Mth.lerp(pt, e.yo, e.getY()), 0.0);
      ps.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.rotLerp(pt, e.yRotO, e.getYRot())));
      // pitch and roll about the middle of the deck
      ps.translate(0.0, 0.08, 0.0);
      ps.mulPose(Axis.XP.rotationDegrees(Mth.lerp(pt, e.visPitchO, e.visPitch)));
      ps.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(pt, e.visRollO, e.visRoll)));
      ps.translate(0.0, -0.08, 0.0);
      // [1.2.2] a one-rider toboggan, about two blocks long, its runners on the snow
      ps.scale(1.1F, 1.1F, 1.1F);
      ps.translate(0.0, 0.5 - 0.02, 0.0);
      this.items.renderStatic(this.stack, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, ps, buffers, e.level(), e.getId());
      ps.popPose();
      super.render(e, yaw, pt, ps, buffers, light);
   }

}
