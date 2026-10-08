import com.formaworks.frontierhunts.client.CubeAnimalData;
import com.formaworks.frontierhunts.client.McAnimalPose;
import com.formaworks.frontierhunts.client.rutfight.BoxFightModels;
import com.formaworks.frontierhunts.hunting.AntlerGeometry;
import com.formaworks.frontierhunts.hunting.DeerAnimator;
import com.formaworks.frontierhunts.hunting.DeerMeshData;
import com.formaworks.frontierhunts.hunting.DeerSkeleton;
import com.formaworks.frontierhunts.hunting.DeerTraits;
import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.formaworks.frontierhunts.hunting.rutfight.FightModel;
import com.formaworks.frontierhunts.hunting.rutfight.UltraFightModels;
import com.formaworks.frontierhunts.sign.work.SignAct;
import com.formaworks.frontierhunts.sign.work.SignGeometry;
import com.formaworks.frontierhunts.sign.work.SignMotion;
import com.formaworks.frontierhunts.sign.work.SignPlan;
import com.formaworks.frontierhunts.sign.work.SignPose;
import com.formaworks.frontierhunts.sign.work.SignSeason;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * [deersign] Offline checks + previews of a buck making sign, through the mod's real code: SignPlan (the walk / settle /
 * act state machine, decisions, timings), SignSeason (calendar), SignMotion (strokes), SignPose / DeerAnimator /
 * McAnimalPose (the postures on the skeletal rig and the Vanilla box model), SignGeometry (antlers meeting the bark,
 * nose reaching the licking branch) with the real Ultra meshes / antler geometry and the real Vanilla cubes.
 */
public final class SignHarness {
   static final int BODY = 0, SKULL = 1, ANTLER = 2, TRUNK = 3, BAND = 4, MARK = 5, GROUND = 6;
   static int checks, fails;

   static void check(boolean ok, String what) {
      checks++;
      if (!ok) {
         fails++;
      }
      System.out.println((ok ? "  ok   " : "  FAIL ") + what);
   }

   static final class Geo {
      final List<float[]> tris = new ArrayList<>();
      final List<Integer> tags = new ArrayList<>();
      float slideX, slideZ;

      void add(float[] a, float[] b, float[] c, int tag) {
         this.tris.add(new float[]{a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2]});
         this.tags.add(tag);
      }

      void shift(float dx, float dz) {
         for (float[] t : this.tris) for (int k = 0; k < 3; k++) { t[k * 3] += dx; t[k * 3 + 2] += dz; }
      }

