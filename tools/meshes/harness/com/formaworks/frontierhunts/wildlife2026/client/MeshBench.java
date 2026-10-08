package com.formaworks.frontierhunts.wildlife2026.client;

import com.formaworks.frontierhunts.perf.client.AnimationLod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.Locale;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * [meshes] Microbenchmark of the Ultra wildlife render path on the CPU: pose (WildlifeRig) + skinning + per-vertex
 * emission into an entity-format vertex buffer (36 bytes per vertex, written like BufferBuilder's fast path).
 * BEFORE = master's meshes and master's code path (two-pass skin then transform, ultra/bal levels);
 * AFTER = this branch's meshes and code (ultra/mid/bal levels, compact influences, fused skin-to-camera, still
 * animals at 30 poses/s). Scenes of 1, 5 and 20 wolves at fixed distances, 100 FPS simulated frames, ULTRA animal detail.
 * usage: MeshBench &lt;master mesh dir&gt; &lt;branch mesh dir&gt; [species] [height]
 */
public final class MeshBench {
   /** thread CPU time, not wall time: the build machine is shared, a descheduled thread must not count */
   static final java.lang.management.ThreadMXBean CPU = java.lang.management.ManagementFactory.getThreadMXBean();
   static SkinnedMesh load(File f) throws Exception {
      Method p = SkinnedMesh.class.getDeclaredMethod("parse", InputStream.class);
      p.setAccessible(true);
      return (SkinnedMesh)p.invoke(null, new ByteArrayInputStream(Files.readAllBytes(f.toPath())));
   }

   /** a vertex sink with BufferBuilder's per-vertex cost (absolute puts into a direct buffer) */
   static final class Sink implements VertexConsumer {
      ByteBuffer b = ByteBuffer.allocateDirect(64 << 20).order(ByteOrder.nativeOrder());
      int p;

      @Override
      public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
         if (this.p > this.b.capacity() - 64) this.p = 0;
         ByteBuffer b = this.b;
         int p = this.p;
         b.putFloat(p, x);
         b.putFloat(p + 4, y);
         b.putFloat(p + 8, z);
         b.putInt(p + 12, color);
         b.putFloat(p + 16, u);
         b.putFloat(p + 20, v);
         b.putInt(p + 24, overlay);
         b.putInt(p + 28, light);
         b.put(p + 32, (byte)(int)(Mth.clamp(nx, -1, 1) * 127));
         b.put(p + 33, (byte)(int)(Mth.clamp(ny, -1, 1) * 127));
         b.put(p + 34, (byte)(int)(Mth.clamp(nz, -1, 1) * 127));
         this.p = p + 36;
      }

