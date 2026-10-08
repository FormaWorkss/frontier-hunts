package com.formaworks.frontierhunts.hunting;

import com.formaworks.frontierhunts.HuntContent;
import net.minecraft.core.NonNullList;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;

public final class ButcherRecipe extends CustomRecipe {
   public ButcherRecipe(CraftingBookCategory var1) {
      super(var1);
   }

   private static boolean quarter(ItemStack var0) {
      return var0.is((Item)HuntContent.VENISON_QUARTER.get()) || var0.is((Item)HuntContent.AGED_VENISON.get());
   }

   private static boolean knife(ItemStack var0) {
      return var0.is((Item)HuntContent.SKINNING_TOOL.get()) || var0.is((Item)HuntContent.FIELD_KNIFE.get());
   }

   public boolean matches(CraftingInput var1, Level var2) {
      int var3 = 0;
      int var4 = 0;

      for (int var5 = 0; var5 < var1.size(); var5++) {
         ItemStack var6 = var1.getItem(var5);
         if (!var6.isEmpty()) {
            if (quarter(var6) && var6.getCount() == 1) {
               var3++;
            } else {
               if (!knife(var6)) {
                  return false;
               }

               var4++;
            }
         }
      }

      return var3 == 1 && var4 == 1;
   }

   public ItemStack assemble(CraftingInput var1, Provider var2) {
      boolean var3 = false;

      for (int var4 = 0; var4 < var1.size(); var4++) {
         if (var1.getItem(var4).is((Item)HuntContent.AGED_VENISON.get())) {
            var3 = true;
         }
      }

      return new ItemStack((ItemLike)HuntContent.VENISON.get(), var3 ? 6 : 4);
   }

   public NonNullList<ItemStack> getRemainingItems(CraftingInput var1) {
      NonNullList var2 = NonNullList.withSize(var1.size(), ItemStack.EMPTY);

      for (int var3 = 0; var3 < var1.size(); var3++) {
         ItemStack var4 = var1.getItem(var3);
         if (knife(var4)) {
            if (!var4.isDamageableItem()) {
               var2.set(var3, var4.copy());
            } else {
               ItemStack var5 = var4.copy();
               if (var5.getDamageValue() + 1 < var5.getMaxDamage()) {
                  var5.setDamageValue(var5.getDamageValue() + 1);
                  var2.set(var3, var5);
               }
            }
         }
      }

      return var2;
   }

   public boolean canCraftInDimensions(int var1, int var2) {
      return var1 * var2 >= 2;
   }

   public RecipeSerializer<?> getSerializer() {
      return (RecipeSerializer<?>)HuntContent.BUTCHER.get();
   }
}
