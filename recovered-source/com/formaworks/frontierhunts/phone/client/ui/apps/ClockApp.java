package com.formaworks.frontierhunts.phone.client.ui.apps;

import com.formaworks.frontierhunts.phone.client.ui.App;
import com.formaworks.frontierhunts.phone.client.ui.Canvas.Font;
import com.formaworks.frontierhunts.phone.client.ui.Frame;
import com.formaworks.frontierhunts.phone.client.ui.G;
import com.formaworks.frontierhunts.phone.client.ui.PhoneActions;
import com.formaworks.frontierhunts.phone.client.ui.PhoneModel;
import com.formaworks.frontierhunts.phone.client.ui.Theme;
import com.formaworks.frontierhunts.phone.client.ui.Ui;

/**
 * [phone] Clock: the reserve's time on a real dial (it follows the game clock smoothly), sunrise and sunset, the
 * moon, the best hunting hours and solunar periods; an alarm that rings in game (even with the phone in your pocket);
 * and a tracking timer (wait before you follow a hit animal).
 */
public final class ClockApp extends App {
   private static final String[] PRESET_LABELS = {"1 h before sunrise", "30 min before sunrise", "1 h before sunset"};
   private static final int[] TIMER_MINS = {5, 15, 30, 60};
   private static final String[] TIMER_TITLES = {"5 minutes", "15 minutes", "30 minutes", "60 minutes"};
   private static final String[] TIMER_WHAT = {"Let a deer settle after you bump it", "Wait before glassing a new ridge", "Wait before tracking a heart-lung hit",
      "Wait before tracking a gut or liver hit"};
   private final int[] presetTimes = new int[3];
   private static final int Z_TAB = 100;
   private static final int Z_STEP = 101;
   private static final int Z_ALARM_TOGGLE = 102;
   private static final int Z_PRESET = 103;
   private static final int Z_TIMER = 104;
   private static final int Z_TIMER_PRESET = 105;
   private static final String[] TABS = {"Clock", "Alarm", "Timer"};
   private int tab;
   private int alarmEdit = 5 * 60 + 30;

   public ClockApp() {
      super("clock", "Clock", 0);
   }

   @Override
   public void opened() {
      if (this.m.alarmMinute >= 0) {
         this.alarmEdit = this.m.alarmMinute;
      } else {
         // a sensible first alarm: half an hour before sunrise
         this.alarmEdit = Math.floorMod(this.m.weather.sunrise - 30, 1440) / 5 * 5;
      }
   }

   @Override
   protected void drawPage(Frame f, int page, float w, float h) {
      float y = Ui.header(this.ui, f, "Clock", "", false, w) - 6.0F;
      Ui.segmented(this.ui, f, Z_TAB, 9.0F, y, w - 18.0F, 16.0F, TABS, this.tab);
      y += 24.0F;
      float y0 = this.beginScroll(f, y, w, h);
      float end = switch (this.tab) {
         case 1 -> this.alarm(f, w, y0);
         case 2 -> this.timer(f, w, y0);
         default -> this.clock(f, w, y0);
      };
      this.endScroll(f, y0, end, w, h);
   }

   // ------------------------------------------------------------------------------------------------ clock

