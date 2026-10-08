package com.formaworks.frontierhunts.phone.client.ui.apps;

import com.formaworks.frontierhunts.phone.client.ui.Canvas.Font;
import com.formaworks.frontierhunts.phone.client.ui.Frame;
import com.formaworks.frontierhunts.phone.client.ui.G;
import com.formaworks.frontierhunts.phone.client.ui.PhoneActions;
import com.formaworks.frontierhunts.phone.client.ui.PhoneModel;
import com.formaworks.frontierhunts.phone.client.ui.Theme;
import com.formaworks.frontierhunts.phone.client.ui.Ui;
import com.formaworks.frontierhunts.phone.client.ui.Worker;
import com.formaworks.frontierhunts.phone.games.Chess;
import com.formaworks.frontierhunts.phone.games.ChessAi;
import com.formaworks.frontierhunts.phone.games.ChessPuzzles;
import com.formaworks.frontierhunts.phone.games.GameKind;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

/**
 * [phone] Lodge Chess on a walnut and birch board: mate-in-two puzzles to solve alone, a real engine to play at three
 * strengths, or a hunter on the server (every move checked by the server). Legal moves are shown when you pick a
 * piece; promotion lets you choose; check, mate, stalemate, repetition and the fifty-move rule all end games properly.
 */
public final class ChessApp extends GameApp {
   private static final int[] START_MATERIAL = {0, 8, 2, 2, 2, 1, 1};
   private static final int[] PROMO_TYPES = {Chess.QUEEN, Chess.ROOK, Chess.BISHOP, Chess.KNIGHT};
   private final int[] have = new int[7];
   private static final int Z_PUZZLES = 100;
   private static final int Z_AI = 101;
   private static final int Z_SQ = 102;
   private static final int Z_PROMO = 103;
   private static final int Z_UNDO = 104;
   private static final int Z_RESIGN = 105;
   private static final int Z_AGAIN = 106;
   private static final int Z_NEXT = 107;
   private static final int Z_LOBBY = 108;
   private static final int Z_FLIP = 109;
   private static final String[] THINKING = {"thinking", "thinking.", "thinking..", "thinking..."};
   private static final int LIGHT = 0xFFE6D6B4;
   private static final int DARK = 0xFF8B6542;
   private Chess game = new Chess();
   private final List<Integer> played = new ArrayList<>();
   private final List<String> sans = new ArrayList<>();
   private int mySide = Chess.WHITE;
   private int selected = -1;
   private final int[] targets = new int[64];
   private int targetCount;
   private int promoFrom = -1, promoTo = -1;
   private final ChessAi ai = new ChessAi();
   private Future<Integer> aiJob;
   private long aiAskedAt;
   private int[] result = {Chess.PLAYING, Chess.R_NONE};
   private boolean resigned;
   private boolean reported;
   // puzzles
   private int puzzle;
   private int puzzleStage;
   private String note = "";
   private long noteAt;
   // animation
   private int animFrom = -1, animTo = -1, animPiece;
   private long animAt;
   private int seenSeq = -1;
   private int confirmResign;
   private long confirmUntil;

   public ChessApp() {
      super("chess", "Chess", 10, GameKind.CHESS, 0xFFE0B878);
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
      float y = this.hero(f, w, "Mate-in-two puzzles, a real engine at three strengths, or a hunter on the server.", G.KNIGHT);
      float y0 = this.beginScroll(f, y, w, h);
      y = y0;
      int solved = Long.bitCount(this.m.games.puzzlesSolved);
      y += this.modeCard(f, Z_PUZZLES, y, w, G.PUZZLE, "Puzzles", "Find the move that forces mate in two.", solved + " / " + ChessPuzzles.ALL.length) + 6.0F;
      int[] st = this.m.games.stats;
      int base = GameKind.stat(GameKind.CHESS, GameKind.MODE_AI, 0);
      String rec = st[base] + "W " + st[base + 1] + "L " + st[base + 2] + "D";
      y += this.modeCard(f, Z_AI, y, w, G.CHIP, "Play the engine", "You take white against the lodge's chess engine.", rec, 22.0F);
      this.levelPicker(f, y, w);
      y += 6.0F;
      y = this.onlineSection(f, y + 2.0F, w);
      this.endScroll(f, y0, y, w, h);
   }

