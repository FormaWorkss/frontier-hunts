package com.formaworks.frontierqa;

import com.formaworks.frontierhunts.benches.Bench;
import com.formaworks.frontierhunts.benches.BenchCatalog;
import com.formaworks.frontierhunts.benches.BenchMenu;
import com.formaworks.frontierhunts.benches.BenchRecipes;
import com.formaworks.frontierhunts.benches.BenchTab;
import com.formaworks.frontierhunts.benches.OldBenches;
import com.formaworks.frontierhunts.hunting.ArrowTip;
import com.formaworks.frontierhunts.workshop.WideStationBlock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * [benches] The three workbenches on a dedicated server: /fhqa bench recipes | table | craft | batch | refit | old | all.
 * Prints "bench PASS ..." / "bench FAIL ..." lines. Uses the fake hunter FHQA (crafts go through the real BenchMenu
 * button path; the old-bench conversion through the real right-click path).
 */
final class BenchTest {
   private static final Map<java.util.UUID, Integer> CRAFTED = new HashMap<>();
   private static boolean listening;

   private BenchTest() {
   }

   static void say(String s) {
      FrontierQa.say("bench " + s);
   }

   static void verdict(boolean ok, String what) {
      say((ok ? "PASS " : "FAIL ") + what);
   }

   static int run(CommandSourceStack src, String which) {
      MinecraftServer server = src.getServer();
      try {
         if (!listening) {
            listening = true;
            NeoForge.EVENT_BUS.addListener((PlayerEvent.ItemCraftedEvent e) -> CRAFTED.merge(e.getEntity().getUUID(), 1, Integer::sum));
         }
         switch (which.trim()) {
            case "recipes" -> recipes(server);
            case "table" -> table(server);
            case "craft" -> craft(server);
            case "batch" -> batch(server);
            case "refit" -> refit(server);
            case "old" -> old(server);
            case "all" -> {
               recipes(server);
               table(server);
               craft(server);
               batch(server);
               refit(server);
               old(server);
            }
            default -> say("FAIL unknown case " + which + " (recipes | table | craft | batch | refit | old | all)");
         }
      } catch (Throwable t) {
         FrontierQa.fail("bench " + which, t);
      }
      return 1;
   }

   // ============================================================================================ (a) + (f) recipes

   /** The first item of each ingredient laid out on a crafting grid as the recipe's pattern (shaped) or packed (shapeless). */
   static CraftingInput input(CraftingRecipe r) {
      List<ItemStack> grid = new ArrayList<>();
      int w, h;
      if (r instanceof ShapedRecipe s) {
         w = s.getWidth();
         h = s.getHeight();
         for (Ingredient i : s.getIngredients()) {
            grid.add(i.isEmpty() || i.getItems().length == 0 ? ItemStack.EMPTY : i.getItems()[0].copyWithCount(1));
         }
      } else {
         List<Ingredient> ings = r.getIngredients().stream().filter(i -> !i.isEmpty()).toList();
         w = 3;
         h = (ings.size() + 2) / 3;
         for (Ingredient i : ings) {
            grid.add(i.getItems().length == 0 ? ItemStack.EMPTY : i.getItems()[0].copyWithCount(1));
         }
         while (grid.size() < w * h) {
            grid.add(ItemStack.EMPTY);
         }
      }
      return CraftingInput.of(w, h, grid);
   }

