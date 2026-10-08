package com.formaworks.frontierhunts.workshop;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.HuntContent;
import com.formaworks.frontierhunts.expedition.ExpeditionGear;
import com.formaworks.frontierhunts.expedition.ExpeditionService;
import com.formaworks.frontierhunts.hunting.ArrowTip;
import com.formaworks.frontierhunts.hunting.ArrowTipItem;
import com.formaworks.frontierhunts.rifle.RifleContent;
import com.formaworks.frontierhunts.rifle.RifleItem;
import com.formaworks.frontierhunts.rifle.RifleState;
import com.formaworks.frontierhunts.recipes.ClothingTable;
import com.formaworks.frontierhunts.recipes.ClothingTableRecipe;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;

public final class WorkbenchMenu extends AbstractContainerMenu {
   private final ContainerLevelAccess access;
   private final Inventory inventory;
   private final ContainerData ready;
   private final List<RecipeHolder<CraftingRecipe>> recipes;
   public final boolean attachments;
   public final WorkshopKind kind;
   private final int[] availability;
   private List<ItemStack> lastInventory = List.of();
   private long checkedTick = Long.MIN_VALUE;
   private boolean lastCreative;
   private int lastSelected = -1;
   private long traded = Long.MIN_VALUE;
   public static final int TIP_BUTTON = 30000;
   public static final int TIP_ACTIONS = ArrowTip.values().length * 4;
   private int tipStart;
   /** [bows] Refit one inventory arrow stack: REFIT_BUTTON + ((slot * 16 + code) * 2 + (whole stack ? 1 : 0)). */
   public static final int REFIT_BUTTON = 40000;
   /** [bows] Refit code that takes the fitted heads off and puts the shaft's stock head back. */
   public static final int REFIT_STOCK = 15;
   public static final int REFIT_SLOTS = 36;
   private static final int REFIT_ACTIONS = REFIT_SLOTS * 16 * 2;

   public WorkbenchMenu(int var1, Inventory var2) {
      this(var1, var2, false);
   }

   public WorkbenchMenu(int var1, Inventory var2, boolean var3) {
      this(var1, var2, var3 ? WorkshopKind.ATTACHMENTS : WorkshopKind.WEAPONS);
   }

   public WorkbenchMenu(int var1, Inventory var2, WorkshopKind var3) {
      this(var1, var2, ContainerLevelAccess.NULL, var2.player.level(), false, var3);
   }

   public WorkbenchMenu(int var1, Inventory var2, Level var3, BlockPos var4) {
      this(var1, var2, ContainerLevelAccess.create(var3, var4), var3, true, Objects.requireNonNull(WorkshopKind.of(var3.getBlockState(var4))));
   }

   private WorkbenchMenu(int var1, Inventory var2, ContainerLevelAccess var3, Level var4, boolean var5, WorkshopKind var6) {
      super(var6.menu(), var1);
      this.access = var3;
      this.inventory = var2;
      this.kind = var6;
      this.attachments = var6 == WorkshopKind.ATTACHMENTS;
      this.recipes = EquipmentCatalog.recipes(var4, var6);
      this.tipStart = this.recipes.size() + 2 + EquipmentCatalog.PARTS.size() * 2 + (var6 == WorkshopKind.AMMUNITION ? this.recipes.size() : 0);
      this.availability = new int[this.tipStart + (var6 == WorkshopKind.BOWS ? TIP_ACTIONS : 0)];
      this.ready = (ContainerData)(var5 ? new ContainerData() {
         public int get(int var1) {
            WorkbenchMenu.this.updateAvailability();
            return WorkbenchMenu.this.availability[var1];
         }

         public void set(int var1, int var2) {
         }

         public int getCount() {
            return WorkbenchMenu.this.availability.length;
         }
      } : new SimpleContainerData(this.availability.length));
      this.addDataSlots(this.ready);
   }

