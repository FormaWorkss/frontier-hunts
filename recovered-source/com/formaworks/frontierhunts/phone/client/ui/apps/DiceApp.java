package com.formaworks.frontierhunts.phone.client.ui.apps;

import com.formaworks.frontierhunts.phone.client.ui.Canvas.Font;
import com.formaworks.frontierhunts.phone.client.ui.Frame;
import com.formaworks.frontierhunts.phone.client.ui.G;
import com.formaworks.frontierhunts.phone.client.ui.PhoneActions;
import com.formaworks.frontierhunts.phone.client.ui.PhoneModel;
import com.formaworks.frontierhunts.phone.client.ui.Theme;
import com.formaworks.frontierhunts.phone.client.ui.Ui;
import com.formaworks.frontierhunts.phone.client.ui.Worker;
import com.formaworks.frontierhunts.phone.games.Dice;
import com.formaworks.frontierhunts.phone.games.DiceAi;
import com.formaworks.frontierhunts.phone.games.GameKind;
import java.util.Random;
import java.util.concurrent.Future;

/**
 * [phone] Hunter's Dice on a green felt tray: roll, hold, roll again, and fill the thirteen boxes. Solo for a high
 * score, against the AI (it rolls and thinks out loud), or online where the server rolls the dice.
 */
public final class DiceApp extends GameApp {
   private static final int Z_SOLO = 100;
   private static final int Z_AI = 101;
   private static final int Z_DIE = 102;
   private static final int Z_ROLL = 103;
   private static final int Z_BOX = 104;
   private static final int Z_AGAIN = 105;
   private static final int Z_LOBBY = 106;
   private Dice game = new Dice(1);
   private int hold;
   private final Random rnd = new Random();
   private long rollAt = -10000L;
   private int seenRoll;
   private int seenSeq = -1;
   private DiceAi ai;
   private Future<Integer> aiJob;
   private long aiNextAt;
   private int aiPhase;
   private String aiSays = "";
   private int lastBox = -1;
   private int lastBoxPlayer;
   private long lastBoxAt;
   private boolean reported;

   public DiceApp() {
      super("dice", "Dice", 11, GameKind.DICE, 0xFF8CD46A);
   }

   @Override
   protected void drawPage(Frame f, int page, float w, float h) {
      switch (page) {
         case PLAY -> this.play(f, w, h);
         case PICK -> this.pick(f, w, h);
         default -> this.lobby(f, w, h);
      }
   }

   private void lobby(Frame f, float w, float h) {
      f.fill(0.0F, 0.0F, w, h, Theme.BG);
      float y = this.hero(f, w, "Five dice, three rolls, thirteen boxes. Chase the 63-point bonus and the Grand slam.", G.DIE);
      float y0 = this.beginScroll(f, y, w, h);
      y = y0;
      y += this.modeCard(f, Z_SOLO, y, w, G.PERSON, "Solo", "Play the whole card and beat your best score.", "Best " + this.m.games.diceBest) + 6.0F;
      int[] st = this.m.games.stats;
      int base = GameKind.stat(GameKind.DICE, GameKind.MODE_AI, 0);
      y += this.modeCard(f, Z_AI, y, w, G.CHIP, "Play the AI", "Take turns with the dice-playing AI.", st[base] + "W " + st[base + 1] + "L", 22.0F);
      this.levelPicker(f, y, w);
      y += 6.0F;
      y = this.onlineSection(f, y + 2.0F, w);
      this.endScroll(f, y0, y, w, h);
   }

   // ------------------------------------------------------------------------------------------------ setting up

   private void start(int mode) {
      this.mode = mode;
      this.session = 0L;
      this.game = new Dice(mode == GameKind.MODE_AI ? 2 : 1);
      this.hold = 0;
      this.ai = mode == GameKind.MODE_AI ? new DiceAi(this.level, this.act.millis()) : null;
      this.aiPhase = 0;
      this.aiSays = "";
      this.lastBox = -1;
      this.reported = false;
      this.cancelAi();
   }

   @Override
   protected void startOnline() {
      this.seenSeq = -1;
      this.game = new Dice(2);
      this.hold = 0;
      this.ai = null;
      this.lastBox = -1;
      this.reported = false;
      this.cancelAi();
      this.syncOnline();
   }

