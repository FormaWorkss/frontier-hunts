package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.HuntContent;
import com.formaworks.frontierhunts.expedition.ExpeditionWeapon;
import com.formaworks.frontierhunts.expedition.Weapon;
import com.formaworks.frontierhunts.rifle.RifleItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public final class DisplayPose {
   private static final float REST_Y = 0.012F;

   public static boolean frontier(ItemStack var0) {
      return var0.getItem() instanceof ExpeditionWeapon || var0.getItem() instanceof RifleItem;
   }

   private static boolean handgun(ItemStack var0) {
      return !(var0.getItem() instanceof ExpeditionWeapon var1)
         ? false
         : var1.weapon == Weapon.FIELD_PISTOL || var1.weapon == Weapon.REVOLVER || var1.weapon == Weapon.FLARE_GUN;
   }

   public static void layFirearm(PoseStack var0, ItemStack var1, MultiBufferSource var2, int var3, int var4, Level var5, int var6) {
      var0.pushPose();
      var0.mulPose(Axis.YP.rotationDegrees(-90.0F));
      if (frontier(var1)) {
         boolean var7 = handgun(var1);
         float var8 = var7 ? 1.45F : 0.8F;
         var0.scale(var8, var8, var8);
         var0.translate(0.0, -0.012F, var7 ? 0.045 : 0.155);
         render(var1, ItemDisplayContext.NONE, var0, var2, var3, var4, var5, var6);
      } else {
         var0.scale(1.15F, 1.15F, 1.15F);
         var0.mulPose(Axis.YP.rotationDegrees(180.0F));
         render(var1, ItemDisplayContext.FIXED, var0, var2, var3, var4, var5, var6);
      }

      var0.popPose();
   }

   public static void standBow(PoseStack var0, ItemStack var1, MultiBufferSource var2, int var3, int var4, Level var5, int var6, float var7) {
      label40: {
         var0.pushPose();
         if (var1.getItem() instanceof ExpeditionWeapon var8 && var8.weapon.bow) {
            if (var8.weapon == Weapon.CROSSBOW) {
               var0.translate(0.0, 0.08 * (double)var7, 0.0);
               var0.mulPose(Axis.XP.rotationDegrees(90.0F));
               var0.mulPose(Axis.YP.rotationDegrees(180.0F));
               var0.scale(1.15F * var7, 1.15F * var7, 1.15F * var7);
            } else if (var8.weapon == Weapon.HUNTING_SPEAR) {
               var0.scale(0.95F * var7, 0.95F * var7, 0.95F * var7);
            } else {
               var0.mulPose(Axis.YP.rotationDegrees(90.0F));
               float var10 = (var8.weapon != Weapon.COMPOUND_BOW && var8.weapon != Weapon.BOWFISHING_BOW ? 1.6F : 1.45F) * var7;
               var0.scale(var10, var10, var10);
            }

            render(var1, ItemDisplayContext.NONE, var0, var2, var3, var4, var5, var6);
            break label40;
         }

         if (var1.getItem() == HuntContent.FIELD_BOW.get()) {
            var0.mulPose(Axis.YP.rotationDegrees(90.0F));
            var0.scale(1.6F * var7, 1.6F * var7, 1.6F * var7);
            render(var1, ItemDisplayContext.NONE, var0, var2, var3, var4, var5, var6);
         } else {
            var0.mulPose(Axis.ZP.rotationDegrees(var1.is(Items.CROSSBOW) ? -45.0F : 45.0F));
            var0.scale(1.25F * var7, 1.25F * var7, 1.25F * var7);
            render(var1, ItemDisplayContext.FIXED, var0, var2, var3, var4, var5, var6);
         }
      }

      var0.popPose();
   }

   static void render(ItemStack var0, ItemDisplayContext var1, PoseStack var2, MultiBufferSource var3, int var4, int var5, Level var6, int var7) {
      Minecraft.getInstance().getItemRenderer().renderStatic(var0, var1, var4, var5, var2, var3, var6, var7);
   }

   private DisplayPose() {
   }
}
