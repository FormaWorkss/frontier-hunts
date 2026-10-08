package com.formaworks.frontierhunts.client.settings;

/**
 * [gui] Layout math of the Frontier settings screen, in GUI pixels. Pure Java (no Minecraft classes) so
 * tools/gui/LayoutDump can check it for every window size and GUI scale offline.
 *
 * <pre>
 * +-------------------------------------------------------------+
 * | BRAND     | Page title                       [shader pill]  |  header (title + subtitle, or a page selector
 * | tab       | subtitle                                        |          in compact mode)
 * | tab       |-------------------------------------------------|
 * | tab       | content items (rows, headers, cards), scrolled  |
 * | ...       |                                                 |
 * |           |-------------------------------------------------|
 * |           | PRESET / GPU LOAD / notice     [Reset] [Done]   |  footer
 * +-------------------------------------------------------------+
 * </pre>
 *
 * Compact mode (narrow or very short windows) drops the sidebar and shows a page selector in the header instead.
 */
public final class SettingsLayout {
   public static final int MAX_W = 560;
   public static final int MAX_H = 340;
   public static final int MARGIN = 8;
   public static final int MIN_TAB = 14;
   public static final int MAX_TAB = 22;
   public static final int ROW_GAP = 4;
   /** Rows narrower than this put the control under the label. */
   public static final int STACK_BELOW = 250;

   public final int width;
   public final int height;
   public final int pages;
   public int px, py, pw, ph;
   public boolean compact;
   public boolean shortHeader;
   public boolean compactBrand;
   public int sideW, brandH, tabX, tabY, tabW, tabH, tabStep;
   public int titleY, subtitleY, headerLineY;
   public int selX, selY, selW, selH;
   public boolean showPill;
   public int pillRight, pillY;
   public int contentX, contentY, contentW, contentH;
   public int footLineY, btnY, btnH, doneX, doneW, resetX, resetW;
   public boolean shortReset;
   public int footLeftW;
   public boolean stacked;
   public int rowH;
   public int scrollbarX;

   private SettingsLayout(int width, int height, int pages) {
      this.width = width;
      this.height = height;
      this.pages = Math.max(1, pages);
   }

   public static SettingsLayout of(int width, int height, int pages) {
      SettingsLayout l = new SettingsLayout(width, height, pages);
      l.compute();
      return l;
   }

   private void compute() {
      this.pw = Math.max(120, Math.min(this.width - 2 * MARGIN, MAX_W));
      this.ph = Math.max(96, Math.min(this.height - 2 * MARGIN, MAX_H));
      this.pw = Math.min(this.pw, this.width);
      this.ph = Math.min(this.ph, this.height);
      this.px = (this.width - this.pw) / 2;
      this.py = (this.height - this.ph) / 2;
      this.shortHeader = this.ph < 200;

      // sidebar: needs room for every tab and a usable content column
      this.sideW = this.pw < 440 ? 96 : 118;
      this.brandH = 46;
      this.compactBrand = false;
      int space = this.ph - this.brandH - 8;
      if (space < this.pages * MIN_TAB) {
         this.brandH = 28;
         this.compactBrand = true;
         space = this.ph - this.brandH - 8;
      }
      this.compact = this.pw - this.sideW - 24 < 200 || space < this.pages * MIN_TAB;
      if (this.compact) {
         this.sideW = 0;
         this.contentX = this.px + 12;
         this.contentW = this.pw - 24;
      } else {
         this.tabStep = Math.max(MIN_TAB, Math.min(MAX_TAB, space / this.pages));
         this.tabH = this.tabStep - 2;
         this.tabX = this.px + 6;
         this.tabW = this.sideW - 12;
         this.tabY = this.py + this.brandH;
         this.contentX = this.px + this.sideW + 12;
         this.contentW = this.pw - this.sideW - 24;
      }
      this.scrollbarX = this.px + this.pw - 8;

      // header
      if (this.shortHeader) {
         this.titleY = this.py + 8;
         this.subtitleY = -1;
         this.headerLineY = this.py + 27;
      } else {
         this.titleY = this.py + 11;
         this.subtitleY = this.py + 27;
         this.headerLineY = this.py + 40;
      }
      this.contentY = this.headerLineY + (this.shortHeader ? 5 : 10);
      this.selH = 17;
      this.selX = this.contentX;
      this.selY = this.py + (this.shortHeader ? 5 : 7);
      this.selW = Math.min(this.contentW, 200);
      this.pillY = this.py + 12;
      this.pillRight = this.contentX + this.contentW;
      // the pill needs ~96px beside the title (or the compact page selector)
      this.showPill = this.compact ? this.contentW - this.selW >= 104 : this.contentW >= 300;

      // footer
      this.btnH = this.ph < 160 ? 16 : 18;
      this.btnY = this.py + this.ph - (this.ph < 160 ? 21 : 26);
      this.footLineY = this.btnY - (this.ph < 160 ? 4 : 6);
      this.contentH = this.footLineY - 4 - this.contentY;
      this.doneW = this.contentW < 260 ? 56 : 76;
      this.doneX = this.contentX + this.contentW - this.doneW;
      this.shortReset = this.contentW < 300;
      this.resetW = this.shortReset ? 54 : 100;
      this.resetX = this.doneX - 6 - this.resetW;
      this.footLeftW = this.resetX - 8 - this.contentX;

      // rows
      this.stacked = this.contentW < STACK_BELOW;
      this.rowH = this.stacked ? 40 : 32;
   }