   private void cancelAi() {
      if (this.aiJob != null) {
         this.aiJob.cancel(false);
         this.aiJob = null;
      }
   }

   private void syncOnline() {
      PhoneModel.Online o = this.online();
      if (o == null || o.seq == this.seenSeq || o.state.length < 2) {
         return;
      }
      this.seenSeq = o.seq;
      int[] s = new int[o.state.length - 1];
      System.arraycopy(o.state, 1, s, 0, s.length);
      Dice d = new Dice(2);
      if (d.restore(s)) {
         // a box filled since last time: flash it
         for (int p = 0; p < 2; p++) {
            for (int c = 0; c < Dice.CATS; c++) {
               if (d.scores[p][c] >= 0 && this.game.scores[p][c] < 0) {
                  this.lastBox = c;
                  this.lastBoxPlayer = p;
                  this.lastBoxAt = this.act.millis();
                  this.act.sound(PhoneActions.Sfx.DICE_SCORE);
               }
            }
         }
         if (d.rollSerial != this.game.rollSerial && d.rollsLeft < 3) {
            this.rollAt = this.act.millis();
            this.act.sound(PhoneActions.Sfx.DICE_ROLL);
         }
         if (d.turn != this.game.turn || d.rollsLeft == 3) {
            this.hold = 0;
         } else if (d.turn != o.me) {
            this.hold = d.held;
         }
         this.game = d;
      }
   }

   private int me() {
      PhoneModel.Online o = this.mode == GameKind.MODE_ONLINE ? this.online() : null;
      return o == null ? 0 : o.me;
   }

   private boolean myTurn() {
      if (this.game.over()) {
         return false;
      }
      if (this.mode == GameKind.MODE_ONLINE) {
         PhoneModel.Online o = this.online();
         return o != null && o.status == 0 && this.game.turn == o.me;
      }
      return this.game.turn == 0;
   }

   private boolean rolling() {
      return this.act.millis() - this.rollAt < 650L;
   }

   // ------------------------------------------------------------------------------------------------ the clock

   @Override
   public void tick() {
      super.tick();
      if (this.page() != PLAY) {
         return;
      }
      if (this.mode == GameKind.MODE_ONLINE) {
         this.syncOnline();
         return;
      }
      if (this.mode == GameKind.MODE_AI && this.game.turn == 1 && !this.game.over()) {
         this.aiStep();
      }
      if (this.game.over() && !this.reported) {
         this.reported = true;
         if (this.mode == GameKind.MODE_SOLO) {
            int t = this.game.total(0);
            this.act.gameStat(GameKind.DICE, GameKind.MODE_SOLO, t);
            if (t > this.m.games.diceBest) {
               this.m.games.diceBest = t;
            }
            this.act.sound(PhoneActions.Sfx.WIN);
         } else {
            int w = this.game.winner();
            this.act.gameStat(GameKind.DICE, GameKind.MODE_AI, w == 0 ? GameKind.WIN : (w == 1 ? GameKind.LOSS : GameKind.DRAW));
            this.act.sound(w == 0 ? PhoneActions.Sfx.WIN : PhoneActions.Sfx.LOSE);
         }
      }
   }

   private void aiStep() {
      long now = this.act.millis();
      if (now < this.aiNextAt || this.rolling()) {
         return;
      }
      if (this.aiJob != null) {
         if (!this.aiJob.isDone()) {
            return;
         }
         int mask;
         try {
            mask = this.aiJob.get();
         } catch (Exception e) {
            mask = 31;
         }
         this.aiJob = null;
         if (mask == 31 || this.game.rollsLeft == 0) {
            this.aiScore();
         } else {
            this.hold = mask;
            this.aiSays = mask == 0 ? "Rerolls everything" : "Keeps " + this.describe(mask);
            this.aiNextAt = now + 750L;
            this.aiPhase = 2;
         }
         return;
      }
      if (this.game.rollsLeft == 3 || this.aiPhase == 2) {
         this.game.roll(this.game.rollsLeft == 3 ? 0 : this.hold, this.rnd);
         this.rollAt = now;
         this.act.sound(PhoneActions.Sfx.DICE_ROLL);
         this.aiPhase = 1;
         this.aiNextAt = now + 900L;
         return;
      }
      if (this.game.rollsLeft == 0) {
         this.aiScore();
         return;
      }
      Dice snapshot = this.game.copy();
      DiceAi brain = this.ai;
      this.aiSays = "Thinking…";
      this.aiJob = Worker.submit(() -> brain.stop(snapshot) ? 31 : brain.hold(snapshot));
   }