   public static int category(ItemStack var0) {
      if (EquipmentCatalog.ammunition(var0.getItem())) {
         return 1;
      } else if (var0.getItem() instanceof ExpeditionGear) {
         return 3;
      } else {
         String var1 = BuiltInRegistries.ITEM.getKey(var0.getItem()).getPath();
         if (var1.endsWith("round") || var1.endsWith("shell") || var1.endsWith("dart") || var1.equals("bowfishing_arrow")) {
            return 1;
         } else if (var1.equals("bowfishing_bow")) {
            return 2;
         } else if (var0.is((Item)WorkshopContent.OPTIC.get())) {
            return 3;
         } else if (var0.getItem() instanceof FieldRodItem) {
            return 2;
         } else {
            return !var0.is((Item)RifleContent.AMMO.get()) && !ArrowTip.arrow(var0) && !(var0.getItem() instanceof ArrowTipItem) ? 0 : 1;
         }
      }
   }

   public static List<RecipeHolder<CraftingRecipe>> catalog(Level var0) {
      return EquipmentCatalog.recipes(var0, false);
   }

   private void updateAvailability() {
      long var1 = this.inventory.player.level().getGameTime();
      if (this.checkedTick != var1) {
         this.checkedTick = var1;
         boolean var3 = this.inventory.player.hasInfiniteMaterials();
         boolean var4 = this.lastInventory.size() != this.inventory.items.size() || var3 != this.lastCreative || this.lastSelected != this.inventory.selected;

         for (int var5 = 0; !var4 && var5 < this.lastInventory.size(); var5++) {
            var4 = !ItemStack.matches(this.lastInventory.get(var5), (ItemStack)this.inventory.items.get(var5));
         }

         if (var4) {
            this.lastInventory = this.copy();
            this.lastCreative = var3;
            this.lastSelected = this.inventory.selected;

            for (int var6 = 0; var6 < this.availability.length; var6++) {
               this.availability[var6] = this.actionPlan(var6) != null ? 1 : 0;
            }
         }
      }
   }

   public int attachmentAction(String var1, boolean var2) {
      int var3 = EquipmentCatalog.PARTS.indexOf(var1);
      return var3 < 0 ? -1 : this.recipes.size() + 2 + var3 * 2 + (var2 ? 0 : 1);
   }

   public static int tipAction(ArrowTip var0, boolean var1, boolean var2) {
      return var0.ordinal() * 4 + (var1 ? 2 : 0) + (var2 ? 1 : 0);
   }

   public boolean readyTip(int var1) {
      return this.kind == WorkshopKind.BOWS && var1 >= 0 && var1 < TIP_ACTIONS && this.ready(this.tipStart + var1);
   }

   private List<ItemStack> tipPlan(int var1) {
      if (this.kind == WorkshopKind.BOWS && var1 >= 0 && var1 < TIP_ACTIONS) {
         ArrowTip var2 = ArrowTip.byOrdinal(var1 / 4);
         boolean var3 = (var1 & 2) != 0;
         boolean var4 = (var1 & 1) != 0;
         boolean var5 = this.inventory.player.hasInfiniteMaterials();
         if (this.source(this.inventory.items, var2, var3) < 0) {
            return null;
         } else {
            List<ItemStack> var6 = this.copy();
            int var7 = this.source(var6, var2, var3);
            if (var7 < 0) {
               return null;
            } else {
               int var8 = var4 ? ((ItemStack)var6.get(var7)).getCount() : 1;
               if (!var5) {
                  int var9 = this.heads(var6, var2);

                  for (int var10 = 0; var10 < 8 && var9 < var8; var10++) {
                     List<ItemStack> var11 = this.craftHead(var6, var2);
                     if (var11 == null) {
                        break;
                     }

                     int var12 = this.heads(var11, var2);
                     if (var12 <= var9) {
                        break;
                     }

                     var6 = var11;
                     var9 = var12;
                     var7 = this.source(var11, var2, var3);
                     if (var7 < 0) {
                        return null;
                     }
                  }

                  var8 = Math.min(var8, var9);
               }

               if (var8 <= 0) {
                  return null;
               } else {
                  ItemStack var19 = (ItemStack)var6.get(var7);
                  int var20 = Math.min(var8, var19.getCount());
                  boolean var21 = ArrowTip.fitted(var19);
                  ArrowTip var22 = ArrowTip.of(var19);
                  ItemStack var13 = ArrowTip.arrowStack(var3, var2, var20);
                  var19.shrink(var20);
                  if (!var5) {
                     int var14 = var20;

                     for (ItemStack var16 : var6) {
                        if (var14 > 0) {
                           Item var18 = var16.getItem();
                           if (var18 instanceof ArrowTipItem) {
                              ArrowTipItem var17 = (ArrowTipItem)var18;
                              if (var17.tip == var2) {
                                 int var23 = Math.min(var14, var16.getCount());
                                 var16.shrink(var23);
                                 var14 -= var23;
                              }
                           }
                        }
                     }
                  }

                  if (!insert(var6, var13)) {
                     return null;
                  } else {
                     return !var5 && var21 && !insert(var6, var22.tipItem(var20)) ? null : var6;
                  }
               }
            }
         }
      } else {
         return null;
      }
   }

