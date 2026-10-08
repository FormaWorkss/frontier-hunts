package com.formaworks.frontierhunts.benches.client;

import com.formaworks.frontierhunts.client.FrontierUi;
import net.minecraft.client.gui.GuiGraphics;

/**
 * [benches] Drawing helpers and the palette of the bench screen. Every helper only fills and writes text, so callers
 * wrap them in one {@link FrontierUi#batch}; nothing here blits or renders items.
 */
final class BenchDraw {
   // the workshop: warm charcoal panels with inset wells
   static final int PANEL = 0xFF1D1A16, OUTER = 0xFF0A0907;
   static final int INSET = 0xFF141210, INSET_EDGE = 0xFF2B261F;
   static final int TEXT = 0xFFF2E9D8, MUTED = 0xFFA89D88, DIM = 0xFF8A806D;
   static final int TILE = 0xFF27221B, TILE_HOT = 0xFF332C23, TILE_SEL = 0xFF3D3428, TILE_OFF = 0xFF201C17;
   static final int READY = 0xFF8FD694, MISSING = 0xFFE58068;
   static final int RULE_DARK = 0xFF2D2720;
   // the detail card: the Handbook's paper
   static final int PAPER = 0xFFEEE6D2, PAPER_EDGE = 0xFFB7A47C, PAPER_TOP = 0xFFF6F0E0, PAPER_WELL = 0xFFE3D8C0, PAPER_RULE = 0xFFD3C6A6;
   static final int INK = 0xFF2A2219, INK_MUTED = 0xFF6E624F, GOLD_DARK = 0xFF8F6E2F;
   static final int GREEN_INK = 0xFF3D7A39, RED_INK = 0xFFB23A2B;

   private BenchDraw() {
   }

   static int mix(int a, int b, float t) {
      t = Math.max(0.0F, Math.min(1.0F, t));
      int aa = a >>> 24, ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
      int ba = b >>> 24, br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
      return Math.round(aa + (ba - aa) * t) << 24 | Math.round(ar + (br - ar) * t) << 16 | Math.round(ag + (bg - ag) * t) << 8 | Math.round(ab + (bb - ab) * t);
   }

   static int alpha(int c, float a) {
      return Math.round(Math.max(0.0F, Math.min(1.0F, a)) * 255.0F) << 24 | c & 0xFFFFFF;
   }

   /** A rounded outline drawn as two nested rounded fills (outer colour, then the inside colour). */
   static void framed(GuiGraphics g, float x, float y, float w, float h, float r, int edge, float t, int inside) {
      FrontierUi.rect(g, x, y, w, h, r, edge);
      FrontierUi.rect(g, x + t, y + t, w - 2 * t, h - 2 * t, Math.max(0.0F, r - t), inside);
   }

   /** A pill label at (x, y); returns its width. */
   static float pill(GuiGraphics g, String s, float x, float y, int bg, int fg) {
      float w = FrontierUi.width(s, FrontierUi.Size.SMALL) + 8;
      FrontierUi.rect(g, x, y, w, 10, 5.0F, bg);
      FrontierUi.text(g, s, x + 4, y + 1.5F, fg, FrontierUi.Size.SMALL);
      return w;
   }

   /** A check mark centred on (cx, cy). */
   static void check(GuiGraphics g, float cx, float cy, int c, float k) {
      for (int i = 0; i <= 4; i++) {
         FrontierUi.rect(g, cx + (-3.2F + i * 0.55F) * k, cy + (-0.2F + i * 0.55F) * k, 1.3F * k, 1.3F * k, 0.3F, c);
      }
      for (int i = 0; i <= 8; i++) {
         FrontierUi.rect(g, cx + (-0.9F + i * 0.55F) * k, cy + (2.0F - i * 0.7F) * k, 1.3F * k, 1.3F * k, 0.3F, c);
      }
   }

   /** A round "!" warning mark centred on (cx, cy). */
   static void warn(GuiGraphics g, float cx, float cy, int c, int bg) {
      FrontierUi.circle(g, cx, cy, 3.4F, c);
      FrontierUi.rect(g, cx - 0.5F, cy - 2.2F, 1.0F, 2.6F, 0.0F, bg);
      FrontierUi.rect(g, cx - 0.5F, cy + 1.0F, 1.0F, 1.0F, 0.0F, bg);
   }

