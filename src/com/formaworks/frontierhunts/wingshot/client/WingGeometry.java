package com.formaworks.frontierhunts.wingshot.client;

/**
 * [wingshot] The Ultra birds' spread wings: a real wing skeleton (humerus, forearm, hand) carrying individual
 * feathers - ten primaries fanning from the hand, secondaries along the trailing edge of the forearm, tertials and
 * scapulars over the root, rows of greater, median and marginal coverts and primary coverts on top, the alula at the
 * wrist - each a slightly cambered two-segment quad with its own top and underside texture (cutout silhouettes from
 * the species' wing atlas). The sculpted body keeps its folded wing; this wing is drawn whenever the bird's wings are
 * open and folds back onto the flank as they close. Pure math: it fills float arrays the renderer (and the offline
 * preview) draws.
 */
public final class WingGeometry {
   /** per-vertex floats: x y z  nx ny nz  u v */
   public static final int STRIDE = 8;

   /** Feather kinds; their atlas regions (pixels, 256x256 atlas): top-face rect, bottom-face rect. */
   enum Part {
      PRIMARY_OUT(0, 0, 128, 16),
      PRIMARY_MID(0, 16, 128, 16),
      PRIMARY_IN(0, 32, 128, 16),
      SECONDARY(0, 48, 96, 24),
      SECONDARY_IN(0, 72, 96, 24),
      TERTIAL(0, 96, 96, 24),
      GREATER(0, 120, 64, 20),
      MEDIAN(0, 144, 48, 16),
      MARGINAL(0, 164, 32, 14),
      SCAPULAR(0, 180, 96, 28),
      PRIMARY_COVERT(0, 210, 64, 16),
      ALULA(0, 228, 40, 12);

      final float u0, v0, u1, v1;

      Part(int x, int y, int w, int h) {
         this.u0 = x / 256.0F;
         this.v0 = y / 256.0F;
         this.u1 = (x + w) / 256.0F;
         this.v1 = (y + h) / 256.0F;
      }
   }

   /** Wing dimensions (model units = blocks at the mesh's scale). */
   public static final class Spec {
      final float hum, fore, hand;
      final float[] primaryLen;
      final float primaryW, secLen, secW, tertLen, tertW;
      final int secondaries;
      final float bend;

      Spec(float hum, float fore, float hand, float[] primaryLen, float primaryW, int secondaries, float secLen, float secW, float tertLen, float tertW, float bend) {
         this.hum = hum;
         this.fore = fore;
         this.hand = hand;
         this.primaryLen = primaryLen;
         this.primaryW = primaryW;
         this.secondaries = secondaries;
         this.secLen = secLen;
         this.secW = secW;
         this.tertLen = tertLen;
         this.tertW = tertW;
         this.bend = bend;
      }

      /** semispan from the shoulder (fully spread) */
      public float semispan() {
         float maxP = 0.0F;
         for (float p : this.primaryLen) {
            maxP = Math.max(maxP, p);
         }
         return this.hum + this.fore + this.hand + maxP * 0.8F;
      }
   }

   /**
    * Mallard: span ~0.9 m at a body length of ~0.58 m; the mesh is 0.48 long, so ~0.75 here (0.33 per wing from the
    * shoulder). Pointed wing: the outer primaries (p9, p8) longest.
    */
   public static final Spec DUCK = new Spec(0.066F, 0.09F, 0.044F,
      new float[]{0.128F, 0.136F, 0.134F, 0.127F, 0.119F, 0.111F, 0.104F, 0.098F, 0.093F, 0.089F}, 0.036F, 12, 0.118F, 0.038F, 0.118F, 0.040F, 0.10F);
   /**
    * Ruffed grouse: short, broad, rounded wing (span ~0.6 m on a 0.45 m bird; 0.36-long mesh -> ~0.48 span); the middle
    * primaries longest, strongly curved.
    */
   public static final Spec GROUSE = new Spec(0.042F, 0.054F, 0.03F,
      new float[]{0.072F, 0.081F, 0.087F, 0.09F, 0.089F, 0.086F, 0.082F, 0.078F, 0.075F, 0.072F}, 0.03F, 10, 0.08F, 0.034F, 0.08F, 0.034F, 0.16F);

   private final float[] buf;
   private int n;

