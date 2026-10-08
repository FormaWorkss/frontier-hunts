package com.formaworks.frontierhunts.hunting;

import com.formaworks.frontierhunts.HuntContent;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

public final class QuiverItem extends Item {
   public static final int SLOTS = 4;
   public static final int PER_SLOT = 32;
   public static final String SLOT_TYPE = "quiver";

   public QuiverItem(Properties var1) {
      super(var1.stacksTo(1));
   }

   public static NonNullList<ItemStack> read(ItemStack var0) {
      NonNullList var1 = NonNullList.withSize(4, ItemStack.EMPTY);
      ItemContainerContents var2 = (ItemContainerContents)var0.get(DataComponents.CONTAINER);
      if (var2 != null) {
         var2.copyInto(var1);
      }

      return var1;
   }

   static void write(ItemStack var0, NonNullList<ItemStack> var1) {
      var0.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(var1));
   }

   public static int count(ItemStack var0) {
      int var1 = 0;

      for (ItemStack var3 : read(var0)) {
         var1 += var3.getCount();
      }

      return var1;
   }

   public static int active(ItemStack var0) {
      CustomData var1 = (CustomData)var0.get(DataComponents.CUSTOM_DATA);
      return var1 == null ? 0 : Math.clamp((long)var1.copyTag().getInt("quiver_active"), 0, 3);
   }

   static void setActive(ItemStack var0, int var1) {
      CompoundTag var2 = var0.has(DataComponents.CUSTOM_DATA) ? ((CustomData)var0.get(DataComponents.CUSTOM_DATA)).copyTag() : new CompoundTag();
      var2.putInt("quiver_active", var1);
      var0.set(DataComponents.CUSTOM_DATA, CustomData.of(var2));
   }

   public static int drawSlot(ItemStack var0) {
      NonNullList var1 = read(var0);
      int var2 = active(var0);

      for (int var3 = 0; var3 < 4; var3++) {
         int var4 = (var2 + var3) % 4;
         if (!((ItemStack)var1.get(var4)).isEmpty()) {
            return var4;
         }
      }

      return -1;
   }

   public static ItemStack peek(ItemStack var0) {
      int var1 = drawSlot(var0);
      return var1 < 0 ? ItemStack.EMPTY : (ItemStack)read(var0).get(var1);
   }

   public static ItemStack takeOne(ItemStack var0) {
      int var1 = drawSlot(var0);
      if (var1 < 0) {
         return ItemStack.EMPTY;
      } else {
         NonNullList var2 = read(var0);
         ItemStack var3 = ((ItemStack)var2.get(var1)).split(1);
         write(var0, var2);
         return var3;
      }
   }

   public static ItemStack insert(ItemStack var0, ItemStack var1) {
      if (ArrowTip.arrow(var1) && !var1.isEmpty()) {
         ItemStack var2 = var1.copy();
         if (var2.is((Item)HuntContent.TRACER_ARROW.get())) {
            var2 = ArrowTip.arrowStack(false, ArrowTip.TRACER_BROADHEAD, var2.getCount());
         }

         NonNullList var3 = read(var0);

         for (int var4 = 0; var4 < 4 && !var2.isEmpty(); var4++) {
            ItemStack var5 = (ItemStack)var3.get(var4);
            if (!var5.isEmpty() && ItemStack.isSameItemSameComponents(var5, var2)) {
               int var6 = Math.min(var2.getCount(), 32 - var5.getCount());
               var5.grow(var6);
               var2.shrink(var6);
            }
         }

         for (int var7 = 0; var7 < 4 && !var2.isEmpty(); var7++) {
            if (((ItemStack)var3.get(var7)).isEmpty()) {
               int var8 = Math.min(var2.getCount(), 32);
               var3.set(var7, var2.split(var8));
            }
         }

         write(var0, var3);
         return var2;
      } else {
         return var1;
      }
   }

   public static ItemStack cycle(ItemStack var0) {
      NonNullList var1 = read(var0);
      int var2 = drawSlot(var0);
      if (var2 < 0) {
         return ItemStack.EMPTY;
      } else {
         for (int var3 = 1; var3 <= 4; var3++) {
            int var4 = (var2 + var3) % 4;
            if (!((ItemStack)var1.get(var4)).isEmpty()) {
               setActive(var0, var4);
               return (ItemStack)var1.get(var4);
            }
         }

         return (ItemStack)var1.get(var2);
      }
   }

   private static ItemStack removeSleeve(ItemStack var0) {
      int var1 = drawSlot(var0);
      if (var1 < 0) {
         return ItemStack.EMPTY;
      } else {
         NonNullList var2 = read(var0);
         ItemStack var3 = (ItemStack)var2.get(var1);
         var2.set(var1, ItemStack.EMPTY);
         write(var0, var2);
         return var3;
      }
   }

   public static String label(ItemStack var0) {
      return ArrowTip.of(var0).title + (ArrowTip.primitiveShaft(var0) ? " (primitive)" : "");
   }

   public boolean overrideStackedOnOther(ItemStack var1, Slot var2, ClickAction var3, Player var4) {
      if (var3 != ClickAction.SECONDARY) {
         return false;
      } else {
         ItemStack var5 = var2.getItem();
         if (var5.isEmpty()) {
            ItemStack var8 = removeSleeve(var1);
            if (!var8.isEmpty()) {
               ItemStack var9 = var2.safeInsert(var8);
               if (!var9.isEmpty()) {
                  insert(var1, var9);
               }

               var4.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.9F);
            }

            return true;
         } else if (ArrowTip.arrow(var5) && var2.allowModification(var4)) {
            ItemStack var6 = insert(var1, var5.copy());
            int var7 = var5.getCount() - var6.getCount();
            if (var7 > 0) {
               var2.safeTake(var7, var7, var4);
               var4.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.9F);
            }

            return true;
         } else {
            return false;
         }
      }
   }

   public boolean overrideOtherStackedOnMe(ItemStack var1, ItemStack var2, Slot var3, ClickAction var4, Player var5, SlotAccess var6) {
      if (!var3.allowModification(var5)) {
         return false;
      } else if (ArrowTip.arrow(var2)) {
         ItemStack var8 = insert(var1, var2.copy());
         if (var8.getCount() != var2.getCount()) {
            var6.set(var8);
            var5.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.9F);
         }

         return true;
      } else if (var2.isEmpty() && var4 == ClickAction.SECONDARY) {
         ItemStack var7 = removeSleeve(var1);
         if (!var7.isEmpty()) {
            var6.set(var7);
            var5.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.9F);
         }

         return true;
      } else {
         return false;
      }
   }

   public InteractionResultHolder<ItemStack> use(Level var1, Player var2, InteractionHand var3) {
      ItemStack var4 = var2.getItemInHand(var3);
      if (var1.isClientSide) {
         return InteractionResultHolder.success(var4);
      } else {
         if (!var2.isShiftKeyDown() && var2 instanceof ServerPlayer var5 && NativeGear.quiver(var2).isEmpty() && NativeGear.equipQuiver(var5, var4)) {
            var2.playSound((SoundEvent)SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.0F, 1.0F);
            var2.displayClientMessage(Component.literal("Quiver on: bows draw from it. Sneak + G with a bow to remove it"), true);
            return InteractionResultHolder.consume(var4);
         }

         if (!var2.isShiftKeyDown()
            && NativeGear.quiver(var2).isEmpty()
            && CuriosBridge.available()
            && CuriosBridge.find(var2, var0 -> var0.getItem() instanceof QuiverItem, "quiver").isEmpty()
            && CuriosBridge.equip(var2, "quiver", var4.copy())) {
            var4.setCount(0);
            var2.playSound((SoundEvent)SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.0F, 1.0F);
            var2.displayClientMessage(Component.literal("Quiver on: your bows now draw from it"), true);
            return InteractionResultHolder.consume(var4);
         } else {
            ItemStack var6 = cycle(var4);
            var2.displayClientMessage(
               Component.literal(var6.isEmpty() ? "The quiver is empty: click arrows onto it to fill it" : "Drawing: " + label(var6) + " · " + var6.getCount()),
               true
            );
            return InteractionResultHolder.consume(var4);
         }
      }
   }

   public boolean isBarVisible(ItemStack var1) {
      return count(var1) > 0;
   }

   public int getBarWidth(ItemStack var1) {
      return Math.min(13, Math.round(13.0F * (float)count(var1) / 128.0F));
   }

   public int getBarColor(ItemStack var1) {
      return 14263361;
   }

   public void appendHoverText(ItemStack var1, TooltipContext var2, List<Component> var3, TooltipFlag var4) {
      NonNullList var5 = read(var1);
      int var6 = drawSlot(var1);
      var3.add(Component.literal(count(var1) + " / 128 arrows").withStyle(ChatFormatting.GRAY));

      for (int var7 = 0; var7 < 4; var7++) {
         ItemStack var8 = (ItemStack)var5.get(var7);
         if (!var8.isEmpty()) {
            var3.add(
               Component.literal(
                     (var7 == var6 ? "▶ " : "   ")
                        + var8.getCount()
                        + " × "
                        + (ArrowTip.primitiveShaft(var8) ? "Primitive" : "Hunting")
                        + " · "
                        + ArrowTip.of(var8).title
                  )
                  .withStyle(var7 == var6 ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY)
            );
         }
      }

      var3.add(Component.literal("Use to wear it beside a field pack. Sneak + G with a bow to remove it.").withStyle(ChatFormatting.DARK_GREEN));
      var3.add(Component.literal("Click arrows onto it to fill · right-click it on an empty slot to empty a sleeve").withStyle(ChatFormatting.DARK_GREEN));
      var3.add(Component.literal("Use: switch arrows · G while holding a bow: switch arrows").withStyle(ChatFormatting.DARK_GREEN));
   }
}
