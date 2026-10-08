package com.formaworks.frontierhunts.hunting.rutfight;

import java.util.Arrays;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * [rutfight] What one fighter's head looks like to the contact solver, for one graphics preset: the head bone matrix
 * in the canonical locked pose (model space), the antler and skull surfaces as dense point samples in head-bone space,
 * and the model-to-entity scale. Head-bone space is pose independent for every preset (antlers and skull are rigidly
 * attached to the head bone), so the points are built once per animal and re-placed with any head transform.
 *
 * <p>Entity space: origin at the feet, +z forward (towards the rival), +y up. The renderers turn the model by 180 degrees
 * and scale it, so entity = (-x * sx, y * sy, -z * sz) of model space.</p>
 */
public final class FightModel {
   /** Antler / skull point spacing (blocks of model space); triangles are subdivided down to this. */
   public static final float SPACING = 0.022F;
   public final Matrix4f head;
   public final float sx;
   public final float sy;
   public final float sz;
   /** Head-bone-space antler surface points (xyz triples). */
   public final float[] antler;
   /** Head-bone-space skull / face surface points (xyz triples). */
   public final float[] skull;

   public FightModel(Matrix4f head, float sx, float sy, float sz, float[] antler, float[] skull) {
      this.head = new Matrix4f(head);
      this.sx = sx;
      this.sy = sy;
      this.sz = sz;
      this.antler = antler;
      this.skull = skull;
   }

   public boolean hasAntlers() {
      return this.antler.length >= 3;
   }

   /** Model-space point to entity space. */
   public Vector3f toEntity(Vector3f model, Vector3f out) {
      return out.set(-model.x * this.sx, model.y * this.sy, -model.z * this.sz);
   }

   /** Entity-space point to model space. */
   public Vector3f toModel(Vector3f entity, Vector3f out) {
      return out.set(-entity.x / this.sx, entity.y / this.sy, -entity.z / this.sz);
   }

   /** Head-space points placed with head matrix {@code h}, in entity space (xyz triples). */
   public float[] place(float[] local, Matrix4f h) {
      float[] out = new float[local.length];
      Vector3f p = new Vector3f();

      for (int i = 0; i + 2 < local.length; i += 3) {
         h.transformPosition(local[i], local[i + 1], local[i + 2], p);
         out[i] = -p.x * this.sx;
         out[i + 1] = p.y * this.sy;
         out[i + 2] = -p.z * this.sz;
      }

      return out;
   }

   /** Collects surface samples (in whatever space the caller feeds) with at most {@link #SPACING} between them. */
   public static final class Points {
      private float[] data = new float[3072];
      private int n;

      public void add(float x, float y, float z) {
         if (this.n + 3 > this.data.length) {
            this.data = Arrays.copyOf(this.data, this.data.length * 2);
         }

         this.data[this.n++] = x;
         this.data[this.n++] = y;
         this.data[this.n++] = z;
      }

      /** Samples a triangle: corners, edges and interior on a barycentric grid fine enough for {@link #SPACING}. */
      public void triangle(Vector3f a, Vector3f b, Vector3f c) {
         float l = Math.max(a.distance(b), Math.max(b.distance(c), c.distance(a)));
         int k = Math.max(1, Math.min(40, (int)Math.ceil(l / SPACING)));

         for (int i = 0; i <= k; i++) {
            for (int j = 0; j <= k - i; j++) {
               float u = (float)i / k;
               float v = (float)j / k;
               float w = 1.0F - u - v;
               this.add(a.x * w + b.x * u + c.x * v, a.y * w + b.y * u + c.y * v, a.z * w + b.z * u + c.z * v);
            }
         }
      }

      /** Samples the six faces of a box given by a corner and three edge vectors. */
      public void box(Vector3f o, Vector3f e0, Vector3f e1, Vector3f e2) {
         Vector3f[] c = new Vector3f[8];

         for (int i = 0; i < 8; i++) {
            float a = i != 1 && i != 2 && i != 5 && i != 6 ? 0.0F : 1.0F;
            float b = i != 2 && i != 3 && i != 6 && i != 7 ? 0.0F : 1.0F;
            float d = i >= 4 ? 1.0F : 0.0F;
            c[i] = new Vector3f(o).fma(a, e0).fma(b, e1).fma(d, e2);
         }

         int[][] faces = {{0, 3, 2, 1}, {5, 6, 7, 4}, {4, 7, 3, 0}, {1, 2, 6, 5}, {3, 7, 6, 2}, {4, 0, 1, 5}};

         for (int[] f : faces) {
            this.triangle(c[f[0]], c[f[1]], c[f[2]]);
            this.triangle(c[f[0]], c[f[2]], c[f[3]]);
         }
      }

      public int size() {
         return this.n / 3;
      }

      /** Thins duplicates on a grid of half the spacing (shared corners, coplanar faces). */
      public float[] toArray() {
         float cell = SPACING * 0.5F;
         java.util.HashSet<Long> seen = new java.util.HashSet<>();
         float[] out = new float[this.n];
         int m = 0;

         for (int i = 0; i < this.n; i += 3) {
            long key = ((long)(int)Math.floor(this.data[i] / cell) & 2097151L) << 42
               | ((long)(int)Math.floor(this.data[i + 1] / cell) & 2097151L) << 21
               | (long)(int)Math.floor(this.data[i + 2] / cell) & 2097151L;
            if (seen.add(key)) {
               out[m++] = this.data[i];
               out[m++] = this.data[i + 1];
               out[m++] = this.data[i + 2];
            }
         }

         return Arrays.copyOf(out, m);
      }
   }
}
