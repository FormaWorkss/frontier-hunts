package com.formaworks.frontierhunts.phone.client.ui.apps;

import com.formaworks.frontierhunts.phone.client.ui.App;
import com.formaworks.frontierhunts.phone.client.ui.Canvas.Font;
import com.formaworks.frontierhunts.phone.client.ui.Emoji;
import com.formaworks.frontierhunts.phone.client.ui.Frame;
import com.formaworks.frontierhunts.phone.client.ui.G;
import com.formaworks.frontierhunts.phone.client.ui.PhoneActions;
import com.formaworks.frontierhunts.phone.client.ui.PhoneModel;
import com.formaworks.frontierhunts.phone.client.ui.Theme;
import com.formaworks.frontierhunts.phone.client.ui.Ui;
import java.util.ArrayList;
import java.util.List;

/**
 * [phone] Messages: texts between hunters on the server. Conversations newest first with unread dots; a thread of
 * bubbles; type with the keyboard (Enter sends) or tap a quick reply ("On my way", "Got one!", "Meet at camp"...), or
 * send your position. Texts to a hunter who is offline wait on the server until they come back. Needs a bar of signal
 * to send; the server checks and paces every text.
 */
public final class MessagesApp extends App {
   static final int LIST = 0;
   static final int THREAD = 1;
   static final int PICK = 2;
   private static final int Z_THREAD = 100;
   private static final int Z_NEW = 101;
   private static final int Z_PICK = 102;
   private static final int Z_SEND = 103;
   private static final int Z_QUICK = 104;
   private static final int Z_FIELD = 105;
   private static final int Z_SPOT = 106;
   private static final int Z_CAMERA = 107;
   private static final int Z_EMOJI = 108;
   private static final int Z_EMO = 109;
   private static final int Z_PHOTO = 110;
   private static final int Z_PBACK = 111;
   static final int PHOTO = 3;
   private static final int MAX = 200;
   private static final String[] QUICK = {"On my way", "Got one!", "Meet at camp", "Where are you?", "Nice shot!", "Heading back", "Deer moving here",
      "Need a hand dragging"};
   private final StringBuilder draft = new StringBuilder();
   private boolean emojiOpen;
   private long photoId;
   private String peer = "";
   // conversation summaries, rebuilt when the texts change
   private final List<String> peers = new ArrayList<>();
   private final List<PhoneModel.Text> last = new ArrayList<>();
   private final List<Integer> unread = new ArrayList<>();
   private final List<String> pickable = new ArrayList<>();
   private int builtFor = -1;
   private int builtSize = -1;

   public MessagesApp() {
      super("messages", "Messages", 14);
   }

   @Override
   public void opened() {
      this.act.refresh(PhoneActions.R_MESSAGES);
   }

   @Override
   public boolean grabsKeys() {
      return this.page() == THREAD;
   }

   @Override
   protected boolean landscapePage(int page) {
      return page == PHOTO;
   }

   private void rebuild() {
      PhoneModel.Messages ms = this.m.messages;
      if (this.builtFor == this.m.version && this.builtSize == ms.texts.size()) {
         return;
      }
      this.builtFor = this.m.version;
      this.builtSize = ms.texts.size();
      this.peers.clear();
      this.last.clear();
      this.unread.clear();
      for (int i = ms.texts.size() - 1; i >= 0; i--) {
         PhoneModel.Text t = ms.texts.get(i);
         int at = this.indexOf(t.peer());
         if (at < 0) {
            this.peers.add(t.peer());
            this.last.add(t);
            this.unread.add(!t.mine() && !t.read() ? 1 : 0);
         } else if (!t.mine() && !t.read()) {
            this.unread.set(at, this.unread.get(at) + 1);
         }
      }
      this.pickable.clear();
      for (String p : this.m.players) {
         if (!p.equalsIgnoreCase(this.m.playerName) && !this.pickable.contains(p)) {
            this.pickable.add(p);
         }
      }
      for (String p : ms.contacts) {
         if (!p.equalsIgnoreCase(this.m.playerName) && !this.pickable.contains(p)) {
            this.pickable.add(p);
         }
      }
   }

