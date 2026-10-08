package com.formaworks.frontierstructures;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;

/**
 * [structures2] Terrain adaptation for the authored settlement templates (the alpine chunk generator has no Beardifier,
 * so vanilla terrain_adaptation does nothing there). Per column, deterministic and chunk-local:
 * <ul>
 * <li>footprint (every column the template occupies): natural terrain, plants and trees above the template's ground
 * level are cut away (no hill inside a room, no buried door), voids below are filled down to solid ground (no floating
 * foundations);</li>
 * <li>apron (a ring around the footprint): the natural ground top is eased toward the yard level with a smooth falloff,
 * so the building sits in a graded yard instead of on a cliff or in a pit; trees standing on re-graded columns go.</li>
 * </ul>
 * The natural ground height comes from the chunk generator (pure function of seed and position), never from neighbouring
 * chunks, so the result is the same whatever order chunks generate in.
 */
public final class TerrainFit {
    public static final int APRON = 7;
    private final Set<Long> footprint = new HashSet<>();
    private final Map<Long, Integer> lowest = new HashMap<>();
    private final Map<Long, Integer> dist = new HashMap<>();
    private final int groundY;
    /** modded blocks of the template (multi-part stations / tents may have block-entity-less secondary parts) */
    private final List<BlockPos> modded = new ArrayList<>();

    static long key(int x, int z) {return ((long)x << 32) ^ (z & 0xFFFFFFFFL);}