   // ------------------------------------------------------------------------------------------------ setting up

   private void newAiGame() {
      this.mode = GameKind.MODE_AI;
      this.session = 0L;
      this.game = new Chess();
      this.mySide = Chess.WHITE;
      this.clear();
   }

   private void newPuzzle(int i) {
      this.mode = GameKind.MODE_SOLO;
      this.session = 0L;
      this.puzzle = Math.floorMod(i, ChessPuzzles.ALL.length);
      this.game = new Chess();
      this.game.load(ChessPuzzles.ALL[this.puzzle][0]);
      this.mySide = this.game.side;
      this.puzzleStage = 0;
      this.clear();
      this.say((this.mySide == Chess.WHITE ? "White" : "Black") + " to play and mate in two");
   }

   @Override
   protected void startOnline() {
      this.seenSeq = -1;
      this.clear();
      this.syncOnline();
   }

   private void clear() {
      this.played.clear();
      this.sans.clear();
      this.selected = -1;
      this.targetCount = 0;
      this.promoFrom = -1;
      this.animFrom = -1;
      this.result = new int[]{Chess.PLAYING, Chess.R_NONE};
      this.resigned = false;
      this.reported = false;
      this.cancelAi();
   }

   private void cancelAi() {
      if (this.aiJob != null) {
         this.ai.cancel();
         this.aiJob.cancel(false);
         this.aiJob = null;
      }
   }

   private void syncOnline() {
      PhoneModel.Online o = this.online();
      if (o == null || o.seq == this.seenSeq) {
         return;
      }
      this.seenSeq = o.seq;
      int[] s = o.state;
      this.mySide = o.me == 0 ? Chess.WHITE : Chess.BLACK;
      if (s.length >= 72) {
         // replay the moves for the move list (and repetition history); fall back to the saved position
         Chess c = new Chess();
         List<String> list = new ArrayList<>();
         List<Integer> ms = new ArrayList<>();
         int n = Math.max(0, Math.min(s[71], s.length - 72));
         boolean ok = true;
         for (int i = 0; i < n; i++) {
            int m = s[72 + i];
            if (!c.isLegal(m)) {
               ok = false;
               break;
            }
            list.add(c.san(m));
            ms.add(m);
            c.make(m);
         }
         int last = this.game.lastMove;
         if (!ok) {
            c = new Chess();
            c.restore(s, 1);
            list.clear();
            ms.clear();
         }
         if (c.lastMove != last && c.lastMove >= 0) {
            int lm = c.lastMove;
            this.animate(Chess.from(lm), Chess.to(lm), c.board[Chess.to(lm)]);
            boolean capture = (Chess.flags(lm) & Chess.F_CAPTURE) != 0;
            this.act.sound(c.inCheck() ? PhoneActions.Sfx.CHESS_CHECK : (capture ? PhoneActions.Sfx.CHESS_CAPTURE : PhoneActions.Sfx.CHESS_MOVE));
         }
         this.game = c;
         this.sans.clear();
         this.sans.addAll(list);
         this.played.clear();
         this.played.addAll(ms);
      }
      this.selected = -1;
      this.targetCount = 0;
      if (o.status != 0) {
         this.result = new int[]{o.winner == 2 ? Chess.DRAW : (o.winner == 0 ? Chess.WHITE_WINS : (o.winner == 1 ? Chess.BLACK_WINS : Chess.DRAW)),
            reasonCode(o.reason)};
      } else {
         this.result = new int[]{Chess.PLAYING, Chess.R_NONE};
      }
   }