   private float clock(Frame f, float w, float y) {
      PhoneModel m = this.m;
      long dt = Math.floorMod(m.dayTime, 24000L);
      float minuteF = (dt * 1440.0F / 24000.0F + 360.0F) % 1440.0F;
      float cx = w / 2.0F, cy = y + 56.0F, r = 52.0F;
      // dial
      f.circle(cx, cy + 1.5F, r + 1.0F, 0x50000000);
      f.circle(cx, cy, r, 0xFFF3F0E6);
      f.circle(cx, cy, r - 3.0F, 0xFFFBF9F2);
      for (int i = 0; i < 60; i++) {
         double a = i / 60.0 * Math.PI * 2.0;
         boolean hour = i % 5 == 0;
         float r0 = r - (hour ? 9.0F : 6.0F);
         float r1 = r - 4.0F;
         f.line(cx + (float)Math.sin(a) * r0, cy - (float)Math.cos(a) * r0, cx + (float)Math.sin(a) * r1, cy - (float)Math.cos(a) * r1,
            hour ? 1.6F : 0.6F, hour ? 0xFF262A27 : 0xFF8A8F88);
      }
      for (int i = 1; i <= 12; i++) {
         double a = i / 12.0 * Math.PI * 2.0;
         String n = Integer.toString(i);
         f.center(n, cx + (float)Math.sin(a) * (r - 16.0F), cy - (float)Math.cos(a) * (r - 16.0F) - 4.0F, 0xFF262A27, Font.SMALL);
      }
      // daylight band on the dial: from sunrise to sunset (12-hour face shows the day half)
      // hands
      float hours = minuteF / 60.0F;
      double ha = (hours % 12.0F) / 12.0 * Math.PI * 2.0;
      double ma = (minuteF % 60.0F) / 60.0 * Math.PI * 2.0;
      f.line(cx, cy, cx + (float)Math.sin(ha) * r * 0.5F, cy - (float)Math.cos(ha) * r * 0.5F, 3.2F, 0xFF1D211E);
      f.line(cx, cy, cx + (float)Math.sin(ma) * r * 0.76F, cy - (float)Math.cos(ma) * r * 0.76F, 2.0F, 0xFF1D211E);
      // a real-time second hand (the phone's own clock ticking)
      double sa = (this.act.millis() % 60000L) / 60000.0 * Math.PI * 2.0;
      f.line(cx - (float)Math.sin(sa) * 8.0F, cy + (float)Math.cos(sa) * 8.0F, cx + (float)Math.sin(sa) * r * 0.84F, cy - (float)Math.cos(sa) * r * 0.84F,
         0.8F, Theme.BLAZE);
      f.circle(cx, cy, 3.0F, Theme.BLAZE);
      f.circle(cx, cy, 1.2F, 0xFFFBF9F2);
      // small sun / moon window
      boolean day = minuteF >= m.weather.sunrise && minuteF <= m.weather.sunset;
      if (day) {
         G.SUN.draw(f, cx, cy + 22.0F, 10.0F, 0xFFE8A030);
      } else {
         G.moon(m.moonPhase).draw(f, cx, cy + 22.0F, 10.0F, 0xFF6A7088);
      }
      y = cy + r + 10.0F;
      int minute = (int)minuteF;
      String t = Ui.clock(m, minute);
      float tw = f.width(t, Font.DISPLAY);
      f.text(t, cx - tw / 2.0F - 6.0F, y, Theme.TEXT, Font.DISPLAY);
      f.text(Ui.ampm(m, minute), cx + tw / 2.0F - 3.0F, y + 13.0F, Theme.TEXT2, Font.STRONG);
      y += 30.0F;
      f.center(MONTHS[Math.floorMod(m.month, 12)] + " " + m.day + " · Day " + (m.gameTime / 24000L + 1L) + " in the reserve", cx, y, Theme.TEXT3, Font.SMALL);
      y += 14.0F;
      // hunting hours
      Ui.card(f, 8.0F, y, w - 16.0F, 76.0F);
      Ui.section(f, "PRIME HUNTING HOURS", 15.0F, y + 6.0F, w);
      int sr = m.weather.sunrise, ss = m.weather.sunset;
      this.span(f, w, y + 18.0F, G.SUNRISE, "Dawn", sr - 30, sr + 120, minute, Theme.YELLOW);
      this.span(f, w, y + 35.0F, G.SUNSET, "Dusk", ss - 120, ss + 30, minute, Theme.BLAZE);
      // legal light: half an hour either side of the sun
      f.text("Legal light " + Ui.clockFull(m, sr - 30) + " – " + Ui.clockFull(m, ss + 30), 15.0F, y + 56.0F, Theme.TEXT3, Font.SMALL);
      y += 82.0F;
      // solunar
      int phase = Math.floorMod(m.moonPhase, 8);
      int stars = phase == 0 || phase == 4 ? 4 : (phase == 2 || phase == 6 ? 2 : 3);
      Ui.card(f, 8.0F, y, w - 16.0F, 76.0F);
      Ui.section(f, "SOLUNAR", 15.0F, y + 6.0F, w);
      for (int i = 0; i < 4; i++) {
         G.STAR.draw(f, w - 46.0F + i * 9.0F, y + 9.5F, 7.5F, i < stars ? Theme.GOLD : 0x40FFFFFF);
      }
      this.span(f, w, y + 18.0F, G.MOON, "Major", 23 * 60, 25 * 60, minute, Theme.VIOLET);
      this.span(f, w, y + 35.0F, G.SUN, "Major", 11 * 60, 13 * 60, minute, Theme.VIOLET);
      f.text("Minor " + Ui.clockFull(m, 5 * 60 + 30) + " and " + Ui.clockFull(m, 17 * 60 + 30) + " (moonset and moonrise)", 15.0F, y + 56.0F,
         Theme.TEXT3, Font.SMALL);
      return y + 82.0F;
   }

