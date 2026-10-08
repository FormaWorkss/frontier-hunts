package com.formaworks.frontierhunts.phone.client.ui.apps;

import com.formaworks.frontierhunts.phone.client.ui.App;
import com.formaworks.frontierhunts.phone.client.ui.Canvas.Font;
import com.formaworks.frontierhunts.phone.client.ui.Frame;
import com.formaworks.frontierhunts.phone.client.ui.G;
import com.formaworks.frontierhunts.phone.client.ui.PhoneActions;
import com.formaworks.frontierhunts.phone.client.ui.PhoneModel;
import com.formaworks.frontierhunts.phone.client.ui.Theme;
import com.formaworks.frontierhunts.phone.client.ui.Ui;
import com.formaworks.frontierhunts.phone.games.GameKind;

/**
 * [phone] What the three games share: the lobby (play alone, against the AI at three strengths, or online against a
 * hunter on the server), invites in and out, games in progress, and the player picker.
 */
public abstract class GameApp extends App {
   static final int LOBBY = 0;
   static final int PLAY = 1;
   static final int PICK = 2;
   static final int Z_ACCEPT = 150;
   static final int Z_DECLINE = 151;
   static final int Z_RESUME = 152;
   static final int Z_CHALLENGE = 153;
   static final int Z_PLAYER = 154;
   static final int Z_LEVEL = 155;
   static final int Z_REMATCH = 156;
   static final int Z_TO_LOBBY = 157;
   static final String[] LEVELS = {"Easy", "Medium", "Hard"};
   protected final int game;
   protected final int accent;
   /** the online game being played, 0 for a local one */
   protected long session;
   protected int mode;
   protected int level = 1;
   private int refreshIn;

   protected GameApp(String id, String name, int icon, int game, int accent) {
      super(id, name, icon);
      this.game = game;
      this.accent = accent;
   }

   @Override
   public void opened() {
      this.act.refresh(PhoneActions.R_GAMES);
      this.refreshIn = 100;
   }

   @Override
   public void tick() {
      this.followRematch();
      if (this.page() != PLAY && --this.refreshIn <= 0) {
         this.act.refresh(PhoneActions.R_GAMES);
         this.refreshIn = 200;
      }
   }

   /** Which game this is ({@link GameKind}). */
   public int gameKind() {
      return this.game;
   }

   protected PhoneModel.Online online() {
      for (PhoneModel.Online o : this.m.games.online) {
         if (o.id == this.session) {
            return o;
         }
      }
      return null;
   }

   // ------------------------------------------------------------------------------------------------ lobby pieces

   /** A banner with the game's name and a big glyph. Returns y below. */
   protected float hero(Frame f, float w, String tagline, G glyph) {
      float y = Theme.STATUS_H;
      f.gradient(0.0F, 0.0F, w, 96.0F, Theme.shade(this.accent, 0.45F), Theme.BG);
      glyph.draw(f, w - 24.0F, y + 30.0F, 44.0F, Theme.withAlpha(this.accent, 46));
      f.text(GameKind.TITLES[this.game], Ui.PAD, y + 14.0F, Theme.TEXT, Font.LARGE);
      this.txt.para(f, tagline, Ui.PAD + 0.5F, y + 36.0F, w - 70.0F, Theme.TEXT2, Font.SMALL, 1.0F);
      return y + 66.0F;
   }

   /** A big tappable mode card. Returns its height. */
   protected float modeCard(Frame f, int id, float y, float w, G glyph, String title, String text, String right) {
      return this.modeCard(f, id, y, w, glyph, title, text, right, 0.0F);
   }

   /** A mode card with {@code extra} room at its bottom (for a strength picker). */
   protected float modeCard(Frame f, int id, float y, float w, G glyph, String title, String text, String right, float extra) {
      float th = this.txt.paraHeight(f, text, w - 64.0F, Font.SMALL, 1.0F);
      float ch = Math.max(40.0F, 22.0F + th + 6.0F) + extra;
      Ui.tapCard(this.ui, f, id, 0L, 9.0F, y, w - 18.0F, ch, Theme.SURFACE2);
      f.round(16.0F, y + 8.0F, 22.0F, 22.0F, 7.0F, Theme.withAlpha(Theme.shade(this.accent, 0.4F), 255));
      glyph.draw(f, 27.0F, y + 19.0F, 14.0F, this.accent);
      f.text(title, 45.0F, y + 7.0F, Theme.TEXT, Font.STRONG);
      if (!right.isEmpty()) {
         f.right(right, w - 16.0F, y + 8.0F, Theme.TEXT3, Font.SMALL);
      }
      this.txt.para(f, text, 45.0F, y + 20.0F, w - 64.0F, Theme.TEXT3, Font.SMALL, 1.0F);
      return ch;
   }

