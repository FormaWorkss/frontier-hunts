package com.formaworks.frontierhunts.phone.client.ui.apps;

import com.formaworks.frontierhunts.phone.client.ui.App;
import com.formaworks.frontierhunts.phone.client.ui.Canvas.Font;
import com.formaworks.frontierhunts.phone.client.ui.Frame;
import com.formaworks.frontierhunts.phone.client.ui.G;
import com.formaworks.frontierhunts.phone.client.ui.PhoneActions;
import com.formaworks.frontierhunts.phone.client.ui.PhoneModel;
import com.formaworks.frontierhunts.phone.client.ui.PhoneUi;
import com.formaworks.frontierhunts.phone.client.ui.Theme;
import com.formaworks.frontierhunts.phone.client.ui.Ui;
import java.util.List;

/**
 * [phone] Trail Cams: everything the Camera Base Station did, in the phone. Every camera you own with its latest real
 * photo, battery, roll and status; its full roll of developed photos; a sideways full-screen viewer with browsing,
 * save to screenshots and delete; live lens view; clear the roll. Photos develop in the darkroom behind the phone
 * (the window goes dark while they do), and far cameras send theirs over the uplink.
 */
public final class CamsApp extends App implements PhoneUi.CamsHost {
   private static final String[] SUMMARY = {"PHOTOS", "ONLINE", "LOW BATT."};
   private final int[] summary = new int[3];
   static final int LIST = 0;
   static final int GALLERY = 1;
   static final int VIEWER = 2;
   private static final int Z_CAM = 100;
   private static final int Z_PHOTO = 101;
   private static final int Z_LIVE = 102;
   private static final int Z_CLEAR = 103;
   private static final int Z_PREV = 104;
   private static final int Z_NEXT = 105;
   private static final int Z_SAVE = 106;
   private static final int Z_DELETE = 107;
   private static final int Z_REFRESH = 108;
   private static final int Z_VBACK = 109;
   private static final int Z_HIDE = 110;
   private int viewing;
   private long viewingId;
   private long confirmDelete;
   private long confirmUntil;
   private long confirmClearUntil;
   private int refreshIn;
   private boolean chrome = true;

   public CamsApp() {
      super("cams", "Trail Cams", 2);
   }

   @Override
   protected boolean landscapePage(int page) {
      return page == VIEWER;
   }

   @Override
   public boolean darkroomWanted() {
      if (this.page() == LIST) {
         for (PhoneModel.Cam c : this.m.cams.list) {
            if (c.latest() != null && pending(c.latest())) {
               return true;
            }
         }
         return false;
      }
      PhoneModel.Gallery g = this.m.cams.gallery;
      if (g == null) {
         return false;
      }
      for (PhoneModel.PhotoRef p : g.photos) {
         if (pending(p)) {
            return true;
         }
      }
      return this.m.cams.uplink != null;
   }

   private static boolean pending(PhoneModel.PhotoRef p) {
      int s = p.state();
      return s == 0 || s == 1 || s == 2 || s == 5;
   }

   @Override
   public void opened() {
      this.act.camHub();
      this.refreshIn = 100;
      if (this.page() != LIST && this.m.cams.gallery != null) {
         this.act.camRoll(this.m.cams.gallery.pos);
      }
   }

   @Override
   public void tick() {
      if (--this.refreshIn <= 0) {
         this.refreshIn = 200;
         if (this.page() == LIST) {
            this.act.camHub();
         }
      }
      long now = this.act.millis();
      if (this.confirmUntil != 0L && now > this.confirmUntil) {
         this.confirmUntil = 0L;
         this.confirmDelete = 0L;
      }
      if (this.confirmClearUntil != 0L && now > this.confirmClearUntil) {
         this.confirmClearUntil = 0L;
      }
      if (this.page() == VIEWER) {
         List<PhoneModel.PhotoRef> photos = this.photos();
         // keep the viewer on the same photo when the roll changes under it
         int idx = -1;
         for (int i = 0; i < photos.size(); i++) {
            if (photos.get(i).id() == this.viewingId) {
               idx = i;
               break;
            }
         }
         if (idx < 0) {
            if (photos.isEmpty()) {
               this.back();
            } else {
               this.viewing = Math.min(this.viewing, photos.size() - 1);
               this.viewingId = photos.get(this.viewing).id();
            }
         } else {
            this.viewing = idx;
            this.act.camPrioritise(photos.get(idx));
         }
      }
   }

