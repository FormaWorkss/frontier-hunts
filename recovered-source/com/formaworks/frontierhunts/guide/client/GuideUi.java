package com.formaworks.frontierhunts.guide.client;

import com.formaworks.frontierhunts.client.FrontierUi;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** [guide] Shared look of the Field School screens: the Hunter's Journal palette, notebook skin and text helpers. */
final class GuideUi {
   // Hunter's Journal palette (same values as client/JournalScreen + FieldNotebookSkin)
   static final int PAPER = 0xFFEAE3D0;
   static final int INK = 0xFF243A32;
   static final int INK_BROWN = 0xFF3B2E20;
   static final int MUTED = 0xFF657064;
   static final int GOLD = 0xFFB99859;
   static final int GOLD_DARK = 0xFF8F6E2F;
   static final int SPINE = 0xFF492D2B;
   static final int SIDEBAR = 0xFF49372B;
   static final int BUTTON = 0xFF2D4237;
   static final int BUTTON_SELECTED = 0xFF3E5945;
   static final int BUTTON_HOVER = 0xFF516C56;
   static final int BUTTON_OFF = 0xFF6B7567;
   static final int SIDEBAR_TEXT = 0xFFEFE8D7;
   static final int SIDEBAR_MUTED = 0xFFC9B99A;
   static final int RULE = 0xFFC5C2AE;
   static final int RED = 0xFFA5281F;
   static final int GREEN = 0xFF3E6E37;
   static final int BLUE = 0xFF2F5F80;
   static final int TINT = 0x22B99859;

   private GuideUi() {
   }

   static Font font() {
      return Minecraft.getInstance().font;
   }

   static String tr(String key, Object... args) {
      return I18n.get(key, args);
   }

   static boolean has(String key) {
      return I18n.exists(key);
   }

   static List<FormattedCharSequence> wrap(String text, int width, FrontierUi.Size size) {
      return font().split(FrontierUi.c(text, size), Math.max(20, width));
   }

   /** Draws wrapped text, returns the height used. */
   static int para(GuiGraphics g, String text, int x, int y, int width, int color, FrontierUi.Size size, int lineGap) {
      int lh = FrontierUi.lineHeight(size) + lineGap;
      int yy = y;
      for (FormattedCharSequence line : wrap(text, width, size)) {
         g.drawString(font(), line, x, yy, color, false);
         yy += lh;
      }
      return yy - y;
   }

   static int paraHeight(String text, int width, FrontierUi.Size size, int lineGap) {
      return wrap(text, width, size).size() * (FrontierUi.lineHeight(size) + lineGap);
   }

   /** The journal's notebook cover and pages (same construction as the Hunter's Journal). */
   static void notebook(GuiGraphics g, int x, int y, int w, int h, int side) {
      g.fill(x + 4, y + 5, x + w + 4, y + h + 5, 0x80000000);
      g.fill(x - 3, y - 3, x + w + 3, y + h + 3, 0xFF413526);
      g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF88704A);
      g.fill(x, y, x + w, y + h, PAPER);
      g.fill(x, y, x + side, y + h, SIDEBAR);
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

   /** A small round status badge: done (gold disc + tick), current (gold ring), open (dim ring). */
   static void badge(GuiGraphics g, float cx, float cy, int number, int state, boolean dark) {
      FrontierUi.batch(g, () -> badge0(g, cx, cy, number, state, dark)); // [1.2.9] one draw, not dozens
   }

   private static void badge0(GuiGraphics g, float cx, float cy, int number, int state, boolean dark) {
      int ring = state == 0 ? (dark ? 0xFF7F8A7A : 0xFFA0A090) : GOLD;
      if (state == 2) {
         FrontierUi.circle(g, cx, cy, 6.5F, GOLD);
         tick(g, cx, cy, dark ? SIDEBAR : PAPER);
      } else {
         FrontierUi.circle(g, cx, cy, 6.5F, ring);
         FrontierUi.circle(g, cx, cy, 5.5F, dark ? SIDEBAR : PAPER);
         String n = Integer.toString(number);
         int c = state == 1 ? GOLD : (dark ? SIDEBAR_MUTED : MUTED);
         FrontierUi.center(g, n, cx + 0.5F, cy - 3.5F, c, FrontierUi.Size.SMALL);
      }
   }

   /** A check mark drawn with sub-pixel rects. */
   static void tick(GuiGraphics g, float cx, float cy, int color) {
      FrontierUi.batch(g, () -> tick0(g, cx, cy, color)); // [1.2.9]
   }

   private static void tick0(GuiGraphics g, float cx, float cy, int color) {
      for (int i = 0; i <= 4; i++) {
         FrontierUi.rect(g, cx - 3.2F + i * 0.55F, cy - 0.2F + i * 0.55F, 1.3F, 1.3F, 0.3F, color);
      }
      for (int i = 0; i <= 8; i++) {
         FrontierUi.rect(g, cx - 0.9F + i * 0.55F, cy + 2.0F - i * 0.7F, 1.3F, 1.3F, 0.3F, color);
      }
   }

   /** A thin line between two GUI points (drawn at full screen resolution). */
   static void line(GuiGraphics g, float x0, float y0, float x1, float y1, float width, int color) {
      FrontierUi.batch(g, () -> line0(g, x0, y0, x1, y1, width, color)); // [1.2.9]
   }

   private static void line0(GuiGraphics g, float x0, float y0, float x1, float y1, float width, int color) {
      float dx = x1 - x0, dy = y1 - y0;
      float len = (float)Math.sqrt(dx * dx + dy * dy);
      int steps = Math.max(1, (int)(len * 2.0F));
      for (int i = 0; i <= steps; i++) {
         float t = i / (float)steps;
         FrontierUi.rect(g, x0 + dx * t - width / 2, y0 + dy * t - width / 2, width, width, 0.0F, color);
      }
   }

   /** A pill label, right-aligned at x. Returns its width. */
   static int pill(GuiGraphics g, String text, int rightX, int y, int bg, int fg) {
      int w = FrontierUi.width(text, FrontierUi.Size.SMALL) + 10;
      FrontierUi.rect(g, rightX - w, y, w, 11, 5.5F, bg);
      FrontierUi.text(g, text, rightX - w + 5, y + 2, fg, FrontierUi.Size.SMALL);
      return w;
   }

   static Component c(String s, FrontierUi.Size size) {
      return FrontierUi.c(s, size);
   }
}
