package com.formaworks.frontierhunts.phone.client.ui.apps;

import com.formaworks.frontierhunts.phone.client.ui.App;
import com.formaworks.frontierhunts.phone.client.ui.Canvas.Font;
import com.formaworks.frontierhunts.phone.client.ui.Frame;
import com.formaworks.frontierhunts.phone.client.ui.G;
import com.formaworks.frontierhunts.phone.client.ui.Theme;
import com.formaworks.frontierhunts.phone.client.ui.Ui;

/**
 * [phone] Calls: a library of the real call recordings the mod's calls use (grunt tube, doe bleat, rattling, predator
 * distress, duck hail...) to learn them before you blow the real thing, with when each works and what the rut is doing
 * now. The phone's little speaker plays them for you only: animals do not come to a phone.
 */
public final class CallsApp extends App {
   private static final int Z_PLAY = 100;

   private record Call(String sound, String name, String species, String when, G glyph, int color, float seconds) {
   }

   private static final Call[] CALLS = {
      new Call("grunt_tube", "Grunt tube", "Whitetail · elk · moose", "Contact grunts all fall; tending grunts in the seeking and peak rut.", G.HORN, 0xFFD9A066,
         2.4F),
      new Call("bleat_call", "Doe bleat", "Whitetail · cow elk and moose", "Estrus bleats pull cruising bucks in the seeking phase.", G.HORN, 0xFFE8C08A, 2.0F),
      new Call("antler_rattle", "Rattling antlers", "Whitetail bucks", "Rattle in the pre-rut and seeking phase: bucks come to a fight.", G.ANTLER,
         0xFFF0E6D0, 4.0F),
      new Call("deer_call", "Deer caller", "Whitetail", "An all-round mouth call: soft grunts and bleats.", G.HORN, 0xFFC9A27A, 2.2F),
      new Call("predator_call", "Predator call", "Coyote · wolf · cougar · bear", "Fawn distress. Best after dark; predators swing downwind.", G.WAVES,
         0xFFE5574B, 3.5F),
      new Call("duck_call", "Duck call", "Mallard", "Hail calls to passing ducks; quiet feeding chatter over decoys.", G.DUCK, 0xFF7FB6A0, 2.5F),
      new Call("horse_whistle", "Horse whistle", "Your horse", "Calls your horse to you.", G.SPEAKER, 0xFFA8B4C0, 1.2F)};
   private int playing = -1;
   private long playingSince;

   public CallsApp() {
      super("calls", "Calls", 7);
   }

   @Override
   protected void drawPage(Frame f, int page, float w, float h) {
      f.fill(0.0F, 0.0F, w, h, 0xFF140E0C);
      f.gradient(0.0F, 0.0F, w, 110.0F, 0xFF3A1810, 0xFF140E0C);
      float y = Ui.header(this.ui, f, "Calls", "Learn them before you blow the real thing", false, w);
      // the rut now
      float rh = 22.0F + this.txt.paraHeight(f, this.m.rutNote, w - 36.0F, Font.SMALL, 1.0F);
      f.round(9.0F, y, w - 18.0F, rh, 9.0F, 0x1EFFFFFF);
      G.ANTLER.draw(f, 20.0F, y + 9.0F, 10.0F, Theme.GOLD);
      f.text("Whitetail rut: " + this.m.rutTitle, 29.0F, y + 5.0F, Theme.GOLD, Font.STRONG);
      this.txt.para(f, this.m.rutNote, 18.0F, y + 17.0F, w - 36.0F, Theme.TEXT2, Font.SMALL, 1.0F);
      y += rh + 8.0F;
      float y0 = this.beginScroll(f, y, w, h);
      y = y0;
      long now = this.act.millis();
      for (int i = 0; i < CALLS.length; i++) {
         Call c = CALLS[i];
         float th = this.txt.paraHeight(f, c.when, w - 70.0F, Font.SMALL, 1.0F);
         float ch = 30.0F + th;
         if (f.visible(0.0F, y, w, ch)) {
            boolean on = this.playing == i && now - this.playingSince < (long)(c.seconds * 1000.0F);
            Ui.tapCard(this.ui, f, Z_PLAY, i, 9.0F, y, w - 18.0F, ch, on ? 0xFF3A2218 : 0xFF221815);
            float cx = 26.0F, cy = y + ch / 2.0F;
            f.circle(cx, cy, 11.0F, on ? c.color : Theme.withAlpha(c.color, 60));
            (on ? G.STOP : G.PLAY).draw(f, cx + (on ? 0.0F : 1.0F), cy, 10.0F, on ? 0xFF1A100C : c.color);
            f.text(c.name, 44.0F, y + 6.0F, Theme.TEXT, Font.STRONG);
            f.text(this.txt.fit(f, c.species, w - 70.0F, Font.SMALL), 44.0F, y + 17.0F, c.color, Font.SMALL);
            this.txt.para(f, c.when, 44.0F, y + 27.0F, w - 70.0F, Theme.TEXT3, Font.SMALL, 1.0F);
            if (on) {
               // a live waveform
               float t = (now - this.playingSince) / 1000.0F;
               for (int k = 0; k < 9; k++) {
                  float amp = 2.0F + 5.0F * Math.abs((float)Math.sin(t * 9.0F + k * 1.3F)) * (1.0F - t / c.seconds);
                  f.round(w - 46.0F + k * 3.2F, y + 9.0F - amp / 2.0F + 3.0F, 1.8F, amp, 0.9F, c.color);
               }
            }
         }
         y += ch + 5.0F;
      }
      y += this.txt.para(f, "Played through the phone's speaker for you only. Game answers a real call you blow, not a phone.", 12.0F, y + 2.0F, w - 24.0F,
         Theme.TEXT3, Font.SMALL, 1.0F) + 8.0F;
      this.endScroll(f, y0, y, w, h);
   }

   @Override
   public void tap(int id, long data) {
      if (id == Z_PLAY && data >= 0 && data < CALLS.length) {
         long now = this.act.millis();
         int i = (int)data;
         if (this.playing == i && now - this.playingSince < (long)(CALLS[i].seconds * 1000.0F)) {
            this.playing = -1;
            this.act.playCall("");
         } else {
            this.playing = i;
            this.playingSince = now;
            this.act.playCall(CALLS[i].sound);
         }
      }
   }

   @Override
   public void closed() {
      if (this.playing >= 0) {
         this.act.playCall("");
         this.playing = -1;
      }
   }
}
