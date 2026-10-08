package com.formaworks.frontierhunts.phone;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * [phone] The Field Phone: a rugged outdoor smartphone. Using it takes it out of the pocket (the phone screen opens on
 * the client); it also opens with the phone key while it is anywhere in the inventory. Its battery and torch live on
 * the stack ({@link #charge}, {@link #light}); {@link PhoneBattery} drains and charges it on the server.
 */
public final class FieldPhoneItem extends Item {
   /** a full battery, in battery units (one unit is a second of screen time) */
   public static final int CAPACITY = 3600;
   private static final String CHARGE = "phone_charge";
   private static final String LIGHT = "phone_light";
   /** client hook: open the phone (set by client code; never loaded on a dedicated server) */
   public static Runnable opener = () -> {
   };

   public FieldPhoneItem() {
      super(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
   }

   public static boolean is(ItemStack stack) {
      return stack != null && !stack.isEmpty() && stack.getItem() instanceof FieldPhoneItem;
   }

   private static CompoundTag data(ItemStack stack) {
      CustomData d = stack.get(DataComponents.CUSTOM_DATA);
      return d == null ? new CompoundTag() : d.copyTag();
   }

   private static void save(ItemStack stack, CompoundTag t) {
      stack.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
   }

   /** Battery units left (a new phone comes charged). */
   public static int charge(ItemStack stack) {
      CompoundTag t = data(stack);
      return t.contains(CHARGE) ? Math.max(0, Math.min(CAPACITY, t.getInt(CHARGE))) : CAPACITY;
   }

   public static int percent(ItemStack stack) {
      int c = charge(stack);
      return c <= 0 ? 0 : Math.max(1, Math.round(c * 100.0F / CAPACITY));
   }

   public static void setCharge(ItemStack stack, int units) {
      CompoundTag t = data(stack);
      int v = Math.max(0, Math.min(CAPACITY, units));
      if (t.contains(CHARGE) && t.getInt(CHARGE) == v) {
         return;
      }
      t.putInt(CHARGE, v);
      if (v <= 0) {
         t.remove(LIGHT);
      }
      save(stack, t);
   }

   public static boolean light(ItemStack stack) {
      return data(stack).getBoolean(LIGHT);
   }

   public static void setLight(ItemStack stack, boolean on) {
      if (light(stack) == on) {
         return;
      }
      CompoundTag t = data(stack);
      if (on) {
         t.putBoolean(LIGHT, true);
      } else {
         t.remove(LIGHT);
      }
      save(stack, t);
   }

   @Override
   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      ItemStack stack = player.getItemInHand(hand);
      if (level.isClientSide) {
         opener.run();
      }
      return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
   }

   @Override
   public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
      // the battery ticking down must not bob the phone in the hand
      return slotChanged || !(newStack.getItem() instanceof FieldPhoneItem);
   }

   @Override
   public boolean isBarVisible(ItemStack stack) {
      return charge(stack) < CAPACITY;
   }

   @Override
   public int getBarWidth(ItemStack stack) {
      return Math.round(13.0F * charge(stack) / CAPACITY);
   }

   @Override
   public int getBarColor(ItemStack stack) {
      int p = percent(stack);
      return p <= 15 ? 0xE5574B : (p <= 40 ? 0xE8B23A : 0x7FB24A);
   }

   @Override
   public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
      lines.add(Component.translatable("item.frontierhunts.field_phone.tip").withStyle(ChatFormatting.GRAY));
      lines.add(Component.translatable("item.frontierhunts.field_phone.battery", percent(stack)).withStyle(ChatFormatting.DARK_GRAY));
   }
}
