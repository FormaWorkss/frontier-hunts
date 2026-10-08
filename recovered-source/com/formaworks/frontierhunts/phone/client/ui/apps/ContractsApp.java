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
 * [phone] Contracts: your work in the reserve, the same as the expedition journal and the ranger dossier but in your
 * pocket. Ranger Mara's story missions (file the report when done), the contract board (accept, track the clock, hand
 * in, abandon) and ranger assignments (accept, collect, cancel). Every button only asks: the server checks the same
 * rules as the boards and journals do.
 */
public final class ContractsApp extends App {
   private static final int Z_TAB = 100;
   private static final int Z_STORY = 101;
   private static final int Z_HAND_IN = 102;
   private static final int Z_ABANDON = 103;
   private static final int Z_ACCEPT = 104;
   private static final int Z_COLLECT = 105;
   private static final int Z_CANCEL = 106;
   private static final int Z_TAKE = 107;
   private static final String[] TABS = {"Active", "Board", "Ranger"};
   private int tab;
   private int refreshIn;
   private long confirmAbandon;
   private long confirmCancel;
   private long busyUntil;

   public ContractsApp() {
      super("contracts", "Contracts", 4);
   }

   @Override
   public void opened() {
      this.act.refresh(PhoneActions.R_CONTRACTS);
      this.refreshIn = 60;
   }

   @Override
   public void tick() {
      if (--this.refreshIn <= 0) {
         this.act.refresh(PhoneActions.R_CONTRACTS);
         this.refreshIn = 100;
      }
   }

   @Override
   protected void drawPage(Frame f, int page, float w, float h) {
      PhoneModel.Contracts c = this.m.contracts;
      f.fill(0.0F, 0.0F, w, h, Theme.BG);
      float y = Ui.header(this.ui, f, "Contracts", "", false, w) - 14.0F;
      // tokens
      String tok = Integer.toString(c.tokens);
      float tw = f.width(tok, Font.STRONG) + 24.0F;
      f.round(w - 9.0F - tw, Theme.STATUS_H + 12.0F, tw, 16.0F, 8.0F, Theme.withAlpha(Theme.shade(Theme.GOLD, 0.3F), 255));
      G.TOKEN.draw(f, w - 9.0F - tw + 9.0F, Theme.STATUS_H + 20.0F, 9.0F, Theme.GOLD);
      f.text(tok, w - 9.0F - tw + 16.0F, Theme.STATUS_H + 16.0F, Theme.GOLD, Font.STRONG);
      y += 14.0F;
      Ui.segmented(this.ui, f, Z_TAB, 9.0F, y, w - 18.0F, 16.0F, TABS, this.tab);
      y += 22.0F;
      if (!c.loaded) {
         Ui.empty(this.ui, f, G.CLIPBOARD, this.m.signal > 0 ? "Checking the boards…" : "No signal",
            this.m.signal > 0 ? "Your contracts arrive in a moment." : "Contracts need a bar of signal. Get out of the valley or step outside.", w / 2.0F,
            y + 30.0F, w - 40.0F);
         return;
      }
      float y0 = this.beginScroll(f, y, w, h);
      float end = switch (this.tab) {
         case 1 -> this.board(f, w, y0);
         case 2 -> this.ranger(f, w, y0);
         default -> this.active(f, w, y0);
      };
      this.endScroll(f, y0, end, w, h);
   }

   // ------------------------------------------------------------------------------------------------ cards

   private float textCard(Frame f, float y, float w, int accent, G glyph, String kicker, String title, String text, int count, int amount, String right,
      int rightColor) {
      float inner = w - 18.0F - 20.0F;
      float th = this.txt.paraHeight(f, text, inner, Font.SMALL, 1.0F);
      float ch = 44.0F + th + (amount > 0 ? 14.0F : 0.0F);
      Ui.card(f, 9.0F, y, w - 18.0F, ch);
      f.round(9.0F, y, 3.0F, ch, 1.5F, accent);
      glyph.draw(f, 21.0F, y + 9.0F, 8.0F, accent);
      float rw = right.isEmpty() ? 0.0F : f.width(right, Font.SMALL) + 6.0F;
      f.text(this.txt.fit(f, kicker, w - 45.0F - rw, Font.SMALL), 28.0F, y + 6.0F, accent, Font.SMALL);
      if (!right.isEmpty()) {
         f.right(right, w - 17.0F, y + 6.0F, rightColor, Font.SMALL);
      }
      f.text(this.txt.fit(f, title, inner, Font.STRONG), 19.0F, y + 17.0F, Theme.TEXT, Font.STRONG);
      this.txt.para(f, text, 19.0F, y + 30.0F, inner, Theme.TEXT2, Font.SMALL, 1.0F);
      if (amount > 0) {
         float by = y + 34.0F + th;
         Ui.bar(f, 19.0F, by + 3.0F, inner - 30.0F, 4.0F, count / (float)amount, Theme.SURFACE4, count >= amount ? Theme.MOSS : accent);
         f.right(Math.min(count, amount) + " / " + amount, w - 17.0F, by, count >= amount ? Theme.MOSS : Theme.TEXT, Font.SMALL);
      }
      return ch;
   }

