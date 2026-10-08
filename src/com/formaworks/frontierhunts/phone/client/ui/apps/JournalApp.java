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
 * [phone] Journal: the hunter's rank and its progress, the headline numbers of the hunting journal, the challenges
 * closest to done (the journal's checklist, achievements with XP) and the phone games' records. Every string is built
 * when the data changes (the wallet's version), never per frame.
 */
public final class JournalApp extends App {
   private static final String[] STAT_LABELS = {"Harvests", "Recoveries", "Clean kills", "Longest shot", "Best buck", "Species taken",
      "Camera photos", "Games won"};
   private static final G[] STAT_GLYPHS = {G.ANTLER, G.TRACK, G.TARGET, G.CROSSHAIR, G.STAR, G.LEAF, G.CAMERA, G.CUP};
   private static final G[] GAME_GLYPHS = {G.KNIGHT, G.DIE, G.DUCK};
   private static final int[] GAME_COLS = {0xFFE0B878, 0xFF8CD46A, 0xFFF2A65E};
   private int builtFor = -1;
   private int builtGames = -1;
   private String sub = "";
   private String rankLine = "";
   private String xpLine = "";
   private float rankT;
   private final String[] stats = new String[8];
   private String achLine = "";
   private String[] goalValue = new String[0];
   private String[] goalXp = new String[0];
   private final String[] gameLines = new String[GameKind.COUNT];
   private final String[] gameSubs = new String[GameKind.COUNT];

   public JournalApp() {
      super("journal", "Journal", 15);
   }

   @Override
   public void opened() {
      this.act.refresh(PhoneActions.R_WALLET);
      this.act.refresh(PhoneActions.R_GAMES);
   }

   private static String unit(int unit, int v) {
      return switch (unit) {
         case 1 -> v >= 1000 ? String.format(java.util.Locale.ROOT, "%.1f km", v / 1000.0F) : v + " m";
         case 2 -> v + " m";
         case 3 -> v + " in";
         default -> Integer.toString(v);
      };
   }

   private void build() {
      PhoneModel.Wallet wl = this.m.wallet;
      PhoneModel.Games g = this.m.games;
      if (this.builtFor == wl.version && this.builtGames == g.version) {
         return;
      }
      this.builtFor = wl.version;
      this.builtGames = g.version;
      this.sub = wl.rank.isEmpty() ? "Your hunting record" : wl.rank + " · " + wl.xp + " XP";
      this.rankLine = wl.rank.isEmpty() ? "Hunter" : wl.rank;
      long span = Math.max(1L, wl.rankTo - wl.rankFrom);
      this.rankT = wl.nextRank.isEmpty() ? 1.0F : Theme.clamp01((float)(wl.xp - wl.rankFrom) / span);
      this.xpLine = wl.nextRank.isEmpty() ? "Top rank reached" : (wl.rankTo - wl.xp) + " XP to " + wl.nextRank;
      int won = 0;
      for (int game = 0; game < GameKind.COUNT; game++) {
         won += g.stats[GameKind.stat(game, GameKind.MODE_AI, GameKind.WIN)] + g.stats[GameKind.stat(game, GameKind.MODE_ONLINE, GameKind.WIN)];
      }
      int[] v = {wl.harvests, wl.recoveries, wl.clean, wl.longest, wl.bestScore, wl.species, wl.photos, won};
      for (int i = 0; i < 8; i++) {
         this.stats[i] = i == 3 ? v[i] + " m" : Integer.toString(v[i]);
      }
      this.achLine = wl.achTotal <= 0 ? "" : wl.achDone + " of " + wl.achTotal + " done";
      this.goalValue = new String[wl.goals.size()];
      this.goalXp = new String[wl.goals.size()];
      for (int i = 0; i < wl.goals.size(); i++) {
         PhoneModel.Goal goal = wl.goals.get(i);
         this.goalXp[i] = "+" + goal.xp() + " XP";
         this.goalValue[i] = unit(goal.unit(), Math.min(goal.value(), goal.target())) + " / " + unit(goal.unit(), goal.target());
      }
      for (int game = 0; game < GameKind.COUNT; game++) {
         int base = game * 6;
         String ai = "AI " + g.stats[base] + "–" + g.stats[base + 1] + (g.stats[base + 2] > 0 ? "–" + g.stats[base + 2] : "");
         String on = "online " + g.stats[base + 3] + "–" + g.stats[base + 4] + (g.stats[base + 5] > 0 ? "–" + g.stats[base + 5] : "");
         this.gameLines[game] = GameKind.TITLES[game];
         this.gameSubs[game] = switch (game) {
            case GameKind.CHESS -> Long.bitCount(g.puzzlesSolved) + " puzzles solved · " + ai + " · " + on;
            case GameKind.DICE -> "Best solo card " + g.diceBest + " · " + ai + " · " + on;
            default -> "Best run " + g.flushBest + (g.flushRank > 0 ? " (#" + g.flushRank + ")" : "") + " · " + ai + " · " + on;
         };
      }
   }

   @Override
   protected void drawPage(Frame f, int page, float w, float h) {
      PhoneModel.Wallet wl = this.m.wallet;
      this.build();
      f.fill(0.0F, 0.0F, w, h, 0xFF100D09);
      f.gradient(0.0F, 0.0F, w, 130.0F, 0xFF2C2114, 0xFF100D09);
      float y = Ui.header(this.ui, f, "Journal", this.sub, false, w);
      if (!wl.loaded) {
         Ui.empty(this.ui, f, G.LIST, "Opening the journal…", "", w / 2.0F, y + 30.0F, w - 40.0F);
         return;
      }
      float y0 = this.beginScroll(f, y, w, h);
      y = y0;
      // rank card: a ring with the progress to the next rank
      f.round(9.0F, y, w - 18.0F, 52.0F, 12.0F, 0xFF2A2016);
      f.round(9.0F, y, w - 18.0F, 0.6F, 0.3F, 0x22FFFFFF);
      Ui.ring(f, 34.0F, y + 26.0F, 17.0F, 4.0F, this.rankT, 0xFF3E3022, Theme.GOLD);
      G.ANTLER.draw(f, 34.0F, y + 26.0F, 15.0F, Theme.GOLD);
      f.text(this.txt.fit(f, this.rankLine, w - 80.0F, Font.TITLE), 60.0F, y + 11.0F, Theme.TEXT, Font.TITLE);
      f.text(this.txt.fit(f, this.xpLine, w - 80.0F, Font.SMALL), 60.0F, y + 29.0F, Theme.TEXT2, Font.SMALL);
      Ui.bar(f, 60.0F, y + 40.0F, w - 82.0F, 3.0F, this.rankT, 0xFF3E3022, Theme.GOLD);
      y += 60.0F;
      // the numbers
      float tw = (w - 18.0F - 6.0F) / 2.0F;
      for (int i = 0; i < 8; i++) {
         float tx = 9.0F + (i % 2) * (tw + 6.0F);
         float ty = y + (i / 2) * 36.0F;
         if (f.visible(tx, ty, tw, 31.0F)) {
            f.round(tx, ty, tw, 31.0F, 9.0F, 0xFF1F1912);
            STAT_GLYPHS[i].draw(f, tx + 11.0F, ty + 10.0F, 9.0F, Theme.GOLD);
            f.text(STAT_LABELS[i], tx + 19.0F, ty + 6.5F, Theme.TEXT3, Font.SMALL);
            f.text(this.stats[i], tx + 8.0F, ty + 16.0F, Theme.TEXT, Font.MEDIUM);
         }
      }
      y += 4 * 36.0F + 4.0F;
      // the challenges closest to done
      Ui.section(f, "CHALLENGES", 12.0F, y, w);
      f.right(this.achLine, w - 13.0F, y, Theme.GOLD, Font.SMALL);
      y += 11.0F;
      if (wl.goals.isEmpty()) {
         y += this.txt.para(f, wl.achTotal > 0 && wl.achDone >= wl.achTotal ? "Every challenge in the journal is done. Nothing left but bigger bucks."
            : "Your challenges show here once the journal has your record.", 12.0F, y + 2.0F, w - 24.0F, Theme.TEXT3, Font.SMALL,
            1.0F) + 8.0F;
      }
      for (int i = 0; i < wl.goals.size() && i < this.goalValue.length; i++) {
         PhoneModel.Goal goal = wl.goals.get(i);
         float th = this.txt.paraHeight(f, goal.hint(), w - 40.0F, Font.SMALL, 1.0F);
         float ch = 34.0F + th;
         if (f.visible(9.0F, y, w - 18.0F, ch)) {
            f.round(9.0F, y, w - 18.0F, ch, 10.0F, 0xFF1F1912);
            f.text(this.txt.fit(f, goal.title(), w - 90.0F, Font.STRONG), 17.0F, y + 6.0F, Theme.TEXT, Font.STRONG);
            if (goal.xp() > 0) {
               Ui.chipRight(f, this.goalXp[i], w - 15.0F, y + 5.0F, 0xFF3A2C18, Theme.GOLD);
            }
            this.txt.para(f, goal.hint(), 17.0F, y + 18.0F, w - 40.0F, Theme.TEXT3, Font.SMALL, 1.0F);
            float t = goal.target() <= 0 ? 0.0F : (float)goal.value() / goal.target();
            Ui.bar(f, 17.0F, y + ch - 10.0F, w - 90.0F, 3.0F, t, 0xFF3A2E20, Theme.MOSS);
            f.right(this.goalValue[i], w - 17.0F, y + ch - 12.5F, Theme.TEXT2, Font.SMALL);
         }
         y += ch + 5.0F;
      }
      y += 4.0F;
      // games
      Ui.section(f, "PHONE GAMES", 12.0F, y, w);
      y += 11.0F;
      for (int game = 0; game < GameKind.COUNT; game++) {
         y += Ui.row(this.ui, f, 0, 0L, 12.0F, y, w - 24.0F, GAME_GLYPHS[game], GAME_COLS[game], this.gameLines[game], this.gameSubs[game], "", 0, false) + 2.0F;
      }
      this.endScroll(f, y0, y + 6.0F, w, h);
   }
}
