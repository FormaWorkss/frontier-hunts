package com.formaworks.frontierhunts.wildlife2026.client;

import com.formaworks.frontierhunts.ecology.client.HuntPose;
import com.formaworks.frontierhunts.wingshot.Flight;
import com.formaworks.frontierhunts.wingshot.client.BirdAnim;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * [meshes] Offline animation audit of every Ultra wildlife mesh (and the tracking hound): drives the real rig
 * (WildlifeRig + ecology HuntPose + wingshot BirdPose, HoundRig for the hound) through every state the game uses and
 * measures skinning artefacts numerically on the real skinned vertices:
 * <ul>
 * <li>stretch: worst |posed edge / bind edge - 1| (p99.9 and max) - rubber-banding, dragged vertices</li>
 * <li>collapse: smallest determinant of the blended 3x3 skinning matrix per vertex - candy-wrapper twist / volume loss</li>
 * <li>flipped: triangles whose posed normal points against their bind normal carried by the dominant bone</li>
 * <li>buried: vertices that end up inside the body (generalized winding number of a point just off the surface),
 *     minus those already inside at bind pose - legs or head passing through the torso</li>
 * <li>sink: lowest vertex below the ground while standing (feet in the soil)</li>
 * </ul>
 * usage: MeshAudit &lt;mesh dir&gt; &lt;out dir&gt; [dump] [species,...]
 */
public final class MeshAudit {
   static Method PARSE;
   /** buried-test sample stride over the welded vertices (-Dmeshaudit.stride, default 12) */
   static final int STRIDE = Integer.getInteger("meshaudit.stride", 12);

   static SkinnedMesh load(File f) throws Exception {
      if (PARSE == null) {
         PARSE = SkinnedMesh.class.getDeclaredMethod("parse", InputStream.class);
         PARSE.setAccessible(true);
      }
      return (SkinnedMesh)PARSE.invoke(null, new ByteArrayInputStream(Files.readAllBytes(f.toPath())));
   }

   record State(String name, int samples, Consumer<WildlifeRig.Input> setup, boolean standing) {
   }

   static final String[] SPECIES = {"wolf", "coyote", "cougar", "panther", "lion", "cheetah", "grizzly", "black_bear", "polar_bear",
      "bison", "boar", "pronghorn", "grouse", "duck"}; // [anims] the hound (own rig, owned by the hound workstream) is audited there

   static float depth(String sp) {
      return switch (sp) {
         case "cougar", "panther", "lion" -> 1.0F;
         case "cheetah" -> 0.6F;
         case "wolf", "coyote" -> 0.45F;
         case "grizzly", "black_bear", "polar_bear" -> 0.2F;
         default -> 0.0F;
      };
   }