   private float active(Frame f, float w, float y) {
      PhoneModel.Contracts c = this.m.contracts;
      long now = this.act.millis();
      boolean busy = now < this.busyUntil;
      float start = y;
      // the campaign
      if (!c.storyDone && !c.storyTitle.isEmpty()) {
         boolean ready = c.storyAmount > 0 && c.storyCount >= c.storyAmount;
         float ch = this.textCard(f, y, w, Theme.GOLD, G.STAR, "RANGER MARA · CHAPTER " + roman(c.storyChapter), c.storyTitle, c.storyText, c.storyCount,
            c.storyAmount, c.storyTokens + " tokens", Theme.GOLD);
         y += ch + 4.0F;
         if (ready) {
            Ui.button(this.ui, f, Z_STORY, 0L, 9.0F, y, w - 18.0F, 22.0F, "File the report · " + c.storyTokens + " tokens", Ui.GOOD, !busy);
            y += 28.0F;
         }
         y += 4.0F;
      } else if (c.storyDone) {
         Ui.card(f, 9.0F, y, w - 18.0F, 30.0F);
         G.CHECK.draw(f, 22.0F, y + 15.0F, 11.0F, Theme.MOSS);
         this.txt.para(f, "Season ledger closed. Contracts are yours to revisit with friends.", 32.0F, y + 6.0F, w - 50.0F, Theme.TEXT2, Font.SMALL, 1.0F);
         y += 36.0F;
      }
      // the contract
      if (c.contract >= 0) {
         String st = c.contractStatus;
         boolean ready = st.equals("ready");
         boolean expired = st.equals("expired");
         String right = ready ? "READY" : (expired ? "EXPIRED" : Ui.duration(c.contractSeconds) + " left");
         int rc = ready ? Theme.MOSS : (expired ? Theme.RED : (c.contractSeconds < 120 ? Theme.RED : Theme.TEXT2));
         float ch = this.textCard(f, y, w, Theme.BLAZE, G.CLIPBOARD, "CONTRACT · " + c.contractTokens + " TOKENS", c.contractTitle, c.contractText,
            c.contractCount, c.contractAmount, right, rc);
         y += ch + 4.0F;
         float bw = (w - 24.0F) / 2.0F;
         boolean sure = now < this.confirmAbandon;
         if (ready) {
            Ui.button(this.ui, f, Z_HAND_IN, 0L, 9.0F, y, w - 18.0F, 22.0F, "Hand in · " + c.contractTokens + " tokens", Ui.GOOD, !busy);
            y += 28.0F;
         } else {
            Ui.button(this.ui, f, Z_ABANDON, 0L, 9.0F, y, w - 18.0F, 20.0F, expired ? "Clear it from the board" : (sure ? "Tap again to abandon" : "Abandon"),
               expired ? Ui.TONAL : Ui.DANGER, !busy);
            y += 26.0F;
         }
         y += 4.0F;
      }
      // the assignment
      if (c.assignState != 0) {
         boolean ready = c.assignState == 2;
         boolean failed = c.assignState == 3;
         String right = ready ? "READY" : (failed ? "FAILED" : (c.assignTicks > 0 ? Ui.duration(c.assignTicks / 20L) + " left" : ""));
         int rc = ready ? Theme.MOSS : (failed ? Theme.RED : Theme.TEXT2);
         float ch = this.textCard(f, y, w, Theme.SKY, G.RANGER, "RANGER ASSIGNMENT · " + c.assignTokens + " TOKENS", c.assignTitle, c.assignText,
            c.assignCount, c.assignTarget, right, rc);
         y += ch + 4.0F;
         boolean sure = now < this.confirmCancel;
         if (ready) {
            Ui.button(this.ui, f, Z_COLLECT, 0L, 9.0F, y, w - 18.0F, 22.0F, "Collect · " + c.assignTokens + " tokens · " + c.assignXp + " XP", Ui.GOOD,
               !busy);
            y += 28.0F;
         } else {
            Ui.button(this.ui, f, Z_CANCEL, 0L, 9.0F, y, w - 18.0F, 20.0F, failed ? "Clear" : (sure ? "Tap again to cancel" : "Cancel assignment"),
               failed ? Ui.TONAL : Ui.DANGER, !busy);
            y += 26.0F;
         }
      }
      if (y == start || c.contract < 0 && c.assignState == 0) {
         float ey = y + 8.0F;
         Ui.card(f, 9.0F, ey, w - 18.0F, 46.0F, Theme.SURFACE);
         G.CLIPBOARD.draw(f, 24.0F, ey + 23.0F, 14.0F, Theme.TEXT3);
         f.text("Nothing on the clock", 36.0F, ey + 10.0F, Theme.TEXT, Font.STRONG);
         this.txt.para(f, "Take a contract from the Board or a Ranger assignment.", 36.0F, ey + 23.0F, w - 56.0F, Theme.TEXT3, Font.SMALL, 1.0F);
         y = ey + 52.0F;
      }
      return y;
   }