   private List<PhoneModel.PhotoRef> photos() {
      PhoneModel.Gallery g = this.m.cams.gallery;
      return g == null ? List.of() : g.photos;
   }

   @Override
   protected void drawPage(Frame f, int page, float w, float h) {
      switch (page) {
         case GALLERY -> this.gallery(f, w, h);
         case VIEWER -> this.viewer(f, w, h);
         default -> this.list(f, w, h);
      }
   }

   // ------------------------------------------------------------------------------------------------ the camera list

   private void list(Frame f, float w, float h) {
      PhoneModel.Cams cams = this.m.cams;
      f.fill(0.0F, 0.0F, w, h, Theme.BG);
      String sub = cams.seasonLine.isEmpty() ? "Your camera network" : cams.seasonLine;
      float y = Ui.header(this.ui, f, "Trail Cams", sub, false, w);
      Ui.iconButton(this.ui, f, Z_REFRESH, 0L, w - 20.0F, Theme.STATUS_H + 19.0F, 9.0F, G.REFRESH, Theme.SURFACE2, Theme.TEXT2, true);
      if (!this.m.station && this.m.signal <= 0 && cams.list.isEmpty()) {
         Ui.empty(this.ui, f, G.SIGNAL, "No signal", "Cameras report over the cell network. Climb out of the valley or step outside.", w / 2.0F, y + 40.0F,
            w - 40.0F);
         return;
      }
      if (!cams.loaded) {
         Ui.empty(this.ui, f, G.CAMERA, "Calling your cameras…", "Every camera you own reports here.", w / 2.0F, y + 40.0F, w - 40.0F);
         return;
      }
      if (cams.list.isEmpty()) {
         Ui.empty(this.ui, f, G.CAMERA, "No cameras out yet", "Strap a Trail Camera to a tree, about waist high and angled along a trail. It reports here.",
            w / 2.0F, y + 40.0F, w - 40.0F);
         return;
      }
      // totals
      int photos = 0, live = 0, low = 0;
      for (PhoneModel.Cam c : cams.list) {
         photos += c.frames();
         live += c.live() ? 1 : 0;
         low += c.percent() < 15 ? 1 : 0;
      }
      float tw = (w - 18.0F - 8.0F) / 3.0F;
      String[] k = SUMMARY;
      int[] v = this.summary;
      v[0] = photos;
      v[1] = live;
      v[2] = low;
      for (int i = 0; i < 3; i++) {
         float x = 9.0F + i * (tw + 4.0F);
         f.round(x, y, tw, 30.0F, 8.0F, Theme.SURFACE2);
         f.text(k[i], x + 7.0F, y + 5.0F, Theme.TEXT3, Font.SMALL);
         f.text(Ui.num(v[i]), x + 7.0F, y + 14.0F, i == 2 && low > 0 ? Theme.RED : Theme.TEXT, Font.MEDIUM);
      }
      y += 38.0F;
      float y0 = this.beginScroll(f, y, w, h);
      float ry = y0;
      for (int i = 0; i < cams.list.size(); i++) {
         PhoneModel.Cam c = cams.list.get(i);
         float ch = 54.0F;
         if (f.visible(0.0F, ry, w, ch)) {
            this.camRow(f, c, i, ry, w, ch);
         }
         ry += ch + 6.0F;
      }
      this.endScroll(f, y0, ry, w, h);
   }

