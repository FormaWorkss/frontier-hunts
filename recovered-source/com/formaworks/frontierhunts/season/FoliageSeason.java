package com.formaworks.frontierhunts.season;

/**
 * Pure seasonal phenology for the world (no Minecraft types, both sides, unit-testable).
 *
 * <p>Time is the {@link SeasonClock} month position t (0..12, 0 = Jan 1). Every deciduous tree runs the same
 * yearly cycle, shifted per tree: bare winter crown, bud-burst (sparse, small, yellow-green sprays), leaf-out to
 * fresh green, summer (the tree's own texture colours, untouched), a late-summer olive tinge, the autumn turn to a
 * per-species palette (a colour picked per tree, so a hillside is a patchwork), peak, browning and leaf drop until
 * the crown is bare. Oaks keep a few dry brown leaves through winter (marcescence), as real oaks do.
 *
 * <p>Colours are "tint space" RGB (the same space as a biome foliage colour: greyscale leaf textures are multiplied
 * by it). A {@link Look} says how far the foliage colour moves from the tree's own summer green toward a target.
 */
public final class FoliageSeason {
    private FoliageSeason() {}

    // ------------------------------------------------------------------ species
    public enum Profile {
        //         bud   turn  peak  drop  bare   marc
        EVERGREEN(0, 0, 0, 0, 0, 0, null, null),
        MAPLE(2.10F, 8.35F, 9.25F, 9.45F, 10.35F, 0F, new float[][]{
            {0.86F, 0.15F, 0.08F}, {0.86F, 0.15F, 0.08F}, {0.92F, 0.30F, 0.07F}, {0.95F, 0.47F, 0.08F},
            {0.95F, 0.47F, 0.08F}, {0.93F, 0.65F, 0.12F}, {0.68F, 0.10F, 0.11F}}, new float[]{0.47F, 0.30F, 0.16F}),
        OAK(2.30F, 8.90F, 9.80F, 9.90F, 10.90F, 0.07F, new float[][]{
            {0.62F, 0.27F, 0.10F}, {0.58F, 0.38F, 0.14F}, {0.72F, 0.23F, 0.10F}, {0.52F, 0.34F, 0.15F},
            {0.64F, 0.46F, 0.16F}, {0.62F, 0.27F, 0.10F}}, new float[]{0.50F, 0.33F, 0.17F}),
        DARK_OAK(2.30F, 8.90F, 9.85F, 9.95F, 10.95F, 0.05F, new float[][]{
            {0.52F, 0.31F, 0.12F}, {0.58F, 0.37F, 0.14F}, {0.46F, 0.28F, 0.13F}, {0.62F, 0.43F, 0.16F}},
            new float[]{0.45F, 0.30F, 0.16F}),
        BIRCH(1.95F, 8.40F, 9.25F, 9.40F, 10.25F, 0F, new float[][]{
            {0.97F, 0.78F, 0.16F}, {0.94F, 0.70F, 0.12F}, {0.90F, 0.82F, 0.28F}, {0.98F, 0.62F, 0.10F},
            {0.97F, 0.78F, 0.16F}}, new float[]{0.55F, 0.40F, 0.18F}),
        CHERRY(1.90F, 8.50F, 9.30F, 9.45F, 10.30F, 0F, new float[][]{
            {0.78F, 0.26F, 0.14F}, {0.84F, 0.40F, 0.16F}, {0.68F, 0.30F, 0.18F}, {0.88F, 0.54F, 0.20F}},
            new float[]{0.50F, 0.30F, 0.18F}, new float[]{0.40F, 0.62F, 0.24F}),
        /** The mod's golden aspen: an aspen whose texture is its October gold. */
        GOLDEN_ASPEN(1.95F, 8.40F, 9.25F, 9.40F, 10.25F, 0F, new float[][]{
            {1.00F, 0.80F, 0.10F}, {0.98F, 0.72F, 0.08F}, {0.96F, 0.86F, 0.22F}, {1.00F, 0.66F, 0.08F}},
            new float[]{0.55F, 0.40F, 0.18F}, new float[]{0.50F, 0.68F, 0.27F}),
        /** The mod's autumn maple: a sugar maple whose texture is its October red. */
        AUTUMN_MAPLE(2.10F, 8.35F, 9.25F, 9.45F, 10.35F, 0F, new float[][]{
            {0.92F, 0.16F, 0.07F}, {0.95F, 0.30F, 0.06F}, {0.97F, 0.46F, 0.07F}, {0.80F, 0.12F, 0.09F}},
            new float[]{0.47F, 0.30F, 0.16F}, new float[]{0.44F, 0.62F, 0.22F}),
        LARCH(2.10F, 8.90F, 9.75F, 9.90F, 10.70F, 0F, new float[][]{
            {0.94F, 0.72F, 0.20F}, {0.88F, 0.62F, 0.16F}, {0.96F, 0.80F, 0.30F}}, new float[]{0.58F, 0.42F, 0.20F},
            new float[]{0.46F, 0.64F, 0.28F}),
        ROWAN(2.00F, 8.40F, 9.20F, 9.40F, 10.30F, 0F, new float[][]{
            {0.86F, 0.30F, 0.10F}, {0.92F, 0.45F, 0.10F}, {0.76F, 0.20F, 0.12F}}, new float[]{0.48F, 0.30F, 0.16F}),
        WILLOW(1.90F, 9.00F, 9.90F, 10.00F, 10.90F, 0F, new float[][]{
            {0.82F, 0.80F, 0.32F}, {0.88F, 0.76F, 0.26F}, {0.74F, 0.76F, 0.34F}}, new float[]{0.55F, 0.46F, 0.22F}),
        ALDER(2.05F, 9.10F, 9.95F, 9.90F, 10.80F, 0F, new float[][]{
            {0.48F, 0.52F, 0.22F}, {0.52F, 0.46F, 0.20F}, {0.44F, 0.44F, 0.22F}}, new float[]{0.42F, 0.34F, 0.18F}),
        GENERIC(2.10F, 8.60F, 9.50F, 9.65F, 10.60F, 0F, new float[][]{
            {0.84F, 0.64F, 0.18F}, {0.70F, 0.46F, 0.16F}, {0.90F, 0.54F, 0.14F}}, new float[]{0.48F, 0.32F, 0.17F});

