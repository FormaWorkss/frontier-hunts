package com.formaworks.frontierhunts.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import java.util.List;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Vector3f;

/** Small procedural-mesh toolkit for sculpted equipment (2x2 material sheets, baked vertex colours). */
final class MeshKit {
   private MeshKit() {
   }

   static final class Mesh {
      final float[] data;
      final int[] colors;

      Mesh(float[] data, int[] colors) {
         this.data = data;
         this.colors = colors;
      }

      void draw(PoseStack pose, VertexConsumer out, int light) {
         Pose p = pose.last();
         float[] d = this.data;
         for (int i = 0, v = 0; v < this.colors.length; i += 8, v++) {
            out.addVertex(p.pose(), d[i], d[i + 1], d[i + 2])
               .setColor(0xFF000000 | this.colors[v])
               .setUv(d[i + 3], d[i + 4])
               .setOverlay(OverlayTexture.NO_OVERLAY)
               .setLight(light)
               .setNormal(p, d[i + 5], d[i + 6], d[i + 7]);
         }
      }

      /** Raw vertices for tools: {x, y, z, u, v, nx, ny, nz} per vertex, four per quad. */
      public float[] data() {
         return this.data;
      }

      public int[] colors() {
         return this.colors;
      }
   }

   static final class Builder {
      private float[] data = new float[8 * 4096];
      private int[] colors = new int[4096];
      private int count;
      /** Optional affine transform {r00,r01,r02,tx, r10,r11,r12,ty, r20,r21,r22,tz} applied to positions (rotation part to normals). */
      private double[] xf;

      void transform(double[] m) {
         this.xf = m;
      }

      /** Frame with origin o and orthonormal axes ex, ey, ez (local x, y, z map onto them). */
      static double[] frame(double[] o, double[] ex, double[] ey, double[] ez) {
         return new double[]{ex[0], ey[0], ez[0], o[0], ex[1], ey[1], ez[1], o[1], ex[2], ey[2], ez[2], o[2]};
      }

      Mesh mesh() {
         float[] d = new float[this.count * 8];
         int[] c = new int[this.count];
         System.arraycopy(this.data, 0, d, 0, d.length);
         System.arraycopy(this.colors, 0, c, 0, c.length);
         return new Mesh(d, c);
      }

      void put(int tile, int color, double x, double y, double z, double u, double v, double nx, double ny, double nz) {
         if (this.count == this.colors.length) {
            float[] nd = new float[this.data.length * 2];
            int[] nc = new int[this.colors.length * 2];
            System.arraycopy(this.data, 0, nd, 0, this.data.length);
            System.arraycopy(this.colors, 0, nc, 0, this.colors.length);
            this.data = nd;
            this.colors = nc;
         }

         if (this.xf != null) {
            double[] m = this.xf;
            double px = m[0] * x + m[1] * y + m[2] * z + m[3];
            double py = m[4] * x + m[5] * y + m[6] * z + m[7];
            double pz = m[8] * x + m[9] * y + m[10] * z + m[11];
            double qx = m[0] * nx + m[1] * ny + m[2] * nz;
            double qy = m[4] * nx + m[5] * ny + m[6] * nz;
            double qz = m[8] * nx + m[9] * ny + m[10] * nz;
            x = px;
            y = py;
            z = pz;
            nx = qx;
            ny = qy;
            nz = qz;
         }

         double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
         if (len < 1.0E-9) {
            nx = 0.0;
            ny = 1.0;
            nz = 0.0;
            len = 1.0;
         }

         int o = this.count * 8;
         this.data[o] = (float)x;
         this.data[o + 1] = (float)y;
         this.data[o + 2] = (float)z;
         this.data[o + 3] = (float)((tile % 2) * 0.5 + 0.008 + Math.clamp(u, 0.0, 1.0) * 0.484);
         this.data[o + 4] = (float)((tile / 2) * 0.5 + 0.008 + Math.clamp(v, 0.0, 1.0) * 0.484);
         this.data[o + 5] = (float)(nx / len);
         this.data[o + 6] = (float)(ny / len);
         this.data[o + 7] = (float)(nz / len);
         this.colors[this.count] = color & 0xFFFFFF;
         this.count++;
      }

