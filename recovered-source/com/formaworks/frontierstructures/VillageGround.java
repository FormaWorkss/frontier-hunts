package com.formaworks.frontierstructures;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * [villages] One terrain model for a whole village: every building sits on its own level yard (cut into the slope,
 * filled underneath, stone footings on the downhill edge), the green is level, roads ramp smoothly between them, and
 * around all of it the land eases back to its natural shape over a ragged, noise-widened apron - terraces on slopes,
 * no hard rectangles. Per column and deterministic (natural heights come from the generator), so chunks can generate
 * in any order. Water columns are never regraded.
 */
public final class VillageGround {
    public static final int APRON = 8;
    /** [1.1.3] the farthest a grade-2 apron reaches from a yard (its width grows with the height it eases out) */
    public static final int REACH = 30;
    static final int FOOT = 1, GREEN = 2, ROAD = 3, EDGE = 4, NONE = 0, WET = 5;

    private final VillagePlan p;
    private final List<Shape> shapes = new ArrayList<>();
    private final List<double[]> segs = new ArrayList<>(); // x0 z0 x1 z1 y0 y1 half road# along0 along1 style fade

    /** footprint and distance field of one lot */
    static final class Shape {
        final int x0, z0, w, h, yard;
        final float[] dist;
        final Map<Long, Integer> lowest = new HashMap<>();
        final int lot;
        Shape(VillagePlan.Lot l, VillagePlan.Info in, int lot, int pad) {
            this.lot = lot;
            yard = l.yard;
            int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (var e : in.lowest.entrySet()) {
                long k = e.getKey();
                int[] r = VillagePlan.rot(l.rotation, (int)(k >> 32), (int)k);
                int x = l.ox + r[0], z = l.oz + r[1];
                lowest.put(TerrainFit.key(x, z), l.originY() + e.getValue());
                minX = Math.min(minX, x); maxX = Math.max(maxX, x); minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
            }
            x0 = minX - pad; z0 = minZ - pad; w = maxX - minX + 1 + 2 * pad; h = maxZ - minZ + 1 + 2 * pad;
            dist = new float[w * h];
            Arrays.fill(dist, 1e6f);
            for (long k : lowest.keySet()) dist[((int)(k >> 32) - x0) + ((int)k - z0) * w] = 0;
            // two-pass chamfer distance transform (1, sqrt 2)
            final float D = 1.4142f;
            for (int z = 0; z < h; z++) for (int x = 0; x < w; x++) {
                float d = dist[x + z * w];
                if (x > 0) d = Math.min(d, dist[x - 1 + z * w] + 1);
                if (z > 0) d = Math.min(d, dist[x + (z - 1) * w] + 1);
                if (x > 0 && z > 0) d = Math.min(d, dist[x - 1 + (z - 1) * w] + D);
                if (x < w - 1 && z > 0) d = Math.min(d, dist[x + 1 + (z - 1) * w] + D);
                dist[x + z * w] = d;
            }
            for (int z = h - 1; z >= 0; z--) for (int x = w - 1; x >= 0; x--) {
                float d = dist[x + z * w];
                if (x < w - 1) d = Math.min(d, dist[x + 1 + z * w] + 1);
                if (z < h - 1) d = Math.min(d, dist[x + (z + 1) * w] + 1);
                if (x < w - 1 && z < h - 1) d = Math.min(d, dist[x + 1 + (z + 1) * w] + D);
                if (x > 0 && z < h - 1) d = Math.min(d, dist[x - 1 + (z + 1) * w] + D);
                dist[x + z * w] = d;
            }
        }
        float d(int x, int z) {
            int i = x - x0, j = z - z0;
            return i < 0 || j < 0 || i >= w || j >= h ? 1e6f : dist[i + j * w];
        }
    }

