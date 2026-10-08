package com.formaworks.frontierhunts.guide.client;

import com.formaworks.frontierhunts.client.FrontierUi;
import com.formaworks.frontierhunts.hunting.ArrowTip;
import com.formaworks.frontierhunts.recipes.ClothingTableRecipe;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;

/**
 * [onboard] Drawing helpers of the Frontier Handbook: real item icons from the registry and recipe cards read live from
 * the client's recipe manager (so a recipe shown is always the recipe the game uses, tags cycle through their items).
 */
final class HandbookUi {
   private static final Map<String, ItemStack> STACKS = new HashMap<>();
   static final int SLOT = 18;
   static final int SLOT_BG = 0xFFD7CDB2;
   static final int SLOT_EDGE = 0xFF9C8B68;
   static final int SLOT_SHADE = 0xFFC2B796;

   private HandbookUi() {
   }

   static ItemStack stack(String id) {
      return STACKS.computeIfAbsent(id, k -> {
         ResourceLocation rl = ResourceLocation.tryParse(k);
         Item i = rl == null ? Items.AIR : BuiltInRegistries.ITEM.get(rl);
         return i == Items.AIR ? ItemStack.EMPTY : new ItemStack(i);
      });
   }

   /** A real item icon at (x, y) (top-left of a 16 px icon), scaled. */
   static void item(GuiGraphics g, String id, float x, float y, float scale) {
      ItemStack st = stack(id);
      if (st.isEmpty()) {
         return;
      }
      g.pose().pushPose();
      g.pose().translate(x, y, 0.0F);
      g.pose().scale(scale, scale, 1.0F);
      g.renderItem(st, 0, 0);
      g.pose().popPose();
   }

   /** One slot, journal style. */
   static void slot(GuiGraphics g, int x, int y, boolean result) {
      int s = result ? SLOT + 6 : SLOT;
      g.fill(x, y, x + s, y + s, SLOT_EDGE);
      g.fill(x + 1, y + 1, x + s, y + s, 0xFFF2EBDA);
      g.fill(x + 1, y + 1, x + s - 1, y + s - 1, result ? 0xFFE4D6AE : SLOT_BG);
      g.fill(x + 1, y + 1, x + s - 1, y + 2, SLOT_SHADE);
      g.fill(x + 1, y + 1, x + 2, y + s - 1, SLOT_SHADE);
   }

   // ============================================================================================ recipes

   /** A recipe ready to draw: grid ingredients (row major, w x h), result, and the station it is made at. */
   record View(String id, int w, int h, List<Ingredient> grid, ItemStack result, String station) {
      int width() {
         return 6 + this.w * SLOT + 26 + SLOT + 6 + 6;
      }

      int height() {
         return 14 + Math.max(this.h * SLOT, SLOT + 6) + 13;
      }
   }

   private static final Map<String, View> CACHE = new HashMap<>();
   private static Object cacheOwner;

