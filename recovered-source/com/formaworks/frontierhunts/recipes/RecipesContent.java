package com.formaworks.frontierhunts.recipes;

import com.formaworks.frontierhunts.FrontierHunts;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;

/** [recipes] Registers the Clothing Table recipe type + serializer ({@code frontierhunts:clothing_table}). */
@EventBusSubscriber(modid = FrontierHunts.ID, bus = EventBusSubscriber.Bus.MOD)
public final class RecipesContent {
   public static final ResourceLocation CLOTHING_TABLE_ID = ResourceLocation.fromNamespaceAndPath(FrontierHunts.ID, "clothing_table");
   public static final DeferredHolder<RecipeType<?>, RecipeType<ClothingTableRecipe>> CLOTHING_TABLE = DeferredHolder.create(
      Registries.RECIPE_TYPE, CLOTHING_TABLE_ID
   );
   public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<ClothingTableRecipe>> CLOTHING_TABLE_SERIALIZER = DeferredHolder.create(
      Registries.RECIPE_SERIALIZER, CLOTHING_TABLE_ID
   );

   private RecipesContent() {
   }

   @SubscribeEvent
   public static void register(RegisterEvent e) {
      e.register(Registries.RECIPE_TYPE, CLOTHING_TABLE_ID, () -> new RecipeType<ClothingTableRecipe>() {
         @Override
         public String toString() {
            return CLOTHING_TABLE_ID.toString();
         }
      });
      e.register(Registries.RECIPE_SERIALIZER, CLOTHING_TABLE_ID, ClothingTableRecipe.Serializer::new);
   }
}
