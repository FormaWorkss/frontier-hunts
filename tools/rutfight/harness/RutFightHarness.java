import com.formaworks.frontierhunts.client.CubeAnimalData;
import com.formaworks.frontierhunts.client.McAnimalPose;
import com.formaworks.frontierhunts.client.rutfight.BoxFightModels;
import com.formaworks.frontierhunts.hunting.AntlerGeometry;
import com.formaworks.frontierhunts.hunting.DeerAnimator;
import com.formaworks.frontierhunts.hunting.DeerMeshData;
import com.formaworks.frontierhunts.hunting.DeerSkeleton;
import com.formaworks.frontierhunts.hunting.DeerTraits;
import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.formaworks.frontierhunts.hunting.rutfight.FightAim;
import com.formaworks.frontierhunts.hunting.rutfight.FightFit;
import com.formaworks.frontierhunts.hunting.rutfight.FightModel;
import com.formaworks.frontierhunts.hunting.rutfight.FightPose;
import com.formaworks.frontierhunts.hunting.rutfight.UltraFightModels;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * [rutfight] Offline check of locked-antler contact for every species x graphics preset x antler size, using the
 * real meshes / cube models / skeleton clips from the mod jar and the real animation code (DeerAnimator with the fight
 * layer and head reach, McAnimalPose for Classic).
 *
 * <p>For each pair the contact fit gives the body distance; both animals are then posed exactly the way the client
 * does in game (FightAim target -> animator), the geometry is built the way WhitetailRenderer draws it, B is placed
 * facing A at the distance, and the racks are measured: the gap between the two racks along the shared axis (negative
 * = pressed into each other / interlocked), the closest antler-to-antler distance, and the clearance between each rack
 * and the rival's skull. Also checked with the clients' worst cases: a 0.12 block interpolation error, twist and
 * heave, a half-block ground step, and mismatched bucks.</p>
 */
public final class RutFightHarness {
   enum Preset { CLASSIC, BALANCED, ULTRA }

   static final int BODY = 0, SKULL = 1, ANTLER = 2;

   /** Triangles of one posed animal in its entity space (+z forward). */
   static final class Geo {
      final List<float[]> tris = new ArrayList<>();
      final List<Integer> tags = new ArrayList<>();
      float slideX, slideZ, residualY;

      void add(float[] a, float[] b, float[] c, int tag) {
         this.tris.add(new float[]{a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2]});
         this.tags.add(tag);
      }

      float[] points(int tag) {
         FightModel.Points p = new FightModel.Points();
         for (int i = 0; i < this.tris.size(); i++) {
            if (this.tags.get(i) == tag) {
               float[] t = this.tris.get(i);
               p.triangle(new Vector3f(t[0], t[1], t[2]), new Vector3f(t[3], t[4], t[5]), new Vector3f(t[6], t[7], t[8]));
            }
         }
         return p.toArray();
      }

