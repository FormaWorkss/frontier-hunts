package com.formaworks.frontierhunts.shelter;

import com.formaworks.frontierhunts.survival.SurvivalMath;
import java.util.Locale;

/**
 * [shelter] Offline harness: the shared shelter detector on synthetic block grids and the shelter/fire thermal maths.
 * Run with tools/shelter/harness/run.sh. Prints a table and PASS/FAIL per expectation; exit code 1 on any failure.
 */
public final class ShelterHarness {
    static int fails;

    // ------------------------------------------------------------------ synthetic world
    static final class Box implements ShelterScan.Grid {
        static final int N = 48, H = 48, O = 24; // x,z in [-24, 24), y in [0, 48)
        final byte[] b = new byte[N * N * H];
        int reads;

        static int i(int x, int y, int z) { return ((x + O) * N + (z + O)) * H + y; }
        boolean in(int x, int y, int z) { return x >= -O && x < O && z >= -O && z < O && y >= 0 && y < H; }

        Box set(int x, int y, int z, byte c) { if (in(x, y, z)) b[i(x, y, z)] = c; return this; }

        Box fill(int x0, int y0, int z0, int x1, int y1, int z1, byte c) {
            for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
                for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
                    for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) set(x, y, z, c);
            return this;
        }

        /** Hollow box: walls + roof + floor (floor at y0). */
        Box room(int x0, int y0, int z0, int x1, int y1, int z1, byte c) {
            fill(x0, y0, z0, x1, y1, z1, c);
            fill(x0 + 1, y0 + 1, z0 + 1, x1 - 1, y1 - 1, z1 - 1, ShelterScan.AIR);
            return this;
        }

        @Override
        public byte at(int x, int y, int z) {
            reads++;
            return in(x, y, z) ? b[i(x, y, z)] : ShelterScan.AIR;
        }

        @Override
        public int top(int x, int z) {
            if (x < -O || x >= O || z < -O || z >= O) return 0;
            for (int y = H - 1; y >= 0; y--) if (b[i(x, y, z)] != ShelterScan.AIR) return y + 1;
            return 0;
        }

