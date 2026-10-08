package com.formaworks.frontierhunts.season.client;

import java.util.ArrayList;
import java.util.List;

/**
 * [1.1.2] The shape of the Frontier snowpack, free of Minecraft's renderer so it can be built and checked offline
 * (tools/snow). {@link SmoothSnowModel} turns the quads into baked block-model quads.
 *
 * <p>The snow is one continuous blanket. Its height is known at every block corner, from the four columns meeting
 * there, and each snow block's top is a bicubic Catmull-Rom patch through the 4 x 4 corners around it, so the surface
 * is smooth (C1) across block borders. On top of that:
 * <ul>
 * <li><b>Steps are buried.</b> Every corner height is the mean of the true snow surfaces around it, the same value for
 *     every block sharing that corner, and a block's blanket may rise into the open cell above it. So over a one-block
 *     step the snow runs on as one slope; in thin snow the top of the step face shows, as on a real snowy hillside.</li>
 * <li><b>Banks.</b> Snow piles up against walls, rocks and trunks, higher the deeper it lies.</li>
 * <li><b>Wind.</b> Shallow sastrugi ripples run across open snow.</li>
 * <li><b>Prints.</b> The dents of {@link SnowPrints} are pressed in.</li>
 * <li><b>Light.</b> Without a shader pack the relief is lit into the vertex colours (a soft sun from the south-east and
 *     sky light), and hollows take a faint cold blue, as real snow does in its own shade. With a shader pack only the
 *     faint tint is kept and the true normals do the rest.</li>
 * </ul>
 */
public final class SnowField {
   private SnowField() {
   }

   /** what the field needs to know about the world */
   public interface Probe {
      /** snow layers (1..8) at the block, 0 if it is not snow */
      int snow(int x, int y, int z);

      /** air or a passable plant (nothing to stand on) */
      boolean open(int x, int y, int z);

      /** a full solid cube */
      boolean full(int x, int y, int z);
   }

   /** the prints pressed into the snow, in world coordinates */
   public interface Dents {
      /** any prints in the 3 x 3 blocks around (x, z), near row y (the block then gets the fine grid) */
      boolean near(int x, int y, int z);

      /** how deep the snow is pressed down at world (wx, wz), near row y */
      float at(int y, double wx, double wz);
   }

   /** one quad, vertices counter-clockwise seen from outside: positions (block-local), uv (0..1 of a 4 x 4 block tile), normals, colours */
   public static final class Quad {
      public final float[] x = new float[4], y = new float[4], z = new float[4], u = new float[4], v = new float[4];
      public final float[] nx = new float[4], ny = new float[4], nz = new float[4];
      public final int[] rgb = new int[4];
      /** 0 up, 2 north, 3 south, 4 west, 5 east (Direction ordinals) */
      public int face;
   }

   // ------------------------------------------------------------------ [perf3] quad pool
   /**
    * [perf3] Snow quads live from {@link #build} (or CrownSnow.build) to SmoothSnowModel's bake a moment later, on the
    * same chunk-mesh worker, for every snow block of every rebuilt section: ten arrays each (~320 bytes), about ten per
    * block. They now come from a per-thread pool and go back once baked ({@link #release}); a pooled quad is reset to
    * exactly the state of a new one first, so the snow is unchanged.
    */
   private static final class Pool {
      final Quad[] items = new Quad[1024];
      int size;
   }

   private static final ThreadLocal<Pool> POOL = ThreadLocal.withInitial(Pool::new);

   /** A quad in the state {@code new Quad()} has (from this thread's pool when it has one). */
   public static Quad quad() {
      Pool pool = POOL.get();
      if (pool.size == 0) return new Quad();
      Quad q = pool.items[--pool.size];
      pool.items[pool.size] = null;
      java.util.Arrays.fill(q.x, 0.0F);
      java.util.Arrays.fill(q.y, 0.0F);
      java.util.Arrays.fill(q.z, 0.0F);
      java.util.Arrays.fill(q.u, 0.0F);
      java.util.Arrays.fill(q.v, 0.0F);
      java.util.Arrays.fill(q.nx, 0.0F);
      java.util.Arrays.fill(q.ny, 0.0F);
      java.util.Arrays.fill(q.nz, 0.0F);
      java.util.Arrays.fill(q.rgb, 0);
      q.face = 0;
      return q;
   }

