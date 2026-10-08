package com.formaworks.frontierhunts.recipes;

import com.formaworks.frontierhunts.survival.Clothing;
import com.formaworks.frontierhunts.survival.SurvivalContent;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

/**
 * [recipes] The Clothing Table's own recipe list and the sewing rule. Used by {@code workshop/EquipmentCatalog} (list) and
 * {@code workshop/WorkbenchMenu} (sewing plan); both run on client and server from the same synced recipes, so the
 * button indices agree.
 */
public final class ClothingTable {
   /** List order of the recipe groups (unknown groups sort last, then by recipe id). */
   private static final List<String> GROUPS = List.of("frontier_furs", "fur_layers", "sewing", "carbon_layer");
   private static final Map<RecipeManager, List<RecipeHolder<CraftingRecipe>>> CACHE = new WeakHashMap<>();

   private ClothingTable() {
   }

   public static synchronized void clear() {
      CACHE.clear();
   }

   /** Clothing Table recipes, ordered by group then id. Typed as crafting recipes for the shared workbench code. */
   @SuppressWarnings({"unchecked", "rawtypes"})
   public static synchronized List<RecipeHolder<CraftingRecipe>> recipes(RecipeManager manager) {
      return CACHE.computeIfAbsent(manager, m -> (List) m.getAllRecipesFor(RecipesContent.CLOTHING_TABLE.get())
         .stream()
         .filter(h -> !h.value().getIngredients().stream().allMatch(i -> i.isEmpty()))
         .sorted(Comparator.<RecipeHolder<ClothingTableRecipe>>comparingInt(h -> rank(h.value().getGroup()))
            .thenComparing(h -> h.id().toString()))
         .toList());
   }

   private static int rank(String group) {
      int i = GROUPS.indexOf(group);
      return i < 0 ? GROUPS.size() : i;
   }

   /**
    * Can {@code bit} (1 fur lining, 2 fur mittens) be sewn into this stack: one worn piece (head / chest / legs / feet:
    * the fur garments, camo, ghillie, coveralls, carbon layer, armour) that does not have it yet; mittens only on a chest
    * piece. Same rule as the old crafting-grid {@code LiningRecipe}.
    */
   public static boolean canSew(ItemStack s, int bit) {
      if (s.isEmpty() || s.getCount() != 1 || bit == 0) {
         return false;
      }
      if (s.is(SurvivalContent.FUR_LINING.get()) || s.is(SurvivalContent.FUR_MITTENS.get())) {
         return false;
      }
      Equipable e = Equipable.get(s);
      if (e == null) {
         return false;
      }
      EquipmentSlot slot = e.getEquipmentSlot();
      if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR || bit == 2 && slot != EquipmentSlot.CHEST) {
         return false;
      }
      return (Clothing.lining(s) & bit) == 0;
   }

   /** A copy of {@code s} with {@code bit} sewn in. */
   public static ItemStack sewn(ItemStack s, int bit) {
      ItemStack out = s.copyWithCount(1);
      out.set(SurvivalContent.LINING.get(), Clothing.lining(out) | bit);
      return out;
   }
}
