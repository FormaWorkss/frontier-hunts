package com.formaworks.frontierhunts.phone.client.ui;

import com.formaworks.frontierhunts.phone.client.ui.Canvas.Font;
import java.util.ArrayList;
import java.util.List;

/**
 * [phone] FieldOS: the Field Phone's whole interface. The rugged case, the screen with its status bar, the live
 * landscape wallpaper and home screen, apps opening from their icons, notification banners, the pull-down shade with
 * quick toggles, and the phone sliding up out of the pocket. Everything is drawn through a {@link Canvas}; the game's
 * screen ({@code phone.client.PhoneScreen}) only forwards input and the window size.
 */
public final class PhoneUi {
   public final PhoneModel model;
   public final PhoneActions actions;
   public final Txt txt = new Txt();
   final Anim anim = new Anim();
   private final Frame frame = new Frame();
   public final List<App> apps = new ArrayList<>();
   /** home grid and dock order */
   private final List<App> grid = new ArrayList<>();
   private final List<App> dock = new ArrayList<>();
   private final Wallpaper wallpaper = new Wallpaper();

   // layout (GUI pixels)
   private float screenW, screenH;
   private float scaleP = 1.0F, scaleL = 1.0F;
   // the phone as drawn this frame: it turns sideways for landscape pages
   private float orient;
   private float cw = Theme.CASE_W, ch = Theme.CASE_H;
   private float sw = Theme.SCREEN_W, sh = Theme.SCREEN_H;
   private float scale = 1.0F;
   private float ox, oy;

   // phone state
   private float openT;
   private boolean closing;
   private App current;
   private App launching;
   /** app open/close transition 0..1 */
   private float appT = 1.0F;
   private boolean appClosing;
   private App leaving;
   private float iconX, iconY, iconS = 30.0F;
   private float shadeT;
   private boolean shade;
   private float dtLast = 1.0F / 60.0F;
   private long lastMillis;

   // input
   private float mouseX = -1, mouseY = -1;
   private int hoverIndex = -1;
   private int downId;
   private long downData;
   private float downX, downY;
   private boolean dragging;
   private boolean pressedApp;
   private float dragLastY;
   private float dragLastX;
   private long dragLastMillis;

   // reused every frame (no per-frame arrays)
   private final String[] hintKeys = {"Esc", ""};
   private static final String[] HINT_WHAT = {"back", "put away"};
   private final G[] qsGlyphs = {G.FLASHLIGHT, G.NIGHT, G.BELL, G.THERMO};
   private final String[] qsLabels = {"Torch", "Red light", "Sound", "°C"};
   private final boolean[] qsOn = new boolean[4];
   private static final int[] QS_COLORS = {Theme.YELLOW, Theme.RED, Theme.VIOLET, Theme.SKY};

   // lock screen: shown when the phone comes out of the pocket after a while (1 = fully shown)
   private boolean locked;
   private float lockT;
   private long putAwayAt;
   /** the phone stays unlocked this long after it was put away (ms) */
   public static final long RELOCK_MS = 20_000L;
   private long dateKey = Long.MIN_VALUE;
   private String dateText = "";

   // banner and toast
   private final List<PhoneModel.Notice> banners = new ArrayList<>();
   private float bannerT;
   private long bannerShownAt;
   private String toast = "";
   private long toastAt;
   private int noticesSeen;

   public PhoneUi(PhoneModel model, PhoneActions actions, List<App> appsInOrder, List<String> dockIds) {
      this.model = model;
      this.actions = actions;
      for (App a : appsInOrder) {
         a.attach(this);
         this.apps.add(a);
         if (dockIds.contains(a.id)) {
            this.dock.add(a);
         } else {
            this.grid.add(a);
         }
      }
      // dock keeps the order of dockIds
      this.dock.sort((a, b) -> Integer.compare(dockIds.indexOf(a.id), dockIds.indexOf(b.id)));
      this.noticesSeen = model.notices.size();
   }

   public App app(String id) {
      for (App a : this.apps) {
         if (a.id.equals(id)) {
            return a;
         }
      }
      return null;
   }

   public App current() {
      return this.current;
   }

   // ------------------------------------------------------------------------------------------------ opening and closing

   /** The phone was taken out: slides up, showing {@code appId} (or the home screen). */
   public void open(String appId) {
      this.openT = 0.0F;
      this.closing = false;
      this.shade = false;
      this.shadeT = 0.0F;
      long nowMs = this.actions.millis();
      this.locked = appId == null && !this.model.station && (this.putAwayAt == 0L || nowMs - this.putAwayAt > RELOCK_MS);
      this.lockT = this.locked ? 1.0F : 0.0F;
      App a = appId == null ? null : this.app(appId);
      if (a != null) {
         this.current = a;
         this.appT = 1.0F;
         this.appClosing = false;
         a.opened();
      } else if (this.current != null) {
         this.current.opened();
      }
      this.noticesSeen = this.model.notices.size();
   }

   /** Shows an app right away (already open phone), e.g. a notification tapped. */
   public void launch(String appId) {
      App a = this.app(appId);
      if (a == null || a == this.current) {
         return;
      }
      if (this.current != null) {
         this.current.closed();
      }
      this.current = a;
      this.appT = 0.0F;
      this.appClosing = false;
      this.iconX = Theme.SCREEN_W / 2.0F - 15.0F;
      this.iconY = 40.0F;
      this.iconS = 30.0F;
      a.opened();
      this.actions.sound(PhoneActions.Sfx.OPEN);
   }

   /** Is the lock screen up? */
   public boolean locked() {
      return this.locked;
   }

   /** Unlocks (the lock screen slides away to the home screen or the app below). */
   public void unlock() {
      if (this.locked) {
         this.locked = false;
         this.actions.sound(PhoneActions.Sfx.UNLOCK);
      }
   }

   public boolean isClosing() {
      return this.closing;
   }

   /** Starts the slide-down; the screen closes when {@link #closed()} turns true. */
   public void close() {
      if (!this.closing) {
         this.closing = true;
         this.actions.sound(PhoneActions.Sfx.CLOSE);
      }
   }

   public boolean closed() {
      return this.closing && this.openT <= 0.0F;
   }

   /** The phone is put away for good (screen removed). */
   public void removed() {
      this.putAwayAt = Math.max(1L, this.actions.millis());
      if (this.current != null) {
         this.current.closed();
      }
      this.actions.darkroom(false);
   }

   public void goHome() {
      if (this.model.station) {
         this.close();
         return;
      }
      if (this.current != null && !this.appClosing) {
         this.leaving = this.current;
         this.appClosing = true;
         this.appT = 1.0F;
         this.current.closed();
         this.actions.sound(PhoneActions.Sfx.CLOSE);
         this.actions.darkroom(false);
      }
   }

   private void startApp(App a, float ix, float iy, float is) {
      if (a == this.current && !this.appClosing) {
         return;
      }
      if (this.current != null && !this.appClosing) {
         this.current.closed();
      }
      this.current = a;
      this.leaving = null;
      this.appClosing = false;
      this.appT = 0.0F;
      this.iconX = ix;
      this.iconY = iy;
      this.iconS = is;
      a.opened();
      this.actions.sound(PhoneActions.Sfx.OPEN);
   }

   // ------------------------------------------------------------------------------------------------ helpers for apps

   /** Is the mouse over a zone (last frame)? */
   public boolean hot(int id, long data) {
      return this.hoverIndex >= 0 && this.frame.hitId(this.hoverIndex) == id && this.frame.hitData(this.hoverIndex) == data && !this.dragging;
   }

   /** Is a zone being pressed? */
   public boolean down(int id, long data) {
      return this.downId == id && this.downData == data && !this.dragging && this.hot(id, data);
   }

   public float anim(long key, float target, float rate) {
      return this.anim.get(key, target, rate);
   }

   public float dt() {
      return this.dtLast;
   }

   public long millis() {
      return this.actions.millis();
   }

   /** Mouse position in the app's local coordinates (valid while an app is fully open). */
   public float appMouseX() {
      return (this.mouseX - this.ox) / this.scale - Theme.BEZEL;
   }

   public float appMouseY() {
      return (this.mouseY - this.oy) / this.scale - Theme.BEZEL;
   }

