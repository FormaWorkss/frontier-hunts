package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.HuntContent;
import com.formaworks.frontierhunts.hunting.ArrowTip;
import com.formaworks.frontierhunts.hunting.ArrowTipItem;
import com.formaworks.frontierhunts.workshop.EquipmentCatalog;
import com.formaworks.frontierhunts.workshop.WorkbenchMenu;
import com.formaworks.frontierhunts.workshop.WorkshopKind;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;

public final class WorkbenchScreen extends AbstractContainerScreen<WorkbenchMenu> {
   private int selected = -1;
   private int page;
   private int rows;
   private int listWidth;
   private int materialScroll;
   private boolean readyOnly;
   private String query = "";
   private EditBox search;
   private Button craft;
   private Button batch;
   private Button fit;
   private Button remove;
   private Button readyFilter;
   private List<ItemStack> outputs = List.of();
   private List<Integer> filtered = List.of();
   private List<WorkbenchScreen.Material> materials = List.of();
   private int materialSelection = -2;
   private int readyFingerprint;
   private final List<Button> actions = new ArrayList<>();
   private boolean tipsMode;
   // [bows] arrow tips tab state
   private int arrowSlot = -1;
   private int arrowPage;
   private List<Integer> arrowSlots = List.of();
   private int tipCode = 1;
   private int tipGridRows = 1;
   private int allCount;
   private int stripCount;
   private Button fitOne;
   private Button fitAll;
   private Button makeHeads;
   private Button stripHeads;

   public WorkbenchScreen(WorkbenchMenu var1, Inventory var2, Component var3) {
      super(var1, var2, var3);
   }

   protected void init() {
      this.imageWidth = Math.min(480, this.width - 12);
      this.imageHeight = Math.min(306, this.height - 12);
      super.init();
      this.listWidth = Math.max(115, (this.imageWidth - 30) * 42 / 100);
      this.rows = Math.max(3, (this.imageHeight - 101) / 29);
      this.outputs = ((WorkbenchMenu)this.menu)
         .recipes()
         .stream()
         .map(var1 -> ((CraftingRecipe)var1.value()).getResultItem(this.minecraft.level.registryAccess()).copy())
         .toList();
      this.rebuild();
   }

   private void filter() {
      ArrayList var1 = new ArrayList();
      String var2 = this.query.strip().toLowerCase(Locale.ROOT);

      for (int var3 = 0; var3 < this.outputs.size(); var3++) {
         if ((!this.readyOnly || ((WorkbenchMenu)this.menu).ready(var3))
            && (var2.isEmpty() || this.outputs.get(var3).getHoverName().getString().toLowerCase(Locale.ROOT).contains(var2))) {
            var1.add(var3);
         }
      }

      this.filtered = var1;
      if (!this.filtered.contains(this.selected)) {
         this.selected = this.filtered.isEmpty() ? -1 : this.filtered.getFirst();
      }

      this.page = Math.clamp((long)this.page, 0, Math.max(0, (this.filtered.size() - 1) / this.rows));
   }

