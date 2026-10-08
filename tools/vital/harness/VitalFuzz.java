import com.formaworks.frontierhunts.vital.WildlifeVitals;
import com.formaworks.frontierhunts.wildlife2026.WildlifeSpecies;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.minecraft.world.phys.Vec3;

/**
 * [bugs] Fuzz harness for WildlifeVitals.classify: every species, random and extreme inputs (huge / NaN / infinite
 * coordinates, zero / NaN / tiny / huge directions, odd scales, yaws and energies). Asserts that every call terminates
 * quickly and returns a zone with a finite (or null) entry and finite energy.
 *
 * javac -cp "$(cat /home/claude/fh/cp62.txt):<classes>" -d <out> VitalFuzz.java
 * java  -cp "$(cat /home/claude/fh/cp62.txt):<classes>:<out>" VitalFuzz
 */
public class VitalFuzz {
   static final double[] EXTREME = {
      0.0, -0.0, 1.0E-300, -1.0E-12, 0.5, 29_999_984.0, -29_999_984.0, 3.0E7 + 0.123, 1.0E9, -1.0E13, 1.0E15, 1.0E17, 1.0E30,
      1.0E154, -1.0E200, Double.MAX_VALUE, -Double.MAX_VALUE, Double.MIN_VALUE, Double.NaN, Double.POSITIVE_INFINITY,
      Double.NEGATIVE_INFINITY
   };
   static final float[] EXTREME_F = {
      0.0F, -0.0F, 1.0E-30F, 0.2F, 1.0F, 16.0F, 1.0E9F, -5.0F, Float.MAX_VALUE, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY
   };
   static int fails;
   static long calls;

   static double pick(Random r) {
      return r.nextInt(3) == 0 ? EXTREME[r.nextInt(EXTREME.length)] : (r.nextDouble() - 0.5) * Math.pow(10, r.nextInt(9));
   }

   static float pickF(Random r) {
      return r.nextInt(3) == 0 ? EXTREME_F[r.nextInt(EXTREME_F.length)] : (float)(r.nextDouble() * 2.0);
   }

   static void check(String what, WildlifeVitals.Hit h) {
      calls++;
      boolean ok = h != null && h.zone() != null && Float.isFinite(h.energy())
         && (h.entry() == null || Double.isFinite(h.entry().x) && Double.isFinite(h.entry().y) && Double.isFinite(h.entry().z));
      if (!ok) {
         if (++fails < 20) {
            System.out.println("FAIL " + what + " -> " + h);
         }
      }
   }

   public static void main(String[] args) throws Exception {
      ExecutorService ex = Executors.newSingleThreadExecutor(r -> {
         Thread t = new Thread(r, "vital-fuzz");
         t.setDaemon(true);
         return t;
      });
      Future<?> job = ex.submit(VitalFuzz::run);
      try {
         job.get(120, TimeUnit.SECONDS);
      } catch (java.util.concurrent.TimeoutException e) {
         System.out.println("FAIL: classify did not terminate within 120 s (hang) after " + calls + " calls");
         System.exit(2);
      }
      System.out.println(calls + " calls, " + (fails == 0 ? "ALL PASS (terminates, finite outputs)" : fails + " FAIL"));
      System.exit(fails == 0 ? 0 : 1);
   }

   static void run() {
      Random r = new Random(0xF407L);
      for (WildlifeSpecies s : WildlifeSpecies.values()) {
         long t0 = System.nanoTime();
         // 1) the worst cases deterministically: every extreme value in each slot
         for (double e : EXTREME) {
            Vec3 o = new Vec3(e, 64, e);
            check(s + " origin " + e, WildlifeVitals.classify(s, o, 0F, 1F, false, new Vec3(0.3, 64.8, 0), new Vec3(-1, 0, 0), 1F, true));
            check(s + " point " + e, WildlifeVitals.classify(s, Vec3.ZERO, 0F, 1F, false, new Vec3(e, 0.8, 0), new Vec3(-1, 0, 0), 1F, true));
            check(s + " far point on line " + e, WildlifeVitals.classify(s, Vec3.ZERO, 0F, 1F, false, new Vec3(e, 0.8, 0.1), new Vec3(1, 0, 0), 1F, true));
            check(s + " dir " + e, WildlifeVitals.classify(s, Vec3.ZERO, 0F, 1F, true, new Vec3(1, 0.8, 0), new Vec3(e, 0, -e), 1F, true));
            check(s + " dir y " + e, WildlifeVitals.classify(s, Vec3.ZERO, 30F, 1F, false, new Vec3(0, 3, 0), new Vec3(0, e, 0), 0.5F, false));
         }
         for (float f : EXTREME_F) {
            check(s + " yaw " + f, WildlifeVitals.classify(s, Vec3.ZERO, f, 1F, false, new Vec3(2, 0.8, 0), new Vec3(-1, 0, 0), 1F, true));
            check(s + " scale " + f, WildlifeVitals.classify(s, Vec3.ZERO, 0F, f, false, new Vec3(2, 0.8, 0), new Vec3(-1, 0, 0), 1F, true));
            check(s + " energy " + f, WildlifeVitals.classify(s, Vec3.ZERO, 0F, 1F, false, new Vec3(2, 0.8, 0), new Vec3(-1, 0, 0), f, true));
         }
         // a real shot at the edge of the world border (large but legal coordinates) still classifies
         Vec3 edge = new Vec3(29_999_000.5, 70, -29_999_000.5);
         WildlifeVitals.Hit h = WildlifeVitals.classify(s, edge, 45F, 1F, false, edge.add(3, 0.7 * (s.bird ? 0.5 : 1.2), 0), new Vec3(-1, 0, 0), 1F, true);
         check(s + " world border", h);
         // 2) random soup
         for (int i = 0; i < 40_000; i++) {
            Vec3 o = new Vec3(pick(r), pick(r), pick(r));
            Vec3 p = r.nextBoolean() ? o.add(pick(r) * 1e-3, pick(r) * 1e-3, pick(r) * 1e-3) : new Vec3(pick(r), pick(r), pick(r));
            Vec3 d = new Vec3(pick(r), pick(r), pick(r));
            check(s + " random " + i, WildlifeVitals.classify(s, o, pickF(r) * 360F, pickF(r), r.nextBoolean(), p, d, pickF(r), r.nextBoolean()));
         }
         System.out.printf("%-12s ok in %d ms%n", s, (System.nanoTime() - t0) / 1_000_000);
      }
   }
}
