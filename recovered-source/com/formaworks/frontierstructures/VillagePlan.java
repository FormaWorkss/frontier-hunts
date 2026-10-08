package com.formaworks.frontierstructures;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * [villages] The layout of one planned hunting village built from Austin's eight authored buildings: which building
 * stands where (entrance facing the village green), the yard level of each (terraces follow the land), the road network
 * (spokes from every door to a ring road around the green, roads out of the village that fade into the countryside)
 * and the furnishing (fire pit, flagpole, notice board, well, lamp posts, benches, wood piles, hitching rails, sign
 * posts). Pure data + a deterministic planner; {@link VillageGround} grades the land from it, {@link VillageDeco}
 * furnishes it. Everything is a function of the seed and the generator's natural heights, so every chunk agrees.
 */
public final class VillagePlan {
    // ------------------------------------------------------------------ authored templates

    /** footprint and entrance of an authored template (local coordinates, front = -Z, path stub at z = 0) */
    public static final class Info {
        public final int sx, sy, sz, ex;
        public final Map<Long, Integer> lowest = new HashMap<>();
        Info(StructureTemplate t) {this(t.save(new CompoundTag()));}

        /** from saved template data (the .nbt layout): offline tools use this too */
        public Info(CompoundTag data) {
            var size = data.getList("size", 3);
            sx = size.getInt(0); sy = size.getInt(1); sz = size.getInt(2);
            var palette = data.getList("palette", 10);
            if (palette.isEmpty() && data.contains("palettes")) palette = data.getList("palettes", 9).getList(0);
            int sum = 0, n = 0;
            for (var e : data.getList("blocks", 10)) {
                var b = (CompoundTag)e;
                String name = palette.getCompound(b.getInt("state")).getString("Name");
                if (name.equals("minecraft:air") || name.equals("minecraft:structure_void") || name.equals("minecraft:structure_block")
                    || name.equals("frontierstructures:structure_space")) continue;
                var p = b.getList("pos", 3);
                int x = p.getInt(0), y = p.getInt(1), z = p.getInt(2);
                lowest.merge(TerrainFit.key(x, z), y, Math::min);
                if (z == 0 && y < 2) {sum += x; n++;}
            }
            ex = n > 0 ? Math.round((float)sum / n) : sx / 2;
        }
    }

    private static final Map<ResourceLocation, Info> INFO = new ConcurrentHashMap<>();

    public static Info info(StructureTemplateManager m, ResourceLocation id) {
        return INFO.computeIfAbsent(id, k -> new Info(m.getOrCreate(k)));
    }

    /** template lookup (the game's template manager, or the .nbt files for offline previews) */
    public interface Templates {Info info(ResourceLocation id);}

    public static Templates templates(StructureTemplateManager m) {return id -> info(m, id);}

    public static ResourceLocation tpl(String name) {return ResourceLocation.fromNamespaceAndPath(FrontierStructures.ID, "expedition/settlement_" + name);}

    // ------------------------------------------------------------------ plan data

    public static final class Lot {
        public final ResourceLocation template;
        public final Rotation rotation;
        public final int ox, oz, yard; // template origin x/z, yard level (first air above the yard; template y 2)
        public final int wx, wz;       // entrance (start of the authored path stub) in world coordinates
        Lot(ResourceLocation t, Rotation r, int ox, int oz, int yard, int wx, int wz) {template = t; rotation = r; this.ox = ox; this.oz = oz; this.yard = yard; this.wx = wx; this.wz = wz;}
        public int originY() {return yard - 2;}
    }

    /** a road: polyline with a level per point; style 0 = dirt road, 1 = gravel ring / village street */
    public static final class Road {
        public final int[] x, z, y;
        public final float half;
        public final int style;
        public final boolean fade; // the far end thins out into the countryside
        Road(int[] x, int[] z, int[] y, float half, int style, boolean fade) {this.x = x; this.z = z; this.y = y; this.half = half; this.style = style; this.fade = fade;}
    }

    /** a furnishing at (x, z) on the graded ground y (first air), facing a horizontal direction (2D data value) */
    public static final class Deco {
        public final String kind;
        public final int x, z, y, facing, arg;
        Deco(String kind, int x, int z, int y, int facing, int arg) {this.kind = kind; this.x = x; this.z = z; this.y = y; this.facing = facing; this.arg = arg;}
    }

    public final String type;
    public final long seed;
    public final int cx, cz;
    public int greenY, greenR;
    public final List<Lot> lots = new ArrayList<>();
    public final List<Road> roads = new ArrayList<>();
    public final List<Deco> decos = new ArrayList<>();
    public int minX, minZ, maxX, maxZ, minY, maxY;
    /**
     * [1.1.3] how the ground is graded: 1 = up to 1.1.2 (an 8-block apron that ended in a step - the cliffs and pits
     * round villages), 2 = the apron widens with the height it has to make up and eases out smoothly. Villages planned
     * before keep 1, so a village half generated on an older version still joins up.
     */
    public int grade = 2;

    VillagePlan(String type, long seed, int cx, int cz) {this.type = type; this.seed = seed; this.cx = cx; this.cz = cz;}

    // ------------------------------------------------------------------ NBT