   public void toast(String s) {
      this.toast = s == null ? "" : s;
      this.toastAt = this.actions.millis();
   }

   public void notifyBanner(PhoneModel.Notice n) {
      if (this.model.settings.silent || this.model.station) {
         return;
      }
      this.banners.add(n);
      if (this.banners.size() > 4) {
         this.banners.remove(0);
      }
      if (this.banners.size() == 1) {
         this.bannerShownAt = this.actions.millis();
         this.bannerT = 0.0F;
      }
   }

   // ------------------------------------------------------------------------------------------------ frame

   /** Lays out for a window of {@code w} x {@code h} GUI pixels. */
   public void layout(float w, float h) {
      this.screenW = w;
      this.screenH = h;
      this.scaleP = Math.max(0.6F, Math.min(1.3F, (h - 10.0F) / Theme.CASE_H));
      this.scaleL = Math.max(0.6F, Math.min(1.55F, Math.min((w - 60.0F) / Theme.CASE_H, (h - 40.0F) / Theme.CASE_W)));
      this.place();
   }

   /** Case size, scale and position for the current orientation. */
   private void place() {
      float e = Theme.easeInOut(this.orient);
      this.cw = Theme.CASE_W + (Theme.CASE_H - Theme.CASE_W) * e;
      this.ch = Theme.CASE_H + (Theme.CASE_W - Theme.CASE_H) * e;
      this.sw = this.cw - Theme.BEZEL * 2.0F;
      this.sh = this.ch - Theme.BEZEL * 2.0F;
      this.scale = this.scaleP + (this.scaleL - this.scaleP) * e;
      this.ox = (this.screenW - this.cw * this.scale) / 2.0F;
      this.oy = (this.screenH - this.ch * this.scale) / 2.0F;
   }

   public void tick() {
      if (this.current != null) {
         this.current.tick();
      }
      // new notifications that arrived while open
      int n = this.model.notices.size();
      if (n < this.noticesSeen) {
         this.noticesSeen = n;
      }
      while (this.noticesSeen < n) {
         this.notifyBanner(this.model.notices.get(this.noticesSeen++));
      }
   }

   /** Draws everything. {@code dim} = how dark the world behind the phone gets (0..1; 1 = opaque). */
   public void render(Canvas out, float dim) {
      long now = this.actions.millis();
      float dt = this.lastMillis == 0L ? 1.0F / 60.0F : Math.max(0.0F, Math.min(0.1F, (now - this.lastMillis) / 1000.0F));
      this.lastMillis = now;
      this.dtLast = dt;
      this.anim.frame(dt);
      boolean still = this.model.reducedMotion;
      if (this.closing) {
         this.openT = still ? 0.0F : Math.max(0.0F, this.openT - dt / 0.22F);
      } else {
         this.openT = still ? 1.0F : Math.min(1.0F, this.openT + dt / 0.30F);
      }
      this.lockT = still ? (this.locked ? 1.0F : 0.0F) : Theme.approach(this.lockT, this.locked ? 1.0F : 0.0F, 9.0F, dt);
      if (!this.locked && this.lockT < 0.004F) {
         this.lockT = 0.0F;
      }
      boolean land = this.current != null && !this.appClosing && this.appT >= 1.0F && this.current.landscape() && !this.closing;
      float ot = land ? 1.0F : 0.0F;
      if (this.orient != ot && still) {
         this.orient = ot;
      } else if (this.orient != ot) {
         this.orient = ot > this.orient ? Math.min(ot, this.orient + dt / 0.34F) : Math.max(ot, this.orient - dt / 0.34F);
      }
      this.place();
      Frame f = this.frame;
      f.begin(out);
      f.recording = !this.closing;
      f.night = false;
      this.hoverIndex = this.mouseX < 0 ? -1 : f.hit(this.mouseX, this.mouseY);
      float e = this.closing ? Theme.easeInOut(this.openT) : Theme.easeOut(this.openT);
      // the world behind: dimmed (or opaque while photos develop)
      float backdrop = Math.max(dim, 0.55F * e);
      if (backdrop > 0.0F) {
         f.fill(0.0F, 0.0F, this.screenW, this.screenH, Theme.withAlpha(0x050706, (int)(255 * Math.min(1.0F, backdrop))));
      }
      if (dim >= 1.0F) {
         // a quiet landscape glow behind the phone when the world is hidden
         f.gradient(0.0F, 0.0F, this.screenW, this.screenH, 0xFF0B0F0D, 0xFF141A16);
      }
      this.hints(f, e);
      f.push();
      f.translate(this.ox, this.oy + (1.0F - e) * (this.screenH - this.oy + 20.0F));
      f.scale(this.scale);
      this.drawCase(f);
      f.translate(Theme.BEZEL, Theme.BEZEL);
      f.clip(0.0F, 0.0F, this.sw, this.sh);
      f.night = this.model.settings.night;
      this.drawScreen(f, dt, now);
      f.night = false;
      f.unclip();
      this.drawScreenCorners(f);
      f.pop();
   }

   private void hints(Frame f, float e) {
      if (e <= 0.05F || this.screenW < 360.0F) {
         return;
      }
      float a = e * 0.9F;
      f.push();
      f.alpha(a);
      String[] keys = this.hintKeys;
      keys[1] = this.model.keyName;
      String[] what = HINT_WHAT;
      float total = 0.0F;
      for (int i = 0; i < keys.length; i++) {
         total += f.width(keys[i], Font.SMALL) + 8.0F + f.width(what[i], Font.SMALL) + 14.0F;
      }
      float x = this.ox + this.cw * this.scale + 18.0F;
      float y = this.screenH - 26.0F;
      if (x + total > this.screenW - 6.0F) {
         // no room beside the phone (it is turned sideways): under it, centred
         x = (this.screenW - total) / 2.0F;
         y = Math.min(this.screenH - 14.0F, this.oy + this.ch * this.scale + 4.0F);
      }
      for (int i = 0; i < keys.length; i++) {
         float kw = f.width(keys[i], Font.SMALL) + 8.0F;
         f.round(x, y, kw, 12.0F, 3.0F, 0x30FFFFFF);
         f.text(keys[i], x + 4.0F, y + 2.5F, Theme.TEXT2, Font.SMALL);
         f.text(what[i], x + kw + 4.0F, y + 2.5F, Theme.TEXT3, Font.SMALL);
         x += kw + f.width(what[i], Font.SMALL) + 14.0F;
      }
      f.pop();
   }

   // ------------------------------------------------------------------------------------------------ the case

   private void drawCase(Frame f) {
      float w = this.cw, h = this.ch;
      float e = Theme.easeInOut(this.orient);
      // drop shadow
      for (int i = 6; i >= 1; i--) {
         float s = i * 2.2F;
         f.round(-s, -s + 5.0F, w + s * 2.0F, h + s * 2.0F, 26.0F + s, Theme.withAlpha(0, 16 + (6 - i) * 6));
      }
      // side buttons (behind the body): power and volume on the right, the orange field key on the left; turned with the phone
      if (e < 0.08F || e > 0.92F) {
         boolean land = e > 0.5F;
         this.button(f, land, 78.0F, 34.0F, true, 0xFF2C312B, 0xFF3D443B);
         this.button(f, land, 122.0F, 22.0F, true, 0xFF2C312B, 0xFF3D443B);
         this.button(f, land, 96.0F, 26.0F, false, 0xFFB65516, 0xFFE8782A);
      }
      // rubber body: dark olive, a light rim, and the inner frame
      f.round(0.0F, 0.0F, w, h, 25.0F, 0xFF121510);
      f.gradient(1.0F, 12.0F, w - 2.0F, h - 24.0F, 0xFF2E3429, 0xFF22271F);
      f.round(0.8F, 0.8F, w - 1.6F, 24.0F, 24.0F, 0xFF2E3429);
      f.round(0.8F, h - 24.8F, w - 1.6F, 24.0F, 24.0F, 0xFF22271F);
      f.round(3.2F, 3.2F, w - 6.4F, h - 6.4F, 22.0F, 0xFF1A1E17);
      // raised corner bumpers
      float b = 30.0F;
      float[][] corners = {{0.0F, 0.0F}, {w - b, 0.0F}, {0.0F, h - b}, {w - b, h - b}};
      for (float[] c : corners) {
         f.round(c[0] + 0.6F, c[1] + 0.6F, b - 1.2F, b - 1.2F, 22.0F, 0xFF384033);
         f.round(c[0] + 1.6F, c[1] + 1.6F, b - 3.2F, b - 3.2F, 21.0F, 0xFF2B3127);
      }
      // grip ridges along the long edges
      if (e < 0.08F || e > 0.92F) {
         for (int i = 0; i < 9; i++) {
            float g = 160.0F + i * 5.0F;
            if (e < 0.5F) {
               f.round(0.6F, g, 2.2F, 2.6F, 1.0F, 0xFF3A4235);
               f.round(w - 2.8F, g, 2.2F, 2.6F, 1.0F, 0xFF3A4235);
            } else {
               f.round(g, 0.6F, 2.6F, 2.2F, 1.0F, 0xFF3A4235);
               f.round(g, h - 2.8F, 2.6F, 2.2F, 1.0F, 0xFF3A4235);
            }
         }
      }
      // glass
      f.round(Theme.BEZEL - 2.6F, Theme.BEZEL - 2.6F, w - (Theme.BEZEL - 2.6F) * 2.0F, h - (Theme.BEZEL - 2.6F) * 2.0F, Theme.SCREEN_R + 2.6F, 0xFF050605);
      // screws in the bumpers
      int screw = 0xFF4A5244;
      float[][] sc = {{9.0F, 9.0F}, {w - 9.0F, 9.0F}, {9.0F, h - 9.0F}, {w - 9.0F, h - 9.0F}};
      for (float[] c : sc) {
         f.circle(c[0], c[1], 1.7F, screw);
         f.line(c[0] - 1.0F, c[1], c[0] + 1.0F, c[1], 0.5F, 0xFF20241D);
      }
   }