      void addAll(Geo o) {
         this.tris.addAll(o.tris);
         this.tags.addAll(o.tags);
      }
   }

   static float[] ent(float sx, float sy, float sz, Vector3f p) {
      return new float[]{-p.x * sx, p.y * sy, -p.z * sz};
   }

   // ------------------------------------------------------------------ renderer-equivalent geometry (as rutfight's harness)

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

   static Geo boxes(DeerTraits t, Matrix4f[] skin) {
      Geo g = new Geo();
      GameSpecies s = t.species();
      CubeAnimalData d = CubeAnimalData.of(s, true);
      int start = Math.min(s == GameSpecies.ELK ? 22 : s == GameSpecies.MOOSE ? 23 : 21, d.count());
      int head = s == GameSpecies.WHITETAIL ? 10 : 7;
      cubes(g, t, d, skin, 0, start, head, -1);
      if (t.buck()) cubes(g, t, d, BoxFightModels.rackSkin(t, skin, true), start, d.count(), head, ANTLER);
      return g;
   }

   // ------------------------------------------------------------------ posing like the game

   static final class Posed {
      Geo geo;
      Matrix4f head;
      DeerAnimator an;
      McAnimalPose mp;
      float[] hooves; // entity y of the 4 hoof joints (rig)
      float contactY = Float.NaN, slide = Float.NaN, spread;
   }

   static DeerAnimator.Input input(int phase, float age, float dur, int seed) {
      DeerAnimator.Input in = new DeerAnimator.Input();
      in.buck = true;
      in.signPhase = phase;
      in.signAge = age;
      in.signDur = dur;
      in.signSeed = seed;
      in.signW = phase == 0 ? 0.0F : 1.0F;
      SignPose.apply(in);
      return in;
   }

   /** Ultra pose: animator settled on the input (head target from {@code target}), rig hooves measured. */
   static Posed ultraPose(DeerTraits t, DeerAnimator.Input in, Matrix4f target) {
      DeerAnimator an = new DeerAnimator(t.species());
      in.fightHead = target;
      in.fightReach = target != null ? 1.0F : 0.0F;
      float age0 = in.signAge;
      for (int i = 0; i < 80; i++) {
         in.time = 5.0F + i * 0.05F;
         an.update(in);
      }
      in.signAge = age0;
      an.update(in);
      Posed p = new Posed();
      p.an = an;
      DeerSkeleton sk = DeerSkeleton.of(t.species());
      p.head = new Matrix4f(an.model[sk.head]);
      p.geo = ultra(t, an.model, an.skin);
      FightModel m = UltraFightModels.of(t);
      p.hooves = new float[4];
      for (int k = 0; k < 4; k++) {
         p.hooves[k] = an.model[sk.legs[k][4]].m31() * m.sy;
      }
      return p;
   }

   static Posed vanillaPose(DeerTraits t, DeerAnimator.Input in) {
      McAnimalPose mp = new McAnimalPose(t.species());
      for (int i = 0; i < 80; i++) {
         in.time = 5.0F + i * 0.05F;
         mp.update(in, t.frameLength(), 0.05F);
      }
      Posed p = new Posed();
      p.mp = mp;
      p.head = new Matrix4f(mp.head);
      p.geo = boxes(t, mp.skin);
      return p;
   }

   /** Rubbing pose against a trunk at (0, cz) with feedback on height like SignWorkClient (Ultra or Vanilla). */
   static Posed rubPose(DeerTraits t, boolean vanilla, float v, float roll, float yaw, float wantY, SignGeometry.Trunk trunk, int phase) {
      FightModel m = vanilla ? BoxFightModels.of(t, true) : UltraFightModels.of(t);
      float corr = 0.0F;
      Posed p = null;
      float amp = 0.0F;
      for (int it = 0; it < 14; it++) {
         DeerAnimator.Input in = input(phase, 2.0F, 8.0F, 7);
         float ts = SignGeometry.twistScale(t.species());
         if (vanilla) {
            in.signPitch = Math.max(-1.0F, Math.min(1.0F, -corr * SignGeometry.BOX_NOD_PER_BLOCK));
            in.signYaw = (yaw + roll * 0.5F) * ts;
            p = vanillaPose(t, in);
         } else {
            Matrix4f tg = SignGeometry.rubTarget(m, t.species(), corr / m.sy, roll * ts, yaw * ts, v * SignGeometry.strokeNod(t.species()), new Matrix4f());
            p = ultraPose(t, in, tg);
         }
         SignGeometry.Contact c = SignGeometry.contact(m, p.head, trunk, 1, new SignGeometry.Contact());
         if (!c.ok()) break;
         p.contactY = c.y;
         p.slide = c.slide;
         p.spread = c.spread;
         corr = Math.max(-0.45F, Math.min(0.45F, corr + (wantY - c.y) * 0.5F));
         if (vanilla) corr = Math.max(-1.0F / SignGeometry.BOX_NOD_PER_BLOCK, Math.min(1.0F / SignGeometry.BOX_NOD_PER_BLOCK, corr));
      }
      if (p != null && !Float.isNaN(p.slide)) {
         p.geo.shift(0.0F, p.slide + 0.012F);
      }
      return p;
   }

   static float dropPerPx(DeerTraits t) {
      DeerAnimator.Input in = new DeerAnimator.Input();
      in.buck = true;
      in.fightLower = 1.0F;
      float y0 = McAnimalPose.still(t.species(), in, 1.0F).head.m31();
      in.signDrop = 1.0F;
      float y1 = McAnimalPose.still(t.species(), in, 1.0F).head.m31();
      return (y0 - y1) * t.frameHeight();
   }

   // ------------------------------------------------------------------ scenery

   static Geo cylinder(float cx, float cz, float r, float y0, float y1, float bandLo, float bandHi, float bandCentre) {
      Geo g = new Geo();
      int n = 20;
      int rows = 24;
      for (int j = 0; j < rows; j++) {
         float ya = y0 + (y1 - y0) * j / rows, yb = y0 + (y1 - y0) * (j + 1) / rows;
         for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            float[] p0 = {cx + r * (float)Math.sin(a0), ya, cz + r * (float)Math.cos(a0)};
            float[] p1 = {cx + r * (float)Math.sin(a1), ya, cz + r * (float)Math.cos(a1)};
            float[] p2 = {cx + r * (float)Math.sin(a1), yb, cz + r * (float)Math.cos(a1)};
            float[] p3 = {cx + r * (float)Math.sin(a0), yb, cz + r * (float)Math.cos(a0)};
            double mid = (a0 + a1) * 0.5;
            boolean front = Math.cos(mid - bandCentre) > 0.35;
            int tag = front && ya + 0.001F >= bandLo && yb - 0.001F <= bandHi + (y1 - y0) / rows ? BAND : TRUNK;
            g.add(p0, p1, p2, tag);
            g.add(p0, p2, p3, tag);
         }
      }
      return g;
   }

   static Geo block(float cx, float cz, float y0, float y1, float bandLo, float bandHi) {
      Geo g = new Geo();
      int rows = 16;
      for (int j = 0; j < rows; j++) {
         float ya = y0 + (y1 - y0) * j / rows, yb = y0 + (y1 - y0) * (j + 1) / rows;
         float[][] c = {{cx - 0.5F, cz - 0.5F}, {cx + 0.5F, cz - 0.5F}, {cx + 0.5F, cz + 0.5F}, {cx - 0.5F, cz + 0.5F}};
         for (int i = 0; i < 4; i++) {
            float[] a = c[i], b = c[(i + 1) % 4];
            boolean front = i == 0; // the face toward the buck (-z)
            int tag = front && ya >= bandLo - 0.001F && yb <= bandHi + (y1 - y0) / rows ? BAND : TRUNK;
            g.add(new float[]{a[0], ya, a[1]}, new float[]{b[0], ya, b[1]}, new float[]{b[0], yb, b[1]}, tag);
            g.add(new float[]{a[0], ya, a[1]}, new float[]{b[0], yb, b[1]}, new float[]{a[0], yb, a[1]}, tag);
         }
      }
      return g;
   }

   static Geo marker(float x, float y, float z, float r, int tag) {
      Geo g = new Geo();
      float[][] o = {{r, 0, 0}, {-r, 0, 0}, {0, r, 0}, {0, -r, 0}, {0, 0, r}, {0, 0, -r}};
      int[][] f = {{0, 2, 4}, {2, 1, 4}, {1, 3, 4}, {3, 0, 4}, {2, 0, 5}, {1, 2, 5}, {3, 1, 5}, {0, 3, 5}};
      for (int[] t : f) g.add(add(o[t[0]], x, y, z), add(o[t[1]], x, y, z), add(o[t[2]], x, y, z), tag);
      return g;
   }

   static Geo branch(float[] root, float[] tip, int tag) {
      Geo g = new Geo();
      int n = 8;
      float[] mid = {(root[0] + tip[0]) * 0.5F, Math.max(root[1], tip[1]) + 0.15F, (root[2] + tip[2]) * 0.5F};
      float[] prev = root;
      for (int i = 1; i <= n; i++) {
         float t = i / (float)n, a = (1 - t) * (1 - t), b = 2 * (1 - t) * t, c = t * t;
         float[] p = {a * root[0] + b * mid[0] + c * tip[0], a * root[1] + b * mid[1] + c * tip[1], a * root[2] + b * mid[2] + c * tip[2]};
         float r = 0.03F * (1 - t) + 0.008F;
         g.add(new float[]{prev[0] - r, prev[1], prev[2]}, new float[]{prev[0] + r, prev[1], prev[2]}, new float[]{p[0] + r, p[1], p[2]}, tag);
         g.add(new float[]{prev[0] - r, prev[1], prev[2]}, new float[]{p[0] + r, p[1], p[2]}, new float[]{p[0] - r, p[1], p[2]}, tag);
         g.add(new float[]{prev[0], prev[1], prev[2] - r}, new float[]{prev[0], prev[1], prev[2] + r}, new float[]{p[0], p[1], p[2] + r}, tag);
         g.add(new float[]{prev[0], prev[1], prev[2] - r}, new float[]{p[0], p[1], p[2] + r}, new float[]{p[0], p[1], p[2] - r}, tag);
         prev = p;
      }
      g.add(new float[]{tip[0], tip[1], tip[2]}, new float[]{tip[0] + 0.01F, tip[1] - 0.09F, tip[2]}, new float[]{tip[0] - 0.01F, tip[1] - 0.09F, tip[2]}, tag);
      return g;
   }

   static float[] add(float[] o, float x, float y, float z) {
      return new float[]{o[0] + x, o[1] + y, o[2] + z};
   }

   // ------------------------------------------------------------------ drawing

   static int color(int tag) {
      return switch (tag) {
         case ANTLER -> 0xEBDDBE;
         case SKULL -> 0x9C6B44;
         case TRUNK -> 0x5E4A38;
         case BAND -> 0xE9D9A6;
         case MARK -> 0x3C8C3C;
         case GROUND -> 0x8B7355;
         default -> 0x8A5A36;
      };
   }

   /** view 0 side (from the buck's left, +z to the right), 1 top, 2 front (from ahead), 3 rear three-quarter */
   static void draw(Raster r, Geo g, float ox, float oy, float ppb, int view) {
      for (int i = 0; i < g.tris.size(); i++) {
         float[] t = g.tris.get(i);
         float[][] q = new float[3][];
         for (int k = 0; k < 3; k++) {
            float x = t[k * 3], y = t[k * 3 + 1], z = t[k * 3 + 2];
            q[k] = switch (view) {
               case 0 -> new float[]{ox + z * ppb, oy - y * ppb, -x};
               case 1 -> new float[]{ox + z * ppb, oy + x * ppb, y};
               case 2 -> new float[]{ox - x * ppb, oy - y * ppb, z};
               case 4 -> new float[]{ox + x * ppb, oy - y * ppb, -z};
               default -> {
                  float a = 0.75F; // rear three-quarter from behind-left
                  float sx = (float)(x * Math.cos(a) + z * Math.sin(a));
                  float sz = (float)(-x * Math.sin(a) + z * Math.cos(a));
                  yield new float[]{ox + sx * ppb, oy - (y - sz * 0.25F) * ppb, -sz};
               }
            };
         }
         float ax = t[3] - t[0], ay = t[4] - t[1], az = t[5] - t[2], bx = t[6] - t[0], by = t[7] - t[1], bz = t[8] - t[2];
         float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
         float len = (float)Math.sqrt(nx * nx + ny * ny + nz * nz) + 1e-9f;
         float shade = 0.45f + 0.55f * Math.abs((nx * 0.45f + ny * 0.75f - nz * 0.48f) / len);
         r.tri(q[0], q[1], q[2], color(g.tags.get(i)), shade);
      }
   }

   // ------------------------------------------------------------------ previews

   static DeerTraits buck(GameSpecies s, int age, int genes) {
      return new DeerTraits(s, true, age, 60, 80, genes, 11, 0, 60);
   }

   static void rubPreview(String file, boolean vanilla) throws Exception {
      int W = 2600, H = vanilla ? 1500 : 1700;
      Raster r = new Raster(W, H, 0xEEF0EA);
      GameSpecies[] species = vanilla ? new GameSpecies[]{GameSpecies.WHITETAIL} : GameSpecies.values();
      int row = 0;
      for (GameSpecies s : species) {
         DeerTraits t = buck(s, 66, 85);
         FightModel um = UltraFightModels.of(t);
         float[] band = band(t);
         float centre = (band[0] + band[1]) * 0.5F;
         float amp = strokeAmp(t);
         float tr = vanilla ? 0.5F : (s == GameSpecies.WHITETAIL ? 0.12F : 0.16F);
         float cz = 2.0F;
         SignGeometry.Trunk trunk = new SignGeometry.Trunk(!vanilla, 0.0F, cz, tr, 0.0F, 0.02F, 3.0F);
         float[][] cases = {{-1.0F, 0.0F, 0.0F}, {0.0F, 0.25F, 0.08F}, {1.0F, 0.0F, 0.0F}, {0.4F, -0.28F, -0.1F}};
         String[] names = {"stroke down", "mid stroke, twisting", "stroke up", "twist other way"};
         float ppb = s == GameSpecies.WHITETAIL ? 260 : 140;
         float rowH = vanilla ? 800 : 560;
         for (int c = 0; c < cases.length; c++) {
            float v = cases[c][0];
            Posed p = rubPose(t, vanilla, v, cases[c][1], cases[c][2], centre + v * amp, trunk, SignAct.RUB_STROKES);
            Geo scene = new Geo();
            scene.addAll(p.geo);
            scene.addAll(vanilla ? block(0.0F, cz + 0.5F - 0.5F, 0.0F, 2.0F, band[0], band[1]) : cylinder(0.0F, cz, tr, 0.0F, 2.2F, band[0], band[1], (float)Math.PI));
            float ox = 40 + c * 650 - (cz - (s == GameSpecies.WHITETAIL ? 2.3F : 3.9F)) * ppb;
            float oy = row * rowH + (vanilla ? 560 : 470);
            draw(r, scene, ox, oy, ppb, 0);
            r.hline((int)oy, 0x9AA090);
            r.text(String.format("%s %s: %s  contact y %.2f (want %.2f)  band %.2f-%.2f", vanilla ? "Vanilla" : "Ultra", s, names[c], p.contactY,
               centre + v * amp, band[0], band[1]), 20 + c * 650, (int)(row * rowH + 24 + (c % 2) * 16));
            if (vanilla) {
               // top view below
               draw(r, scene, ox, oy + 330, ppb, 1);
            }
         }
         row++;
      }
      r.save(file);
   }

   static float[] band(DeerTraits t) {
      FightModel m = UltraFightModels.of(t);
      float amp = strokeAmp(t);
      SignGeometry.Contact c = SignGeometry.contact(m, SignGeometry.rubBase(m, t.species(), new Matrix4f()),
         new SignGeometry.Trunk(true, 0.0F, 6.0F, t.species() == GameSpecies.WHITETAIL ? 0.12F : 0.16F, 0.0F, 0.05F, 3.0F), 1, new SignGeometry.Contact());
      float hc = c.y;
      float sp = Math.min(0.2F, c.spread);
      return new float[]{Math.max(0.08F, hc - amp - sp * 0.5F - 0.03F), hc + amp + sp * 0.5F + 0.03F, hc, c.spread};
   }

   static float strokeAmp(DeerTraits t) {
      return com.formaworks.frontierhunts.sign.work.SignWork.strokeAmp(t);
   }

   static void pawPreview(String file) throws Exception {
      int W = 2400, H = 1300;
      Raster r = new Raster(W, H, 0xEEF0EA);
      DeerTraits t = buck(GameSpecies.WHITETAIL, 66, 85);
      float[] phases = {0.0F, 0.22F, 0.42F, 0.55F, 0.7F, 0.85F};
      String[] names = {"under", "lift + fold", "reach", "strike", "drag", "dragged back"};
      // find a time in the paw clip where foot 0 is at each phase
      int seed = 0;
      float dur = 6.0F;
      float[] mo = new float[4];
      for (int i = 0; i < phases.length; i++) {
         float found = -1;
         for (float tt = 0.2F; tt < dur - 0.4F; tt += 0.005F) {
            SignMotion.paw(seed, tt, dur, mo);
            if (mo[SignMotion.PAW_AMP] > 0.9F && Math.abs(mo[SignMotion.PHASE] - phases[i]) < 0.006F) { found = tt; break; }
         }
         DeerAnimator.Input in = input(SignAct.PAW, found, dur, seed);
         Posed p = ultraPose(t, in, null);
         Geo scene = new Geo();
         scene.addAll(p.geo);
         float ox = 160 + i * 390, oy = 520;
         draw(r, scene, ox, oy, 200, 0);
         r.text(String.format("Ultra paw %s (t %.2f) hooves %.3f %.3f %.3f %.3f", names[i], found, p.hooves[0], p.hooves[1], p.hooves[2], p.hooves[3]), (int)ox, 30 + (i % 2) * 16);
         DeerAnimator.Input vi = input(SignAct.PAW, found, dur, seed);
         Posed vp = vanillaPose(t, vi);
         draw(r, vp.geo, ox, oy + 650, 200, 0);
         r.text("Vanilla " + names[i], (int)ox, 700);
      }
      r.hline(520, 0x9AA090);
      r.hline(1170, 0x9AA090);
      r.save(file);
   }

   static void lickPreview(String file) throws Exception {
      int W = 2000, H = 1300;
      Raster r = new Raster(W, H, 0xEEF0EA);
      DeerTraits[] ts = {buck(GameSpecies.WHITETAIL, 66, 85), buck(GameSpecies.WHITETAIL, 30, 50)};
      int col = 0;
      for (DeerTraits t : ts) {
         FightModel m = UltraFightModels.of(t);
         DeerSkeleton sk = DeerSkeleton.of(t.species());
         float[] reach = SignGeometry.lickReach(m, sk);
         float[][] wig = {{0, 0, 0, 0}, {0.2F, 0.1F, 0.22F, 0.05F}};
         for (float[] wg : wig) {
            DeerAnimator.Input in = input(SignAct.LICK, 2.0F, 5.0F, 3);
            Matrix4f tg = SignGeometry.lickTarget(m, sk, wg[0], wg[1], wg[2], wg[3] / m.sy, new Matrix4f());
            Posed p = ultraPose(t, in, tg);
            Vector3f nose = p.head.transformPosition(new Vector3f(SignGeometry.nose(m, sk)));
            Vector3f ne = m.toEntity(nose, new Vector3f());
            Geo scene = new Geo();
            scene.addAll(p.geo);
            float[] tip = {0.0F, reach[1] + 0.06F, reach[0]};
            scene.addAll(branch(new float[]{0.0F, reach[1] + 0.5F, reach[0] + 1.3F}, tip, TRUNK));
            scene.addAll(marker(ne.x, ne.y, ne.z, 0.02F, MARK));
            float ox = 200 + col * 480, oy = 560;
            draw(r, scene, ox, oy, 220, 0);
            float err = (float)Math.sqrt(Math.pow(ne.y - (tip[1] - 0.06F), 2) + Math.pow(ne.z - tip[2], 2));
            r.text(String.format("Ultra lick %d mo: reach fwd %.2f up %.2f, nose err %.3f", t.ageMonths(), reach[0], reach[1], err), (int)ox - 120, 30 + (col % 2) * 16);
            DeerAnimator.Input vi = input(SignAct.LICK, 2.0F, 5.0F, 3);
            vi.signPitch = -(0.5F + wg[1]);
            vi.signYaw = wg[0];
            vi.signDrop = -(0.08F + wg[3]) / dropPerPx(t);
            Posed vp = vanillaPose(t, vi);
            Geo vs = new Geo();
            vs.addAll(vp.geo);
            vs.addAll(branch(new float[]{0.0F, reach[1] + 0.5F, reach[0] + 1.3F}, tip, TRUNK));
            draw(r, vs, ox, oy + 650, 220, 0);
            r.text("Vanilla lick", (int)ox, 700);
            col++;
         }
      }
      r.hline(560, 0x9AA090);
      r.hline(1210, 0x9AA090);
      r.save(file);
   }

   static void urinatePreview(String file) throws Exception {
      int W = 1800, H = 1200;
      Raster r = new Raster(W, H, 0xEEF0EA);
      DeerTraits t = buck(GameSpecies.WHITETAIL, 66, 85);
      for (int c = 0; c < 2; c++) {
         DeerAnimator.Input in = input(c == 0 ? 0 : SignAct.URINATE, 2.0F, 4.0F, 5);
         Posed p = ultraPose(t, in, null);
         draw(r, p.geo, 250 + c * 800, 480, 320, 0);
         draw(r, p.geo, 300 + c * 800, 1050, 320, 4);
         r.text(String.format("%s hooves y %.3f %.3f %.3f %.3f", c == 0 ? "standing" : "rub-urinating", p.hooves[0], p.hooves[1], p.hooves[2], p.hooves[3]),
            100 + c * 800, 30);
      }
      r.hline(480, 0x9AA090);
      r.save(file);
   }

   // ------------------------------------------------------------------ checks

   static void checkMotion() {
      System.out.println("SignMotion");
      float[] o = new float[6];
      for (int seed : new int[]{1, 99, -12345, 777}) {
         for (float dur : new float[]{6.0F, 10.0F, 15.0F}) {
            int strokes = 0;
            float last = -1;
            float vmin = 9, vmax = -9;
            for (float t = 0; t < dur; t += 0.05F) {
               SignMotion.rub(seed, t, dur, o);
               if (o[SignMotion.STROKE] != last) { strokes++; last = o[SignMotion.STROKE]; }
               vmin = Math.min(vmin, o[SignMotion.V]);
               vmax = Math.max(vmax, o[SignMotion.V]);
            }
            check(strokes >= dur * 0.9F && strokes <= dur * 3.2F && vmin < -0.6F && vmax > 0.6F,
               String.format("rub seed %d %.0f s: %d strokes, v %.2f..%.2f", seed, dur, strokes, vmin, vmax));
         }
      }
      float[] p = new float[4];
      int changes = 0;
      float lastFoot = -1;
      int strokes = 0;
      float ls = -1;
      for (float t = 0; t < 6; t += 0.02F) {
         SignMotion.paw(5, t, 6, p);
         if (p[SignMotion.PAW_AMP] > 0 && p[SignMotion.FOOT] != lastFoot) { changes++; lastFoot = p[SignMotion.FOOT]; }
         if (p[SignMotion.PAW_STROKE] != ls) { strokes++; ls = p[SignMotion.PAW_STROKE]; }
      }
      check(changes >= 2 && strokes >= 5, "paw 6 s: " + strokes + " strokes, " + changes + " foot changes (alternating front hooves)");
   }

   static void checkAct() {
      System.out.println("SignAct timeline / sync tag");
      int[][] pt = SignPlan.phases(SignAct.SCRAPE, 60, new float[]{0.5F, 0.5F, 0.5F, 0.5F, 0.5F});
      SignAct a = new SignAct(SignAct.SCRAPE, 1000L, new net.minecraft.core.BlockPos(10, 64, -3), 42, pt[0], pt[1]);
      SignAct b = SignAct.load(a.save());
      check(b.active() && b.kind() == a.kind() && b.start() == 1000L && b.sign().equals(a.sign()) && java.util.Arrays.equals(b.ticks(), a.ticks()),
         "SignAct save/load round trip");
      check(!SignAct.load(new net.minecraft.nbt.CompoundTag()).active(), "empty tag = no work");
      net.minecraft.nbt.CompoundTag bad = a.save();
      bad.putIntArray("d", new int[]{5000, 1, 1, 1});
      check(!SignAct.load(bad).active(), "malformed tag rejected");
      int t = 0;
      boolean seq = true;
      int[] order = {SignAct.SNIFF_GROUND, SignAct.PAW, SignAct.LICK, SignAct.URINATE};
      for (int i = 0; i < 4; i++) {
         seq &= a.at(t)[0] == order[i] && a.at(t + pt[1][i] - 1)[0] == order[i];
         t += pt[1][i];
      }
      check(seq && a.at(t)[0] == 0 && a.total() == t, "scrape phases in order: sniff, paw, lick branch, urinate (" + t / 20.0F + " s)");
      Random rnd = new Random(1);
      float rmin = 99, rmax = 0;
      for (int i = 0; i < 2000; i++) {
         int age = 16 + rnd.nextInt(90);
         int[][] q = SignPlan.phases(SignAct.RUB, age, new float[]{rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat()});
         rmin = Math.min(rmin, q[1][1] / 20.0F);
         rmax = Math.max(rmax, q[1][1] / 20.0F);
      }
      check(rmin >= 6.0F && rmax <= 15.0F, String.format("rub strokes last %.1f-%.1f s (6-15 s)", rmin, rmax));
   }

   /** Simulated buck: walks at walk speed to the stand point, settles, acts; with and without a disturbance. */
   static void checkMachine() {
      System.out.println("SignPlan state machine (simulated buck)");
      for (int run = 0; run < 3; run++) {
         double x = 6.0 + run * 3, z = -2.0;
         double sx = 0.0, sz = 0.0;
         float yaw = 120.0F, want = -90.0F;
         int state = SignPlan.WALK;
         long now = 0, stateAt = 0, progressAt = 0;
         double lastX = x, lastZ = z;
         long deadline = SignPlan.walkTicks(Math.hypot(x - sx, z - sz));
         int[][] pt = SignPlan.phases(SignAct.RUB, 60, new float[]{0.3F, 0.4F, 0.5F, 0.5F, 0.5F});
         int total = pt[1][0] + pt[1][1];
         long actAt = -1;
         long settleTicks = 0;
         boolean disturbed = run == 2;
         int result = -1;
         for (; now < 4000; now++) {
            double d = Math.hypot(sx - x, sz - z);
            if (state == SignPlan.WALK) {
               double step = Math.min(d, 0.11); // whitetail walk ~2.2 blocks/s
               x += (sx - x) / Math.max(1e-6, d) * step;
               z += (sz - z) / Math.max(1e-6, d) * step;
               if (Math.hypot(x - lastX, z - lastZ) > 0.5) { lastX = x; lastZ = z; progressAt = now; }
               d = Math.hypot(sx - x, sz - z);
            } else if (state == SignPlan.SETTLE) {
               double step = Math.min(d, SignPlan.GLIDE);
               if (d > 1e-4) { x += (sx - x) / d * step; z += (sz - z) / d * step; }
               d = Math.hypot(sx - x, sz - z);
               float err = want - yaw;
               yaw += Math.max(-SignPlan.TURN, Math.min(SignPlan.TURN, err));
               settleTicks++;
            }
            boolean siteOk = !(disturbed && state == SignPlan.ACT && now - actAt > 100);
            int next = SignPlan.step(state, d, want - yaw, now > 30, now - stateAt, now - progressAt, now > deadline, siteOk, actAt < 0 ? 0 : now - actAt, total);
            if (next != state) {
               if (next == SignPlan.ACT) actAt = now;
               if (next == SignPlan.DONE || next == SignPlan.ABORT) { result = next; break; }
               state = next;
               stateAt = now;
            }
         }
         if (!disturbed) {
            check(result == SignPlan.DONE && settleTicks <= 60 && now - actAt == total,
               String.format("run %d: walked %.1f blocks, settled in %.1f s, worked %.1f s, done at %.1f s", run, Math.hypot(6.0 + run * 3, 2.0), settleTicks / 20.0F,
                  (now - actAt) / 20.0F, now / 20.0F));
         } else {
            check(result == SignPlan.ABORT && now - actAt > 100 && now - actAt < total, "run 2: site lost mid-rub -> work aborted at " + (now - actAt) / 20.0F + " s");
         }
      }
      // stuck walk
      int s = SignPlan.step(SignPlan.WALK, 5.0, 0, true, 300, SignPlan.STUCK + 1, false, true, 0, 0);
      check(s == SignPlan.ABORT, "walk with no progress for 7 s is given up");
      check(SignPlan.step(SignPlan.SETTLE, 0.0, 0.0F, false, 20, 0, false, true, 0, 0) == SignPlan.SETTLE
         && SignPlan.step(SignPlan.SETTLE, 0.0, 0.0F, false, 51, 0, false, true, 0, 0) == SignPlan.ACT, "settling waits up to 2.5 s for the head geometry, then works anyway");
   }

   /** Monte Carlo of the decide / rest loop over the season: sign per active hour for mature and young bucks. */
   static void checkSeason() {
      System.out.println("SignSeason (sign made per 10 active minutes, Monte Carlo of SignPlan.decide + rests)");
      String[] names = {"Sep 1", "Sep 5", "Sep 15", "Oct 1", "Oct 20", "Nov 5", "Nov 15", "Dec 1", "Dec 20", "Jul 1"};
      double[] ys = {8.0, 8.13, 8.47, 9.0, 9.65, 10.17, 10.5, 11.0, 11.65, 6.0};
      Random rnd = new Random(9);
      double[] matureRate = new double[ys.length];
      for (int i = 0; i < ys.length; i++) {
         StringBuilder sb = new StringBuilder(String.format("  %-7s rub %.2f scrape %.2f |", names[i], SignSeason.rubRate(GameSpecies.WHITETAIL, ys[i]),
            SignSeason.scrapeRate(GameSpecies.WHITETAIL, ys[i])));
         for (int age : new int[]{66, 30, 18}) {
            float m = SignSeason.maturity(age);
            float rub = SignSeason.rubRate(GameSpecies.WHITETAIL, ys[i]) * m, scr = SignSeason.scrapeRate(GameSpecies.WHITETAIL, ys[i]) * m;
            int rubs = 0, scrapes = 0, revisits = 0;
            long minutes = 10L * 60 * 20 * 200; // 200 x 10 minutes
            long now = 0, next = 0;
            while (now < minutes) {
               if (now >= next) {
                  int k = SignPlan.decide(rub, scr, scrapes + revisits > 0 && rnd.nextFloat() < 0.6F, rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat());
                  next = now + SignPlan.lookInterval(rnd.nextFloat());
                  if (k != 0) {
                     int[][] pt = SignPlan.phases(k, age, new float[]{rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat()});
                     long work = 160 + pt[1][0] + pt[1][1] + (pt[1].length > 2 ? pt[1][2] + pt[1][3] : 0); // walk ~8 s + act
                     if (k == SignAct.RUB) rubs++; else if (k == SignAct.SCRAPE) scrapes++; else revisits++;
                     now += work;
                     next = now + SignPlan.restAfter(k, rnd.nextFloat(), rnd.nextFloat());
                  }
               }
               now += 20;
            }
            sb.append(String.format("  %d mo: %4.1f rubs %4.1f scrapes %4.1f revisits", age, rubs / 200.0, scrapes / 200.0, revisits / 200.0));
            if (age == 66) matureRate[i] = (rubs + scrapes + revisits) / 200.0;
         }
         System.out.println(sb);
      }
      check(matureRate[0] == 0.0 && matureRate[9] == 0.0 && matureRate[8] == 0.0, "no sign before velvet shedding (Sep 1), in summer or after mid December");
      check(matureRate[1] > 0.5, String.format("early September (velvet off): a mature buck makes sign %.1fx per 10 active minutes", matureRate[1]));
      check(matureRate[3] >= 2.0 && matureRate[4] >= 2.0, String.format("October pre-rut: %.1f / %.1f per 10 active minutes (seen within a few minutes)", matureRate[3], matureRate[4]));
      check(matureRate[4] > matureRate[7], "pre-rut more than post-rut");
   }

   static void checkContact() {
      System.out.println("SignGeometry contact");
      SignGeometry.Trunk circle = new SignGeometry.Trunk(true, 0.1F, 2.0F, 0.2F, 0.0F, 0F, 3F);
      check(Math.abs(circle.entry(0.1F, 0.0F) - 1.8F) < 1e-4 && circle.entry(0.35F, 0.0F) == Float.POSITIVE_INFINITY, "ray vs round stem");
      SignGeometry.Trunk sq = new SignGeometry.Trunk(false, 0.0F, 2.0F, 0.5F, 0.0F, 0F, 3F);
      check(Math.abs(sq.entry(0.3F, 0.0F) - 1.5F) < 1e-4 && sq.entry(0.6F, 0.0F) == Float.POSITIVE_INFINITY, "ray vs block log face");
      SignGeometry.Trunk sq45 = new SignGeometry.Trunk(false, 0.0F, 2.0F, 0.5F, (float)(Math.PI / 4), 0F, 3F);
      check(Math.abs(sq45.entry(0.0F, 0.0F) - (2.0F - 0.7071F)) < 1e-3, "ray vs block log turned 45 degrees (corner)");
      for (GameSpecies s : GameSpecies.values()) {
         for (int age : new int[]{20, 42, 90}) {
            DeerTraits t = buck(s, age, 70);
            float[] b = band(t);
            boolean whitetail = s == GameSpecies.WHITETAIL;
            boolean ok = whitetail ? b[0] >= 0.15F && b[0] <= 0.55F && b[1] >= 0.55F && b[1] <= 1.25F : b[0] >= 0.3F && b[1] <= 2.0F && b[1] - b[0] >= 0.3F;
            check(ok, String.format("%s %d mo: antlers meet a small trunk at %.2f m (patch %.2f) -> rub band %.2f-%.2f m", s, age, b[2], b[3], b[0], b[1]));
         }
      }
      // presets: every pose of a stroke cycle touches the bark at the wanted height
      for (boolean vanilla : new boolean[]{false, true}) {
         for (GameSpecies s : vanilla ? new GameSpecies[]{GameSpecies.WHITETAIL, GameSpecies.ELK, GameSpecies.MOOSE} : GameSpecies.values()) {
            DeerTraits t = buck(s, 60, 80);
            float[] b = band(t);
            float amp = strokeAmp(t);
            float tr = vanilla ? 0.5F : (s == GameSpecies.WHITETAIL ? 0.12F : 0.16F);
            float worst = 0;
            float worstSlide = 0;
            for (float v : new float[]{-1, -0.5F, 0, 0.5F, 1}) {
               SignGeometry.Trunk trunk = new SignGeometry.Trunk(!vanilla, 0.0F, 2.0F, tr, 0.0F, 0.02F, 3.0F);
               Posed p = rubPose(t, vanilla, v, 0.15F, 0.05F, b[2] + v * amp, trunk, SignAct.RUB_STROKES);
               worst = Math.max(worst, Math.abs(p.contactY - (b[2] + v * amp)));
               if (System.getenv("SIGN_DEBUG") != null) System.out.printf("     v %+.1f contact %.3f want %.3f slide %.3f%n", v, p.contactY, b[2] + v * amp, p.slide);
               worstSlide = Math.max(worstSlide, Math.abs(p.slide - (2.0F - 0.5F - 0.03F - 0.45F)));
            }
            check(worst < (vanilla ? 0.12F : 0.06F), String.format("%s %s: contact height within %.3f m of the rub's stroke through the whole stroke", vanilla ? "Vanilla" : "Ultra", s, worst));
         }
      }
   }

   static void checkLegs() {
      System.out.println("Legs on the rig (hooves stay on the ground)");
      for (GameSpecies s : GameSpecies.values()) {
         DeerTraits t = buck(s, 60, 80);
         Posed st = ultraPose(t, input(0, 0, 1, 0), null);
         float[] g = st.hooves;
         Posed u = ultraPose(t, input(SignAct.URINATE, 2.0F, 4.0F, 3), null);
         float d = 0;
         for (int k = 0; k < 4; k++) d = Math.max(d, Math.abs(u.hooves[k] - g[k]));
         check(d < 0.035F, String.format("%s rub-urinating: hooves within %.3f of the ground", s, d));
         float minLift = 9, planted = 0;
         float[] mo = new float[4];
         for (float tt = 0.3F; tt < 5.5F; tt += 0.1F) {
            SignMotion.paw(1, tt, 6, mo);
            Posed p = ultraPose(t, input(SignAct.PAW, tt, 6, 1), null);
            int foot = mo[SignMotion.FOOT] < 0.5F ? 0 : 1;
            for (int k = 0; k < 4; k++) {
               if (k == foot) minLift = Math.min(minLift, p.hooves[k] - g[k]);
               else planted = Math.max(planted, Math.abs(p.hooves[k] - g[k]));
            }
         }
         check(minLift > -0.03F && planted < 0.02F, String.format("%s pawing: working hoof never below ground (min %.3f), others planted (%.3f)", s, minLift, planted));
      }
   }

   public static void main(String[] args) throws Exception {
      String out = args.length > 0 ? args[0] : ".";
      checkMotion();
      checkAct();
      checkMachine();
      checkSeason();
      checkContact();
      checkLegs();
      rubPreview(out + "/rub_ultra.png", false);
      rubPreview(out + "/rub_vanilla.png", true);
      pawPreview(out + "/paw.png");
      lickPreview(out + "/lick.png");
      urinatePreview(out + "/urinate.png");
      System.out.println(checks + " checks, " + fails + " failed");
      System.exit(fails == 0 ? 0 : 1);
   }
}
