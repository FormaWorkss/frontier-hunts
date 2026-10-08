package com.formaworks.frontierhunts.campcook;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** [licence] A camp dish: food (nutrition through the survival table), its bowl back, and its camp-meal buff. */
public class CampDishItem extends Item {
   public final Dish dish;

   public CampDishItem(Dish dish) {
      super(new Item.Properties().stacksTo(dish.bowl ? 16 : 64)
         .food(new FoodProperties.Builder().nutrition(dish.nutrition).saturationModifier(dish.saturation).build()));
      this.dish = dish;
   }

   @Override
   public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity e) {
      ItemStack rest = super.finishUsingItem(stack, level, e);
      if (!level.isClientSide) {
         MealBuffs.apply(e, this.dish);
      }
      if (this.dish.bowl && !(e instanceof Player p && p.getAbilities().instabuild)) {
         if (rest.isEmpty()) {
            return new ItemStack(Items.BOWL);
         }
         if (e instanceof Player p && !p.getInventory().add(new ItemStack(Items.BOWL))) {
            p.drop(new ItemStack(Items.BOWL), false);
         }
      }
      return rest;
   }

   @Override
   public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
      MealBuffs.Buff b = this.dish.buff;
      tip.add(Component.translatable("campcook.frontierhunts.tip.buff", Component.translatable("effect.frontierhunts." + b.id()),
         this.dish.minutes + ":00").withStyle(s -> s.withColor(b.color & 0xFFFFFF)));
      tip.add(Component.translatable("campcook.frontierhunts.buff." + b.name().toLowerCase(java.util.Locale.ROOT)).withStyle(ChatFormatting.GRAY));
      tip.add(Component.translatable("campcook.frontierhunts.tip.one").withStyle(ChatFormatting.DARK_GRAY));
   }
}