        final float bud, turn, peak, drop, bare, marcescent;
        final float[][] palette;
        final float[] brown;
        /**
         * Explicit summer colour for species whose texture is not their summer look (cherry blossom, the mod's
         * always-autumn golden aspen and autumn maple): with seasons they are green in summer.
         */
        final float[] summer;

        Profile(float bud, float turn, float peak, float drop, float bare, float marc, float[][] palette, float[] brown) {
            this(bud, turn, peak, drop, bare, marc, palette, brown, null);
        }

        Profile(float bud, float turn, float peak, float drop, float bare, float marc, float[][] palette, float[] brown, float[] summer) {
            this.bud = bud; this.turn = turn; this.peak = peak; this.drop = drop; this.bare = bare; this.marcescent = marc;
            this.palette = palette; this.brown = brown; this.summer = summer;
        }

        public boolean deciduous() { return this != EVERGREEN; }
        /** Cherry: pink blossom in spring, then its own (explicit) summer green. */
        public boolean blossom() { return this == CHERRY; }
        /** The canonical texture is not this species' summer look: never drawn unchanged while seasons run. */
        public boolean explicitSummer() { return summer != null; }
        float leafFull() { return bud + 1.45F; }
        float green() { return bud + 2.8F; }
        float tinge() { return turn - 0.6F; }
    }

    /** Species by a leaves block's registry path (namespace-free, so other mods' leaves get a sensible guess). */
    public static Profile profile(String path) {
        String p = path.toLowerCase(java.util.Locale.ROOT);
        if (p.contains("larch") || p.contains("tamarack")) return Profile.LARCH;
        String q = p.replace("alpine", "");
        if (q.contains("pine") || q.contains("spruce") || q.contains("fir") || q.contains("cedar") || q.contains("hemlock")
            || q.contains("juniper") || q.contains("redwood") || q.contains("needle") || q.contains("bough") || q.contains("yew")
            || q.contains("cypress") || q.contains("sequoia")) return Profile.EVERGREEN;
        if (p.contains("jungle") || p.contains("mangrove") || p.contains("acacia") || p.contains("palm") || p.contains("azalea")
            || p.contains("tropical") || p.contains("banana") || p.contains("eucalyptus") || p.contains("holly") || p.contains("rhododendron")
            || p.contains("baobab") || p.contains("olive") || p.contains("citrus") || p.contains("orange") || p.contains("lemon")) return Profile.EVERGREEN;
        if (p.contains("autumn_maple")) return Profile.AUTUMN_MAPLE;
        if (p.contains("golden_aspen")) return Profile.GOLDEN_ASPEN;
        if (p.contains("maple")) return Profile.MAPLE;
        if (p.contains("dark_oak")) return Profile.DARK_OAK;
        if (p.contains("oak")) return Profile.OAK;
        if (p.contains("birch") || p.contains("aspen") || p.contains("cottonwood") || p.contains("poplar") || p.contains("beech")
            || p.contains("ginkgo")) return Profile.BIRCH;
        if (p.contains("cherry") || p.contains("sakura")) return Profile.CHERRY;
        if (p.contains("rowan") || p.contains("sumac")) return Profile.ROWAN;
        if (p.contains("willow")) return Profile.WILLOW;
        if (p.contains("alder")) return Profile.ALDER;
        return Profile.GENERIC;
    }

