package com.formaworks.frontierhunts.benches.client;

import com.formaworks.frontierhunts.HuntContent;
import com.formaworks.frontierhunts.benches.BenchCatalog;
import com.formaworks.frontierhunts.benches.BenchCrafting;
import com.formaworks.frontierhunts.benches.BenchMenu;
import com.formaworks.frontierhunts.client.FrontierUi;
import com.formaworks.frontierhunts.hunting.ArrowTip;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * [benches] The Reloading Bench "Fit arrowheads" tab ([smalls] renamed from "Refit arrows", with a one-line explanation): your arrow stacks on the left (shaft, the head they carry, how
 * many); on the paper card the heads you can fit (how many of each you carry; the last cell takes fitted heads off),
 * the current head against the new one (penetration, blood trail, damage, recovery), what the refit will do and the
 * buttons: Refit 1, Heads off, and Refit all (Enter). Missing heads are made from your materials with the bench's head
 * recipe; the old heads come back. Plans are recomputed only when the inventory or the choice changes; the server
 * recomputes them on every click ({@link BenchCrafting#refit}).
 */
final class BenchRefitPanel {
   static final int R_TIP = 110, R_ONE = 111, R_ALL = 112, R_STRIP = 113;
   private static final ArrowTip[] TIPS = ArrowTip.values();
   private static final int ROW_H = 30, ROW_GAP = 4, CELL = 27, CELL_GAP = 3, CELLS_PER_ROW = 6;

   private final BenchScreen s;
   private final List<Integer> slots = new ArrayList<>();
   private int slot = -1;
   private int code = ArrowTip.FIXED_BROADHEAD.ordinal();
   private final int[] owned = new int[TIPS.length];
   private int countOne, countAll, strip;
   private boolean canOne;
   private String status = "";
   private boolean statusOk;
   private float scroll;
   private final List<String> summary = new ArrayList<>();

   BenchRefitPanel(BenchScreen s) {
      this.s = s;
   }

   private static Minecraft mc() {
      return Minecraft.getInstance();
   }

   String badge() {
      return this.slots.isEmpty() ? "" : Integer.toString(this.slots.size());
   }

   private ItemStack stack(int slot) {
      List<ItemStack> items = mc().player.getInventory().items;
      return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
   }

   private static ArrowTip current(ItemStack st) {
      return st.isEmpty() || st.is(Items.ARROW) ? null : ArrowTip.of(st);
   }

   private static ArrowTip stockTip(ItemStack st) {
      return ArrowTip.primitiveShaft(st) ? ArrowTip.FLINT_POINT : ArrowTip.FIXED_BROADHEAD;
   }

   private ArrowTip target() {
      return this.code == BenchCrafting.REFIT_STOCK ? stockTip(this.stack(this.slot)) : TIPS[Math.max(0, Math.min(TIPS.length - 1, this.code))];
   }

   private int cellCode(int i) {
      return i < TIPS.length ? i : BenchCrafting.REFIT_STOCK;
   }

   private BenchCatalog.Entry headRecipe() {
      return this.code < TIPS.length ? this.s.getMenu().headRecipe(TIPS[this.code]) : null;
   }

   // ============================================================================================ state

   void refresh() {
      Player p = mc().player;
      if (p == null) {
         return;
      }
      List<ItemStack> items = p.getInventory().items;
      this.slots.clear();
      int best = -1;
      for (int i = 0; i < Math.min(BenchCrafting.REFIT_SLOTS, items.size()); i++) {
         if (BenchCrafting.refittable(items.get(i))) {
            this.slots.add(i);
            if (best < 0 || items.get(i).getCount() > items.get(best).getCount()) {
               best = i;
            }
         }
      }
      if (!this.slots.contains(this.slot)) {
         this.slot = best;
      }
      for (ArrowTip t : TIPS) {
         this.owned[t.ordinal()] = BenchCrafting.heads(items, t);
      }
      this.recompute();
   }

   private void recompute() {
      Player p = mc().player;
      List<ItemStack> items = p.getInventory().items;
      boolean stock = this.code == BenchCrafting.REFIT_STOCK;
      BenchCatalog.Entry recipe = this.headRecipe();
      boolean any = this.slot >= 0;
      this.canOne = any && !stock && BenchCrafting.refit(p, items, this.slot, this.code, false, recipe) != null;
      this.countAll = any && !stock ? BenchCrafting.refitCount(p, items, this.slot, this.code, true, recipe) : 0;
      this.countOne = this.canOne ? 1 : 0;
      this.strip = any ? BenchCrafting.refitCount(p, items, this.slot, BenchCrafting.REFIT_STOCK, true, null) : 0;
      ItemStack arrows = this.stack(this.slot);
      ArrowTip now = current(arrows);
      ArrowTip next = this.target();
      // status
      if (!any) {
         this.set(BenchScreen.tr("bench.frontierhunts.refit.pick"), false);
      } else if (stock) {
         this.set(this.strip > 0 ? BenchScreen.tr("bench.frontierhunts.refit.status_stock") : BenchScreen.tr("bench.frontierhunts.refit.status_stock_none"),
            this.strip > 0);
      } else if (now == next && !arrows.is((Item)HuntContent.TRACER_ARROW.get())) {
         this.set(BenchScreen.tr("bench.frontierhunts.refit.same"), false);
      } else if (p.hasInfiniteMaterials()) {
         this.set(BenchScreen.tr("bench.frontierhunts.refit.creative"), true);
      } else if (this.countAll <= 0) {
         this.set(this.owned[next.ordinal()] > 0 ? BenchScreen.tr("bench.frontierhunts.refit.status_full") : BenchScreen.tr("bench.frontierhunts.refit.status_none"),
            false);
      } else {
         int have = this.owned[next.ordinal()];
         int made = Math.max(0, this.countAll - have);
         this.set(made > 0 ? BenchScreen.tr("bench.frontierhunts.refit.status_make", have, made) : BenchScreen.tr("bench.frontierhunts.refit.status_have", have),
            true);
      }
      this.summary.clear();
      String text = stock ? BenchScreen.tr("bench.frontierhunts.refit.stock_hint") : next.summary();
      for (FormattedCharSequence seq : this.s.uiFont().split(FrontierUi.c(text, FrontierUi.Size.SMALL), BenchScreen.DETAIL_W - 20)) {
         StringBuilder b = new StringBuilder();
         seq.accept((idx, style, cp) -> {
            b.appendCodePoint(cp);
            return true;
         });
         this.summary.add(b.toString());
      }
   }

   private void set(String s, boolean ok) {
      this.status = s;
      this.statusOk = ok;
   }

   // ============================================================================================ layout / input

   private float cellX(int i) {
      return this.s.detX + 10 + (i % CELLS_PER_ROW) * (CELL + CELL_GAP);
   }

   private float cellY(int i) {
      return this.s.bodyY + 21 + (i / CELLS_PER_ROW) * (CELL + CELL_GAP);
   }

   void layout() {
      for (int i = 0; i <= TIPS.length; i++) {
         this.s.hots.add(new BenchScreen.Hot(this.cellX(i), this.cellY(i), CELL, CELL, R_TIP, this.cellCode(i)));
      }
      float by = this.s.bodyY + this.s.bodyH - 10 - 22;
      float x = this.s.detX + 10, w = BenchScreen.DETAIL_W - 20;
      float half = (w - 6) / 2.0F;
      this.s.hots.add(new BenchScreen.Hot(x, by - 24, half, 18, R_ONE, 0));
      this.s.hots.add(new BenchScreen.Hot(x + half + 6, by - 24, w - half - 6, 18, R_STRIP, 0));
      this.s.hots.add(new BenchScreen.Hot(x, by, w, 22, R_ALL, 0));
   }

   /** [smalls] room for the one-line explanation under the title */
   private static final int EXPLAIN_H = 11;

   private float listTop() {
      return this.s.bodyY + BenchScreen.GRID_TOP + EXPLAIN_H;
   }

   private float listView() {
      return this.s.bodyH - BenchScreen.GRID_TOP - EXPLAIN_H - 4;
   }

   boolean click(double mx, double my) {
      for (BenchScreen.Hot h : this.s.hots) {
         if (h.kind() < R_TIP || h.kind() > R_STRIP || !h.in(mx, my)) {
            continue;
         }
         switch (h.kind()) {
            case R_TIP -> {
               if (this.code != h.data()) {
                  this.code = h.data();
                  this.s.click();
                  this.recompute();
               }
            }
            case R_ONE -> {
               if (this.code != BenchCrafting.REFIT_STOCK && this.canOne) {
                  this.s.send(BenchMenu.refitButton(this.slot, this.code, false));
               }
            }
            case R_STRIP -> {
               if (this.strip > 0) {
                  this.s.send(BenchMenu.refitButton(this.slot, BenchCrafting.REFIT_STOCK, true));
               }
            }
            case R_ALL -> this.primary();
            default -> {
            }
         }
         return true;
      }
      // the arrow list
      float top = this.listTop();
      if (BenchScreen.in(mx, my, this.s.gridX + 8, top, this.s.gridW - 16, this.listView())) {
         int i = (int)((my - top + this.scroll) / (ROW_H + ROW_GAP));
         if (i >= 0 && i < this.slots.size() && (my - top + this.scroll) - i * (ROW_H + ROW_GAP) < ROW_H) {
            if (this.slots.get(i) != this.slot) {
               this.slot = this.slots.get(i);
               this.s.click();
               this.recompute();
            }
            return true;
         }
      }
      return false;
   }

   /** Enter / the big button: refit the whole stack (or take the heads off when the stock cell is chosen). */
   void primary() {
      if (this.slot < 0) {
         return;
      }
      if (this.code == BenchCrafting.REFIT_STOCK) {
         if (this.strip > 0) {
            this.s.send(BenchMenu.refitButton(this.slot, BenchCrafting.REFIT_STOCK, true));
         }
      } else if (this.countAll > 0) {
         this.s.send(BenchMenu.refitButton(this.slot, this.code, true));
      }
   }

   void arrowKey(int key) {
      if (key == InputConstants.KEY_UP || key == InputConstants.KEY_DOWN) {
         int i = this.slots.indexOf(this.slot);
         int n = this.slots.size();
         if (n > 0) {
            i = Math.max(0, Math.min(n - 1, i + (key == InputConstants.KEY_UP ? -1 : 1)));
            this.slot = this.slots.get(i);
            float y = i * (ROW_H + ROW_GAP);
            if (y < this.scroll) {
               this.scroll = y;
            } else if (y + ROW_H > this.scroll + this.listView()) {
               this.scroll = y + ROW_H - this.listView();
            }
            this.recompute();
         }
      } else {
         int at = this.code == BenchCrafting.REFIT_STOCK ? TIPS.length : this.code;
         at = Math.max(0, Math.min(TIPS.length, at + (key == InputConstants.KEY_LEFT ? -1 : 1)));
         this.code = this.cellCode(at);
         this.recompute();
      }
   }

   boolean scrolled(double mx, double my, double sy) {
      float max = Math.max(0.0F, this.slots.size() * (ROW_H + ROW_GAP) - this.listView());
      this.scroll = Math.max(0.0F, Math.min(max, this.scroll - (float)sy * 20.0F));
      return true;
   }

   // ============================================================================================ drawing

   void draw(GuiGraphics g, int mx, int my) {
      int x = this.s.gridX, y = this.s.bodyY, w = this.s.gridW, h = this.s.bodyH;
      int dx = this.s.detX, dw = BenchScreen.DETAIL_W;
      int accent = this.s.bench.accent;
      float top = this.listTop(), view = this.listView();
      ItemStack arrows = this.stack(this.slot);
      ArrowTip now = current(arrows);
      ArrowTip next = this.target();
      boolean stock = this.code == BenchCrafting.REFIT_STOCK;
      FrontierUi.batch(g, () -> {
         BenchDraw.framed(g, x, y, w, h, 6.0F, BenchDraw.INSET_EDGE, 1.0F, BenchDraw.INSET);
         String title = BenchScreen.tr("bench.frontierhunts.refit.arrows");
         float tw = FrontierUi.text(g, title, x + 9, y + 7, BenchDraw.TEXT, FrontierUi.Size.STRONG) - (x + 9);
         FrontierUi.text(g, BenchScreen.tr("bench.frontierhunts.refit.arrows_hint"), x + 9 + tw + 7, y + 8.5F, BenchDraw.DIM, FrontierUi.Size.SMALL);
         // [smalls] what this tab is for, in one line
         FrontierUi.text(g, FrontierUi.fit(BenchScreen.tr("bench.frontierhunts.refit.explain"), w - 18, FrontierUi.Size.SMALL), x + 9, y + 21,
            BenchDraw.mix(BenchDraw.MUTED, BenchDraw.TEXT, 0.25F), FrontierUi.Size.SMALL);
         if (this.slots.isEmpty()) {
            int ty = (int)top + 30;
            ty += FrontierUi.wrap(g, BenchScreen.tr("bench.frontierhunts.refit.none"), x + 20, ty, w - 40, BenchDraw.TEXT, FrontierUi.Size.STRONG) + 4;
            FrontierUi.wrap(g, BenchScreen.tr("bench.frontierhunts.refit.none_hint"), x + 20, ty, w - 40, BenchDraw.MUTED, FrontierUi.Size.SMALL);
         }
         // the paper card
         BenchDraw.framed(g, dx, y, dw, h, 6.0F, BenchDraw.PAPER_EDGE, 1.0F, BenchDraw.PAPER);
         FrontierUi.text(g, BenchScreen.tr("bench.frontierhunts.refit.new_head"), dx + 10, y + 9, BenchDraw.GOLD_DARK, FrontierUi.Size.SMALL);
         FrontierUi.right(g, BenchScreen.tr("bench.frontierhunts.refit.owned"), dx + dw - 10, y + 9, BenchDraw.INK_MUTED, FrontierUi.Size.SMALL);
         for (int i = 0; i <= TIPS.length; i++) {
            int c = this.cellCode(i);
            float cx = this.cellX(i), cy = this.cellY(i);
            boolean sel = c == this.code;
            boolean over = BenchScreen.in(mx, my, cx, cy, CELL, CELL);
            if (sel) {
               FrontierUi.rect(g, cx - 1.5F, cy - 1.5F, CELL + 3, CELL + 3, 5.5F, accent);
            }
            FrontierUi.rect(g, cx, cy, CELL, CELL, 4.0F, sel ? 0xFF2A241C : (over ? 0xFFE9DFC8 : 0xFFDCD1B8));
            if (c == BenchCrafting.REFIT_STOCK) {
               // "take off" arrow: a circular arrow drawn as a ring and a head
               FrontierUi.circle(g, cx + CELL / 2.0F, cy + CELL / 2.0F, 6.0F, sel ? BenchDraw.TEXT : BenchDraw.INK);
               FrontierUi.circle(g, cx + CELL / 2.0F, cy + CELL / 2.0F, 4.6F, sel ? 0xFF2A241C : (over ? 0xFFE9DFC8 : 0xFFDCD1B8));
               FrontierUi.rect(g, cx + CELL / 2.0F + 1, cy + CELL / 2.0F - 7, 6, 6, 0.0F, sel ? 0xFF2A241C : (over ? 0xFFE9DFC8 : 0xFFDCD1B8));
               BenchDraw.line(g, cx + CELL / 2.0F + 0.5F, cy + CELL / 2.0F - 6.0F, cx + CELL / 2.0F + 3.5F, cy + CELL / 2.0F - 8.0F, 1.2F,
                  sel ? BenchDraw.TEXT : BenchDraw.INK);
               BenchDraw.line(g, cx + CELL / 2.0F + 0.5F, cy + CELL / 2.0F - 6.0F, cx + CELL / 2.0F + 3.0F, cy + CELL / 2.0F - 3.5F, 1.2F,
                  sel ? BenchDraw.TEXT : BenchDraw.INK);
               continue;
            }
            ArrowTip t = TIPS[c];
            if (t.tracer()) {
               FrontierUi.rect(g, cx + 3, cy + CELL - 4, CELL - 6, 2, 1.0F, 0xFF000000 | t.glow);
            }
         }
         float sy = this.cellY(TIPS.length) + CELL + 6;
         FrontierUi.rect(g, dx + 10, sy, dw - 20, 1, 0.0F, BenchDraw.PAPER_RULE);
         sy += 6;
         // current -> new
         String a = arrows.isEmpty() ? "-" : (now == null ? BenchScreen.tr("bench.frontierhunts.refit.plain") : now.title);
         String b = stock ? BenchScreen.tr("bench.frontierhunts.refit.stock") + ": " + next.title : next.title;
         FrontierUi.text(g, FrontierUi.fit(a, 56, FrontierUi.Size.SMALL), dx + 29, sy + 4.5F, BenchDraw.INK, FrontierUi.Size.SMALL);
         FrontierUi.text(g, "→", dx + 88, sy + 3.5F, BenchDraw.GOLD_DARK, FrontierUi.Size.STRONG);
         FrontierUi.text(g, FrontierUi.fit(b, dw - 128, FrontierUi.Size.STRONG), dx + 118, sy + 4.0F, BenchDraw.INK, FrontierUi.Size.STRONG);
         sy += 22;
         sy = this.bar(g, dx, sy, dw, "bench.frontierhunts.refit.penetration", now == null ? -1 : now.penetration, next.penetration, 1.25F, 0xFF5E7E9A);
         sy = this.bar(g, dx, sy, dw, "bench.frontierhunts.refit.bleed", now == null ? -1 : now.bleed, next.bleed, 1.4F, 0xFFA2412F);
         sy = this.bar(g, dx, sy, dw, "bench.frontierhunts.refit.damage", now == null ? -1 : now.damage, next.damage, 1.1F, 0xFF8F6E2F);
         sy = this.bar(g, dx, sy, dw, "bench.frontierhunts.refit.recovery", now == null ? -1 : now.recovery, next.recovery, 1.0F, 0xFF6C7F57);
         sy += 2;
         float by = y + h - 10 - 22;
         float statusY = by - 24 - 14;
         for (String line : this.summary) {
            if (sy + 9 > statusY - 2) {
               break;
            }
            FrontierUi.text(g, line, dx + 10, sy, BenchDraw.INK_MUTED, FrontierUi.Size.SMALL);
            sy += 9;
         }
         int sc = this.statusOk ? BenchDraw.GREEN_INK : BenchDraw.RED_INK;
         if (this.statusOk) {
            BenchDraw.check(g, dx + 14, statusY + 3.5F, sc, 0.8F);
         } else {
            BenchDraw.warn(g, dx + 14, statusY + 4, sc, BenchDraw.PAPER);
         }
         FrontierUi.text(g, FrontierUi.fit(this.status, dw - 34, FrontierUi.Size.SMALL), dx + 21, statusY + 0.5F, sc, FrontierUi.Size.SMALL);
         for (BenchScreen.Hot hz : this.s.hots) {
            boolean over = hz.in(mx, my);
            switch (hz.kind()) {
               case R_ONE -> BenchDraw.button(g, hz.x(), hz.y(), hz.w(), hz.h(), BenchScreen.tr("bench.frontierhunts.refit.one"), BenchDraw.Look.PAPER, accent,
                  !stock && this.canOne, over, null);
               case R_STRIP -> BenchDraw.button(g, hz.x(), hz.y(), hz.w(), hz.h(), this.strip > 0 ? BenchScreen.tr("bench.frontierhunts.refit.heads_off", this.strip)
                  : BenchScreen.tr("bench.frontierhunts.refit.heads_off0"), BenchDraw.Look.PAPER, accent, this.strip > 0, over, null);
               case R_ALL -> {
                  String label = stock ? (this.strip > 0 ? BenchScreen.tr("bench.frontierhunts.refit.heads_off", this.strip)
                     : BenchScreen.tr("bench.frontierhunts.refit.heads_off0"))
                     : (this.countAll > 0 ? BenchScreen.tr("bench.frontierhunts.refit.all", this.countAll) : BenchScreen.tr("bench.frontierhunts.refit.all0"));
                  BenchDraw.button(g, hz.x(), hz.y(), hz.w(), hz.h(), label, BenchDraw.Look.PRIMARY, accent, stock ? this.strip > 0 : this.countAll > 0, over, "Enter");
               }
               default -> {
               }
            }
         }
      });
      // the arrow list (scrolls)
      g.enableScissor(x + 1, (int)top - 1, x + w - 1, (int)(top + view));
      FrontierUi.batch(g, () -> {
         for (int i = 0; i < this.slots.size(); i++) {
            float ry = top + i * (ROW_H + ROW_GAP) - this.scroll;
            if (ry > top + view || ry + ROW_H < top) {
               continue;
            }
            int sl = this.slots.get(i);
            ItemStack st = this.stack(sl);
            boolean sel = sl == this.slot;
            boolean over = BenchScreen.in(mx, my, x + 8, ry, w - 16, ROW_H);
            if (sel) {
               FrontierUi.rect(g, x + 6.5F, ry - 1.5F, w - 13, ROW_H + 3, 6.5F, accent);
            }
            FrontierUi.rect(g, x + 8, ry, w - 16, ROW_H, 5.0F, sel ? BenchDraw.mix(BenchDraw.TILE_SEL, accent, 0.08F) : (over ? BenchDraw.TILE_HOT : BenchDraw.TILE));
            String shaft = st.is(Items.ARROW) ? BenchScreen.tr("bench.frontierhunts.refit.vanilla")
               : (ArrowTip.primitiveShaft(st) ? BenchScreen.tr("bench.frontierhunts.refit.primitive") : BenchScreen.tr("bench.frontierhunts.refit.hunting"));
            ArrowTip t = current(st);
            FrontierUi.text(g, FrontierUi.fit(shaft, w - 90, FrontierUi.Size.STRONG), x + 38, ry + 6, BenchDraw.TEXT, FrontierUi.Size.STRONG);
            FrontierUi.text(g, FrontierUi.fit(t == null ? BenchScreen.tr("bench.frontierhunts.refit.plain") : t.title, w - 90, FrontierUi.Size.SMALL), x + 38,
               ry + 17, t != null && t.tracer() ? 0xFF000000 | t.glow : BenchDraw.mix(BenchDraw.MUTED, BenchDraw.TEXT, 0.2F), FrontierUi.Size.SMALL);
            FrontierUi.right(g, "×" + st.getCount(), x + w - 16, ry + 11, BenchDraw.TEXT, FrontierUi.Size.STRONG);
         }
      });
      for (int i = 0; i < this.slots.size(); i++) {
         float ry = top + i * (ROW_H + ROW_GAP) - this.scroll;
         if (ry > top + view || ry + ROW_H < top) {
            continue;
         }
         BenchScreen.icon(g, this.stack(this.slots.get(i)), x + 14, ry + 7, 1.0F);
      }
      g.disableScissor();
      // head icons and their counts
      for (int i = 0; i < TIPS.length; i++) {
         BenchScreen.icon(g, TIPS[i].tipItem(1), this.cellX(i) + 5.5F, this.cellY(i) + 5.5F, 1.0F);
      }
      float sy = this.cellY(TIPS.length) + CELL + 12;
      if (!arrows.isEmpty()) {
         BenchScreen.icon(g, now == null ? arrows : now.tipItem(1), dx + 10, sy, 1.0F);
      }
      BenchScreen.icon(g, next.tipItem(1), dx + 98, sy, 1.0F);
      g.pose().pushPose();
      g.pose().translate(0.0F, 0.0F, 250.0F);
      FrontierUi.batch(g, () -> {
         for (int i = 0; i < TIPS.length; i++) {
            float cx = this.cellX(i), cy = this.cellY(i);
            boolean sel = i == this.code;
            if (this.owned[i] > 0) {
               String n = this.owned[i] > 99 ? "99+" : Integer.toString(this.owned[i]);
               FrontierUi.right(g, n, cx + CELL - 1.5F, cy + CELL - 10, sel ? BenchDraw.TEXT : BenchDraw.INK, FrontierUi.Size.SMALL);
            }
            if (now == TIPS[i] && !arrows.isEmpty()) {
               FrontierUi.circle(g, cx + 4, cy + 4, 2.2F, BenchDraw.GREEN_INK); // the head these arrows carry now
            }
         }
      });
      g.pose().popPose();
   }

   /** One comparison row: the bar is the new head, the dark tick the head on the arrows now, and the change. */
   private float bar(GuiGraphics g, float dx, float y, float dw, String key, float now, float next, float max, int color) {
      FrontierUi.text(g, BenchScreen.tr(key), dx + 10, y, BenchDraw.INK_MUTED, FrontierUi.Size.SMALL);
      float bx = dx + 64, bw = dw - 64 - 40;
      FrontierUi.rect(g, bx, y + 1.5F, bw, 5, 2.5F, 0xFFD8CCB0);
      FrontierUi.rect(g, bx, y + 1.5F, Math.max(2.5F, bw * Math.min(1.0F, next / max)), 5, 2.5F, color);
      if (now >= 0.0F) {
         float t = bx + bw * Math.min(1.0F, now / max);
         FrontierUi.rect(g, t - 0.75F, y - 0.5F, 1.5F, 9, 0.5F, BenchDraw.INK);
         int pct = now <= 0.0F ? (next > 0.0F ? 100 : 0) : Math.round((next / now - 1.0F) * 100.0F);
         String d = pct == 0 ? "=" : (pct > 0 ? "+" + pct + "%" : pct + "%");
         FrontierUi.right(g, d, dx + dw - 10, y, pct > 0 ? BenchDraw.GREEN_INK : (pct < 0 ? BenchDraw.RED_INK : BenchDraw.INK_MUTED), FrontierUi.Size.SMALL);
      }
      return y + 12;
   }

   void tooltip(GuiGraphics g, int mx, int my) {
      for (int i = 0; i <= TIPS.length; i++) {
         if (BenchScreen.in(mx, my, this.cellX(i), this.cellY(i), CELL, CELL)) {
            if (i == TIPS.length) {
               g.renderComponentTooltip(this.s.uiFont(), List.of(Component.literal(BenchScreen.tr("bench.frontierhunts.refit.stock")),
                  Component.literal(BenchScreen.tr("bench.frontierhunts.refit.stock_hint")).withStyle(ChatFormatting.GRAY)), mx, my);
            } else {
               g.renderTooltip(this.s.uiFont(), TIPS[i].tipItem(1), mx, my);
            }
            return;
         }
      }
      float top = this.listTop();
      if (BenchScreen.in(mx, my, this.s.gridX + 8, top, this.s.gridW - 16, this.listView())) {
         int i = (int)((my - top + this.scroll) / (ROW_H + ROW_GAP));
         if (i >= 0 && i < this.slots.size()) {
            ItemStack st = this.stack(this.slots.get(i));
            if (!st.isEmpty()) {
               g.renderTooltip(this.s.uiFont(), st, mx, my);
            }
         }
      }
   }
}
