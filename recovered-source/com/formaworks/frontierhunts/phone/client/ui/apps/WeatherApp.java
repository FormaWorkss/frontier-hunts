package com.formaworks.frontierhunts.phone.client.ui.apps;

import com.formaworks.frontierhunts.phone.client.ui.App;
import com.formaworks.frontierhunts.phone.client.ui.Canvas.Font;
import com.formaworks.frontierhunts.phone.client.ui.Frame;
import com.formaworks.frontierhunts.phone.client.ui.G;
import com.formaworks.frontierhunts.phone.client.ui.PhoneActions;
import com.formaworks.frontierhunts.phone.client.ui.PhoneModel;
import com.formaworks.frontierhunts.phone.client.ui.Theme;
import com.formaworks.frontierhunts.phone.client.ui.Ui;
import com.formaworks.frontierhunts.phone.client.ui.Wallpaper;
import com.formaworks.frontierhunts.phone.client.ui.Wx;

/**
 * [phone] Weather: what it is doing here now (from the mod's own regional weather and seasons), the next hours and
 * days as the server forecasts them (vanilla's rain clock plus the storms drifting this way), the wind as a hunter
 * reads it (where your scent goes), storm warnings, sun and moon, and how likely deer are to be moving.
 */
public final class WeatherApp extends App {
   private static final String[] POINT_NAMES = {"north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest"};
   private int refreshIn;

   public WeatherApp() {
      super("weather", "Weather", 1);
   }

   @Override
   public void opened() {
      this.act.refresh(PhoneActions.R_WEATHER);
      this.refreshIn = 200;
   }

   @Override
   public void tick() {
      if (--this.refreshIn <= 0) {
         this.act.refresh(PhoneActions.R_WEATHER);
         this.refreshIn = 400;
      }
   }

   @Override
   protected void drawPage(Frame f, int page, float w, float h) {
      PhoneModel.Weather wx = this.m.weather;
      int minute = Ui.minute(this.m.dayTime);
      int top = Wallpaper.skyTop(minute), low = Wallpaper.skyLow(minute);
      boolean grey = wx.icon.equals("cloudy") || wx.icon.equals("rain") || wx.icon.equals("snow") || wx.icon.equals("blizzard") || wx.icon.equals("fog")
         || wx.icon.equals("thunder") || wx.icon.equals("drizzle") || wx.icon.equals("sleet");
      if (grey) {
         top = Theme.mix(top, 0xFF3A424A, 0.6F);
         low = Theme.mix(low, 0xFF6A747C, 0.6F);
      }
      f.gradient(0.0F, 0.0F, w, h * 0.7F, Theme.shade(top, 0.8F), Theme.shade(low, 0.55F));
      f.fill(0.0F, h * 0.7F - 0.5F, w, h * 0.3F + 1.0F, Theme.shade(low, 0.55F));
      f.fill(0.0F, 0.0F, w, h, 0x38000000);
      float y0 = this.beginScroll(f, Theme.STATUS_H, w, h);
      float y = y0 + 8.0F;
      // ------------------------------------------------------------------------------------- now
      f.center(this.m.season + " · " + this.biomeLine(), w / 2.0F, y, 0xD0FFFFFF, Font.SMALL);
      y += 11.0F;
      String t = Ui.temp(this.m, wx.tempC);
      f.center(t, w / 2.0F + 4.0F, y, 0xFFFFFFFF, Font.HUGE);
      y += 45.0F;
      f.center(wx.condition, w / 2.0F, y, 0xFFFFFFFF, Font.STRONG);
      y += 12.0F;
      float hi = wx.days.isEmpty() ? wx.tempC + 3.0F : wx.days.get(0).hi();
      float lo = wx.days.isEmpty() ? wx.tempC - 4.0F : wx.days.get(0).lo();
      f.center("H " + Ui.temp(this.m, hi) + "   L " + Ui.temp(this.m, lo) + "   Feels " + Ui.temp(this.m, wx.feelsC), w / 2.0F, y, 0xD0FFFFFF, Font.SMALL);
      y += 16.0F;
      if (!wx.loaded) {
         f.center(this.m.signal > 0 ? "Getting the forecast…" : "No signal: forecast unavailable", w / 2.0F, y, 0xB0FFFFFF, Font.SMALL);
         y += 14.0F;
      }
      // ------------------------------------------------------------------------------------- warnings
      for (PhoneModel.Alert a : wx.alerts) {
         String when = a.etaMinutes() > 0 ? "Arrives in " + eta(a.etaMinutes()) : "Here now";
         float ah = 30.0F + this.txt.paraHeight(f, a.body(), w - 46.0F, Font.SMALL, 1.0F) + 6.0F;
         int col = a.strength() >= 0.6F ? Theme.RED : Theme.BLAZE;
         f.round(8.0F, y, w - 16.0F, ah, 9.0F, Theme.withAlpha(Theme.shade(col, 0.35F), 225));
         f.round(8.0F, y, 3.0F, ah, 1.5F, col);
         G.WARN.draw(f, 21.0F, y + 10.0F, 10.0F, col);
         f.text(this.txt.fit(f, a.title(), w - 46.0F, Font.STRONG), 30.0F, y + 5.0F, Theme.TEXT, Font.STRONG);
         f.text(when, 30.0F, y + 17.0F, col, Font.SMALL);
         this.txt.para(f, a.body(), 30.0F, y + 28.0F, w - 46.0F, Theme.TEXT2, Font.SMALL, 1.0F);
         y += ah + 6.0F;
      }
      // ------------------------------------------------------------------------------------- hours
      y = this.hours(f, wx, w, y);
      // ------------------------------------------------------------------------------------- wind
      y = this.wind(f, wx, w, y);
      // ------------------------------------------------------------------------------------- days
      y = this.days(f, wx, w, y);
      // ------------------------------------------------------------------------------------- sun and moon
      y = this.sun(f, wx, w, y, minute);
      // ------------------------------------------------------------------------------------- hunting
      y = this.hunting(f, wx, w, y);
      if (wx.loaded && wx.updated > 0L) {
         long mins = Math.max(0L, (this.m.gameTime - wx.updated) / 20L / 60L);
         f.center(mins < 1 ? "Updated just now" : "Updated " + mins + " min ago", w / 2.0F, y + 2.0F, 0x90FFFFFF, Font.SMALL);
         y += 12.0F;
      }
      this.endScroll(f, y0, y + 10.0F, w, h);
   }

