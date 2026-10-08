package com.formaworks.frontierhunts.landscape.stand;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public final class BowStandBlockEntity extends BlockEntity {
   private final ItemStack[] items = new ItemStack[]{ItemStack.EMPTY, ItemStack.EMPTY};

   public BowStandBlockEntity(BlockPos var1, BlockState var2) {
      super((BlockEntityType)BowStandContent.ENTITY.get(), var1, var2);
   }

   public ItemStack get(int var1) {
      return this.items[var1];
   }

   public void set(int var1, ItemStack var2) {
      this.items[var1] = var2;
      this.setChanged();
      if (this.level != null) {
         this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 2);
      }
   }

   public static int slotFor(ItemStack var0) {
      if (var0.isEmpty()) {
         return -1;
      } else {
         String var1 = BuiltInRegistries.ITEM.getKey(var0.getItem()).getPath();
         if (var1.contains("quiver")) {
            return 1;
         } else if (var0.getItem() instanceof ProjectileWeaponItem || var0.getItem() instanceof CrossbowItem) {
            return 0;
         } else {
            return !var1.endsWith("_bow") && !var1.equals("bow") && !var1.equals("crossbow") && !var1.endsWith("crossbow") ? -1 : 0;
         }
      }
   }

   protected void saveAdditional(CompoundTag var1, Provider var2) {
      super.saveAdditional(var1, var2);

      for (int var3 = 0; var3 < 2; var3++) {
         if (!this.items[var3].isEmpty()) {
            var1.put(var3 == 0 ? "Bow" : "Quiver", this.items[var3].save(var2));
         }
      }
   }

   protected void loadAdditional(CompoundTag var1, Provider var2) {
      super.loadAdditional(var1, var2);

      for (int var3 = 0; var3 < 2; var3++) {
         String var4 = var3 == 0 ? "Bow" : "Quiver";
         this.items[var3] = var1.contains(var4) ? ItemStack.parse(var2, var1.getCompound(var4)).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
      }
   }

   public CompoundTag getUpdateTag(Provider var1) {
      CompoundTag var2 = new CompoundTag();
      this.saveAdditional(var2, var1);
      var2.putBoolean("Sync", true);
      return var2;
   }

   public Packet<ClientGamePacketListener> getUpdatePacket() {
      return ClientboundBlockEntityDataPacket.create(this);
   }
}
