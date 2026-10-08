import com.formaworks.frontierhunts.client.settings.SettingsLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * [gui] Offline layout check of the Frontier settings screen (tools/gui/run.sh).
 *
 * For every window size x GUI scale it computes the screen's layout with SettingsLayout (the same code the screen uses),
 * stacks every page's content at scroll 0 and at max scroll, and checks: everything inside the window and panel, tabs and
 * footer buttons never overlap each other or the content, at least one full row visible, last item reachable by
 * scrolling, row label areas never run into their controls, slider knob never touches the value text, preset cards
 * inside the column. Prints one line per size and every problem; exits 1 on a problem.
 *
 * Page specs mirror FrontierSettingsScreen.buildPage(): C cards, D preset detail, S section, T status, I info (chars),
 * R option row, L list row (pack picker).
 */
public final class LayoutDump {
   static final String[][] PAGES = {
      {"Graphics preset", "C D S I170 R R R R R S T T I220 L L L L"}, // [presets] 2 cards, Tune Ultra rows, 2 x 2 pack rows
      {"Trees & world", "S R S R R R R S R R R R R"},
      {"Wildlife", "R R R"},
      {"Atmosphere & weather", "R R S R R R R"},
      {"Seasons", "R R R"},
      {"Effects", "R R S R R"},
      {"Sound", "S R R R R R R R R R S R R"},
      {"Interface & HUD", "R R R S R R R"},
      {"Gameplay aids", "R R R"},
      {"Performance", "R R S R R"},
   };

   static final List<String> problems = new ArrayList<>();
   static String where;

   static void fail(String msg) {
      problems.add(where + ": " + msg);
   }

   record Rect(String name, int x, int y, int w, int h) {
      int r() { return x + w; }
      int b() { return y + h; }
      boolean overlaps(Rect o) { return x < o.r() && o.x < r() && y < o.b() && o.y < b(); }
      boolean inside(Rect o) { return x >= o.x && y >= o.y && r() <= o.r() && b() <= o.b(); }
      public String toString() { return name + "[" + x + "," + y + " " + w + "x" + h + "]"; }
   }

   static void inside(Rect a, Rect b) {
      if (!a.inside(b)) fail(a + " outside " + b);
   }

   static void apart(Rect a, Rect b) {
      if (a.overlaps(b)) fail(a + " overlaps " + b);
   }