   // ------------------------------------------------------------------ [bows] arrow-tip refit of one chosen stack

   public static int refitAction(int slot, int code, boolean all) {
      return REFIT_BUTTON + (slot * 16 + code) * 2 + (all ? 1 : 0);
   }

   /** Whether the refit is possible right now (computed on either side from the menu's view of the inventory). */
   public boolean canRefit(int slot, int code, boolean all) {
      return this.refitPlan(slot, code, all) != null;
   }

   /** How many arrows of that stack the refit would change (heads owned + heads makeable, capped by the stack). */
   public int refitCount(int slot, int code, boolean all) {
      List<ItemStack> plan = this.refitPlan(slot, code, all);
      if (plan == null) {
         return 0;
      }
      ItemStack before = this.inventory.items.get(slot);
      ItemStack after = plan.get(slot);
      return ItemStack.isSameItemSameComponents(before, after) ? before.getCount() - after.getCount() : before.getCount();
   }

   /** Heads of {@code tip} the selected refit would first make from materials (0 if it uses heads already owned). */
   public int headsToMake(ArrowTip tip) {
      List<ItemStack> inv = this.copy();
      int have = this.heads(inv, tip);
      List<ItemStack> next = this.craftHead(inv, tip);
      return next == null ? 0 : Math.max(0, this.heads(next, tip) - have);
   }

   /** Index of the recipe that makes {@code tip} heads at this bench, -1 if none. */
   public int headRecipe(ArrowTip tip) {
      Level level = this.inventory.player.level();
      for (int i = 0; i < this.recipes.size(); i++) {
         ItemStack out = ((CraftingRecipe)this.recipes.get(i).value()).getResultItem(level.registryAccess());
         if (out.getItem() instanceof ArrowTipItem t && t.tip == tip) {
            return i;
         }
      }
      return -1;
   }