      /** Surface samples with outward face normals (x y z nx ny nz), for the penetration depth. */
      float[] pointsN(int tag) {
         List<float[]> out = new ArrayList<>();
         for (int i = 0; i < this.tris.size(); i++) {
            if (this.tags.get(i) != tag) continue;
            float[] t = this.tris.get(i);
            float ax = t[3] - t[0], ay = t[4] - t[1], az = t[5] - t[2], bx = t[6] - t[0], by = t[7] - t[1], bz = t[8] - t[2];
            float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
            float len = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1e-9f) continue;
            nx /= len; ny /= len; nz /= len;
            float l = Math.max((float)Math.sqrt(ax * ax + ay * ay + az * az), (float)Math.sqrt(bx * bx + by * by + bz * bz));
            int k = Math.max(1, Math.min(30, (int)Math.ceil(l / FightModel.SPACING)));
            for (int u = 0; u <= k; u++) for (int v = 0; v <= k - u; v++) {
               float fu = (float)u / k, fv = (float)v / k;
               out.add(new float[]{t[0] + ax * fu + bx * fv, t[1] + ay * fu + by * fv, t[2] + az * fu + bz * fv, nx, ny, nz});
            }
         }
         float[] r = new float[out.size() * 6];
         for (int i = 0; i < out.size(); i++) System.arraycopy(out.get(i), 0, r, i * 6, 6);
         return r;
      }
   }

   /** B-frame samples with normals into A's frame at distance d. */
   static float[] toAN(float[] p, float dy, float d) {
      float[] o = new float[p.length];
      for (int i = 0; i + 5 < p.length; i += 6) {
         o[i] = -p[i]; o[i + 1] = p[i + 1] + dy; o[i + 2] = d - p[i + 2];
         o[i + 3] = -p[i + 3]; o[i + 4] = p[i + 4]; o[i + 5] = -p[i + 5];
      }
      return o;
   }

   /** Deepest point of one rack inside the other (distance behind the nearest outward surface), both ways. */
   static float penetration(float[] a, float[] b) {
      return Math.max(depthInto(a, b), depthInto(b, a));
   }

   static float depthInto(float[] p, float[] q) {
      float cell = 0.06F;
      java.util.Map<Long, List<float[]>> grid = new java.util.HashMap<>();
      for (int i = 0; i + 5 < q.length; i += 6) {
         long k = pack((int)Math.floor(q[i] / cell), (int)Math.floor(q[i + 1] / cell), (int)Math.floor(q[i + 2] / cell));
         grid.computeIfAbsent(k, x -> new ArrayList<>()).add(new float[]{q[i], q[i + 1], q[i + 2], q[i + 3], q[i + 4], q[i + 5]});
      }
      float worst = 0.0F;
      for (int i = 0; i + 5 < p.length; i += 6) {
         int cx = (int)Math.floor(p[i] / cell), cy = (int)Math.floor(p[i + 1] / cell), cz = (int)Math.floor(p[i + 2] / cell);
         float best = cell * cell;
         float[] nq = null;
         for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            List<float[]> c = grid.get(pack(cx + dx, cy + dy, cz + dz));
            if (c == null) continue;
            for (float[] o : c) {
               float x = p[i] - o[0], y = p[i + 1] - o[1], z = p[i + 2] - o[2];
               float s2 = x * x + y * y + z * z;
               if (s2 < best) { best = s2; nq = o; }
            }
         }
         if (nq != null) {
            float depth = -((p[i] - nq[0]) * nq[3] + (p[i + 1] - nq[1]) * nq[4] + (p[i + 2] - nq[2]) * nq[5]);
            worst = Math.max(worst, depth);
         }
      }
      return worst;
   }

   static long pack(int x, int y, int z) {
      return ((long)x & 2097151L) << 42 | ((long)y & 2097151L) << 21 | (long)z & 2097151L;
   }

   static float[] ent(float sx, float sy, float sz, Vector3f p) {
      return new float[]{-p.x * sx, p.y * sy, -p.z * sz};
   }

   // ------------------------------------------------------------------ renderer-equivalent geometry

   static Geo ultra(DeerTraits t, Matrix4f[] model, Matrix4f[] skin) {
      Geo g = new Geo();
      GameSpecies s = t.species();
      float rx = s == GameSpecies.WHITETAIL ? UltraFightModels.WHITETAIL_REAL_XZ : 1.0F;
      float ry = s == GameSpecies.WHITETAIL ? UltraFightModels.WHITETAIL_REAL_Y : 1.0F;
      float sx = t.frameWidth() * rx, sy = t.frameHeight() * ry, sz = t.frameLength() * rx;
      DeerMeshData m = DeerMeshData.of(s);
      float[] pos = new float[m.vertices * 3], nrm = new float[m.vertices * 3];
      float rack = s.meshAntlers() && t.buck() ? Math.max(0.55F, Math.min(1.12F, t.rackScale())) : 1.0F;
      m.skin(0, skin, t.neckGirth(), t.headScale(), rack, pos, nrm);
      int[] f = m.faces(0, t.buck());
      Vector3f a = new Vector3f(), b = new Vector3f(), c = new Vector3f();
      for (int i = 0; i + 2 < f.length; i += 3) {
         int va = f[i], vb = f[i + 1], vc = f[i + 2];
         boolean antler = ((m.region[va] | m.region[vb] | m.region[vc]) & DeerMeshData.REGION_ANTLER) != 0;
         int tag = antler ? ANTLER : (m.headWeight[va] > 0.5F && m.headWeight[vb] > 0.5F && m.headWeight[vc] > 0.5F ? SKULL : BODY);
         a.set(pos[va * 3], pos[va * 3 + 1], pos[va * 3 + 2]);
         b.set(pos[vb * 3], pos[vb * 3 + 1], pos[vb * 3 + 2]);
         c.set(pos[vc * 3], pos[vc * 3 + 1], pos[vc * 3 + 2]);
         g.add(ent(sx, sy, sz, a), ent(sx, sy, sz, b), ent(sx, sy, sz, c), tag);
      }
      if (t.buck() && s.antlers == GameSpecies.Antlers.PROCEDURAL_WHITETAIL) {
         // DeerDraw.antlers: pose * head model * antler frame
         AntlerGeometry ag = AntlerGeometry.of(t, 8);
         DeerSkeleton sk = DeerSkeleton.of(s);
         Matrix4f A = new Matrix4f(model[sk.head]).mul(sk.antlerFrame);
         for (int i = 0; i + 2 < ag.tris.length; i += 3) {
            A.transformPosition(ag.pos[ag.tris[i] * 3], ag.pos[ag.tris[i] * 3 + 1], ag.pos[ag.tris[i] * 3 + 2], a);
            A.transformPosition(ag.pos[ag.tris[i + 1] * 3], ag.pos[ag.tris[i + 1] * 3 + 1], ag.pos[ag.tris[i + 1] * 3 + 2], b);
            A.transformPosition(ag.pos[ag.tris[i + 2] * 3], ag.pos[ag.tris[i + 2] * 3 + 1], ag.pos[ag.tris[i + 2] * 3 + 2], c);
            g.add(ent(sx, sy, sz, a), ent(sx, sy, sz, b), ent(sx, sy, sz, c), ANTLER);
         }
      }
      return g;
   }

   static void cubes(Geo g, DeerTraits t, CubeAnimalData d, Matrix4f[] skin, int from, int to, int headBone, int forceTag) {
      int[][] faces = {{0, 3, 2, 1}, {5, 6, 7, 4}, {4, 7, 3, 0}, {1, 2, 6, 5}, {3, 7, 6, 2}, {4, 0, 1, 5}};
      float sx = t.frameWidth(), sy = t.frameHeight(), sz = t.frameLength();
      Vector3f[] c = new Vector3f[8];
      for (int k = from; k < to; k++) {
         int bone = d.bones[k];
         if (bone < 0 || bone >= skin.length) continue;
         Matrix4f m = skin[bone];
         int o = k * d.stride;
         float[] D = d.data;
         for (int j = 0; j < 8; j++) {
            float u = (j == 1 || j == 2 || j == 5 || j == 6) ? 1 : 0, v = (j == 2 || j == 3 || j == 6 || j == 7) ? 1 : 0, w = j >= 4 ? 1 : 0;
            c[j] = m.transformPosition(new Vector3f(D[o] + D[o + 3] * u + D[o + 6] * v + D[o + 9] * w, D[o + 1] + D[o + 4] * u + D[o + 7] * v + D[o + 10] * w,
               D[o + 2] + D[o + 5] * u + D[o + 8] * v + D[o + 11] * w));
         }
         int tag = forceTag >= 0 ? forceTag : (bone == headBone ? SKULL : BODY);
         for (int[] fc : faces) {
            float[] p0 = ent(sx, sy, sz, c[fc[0]]), p1 = ent(sx, sy, sz, c[fc[1]]), p2 = ent(sx, sy, sz, c[fc[2]]), p3 = ent(sx, sy, sz, c[fc[3]]);
            g.add(p0, p1, p2, tag);
            g.add(p0, p2, p3, tag);
         }
      }
   }

   static int antlerStart(GameSpecies s, boolean vanilla) {
      return vanilla ? (s == GameSpecies.ELK ? 22 : s == GameSpecies.MOOSE ? 23 : 21) : (s == GameSpecies.ELK ? 38 : s == GameSpecies.MOOSE ? 41 : 37);
   }

   static Geo boxes(DeerTraits t, Matrix4f[] skin, boolean vanilla) {
      Geo g = new Geo();
      GameSpecies s = t.species();
      CubeAnimalData d = CubeAnimalData.of(s, vanilla);
      int start = Math.min(antlerStart(s, vanilla), d.count());
      int head = s == GameSpecies.WHITETAIL ? 10 : 7;
      cubes(g, t, d, skin, 0, start, head, -1);
      if (t.buck()) cubes(g, t, d, BoxFightModels.rackSkin(t, skin, vanilla), start, d.count(), head, ANTLER);
      return g;
   }

   // ------------------------------------------------------------------ posing exactly like the client

   static FightModel model(Preset p, DeerTraits t) {
      return p == Preset.ULTRA ? UltraFightModels.of(t) : BoxFightModels.of(t, p == Preset.CLASSIC);
   }

   /** Poses one fighter with the given head target and builds its rendered geometry. */
   static Geo posed(Preset p, DeerTraits t, Matrix4f target) {
      DeerAnimator.Input in = new DeerAnimator.Input();
      in.buck = true;
      in.fightLower = 1.0F;
      in.fightHead = target;
      in.fightReach = 1.0F;
      if (p == Preset.CLASSIC) {
         McAnimalPose mp = McAnimalPose.still(t.species(), in, t.frameLength());
         return boxes(t, mp.skin, true);
      }
      DeerAnimator an = new DeerAnimator(t.species());
      for (int i = 0; i < 60; i++) {
         in.time = 5.0F + i * 0.05F;
         an.update(in);
      }
      Geo g = p == Preset.ULTRA ? ultra(t, an.model, an.skin) : boxes(t, an.skin, false);
      // the renderer slides the body by the horizontal part of what the neck could not reach (RutFightClient.bodySlide)
      float rs = p == Preset.ULTRA && t.species() == GameSpecies.WHITETAIL ? UltraFightModels.WHITETAIL_REAL_XZ : 1.0F;
      float ex = -an.fightResidual.x * t.frameWidth() * rs, ez = -an.fightResidual.z * t.frameLength() * rs;
      g.slideX = ex;
      g.slideZ = ez;
      g.residualY = an.fightResidual.y * t.frameHeight() * (p == Preset.ULTRA && t.species() == GameSpecies.WHITETAIL ? UltraFightModels.WHITETAIL_REAL_Y : 1.0F);
      for (float[] tri : g.tris) for (int k = 0; k < 3; k++) { tri[k * 3] += ex; tri[k * 3 + 2] += ez; }
      return g;
   }

   static float[] toA(float[] p, float dy, float d) {
      return FightFit.toA(p, dy, d);
   }

   /** Rack gap along the axis: distance minus the deepest lateral-overlapping pair (negative = pressed together). */
   static float gap(float[] a, float[] bInA) {
      float best = Float.MAX_VALUE;
      float e2 = FightFit.EPS * FightFit.EPS;
      java.util.Map<Long, List<float[]>> grid = new java.util.HashMap<>();
      for (int i = 0; i + 2 < bInA.length; i += 3) {
         long k = (long)Math.floor(bInA[i] / FightFit.EPS) << 32 | ((long)Math.floor(bInA[i + 1] / FightFit.EPS) & 0xffffffffL);
         grid.computeIfAbsent(k, x -> new ArrayList<>()).add(new float[]{bInA[i], bInA[i + 1], bInA[i + 2]});
      }
      for (int i = 0; i + 2 < a.length; i += 3) {
         int cx = (int)Math.floor(a[i] / FightFit.EPS), cy = (int)Math.floor(a[i + 1] / FightFit.EPS);
         for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
            List<float[]> cell = grid.get((long)(cx + dx) << 32 | ((long)(cy + dy) & 0xffffffffL));
            if (cell == null) continue;
            for (float[] q : cell) {
               float rx = a[i] - q[0], ry = a[i + 1] - q[1];
               if (rx * rx + ry * ry <= e2) best = Math.min(best, q[2] - a[i + 2]);
            }
         }
      }
      return best;
   }

   record Result(float distance, float gap /* penetration depth */, float tip, float skull, float bodyShiftA, float bodyShiftB, float vA, float vB) {}

   /** Builds both posed fighters at an actual distance / ground step and measures the racks. */
   static Result measure(Preset p, DeerTraits ta, DeerTraits tb, float error, float dy, float roll, float heave, float clashAge, Geo[] keep) {
      FightModel ma = model(p, ta), mb = model(p, tb);
      FightFit fit = FightFit.solve(ma, mb, dy);
      float d = fit.distance + error;
      float scale = FightAim.twistScale(fit);
      float jolt = FightPose.clashLift(clashAge, Math.max(0.6F, fit.lever * 1.5F));
      Matrix4f ha = new Matrix4f(), hb = new Matrix4f();
      Geo ga = null, gb = null;
      float resA = 0.0F, resB = 0.0F;
      // the client poses each rack with the rival's last vertical shortfall added, so the pair settles level in a frame or two
      for (int frame = 0; frame < 3; frame++) {
         FightAim.target(ma, fit, true, d, dy, roll * scale, heave * scale, jolt - resB, ha);
         FightAim.target(mb, fit, false, d, dy, roll * scale, heave * scale, jolt - resA, hb);
         ga = posed(p, ta, ha);
         gb = posed(p, tb, hb);
         resA = ga.residualY;
         resB = gb.residualY;
      }
      float da = ga.slideZ, db = gb.slideZ;
      float[] aAnt = ga.points(ANTLER), bAnt = gb.points(ANTLER), aSk = ga.points(SKULL), bSk = gb.points(SKULL);
      float[] bAntA = toA(bAnt, dy, d), bSkA = toA(bSk, dy, d);
      float pen = penetration(ga.pointsN(ANTLER), toAN(gb.pointsN(ANTLER), dy, d));
      float tip = FightFit.minDistance(aAnt, bAntA, 0.5F);
      float skull = Math.min(FightFit.minDistance(aAnt, bSkA, 0.5F), Math.min(FightFit.minDistance(bAntA, aSk, 0.5F), FightFit.minDistance(aSk, bSkA, 0.5F)));
      if (keep != null) { keep[0] = ga; keep[1] = gb; }
      return new Result(d, pen, tip, skull, da, db, resA, resB);
   }

   // ------------------------------------------------------------------ preview

   static int color(int tag, boolean b) {
      return tag == ANTLER ? (b ? 0xD8E4F0 : 0xF0E2C2) : tag == SKULL ? (b ? 0x5F6F86 : 0xA06A40) : (b ? 0x4E5D73 : 0x8A5A36);
   }

   static void draw(Raster r, Geo g, boolean isB, float d, float dy, float ignored, float ox, float oy, float ppb, int view) {
      float shift = 0.0F;
      for (int i = 0; i < g.tris.size(); i++) {
         float[] t = g.tris.get(i).clone();
         for (int k = 0; k < 3; k++) {
            if (isB) { t[k * 3] = -t[k * 3]; t[k * 3 + 1] += dy; t[k * 3 + 2] = d - shift - t[k * 3 + 2]; }
            else t[k * 3 + 2] += shift;
         }
         float[][] q = new float[3][];
         for (int k = 0; k < 3; k++) {
            float x = t[k * 3], y = t[k * 3 + 1], z = t[k * 3 + 2];
            q[k] = view == 0 ? new float[]{ox + z * ppb, oy - y * ppb, -x} : new float[]{ox + z * ppb, oy + x * ppb, y};
         }
         float ax = t[3] - t[0], ay = t[4] - t[1], az = t[5] - t[2], bx = t[6] - t[0], by = t[7] - t[1], bz = t[8] - t[2];
         float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
         float len = (float)Math.sqrt(nx * nx + ny * ny + nz * nz) + 1e-9f;
         float shade = 0.42f + 0.58f * Math.abs((nx * 0.35f + ny * 0.8f + nz * 0.48f) / len);
         r.tri(q[0], q[1], q[2], color(g.tags.get(i), isB), shade);
      }
   }

   static void preview(Preset p, String file, float roll, float heave) throws Exception {
      int W = 2000, H = 1500;
      Raster r = new Raster(W, H, 0xEEF0EC);
      int row = 0;
      for (GameSpecies s : GameSpecies.values()) {
         DeerTraits ta = new DeerTraits(s, true, 72, 60, 75, 90, 11, 0, 60);
         DeerTraits tb = new DeerTraits(s, true, 44, 45, 70, 60, 23, 0, 40);
         Geo[] keep = new Geo[2];
         Result res = measure(p, ta, tb, 0.0F, 0.0F, roll, heave, -1.0F, keep);
         // fit the pair (body lengths + distance) into a 1000 px panel
         float span = res.distance() + (s == GameSpecies.WHITETAIL ? 2.6F : 4.6F);
         float ppb = Math.min(1000.0F / span, 380.0F / (s == GameSpecies.WHITETAIL ? 1.9F : 3.0F));
         float cx = 520 - res.distance() * ppb * 0.5F;
         float baseY = row * 500 + 470;
         draw(r, keep[0], false, res.distance(), 0, 0, cx, baseY, ppb, 0);
         draw(r, keep[1], true, res.distance(), 0, 0, cx, baseY, ppb, 0);
         r.hline((int)baseY, 0x9AA090);
         float tx = 1500 - res.distance() * ppb * 0.5F;
         draw(r, keep[0], false, res.distance(), 0, 0, tx, baseY - 230, ppb, 1);
         draw(r, keep[1], true, res.distance(), 0, 0, tx, baseY - 230, ppb, 1);
         r.text(String.format("%s %s - side view | top view   distance %.2f   closest antlers %.3f   rack-in-rack %.3f   skull clearance %.3f",
            p, s, res.distance(), res.tip(), res.gap(), res.skull()), 20, row * 500 + 22);
         row++;
      }
      for (int x = 1040; x < 1044; x++) r.vline(x, 0xC8CCC4);
      r.save(file);
   }

   public static void main(String[] args) throws Exception {
      String out = args.length > 0 ? args[0] : ".";
      int fails = 0, checks = 0;
      int[][] sizes = {{30, 40}, {42, 70}, {60, 90}, {100, 160}};
      System.out.println("preset   species   A(age/genes) B(age/genes) case            dist   rack-in-rack closest  skull  slideA slideB  vresA vresB  result");
      for (Preset p : Preset.values()) {
         for (GameSpecies s : GameSpecies.values()) {
            List<DeerTraits[]> pairs = new ArrayList<>();
            for (int[] z : sizes) pairs.add(new DeerTraits[]{new DeerTraits(s, true, z[0], 50, 75, z[1], 7, 0, 60), new DeerTraits(s, true, z[0], 50, 75, z[1], 1234567, 0, 30)});
            pairs.add(new DeerTraits[]{new DeerTraits(s, true, 30, 30, 60, 40, 5, 0, 60), new DeerTraits(s, true, 100, 90, 90, 160, 99, 0, 30)});
            pairs.add(new DeerTraits[]{new DeerTraits(s, true, 42, 60, 75, 110, -77, 0, 60), new DeerTraits(s, true, 66, 40, 80, 50, 31337, 0, 30)});
            for (DeerTraits[] pr : pairs) {
               Object[][] cases = {
                  {"locked", 0F, 0F, 0F, 0F, 0F}, {"interp +0.12", 0.12F, 0F, 0F, 0F, 0F}, {"interp -0.08", -0.08F, 0F, 0F, 0F, 0F},
                  {"twist+heave", 0F, 0F, 0.3F, 0.09F, -1F}, {"twist-", 0F, 0F, -0.3F, -0.09F, -1F}, {"clash jolt", 0F, 0F, 0F, 0F, 0.1F},
                  {"step +0.5", 0F, 0.5F, 0.15F, 0F, -1F}, {"step -0.3", 0F, -0.3F, -0.15F, 0.05F, -1F}};
               for (Object[] c : cases) {
                  Result res = measure(p, pr[0], pr[1], (Float)c[1], (Float)c[2], (Float)c[3], (Float)c[4], (Float)c[5], null);
                  boolean ok = res.tip <= 0.05F && res.gap <= 0.09F && res.skull > 0.0F;
                  checks++;
                  if (!ok) fails++;
                  System.out.printf("%-8s %-9s %3d/%-3d      %3d/%-3d      %-14s %5.2f  %6.3f       %5.3f  %5.3f  %+5.2f  %+5.2f  %+5.2f %+5.2f  %s%n", p, s,
                     pr[0].ageMonths(), pr[0].rackGenes(), pr[1].ageMonths(), pr[1].rackGenes(), c[0], res.distance, res.gap, res.tip, res.skull, res.bodyShiftA, res.bodyShiftB, res.vA, res.vB, ok ? "ok" : "FAIL");
               }
            }
         }
      }
      System.out.println(checks + " checks, " + fails + " failed");
      for (Preset p : Preset.values()) {
         preview(p, out + "/rutfight_" + p.name().toLowerCase() + ".png", 0.0F, 0.0F);
      }
      preview(Preset.ULTRA, out + "/rutfight_ultra_twist.png", 0.3F, 0.06F);
      System.exit(fails == 0 ? 0 : 1);
   }
}
