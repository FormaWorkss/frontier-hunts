package com.formaworks.frontierhunts.wildlife2026.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * [meshes] The CPU skinning and emission path of the Ultra wildlife (WildlifeRenderer), allocation free.
 *
 * <ul>
 * <li>Each mesh is compiled once into a compact influence list ({@link Compiled}): per vertex only its non-zero bone
 * influences (most body vertices have one or two), bone offsets pre-multiplied, rigid vertices on a fast path.</li>
 * <li>{@link #skinView}: an animal that is posed this frame is skinned straight into camera space. The 3x4 bone
 * matrices are pre-multiplied by the pose matrix (and the normal matrix) once per bone, so every vertex is
 * transformed once instead of twice (skin into model space, then model into camera space).</li>
 * <li>{@link #skinModel} + {@link #transform}: an animal that keeps its pose for a few frames (far, or standing
 * still) is skinned into model space when due and only moved with the body in between.</li>
 * <li>{@link #emit}: one fast-path vertex call per triangle corner (the entity buffers are not indexed).</li>
 * </ul>
 * Output is exactly what the old two-pass code produced (same maths in a different order: within float rounding).
 */
public final class MeshSkinner {
   private MeshSkinner() {
   }

   /** compact influences of one mesh: vertex v uses entries start[v] .. start[v+1]-1 */
   public static final class Compiled {
      final int[] start;
      final int[] boneOff;
      final float[] w;

      Compiled(SkinnedMesh m) {
         int nv = m.vertexCount;
         this.start = new int[nv + 1];
         int n = 0;
         // SkinnedMesh.parse pins an unweighted vertex to its first bone (weight 1): every vertex has an influence
         for (int v = 0; v < nv; v++) {
            for (int k = 0; k < 4; k++) {
               if (m.weight[v * 4 + k] > 0.0F) n++;
            }
         }
         this.boneOff = new int[n];
         this.w = new float[n];
         int i = 0;
         for (int v = 0; v < nv; v++) {
            this.start[v] = i;
            for (int k = 0; k < 4; k++) {
               float wt = m.weight[v * 4 + k];
               if (wt > 0.0F) {
                  this.boneOff[i] = (m.bone[v * 4 + k] & 255) * 12;
                  this.w[i] = wt;
                  i++;
               }
            }
         }
         this.start[nv] = i;
      }
   }

   public static Compiled compiled(SkinnedMesh m) {
      Compiled c = m.compiled;
      if (c == null) {
         c = new Compiled(m);
         m.compiled = c;
      }
      return c;
   }

   /** Skins into model space: out = (x, y, z, nx, ny, nz) per vertex, normals unit length. */
   public static void skinModel(SkinnedMesh m, float[] M, float[] out) {
      run(m, compiled(m), M, M, out);
   }

   /**
    * Skins straight into camera space with this pose (position by {@code pose.pose()}, normal by
    * {@code pose.normal()}); {@code scratch} must hold bones * 24 floats.
    */
   public static void skinView(SkinnedMesh m, float[] M, PoseStack.Pose pose, float[] scratch, float[] out) {
      int nb = m.names.length;
      Matrix4f P = pose.pose();
      Matrix3f Nm = pose.normal();
      float p00 = P.m00(), p01 = P.m10(), p02 = P.m20(), p03 = P.m30();
      float p10 = P.m01(), p11 = P.m11(), p12 = P.m21(), p13 = P.m31();
      float p20 = P.m02(), p21 = P.m12(), p22 = P.m22(), p23 = P.m32();
      float n00 = Nm.m00(), n01 = Nm.m10(), n02 = Nm.m20();
      float n10 = Nm.m01(), n11 = Nm.m11(), n12 = Nm.m21();
      float n20 = Nm.m02(), n21 = Nm.m12(), n22 = Nm.m22();
      int half = nb * 12;
      for (int b = 0; b < nb; b++) {
         int o = b * 12;
         for (int c = 0; c < 4; c++) {
            float a0 = M[o + c], a1 = M[o + 4 + c], a2 = M[o + 8 + c];
            // position: P * [M; 0 0 0 1]
            scratch[o + c] = p00 * a0 + p01 * a1 + p02 * a2 + (c == 3 ? p03 : 0.0F);
            scratch[o + 4 + c] = p10 * a0 + p11 * a1 + p12 * a2 + (c == 3 ? p13 : 0.0F);
            scratch[o + 8 + c] = p20 * a0 + p21 * a1 + p22 * a2 + (c == 3 ? p23 : 0.0F);
            // normal: N * R (translation column unused)
            scratch[half + o + c] = n00 * a0 + n01 * a1 + n02 * a2;
            scratch[half + o + 4 + c] = n10 * a0 + n11 * a1 + n12 * a2;
            scratch[half + o + 8 + c] = n20 * a0 + n21 * a1 + n22 * a2;
         }
      }
      run2(m, compiled(m), scratch, half, out);
   }

   private static void run(SkinnedMesh m, Compiled c, float[] M, float[] R, float[] out) {
      int nv = m.vertexCount;
      float[] P = m.pos, N = m.nrm;
      int[] st = c.start, bo = c.boneOff;
      float[] W = c.w;
      for (int v = 0; v < nv; v++) {
         int v3 = v * 3;
         float px = P[v3], py = P[v3 + 1], pz = P[v3 + 2];
         float nx = N[v3], ny = N[v3 + 1], nz = N[v3 + 2];
         int i = st[v], e = st[v + 1];
         float x, y, z, a, b, cc;
         int o = bo[i];
         float w = W[i];
         x = w * (M[o] * px + M[o + 1] * py + M[o + 2] * pz + M[o + 3]);
         y = w * (M[o + 4] * px + M[o + 5] * py + M[o + 6] * pz + M[o + 7]);
         z = w * (M[o + 8] * px + M[o + 9] * py + M[o + 10] * pz + M[o + 11]);
         a = w * (R[o] * nx + R[o + 1] * ny + R[o + 2] * nz);
         b = w * (R[o + 4] * nx + R[o + 5] * ny + R[o + 6] * nz);
         cc = w * (R[o + 8] * nx + R[o + 9] * ny + R[o + 10] * nz);
         for (i++; i < e; i++) {
            o = bo[i];
            w = W[i];
            x += w * (M[o] * px + M[o + 1] * py + M[o + 2] * pz + M[o + 3]);
            y += w * (M[o + 4] * px + M[o + 5] * py + M[o + 6] * pz + M[o + 7]);
            z += w * (M[o + 8] * px + M[o + 9] * py + M[o + 10] * pz + M[o + 11]);
            a += w * (R[o] * nx + R[o + 1] * ny + R[o + 2] * nz);
            b += w * (R[o + 4] * nx + R[o + 5] * ny + R[o + 6] * nz);
            cc += w * (R[o + 8] * nx + R[o + 9] * ny + R[o + 10] * nz);
         }
         float l = Mth.invSqrt(a * a + b * b + cc * cc + 1.0E-12F);
         int q = v * 6;
         out[q] = x;
         out[q + 1] = y;
         out[q + 2] = z;
         out[q + 3] = a * l;
         out[q + 4] = b * l;
         out[q + 5] = cc * l;
      }
   }

   /** same as run with the normal matrices stored after the position matrices in one array */
   private static void run2(SkinnedMesh m, Compiled c, float[] S, int h, float[] out) {
      int nv = m.vertexCount;
      float[] P = m.pos, N = m.nrm;
      int[] st = c.start, bo = c.boneOff;
      float[] W = c.w;
      for (int v = 0; v < nv; v++) {
         int v3 = v * 3;
         float px = P[v3], py = P[v3 + 1], pz = P[v3 + 2];
         float nx = N[v3], ny = N[v3 + 1], nz = N[v3 + 2];
         int i = st[v], e = st[v + 1];
         int o = bo[i];
         float w = W[i];
         float x = w * (S[o] * px + S[o + 1] * py + S[o + 2] * pz + S[o + 3]);
         float y = w * (S[o + 4] * px + S[o + 5] * py + S[o + 6] * pz + S[o + 7]);
         float z = w * (S[o + 8] * px + S[o + 9] * py + S[o + 10] * pz + S[o + 11]);
         int r = h + o;
         float a = w * (S[r] * nx + S[r + 1] * ny + S[r + 2] * nz);
         float b = w * (S[r + 4] * nx + S[r + 5] * ny + S[r + 6] * nz);
         float cc = w * (S[r + 8] * nx + S[r + 9] * ny + S[r + 10] * nz);
         for (i++; i < e; i++) {
            o = bo[i];
            w = W[i];
            r = h + o;
            x += w * (S[o] * px + S[o + 1] * py + S[o + 2] * pz + S[o + 3]);
            y += w * (S[o + 4] * px + S[o + 5] * py + S[o + 6] * pz + S[o + 7]);
            z += w * (S[o + 8] * px + S[o + 9] * py + S[o + 10] * pz + S[o + 11]);
            a += w * (S[r] * nx + S[r + 1] * ny + S[r + 2] * nz);
            b += w * (S[r + 4] * nx + S[r + 5] * ny + S[r + 6] * nz);
            cc += w * (S[r + 8] * nx + S[r + 9] * ny + S[r + 10] * nz);
         }
         float l = Mth.invSqrt(a * a + b * b + cc * cc + 1.0E-12F);
         int q = v * 6;
         out[q] = x;
         out[q + 1] = y;
         out[q + 2] = z;
         out[q + 3] = a * l;
         out[q + 4] = b * l;
         out[q + 5] = cc * l;
      }
   }

   /** Moves a model-space skin (from {@link #skinModel}) into camera space with this pose. */
   public static void transform(SkinnedMesh m, float[] model, PoseStack.Pose pose, float[] out) {
      Matrix4f P = pose.pose();
      Matrix3f Nm = pose.normal();
      float p00 = P.m00(), p01 = P.m10(), p02 = P.m20(), p03 = P.m30();
      float p10 = P.m01(), p11 = P.m11(), p12 = P.m21(), p13 = P.m31();
      float p20 = P.m02(), p21 = P.m12(), p22 = P.m22(), p23 = P.m32();
      float n00 = Nm.m00(), n01 = Nm.m10(), n02 = Nm.m20();
      float n10 = Nm.m01(), n11 = Nm.m11(), n12 = Nm.m21();
      float n20 = Nm.m02(), n21 = Nm.m12(), n22 = Nm.m22();
      int n = m.vertexCount * 6;
      for (int o = 0; o < n; o += 6) {
         float x = model[o], y = model[o + 1], z = model[o + 2], a = model[o + 3], b = model[o + 4], c = model[o + 5];
         out[o] = p00 * x + p01 * y + p02 * z + p03;
         out[o + 1] = p10 * x + p11 * y + p12 * z + p13;
         out[o + 2] = p20 * x + p21 * y + p22 * z + p23;
         float nx = n00 * a + n01 * b + n02 * c, ny = n10 * a + n11 * b + n12 * c, nz = n20 * a + n21 * b + n22 * c;
         // unit length like PoseStack.Pose.transformNormal (a non-uniform scale upstream leaves the normal matrix unscaled)
         float l = Mth.invSqrt(nx * nx + ny * ny + nz * nz + 1.0E-12F);
         out[o + 3] = nx * l;
         out[o + 4] = ny * l;
         out[o + 5] = nz * l;
      }
   }

   /** One fast-path vertex per triangle corner (BufferBuilder writes the whole entity vertex at once). */
   public static void emit(SkinnedMesh m, float[] view, VertexConsumer vc, int light, int overlay) {
      int[] T = m.tris;
      float[] UV = m.uv;
      for (int i = 0; i < T.length; i++) {
         int v = T[i];
         int o = v * 6;
         vc.addVertex(view[o], view[o + 1], view[o + 2], -1, UV[v * 2], UV[v * 2 + 1], overlay, light, view[o + 3], view[o + 4], view[o + 5]);
      }
   }
}
