package com.formaworks.frontierhunts.client.tree;

import java.util.Arrays;
import java.util.List;

/**
 * [perf] The third, most distant level of detail of a grown tree: a handful of flat cutout cards.
 *
 * <p>While {@link TreeGrowth} grows a tree it hands every foliage card of the crown to {@link #card}
 * (whatever levels it builds), so the impostor is fitted to the tree's real crown, not to its leaf
 * blocks: the crown is measured along three horizontal directions 60 degrees apart and in height, and
 * becomes three crossed vertical planes (conifers: two stacked, narrowing tiers, so the silhouette
 * tapers to a point; broadleaves: one plane each plus a horizontal card across the upper crown for views
 * from above), textured with the species' own crown-fringe sprite and bent normals (tops lit like the top
 * of a crown, undersides darker). Each stem keeps a simple three-sided bark prism. Double-sided cards:
 * the chunk layers cull back faces. About 11 quads for a broadleaf, 15 for a conifer.
 *
 * <p>The quads are ordinary {@link TreeShape.Quad}s (texture 2 foliage, 0 bark), so they are hosted like
 * every other tree quad (foliage by the tree's own leaves, wood by its logs) and drawn in the same solid
 * and cutout-mipped chunk layers: no custom render pass, so Sodium, Iris and any shader pack treat them
 * like the rest of the tree. Pure Java (no Minecraft types) like TreeGrowth.
 */
final class TreeImpostor {
   /** A tier or trunk segment never spans more than this, so some chunk section can always host it. */
   static final float MAX_SPAN = 14.0F;
   /** Horizontal directions the crown is measured along (and planes drawn in). */
   static final int PLANES = 3;

   /** Card samples: x, y, z, half extent. */
   private float[] samples = new float[256];
   private int count;
   private int crownStart;

   void reset() {
      this.count = 0;
      this.crownStart = 0;
   }

   /** Starts the cards of one stem's crown (lean is applied to them in {@link #endCrown}). */
   void beginCrown() {
      this.crownStart = this.count;
   }

   /** One foliage card: centre and full size. */
   void card(float[] c, float width, float height) {
      if (this.count * 4 + 4 > this.samples.length) this.samples = Arrays.copyOf(this.samples, this.samples.length * 2);
      int o = this.count++ * 4;
      this.samples[o] = c[0];
      this.samples[o + 1] = c[1];
      this.samples[o + 2] = c[2];
      // a card may be tilted: its reach from its centre is up to half its larger side
      this.samples[o + 3] = 0.5F * Math.max(width, height);
   }

   /** The same lean TreeGrowth applies to the crown's quads (x, z shift by height above the stem foot). */
   void endCrown(float leanX, float leanZ, float y0) {
      for (int i = this.crownStart; i < this.count; i++) {
         int o = i * 4;
         float h = this.samples[o + 1] - y0;
         this.samples[o] += leanX * h;
         this.samples[o + 2] += leanZ * h;
      }
   }

   int samples() {
      return this.count;
   }

   /** Receives the impostor's quads. */
   interface Out {
      void quad(TreeShape.Quad q);
   }

   /** One trunk: foot centre and radius, and where it disappears into the crown. */
   record Trunk(float x0, float y0, float z0, float r0, float x1, float y1, float z1, float r1) {
   }

   /**
    * Builds the impostor. {@code seed} turns the planes (neighbouring trees never line up), {@code
    * conifer} selects the tapering tiers.
    */
   void build(long seed, boolean conifer, List<Trunk> trunks, Out out) {
      float turn = (float)((seed >>> 11 & 1023) / 1024.0 * Math.PI / PLANES);
      if (this.count > 0) this.crown(turn, conifer, out);
      int n = 0;
      for (Trunk t : trunks) {
         if (n++ == 3) break; // a clump's further stems hide behind the first ones at this range
         this.trunk(t, turn + n * 0.7F, out);
      }
   }

   // ------------------------------------------------------------------ crown

