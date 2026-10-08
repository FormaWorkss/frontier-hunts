package com.formaworks.frontierhunts.phone.client.ui.apps;

import com.formaworks.frontierhunts.phone.client.ui.App;
import com.formaworks.frontierhunts.phone.client.ui.Canvas.Font;
import com.formaworks.frontierhunts.phone.client.ui.Frame;
import com.formaworks.frontierhunts.phone.client.ui.G;
import com.formaworks.frontierhunts.phone.client.ui.PhoneModel;
import com.formaworks.frontierhunts.phone.client.ui.Theme;
import com.formaworks.frontierhunts.phone.client.ui.Ui;
import java.util.ArrayList;
import java.util.List;

/**
 * [1.4.0] Camera: the phone's own camera, which replaced the Field Camera. "Camera" and "Selfie" put the phone up: the
 * screen goes away and the whole view becomes the viewfinder, so the hunter can walk, crouch and turn to frame the shot
 * ({@code phone.client.PhoneCamera}). Photos are saved on this computer, full size, and shown here: a grid newest first,
 * a sideways viewer, delete, and "Send" texts a photo to another hunter.
 */
public final class CameraApp extends App {
   static final int GRID = 0;
   static final int VIEWER = 1;
   static final int SEND = 2;
   private static final int Z_SHOOT = 100;
   private static final int Z_SELFIE = 101;
   private static final int Z_SHOT = 102;
   private static final int Z_PREV = 103;
   private static final int Z_NEXT = 104;
   private static final int Z_SEND = 105;
   private static final int Z_DELETE = 106;
   private static final int Z_VBACK = 107;
   private static final int Z_TO = 108;
   private static final int Z_FOLDER = 109;
   private static final int Z_HIDE = 110;
   private int viewing;
   private String viewingKey = "";
   private String confirmKey = "";
   private long confirmUntil;
   private boolean chrome = true;
   private final List<String> people = new ArrayList<>();

   public CameraApp() {
      super("camera", "Camera", 16);
   }

   @Override
   public void opened() {
      this.act.photosRefresh();
   }

   @Override
   protected boolean landscapePage(int page) {
      return page == VIEWER;
   }

   @Override
   public int statusTint() {
      return this.page() == VIEWER ? 0xFF000000 : 0;
   }

   @Override
   protected void drawPage(Frame f, int page, float w, float h) {
      switch (page) {
         case VIEWER -> this.viewer(f, w, h);
         case SEND -> this.send(f, w, h);
         default -> this.grid(f, w, h);
      }
   }

   // ------------------------------------------------------------------------------------------------ the grid

   private void grid(Frame f, float w, float h) {
      PhoneModel.Photos ph = this.m.photos;
      f.fill(0.0F, 0.0F, w, h, Theme.BG);
      f.gradient(0.0F, 0.0F, w, 120.0F, 0xFF2A1B10, Theme.BG);
      int n = ph.shots.size();
      float y = Ui.header(this.ui, f, "Camera", !ph.loaded ? "Looking for your photos…" : (n == 0 ? "No photos yet" : n + (n == 1 ? " photo" : " photos")),
         false, w);
      // the two ways to shoot
      float bw = (w - 24.0F) / 2.0F, bh = 54.0F;
      this.bigButton(f, Z_SHOOT, 9.0F, y, bw, bh, G.CAMERA, "Camera", "Walk & frame", 0xFF3A2614, 0xFF5A3A1C);
      this.bigButton(f, Z_SELFIE, 15.0F + bw, y, bw, bh, G.PERSON, "Selfie", "Strike a pose", 0xFF143A2E, 0xFF1F5A47);
      y += bh + 8.0F;
      if (!ph.loaded) {
         return;
      }
      if (n == 0) {
         Ui.empty(this.ui, f, G.CAMERA, "Take your first photo",
            "Your view becomes the viewfinder: walk, crouch and turn to frame it. Click to shoot, right-click for selfies, the wheel zooms.",
            w / 2.0F, y + 6.0F, w - 36.0F);
         return;
      }
      float y0 = this.beginScroll(f, y, w, h);
      int cols = 3;
      float gap = 3.0F;
      float cw = (w - 18.0F - gap * (cols - 1)) / cols;
      for (int i = 0; i < n; i++) {
         PhoneModel.Shot s = ph.shots.get(i);
         float x = 9.0F + (i % cols) * (cw + gap);
         float cy = y0 + (i / cols) * (cw + gap);
         if (!f.visible(x, cy, cw, cw)) {
            continue;
         }
         PhoneModel.Pic p = ph.thumb.apply(s);
         f.round(x, cy, cw, cw, 4.0F, Theme.SURFACE2);
         if (p != null) {
            this.cover(f, p, x, cy, cw, cw);
         } else {
            float pulse = 0.5F + 0.5F * (float)Math.sin((this.act.millis() % 100000L) / 300.0 + i);
            f.fill(x + 2.0F, cy + 2.0F, cw - 4.0F, cw - 4.0F, Theme.withAlpha(0x2A332D, (int)(120 + 80 * pulse)));
         }
         if (this.ui.hot(Z_SHOT, i)) {
            f.fill(x, cy, cw, cw, 0x22FFFFFF);
         }
         f.zone(Z_SHOT, x, cy, cw, cw, i);
      }
      float end = y0 + ((n + cols - 1) / cols) * (cw + gap) + 4.0F;
      Ui.button(this.ui, f, Z_FOLDER, 0L, 9.0F, end, w - 18.0F, 18.0F, "Open the photo folder", Ui.PLAIN, true);
      end += 24.0F;
      this.endScroll(f, y0, end, w, h);
   }

