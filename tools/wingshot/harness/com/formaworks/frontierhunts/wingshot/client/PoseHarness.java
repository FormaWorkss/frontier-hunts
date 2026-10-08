package com.formaworks.frontierhunts.wingshot.client;

import com.formaworks.frontierhunts.wildlife2026.client.SkinnedMesh;
import com.formaworks.frontierhunts.wildlife2026.client.WildlifeRig;
import com.formaworks.frontierhunts.wingshot.Flight;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.ZipFile;

/**
 * [wingshot] Offline pose dump: poses the real Ultra meshes with the real rig + bird pose + feathered wing geometry and
 * writes the skinned triangles for the preview renderer (tools/wingshot/preview.py). Also checks every pose for NaN,
 * wings attached to the body, and span / proportions.
 */
public final class PoseHarness {
   static int fails;

   static void check(boolean ok, String what) {
      if (!ok) {
         fails++;
         System.out.println("FAIL " + what);
      }
   }

   record Pose(String name, byte phase, float cycle, boolean dead, boolean down, boolean water, float climb, float yawRate) {
   }

   static final Pose[] POSES = {
      new Pose("folded", Flight.NONE, 0, false, false, false, 0, 0),
      new Pose("stroke_top", Flight.FLAP, 0.0F, false, false, false, 0, 0),
      new Pose("stroke_mid_down", Flight.FLAP, 0.27F, false, false, false, 0, 0),
      new Pose("stroke_bottom", Flight.FLAP, 0.55F, false, false, false, 0, 0),
      new Pose("stroke_mid_up", Flight.FLAP, 0.78F, false, false, false, 0, 0),
      new Pose("takeoff", Flight.TAKEOFF, 0.3F, false, false, false, 0.25F, 0),
      new Pose("glide_bank", Flight.GLIDE, 0, false, false, false, -0.05F, 0.05F),
      new Pose("set_circle", Flight.SET, 0.2F, false, false, false, -0.04F, -0.04F),
      new Pose("landing", Flight.LAND, 0.1F, false, false, false, -0.1F, 0),
      new Pose("drum", Flight.DRUM_A, 0, false, false, false, 0, 0),
      new Pose("dead_fall", Flight.FALL, 0, true, false, false, -0.4F, 0),
      new Pose("dead_down", Flight.NONE, 0, true, true, false, 0, 0),
      new Pose("dead_water", Flight.NONE, 0, true, true, true, 0, 0),
   };

   public static void main(String[] a) throws Exception {
      String jar = a[0];
      String out = a[1];
      Method parse = SkinnedMesh.class.getDeclaredMethod("parse", InputStream.class);
      parse.setAccessible(true);
      try (ZipFile z = new ZipFile(jar)) {
         for (String sp : new String[]{"duck", "grouse"}) {
            for (String lod : new String[]{"ultra", "bal"}) {
               var e = z.getEntry("assets/frontierhunts/models/wildlife/" + sp + "_" + lod + ".fhsk");
               SkinnedMesh m = (SkinnedMesh)parse.invoke(null, new ByteArrayInputStream(z.getInputStream(e).readAllBytes()));
               for (Pose p : POSES) {
                  if (p.name.equals("drum") && !sp.equals("grouse")) {
                     continue;
                  }
                  BirdAnim.State st = new BirdAnim.State(12345, sp.equals("grouse"));
                  // settle into the phase (60 ticks), then put the stroke where we want it
                  float speed = p.phase == Flight.NONE || p.phase >= Flight.DRUM_A ? 0.0F : 0.8F;
                  float t = 100.0F;
                  for (int i = 0; i < 80; i++) {
                     BirdAnim.step(st, p.phase, t, speed, p.climb, p.yawRate, p.dead, false, false, false);
                     t += 1.0F;
                  }
                  if (p.dead) {
                     for (int i = 0; i < 60; i++) {
                        BirdAnim.step(st, p.phase, t, 0.0F, p.climb, 0.0F, true, p.down, p.water, p.down);
                        t += 1.0F;
                     }
                  }
                  if (p.name.equals("drum")) {
                     // the moment of a beat
                     st.phaseStart = t - 0.79F * 20.0F;
                  }
                  st.cycle = p.cycle;
                  BirdAnim.step(st, p.phase, t + 0.001F, speed, p.climb, p.yawRate, p.dead, p.down, p.water, p.down);
                  WildlifeRig.Input in = new WildlifeRig.Input();
                  in.age = t;
                  in.bird = st;
                  in.water = p.water;
                  in.flying = BirdAnim.flying(p.phase);
                  float[] mats = new float[m.names.length * 12];
                  WildlifeRig.pose(m, in, mats);
                  for (float f : mats) {
                     check(Float.isFinite(f), sp + " " + p.name + " NaN matrix");
                  }
                  float[] body = skin(m, mats);
                  WingGeometry g = new WingGeometry();
                  int chest = m.bone("chest"), wl = m.bone("wing_l"), wr = m.bone("wing_r");
                  float[] jl = {m.joint[wl * 3], m.joint[wl * 3 + 1], m.joint[wl * 3 + 2]};
                  float[] jr = {m.joint[wr * 3], m.joint[wr * 3 + 1], m.joint[wr * 3 + 2]};
                  WingGeometry.Spec spec = sp.equals("grouse") ? WingGeometry.GROUSE : WingGeometry.DUCK;
                  g.build(spec, st, mats, chest * 12, jl, jr);
                  float[] wd = g.data();
                  int wn = g.vertices();
                  float minX = 9, maxX = -9;
                  for (int i = 0; i < wn; i++) {
                     for (int k = 0; k < WingGeometry.STRIDE; k++) {
                        check(Float.isFinite(wd[i * WingGeometry.STRIDE + k]), sp + " " + p.name + " NaN wing");
                     }
                     minX = Math.min(minX, wd[i * WingGeometry.STRIDE]);
                     maxX = Math.max(maxX, wd[i * WingGeometry.STRIDE]);
                  }
                  if (p.name.equals("glide_bank")) {
                     float span = maxX - minX;
                     float bodyLen = m.meta[0] / 0.38F * 0.48F;
                     System.out.printf("%s %s span %.3f (body length %.3f, span/len %.2f)%n", sp, lod, span, sp.equals("grouse") ? 0.36F : 0.48F,
                        span / (sp.equals("grouse") ? 0.36F : 0.48F));
                     check(span / (sp.equals("grouse") ? 0.36F : 0.48F) > 1.2F && span / (sp.equals("grouse") ? 0.36F : 0.48F) < 1.9F, sp + " span/length ratio");
                  }
                  if (p.name.equals("folded")) {
                     check(wn == 0, sp + " folded: no feathered wing drawn (" + wn + ")");
                  }
                  if (lod.equals("ultra")) {
                     write(out + "/" + sp + "_" + p.name + ".bin", body, m.tris, wd, wn, st);
                  }
               }
            }
         }
      }
      System.out.println(fails == 0 ? "POSES OK" : fails + " FAILED");
      System.exit(fails == 0 ? 0 : 1);
   }