   private static String roman(int n) {
      return n <= 1 ? "I" : (n == 2 ? "II" : "III");
   }

   private float board(Frame f, float w, float y) {
      PhoneModel.Contracts c = this.m.contracts;
      boolean busy = this.act.millis() < this.busyUntil;
      boolean free = c.contract < 0 || c.contractStatus.equals("expired");
      if (!c.boardNote.isEmpty()) {
         y += this.txt.para(f, c.boardNote, 12.0F, y, w - 24.0F, Theme.TEXT3, Font.SMALL, 1.0F) + 6.0F;
      }
      if (!free) {
         f.round(9.0F, y, w - 18.0F, 18.0F, 9.0F, Theme.withAlpha(Theme.shade(Theme.BLAZE, 0.25F), 255));
         f.center("Finish or abandon your contract first", w / 2.0F, y + 5.0F, Theme.BLAZE, Font.SMALL);
         y += 24.0F;
      }
      for (PhoneModel.Offer o : c.board) {
         float inner = w - 18.0F - 20.0F;
         float th = this.txt.paraHeight(f, o.text(), inner, Font.SMALL, 1.0F);
         float ch = 56.0F + th;
         if (f.visible(0.0F, y, w, ch)) {
            boolean mine = o.index() == c.contract && !c.contractStatus.equals("expired");
            Ui.card(f, 9.0F, y, w - 18.0F, ch);
            float cx = 19.0F;
            if (!o.species().isEmpty()) {
               cx += Ui.chip(f, o.species(), cx, y + 6.0F, Theme.withAlpha(Theme.shade(Theme.MOSS, 0.3F), 255), Theme.MOSS) + 4.0F;
            }
            Ui.chip(f, "x" + o.amount(), cx, y + 6.0F, Theme.SURFACE3, Theme.TEXT2);
            G.TOKEN.draw(f, w - 40.0F, y + 11.5F, 8.0F, Theme.GOLD);
            f.text(Integer.toString(o.tokens()), w - 34.0F, y + 8.0F, Theme.GOLD, Font.STRONG);
            f.text(this.txt.fit(f, o.title(), inner, Font.STRONG), 19.0F, y + 21.0F, Theme.TEXT, Font.STRONG);
            this.txt.para(f, o.text(), 19.0F, y + 33.0F, inner, Theme.TEXT2, Font.SMALL, 1.0F);
            Ui.button(this.ui, f, Z_ACCEPT, o.index(), w - 70.0F, y + ch - 20.0F, 54.0F, 15.0F, mine ? "Taken" : "Accept", Ui.FILLED, free && !busy && !mine);
         }
         y += ch + 6.0F;
      }
      return y;
   }

