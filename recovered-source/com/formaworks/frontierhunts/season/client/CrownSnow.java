package com.formaworks.frontierhunts.season.client;

import java.util.ArrayList;
import java.util.List;

/**
 * [1.2.2] Snow lying on a leaves block (Minecraft-style trees): not a flat slab but a soft pillow of snow that runs on
 * across the neighbouring snowy crowns, rounds over the open edges and hangs a little way down the sides in a wavy lip,
 * as wet snow sits on a bough. Drawn in the snow's own block from the Frontier snow texture.
 */
public final class CrownSnow {
   private CrownSnow() {
   }

   /** what the pillow needs to know about its neighbours */
   public interface Probe {
      /** a crown (leaves) block with snow on it at this column, one level with this one */
      boolean snowyCrown(int x, int y, int z);

      /** anything solid or leafy beside the snow (no lip hangs into it) */
      boolean filled(int x, int y, int z);
   }

   private static float hash(int x, int y, int z, int salt) {
      int h = x * 0x27D4EB2D ^ y * 0x165667B1 ^ z * 0x9E3779B9 ^ salt * 0x85EBCA6B;
      h ^= h >>> 15;
      h *= 0x2C1B3C6D;
      h ^= h >>> 12;
      return (h >>> 8) / (float)(1 << 24);
   }

   private static float smooth(float t) {
      t = Math.max(0.0F, Math.min(1.0F, t));
      return t * t * (3.0F - 2.0F * t);
   }

   /** the pillow's height at a point of the block (0..1 each way, beyond for the overhang) */
   private static float height(float u, float w, float t0, boolean[] open, int x, int y, int z) {
      // distance to each open edge: west (u=0), east (u=1), north (w=0), south (w=1)
      float k = 1.0F;
      if (open[0]) k = Math.min(k, smooth(u / 0.4F));
      if (open[1]) k = Math.min(k, smooth((1.0F - u) / 0.4F));
      if (open[2]) k = Math.min(k, smooth(w / 0.4F));
      if (open[3]) k = Math.min(k, smooth((1.0F - w) / 0.4F));
      // gentle lumps (in world space, so they run on across blocks)
      float wx = x + u, wz = z + w;
      float lump = (float)(Math.sin(wx * 2.9 + wz * 1.3) * Math.sin(wz * 2.3 - wx * 0.7)) * 0.018F;
      return t0 * (0.4F + 0.6F * k) + lump;
   }

