package com.formaworks.frontierhunts.phone.client.ui;

import com.formaworks.frontierhunts.phone.client.ui.Canvas.Font;

/** [phone] FieldOS controls: headers, cards, buttons, toggles, segmented controls, list rows, progress. */
public final class Ui {
   /** zone ids below 100 belong to the phone; apps use 100+ */
   public static final int Z_BACK = 1;
   public static final int Z_HOME = 2;
   public static final int Z_STATUS = 3;
   public static final int Z_BANNER = 4;
   public static final int Z_SHADE = 5;
   public static final int Z_TOGGLE = 6;
   public static final int Z_APP = 7;
   public static final int Z_DOCK = 8;
   public static final int Z_NOTICE = 9;
   public static final int Z_SINK = 10;
   public static final int Z_CLOSE_SHADE = 11;
   public static final int Z_CLEAR_NOTICES = 12;
   public static final int Z_PAGE = 13;

   public static final float PAD = 9.0F;

   private Ui() {
   }

   // ------------------------------------------------------------------------------------------------ keys (GLFW codes)

   public static final int K_ESC = 256, K_ENTER = 257, K_TAB = 258, K_BACKSPACE = 259, K_DELETE = 261, K_RIGHT = 262, K_LEFT = 263,
      K_DOWN = 264, K_UP = 265, K_SPACE = 32, K_R = 82, K_A = 65, K_D = 68, K_W = 87, K_S = 83, K_E = 69, K_Q = 81, K_N = 78, K_H = 72,
      K_1 = 49, K_5 = 53;

   // ------------------------------------------------------------------------------------------------ app chrome

   /**
    * Large app title under the status bar, with an optional back chevron and subtitle. Returns the y below it.
    */
   public static float header(PhoneUi ui, Frame f, String title, String sub, boolean back, float w) {
      float y = Theme.STATUS_H + 3.0F;
      float x = PAD;
      if (back) {
         boolean hot = ui.hot(Z_BACK, 0L);
         f.zone(Z_BACK, 0.0F, y - 2.0F, 44.0F, 22.0F);
         G.BACK.draw(f, PAD + 4.0F, y + 9.0F, 13.0F, hot ? Theme.TEXT : Theme.BLAZE);
         f.text("Back", PAD + 12.0F, y + 5.0F, hot ? Theme.TEXT : Theme.BLAZE, Font.STRONG);
         y += 20.0F;
      } else {
         y += 6.0F;
      }
      f.text(ui.txt.fit(f, title, w - PAD * 2.0F, Font.LARGE), x, y, Theme.TEXT, Font.LARGE);
      y += 21.0F;
      if (sub != null && !sub.isEmpty()) {
         f.text(ui.txt.fit(f, sub, w - PAD * 2.0F, Font.SMALL), x + 0.5F, y, Theme.TEXT3, Font.SMALL);
         y += 11.0F;
      }
      return y + 4.0F;
   }

   /** Compact centred title bar (for deep pages and games): back on the left, title in the middle. Returns y below. */
   public static float bar(PhoneUi ui, Frame f, String title, float w, boolean back) {
      float y = Theme.STATUS_H + 2.0F;
      if (back) {
         boolean hot = ui.hot(Z_BACK, 0L);
         f.zone(Z_BACK, 0.0F, y - 2.0F, 40.0F, 22.0F);
         G.BACK.draw(f, PAD + 3.0F, y + 9.0F, 13.0F, hot ? Theme.TEXT : Theme.BLAZE);
      }
      f.center(ui.txt.fit(f, title, w - 90.0F, Font.STRONG), w / 2.0F, y + 5.0F, Theme.TEXT, Font.STRONG);
      return y + 22.0F;
   }

   public static void section(Frame f, String label, float x, float y, float w) {
      f.text(label, x + 1.0F, y, Theme.TEXT3, Font.SMALL);
   }

   // ------------------------------------------------------------------------------------------------ surfaces

   public static void card(Frame f, float x, float y, float w, float h) {
      f.round(x, y, w, h, 9.0F, Theme.SURFACE2);
   }

   public static void card(Frame f, float x, float y, float w, float h, int color) {
      f.round(x, y, w, h, 9.0F, color);
   }

   /** A card that lifts slightly under the mouse and darkens when pressed; registers its zone. */
   public static void tapCard(PhoneUi ui, Frame f, int id, long data, float x, float y, float w, float h, int color) {
      boolean hot = ui.hot(id, data);
      boolean down = ui.down(id, data);
      int c = down ? Theme.shade(color, 0.85F) : (hot ? Theme.mix(color, 0xFFFFFFFF, 0.05F) : color);
      f.round(x, y, w, h, 9.0F, c);
      f.zone(id, x, y, w, h, data);
   }

