package com.formaworks.frontierhunts.client.rack;

import java.util.Random;

/**
 * [1.1.8] Procedural antler racks for whitetail, elk and moose, built as smooth tubes (beams and tines) and, for
 * moose, cupped palms - written to replace the old racks whose extra points grew as long straight spikes.
 *
 * <p>Pure geometry, no Minecraft types (unit-testable offline, tools/rack). Coordinates are the skeleton's antler frame
 * in metres: +X to one side (each side is mirrored), +Y up off the skull, +Z back toward the tail; the origin lies
 * between the pedicles. Every rack has a family shape that follows the real animal, scaled by its size, plus one of
 * five variants (wide, tall, basket, heavy, sweeper / dagger / whale-tail / butterfly...) and seeded small differences
 * left to right, so no two heads match. Extra points on big or non-typical heads come as the things real antlers grow:
 * more tines in the right places, kickers, stickers, drop tines and split brows - never longer spikes.
 */
public final class RackGen {
   private RackGen() {
   }

   public enum Family {
      WHITETAIL, ELK, MOOSE
   }

   /** what a rack is built from (all derived from the animal's traits by {@code RackDraw}) */
   public static final class Params {
      public Family family = Family.WHITETAIL;
      /** typical points per side, beam tip included (whitetail 1..7, elk 2..7, moose 2..12) */
      public int pointsL = 4, pointsR = 4;
      /** non-typical points per side */
      public int abnL, abnR;
      /** overall size, 1 = a mature animal's rack */
      public float size = 1.0F;
      /** beam thickness multiplier */
      public float mass = 1.0F;
      public float spread = 1.0F, height = 1.0F, depth = 1.0F;
      /** half the distance between the pedicles (m) */
      public float pedicle = 0.072F;
      public int variant;
      public long seed = 1L;
   }

   /** a finished mesh: per vertex position (3), normal (3), uv (2), shade (1); triangles as vertex indices */
   public static final class Mesh {
      public float[] pos = new float[3 * 512], nrm = new float[3 * 512], uv = new float[2 * 512], shade = new float[512];
      public int[] tri = new int[3 * 1024];
      public int vertices, triangles;

      int vertex(float x, float y, float z, float nx, float ny, float nz, float u, float v, float s) {
         if (this.vertices == this.shade.length) {
            int n = this.shade.length * 2;
            this.pos = java.util.Arrays.copyOf(this.pos, n * 3);
            this.nrm = java.util.Arrays.copyOf(this.nrm, n * 3);
            this.uv = java.util.Arrays.copyOf(this.uv, n * 2);
            this.shade = java.util.Arrays.copyOf(this.shade, n);
         }
         int i = this.vertices++;
         this.pos[i * 3] = x;
         this.pos[i * 3 + 1] = y;
         this.pos[i * 3 + 2] = z;
         float l = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
         if (l < 1.0E-6F) {
            nx = 0.0F;
            ny = 1.0F;
            nz = 0.0F;
            l = 1.0F;
         }
         this.nrm[i * 3] = nx / l;
         this.nrm[i * 3 + 1] = ny / l;
         this.nrm[i * 3 + 2] = nz / l;
         this.uv[i * 2] = u;
         this.uv[i * 2 + 1] = v;
         this.shade[i] = s;
         return i;
      }

      void tri(int a, int b, int c) {
         if (this.triangles * 3 + 3 > this.tri.length) {
            this.tri = java.util.Arrays.copyOf(this.tri, this.tri.length * 2);
         }
         this.tri[this.triangles * 3] = a;
         this.tri[this.triangles * 3 + 1] = b;
         this.tri[this.triangles * 3 + 2] = c;
         this.triangles++;
      }
   }

   // ============================================================================================ small vector maths

   static float[] v(float x, float y, float z) {
      return new float[]{x, y, z};
   }

   static float[] add(float[] a, float[] b) {
      return v(a[0] + b[0], a[1] + b[1], a[2] + b[2]);
   }

   static float[] sub(float[] a, float[] b) {
      return v(a[0] - b[0], a[1] - b[1], a[2] - b[2]);
   }

   static float[] mul(float[] a, float k) {
      return v(a[0] * k, a[1] * k, a[2] * k);
   }

   static float dot(float[] a, float[] b) {
      return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
   }