    // ------------------------------------------------------------------ fixed colours (tint space)
    static final float[] BUD = {0.72F, 0.84F, 0.30F};
    static final float[] FRESH = {0.52F, 0.78F, 0.26F};
    static final float[] DRY = {0.54F, 0.38F, 0.21F};
    /** A generic mid green, for things (falling leaves) that have no tree of their own to take a green from. */
    public static final float[] GREEN = {0.45F, 0.63F, 0.22F};

    // ------------------------------------------------------------------ look
    /** How a piece of foliage looks at one moment. Reused (not thread-shared): one per caller. */
    public static final class Look {
        /** Share of the crown still carrying leaves (0 bare .. 1 full). */
        public float density;
        /** Spray size relative to full grown (bud-burst sprays are small). */
        public float size;
        /** Weight 0..1 from the tree's own summer green toward {@link #target}. */
        public float w;
        public final float[] target = new float[3];
        /** Late-summer dulling of the summer green, 0..1. */
        public float tinge;
        /** Cherry blossom: draw the canonical (pink) foliage unchanged, only density/size apply. */
        public boolean blossom;
        /** The foliage is exactly the canonical summer look (no colour change, full, full size). */
        public boolean identity;

        void set(float density, float size, float w, float[] target, float tinge) {
            this.density = density; this.size = size; this.w = w; this.tinge = tinge; this.blossom = false;
            if (target != null) { this.target[0] = target[0]; this.target[1] = target[1]; this.target[2] = target[2]; }
            this.identity = w <= 0 && tinge <= 0 && density >= 1 && size >= 1;
        }

        /**
         * Final multiplier colour: the green reference (the tree's own summer foliage colour) dulled by the tinge,
         * moved toward the target. Written to out[0..2].
         */
        public void compose(float gr, float gg, float gb, float[] out) {
            float mr = 1 + 0.03F * tinge, mg = 1 - 0.08F * tinge, mb = 1 - 0.24F * tinge;
            out[0] = (gr * mr) * (1 - w) + target[0] * w;
            out[1] = (gg * mg) * (1 - w) + target[1] * w;
            out[2] = (gb * mb) * (1 - w) + target[2] * w;
        }
    }

    private static float clamp01(float v) { return v < 0 ? 0 : v > 1 ? 1 : v; }
    static float smooth(float v) { v = clamp01(v); return v * v * (3 - 2 * v); }
    private static void lerp(float[] a, float[] b, float s, float[] out) {
        out[0] = a[0] + (b[0] - a[0]) * s; out[1] = a[1] + (b[1] - a[1]) * s; out[2] = a[2] + (b[2] - a[2]) * s;
    }
    public static float wrap(float t) { t %= 12F; return t < 0 ? t + 12F : t; }