   private static int reasonCode(String r) {
      return switch (r) {
         case "mate" -> Chess.R_MATE;
         case "stalemate" -> Chess.R_STALEMATE;
         case "fifty" -> Chess.R_FIFTY;
         case "repetition" -> Chess.R_REPETITION;
         case "material" -> Chess.R_MATERIAL;
         case "resign" -> Chess.R_RESIGN;
         case "left" -> Chess.R_LEFT;
         default -> Chess.R_TIMEOUT;
      };
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
      if (this.aiJob != null && this.aiJob.isDone() && this.act.millis() - this.aiAskedAt > 450L) {
         int m;
         try {
            m = this.aiJob.get();
         } catch (Exception e) {
            m = -1;
         }
         this.aiJob = null;
         if (m >= 0 && this.game.isLegal(m) && this.result[0] == Chess.PLAYING) {
            this.apply(m);
            if (this.mode == GameKind.MODE_SOLO) {
               this.puzzleStage = 2;
               this.say("Now finish it: mate in one");
            }
         }
      }
      if (this.mode == GameKind.MODE_AI && this.aiJob == null && this.result[0] == Chess.PLAYING && this.game.side != this.mySide) {
         this.askAi(this.level);
      }
      if (this.mode == GameKind.MODE_SOLO && this.puzzleStage == 1 && this.aiJob == null && this.game.side != this.mySide) {
         this.askAi(ChessAi.HARD);
      }
   }

   private void askAi(int lvl) {
      Chess snapshot = this.game.copy();
      long seed = this.act.millis() ^ this.played.size() * 7919L;
      this.aiAskedAt = this.act.millis();
      this.aiJob = Worker.submit(() -> this.ai.best(snapshot, lvl, seed));
   }

   private void apply(int m) {
      int from = Chess.from(m), to = Chess.to(m);
      boolean capture = (Chess.flags(m) & Chess.F_CAPTURE) != 0;
      String san = this.game.san(m);
      int piece = Chess.promo(m) != 0 ? this.game.side * Chess.promo(m) : this.game.board[from];
      this.game.make(m);
      this.played.add(m);
      this.sans.add(san);
      this.animate(from, to, piece);
      this.selected = -1;
      this.targetCount = 0;
      this.result = this.game.statusReason();
      if (this.result[0] != Chess.PLAYING) {
         boolean won = this.result[0] == Chess.WHITE_WINS && this.mySide == Chess.WHITE || this.result[0] == Chess.BLACK_WINS && this.mySide == Chess.BLACK;
         this.act.sound(this.result[0] == Chess.DRAW ? PhoneActions.Sfx.CHESS_MOVE : (won ? PhoneActions.Sfx.WIN : PhoneActions.Sfx.LOSE));
         this.report();
      } else {
         this.act.sound(this.game.inCheck() ? PhoneActions.Sfx.CHESS_CHECK : (capture ? PhoneActions.Sfx.CHESS_CAPTURE : PhoneActions.Sfx.CHESS_MOVE));
      }
   }

   private void report() {
      if (this.reported || this.mode != GameKind.MODE_AI) {
         return;
      }
      this.reported = true;
      int r = this.result[0];
      boolean won = r == Chess.WHITE_WINS && this.mySide == Chess.WHITE || r == Chess.BLACK_WINS && this.mySide == Chess.BLACK;
      this.act.gameStat(GameKind.CHESS, GameKind.MODE_AI, r == Chess.DRAW ? GameKind.DRAW : (won ? GameKind.WIN : GameKind.LOSS));
   }

   private void animate(int from, int to, int piece) {
      this.animFrom = from;
      this.animTo = to;
      this.animPiece = piece;
      this.animAt = this.act.millis();
   }

   private void say(String s) {
      this.note = s;
      this.noteAt = this.act.millis();
   }

   // ------------------------------------------------------------------------------------------------ the board

   private boolean myTurn() {
      if (this.result[0] != Chess.PLAYING || this.game.side != this.mySide) {
         return false;
      }
      if (this.mode == GameKind.MODE_ONLINE) {
         PhoneModel.Online o = this.online();
         return o != null && o.status == 0 && o.state.length > 0 && o.state[0] == o.me;
      }
      if (this.mode == GameKind.MODE_SOLO) {
         return this.puzzleStage == 0 || this.puzzleStage == 2;
      }
      return this.aiJob == null;
   }