      @Override public VertexConsumer addVertex(float x, float y, float z) { return this; }
      @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
      @Override public VertexConsumer setUv(float u, float v) { return this; }
      @Override public VertexConsumer setUv1(int u, int v) { return this; }
      @Override public VertexConsumer setUv2(int u, int v) { return this; }
      @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
   }

   // ---------------------------------------------------------------- master's code path (WildlifeRenderer at 644f37a)
   static void legacySkin(SkinnedMesh m, float[] M, float[] out) {
      int nv = m.vertexCount;
      float[] P = m.pos, N = m.nrm, W = m.weight;
      byte[] B = m.bone;
      for (int v = 0; v < nv; v++) {
         float px = P[v * 3], py = P[v * 3 + 1], pz = P[v * 3 + 2];
         float nx = N[v * 3], ny = N[v * 3 + 1], nz = N[v * 3 + 2];
         float x = 0, y = 0, z = 0, a = 0, b = 0, c = 0;
         for (int k = 0; k < 4; k++) {
            float w = W[v * 4 + k];
            if (w <= 0.0F) continue;
            int o = (B[v * 4 + k] & 255) * 12;
            x += w * (M[o] * px + M[o + 1] * py + M[o + 2] * pz + M[o + 3]);
            y += w * (M[o + 4] * px + M[o + 5] * py + M[o + 6] * pz + M[o + 7]);
            z += w * (M[o + 8] * px + M[o + 9] * py + M[o + 10] * pz + M[o + 11]);
            a += w * (M[o] * nx + M[o + 1] * ny + M[o + 2] * nz);
            b += w * (M[o + 4] * nx + M[o + 5] * ny + M[o + 6] * nz);
            c += w * (M[o + 8] * nx + M[o + 9] * ny + M[o + 10] * nz);
         }
         float l = Mth.invSqrt(a * a + b * b + c * c + 1.0E-12F);
         int o = v * 6;
         out[o] = x; out[o + 1] = y; out[o + 2] = z;
         out[o + 3] = a * l; out[o + 4] = b * l; out[o + 5] = c * l;
      }
   }

   static void legacyDraw(SkinnedMesh m, float[] model, PoseStack.Pose pose, VertexConsumer vc, float[] out, Vector3f t) {
      int nv = m.vertexCount;
      Matrix4f mat = pose.pose();
      for (int v = 0; v < nv; v++) {
         int o = v * 6;
         mat.transformPosition(model[o], model[o + 1], model[o + 2], t);
         out[o] = t.x; out[o + 1] = t.y; out[o + 2] = t.z;
         pose.transformNormal(model[o + 3], model[o + 4], model[o + 5], t);
         out[o + 3] = t.x; out[o + 4] = t.y; out[o + 5] = t.z;
      }
      int[] T = m.tris;
      float[] UV = m.uv;
      for (int i = 0; i < T.length; i++) {
         int v = T[i];
         int o = v * 6;
         vc.addVertex(out[o], out[o + 1], out[o + 2], -1, UV[v * 2], UV[v * 2 + 1], 655360, 15728880, out[o + 3], out[o + 4], out[o + 5]);
      }
   }

   // ---------------------------------------------------------------- scene simulation
   static final class Animal {
      double dist;
      boolean grazing;
      float lastPose = Float.NaN;
      SkinnedMesh mesh;
      float[] model = new float[0], bones = new float[0];
      boolean modelValid;
      int tier = 1;
      float phase;
   }

   static SkinnedMesh mU0, mB0, mU1, mM1, mB1;
   static double height = 0.95;
   static final Sink SINK = new Sink();
   static float[] M = new float[64 * 12], OUT = new float[200000 * 6], VB = new float[64 * 24];
   static final Vector3f TMP = new Vector3f();
   static long poses, skins;

   static void pose(SkinnedMesh m, Animal a, float t) {
      WildlifeRig.Input in = new WildlifeRig.Input();
      in.age = t * 20.0F;
      if (a.grazing) {
         in.w[WildlifeRig.FEED] = 1.0F;
      } else {
         in.amount = 0.6F;
         in.limbSwing = t * 12.0F + a.phase;
      }
      in.headYaw = 10.0F;
      WildlifeRig.pose(m, in, M);
      poses++;
   }

   /** one frame of one animal on master's path */
   static void before(Animal a, int rank, float t, PoseStack.Pose pose) {
      boolean near = a.dist < 52.0 && rank < 10;
      SkinnedMesh m = near ? mU0 : mB0;
      double eff = a.dist / height;
      boolean hold = a.mesh == m && t > a.lastPose && !AnimationLod.due(t - a.lastPose, eff);
      if (!hold) {
         pose(m, a, t);
         if (a.model.length < m.vertexCount * 6) a.model = new float[m.vertexCount * 6];
         legacySkin(m, M, a.model);
         skins++;
         a.mesh = m;
         a.lastPose = t;
      }
      legacyDraw(m, a.model, pose, SINK, OUT, TMP);
   }

   /** one frame of one animal on this branch's path (mirrors WildlifeRenderer.renderSculpted) */
   static void after(Animal a, int rank, float t, PoseStack.Pose pose) {
      boolean near = a.dist < 52.0 && rank < 10;
      double eff = a.dist / height;
      int tier = 2;
      if (near) {
         boolean full = eff < 12.0 * (a.tier == 0 ? 1.12 : 1.0) && rank < 3; // MeshLod at ULTRA detail
         tier = full ? 0 : 1;
      }
      a.tier = tier;
      SkinnedMesh m = tier == 0 ? mU1 : tier == 1 ? mM1 : mB1;
      double rateEff = a.grazing ? Math.max(eff, 46.0) : eff; // still animals: 30 poses a second
      boolean hold = a.mesh == m && t > a.lastPose && !AnimationLod.due(t - a.lastPose, rateEff);
      if (!hold) {
         pose(m, a, t);
         int nb12 = m.names.length * 12;
         if (a.bones.length < nb12) a.bones = new float[nb12];
         System.arraycopy(M, 0, a.bones, 0, nb12);
         a.modelValid = false;
         if (AnimationLod.interval(eff) > 0.0F || a.grazing) {
            if (a.model.length < m.vertexCount * 6) a.model = new float[m.vertexCount * 6];
            MeshSkinner.skinModel(m, M, a.model);
            a.modelValid = true;
         }
         skins++;
         a.mesh = m;
         a.lastPose = t;
      }
      if (a.modelValid) MeshSkinner.transform(m, a.model, pose, OUT);
      else MeshSkinner.skinView(m, a.bones, pose, VB, OUT);
      MeshSkinner.emit(m, OUT, SINK, 15728880, 655360);
   }

   static Animal[] scene(int n, boolean herd) {
      Animal[] s = new Animal[n];
      for (int i = 0; i < n; i++) {
         s[i] = new Animal();
         s[i].dist = n == 1 ? 6.0 : 6.0 + (n == 5 ? 22.0 : 54.0) * i / (n - 1);
         s[i].grazing = herd && i % 2 == 1;
         s[i].phase = i * 1.7F;
      }
      return s;
   }

   static double run(int n, boolean herd, boolean after, int frames) {
      Animal[] s = scene(n, herd);
      PoseStack ps = new PoseStack();
      ps.mulPose(Axis.YP.rotationDegrees(33.0F));
      ps.translate(0.2F, -1.1F, 4.0F);
      long t0 = 0;
      for (int f = -frames / 2; f < frames; f++) { // first half = warm-up
         if (f == 0) t0 = CPU.getCurrentThreadCpuTime();
         float t = (f + frames) / 100.0F;
         for (int i = 0; i < n; i++) {
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(i * 7.0F + f * 0.05F));
            if (after) after(s[i], i, t, ps.last());
            else before(s[i], i, t, ps.last());
            ps.popPose();
         }
      }
      return (CPU.getCurrentThreadCpuTime() - t0) / 1e6 / frames;
   }

   public static void main(String[] a) throws Exception {
      String sp = a.length > 2 ? a[2] : "wolf";
      if (a.length > 3) height = Double.parseDouble(a[3]);
      mU0 = load(new File(a[0], sp + "_ultra.fhsk"));
      mB0 = load(new File(a[0], sp + "_bal.fhsk"));
      mU1 = load(new File(a[1], sp + "_ultra.fhsk"));
      File mid = new File(a[1], sp + "_mid.fhsk");
      mM1 = mid.exists() ? load(mid) : mU1;
      mB1 = load(new File(a[1], sp + "_bal.fhsk"));
      System.out.printf(Locale.ROOT, "%s tris: master ultra %d / bal %d | branch ultra %d / mid %d / bal %d%n", sp, mU0.tris.length / 3, mB0.tris.length / 3,
         mU1.tris.length / 3, mM1.tris.length / 3, mB1.tris.length / 3);
      // single-animal costs per level (pose + skin + draw every frame)
      double[] best = {1e9, 1e9, 1e9, 1e9, 1e9};
      for (int rep = 0; rep < 4; rep++) {
         StringBuilder line = new StringBuilder("per animal per frame, posed every frame, best of 4 (ms): ");
         int ci = 0;
         for (Object[] c : new Object[][]{{"master ultra", mU0, false}, {"master bal", mB0, false}, {"branch ultra", mU1, true}, {"branch mid", mM1, true}, {"branch bal", mB1, true}}) {
            SkinnedMesh m = (SkinnedMesh)c[1];
            boolean nw = (Boolean)c[2];
            Animal an = new Animal();
            PoseStack ps = new PoseStack();
            int frames = 300;
            long t0 = 0;
            for (int f = -100; f < frames; f++) {
               if (f == 0) t0 = CPU.getCurrentThreadCpuTime();
               pose(m, an, f / 100.0F);
               if (an.model.length < m.vertexCount * 6) an.model = new float[m.vertexCount * 6];
               if (nw) {
                  MeshSkinner.skinView(m, M, ps.last(), VB, OUT);
                  MeshSkinner.emit(m, OUT, SINK, 15728880, 655360);
               } else {
                  legacySkin(m, M, an.model);
                  legacyDraw(m, an.model, ps.last(), SINK, OUT, TMP);
               }
            }
            best[ci] = Math.min(best[ci], (CPU.getCurrentThreadCpuTime() - t0) / 1e6 / frames);
            line.append(String.format(Locale.ROOT, "%s %.3f  ", c[0], best[ci]));
            ci++;
         }
         if (rep == 3) System.out.println(line);
      }
      System.out.println("scene (100 FPS, ULTRA detail)            | before ms/frame | after ms/frame | speed-up | before/after ms per animal");
      for (boolean herd : new boolean[]{false, true}) {
         for (int n : new int[]{1, 5, 20}) {
            // alternating repetitions, best of each (the machine is shared: the minimum is the least disturbed run)
            double b = 1e9, f = 1e9;
            for (int rep = 0; rep < 5; rep++) {
               b = Math.min(b, run(n, herd, false, 300));
               f = Math.min(f, run(n, herd, true, 300));
            }
            System.out.printf(Locale.ROOT, "%2d wolves %-28s | %15.3f | %14.3f | %7.2fx | %.3f / %.3f%n", n, herd ? "(herd: half grazing)" : "(all walking)", b, f, b / f, b / n, f / n);
         }
      }
   }
}