    public CompoundTag save() {
        var t = new CompoundTag();
        t.putString("type", type); t.putLong("seed", seed); t.putInt("cx", cx); t.putInt("cz", cz);
        t.putInt("gy", greenY); t.putInt("gr", greenR); t.putInt("grade", grade);
        t.putIntArray("box", new int[]{minX, minZ, maxX, maxZ, minY, maxY});
        var ls = new ListTag();
        for (Lot l : lots) {
            var c = new CompoundTag();
            c.putString("t", l.template.toString()); c.putString("r", l.rotation.name());
            c.putIntArray("p", new int[]{l.ox, l.oz, l.yard, l.wx, l.wz});
            ls.add(c);
        }
        t.put("lots", ls);
        var rs = new ListTag();
        for (Road r : roads) {
            var c = new CompoundTag();
            c.putIntArray("x", r.x); c.putIntArray("z", r.z); c.putIntArray("y", r.y);
            c.putFloat("h", r.half); c.putInt("s", r.style); c.putBoolean("f", r.fade);
            rs.add(c);
        }
        t.put("roads", rs);
        var ds = new ListTag();
        for (Deco d : decos) {
            var c = new CompoundTag();
            c.putString("k", d.kind); c.putIntArray("p", new int[]{d.x, d.z, d.y, d.facing, d.arg});
            ds.add(c);
        }
        t.put("decos", ds);
        return t;
    }

    public static VillagePlan load(CompoundTag t) {
        var p = new VillagePlan(t.getString("type"), t.getLong("seed"), t.getInt("cx"), t.getInt("cz"));
        p.greenY = t.getInt("gy"); p.greenR = t.getInt("gr");
        p.grade = t.contains("grade") ? t.getInt("grade") : 1;
        int[] b = t.getIntArray("box");
        if (b.length == 6) {p.minX = b[0]; p.minZ = b[1]; p.maxX = b[2]; p.maxZ = b[3]; p.minY = b[4]; p.maxY = b[5];}
        for (Tag e : t.getList("lots", 10)) {
            var c = (CompoundTag)e; int[] q = c.getIntArray("p");
            p.lots.add(new Lot(ResourceLocation.parse(c.getString("t")), Rotation.valueOf(c.getString("r")), q[0], q[1], q[2], q[3], q[4]));
        }
        for (Tag e : t.getList("roads", 10)) {
            var c = (CompoundTag)e;
            p.roads.add(new Road(c.getIntArray("x"), c.getIntArray("z"), c.getIntArray("y"), c.getFloat("h"), c.getInt("s"), c.getBoolean("f")));
        }
        for (Tag e : t.getList("decos", 10)) {
            var c = (CompoundTag)e; int[] q = c.getIntArray("p");
            p.decos.add(new Deco(c.getString("k"), q[0], q[1], q[2], q[3], q[4]));
        }
        return p;
    }

    // ------------------------------------------------------------------ geometry helpers

    /** template-local (x, z) -> world offset, same as StructureTemplate.transform with pivot 0 and no mirror */
    public static int[] rot(Rotation r, int x, int z) {
        return switch (r) {
            case NONE -> new int[]{x, z};
            case CLOCKWISE_90 -> new int[]{-z, x};
            case CLOCKWISE_180 -> new int[]{-x, -z};
            case COUNTERCLOCKWISE_90 -> new int[]{z, -x};
        };
    }

    /** the rotation whose front (template -Z) points along (dx, dz) */
    static Rotation facing(double dx, double dz) {
        if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? Rotation.CLOCKWISE_90 : Rotation.COUNTERCLOCKWISE_90;
        return dz > 0 ? Rotation.CLOCKWISE_180 : Rotation.NONE;
    }

    /** unit front vector of a rotation */
    static int[] front(Rotation r) {return rot(r, 0, -1);}

    /** world footprint bbox of a template placed with origin (ox, oz) */
    static int[] bbox(Info in, Rotation r, int ox, int oz) {
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (long k : in.lowest.keySet()) {
            int[] w = rot(r, (int)(k >> 32), (int)k);
            minX = Math.min(minX, ox + w[0]); maxX = Math.max(maxX, ox + w[0]);
            minZ = Math.min(minZ, oz + w[1]); maxZ = Math.max(maxZ, oz + w[1]);
        }
        return new int[]{minX, minZ, maxX, maxZ};
    }

    static boolean overlap(int[] a, int[] b, int gap) {
        return a[0] - gap <= b[2] && b[0] - gap <= a[2] && a[1] - gap <= b[3] && b[1] - gap <= a[3];
    }

    static double distToBox(double x, double z, int[] b) {
        double dx = Math.max(Math.max(b[0] - x, 0), x - b[2]), dz = Math.max(Math.max(b[1] - z, 0), z - b[3]);
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ------------------------------------------------------------------ planner

    /** what the planner needs from the world: natural heights (first air above ground / above water) */
    public interface Land extends TerrainFit.Natural {}

    private final List<int[]> boxes = new ArrayList<>();       // footprint bboxes of placed lots
    private final Set<Long> used = new HashSet<>();             // cells claimed by roads / decos (plan time)
    private TerrainFit.Natural land;
    private Random rnd;

    boolean wet(int x, int z) {return land.surface(x, z) > land.floor(x, z);}

    /** distance from a building box that must stay dry: beaches and sandy shores lie within it */
    static final int SHORE = 12;

    /**
     * True when open water lies within {@link #SHORE} blocks of the box and the lot would sit less than 6 blocks above
     * that water: the strip of sand/gravel at a lake or sea edge. Houses stand back from the water on firm ground.
     */
    boolean shore(int[] b, int yard) {
        for (int ring = 0; ring <= SHORE; ring += 4) {
            int x0 = b[0] - ring, z0 = b[1] - ring, x1 = b[2] + ring, z1 = b[3] + ring;
            int steps = 10;
            for (int i = 0; i <= steps; i++) {
                int[][] pts = {{x0 + (x1 - x0) * i / steps, z0}, {x0 + (x1 - x0) * i / steps, z1}, {x0, z0 + (z1 - z0) * i / steps}, {x1, z0 + (z1 - z0) * i / steps}};
                for (int[] p : pts) {
                    if (land.shore(p[0], p[1])) return true;
                    if (wet(p[0], p[1]) && yard - land.surface(p[0], p[1]) < 6) return true;
                }
            }
        }
        return false;
    }

    /** sampled ground over a box: {min, max, upper median, wet samples} */
    int[] survey(int[] b, int n) {
        int[] hs = new int[n * n]; int k = 0, wet = 0;
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) {
            int x = b[0] + (b[2] - b[0]) * i / (n - 1), z = b[1] + (b[3] - b[1]) * j / (n - 1);
            hs[k++] = land.floor(x, z);
            if (wet(x, z)) wet++;
        }
        Arrays.sort(hs);
        return new int[]{hs[0], hs[hs.length - 1], hs[hs.length * 3 / 5], wet};
    }