   private int indexOf(String peer) {
      for (int i = 0; i < this.peers.size(); i++) {
         if (this.peers.get(i).equalsIgnoreCase(peer)) {
            return i;
         }
      }
      return -1;
   }

   private boolean online(String name) {
      for (String p : this.m.players) {
         if (p.equalsIgnoreCase(name)) {
            return true;
         }
      }
      return false;
   }

   private static final String[] AGO = new String[60 + 24 + 400];

   /** "now", "5 min", "3 h", "2 d": built once per value (no new strings each frame). */
   static String ago(long at, long now) {
      long s = Math.max(0L, (now - at) / 1000L);
      if (s < 60L) {
         return "now";
      }
      int slot = s < 3600L ? (int)(s / 60L) : (s < 86400L ? 60 + (int)(s / 3600L) : 84 + (int)Math.min(399L, s / 86400L));
      String c = AGO[slot];
      if (c == null) {
         c = slot < 60 ? slot + " min" : (slot < 84 ? (slot - 60) + " h" : (slot - 84) + " d");
         AGO[slot] = c;
      }
      return c;
   }

   @Override
   protected void drawPage(Frame f, int page, float w, float h) {
      this.rebuild();
      switch (page) {
         case THREAD -> this.thread(f, w, h);
         case PICK -> this.pick(f, w, h);
         case PHOTO -> this.photo(f, w, h);
         default -> this.list(f, w, h);
      }
   }

   private void list(Frame f, float w, float h) {
      f.fill(0.0F, 0.0F, w, h, Theme.BG);
      f.gradient(0.0F, 0.0F, w, 110.0F, 0xFF16261C, Theme.BG);
      PhoneModel.Messages ms = this.m.messages;
      float y = Ui.header(this.ui, f, "Messages", ms.unread > 0 ? ms.unread + (ms.unread == 1 ? " unread text" : " unread texts") : "Texts with hunters here",
         false, w);
      Ui.button(this.ui, f, Z_NEW, 0L, 9.0F, y, w - 18.0F, 20.0F, this.m.signal > 0 ? "New message" : "No signal", Ui.TONAL, this.m.signal > 0);
      y += 28.0F;
      if (!ms.loaded) {
         Ui.empty(this.ui, f, G.LIST, "Loading…", "", w / 2.0F, y + 20.0F, w - 40.0F);
         return;
      }
      if (this.peers.isEmpty()) {
         Ui.empty(this.ui, f, G.PERSON, "No texts yet", "Text another hunter on this server: plan a drive, share a spot, brag about a buck.", w / 2.0F,
            y + 20.0F, w - 40.0F);
         return;
      }
      float y0 = this.beginScroll(f, y, w, h);
      y = y0;
      long now = System.currentTimeMillis();
      for (int i = 0; i < this.peers.size(); i++) {
         String p = this.peers.get(i);
         PhoneModel.Text t = this.last.get(i);
         int un = this.unread.get(i);
         float ch = 38.0F;
         if (f.visible(0.0F, y, w, ch)) {
            boolean hot = this.ui.hot(Z_THREAD, i);
            if (hot) {
               f.round(6.0F, y, w - 12.0F, ch, 9.0F, 0x14FFFFFF);
            }
            // avatar: initial in a coloured disc
            int col = Theme.mix(0xFF2E5A3A, 0xFF7A5A2A, (p.hashCode() & 255) / 255.0F);
            f.circle(26.0F, y + ch / 2.0F, 13.0F, col);
            f.center(p.isEmpty() ? "?" : p.substring(0, 1).toUpperCase(java.util.Locale.ROOT), 26.0F, y + ch / 2.0F - 5.0F, Theme.TEXT, Font.STRONG);
            if (this.online(p)) {
               f.circle(36.0F, y + ch / 2.0F + 9.0F, 3.4F, Theme.BG);
               f.circle(36.0F, y + ch / 2.0F + 9.0F, 2.4F, Theme.MOSS);
            }
            String when = ago(t.at(), now);
            float ww = f.width(when, Font.SMALL);
            f.text(this.txt.fit(f, p, w - 60.0F - ww - 12.0F, Font.STRONG), 46.0F, y + 7.0F, Theme.TEXT, Font.STRONG);
            f.right(when, w - 14.0F, y + 8.0F, un > 0 ? Theme.BLAZE : Theme.TEXT3, Font.SMALL);
            int lc = un > 0 ? Theme.TEXT2 : Theme.TEXT3;
            float lx = 46.0F, lw = w - 60.0F - (un > 0 ? 18.0F : 0.0F);
            if (t.mine()) {
               lx = f.text("You: ", lx, y + 21.0F, lc, Font.SMALL);
            }
            if (photoOf(t.text()) != 0L) {
               // [1.4.0] a photo: a camera glyph and the word
               G.CAMERA.draw(f, lx + 4.0F, y + 24.5F, 8.0F, lc);
               f.text("Photo", lx + 10.0F, y + 21.0F, lc, Font.SMALL);
            } else {
               Emoji.line(f, t.text(), lx, y + 21.0F, lw - (lx - 46.0F), lc, Font.SMALL);
            }
            if (un > 0) {
               String b = un > 9 ? "9+" : Integer.toString(un);
               float bw = Math.max(10.0F, f.width(b, Font.SMALL) + 6.0F);
               f.round(w - 14.0F - bw, y + 19.0F, bw, 10.0F, 5.0F, Theme.BLAZE);
               f.center(b, w - 14.0F - bw / 2.0F, y + 20.5F, 0xFFFFFFFF, Font.SMALL);
            }
            f.zone(Z_THREAD, 6.0F, y, w - 12.0F, ch, i);
         }
         y += ch + 2.0F;
         Ui.divider(f, 46.0F, y - 1.0F, w - 56.0F);
      }
      this.endScroll(f, y0, y, w, h);
   }

