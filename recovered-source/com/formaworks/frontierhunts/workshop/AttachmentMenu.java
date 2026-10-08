package com.formaworks.frontierhunts.workshop;

import com.formaworks.frontierhunts.expedition.AttachmentSpec;
import com.formaworks.frontierhunts.expedition.ExpeditionWeapon;
import com.formaworks.frontierhunts.rifle.RifleItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public final class AttachmentMenu extends AbstractContainerMenu {
   public static final int INSTALL = 0;
   public static final int PARTS = 100;
   private final SimpleContainer tray = new SimpleContainer(2);
   private final Inventory inventory;
   private final ContainerLevelAccess access;
   private boolean returned;

   public AttachmentMenu(int var1, Inventory var2) {
      this(var1, var2, ContainerLevelAccess.NULL);
   }

   public AttachmentMenu(int var1, Inventory var2, Level var3, BlockPos var4) {
      this(var1, var2, ContainerLevelAccess.create(var3, var4));
   }

   private AttachmentMenu(int var1, Inventory var2, ContainerLevelAccess var3) {
      super((MenuType)WorkshopContent.FITTING_MENU.get(), var1);
      this.inventory = var2;
      this.access = var3;
      this.addSlot(new Slot(this.tray, 0, 18, 44) {
         public boolean mayPlace(ItemStack var1) {
            return AttachmentMenu.equipment(var1);
         }

         public int getMaxStackSize() {
            return 1;
         }
      });
      this.addSlot(new Slot(this.tray, 1, 18, 91) {
         public boolean mayPlace(ItemStack var1) {
            return EquipmentCatalog.attachment(var1.getItem());
         }
      });

      for (int var4 = 0; var4 < 3; var4++) {
         for (int var5 = 0; var5 < 9; var5++) {
            this.addSlot(new Slot(var2, var5 + var4 * 9 + 9, 8 + var5 * 18, 139 + var4 * 18));
         }
      }

      for (int var6 = 0; var6 < 9; var6++) {
         this.addSlot(new Slot(var2, var6, 8 + var6 * 18, 197));
      }
   }

   public static boolean equipment(ItemStack var0) {
      if (var0.getItem() instanceof RifleItem) {
         return true;
      } else {
         if (var0.getItem() instanceof ExpeditionWeapon var1 && !var1.weapon.bow) {
            return true;
         }

         return false;
      }
   }

   public ItemStack gun() {
      return this.tray.getItem(0);
   }

   public ItemStack part() {
      return this.tray.getItem(1);
   }

   private List<ItemStack> snapshot() {
      ArrayList var1 = new ArrayList();
      var1.add(this.gun().copy());
      var1.add(this.part().copy());
      this.inventory.items.forEach(var1x -> var1.add(var1x.copy()));
      return var1;
   }

   public String problem() {
      if (this.gun().isEmpty()) {
         return "Place a gun in the upper slot";
      } else if (this.part().isEmpty()) {
         return "Place an attachment below";
      } else {
         String var1 = EquipmentCatalog.id(this.part().getItem());
         if (!AttachmentFitting.supported(this.gun(), var1)) {
            return "This part does not fit this gun";
         } else if (AttachmentFitting.fitted(this.gun(), var1)) {
            return "Already installed";
         } else {
            return this.installation() == null ? "Finish reload or free inventory space" : "Ready to install";
         }
      }
   }

   public boolean canInstall() {
      return !this.gun().isEmpty() && !this.part().isEmpty() && this.installation() != null;
   }

   private List<ItemStack> installation() {
      if (equipment(this.gun()) && !this.part().isEmpty()) {
         String var1 = EquipmentCatalog.id(this.part().getItem());
         if (EquipmentCatalog.PARTS.contains(var1) && AttachmentFitting.supported(this.gun(), var1)) {
            List var2 = this.snapshot();

            for (String var4 : EquipmentCatalog.PARTS) {
               if (!var4.equals(var1) && AttachmentSpec.exclusive(var1, var4) && AttachmentFitting.fitted((ItemStack)var2.get(0), var4)) {
                  var2 = AttachmentFitting.plan(var2, 0, var4, false);
                  if (var2 == null) {
                     return null;
                  }
               }
            }

            return AttachmentFitting.plan(var2, 0, var1, true);
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   public boolean clickMenuButton(Player var1, int var2) {
      if (!(var1 instanceof ServerPlayer var3) || var3.containerMenu != this || var3.isSpectator() || !this.stillValid(var3)) {
         return false;
      }

      if (var2 == 100) {
         this.access
            .execute(
               (var1x, var2x) -> var3.openMenu(
                     new SimpleMenuProvider((var2xx, var3x, var4x) -> new WorkbenchMenu(var2xx, var3x, var1x, var2x), Component.literal("Attachment supplies"))
                  )
            );
         return true;
      } else {
         List var4;
         if (var2 == 0) {
            var4 = this.installation();
         } else {
            int var5 = var2 - 1;
            if (var5 < 0 || var5 >= EquipmentCatalog.PARTS.size()) {
               return false;
            }

            var4 = AttachmentFitting.plan(this.snapshot(), 0, EquipmentCatalog.PARTS.get(var5), false);
         }

         if (var4 == null) {
            return false;
         } else {
            this.tray.setItem(0, (ItemStack)var4.get(0));
            this.tray.setItem(1, (ItemStack)var4.get(1));

            for (int var6 = 0; var6 < this.inventory.items.size(); var6++) {
               this.inventory.setItem(var6, (ItemStack)var4.get(var6 + 2));
            }

            this.inventory.setChanged();
            this.broadcastChanges();
            this.access.execute((var0, var1x) -> var0.playSound(null, var1x, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.45F, 1.25F));
            return true;
         }
      }
   }

   public boolean stillValid(Player var1) {
      return var1.isAlive()
         && (Boolean)this.access
            .evaluate(
               (var1x, var2) -> var1x.getBlockState(var2).is((Block)WorkshopContent.ATTACHMENT_BENCH.get()) && var1.distanceToSqr(var2.getCenter()) <= 64.0,
               true
            );
   }

   public ItemStack quickMoveStack(Player var1, int var2) {
      if (var2 >= 0 && var2 < this.slots.size()) {
         Slot var3 = (Slot)this.slots.get(var2);
         if (!var3.hasItem()) {
            return ItemStack.EMPTY;
         } else {
            ItemStack var4 = var3.getItem();
            ItemStack var5 = var4.copy();
            boolean var6;
            if (var2 < 2) {
               var6 = this.moveItemStackTo(var4, 2, 38, true);
            } else if (equipment(var4)) {
               var6 = this.moveItemStackTo(var4, 0, 1, false);
            } else if (EquipmentCatalog.attachment(var4.getItem())) {
               var6 = this.moveItemStackTo(var4, 1, 2, false);
            } else {
               var6 = var2 < 29 ? this.moveItemStackTo(var4, 29, 38, false) : this.moveItemStackTo(var4, 2, 29, false);
            }

            if (!var6) {
               return ItemStack.EMPTY;
            } else {
               if (var4.isEmpty()) {
                  var3.setByPlayer(ItemStack.EMPTY);
               } else {
                  var3.setChanged();
               }

               var3.onTake(var1, var4);
               return var5;
            }
         }
      } else {
         return ItemStack.EMPTY;
      }
   }

   public void removed(Player var1) {
      super.removed(var1);
      if (!var1.level().isClientSide && !this.returned) {
         this.returned = true;
         this.clearContainer(var1, this.tray);
      }
   }
}