   // ------------------------------------------------------------------------------------------------ buttons

   public static final int FILLED = 0;
   public static final int TONAL = 1;
   public static final int PLAIN = 2;
   public static final int DANGER = 3;
   public static final int GOOD = 4;

   public static void button(PhoneUi ui, Frame f, int id, long data, float x, float y, float w, float h, String label, int style, boolean enabled) {
      boolean hot = enabled && ui.hot(id, data);
      boolean down = enabled && ui.down(id, data);
      int bg, fg;
      switch (style) {
         case FILLED -> {
            bg = Theme.BLAZE;
            fg = 0xFF1C1209;
         }
         case DANGER -> {
            bg = Theme.RED_DIM;
            fg = Theme.RED;
         }
         case GOOD -> {
            bg = Theme.MOSS;
            fg = 0xFF10200C;
         }
         case PLAIN -> {
            bg = 0x00000000;
            fg = Theme.BLAZE;
         }
         default -> {
            bg = Theme.SURFACE3;
            fg = Theme.TEXT;
         }
      }
      if (!enabled) {
         bg = style == PLAIN ? 0 : Theme.SURFACE3;
         fg = Theme.TEXT3;
      } else if (down) {
         bg = style == PLAIN ? 0x20FFFFFF : Theme.shade(bg, 0.82F);
      } else if (hot) {
         bg = style == PLAIN ? 0x14FFFFFF : Theme.mix(bg, 0xFFFFFFFF, 0.08F);
      }
      if ((bg >>> 24) != 0) {
         f.round(x, y, w, h, Math.min(h / 2.0F, 8.0F), bg);
      }
      Font font = h >= 20.0F ? Font.STRONG : Font.SMALL;
      String s = ui.txt.fit(f, label, w - 8.0F, font);
      float ty = y + (h - Canvas.capHeight(font)) / 2.0F - Canvas.capTop(font);
      f.center(s, x + w / 2.0F, ty, fg, font);
      if (enabled) {
         f.zone(id, x, y, w, h, data);
      }
   }

   /** Round icon button. */
   public static void iconButton(PhoneUi ui, Frame f, int id, long data, float cx, float cy, float r, G glyph, int bg, int fg, boolean enabled) {
      boolean hot = enabled && ui.hot(id, data);
      boolean down = enabled && ui.down(id, data);
      int b = !enabled ? Theme.SURFACE2 : (down ? Theme.shade(bg, 0.8F) : (hot ? Theme.mix(bg, 0xFFFFFFFF, 0.1F) : bg));
      if ((b >>> 24) != 0) {
         f.circle(cx, cy, r, b);
      }
      glyph.draw(f, cx, cy, r * 1.15F, enabled ? fg : Theme.TEXT3);
      if (enabled) {
         f.zone(id, cx - r, cy - r, r * 2.0F, r * 2.0F, data);
      }
   }

   // ------------------------------------------------------------------------------------------------ toggles and choices

   /** iOS-style switch, 26 x 15, with its knob easing to the side. */
   public static void toggle(PhoneUi ui, Frame f, int id, long data, float x, float y, boolean on) {
      float t = ui.anim(id * 7919L + data, on ? 1.0F : 0.0F, 14.0F);
      int track = Theme.mix(Theme.SURFACE4, Theme.MOSS, t);
      f.round(x, y, 26.0F, 15.0F, 7.5F, track);
      float kx = x + 7.5F + t * 11.0F;
      f.circle(kx, y + 7.5F + 0.5F, 6.4F, 0x40000000);
      f.circle(kx, y + 7.5F, 6.2F, 0xFFF7F5EE);
      f.zone(id, x - 4.0F, y - 3.0F, 34.0F, 21.0F, data);
   }

   /** Segmented control; the selected segment's highlight slides. Zone data = segment index. */
   public static void segmented(PhoneUi ui, Frame f, int id, float x, float y, float w, float h, String[] labels, int selected) {
      f.round(x, y, w, h, h / 2.0F, Theme.SURFACE2);
      float sw = w / labels.length;
      float pos = ui.anim(id * 104729L, selected, 16.0F);
      f.round(x + 1.5F + pos * sw, y + 1.5F, sw - 3.0F, h - 3.0F, (h - 3.0F) / 2.0F, Theme.SURFACE4);
      for (int i = 0; i < labels.length; i++) {
         boolean hot = ui.hot(id, i);
         int col = i == selected ? Theme.TEXT : (hot ? Theme.TEXT2 : Theme.TEXT3);
         float ty = y + (h - Canvas.capHeight(Font.SMALL)) / 2.0F - Canvas.capTop(Font.SMALL);
         f.center(ui.txt.fit(f, labels[i], sw - 4.0F, Font.SMALL), x + sw * i + sw / 2.0F, ty, col, Font.SMALL);
         f.zone(id, x + sw * i, y, sw, h, i);
      }
   }