   public WingGeometry() {
      this.buf = new float[2 * 80 * 24 * STRIDE];
   }

   public float[] data() {
      return this.buf;
   }

   /** number of vertices written (triangles = vertices / 3) */
   public int vertices() {
      return this.n;
   }

   // ================================================================ build

   /**
    * Build both wings for this frame. {@code chest}: the posed chest bone matrix (3x4 row-major, model space);
    * {@code jl}/{@code jr}: the rest-pose shoulder joints (wing_l / wing_r bones) in model space.
    */
   public void build(Spec spec, BirdAnim.State st, float[] chest, int co, float[] jl, float[] jr) {
      this.n = 0;
      if (!st.wingsOut) {
         return;
      }
      this.wing(spec, st, chest, co, jr, 1.0F);
      this.wing(spec, st, chest, co, jl, -1.0F);
   }

   // scratch frames: 3x3 row-major
   private final float[] rh = new float[9], rf = new float[9], rw = new float[9], tmp = new float[9], tmp2 = new float[9];
   private final float[] e = new float[3], w = new float[3], hb = new float[3];
   private static final float[] ZERO = {0.0F, 0.0F, 0.0F};
   private float[] chest;
   private int co;
   private float side;
   private float sx, sy, sz;

   private void wing(Spec sp, BirdAnim.State st, float[] chest, int co, float[] joint, float side) {
      this.chest = chest;
      this.co = co;
      this.side = side;
      // shoulder: a touch above and outside the joint, so the root emerges from the back rather than mid-flank
      this.sx = joint[0] + side * 0.012F;
      this.sy = joint[1] + 0.012F;
      this.sz = joint[2] + 0.006F;
      float spread = BirdAnim.smooth(Math.min(1.0F, Math.max(0.0F, st.spread)));
      float flex = st.flex;
      // planar directions in the wing plane (0 = straight out, + = swept back), folded -> spread
      float psiH = lerp(1.75F, 0.08F - st.sweep * 0.6F, spread);
      float psiF = lerp(-1.38F, -0.12F - st.sweep * 0.45F - 0.3F * flex, spread);
      float psiW = lerp(1.62F, 0.16F - st.sweep * 0.25F + 1.0F * flex + 0.15F * st.cup, spread);
      float elev = st.elev;
      // hand: tips trail up under load on the downstroke, droop when cupped
      float handBend = (0.12F - 0.35F * flex) * spread - 0.75F * st.cup * spread;
      float foreDih = 0.05F * spread - 0.1F * st.cup * spread;
      float twist = st.twist * spread;
      frame(this.rh, elev, psiH, -twist * 0.4F, 0.0F);
      frame(this.rf, elev + foreDih, psiF, -twist * 0.8F, 0.0F);
      frame(this.rw, elev + foreDih, psiW, -twist, handBend);
      // joints
      float[] S = ZERO;
      mulAdd(this.rh, sp.hum, 0, 0, S, this.e);
      mulAdd(this.rf, sp.fore, 0, 0, this.e, this.w);
      float fan = st.fan * spread;
      float lay = 0.0016F;
      // ---- primaries (p10 outermost first), from the hand: fanned when spread, stacked when folded
      for (int k = 0; k < 10; k++) {
         float t = 1.0F - k * 0.095F; // attachment along the hand (outer ones at the tip)
         float rel = lerp(0.02F * k, -0.03F + k * 0.118F * fan, spread);
         float len = sp.primaryLen[k];
         Part part = k < 3 ? Part.PRIMARY_OUT : (k < 7 ? Part.PRIMARY_MID : Part.PRIMARY_IN);
         float[] hb = this.hb;
         mulAdd(this.rw, sp.hand * t, 0, 0, this.w, hb);
         this.feather(this.rw, hb[0], hb[1], hb[2], rel, len, sp.primaryW, sp.bend * 1.2F, -k * 0.0004F, part);
      }
      // ---- primary coverts over the bases of the primaries
      for (int k = 0; k < 7; k++) {
         float t = 0.95F - k * 0.14F;
         float[] hb = this.hb;
         mulAdd(this.rw, sp.hand * t, 0, 0, this.w, hb);
         float rel = lerp(0.03F * k, 0.05F + k * 0.13F * fan, spread);
         this.feather(this.rw, hb[0], hb[1], hb[2], rel, sp.primaryLen[5] * 0.42F, sp.primaryW * 1.1F, sp.bend, lay * 3 + k * 0.0003F, Part.PRIMARY_COVERT);
      }
      // ---- alula at the wrist (raised when landing)
      {
         float rel = -0.35F - 0.25F * st.cup;
         this.feather(this.rw, this.w[0], this.w[1], this.w[2], rel, sp.hand * 0.75F, sp.primaryW * 0.8F, 0.0F, lay * 5 + 0.004F * st.cup, Part.ALULA);
      }
      // ---- secondaries along the forearm, trailing straight back (absolute direction ~ backward)
      int ns = sp.secondaries;
      for (int i = 0; i < ns; i++) {
         float t = 1.0F - i / (float)(ns - 1) * 0.98F; // from the wrist (s1) in to the elbow
         float[] fb = this.hb;
         mulAdd(this.rf, sp.fore * t, 0, 0, this.e, fb);
         float abs = lerp(1.62F + 0.01F * i, 1.42F + 0.016F * i - 0.1F * flex, spread);
         float rel = abs - psiF;
         boolean spec = i >= 2 && i <= 9;
         this.feather(this.rf, fb[0], fb[1], fb[2], rel, sp.secLen * (1.0F - 0.004F * i), sp.secW, sp.bend * 0.5F, i * 0.0003F, spec ? Part.SECONDARY : Part.SECONDARY_IN);
      }
      // ---- tertials over the inner wing (from the humerus)
      for (int i = 0; i < 4; i++) {
         float t = 0.95F - i * 0.2F;
         float[] hb = this.hb;
         mulAdd(this.rh, sp.hum * t, 0, 0, S, hb);
         float abs = lerp(1.7F, 1.52F + i * 0.07F, spread);
         this.feather(this.rh, hb[0], hb[1], hb[2], abs - psiH, sp.tertLen * (1.0F - i * 0.06F), sp.tertW, sp.bend * 0.4F, lay + i * 0.0004F, Part.TERTIAL);
      }
      // ---- greater coverts (a row over the secondaries' bases), median and marginal coverts toward the leading edge
      for (int i = 0; i < ns; i++) {
         float t = 1.0F - i / (float)(ns - 1) * 0.98F;
         float[] fb = this.hb;
         mulAdd(this.rf, sp.fore * t, 0, 0, this.e, fb);
         float abs = lerp(1.6F, 1.4F + 0.015F * i, spread);
         this.feather(this.rf, fb[0], fb[1], fb[2], abs - psiF, sp.secLen * 0.56F, sp.secW * 0.95F, sp.bend * 0.3F, lay * 2 + i * 0.0003F, Part.GREATER);
      }
      for (int i = 0; i < 10; i++) {
         float t = 1.0F - i / 9.0F * 0.96F;
         float[] fb = this.hb;
         mulAdd(this.rf, sp.fore * t, 0, 0, this.e, fb);
         float abs = lerp(1.6F, 1.35F, spread);
         this.feather(this.rf, fb[0], fb[1], fb[2], abs - psiF, sp.secLen * 0.34F, sp.secW * 0.85F, 0.0F, lay * 4 + i * 0.0002F, Part.MEDIAN);
      }
      for (int i = 0; i < 9; i++) {
         // marginal coverts along the whole leading edge: humerus then forearm
         boolean onHum = i < 3;
         float t = onHum ? 0.2F + i * 0.3F : (i - 3) / 5.0F;
         float[] fb = this.hb;
         if (onHum) {
            mulAdd(this.rh, sp.hum * t, 0, 0, S, fb);
         } else {
            mulAdd(this.rf, sp.fore * t, 0, 0, this.e, fb);
         }
         float[] fr = onHum ? this.rh : this.rf;
         float abs = lerp(1.6F, 1.3F, spread);
         this.feather(fr, fb[0], fb[1], fb[2], abs - (onHum ? psiH : psiF), sp.secLen * 0.22F, sp.secW * 0.8F, 0.0F, lay * 6 + i * 0.0002F, Part.MARGINAL);
      }
      // ---- scapulars: the long back feathers lying over the wing root
      for (int i = 0; i < 4; i++) {
         float t = 0.05F + i * 0.18F;
         float[] hb = this.hb;
         mulAdd(this.rh, sp.hum * t, 0, 0, S, hb);
         float abs = lerp(1.68F, 1.62F + 0.05F * i, spread);
         this.feather(this.rh, hb[0], hb[1] + 0.004F, hb[2] - 0.01F, abs - psiH, sp.tertLen * 1.05F, sp.tertW * 1.2F, 0.0F, lay * 7 + i * 0.0004F, Part.SCAPULAR);
      }
   }