   private void play(Frame f, float w, float h) {
      f.fill(0.0F, 0.0F, w, h, 0xFF15110D);
      String title = this.mode == GameKind.MODE_SOLO ? "Puzzle " + (this.puzzle + 1) + " of " + ChessPuzzles.ALL.length
         : (this.mode == GameKind.MODE_AI ? "Engine · " + LEVELS[this.level] : "vs " + this.opponent(this.online()));
      float y = Ui.bar(this.ui, f, title, w, true);
      float bs = w - 14.0F;
      float sq = bs / 8.0F;
      float bx = 7.0F;
      String oppName = this.mode == GameKind.MODE_ONLINE ? this.opponent(this.online()) : (this.mode == GameKind.MODE_AI ? "Engine" : "Defender");
      // opponent strip
      this.strip(f, w, y, oppName, -this.mySide, this.aiJob != null || this.mode == GameKind.MODE_ONLINE && !this.myTurn() && this.result[0] == 0);
      y += 20.0F;
      float by = y;
      boolean flip = this.mySide == Chess.BLACK;
      int lm = this.game.lastMove;
      int checkSq = this.game.inCheck() ? this.game.king(this.game.side) : -1;
      long now = this.act.millis();
      float animT = this.animFrom >= 0 ? Theme.clamp01((now - this.animAt) / 170.0F) : 1.0F;
      if (animT >= 1.0F) {
         this.animFrom = -1;
      }
      // frame of the board
      f.round(bx - 3.0F, by - 3.0F, bs + 6.0F, bs + 6.0F, 4.0F, 0xFF3A2614);
      for (int r = 0; r < 8; r++) {
         for (int c = 0; c < 8; c++) {
            int s = flip ? (r * 8 + (7 - c)) : ((7 - r) * 8 + c);
            float x = bx + c * sq, yy = by + r * sq;
            boolean light = ((s >> 3) + (s & 7)) % 2 == 1;
            int col = light ? LIGHT : DARK;
            if (lm >= 0 && (s == Chess.from(lm) || s == Chess.to(lm))) {
               col = Theme.mix(col, 0xFFE8C840, 0.42F);
            }
            if (s == this.selected) {
               col = Theme.mix(col, 0xFF7FC860, 0.55F);
            }
            f.fill(x, yy, sq + 0.2F, sq + 0.2F, col);
            if (s == checkSq) {
               f.circle(x + sq / 2.0F, yy + sq / 2.0F, sq * 0.48F, 0x90E03028);
            }
            if (c == 0) {
               f.text(Integer.toString((s >> 3) + 1), x + 1.0F, yy + 0.5F, light ? DARK : LIGHT, Font.SMALL);
            }
            if (r == 7) {
               f.right(Character.toString((char)('a' + (s & 7))), x + sq - 1.0F, yy + sq - 7.5F, light ? DARK : LIGHT, Font.SMALL);
            }
            f.zone(Z_SQ, x, yy, sq, sq, s);
         }
      }
      // pieces
      for (int s = 0; s < 64; s++) {
         int p = this.game.board[s];
         if (p == 0 || s == this.animTo && this.animFrom >= 0) {
            continue;
         }
         this.squareXY(s, bx, by, sq, flip);
         this.piece(f, p, this.sqX, this.sqY, sq);
      }
      if (this.animFrom >= 0) {
         this.squareXY(this.animFrom, bx, by, sq, flip);
         float ax = this.sqX, ay = this.sqY;
         this.squareXY(this.animTo, bx, by, sq, flip);
         float e = Theme.easeOut(animT);
         this.piece(f, this.animPiece, ax + (this.sqX - ax) * e, ay + (this.sqY - ay) * e - (float)Math.sin(e * Math.PI) * sq * 0.15F, sq);
      }
      // legal destinations
      for (int i = 0; i < this.targetCount; i++) {
         int t = this.targets[i];
         this.squareXY(t, bx, by, sq, flip);
         if (this.game.board[t] != 0) {
            f.ring(this.sqX + sq / 2.0F, this.sqY + sq / 2.0F, sq * 0.47F, sq * 0.09F, 0x70203A18);
         } else {
            f.circle(this.sqX + sq / 2.0F, this.sqY + sq / 2.0F, sq * 0.16F, 0x70203A18);
         }
      }
      y = by + bs + 3.0F;
      this.strip(f, w, y, this.mode == GameKind.MODE_ONLINE ? this.m.playerName : "You", this.mySide, false);
      y += 21.0F;
      // moves
      f.round(7.0F, y, w - 14.0F, 15.0F, 5.0F, 0xFF231C16);
      StringBuilder sb = new StringBuilder();
      int first = Math.max(0, this.sans.size() - 8);
      if (first % 2 == 1) {
         first--;
      }
      for (int i = first; i < this.sans.size(); i++) {
         if (i % 2 == 0) {
            sb.append(i / 2 + 1).append(". ");
         }
         sb.append(this.sans.get(i)).append(' ');
      }
      String moves = sb.length() == 0 ? "No moves yet" : sb.toString();
      String fitted = moves;
      while (f.width(fitted, Font.SMALL) > w - 24.0F && fitted.indexOf(' ') > 0) {
         fitted = fitted.substring(fitted.indexOf(' ') + 1);
      }
      f.text(fitted, 12.0F, y + 4.0F, Theme.TEXT2, Font.SMALL);
      y += 20.0F;
      // status and buttons
      this.status(f, w, y);
      y += 16.0F;
      this.buttons(f, w, y);
      if (this.promoFrom >= 0) {
         this.promoPicker(f, bx, by, bs);
      }
      if (this.result[0] != Chess.PLAYING) {
         this.overCard(f, w, bx, by, bs);
      }
   }