   /** The strength picker inside the bottom of a mode card that ends at {@code cardBottom}. */
   protected void levelPicker(Frame f, float cardBottom, float w) {
      Ui.segmented(this.ui, f, Z_LEVEL, 45.0F, cardBottom - 21.0F, w - 61.0F, 15.0F, LEVELS, this.level);
   }

   /** Invites, online games in progress, and the challenge button. Returns y below. */
   protected float onlineSection(Frame f, float y, float w) {
      PhoneModel.Games g = this.m.games;
      Ui.section(f, "ONLINE", 12.0F, y, w);
      y += 11.0F;
      for (PhoneModel.Invite inv : g.invites) {
         if (inv.game() != this.game) {
            continue;
         }
         Ui.card(f, 9.0F, y, w - 18.0F, 46.0F, Theme.withAlpha(Theme.shade(this.accent, 0.3F), 255));
         G.PERSON.draw(f, 22.0F, y + 12.0F, 11.0F, this.accent);
         f.text(this.txt.fit(f, inv.from() + " challenges you", w - 50.0F, Font.STRONG), 32.0F, y + 7.0F, Theme.TEXT, Font.STRONG);
         float bw = (w - 18.0F - 18.0F) / 2.0F;
         Ui.button(this.ui, f, Z_ACCEPT, inv.id(), 15.0F, y + 22.0F, bw, 17.0F, "Play", Ui.GOOD, true);
         Ui.button(this.ui, f, Z_DECLINE, inv.id(), 21.0F + bw, y + 22.0F, bw, 17.0F, "Decline", Ui.TONAL, true);
         y += 52.0F;
      }
      for (PhoneModel.Online o : g.online) {
         if (o.game != this.game) {
            continue;
         }
         String opp = o.names[1 - o.me] == null ? "Opponent" : o.names[1 - o.me];
         String st;
         int sc;
         if (o.status != 0) {
            st = o.winner == o.me ? "You won" : (o.winner == 2 ? "Draw" : (o.winner < 0 ? "Ended" : "You lost"));
            sc = o.winner == o.me ? Theme.MOSS : Theme.TEXT3;
         } else if (o.opponentAway) {
            st = "Waiting for " + opp + " to come back";
            sc = Theme.YELLOW;
         } else if (this.game == GameKind.FLUSH) {
            st = o.startIn > 0 ? "Starting" : "In the race";
            sc = this.accent;
         } else {
            boolean mine = o.state.length > 0 && o.state[0] == o.me;
            st = mine ? "Your turn" : opp + "'s turn";
            sc = mine ? Theme.MOSS : Theme.TEXT3;
         }
         y += Ui.row(this.ui, f, Z_RESUME, o.id, 12.0F, y, w - 24.0F, G.GLOBE, this.accent, "vs " + opp, st, "", sc, true) + 2.0F;
      }
      boolean signal = this.m.signal > 0;
      Ui.button(this.ui, f, Z_CHALLENGE, 0L, 9.0F, y + 2.0F, w - 18.0F, 20.0F, signal ? "Challenge a hunter" : "No signal", Ui.TONAL, signal);
      return y + 28.0F;
   }

   protected void pick(Frame f, float w, float h) {
      f.fill(0.0F, 0.0F, w, h, Theme.BG);
      float y = Ui.bar(this.ui, f, "Challenge a hunter", w, true);
      PhoneModel m = this.m;
      int n = 0;
      float y0 = this.beginScroll(f, y, w, h);
      y = y0;
      for (int i = 0; i < m.players.size(); i++) {
         String p = m.players.get(i);
         if (p.equals(m.playerName)) {
            continue;
         }
         y += Ui.row(this.ui, f, Z_PLAYER, i, 12.0F, y, w - 24.0F, G.PERSON, this.accent, p, "Online now", "Invite", this.accent, false) + 2.0F;
         n++;
      }
      if (n == 0) {
         Ui.empty(this.ui, f, G.PERSON, "Nobody else is online", "Online games are played with other hunters on this server. They need a Field Phone too.",
            w / 2.0F, y + 10.0F, w - 40.0F);
         y += 110.0F;
      } else {
         y += this.txt.para(f, "They get your invite on their Field Phone and have a minute to answer.", 12.0F, y + 6.0F, w - 24.0F, Theme.TEXT3,
            Font.SMALL, 1.0F) + 12.0F;
      }
      this.endScroll(f, y0, y, w, h);
   }

