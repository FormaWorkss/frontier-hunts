package com.formaworks.frontierhunts.landscape.ride;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.level.Level;

public class Wingsuit extends Item implements Equipable {
   public Wingsuit(Properties var1) {
      super(var1);
   }

   public static ItemStack worn(LivingEntity var0) {
      ItemStack var1 = var0.getItemBySlot(EquipmentSlot.CHEST);
      return var1.getItem() instanceof Wingsuit ? var1 : ItemStack.EMPTY;
   }

   public static boolean wearing(LivingEntity var0) {
      return !worn(var0).isEmpty();
   }

   public static boolean canopyOpen(LivingEntity var0) {
      ItemStack var1 = worn(var0);
      return !var1.isEmpty() && Boolean.TRUE.equals(var1.get((DataComponentType)RideContent.WING_OPEN.get()));
   }

   public static boolean flying(LivingEntity var0) {
      return var0.isFallFlying() && wearing(var0) && !canopyOpen(var0);
   }

   public static void setCanopy(ItemStack var0, boolean var1) {
      if (var1) {
         var0.set((DataComponentType)RideContent.WING_OPEN.get(), true);
      } else {
         var0.remove((DataComponentType)RideContent.WING_OPEN.get());
      }
   }

   public EquipmentSlot getEquipmentSlot() {
      return EquipmentSlot.CHEST;
   }

   public Holder<SoundEvent> getEquipSound() {
      return SoundEvents.ARMOR_EQUIP_ELYTRA;
   }

   public InteractionResultHolder<ItemStack> use(Level var1, Player var2, InteractionHand var3) {
      return this.swapWithEquipmentSlot(this, var1, var2, var3);
   }

   public boolean canElytraFly(ItemStack var1, LivingEntity var2) {
      return !Boolean.TRUE.equals(var1.get((DataComponentType)RideContent.WING_OPEN.get()));
   }

   public boolean elytraFlightTick(ItemStack var1, LivingEntity var2, int var3) {
      return true;
   }

   public void appendHoverText(ItemStack var1, TooltipContext var2, List<Component> var3, TooltipFlag var4) {
      var3.add(Component.translatable("item.frontierhunts.wingsuit.tip1").withStyle(ChatFormatting.GRAY));
      var3.add(Component.translatable("item.frontierhunts.wingsuit.tip2").withStyle(ChatFormatting.GRAY));
      var3.add(Component.translatable("item.frontierhunts.wingsuit.tip3").withStyle(ChatFormatting.GOLD));
   }
}
