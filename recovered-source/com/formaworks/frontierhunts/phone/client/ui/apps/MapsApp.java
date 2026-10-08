package com.formaworks.frontierhunts.phone.client.ui.apps;

import com.formaworks.frontierhunts.phone.client.ui.App;
import com.formaworks.frontierhunts.phone.client.ui.Canvas.Font;
import com.formaworks.frontierhunts.phone.client.ui.Frame;
import com.formaworks.frontierhunts.phone.client.ui.G;
import com.formaworks.frontierhunts.phone.client.ui.PhoneActions;
import com.formaworks.frontierhunts.phone.client.ui.PhoneModel;
import com.formaworks.frontierhunts.phone.client.ui.Theme;
import com.formaworks.frontierhunts.phone.client.ui.Ui;
import java.util.ArrayList;
import java.util.List;

/**
 * [phone] Maps: a shaded topographic map of the ground around you (surveyed from the terrain your game has loaded:
 * hill shading, contour lines, water), north up, with you on it; your beginner hunting area during the first hunt,
 * camps, lodges, contract boards, ranger counters, trail cameras and your own pins. Places off the map point from its
 * edge. Pick one to be guided there: a bearing and distance here, on the Compass, and on screen while you walk.
 */
public final class MapsApp extends App {
   static final int MAP = 0;
   static final int PLACE = 1;
   static final int ADD = 2;
   private static final int Z_TAB = 100;
   private static final int Z_ZOOM = 101;
   private static final int Z_CENTER = 102;
   private static final int Z_MARK = 103;
   private static final int Z_ROW = 104;
   private static final int Z_NAV = 105;
   private static final int Z_REMOVE = 106;
   private static final int Z_DROP = 107;
   private static final int Z_STOP = 108;
   private static final int Z_ICON = 109;
   private static final int Z_SAVE = 110;
   private static final int Z_FIELD = 111;
   private static final String[] TABS = {"Map", "Places"};
   private static final float[] SPANS = {320.0F, 160.0F, 80.0F};
   public static final G[] PIN_ICONS = {G.PIN, G.FLAG, G.TENT, G.BINOCULARS, G.TARGET, G.STAR, G.TRACK, G.CAMERA};
   private int tab;
   private int zoom = 1;
   private float panX, panZ;
   private String selected = "";
   private final StringBuilder pinName = new StringBuilder();
   private int pinIcon;
   private long confirmRemove;
   private final List<PhoneModel.Place> sorted = new ArrayList<>();

   public MapsApp() {
      super("maps", "Maps", 3);
   }

   @Override
   public void opened() {
      this.act.refresh(PhoneActions.R_PLACES);
      this.act.mapWanted(true);
      this.panX = 0.0F;
      this.panZ = 0.0F;
   }

   @Override
   public void closed() {
      this.act.mapWanted(false);
   }

   @Override
   public boolean grabsKeys() {
      return this.page() == ADD;
   }

   @Override
   protected void drawPage(Frame f, int page, float w, float h) {
      switch (page) {
         case PLACE -> this.place(f, w, h);
         case ADD -> this.add(f, w, h);
         default -> this.main(f, w, h);
      }
   }

   static G glyph(PhoneModel.Place p) {
      String k = p.kind();
      if (k.startsWith("pin")) {
         int i = k.length() > 3 ? Math.max(0, Math.min(PIN_ICONS.length - 1, k.charAt(3) - '0')) : 0;
         return PIN_ICONS[i];
      }
      return switch (k) {
         case "area" -> G.TARGET;
         case "camp" -> G.TENT;
         case "lodge", "station" -> G.LODGE;
         case "board" -> G.BOARD;
         case "ranger" -> G.RANGER;
         case "cam" -> G.CAMERA;
         case "home" -> G.HOME;
         case "spot" -> G.FLAG;
         case "kill" -> G.ANTLER;
         default -> G.PIN;
      };
   }

   static int color(PhoneModel.Place p) {
      String k = p.kind();
      if (k.startsWith("pin")) {
         return Theme.BLAZE;
      }
      return switch (k) {
         case "area", "spot" -> Theme.YELLOW;
         case "camp" -> Theme.MOSS;
         case "lodge", "station" -> Theme.GOLD;
         case "board" -> 0xFFD9A066;
         case "ranger" -> Theme.SKY;
         case "cam" -> Theme.VIOLET;
         case "home" -> 0xFFE8E4D8;
         case "kill" -> 0xFFE05A4A;
         default -> Theme.BLAZE;
      };
   }

