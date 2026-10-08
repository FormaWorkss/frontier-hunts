package com.formaworks.frontierhunts.camps.client;

import com.formaworks.frontierhunts.camps.CampPostBlock;
import com.formaworks.frontierhunts.camps.CampPostBlockEntity;
import com.formaworks.frontierhunts.camps.CampService;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Camp Post: paints the camp name on the carved plaque and flies a cloth pennant in the camp's colour from the pole.
 * The pennant is a small tessellated sheet (17 x 5 vertices) that ripples from the pole to the fly end; the stitched
 * rack emblem is a second, untinted layer on both faces.
 */
public final class CampPostRenderer implements BlockEntityRenderer<CampPostBlockEntity> {
   private static final ResourceLocation FLAG = ResourceLocation.fromNamespaceAndPath("frontierhunts", "textures/entity/camp_flag.png");
   private static final ResourceLocation EMBLEM = ResourceLocation.fromNamespaceAndPath("frontierhunts", "textures/entity/camp_flag_emblem.png");
   private static final int COLS = 16;
   private static final int ROWS = 4;
   private static final float LENGTH = 1.3F;
   private static final float HEIGHT = 0.72F;
   private final Font font;
   private final float[] px = new float[(COLS + 1) * (ROWS + 1)];
   private final float[] py = new float[(COLS + 1) * (ROWS + 1)];
   private final float[] pz = new float[(COLS + 1) * (ROWS + 1)];

   public CampPostRenderer(BlockEntityRendererProvider.Context ctx) {
      this.font = ctx.getFont();
   }

   @Override
   public void render(CampPostBlockEntity be, float partial, PoseStack pose, MultiBufferSource buf, int light, int overlay) {
      if (!be.getBlockState().hasProperty(CampPostBlock.FACING)) {
         return;
      }
      Direction facing = be.getBlockState().getValue(CampPostBlock.FACING);
      pose.pushPose();
      pose.translate(0.5, 0.0, 0.5);
      pose.mulPose(Axis.YP.rotationDegrees(-TrophyBoardRenderer.yRot(facing)));
      boolean bound = be.camp != null && !be.name.isEmpty();
      // plaque text (plaque front at z = 5/16)
      double z = 4.96 / 16.0 - 0.5;
      String title = bound ? be.name : "UNCLAIMED CAMP";
      int cream = 0xFFF0E2BE;
      WorldText.draw(pose, buf, this.font, title, 0.0, 12.15 / 16.0, z, WorldText.fit(this.font, title, 0.0088F, 0.8), cream, light, 0);
      String sub = bound ? CampService.RANKS[Math.clamp(be.tier, 0, 4)].toUpperCase() + "  ·  " + be.members + (be.members == 1 ? " HUNTER" : " HUNTERS") : "use the post to found a camp";
      WorldText.draw(pose, buf, this.font, sub, 0.0, 9.55 / 16.0, z, WorldText.fit(this.font, sub, 0.0046F, 0.8), 0xFFC9B68A, light, 0);
      // pennant
      int flagLight = be.getLevel() == null ? light : LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().above());
      float time = be.getLevel() == null ? 0.0F : (be.getLevel().getGameTime() % 24000L) + partial;
      int argb = bound ? DyeColor.byId(Math.floorMod(be.color, 16)).getTextureDiffuseColor() | 0xFF000000 : 0xFFD8CFB6;
      this.buildSheet(time, be.getBlockPos());
      this.sheet(pose, buf.getBuffer(RenderType.entityCutoutNoCull(FLAG)), argb, flagLight, 0.0F);
      if (bound) {
         VertexConsumer em = buf.getBuffer(RenderType.entityCutoutNoCull(EMBLEM));
         this.sheet(pose, em, 0xFFFFFFFF, flagLight, 0.0035F);
         this.sheet(pose, em, 0xFFFFFFFF, flagLight, -0.0035F);
      }
      pose.popPose();
   }

   /** Cloth positions: rooted at the pole rings, rippling toward the fly end, sagging slightly with distance. */
   private void buildSheet(float t, BlockPos pos) {
      float phase = (pos.getX() * 13 + pos.getZ() * 7) % 100 * 0.37F;
      float x0 = 10.4F / 16.0F - 0.5F;
      float top = 1.0F + 14.6F / 16.0F;
      for (int i = 0; i <= COLS; i++) {
         float u = i / (float)COLS;
         float amp = 0.075F * (float)Math.pow(u, 0.85);
         for (int j = 0; j <= ROWS; j++) {
            float v = j / (float)ROWS;
            float wave = (float)Math.sin(u * 7.2 - t * 0.21 + phase) * amp + (float)Math.sin(u * 13.0 - t * 0.37 + v * 2.2 + phase) * amp * 0.28F;
            float flutter = (float)Math.sin(t * 0.13 + v * 3.1 + u * 4.0 + phase) * 0.012F * u;
            int k = i * (ROWS + 1) + j;
            // the fly end shortens a little as the cloth ripples out of plane
            this.px[k] = x0 + u * LENGTH * (1.0F - 0.03F * Math.abs((float)Math.sin(t * 0.21 + phase)));
            this.py[k] = top - v * HEIGHT - 0.055F * u * u;
            this.pz[k] = wave + flutter;
         }
      }
   }

   private void sheet(PoseStack pose, VertexConsumer vc, int argb, int light, float offset) {
      PoseStack.Pose last = pose.last();
      for (int i = 0; i < COLS; i++) {
         for (int j = 0; j < ROWS; j++) {
            int a = i * (ROWS + 1) + j;
            int b = (i + 1) * (ROWS + 1) + j;
            int c = (i + 1) * (ROWS + 1) + j + 1;
            int d = i * (ROWS + 1) + j + 1;
            float u0 = i / (float)COLS;
            float u1 = (i + 1) / (float)COLS;
            float v0 = j / (float)ROWS;
            float v1 = (j + 1) / (float)ROWS;
            // normal from the slope of the ripple along the cloth
            float dzdx = (this.pz[b] - this.pz[a]) / Math.max(1.0E-4F, this.px[b] - this.px[a]);
            float inv = (float)(1.0 / Math.sqrt(1.0 + dzdx * dzdx));
            float nx = -dzdx * inv;
            float nz = inv;
            this.vertex(vc, last, a, u0, v0, argb, light, offset, nx, nz);
            this.vertex(vc, last, b, u1, v0, argb, light, offset, nx, nz);
            this.vertex(vc, last, c, u1, v1, argb, light, offset, nx, nz);
            this.vertex(vc, last, d, u0, v1, argb, light, offset, nx, nz);
         }
      }
   }

   private void vertex(VertexConsumer vc, PoseStack.Pose last, int k, float u, float v, int argb, int light, float offset, float nx, float nz) {
      vc.addVertex(last, this.px[k], this.py[k], this.pz[k] + offset)
         .setColor(argb)
         .setUv(u, v)
         .setOverlay(OverlayTexture.NO_OVERLAY)
         .setLight(light)
         .setNormal(last, nx, 0.0F, nz);
   }

   @Override
   public int getViewDistance() {
      return 96;
   }

   @Override
   public AABB getRenderBoundingBox(CampPostBlockEntity be) {
      BlockPos p = be.getBlockPos();
      return new AABB(Vec3.atLowerCornerOf(p).subtract(1.6, 0.0, 1.6), Vec3.atLowerCornerOf(p).add(2.6, 2.2, 2.6));
   }
}