   private void rebuild() {
      this.filter();
      this.clearWidgets();
      this.actions.clear();
      int var1 = this.leftPos;
      int var2 = this.topPos;
      if (((WorkbenchMenu)this.menu).kind == WorkshopKind.BOWS) {
         byte var3 = 78;
         int var4 = var1 + this.imageWidth - 34 - var3 * 2;
         ((Button)this.addRenderableWidget(Button.builder(Component.literal(this.tipsMode ? "Craft" : "› Craft"), var1x -> {
            this.tipsMode = false;
            this.rebuild();
         }).bounds(var4, var2 + 7, var3, 18).build())).active = this.tipsMode;
         ((Button)this.addRenderableWidget(Button.builder(Component.literal(this.tipsMode ? "› Arrow tips" : "Arrow tips"), var1x -> {
            this.tipsMode = true;
            this.rebuild();
         }).bounds(var4 + var3 + 2, var2 + 7, var3, 18).build())).active = !this.tipsMode;
         if (this.tipsMode) {
            this.rebuildTips();
            return;
         }
      }

      this.search = (EditBox)this.addRenderableWidget(
         new EditBox(this.font, var1 + 12, var2 + 39, this.listWidth - 3, 20, Component.literal("Search " + ((WorkbenchMenu)this.menu).kind.title))
      );
      this.search.setMaxLength(80);
      this.search.setHint(Component.literal("Search equipment…"));
      this.search.setValue(this.query);
      this.search.setResponder(var1x -> {
         this.query = var1x;
         this.page = 0;
         this.rebuild();
         this.setFocused(this.search);
         this.search.setFocused(true);
      });
      int var7 = var1 + this.listWidth + 24;
      int var8 = this.imageWidth - this.listWidth - 36;
      this.readyFilter = (Button)this.addRenderableWidget(Button.builder(Component.literal(this.readyOnly ? "Ready only: ON" : "Ready only: OFF"), var1x -> {
         this.readyOnly = !this.readyOnly;
         this.page = 0;
         this.rebuild();
      }).bounds(var7, var2 + 39, var8, 20).build());

      for (int var5 = 0; var5 < this.rows && this.page * this.rows + var5 < this.filtered.size(); var5++) {
         int var6 = this.filtered.get(this.page * this.rows + var5);
         this.actions.add((Button)this.addRenderableWidget(new WorkbenchScreen.RecipeRow(var1 + 10, var2 + 68 + var5 * 29, this.listWidth, 26, var6)));
      }

      ((Button)this.addRenderableWidget(Button.builder(Component.literal("‹"), var1x -> {
         this.page--;
         this.rebuild();
      }).bounds(var1 + 10, var2 + this.imageHeight - 25, 25, 18).build())).active = this.page > 0;
      ((Button)this.addRenderableWidget(Button.builder(Component.literal("›"), var1x -> {
         this.page++;
         this.rebuild();
      }).bounds(var1 + this.listWidth - 15, var2 + this.imageHeight - 25, 25, 18).build())).active = (this.page + 1) * this.rows < this.filtered.size();
      int var9 = var2 + this.imageHeight - 48;
      this.craft = (Button)this.addRenderableWidget(
         Button.builder(Component.literal(this.minecraft.player.hasInfiniteMaterials() ? "Take" : "Assemble"), var1x -> {
            if (this.selected >= 0) {
               this.request(this.selected);
            }
         }).bounds(var7, var9, var8, 20).build()
      );
      this.batch = (Button)this.addRenderableWidget(Button.builder(Component.literal("Assemble 5 batches"), var1x -> {
         if (this.selected >= 0) {
            this.request(10000 + this.selected);
         }
      }).bounds(var7, var9 + 24, var8, 18).build());
      this.batch.visible = ((WorkbenchMenu)this.menu).kind == WorkshopKind.AMMUNITION;
      int var10 = (var8 - 5) / 2;
      this.fit = (Button)this.addRenderableWidget(
         Button.builder(Component.literal("Install"), var1x -> this.request(this.partAction(true))).bounds(var7, var9 + 24, var10, 18).build()
      );
      this.remove = (Button)this.addRenderableWidget(
         Button.builder(Component.literal("Remove"), var1x -> this.request(this.partAction(false)))
            .bounds(var7 + var10 + 5, var9 + 24, var8 - var10 - 5, 18)
            .build()
      );
      this.fit.visible = this.remove.visible = ((WorkbenchMenu)this.menu).kind == WorkshopKind.FISHING;
      if (((WorkbenchMenu)this.menu).attachments) {
         this.addRenderableWidget(Button.builder(Component.literal("Fit to weapon ›"), var1x -> this.request(9000)).bounds(var7, var9 + 24, var8, 18).build());
      }

      this.addRenderableWidget(Button.builder(Component.literal("×"), var1x -> this.onClose()).bounds(var1 + this.imageWidth - 26, var2 + 8, 18, 18).build());
      this.updateButtons();
   }

   // ---------------------------------------------------------------------------------- [bows] Arrow tips tab
   // 1. pick a stack of your arrows (left)  2. click the head you want (grid, with how many you own)
   // 3. Fit to 1 / Fit all - missing heads are made from your materials; or take the heads off again.

   private void rebuildTips() {
      int x0 = this.leftPos;
      int y0 = this.topPos;
      int px = x0 + this.listWidth + 24;
      int pw = this.imageWidth - this.listWidth - 36;
      this.arrowSlots = this.arrowSlots();
      if (!this.arrowSlots.contains(this.arrowSlot)) {
         this.arrowSlot = this.arrowSlots.isEmpty() ? -1 : this.largest();
      }
      int rows = this.arrowRows();
      this.arrowPage = Math.clamp((long)this.arrowPage, 0, Math.max(0, (this.arrowSlots.size() - 1) / rows));
      for (int i = 0; i < rows && this.arrowPage * rows + i < this.arrowSlots.size(); i++) {
         this.addRenderableWidget(new WorkbenchScreen.ArrowRow(x0 + 10, y0 + 52 + i * 26, this.listWidth, 24, this.arrowSlots.get(this.arrowPage * rows + i)));
      }
      ((Button)this.addRenderableWidget(Button.builder(Component.literal("‹"), b -> {
         this.arrowPage--;
         this.rebuild();
      }).bounds(x0 + 10, y0 + this.imageHeight - 25, 25, 18).build())).active = this.arrowPage > 0;
      ((Button)this.addRenderableWidget(Button.builder(Component.literal("›"), b -> {
         this.arrowPage++;
         this.rebuild();
      }).bounds(x0 + this.listWidth - 15, y0 + this.imageHeight - 25, 25, 18).build())).active = (this.arrowPage + 1) * rows < this.arrowSlots.size();
      int cols = Math.max(4, Math.min(12, pw / 24));
      for (int i = 0; i < TIP_CODES.length; i++) {
         this.addRenderableWidget(new WorkbenchScreen.TipCell(px + (i % cols) * 24, y0 + 51 + (i / cols) * 24, TIP_CODES[i]));
      }
      this.tipGridRows = (TIP_CODES.length + cols - 1) / cols;
      int by = y0 + this.imageHeight - 48;
      int half = (pw - 5) / 2;
      this.fitOne = (Button)this.addRenderableWidget(
         Button.builder(Component.literal("Fit to 1"), b -> this.request(WorkbenchMenu.refitAction(this.arrowSlot, this.tipCode, false))).bounds(px, by, half, 20).build()
      );
      this.fitAll = (Button)this.addRenderableWidget(
         Button.builder(Component.literal("Fit all"), b -> this.request(WorkbenchMenu.refitAction(this.arrowSlot, this.tipCode, true)))
            .bounds(px + half + 5, by, pw - half - 5, 20)
            .build()
      );
      this.makeHeads = (Button)this.addRenderableWidget(Button.builder(Component.literal("Make heads"), b -> {
         int r = this.tipCode < TIPS.length ? ((WorkbenchMenu)this.menu).headRecipe(TIPS[this.tipCode]) : -1;
         if (r >= 0) {
            this.request(r);
         }
      }).bounds(px, by + 23, half, 18).build());
      this.stripHeads = (Button)this.addRenderableWidget(
         Button.builder(Component.literal("Take heads off"), b -> this.request(WorkbenchMenu.refitAction(this.arrowSlot, WorkbenchMenu.REFIT_STOCK, true)))
            .bounds(px + half + 5, by + 23, pw - half - 5, 18)
            .build()
      );
      this.addRenderableWidget(Button.builder(Component.literal("×"), b -> this.onClose()).bounds(x0 + this.imageWidth - 26, y0 + 8, 18, 18).build());
      this.refreshTips();
   }

