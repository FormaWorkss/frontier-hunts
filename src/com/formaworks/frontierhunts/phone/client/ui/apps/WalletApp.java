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
 * [phone] Licences: a wallet of what you carry this licence season. The hunting licence, every big-game tag (in your
 * pack, bought this season, the bag), the bird stamps with today's bag, what is open this month and your record with
 * the warden. Bought at a counter, not here: the wallet tells you where.
 */
public final class WalletApp extends App {
   private int refreshIn;

   public WalletApp() {
      super("wallet", "Licences", 5);
   }

   @Override
   public void opened() {
      this.act.refresh(PhoneActions.R_WALLET);
      this.refreshIn = 200;
   }

   @Override
   public void tick() {
      if (--this.refreshIn <= 0) {
         this.act.refresh(PhoneActions.R_WALLET);
         this.refreshIn = 400;
      }
   }

   @Override
   protected void drawPage(Frame f, int page, float w, float h) {
      PhoneModel.Wallet wl = this.m.wallet;
      f.fill(0.0F, 0.0F, w, h, Theme.BG);
      String sub = wl.loaded ? wl.period + " · " + wl.monthName + " · " + wl.daysLeft + (wl.daysLeft == 1 ? " day" : " days") + " left" : "";
      float y = Ui.header(this.ui, f, "Licences", sub, false, w);
      if (!wl.loaded) {
         Ui.empty(this.ui, f, G.CARD, this.m.signal > 0 ? "Opening your wallet…" : "No signal", "Your licence and tags appear here.", w / 2.0F, y + 30.0F,
            w - 40.0F);
         return;
      }
      float y0 = this.beginScroll(f, y, w, h);
      y = y0;
      y = this.licence(f, wl, w, y);
      y = this.tags(f, wl, w, y);
      y = this.stamps(f, wl, w, y);
      if (!wl.openNow.isEmpty()) {
         Ui.section(f, "OPEN THIS MONTH", 12.0F, y, w);
         y += 11.0F;
         float x = 12.0F;
         for (String s : wl.openNow) {
            float cw = f.width(s, Font.SMALL) + 8.0F;
            if (x + cw > w - 12.0F) {
               x = 12.0F;
               y += 14.0F;
            }
            Ui.chip(f, s, x, y, Theme.withAlpha(Theme.shade(Theme.MOSS, 0.28F), 255), Theme.MOSS);
            x += cw + 4.0F;
         }
         y += 20.0F;
      }
      y = this.warden(f, wl, w, y);
      y += this.txt.para(f, "Buy licences, tags and stamps at a Ranger counter, the Lodge Stores, or an Expedition or Contract board.", 12.0F, y,
         w - 24.0F, Theme.TEXT3, Font.SMALL, 1.0F) + 6.0F;
      this.endScroll(f, y0, y, w, h);
   }

   private float licence(Frame f, PhoneModel.Wallet wl, float w, float y) {
      float ch = 84.0F;
      boolean valid = wl.licence && wl.licenceHeld && !wl.suspended;
      int top = wl.suspended ? 0xFF5A1C18 : (valid ? 0xFFE8701E : 0xFF4A4F4A);
      int bottom = wl.suspended ? 0xFF3A100E : (valid ? 0xFFB9501A : 0xFF323632);
      f.round(9.0F, y + 1.5F, w - 18.0F, ch, 10.0F, 0x60000000);
      f.round(9.0F, y, w - 18.0F, ch, 10.0F, top);
      f.gradient(9.0F, y + 10.0F, w - 18.0F, ch - 20.0F, top, bottom);
      f.round(9.0F, y + ch - 20.0F, w - 18.0F, 20.0F, 10.0F, bottom);
      // guilloche-like lines
      for (int i = 0; i < 6; i++) {
         float yy = y + 18.0F + i * 9.0F;
         for (int k = 0; k < 16; k++) {
            float x0 = 12.0F + k * 9.0F;
            f.line(x0, yy + (float)Math.sin(k * 0.8 + i) * 2.0F, x0 + 9.0F, yy + (float)Math.sin((k + 1) * 0.8 + i) * 2.0F, 0.4F, 0x14FFFFFF);
         }
      }
      f.text("RESERVE HUNTING LICENCE", 17.0F, y + 7.0F, 0xD0FFFFFF, Font.SMALL);
      f.text(this.txt.fit(f, this.m.playerName, w - 70.0F, Font.MEDIUM), 17.0F, y + 20.0F, 0xFFFFFFFF, Font.MEDIUM);
      f.text(wl.period, 17.0F, y + 36.0F, 0xE0FFFFFF, Font.SMALL);
      String st = wl.suspended ? "SUSPENDED" : (!wl.licence ? "NOT BOUGHT" : (!wl.licenceHeld ? "NOT IN PACK" : "VALID"));
      Ui.chip(f, st, 17.0F, y + ch - 17.0F, 0x50000000, wl.suspended ? 0xFFFF9A8A : (valid ? 0xFFFFFFFF : 0xFFE0E0D8));
      String rule = "Rules: " + wl.rules;
      f.right(rule, w - 17.0F, y + ch - 15.0F, 0xC0FFFFFF, Font.SMALL);
      // the seal
      f.circle(w - 32.0F, y + 28.0F, 13.0F, 0x30FFFFFF);
      f.ring(w - 32.0F, y + 28.0F, 13.0F, 1.0F, 0x70FFFFFF);
      G.ANTLER.draw(f, w - 32.0F, y + 28.0F, 16.0F, 0xC0FFFFFF);
      if (wl.licence && !wl.licenceHeld && !wl.suspended) {
         y += ch + 4.0F;
         y += this.txt.para(f, "Your licence is not in your pack. A lost licence is replaced free at a counter.", 12.0F, y, w - 24.0F, Theme.YELLOW,
            Font.SMALL, 1.0F) + 2.0F;
         return y + 6.0F;
      }
      return y + ch + 10.0F;
   }

