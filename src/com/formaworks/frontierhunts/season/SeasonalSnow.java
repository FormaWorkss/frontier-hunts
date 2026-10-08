package com.formaworks.frontierhunts.season;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Server side of seasonal snow, run from the vanilla precipitation sample (one random column per chunk every ~16
 * ticks, so it is naturally amortised over loaded chunks).
 *
 * <ul>
 * <li>Snow falls through bare deciduous crowns and settles on the forest floor (vanilla would stack it on top of
 * the leaves blocks, a white slab floating over bare branches). Conifer crowns still catch it.</li>
 * <li>While it snows, short grass and ferns are buried (replaced by the snow layer).</li>
 * <li>When a seasonal biome is warm again (spring), snow layers melt a layer at a time and lake/river ice turns back
 * to water; bare ground under melted snow sometimes sprouts grass again.</li>
 * </ul>
 * Vanilla snowy biomes are never melted (they are not seasonal), and nothing runs when seasons or seasonal snow are
 * off.
 */
public final class SeasonalSnow {
    private SeasonalSnow() {}

    private static final Map<Block, Boolean> DECIDUOUS = new ConcurrentHashMap<>();

    /** Natural leaves of a deciduous species (player-placed, persistent leaves keep the vanilla behaviour). */
    public static boolean deciduousCanopy(BlockState state) {
        Block block = state.getBlock();
        if (!(block instanceof LeavesBlock)) return false;
        if (state.hasProperty(LeavesBlock.PERSISTENT) && state.getValue(LeavesBlock.PERSISTENT)) return false;
        return DECIDUOUS.computeIfAbsent(block, b -> FoliageSeason.profile(BuiltInRegistries.BLOCK.getKey(b).getPath()).deciduous());
    }

    /**
     * [1.2.0] Leaves of any tree, evergreen too. A crown carries at most one thin layer of snow (the Minecraft look);
     * the Ultra look hides that layer and frosts the tree instead (client: SmoothSnowModel, SeasonalFoliage).
     */
    public static boolean naturalCanopy(BlockState state) {
        // the reserve's own trees are generated with persistent leaves (so they never decay), so every leaf counts
        return state.getBlock() instanceof LeavesBlock || state.is(net.minecraft.tags.BlockTags.LEAVES);
    }

    /** @return true when the column was handled here and vanilla must not run (snow under a crown). */
    public static boolean precipitation(ServerLevel level, BlockPos column) {
        if (!level.dimensionType().natural()) return false;
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
        BlockState below = level.getBlockState(top.below());
        if (naturalCanopy(below) && !deciduousCanopy(below)) {
            // [1.2.1] a crown carries one thin layer, never a pile (vanilla tops it up to one; older piles shrink to one).
            // The Ultra look draws no slab on a crown at all and frosts the tree instead (client).
            BlockState atTop = level.getBlockState(top);
            if (atTop.is(Blocks.SNOW) && atTop.getValue(SnowLayerBlock.LAYERS) > 1) {
                level.setBlockAndUpdate(top, atTop.setValue(SnowLayerBlock.LAYERS, 1));
                return true;
            }
            return false;
        }
        if (!SeasonState.snow()) return false;
        if (deciduousCanopy(below)) {
            // old snow resting on the crown (from before seasons) slides off
            BlockState atTop = level.getBlockState(top);
            if (atTop.is(Blocks.SNOW)) level.setBlockAndUpdate(top, Blocks.AIR.defaultBlockState());
            BlockPos ground = groundUnder(level, top.below());
            if (ground != null) settleOrMelt(level, ground);
            return true;
        }
        BlockState atTop = level.getBlockState(top);
        Biome biome = level.getBiome(top).value();
        if (level.isRaining() && buriable(atTop) && coldAt(biome, top) && canSettle(level, top)) {
            // bury the grass tuft; vanilla then treats the new layer as settled snow
            level.setBlockAndUpdate(top, Blocks.SNOW.defaultBlockState());
            return false;
        }
        if (level.isRaining() && coldAt(biome, top)) {
            // [1.1.0] snow piles up toward its depth here (drifts, ridges, shelter) instead of stopping at one layer
            if (level.getGameRules().getInt(GameRules.RULE_SNOW_ACCUMULATION_HEIGHT) > 0 && biome.shouldSnow(level, top)) {
                DeepSnow.accumulate(level, top, biome);
            }
            return false;
        }
        melt(level, top, biome);
        return false;
    }

