package com.formaworks.frontierhunts.wildlife2026.client;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.world.entity.WalkAnimationState;

/**
 * [anims] Offline gait audit: a simulated animal walks in a straight line at the speeds the game's navigation gives it
 * (vanilla mob physics: ground speed = 2.16 x (attribute x modifier)^2 blocks a tick); every frame runs what
 * WildlifeRenderer.animate does (vanilla WalkAnimationState updated per tick as LivingEntity.calculateEntityAnimation
 * does, WildlifeGait.track/fill when present), then the real WildlifeRig.pose and MeshSkinner.skinModel. The paw
 * soles (lowest vertices of each leg) are followed in world space and measured:
 * <ul>
 * <li>slip: mean |paw speed| / body speed while the paw is on the ground (0 = planted, 1 = carried along = gliding)</li>
 * <li>sweep: fore-aft travel of the paw relative to the body; lift: how high it rises; ground: lowest sole height</li>
 * <li>duty: share of the stride on the ground; freq: strides a second</li>
 * </ul>
 * Works against trees without WildlifeGait too (the old rig), for before/after numbers.
 * usage: GaitAudit &lt;mesh dir&gt; &lt;out csv&gt; [species,...] [trace dir]
 */
public final class GaitAudit {
   static final boolean DEBUG = Boolean.getBoolean("gait.debug");
   /** -Dgait.dump=dir: write posed frames (one stride, 12 frames) of every species x case for tools/anims/filmstrip.py */
   static final String DUMP = System.getProperty("gait.dump");
   static String dumpName;
   static Method PARSE, TRACK, FILL, INFO, STYLE;

   static SkinnedMesh load(File f) throws Exception {
      if (PARSE == null) {
         PARSE = SkinnedMesh.class.getDeclaredMethod("parse", InputStream.class);
         PARSE.setAccessible(true);
      }
      return (SkinnedMesh)PARSE.invoke(null, new ByteArrayInputStream(Files.readAllBytes(f.toPath())));
   }

   /** species, movement speed attribute, gait style (WildlifeGait constants), predator */
   static final Object[][] SPECIES = {
      {"wolf", 0.3, 0, true}, {"coyote", 0.32, 0, true}, {"cougar", 0.32, 1, true}, {"panther", 0.32, 1, true}, {"lion", 0.3, 1, true},
      {"cheetah", 0.38, 2, true}, {"grizzly", 0.27, 3, true}, {"black_bear", 0.28, 3, true}, {"polar_bear", 0.27, 3, true},
      {"bison", 0.26, 4, false}, {"boar", 0.27, 5, false}, {"pronghorn", 0.34, 5, false}};

   record Case(String name, double mod, boolean flee) {
   }

   static final Case[] CASES = {new Case("amble", 0.45, false), new Case("wander", 0.7, false), new Case("trot", 1.0, false),
      new Case("chase", 1.2, true), new Case("flee", 1.6, true)};

