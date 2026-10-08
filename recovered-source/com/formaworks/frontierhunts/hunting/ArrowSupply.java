package com.formaworks.frontierhunts.hunting;

import com.formaworks.frontierhunts.HuntContent;
import com.formaworks.frontierhunts.expedition.ExpeditionWeapon;
import java.util.ArrayList;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ArrowSupply {
   public static Supplier<String> SWITCH_KEYS = () -> "R / V";

   public static ItemStack wornQuiver(Player var0) {
      ItemStack var1 = NativeGear.quiver(var0);
      return !var1.isEmpty() ? var1 : CuriosBridge.find(var0, var0x -> var0x.getItem() instanceof QuiverItem, "quiver");
   }

   public static ItemStack feedingQuiver(Player var0) {
      ItemStack var1 = wornQuiver(var0);
      if (!var1.isEmpty() && QuiverItem.drawSlot(var1) >= 0) {
         return var1;
      } else {
         Inventory var2 = var0.getInventory();

         for (int var3 = 0; var3 < var2.getContainerSize(); var3++) {
            ItemStack var4 = var2.getItem(var3);
            if (var4.getItem() instanceof QuiverItem && QuiverItem.drawSlot(var4) >= 0) {
               return var4;
            }
         }

         return var1;
      }
   }

   private static ArrowSupply.Source locate(Player var0) {
      ItemStack var1 = var0.getOffhandItem();
      if (ArrowTip.arrow(var1)) {
         return new ArrowSupply.Source(var1, null);
      } else {
         ItemStack var2 = feedingQuiver(var0);
         if (!var2.isEmpty()) {
            ItemStack var3 = QuiverItem.peek(var2);
            if (!var3.isEmpty()) {
               return new ArrowSupply.Source(var3, var2);
            }
         }

         Inventory var7 = var0.getInventory();
         ItemStack var4 = null;

         for (int var5 = 0; var5 < var7.getContainerSize(); var5++) {
            ItemStack var6 = var7.getItem(var5);
            if (var6.is((Item)HuntContent.TRACER_ARROW.get())) {
               if (var4 == null) {
                  var4 = var6;
               }
            } else if (ArrowTip.arrow(var6)) {
               return new ArrowSupply.Source(var6, null);
            }
         }

         return var4 == null ? null : new ArrowSupply.Source(var4, null);
      }
   }

   public static boolean has(Player var0) {
      return var0.hasInfiniteMaterials() || locate(var0) != null;
   }

   public static ArrowSupply.Shot peek(Player var0) {
      ArrowSupply.Source var1 = locate(var0);
      return var1 == null ? ArrowSupply.Shot.DEFAULT : new ArrowSupply.Shot(ArrowTip.of(var1.stack), ArrowTip.primitiveShaft(var1.stack));
   }

   public static ItemStack peekStack(Player var0) {
      ArrowSupply.Source var1 = locate(var0);
      return var1 == null ? ItemStack.EMPTY : var1.stack;
   }

   public static int available(Player var0) {
      ArrowSupply.Source var1 = locate(var0);
      if (var1 == null) {
         return 0;
      } else {
         int var2 = 0;
         ItemStack var3 = var0.getOffhandItem();
         if (ArrowTip.arrow(var3) && ItemStack.isSameItemSameComponents(var3, var1.stack)) {
            var2 += var3.getCount();
         }

         ItemStack var4 = feedingQuiver(var0);
         if (!var4.isEmpty()) {
            for (ItemStack var6 : QuiverItem.read(var4)) {
               if (ItemStack.isSameItemSameComponents(var6, var1.stack)) {
                  var2 += var6.getCount();
               }
            }
         }

         for (ItemStack var8 : var0.getInventory().items) {
            if (ItemStack.isSameItemSameComponents(var8, var1.stack)) {
               var2 += var8.getCount();
            }
         }

         return var2;
      }
   }

   public static ArrowSupply.Shot take(Player var0) {
      ArrowSupply.Source var1 = locate(var0);
      if (var1 == null) {
         return var0.hasInfiniteMaterials() ? ArrowSupply.Shot.DEFAULT : null;
      } else {
         ArrowSupply.Shot var2 = new ArrowSupply.Shot(ArrowTip.of(var1.stack), ArrowTip.primitiveShaft(var1.stack));
         if (!var0.hasInfiniteMaterials()) {
            if (var1.quiver != null) {
               ItemStack var3 = NativeGear.quiver(var0);
               boolean var4 = !var3.isEmpty() && ItemStack.isSameItemSameComponents(var3, var1.quiver);
               QuiverItem.takeOne(var1.quiver);
               if (var4 && var0 instanceof ServerPlayer var5) {
                  NativeGear.updateQuiver(var5, var1.quiver);
               }
            } else {
               var1.stack.shrink(1);
            }
         }

         return var2;
      }
   }

   public static void cycle(Player var0) {
      if (var0 instanceof ServerPlayer var1 && var0.isShiftKeyDown() && NativeGear.unequipQuiver(var1)) {
         var0.displayClientMessage(Component.literal("Quiver removed"), true);
         return;
      }

      ItemStack var9 = var0.getOffhandItem();
      if (ArrowTip.arrow(var9)) {
         var0.displayClientMessage(Component.literal("Offhand arrows are always shot first: " + QuiverItem.label(var9)), true);
      } else {
         ItemStack var2 = feedingQuiver(var0);
         if (!var2.isEmpty()) {
            ItemStack var3 = NativeGear.quiver(var0);
            boolean var4 = !var3.isEmpty() && ItemStack.isSameItemSameComponents(var3, var2);
            ItemStack var5 = QuiverItem.cycle(var2);
            if (var4 && var0 instanceof ServerPlayer var6) {
               NativeGear.updateQuiver(var6, var2);
            }

            if (!var5.isEmpty()) {
               var0.displayClientMessage(Component.literal("Drawing: " + QuiverItem.label(var5) + " · " + var5.getCount()), true);
               return;
            }
         }

         Inventory var10 = var0.getInventory();
         ArrayList var11 = new ArrayList();

         for (int var12 = 0; var12 < var10.items.size(); var12++) {
            if (ArrowTip.arrow((ItemStack)var10.items.get(var12))) {
               var11.add(var12);
            }
         }

         if (var11.isEmpty()) {
            var0.displayClientMessage(Component.literal("No arrows"), true);
         } else {
            ItemStack var13 = ((ItemStack)var10.items.get((Integer)var11.get(0))).copy();

            for (int var14 = 1; var14 < var11.size(); var14++) {
               ItemStack var7 = (ItemStack)var10.items.get((Integer)var11.get(0));

               for (int var8 = 0; var8 < var11.size() - 1; var8++) {
                  var10.items.set((Integer)var11.get(var8), (ItemStack)var10.items.get((Integer)var11.get(var8 + 1)));
               }

               var10.items.set((Integer)var11.get(var11.size() - 1), var7);
               ItemStack var15 = (ItemStack)var10.items.get((Integer)var11.get(0));
               if (!ItemStack.isSameItemSameComponents(var15, var13)) {
                  var10.setChanged();
                  var0.displayClientMessage(Component.literal("Drawing: " + QuiverItem.label(var15) + " · " + var15.getCount()), true);
                  return;
               }
            }

            var10.setChanged();
            var0.displayClientMessage(Component.literal("Only one kind of arrow: " + QuiverItem.label(var13)), true);
         }
      }
   }

   public static String switchKeys() {
      return SWITCH_KEYS.get();
   }

   public static boolean arrowBow(ItemStack var0) {
      if (var0.is((Item)HuntContent.FIELD_BOW.get())) {
         return true;
      } else {
         if (var0.getItem() instanceof ExpeditionWeapon var1 && var1.weapon.bow && var1.weapon.ammo.equals("field_arrow")) {
            return true;
         }

         return false;
      }
   }

   private ArrowSupply() {
   }

   public static record Shot(ArrowTip tip, boolean primitive) {
      public static final ArrowSupply.Shot DEFAULT = new ArrowSupply.Shot(ArrowTip.FIXED_BROADHEAD, false);

      public ItemStack stack(int var1) {
         return ArrowTip.arrowStack(this.primitive, this.tip, var1);
      }
   }

   private static record Source(ItemStack stack, ItemStack quiver) {
   }
}
