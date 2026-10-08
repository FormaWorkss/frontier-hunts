package com.formaworks.frontierhunts.survival;

import java.util.Locale;

/**
 * [survival] Offline checks of the pure Frontier Survival maths (no Minecraft needed).
 * Run: tools/survival/harness/run.sh
 */
public final class SurvivalHarness {
   static int fails;

   static void check(boolean ok, String what) {
      System.out.println((ok ? "  ok   " : "  FAIL ") + what);
      if (!ok) {
         fails++;
      }
   }

   public static void main(String[] a) {
      Locale.setDefault(Locale.ROOT);
      System.out.println("== drain per in-game day (idle / moderate work ~40 exhaustion)");
      for (SurvivalMath.Mode m : SurvivalMath.Mode.values()) {
         if (!m.on()) {
            continue;
         }
         float[] idle = new float[3], work = new float[3];
         for (int i = 0; i < 3; i++) {
            for (int s = 0; s < 1200; s++) {
               idle[i] += SurvivalMath.drain(i, 1F, 0F, 0F, 0F, m.drain, 1F);
               work[i] += SurvivalMath.drain(i, 1F, 40F / 1200F, 0F, 0F, m.drain, 1F);
            }
         }
         System.out.printf("  %-9s idle P%.0f F%.0f E%.0f | working P%.0f F%.0f E%.0f%n", m, idle[0], idle[1], idle[2], work[0], work[1], work[2]);
      }
      float e = 0;
      for (int s = 0; s < 1200; s++) {
         e += SurvivalMath.drain(2, 1F, 0F, 0F, 0F, 1F, 1F);
      }
      check(Math.abs(e - 40F) < 0.5F, "balanced idle energy drain is 40 per day (" + e + ")");

      System.out.println("== air temperature (summer day / winter night), plains 0.8, taiga 0.25, snowy 0.0, desert 2.0");
      for (float b : new float[]{0.8F, 0.25F, 0.0F, 2.0F, -0.7F}) {
         float sum = SurvivalMath.air(b, b < 0.9F && b > 0.1F, true, 6.5, 9000, 70, b >= 1.5F);
         float win = SurvivalMath.air(b, b < 0.9F && b > 0.1F, true, 0.5, 21000, 70, b >= 1.5F);
         System.out.printf("  biome %.2f: July 3pm %.1f C, January 3am %.1f C%n", b, sum, win);
      }
      check(SurvivalMath.air(0.8F, true, true, 6.5, 9000, 70, false) > 20F, "plains summer afternoon is warm");
      check(SurvivalMath.air(0.8F, true, true, 0.5, 21000, 70, false) < 0F, "plains winter night freezes");
      check(SurvivalMath.air(0.8F, true, false, 0.5, 9000, 70, false) > 15F, "seasons off: no winter");

      System.out.println("== body heat: minutes until shivering / freezing (balanced, standing still)");
      float[][] cases = {
         {-6F, 1F}, {-6F, 3F}, {-6F, 5F}, {-16F, 5F}, {-16F, 7.6F}, {-30F, 8.4F}, {4F, 1F}, {10F, 1F}
      };
      String[] names = {
         "plains Jan night, bare", "plains Jan night, leather", "plains Jan night, buckskin", "taiga Jan night, buckskin", "taiga Jan night, bear fur kit",
         "peak blizzard, lined fur kit", "plains Oct night, bare", "spring evening, bare"
      };
      for (int i = 0; i < cases.length; i++) {
         float h = 0;
         int shiver = -1, freeze = -1;
         for (int s = 0; s < 1800; s++) {
            h = SurvivalMath.stepHeat(h, cases[i][0], cases[i][1], 1F, 1F, 0F, 1F);
            if (shiver < 0 && h <= SurvivalMath.SHIVER) {
               shiver = s;
            }
            if (freeze < 0 && h <= -88F) {
               freeze = s;
            }
         }
         System.out.printf("  %-32s comfort %.0fC: shiver %s, freezing %s%n", names[i], SurvivalMath.comfortLow(cases[i][1]),
            shiver < 0 ? "never" : String.format("%.1f min", shiver / 60F), freeze < 0 ? "never" : String.format("%.1f min", freeze / 60F));
      }
      float warm = -80F;
      int back = 0;
      while (warm < -1F && back < 600) {
         warm = SurvivalMath.stepHeat(warm, 20F, 1F, 1F, 1F, 0F, 1F);
         back++;
      }
      check(back < 240, "by a fire (+20C felt) a frozen body warms back in " + back + " s");
      float h1 = SurvivalMath.stepHeat(0F, -20F, 1F, 1F, 1F, 0F, 1F);
      float h2 = SurvivalMath.stepHeat(0F, -20F, 8F, 1F, 1F, 0F, 1F);
      check(h2 > h1, "more insulation loses less heat");
      check(SurvivalMath.stepHeat(-40F, 12F, 1F, 1F, 1F, 0F, 1F) > -40F, "comfortable air recovers body heat");
      check(SurvivalMath.stepHeat(0F, 45F, 1F, 1F, 1F, 0F, 1F) > 0F, "desert heat warms the body");
      // [1.2.5] a closed, roofed house warms a frozen body even at -35C outside, with no fire
      for (float out : new float[]{-10F, -25F, -35F}) {
         float house = SurvivalMath.shelterAir(out, 0F, 1F, 1F, 0F);
         check(SurvivalMath.stepHeat(-40F, house, 1F, 1F, 1F, 0F, 1F, 1F) > -40F,
            String.format("inside a house at %.0fC outside the air is %.1fC and a frozen body warms", out, house));
      }
      check(SurvivalMath.dugInAir(-30F) > -30F + 20F, "a hole in the ground holds the air well above the frost");

      System.out.println("== spoilage");
      long now = 100_000L;
      float shelf = 3F * SurvivalMath.DAY;
      long made = SurvivalMath.stamp(now);
      check(made <= now && now - made < SurvivalMath.BUCKET, "stamp buckets down");
      float f0 = SurvivalMath.spoil(made, now + 36000, SurvivalMath.AMBIENT, shelf);
      long cold = SurvivalMath.rebase(made, now + 36000, SurvivalMath.AMBIENT, SurvivalMath.COLD);
      float f1 = SurvivalMath.spoil(cold, now + 36000, SurvivalMath.COLD, shelf);
      check(Math.abs(f0 - f1) < 0.02F && f1 >= f0, "re-basing to cold keeps the spoil fraction (" + f0 + " -> " + f1 + ")");
      float later = SurvivalMath.spoil(cold, now + 36000 + 24000, SurvivalMath.COLD, shelf);
      check(later - f1 < 0.11F, "a day in cold storage spoils ~0.3 day's worth (" + (later - f1) + ")");
      long m = SurvivalMath.merge(1000L, 10, 50000L, 10);
      check(m <= 25500L && m >= 24000L, "merging averages stamps by count, snapped older (" + m + ")");
      long m2 = SurvivalMath.merge(made, 5, made, 5);
      check(m2 <= made, "merging equal stamps never refreshes");
      check(SurvivalMath.ticksLeft(made, now, SurvivalMath.FROZEN, shelf) > 50 * SurvivalMath.DAY, "frozen meat keeps for many weeks");
      check(SurvivalMath.storeFor(-10F) == SurvivalMath.FROZEN && SurvivalMath.storeFor(30F) == SurvivalMath.WARM, "store classes by air");
      check(SurvivalMath.freshnessValue(0.5F) == 1F && SurvivalMath.freshnessValue(0.99F) < 0.75F, "old meat nourishes less");

      System.out.println("== lean seasons (deer 84 kg, average condition)");
      String[] month = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
      for (int i = 0; i < 12; i++) {
         float c = SurvivalMath.animalCondition(i + 0.5, 50);
         System.out.printf("  %s condition %.2f  meat x%.2f  fat %d  elk fat %d  bear fat %d  spawns denied %.0f%%%n", month[i], c, SurvivalMath.meatYield(c),
            SurvivalMath.fatYield(c, 84, 1F), SurvivalMath.fatYield(c, 300, 1F), SurvivalMath.fatYield(c, 200, 2.5F), SurvivalMath.spawnDenial(i + 0.5) * 100F);
      }
      check(SurvivalMath.fatYield(SurvivalMath.animalCondition(2.5, 50), 84, 1F) == 0, "March deer carry no fat");
      check(SurvivalMath.fatYield(SurvivalMath.animalCondition(9.5, 50), 84, 1F) >= 1, "October deer carry fat");
      check(SurvivalMath.spawnDenial(7.5) == 0F && SurvivalMath.spawnDenial(2.5) > 0.3F, "thin herds only in late winter");
      check(SurvivalMath.yieldPhase(9.2) == 0 && SurvivalMath.yieldPhase(2.2) == 2, "yield phases");

      System.out.println("== effects");
      check(SurvivalMath.empties(5F, 50F, 5F) == 2, "empty meter count");
      check(SurvivalMath.vigor(80F, 70F, 60F, 1000L) && !SurvivalMath.vigor(80F, 70F, 60F, 40000L) && !SurvivalMath.vigor(80F, 70F, 60F, -1L), "vigor needs a recent game meal");
      check(SurvivalMath.sway(-80F, 80F) > 2F && SurvivalMath.sway(0F, 80F) == 1F, "shivering shakes the aim");
      check(SurvivalMath.Mode.BALANCED.starveFloor() == 1F && SurvivalMath.Mode.HARDCORE.starveFloor() == 0F, "starvation floor");
      System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILED");
      System.exit(fails == 0 ? 0 : 1);
   }
}
