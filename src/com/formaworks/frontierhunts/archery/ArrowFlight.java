package com.formaworks.frontierhunts.archery;

import net.minecraft.util.Mth;

/**
 * [archery2] The released arrow's flight in world space, tick for tick as the server flies it, so the client can show
 * exactly where the arrow it is about to loose will land.
 *
 * <p>Transcribes, step for step:
 * <ul>
 *   <li>the launch: {@code Projectile.shootFromRotation(shooter, xRot, yRot, 0, speed, 0)} - float trigonometry through
 *       Mojang's sine table, normalised, scaled by the speed - from the spawn point (eye, or 0.1 below it for the Field
 *       Recurve's {@code FieldArrow});</li>
 *   <li>{@link BowBallistics.Drag#QUADRATIC} ({@code hunting.FieldArrow.integrate}): drag on the air-relative velocity
 *       (wind / 20 per tick), 0.28 in water, gravity 0.024525;</li>
 *   <li>{@link BowBallistics.Drag#LINEAR} ({@code expedition.HuntProjectile}): x0.993 (x0.76 in water), wind x 1e-4,
 *       gravity 0.035.</li>
 * </ul>
 * Each tick the straight segment from the position to position + velocity is tested for a hit first (blocks, then the
 * nearest entity), exactly like both entities do. Pure maths plus a {@link Collider}; the offline harness checks it
 * against an independent copy of the server code.
 */
public final class ArrowFlight {
   /** Hit test for one tick's straight segment. */
   public interface Collider {
      /** Fraction (0..1) along a->b of the first thing the arrow would strike, or NaN if the segment is clear. */
      double hit(double ax, double ay, double az, double bx, double by, double bz);

      /** Whether the arrow at this point counts as in water (drag). */
      default boolean water(double x, double y, double z) {
         return false;
      }
   }

   /** Where the flight ended. {@code hit} false = still flying after {@code ticks} (out of range). */
   public record Impact(double x, double y, double z, double vx, double vy, double vz, int ticks, boolean hit) {
   }

   private ArrowFlight() {
   }

   /** Launch velocity of {@code Projectile.shootFromRotation(s, xRot, yRot, 0, speed, 0)} (no shooter movement). */
   public static double[] launch(float xRot, float yRot, float speed) {
      float deg = (float)(Math.PI / 180.0);
      float fx = -Mth.sin(yRot * deg) * Mth.cos(xRot * deg);
      float fy = -Mth.sin((xRot + 0.0F) * deg);
      float fz = Mth.cos(yRot * deg) * Mth.cos(xRot * deg);
      double x = fx;
      double y = fy;
      double z = fz;
      double len = Math.sqrt(x * x + y * y + z * z);
      if (len < 1.0E-4) {
         return new double[]{0.0, 0.0, 0.0};
      }
      return new double[]{x / len * speed, y / len * speed, z / len * speed};
   }

   /** Launch speed for a draw (0..1) as the server computes it: Field Recurve 2.75 x sqrt(draw), the others speed x draw. */
   public static float speed(BowBallistics.Profile p, float draw) {
      float d = Math.clamp(draw, 0.0F, 1.0F);
      return p.drag() == BowBallistics.Drag.QUADRATIC ? 2.75F * (float)Math.sqrt(d) : (float)(p.speed() * d);
   }

   /** One server tick of velocity change (after the move). {@code windEast/South} in m/s as Wilderness.Wind gives them. */
   public static void step(BowBallistics.Profile p, double[] v, double windEast, double windSouth, boolean water) {
      if (p.drag() == BowBallistics.Drag.QUADRATIC) {
         double rx = v[0] - windEast / 20.0;
         double ry = v[1];
         double rz = v[2] - windSouth / 20.0;
         double k = water ? 0.28 : Math.min(0.035, Math.sqrt(rx * rx + ry * ry + rz * rz) * 0.0022);
         v[0] = v[0] - rx * k;
         v[1] = v[1] - ry * k + -p.gravity();
         v[2] = v[2] - rz * k;
      } else {
         double s = water ? 0.76 : 0.993;
         v[0] = v[0] * s + windEast * 1.0E-4;
         v[1] = v[1] * s + -p.gravity();
         v[2] = v[2] * s + windSouth * 1.0E-4;
      }
   }

   /**
    * Fly an arrow from spawn point (x, y, z) with launch velocity {@code v} until it strikes something ({@code c}) or
    * {@code maxTicks} pass. {@code v} is consumed.
    */
   public static Impact fly(BowBallistics.Profile p, double x, double y, double z, double[] v, double windEast, double windSouth, Collider c, int maxTicks) {
      for (int t = 0; t < maxTicks; t++) {
         double bx = x + v[0];
         double by = y + v[1];
         double bz = z + v[2];
         double f = c.hit(x, y, z, bx, by, bz);
         if (!Double.isNaN(f)) {
            f = Math.clamp(f, 0.0, 1.0);
            return new Impact(x + (bx - x) * f, y + (by - y) * f, z + (bz - z) * f, v[0], v[1], v[2], t, true);
         }
         boolean water = c.water(x, y, z);
         x = bx;
         y = by;
         z = bz;
         step(p, v, windEast, windSouth, water);
         if (!Double.isFinite(x + y + z)) {
            break;
         }
      }
      return new Impact(x, y, z, v[0], v[1], v[2], maxTicks, false);
   }

   /** Spawn height below the eye for this bow's arrow entity. */
   public static double spawnDrop(BowBallistics.Profile p) {
      return p.originDrop();
   }
}