   private float sqX, sqY;

   private void squareXY(int s, float bx, float by, float sq, boolean flip) {
      int r = s >> 3, c = s & 7;
      int col = flip ? 7 - c : c;
      int row = flip ? r : 7 - r;
      this.sqX = bx + col * sq;
      this.sqY = by + row * sq;
   }

   private void piece(Frame f, int p, float x, float y, float sq) {
      int t = Math.abs(p) - 1;
      int row = p > 0 ? 0 : 1;
      f.sprite("chess", x - sq * 0.04F, y - sq * 0.06F, sq * 1.08F, sq * 1.08F, t / 6.0F, row / 2.0F, (t + 1) / 6.0F, (row + 1) / 2.0F, 0xFFFFFFFF);
   }

   private void strip(Frame f, float w, float y, String name, int side, boolean thinking) {
      boolean toMove = this.game.side == side && this.result[0] == Chess.PLAYING;
      f.circle(14.0F, y + 8.0F, 5.0F, side == Chess.WHITE ? 0xFFF3ECDC : 0xFF2A2420);
      f.ring(14.0F, y + 8.0F, 5.0F, 0.8F, toMove ? Theme.MOSS : 0x50FFFFFF);
      f.text(this.txt.fit(f, name, 60.0F, Font.STRONG), 24.0F, y + 3.0F, toMove ? Theme.TEXT : Theme.TEXT2, Font.STRONG);
      if (thinking) {
         int dots = (int)(this.act.millis() / 300L % 4L);
         f.text(THINKING[dots], 24.0F + Math.min(60.0F, f.width(name, Font.STRONG)) + 5.0F, y + 4.0F, Theme.TEXT3, Font.SMALL);
      }
      // captured material, as small pieces
      int[] start = START_MATERIAL;
      int[] have = this.have;
      java.util.Arrays.fill(have, 0);
      for (int pc : this.game.board) {
         if (Integer.signum(pc) == -side) {
            have[Math.abs(pc)]++;
         }
      }
      float x = w - 10.0F;
      for (int t = 5; t >= 1; t--) {
         int lost = Math.max(0, start[t] - have[t]);
         for (int i = 0; i < lost; i++) {
            x -= 6.0F;
            int pc = -side * t;
            int row = pc > 0 ? 0 : 1;
            f.sprite("chess", x - 1.0F, y + 2.0F, 10.0F, 10.0F, (t - 1) / 6.0F, row / 2.0F, t / 6.0F, (row + 1) / 2.0F, 0xFFFFFFFF);
         }
      }
      int diff = this.game.material(side) - this.game.material(-side);
      if (diff > 0) {
         f.right("+" + diff, x - 3.0F, y + 4.0F, Theme.TEXT3, Font.SMALL);
      }
   }