   /**
    * The inventory after refitting the arrow stack in {@code slot}: fit {@code code}'s head (making missing heads from
    * materials, whole recipe batches, up to 16), or with {@link #REFIT_STOCK} take the fitted heads off and give the
    * shaft its stock head back. Old fitted heads return to the inventory. Null if not possible. Server-authoritative:
    * the click handler recomputes this from the server inventory.
    */
   private List<ItemStack> refitPlan(int slot, int code, boolean all) {
      if (this.kind != WorkshopKind.BOWS || slot < 0 || slot >= Math.min(REFIT_SLOTS, this.inventory.items.size()) || code < 0 || code > REFIT_STOCK) {
         return null;
      }
      List<ItemStack> inv = this.copy();
      ItemStack src = inv.get(slot);
      boolean plain = src.is(Items.ARROW);
      if (src.isEmpty() || !plain && !ArrowTip.arrow(src)) {
         return null;
      }
      boolean legacy = src.is((Item)HuntContent.TRACER_ARROW.get());
      boolean primitive = ArrowTip.primitiveShaft(src);
      ArrowTip current = plain ? null : ArrowTip.of(src);
      boolean fitted = !plain && (legacy || ArrowTip.fitted(src));
      ArrowTip target;
      if (code == REFIT_STOCK) {
         if (!fitted) {
            return null;
         }
         target = primitive ? ArrowTip.FLINT_POINT : ArrowTip.FIXED_BROADHEAD;
      } else {
         if (code >= ArrowTip.values().length) {
            return null;
         }
         target = ArrowTip.byOrdinal(code);
         if (!plain && !legacy && current == target) {
            return null;
         }
      }
      boolean creative = this.inventory.player.hasInfiniteMaterials();
      int count = all ? src.getCount() : 1;
      ItemStack original = src.copy();
      if (code != REFIT_STOCK && !creative) {
         int have = this.heads(inv, target);
         for (int i = 0; i < 16 && have < count; i++) {
            List<ItemStack> next = this.craftHead(inv, target);
            if (next == null) {
               break;
            }
            int h = this.heads(next, target);
            if (h <= have) {
               break;
            }
            inv = next;
            have = h;
         }
         count = Math.min(count, have);
      }
      ItemStack stack = inv.get(slot);
      if (count <= 0 || !ItemStack.isSameItemSameComponents(stack, original)) {
         return null;
      }
      count = Math.min(count, stack.getCount());
      if (code != REFIT_STOCK && !creative) {
         int left = count;
         for (ItemStack h : inv) {
            if (left > 0 && h.getItem() instanceof ArrowTipItem t && t.tip == target) {
               int take = Math.min(left, h.getCount());
               h.shrink(take);
               left -= take;
            }
         }
         if (left > 0) {
            return null;
         }
      }
      ItemStack refitted = ArrowTip.arrowStack(primitive, target, count);
      stack.shrink(count);
      if (stack.isEmpty()) {
         inv.set(slot, refitted); // the new arrows stay where the old ones were
      } else if (!insert(inv, refitted)) {
         return null;
      }
      if (fitted && !creative && !insert(inv, current.tipItem(count))) {
         return null;
      }
      return inv;
   }

   private int source(Collection<ItemStack> var1, ArrowTip var2, boolean var3) {
      return this.source(new ArrayList<>(var1), var2, var3);
   }

   private int source(List<ItemStack> var1, ArrowTip var2, boolean var3) {
      int var4 = -1;

      for (int var5 = 0; var5 < var1.size(); var5++) {
         ItemStack var6 = (ItemStack)var1.get(var5);
         boolean var7 = !var3 && var6.is(Items.ARROW);
         if ((
               var7
                  || ArrowTip.arrow(var6)
                     && ArrowTip.primitiveShaft(var6) == var3
                     && (!var6.is((Item)HuntContent.TRACER_ARROW.get()) || !var3)
                     && (ArrowTip.of(var6) != var2 || var6.is((Item)HuntContent.TRACER_ARROW.get()))
            )
            && (var4 < 0 || var6.getCount() > ((ItemStack)var1.get(var4)).getCount())) {
            var4 = var5;
         }
      }

      return var4;
   }

   private int heads(List<ItemStack> var1, ArrowTip var2) {
      int var3 = 0;

      for (ItemStack var5 : var1) {
         Item var7 = var5.getItem();
         if (var7 instanceof ArrowTipItem) {
            ArrowTipItem var6 = (ArrowTipItem)var7;
            if (var6.tip == var2) {
               var3 += var5.getCount();
            }
         }
      }

      return var3;
   }

   private List<ItemStack> craftHead(List<ItemStack> var1, ArrowTip var2) {
      Level var3 = this.inventory.player.level();

      for (int var4 = 0; var4 < this.recipes.size(); var4++) {
         ItemStack var5 = ((CraftingRecipe)this.recipes.get(var4).value()).getResultItem(var3.registryAccess());
         if (var5.getItem() instanceof ArrowTipItem var6 && var6.tip == var2) {
            return this.plan(var4, var1);
         }
      }

      return null;
   }

