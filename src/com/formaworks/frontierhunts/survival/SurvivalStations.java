package com.formaworks.frontierhunts.survival;

import com.formaworks.frontierhunts.HuntContent;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * [survival] What the existing camp stations do with Frontier Survival: the smokehouse smokes any raw meat or fish into
 * its cooked form, marked smoked (keeps four times longer); the tanning rack tans elk/moose/bison hides, fur pelts and bear
 * pelts. Called from the hook lines in {@code camp/CampStation} (marked [survival]).
 */
@EventBusSubscriber(modid = "frontierhunts")
public final class SurvivalStations {
   private static final Map<Item, Optional<Item>> COOKED = new ConcurrentHashMap<>();

   private SurvivalStations() {
   }

   public static boolean smokable(ItemStack s) {
      if (s.isEmpty() || s.is(HuntContent.VENISON.get())) {
         return !s.isEmpty();
      }
      FoodValues v = NutritionTable.any(s.getItem());
      return v != null && v.raw() && !v.spoiled();
   }

   public static boolean tannable(ItemStack s) {
      return s.is(SurvivalContent.HEAVY_HIDE.get()) || s.is(SurvivalContent.FUR_PELT.get()) || s.is(SurvivalContent.BEAR_PELT.get());
   }

   /** Four smoked pieces of whatever the input cooks into, or null when it does not cook. */
   public static ItemStack smoked(Level level, ItemStack in) {
      if (level == null || in.isEmpty()) {
         return null;
      }
      Item cooked = COOKED.computeIfAbsent(in.getItem(), i -> cookedOf(level, i)).orElse(null);
      if (cooked == null) {
         return null;
      }
      ItemStack r = new ItemStack(cooked, 4);
      if (!level.isClientSide && SurvivalConfig.spoilage()) {
         Perishable.cure(r, level.getGameTime(), SurvivalMath.AMBIENT, SurvivalMath.SMOKED);
      }
      return r;
   }

   private static Optional<Item> cookedOf(Level level, Item raw) {
      SingleRecipeInput input = new SingleRecipeInput(new ItemStack(raw));
      for (RecipeType<?> t : new RecipeType<?>[]{RecipeType.SMOKING, RecipeType.SMELTING, RecipeType.CAMPFIRE_COOKING}) {
         @SuppressWarnings("unchecked")
         Optional<? extends RecipeHolder<?>> h = level.getRecipeManager().getRecipeFor(
            (RecipeType<net.minecraft.world.item.crafting.AbstractCookingRecipe>) t, input, level
         );
         if (h.isPresent()) {
            ItemStack out = h.get().value().getResultItem(level.registryAccess());
            if (!out.isEmpty() && out.getItem() != raw) {
               return Optional.of(out.getItem());
            }
         }
      }
      return Optional.empty();
   }

   /** Tanning result for the new hides; null for the deer hide (the station's own rules). */
   public static ItemStack tanned(ItemStack in, boolean bone) {
      if (in.is(SurvivalContent.HEAVY_HIDE.get())) {
         return bone ? new ItemStack(SurvivalContent.TANNED_HEAVY_HIDE.get()) : new ItemStack(Items.LEATHER, 3);
      }
      if (in.is(SurvivalContent.FUR_PELT.get())) {
         return new ItemStack(SurvivalContent.TANNED_FUR.get());
      }
      if (in.is(SurvivalContent.BEAR_PELT.get())) {
         return new ItemStack(SurvivalContent.BEAR_FUR.get(), 2);
      }
      return null;
   }

   /** The output slot takes {@code add}: same item and data, or the same food differing only in its freshness stamp. */
   public static boolean fits(ItemStack out, ItemStack add) {
      if (out.getCount() > out.getMaxStackSize() - add.getCount() || out.getItem() != add.getItem()) {
         return false;
      }
      if (ItemStack.isSameItemSameComponents(out, add)) {
         return true;
      }
      Freshness a = Perishable.get(out), b = Perishable.get(add);
      if (a == null || b == null || a.cure() != b.cure() || a.store() != b.store()) {
         return false;
      }
      ItemStack c = add.copy();
      c.set(SurvivalContent.FRESHNESS.get(), a);
      return ItemStack.isSameItemSameComponents(out, c);
   }

   public static void merge(ItemStack out, ItemStack add) {
      Freshness a = Perishable.get(out), b = Perishable.get(add);
      if (a != null && b != null && a.made() != b.made()) {
         out.set(SurvivalContent.FRESHNESS.get(), a.withMade(SurvivalMath.merge(a.made(), out.getCount(), b.made(), add.getCount())));
      }
      out.grow(add.getCount());
   }

   @SubscribeEvent
   public static void reload(AddReloadListenerEvent e) {
      COOKED.clear();
   }
}
