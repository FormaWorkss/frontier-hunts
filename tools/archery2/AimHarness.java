import com.formaworks.frontierhunts.Wilderness;
import com.formaworks.frontierhunts.archery.ArrowFlight;
import com.formaworks.frontierhunts.archery.BowBallistics;
import com.formaworks.frontierhunts.archery.SightOptics;
import java.util.Random;
import net.minecraft.world.phys.Vec3;

/**
 * [archery2] End-to-end check of the traditional bows' aiming reference (the impact dot), from the player's view to the
 * arrow sticking in the target.
 *
 * <p>For the Field Recurve (FieldArrow) and the Recurve (HuntProjectile), at 10-40 m, targets level / uphill /
 * downhill, any heading, every common world FOV (with the full-draw zoom) and screen size, in calm air and in wind:
 * <ol>
 *   <li>CLIENT: the dot's position is what the game draws - {@link ArrowFlight} (BowAim's solver) flown from the eye
 *       with the player's float rotation into the scene (a 2 x 2 m board facing the archer, the ground), projected
 *       through {@link SightOptics#project} with the world FOV and rounded to the pixel the dot is drawn on;</li>
 *   <li>the player puts that dot on the bullseye: the rotation (float degrees, as the client holds it) is searched
 *       until the drawn dot pixel sits on the bullseye pixel;</li>
 *   <li>SERVER: the arrow released at that rotation is flown by an independent transcription of the server code -
 *       Projectile.shootFromRotation with Minecraft's own 65536-entry sine table, the spawn point (eye / eye - 0.1),
 *       a verbatim copy of {@code FieldArrow.integrate} with {@code Wilderness.wind}, HuntProjectile's tick -
 *       until it crosses the board;</li>
 *   <li>the miss (server impact vs bullseye) must be under 0.1 block. Also checked: the projection against a JOML
 *       perspective+view matrix built the way GameRenderer/Camera build them, and the release-draw rule (the dot only
 *       shows at full draw; the server treats >= 95 % as full).</li>
 * </ol>
 * The pixel the dot is drawn on is a quantisation the player cannot beat; it is included (worst case at the widest
 * FOV / smallest screen).
 */
public final class AimHarness {
   // ---- independent Minecraft sine table (Mth.sin)
   static final float[] SIN = new float[65536];

   static {
      for (int i = 0; i < SIN.length; i++) {
         SIN[i] = (float)Math.sin(i * Math.PI * 2.0 / 65536.0);
      }
   }

   static float sin(float v) {
      return SIN[(int)(v * 10430.378F) & 65535];
   }

   static float cos(float v) {
      return SIN[(int)(v * 10430.378F + 16384.0F) & 65535];
   }

   /** The scene: a board (plane facing the archer) at horizontal distance d along heading yaw, plus flat ground. */
   record Scene(double eyeY, double yawDeg, double d, double ty) {
      double[] fwd() {
         double r = Math.toRadians(this.yawDeg);
         return new double[]{-Math.sin(r), Math.cos(r)};
      }

      /** Fraction along a->b where the segment crosses the board plane or the ground (y = 0), NaN if neither. */
      double hit(double ax, double ay, double az, double bx, double by, double bz) {
         double[] f = this.fwd();
         double da = ax * f[0] + az * f[1] - this.d;
         double db = bx * f[0] + bz * f[1] - this.d;
         double best = Double.NaN;
         if (da < 0 && db >= 0) {
            best = da / (da - db);
         }
         if (ay > 0 && by <= 0) {
            double g = ay / (ay - by);
            if (Double.isNaN(best) || g < best) {
               best = g;
            }
         }
         return best;
      }

      double[] target() {
         double[] f = this.fwd();
         return new double[]{f[0] * this.d, this.ty, f[1] * this.d};
      }
   }

   static final class Wind {
      double e;
      double s;
   }

   /** Client: BowAim's solve (production ArrowFlight) for rotation (pitch, yaw). */
   static ArrowFlight.Impact client(BowBallistics.Profile p, Scene sc, float pitch, float yaw, Wind w) {
      double[] v = ArrowFlight.launch(pitch, yaw, ArrowFlight.speed(p, 1.0F));
      return ArrowFlight.fly(p, 0.0, sc.eyeY - ArrowFlight.spawnDrop(p), 0.0, v, w.e, w.s, sc::hit, 120);
   }

