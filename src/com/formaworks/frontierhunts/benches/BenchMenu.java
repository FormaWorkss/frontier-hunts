package com.formaworks.frontierhunts.benches;

import com.formaworks.frontierhunts.expedition.ExpeditionService;
import com.formaworks.frontierhunts.hunting.ArrowTip;
import com.formaworks.frontierhunts.hunting.ArrowTipItem;
import com.formaworks.frontierhunts.workshop.EquipmentCatalog;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.EventHooks;

/**
 * [benches] The menu behind all three benches. It has no slots: the bench works straight from the player's main
 * inventory. The screen sends button clicks (vanilla's container button packet); the server checks the player, the
 * menu, the distance to the bench and a short anti-spam delay, recomputes the plan from its own inventory
 * ({@link BenchCrafting}) and applies it. Button ids:
 * <ul>
 *   <li>{@code 0 .. 9999} craft entry i of {@link BenchCatalog#entries} once; {@code 10000 + i} five times (Reloading
 *       Bench only);</li>
 *   <li>{@code 20000 + part * 2 + (fit ? 0 : 1)} fit / remove {@link EquipmentCatalog#PARTS}[part] on the held item
 *       (Gunsmith's Bench, and the fishing drag kit at the Frontier Workbench);</li>
 *   <li>{@code 40000 + (slot * 16 + code) * 2 + (whole stack ? 1 : 0)} refit the arrow stack in an inventory slot with a
 *       head ({@link ArrowTip} ordinal, 15 = stock head back) (Reloading Bench).</li>
 * </ul>
 * Every craft awards the crafted stat, fires {@code PlayerEvent.ItemCraftedEvent} (the Handbook's task tracker and food
 * spoilage listen to it), counts for the expedition "craft" missions and plays the bench sound.
 */
public final class BenchMenu extends AbstractContainerMenu {
   public static final int BATCH = 10000;
   public static final int FIT = 20000;
   public static final int REFIT = 40000;
   private static final int REFIT_END = REFIT + BenchCrafting.REFIT_SLOTS * 16 * 2;
   /** Server: ticks between two crafts of one player (anti-spam, as the old workbench). */
   private static final long CRAFT_GAP = 8L, REFIT_GAP = 4L;

   public final Bench bench;
   private final Inventory inventory;
   private final ContainerLevelAccess access;
   private final List<BenchCatalog.Entry> entries;
   private long lastAction = Long.MIN_VALUE;
   private int sentInventory;

   /** Client side (from the menu type). */
   public BenchMenu(int id, Inventory inv, Bench bench) {
      this(id, inv, ContainerLevelAccess.NULL, inv.player.level(), bench);
   }

   /** Server side: the bench at {@code pos} (its left half). */
   public BenchMenu(int id, Inventory inv, Level level, BlockPos pos, Bench bench) {
      this(id, inv, ContainerLevelAccess.create(level, pos), level, bench);
   }

   private BenchMenu(int id, Inventory inv, ContainerLevelAccess access, Level level, Bench bench) {
      super(bench.menu(), id);
      this.bench = bench;
      this.inventory = inv;
      this.access = access;
      this.entries = BenchCatalog.entries(level.getRecipeManager(), level.registryAccess(), bench);
   }

   public List<BenchCatalog.Entry> entries() {
      return this.entries;
   }

   public Player player() {
      return this.inventory.player;
   }

   /** The Reloading Bench recipe that makes {@code tip} heads (null if there is none). */
   public BenchCatalog.Entry headRecipe(ArrowTip tip) {
      if (this.bench != Bench.RELOADING) {
         return null;
      }
      for (BenchCatalog.Entry e : this.entries) {
         if (e.product.getItem() instanceof ArrowTipItem t && t.tip == tip) {
            return e;
         }
      }
      return null;
   }

   public static int fitButton(String part, boolean on) {
      int i = EquipmentCatalog.PARTS.indexOf(part);
      return i < 0 ? -1 : FIT + i * 2 + (on ? 0 : 1);
   }

   public static int refitButton(int slot, int code, boolean all) {
      return REFIT + (slot * 16 + code) * 2 + (all ? 1 : 0);
   }

   /** Can this bench fit that part (Gunsmith: every weapon part; Frontier: the fishing drag kit)? */
   public boolean fits(String part) {
      return this.bench == Bench.GUNSMITH || this.bench == Bench.FRONTIER && part.equals("fishing_drag_kit");
   }

   @Override
   public boolean stillValid(Player p) {
      return p.isAlive() && this.access.evaluate((level, pos) -> Bench.of(level.getBlockState(pos)) == this.bench
         && p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0, true);
   }

   @Override
   public ItemStack quickMoveStack(Player p, int index) {
      return ItemStack.EMPTY;
   }

   @Override
   public void broadcastChanges() {
      super.broadcastChanges();
      // The bench has no slots of its own, and while another menu is open the client only takes inventory-menu slot
      // updates for the hotbar: send the whole inventory when it changed (a craft, a pickup...) so the screen is current.
      if (this.inventory.player instanceof ServerPlayer sp && sp.containerMenu == this) {
         int h = fingerprint(this.inventory);
         if (h != this.sentInventory) {
            this.sentInventory = h;
            sp.inventoryMenu.broadcastFullState();
         }
      }
   }

