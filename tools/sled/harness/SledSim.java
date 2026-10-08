import com.formaworks.frontierhunts.sled.SledPhysics;
import java.util.Random;

/** [1.2.2] Offline test of the toboggan's real physics (SledPhysics) on made-up snowy hills. */
public class SledSim {
   static final int N = 1200;

   /** a world of block columns: ground height (whole blocks), snow layers on top, and trunks */
   static final class World implements SledPhysics.Terrain {
      final int[][] base = new int[N][N];
      final int[][] snow = new int[N][N];
      final boolean[][] trunk = new boolean[N][N];
      double grip = 0.02;

      double ground(int x, int z) {
         if (x < 0 || z < 0 || x >= N || z >= N) return Double.NaN;
         return this.base[x][z] + this.snow[x][z] / 8.0;
      }

      public double top(int x, int z, double fromY) {
         double g = this.ground(x, z);
         if (Double.isNaN(g)) return g;
         if (g <= fromY + 1e-9) return fromY - g > 14 ? fromY - 64 : g;
         // the column stands above the reach: the block at the reach height is the top found
         double b = Math.floor(fromY) + 1.0;
         return Math.min(g, b);
      }

      public boolean blocked(double x0, double y0, double z0, double x1, double y1, double z1) {
         for (int x = (int)Math.floor(x0); x <= (int)Math.floor(x1 - 1e-9); x++) {
            for (int z = (int)Math.floor(z0); z <= (int)Math.floor(z1 - 1e-9); z++) {
               if (x < 0 || z < 0 || x >= N || z >= N) continue;
               if (this.trunk[x][z] && y0 < this.base[x][z] + 12) return true;
               // solid blocks (not the snow on them) reaching into the box
               if (this.base[x][z] > y0 + 1e-6) return true;
            }
         }
         return false;
      }

      public double grip(int x, int z, double y) {
         return this.grip;
      }
   }

   /** a hill falling along +z: one block down every {@code run} blocks, snow 1..8 layers, plus some roughness */
   static World hill(double run, double rough, long seed, double treeDensity) {
      World w = new World();
      Random r = new Random(seed);
      double[][] n = new double[N][N];
      for (int x = 0; x < N; x++) for (int z = 0; z < N; z++) n[x][z] = 0.0;
      // smooth bumps
      for (int i = 0; i < 400 && rough > 0; i++) {
         int cx = r.nextInt(N), cz = r.nextInt(N);
         double a = (r.nextDouble() * 2 - 1) * rough, rad = 3 + r.nextDouble() * 8;
         for (int x = Math.max(0, (int)(cx - rad)); x < Math.min(N, cx + rad); x++)
            for (int z = Math.max(0, (int)(cz - rad)); z < Math.min(N, cz + rad); z++) {
               double d = Math.hypot(x - cx, z - cz) / rad;
               if (d < 1) n[x][z] += a * (1 - d * d);
            }
      }
      for (int x = 0; x < N; x++) {
         for (int z = 0; z < N; z++) {
            double h = 1000 - z / run + n[x][z];
            double layers = (h - Math.floor(h)) * 8; // the snow evens out the steps a little
            w.base[x][z] = (int)Math.floor(h);
            w.snow[x][z] = Math.max(1, Math.min(8, (int)Math.round(layers + r.nextInt(3) - 1)));
            if (r.nextDouble() < treeDensity) w.trunk[x][z] = true;
         }
      }
      return w;
   }

   static World flat() {
      World w = new World();
      for (int x = 0; x < N; x++) for (int z = 0; z < N; z++) { w.base[x][z] = 100; w.snow[x][z] = 2; }
      return w;
   }

   /** flat for 60 blocks, then a 40 degree face */
   static World crest() {
      World w = new World();
      for (int x = 0; x < N; x++) for (int z = 0; z < N; z++) {
         double h = z < 300 ? 600 - z / 4.0 : 600 - 75 - (z - 300) * 0.84;
         w.base[x][z] = (int)Math.floor(h);
         w.snow[x][z] = Math.max(1, (int)Math.round((h - Math.floor(h)) * 8));
      }
      return w;
   }

   /** flat, then a 20-block cliff, then flat again */
   static World cliff() {
      World w = new World();
      for (int x = 0; x < N; x++) for (int z = 0; z < N; z++) { w.base[x][z] = z < 200 ? 300 - z / 3 : 300 - 200 / 3 - 20 - (z - 200) / 3; w.snow[x][z] = 2; }
      return w;
   }

   static String kmh(double bpt) {
      return String.format("%5.0f", bpt * 72.0);
   }

   static SledPhysics sled(World w, double x, double z, float yaw) {
      SledPhysics s = new SledPhysics();
      s.x = x; s.z = z; s.yaw = yaw;
      s.y = w.ground((int)x, (int)z);
      return s;
   }