    /** @param groundY first air y of the yard (template y 2) */
    public TerrainFit(StructureTemplate template, BlockPos origin, StructurePlaceSettings settings, int groundY) {
        this.groundY = groundY;
        var data = template.save(new CompoundTag());
        var palette = data.getList("palette", 10);
        for (var entry : data.getList("blocks", 10)) {
            var b = (CompoundTag)entry;
            String name = palette.getCompound(b.getInt("state")).getString("Name");
            if (name.equals("minecraft:air") || name.equals("minecraft:structure_void") || name.equals("minecraft:structure_block")) continue;
            var p = b.getList("pos", 3);
            var w = origin.offset(StructureTemplate.calculateRelativePosition(settings, new BlockPos(p.getInt(0), p.getInt(1), p.getInt(2))));
            long k = key(w.getX(), w.getZ());
            if (name.startsWith("frontierhunts:")) modded.add(w.immutable());
            footprint.add(k);
            lowest.merge(k, w.getY(), Math::min);
        }
        // breadth-first distance (chessboard) from the footprint, out to the apron width
        ArrayDeque<long[]> q = new ArrayDeque<>();
        for (long k : footprint) {dist.put(k, 0); q.add(new long[]{k >> 32, (int)k, 0});}
        while (!q.isEmpty()) {
            long[] c = q.poll();
            if (c[2] >= APRON) continue;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                long n = key((int)c[0] + dx, (int)c[1] + dz);
                if (dist.putIfAbsent(n, (int)c[2] + 1) == null) q.add(new long[]{c[0] + dx, c[1] + dz, c[2] + 1});
            }
        }
    }

    public interface Natural {int floor(int x, int z); int surface(int x, int z);
        /** [integ] true on beach / sea-shore ground (no buildings there) */
        default boolean shore(int x, int z) {return false;}}

    static boolean terrain(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY)
            || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.POWDER_SNOW) || s.is(Blocks.PACKED_ICE) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.MOSSY_COBBLESTONE)
            || s.is(Blocks.CALCITE) || s.is(Blocks.TUFF) || s.is(Blocks.DIRT_PATH) || s.is(Blocks.MUD)
            || s.getBlock().builtInRegistryHolder().key().location().getNamespace().equals("frontierhunts") && s.isSolidRender(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    static boolean vegetation(BlockState s) {
        return s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.canBeReplaced() && s.getFluidState().isEmpty() && !s.isAir() || s.is(Blocks.SNOW)
            || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS);
    }

    /** runs before the template is placed into this chunk */
    public void apply(WorldGenLevel level, BoundingBox chunk, Natural nat) {
        var m = new BlockPos.MutableBlockPos();
        int maxY = level.getMaxBuildHeight() - 1;
        for (int x = chunk.minX(); x <= chunk.maxX(); x++) for (int z = chunk.minZ(); z <= chunk.maxZ(); z++) {
            Integer d = dist.get(key(x, z));
            if (d == null) continue;
            int natural = nat.floor(x, z);
            boolean wet = nat.surface(x, z) > natural;
            if (d == 0) {
                // cut: nothing natural above the yard level inside the building
                for (int y = groundY; y <= Math.min(maxY, Math.max(natural, groundY) + 24); y++) {
                    var s = level.getBlockState(m.set(x, y, z));
                    if (terrain(s) || vegetation(s)) level.setBlock(m, Blocks.AIR.defaultBlockState(), 2);
                }
                // fill: the lowest template block of the column rests on solid ground
                int low = lowest.getOrDefault(key(x, z), groundY);
                if (low <= groundY) {
                    BlockState top = Blocks.GRASS_BLOCK.defaultBlockState();
                    for (int y = Math.min(low, groundY) - 1, n = 0; y > level.getMinBuildHeight() && n < 40; y--, n++) {
                        var s = level.getBlockState(m.set(x, y, z));
                        if (s.isSolid() && !s.is(BlockTags.LEAVES) && !s.is(BlockTags.LOGS)) break;
                        level.setBlock(m, y == groundY - 1 ? top : Blocks.DIRT.defaultBlockState(), 2);
                    }
                }
                continue;
            }
            if (wet) continue; // never regrade shoreline or open water
            double t = 1.0 - (d - 0.5) / APRON;
            double w = t <= 0 ? 0 : t * t * (3 - 2 * t);
            int target = (int)Math.round(natural + (groundY - natural) * w);
            if (target == natural) continue;
            // the natural top block of this column (grass, podzol, snow ...) stays the top block after regrading
            BlockState surface = level.getBlockState(m.set(x, natural - 1, z));
            if (!terrain(surface)) surface = Blocks.GRASS_BLOCK.defaultBlockState();
            if (target < natural) {
                for (int y = target; y < natural; y++) level.setBlock(m.set(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(m.set(x, target - 1, z), surface, 2);
            } else {
                for (int y = natural - 1; y < target - 1; y++) level.setBlock(m.set(x, y, z), Blocks.DIRT.defaultBlockState(), 2);
                level.setBlock(m.set(x, target - 1, z), surface, 2);
            }
            // whatever stood on the old ground of a regraded column (a trunk, a bush) would now float or be buried
            int from = Math.min(target, natural);
            for (int y = from; y <= Math.min(maxY, Math.max(target, natural) + 30); y++) {
                var s = level.getBlockState(m.set(x, y, z));
                if (s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || vegetation(s)) level.setBlock(m, Blocks.AIR.defaultBlockState(), 2);
                else if (!s.isAir() && y > Math.max(target, natural) + 2) break;
            }
        }
    }

    public boolean covers(int x, int z) {return dist.containsKey(key(x, z));}

    /**
     * Runs after the template is placed. Secondary parts of multi-part blocks (the right half of a wide station ...) are
     * EntityBlocks without a block entity; a proto chunk still queues a placeholder for them, which logs "Tried to load a
     * block entity ... but failed" when the chunk is promoted. Drop those placeholders (same fix as the living world).
     */
    public void dropOrphanBlockEntities(WorldGenLevel level, BoundingBox chunk) {
        for (BlockPos p : modded) {
            if (!chunk.isInside(p)) continue;
            BlockState s = level.getBlockState(p);
            if (s.hasBlockEntity() && s.getBlock() instanceof net.minecraft.world.level.block.EntityBlock eb && eb.newBlockEntity(p, s) == null) {
                level.getChunk(p).removeBlockEntity(p);
            }
        }
    }
}
