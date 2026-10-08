package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.expedition.ShootingTarget;
import com.formaworks.frontierhunts.expedition.TargetFace;
import com.formaworks.frontierhunts.hunting.ArrowSupply;
import com.formaworks.frontierhunts.hunting.ArrowTip;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class TargetRenderer implements BlockEntityRenderer<TargetFace> {
   public TargetRenderer(Context var1) {
   }

   public void render(TargetFace var1, float var2, PoseStack var3, MultiBufferSource var4, int var5, int var6) {
      BlockState var7 = var1.getBlockState();
      if (var7.getBlock() instanceof ShootingTarget) {
         List var8 = var1.hits();
         if (!var8.isEmpty()) {
            Direction var9 = (Direction)var7.getValue(ShootingTarget.FACING);
            float var10 = 180.0F - var9.getOpposite().toYRot();
            FilteredFieldTexture.ensure(FieldMaterials.ATLAS);
            VertexConsumer var11 = var4.getBuffer(RenderType.entityCutoutNoCull(FieldMaterials.ATLAS));
            VertexConsumer var12 = FieldMaterials.flat(var11);
            Vec3 var13 = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            boolean var14 = var13.distanceToSqr(
                  (double)var1.getBlockPos().getX() + 0.5, (double)var1.getBlockPos().getY() + 0.5, (double)var1.getBlockPos().getZ() + 0.5
               )
               < 81.0;

            for (TargetFace.Hit var16 : var8) {
               Vec3 var17 = TargetFace.world(BlockPos.ZERO, var9, var16.u(), var16.v(), 0.0);
               var3.pushPose();
               var3.translate(var17.x, var17.y, var17.z);
               var3.mulPose(Axis.YP.rotationDegrees(var10));
               if (var16.kind() == 0) {
                  long var18 = var16.at() * 31L + (long)Float.floatToIntBits(var16.u());
                  var3.mulPose(Axis.YP.rotationDegrees(-var16.yaw() + (float)(Math.floorMod(var18 >> 3, 7) - 3) * 0.5F));
                  var3.mulPose(Axis.XP.rotationDegrees(-var16.pitch() + (float)(Math.floorMod(var18, 7) - 3) * 0.5F));
                  double var20 = Math.max(0.25, Math.cos(Math.toRadians(Math.hypot((double)var16.pitch(), (double)var16.yaw()))));
                  var3.translate(0.0, 0.0, 0.29 + (1.0 - var20) * 0.16 + (double)Math.floorMod(var18 >> 6, 5) * 0.004);
                  if (var14) {
                     ArrowTip var22 = var16.tip() < 0 ? ArrowTip.FIELD_POINT : ArrowTip.byOrdinal(var16.tip());
                     FieldArrowModel.draw(var3, var11, var5, false, false, new ArrowSupply.Shot(var22, var16.primitive()), false);
                  } else {
                     shaft(var3, var12, var5, var16.primitive());
                  }
               } else {
                  boolean var23 = var16.kind() == 2;
                  double var19 = var23 ? 0.0095 : 0.017;
                  HuntMesh.ell(var3, var12, var5, var23 ? 1316879 : 2760727, 0.0, 0.0, 0.004, var19, var19, 0.0025);
                  if (!var23) {
                     HuntMesh.ell(var3, var12, var5, 4010788, 0.0, 0.0, 0.0045, var19 * 0.55, var19 * 0.55, 0.002);
                  }
               }

               var3.popPose();
            }
         }
      }
   }

   private static void shaft(PoseStack var0, VertexConsumer var1, int var2, boolean var3) {
      int var4 = var3 ? 9072456 : 2830124;
      HuntMesh.tube(var0, var1, var2, var4, 0.0, 0.0, -0.38, 0.0, 0.0, 0.34, 0.0042, 0.0042, 6);
      HuntMesh.tube(var0, var1, var2, 13222827, 0.0, 0.0, 0.34, 0.0, 0.0, 0.38, 0.0055, 0.005, 6);
      ArtMesh.box(var0, var1, var2, 12371118, 0.008, 0.0, 0.26, 0.016, 0.002, 0.05);
   }

   public int getViewDistance() {
      return 28;
   }

   public AABB getRenderBoundingBox(TargetFace var1) {
      return new AABB(var1.getBlockPos()).inflate(1.1);
   }

   public boolean shouldRenderOffScreen(TargetFace var1) {
      return false;
   }
}
