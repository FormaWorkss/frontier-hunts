package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.expedition.ExpeditionWeapon;
import com.formaworks.frontierhunts.expedition.Weapon;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel.ArmPose;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

public final class ExpeditionItemRenderer extends BlockEntityWithoutLevelRenderer {
   private static ExpeditionItemRenderer INSTANCE;

   public ExpeditionItemRenderer() {
      super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
   }

   public void renderByItem(ItemStack var1, ItemDisplayContext var2, PoseStack var3, MultiBufferSource var4, int var5, int var6) {
      FilteredFieldTexture.ensure(FieldMaterials.ATLAS);
      String var7 = BuiltInRegistries.ITEM.getKey(var1.getItem()).getPath();
      if (var1.getItem() instanceof ExpeditionWeapon || var7.equals("bowfishing_bow")) {
         var4 = WeaponIcons.lift(var2, var4); // [1.1.2] in slots: the readable icon shader
      }
      var3.pushPose();
      var3.translate(0.5, 0.5, 0.5);
      if (var7.equals("expedition_guide")) {
         FieldBookModel.draw(true, var3, var4, var5);
         var3.popPose();
      } else if (var7.equals("field_flashlight")) {
         FieldFlashlightModel.draw(var1, var2, var3, var4, var5);
         var3.popPose();
      } else if (var7.equals("bowfishing_bow")) {
         BowfishingClient.draw(var1, var2, var3, var4, var5, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
         var3.popPose();
      } else if (FieldSupplyMesh.draw(var7, var2, var3, var4, var5)) {
         var3.popPose();
      } else if (FieldPackModel.draw(var7, var2, var3, var4, var5)) {
         var3.popPose();
      } else {
         VertexConsumer var8 = var4.getBuffer(RenderType.entityCutoutNoCull(FieldMaterials.ATLAS));
         Minecraft var9 = Minecraft.getInstance();
         double var10 = var9.level == null ? 0.0 : (double)((float)var9.level.getGameTime() + var9.getTimer().getGameTimeDeltaPartialTick(false));
         CompoundTag var12 = ExpeditionWeapon.data(var1);
         float var13 = FieldWeaponMesh.reloadProgress(var1, var10);
         float var14 = (float)Math.max(0.0, 1.0 - (var10 - (double)var12.getLong("shot_at")) / 7.0);
         if (var12.getLong("shot_at") == 0L) {
            var14 = 0.0F;
         }

         if (var1.getItem() instanceof ExpeditionWeapon var15) {
            if (var2.firstPerson()) {
               var3.translate(0.0, -Math.sin((double)var13 * Math.PI) * 0.14, (double)var14 * 0.045);
               var3.mulPose(Axis.ZP.rotationDegrees((float)Math.sin((double)var13 * Math.PI) * -20.0F));
               var3.mulPose(Axis.XP.rotationDegrees(var14 * -3.0F));
            }

            if (var15.weapon.bow) {
               net.minecraft.world.entity.LivingEntity var19 = var2.firstPerson() ? var9.player : HuntEquipmentRenderer.renderedHolder;
               float var17 = var19 != null && var19.getUseItem() == var1
                  ? Math.min(1.0F, ((float)var19.getTicksUsingItem() + var9.getTimer().getGameTimeDeltaPartialTick(false)) / (float)var15.weapon.interval)
                  : 0.0F;
               FieldEquipmentModel.bow(var15.weapon, var3, var8, var5, var17);
               if (var15.weapon != Weapon.HUNTING_SPEAR) {
                  boolean var18 = var15.weapon == Weapon.CROSSBOW;
                  FieldArrowModel.nocked(
                     var3,
                     var8,
                     var5,
                     var18 ? 0.0 : 0.015,
                     var18 ? FieldBowPresentation.XB_BOLT_Y : 0.05, // [archery2] the bolt lies on the rail
                     var18 ? FieldBowPresentation.crossbowLatchZ(var17) : FieldBows.nockZ(var17),
                     false,
                     var18,
                     FieldArrowModel.shotFor((LivingEntity)(var2.firstPerson() ? Minecraft.getInstance().player : HuntEquipmentRenderer.renderedHolder))
                  );
               }
            } else {
               FieldWeaponMesh.draw(var15.weapon, var1, var2, var3, var4, var5, var10);
            }
         } else {
            if (var2 == ItemDisplayContext.GUI) {
               var3.scale(1.65F, 1.65F, 1.65F);
            }

            String var20 = BuiltInRegistries.ITEM.getKey(var1.getItem()).getPath();
            if (!FishingGearModel.draw(var20, var2, var3, var8, var5)) {
               FieldSupplyModel.draw(var20, var1, var3, var8, var5);
            }
         }

         var3.popPose();
      }
   }

   public static class Extensions implements IClientItemExtensions {
      public ArmPose getArmPose(LivingEntity var1, InteractionHand var2, ItemStack var3) {
         return null;
      }

      public BlockEntityWithoutLevelRenderer getCustomRenderer() {
         if (ExpeditionItemRenderer.INSTANCE == null) {
            ExpeditionItemRenderer.INSTANCE = new ExpeditionItemRenderer();
         }

         return ExpeditionItemRenderer.INSTANCE;
      }
   }
}