   private List<ItemStack> actionPlan(int var1) {
      if (var1 >= this.tipStart) {
         return this.tipPlan(var1 - this.tipStart);
      } else {
         int var2 = this.recipes.size() + 2 + EquipmentCatalog.PARTS.size() * 2;
         if (var1 >= var2) {
            int var4 = var1 - var2;
            return this.kind == WorkshopKind.AMMUNITION && var4 < this.recipes.size() ? this.batchPlan(var4) : null;
         } else if (var1 < this.recipes.size()) {
            return this.plan(var1);
         } else if (var1 < this.recipes.size() + 2) {
            return this.attachments ? this.opticPlan(var1 == this.recipes.size()) : null;
         } else {
            int var3 = var1 - this.recipes.size() - 2;
            return (this.attachments || this.kind == WorkshopKind.FISHING)
                  && var3 >= 0
                  && var3 / 2 < EquipmentCatalog.PARTS.size()
                  && this.kind.accepts((Item)BuiltInRegistries.ITEM.get(FrontierHunts.id(EquipmentCatalog.PARTS.get(var3 / 2))))
               ? AttachmentFitting.plan(this.inventory, EquipmentCatalog.PARTS.get(var3 / 2), var3 % 2 == 0)
               : null;
         }
      }
   }

   public List<RecipeHolder<CraftingRecipe>> recipes() {
      return this.recipes;
   }

   public boolean ready(int var1) {
      return var1 >= 0 && var1 < this.ready.getCount() && this.ready.get(var1) == 1;
   }

   public boolean readyBatch(int var1) {
      return this.kind == WorkshopKind.AMMUNITION && this.ready(this.recipes.size() + 2 + EquipmentCatalog.PARTS.size() * 2 + var1);
   }

   public boolean stillValid(Player var1) {
      return var1.isAlive()
         && (Boolean)this.access
            .evaluate(
               (var2, var3) -> WorkshopKind.of(var2.getBlockState(var3)) == this.kind
                     && var1.distanceToSqr((double)var3.getX() + 0.5, (double)var3.getY() + 0.5, (double)var3.getZ() + 0.5) <= 64.0,
               true
            );
   }

   public ItemStack quickMoveStack(Player var1, int var2) {
      return ItemStack.EMPTY;
   }

   private List<ItemStack> copy() {
      return this.inventory.items.stream().<ItemStack>map(ItemStack::copy).collect(Collectors.toCollection(ArrayList::new));
   }

   public static boolean insert(List<ItemStack> var0, ItemStack var1) {
      ItemStack var2 = var1.copy();

      for (ItemStack var4 : var0) {
         if (!var4.isEmpty() && ItemStack.isSameItemSameComponents(var4, var2)) {
            int var5 = Math.min(var2.getCount(), Math.max(0, var4.getMaxStackSize() - var4.getCount()));
            var4.grow(var5);
            var2.shrink(var5);
            if (var2.isEmpty()) {
               return true;
            }
         }
      }

      for (int var6 = 0; var6 < var0.size(); var6++) {
         if (((ItemStack)var0.get(var6)).isEmpty()) {
            int var7 = Math.min(var2.getCount(), var2.getMaxStackSize());
            var0.set(var6, var2.copyWithCount(var7));
            var2.shrink(var7);
            if (var2.isEmpty()) {
               return true;
            }
         }
      }

      return var2.isEmpty();
   }

   private List<ItemStack> plan(int var1) {
      return this.plan(var1, this.copy());
   }

   private List<ItemStack> batchPlan(int var1) {
      List<ItemStack> var2 = this.copy();

      for (int var3 = 0; var3 < 5; var3++) {
         var2 = this.plan(var1, var2);
         if (var2 == null) {
            return null;
         }
      }

      return var2;
   }

