package com.formaworks.frontierhunts.landscape.ride.rig;

import com.formaworks.frontierhunts.landscape.ride.Atv;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * [atvfuel] Cargo box (27) + can cradles (2) + player inventory. Server side the container is the ATV itself; every
 * slot checks what is fitted right now, so a box unstrapped by someone else can never be used as phantom storage.
 * Buttons (vanilla container-button packet): 0 pour cans into the tank, 1 unstrap the box, 2 unbolt the carrier.
 */
public class RigMenu extends AbstractContainerMenu {
   public static final int BTN_POUR = 0;
   public static final int BTN_BOX = 1;
   public static final int BTN_CARRIER = 2;
   /** Layout shared with the screen. */
   public static final int CAN_X = 222;
   public static final int CAN_Y0 = 20;
   public static final int CAN_DY = 26;
   public static final int INV_Y = 85;

   /** Server: the ATV. Client: the ATV if it is loaded (for the live gauge), else null. */
   public final Atv atv;
   private final Container container;
   private final boolean server;
   private int flags;
   private int fuelCenti;
   private int reserveDeci;

   public RigMenu(int id, Inventory inv, Atv atv) {
      this(id, inv, atv, atv, true, AtvRig.flags(atv));
   }

   public static RigMenu client(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
      Entity e = inv.player.level().getEntity(buf.readVarInt());
      int flags = buf.readVarInt();
      return new RigMenu(id, inv, e instanceof Atv a ? a : null, new SimpleContainer(AtvRig.SLOTS), false, flags);
   }

   private RigMenu(int id, Inventory inv, Atv atv, Container container, boolean server, int flags) {
      super(RigContent.MENU.get(), id);
      checkContainerSize(container, AtvRig.SLOTS);
      this.atv = atv;
      this.container = container;
      this.server = server;
      this.flags = flags;
      for (int r = 0; r < 3; r++) {
         for (int c = 0; c < 9; c++) {
            this.addSlot(new BoxSlot(container, c + r * 9, 8 + c * 18, 18 + r * 18));
         }
      }
      for (int i = 0; i < 2; i++) {
         this.addSlot(new CanSlot(container, AtvRig.CAN_SLOT + i, CAN_X, CAN_Y0 + i * CAN_DY));
      }
      for (int r = 0; r < 3; r++) {
         for (int c = 0; c < 9; c++) {
            this.addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, INV_Y + r * 18));
         }
      }
      for (int c = 0; c < 9; c++) {
         this.addSlot(new Slot(inv, c, 8 + c * 18, INV_Y + 58));
      }
      this.addDataSlot(new DataSlot() {
         @Override
         public int get() {
            return RigMenu.this.server ? AtvRig.flags(RigMenu.this.atv) & 0xFF : RigMenu.this.flags;
         }

         @Override
         public void set(int v) {
            RigMenu.this.flags = v;
         }
      });
      this.addDataSlot(new DataSlot() {
         @Override
         public int get() {
            return RigMenu.this.server ? (int)Math.round(AtvFuel.exact(RigMenu.this.atv) * 100.0) : RigMenu.this.fuelCenti;
         }

         @Override
         public void set(int v) {
            RigMenu.this.fuelCenti = v;
         }
      });
      this.addDataSlot(new DataSlot() {
         @Override
         public int get() {
            return RigMenu.this.server ? Math.min(32000, Math.round(AtvRig.reserve(RigMenu.this.atv) * 10.0F)) : RigMenu.this.reserveDeci;
         }

         @Override
         public void set(int v) {
            RigMenu.this.reserveDeci = v;
         }
      });
   }

   public int flags() {
      return this.server ? AtvRig.flags(this.atv) : this.flags;
   }

   public boolean hasBox() {
      return (this.flags() & AtvRig.BOX) != 0;
   }

   public boolean hasCarrier() {
      return (this.flags() & AtvRig.CARRIER) != 0;
   }

   public float fuel() {
      return this.server ? (float)AtvFuel.exact(this.atv) : this.fuelCenti / 100.0F;
   }

   public float reserve() {
      return this.server ? AtvRig.reserve(this.atv) : this.reserveDeci / 10.0F;
   }

   @Override
   public boolean stillValid(Player player) {
      if (!this.server) {
         return true;
      }
      return this.atv.isAlive()
         && !this.atv.isRemoved()
         && (AtvRig.flags(this.atv) & (AtvRig.BOX | AtvRig.CARRIER)) != 0
         && (player.getVehicle() == this.atv || player.distanceToSqr(this.atv) < 64.0);
   }

   @Override
   public boolean clickMenuButton(Player player, int id) {
      if (!this.server || !this.stillValid(player) || player.isSpectator()) {
         return false;
      }
      switch (id) {
         case BTN_POUR -> AtvRig.reserveRefuel(this.atv, player, false);
         case BTN_BOX -> AtvRig.detachBox(this.atv, player);
         case BTN_CARRIER -> AtvRig.detachCarrier(this.atv, player);
         default -> {
            return false;
         }
      }
      return true;
   }

   @Override
   public void removed(Player player) {
      super.removed(player);
      if (this.server) {
         this.container.stopOpen(player);
      }
   }

   @Override
   public void broadcastChanges() {
      super.broadcastChanges();
      if (this.server && this.atv.tickCount % 5 == 0) {
         AtvRig.sync(this.atv);
      }
   }

   @Override
   public ItemStack quickMoveStack(Player player, int index) {
      Slot slot = this.slots.get(index);
      if (slot == null || !slot.hasItem() || !slot.mayPickup(player)) {
         return ItemStack.EMPTY;
      }
      ItemStack stack = slot.getItem();
      ItemStack original = stack.copy();
      int rig = AtvRig.SLOTS;
      int end = this.slots.size();
      if (index < rig) {
         if (!this.moveItemStackTo(stack, rig, end, true)) {
            return ItemStack.EMPTY;
         }
      } else {
         boolean moved = false;
         if (stack.getItem() instanceof JerryCanItem && this.hasCarrier()) {
            moved = this.moveItemStackTo(stack, AtvRig.CAN_SLOT, rig, false);
         }
         if (!stack.isEmpty() && this.hasBox()) {
            moved |= this.moveItemStackTo(stack, 0, AtvRig.BOX_SLOTS, false);
         }
         if (!moved) {
            return ItemStack.EMPTY;
         }
      }
      if (stack.isEmpty()) {
         slot.setByPlayer(ItemStack.EMPTY);
      } else {
         slot.setChanged();
      }
      return original;
   }

   final class BoxSlot extends Slot {
      BoxSlot(Container c, int i, int x, int y) {
         super(c, i, x, y);
      }

      @Override
      public boolean isActive() {
         return RigMenu.this.hasBox();
      }

      @Override
      public boolean mayPlace(ItemStack stack) {
         return RigMenu.this.hasBox();
      }

      @Override
      public boolean mayPickup(Player player) {
         return RigMenu.this.hasBox();
      }
   }

   final class CanSlot extends Slot {
      CanSlot(Container c, int i, int x, int y) {
         super(c, i, x, y);
      }

      @Override
      public boolean isActive() {
         return RigMenu.this.hasCarrier();
      }

      @Override
      public boolean mayPlace(ItemStack stack) {
         return RigMenu.this.hasCarrier() && stack.getItem() instanceof JerryCanItem;
      }

      @Override
      public boolean mayPickup(Player player) {
         return RigMenu.this.hasCarrier();
      }

      @Override
      public int getMaxStackSize() {
         return 1;
      }

      @Override
      public int getMaxStackSize(ItemStack stack) {
         return 1;
      }
   }
}
