import com.formaworks.frontierhunts.archery.BowBallistics;
import com.formaworks.frontierhunts.archery.SightOptics;

/**
 * [bows] Offline check of the bow sights.
 *
 * <p>For every bow profile, every arrow head, every pin distance offered in the settings, several bow angles
 * (level, up and down hill) and several compass headings, it:
 * <ol>
 *   <li>solves the pin angle with {@link BowBallistics#pinAngle} (what the client draws),</li>
 *   <li>flies the arrow with an independent transcription of the SERVER code (FieldArrow / HuntProjectile tick,
 *       Projectile.shootFromRotation with Minecraft's 65536-entry sine table, float rotations and speeds),
 *       launched along the view with the pin held on a target at the pin distance on the pin's sight line,</li>
 *   <li>measures the miss at the target (perpendicular to the sight line) and fails if it is 0.1 block or more.</li>
 * </ol>
 * It also checks the hand/world FOV mapping (a hand-pass pin covers the world spot) and that a full steady draw has
 * zero random spread. Run: see docs/ws/bows.md.
 */
public final class BallisticHarness {
   static final String[] TIPS = {
      "field_point", "fixed_broadhead", "mechanical_broadhead", "cut_on_contact_broadhead", "judo_point", "flint_point",
      "obsidian_point", "bone_point", "tracer_broadhead", "tracer_field_point", "tracer_ice_broadhead"
   };
   static final int[][] PIN_SETS = {{10, 20, 30}, {20, 30, 40}, {20, 30, 40, 50}, {30, 40, 50, 60}};

   // ---- Minecraft's Mth sine table (as used by Projectile.shootFromRotation)
   static final float[] SIN = new float[65536];
   static {
      for (int i = 0; i < SIN.length; i++) {
         SIN[i] = (float)Math.sin(i * Math.PI * 2.0 / 65536.0);
      }
   }

   static float mthSin(float v) {
      return SIN[(int)(v * 10430.378F) & 65535];
   }

   static float mthCos(float v) {
      return SIN[(int)(v * 10430.378F + 16384.0F) & 65535];
   }

   /**
    * Server flight transcription. Returns the miss (blocks) perpendicular to the sight line at the target, which
    * stands at slant distance d along (yaw, losPitchDeg) from the eye.
    */
   static double serverMiss(BowBallistics.Profile p, float yawDeg, float xRot, double losPitchDeg, double d) {
      // Projectile.shootFromRotation(shooter, xRot, yRot, 0, velocity, 0)
      float rad = (float)(Math.PI / 180.0);
      float fx = -mthSin(yawDeg * rad) * mthCos(xRot * rad);
      float fy = -mthSin((xRot + 0.0F) * rad);
      float fz = mthCos(yawDeg * rad) * mthCos(xRot * rad);
      double len = Math.sqrt((double)fx * fx + (double)fy * fy + (double)fz * fz);
      float speed = p.drag() == BowBallistics.Drag.QUADRATIC ? 2.75F * (float)Math.sqrt(1.0) : (float)p.speed() * 1.0F;
      double vx = fx / len * speed, vy = fy / len * speed, vz = fz / len * speed;
      // spawn: FieldArrow at eyeY - 0.1, HuntProjectile at the eye
      double x = 0, y = p.drag() == BowBallistics.Drag.QUADRATIC ? -0.1 : 0.0, z = 0;
      double los = Math.toRadians(losPitchDeg);
      double hx = -Math.sin(Math.toRadians(yawDeg)), hz = Math.cos(Math.toRadians(yawDeg));
      double tRange = d * Math.cos(los);
      double tY = d * Math.sin(los);
      for (int t = 0; t < 400; t++) {
         double nx = x + vx, ny = y + vy, nz = z + vz;
         double r0 = x * hx + z * hz, r1 = nx * hx + nz * hz;
         if (r1 >= tRange) {
            double f = (tRange - r0) / (r1 - r0);
            double hy = y + (ny - y) * f;
            return (hy - tY) * Math.cos(los);
         }
         x = nx;
         y = ny;
         z = nz;
         if (p.drag() == BowBallistics.Drag.QUADRATIC) {
            // FieldArrow.integrate(v, wind 0, inWater false)
            double l = Math.sqrt(vx * vx + vy * vy + vz * vz);
            double k = Math.min(0.035, l * 0.0022);
            vx = vx - vx * k;
            vy = vy - vy * k + -0.024525;
            vz = vz - vz * k;
         } else {
            // HuntProjectile: v.scale(0.993).add(wind*1e-4 (0), -0.035, wind*1e-4 (0))
            vx = vx * 0.993;
            vy = vy * 0.993 + -0.035;
            vz = vz * 0.993;
         }
      }
      return Double.NaN;
   }