   public static void main(String[] a) throws Exception {
      File dir = new File(a[0]);
      String only = a.length > 2 ? a[2] : "";
      File traceDir = a.length > 3 ? new File(a[3]) : null;
      Class<?> gait = null;
      try {
         gait = Class.forName("com.formaworks.frontierhunts.wildlife2026.client.WildlifeGait");
         Class<?> track = Class.forName("com.formaworks.frontierhunts.wildlife2026.client.WildlifeGait$Track");
         Class<?> info = Class.forName("com.formaworks.frontierhunts.wildlife2026.client.WildlifeGait$Info");
         TRACK = gait.getMethod("track", track, info, int.class, double.class, double.class, float.class, float.class, float.class, float.class,
            float.class, float.class);
         FILL = gait.getMethod("fill", track, int.class, WildlifeRig.Input.class);
         INFO = gait.getMethod("info", SkinnedMesh.class);
      } catch (ClassNotFoundException e) {
         System.out.println("(no WildlifeGait: old rig)");
      }
      try (PrintWriter out = new PrintWriter(a[1])) {
         out.println("species,lod,case,v_bpt,freq_hz,leg,slip,sweep,lift,ground,duty");
         int fails = 0, checks = 0;
         double worstSlip = 0;
         for (Object[] sp : SPECIES) {
            String name = (String)sp[0];
            if (!only.isEmpty() && !(("," + only + ",").contains("," + name + ","))) continue;
            for (String lod : new String[]{"ultra", "mid", "bal"}) {
               File f = new File(dir, name + "_" + lod + ".fhsk");
               if (!f.exists()) continue;
               SkinnedMesh m = load(f);
               for (Case c : CASES) {
                  if (c.name.equals("chase") && !(Boolean)sp[3]) continue;
                  double s = (Double)sp[1] * c.mod;
                  float v = (float)(0.98 * s * s / (1.0 - 0.546));
                  PrintWriter tr = traceDir != null && lod.equals(System.getProperty("gait.lod", "ultra")) ? new PrintWriter(new File(traceDir, name + "_" + c.name + ".csv")) : null;
                  dumpName = DUMP != null && lod.equals("ultra") ? name + "_" + c.name : null;
                  float[][] r = run(m, v, c.flee, (Integer)sp[2], gait != null, tr);
                  if (tr != null) tr.close();
                  StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "%-10s %-5s %-6s v=%.3f %4.2fHz", name, lod, c.name, v, r[4][0]));
                  for (int l = 0; l < 4; l++) {
                     float[] q = r[l];
                     out.printf(Locale.ROOT, "%s,%s,%s,%.4f,%.3f,%d,%.3f,%.3f,%.3f,%.3f,%.2f%n", name, lod, c.name, v, r[4][0], l, q[0], q[1], q[2], q[3], q[4]);
                     line.append(String.format(Locale.ROOT, " | slip %.2f sw %.2f lift %.3f gnd %+.3f duty %.2f", q[0], q[1], q[2], q[3], q[4]));
                     checks++;
                     float H = m.meta[0];
                     boolean bad = q[0] > 0.15F || Math.abs(q[3]) > 0.03F * H + 0.01F || q[1] < 0.02F;
                     if (bad) fails++;
                     worstSlip = Math.max(worstSlip, q[0]);
                  }
                  System.out.println(line);
               }
            }
         }
         System.out.printf(Locale.ROOT, "GAIT %s: %d leg checks, %d failed (slip > 0.15, sole off the ground > 3%% height, sweep < 0.02), worst slip %.2f%n",
            fails == 0 ? "OK" : "FAIL", checks, fails, worstSlip);
      }
   }

   static int dominant(SkinnedMesh m, int v) {
      int best = 0;
      for (int k = 1; k < 4; k++) if (m.weight[v * 4 + k] > m.weight[v * 4 + best]) best = k;
      return m.bone[v * 4 + best] & 255;
   }

   /** per leg {slip, sweep, lift, ground, duty}; [4][0] = stride frequency (Hz) */
   static float[][] run(SkinnedMesh m, float v, boolean flee, int style, boolean gait, PrintWriter trace) throws Exception {
      int nv = m.vertexCount, nb = m.names.length;
      String[] legs = {"fl", "fr", "bl", "br"};
      // paw soles: the lowest vertices dominated by each leg's bones
      List<int[]> soles = new ArrayList<>();
      for (int l = 0; l < 4; l++) {
         java.util.Set<Integer> bones = new java.util.HashSet<>();
         for (String sfx : new String[]{"_upper", "_lower", "_foot", "_toe"}) bones.add(m.bone(legs[l] + sfx));
         float minY = Float.MAX_VALUE;
         List<Integer> vs = new ArrayList<>();
         for (int i = 0; i < nv; i++) {
            if (bones.contains(dominant(m, i))) {
               vs.add(i);
               minY = Math.min(minY, m.pos[i * 3 + 1]);
            }
         }
         List<Integer> sel = new ArrayList<>();
         for (int i : vs) if (m.pos[i * 3 + 1] < minY + 0.02F * m.meta[0]) sel.add(i);
         soles.add(sel.stream().mapToInt(Integer::intValue).toArray());
      }
      WalkAnimationState walk = new WalkAnimationState();
      WildlifeRig.Input in = new WildlifeRig.Input();
      if (flee) in.w[WildlifeRig.FLEE] = 1.0F;
      Object track = gait ? Class.forName("com.formaworks.frontierhunts.wildlife2026.client.WildlifeGait$Track").getConstructor().newInstance() : null;
      Object info = gait ? INFO.invoke(null, m) : null;
      float[] M = new float[nb * 12];
      float[] sk = new float[nv * 6];
      int ticks = 200, warm = 80, sub = 6;
      int frames = (ticks - warm) * sub;
      float[][] y = new float[4][frames], z = new float[4][frames];
      float[] body = new float[frames];
      float[] phaseHist = new float[frames];
      java.io.DataOutputStream dump = null;
      double pos = 0, old = 0;
      int fi = 0;
      for (int t = 0; t < ticks; t++) {
         old = pos;
         pos += v;
         walk.update(Math.min((float)(pos - old) * 4.0F, 1.0F), 0.4F);
         for (int k = 0; k < sub; k++) {
            float pt = k / (float)sub;
            float now = t + pt;
            in.limbSwing = walk.position(pt);
            in.amount = Math.min(1.0F, walk.speed(pt));
            in.age = now;
            double x = old + (pos - old) * pt;
            if (gait) {
               TRACK.invoke(null, track, info, style, x, 0.0, 0.0F, now, 1.0F, v, walk.position(pt), walk.speed(pt));
               FILL.invoke(null, track, style, in);
            }
            if (t < warm) continue;
            WildlifeRig.pose(m, in, M);
            MeshSkinner.skinModel(m, M, sk);
            body[fi] = (float)x;
            if (dumpName != null && fi % 2 == 0 && fi < 2 * 48) {
               if (dump == null) {
                  dump = new java.io.DataOutputStream(new java.io.BufferedOutputStream(new java.io.FileOutputStream(new File(DUMP, dumpName + ".bin"))));
                  dump.writeInt(nv);
               }
               dump.writeFloat((float)x);
               java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate(nv * 24).order(java.nio.ByteOrder.BIG_ENDIAN);
               for (int i = 0; i < nv * 6; i++) bb.putFloat(sk[i]);
               dump.write(bb.array());
            }
            if (gait) phaseHist[fi] = (float)track.getClass().getField("phase").getFloat(track);
            for (int l = 0; l < 4; l++) {
               float my = Float.MAX_VALUE, mz = 0;
               int[] sl = soles.get(l);
               for (int i : sl) {
                  if (DEBUG && sk[i * 6 + 1] < -0.03F) System.out.printf("  low leg %d v%d y %.3f bind %.3f %.3f %.3f bone %s%n", l, i, sk[i * 6 + 1], m.pos[i * 3], m.pos[i * 3 + 1], m.pos[i * 3 + 2], m.names[dominant(m, i)]);
                  my = Math.min(my, sk[i * 6 + 1]);
                  mz += sk[i * 6 + 2];
               }
               y[l][fi] = my;
               z[l][fi] = mz / Math.max(1, sl.length);
            }
            if (trace != null) {
               trace.printf(Locale.ROOT, "%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f%n", fi, x, y[0][fi], x - z[0][fi], y[1][fi], x - z[1][fi],
                  y[2][fi], x - z[2][fi], y[3][fi], x - z[3][fi]);
            }
            fi++;
         }
      }
      if (dump != null) dump.close();
      float[][] r = new float[5][5];
      float H = m.meta[0];
      for (int l = 0; l < 4; l++) {
         float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE, zlo = Float.MAX_VALUE, zhi = -Float.MAX_VALUE;
         for (int i = 0; i < frames; i++) {
            lo = Math.min(lo, y[l][i]);
            hi = Math.max(hi, y[l][i]);
            zlo = Math.min(zlo, z[l][i]);
            zhi = Math.max(zhi, z[l][i]);
         }
         float tol = 0.006F * H + 0.004F;
         double moved = 0, foot = 0;
         int on = 0;
         for (int i = 1; i < frames; i++) {
            if (y[l][i] <= lo + tol && y[l][i - 1] <= lo + tol) {
               on++;
               // world fore-aft: body position + (-z) (the head is toward -z)
               double w1 = body[i] - z[l][i], w0 = body[i - 1] - z[l][i - 1];
               foot += Math.abs(w1 - w0);
               moved += Math.abs(body[i] - body[i - 1]);
            }
         }
         r[l][0] = moved > 1e-6 ? (float)(foot / moved) : 1.0F;
         r[l][1] = zhi - zlo;
         r[l][2] = hi - lo;
         r[l][3] = lo;
         r[l][4] = on / (float)(frames - 1);
      }
      // stride frequency: phase wraps (new rig) or vanilla limbSwing rate x stride meta / 2pi (old rig)
      if (gait) {
         int wraps = 0;
         for (int i = 1; i < frames; i++) if (phaseHist[i] < phaseHist[i - 1] - 0.5F) wraps++;
         r[4][0] = wraps / ((frames / (float)sub) / 20.0F);
      } else {
         r[4][0] = Math.min(1.0F, v * 4.0F) * m.meta[5] * 20.0F / (float)(2 * Math.PI);
      }
      return r;
   }
}
