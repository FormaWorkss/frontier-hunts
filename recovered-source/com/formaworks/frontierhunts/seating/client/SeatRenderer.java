package com.formaworks.frontierhunts.seating.client;

import com.formaworks.frontierhunts.seating.SeatEntity;
import com.formaworks.frontierhunts.seating.SeatKind;
import com.formaworks.frontierhunts.seating.SwivelChairBlock;
import com.formaworks.frontierhunts.seating.TowerChairBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * [onboard2] The seat entity itself is invisible. For the swivel chairs it draws the seat top (cushion and back),
 * turned with the sitter's body (interpolated per frame), while the block model shows only the base - so the chair
 * swivels with the hunter instead of the hunter turning inside a fixed chair. Same model, lighting and atlas as the
 * block, entity-cutout (shader packs treat it like any block entity).
 */
public class SeatRenderer extends EntityRenderer<SeatEntity> {
   public SeatRenderer(EntityRendererProvider.Context ctx) {
      super(ctx);
      this.shadowRadius = 0.0F;
   }

   @Override
   public boolean shouldRender(SeatEntity e, Frustum frustum, double x, double y, double z) {
      return e.kind().rotatingTop && frustum.isVisible(new AABB(e.origin()).inflate(0.25, 0.5, 0.25));
   }

   @Override
   public void render(SeatEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
      SeatKind kind = e.kind();
      if (!kind.rotatingTop) {
         return;
      }
      BlockPos o = e.origin();
      BlockState st = e.level().getBlockState(o);
      if (!(st.getBlock() instanceof SwivelChairBlock) || !st.getValue(SwivelChairBlock.OCCUPIED)) {
         return; // the block model still shows the whole chair
      }
      BakedModel model = Minecraft.getInstance().getModelManager().getModel(kind == SeatKind.TOWER_CHAIR ? SeatClient.TOWER_TOP : SeatClient.BLIND_TOP);
      if (model == null || model == Minecraft.getInstance().getModelManager().getMissingModel()) {
         return;
      }
      float body = e.getYRot();
      Entity rider = e.getFirstPassenger();
      if (rider instanceof LivingEntity le) {
         body = Mth.rotLerp(partial, le.yBodyRotO, le.yBodyRot);
      }
      double dy = 0.0;
      if (st.getBlock() instanceof TowerChairBlock) {
         dy = ((st.getValue(TowerChairBlock.RAISED) ? SeatKind.TOWER_RAISE : 0.0) + (st.getValue(TowerChairBlock.SUNK) ? SeatKind.SUNK : 0.0)) / 16.0;
      }
      double ex = Mth.lerp(partial, e.xo, e.getX()), ey = Mth.lerp(partial, e.yo, e.getY()), ez = Mth.lerp(partial, e.zo, e.getZ());
      pose.pushPose();
      pose.translate(o.getX() + 0.5 - ex, o.getY() - ey + dy, o.getZ() + 0.5 - ez);
      pose.mulPose(Axis.YP.rotationDegrees(180.0F - body));
      pose.translate(-0.5, 0.0, -0.5);
      Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(Sheets.cutoutBlockSheet()), st, model,
         1.0F, 1.0F, 1.0F, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, Sheets.cutoutBlockSheet());
      pose.popPose();
   }

   @SuppressWarnings("deprecation")
   @Override
   public ResourceLocation getTextureLocation(SeatEntity e) {
      return TextureAtlas.LOCATION_BLOCKS;
   }
}
