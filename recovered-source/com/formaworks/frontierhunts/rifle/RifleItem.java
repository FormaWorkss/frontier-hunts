package com.formaworks.frontierhunts.rifle;

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

public final class RifleItem extends Item {
   public RifleItem() {
      super(new Properties().stacksTo(1).durability(2400));
   }

   public boolean shouldCauseReequipAnimation(ItemStack var1, ItemStack var2, boolean var3) {
      return var3 || var1.getItem() != var2.getItem();
   }

   public InteractionResultHolder<ItemStack> use(Level var1, Player var2, InteractionHand var3) {
      ItemStack var4 = var2.getItemInHand(var3);
      return var3 == InteractionHand.MAIN_HAND && RifleState.read(var4).action() == 0 ? InteractionResultHolder.pass(var4) : InteractionResultHolder.fail(var4);
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

   public void inventoryTick(ItemStack var1, Level var2, Entity var3, int var4, boolean var5) {
      if (!var2.isClientSide && var3 instanceof ServerPlayer var6) {
         RifleActions.tick(var6, var1, var5);
      }
   }

   public void appendHoverText(ItemStack var1, TooltipContext var2, List<Component> var3, TooltipFlag var4) {
      RifleState var5 = RifleState.read(var1);
      var3.add(Component.translatable("rifle.frontierhunts.rounds", new Object[]{var5.magazine(), var5.chamber() ? 1 : 0}));
      var3.add(Component.translatable("rifle.frontierhunts.controls"));
      var3.add(Component.translatable("rifle.frontierhunts.bolt_hint"));
      // [rifle] fitted sight (swap at the Attachment Workbench) and the prone stance
      var3.add(Component.translatable("rifle.frontierhunts.sight", Component.translatable(RidgelineOptics.nameKey(RidgelineOptics.sight(var1)))));
      var3.add(Component.translatable("rifle.frontierhunts.prone_hint", RidgelineOptics.PRONE_KEY.get()));
   }
}
