package com.formaworks.frontierhunts.camp;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class StationMenu extends AbstractContainerMenu {
   public final boolean smoke;
   private final Container inventory;
   private final ContainerData data;

   public StationMenu(int var1, Inventory var2, boolean var3) {
      this(var1, var2, var3, new SimpleContainer(3), new SimpleContainerData(4));
   }

   public StationMenu(int var1, Inventory var2, CampStation var3) {
      this(var1, var2, var3.smoke(), var3, var3.data);
   }

   private StationMenu(int var1, Inventory var2, final boolean var3, Container var4, ContainerData var5) {
      super(var3 ? (MenuType)CampContent.SMOKE_MENU.get() : (MenuType)CampContent.TAN_MENU.get(), var1);
      this.smoke = var3;
      this.inventory = var4;
      this.data = var5;
      checkContainerSize(var4, 3);
      checkContainerDataCount(var5, 4);
      this.addSlot(new Slot(var4, 0, 38, 87) {
         public boolean mayPlace(ItemStack var1) {
            return CampStation.input(var3, var1);
         }
      });
      this.addSlot(new Slot(var4, 1, 95, 87) {
         public boolean mayPlace(ItemStack var1) {
            return CampStation.reagent(var3, var1);
         }
      });
      this.addSlot(new Slot(var4, 2, 222, 87) {
         public boolean mayPlace(ItemStack var1) {
            return false;
         }
      });

      for (int var6 = 0; var6 < 3; var6++) {
         for (int var7 = 0; var7 < 9; var7++) {
            this.addSlot(new Slot(var2, var7 + var6 * 9 + 9, 58 + var7 * 18, 142 + var6 * 18));
         }
      }

      for (int var8 = 0; var8 < 9; var8++) {
         this.addSlot(new Slot(var2, var8, 58 + var8 * 18, 200));
      }

      this.addDataSlots(var5);
   }

   public int progress() {
      return this.data.get(0);
   }

   public int duration() {
      return Math.max(1, this.data.get(1));
   }

   public boolean reserved() {
      return this.data.get(2) != 0;
   }

   public boolean running() {
      return this.data.get(3) != 0;
   }

   public boolean stillValid(Player var1) {
      return var1.isAlive() && this.inventory.stillValid(var1);
   }

   public ItemStack quickMoveStack(Player var1, int var2) {
      if (var2 >= 0 && var2 < this.slots.size()) {
         Slot var3 = (Slot)this.slots.get(var2);
         if (!var3.hasItem()) {
            return ItemStack.EMPTY;
         } else {
            ItemStack var4 = var3.getItem();
            ItemStack var5 = var4.copy();
            if (var2 < 3) {
               if (!this.moveItemStackTo(var4, 3, 39, true)) {
                  return ItemStack.EMPTY;
               }
            } else if (CampStation.input(this.smoke, var4)) {
               if (!this.moveItemStackTo(var4, 0, 1, false)) {
                  return ItemStack.EMPTY;
               }
            } else if (CampStation.reagent(this.smoke, var4)) {
               if (!this.moveItemStackTo(var4, 1, 2, false)) {
                  return ItemStack.EMPTY;
               }
            } else if (!this.moveItemStackTo(var4, var2 < 30 ? 30 : 3, var2 < 30 ? 39 : 30, false)) {
               return ItemStack.EMPTY;
            }

            if (var4.isEmpty()) {
               var3.setByPlayer(ItemStack.EMPTY);
            } else {
               var3.setChanged();
            }

            var3.onTake(var1, var4);
            return var5;
         }
      } else {
         return ItemStack.EMPTY;
      }
   }
}
