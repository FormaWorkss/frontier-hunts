package com.formaworks.frontierqa;

import com.formaworks.frontierhunts.benches.Bench;
import com.formaworks.frontierhunts.benches.BenchCatalog;
import com.formaworks.frontierhunts.benches.BenchMenu;
import com.formaworks.frontierhunts.benches.BenchTab;
import com.formaworks.frontierhunts.hunting.ArrowTip;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * [smalls] /fhqa smalls arrows | make | refit | food | all. Prints "smalls PASS ..." / "smalls FAIL ...".
 * arrows: a fitted-arrow recipe ("Hunting Arrow · Field Point") takes a stick, a feather and four heads and gives four
 * arrows carrying that head; make: with no heads but the head's materials, the craft makes the heads first; refit: the
 * Fit arrowheads tab still refits a stack and gives old heads back; food: every crafted food is on the Frontier
 * Workbench's Food tab, the Food tab holds only food, and every Frontier food item has a way to get it.
 */
final class SmallsTest {
   /** Frontier foods made without a recipe: harvest drops, the game pole, the drying rack, spoilage. */
   static final Set<String> NO_RECIPE = Set.of("venison", "venison_quarter", "backstrap", "organ_meat", "game_fat", "game_meat", "bear_meat",
      "wild_fowl", "aged_venison", "jerky", "spoiled_meat");

   private SmallsTest() {
   }

   static void verdict(boolean ok, String what) {
      FrontierQa.say("smalls " + (ok ? "PASS " : "FAIL ") + what);
   }

   static int run(CommandSourceStack src, String which) {
      MinecraftServer server = src.getServer();
      try {
         switch (which.trim()) {
            case "arrows" -> arrows(server);
            case "make" -> make(server);
            case "refit" -> refit(server);
            case "food" -> food(server);
            case "all" -> {
               arrows(server);
               make(server);
               refit(server);
               food(server);
            }
            default -> verdict(false, "unknown case " + which + " (arrows | make | refit | food | all)");
         }
      } catch (Exception e) {
         verdict(false, which + " threw " + e);
      }
      return 1;
   }

   static BenchCatalog.Entry byRecipe(ServerLevel level, Bench b, String recipe) {
      for (BenchCatalog.Entry e : BenchCatalog.entries(level.getRecipeManager(), level.registryAccess(), b)) {
         if (e.id.toString().equals(recipe)) {
            return e;
         }
      }
      return null;
   }

   static int arrowsWith(ServerPlayer fp, ArrowTip tip) {
      int n = 0;
      for (ItemStack s : fp.getInventory().items) {
         if (s.is(BuiltInRegistries.ITEM.get(ResourceLocation.parse("frontierhunts:field_arrow"))) && ArrowTip.of(s) == tip) {
            n += s.getCount();
         }
      }
      return n;
   }

   static int count(ServerPlayer fp, Item item) {
      return BenchTest.count(fp, item);
   }

   // ============================================================================================ arrows made with a head

