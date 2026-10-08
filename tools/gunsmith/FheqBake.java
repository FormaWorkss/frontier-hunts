import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

/**
 * [gunsmith] Ambient-occlusion bake for .fheq meshes (see bake.py for the model): per vertex, cosine-weighted
 * hemisphere rays (other parts included, rest pose), hits within RANGE weighted by (1 - t / RANGE);
 * tint *= max(FLOOR, 1 - STRENGTH * occlusion). Geometry, normals, UVs, indices and part ids are copied verbatim.
 *
 * usage: java FheqBake.java in.fheq out.fheq strength floor [range rays]
 */
public final class FheqBake {
   record Part(int id, float[] v, int[] c, int[] idx) {
   }

   public static void main(String[] a) throws Exception {
      List<Part> parts = read(Path.of(a[0]));
      double strength = Double.parseDouble(a[2]);
      double floor = Double.parseDouble(a[3]);
      double range = a.length > 4 ? Double.parseDouble(a[4]) : 0.045;
      int rays = a.length > 5 ? Integer.parseInt(a[5]) : 64;
      // all triangles
      int nt = 0;
      for (Part p : parts) {
         nt += p.idx.length / 3;
      }
      float[] T = new float[nt * 9];
      int k = 0;
      for (Part p : parts) {
         for (int i = 0; i < p.idx.length; i++) {
            int o = p.idx[i] * 8;
            T[k++] = p.v[o];
            T[k++] = p.v[o + 1];
            T[k++] = p.v[o + 2];
         }
      }
      Grid grid = new Grid(T, nt, 0.004);
      double[][] H = hemisphere(rays);
      List<Part> out = new ArrayList<>();
      for (Part p : parts) {
         int n = p.c.length;
         int[] nc = new int[n];
         IntStream.range(0, n).parallel().forEach(i -> {
            int o = i * 8;
            double px = p.v[o], py = p.v[o + 1], pz = p.v[o + 2];
            double nx = p.v[o + 3], ny = p.v[o + 4], nz = p.v[o + 5];
            double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (l < 1e-9) {
               nc[i] = p.c[i];
               return;
            }
            nx /= l;
            ny /= l;
            nz /= l;
            double[] t = Math.abs(nx) < 0.9 ? cross(nx, ny, nz, 1, 0, 0) : cross(nx, ny, nz, 0, 1, 0);
            double tl = Math.sqrt(t[0] * t[0] + t[1] * t[1] + t[2] * t[2]);
            t[0] /= tl;
            t[1] /= tl;
            t[2] /= tl;
            double[] b = cross(nx, ny, nz, t[0], t[1], t[2]);
            double ox = px + nx * 4e-4, oy = py + ny * 4e-4, oz = pz + nz * 4e-4;
            double occ = 0;
            for (double[] h : H) {
               double dx = h[0] * t[0] + h[1] * b[0] + h[2] * nx;
               double dy = h[0] * t[1] + h[1] * b[1] + h[2] * ny;
               double dz = h[0] * t[2] + h[1] * b[2] + h[2] * nz;
               double best = grid.trace(T, ox, oy, oz, dx, dy, dz, range);
               if (best < range) {
                  occ += 1 - best / range;
               }
            }
            occ /= H.length;
            double f = Math.max(floor, 1 - strength * occ);
            int c = p.c[i];
            int r = (int)Math.round(((c >> 16) & 255) * f), g = (int)Math.round(((c >> 8) & 255) * f), bb = (int)Math.round((c & 255) * f);
            nc[i] = r << 16 | g << 8 | bb;
         });
         out.add(new Part(p.id, p.v, nc, p.idx));
      }
      write(Path.of(a[1]), out);
   }

   static double[] cross(double ax, double ay, double az, double bx, double by, double bz) {
      return new double[]{ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx};
   }