   static void recipes(MinecraftServer server) {
      ServerLevel level = server.overworld();
      RecipeManager rm = level.getRecipeManager();
      // (a) a mod recipe no longer matches at the crafting table, while the bench recipe exists
      var bow = rm.byKey(ResourceLocation.parse("frontierhunts:field_bow")).orElse(null);
      verdict(bow != null && bow.value().getType() == BenchRecipes.TYPE.get(), "frontierhunts:field_bow is a bench recipe (type "
         + (bow == null ? "missing" : BuiltInRegistries.RECIPE_TYPE.getKey(bow.value().getType())) + ")");
      if (bow != null) {
         ItemStack stick = new ItemStack(Items.STICK), leather = new ItemStack(Items.LEATHER), string = new ItemStack(Items.STRING);
         CraftingInput grid = CraftingInput.of(3, 3, List.of(ItemStack.EMPTY, stick, leather, stick, ItemStack.EMPTY, string, ItemStack.EMPTY, stick, string));
         var hit = rm.getRecipeFor(RecipeType.CRAFTING, grid, level);
         verdict(hit.isEmpty(), "the field bow layout (stick, leather, string) makes nothing at a crafting table"
            + (hit.isPresent() ? " (matched " + hit.get().id() + ")" : ""));
      }
      int benchRecipes = 0, tableHits = 0;
      List<String> leaks = new ArrayList<>();
      for (RecipeHolder<CraftingRecipe> h : rm.getAllRecipesFor(BenchRecipes.TYPE.get())) {
         benchRecipes++;
         var m = rm.getRecipeFor(RecipeType.CRAFTING, input(h.value()), level);
         if (m.isPresent() && BuiltInRegistries.ITEM.getKey(m.get().value().getResultItem(level.registryAccess()).getItem()).getNamespace().equals("frontierhunts")) {
            tableHits++;
            leaks.add(h.id() + "->" + m.get().id());
         }
      }
      verdict(benchRecipes >= 200 && tableHits == 0, benchRecipes + " bench recipes; their layouts make no Frontier item at a crafting table"
         + (leaks.isEmpty() ? "" : ": " + String.join(", ", leaks.subList(0, Math.min(8, leaks.size())))));
      // no mod recipe left at the crafting table except the three benches, the handbook and vanilla-result conversions
      List<String> left = new ArrayList<>();
      for (RecipeHolder<CraftingRecipe> h : rm.getAllRecipesFor(RecipeType.CRAFTING)) {
         if (!FrontierQa.ours(h.id()) || h.value().isSpecial()) {
            continue;
         }
         ItemStack out = h.value().getResultItem(level.registryAccess());
         String rid = BuiltInRegistries.ITEM.getKey(out.getItem()).toString();
         if (!rid.startsWith("minecraft:") && !BenchCatalog.TABLE_ONLY.contains(rid)) {
            left.add(h.id().toString());
         }
      }
      verdict(left.isEmpty(), "no Frontier item is still made at the crafting table" + (left.isEmpty() ? "" : ": " + left));
      var arrowRefit = rm.byKey(ResourceLocation.parse("frontierhunts:arrow_refit"));
      verdict(arrowRefit.isEmpty(), "the crafting-grid arrow refit is gone (refit at the Reloading Bench)");
      // (f) every bench recipe on exactly one bench and tab, explicitly placed; no recipe tab empty
      Map<Bench, Integer> per = new EnumMap<>(Bench.class);
      Map<BenchTab, Integer> tabs = new EnumMap<>(BenchTab.class);
      List<String> fallback = new ArrayList<>();
      java.util.Set<ResourceLocation> seen = new java.util.HashSet<>();
      int dup = 0;
      for (Bench b : Bench.values()) {
         for (BenchCatalog.Entry e : BenchCatalog.entries(rm, level.registryAccess(), b)) {
            per.merge(b, 1, Integer::sum);
            tabs.merge(e.tab(), 1, Integer::sum);
            if (!seen.add(e.id)) {
               dup++;
            }
            if (!e.place.explicit()) {
               fallback.add(e.id + " (" + e.tab() + ")");
            }
            if (e.tab().bench != b) {
               dup++;
            }
         }
      }
      int clothing = rm.getAllRecipesFor(com.formaworks.frontierhunts.recipes.RecipesContent.CLOTHING_TABLE.get()).size();
      verdict(dup == 0 && seen.size() == benchRecipes + clothing, "every bench recipe (" + benchRecipes + ") and Clothing Table recipe (" + clothing
         + ") is on exactly one bench: " + per + (dup > 0 ? ", " + dup + " duplicates" : ""));
      verdict(fallback.isEmpty(), "every recipe has an explicit bench and tab" + (fallback.isEmpty() ? "" : "; fell back: " + fallback));
      List<String> empty = new ArrayList<>();
      for (BenchTab t : BenchTab.values()) {
         if (t.recipes() && t != BenchTab.MISC && tabs.getOrDefault(t, 0) == 0) {
            empty.add(t.name());
         }
      }
      verdict(empty.isEmpty() && tabs.getOrDefault(BenchTab.MISC, 0) == 0, "no recipe tab is empty and nothing is in Misc: " + tabs
         + (empty.isEmpty() ? "" : " EMPTY " + empty));
   }

