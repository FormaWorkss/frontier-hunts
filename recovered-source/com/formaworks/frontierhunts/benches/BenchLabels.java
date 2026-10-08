package com.formaworks.frontierhunts.benches;

import com.formaworks.frontierhunts.recipes.ClothingTableRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

/** [benches] Lang keys naming where a recipe is made ("Gunsmith's Bench · Bows"), for the Handbook's recipe cards. */
public final class BenchLabels {
   private BenchLabels() {
   }

   /** The "bench · tab" lang key of a bench recipe (or a Clothing Table one), null for any other recipe. */
   public static String where(Recipe<?> r, ItemStack result) {
      if (r instanceof ClothingTableRecipe ct) {
         return where(ct.sewing() ? BenchTab.CLOTHING : BenchCatalog.place(result.getItem()).tab());
      }
      if (r.getType() == BenchRecipes.TYPE.get()) {
         return where(BenchCatalog.place(result.getItem()).tab());
      }
      return null;
   }

   public static String where(BenchTab tab) {
      return "bench.frontierhunts.where." + tab.key;
   }
}
