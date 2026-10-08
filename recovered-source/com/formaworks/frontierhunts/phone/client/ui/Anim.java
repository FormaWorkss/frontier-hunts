package com.formaworks.frontierhunts.phone.client.ui;

/**
 * [phone] Small animated values keyed by a long (toggle knobs, segmented highlights, hover glows): each frame a value
 * eases toward its target. Open addressing on primitive arrays: no boxing, no allocation while drawing.
 */
final class Anim {
   private static final int SIZE = 1024;
   private final long[] keys = new long[SIZE];
   private final float[] vals = new float[SIZE];
   private final boolean[] used = new boolean[SIZE];
   private final int[] seen = new int[SIZE];
   private int count;
   private int frame;
   float dt = 1.0F / 60.0F;

   void frame(float dt) {
      this.dt = dt;
      this.frame++;
      if (this.count > SIZE * 3 / 4) {
         // forget values not used for a while
         for (int i = 0; i < SIZE; i++) {
            if (this.used[i] && this.frame - this.seen[i] > 120) {
               this.used[i] = false;
               this.count--;
            }
         }
         // rehash survivors
         long[] k = this.keys.clone();
         float[] v = this.vals.clone();
         boolean[] u = this.used.clone();
         int[] s = this.seen.clone();
         java.util.Arrays.fill(this.used, false);
         this.count = 0;
         for (int i = 0; i < SIZE; i++) {
            if (u[i]) {
               int j = this.slot(k[i]);
               this.keys[j] = k[i];
               this.vals[j] = v[i];
               this.seen[j] = s[i];
               this.used[j] = true;
               this.count++;
            }
         }
      }
   }

   private int slot(long key) {
      int h = (int)(key ^ key >>> 32) * 0x9E3779B1;
      int i = h >>> 22 & SIZE - 1;
      while (this.used[i] && this.keys[i] != key) {
         i = i + 1 & SIZE - 1;
      }
      return i;
   }

   float get(long key, float target, float rate) {
      int i = this.slot(key);
      if (!this.used[i]) {
         if (this.count >= SIZE - 8) {
            return target;
         }
         this.used[i] = true;
         this.keys[i] = key;
         this.vals[i] = target;
         this.count++;
      } else {
         this.vals[i] = Theme.approach(this.vals[i], target, rate, this.dt);
         if (Math.abs(this.vals[i] - target) < 0.001F) {
            this.vals[i] = target;
         }
      }
      this.seen[i] = this.frame;
      return this.vals[i];
   }
}
