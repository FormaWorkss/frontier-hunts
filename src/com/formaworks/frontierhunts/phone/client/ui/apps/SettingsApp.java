package com.formaworks.frontierhunts.phone.client.ui.apps;

import com.formaworks.frontierhunts.phone.client.ui.App;
import com.formaworks.frontierhunts.phone.client.ui.Canvas.Font;
import com.formaworks.frontierhunts.phone.client.ui.Frame;
import com.formaworks.frontierhunts.phone.client.ui.G;
import com.formaworks.frontierhunts.phone.client.ui.PhoneActions;
import com.formaworks.frontierhunts.phone.client.ui.PhoneModel;
import com.formaworks.frontierhunts.phone.client.ui.Theme;
import com.formaworks.frontierhunts.phone.client.ui.Ui;

/** [phone] Settings: units, clock, red night light, sounds, notifications, on-screen guidance; about the phone. */
public final class SettingsApp extends App {
   private static final int Z_UNITS = 100;
   private static final int Z_TOGGLE = 101;
   private static final int Z_VOLUME = 102;
   private static final String[] UNITS = {"Metric · °C", "Imperial · °F"};
   private static final String[] VOLUME = {"Off", "Low", "Mid", "High"};

   public SettingsApp() {
      super("settings", "Settings", 9);
   }

   @Override
   protected void drawPage(Frame f, int page, float w, float h) {
      PhoneModel.Settings s = this.m.settings;
      f.fill(0.0F, 0.0F, w, h, Theme.BG);
      float y = Ui.header(this.ui, f, "Settings", "", false, w);
      float y0 = this.beginScroll(f, y, w, h);
      y = y0;
      Ui.section(f, "UNITS", 12.0F, y, w);
      y += 11.0F;
      Ui.segmented(this.ui, f, Z_UNITS, 9.0F, y, w - 18.0F, 18.0F, UNITS, s.celsius ? 0 : 1);
      y += 26.0F;
      Ui.section(f, "DISPLAY", 12.0F, y, w);
      y += 11.0F;
      y = this.toggle(f, w, y, 0, G.TIMER, Theme.SKY, "24-hour clock", "", s.clock24);
      y = this.toggle(f, w, y, 1, G.NIGHT, Theme.RED, "Red night light", "Keeps your eyes used to the dark", s.night);
      y = this.toggle(f, w, y, 2, G.ROUTE, Theme.BLAZE, "Guidance on screen", "Bearing and distance while Maps guides you", s.hudNav);
      y += 6.0F;
      Ui.section(f, "SOUND", 12.0F, y, w);
      y += 11.0F;
      Ui.segmented(this.ui, f, Z_VOLUME, 9.0F, y, w - 18.0F, 18.0F, VOLUME, Math.min(3, s.volume / 33));
      y += 24.0F;
      y = this.toggle(f, w, y, 3, s.silent ? G.BELL_OFF : G.BELL, Theme.VIOLET, "Silent", "No sounds or banners for notifications", s.silent);
      y += 6.0F;
      Ui.section(f, "NOTIFY ME ABOUT", 12.0F, y, w);
      y += 11.0F;
      y = this.toggle(f, w, y, 4, G.CAMERA, Theme.VIOLET, "New trail camera photos", "", s.notifyCams);
      y = this.toggle(f, w, y, 5, G.CLIPBOARD, Theme.BLAZE, "Contracts ready", "", s.notifyContracts);
      y = this.toggle(f, w, y, 6, G.STORM, Theme.YELLOW, "Weather warnings", "", s.notifyWeather);
      y = this.toggle(f, w, y, 7, G.DIE, Theme.MOSS, "Game invites and moves", "", s.notifyGames);
      y += 8.0F;
      Ui.card(f, 9.0F, y, w - 18.0F, 56.0F);
      f.text("Field Phone", 16.0F, y + 7.0F, Theme.TEXT, Font.STRONG);
      f.text("FieldOS 1.3 · Frontier Hunts", 16.0F, y + 19.0F, Theme.TEXT3, Font.SMALL);
      f.text("Open it with " + this.m.keyName + " while it is in your inventory", 16.0F, y + 30.0F, Theme.TEXT3, Font.SMALL);
      f.text("(change the key in Controls).", 16.0F, y + 39.0F, Theme.TEXT3, Font.SMALL);
      y += 64.0F;
      this.endScroll(f, y0, y, w, h);
   }

   private float toggle(Frame f, float w, float y, int i, G g, int color, String title, String sub, boolean on) {
      float rh = Ui.row(this.ui, f, 0, 0L, 12.0F, y, w - 54.0F, g, color, title, sub, "", 0, false);
      Ui.toggle(this.ui, f, Z_TOGGLE, i, w - 40.0F, y + (rh - 15.0F) / 2.0F, on);
      return y + rh + 3.0F;
   }

   @Override
   public void tap(int id, long data) {
      PhoneModel.Settings s = this.m.settings;
      switch (id) {
         case Z_UNITS -> s.celsius = data == 0L;
         case Z_VOLUME -> s.volume = (int)Math.min(100L, data * 34L);
         case Z_TOGGLE -> {
            switch ((int)data) {
               case 0 -> s.clock24 = !s.clock24;
               case 1 -> s.night = !s.night;
               case 2 -> s.hudNav = !s.hudNav;
               case 3 -> s.silent = !s.silent;
               case 4 -> s.notifyCams = !s.notifyCams;
               case 5 -> s.notifyContracts = !s.notifyContracts;
               case 6 -> s.notifyWeather = !s.notifyWeather;
               case 7 -> s.notifyGames = !s.notifyGames;
               default -> {
               }
            }
         }
         default -> {
            return;
         }
      }
      this.act.settingsChanged();
      this.act.sound(PhoneActions.Sfx.TOGGLE);
   }
}
