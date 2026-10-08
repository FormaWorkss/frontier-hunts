package com.formaworks.frontierhunts.benches;

import com.formaworks.frontierhunts.HuntContent;
import com.formaworks.frontierhunts.expedition.AttachmentSpec;
import com.formaworks.frontierhunts.hunting.ArrowTip;
import com.formaworks.frontierhunts.hunting.ArrowTipItem;
import com.formaworks.frontierhunts.recipes.ClothingTable;
import com.formaworks.frontierhunts.workshop.AttachmentFitting;
import com.formaworks.frontierhunts.workshop.EquipmentCatalog;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * [benches] Inventory plans of the benches, pure functions over a copy of the player's main inventory (36 slots): what
 * the inventory looks like after a craft, a part fitted to the held weapon or an arrow stack refitted, or null when it
 * can't be done. The client uses them to show what is possible (computed only when the inventory changes); the server
 * recomputes them from its own inventory on every click and applies the result.
 */
public final class BenchCrafting {
   /** Max refit / tip actions per arrow slot (codes 0..15: every head, 15 = the stock head back). */
   public static final int REFIT_STOCK = 15;
   public static final int REFIT_SLOTS = 36;

   private BenchCrafting() {
   }

   public static List<ItemStack> copy(Inventory inv) {
      List<ItemStack> out = new ArrayList<>(inv.items.size());
      for (ItemStack s : inv.items) {
         out.add(s.copy());
      }
      return out;
   }

   private static List<ItemStack> copy(List<ItemStack> l) {
      List<ItemStack> out = new ArrayList<>(l.size());
      for (ItemStack s : l) {
         out.add(s.copy());
      }
      return out;
   }

   // ============================================================================================ crafting

   /**
    * The inventory after taking one craft's ingredients (and putting back their remainders, e.g. the bucket of a lava
    * bucket) and, for a sewing recipe, sewing into the selected slot. The product is NOT added: see {@link #craft}.
    * Null when the materials are missing. {@code consumed} (optional) receives one stack per item taken.
    */
   public static List<ItemStack> consume(Player p, BenchCatalog.Entry e, List<ItemStack> inv, List<ItemStack> consumed) {
      int sewSlot = -1;
      if (e.sewing()) {
         sewSlot = p.getInventory().selected;
         if (sewSlot < 0 || sewSlot >= inv.size() || !ClothingTable.canSew(inv.get(sewSlot), e.sewBit)) {
            return null;
         }
      }
      List<ItemStack> after = inv;
      if (!p.hasInfiniteMaterials()) {
         if (e.ingredients.size() > 9) {
            return null;
         }
         if (sewSlot >= 0) {
            ItemStack target = inv.get(sewSlot);
            for (Ingredient i : e.ingredients) {
               if (i.test(target)) {
                  return null; // a sewing recipe never eats the garment it sews into
               }
            }
         }
         // scarce ingredients first: fewer branches to try
         List<Ingredient> ings = new ArrayList<>(e.ingredients);
         IdentityHashMap<Ingredient, Integer> avail = new IdentityHashMap<>();
         for (Ingredient i : ings) {
            int n = 0;
            for (ItemStack s : inv) {
               if (!s.isEmpty() && i.test(s)) {
                  n += s.getCount();
               }
            }
            avail.put(i, n);
         }
         ings.sort(Comparator.comparingInt(avail::get));
         after = take(ings, 0, copy(inv));
         if (after == null && e.fitTip != null) {
            // [smalls] a fitted arrow without enough heads: make them first from materials with the bench's head recipe
            List<ItemStack> made = withHeads(p, e, inv);
            after = made == null ? null : take(ings, 0, made);
         }
         if (after == null) {
            return null;
         }
         if (consumed != null) {
            diff(inv, after, consumed);
         }
      } else {
         after = copy(inv);
      }
      if (sewSlot >= 0) {
         ItemStack target = after.get(sewSlot);
         if (!ClothingTable.canSew(target, e.sewBit)) {
            return null;
         }
         after.set(sewSlot, ClothingTable.sewn(target, e.sewBit));
      }
      return after;
   }