   /** Small rounded label. Returns its width. */
   public static float chip(Frame f, String s, float x, float y, int bg, int fg) {
      float w = f.width(s, Font.SMALL) + 8.0F;
      f.round(x, y, w, 11.0F, 5.5F, bg);
      f.text(s, x + 4.0F, y + 2.0F, fg, Font.SMALL);
      return w;
   }

   public static float chipRight(Frame f, String s, float rx, float y, int bg, int fg) {
      float w = f.width(s, Font.SMALL) + 8.0F;
      return chip(f, s, rx - w, y, bg, fg);
   }

   // ------------------------------------------------------------------------------------------------ progress

   public static void bar(Frame f, float x, float y, float w, float h, float t, int track, int fill) {
      f.round(x, y, w, h, h / 2.0F, track);
      t = Theme.clamp01(t);
      if (t > 0.0F) {
         f.round(x, y, Math.max(h, w * t), h, h / 2.0F, fill);
      }
   }

   public static void ring(Frame f, float cx, float cy, float r, float thick, float t, int track, int fill) {
      f.ring(cx, cy, r, thick, track);
      t = Theme.clamp01(t);
      if (t > 0.001F) {
         f.arc(cx, cy, r, thick, 0.0F, (float)(Math.PI * 2.0 * t), fill);
         // round caps
         float a1 = (float)(Math.PI * 2.0 * t);
         float rm = r - thick / 2.0F;
         f.circle(cx, cy - rm, thick / 2.0F, fill);
         f.circle(cx + (float)Math.sin(a1) * rm, cy - (float)Math.cos(a1) * rm, thick / 2.0F, fill);
      }
   }

   // ------------------------------------------------------------------------------------------------ list rows

   /**
    * A list row: a glyph in a tinted rounded square, title and subtitle, an optional right-hand value and chevron.
    * Returns the row height.
    */
   public static float row(PhoneUi ui, Frame f, int id, long data, float x, float y, float w, G glyph, int glyphColor, String title, String sub,
      String value, int valueColor, boolean chevron) {
      float h = sub == null || sub.isEmpty() ? 26.0F : 32.0F;
      boolean hot = id != 0 && ui.hot(id, data);
      boolean down = id != 0 && ui.down(id, data);
      if (hot || down) {
         f.round(x - 3.0F, y, w + 6.0F, h, 7.0F, down ? 0x24FFFFFF : 0x12FFFFFF);
      }
      float tx = x;
      if (glyph != null) {
         f.round(x, y + (h - 20.0F) / 2.0F, 20.0F, 20.0F, 6.0F, Theme.mix(Theme.SURFACE3, glyphColor, 0.18F));
         glyph.draw(f, x + 10.0F, y + h / 2.0F, 13.0F, glyphColor);
         tx = x + 27.0F;
      }
      float right = x + w - (chevron ? 12.0F : 0.0F);
      float vw = value == null || value.isEmpty() ? 0.0F : f.width(value, Font.STRONG) + 6.0F;
      float tw = right - tx - vw;
      if (sub == null || sub.isEmpty()) {
         f.text(ui.txt.fit(f, title, tw, Font.STRONG), tx, y + 8.0F, Theme.TEXT, Font.STRONG);
      } else {
         f.text(ui.txt.fit(f, title, tw, Font.STRONG), tx, y + 5.0F, Theme.TEXT, Font.STRONG);
         f.text(ui.txt.fit(f, sub, right - tx - (vw > 0 ? vw : 0), Font.SMALL), tx, y + 18.0F, Theme.TEXT3, Font.SMALL);
      }
      if (vw > 0.0F) {
         f.right(value, right, y + (h - 10.0F) / 2.0F + 1.0F, valueColor, Font.STRONG);
      }
      if (chevron) {
         G.FORWARD.draw(f, x + w - 4.0F, y + h / 2.0F, 9.0F, Theme.TEXT3);
      }
      if (id != 0) {
         f.zone(id, x - 3.0F, y, w + 6.0F, h, data);
      }
      return h;
   }

   public static void divider(Frame f, float x, float y, float w) {
      f.fill(x, y, w, 0.5F, Theme.LINE);
   }

   /** Empty-state block: glyph, title and a sentence, centred. */
   public static void empty(PhoneUi ui, Frame f, G glyph, String title, String text, float cx, float y, float w) {
      f.circle(cx, y + 18.0F, 18.0F, Theme.SURFACE2);
      glyph.draw(f, cx, y + 18.0F, 20.0F, Theme.TEXT3);
      f.center(title, cx, y + 44.0F, Theme.TEXT, Font.STRONG);
      ui.txt.paraCenter(f, text, cx, y + 58.0F, w, Theme.TEXT3, Font.SMALL, 2.0F);
   }

