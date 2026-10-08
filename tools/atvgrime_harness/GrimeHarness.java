// Offline check of the grime simulation rates (no Minecraft runtime needed).
// javac -cp "<compiled src>:$(cat /home/claude/fh/cp62.txt)" -d /tmp/h tools/atvgrime_harness/GrimeHarness.java
// java -cp "/tmp/h:<compiled src>:$(cat /home/claude/fh/cp62.txt)" com.formaworks.frontierhunts.landscape.ride.grime.GrimeHarness
package com.formaworks.frontierhunts.landscape.ride.grime;

public class GrimeHarness {
    static AtvGrime g = new AtvGrime();
    static AtvGrime.Sample s = new AtvGrime.Sample();
    static void run(String label, int ticks, float mud, float snow, float depth, float speed) {
        s.mudAvg = mud; s.snowAvg = snow; s.depth = depth; s.contact = 4;
        for (int i = 0; i < ticks; i++) g.simulate(s, speed);
        System.out.printf("%-48s t=%5ds  mudLow %.2f (L%d) mudHigh %.2f (L%d) moist %.2f snow %.2f (L%d) wet %.2f%n", label, ticks / 20,
            g.mudLow, AtvGrime.mudLevel(g.mudLow), g.mudHigh, AtvGrime.mudLevel(g.mudHigh), g.moist, g.snow, AtvGrime.snowLevel(g.snow), g.wet);
    }
    public static void main(String[] a) {
        run("slow 0.2 b/t through mud, 10 blocks", 50, 1f, 0, 0, 0.2f);
        run("fast 0.7 b/t through mud, 35 blocks", 50, 1f, 0, 0, 0.7f);
        run("fast 0.9 b/t through mud, 90 more blocks", 100, 1f, 0, 0, 0.9f);
        run("drive on clean ground 0.6, 1 min", 1200, 0, 0, 0, 0.6f);
        run("parked 3 min", 3600, 0, 0, 0, 0f);
        run("parked 30 min", 36000, 0, 0, 0, 0f);
        g = new AtvGrime();
        run("fast mud 0.8, 60 blocks", 75, 1f, 0, 0, 0.8f);
        run("shallow water 0.3 deep at 0.4, 5s", 100, 0, 0, 0.3f, 0.4f);
        run("1-block water at 0.3, 5s", 100, 0, 0, 0.89f, 0.3f);
        run("1-block water at 0.3, 5s more", 100, 0, 0, 0.89f, 0.3f);
        run("out of water, 1 min", 1200, 0, 0, 0, 0.3f);
        g = new AtvGrime();
        run("snow 0.5 b/t, 50 blocks", 100, 0, 1f, 0, 0.5f);
        run("snow 0.9 b/t, 90 blocks", 100, 0, 1f, 0, 0.9f);
        run("temperate parked 1 min", 1200, 0, 0, 0, 0f);
        run("temperate parked 2 min", 2400, 0, 0, 0, 0f);
        g = new AtvGrime();
        run("rain-wet dirt (0.55) at 0.6, 1 min", 1200, 0.55f, 0, 0, 0.6f);
        // [atv2] fade / rain / hub-deep water timings for a heavy wet coat
        System.out.println("-- [atv2] heavy coat, parked, temperate (target: mostly gone 8-10 min, never instantly)");
        heavy();
        for (int m = 1; m <= 10; m++) run("parked " + m + " min", 1200, 0, 0, 0, 0f);
        System.out.println("-- [atv2] heavy coat, driving on clean ground at 0.6");
        heavy();
        for (int m = 1; m <= 7; m++) run("clean ground " + m + " min", 1200, 0, 0, 0, 0.6f);
        System.out.println("-- [atv2] heavy coat, rain under open sky (target 60-90 s)");
        heavy();
        rain(true);
        for (int k = 1; k <= 6; k++) run("rain " + 15 * k + " s", 300, 0, 0, 0, 0f);
        rain(false);
        System.out.println("-- [atv2] heavy coat, hub-deep water (0.35), parked / driving 0.5 (target: a few seconds)");
        heavy();
        for (int k = 1; k <= 4; k++) run("hub-deep parked " + 2 * k + " s", 40, 0, 0, 0.35f, 0f);
        heavy();
        for (int k = 1; k <= 4; k++) run("hub-deep driving " + 2 * k + " s", 40, 0, 0, 0.35f, 0.5f);
    }

    static void heavy() {
        g = new AtvGrime();
        g.mudLow = 1f; g.mudHigh = 1f; g.moist = 1f;
    }

    static void rain(boolean on) {
        try {
            java.lang.reflect.Field f = AtvGrime.class.getDeclaredField("rainingHere");
            f.setAccessible(true);
            f.setBoolean(g, on);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