   private void span(Frame f, float w, float y, G g, String label, int from, int to, int now, int col) {
      int a = Math.floorMod(from, 1440), b = Math.floorMod(to, 1440);
      boolean in = a <= b ? now >= a && now <= b : now >= a || now <= b;
      g.draw(f, 20.0F, y + 6.0F, 10.0F, in ? col : Theme.TEXT2);
      f.text(label, 30.0F, y + 2.0F, in ? Theme.TEXT : Theme.TEXT2, Font.STRONG);
      f.right(Ui.clockFull(this.m, a) + " – " + Ui.clockFull(this.m, b), w - 15.0F, y + 2.0F, in ? col : Theme.TEXT, Font.STRONG);
      if (in) {
         f.round(14.0F, y - 1.0F, 2.0F, 14.0F, 1.0F, col);
      }
   }

   // ------------------------------------------------------------------------------------------------ alarm

   private float alarm(Frame f, float w, float y) {
      PhoneModel m = this.m;
      boolean on = m.alarmMinute >= 0;
      Ui.card(f, 8.0F, y, w - 16.0F, 120.0F);
      float cx = w / 2.0F;
      int hour = this.alarmEdit / 60, min = this.alarmEdit % 60;
      String hs = m.settings.clock24 ? (hour < 10 ? "0" : "") + hour : Integer.toString(hour % 12 == 0 ? 12 : hour % 12);
      String ms = (min < 10 ? "0" : "") + min;
      // steppers
      float hx = cx - 30.0F, mx = cx + 30.0F, ty = y + 24.0F;
      Ui.iconButton(this.ui, f, Z_STEP, 0, hx, ty, 9.0F, G.PLUS, Theme.SURFACE3, Theme.TEXT, true);
      Ui.iconButton(this.ui, f, Z_STEP, 1, mx, ty, 9.0F, G.PLUS, Theme.SURFACE3, Theme.TEXT, true);
      f.center(hs, hx, ty + 13.0F, on ? Theme.TEXT : Theme.TEXT2, Font.DISPLAY);
      f.center(":", cx, ty + 12.0F, Theme.TEXT3, Font.DISPLAY);
      f.center(ms, mx, ty + 13.0F, on ? Theme.TEXT : Theme.TEXT2, Font.DISPLAY);
      Ui.iconButton(this.ui, f, Z_STEP, 2, hx, ty + 56.0F, 9.0F, G.MINUS, Theme.SURFACE3, Theme.TEXT, true);
      Ui.iconButton(this.ui, f, Z_STEP, 3, mx, ty + 56.0F, 9.0F, G.MINUS, Theme.SURFACE3, Theme.TEXT, true);
      if (!m.settings.clock24) {
         f.center(this.alarmEdit < 720 ? "AM" : "PM", cx, ty + 50.0F, Theme.TEXT2, Font.SMALL);
      }
      f.text("Alarm", 16.0F, y + 100.0F, Theme.TEXT, Font.STRONG);
      Ui.toggle(this.ui, f, Z_ALARM_TOGGLE, 0L, w - 42.0F, y + 97.0F, on);
      y += 128.0F;
      String status = on ? "Rings at " + Ui.clockFull(m, m.alarmMinute) + ", in " + until(Ui.minute(m.dayTime), m.alarmMinute) + " of game time."
         : "Off. Set a time and switch it on.";
      y += this.txt.para(f, status, 12.0F, y, w - 24.0F, on ? Theme.MOSS : Theme.TEXT3, Font.SMALL, 1.0F) + 10.0F;
      Ui.section(f, "QUICK SET", 12.0F, y, w);
      y += 11.0F;
      int sr = m.weather.sunrise, ss = m.weather.sunset;
      String[] labels = PRESET_LABELS;
      int[] times = this.presetTimes;
      times[0] = sr - 60;
      times[1] = sr - 30;
      times[2] = ss - 60;
      for (int i = 0; i < labels.length; i++) {
         y += Ui.row(this.ui, f, Z_PRESET, i, 12.0F, y, w - 24.0F, i < 2 ? G.SUNRISE : G.SUNSET, i < 2 ? Theme.YELLOW : Theme.BLAZE, labels[i], "",
            Ui.clockFull(m, Math.floorMod(times[i], 1440)), Theme.TEXT2, false) + 2.0F;
      }
      y += 6.0F;
      y += this.txt.para(f, "The alarm rings in game while the phone is in your inventory and charged, even when it is put away.", 12.0F, y, w - 24.0F,
         Theme.TEXT3, Font.SMALL, 1.0F);
      return y + 8.0F;
   }

   private static String until(int now, int at) {
      int d = Math.floorMod(at - now, 1440);
      return d >= 60 ? d / 60 + " h " + d % 60 + " min" : d + " min";
   }

