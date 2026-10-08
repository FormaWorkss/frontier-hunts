package com.formaworks.frontierhunts.campcook;

import com.formaworks.frontierhunts.journal.JournalApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * [licence] Dutch oven menu: six ingredient slots, bowls, coals, the dish; the player inventory. Taking dishes out
 * credits the Camp Cook journal entries (server).
 */
public class DutchOvenMenu extends AbstractContainerMenu {
   public static final int ING_X = 44, ING_Y = 20, BOWL_X = 17, BOWL_Y = 20, FUEL_X = 17, FUEL_Y = 54, OUT_X = 139, OUT_Y = 30, INV_Y = 86;
   private final Container oven;
   private final ContainerData data;
   private final BlockPos pos;

   public static DutchOvenMenu client(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
      BlockPos pos = buf == null ? BlockPos.ZERO : buf.readBlockPos();
      return new DutchOvenMenu(id, inv, new SimpleContainer(DutchOvenEntity.SIZE), new SimpleContainerData(5), pos);
   }

   public DutchOvenMenu(int id, Inventory inv, DutchOvenEntity be, ContainerData data) {
      this(id, inv, be, data, be.getBlockPos());
   }

   private DutchOvenMenu(int id, Inventory inv, Container oven, ContainerData data, BlockPos pos) {
      super(CampCookContent.MENU.get(), id);
      checkContainerSize(oven, DutchOvenEntity.SIZE);
      this.oven = oven;
      this.data = data;
      this.pos = pos;
      oven.startOpen(inv.player);
      for (int r = 0; r < 2; r++) {
         for (int c = 0; c < 3; c++) {
            this.addSlot(new Slot(oven, r * 3 + c, ING_X + c * 18, ING_Y + r * 18));
         }
      }
      this.addSlot(new Slot(oven, DutchOvenEntity.BOWL, BOWL_X, BOWL_Y) {
         @Override
         public boolean mayPlace(ItemStack s) {
            return s.is(Items.BOWL);
         }
      });
      this.addSlot(new Slot(oven, DutchOvenEntity.FUEL, FUEL_X, FUEL_Y) {
         @Override
         public boolean mayPlace(ItemStack s) {
            return DutchOvenEntity.isFuel(s);
         }
      });
      this.addSlot(new Slot(oven, DutchOvenEntity.OUT, OUT_X, OUT_Y) {
         @Override
         public boolean mayPlace(ItemStack s) {
            return false;
         }

         @Override
         public void onTake(Player p, ItemStack s) {
            credit(p, s);
            super.onTake(p, s);
         }

         @Override
         public ItemStack remove(int amount) {
            return super.remove(amount);
         }
      });
      for (int r = 0; r < 3; r++) {
         for (int c = 0; c < 9; c++) {
            this.addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, INV_Y + r * 18));
         }
      }
      for (int c = 0; c < 9; c++) {
         this.addSlot(new Slot(inv, c, 8 + c * 18, INV_Y + 58));
      }
      this.addDataSlots(data);
   }

   /** Journal: Camp Cook entries (meals cooked, different dishes). */
   static void credit(Player p, ItemStack s) {
      if (!(p instanceof ServerPlayer sp) || s.isEmpty() || !(s.getItem() instanceof CampDishItem dish)) {
         return;
      }
      try {
         String key = "campcook.dish." + dish.dish.id;
         if (JournalApi.counter(sp, key) == 0) {
            JournalApi.count(sp, "campcook.kinds", 1);
         }
         JournalApi.count(sp, key, s.getCount());
         JournalApi.count(sp, "campcook.meals", s.getCount());
      } catch (RuntimeException ignored) {
      }
   }

   public int progress() {
      return this.data.get(0);
   }

   public int total() {
      return this.data.get(1);
   }

   public int burn() {
      return this.data.get(2);
   }

   public int burnMax() {
      return this.data.get(3);
   }

   public int heat() {
      return this.data.get(4);
   }

   public Container oven() {
      return this.oven;
   }

   public BlockPos pos() {
      return this.pos;
   }

   @Override
   public boolean stillValid(Player p) {
      return this.oven.stillValid(p);
   }

   @Override
   public void removed(Player p) {
      super.removed(p);
      this.oven.stopOpen(p);
   }

   @Override
   public ItemStack quickMoveStack(Player p, int index) {
      Slot slot = this.slots.get(index);
      if (slot == null || !slot.hasItem()) {
         return ItemStack.EMPTY;
      }
      ItemStack s = slot.getItem();
      ItemStack copy = s.copy();
      int ovenEnd = DutchOvenEntity.SIZE;
      int invEnd = this.slots.size();
      if (index < ovenEnd) {
         if (!this.moveItemStackTo(s, ovenEnd, invEnd, true)) {
            return ItemStack.EMPTY;
         }
         if (index == DutchOvenEntity.OUT) {
            slot.onQuickCraft(s, copy);
            credit(p, copy.copyWithCount(copy.getCount() - s.getCount()));
         }
      } else if (s.is(Items.BOWL)) {
         if (!this.moveItemStackTo(s, DutchOvenEntity.BOWL, DutchOvenEntity.BOWL + 1, false)) {
            return ItemStack.EMPTY;
         }
      } else if (DutchOvenEntity.isFuel(s) && !isIngredient(s)) {
         if (!this.moveItemStackTo(s, DutchOvenEntity.FUEL, DutchOvenEntity.FUEL + 1, false)) {
            return ItemStack.EMPTY;
         }
      } else if (!this.moveItemStackTo(s, 0, CampCookingRecipe.SLOTS, false)) {
         return ItemStack.EMPTY;
      }
      if (s.isEmpty()) {
         slot.setByPlayer(ItemStack.EMPTY);
      } else {
         slot.setChanged();
      }
      if (s.getCount() == copy.getCount()) {
         return ItemStack.EMPTY;
      }
      if (index != DutchOvenEntity.OUT) {
         slot.onTake(p, s);
      }
      return copy;
   }

   /** Fuel that is also food stays an ingredient on shift-click (none in vanilla, but keeps modded food safe). */
   private static boolean isIngredient(ItemStack s) {
      return s.has(net.minecraft.core.component.DataComponents.FOOD) || BuiltInRegistries.ITEM.getKey(s.getItem()).getPath().contains("dried_kelp");
   }
}
