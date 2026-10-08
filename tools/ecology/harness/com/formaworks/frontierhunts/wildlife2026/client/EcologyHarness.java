package com.formaworks.frontierhunts.wildlife2026.client;

import com.formaworks.frontierhunts.ecology.Predator;
import com.formaworks.frontierhunts.ecology.Prey;
import com.formaworks.frontierhunts.ecology.KillRecord;
import java.io.ByteArrayInputStream;
import java.util.zip.ZipFile;

/**
 * [ecology] Offline checks: (1) hunt poses keep the paws on the ground (posed toe tips vs. ground) and never produce
 * NaN, for every predator mesh, at full and partial weights; (2) most runs fail and appetite rules hold; (3) kill
 * records round-trip through NBT.
 */
public final class EcologyHarness {
   static int fails;

   static void check(boolean ok, String what) {
      if (!ok) {
         fails++;
         System.out.println("FAIL " + what);
      }
   }

   public static void main(String[] a) throws Exception {
      String jar = a[0];
      try (ZipFile z = new ZipFile(jar)) {
         for (String sp : new String[]{"wolf", "coyote", "cougar", "panther", "lion", "cheetah", "grizzly", "black_bear", "polar_bear"}) {
            for (String v : new String[]{"ultra", "bal"}) {
               var e = z.getEntry("assets/frontierhunts/models/wildlife/" + sp + "_" + v + ".fhsk");
               if (e == null) continue;
               SkinnedMesh m = SkinnedMesh.parse(new ByteArrayInputStream(z.getInputStream(e).readAllBytes()));
               // the mesh's own standing pose (no hunt weights): the reference the paws must keep
               float[] baseY = new float[4];
               {
                  WildlifeRig.Input in0 = new WildlifeRig.Input();
                  in0.age = 37.0F;
                  float[] o0 = new float[m.names.length * 12];
                  WildlifeRig.pose(m, in0, o0);
                  String[] lg = {"fl", "fr", "bl", "br"};
                  for (int l = 0; l < 4; l++) {
                     int bb = m.bone(lg[l] + "_toe");
                     baseY[l] = o0[bb * 12 + 4] * m.joint[bb * 3] + o0[bb * 12 + 6] * m.joint[bb * 3 + 2] + o0[bb * 12 + 7];
                  }
               }
               for (int pose = 0; pose < 5; pose++) {
                  for (float w : new float[]{0.35F, 1.0F}) {
                     WildlifeRig.Input in = new WildlifeRig.Input();
                     in.age = 37.0F;
                     in.eco[pose] = w * (pose == 0 ? (sp.contains("bear") ? 0.2F : sp.equals("wolf") || sp.equals("coyote") ? 0.45F : sp.equals("cheetah") ? 0.6F : 1.0F) : 1.0F);
                     float[] out = new float[m.names.length * 12];
                     WildlifeRig.pose(m, in, out);
                     String[] legs = {"fl", "fr", "bl", "br"};
                     float minY = 9, maxY = -9, worst = 0;
                     for (int li = 0; li < 4; li++) {
                        String l = legs[li];
                        int b = m.bone(l + "_toe");
                        float jx = m.joint[b * 3], jz = m.joint[b * 3 + 2];
                        int o = b * 12;
                        float y = out[o + 4] * jx + out[o + 5] * 0.0F + out[o + 6] * jz + out[o + 7];
                        check(Float.isFinite(y), sp + " NaN toe");
                        minY = Math.min(minY, y);
                        maxY = Math.max(maxY, y);
                        worst = Math.max(worst, Math.abs(y - baseY[li]));
                     }
                     for (float f : out) check(Float.isFinite(f), sp + " NaN matrix");
                     String[] names = {"stalk", "chase", "pounce", "tear", "howl"};
                     // the pounce is airborne, the chase is a gallop pose: only the ground poses must plant the paws
                     if (pose == 0 || pose == 3 || pose == 4) {
                        float H = m.meta[0];
                        check(worst < 0.03F * H, String.format("%s %s %s w=%.2f paws off their standing height by %.3f (H %.2f)", sp, v, names[pose], w, worst, H));
                     }
                     if (v.equals("ultra") && System.getProperty("legs") != null) {
                        StringBuilder sb = new StringBuilder();
                        for (String l : legs) {
                           int bb = m.bone(l + "_toe");
                           int o2 = bb * 12;
                           float yy = out[o2 + 4] * m.joint[bb * 3] + out[o2 + 6] * m.joint[bb * 3 + 2] + out[o2 + 7];
                           float zz = out[o2 + 8] * m.joint[bb * 3] + out[o2 + 10] * m.joint[bb * 3 + 2] + out[o2 + 11];
                           sb.append(String.format(" %s y%.3f z%.3f(rest z %.3f)", l, yy, zz, m.joint[bb * 3 + 2]));
                        }
                        System.out.println("   " + sp + " " + names[pose] + sb);
                     }
                     if (v.equals("ultra") && w == 1.0F) {
                        int pel = m.bone("pelvis");
                        System.out.printf("%-10s %-6s paws y %.3f..%.3f  root dy %.3f%n", sp, names[pose], minY, maxY, out[pel * 12 + 7] - (m.joint[pel * 3 + 1] - (out[pel * 12 + 4] * m.joint[pel * 3] + out[pel * 12 + 5] * m.joint[pel * 3 + 1] + out[pel * 12 + 6] * m.joint[pel * 3 + 2])));
                     }
                  }
               }
            }
         }
      }
      // (2) odds: averaged over typical prey, most runs fail
      Prey doe = new Prey(Prey.Kind.WHITETAIL, "whitetail doe", false, false, false, true);
      Prey yearling = new Prey(Prey.Kind.WHITETAIL, "whitetail yearling", true, false, false, true);
      Prey moose = new Prey(Prey.Kind.MOOSE, "moose cow", false, false, false, false);
      Prey bison = new Prey(Prey.Kind.BISON, "bison", false, false, true, true);
      for (Predator p : Predator.values()) {
         float o = p.odds(doe, 3);
         System.out.printf("%-10s doe odds %.2f  yearling %.2f  appetite(doe,1)=%.2f (bison,1)=%.2f (bison,4)=%.2f%n", p, o, p.odds(yearling, 3), p.appetite(doe, 1), p.appetite(bison, 1), p.appetite(bison, 4));
         check(o < 0.6F, p + " odds too high");
      }
      check(Predator.WOLF.appetite(moose, 1) == 0.0F, "lone wolf on moose");
      check(Predator.WOLF.appetite(bison, 5) == 0.0F, "wolves on a healthy bison");
      check(Predator.WOLF.odds(moose, 4) < 0.1F, "moose odds");
      check(Predator.COYOTE.appetite(doe, 1) == 0.0F, "lone coyote on a doe");
      for (long t = 0; t < 24000; t += 500) {
         for (Predator.Time tm : Predator.Time.values()) {
            float w = tm.weight(t);
            check(w >= 0 && w <= 1, "time weight");
         }
      }
      check(Predator.Time.NIGHT.weight(6000) < 0.2F && Predator.Time.NIGHT.weight(12500) == 1.0F, "night hunters at noon / dusk");
      check(Predator.Time.DAY.weight(6000) == 1.0F && Predator.Time.DAY.weight(18000) < 0.1F, "cheetah by day");
      // (3) record round trip
      KillRecord r = new KillRecord("WOLF", 3, "whitetail doe", "WHITETAIL", false, 123456L);
      r.feed(0.4F);
      r.visited("grizzly");
      KillRecord r2 = KillRecord.load(r.save());
      check(r2.pack == 3 && r2.prey.equals("whitetail doe") && Math.abs(r2.fed - 0.4F) < 1e-6 && r2.visitors.equals("grizzly") && r2.killedAt == 123456L
         && r2.predatorKind() == Predator.WOLF && r2.preyKind() == Prey.Kind.WHITETAIL, "record round trip");
      check(r.aged(1000).killedAt == 122456L && r.aged(1000).fed == r.fed, "aged");
      r.feed(Float.NaN);
      r.feed(5.0F);
      check(r.fed == 1.0F, "feed clamps");
      System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILURES");
      System.exit(fails == 0 ? 0 : 1);
   }
}