    /**
     * The look of one spray/leaf block of a {@code p} tree at (already per-tree shifted) time t.
     * @param hue  0..1, picks the autumn colour of the tree
     * @param pick 0..1, a small per-spray variation of that colour (neighbouring palette entry, brightness)
     */
    public static Look look(Profile p, float t, float hue, float pick, Look out) {
        t = wrap(t);
        if (!p.deciduous()) { out.set(1, 1, 0, null, 0); return out; }
        float[] tmp = out.target;
        float leafFull = p.leafFull(), green = p.green(), tinge = p.tinge();
        // winter: bare (oaks keep a few dry leaves)
        if (t >= p.bare || t < p.bud) {
            out.set(p.marcescent, 1, 1, DRY, 0);
            return out;
        }
        float[] summer = p.summer;
        if (t < leafFull) { // bud-burst to leaf-out
            float s = smooth((t - p.bud) / (leafFull - p.bud));
            if (p.blossom()) {
                out.set(0.30F + 0.70F * s, 0.40F + 0.60F * s, 0, null, 0);
                out.blossom = true;
                out.identity = false;
                return out;
            }
            lerp(BUD, FRESH, s, tmp);
            out.set(0.28F + 0.72F * s, 0.35F + 0.65F * s, summer != null ? 1 : 0.90F - 0.25F * s, null, 0);
            return out;
        }
        if (t < green) { // fresh green deepening to the summer green
            float s = smooth((t - leafFull) / (green - leafFull));
            if (p.blossom()) { out.set(1, 1, s, summer, 0); return out; }
            if (summer != null) { lerp(FRESH, summer, s, tmp); out.set(1, 1, 1, null, 0); return out; }
            out.set(1, 1, 0.65F * (1 - s), FRESH, 0);
            return out;
        }
        // leaf drop runs across the turn and after the peak
        float density = t < p.drop ? 1 : 1 - smooth((t - p.drop) / (p.bare - p.drop));
        density = Math.max(density, p.marcescent);
        float[] base = summer;
        if (t < tinge) { // summer
            if (base != null) out.set(1, 1, 1, base, 0); else out.set(1, 1, 0, null, 0);
            return out;
        }
        if (t < p.turn) { // late-summer olive tinge
            float s = smooth((t - tinge) / (p.turn - tinge));
            if (base != null) out.set(density, 1, 1, base, s * 0.6F); else out.set(density, 1, 0, null, s * 0.6F);
            return out;
        }
        if (t < p.peak) { // the turn
            float s = smooth((t - p.turn) / (p.peak - p.turn));
            palette(p, hue, pick, tmp);
            if (base != null) { lerp(base, tmp, s, tmp); out.set(density, 1, 1, null, 0.6F * (1 - s)); }
            else out.set(density, 1, (float) Math.pow(s, 0.85), null, 0.6F * (1 - s));
            return out;
        }
        // after the peak: browning while the leaves fall
        float b = smooth((t - p.peak - 0.1F) / (p.bare - p.peak - 0.1F));
        palette(p, hue, pick, tmp);
        lerp(tmp, p.brown, b, tmp);
        out.set(density, 1, 1, null, 0);
        return out;
    }

    /** The autumn colour of a tree (hue) with a per-spray variation (pick), brightness-jittered, into dst. */
    public static void palette(Profile p, float hue, float pick, float[] dst) {
        float[][] pal = p.palette;
        int n = pal.length;
        int i = Math.min(n - 1, (int) (clamp01(hue) * n));
        if (pick < 0.12F) i = Math.max(0, i - 1);
        else if (pick > 0.88F) i = Math.min(n - 1, i + 1);
        float k = 0.92F + 0.16F * frac(pick * 13.7F);
        float[] c = pal[i];
        dst[0] = Math.min(1, c[0] * k); dst[1] = Math.min(1, c[1] * k); dst[2] = Math.min(1, c[2] * k);
    }

    static float frac(float v) { return v - (float) Math.floor(v); }

