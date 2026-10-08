package com.formaworks.frontierhunts.landscape.stand;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers;

public final class BowStandRenderer implements BlockEntityRenderer<BowStandBlockEntity> {
   public BowStandRenderer(Context var1) {
   }

   public void render(BowStandBlockEntity var1, float var2, PoseStack var3, MultiBufferSource var4, int var5, int var6) {
      ItemRenderer var7 = Minecraft.getInstance().getItemRenderer();
      Direction var8 = (Direction)var1.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
      var3.pushPose();
      var3.translate(0.5, 0.0, 0.5);
      var3.mulPose(Axis.YP.rotationDegrees(-var8.toYRot() + 180.0F));
      ItemStack var9 = var1.get(0);
      if (!var9.isEmpty()) {
         var3.pushPose();
         var3.translate(0.0, 1.075, 0.1734);
         var3.scale(0.85F, 0.85F, 0.85F);
         var7.renderStatic(var9, ItemDisplayContext.FIXED, var5, var6, var3, var4, var1.getLevel(), (int)var1.getBlockPos().asLong());
         var3.popPose();
      }

      ItemStack var10 = var1.get(1);
      if (!var10.isEmpty()) {
         var3.pushPose();
         var3.translate(0.0, 0.42, -0.24);
         var3.mulPose(Axis.ZP.rotationDegrees(9.0F));
         var3.scale(0.55F, 0.55F, 0.55F);
         var7.renderStatic(var10, ItemDisplayContext.FIXED, var5, var6, var3, var4, var1.getLevel(), (int)var1.getBlockPos().asLong() + 1);
         var3.popPose();
      }

      var3.popPose();
   }

   public AABB getRenderBoundingBox(BowStandBlockEntity var1) {
      BlockPos var2 = var1.getBlockPos();
      return new AABB(
         (double)var2.getX() - 0.5,
         (double)var2.getY(),
         (double)var2.getZ() - 0.5,
         (double)var2.getX() + 1.5,
         (double)(var2.getY() + 3),
         (double)var2.getZ() + 1.5
      );
   }

   @EventBusSubscriber(
      modid = "frontierhunts",
      bus = Bus.MOD,
      value = {Dist.CLIENT}
   )
   public static final class Setup {
      @SubscribeEvent
      public static void renderers(RegisterRenderers var0) {
         var0.registerBlockEntityRenderer((BlockEntityType)BowStandContent.ENTITY.get(), BowStandRenderer::new);
      }
   }
}
