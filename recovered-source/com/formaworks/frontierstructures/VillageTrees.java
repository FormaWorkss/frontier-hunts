package com.formaworks.frontierstructures;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.BitSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * [1.1.0] No half trees in villages.
 *
 * <p>Trees are planted chunk by chunk; a tree whose crown reached into a village that a neighbouring chunk cleared
 * later lost the part over the village and left floating logs and stair-stepped walls of leaves behind. Two fixes:
 * <ul>
 * <li><b>New land:</b> every village registers its plan the moment its structure start exists (or is loaded); the
 *     tree planter ({@code SettlementTreeSpace}) then refuses any tree whose trunk would stand within a crown's reach
 *     of ground the village touches (footprints, roads, green and the apron), so no tree is ever cut.</li>
 * <li><b>Villages already in a world:</b> once all the land around a village is loaded, one pass finds the trees in
 *     and around it - logs joined into trees, leaves given to the nearest tree - and removes, whole, every tree that is
 *     floating (cut off from the ground) or that reaches into the village's ground, plus loose leaves that belong to no
 *     tree. Building timber and decorations (no natural leaves, standing on something) are never touched. Each village
 *     is tidied once (remembered in the world's data).</li>
 * </ul>
 */
public final class VillageTrees {
    private VillageTrees() {}

    static final int CROWN = 8;
    record Site(BoundingBox box, CompoundTag plan) {}
    /** the village's influence, and the trunk-free reserve around it, as column masks */
    record Mask(int x0, int z0, int w, int h, BitSet touch, BitSet reserve, BitSet foot) {
        boolean in(BitSet b, int x, int z) {
            int i = x - x0, j = z - z0;
            return i >= 0 && j >= 0 && i < w && j < h && b.get(i + j * w);
        }
    }

    static final Map<Long, Site> SITES = new ConcurrentHashMap<>();
    static final Map<Long, Mask> MASKS = new ConcurrentHashMap<>();

    static long key(BoundingBox b) {return TerrainFit.key(b.minX(), b.minZ());}

    /** called when a village's grounds piece is created or loaded */
    static void register(BoundingBox box, CompoundTag plan) {
        SITES.putIfAbsent(key(box), new Site(box, plan));
    }

    static Mask mask(Site s, StructureTemplateManager tm, TerrainFit.Natural nat) {
        return MASKS.computeIfAbsent(key(s.box), k -> {
            VillagePlan plan = VillagePlan.load(s.plan);
            VillageGround g = new VillageGround(plan, VillagePlan.templates(tm));
            int pad = CROWN + 2;
            int x0 = s.box.minX() - pad, z0 = s.box.minZ() - pad, w = s.box.getXSpan() + 2 * pad, h = s.box.getZSpan() + 2 * pad;
            BitSet touch = new BitSet(w * h), foot = new BitSet(w * h);
            for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) {
                int x = x0 + i, z = z0 + j;
                if (x < s.box.minX() || x > s.box.maxX() || z < s.box.minZ() || z > s.box.maxZ()) continue;
                if (g.influences(x, z, nat)) touch.set(i + j * w);
                if (g.foot(x, z)) foot.set(i + j * w);
            }
            return new Mask(x0, z0, w, h, touch, dilate(touch, w, h, CROWN), foot);
        });
    }

    /** [1.1.3] the natural ground heights of the world's generator */
    static TerrainFit.Natural natural(ServerLevel server, net.minecraft.world.level.LevelHeightAccessor height) {
        var src = server.getChunkSource();
        return new Heights(src.getGenerator(), height, src.randomState());
    }

    /** square (Chebyshev) dilation of a column mask */
    static BitSet dilate(BitSet src, int w, int h, int r) {
        BitSet rows = new BitSet(w * h), out = new BitSet(w * h);
        for (int j = 0; j < h; j++) {
            int last = -1_000_000;
            for (int i = 0; i < w; i++) if (src.get(i + j * w)) last = i;
                else if (i - last <= r) rows.set(i + j * w);
            last = 1_000_000;
            for (int i = w - 1; i >= 0; i--) if (src.get(i + j * w)) {last = i; rows.set(i + j * w);}
                else if (last - i <= r) rows.set(i + j * w);
        }
        for (int i = 0; i < w; i++) {
            int last = -1_000_000;
            for (int j = 0; j < h; j++) if (rows.get(i + j * w)) last = j;
                else if (j - last <= r) out.set(i + j * w);
            last = 1_000_000;
            for (int j = h - 1; j >= 0; j--) if (rows.get(i + j * w)) {last = j; out.set(i + j * w);}
                else if (last - j <= r) out.set(i + j * w);
        }
        return out;
    }

    /** worldgen: is this spot kept free of tree trunks for a village? (any thread) */
    public static boolean reserved(WorldGenLevel level, BlockPos origin) {
        if (SITES.isEmpty()) return false;
        int x = origin.getX(), z = origin.getZ();
        for (Site s : SITES.values()) {
            BoundingBox b = s.box;
            if (x < b.minX() - CROWN - 1 || x > b.maxX() + CROWN + 1 || z < b.minZ() - CROWN - 1 || z > b.maxZ() + CROWN + 1) continue;
            try {
                Mask m = mask(s, level.getLevel().getStructureManager(), natural(level.getLevel(), level));
                if (m.in(m.reserve, x, z)) return true;
            } catch (RuntimeException e) {
                return true; // no plan to go by: keep trees out of the village box rather than cut one later
            }
        }
        return false;
    }

    /** [1.1.0] no wild animals spawn in a village or within 24 blocks of its edge (cheap: the registered boxes) */
    public static boolean inhabited(net.minecraft.world.level.LevelAccessor level, BlockPos pos) {
        int x = pos.getX(), z = pos.getZ(), m = 24;
        for (Site s : SITES.values()) {
            BoundingBox b = s.box;
            if (x >= b.minX() - m && x <= b.maxX() + m && z >= b.minZ() - m && z <= b.maxZ() + m) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------- existing villages

    public static final class Ledger extends SavedData {
        final LongOpenHashSet done = new LongOpenHashSet();
        static Ledger load(CompoundTag t, HolderLookup.Provider p) {
            var d = new Ledger();
            for (long k : t.getLongArray("done")) d.done.add(k);
            return d;
        }
        static Ledger get(ServerLevel l) {
            return l.getDataStorage().computeIfAbsent(new Factory<>(Ledger::new, Ledger::load), "frontierstructures_village_trees2"); // [1.1.3] a fresh pass for every village
        }
        @Override public CompoundTag save(CompoundTag t, HolderLookup.Provider p) {
            t.put("done", new LongArrayTag(done.toLongArray()));
            return t;
        }
    }

    public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent e) {
        SITES.clear();
        MASKS.clear();
    }

    public static void tick(ServerTickEvent.Post e) {
        if (e.getServer().getTickCount() % 100 != 37 || SITES.isEmpty()) return;
        ServerLevel level = e.getServer().getLevel(Level.OVERWORLD);
        if (level == null) return;
        Ledger ledger = Ledger.get(level);
        for (var en : SITES.entrySet()) {
            if (ledger.done.contains(en.getKey().longValue())) continue;
            Site s = en.getValue();
            if (!loaded(level, s.box, CROWN + 20)) continue;
            try {
                int n = tidy(level, s, mask(s, level.getStructureManager(), natural(level, level)));
                if (n > 0) org.slf4j.LoggerFactory.getLogger("frontierstructures").info("[Frontier Structures] tidied {} cut tree blocks around the village at {} {}", n,
                    s.box.getCenter().getX(), s.box.getCenter().getZ());
            } catch (RuntimeException ex) {
                org.slf4j.LoggerFactory.getLogger("frontierstructures").warn("[Frontier Structures] village tree tidy failed", ex);
            }
            ledger.done.add(en.getKey().longValue());
            ledger.setDirty();
            return; // one village per pass
        }
    }

    static boolean loaded(ServerLevel l, BoundingBox b, int pad) {
        for (int cx = (b.minX() - pad) >> 4; cx <= (b.maxX() + pad) >> 4; cx++)
            for (int cz = (b.minZ() - pad) >> 4; cz <= (b.maxZ() + pad) >> 4; cz++)
                if (l.getChunkSource().getChunkNow(cx, cz) == null) return false;
        return true;
    }

    static boolean log(BlockState s) {return s.is(BlockTags.LOGS);}
    static boolean leaf(BlockState s) {return s.getBlock() instanceof LeavesBlock && !s.getValue(LeavesBlock.PERSISTENT);}

    /** removes cut, floating and village-crowding trees around one village; returns the blocks removed */
    static int tidy(ServerLevel level, Site s, Mask m) {
        BoundingBox b = s.box;
        int pad = CROWN + 20;                     // scanned
        int act = CROWN + 2;                      // acted on (whole trees only, never ones running off the scan)
        int x0 = b.minX() - pad, x1 = b.maxX() + pad, z0 = b.minZ() - pad, z1 = b.maxZ() + pad;
        int y0 = Math.max(level.getMinBuildHeight(), b.minY() + 18), y1 = Math.min(level.getMaxBuildHeight() - 1, b.maxY());
        var pos = new BlockPos.MutableBlockPos();
        LongArrayList logs = new LongArrayList();
        LongOpenHashSet logSet = new LongOpenHashSet(), leafSet = new LongOpenHashSet();
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            if (m.in(m.foot, x, z)) continue;
            // from the top of the column down to the first solid ground (or roof): trees live in between
            int top = Math.min(y1, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z));
            for (int y = top; y >= y0; y--) {
                BlockState st = level.getBlockState(pos.set(x, y, z));
                if (st.isAir()) continue;
                long k = BlockPos.asLong(x, y, z);
                if (log(st)) {logs.add(k); logSet.add(k);}
                else if (leaf(st)) leafSet.add(k);
                else if (st.isSolid() || !st.getFluidState().isEmpty()) break;
            }
        }
        // trees: logs joined through any of their 26 neighbours
        Long2IntOpenHashMap comp = new Long2IntOpenHashMap();
        comp.defaultReturnValue(-1);
        int trees = 0;
        LongArrayFIFOQueue q = new LongArrayFIFOQueue();
        for (int i = 0; i < logs.size(); i++) {
            long k = logs.getLong(i);
            if (comp.get(k) >= 0) continue;
            int id = trees++;
            comp.put(k, id);
            q.enqueue(k);
            while (!q.isEmpty()) {
                long c = q.dequeueLong();
                int cx = BlockPos.getX(c), cy = BlockPos.getY(c), cz = BlockPos.getZ(c);
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                    long n = BlockPos.asLong(cx + dx, cy + dy, cz + dz);
                    if (logSet.contains(n) && comp.get(n) < 0) {comp.put(n, id); q.enqueue(n);}
                }
            }
        }
        // leaves belong to the nearest tree, through the leaves, as far as a leaf can be from its log (6)
        Long2IntOpenHashMap leafOf = new Long2IntOpenHashMap();
        leafOf.defaultReturnValue(-1);
        Long2IntOpenHashMap dist = new Long2IntOpenHashMap();
        for (int i = 0; i < logs.size(); i++) q.enqueue(logs.getLong(i));
        for (int i = 0; i < logs.size(); i++) dist.put(logs.getLong(i), 0);
        int[][] six = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!q.isEmpty()) {
            long c = q.dequeueLong();
            int d = dist.get(c);
            if (d >= 6) continue;
            int id = logSet.contains(c) ? comp.get(c) : leafOf.get(c);
            int cx = BlockPos.getX(c), cy = BlockPos.getY(c), cz = BlockPos.getZ(c);
            for (int[] o : six) {
                long n = BlockPos.asLong(cx + o[0], cy + o[1], cz + o[2]);
                if (leafSet.contains(n) && !dist.containsKey(n)) {dist.put(n, d + 1); leafOf.put(n, id); q.enqueue(n);}
            }
        }
        // judge each tree
        boolean[] anchored = new boolean[trees], leafy = new boolean[trees], crowding = new boolean[trees], edge = new boolean[trees], border = new boolean[trees];
        for (int i = 0; i < logs.size(); i++) {
            long k = logs.getLong(i);
            int id = comp.get(k);
            int x = BlockPos.getX(k), y = BlockPos.getY(k), z = BlockPos.getZ(k);
            for (int[] o : six) {
                BlockState n = level.getBlockState(pos.set(x + o[0], y + o[1], z + o[2]));
                if (!n.isAir() && n.isSolid() && !log(n) && !(n.getBlock() instanceof LeavesBlock)) {anchored[id] = true; break;}
            }
            judge(m, x, z, id, b, act, crowding, edge);
            if (x <= x0 + 1 || x >= x1 - 1 || z <= z0 + 1 || z >= z1 - 1 || y <= y0 || y >= y1) border[id] = true;
        }
        for (var en : leafOf.long2IntEntrySet()) {
            int id = en.getIntValue();
            long k = en.getLongKey();
            leafy[id] = true;
            judge(m, BlockPos.getX(k), BlockPos.getZ(k), id, b, act, crowding, edge);
        }
        int removed = 0;
        for (int i = 0; i < logs.size(); i++) {
            long k = logs.getLong(i);
            int id = comp.get(k);
            // [1.1.3] a floating remnant goes wherever it is (unless it runs off the scan, where its ground may be);
            // a whole tree only when it crowds the village
            if (!anchored[id] && !border[id] || !edge[id] && leafy[id] && crowding[id]) removed += clear(level, pos, k);
        }
        for (long k : leafSet) {
            int id = leafOf.get(k);
            int x = BlockPos.getX(k), z = BlockPos.getZ(k);
            boolean inside = x > x0 + 7 && x < x1 - 7 && z > z0 + 7 && z < z1 - 7;
            if (id < 0 ? inside : !anchored[id] && !border[id] || !edge[id] && crowding[id]) removed += clear(level, pos, k);
        }
        return removed;
    }

    private static void judge(Mask m, int x, int z, int id, BoundingBox b, int act, boolean[] crowding, boolean[] edge) {
        if (x < b.minX() - act || x > b.maxX() + act || z < b.minZ() - act || z > b.maxZ() + act) edge[id] = true;
        for (int dx = -1; dx <= 1 && !crowding[id]; dx++) for (int dz = -1; dz <= 1; dz++) if (m.in(m.touch, x + dx, z + dz)) {crowding[id] = true; break;}
    }

    private static int clear(ServerLevel level, BlockPos.MutableBlockPos pos, long k) {
        pos.set(k);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        // snow that lay on it
        pos.move(0, 1, 0);
        if (level.getBlockState(pos).is(Blocks.SNOW)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        return 1;
    }
}