   private static final ArrowTip[] TIPS = ArrowTip.values();
   /** Grid order: every head, then the "stock head" (take fitted heads off) cell. */
   private static final int[] TIP_CODES = tipCodes();

   private static int[] tipCodes() {
      int[] c = new int[TIPS.length + 1];
      for (int i = 0; i < TIPS.length; i++) {
         c[i] = i;
      }
      c[TIPS.length] = WorkbenchMenu.REFIT_STOCK;
      return c;
   }

   private int arrowRows() {
      return Math.max(2, (this.imageHeight - 29 - 52) / 26);
   }

   private List<Integer> arrowSlots() {
      List<Integer> out = new ArrayList<>();
      List<ItemStack> items = this.minecraft.player.getInventory().items;
      for (int i = 0; i < Math.min(WorkbenchMenu.REFIT_SLOTS, items.size()); i++) {
         ItemStack s = items.get(i);
         if (!s.isEmpty() && (ArrowTip.arrow(s) || s.is(Items.ARROW))) {
            out.add(i);
         }
      }
      return out;
   }

   private int largest() {
      int best = -1;
      List<ItemStack> items = this.minecraft.player.getInventory().items;
      for (int i : this.arrowSlots) {
         if (best < 0 || items.get(i).getCount() > items.get(best).getCount()) {
            best = i;
         }
      }
      return best;
   }

   private ItemStack selectedArrows() {
      List<ItemStack> items = this.minecraft.player.getInventory().items;
      return this.arrowSlot >= 0 && this.arrowSlot < items.size() ? items.get(this.arrowSlot) : ItemStack.EMPTY;
   }

   /** The head the selected stack carries now (null for plain vanilla arrows). */
   private static ArrowTip currentTip(ItemStack s) {
      return s.isEmpty() || s.is(Items.ARROW) ? null : ArrowTip.of(s);
   }

   private static ArrowTip stockTip(ItemStack s) {
      return ArrowTip.primitiveShaft(s) ? ArrowTip.FLINT_POINT : ArrowTip.FIXED_BROADHEAD;
   }

   /** The head the chosen action leaves on the arrows. */
   private ArrowTip targetTip() {
      return this.tipCode == WorkbenchMenu.REFIT_STOCK ? stockTip(this.selectedArrows()) : TIPS[Math.clamp(this.tipCode, 0, TIPS.length - 1)];
   }

   private int heads(ArrowTip tip) {
      int n = 0;
      for (ItemStack s : this.minecraft.player.getInventory().items) {
         if (s.getItem() instanceof ArrowTipItem t && t.tip == tip) {
            n += s.getCount();
         }
      }
      return n;
   }

   /** Recomputes what the buttons can do (both sides compute the same plan; the server re-checks on click). */
   private void refreshTips() {
      if (this.fitOne == null) {
         return;
      }
      WorkbenchMenu m = (WorkbenchMenu)this.menu;
      boolean any = this.arrowSlot >= 0;
      boolean stock = this.tipCode == WorkbenchMenu.REFIT_STOCK;
      this.fitOne.active = any && !stock && m.canRefit(this.arrowSlot, this.tipCode, false);
      this.allCount = any && !stock ? m.refitCount(this.arrowSlot, this.tipCode, true) : 0;
      this.fitAll.active = this.allCount > 0;
      this.fitAll.setMessage(Component.literal(this.allCount > 0 ? "Fit all (" + this.allCount + ")" : "Fit all"));
      this.fitOne.visible = this.fitAll.visible = !stock;
      int recipe = !stock && this.tipCode < TIPS.length ? m.headRecipe(TIPS[this.tipCode]) : -1;
      this.makeHeads.visible = recipe >= 0;
      this.makeHeads.active = recipe >= 0 && m.ready(recipe);
      if (recipe >= 0) {
         ItemStack out = ((CraftingRecipe)m.recipes().get(recipe).value()).getResultItem(this.minecraft.level.registryAccess());
         this.makeHeads.setMessage(Component.literal("Make " + out.getCount() + " heads"));
      }
      this.stripCount = any ? m.refitCount(this.arrowSlot, WorkbenchMenu.REFIT_STOCK, true) : 0;
      this.stripHeads.active = this.stripCount > 0;
      this.stripHeads.setMessage(Component.literal(this.stripCount > 0 ? "Take heads off (" + this.stripCount + ")" : "Take heads off"));
      if (stock) {
         // the stock cell turns the main buttons into the strip action
         this.stripHeads.setX(this.fitOne.getX());
         this.stripHeads.setY(this.fitOne.getY());
         this.stripHeads.setWidth(this.fitAll.getX() + this.fitAll.getWidth() - this.fitOne.getX());
         this.stripHeads.setHeight(20);
      } else {
         int half = this.fitOne.getWidth();
         this.stripHeads.setX(this.fitOne.getX() + half + 5);
         this.stripHeads.setY(this.fitOne.getY() + 23);
         this.stripHeads.setWidth(this.fitAll.getWidth());
         this.stripHeads.setHeight(18);
      }
   }