   static List<State> states(String sp, SkinnedMesh m) {
      List<State> s = new ArrayList<>();
      boolean bird = m.bird;
      s.add(new State("idle", 1, in -> {}, true));
      s.add(new State("walk", 8, in -> in.amount = 0.35F, true));
      s.add(new State("trot", 8, in -> in.amount = 0.75F, true));
      s.add(new State("run", 8, in -> in.amount = 1.0F, true));
      if (!bird) {
         s.add(new State("gallop", 8, in -> { in.amount = 1.0F; in.w[WildlifeRig.FLEE] = 1.0F; }, false));
         s.add(new State("leap", 4, in -> { in.amount = 1.0F; in.w[WildlifeRig.FLEE] = 1.0F; in.air = 1.0F; }, false));
      }
      s.add(new State("graze", 3, in -> in.w[WildlifeRig.FEED] = 1.0F, true));
      s.add(new State("rest", 2, in -> in.w[WildlifeRig.REST] = 1.0F, false));
      s.add(new State("rest_look", 2, in -> { in.w[WildlifeRig.REST] = 1.0F; in.headYaw = 60.0F; in.headPitch = -40.0F; }, false));
      s.add(new State("alert", 1, in -> in.w[WildlifeRig.ALERT] = 1.0F, true));
      s.add(new State("curious", 3, in -> in.w[WildlifeRig.CURIOUS] = 1.0F, true));
      s.add(new State("warn", 1, in -> in.w[WildlifeRig.WARN] = 1.0F, true));
      s.add(new State("look_left", 1, in -> in.headYaw = 60.0F, true));
      s.add(new State("look_right_up", 1, in -> { in.headYaw = -60.0F; in.headPitch = -40.0F; }, true));
      s.add(new State("look_down", 1, in -> in.headPitch = 40.0F, true));
      s.add(new State("alert_look", 1, in -> { in.w[WildlifeRig.ALERT] = 1.0F; in.headYaw = 60.0F; in.headPitch = -40.0F; }, true));
      s.add(new State("graze_walk", 4, in -> { in.w[WildlifeRig.FEED] = 0.6F; in.amount = 0.25F; }, true));
      s.add(new State("flee_look", 8, in -> { in.amount = 1.0F; in.w[WildlifeRig.FLEE] = 1.0F; in.headYaw = 45.0F; }, false));
      float d = depth(sp);
      if (d > 0.0F) {
         s.add(new State("stalk", 4, in -> { in.eco[HuntPose.STALK] = d; in.amount = 0.2F; }, true));
         s.add(new State("stalk_still", 1, in -> in.eco[HuntPose.STALK] = d, true));
         s.add(new State("chase", 8, in -> { in.eco[HuntPose.CHASE] = 1.0F; in.w[WildlifeRig.FLEE] = 1.0F; in.amount = 1.0F; }, false));
         s.add(new State("pounce", 2, in -> { in.eco[HuntPose.POUNCE] = 1.0F; in.w[WildlifeRig.FLEE] = 1.0F; in.amount = 1.0F; in.air = 1.0F; }, false));
         s.add(new State("tear", 4, in -> in.eco[HuntPose.TEAR] = 1.0F, true));
         if (sp.equals("wolf") || sp.equals("coyote")) {
            s.add(new State("howl", 2, in -> in.eco[HuntPose.HOWL] = 1.0F, true));
         }
      }
      if (bird) {
         s.add(new State("swim", 4, in -> { in.water = true; in.amount = 0.4F; }, false));
      }
      return s;
   }

   // ---------------------------------------------------------------------------------------------- skinning
   static void skin(SkinnedMesh m, float[] M, float[] out, float[] det) {
      int nv = m.vertexCount;
      float[] P = m.pos, W = m.weight;
      byte[] B = m.bone;
      for (int v = 0; v < nv; v++) {
         float px = P[v * 3], py = P[v * 3 + 1], pz = P[v * 3 + 2];
         float x = 0, y = 0, z = 0;
         float a00 = 0, a01 = 0, a02 = 0, a10 = 0, a11 = 0, a12 = 0, a20 = 0, a21 = 0, a22 = 0;
         for (int k = 0; k < 4; k++) {
            float w = W[v * 4 + k];
            if (w <= 0) continue;
            int o = (B[v * 4 + k] & 255) * 12;
            x += w * (M[o] * px + M[o + 1] * py + M[o + 2] * pz + M[o + 3]);
            y += w * (M[o + 4] * px + M[o + 5] * py + M[o + 6] * pz + M[o + 7]);
            z += w * (M[o + 8] * px + M[o + 9] * py + M[o + 10] * pz + M[o + 11]);
            a00 += w * M[o]; a01 += w * M[o + 1]; a02 += w * M[o + 2];
            a10 += w * M[o + 4]; a11 += w * M[o + 5]; a12 += w * M[o + 6];
            a20 += w * M[o + 8]; a21 += w * M[o + 9]; a22 += w * M[o + 10];
         }
         out[v * 3] = x;
         out[v * 3 + 1] = y;
         out[v * 3 + 2] = z;
         if (det != null) {
            det[v] = a00 * (a11 * a22 - a12 * a21) - a01 * (a10 * a22 - a12 * a20) + a02 * (a10 * a21 - a11 * a20);
         }
      }
   }

   static int dominant(SkinnedMesh m, int v) {
      int best = 0;
      for (int k = 1; k < 4; k++) if (m.weight[v * 4 + k] > m.weight[v * 4 + best]) best = k;
      return m.bone[v * 4 + best] & 255;
   }

