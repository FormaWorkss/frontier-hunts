package com.formaworks.frontierhunts.phone.games;

import java.util.Random;

/**
 * [phone] The Hunter's Dice opponent. It weighs every way to hold the dice by simulating the rerolls (Monte Carlo, two
 * rolls deep), and fills boxes by points now against what each box is usually worth later (chasing the upper bonus,
 * keeping Grand slam and the straights open while there is time). Easy keeps the most common face and takes the
 * biggest score; Medium and Hard simulate, Hard with many more samples.
 */
public final class DiceAi {
   private final Random rnd;
   private final int level;
   private final int[] tmp = new int[5];
   private final int[] tmp2 = new int[5];
   /** what a box is worth on average when left for later */
   private static final float[] LATER = {2.1F, 5.3F, 8.6F, 12.2F, 15.7F, 19.2F, 15.0F, 6.5F, 14.0F, 21.0F, 13.0F, 12.0F, 22.0F};

   public DiceAi(int level, long seed) {
      this.level = level;
      this.rnd = new Random(seed);
   }

   /** The hold mask to roll with next. */
   public int hold(Dice g) {
      if (g.rollsLeft == 3) {
         return 0;
      }
      if (this.level == 0) {
         // keep the most common face
         int[] c = new int[7];
         for (int v : g.dice) {
            c[v]++;
         }
         int best = 1;
         for (int f = 1; f <= 6; f++) {
            if (c[f] > c[best] || c[f] == c[best] && f > best) {
               best = f;
            }
         }
         int mask = 0;
         for (int i = 0; i < 5; i++) {
            if (g.dice[i] == best) {
               mask |= 1 << i;
            }
         }
         return mask;
      }
      int outer = this.level == 1 ? 14 : 30;
      int inner = this.level == 1 ? 6 : 12;
      int bestMask = 31;
      float bestVal = this.bestBox(g, g.dice);
      for (int mask = 0; mask < 32; mask++) {
         if (this.duplicate(g.dice, mask)) {
            continue;
         }
         float total = 0.0F;
         for (int s = 0; s < outer; s++) {
            for (int i = 0; i < 5; i++) {
               this.tmp[i] = (mask & 1 << i) != 0 ? g.dice[i] : 1 + this.rnd.nextInt(6);
            }
            if (g.rollsLeft >= 2) {
               // one more reroll after this one: the best simple hold there
               float b = this.bestBox(g, this.tmp);
               for (int m2 = 0; m2 < 32; m2 += 1) {
                  if (this.duplicate(this.tmp, m2)) {
                     continue;
                  }
                  float t2 = 0.0F;
                  for (int s2 = 0; s2 < inner; s2++) {
                     for (int i = 0; i < 5; i++) {
                        this.tmp2[i] = (m2 & 1 << i) != 0 ? this.tmp[i] : 1 + this.rnd.nextInt(6);
                     }
                     t2 += this.bestBox(g, this.tmp2);
                  }
                  b = Math.max(b, t2 / inner);
               }
               total += b;
            } else {
               total += this.bestBox(g, this.tmp);
            }
         }
         float v = total / outer;
         if (v > bestVal + 0.01F) {
            bestVal = v;
            bestMask = mask;
         }
      }
      return bestMask;
   }

   /** Skip masks that keep the same multiset of values as a lower mask (fewer simulations, same choices). */
   private boolean duplicate(int[] d, int mask) {
      for (int i = 0; i < 5; i++) {
         for (int j = i + 1; j < 5; j++) {
            // keeping die j but not an equal die i < j is the same as keeping i but not j
            if (d[i] == d[j] && (mask & 1 << j) != 0 && (mask & 1 << i) == 0) {
               return true;
            }
         }
      }
      return false;
   }

   /** Should the AI stop rolling and score now? */
   public boolean stop(Dice g) {
      if (g.rollsLeft == 0) {
         return true;
      }
      if (g.rollsLeft == 3) {
         return false;
      }
      return this.hold(g) == 31;
   }

   /** The box to fill. */
   public int box(Dice g) {
      int best = -1;
      float bestV = -1e9F;
      for (int c = 0; c < Dice.CATS; c++) {
         if (!g.canScore(c)) {
            continue;
         }
         float v = this.level == 0 ? g.value(c) : this.boxValue(g, g.dice, c, g.value(c));
         if (v > bestV) {
            bestV = v;
            best = c;
         }
      }
      return best;
   }

   private final int[] counts = new int[7];

   private float bestBox(Dice g, int[] dice) {
      boolean joker = Dice.isFive(dice) && g.scores[g.turn][Dice.FIVE] >= 0;
      java.util.Arrays.fill(this.counts, 0);
      int sum = 0;
      for (int v : dice) {
         this.counts[v]++;
         sum += v;
      }
      float best = -1e9F;
      for (int c = 0; c < Dice.CATS; c++) {
         if (g.scores[g.turn][c] >= 0) {
            continue;
         }
         float v = this.boxValue(g, dice, c, Dice.points(this.counts, sum, c, joker));
         best = Math.max(best, v);
      }
      if (Dice.isFive(dice) && g.scores[g.turn][Dice.FIVE] == 50) {
         best += 100.0F;
      }
      return best;
   }

   private float boxValue(Dice g, int[] dice, int c, int pts) {
      int[] s = g.scores[g.turn];
      int left = 0;
      for (int v : s) {
         if (v < 0) {
            left++;
         }
      }
      float time = Math.min(1.0F, (left - 1) / 8.0F);
      float v = pts - LATER[c] * (0.55F + 0.45F * time);
      if (c < 6) {
         // progress toward the 63-point upper bonus
         int upper = 0, open = 0;
         for (int k = 0; k < 6; k++) {
            if (s[k] >= 0) {
               upper += s[k];
            } else {
               open++;
            }
         }
         if (upper < 63) {
            float par = 3.0F * (c + 1);
            v += (pts - par) * 0.9F;
            if (upper + pts >= 63) {
               v += 22.0F;
            }
         }
      }
      if (c == Dice.FIVE && pts == 0) {
         v -= 12.0F * time;
      }
      return v;
   }
}
