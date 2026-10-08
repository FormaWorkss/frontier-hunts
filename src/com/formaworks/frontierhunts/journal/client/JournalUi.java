package com.formaworks.frontierhunts.journal.client;

import com.formaworks.frontierhunts.client.FrontierUi;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * [journal] The Hunter's Journal look (leather notebook, paper, ink, brass) and small drawing helpers shared by the
 * journal screen, its toasts and extra page views. Text is turned into {@link FormattedCharSequence}s when a page is
 * laid out, so frames only draw.
 */
public final class JournalUi {
   public static final int PAPER = 0xFFEAE3D0;
   public static final int PAPER_DARK = 0xFFDCD3BC;
   public static final int INK = 0xFF243A32;
   public static final int INK_BROWN = 0xFF3B2E20;
   public static final int MUTED = 0xFF6A7266;
   public static final int FAINT = 0xFF9A9A88;
   public static final int GOLD = 0xFFB99859;
   public static final int GOLD_DARK = 0xFF8F6E2F;
   public static final int LEATHER = 0xFF49372B;
   public static final int LEATHER_DARK = 0xFF35271E;
   public static final int SIDEBAR_TEXT = 0xFFEFE8D7;
   public static final int SIDEBAR_MUTED = 0xFFC9B99A;
   public static final int BUTTON = 0xFF2D4237;
   public static final int BUTTON_SELECTED = 0xFF3E5945;
   public static final int BUTTON_HOVER = 0xFF516C56;
   public static final int RULE = 0xFFC5C2AE;
   public static final int TRACK = 0xFFD3CCB6;
   public static final int GREEN = 0xFF3E6E37;
   public static final int RED = 0xFFA5281F;
   public static final int HOVER = 0x16243A32;

   private static final Map<String, ItemStack> ICONS = new HashMap<>();

   private JournalUi() {
   }

   public static Font font() {
      return Minecraft.getInstance().font;
   }

   public static String tr(String key, Object... args) {
      return I18n.get(key, args);
   }

   public static boolean has(String key) {
      return I18n.exists(key);
   }

   public static FormattedCharSequence seq(String text, FrontierUi.Size size) {
      return FrontierUi.c(text, size).getVisualOrderText();
   }

   public static FormattedCharSequence fit(String text, int width, FrontierUi.Size size) {
      return seq(FrontierUi.fit(text, Math.max(8, width), size), size);
   }

   public static List<FormattedCharSequence> wrap(String text, int width, FrontierUi.Size size) {
      return font().split(FrontierUi.c(text, size), Math.max(20, width));
   }

   public static int width(FormattedCharSequence s) {
      return font().width(s);
   }

   public static void draw(GuiGraphics g, FormattedCharSequence s, int x, int y, int color) {
      g.drawString(font(), s, x, y, color, false);
   }

   public static void drawRight(GuiGraphics g, FormattedCharSequence s, int right, int y, int color) {
      g.drawString(font(), s, right - font().width(s), y, color, false);
   }

   /** Cached icon stack for an item id ("" or unknown -> empty). */
   public static ItemStack icon(String id) {
      return ICONS.computeIfAbsent(id == null ? "" : id, k -> {
         ResourceLocation rl = ResourceLocation.tryParse(k);
         Item item = rl == null || k.isEmpty() ? Items.AIR : BuiltInRegistries.ITEM.get(rl);
         return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
      });
   }

   /** Rounded progress bar with a 1 px inset fill. */
   public static void bar(GuiGraphics g, float x, float y, float w, float h, float frac, int track, int fill) {
      FrontierUi.rect(g, x, y, w, h, h / 2.0F, track);
      float f = Math.max(0.0F, Math.min(1.0F, frac));
      if (f > 0.0F) {
         FrontierUi.rect(g, x, y, Math.max(h, w * f), h, h / 2.0F, fill);
      }
   }