   /** [1.4.0] The photo a text shows ({@code [photo:<16 hex>]}), or 0. */
   static long photoOf(String text) {
      if (text == null) {
         return 0L;
      }
      int i = text.indexOf("[photo:");
      if (i < 0 || i + 24 > text.length() || text.charAt(i + 23) != ']') {
         return 0L;
      }
      try {
         return Long.parseUnsignedLong(text.substring(i + 7, i + 23), 16);
      } catch (NumberFormatException e) {
         return 0L;
      }
   }

   /** The words sent with a photo (anything but the photo's token). */
   static String caption(String text) {
      int i = text.indexOf("[photo:");
      return i < 0 ? text : (text.substring(0, i) + text.substring(Math.min(text.length(), i + 24))).trim();
   }

   private void thread(Frame f, float w, float h) {
      f.fill(0.0F, 0.0F, w, h, 0xFF0B100D);
      float top = Ui.bar(this.ui, f, this.peer, w, true);
      if (this.online(this.peer)) {
         f.center("online", w / 2.0F, top - 6.0F, Theme.MOSS, Font.SMALL);
      }
      PhoneModel.Messages ms = this.m.messages;
      boolean signal = this.m.signal > 0;
      // composer at the bottom: quick replies (or the emoji board), then the field
      float fieldH = 22.0F;
      float fieldY = h - Theme.HOME_BAR_H - fieldH - 4.0F;
      float emojiH = this.ui.anim(0xE301L, this.emojiOpen ? 76.0F : 0.0F, 18.0F);
      float quickY = fieldY - 21.0F - emojiH;
      float listBottom = quickY - 4.0F;
      // the thread, bottom-aligned: measure, then draw from the newest upward
      f.clip(0.0F, top, w, listBottom - top);
      float y = listBottom - 4.0F;
      long now = System.currentTimeMillis();
      float maxW = w * 0.72F;
      int shown = 0;
      for (int i = ms.texts.size() - 1; i >= 0 && y > top - 40.0F && shown < 60; i--) {
         PhoneModel.Text t = ms.texts.get(i);
         if (!t.peer().equalsIgnoreCase(this.peer)) {
            continue;
         }
         shown++;
         y = this.bubble(f, t, y, w, maxW, now, i);
      }
      if (shown == 0) {
         this.txt.paraCenter(f, "Say hello to " + this.peer + ". Texts, photos and emoji wait for them if they are away.", w / 2.0F, listBottom - 40.0F,
            w - 50.0F, Theme.TEXT3, Font.SMALL, 2.0F);
      }
      f.unclip();
      if (emojiH > 1.0F) {
         this.emojiBoard(f, w, quickY + 19.0F, emojiH);
      }
      // quick replies (a row of chips): your spot first
      float x = 9.0F;
      {
         boolean hot = this.ui.hot(Z_SPOT, 0L);
         float cw = f.width("My spot", Font.SMALL) + 21.0F;
         f.round(x, quickY, cw, 15.0F, 7.5F, hot ? Theme.SURFACE4 : Theme.SURFACE2);
         G.PIN.draw(f, x + 8.0F, quickY + 7.5F, 9.0F, signal ? Theme.BLAZE : Theme.TEXT3);
         f.text("My spot", x + 14.0F, quickY + 4.0F, signal ? Theme.TEXT2 : Theme.TEXT3, Font.SMALL);
         if (signal) {
            f.zone(Z_SPOT, x, quickY, cw, 15.0F);
         }
         x += cw + 4.0F;
      }
      for (int i = 0; i < QUICK.length; i++) {
         float cw = f.width(QUICK[i], Font.SMALL) + 12.0F;
         if (x + cw > w - 9.0F) {
            break;
         }
         boolean hot = this.ui.hot(Z_QUICK, i);
         f.round(x, quickY, cw, 15.0F, 7.5F, hot ? Theme.SURFACE4 : Theme.SURFACE2);
         f.text(QUICK[i], x + 6.0F, quickY + 4.0F, signal ? Theme.TEXT2 : Theme.TEXT3, Font.SMALL);
         if (signal) {
            f.zone(Z_QUICK, x, quickY, cw, 15.0F, i);
         }
         x += cw + 4.0F;
      }
      // the field: camera (a selfie for them), emoji, the text, send
      f.round(9.0F, fieldY, w - 18.0F, fieldH, 11.0F, Theme.SURFACE2);
      boolean camHot = this.ui.hot(Z_CAMERA, 0L);
      G.CAMERA.draw(f, 21.0F, fieldY + fieldH / 2.0F, 12.0F, !signal ? Theme.TEXT3 : (camHot ? Theme.TEXT : Theme.BLAZE));
      if (signal) {
         f.zone(Z_CAMERA, 11.0F, fieldY, 20.0F, fieldH);
      }
      boolean emoHot = this.ui.hot(Z_EMOJI, 0L);
      if (this.emojiOpen) {
         f.circle(39.0F, fieldY + fieldH / 2.0F, 8.0F, Theme.SURFACE4);
      }
      Emoji.draw(f, 0, 33.0F, fieldY + fieldH / 2.0F - 6.0F, 12.0F);
      if (!emoHot && !this.emojiOpen) {
         f.circle(39.0F, fieldY + fieldH / 2.0F, 6.5F, 0x40000000);
      }
      f.zone(Z_EMOJI, 31.0F, fieldY, 17.0F, fieldH);
      String d = this.draft.toString();
      float fx = 50.0F, fw = w - 18.0F - 41.0F - 26.0F;
      if (!signal) {
         f.text("No signal", fx, fieldY + 7.0F, Theme.TEXT3, Font.BODY);
      } else if (d.isEmpty()) {
         f.text(this.txt.fit(f, "Text " + this.peer, fw, Font.BODY), fx, fieldY + 7.0F, Theme.TEXT3, Font.BODY);
      } else {
         // show the end of a long draft
         String shownText = d;
         while (shownText.length() > 1 && Emoji.width(f, shownText, Font.BODY, 10.0F) > fw) {
            shownText = shownText.substring(1);
         }
         Emoji.line(f, shownText, fx, fieldY + 7.0F, fw + 4.0F, Theme.TEXT, Font.BODY);
      }
      if (signal && (this.act.millis() / 500L) % 2L == 0L) {
         float cx = fx + Math.min(fw, d.isEmpty() ? 0.0F : Emoji.width(f, d, Font.BODY, 10.0F)) + 0.5F;
         f.fill(cx, fieldY + 5.0F, 1.0F, 12.0F, Theme.BLAZE);
      }
      boolean can = signal && !d.isBlank();
      boolean sendHot = this.ui.hot(Z_SEND, 0L);
      f.circle(w - 21.0F, fieldY + fieldH / 2.0F, 9.0F, can ? (sendHot ? 0xFFF08A3A : Theme.BLAZE) : Theme.SURFACE4);
      G.ARROW.draw(f, w - 21.0F, fieldY + fieldH / 2.0F, 10.0F, can ? 0xFFFFFFFF : Theme.TEXT3);
      if (can) {
         f.zone(Z_SEND, w - 32.0F, fieldY, 22.0F, fieldH);
      }
      f.zone(Z_FIELD, fx, fieldY, fw, fieldH);
   }

