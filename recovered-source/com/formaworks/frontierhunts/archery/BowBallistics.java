package com.formaworks.frontierhunts.archery;

/**
 * [bows] Arrow flight, exactly as the server integrates it, and the sight maths built on it.
 *
 * <p>Pure maths (no Minecraft types) so the offline harness in tools/bows can verify it. Two integrators exist in the
 * mod and both are mirrored here step for step (no wind, out of water - a sight can no more read the wind than a real
 * one):
 * <ul>
 *   <li>{@link Drag#QUADRATIC}: {@code hunting.FieldArrow} (Field Recurve Bow). Spawned 0.1 below the eye; per tick
 *       {@code p += v; v -= v * min(0.035, |v| * 0.0022); v.y -= 0.024525}.</li>
 *   <li>{@link Drag#LINEAR}: {@code expedition.HuntProjectile} (recurve, compound, crossbow, bowfishing). Spawned at the
 *       eye; per tick {@code p += v; v = v * 0.993; v.y -= 0.035}.</li>
 * </ul>
 * Distances are blocks (= metres), angles radians, pitch positive UP (Minecraft's xRot is the negative of this).
 */
public final class BowBallistics {
   public enum Drag {
      QUADRATIC,
      LINEAR
   }

   /** One bow's full-draw flight. {@code originDrop}: how far below the eye the arrow spawns. */
   public record Profile(String id, double speed, double gravity, Drag drag, double originDrop) {
   }

   public static final Profile FIELD_RECURVE = new Profile("field_bow", 2.75, 0.024525, Drag.QUADRATIC, 0.1);
   public static final Profile RECURVE = new Profile("recurve_bow", 2.8, 0.035, Drag.LINEAR, 0.0);
   public static final Profile COMPOUND = new Profile("compound_bow", 3.6, 0.035, Drag.LINEAR, 0.0);
   public static final Profile CROSSBOW = new Profile("crossbow", 4.2, 0.035, Drag.LINEAR, 0.0);
   public static final Profile BOWFISHING = new Profile("bowfishing_bow", 2.6, 0.035, Drag.LINEAR, 0.0);
   public static final Profile[] ALL = new Profile[]{FIELD_RECURVE, RECURVE, COMPOUND, CROSSBOW, BOWFISHING};

   /** Longest flight followed, in ticks (both entities live far longer; 400 ticks is beyond any useful range). */
   private static final int MAX_TICKS = 400;

   private BowBallistics() {
   }

   /** Weapon id ({@code Weapon.id()} or "field_bow") to its profile; null for anything that is not a bow. */
   public static Profile forId(String id) {
      for (Profile p : ALL) {
         if (p.id.equals(id)) {
            return p;
         }
      }
      return null;
   }

   /** One server tick of velocity change (vx = horizontal speed in the plane of the shot, vy vertical). */
   static void step(Profile p, double[] v) {
      if (p.drag == Drag.QUADRATIC) {
         double len = Math.sqrt(v[0] * v[0] + v[1] * v[1]);
         double k = Math.min(0.035, len * 0.0022);
         v[0] -= v[0] * k;
         v[1] -= v[1] * k;
         v[1] += -p.gravity;
      } else {
         v[0] = v[0] * 0.993;
         v[1] = v[1] * 0.993 + -p.gravity;
      }
   }

   /**
    * Height (relative to the eye) at which the arrow crosses horizontal distance {@code range}, launched at
    * {@code pitch} with {@code speed}; NaN if it never gets there. The flight is the server's straight segment per tick,
    * so a target standing at that distance is struck exactly here.
    */
   public static double heightAt(Profile p, double pitch, double speed, double range) {
      double x = 0.0;
      double y = -p.originDrop;
      double[] v = new double[]{Math.cos(pitch) * speed, Math.sin(pitch) * speed};
      for (int t = 0; t < MAX_TICKS; t++) {
         double nx = x + v[0];
         double ny = y + v[1];
         if (nx >= range) {
            double f = (range - x) / (nx - x);
            return y + (ny - y) * f;
         }
         x = nx;
         y = ny;
         step(p, v);
         if (v[0] <= 1.0E-6) {
            return Double.NaN;
         }
      }
      return Double.NaN;
   }

   /** Ticks of flight to horizontal distance {@code range} (fractional), NaN if never. */
   public static double timeTo(Profile p, double pitch, double speed, double range) {
      double x = 0.0;
      double[] v = new double[]{Math.cos(pitch) * speed, Math.sin(pitch) * speed};
      for (int t = 0; t < MAX_TICKS; t++) {
         double nx = x + v[0];
         if (nx >= range) {
            return t + (range - x) / (nx - x);
         }
         x = nx;
         step(p, v);
      }
      return Double.NaN;
   }