   /** Server transcription: the released arrow at rotation (pitch, yaw), draw as the server computed it. */
   static double[] server(BowBallistics.Profile p, Scene sc, float pitch, float yaw, float draw, Wind w) {
      float rad = (float)(Math.PI / 180.0);
      float fx = -sin(yaw * rad) * cos(pitch * rad);
      float fy = -sin((pitch + 0.0F) * rad);
      float fz = cos(yaw * rad) * cos(pitch * rad);
      boolean fieldArrow = p.drag() == BowBallistics.Drag.QUADRATIC;
      if (draw >= 0.95F) {
         draw = 1.0F; // FieldBow.releaseUsing / ExpeditionWeapon.releaseUsing [archery2]
      }
      float speed = fieldArrow ? 2.75F * (float)Math.sqrt((double)draw) : (float)p.speed() * draw;
      Vec3 v = new Vec3(fx, fy, fz).normalize().scale(speed);
      Vec3 pos = new Vec3(0.0, sc.eyeY - (fieldArrow ? 0.1 : 0.0), 0.0);
      for (int t = 0; t < 400; t++) {
         Vec3 next = pos.add(v);
         double f = sc.hit(pos.x, pos.y, pos.z, next.x, next.y, next.z);
         if (!Double.isNaN(f)) {
            Vec3 at = pos.lerp(next, f);
            return new double[]{at.x, at.y, at.z};
         }
         pos = next;
         if (fieldArrow) {
            v = fieldArrowIntegrate(v, new Wilderness.Wind(w.e, w.s), false);
         } else {
            v = v.scale(0.993).add(w.e * 1.0E-4, -0.035, w.s * 1.0E-4);
         }
      }
      return null;
   }

   /** Rounded pixel the HUD draws the dot on (BowSightHud.impactDot: Math.round of the projected position). */
   static double[] dotPixel(ArrowFlight.Impact imp, Scene sc, float pitch, float yaw, double fov, int w, int h) {
      if (!imp.hit()) {
         return null;
      }
      double[] s = SightOptics.project(imp.x(), imp.y() - sc.eyeY, imp.z(), yaw, pitch, fov, w, h);
      return s == null ? null : new double[]{Math.round(s[0]), Math.round(s[1]), s[0], s[1]};
   }

