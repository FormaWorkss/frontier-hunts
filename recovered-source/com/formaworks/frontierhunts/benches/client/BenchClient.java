package com.formaworks.frontierhunts.benches.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.benches.Bench;
import com.formaworks.frontierhunts.benches.BenchCatalog;
import com.formaworks.frontierhunts.benches.BenchContent;
import com.formaworks.frontierhunts.benches.BenchRecipes;
import com.formaworks.frontierhunts.benches.OldBenches;
import net.minecraft.ChatFormatting;
import net.minecraft.client.RecipeBookCategories;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterRecipeBookCategoriesEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * [benches] Client side of the benches: the bench screen for the three menus, the recipe-book category of bench recipes
 * (none: they never show in the green recipe book, and the game never logs "Unknown recipe category" for them), the
 * "retired" tooltip of the old bench items, and the recipe cache reset when the server sends new recipes.
 */
public final class BenchClient {
   private BenchClient() {
   }

   @EventBusSubscriber(modid = FrontierHunts.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
   public static final class Mod {
      private Mod() {
      }

      @SubscribeEvent
      public static void screens(RegisterMenuScreensEvent e) {
         for (Bench b : Bench.values()) {
            e.register(BenchContent.MENUS.get(b).get(), BenchScreen::new);
         }
      }

      @SubscribeEvent
      public static void recipeBook(RegisterRecipeBookCategoriesEvent e) {
         e.registerRecipeCategoryFinder(BenchRecipes.TYPE.get(), r -> RecipeBookCategories.UNKNOWN);
      }
   }

   @EventBusSubscriber(modid = FrontierHunts.ID, value = Dist.CLIENT)
   public static final class Game {
      private Game() {
      }

      @SubscribeEvent
      public static void recipes(RecipesUpdatedEvent e) {
         BenchCatalog.clear();
      }

      @SubscribeEvent
      public static void tooltip(ItemTooltipEvent e) {
         Bench b = OldBenches.successor(e.getItemStack().getItem());
         if (b == null) {
            return;
         }
         Component name = Component.translatable(b.nameKey());
         e.getToolTip().add(Component.translatable("bench.frontierhunts.retired", name).withStyle(ChatFormatting.GOLD));
         e.getToolTip().add(Component.translatable("bench.frontierhunts.retired.place", name).withStyle(ChatFormatting.GRAY));
      }
   }
}