   private float tags(Frame f, PhoneModel.Wallet wl, float w, float y) {
      if (wl.tags.isEmpty()) {
         return y;
      }
      Ui.section(f, "BIG-GAME TAGS", 12.0F, y, w);
      y += 11.0F;
      for (PhoneModel.Tag t : wl.tags) {
         float ch = 36.0F;
         if (f.visible(0.0F, y, w, ch)) {
            Ui.card(f, 9.0F, y, w - 18.0F, ch);
            f.round(9.0F, y, 4.0F, ch, 2.0F, t.color());
            // the tag shape
            f.round(18.0F, y + 7.0F, 18.0F, 22.0F, 3.0F, t.color());
            f.circle(27.0F, y + 11.5F, 2.0F, Theme.SURFACE2);
            String held = t.held() + " in pack";
            String season = t.bought() + " / " + t.bag() + " bought";
            float rw = Math.max(f.width(held, Font.STRONG), f.width(season, Font.SMALL)) + 6.0F;
            f.text(this.txt.fit(f, t.name(), w - 59.0F - rw, Font.STRONG), 42.0F, y + 6.0F, Theme.TEXT, Font.STRONG);
            f.text(this.txt.fit(f, t.months(), w - 59.0F - rw, Font.SMALL), 42.0F, y + 19.0F, t.open() ? Theme.MOSS : Theme.TEXT3, Font.SMALL);
            f.right(held, w - 17.0F, y + 6.0F, t.held() > 0 ? Theme.TEXT : Theme.TEXT3, Font.STRONG);
            int left = Math.max(0, t.bag() - t.bought());
            f.right(season, w - 17.0F, y + 19.0F, left == 0 ? Theme.YELLOW : Theme.TEXT3, Font.SMALL);
         }
         y += ch + 4.0F;
      }
      return y + 6.0F;
   }

   private float stamps(Frame f, PhoneModel.Wallet wl, float w, float y) {
      if (wl.stamps.isEmpty()) {
         return y;
      }
      Ui.section(f, "BIRD STAMPS", 12.0F, y, w);
      y += 11.0F;
      for (PhoneModel.Stamp s : wl.stamps) {
         float ch = 32.0F;
         Ui.card(f, 9.0F, y, w - 18.0F, ch);
         f.round(15.0F, y + 6.0F, 20.0F, 20.0F, 4.0F, s.held() ? 0xFF3D6E8C : Theme.SURFACE3);
         G.FEATHER.draw(f, 25.0F, y + 16.0F, 13.0F, s.held() ? 0xFFE8F2F8 : Theme.TEXT3);
         float rw = Math.max(f.width(s.today(), Font.SMALL), 30.0F) + 6.0F;
         f.text(this.txt.fit(f, s.name(), w - 59.0F - rw, Font.STRONG), 42.0F, y + 5.0F, Theme.TEXT, Font.STRONG);
         f.text(this.txt.fit(f, s.months(), w - 59.0F - rw, Font.SMALL), 42.0F, y + 18.0F, s.open() ? Theme.MOSS : Theme.TEXT3, Font.SMALL);
         f.right(s.held() ? "Held" : "None", w - 17.0F, y + 5.0F, s.held() ? Theme.TEXT : Theme.TEXT3, Font.STRONG);
         f.right(s.today(), w - 17.0F, y + 18.0F, Theme.TEXT3, Font.SMALL);
         y += ch + 4.0F;
      }
      return y + 6.0F;
   }

   private float warden(Frame f, PhoneModel.Wallet wl, float w, float y) {
      Ui.section(f, "WARDEN RECORD", 12.0F, y, w);
      y += 11.0F;
      float ch = 44.0F;
      Ui.card(f, 9.0F, y, w - 18.0F, ch);
      boolean clean = wl.violations == 0 && wl.strikes == 0;
      G.SHIELD.draw(f, 23.0F, y + 15.0F, 14.0F, clean ? Theme.MOSS : Theme.YELLOW);
      f.text(clean ? "Clean record" : wl.violations + (wl.violations == 1 ? " violation" : " violations") + " this season", 36.0F, y + 9.0F, Theme.TEXT,
         Font.STRONG);
      f.text(wl.filled + " filled tags · " + wl.strikes + " strikes · " + wl.fines + " tokens fined", 36.0F, y + 22.0F, Theme.TEXT3, Font.SMALL);
      if (wl.suspended) {
         f.text("Licence suspended for this season.", 36.0F, y + 32.0F, Theme.RED, Font.SMALL);
      }
      return y + ch + 10.0F;
   }
}