   private PhoneModel.Place find(String id) {
      for (PhoneModel.Place p : this.m.map.places) {
         if (p.id().equals(id)) {
            return p;
         }
      }
      return null;
   }

   private double dist(PhoneModel.Place p) {
      return Math.hypot(p.x() + 0.5 - this.m.x, p.z() + 0.5 - this.m.z);
   }

   // ------------------------------------------------------------------------------------------------ main (map | places)

   private void main(Frame f, float w, float h) {
      f.fill(0.0F, 0.0F, w, h, Theme.BG);
      float y = Theme.STATUS_H + 4.0F;
      Ui.segmented(this.ui, f, Z_TAB, 9.0F, y, w - 18.0F, 16.0F, TABS, this.tab);
      y += 22.0F;
      if (this.tab == 0) {
         this.map(f, w, h, y);
      } else {
         this.places(f, w, h, y);
      }
   }

   private void map(Frame f, float w, float h, float top) {
      PhoneModel m = this.m;
      PhoneModel.MapData md = m.map;
      PhoneModel.Place target = this.find(md.target);
      float sheet = target != null ? 58.0F : 40.0F;
      float mx = 0.0F, my = top, mw = w, mh = h - top - sheet;
      f.clip(mx, my, mw, mh);
      f.fill(mx, my, mw, mh, 0xFF1A1F1B);
      float span = SPANS[this.zoom];
      float ppb = mw / span;
      double vcx = m.x + this.panX, vcz = m.z + this.panZ;
      float ccx = mx + mw / 2.0F, ccy = my + mh / 2.0F;
      // the surveyed map
      if (md.texture != null) {
         float tx0 = (float)(ccx + (md.centerX - md.size / 2.0 - vcx) * ppb);
         float ty0 = (float)(ccy + (md.centerZ - md.size / 2.0 - vcz) * ppb);
         f.image(md.texture, tx0, ty0, md.size * ppb, md.size * ppb, 0.0F, 0.0F, 1.0F, 1.0F, 0xFFFFFFFF);
      }
      // grid every 64 blocks, with coordinates
      float step = 64.0F * ppb;
      double gx0 = Math.floor((vcx - span / 2.0) / 64.0) * 64.0;
      for (int i = 0; i < 8; i++) {
         double gx = gx0 + i * 64.0;
         float sx = (float)(ccx + (gx - vcx) * ppb);
         if (sx > mx && sx < mx + mw) {
            f.fill(sx, my, 0.5F, mh, 0x22FFFFFF);
         }
      }
      double gz0 = Math.floor((vcz - mh / ppb / 2.0) / 64.0) * 64.0;
      for (int i = 0; i < 14; i++) {
         double gz = gz0 + i * 64.0;
         float sy = (float)(ccy + (gz - vcz) * ppb);
         if (sy > my && sy < my + mh) {
            f.fill(mx, sy, mw, 0.5F, 0x22FFFFFF);
         }
      }
      // the beginner area
      if (md.area) {
         float ax = (float)(ccx + (md.areaX + 0.5 - vcx) * ppb), az = (float)(ccy + (md.areaZ + 0.5 - vcz) * ppb);
         float ar = md.areaR * ppb;
         if (ar > 2.0F) {
            f.circle(ax, az, ar, 0x2AF0C64E);
            f.ring(ax, az, ar, 1.2F, 0xC0F0C64E);
         }
      }
      // markers
      boolean any = false;
      for (PhoneModel.Place p : md.places) {
         float sx = (float)(ccx + (p.x() + 0.5 - vcx) * ppb), sy = (float)(ccy + (p.z() + 0.5 - vcz) * ppb);
         boolean inside = sx > mx + 6.0F && sx < mx + mw - 6.0F && sy > my + 6.0F && sy < my + mh - 6.0F;
         int col = color(p);
         boolean tgt = p.id().equals(md.target);
         if (inside) {
            float r = tgt ? 7.5F : 6.0F;
            f.circle(sx, sy + 0.8F, r + 0.8F, 0x80000000);
            f.circle(sx, sy, r + 0.8F, 0xFF101411);
            f.circle(sx, sy, r, col);
            glyph(p).draw(f, sx, sy, r * 1.25F, 0xFF15130F);
            if (this.zoom >= 1 || tgt) {
               String n = this.txt.fit(f, p.name(), 70.0F, Font.SMALL);
               float nw = f.width(n, Font.SMALL);
               f.round(sx - nw / 2.0F - 3.0F, sy + r + 2.0F, nw + 6.0F, 10.0F, 3.0F, 0xB0000000);
               f.center(n, sx, sy + r + 3.0F, Theme.TEXT, Font.SMALL);
            }
            f.zone(Z_MARK, sx - 9.0F, sy - 9.0F, 18.0F, 18.0F, this.m.map.places.indexOf(p));
            any = true;
         } else if (tgt || p.kind().equals("area") || p.kind().equals("camp")) {
            // edge arrow toward an important place off the map
            double ang = Math.atan2(sy - ccy, sx - ccx);
            float ex = ccx + (float)Math.cos(ang) * (mw / 2.0F - 10.0F);
            float ey = ccy + (float)Math.sin(ang) * (mh / 2.0F - 10.0F);
            float ux = (float)Math.cos(ang), uy = (float)Math.sin(ang);
            f.circle(ex, ey, 7.0F, 0xD0101411);
            f.triangle(ex + ux * 9.0F, ey + uy * 9.0F, ex - uy * 4.0F + ux * 5.0F, ey + ux * 4.0F + uy * 5.0F, ex + uy * 4.0F + ux * 5.0F,
               ey - ux * 4.0F + uy * 5.0F, col);
            glyph(p).draw(f, ex, ey, 8.0F, col);
            f.zone(Z_MARK, ex - 8.0F, ey - 8.0F, 16.0F, 16.0F, this.m.map.places.indexOf(p));
         }
      }
      // you
      float px = (float)(ccx - this.panX * ppb), pz = (float)(ccy - this.panZ * ppb);
      double heading = Math.toRadians(m.yaw + 180.0F);
      float fx = (float)Math.sin(heading), fy = (float)-Math.cos(heading);
      for (int k = 0; k < 5; k++) {
         double a0 = heading - 0.5 + k * 0.2, a1 = a0 + 0.2;
         f.triangle(px, pz, px + (float)Math.sin(a0) * 26.0F, pz - (float)Math.cos(a0) * 26.0F, px + (float)Math.sin(a1) * 26.0F,
            pz - (float)Math.cos(a1) * 26.0F, 0x1A73A9DE);
      }
      f.circle(px, pz, 6.5F, 0xFFFFFFFF);
      f.circle(px, pz, 5.0F, 0xFF3D8BFF);
      f.triangle(px + fx * 10.0F, pz + fy * 10.0F, px - fy * 3.6F + fx * 6.0F, pz + fx * 3.6F + fy * 6.0F, px + fy * 3.6F + fx * 6.0F,
         pz - fx * 3.6F + fy * 6.0F, 0xFF3D8BFF);
      // navigation line
      if (target != null) {
         float tx = (float)(ccx + (target.x() + 0.5 - vcx) * ppb), tz = (float)(ccy + (target.z() + 0.5 - vcz) * ppb);
         float dx = tx - px, dz = tz - pz;
         float len = (float)Math.sqrt(dx * dx + dz * dz);
         int dashes = (int)Math.min(80.0F, len / 6.0F);
         for (int i = 0; i < dashes; i++) {
            float t0 = (i * 6.0F + 8.0F) / len, t1 = (i * 6.0F + 11.0F) / len;
            if (t1 < 1.0F) {
               f.line(px + dx * t0, pz + dz * t0, px + dx * t1, pz + dz * t1, 1.4F, Theme.BLAZE);
            }
         }
      }
      // surveying overlay
      if (md.texture == null || md.progress < 1.0F) {
         float pr = md.progress;
         f.round(ccx - 52.0F, my + 8.0F, 104.0F, 16.0F, 8.0F, 0xC0101411);
         Ui.bar(f, ccx - 44.0F, my + 18.0F, 88.0F, 2.0F, pr, 0x30FFFFFF, Theme.MOSS);
         f.center("Surveying the ground… " + Math.round(pr * 100.0F) + "%", ccx, my + 10.0F, Theme.TEXT2, Font.SMALL);
      }
      f.unclip();
      // north, scale
      f.circle(mx + 14.0F, my + 14.0F, 8.0F, 0xC0101411);
      f.triangle(mx + 14.0F, my + 7.5F, mx + 17.0F, my + 15.0F, mx + 11.0F, my + 15.0F, Theme.RED);
      f.center("N", mx + 14.0F, my + 13.5F, Theme.TEXT, Font.SMALL);
      float bar = 50.0F * ppb;
      f.fill(mx + 8.0F, my + mh - 9.0F, bar, 1.2F, 0xE0FFFFFF);
      f.fill(mx + 8.0F, my + mh - 12.0F, 1.0F, 4.0F, 0xE0FFFFFF);
      f.fill(mx + 8.0F + bar - 1.0F, my + mh - 12.0F, 1.0F, 4.0F, 0xE0FFFFFF);
      f.text("50 m", mx + 11.0F + bar, my + mh - 14.0F, 0xE0FFFFFF, Font.SMALL);
      // zoom and recentre
      float bx = mx + mw - 16.0F;
      Ui.iconButton(this.ui, f, Z_ZOOM, 1L, bx, my + 16.0F, 9.0F, G.PLUS, 0xD0101411, Theme.TEXT, this.zoom < SPANS.length - 1);
      Ui.iconButton(this.ui, f, Z_ZOOM, 0L, bx, my + 37.0F, 9.0F, G.MINUS, 0xD0101411, Theme.TEXT, this.zoom > 0);
      boolean panned = Math.abs(this.panX) > 1.0F || Math.abs(this.panZ) > 1.0F;
      Ui.iconButton(this.ui, f, Z_CENTER, 0L, bx, my + 58.0F, 9.0F, G.RECENTER, 0xD0101411, panned ? Theme.BLAZE : Theme.TEXT2, true);
      // the bottom sheet
      float sy = h - sheet;
      f.fill(0.0F, sy, w, sheet, Theme.SURFACE);
      f.fill(0.0F, sy, w, 0.5F, Theme.LINE);
      if (target != null) {
         double d = this.dist(target);
         float b = Ui.bearing(m.x, m.z, target.x() + 0.5, target.z() + 0.5);
         float rel = (float)Math.toRadians(b - (m.yaw + 180.0F));
         float ax = 22.0F, ay = sy + 21.0F;
         f.circle(ax, ay, 12.0F, Theme.withAlpha(Theme.shade(Theme.BLAZE, 0.3F), 255));
         float ux = (float)Math.sin(rel), uy = (float)-Math.cos(rel);
         f.triangle(ax + ux * 9.0F, ay + uy * 9.0F, ax - uy * 5.0F - ux * 5.0F, ay + ux * 5.0F - uy * 5.0F, ax + uy * 5.0F - ux * 5.0F,
            ay - ux * 5.0F - uy * 5.0F, Theme.BLAZE);
         f.text(this.txt.fit(f, target.name(), w - 90.0F, Font.STRONG), 40.0F, sy + 9.0F, Theme.TEXT, Font.STRONG);
         f.text(Ui.distance(d) + " · " + Ui.point(b) + " · " + Math.round(b) + "°", 40.0F, sy + 22.0F, Theme.BLAZE, Font.SMALL);
         Ui.button(this.ui, f, Z_STOP, 0L, w - 46.0F, sy + 11.0F, 38.0F, 18.0F, "End", Ui.TONAL, true);
         if (d < 8.0) {
            f.text("You have arrived", 40.0F, sy + 34.0F, Theme.MOSS, Font.SMALL);
         }
      } else {
         Ui.button(this.ui, f, Z_DROP, 0L, 9.0F, sy + 9.0F, w - 18.0F, 20.0F, "Drop a pin here", Ui.TONAL, this.m.signal > 0 || true);
      }
   }