   private void crown(float turn, boolean conifer, Out out) {
      int n = this.count;
      float[] s = this.samples;
      // centre: area-weighted mean of the cards (follows a leaning or one-sided crown)
      double sx = 0, sz = 0, sw = 0;
      // [perf3] reused across growths (TreeGrowth keeps one TreeImpostor per thread); only the first n are read
      if (this.lo.length < n) {
         this.lo = new float[Math.max(n, this.lo.length * 2)];
         this.hi = new float[this.lo.length];
      }
      float[] lo = this.lo, hi = this.hi;
      for (int i = 0; i < n; i++) {
         float w = s[i * 4 + 3] * s[i * 4 + 3];
         sx += s[i * 4] * w;
         sz += s[i * 4 + 2] * w;
         sw += w;
         lo[i] = s[i * 4 + 1] - s[i * 4 + 3];
         hi[i] = s[i * 4 + 1] + s[i * 4 + 3];
      }
      float cx = (float)(sx / sw), cz = (float)(sz / sw);
      // height: ignore the odd stray card below the crown (percentiles), keep the very top
      float bottom = percentile(lo, n, 0.03F), top = percentile(hi, n, 0.995F);
      if (!(top - bottom > 0.5F)) return;
      float height = top - bottom;
      // (a broadleaf's cards reach past the crown for the sprite's margin: count that into the span)
      int tiers = Math.max(conifer ? 2 : 1, (int)Math.ceil(height * (conifer ? 1.0F : 1.3F) / MAX_SPAN));
      // tier boundaries, overlapping a little so no daylight shows between them
      float[] edge = new float[tiers + 1];
      for (int t = 0; t <= tiers; t++) {
         // conifers: the lower tier carries more of the crown (the broad skirt)
         float f = t / (float)tiers;
         if (conifer && tiers == 2 && t == 1) f = 0.55F;
         edge[t] = bottom + height * f;
      }
      float overlap = Math.min(2.0F, height * 0.1F);
      for (int k = 0; k < PLANES; k++) {
         float a = turn + (float)(Math.PI * k / PLANES);
         float dx = (float)Math.cos(a), dz = (float)Math.sin(a);
         if (conifer) {
            // a cone of stacked trapezoids through the crown's measured width at each tier edge, the sprite's
            // dense middle stretched over them (its outer fringe of stray needles would read as a narrower,
            // lumpy crown); the leader narrows to a point
            float[][] widths = new float[tiers + 1][];
            for (int t = 0; t <= tiers; t++) {
               float band = height / (tiers * 2.0F);
               // the outermost boughs set the silhouette; the sprite's ragged edge takes back ~10%
               widths[t] = t == tiers ? new float[]{0.12F, 0.12F} : this.reach(cx, cz, dx, dz, edge[t], edge[t] + band, 0.99F, 1.12F);
            }
            for (int t = 0; t < tiers; t++) {
               float ya = edge[t] - (t > 0 ? overlap : 0), yb = edge[t + 1];
               float lb = widths[t][0], rb = widths[t][1];
               float lt = Math.min(widths[t + 1][0], lb), rt = Math.min(widths[t + 1][1], rb);
               if (lb + rb < 0.3F) continue;
               this.plane(cx, cz, dx, dz, ya, yb, lb, rb, Math.max(0.1F, lt), Math.max(0.1F, rt),
                  bend((ya - bottom) / height), bend((yb - bottom) / height), CONIFER_UV, out);
            }
         } else {
            // a broad crown is round: the sprite's own ragged round clump shapes it. The clump fills about
            // the middle 80% of the sprite, so the card reaches that much past the crown on every side. A
            // crown too tall for one card is cut into slices of one card (the sprite sliced with it).
            float[] w = this.reach(cx, cz, dx, dz, bottom, top);
            if (w[0] + w[1] < 0.3F) continue;
            float grow = 0.12F / 0.76F;
            float l = w[0] * (1 + 2 * grow), r = w[1] * (1 + 2 * grow);
            float y0 = bottom - height * grow, y1 = top + height * grow * 0.6F;
            for (int t = 0; t < tiers; t++) {
               float fa = t / (float)tiers, fb = (t + 1) / (float)tiers;
               this.plane(cx, cz, dx, dz, y0 + (y1 - y0) * fa, y0 + (y1 - y0) * fb, l, r, l, r, bend(fa), bend(fb),
                  new float[]{0, 1 - fb, 1, 1 - fa}, out);
            }
         }
      }
      if (!conifer) {
         // a horizontal card through the upper crown: what an aerial or hillside view sees
         float[] r0 = this.reach(cx, cz, 1, 0, bottom, top), r1 = this.reach(cx, cz, 0, 1, bottom, top);
         float radius = 0.25F * (r0[0] + r0[1] + r1[0] + r1[1]) * 1.1F; // the sprite clump fills ~80%
         radius = Math.min(radius, MAX_SPAN * 0.5F);
         if (radius > 0.4F) this.top(cx, bottom + height * 0.74F, cz, radius, turn, out);
      }
   }