    public VillageGround(VillagePlan p, VillagePlan.Templates m) {
        this.p = p;
        int i = 0;
        int pad = p.grade >= 2 ? REACH + 4 : APRON + 4;
        for (var l : p.lots) shapes.add(new Shape(l, m.info(l.template), i++, pad));
        int r = 0;
        for (var road : p.roads) {
            double total = 0;
            for (int k = 0; k + 1 < road.x.length; k++) total += Math.hypot(road.x[k + 1] - road.x[k], road.z[k + 1] - road.z[k]);
            double acc = 0;
            for (int k = 0; k + 1 < road.x.length; k++) {
                double len = Math.hypot(road.x[k + 1] - road.x[k], road.z[k + 1] - road.z[k]);
                segs.add(new double[]{road.x[k] + 0.5, road.z[k] + 0.5, road.x[k + 1] + 0.5, road.z[k + 1] + 0.5, road.y[k], road.y[k + 1], road.half, r,
                    acc / Math.max(total, 1), (acc + len) / Math.max(total, 1), road.style, road.fade ? 1 : 0});
                acc += len;
            }
            r++;
        }
    }

    /** what the village does to one column */
    public static final class Col {
        int kind, y, n, lot = -1, style;
        double w, dBuild = 1e6, dRoad = 1e6, along, half;
        boolean fade;
        Integer lowest;
    }

    static double hash(long seed, int x, int z) {
        long h = seed ^ x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 33; h *= 0xff51afd7ed558ccdL; h ^= h >>> 33; h *= 0xc4ceb9fe1a85ec53L; h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }

    /** smooth value noise in [-1, 1] */
    static double noise(long seed, int x, int z, double cell) {
        double fx = x / cell, fz = z / cell;
        int ix = (int)Math.floor(fx), iz = (int)Math.floor(fz);
        double tx = fx - ix, tz = fz - iz;
        tx = tx * tx * (3 - 2 * tx); tz = tz * tz * (3 - 2 * tz);
        double a = hash(seed, ix, iz), b = hash(seed, ix + 1, iz), c = hash(seed, ix, iz + 1), d = hash(seed, ix + 1, iz + 1);
        return (a + (b - a) * tx + (c - a) * tz + (a - b - c + d) * tx * tz) * 2 - 1;
    }

    static double smooth(double t) {return t <= 0 ? 0 : t >= 1 ? 1 : t * t * (3 - 2 * t);}

    /**
     * [1.1.3] grade 2: how far an influence that has to make up {@code dh} blocks reaches - at least its base reach,
     * otherwise about two blocks out for every block of height (a 1:2 slope that grass can hold), at most {@link #REACH}
     */
    static double reach(double base, double dh, double scale) {
        return Math.min(REACH, Math.max(base, Math.abs(dh) * 2.1) * scale);
    }

    public Col col(int x, int z, TerrainFit.Natural nat) {
        if (p.grade >= 2) return col2(x, z, nat);
        Col c = new Col();
        c.n = nat.floor(x, z);
        long k = TerrainFit.key(x, z);
        for (Shape s : shapes) {
            Integer low = s.lowest.get(k);
            if (low != null) {c.kind = FOOT; c.y = s.yard; c.lowest = low; c.lot = s.lot; c.w = 1; c.dBuild = 0; return c;}
        }
        if (nat.surface(x, z) > c.n) {c.kind = WET; c.y = c.n; return c;}
        double scale = 1 + 0.35 * noise(p.seed, x, z, 9);
        double acc = 0, sum = 0, wmax = 0;
        for (Shape s : shapes) {
            double d = s.d(x, z);
            c.dBuild = Math.min(c.dBuild, d);
            double w = smooth(1 - (d - 0.5) / (APRON * scale));
            if (w > 0) {acc += w * (s.yard - c.n); sum += w; wmax = Math.max(wmax, w);}
        }
        // roads (the ring road is a road at the green's level)
        double bestD = 1e9, bestY = 0;
        double[] best = null;
        for (double[] g : segs) {
            double ax = g[0], az = g[1], bx = g[2], bz = g[3];
            double px = x + 0.5, pz = z + 0.5;
            if (px < Math.min(ax, bx) - 12 || px > Math.max(ax, bx) + 12 || pz < Math.min(az, bz) - 12 || pz > Math.max(az, bz) + 12) continue;
            double vx = bx - ax, vz = bz - az, l2 = vx * vx + vz * vz;
            double t = l2 < 1e-9 ? 0 : Math.max(0, Math.min(1, ((px - ax) * vx + (pz - az) * vz) / l2));
            double d = Math.hypot(px - ax - vx * t, pz - az - vz * t) - g[6];
            if (d < bestD) {bestD = d; bestY = g[4] + (g[5] - g[4]) * t; best = g; c.along = g[8] + (g[9] - g[8]) * t;}
        }
        if (best != null) {
            c.dRoad = bestD; c.half = best[6]; c.style = (int)best[10]; c.fade = best[11] > 0;
            if (bestD <= 0) {c.kind = ROAD; c.y = (int)Math.round(bestY); c.w = 1; return c;}
            double w = smooth(1 - bestD / (4.5 * scale)) * (c.fade ? 1 - smooth((c.along - 0.55) / 0.45) * 0.7 : 1);
            if (w > 0) {acc += w * (bestY - c.n); sum += w; wmax = Math.max(wmax, w);}
        }
        if (p.greenR > 0) {
            double d = Math.hypot(x - p.cx, z - p.cz) - (p.greenR + 3);
            if (d <= 0) {c.kind = GREEN; c.y = p.greenY; c.w = 1; return c;}
            double w = smooth(1 - d / (9 * scale));
            if (w > 0) {acc += w * (p.greenY - c.n); sum += w; wmax = Math.max(wmax, w);}
        }
        c.w = wmax;
        if (sum <= 0) {c.kind = NONE; c.y = c.n; return c;}
        int dy = (int)Math.round(acc / Math.max(1, sum));
        c.y = c.n + Math.max(-12, Math.min(12, dy));
        c.kind = EDGE;
        return c;
    }

