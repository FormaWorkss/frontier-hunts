package com.formaworks.frontierhunts.shelter;

/**
 * [shelter] The one shelter detector shared by the client storm presentation (frost, snow, fog, wind sound) and the
 * server body-temperature model, so what you see and what you feel always agree.
 *
 * Pure Java on purpose (no Minecraft types): the world is read through {@link Grid}, a block classified into a few
 * {@code byte} codes. {@link Shelter} adapts a live level; the offline harness feeds synthetic block grids.
 *
 * <h2>What it measures, around an eye position</h2>
 * <ul>
 *   <li><b>roof</b> 0..1 - weighted share of 9 columns (the eye column, 4 at two blocks, 4 diagonal) with something
 *   solid above within {@value #ROOF_UP} blocks. Leaves are not a roof (they only count as {@link Result#canopy}).
 *   Columns whose height-map top is at or below the eye are open without a single block lookup.</li>
 *   <li><b>walls</b> 0..1 - share of 16 short horizontal rays (8 compass directions at eye and chest height, up to
 *   {@value #WALL_REACH} blocks) that hit something solid. A doorway or a window leaks a couple of rays.</li>
 *   <li><b>fabric</b> 0..1 - the eye or the feet are inside a tent or a blind (multi-block canvas shelters whose
 *   cells are partial shapes, so neither the column nor the ray test can see them from the inside): 1 for a closed
 *   tent, less with the door flap open.</li>
 * </ul>
 * From those: {@link Result#enclosure} (inside-ness, what fades the storm out), {@link Result#windBlock} and
 * {@link Result#precipBlock}. Partial shelter (doorway, overhang, porch) gives partial values, never a hard switch.
 *
 * <p>Cost: open ground resolves the 9 roof columns from the height map (no block reads) plus at most 16 x 6 ray
 * reads; a closed room stops every ray at its wall. Typical 40-140 block reads; callers evaluate it a few times per
 * second (client) or once per second per player (server), never per frame.
 */
public final class ShelterScan {
    /** Block codes returned by {@link Grid#at}. */
    public static final byte AIR = 0, SOLID = 1, LEAVES = 2, TENT = 3, TENT_OPEN = 4, BLIND = 5, BLIND_OPEN = 6, FLUID = 7;

    /** How far up a column is searched for a roof, and how far a wall ray reaches. */
    public static final int ROOF_UP = 20, WALL_REACH = 6;