   private void camRow(Frame f, PhoneModel.Cam c, int i, float y, float w, float ch) {
      Ui.tapCard(this.ui, f, Z_CAM, i, 9.0F, y, w - 18.0F, ch, Theme.SURFACE2);
      float tw = 58.0F, th = tw * 9.0F / 16.0F;
      f.clip(15.0F, y + 6.0F, tw, th);
      this.picture(f, c.latest(), 15.0F, y + 6.0F, tw, th, true);
      f.unclip();
      if (c.latest() != null && c.latest().infrared() && c.latest().state() == 4) {
         Ui.chip(f, "IR", 17.0F, y + 8.0F, 0xB0000000, Theme.IR);
      }
      float tx = 15.0F + tw + 7.0F;
      float right = w - 15.0F;
      f.text(this.txt.fit(f, c.name(), right - tx, Font.STRONG), tx, y + 6.0F, Theme.TEXT, Font.STRONG);
      String where = Ui.distance(c.distance()) + " " + c.bearing();
      f.text(this.txt.fit(f, where, right - tx, Font.SMALL), tx, y + 18.0F, Theme.TEXT3, Font.SMALL);
      String latest = c.latest() == null ? (c.frames() == 0 ? "No photos yet" : "") : c.latest().title();
      f.text(this.txt.fit(f, latest, right - tx, Font.SMALL), tx, y + 27.0F, Theme.TEXT2, Font.SMALL);
      // bottom line: status, photos, best; battery on the right
      float by = y + ch - 13.0F;
      int pct = c.percent();
      int bc = pct < 15 ? Theme.RED : Theme.MOSS;
      f.right(pct + "%", right - 20.0F, by + 2.0F, pct < 15 ? Theme.RED : Theme.TEXT3, Font.SMALL);
      f.round(right - 17.0F, by + 2.0F, 16.0F, 7.0F, 2.0F, Theme.SURFACE4);
      f.round(right - 16.0F, by + 3.0F, Math.max(1.0F, 14.0F * pct / 100.0F), 5.0F, 1.4F, bc);
      float limit = right - 24.0F - f.width(pct + "%", Font.SMALL);
      String st = pct <= 0 ? "DEAD" : (c.live() ? "LIVE" : "LOGGING");
      int sc = pct <= 0 ? Theme.RED : (c.live() ? Theme.MOSS : Theme.TEXT3);
      float x = 15.0F + Ui.chip(f, st, 15.0F, by, Theme.withAlpha(Theme.shade(sc, 0.3F), 255), sc) + 4.0F;
      String n = Integer.toString(c.frames());
      if (x + f.width(n, Font.SMALL) + 18.0F < limit) {
         float cw = f.width(n, Font.SMALL) + 18.0F;
         f.round(x, by, cw, 11.0F, 5.5F, Theme.SURFACE3);
         G.CAMERA.draw(f, x + 6.5F, by + 5.5F, 7.0F, Theme.TEXT2);
         f.text(n, x + 12.0F, by + 2.0F, Theme.TEXT2, Font.SMALL);
         x += cw + 4.0F;
      }
      if (c.bestBuck() && c.bestScore() > 0) {
         String b = "Best " + c.bestScore();
         if (x + f.width(b, Font.SMALL) + 8.0F < limit) {
            Ui.chip(f, b, x, by, Theme.withAlpha(Theme.shade(Theme.GOLD, 0.35F), 255), Theme.GOLD);
         }
      }
   }

   // ------------------------------------------------------------------------------------------------ the roll