   private void status(Frame f, float w, float y) {
      String s;
      int col = Theme.TEXT2;
      long age = this.act.millis() - this.noteAt;
      if (!this.note.isEmpty() && age < 4000L && this.mode == GameKind.MODE_SOLO) {
         s = this.note;
         col = this.puzzleStage == 3 ? Theme.MOSS : Theme.GOLD;
      } else if (this.result[0] != Chess.PLAYING) {
         s = "Game over";
      } else if (this.myTurn()) {
         s = this.game.inCheck() ? "Check! Your move" : "Your move";
         col = this.game.inCheck() ? Theme.RED : Theme.MOSS;
      } else if (this.mode == GameKind.MODE_ONLINE) {
         PhoneModel.Online o = this.online();
         s = o != null && o.opponentAway ? this.opponent(o) + " left · waiting " + o.awaySeconds + " s" : "Waiting for " + this.opponent(o);
         col = o != null && o.opponentAway ? Theme.YELLOW : Theme.TEXT3;
      } else {
         s = this.mode == GameKind.MODE_SOLO ? "The defender replies…" : "Engine to move";
         col = Theme.TEXT3;
      }
      f.center(this.txt.fit(f, s, w - 16.0F, Font.STRONG), w / 2.0F, y, col, Font.STRONG);
   }

   private void buttons(Frame f, float w, float y) {
      float bw = (w - 14.0F - 8.0F) / 3.0F;
      boolean over = this.result[0] != Chess.PLAYING;
      if (this.mode == GameKind.MODE_AI) {
         Ui.button(this.ui, f, Z_UNDO, 0L, 7.0F, y, bw, 18.0F, "Take back", Ui.TONAL, this.played.size() >= 2 && this.aiJob == null && !over);
         Ui.button(this.ui, f, Z_AGAIN, 0L, 11.0F + bw, y, bw, 18.0F, "New game", Ui.TONAL, true);
         boolean sure = this.act.millis() < this.confirmUntil;
         Ui.button(this.ui, f, Z_RESIGN, 0L, 15.0F + bw * 2.0F, y, bw, 18.0F, sure ? "Sure?" : "Resign", Ui.DANGER, !over);
      } else if (this.mode == GameKind.MODE_SOLO) {
         Ui.button(this.ui, f, Z_AGAIN, 0L, 7.0F, y, bw * 1.5F + 2.0F, 18.0F, "Restart", Ui.TONAL, true);
         Ui.button(this.ui, f, Z_NEXT, 0L, 13.0F + bw * 1.5F, y, bw * 1.5F + 2.0F, 18.0F, "Next puzzle", this.puzzleStage == 3 ? Ui.GOOD : Ui.TONAL, true);
      } else {
         boolean sure = this.act.millis() < this.confirmUntil;
         Ui.button(this.ui, f, Z_RESIGN, 0L, 7.0F, y, w - 14.0F, 18.0F, over ? "Back to the lobby" : (sure ? "Tap again to resign" : "Resign"),
            over ? Ui.TONAL : Ui.DANGER, true);
      }
   }

   private void promoPicker(Frame f, float bx, float by, float bs) {
      f.fill(bx, by, bs, bs, 0x90000000);
      float cw = bs * 0.8F, ch = bs * 0.34F;
      float cx = bx + (bs - cw) / 2.0F, cy = by + (bs - ch) / 2.0F;
      f.round(cx, cy, cw, ch, 8.0F, 0xFF2A221B);
      f.center("Promote to", bx + bs / 2.0F, cy + 4.0F, Theme.TEXT2, Font.SMALL);
      int[] types = PROMO_TYPES;
      float s = (cw - 10.0F) / 4.0F;
      for (int i = 0; i < 4; i++) {
         float x = cx + 5.0F + i * s;
         boolean hot = this.ui.hot(Z_PROMO, types[i]);
         f.round(x + 1.0F, cy + 14.0F, s - 2.0F, s - 2.0F, 5.0F, hot ? 0xFF4A3A2A : 0xFF3A2E22);
         this.piece(f, this.mySide * types[i], x + 3.0F, cy + 16.0F, s - 6.0F);
         f.zone(Z_PROMO, x, cy + 14.0F, s, s, types[i]);
      }
   }

