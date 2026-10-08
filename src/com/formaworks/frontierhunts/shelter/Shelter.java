package com.formaworks.frontierhunts.shelter;

import com.formaworks.frontierhunts.camp.CampingTent;
import com.formaworks.frontierhunts.expedition.HubGroundBlind;
import com.formaworks.frontierhunts.expedition.TowerBlind;
import com.formaworks.frontierhunts.landscape.tent.CompactTent;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * [shelter] Live-world adapter of {@link ShelterScan}. Common code (no client classes): the client evaluates it at the
 * camera for the storm presentation ({@code shelter.client.ShelterClient}), the server at the player's eye for body
 * temperature ({@code survival.Thermal}). Same rules, same block classification, so both sides agree.
 *
 * Block classification is cached per {@link BlockState} (state-only facts: class, tags, open/closed, collision box).
 * Tents and blinds are canvas multi-blocks: inside them every cell is a part block with a partial shape and they let
 * sky light through, so they are recognised by class, not by shape or light.
 */
public final class Shelter {
    private static final Map<BlockState, Byte> KIND = new ConcurrentHashMap<>();

    private Shelter() {}

    /** Shelter around an eye position (any entity, any side). */
    public static ShelterScan.Result scan(Level level, double x, double y, double z, ShelterScan.Result out) {
        BlockPos eye = BlockPos.containing(x, y, z);
        int sky = level.dimensionType().hasSkyLight() ? level.getBrightness(LightLayer.SKY, eye) : 0;
        return ShelterScan.scan(new LevelGrid(level), eye.getX(), eye.getY(), eye.getZ(), sky, out);
    }

    /** Shelter of a player at {@code eye} (the camera on the client), including sleeping in a tent. */
    public static ShelterScan.Result scan(Player p, Vec3 eye, ShelterScan.Result out) {
        scan(p.level(), eye.x, eye.y, eye.z, out);
        if (p.isSleeping() && sleepingInTent(p)) {
            out.fabric = 1f;
            out.tent = true;
            ShelterScan.finish(out);
        }
        return out;
    }

    /** Sleeping in a compact tent, or in a bed / bedroll standing inside a camping or compact tent. */
    public static boolean sleepingInTent(Player p) {
        BlockPos bed = p.getSleepingPos().orElse(null);
        if (bed == null) return false;
        Level level = p.level();
        Block b = level.getBlockState(bed).getBlock();
        if (b instanceof CompactTent) return true;
        // a vanilla bed or the hide bedroll pitched inside a tent: any tent cell right above or around the head
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dy = 0; dy <= 2; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    byte k = classify(level.getBlockState(m.set(bed.getX() + dx, bed.getY() + dy, bed.getZ() + dz)));
                    if (k == ShelterScan.TENT || k == ShelterScan.TENT_OPEN) return true;
                }
            }
        }
        return false;
    }

    /** Shelter code of a block state (cached). */
    public static byte classify(BlockState st) {
        if (st.isAir()) return ShelterScan.AIR;
        Byte k = KIND.get(st);
        if (k == null) {
            k = compute(st);
            if (KIND.size() < 65536) KIND.put(st, k);
        }
        return k;
    }

    private static byte compute(BlockState st) {
        Block b = st.getBlock();
        boolean open = st.hasProperty(BlockStateProperties.OPEN) && st.getValue(BlockStateProperties.OPEN);
        if (b instanceof CompactTent || b instanceof CampingTent) return open ? ShelterScan.TENT_OPEN : ShelterScan.TENT;
        if (b instanceof HubGroundBlind || b instanceof TowerBlind) return open ? ShelterScan.BLIND_OPEN : ShelterScan.BLIND;
        if (b instanceof LiquidBlock) return ShelterScan.FLUID;
        if (st.is(BlockTags.LEAVES)) return ShelterScan.LEAVES;
        // an open door, trapdoor or gate is an opening, whatever is left of its frame
        if (open && (b instanceof DoorBlock || b instanceof TrapDoorBlock || b instanceof FenceGateBlock)) return ShelterScan.AIR;
        try {
            // anything with a body that reaches at least half a block up and is more than a post: walls, roofs, glass,
            // panes, fences, slabs, stairs, furniture. Carpets, single snow layers, lanterns and torches are not.
            VoxelShape shape = st.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            if (shape.isEmpty()) return ShelterScan.AIR;
            AABB bb = shape.bounds();
            if (bb.maxY < 0.45) return ShelterScan.AIR;
            double wx = bb.getXsize(), wz = bb.getZsize();
            if (wx * wz < 0.2 && Math.max(wx, wz) < 0.9) return ShelterScan.AIR;
            return ShelterScan.SOLID;
        } catch (RuntimeException ex) {
            return st.blocksMotion() ? ShelterScan.SOLID : ShelterScan.AIR; // the shape needs a real world: fall back to the flag
        }
    }

    /** Grid over a live level; unloaded chunks read as open air. */
    static final class LevelGrid implements ShelterScan.Grid {
        private final Level level;
        private final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

        LevelGrid(Level level) {
            this.level = level;
        }

        @Override
        public byte at(int x, int y, int z) {
            if (!level.hasChunk(x >> 4, z >> 4)) return ShelterScan.AIR;
            return classify(level.getBlockState(m.set(x, y, z)));
        }

        @Override
        public int top(int x, int z) {
            if (!level.hasChunk(x >> 4, z >> 4)) return Integer.MIN_VALUE;
            return level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        }
    }
}
