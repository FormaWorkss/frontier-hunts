package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.HuntContent;
import com.formaworks.frontierhunts.expedition.BowHold;
import com.formaworks.frontierhunts.expedition.ExpeditionWeapon;
import com.formaworks.frontierhunts.expedition.Weapon;
import com.formaworks.frontierhunts.hunting.HunterCover;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

final class FieldBowAim {
   private static int cover = 0;
   private static int coverAtHeld = Integer.MIN_VALUE;

   static boolean drawn(ItemStack var0) {
      if (var0.is((Item)HuntContent.FIELD_BOW.get())) {
         return true;
      } else {
         if (var0.getItem() instanceof ExpeditionWeapon var1 && var1.weapon.bow && var1.weapon != Weapon.HUNTING_SPEAR && var1.weapon != Weapon.BOWFISHING_BOW) {
            return true;
         }

         return false;
      }
   }

   static int interval(ItemStack var0) {
      if (var0.is((Item)HuntContent.FIELD_BOW.get())) {
         return 26;
      } else {
         return var0.getItem() instanceof ExpeditionWeapon var1 ? var1.weapon.interval : 26;
      }
   }

   static int heldTicks(Player var0, ItemStack var1) {
      return var0.isUsingItem() && var0.getUseItem() == var1 ? var0.getTicksUsingItem() : 0;
   }

   static float drawOf(Player var0, ItemStack var1, float var2) {
      return var0.isUsingItem() && var0.getUseItem() == var1 ? Math.min(1.0F, ((float)var0.getTicksUsingItem() + var2) / (float)interval(var1)) : 0.0F;
   }

   /** [archery2] Raise-to-anchor progress for a draw: the anchor (and with it the sight) is reached exactly at full draw. */
   static float aim(float var0) {
      float var1 = Mth.clamp((var0 - 0.1F) / 0.9F, 0.0F, 1.0F);
      return var1 * var1 * (3.0F - 2.0F * var1);
   }

   private static int cover(Player var0, int var1) {
      if (var1 <= 1 || var1 - coverAtHeld >= 20 || var1 < coverAtHeld) {
         coverAtHeld = var1;
         cover = HunterCover.of(var0);
      }

      return cover;
   }

   static float strain(Player var0, ItemStack var1) {
      int var2 = heldTicks(var0, var1);
      return var2 <= 60 ? 0.0F : BowHold.strain(var0, var2, cover(var0, var2));
   }

   static float swayYaw(double var0, float var2) {
      return var2 <= 0.0F ? 0.0F : (float)((double)var2 * (1.15 * Math.sin(var0 * 0.86) + 0.5 * Math.sin(var0 * 2.7 + 1.3)));
   }

   static float swayPitch(double var0, float var2) {
      return var2 <= 0.0F ? 0.0F : (float)((double)var2 * (0.95 * Math.sin(var0 * 1.21 + 0.7) + 0.38 * Math.sin(var0 * 3.1)));
   }

   static float aimFactor(Player var0, float var1) {
      ItemStack var2 = var0.getMainHandItem();
      return !drawn(var2) ? 0.0F : aim(drawOf(var0, var2, var1));
   }

   static double seconds(float var0) {
      Minecraft var1 = Minecraft.getInstance();
      return var1.level == null ? 0.0 : (double)((float)var1.level.getGameTime() + var0) / 20.0;
   }

   private FieldBowAim() {
   }
}