   static double intersect(float[] T, int ti, double ox, double oy, double oz, double dx, double dy, double dz, double tmax) {
      int o = ti * 9;
      double v0x = T[o], v0y = T[o + 1], v0z = T[o + 2];
      double e1x = T[o + 3] - v0x, e1y = T[o + 4] - v0y, e1z = T[o + 5] - v0z;
      double e2x = T[o + 6] - v0x, e2y = T[o + 7] - v0y, e2z = T[o + 8] - v0z;
      double px = dy * e2z - dz * e2y, py = dz * e2x - dx * e2z, pz = dx * e2y - dy * e2x;
      double det = e1x * px + e1y * py + e1z * pz;
      if (Math.abs(det) < 1e-14) {
         return Double.MAX_VALUE;
      }
      double inv = 1 / det;
      double tx = ox - v0x, ty = oy - v0y, tz = oz - v0z;
      double u = (tx * px + ty * py + tz * pz) * inv;
      if (u < 0 || u > 1) {
         return Double.MAX_VALUE;
      }
      double qx = ty * e1z - tz * e1y, qy = tz * e1x - tx * e1z, qz = tx * e1y - ty * e1x;
      double v = (dx * qx + dy * qy + dz * qz) * inv;
      if (v < 0 || u + v > 1) {
         return Double.MAX_VALUE;
      }
      double t = (e2x * qx + e2y * qy + e2z * qz) * inv;
      return t > 1e-5 && t < tmax ? t : Double.MAX_VALUE;
   }

   static double[][] hemisphere(int n) {
      Random r = new Random(7);
      int s = (int)Math.ceil(Math.sqrt(n));
      double[][] out = new double[s * s][];
      for (int i = 0; i < s * s; i++) {
         double u1 = (i / s + r.nextDouble()) / s, u2 = (i % s + r.nextDouble()) / s;
         double rr = Math.sqrt(u1), th = 2 * Math.PI * u2;
         out[i] = new double[]{rr * Math.cos(th), rr * Math.sin(th), Math.sqrt(Math.max(0, 1 - u1))};
      }
      return out;
   }

   /** Uniform grid of triangle boxes; near() returns triangles whose box is within one cell (= range) of the point. */
   static final class Grid {
      final double cell;
      final java.util.HashMap<Long, List<Integer>> map = new java.util.HashMap<>();

      Grid(float[] T, int nt, double range) {
         this.cell = range;
         for (int t = 0; t < nt; t++) {
            double[] lo = {1e9, 1e9, 1e9}, hi = {-1e9, -1e9, -1e9};
            for (int j = 0; j < 3; j++) {
               for (int d = 0; d < 3; d++) {
                  lo[d] = Math.min(lo[d], T[t * 9 + j * 3 + d]);
                  hi[d] = Math.max(hi[d], T[t * 9 + j * 3 + d]);
               }
            }
            for (long x = cellOf(lo[0]); x <= cellOf(hi[0]); x++) {
               for (long y = cellOf(lo[1]); y <= cellOf(hi[1]); y++) {
                  for (long z = cellOf(lo[2]); z <= cellOf(hi[2]); z++) {
                     this.map.computeIfAbsent(key(x, y, z), kk -> new ArrayList<>()).add(t);
                  }
               }
            }
         }
      }

      long cellOf(double v) {
         return (long)Math.floor(v / this.cell);
      }

      static long key(long x, long y, long z) {
         return (x & 0x1FFFFF) << 42 | (y & 0x1FFFFF) << 21 | (z & 0x1FFFFF);
      }