   private String biomeLine() {
      return Math.round(this.m.y) + " m above sea";
   }

   private static String eta(int minutes) {
      return minutes >= 60 ? minutes / 60 + " h " + minutes % 60 + " min" : minutes + " min";
   }

   private float card(Frame f, float y, float w, float h, G g, String title) {
      f.round(8.0F, y, w - 16.0F, h, 10.0F, 0x52101814);
      g.draw(f, 18.0F, y + 9.0F, 8.0F, 0xA0FFFFFF);
      f.text(title, 25.0F, y + 6.0F, 0xA0FFFFFF, Font.SMALL);
      f.fill(15.0F, y + 15.0F, w - 30.0F, 0.5F, 0x24FFFFFF);
      return y + 19.0F;
   }

   private float hours(Frame f, PhoneModel.Weather wx, float w, float y) {
      float ch = 84.0F;
      float in = this.card(f, y, w, ch, G.TIMER, "NEXT HOURS");
      int n = 7;
      float colW = (w - 28.0F) / n;
      float min = 999.0F, max = -999.0F;
      for (int i = 0; i < n; i++) {
         min = Math.min(min, wx.hourTemp[i]);
         max = Math.max(max, wx.hourTemp[i]);
      }
      float span = Math.max(3.0F, max - min);
      float px = 0.0F, py = 0.0F;
      for (int i = 0; i < n; i++) {
         float cx = 14.0F + colW * i + colW / 2.0F;
         int hour = (wx.hourStart + i) % 24;
         String label = i == 0 ? "Now" : (this.m.settings.clock24 ? (hour < 10 ? "0" : "") + hour : (hour % 12 == 0 ? 12 : hour % 12) + (hour < 12 ? "a" : "p"));
         f.center(label, cx, in + 1.0F, i == 0 ? Theme.TEXT : 0xB0FFFFFF, Font.SMALL);
         Wx.icon(f, Wx.forHour(wx.hourIcon[i], hour), cx, in + 17.0F, 15.0F);
         f.center(Ui.temp(this.m, wx.hourTemp[i]), cx + 1.0F, in + 27.0F, Theme.TEXT, Font.SMALL);
         float ty = in + 47.0F - (wx.hourTemp[i] - min) / span * 8.0F;
         if (i > 0) {
            f.line(px, py, cx, ty, 1.2F, 0x70FFFFFF);
         }
         px = cx;
         py = ty;
         if (wx.hourRain[i] >= 20) {
            f.center(wx.hourRain[i] + "%", cx, in + 54.0F, 0xFF8EC8F4, Font.SMALL);
         }
      }
      for (int i = 0; i < n; i++) {
         float cx = 14.0F + colW * i + colW / 2.0F;
         float ty = in + 47.0F - (wx.hourTemp[i] - min) / span * 8.0F;
         f.circle(cx, ty, 1.8F, tempColor(wx.hourTemp[i]));
      }
      return y + ch + 6.0F;
   }