    /** Column offsets and weights: the eye column counts double, axis columns at 2 blocks, corners at (2,2) half. */
    private static final int[][] COLUMNS = {{0, 0}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}, {2, 2}, {2, -2}, {-2, 2}, {-2, -2}};
    private static final float[] COLUMN_WEIGHT = {2f, 1f, 1f, 1f, 1f, 0.5f, 0.5f, 0.5f, 0.5f};
    private static final float COLUMN_TOTAL = 8f;
    private static final int[][] RAYS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    /** Read-only view of the world around the eye. */
    public interface Grid {
        /** Code of the block at a position (see the constants). Unknown/unloaded = {@link #AIR}. */
        byte at(int x, int y, int z);

        /**
         * Lowest y with nothing at or above it in the column - leaves and fluids included, like Minecraft's
         * MOTION_BLOCKING height map - or {@link Integer#MIN_VALUE} when unknown (the column is then scanned).
         */
        int top(int x, int z);
    }

    /** Mutable result (callers reuse one instance; nothing is allocated per scan). */
    public static final class Result {
        public float roof, walls, canopy, fabric;
        /** 1 when the fabric shelter is a sleeping tent (tents warm more than blinds). */
        public boolean tent;
        /** 0..1 enclosed by sealed walls/roof, from sky light (caves, cellars); 0 when unknown. */
        public float sealed;
        public float enclosure, windBlock, precipBlock;

        public Result set(Result o) {
            roof = o.roof; walls = o.walls; canopy = o.canopy; fabric = o.fabric; tent = o.tent; sealed = o.sealed;
            enclosure = o.enclosure; windBlock = o.windBlock; precipBlock = o.precipBlock;
            return this;
        }

        public Result clear() {
            roof = walls = canopy = fabric = sealed = enclosure = windBlock = precipBlock = 0f;
            tent = false;
            return this;
        }

        @Override
        public String toString() {
            return String.format(java.util.Locale.ROOT, "roof %.2f walls %.2f canopy %.2f fabric %.2f%s sealed %.2f -> enclosure %.2f wind %.2f precip %.2f",
                    roof, walls, canopy, fabric, tent ? " (tent)" : "", sealed, enclosure, windBlock, precipBlock);
        }
    }

    private ShelterScan() {}

    static boolean roofLike(byte c) {
        return c == SOLID || c == TENT || c == TENT_OPEN || c == BLIND || c == BLIND_OPEN;
    }

    static float fabricValue(byte c) {
        return switch (c) {
            case TENT -> 1f;
            case TENT_OPEN -> 0.72f;   // door flap open: a draught and the storm in the doorway
            case BLIND -> 0.95f;
            case BLIND_OPEN -> 0.8f;   // shooting windows open
            default -> 0f;
        };
    }

    /**
     * Scans around the eye block ({@code ex, ey, ez}; {@code ey} = the block the eye is in). {@code sky} is the sky
     * light at the eye (0..15), or -1 when unknown. Fills and returns {@code out}.
     */
    public static Result scan(Grid g, int ex, int ey, int ez, int sky, Result out) {
        out.clear();
        // ---- fabric shelters: tent / blind cells at the eye or the feet (inside them every cell is a part block)
        byte eye = g.at(ex, ey, ez), feet = g.at(ex, ey - 1, ez);
        float fab = Math.max(fabricValue(eye), fabricValue(feet));
        if (fab > 0f && !(eye == TENT || eye == TENT_OPEN || feet == TENT || feet == TENT_OPEN) && !roofWithin(g, ex, ey, ez, 3)
                && fabricAround(g, ex, ey, ez) < 2) {
            fab = 0.3f; // a tower blind's legs or ladder, not its cabin
        }
        if (fab > 0f) {
            out.fabric = fab;
            out.tent = eye == TENT || eye == TENT_OPEN || feet == TENT || feet == TENT_OPEN;
        }

        // ---- roof columns
        float roof = 0f, canopy = 0f;
        boolean canvasRoof = false;
        for (int i = 0; i < COLUMNS.length; i++) {
            int x = ex + COLUMNS[i][0], z = ez + COLUMNS[i][1];
            int top = g.top(x, z);
            if (top != Integer.MIN_VALUE && ey + 1 >= top) continue; // nothing at all above the eye (not even leaves)
            int stop = ey + ROOF_UP;
            if (top != Integer.MIN_VALUE) stop = Math.min(stop, top - 1);
            boolean hit = false, leaves = false;
            for (int y = ey + 1; y <= stop; y++) {
                byte c = g.at(x, y, z);
                if (roofLike(c)) {
                    hit = true;
                    if (i == 0 && (c == TENT || c == TENT_OPEN)) canvasRoof = true;
                    break;
                }
                if (c == LEAVES) leaves = true;
            }
            if (hit) {
                roof += COLUMN_WEIGHT[i];
            } else if (top != Integer.MIN_VALUE && top - 1 > ey + ROOF_UP && sky >= 0 && sky < 12) {
                roof += COLUMN_WEIGHT[i] * 0.75f; // a ceiling higher than we look and little sky light: big cavern, tall hall
            } else if (leaves) {
                canopy += COLUMN_WEIGHT[i];
            }
        }
        out.roof = clamp01(roof / COLUMN_TOTAL);
        out.canopy = clamp01(canopy / COLUMN_TOTAL);

        // ---- walls: 8 directions x 2 heights
        float walls = 0f;
        for (int h = 0; h < 2; h++) {
            int y = ey - h;
            for (int[] r : RAYS) {
                for (int s = 1; s <= WALL_REACH; s++) {
                    byte c = g.at(ex + r[0] * s, y, ez + r[1] * s);
                    if (roofLike(c)) { walls += c == TENT_OPEN ? 0.85f : c == BLIND_OPEN ? 0.9f : 1f; break; } // open flap / windows
                    if (c == LEAVES) { walls += 0.35f; break; }
                }
            }
        }
        out.walls = walls / 16f;
        // a walk-in camping tent: its canvas shell is around you (the interior cells are air), not at your eye
        if (canvasRoof && out.walls >= 0.6f) out.tent = true;
        out.sealed = sky < 0 ? 0f : clamp01((11f - sky) / 9f) * out.roof;
        return finish(out);
    }

    /** Derives enclosure / wind / precipitation blocking from the raw measurements. */
    public static Result finish(Result r) {
        float w = r.walls;
        float shell = r.roof * (0.2f + 0.8f * (float) Math.pow(w, 1.5));
        r.enclosure = clamp01(Math.max(Math.max(shell, r.fabric), r.sealed));
        r.windBlock = clamp01(Math.max(Math.max((float) Math.pow(w, 1.2) * (0.5f + 0.5f * r.roof), r.fabric * 0.97f), r.sealed));
        r.precipBlock = clamp01(Math.max(Math.max(r.roof, r.fabric), r.canopy * 0.35f));
        return r;
    }

    /** Fabric cells right beside the eye (a blind's cabin is a block of part cells; a ladder or a leg is a lone column). */
    private static int fabricAround(Grid g, int x, int y, int z) {
        int n = 0;
        for (int[] r : RAYS) {
            if (r[0] != 0 && r[1] != 0) continue;
            if (fabricValue(g.at(x + r[0], y, z + r[1])) > 0f) n++;
        }
        return n;
    }

    private static boolean roofWithin(Grid g, int x, int ey, int z, int up) {
        for (int y = ey + 1; y <= ey + up; y++) {
            if (roofLike(g.at(x, y, z))) return true;
        }
        return false;
    }

    static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
