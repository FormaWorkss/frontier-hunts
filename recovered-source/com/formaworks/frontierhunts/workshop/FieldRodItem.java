package com.formaworks.frontierhunts.workshop;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.level.Level;

public final class FieldRodItem extends Item {
   public FieldRodItem() {
      super(new Properties().durability(256));
   }

   public InteractionResultHolder<ItemStack> use(Level var1, Player var2, InteractionHand var3) {
      ItemStack var4 = var2.getItemInHand(var3);
      if (var3 == InteractionHand.MAIN_HAND && !var2.isUnderWater() && !var2.isSprinting()) {
         if (var2 instanceof ServerPlayer var5) {
            FishingActions.begin(var5, var4);
         }

         var2.startUsingItem(var3);
         return InteractionResultHolder.consume(var4);
      } else {
         return InteractionResultHolder.fail(var4);
      }
   }

   public void releaseUsing(ItemStack var1, Level var2, LivingEntity var3, int var4) {
      if (var3 instanceof ServerPlayer var5) {
         FishingActions.release(var5, var1);
      }
   }

   public int getUseDuration(ItemStack var1, LivingEntity var2) {
      return 72000;
   }

   public UseAnim getUseAnimation(ItemStack var1) {
      return UseAnim.NONE;
   }

   public boolean onLeftClickEntity(ItemStack var1, Player var2, Entity var3) {
      return true;
   }

   public int getEnchantmentValue() {
      return 1;
   }

   public void appendHoverText(ItemStack var1, TooltipContext var2, List<Component> var3, TooltipFlag var4) {
      var3.add(Component.literal("Hold use to load the cast; release to send the float."));
      var3.add(Component.literal("Strike when the float dips. Hold use to reel; ease off under strain."));
      var3.add(Component.literal("Find live fish in open water."));
   }
}
