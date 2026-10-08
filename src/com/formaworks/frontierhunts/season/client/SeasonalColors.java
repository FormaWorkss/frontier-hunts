package com.formaworks.frontierhunts.season.client;

import com.formaworks.frontierhunts.season.FoliageSeason;
import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/**
 * Seasonal block colours: wraps (after every other registration) the colour handlers of all leaves and of the
 * grass/fern/vine blocks.
 *
 * <ul>
 * <li>Leaves (Minecraft-style cubes, the realistic pack's loose leaves): the fall/spring colour of the leaf block's
 * tree patch ({@link #leafLook}). Tint index {@link #SEASON_TINT} marks quads {@link SeasonalLeafModel} swapped to
 * a grey variant of a painted texture: they get the absolute seasonal colour.</li>
 * <li>Grass and ground plants: a subtle seasonal shift (fall tan/olive, winter dull straw, spring fresh) that keeps
 * each biome's brightness.</li>
 * </ul>
 * Player-placed (persistent) leaves and evergreens keep their colour. Realistic trees bake their colour into their
 * geometry from {@link #baseColor} (the colour without the season) and apply the season themselves.
 */
@EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class SeasonalColors {
    private SeasonalColors() {}

    /** Tint index of seasonal (grey-variant) leaf quads. */
    public static final int SEASON_TINT = 7;

    /** Wrapped handler -> the handler it replaced (for colours without the season). */
    private static final Map<Block, BlockColor> ORIGINAL = new IdentityHashMap<>();
    private static final ThreadLocal<float[]> RGB = ThreadLocal.withInitial(() -> new float[3]);

    /** [1.2.0] the temperature blend the frost reads (a level only blends resolvers registered here) */
    @SubscribeEvent
    public static void resolvers(RegisterColorHandlersEvent.ColorResolvers event) {
        event.register(FrostColors.TEMPERATURE);
        FrostColors.registered = true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void colors(RegisterColorHandlersEvent.Block event) {
        BlockColors colors = event.getBlockColors();
        Map<Block, BlockColor> registered = registered(colors);
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!(block instanceof LeavesBlock)) continue;
            BlockColor original = registered == null ? null : registered.get(block);
            FoliageSeason.Profile profile = FoliageSeason.profile(BuiltInRegistries.BLOCK.getKey(block).getPath());
            ORIGINAL.put(block, original);
            // [1.2.0] every tree frosts over where it snows (evergreens included); deciduous ones also turn with the season
            BlockColor seasonal = profile.deciduous() ? new LeafColor(block, original, profile) : original;
            if (seasonal == null) continue;
            event.register(new FrostColors.Frosted(seasonal), block);
        }
        for (Block block : grassBlocks()) {
            BlockColor original = registered == null ? null : registered.get(block);
            if (original == null) continue;
            ORIGINAL.put(block, original);
            event.register(new GrassColor(original), block);
        }
    }

    private static Block[] grassBlocks() {
        java.util.List<Block> list = new java.util.ArrayList<>(java.util.List.of(Blocks.GRASS_BLOCK, Blocks.SHORT_GRASS, Blocks.TALL_GRASS,
            Blocks.FERN, Blocks.LARGE_FERN, Blocks.VINE));
        for (String id : new String[]{"alpine_pasture", "alpine_turf", "alpine_overgrowth"}) {
            BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath("frontierhunts", id)).ifPresent(list::add);
        }
        return list.toArray(new Block[0]);
    }

    @SuppressWarnings("unchecked")
    private static Map<Block, BlockColor> registered(BlockColors colors) {
        try {
            Field f;
            try {
                f = BlockColors.class.getDeclaredField("blockColors");
            } catch (NoSuchFieldException e) {
                f = null;
                for (Field c : BlockColors.class.getDeclaredFields()) {
                    if (Map.class.isAssignableFrom(c.getType())) { f = c; break; } // the first Map is the handler map
                }
                if (f == null) throw e;
            }
            f.setAccessible(true);
            return (Map<Block, BlockColor>) f.get(colors);
        } catch (ReflectiveOperationException | RuntimeException e) {
            com.mojang.logging.LogUtils.getLogger().warn("Frontier seasons: cannot read block colour handlers; seasonal colours use defaults", e);
            return null;
        }
    }

    /** The colour a block would have without the season (realistic trees bake this, then apply the season). */
    public static int baseColor(BlockState state, BlockAndTintGetter level, BlockPos pos, int tint) {
        Block block = state.getBlock();
        if (ORIGINAL.containsKey(block)) {
            BlockColor original = ORIGINAL.get(block);
            return original == null ? -1 : original.getColor(state, level, pos, tint);
        }
        return Minecraft.getInstance().getBlockColors().getColor(state, level, pos, tint);
    }

    // ------------------------------------------------------------------ leaves
    /**
     * The seasonal look of a natural leaves block: its tree patch sets colour and timing, the block itself adds a
     * little jitter. Thread-local result, valid until the next call on this thread.
     */
    public static FoliageSeason.Look leafLook(FoliageSeason.Profile profile, int x, int y, int z) {
        LookMemo memo = MEMO.get();
        float time = SeasonView.time();
        // the colour handler runs once per quad: the same block asks several times in a row
        if (memo.valid && memo.x == x && memo.y == y && memo.z == z && memo.time == time && memo.profile == profile) return memo.look;
        int patch = FoliageSeason.patch(x + 0.5, z + 0.5);
        int h = FoliageSeason.hash(x, y, z);
        float t = time + FoliageSeason.shift(patch, time) + 0.10F * (FoliageSeason.unit(h) - 0.5F);
        FoliageSeason.look(profile, t, FoliageSeason.hue(patch), FoliageSeason.unit(FoliageSeason.mix(h)), memo.look);
        memo.x = x; memo.y = y; memo.z = z; memo.time = time; memo.profile = profile; memo.valid = true;
        return memo.look;
    }

    private static final class LookMemo {
        final FoliageSeason.Look look = new FoliageSeason.Look();
        int x, y, z;
        float time;
        FoliageSeason.Profile profile;
        boolean valid;
    }

    private static final ThreadLocal<LookMemo> MEMO = ThreadLocal.withInitial(LookMemo::new);

    static boolean natural(BlockState state) {
        return !state.hasProperty(LeavesBlock.PERSISTENT) || !state.getValue(LeavesBlock.PERSISTENT);
    }

    private record LeafColor(Block block, BlockColor original, FoliageSeason.Profile profile) implements BlockColor {
        @Override
        public int getColor(BlockState state, BlockAndTintGetter level, BlockPos pos, int tint) {
            boolean season = tint == SEASON_TINT;
            int base = this.original == null ? -1 : this.original.getColor(state, level, pos, season ? 0 : tint);
            if (level == null || pos == null) return base;
            SeasonView.track(pos.getX(), pos.getY(), pos.getZ());
            if (!SeasonView.on() || !natural(state)) return base;
            float[] green = SeasonalLeafSprites.blockGreen(this.block);
            if (!season && green != null) return base; // canonical painted quads: only drawn while unchanged
            FoliageSeason.Look look = leafLook(this.profile, pos.getX(), pos.getY(), pos.getZ());
            if (!season && look.identity) return base;
            float br = base == -1 ? 1 : (base >> 16 & 255) / 255F, bg = base == -1 ? 1 : (base >> 8 & 255) / 255F, bb = base == -1 ? 1 : (base & 255) / 255F;
            float[] c = RGB.get();
            if (season) {
                float[] g = green != null ? green : FoliageSeason.GREEN;
                if (look.blossom) { c[0] = g[0]; c[1] = g[1]; c[2] = g[2]; }
                else look.compose(g[0], g[1], g[2], c);
                // keep the handler's own light/shade noise (painted leaves are tinted near-white)
                return pack(c[0] * br, c[1] * bg, c[2] * bb);
            }
            look.compose(br, bg, bb, c);
            return pack(c[0], c[1], c[2]);
        }
    }

    // ------------------------------------------------------------------ grass
    private record GrassColor(BlockColor original) implements BlockColor {
        @Override
        public int getColor(BlockState state, BlockAndTintGetter level, BlockPos pos, int tint) {
            int base = this.original.getColor(state, level, pos, tint);
            if (level == null || pos == null || base == -1) return base;
            SeasonView.track(pos.getX(), pos.getY(), pos.getZ());
            if (!SeasonView.on()) return base;
            float w = SeasonView.grassWeight();
            if (w <= 0) return base;
            float r = (base >> 16 & 255) / 255F, g = (base >> 8 & 255) / 255F, b = (base & 255) / 255F;
            float[] t = SeasonView.grassTarget();
            // move toward the season's colour at the biome's own brightness
            float lb = 0.30F * r + 0.59F * g + 0.11F * b, lt = 0.30F * t[0] + 0.59F * t[1] + 0.11F * t[2];
            float k = lt > 0.01F ? lb / lt : 1;
            return pack(r + (t[0] * k - r) * w, g + (t[1] * k - g) * w, b + (t[2] * k - b) * w);
        }
    }

    static int pack(float r, float g, float b) {
        int R = Math.max(0, Math.min(255, Math.round(r * 255))), G = Math.max(0, Math.min(255, Math.round(g * 255))),
            B = Math.max(0, Math.min(255, Math.round(b * 255)));
        return R << 16 | G << 8 | B;
    }
}
