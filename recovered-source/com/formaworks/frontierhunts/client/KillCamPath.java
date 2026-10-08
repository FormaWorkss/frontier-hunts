package com.formaworks.frontierhunts.client;

import net.minecraft.world.phys.Vec3;

/** Smooth (Catmull-Rom) arc-length parameterised flight path through the server's per-tick projectile samples. */
final class KillCamPath {
   private static final int SUB = 8;
   private final double[] xs;
   private final double[] ys;
   private final double[] zs;
   private final double[] cum;
   final double length;

   KillCamPath(Vec3 origin, float[] rel, Vec3 impact) {
      int n = rel.length / 3;
      Vec3[] pts;
      if (n >= 2) {
         pts = new Vec3[n];
         for (int i = 0; i < n; i++) {
            pts[i] = origin.add(rel[i * 3], rel[i * 3 + 1], rel[i * 3 + 2]);
         }
         pts[n - 1] = impact;
      } else {
         pts = new Vec3[]{origin, impact};
      }
      pts = dedupe(pts);
      int segs = pts.length - 1;
      int count = segs * SUB + 1;
      this.xs = new double[count];
      this.ys = new double[count];
      this.zs = new double[count];
      this.cum = new double[count];
      int k = 0;
      for (int s = 0; s < segs; s++) {
         Vec3 p0 = pts[Math.max(0, s - 1)];
         Vec3 p1 = pts[s];
         Vec3 p2 = pts[s + 1];
         Vec3 p3 = pts[Math.min(pts.length - 1, s + 2)];
         for (int j = 0; j < SUB; j++) {
            double t = (double)j / SUB;
            set(k++, cr(p0, p1, p2, p3, t));
         }
      }
      set(k, pts[pts.length - 1]);
      for (int i = 1; i < count; i++) {
         double dx = this.xs[i] - this.xs[i - 1];
         double dy = this.ys[i] - this.ys[i - 1];
         double dz = this.zs[i] - this.zs[i - 1];
         this.cum[i] = this.cum[i - 1] + Math.sqrt(dx * dx + dy * dy + dz * dz);
      }
      this.length = Math.max(1.0E-3, this.cum[count - 1]);
   }

   private static Vec3[] dedupe(Vec3[] pts) {
      java.util.ArrayList<Vec3> out = new java.util.ArrayList<>();
      for (Vec3 p : pts) {
         if (out.isEmpty() || out.get(out.size() - 1).distanceToSqr(p) > 1.0E-6) {
            out.add(p);
         }
      }
      if (out.size() < 2) {
         out.add(out.get(0).add(0.0, 0.0, 0.01));
      }
      return out.toArray(new Vec3[0]);
   }

   private void set(int i, Vec3 v) {
      this.xs[i] = v.x;
      this.ys[i] = v.y;
      this.zs[i] = v.z;
   }

   private static Vec3 cr(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
      double t2 = t * t;
      double t3 = t2 * t;
      return new Vec3(
         0.5 * (2.0 * p1.x + (-p0.x + p2.x) * t + (2.0 * p0.x - 5.0 * p1.x + 4.0 * p2.x - p3.x) * t2 + (-p0.x + 3.0 * p1.x - 3.0 * p2.x + p3.x) * t3),
         0.5 * (2.0 * p1.y + (-p0.y + p2.y) * t + (2.0 * p0.y - 5.0 * p1.y + 4.0 * p2.y - p3.y) * t2 + (-p0.y + 3.0 * p1.y - 3.0 * p2.y + p3.y) * t3),
         0.5 * (2.0 * p1.z + (-p0.z + p2.z) * t + (2.0 * p0.z - 5.0 * p1.z + 4.0 * p2.z - p3.z) * t2 + (-p0.z + 3.0 * p1.z - 3.0 * p2.z + p3.z) * t3)
      );
   }

   /** Position at arc-length fraction {@code s} in [0,1]. */
   Vec3 at(double s) {
      double d = Math.clamp(s, 0.0, 1.0) * this.length;
      int lo = 0;
      int hi = this.cum.length - 1;
      while (hi - lo > 1) {
         int mid = (lo + hi) >>> 1;
         if (this.cum[mid] <= d) {
            lo = mid;
         } else {
            hi = mid;
         }
      }
      double span = this.cum[hi] - this.cum[lo];
      double f = span <= 1.0E-9 ? 0.0 : (d - this.cum[lo]) / span;
      return new Vec3(
         this.xs[lo] + (this.xs[hi] - this.xs[lo]) * f, this.ys[lo] + (this.ys[hi] - this.ys[lo]) * f, this.zs[lo] + (this.zs[hi] - this.zs[lo]) * f
      );
   }

   /** Unit flight direction at {@code s}. */
   Vec3 tangent(double s) {
      double h = Math.min(0.02, 0.6 / this.length);
      Vec3 a = this.at(Math.max(0.0, s - h));
      Vec3 b = this.at(Math.min(1.0, s + h));
      Vec3 d = b.subtract(a);
      return d.lengthSqr() < 1.0E-12 ? new Vec3(0.0, 0.0, 1.0) : d.normalize();
   }

   /** Arc-length fraction {@code metres} back along the path from {@code s} (clamped at the muzzle). */
   double back(double s, double metres) {
      return Math.max(0.0, s - metres / this.length);
   }
}