   /**
    * A side button. Portrait: on the right edge ({@code right}) or the left edge at {@code at} from the top. Landscape
    * (the phone turned a quarter left): the right edge is the top, the left edge the bottom.
    */
   private void button(Frame f, boolean land, float at, float len, boolean right, int body, int lit) {
      if (!land) {
         float x = right ? this.cw - 2.0F : -2.6F;
         f.round(x, at, 4.6F, len, 1.8F, body);
         f.round(right ? x : x, at + 1.0F, right ? 1.6F : 2.0F, len - 2.0F, 0.8F, lit);
      } else {
         float y = right ? -2.6F : this.ch - 2.0F;
         f.round(at, y, len, 4.6F, 1.8F, body);
         f.round(at + 1.0F, right ? y : y + 2.6F, len - 2.0F, 2.0F, 0.8F, lit);
      }
   }

   /** Rounds the screen's corners (the content is clipped to a rectangle) and adds the punch-hole camera. */
   private void drawScreenCorners(Frame f) {
      float w = this.sw, h = this.sh, r = Theme.SCREEN_R;
      f.push();
      int glass = 0xFF050605;
      cornerMask(f, 0.0F, 0.0F, r, 0, glass);
      cornerMask(f, w - r, 0.0F, r, 1, glass);
      cornerMask(f, 0.0F, h - r, r, 2, glass);
      cornerMask(f, w - r, h - r, r, 3, glass);
      // camera (top centre; left middle when sideways)
      float e = Theme.easeInOut(this.orient);
      float camX = w / 2.0F + (6.5F - w / 2.0F) * e, camY = 6.5F + (h / 2.0F - 6.5F) * e;
      f.circle(camX, camY, 3.1F, 0xFF020202);
      f.circle(camX, camY, 1.9F, 0xFF0B0E16);
      f.circle(camX - 0.6F, camY - 0.6F, 0.6F, 0x5576A0D0);
      f.pop();
   }

   /** Fills the outside of a quarter circle (the corner outside the rounded screen). */
   private static void cornerMask(Frame f, float x, float y, float r, int which, int color) {
      int steps = 10;
      for (int i = 0; i < steps; i++) {
         float t0 = (float)i / steps, t1 = (float)(i + 1) / steps;
         float a0 = (float)(t0 * Math.PI / 2.0), a1 = (float)(t1 * Math.PI / 2.0);
         // points on the arc relative to the corner circle centre
         float c0 = (float)Math.cos(a0) * r, s0 = (float)Math.sin(a0) * r;
         float c1 = (float)Math.cos(a1) * r, s1 = (float)Math.sin(a1) * r;
         switch (which) {
            case 0 -> { // top-left: centre (x+r, y+r), corner (x, y)
               float cx = x + r, cy = y + r;
               f.quad(x, y, cx - c0, cy - s0, cx - c1, cy - s1, x, y, color);
            }
            case 1 -> {
               float cx = x, cy = y + r;
               f.quad(x + r, y, cx + c0, cy - s0, cx + c1, cy - s1, x + r, y, color);
            }
            case 2 -> {
               float cx = x + r, cy = y;
               f.quad(x, y + r, cx - c0, cy + s0, cx - c1, cy + s1, x, y + r, color);
            }
            default -> {
               float cx = x, cy = y;
               f.quad(x + r, y + r, cx + c0, cy + s0, cx + c1, cy + s1, x + r, y + r, color);
            }
         }
      }
   }

   // ------------------------------------------------------------------------------------------------ the screen

   private void drawScreen(Frame f, float dt, long now) {
      float w = this.sw, h = this.sh;
      if (!this.model.hasPhone && !this.model.station || this.model.battery <= 0 && !this.model.station) {
         this.dead(f, w, h);
         return;
      }
      // app transition
      if (this.model.reducedMotion) {
         this.appT = this.appClosing ? 0.0F : 1.0F;
      }
      if (this.appClosing) {
         this.appT = Math.max(0.0F, this.appT - dt / 0.24F);
         if (this.appT <= 0.0F) {
            this.appClosing = false;
            this.current = null;
            this.leaving = null;
         }
      } else if (this.appT < 1.0F) {
         this.appT = Math.min(1.0F, this.appT + dt / 0.30F);
      }
      App shown = this.appClosing ? this.leaving : this.current;
      boolean full = shown != null && this.appT >= 1.0F && !this.appClosing;
      if (this.lockT >= 1.0F) {
         // only the lock screen: nothing below it is drawn or tappable
         this.lockScreen(f, w, h, now);
         this.statusBar(f, w, null);
         this.toastDraw(f, w, h, now);
         if (this.model.alarmRinging) {
            this.alarmOverlay(f, w, h, now);
         }
         return;
      }
      boolean turning = this.orient > 0.0F && this.orient < 1.0F;
      boolean sideways = this.orient >= 1.0F;
      if (turning) {
         // mid-turn: the app's page for the new orientation, fitted into the turning screen
         f.fill(0.0F, 0.0F, w, h, 0xFF000000);
         if (shown != null) {
            boolean toLand = shown.landscape() && !this.appClosing;
            float tw = toLand ? Theme.SCREEN_H : Theme.SCREEN_W, th = toLand ? Theme.SCREEN_W : Theme.SCREEN_H;
            float k = Math.min(w / tw, h / th);
            float e = Theme.easeInOut(this.orient);
            float a = toLand ? Theme.clamp01((e - 0.35F) / 0.65F) : Theme.clamp01((0.65F - e) / 0.65F);
            f.push();
            f.translate((w - tw * k) / 2.0F, (h - th * k) / 2.0F);
            f.scale(k);
            f.alpha(a);
            boolean rec = f.recording;
            f.recording = false;
            f.fill(0.0F, 0.0F, tw, th, Theme.BG);
            shown.render(f, tw, th, dt);
            f.recording = rec;
            f.pop();
         }
         return;
      }
      if (!full) {
         this.wallpaper.draw(this, f, w, h, now);
         boolean rec = f.recording;
         f.recording = rec && shown == null;
         this.home(f, w, h, shown == null ? 1.0F : 1.0F - this.appT);
         f.recording = rec;
      }
      if (shown != null) {
         float e = this.appClosing ? Theme.easeInOut(this.appT) : Theme.easeOut(this.appT);
         f.push();
         if (!full) {
            // zoom from (or back to) the icon
            float sx = this.iconX + this.iconS / 2.0F, sy = this.iconY + this.iconS / 2.0F;
            float s = this.iconS / w + (1.0F - this.iconS / w) * e;
            float cx = sx + (w / 2.0F - sx) * e;
            float cy = sy + (h / 2.0F - sy) * e;
            f.translate(cx - w * s / 2.0F, cy - h * s / 2.0F);
            f.scale(s);
            f.alpha(Math.min(1.0F, e * 1.6F));
            f.clip(0.0F, 0.0F, w, h);
         }
         boolean rec = f.recording;
         f.recording = rec && full;
         f.fill(0.0F, 0.0F, w, h, Theme.BG);
         shown.render(f, w, h, dt);
         f.recording = rec;
         if (!full) {
            f.unclip();
         }
         f.pop();
         // the gallery covers the whole window while it develops photos behind the phone
         this.actions.darkroom(full && shown instanceof CamsHost host && host.darkroomWanted());
      } else {
         this.actions.darkroom(false);
      }
      if (this.lockT > 0.0F) {
         // unlocking: the lock screen lifts away over the home screen (not tappable on its way out)
         boolean rec = f.recording;
         f.recording = false;
         this.lockScreen(f, w, h, now);
         f.recording = rec;
      }
      if (!sideways) {
         this.statusBar(f, w, shown != null && full ? shown : null);
      }
      if (!sideways) {
         this.homeBar(f, w, h, shown != null && full ? shown : null);
      }
      this.banner(f, w, now);
      this.toastDraw(f, w, h, now);
      if (!sideways) {
         this.shade(f, w, h, dt);
      }
      if (this.model.alarmRinging) {
         this.alarmOverlay(f, w, h, now);
      }
   }