   // ------------------------------------------------------------------------------------------------ timer

   private float timer(Frame f, float w, float y) {
      PhoneModel m = this.m;
      long now = this.act.millis();
      long left = m.timerRunning ? Math.max(0L, m.timerEnd - now) : m.timerLeft;
      long total = Math.max(1L, m.timerTotal);
      float cx = w / 2.0F, cy = y + 62.0F;
      Ui.ring(f, cx, cy, 56.0F, 4.0F, left / (float)total, Theme.SURFACE3, m.timerRunning ? Theme.BLAZE : Theme.TEXT2);
      long secs = (left + 999L) / 1000L;
      String t = (secs / 60L < 10 ? "0" : "") + secs / 60L + ":" + (secs % 60L < 10 ? "0" : "") + secs % 60L;
      f.center(t, cx, cy - 15.0F, Theme.TEXT, Font.DISPLAY);
      f.center(m.timerRunning ? "Counting down" : (left > 0 ? "Paused" : "Tracking timer"), cx, cy + 16.0F, Theme.TEXT3, Font.SMALL);
      y = cy + 66.0F;
      float bw = (w - 30.0F) / 2.0F;
      boolean has = m.timerLeft > 0 || m.timerRunning;
      Ui.button(this.ui, f, Z_TIMER, 0, 12.0F, y, bw, 22.0F, has ? "Reset" : "Clear", Ui.TONAL, has);
      Ui.button(this.ui, f, Z_TIMER, 1, 18.0F + bw, y, bw, 22.0F, m.timerRunning ? "Pause" : (has ? "Resume" : "Start 30:00"),
         m.timerRunning ? Ui.TONAL : Ui.GOOD, true);
      y += 32.0F;
      Ui.section(f, "PRESETS", 12.0F, y, w);
      y += 11.0F;
      for (int i = 0; i < TIMER_MINS.length; i++) {
         y += Ui.row(this.ui, f, Z_TIMER_PRESET, i, 12.0F, y, w - 24.0F, G.TIMER, Theme.BLAZE, TIMER_TITLES[i], TIMER_WHAT[i], "", 0, true) + 2.0F;
      }
      return y + 6.0F;
   }

   // ------------------------------------------------------------------------------------------------ input

   @Override
   public void tap(int id, long data) {
      PhoneModel m = this.m;
      switch (id) {
         case Z_TAB -> {
            this.tab = (int)data;
            this.scroller().reset();
            this.act.sound(PhoneActions.Sfx.TAP);
         }
         case Z_STEP -> {
            int d = data == 0 ? 60 : (data == 1 ? 5 : (data == 2 ? -60 : -5));
            this.alarmEdit = Math.floorMod(this.alarmEdit + d, 1440);
            if (m.alarmMinute >= 0) {
               this.act.setAlarm(this.alarmEdit);
            }
            this.act.sound(PhoneActions.Sfx.KEY);
         }
         case Z_ALARM_TOGGLE -> {
            this.act.setAlarm(m.alarmMinute >= 0 ? -1 : this.alarmEdit);
            this.act.sound(PhoneActions.Sfx.TOGGLE);
         }
         case Z_PRESET -> {
            int sr = m.weather.sunrise, ss = m.weather.sunset;
            int[] times = {sr - 60, sr - 30, ss - 60};
            this.alarmEdit = Math.floorMod(times[(int)Math.min(2, data)], 1440);
            this.act.setAlarm(this.alarmEdit);
            this.act.sound(PhoneActions.Sfx.TOGGLE);
         }
         case Z_TIMER -> {
            long now = this.act.millis();
            if (data == 0) {
               m.timerRunning = false;
               m.timerLeft = 0L;
               m.timerTotal = 0L;
            } else if (m.timerRunning) {
               m.timerLeft = Math.max(0L, m.timerEnd - now);
               m.timerRunning = false;
            } else {
               if (m.timerLeft <= 0L) {
                  m.timerTotal = 30L * 60000L;
                  m.timerLeft = m.timerTotal;
               }
               m.timerEnd = now + m.timerLeft;
               m.timerRunning = true;
            }
            this.act.sound(PhoneActions.Sfx.TAP);
         }
         case Z_TIMER_PRESET -> {
            m.timerTotal = TIMER_MINS[(int)Math.max(0, Math.min(3, data))] * 60000L;
            m.timerLeft = m.timerTotal;
            m.timerEnd = this.act.millis() + m.timerLeft;
            m.timerRunning = true;
            this.act.sound(PhoneActions.Sfx.TOGGLE);
         }
         default -> {
         }
      }
   }

   static final String[] MONTHS = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November",
      "December"};
}