        Box ground() { return fill(-O, 0, -O, O - 1, 9, O - 1, ShelterScan.SOLID); } // ground top at y=9, stand at y=10
    }

    static final int O = Box.O;
    static final int GY = 10; // feet block on flat ground; eye block = GY + 1

    static ShelterScan.Result scan(Box g, int x, int eyeY, int z, int sky) {
        g.reads = 0;
        return ShelterScan.scan(g, x, eyeY, z, sky, new ShelterScan.Result());
    }

    static void expect(String name, ShelterScan.Result r, int reads, float encLo, float encHi, float windLo, float windHi, float precLo, float precHi) {
        boolean ok = r.enclosure >= encLo - 1e-4 && r.enclosure <= encHi + 1e-4 && r.windBlock >= windLo - 1e-4 && r.windBlock <= windHi + 1e-4
                && r.precipBlock >= precLo - 1e-4 && r.precipBlock <= precHi + 1e-4;
        if (!ok) fails++;
        System.out.printf(Locale.ROOT, "%-4s %-34s enc %.2f wind %.2f precip %.2f | %s | %3d reads%n", ok ? "PASS" : "FAIL", name, r.enclosure, r.windBlock,
                r.precipBlock, r, reads);
    }

    // ------------------------------------------------------------------ scenes
    static void shelterScenes() {
        System.out.println("== shelter detector on synthetic grids (eye block, sky light) ==");
        ShelterScan.Result r;

        Box open = new Box().ground();
        r = scan(open, 0, GY + 1, 0, 15);
        expect("open field", r, open.reads, 0f, 0.02f, 0f, 0.02f, 0f, 0.02f);

        // small cabin: 5x5 outside (3x3 inside), walls y10..12, roof y13, door in the south wall (x=0, z=2), y10-11
        Box cabin = new Box().ground().room(-2, 9, -2, 2, 13, 2, ShelterScan.SOLID);
        r = scan(cabin, 0, GY + 1, 0, 6);
        expect("cabin, door closed", r, cabin.reads, 0.98f, 1f, 0.98f, 1f, 1f, 1f);
        cabin.fill(0, 10, 2, 0, 11, 2, ShelterScan.AIR);
        r = scan(cabin, 0, GY + 1, 0, 11);
        expect("cabin, door open (centre)", r, cabin.reads, 0.8f, 0.97f, 0.75f, 0.97f, 1f, 1f);
        r = scan(cabin, 0, GY + 1, 1, 12);
        expect("cabin, door open (by the door)", r, cabin.reads, 0.55f, 0.9f, 0.6f, 0.95f, 0.7f, 1f);
        r = scan(cabin, 0, GY + 1, 2, 14);
        expect("standing in the doorway", r, cabin.reads, 0.3f, 0.75f, 0.3f, 0.8f, 0.55f, 0.9f);
        r = scan(cabin, 0, GY + 1, 4, 15);
        expect("outside, 2 blocks from the door", r, cabin.reads, 0f, 0.1f, 0f, 0.35f, 0f, 0.3f);

        // cabin with glass windows (glass = SOLID) and one window gap missing (broken pane)
        Box win = new Box().ground().room(-3, 9, -3, 3, 13, 3, ShelterScan.SOLID);
        win.set(-3, 11, 0, ShelterScan.AIR);
        r = scan(win, 0, GY + 1, 0, 9);
        expect("lodge, one open window", r, win.reads, 0.85f, 1f, 0.85f, 1f, 1f, 1f);

        // compact tent: 3x2x3 tent cells (all TENT), eye in the upper row
        Box tent = new Box().ground().fill(-1, 10, 0, 1, 11, 2, ShelterScan.TENT);
        r = scan(tent, 0, GY + 1, 1, 15);
        expect("tent, door closed", r, tent.reads, 1f, 1f, 0.95f, 1f, 1f, 1f);
        tent.fill(-1, 10, 0, 1, 11, 2, ShelterScan.TENT_OPEN);
        r = scan(tent, 0, GY + 1, 1, 15);
        expect("tent, door open", r, tent.reads, 0.7f, 0.8f, 0.68f, 0.8f, 0.7f, 1f);
        r = scan(tent, 0, GY + 1, -1, 15);
        expect("outside the tent door", r, tent.reads, 0f, 0.1f, 0f, 0.3f, 0f, 0.1f);

        // walk-in camping tent: 5x3x5 canvas shell, air inside (3x2x3), standing eye in the middle
        Box camp = new Box().ground().room(-2, 9, -2, 2, 12, 2, ShelterScan.TENT).fill(-2, 9, -2, 2, 9, 2, ShelterScan.SOLID);
        r = scan(camp, 0, GY + 1, 0, 15);
        expect("camping tent, closed", r, camp.reads, 0.98f, 1f, 0.97f, 1f, 1f, 1f);
        check("camping tent counts as a tent (warmth, sound)", r.tent, "tent=" + r.tent);
        camp.fill(-2, 10, -2, 2, 12, 2, ShelterScan.TENT_OPEN).fill(-1, 10, -1, 1, 11, 1, ShelterScan.AIR);
        r = scan(camp, 0, GY + 1, 0, 15);
        expect("camping tent, flap open", r, camp.reads, 0.75f, 0.9f, 0.75f, 0.9f, 1f, 1f);

        // cave: solid hill with a tunnel 3 wide 3 high going in 12 blocks from x=-12
        Box cave = new Box().fill(-O, 0, -O, O - 1, 30, O - 1, ShelterScan.SOLID).fill(-O, 10, -1, 10, 12, 1, ShelterScan.AIR);
        r = scan(cave, 8, GY + 1, 0, 0);
        expect("cave, deep inside", r, cave.reads, 0.95f, 1f, 0.95f, 1f, 1f, 1f);
        r = scan(cave, -20, GY + 1, 0, 13);
        expect("cave tunnel near the mouth", r, cave.reads, 0.7f, 1f, 0.7f, 1f, 0.9f, 1f);

        // under a tree: trunk at (0,10..15,1), leaves 5x5 at y 14..16
        Box tree = new Box().ground().fill(-2, 14, -2, 2, 16, 3, ShelterScan.LEAVES).fill(0, 10, 1, 0, 15, 1, ShelterScan.SOLID);
        r = scan(tree, 0, GY + 1, 0, 14);
        expect("under a tree", r, tree.reads, 0f, 0.05f, 0f, 0.25f, 0.2f, 0.4f);

        // rock overhang: cliff wall at z <= -2, a ledge over z in [-1, 1] at y 13..14
        Box over = new Box().ground().fill(-O, 10, -O, O - 1, 20, -2, ShelterScan.SOLID).fill(-O, 13, -1, O - 1, 14, 1, ShelterScan.SOLID);
        r = scan(over, 0, GY + 1, 0, 13);
        expect("under a rock overhang", r, over.reads, 0.15f, 0.45f, 0.2f, 0.55f, 0.5f, 0.9f);

        // walled pen without a roof (fence ring 2 high)
        Box pen = new Box().ground().room(-3, 9, -3, 3, 11, 3, ShelterScan.SOLID).fill(-2, 11, -2, 2, 11, 2, ShelterScan.AIR);
        pen.fill(-3, 12, -3, 3, 12, 3, ShelterScan.AIR);
        r = scan(pen, 0, GY + 1, 0, 15);
        expect("walled pen, no roof", r, pen.reads, 0f, 0.05f, 0.4f, 0.55f, 0f, 0.02f);

        // ground blind (BLIND cells 3x2x3) and a tower blind's ladder (BLIND, nothing above)
        Box blind = new Box().ground().fill(-1, 10, -1, 1, 11, 1, ShelterScan.BLIND);
        r = scan(blind, 0, GY + 1, 0, 15);
        expect("ground blind", r, blind.reads, 0.94f, 1f, 0.9f, 1f, 0.94f, 1f);
        Box ladder = new Box().ground().fill(0, 10, 0, 0, 11, 0, ShelterScan.BLIND).fill(0, 10, 0, 0, 10, 0, ShelterScan.BLIND);
        r = scan(ladder, 0, GY + 1, 0, 15);
        expect("tower blind ladder (no cabin)", r, ladder.reads, 0.25f, 0.35f, 0f, 0.35f, 0.25f, 0.35f);

        // budget: worst case reads stay small
        int worst = Math.max(Math.max(open.reads, cabin.reads), Math.max(cave.reads, tree.reads));
        System.out.printf(Locale.ROOT, "%-4s worst-case block reads per scan: %d (budget 400)%n", worst <= 400 ? "PASS" : "FAIL", worst);
        if (worst > 400) fails++;

        // timing: scans per ms (pure logic; a live level adds chunk lookups)
        long t0 = System.nanoTime();
        ShelterScan.Result rr = new ShelterScan.Result();
        int n = 200000;
        for (int k = 0; k < n; k++) ShelterScan.scan((k & 1) == 0 ? cabin : tree, 0, GY + 1, k % 3 - 1, 10, rr);
        double us = (System.nanoTime() - t0) / 1000.0 / n;
        System.out.printf(Locale.ROOT, "info synthetic scan cost %.2f us (client runs it 4x/s: %.4f ms per second)%n", us, us * 4 / 1000.0);
    }

    // ------------------------------------------------------------------ thermal
    record Env(String name, float enclosure, float roof, float windBlock, float precipBlock, boolean tent, float under, float fire) {}

    /** Mirrors SurvivalService.update's felt-temperature composition (with Thermal's shelter and weather parts). */
    static float felt(float air, Env e, float blizzard, float windSpeed, float windProof, boolean sleeping, boolean bedroll) {
        float still = SurvivalMath.shelterAir(air, e.under(), e.roof(), e.enclosure(), e.tent() ? 1F : 0F);
        float room = SurvivalMath.heatedAir(still, e.enclosure(), e.fire());
        float exposed = 1F - Math.max(e.precipBlock(), e.windBlock());
        float weather = (4F + blizzard * 10F) * exposed; // thundering + blizzard
        float f = room + SurvivalMath.radiant(e.fire(), e.enclosure()) - weather - SurvivalMath.windChill(windSpeed, 1F - e.windBlock(), windProof);
        if (sleeping) f += SurvivalMath.sleepWarmth(bedroll, e.tent());
        return f;
    }

    static void thermal() {
        System.out.println();
        for (float a : new float[]{-17F, -32F}) table(a);
    }

    static void table(float air) {
        System.out.println();
        System.out.printf(Locale.ROOT, "== body heat in a full blizzard, air %.0f C (%s), wind 3 + blizzard 9 m/s ==%n", air,
                air > -20F ? "snowy plains, January night" : "high snowy ridge, January night");
        Env[] envs = {
                new Env("outside, open", 0F, 0F, 0F, 0F, false, 0F, 0F),
                new Env("outside, by a campfire (2 bl)", 0F, 0F, 0F, 0F, false, 0F, 11.7F),
                new Env("tent, door closed", 1F, 0F, 0.97F, 1F, true, 0F, 0F),
                new Env("tent + campfire at the door", 1F, 0F, 0.97F, 1F, true, 0F, 9.5F),
                new Env("cabin, no fire", 1F, 1F, 1F, 1F, false, 0.5F, 0F),
                new Env("cabin + campfire / stove", 1F, 1F, 1F, 1F, false, 0.5F, 11.7F),
                new Env("cabin door open + stove", 0.87F, 1F, 0.86F, 1F, false, 0.3F, 11.7F),
        };
        float wind = 12F;
        float[] insul = {1.0F, 4.4F};
        String[] outfit = {"bare clothes", "buckskin kit"};
        SurvivalMath.Mode[] modes = {SurvivalMath.Mode.LIGHT, SurvivalMath.Mode.BALANCED, SurvivalMath.Mode.HARDCORE};
        System.out.printf(Locale.ROOT, "%-32s %-13s %7s | %s%n", "place", "outfit", "felt C", "Light / Balanced / Hardcore: body heat after 10 min from 0; minutes to recover -95 -> -30 (chilly) / -> 0");
        for (Env e : envs) {
            for (int o = 0; o < insul.length; o++) {
                float f = felt(air, e, 1F, wind, o == 0 ? 0F : 0.6F, false, false);
                StringBuilder sb = new StringBuilder();
                for (SurvivalMath.Mode m : modes) {
                    float h = 0F;
                    for (int s = 0; s < 600; s++) h = SurvivalMath.stepHeat(h, f, insul[o], 1F, m.cold, 0F, 1F, e.enclosure());
                    if (m == SurvivalMath.Mode.LIGHT) h = Math.max(h, -85F);
                    float r = -95F;
                    int tChilly = -1, tZero = -1;
                    for (int s = 1; s <= 3600 && tZero < 0; s++) {
                        r = SurvivalMath.stepHeat(r, f, insul[o], 1F, m.cold, 0F, 1F, e.enclosure());
                        if (tChilly < 0 && r >= SurvivalMath.CHILLY) tChilly = s;
                        if (r >= -0.5F) tZero = s;
                    }
                    sb.append(String.format(Locale.ROOT, "%6.0f %5s/%-5s  ", h, tChilly < 0 ? "never" : String.format(Locale.ROOT, "%.1f", tChilly / 60F),
                            tZero < 0 ? "never" : String.format(Locale.ROOT, "%.1f", tZero / 60F)));
                }
                System.out.printf(Locale.ROOT, "%-32s %-13s %7.1f | %s%n", e.name(), outfit[o], f, sb);
            }
        }
        if (air > -30F) return;
        // expectations (harsh case)
        Env cabinFire = envs[5], tentFire = envs[3], tentClosed = envs[2], outside = envs[0], outsideFire = envs[1];
        for (SurvivalMath.Mode m : modes) {
            for (Env e : new Env[]{cabinFire, tentFire}) {
                float f = felt(air, e, 1F, wind, 0F, false, false);
                float r = -95F;
                int t = -1;
                for (int s = 1; s <= 600; s++) {
                    r = SurvivalMath.stepHeat(r, f, 1.0F, 1F, m.cold, 0F, 1F, e.enclosure());
                    if (t < 0 && r >= SurvivalMath.SHIVER) t = s;
                }
                check(m + " " + e.name() + ", bare: shivering stops < 60 s and comfortable within 10 min", t > 0 && t <= 60 && r >= -1F,
                        String.format(Locale.ROOT, "shiver stops after %d s, heat %.0f after 10 min", t, r));
            }
            float fo = felt(air, outside, 1F, wind, 0F, false, false);
            float h = 0F;
            for (int s = 0; s < 600; s++) h = SurvivalMath.stepHeat(h, fo, 1.0F, 1F, m.cold, 0F, 1F, 0F);
            if (m != SurvivalMath.Mode.LIGHT) check(m + " outside in the blizzard still freezes you", h <= -88F, String.format(Locale.ROOT, "heat %.0f after 10 min", h));
        }
        float ft = felt(air, tentClosed, 1F, wind, 0.6F, false, false), fo = felt(air, outside, 1F, wind, 0.6F, false, false);
        check("a closed tent is far warmer than the open blizzard (>= 30 C felt)", ft - fo >= 30F, String.format(Locale.ROOT, "tent %.1f vs open %.1f", ft, fo));
        float fof = felt(air, outsideFire, 1F, wind, 0F, false, false), fcf = felt(air, cabinFire, 1F, wind, 0F, false, false);
        check("an enclosed fire is much warmer than the same fire in the open", fcf - fof >= 25F, String.format(Locale.ROOT, "cabin+fire %.1f vs open+fire %.1f", fcf, fof));
        float sleepTent = felt(air, tentClosed, 1F, wind, 0F, true, true);
        check("sleeping in a closed tent on a hide bedroll is comfortable in buckskin (> comfort floor -7)", sleepTent > SurvivalMath.comfortLow(4.4F),
                String.format(Locale.ROOT, "felt %.1f", sleepTent));
        float clear = felt(-8F, new Env("cabin", 1F, 1F, 1F, 1F, false, 0.5F, 0F), 0F, 3F, 0F, false, false);
        check("an unheated cabin on a clear winter night is not freezing (bare: felt > 0 C)", clear > 0F, String.format(Locale.ROOT, "felt %.1f", clear));
    }

    static void check(String name, boolean ok, String detail) {
        if (!ok) fails++;
        System.out.printf(Locale.ROOT, "%-4s %s (%s)%n", ok ? "PASS" : "FAIL", name, detail);
    }

    public static void main(String[] args) {
        shelterScenes();
        thermal();
        System.out.println();
        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILED");
        if (fails > 0) System.exit(1);
    }
}