   private float wind(Frame f, PhoneModel.Weather wx, float w, float y) {
      String scent = wx.windKmh < 2.0F ? "Calm air: your scent pools and drifts. Keep still and low." : "Your scent drifts " + Ui.point(wx.windFrom + 180.0F)
         + ". Approach from the " + Ui.point(wx.windFrom) + " with the wind in your face.";
      float ch = Math.max(84.0F, 19.0F + 32.0F + this.txt.paraHeight(f, scent, w - 76.0F - 14.0F, Font.SMALL, 1.0F) + 8.0F);
      float in = this.card(f, y, w, ch, G.WIND, "WIND AND SCENT");
      float cx = 42.0F, cy = in + 31.0F, r = 26.0F;
      f.circle(cx, cy, r, 0x30FFFFFF);
      f.ring(cx, cy, r, 0.8F, 0x50FFFFFF);
      for (int i = 0; i < 36; i++) {
         double a = Math.toRadians(i * 10.0);
         float r0 = i % 9 == 0 ? r - 5.0F : r - 2.5F;
         f.line(cx + (float)Math.sin(a) * r0, cy - (float)Math.cos(a) * r0, cx + (float)Math.sin(a) * (r - 0.8F), cy - (float)Math.cos(a) * (r - 0.8F),
            0.6F, 0x80FFFFFF);
      }
      f.center("N", cx, cy - r + 6.0F, Theme.RED, Font.SMALL);
      // scent cone downwind (where an animal could smell you)
      double to = Math.toRadians(wx.windFrom + 180.0);
      for (int k = 0; k < 6; k++) {
         double a0 = to - 0.32 + k * 0.106, a1 = a0 + 0.106;
         f.triangle(cx, cy, cx + (float)Math.sin(a0) * (r - 3.0F), cy - (float)Math.cos(a0) * (r - 3.0F), cx + (float)Math.sin(a1) * (r - 3.0F),
            cy - (float)Math.cos(a1) * (r - 3.0F), 0x30FF7A1F);
      }
      Wx.windArrow(f, cx, cy, r * 0.72F, wx.windFrom, Theme.TEXT);
      float tx = 76.0F;
      f.text(Ui.wind(this.m, wx.windKmh), tx, in + 4.0F, Theme.TEXT, Font.MEDIUM);
      f.text("from the " + name(wx.windFrom), tx, in + 19.0F, 0xC0FFFFFF, Font.SMALL);
      this.txt.para(f, scent, tx, in + 32.0F, w - tx - 14.0F, 0xB0FFFFFF, Font.SMALL, 1.0F);
      return y + ch + 6.0F;
   }

   private static String name(float deg) {
      return POINT_NAMES[Math.floorMod(Math.round(deg / 45.0F), 8)];
   }

   private float days(Frame f, PhoneModel.Weather wx, float w, float y) {
      int n = Math.min(5, wx.days.size());
      if (n == 0) {
         return y;
      }
      float ch = 22.0F + n * 18.0F;
      float in = this.card(f, y, w, ch, G.CALENDAR, n + "-DAY FORECAST");
      float min = 999.0F, max = -999.0F;
      for (int i = 0; i < n; i++) {
         min = Math.min(min, wx.days.get(i).lo());
         max = Math.max(max, wx.days.get(i).hi());
      }
      float span = Math.max(4.0F, max - min);
      for (int i = 0; i < n; i++) {
         PhoneModel.Day d = wx.days.get(i);
         float ry = in + i * 18.0F;
         f.text(i == 0 ? "Today" : d.name(), 15.0F, ry + 4.0F, Theme.TEXT, Font.STRONG);
         Wx.icon(f, d.icon(), 56.0F, ry + 8.0F, 14.0F);
         if (d.rain() >= 20) {
            f.text(d.rain() + "%", 65.0F, ry + 4.5F, 0xFF8EC8F4, Font.SMALL);
         }
         f.right(Ui.temp(this.m, d.lo()), 92.0F, ry + 4.5F, 0xA0FFFFFF, Font.SMALL);
         float bx = 96.0F, bw = w - bx - 34.0F;
         f.round(bx, ry + 6.5F, bw, 3.0F, 1.5F, 0x30000000);
         float x0 = bx + (d.lo() - min) / span * bw, x1 = bx + (d.hi() - min) / span * bw;
         f.round(x0, ry + 6.5F, Math.max(3.0F, x1 - x0), 3.0F, 1.5F, tempColor((d.lo() + d.hi()) / 2.0F));
         f.text(Ui.temp(this.m, d.hi()), w - 30.0F, ry + 4.5F, Theme.TEXT, Font.SMALL);
      }
      return y + ch + 6.0F;
   }