   /** Reads a recipe from the recipe manager; null when the world has no such recipe (data packs). */
   static View view(String id) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return null;
      }
      if (cacheOwner != mc.level.getRecipeManager()) {
         cacheOwner = mc.level.getRecipeManager();
         CACHE.clear();
      }
      if (CACHE.containsKey(id)) {
         return CACHE.get(id);
      }
      View v = null;
      try {
         v = id.startsWith("fit:") ? fitting(id.substring(4)) : build(id);
      } catch (RuntimeException ignored) {
      }
      CACHE.put(id, v);
      return v;
   }

   private static View build(String id) {
      Minecraft mc = Minecraft.getInstance();
      ResourceLocation rl = ResourceLocation.tryParse(id);
      if (rl == null) {
         return null;
      }
      RecipeHolder<?> holder = mc.level.getRecipeManager().byKey(rl).orElse(null);
      if (holder == null) {
         return null;
      }
      Recipe<?> r = holder.value();
      ItemStack result = r instanceof ClothingTableRecipe c ? c.product() : r.getResultItem(mc.level.registryAccess());
      if (result == null || result.isEmpty()) {
         return null;
      }
      List<Ingredient> ings = new ArrayList<>(r.getIngredients());
      // [benches] made at a bench: no grid there, so the card packs the ingredients and names the bench and tab
      String bench = com.formaworks.frontierhunts.benches.BenchLabels.where(r, result);
      if (bench != null) {
         List<Ingredient> packed = new ArrayList<>();
         for (Ingredient i : ings) {
            if (!i.isEmpty()) {
               packed.add(i);
            }
         }
         int n = Math.max(1, packed.size());
         int w = n <= 1 ? 1 : (n <= 4 ? 2 : 3);
         return new View(id, w, (n + w - 1) / w, packed, result, bench);
      }
      if (r instanceof com.formaworks.frontierhunts.campcook.CampCookingRecipe) { // [licence] cooked in the Camp Dutch Oven
         int n = Math.max(1, ings.size());
         return new View(id, n <= 4 ? 2 : 3, (n + (n <= 4 ? 1 : 2)) / (n <= 4 ? 2 : 3), ings, result, "onboard.frontierhunts.station.dutch_oven");
      }
      if (r instanceof ClothingTableRecipe) {
         ShapedRecipe s = (ShapedRecipe)r;
         return new View(id, s.getWidth(), s.getHeight(), ings, result, "onboard.frontierhunts.station.clothing");
      }
      if (r instanceof ShapedRecipe s) {
         String st = s.getWidth() <= 2 && s.getHeight() <= 2 ? "onboard.frontierhunts.station.hand" : "onboard.frontierhunts.station.table";
         return new View(id, s.getWidth(), s.getHeight(), ings, result, st);
      }
      if (r instanceof AbstractCookingRecipe) {
         return new View(id, 1, 1, ings.subList(0, Math.min(1, ings.size())), result,
            r instanceof CampfireCookingRecipe ? "onboard.frontierhunts.station.campfire" : "onboard.frontierhunts.station.furnace");
      }
      // shapeless: pack into a square-ish grid
      int n = Math.max(1, ings.size());
      int w = n <= 1 ? 1 : (n <= 4 ? 2 : 3);
      int h = (n + w - 1) / w;
      return new View(id, w, h, ings, result, n <= 4 ? "onboard.frontierhunts.station.hand" : "onboard.frontierhunts.station.table");
   }

   /** Arrow + tip in a crafting grid (or whole stacks at the Bow Tuning Rack): the fitted arrow. */
   private static View fitting(String tipId) {
      ArrowTip tip = ArrowTip.byId(tipId);
      ItemStack arrow = stack("frontierhunts:field_arrow");
      ItemStack head = stack("frontierhunts:" + tipId);
      if (tip == null || arrow.isEmpty() || head.isEmpty()) {
         return null;
      }
      ItemStack fitted = ArrowTip.with(arrow.copy(), tip);
      return new View("fit:" + tipId, 2, 1, List.of(Ingredient.of(arrow), Ingredient.of(head)), fitted, "onboard.frontierhunts.station.fit");
   }

   /** The stack an ingredient shows right now (tags cycle once a second). */
   static ItemStack shown(Ingredient ing) {
      if (ing == null || ing.isEmpty()) {
         return ItemStack.EMPTY;
      }
      ItemStack[] items = ing.getItems();
      if (items.length == 0) {
         return ItemStack.EMPTY;
      }
      return items[(int)(System.currentTimeMillis() / 1000L % items.length)];
   }

   /**
    * Draws a recipe card at (x, y): result name, the grid, an arrow, the result slot with its count, and where it is
    * made. Returns the stack under the mouse (for a tooltip) or EMPTY.
    */
   static ItemStack card(GuiGraphics g, View v, int x, int y, int mx, int my) {
      ItemStack hover = ItemStack.EMPTY;
      int cw = v.width(), ch = v.height();
      FrontierUi.rect(g, x, y, cw, ch, 3.0F, 0x1E8F6E2F);
      FrontierUi.text(g, FrontierUi.fit(v.result.getHoverName().getString(), cw - 10, FrontierUi.Size.SMALL), x + 6, y + 4, GuideUi.INK_BROWN,
         FrontierUi.Size.SMALL);
      int gy = y + 14;
      int gridH = v.h * SLOT;
      int top = gy + Math.max(0, (SLOT + 6 - gridH) / 2);
      for (int r = 0; r < v.h; r++) {
         for (int c = 0; c < v.w; c++) {
            int sx = x + 6 + c * SLOT, sy = top + r * SLOT;
            slot(g, sx, sy, false);
            int idx = r * v.w + c;
            if (idx < v.grid.size()) {
               ItemStack st = shown(v.grid.get(idx));
               if (!st.isEmpty()) {
                  g.renderItem(st, sx + 1, sy + 1);
                  if (mx >= sx && mx < sx + SLOT && my >= sy && my < sy + SLOT) {
                     hover = st;
                  }
               }
            }
         }
      }
      int ax = x + 6 + v.w * SLOT + 5;
      int ay = gy + Math.max(gridH, SLOT + 6) / 2;
      // arrow: shaft + head
      g.fill(ax, ay - 1, ax + 12, ay + 1, GuideUi.GOLD_DARK);
      for (int i = 0; i < 4; i++) {
         g.fill(ax + 12 + i, ay - 4 + i, ax + 13 + i, ay + 4 - i, GuideUi.GOLD_DARK);
      }
      int rx = ax + 21, ry = ay - (SLOT + 6) / 2;
      slot(g, rx, ry, true);
      g.renderItem(v.result, rx + 4, ry + 4);
      g.renderItemDecorations(GuideUi.font(), v.result, rx + 4, ry + 4);
      if (mx >= rx && mx < rx + SLOT + 6 && my >= ry && my < ry + SLOT + 6) {
         hover = v.result;
      }
      FrontierUi.text(g, FrontierUi.fit(GuideUi.tr(v.station), cw - 10, FrontierUi.Size.SMALL), x + 6, y + ch - 11, GuideUi.MUTED, FrontierUi.Size.SMALL);
      return hover;
   }
}