   /** Hands baked quads back to this thread's pool (nothing may use them afterwards). */
   public static void release(List<Quad> quads) {
      Pool pool = POOL.get();
      for (int i = 0, n = quads.size(); i < n && pool.size < pool.items.length; i++) pool.items[pool.size++] = quads.get(i);
   }

   static final int NONE = 0, SNOW = 1, GROUND = 2, WALL = 4;

   /** how far below the highest snow at a corner the blanket may lie there */
   static final float FILL = 0.28F;

   /** top grid resolution: plain and trodden */
   public static final int PLAIN = 3;

   // ------------------------------------------------------------------------------------------- columns

   /** snow surface above the snow block at (x, y, z) (l layers), following a stack of full snow blocks upward */
   static float stack(Probe p, int x, int y, int z, int l) {
      float h = l / 8.0F;
      for (int up = 1; l == 8 && up <= 6; up++) {
         l = p.snow(x, y + up, z);
         if (l <= 0) {
            break;
         }
         h = up + l / 8.0F;
      }
      return h;
   }

   /** how far up and down a column is followed to find its surface */
   static final int REACH = 4;

   /**
    * The surface of column (x, z) near row y, relative to the base of row y: the top of the snow, or of the ground or
    * solid run the row is in or above. It is the same absolute height whichever row asks (the run is followed up and
    * down), so every block sharing a corner agrees on it. Canopies overhead are ignored; a solid run taller than the
    * reach is a wall.
    */
   static void column(Probe p, int x, int y, int z, float[] s, int[] k, float[] d, int i) {
      int l = p.snow(x, y, z);
      if (l > 0) {
         float h = stack(p, x, y, z, l);
         s[i] = h;
         k[i] = SNOW;
         d[i] = h;
         return;
      }
      if (p.open(x, y, z)) {
         // down to the ground or the snow on it
         for (int dy = 1; dy <= REACH; dy++) {
            int b = p.snow(x, y - dy, z);
            if (b > 0) {
               s[i] = b / 8.0F - dy;
               k[i] = SNOW;
               d[i] = b / 8.0F;
               return;
            }
            if (p.full(x, y - dy, z)) {
               s[i] = 1.0F - dy;
               k[i] = GROUND;
               d[i] = 0.0F;
               return;
            }
            if (!p.open(x, y - dy, z)) {
               k[i] = NONE;
               return;
            }
         }
         s[i] = -REACH;
         k[i] = GROUND;
         d[i] = 0.0F;
         return;
      }
      if (p.full(x, y, z)) {
         // up the solid run to its top, and the snow lying on it
         for (int dy = 1; dy <= REACH; dy++) {
            int a = p.snow(x, y + dy, z);
            if (a > 0) {
               float h = stack(p, x, y + dy, z, a);
               s[i] = dy + h;
               k[i] = SNOW;
               d[i] = h;
               return;
            }
            if (p.open(x, y + dy, z)) {
               s[i] = dy;
               k[i] = GROUND;
               d[i] = 0.0F;
               return;
            }
            if (!p.full(x, y + dy, z)) {
               s[i] = dy;
               k[i] = WALL;
               d[i] = 0.0F;
               return;
            }
         }
         s[i] = REACH + 1;
         k[i] = WALL;
         d[i] = 0.0F;
         return;
      }
      k[i] = NONE;
   }

