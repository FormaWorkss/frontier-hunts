package com.formaworks.frontierhunts.benches.client;

import com.formaworks.frontierhunts.benches.BenchCrafting;
import com.formaworks.frontierhunts.benches.BenchMenu;
import com.formaworks.frontierhunts.client.FrontierUi;
import com.formaworks.frontierhunts.expedition.AttachmentSpec;
import com.formaworks.frontierhunts.expedition.ExpeditionWeapon;
import com.formaworks.frontierhunts.workshop.AttachmentFitting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * [benches] The Gunsmith's Bench "Fit" tab: the weapon in your main hand, big, on a paper card with what is fitted, and
 * one row per slot it has (optic, muzzle, magazine, grip, stock, bipod; the reel of a fishing rod) with the fitted part,
 * a Remove button and every part that fits as a chip (how many you carry; click to fit or swap; a part you don't have
 * jumps to its recipe on the Attachments tab). A hotbar strip on top picks which weapon you hold. Everything is
 * recomputed only when the inventory or the held slot changes; the server re-checks every click
 * ({@link BenchCrafting#fit}).
 */
final class BenchFitPanel {
   static final int F_HOTBAR = 100;
   private static final int SHOW_W = 176, CHIP = 22, CHIP_GAP = 2;
   /** slot key -> the parts that go there, in chip order */
   private static final String[][] SLOTS = {
      {"optic", "ridgeline_scope", "four_power_optic", "reflex_sight", "micro_red_dot", "holographic_sight", "two_power_prism", "six_power_scope",
         "eight_power_scope", "twelve_power_scope", "thermal_scope"},
      {"muzzle", "suppressor", "muzzle_brake"},
      {"magazine", "sniper_magazine", "pistol_magazine", "extended_magazine"},
      {"grip", "angled_foregrip"},
      {"stock", "steady_stock"},
      {"bipod", "bipod"},
      {"reel", "fishing_drag_kit"}
   };

   record Chip(String part, ItemStack icon, String name, int owned, boolean fitted, boolean canFit) {
   }

   record SlotRow(String key, String label, String fitted, ItemStack fittedIcon, String fittedName, boolean canRemove, List<Chip> chips, int y, int h) {
   }

   private final BenchScreen s;
   private ItemStack held = ItemStack.EMPTY;
   private int heldSlot = -1;
   private final List<SlotRow> rows = new ArrayList<>();
   private final List<String> info = new ArrayList<>();
   private final List<String> facts = new ArrayList<>();
   private final ItemStack[] hotbar = new ItemStack[9];
   private final boolean[] fittable = new boolean[9];
   private boolean reloading;
   private int contentH, perRow = 9;
   private float scroll;
   private int fittedCount;

   BenchFitPanel(BenchScreen s) {
      this.s = s;
      java.util.Arrays.fill(this.hotbar, ItemStack.EMPTY);
   }

   private static net.minecraft.client.Minecraft mc() {
      return net.minecraft.client.Minecraft.getInstance();
   }

   String badge() {
      return this.fittedCount > 0 ? Integer.toString(this.fittedCount) : "";
   }

   private int rowsX() {
      return this.s.gridX + 8 + SHOW_W + 8;
   }

   private int rowsW() {
      return this.s.gridX + this.s.wideW - 8 - this.rowsX();
   }

   private int rowsTop() {
      return this.s.bodyY + 30;
   }

   private int rowsView() {
      return this.s.bodyH - 30 - 18;
   }

   void layout() {
      int x0 = this.s.gridX + this.s.wideW - 9 - 9 * (20 + 2) + 2;
      for (int i = 0; i < 9; i++) {
         this.s.hots.add(new BenchScreen.Hot(x0 + i * 22, this.s.bodyY + 4, 20, 20, F_HOTBAR, i));
      }
   }

   /** Inventory or held slot changed. */
   void refresh() {
      if (mc().player == null) {
         return;
      }
      Inventory inv = mc().player.getInventory();
      this.heldSlot = inv.selected;
      this.held = inv.getSelected();
      for (int i = 0; i < 9; i++) {
         this.hotbar[i] = inv.items.get(i);
         this.fittable[i] = fittable(this.hotbar[i]);
      }
      this.rows.clear();
      this.info.clear();
      this.facts.clear();
      this.fittedCount = 0;
      this.reloading = this.held.getItem() instanceof ExpeditionWeapon && ExpeditionWeapon.data(this.held).getLong("reload_until") != 0L;
      this.perRow = Math.max(1, (this.rowsW() - 16 + CHIP_GAP) / (CHIP + CHIP_GAP));
      int y = 0;
      if (fittable(this.held)) {
         for (String[] slot : SLOTS) {
            List<Chip> chips = new ArrayList<>();
            String fitted = null;
            for (int i = 1; i < slot.length; i++) {
               String part = slot[i];
               if (!AttachmentFitting.supported(this.held, part) || !this.s.getMenu().fits(part)) {
                  continue;
               }
               boolean on = AttachmentFitting.fitted(this.held, part);
               if (on && fitted == null) {
                  fitted = part;
               }
               Item item = AttachmentFitting.part(part);
               int owned = 0;
               for (ItemStack st : inv.items) {
                  if (st.is(item)) {
                     owned += st.getCount();
                  }
               }
               boolean can = !on && owned > 0 && BenchCrafting.fit(inv, part, true) != null;
               chips.add(new Chip(part, new ItemStack(item), new ItemStack(item).getHoverName().getString(), owned, on, can));
            }
            if (chips.isEmpty()) {
               continue;
            }
            String label = BenchScreen.tr("bench.frontierhunts.fit.slot." + slot[0]);
            ItemStack fi = fitted == null ? ItemStack.EMPTY : new ItemStack(AttachmentFitting.part(fitted));
            String fn = fitted == null ? null : fi.getHoverName().getString();
            boolean canRemove = fitted != null && BenchCrafting.fit(inv, fitted, false) != null;
            int lines = (chips.size() + this.perRow - 1) / this.perRow;
            int h = 22 + lines * (CHIP + 2) + 6;
            this.rows.add(new SlotRow(slot[0], label, fitted, fi, fn, canRemove, List.copyOf(chips), y, h));
            y += h + 6;
            if (fitted != null) {
               this.fittedCount++;
            }
            String shownFit = fn != null ? fn : BenchScreen.tr(slot[0].equals("magazine") ? "bench.frontierhunts.fit.standard" : "bench.frontierhunts.fit.empty");
            this.facts.add(label + "\u0000" + shownFit);
         }
      }
      this.contentH = y;
      this.scroll = Math.max(0.0F, Math.min(this.scroll, Math.max(0, this.contentH - this.rowsView())));
      // the held item's own tooltip under the facts
      if (!this.held.isEmpty()) {
         try {
            List<Component> lines = this.held.getTooltipLines(Item.TooltipContext.of(mc().level), mc().player, TooltipFlag.NORMAL);
            int budget = 6;
            for (int i = 1; i < lines.size() && budget > 0; i++) {
               String t = ChatFormatting.stripFormatting(lines.get(i).getString()).strip();
               if (t.isEmpty()) {
                  continue;
               }
               for (FormattedCharSequence seq : this.s.uiFont().split(FrontierUi.c(t, FrontierUi.Size.SMALL), SHOW_W - 22)) {
                  if (budget-- <= 0) {
                     break;
                  }
                  StringBuilder b = new StringBuilder();
                  seq.accept((idx, style, cp) -> {
                     b.appendCodePoint(cp);
                     return true;
                  });
                  this.info.add(b.toString());
               }
            }
         } catch (RuntimeException ignored) {
            // the card works without the tooltip
         }
      }
   }

   /** Has this stack any slot a part fits (guns, the Ridgeline, a fishing rod)? */
   private boolean fittable(ItemStack st) {
      if (st.isEmpty()) {
         return false;
      }
      for (String[] slot : SLOTS) {
         for (int i = 1; i < slot.length; i++) {
            if (AttachmentFitting.supported(st, slot[i]) && this.s.getMenu().fits(slot[i])) {
               return true;
            }
         }
      }
      return false;
   }

   // ============================================================================================ input

   boolean click(double mx, double my) {
      for (BenchScreen.Hot h : this.s.hots) {
         if (h.kind() == F_HOTBAR && h.in(mx, my)) {
            if (h.data() != this.heldSlot) {
               Inventory inv = mc().player.getInventory();
               inv.selected = h.data();
               mc().getConnection().send(new ServerboundSetCarriedItemPacket(h.data()));
               this.s.click();
               this.refresh();
            }
            return true;
         }
      }
      int rx = this.rowsX(), rw = this.rowsW(), top = this.rowsTop();
      if (!BenchScreen.in(mx, my, rx, top, rw, this.rowsView())) {
         return false;
      }
      for (SlotRow r : this.rows) {
         float ry = top + r.y() - this.scroll;
         if (r.fitted() != null && BenchScreen.in(mx, my, rx + rw - 8 - 58, ry + 3, 58, 16)) {
            if (r.canRemove() && !this.reloading) {
               this.s.click();
               this.s.send(BenchMenu.fitButton(r.fitted(), false));
            }
            return true;
         }
         for (int i = 0; i < r.chips().size(); i++) {
            float cx = rx + 8 + (i % this.perRow) * (CHIP + CHIP_GAP);
            float cy = ry + 22 + (i / this.perRow) * (CHIP + 2);
            if (BenchScreen.in(mx, my, cx, cy, CHIP, CHIP)) {
               Chip c = r.chips().get(i);
               if (c.fitted()) {
                  return true;
               }
               if (c.owned() <= 0) {
                  this.s.click();
                  this.s.showRecipe(c.icon().getItem());
               } else if (c.canFit() && !this.reloading) {
                  this.s.click();
                  this.s.send(BenchMenu.fitButton(c.part(), true));
               }
               return true;
            }
         }
      }
      return true;
   }

   boolean scrolled(double mx, double my, double sy) {
      if (BenchScreen.in(mx, my, this.rowsX(), this.rowsTop(), this.rowsW(), this.rowsView())) {
         this.scroll = Math.max(0.0F, Math.min(Math.max(0, this.contentH - this.rowsView()), this.scroll - (float)sy * 24.0F));
      }
      return true;
   }

   // ============================================================================================ drawing

   void draw(GuiGraphics g, int mx, int my) {
      int x = this.s.gridX, y = this.s.bodyY, w = this.s.wideW, h = this.s.bodyH;
      int accent = this.s.bench.accent;
      int cardY = y + 30, cardH = h - 38;
      int rx = this.rowsX(), rw = this.rowsW(), top = this.rowsTop(), view = this.rowsView();
      boolean has = !this.rows.isEmpty();
      FrontierUi.batch(g, () -> {
         BenchDraw.framed(g, x, y, w, h, 6.0F, BenchDraw.INSET_EDGE, 1.0F, BenchDraw.INSET);
         String title = BenchScreen.tr("bench.frontierhunts.tab.fit");
         float tw = FrontierUi.text(g, title, x + 9, y + 7, BenchDraw.TEXT, FrontierUi.Size.STRONG) - (x + 9);
         FrontierUi.text(g, BenchScreen.tr("bench.frontierhunts.tab.fit.hint"), x + 9 + tw + 7, y + 8.5F, BenchDraw.DIM, FrontierUi.Size.SMALL);
         // hotbar picker
         float hx = x + w - 9 - 9 * 22 + 2;
         FrontierUi.right(g, BenchScreen.tr("bench.frontierhunts.fit.hotbar"), hx - 6, y + 9, BenchDraw.DIM, FrontierUi.Size.SMALL);
         for (int i = 0; i < 9; i++) {
            float sx = hx + i * 22;
            boolean sel = i == this.heldSlot;
            boolean over = BenchScreen.in(mx, my, sx, y + 4, 20, 20);
            if (sel) {
               FrontierUi.rect(g, sx - 1.5F, y + 2.5F, 23, 23, 5.0F, accent);
            }
            FrontierUi.rect(g, sx, y + 4, 20, 20, 4.0F, this.fittable[i] ? (over ? 0xFF3A3229 : 0xFF2E2820) : (over ? 0xFF26211A : 0xFF1E1A15));
         }
         // the showcase card
         BenchDraw.framed(g, x + 8, cardY, SHOW_W, cardH, 6.0F, BenchDraw.PAPER_EDGE, 1.0F, BenchDraw.PAPER);
         g.fillGradient(x + 9, cardY + 6, x + 8 + SHOW_W - 1, cardY + 90, BenchDraw.PAPER_TOP, BenchDraw.PAPER);
         FrontierUi.text(g, BenchScreen.tr("bench.frontierhunts.fit.in_hand"), x + 18, cardY + 9, BenchDraw.GOLD_DARK, FrontierUi.Size.SMALL);
         String name = this.held.isEmpty() ? BenchScreen.tr("bench.frontierhunts.fit.nothing") : this.held.getHoverName().getString();
         FrontierUi.text(g, FrontierUi.fit(name, SHOW_W - 20, FrontierUi.Size.STRONG), x + 18, cardY + 19, BenchDraw.INK, FrontierUi.Size.STRONG);
         FrontierUi.rect(g, x + 18, cardY + 34, SHOW_W - 20, 70, 6.0F, BenchDraw.PAPER_WELL);
         float iy = cardY + 112;
         if (has) {
            for (String f : this.facts) {
               int cut = f.indexOf('\u0000');
               FrontierUi.text(g, f.substring(0, cut), x + 18, iy, BenchDraw.INK_MUTED, FrontierUi.Size.SMALL);
               FrontierUi.right(g, FrontierUi.fit(f.substring(cut + 1), 100, FrontierUi.Size.SMALL), x + 8 + SHOW_W - 10, iy, BenchDraw.INK, FrontierUi.Size.SMALL);
               iy += 11;
            }
            FrontierUi.rect(g, x + 18, iy + 2, SHOW_W - 20, 1, 0.0F, BenchDraw.PAPER_RULE);
            iy += 7;
            for (String line : this.info) {
               if (iy > cardY + cardH - 12) {
                  break;
               }
               FrontierUi.text(g, line, x + 18, iy, BenchDraw.INK_MUTED, FrontierUi.Size.SMALL);
               iy += 9;
            }
         } else {
            String why = this.held.isEmpty() ? BenchScreen.tr("bench.frontierhunts.fit.nothing_hint") : BenchScreen.tr("bench.frontierhunts.fit.no_parts_hint");
            FrontierUi.wrap(g, why, x + 18, (int)iy, SHOW_W - 22, BenchDraw.INK_MUTED, FrontierUi.Size.SMALL);
         }
         if (!has) {
            String t = this.held.isEmpty() ? BenchScreen.tr("bench.frontierhunts.fit.nothing") : BenchScreen.tr("bench.frontierhunts.fit.no_parts");
            int ty = top + 30;
            ty += FrontierUi.wrap(g, t, rx + 12, ty, rw - 24, BenchDraw.TEXT, FrontierUi.Size.STRONG) + 4;
            FrontierUi.wrap(g, BenchScreen.tr("bench.frontierhunts.fit.nothing_hint"), rx + 12, ty, rw - 24, BenchDraw.MUTED, FrontierUi.Size.SMALL);
         } else {
            String legend = this.reloading ? BenchScreen.tr("bench.frontierhunts.fit.reloading") : BenchScreen.tr("bench.frontierhunts.fit.legend");
            FrontierUi.text(g, FrontierUi.fit(legend, rw, FrontierUi.Size.SMALL), rx + 2, y + h - 14, this.reloading ? BenchDraw.MISSING : BenchDraw.DIM,
               FrontierUi.Size.SMALL);
            if (this.contentH > view) {
               float bh = Math.max(20.0F, view * (float)view / this.contentH);
               float by = top + (view - bh) * (this.scroll / Math.max(1.0F, this.contentH - view));
               FrontierUi.rect(g, x + w - 5, top, 2.5F, view, 1.2F, 0x18FFFFFF);
               FrontierUi.rect(g, x + w - 5, by, 2.5F, bh, 1.2F, BenchDraw.alpha(accent, 0.75F));
            }
         }
      });
      // icons outside the batch: hotbar and the big weapon
      float hx = x + w - 9 - 9 * 22 + 2;
      for (int i = 0; i < 9; i++) {
         BenchScreen.icon(g, this.hotbar[i], hx + i * 22 + 2, y + 6, 1.0F);
      }
      g.pose().pushPose();
      g.pose().translate(0.0F, 0.0F, 250.0F);
      FrontierUi.batch(g, () -> {
         for (int i = 0; i < 9; i++) {
            if (!this.fittable[i] && !this.hotbar[i].isEmpty()) {
               FrontierUi.rect(g, hx + i * 22, y + 4, 20, 20, 4.0F, 0x88141210);
            }
         }
      });
      g.pose().popPose();
      BenchScreen.icon(g, this.held, x + 8 + SHOW_W / 2.0F - 32, cardY + 37, 4.0F);
      if (!has) {
         return;
      }
      g.enableScissor(rx - 2, top - 1, rx + rw + 2, top + view);
      FrontierUi.batch(g, () -> {
         for (SlotRow r : this.rows) {
            float ry = top + r.y() - this.scroll;
            if (ry > top + view || ry + r.h() < top) {
               continue;
            }
            FrontierUi.rect(g, rx, ry, rw, r.h(), 6.0F, 0xFF221E18);
            FrontierUi.text(g, r.label().toUpperCase(java.util.Locale.ROOT), rx + 8, ry + 7, BenchDraw.mix(accent, BenchDraw.TEXT, 0.25F), FrontierUi.Size.SMALL);
            float fx = rx + 70;
            if (r.fitted() != null) {
               FrontierUi.text(g, FrontierUi.fit(r.fittedName(), rw - 70 - 20 - 72, FrontierUi.Size.STRONG), fx + 20, ry + 6.5F, BenchDraw.TEXT,
                  FrontierUi.Size.STRONG);
               boolean over = BenchScreen.in(mx, my, rx + rw - 8 - 58, ry + 3, 58, 16);
               BenchDraw.button(g, rx + rw - 8 - 58, ry + 3, 58, 16, BenchScreen.tr("bench.frontierhunts.fit.remove"), BenchDraw.Look.DARK, accent,
                  r.canRemove() && !this.reloading, over, null);
            } else {
               FrontierUi.text(g, BenchScreen.tr(r.key().equals("magazine") ? "bench.frontierhunts.fit.standard" : "bench.frontierhunts.fit.empty"), fx,
                  ry + 6.5F, BenchDraw.DIM, FrontierUi.Size.BODY);
            }
            for (int i = 0; i < r.chips().size(); i++) {
               Chip c = r.chips().get(i);
               float cx = rx + 8 + (i % this.perRow) * (CHIP + CHIP_GAP);
               float cy = ry + 22 + (i / this.perRow) * (CHIP + 2);
               boolean over = BenchScreen.in(mx, my, cx, cy, CHIP, CHIP);
               if (c.fitted()) {
                  FrontierUi.rect(g, cx - 1.5F, cy - 1.5F, CHIP + 3, CHIP + 3, 5.5F, accent);
                  FrontierUi.rect(g, cx, cy, CHIP, CHIP, 4.0F, BenchDraw.mix(BenchDraw.TILE_SEL, accent, 0.25F));
               } else if (c.owned() > 0) {
                  FrontierUi.rect(g, cx, cy, CHIP, CHIP, 4.0F, over ? BenchDraw.mix(0xFF2E2820, accent, 0.25F) : 0xFF2E2820);
               } else {
                  FrontierUi.rect(g, cx, cy, CHIP, CHIP, 4.0F, over ? 0xFF4A4034 : 0xFF3A3229);
                  FrontierUi.rect(g, cx + 1, cy + 1, CHIP - 2, CHIP - 2, 3.0F, over ? 0xFF26211A : 0xFF1C1814);
               }
            }
         }
      });
      for (SlotRow r : this.rows) {
         float ry = top + r.y() - this.scroll;
         if (ry > top + view || ry + r.h() < top) {
            continue;
         }
         if (r.fitted() != null) {
            BenchScreen.icon(g, r.fittedIcon(), rx + 70, ry + 3, 1.0F);
         }
         for (int i = 0; i < r.chips().size(); i++) {
            float cx = rx + 8 + (i % this.perRow) * (CHIP + CHIP_GAP);
            float cy = ry + 22 + (i / this.perRow) * (CHIP + 2);
            BenchScreen.icon(g, r.chips().get(i).icon(), cx + 3, cy + 3, 1.0F);
         }
      }
      g.pose().pushPose();
      g.pose().translate(0.0F, 0.0F, 250.0F);
      FrontierUi.batch(g, () -> {
         for (SlotRow r : this.rows) {
            float ry = top + r.y() - this.scroll;
            if (ry > top + view || ry + r.h() < top) {
               continue;
            }
            for (int i = 0; i < r.chips().size(); i++) {
               Chip c = r.chips().get(i);
               float cx = rx + 8 + (i % this.perRow) * (CHIP + CHIP_GAP);
               float cy = ry + 22 + (i / this.perRow) * (CHIP + 2);
               if (c.owned() <= 0 && !c.fitted()) {
                  FrontierUi.rect(g, cx + 1, cy + 1, CHIP - 2, CHIP - 2, 3.0F, 0x99141210);
                  FrontierUi.circle(g, cx + CHIP - 4.5F, cy + 4.5F, 3.5F, accent);
                  FrontierUi.rect(g, cx + CHIP - 6.5F, cy + 4.0F, 4.0F, 1.0F, 0.0F, 0xFF1A140E);
                  FrontierUi.rect(g, cx + CHIP - 5.0F, cy + 2.5F, 1.0F, 4.0F, 0.0F, 0xFF1A140E);
               } else if (c.owned() > 0 && !c.fitted()) {
                  FrontierUi.right(g, Integer.toString(Math.min(99, c.owned())), cx + CHIP - 1.5F, cy + CHIP - 9, BenchDraw.TEXT, FrontierUi.Size.SMALL);
               }
            }
         }
      });
      g.pose().popPose();
      g.disableScissor();
   }

   void tooltip(GuiGraphics g, int mx, int my) {
      float hx = this.s.gridX + this.s.wideW - 9 - 9 * 22 + 2;
      for (int i = 0; i < 9; i++) {
         if (BenchScreen.in(mx, my, hx + i * 22, this.s.bodyY + 4, 20, 20) && !this.hotbar[i].isEmpty()) {
            g.renderTooltip(this.s.uiFont(), this.hotbar[i], mx, my);
            return;
         }
      }
      int rx = this.rowsX(), top = this.rowsTop();
      if (!BenchScreen.in(mx, my, rx, top, this.rowsW(), this.rowsView())) {
         if (BenchScreen.in(mx, my, this.s.gridX + 18, this.s.bodyY + 64, SHOW_W - 20, 70) && !this.held.isEmpty()) {
            g.renderTooltip(this.s.uiFont(), this.held, mx, my);
         }
         return;
      }
      for (SlotRow r : this.rows) {
         float ry = top + r.y() - this.scroll;
         for (int i = 0; i < r.chips().size(); i++) {
            float cx = rx + 8 + (i % this.perRow) * (CHIP + CHIP_GAP);
            float cy = ry + 22 + (i / this.perRow) * (CHIP + 2);
            if (BenchScreen.in(mx, my, cx, cy, CHIP, CHIP)) {
               Chip c = r.chips().get(i);
               List<Component> lines = new ArrayList<>();
               lines.add(Component.literal(c.name()));
               if (c.fitted()) {
                  lines.add(Component.literal(BenchScreen.tr("bench.frontierhunts.fit.click_remove")).withStyle(ChatFormatting.GREEN));
               } else if (c.owned() <= 0) {
                  lines.add(Component.literal(BenchScreen.tr("bench.frontierhunts.fit.not_owned")).withStyle(ChatFormatting.GRAY));
                  lines.add(Component.literal(BenchScreen.tr("bench.frontierhunts.fit.craft_it")).withStyle(ChatFormatting.GOLD));
               } else {
                  lines.add(Component.literal(BenchScreen.tr("bench.frontierhunts.fit.owned", c.owned())).withStyle(ChatFormatting.GRAY));
                  if (!c.canFit() || this.reloading) {
                     lines.add(Component.literal(BenchScreen.tr("bench.frontierhunts.fit.cant")).withStyle(ChatFormatting.RED));
                  } else if (r.fitted() != null && AttachmentSpec.exclusive(c.part(), r.fitted())) {
                     lines.add(Component.literal(BenchScreen.tr("bench.frontierhunts.fit.click_swap", r.fittedName())).withStyle(ChatFormatting.GOLD));
                  } else {
                     lines.add(Component.literal(BenchScreen.tr("bench.frontierhunts.fit.click_fit")).withStyle(ChatFormatting.GOLD));
                  }
               }
               g.renderComponentTooltip(this.s.uiFont(), lines, mx, my);
               return;
            }
         }
      }
   }
}
