package com.formaworks.frontierhunts.season.client;

import com.formaworks.frontierhunts.season.FoliageSeason;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The seasonal stage chunk meshing draws with, published by the client thread ({@link SeasonalClient}); read by
 * block colour handlers, leaf models and realistic trees on the mesh threads. Every seasonal look in a chunk is
 * drawn at {@link #time()}, the centre of the current quarter-month stage, so sections rebuilt at different moments
 * of one stage always match. Also records which sections hold seasonal content, so a stage change rebuilds only
 * those (a few per tick).
 */
public final class SeasonView {
    private SeasonView() {}

    private static volatile boolean on = true;
    private static volatile int key = Integer.MIN_VALUE;
    private static volatile float time = 6.2F;

    /** Seasons drive the look (false = the world's normal, all-year look). */
    public static boolean on() { return on; }
    /** Stage time in months (0..12). */
    public static float time() { return time; }
    public static int key() { return key; }

    private static volatile float[] grass = {0, 0, 0, 0};

    /** Seasonal grass colour target (rgb) and weight for the current stage. */
    public static float[] grassTarget() { return grass; }
    public static float grassWeight() { return grass[3]; }

    static void publish(boolean enabled, int stageKey) {
        float[] g = new float[4];
        FoliageSeason.grass(FoliageSeason.stageTime(stageKey), g);
        grass = g;
        time = FoliageSeason.stageTime(stageKey);
        key = stageKey;
        on = enabled;
    }

    // ------------------------------------------------------------------ seasonal sections
    private static final Set<Long> SECTIONS = ConcurrentHashMap.newKeySet();
    private static final ThreadLocal<long[]> LAST = ThreadLocal.withInitial(() -> new long[]{Long.MIN_VALUE});

    /** Mesh threads: the section holding (x, y, z) draws something seasonal. */
    public static void track(int x, int y, int z) {
        long section = pack(x >> 4, y >> 4, z >> 4);
        long[] last = LAST.get();
        if (last[0] == section) return;
        last[0] = section;
        SECTIONS.add(section);
    }

    static Set<Long> sections() { return SECTIONS; }

    static long pack(int sx, int sy, int sz) {
        return ((long) sx & 0x3FFFFF) << 42 | ((long) sz & 0x3FFFFF) << 20 | ((long) sy & 0xFFFFF);
    }

    static int sx(long p) { return (int) (p >> 42); }
    static int sz(long p) { return (int) (p << 22 >> 42); }
    static int sy(long p) { return (int) (p << 44 >> 44); }
}