   /** Width of an option row's control (list rows - pack pickers - are wider). */
   public int controlWidth(int rowW, boolean list) {
      if (this.stacked) {
         return rowW - 20;
      }
      return list ? Math.min(170, Math.max(96, rowW / 2 - 10)) : Math.min(128, Math.max(84, rowW / 3));
   }

   /** Option row geometry: out = {controlX, controlW, controlCentreY, textW}. */
   public void row(int x, int y, int w, int h, boolean list, int[] out) {
      int cw = this.controlWidth(w, list);
      if (this.stacked) {
         out[0] = x + 10;
         out[1] = cw;
         out[2] = y + 29;
         out[3] = w - 20;
      } else {
         out[0] = x + w - cw - 9;
         out[1] = cw;
         out[2] = y + h / 2;
         out[3] = w - cw - 26;
      }
   }

   /** Slider track inside a control: x and width (the value text sits right of it, at most SLIDER_VALUE_W wide). */
   public static final int SLIDER_VALUE_W = 40;

   public static int sliderTrackX(int ctlX) {
      return ctlX + 4;
   }

   public static int sliderTrackW(int ctlW) {
      return Math.max(10, ctlW - SLIDER_VALUE_W - 10);
   }

   /** Columns of preset cards for this content width ([presets] two presets, Vanilla and Ultra, side by side). */
   public int cardColumns() {
      return this.contentW >= 180 ? 2 : 1;
   }

   public int cardWidth() {
      int cols = this.cardColumns();
      return (this.contentW - 6 * (cols - 1)) / cols;
   }

   public int cardHeight() {
      return (int)(this.cardWidth() * 0.5625F) + 44;
   }

   /** Height of the card grid for n cards (no trailing gap). */
   public int cardGridHeight(int n) {
      int cols = this.cardColumns();
      int rows = (n + cols - 1) / cols;
      return rows * this.cardHeight() + Math.max(0, rows - 1) * 6;
   }

   /**
    * Stacks content items of the given heights from the top of the content area at the given scroll; writes each item's
    * y into outY and returns the total height (items + gaps + bottom padding).
    */
   public int stack(int[] heights, int scroll, int[] outY) {
      int y = this.contentY + 4 - scroll;
      int total = 4;
      for (int i = 0; i < heights.length; i++) {
         outY[i] = y;
         y += heights[i] + ROW_GAP;
         total += heights[i] + ROW_GAP;
      }
      return total + 4;
   }

   public int maxScroll(int total) {
      return Math.max(0, total - this.contentH);
   }

   /** Top of the content viewport (items are clipped to [contentTop, contentBottom)). */
   public int contentTop() {
      return this.contentY;
   }

   public int contentBottom() {
      return this.contentY + this.contentH;
   }

   @Override
   public String toString() {
      return String.format("%dx%d panel %d,%d %dx%d %s%s content %d,%d %dx%d rowH %d",
         this.width, this.height, this.px, this.py, this.pw, this.ph, this.compact ? "compact" : "sidebar " + this.sideW + " tab " + this.tabStep,
         this.shortHeader ? " short-header" : "", this.contentX, this.contentY, this.contentW, this.contentH, this.rowH);
   }
}