   private void places(Frame f, float w, float h, float top) {
      PhoneModel m = this.m;
      this.sorted.clear();
      this.sorted.addAll(m.map.places);
      this.sorted.sort((a, b) -> Double.compare(this.dist(a), this.dist(b)));
      float y0 = this.beginScroll(f, top, w, h);
      float y = y0;
      if (m.map.area) {
         Ui.card(f, 9.0F, y, w - 18.0F, 46.0F, Theme.withAlpha(Theme.shade(Theme.YELLOW, 0.22F), 255));
         G.TARGET.draw(f, 21.0F, y + 12.0F, 12.0F, Theme.YELLOW);
         f.text(this.txt.fit(f, m.map.areaTitle.isEmpty() ? "Your beginner area" : m.map.areaTitle, w - 50.0F, Font.STRONG), 31.0F, y + 7.0F, Theme.TEXT,
            Font.STRONG);
         double d = Math.hypot(m.map.areaX + 0.5 - m.x, m.map.areaZ + 0.5 - m.z);
         float b = Ui.bearing(m.x, m.z, m.map.areaX + 0.5, m.map.areaZ + 0.5);
         String line = d <= m.map.areaR ? "You are inside it" : Ui.distance(Math.max(0.0, d - m.map.areaR)) + " to its edge · " + Ui.point(b);
         f.text(line, 31.0F, y + 19.0F, Theme.YELLOW, Font.SMALL);
         this.txt.para(f, this.txt.fit(f, m.map.areaNote, (w - 50.0F) * 2.0F, Font.SMALL), 31.0F, y + 29.0F, w - 46.0F, Theme.TEXT3, Font.SMALL, 0.0F);
         y += 52.0F;
      }
      if (this.sorted.isEmpty()) {
         Ui.empty(this.ui, f, G.MAP, "No places yet", "Visit a lodge, a contract board or a ranger counter and it is remembered here. Drop pins on the map.",
            w / 2.0F, y + 20.0F, w - 40.0F);
         y += 120.0F;
      }
      for (int i = 0; i < this.sorted.size(); i++) {
         PhoneModel.Place p = this.sorted.get(i);
         double d = this.dist(p);
         String sub = Ui.distance(d) + " " + Ui.point(Ui.bearing(m.x, m.z, p.x() + 0.5, p.z() + 0.5)) + (p.note().isEmpty() ? "" : " · " + p.note());
         boolean tgt = p.id().equals(m.map.target);
         if (f.visible(0.0F, y, w, 34.0F)) {
            Ui.row(this.ui, f, Z_ROW, m.map.places.indexOf(p), 12.0F, y, w - 24.0F, glyph(p), color(p), p.name(), sub, tgt ? "Guiding" : "",
               Theme.BLAZE, true);
         }
         y += 34.0F;
      }
      this.endScroll(f, y0, y, w, h);
   }