   private List<ItemStack> plan(int var1, List<ItemStack> var2) {
      RecipeHolder<CraftingRecipe> var3 = this.recipes.get(var1);
      RecipeHolder<?> var4 = (RecipeHolder<?>)this.inventory.player.level().getRecipeManager().byKey(var3.id()).orElse(null);
      if (var4 == null || var4.value() != var3.value()) {
         return null;
      }
      // [recipes] Clothing Table recipes: sewing changes the held garment; the others hand out their product (never the list icon)
      ClothingTableRecipe station = var3.value() instanceof ClothingTableRecipe ct ? ct : null;
      if (station != null && this.kind != WorkshopKind.CLOTHING) {
         return null;
      }
      int sewSlot = -1;
      if (station != null && station.sewing()) {
         sewSlot = this.inventory.selected;
         if (sewSlot < 0 || sewSlot >= var2.size() || !ClothingTable.canSew(var2.get(sewSlot), station.sew().bit())) {
            return null;
         }
      }
      ItemStack product = station != null ? station.product() : ((CraftingRecipe)var3.value()).getResultItem(this.inventory.player.level().registryAccess()).copy();
      if (!this.inventory.player.hasInfiniteMaterials()) {
         ArrayList<Ingredient> var5 = ((CraftingRecipe)var3.value())
            .getIngredients()
            .stream()
            .filter(var0 -> !var0.isEmpty())
            .collect(Collectors.toCollection(ArrayList::new));
         if (var5.size() > 9) {
            return null;
         }
         if (sewSlot >= 0) {
            ItemStack target = var2.get(sewSlot);
            if (var5.stream().anyMatch(i -> i.test(target))) {
               return null; // a sewing recipe never eats the garment it sews into
            }
         }
         IdentityHashMap<Ingredient, Integer> var6 = new IdentityHashMap<>();

         for (Ingredient var8 : var5) {
            var6.put(var8, (int)this.inventory.items.stream().filter(var8::test).count());
         }

         var5.sort(Comparator.comparingInt(var6::get));
         var2 = consume(var5, 0, var2);
         if (var2 == null) {
            return null;
         }
      }
      if (sewSlot >= 0) {
         ItemStack target = var2.get(sewSlot);
         if (!ClothingTable.canSew(target, station.sew().bit())) {
            return null;
         }
         var2.set(sewSlot, ClothingTable.sewn(target, station.sew().bit()));
         return var2;
      }
      return product.isEmpty() || insert(var2, product) ? var2 : null;
   }

   private static List<ItemStack> consume(List<Ingredient> var0, int var1, List<ItemStack> var2) {
      if (var1 == var0.size()) {
         return var2;
      } else {
         HashSet<Item> var3 = new HashSet<>();

         for (int var4 = 0; var4 < var2.size(); var4++) {
            ItemStack var5 = (ItemStack)var2.get(var4);
            if (!var5.isEmpty() && ((Ingredient)var0.get(var1)).test(var5) && var3.add(var5.getItem())) {
               int var6 = var4;

               for (int var7 = var4 + 1; var7 < var2.size(); var7++) {
                  if (ItemStack.isSameItemSameComponents((ItemStack)var2.get(var7), var5)
                     && ((ItemStack)var2.get(var7)).getCount() > ((ItemStack)var2.get(var6)).getCount()) {
                     var6 = var7;
                  }
               }

               ArrayList<ItemStack> var11 = var2.stream().map(ItemStack::copy).collect(Collectors.toCollection(ArrayList::new));
               ItemStack var8 = ((ItemStack)var11.get(var6)).copyWithCount(1);
               ((ItemStack)var11.get(var6)).shrink(1);
               List<ItemStack> var9 = consume(var0, var1 + 1, var11);
               if (var9 != null) {
                  ItemStack var10 = var8.getCraftingRemainingItem();
                  if (var10.isEmpty() || insert(var9, var10)) {
                     return var9;
                  }
               }
            }
         }

         return null;
      }
   }

   private List<ItemStack> opticPlan(boolean var1) {
      List<ItemStack> var2 = this.copy();
      ItemStack var3 = var2.stream()
         .filter(var1x -> var1x.getItem() instanceof RifleItem && OpticUpgrade.fitted(var1x) != var1)
         .findFirst()
         .orElse(ItemStack.EMPTY);
      if (!var3.isEmpty() && RifleState.read(var3).action() == 0) {
         if (var1) {
            ItemStack var4 = var2.stream().filter(var0 -> var0.is((Item)WorkshopContent.OPTIC.get())).findFirst().orElse(ItemStack.EMPTY);
            if (var4.isEmpty()) {
               return null;
            }

            var4.shrink(1);
         } else if (!insert(var2, new ItemStack((ItemLike)WorkshopContent.OPTIC.get()))) {
            return null;
         }

         OpticUpgrade.set(var3, var1);
         return var2;
      } else {
         return null;
      }
   }

