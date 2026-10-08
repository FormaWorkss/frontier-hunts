package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.expedition.ExpeditionWeapon;
import com.formaworks.frontierhunts.rifle.RidgelineOptics;
import com.formaworks.frontierhunts.rifle.RidgelineScopeItem;
import com.formaworks.frontierhunts.rifle.RifleItem;
import com.formaworks.frontierhunts.rifle.RifleState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel.ArmPose;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

public final class RifleRenderer extends BlockEntityWithoutLevelRenderer {
   private static RifleRenderer instance;

   public RifleRenderer() {
      super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
   }

   public void renderByItem(ItemStack var1, ItemDisplayContext var2, PoseStack var3, MultiBufferSource var4, int var5, int var6) {
      Minecraft var7 = Minecraft.getInstance();
      double var8 = var7.level == null ? 0.0 : (double)((float)var7.level.getGameTime() + var7.getTimer().getGameTimeDeltaPartialTick(true));
      if (var1.getItem() instanceof RifleItem) {
         var4 = WeaponIcons.lift(var2, var4); // [1.1.2] in slots: the readable icon shader
      }
      var3.pushPose();
      var3.translate(0.5, 0.5, 0.5);
      if (var1.getItem() instanceof RifleItem) {
         boolean var10 = ExpeditionWeapon.attachment(var1, "bipod");
         Entity var11 = var2.firstPerson()
            ? var7.player
            : (
               var2 != ItemDisplayContext.THIRD_PERSON_LEFT_HAND && var2 != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                  ? null
                  : HuntEquipmentRenderer.renderedHolder
            );
         float var12 = !var10
            ? 0.0F
            : (
               var2 == ItemDisplayContext.GUI || var2 == ItemDisplayContext.FIXED
                  ? 1.0F
                  : (var11 == null ? 0.0F : FieldAttachmentHardware.bipodDeploy(var11, var11.isCrouching(), var8))
            );
         RidgelineModel.draw(var3, var4, var5, RifleState.read(var1), var8, var10, var12, RidgelineOptics.sight(var1)); // [rifle] fitted sight
      } else if (var1.getItem() instanceof RidgelineScopeItem) {
         // [rifle] the loose factory scope: the scope portion of the supplied rifle mesh, centred like the other loose optics
         var3.translate(0.0, -0.147, -0.05);
         CamoRifleModel.drawScope(var3, var4, var5);
      } else {
         var3.scale(8.0F, 8.0F, 8.0F);
         RidgelineModel.cartridge(var3, var4.getBuffer(RenderType.entityCutout(WhitetailRenderer.MATERIAL)), var5, false);
      }

      var3.popPose();
   }

   public static final class Extensions implements IClientItemExtensions {
      public BlockEntityWithoutLevelRenderer getCustomRenderer() {
         if (RifleRenderer.instance == null) {
            RifleRenderer.instance = new RifleRenderer();
         }

         return RifleRenderer.instance;
      }

      public ArmPose getArmPose(LivingEntity var1, InteractionHand var2, ItemStack var3) {
         return null;
      }
   }
}