   // ------------------------------------------------------------------------------------------------ a place

   private void place(Frame f, float w, float h) {
      f.fill(0.0F, 0.0F, w, h, Theme.BG);
      PhoneModel.Place p = this.find(this.selected);
      float y = Ui.bar(this.ui, f, p == null ? "Place" : p.name(), w, true);
      if (p == null) {
         return;
      }
      PhoneModel m = this.m;
      double d = this.dist(p);
      float b = Ui.bearing(m.x, m.z, p.x() + 0.5, p.z() + 0.5);
      float cx = w / 2.0F, cy = y + 60.0F;
      f.circle(cx, cy, 50.0F, Theme.SURFACE2);
      f.ring(cx, cy, 50.0F, 1.0F, Theme.LINE);
      float rel = (float)Math.toRadians(b - (m.yaw + 180.0F));
      float ux = (float)Math.sin(rel), uy = (float)-Math.cos(rel);
      int col = color(p);
      f.triangle(cx + ux * 38.0F, cy + uy * 38.0F, cx - uy * 16.0F - ux * 14.0F, cy + ux * 16.0F - uy * 14.0F, cx - ux * 4.0F, cy - uy * 4.0F, col);
      f.triangle(cx + ux * 38.0F, cy + uy * 38.0F, cx - ux * 4.0F, cy - uy * 4.0F, cx + uy * 16.0F - ux * 14.0F, cy - ux * 16.0F - uy * 14.0F,
         Theme.shade(col, 0.75F));
      y = cy + 58.0F;
      f.center(Ui.distance(d), cx, y, Theme.TEXT, Font.DISPLAY);
      y += 30.0F;
      f.center(Ui.point(b) + " · " + Math.round(b) + "° · " + (p.y() > m.y + 4 ? Math.round(p.y() - m.y) + " m up" : (p.y() < m.y - 4 ? Math.round(m.y - p.y())
         + " m down" : "level")), cx, y, Theme.TEXT2, Font.SMALL);
      y += 14.0F;
      Ui.card(f, 9.0F, y, w - 18.0F, 30.0F);
      glyph(p).draw(f, 22.0F, y + 15.0F, 12.0F, col);
      f.text(KIND.getOrDefault(p.kind().startsWith("pin") ? "pin" : p.kind(), "Place"), 34.0F, y + 5.0F, Theme.TEXT, Font.STRONG);
      f.text("X " + p.x() + "  Y " + p.y() + "  Z " + p.z() + (p.note().isEmpty() ? "" : " · " + p.note()), 34.0F, y + 17.0F, Theme.TEXT3, Font.SMALL);
      y += 38.0F;
      boolean tgt = p.id().equals(m.map.target);
      Ui.button(this.ui, f, Z_NAV, 0L, 9.0F, y, w - 18.0F, 22.0F, tgt ? "Stop guiding" : "Guide me there", tgt ? Ui.TONAL : Ui.FILLED, true);
      y += 28.0F;
      if (p.removable()) {
         boolean sure = this.confirmRemove > this.act.millis();
         Ui.button(this.ui, f, Z_REMOVE, 0L, 9.0F, y, w - 18.0F, 20.0F, sure ? "Tap again to remove" : "Remove pin", Ui.DANGER, true);
      }
   }