   private void renderTipsLabels(GuiGraphics g, int mouseX, int mouseY) {
      g.drawString(this.font, "BOW WORKSHOP · ARROW TIPS", 12, 10, -1119785, false);
      g.drawString(this.font, "1  YOUR ARROWS", 12, 40, -4865363, false);
      int px = this.listWidth + 24;
      int pw = this.imageWidth - this.listWidth - 36;
      g.drawString(this.font, "2  NEW HEAD", px + 2, 40, -8623803, false);
      String own = "number = heads you have";
      if (pw > this.font.width("2  NEW HEAD") + this.font.width(own) + 12) {
         g.drawString(this.font, own, px + pw - this.font.width(own), 40, -6252155, false);
      }
      if (this.arrowSlots.isEmpty()) {
         int y = 56;
         for (FormattedCharSequence line : this.font.split(
            Component.literal("No arrows in your inventory. Craft Hunting or Primitive Arrows on the Craft tab, or take some out of your quiver."), this.listWidth - 8
         )) {
            g.drawString(this.font, line, 14, y, -5655652, false);
            y += 10;
         }
      }
      ItemStack arrows = this.selectedArrows();
      int y = 51 + this.tipGridRows * 24 + 4;
      g.fill(px, y, px + pw, y + 1, -4605535);
      y += 5;
      ArrowTip now = currentTip(arrows);
      ArrowTip next = this.targetTip();
      boolean stock = this.tipCode == WorkbenchMenu.REFIT_STOCK;
      // "current → new" header with icons
      if (!arrows.isEmpty()) {
         g.renderItem(now == null ? arrows : now.tipItem(1), px, y);
         String a = now == null ? "Plain arrow" : now.title;
         String b = stock ? "Stock " + next.title : next.title;
         int aw = Math.min(this.font.width(a), (pw - 64) / 2);
         g.drawString(this.font, this.font.plainSubstrByWidth(a, aw), px + 18, y + 4, -12627647, false);
         g.drawString(this.font, "→", px + 22 + aw, y + 4, -8623803, false);
         g.renderItem(next.tipItem(1), px + 32 + aw, y);
         g.drawString(this.font, this.font.plainSubstrByWidth(b, pw - (50 + aw)), px + 50 + aw, y + 4, -14599376, false);
      } else {
         g.renderItem(next.tipItem(1), px, y);
         g.drawString(this.font, this.font.plainSubstrByWidth(next.title, pw - 20), px + 18, y + 4, -14599376, false);
      }
      y += 20;
      y = this.compare(g, px, y, pw, "Penetration", now == null ? -1 : now.penetration, next.penetration, 1.25F, -10650470);
      y = this.compare(g, px, y, pw, "Blood trail", now == null ? -1 : now.bleed, next.bleed, 1.4F, -6141380);
      y = this.compare(g, px, y, pw, "Damage", now == null ? -1 : now.damage, next.damage, 1.1F, -7316921);
      y = this.compare(g, px, y, pw, "Recovery", now == null ? -1 : now.recovery, next.recovery, 1.0F, -8611235);
      int bottom = this.imageHeight - 53 - 12;
      for (FormattedCharSequence line : this.font.split(Component.literal(next.summary()), pw - 4)) {
         if (y + 9 > bottom) {
            break;
         }
         g.drawString(this.font, line, px + 2, y + 1, -12627647, false);
         y += 10;
      }
      g.drawString(this.font, this.font.plainSubstrByWidth(this.status(arrows, now, next), pw), px + 1, this.imageHeight - 53 - 11, this.statusColor, false);
   }

   private int statusColor = -13082304;

   private String status(ItemStack arrows, ArrowTip now, ArrowTip next) {
      this.statusColor = -13082304;
      boolean creative = this.minecraft.player.hasInfiniteMaterials();
      if (arrows.isEmpty()) {
         this.statusColor = -6926015;
         return "Pick a stack of arrows on the left";
      }
      if (this.tipCode == WorkbenchMenu.REFIT_STOCK) {
         if (this.stripCount <= 0) {
            this.statusColor = -6926015;
            return "These arrows carry their stock head - nothing to take off";
         }
         return "Heads back to your inventory · shafts get their stock " + next.title.toLowerCase(java.util.Locale.ROOT);
      }
      if (now == next && !arrows.is((Item)HuntContent.TRACER_ARROW.get())) {
         return "These arrows already carry this head";
      }
      if (creative) {
         return "Creative: heads are free";
      }
      int owned = this.heads(next);
      if (this.allCount <= 0) {
         this.statusColor = -6926015;
         return owned > 0 ? "No space in your inventory" : "No heads, and not enough material to make any";
      }
      int made = Math.max(0, this.allCount - owned);
      return made > 0
         ? "You have " + owned + " · " + made + " more made from your materials"
         : "You have " + owned + " heads" + (ArrowTip.fitted(arrows) || arrows.is((Item)HuntContent.TRACER_ARROW.get()) ? " · old heads come back" : "");
   }

