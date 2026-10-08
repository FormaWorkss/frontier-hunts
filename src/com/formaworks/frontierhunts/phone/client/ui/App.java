package com.formaworks.frontierhunts.phone.client.ui;

/**
 * [phone] One app on the Field Phone. Apps are immediate-mode: every frame they draw their current page and register
 * tap zones ({@link Frame#zone}); a tap on a zone comes back as {@link #tap(int, long)}. Pages inside an app slide in
 * and out like a phone's navigation stack ({@link #push(int)}, {@link #back()}).
 */
public abstract class App {
   public final String id;
   public final String name;
   /** index on the "apps" icon sheet */
   public final int icon;
   protected PhoneUi ui;
   protected PhoneModel m;
   protected PhoneActions act;
   protected Txt txt;
   // navigation stack
   private final int[] stack = new int[8];
   private int depth;
   private int prevPage = -1;
   private float navT = 1.0F;
   private boolean navBack;
   protected final Scroll[] scroll = new Scroll[16];

   protected App(String id, String name, int icon) {
      this.id = id;
      this.name = name;
      this.icon = icon;
      for (int i = 0; i < this.scroll.length; i++) {
         this.scroll[i] = new Scroll();
      }
   }

   void attach(PhoneUi ui) {
      this.ui = ui;
      this.m = ui.model;
      this.act = ui.actions;
      this.txt = ui.txt;
   }

   // ------------------------------------------------------------------------------------------------ lifecycle

   /** The app came to the front. */
   public void opened() {
   }

   /** The app left the front (home, another app, phone closed). */
   public void closed() {
   }

   /** 20 times a second while open. */
   public void tick() {
   }

   /** Light status bar text (dark app) by default. */
   public boolean lightStatus() {
      return true;
   }

   /** The status bar draws over the app without its own backdrop (the app paints the area). */
   public int statusTint() {
      return 0;
   }

   /** The phone turns sideways for this page (photo viewer, the arcade game). */
   public boolean landscape() {
      return this.landscapePage(this.page());
   }

   protected boolean landscapePage(int page) {
      return false;
   }

   /** The pointer is hidden over the phone (a game draws its own crosshair). */
   public boolean hidesCursor() {
      return false;
   }

   /** The app needs every key (a game): Esc still goes back. */
   public boolean grabsKeys() {
      return false;
   }

   // ------------------------------------------------------------------------------------------------ pages

   public int page() {
      return this.stack[this.depth];
   }

   public int depth() {
      return this.depth;
   }

   protected void push(int page) {
      if (this.depth + 1 < this.stack.length) {
         this.prevPage = this.page();
         this.stack[++this.depth] = page;
         // turning the phone is the transition: no slide on top of it
         this.navT = this.landscapePage(this.prevPage) != this.landscapePage(page) ? 1.0F : 0.0F;
         this.navBack = false;
         this.scroll[page & 15].reset();
         this.act.sound(PhoneActions.Sfx.TAP);
      }
   }

   /** Replaces the current page without animation. */
   protected void replace(int page) {
      this.stack[this.depth] = page;
   }

   protected void home(int page) {
      this.depth = 0;
      this.stack[0] = page;
      this.navT = 1.0F;
      this.prevPage = -1;
   }

   /** Back inside the app; false when already on its first page (the phone then goes home). */
   public boolean back() {
      if (this.depth == 0) {
         return false;
      }
      this.prevPage = this.page();
      this.depth--;
      this.navT = this.landscapePage(this.prevPage) != this.landscapePage(this.page()) ? 1.0F : 0.0F;
      this.navBack = true;
      this.act.sound(PhoneActions.Sfx.BACK);
      return true;
   }

   public Scroll scroller() {
      return this.scroll[this.page() & 15];
   }

   /** Draws the app: the current page, sliding over the previous one during a navigation. */
   public final void render(Frame f, float w, float h, float dt) {
      if (this.navT < 1.0F) {
         this.navT = Math.min(1.0F, this.navT + dt / 0.26F);
      }
      for (Scroll s : this.scroll) {
         s.update(dt);
      }
      if (this.navT >= 1.0F || this.prevPage < 0) {
         this.drawPage(f, this.page(), w, h);
         return;
      }
      float e = Theme.easeOut(this.navT);
      int under = this.navBack ? this.page() : this.prevPage;
      int over = this.navBack ? this.prevPage : this.page();
      float k = this.navBack ? 1.0F - e : e;
      boolean rec = f.recording;
      // the page underneath drifts a third of the way, dimmed
      f.push();
      f.translate(-w * 0.3F * k, 0.0F);
      f.recording = false;
      this.drawPage(f, under, w, h);
      f.recording = rec;
      f.pop();
      f.fill(0.0F, 0.0F, w, h, Theme.withAlpha(0, (int)(110 * k)));
      f.push();
      f.translate(w * (1.0F - k), 0.0F);
      f.recording = rec && !this.navBack;
      // a soft shadow along the incoming page's left edge
      f.hgradient(-8.0F, 0.0F, 8.0F, h, 0x00000000, 0x50000000);
      this.drawPage(f, over, w, h);
      f.recording = rec;
      f.pop();
   }

   protected abstract void drawPage(Frame f, int page, float w, float h);

   // ------------------------------------------------------------------------------------------------ scrolling pages

   /**
    * Starts drawing a scrolling page below {@code top}: clips and shifts by the page's scroll offset. Draw the content
    * from the returned y, then call {@link #endScroll} with the y below the last thing drawn.
    */
   protected float beginScroll(Frame f, float top, float w, float h) {
      Scroll s = this.scroller();
      s.view = h - top;
      f.clip(0.0F, top, w, h - top);
      f.push();
      f.translate(0.0F, -s.offset);
      return top;
   }

   protected void endScroll(Frame f, float top, float bottom, float w, float h) {
      f.pop();
      f.unclip();
      Scroll s = this.scroller();
      s.content = bottom - top + 14.0F;
      float moving = s.dragging() ? 1.0F : 0.0F;
      float a = this.ui.anim(System.identityHashCode(s) * 31L + 7L, moving, moving > 0.0F ? 20.0F : 3.0F);
      Ui.scrollbar(f, s, w - 4.0F, top + 2.0F, h - top - 14.0F, a);
   }

   // ------------------------------------------------------------------------------------------------ input

   public void tap(int id, long data) {
   }

   /** A press anywhere (games use raw presses); return true to swallow the tap that would follow. */
   public boolean press(float x, float y, int button) {
      return false;
   }

   public void release(float x, float y, int button) {
   }

   /** Mouse moved (games); x, y in screen coordinates. */
   public void mouse(float x, float y) {
   }

   /** Mouse wheel; return false to scroll the page. */
   public boolean wheel(float amount) {
      return false;
   }

   /** A drag step (dx, dy since the last one); return false to let the page's scroller have it. */
   public boolean drag(float x, float y, float dx, float dy) {
      return false;
   }

   public boolean key(int key, int mods) {
      return false;
   }

   public boolean typed(char c) {
      return false;
   }
}
