package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.HuntContent;
import com.formaworks.frontierhunts.hunting.ArrowSupply;
import com.formaworks.frontierhunts.hunting.ArrowTip;
import com.formaworks.frontierhunts.hunting.ArrowTipItem;
import com.formaworks.frontierhunts.hunting.QuiverItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent.Post;
import net.neoforged.neoforge.client.event.RenderLivingEvent.Pre;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
public final class HuntEquipmentRenderer extends BlockEntityWithoutLevelRenderer {
   static LivingEntity renderedHolder;
   private static HuntEquipmentRenderer instance;

   public HuntEquipmentRenderer() {
      super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
   }

   @SubscribeEvent
   public static void before(Pre<?, ?> var0) {
      renderedHolder = var0.getEntity();
   }

   @SubscribeEvent
   public static void after(Post<?, ?> var0) {
      renderedHolder = null;
   }

   @Override
   public void renderByItem(ItemStack var1, ItemDisplayContext var2, PoseStack var3, MultiBufferSource var4, int var5, int var6) {
      VertexConsumer var7 = var4.getBuffer(RenderType.entityCutout(WhitetailRenderer.MATERIAL));
      LivingEntity var8 = var2.firstPerson()
         ? Minecraft.getInstance().player
         : (var2 != ItemDisplayContext.THIRD_PERSON_LEFT_HAND && var2 != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND ? null : renderedHolder);
      float var9 = var8 != null && var8.isUsingItem() && var8.getUseItem() == var1
         ? Math.min(1.0F, ((float)var8.getTicksUsingItem() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true)) / 26.0F)
         : 0.0F;
      var3.pushPose();
      var3.translate(0.5, 0.5, 0.5);
      if (var1.is(HuntContent.FIELD_BOW.get())) {
         boolean var11;
         label87: {
            if (var8 instanceof Player var12 && var12.getCooldowns().isOnCooldown(HuntContent.FIELD_BOW.get())) {
               var11 = true;
               break label87;
            }

            var11 = false;
         }

         if (var11 && var8 instanceof Player var13) {
            double var14 = (double)(
               10.0F
                  * (
                     1.0F
                        - var13.getCooldowns()
                           .getCooldownPercent(HuntContent.FIELD_BOW.get(), Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true))
                  )
            );
            var3.translate(0.0, 0.0, Math.sin(var14 * 2.8) * Math.exp(-var14 * 0.5) * 0.015);
         }