   /** A cheap hash of the whole inventory (items, counts, components). */
   public static int fingerprint(Inventory inv) {
      int h = 1;
      for (int i = 0; i < inv.getContainerSize(); i++) {
         ItemStack s = inv.getItem(i);
         h = 31 * h + (s.isEmpty() ? 0 : ItemStack.hashItemAndComponents(s) * 31 + s.getCount());
      }
      return 31 * h + inv.selected;
   }

   // ============================================================================================ server actions

   @Override
   public boolean clickMenuButton(Player player, int id) {
      if (!(player instanceof ServerPlayer sp) || sp.containerMenu != this || sp.isSpectator() || !this.stillValid(sp)) {
         return false;
      }
      long now = sp.level().getGameTime();
      if (id >= REFIT && id < REFIT_END) {
         return this.bench == Bench.RELOADING && this.gap(now, REFIT_GAP) && this.refit(sp, now, id - REFIT);
      }
      if (id >= FIT && id < FIT + EquipmentCatalog.PARTS.size() * 2) {
         int part = (id - FIT) / 2;
         return this.gap(now, REFIT_GAP) && this.fit(sp, now, EquipmentCatalog.PARTS.get(part), (id - FIT) % 2 == 0);
      }
      boolean batch = id >= BATCH && id < BATCH * 2;
      int index = batch ? id - BATCH : id;
      if (index < 0 || index >= this.entries.size() || batch && !this.bench.batches() || !this.gap(now, CRAFT_GAP)) {
         return false;
      }
      return this.craft(sp, now, this.entries.get(index), batch ? 5 : 1);
   }

   private boolean gap(long now, long gap) {
      return this.lastAction == Long.MIN_VALUE || now < this.lastAction || now - this.lastAction >= gap;
   }

   private boolean craft(ServerPlayer sp, long now, BenchCatalog.Entry e, int times) {
      Inventory inv = sp.getInventory();
      // all or nothing: the whole batch must be possible with the server's inventory
      if (BenchCrafting.craft(sp, e, BenchCrafting.copy(inv), times) == null) {
         return false;
      }
      this.lastAction = now;
      int made = 0;
      for (int t = 0; t < times; t++) {
         List<ItemStack> consumed = new ArrayList<>();
         List<ItemStack> after = BenchCrafting.consume(sp, e, BenchCrafting.copy(inv), consumed);
         if (after == null) {
            break;
         }
         apply(inv, after);
         made++;
         if (e.sewing()) {
            continue;
         }
         ItemStack product = e.product.copy();
         product.onCraftedBy(sp.level(), sp, product.getCount()); // crafted stat + the item's own hook
         sp.triggerRecipeCrafted(e.holder, consumed);
         SimpleContainer used = new SimpleContainer(Math.max(1, consumed.size()));
         for (int i = 0; i < consumed.size(); i++) {
            used.setItem(i, consumed.get(i));
         }
         EventHooks.firePlayerCraftingEvent(sp, product, used); // listeners may stamp the product (food spoilage)
         if (!inv.add(product) && !product.isEmpty()) {
            sp.drop(product, false);
         }
      }
      if (made == 0) {
         return false;
      }
      this.synced(sp);
      if (!e.sewing()) {
         ExpeditionService.record(sp, "craft", "*", made, 0.0);
      }
      float pitch = switch (this.bench) {
         case FRONTIER -> 1.0F;
         case GUNSMITH -> 1.1F;
         case RELOADING -> 1.25F;
      };
      this.access.execute((level, pos) -> level.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.55F, pitch));
      return true;
   }

   private boolean fit(ServerPlayer sp, long now, String part, boolean on) {
      if (!this.fits(part)) {
         return false;
      }
      List<ItemStack> plan = BenchCrafting.fit(sp.getInventory(), part, on);
      if (plan == null) {
         return false;
      }
      this.lastAction = now;
      apply(sp.getInventory(), plan);
      this.synced(sp);
      this.access.execute((level, pos) -> level.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.45F, 1.3F));
      return true;
   }

   private boolean refit(ServerPlayer sp, long now, int raw) {
      int code = raw / 2;
      int slot = code / 16;
      int head = code % 16;
      boolean all = (raw & 1) != 0;
      BenchCatalog.Entry recipe = head < ArrowTip.values().length ? this.headRecipe(ArrowTip.byOrdinal(head)) : null;
      List<ItemStack> plan = BenchCrafting.refit(sp, BenchCrafting.copy(sp.getInventory()), slot, head, all, recipe);
      if (plan == null) {
         return false;
      }
      this.lastAction = now;
      apply(sp.getInventory(), plan);
      this.synced(sp);
      this.access.execute((level, pos) -> level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.5F, 1.4F));
      return true;
   }

   private static void apply(Inventory inv, List<ItemStack> plan) {
      for (int i = 0; i < plan.size() && i < inv.items.size(); i++) {
         if (!ItemStack.matches(inv.items.get(i), plan.get(i))) {
            inv.setItem(i, plan.get(i));
         }
      }
      inv.setChanged();
   }

   private void synced(ServerPlayer sp) {
      this.sentInventory = fingerprint(sp.getInventory());
      sp.inventoryMenu.broadcastFullState();
   }
}