   public static void main(String[] args) {
      int checks = 0;
      int fails = 0;
      double worst = 0;
      String worstAt = "";
      float[] yaws = {0F, 37.5F, 135F, -100.25F, 179.9F};
      double[] views = {0.0, 8.0, -8.0, 25.0, -25.0, 40.0, -40.0};
      System.out.println("bow               pins(m)       pin angles at level (deg)");
      for (BowBallistics.Profile p : BowBallistics.ALL) {
         for (int[] set : PIN_SETS) {
            StringBuilder sb = new StringBuilder();
            for (int d : set) {
               sb.append(String.format("%6.3f", Math.toDegrees(BowBallistics.pinAngle(p, p.speed(), 0.0, d))));
            }
            System.out.printf("%-16s %-14s %s%n", p.id(), java.util.Arrays.toString(set), sb);
            for (String tip : TIPS) {
               // arrow heads change damage, bleeding and recovery, never the flight: every head shares the bow's profile
               for (int d : set) {
                  for (double viewDeg : views) {
                     if (p == BowBallistics.BOWFISHING && (d > 40 || Math.abs(viewDeg) > 25.0)) {
                        continue; // bowfishing: no sight, short shots at fish - only its real range is checked
                     }
                     double view = Math.toRadians(viewDeg);
                     double pin = BowBallistics.pinAngle(p, p.speed(), view, d);
                     if (Double.isNaN(pin)) {
                        fails++;
                        System.out.println("  unreachable " + p.id() + " " + d + " m at " + viewDeg);
                        continue;
                     }
                     double losDeg = Math.toDegrees(view - pin);
                     for (float yaw : yaws) {
                        float xRot = (float)-viewDeg; // the bow points where the player looks
                        double m = serverMiss(p, yaw, xRot, losDeg, d);
                        double own = BowBallistics.miss(p, p.speed(), view, pin, d);
                        checks++;
                        double err = Math.abs(m);
                        if (!(err < 0.1) || !(Math.abs(own) < 1.0E-6)) {
                           fails++;
                           System.out.printf("  FAIL %s %s %d m view %.0f yaw %.1f: server miss %.4f, model miss %.2e%n", p.id(), tip, d, viewDeg, yaw, m, own);
                        }
                        if (err > worst) {
                           worst = err;
                           worstAt = p.id() + " " + d + " m, bow at " + viewDeg + " deg, heading " + yaw;
                        }
                     }
                  }
               }
            }
         }
      }
      // the client reads pins through the interpolated table: check hits through it at odd bow angles too
      java.util.Random rnd = new java.util.Random(7);
      int tableShots = 0;
      double tableWorst = 0;
      for (BowBallistics.Profile p : BowBallistics.ALL) {
         for (int[] set : PIN_SETS) {
            for (int d : set) {
               if (p == BowBallistics.BOWFISHING && d > 40) {
                  continue;
               }
               for (int k = 0; k < 40; k++) {
                  double viewDeg = (rnd.nextDouble() * 2 - 1) * (p == BowBallistics.BOWFISHING ? 25 : 40);
                  double view = Math.toRadians(viewDeg);
                  double pin = BowBallistics.pinAngleAt(p, d, view);
                  double m = serverMiss(p, (float)(rnd.nextDouble() * 360 - 180), (float)-viewDeg, Math.toDegrees(view - pin), d);
                  tableShots++;
                  checks++;
                  if (!(Math.abs(m) < 0.1)) {
                     fails++;
                     System.out.printf("  FAIL table %s %d m view %.2f: miss %.4f%n", p.id(), d, viewDeg, m);
                  }
                  tableWorst = Math.max(tableWorst, Math.abs(m));
               }
            }
         }
      }
      System.out.printf("pin table: %d shots, worst |miss| %.4f block%n", tableShots, tableWorst);
      // [1.2.5] traditional bows shoot where the crosshair is: launched at PointOfAim's holdover for the distance of
      // what's under the crosshair, the arrow (real server flight, from its real spawn point) must land on that point
      int poaShots = 0;
      double poaWorst = 0;
      String poaAt = "";
      for (BowBallistics.Profile p : new BowBallistics.Profile[]{BowBallistics.FIELD_RECURVE, BowBallistics.RECURVE}) {
         for (int d : new int[]{3, 5, 10, 15, 20, 30, 40, 50, 60, 70, 80}) {
            for (double viewDeg : new double[]{-60, -40, -25, -8, 0, 8, 25, 40, 60}) {
               double launch = BowBallistics.launchFor(p, p.speed(), Math.toRadians(viewDeg), d);
               if (Double.isNaN(launch)) {
                  continue; // out of the bow's reach: it shoots along the view
               }
               float xRot = Math.max((float)-viewDeg - 30.0F, Math.min((float)-viewDeg + 2.0F, (float)-Math.toDegrees(launch)));
               for (float yaw : yaws) {
                  double m = serverMiss(p, yaw, xRot, viewDeg, d);
                  poaShots++;
                  checks++;
                  if (!(Math.abs(m) < 0.1)) {
                     fails++;
                     System.out.printf("  FAIL crosshair %s %d m view %.0f yaw %.1f: miss %.4f%n", p.id(), d, viewDeg, yaw, m);
                  }
                  if (Math.abs(m) > poaWorst) {
                     poaWorst = Math.abs(m);
                     poaAt = p.id() + " " + d + " m at " + viewDeg + " deg";
                  }
               }
            }
         }
      }
      System.out.printf("traditional bows on the crosshair: %d shots, worst |miss| %.4f block (%s)%n", poaShots, poaWorst, poaAt);
      // and how far off they were before (launched straight along the view)
      for (BowBallistics.Profile p : new BowBallistics.Profile[]{BowBallistics.FIELD_RECURVE, BowBallistics.RECURVE}) {
         StringBuilder sb = new StringBuilder();
         for (int d : new int[]{10, 20, 30, 40, 60}) {
            sb.append(String.format(" %dm %.2f", d, -serverMiss(p, 0F, 0F, 0.0, d)));
         }
         System.out.printf("  before 1.2.5, %s landed this far below the crosshair (blocks):%s%n", p.id(), sb);
      }
      System.out.printf("pin impact: %d shots, %d failed, worst |miss| %.4f block (%s)%n", checks, fails, worst, worstAt);

      // hand/world FOV mapping: a hand-pass pin at depth 0.825 covers the same pixel as the world spot
      double worstPx = 0;
      for (double wf : new double[]{30, 50, 70, 90, 110}) {
         for (double hf : new double[]{59.8, 70}) {
            for (double a : new double[]{0.005, 0.02, 0.05, 0.09}) {
               double depth = 0.825;
               double drop = SightOptics.handDrop(a, depth, wf, hf);
               double handPx = (drop / depth) / Math.tan(Math.toRadians(hf) / 2) * 540;
               double worldPx = SightOptics.screenDrop(a, wf, 1080);
               worstPx = Math.max(worstPx, Math.abs(handPx - worldPx));
            }
         }
      }
      System.out.printf("fov mapping: worst pixel error %.2e at 1080p%n", worstPx);
      if (worstPx > 0.01) {
         fails++;
      }

      float steady = BowBallistics.spread(1.0F, 0.0F, 0.0, false);
      System.out.printf("spread: full steady draw %.3f, half draw %.3f, tired (strain 1) %.3f, walking %.3f%n",
         steady, BowBallistics.spread(0.5F, 0, 0, false), BowBallistics.spread(1, 1, 0, false), BowBallistics.spread(1, 0, 0.1, false));
      if (steady != 0.0F) {
         fails++;
      }
      System.out.println(fails == 0 ? "PASS" : "FAIL (" + fails + ")");
      if (fails != 0) {
         System.exit(1);
      }
   }
}