   /** Scroll bar indicator on the right edge while a list moves. */
   public static void scrollbar(Frame f, Scroll s, float x, float y, float h, float alpha) {
      if (s.max() <= 0.0F || alpha <= 0.01F) {
         return;
      }
      float knob = Math.max(16.0F, h * s.view / Math.max(s.view, s.content));
      float t = Math.max(0.0F, Math.min(1.0F, s.offset / s.max()));
      f.round(x, y + (h - knob) * t, 2.5F, knob, 1.25F, Theme.withAlpha(0xFFFFFF, (int)(90 * alpha)));
   }

   // ------------------------------------------------------------------------------------------------ formatting

   public static String temp(PhoneModel m, float c) {
      if (m.settings.celsius) {
         return Math.round(c) + "°";
      }
      return Math.round(c * 9.0F / 5.0F + 32.0F) + "°";
   }

   public static String tempUnit(PhoneModel m, float c) {
      return m.settings.celsius ? Math.round(c) + "°C" : Math.round(c * 9.0F / 5.0F + 32.0F) + "°F";
   }

   public static String wind(PhoneModel m, float kmh) {
      return m.settings.celsius ? Math.round(kmh) + " km/h" : Math.round(kmh * 0.621371F) + " mph";
   }

   private static final String[] NUMS = new String[1000];

   /** A small number as text without a new string each frame (0..999 are cached). */
   public static String num(int v) {
      if (v >= 0 && v < NUMS.length) {
         String s = NUMS[v];
         if (s == null) {
            s = Integer.toString(v);
            NUMS[v] = s;
         }
         return s;
      }
      return Integer.toString(v);
   }

   /** every clock string, built once: [0] 12-hour, [1] 24-hour, [2] 12-hour with AM/PM (no per-frame strings) */
   private static final String[][] CLOCKS = new String[3][1440];

   private static String clockText(int kind, int minuteOfDay) {
      int md = Math.floorMod(minuteOfDay, 1440);
      String s = CLOCKS[kind][md];
      if (s == null) {
         int h = md / 60, mi = md % 60;
         String mm = (mi < 10 ? "0" : "") + mi;
         int h12 = h % 12 == 0 ? 12 : h % 12;
         s = switch (kind) {
            case 0 -> h12 + ":" + mm;
            case 1 -> (h < 10 ? "0" : "") + h + ":" + mm;
            default -> h12 + ":" + mm + (md < 720 ? " AM" : " PM");
         };
         CLOCKS[kind][md] = s;
      }
      return s;
   }

   public static String clock(PhoneModel m, int minuteOfDay) {
      return clockText(m.settings.clock24 ? 1 : 0, minuteOfDay);
   }

   public static String ampm(PhoneModel m, int minuteOfDay) {
      return m.settings.clock24 ? "" : (Math.floorMod(minuteOfDay, 1440) < 720 ? "AM" : "PM");
   }

   public static String clockFull(PhoneModel m, int minuteOfDay) {
      return clockText(m.settings.clock24 ? 1 : 2, minuteOfDay);
   }

   /** Minecraft day time to minute of day (0 = 06:00). */
   public static int minute(long dayTime) {
      return (int)((Math.floorMod(dayTime, 24000L) * 1440L / 24000L + 360L) % 1440L);
   }

   public static String distance(double blocks) {
      if (blocks < 1000.0) {
         return Math.round(blocks) + " m";
      }
      return String.format(java.util.Locale.ROOT, "%.1f km", blocks / 1000.0);
   }

   public static String duration(long seconds) {
      seconds = Math.max(0L, seconds);
      if (seconds >= 3600L) {
         return seconds / 3600L + " h " + seconds % 3600L / 60L + " min";
      }
      if (seconds >= 60L) {
         return seconds / 60L + ":" + (seconds % 60L < 10L ? "0" : "") + seconds % 60L;
      }
      return seconds + " s";
   }

   public static final String[] COMPASS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

   /** Compass point of a bearing in degrees (0 = north, clockwise). */
   public static String point(float deg) {
      return COMPASS[Math.floorMod(Math.round(deg / 45.0F), 8)];
   }

   /** Bearing (degrees from north, clockwise) from one spot to another, Minecraft axes (+z south, +x east). */
   public static float bearing(double fromX, double fromZ, double toX, double toZ) {
      double dx = toX - fromX, dz = toZ - fromZ;
      double deg = Math.toDegrees(Math.atan2(dx, -dz));
      return (float)((deg + 360.0) % 360.0);
   }
}
