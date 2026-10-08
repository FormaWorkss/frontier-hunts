package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.expedition.ExpeditionGear;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;

@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
public final class FieldOpticPresentation {
   public static float progress(float var0) {
      LocalPlayer var1 = Minecraft.getInstance().player;
      if (var1 != null && var1.isUsingItem()) {
         float var2 = Math.clamp(((float)var1.getTicksUsingItem() + var0) / 16.0F, 0.0F, 1.0F);
         return var2 * var2 * (3.0F - 2.0F * var2);
      } else {
         return 0.0F;
      }
   }

   @SubscribeEvent
   public static void hand(RenderHandEvent var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (var1.player != null && var1.level != null) {
         ItemStack var2 = var1.player.getItemInHand(var0.getHand());
         if (var2.getItem() instanceof ExpeditionGear var3 && (var3.id.contains("binoculars") || var3.id.equals("rangefinder"))) {
            var0.setCanceled(true);
            float var9 = (float)((var0.getHand() == InteractionHand.MAIN_HAND ? 1 : -1) * (var1.player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1));
            float var5 = var1.player.isUsingItem() && var1.player.getUsedItemHand() == var0.getHand() ? progress(var0.getPartialTick()) : 0.0F;
            if (var5 > 0.985F) {
               return;
            }

            PoseStack var6 = var0.getPoseStack();
            var6.pushPose();
            var6.translate(
               (double)var9 * (0.28 - 0.28 * (double)var5), -0.29 + 0.26 * (double)var5, -0.6 + 0.33 * (double)var5 - (double)var0.getEquipProgress() * 0.23
            );
            var6.mulPose(Axis.YP.rotationDegrees(var9 * (-22.0F + 22.0F * var5)));
            var6.mulPose(Axis.XP.rotationDegrees(-26.0F + 26.0F * var5));
            var6.scale(1.28F, 1.28F, 1.28F);
            FilteredFieldTexture.ensure(FieldMaterials.ATLAS);
            if (!FieldElectronicModels.draw(var3.id, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, var6, var0.getMultiBufferSource(), var0.getPackedLight())) {
               VertexConsumer var7 = var0.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(FieldMaterials.ATLAS));
               FieldSupplyModel.draw(var3.id, var2, var6, var7, var0.getPackedLight());
            }

            var6.popPose();
            HumanoidArm var10 = var9 > 0.0F ? HumanoidArm.RIGHT : HumanoidArm.LEFT;
            opticWrist(
               var6,
               var0,
               var10,
               (double)var9 * (0.27 - 0.22 * (double)var5),
               -0.31 + 0.13 * (double)var5,
               -0.56 + 0.26 * (double)var5,
               -58.0F - 20.0F * var5,
               var9 * (34.0F - 20.0F * var5),
               var9 * 5.0F
            );
            if (var3.id.contains("binoculars")
               && var1.player.getItemInHand(var0.getHand() == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND).isEmpty()) {
               HumanoidArm var8 = var10 == HumanoidArm.RIGHT ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
               opticWrist(
                  var6,
                  var0,
                  var8,
                  (double)(-var9) * (0.12 - 0.08 * (double)var5),
                  -0.35 + 0.15 * (double)var5,
                  -0.59 + 0.27 * (double)var5,
                  -58.0F - 20.0F * var5,
                  -var9 * (35.0F - 20.0F * var5),
                  -var9 * 5.0F
               );
            }

            return;
         }
      }
   }

   private static void opticWrist(
      PoseStack var0, RenderHandEvent var1, HumanoidArm var2, double var3, double var5, double var7, float var9, float var10, float var11
   ) {
      var0.pushPose();
      var0.translate(var3, var5, var7);
      var0.scale(0.78F, 0.78F, 0.78F);
      FieldPlayerArms.wrist(var0, var1.getMultiBufferSource(), var1.getPackedLight(), var2, 0.0, 0.0, 0.0, var9, var10, var11);
      var0.popPose();
   }

   private FieldOpticPresentation() {
   }
}
