package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.expedition.StationStorage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class StationStorageRenderer implements BlockEntityRenderer<StationStorage> {
   public static final ModelResourceLocation LID = ModelResourceLocation.standalone(FrontierHunts.id("block/lodge_stores_lid"));
   private static final float HINGE_Y = 0.5625F;
   private static final float HINGE_Z = 0.9375F;
   private static final float[] REST = new float[]{0.196875F, 0.496875F, 0.796875F};

   public StationStorageRenderer(Context var1) {
   }

   public void render(StationStorage var1, float var2, PoseStack var3, MultiBufferSource var4, int var5, int var6) {
      if (var1.isBowRack()) {
         bowRack(var1, var3, var4, var5, var6);
      } else if (var1.isRack()) {
         rack(var1, var3, var4, var5, var6);
      } else if (!var1.isTrophy()) {
         lid(var1, var2, var3, var4, var5, var6);
      } else if (!var1.isEmpty()) {
         var3.pushPose();
         var3.translate(0.5, 1.34, 0.5);
         var3.mulPose(Axis.YP.rotationDegrees(180.0F));
         var3.scale(0.9F, 0.9F, 0.9F);
         Minecraft.getInstance().getItemRenderer().renderStatic(var1.getItem(0), ItemDisplayContext.FIXED, var5, var6, var3, var4, var1.getLevel(), 0);
         var3.popPose();
      }
   }

   private static float yaw(BlockState var0) {
      return switch ((Direction)var0.getValue(HorizontalDirectionalBlock.FACING)) {
         case EAST -> 90.0F;
         case SOUTH -> 180.0F;
         case WEST -> 270.0F;
         default -> 0.0F;
      };
   }

   private static void bowRack(StationStorage var0, PoseStack var1, MultiBufferSource var2, int var3, int var4) {
      BlockState var5 = var0.getBlockState();
      if (var5.hasProperty(HorizontalDirectionalBlock.FACING)) {
         var1.pushPose();
         var1.translate(0.5, 0.0, 0.5);
         var1.mulPose(Axis.YP.rotationDegrees(-yaw(var5)));
         var1.translate(-0.5, 0.0, -0.5);

         for (int var6 = 0; var6 < 2; var6++) {
            ItemStack var7 = var0.getItem(var6);
            if (!var7.isEmpty()) {
               var1.pushPose();
               var1.translate(var6 == 0 ? 0.5 : 1.5, 0.935, 0.56);
               var1.mulPose(Axis.XP.rotationDegrees(-90.0F));
               var1.mulPose(Axis.ZP.rotationDegrees(90.0F));
               DisplayPose.standBow(var1, var7, var2, var3, var4, var0.getLevel(), var6, 0.66F);
               var1.popPose();
            }
         }

         var1.popPose();
      }
   }

   private static void rack(StationStorage var0, PoseStack var1, MultiBufferSource var2, int var3, int var4) {
      BlockState var5 = var0.getBlockState();
      if (var5.hasProperty(HorizontalDirectionalBlock.FACING)) {
         var1.pushPose();
         var1.translate(0.5, 0.0, 0.5);
         var1.mulPose(Axis.YP.rotationDegrees(-yaw(var5)));

         for (int var6 = 0; var6 < 3; var6++) {
            ItemStack var7 = var0.getItem(var6);
            if (!var7.isEmpty()) {
               var1.pushPose();
               var1.translate(0.0F, REST[var6], 0.22500002F);
               DisplayPose.layFirearm(var1, var7, var2, var3, var4, var0.getLevel(), var6);
               var1.popPose();
            }
         }

         var1.popPose();
      }
   }

   private static void lid(StationStorage var0, float var1, PoseStack var2, MultiBufferSource var3, int var4, int var5) {
      BlockState var6 = var0.getBlockState();
      if (var6.hasProperty(HorizontalDirectionalBlock.FACING)) {
         float var7 = var0.openness(var1);
         var7 = 1.0F - (1.0F - var7) * (1.0F - var7) * (1.0F - var7);

         float var8 = switch ((Direction)var6.getValue(HorizontalDirectionalBlock.FACING)) {
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
         };
         Minecraft var9 = Minecraft.getInstance();
         BakedModel var10 = var9.getModelManager().getModel(LID);
         var2.pushPose();
         var2.translate(0.5, 0.5, 0.5);
         var2.mulPose(Axis.YP.rotationDegrees(-var8));
         var2.translate(-0.5, -0.5, -0.5);
         var2.translate(0.0F, 0.5625F, 0.9375F);
         var2.mulPose(Axis.XP.rotationDegrees(var7 * 90.0F));
         var2.translate(0.0F, -0.5625F, -0.9375F);
         var9.getBlockRenderer().getModelRenderer().renderModel(var2.last(), var3.getBuffer(RenderType.cutout()), var6, var10, 1.0F, 1.0F, 1.0F, var4, var5);
         var2.popPose();
      }
   }
}