   /** ride straight down a hill; print the speed over time, hits, airtime, how rough the ride is */
   static void ride(String name, World w, float yaw, float steerEvery, double startX) {
      SledPhysics s = sled(w, startX, 20.5, yaw);
      StringBuilder sb = new StringBuilder();
      int hits = 0, air = 0;
      double maxSp = 0, jerk = 0, lastVy = 0;
      double dist = 0;
      for (int t = 1; t <= 400; t++) {
         float side = 0;
         if (steerEvery > 0) side = (float)Math.sin(t / steerEvery) > 0 ? 0.6F : -0.6F;
         double px = s.x, pz = s.z;
         s.tick(w, t < 10 ? 1.0F : 0.0F, side, true);
         dist += Math.hypot(s.x - px, s.z - pz);
         if (s.impact > 0.05) hits++;
         if (!s.ground) air++;
         if (s.ground) { jerk += Math.abs(s.vy - lastVy); lastVy = s.vy; }
         maxSp = Math.max(maxSp, s.speed());
         if (t == 20 || t == 40 || t == 60 || t == 100 || t == 160 || t == 240 || t == 400) sb.append(" t").append(t / 20).append("s=").append(kmh(s.speed()));
         if (s.z > N - 20 || s.x < 5 || s.x > N - 5) break;
      }
      System.out.printf("%-34s%s | max %s km/h, %4.0f blocks, hits %d, air %d ticks, rattle %.3f%n", name, sb, kmh(maxSp), dist, hits, air, jerk / 400);
   }

   public static void main(String[] a) {
      System.out.println("== straight down the fall line (km/h at 1,2,3,5,8,12,20 s)");
      ride("45 deg stairs, snow", hill(1, 0, 1, 0), 0, 0, 600);
      ride("34 deg (2 down per 3)", hill(1.5, 0, 2, 0), 0, 0, 600);
      ride("27 deg (1 down per 2)", hill(2, 0, 3, 0), 0, 0, 600);
      ride("18 deg (1 down per 3)", hill(3, 0, 4, 0), 0, 0, 600);
      ride("9 deg (1 down per 6)", hill(6, 0, 5, 0), 0, 0, 600);
      ride("5 deg (1 down per 12)", hill(12, 0, 6, 0), 0, 0, 600);
      ride("50 deg jagged face (bumps +-2)", hill(0.83, 2.0, 15, 0), 0, 0, 600);
      ride("60 deg face", hill(0.58, 0.5, 16, 0), 0, 0, 600);
      ride("rough 34 deg (bumps +-2)", hill(1.5, 2.0, 7, 0), 0, 0, 600);
      ride("rough 27 deg (bumps +-3)", hill(2, 3.0, 8, 0), 0, 0, 600);
      ride("27 deg, weaving left/right", hill(2, 1.0, 9, 0), 0, 12, 600);
      ride("27 deg, 40 deg off the fall line", hill(2, 0, 10, 0), -40, 0, 600);
      ride("27 deg, across the slope (85 off)", hill(2, 0, 11, 0), -85, 0, 600);
      ride("27 deg forest (1 trunk / 60 cols)", hill(2, 1.0, 12, 1.0 / 60), 0, 0, 600);
      ride("crest: gentle run onto a 40 deg face", crest(), 0, 0, 600);
      ride("18 deg run over a 20-block cliff", cliff(), 0, 0, 600);
      // unridden, dropped from the sky
      World c = flat();
      SledPhysics u = sled(c, 600, 600, 0);
      u.y = 250;
      int tt = 0;
      while (tt < 400 && (!u.ground || u.y > 120)) { u.tick(c, 0, 0, false); tt++; }
      System.out.printf("dropped unridden from 250: lands at y %.2f (snow %.2f) after %d ticks%n", u.y, c.ground(600, 600), tt);
      // turning: at speed on a 27 degree slope, hold left for 2 s
      World w = hill(2, 0, 13, 0);
      SledPhysics s = sled(w, 600, 20.5, 0);
      for (int t = 0; t < 120; t++) s.tick(w, 0, 0, true);
      double v0 = s.speed(); float y0 = s.yaw;
      for (int t = 0; t < 40; t++) s.tick(w, 0, 1.0F, true);
      System.out.printf("hard turn 2 s at %s km/h: turned %.0f deg, speed now %s km/h%n", kmh(v0), y0 - s.yaw, kmh(s.speed()));
      // braking
      s = sled(w, 600, 20.5, 0);
      for (int t = 0; t < 120; t++) s.tick(w, 0, 0, true);
      v0 = s.speed();
      int t = 0;
      while (s.speed() > 0.05 && t < 400) { s.tick(w, -1.0F, 0, true); t++; }
      System.out.printf("brake on 27 deg from %s km/h: %.1f s to a crawl (%s km/h)%n", kmh(v0), t / 20.0, kmh(s.speed()));
      // flat: push off and coast
      World f = flat();
      s = sled(f, 600, 600, 0);
      double d = 0;
      for (t = 0; t < 600; t++) { double pz = s.z; s.tick(f, t < 40 ? 1.0F : 0.0F, 0, true); d += s.z - pz; }
      System.out.printf("flat: push 2 s then coast: %.0f blocks, %s km/h at the end%n", d, kmh(s.speed()));
      // unridden: stays put on a slope
      s = sled(w, 600, 300.5, 0);
      double z0 = s.z;
      for (t = 0; t < 200; t++) s.tick(w, 0, 0, false);
      System.out.printf("unridden on 27 deg for 10 s: moved %.2f blocks, on the snow %b (y - snow %.2f)%n", Math.abs(s.z - z0), s.ground, s.y - s.surface(s.x, s.z));
      // earth: stops fast
      World e = hill(3, 0, 14, 0);
      e.grip = 1.0;
      ride("18 deg on bare earth", e, 0, 0, 600);
   }
}