   static void arrows(MinecraftServer server) throws Exception {
      Persist.join(server, false);
      ServerPlayer fp = FrontierQa.fake(server);
      fp.getInventory().clearContent();
      ServerLevel level = fp.serverLevel();
      BenchCatalog.Entry e = byRecipe(level, Bench.RELOADING, "frontierhunts:arrows/hunting_arrow_field_point");
      verdict(e != null && e.tab() == BenchTab.ARROWS && e.fitTip == ArrowTip.FIELD_POINT && e.headEntry != null,
         "Hunting Arrow · Field Point is on the Reloading Bench's Arrows tab" + (e == null ? " (missing)" : " (" + e.tab() + " / " + e.place.section()
            + ", head recipe " + (e.headEntry == null ? "none" : e.headEntry.id) + ")"));
      int fitted = 0;
      for (BenchCatalog.Entry x : BenchCatalog.entries(level.getRecipeManager(), level.registryAccess(), Bench.RELOADING)) {
         if (x.fitTip != null) {
            fitted++;
         }
      }
      verdict(fitted == 9, "the Arrows tab lists 9 arrows made with a chosen head (" + fitted + ")");
      if (e == null) {
         return;
      }
      verdict(e.display.getHoverName().getString().equals("Hunting Arrow · Field Point"), "its tile is named \"" + e.display.getHoverName().getString() + "\"");
      BlockPos at = BenchTest.place(fp, Bench.RELOADING.block());
      fp.getInventory().add(new ItemStack(Items.STICK));
      fp.getInventory().add(new ItemStack(Items.FEATHER));
      fp.getInventory().add(ArrowTip.FIELD_POINT.tipItem(4));
      Item point = ArrowTip.FIELD_POINT.tipItem(1).getItem();
      boolean ok = BenchTest.open(fp, Bench.RELOADING, at).clickMenuButton(fp, e.index);
      verdict(ok && arrowsWith(fp, ArrowTip.FIELD_POINT) == 4 && count(fp, point) == 0 && count(fp, Items.STICK) == 0 && count(fp, Items.FEATHER) == 0,
         "stick + feather + 4 field points -> " + arrowsWith(fp, ArrowTip.FIELD_POINT) + " Hunting Arrows with field points (points left " + count(fp, point)
            + ", sticks " + count(fp, Items.STICK) + ", feathers " + count(fp, Items.FEATHER) + ")");
      boolean again = BenchTest.open(fp, Bench.RELOADING, at).clickMenuButton(fp, e.index);
      verdict(!again, "a second click with nothing left does nothing");
      fp.containerMenu = fp.inventoryMenu;
      BenchTest.clear(fp, at);
      fp.getInventory().clearContent();
   }

   static void make(MinecraftServer server) throws Exception {
      Persist.join(server, false);
      ServerPlayer fp = FrontierQa.fake(server);
      fp.getInventory().clearContent();
      ServerLevel level = fp.serverLevel();
      BenchCatalog.Entry e = byRecipe(level, Bench.RELOADING, "frontierhunts:arrows/hunting_arrow_field_point");
      if (e == null || e.headEntry == null) {
         verdict(false, "no fitted field-point arrow recipe / head recipe");
         return;
      }
      BlockPos at = BenchTest.place(fp, Bench.RELOADING.block());
      fp.getInventory().add(new ItemStack(Items.STICK));
      fp.getInventory().add(new ItemStack(Items.FEATHER));
      BenchTest.give(fp, e.headEntry, 1); // the field point recipe's materials, no heads
      int mats = BenchTest.materials(fp, e.headEntry);
      boolean ok = BenchTest.open(fp, Bench.RELOADING, at).clickMenuButton(fp, e.index);
      Item point = ArrowTip.FIELD_POINT.tipItem(1).getItem();
      verdict(ok && arrowsWith(fp, ArrowTip.FIELD_POINT) == 4 && BenchTest.materials(fp, e.headEntry) == 0,
         "no heads, but their materials: the craft makes the heads first -> " + arrowsWith(fp, ArrowTip.FIELD_POINT) + " arrows, head materials " + mats
            + " -> " + BenchTest.materials(fp, e.headEntry) + ", spare points " + count(fp, point));
      fp.getInventory().clearContent();
      fp.getInventory().add(new ItemStack(Items.STICK));
      fp.getInventory().add(new ItemStack(Items.FEATHER));
      boolean none = BenchTest.open(fp, Bench.RELOADING, at).clickMenuButton(fp, e.index);
      verdict(!none && count(fp, Items.STICK) == 1, "no heads and no materials for them: nothing is taken");
      // Craft x5 (all or nothing) with heads for 5
      fp.getInventory().clearContent();
      fp.getInventory().add(new ItemStack(Items.STICK, 5));
      fp.getInventory().add(new ItemStack(Items.FEATHER, 5));
      fp.getInventory().add(ArrowTip.FIELD_POINT.tipItem(20));
      boolean five = BenchTest.open(fp, Bench.RELOADING, at).clickMenuButton(fp, BenchMenu.BATCH + e.index);
      verdict(five && arrowsWith(fp, ArrowTip.FIELD_POINT) == 20, "Craft x5 makes " + arrowsWith(fp, ArrowTip.FIELD_POINT) + " arrows (expected 20)");
      fp.containerMenu = fp.inventoryMenu;
      BenchTest.clear(fp, at);
      fp.getInventory().clearContent();
   }