   private void bigButton(Frame f, int zone, float x, float y, float w, float h, G glyph, String title, String sub, int top, int bottom) {
      boolean hot = this.ui.hot(zone, 0L);
      boolean down = this.ui.down(zone, 0L);
      float s = this.ui.anim(0xCA30L + zone, down ? 0.96F : 1.0F, 22.0F);
      f.push();
      f.translate(x + w / 2.0F, y + h / 2.0F);
      f.scale(s);
      f.translate(-w / 2.0F, -h / 2.0F);
      f.round(0.0F, 1.5F, w, h, 12.0F, 0x50000000);
      f.round(0.0F, 0.0F, w, h, 12.0F, hot ? Theme.mix(bottom, 0xFFFFFFFF, 0.08F) : bottom);
      f.round(0.0F, 0.0F, w, h * 0.6F, 12.0F, top);
      f.gradient(0.0F, h * 0.3F, w, h * 0.32F, top, hot ? Theme.mix(bottom, 0xFFFFFFFF, 0.08F) : bottom);
      f.circle(18.0F, 16.0F, 10.0F, 0x30FFFFFF);
      glyph.draw(f, 18.0F, 16.0F, 13.0F, Theme.TEXT);
      G.FORWARD.draw(f, w - 11.0F, 16.0F, 9.0F, 0x90FFFFFF);
      f.text(title, 9.0F, 29.0F, Theme.TEXT, Font.MEDIUM);
      f.text(this.txt.fit(f, sub, w - 14.0F, Font.SMALL), 9.5F, 42.0F, Theme.TEXT2, Font.SMALL);
      f.pop();
      f.zone(zone, x, y, w, h);
   }

   /** The picture cropped to fill the box (centre crop). */
   private void cover(Frame f, PhoneModel.Pic p, float x, float y, float w, float h) {
      float pa = p.w() / (float)Math.max(1, p.h()), ba = w / h;
      float u0 = 0.0F, u1 = 1.0F, v0 = 0.0F, v1 = 1.0F;
      if (pa > ba) {
         float k = ba / pa;
         u0 = (1.0F - k) / 2.0F;
         u1 = u0 + k;
      } else if (pa < ba) {
         float k = pa / ba;
         v0 = (1.0F - k) / 2.0F;
         v1 = v0 + k;
      }
      f.image(p.handle(), x, y, w, h, u0, v0, u1, v1, 0xFFFFFFFF);
   }

   // ------------------------------------------------------------------------------------------------ the viewer

   private PhoneModel.Shot current() {
      List<PhoneModel.Shot> shots = this.m.photos.shots;
      if (shots.isEmpty()) {
         return null;
      }
      // keep showing the same photo when the list changes under us
      if (this.viewing >= shots.size() || !shots.get(this.viewing).key().equals(this.viewingKey)) {
         for (int i = 0; i < shots.size(); i++) {
            if (shots.get(i).key().equals(this.viewingKey)) {
               this.viewing = i;
               break;
            }
         }
         this.viewing = Math.min(this.viewing, shots.size() - 1);
         this.viewingKey = shots.get(this.viewing).key();
      }
      return shots.get(this.viewing);
   }