    /** Snow may replace a buried plant here: dark enough (no torch nearby) and a full face below. */
    private static boolean canSettle(ServerLevel level, BlockPos pos) {
        return level.getGameRules().getInt(GameRules.RULE_SNOW_ACCUMULATION_HEIGHT) > 0
            && level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos) < 10
            && Blocks.SNOW.defaultBlockState().canSurvive(level, pos);
    }

    private static boolean coldAt(Biome biome, BlockPos pos) {
        return !biome.warmEnoughToRain(pos);
    }

    /** Short plants snow covers (not flowers, crops, saplings or the Frontier meadow sward). */
    private static boolean buriable(BlockState state) {
        return state.is(Blocks.SHORT_GRASS) || state.is(Blocks.FERN);
    }

    /** First open position on the ground below a deciduous crown (null if none within reach). */
    private static BlockPos groundUnder(ServerLevel level, BlockPos leaves) {
        BlockPos.MutableBlockPos m = leaves.mutable();
        int floor = Math.max(level.getMinBuildHeight(), leaves.getY() - 48);
        while (m.getY() > floor) {
            m.move(0, -1, 0);
            BlockState s = level.getBlockState(m);
            if (s.isAir() || s.getBlock() instanceof LeavesBlock || buriable(s) || s.is(Blocks.SNOW)) continue;
            if (!s.getFluidState().isEmpty()) return null; // a crown over water: nothing to settle on
            if (s.getCollisionShape(level, m).isEmpty() && s.canBeReplaced()) continue; // other ground cover
            return m.above().immutable();
        }
        return null;
    }

    private static void settleOrMelt(ServerLevel level, BlockPos pos) {
        Biome biome = level.getBiome(pos).value();
        BlockState state = level.getBlockState(pos);
        if (level.isRaining() && coldAt(biome, pos)) {
            int limit = level.getGameRules().getInt(GameRules.RULE_SNOW_ACCUMULATION_HEIGHT);
            if (limit <= 0) return;
            if (buriable(state) && canSettle(level, pos)) {
                level.setBlockAndUpdate(pos, Blocks.SNOW.defaultBlockState());
                return;
            }
            if (state.is(Blocks.SNOW) || level.getBlockState(pos.below()).is(Blocks.SNOW)) {
                DeepSnow.accumulate(level, pos, biome); // [1.1.0] piles up to the column's depth
            } else if (!biome.shouldSnow(level, pos)) {
                return;
            } else {
                level.setBlockAndUpdate(pos, Blocks.SNOW.defaultBlockState());
            }
            return;
        }
        melt(level, pos, biome);
    }

    /** Spring melt at a column's surface: one snow layer, or surface ice over water. */
    private static void melt(ServerLevel level, BlockPos pos, Biome biome) {
        if (!SeasonState.seasonal(biome) || coldAt(biome, pos)) return;
        if (!level.isDay() && level.random.nextInt(3) != 0) return; // slower at night
        if (DeepSnow.meltTop(level, pos)) return; // [1.1.0] deep snow thaws from the top of the pile
        BlockPos pile = DeepSnow.top(level, pos);
        if (pile != null) pos = pile;
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.SNOW)) {
            int layers = state.getValue(SnowLayerBlock.LAYERS);
            if (layers > 1) {
                level.setBlockAndUpdate(pos, state.setValue(SnowLayerBlock.LAYERS, layers - 1));
            } else {
                boolean grass = level.getBlockState(pos.below()).is(Blocks.GRASS_BLOCK) && level.random.nextInt(10) < 3;
                level.setBlockAndUpdate(pos, grass ? Blocks.SHORT_GRASS.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
            return;
        }
        BlockPos under = pos.below();
        if (level.getBlockState(under).is(Blocks.ICE) && level.getFluidState(under.below()).isSource()) {
            // lake/river ice (frozen on top of water); ice laid on dry ground by players is left alone
            level.setBlockAndUpdate(under, Blocks.WATER.defaultBlockState());
        }
    }
}