   /** Vertical part of a crown card's bent normal at a height fraction of the crown (shaded base, lit top). */
   private static float bend(float f) {
      return -0.45F + 1.05F * Math.min(1, Math.max(0, f));
   }

   /** Sprite windows {u0, v0, u1, v1}: the whole fringe, or its dense middle. */
   private static final float[] CONIFER_UV = {0.2F, 0.16F, 0.8F, 0.86F};

   /** 97th-percentile reach of the crown's cards to the -d and +d side, among cards reaching into [ya, yb]. */
   private float[] reach(float cx, float cz, float dx, float dz, float ya, float yb) {
      return this.reach(cx, cz, dx, dz, ya, yb, 0.97F, 1.0F);
   }

   private float[] reach(float cx, float cz, float dx, float dz, float ya, float yb, float p, float scale) {
      int n = this.count;
      float[] s = this.samples;
      // [perf2] scratch reused across the ~20 measurements of one growth (was three new arrays per call)
      if (this.neg.length < n) {
         this.neg = new float[n];
         this.pos = new float[n];
      }
      float[] neg = this.neg, pos = this.pos;
      int m = 0;
      for (int i = 0; i < n; i++) {
         float y = s[i * 4 + 1], e = s[i * 4 + 3];
         if (y + e < ya || y - e > yb) continue;
         float along = (s[i * 4] - cx) * dx + (s[i * 4 + 2] - cz) * dz;
         neg[m] = e - along;
         pos[m] = e + along;
         m++;
      }
      if (m == 0) return new float[]{0, 0};
      return new float[]{Math.max(0, percentile(neg, m, p) * scale), Math.max(0, percentile(pos, m, p) * scale)};
   }

   private float[] neg = new float[0], pos = new float[0];
   private float[] lo = new float[0], hi = new float[0]; // [perf3]

   private static float percentile(float[] values, float p) {
      return percentile(values, values.length, p);
   }

   /**
    * The value at rank round(p * (n - 1)) of the first n values, exactly what sorting them would give. [perf2] By
    * selection (linear) instead of a full sort: the sorts of a few thousand card samples, about twenty per growth, were
    * a third of an impostor-only growth and a sixth of a full one. Reorders the first n values.
    */
   static float percentile(float[] v, int n, float p) {
      int k = Math.min(n - 1, Math.max(0, Math.round(p * (n - 1))));
      int lo = 0, hi = n - 1;
      while (hi > lo) {
         int mid = (lo + hi) >>> 1;
         float x = v[lo], y = v[mid], z = v[hi];
         float pivot = x < y ? (y < z ? y : (x < z ? z : x)) : (x < z ? x : (y < z ? z : y));
         int i = lo, j = hi;
         while (i <= j) {
            while (v[i] < pivot) i++;
            while (v[j] > pivot) j--;
            if (i <= j) {
               float t = v[i];
               v[i] = v[j];
               v[j] = t;
               i++;
               j--;
            }
         }
         if (k <= j) hi = j;
         else if (k >= i) lo = i;
         else return v[k];
      }
      return v[k];
   }