    /**
     * Places one building with its entrance on the ray from the centre at angle {@code ang}, front facing the centre,
     * pushed outward until it keeps {@code gap} blocks from the other buildings and clears radius {@code clear}.
     * @return the lot, or null when the ground there does not suit it
     */
    Lot placeOnRay(Templates m, String name, double ang, int clear, int gap, int lo, int hi, int maxRelief) {
        var id = tpl(name);
        Info in = m.info(id);
        double ux = Math.cos(ang), uz = Math.sin(ang);
        Rotation r = facing(-ux, -uz);
        int[] e = rot(r, in.ex, 0);
        for (int d = clear; d < clear + 40; d++) {
            int wx = cx + (int)Math.round(ux * d), wz = cz + (int)Math.round(uz * d);
            int ox = wx - e[0], oz = wz - e[1];
            int[] b = bbox(in, r, ox, oz);
            if (distToBox(cx, cz, b) < clear - 1) continue;
            boolean hit = false;
            for (int[] o : boxes) if (overlap(b, o, gap)) {hit = true; break;}
            if (hit) continue;
            return fit(id, in, r, ox, oz, wx, wz, b, lo, hi, maxRelief);
        }
        return null;
    }

    /** levels a lot: rejects water and steep ground, yard = upper median clamped to [lo, hi] */
    Lot fit(ResourceLocation id, Info in, Rotation r, int ox, int oz, int wx, int wz, int[] b, int lo, int hi, int maxRelief) {
        int[] s = survey(b, 5);
        if (s[3] > 0 || wet(wx, wz)) return no("lot:wet");
        if (s[1] - s[0] > maxRelief) return no("lot:relief");
        int yard = Math.max(lo, Math.min(hi, s[2]));
        if (shore(b, yard)) return no("lot:shore"); // no buildings on beaches / sandy lake shores
        // never bury more than a storey or perch the building on a tall plinth
        if (yard - s[0] > 8 || s[1] - yard > 9) return no("lot:yard");
        boxes.add(b);
        Lot l = new Lot(id, r, ox, oz, yard, wx, wz);
        lots.add(l);
        return l;
    }

    // ---- roads

    /** a road along points (x, z) whose level follows the land, smoothed, slope-limited and pinned at the ends */
    Road road(List<int[]> pts, int y0, int y1, float half, int style, boolean fade) {
        int n = pts.size();
        int[] xs = new int[n], zs = new int[n];
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {xs[i] = pts.get(i)[0]; zs[i] = pts.get(i)[1]; y[i] = wet(xs[i], zs[i]) ? Double.NaN : land.floor(xs[i], zs[i]);}
        for (int i = 0; i < n; i++) if (Double.isNaN(y[i])) y[i] = i > 0 ? y[i - 1] : (y0 != Integer.MIN_VALUE ? y0 : 64);
        if (y0 != Integer.MIN_VALUE) y[0] = y0;
        if (y1 != Integer.MIN_VALUE) y[n - 1] = y1;
        for (int pass = 0; pass < 3; pass++) { // smooth
            double[] s = y.clone();
            for (int i = 1; i < n - 1; i++) {double a = 0; int c = 0; for (int j = Math.max(0, i - 3); j <= Math.min(n - 1, i + 3); j++) {a += y[j]; c++;} s[i] = a / c;}
            y = s;
            if (y0 != Integer.MIN_VALUE) y[0] = y0;
            if (y1 != Integer.MIN_VALUE) y[n - 1] = y1;
        }
        // limit the grade (points are ~2 blocks apart): at most one block rise per two blocks of road
        for (int i = 1; i < n; i++) y[i] = Math.max(y[i - 1] - 1.0, Math.min(y[i - 1] + 1.0, y[i]));
        for (int i = n - 2; i >= 0; i--) y[i] = Math.max(y[i + 1] - 1.0, Math.min(y[i + 1] + 1.0, y[i]));
        int[] ys = new int[n];
        for (int i = 0; i < n; i++) ys[i] = (int)Math.round(y[i]);
        Road r = new Road(xs, zs, ys, half, style, fade);
        roads.add(r);
        for (int i = 0; i + 1 < n; i++) claim(xs[i], zs[i], xs[i + 1], zs[i + 1], half + 1);
        return r;
    }

    void claim(int x0, int z0, int x1, int z1, float half) {
        int steps = Math.max(1, (int)Math.ceil(Math.hypot(x1 - x0, z1 - z0)));
        int h = (int)Math.ceil(half);
        for (int s = 0; s <= steps; s++) {
            int x = x0 + (x1 - x0) * s / steps, z = z0 + (z1 - z0) * s / steps;
            for (int dx = -h; dx <= h; dx++) for (int dz = -h; dz <= h; dz++) used.add(TerrainFit.key(x + dx, z + dz));
        }
    }