   /**
    * Segment frame: elevation about the body axis, planar direction {@code psi} (swept back from straight out), twist
    * about the segment axis (+ = trailing edge up), and {@code bend} (tip up) about the chord axis.
    */
   private void frame(float[] out, float elev, float psi, float twist, float bend) {
      // R = Rz(elev) * Yb(psi) * Rz(bend) * Rx(twist); columns are the segment's out / up / back axes
      float ce = (float)Math.cos(elev), se = (float)Math.sin(elev);
      float cp = (float)Math.cos(psi), spn = (float)Math.sin(psi);
      float cb = (float)Math.cos(bend), sb = (float)Math.sin(bend);
      float ct = (float)Math.cos(twist), st = (float)Math.sin(twist);
      // Rz(elev)
      float[] a = {ce, -se, 0, se, ce, 0, 0, 0, 1};
      // Yb(psi): x -> (cos, 0, sin), z -> (-sin, 0, cos)
      float[] b = {cp, 0, -spn, 0, 1, 0, spn, 0, cp};
      float[] c = {cb, -sb, 0, sb, cb, 0, 0, 0, 1};
      float[] d = {1, 0, 0, 0, ct, -st, 0, st, ct};
      mul(a, b, this.tmp);
      mul(this.tmp, c, this.tmp2);
      mul(this.tmp2, d, out);
   }