   /**
    * One double-sided vertical crown card along d, from ya to yb, reaching lb/rb (bottom) and lt/rt (top)
    * to the -d/+d side. Normals are bent outwards and up/down like points on a rounded crown. A card wider
    * than {@link #MAX_SPAN} is split down its middle (with the sprite) so each half fits a chunk section.
    */
   private void plane(float cx, float cz, float dx, float dz, float ya, float yb, float lb, float rb, float lt, float rt,
                      float vb, float vt, float[] window, Out out) {
      float u0 = window[0], u1 = window[2], v0 = window[1], v1 = window[3];
      if (Math.max(lb + rb, lt + rt) <= MAX_SPAN) {
         this.card(cx, cz, dx, dz, new float[]{-lb, rb, rt, -lt}, ya, yb, new float[]{u0, u1, u1, u0}, v0, v1,
            new float[]{-1, 1, 1, -1}, vb, vt, out);
         return;
      }
      float mb = u0 + (u1 - u0) * lb / (lb + rb), mt = u0 + (u1 - u0) * lt / Math.max(1e-3F, lt + rt);
      this.card(cx, cz, dx, dz, new float[]{-lb, 0, 0, -lt}, ya, yb, new float[]{u0, mb, mt, u0}, v0, v1,
         new float[]{-1, 0, 0, -1}, vb, vt, out);
      this.card(cx, cz, dx, dz, new float[]{0, rb, rt, 0}, ya, yb, new float[]{mb, u1, u1, mt}, v0, v1,
         new float[]{0, 1, 1, 0}, vb, vt, out);
   }

   /** Corners (bottom -d, bottom +d, top +d, top -d) at offsets {@code along} from the centre line. */
   private void card(float cx, float cz, float dx, float dz, float[] along, float ya, float yb, float[] u, float v0, float v1,
                     float[] side, float vb, float vt, Out out) {
      float nx = -dz, nz = dx; // (d x up)
      float[] ys = {ya, ya, yb, yb}, vs = {v1, v1, v0, v0}, bend = {vb, vb, vt, vt};
      for (int reverse = 0; reverse < 2; reverse++) {
         float fx = reverse == 0 ? nx : -nx, fz = reverse == 0 ? nz : -nz;
         TreeShape.Quad q = TreeGrowth.newQuad(); // [perf3] pooled
         q.texture = 2;
         q.nx = fx;
         q.ny = 0;
         q.nz = fz;
         for (int i = 0; i < 4; i++) {
            int c = reverse == 0 ? i : 3 - i;
            float bx = fx * 0.5F + dx * side[c] * 0.6F, bz = fz * 0.5F + dz * side[c] * 0.6F;
            put(q, i, new float[]{cx + dx * along[c], ys[c], cz + dz * along[c]}, u[c], vs[c], bx, bend[c], bz);
         }
         out.quad(q);
      }
   }

   /** A double-sided horizontal card (the crown seen from above). */
   private void top(float cx, float y, float cz, float radius, float turn, Out out) {
      float ux = (float)Math.cos(turn), uz = (float)Math.sin(turn);
      float vx = uz, vz = -ux; // u x v = up
      float[][] corner = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
      for (int reverse = 0; reverse < 2; reverse++) {
         TreeShape.Quad q = TreeGrowth.newQuad(); // [perf3] pooled
         q.texture = 2;
         q.nx = 0;
         q.ny = reverse == 0 ? 1 : -1;
         q.nz = 0;
         for (int i = 0; i < 4; i++) {
            int c = reverse == 0 ? i : 3 - i;
            float a = corner[c][0], b = corner[c][1];
            float px = cx + (ux * a + vx * b) * radius, pz = cz + (uz * a + vz * b) * radius;
            // bent like the top of a dome (the underside like its shaded base)
            float ny = reverse == 0 ? 0.85F : -0.6F;
            put(q, i, new float[]{px, y, pz}, (a + 1) * 0.5F, (1 - b) * 0.5F, (ux * a + vx * b) * 0.45F, ny, (uz * a + vz * b) * 0.45F);
         }
         out.quad(q);
      }
   }

   // ------------------------------------------------------------------ trunk

