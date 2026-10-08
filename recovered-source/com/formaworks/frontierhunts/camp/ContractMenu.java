package com.formaworks.frontierhunts.camp;

import com.formaworks.frontierhunts.HuntContent;
import com.formaworks.frontierhunts.HuntRules;
import com.formaworks.frontierhunts.HunterLedger;
import com.formaworks.frontierhunts.progression.AssignmentService;
import com.formaworks.frontierhunts.rifle.RifleContent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public final class ContractMenu extends AbstractContainerMenu {
   // [economy] ranger board trades (tokens); value model and reasoning: tools/economy, docs/ws/economy.md
   public static final int BOARD_VENISON_PAY = 12;   // 4 cooked venison (hunting income, unchanged)
   public static final int BOARD_LEATHER_PAY = 5;    // 2 leather (was 10: cow leather turned into tokens at 3x its value)
   public static final int BOARD_ARROWS_PRICE = 3;   // 4 hunting arrows (was 8: 7x their crafting value)
   public static final int BOARD_AMMO_PRICE = 6;     // 8 Reserve .308 (was 16: 3x their crafting value)
   private final ContainerLevelAccess access;
   private final ContainerData data;
   private long lastTrade = Long.MIN_VALUE;

   public ContractMenu(int var1, Inventory var2) {
      this(var1, var2, ContainerLevelAccess.NULL, new SimpleContainerData(7));
   }

   public ContractMenu(int var1, final Inventory var2, final Level var3, BlockPos var4) {
      this(var1, var2, ContainerLevelAccess.create(var3, var4), new ContainerData() {
         public int get(int var1) {
            if (!(var2.player instanceof ServerPlayer var2x)) {
               return 0;
            } else {
               HunterLedger.Hunter var4 = HunterLedger.get(var2x.serverLevel()).hunter(var2x.getUUID());

               return switch (var1) {
                  case 0 -> var4.tokens();
                  case 1 -> var4.provisionsDelivered();
                  case 2 -> ContractMenu.count(var2, (Item)HuntContent.COOKED_VENISON.get());
                  case 3 -> ContractMenu.count(var2, Items.LEATHER);
                  case 4 -> AssignmentService.fieldLevel(var3) && !var2x.isCreative() && !var2x.isSpectator() ? 1 : 0; // [academy] the board trades in any Overworld game
                  case 5 -> ContractMenu.room(var2, new ItemStack((ItemLike)HuntContent.FIELD_ARROW.get(), 4)) ? 1 : 0;
                  case 6 -> ContractMenu.room(var2, new ItemStack((ItemLike)RifleContent.AMMO.get(), 8)) ? 1 : 0;
                  default -> 0;
               };
            }
         }

         public void set(int var1, int var2x) {
         }

         public int getCount() {
            return 7;
         }
      });
   }

   private ContractMenu(int var1, Inventory var2, ContainerLevelAccess var3, ContainerData var4) {
      super((MenuType)CampContent.CONTRACT_MENU.get(), var1);
      this.access = var3;
      this.data = var4;
      this.addDataSlots(var4);
   }

   public int value(int var1) {
      return this.data.get(var1);
   }

   public boolean stillValid(Player var1) {
      return var1.isAlive() && stillValid(this.access, var1, (Block)CampContent.CONTRACT_BOARD.get());
   }

   public ItemStack quickMoveStack(Player var1, int var2) {
      return ItemStack.EMPTY;
   }

   public boolean clickMenuButton(Player var1, int var2) {
      if (var1 instanceof ServerPlayer var3
         && var2 >= 0
         && var2 <= 3
         && var3.containerMenu == this
         && this.stillValid(var3)
         && !var3.isCreative()
         && !var3.isSpectator()
         && AssignmentService.fieldLevel(var3.level())) { // [academy] deliveries count without the reserve rule
         long var4 = var3.level().getGameTime();
         if (this.lastTrade != Long.MIN_VALUE && var4 - this.lastTrade < 8L) {
            return false;
         }

         this.lastTrade = var4;
         Inventory var6 = var3.getInventory();
         HunterLedger var7 = HunterLedger.get(var3.serverLevel());
         if (var2 < 2) {
            Item var8 = var2 == 0 ? (Item)HuntContent.COOKED_VENISON.get() : Items.LEATHER;
            int var9 = var2 == 0 ? 4 : 2;
            int var10 = var2 == 0 ? BOARD_VENISON_PAY : BOARD_LEATHER_PAY; // [economy]
            if (var2 == 0 && var3 instanceof net.minecraft.server.level.ServerPlayer sp && com.formaworks.frontierhunts.camps.CampPerks.tier(sp) >= 2) {
               var10 = Math.round(var10 * 1.25F); // [1.1.6] wall-tent camp: the meat pole pays 25% more
            }
            if (count(var6, var8) < var9 || !var7.provisionPayment(var3.getUUID(), var10)) {
               return false;
            }

            for (ItemStack var12 : var6.items) {
               if (var12.is(var8)) {
                  int var13 = Math.min(var9, var12.getCount());
                  var12.shrink(var13);
                  var9 -= var13;
                  if (var9 == 0) {
                     break;
                  }
               }
            }
         } else {
            ItemStack var14 = var2 == 2 ? new ItemStack((ItemLike)HuntContent.FIELD_ARROW.get(), 4) : new ItemStack((ItemLike)RifleContent.AMMO.get(), 8);
            if (!room(var6, var14) || !var7.spend(var3.getUUID(), var2 == 2 ? BOARD_ARROWS_PRICE : BOARD_AMMO_PRICE)) { // [economy]
               return false;
            }

            var6.add(var14);
            if (!var14.isEmpty()) {
               throw new IllegalStateException("Validated ranger supply no longer fits");
            }
         }

         var6.setChanged();
         var3.inventoryMenu.broadcastFullState();
         this.broadcastChanges();
         if (var2 < 2) {
            AssignmentService.delivery(var3);
         }

         return true;
      }

      return false;
   }

   private static int count(Inventory var0, Item var1) {
      int var2 = 0;

      for (ItemStack var4 : var0.items) {
         if (var4.is(var1)) {
            var2 += var4.getCount();
         }
      }

      return var2;
   }

   private static boolean room(Inventory var0, ItemStack var1) {
      int var2 = 0;

      for (ItemStack var4 : var0.items) {
         if (var4.isEmpty()) {
            return true;
         }

         if (ItemStack.isSameItemSameComponents(var4, var1)) {
            var2 += Math.max(0, Math.min(var0.getMaxStackSize(), var4.getMaxStackSize()) - var4.getCount());
         }
      }

      return var2 >= var1.getCount();
   }
}