   /** Check box: done = filled with a tick, else an empty square. */
   public static void checkbox(GuiGraphics g, float x, float y, boolean done, int color) {
      if (done) {
         FrontierUi.rect(g, x, y, 10, 10, 2.0F, color);
         tick(g, x + 5.0F, y + 5.0F, PAPER);
      } else {
         FrontierUi.outline(g, x, y, 10, 10, 2.0F, 0xFF8C8A78, PAPER);
      }
   }

   /** A check mark drawn with sub-pixel rects (same as the Field School). */
   public static void tick(GuiGraphics g, float cx, float cy, int color) {
      for (int i = 0; i <= 4; i++) {
         FrontierUi.rect(g, cx - 3.2F + i * 0.55F, cy - 0.2F + i * 0.55F, 1.3F, 1.3F, 0.3F, color);
      }
      for (int i = 0; i <= 8; i++) {
         FrontierUi.rect(g, cx - 0.9F + i * 0.55F, cy + 2.0F - i * 0.7F, 1.3F, 1.3F, 0.3F, color);
      }
   }

   /** A small padlock. */
   public static void lock(GuiGraphics g, float cx, float cy, int color, int bg) {
      FrontierUi.outline(g, cx - 2.5F, cy - 5.0F, 5.0F, 5.5F, 2.5F, color, bg);
      FrontierUi.rect(g, cx - 3.5F, cy - 1.0F, 7.0F, 5.5F, 1.0F, color);
   }

   /** Notebook cover, pages and stitching (same construction as the Field School guide). */
   public static void notebook(GuiGraphics g, int x, int y, int w, int h, int side) {
      g.fill(x + 4, y + 5, x + w + 4, y + h + 5, 0x80000000);
      g.fill(x - 3, y - 3, x + w + 3, y + h + 3, 0xFF413526);
      g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF88704A);
      g.fill(x, y, x + w, y + h, PAPER);
      g.fill(x, y, x + side, y + h, LEATHER);
      for (int i = 0; i < 4; i++) {
         g.fill(x + w - 2 - i, y + 2 + i, x + w - 1 - i, y + h - i, 0xFFC4B99A);
         g.fill(x + side + 3, y + h - 2 - i, x + w - i, y + h - 1 - i, 0xFFC4B99A);
      }
      g.fillGradient(x + side, y, x + side + 12, y + h, 0xFFB9A984, 0xFFE0D6BB);
      g.fill(x + side, y, x + side + 2, y + h, 0xFFA2804B);
      for (int k = 8; k < h - 5; k += 7) {
         g.fill(x + 3, y + k, x + 4, y + k + 3, 0xFF9C8667);
         g.fill(x + side - 5, y + k, x + side - 4, y + k + 3, 0xFF9C8667);
      }
      for (int k = 8; k < side - 6; k += 7) {
         g.fill(x + k, y + 4, x + k + 3, y + 5, 0xFF9C8667);
         g.fill(x + k, y + h - 5, x + k + 3, y + h - 4, 0xFF9C8667);
      }
   }

   /** Journal-style button (leather green, brass edge when selected/focused). */
   public static void button(GuiGraphics g, int x, int y, int w, int h, FormattedCharSequence label, boolean hover, boolean selected, boolean active) {
      int bg = !active ? 0xFF6B7567 : (hover ? BUTTON_HOVER : (selected ? BUTTON_SELECTED : BUTTON));
      FrontierUi.rect(g, x, y, w, h, 2.0F, bg);
      if (selected) {
         FrontierUi.rect(g, x, y, 2, h, 1.0F, GOLD);
      }
      int tw = font().width(label);
      g.drawString(font(), label, x + (w - tw) / 2, y + (h - 8) / 2, active ? SIDEBAR_TEXT : 0xFFC4C2B4, false);
   }

   public static int alpha(int color, float a) {
      int al = (int)((color >>> 24) * Math.max(0.0F, Math.min(1.0F, a)));
      return al << 24 | color & 0xFFFFFF;
   }
}