   /** Implemented by the camera app: it wants the darkroom (photos developing behind an opaque window). */
   public interface CamsHost {
      boolean darkroomWanted();
   }

   private void dead(Frame f, float w, float h) {
      f.fill(0.0F, 0.0F, w, h, 0xFF000000);
      float cx = w / 2.0F, cy = h / 2.0F - 20.0F;
      // empty battery
      f.round(cx - 22.0F, cy - 11.0F, 44.0F, 22.0F, 5.0F, 0xFF6A1C17);
      f.round(cx - 20.6F, cy - 9.6F, 41.2F, 19.2F, 4.0F, 0xFF000000);
      f.round(cx + 22.5F, cy - 5.0F, 3.5F, 10.0F, 1.5F, 0xFF6A1C17);
      f.round(cx - 18.5F, cy - 7.5F, 4.0F, 15.0F, 1.5F, 0xFFE5574B);
      if (!this.model.hasPhone) {
         this.txt.paraCenter(f, "No Field Phone on you.", cx, cy + 26.0F, w - 30.0F, Theme.TEXT2, Font.STRONG, 2.0F);
         return;
      }
      f.center("Battery empty", cx, cy + 24.0F, Theme.TEXT2, Font.STRONG);
      this.txt.paraCenter(f, "Charge it by a campfire, camp post or lodge stove, on a vehicle, or sleep the night.", cx, cy + 40.0F, w - 36.0F,
         Theme.TEXT3, Font.SMALL, 2.0F);
   }

   // ------------------------------------------------------------------------------------------------ status bar

   private void statusBar(Frame f, float w, App app) {
      boolean light = app == null || app.lightStatus();
      int fg = light ? Theme.TEXT : Theme.TEXT_DARK;
      int tint = app == null ? 0 : app.statusTint();
      if ((tint >>> 24) != 0) {
         f.fill(0.0F, 0.0F, w, Theme.STATUS_H, tint);
      }
      int minute = Ui.minute(this.model.dayTime);
      String t = Ui.clock(this.model, minute);
      f.text(t, 13.0F, 4.0F, fg, Font.STRONG);
      float rx = w - 12.0F;
      if (this.model.station) {
         f.right("BASE STATION", rx, 4.5F, Theme.GOLD, Font.SMALL);
         return;
      }
      // battery
      int pct = this.model.battery;
      float bw = 17.0F, bh = 8.0F, bx = rx - bw - 1.5F, by = 4.0F;
      f.round(bx, by, bw, bh, 2.4F, Theme.withAlpha(fg, 120));
      f.round(bx + 0.8F, by + 0.8F, bw - 1.6F, bh - 1.6F, 1.8F, light ? 0xFF000000 : 0xFFE8E8E0);
      int bc = pct <= 15 ? Theme.RED : (this.model.charging ? Theme.MOSS : fg);
      f.round(bx + 1.6F, by + 1.6F, Math.max(1.6F, (bw - 3.2F) * pct / 100.0F), bh - 3.2F, 1.2F, bc);
      f.round(rx - 1.2F, by + 2.6F, 1.6F, 2.8F, 0.6F, Theme.withAlpha(fg, 120));
      if (this.model.charging) {
         G.BOLT.draw(f, bx + bw / 2.0F, by + bh / 2.0F, 8.0F, light ? 0xFF000000 : 0xFFFFFFFF);
      }
      String p = pct + "%";
      float px = bx - 3.0F - f.width(p, Font.SMALL);
      f.text(p, px, 4.5F, fg, Font.SMALL);
      // signal bars
      float sx = px - 18.0F;
      for (int i = 0; i < 4; i++) {
         float bh2 = 3.0F + i * 2.0F;
         f.round(sx + i * 3.6F, 12.0F - bh2, 2.4F, bh2, 0.8F, i < this.model.signal ? fg : Theme.withAlpha(fg, 70));
      }
      if (this.model.signal == 0) {
         f.line(sx - 0.5F, 5.0F, sx + 13.0F, 12.0F, 1.0F, Theme.withAlpha(fg, 180));
      }
      float ix = sx - 9.0F;
      if (this.model.flashlight) {
         G.FLASHLIGHT.draw(f, ix, 8.0F, 9.0F, Theme.YELLOW);
         ix -= 11.0F;
      }
      if (this.model.alarmMinute >= 0) {
         G.ALARM.draw(f, ix, 8.0F, 9.0F, fg);
         ix -= 11.0F;
      }
      if (this.model.settings.night) {
         G.NIGHT.draw(f, ix, 8.0F, 9.0F, Theme.RED);
      }
      // tap the status bar for the shade
      f.zone(Ui.Z_STATUS, 0.0F, 0.0F, w, Theme.STATUS_H + 2.0F);
   }

   private void homeBar(Frame f, float w, float h, App app) {
      boolean light = app == null || app.lightStatus();
      boolean hot = this.hot(Ui.Z_HOME, 0L);
      float bw = hot ? 56.0F : 48.0F;
      f.round(w / 2.0F - bw / 2.0F, h - 6.5F, bw, 3.0F, 1.5F, Theme.withAlpha(light ? 0xFFFFFF : 0x000000, hot ? 230 : 160));
      f.zone(Ui.Z_HOME, w / 2.0F - 40.0F, h - Theme.HOME_BAR_H, 80.0F, Theme.HOME_BAR_H);
   }

   // ------------------------------------------------------------------------------------------------ home screen

   private static final float ICON = 31.0F;
   private static final int COLS = 4;

   private void home(Frame f, float w, float h, float vis) {
      if (vis <= 0.0F) {
         return;
      }
      f.push();
      // the home screen recedes slightly as an app grows over it
      float s = 0.94F + 0.06F * vis;
      f.translate(w * (1.0F - s) / 2.0F, h * (1.0F - s) / 2.0F);
      f.scale(s);
      f.alpha(Math.min(1.0F, vis * 1.2F));
      this.widget(f, w);
      float cell = (w - 12.0F) / COLS;
      float gy = 148.0F;
      for (int i = 0; i < this.grid.size(); i++) {
         App a = this.grid.get(i);
         float cx = 6.0F + cell * (i % COLS) + cell / 2.0F;
         float cy = gy + (i / COLS) * 46.0F;
         this.icon(f, a, cx - ICON / 2.0F, cy, i, true);
      }
      // dock
      float dy = h - 55.0F;
      f.round(6.0F, dy, w - 12.0F, 45.0F, 16.0F, 0x5A101411);
      f.round(6.0F, dy, w - 12.0F, 0.6F, 0.3F, 0x18FFFFFF);
      float dcell = (w - 12.0F) / Math.max(4, this.dock.size());
      for (int i = 0; i < this.dock.size(); i++) {
         App a = this.dock.get(i);
         float cx = 6.0F + dcell * i + dcell / 2.0F;
         this.icon(f, a, cx - ICON / 2.0F, dy + 7.0F, 100 + i, false);
      }
      f.pop();
   }