      /** Flat quad; the geometric normal is flipped to agree with the hint direction. */
      void quad(int tile, int color, double[] a, double[] b, double[] c, double[] d, double hx, double hy, double hz) {
         Vector3f n = new Vector3f((float)(b[0] - a[0]), (float)(b[1] - a[1]), (float)(b[2] - a[2]))
            .cross((float)(c[0] - a[0]), (float)(c[1] - a[1]), (float)(c[2] - a[2]));
         if (n.lengthSquared() < 1.0E-16F) {
            n.set((float)(c[0] - a[0]), (float)(c[1] - a[1]), (float)(c[2] - a[2])).cross((float)(d[0] - a[0]), (float)(d[1] - a[1]), (float)(d[2] - a[2]));
         }

         if (n.lengthSquared() < 1.0E-16F) {
            n.set((float)hx, (float)hy, (float)hz);
         }

         if (n.dot((float)hx, (float)hy, (float)hz) < 0.0F) {
            n.negate();
         }

         for (double[] p : new double[][]{a, b, c, d}) {
            this.put(tile, color, p[0], p[1], p[2], p.length > 3 ? p[3] : 0.5, p.length > 4 ? p[4] : 0.5, n.x, n.y, n.z);
         }
      }

      /** Smooth quad: each vertex {x, y, z, u, v, nx, ny, nz}. */
      void quadSmooth(int tile, int color, double[] a, double[] b, double[] c, double[] d) {
         for (double[] p : new double[][]{a, b, c, d}) {
            this.put(tile, color, p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7]);
         }
      }

      /** Axis-aligned box. */
      void box(double x0, double y0, double z0, double x1, double y1, double z1, int tile, int color) {
         double[] p000 = {x0, y0, z0, 0, 0}, p100 = {x1, y0, z0, 1, 0}, p110 = {x1, y1, z0, 1, 1}, p010 = {x0, y1, z0, 0, 1};
         double[] p001 = {x0, y0, z1, 0, 0}, p101 = {x1, y0, z1, 1, 0}, p111 = {x1, y1, z1, 1, 1}, p011 = {x0, y1, z1, 0, 1};
         this.quad(tile, shade(color, 0.86), p000, p100, p110, p010, 0, 0, -1);
         this.quad(tile, shade(color, 0.92), p001, p011, p111, p101, 0, 0, 1);
         this.quad(tile, shade(color, 0.9), p000, p010, p011, p001, -1, 0, 0);
         this.quad(tile, shade(color, 0.9), p100, p101, p111, p110, 1, 0, 0);
         this.quad(tile, color, p010, p110, p111, p011, 0, 1, 0);
         this.quad(tile, shade(color, 0.75), p000, p001, p101, p100, 0, -1, 0);
      }