   /** One comparison row: the bar shows the new head, a dark tick the head on the arrows now, and the change. */
   private int compare(GuiGraphics g, int x, int y, int w, String label, float now, float next, float max, int color) {
      g.drawString(this.font, label, x + 2, y, -10855868, false);
      int bx = x + 68;
      int bw = w - 68 - 34;
      g.fill(bx, y + 2, bx + bw, y + 7, -3159628);
      g.fill(bx, y + 2, bx + Math.round(bw * Math.clamp(next / max, 0.0F, 1.0F)), y + 7, color);
      if (now >= 0.0F) {
         int t = bx + Math.round(bw * Math.clamp(now / max, 0.0F, 1.0F));
         g.fill(t - 1, y, t + 1, y + 9, -14605024);
         int pct = now <= 0.0F ? (next > 0.0F ? 100 : 0) : Math.round((next / now - 1.0F) * 100.0F);
         String d = pct == 0 ? "=" : (pct > 0 ? "+" + pct + "%" : pct + "%");
         g.drawString(this.font, d, x + w - this.font.width(d), y, pct > 0 ? -12873432 : (pct < 0 ? -5164990 : -8355712), false);
      }
      return y + 11;
   }

   private final class ArrowRow extends Button {
      private final int slot;

      ArrowRow(int x, int y, int w, int h, int slot) {
         super(x, y, w, h, Component.literal("Arrows"), b -> {
            WorkbenchScreen.this.arrowSlot = slot;
            WorkbenchScreen.this.refreshTips();
         }, DEFAULT_NARRATION);
         this.slot = slot;
      }

      ItemStack stack() {
         List<ItemStack> items = WorkbenchScreen.this.minecraft.player.getInventory().items;
         return this.slot < items.size() ? items.get(this.slot) : ItemStack.EMPTY;
      }

      protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
         int x = this.getX();
         int y = this.getY();
         boolean sel = WorkbenchScreen.this.arrowSlot == this.slot;
         g.fill(x, y, x + this.width, y + this.height, sel ? -11309992 : (this.isHoveredOrFocused() ? -13349311 : -14468817));
         if (sel) {
            g.fill(x, y, x + 2, y + this.height, 0xFF000000 | ((WorkbenchMenu)WorkbenchScreen.this.menu).kind.accent);
         }
         ItemStack s = this.stack();
         if (s.isEmpty()) {
            return;
         }
         g.renderItem(s, x + 4, y + 4);
         ArrowTip tip = currentTip(s);
         String shaft = s.is(Items.ARROW) ? "Plain arrow" : (ArrowTip.primitiveShaft(s) ? "Primitive arrow" : "Hunting arrow");
         String count = "×" + s.getCount();
         int tw = this.width - 30 - WorkbenchScreen.this.font.width(count) - 6;
         g.drawString(WorkbenchScreen.this.font, WorkbenchScreen.this.font.plainSubstrByWidth(shaft, tw), x + 24, y + 3, -1251114, false);
         String head = tip == null ? "no hunting head" : tip.title;
         g.drawString(WorkbenchScreen.this.font, WorkbenchScreen.this.font.plainSubstrByWidth(head, tw), x + 24, y + 13, tip != null && tip.tracer() ? 0xFF000000 | tip.glow : -5655652, false);
         g.drawString(WorkbenchScreen.this.font, count, x + this.width - 5 - WorkbenchScreen.this.font.width(count), y + 8, -4729454, false);
      }
   }

   private final class TipCell extends Button {
      private final int code;

      TipCell(int x, int y, int code) {
         super(x, y, 22, 22, Component.literal(code == WorkbenchMenu.REFIT_STOCK ? "Stock head" : TIPS[code].title), b -> {
            WorkbenchScreen.this.tipCode = code;
            WorkbenchScreen.this.refreshTips();
         }, DEFAULT_NARRATION);
         this.code = code;
      }

      ItemStack icon() {
         ItemStack arrows = WorkbenchScreen.this.selectedArrows();
         return (this.code == WorkbenchMenu.REFIT_STOCK ? stockTip(arrows) : TIPS[this.code]).tipItem(1);
      }

      protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
         int x = this.getX();
         int y = this.getY();
         boolean sel = WorkbenchScreen.this.tipCode == this.code;
         boolean stock = this.code == WorkbenchMenu.REFIT_STOCK;
         ArrowTip on = currentTip(WorkbenchScreen.this.selectedArrows());
         g.fill(x, y, x + 22, y + 22, sel ? -1 : (this.isHoveredOrFocused() ? -2763307 : -4144960));
         g.fill(x + 1, y + 1, x + 21, y + 21, sel ? -11309992 : (stock ? -12898995 : -14468817));
         if (!stock && TIPS[this.code].tracer()) {
            g.fill(x + 1, y + 19, x + 21, y + 21, 0xFF000000 | TIPS[this.code].glow);
         }
         g.renderItem(this.icon(), x + 3, y + 3);
         if (stock) {
            // "take off" badge
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, 200.0F);
            g.drawString(WorkbenchScreen.this.font, "↺", x + 14, y + 1, -1, true);
            g.pose().popPose();
            return;
         }
         if (on == TIPS[this.code]) {
            g.fill(x + 1, y + 1, x + 5, y + 5, -12873432); // the head these arrows already carry
         }
         int n = WorkbenchScreen.this.heads(TIPS[this.code]);
         if (n > 0) {
            String c = n > 99 ? "99+" : Integer.toString(n);
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, 200.0F);
            g.drawString(WorkbenchScreen.this.font, c, x + 22 - WorkbenchScreen.this.font.width(c), y + 14, -1, true);
            g.pose().popPose();
         }
      }
   }

   @Override
   public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
      super.render(g, mouseX, mouseY, pt);
      if (this.tipsMode) {
         for (var child : this.children()) {
            if (child instanceof WorkbenchScreen.TipCell cell && cell.isHovered()) {
               if (cell.code == WorkbenchMenu.REFIT_STOCK) {
                  g.renderComponentTooltip(this.font, List.of(
                     Component.literal("Take heads off"),
                     Component.literal("The fitted heads go back to your inventory;").withStyle(net.minecraft.ChatFormatting.GRAY),
                     Component.literal("the shafts get their stock head back.").withStyle(net.minecraft.ChatFormatting.GRAY)
                  ), mouseX, mouseY);
               } else {
                  g.renderTooltip(this.font, cell.icon(), mouseX, mouseY);
               }
            } else if (child instanceof WorkbenchScreen.ArrowRow row && row.isHovered() && !row.stack().isEmpty()) {
               g.renderTooltip(this.font, row.stack(), mouseX, mouseY);
            }
         }
      }
   }

   private void request(int var1) {
      if (var1 >= 0 && this.minecraft.gameMode != null) {
         this.minecraft.gameMode.handleInventoryButtonClick(((WorkbenchMenu)this.menu).containerId, var1);
      }
   }

   private int partAction(boolean var1) {
      return this.selected < 0 ? -1 : ((WorkbenchMenu)this.menu).attachmentAction(EquipmentCatalog.id(this.outputs.get(this.selected).getItem()), var1);
   }

   private void updateButtons() {
      this.craft.active = this.selected >= 0 && ((WorkbenchMenu)this.menu).ready(this.selected);
      this.batch.active = this.selected >= 0 && ((WorkbenchMenu)this.menu).readyBatch(this.selected);
      this.fit.active = ((WorkbenchMenu)this.menu).ready(this.partAction(true));
      this.remove.active = ((WorkbenchMenu)this.menu).ready(this.partAction(false));
   }

   protected void containerTick() {
      super.containerTick();
      if (this.tipsMode) {
         if (!this.arrowSlots().equals(this.arrowSlots)) {
            this.rebuild(); // [bows] a stack appeared or went: rebuild the list
         } else {
            this.refreshTips();
         }
      } else {
         int var1 = 1;

         for (int var2 = 0; var2 < this.outputs.size(); var2++) {
            var1 = 31 * var1 + (((WorkbenchMenu)this.menu).ready(var2) ? 1 : 0);
         }

         if (this.readyOnly && var1 != this.readyFingerprint) {
            this.rebuild();
         }

         this.readyFingerprint = var1;
         this.updateButtons();
      }
   }

   public boolean mouseScrolled(double var1, double var3, double var5, double var7) {
      if (this.tipsMode) {
         if (var1 < (double)(this.leftPos + this.listWidth + 20)) {
            this.arrowPage += var7 > 0.0 ? -1 : 1;
            this.rebuild();
            return true;
         } else {
            return super.mouseScrolled(var1, var3, var5, var7);
         }
      } else if (var1 >= (double)(this.leftPos + this.listWidth + 24)
         && var1 < (double)(this.leftPos + this.imageWidth)
         && var3 >= (double)(this.topPos + 134)
         && var3 < (double)(this.topPos + this.imageHeight - 53)) {
         this.materialScroll = Math.clamp((long)(this.materialScroll + (var7 > 0.0 ? -1 : 1)), 0, Math.max(0, this.materials.size() - this.materialRows()));
         return true;
      } else if (var1 >= (double)this.leftPos
         && var1 < (double)(this.leftPos + this.listWidth + 20)
         && var3 >= (double)(this.topPos + 64)
         && var3 < (double)(this.topPos + this.imageHeight - 25)) {
         this.page += var7 > 0.0 ? -1 : 1;
         this.rebuild();
         return true;
      } else {
         return super.mouseScrolled(var1, var3, var5, var7);
      }
   }

   public void renderBackground(GuiGraphics var1, int var2, int var3, float var4) {
      this.renderTransparentBackground(var1);
      this.renderBg(var1, var4, var2, var3);
   }

   protected void renderBg(GuiGraphics var1, float var2, int var3, int var4) {
      int var5 = this.leftPos;
      int var6 = this.topPos;
      int var7 = this.imageWidth;
      int var8 = this.imageHeight;
      var1.fill(var5 - 2, var6 - 2, var5 + var7 + 2, var6 + var8 + 2, -15721701);
      var1.fill(var5, var6, var5 + var7, var6 + var8, -14667730);
      var1.fill(var5, var6, var5 + var7, var6 + 2, 0xFF000000 | ((WorkbenchMenu)this.menu).kind.accent);
      var1.fill(var5 + 1, var6 + 3, var5 + var7 - 1, var6 + 31, -13878217);
      var1.fill(var5 + 8, this.tipsMode ? var6 + 36 : var6 + 65, var5 + this.listWidth + 12, var6 + var8 - 29, -15391711);
      int panelTop = this.tipsMode ? 36 : 65; // [bows] the arrow tips tab uses the full height
      var1.fill(var5 + this.listWidth + 20, var6 + panelTop, var5 + var7 - 9, var6 + var8 - 53, -1777715);
      var1.fill(var5 + this.listWidth + 20, var6 + panelTop, var5 + this.listWidth + 23, var6 + var8 - 53, 0xFF000000 | ((WorkbenchMenu)this.menu).kind.accent);
   }

   protected void renderLabels(GuiGraphics var1, int var2, int var3) {
      if (this.tipsMode) {
         this.renderTipsLabels(var1, var2, var3);
      } else {
         var1.drawString(this.font, ((WorkbenchMenu)this.menu).kind.title.toUpperCase(Locale.ROOT), 12, 10, -1119785, false);
         int var4 = this.listWidth + 24;
         int var5 = this.imageWidth - this.listWidth - 36;
         if (this.imageWidth > 420 && ((WorkbenchMenu)this.menu).kind != WorkshopKind.BOWS) {
            var1.drawString(this.font, "FRONTIER  /  FIELD STATIONS", this.imageWidth - 193, 22, -6114660, false);
         }

         String var6 = this.filtered.isEmpty() ? "No matches" : this.page + 1 + " / " + Math.max(1, (this.filtered.size() + this.rows - 1) / this.rows);
         var1.drawCenteredString(this.font, var6, 10 + this.listWidth / 2, this.imageHeight - 20, -4865363);
         if (this.selected < 0) {
            var1.drawWordWrap(
               this.font, Component.literal("No equipment matches. Change the search or turn off Ready only."), var4 + 6, 80, var5 - 12, -12232885
            );
         } else {
            ItemStack var7 = this.outputs.get(this.selected);
            var1.pose().pushPose();
            var1.pose().translate((float)(var4 + 3), 73.0F, 0.0F);
            var1.pose().scale(2.0F, 2.0F, 2.0F);
            var1.renderItem(var7, 0, 0);
            var1.pose().popPose();
            List var8 = this.font.split(var7.getHoverName(), var5 - 44);

            for (int var9 = 0; var9 < Math.min(3, var8.size()); var9++) {
               var1.drawString(this.font, (FormattedCharSequence)var8.get(var9), var4 + 42, 74 + var9 * 10, -14599376, false);
            }

            var1.drawString(this.font, "Output: " + var7.getCount(), var4 + 42, 103, -10128806, false);
            var1.fill(var4, 113, var4 + var5, 114, -4605535);
            var1.drawString(this.font, "MATERIALS", var4 + 2, 118, -8623803, false);
            String var22 = "Have / Need";
            var1.drawString(this.font, var22, var4 + var5 - this.font.width(var22), 118, -8623803, false);
            if (this.materialSelection != this.selected) {
               this.materialSelection = this.selected;
               this.materialScroll = 0;
               ArrayList var10 = new ArrayList();

               for (Ingredient var12 : ((CraftingRecipe)((WorkbenchMenu)this.menu).recipes().get(this.selected).value()).getIngredients()) {
                  if (!var12.isEmpty()) {
                     int var13 = -1;

                     for (int var14 = 0; var14 < var10.size(); var14++) {
                        if (Arrays.equals(((WorkbenchScreen.Material)var10.get(var14)).ingredient.getItems(), var12.getItems())) {
                           var13 = var14;
                           break;
                        }
                     }

                     if (var13 < 0) {
                        for (int var30 = 0; var30 < var10.size(); var30++) {
                           ItemStack[] var15 = ((WorkbenchScreen.Material)var10.get(var30)).ingredient.getItems();
                           ItemStack[] var16 = var12.getItems();
                           boolean var17 = var15.length == var16.length;

                           for (int var18 = 0; var17 && var18 < var15.length; var18++) {
                              var17 = ItemStack.isSameItemSameComponents(var15[var18], var16[var18]);
                           }

                           if (var17) {
                              var13 = var30;
                              break;
                           }
                        }
                     }

                     if (var13 < 0) {
                        var10.add(new WorkbenchScreen.Material(var12, 1));
                     } else {
                        WorkbenchScreen.Material var31 = (WorkbenchScreen.Material)var10.get(var13);
                        var10.set(var13, new WorkbenchScreen.Material(var31.ingredient, var31.amount + 1));
                     }
                  }
               }

               this.materials = var10;
            }

            byte var23 = 18;
            int var24 = this.materialRows();
            this.materialScroll = Math.clamp((long)this.materialScroll, 0, Math.max(0, this.materials.size() - var24));

            for (int var25 = this.materialScroll; var25 < Math.min(this.materials.size(), this.materialScroll + var24); var25++) {
               WorkbenchScreen.Material var28 = this.materials.get(var25);
               ItemStack[] var32 = var28.ingredient.getItems();
               if (var32.length != 0) {
                  ItemStack var33 = Arrays.stream(var32)
                     .filter(var1x -> this.minecraft.player.getInventory().items.stream().anyMatch(var1xx -> ItemStack.isSameItemSameComponents(var1x, var1xx)))
                     .findFirst()
                     .orElse(var32[0]);
                  int var34 = this.minecraft.player.getInventory().items.stream().filter(var28.ingredient::test).mapToInt(ItemStack::getCount).sum();
                  int var35 = 131 + (var25 - this.materialScroll) * var23;
                  float var36 = 1.0F;
                  var1.pose().pushPose();
                  var1.pose().translate((float)(var4 + 1), (float)var35, 0.0F);
                  var1.pose().scale(var36, var36, var36);
                  var1.renderItem(var33, 0, 0);
                  var1.pose().popPose();
                  String var19 = var34 + " / " + var28.amount;
                  int var20 = this.font.width(var19);
                  int var21 = var4 + (var23 < 16 ? 13 : 20);
                  var1.drawString(
                     this.font,
                     this.font.plainSubstrByWidth(var33.getHoverName().getString(), Math.max(12, var5 - (var21 - var4) - var20 - 8)),
                     var21,
                     var35 + 2,
                     -12627647,
                     false
                  );
                  var1.drawString(this.font, var19, var4 + var5 - var20, var35 + 2, var34 >= var28.amount ? -13082304 : -6926015, false);
                  if (var2 - this.leftPos >= var4 && var2 - this.leftPos < var4 + var5 && var3 - this.topPos >= var35 && var3 - this.topPos < var35 + var23) {
                     var1.renderTooltip(this.font, var33, var2 - this.leftPos, var3 - this.topPos);
                  }
               }
            }

            if (this.materials.size() > var24) {
               var1.drawString(
                  this.font,
                  this.materialScroll + 1 + "–" + Math.min(this.materials.size(), this.materialScroll + var24) + " of " + this.materials.size() + " · scroll",
                  var4 + 2,
                  this.imageHeight - 65,
                  -9603490,
                  false
               );
            }

            if (!this.batch.visible && !this.fit.visible) {
               String var26 = ((WorkbenchMenu)this.menu).ready(this.selected) ? "Uses your inventory · one click" : "More materials or free space needed";
               var1.drawString(this.font, this.font.plainSubstrByWidth(var26, var5), var4, this.imageHeight - 18, -4471377, false);
            }

            if (this.fit.visible && this.imageHeight > 270 && this.materials.size() <= var24) {
               ItemStack var27 = this.minecraft.player.getMainHandItem();
               String var29 = var27.isEmpty() ? "Select equipment in your hotbar" : var27.getHoverName().getString();
               var1.drawString(this.font, this.font.plainSubstrByWidth(var29, var5), var4, this.imageHeight - 61, -10391471, false);
            }
         }
      }
   }

   private int materialRows() {
      return Math.max(1, (this.imageHeight - 195) / 18);
   }

   private static record Material(Ingredient ingredient, int amount) {
   }

   private final class RecipeRow extends Button {
      private final int recipe;

      RecipeRow(int nullx, int nullxx, int nullxxx, int nullxxxx, int nullxxxxx) {
         super(nullx, nullxx, nullxxx, nullxxxx, WorkbenchScreen.this.outputs.get(nullxxxxx).getHoverName(), var2 -> {
            WorkbenchScreen.this.selected = nullxxxxx;
            WorkbenchScreen.this.materialSelection = -2;
            WorkbenchScreen.this.updateButtons();
         }, DEFAULT_NARRATION);
         this.recipe = nullxxxxx;
      }

      protected void renderWidget(GuiGraphics var1, int var2, int var3, float var4) {
         int var5 = this.getX();
         int var6 = this.getY();
         boolean var7 = WorkbenchScreen.this.selected == this.recipe;
         var1.fill(var5, var6, var5 + this.width, var6 + this.height, var7 ? -11309992 : (this.isHoveredOrFocused() ? -13349311 : -14468817));
         if (var7) {
            var1.fill(var5, var6, var5 + 2, var6 + this.height, 0xFF000000 | ((WorkbenchMenu)WorkbenchScreen.this.menu).kind.accent);
         }

         var1.renderItem(WorkbenchScreen.this.outputs.get(this.recipe), var5 + 5, var6 + 5);
         var1.renderItemDecorations(WorkbenchScreen.this.font, WorkbenchScreen.this.outputs.get(this.recipe), var5 + 5, var6 + 5);
         List var8 = WorkbenchScreen.this.font.split(WorkbenchScreen.this.outputs.get(this.recipe).getHoverName(), this.width - 39);

         for (int var9 = 0; var9 < Math.min(2, var8.size()); var9++) {
            var1.drawString(
               WorkbenchScreen.this.font, (FormattedCharSequence)var8.get(var9), var5 + 26, var6 + (var8.size() > 1 ? 4 : 9) + var9 * 10, -1251114, false
            );
         }

         var1.fill(
            var5 + this.width - 7,
            var6 + 11,
            var5 + this.width - 4,
            var6 + 14,
            ((WorkbenchMenu)WorkbenchScreen.this.menu).ready(this.recipe) ? -4729454 : -7563648
         );
      }
   }
}