   private void icon(Frame f, App a, float x, float y, int slot, boolean label) {
      boolean hot = this.hot(Ui.Z_APP, slot);
      boolean down = this.down(Ui.Z_APP, slot);
      float lift = this.anim(0x51C0L + slot, down ? 0.92F : (hot ? 1.06F : 1.0F), 22.0F);
      f.push();
      f.translate(x + ICON / 2.0F, y + ICON / 2.0F);
      f.scale(lift);
      f.round(-ICON / 2.0F + 0.5F, -ICON / 2.0F + 1.4F, ICON - 1.0F, ICON, 8.0F, 0x50000000);
      int col = a.icon % 8, row = a.icon / 8;
      f.sprite("apps", -ICON / 2.0F, -ICON / 2.0F, ICON, ICON, col / 8.0F, row / 3.0F, (col + 1) / 8.0F, (row + 1) / 3.0F, 0xFFFFFFFF);
      f.pop();
      int badge = this.badge(a);
      if (badge > 0) {
         String b = badge > 9 ? "9+" : Integer.toString(badge);
         float bw = Math.max(10.0F, f.width(b, Font.SMALL) + 6.0F);
         f.round(x + ICON - bw + 4.0F, y - 3.0F, bw, 10.0F, 5.0F, Theme.RED);
         f.center(b, x + ICON - bw / 2.0F + 4.0F, y - 1.5F, 0xFFFFFFFF, Font.SMALL);
      }
      if (label) {
         String n = this.txt.fit(f, a.name, 40.0F, Font.SMALL);
         f.center(n, x + ICON / 2.0F + 0.4F, y + ICON + 4.4F, 0x90000000, Font.SMALL);
         f.center(n, x + ICON / 2.0F, y + ICON + 4.0F, 0xFFF4F2EA, Font.SMALL);
      }
      f.zone(Ui.Z_APP, x - 4.0F, y - 3.0F, ICON + 8.0F, ICON + (label ? 16.0F : 6.0F), slot);
   }

   private int badge(App a) {
      PhoneModel m = this.model;
      return switch (a.id) {
         case "contracts" -> ("ready".equals(m.contracts.contractStatus) ? 1 : 0) + (m.contracts.assignState == 2 ? 1 : 0)
            + (m.contracts.storyAmount > 0 && m.contracts.storyCount >= m.contracts.storyAmount && !m.contracts.storyDone ? 1 : 0);
         case "chess" -> this.invites(0);
         case "dice" -> this.invites(1);
         case "flush" -> this.invites(2);
         case "weather" -> m.weather.alerts.isEmpty() ? 0 : 1;
         case "messages" -> m.messages.unread;
         default -> 0;
      };
   }

   private int invites(int game) {
      int n = 0;
      for (PhoneModel.Invite i : this.model.games.invites) {
         if (i.game() == game) {
            n++;
         }
      }
      for (PhoneModel.Online o : this.model.games.online) {
         if (o.game == game && o.status == 0 && game != 2 && this.myTurn(o)) {
            n++;
         }
      }
      return n;
   }

   private boolean myTurn(PhoneModel.Online o) {
      return o.state.length > 0 && o.state[0] == o.me;
   }

   /** Clock, date, weather and the hunting outlook on top of the home screen. */
   private void widget(Frame f, float w) {
      PhoneModel m = this.model;
      int minute = Ui.minute(m.dayTime);
      String t = Ui.clock(m, minute);
      float tw = f.width(t, Font.HUGE);
      float tx = w / 2.0F - tw / 2.0F;
      f.text(t, tx + 0.6F, 27.6F, 0x50000000, Font.HUGE);
      f.text(t, tx, 27.0F, 0xFFF7F5EE, Font.HUGE);
      String ap = Ui.ampm(m, minute);
      if (!ap.isEmpty()) {
         f.text(ap, tx + tw + 2.0F, 52.0F, 0xD8F7F5EE, Font.STRONG);
      }
      String date = this.date();
      f.center(date, w / 2.0F + 0.4F, 73.4F, 0x70000000, Font.STRONG);
      f.center(date, w / 2.0F, 73.0F, 0xFFF1EFE6, Font.STRONG);
      // weather + hunting outlook card
      float cy = 88.0F, ch = 50.0F;
      f.round(9.0F, cy, w - 18.0F, ch, 12.0F, 0x66101411);
      f.round(9.0F, cy, w - 18.0F, 0.6F, 0.3F, 0x16FFFFFF);
      Wx.icon(f, m.weather.icon, 30.0F, cy + 17.0F, 24.0F);
      f.text(Ui.temp(m, m.weather.tempC), 46.0F, cy + 6.0F, Theme.TEXT, Font.LARGE);
      f.text(this.txt.fit(f, m.weather.condition, 70.0F, Font.SMALL), 47.0F, cy + 26.0F, Theme.TEXT2, Font.SMALL);
      // wind arrow (pointing where the wind blows to)
      float wx = w - 34.0F, wy = cy + 15.0F;
      f.circle(wx, wy, 10.0F, 0x30FFFFFF);
      Wx.windArrow(f, wx, wy, 8.0F, m.weather.windFrom, Theme.SKY);
      f.center(Ui.point(m.weather.windFrom) + " " + Math.round(m.settings.celsius ? m.weather.windKmh : m.weather.windKmh * 0.621371F), wx, cy + 28.0F,
         Theme.TEXT2, Font.SMALL);
      f.fill(17.0F, cy + 37.0F, w - 34.0F, 0.5F, 0x22FFFFFF);
      int score = m.weather.huntScore;
      int sc = score >= 70 ? Theme.MOSS : (score >= 45 ? Theme.YELLOW : Theme.TEXT3);
      G.TRACK.draw(f, 21.0F, cy + 44.5F, 8.0F, sc);
      String out = (score >= 70 ? "Deer moving" : (score >= 45 ? "Fair movement" : "Slow day")) + (m.rutActive ? " · " + m.rutTitle : "");
      f.text(this.txt.fit(f, out, w - 80.0F, Font.SMALL), 28.0F, cy + 41.5F, Theme.TEXT2, Font.SMALL);
      Ui.bar(f, w - 50.0F, cy + 43.0F, 32.0F, 3.0F, score / 100.0F, 0x30FFFFFF, sc);
      f.zone(Ui.Z_APP, 9.0F, cy, w - 18.0F, ch, 999L);
   }

   /** The date line ("Thursday, October 14"), rebuilt when the day changes. */
   private String date() {
      PhoneModel m = this.model;
      long key = (m.gameTime / 24000L) * 4096L + Math.floorMod(m.month, 12) * 64L + m.day;
      if (key != this.dateKey) {
         this.dateKey = key;
         this.dateText = DAYS[(int)Math.floorMod(m.gameTime / 24000L + 5L, 7L)] + ", " + MONTHS[Math.floorMod(m.month, 12)] + " " + m.day;
      }
      return this.dateText;
   }

   private long wxKey = Long.MIN_VALUE;
   private String wxText = "";

   /** "4° · Partly cloudy · NW 11 km/h" under the lock-screen date, rebuilt only when one of its parts changes. */
   private String lockWeather() {
      PhoneModel m = this.model;
      if (!m.weather.loaded) {
         return m.season;
      }
      long key = ((long)Math.round(m.weather.tempC * 2.0F) & 0xFFFFL) << 40 | ((long)Math.round(m.weather.windKmh) & 0xFFFL) << 28
         | ((long)Math.round(m.weather.windFrom) & 0x1FFL) << 19 | (m.settings.celsius ? 1L : 0L) << 18 | (m.weather.condition.hashCode() & 0x3FFFFL);
      if (key != this.wxKey) {
         this.wxKey = key;
         this.wxText = Ui.temp(m, m.weather.tempC) + " · " + m.weather.condition + " · " + Ui.point(m.weather.windFrom) + " " + Ui.wind(m, m.weather.windKmh);
      }
      return this.wxText;
   }

   private static final int Z_LOCK = 9100;
   private static final int Z_LOCK_NOTICE = 9101;
   private static final int Z_LOCK_TORCH = 9102;
   private static final int Z_LOCK_CAMS = 9103;