   private float ranger(Frame f, float w, float y) {
      PhoneModel.Contracts c = this.m.contracts;
      boolean busy = this.act.millis() < this.busyUntil;
      if (!c.eligible) {
         y += this.txt.para(f, "Ranger assignments need Survival mode in an active reserve.", 12.0F, y, w - 24.0F, Theme.TEXT3, Font.SMALL, 1.0F) + 8.0F;
      }
      boolean free = c.assignState == 0;
      if (!free) {
         f.round(9.0F, y, w - 18.0F, 18.0F, 9.0F, Theme.withAlpha(Theme.shade(Theme.SKY, 0.25F), 255));
         f.center("One assignment at a time", w / 2.0F, y + 5.0F, Theme.SKY, Font.SMALL);
         y += 24.0F;
      }
      for (int i = 0; i < c.assignments.size(); i++) {
         PhoneModel.Assignment a = c.assignments.get(i);
         float inner = w - 18.0F - 20.0F;
         String text = this.txt.fit(f, a.text(), inner * 3.0F, Font.SMALL);
         float th = this.txt.paraHeight(f, text, inner, Font.SMALL, 1.0F);
         float ch = 56.0F + th;
         if (f.visible(0.0F, y, w, ch)) {
            boolean mine = a.id().equals(c.assignId) && c.assignState != 0;
            Ui.card(f, 9.0F, y, w - 18.0F, ch);
            float cx = 19.0F;
            cx += Ui.chip(f, a.kind(), cx, y + 6.0F, Theme.withAlpha(Theme.shade(Theme.SKY, 0.3F), 255), Theme.SKY) + 4.0F;
            Ui.chip(f, "x" + a.target(), cx, y + 6.0F, Theme.SURFACE3, Theme.TEXT2);
            G.TOKEN.draw(f, w - 40.0F, y + 11.5F, 8.0F, Theme.GOLD);
            f.text(Integer.toString(a.tokens()), w - 34.0F, y + 8.0F, Theme.GOLD, Font.STRONG);
            f.text(this.txt.fit(f, a.title(), inner, Font.STRONG), 19.0F, y + 21.0F, Theme.TEXT, Font.STRONG);
            this.txt.para(f, text, 19.0F, y + 33.0F, inner, Theme.TEXT2, Font.SMALL, 1.0F);
            String note = a.cooldownTicks() > 0 ? "Again in " + Ui.duration(a.cooldownTicks() / 20L) : (a.duration() > 0 ? Ui.duration(a.duration() / 20L)
               + " to finish" : "");
            f.text(note, 19.0F, y + ch - 16.0F, a.cooldownTicks() > 0 ? Theme.YELLOW : Theme.TEXT3, Font.SMALL);
            Ui.button(this.ui, f, Z_TAKE, i, w - 70.0F, y + ch - 20.0F, 54.0F, 15.0F, mine ? "Taken" : "Accept", Ui.FILLED,
               free && c.eligible && a.cooldownTicks() <= 0 && !busy && !mine);
         }
         y += ch + 6.0F;
      }
      if (c.assignments.isEmpty()) {
         f.center("No assignments posted.", w / 2.0F, y + 10.0F, Theme.TEXT3, Font.SMALL);
         y += 30.0F;
      }
      return y;
   }

   // ------------------------------------------------------------------------------------------------ input

   private void busy() {
      this.busyUntil = this.act.millis() + 700L;
      this.refreshIn = 12;
   }

   @Override
   public void tap(int id, long data) {
      PhoneModel.Contracts c = this.m.contracts;
      long now = this.act.millis();
      switch (id) {
         case Z_TAB -> {
            this.tab = (int)data;
            this.act.sound(PhoneActions.Sfx.TAP);
         }
         case Z_STORY -> {
            this.act.contract(PhoneActions.C_MISSION_CLAIM, 0, c.storyId);
            this.busy();
            this.act.sound(PhoneActions.Sfx.SUCCESS);
         }
         case Z_HAND_IN -> {
            this.act.contract(PhoneActions.C_CONTRACT_CLAIM, c.contract, c.contractSerial);
            this.busy();
            this.act.sound(PhoneActions.Sfx.SUCCESS);
         }
         case Z_ABANDON -> {
            if (c.contractStatus.equals("expired") || now < this.confirmAbandon) {
               this.confirmAbandon = 0L;
               this.act.contract(PhoneActions.C_CONTRACT_ABANDON, c.contract, "");
               this.busy();
            } else {
               this.confirmAbandon = now + 4000L;
               this.act.sound(PhoneActions.Sfx.TAP);
            }
         }
         case Z_ACCEPT -> {
            this.act.contract(PhoneActions.C_CONTRACT_ACCEPT, (int)data, "");
            this.busy();
            this.tab = 0;
            this.act.sound(PhoneActions.Sfx.SUCCESS);
         }
         case Z_COLLECT -> {
            this.act.contract(PhoneActions.C_ASSIGN_CLAIM, 0, c.assignId);
            this.busy();
            this.act.sound(PhoneActions.Sfx.SUCCESS);
         }
         case Z_CANCEL -> {
            if (c.assignState == 3 || now < this.confirmCancel) {
               this.confirmCancel = 0L;
               this.act.contract(PhoneActions.C_ASSIGN_CANCEL, 0, c.assignId);
               this.busy();
            } else {
               this.confirmCancel = now + 4000L;
               this.act.sound(PhoneActions.Sfx.TAP);
            }
         }
         case Z_TAKE -> {
            if (data >= 0 && data < c.assignments.size()) {
               this.act.contract(PhoneActions.C_ASSIGN_ACCEPT, 0, c.assignments.get((int)data).id());
               this.busy();
               this.tab = 0;
               this.act.sound(PhoneActions.Sfx.SUCCESS);
            }
         }
         default -> {
         }
      }
   }
}