   /** One text, drawn with its bottom at {@code bottom}; returns the top of the space it took. */
   private float bubble(Frame f, PhoneModel.Text t, float bottom, float w, float maxW, long now, int index) {
      String text = t.text();
      String when = ago(t.at(), now);
      long photo = photoOf(text);
      int big = photo == 0L ? Emoji.onlyEmoji(text) : 0;
      float y;
      if (big > 0) {
         // one to three emoji: big, no bubble
         float size = big == 1 ? 30.0F : 24.0F;
         float bw = big * (size + 2.0F);
         y = bottom - size - 9.0F;
         float bx = t.mine() ? w - 9.0F - bw : 9.0F;
         float k = this.ui.anim(0xB16E000L + index, 1.0F, 10.0F);
         float lx = bx;
         for (Emoji.Run r : Emoji.runs(text)) {
            if (r.emoji() >= 0) {
               float s = size * (0.6F + 0.4F * k);
               Emoji.draw(f, r.emoji(), lx + (size - s) / 2.0F, y + (size - s), s);
               lx += size + 2.0F;
            }
         }
         f.text(when, t.mine() ? bx + bw - f.width(when, Font.SMALL) : bx + 2.0F, y + size + 1.5F, Theme.TEXT3, Font.SMALL);
         return y;
      }
      int bg = t.mine() ? 0xFFD9661C : Theme.SURFACE3;
      int fg = t.mine() ? 0xFFFFF7EE : Theme.TEXT;
      if (photo != 0L) {
         PhoneModel.Pic p = this.m.photos.shared.apply(photo);
         float pa = p == null ? 16.0F / 9.0F : p.w() / (float)Math.max(1, p.h());
         float pw = maxW, phh = Math.min(maxW * 1.2F, pw / pa);
         pw = phh * pa;
         String cap = caption(text);
         float ch = cap.isEmpty() ? 0.0F : Emoji.paraHeight(f, cap, pw - 8.0F, Font.BODY) + 6.0F;
         float bw = pw + 6.0F, bh = phh + 6.0F + ch;
         y = bottom - bh - 9.0F;
         float bx = t.mine() ? w - 9.0F - bw : 9.0F;
         f.round(bx, y, bw, bh, 9.0F, bg);
         if (p != null) {
            f.image(p.handle(), bx + 3.0F, y + 3.0F, pw, phh, 0.0F, 0.0F, 1.0F, 1.0F, 0xFFFFFFFF);
         } else {
            f.fill(bx + 3.0F, y + 3.0F, pw, phh, 0xFF101412);
            boolean gone = this.m.photos.sharedGone.test(photo);
            G.CAMERA.draw(f, bx + 3.0F + pw / 2.0F, y + 3.0F + phh / 2.0F - 5.0F, 16.0F, 0x50FFFFFF);
            f.center(gone ? "Photo expired" : "Loading photo…", bx + 3.0F + pw / 2.0F, y + 3.0F + phh / 2.0F + 6.0F, Theme.TEXT3, Font.SMALL);
         }
         if (!cap.isEmpty()) {
            Emoji.para(f, cap, bx + 6.0F, y + phh + 7.0F, pw - 8.0F, fg, Font.BODY);
         }
         if (p != null) {
            f.zone(Z_PHOTO, bx, y, bw, phh + 6.0F, photo);
         }
         f.text(when, t.mine() ? bx + bw - f.width(when, Font.SMALL) : bx + 2.0F, y + bh + 1.5F, Theme.TEXT3, Font.SMALL);
         return y;
      }
      float th = Emoji.paraHeight(f, text, maxW - 14.0F, Font.BODY);
      float bh = th + 10.0F;
      float tw = Math.min(maxW - 14.0F, Emoji.paraWidth(f, text, maxW - 14.0F, Font.BODY));
      float bw = Math.max(26.0F, tw + 14.0F);
      y = bottom - bh - 9.0F;
      float bx = t.mine() ? w - 9.0F - bw : 9.0F;
      f.round(bx, y, bw, bh, 9.0F, bg);
      // a small tail on the bubble
      if (t.mine()) {
         f.triangle(bx + bw - 6.0F, y + bh - 6.0F, bx + bw + 2.5F, y + bh + 0.5F, bx + bw - 12.0F, y + bh, bg);
      } else {
         f.triangle(bx + 6.0F, y + bh - 6.0F, bx - 2.5F, y + bh + 0.5F, bx + 12.0F, y + bh, bg);
      }
      Emoji.para(f, text, bx + 7.0F, y + 5.0F, maxW - 14.0F, fg, Font.BODY);
      f.text(when, t.mine() ? bx + bw - f.width(when, Font.SMALL) : bx + 2.0F, y + bh + 1.5F, Theme.TEXT3, Font.SMALL);
      return y;
   }

