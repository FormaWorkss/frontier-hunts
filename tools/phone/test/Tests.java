import com.formaworks.frontierhunts.phone.games.Chess;
import com.formaworks.frontierhunts.phone.games.ChessAi;

final class Tests {
   static void more() throws Exception {
      flush();
      dice();
      puzzles();
      chess();
   }

   static void flush() {
      int mism = 0;
      long sum = 0, aiSum = 0;
      for (int g = 0; g < 40; g++) {
         com.formaworks.frontierhunts.phone.games.Flush f = new com.formaworks.frontierhunts.phone.games.Flush(1000L + g, g % 2 == 0, g % 3);
         java.util.Random r = new java.util.Random(g);
         while (!f.over()) {
            if (r.nextInt(6) == 0) {
               // a player who aims at a bird with a sloppy lead
               for (com.formaworks.frontierhunts.phone.games.Flush.Bird b : f.birds) {
                  if (b.flying(f.tick) && !b.kind.protectedBird && r.nextBoolean()) {
                     float t = f.tick + r.nextInt(4);
                     f.shoot(b.x(t) + (float)r.nextGaussian() * 2.0F, b.y(t) + (float)r.nextGaussian() * 2.0F);
                     break;
                  }
               }
            }
            f.step();
         }
         if (g % 2 == 1) {
            int replayed = com.formaworks.frontierhunts.phone.games.Flush.replay(1000L + g, f.shotLog());
            if (replayed != f.score[0]) {
               mism++;
               System.out.println("replay mismatch seed " + (1000 + g) + ": " + replayed + " vs " + f.score[0]);
            }
         }
         sum += f.score[0];
         aiSum += f.score[1];
      }
      EngineTest.check(mism == 0, "flush replays reproduce every solo score (avg player " + sum / 40 + ", avg AI in vs-AI games " + aiSum / 20 + ")");
      int[] bad = {5, 100, 100, 3, 100, 100};
      EngineTest.check(com.formaworks.frontierhunts.phone.games.Flush.replay(1L, bad) == -1, "flush replay rejects out-of-order shot logs");
   }

   static void puzzles() {
      int ok = 0;
      for (String[] p : com.formaworks.frontierhunts.phone.games.ChessPuzzles.ALL) {
         Chess c = new Chess();
         c.load(p[0]);
         java.util.List<Integer> keys = PuzzleGen.keys(c);
         if (keys.size() == 1 && c.san(keys.get(0)).equals(p[1]) && !PuzzleGen.mateIn1(c)) {
            ok++;
         } else {
            System.out.println("bad puzzle " + p[0]);
         }
      }
      EngineTest.check(ok == com.formaworks.frontierhunts.phone.games.ChessPuzzles.ALL.length, ok + " chess puzzles: unique mate in two");
   }

   static void dice() {
      com.formaworks.frontierhunts.phone.games.Dice d;
      int[] five = {3, 3, 3, 3, 3};
      EngineTest.check(com.formaworks.frontierhunts.phone.games.Dice.points(five, 11, false) == 50 && com.formaworks.frontierhunts.phone.games.Dice.points(new int[]{2, 3, 4, 5, 1}, 10, false) == 40
         && com.formaworks.frontierhunts.phone.games.Dice.points(new int[]{2, 3, 4, 5, 5}, 9, false) == 30 && com.formaworks.frontierhunts.phone.games.Dice.points(new int[]{2, 2, 5, 5, 5}, 8, false) == 25
         && com.formaworks.frontierhunts.phone.games.Dice.points(new int[]{6, 6, 6, 6, 1}, 7, false) == 25, "dice scoring");
      for (int lvl = 0; lvl <= 2; lvl++) {
         long total = 0, worst = 0;
         int games = lvl == 2 ? 8 : 30;
         for (int g = 0; g < games; g++) {
            d = new com.formaworks.frontierhunts.phone.games.Dice(1);
            com.formaworks.frontierhunts.phone.games.DiceAi ai = new com.formaworks.frontierhunts.phone.games.DiceAi(lvl, g);
            java.util.Random r = new java.util.Random(1000 + g);
            while (!d.over()) {
               d.roll(0, r);
               while (!ai.stop(d)) {
                  long t0 = System.nanoTime();
                  int h = ai.hold(d);
                  worst = Math.max(worst, (System.nanoTime() - t0) / 1000000L);
                  d.roll(h, r);
               }
               if (!d.score(ai.box(d))) {
                  EngineTest.check(false, "dice AI chose an illegal box");
                  return;
               }
            }
            total += d.total(0);
         }
         EngineTest.check(true, "dice AI level " + lvl + " averages " + total / games + " points, slowest decision " + worst + " ms");
      }
   }

   static void chess() throws Exception {
      ChessAi ai = new ChessAi();
      Chess c = new Chess();
      c.load("6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1");
      int m = ai.best(c, ChessAi.HARD, 1);
      EngineTest.check(c.san(m).equals("Rd8#"), "hard AI finds the back-rank mate (" + c.san(m) + ")");
      c.load("6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1");
      m = ai.best(c, ChessAi.EASY, 3);
      EngineTest.check(m >= 0, "easy AI moves (" + c.san(m) + ")");
      for (int lvl = 0; lvl <= 2; lvl++) {
         c.reset();
         long t0 = System.nanoTime();
         m = ai.best(c, lvl, 7);
         long ms = (System.nanoTime() - t0) / 1_000_000L;
         EngineTest.check(m >= 0 && ms < 4000, "level " + lvl + " opening move " + c.san(m) + " in " + ms + " ms, depth " + ai.lastDepth);
      }
      for (int g = 0; g < 2; g++) {
         int white = g == 0 ? ChessAi.HARD : ChessAi.MEDIUM, black = g == 0 ? ChessAi.MEDIUM : ChessAi.EASY;
         Chess cc = new Chess();
         int pl = 0;
         StringBuilder sb = new StringBuilder();
         while (cc.status() == Chess.PLAYING && pl < 200) {
            int mm = ai.best(cc, cc.side == Chess.WHITE ? white : black, pl * 31 + g);
            if (cc.side == Chess.WHITE) {
               sb.append(cc.fullmove).append(". ");
            }
            sb.append(cc.san(mm)).append(' ');
            cc.make(mm);
            pl++;
         }
         int[] r = cc.statusReason();
         System.out.println("game " + g + ": result " + r[0] + " reason " + r[1] + " plies " + pl + ": " + sb);
      }
      // a full game, medium (white) against easy (black)
      c.reset();
      int plies = 0;
      long worst = 0;
      StringBuilder pgn = new StringBuilder();
      while (c.status() == Chess.PLAYING && plies < 300) {
         long t0 = System.nanoTime();
         m = ai.best(c, c.side == Chess.WHITE ? ChessAi.MEDIUM : ChessAi.EASY, plies);
         worst = Math.max(worst, (System.nanoTime() - t0) / 1_000_000L);
         if (c.side == Chess.WHITE) {
            pgn.append(c.fullmove).append(". ");
         }
         pgn.append(c.san(m)).append(' ');
         if (!c.isLegal(m)) {
            EngineTest.check(false, "AI played an illegal move");
            break;
         }
         c.make(m);
         plies++;
      }
      int[] st = c.statusReason();
      EngineTest.check(st[0] != Chess.PLAYING || plies >= 300, "medium vs easy game ends: result " + st[0] + " reason " + st[1] + " after " + plies
         + " plies, slowest move " + worst + " ms");
      System.out.println(pgn);
   }
}