    /**
     * [1.1.3] grade 2. The same pulls as grade 1 (yards, roads, the green), but each reaches out as far as its height
     * difference needs, and the result eases into the natural ground by the strongest pull - so the land meets the
     * village in smooth slopes, never in a step where the apron used to end.
     */
    public static java.util.function.Consumer<String> DEBUG = null;

    Col col2(int x, int z, TerrainFit.Natural nat) {
        Col c = new Col();
        c.n = nat.floor(x, z);
        long k = TerrainFit.key(x, z);
        for (Shape s : shapes) {
            Integer low = s.lowest.get(k);
            if (low != null) {c.kind = FOOT; c.y = s.yard; c.lowest = low; c.lot = s.lot; c.w = 1; c.dBuild = 0; return c;}
        }
        if (nat.surface(x, z) > c.n) {c.kind = WET; c.y = c.n; return c;}
        double scale = 1 + 0.25 * noise(p.seed, x, z, 9);
        double acc = 0, sum = 0, wmax = 0;
        for (Shape s : shapes) {
            double d = s.d(x, z);
            c.dBuild = Math.min(c.dBuild, d);
            double w = smooth(1 - (d - 0.5) / reach(APRON, s.yard - c.n, scale));
            if (w > 0) {acc += w * (s.yard - c.n); sum += w; wmax = Math.max(wmax, w);}
            if (DEBUG != null && w > 0) DEBUG.accept("  lot " + s.lot + " d " + d + " yard " + s.yard + " w " + w);
        }
        double bestD = 1e9, bestY = 0;
        double[] best = null;
        double[] ds = new double[64], ys = new double[64];
        int nd = 0;
        for (double[] g : segs) {
            double ax = g[0], az = g[1], bx = g[2], bz = g[3];
            double px = x + 0.5, pz = z + 0.5;
            if (px < Math.min(ax, bx) - REACH - 2 || px > Math.max(ax, bx) + REACH + 2 || pz < Math.min(az, bz) - REACH - 2 || pz > Math.max(az, bz) + REACH + 2) continue;
            double vx = bx - ax, vz = bz - az, l2 = vx * vx + vz * vz;
            double t = l2 < 1e-9 ? 0 : Math.max(0, Math.min(1, ((px - ax) * vx + (pz - az) * vz) / l2));
            double d = Math.hypot(px - ax - vx * t, pz - az - vz * t) - g[6];
            double sy = g[4] + (g[5] - g[4]) * t, along = g[8] + (g[9] - g[8]) * t;
            if (d < bestD) {bestD = d; bestY = sy; best = g; c.along = along;}
            ds[nd] = d; ys[nd] = sy; nd = Math.min(nd + 1, ds.length - 1);
            // every road pulls (not just the nearest): where two roads at different heights meet, the ground blends
            // between them instead of jumping where one becomes nearer than the other; close to a road its pull is
            // much the strongest, so the road keeps soft banks rather than a cut wall
            if (d > 0) {
                double w = smooth(1 - d / reach(4.5, sy - c.n, scale)) * (g[11] > 0 ? 1 - smooth((along - 0.55) / 0.45) * 0.7 : 1);
                double boost = 1 + 5 * smooth(1 - d / 4);
                if (w > 0) {acc += w * boost * (sy - c.n); sum += w * boost; wmax = Math.max(wmax, w);}
                if (DEBUG != null && w > 0) DEBUG.accept("  road " + (int)g[7] + " d " + d + " y " + sy + " w " + w);
            }
        }
        if (best != null) {
            c.dRoad = bestD; c.half = best[6]; c.style = (int)best[10]; c.fade = best[11] > 0;
            if (bestD <= 0) {
                // where roads meet, their levels blend over a few blocks instead of stepping
                double wy = 0, ws = 0;
                for (int i = 0; i < nd; i++) {
                    double e = ds[i] - bestD;
                    if (e > 3) continue;
                    double w = 1 / ((0.3 + e) * (0.3 + e));
                    wy += w * ys[i]; ws += w;
                }
                c.kind = ROAD; c.y = (int)Math.round(ws > 0 ? wy / ws : bestY); c.w = 1; return c;
            }
        }
        if (p.greenR > 0) {
            double d = Math.hypot(x - p.cx, z - p.cz) - (p.greenR + 3);
            if (d <= 0) {c.kind = GREEN; c.y = p.greenY; c.w = 1; return c;}
            double w = smooth(1 - d / reach(9, p.greenY - c.n, scale));
            if (w > 0) {acc += w * (p.greenY - c.n); sum += w; wmax = Math.max(wmax, w);}
        }
        c.w = wmax;
        if (sum <= 0) {c.kind = NONE; c.y = c.n; return c;}
        // the blend of the pulls, eased out by the strongest one: full at a yard's edge, nothing where the reach ends
        double dy = acc / sum * wmax;
        // and nothing at the edge of the village's box, where its grading stops
        int edge = Math.min(Math.min(x - p.minX, p.maxX - x), Math.min(z - p.minZ, p.maxZ - z));
        dy *= smooth(edge / 8.0);
        // and it eases out toward water: banks keep their natural line instead of a wall at the shore
        if (Math.abs(dy) >= 0.5) dy *= smooth(wetDistance(x, z, nat) / 7.0);
        if (DEBUG != null) DEBUG.accept("  n " + c.n + " acc/sum " + acc / sum + " wmax " + wmax + " edge " + edge + " dy " + dy);
        c.y = c.n + (int)Math.max(-14, Math.min(14, Math.round(dy)));
        c.kind = EDGE;
        return c;
    }