   private void overCard(Frame f, float w, float bx, float by, float bs) {
      int r = this.result[0];
      boolean won = r == Chess.WHITE_WINS && this.mySide == Chess.WHITE || r == Chess.BLACK_WINS && this.mySide == Chess.BLACK;
      if (this.mode == GameKind.MODE_SOLO) {
         return;
      }
      String head = r == Chess.DRAW ? "Draw" : (won ? "You win!" : (this.mode == GameKind.MODE_AI ? "The engine wins" : this.opponent(this.online()) + " wins"));
      String why = switch (this.result[1]) {
         case Chess.R_MATE -> "Checkmate";
         case Chess.R_STALEMATE -> "Stalemate";
         case Chess.R_FIFTY -> "Fifty moves without a capture or pawn move";
         case Chess.R_REPETITION -> "Threefold repetition";
         case Chess.R_MATERIAL -> "Neither side can mate";
         case Chess.R_RESIGN -> won ? "Your opponent resigned" : "Resigned";
         case Chess.R_LEFT -> won ? "Your opponent left the game" : "You left the game";
         default -> "Game over";
      };
      float cw = bs * 0.86F, ch = 58.0F;
      float cx = bx + (bs - cw) / 2.0F, cy = by + (bs - ch) / 2.0F;
      f.round(cx, cy + 2.0F, cw, ch, 10.0F, 0x80000000);
      f.round(cx, cy, cw, ch, 10.0F, 0xF02A221B);
      f.center(head, bx + bs / 2.0F, cy + 9.0F, won ? Theme.MOSS : (r == Chess.DRAW ? Theme.GOLD : Theme.TEXT), Font.MEDIUM);
      f.center(this.txt.fit(f, why, cw - 12.0F, Font.SMALL), bx + bs / 2.0F, cy + 25.0F, Theme.TEXT2, Font.SMALL);
      if (this.mode == GameKind.MODE_AI) {
         Ui.button(this.ui, f, Z_AGAIN, 0L, cx + 10.0F, cy + 37.0F, cw - 20.0F, 16.0F, "Play again", Ui.FILLED, true);
      } else {
         this.onlineEndButtons(f, cx + 8.0F, cy + 37.0F, cw - 16.0F, 16.0F);
      }
   }

   // ------------------------------------------------------------------------------------------------ input

   @Override
   public void tap(int id, long data) {
      if (this.lobbyTap(id, data)) {
         return;
      }
      long now = this.act.millis();
      switch (id) {
         case Z_PUZZLES -> {
            long solved = this.m.games.puzzlesSolved;
            int first = 0;
            while (first < ChessPuzzles.ALL.length - 1 && (solved >>> first & 1L) != 0L) {
               first++;
            }
            this.newPuzzle(first);
            this.push(PLAY);
         }
         case Z_AI -> {
            this.newAiGame();
            this.push(PLAY);
         }
         case Z_SQ -> this.square((int)data);
         case Z_PROMO -> {
            if (this.promoFrom >= 0) {
               int m = this.game.find(this.promoFrom, this.promoTo, (int)data);
               this.promoFrom = -1;
               if (m >= 0) {
                  this.mine(m);
               }
            }
         }
         case Z_UNDO -> {
            if (this.played.size() >= 2 && this.aiJob == null) {
               for (int i = 0; i < 2; i++) {
                  int m = this.played.remove(this.played.size() - 1);
                  this.sans.remove(this.sans.size() - 1);
                  this.game.unmake(m);
               }
               this.selected = -1;
               this.targetCount = 0;
               this.animFrom = -1;
               this.act.sound(PhoneActions.Sfx.BACK);
            }
         }
         case Z_AGAIN -> {
            if (this.mode == GameKind.MODE_SOLO) {
               this.newPuzzle(this.puzzle);
            } else {
               this.newAiGame();
            }
            this.act.sound(PhoneActions.Sfx.TAP);
         }
         case Z_NEXT -> {
            this.newPuzzle(this.puzzle + 1);
            this.act.sound(PhoneActions.Sfx.TAP);
         }
         case Z_LOBBY -> this.back();
         case Z_RESIGN -> {
            if (this.mode == GameKind.MODE_ONLINE && this.result[0] != Chess.PLAYING) {
               this.back();
               return;
            }
            if (now < this.confirmUntil) {
               this.confirmUntil = 0L;
               if (this.mode == GameKind.MODE_ONLINE) {
                  this.act.leave(this.session);
               } else {
                  this.cancelAi();
                  this.result = new int[]{this.mySide == Chess.WHITE ? Chess.BLACK_WINS : Chess.WHITE_WINS, Chess.R_RESIGN};
                  this.act.sound(PhoneActions.Sfx.LOSE);
                  this.report();
               }
            } else {
               this.confirmUntil = now + 3000L;
               this.act.sound(PhoneActions.Sfx.TAP);
            }
         }
         default -> {
         }
      }
   }