    // ------------------------------------------------------------------ per-tree variation
    /**
     * A tree-sized patch id (jittered 6-block cells, nearest point): trees, leaf blocks and falling leaves in the
     * same patch agree on one tree's colour and timing, neighbouring patches differ - an October patchwork.
     */
    public static int patch(double x, double z) {
        final double size = 6.0;
        int cx = (int) Math.floor(x / size), cz = (int) Math.floor(z / size);
        double best = Double.MAX_VALUE;
        int result = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int h = hash(cx + dx, cz + dz, 0x51A5);
                double px = (cx + dx + (h & 255) / 256.0) * size, pz = (cz + dz + ((h >>> 8) & 255) / 256.0) * size;
                double d = (px - x) * (px - x) + (pz - z) * (pz - z);
                if (d < best) { best = d; result = h; }
            }
        }
        return result;
    }

    public static float hue(int patch) { return ((mix(patch ^ 0x2F1B) >>> 8) & 0xFFFF) / 65536F; }

    /**
     * Per-tree phenology shift in months (early and late trees), at time t. A few trees (stressed ones in the
     * real world) turn well before the rest, dotting a green September forest with red.
     */
    public static float shift(int patch, float t) {
        int h = mix(patch ^ 0x6C3D);
        float s = (((h >>> 8) & 0xFFFF) / 65536F - 0.5F) * 0.56F;
        float early = (h & 0xFF) / 256F;
        float tw = wrap(t);
        if (early < 0.06F && tw > 6.8F && tw < 10.8F) s += 0.55F;
        return s;
    }

    public static int hash(int x, int y, int z) {
        int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
        return mix(h);
    }

    public static int mix(int h) {
        h ^= h >>> 16; h *= 0x7FEB352D; h ^= h >>> 15; h *= 0x846CA68B; h ^= h >>> 16;
        return h;
    }

    public static float unit(int h) { return ((h >>> 8) & 0xFFFF) / 65536F; }

    // ------------------------------------------------------------------ falling leaves
    /** How heavily a tree of this species is dropping leaves at t (0..1). */
    public static float leafFall(Profile p, float t) {
        if (!p.deciduous()) return 0;
        t = wrap(t);
        if (p.marcescent > 0 && (t >= p.bare || t < p.bud)) return 0.04F;
        float start = p.peak - 0.45F, mid = (p.drop + p.bare) * 0.5F, end = p.bare + 0.1F;
        if (t < start || t > end) return 0;
        if (t < p.drop) return 0.12F + 0.5F * smooth((t - start) / (p.drop - start));
        if (t < mid) return 0.62F + 0.38F * smooth((t - p.drop) / (mid - p.drop));
        return smooth((end - t) / (end - mid));
    }

    // ------------------------------------------------------------------ ground, snow, coats
    /**
     * Seasonal grass/ground colour shift: out = {target r, g, b, weight}. Summer lush (no change), fall tan/olive,
     * winter dull straw, spring fresh. The caller keeps the biome's brightness.
     */
    public static void grass(float t, float[] out) {
        t = wrap(t);
        final float[] WINTER = {0.58F, 0.53F, 0.36F}, FALL = {0.66F, 0.62F, 0.32F}, SPRING = {0.50F, 0.80F, 0.28F};
        float w;
        float[] c;
        if (t < 1.8F || t >= 11.5F) { c = WINTER; w = 0.42F; }
        else if (t < 3.0F) { // thaw: straw greening up
            float s = smooth((t - 1.8F) / 1.2F);
            c = new float[3]; lerp(WINTER, SPRING, s, c); w = 0.42F - 0.24F * s;
        } else if (t < 5.0F) { c = SPRING; w = 0.18F * (1 - smooth((t - 4.2F) / 0.8F)); }
        else if (t < 8.0F) { c = FALL; w = 0; }
        else if (t < 10.0F) { c = FALL; w = 0.30F * smooth((t - 8.0F) / 2.0F); }
        else { // late fall into winter
            float s = smooth((t - 10.0F) / 1.5F);
            c = new float[3]; lerp(FALL, WINTER, s, c); w = 0.30F + 0.12F * s;
        }
        out[0] = c[0]; out[1] = c[1]; out[2] = c[2]; out[3] = w;
    }

    /** Winter cold 0..1 for seasonal snow: none Mar 12 - Nov 11, full Dec 5 - Feb 18, smooth in between. */
    public static float snowCold(double t) {
        float m = wrap((float) t);
        if (m >= 11.15F || m < 1.6F) return 1;
        if (m >= 10.35F) return smooth((m - 10.35F) / 0.8F);
        if (m < 2.4F) return 1 - smooth((m - 1.6F) / 0.8F);
        return 0;
    }

    /** Share of deer (by their individual coat value) already in winter coat: moult Aug-Oct, back to summer red Apr-May. */
    public static float coatGrey(double t) {
        float m = wrap((float) t);
        if (m >= 5.0F && m < 7.6F) return 0;
        if (m >= 7.6F && m < 9.6F) return smooth((m - 7.6F) / 2.0F);
        if (m >= 3.2F && m < 5.0F) return 1 - smooth((m - 3.2F) / 1.8F);
        return 1;
    }

    // ------------------------------------------------------------------ rebuild stages
    public static final int SUMMER_KEY = 1000, WINTER_KEY = 1001;

    /**
     * The world's seasonal look is rebuilt in quarter-month stages; long stretches where nothing changes (high
     * summer, deep winter) are one stage each, so no chunk is rebuilt for them.
     */
    public static int stageKey(double t) {
        float m = wrap((float) t);
        if (m >= 5.6F && m < 6.8F) return SUMMER_KEY;
        if (m >= 11.5F || m < 1.5F) return WINTER_KEY;
        return (int) Math.floor(m * 4);
    }

    /** The time every seasonal look of a stage is drawn at. */
    public static float stageTime(int key) {
        if (key == SUMMER_KEY) return 6.2F;
        if (key == WINTER_KEY) return 0.5F;
        return (key + 0.5F) / 4F;
    }
}