   /** Handles the shared lobby taps; true when handled. */
   protected boolean lobbyTap(int id, long data) {
      switch (id) {
         case Z_ACCEPT -> {
            this.act.answerInvite(data, true);
            this.act.sound(PhoneActions.Sfx.SUCCESS);
            this.ui.toast("Starting the game…");
            return true;
         }
         case Z_DECLINE -> {
            this.act.answerInvite(data, false);
            this.act.sound(PhoneActions.Sfx.TAP);
            return true;
         }
         case Z_RESUME -> {
            this.session = data;
            this.mode = GameKind.MODE_ONLINE;
            this.startOnline();
            this.push(PLAY);
            return true;
         }
         case Z_CHALLENGE -> {
            this.push(PICK);
            return true;
         }
         case Z_PLAYER -> {
            if (data >= 0 && data < this.m.players.size()) {
               String p = this.m.players.get((int)data);
               this.act.invite(this.game, p);
               this.ui.toast("Invite sent to " + p);
               this.act.sound(PhoneActions.Sfx.SUCCESS);
               this.back();
            }
            return true;
         }
         case Z_LEVEL -> {
            this.level = (int)data;
            this.act.sound(PhoneActions.Sfx.TAP);
            return true;
         }
         case Z_REMATCH -> {
            PhoneModel.Online o = this.online();
            if (o != null && o.status != 0 && !o.rematchMine && !o.opponentGone && o.next == 0L) {
               this.act.rematch(o.id);
               this.act.sound(o.rematchTheirs ? PhoneActions.Sfx.SUCCESS : PhoneActions.Sfx.TAP);
               if (!o.rematchTheirs) {
                  this.ui.toast("Rematch asked: waiting for " + this.opponent(o));
               }
            }
            return true;
         }
         case Z_TO_LOBBY -> {
            this.back();
            return true;
         }
         default -> {
            return false;
         }
      }
   }

   /** An online game was opened from the lobby (or an accepted invite arrived): set up the board for it. */
   protected abstract void startOnline();

   /** The server started an online game of ours (an invite was accepted): open it. */
   public void onlineStarted(long id) {
      if (this.page() == PLAY && this.session == id) {
         return;
      }
      this.session = id;
      this.mode = GameKind.MODE_ONLINE;
      this.startOnline();
      if (this.page() == PLAY) {
         this.replace(PLAY);
      } else {
         this.push(PLAY);
      }
   }

   /**
    * The two buttons under a finished online game: Rematch (or "Accept rematch" when they asked, "Rematch asked" while
    * waiting, gone when they left) and Lobby. Spans {@code w} from {@code x}.
    */
   protected void onlineEndButtons(Frame f, float x, float y, float w, float h) {
      PhoneModel.Online o = this.online();
      float bw = (w - 6.0F) / 2.0F;
      String label;
      boolean on;
      int style;
      if (o == null || o.opponentGone || o.next != 0L) {
         label = o != null && o.next != 0L ? "Rematch started" : "Opponent left";
         on = false;
         style = Ui.TONAL;
      } else if (o.rematchMine) {
         label = "Rematch asked…";
         on = false;
         style = Ui.TONAL;
      } else if (o.rematchTheirs) {
         label = "Accept rematch";
         on = this.m.signal > 0;
         style = Ui.GOOD;
      } else {
         label = "Rematch";
         on = this.m.signal > 0;
         style = Ui.FILLED;
      }
      Ui.button(this.ui, f, Z_REMATCH, 0L, x, y, bw, h, label, style, on);
      Ui.button(this.ui, f, Z_TO_LOBBY, 0L, x + bw + 6.0F, y, bw, h, "Lobby", Ui.TONAL, true);
   }

   /** When a rematch of the game on screen has started, switch to it. Call from tick(). */
   protected void followRematch() {
      if (this.page() != PLAY || this.mode != GameKind.MODE_ONLINE) {
         return;
      }
      PhoneModel.Online o = this.online();
      if (o != null && o.next != 0L) {
         for (PhoneModel.Online n : this.m.games.online) {
            if (n.id == o.next && n.status == 0) {
               this.onlineStarted(n.id);
               return;
            }
         }
      }
   }

   protected String opponent(PhoneModel.Online o) {
      String n = o == null ? null : o.names[1 - o.me];
      return n == null ? "Opponent" : n;
   }
}
