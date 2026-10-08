package com.formaworks.frontierhunts.phone.client.ui.apps;

import com.formaworks.frontierhunts.phone.client.ui.App;
import com.formaworks.frontierhunts.phone.client.ui.Canvas.Font;
import com.formaworks.frontierhunts.phone.client.ui.Frame;
import com.formaworks.frontierhunts.phone.client.ui.G;
import com.formaworks.frontierhunts.phone.client.ui.PhoneModel;
import com.formaworks.frontierhunts.phone.client.ui.Theme;
import com.formaworks.frontierhunts.phone.client.ui.Ui;
import com.formaworks.frontierhunts.phone.client.ui.Wx;

/**
 * [phone] Compass: a heading-up rose that turns as you turn, with the wind and your scent cone laid on it (face into
 * the wind when you stalk), the sun, and the bearing to whatever Maps is guiding you to; your position and elevation.
 */
public final class CompassApp extends App {
   private static final String[] CARD = {"N", "E", "S", "W"};
   private static final String[] POS_LABELS = {"X", "ELEVATION", "Z"};
   private final String[] pos = {"", "", ""};
   private long px = Long.MIN_VALUE, py = Long.MIN_VALUE, pz = Long.MIN_VALUE;

   /** The X / elevation / Z strings, rebuilt only when the hunter crosses a block. */
   private String[] position(com.formaworks.frontierhunts.phone.client.ui.PhoneModel m) {
      long x = (long)Math.floor(m.x), y = Math.round(m.y), z = (long)Math.floor(m.z);
      if (x != this.px || y != this.py || z != this.pz) {
         this.px = x;
         this.py = y;
         this.pz = z;
         this.pos[0] = Long.toString(x);
         this.pos[1] = y + " m";
         this.pos[2] = Long.toString(z);
      }
      return this.pos;
   }

   private float shownHeading = Float.NaN;

   public CompassApp() {
      super("compass", "Compass", 13);
   }

