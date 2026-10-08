package com.formaworks.frontierhunts.expedition;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestLidController;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class StationStorage extends RandomizableContainerBlockEntity {
   private NonNullList<ItemStack> items;
   private final ChestLidController lid = new ChestLidController();
   private final ContainerOpenersCounter openers = new ContainerOpenersCounter() {
      protected void onOpen(Level var1, BlockPos var2, BlockState var3) {
         StationStorage.sound(var1, var2, SoundEvents.CHEST_OPEN, 0.92F);
      }

      protected void onClose(Level var1, BlockPos var2, BlockState var3) {
         StationStorage.sound(var1, var2, SoundEvents.CHEST_CLOSE, 0.86F);
      }

      protected void openerCountChanged(Level var1, BlockPos var2, BlockState var3, int var4, int var5) {
         var1.blockEvent(var2, var3.getBlock(), 1, var5);
      }

      protected boolean isOwnContainer(Player var1) {
         if (var1.containerMenu instanceof ChestMenu var2 && var2.getContainer() == StationStorage.this) {
            return true;
         }

         return false;
      }
   };

   private static void sound(Level var0, BlockPos var1, SoundEvent var2, float var3) {
      var0.playSound(
         null,
         (double)var1.getX() + 0.5,
         (double)var1.getY() + 0.5,
         (double)var1.getZ() + 0.5,
         var2,
         SoundSource.BLOCKS,
         0.5F,
         var3 + var0.random.nextFloat() * 0.08F
      );
      var0.playSound(
         null, (double)var1.getX() + 0.5, (double)var1.getY() + 0.5, (double)var1.getZ() + 0.5, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.25F, 0.7F
      );
   }

   public float openness(float var1) {
      return this.lid.getOpenness(var1);
   }

   public static void clientTick(Level var0, BlockPos var1, BlockState var2, StationStorage var3) {
      var3.lid.tickLid();
   }

   public boolean triggerEvent(int var1, int var2) {
      if (var1 == 1) {
         this.lid.shouldBeOpen(var2 > 0);
         return true;
      } else {
         return super.triggerEvent(var1, var2);
      }
   }

   public void startOpen(Player var1) {
      if (!this.remove && !var1.isSpectator() && this.isChest()) {
         this.openers.incrementOpeners(var1, this.getLevel(), this.getBlockPos(), this.getBlockState());
      }
   }

   public void stopOpen(Player var1) {
      if (!this.remove && !var1.isSpectator() && this.isChest()) {
         this.openers.decrementOpeners(var1, this.getLevel(), this.getBlockPos(), this.getBlockState());
      }
   }

   public void recheckOpen() {
      if (!this.remove && this.isChest()) {
         this.openers.recheckOpeners(this.getLevel(), this.getBlockPos(), this.getBlockState());
      }
   }

   public StationStorage(BlockPos var1, BlockState var2) {
      super((BlockEntityType)ExpeditionContent.STORAGE.get(), var1, var2);
      this.items = NonNullList.withSize(this.isTrophy() ? 1 : (this.isBowRack() ? 2 : (this.isRack() ? 3 : 27)), ItemStack.EMPTY);
   }

   public boolean isTrophy() {
      if (this.getBlockState().getBlock() instanceof ExpeditionStation var1 && var1.id.equals("trophy_plinth")) {
         return true;
      }

      return false;
   }

   public boolean isRack() {
      if (this.getBlockState().getBlock() instanceof ExpeditionStation var1 && var1.id.equals("gun_rack")) {
         return true;
      }

      return false;
   }

   public boolean isBowRack() {
      if (this.getBlockState().getBlock() instanceof ExpeditionStation var1 && var1.id.equals("bow_tuning_rack")) {
         return true;
      }

      return false;
   }

   private boolean isChest() {
      return !this.isTrophy() && !this.isRack() && !this.isBowRack();
   }

   public int getContainerSize() {
      return this.items.size();
   }

   protected NonNullList<ItemStack> getItems() {
      return this.items;
   }

   protected void setItems(NonNullList<ItemStack> var1) {
      this.items = var1;
   }

   protected Component getDefaultName() {
      return Component.literal(this.isTrophy() ? "Trophy display" : (this.isBowRack() ? "Bow display" : (this.isRack() ? "Gun rack" : "Lodge stores")));
   }

   protected AbstractContainerMenu createMenu(int var1, Inventory var2) {
      return this.isChest() ? ChestMenu.threeRows(var1, var2, this) : null;
   }

   protected void saveAdditional(CompoundTag var1, Provider var2) {
      super.saveAdditional(var1, var2);
      if (!this.trySaveLootTable(var1)) {
         ContainerHelper.saveAllItems(var1, this.items, var2);
      }
   }

   protected void loadAdditional(CompoundTag var1, Provider var2) {
      super.loadAdditional(var1, var2);
      this.items = NonNullList.withSize(this.isTrophy() ? 1 : (this.isBowRack() ? 2 : (this.isRack() ? 3 : 27)), ItemStack.EMPTY);
      this.setLootTable(null);
      this.setLootTableSeed(0L);
      if (!this.tryLoadLootTable(var1)) {
         ContainerHelper.loadAllItems(var1, this.items, var2);
      }
   }

   public void setChanged() {
      super.setChanged();
      if (this.level != null && !this.level.isClientSide) {
         this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
      }
   }

   public CompoundTag getUpdateTag(Provider var1) {
      return this.saveWithoutMetadata(var1);
   }

   public ClientboundBlockEntityDataPacket getUpdatePacket() {
      return ClientboundBlockEntityDataPacket.create(this);
   }
}