   // ============================================================================================ (b) the benches at the table

   static void table(MinecraftServer server) {
      ServerLevel level = server.overworld();
      RecipeManager rm = level.getRecipeManager();
      for (Bench b : Bench.values()) {
         var h = rm.byKey(b.rl()).orElse(null);
         if (h == null || !(h.value() instanceof CraftingRecipe cr) || h.value().getType() != RecipeType.CRAFTING) {
            verdict(false, b.id + " has a crafting-table recipe");
            continue;
         }
         var m = rm.getRecipeFor(RecipeType.CRAFTING, input(cr), level);
         ItemStack out = m.map(x -> x.value().assemble(input(cr), level.registryAccess())).orElse(ItemStack.EMPTY);
         boolean early = true;
         for (Ingredient i : cr.getIngredients()) { // every slot can be filled with a vanilla item (tags may also hold modded planks/logs)
            if (i.isEmpty()) {
               continue;
            }
            boolean vanilla = false;
            for (ItemStack s : i.getItems()) {
               vanilla |= BuiltInRegistries.ITEM.getKey(s.getItem()).getNamespace().equals("minecraft");
            }
            early &= vanilla;
         }
         verdict(out.is(b.item()) && early, b.id + " is made at a crafting table from vanilla materials (" + m.map(x -> x.id().toString()).orElse("no match") + ")");
      }
   }

   // ============================================================================================ (c) (d) crafting at the benches

   /** Places a bench (both halves) 2 blocks east of the hunter, facing west toward them; returns the left half. */
   static BlockPos place(ServerPlayer fp, Block block) {
      ServerLevel level = fp.serverLevel();
      BlockPos at = fp.blockPosition().east(2);
      Direction facing = Direction.WEST;
      BlockState left = block.defaultBlockState().setValue(WideStationBlock.FACING, facing).setValue(WideStationBlock.PART, WideStationBlock.Part.LEFT);
      level.setBlock(at.relative(facing.getClockWise()), Blocks.AIR.defaultBlockState(), 3);
      level.setBlock(at, left, 3);
      level.setBlock(at.relative(facing.getClockWise()), left.setValue(WideStationBlock.PART, WideStationBlock.Part.RIGHT), 3);
      return at;
   }

   static void clear(ServerPlayer fp, BlockPos at) {
      ServerLevel level = fp.serverLevel();
      level.setBlock(at.relative(Direction.WEST.getClockWise()), Blocks.AIR.defaultBlockState(), 3);
      level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
   }

   static BenchCatalog.Entry entry(ServerLevel level, Bench b, String product) {
      for (BenchCatalog.Entry e : BenchCatalog.entries(level.getRecipeManager(), level.registryAccess(), b)) {
         if (BuiltInRegistries.ITEM.getKey(e.product.getItem()).toString().equals(product)) {
            return e;
         }
      }
      return null;
   }

   static void give(ServerPlayer fp, BenchCatalog.Entry e, int times) {
      for (BenchCatalog.Material m : e.materials) {
         ItemStack s = m.ingredient().getItems()[0].copyWithCount(m.count() * times);
         while (!s.isEmpty()) {
            ItemStack part = s.split(s.getMaxStackSize());
            fp.getInventory().add(part);
         }
      }
   }

   static int count(ServerPlayer fp, Item item) {
      int n = 0;
      for (ItemStack s : fp.getInventory().items) {
         if (s.is(item)) {
            n += s.getCount();
         }
      }
      return n;
   }

   static int materials(ServerPlayer fp, BenchCatalog.Entry e) {
      int n = 0;
      for (BenchCatalog.Material m : e.materials) {
         for (ItemStack s : fp.getInventory().items) {
            if (!s.isEmpty() && m.ingredient().test(s)) {
               n += s.getCount();
            }
         }
      }
      return n;
   }

   static BenchMenu open(ServerPlayer fp, Bench b, BlockPos at) {
      BenchMenu menu = new BenchMenu(9000 + b.ordinal(), fp.getInventory(), fp.serverLevel(), at, b);
      fp.containerMenu = menu;
      return menu;
   }