   /**
    * [smalls] The inventory after making enough heads for one craft of a fitted-arrow recipe (whole batches of the head
    * recipe, at most 8), or null when the materials don't stretch that far.
    */
   static List<ItemStack> withHeads(Player p, BenchCatalog.Entry e, List<ItemStack> inv) {
      BenchCatalog.Entry head = e.headEntry;
      int need = e.headsPerCraft();
      if (head == null || need <= 0 || head.fitTip != null) {
         return null;
      }
      List<ItemStack> cur = inv;
      int have = heads(cur, e.fitTip);
      for (int i = 0; i < 8 && have < need; i++) {
         List<ItemStack> next = craft(p, head, cur);
         if (next == null) {
            return null;
         }
         int h = heads(next, e.fitTip);
         if (h <= have) {
            return null;
         }
         cur = next;
         have = h;
      }
      return have >= need ? cur : null;
   }

   /** [smalls] Heads of a fitted-arrow recipe one craft would make from materials first (0 when you carry enough). */
   public static int headsToMake(List<ItemStack> inv, BenchCatalog.Entry e) {
      return e.fitTip == null ? 0 : Math.max(0, e.headsPerCraft() - heads(inv, e.fitTip));
   }

   /** The inventory after one whole craft (product inserted), or null. */
   public static List<ItemStack> craft(Player p, BenchCatalog.Entry e, List<ItemStack> inv) {
      List<ItemStack> after = consume(p, e, inv, null);
      if (after == null) {
         return null;
      }
      if (e.sewing() || e.product.isEmpty()) {
         return after;
      }
      return insert(after, e.product.copy()) ? after : null;
   }

   /** The inventory after {@code times} crafts in a row, or null if any of them can't be done. */
   public static List<ItemStack> craft(Player p, BenchCatalog.Entry e, List<ItemStack> inv, int times) {
      List<ItemStack> cur = inv;
      for (int i = 0; i < times && cur != null; i++) {
         cur = craft(p, e, cur);
      }
      return cur;
   }

   /** How many of each material line the inventory holds (any matching item counts). */
   public static int have(List<ItemStack> inv, Ingredient ing) {
      int n = 0;
      for (ItemStack s : inv) {
         if (!s.isEmpty() && ing.test(s)) {
            n += s.getCount();
         }
      }
      return n;
   }

   private static List<ItemStack> take(List<Ingredient> ings, int at, List<ItemStack> inv) {
      if (at == ings.size()) {
         return inv;
      }
      Ingredient ing = ings.get(at);
      HashSet<Item> tried = new HashSet<>();
      for (int i = 0; i < inv.size(); i++) {
         ItemStack s = inv.get(i);
         if (s.isEmpty() || !ing.test(s) || !tried.add(s.getItem())) {
            continue;
         }
         // take from the biggest stack of that item (keeps small stacks, e.g. a named one, intact)
         int best = i;
         for (int j = i + 1; j < inv.size(); j++) {
            if (ItemStack.isSameItemSameComponents(inv.get(j), s) && inv.get(j).getCount() > inv.get(best).getCount()) {
               best = j;
            }
         }
         List<ItemStack> next = copy(inv);
         ItemStack one = next.get(best).copyWithCount(1);
         next.get(best).shrink(1);
         List<ItemStack> done = take(ings, at + 1, next);
         if (done != null) {
            ItemStack rem = one.getCraftingRemainingItem();
            if (rem.isEmpty() || insert(done, rem)) {
               return done;
            }
         }
      }
      return null;
   }

   /** What went missing between two inventories (one stack per item kind and slot). */
   private static void diff(List<ItemStack> before, List<ItemStack> after, List<ItemStack> out) {
      for (int i = 0; i < before.size(); i++) {
         ItemStack b = before.get(i), a = after.get(i);
         if (b.isEmpty()) {
            continue;
         }
         int lost = ItemStack.isSameItemSameComponents(a, b) ? b.getCount() - a.getCount() : b.getCount();
         if (lost > 0) {
            out.add(b.copyWithCount(lost));
         }
      }
   }