   /**
    * Launch pitch that puts the arrow through the point at slant distance {@code distance} along the line of sight
    * {@code losPitch} (both from the eye). NaN if out of reach.
    */
   public static double launchFor(Profile p, double speed, double losPitch, double distance) {
      double range = distance * Math.cos(losPitch);
      double want = distance * Math.sin(losPitch);
      if (range < 0.05) {
         return losPitch;
      }
      double lo = losPitch - 0.05;
      double hi = Math.min(Math.PI / 2.0 - 0.05, losPitch + 0.7);
      double mLo = heightAt(p, lo, speed, range) - want;
      double mHi = heightAt(p, hi, speed, range) - want;
      if (!(mLo <= 0.0) || !(mHi >= 0.0)) {
         return Double.NaN;
      }
      for (int i = 0; i < 60; i++) {
         double mid = 0.5 * (lo + hi);
         double m = heightAt(p, mid, speed, range) - want;
         if (Double.isNaN(m) || m < 0.0) {
            lo = mid;
         } else {
            hi = mid;
         }
         if (hi - lo < 1.0E-9) {
            break;
         }
      }
      return 0.5 * (lo + hi);
   }

   /**
    * How far below the centre of view (the arrow's launch line) the pin for {@code distance} sits, given the bow is
    * held at {@code viewPitch}: the pin's own sight line is {@code viewPitch - angle}, and the arrow launched along
    * {@code viewPitch} passes through the target at {@code distance} on that sight line. Solved as a fixed point (the
    * pin moves a hair with the bow's angle, so shots up and down hills hit too). NaN if out of reach.
    */
   public static double pinAngle(Profile p, double speed, double viewPitch, double distance) {
      double e = launchFor(p, speed, 0.0, distance); // the level pin: a close first guess at any angle
      if (Double.isNaN(e)) {
         return Double.NaN;
      }
      for (int i = 0; i < 16; i++) {
         double launch = launchFor(p, speed, viewPitch - e, distance);
         if (Double.isNaN(launch)) {
            return Double.NaN;
         }
         double next = launch - (viewPitch - e);
         if (Math.abs(next - e) < 1.0E-10) {
            return next;
         }
         e = next;
      }
      return e;
   }

   private static final java.util.Map<Long, Double> TABLE = new java.util.HashMap<>();
   /** Grid spacing of the pin table, degrees of bow angle. */
   static final double TABLE_STEP = 0.5;

   /**
    * {@link #pinAngle} through a lazily filled table (every 0.5 degrees of bow angle, linearly interpolated): what the
    * client calls every frame. The interpolation error is far below a pixel (the harness checks the hits through it).
    */
   public static synchronized double pinAngleAt(Profile p, double distance, double viewPitch) {
      double g = (Math.toDegrees(viewPitch) + 90.0) / TABLE_STEP;
      if (!Double.isFinite(g)) {
         return Double.NaN;
      }
      int i = (int)Math.floor(Math.max(0.0, Math.min(359.0, g)));
      double f = Math.max(0.0, Math.min(1.0, g - i));
      double a = grid(p, distance, i);
      double b = grid(p, distance, i + 1);
      if (Double.isNaN(a) || Double.isNaN(b)) {
         return pinAngle(p, p.speed, viewPitch, distance);
      }
      return a + (b - a) * f;
   }

   private static double grid(Profile p, double distance, int i) {
      int pi = 0;
      while (pi < ALL.length && ALL[pi] != p) {
         pi++;
      }
      long key = ((long)pi << 40) | ((long)Math.round(distance * 16.0) << 16) | (long)i;
      Double v = TABLE.get(key);
      if (v == null) {
         if (TABLE.size() > 20000) {
            TABLE.clear();
         }
         v = pinAngle(p, p.speed, Math.toRadians(-90.0 + i * TABLE_STEP), distance);
         TABLE.put(key, v);
      }
      return v;
   }

   /**
    * Signed miss perpendicular to the line of sight (blocks, positive = high) at slant distance {@code distance} when
    * the pin at {@code pinAngle} is held on a target that far away and the bow is at {@code viewPitch}.
    */
   public static double miss(Profile p, double speed, double viewPitch, double pinAngle, double distance) {
      double los = viewPitch - pinAngle;
      double range = distance * Math.cos(los);
      double h = heightAt(p, viewPitch, speed, range);
      double vertical = h - distance * Math.sin(los);
      return vertical * Math.cos(los);
   }

   /**
    * Random spread ("inaccuracy" as Projectile.shoot takes it) of a released arrow. A full, steady draw from a standing
    * still archer flies exactly where the sight says (0). A short draw, a fatigued hold (the visible sway is the main
    * cost of fatigue; this is just the release tremor), walking and jumping add to it.
    */
   public static float spread(float draw, float strain, double horizontalSpeed, boolean airborne) {
      float s = 0.0F;
      if (draw < 0.98F) {
         s += 0.6F * (1.0F - Math.max(0.0F, draw));
      }
      s += 0.12F * Math.min(2.4F, Math.max(0.0F, strain));
      if (horizontalSpeed > 0.02) {
         s += (float)Math.min(1.2, horizontalSpeed * 4.5);
      }
      if (airborne) {
         s += 1.1F;
      }
      return s;
   }
}