   @Override
   protected void drawPage(Frame f, int page, float w, float h) {
      PhoneModel m = this.m;
      f.fill(0.0F, 0.0F, w, h, 0xFF0B0E0C);
      float y = Ui.header(this.ui, f, "Compass", "", false, w) + 2.0F;
      float heading = (float)Math.floorMod((long)Math.round((m.yaw + 180.0F) * 10.0F), 3600L) / 10.0F;
      if (Float.isNaN(this.shownHeading)) {
         this.shownHeading = heading;
      }
      float d = ((heading - this.shownHeading + 540.0F) % 360.0F) - 180.0F;
      this.shownHeading = (this.shownHeading + d * Math.min(1.0F, this.ui.dt() * 12.0F) + 360.0F) % 360.0F;
      float hd = this.shownHeading;
      // heading readout
      String deg = Math.round(heading) % 360 + "°";
      f.center(deg + " " + Ui.point(heading), w / 2.0F, y, Theme.TEXT, Font.LARGE);
      y += 26.0F;
      float cx = w / 2.0F, cy = y + 64.0F, r = 62.0F;
      // lubber line
      f.triangle(cx, cy - r - 7.0F, cx + 4.0F, cy - r - 13.0F, cx - 4.0F, cy - r - 13.0F, Theme.BLAZE);
      f.circle(cx, cy, r + 2.0F, 0xFF1B201D);
      f.circle(cx, cy, r, 0xFF121614);
      // scent cone (downwind), relative to heading
      PhoneModel.Weather wx = m.weather;
      double down = Math.toRadians(wx.windFrom + 180.0F - hd);
      if (wx.windKmh >= 1.0F) {
         for (int k = 0; k < 8; k++) {
            double a0 = down - 0.36 + k * 0.09, a1 = a0 + 0.09;
            f.triangle(cx, cy, cx + (float)Math.sin(a0) * (r - 4.0F), cy - (float)Math.cos(a0) * (r - 4.0F), cx + (float)Math.sin(a1) * (r - 4.0F),
               cy - (float)Math.cos(a1) * (r - 4.0F), 0x28FF7A1F);
         }
      }
      // ticks and letters
      for (int i = 0; i < 72; i++) {
         double a = Math.toRadians(i * 5.0 - hd);
         boolean big = i % 6 == 0;
         float r0 = r - (big ? 8.0F : (i % 2 == 0 ? 5.0F : 3.5F));
         f.line(cx + (float)Math.sin(a) * r0, cy - (float)Math.cos(a) * r0, cx + (float)Math.sin(a) * (r - 1.0F), cy - (float)Math.cos(a) * (r - 1.0F),
            big ? 1.2F : 0.6F, big ? Theme.TEXT : 0x80FFFFFF);
      }
      String[] card = CARD;
      for (int i = 0; i < 4; i++) {
         double a = Math.toRadians(i * 90.0 - hd);
         float lx = cx + (float)Math.sin(a) * (r - 17.0F), ly = cy - (float)Math.cos(a) * (r - 17.0F);
         f.center(card[i], lx, ly - 5.0F, i == 0 ? Theme.RED : Theme.TEXT, Font.TITLE);
      }
      for (int i = 0; i < 12; i++) {
         if (i % 3 == 0) {
            continue;
         }
         double a = Math.toRadians(i * 30.0 - hd);
         float lx = cx + (float)Math.sin(a) * (r - 15.0F), ly = cy - (float)Math.cos(a) * (r - 15.0F);
         f.center(Integer.toString(i * 30), lx, ly - 3.0F, Theme.TEXT3, Font.SMALL);
      }
      // the sun's bearing (east at dawn, south at noon, west at dusk)
      int minute = Ui.minute(m.dayTime);
      if (minute > wx.sunrise && minute < wx.sunset) {
         float t = (minute - wx.sunrise) / (float)Math.max(1, wx.sunset - wx.sunrise);
         double sb = Math.toRadians(90.0 + t * 180.0 - hd);
         G.SUN.draw(f, cx + (float)Math.sin(sb) * (r + 9.0F), cy - (float)Math.cos(sb) * (r + 9.0F), 9.0F, Theme.YELLOW);
      }
      // navigation target
      PhoneModel.Place target = null;
      for (PhoneModel.Place p : m.map.places) {
         if (p.id().equals(m.map.target)) {
            target = p;
         }
      }
      if (target != null) {
         float b = Ui.bearing(m.x, m.z, target.x() + 0.5, target.z() + 0.5);
         double a = Math.toRadians(b - hd);
         float tx = cx + (float)Math.sin(a) * (r - 28.0F), ty = cy - (float)Math.cos(a) * (r - 28.0F);
         f.line(cx, cy, tx, ty, 1.0F, 0x80FF7A1F);
         f.circle(tx, ty, 5.0F, Theme.BLAZE);
         G.PIN.draw(f, tx, ty, 7.0F, 0xFF1C1209);
      }
      // the wind arrow over the centre
      Wx.windArrow(f, cx, cy, 20.0F, wx.windFrom - hd, 0xE0FFFFFF);
      f.circle(cx, cy, 2.5F, Theme.TEXT);
      y = cy + r + 14.0F;
      // wind relative to where you face
      float rel = ((wx.windFrom - heading + 540.0F) % 360.0F) - 180.0F;
      String verdict;
      int vc;
      if (wx.windKmh < 1.5F) {
         verdict = "Calm: scent pools around you";
         vc = Theme.TEXT2;
      } else if (Math.abs(rel) <= 45.0F) {
         verdict = "Wind in your face: good";
         vc = Theme.MOSS;
      } else if (Math.abs(rel) >= 135.0F) {
         verdict = "Wind at your back: they will smell you";
         vc = Theme.RED;
      } else {
         verdict = "Crosswind from the " + (rel < 0 ? "left" : "right");
         vc = Theme.YELLOW;
      }
      f.round(8.0F, y, w - 16.0F, 22.0F, 11.0F, Theme.withAlpha(Theme.shade(vc, 0.3F), 255));
      G.WIND.draw(f, 20.0F, y + 11.0F, 10.0F, vc);
      f.text(this.txt.fit(f, verdict, w - 44.0F, Font.STRONG), 30.0F, y + 7.0F, vc, Font.STRONG);
      y += 30.0F;
      // position
      Ui.card(f, 8.0F, y, w - 16.0F, 52.0F);
      float colW = (w - 16.0F) / 3.0F;
      String[] k = POS_LABELS;
      String[] v = this.position(m);
      for (int i = 0; i < 3; i++) {
         float x = 8.0F + colW * i + colW / 2.0F;
         f.center(k[i], x, y + 7.0F, Theme.TEXT3, Font.SMALL);
         f.center(v[i], x, y + 18.0F, Theme.TEXT, Font.MEDIUM);
      }
      String place = m.biome.isEmpty() ? "" : m.biome;
      if (target != null) {
         double dist = Math.hypot(target.x() + 0.5 - m.x, target.z() + 0.5 - m.z);
         place = "To " + target.name() + ": " + Ui.distance(dist) + " " + Ui.point(Ui.bearing(m.x, m.z, target.x() + 0.5, target.z() + 0.5));
      }
      f.center(this.txt.fit(f, place, w - 30.0F, Font.SMALL), w / 2.0F, y + 37.0F, target != null ? Theme.BLAZE : Theme.TEXT2, Font.SMALL);
   }
}
