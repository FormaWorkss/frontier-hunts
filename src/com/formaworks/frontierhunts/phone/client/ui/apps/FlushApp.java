package com.formaworks.frontierhunts.phone.client.ui.apps;

import com.formaworks.frontierhunts.phone.client.ui.Canvas.Font;
import com.formaworks.frontierhunts.phone.client.ui.Frame;
import com.formaworks.frontierhunts.phone.client.ui.G;
import com.formaworks.frontierhunts.phone.client.ui.PhoneActions;
import com.formaworks.frontierhunts.phone.client.ui.PhoneModel;
import com.formaworks.frontierhunts.phone.client.ui.Theme;
import com.formaworks.frontierhunts.phone.client.ui.Ui;
import com.formaworks.frontierhunts.phone.games.Flush;
import com.formaworks.frontierhunts.phone.games.GameKind;

/**
 * [phone] Flush!: the phone turns sideways for sixty seconds of wingshooting over a dawn marsh. Solo runs are replayed
 * by the server and ranked on its leaderboard; against the AI you shoot at the same birds as a marksman; online two
 * hunters race the same flight of birds.
 */
public final class FlushApp extends GameApp {
   private static final int Z_SOLO = 100;
   private static final int Z_AI = 101;
   private static final int Z_AGAIN = 102;
   private static final int Z_LOBBY = 103;
   private static final int COUNTDOWN = 60;
   private Flush game;
   private long seed;
   private long token;
   private boolean ranked;
   private int countdown;
   private long waitingSince;
   private boolean submitted;
   private long lastTickAt;
   private float mx = 50.0F, my = 20.0F;
   private int seenEvents;
   private int seenSeq = -1;
   private int progressIn;
   private float shake;
   private long protectedAt = -10000L;
   private String protectedText = "";

   public FlushApp() {
      super("flush", "Flush!", 12, GameKind.FLUSH, 0xFFF2A65E);
   }

   @Override
   protected boolean landscapePage(int page) {
      return page == PLAY;
   }

   @Override
   public boolean grabsKeys() {
      return this.page() == PLAY;
   }

   /** The crosshair replaces the mouse pointer over the marsh. */
   @Override
   public boolean hidesCursor() {
      return this.page() == PLAY && this.game != null && !this.game.over();
   }

   @Override
   protected void drawPage(Frame f, int page, float w, float h) {
      switch (page) {
         case PLAY -> this.play(f, w, h);
         case PICK -> this.pick(f, w, h);
         default -> this.lobby(f, w, h);
      }
   }

   // ------------------------------------------------------------------------------------------------ lobby

   private void lobby(Frame f, float w, float h) {
      f.fill(0.0F, 0.0F, w, h, Theme.BG);
      float y = this.hero(f, w, "Sixty seconds over the marsh. Lead your birds, mind the hens and the hawks.", G.DUCK);
      float y0 = this.beginScroll(f, y, w, h);
      y = y0;
      PhoneModel.Games g = this.m.games;
      y += this.modeCard(f, Z_SOLO, y, w, G.CUP, "Solo run", this.m.signal > 0 ? "Ranked on this server's leaderboard." : "No signal: an unranked run.",
         "Best " + g.flushBest) + 6.0F;
      int[] st = g.stats;
      int base = GameKind.stat(GameKind.FLUSH, GameKind.MODE_AI, 0);
      y += this.modeCard(f, Z_AI, y, w, G.CHIP, "Duel the AI", "Same birds, one marksman beside you. Whoever shoots first gets the bird.",
         st[base] + "W " + st[base + 1] + "L", 22.0F);
      this.levelPicker(f, y, w);
      y += 6.0F;
      // leaderboard
      Ui.section(f, "SERVER LEADERBOARD", 12.0F, y, w);
      y += 11.0F;
      if (g.leaderboard.isEmpty()) {
         f.text(this.m.signal > 0 ? "No runs yet: set the first score." : "Needs signal.", 12.0F, y, Theme.TEXT3, Font.SMALL);
         y += 14.0F;
      } else {
         Ui.card(f, 9.0F, y, w - 18.0F, g.leaderboard.size() * 13.0F + 8.0F);
         for (int i = 0; i < g.leaderboard.size(); i++) {
            PhoneModel.Score s = g.leaderboard.get(i);
            float ry = y + 4.0F + i * 13.0F;
            int col = s.me() ? this.accent : Theme.TEXT;
            f.text((i + 1) + ".", 15.0F, ry + 2.0F, i < 3 ? Theme.GOLD : Theme.TEXT3, Font.SMALL);
            f.text(this.txt.fit(f, s.name(), w - 90.0F, Font.STRONG), 30.0F, ry + 1.5F, col, Font.STRONG);
            f.right(Integer.toString(s.score()), w - 16.0F, ry + 1.5F, col, Font.STRONG);
         }
         y += g.leaderboard.size() * 13.0F + 14.0F;
      }
      y = this.onlineSection(f, y + 2.0F, w);
      this.endScroll(f, y0, y, w, h);
   }

