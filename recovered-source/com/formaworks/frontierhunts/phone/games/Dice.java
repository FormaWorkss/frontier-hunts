package com.formaworks.frontierhunts.phone.games;

import java.util.Random;

/**
 * [phone] Hunter's Dice: five dice, three rolls a turn, thirteen boxes to fill (the classic rules: the upper bonus at
 * 63, five of a kind for 50, extra five-of-a-kinds for 100 with the joker rule). One or two players. Pure Java: the
 * server rolls the dice of an online game and checks every action; the phone plays solo and against the AI.
 */
public final class Dice {
   public static final int CATS = 13;
   public static final int THREE = 6, FOUR = 7, FULL = 8, SMALL = 9, LARGE = 10, FIVE = 11, CHANCE = 12;
   public static final String[] NAMES = {"Ones", "Twos", "Threes", "Fours", "Fives", "Sixes", "Three of a kind", "Four of a kind", "Full camp",
      "Short track", "Long track", "Grand slam", "Chance"};
   public static final String[] HINTS = {"Total of ones", "Total of twos", "Total of threes", "Total of fours", "Total of fives", "Total of sixes",
      "Three alike: all dice", "Four alike: all dice", "Three and two: 25", "Four in a row: 30", "Five in a row: 40", "Five alike: 50", "Any dice: all dice"};
   public int players = 1;
   public final int[][] scores = new int[2][CATS];
   public final int[] bonus5 = new int[2];
   public final int[] dice = {1, 2, 3, 4, 5};
   public int held;
   public int rollsLeft = 3;
   public int turn;
   /** turns taken by the current player (0..12); the game ends when both have filled every box */
   public int finished;
   public int rollSerial;

   public Dice(int players) {
      this.players = Math.max(1, Math.min(2, players));
      for (int[] s : this.scores) {
         java.util.Arrays.fill(s, -1);
      }
   }

   public Dice copy() {
      Dice d = new Dice(this.players);
      for (int p = 0; p < 2; p++) {
         System.arraycopy(this.scores[p], 0, d.scores[p], 0, CATS);
      }
      System.arraycopy(this.bonus5, 0, d.bonus5, 0, 2);
      System.arraycopy(this.dice, 0, d.dice, 0, 5);
      d.held = this.held;
      d.rollsLeft = this.rollsLeft;
      d.turn = this.turn;
      d.finished = this.finished;
      d.rollSerial = this.rollSerial;
      return d;
   }

   // ------------------------------------------------------------------------------------------------ actions

   public boolean canRoll() {
      return !this.over() && this.rollsLeft > 0;
   }

   /** Rolls every die not in {@code holdMask} (ignored on the first roll of a turn). */
   public boolean roll(int holdMask, Random rnd) {
      if (!this.canRoll()) {
         return false;
      }
      this.held = this.rollsLeft == 3 ? 0 : holdMask & 31;
      for (int i = 0; i < 5; i++) {
         if ((this.held & 1 << i) == 0) {
            this.dice[i] = 1 + rnd.nextInt(6);
         }
      }
      this.rollsLeft--;
      this.rollSerial++;
      return true;
   }

   public boolean canScore(int cat) {
      if (this.over() || this.rollsLeft == 3 || cat < 0 || cat >= CATS || this.scores[this.turn][cat] >= 0) {
         return false;
      }
      if (this.joker()) {
         // forced joker: the matching upper box first, then any lower box, then any upper box
         int face = this.dice[0] - 1;
         if (this.scores[this.turn][face] < 0) {
            return cat == face;
         }
         boolean lowerOpen = false;
         for (int c = THREE; c < CATS; c++) {
            if (this.scores[this.turn][c] < 0) {
               lowerOpen = true;
            }
         }
         return lowerOpen ? cat >= THREE : cat < THREE;
      }
      return true;
   }

   /** Points a box would take with the dice as they lie (0 for impossible boxes; the joker rule applied). */
   public int value(int cat) {
      return points(this.dice, cat, this.joker());
   }

   public boolean score(int cat) {
      if (!this.canScore(cat)) {
         return false;
      }
      int[] s = this.scores[this.turn];
      boolean yahtzee = isFive(this.dice);
      if (yahtzee && s[FIVE] == 50) {
         this.bonus5[this.turn]++;
      }
      s[cat] = this.value(cat);
      this.rollsLeft = 3;
      this.held = 0;
      if (this.players == 2) {
         if (this.turn == 1) {
            this.finished++;
         }
         this.turn = 1 - this.turn;
      } else {
         this.finished++;
      }
      return true;
   }