   /** The lock screen: the wallpaper, the big clock and date, the latest notifications, torch and Trail Cams shortcuts. */
   private void lockScreen(Frame f, float w, float h, long now) {
      PhoneModel m = this.model;
      float e = this.lockT;
      f.push();
      f.translate(0.0F, -(1.0F - e) * h * 0.22F);
      f.alpha(Math.min(1.0F, e * 1.4F));
      this.wallpaper.draw(this, f, w, h, now);
      f.gradient(0.0F, 0.0F, w, h * 0.45F, 0x58000000, 0x00000000);
      f.gradient(0.0F, h * 0.6F, w, h * 0.4F, 0x00000000, 0x70000000);
      f.zone(Z_LOCK, 0.0F, 0.0F, w, h);
      G.LOCK.draw(f, w / 2.0F, Theme.STATUS_H + 8.0F, 10.0F, 0xE0F7F5EE);
      int minute = Ui.minute(m.dayTime);
      String t = Ui.clock(m, minute);
      float tw = f.width(t, Font.HUGE);
      float tx = w / 2.0F - tw / 2.0F;
      f.text(t, tx + 0.7F, 40.7F, 0x50000000, Font.HUGE);
      f.text(t, tx, 40.0F, 0xFFF7F5EE, Font.HUGE);
      String ap = Ui.ampm(m, minute);
      if (!ap.isEmpty()) {
         f.text(ap, tx + tw + 2.0F, 65.0F, 0xD8F7F5EE, Font.STRONG);
      }
      String date = this.date();
      f.center(date, w / 2.0F + 0.4F, 87.4F, 0x70000000, Font.STRONG);
      f.center(date, w / 2.0F, 87.0F, 0xFFF1EFE6, Font.STRONG);
      // a slim weather line under the date
      f.center(this.lockWeather(), w / 2.0F, 99.0F, 0xC8F1EFE6, Font.SMALL);
      // the latest notifications, newest first
      float y = 118.0F;
      int shown = 0;
      for (int i = m.notices.size() - 1; i >= 0 && shown < 3; i--, shown++) {
         PhoneModel.Notice n = m.notices.get(i);
         boolean hot = this.hot(Z_LOCK_NOTICE, i);
         f.round(9.0F, y, w - 18.0F, 36.0F, 12.0F, hot ? 0xA0262C27 : 0x8A161B18);
         f.round(9.0F, y, w - 18.0F, 0.6F, 0.3F, 0x1AFFFFFF);
         App a = this.app(n.app());
         if (a != null) {
            int col = a.icon % 8, row = a.icon / 8;
            f.sprite("apps", 15.0F, y + 8.0F, 20.0F, 20.0F, col / 8.0F, row / 3.0F, (col + 1) / 8.0F, (row + 1) / 3.0F, 0xFFFFFFFF);
         }
         f.text(this.txt.fit(f, n.title(), w - 64.0F, Font.STRONG), 41.0F, y + 6.0F, Theme.TEXT, Font.STRONG);
         f.text(this.txt.fit(f, n.body(), w - 56.0F, Font.SMALL), 41.0F, y + 20.0F, Theme.TEXT2, Font.SMALL);
         f.zone(Z_LOCK_NOTICE, 9.0F, y, w - 18.0F, 36.0F, i);
         y += 40.0F;
      }
      if (m.notices.size() > 3) {
         f.center(this.txt.fit(f, m.notices.size() - 3 > 1 ? "and more in the shade" : "and one more in the shade", w - 30.0F, Font.SMALL), w / 2.0F, y,
            0xA0F1EFE6, Font.SMALL);
      }
      // shortcuts: torch (left) and Trail Cams (right)
      float by = h - 40.0F;
      boolean th = this.hot(Z_LOCK_TORCH, 0L);
      f.circle(30.0F, by, 15.0F, m.flashlight ? Theme.YELLOW : (th ? 0x70404840 : 0x5A202620));
      G.FLASHLIGHT.draw(f, 30.0F, by, 13.0F, m.flashlight ? 0xFF15130F : Theme.TEXT);
      f.zone(Z_LOCK_TORCH, 13.0F, by - 17.0F, 34.0F, 34.0F);
      boolean ch = this.hot(Z_LOCK_CAMS, 0L);
      f.circle(w - 30.0F, by, 15.0F, ch ? 0x70404840 : 0x5A202620);
      G.CAMERA.draw(f, w - 30.0F, by, 13.0F, Theme.TEXT);
      f.zone(Z_LOCK_CAMS, w - 47.0F, by - 17.0F, 34.0F, 34.0F);
      // the hint (a slow breathing glow, still with reduced motion)
      float pulse = m.reducedMotion ? 1.0F : 0.65F + 0.35F * (float)Math.sin(now / 420.0);
      f.center("Click or swipe up to unlock", w / 2.0F, h - 24.0F, Theme.withAlpha(0xF1EFE6, (int)(200 * pulse)), Font.SMALL);
      f.pop();
   }

