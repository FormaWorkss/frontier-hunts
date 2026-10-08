package com.formaworks.frontierhunts.livingworld.plan;

/** [livingworld] Randomised, collision-free placement of camp pieces around a centre (local coordinates). */
public final class Place {
   public final int x;
   public final int z;
   public final Dir f;
   public final int[] rect;

   Place(int x, int z, Dir f, int[] rect) {
      this.x = x;
      this.z = z;
      this.f = f;
      this.rect = rect;
   }

   /**
    * Finds a spot in the ring rMin..rMax (blocks from the centre) between bearings aMin..aMax (degrees, 0 = local north,
    * clockwise) for a box (north-facing extents bx0..bx1, bz0..bz1 around its origin). The piece faces the centre when
    * {@code face} is null, otherwise {@code face}; {@code inward} = false faces away. Returns null if nothing fits.
    */
   public static Place ring(Layout l, Rnd r, int cx, int cz, double rMin, double rMax, double aMin, double aMax, int bx0, int bz0, int bx1, int bz1,
      int margin, Dir face, boolean inward, java.util.function.IntBinaryOperator inside) {
      for (int attempt = 0; attempt < 48; attempt++) {
         double a = Math.toRadians(r.range(aMin, aMax));
         double rad = r.range(rMin, rMax);
         int x = cx + (int)Math.round(Math.sin(a) * rad);
         int z = cz - (int)Math.round(Math.cos(a) * rad);
         Dir f = face != null ? face : Layout.toward(x, z, cx, cz);
         if (face == null && !inward) {
            f = f.opposite();
         }
         int[] rect = Layout.rect(x, z, bx0, bz0, bx1, bz1, f);
         if (!l.fits(rect, margin)) {
            continue;
         }
         if (inside != null && (inside.applyAsInt(rect[0], rect[1]) == 0 || inside.applyAsInt(rect[2], rect[3]) == 0
            || inside.applyAsInt(rect[0], rect[3]) == 0 || inside.applyAsInt(rect[2], rect[1]) == 0)) {
            continue;
         }
         l.take(rect);
         return new Place(x, z, f, rect);
      }
      return null;
   }

   /** ellipse membership test for {@link #ring} */
   public static java.util.function.IntBinaryOperator ellipse(double rx, double rz) {
      return (x, z) -> (x / rx) * (x / rx) + (z / rz) * (z / rz) <= 1.0 ? 1 : 0;
   }
}