   static float[] skin(SkinnedMesh m, float[] M) {
      int nv = m.vertexCount;
      float[] out = new float[nv * 8];
      for (int v = 0; v < nv; v++) {
         float px = m.pos[v * 3], py = m.pos[v * 3 + 1], pz = m.pos[v * 3 + 2];
         float nx = m.nrm[v * 3], ny = m.nrm[v * 3 + 1], nz = m.nrm[v * 3 + 2];
         float x = 0, y = 0, z = 0, a = 0, b = 0, c = 0;
         for (int k = 0; k < 4; k++) {
            float w = m.weight[v * 4 + k];
            if (w <= 0) continue;
            int o = (m.bone[v * 4 + k] & 255) * 12;
            x += w * (M[o] * px + M[o + 1] * py + M[o + 2] * pz + M[o + 3]);
            y += w * (M[o + 4] * px + M[o + 5] * py + M[o + 6] * pz + M[o + 7]);
            z += w * (M[o + 8] * px + M[o + 9] * py + M[o + 10] * pz + M[o + 11]);
            a += w * (M[o] * nx + M[o + 1] * ny + M[o + 2] * nz);
            b += w * (M[o + 4] * nx + M[o + 5] * ny + M[o + 6] * nz);
            c += w * (M[o + 8] * nx + M[o + 9] * ny + M[o + 10] * nz);
         }
         float l = (float)Math.sqrt(a * a + b * b + c * c) + 1e-9F;
         int o = v * 8;
         out[o] = x;
         out[o + 1] = y;
         out[o + 2] = z;
         out[o + 3] = a / l;
         out[o + 4] = b / l;
         out[o + 5] = c / l;
         out[o + 6] = m.uv[v * 2];
         out[o + 7] = m.uv[v * 2 + 1];
      }
      return out;
   }

   static void write(String path, float[] body, int[] tris, float[] wd, int wn, BirdAnim.State st) throws Exception {
      int nb = tris.length;
      ByteBuffer bb = ByteBuffer.allocate(4 * (8 + nb * 8 + wn * 8)).order(ByteOrder.LITTLE_ENDIAN);
      bb.putInt(nb);
      bb.putInt(wn);
      bb.putFloat(st.bank);
      bb.putFloat(st.pitch);
      bb.putFloat(st.limp);
      bb.putFloat(st.tumble);
      bb.putFloat(st.down ? 1 : 0);
      bb.putFloat(st.restRoll());
      for (int i = 0; i < nb; i++) {
         int v = tris[i];
         for (int k = 0; k < 8; k++) bb.putFloat(body[v * 8 + k]);
      }
      for (int i = 0; i < wn * 8; i++) bb.putFloat(wd[i]);
      try (DataOutputStream o = new DataOutputStream(new FileOutputStream(path))) {
         o.write(bb.array());
      }
   }
}