    /** points from a to b every ~2 blocks, bowed sideways by {@code bow} blocks at the middle */
    static List<int[]> line(double ax, double az, double bx, double bz, double bow) {
        double len = Math.hypot(bx - ax, bz - az);
        int n = Math.max(2, (int)Math.ceil(len / 2));
        double nx = -(bz - az) / Math.max(len, 1e-6), nz = (bx - ax) / Math.max(len, 1e-6);
        var out = new ArrayList<int[]>();
        for (int i = 0; i <= n; i++) {
            double t = (double)i / n, off = bow * 4 * t * (1 - t);
            int[] p = {(int)Math.round(ax + (bx - ax) * t + nx * off), (int)Math.round(az + (bz - az) * t + nz * off)};
            if (out.isEmpty() || out.get(out.size() - 1)[0] != p[0] || out.get(out.size() - 1)[1] != p[1]) out.add(p);
        }
        return out;
    }

    /** spoke road from a lot's door to the ring road */
    void spoke(Lot l, int ringR) {
        int[] f = front(l.rotation);
        double ax = l.wx + 0.5 - f[0] * 0.5, az = l.wz + 0.5 - f[1] * 0.5; // just inside the stub
        double sx = l.wx + f[0] * 3, sz = l.wz + f[1] * 3;
        double dx = sx - cx, dz = sz - cz, d = Math.max(1, Math.hypot(dx, dz));
        double ex = cx + dx / d * ringR, ez = cz + dz / d * ringR;
        var pts = line(ax, az, sx, sz, 0);
        var rest = line(sx, sz, ex, ez, 0);
        pts.addAll(rest.subList(1, rest.size()));
        road(pts, l.yard, greenY, 1.4f, 0, false);
    }

    void ring(int r) {
        var pts = new ArrayList<int[]>();
        int n = Math.max(16, (int)(2 * Math.PI * r / 2));
        for (int i = 0; i <= n; i++) {double a = 2 * Math.PI * i / n; pts.add(new int[]{cx + (int)Math.round(Math.cos(a) * r), cz + (int)Math.round(Math.sin(a) * r)});}
        int[] xs = new int[pts.size()], zs = new int[pts.size()], ys = new int[pts.size()];
        for (int i = 0; i < pts.size(); i++) {xs[i] = pts.get(i)[0]; zs[i] = pts.get(i)[1]; ys[i] = greenY;}
        roads.add(new Road(xs, zs, ys, 1.5f, 1, false));
        for (int i = 0; i + 1 < xs.length; i++) claim(xs[i], zs[i], xs[i + 1], zs[i + 1], 2.5f);
    }

    /** road out of the village: from the ring outward, gently curving, fading into the land */
    Road exit(double ang, int from, int len) {
        double ux = Math.cos(ang), uz = Math.sin(ang);
        double ax = cx + ux * from, az = cz + uz * from, bx = cx + ux * (from + len), bz = cz + uz * (from + len);
        return road(line(ax, az, bx, bz, (rnd.nextDouble() - 0.5) * 10), greenY, Integer.MIN_VALUE, 1.4f, 0, true);
    }

    // ---- furnishing

    boolean free(int x, int z, int w, int d) {
        for (int i = -w; i <= w; i++) for (int j = -d; j <= d; j++) {
            if (used.contains(TerrainFit.key(x + i, z + j))) return false;
            for (int[] b : boxes) if (x + i >= b[0] - 1 && x + i <= b[2] + 1 && z + j >= b[1] - 1 && z + j <= b[3] + 1) return false;
            if (wet(x + i, z + j)) return false;
        }
        return true;
    }

    void deco(String kind, int x, int z, int facing, int arg, int w, int d, VillageGround g) {
        if (!free(x, z, w, d)) return;
        for (int i = -w; i <= w; i++) for (int j = -d; j <= d; j++) used.add(TerrainFit.key(x + i, z + j));
        decos.add(new Deco(kind, x, z, g.level(x, z, land), facing, arg));
    }

    /** 2D data value of the horizontal direction nearest to (dx, dz): 0 south, 1 west, 2 north, 3 east */
    static int dir(double dx, double dz) {
        if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? 3 : 1;
        return dz > 0 ? 0 : 2;
    }

    /** lamp posts along a road every {@code every} blocks, alternating sides */
    void lamps(Road r, int every, int skipStart, VillageGround g) {
        double acc = 0; int side = 1, placed = 0;
        for (int i = 1; i < r.x.length; i++) {
            double seg = Math.hypot(r.x[i] - r.x[i - 1], r.z[i] - r.z[i - 1]);
            acc += seg;
            if (acc < skipStart + every * (placed + 1)) continue;
            double tx = (r.x[i] - r.x[i - 1]) / Math.max(seg, 1e-6), tz = (r.z[i] - r.z[i - 1]) / Math.max(seg, 1e-6);
            int lx = (int)Math.round(r.x[i] - tz * side * (r.half + 1.6)), lz = (int)Math.round(r.z[i] + tx * side * (r.half + 1.6));
            deco("lamp", lx, lz, 0, 0, 0, 0, g);
            side = -side; placed++;
            if (r.fade && acc > (r.x.length * 2) * 0.6) break;
        }
    }

    /** [gear20] the pieces beside one building: chosen for its kind, varied by seed, at most two */
    void lotDecos(Lot l, boolean right, int i, VillageGround g) {
        String t = l.template.getPath();
        List<String> pick = new ArrayList<>();
        if (t.endsWith("lodge")) pick.addAll(List.of("hitch", "seat"));
        else if (t.endsWith("outfitter")) pick.addAll(List.of("hitch", rnd.nextBoolean() ? "barrels" : "seat"));
        else if (t.endsWith("homestead")) pick.addAll(List.of("garden", rnd.nextBoolean() ? "woodpile" : "seat"));
        else if (t.endsWith("trapper")) pick.addAll(List.of("woodpile", rnd.nextBoolean() ? "garden" : "barrels"));
        else if (t.endsWith("smokehouse")) pick.addAll(List.of("woodpile", "barrels"));
        else if (t.endsWith("fishing")) pick.addAll(List.of("barrels", "seat"));
        else pick.add(rnd.nextBoolean() ? "seat" : "woodpile");
        if (pick.size() > 1 && rnd.nextInt(4) == 0) pick.remove(1);
        boolean side = right;
        for (String k : pick) {
            int along = switch (k) {case "garden" -> -6; case "hitch" -> -4; case "seat" -> 1; case "woodpile" -> 2; default -> -3;};
            besideLot(l, k, rnd.nextInt(4), side, along, g);
            side = !side;
        }
    }