   static void craft(MinecraftServer server) throws Exception {
      Persist.join(server, false);
      ServerPlayer fp = FrontierQa.fake(server);
      String[][] cases = {{"FRONTIER", "frontierhunts:skinning_tool"}, {"GUNSMITH", "frontierhunts:field_bow"}, {"RELOADING", "frontierhunts:field_arrow"}};
      for (String[] c : cases) {
         Bench b = Bench.valueOf(c[0]);
         fp.getInventory().clearContent();
         BenchCatalog.Entry e = entry(fp.serverLevel(), b, c[1]);
         if (e == null) {
            verdict(false, b.id + ": no recipe for " + c[1]);
            continue;
         }
         BlockPos at = place(fp, b.block());
         give(fp, e, 1);
         Item item = e.product.getItem();
         int before = count(fp, item), mats = materials(fp, e);
         int events = CRAFTED.getOrDefault(fp.getUUID(), 0);
         BenchMenu menu = open(fp, b, at);
         boolean clicked = menu.clickMenuButton(fp, e.index);
         int after = count(fp, item);
         verdict(clicked && after == before + e.product.getCount() && materials(fp, e) == 0,
            b.id + " crafts " + c[1] + ": +" + (after - before) + " (expected +" + e.product.getCount() + "), materials " + mats + " -> " + materials(fp, e));
         // (a FakePlayer ignores awardStat, so the crafted stat itself is checked in-game: see docs/ws/benches.md)
         verdict(CRAFTED.getOrDefault(fp.getUUID(), 0) == events + 1,
            b.id + ": PlayerEvent.ItemCraftedEvent fired once for the product (" + (CRAFTED.getOrDefault(fp.getUUID(), 0) - events) + ")");
         boolean again = menu.clickMenuButton(fp, e.index);
         verdict(!again && count(fp, item) == after, b.id + ": a second click without materials does nothing");
         BenchMenu far = open(fp, b, at.east(40));
         verdict(!far.clickMenuButton(fp, e.index), b.id + ": a menu for a bench 40 blocks away is refused");
         fp.containerMenu = fp.inventoryMenu;
         clear(fp, at);
      }
      fp.getInventory().clearContent();
   }

   static void batch(MinecraftServer server) throws Exception {
      Persist.join(server, false);
      ServerPlayer fp = FrontierQa.fake(server);
      fp.getInventory().clearContent();
      BenchCatalog.Entry e = entry(fp.serverLevel(), Bench.RELOADING, "frontierhunts:rifle_round");
      if (e == null) {
         verdict(false, "reloading_bench: no rifle_round recipe");
         return;
      }
      BlockPos at = place(fp, Bench.RELOADING.block());
      give(fp, e, 5);
      Item item = e.product.getItem();
      int before = count(fp, item);
      boolean ok = open(fp, Bench.RELOADING, at).clickMenuButton(fp, BenchMenu.BATCH + e.index);
      verdict(ok && count(fp, item) == before + 5 * e.product.getCount() && materials(fp, e) == 0,
         "reloading_bench: Craft x5 makes " + (count(fp, item) - before) + " rifle rounds (expected " + 5 * e.product.getCount() + ")");
      give(fp, e, 4);
      boolean four = open(fp, Bench.RELOADING, at).clickMenuButton(fp, BenchMenu.BATCH + e.index);
      verdict(!four, "reloading_bench: Craft x5 with materials for 4 does nothing (all or nothing)");
      BenchCatalog.Entry g = entry(fp.serverLevel(), Bench.GUNSMITH, "frontierhunts:field_bow");
      BlockPos at2 = at.north(4);
      fp.getInventory().clearContent();
      fp.containerMenu = fp.inventoryMenu;
      clear(fp, at);
      if (g != null) {
         ServerLevel level = fp.serverLevel();
         BlockState left = Bench.GUNSMITH.block().defaultBlockState().setValue(WideStationBlock.FACING, Direction.WEST)
            .setValue(WideStationBlock.PART, WideStationBlock.Part.LEFT);
         level.setBlock(at2, left, 3);
         give(fp, g, 5);
         BenchMenu m = new BenchMenu(9100, fp.getInventory(), level, at2, Bench.GUNSMITH);
         fp.containerMenu = m;
         verdict(!m.clickMenuButton(fp, BenchMenu.BATCH + g.index), "gunsmith_bench: there is no Craft x5 for weapons");
         level.setBlock(at2, Blocks.AIR.defaultBlockState(), 3);
      }
      fp.containerMenu = fp.inventoryMenu;
      fp.getInventory().clearContent();
   }