   /** height of the corner between columns (a, b) .. (a+1, b+1) of the 7 x 7 window */
   static float corner(float[] s, int[] k, float[] d, int a, int b, float self, float[] lowest, int li) {
      float lo = 99.0F;
      for (int i = a; i <= a + 1; i++) {
         for (int j = b; j <= b + 1; j++) {
            int c = i * 7 + j;
            if (k[c] == SNOW || k[c] == GROUND) {
               lo = Math.min(lo, s[c]);
            }
         }
      }
      if (lo > 98.0F) {
         lowest[li] = -99.0F;
         return self;
      }
      // anything standing more than a block and a half above the lowest ground here is a wall (decided from absolute
      // heights only, so every block at this corner decides the same)
      float sum = 0.0F, depth = 0.0F, max = -99.0F;
      int n = 0, nd = 0, walls = 0;
      for (int i = a; i <= a + 1; i++) {
         for (int j = b; j <= b + 1; j++) {
            int c = i * 7 + j;
            if (k[c] == NONE) {
               continue;
            }
            if (k[c] == WALL || s[c] > lo + 1.6F) {
               walls++;
               continue;
            }
            sum += s[c];
            n++;
            max = Math.max(max, s[c]);
            if (k[c] == SNOW) {
               depth += d[c];
               nd++;
            }
         }
      }
      float mean = sum / n;
      if (walls > 0) {
         // snow banks against a wall, rock or trunk: higher the deeper it lies, never up the whole face
         float dep = nd > 0 ? depth / nd : 0.0F;
         float bank = mean + 0.22F + dep * 0.9F;
         sum += bank * walls;
         n += walls;
         mean = sum / n;
      }
      // the mean of the true (absolute) surfaces: the same value seen from every block that shares the corner, so the
      // blanket is continuous over steps; and snow fills in below anything higher close by, as wind lays it into every
      // lee: steps show only as a soft shoulder, edges end in a rounded bank or a cornice instead of thinning to nothing
      lowest[li] = max - FILL;
      return Math.max(mean, max - FILL);
   }

   /** Catmull-Rom on one axis */
   static float cr(float p0, float p1, float p2, float p3, float t) {
      float t2 = t * t, t3 = t2 * t;
      return 0.5F * (2.0F * p1 + (-p0 + p2) * t + (2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3) * t2 + (-p0 + 3.0F * p1 - 3.0F * p2 + p3) * t3);
   }

   static float patch(float[][] h, float u, float v) {
      float r0 = cr(h[0][0], h[0][1], h[0][2], h[0][3], v);
      float r1 = cr(h[1][0], h[1][1], h[1][2], h[1][3], v);
      float r2 = cr(h[2][0], h[2][1], h[2][2], h[2][3], v);
      float r3 = cr(h[3][0], h[3][1], h[3][2], h[3][3], v);
      return cr(r0, r1, r2, r3, u);
   }

   /** wind ripples (sastrugi), fixed to the world; long soft swells with a finer ribbing across them */
   static float ripple(double wx, double wz) {
      double a = Math.sin(wx * 1.3 + wz * 0.45) * 0.55 + Math.sin(wx * 0.6 - wz * 1.7 + 1.7) * 0.45;
      double b = Math.sin(wx * 4.1 + wz * 2.9 + Math.sin(wz * 0.7) * 1.3) * 0.22;
      return (float)((a + b) * 0.014);
   }

   static float clamp(float v, float lo, float hi) {
      return v < lo ? lo : v > hi ? hi : v;
   }

   // ------------------------------------------------------------------------------------------- build

   /** the raw blanket (relative to row y) on the (n+3)^2 grid of a block: its n+1 vertices plus one ring outside */
   static float[][] blanket(Probe p, int x, int y, int z, int n, Dents dents, float[] s, int[] k, float[] d) {
      for (int i = 0; i < 7; i++) {
         for (int j = 0; j < 7; j++) {
            column(p, x + i - 3, y, z + j - 3, s, k, d, i * 7 + j);
         }
      }
      float self = k[24] == SNOW ? s[24] : 0.0F;
      float[][] c6 = new float[6][6];
      float[] lowest = new float[36];
      for (int a = 0; a < 6; a++) {
         for (int b = 0; b < 6; b++) {
            c6[a][b] = corner(s, k, d, a, b, self, lowest, a * 6 + b);
         }
      }
      // one soft pass of smoothing over the corners: drifts round off instead of meeting in ridges
      float[][] h = new float[4][4];
      for (int a = 0; a < 4; a++) {
         for (int b = 0; b < 4; b++) {
            float acc = 0.0F;
            for (int i = -1; i <= 1; i++) {
               for (int j = -1; j <= 1; j++) {
                  acc += c6[a + 1 + i][b + 1 + j] * (i == 0 ? 2 : 1) * (j == 0 ? 2 : 1);
               }
            }
            // (but never below the lee fill, or the corners of buried steps would poke out again)
            h[a][b] = Math.max(acc / 16.0F, lowest[(a + 1) * 6 + b + 1]);
         }
      }
      float[][] f = new float[n + 3][n + 3];
      for (int i = -1; i <= n + 1; i++) {
         for (int j = -1; j <= n + 1; j++) {
            float u = (float)i / n, v = (float)j / n;
            float hgt = patch(h, u, v) + ripple(x + u, z + v);
            if (dents != null) {
               // sampled in world space, so prints run on across blocks and the light on their walls is seamless
               hgt -= dents.at(y, x + u, z + v);
            }
            f[i + 1][j + 1] = hgt;
         }
      }
      return f;
   }

