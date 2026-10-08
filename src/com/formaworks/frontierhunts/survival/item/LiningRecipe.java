package com.formaworks.frontierhunts.survival.item;

import com.formaworks.frontierhunts.survival.Clothing;
import com.formaworks.frontierhunts.survival.SurvivalContent;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * [survival] Layering: sew a Fur Lining into any worn piece (head, chest, legs, feet: camo, ghillie, coveralls, armour)
 * or Fur Mittens into any chest piece. The piece keeps its look, name, enchantments and damage.
 */
public final class LiningRecipe extends CustomRecipe {
   public LiningRecipe(CraftingBookCategory category) {
      super(category);
   }

   private static EquipmentSlot slot(ItemStack s) {
      Equipable e = Equipable.get(s);
      if (e == null) {
         return null;
      }
      EquipmentSlot slot = e.getEquipmentSlot();
      return slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR ? slot : null;
   }

   /** {piece index, add bit} or null. */
   private static int[] find(CraftingInput in) {
      int piece = -1, bit = 0;
      for (int i = 0; i < in.size(); i++) {
         ItemStack s = in.getItem(i);
         if (s.isEmpty()) {
            continue;
         }
         if (s.is(SurvivalContent.FUR_LINING.get()) || s.is(SurvivalContent.FUR_MITTENS.get())) {
            if (bit != 0) {
               return null;
            }
            bit = s.is(SurvivalContent.FUR_LINING.get()) ? 1 : 2;
         } else if (slot(s) != null && s.getCount() == 1) {
            if (piece >= 0) {
               return null;
            }
            piece = i;
         } else {
            return null;
         }
      }
      if (piece < 0 || bit == 0) {
         return null;
      }
      ItemStack p = in.getItem(piece);
      if ((Clothing.lining(p) & bit) != 0 || bit == 2 && slot(p) != EquipmentSlot.CHEST) {
         return null;
      }
      return new int[]{piece, bit};
   }

   @Override
   public boolean matches(CraftingInput in, Level level) {
      return find(in) != null;
   }

   @Override
   public ItemStack assemble(CraftingInput in, HolderLookup.Provider provider) {
      int[] f = find(in);
      if (f == null) {
         return ItemStack.EMPTY;
      }
      ItemStack out = in.getItem(f[0]).copyWithCount(1);
      out.set(SurvivalContent.LINING.get(), Clothing.lining(out) | f[1]);
      return out;
   }

   @Override
   public boolean canCraftInDimensions(int w, int h) {
      return w * h >= 2;
   }

   @Override
   public RecipeSerializer<?> getSerializer() {
      return SurvivalContent.LINING_RECIPE.get();
   }
}