    /** wood pile against the side of a lot (behind the front, beside the wall) */
    void besideLot(Lot l, String kind, int arg, boolean right, int along, VillageGround g) {
        int ext = kind.equals("garden") ? 2 : 1;
        int[] b = boxes.get(lots.indexOf(l));
        int[] f = front(l.rotation);
        int[] s = right ? new int[]{-f[1], f[0]} : new int[]{f[1], -f[0]};
        // centre of the box, pushed to the chosen side
        double mx = (b[0] + b[2]) / 2.0, mz = (b[1] + b[3]) / 2.0;
        double hx = (b[2] - b[0]) / 2.0 + 3 + ext, hz = (b[3] - b[1]) / 2.0 + 3 + ext;
        int x = (int)Math.round(mx + s[0] * hx + f[0] * along), z = (int)Math.round(mz + s[1] * hz + f[1] * along);
        deco(kind, x, z, dir(f[0], f[1]), arg, ext, ext, g);
    }

    void plaza(VillageGround g, double mainAng, int ringR) {
        // the green: fire pit with log benches, a flagpole, the notice board facing the main road, a well
        double ux = Math.cos(mainAng), uz = Math.sin(mainAng);
        // [gear20] the fire pit's open side faces the main road (the way in), seats on the other three
        decos.add(new Deco("firepit", cx, cz, greenY, dir(ux, uz), 0));
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) used.add(TerrainFit.key(cx + dx, cz + dz));
        // [gear20] where the main road reaches the green: the flagpole on one side of the way in, the Big-Buck Board and
        // the contract board on the other (fronts toward the road), so both greet you as you walk in
        double px = -uz, pz = ux;
        int ex = cx + (int)Math.round(ux * (greenR - 1)), ez = cz + (int)Math.round(uz * (greenR - 1));
        int fx = ex + (int)Math.round(px * 3), fz = ez + (int)Math.round(pz * 3);
        decos.add(new Deco("flagpole", fx, fz, greenY, dir(cx - fx, cz - fz), (int)Math.floorMod(Math.round(Math.toDegrees(mainAng + Math.PI / 2) / 22.5), 16)));
        for (int i = -2; i <= 2; i++) for (int j = -2; j <= 2; j++) used.add(TerrainFit.key(fx + i, fz + j));
        int facing = dir(ux, uz);
        int bx = ex - (int)Math.round(px * 3), bz = ez - (int)Math.round(pz * 3);
        decos.add(new Deco("notice_board", bx, bz, greenY, facing, 0));
        for (int i = -3; i <= 3; i++) for (int j = -3; j <= 3; j++) used.add(TerrainFit.key(bx + i, bz + j));
        // a well in the plaza beside the ring road, away from the main road
        for (int t = 0; t < 6; t++) {
            double a = mainAng + Math.PI * 0.75 + t * 0.6;
            int wx = cx + (int)Math.round(Math.cos(a) * (ringR + 5)), wz = cz + (int)Math.round(Math.sin(a) * (ringR + 5));
            int before = decos.size();
            deco("well", wx, wz, 0, 0, 2, 2, g);
            if (decos.size() > before) break;
        }
        // lamps around the ring; benches and chairs on two or three of its corners, each a different kind of seat
        int seats = 2 + rnd.nextInt(2), kind0 = rnd.nextInt(3);
        for (int i = 0; i < 4; i++) {
            double a = mainAng + Math.PI / 4 + i * Math.PI / 2;
            deco("lamp", cx + (int)Math.round(Math.cos(a) * (ringR + 3)), cz + (int)Math.round(Math.sin(a) * (ringR + 3)), dir(-Math.cos(a), -Math.sin(a)), 0, 0, 0, g);
            if (i >= seats) continue;
            double b2 = a + 0.3;
            int bx2 = cx + (int)Math.round(Math.cos(b2) * (ringR + 3)), bz2 = cz + (int)Math.round(Math.sin(b2) * (ringR + 3));
            deco("bench", bx2, bz2, dir(-Math.cos(b2), -Math.sin(b2)), kind0 + i, 1, 1, g);
        }
    }

    void bounds(int apron) {
        minX = cx; maxX = cx; minZ = cz; maxZ = cz; minY = greenY; maxY = greenY;
        for (int[] b : boxes) {minX = Math.min(minX, b[0]); minZ = Math.min(minZ, b[1]); maxX = Math.max(maxX, b[2]); maxZ = Math.max(maxZ, b[3]);}
        for (Lot l : lots) {minY = Math.min(minY, l.yard); maxY = Math.max(maxY, l.yard);}
        for (Road r : roads) for (int i = 0; i < r.x.length; i++) {
            minX = Math.min(minX, r.x[i]); maxX = Math.max(maxX, r.x[i]); minZ = Math.min(minZ, r.z[i]); maxZ = Math.max(maxZ, r.z[i]);
            minY = Math.min(minY, r.y[i]); maxY = Math.max(maxY, r.y[i]);
        }
        minX -= apron; minZ -= apron; maxX += apron; maxZ += apron;
        if (grade >= 2) {
            // [1.1.3] room for the wider apron, but every part of a village stays within 7 chunks of its start chunk
            // (structure references only reach 8)
            int more = VillageGround.REACH + 3 - apron, lim = 7 * 16;
            minX = Math.max(minX - more, cx - lim); minZ = Math.max(minZ - more, cz - lim);
            maxX = Math.min(maxX + more, cx + lim); maxZ = Math.min(maxZ + more, cz + lim);
        }
    }

    // ------------------------------------------------------------------ village types

    public static final String[] TYPES = {"outpost", "lakeside", "crowsnest", "junction"};

    /**
     * Plans a village of the given type centred at (cx, cz), or returns null when the land does not suit it. Cheap:
     * a few dozen natural-height samples (cached per Heights instance), no world access.
     */
    public static VillagePlan plan(String type, Templates m, TerrainFit.Natural land, long seed, int cx, int cz, int seaLevel) {
        var p = new VillagePlan(type, seed, cx, cz);
        p.land = land;
        p.rnd = new Random(seed);
        // quick look: centre dry, the wider area not a cliff or a lake
        if (p.wet(cx, cz)) return no("wet");
        int c0 = land.floor(cx, cz);
        if (c0 <= seaLevel) return no("sea");
        int lo = c0, hi = c0;
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            int h = land.floor(cx + (int)(Math.cos(a) * 36), cz + (int)(Math.sin(a) * 36));
            lo = Math.min(lo, h); hi = Math.max(hi, h);
        }
        if (hi - lo > switch (type) {case "crowsnest" -> 34; case "junction" -> 24; case "lakeside" -> 30; default -> 26;}) return no(type + ":octo");
        try {
            boolean ok = switch (type) {
                case "lakeside" -> p.lakeside(m);
                case "crowsnest" -> p.crowsnest(m);
                case "junction" -> p.junction(m);
                default -> p.outpost(m);
            };
            return ok ? p : null;
        } finally {
            p.land = null;
        }
    }

    public static final int ATTEMPTS = 12;

    /** rejection statistics for the offline harness */
    public static final Map<String, Integer> WHY = new ConcurrentHashMap<>();
    public static boolean STATS = false;
    static <T> T no(String why) {if (STATS) WHY.merge(why, 1, Integer::sum); return null;}


    public interface BiomeTest {boolean ok(int x, int y, int z);}

    /**
     * The land decides the layout: a few spots around the cell centre, at each the layouts in the order this land
     * suggests (water close by: the fishing landing first; otherwise a seeded order), the first plan that fits wins.
     * Shared with the offline harness (tools/villages).
     */
    public static VillagePlan chooseIn(VillagePlan.Templates t, TerrainFit.Natural h, RandomSource rnd, int mx, int mz, int sea, int minY, int maxY, BiomeTest biome) {
        // a lake or river near the cell's site: try a fishing landing on its shore first
        int shore = 0;
        for (int i = -4; i <= 4 && shore < 3; i++) for (int j = -4; j <= 4 && shore < 3; j++) {
            int wx = mx + i * 44, wz = mz + j * 44;
            if (h.surface(wx, wz) <= h.floor(wx, wz)) continue;
            for (int d = 0; d < 4 && shore < 3; d++) {
                double a = d * Math.PI / 2 + Math.PI / 4;
                int x = wx + (int)Math.round(Math.cos(a) * 60), z = wz + (int)Math.round(Math.sin(a) * 60);
                int y = h.floor(x, z);
                if (y <= sea || h.surface(x, z) > y || !biome.ok(x, y, z)) continue;
                shore++;
                VillagePlan p = ok(VillagePlan.plan("lakeside", t, h, rnd.nextLong(), x, z, sea), minY, maxY);
                if (p != null) return p;
            }
        }
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            // a spiral of spots out to ~130 blocks (so /locate still points into the village): the gentlest ground near the cell's site gets the village
            double ang = attempt * 2.39996 + rnd.nextDouble() * 0.5, rad = attempt == 0 ? 0 : 24 + attempt * 9 + rnd.nextInt(8);
            int x = mx + (int)Math.round(Math.cos(ang) * rad), z = mz + (int)Math.round(Math.sin(ang) * rad);
            long seed = rnd.nextLong();
            int y = h.floor(x, z);
            if (y <= sea || h.surface(x, z) > y || !biome.ok(x, y, z)) continue;
            for (String type : order(seed)) {
                VillagePlan p = ok(VillagePlan.plan(type, t, h, seed, x, z, sea), minY, maxY);
                if (p != null) return p;
            }
        }
        return null;
    }

    static VillagePlan ok(VillagePlan p, int minY, int maxY) {return p != null && p.minY >= minY + 16 && p.maxY + 48 <= maxY ? p : null;}

    /** layout order: the land decides - a shore makes a fishing landing, a wide flat Pine Junction, then a hill the
     *  Crowsnest or rolling ground an outpost (seeded, so both kinds of ground can give either) */
    static String[] order(long seed) {
        return (seed >>> 17) % 3 != 0 ? new String[]{"junction", "outpost", "crowsnest"} : new String[]{"junction", "crowsnest", "outpost"};
    }

    boolean green(int r, int maxRelief) {
        greenR = r;
        int[] s = survey(new int[]{cx - r - 2, cz - r - 2, cx + r + 2, cz + r + 2}, 4);
        if (s[3] > 0 || s[1] - s[0] > maxRelief) return no(type + ":green") != null;
        greenY = s[2];
        return true;
    }

    /** Frontier Outpost: lodge, outfitter, smokehouse and homestead around a green, two roads out */
    boolean outpost(Templates m) {
        if (!green(8, 6)) return false;
        double a0 = rnd.nextInt(4) * Math.PI / 2 + (rnd.nextDouble() - 0.5) * 0.35;
        int clear = greenR + 13;
        String[] order = {"lodge", "outfitter", "homestead", "smokehouse"};
        if (rnd.nextBoolean()) order = new String[]{"lodge", "smokehouse", "homestead", "outfitter"};
        int placed = 0;
        for (int i = 0; i < 4; i++) {
            double a = a0 + i * Math.PI / 2 + (rnd.nextDouble() - 0.5) * 0.3;
            Lot l = placeOnRay(m, order[i], a, clear, 9, greenY - 7, greenY + 7, 13);
            if (l == null && i == 0) return no("outpost:first") != null;
            if (l != null) placed++;
        }
        if (placed < 3) return no("outpost:placed<3") != null;
        // optional fifth: a trapper's cabin out on the edge, between two of the others
        double ta = a0 + Math.PI / 4 + rnd.nextInt(4) * Math.PI / 2;
        Lot trap = rnd.nextInt(3) > 0 ? placeOnRay(m, "trapper", ta, clear + 12, 9, greenY - 6, greenY + 6, 10) : null;
        return finish(m, new double[]{a0 + Math.PI / 4, a0 + Math.PI * 1.25}, "outpost");
    }

    /** Reedbank Landing: fishing hut toward the nearest lake or river, trapper and smokehouse beside it */
    boolean lakeside(Templates m) {
        // look for open water 30-60 blocks away
        double best = Double.NaN;
        outer:
        for (int r = 32; r <= 88; r += 14) for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8;
            if (wet(cx + (int)(Math.cos(a) * r), cz + (int)(Math.sin(a) * r))) {best = a; break outer;}
        }
        if (Double.isNaN(best)) return no("lakeside:nowater") != null;
        if (!green(6, 6)) return false;
        int clear = greenR + 11;
        Lot f = placeOnRay(m, "fishing", best, clear, 8, greenY - 6, greenY + 3, 11);
        if (f == null) f = placeOnRay(m, "fishing", best + 0.5, clear, 8, greenY - 6, greenY + 3, 11);
        if (f == null) return no("lakeside:fishing") != null;
        Lot t = placeOnRay(m, "trapper", best + 2.0 + (rnd.nextDouble() - 0.5) * 0.3, clear, 8, greenY - 5, greenY + 5, 10);
        Lot s = placeOnRay(m, "smokehouse", best - 2.0 + (rnd.nextDouble() - 0.5) * 0.3, clear, 8, greenY - 5, greenY + 5, 10);
        if (t == null && s == null) return no("lakeside:others") != null;
        return finish(m, new double[]{best + Math.PI}, "lakeside");
    }

    /** Crowsnest Ridge: the timber lookout on the high side, trapper and homestead below it */
    boolean crowsnest(Templates m) {
        if (!green(6, 6)) return false;
        double up = 0; int top = Integer.MIN_VALUE;
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6;
            int h = land.floor(cx + (int)(Math.cos(a) * 26), cz + (int)(Math.sin(a) * 26));
            if (h > top) {top = h; up = a;}
        }
        if (top - greenY < 3) return no("crowsnest:nohill") != null; // wants a hill
        int clear = greenR + 11;
        Lot l = placeOnRay(m, "lookout", up, clear, 8, greenY - 2, greenY + 9, 12);
        if (l == null) return false;
        Lot t = placeOnRay(m, "trapper", up + 2.1 + (rnd.nextDouble() - 0.5) * 0.3, clear, 8, greenY - 5, greenY + 4, 10);
        Lot h = placeOnRay(m, "homestead", up - 2.1 + (rnd.nextDouble() - 0.5) * 0.3, clear, 8, greenY - 5, greenY + 4, 10);
        if (t == null && h == null) return false;
        return finish(m, new double[]{up + Math.PI}, "crowsnest");
    }

    /** shared: ring road, spokes, roads out, furnishing */
    boolean finish(Templates m, double[] exits, String kind) {
        int ringR = greenR + 2;
        ring(ringR);
        for (Lot l : lots) spoke(l, ringR);
        int far = 0;
        for (int[] b : boxes) far = Math.max(far, (int)Math.ceil(Math.max(Math.max(Math.abs(b[0] - cx), Math.abs(b[2] - cx)), Math.max(Math.abs(b[1] - cz), Math.abs(b[3] - cz)))));
        var outs = new ArrayList<Road>();
        for (double a : exits) {
            // steer the road between buildings
            double best = a; double bestScore = -1;
            for (int k = -6; k <= 6; k++) {
                double aa = a + k * 0.08, score = 1e9;
                for (int[] b : boxes) for (int d = ringR; d < far + 10; d += 3) score = Math.min(score, distToBox(cx + Math.cos(aa) * d, cz + Math.sin(aa) * d, b));
                score -= Math.abs(k) * 0.05;
                if (score > bestScore) {bestScore = score; best = aa;}
            }
            if (bestScore < 3) continue;
            outs.add(exit(best, ringR, far - ringR + 26));
        }
        VillageGround g = new VillageGround(this, m);
        plaza(g, outs.isEmpty() ? exits[0] : Math.atan2(outs.get(0).z[outs.get(0).z.length - 1] - cz, outs.get(0).x[outs.get(0).x.length - 1] - cx), ringR);
        for (Road r : outs) {
            lamps(r, 11, 6, g);
            int n = r.x.length - 1, k = Math.max(1, n - 6);
            int tx = r.x[n] - r.x[k], tz = r.z[n] - r.z[k];
            double len = Math.max(1, Math.hypot(tx, tz));
            // village sign where the road meets the first buildings (facing travellers coming in)
            int si = Math.min(n, Math.max(1, (int)((far - ringR + 2) / 2.0)));
            int sx = (int)Math.round(r.x[si] - tz / len * (r.half + 1.7)), sz = (int)Math.round(r.z[si] + tx / len * (r.half + 1.7));
            deco("signpost", sx, sz, dir(tx, tz), r == outs.get(0) ? 1 : 0, 0, 0, g);
        }
        for (Road r : roads) if (r.style == 0 && !r.fade && r.x.length > 6) lamps(r, 8, 3, g);
        // per-lot furnishing: what that kind of place would have, at most two pieces, and never the same set twice
        int i = 0;
        for (Lot l : lots) {
            lotDecos(l, ((seed >>> i) & 1) == 0, i, g);
            i++;
        }
        bounds(VillageGround.APRON + 3);
        return true;
    }

    /** Pine Junction: Austin's whole hamlet on level ground, with a homestead and a trapper at the ends of its street */
    boolean junction(Templates m) {
        var hid = tpl("hamlet");
        Info h = m.info(hid);
        Rotation hr = Rotation.values()[rnd.nextInt(4)];
        // hamlet-local -> world: origin so the hamlet's middle lands on the centre
        int[] mid = rot(hr, h.sx / 2, h.sz / 2);
        int ox = cx - mid[0], oz = cz - mid[1];
        int[] b = bbox(h, hr, ox, oz);
        int[] s = survey(b, 7);
        if (s[3] > 0 || s[1] - s[0] > 19) return no("junction:flat") != null;
        greenY = s[2]; greenR = 0;
        boxes.add(b);
        int[] hw0 = rot(hr, 0, 0);
        lots.add(new Lot(hid, hr, ox, oz, greenY, ox + hw0[0], oz + hw0[1]));
        // street axis: hamlet-local z = 38, x from 6 to 124
        int streetZ = 38;
        java.util.function.BiFunction<Integer, Integer, int[]> W = (lx, lz) -> {int[] w = rot(hr, lx, lz); return new int[]{ox + w[0], oz + w[1]};};
        int[] west = W.apply(6, streetZ), east = W.apply(h.sx - 2, streetZ);
        int len = 48;
        int[] westEnd = W.apply(6 - len, streetZ), eastEnd = W.apply(h.sx - 2 + len, streetZ);
        // extra buildings: a homestead north of the west road, a trapper south of the east road, doors to the road
        Lot hl = extra(m, "homestead", hr.getRotated(Rotation.CLOCKWISE_180), W.apply(-16, streetZ - 4));
        Lot tl = extra(m, "trapper", hr, W.apply(h.sx + 14, streetZ + 4));
        Road wr = road(line(west[0], west[1], westEnd[0], westEnd[1], (rnd.nextDouble() - 0.5) * 6), greenY, Integer.MIN_VALUE, 1.5f, 0, true);
        Road er = road(line(east[0], east[1], eastEnd[0], eastEnd[1], (rnd.nextDouble() - 0.5) * 6), greenY, Integer.MIN_VALUE, 1.5f, 0, true);
        for (Lot l : new Lot[]{hl, tl}) {
            if (l == null) continue;
            int[] f = front(l.rotation);
            road(line(l.wx + 0.5, l.wz + 0.5, l.wx + f[0] * 4 + 0.5, l.wz + f[1] * 4 + 0.5, 0), l.yard, Integer.MIN_VALUE, 1.3f, 0, false);
        }
        VillageGround g = new VillageGround(this, m);
        for (Road r : new Road[]{wr, er}) {
            lamps(r, 12, 4, g);
            int n = r.x.length - 1;
            int tx = r.x[n] - r.x[0], tz = r.z[n] - r.z[0];
            double l2 = Math.max(1, Math.hypot(tx, tz));
            int si = Math.min(n, 4);
            int sx = (int)Math.round(r.x[si] - tz / l2 * (r.half + 1.7)), sz = (int)Math.round(r.z[si] + tx / l2 * (r.half + 1.7));
            deco("signpost", sx, sz, dir(tx, tz), 0, 0, 0, g);
        }
        int i = 0;
        for (Lot l : new Lot[]{hl, tl}) {
            if (l == null) continue;
            lotDecos(l, (i & 1) == 0, i, g);
            i++;
        }
        // [gear20] Pine Junction has no green: the flag and the boards stand where the west road meets the street
        {
            int tx = west[0] - westEnd[0], tz = west[1] - westEnd[1];
            double l2 = Math.max(1, Math.hypot(tx, tz)), ux = tx / l2, uz = tz / l2;
            boolean flag = false, board = false;
            for (int back = 3; back <= 21 && !(flag && board); back += 2) {
                for (int sgn = 1; sgn >= -1 && !flag; sgn -= 2) {
                    // the flag beside the road, the far side first (the boards take the near side), then the near side
                    int fx = (int)Math.round(west[0] - ux * back - uz * 4 * sgn), fz = (int)Math.round(west[1] - uz * back + ux * 4 * sgn);
                    int before = decos.size();
                    deco("flagpole", fx, fz, dir(uz * sgn, -ux * sgn), (int)Math.floorMod(Math.round(Math.toDegrees(Math.atan2(uz, ux) + Math.PI / 2) / 22.5), 16), 2, 2, g);
                    flag = decos.size() > before;
                }
                for (int off = 5; off <= 9 && !board; off += 2) {
                    int bx = (int)Math.round(west[0] - ux * back + uz * off), bz = (int)Math.round(west[1] - uz * back - ux * off);
                    int before = decos.size();
                    deco("notice_board", bx, bz, dir(-uz, ux), 0, 3, 3, g);
                    board = decos.size() > before;
                }
            }
        }
        bounds(VillageGround.APRON + 3);
        return true;
    }

    Lot extra(Templates m, String name, Rotation r, int[] w) {
        var id = tpl(name);
        Info in = m.info(id);
        int[] e = rot(r, in.ex, 0);
        int ox = w[0] - e[0], oz = w[1] - e[1];
        int[] b = bbox(in, r, ox, oz);
        for (int[] o : boxes) if (overlap(b, o, 5)) return null;
        return fit(id, in, r, ox, oz, w[0], w[1], b, greenY - 8, greenY + 8, 10);
    }
}