   /** Puts a stack into the inventory list (merging first, then empty slots); false if it does not all fit. */
   public static boolean insert(List<ItemStack> inv, ItemStack stack) {
      ItemStack left = stack.copy();
      for (ItemStack s : inv) {
         if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, left)) {
            int n = Math.min(left.getCount(), Math.max(0, s.getMaxStackSize() - s.getCount()));
            s.grow(n);
            left.shrink(n);
            if (left.isEmpty()) {
               return true;
            }
         }
      }
      for (int i = 0; i < inv.size(); i++) {
         if (inv.get(i).isEmpty()) {
            int n = Math.min(left.getCount(), left.getMaxStackSize());
            inv.set(i, left.copyWithCount(n));
            left.shrink(n);
            if (left.isEmpty()) {
               return true;
            }
         }
      }
      return left.isEmpty();
   }

   // ============================================================================================ fitting

   /**
    * Fit ({@code on}) or remove one part on the weapon in the selected hotbar slot. Fitting a part that conflicts with
    * a fitted one (two sights, two magazines, suppressor and brake) takes the old one off first, back into the
    * inventory, like the old fitting tray did.
    */
   public static List<ItemStack> fit(Inventory inv, String part, boolean on) {
      List<ItemStack> items = copy(inv);
      int slot = inv.selected;
      if (slot < 0 || slot >= items.size() || !EquipmentCatalog.PARTS.contains(part)) {
         return null;
      }
      ItemStack held = items.get(slot);
      if (held.isEmpty() || !AttachmentFitting.supported(held, part)) {
         return null;
      }
      if (on && !(held.getItem() instanceof com.formaworks.frontierhunts.rifle.RifleItem)) {
         for (String other : EquipmentCatalog.PARTS) {
            if (!other.equals(part) && AttachmentSpec.exclusive(part, other) && AttachmentFitting.fitted(items.get(slot), other)) {
               items = AttachmentFitting.plan(items, slot, other, false);
               if (items == null) {
                  return null;
               }
            }
         }
      }
      return AttachmentFitting.plan(items, slot, part, on);
   }

   // ============================================================================================ arrow refit

   /** Heads of {@code tip} in the inventory. */
   public static int heads(List<ItemStack> inv, ArrowTip tip) {
      int n = 0;
      for (ItemStack s : inv) {
         if (s.getItem() instanceof ArrowTipItem t && t.tip == tip) {
            n += s.getCount();
         }
      }
      return n;
   }

   /** Is this stack an arrow the Reloading Bench can refit (hunting, primitive, tracer or a plain vanilla arrow)? */
   public static boolean refittable(ItemStack s) {
      return !s.isEmpty() && (s.is(Items.ARROW) || ArrowTip.arrow(s));
   }

   /**
    * The inventory after refitting the arrow stack in {@code slot}: fit {@code code}'s head (making missing heads from
    * materials with the bench's head recipe, whole batches, up to 16), or with {@link #REFIT_STOCK} take the fitted
    * heads off and give the shafts their stock head back. Old fitted heads return to the inventory. Null if not possible.
    * {@code headRecipe} is the Reloading Bench entry that makes the target head (null: only heads already owned).
    */
   public static List<ItemStack> refit(Player p, List<ItemStack> start, int slot, int code, boolean all, BenchCatalog.Entry headRecipe) {
      if (slot < 0 || slot >= Math.min(REFIT_SLOTS, start.size()) || code < 0 || code > REFIT_STOCK) {
         return null;
      }
      List<ItemStack> inv = copy(start);
      ItemStack src = inv.get(slot);
      boolean plain = src.is(Items.ARROW);
      if (!refittable(src)) {
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
      boolean creative = p.hasInfiniteMaterials();
      int count = all ? src.getCount() : 1;
      ItemStack original = src.copy();
      if (code != REFIT_STOCK && !creative) {
         int have = heads(inv, target);
         for (int i = 0; i < 16 && have < count && headRecipe != null; i++) {
            List<ItemStack> next = craft(p, headRecipe, inv);
            if (next == null) {
               break;
            }
            int h = heads(next, target);
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
               int n = Math.min(left, h.getCount());
               h.shrink(n);
               left -= n;
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

   /** How many arrows of that stack a refit changes (0 if none). */
   public static int refitCount(Player p, List<ItemStack> inv, int slot, int code, boolean all, BenchCatalog.Entry headRecipe) {
      List<ItemStack> plan = refit(p, inv, slot, code, all, headRecipe);
      if (plan == null) {
         return 0;
      }
      ItemStack before = inv.get(slot);
      ItemStack after = plan.get(slot);
      return ItemStack.isSameItemSameComponents(before, after) ? before.getCount() - after.getCount() : before.getCount();
   }
}