      /** 3D-DDA through the cells the ray crosses, nearest hit below tmax (or tmax). */
      double trace(float[] T, double ox, double oy, double oz, double dx, double dy, double dz, double tmax) {
         long cx = cellOf(ox), cy = cellOf(oy), cz = cellOf(oz);
         int sx = dx > 0 ? 1 : -1, sy = dy > 0 ? 1 : -1, sz = dz > 0 ? 1 : -1;
         double tdx = Math.abs(dx) < 1e-12 ? Double.MAX_VALUE : this.cell / Math.abs(dx);
         double tdy = Math.abs(dy) < 1e-12 ? Double.MAX_VALUE : this.cell / Math.abs(dy);
         double tdz = Math.abs(dz) < 1e-12 ? Double.MAX_VALUE : this.cell / Math.abs(dz);
         double tx = Math.abs(dx) < 1e-12 ? Double.MAX_VALUE : ((sx > 0 ? (cx + 1) * this.cell - ox : ox - cx * this.cell) / Math.abs(dx));
         double ty = Math.abs(dy) < 1e-12 ? Double.MAX_VALUE : ((sy > 0 ? (cy + 1) * this.cell - oy : oy - cy * this.cell) / Math.abs(dy));
         double tz = Math.abs(dz) < 1e-12 ? Double.MAX_VALUE : ((sz > 0 ? (cz + 1) * this.cell - oz : oz - cz * this.cell) / Math.abs(dz));
         double best = tmax;
         double tcur = 0;
         while (tcur < best) {
            List<Integer> l = this.map.get(key(cx, cy, cz));
            if (l != null) {
               for (int ti : l) {
                  double hit = intersect(T, ti, ox, oy, oz, dx, dy, dz, best);
                  if (hit < best) {
                     best = hit;
                  }
               }
            }
            if (tx <= ty && tx <= tz) {
               tcur = tx;
               tx += tdx;
               cx += sx;
            } else if (ty <= tz) {
               tcur = ty;
               ty += tdy;
               cy += sy;
            } else {
               tcur = tz;
               tz += tdz;
               cz += sz;
            }
         }
         return best;
      }

      int[] near(double px, double py, double pz) {
         java.util.TreeSet<Integer> s = new java.util.TreeSet<>();
         long cx = cellOf(px), cy = cellOf(py), cz = cellOf(pz);
         for (long x = cx - 1; x <= cx + 1; x++) {
            for (long y = cy - 1; y <= cy + 1; y++) {
               for (long z = cz - 1; z <= cz + 1; z++) {
                  List<Integer> l = this.map.get(key(x, y, z));
                  if (l != null) {
                     s.addAll(l);
                  }
               }
            }
         }
         return s.stream().mapToInt(Integer::intValue).toArray();
      }
   }

   static List<Part> read(Path p) throws Exception {
      try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(Files.readAllBytes(p)))) {
         if (in.readInt() != 1179141457 || in.readInt() != 1) {
            throw new IllegalStateException("header " + p);
         }
         int n = in.readInt();
         List<Part> out = new ArrayList<>();
         for (int q = 0; q < n; q++) {
            int id = in.readInt(), nv = in.readInt(), nt = in.readInt();
            float[] v = new float[nv * 8];
            int[] c = new int[nv];
            for (int i = 0; i < nv; i++) {
               for (int j = 0; j < 8; j++) {
                  v[i * 8 + j] = in.readFloat();
               }
               c[i] = in.readInt();
            }
            int[] idx = new int[nt * 3];
            for (int i = 0; i < idx.length; i++) {
               idx[i] = in.readInt();
            }
            out.add(new Part(id, v, c, idx));
         }
         return out;
      }
   }

   static void write(Path p, List<Part> parts) throws Exception {
      ByteArrayOutputStream bo = new ByteArrayOutputStream();
      DataOutputStream o = new DataOutputStream(bo);
      o.writeInt(1179141457);
      o.writeInt(1);
      o.writeInt(parts.size());
      for (Part q : parts) {
         o.writeInt(q.id);
         o.writeInt(q.c.length);
         o.writeInt(q.idx.length / 3);
         for (int i = 0; i < q.c.length; i++) {
            for (int j = 0; j < 8; j++) {
               o.writeFloat(q.v[i * 8 + j]);
            }
            o.writeInt(q.c[i]);
         }
         for (int i : q.idx) {
            o.writeInt(i);
         }
      }
      Files.write(p, bo.toByteArray());
   }
}