      /**
       * Prism along Z: an XY section (counter-clockwise, convex or star-shaped around its centroid) extruded from z0 to z1
       * with the front section scaled by frontScale (a bevelled nose) and both ends capped.
       */
      void prismZ(double[][] section, double z0, double z1, double frontScale, int tile, int color, boolean caps) {
         double cx = 0.0;
         double cy = 0.0;
         for (double[] p : section) {
            cx += p[0];
            cy += p[1];
         }

         cx /= section.length;
         cy /= section.length;
         int n = section.length;
         double perimeter = 0.0;
         for (int i = 0; i < n; i++) {
            double[] a = section[i];
            double[] b = section[(i + 1) % n];
            perimeter += Math.hypot(b[0] - a[0], b[1] - a[1]);
         }

         double along = 0.0;
         for (int i = 0; i < n; i++) {
            double[] a = section[i];
            double[] b = section[(i + 1) % n];
            double seg = Math.hypot(b[0] - a[0], b[1] - a[1]);
            double nx = b[1] - a[1];
            double ny = -(b[0] - a[0]);
            double mx = (a[0] + b[0]) * 0.5 - cx;
            double my = (a[1] + b[1]) * 0.5 - cy;
            if (nx * mx + ny * my < 0.0) {
               nx = -nx;
               ny = -ny;
            }

            double v0 = along / perimeter;
            double v1 = (along + seg) / perimeter;
            along += seg;
            double[] fa = {cx + (a[0] - cx) * frontScale, cy + (a[1] - cy) * frontScale, z0, 0, v0};
            double[] fb = {cx + (b[0] - cx) * frontScale, cy + (b[1] - cy) * frontScale, z0, 0, v1};
            double[] ra = {a[0], a[1], z1, 1, v0};
            double[] rb = {b[0], b[1], z1, 1, v1};
            double l = Math.hypot(nx, ny);
            int col = shade(color, lightFor(nx / l, ny / l, 0.0));
            this.quad(tile, col, fa, fb, rb, ra, nx, ny, 0.0);
         }

         if (caps) {
            for (int end = 0; end < 2; end++) {
               double z = end == 0 ? z0 : z1;
               double s = end == 0 ? frontScale : 1.0;
               for (int i = 0; i < n; i++) {
                  double[] a = section[i];
                  double[] b = section[(i + 1) % n];
                  double[] pa = {cx + (a[0] - cx) * s, cy + (a[1] - cy) * s, z, 0.5 + (a[0] - cx) * 8, 0.5 + (a[1] - cy) * 8};
                  double[] pb = {cx + (b[0] - cx) * s, cy + (b[1] - cy) * s, z, 0.5 + (b[0] - cx) * 8, 0.5 + (b[1] - cy) * 8};
                  double[] pc = {cx, cy, z, 0.5, 0.5};
                  double dir = (end == 0) == (z0 < z1) ? -1 : 1;
                  this.quad(tile, shade(color, dir < 0 ? 0.8 : 0.9), pc, pa, pb, pc, 0, 0, dir);
               }
            }
         }
      }

      /** Rectangular-section tube along a polyline in the YZ plane (x centred), e.g. a trigger guard. */
      void tubeYZ(List<double[]> path, double halfX, double halfT, int tile, int color) {
         int n = path.size();
         double[][] left = new double[n][];
         double[][] right = new double[n][];
         for (int i = 0; i < n; i++) {
            double[] prev = path.get(Math.max(0, i - 1));
            double[] next = path.get(Math.min(n - 1, i + 1));
            double dy = next[0] - prev[0];
            double dz = next[1] - prev[1];
            double l = Math.max(1.0E-9, Math.hypot(dy, dz));
            double ny = -dz / l;
            double nz = dy / l;
            double[] p = path.get(i);
            left[i] = new double[]{p[0] + ny * halfT, p[1] + nz * halfT};
            right[i] = new double[]{p[0] - ny * halfT, p[1] - nz * halfT};
         }

         for (int i = 0; i < n - 1; i++) {
            double u0 = (double)i / (n - 1);
            double u1 = (double)(i + 1) / (n - 1);
            double[][] sides = {left[i], left[i + 1], right[i], right[i + 1]};
            double[] c = path.get(i);
            double[] c1 = path.get(i + 1);
            // outer (left) and inner (right) faces
            double oy = (left[i][0] + left[i + 1][0]) * 0.5 - (c[0] + c1[0]) * 0.5;
            double oz = (left[i][1] + left[i + 1][1]) * 0.5 - (c[1] + c1[1]) * 0.5;
            this.quad(tile, shade(color, lightFor(0, oy, oz)), new double[]{-halfX, left[i][0], left[i][1], u0, 0}, new double[]{halfX, left[i][0], left[i][1], u0, 1},
               new double[]{halfX, left[i + 1][0], left[i + 1][1], u1, 1}, new double[]{-halfX, left[i + 1][0], left[i + 1][1], u1, 0}, 0, oy, oz);
            this.quad(tile, shade(color, lightFor(0, -oy, -oz)), new double[]{-halfX, right[i][0], right[i][1], u0, 0}, new double[]{halfX, right[i][0], right[i][1], u0, 1},
               new double[]{halfX, right[i + 1][0], right[i + 1][1], u1, 1}, new double[]{-halfX, right[i + 1][0], right[i + 1][1], u1, 0}, 0, -oy, -oz);
            for (int s = -1; s <= 1; s += 2) {
               double x = s * halfX;
               this.quad(tile, shade(color, 0.9), new double[]{x, sides[0][0], sides[0][1], u0, 0}, new double[]{x, sides[1][0], sides[1][1], u1, 0},
                  new double[]{x, sides[3][0], sides[3][1], u1, 1}, new double[]{x, sides[2][0], sides[2][1], u0, 1}, s, 0, 0);
            }
         }
      }

