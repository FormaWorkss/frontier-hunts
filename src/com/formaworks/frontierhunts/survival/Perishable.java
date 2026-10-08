package com.formaworks.frontierhunts.survival;

import java.util.List;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * [survival] Spoilage of perishable food stacks (raw/cooked meat and fish by default; anything with shelf_days in the
 * food table).
 *
 * A stack carries {@link Freshness}: its stamp (bucketed so meat from one hunt stacks), the store class it sits in and its
 * cure. Spoil fraction = age x store rate / (shelf x cure). Moving between stores re-bases the stamp so the fraction is
 * kept (only when the class changes, so stacks are not rewritten all the time). Merging stacks averages the stamps by
 * count (never better than the average), so splitting and re-merging gains nothing.
 */
public final class Perishable {
   private Perishable() {
   }

   public static Freshness get(ItemStack s) {
      return s.get(SurvivalContent.FRESHNESS.get());
   }

   /** Food values when the stack can spoil (and spoilage is on), else null. */
   public static FoodValues values(ItemStack s, boolean client) {
      if (s.isEmpty() || !SurvivalSync.spoilage(client)) {
         return null;
      }
      FoodValues v = NutritionTable.get(s, client);
      return v != null && v.perishable() && !v.spoiled() ? v : null;
   }

   public static float shelfTicks(FoodValues v, Freshness f) {
      return v.shelfDays() * SurvivalMath.DAY * SurvivalMath.CURE_SHELF[f == null ? 0 : f.cure()];
   }

   /** 0..1, 0 when unstamped (it will be stamped fresh the first time it is seen). */
   public static float spoil(ItemStack s, long now, boolean client) {
      FoodValues v = values(s, client);
      Freshness f = v == null ? null : get(s);
      return f == null ? 0F : SurvivalMath.spoil(f.made(), now, f.store(), shelfTicks(v, f));
   }

   /** Stamps an unstamped perishable stack; returns true when it changed. */
   public static boolean stamp(ItemStack s, long now, int store) {
      if (values(s, false) == null || get(s) != null) {
         return false;
      }
      s.set(SurvivalContent.FRESHNESS.get(), new Freshness(SurvivalMath.stamp(now), store, 0));
      return true;
   }

   /** Stamps with a given cure (smokehouse, salt) keeping nothing of the old age. */
   public static void cure(ItemStack s, long now, int store, int cure) {
      if (values(s, false) != null) {
         s.set(SurvivalContent.FRESHNESS.get(), new Freshness(SurvivalMath.stamp(now), store, cure));
      }
   }

   /** Gives {@code s} at least the spoil fraction {@code frac} (crafted from older ingredients). */
   public static void inherit(ItemStack s, long now, float frac) {
      FoodValues v = values(s, false);
      if (v == null || frac <= 0F) {
         return;
      }
      Freshness f = get(s);
      if (f == null) {
         f = new Freshness(SurvivalMath.stamp(now), SurvivalMath.AMBIENT, 0);
      }
      float shelf = shelfTicks(v, f);
      long made = now - (long) Math.ceil(frac * shelf / SurvivalMath.STORE_RATE[f.store()]);
      if (made < f.made()) {
         s.set(SurvivalContent.FRESHNESS.get(), f.withMade(Math.floorDiv(made, SurvivalMath.SNAP) * SurvivalMath.SNAP));
      }
   }

   /** Moves a stack into a store class (stamping it if needed). Returns true when the component changed. */
   public static boolean store(ItemStack s, long now, int store) {
      if (values(s, false) == null) {
         return false;
      }
      Freshness f = get(s);
      if (f == null) {
         return stamp(s, now, store);
      }
      if (f.store() == store) {
         return false;
      }
      s.set(SurvivalContent.FRESHNESS.get(), new Freshness(SurvivalMath.rebase(f.made(), now, f.store(), store), store, f.cure()));
      return true;
   }

   /** The spoiled replacement if this stack has gone off, else null. */
   public static ItemStack spoiledReplacement(ItemStack s, long now) {
      FoodValues v = values(s, false);
      Freshness f = v == null ? null : get(s);
      if (f == null || SurvivalMath.spoil(f.made(), now, f.store(), shelfTicks(v, f)) < 1F) {
         return null;
      }
      return new ItemStack(SurvivalContent.SPOILED_MEAT.get(), s.getCount());
   }

   /**
    * Walks a container: stamps, re-stores, spoils. Returns the number of stacks that spoiled (for stats and the note).
    */
   public static int sweep(Container c, long now, int store, int from, int to) {
      int spoiled = 0;
      int end = Math.min(to, c.getContainerSize());
      for (int i = Math.max(0, from); i < end; i++) {
         ItemStack s = c.getItem(i);
         if (s.isEmpty() || values(s, false) == null) {
            continue;
         }
         ItemStack rotten = spoiledReplacement(s, now);
         if (rotten != null) {
            c.setItem(i, rotten);
            spoiled++;
            continue;
         }
         if (store(s, now, store)) {
            c.setChanged();
         }
      }
      return spoiled;
   }

   /**
    * Folds stacks of the same perishable item (same everything but their stamp) whose ages are within a quarter of the
    * shelf life into one, averaging the stamp by count. Keeps inventories tidy without letting old meat ride on fresh.
    */
   public static void consolidate(Inventory inv, long now) {
      List<ItemStack> items = inv.items;
      for (int i = 0; i < items.size(); i++) {
         ItemStack a = items.get(i);
         FoodValues v = a.isEmpty() ? null : values(a, false);
         Freshness fa = v == null ? null : get(a);
         if (fa == null || a.getCount() >= a.getMaxStackSize()) {
            continue;
         }
         float quarter = shelfTicks(v, fa) * 0.25F;
         for (int j = i + 1; j < items.size() && a.getCount() < a.getMaxStackSize(); j++) {
            ItemStack b = items.get(j);
            if (b.isEmpty() || b.getItem() != a.getItem()) {
               continue;
            }
            Freshness fb = get(b);
            if (fb == null || fb.store() != fa.store() || fb.cure() != fa.cure() || fb.made() == fa.made()) {
               continue;
            }
            if (Math.abs(fb.made() - fa.made()) * SurvivalMath.STORE_RATE[fa.store()] > quarter) {
               continue;
            }
            ItemStack bc = b.copy();
            bc.set(SurvivalContent.FRESHNESS.get(), fa);
            if (!ItemStack.isSameItemSameComponents(a, bc)) {
               continue;
            }
            int move = Math.min(b.getCount(), a.getMaxStackSize() - a.getCount());
            long merged = SurvivalMath.merge(fa.made(), a.getCount(), fb.made(), move);
            fa = fa.withMade(merged);
            a.set(SurvivalContent.FRESHNESS.get(), fa);
            a.grow(move);
            b.shrink(move);
            if (move > 0 && !b.isEmpty()) {
               // the rest of b keeps its own stamp
               b.set(SurvivalContent.FRESHNESS.get(), fb);
            }
         }
      }
   }
}