   private void gallery(Frame f, float w, float h) {
      PhoneModel.Gallery g = this.m.cams.gallery;
      f.fill(0.0F, 0.0F, w, h, Theme.BG);
      String label = g == null ? "Camera" : g.label;
      float y = Ui.bar(this.ui, f, label, w, true);
      if (g == null) {
         return;
      }
      // battery and status
      Ui.card(f, 9.0F, y, w - 18.0F, 36.0F);
      int pct = g.percent;
      f.text("Battery " + pct + "%", 16.0F, y + 6.0F, pct < 15 ? Theme.RED : Theme.TEXT, Font.STRONG);
      Ui.bar(f, 16.0F, y + 18.0F, 52.0F, 3.0F, pct / 100.0F, Theme.SURFACE4, pct < 15 ? Theme.RED : Theme.MOSS);
      String status = this.m.cams.uplink != null ? this.m.cams.uplink : (g.live ? (g.keepsLoaded ? "LIVE · AREA LOADED" : "LIVE ON THE NETWORK")
         : "OFF-GRID · LOGGING");
      f.right(this.txt.fit(f, status, w - 90.0F, Font.SMALL), w - 16.0F, y + 7.0F, g.live ? Theme.MOSS : Theme.TEXT3, Font.SMALL);
      int developing = 0;
      for (PhoneModel.PhotoRef p : g.photos) {
         if (pending(p) || p.state() == 3) {
            developing++;
         }
      }
      String count = !g.hasRoll ? "Reading the memory card…" : g.photos.size() + (g.photos.size() == 1 ? " photo" : " photos")
         + (developing > 0 ? " · developing " + developing : "");
      f.right(count, w - 16.0F, y + 20.0F, developing > 0 ? Theme.GOLD : Theme.TEXT3, Font.SMALL);
      y += 42.0F;
      // actions
      float bw = (w - 24.0F) / 2.0F;
      boolean confirming = this.act.millis() < this.confirmClearUntil;
      Ui.button(this.ui, f, Z_LIVE, 0L, 9.0F, y, bw, 20.0F, "Live view", Ui.TONAL, pct > 0 && !this.m.station || pct > 0 && this.m.station);
      Ui.button(this.ui, f, Z_CLEAR, 0L, 15.0F + bw, y, bw, 20.0F, confirming ? "Tap to confirm" : "Clear roll", confirming ? Ui.DANGER : Ui.TONAL,
         !g.photos.isEmpty());
      y += 28.0F;
      if (g.photos.isEmpty()) {
         Ui.empty(this.ui, f, G.CAMERA, g.hasRoll ? "Nothing has walked past yet" : "Reading the memory card…",
            "The camera photographs animals and people that move through its frame.", w / 2.0F, y + 20.0F, w - 40.0F);
         return;
      }
      float y0 = this.beginScroll(f, y, w, h);
      float cw = (w - 18.0F - 6.0F) / 2.0F, th = cw * 9.0F / 16.0F, ch = th + 24.0F;
      for (int i = 0; i < g.photos.size(); i++) {
         PhoneModel.PhotoRef p = g.photos.get(i);
         float x = 9.0F + (i % 2) * (cw + 6.0F);
         float cy = y0 + (i / 2) * (ch + 6.0F);
         if (!f.visible(x, cy, cw, ch)) {
            continue;
         }
         boolean hot = this.ui.hot(Z_PHOTO, i);
         f.round(x, cy, cw, ch, 7.0F, hot ? Theme.SURFACE3 : Theme.SURFACE2);
         f.clip(x, cy, cw, th + 2.0F);
         this.picture(f, p, x, cy, cw, th, true);
         f.unclip();
         String when = p.when();
         f.text(this.txt.fit(f, when, cw - 10.0F, Font.SMALL), x + 5.0F, cy + th + 3.0F, p.infrared() ? Theme.IR : Theme.GOLD, Font.SMALL);
         if (p.infrared() && p.state() == 4) {
            Ui.chip(f, "IR", x + 3.0F, cy + 3.0F, 0xB0000000, Theme.IR);
         }
         f.text(this.txt.fit(f, p.title(), cw - 10.0F, Font.SMALL), x + 5.0F, cy + th + 12.0F, Theme.TEXT, Font.SMALL);
         f.zone(Z_PHOTO, x, cy, cw, ch, i);
      }
      float end = y0 + ((g.photos.size() + 1) / 2) * (ch + 6.0F);
      this.endScroll(f, y0, end, w, h);
   }