   /**
    * The snow quads of the snow block at (x, y, z) with {@code layers} layers. {@code dents} are the prints of
    * {@link SnowPrints} (or null); {@code lit} bakes the light into the colours (no shader pack).
    */
   public static List<Quad> build(Probe p, int x, int y, int z, int layers, Dents dents, boolean lit) {
      float[] s = new float[49], d = new float[49];
      int[] k = new int[49];
      boolean covered = layers == 8 && p.snow(x, y + 1, z) > 0;
      if (dents != null && (covered || !dents.near(x, y, z))) {
         dents = null;
      }
      int n = dents != null ? SnowPrints.N - 1 : PLAIN;
      float[][] f;
      float floor, ceiling;
      if (covered) {
         // a full block of snow under more snow: its top is inside the pile; its sides follow the blanket of the top
         int up = 1;
         while (up < 6 && p.snow(x, y + up, z) == 8 && p.snow(x, y + up + 1, z) > 0) {
            up++;
         }
         f = blanket(p, x, y + up, z, n, null, new float[49], new int[49], new float[49]);
         for (float[] row : f) {
            for (int j = 0; j < row.length; j++) {
               row[j] += up;
            }
         }
         // the neighbours as seen from this row (for the skirts)
         for (int i = 0; i < 7; i++) {
            for (int j = 0; j < 7; j++) {
               column(p, x + i - 3, y, z + j - 3, s, k, d, i * 7 + j);
            }
         }
         floor = 0.0F;
         ceiling = 1.0F;
      } else {
         f = blanket(p, x, y, z, n, dents, s, k, d);
         // the blanket may rise into the open cell above (over a buried step it runs on up the slope), and sink into a
         // full snow block below (a drift flows down over its own pile); never into anything else
         ceiling = p.open(x, y + 1, z) ? 1.9F : 1.0F;
         floor = p.snow(x, y - 1, z) == 8 ? -0.996F : 0.004F;
      }
      for (float[] row : f) {
         for (int j = 0; j < row.length; j++) {
            row[j] = clamp(row[j], floor, ceiling);
         }
      }
      List<Quad> out = new ArrayList<>(n * n + 4 * n);
      int tx = Math.floorMod(x, 4), tz = Math.floorMod(z, 4), ty = Math.floorMod(y, 4);
      float[][] top = new float[n + 1][n + 1];
      for (int i = 0; i <= n; i++) {
         for (int j = 0; j <= n; j++) {
            top[i][j] = f[i + 1][j + 1];
         }
      }
      if (!covered) {
         float step = 1.0F / n;
         for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
               Quad q = quad(); // [perf3] pooled
               q.face = 0;
               // counter-clockwise from above: (i, j), (i, j+1), (i+1, j+1), (i+1, j)
               for (int c = 0; c < 4; c++) {
                  int a = c >= 2 ? i + 1 : i, b = c == 1 || c == 2 ? j + 1 : j; // [perf3] (was an int[4][2] per quad)
                  q.x[c] = a * step;
                  q.z[c] = b * step;
                  q.y[c] = top[a][b];
                  q.u[c] = (tx + a * step) / 4.0F;
                  q.v[c] = (tz + b * step) / 4.0F;
                  // the normal from the blanket as drawn (smooth across blocks where nothing clamps it)
                  float dx = (f[a + 2][b + 1] - f[a][b + 1]) / (2.0F * step);
                  float dz = (f[a + 1][b + 2] - f[a + 1][b]) / (2.0F * step);
                  float len = (float)Math.sqrt(dx * dx + 1.0F + dz * dz);
                  q.nx[c] = -dx / len;
                  q.ny[c] = 1.0F / len;
                  q.nz[c] = -dz / len;
                  float hollow = (f[a + 2][b + 1] + f[a][b + 1] + f[a + 1][b + 2] + f[a + 1][b] - 4.0F * f[a + 1][b + 1]) / (step * step);
                  q.rgb[c] = shade(q.nx[c], q.ny[c], q.nz[c], hollow, lit);
               }
               out.add(q);
            }
         }
      }
      // skirts: the cut face of the blanket where the neighbour does not carry it on
      edge(out, k[23], s[23], 2, top, n, tx, ty, tz, lit, covered); // north (z-1)
      edge(out, k[25], s[25], 3, top, n, tx, ty, tz, lit, covered); // south (z+1)
      edge(out, k[17], s[17], 4, top, n, tx, ty, tz, lit, covered); // west (x-1)
      edge(out, k[31], s[31], 5, top, n, tx, ty, tz, lit, covered); // east (x+1)
      return out;
   }

   /** the baked light of a vertex: soft sun and sky, and a cold blue in the hollows */
   static int shade(float nx, float ny, float nz, float hollow, boolean lit) {
      float b = 1.0F;
      if (lit) {
         // sun from the south-east, fairly high; sky light from above
         float lx = 0.32F, ly = 0.86F, lz = 0.40F;
         float sun = Math.max(0.0F, nx * lx + ny * ly + nz * lz);
         b = 0.70F + 0.22F * sun + 0.08F * ny;
      }
      float cold = clamp(hollow * 0.5F, 0.0F, 1.0F) * (lit ? 0.07F : 0.04F);
      float r = b * (1.0F - cold * 1.6F), g = b * (1.0F - cold * 0.9F), bl = b * (1.0F - cold * 0.1F);
      return (int)(clamp(r, 0, 1) * 255.0F) << 16 | (int)(clamp(g, 0, 1) * 255.0F) << 8 | (int)(clamp(bl, 0, 1) * 255.0F);
   }

   private static void edge(List<Quad> out, int kind, float ns, int face, float[][] top, int n, int tx, int ty, int tz, boolean lit, boolean covered) {
      if (kind == WALL || kind != NONE && ns >= 1.0F) {
         return; // a solid block (or a full block of snow) hides it
      }
      if (kind == SNOW && (ns >= -1.0F || covered)) {
         return; // the neighbour's own blanket carries on from the shared edge
      }
      float[] e = new float[n + 1];
      float max = 0.0F;
      for (int c = 0; c <= n; c++) {
         e[c] = Math.max(0.0F, switch (face) {
            case 2 -> top[c][0];
            case 3 -> top[c][n];
            case 4 -> top[0][c];
            default -> top[n][c];
         });
         max = Math.max(max, e[c]);
      }
      if (max < 0.01F) {
         return;
      }
      float nx = face == 4 ? -1 : face == 5 ? 1 : 0, nz = face == 2 ? -1 : face == 3 ? 1 : 0;
      int rgb = shade(nx, 0.0F, nz, 0.0F, lit);
      for (int c = 0; c < n; c++) {
         float t0 = (float)c / n, t1 = (float)(c + 1) / n, h0 = e[c], h1 = e[c + 1];
         Quad q = quad(); // [perf3] pooled
         q.face = face;
         // corners: (t, h) pairs, wound to face outward
         float[][] pts = switch (face) {
            case 2 -> new float[][]{{t1, h1, 0}, {t1, 0, 0}, {t0, 0, 0}, {t0, h0, 0}};
            case 3 -> new float[][]{{t0, h0, 1}, {t0, 0, 1}, {t1, 0, 1}, {t1, h1, 1}};
            case 4 -> new float[][]{{0, h0, t0}, {0, 0, t0}, {0, 0, t1}, {0, h1, t1}};
            default -> new float[][]{{1, h1, t1}, {1, 0, t1}, {1, 0, t0}, {1, h0, t0}};
         };
         for (int v = 0; v < 4; v++) {
            q.x[v] = pts[v][0];
            q.y[v] = pts[v][1];
            q.z[v] = pts[v][2];
            float along = face == 2 || face == 3 ? (tx + pts[v][0]) : (tz + pts[v][2]);
            q.u[v] = along / 4.0F;
            q.v[v] = (ty + 1.0F - pts[v][1]) / 4.0F;
            q.nx[v] = nx;
            q.ny[v] = 0.0F;
            q.nz[v] = nz;
            q.rgb[v] = rgb;
         }
         out.add(q);
      }
   }
}