   /** A rolled five of a kind when the Grand slam box is already filled. */
   public boolean joker() {
      return this.rollsLeft < 3 && isFive(this.dice) && this.scores[this.turn][FIVE] >= 0;
   }

   public boolean over() {
      return this.finished >= CATS;
   }

   // ------------------------------------------------------------------------------------------------ scoring

   public static boolean isFive(int[] d) {
      return d[0] == d[1] && d[1] == d[2] && d[2] == d[3] && d[3] == d[4];
   }

   public static int points(int[] d, int cat, boolean joker) {
      int[] counts = new int[7];
      int sum = 0;
      for (int v : d) {
         counts[v]++;
         sum += v;
      }
      return points(counts, sum, cat, joker);
   }

   /** Points from face counts (counts[1..6]) and the dice total: no allocation. */
   public static int points(int[] counts, int sum, int cat, boolean joker) {
      if (cat < 6) {
         return counts[cat + 1] * (cat + 1);
      }
      int max = 0;
      boolean three = false, two = false;
      for (int f = 1; f <= 6; f++) {
         max = Math.max(max, counts[f]);
         three |= counts[f] == 3;
         two |= counts[f] == 2;
      }
      return switch (cat) {
         case THREE -> max >= 3 ? sum : 0;
         case FOUR -> max >= 4 ? sum : 0;
         case FULL -> three && two || joker ? 25 : 0;
         case SMALL -> run(counts) >= 4 || joker ? 30 : 0;
         case LARGE -> run(counts) >= 5 || joker ? 40 : 0;
         case FIVE -> max == 5 ? 50 : 0;
         default -> sum;
      };
   }

   private static int run(int[] counts) {
      int best = 0, cur = 0;
      for (int f = 1; f <= 6; f++) {
         cur = counts[f] > 0 ? cur + 1 : 0;
         best = Math.max(best, cur);
      }
      return best;
   }

   public int upper(int p) {
      int u = 0;
      for (int c = 0; c < 6; c++) {
         u += Math.max(0, this.scores[p][c]);
      }
      return u;
   }

   public int bonus(int p) {
      return this.upper(p) >= 63 ? 35 : 0;
   }

   public int total(int p) {
      int t = 0;
      for (int c = 0; c < CATS; c++) {
         t += Math.max(0, this.scores[p][c]);
      }
      return t + this.bonus(p) + this.bonus5[p] * 100;
   }

   /** -1 playing, 0/1 winner, 2 draw (a solo game is "won" by player 0). */
   public int winner() {
      if (!this.over()) {
         return -1;
      }
      if (this.players == 1) {
         return 0;
      }
      int a = this.total(0), b = this.total(1);
      return a > b ? 0 : (b > a ? 1 : 2);
   }

   // ------------------------------------------------------------------------------------------------ network form

   public int[] save() {
      int[] a = new int[16 + CATS * 2];
      a[0] = this.turn;
      a[1] = this.players;
      a[2] = this.rollsLeft;
      a[3] = this.held;
      for (int i = 0; i < 5; i++) {
         a[4 + i] = this.dice[i];
      }
      a[9] = this.bonus5[0];
      a[10] = this.bonus5[1];
      a[11] = this.finished;
      a[12] = this.rollSerial;
      for (int c = 0; c < CATS; c++) {
         a[16 + c] = this.scores[0][c];
         a[16 + CATS + c] = this.scores[1][c];
      }
      return a;
   }

   public boolean restore(int[] a) {
      if (a == null || a.length < 16 + CATS * 2) {
         return false;
      }
      this.turn = a[0] == 1 ? 1 : 0;
      this.players = a[1] == 2 ? 2 : 1;
      this.rollsLeft = Math.max(0, Math.min(3, a[2]));
      this.held = a[3] & 31;
      for (int i = 0; i < 5; i++) {
         this.dice[i] = Math.max(1, Math.min(6, a[4 + i]));
      }
      this.bonus5[0] = Math.max(0, a[9]);
      this.bonus5[1] = Math.max(0, a[10]);
      this.finished = Math.max(0, Math.min(CATS, a[11]));
      this.rollSerial = a[12];
      for (int c = 0; c < CATS; c++) {
         this.scores[0][c] = Math.max(-1, a[16 + c]);
         this.scores[1][c] = Math.max(-1, a[16 + CATS + c]);
      }
      return true;
   }
}