   /** A photo, or its darkroom placeholder while it develops. */
   private void picture(Frame f, PhoneModel.PhotoRef p, float x, float y, float w, float h, boolean thumb) {
      if (p == null) {
         f.round(x, y, w, h, 4.0F, 0xFF101412);
         G.CAMERA.draw(f, x + w / 2.0F, y + h / 2.0F, Math.min(w, h) * 0.4F, 0x30FFFFFF);
         return;
      }
      int st = p.state();
      if (st == 4) {
         Object tex = thumb ? p.thumb() : p.full();
         if (tex == null) {
            tex = p.thumb();
         }
         if (tex != null) {
            f.image(tex, x, y, w, h, 0.0F, 0.0F, 1.0F, 1.0F, 0xFFFFFFFF);
            return;
         }
      }
      if (st == 6) {
         if (p.fallback() != null) {
            f.custom(p.fallback(), x, y, w, h);
         } else {
            f.fill(x, y, w, h, 0xFF141816);
            f.center("No image", x + w / 2.0F, y + h / 2.0F - 3.0F, Theme.TEXT3, Font.SMALL);
         }
         return;
      }
      // darkroom safelight: a deep red tray with a slow sweep while the frame develops
      f.fill(x, y, w, h, 0xFF140606);
      float time = (this.act.millis() % 100000L) / 1000.0F;
      boolean developing = st == 2;
      float sweep = (time * 0.35F % 1.0F) * (w + h);
      for (int i = 0; i < 6; i++) {
         float sx = x + sweep - h + i * 3.0F;
         float x0 = Math.max(x, sx), x1 = Math.min(x + w, sx + 2.0F);
         if (x1 > x0) {
            f.fill(x0, y, x1 - x0, h, developing ? 0x302A0A08 : 0x14200808);
         }
      }
      f.fill(x, y + h - 1.5F, w, 1.5F, 0xFF3A0C0A);
      String label = switch (st) {
         case 2 -> "DEVELOPING";
         case 1 -> "IN THE TRAY";
         case 5 -> this.m.cams.uplink != null ? "UPLINK" : "WAITING FOR SIGNAL";
         case 3 -> "LOADING";
         default -> "WAITING";
      };
      float pulse = developing ? 0.55F + 0.45F * (float)Math.sin(time * 4.0F) : 1.0F;
      int col = developing ? 0xE0564A : 0x9A3B33;
      Font font = w > 80.0F ? Font.STRONG : Font.SMALL;
      f.center(label, x + w / 2.0F, y + h / 2.0F - 4.0F, Theme.withAlpha(col, (int)(255 * pulse)), font);
      if (st == 5 && !thumb) {
         this.txt.paraCenter(f, "The ground around this camera is not loaded; it comes over the uplink.", x + w / 2.0F, y + h / 2.0F + 10.0F, w - 40.0F,
            0xFF9A3B33, Font.SMALL, 1.0F);
      }
   }

   // ------------------------------------------------------------------------------------------------ the viewer (sideways)