   private void aiScore() {
      int box = this.ai.box(this.game);
      int pts = this.game.value(box);
      if (this.game.score(box)) {
         this.lastBox = box;
         this.lastBoxPlayer = 1;
         this.lastBoxAt = this.act.millis();
         this.aiSays = Dice.NAMES[box] + " for " + pts;
         this.act.sound(PhoneActions.Sfx.DICE_SCORE);
      }
      this.hold = 0;
      this.aiPhase = 0;
      this.aiNextAt = this.act.millis() + 600L;
   }

   private String describe(int mask) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < 5; i++) {
         if ((mask & 1 << i) != 0) {
            if (sb.length() > 0) {
               sb.append(' ');
            }
            sb.append(this.game.dice[i]);
         }
      }
      return sb.toString();
   }

   // ------------------------------------------------------------------------------------------------ drawing

   private void play(Frame f, float w, float h) {
      f.fill(0.0F, 0.0F, w, h, 0xFF0F1410);
      String title = this.mode == GameKind.MODE_SOLO ? "Solo" : (this.mode == GameKind.MODE_AI ? "vs AI · " + LEVELS[this.level] : "vs " + this.opponent(this.online()));
      float y = Ui.bar(this.ui, f, title, w, true);
      int me = this.me();
      // totals
      if (this.game.players == 2) {
         float bw = (w - 18.0F - 6.0F) / 2.0F;
         for (int p = 0; p < 2; p++) {
            int who = p == 0 ? me : 1 - me;
            float x = 9.0F + p * (bw + 6.0F);
            boolean turn = this.game.turn == who && !this.game.over();
            f.round(x, y, bw, 22.0F, 8.0F, turn ? Theme.withAlpha(Theme.shade(this.accent, 0.35F), 255) : Theme.SURFACE2);
            String name = p == 0 ? "You" : (this.mode == GameKind.MODE_AI ? "AI" : this.opponent(this.online()));
            f.text(this.txt.fit(f, name, bw - 34.0F, Font.SMALL), x + 6.0F, y + 7.0F, turn ? Theme.TEXT : Theme.TEXT2, Font.SMALL);
            f.right(Integer.toString(this.game.total(who)), x + bw - 6.0F, y + 5.0F, Theme.TEXT, Font.STRONG);
         }
      } else {
         f.round(9.0F, y, w - 18.0F, 22.0F, 8.0F, Theme.SURFACE2);
         f.text("Score", 15.0F, y + 7.0F, Theme.TEXT2, Font.SMALL);
         f.right(Integer.toString(this.game.total(0)), w - 15.0F, y + 5.0F, Theme.TEXT, Font.STRONG);
         f.text("Best " + this.m.games.diceBest, 60.0F, y + 7.0F, Theme.TEXT3, Font.SMALL);
      }
      y += 27.0F;
      y = this.tray(f, w, y);
      y = this.rollBar(f, w, y);
      this.card(f, w, h, y);
      if (this.game.over()) {
         this.overCard(f, w, h);
      }
   }

   private float tray(Frame f, float w, float y) {
      float th = 50.0F;
      f.round(9.0F, y, w - 18.0F, th, 10.0F, 0xFF4A2E1A);
      f.round(11.0F, y + 2.0F, w - 22.0F, th - 4.0F, 8.0F, 0xFF1F5A34);
      f.gradient(11.0F, y + 2.0F, w - 22.0F, th - 4.0F, 0x14FFFFFF, 0x30000000);
      boolean rolling = this.rolling();
      long now = this.act.millis();
      float ds = 21.0F;
      float gap = (w - 22.0F - ds * 5.0F) / 6.0F;
      boolean mine = this.myTurn() && this.game.rollsLeft < 3 && this.game.rollsLeft > 0 && !rolling;
      for (int i = 0; i < 5; i++) {
         boolean held = (this.hold & 1 << i) != 0 && this.game.rollsLeft < 3;
         float x = 11.0F + gap + i * (ds + gap);
         float dy = y + (th - ds) / 2.0F + (held ? -6.0F : 2.0F);
         int face = this.game.dice[i];
         if (rolling && !held) {
            float t = (now - this.rollAt) / 650.0F;
            face = 1 + (int)((now / 60L + i * 3L) % 6L);
            dy += (float)(Math.abs(Math.sin(t * 9.0 + i)) * -6.0 * (1.0 - t));
            x += (float)(Math.sin(t * 13.0 + i * 2.0) * 2.0 * (1.0 - t));
         }
         if (this.game.rollsLeft == 3 && !rolling) {
            continue;
         }
         this.die(f, x, dy, ds, face, held);
         if (held) {
            f.center("HELD", x + ds / 2.0F, y + th - 9.0F, 0xE0FFFFFF, Font.SMALL);
         }
         if (mine) {
            f.zone(Z_DIE, x - 2.0F, y, ds + 4.0F, th, i);
         }
      }
      if (this.game.rollsLeft == 3 && !rolling && this.myTurn()) {
         G.DIE.draw(f, w / 2.0F, y + th / 2.0F - 6.0F, 16.0F, 0x50FFFFFF);
         f.center("Roll to start your turn", w / 2.0F, y + th / 2.0F + 6.0F, 0xC0FFFFFF, Font.SMALL);
      }
      return y + th + 6.0F;
   }

   private void die(Frame f, float x, float y, float s, int face, boolean held) {
      f.round(x + 0.5F, y + 1.5F, s, s, 5.0F, 0x60000000);
      f.round(x, y, s, s, 5.0F, held ? 0xFFFFE9C8 : 0xFFF6F2EA);
      f.round(x, y + s * 0.6F, s, s * 0.4F, 5.0F, held ? 0xFFF2D8B0 : 0xFFE8E2D6);
      f.round(x, y, s, s * 0.7F, 5.0F, held ? 0xFFFFE9C8 : 0xFFF6F2EA);
      if (held) {
         f.ring(x + s / 2.0F, y + s / 2.0F, s * 0.72F, 1.2F, 0xFFFF7A1F);
      }
      if (face <= 0) {
         G.DIE.draw(f, x + s / 2.0F, y + s / 2.0F, s * 0.5F, 0x30000000);
         return;
      }
      int[][] layout = PIPS[face];
      float r = s * 0.085F;
      for (int[] p : layout) {
         f.circle(x + s / 2.0F + p[0] * s * 0.25F, y + s / 2.0F + p[1] * s * 0.25F, r, face == 1 ? 0xFFC0302A : 0xFF1C241E);
      }
   }

   private static final int[][][] PIPS = {{}, {{0, 0}}, {{-1, -1}, {1, 1}}, {{-1, -1}, {0, 0}, {1, 1}}, {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}},
      {{-1, -1}, {1, -1}, {0, 0}, {-1, 1}, {1, 1}}, {{-1, -1}, {1, -1}, {-1, 0}, {1, 0}, {-1, 1}, {1, 1}}};

   private float rollBar(Frame f, float w, float y) {
      boolean can = this.myTurn() && this.game.canRoll() && !this.rolling();
      String label = this.game.rollsLeft == 3 ? "Roll" : "Roll again";
      Ui.button(this.ui, f, Z_ROLL, 0L, 9.0F, y, w - 60.0F, 20.0F, label, Ui.FILLED, can);
      // rolls left pips
      for (int i = 0; i < 3; i++) {
         float cx = w - 40.0F + i * 11.0F;
         f.circle(cx, y + 10.0F, 3.6F, i < this.game.rollsLeft ? this.accent : Theme.SURFACE3);
      }
      y += 24.0F;
      String hint;
      int col = Theme.TEXT3;
      if (this.game.over()) {
         hint = "Card complete";
      } else if (!this.myTurn()) {
         hint = this.mode == GameKind.MODE_AI ? "AI: " + this.aiSays : "Waiting for " + this.opponent(this.online());
         PhoneModel.Online o = this.online();
         if (o != null && o.opponentAway) {
            hint = this.opponent(o) + " left · waiting " + o.awaySeconds + " s";
            col = Theme.YELLOW;
         }
      } else if (this.game.rollsLeft == 3) {
         hint = "Your turn";
         col = Theme.MOSS;
      } else if (this.game.rollsLeft == 0) {
         hint = "Pick a box to score";
         col = this.accent;
      } else {
         hint = "Tap dice to hold them, then roll, or pick a box";
      }
      f.center(this.txt.fit(f, hint, w - 18.0F, Font.SMALL), w / 2.0F, y, col, Font.SMALL);
      return y + 12.0F;
   }

   private void card(Frame f, float w, float h, float top) {
      float y0 = this.beginScroll(f, top, w, h);
      float y = y0;
      int me = this.me();
      boolean two = this.game.players == 2;
      boolean choosing = this.myTurn() && this.game.rollsLeft < 3 && !this.rolling();
      long now = this.act.millis();
      for (int c = 0; c < Dice.CATS; c++) {
         if (c == 6) {
            // the upper subtotal and bonus
            float rh = 12.0F;
            f.text("Upper " + this.game.upper(me) + " / 63", 14.0F, y + 2.5F, Theme.TEXT3, Font.SMALL);
            f.right(this.game.bonus(me) > 0 ? "Bonus +35" : "Bonus at 63", two ? w - 46.0F : w - 14.0F, y + 2.5F,
               this.game.bonus(me) > 0 ? Theme.MOSS : Theme.TEXT3, Font.SMALL);
            if (two) {
               f.right(this.game.bonus(1 - me) > 0 ? "+35" : this.game.upper(1 - me) + "", w - 14.0F, y + 2.5F, Theme.TEXT3, Font.SMALL);
            }
            y += rh + 2.0F;
         }
         float rh = 15.0F;
         boolean open = this.game.scores[me][c] < 0;
         boolean can = choosing && this.game.canScore(c);
         boolean hot = can && this.ui.hot(Z_BOX, c);
         boolean flash = this.lastBox == c && now - this.lastBoxAt < 1200L;
         int bg = hot ? Theme.SURFACE3 : (c % 2 == 0 ? Theme.SURFACE : 0xFF131815);
         if (flash) {
            bg = Theme.mix(bg, this.accent, 0.35F * (1.0F - (now - this.lastBoxAt) / 1200.0F));
         }
         f.round(9.0F, y, w - 18.0F, rh, 4.0F, bg);
         f.text(Dice.NAMES[c], 14.0F, y + 3.5F, open ? Theme.TEXT : Theme.TEXT2, Font.SMALL);
         float vx = two ? w - 46.0F : w - 14.0F;
         if (!open) {
            f.right(Integer.toString(this.game.scores[me][c]), vx, y + 3.0F, Theme.TEXT, Font.STRONG);
         } else if (can) {
            int v = this.game.value(c);
            f.right(Integer.toString(v), vx, y + 3.0F, v > 0 ? this.accent : Theme.TEXT3, Font.STRONG);
         } else {
            f.right("–", vx, y + 3.0F, Theme.TEXT3, Font.STRONG);
         }
         if (two) {
            int ov = this.game.scores[1 - me][c];
            f.right(ov < 0 ? "–" : Integer.toString(ov), w - 14.0F, y + 3.0F, ov < 0 ? Theme.TEXT3 : Theme.TEXT2, Font.STRONG);
         }
         if (can) {
            f.zone(Z_BOX, 9.0F, y, w - 18.0F, rh, c);
         }
         y += rh + 2.0F;
      }
      if (this.game.bonus5[me] > 0 || two && this.game.bonus5[1 - me] > 0) {
         f.text("Grand slam bonus", 14.0F, y + 2.0F, Theme.GOLD, Font.SMALL);
         f.right("+" + this.game.bonus5[me] * 100, two ? w - 46.0F : w - 14.0F, y + 2.0F, Theme.GOLD, Font.SMALL);
         y += 12.0F;
      }
      this.endScroll(f, y0, y, w, h);
   }

   private void overCard(Frame f, float w, float h) {
      int win = this.game.winner();
      int me = this.me();
      String head;
      if (this.game.players == 1) {
         head = this.game.total(0) >= this.m.games.diceBest ? "New best: " + this.game.total(0) + "!" : "Final score " + this.game.total(0);
      } else {
         head = win == 2 ? "A draw" : (win == me ? "You win!" : (this.mode == GameKind.MODE_AI ? "The AI wins" : this.opponent(this.online()) + " wins"));
      }
      float cw = w - 30.0F, ch = 64.0F, cx = 15.0F, cy = h / 2.0F - 20.0F;
      f.round(cx, cy + 2.0F, cw, ch, 12.0F, 0x90000000);
      f.round(cx, cy, cw, ch, 12.0F, 0xF21E2620);
      f.center(head, w / 2.0F, cy + 9.0F, win == me || this.game.players == 1 ? this.accent : Theme.TEXT, Font.MEDIUM);
      if (this.game.players == 2) {
         f.center(this.game.total(me) + " – " + this.game.total(1 - me), w / 2.0F, cy + 25.0F, Theme.TEXT2, Font.STRONG);
      }
      if (this.mode == GameKind.MODE_ONLINE) {
         this.onlineEndButtons(f, cx + 10.0F, cy + 40.0F, cw - 20.0F, 17.0F);
      } else {
         Ui.button(this.ui, f, Z_AGAIN, 0L, cx + 10.0F, cy + 40.0F, cw - 20.0F, 17.0F, "Play again", Ui.FILLED, true);
      }
   }

   // ------------------------------------------------------------------------------------------------ input

   @Override
   public void tap(int id, long data) {
      if (this.lobbyTap(id, data)) {
         return;
      }
      switch (id) {
         case Z_SOLO -> {
            this.start(GameKind.MODE_SOLO);
            this.push(PLAY);
         }
         case Z_AI -> {
            this.start(GameKind.MODE_AI);
            this.push(PLAY);
         }
         case Z_DIE -> {
            this.hold ^= 1 << (int)data;
            this.act.sound(PhoneActions.Sfx.DICE_HOLD);
         }
         case Z_ROLL -> {
            if (!this.myTurn() || !this.game.canRoll() || this.rolling()) {
               return;
            }
            if (this.mode == GameKind.MODE_ONLINE) {
               this.act.move(this.session, new int[]{0, this.game.rollsLeft == 3 ? 0 : this.hold});
               this.rollAt = this.act.millis();
               this.act.sound(PhoneActions.Sfx.DICE_ROLL);
               return;
            }
            this.game.roll(this.hold, this.rnd);
            this.rollAt = this.act.millis();
            this.act.sound(PhoneActions.Sfx.DICE_ROLL);
         }
         case Z_BOX -> {
            int c = (int)data;
            if (!this.myTurn() || !this.game.canScore(c)) {
               return;
            }
            if (this.mode == GameKind.MODE_ONLINE) {
               this.act.move(this.session, new int[]{1, c});
               return;
            }
            this.game.score(c);
            this.lastBox = c;
            this.lastBoxPlayer = 0;
            this.lastBoxAt = this.act.millis();
            this.hold = 0;
            this.act.sound(PhoneActions.Sfx.DICE_SCORE);
            this.aiNextAt = this.act.millis() + 700L;
         }
         case Z_AGAIN -> this.start(this.mode);
         case Z_LOBBY -> this.back();
         default -> {
         }
      }
   }

   @Override
   public boolean key(int key, int mods) {
      if (this.page() == PLAY) {
         if (key == Ui.K_SPACE || key == Ui.K_R) {
            this.tap(Z_ROLL, 0L);
            return true;
         }
         if (key >= Ui.K_1 && key <= Ui.K_5 && this.myTurn() && this.game.rollsLeft < 3 && this.game.rollsLeft > 0) {
            this.tap(Z_DIE, key - Ui.K_1);
            return true;
         }
      }
      return false;
   }

   @Override
   public boolean grabsKeys() {
      return this.page() == PLAY;
   }

   @Override
   public void closed() {
      this.cancelAi();
   }
}
