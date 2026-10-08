package com.formaworks.frontierhunts.perf.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * [perf] A per-frame budget of expensive detail: at most {@code limit} animals of one kind get it, the
 * ones biggest on screen. Each renders its score (larger = more important, e.g. 1 / effective distance)
 * through {@link #admit}; the cut-off is the limit-th best score of the previous frame (one frame late,
 * which never shows), with a small hysteresis so two animals at nearly the same distance don't trade.
 */
public final class RenderBudget {
   private static final List<RenderBudget> ALL = new ArrayList<>();

   private final float[] scores = new float[512];
   private final float[] scratch = new float[512]; // [perf2]
   private int count;
   private float cut;
   private int limit;

   public RenderBudget(int limit) {
      this.limit = limit;
      synchronized (ALL) {
         ALL.add(this);
      }
   }

   public void limit(int limit) {
      this.limit = Math.max(0, limit);
   }

   static void beginFrame() {
      synchronized (ALL) {
         for (RenderBudget b : ALL) b.roll();
      }
   }

   private void roll() {
      int n = this.count;
      if (n > this.limit && this.limit > 0) {
         // [perf2] sorted in a reused scratch array (was a new copy every frame per budget)
         float[] s = this.scratch;
         System.arraycopy(this.scores, 0, s, 0, n);
         Arrays.sort(s, 0, n);
         this.cut = s[n - this.limit];
      } else {
         this.cut = this.limit == 0 && n > 0 ? Float.MAX_VALUE : 0;
      }
      this.count = 0;
   }

   /**
    * Records this frame's score and says whether the detail is granted: {@code had} is whether it had it
    * last frame (it keeps it down to 90% of the cut-off).
    */
   public boolean admit(float score, boolean had) {
      if (this.count < this.scores.length) this.scores[this.count++] = score;
      return score >= (had ? this.cut * 0.9F : this.cut);
   }
}