   private void viewer(Frame f, float w, float h) {
      f.fill(0.0F, 0.0F, w, h, 0xFF000000);
      List<PhoneModel.PhotoRef> photos = this.photos();
      if (photos.isEmpty() || this.viewing >= photos.size()) {
         return;
      }
      PhoneModel.PhotoRef p = photos.get(this.viewing);
      float side = 46.0F;
      float pw = Math.min(w - side, h * 16.0F / 9.0F);
      float ph = pw * 9.0F / 16.0F;
      float px = (w - side - pw) / 2.0F, py = (h - ph) / 2.0F;
      this.picture(f, p, px, py, pw, ph, false);
      f.zone(Z_HIDE, px + pw * 0.33F, py, pw * 0.34F, ph);
      // browse by tapping the left or right third
      f.zone(Z_PREV, px, py, pw * 0.33F, ph);
      f.zone(Z_NEXT, px + pw * 0.67F, py, pw * 0.33F, ph);
      float a = this.ui.anim(0xCA11L, this.chrome ? 1.0F : 0.0F, 12.0F);
      if (a > 0.01F) {
         f.push();
         f.alpha(a);
         // caption over the bottom of the photo
         f.gradient(px, py + ph - 40.0F, pw, 40.0F, 0x00000000, 0xC8000000);
         String title = p.title();
         f.text(this.txt.fit(f, title, pw * 0.6F, Font.MEDIUM), px + 9.0F, py + ph - 31.0F, Theme.TEXT, Font.MEDIUM);
         String detail = p.detail();
         if (!detail.isEmpty()) {
            f.text(this.txt.fit(f, detail, pw - 20.0F, Font.SMALL), px + 9.0F, py + ph - 17.0F, Theme.TEXT2, Font.SMALL);
         }
         String when = p.when() + (p.frame() > 0 ? String.format(java.util.Locale.ROOT, " · #%04d", p.frame()) : "") + (p.infrared() ? " · infrared" : "");
         f.right(this.txt.fit(f, when, pw * 0.42F, Font.SMALL), px + pw - 8.0F, py + ph - 29.0F, p.infrared() ? Theme.IR : Theme.GOLD, Font.SMALL);
         if (p.state() == 6 && !p.note().isEmpty()) {
            f.right(this.txt.fit(f, p.note(), pw * 0.5F, Font.SMALL), px + pw - 8.0F, py + 6.0F, Theme.RED, Font.SMALL);
         }
         // back, counter
         Ui.iconButton(this.ui, f, Z_VBACK, 0L, px + 13.0F, py + 13.0F, 8.5F, G.BACK, 0x90000000, Theme.TEXT, true);
         String n = (this.viewing + 1) + " / " + photos.size();
         float nw = f.width(n, Font.SMALL) + 10.0F;
         f.round(px + pw / 2.0F - nw / 2.0F, py + 6.0F, nw, 12.0F, 6.0F, 0x90000000);
         f.center(n, px + pw / 2.0F, py + 8.5F, Theme.TEXT, Font.SMALL);
         // browse arrows
         if (photos.size() > 1) {
            G.BACK.draw(f, px + 10.0F, py + ph / 2.0F, 12.0F, 0xB0FFFFFF);
            G.FORWARD.draw(f, px + pw - 10.0F, py + ph / 2.0F, 12.0F, 0xB0FFFFFF);
         }
         f.pop();
      }
      // the side bar: save, delete
      float sx = w - side / 2.0F;
      boolean confirming = this.confirmDelete == p.id() && this.act.millis() < this.confirmUntil;
      Ui.iconButton(this.ui, f, Z_SAVE, 0L, sx, h / 2.0F - 26.0F, 13.0F, G.SAVE, Theme.SURFACE3, Theme.TEXT, p.state() == 4);
      f.center("Save", sx, h / 2.0F - 10.0F, Theme.TEXT3, Font.SMALL);
      Ui.iconButton(this.ui, f, Z_DELETE, 0L, sx, h / 2.0F + 18.0F, 13.0F, G.TRASH, confirming ? Theme.RED : Theme.SURFACE3,
         confirming ? 0xFF1C0A08 : Theme.RED, true);
      f.center(confirming ? "Sure?" : "Delete", sx, h / 2.0F + 34.0F, confirming ? Theme.RED : Theme.TEXT3, Font.SMALL);
   }

   // ------------------------------------------------------------------------------------------------ input