   private static final java.util.Map<String, String> KIND = java.util.Map.ofEntries(java.util.Map.entry("area", "Beginner hunting area"),
      java.util.Map.entry("camp", "Your camp"), java.util.Map.entry("lodge", "Lodge stores"), java.util.Map.entry("station", "Expedition board"),
      java.util.Map.entry("board", "Contract board"), java.util.Map.entry("ranger", "Ranger counter"), java.util.Map.entry("cam", "Trail camera"),
      java.util.Map.entry("pin", "Your pin"), java.util.Map.entry("home", "Your bed"), java.util.Map.entry("spot", "First-hunt camp spot"),
      java.util.Map.entry("kill", "Your last harvest"));

   // ------------------------------------------------------------------------------------------------ dropping a pin

   private void add(Frame f, float w, float h) {
      f.fill(0.0F, 0.0F, w, h, Theme.BG);
      float y = Ui.bar(this.ui, f, "Drop a pin", w, true);
      PhoneModel m = this.m;
      f.text("Name", 12.0F, y + 2.0F, Theme.TEXT3, Font.SMALL);
      y += 12.0F;
      f.round(9.0F, y, w - 18.0F, 22.0F, 7.0F, Theme.SURFACE2);
      String name = this.pinName.toString();
      float tx = 16.0F;
      if (name.isEmpty()) {
         f.text("Pin " + (this.pins() + 1), tx, y + 7.0F, Theme.TEXT3, Font.BODY);
      } else {
         f.text(this.txt.fit(f, name, w - 40.0F, Font.BODY), tx, y + 7.0F, Theme.TEXT, Font.BODY);
      }
      boolean blink = (this.act.millis() / 500L) % 2L == 0L;
      if (blink) {
         float cx = tx + (name.isEmpty() ? 0.0F : Math.min(w - 40.0F, f.width(name, Font.BODY))) + 0.5F;
         f.fill(cx, y + 5.0F, 1.0F, 12.0F, Theme.BLAZE);
      }
      f.zone(Z_FIELD, 9.0F, y, w - 18.0F, 22.0F);
      y += 28.0F;
      f.text("Type a name with your keyboard.", 12.0F, y, Theme.TEXT3, Font.SMALL);
      y += 16.0F;
      f.text("Icon", 12.0F, y, Theme.TEXT3, Font.SMALL);
      y += 12.0F;
      float s = (w - 18.0F - 3 * 6.0F) / 4.0F;
      for (int i = 0; i < PIN_ICONS.length; i++) {
         float x = 9.0F + (i % 4) * (s + 6.0F);
         float yy = y + (i / 4) * (s * 0.7F + 6.0F);
         boolean sel = i == this.pinIcon;
         boolean hot = this.ui.hot(Z_ICON, i);
         f.round(x, yy, s, s * 0.7F, 8.0F, sel ? Theme.withAlpha(Theme.shade(Theme.BLAZE, 0.35F), 255) : (hot ? Theme.SURFACE3 : Theme.SURFACE2));
         PIN_ICONS[i].draw(f, x + s / 2.0F, yy + s * 0.35F, 14.0F, sel ? Theme.BLAZE : Theme.TEXT2);
         f.zone(Z_ICON, x, yy, s, s * 0.7F, i);
      }
      y += 2 * (s * 0.7F + 6.0F) + 6.0F;
      f.text("Here: X " + (int)Math.floor(m.x) + "  Y " + (int)Math.floor(m.y) + "  Z " + (int)Math.floor(m.z), 12.0F, y, Theme.TEXT2, Font.SMALL);
      y += 14.0F;
      Ui.button(this.ui, f, Z_SAVE, 0L, 9.0F, y, w - 18.0F, 22.0F, "Save pin", Ui.FILLED, this.pins() < 32);
      if (this.pins() >= 32) {
         f.center("You have 32 pins: remove one first.", w / 2.0F, y + 28.0F, Theme.RED, Font.SMALL);
      }
   }