   // ---------------------------------------------------------------------------------------------- topology
   static final class Topo {
      int[] edges;          // pairs
      float[] restLen;
      int[] welded;         // vertex -> welded id
      int nw;
      int[] sample;         // vertices sampled for the buried test
   }

   static Topo topo(SkinnedMesh m) {
      Topo t = new Topo();
      Map<Long, Integer> wid = new HashMap<>();
      t.welded = new int[m.vertexCount];
      for (int v = 0; v < m.vertexCount; v++) {
         long k = (Math.round(m.pos[v * 3] * 1e4) & 0x1FFFFF) << 42 | (Math.round(m.pos[v * 3 + 1] * 1e4) & 0x1FFFFF) << 21 | (Math.round(m.pos[v * 3 + 2] * 1e4) & 0x1FFFFF);
         Integer i = wid.get(k);
         if (i == null) wid.put(k, i = wid.size());
         t.welded[v] = i;
      }
      t.nw = wid.size();
      java.util.HashSet<Long> seen = new java.util.HashSet<>();
      List<int[]> e = new ArrayList<>();
      int[] T = m.tris;
      for (int i = 0; i < T.length; i += 3) {
         for (int k = 0; k < 3; k++) {
            int a = T[i + k], b = T[i + (k + 1) % 3];
            // per real vertex pair (not per welded position): a vertex cut at a junction (tail against a hock) has a
            // copy per side that follows only its own bones, and an edge only ever joins copies on the same side
            long key = (long)Math.min(a, b) << 32 | Math.max(a, b);
            if (seen.add(key)) e.add(new int[]{a, b});
         }
      }
      t.edges = new int[e.size() * 2];
      t.restLen = new float[e.size()];
      for (int i = 0; i < e.size(); i++) {
         int a = e.get(i)[0], b = e.get(i)[1];
         t.edges[i * 2] = a;
         t.edges[i * 2 + 1] = b;
         t.restLen[i] = dist(m.pos, a, m.pos, b);
      }
      // buried-test sample: one corner vertex per welded vertex, every 3rd
      boolean[] took = new boolean[t.nw];
      List<Integer> smp = new ArrayList<>();
      for (int v = 0; v < m.vertexCount; v++) {
         int w = t.welded[v];
         if (!took[w] && w % STRIDE == 0) {
            took[w] = true;
            smp.add(v);
         }
      }
      t.sample = smp.stream().mapToInt(Integer::intValue).toArray();
      return t;
   }