   /** A thin line between two GUI points. */
   static void line(GuiGraphics g, float x0, float y0, float x1, float y1, float wd, int c) {
      float dx = x1 - x0, dy = y1 - y0;
      float len = (float)Math.sqrt(dx * dx + dy * dy);
      int steps = Math.max(1, (int)(len * 2.0F));
      for (int i = 0; i <= steps; i++) {
         float t = i / (float)steps;
         FrontierUi.rect(g, x0 + dx * t - wd / 2, y0 + dy * t - wd / 2, wd, wd, 0.0F, c);
      }
   }

   /** A magnifying glass centred on (cx, cy). */
   static void magnifier(GuiGraphics g, float cx, float cy, int c) {
      FrontierUi.circle(g, cx - 0.6F, cy - 0.6F, 3.4F, c);
      FrontierUi.circle(g, cx - 0.6F, cy - 0.6F, 2.3F, 0xFF000000 | 0x141210);
      line(g, cx + 1.6F, cy + 1.6F, cx + 3.8F, cy + 3.8F, 1.3F, c);
   }

   /** An "×" centred on (cx, cy). */
   static void cross(GuiGraphics g, float cx, float cy, float r, int c) {
      line(g, cx - r, cy - r, cx + r, cy + r, 1.4F, c);
      line(g, cx + r, cy - r, cx - r, cy + r, 1.4F, c);
   }

   /** A small keyboard-key cap with its label; returns its width. */
   static float key(GuiGraphics g, String k, float x, float y, int bg, int fg) {
      float w = FrontierUi.width(k, FrontierUi.Size.SMALL) + 8;
      FrontierUi.rect(g, x, y, w, 11, 3.0F, bg);
      FrontierUi.text(g, k, x + 4, y + 1.5F, fg, FrontierUi.Size.SMALL);
      return w;
   }

   enum Look {
      PRIMARY, PAPER, DARK
   }

   /**
    * A button. PRIMARY is filled with the bench's accent, PAPER is the light secondary on the detail card, DARK the
    * quiet one on the dark panels. {@code key} (optional) is drawn as a key cap after the label.
    */
   static void button(GuiGraphics g, float x, float y, float w, float h, String label, Look look, int accent, boolean active, boolean hot, String key) {
      int fg;
      switch (look) {
         case PRIMARY -> {
            int bg = active ? (hot ? mix(accent, 0xFFFFFFFF, 0.14F) : accent) : 0xFF5A5246;
            if (active) {
               FrontierUi.rect(g, x, y + 1.5F, w, h, 4.0F, 0x40000000);
            }
            FrontierUi.rect(g, x, y, w, h, 4.0F, mix(bg, 0xFF000000, 0.35F));
            FrontierUi.rect(g, x + 1, y + 1, w - 2, h - 2, 3.0F, bg);
            if (active) {
               FrontierUi.rect(g, x + 2, y + 1, w - 4, 1.5F, 0.0F, 0x55FFFFFF);
            }
            fg = active ? 0xFF1A140E : 0xFFA0968A;
         }
         case PAPER -> {
            FrontierUi.rect(g, x, y, w, h, 4.0F, active ? (hot ? GOLD_DARK : 0xFFB5A780) : 0xFFD2C8AE);
            FrontierUi.rect(g, x + 1, y + 1, w - 2, h - 2, 3.0F, active ? (hot ? 0xFFFFFBF0 : 0xFFF7F1E2) : 0xFFEAE2CE);
            fg = active ? INK : 0xFFA89D86;
         }
         default -> {
            FrontierUi.rect(g, x, y, w, h, 4.0F, active ? (hot ? mix(accent, 0xFF3E362C, 0.4F) : 0xFF3E362C) : 0xFF2C2620);
            FrontierUi.rect(g, x + 1, y + 1, w - 2, h - 2, 3.0F, hot && active ? 0xFF3A3229 : 0xFF2A241D);
            fg = active ? TEXT : DIM;
         }
      }
      float kw = key == null ? 0.0F : FrontierUi.width(key, FrontierUi.Size.SMALL) + 13;
      String fitted = FrontierUi.fit(label, (int)(w - 10 - kw), FrontierUi.Size.STRONG);
      float tw = FrontierUi.width(fitted, FrontierUi.Size.STRONG);
      float tx = x + (w - tw - kw) / 2.0F;
      FrontierUi.text(g, fitted, tx, y + (h - 9) / 2.0F + 0.5F, fg, FrontierUi.Size.STRONG);
      if (key != null) {
         key(g, key, tx + tw + 5, y + h / 2.0F - 5.5F, active ? 0x22000000 : 0x10000000, fg);
      }
   }
}
