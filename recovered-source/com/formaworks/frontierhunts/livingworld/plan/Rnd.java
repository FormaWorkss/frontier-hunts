package com.formaworks.frontierhunts.livingworld.plan;

/**
 * [livingworld] Small deterministic random source (SplitMix64). The plan layer is pure Java (no Minecraft classes) so the
 * same builders run inside world generation and in the offline preview harness; every structure is rebuilt from the same
 * seed for each chunk it touches, so all randomness must come from here.
 */
public final class Rnd {
   private long state;

   public Rnd(long seed) {
      this.state = seed ^ 0x9E3779B97F4A7C15L;
   }

   public long nextLong() {
      long z = (this.state += 0x9E3779B97F4A7C15L);
      z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
      z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
      return z ^ (z >>> 31);
   }

   /** 0 <= n < bound */
   public int nextInt(int bound) {
      if (bound <= 1) {
         return 0;
      }
      return (int)Long.remainderUnsigned(this.nextLong(), bound);
   }

   /** lo..hi inclusive */
   public int range(int lo, int hi) {
      return hi <= lo ? lo : lo + this.nextInt(hi - lo + 1);
   }

   public double nextDouble() {
      return (this.nextLong() >>> 11) * 0x1.0p-53;
   }

   public double range(double lo, double hi) {
      return lo + (hi - lo) * this.nextDouble();
   }

   public boolean chance(double p) {
      return this.nextDouble() < p;
   }

   @SafeVarargs
   public final <T> T pick(T... options) {
      return options[this.nextInt(options.length)];
   }

   public <T> T pick(java.util.List<T> options) {
      return options.get(this.nextInt(options.size()));
   }

   /** weighted index: weights[i] >= 0 */
   public int weighted(int... weights) {
      int total = 0;
      for (int w : weights) {
         total += Math.max(0, w);
      }
      int r = this.nextInt(Math.max(1, total));
      for (int i = 0; i < weights.length; i++) {
         r -= Math.max(0, weights[i]);
         if (r < 0) {
            return i;
         }
      }
      return 0;
   }

   /** an independent stream for a sub-element (keeps element layouts stable when another element changes) */
   public Rnd fork(long salt) {
      return new Rnd(this.nextLong() * 31L + salt);
   }

   public static long mix(long a, long b) {
      long z = a * 0x9E3779B97F4A7C15L + b;
      z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
      z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
      return z ^ (z >>> 31);
   }
}