   public boolean clickMenuButton(Player var1, int var2) {
      if (var2 == 9000 && this.attachments && var1 instanceof ServerPlayer var3 && var3.containerMenu == this && this.stillValid(var3) && !var3.isSpectator()) {
         this.access
            .execute(
               (var1x, var2x) -> var3.openMenu(
                     new SimpleMenuProvider((var2xx, var3x, var4x) -> new AttachmentMenu(var2xx, var3x, var1x, var2x), Component.literal("Attachment fitting"))
                  )
            );
         return true;
      }

      if (var2 >= REFIT_BUTTON && var2 < REFIT_BUTTON + REFIT_ACTIONS) { // [bows] refit the chosen arrow stack
         int code = (var2 - REFIT_BUTTON) / 2;
         if (var1 instanceof ServerPlayer sp && sp.containerMenu == this && this.stillValid(sp) && !sp.isSpectator() && this.kind == WorkshopKind.BOWS) {
            long now = sp.level().getGameTime();
            if (this.traded != Long.MIN_VALUE && now >= this.traded && now - this.traded < 4L) {
               return false;
            }
            List<ItemStack> plan = this.refitPlan(code / 16, code % 16, ((var2 - REFIT_BUTTON) & 1) != 0);
            if (plan == null) {
               return false;
            }
            this.traded = now;
            for (int i = 0; i < plan.size(); i++) {
               this.inventory.setItem(i, plan.get(i));
            }
            this.inventory.setChanged();
            this.checkedTick = Long.MIN_VALUE;
            sp.inventoryMenu.broadcastFullState();
            this.broadcastChanges();
            this.access.execute((lvl, pos) -> lvl.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.5F, 1.4F));
            return true;
         }
         return false;
      }

      if (var2 >= 30000 && var2 < 30000 + TIP_ACTIONS) {
         if (var1 instanceof ServerPlayer var11
            && var11.containerMenu == this
            && this.stillValid(var11)
            && !var11.isSpectator()
            && this.kind == WorkshopKind.BOWS) {
            long var12 = var11.level().getGameTime();
            if (this.traded != Long.MIN_VALUE && var12 >= this.traded && var12 - this.traded < 4L) {
               return false;
            }

            List<ItemStack> var13 = this.tipPlan(var2 - 30000);
            if (var13 == null) {
               return false;
            }

            this.traded = var12;

            for (int var7 = 0; var7 < var13.size(); var7++) {
               this.inventory.setItem(var7, (ItemStack)var13.get(var7));
            }

            this.inventory.setChanged();
            this.checkedTick = Long.MIN_VALUE;
            var11.inventoryMenu.broadcastFullState();
            this.broadcastChanges();
            this.access.execute((var0, var1x) -> var0.playSound(null, var1x, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.5F, 1.4F));
            return true;
         }

         return false;
      } else {
         boolean var10 = var2 >= 10000;
         int var4 = var10 ? var2 - 10000 : var2;
         if (var1 instanceof ServerPlayer var5
            && var5.containerMenu == this
            && this.stillValid(var5)
            && !var5.isSpectator()
            && var4 >= 0
            && var4 < (var10 ? this.recipes.size() : this.recipes.size() + 2 + EquipmentCatalog.PARTS.size() * 2)
            && (!var10 || this.kind == WorkshopKind.AMMUNITION)) {
            long var6 = var5.level().getGameTime();
            if (this.traded != Long.MIN_VALUE && var6 >= this.traded && var6 - this.traded < 8L) {
               return false;
            }

            List<ItemStack> var8 = var10 ? this.batchPlan(var4) : this.actionPlan(var4);
            if (var8 == null) {
               return false;
            }

            this.traded = var6;

            for (int var9 = 0; var9 < var8.size(); var9++) {
               this.inventory.setItem(var9, (ItemStack)var8.get(var9));
            }

            this.inventory.setChanged();
            this.checkedTick = Long.MIN_VALUE;
            var5.inventoryMenu.broadcastFullState();
            this.broadcastChanges();
            if (var4 < this.recipes.size()) {
               ExpeditionService.record(var5, "craft", "*", var10 ? 5 : 1, 0.0);
            }

            this.access.execute((var0, var1x) -> var0.playSound(null, var1x, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.55F, 1.1F));
            return true;
         }

         return false;
      }
   }
}