    /** [1.1.3] distance to the nearest water column, out to 7 blocks (8 when there is none) */
    static double wetDistance(int x, int z, TerrainFit.Natural nat) {
        double best = 8;
        for (int dx = -7; dx <= 7; dx++) for (int dz = -7; dz <= 7; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d >= best) continue;
            if (nat.surface(x + dx, z + dz) > nat.floor(x + dx, z + dz)) best = d;
        }
        return Math.max(0, best - 0.5);
    }

    /** [1.1.0] a building's footprint column (the template stands here) */
    public boolean foot(int x, int z) {
        long k = TerrainFit.key(x, z);
        for (Shape s : shapes) if (s.lowest.containsKey(k)) return true;
        return false;
    }

    /**
     * [1.1.0] the village touches this column at all - footprint, road, green, or the apron that eases the ground back
     * to nature (the same reach as {@link #col}, without the natural heights, so it is cheap and needs no generator)
     */
    public boolean influences(int x, int z) {
        if (foot(x, z)) return true;
        double scale = 1 + 0.35 * noise(p.seed, x, z, 9);
        for (Shape s : shapes) if (s.d(x, z) - 0.5 < APRON * scale) return true;
        double px = x + 0.5, pz = z + 0.5;
        for (double[] g : segs) {
            double ax = g[0], az = g[1], bx = g[2], bz = g[3];
            if (px < Math.min(ax, bx) - 12 || px > Math.max(ax, bx) + 12 || pz < Math.min(az, bz) - 12 || pz > Math.max(az, bz) + 12) continue;
            double vx = bx - ax, vz = bz - az, l2 = vx * vx + vz * vz;
            double t = l2 < 1e-9 ? 0 : Math.max(0, Math.min(1, ((px - ax) * vx + (pz - az) * vz) / l2));
            double d = Math.hypot(px - ax - vx * t, pz - az - vz * t) - g[6];
            if (d < 4.5 * scale) return true;
        }
        return p.greenR > 0 && Math.hypot(x - p.cx, z - p.cz) - (p.greenR + 3) < 9 * scale;
    }

    /**
     * [1.1.3] the village touches this column: footprint, road, green, ground it regrades, or ground it clears of
     * growth (the same test as {@link #grade}); exact for grade 2, where the apron's reach depends on the land
     */
    public boolean influences(int x, int z, TerrainFit.Natural nat) {
        if (p.grade < 2 || nat == null) return influences(x, z);
        Col c = col(x, z, nat);
        return c.kind != NONE && c.kind != WET && (c.kind != EDGE || c.y != c.n || c.w > 0.3);
    }

    /** the graded ground level (first air) at a column */
    public int level(int x, int z, TerrainFit.Natural nat) {return col(x, z, nat).y;}

    // ------------------------------------------------------------------ phase 0: grading (before the buildings)

    static boolean ground(BlockState s) {return TerrainFit.terrain(s);}

    static boolean soft(BlockState s) {
        return s.isAir() || TerrainFit.vegetation(s) || s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.is(Blocks.SNOW) || s.is(Blocks.VINE)
            || s.is(Blocks.BAMBOO) || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(BlockTags.REPLACEABLE_BY_TREES);
    }

    /** soft blocks plus the Frontier ground flora (sticks, duff, ferns, saplings, deadfall ...) - never terrain */
    static boolean flora(BlockState s) {
        if (soft(s)) return true;
        return !s.isAir() && s.getFluidState().isEmpty() && !s.hasBlockEntity() && !TerrainFit.terrain(s)
            && s.getBlock().builtInRegistryHolder().key().location().getNamespace().equals("frontierhunts");
    }

    /** foliage a NEIGHBOURING chunk's trees spilled into an already finished part of the village (natural leaves only) */
    static boolean spill(BlockState s) {
        if (s.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock) return !s.getValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT);
        return s.is(Blocks.VINE);
    }

    BlockState footing(int x, int z) {
        double h = hash(p.seed + 17, x, z);
        return h < 0.5 ? Blocks.COBBLESTONE.defaultBlockState() : h < 0.75 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.ANDESITE.defaultBlockState();
    }

    public void grade(WorldGenLevel level, BoundingBox box, TerrainFit.Natural nat) {
        var m = new BlockPos.MutableBlockPos();
        int top = level.getMaxBuildHeight() - 1, bottom = level.getMinBuildHeight() + 1;
        // trees of a neighbouring chunk that decorated after this part of the village was built spill their crowns into
        // it: this chunk's trees are done now, so trim natural foliage over the village within reach of them (the
        // feature step may write one chunk around; 8 blocks is well inside)
        int sx0 = Math.max(box.minX() - 8, p.minX), sx1 = Math.min(box.maxX() + 8, p.maxX), sz0 = Math.max(box.minZ() - 8, p.minZ), sz1 = Math.min(box.maxZ() + 8, p.maxZ);
        for (int x = sx0; x <= sx1; x++) for (int z = sz0; z <= sz1; z++) {
            if (box.isInside(x, box.minY(), z)) continue;
            Col c = col(x, z, nat);
            if (c.kind == NONE || c.kind == WET || c.kind == EDGE && c.w <= 0.3) continue;
            int from = Math.max(c.y, c.n) + 1;
            for (int y = from; y <= Math.min(top, from + 34); y++) {
                var s = level.getBlockState(m.set(x, y, z));
                if (spill(s)) level.setBlock(m, Blocks.AIR.defaultBlockState(), 2);
            }
        }
        int x0 = Math.max(box.minX(), p.minX), x1 = Math.min(box.maxX(), p.maxX), z0 = Math.max(box.minZ(), p.minZ), z1 = Math.min(box.maxZ(), p.maxZ);
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            Col c = col(x, z, nat);
            if (c.kind == NONE || c.kind == WET) continue;
            if (c.kind == FOOT) {
                for (int y = c.y; y <= Math.min(top, Math.max(c.n, c.y) + 40); y++) {
                    var s = level.getBlockState(m.set(x, y, z));
                    if (ground(s) || flora(s)) {if (!s.isAir()) level.setBlock(m, Blocks.AIR.defaultBlockState(), 2);}
                }
                int low = Math.min(c.lowest, c.y);
                boolean edge = false;
                for (int[] q : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    boolean in = false;
                    for (Shape s : shapes) if (s.lowest.containsKey(TerrainFit.key(x + q[0], z + q[1]))) {in = true; break;}
                    if (!in) {edge = true; break;}
                }
                for (int y = low - 1, n = 0; y > bottom && n < 40; y--, n++) {
                    var s = level.getBlockState(m.set(x, y, z));
                    if (s.isSolid() && !s.is(BlockTags.LEAVES) && !s.is(BlockTags.LOGS)) break;
                    level.setBlock(m, edge ? footing(x, z) : Blocks.DIRT.defaultBlockState(), 2);
                }
                continue;
            }
            int t = c.y, n = c.n;
            if (t != n) {
                BlockState surface = level.getBlockState(m.set(x, n - 1, z));
                if (!ground(surface) || surface.is(Blocks.DIRT_PATH)) surface = Blocks.GRASS_BLOCK.defaultBlockState();
                if (surface.is(Blocks.STONE) || surface.is(BlockTags.BASE_STONE_OVERWORLD)) surface = t > n ? Blocks.GRASS_BLOCK.defaultBlockState() : surface;
                if (t < n) {
                    for (int y = t; y < n; y++) level.setBlock(m.set(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                } else {
                    for (int y = n - 1; y < t - 1; y++) {
                        var s = level.getBlockState(m.set(x, y, z));
                        if (!s.isSolid() || flora(s)) level.setBlock(m, Blocks.DIRT.defaultBlockState(), 2);
                        else if (ground(s)) level.setBlock(m, Blocks.DIRT.defaultBlockState(), 2);
                    }
                }
                level.setBlock(m.set(x, t - 1, z), surface, 2);
            }
            // [1.1.3] grade 2: where a road is cut deep into a slope, the bank beside it is a dry-stone retaining wall
            // (mossy cobble and andesite) instead of a raw dirt cliff
            if (p.grade >= 2 && c.kind == EDGE && c.dRoad < 2.5) {
                int low = Integer.MAX_VALUE;
                for (int[] q : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    Col o = col(x + q[0], z + q[1], nat);
                    if (o.kind == ROAD) low = Math.min(low, o.y);
                }
                if (low != Integer.MAX_VALUE && t - low >= 3) {
                    for (int y = low; y < t - 1; y++) level.setBlock(m.set(x, y, z), footing(x, y * 31 + z), 2);
                }
            }
            // clear the way: trees and brush on the green, the roads, regraded ground and near the buildings
            if (c.kind == GREEN || c.kind == ROAD || t != n || c.w > 0.3) {
                for (int y = Math.min(t, n); y <= Math.min(top, Math.max(t, n) + 32); y++) {
                    var s = level.getBlockState(m.set(x, y, z));
                    if (s.isAir()) continue;
                    // [1.1.3] grade 2: a crown or bough overhanging from outside stays whole above head height (cutting
                    // it left floating, half trees round villages); only what stands in the way is cleared
                    if (p.grade >= 2 && y > Math.max(t, n) + 4 && (s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES))) continue;
                    if (flora(s)) level.setBlock(m, Blocks.AIR.defaultBlockState(), 2);
                    else if (y > Math.max(t, n) + 2) break;
                }
            }
        }
    }

    // ------------------------------------------------------------------ phase 1: road surfaces and the natural edge

    static boolean surfaceable(BlockState s) {
        return s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.PODZOL) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.MYCELIUM)
            || s.is(Blocks.ROOTED_DIRT) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.MOSS_BLOCK) || s.is(Blocks.SAND) || s.is(Blocks.GRAVEL)
            || s.is(Blocks.MUD) || s.is(Blocks.CLAY) || s.is(Blocks.STONE) || s.is(Blocks.DIRT_PATH)
            || s.getBlock().builtInRegistryHolder().key().location().getNamespace().equals("frontierhunts") && TerrainFit.terrain(s);
    }

    static boolean grassy(BlockState s) {
        return s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.PODZOL) || s.is(Blocks.MOSS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.COARSE_DIRT);
    }

    public void surface(WorldGenLevel level, BoundingBox box, TerrainFit.Natural nat) {
        var m = new BlockPos.MutableBlockPos();
        int x0 = Math.max(box.minX(), p.minX), x1 = Math.min(box.maxX(), p.maxX), z0 = Math.max(box.minZ(), p.minZ), z1 = Math.min(box.maxZ(), p.maxZ);
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            Col c = col(x, z, nat);
            if (c.kind == NONE || c.kind == WET || c.kind == FOOT) continue;
            int y = c.y;
            double h = hash(p.seed + 3, x, z);
            if (p.grade >= 2 && (c.kind == ROAD || c.dRoad < 2.2)) {
                if (road2(level, m, c, x, y, z)) continue;
            }
            if (c.kind == ROAD) {
                boolean raggedEdge = c.dRoad > -0.7 && h < 0.4;
                boolean faded = c.fade && c.along > 0.55 && hash(p.seed + 5, x, z) < smooth((c.along - 0.55) / 0.45) * 0.95;
                if (raggedEdge || faded) {
                    if (faded && hash(p.seed + 6, x, z) < 0.3) setTop(level, m, x, y, z, Blocks.COARSE_DIRT.defaultBlockState());
                    continue;
                }
                double nz = noise(p.seed + 9, x, z, 4);
                BlockState s;
                if (c.style == 1) s = nz < -0.1 ? Blocks.GRAVEL.defaultBlockState() : nz < 0.45 ? Blocks.DIRT_PATH.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
                else s = nz < -0.45 ? Blocks.GRAVEL.defaultBlockState() : nz < 0.5 ? Blocks.DIRT_PATH.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
                setTop(level, m, x, y, z, s);
                continue;
            }
            // the natural blend: brush and ferns hugging the footings, grass and flowers fading out over the apron
            var below = level.getBlockState(m.set(x, y - 1, z));
            if (!grassy(below) || !level.getBlockState(m.set(x, y, z)).isAir() || !level.getBlockState(m.set(x, y + 1, z)).isAir()) continue;
            if (c.dRoad < 1.2) continue;
            BlockState plant = null;
            double r = hash(p.seed + 11, x, z);
            if (c.dBuild >= 0.9 && c.dBuild <= 2.6 && h < 0.2) {
                plant = r < 0.3 ? Blocks.FERN.defaultBlockState() : r < 0.55 ? Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE, 1 + (int)(r * 10) % 3)
                    : r < 0.8 ? Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true) : Blocks.LARGE_FERN.defaultBlockState();
            } else if (c.kind == GREEN) {
                if (h < 0.05) plant = r < 0.4 ? Blocks.DANDELION.defaultBlockState() : r < 0.7 ? Blocks.POPPY.defaultBlockState() : Blocks.OXEYE_DAISY.defaultBlockState();
                else if (h < 0.14) plant = Blocks.SHORT_GRASS.defaultBlockState();
            } else if (c.w > 0.03 && c.w < 0.8) {
                if (h < 0.13) plant = Blocks.SHORT_GRASS.defaultBlockState();
                else if (h < 0.18) plant = Blocks.FERN.defaultBlockState();
                else if (h < 0.21) plant = Blocks.TALL_GRASS.defaultBlockState();
                else if (h < 0.225) plant = r < 0.5 ? Blocks.CORNFLOWER.defaultBlockState() : Blocks.AZURE_BLUET.defaultBlockState();
            }
            if (plant == null) continue;
            if (!below.is(Blocks.GRASS_BLOCK) && !below.is(Blocks.PODZOL) && !below.is(Blocks.MOSS_BLOCK) && plant.is(Blocks.SHORT_GRASS)) continue;
            if (plant.is(Blocks.TALL_GRASS) || plant.is(Blocks.LARGE_FERN)) {
                level.setBlock(m.set(x, y, z), plant.setValue(net.minecraft.world.level.block.DoublePlantBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER), 2);
                level.setBlock(m.set(x, y + 1, z), plant.setValue(net.minecraft.world.level.block.DoublePlantBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 2);
            } else {
                if (plant.is(Blocks.SWEET_BERRY_BUSH) && !below.is(Blocks.GRASS_BLOCK) && !below.is(Blocks.PODZOL) && !below.is(Blocks.DIRT) && !below.is(Blocks.COARSE_DIRT)) continue;
                level.setBlock(m.set(x, y, z), plant, 2);
            }
        }
    }

    /**
     * [1.1.3] grade-2 road surfaces, blended into the land: the trail is mostly packed earth with soft patches of
     * gravel and coarse dirt (broad, uneven patches, not a checker), its edge frays into the grass over a block or two
     * (grass eats in from the side, scuffed earth and stones spill out past it), and a road that fades into the
     * countryside thins to two worn tracks before it is gone. Returns true when it handled the column.
     */
    private boolean road2(WorldGenLevel level, BlockPos.MutableBlockPos m, Col c, int x, int y, int z) {
        double fray = noise(p.seed + 21, x, z, 3) * 0.5 + (hash(p.seed + 22, x, z) - 0.5) * 0.6;
        double d = c.dRoad; // <= 0 on the road
        if (c.kind != ROAD) {
            // past the edge: scuffed earth spilling out, thinning over two blocks
            if (d > 2.2 || c.kind == FOOT || c.kind == WET) return false;
            double spill = (1 - d / 2.2) * 0.55 + fray * 0.3;
            if (spill < 0.42) return false;
            var below = level.getBlockState(m.set(x, y - 1, z));
            if (!below.is(Blocks.GRASS_BLOCK) && !below.is(Blocks.PODZOL)) return false;
            setTop(level, m, x, y, z, hash(p.seed + 23, x, z) < 0.6 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.ROOTED_DIRT.defaultBlockState());
            return true;
        }
        // on the road: grass creeping in from the edges, and the faded end of a road going back to grass
        double into = -d; // blocks in from the edge
        double keep = into / 1.4 + fray * 0.45;
        if (c.fade && c.along > 0.5) {
            double f = smooth((c.along - 0.5) / 0.5);
            // two tracks: the middle grows over first
            double mid = Math.abs(into - c.half);
            keep -= f * (mid < 0.8 ? 1.4 : 0.6);
        }
        if (keep < 0.35) {
            var below = level.getBlockState(m.set(x, y - 1, z));
            if (below.is(Blocks.GRASS_BLOCK) || below.is(Blocks.PODZOL)) return true; // left as grass
            setTop(level, m, x, y, z, Blocks.GRASS_BLOCK.defaultBlockState());
            return true;
        }
        double n1 = noise(p.seed + 9, x, z, 7) * 0.7 + noise(p.seed + 19, x, z, 3) * 0.3;
        BlockState s;
        if (into < 0.9 && keep < 0.65) s = Blocks.COARSE_DIRT.defaultBlockState(); // the scuffed verge
        else if (n1 < -0.42) s = Blocks.GRAVEL.defaultBlockState();
        else if (n1 > 0.48) s = Blocks.COARSE_DIRT.defaultBlockState();
        else s = Blocks.DIRT_PATH.defaultBlockState();
        setTop(level, m, x, y, z, s);
        return true;
    }

    private static void setTop(WorldGenLevel level, BlockPos.MutableBlockPos m, int x, int y, int z, BlockState s) {
        var cur = level.getBlockState(m.set(x, y - 1, z));
        if (!surfaceable(cur)) return;
        level.setBlock(m, s, 2);
        var above = level.getBlockState(m.set(x, y, z));
        if (!above.isAir() && flora(above)) {
            level.setBlock(m, Blocks.AIR.defaultBlockState(), 2);
            var a2 = level.getBlockState(m.set(x, y + 1, z));
            if (a2.getBlock() instanceof net.minecraft.world.level.block.DoublePlantBlock) level.setBlock(m, Blocks.AIR.defaultBlockState(), 2);
        }
    }
}