   /** A three-sided tapering bark prism, split so no piece is taller than {@link #MAX_SPAN}. */
   private void trunk(Trunk t, float turn, Out out) {
      float span = t.y1() - t.y0();
      if (!(span > 0.3F)) return;
      int pieces = Math.max(1, (int)Math.ceil(span / MAX_SPAN));
      // a triangle drawn around the trunk shows between 1.5 and 1.73 times its circumradius: keep the width
      float k = 2.0F / 1.62F;
      for (int piece = 0; piece < pieces; piece++) {
         float f0 = piece / (float)pieces, f1 = (piece + 1) / (float)pieces;
         float[] c0 = lerp(t, f0), c1 = lerp(t, f1);
         float r0 = (t.r0() + (t.r1() - t.r0()) * f0) * k, r1 = (t.r0() + (t.r1() - t.r0()) * f1) * k;
         for (int side = 0; side < 3; side++) {
            float a0 = turn + (float)(Math.PI * 2 * side / 3), a1 = turn + (float)(Math.PI * 2 * (side + 1) / 3);
            float cos0 = (float)Math.cos(a0), sin0 = (float)Math.sin(a0), cos1 = (float)Math.cos(a1), sin1 = (float)Math.sin(a1);
            float[][] p = {
               {c0[0] + cos0 * r0, c0[1], c0[2] + sin0 * r0},
               {c1[0] + cos0 * r1, c1[1], c1[2] + sin0 * r1},
               {c1[0] + cos1 * r1, c1[1], c1[2] + sin1 * r1},
               {c0[0] + cos1 * r0, c0[1], c0[2] + sin1 * r0}};
            float[][] n = {{cos0, 0, sin0}, {cos0, 0, sin0}, {cos1, 0, sin1}, {cos1, 0, sin1}};
            float[][] uv = {{0, 1}, {0, 1 - (f1 - f0)}, {1, 1 - (f1 - f0)}, {1, 1}};
            float mx = (cos0 + cos1) * 0.5F, mz = (sin0 + sin1) * 0.5F;
            // wind the face so it points out of the prism
            float ex = p[2][0] - p[0][0], ey = p[2][1] - p[0][1], ez = p[2][2] - p[0][2];
            float gx = p[3][0] - p[1][0], gy = p[3][1] - p[1][1], gz = p[3][2] - p[1][2];
            float fx = ey * gz - ez * gy, fz = ex * gy - ey * gx;
            int[] order = fx * mx + fz * mz >= 0 ? new int[]{0, 1, 2, 3} : new int[]{0, 3, 2, 1};
            float ml = (float)Math.sqrt(mx * mx + mz * mz);
            TreeShape.Quad q = TreeGrowth.newQuad(); // [perf3] pooled
            q.texture = 0;
            q.nx = mx / ml;
            q.ny = 0;
            q.nz = mz / ml;
            for (int i = 0; i < 4; i++) {
               int c = order[i];
               put(q, i, p[c], uv[c][0], uv[c][1], n[c][0], n[c][1], n[c][2]);
            }
            out.quad(q);
         }
      }
   }

   private static float[] lerp(Trunk t, float f) {
      return new float[]{t.x0() + (t.x1() - t.x0()) * f, t.y0() + (t.y1() - t.y0()) * f, t.z0() + (t.z1() - t.z0()) * f};
   }

   private static void put(TreeShape.Quad q, int k, float[] pos, float u, float v, float nx, float ny, float nz) {
      float l = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
      if (l < 1e-6F) {
         nx = q.nx;
         ny = q.ny;
         nz = q.nz;
         l = 1;
      }
      int o = k * 8;
      q.v[o] = pos[0];
      q.v[o + 1] = pos[1];
      q.v[o + 2] = pos[2];
      q.v[o + 3] = Math.min(1, Math.max(0, u));
      q.v[o + 4] = Math.min(1, Math.max(0, v));
      q.v[o + 5] = nx / l;
      q.v[o + 6] = ny / l;
      q.v[o + 7] = nz / l;
   }
}