   /** [1.4.0] The emoji board: 8 x 4, a tap adds one to the draft. */
   private void emojiBoard(Frame f, float w, float y, float h) {
      f.clip(0.0F, y, w, h);
      f.round(6.0F, y, w - 12.0F, 74.0F, 10.0F, Theme.SURFACE);
      float cell = (w - 20.0F) / 8.0F;
      for (int i = 0; i < Emoji.NAMES.length; i++) {
         float cx = 10.0F + (i % 8) * cell, cy = y + 3.0F + (i / 8) * 17.5F;
         boolean hot = this.ui.hot(Z_EMO, i);
         if (hot) {
            f.round(cx, cy, cell, 17.0F, 5.0F, Theme.SURFACE3);
         }
         float s = hot ? 15.0F : 13.0F;
         Emoji.draw(f, i, cx + (cell - s) / 2.0F, cy + (17.0F - s) / 2.0F, s);
         f.zone(Z_EMO, cx, cy, cell, 17.0F, i);
      }
      f.unclip();
   }

   /** [1.4.0] A shared photo, sideways and as big as the phone allows. */
   private void photo(Frame f, float w, float h) {
      f.fill(0.0F, 0.0F, w, h, 0xFF000000);
      PhoneModel.Pic p = this.m.photos.shared.apply(this.photoId);
      if (p != null) {
         float pa = p.w() / (float)Math.max(1, p.h());
         float pw = Math.min(w - 8.0F, (h - 8.0F) * pa), phh = pw / pa;
         f.image(p.handle(), (w - pw) / 2.0F, (h - phh) / 2.0F, pw, phh, 0.0F, 0.0F, 1.0F, 1.0F, 0xFFFFFFFF);
      }
      Ui.iconButton(this.ui, f, Z_PBACK, 0L, 16.0F, 16.0F, 8.5F, G.BACK, 0x90000000, Theme.TEXT, true);
      f.zone(Z_PBACK, 0.0F, 0.0F, w, h);
   }