   public static void main(String[] args) {
      Random rnd = new Random(20261002L);
      BowBallistics.Profile[] bows = {BowBallistics.FIELD_RECURVE, BowBallistics.RECURVE};
      double[] fovs = {50.0, 70.0, 90.0, 110.0};
      int[][] screens = {{1920, 1080}, {2560, 1440}, {1366, 768}};
      double worst = 0.0;
      double worstPx = 0.0;
      int shots = 0;
      int fails = 0;
      String worstCase = "";
      for (BowBallistics.Profile p : bows) {
         for (double d = 10.0; d <= 40.001; d += 2.5) {
            for (int k = 0; k < 24; k++) {
               double yawD = rnd.nextDouble() * 360.0 - 180.0;
               double rise = (rnd.nextDouble() - 0.4) * d * 0.25; // target from 10 % below to 15 % above the eye line
               Scene sc = new Scene(1.62 + rnd.nextDouble() * 3.0, yawD, d, 0.0);
               sc = new Scene(sc.eyeY, yawD, d, Math.max(0.3, sc.eyeY + rise));
               double fov = fovs[k % fovs.length] / 1.17; // the full-draw zoom (FieldBowPresentation.fov)
               int[] scr = screens[k % screens.length];
               Wind wind = new Wind();
               if (k % 3 != 0) {
                  Wilderness.Wind ww = Wilderness.wind(rnd.nextLong(), rnd.nextInt(2000000), k % 3 == 2, false);
                  wind.e = ww.east();
                  wind.s = ww.south();
               }
               double[] t = sc.target();
               // the player swings the view until the drawn dot pixel sits on the bullseye pixel
               double[] aim = aimDot(p, sc, t, fov, scr[0], scr[1], wind);
               if (aim == null) {
                  System.out.printf("FAIL no aim solution %s d=%.1f%n", p.id(), d);
                  fails++;
                  continue;
               }
               float pitch = (float)aim[0];
               float yaw = (float)aim[1];
               double[] s = server(p, sc, pitch, yaw, 1.0F, wind);
               double miss = s == null ? 99.0 : Math.sqrt(sq(s[0] - t[0]) + sq(s[1] - t[1]) + sq(s[2] - t[2]));
               shots++;
               if (miss > worst) {
                  worst = miss;
                  worstCase = String.format("%s d=%.1f fov=%.0f %dx%d wind=%.1f,%.1f", p.id(), d, fov, scr[0], scr[1], wind.e, wind.s);
               }
               worstPx = Math.max(worstPx, aim[2]);
               if (miss >= 0.1) {
                  fails++;
                  System.out.printf("FAIL %s d=%.1f miss=%.3f%n", p.id(), d, miss);
               }
               // the release one network tick early (server draw 23/24 .. 25/26) must still be a full-power shot
               int interval = p == BowBallistics.FIELD_RECURVE ? 26 : 24;
               double[] early = server(p, sc, pitch, yaw, (interval - 1) / (float)interval, wind);
               double earlyMiss = early == null ? 99.0 : Math.sqrt(sq(early[0] - s[0]) + sq(early[1] - s[1]) + sq(early[2] - s[2]));
               if (earlyMiss > 1.0E-9) {
                  fails++;
                  System.out.printf("FAIL early-release draw changes the shot %s d=%.1f by %.3f%n", p.id(), d, earlyMiss);
               }
            }
         }
      }
      // projection cross-check against a GameRenderer-style projection * view matrix
      double projWorst = 0.0;
      int projChecked = 0;
      for (int i = 0; i < 20000; i++) {
         float yaw = rnd.nextFloat() * 360.0F - 180.0F;
         float pitch = rnd.nextFloat() * 120.0F - 60.0F;
         double fov = 40.0 + rnd.nextDouble() * 80.0;
         int w = 1920;
         int h = 1080;
         double rx = rnd.nextGaussian() * 20.0;
         double ry = rnd.nextGaussian() * 5.0;
         double rz = rnd.nextGaussian() * 20.0;
         double[] a = SightOptics.project(rx, ry, rz, yaw, pitch, fov, w, h);
         org.joml.Matrix4f proj = new org.joml.Matrix4f().perspective((float)Math.toRadians(fov), (float)w / h, 0.05F, 1000.0F);
         // Camera: rotation = rotationYXZ(pi - yaw, -pitch, 0); the level is drawn with its conjugate as the view rotation
         org.joml.Quaternionf cam = new org.joml.Quaternionf().rotationYXZ((float)Math.PI - yaw * (float)(Math.PI / 180.0), -pitch * (float)(Math.PI / 180.0), 0.0F);
         org.joml.Matrix4f view = new org.joml.Matrix4f().rotation(cam.conjugate(new org.joml.Quaternionf()));
         org.joml.Vector4f c = proj.mul(view, new org.joml.Matrix4f()).transform(new org.joml.Vector4f((float)rx, (float)ry, (float)rz, 1.0F));
         if (c.w <= 0.05F) {
            if (a != null && c.w < 0.04F) {
               projWorst = Math.max(projWorst, 1.0E9);
            }
            continue;
         }
         double sx = (c.x / c.w * 0.5 + 0.5) * w;
         double sy = (1.0 - (c.y / c.w * 0.5 + 0.5)) * h;
         if (a == null || sx < 0 || sy < 0 || sx > w || sy > h) {
            continue; // on-screen points only (far off-screen, float precision of the clip coordinates dominates)
         }
         projChecked++;
         projWorst = Math.max(projWorst, Math.max(Math.abs(sx - a[0]), Math.abs(sy - a[1])));
      }
      boolean projOk = projWorst < 0.05;
      System.out.printf("traditional bows: %d aimed shots (10-40 m, up/down hill, any heading, FOV 43-94 zoomed, 3 screens, calm/wind/rain)%n", shots);
      System.out.printf("  worst miss %.4f block (limit 0.1) at %s; worst residual dot offset %.2f px%n", worst, worstCase, worstPx);
      System.out.printf("  projection vs GameRenderer matrices: %d on-screen points, worst %.4f px (limit 0.05)%n", projChecked, projWorst);
      if (fails > 0 || !projOk || worst >= 0.1) {
         System.out.println("AIM HARNESS FAILED (" + fails + " failures)");
         System.exit(1);
      }
      System.out.println("AIM HARNESS OK");
   }