         FieldPrimitiveBow.draw(var3, var4, var5, var9);
         if (!var11) {
            FieldArrowModel.nocked(
               var3,
               var4.getBuffer(RenderType.entityCutoutNoCull(FieldMaterials.ATLAS)),
               var5,
               FieldPrimitiveBow.ARROW_X, // [archery2] on the shelf
               FieldPrimitiveBow.ARROW_Y,
               FieldPrimitiveBow.nockZ(var9),
               false,
               false,
               FieldArrowModel.shotFor((LivingEntity)var8)
            );
         }
      } else if (ArrowTip.arrow(var1)) {
         var3.mulPose(Axis.XP.rotationDegrees(35.0F));
         FieldArrowModel.draw(
            var3,
            var4.getBuffer(RenderType.entityCutoutNoCull(FieldMaterials.ATLAS)),
            var5,
            false,
            false,
            new ArrowSupply.Shot(ArrowTip.of(var1), ArrowTip.primitiveShaft(var1)),
            false
         );
      } else if (var1.getItem() instanceof ArrowTipItem var10) {
         FieldArrowModel.loose(var3, var4.getBuffer(RenderType.entityCutoutNoCull(FieldMaterials.ATLAS)), var5, var10.tip, var2 == ItemDisplayContext.GUI);
      } else if (var1.getItem() instanceof QuiverItem) {
         QuiverModel.item(var1, var3, var4, var5, var2 == ItemDisplayContext.GUI);
      } else if (var1.is(HuntContent.FIELD_KNIFE.get())) {
         KnifeModels.drawItem(false, var3, var4, var5);
      } else if (var1.is(HuntContent.JOURNAL.get())) {
         FieldBookModel.draw(false, var3, var4, var5);
      } else if (var1.is(HuntContent.SKINNING_TOOL.get())) {
         KnifeModels.drawItem(true, var3, var4, var5);
      } else if (var1.is(HuntContent.WHITETAIL_TROPHY.get())) {
         TrophyDisplay.draw(var1, var3, var4, var5, var2 == ItemDisplayContext.GUI);
      } else if (var1.is(HuntContent.DEER_HIDE.get())) {
         VertexConsumer var21 = var4.getBuffer(RenderType.entityCutout(WhitetailRenderer.FUR));

         for (int var23 = 0; var23 < 4; var23++) {
            HuntMesh.ellipsoid(var3, var21, var5, 13875350, 1, 0.0, -0.07 + (double)var23 * 0.04, 0.0, 0.32, 0.035, 0.22);
         }

         for (int var15 : new int[]{-1, 1}) {
            for (int var19 : new int[]{-1, 1}) {
               HuntMesh.ellipsoid(var3, var21, var5, 13875350, 1, (double)var15 * 0.21, -0.1, (double)var19 * 0.2, 0.1, 0.018, 0.13);
            }
         }
      } else {
         int var22 = var1.is(HuntContent.COOKED_VENISON.get()) ? 7884858 : 8469307;
         HuntMesh.ellipsoid(var3, var7, var5, var22, 2, 0.0, 0.0, 0.0, 0.26, 0.105, 0.17);

         for (int var25 = 0; var25 < 6; var25++) {
            HuntMesh.tube(
               var3,
               var7,
               var5,
               var1.is(HuntContent.COOKED_VENISON.get()) ? 4141093 : 13147538,
               -0.15 + (double)var25 * 0.056,
               0.1,
               -0.1,
               -0.11 + (double)var25 * 0.045,
               0.1,
               0.1,
               0.003,
               0.004,
               4
            );
         }
      }

      var3.popPose();
   }

   private static void bow(PoseStack var0, VertexConsumer var1, int var2, float var3) {
      HuntMesh.ellipsoid(var0, var1, var2, 6507056, 2, 0.0, 0.0, 0.0, 0.033, 0.135, 0.034);

      for (int var7 : new int[]{-1, 1}) {
         HuntMesh.bowLimb(var0, var1, var2, var7, var3);
         double var8 = (double)var7 * (0.74 - (double)var3 * 0.065);
         double var10 = 0.035 + (double)var3 * 0.21;
         HuntMesh.ellipsoid(var0, var1, var2, 3160367, 1, 0.0, var8, var10, 0.009, 0.014, 0.006);
         HuntMesh.tube(var0, var1, var2, 12104348, 0.0, var8, var10, 0.036, 0.02, 0.09 + (double)var3 * 0.49, 0.0012, 0.0012, 5);
      }

      for (int var12 = 0; var12 < 14; var12++) {
         for (int var13 = 0; var13 < 12; var13++) {
            double var14 = (double)var13 * Math.PI / 6.0;
            double var15 = (double)(var13 + 1) * Math.PI / 6.0;
            double var16 = -0.082 + (double)var12 * 0.0115;
            HuntMesh.tube(
               var0,
               var1,
               var2,
               var12 % 2 == 0 ? 3686960 : 4344374,
               0.031 * Math.cos(var14),
               var16,
               0.033 * Math.sin(var14),
               0.031 * Math.cos(var15),
               var16 + 7.0E-4,
               0.033 * Math.sin(var15),
               0.0018,
               0.0018,
               4
            );
         }
      }

      HuntMesh.tube(var0, var1, var2, 2502186, 0.025, 0.065, -0.035, 0.048, 0.065, 0.02, 0.006, 0.005, 8);
   }

   public static void arrow(PoseStack var0, VertexConsumer var1, int var2) {
      HuntMesh.tube(var0, var1, var2, 6905152, 0.0, 0.0, -0.37, 0.0, 0.0, 0.37, 0.0038, 0.004, 8);
      HuntMesh.tube(var0, var1, var2, 8160644, 0.0, 0.0, -0.36, 0.0, 0.0, -0.43, 0.012, 5.0E-4, 6);

      for (int var3 = 0; var3 < 3; var3++) {
         var0.pushPose();
         var0.mulPose(Axis.ZP.rotationDegrees((float)(var3 * 120)));
         HuntMesh.ellipsoid(var0, var1, var2, var3 == 0 ? 12364137 : 13486253, 1, 0.0, 0.016, 0.28, 0.002, 0.018, 0.06);
         var0.popPose();
      }

      HuntMesh.tube(var0, var1, var2, 13879199, 0.0, 0.0, 0.35, 0.0, 0.0, 0.38, 0.006, 0.004, 8);
   }

   public static final class Extensions implements IClientItemExtensions {
      @Override
      public BlockEntityWithoutLevelRenderer getCustomRenderer() {
         if (HuntEquipmentRenderer.instance == null) {
            HuntEquipmentRenderer.instance = new HuntEquipmentRenderer();
         }

         return HuntEquipmentRenderer.instance;
      }

      @Override
      public boolean applyForgeHandTransform(PoseStack var1, LocalPlayer var2, HumanoidArm var3, ItemStack var4, float var5, float var6, float var7) {
         if (!var4.is(HuntContent.FIELD_BOW.get())) {
            return false;
         } else {
            int var8 = var3 == HumanoidArm.RIGHT ? 1 : -1;
            var1.translate((double)(-var8) * 0.34, -0.25 - (double)var6 * 0.55, -0.78);
            var1.mulPose(Axis.ZP.rotationDegrees((float)(-var8 * 5)));
            return true;
         }
      }
   }
}