   private void pick(Frame f, float w, float h) {
      f.fill(0.0F, 0.0F, w, h, Theme.BG);
      float y = Ui.bar(this.ui, f, "New message", w, true);
      float y0 = this.beginScroll(f, y, w, h);
      y = y0;
      if (this.pickable.isEmpty()) {
         Ui.empty(this.ui, f, G.PERSON, "Nobody to text yet", "Other hunters on this server show up here once they are online or have used a Field Phone.",
            w / 2.0F, y + 10.0F, w - 40.0F);
         y += 110.0F;
      }
      for (int i = 0; i < this.pickable.size(); i++) {
         String p = this.pickable.get(i);
         boolean on = this.online(p);
         y += Ui.row(this.ui, f, Z_PICK, i, 12.0F, y, w - 24.0F, G.PERSON, on ? Theme.MOSS : Theme.TEXT3, p, on ? "Online now" : "Away · gets it later", "",
            Theme.TEXT3, true) + 2.0F;
      }
      this.endScroll(f, y0, y, w, h);
   }

   private void openThread(String p) {
      this.peer = p;
      this.draft.setLength(0);
      if (this.page() == THREAD) {
         this.replace(THREAD);
      } else {
         this.push(THREAD);
      }
      this.act.messageRead(p);
   }

   private void send(String text) {
      if (this.m.signal <= 0 || text == null || text.isBlank() || this.peer.isEmpty()) {
         return;
      }
      text = Emoji.shortcuts(text);
      this.act.message(this.peer, text.length() > MAX ? text.substring(0, MAX) : text);
   }

