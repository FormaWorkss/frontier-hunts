import com.formaworks.frontierhunts.season.client.SnowField;
import java.io.PrintWriter;
import java.util.List;
import java.util.Random;

/**
 * [1.1.2] Offline bench for the Frontier snowpack: builds test scenes (a terraced hillside with boulders, a trunk, a
 * cabin wall, a 2-block drop, thin and deep snow, a ploughed trench) and writes the snow mesh exactly as the game builds
 * it (SnowField) plus the solid blocks, for tools/snow/render.py.
 * usage: java SnowBench <out dir> [lit]
 */
public class SnowBench {
   static final int W = 40, H = 24;
   static int[][][] world = new int[W][H][W]; // 0 air, 1 solid, 2..9 snow layers 1..8, 10 grass block, 11 log, 12 planks

   static int snow(int x, int y, int z) {
      if (x < 0 || z < 0 || x >= W || z >= W || y < 0 || y >= H) return 0;
      int b = world[x][y][z];
      return b >= 2 && b <= 9 ? b - 1 : 0;
   }

   static int at(int x, int y, int z) {
      if (x < 0 || z < 0 || x >= W || z >= W || y < 0) return 1;
      if (y >= H) return 0;
      return world[x][y][z];
   }

   /** a ploughed trench along z = 26.5 and a line of boot prints along z = 33.5 (as SnowPrints presses them) */
   static float[] dents(int bx, int bz, int layers) {
      int N = com.formaworks.frontierhunts.season.client.SnowPrints.N;
      float[] d = new float[N * N];
      boolean any = false;
      float cap = 1.6F;
      for (int i = 0; i < N; i++) for (int j = 0; j < N; j++) {
         double wx = bx + (double)i / (N - 1), wz = bz + (double)j / (N - 1);
         double v = 0;
         if (wx > 4 && wx < 34) {
            double t = Math.abs(wz - 26.5) / 0.62;
            if (t < 1) v = Math.max(v, 0.55 * (t < 0.45 ? 1 : 1 - smooth((t - 0.45) / 0.55)));
            double step = 0.62, k = Math.floor(wx / step);
            for (int q = -1; q <= 1; q++) {
               double cx = (k + q) * step + step / 2, cz = 33.5 + (((int)(k + q) & 1) == 0 ? 0.14 : -0.14);
               double r = Math.pow((wx - cx) / 0.17, 2) + Math.pow((wz - cz) / 0.085, 2);
               if (r < 1) v = Math.max(v, 0.2 * (r < 0.45 ? 1 : 1 - smooth((r - 0.45) / 0.55)));
            }
         }
         d[i * N + j] = (float)Math.min(cap, v);
         any |= v > 0;
      }
      return any ? d : null;
   }

   static double smooth(double t) { return t * t * (3 - 2 * t); }

   static final SnowField.Dents DENTS = new SnowField.Dents() {
      public boolean near(int x, int y, int z) { return x > 2 && x < 36 && (Math.abs(z - 26) <= 2 || Math.abs(z - 33) <= 2); }
      public float at(int y, double wx, double wz) {
         double v = 0;
         if (wx > 4 && wx < 34) {
            double t = Math.abs(wz - 26.5) / 0.62;
            if (t < 1) v = Math.max(v, 0.55 * (t < 0.45 ? 1 : 1 - smooth((t - 0.45) / 0.55)));
            double step = 0.62, k = Math.floor(wx / step);
            for (int q = -1; q <= 1; q++) {
               double cx = (k + q) * step + step / 2, cz = 33.5 + (((int)(k + q) & 1) == 0 ? 0.14 : -0.14);
               double r = Math.pow((wx - cx) / 0.17, 2) + Math.pow((wz - cz) / 0.085, 2);
               if (r < 1) v = Math.max(v, 0.2 * (r < 0.45 ? 1 : 1 - smooth((r - 0.45) / 0.55)));
            }
         }
         return (float)v;
      }
   };

   public static void main(String[] a) throws Exception {
      String out = a[0];
      boolean lit = a.length < 2 || !a[1].equals("shaders");
      Random r = new Random(7);
      // terraced hillside: height from smooth waves, integer steps
      int[][] ground = new int[W][W];
      for (int x = 0; x < W; x++) {
         for (int z = 0; z < W; z++) {
            double h = 6 + 3.2 * Math.sin(x * 0.16) + 2.4 * Math.cos(z * 0.13 + 0.8) + 1.5 * Math.sin((x + z) * 0.31);
            if (x > 26 && z < 12) h -= 2.5; // a drop
            ground[x][z] = (int)Math.round(h);
            for (int y = 0; y <= ground[x][z]; y++) world[x][y][z] = y == ground[x][z] ? 10 : 1;
         }
      }
      // boulders, a trunk, a cabin wall
      int[][] boulders = {{9, 9}, {10, 9}, {20, 25}, {31, 30}, {14, 31}};
      for (int[] b : boulders) world[b[0]][ground[b[0]][b[1]] + 1][b[1]] = 1;
      for (int y = 1; y <= 9; y++) world[18][ground[18][14] + y][14] = 11;
      for (int x = 24; x <= 32; x++) for (int y = 1; y <= 3; y++) world[x][ground[x][20] + y][20] = 12;
      // snow: depth grows from the west (thin, 1-2 layers) to the east (deep, up to 12 layers stacked)
      for (int x = 0; x < W; x++) {
         for (int z = 0; z < W; z++) {
            int top = H - 1;
            while (top > 0 && world[x][top][z] == 0) top--;
            if (world[x][top][z] == 11 || world[x][top][z] == 12) continue;
            int layers = 1 + (int)(x / 3.6) + r.nextInt(2);
            for (int y = top + 1; layers > 0 && y < H; y++) {
               int l = Math.min(8, layers);
               world[x][y][z] = 1 + l;
               layers -= l;
            }
         }
      }
      SnowField.Probe p = new SnowField.Probe() {
         public int snow(int x, int y, int z) { return SnowBench.snow(x, y, z); }
         public boolean open(int x, int y, int z) { return at(x, y, z) == 0; }
         public boolean full(int x, int y, int z) { int b = at(x, y, z); return b == 1 || b >= 10; }
      };
      int quads = 0;
      try (PrintWriter o = new PrintWriter(out + "/snow.txt"); PrintWriter bl = new PrintWriter(out + "/blocks.txt")) {
         for (int x = 0; x < W; x++) for (int y = 0; y < H; y++) for (int z = 0; z < W; z++) {
            int b = world[x][y][z];
            if (b == 1 || b >= 10) bl.println(x + " " + y + " " + z + " " + b);
            int l = snow(x, y, z);
            if (l == 0) continue;
            List<SnowField.Quad> qs = SnowField.build(p, x, y, z, l, DENTS, lit);
            for (SnowField.Quad q : qs) {
               StringBuilder sb = new StringBuilder();
               for (int v = 0; v < 4; v++) {
                  sb.append(x + q.x[v]).append(' ').append(y + q.y[v]).append(' ').append(z + q.z[v]).append(' ')
                    .append(q.u[v]).append(' ').append(q.v[v]).append(' ').append(q.nx[v]).append(' ').append(q.ny[v]).append(' ').append(q.nz[v]).append(' ')
                    .append(q.rgb[v]).append(' ');
               }
               o.println(sb.toString().trim());
               quads++;
            }
         }
      }
      System.out.println("snow quads " + quads);
   }
}