   private static final String[] DAYS = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
   private static final String[] MONTHS = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November",
      "December"};

   // ------------------------------------------------------------------------------------------------ banner, toast

   private void banner(Frame f, float w, long now) {
      if (this.banners.isEmpty()) {
         return;
      }
      PhoneModel.Notice n = this.banners.get(0);
      float age = (now - this.bannerShownAt) / 1000.0F;
      boolean hot = this.hot(Ui.Z_BANNER, 0L);
      if (age > 4.2F && !hot) {
         this.banners.remove(0);
         this.bannerShownAt = now;
         return;
      }
      float in = Theme.easeBack(Math.min(1.0F, age / 0.35F));
      float out = age > 3.8F && !hot ? Theme.easeInOut((age - 3.8F) / 0.4F) : 0.0F;
      float y = Theme.STATUS_H + 2.0F - 60.0F * (1.0F - in) - 60.0F * out;
      float bh = 40.0F;
      f.round(5.0F, y + 1.0F, w - 10.0F, bh, 12.0F, 0x60000000);
      f.round(5.0F, y, w - 10.0F, bh, 12.0F, 0xF02A302B);
      App a = this.app(n.app());
      if (a != null) {
         int col = a.icon % 8, row = a.icon / 8;
         f.sprite("apps", 12.0F, y + 9.0F, 20.0F, 20.0F, col / 8.0F, row / 3.0F, (col + 1) / 8.0F, (row + 1) / 3.0F, 0xFFFFFFFF);
      }
      f.text(this.txt.fit(f, n.title(), w - 60.0F, Font.STRONG), 38.0F, y + 8.0F, Theme.TEXT, Font.STRONG);
      f.text(this.txt.fit(f, n.body(), w - 50.0F, Font.SMALL), 38.0F, y + 22.0F, Theme.TEXT2, Font.SMALL);
      f.right("now", w - 12.0F, y + 8.5F, Theme.TEXT3, Font.SMALL);
      f.zone(Ui.Z_BANNER, 5.0F, y, w - 10.0F, bh);
   }

   private void toastDraw(Frame f, float w, float h, long now) {
      if (this.toast.isEmpty()) {
         return;
      }
      float age = (now - this.toastAt) / 1000.0F;
      if (age > 3.2F) {
         this.toast = "";
         return;
      }
      float a = Math.min(1.0F, age / 0.15F) * (age > 2.8F ? 1.0F - (age - 2.8F) / 0.4F : 1.0F);
      String[] lines = this.txt.wrap(f, this.toast, w - 40.0F, Font.SMALL);
      float th = lines.length * 9.0F + 10.0F;
      float tw = 0.0F;
      for (String l : lines) {
         tw = Math.max(tw, f.width(l, Font.SMALL));
      }
      tw += 18.0F;
      float y = h - 30.0F - th + (1.0F - Theme.easeOut(Math.min(1.0F, age / 0.2F))) * 10.0F;
      f.push();
      f.alpha(a);
      f.round(w / 2.0F - tw / 2.0F, y, tw, th, Math.min(th / 2.0F, 9.0F), 0xF2343B35);
      for (int i = 0; i < lines.length; i++) {
         f.center(lines[i], w / 2.0F, y + 5.5F + i * 9.0F, Theme.TEXT, Font.SMALL);
      }
      f.pop();
   }

   // ------------------------------------------------------------------------------------------------ shade

   private static final int T_LIGHT = 0;
   private static final int T_NIGHT = 1;
   private static final int T_SILENT = 2;
   private static final int T_UNITS = 3;

   private void shade(Frame f, float w, float h, float dt) {
      this.shadeT = Theme.approach(this.shadeT, this.shade ? 1.0F : 0.0F, 14.0F, dt);
      if (this.shadeT < 0.01F) {
         return;
      }
      float e = this.shadeT;
      f.fill(0.0F, 0.0F, w, h, Theme.withAlpha(0x050706, (int)(200 * e)));
      if (this.shade) {
         f.zone(Ui.Z_CLOSE_SHADE, 0.0F, 0.0F, w, h);
      }
      f.push();
      f.translate(0.0F, -h * (1.0F - e) * 0.35F);
      f.alpha(e);
      PhoneModel m = this.model;
      float y = Theme.STATUS_H + 6.0F;
      // quick toggles
      G[] glyphs = this.qsGlyphs;
      glyphs[2] = m.settings.silent ? G.BELL_OFF : G.BELL;
      String[] labels = this.qsLabels;
      labels[2] = m.settings.silent ? "Silent" : "Sound";
      labels[3] = m.settings.celsius ? "°C" : "°F";
      boolean[] on = this.qsOn;
      on[0] = m.flashlight;
      on[1] = m.settings.night;
      on[2] = m.settings.silent;
      on[3] = !m.settings.celsius;
      int[] colors = QS_COLORS;
      float tw = (w - 18.0F - 3 * 6.0F) / 4.0F;
      for (int i = 0; i < 4; i++) {
         float tx = 9.0F + i * (tw + 6.0F);
         boolean hot = this.hot(Ui.Z_TOGGLE, i);
         int bg = on[i] ? colors[i] : (hot ? Theme.SURFACE4 : Theme.SURFACE3);
         f.round(tx, y, tw, tw, 10.0F, bg);
         glyphs[i].draw(f, tx + tw / 2.0F, y + tw / 2.0F - 2.0F, 14.0F, on[i] ? 0xFF15130F : Theme.TEXT);
         f.center(labels[i], tx + tw / 2.0F, y + tw - 9.0F, on[i] ? 0xFF15130F : Theme.TEXT2, Font.SMALL);
         f.zone(Ui.Z_TOGGLE, tx, y, tw, tw, i);
      }
      y += tw + 8.0F;
      // battery and signal
      f.round(9.0F, y, w - 18.0F, 34.0F, 10.0F, Theme.SURFACE2);
      G.BOLT.draw(f, 21.0F, y + 17.0F, 11.0F, m.charging ? Theme.MOSS : Theme.TEXT3);
      f.text("Battery " + m.battery + "%", 30.0F, y + 6.0F, Theme.TEXT, Font.STRONG);
      String note = m.charging ? m.chargeNote : (m.battery <= 15 ? "Low: charge by a fire, at camp or sleep" : "Lasts about " + this.hoursLeft() + " in-game hours");
      f.text(this.txt.fit(f, note, w - 50.0F, Font.SMALL), 30.0F, y + 19.0F, Theme.TEXT3, Font.SMALL);
      String sig = m.signal == 0 ? "No service" : (m.signal >= 3 ? "Good signal" : "Weak signal");
      f.right(sig, w - 16.0F, y + 7.0F, m.signal == 0 ? Theme.RED : Theme.TEXT2, Font.SMALL);
      y += 42.0F;
      // notifications
      f.text("Notifications", 11.0F, y, Theme.TEXT, Font.STRONG);
      if (!m.notices.isEmpty()) {
         boolean hot = this.hot(Ui.Z_CLEAR_NOTICES, 0L);
         f.right("Clear", w - 12.0F, y + 1.0F, hot ? Theme.TEXT : Theme.BLAZE, Font.SMALL);
         f.zone(Ui.Z_CLEAR_NOTICES, w - 40.0F, y - 3.0F, 36.0F, 14.0F);
      }
      y += 14.0F;
      if (m.notices.isEmpty()) {
         f.center("Nothing new", w / 2.0F, y + 18.0F, Theme.TEXT3, Font.SMALL);
      }
      int shown = 0;
      for (int i = m.notices.size() - 1; i >= 0 && y < h - 50.0F && shown < 7; i--, shown++) {
         PhoneModel.Notice n = m.notices.get(i);
         boolean hot = this.hot(Ui.Z_NOTICE, i);
         f.round(9.0F, y, w - 18.0F, 34.0F, 10.0F, hot ? Theme.SURFACE3 : Theme.SURFACE2);
         App a = this.app(n.app());
         if (a != null) {
            int col = a.icon % 8, row = a.icon / 8;
            f.sprite("apps", 14.0F, y + 7.0F, 20.0F, 20.0F, col / 8.0F, row / 3.0F, (col + 1) / 8.0F, (row + 1) / 3.0F, 0xFFFFFFFF);
         }
         f.text(this.txt.fit(f, n.title(), w - 62.0F, Font.STRONG), 39.0F, y + 5.0F, Theme.TEXT, Font.STRONG);
         f.text(this.txt.fit(f, n.body(), w - 54.0F, Font.SMALL), 39.0F, y + 19.0F, Theme.TEXT3, Font.SMALL);
         f.zone(Ui.Z_NOTICE, 9.0F, y, w - 18.0F, 34.0F, i);
         y += 38.0F;
      }
      f.pop();
   }

   private int hoursLeft() {
      // standby lasts ~100 in-game hours on a full charge, less with the screen on
      return Math.max(1, this.model.battery);
   }

   private void alarmOverlay(Frame f, float w, float h, long now) {
      f.fill(0.0F, 0.0F, w, h, 0xF0080B09);
      float pulse = 0.5F + 0.5F * (float)Math.sin(now / 160.0);
      f.circle(w / 2.0F, 110.0F, 34.0F + pulse * 6.0F, Theme.withAlpha(Theme.BLAZE, (int)(60 + 60 * pulse)));
      f.circle(w / 2.0F, 110.0F, 30.0F, Theme.BLAZE);
      G.ALARM.draw(f, w / 2.0F, 110.0F, 30.0F, 0xFF1C1209);
      f.center("Alarm", w / 2.0F, 160.0F, Theme.TEXT2, Font.STRONG);
      f.center(Ui.clockFull(this.model, Math.max(0, this.model.alarmMinute)), w / 2.0F, 176.0F, Theme.TEXT, Font.DISPLAY);
      Ui.button(this, f, 9001, 0L, 20.0F, h - 90.0F, w - 40.0F, 26.0F, "Stop", Ui.FILLED, true);
      Ui.button(this, f, 9002, 0L, 20.0F, h - 58.0F, w - 40.0F, 24.0F, "Snooze 10 min", Ui.TONAL, true);
   }

   // ------------------------------------------------------------------------------------------------ input

   public void mouseMoved(float x, float y) {
      this.mouseX = x;
      this.mouseY = y;
      if (this.current != null && this.appT >= 1.0F && !this.shade) {
         this.current.mouse(this.appMouseX(), this.appMouseY());
      }
   }

   public boolean mouseDown(float x, float y, int button) {
      this.mouseMoved(x, y);
      if (this.closing || this.openT < 0.5F) {
         return true;
      }
      int i = this.frame.hit(x, y);
      this.downId = this.frame.hitId(i);
      this.downData = this.frame.hitData(i);
      this.downX = x;
      this.downY = y;
      this.dragging = false;
      this.dragLastY = y;
      this.dragLastX = x;
      this.dragLastMillis = this.actions.millis();
      this.pressedApp = false;
      if (this.locked) {
         return true;
      }
      if (this.current != null && this.appT >= 1.0F && !this.shade && !this.model.alarmRinging && this.downId != Ui.Z_STATUS
         && this.downId != Ui.Z_HOME && this.downId != Ui.Z_BANNER && this.inScreen(x, y)) {
         this.pressedApp = this.current.press(this.appMouseX(), this.appMouseY(), button);
         if (this.pressedApp) {
            this.downId = 0;
         }
      }
      return true;
   }

   public boolean mouseDragged(float x, float y, int button) {
      this.mouseMoved(x, y);
      if (this.pressedApp) {
         return true;
      }
      float dy = y - this.dragLastY;
      long now = this.actions.millis();
      if (this.locked) {
         // swipe up anywhere unlocks
         if (this.downY - y > 12.0F) {
            this.unlock();
            this.downId = 0;
            this.dragging = true;
         }
         return true;
      }
      if (!this.dragging && Math.abs(y - this.downY) > 3.0F) {
         this.dragging = true;
         // pulling down from the status bar opens the shade
         if (this.downId == Ui.Z_STATUS && y > this.downY) {
            this.shade = true;
            this.dragging = false;
            this.downId = 0;
            return true;
         }
         // flicking up on the home bar goes home
         if (this.downId == Ui.Z_HOME && y < this.downY) {
            this.goHome();
            this.downId = 0;
            return true;
         }
         if (this.current != null && this.appT >= 1.0F && !this.shade) {
            this.current.scroller().dragStart();
         }
      }
      if (this.dragging && this.current != null && this.appT >= 1.0F && !this.shade) {
         float ldx = (x - this.dragLastX) / this.scale;
         if (!this.current.drag(this.appMouseX(), this.appMouseY(), ldx, dy / this.scale)) {
            this.current.scroller().drag(dy / this.scale, Math.max(1L, now - this.dragLastMillis) / 1000.0F);
         }
      }
      this.dragLastY = y;
      this.dragLastX = x;
      this.dragLastMillis = now;
      return true;
   }

   public boolean mouseUp(float x, float y, int button) {
      this.mouseMoved(x, y);
      if (this.pressedApp) {
         this.pressedApp = false;
         if (this.current != null) {
            this.current.release(this.appMouseX(), this.appMouseY(), button);
         }
         return true;
      }
      if (this.dragging) {
         this.dragging = false;
         if (this.current != null) {
            this.current.scroller().dragEnd();
         }
         this.downId = 0;
         return true;
      }
      int i = this.frame.hit(x, y);
      int id = this.frame.hitId(i);
      long data = this.frame.hitData(i);
      if (i >= 0 && id == this.downId && data == this.downData) {
         this.tap(id, data);
      }
      this.downId = 0;
      return true;
   }

   private boolean inScreen(float x, float y) {
      float lx = this.appMouseX(), ly = this.appMouseY();
      return lx >= 0 && ly >= 0 && lx < this.sw && ly < this.sh;
   }

   public boolean mouseScrolled(float x, float y, float amount) {
      this.mouseMoved(x, y);
      if (!this.locked && this.current != null && this.appT >= 1.0F && !this.shade && !this.current.wheel(amount)) {
         this.current.scroller().wheel(amount);
      }
      return true;
   }

   private void tap(int id, long data) {
      PhoneModel m = this.model;
      if (m.alarmRinging) {
         if (id == 9001 || id == 9002) {
            this.actions.alarmAnswer(id == 9002);
            m.alarmRinging = false;
            this.actions.sound(PhoneActions.Sfx.TAP);
         }
         return;
      }
      if (this.locked) {
         switch (id) {
            case Z_LOCK_TORCH -> {
               this.actions.setFlashlight(!m.flashlight);
               this.actions.sound(PhoneActions.Sfx.TOGGLE);
            }
            case Z_LOCK_CAMS -> {
               this.unlock();
               App cams = this.app("cams");
               if (cams != null) {
                  this.startApp(cams, Theme.SCREEN_W - 45.0F, Theme.SCREEN_H - 55.0F, 30.0F);
               }
            }
            case Z_LOCK_NOTICE -> {
               this.unlock();
               if (data >= 0 && data < m.notices.size()) {
                  this.launchFromNotice(m.notices.get((int)data));
               }
            }
            default -> this.unlock();
         }
         return;
      }
      switch (id) {
         case Ui.Z_BACK -> this.back();
         case Ui.Z_HOME -> this.goHome();
         case Ui.Z_STATUS -> {
            if (!m.station) {
               this.shade = !this.shade;
               this.actions.sound(PhoneActions.Sfx.TAP);
            }
         }
         case Ui.Z_CLOSE_SHADE -> this.shade = false;
         case Ui.Z_BANNER -> {
            if (!this.banners.isEmpty()) {
               PhoneModel.Notice n = this.banners.remove(0);
               this.bannerShownAt = this.actions.millis();
               this.launchFromNotice(n);
            }
         }
         case Ui.Z_NOTICE -> {
            if (data >= 0 && data < m.notices.size()) {
               PhoneModel.Notice n = m.notices.get((int)data);
               this.shade = false;
               this.launchFromNotice(n);
            }
         }
         case Ui.Z_CLEAR_NOTICES -> {
            m.notices.clear();
            this.noticesSeen = 0;
            this.actions.sound(PhoneActions.Sfx.TAP);
         }
         case Ui.Z_TOGGLE -> this.toggle((int)data);
         case Ui.Z_APP -> {
            if (data == 999L) {
               App wx = this.app("weather");
               if (wx != null) {
                  this.startApp(wx, 9.0F, 88.0F, 50.0F);
               }
               return;
            }
            int slot = (int)data;
            App a = slot >= 100 ? (slot - 100 < this.dock.size() ? this.dock.get(slot - 100) : null) : (slot < this.grid.size() ? this.grid.get(slot) : null);
            if (a != null) {
               float[] r = this.iconRect(slot);
               this.startApp(a, r[0], r[1], ICON);
            }
         }
         default -> {
            if (this.current != null && this.appT >= 1.0F) {
               this.current.tap(id, data);
            }
         }
      }
   }

   private float[] iconRect(int slot) {
      float w = Theme.SCREEN_W, h = Theme.SCREEN_H;
      if (slot >= 100) {
         float dcell = (w - 12.0F) / Math.max(4, this.dock.size());
         return new float[]{6.0F + dcell * (slot - 100) + dcell / 2.0F - ICON / 2.0F, h - 49.0F};
      }
      float cell = (w - 12.0F) / COLS;
      return new float[]{6.0F + cell * (slot % COLS) + cell / 2.0F - ICON / 2.0F, 148.0F + (slot / COLS) * 46.0F};
   }

   private void launchFromNotice(PhoneModel.Notice n) {
      App a = this.app(n.app());
      if (a != null) {
         if (this.current == a && !this.appClosing) {
            return;
         }
         this.startApp(a, Theme.SCREEN_W / 2.0F - 10.0F, Theme.STATUS_H + 8.0F, 20.0F);
      }
   }

   private void toggle(int which) {
      PhoneModel m = this.model;
      switch (which) {
         case T_LIGHT -> this.actions.setFlashlight(!m.flashlight);
         case T_NIGHT -> m.settings.night = !m.settings.night;
         case T_SILENT -> m.settings.silent = !m.settings.silent;
         case T_UNITS -> m.settings.celsius = !m.settings.celsius;
         default -> {
         }
      }
      this.actions.settingsChanged();
      this.actions.sound(PhoneActions.Sfx.TOGGLE);
   }

   /** Esc / back gesture. */
   public void back() {
      if (this.model.alarmRinging) {
         return;
      }
      if (this.locked) {
         this.close();
         return;
      }
      if (this.shade) {
         this.shade = false;
         return;
      }
      if (this.current != null && !this.appClosing) {
         if (!this.current.back()) {
            this.goHome();
         }
         return;
      }
      this.close();
   }

   public boolean keyPressed(int key, int mods) {
      if (this.closing) {
         return true;
      }
      if (key == Ui.K_ESC) {
         this.back();
         return true;
      }
      if (this.locked) {
         if (key == Ui.K_ENTER || key == Ui.K_SPACE || key == Ui.K_UP) {
            this.unlock();
         }
         return true;
      }
      if (this.current != null && this.appT >= 1.0F && !this.shade && this.current.key(key, mods)) {
         return true;
      }
      if (key == Ui.K_BACKSPACE && (this.current == null || !this.current.grabsKeys())) {
         this.back();
         return true;
      }
      return false;
   }

   public boolean charTyped(char c) {
      return !this.locked && this.current != null && this.appT >= 1.0F && !this.shade && this.current.typed(c);
   }

   /** Is a key used by the current app (so the screen must not treat it as "close the phone")? */
   public boolean grabsKeys() {
      return this.current != null && this.current.grabsKeys();
   }
}
