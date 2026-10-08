package com.formaworks.frontierhunts.camp;

import com.formaworks.frontierhunts.HuntContent;
import com.formaworks.frontierhunts.workshop.WideStationBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public final class CampStation extends BaseContainerBlockEntity {
   private NonNullList<ItemStack> items = NonNullList.withSize(3, ItemStack.EMPTY);
   private int progress;
   private boolean reserved;
   private boolean alt;
   public final ContainerData data = new ContainerData() {
      public int get(int var1) {
         return var1 == 0
            ? CampStation.this.progress
            : (
               var1 == 1
                  ? CampStation.this.duration()
                  : (var1 == 2 ? (CampStation.this.reserved ? 1 : 0) : (CampStation.this.getBlockState().getValue(CampStationBlock.WORKING) ? 1 : 0))
            );
      }

      public void set(int var1, int var2) {
         if (var1 == 0) {
            CampStation.this.progress = var2;
         }
      }

      public int getCount() {
         return 4;
      }
   };

   public CampStation(BlockPos var1, BlockState var2) {
      super((BlockEntityType)CampContent.STATION.get(), var1, var2);
   }

   public boolean smoke() {
      if (this.getBlockState().getBlock() instanceof CampStationBlock var1 && var1.smoke) {
         return true;
      }

      return false;
   }

   public int duration() {
      return this.smoke() ? 240 : 600;
   }

   public static boolean input(boolean var0, ItemStack var1) {
      // [survival] the smokehouse takes any raw meat or fish; the tanning rack every hide and pelt
      return var0 ? var1.is((Item)HuntContent.VENISON.get()) || com.formaworks.frontierhunts.survival.SurvivalStations.smokable(var1)
         : var1.is((Item)HuntContent.DEER_HIDE.get()) || com.formaworks.frontierhunts.survival.SurvivalStations.tannable(var1);
   }

   public static boolean reagent(boolean var0, ItemStack var1) {
      return var0 ? var1.is(Items.COAL) || var1.is(Items.CHARCOAL) : var1.is(Items.FLINT) || var1.is(Items.BONE);
   }

   public ItemStack result() {
      return this.result(this.alt);
   }

   public ItemStack result(boolean var1) {
      // [survival] smoked output follows the input (smoked + long-keeping); tanning output follows the hide
      ItemStack in = this.items.get(0);
      if (this.smoke()) {
         ItemStack r = com.formaworks.frontierhunts.survival.SurvivalStations.smoked(this.level, in);
         return r != null ? r : new ItemStack((ItemLike)HuntContent.COOKED_VENISON.get(), 4);
      } else {
         ItemStack r = com.formaworks.frontierhunts.survival.SurvivalStations.tanned(in, var1);
         if (r != null) {
            return r;
         }
         return var1 ? new ItemStack((ItemLike)HuntContent.TANNED_HIDE.get(), 1) : new ItemStack(Items.LEATHER, 2);
      }
   }

   public int inputCount() {
      return this.smoke() ? 4 : 1;
   }

   public int getContainerSize() {
      return 3;
   }

   protected NonNullList<ItemStack> getItems() {
      return this.items;
   }

   protected void setItems(NonNullList<ItemStack> var1) {
      this.items = var1;
   }

   protected Component getDefaultName() {
      return Component.translatable(this.getBlockState().getBlock().getDescriptionId());
   }

   protected AbstractContainerMenu createMenu(int var1, Inventory var2) {
      return new StationMenu(var1, var2, this);
   }

   public boolean canPlaceItem(int var1, ItemStack var2) {
      return var1 == 0 ? input(this.smoke(), var2) : var1 == 1 && reagent(this.smoke(), var2);
   }

   protected void loadAdditional(CompoundTag var1, Provider var2) {
      super.loadAdditional(var1, var2);
      this.items = NonNullList.withSize(3, ItemStack.EMPTY);
      ContainerHelper.loadAllItems(var1, this.items, var2);
      this.progress = Math.clamp((long)var1.getInt("Progress"), 0, this.duration() - 1);
      this.reserved = var1.getBoolean("Reserved");
      this.alt = var1.getBoolean("Buckskin");
      if (!this.reserved) {
         this.progress = 0;
      }
   }

   protected void saveAdditional(CompoundTag var1, Provider var2) {
      super.saveAdditional(var1, var2);
      ContainerHelper.saveAllItems(var1, this.items, var2);
      var1.putInt("Progress", this.progress);
      var1.putBoolean("Reserved", this.reserved);
      var1.putBoolean("Buckskin", this.alt);
   }

   public static void tick(Level var0, BlockPos var1, BlockState var2, CampStation var3) {
      ItemStack var4 = var3.getItem(0);
      ItemStack var5 = var3.getItem(1);
      ItemStack var6 = var3.getItem(2);
      boolean var7 = var3.reserved ? var3.alt : !var3.smoke() && var5.is(Items.BONE);
      ItemStack var8 = var3.result(var7);
      boolean var9 = var6.isEmpty() || com.formaworks.frontierhunts.survival.SurvivalStations.fits(var6, var8); // [survival] smoked batches of different hours still stack
      boolean var10 = input(var3.smoke(), var4) && var4.getCount() >= var3.inputCount() && var9;
      boolean var11 = var10 && (var3.reserved || reagent(var3.smoke(), var5));
      if (var11) {
         if (!var3.reserved) {
            var5.shrink(1);
            var3.reserved = true;
            var3.alt = var7;
         }

         if (++var3.progress >= var3.duration()) {
            var4.shrink(var3.inputCount());
            if (var6.isEmpty()) {
               var3.items.set(2, var8);
            } else {
               com.formaworks.frontierhunts.survival.SurvivalStations.merge(var6, var8); // [survival] averages freshness
            }

            var3.progress = 0;
            var3.reserved = false;
            var3.alt = false;
         }

         var3.setChanged();
      }

      if ((Boolean)var2.getValue(CampStationBlock.WORKING) != var11) {
         var0.setBlock(var1, (BlockState)var2.setValue(CampStationBlock.WORKING, var11), 3);
         if (var2.getValue(WideStationBlock.PART) == WideStationBlock.Part.LEFT) {
            BlockPos var12 = WideStationBlock.partner(var1, var2);
            BlockState var13 = var0.getBlockState(var12);
            if (var13.is(var2.getBlock()) && WideStationBlock.secondary(var13)) {
               var0.setBlock(var12, (BlockState)var13.setValue(CampStationBlock.WORKING, var11), 3);
            }
         }
      }
   }
}