   private int pins() {
      int n = 0;
      for (PhoneModel.Place p : this.m.map.places) {
         if (p.kind().startsWith("pin")) {
            n++;
         }
      }
      return n;
   }

   // ------------------------------------------------------------------------------------------------ input

   @Override
   public void tap(int id, long data) {
      PhoneModel m = this.m;
      switch (id) {
         case Z_TAB -> {
            this.tab = (int)data;
            this.act.sound(PhoneActions.Sfx.TAP);
         }
         case Z_ZOOM -> {
            this.zoom = Math.max(0, Math.min(SPANS.length - 1, this.zoom + (data == 1L ? 1 : -1)));
            this.act.sound(PhoneActions.Sfx.KEY);
         }
         case Z_CENTER -> {
            this.panX = 0.0F;
            this.panZ = 0.0F;
            this.act.sound(PhoneActions.Sfx.KEY);
         }
         case Z_MARK, Z_ROW -> {
            if (data >= 0 && data < m.map.places.size()) {
               this.selected = m.map.places.get((int)data).id();
               this.confirmRemove = 0L;
               this.push(PLACE);
            }
         }
         case Z_NAV -> {
            PhoneModel.Place p = this.find(this.selected);
            if (p != null) {
               boolean tgt = p.id().equals(m.map.target);
               this.act.navigate(tgt ? "" : p.id());
               if (!tgt) {
                  this.ui.toast("Guiding you to " + p.name());
                  this.tab = 0;
                  this.back();
               }
               this.act.sound(PhoneActions.Sfx.TOGGLE);
            }
         }
         case Z_STOP -> {
            this.act.navigate("");
            this.act.sound(PhoneActions.Sfx.TAP);
         }
         case Z_REMOVE -> {
            if (this.confirmRemove > this.act.millis()) {
               this.act.removePlace(this.selected);
               this.confirmRemove = 0L;
               this.back();
            } else {
               this.confirmRemove = this.act.millis() + 4000L;
               this.act.sound(PhoneActions.Sfx.TAP);
            }
         }
         case Z_DROP -> {
            this.pinName.setLength(0);
            this.pinIcon = 0;
            this.push(ADD);
         }
         case Z_ICON -> {
            this.pinIcon = (int)Math.max(0, Math.min(PIN_ICONS.length - 1, data));
            this.act.sound(PhoneActions.Sfx.KEY);
         }
         case Z_SAVE -> this.save();
         default -> {
         }
      }
   }