   private static void mul(float[] a, float[] b, float[] o) {
      for (int r = 0; r < 3; r++) {
         for (int c = 0; c < 3; c++) {
            o[r * 3 + c] = a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c];
         }
      }
   }

   /** o = base + R * (x, y, z) */
   private static void mulAdd(float[] R, float x, float y, float z, float[] base, float[] o) {
      o[0] = base[0] + R[0] * x + R[1] * y + R[2] * z;
      o[1] = base[1] + R[3] * x + R[4] * y + R[5] * z;
      o[2] = base[2] + R[6] * x + R[7] * y + R[8] * z;
   }

   // per-feather scratch
   private final float[] fp = new float[3 * 6], fnrm = new float[3];

   /**
    * One feather: base (wing space), direction {@code rel} radians back from the segment's out axis in its plane,
    * length, width, camber (tip curls down by bend*len), lift above the plane (layering), atlas part.
    */
   private void feather(float[] R, float bx, float by, float bz, float rel, float len, float width, float bend, float lift, Part part) {
      if (this.n + 24 > this.buf.length / STRIDE) {
         return;
      }
      float c = (float)Math.cos(rel), sn = (float)Math.sin(rel);
      // in segment-local coords: along = (c, 0, sn), across (toward trailing side) = (-sn, 0, c), up = (0, 1, 0)
      float ax = R[0] * c + R[2] * sn, ay = R[3] * c + R[5] * sn, az = R[6] * c + R[8] * sn;
      float wx = -R[0] * sn + R[2] * c, wy = -R[3] * sn + R[5] * c, wz = -R[6] * sn + R[8] * c;
      float ux = R[1], uy = R[4], uz = R[7];
      float hw = width * 0.5F;
      // three rows along the feather (base, middle, tip), the tip curling down
      for (int r = 0; r < 3; r++) {
         float t = r * 0.5F;
         float down = -bend * len * t * t + lift;
         float px = bx + ax * len * t + ux * down, py = by + ay * len * t + uy * down, pz = bz + az * len * t + uz * down;
         // the feather sits with its leading vane on the axis (a little to the front), trailing vane behind
         this.fp[r * 6] = px - wx * hw * 0.55F;
         this.fp[r * 6 + 1] = py - wy * hw * 0.55F;
         this.fp[r * 6 + 2] = pz - wz * hw * 0.55F;
         this.fp[r * 6 + 3] = px + wx * hw * 1.45F;
         this.fp[r * 6 + 4] = py + wy * hw * 1.45F;
         this.fp[r * 6 + 5] = pz + wz * hw * 1.45F;
      }
      // normal of the (roughly flat) feather: the segment's up, tipped by the camber
      float nx = ux - ax * bend * 0.8F, ny = uy - ay * bend * 0.8F, nz = uz - az * bend * 0.8F;
      float l = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
      this.fnrm[0] = nx / l;
      this.fnrm[1] = ny / l;
      this.fnrm[2] = nz / l;
      float uTop0 = part.u0, uTop1 = part.u1;
      float vT0 = part.v0, vT1 = part.v1;
      float uB0 = part.u0 + 0.5F, uB1 = part.u1 + 0.5F;
      for (int r = 0; r < 2; r++) {
         float t0 = r * 0.5F, t1 = t0 + 0.5F;
         // top face (seen from above), then the underside (reversed winding, its own texture)
         this.quad(r, r + 1, uTop0 + (uTop1 - uTop0) * t0, uTop0 + (uTop1 - uTop0) * t1, vT0, vT1, true);
         this.quad(r, r + 1, uB0 + (uB1 - uB0) * t0, uB0 + (uB1 - uB0) * t1, vT0, vT1, false);
      }
   }

   private void quad(int r0, int r1, float ua, float ub, float va, float vb, boolean top) {
      // corners: (r0, lead) (r0, trail) (r1, trail) (r1, lead)
      float lift = top ? 0.0004F : -0.0004F;
      float nx = top ? this.fnrm[0] : -this.fnrm[0], ny = top ? this.fnrm[1] : -this.fnrm[1], nz = top ? this.fnrm[2] : -this.fnrm[2];
      int[] order;
      // winding: counter-clockwise seen from the face's side; the left wing is mirrored, which flips it
      boolean ccw = top == (this.side > 0.0F);
      order = ccw ? Q_CCW : Q_CW;
      for (int k = 0; k < 6; k++) {
         int corner = order[k];
         int row = corner < 2 ? r0 : r1;
         boolean trail = corner == 1 || corner == 2;
         int o = row * 6 + (trail ? 3 : 0);
         float x = this.fp[o] + this.fnrm[0] * lift, y = this.fp[o + 1] + this.fnrm[1] * lift, z = this.fp[o + 2] + this.fnrm[2] * lift;
         float u = corner < 2 ? ua : ub;
         float v = trail ? vb : va;
         this.emit(x, y, z, nx, ny, nz, u, v);
      }
   }

   /** counter-clockwise seen from the face's normal side (Minecraft culls clockwise faces) */
   private static final int[] Q_CCW = {0, 1, 2, 0, 2, 3};
   private static final int[] Q_CW = {0, 3, 2, 0, 2, 1};

   /** wing space (right-wing coordinates from the shoulder) -> model space through the chest bone */
   private void emit(float x, float y, float z, float nx, float ny, float nz, float u, float v) {
      x = x * this.side + this.sx;
      y = y + this.sy;
      z = z + this.sz;
      nx *= this.side;
      float[] M = this.chest;
      int o = this.co;
      float px = M[o] * x + M[o + 1] * y + M[o + 2] * z + M[o + 3];
      float py = M[o + 4] * x + M[o + 5] * y + M[o + 6] * z + M[o + 7];
      float pz = M[o + 8] * x + M[o + 9] * y + M[o + 10] * z + M[o + 11];
      float qx = M[o] * nx + M[o + 1] * ny + M[o + 2] * nz;
      float qy = M[o + 4] * nx + M[o + 5] * ny + M[o + 6] * nz;
      float qz = M[o + 8] * nx + M[o + 9] * ny + M[o + 10] * nz;
      float l = (float)Math.sqrt(qx * qx + qy * qy + qz * qz) + 1.0E-9F;
      int b = this.n * STRIDE;
      this.buf[b] = px;
      this.buf[b + 1] = py;
      this.buf[b + 2] = pz;
      this.buf[b + 3] = qx / l;
      this.buf[b + 4] = qy / l;
      this.buf[b + 5] = qz / l;
      this.buf[b + 6] = u;
      this.buf[b + 7] = v;
      this.n++;
   }

   static float lerp(float a, float b, float t) {
      return a + (b - a) * t;
   }
}