   // ============================================================================================ (e) refit

   static void refit(MinecraftServer server) throws Exception {
      Persist.join(server, false);
      ServerPlayer fp = FrontierQa.fake(server);
      fp.getInventory().clearContent();
      BlockPos at = place(fp, Bench.RELOADING.block());
      ItemStack arrows = ArrowTip.arrowStack(false, ArrowTip.FIXED_BROADHEAD, 8);
      fp.getInventory().setItem(3, arrows);
      fp.getInventory().setItem(5, ArrowTip.FIELD_POINT.tipItem(8));
      BenchMenu menu = open(fp, Bench.RELOADING, at);
      boolean ok = menu.clickMenuButton(fp, BenchMenu.refitButton(3, ArrowTip.FIELD_POINT.ordinal(), true));
      ItemStack now = fp.getInventory().getItem(3);
      verdict(ok && ArrowTip.arrow(now) && ArrowTip.of(now) == ArrowTip.FIELD_POINT && now.getCount() == 8
         && count(fp, ArrowTip.FIELD_POINT.tipItem(1).getItem()) == 0,
         "reloading_bench: Refit all puts field points on 8 hunting arrows (" + now.getCount() + " x " + ArrowTip.of(now).title + ", points left "
            + count(fp, ArrowTip.FIELD_POINT.tipItem(1).getItem()) + ")");
      BenchMenu menu2 = open(fp, Bench.RELOADING, at);
      boolean off = menu2.clickMenuButton(fp, BenchMenu.refitButton(3, 15, true));
      ItemStack back = fp.getInventory().getItem(3);
      verdict(off && ArrowTip.of(back) == ArrowTip.FIXED_BROADHEAD && count(fp, ArrowTip.FIELD_POINT.tipItem(1).getItem()) == 8,
         "reloading_bench: Heads off returns the 8 field points and the arrows get their stock broadhead");
      BlockPos g = at.north(4);
      fp.serverLevel().setBlock(g, Bench.GUNSMITH.block().defaultBlockState().setValue(WideStationBlock.FACING, Direction.WEST)
         .setValue(WideStationBlock.PART, WideStationBlock.Part.LEFT), 3);
      BenchMenu gm = new BenchMenu(9200, fp.getInventory(), fp.serverLevel(), g, Bench.GUNSMITH);
      fp.containerMenu = gm;
      verdict(!gm.clickMenuButton(fp, BenchMenu.refitButton(3, ArrowTip.FIELD_POINT.ordinal(), true)), "gunsmith_bench refuses arrow refits");
      fp.serverLevel().setBlock(g, Blocks.AIR.defaultBlockState(), 3);
      fp.containerMenu = fp.inventoryMenu;
      clear(fp, at);
      fp.getInventory().clearContent();
   }

   // ============================================================================================ (g) old benches

   static BlockHitResult hit(BlockPos pos) {
      return new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
   }

