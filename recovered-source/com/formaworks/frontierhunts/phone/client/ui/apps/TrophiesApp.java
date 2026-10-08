package com.formaworks.frontierhunts.phone.client.ui.apps;

import com.formaworks.frontierhunts.phone.client.ui.App;
import com.formaworks.frontierhunts.phone.client.ui.Canvas.Font;
import com.formaworks.frontierhunts.phone.client.ui.Frame;
import com.formaworks.frontierhunts.phone.client.ui.G;
import com.formaworks.frontierhunts.phone.client.ui.PhoneActions;
import com.formaworks.frontierhunts.phone.client.ui.PhoneModel;
import com.formaworks.frontierhunts.phone.client.ui.Theme;
import com.formaworks.frontierhunts.phone.client.ui.Ui;

/** [phone] Trophies: the animals you have tagged, newest first, from the licence system's filled-tag log. */
public final class TrophiesApp extends App {
   public TrophiesApp() {
      super("trophies", "Trophies", 6);
   }

   @Override
   public void opened() {
      this.act.refresh(PhoneActions.R_WALLET);
   }

   static G glyph(String species) {
      return switch (species) {
         case "whitetail", "elk", "moose", "pronghorn" -> G.ANTLER;
         case "mallard", "ruffed_grouse", "grouse", "duck" -> G.DUCK;
         case "bison" -> G.TRACK;
         default -> G.TRACK;
      };
   }

   @Override
   protected void drawPage(Frame f, int page, float w, float h) {
      PhoneModel.Wallet wl = this.m.wallet;
      f.fill(0.0F, 0.0F, w, h, 0xFF120E0A);
      f.gradient(0.0F, 0.0F, w, 120.0F, 0xFF2A1C10, 0xFF120E0A);
      float y = Ui.header(this.ui, f, "Trophies", wl.loaded ? wl.trophies.size() + " in your log · " + wl.record + " legal harvests" : "", false, w);
      if (!wl.loaded) {
         Ui.empty(this.ui, f, G.ANTLER, "Opening the log…", "", w / 2.0F, y + 30.0F, w - 40.0F);
         return;
      }
      if (wl.trophies.isEmpty()) {
         Ui.empty(this.ui, f, G.ANTLER, "No filled tags yet", "Take an animal with a tag in your pack and its filled tag is logged here: weight, points, date and place.",
            w / 2.0F, y + 30.0F, w - 40.0F);
         return;
      }
      float y0 = this.beginScroll(f, y, w, h);
      y = y0;
      for (PhoneModel.Trophy t : wl.trophies) {
         float inner = w - 18.0F - 46.0F;
         float ch = 50.0F;
         if (f.visible(0.0F, y, w, ch)) {
            f.round(9.0F, y, w - 18.0F, ch, 10.0F, 0xFF241A12);
            f.round(9.0F, y, w - 18.0F, 0.6F, 0.3F, 0x20FFFFFF);
            // the plaque
            f.round(15.0F, y + 7.0F, 30.0F, 36.0F, 6.0F, t.legal() ? 0xFF6A4528 : 0xFF4A2020);
            f.round(17.0F, y + 9.0F, 26.0F, 32.0F, 5.0F, t.legal() ? 0xFF825632 : 0xFF5A2626);
            glyph(t.species()).draw(f, 30.0F, y + 25.0F, 20.0F, 0xFFF0E6D0);
            f.text(this.txt.fit(f, t.title(), inner - (t.legal() ? 0.0F : 34.0F), Font.STRONG), 52.0F, y + 7.0F, Theme.TEXT, Font.STRONG);
            f.text(this.txt.fit(f, t.detail(), inner, Font.SMALL), 52.0F, y + 20.0F, Theme.GOLD, Font.SMALL);
            f.text(this.txt.fit(f, t.when() + (t.where().isEmpty() ? "" : " · " + t.where()), inner, Font.SMALL), 52.0F, y + 31.0F, Theme.TEXT3, Font.SMALL);
            if (!t.legal()) {
               Ui.chipRight(f, "Seized", w - 15.0F, y + 6.0F, Theme.RED_DIM, Theme.RED);
            }
         }
         y += ch + 6.0F;
      }
      this.endScroll(f, y0, y, w, h);
   }
}