   static int tempColor(float c) {
      if (c < -5.0F) {
         return 0xFF7FB6FF;
      }
      if (c < 5.0F) {
         return Theme.mix(0xFF7FB6FF, 0xFF8CD9B0, (c + 5.0F) / 10.0F);
      }
      if (c < 18.0F) {
         return Theme.mix(0xFF8CD9B0, 0xFFF0C64E, (c - 5.0F) / 13.0F);
      }
      return Theme.mix(0xFFF0C64E, 0xFFFF7A3A, Math.min(1.0F, (c - 18.0F) / 12.0F));
   }

   private float sun(Frame f, PhoneModel.Weather wx, float w, float y, int minute) {
      float ch = 70.0F;
      float in = this.card(f, y, w, ch, G.SUNRISE, "SUN AND MOON");
      float x0 = 20.0F, x1 = w - 56.0F;
      float base = in + 34.0F;
      // daylight arc
      int steps = 30;
      float px = 0, py = 0;
      for (int i = 0; i <= steps; i++) {
         float t = (float)i / steps;
         float x = x0 + (x1 - x0) * t;
         float yy = base - (float)Math.sin(t * Math.PI) * 24.0F;
         if (i > 0) {
            f.line(px, py, x, yy, 1.0F, 0x60FFFFFF);
         }
         px = x;
         py = yy;
      }
      f.fill(x0 - 4.0F, base, x1 - x0 + 8.0F, 0.6F, 0x60FFFFFF);
      float t = (float)(minute - wx.sunrise) / Math.max(1, wx.sunset - wx.sunrise);
      if (t >= 0.0F && t <= 1.0F) {
         float sx = x0 + (x1 - x0) * t;
         float sy = base - (float)Math.sin(t * Math.PI) * 24.0F;
         f.circle(sx, sy, 6.0F, 0x40FFC04A);
         f.circle(sx, sy, 3.6F, 0xFFFFC04A);
      }
      G.SUNRISE.draw(f, x0, base + 9.0F, 9.0F, 0xC0FFFFFF);
      f.text(Ui.clockFull(this.m, wx.sunrise), x0 + 7.0F, base + 5.0F, Theme.TEXT, Font.SMALL);
      G.SUNSET.draw(f, x1 - 38.0F, base + 9.0F, 9.0F, 0xC0FFFFFF);
      f.right(Ui.clockFull(this.m, wx.sunset), x1 + 4.0F, base + 5.0F, Theme.TEXT, Font.SMALL);
      // moon
      float mx = w - 30.0F;
      G.moon(this.m.moonPhase).draw(f, mx, in + 18.0F, 22.0F, 0xFFEDEFF5);
      f.center(MOONS[Math.floorMod(this.m.moonPhase, 8)], mx, in + 33.0F, 0xC0FFFFFF, Font.SMALL);
      return y + ch + 6.0F;
   }

   static final String[] MOONS = {"Full", "Waning gib.", "Last qtr", "Waning cr.", "New", "Waxing cr.", "First qtr", "Waxing gib."};

   private float hunting(Frame f, PhoneModel.Weather wx, float w, float y) {
      float rh = 0.0F;
      for (String r : wx.huntReasons) {
         rh += this.txt.paraHeight(f, r, w - 44.0F, Font.SMALL, 1.0F) + 4.0F;
      }
      float ch = Math.max(66.0F, 52.0F + rh);
      float in = this.card(f, y, w, ch, G.TRACK, "HUNTING OUTLOOK");
      int sc = wx.huntScore;
      int col = sc >= 70 ? Theme.MOSS : (sc >= 45 ? Theme.YELLOW : Theme.TEXT3);
      Ui.ring(f, 32.0F, in + 16.0F, 14.0F, 3.0F, sc / 100.0F, 0x30FFFFFF, col);
      f.center(Integer.toString(sc), 32.0F, in + 12.0F, Theme.TEXT, Font.STRONG);
      String head = sc >= 70 ? "Deer on the move" : (sc >= 45 ? "Fair movement" : "Slow going");
      f.text(head, 54.0F, in + 5.0F, Theme.TEXT, Font.STRONG);
      f.text(this.txt.fit(f, wx.huntLine.isEmpty() ? this.m.rutTitle : wx.huntLine, w - 70.0F, Font.SMALL), 54.0F, in + 17.0F, 0xB0FFFFFF, Font.SMALL);
      float ry = in + 36.0F;
      for (String r : wx.huntReasons) {
         f.circle(17.0F, ry + 4.0F, 1.5F, col);
         ry += this.txt.para(f, r, 24.0F, ry, w - 44.0F, 0xC8FFFFFF, Font.SMALL, 1.0F) + 4.0F;
      }
      return y + ch + 6.0F;
   }
}
