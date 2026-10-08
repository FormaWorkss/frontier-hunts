package com.formaworks.frontierhunts.hunting;

import com.formaworks.frontierhunts.HuntContent;
import net.minecraft.core.NonNullList;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public final class ArrowRefitRecipe extends CustomRecipe {
   public ArrowRefitRecipe(CraftingBookCategory var1) {
      super(var1);
   }

   private static ItemStack arrow(CraftingInput var0) {
      ItemStack var1 = ItemStack.EMPTY;

      for (int var2 = 0; var2 < var0.size(); var2++) {
         ItemStack var3 = var0.getItem(var2);
         if (!var3.isEmpty() && (ArrowTip.arrow(var3) || var3.is(Items.ARROW))) {
            if (!var1.isEmpty()) {
               return ItemStack.EMPTY;
            }

            var1 = var3;
         }
      }

      return var1;
   }

   private static ItemStack head(CraftingInput var0) {
      ItemStack var1 = ItemStack.EMPTY;

      for (int var2 = 0; var2 < var0.size(); var2++) {
         ItemStack var3 = var0.getItem(var2);
         if (!var3.isEmpty() && var3.getItem() instanceof ArrowTipItem) {
            if (!var1.isEmpty()) {
               return ItemStack.EMPTY;
            }

            var1 = var3;
         }
      }

      return var1;
   }

   private static int extras(CraftingInput var0) {
      int var1 = 0;

      for (int var2 = 0; var2 < var0.size(); var2++) {
         ItemStack var3 = var0.getItem(var2);
         if (!var3.isEmpty() && !ArrowTip.arrow(var3) && !var3.is(Items.ARROW) && !(var3.getItem() instanceof ArrowTipItem)) {
            var1++;
         }
      }

      return var1;
   }

   public boolean matches(CraftingInput var1, Level var2) {
      ItemStack var3 = arrow(var1);
      ItemStack var4 = head(var1);
      if (!var3.isEmpty() && !var4.isEmpty() && extras(var1) <= 0) {
         ArrowTip var5 = ArrowTip.ofTipItem(var4);
         if (var5 == null) {
            return false;
         } else {
            return var3.is(Items.ARROW) ? true : ArrowTip.of(var3) != var5 || var3.is((Item)HuntContent.TRACER_ARROW.get());
         }
      } else {
         return false;
      }
   }

   public ItemStack assemble(CraftingInput var1, Provider var2) {
      ItemStack var3 = arrow(var1);
      ItemStack var4 = head(var1);
      ArrowTip var5 = ArrowTip.ofTipItem(var4);
      if (!var3.isEmpty() && var5 != null) {
         boolean var6 = ArrowTip.primitiveShaft(var3);
         return ArrowTip.arrowStack(var6, var5, 1);
      } else {
         return ItemStack.EMPTY;
      }
   }

   public NonNullList<ItemStack> getRemainingItems(CraftingInput var1) {
      NonNullList var2 = NonNullList.withSize(var1.size(), ItemStack.EMPTY);

      for (int var3 = 0; var3 < var1.size(); var3++) {
         ItemStack var4 = var1.getItem(var3);
         if (!var4.isEmpty() && ArrowTip.arrow(var4) && ArrowTip.fitted(var4)) {
            var2.set(var3, ArrowTip.of(var4).tipItem(1));
         }
      }

      return var2;
   }

   public boolean canCraftInDimensions(int var1, int var2) {
      return var1 * var2 >= 2;
   }

   public RecipeSerializer<?> getSerializer() {
      return (RecipeSerializer<?>)HuntContent.ARROW_REFIT.get();
   }

   public boolean matches(CraftingContainer var1, Level var2) {
      return this.matches(var1.asCraftInput(), var2);
   }
}