   public static void main(String[] args) {
      if (args.length == 4 && args[0].equals("rects")) {
         rects(Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]));
         return;
      }
      int[][] windows = {{854, 480}, {1920, 1080}, {1280, 720}, {1366, 768}, {2560, 1440}, {640, 480}, {800, 600}, {1024, 768}};
      StringBuilder out = new StringBuilder();
      for (int[] win : windows) {
         int maxScale = 1;
         while (win[0] / (maxScale + 1) >= 320 && win[1] / (maxScale + 1) >= 240) maxScale++;
         for (int scale = 1; scale <= 4; scale++) {
            // Minecraft clamps the GUI scale to what fits (>= 320x240 scaled); scales beyond that are checked too, as a
            // stress test for windows smaller than Minecraft allows.
            int w = (int)Math.ceil(win[0] / (double)scale);
            int h = (int)Math.ceil(win[1] / (double)scale);
            where = win[0] + "x" + win[1] + "@" + scale + (scale > maxScale ? "(clamped by MC to " + maxScale + ")" : "") + " -> " + w + "x" + h;
            SettingsLayout l = SettingsLayout.of(w, h, PAGES.length);
            out.append(where).append("  ").append(l).append('\n');
            check(l);
         }
      }
      // a few odd sizes
      for (int[] s : new int[][]{{320, 240}, {321, 241}, {400, 250}, {500, 300}, {600, 330}, {360, 200}, {300, 180}, {240, 150}, {213, 120}, {1000, 200}, {200, 600}}) {
         where = "scaled " + s[0] + "x" + s[1];
         SettingsLayout l = SettingsLayout.of(s[0], s[1], PAGES.length);
         out.append(where).append("  ").append(l).append('\n');
         check(l);
      }
      System.out.print(out);
      if (problems.isEmpty()) {
         System.out.println("LAYOUT OK: no overlaps, nothing off screen, every page scrolls to its last item.");
      } else {
         problems.forEach(p -> System.out.println("PROBLEM " + p));
         System.exit(1);
      }
   }

   /** Prints the rectangles of one page (for tools/gui/wireframe.py). */
   static void rects(int w, int h, int pageIndex) {
      SettingsLayout l = SettingsLayout.of(w, h, PAGES.length);
      System.out.println("screen 0 0 " + w + " " + h);
      System.out.println("panel " + l.px + " " + l.py + " " + l.pw + " " + l.ph);
      if (l.compact) {
         System.out.println("selector " + l.selX + " " + l.selY + " " + l.selW + " " + l.selH + " " + PAGES[pageIndex][0]);
      } else {
         System.out.println("sidebar " + (l.px + 4) + " " + (l.py + 4) + " " + (l.sideW - 6) + " " + (l.ph - 8));
         for (int i = 0; i < l.pages; i++) {
            System.out.println("tab " + l.tabX + " " + (l.tabY + i * l.tabStep) + " " + l.tabW + " " + l.tabH + " " + PAGES[i][0]);
         }
         System.out.println("title " + l.contentX + " " + l.titleY + " " + (l.contentW - 104) + " 14 " + PAGES[pageIndex][0]);
      }
      if (l.showPill) System.out.println("pill " + (l.pillRight - 96) + " " + (l.compact ? l.selY + 1 : l.pillY) + " 96 14");
      System.out.println("content " + l.contentX + " " + l.contentY + " " + l.contentW + " " + l.contentH);
      System.out.println("reset " + l.resetX + " " + l.btnY + " " + l.resetW + " " + l.btnH);
      System.out.println("done " + l.doneX + " " + l.btnY + " " + l.doneW + " " + l.btnH);
      System.out.println("footleft " + l.contentX + " " + l.btnY + " " + Math.max(0, l.footLeftW) + " " + l.btnH);
      String[] tokens = PAGES[pageIndex][1].split(" ");
      int[] heights = new int[tokens.length];
      for (int i = 0; i < tokens.length; i++) {
         char k = tokens[i].charAt(0);
         heights[i] = k == 'C' ? l.cardGridHeight(2) : k == 'D' ? 64 : k == 'S' ? 14 : k == 'T' ? 18 : k == 'I' ? 44 : l.rowH;
      }
      int[] ys = new int[tokens.length];
      l.stack(heights, 0, ys);
      int[] g = new int[4];
      for (int i = 0; i < tokens.length; i++) {
         char k = tokens[i].charAt(0);
         System.out.println("item" + k + " " + l.contentX + " " + ys[i] + " " + l.contentW + " " + heights[i]);
         if (k == 'R' || k == 'L') {
            l.row(l.contentX, ys[i], l.contentW, heights[i], k == 'L', g);
            System.out.println("label " + (l.contentX + 10) + " " + (ys[i] + (l.stacked ? 5 : 6)) + " " + g[3] + " 9");
            if (!l.stacked) System.out.println("desc " + (l.contentX + 10) + " " + (ys[i] + 19) + " " + g[3] + " 7");
            System.out.println("control " + g[0] + " " + (g[2] - 8) + " " + g[1] + " 17");
         } else if (k == 'C') {
            for (int c = 0; c < 2; c++) { // [presets] Vanilla, Ultra
               System.out.println("card " + (l.contentX + c % l.cardColumns() * (l.cardWidth() + 6)) + " " + (ys[i] + c / l.cardColumns() * (l.cardHeight() + 6)) + " " + l.cardWidth() + " " + l.cardHeight());
            }
         }
      }
   }

   static void check(SettingsLayout l) {
      Rect screen = new Rect("screen", 0, 0, l.width, l.height);
      Rect panel = new Rect("panel", l.px, l.py, l.pw, l.ph);
      inside(panel, screen);
      Rect content = new Rect("content", l.contentX, l.contentY, l.contentW, l.contentH);
      inside(content, panel);
      if (l.contentH < l.rowH) fail("content area " + l.contentH + "px shows no full row (" + l.rowH + ")");
      if (l.contentY <= l.headerLineY) fail("content starts above the header line");
      if (content.b() > l.footLineY) fail("content runs into the footer line");
      Rect done = new Rect("done", l.doneX, l.btnY, l.doneW, l.btnH);
      Rect reset = new Rect("reset", l.resetX, l.btnY, l.resetW, l.btnH);
      inside(done, panel);
      inside(reset, panel);
      apart(done, reset);
      apart(done, content);
      apart(reset, content);
      if (l.footLeftW >= 56 && l.contentX + 56 > l.resetX - 8) fail("preset block runs into the Reset button");
      Rect header = new Rect("header", l.contentX, l.py, l.contentW, l.headerLineY - l.py);
      inside(header, panel);
      if (l.compact) {
         Rect sel = new Rect("page selector", l.selX, l.selY, l.selW, l.selH);
         inside(sel, header);
         if (l.showPill) apart(new Rect("pill", l.pillRight - 96, l.selY + 1, 96, 14), sel);
         if (l.subtitleY >= 0 && l.subtitleY < sel.b()) fail("subtitle under the page selector");
      } else {
         Rect side = new Rect("sidebar", l.px + 4, l.py + 4, l.sideW - 6, l.ph - 8);
         inside(side, panel);
         apart(side, content);
         apart(side, done);
         apart(side, reset);
         Rect prev = null;
         for (int i = 0; i < l.pages; i++) {
            Rect tab = new Rect("tab" + i, l.tabX, l.tabY + i * l.tabStep, l.tabW, l.tabH);
            inside(tab, side);
            if (prev != null) apart(tab, prev);
            if (l.tabH < 12) fail("tab " + l.tabH + "px tall is too small for text");
            prev = tab;
         }
         if (l.tabY < l.py + (l.compactBrand ? 24 : 38)) fail("tabs overlap the brand");
         if (l.showPill) {
            Rect pill = new Rect("pill", l.pillRight - 96, l.pillY, 96, 14);
            inside(pill, header);
            apart(pill, new Rect("title", l.contentX, l.titleY, l.contentW - 104, 14));
         }
      }
      if (l.scrollbarX <= l.contentX + l.contentW || l.scrollbarX + 3 > l.px + l.pw) fail("scrollbar not in the right margin");

      for (String[] page : PAGES) {
         String saved = where;
         where = saved + " [" + page[0] + "]";
         checkPage(l, page[1], content);
         where = saved;
      }
   }

   static void checkPage(SettingsLayout l, String spec, Rect content) {
      String[] tokens = spec.split(" ");
      int[] heights = new int[tokens.length];
      for (int i = 0; i < tokens.length; i++) {
         String t = tokens[i];
         heights[i] = switch (t.charAt(0)) {
            case 'C' -> l.cardGridHeight(2);
            case 'D' -> 64;
            case 'S' -> 14;
            case 'T' -> 18;
            case 'I' -> {
               int chars = Integer.parseInt(t.substring(1));
               int perLine = Math.max(1, (l.contentW - 4) / 6); // generous: average glyph ~5px
               yield (chars + perLine - 1) / perLine * 10 + 4;
            }
            default -> l.rowH;
         };
      }
      int[] ys = new int[tokens.length];
      int total = l.stack(heights, 0, ys);
      int max = l.maxScroll(total);
      if (ys[0] < l.contentTop()) fail("first item above the content top");
      int[] ys2 = new int[tokens.length];
      l.stack(heights, max, ys2);
      int lastBottom = ys2[tokens.length - 1] + heights[tokens.length - 1];
      if (lastBottom > l.contentBottom()) fail("last item not reachable: bottom " + lastBottom + " > " + l.contentBottom());
      if (max > 0 && ys2[0] + heights[0] > l.contentTop() + total) fail("scroll range wrong");
      int[] g = new int[4];
      for (int i = 0; i < tokens.length; i++) {
         char k = tokens[i].charAt(0);
         Rect item = new Rect(tokens[i] + "#" + i, l.contentX, ys[i], l.contentW, heights[i]);
         if (item.x < content.x || item.r() > content.r()) fail(item + " wider than the content column");
         if (i > 0 && ys[i] < ys[i - 1] + heights[i - 1]) fail(item + " overlaps the item above");
         if (k == 'R' || k == 'L') {
            l.row(item.x, item.y, item.w, item.h, k == 'L', g);
            Rect ctl = new Rect("control", g[0], g[2] - 9, g[1], 18);
            inside(ctl, item);
            if (g[3] < 40) fail(item + " label area only " + g[3] + "px");
            if (l.stacked) {
               if (item.y + 16 > ctl.y) fail("stacked label runs into the control");
            } else if (item.x + 10 + g[3] > g[0]) {
               fail("label area runs into the control");
            }
            int tx = SettingsLayout.sliderTrackX(g[0]);
            int tw = SettingsLayout.sliderTrackW(g[1]);
            if (tx + tw + 6 > g[0] + g[1] - SettingsLayout.SLIDER_VALUE_W) fail("slider knob touches the value text");
            if (tw < 30) fail("slider track only " + tw + "px");
            if (g[1] < 60) fail("control only " + g[1] + "px");
         } else if (k == 'C') {
            int cols = l.cardColumns();
            int cw = l.cardWidth();
            for (int c = 0; c < 2; c++) { // [presets] Vanilla, Ultra
               Rect card = new Rect("card" + c, item.x + c % cols * (cw + 6), item.y + c / cols * (l.cardHeight() + 6), cw, l.cardHeight());
               inside(card, item);
               if (cw < 60) fail("cards only " + cw + "px wide");
            }
         }
      }
   }
}