   // ============================================================================================ Fit arrowheads still works

   static void refit(MinecraftServer server) throws Exception {
      Persist.join(server, false);
      ServerPlayer fp = FrontierQa.fake(server);
      fp.getInventory().clearContent();
      BlockPos at = BenchTest.place(fp, Bench.RELOADING.block());
      fp.getInventory().setItem(3, ArrowTip.arrowStack(false, ArrowTip.FIELD_POINT, 6));
      fp.getInventory().setItem(5, ArrowTip.MECHANICAL_BROADHEAD.tipItem(6));
      boolean ok = BenchTest.open(fp, Bench.RELOADING, at).clickMenuButton(fp, BenchMenu.refitButton(3, ArrowTip.MECHANICAL_BROADHEAD.ordinal(), true));
      ItemStack now = fp.getInventory().getItem(3);
      Item mech = ArrowTip.MECHANICAL_BROADHEAD.tipItem(1).getItem(), point = ArrowTip.FIELD_POINT.tipItem(1).getItem();
      verdict(ok && ArrowTip.of(now) == ArrowTip.MECHANICAL_BROADHEAD && now.getCount() == 6 && count(fp, mech) == 0 && count(fp, point) == 6,
         "Fit arrowheads: 6 field-point arrows -> " + now.getCount() + " x " + ArrowTip.of(now).title + ", the 6 field points came back (" + count(fp, point)
            + ")");
      fp.containerMenu = fp.inventoryMenu;
      BenchTest.clear(fp, at);
      fp.getInventory().clearContent();
   }

   // ============================================================================================ food

   static void food(MinecraftServer server) {
      ServerLevel level = server.overworld();
      var rm = level.getRecipeManager();
      List<String> wrongTab = new ArrayList<>(), notFood = new ArrayList<>(), onTab = new ArrayList<>();
      for (BenchCatalog.Entry e : BenchCatalog.all(rm, level.registryAccess())) {
         boolean edible = e.product.has(DataComponents.FOOD);
         String id = BuiltInRegistries.ITEM.getKey(e.product.getItem()).toString();
         if (edible && e.tab() != BenchTab.FOOD) {
            wrongTab.add(id + " (" + e.tab() + ")");
         }
         if (e.tab() == BenchTab.FOOD) {
            onTab.add(id);
            if (!edible) {
               notFood.add(id);
            }
         }
      }
      verdict(wrongTab.isEmpty(), "every food made at a bench is on the Food tab" + (wrongTab.isEmpty() ? "" : ": " + wrongTab));
      verdict(notFood.isEmpty() && onTab.size() >= 3, "the Food tab holds only food: " + onTab + (notFood.isEmpty() ? "" : ", not food " + notFood));
      // every Frontier food item: made by some recipe (bench, campfire, smoker, furnace, Dutch oven) or a known non-recipe source
      Set<String> made = new HashSet<>();
      for (RecipeHolder<?> h : rm.getRecipes()) {
         try {
            ItemStack out = h.value().getResultItem(level.registryAccess());
            if (out != null && !out.isEmpty()) {
               made.add(BuiltInRegistries.ITEM.getKey(out.getItem()).toString());
            }
         } catch (RuntimeException ignored) {
            // special recipes without a fixed result
         }
      }
      List<String> foods = new ArrayList<>(), orphans = new ArrayList<>();
      for (Item item : BuiltInRegistries.ITEM) {
         ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
         if (!id.getNamespace().equals("frontierhunts") || !new ItemStack(item).has(DataComponents.FOOD)) {
            continue;
         }
         foods.add(id.getPath());
         if (!made.contains(id.toString()) && !NO_RECIPE.contains(id.getPath())) {
            orphans.add(id.getPath());
         }
      }
      verdict(orphans.isEmpty(), foods.size() + " Frontier foods, each made by a recipe or a known source (harvest, game pole, drying rack, spoilage)"
         + (orphans.isEmpty() ? "" : "; NO SOURCE: " + orphans));
      FrontierQa.say("smalls foods " + foods);
   }
}