   @Override
   public void tap(int id, long data) {
      PhoneModel.Cams cams = this.m.cams;
      switch (id) {
         case Z_REFRESH -> {
            this.act.camHub();
            this.act.sound(PhoneActions.Sfx.TAP);
         }
         case Z_CAM -> {
            if (data >= 0 && data < cams.list.size()) {
               PhoneModel.Cam c = cams.list.get((int)data);
               this.act.camRoll(c.pos());
               this.push(GALLERY);
            }
         }
         case Z_PHOTO -> {
            List<PhoneModel.PhotoRef> photos = this.photos();
            if (data >= 0 && data < photos.size()) {
               this.viewing = (int)data;
               this.viewingId = photos.get(this.viewing).id();
               this.chrome = true;
               this.push(VIEWER);
               this.act.camPrioritise(photos.get(this.viewing));
            }
         }
         case Z_LIVE -> {
            if (cams.gallery != null) {
               this.act.camWatch(cams.gallery.pos);
            }
         }
         case Z_CLEAR -> {
            if (cams.gallery == null) {
               return;
            }
            if (this.act.millis() < this.confirmClearUntil) {
               this.confirmClearUntil = 0L;
               this.act.camClear(cams.gallery.pos);
               this.ui.toast("Roll cleared");
               this.act.sound(PhoneActions.Sfx.SUCCESS);
            } else {
               this.confirmClearUntil = this.act.millis() + 4000L;
               this.act.sound(PhoneActions.Sfx.TAP);
            }
         }
         case Z_PREV -> this.step(-1);
         case Z_NEXT -> this.step(1);
         case Z_HIDE -> this.chrome = !this.chrome;
         case Z_VBACK -> this.back();
         case Z_SAVE -> {
            List<PhoneModel.PhotoRef> photos = this.photos();
            if (this.viewing < photos.size() && cams.gallery != null) {
               this.act.camSave(cams.gallery.label, photos.get(this.viewing));
               this.act.sound(PhoneActions.Sfx.SHUTTER);
            }
         }
         case Z_DELETE -> this.delete();
         default -> {
         }
      }
   }

   private void step(int d) {
      List<PhoneModel.PhotoRef> photos = this.photos();
      if (photos.size() > 1) {
         this.viewing = Math.floorMod(this.viewing + d, photos.size());
         this.viewingId = photos.get(this.viewing).id();
         this.confirmDelete = 0L;
         this.act.camPrioritise(photos.get(this.viewing));
         this.act.sound(PhoneActions.Sfx.KEY);
      }
   }

   private void delete() {
      List<PhoneModel.PhotoRef> photos = this.photos();
      PhoneModel.Gallery g = this.m.cams.gallery;
      if (g == null || this.viewing >= photos.size()) {
         return;
      }
      PhoneModel.PhotoRef p = photos.get(this.viewing);
      if (this.confirmDelete == p.id() && this.act.millis() < this.confirmUntil) {
         this.confirmDelete = 0L;
         this.act.camDelete(g.pos, p);
         this.ui.toast("Photo deleted from the camera");
         this.act.sound(PhoneActions.Sfx.SUCCESS);
         List<PhoneModel.PhotoRef> after = this.photos();
         if (after.isEmpty()) {
            this.back();
         } else {
            this.viewing = Math.min(this.viewing, after.size() - 1);
            this.viewingId = after.get(this.viewing).id();
         }
      } else {
         this.confirmDelete = p.id();
         this.confirmUntil = this.act.millis() + 4000L;
         this.act.sound(PhoneActions.Sfx.TAP);
      }
   }

   @Override
   public boolean key(int key, int mods) {
      if (this.page() == VIEWER) {
         if (key == Ui.K_LEFT || key == Ui.K_A) {
            this.step(-1);
            return true;
         }
         if (key == Ui.K_RIGHT || key == Ui.K_D) {
            this.step(1);
            return true;
         }
         if (key == Ui.K_DELETE) {
            this.delete();
            return true;
         }
      }
      return false;
   }

   @Override
   public boolean wheel(float amount) {
      if (this.page() == VIEWER) {
         this.step(amount > 0 ? -1 : 1);
         return true;
      }
      return false;
   }

   @Override
   public boolean drag(float x, float y, float dx, float dy) {
      // the viewer swallows drags (no list to scroll)
      return this.page() == VIEWER;
   }

   /** Called by the game when a roll arrives for a camera (opened from a camera block or a refresh). */
   public void showGallery() {
      if (this.page() == LIST) {
         this.push(GALLERY);
      }
   }
}
