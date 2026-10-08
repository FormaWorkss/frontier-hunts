package com.formaworks.frontierhunts.client.tree;

/**
 * [1.2.2] Snow lying on the realistic trees: soft clumps of snow resting on the sprays that face the sky, and a cold
 * frost on the rest of the crown. Pure geometry (no game classes) so the offline tree preview draws exactly what the
 * game does.
 *
 * <p>A clump is laid on a spray card: the card's own quad, drawn in towards its middle, flattened toward the level
 * (snow settles flat even on a drooping bough) and lifted just clear of the needles, wearing one of four soft-edged
 * clumps of the {@code tree_snow} texture. Back faces are culled, so from below a bough stays green and from above it
 * is white - as a snowy spruce looks.
 */
public final class TreeSnow {
   private TreeSnow() {
   }

   /** quadrant corners of the tree_snow texture, one clump in each */
   private static final float[][] CLUMP = {{0.0F, 0.0F}, {0.5F, 0.0F}, {0.0F, 0.5F}, {0.5F, 0.5F}};

   /** a clump to lay on a card, or null for none */
   public static final class Pad {
      /** x, y, z of 4 corners (in the card's corner order) */
      public final float[] xyz = new float[12];
      /** u, v of 4 corners in the tree_snow texture (0..1) */
      public final float[] uv = new float[8];
      /** brightness (sky-facing clumps a touch brighter) */
      public float shade;
   }

   /**
    * how likely snow lies on a card: by how much it faces up and how open to the sky it is
    *
    * @param amount 0 no snow .. 1 deep winter, snowing
    */
   public static float cover(float amount, float ny, float sky) {
      if (amount <= 0.0F || ny < 0.08F) {
         return 0.0F;
      }
      float up = Math.min(1.0F, (ny - 0.08F) / 0.42F);
      up = up * up * (3.0F - 2.0F * up);
      return Math.min(0.95F, amount * up * (0.6F + 0.5F * sky));
   }

   /**
    * the clump for a card
    *
    * @param v the card's corners as x, y, z, u, v (u, v relative to its texture, 0..1), 5 floats each
    * @param hash a stable per-card number (picks the clump and its size)
    */
   public static Pad pad(float[] v, float ny, float sky, int hash) {
      float cx = 0, cy = 0, cz = 0;
      for (int i = 0; i < 4; i++) {
         cx += v[i * 5];
         cy += v[i * 5 + 1];
         cz += v[i * 5 + 2];
      }
      cx *= 0.25F;
      cy *= 0.25F;
      cz *= 0.25F;
      Pad p = new Pad();
      float size = 0.92F + 0.2F * ((hash >>> 8 & 255) / 255.0F);
      float flat = 0.42F;
      float lift = 0.035F + 0.03F * ((hash >>> 16 & 255) / 255.0F);
      float[] q = CLUMP[hash & 3];
      for (int i = 0; i < 4; i++) {
         float x = cx + (v[i * 5] - cx) * size, y = cy + (v[i * 5 + 1] - cy) * size, z = cz + (v[i * 5 + 2] - cz) * size;
         y = cy + (y - cy) * flat + lift;
         p.xyz[i * 3] = x;
         p.xyz[i * 3 + 1] = y;
         p.xyz[i * 3 + 2] = z;
         float u = Math.max(0.0F, Math.min(1.0F, v[i * 5 + 3])), w = Math.max(0.0F, Math.min(1.0F, v[i * 5 + 4]));
         p.uv[i * 2] = q[0] + 0.004F + u * 0.492F;
         p.uv[i * 2 + 1] = q[1] + 0.004F + w * 0.492F;
      }
      p.shade = 0.9F + 0.1F * Math.min(1.0F, ny);
      return p;
   }

   /** the frost on a card's colour: how far toward cold white (0..1), more on what faces up and out */
   public static float frost(float amount, float ny, float sky) {
      return amount * (0.22F + 0.3F * Math.max(0.0F, ny) * (0.4F + 0.6F * sky));
   }

   /** a cheap stable hash of a card's middle */
   public static int hash(float x, float y, float z) {
      int h = Math.round(x * 64.0F) * 0x27D4EB2D ^ Math.round(y * 61.0F) * 0x165667B1 ^ Math.round(z * 67.0F) * 0x9E3779B9;
      h ^= h >>> 15;
      h *= 0x85EBCA6B;
      h ^= h >>> 13;
      return h;
   }

   /** 0..1 from a hash */
   public static float unit(int h) {
      return (h >>> 8) / (float)(1 << 24);
   }
}