   private void save() {
      String n = this.pinName.toString().trim();
      if (n.isEmpty()) {
         n = "Pin " + (this.pins() + 1);
      }
      this.act.addPin(n, this.pinIcon);
      this.ui.toast("Pin saved: " + n);
      this.act.sound(PhoneActions.Sfx.SUCCESS);
      this.back();
   }

   @Override
   public boolean typed(char c) {
      if (this.page() != ADD) {
         return false;
      }
      if (c >= 32 && c != 127 && this.pinName.length() < 24 && (Character.isLetterOrDigit(c) || " '&.-#".indexOf(c) >= 0)) {
         this.pinName.append(c);
         this.act.sound(PhoneActions.Sfx.KEY);
      }
      return true;
   }

   @Override
   public boolean key(int key, int mods) {
      if (this.page() == ADD) {
         if (key == Ui.K_BACKSPACE) {
            if (this.pinName.length() > 0) {
               this.pinName.setLength(this.pinName.length() - 1);
            }
            return true;
         }
         if (key == Ui.K_ENTER) {
            this.save();
            return true;
         }
         return true;
      }
      return false;
   }

   @Override
   public boolean drag(float x, float y, float dx, float dy) {
      if (this.page() == MAP && this.tab == 0) {
         float k = SPANS[this.zoom] / 162.0F;
         float lim = 160.0F;
         this.panX = Math.max(-lim, Math.min(lim, this.panX - dx * k));
         this.panZ = Math.max(-lim, Math.min(lim, this.panZ - dy * k));
         return true;
      }
      return false;
   }
}
