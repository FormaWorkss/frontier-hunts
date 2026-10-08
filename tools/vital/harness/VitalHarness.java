import com.formaworks.frontierhunts.vital.WildlifeVitals;
import com.formaworks.frontierhunts.vital.WildlifeVitals.Zone;
import com.formaworks.frontierhunts.wildlife2026.WildlifeSpecies;
import net.minecraft.world.phys.Vec3;

/** [vital] offline check of the wildlife shot-placement trace. Animal at the origin facing +Z (yaw 0), side = +X. */
public class VitalHarness {
   static int fails;

   static Zone shot(WildlifeSpecies s, double f, double y, double dx, double dz, float energy, float yaw) {
      // aim at body point (side 0, forward f, height y) from 20 blocks away along (dx, dz) in body frame
      double a = Math.toRadians(yaw);
      Vec3 fw = new Vec3(-Math.sin(a), 0, Math.cos(a)), sd = new Vec3(Math.cos(a), 0, Math.sin(a));
      Vec3 target = fw.scale(f).add(0, y, 0);
      Vec3 dir = sd.scale(dx).add(fw.scale(dz)).normalize();
      Vec3 start = target.subtract(dir.scale(20));
      return WildlifeVitals.classify(s, Vec3.ZERO, yaw, 1.0F, false, start, dir, energy, true).zone();
   }

   static void expect(String what, Zone got, Zone... ok) {
      for (Zone z : ok) if (z == got) { System.out.println("PASS " + what + " -> " + got); return; }
      fails++;
      System.out.println("FAIL " + what + " -> " + got + " expected " + java.util.Arrays.toString(ok));
   }

   public static void main(String[] a) {
      for (WildlifeSpecies s : WildlifeSpecies.values()) {
         if (s.existing()) continue;
         double[] t = torso(s); // hw bot top front rear leg headF headY headR
         double L = t[3] + t[4], H = t[2] - t[1];
         double lu = (t[3] - t[5]) / L;
         double heartF = t[3] - (lu + 0.045) * L, heartY = t[1] + 0.3 * H;
         double lungF = t[3] - (lu + 0.12) * L, lungY = t[1] + 0.6 * H;
         for (float yaw : new float[]{0F, 73F, -140F}) {
            String n = s.id + "@" + (int)yaw;
            if (s.bird) {
               expect(n + " bird core", shot(s, t[3] - 0.42 * L, t[1] + 0.5 * H, -1, 0, 1F, yaw), Zone.HEART);
               expect(n + " bird tail", shot(s, -t[4] + 0.02, t[1] + 0.8 * H, -1, 0, 1F, yaw), Zone.FLESH);
               continue;
            }
            expect(n + " broadside heart", shot(s, heartF, heartY, -1, 0, 1F, yaw), Zone.HEART);
            expect(n + " broadside lungs", shot(s, lungF, lungY, -1, 0, 1F, yaw), Zone.DOUBLE_LUNG);
            expect(n + " quartering lungs", shot(s, lungF, lungY, -0.7, 0.7, 0.03F, yaw), Zone.LUNG, Zone.DOUBLE_LUNG, Zone.HEART);
            expect(n + " liver", shot(s, t[3] - (lu + 0.37) * L, t[1] + 0.5 * H, -1, 0, 1F, yaw), Zone.LIVER);
            expect(n + " gut", shot(s, t[3] - 0.7 * L, t[1] + 0.35 * H, -1, 0, 1F, yaw), Zone.GUT);
            expect(n + " ham", shot(s, -t[4] + 0.03 * L, t[1] + 0.7 * H, -1, 0, 1F, yaw), Zone.FLESH);
            expect(n + " high back", shot(s, t[3] - 0.6 * L, t[2] - 0.03 * H, -1, 0, 1F, yaw), Zone.FLESH);
            expect(n + " front leg", shot(s, t[5], t[1] * 0.5, -1, 0, 1F, yaw), Zone.LEG);
            expect(n + " head", shot(s, t[6], t[7], -1, 0, 1F, yaw), Zone.HEAD);
            expect(n + " over the back", shot(s, 0, t[2] + 0.15, -1, 0, 1F, yaw), Zone.GRAZE);
            expect(n + " weak arrow far lung", shot(s, lungF, lungY, -1, 0, 0.0F, yaw), Zone.LUNG, Zone.DOUBLE_LUNG);
         }
      }
      System.out.println(fails == 0 ? "ALL PASS" : fails + " FAIL");
      System.exit(fails == 0 ? 0 : 1);
   }

   static double[] torso(WildlifeSpecies s) {
      try {
         java.lang.reflect.Method m = WildlifeVitals.class.getDeclaredMethod("torso", WildlifeSpecies.class);
         m.setAccessible(true);
         Object t = m.invoke(null, s);
         String[] names = {"hw", "bot", "top", "front", "rear", "leg", "headF", "headY", "headR"};
         double[] r = new double[names.length];
         for (int i = 0; i < names.length; i++) {
            java.lang.reflect.Method g = t.getClass().getDeclaredMethod(names[i]);
            g.setAccessible(true);
            r[i] = (double)g.invoke(t);
         }
         return r;
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }
}