   /**
    * the quads of the snow on the crown below (x, y, z) is the snow block; the leaves are at y - 1
    */
   public static List<SnowField.Quad> build(Probe p, int x, int y, int z, int layers) {
      List<SnowField.Quad> out = new ArrayList<>();
      // west, east, north, south
      int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
      boolean[] open = new boolean[4];
      for (int d = 0; d < 4; d++) {
         int nx = x + dirs[d][0], nz = z + dirs[d][1];
         open[d] = !p.snowyCrown(nx, y, nz);
      }
      float t0 = 0.075F + 0.03F * Math.min(4, layers);
      float over = 0.07F;
      int n = 4;
      float u0 = open[0] ? -over : 0.0F, u1 = open[1] ? 1.0F + over : 1.0F, w0 = open[2] ? -over : 0.0F, w1 = open[3] ? 1.0F + over : 1.0F;
      float[][] gy = new float[n + 1][n + 1];
      float[] gu = new float[n + 1], gw = new float[n + 1];
      for (int i = 0; i <= n; i++) {
         gu[i] = u0 + (u1 - u0) * i / n;
         gw[i] = w0 + (w1 - w0) * i / n;
      }
      for (int i = 0; i <= n; i++) {
         for (int j = 0; j <= n; j++) {
            float u = gu[i], w = gw[j];
            float h = height(Math.max(0.0F, Math.min(1.0F, u)), Math.max(0.0F, Math.min(1.0F, w)), t0, open, x, y, z);
            // over the edge it rounds down
            float beyond = Math.max(Math.max(-u, u - 1.0F), Math.max(-w, w - 1.0F));
            if (beyond > 0.0F) {
               h -= beyond / over * (h * 0.6F + 0.03F);
            }
            gy[i][j] = h;
         }
      }
      // the top, its normals from the height field
      for (int i = 0; i < n; i++) {
         for (int j = 0; j < n; j++) {
            SnowField.Quad q = SnowField.quad(); // [perf3] pooled
            q.face = 0;
            for (int k = 0; k < 4; k++) {
               int a = k >= 2 ? i + 1 : i, b = k == 1 || k == 2 ? j + 1 : j; // [perf3] (was an int[4][2] per quad)
               q.x[k] = gu[a];
               q.y[k] = gy[a][b];
               q.z[k] = gw[b];
               float dhu = (gy[Math.min(n, a + 1)][b] - gy[Math.max(0, a - 1)][b]) / Math.max(1.0E-4F, gu[Math.min(n, a + 1)] - gu[Math.max(0, a - 1)]);
               float dhw = (gy[a][Math.min(n, b + 1)] - gy[a][Math.max(0, b - 1)]) / Math.max(1.0E-4F, gw[Math.min(n, b + 1)] - gw[Math.max(0, b - 1)]);
               float len = (float)Math.sqrt(dhu * dhu + 1.0F + dhw * dhw);
               q.nx[k] = -dhu / len;
               q.ny[k] = 1.0F / len;
               q.nz[k] = -dhw / len;
               uv(q, k, x + q.x[k], z + q.z[k], x, z);
               float g = 0.94F + 0.06F * q.ny[k];
               q.rgb[k] = rgb(g * 0.985F, g * 0.99F, Math.min(1.0F, g * 1.01F));
            }
            out.add(q);
         }
      }
      // a wavy lip hanging down each open side
      for (int d = 0; d < 4; d++) {
         if (!open[d] || p.filled(x + dirs[d][0], y - 1, z + dirs[d][1])) {
            continue;
         }
         for (int s = 0; s < n; s++) {
            SnowField.Quad q = SnowField.quad(); // [perf3] pooled
            q.face = d == 0 ? 4 : d == 1 ? 5 : d == 2 ? 2 : 3;
            // the edge points of the top along this side, and how far the lip hangs at each
            float[][] pts = new float[2][];
            for (int e = 0; e < 2; e++) {
               int idx = s + e;
               float u, w, top;
               if (d < 2) {
                  int i = d == 0 ? 0 : n;
                  u = gu[i];
                  w = gw[idx];
                  top = gy[i][idx];
               } else {
                  int j = d == 2 ? 0 : n;
                  u = gu[idx];
                  w = gw[j];
                  top = gy[idx][j];
               }
               float along = d < 2 ? z + w : x + u;
               float hang = 0.06F + 0.2F * hash(Math.round(along * 4.0F), y, d, 7) * (0.5F + 0.5F * Math.min(4, layers) / 4.0F);
               pts[e] = new float[]{u, top, w, top - hang - 0.02F};
            }
            // corners: top a, top b, bottom b, bottom a, wound to face outward
            float[][] v = {{pts[0][0], pts[0][1], pts[0][2]}, {pts[1][0], pts[1][1], pts[1][2]}, {pts[1][0], pts[1][3], pts[1][2]},
               {pts[0][0], pts[0][3], pts[0][2]}};
            float ox = dirs[d][0], oz = dirs[d][1];
            float ax = v[1][0] - v[0][0], ay = v[1][1] - v[0][1], az = v[1][2] - v[0][2];
            float bx = v[3][0] - v[0][0], by = v[3][1] - v[0][1], bz = v[3][2] - v[0][2];
            float cx = ay * bz - az * by, cz = ax * by - ay * bx;
            if (cx * ox + cz * oz < 0.0F) {
               float[] t = v[1];
               v[1] = v[3];
               v[3] = t;
            }
            for (int k = 0; k < 4; k++) {
               q.x[k] = v[k][0];
               q.y[k] = v[k][1];
               q.z[k] = v[k][2];
               q.nx[k] = ox;
               q.ny[k] = 0.15F;
               q.nz[k] = oz;
               uv(q, k, (d < 2 ? z + q.z[k] : x + q.x[k]), y + q.y[k], d < 2 ? z : x, y);
               q.rgb[k] = rgb(0.9F, 0.92F, 0.96F);
            }
            out.add(q);
         }
      }
      return out;
   }

   private static void uv(SnowField.Quad q, int k, float a, float b, int blockA, int blockB) {
      // the Frontier snow texture tiles every 4 blocks; one tile per block (the overhang clamps to its edge)
      double ta = Math.floor(blockA / 4.0) * 4.0, tb = Math.floor(blockB / 4.0) * 4.0;
      q.u[k] = (float)Math.max(0.001, Math.min(0.999, (a - ta) / 4.0));
      q.v[k] = (float)Math.max(0.001, Math.min(0.999, (b - tb) / 4.0));
   }

   private static int rgb(float r, float g, float b) {
      return Math.round(Math.min(1.0F, r) * 255) << 16 | Math.round(Math.min(1.0F, g) * 255) << 8 | Math.round(Math.min(1.0F, b) * 255);
   }
}
