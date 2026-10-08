package com.formaworks.frontierhunts.livingworld.plan;

/** [livingworld] One kind of world-generated site (a /locate-able structure) with named layout variants. */
public abstract class Kind {
   public final String id;
   public final String[] variants;
   /** half-size of the footprint in blocks (piece bounds / spacing sanity) */
   public final int radius;

   protected Kind(String id, int radius, String... variants) {
      this.id = id;
      this.radius = radius;
      this.variants = variants;
   }

   /** relative weight of a variant when the world picks one */
   public int weight(int variant) {
      return 1;
   }

   public int pickVariant(Rnd r) {
      int[] w = new int[this.variants.length];
      for (int i = 0; i < w.length; i++) {
         w[i] = this.weight(i);
      }
      return r.weighted(w);
   }

   public int variant(String name) {
      for (int i = 0; i < this.variants.length; i++) {
         if (this.variants[i].equals(name)) {
            return i;
         }
      }
      return -1;
   }

   /**
    * Cheap first look with a handful of height samples (world generation and /locate call this for every candidate
    * chunk, and terrain sampling is not free): false rejects the site before the full plan is built.
    */
   public boolean quickCheck(Ctx c) {
      int r = Math.max(4, (int)(this.radius * 0.55));
      int h0 = c.floor(0, 0);
      int lo = h0;
      int hi = h0;
      for (int[] p : new int[][]{{0, 0}, {r, r}, {-r, r}, {r, -r}, {-r, -r}}) {
         if (c.wet(p[0], p[1])) {
            return false;
         }
         int h = c.floor(p[0], p[1]);
         lo = Math.min(lo, h);
         hi = Math.max(hi, h);
      }
      // [integ] nothing on beaches: not in a beach/shore biome, no open water within ~10 blocks unless well above it
      String bio = c.biome(0, 0);
      if (bio.contains("beach") || bio.contains("shore") || bio.contains("ocean") || bio.contains("coast")) {
         return false;
      }
      int sr = r + 10;
      for (int[] p : new int[][]{{sr, 0}, {-sr, 0}, {0, sr}, {0, -sr}, {sr, sr}, {-sr, sr}, {sr, -sr}, {-sr, -sr}}) {
         if (c.wet(p[0], p[1]) && h0 - c.surface(p[0], p[1]) < 6) {
            return false;
         }
      }
      return hi - lo <= 10;
   }

   /** builds the plan into {@code c}; returns false when the terrain does not suit (no structure there) */
   public abstract boolean build(Ctx c);
}