   private void viewer(Frame f, float w, float h) {
      f.fill(0.0F, 0.0F, w, h, 0xFF000000);
      PhoneModel.Shot s = this.current();
      if (s == null) {
         return;
      }
      float side = 46.0F;
      PhoneModel.Pic p = this.m.photos.full.apply(s);
      if (p == null) {
         p = this.m.photos.thumb.apply(s);
      }
      float aw = w - side, ah = h - 8.0F;
      float pa = p == null ? 16.0F / 9.0F : p.w() / (float)Math.max(1, p.h());
      float pw = Math.min(aw, ah * pa), phh = pw / pa;
      float px = (aw - pw) / 2.0F, py = (h - phh) / 2.0F;
      if (p != null) {
         f.image(p.handle(), px, py, pw, phh, 0.0F, 0.0F, 1.0F, 1.0F, 0xFFFFFFFF);
      } else {
         f.fill(px, py, pw, phh, 0xFF101412);
         f.center("Developing…", px + pw / 2.0F, py + phh / 2.0F - 4.0F, Theme.TEXT3, Font.STRONG);
      }
      int n = this.m.photos.shots.size();
      f.zone(Z_HIDE, px + pw * 0.33F, py, pw * 0.34F, phh);
      f.zone(Z_PREV, px, py, pw * 0.33F, phh);
      f.zone(Z_NEXT, px + pw * 0.67F, py, pw * 0.33F, phh);
      float a = this.ui.anim(0xCA31L, this.chrome ? 1.0F : 0.0F, 12.0F);
      if (a > 0.01F) {
         f.push();
         f.alpha(a);
         f.gradient(px, py + phh - 30.0F, pw, 30.0F, 0x00000000, 0xB8000000);
         f.text(when(s.time()), px + 9.0F, py + phh - 17.0F, Theme.GOLD, Font.SMALL);
         Ui.iconButton(this.ui, f, Z_VBACK, 0L, px + 13.0F, py + 13.0F, 8.5F, G.BACK, 0x90000000, Theme.TEXT, true);
         String c = (this.viewing + 1) + " / " + n;
         float nw = f.width(c, Font.SMALL) + 10.0F;
         f.round(px + pw / 2.0F - nw / 2.0F, py + 6.0F, nw, 12.0F, 6.0F, 0x90000000);
         f.center(c, px + pw / 2.0F, py + 8.5F, Theme.TEXT, Font.SMALL);
         if (n > 1) {
            G.BACK.draw(f, px + 10.0F, py + phh / 2.0F, 12.0F, 0xB0FFFFFF);
            G.FORWARD.draw(f, px + pw - 10.0F, py + phh / 2.0F, 12.0F, 0xB0FFFFFF);
         }
         f.pop();
      }
      float sx = w - side / 2.0F;
      boolean signal = this.m.signal > 0;
      Ui.iconButton(this.ui, f, Z_SEND, 0L, sx, h / 2.0F - 26.0F, 13.0F, G.ARROW, signal ? Theme.BLAZE : Theme.SURFACE3, signal ? 0xFFFFFFFF : Theme.TEXT3,
         signal);
      f.center(signal ? "Send" : "No signal", sx, h / 2.0F - 10.0F, Theme.TEXT3, Font.SMALL);
      boolean confirming = this.confirmKey.equals(s.key()) && this.act.millis() < this.confirmUntil;
      Ui.iconButton(this.ui, f, Z_DELETE, 0L, sx, h / 2.0F + 18.0F, 13.0F, G.TRASH, confirming ? Theme.RED : Theme.SURFACE3,
         confirming ? 0xFF1C0A08 : Theme.RED, true);
      f.center(confirming ? "Sure?" : "Delete", sx, h / 2.0F + 34.0F, confirming ? Theme.RED : Theme.TEXT3, Font.SMALL);
   }

   private static final String[] MONTHS = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