   static float dist(float[] A, int a, float[] B, int b) {
      float dx = A[a * 3] - B[b * 3], dy = A[a * 3 + 1] - B[b * 3 + 1], dz = A[a * 3 + 2] - B[b * 3 + 2];
      return (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
   }

   /** vertex normals of a posed position array (area weighted, per corner vertex via the welded ids) */
   static float[] normals(SkinnedMesh m, Topo t, float[] P) {
      float[] wn = new float[t.nw * 3];
      int[] T = m.tris;
      for (int i = 0; i < T.length; i += 3) {
         int a = T[i], b = T[i + 1], c = T[i + 2];
         float ux = P[b * 3] - P[a * 3], uy = P[b * 3 + 1] - P[a * 3 + 1], uz = P[b * 3 + 2] - P[a * 3 + 2];
         float vx = P[c * 3] - P[a * 3], vy = P[c * 3 + 1] - P[a * 3 + 1], vz = P[c * 3 + 2] - P[a * 3 + 2];
         float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
         for (int k = 0; k < 3; k++) {
            int w = t.welded[T[i + k]];
            wn[w * 3] += nx;
            wn[w * 3 + 1] += ny;
            wn[w * 3 + 2] += nz;
         }
      }
      float[] n = new float[m.vertexCount * 3];
      for (int v = 0; v < m.vertexCount; v++) {
         int w = t.welded[v];
         float x = wn[w * 3], y = wn[w * 3 + 1], z = wn[w * 3 + 2];
         float l = (float)Math.sqrt(x * x + y * y + z * z) + 1e-20F;
         n[v * 3] = x / l;
         n[v * 3 + 1] = y / l;
         n[v * 3 + 2] = z / l;
      }
      return n;
   }

   /** generalized winding number of point q against the triangle soup (Van Oosterom-Strackee solid angles) */
   static double winding(float[] P, int[] T, double qx, double qy, double qz) {
      double sum = 0;
      for (int i = 0; i < T.length; i += 3) {
         int a = T[i] * 3, b = T[i + 1] * 3, c = T[i + 2] * 3;
         double ax = P[a] - qx, ay = P[a + 1] - qy, az = P[a + 2] - qz;
         double bx = P[b] - qx, by = P[b + 1] - qy, bz = P[b + 2] - qz;
         double cx = P[c] - qx, cy = P[c + 1] - qy, cz = P[c + 2] - qz;
         double la = Math.sqrt(ax * ax + ay * ay + az * az), lb = Math.sqrt(bx * bx + by * by + bz * bz), lc = Math.sqrt(cx * cx + cy * cy + cz * cz);
         double det = ax * (by * cz - bz * cy) - ay * (bx * cz - bz * cx) + az * (bx * cy - by * cx);
         double div = la * lb * lc + (ax * bx + ay * by + az * bz) * lc + (bx * cx + by * cy + bz * cz) * la + (cx * ax + cy * ay + cz * az) * lb;
         sum += Math.atan2(det, div);
      }
      return sum / (2 * Math.PI);
   }

   /** vertices whose point just outside the surface (along its posed normal) lies inside the body */
   static boolean[] buried(SkinnedMesh m, Topo t, float[] P, float[] N, float off) {
      boolean[] r = new boolean[t.sample.length];
      for (int i = 0; i < t.sample.length; i++) {
         int v = t.sample[i];
         double w = winding(P, m.tris, P[v * 3] + N[v * 3] * off, P[v * 3 + 1] + N[v * 3 + 1] * off, P[v * 3 + 2] + N[v * 3 + 2] * off);
         r[i] = w > 0.5;
      }
      return r;
   }

   // ---------------------------------------------------------------------------------------------- audit
   static final class Result {
      String state;
      float stretchMax, stretch999, detMin, sink;
      int flipped, buried, nan;
      String stretchBone = "", detBone = "", buriedBone = "", sinkBone = "";
   }

   static float[] posePose(String sp, SkinnedMesh m, WildlifeRig.Input in, Object unused) {
      float[] M = new float[m.names.length * 12];
      in.gaitStyle = gaitStyle(sp); // [anims] the species' gait (legs IK'd onto planted paws)
      WildlifeRig.pose(m, in, M);
      return M;
   }

   static int gaitStyle(String sp) {
      return switch (sp) {
         case "wolf", "coyote" -> WildlifeGait.CANID;
         case "cougar", "panther", "lion" -> WildlifeGait.CAT;
         case "cheetah" -> WildlifeGait.CHEETAH;
         case "grizzly", "black_bear", "polar_bear" -> WildlifeGait.BEAR;
         case "bison" -> WildlifeGait.BISON;
         default -> WildlifeGait.HOOF;
      };
   }

   static Result measure(SkinnedMesh m, Topo t, float[] M, boolean[] restBuried, boolean standing, boolean doBuried, float H) {
      Result r = new Result();
      int nv = m.vertexCount;
      float[] P = new float[nv * 3];
      float[] det = new float[nv];
      skin(m, M, P, det);
      for (float f : M) if (!Float.isFinite(f)) r.nan++;
      // stretch
      int ne = t.restLen.length;
      float[] s = new float[ne];
      int n = 0;
      int worstE = -1;
      for (int i = 0; i < ne; i++) {
         if (t.restLen[i] < 0.002F * H) continue;
         float l = dist(P, t.edges[i * 2], P, t.edges[i * 2 + 1]);
         float d = Math.abs(l / t.restLen[i] - 1.0F);
         s[n++] = d;
         if (d > r.stretchMax) {
            r.stretchMax = d;
            worstE = i;
         }
      }
      java.util.Arrays.sort(s, 0, n);
      r.stretch999 = s[Math.min(n - 1, (int)(n * 0.999))];
      if (worstE >= 0) r.stretchBone = m.names[dominant(m, t.edges[worstE * 2])];
      // collapse
      r.detMin = 9;
      for (int v = 0; v < nv; v++) {
         if (det[v] < r.detMin) {
            r.detMin = det[v];
            r.detBone = m.names[dominant(m, v)];
         }
      }
      // flipped
      int[] T = m.tris;
      float[] B = m.pos;
      for (int i = 0; i < T.length; i += 3) {
         int a = T[i], b = T[i + 1], c = T[i + 2];
         float[] nr = cross(B, a, b, c);
         float[] np = cross(P, a, b, c);
         float lr = len(nr), lp = len(np);
         if (lr < 1e-12F || lp < 1e-12F) continue;
         int o = dominant(m, a) * 12;
         float x = M[o] * nr[0] + M[o + 1] * nr[1] + M[o + 2] * nr[2];
         float y = M[o + 4] * nr[0] + M[o + 5] * nr[1] + M[o + 6] * nr[2];
         float z = M[o + 8] * nr[0] + M[o + 9] * nr[1] + M[o + 10] * nr[2];
         if ((x * np[0] + y * np[1] + z * np[2]) / (lr * lp) < -0.2F) r.flipped++;
      }
      // sink
      float minY = 9;
      int minV = 0;
      for (int v = 0; v < nv; v++) {
         if (P[v * 3 + 1] < minY) {
            minY = P[v * 3 + 1];
            minV = v;
         }
      }
      r.sink = standing ? Math.max(0, -minY) : 0;
      r.sinkBone = m.names[dominant(m, minV)]; // [anims] which part goes into the ground
      // buried
      if (doBuried) {
         float[] N = normals(m, t, P);
         boolean[] bu = buried(m, t, P, N, 0.004F * H);
         Map<String, Integer> by = new HashMap<>();
         for (int i = 0; i < bu.length; i++) {
            if (bu[i] && !restBuried[i]) {
               r.buried++;
               by.merge(m.names[dominant(m, t.sample[i])], 1, Integer::sum);
            }
         }
         r.buriedBone = by.entrySet().stream().sorted((x, y) -> y.getValue() - x.getValue()).limit(2)
            .map(e -> e.getKey() + ":" + e.getValue()).reduce((x, y) -> x + " " + y).orElse("");
      }
      return r;
   }

   static float[] cross(float[] P, int a, int b, int c) {
      float ux = P[b * 3] - P[a * 3], uy = P[b * 3 + 1] - P[a * 3 + 1], uz = P[b * 3 + 2] - P[a * 3 + 2];
      float vx = P[c * 3] - P[a * 3], vy = P[c * 3 + 1] - P[a * 3 + 1], vz = P[c * 3 + 2] - P[a * 3 + 2];
      return new float[]{uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx};
   }

   static float len(float[] v) {
      return (float)Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
   }

   static void worst(Result acc, Result r) {
      if (r.stretchMax > acc.stretchMax) { acc.stretchMax = r.stretchMax; acc.stretchBone = r.stretchBone; }
      acc.stretch999 = Math.max(acc.stretch999, r.stretch999);
      if (r.detMin < acc.detMin) { acc.detMin = r.detMin; acc.detBone = r.detBone; }
      acc.flipped = Math.max(acc.flipped, r.flipped);
      if (r.buried > acc.buried) { acc.buried = r.buried; acc.buriedBone = r.buriedBone; }
      if (r.sink > acc.sink) acc.sinkBone = r.sinkBone;
      acc.sink = Math.max(acc.sink, r.sink);
      acc.nan += r.nan;
   }

   // ---------------------------------------------------------------------------------------------- birds
   record BirdPoseDef(String name, byte phase, float cycle, boolean dead, boolean down, boolean water) {
   }

   static final BirdPoseDef[] BIRD = {
      new BirdPoseDef("fly_top", Flight.FLAP, 0.0F, false, false, false),
      new BirdPoseDef("fly_down", Flight.FLAP, 0.27F, false, false, false),
      new BirdPoseDef("fly_bottom", Flight.FLAP, 0.55F, false, false, false),
      new BirdPoseDef("fly_up", Flight.FLAP, 0.78F, false, false, false),
      new BirdPoseDef("takeoff", Flight.TAKEOFF, 0.3F, false, false, false),
      new BirdPoseDef("glide", Flight.GLIDE, 0, false, false, false),
      new BirdPoseDef("landing", Flight.LAND, 0.1F, false, false, false),
      new BirdPoseDef("drum", Flight.DRUM_A, 0, false, false, false),
      new BirdPoseDef("dead_fall", Flight.FALL, 0, true, false, false),
      new BirdPoseDef("dead_down", Flight.NONE, 0, true, true, false),
   };

   static WildlifeRig.Input birdInput(String sp, BirdPoseDef p) {
      BirdAnim.State st = new BirdAnim.State(12345, sp.equals("grouse"));
      float speed = p.phase == Flight.NONE || p.phase >= Flight.DRUM_A ? 0.0F : 0.8F;
      float t = 100.0F;
      for (int i = 0; i < 80; i++) {
         BirdAnim.step(st, p.phase, t, speed, 0, 0, p.dead, false, false, false);
         t += 1.0F;
      }
      if (p.dead) {
         for (int i = 0; i < 60; i++) {
            BirdAnim.step(st, p.phase, t, 0.0F, 0, 0.0F, true, p.down, p.water, p.down);
            t += 1.0F;
         }
      }
      try {
         java.lang.reflect.Field fc = BirdAnim.State.class.getDeclaredField("cycle");
         fc.setAccessible(true);
         fc.setFloat(st, p.cycle);
      } catch (ReflectiveOperationException e) {
         throw new RuntimeException(e);
      }
      BirdAnim.step(st, p.phase, t + 0.001F, speed, 0, 0, p.dead, p.down, p.water, p.down);
      WildlifeRig.Input in = new WildlifeRig.Input();
      in.age = t;
      in.bird = st;
      in.flying = BirdAnim.flying(p.phase);
      return in;
   }

   // ---------------------------------------------------------------------------------------------- main
   public static void main(String[] a) throws Exception {
      File dir = new File(a[0]);
      File out = new File(a[1]);
      out.mkdirs();
      boolean dump = a.length > 2 && a[2].equals("dump");
      String[] only = a.length > 3 ? a[3].split(",") : SPECIES;
      StringBuilder csv = new StringBuilder("species,lod,state,stretch_max,stretch_p999,stretch_bone,det_min,det_bone,flipped,buried,buried_bones,sink,nan,sink_bone\n");
      for (String sp : only) {
         for (String lod : new String[]{"ultra", "mid", "bal"}) {
            File f = new File(dir, sp + "_" + lod + ".fhsk");
            if (!f.exists()) continue;
            SkinnedMesh m = load(f);
            Topo t = topo(m);
            float H = m.meta[0];
            boolean full = lod.equals("ultra");
            // bind-pose buried baseline (legit overlaps: ears against the head, toes, mouth)
            float[] P0 = m.pos.clone();
            boolean[] restBuried = full ? buried(m, t, P0, normals(m, t, P0), 0.004F * H) : new boolean[t.sample.length];
            Result all = new Result();
            all.detMin = 9;
            List<String[]> rows = new ArrayList<>();
            if (m.bird) {
               for (State st : states(sp, m)) {
                  run(sp, lod, m, t, st, restBuried, full, dump, out, csv, all, H);
               }
               for (BirdPoseDef p : BIRD) {
                  if (p.name.equals("drum") && !sp.equals("grouse")) continue;
                  WildlifeRig.Input in = birdInput(sp, p);
                  float[] M = posePose(sp, m, in, null);
                  Result r = measure(m, t, M, restBuried, false, full, H);
                  r.state = p.name;
                  emit(csv, sp, lod, r);
                  worst(all, r);
                  if (dump && full) write(new File(out, sp + "_" + p.name + ".bin"), m, M);
               }
            } else {
               for (State st : states(sp, m)) {
                  run(sp, lod, m, t, st, restBuried, full, dump, out, csv, all, H);
               }
            }
            System.out.printf(Locale.ROOT, "%-11s %-5s tris %5d | stretch max %5.1f%% (%s) p99.9 %5.1f%% | det min %.2f (%s) | flipped %d | buried %d (%s) | sink %.3f | nan %d%n",
               sp, lod, m.tris.length / 3, 100 * all.stretchMax, all.stretchBone, 100 * all.stretch999, all.detMin, all.detBone, all.flipped, all.buried, all.buriedBone, all.sink, all.nan);
         }
      }
      Files.writeString(new File(out, "audit.csv").toPath(), csv.toString());
   }

   static void run(String sp, String lod, SkinnedMesh m, Topo t, State st, boolean[] restBuried, boolean full, boolean dump, File out,
      StringBuilder csv, Result all, float H) throws Exception {
      Result acc = new Result();
      acc.detMin = 9;
      acc.state = st.name;
      for (int k = 0; k < st.samples; k++) {
         WildlifeRig.Input in = new WildlifeRig.Input();
         in.age = 100 + 7.3F * k;
         st.setup.accept(in);
         in.limbSwing = (float)(2 * Math.PI * k / st.samples) / Math.max(0.1F, m.meta[5]);
         if (!m.bird && in.amount > 0.0F) {
            // [anims] one stride of the ground-locked gait at the state's speed (WildlifeGait), sampled evenly
            WildlifeGait.Info gi = WildlifeGait.info(m);
            WildlifeGait.Style gs = WildlifeGait.style(gaitStyle(sp));
            in.gait = true;
            in.gaitSpeed = Math.min(1.0F, in.amount) * 0.25F * (1.0F + in.w[WildlifeRig.FLEE]);
            in.gaitMix = gs.mix(WildlifeGait.froude(gi, in.gaitSpeed));
            in.gaitPhase = k / (float)st.samples;
            in.gaitStyle = gaitStyle(sp);
         }
         float[] M = posePose(sp, m, in, null);
         Result r = measure(m, t, M, restBuried, st.standing, full && k == st.samples / 2, H);
         worst(acc, r);
         if (dump && full && k == st.samples / 4) write(new File(out, sp + "_" + st.name + ".bin"), m, M);
      }
      emit(csv, sp, lod, acc);
      worst(all, acc);
   }

   static void emit(StringBuilder csv, String sp, String lod, Result r) {
      csv.append(String.format(Locale.ROOT, "%s,%s,%s,%.4f,%.4f,%s,%.3f,%s,%d,%d,%s,%.4f,%d,%s%n", sp, lod, r.state, r.stretchMax, r.stretch999,
         r.stretchBone, r.detMin, r.detBone, r.flipped, r.buried, r.buriedBone, r.sink, r.nan, r.sinkBone));
   }

   /** posed mesh for the contact sheets: nv, nt, pos, nrm (posed), uv, tris (little endian) */
   static void write(File f, SkinnedMesh m, float[] M) throws Exception {
      int nv = m.vertexCount;
      float[] P = new float[nv * 3];
      skin(m, M, P, null);
      float[] N = new float[nv * 3];
      float[] W = m.weight;
      for (int v = 0; v < nv; v++) {
         float nx = m.nrm[v * 3], ny = m.nrm[v * 3 + 1], nz = m.nrm[v * 3 + 2];
         float x = 0, y = 0, z = 0;
         for (int k = 0; k < 4; k++) {
            float w = W[v * 4 + k];
            if (w <= 0) continue;
            int o = (m.bone[v * 4 + k] & 255) * 12;
            x += w * (M[o] * nx + M[o + 1] * ny + M[o + 2] * nz);
            y += w * (M[o + 4] * nx + M[o + 5] * ny + M[o + 6] * nz);
            z += w * (M[o + 8] * nx + M[o + 9] * ny + M[o + 10] * nz);
         }
         float l = (float)Math.sqrt(x * x + y * y + z * z) + 1e-12F;
         N[v * 3] = x / l;
         N[v * 3 + 1] = y / l;
         N[v * 3 + 2] = z / l;
      }
      ByteBuffer bb = ByteBuffer.allocate(8 + nv * 32 + m.tris.length * 4).order(ByteOrder.LITTLE_ENDIAN);
      bb.putInt(nv).putInt(m.tris.length / 3);
      for (float x : P) bb.putFloat(x);
      for (float x : N) bb.putFloat(x);
      for (int i = 0; i < nv * 2; i++) bb.putFloat(m.uv[i]);
      for (int i : m.tris) bb.putInt(i);
      try (DataOutputStream o = new DataOutputStream(new FileOutputStream(f))) {
         o.write(bb.array());
      }
   }
}