   @Override
   public void opened() {
      super.opened();
      this.act.refresh(PhoneActions.R_BOARD);
   }

   // ------------------------------------------------------------------------------------------------ starting

   private void startSolo() {
      this.mode = GameKind.MODE_SOLO;
      this.session = 0L;
      this.game = null;
      this.submitted = false;
      this.ranked = this.m.signal > 0;
      this.m.games.flushToken = 0L;
      this.m.games.flushResult = "";
      if (this.ranked) {
         this.act.flushStart();
         this.waitingSince = this.act.millis();
      } else {
         this.begin(this.act.millis() ^ 0x5DEECE66DL, false);
      }
   }

   private void startAi() {
      this.mode = GameKind.MODE_AI;
      this.session = 0L;
      this.submitted = false;
      this.ranked = false;
      this.game = null;
      this.begin(this.act.millis() * 31L + 17L, true);
   }

   private void begin(long seed, boolean ai) {
      this.seed = seed;
      this.game = new Flush(seed, ai, this.level);
      this.countdown = COUNTDOWN;
      this.seenEvents = 0;
      this.progressIn = 10;
   }

   @Override
   protected void startOnline() {
      this.game = null;
      this.seenSeq = -1;
      this.submitted = false;
      this.syncOnline();
   }

   private void syncOnline() {
      PhoneModel.Online o = this.online();
      if (o == null || o.seq == this.seenSeq) {
         return;
      }
      this.seenSeq = o.seq;
      if (this.game == null && o.seed != 0L) {
         this.begin(o.seed, false);
         this.countdown = Math.max(0, o.startIn);
      }
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
      }
      if (this.game == null) {
         if (this.mode == GameKind.MODE_SOLO) {
            if (this.m.games.flushToken != 0L) {
               this.token = this.m.games.flushToken;
               this.begin(this.m.games.flushSeed, false);
            } else if (this.act.millis() - this.waitingSince > 2500L) {
               // no answer from the server: an unranked run
               this.ranked = false;
               this.begin(this.act.millis(), false);
            }
         }
         return;
      }
      this.lastTickAt = this.act.millis();
      if (this.countdown > 0) {
         this.countdown--;
         if (this.countdown % 20 == 0) {
            this.act.sound(this.countdown == 0 ? PhoneActions.Sfx.FLUSH_FLUSH : PhoneActions.Sfx.KEY);
         }
         return;
      }
      if (!this.game.over()) {
         this.game.step();
         this.events();
         if (this.mode == GameKind.MODE_ONLINE && --this.progressIn <= 0) {
            this.progressIn = 10;
            this.act.flushProgress(this.session, this.game.score[0], this.game.tick);
         }
         if (this.game.over()) {
            this.finish();
         }
      }
      this.shake = Math.max(0.0F, this.shake - 0.25F);
   }

   private void events() {
      for (; this.seenEvents < this.game.events.size(); this.seenEvents++) {
         Flush.Event e = this.game.events.get(this.seenEvents);
         switch (e.type()) {
            case Flush.E_HIT -> this.act.sound(PhoneActions.Sfx.FLUSH_HIT);
            case Flush.E_PROTECTED -> {
               this.act.sound(PhoneActions.Sfx.FLUSH_WARN);
               if (e.shooter() == 0) {
                  this.protectedAt = this.act.millis();
                  this.protectedText = e.text() + " " + e.points();
               }
            }
            default -> {
            }
         }
      }
      // flush cue when a new bird lifts out of the reeds
      for (Flush.Bird b : this.game.birds) {
         if (b.spawn == this.game.tick - 1 && b.kind != Flush.Kind.GOOSE && b.kind != Flush.Kind.HAWK && b.kind != Flush.Kind.DOVE) {
            this.act.sound(PhoneActions.Sfx.FLUSH_FLUSH);
            break;
         }
      }
   }

   private void finish() {
      if (this.submitted) {
         return;
      }
      this.submitted = true;
      int[] shots = this.game.shotLog();
      switch (this.mode) {
         case GameKind.MODE_SOLO -> {
            if (this.ranked && this.token != 0L) {
               this.act.flushSubmit(this.token, shots);
            } else if (this.game.score[0] > this.m.games.flushBest) {
               this.m.games.flushBest = this.game.score[0];
            }
            this.act.sound(PhoneActions.Sfx.WIN);
         }
         case GameKind.MODE_AI -> {
            int a = this.game.score[0], b = this.game.score[1];
            this.act.gameStat(GameKind.FLUSH, GameKind.MODE_AI, a > b ? GameKind.WIN : (a < b ? GameKind.LOSS : GameKind.DRAW));
            this.act.sound(a >= b ? PhoneActions.Sfx.WIN : PhoneActions.Sfx.LOSE);
         }
         default -> this.act.flushSubmit(this.session, shots);
      }
   }

   // ------------------------------------------------------------------------------------------------ the marsh

   private void play(Frame f, float w, float h) {
      float k = w / Flush.W;
      float fh = Flush.H * k;
      float oy = (h - fh) / 2.0F;
      long now = this.act.millis();
      float partial = this.game == null || this.countdown > 0 || this.game.over() ? 0.0F : Theme.clamp01((now - this.lastTickAt) / 50.0F);
      float sx = this.shake > 0.0F ? (float)Math.sin(now * 0.09) * this.shake : 0.0F;
      f.push();
      f.translate(sx, oy);
      this.scenery(f, w, fh, k, now);
      if (this.game != null) {
         float t = this.game.tick + partial;
         this.birds(f, k, t);
         this.reeds(f, w, fh, k, now, true);
         this.shots(f, k, t);
         this.floats(f, k, t);
      } else {
         this.reeds(f, w, fh, k, now, true);
      }
      f.pop();
      this.hud(f, w, h, now);
      if (this.game != null && !this.game.over() && this.countdown <= 0) {
         this.crosshair(f, k, oy);
      }
      if (this.game == null) {
         f.fill(0.0F, 0.0F, w, h, 0x80000000);
         f.center("Checking in with the server…", w / 2.0F, h / 2.0F - 4.0F, Theme.TEXT, Font.STRONG);
      } else if (this.countdown > 0) {
         f.fill(0.0F, 0.0F, w, h, 0x50000000);
         String c = Integer.toString(this.countdown / 20 + 1);
         float pulse = 1.0F + (this.countdown % 20) / 40.0F;
         f.push();
         f.translate(w / 2.0F, h / 2.0F);
         f.scale(pulse);
         f.center(c, 0.0F, -16.0F, Theme.TEXT, Font.HUGE);
         f.pop();
         f.center(this.mode == GameKind.MODE_ONLINE ? "Race against " + this.opponent(this.online()) : "Get ready", w / 2.0F, h / 2.0F + 22.0F,
            Theme.TEXT2, Font.STRONG);
      } else if (this.game.over()) {
         this.results(f, w, h);
      }
   }

   private void scenery(Frame f, float w, float fh, float k, long now) {
      // dawn sky
      f.gradient(0.0F, 0.0F, w, fh * 0.62F, 0xFF3B4A7A, 0xFFF2A86A);
      f.fill(0.0F, fh * 0.62F - 0.5F, w, fh * 0.4F, 0xFFF2A86A);
      // sun
      float sunX = w * 0.72F, sunY = fh * 0.60F;
      for (int i = 5; i >= 1; i--) {
         f.circle(sunX, sunY, 9.0F + i * 6.0F, 0x18FFE0A0);
      }
      f.circle(sunX, sunY, 10.0F, 0xFFFFE2A6);
      // far hills and the tree line
      int steps = 30;
      for (int i = 0; i < steps; i++) {
         float x0 = w * i / steps, x1 = w * (i + 1) / steps + 0.3F;
         float y0 = fh * 0.56F - (float)(Math.sin(i * 0.5) * 6.0 + Math.sin(i * 1.7) * 3.0);
         float y1 = fh * 0.56F - (float)(Math.sin((i + 1) * 0.5) * 6.0 + Math.sin((i + 1) * 1.7) * 3.0);
         f.quad(x0, y0, x1, y1, x1, fh, x0, fh, 0xFF7A5E6A);
      }
      for (int i = 0; i < 46; i++) {
         float x = i * w / 44.0F - 3.0F;
         float th = 8.0F + (i * 37 % 11);
         float base = fh * 0.66F;
         f.triangle(x, base - th, x + 3.2F, base, x - 3.2F, base, 0xFF4A3A44);
      }
      f.fill(0.0F, fh * 0.66F - 0.5F, w, fh * 0.1F, 0xFF4A3A44);
      // marsh water with the sun's reflection
      float wy = fh * 0.74F;
      f.gradient(0.0F, wy, w, fh - wy, 0xFFC88A6A, 0xFF5A4A5A);
      for (int i = 0; i < 9; i++) {
         float yy = wy + 2.0F + i * 2.6F;
         float ww = 18.0F - i;
         float xx = sunX - ww / 2.0F + (float)Math.sin(now / 400.0 + i) * 2.0F;
         f.fill(xx, yy, ww, 0.8F, 0x80FFE2A6);
      }
   }

   private void reeds(Frame f, float w, float fh, float k, long now, boolean front) {
      float base = fh;
      float cover = Flush.COVER * k;
      for (int i = 0; i < 70; i++) {
         float x = (i * 53 % 347) / 347.0F * w;
         float hgt = (fh - cover) + 4.0F + (i * 29 % 13);
         float sway = (float)Math.sin(now / 900.0 + i * 0.7) * 1.4F;
         int col = i % 3 == 0 ? 0xFF2E2420 : (i % 3 == 1 ? 0xFF3A2C24 : 0xFF46382A);
         f.line(x, base, x + sway, base - hgt, 1.1F, col);
         if (i % 4 == 0) {
            f.round(x + sway - 1.0F, base - hgt - 1.0F, 2.2F, 6.0F, 1.0F, 0xFF2A1E18);
         }
      }
      f.gradient(0.0F, fh - 6.0F, w, 6.0F, 0x00000000, 0x80000000);
   }

   private static final String[] KINDS = {"MALLARD", "TEAL", "PHEASANT", "GROUSE", "DOVE", "GOOSE", "HEN", "HAWK"};

   private void birds(Frame f, float k, float t) {
      for (Flush.Bird b : this.game.birds) {
         if (b.gone || t < b.spawn) {
            continue;
         }
         float x, y;
         int frame;
         if (b.hitAt >= 0) {
            float dt = t - b.hitAt;
            x = b.hitX + b.dir() * dt * 0.08F;
            y = b.hitY + 0.035F * dt * dt;
            if (y > Flush.COVER + 3.0F) {
               continue;
            }
            frame = 2;
         } else {
            x = b.x(t);
            y = b.y(t);
            frame = (int)((t - b.spawn) / 2.5F) % 2;
         }
         int col = b.kind.ordinal();
         float size = b.kind.radius * 2.0F * k * 1.9F;
         float u0 = col / 8.0F, u1 = (col + 1) / 8.0F;
         if (b.dir() < 0) {
            float tmp = u0;
            u0 = u1;
            u1 = tmp;
         }
         int tint = b.hitAt >= 0 && b.hitBy == 1 ? 0xFFFFD8B0 : 0xFFFFFFFF;
         f.sprite("flush", x * k - size / 2.0F, y * k - size / 2.0F, size, size, u0, frame / 3.0F, u1, (frame + 1) / 3.0F, tint);
      }
   }

   private void shots(Frame f, float k, float t) {
      for (int i = this.game.events.size() - 1; i >= 0; i--) {
         Flush.Event e = this.game.events.get(i);
         float age = t - e.tick();
         if (age > 12.0F) {
            break;
         }
         if (e.type() != Flush.E_SHOT) {
            continue;
         }
         float arrive = Flush.TRAVEL;
         float x = e.x() * k, y = e.y() * k;
         int col = e.shooter() == 0 ? 0xFFFFFFFF : 0xFFFF9A50;
         if (age < arrive) {
            // the pattern on its way: a small closing ring
            float r = Flush.PATTERN * k * (0.4F + 0.6F * age / arrive);
            f.ring(x, y, r, 0.8F, Theme.withAlpha(col, 120));
         } else {
            float a = 1.0F - (age - arrive) / 10.0F;
            float r = Flush.PATTERN * k * (1.0F + (age - arrive) * 0.05F);
            f.ring(x, y, r, 1.0F, Theme.withAlpha(col, (int)(200 * a)));
            for (int p = 0; p < 9; p++) {
               double ang = p * 2.39996 + e.tick();
               float pr = r * (float)Math.sqrt((p + 0.5) / 9.0);
               f.fill(x + (float)Math.cos(ang) * pr, y + (float)Math.sin(ang) * pr, 1.0F, 1.0F, Theme.withAlpha(col, (int)(220 * a)));
            }
         }
      }
   }

   private void floats(Frame f, float k, float t) {
      for (int i = this.game.events.size() - 1; i >= 0; i--) {
         Flush.Event e = this.game.events.get(i);
         float age = t - e.tick();
         if (age > 30.0F) {
            break;
         }
         if (e.type() != Flush.E_HIT && e.type() != Flush.E_DOUBLE && e.type() != Flush.E_PROTECTED) {
            continue;
         }
         float a = 1.0F - age / 30.0F;
         float x = e.x() * k, y = e.y() * k - age * 0.6F - 6.0F;
         int col = e.type() == Flush.E_PROTECTED ? Theme.RED : (e.shooter() == 1 ? 0xFFFF9A50 : (e.type() == Flush.E_DOUBLE ? Theme.YELLOW : Theme.TEXT));
         String s = e.type() == Flush.E_DOUBLE ? e.text() + " +" + e.points() : (e.points() > 0 ? "+" + e.points() : Integer.toString(e.points()));
         f.center(s, x + 0.5F, y + 0.5F, Theme.withAlpha(0, (int)(160 * a)), Font.STRONG);
         f.center(s, x, y, Theme.withAlpha(col, (int)(255 * a)), Font.STRONG);
      }
   }

   private void hud(Frame f, float w, float h, long now) {
      if (this.game == null) {
         return;
      }
      // score
      f.round(6.0F, 6.0F, 62.0F, 22.0F, 8.0F, 0x70000000);
      f.text("SCORE", 11.0F, 8.5F, 0xB0FFFFFF, Font.SMALL);
      f.text(Integer.toString(this.game.score[0]), 11.0F, 15.0F, Theme.TEXT, Font.STRONG);
      int mult = 4 + Math.min(4, this.game.streak[0]);
      if (mult > 4) {
         Ui.chip(f, "x" + (mult / 4) + (mult % 4 == 0 ? "" : "." + (mult % 4 * 25)), 44.0F, 15.0F, Theme.BLAZE, 0xFF1C1209);
      }
      // the clock
      int secs = this.game.secondsLeft();
      String t = "0:" + (secs < 10 ? "0" : "") + secs;
      if (secs >= 60) {
         t = "1:00";
      }
      f.round(w / 2.0F - 20.0F, 6.0F, 40.0F, 16.0F, 8.0F, 0x70000000);
      f.center(t, w / 2.0F, 9.5F, secs <= 10 ? Theme.RED : Theme.TEXT, Font.STRONG);
      // the rival
      String rival = null;
      int rs = 0;
      if (this.mode == GameKind.MODE_AI) {
         rival = "AI";
         rs = this.game.score[1];
      } else if (this.mode == GameKind.MODE_ONLINE) {
         PhoneModel.Online o = this.online();
         rival = this.opponent(o);
         rs = o == null ? 0 : o.opponentScore;
      }
      if (rival != null) {
         f.round(w - 76.0F, 6.0F, 70.0F, 22.0F, 8.0F, 0x70000000);
         f.text(this.txt.fit(f, rival.toUpperCase(java.util.Locale.ROOT), 60.0F, Font.SMALL), w - 71.0F, 8.5F, 0xFFFFB070, Font.SMALL);
         f.text(Integer.toString(rs), w - 71.0F, 15.0F, Theme.TEXT, Font.STRONG);
      }
      // shells
      boolean reloading = this.game.reloading(0);
      for (int i = 0; i < 2; i++) {
         boolean full = !reloading && i < this.game.shells[0];
         float x = w - 22.0F - i * 12.0F, y = h - 26.0F;
         f.round(x, y, 8.0F, 14.0F, 2.0F, full ? 0xFFC0302A : 0x40FFFFFF);
         f.round(x, y + 10.0F, 8.0F, 5.0F, 1.0F, full ? 0xFFD8B060 : 0x30FFFFFF);
      }
      if (reloading) {
         f.right("Reloading", w - 44.0F, h - 22.0F, 0xD0FFFFFF, Font.SMALL);
      }
      // a protected bird was shot
      long since = now - this.protectedAt;
      if (since < 1600L) {
         float a = 1.0F - since / 1600.0F;
         f.round(w / 2.0F - 60.0F, 26.0F, 120.0F, 16.0F, 8.0F, Theme.withAlpha(0x5A1410, (int)(230 * a)));
         f.center(this.protectedText, w / 2.0F, 30.0F, Theme.withAlpha(Theme.RED, (int)(255 * a)), Font.STRONG);
      }
   }

   private void crosshair(Frame f, float k, float oy) {
      float x = this.mx * k, y = this.my * k + oy;
      float r = Flush.PATTERN * k;
      boolean reloading = this.game.reloading(0);
      int col = reloading ? 0x80FFFFFF : 0xE8FFFFFF;
      f.ring(x, y, r, 1.0F, col);
      f.line(x - r - 4.0F, y, x - r + 2.0F, y, 1.0F, col);
      f.line(x + r - 2.0F, y, x + r + 4.0F, y, 1.0F, col);
      f.line(x, y - r - 4.0F, x, y - r + 2.0F, 1.0F, col);
      f.line(x, y + r - 2.0F, x, y + r + 4.0F, 1.0F, col);
      f.circle(x, y, 0.9F, Theme.BLAZE);
      if (reloading) {
         float p = 1.0F - (this.game.reloadUntil[0] - this.game.tick) / (float)Flush.RELOAD;
         f.arc(x, y, r + 3.0F, 1.6F, 0.0F, (float)(Math.PI * 2.0 * Theme.clamp01(p)), Theme.BLAZE);
      }
   }

   private void results(Frame f, float w, float h) {
      f.fill(0.0F, 0.0F, w, h, 0x90000000);
      float cw = 200.0F, ch = 118.0F, cx = (w - cw) / 2.0F, cy = (h - ch) / 2.0F;
      f.round(cx, cy, cw, ch, 12.0F, 0xF21E2022);
      int me = this.game.score[0];
      String head;
      int col = this.accent;
      if (this.mode == GameKind.MODE_AI) {
         int ai = this.game.score[1];
         head = me > ai ? "You beat the AI!" : (me < ai ? "The AI wins" : "A tie");
         col = me >= ai ? Theme.MOSS : Theme.TEXT;
      } else if (this.mode == GameKind.MODE_ONLINE) {
         PhoneModel.Online o = this.online();
         if (o == null || o.status == 0) {
            head = "Waiting for the result…";
            col = Theme.TEXT2;
         } else {
            head = o.winner == o.me ? "You win the race!" : (o.winner == 2 ? "A tie" : this.opponent(o) + " wins");
            col = o.winner == o.me ? Theme.MOSS : Theme.TEXT;
         }
      } else {
         head = "Run complete";
      }
      f.center(head, w / 2.0F, cy + 9.0F, col, Font.MEDIUM);
      f.center(Integer.toString(me), w / 2.0F, cy + 24.0F, Theme.TEXT, Font.DISPLAY);
      int acc = this.game.shots[0] == 0 ? 0 : Math.round(100.0F * Math.min(this.game.hits[0], this.game.shots[0]) / this.game.shots[0]);
      f.center(this.game.hits[0] + " birds · " + this.game.shots[0] + " shots · " + acc + "% · best streak " + this.game.bestStreak[0], w / 2.0F, cy + 54.0F,
         Theme.TEXT2, Font.SMALL);
      String line = "";
      if (this.mode == GameKind.MODE_SOLO) {
         line = !this.ranked ? "Unranked (no signal)" : (this.m.games.flushResult.isEmpty() ? "Sending the run to the server…" : this.m.games.flushResult);
      } else if (this.mode == GameKind.MODE_AI) {
         line = "AI " + this.game.score[1] + " · " + this.game.hits[1] + " birds";
      } else {
         PhoneModel.Online o = this.online();
         if (o != null && o.status != 0) {
            line = o.finalScores[o.me] + " – " + o.finalScores[1 - o.me] + " (checked by the server)";
         }
      }
      f.center(this.txt.fit(f, line, cw - 16.0F, Font.SMALL), w / 2.0F, cy + 66.0F, Theme.GOLD, Font.SMALL);
      float bw = (cw - 26.0F) / 2.0F;
      if (this.mode == GameKind.MODE_ONLINE) {
         PhoneModel.Online o = this.online();
         if (o != null && o.status != 0) {
            this.onlineEndButtons(f, cx + 10.0F, cy + ch - 28.0F, cw - 20.0F, 18.0F);
         } else {
            Ui.button(this.ui, f, Z_LOBBY, 0L, cx + 10.0F, cy + ch - 28.0F, cw - 20.0F, 18.0F, "Back to the lobby", Ui.TONAL, true);
         }
      } else {
         Ui.button(this.ui, f, Z_AGAIN, 0L, cx + 10.0F, cy + ch - 28.0F, bw, 18.0F, "Again", Ui.FILLED, true);
         Ui.button(this.ui, f, Z_LOBBY, 0L, cx + 16.0F + bw, cy + ch - 28.0F, bw, 18.0F, "Lobby", Ui.TONAL, true);
      }
   }

   // ------------------------------------------------------------------------------------------------ input

   @Override
   public void mouse(float x, float y) {
      if (this.page() != PLAY) {
         return;
      }
      float w = Theme.SCREEN_H, h = Theme.SCREEN_W;
      float k = w / Flush.W;
      float oy = (h - Flush.H * k) / 2.0F;
      this.mx = Math.max(0.0F, Math.min(Flush.W, x / k));
      this.my = Math.max(0.0F, Math.min(Flush.H, (y - oy) / k));
   }

   @Override
   public boolean press(float x, float y, int button) {
      if (this.page() != PLAY || this.game == null || this.game.over() || this.countdown > 0) {
         return false;
      }
      this.mouse(x, y);
      if (button == 1) {
         if (this.game.reload()) {
            this.act.sound(PhoneActions.Sfx.FLUSH_RELOAD);
         }
         return true;
      }
      if (this.game.shoot(this.mx, this.my)) {
         this.act.sound(PhoneActions.Sfx.FLUSH_SHOT);
         this.shake = 1.6F;
      } else {
         this.act.sound(PhoneActions.Sfx.KEY);
      }
      return true;
   }

   @Override
   public boolean key(int key, int mods) {
      if (this.page() == PLAY && key == Ui.K_R && this.game != null && this.game.reload()) {
         this.act.sound(PhoneActions.Sfx.FLUSH_RELOAD);
         return true;
      }
      return this.page() == PLAY && key != Ui.K_ESC;
   }

   @Override
   public boolean drag(float x, float y, float dx, float dy) {
      return this.page() == PLAY;
   }

   @Override
   public void tap(int id, long data) {
      if (this.lobbyTap(id, data)) {
         return;
      }
      switch (id) {
         case Z_SOLO -> {
            this.startSolo();
            this.push(PLAY);
         }
         case Z_AI -> {
            this.startAi();
            this.push(PLAY);
         }
         case Z_AGAIN -> {
            if (this.mode == GameKind.MODE_SOLO) {
               this.startSolo();
            } else {
               this.startAi();
            }
         }
         case Z_LOBBY -> {
            this.back();
            this.act.refresh(PhoneActions.R_BOARD);
         }
         default -> {
         }
      }
   }

   @Override
   public boolean back() {
      if (this.page() == PLAY && this.mode == GameKind.MODE_ONLINE && this.game != null && !this.game.over()) {
         this.act.leave(this.session);
      }
      if (this.page() == PLAY) {
         this.game = null;
      }
      return super.back();
   }
}