   @Override
   public void tap(int id, long data) {
      switch (id) {
         case Z_NEW -> this.push(PICK);
         case Z_THREAD -> {
            if (data >= 0 && data < this.peers.size()) {
               this.openThread(this.peers.get((int)data));
            }
         }
         case Z_PICK -> {
            if (data >= 0 && data < this.pickable.size()) {
               String p = this.pickable.get((int)data);
               // from the picker straight into the thread (Back then returns to the list)
               this.back();
               this.openThread(p);
            }
         }
         case Z_QUICK -> {
            if (data >= 0 && data < QUICK.length) {
               this.send(QUICK[(int)data]);
            }
         }
         case Z_SPOT -> this.send("My spot: " + Math.round(this.m.x) + ", " + Math.round(this.m.y) + ", " + Math.round(this.m.z));
         case Z_CAMERA -> this.act.camera(true, this.peer);
         case Z_EMOJI -> this.emojiOpen = !this.emojiOpen;
         case Z_EMO -> {
            if (data >= 0 && data < Emoji.NAMES.length) {
               String tok = Emoji.token((int)data);
               if (this.draft.length() + tok.length() + 1 <= MAX) {
                  if (this.draft.length() > 0 && this.draft.charAt(this.draft.length() - 1) != ' ') {
                     this.draft.append(' ');
                  }
                  this.draft.append(tok);
                  this.act.sound(PhoneActions.Sfx.KEY);
               }
            }
         }
         case Z_PHOTO -> {
            this.photoId = data;
            this.push(PHOTO);
         }
         case Z_PBACK -> this.back();
         case Z_SEND -> {
            this.send(this.draft.toString().trim());
            this.draft.setLength(0);
         }
         default -> {
         }
      }
   }

   @Override
   public boolean typed(char c) {
      if (this.page() != THREAD) {
         return false;
      }
      if (c >= 32 && c != 127 && c != '§' && !Character.isSurrogate(c) && this.draft.length() < MAX) {
         this.draft.append(c);
         this.act.sound(PhoneActions.Sfx.KEY);
      }
      return true;
   }

   @Override
   public boolean key(int key, int mods) {
      if (this.page() != THREAD) {
         return false;
      }
      if (key == Ui.K_BACKSPACE) {
         if (this.draft.length() > 0) {
            int cut = this.draft.length() - 1;
            if (this.draft.charAt(cut) == ':') {
               int open = this.draft.lastIndexOf(":", cut - 1);
               if (open >= 0 && Emoji.index(this.draft.substring(open + 1, cut)) >= 0) {
                  cut = open;
               }
            }
            this.draft.setLength(cut);
         }
         return true;
      }
      if (key == Ui.K_ENTER) {
         this.send(this.draft.toString().trim());
         this.draft.setLength(0);
         return true;
      }
      return true;
   }
}