   static void old(MinecraftServer server) throws Exception {
      Persist.join(server, false);
      ServerPlayer fp = FrontierQa.fake(server);
      ServerLevel level = fp.serverLevel();
      fp.getInventory().clearContent();
      fp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
      String[][] pairs = {{"weapons_workbench", "GUNSMITH"}, {"bow_tuning_rack", "GUNSMITH"}, {"ammo_reloader", "RELOADING"}, {"tent_bench", "FRONTIER"},
         {"clothing_workbench", "FRONTIER"}, {"fishing_station", "FRONTIER"}, {"attachment_workbench", "GUNSMITH"}};
      Direction facing = Direction.SOUTH;
      for (String[] p : pairs) {
         Block old = OldBenches.block(p[0]);
         Bench want = Bench.valueOf(p[1]);
         BlockPos at = fp.blockPosition().east(2);
         BlockPos right = at.relative(facing.getClockWise());
         boolean single = p[0].equals("ammo_reloader");
         BlockState left = old.defaultBlockState().setValue(WideStationBlock.FACING, facing)
            .setValue(WideStationBlock.PART, single ? WideStationBlock.Part.SINGLE : WideStationBlock.Part.LEFT);
         level.setBlock(right, Blocks.AIR.defaultBlockState(), 3);
         level.setBlock(at, left, 3);
         if (!single) {
            level.setBlock(right, left.setValue(WideStationBlock.PART, WideStationBlock.Part.RIGHT), 3);
         }
         // use the right half of a pair (or the single block), like a player would
         var r = fp.gameMode.useItemOn(fp, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit(single ? at : right));
         BlockState a = level.getBlockState(at), b = level.getBlockState(right);
         boolean rebuilt = a.is(want.block()) && b.is(want.block()) && a.getValue(WideStationBlock.PART) == WideStationBlock.Part.LEFT
            && b.getValue(WideStationBlock.PART) == WideStationBlock.Part.RIGHT && a.getValue(WideStationBlock.FACING) == facing
            && b.getValue(WideStationBlock.FACING) == facing;
         // (a FakePlayer never opens menus: the use being consumed is what the opening path returns)
         boolean used = r.consumesAction();
         verdict(rebuilt && used, "using an old " + p[0] + " rebuilds it as " + want.id + " (both halves, facing kept: " + rebuilt + "), use " + r);
         fp.containerMenu = fp.inventoryMenu;
         level.setBlock(right, Blocks.AIR.defaultBlockState(), 3);
         level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
      }
      // a single old block with no room beside it: opens the successor, block stays
      BlockPos at = fp.blockPosition().east(2);
      BlockPos right = at.relative(facing.getClockWise());
      level.setBlock(right, Blocks.STONE.defaultBlockState(), 3);
      Block reloader = OldBenches.block("ammo_reloader");
      level.setBlock(at, reloader.defaultBlockState().setValue(WideStationBlock.FACING, facing), 3);
      var used = fp.gameMode.useItemOn(fp, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit(at));
      boolean kept = level.getBlockState(at).is(reloader) && level.getBlockState(right).is(Blocks.STONE);
      BenchMenu there = new BenchMenu(9300, fp.getInventory(), level, at, Bench.RELOADING);
      boolean valid = there.stillValid(fp);
      verdict(kept && used.consumesAction() && valid, "a cramped old ammo_reloader stays put and works as a Reloading Bench (kept " + kept + ", use " + used
         + ", menu valid " + valid + ")");
      fp.containerMenu = fp.inventoryMenu;
      level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
      level.setBlock(right, Blocks.AIR.defaultBlockState(), 3);
      // placing an old bench item builds its successor
      BlockPos ground = fp.blockPosition().east(3).below();
      level.setBlock(ground, Blocks.STONE.defaultBlockState(), 3);
      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            level.setBlock(ground.above().offset(dx, 0, dz), Blocks.AIR.defaultBlockState(), 3);
         }
      }
      Item oldItem = OldBenches.block("weapons_workbench").asItem();
      fp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(oldItem));
      fp.gameMode.useItemOn(fp, level, fp.getMainHandItem(), InteractionHand.MAIN_HAND, hit(ground));
      BlockState placed = level.getBlockState(ground.above());
      boolean ok = placed.is(Bench.GUNSMITH.block());
      BlockPos partner = ok ? WideStationBlock.partner(ground.above(), placed) : ground.above();
      verdict(ok && level.getBlockState(partner).is(Bench.GUNSMITH.block()) && fp.getMainHandItem().isEmpty(),
         "placing an old weapons_workbench item builds a two-block Gunsmith's Bench (" + placed + ")");
      level.setBlock(partner, Blocks.AIR.defaultBlockState(), 3);
      level.setBlock(ground.above(), Blocks.AIR.defaultBlockState(), 3);
      fp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
      verdict(OldBenches.modernise(new ItemStack(OldBenches.block("fishing_station").asItem(), 2)).is(Bench.FRONTIER.item()),
         "a retired bench handed out by old code becomes its successor (fishing_station -> frontier_workbench)");
   }
}
