package com.formaworks.frontierhunts.client.terrain;

/**
 * Retired smooth-terrain height field. Only the block-kind constants remain, because
 * {@link com.formaworks.frontierhunts.terrain.TerrainKinds} and tree code classify blocks with them.
 */
public final class HeightField {
   public static final int EMPTY = SmoothField.EMPTY;
   public static final int SOFT = SmoothField.SOFT;
   public static final int ROCK = SmoothField.ROCK;
   public static final int ANCHOR = SmoothField.ANCHOR;
   public static final int WATER = 4;

   private HeightField() {
   }

   /** True when a block of this kind leaves the space open (air, plants, water). */
   public static boolean open(int k) {
      return k == EMPTY || k == WATER;
   }
}