   /** "Oct 8 · 1:42 AM" in the computer's own time (the photo's file time). */
   static String when(long ms) {
      if (ms <= 0L) {
         return "";
      }
      java.time.LocalDateTime t = java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(ms), java.time.ZoneId.systemDefault());
      int hr = t.getHour() % 12 == 0 ? 12 : t.getHour() % 12;
      return MONTHS[t.getMonthValue() - 1] + " " + t.getDayOfMonth() + " · " + hr + ":" + String.format(java.util.Locale.ROOT, "%02d", t.getMinute())
         + (t.getHour() < 12 ? " AM" : " PM");
   }

   // ------------------------------------------------------------------------------------------------ send to

   private void send(Frame f, float w, float h) {
      f.fill(0.0F, 0.0F, w, h, Theme.BG);
      float y = Ui.bar(this.ui, f, "Send photo", w, true);
      PhoneModel.Shot s = this.current();
      if (s != null) {
         PhoneModel.Pic p = this.m.photos.thumb.apply(s);
         float pw = w - 60.0F, pph = pw * 9.0F / 16.0F;
         f.round(30.0F - 2.0F, y - 2.0F, pw + 4.0F, pph + 4.0F, 6.0F, Theme.SURFACE3);
         if (p != null) {
            this.cover(f, p, 30.0F, y, pw, pph);
         }
         y += pph + 10.0F;
      }
      if (this.m.signal <= 0) {
         Ui.empty(this.ui, f, G.SIGNAL, "No signal", "Find higher ground or open country, then try again.", w / 2.0F, y, w - 40.0F);
         return;
      }
      this.people.clear();
      for (String p : this.m.players) {
         if (!p.equalsIgnoreCase(this.m.playerName) && !this.people.contains(p)) {
            this.people.add(p);
         }
      }
      for (String p : this.m.messages.contacts) {
         if (!p.equalsIgnoreCase(this.m.playerName) && !this.people.contains(p)) {
            this.people.add(p);
         }
      }
      Ui.section(f, "TO", 12.0F, y, w - 24.0F);
      y += 12.0F;
      float y0 = this.beginScroll(f, y, w, h);
      float ry = y0;
      if (this.people.isEmpty()) {
         Ui.empty(this.ui, f, G.PERSON, "Nobody to send to yet", "Hunters show up here once they are online or have used a Field Phone.", w / 2.0F, ry,
            w - 40.0F);
         ry += 100.0F;
      }
      for (int i = 0; i < this.people.size(); i++) {
         String p = this.people.get(i);
         boolean on = this.m.players.stream().anyMatch(x -> x.equalsIgnoreCase(p));
         ry += Ui.row(this.ui, f, Z_TO, i, 12.0F, ry, w - 24.0F, G.PERSON, on ? Theme.MOSS : Theme.TEXT3, p, on ? "Online now" : "Away · gets it later",
            "", Theme.TEXT3, true) + 2.0F;
      }
      this.endScroll(f, y0, ry, w, h);
   }

   // ------------------------------------------------------------------------------------------------ input

   @Override
   public void tap(int id, long data) {
      List<PhoneModel.Shot> shots = this.m.photos.shots;
      switch (id) {
         case Z_SHOOT -> this.act.camera(false, null);
         case Z_SELFIE -> this.act.camera(true, null);
         case Z_FOLDER -> this.act.photoFolder();
         case Z_SHOT -> {
            if (data >= 0 && data < shots.size()) {
               this.viewing = (int)data;
               this.viewingKey = shots.get(this.viewing).key();
               this.chrome = true;
               this.push(VIEWER);
            }
         }
         case Z_PREV -> this.step(-1);
         case Z_NEXT -> this.step(1);
         case Z_HIDE -> this.chrome = !this.chrome;
         case Z_VBACK -> this.back();
         case Z_SEND -> {
            if (this.current() != null) {
               this.push(SEND);
            }
         }
         case Z_DELETE -> {
            PhoneModel.Shot s = this.current();
            if (s == null) {
               return;
            }
            if (this.confirmKey.equals(s.key()) && this.act.millis() < this.confirmUntil) {
               this.confirmKey = "";
               this.act.photoDelete(s);
               if (shots.size() <= 1) {
                  this.back();
               }
            } else {
               this.confirmKey = s.key();
               this.confirmUntil = this.act.millis() + 3000L;
            }
         }
         case Z_TO -> {
            PhoneModel.Shot s = this.current();
            if (s != null && data >= 0 && data < this.people.size()) {
               this.act.photoSend(this.people.get((int)data), s);
               this.back();
            }
         }
         default -> {
         }
      }
   }

   private void step(int d) {
      int n = this.m.photos.shots.size();
      if (n > 1) {
         this.viewing = Math.floorMod(this.viewing + d, n);
         this.viewingKey = this.m.photos.shots.get(this.viewing).key();
      }
   }

   @Override
   public boolean key(int key, int mods) {
      if (this.page() == VIEWER) {
         if (key == Ui.K_LEFT) {
            this.step(-1);
            return true;
         }
         if (key == Ui.K_RIGHT) {
            this.step(1);
            return true;
         }
      }
      return false;
   }
}
