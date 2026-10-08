package com.formaworks.frontierhunts.survival.item;

import com.formaworks.frontierhunts.survival.FoodValues;
import com.formaworks.frontierhunts.survival.Freshness;
import com.formaworks.frontierhunts.survival.NutritionTable;
import com.formaworks.frontierhunts.survival.Perishable;
import com.formaworks.frontierhunts.survival.SurvivalContent;
import com.formaworks.frontierhunts.survival.SurvivalMath;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * [survival] Salt curing: one Salt with up to eight pieces of the same raw meat or fish gives the same pieces salt-cured
 * (they keep five times longer). Already-cured or spoiled meat is refused.
 */
public final class SaltCureRecipe extends CustomRecipe {
   public SaltCureRecipe(CraftingBookCategory category) {
      super(category);
   }

   private static boolean curable(ItemStack s) {
      FoodValues v = NutritionTable.any(s.getItem());
      if (v == null || !v.raw() || !v.perishable() || v.spoiled()) {
         return false;
      }
      Freshness f = Perishable.get(s);
      return f == null || f.cure() == SurvivalMath.RAW;
   }

   /** Number of meat pieces, or 0 when the grid is not a salt cure. */
   private static int count(CraftingInput in) {
      int salt = 0, meat = 0;
      Item kind = null;
      for (int i = 0; i < in.size(); i++) {
         ItemStack s = in.getItem(i);
         if (s.isEmpty()) {
            continue;
         }
         if (s.is(SurvivalContent.SALT.get())) {
            salt++;
         } else if (curable(s) && (kind == null || kind == s.getItem())) {
            kind = s.getItem();
            meat++;
         } else {
            return 0;
         }
      }
      return salt == 1 && meat >= 1 ? meat : 0;
   }

   @Override
   public boolean matches(CraftingInput in, Level level) {
      return count(in) > 0;
   }

   @Override
   public ItemStack assemble(CraftingInput in, HolderLookup.Provider provider) {
      int n = count(in);
      if (n <= 0) {
         return ItemStack.EMPTY;
      }
      ItemStack meat = ItemStack.EMPTY;
      for (int i = 0; i < in.size() && meat.isEmpty(); i++) {
         if (!in.getItem(i).isEmpty() && !in.getItem(i).is(SurvivalContent.SALT.get())) {
            meat = in.getItem(i);
         }
      }
      ItemStack out = meat.copyWithCount(n);
      MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
      long now = server != null ? server.overworld().getGameTime() : 0L;
      out.set(SurvivalContent.FRESHNESS.get(), new Freshness(SurvivalMath.stamp(now), SurvivalMath.AMBIENT, SurvivalMath.SALTED));
      return out;
   }

   @Override
   public boolean canCraftInDimensions(int w, int h) {
      return w * h >= 2;
   }

   @Override
   public RecipeSerializer<?> getSerializer() {
      return SurvivalContent.SALT_CURE.get();
   }
}