      /** Disk facing +/-Z (dir), centred at (x, y, z). */
      void diskZ(double x, double y, double z, double r, int dir, int seg, int tile, int color) {
         for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg;
            double a1 = Math.PI * 2 * (i + 1) / seg;
            this.quad(tile, color, new double[]{x, y, z, 0.5, 0.5}, new double[]{x + Math.cos(a0) * r, y + Math.sin(a0) * r, z, 0.5 + Math.cos(a0) * 0.5, 0.5 + Math.sin(a0) * 0.5},
               new double[]{x + Math.cos(a1) * r, y + Math.sin(a1) * r, z, 0.5 + Math.cos(a1) * 0.5, 0.5 + Math.sin(a1) * 0.5}, new double[]{x, y, z, 0.5, 0.5}, 0, 0, dir);
         }
      }

      /** Disk facing +/-X. */
      void diskX(double x, double y, double z, double r, int dir, int seg, int tile, int color) {
         for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg;
            double a1 = Math.PI * 2 * (i + 1) / seg;
            this.quad(tile, color, new double[]{x, y, z, 0.5, 0.5}, new double[]{x, y + Math.cos(a0) * r, z + Math.sin(a0) * r, 0.5 + Math.cos(a0) * 0.5, 0.5 + Math.sin(a0) * 0.5},
               new double[]{x, y + Math.cos(a1) * r, z + Math.sin(a1) * r, 0.5 + Math.cos(a1) * 0.5, 0.5 + Math.sin(a1) * 0.5}, new double[]{x, y, z, 0.5, 0.5}, dir, 0, 0);
         }
      }

      /** Cylinder along Z. */
      void cylinderZ(double x, double y, double z0, double z1, double r, int seg, int tile, int color) {
         for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg;
            double a1 = Math.PI * 2 * (i + 1) / seg;
            double c0 = Math.cos(a0);
            double s0 = Math.sin(a0);
            double c1 = Math.cos(a1);
            double s1 = Math.sin(a1);
            this.quadSmooth(tile, shade(color, lightFor(c0, s0, 0)), new double[]{x + c0 * r, y + s0 * r, z0, 0, (double)i / seg, c0, s0, 0},
               new double[]{x + c1 * r, y + s1 * r, z0, 0, (double)(i + 1) / seg, c1, s1, 0}, new double[]{x + c1 * r, y + s1 * r, z1, 1, (double)(i + 1) / seg, c1, s1, 0},
               new double[]{x + c0 * r, y + s0 * r, z1, 1, (double)i / seg, c0, s0, 0});
         }
      }
   }

   /** Fixed studio reflection: top faces brighter, undersides darker. */
   static double lightFor(double nx, double ny, double nz) {
      return 0.84 + 0.2 * ny + 0.04 * nz - 0.02 * Math.abs(nx);
   }

   static int shade(int rgb, double f) {
      int r = (int)Math.clamp(((rgb >> 16) & 255) * f, 0.0, 255.0);
      int g = (int)Math.clamp(((rgb >> 8) & 255) * f, 0.0, 255.0);
      int b = (int)Math.clamp((rgb & 255) * f, 0.0, 255.0);
      return r << 16 | g << 8 | b;
   }
}