   static float[] cross(float[] a, float[] b) {
      return v(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]);
   }

   static float[] norm(float[] a) {
      float l = (float)Math.sqrt(dot(a, a));
      return l < 1.0E-7F ? v(0.0F, 1.0F, 0.0F) : mul(a, 1.0F / l);
   }

   static float[] lerp(float[] a, float[] b, float t) {
      return v(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t);
   }

   static float[] bez(float[] p0, float[] p1, float[] p2, float[] p3, float t) {
      float u = 1.0F - t;
      float a = u * u * u, b = 3.0F * u * u * t, c = 3.0F * u * t * t, d = t * t * t;
      return v(a * p0[0] + b * p1[0] + c * p2[0] + d * p3[0], a * p0[1] + b * p1[1] + c * p2[1] + d * p3[1], a * p0[2] + b * p1[2] + c * p2[2] + d * p3[2]);
   }

   /** a cubic curve, sampled; ctrl are the four points */
   static float[][] curve(float[][] ctrl, int n) {
      float[][] out = new float[n + 1][];
      for (int i = 0; i <= n; i++) {
         out[i] = bez(ctrl[0], ctrl[1], ctrl[2], ctrl[3], i / (float)n);
      }
      return out;
   }

   static float[] at(float[][] path, float t) {
      float f = Math.max(0.0F, Math.min(1.0F, t)) * (path.length - 1);
      int i = Math.min(path.length - 2, (int)f);
      return lerp(path[i], path[i + 1], f - i);
   }

   static float[] tangent(float[][] path, float t) {
      float f = Math.max(0.0F, Math.min(1.0F, t)) * (path.length - 1);
      int i = Math.min(path.length - 2, (int)f);
      return norm(sub(path[i + 1], path[i]));
   }

   // ============================================================================================ tubes

   /**
    * A smooth tapering tube along {@code path}: rings of {@code sides} vertices carried along by parallel transport
    * (no twisting), a rounded tip, texture v from base (dark) to tip (ivory). {@code r0}/{@code r1} are base/tip
    * radii; {@code flare} widens the first ring (where a tine grows out of the beam, or the burr at the skull).
    */
   static void tube(Mesh m, float[][] path, float r0, float r1, int sides, float flare, float shade0, float shade1, float u0) {
      int n = path.length;
      float[] t0 = norm(sub(path[1], path[0]));
      float[] ref = Math.abs(t0[1]) < 0.9F ? v(0.0F, 1.0F, 0.0F) : v(1.0F, 0.0F, 0.0F);
      float[] nrm = norm(cross(t0, ref));
      int base = m.vertices;
      float len = 0.0F;
      float[] lens = new float[n];
      for (int i = 1; i < n; i++) {
         len += Math.sqrt(dot(sub(path[i], path[i - 1]), sub(path[i], path[i - 1])));
         lens[i] = len;
      }
      for (int i = 0; i < n; i++) {
         float[] t = i == 0 ? t0 : i == n - 1 ? norm(sub(path[i], path[i - 1])) : norm(sub(path[i + 1], path[i - 1]));
         nrm = norm(sub(nrm, mul(t, dot(nrm, t))));
         float[] bin = cross(t, nrm);
         float f = len <= 0.0F ? 0.0F : lens[i] / len;
         // taper: quick at the very tip (rounded point), slow along the length
         float r = r0 + (r1 - r0) * (float)Math.pow(f, 1.15);
         if (i == n - 1) {
            r = Math.max(r1 * 0.35F, 0.0012F);
         } else if (i == n - 2) {
            r *= 0.88F;
         }
         if (i == 0 && flare > 1.0F) {
            r *= flare;
         } else if (i == 1 && flare > 1.0F) {
            r *= 1.0F + (flare - 1.0F) * 0.35F;
         }
         float s = shade0 + (shade1 - shade0) * f;
         for (int k = 0; k <= sides; k++) {
            double a = Math.PI * 2.0 * k / sides;
            float c = (float)Math.cos(a), sn = (float)Math.sin(a);
            float[] d = add(mul(nrm, c), mul(bin, sn));
            m.vertex(path[i][0] + d[0] * r, path[i][1] + d[1] * r, path[i][2] + d[2] * r, d[0], d[1], d[2], u0 + k / (float)sides, 0.04F + 0.92F * f, s);
         }
      }
      int ring = sides + 1;
      for (int i = 0; i < n - 1; i++) {
         for (int k = 0; k < sides; k++) {
            int a = base + i * ring + k, b = a + 1, c = a + ring, d = c + 1;
            m.tri(a, c, b);
            m.tri(b, c, d);
         }
      }
      // rounded tip
      float[] tip = path[n - 1];
      float[] tt = norm(sub(path[n - 1], path[n - 2]));
      float tr = Math.max(r1 * 0.35F, 0.0012F);
      int apex = m.vertex(tip[0] + tt[0] * tr, tip[1] + tt[1] * tr, tip[2] + tt[2] * tr, tt[0], tt[1], tt[2], u0 + 0.5F, 0.98F, shade1);
      int last = base + (n - 1) * ring;
      for (int k = 0; k < sides; k++) {
         m.tri(last + k, apex, last + k + 1);
      }
   }

   // ============================================================================================ racks

   public static Mesh build(Params p, int sides) {
      Mesh m = new Mesh();
      sides = Math.max(4, Math.min(10, sides));
      for (int side = 0; side < 2; side++) {
         boolean left = side == 0;
         Random r = new Random(p.seed * 31L + (left ? 17L : 91L));
         float s = left ? -1.0F : 1.0F;
         // left and right are never quite the same
         float lr = 1.0F + (r.nextFloat() - 0.5F) * 0.07F;
         int pts = Math.max(1, left ? p.pointsL : p.pointsR);
         int abn = Math.max(0, left ? p.abnL : p.abnR);
         switch (p.family) {
            case ELK -> elkSide(m, p, s, pts, abn, lr, r, sides);
            case MOOSE -> mooseSide(m, p, s, pts, abn, lr, r, sides);
            default -> whitetailSide(m, p, s, pts, abn, lr, r, sides);
         }
      }
      return m;
   }

   /** a point mirrored to one side: x by the side sign */
   static float[] sv(float s, float x, float y, float z) {
      return v(s * x, y, z);
   }

   // ------------------------------------------------------------------------------------------------ whitetail

   static void whitetailSide(Mesh m, Params p, float s, int pts, int abn, float lr, Random r, int sides) {
      float k = p.size * lr;
      float w = p.spread, h = p.height, d = p.depth;
      float mass = p.mass;
      int var = p.variant;
      if (var == 0) {
         w *= 1.16F; // wide
         h *= 0.92F;
      } else if (var == 1) {
         h *= 1.08F; // tall tines, tighter
         w *= 0.92F;
      } else if (var == 2) {
         w *= 0.88F; // basket: tips hook in
      } else if (var == 3) {
         mass *= 1.25F; // heavy, chunky
      } else {
         d *= 1.15F; // sweeper: long beams reaching forward
      }
      float ph = p.pedicle;
      float beamR = 0.0172F * mass * (0.6F + 0.4F * Math.min(1.25F, p.size));
      float[][] beam;
      int n = sides + 6;
      if (pts <= 1) {
         // spike: one straight-ish point up, a little out and back
         beam = curve(new float[][]{sv(s, ph, 0, 0), sv(s, ph + 0.02F * k, 0.07F * k * h, 0.03F * k), sv(s, ph + 0.04F * k, 0.13F * k * h, 0.02F * k),
            sv(s, ph + 0.045F * k, 0.19F * k * h, -0.01F * k)}, n / 2 + 2);
         tube(m, beam, beamR * 0.9F, beamR * 0.25F, sides, 1.35F, 0.0F, 1.0F, 0.0F);
         return;
      }
      float beamLen = pts == 2 ? 0.62F : 1.0F;
      float inHook = var == 2 ? 0.08F : 0.0F;
      float[] p0 = sv(s, ph, 0, 0);
      // out, up and back off the skull, then sweeping round and forward: the tips end above the eyes, not past the nose
      float[] p1 = sv(s, ph + 0.12F * k * w * beamLen, 0.07F * k * h, 0.12F * k * d * beamLen);
      float[] p2 = sv(s, 0.27F * k * w * beamLen, 0.13F * k * h * (0.8F + 0.2F * beamLen), 0.05F * k * d * beamLen);
      float[] p3 = sv(s, (0.19F - inHook) * k * w * beamLen, 0.16F * k * h * (0.75F + 0.25F * beamLen), (-0.10F - (var == 4 ? 0.06F : 0.0F)) * k * d * beamLen);
      beam = curve(new float[][]{p0, p1, p2, p3}, n);
      tube(m, beam, beamR, beamR * 0.32F, sides, 1.45F, 0.0F, 1.0F, 0.0F);
      // the tines that real whitetails grow, in the order they appear: G2, brow (G1), G3, G4, G5, G6
      float[][] tines = {
         {0.38F, 0.20F}, // G2: tallest
         {0.08F, 0.08F}, // G1 brow
         {0.58F, 0.165F}, // G3
         {0.76F, 0.11F}, // G4
         {0.88F, 0.07F}, // G5
         {0.50F, 0.07F} // G6 (rare, between G2 and G3 on giants)
      };
      int typical = Math.min(tines.length, pts - 1);
      if (pts == 2) {
         // forkhorn: the beam forks once
         addTine(m, beam, 0.62F, 0.16F * k * h, s, beamR, sides, r, var, false, 0.0F);
      } else {
         for (int i = 0; i < typical; i++) {
            float t = tines[i][0] + (r.nextFloat() - 0.5F) * 0.04F;
            float len = tines[i][1] * k * h * (0.9F + r.nextFloat() * 0.2F);
            addTine(m, beam, t, len, s, beamR, sides, r, var, i == 1, i == 1 ? 0.35F : 0.0F);
         }
      }
      // non-typical: kickers, stickers, drop tines, a split brow
      for (int i = 0; i < abn; i++) {
         int kind = Math.floorMod((int)(p.seed >>> (i * 3)) + i, 5);
         float t = 0.18F + r.nextFloat() * 0.6F;
         float[] a = at(beam, t), tg = tangent(beam, t);
         float len = (0.035F + r.nextFloat() * 0.07F) * k;
         float[] dir;
         switch (kind) {
            case 0 -> dir = norm(v(s * 0.25F, -1.0F, 0.15F)); // drop tine
            case 1 -> dir = norm(v(s * 0.9F, 0.4F, r.nextFloat() - 0.5F)); // kicker outward
            case 2 -> dir = norm(v(-s * 0.6F, 0.7F, -0.2F)); // sticker inward
            case 3 -> {
               t = 0.1F; // split brow
               a = at(beam, t);
               dir = norm(v(s * 0.2F, 1.0F, -0.35F));
               len = 0.07F * k;
            }
            default -> dir = norm(v(s * 0.3F, 0.6F, 0.6F)); // a point off the back of the beam
         }
         dir = norm(sub(dir, mul(tg, dot(dir, tg) * 0.6F)));
         float rb = beamR * (1.0F - 0.55F * t);
         float[] start = sub(a, mul(dir, rb * 0.6F));
         float[] curl = norm(v(r.nextFloat() - 0.5F, r.nextFloat() - 0.3F, r.nextFloat() - 0.5F));
         float[][] path = curve(new float[][]{start, add(a, mul(dir, len * 0.4F)), add(add(a, mul(dir, len * 0.75F)), mul(curl, len * 0.12F)),
            add(add(a, mul(dir, len)), mul(curl, len * 0.25F))}, Math.max(3, sides / 2 + 1));
         tube(m, path, rb * 0.55F, rb * 0.15F, Math.max(4, sides - 2), 1.25F, 0.35F, 1.0F, 0.37F);
      }
   }

   /** a whitetail tine rising off the top of the beam: mostly up, leaning a little in, curving in at the tip */
   static void addTine(Mesh m, float[][] beam, float t, float len, float s, float beamR, int sides, Random r, int var, boolean brow, float fwd) {
      float[] a = at(beam, t);
      float rb = beamR * (1.0F - 0.62F * t);
      float lean = var == 2 ? 0.30F : 0.16F;
      // rises up and a little back, then the tip curls in and forward (tines are curved, never straight spikes)
      float[] dir = norm(v(-s * lean, 1.0F, 0.18F - fwd + (var == 4 ? -0.14F : 0.0F)));
      float[] curl = norm(v(-s * 0.7F, 0.1F, var == 4 ? -0.7F : -0.45F));
      float[] start = sub(a, mul(dir, rb * 1.0F)); // grows out from inside the beam, so the joint is seamless
      float[][] path = curve(new float[][]{start, add(a, mul(dir, len * 0.42F)), add(add(a, mul(dir, len * 0.8F)), mul(curl, len * 0.10F)),
         add(add(a, mul(dir, len * (brow ? 0.92F : 0.95F))), mul(curl, len * 0.26F))}, Math.max(4, sides / 2 + 2));
      tube(m, path, rb * (brow ? 0.6F : 0.7F), rb * 0.15F, Math.max(4, sides - 1), 1.18F, 0.3F, 1.0F, 0.21F);
   }

   // ------------------------------------------------------------------------------------------------ elk

   static void elkSide(Mesh m, Params p, float s, int pts, int abn, float lr, Random r, int sides) {
      float k = p.size * lr;
      float w = p.spread, h = p.height, d = p.depth, mass = p.mass;
      int var = p.variant;
      if (var == 0) {
         w *= 1.18F;
      } else if (var == 1) {
         h *= 1.15F;
         w *= 0.9F;
      } else if (var == 3) {
         mass *= 1.2F;
      }
      float ph = p.pedicle;
      float beamR = 0.028F * mass * (0.5F + 0.5F * Math.min(1.2F, p.size));
      float young = pts <= 3 ? 0.62F : 1.0F;
      float hook = var == 4 ? 0.12F : 0.0F; // crab-claw: tips curl in
      // up and back over the neck in a long arc, swinging out, the end curling up and in
      float[][] beam = curve(new float[][]{sv(s, ph, 0, 0), sv(s, 0.24F * k * w, 0.26F * k * h * young, 0.36F * k * d * young),
         sv(s, 0.52F * k * w, 0.66F * k * h * young, 0.70F * k * d * young), sv(s, (0.40F - hook) * k * w, 0.90F * k * h * young, 0.52F * k * d * young)}, sides + 8);
      tube(m, beam, beamR, beamR * 0.36F, sides, 1.5F, 0.0F, 1.0F, 0.0F);
      // brow, bez, trez, royal (fourth), sword (fifth), sixth - in the order a bull grows them
      float[][] spec = { // t, length, up-tilt (0 forward .. 1 straight up)
         {0.055F, 0.40F, 0.18F}, // brow
         {0.55F, 0.46F, 0.70F}, // royal
         {0.36F, 0.30F, 0.55F}, // trez
         {0.73F, 0.34F, 0.80F}, // sword
         {0.13F, 0.32F, 0.25F}, // bez
         {0.88F, 0.20F, 0.85F} // sixth
      };
      int n = Math.min(spec.length, pts - 1);
      for (int i = 0; i < n; i++) {
         float t = spec[i][0] + (r.nextFloat() - 0.5F) * 0.03F;
         float len = spec[i][1] * k * (0.9F + r.nextFloat() * 0.2F);
         if (var == 2 && i == 1) {
            len *= 1.35F; // dagger royal
         }
         if (var == 4 && i == 5) {
            len *= 1.4F; // whale-tail crown fork
         }
         float up = spec[i][2];
         float[] a = at(beam, t);
         float rb = beamR * (1.0F - 0.55F * t);
         float[] dir = norm(v(-s * 0.08F, up * 1.1F, -(1.0F - up) * 1.1F - 0.15F));
         float[] curl = norm(v(-s * 0.25F, 1.0F, 0.05F));
         float[] start = sub(a, mul(dir, rb * 0.7F));
         float[][] path = curve(new float[][]{start, add(a, mul(dir, len * 0.38F)), add(add(a, mul(dir, len * 0.75F)), mul(curl, len * 0.12F)),
            add(add(a, mul(dir, len * 0.92F)), mul(curl, len * 0.28F))}, Math.max(4, sides / 2 + 3));
         tube(m, path, rb * 0.72F, rb * 0.16F, Math.max(4, sides - 1), 1.3F, 0.3F, 1.0F, 0.17F * i);
      }
      for (int i = 0; i < abn; i++) {
         float t = 0.2F + r.nextFloat() * 0.6F;
         float[] a = at(beam, t);
         float rb = beamR * (1.0F - 0.55F * t);
         float[] dir = norm(v(s * (0.4F + r.nextFloat() * 0.5F), r.nextFloat() - 0.4F, r.nextFloat() - 0.5F));
         float len = (0.06F + r.nextFloat() * 0.1F) * k;
         float[][] path = curve(new float[][]{sub(a, mul(dir, rb * 0.6F)), add(a, mul(dir, len * 0.4F)), add(a, mul(dir, len * 0.75F)), add(a, mul(dir, len))}, 4);
         tube(m, path, rb * 0.55F, rb * 0.2F, Math.max(4, sides - 2), 1.2F, 0.35F, 1.0F, 0.5F);
      }
   }

   // ------------------------------------------------------------------------------------------------ moose

   static void mooseSide(Mesh m, Params p, float s, int pts, int abn, float lr, Random r, int sides) {
      float k = p.size * lr;
      float w = p.spread, h = p.height, mass = p.mass;
      int var = p.variant;
      if (var == 0) {
         w *= 1.15F;
      } else if (var == 1) {
         h *= 1.15F;
      } else if (var == 3) {
         mass *= 1.2F;
      }
      float ph = p.pedicle;
      float beamR = 0.034F * mass * (0.5F + 0.5F * Math.min(1.2F, p.size));
      if (p.size < 0.55F) {
         // a young bull: a short beam with a fork or two, no palm yet
         float[][] beam = curve(new float[][]{sv(s, ph, 0, 0), sv(s, ph + 0.10F * k, 0.06F * k, 0.0F), sv(s, ph + 0.22F * k, 0.16F * k, 0.04F * k),
            sv(s, ph + 0.28F * k, 0.30F * k, 0.02F * k)}, sides + 2);
         tube(m, beam, beamR, beamR * 0.3F, sides, 1.4F, 0.0F, 1.0F, 0.0F);
         for (int i = 0; i < Math.min(2, pts - 1); i++) {
            float t = i == 0 ? 0.55F : 0.3F;
            float[] a = at(beam, t);
            float[] dir = norm(i == 0 ? v(s * 0.2F, 0.8F, -0.5F) : v(s * 0.1F, 0.6F, 0.7F));
            float len = 0.14F * k;
            tube(m, curve(new float[][]{sub(a, mul(dir, beamR * 0.5F)), add(a, mul(dir, len * 0.4F)), add(a, mul(dir, len * 0.8F)), add(a, mul(dir, len))}, 4),
               beamR * 0.6F, beamR * 0.2F, Math.max(4, sides - 1), 1.25F, 0.3F, 1.0F, 0.3F);
         }
         return;
      }
      // beam: out to the side and a little up, then the palm opens out, up and back
      float[] root = sv(s, ph + 0.20F * k * w, 0.07F * k, 0.02F * k);
      float[][] beam = curve(new float[][]{sv(s, ph, 0, 0), sv(s, ph + 0.07F * k * w, 0.01F, 0.0F), sv(s, ph + 0.14F * k * w, 0.04F * k, 0.02F * k), root}, sides);
      tube(m, beam, beamR, beamR * 0.85F, sides, 1.45F, 0.0F, 0.35F, 0.0F);
      // palm plane: a = out and back, b = up (cupped toward the head)
      // the palm lies out to the side and back, tipped up about 35 degrees and dished upward
      float[] ua = norm(v(s * 1.0F, 0.22F, -0.12F));
      float[] ub = norm(v(s * 0.15F, 0.55F * h, 1.0F * (var == 4 ? 0.8F : 1.0F)));
      ub = norm(sub(ub, mul(ua, dot(ub, ua))));
      float[] nn = norm(cross(ua, ub));
      if (nn[1] < 0.0F) {
         nn = mul(nn, -1.0F);
      }
      float R = 0.40F * k * (var == 0 ? 1.12F : 1.0F);
      int rim = Math.max(3, Math.min(11, pts));
      float a0 = (float)Math.toRadians(-8.0), a1 = (float)Math.toRadians(var == 1 ? 100.0 : 92.0);
      if (var == 2) {
         // butterfly: the main palm is split by a cleft into two lobes
         palm(m, root, ua, ub, nn, R, a0, (a0 + a1) * 0.46F, Math.max(2, rim / 2), h, mass, sides, r, 0.0F);
         palm(m, root, ua, ub, nn, R * 0.9F, (a0 + a1) * 0.56F, a1, Math.max(2, rim - rim / 2), h, mass, sides, r, 0.4F);
      } else {
         palm(m, root, ua, ub, nn, R, a0, a1, rim, h, mass, sides, r, 0.0F);
      }
      // the brow palm: smaller, forward and up off the beam
      float[] broot = at(beam, 0.55F);
      float[] fa = norm(v(s * 0.55F, 0.30F, -1.0F));
      float[] fb = norm(sub(v(s * 1.0F, 0.5F, 0.0F), mul(fa, dot(v(s * 1.0F, 0.5F, 0.0F), fa))));
      float[] fn = norm(cross(fa, fb));
      if (fn[1] < 0.0F) {
         fn = mul(fn, -1.0F);
      }
      int bp = Math.max(2, Math.min(4, pts / 3 + 1));
      palm(m, broot, fa, fb, fn, R * 0.42F, (float)Math.toRadians(-5.0), (float)Math.toRadians(80.0), bp, 1.0F, mass * 0.8F, sides, r, 0.7F);
      for (int i = 0; i < abn; i++) {
         float t = r.nextFloat();
         float ang = a0 + (a1 - a0) * t;
         float[] dir = norm(add(mul(ua, (float)Math.cos(ang)), mul(ub, (float)Math.sin(ang))));
         float[] base = add(root, mul(dir, R * (0.55F + 0.3F * r.nextFloat())));
         float[] out = norm(add(mul(nn, r.nextBoolean() ? 1.0F : -1.0F), mul(dir, 0.5F)));
         float len = (0.04F + 0.05F * r.nextFloat()) * k;
         tube(m, curve(new float[][]{base, add(base, mul(out, len * 0.35F)), add(base, mul(out, len * 0.7F)), add(base, mul(out, len))}, 3),
            beamR * 0.35F, beamR * 0.12F, Math.max(4, sides - 2), 1.0F, 0.5F, 1.0F, 0.6F);
      }
   }

   /**
    * A palm: a thick, slightly cupped fan from {@code root} between angles a0..a1 in the (ua, ub) plane, radius about R,
    * its rim scalloped into {@code points} tines.
    */
   static void palm(Mesh m, float[] root, float[] ua, float[] ub, float[] nn, float R, float a0, float a1, int points, float h, float mass, int sides,
      Random r, float shadeBase) {
      int J = Math.max(10, points * 4); // around
      int K = 5; // out
      float thick = 0.022F * mass * Math.max(0.6F, R / 0.4F);
      int[][] top = new int[K + 1][J + 1], bot = new int[K + 1][J + 1];
      float[][][] P = new float[K + 1][J + 1][];
      for (int j = 0; j <= J; j++) {
         float f = j / (float)J;
         float ang = a0 + (a1 - a0) * f;
         // rim radius: fuller in the middle, scalloped between the points
         float scal = 0.93F + 0.07F * (float)Math.cos(f * points * Math.PI * 2.0);
         float rr = R * (0.78F + 0.22F * (float)Math.sin(Math.PI * f)) * (ang > 0.6F ? h : 1.0F);
         float[] dir = add(mul(ua, (float)Math.cos(ang)), mul(ub, (float)Math.sin(ang)));
         // the palm grows from a short base line along the beam's end, not from one point: broad from the start
         float[] base = add(root, mul(ub, (f - 0.5F) * 0.22F * R));
         for (int k = 0; k <= K; k++) {
            float q = k / (float)K;
            // scallops only at the rim, so the face of the palm stays one smooth sheet
            float rim = q > 0.7F ? 1.0F - (1.0F - scal) * (q - 0.7F) / 0.3F : 1.0F;
            float rad = rr * (0.06F + 0.94F * q) * rim;
            float cup = 0.10F * R * q * q; // dished upward
            P[k][j] = add(add(base, mul(dir, rad)), mul(nn, cup));
         }
      }
      // smooth normals from the grid
      for (int k = 0; k <= K; k++) {
         for (int j = 0; j <= J; j++) {
            float[] du = sub(P[k][Math.min(J, j + 1)], P[k][Math.max(0, j - 1)]);
            float[] dv = sub(P[Math.min(K, k + 1)][j], P[Math.max(0, k - 1)][j]);
            float[] n = norm(cross(dv, du));
            if (dot(n, nn) < 0.0F) {
               n = mul(n, -1.0F);
            }
            float q = k / (float)K;
            float t = thick * (1.0F - 0.55F * q);
            float sh = shadeBase + (1.0F - shadeBase) * q * 0.85F;
            float[] pt = add(P[k][j], mul(n, t * 0.5F)), pb = sub(P[k][j], mul(n, t * 0.5F));
            top[k][j] = m.vertex(pt[0], pt[1], pt[2], n[0], n[1], n[2], 0.3F + 0.25F * j / J, 0.25F + 0.55F * q, sh);
            bot[k][j] = m.vertex(pb[0], pb[1], pb[2], -n[0], -n[1], -n[2], 0.3F + 0.25F * j / J, 0.25F + 0.55F * q, sh);
         }
      }
      for (int k = 0; k < K; k++) {
         for (int j = 0; j < J; j++) {
            m.tri(top[k][j], top[k + 1][j], top[k][j + 1]);
            m.tri(top[k][j + 1], top[k + 1][j], top[k + 1][j + 1]);
            m.tri(bot[k][j], bot[k][j + 1], bot[k + 1][j]);
            m.tri(bot[k][j + 1], bot[k + 1][j + 1], bot[k + 1][j]);
         }
      }
      // rim and the two side edges: close the thickness
      for (int j = 0; j < J; j++) {
         edge(m, P[K][j], P[K][j + 1], top[K][j], top[K][j + 1], bot[K][j], bot[K][j + 1], root);
      }
      for (int k = 0; k < K; k++) {
         edge(m, P[k][0], P[k + 1][0], top[k][0], top[k + 1][0], bot[k][0], bot[k + 1][0], root);
         edge(m, P[k][J], P[k + 1][J], top[k][J], top[k + 1][J], bot[k][J], bot[k + 1][J], root);
      }
      // tines off the rim at the scallop peaks, pointing outward along the palm, tipped a little up off its face
      for (int i = 0; i < points; i++) {
         float f = (i + 0.5F) / points;
         int j = Math.round(f * J);
         float[] tipBase = P[K][j];
         float[] out = norm(sub(P[K][j], P[K - 1][j]));
         float[] dir = norm(add(out, mul(nn, 0.25F)));
         float len = (0.06F + 0.06F * r.nextFloat()) * Math.max(0.6F, R / 0.4F) * (f > 0.4F && f < 0.8F ? 1.2F : 1.0F);
         float[] start = sub(tipBase, mul(out, thick * 1.2F));
         tube(m, curve(new float[][]{start, add(tipBase, mul(dir, len * 0.35F)), add(tipBase, mul(dir, len * 0.72F)), add(add(tipBase, mul(dir, len)), mul(nn, len * 0.08F))}, 4),
            thick * 0.75F, thick * 0.18F, Math.max(4, sides - 2), 1.15F, 0.7F, 1.0F, f);
      }
   }

   /** a strip closing the palm's thickness between two edge points; its normal faces away from the root */
   static void edge(Mesh m, float[] p0, float[] p1, int t0, int t1, int b0, int b1, float[] root) {
      float[] out = norm(sub(lerp(p0, p1, 0.5F), root));
      int a = m.vertex(m.pos[t0 * 3], m.pos[t0 * 3 + 1], m.pos[t0 * 3 + 2], out[0], out[1], out[2], m.uv[t0 * 2], 0.9F, m.shade[t0]);
      int b = m.vertex(m.pos[t1 * 3], m.pos[t1 * 3 + 1], m.pos[t1 * 3 + 2], out[0], out[1], out[2], m.uv[t1 * 2], 0.9F, m.shade[t1]);
      int c = m.vertex(m.pos[b0 * 3], m.pos[b0 * 3 + 1], m.pos[b0 * 3 + 2], out[0], out[1], out[2], m.uv[b0 * 2], 0.9F, m.shade[b0]);
      int d = m.vertex(m.pos[b1 * 3], m.pos[b1 * 3 + 1], m.pos[b1 * 3 + 2], out[0], out[1], out[2], m.uv[b1 * 2], 0.9F, m.shade[b1]);
      m.tri(a, c, b);
      m.tri(b, c, d);
   }
}