   private void square(int s) {
      if (!this.myTurn() || this.promoFrom >= 0) {
         return;
      }
      int p = this.game.board[s];
      if (this.selected >= 0) {
         for (int i = 0; i < this.targetCount; i++) {
            if (this.targets[i] == s) {
               int from = this.selected;
               int piece = Math.abs(this.game.board[from]);
               int rank = s >> 3;
               if (piece == Chess.PAWN && (rank == 7 || rank == 0)) {
                  this.promoFrom = from;
                  this.promoTo = s;
                  return;
               }
               int m = this.game.find(from, s, 0);
               if (m >= 0) {
                  this.mine(m);
               }
               return;
            }
         }
      }
      if (p != 0 && Integer.signum(p) == this.mySide) {
         this.selected = s;
         this.targetCount = 0;
         int[] out = new int[256];
         int n = this.game.legal(out);
         for (int i = 0; i < n; i++) {
            if (Chess.from(out[i]) == s) {
               int t = Chess.to(out[i]);
               boolean dup = false;
               for (int k = 0; k < this.targetCount; k++) {
                  dup |= this.targets[k] == t;
               }
               if (!dup) {
                  this.targets[this.targetCount++] = t;
               }
            }
         }
         this.act.sound(PhoneActions.Sfx.KEY);
      } else {
         this.selected = -1;
         this.targetCount = 0;
      }
   }

   /** The player's move. */
   private void mine(int m) {
      if (this.mode == GameKind.MODE_SOLO) {
         this.puzzleMove(m);
         return;
      }
      if (this.mode == GameKind.MODE_ONLINE) {
         this.act.move(this.session, new int[]{Chess.from(m), Chess.to(m), Chess.promo(m)});
      }
      this.apply(m);
   }

   private void puzzleMove(int m) {
      String san = this.game.san(m);
      if (this.puzzleStage == 0) {
         if (!san.equals(ChessPuzzles.ALL[this.puzzle][1])) {
            this.say("Not the move. Look for checks and captures");
            this.selected = -1;
            this.targetCount = 0;
            this.act.sound(PhoneActions.Sfx.ERROR);
            return;
         }
         this.apply(m);
         this.puzzleStage = 1;
         this.say("Good! The defender replies…");
         this.askAi(ChessAi.HARD);
         return;
      }
      if (this.puzzleStage == 2) {
         this.game.make(m);
         boolean mate = this.game.inCheck() && this.game.legal(new int[256]) == 0;
         this.game.unmake(m);
         if (!mate) {
            this.say("That is not mate. Try again");
            this.selected = -1;
            this.targetCount = 0;
            this.act.sound(PhoneActions.Sfx.ERROR);
            return;
         }
         this.apply(m);
         this.puzzleStage = 3;
         this.say("Solved! Checkmate");
         this.act.sound(PhoneActions.Sfx.WIN);
         this.act.gameStat(GameKind.CHESS, GameKind.MODE_SOLO, this.puzzle);
         this.m.games.puzzlesSolved |= 1L << this.puzzle;
      }
   }

   @Override
   public boolean back() {
      if (this.page() == PLAY) {
         this.cancelAi();
      }
      return super.back();
   }

   @Override
   public void closed() {
      this.cancelAi();
   }
}