   /** hunting.FieldArrow.integrate, verbatim from the mod (the entity class itself cannot load outside the game). */
   static Vec3 fieldArrowIntegrate(Vec3 var0, Wilderness.Wind var1, boolean var2) {
      Vec3 var3 = var0.subtract(var1.east() / 20.0, 0.0, var1.south() / 20.0);
      double var4 = var2 ? 0.28 : Math.min(0.035, var3.length() * 0.0022);
      return var0.subtract(var3.scale(var4)).add(0.0, -0.024525, 0.0);
   }

   static double sq(double v) {
      return v * v;
   }

   /**
    * The player's aim: float pitch/yaw that put the drawn (rounded) dot pixel on the bullseye's pixel. Coarse solve in
    * doubles, then a float-degree search around it scoring the drawn-pixel distance (what the eye compares), ties broken
    * by the sub-pixel distance. Returns {pitch, yaw, residualPx}.
    */
   static double[] aimDot(BowBallistics.Profile p, Scene sc, double[] t, double fov, int w, int h, Wind wind) {
      double pitch = -Math.toDegrees(Math.atan2(t[1] - sc.eyeY, sc.d)) - 3.0;
      double yaw = sc.yawDeg;
      // Newton in the target plane: make the client impact equal the bullseye
      for (int i = 0; i < 40; i++) {
         ArrowFlight.Impact a = client(p, sc, (float)pitch, (float)yaw, wind);
         if (!a.hit()) {
            pitch -= 2.0;
            continue;
         }
         double[] f = sc.fwd();
         double lat = (a.x() - t[0]) * f[1] - (a.z() - t[2]) * f[0];
         double dy = a.y() - t[1];
         if (Math.abs(dy) < 1.0E-5 && Math.abs(lat) < 1.0E-5) {
            break;
         }
         pitch += Math.toDegrees(dy / sc.d) * 0.9;
         yaw += Math.toDegrees(lat / sc.d) * 0.9 * (1.0);
      }
      // the bullseye pixel, seen through the camera at the solved aim
      double[] tp = SightOptics.project(t[0], t[1] - sc.eyeY, t[2], (float)yaw, (float)pitch, fov, w, h);
      if (tp == null) {
         return null;
      }
      double best = Double.MAX_VALUE;
      double[] out = null;
      // a mouse moves the view by tiny float steps; search the float neighbourhood like a player nudging the dot onto the mark
      float p0 = (float)pitch;
      float y0 = (float)yaw;
      double step = 0.004;
      for (int i = -12; i <= 12; i++) {
         for (int j = -12; j <= 12; j++) {
            float pp = p0 + (float)(i * step);
            float yy = y0 + (float)(j * step);
            ArrowFlight.Impact a = client(p, sc, pp, yy, wind);
            double[] dp = dotPixel(a, sc, pp, yy, fov, w, h);
            double[] tq = SightOptics.project(t[0], t[1] - sc.eyeY, t[2], yy, pp, fov, w, h);
            if (dp == null || tq == null) {
               continue;
            }
            double pix = Math.hypot(dp[0] - Math.round(tq[0]), dp[1] - Math.round(tq[1]));
            double sub = Math.hypot(dp[2] - tq[0], dp[3] - tq[1]);
            double score = pix * 1000.0 + sub;
            if (score < best) {
               best = score;
               out = new double[]{pp, yy, pix};
            }
         }
      }
      return out;
   }
}
