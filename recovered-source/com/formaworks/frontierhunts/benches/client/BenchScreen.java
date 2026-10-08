package com.formaworks.frontierhunts.benches.client;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.benches.Bench;
import com.formaworks.frontierhunts.benches.BenchCatalog;
import com.formaworks.frontierhunts.benches.BenchCrafting;
import com.formaworks.frontierhunts.benches.BenchMenu;
import com.formaworks.frontierhunts.benches.BenchTab;
import com.formaworks.frontierhunts.client.FrontierUi;
import com.formaworks.frontierhunts.recipes.ClothingTable;
import com.formaworks.frontierhunts.workshop.AttachmentFitting;
import com.formaworks.frontierhunts.workshop.EquipmentCatalog;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * [benches] The one screen of the three benches: a header band in the bench's colour (emblem, name, what it makes, the
 * search box and the "Can craft" filter), a tab rail on the left (icon, name, craftable / total), a grid of item tiles
 * grouped by section (a green dot when you can make it now, dimmed when materials are missing) and a paper detail card
 * on the right (big icon, what the item does, every material with have / need, the status and the Craft button). The
 * Gunsmith's Fit tab and the Reloading Bench's Refit tab are drawn by {@link BenchFitPanel} and {@link BenchRefitPanel}.
 *
 * <p>Laid out for the 640 x 360 GUI pixels {@code FrontierGuiScale} guarantees (the panel is at most 624 x 344, centred).
 * Lists, availability and the detail card are recomputed only when something changes (tab, search, filter, selection,
 * the inventory); a frame only draws, in a handful of batches ({@link FrontierUi#batch}). Keyboard: typing searches,
 * arrows move the selection, Enter crafts (Shift+Enter five at the Reloading Bench), Tab / Shift+Tab change tabs, Esc
 * closes. Respects {@code reducedMotion}. Mirrored by {@code tools/benches/mock_bench.py}.
 */
public final class BenchScreen extends AbstractContainerScreen<BenchMenu> {
   // ---------------------------------------------------------------- layout (GUI pixels; mirrored by mock_bench.py)
   static final int PANEL_MAX_W = 624, PANEL_MAX_H = 344, HEAD_H = 44, FOOT_H = 22, PAD = 8, RAIL_W = 122, DETAIL_W = 198;
   static final int TAB_H = 28, TAB_GAP = 3, COLS = 5, TILE_W = 48, TILE_H = 56, TILE_GAP = 4, SECTION_H = 14, GRID_TOP = 26, MAT_ROW = 18;
   static final int SEARCH_W = 150, FILTER_W = 72, CLOSE_W = 18;
   private static final int MAX_QUERY = 32;
   /** client-side pause after a click: the server takes one craft per 8 ticks */
   private static final long BUSY_MS = 420L;
   private static final float ICON_SCALE = 1.5F;

   // hot-zone kinds
   static final int H_TAB = 1, H_CLEAR = 2, H_FILTER = 3, H_CLOSE = 4, H_CRAFT = 5, H_CRAFT5 = 6, H_FIT_HELD = 7, H_SEARCH = 8;

   /** A clickable area that is not part of a scrolling list. */
   record Hot(float x, float y, float w, float h, int kind, int data) {
      boolean in(double mx, double my) {
         return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
      }
   }

   /** One row of the grid: a section header, or up to {@link #COLS} tiles ({@code from}..{@code to} in {@link #shown}). */
   record Row(int y, int h, String header, int from, int to) {
   }

   final Bench bench;
   final List<BenchCatalog.Entry> entries;
   private final String[] names;
   private final String[] lower;
   private final String[][] tileLines;
   /** [smalls] per entry: the head an arrow recipe's arrows carry (tile badge, and the tile names the head), else EMPTY */
   private final ItemStack[] heads;
   private final String[] tabNames;
   private final String[] tabHints;
   final boolean[] ready;
   final boolean[] ready5;
   private final float[] hover;
   private final int[] selByTab = new int[BenchTab.values().length];
   private final int[] tabTotal = new int[BenchTab.values().length];
   private final int[] tabReady = new int[BenchTab.values().length];
   private final String[] tabCounts = new String[BenchTab.values().length];
   private final List<BenchTab> tabs = new ArrayList<>();
   BenchTab tab;
   private String query = "";
   private boolean canOnly;
   private int[] shown = new int[0];
   private final List<Row> rows = new ArrayList<>();
   private int contentH;
   private float scroll, scrollTo;
   int selected = -1;
   private int invPrint = Integer.MIN_VALUE;
   private boolean creative;
   final List<Hot> hots = new ArrayList<>();
   // detail card (recomputed when the selection or the inventory changes)
   private int detailOf = -2;
   private final List<String> desc = new ArrayList<>();
   private String[] matNames = new String[0];
   private int[] matHave = new int[0];
   /** [smalls] material lines the craft makes from other materials first (a fitted arrow's heads) */
   private boolean[] matMake = new boolean[0];
   /** [smalls] the card's hint lines over the status (where else this kind of thing is made, how heads are fitted) */
   private final List<String> hint = new ArrayList<>();
   private String status = "";
   private int statusColor = BenchDraw.INK_MUTED;
   private boolean statusOk;
   private boolean canCraft, canCraft5;
   private String fitPart;
   private boolean fitCan;
   private String fitLabel = "";
   private int matScroll;
   private String metaLine = "";
   private List<FormattedCharSequence> nameLines = List.of();
   private String makesLine;
   private String variantLine;
   // feedback
   private long busyUntil, pulseAt;
   private int pulseEntry = -1;
   private long openedAt, lastFrame;
   // geometry
   int L, T, W, H, bodyY, bodyH, railX, gridX, gridW, detX, wideW, footY;
   final BenchFitPanel fit;
   final BenchRefitPanel refit;

   public BenchScreen(BenchMenu menu, Inventory inv, Component title) {
      super(menu, inv, title);
      this.bench = menu.bench;
      this.entries = menu.entries();
      int n = this.entries.size();
      this.names = new String[n];
      this.lower = new String[n];
      this.tileLines = new String[n][];
      this.heads = new ItemStack[n];
      this.ready = new boolean[n];
      this.ready5 = new boolean[n];
      this.hover = new float[n];
      Arrays.fill(this.selByTab, -1);
      for (BenchCatalog.Entry e : this.entries) {
         this.names[e.index] = e.display.getHoverName().getString();
         this.heads[e.index] = e.tab() == BenchTab.ARROWS && com.formaworks.frontierhunts.hunting.ArrowTip.arrow(e.display)
            ? com.formaworks.frontierhunts.hunting.ArrowTip.of(e.display).tipItem(1) : ItemStack.EMPTY;
         this.lower[e.index] = this.names[e.index].toLowerCase(Locale.ROOT);
         this.tabTotal[e.tab().ordinal()]++;
      }
      this.tabNames = new String[BenchTab.values().length];
      this.tabHints = new String[BenchTab.values().length];
      for (BenchTab t : BenchTab.values()) {
         this.tabNames[t.ordinal()] = I18n.get(t.langKey());
         this.tabHints[t.ordinal()] = I18n.get(t.hintKey());
      }
      for (BenchTab t : BenchTab.of(this.bench)) {
         if (t != BenchTab.MISC || this.tabTotal[t.ordinal()] > 0) {
            this.tabs.add(t);
         }
      }
      this.tab = this.tabs.isEmpty() ? BenchTab.of(this.bench).getFirst() : this.tabs.getFirst();
      this.fit = new BenchFitPanel(this);
      this.refit = new BenchRefitPanel(this);
   }

   // ============================================================================================ helpers

   static String tr(String key, Object... args) {
      return I18n.get(key, args);
   }

   boolean reducedMotion() {
      try {
         return HuntConfig.REDUCED_MOTION.get();
      } catch (RuntimeException e) {
         return false;
      }
   }

   void click() {
      this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.35F));
   }

   /** Sends a bench action to the server (the server validates and rate-limits it). */
   boolean send(int id) {
      long now = System.currentTimeMillis();
      if (id < 0 || this.minecraft.gameMode == null || now < this.busyUntil) {
         return false;
      }
      this.busyUntil = now + BUSY_MS;
      this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
      return true;
   }

   net.minecraft.client.gui.Font uiFont() {
      return this.font;
   }

   boolean busy() {
      return System.currentTimeMillis() < this.busyUntil;
   }

   List<ItemStack> inv() {
      return this.minecraft.player.getInventory().items;
   }

   static boolean in(double mx, double my, float x, float y, float w, float h) {
      return mx >= x && mx < x + w && my >= y && my < y + h;
   }

   static void icon(GuiGraphics g, ItemStack st, float x, float y, float scale) {
      if (st.isEmpty()) {
         return;
      }
      g.pose().pushPose();
      g.pose().translate(x, y, 0.0F);
      g.pose().scale(scale, scale, 1.0F);
      g.renderItem(st, 0, 0);
      g.pose().popPose();
   }

   /** The name a material line shows: the item, or "any planks" for a tag. */
   static String ingredientName(Ingredient ing, ItemStack shown) {
      Ingredient.Value[] vs = ing.getValues();
      if (vs.length == 1 && vs[0] instanceof Ingredient.TagValue tv && ing.getItems().length > 1) {
         TagKey<Item> tag = tv.tag();
         String path = tag.location().getPath();
         int slash = path.lastIndexOf('/');
         String pretty = (slash >= 0 ? path.substring(slash + 1) : path).replace('_', ' ');
         return tr("bench.frontierhunts.ui.any_of", pretty);
      }
      return shown.getHoverName().getString();
   }

   /** What an ingredient shows: an item you carry if any matches, otherwise its choices in turn (once a second). */
   ItemStack shown(Ingredient ing) {
      ItemStack[] items = ing.getItems();
      if (items.length == 0) {
         return ItemStack.EMPTY;
      }
      for (ItemStack c : items) {
         for (ItemStack s : this.inv()) {
            if (!s.isEmpty() && s.is(c.getItem())) {
               return c;
            }
         }
      }
      return items[(int)(System.currentTimeMillis() / 1000L % items.length)];
   }

   // ============================================================================================ init / layout

   @Override
   protected void init() {
      this.W = Math.min(PANEL_MAX_W, this.width - 16);
      this.H = Math.min(PANEL_MAX_H, this.height - 16);
      this.imageWidth = this.W;
      this.imageHeight = this.H;
      super.init();
      this.L = (this.width - this.W) / 2;
      this.T = (this.height - this.H) / 2;
      this.bodyY = this.T + HEAD_H + PAD;
      this.bodyH = this.H - HEAD_H - PAD - FOOT_H;
      this.railX = this.L + PAD;
      this.gridX = this.railX + RAIL_W + PAD;
      this.gridW = this.W - PAD * 4 - RAIL_W - DETAIL_W;
      this.detX = this.L + this.W - PAD - DETAIL_W;
      this.wideW = this.L + this.W - PAD - this.gridX;
      this.footY = this.T + this.H - FOOT_H;
      if (this.openedAt == 0L) {
         this.openedAt = System.currentTimeMillis();
      }
      for (BenchCatalog.Entry e : this.entries) {
         // [smalls] arrow tiles sit under their shaft's section header: the tile names the head ("Field Point")
         this.tileLines[e.index] = tileLines(this.heads[e.index].isEmpty() ? this.names[e.index] : this.heads[e.index].getHoverName().getString());
      }
      this.invPrint = Integer.MIN_VALUE;
      this.refreshInventory();
      this.refilter(false);
   }

   private static String[] tileLines(String name) {
      int w = TILE_W - 6;
      List<String> words = new ArrayList<>(Arrays.asList(name.split(" ")));
      StringBuilder a = new StringBuilder();
      while (!words.isEmpty()) {
         String t = a.isEmpty() ? words.getFirst() : a + " " + words.getFirst();
         if (FrontierUi.width(t, FrontierUi.Size.SMALL) > w && !a.isEmpty()) {
            break;
         }
         a.setLength(0);
         a.append(t);
         words.removeFirst();
      }
      if (words.isEmpty()) {
         return new String[]{FrontierUi.fit(a.toString(), w, FrontierUi.Size.SMALL)};
      }
      return new String[]{FrontierUi.fit(a.toString(), w, FrontierUi.Size.SMALL), FrontierUi.fit(String.join(" ", words), w, FrontierUi.Size.SMALL)};
   }

   /** The inventory changed (or the screen opened): what can be made now, the counts, the detail card, the panels. */
   void refreshInventory() {
      Inventory inv = this.minecraft.player.getInventory();
      int print = BenchMenu.fingerprint(inv) * 31 + (this.minecraft.player.hasInfiniteMaterials() ? 1 : 0);
      if (print == this.invPrint) {
         return;
      }
      this.invPrint = print;
      this.creative = this.minecraft.player.hasInfiniteMaterials();
      List<ItemStack> items = BenchCrafting.copy(inv);
      Arrays.fill(this.tabReady, 0);
      for (BenchCatalog.Entry e : this.entries) {
         boolean r = this.creative || (e.fitTip != null || quick(items, e)) && BenchCrafting.craft(this.minecraft.player, e, items) != null; // [smalls]
         this.ready[e.index] = r;
         this.ready5[e.index] = r && this.bench.batches() && (this.creative || BenchCrafting.craft(this.minecraft.player, e, items, 5) != null);
         if (r) {
            this.tabReady[e.tab().ordinal()]++;
         }
      }
      this.detailOf = -2;
      this.fit.refresh();
      this.refit.refresh();
      for (BenchTab t : BenchTab.values()) {
         this.tabCounts[t.ordinal()] = switch (t.kind) {
            case RECIPES -> this.tabReady[t.ordinal()] + "/" + this.tabTotal[t.ordinal()];
            case FIT -> this.fit.badge();
            case REFIT -> this.refit.badge();
         };
      }
      if (this.canOnly) {
         this.refilter(true);
      }
   }

   /** Cheap first test before a full plan: every material line has enough matching items. */
   private static boolean quick(List<ItemStack> items, BenchCatalog.Entry e) {
      for (BenchCatalog.Material m : e.materials) {
         if (BenchCrafting.have(items, m.ingredient()) < m.count()) {
            return false;
         }
      }
      return !e.ingredients.isEmpty();
   }

   /** Which tiles show (tab or search, and the filter) and the grid's rows. */
   void refilter(boolean keepScroll) {
      String q = this.query.strip().toLowerCase(Locale.ROOT);
      boolean searching = !q.isEmpty();
      int[] out = new int[this.entries.size()];
      int n = 0;
      for (BenchCatalog.Entry e : this.entries) {
         if (searching ? e.tab().recipes() && this.lower[e.index].contains(q) : e.tab() == this.tab) {
            if (!this.canOnly || this.ready[e.index]) {
               out[n++] = e.index;
            }
         }
      }
      this.shown = Arrays.copyOf(out, n);
      // rows: a header before each group (section, or tab while searching) unless the tab has a single group
      this.rows.clear();
      int y = 0;
      int groups = 0;
      String last = null;
      for (int i = 0; i < n; i++) {
         String key = this.groupKey(this.shown[i], searching);
         if (!key.equals(last)) {
            groups++;
            last = key;
         }
      }
      boolean headers = groups > 1 || searching;
      int i = 0;
      while (i < n) {
         String key = this.groupKey(this.shown[i], searching);
         int j = i;
         while (j < n && this.groupKey(this.shown[j], searching).equals(key)) {
            j++;
         }
         if (headers) {
            BenchCatalog.Entry e = this.entries.get(this.shown[i]);
            String label = searching ? this.tabNames[e.tab().ordinal()] : tr("bench.frontierhunts.section." + e.place.section());
            this.rows.add(new Row(y, SECTION_H, label.toUpperCase(Locale.ROOT), -1, -1));
            y += SECTION_H;
         }
         for (int k = i; k < j; k += COLS) {
            this.rows.add(new Row(y, TILE_H, null, k, Math.min(j, k + COLS)));
            y += TILE_H + TILE_GAP;
         }
         y += 2;
         i = j;
      }
      this.contentH = y;
      if (!keepScroll) {
         this.scroll = this.scrollTo = 0.0F;
      }
      this.clampScroll();
      // keep the selection when it is still shown, else the tab's remembered one, else the first you can make
      if (n > 0 && indexOf(this.shown, this.selected) < 0) {
         int remembered = searching ? -1 : this.selByTab[this.tab.ordinal()];
         int pick = indexOf(this.shown, remembered) >= 0 ? remembered : -1;
         for (int k = 0; pick < 0 && k < n; k++) {
            if (this.ready[this.shown[k]]) {
               pick = this.shown[k];
            }
         }
         this.select(pick < 0 ? this.shown[0] : pick, false);
      } else if (n == 0 && this.tab.recipes()) {
         this.selected = -1;
      }
      this.layoutHots();
   }

   private String groupKey(int index, boolean searching) {
      BenchCatalog.Entry e = this.entries.get(index);
      return searching ? e.tab().key : e.place.section();
   }

   private static int indexOf(int[] a, int v) {
      for (int i = 0; i < a.length; i++) {
         if (a[i] == v) {
            return i;
         }
      }
      return -1;
   }

   void select(int index, boolean reveal) {
      if (index < 0 || index >= this.entries.size()) {
         return;
      }
      this.selected = index;
      this.matScroll = 0;
      if (this.query.isBlank()) {
         this.selByTab[this.tab.ordinal()] = index;
      }
      this.detailOf = -2;
      if (reveal) {
         this.reveal(index);
      }
      this.layoutHots();
   }

   /** Scrolls so that the tile of {@code index} is in view. */
   private void reveal(int index) {
      int at = indexOf(this.shown, index);
      for (Row r : this.rows) {
         if (r.header == null && at >= r.from && at < r.to) {
            float top = r.y - 4;
            float bottom = r.y + TILE_H + 4;
            float view = this.gridViewH();
            if (top < this.scrollTo) {
               this.scrollTo = top;
            } else if (bottom > this.scrollTo + view) {
               this.scrollTo = bottom - view;
            }
            this.clampScroll();
            if (this.reducedMotion()) {
               this.scroll = this.scrollTo;
            }
            return;
         }
      }
   }

   private float gridViewH() {
      return this.bodyH - GRID_TOP - 4;
   }

   private void clampScroll() {
      float max = Math.max(0.0F, this.contentH - this.gridViewH());
      this.scrollTo = Math.max(0.0F, Math.min(max, this.scrollTo));
      this.scroll = Math.max(0.0F, Math.min(max, this.scroll));
   }

   void setTab(BenchTab t) {
      if (t == this.tab && this.query.isEmpty()) {
         return;
      }
      this.tab = t;
      this.query = "";
      this.selected = -1;
      this.refilter(false);
      if (t.kind == BenchTab.Kind.FIT) {
         this.fit.refresh();
      } else if (t.kind == BenchTab.Kind.REFIT) {
         this.refit.refresh();
      }
   }

   /** Jumps to the recipe that makes {@code item} (from the Fit tab's "make one"). */
   void showRecipe(Item item) {
      for (BenchCatalog.Entry e : this.entries) {
         if (e.product.is(item) && e.tab().recipes()) {
            this.tab = e.tab();
            this.query = "";
            this.canOnly = false;
            this.selected = -1;
            this.refilter(false);
            this.select(e.index, true);
            return;
         }
      }
   }

   /** The static clickable areas (header, rail, detail buttons). The scrolling lists are hit-tested from their rows. */
   void layoutHots() {
      this.hots.clear();
      int cx = this.L + this.W - PAD - CLOSE_W;
      float hy = this.T + (HEAD_H - 18) / 2.0F;
      this.hots.add(new Hot(cx, hy, CLOSE_W, 18, H_CLOSE, 0));
      if (this.tab.recipes()) {
         int fx = cx - 6 - FILTER_W;
         int sx = fx - 6 - SEARCH_W;
         this.hots.add(new Hot(fx, hy, FILTER_W, 18, H_FILTER, 0));
         if (!this.query.isEmpty()) {
            this.hots.add(new Hot(sx + SEARCH_W - 16, hy, 16, 18, H_CLEAR, 0));
         }
         this.hots.add(new Hot(sx, hy, SEARCH_W, 18, H_SEARCH, 0));
      }
      float ty = this.bodyY + 5;
      float th = this.tabH();
      for (BenchTab t : this.tabs) {
         this.hots.add(new Hot(this.railX + 5, ty, RAIL_W - 10, th, H_TAB, t.ordinal()));
         ty += th + TAB_GAP;
      }
      if (this.tab.recipes() && this.selected >= 0) {
         float by = this.bodyY + this.bodyH - 10 - 22;
         float x = this.detX + 10, w = DETAIL_W - 20;
         if (this.bench.batches()) {
            float bw = (w - 6) * 0.62F;
            this.hots.add(new Hot(x, by, bw, 22, H_CRAFT, 0));
            this.hots.add(new Hot(x + bw + 6, by, w - bw - 6, 22, H_CRAFT5, 0));
         } else {
            this.hots.add(new Hot(x, by, w, 22, H_CRAFT, 0));
         }
         this.hots.add(new Hot(x, by - 24, w, 18, H_FIT_HELD, 0));
      }
      if (this.tab.kind == BenchTab.Kind.FIT) {
         this.fit.layout();
      } else if (this.tab.kind == BenchTab.Kind.REFIT) {
         this.refit.layout();
      }
   }

   float tabH() {
      int n = Math.max(1, this.tabs.size());
      return Math.min(TAB_H, (this.bodyH - 10 - (n - 1) * TAB_GAP) / (float)n);
   }

   // ============================================================================================ detail card data

   private void ensureDetail() {
      if (this.detailOf == this.selected) {
         return;
      }
      this.detailOf = this.selected;
      this.desc.clear();
      this.fitPart = null;
      if (this.selected < 0) {
         return;
      }
      BenchCatalog.Entry e = this.entries.get(this.selected);
      this.nameLines = this.font.split(FrontierUi.c(this.names[e.index], FrontierUi.Size.STRONG), DETAIL_W - 68);
      // what the item does: its own tooltip, without the name line
      try {
         List<Component> lines = e.display.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(this.minecraft.level), this.minecraft.player,
            TooltipFlag.NORMAL);
         int budget = 4;
         for (int i = 1; i < lines.size() && budget > 0; i++) {
            String s = lines.get(i).getString().strip();
            if (s.isEmpty()) {
               continue;
            }
            for (FormattedCharSequence seq : this.font.split(FrontierUi.c(s, FrontierUi.Size.SMALL), DETAIL_W - 20)) {
               if (budget-- <= 0) {
                  break;
               }
               StringBuilder b = new StringBuilder();
               seq.accept((idx, style, cp) -> {
                  b.appendCodePoint(cp);
                  return true;
               });
               this.desc.add(b.toString());
            }
         }
      } catch (RuntimeException ignored) {
         // an item's tooltip code failed: the card still works without it
      }
      // materials: what you have of each
      List<ItemStack> items = this.inv();
      int m = e.materials.size();
      this.matNames = new String[m];
      this.matHave = new int[m];
      this.matMake = new boolean[m];
      int missing = 0;
      int toMake = this.ready[e.index] && !this.creative ? BenchCrafting.headsToMake(items, e) : 0; // [smalls]
      for (int i = 0; i < m; i++) {
         BenchCatalog.Material mat = e.materials.get(i);
         this.matHave[i] = BenchCrafting.have(items, mat.ingredient());
         this.matNames[i] = ingredientName(mat.ingredient(), this.shown(mat.ingredient()));
         if (this.matHave[i] < mat.count()) {
            missing++;
            // [smalls] a fitted arrow's heads: made from your materials when you craft
            this.matMake[i] = toMake > 0 && e.fitTip != null && mat.ingredient().test(e.fitTip.tipItem(1));
         }
      }
      this.hint.clear();
      String hintText = e.table ? tr("bench.frontierhunts.ui.also_table") : this.hintFor(e);
      if (hintText != null) {
         for (FormattedCharSequence seq : this.font.split(FrontierUi.c(hintText, FrontierUi.Size.SMALL), DETAIL_W - 20)) {
            if (this.hint.size() < 2) {
               StringBuilder b = new StringBuilder();
               seq.accept((idx, style, cp) -> {
                  b.appendCodePoint(cp);
                  return true;
               });
               this.hint.add(b.toString());
            }
         }
      }
      this.canCraft = this.ready[e.index];
      this.canCraft5 = this.ready5[e.index];
      this.metaLine = this.tabNames[e.tab().ordinal()] + " · " + tr("bench.frontierhunts.section." + e.place.section());
      this.makesLine = e.product.getCount() > 1 ? tr("bench.frontierhunts.ui.makes", e.product.getCount()) : null;
      this.variantLine = e.variants > 1 ? tr("bench.frontierhunts.ui.variant", e.variant, e.variants) : null;
      // status line
      ItemStack held = this.minecraft.player.getMainHandItem();
      if (e.sewing()) {
         if (held.isEmpty() || Equipable.get(held) == null) {
            this.setStatus(tr("bench.frontierhunts.ui.sew_hold"), false);
         } else if (e.sewBit == 2 && Equipable.get(held).getEquipmentSlot() != EquipmentSlot.CHEST) {
            this.setStatus(tr("bench.frontierhunts.ui.sew_mittens_chest"), false);
         } else if (!ClothingTable.canSew(held, e.sewBit)) {
            this.setStatus(tr("bench.frontierhunts.ui.sew_done", held.getHoverName().getString()), false);
         } else if (missing > 0) {
            this.setStatus(missing == 1 ? tr("bench.frontierhunts.ui.missing_one") : tr("bench.frontierhunts.ui.missing", missing), false);
         } else {
            this.setStatus(tr("bench.frontierhunts.ui.sew_target", held.getHoverName().getString()), true);
         }
      } else if (this.creative) {
         this.setStatus(tr("bench.frontierhunts.ui.creative"), true);
      } else if (this.canCraft && toMake > 0) {
         this.setStatus(tr("bench.frontierhunts.ui.ready_heads", toMake), true); // [smalls]
      } else if (this.canCraft) {
         this.setStatus(this.canCraft5 ? tr("bench.frontierhunts.ui.ready5") : tr("bench.frontierhunts.ui.ready"), true);
      } else if (missing > 0) {
         this.setStatus(missing == 1 ? tr("bench.frontierhunts.ui.missing_one") : tr("bench.frontierhunts.ui.missing", missing), false);
      } else {
         this.setStatus(tr("bench.frontierhunts.ui.no_room"), false);
      }
      // a part you could fit to the item in your hand right away
      String part = BuiltInRegistries.ITEM.getKey(e.product.getItem()).getPath();
      if (!e.sewing() && EquipmentCatalog.PARTS.contains(part) && this.menu.fits(part) && !held.isEmpty() && AttachmentFitting.supported(held, part)
         && !AttachmentFitting.fitted(held, part)) {
         this.fitPart = part;
         this.fitCan = BenchCrafting.fit(this.minecraft.player.getInventory(), part, true) != null;
         this.fitLabel = tr("bench.frontierhunts.ui.fit_held", held.getHoverName().getString());
      }
   }

   /** [smalls] The card's hint for a tab: where the rest of the food comes from, how heads get onto arrows. */
   private String hintFor(BenchCatalog.Entry e) {
      return switch (e.tab()) {
         case FOOD -> tr("bench.frontierhunts.ui.hint.food");
         case ARROWS -> e.fitTip != null ? tr("bench.frontierhunts.ui.hint.arrow_fitted") : (e.product.is(net.minecraft.world.item.Items.ARROW)
            || com.formaworks.frontierhunts.hunting.ArrowTip.arrow(e.product) ? tr("bench.frontierhunts.ui.hint.arrow_stock") : null);
         case TIPS -> tr("bench.frontierhunts.ui.hint.tips");
         default -> null;
      };
   }

   private void setStatus(String s, boolean ok) {
      this.status = s;
      this.statusOk = ok;
      this.statusColor = ok ? BenchDraw.GREEN_INK : BenchDraw.RED_INK;
   }

   // ============================================================================================ actions

   void craft(boolean five) {
      if (this.selected < 0 || !this.tab.recipes() && this.query.isEmpty()) {
         return;
      }
      BenchCatalog.Entry e = this.entries.get(this.selected);
      boolean can = five ? this.ready5[e.index] : (e.sewing() ? this.statusOk : this.ready[e.index]);
      if (!can) {
         return;
      }
      if (this.send(five ? BenchMenu.BATCH + e.index : e.index)) {
         this.pulseAt = System.currentTimeMillis();
         this.pulseEntry = e.index;
      }
   }

   private void hotClick(Hot h) {
      switch (h.kind) {
         case H_CLOSE -> this.onClose();
         case H_FILTER -> {
            this.canOnly = !this.canOnly;
            this.click();
            this.refilter(false);
         }
         case H_CLEAR -> {
            this.query = "";
            this.click();
            this.refilter(false);
         }
         case H_TAB -> {
            BenchTab t = BenchTab.values()[h.data];
            if (t != this.tab || !this.query.isEmpty()) {
               this.click();
               this.setTab(t);
            }
         }
         case H_CRAFT -> this.craft(false);
         case H_CRAFT5 -> this.craft(true);
         case H_FIT_HELD -> {
            this.ensureDetail();
            if (this.fitPart != null && this.fitCan) {
               this.send(BenchMenu.fitButton(this.fitPart, true));
            }
         }
         default -> {
         }
      }
   }

   @Override
   public boolean mouseClicked(double mx, double my, int button) {
      if (button != 0) {
         return true;
      }
      if (this.tab.kind == BenchTab.Kind.FIT && this.fit.click(mx, my)) {
         return true;
      }
      if (this.tab.kind == BenchTab.Kind.REFIT && this.refit.click(mx, my)) {
         return true;
      }
      for (Hot h : this.hots) {
         if (h.kind < 100 && h.in(mx, my)) {
            if (h.kind == H_FIT_HELD && (this.selected < 0 || this.fitPart == null)) {
               continue;
            }
            if (h.kind == H_CRAFT5 && !this.bench.batches()) {
               continue;
            }
            this.hotClick(h);
            return true;
         }
      }
      if (this.tab.recipes() || !this.query.isEmpty()) {
         int hit = this.tileAt(mx, my);
         if (hit >= 0) {
            if (hit != this.selected) {
               this.click();
               this.select(hit, true);
            }
            return true;
         }
      }
      return true;
   }

   @Override
   public boolean mouseReleased(double mx, double my, int button) {
      return true;
   }

   @Override
   public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
      return true;
   }

   /** The entry index of the tile under the mouse, or -1. */
   private int tileAt(double mx, double my) {
      float top = this.bodyY + GRID_TOP;
      if (!in(mx, my, this.gridX, top, this.gridW, this.gridViewH())) {
         return -1;
      }
      float gx = this.gridX + (this.gridW - (COLS * TILE_W + (COLS - 1) * TILE_GAP)) / 2.0F;
      double cy = my - top + this.scroll;
      for (Row r : this.rows) {
         if (r.header == null && cy >= r.y && cy < r.y + TILE_H) {
            int col = (int)Math.floor((mx - gx) / (TILE_W + TILE_GAP));
            double inX = mx - gx - col * (TILE_W + TILE_GAP);
            if (col >= 0 && col < r.to - r.from && inX < TILE_W) {
               return this.shown[r.from + col];
            }
         }
      }
      return -1;
   }

   @Override
   public boolean mouseScrolled(double mx, double my, double sx, double sy) {
      if (this.tab.kind == BenchTab.Kind.FIT) {
         return this.fit.scrolled(mx, my, sy);
      }
      if (this.tab.kind == BenchTab.Kind.REFIT && this.query.isEmpty()) {
         return this.refit.scrolled(mx, my, sy);
      }
      if (in(mx, my, this.detX, this.bodyY, DETAIL_W, this.bodyH)) {
         this.matScroll = Math.max(0, this.matScroll - (int)Math.signum(sy));
         return true;
      }
      if (in(mx, my, this.gridX, this.bodyY, this.gridW, this.bodyH)) {
         this.scrollTo -= (float)sy * (TILE_H + TILE_GAP) * 0.75F;
         this.clampScroll();
         if (this.reducedMotion()) {
            this.scroll = this.scrollTo;
         }
         return true;
      }
      if (in(mx, my, this.railX, this.bodyY, RAIL_W, this.bodyH)) {
         int i = this.tabs.indexOf(this.tab);
         int next = Math.max(0, Math.min(this.tabs.size() - 1, i - (int)Math.signum(sy)));
         if (next != i) {
            this.click();
            this.setTab(this.tabs.get(next));
         }
         return true;
      }
      return true;
   }

   @Override
   public boolean keyPressed(int key, int scan, int mods) {
      if (key == InputConstants.KEY_ESCAPE) {
         this.onClose();
         return true;
      }
      boolean shift = Screen.hasShiftDown();
      switch (key) {
         case InputConstants.KEY_TAB -> {
            int i = this.tabs.indexOf(this.tab);
            int n = this.tabs.size();
            this.click();
            this.setTab(this.tabs.get(Math.floorMod(i + (shift ? -1 : 1), n)));
            return true;
         }
         case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
            if (this.tab.kind == BenchTab.Kind.REFIT && this.query.isEmpty()) {
               this.refit.primary();
            } else {
               this.craft(shift && this.bench.batches());
            }
            return true;
         }
         case InputConstants.KEY_LEFT, InputConstants.KEY_RIGHT, InputConstants.KEY_UP, InputConstants.KEY_DOWN -> {
            if (this.tab.kind == BenchTab.Kind.REFIT && this.query.isEmpty()) {
               this.refit.arrowKey(key);
            } else {
               this.move(key);
            }
            return true;
         }
         case InputConstants.KEY_PAGEUP, InputConstants.KEY_PAGEDOWN -> {
            this.scrollTo += (key == InputConstants.KEY_PAGEDOWN ? 1 : -1) * this.gridViewH() * 0.85F;
            this.clampScroll();
            return true;
         }
         case InputConstants.KEY_BACKSPACE -> {
            if (!this.query.isEmpty() && this.searchable()) {
               if (Screen.hasControlDown()) {
                  String q = this.query.stripTrailing();
                  int sp = q.lastIndexOf(' ');
                  this.query = sp < 0 ? "" : q.substring(0, sp + 1);
               } else {
                  this.query = this.query.substring(0, this.query.length() - 1);
               }
               this.refilter(false);
            }
            return true;
         }
         case InputConstants.KEY_DELETE -> {
            if (!this.query.isEmpty()) {
               this.query = "";
               this.refilter(false);
            }
            return true;
         }
         default -> {
         }
      }
      if (Screen.isPaste(key) && this.searchable()) {
         this.type(this.minecraft.keyboardHandler.getClipboard());
         return true;
      }
      if (Screen.isSelectAll(key)) {
         return true;
      }
      // letters, digits and punctuation are typed into the search (charTyped); they must not close the screen
      if (key >= 32 && key < 256 && !Screen.hasControlDown() && !Screen.hasAltDown()) {
         return true;
      }
      return false;
   }

   private boolean searchable() {
      return this.tab.recipes() || !this.query.isEmpty();
   }

   @Override
   public boolean charTyped(char c, int mods) {
      if (c >= ' ' && c != 127 && this.searchable()) {
         this.type(String.valueOf(c));
      }
      return true;
   }

   private void type(String s) {
      if (s == null || s.isEmpty()) {
         return;
      }
      StringBuilder b = new StringBuilder(this.query);
      for (int i = 0; i < s.length() && b.length() < MAX_QUERY; i++) {
         char c = s.charAt(i);
         if (c >= ' ' && c != 127) {
            b.append(c);
         }
      }
      if (!b.toString().equals(this.query)) {
         this.query = b.toString();
         this.refilter(false);
      }
   }

   /** Arrow keys in the grid. */
   private void move(int key) {
      if (this.shown.length == 0) {
         return;
      }
      int at = indexOf(this.shown, this.selected);
      if (at < 0) {
         this.select(this.shown[0], true);
         return;
      }
      int target = at;
      if (key == InputConstants.KEY_LEFT) {
         target = Math.max(0, at - 1);
      } else if (key == InputConstants.KEY_RIGHT) {
         target = Math.min(this.shown.length - 1, at + 1);
      } else {
         int ri = -1;
         for (int i = 0; i < this.rows.size(); i++) {
            Row r = this.rows.get(i);
            if (r.header == null && at >= r.from && at < r.to) {
               ri = i;
            }
         }
         if (ri < 0) {
            return;
         }
         int col = at - this.rows.get(ri).from;
         int step = key == InputConstants.KEY_UP ? -1 : 1;
         for (int i = ri + step; i >= 0 && i < this.rows.size(); i += step) {
            Row r = this.rows.get(i);
            if (r.header == null) {
               target = Math.min(r.to - 1, r.from + col);
               break;
            }
         }
      }
      if (target != at) {
         this.select(this.shown[target], true);
      }
   }

   @Override
   protected void containerTick() {
      super.containerTick();
      this.refreshInventory();
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   // ============================================================================================ drawing

   @Override
   protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
   }

   @Override
   protected void renderLabels(GuiGraphics g, int mx, int my) {
   }

   @Override
   public void render(GuiGraphics g, int mx, int my, float pt) {
      long now = System.currentTimeMillis();
      float dt = this.lastFrame == 0L ? 0.016F : Math.min(0.1F, (now - this.lastFrame) / 1000.0F);
      this.lastFrame = now;
      boolean still = this.reducedMotion();
      this.renderTransparentBackground(g);
      float intro = still ? 1.0F : Math.min(1.0F, (now - this.openedAt) / 180.0F);
      float lift = (1.0F - intro) * (1.0F - intro) * 8.0F;
      g.pose().pushPose();
      g.pose().translate(0.0F, lift, 0.0F);
      int my2 = (int)(my - lift);
      // smooth scroll and hover
      this.scroll = still ? this.scrollTo : this.scroll + (this.scrollTo - this.scroll) * Math.min(1.0F, dt * 16.0F);
      if (Math.abs(this.scroll - this.scrollTo) < 0.3F) {
         this.scroll = this.scrollTo;
      }
      int hot = (this.tab.recipes() || !this.query.isEmpty()) ? this.tileAt(mx, my2) : -1;
      for (int i : this.shown) {
         float target = i == hot ? 1.0F : 0.0F;
         this.hover[i] = still ? target : this.hover[i] + (target - this.hover[i]) * Math.min(1.0F, dt * 18.0F);
      }
      this.ensureDetail();
      this.drawFrame(g, mx, my2);
      this.drawRail(g, mx, my2);
      if (this.tab.recipes() || !this.query.isEmpty()) {
         this.drawGrid(g, mx, my2, hot);
         this.drawDetail(g, mx, my2);
      } else if (this.tab.kind == BenchTab.Kind.FIT) {
         this.fit.draw(g, mx, my2);
      } else {
         this.refit.draw(g, mx, my2);
      }
      this.drawFooter(g);
      g.pose().popPose();
      this.drawTooltips(g, mx, my2);
   }

   private void drawFrame(GuiGraphics g, int mx, int my) {
      int l = this.L, t = this.T, w = this.W;
      int accent = this.bench.accent, deep = this.bench.deep;
      FrontierUi.shadow(g, l, t, w, this.H, 8.0F, 9.0F);
      FrontierUi.batch(g, () -> {
         FrontierUi.rect(g, l - 1, t - 1, w + 2, this.H + 2, 8.0F, BenchDraw.OUTER);
         FrontierUi.rect(g, l, t, w, this.H, 7.0F, BenchDraw.PANEL);
         // header: the bench's deep tone, lit from the emblem side, the accent rule under it
         int lit = BenchDraw.mix(deep, accent, 0.20F);
         FrontierUi.rect(g, l, t, w, 10, 7.0F, lit);
         g.fillGradient(l, t + 6, l + w, t + HEAD_H, lit, deep);
         FrontierUi.circle(g, l + 26, t + HEAD_H / 2.0F, 21.0F, BenchDraw.alpha(accent, 0.12F));
         FrontierUi.rect(g, l, t + HEAD_H - 1, w, 2, 0.0F, accent);
         FrontierUi.rect(g, l, t + HEAD_H + 1, w, 3, 0.0F, 0x30000000);
         FrontierUi.circle(g, l + 26, t + HEAD_H / 2.0F, 17.0F, 0x33000000);
         FrontierUi.text(g, tr(this.bench.nameKey()), l + 50, t + 9, BenchDraw.TEXT, FrontierUi.Size.TITLE);
         FrontierUi.text(g, tr("bench.frontierhunts." + this.bench.id + ".subtitle"), l + 50, t + 27, BenchDraw.mix(BenchDraw.TEXT, accent, 0.35F),
            FrontierUi.Size.SMALL);
         for (Hot h : this.hots) {
            boolean over = h.in(mx, my);
            switch (h.kind) {
               case H_CLOSE -> {
                  FrontierUi.rect(g, h.x, h.y, h.w, h.h, 4.0F, over ? BenchDraw.alpha(accent, 0.35F) : 0x26000000);
                  BenchDraw.cross(g, h.x + h.w / 2, h.y + h.h / 2, 3.0F, BenchDraw.TEXT);
               }
               case H_SEARCH -> this.drawSearch(g, h, over);
               case H_FILTER -> {
                  boolean on = this.canOnly;
                  FrontierUi.rect(g, h.x, h.y, h.w, h.h, 9.0F, on ? BenchDraw.alpha(accent, 0.9F) : (over ? 0x55FFFFFF : 0x30FFFFFF));
                  if (!on) {
                     FrontierUi.rect(g, h.x + 1, h.y + 1, h.w - 2, h.h - 2, 8.0F, over ? 0xFF2A241D : 0xFF201B16);
                  }
                  float kx = h.x + 6;
                  FrontierUi.circle(g, kx + 5, h.y + 9, 5.0F, on ? 0xFF1A140E : 0x50FFFFFF);
                  if (on) {
                     BenchDraw.check(g, kx + 5, h.y + 8.5F, accent, 0.85F);
                  }
                  FrontierUi.text(g, tr("bench.frontierhunts.ui.can_craft"), kx + 13, h.y + 4.5F, on ? 0xFF1A140E : BenchDraw.MUTED, FrontierUi.Size.STRONG);
               }
               default -> {
               }
            }
         }
      });
      // the emblem (a blit: outside the batch)
      RenderSystem.enableBlend();
      g.blit(ResourceLocation.fromNamespaceAndPath("frontierhunts", "textures/gui/bench/" + this.bench.id + "_emblem.png"), l + 9,
         (int)(t + HEAD_H / 2.0F - 17), 34, 34, 0.0F, 0.0F, 64, 64, 64, 64);
      RenderSystem.disableBlend();
   }

   private void drawSearch(GuiGraphics g, Hot h, boolean over) {
      boolean typed = !this.query.isEmpty();
      FrontierUi.rect(g, h.x, h.y, h.w, h.h, 9.0F, typed ? BenchDraw.alpha(this.bench.accent, 0.9F) : (over ? 0x55FFFFFF : 0x30FFFFFF));
      FrontierUi.rect(g, h.x + 1, h.y + 1, h.w - 2, h.h - 2, 8.0F, 0xFF1A1612);
      BenchDraw.magnifier(g, h.x + 11, h.y + 8.5F, typed ? BenchDraw.TEXT : BenchDraw.MUTED);
      if (typed) {
         String q = FrontierUi.fit(this.query, (int)h.w - 40, FrontierUi.Size.BODY);
         float tx = FrontierUi.text(g, q, h.x + 20, h.y + 4.5F, BenchDraw.TEXT, FrontierUi.Size.BODY);
         boolean blink = this.reducedMotion() || System.currentTimeMillis() / 530L % 2L == 0L;
         if (blink) {
            FrontierUi.rect(g, tx + 1, h.y + 4, 0.8F, 10, 0.0F, BenchDraw.TEXT);
         }
         BenchDraw.cross(g, h.x + h.w - 9, h.y + 9, 2.5F, BenchDraw.MUTED);
      } else {
         FrontierUi.text(g, tr("bench.frontierhunts.ui.search"), h.x + 20, h.y + 4.5F, BenchDraw.DIM, FrontierUi.Size.BODY);
      }
   }

   private String tabCount(BenchTab t) {
      return this.tabCounts[t.ordinal()];
   }

   private void drawRail(GuiGraphics g, int mx, int my) {
      int x = this.railX, y = this.bodyY, w = RAIL_W, h = this.bodyH;
      int accent = this.bench.accent;
      float th = this.tabH();
      boolean searching = !this.query.isEmpty();
      FrontierUi.batch(g, () -> {
         BenchDraw.framed(g, x, y, w, h, 6.0F, BenchDraw.INSET_EDGE, 1.0F, BenchDraw.INSET);
         for (Hot hz : this.hots) {
            if (hz.kind != H_TAB) {
               continue;
            }
            BenchTab t = BenchTab.values()[hz.data];
            boolean sel = t == this.tab && !searching;
            boolean over = hz.in(mx, my);
            if (sel) {
               FrontierUi.rect(g, hz.x, hz.y, hz.w, hz.h, 5.0F, BenchDraw.mix(BenchDraw.TILE_SEL, accent, 0.10F));
               FrontierUi.rect(g, hz.x, hz.y + 5, 2.5F, hz.h - 10, 1.2F, accent);
            } else if (over) {
               FrontierUi.rect(g, hz.x, hz.y, hz.w, hz.h, 5.0F, 0xFF221E18);
            }
            String count = this.tabCount(t);
            float cw = 0.0F;
            if (count != null && !count.isEmpty()) {
               cw = FrontierUi.width(count, FrontierUi.Size.SMALL) + 8;
               FrontierUi.rect(g, hz.x + hz.w - 5 - cw, hz.y + (th - 10) / 2.0F, cw, 10, 5.0F, sel ? BenchDraw.alpha(accent, 0.22F) : 0x14FFFFFF);
               FrontierUi.text(g, count, hz.x + hz.w - 5 - cw + 4, hz.y + (th - 10) / 2.0F + 1.5F, sel ? BenchDraw.TEXT : BenchDraw.MUTED,
                  FrontierUi.Size.SMALL);
            }
            String label = FrontierUi.fit(this.tabNames[t.ordinal()], (int)(hz.w - 32 - cw - 6), FrontierUi.Size.STRONG);
            FrontierUi.text(g, label, hz.x + 30, hz.y + (th - 9) / 2.0F + 0.5F, sel || over ? BenchDraw.TEXT : BenchDraw.mix(BenchDraw.MUTED, BenchDraw.TEXT, 0.25F),
               FrontierUi.Size.STRONG);
         }
      });
      for (Hot hz : this.hots) {
         if (hz.kind == H_TAB) {
            BenchTab t = BenchTab.values()[hz.data];
            icon(g, RailIcons.get(t), hz.x + 8, hz.y + (th - 16) / 2.0F, 1.0F);
         }
      }
   }

   private void drawGrid(GuiGraphics g, int mx, int my, int hot) {
      int x = this.gridX, y = this.bodyY, w = this.gridW, h = this.bodyH;
      int accent = this.bench.accent;
      boolean searching = !this.query.isEmpty();
      float top = y + GRID_TOP;
      float view = this.gridViewH();
      float gx = x + (w - (COLS * TILE_W + (COLS - 1) * TILE_GAP)) / 2.0F;
      long now = System.currentTimeMillis();
      FrontierUi.batch(g, () -> {
         BenchDraw.framed(g, x, y, w, h, 6.0F, BenchDraw.INSET_EDGE, 1.0F, BenchDraw.INSET);
         String title = searching ? (this.shown.length == 1 ? tr("bench.frontierhunts.ui.result") : tr("bench.frontierhunts.ui.results", this.shown.length))
            : this.tabNames[this.tab.ordinal()];
         String hint = searching ? tr("bench.frontierhunts.ui.results_hint") : this.tabHints[this.tab.ordinal()];
         float tw = FrontierUi.text(g, title, x + 9, y + 7, BenchDraw.TEXT, FrontierUi.Size.STRONG) - (x + 9);
         FrontierUi.text(g, FrontierUi.fit(hint, (int)(w - 18 - tw - 8), FrontierUi.Size.SMALL), x + 9 + tw + 7, y + 8.5F, BenchDraw.DIM,
            FrontierUi.Size.SMALL);
         if (this.shown.length == 0) {
            String a = searching ? tr("bench.frontierhunts.ui.no_results", this.query) : tr("bench.frontierhunts.ui.none_ready");
            String b = searching ? tr("bench.frontierhunts.ui.no_results_hint") : tr("bench.frontierhunts.ui.none_ready_hint");
            int ty = (int)top + 30;
            ty += FrontierUi.wrap(g, a, x + 20, ty, w - 40, BenchDraw.TEXT, FrontierUi.Size.STRONG) + 4;
            FrontierUi.wrap(g, b, x + 20, ty, w - 40, BenchDraw.MUTED, FrontierUi.Size.SMALL);
         }
         // scrollbar
         if (this.contentH > view) {
            float bh = Math.max(20.0F, view * view / this.contentH);
            float by = top + (view - bh) * (this.scroll / Math.max(1.0F, this.contentH - view));
            FrontierUi.rect(g, x + w - 6, top, 3, view, 1.5F, 0x18FFFFFF);
            FrontierUi.rect(g, x + w - 6, by, 3, bh, 1.5F, BenchDraw.alpha(accent, 0.75F));
         }
      });
      if (this.shown.length == 0) {
         return;
      }
      g.enableScissor(x + 1, (int)top - 1, x + w - 7, (int)(top + view));
      // pass 1: section headers, tile plates and names
      FrontierUi.batch(g, () -> {
         for (Row r : this.rows) {
            float ry = top + r.y - this.scroll;
            if (ry + r.h < top - 2 || ry > top + view) {
               continue;
            }
            if (r.header != null) {
               FrontierUi.text(g, r.header, gx + 1, ry + 3, BenchDraw.mix(accent, BenchDraw.TEXT, 0.25F), FrontierUi.Size.SMALL);
               float lw = FrontierUi.width(r.header, FrontierUi.Size.SMALL);
               FrontierUi.rect(g, gx + lw + 7, ry + 6.5F, COLS * TILE_W + (COLS - 1) * TILE_GAP - lw - 8, 1, 0.0F, BenchDraw.RULE_DARK);
               continue;
            }
            for (int k = r.from; k < r.to; k++) {
               int i = this.shown[k];
               float tx = gx + (k - r.from) * (TILE_W + TILE_GAP);
               this.drawTile(g, i, tx, ry, now);
            }
         }
      });
      // pass 2: the item icons
      for (Row r : this.rows) {
         float ry = top + r.y - this.scroll;
         if (r.header != null || ry + r.h < top - 2 || ry > top + view) {
            continue;
         }
         for (int k = r.from; k < r.to; k++) {
            int i = this.shown[k];
            float tx = gx + (k - r.from) * (TILE_W + TILE_GAP);
            icon(g, this.entries.get(i).display, tx + (TILE_W - 24) / 2.0F, ry + 6, ICON_SCALE);
            if (!this.heads[i].isEmpty()) { // [smalls] the head an arrow carries, as a small badge over its icon
               g.pose().pushPose();
               g.pose().translate(0.0F, 0.0F, 60.0F);
               icon(g, this.heads[i], tx + (TILE_W - 30) / 2.0F + 2, ry + 6, 0.625F); // top left of the icon well, clear of the arrow and the count
               g.pose().popPose();
            }
         }
      }
      // pass 3: above the icons: dimming for what you can't make yet, output counts
      g.pose().pushPose();
      g.pose().translate(0.0F, 0.0F, 250.0F);
      FrontierUi.batch(g, () -> {
         for (Row r : this.rows) {
            float ry = top + r.y - this.scroll;
            if (r.header != null || ry + r.h < top - 2 || ry > top + view) {
               continue;
            }
            for (int k = r.from; k < r.to; k++) {
               int i = this.shown[k];
               float tx = gx + (k - r.from) * (TILE_W + TILE_GAP);
               boolean sel = i == this.selected;
               if (!this.ready[i]) {
                  FrontierUi.rect(g, tx + (TILE_W - 30) / 2.0F, ry + 4, 30, 28, 4.0F, BenchDraw.alpha(sel ? BenchDraw.TILE_SEL : BenchDraw.TILE_OFF,
                     sel ? 0.35F : 0.55F - 0.2F * this.hover[i]));
               }
               BenchCatalog.Entry e = this.entries.get(i);
               if (e.product.getCount() > 1) {
                  String s = "×" + e.product.getCount();
                  FrontierUi.right(g, s, tx + TILE_W - 5, ry + 25, this.ready[i] ? BenchDraw.TEXT : BenchDraw.DIM, FrontierUi.Size.SMALL);
               }
               if (e.variants > 1) {
                  BenchDraw.pill(g, Integer.toString(e.variant), tx + 3, ry + 3, 0x40000000, BenchDraw.MUTED);
               }
            }
         }
      });
      g.pose().popPose();
      g.disableScissor();
   }

   private void drawTile(GuiGraphics g, int i, float x, float y, long now) {
      boolean sel = i == this.selected;
      boolean ok = this.ready[i];
      float hv = this.hover[i];
      int base = ok ? BenchDraw.TILE : BenchDraw.TILE_OFF;
      int bg = sel ? BenchDraw.TILE_SEL : BenchDraw.mix(base, BenchDraw.TILE_HOT, hv);
      int accent = this.bench.accent;
      if (sel) {
         FrontierUi.rect(g, x - 1.5F, y - 1.5F, TILE_W + 3, TILE_H + 3, 6.5F, accent);
      } else if (hv > 0.02F) {
         FrontierUi.rect(g, x - 1, y - 1, TILE_W + 2, TILE_H + 2, 6.0F, BenchDraw.alpha(0xFFFFFFFF, 0.16F * hv));
      }
      FrontierUi.rect(g, x, y, TILE_W, TILE_H, 5.0F, bg);
      // a crafted pulse: a short glow on the tile just made
      if (i == this.pulseEntry && now - this.pulseAt < 450L) {
         float p = 1.0F - (now - this.pulseAt) / 450.0F;
         FrontierUi.rect(g, x, y, TILE_W, TILE_H, 5.0F, BenchDraw.alpha(accent, 0.30F * p));
      }
      FrontierUi.rect(g, x + (TILE_W - 30) / 2.0F, y + 4, 30, 28, 4.0F, ok ? 0x22000000 : 0x18000000);
      if (ok) {
         FrontierUi.circle(g, x + TILE_W - 6, y + 6, 2.6F, BenchDraw.READY);
      }
      String[] lines = this.tileLines[i];
      float ly = lines.length == 2 ? y + 35 : y + 39;
      int c = sel ? BenchDraw.TEXT : ok ? BenchDraw.mix(BenchDraw.TEXT, BenchDraw.MUTED, 0.2F - 0.2F * hv) : BenchDraw.mix(BenchDraw.DIM, BenchDraw.TEXT, 0.35F * hv);
      for (String s : lines) {
         FrontierUi.center(g, s, x + TILE_W / 2.0F, ly, c, FrontierUi.Size.SMALL);
         ly += 8;
      }
   }

   private void drawDetail(GuiGraphics g, int mx, int my) {
      int x = this.detX, y = this.bodyY, w = DETAIL_W, h = this.bodyH;
      int accent = this.bench.accent;
      BenchCatalog.Entry e = this.selected < 0 ? null : this.entries.get(this.selected);
      float by = y + h - 10 - 22;
      float statusY = (this.fitPart != null ? by - 24 : by) - 14;
      float matTop = y + 58 + this.desc.size() * 9 + (this.desc.isEmpty() ? 0 : 5);
      float hintY = statusY - 4 - this.hint.size() * 9; // [smalls] hint lines sit over the status line
      int rowsFit = Math.max(1, (int)((hintY - 4 - (matTop + 14)) / (MAT_ROW + 2)));
      int mats = e == null ? 0 : e.materials.size();
      this.matScroll = Math.max(0, Math.min(this.matScroll, mats - rowsFit));
      boolean busy = this.busy();
      FrontierUi.batch(g, () -> {
         BenchDraw.framed(g, x, y, w, h, 6.0F, BenchDraw.PAPER_EDGE, 1.0F, BenchDraw.PAPER);
         g.fillGradient(x + 1, y + 6, x + w - 1, y + 56, BenchDraw.PAPER_TOP, BenchDraw.PAPER);
         FrontierUi.rect(g, x + 1, y + 1, w - 2, 6, 5.0F, BenchDraw.PAPER_TOP);
         if (e == null) {
            FrontierUi.wrap(g, tr("bench.frontierhunts.ui.none_ready_hint"), x + 12, y + 14, w - 24, BenchDraw.INK_MUTED, FrontierUi.Size.SMALL);
            return;
         }
         BenchDraw.framed(g, x + 10, y + 10, 40, 40, 5.0F, 0xFFD9CDB1, 1.0F, BenchDraw.PAPER_WELL);
         // name, then the tab · section, the output count and which recipe this is
         int ny = y + 12;
         for (int i = 0; i < Math.min(2, this.nameLines.size()); i++) {
            g.drawString(this.font, this.nameLines.get(i), x + 58, ny, BenchDraw.INK, false);
            ny += 10;
         }
         float mx0 = x + 58;
         if (this.makesLine != null) {
            mx0 += BenchDraw.pill(g, this.makesLine, mx0, ny + 2, BenchDraw.alpha(accent, 0.28F), BenchDraw.INK) + 4;
         }
         if (this.variantLine != null) {
            mx0 += BenchDraw.pill(g, this.variantLine, mx0, ny + 2, 0x1E000000, BenchDraw.INK) + 4;
         }
         if (this.makesLine == null && this.variantLine == null) {
            FrontierUi.text(g, FrontierUi.fit(this.metaLine, w - 68, FrontierUi.Size.SMALL), mx0, ny + 3.5F, BenchDraw.INK_MUTED, FrontierUi.Size.SMALL);
         }
         // what it does
         float dy = y + 58;
         for (String s : this.desc) {
            FrontierUi.text(g, s, x + 10, dy, BenchDraw.INK_MUTED, FrontierUi.Size.SMALL);
            dy += 9;
         }
         // materials
         float my0 = matTop;
         FrontierUi.text(g, tr("bench.frontierhunts.ui.materials"), x + 10, my0, BenchDraw.GOLD_DARK, FrontierUi.Size.SMALL);
         FrontierUi.right(g, tr("bench.frontierhunts.ui.have_need"), x + w - 10, my0, BenchDraw.GOLD_DARK, FrontierUi.Size.SMALL);
         FrontierUi.rect(g, x + 10, my0 + 10, w - 20, 1, 0.0F, BenchDraw.PAPER_RULE);
         float ry = my0 + 14;
         for (int k = this.matScroll; k < Math.min(mats, this.matScroll + rowsFit); k++) {
            BenchCatalog.Material m = e.materials.get(k);
            boolean ok = this.creative || this.matHave[k] >= m.count();
            boolean make = !ok && k < this.matMake.length && this.matMake[k]; // [smalls] made from materials when you craft
            FrontierUi.rect(g, x + 8, ry - 1, w - 16, MAT_ROW, 3.0F, ok || make ? 0x0A000000 : 0x14B23A2B);
            String hn = (this.creative ? "∞" : Integer.toString(Math.min(999, this.matHave[k]))) + " / " + m.count();
            float pw = FrontierUi.width(hn, FrontierUi.Size.STRONG) + 10;
            FrontierUi.text(g, FrontierUi.fit(this.matNames[k], (int)(w - 50 - pw), FrontierUi.Size.BODY), x + 31, ry + 4, BenchDraw.INK, FrontierUi.Size.BODY);
            FrontierUi.rect(g, x + w - 11 - pw, ry + 2.5F, pw, 11, 5.5F, ok ? 0x263D7A39 : (make ? 0x308F6E2F : 0x26B23A2B));
            FrontierUi.text(g, hn, x + w - 11 - pw + 5, ry + 3.5F, ok ? BenchDraw.GREEN_INK : (make ? BenchDraw.GOLD_DARK : BenchDraw.RED_INK),
               FrontierUi.Size.STRONG);
            ry += MAT_ROW + 2;
         }
         if (mats > rowsFit) {
            String more = (this.matScroll + 1) + "-" + Math.min(mats, this.matScroll + rowsFit) + " / " + mats;
            FrontierUi.right(g, more, x + w - 10, ry, BenchDraw.INK_MUTED, FrontierUi.Size.SMALL);
         }
         // status
         if (this.statusOk) {
            BenchDraw.check(g, x + 14, statusY + 3.5F, this.statusColor, 0.8F);
         } else {
            BenchDraw.warn(g, x + 14, statusY + 4, this.statusColor, BenchDraw.PAPER);
         }
         FrontierUi.text(g, FrontierUi.fit(this.status, w - 34, FrontierUi.Size.SMALL), x + 21, statusY + 0.5F, this.statusColor, FrontierUi.Size.SMALL);
         // [smalls] the hint (or "also made at a crafting table") over the status
         float hy = hintY;
         for (String line : this.hint) {
            FrontierUi.text(g, line, x + 10, hy, BenchDraw.INK_MUTED, FrontierUi.Size.SMALL);
            hy += 9;
         }
         // buttons
         for (Hot hz : this.hots) {
            boolean over = hz.in(mx, my);
            switch (hz.kind) {
               case H_CRAFT -> {
                  boolean can = e.sewing() ? this.statusOk : this.ready[e.index];
                  String label = e.sewing() ? tr("bench.frontierhunts.ui.sew") : tr("bench.frontierhunts.ui.craft");
                  BenchDraw.button(g, hz.x, hz.y, hz.w, hz.h, label, BenchDraw.Look.PRIMARY, accent, can, over, "Enter");
                  if (busy && can) {
                     float p = (this.busyUntil - System.currentTimeMillis()) / (float)BUSY_MS;
                     FrontierUi.rect(g, hz.x + 3, hz.y + hz.h - 3, (hz.w - 6) * Math.max(0.0F, p), 1.5F, 0.0F, 0x80000000);
                  }
               }
               case H_CRAFT5 -> {
                  if (this.bench.batches()) {
                     BenchDraw.button(g, hz.x, hz.y, hz.w, hz.h, tr("bench.frontierhunts.ui.craft5"), BenchDraw.Look.PAPER, accent, this.ready5[e.index], over, null);
                  }
               }
               case H_FIT_HELD -> {
                  if (this.fitPart != null) {
                     BenchDraw.button(g, hz.x, hz.y, hz.w, hz.h, this.fitLabel, BenchDraw.Look.PAPER, accent, this.fitCan, over, null);
                  }
               }
               default -> {
               }
            }
         }
      });
      if (e == null) {
         return;
      }
      icon(g, e.display, x + 14, y + 14, 2.0F);
      float ry = matTop + 14;
      for (int k = this.matScroll; k < Math.min(mats, this.matScroll + rowsFit); k++) {
         icon(g, this.shown(e.materials.get(k).ingredient()), x + 11, ry, 1.0F);
         ry += MAT_ROW + 2;
      }
   }

   private void drawFooter(GuiGraphics g) {
      int fy = this.footY;
      String left = switch (this.tab.kind) {
         case FIT -> this.query.isEmpty() ? tr("bench.frontierhunts.ui.footer_fit") : tr("bench.frontierhunts.ui.footer");
         case REFIT -> this.query.isEmpty() ? tr("bench.frontierhunts.ui.footer_refit") : tr("bench.frontierhunts.ui.footer");
         default -> tr("bench.frontierhunts.ui.footer");
      };
      boolean recipes = this.tab.recipes() || !this.query.isEmpty();
      FrontierUi.batch(g, () -> {
         FrontierUi.rect(g, this.L + PAD, fy, this.W - 2 * PAD, 1, 0.0F, BenchDraw.RULE_DARK);
         FrontierUi.text(g, left, this.L + PAD + 2, fy + 7, BenchDraw.DIM, FrontierUi.Size.SMALL);
         float x = this.L + this.W - PAD - 2;
         String[][] keys = recipes ? KEYS_RECIPES : (this.tab.kind == BenchTab.Kind.REFIT ? KEYS_REFIT : KEYS_FIT);
         for (int i = keys.length - 1; i >= 0; i--) {
            String label = tr(keys[i][1]);
            x -= FrontierUi.width(label, FrontierUi.Size.SMALL);
            FrontierUi.text(g, label, x, fy + 7, BenchDraw.DIM, FrontierUi.Size.SMALL);
            float kw = FrontierUi.width(keys[i][0], FrontierUi.Size.SMALL) + 8;
            x -= kw + 4;
            BenchDraw.key(g, keys[i][0], x, fy + 5.5F, 0x1CFFFFFF, BenchDraw.MUTED);
            x -= 12;
         }
      });
   }

   private static final String[][] KEYS_RECIPES = {
      {"Enter", "bench.frontierhunts.ui.key.craft"}, {"↑↓←→", "bench.frontierhunts.ui.key.select"}, {"Tab", "bench.frontierhunts.ui.key.tab"},
      {"Esc", "bench.frontierhunts.ui.key.close"}};
   private static final String[][] KEYS_REFIT = {
      {"Enter", "bench.frontierhunts.ui.key.refit"}, {"↑↓", "bench.frontierhunts.ui.key.select"}, {"Tab", "bench.frontierhunts.ui.key.tab"},
      {"Esc", "bench.frontierhunts.ui.key.close"}};
   private static final String[][] KEYS_FIT = {{"Tab", "bench.frontierhunts.ui.key.tab"}, {"Esc", "bench.frontierhunts.ui.key.close"}};

   private void drawTooltips(GuiGraphics g, int mx, int my) {
      if (this.tab.kind == BenchTab.Kind.FIT && this.query.isEmpty()) {
         this.fit.tooltip(g, mx, my);
         return;
      }
      if (this.tab.kind == BenchTab.Kind.REFIT && this.query.isEmpty()) {
         this.refit.tooltip(g, mx, my);
         return;
      }
      if (this.selected < 0) {
         return;
      }
      BenchCatalog.Entry e = this.entries.get(this.selected);
      if (in(mx, my, this.detX + 10, this.bodyY + 10, 40, 40)) {
         g.renderTooltip(this.font, e.display, mx, my);
         return;
      }
      float matTop = this.bodyY + 58 + this.desc.size() * 9 + (this.desc.isEmpty() ? 0 : 5);
      float ry = matTop + 14;
      float by = this.bodyY + this.bodyH - 10 - 22;
      float statusY = (this.fitPart != null ? by - 24 : by) - 14;
      int rowsFit = Math.max(1, (int)((statusY - 6 - (matTop + 14)) / (MAT_ROW + 2)));
      for (int k = this.matScroll; k < Math.min(e.materials.size(), this.matScroll + rowsFit); k++) {
         if (in(mx, my, this.detX + 8, ry - 1, DETAIL_W - 16, MAT_ROW)) {
            ItemStack st = this.shown(e.materials.get(k).ingredient());
            if (!st.isEmpty()) {
               g.renderTooltip(this.font, st, mx, my);
            }
            return;
         }
         ry += MAT_ROW + 2;
      }
   }

   /** Rail icons: one stack per tab, made once. */
   static final class RailIcons {
      private static final ItemStack[] STACKS = new ItemStack[BenchTab.values().length];

      static ItemStack get(BenchTab t) {
         ItemStack s = STACKS[t.ordinal()];
         if (s == null) {
            ResourceLocation rl = ResourceLocation.tryParse(t.icon);
            Item item = rl == null ? Items.AIR : BuiltInRegistries.ITEM.get(rl);
            s = item == Items.AIR ? new ItemStack(Items.CRAFTING_TABLE) : new ItemStack(item);
            STACKS[t.ordinal()] = s;
         }
         return s;
      }
   }
}
