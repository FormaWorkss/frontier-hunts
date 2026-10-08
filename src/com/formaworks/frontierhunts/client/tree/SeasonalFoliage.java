package com.formaworks.frontierhunts.client.tree;

import com.formaworks.frontierhunts.season.FoliageSeason;
import com.formaworks.frontierhunts.season.client.SeasonView;
import com.formaworks.frontierhunts.season.client.SeasonalLeafSprites;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;

/**
 * [seasons] The seasons on the realistic trees: fall colour, leaf drop, bare winter crowns and spring leaf-out.
 *
 * <p>A grown tree is baked once in its canonical summer look ({@link TrunkModel#bakedCell}, float quads released
 * afterwards). The season is applied on top, per holder cell, while a chunk section is meshed: every foliage card
 * keeps or drops out, shrinks (bud-burst) and is recoloured by rewriting a copy of its baked vertex colours - no
 * regrowth, no rebake of the tree. Wood is never touched, so a bare crown shows its real limbs and twigs.
 * Both sides of a double-sided card share one decision (keyed by the card centre), and so do its near and far
 * level-of-detail copies.
 *
 * <p>Per tree: the tree's patch ({@link FoliageSeason#patch} of its anchor) picks its autumn colour and shifts its
 * timing (early and late trees). Per card: sun-exposed and high sprays turn and fall first, with a little noise.
 * Everything is drawn at the stage time {@link SeasonView#time()}; {@code SeasonalClient} rebuilds the sections
 * when the stage changes (a few per tick). High summer and deep winter are single stages (no rebuilds), and in
 * summer {@link #apply} returns the canonical cell unchanged.
 *
 * <p>API for other tree renderers (e.g. distant impostor cards): {@link #foliageKeep}, {@link #tint}.
 */
public final class SeasonalFoliage {
    private SeasonalFoliage() {}

    private static final Map<Object, FoliageSeason.Profile> PROFILES = new ConcurrentHashMap<>();
    private static final ThreadLocal<FoliageSeason.Look> LOOK = ThreadLocal.withInitial(FoliageSeason.Look::new);
    private static final ThreadLocal<float[]> RGB = ThreadLocal.withInitial(() -> new float[3]);
    /** Seasonal time offsets of single cards stay inside +-this (months). */
    private static final float CARD_SPREAD = 0.26F;

    static FoliageSeason.Profile profile(Object species) {
        if (!(species instanceof Block block)) return FoliageSeason.Profile.EVERGREEN;
        return PROFILES.computeIfAbsent(block, b -> FoliageSeason.profile(BuiltInRegistries.BLOCK.getKey((Block) b).getPath()));
    }

    /** Tree-level seasonal time (months) of a tree: the stage time plus the tree's own shift. */
    static float treeTime(TreeGrowth.Tree tree) {
        float time = SeasonView.time();
        return time + FoliageSeason.shift(FoliageSeason.patch(tree.anchorX, tree.anchorZ), time);
    }

    /**
     * The seasonal version of one holder cell {wood, foliage} of a grown tree. Never mutates the cached cell.
     */
    public static List<BakedQuad>[] apply(TreeGrowth.Tree tree, BlockPos pos, int lod, List<BakedQuad>[] cell) {
        return frost(tree, pos, lod, season(tree, pos, lod, cell));
    }

    private static List<BakedQuad>[] season(TreeGrowth.Tree tree, BlockPos pos, int lod, List<BakedQuad>[] cell) {
        List<BakedQuad> foliage = cell[1];
        if (foliage.isEmpty()) return cell;
        FoliageSeason.Profile profile = profile(tree.foliageSpecies);
        if (!profile.deciduous()) return cell;
        SeasonView.track(pos.getX(), pos.getY(), pos.getZ());
        if (!SeasonView.on()) return cell;
        // one derivation per cell and stage: later rebuilds of the section (level of detail, block updates) reuse it
        int stage = SeasonView.key();
        Object baked = tree.baked;
        Cache cache = tree.seasonal instanceof Cache c && c.stage == stage && c.baked == baked ? c : null;
        if (cache == null) {
            cache = new Cache(stage, baked);
            tree.seasonal = cache;
        }
        // [integration] key by all three levels (NEAR 0, FAR 1, [perf] IMPOSTOR 2): lod & 1 made IMPOSTOR share NEAR's
        // entry, and two cells with the same (empty) wood list would have been served each other's foliage. The x bits
        // shifted out are irrelevant here: the cache holds the cells of one tree.
        long key = TreeGrowth.pack(pos.getX(), pos.getY(), pos.getZ()) << 2 | (lod & 3);
        List<BakedQuad>[] done = cache.cells.get(key);
        if (done != null && done[0] == cell[0]) return done;
        List<BakedQuad>[] result = lod == TreeLod.IMPOSTOR ? deriveImpostor(tree, pos, profile, cell) : derive(tree, pos, profile, cell);
        if (result != cell) com.formaworks.frontierhunts.perf.client.PerfStats.inc(com.formaworks.frontierhunts.perf.client.PerfStats.SEASON_CELLS); // [perf2]
        if (result != cell) cache.cells.put(key, result);
        return result;
    }

    /** Seasonal cells of one tree for one stage (dropped with the tree, replaced at the next stage). */
    private static final class Cache {
        final int stage;
        final Object baked;
        final ConcurrentHashMap<Long, List<BakedQuad>[]> cells = new ConcurrentHashMap<>();

        Cache(int stage, Object baked) {
            this.stage = stage;
            this.baked = baked;
        }
    }

    /**
     * [integration] perf's impostor level: a handful of crossed cards stand for the whole crown, so cards cannot drop out
     * one by one like the near sprays (the three crossed planes share one centre and would all go together, and one
     * surviving card would draw a full crown). The whole impostor crown shows while the tree keeps enough foliage
     * ({@link #foliageKeep}, a per-tree threshold of 0.1-0.4 so a distant forest thins out tree by tree) and is dropped
     * otherwise: bare deciduous impostors in winter, only their bark prism left. Visible cards take the same per-card
     * seasonal colour as the near geometry (grey-variant swap for painted leaves). Evergreens never get here.
     */
    @SuppressWarnings("unchecked")
    private static List<BakedQuad>[] deriveImpostor(TreeGrowth.Tree tree, BlockPos pos, FoliageSeason.Profile profile, List<BakedQuad>[] cell) {
        float keep = foliageKeep(tree);
        float threshold = 0.1F + 0.3F * FoliageSeason.unit(FoliageSeason.mix(FoliageSeason.hash((int) tree.anchorX, 7, (int) tree.anchorZ)));
        if (keep < threshold) return new List[]{cell[0], List.of()};
        return derive(tree, pos, profile, cell, true);
    }

    private static List<BakedQuad>[] derive(TreeGrowth.Tree tree, BlockPos pos, FoliageSeason.Profile profile, List<BakedQuad>[] cell) {
        return derive(tree, pos, profile, cell, false);
    }

    @SuppressWarnings("unchecked")
    private static List<BakedQuad>[] derive(TreeGrowth.Tree tree, BlockPos pos, FoliageSeason.Profile profile, List<BakedQuad>[] cell, boolean impostor) {
        List<BakedQuad> foliage = cell[1];
        int patch = FoliageSeason.patch(tree.anchorX, tree.anchorZ);
        float t = treeTime(tree), hue = FoliageSeason.hue(patch);
        FoliageSeason.Look look = LOOK.get();
        // whole-cell shortcuts: summer (canonical) and bare winter
        boolean early = FoliageSeason.look(profile, t - CARD_SPREAD, hue, 0.5F, look).identity;
        boolean late = FoliageSeason.look(profile, t + CARD_SPREAD, hue, 0.5F, look).identity;
        if (early && late && FoliageSeason.look(profile, t, hue, 0.5F, look).identity) return cell;
        if (!impostor && bare(profile, t - CARD_SPREAD, look) && bare(profile, t + CARD_SPREAD, look)) return new List[]{cell[0], List.of()};

        TextureAtlasSprite sprite = foliage.get(0).getSprite();
        float[] green = SeasonalLeafSprites.green(sprite);
        TextureAtlasSprite grey = green == null ? null : SeasonalLeafSprites.variant(sprite, SeasonalLeafSprites.GREY);
        if (grey == null) green = null;
        int ref = TrunkModel.bakedTint(tree);
        float rr = ref == -1 ? 1 : Math.max(0.05F, (ref >> 16 & 255) / 255F), rg = ref == -1 ? 1 : Math.max(0.05F, (ref >> 8 & 255) / 255F),
            rb = ref == -1 ? 1 : Math.max(0.05F, (ref & 255) / 255F);
        int anchorY = (int) (tree.anchor << 52 >> 52);
        float[] c = RGB.get();
        List<BakedQuad> out = new ArrayList<>(foliage.size());
        for (BakedQuad q : foliage) {
            int[] v = q.getVertices();
            int stride = v.length / 4;
            float cx = 0, cy = 0, cz = 0;
            for (int i = 0; i < 4; i++) {
                cx += Float.intBitsToFloat(v[i * stride]);
                cy += Float.intBitsToFloat(v[i * stride + 1]);
                cz += Float.intBitsToFloat(v[i * stride + 2]);
            }
            cx *= 0.25F; cy *= 0.25F; cz *= 0.25F;
            int h = FoliageSeason.hash(Math.round((pos.getX() + cx) * 64), Math.round((pos.getY() + cy) * 64), Math.round((pos.getZ() + cz) * 64));
            float exposure = ((v[6] >>> 20) & 15) / 15F;
            float height = Math.max(0, Math.min(1, (pos.getY() + cy - anchorY) / 12F));
            float tq = t + 0.16F * (exposure - 0.7F) + 0.08F * (height - 0.5F) + 0.10F * (FoliageSeason.unit(h) - 0.5F);
            FoliageSeason.look(profile, tq, hue, FoliageSeason.unit(FoliageSeason.mix(h)), look);
            if (!impostor && FoliageSeason.unit(FoliageSeason.mix(h ^ 0x5A5A5A)) >= look.density) continue; // this card has fallen / not out yet
            if (look.identity) {
                out.add(q);
                continue;
            }
            float fr, fg, fb;
            TextureAtlasSprite to = null;
            if (look.blossom) {
                fr = fg = fb = 1; // canonical (blossom) colours, only the size changes
            } else if (green != null) {
                look.compose(green[0], green[1], green[2], c); // painted texture: grey variant x seasonal colour
                fr = c[0]; fg = c[1]; fb = c[2];
                to = grey;
            } else {
                look.compose(rr, rg, rb, c); // greyscale texture: swap its baked tint for the seasonal one
                fr = c[0] / rr; fg = c[1] / rg; fb = c[2] / rb;
            }
            // impostor cards keep their size: a crown sliced into stacked cards must not open gaps between the slices
            out.add(derive(q, v, stride, cx, cy, cz, impostor ? 1.0F : look.size, fr, fg, fb, to));
        }
        return new List[]{cell[0], out};
    }

    // ------------------------------------------------------------------ [1.2.0, 1.2.2] snow on the crown
    /** snowy cells of one tree at one snow level, and how snowy the tree is (looked at again now and then) */
    private static final class FrostCache {
        final int level;
        final long checked;
        final ConcurrentHashMap<Long, Object[]> cells = new ConcurrentHashMap<>();

        FrostCache(int level, long checked) {
            this.level = level;
            this.checked = checked;
        }
    }

    private static final net.minecraft.resources.ResourceLocation SNOW = com.formaworks.frontierhunts.FrontierHunts.id("block/tree_snow");

    /**
     * [1.2.2] How snowy a tree is, 0..1: as snowy as the ground around it (snow lying under and around the crown), or
     * wherever it's cold enough to snow at the crown. The ground is the reliable sign: the trees always match the snow
     * you see on the forest floor.
     */
    static float snowiness(TreeGrowth.Tree tree, BlockPos crown) {
        float cold = com.formaworks.frontierhunts.season.client.FrostColors.frost(null, crown);
        net.minecraft.client.multiplayer.ClientLevel world = net.minecraft.client.Minecraft.getInstance().level;
        if (world == null) return cold;
        int snowy = 0, seen = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int ax = (int) Math.floor(tree.anchorX), az = (int) Math.floor(tree.anchorZ);
        for (int ring = 0; ring < 2; ring++) {
            int r = ring == 0 ? 3 : 6;
            for (int k = 0; k < 8; k++) {
                double ang = (k + ring * 0.5) * Math.PI / 4.0;
                int x = ax + (int) Math.round(Math.cos(ang) * r), z = az + (int) Math.round(Math.sin(ang) * r);
                if (!world.hasChunk(x >> 4, z >> 4)) continue;
                for (int y = crown.getY() + 2; y > crown.getY() - 48 && y > world.getMinBuildHeight(); y--) {
                    net.minecraft.world.level.block.state.BlockState st = world.getBlockState(m.set(x, y, z));
                    if (st.isAir() || st.is(net.minecraft.tags.BlockTags.LEAVES) || st.is(net.minecraft.tags.BlockTags.LOGS)) continue;
                    if (st.getBlock() instanceof net.minecraft.world.level.block.SnowLayerBlock
                        && !world.getBlockState(m.set(x, y - 1, z)).is(net.minecraft.tags.BlockTags.LEAVES)) {
                        snowy++;
                        seen++;
                        break;
                    }
                    if (st.is(net.minecraft.world.level.block.Blocks.SNOW_BLOCK) || st.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)) {
                        snowy++;
                        seen++;
                        break;
                    }
                    if (!st.getCollisionShape(world, m).isEmpty() || !st.getFluidState().isEmpty()) {
                        seen++;
                        break;
                    }
                }
            }
        }
        float ground = seen < 4 ? 0.0F : Math.min(1.0F, snowy / (float) seen * 1.4F);
        return Math.max(cold, ground);
    }

    /**
     * [1.2.0, 1.2.2] Snow on a crown, drawn on the tree itself: clumps of snow resting on every spray that faces the sky
     * ({@link TreeSnow}), and a cold frost over the rest of the crown. Never mutates the cell; one derivation per cell
     * and snow level.
     */
    @SuppressWarnings("unchecked")
    private static List<BakedQuad>[] frost(TreeGrowth.Tree tree, BlockPos pos, int lod, List<BakedQuad>[] cell) {
        List<BakedQuad> foliage = cell[1];
        if (foliage.isEmpty()) return cell;
        long now = System.nanoTime();
        FrostCache cache = tree.frost instanceof FrostCache c ? c : null;
        if (cache == null || now - cache.checked > 30_000_000_000L) {
            // one snow level per tree, looked at again every half a minute (when its sections are rebuilt)
            BlockPos crown = tree.foliagePosition != 0L ? BlockPos.of(tree.foliagePosition) : pos;
            int level = Math.round(snowiness(tree, crown) * 4.0F);
            if (cache == null || cache.level != level) {
                cache = new FrostCache(level, now);
            } else {
                FrostCache again = new FrostCache(level, now);
                again.cells.putAll(cache.cells);
                cache = again;
            }
            tree.frost = cache;
        }
        if (cache.level <= 0) return cell;
        long key = TreeGrowth.pack(pos.getX(), pos.getY(), pos.getZ()) << 2 | (lod & 3);
        Object[] done = cache.cells.get(key);
        if (done != null && done[0] == foliage) return new List[]{cell[0], (List<BakedQuad>) done[1]};
        float amount = cache.level / 4.0F;
        TextureAtlasSprite snow = net.minecraft.client.Minecraft.getInstance().getTextureAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS).apply(SNOW);
        boolean far = lod == TreeLod.IMPOSTOR || lod != 0;
        List<BakedQuad> out = new ArrayList<>(foliage.size() + foliage.size() / 3);
        float[] card = new float[20];
        for (BakedQuad q : foliage) {
            int[] v = q.getVertices();
            int stride = v.length / 4;
            TextureAtlasSprite from = q.getSprite();
            float cx = 0, cy = 0, cz = 0, ny = 0, sky = 0;
            for (int i = 0; i < 4; i++) {
                float x = Float.intBitsToFloat(v[i * stride]), y = Float.intBitsToFloat(v[i * stride + 1]), z = Float.intBitsToFloat(v[i * stride + 2]);
                cx += x;
                cy += y;
                cz += z;
                if (stride > 7) ny += (byte) (v[i * stride + 7] >> 8) / 127.0F;
                sky += ((v[i * stride + 6] >>> 20) & 15) / 15.0F;
                card[i * 5] = x;
                card[i * 5 + 1] = y;
                card[i * 5 + 2] = z;
                card[i * 5 + 3] = (Float.intBitsToFloat(v[i * stride + 4]) - from.getU0()) / Math.max(1.0E-6F, from.getU1() - from.getU0());
                card[i * 5 + 4] = (Float.intBitsToFloat(v[i * stride + 5]) - from.getV0()) / Math.max(1.0E-6F, from.getV1() - from.getV0());
            }
            cx *= 0.25F; cy *= 0.25F; cz *= 0.25F; ny *= 0.25F; sky *= 0.25F;
            // the frost on the needles themselves (stronger at a distance, where the clumps are too small to see)
            float f = TreeSnow.frost(amount, ny, sky);
            if (far) f = Math.max(f, 0.5F * amount);
            out.add(whiten(q, v, stride, f));
            int h = TreeSnow.hash(pos.getX() + cx, pos.getY() + cy, pos.getZ() + cz);
            if (snow != null && TreeSnow.unit(h) < TreeSnow.cover(amount, ny, sky)) {
                out.add(pad(q, v, stride, TreeSnow.pad(card, ny, sky, h), snow));
            }
        }
        cache.cells.put(key, new Object[]{foliage, out});
        return new List[]{cell[0], out};
    }

    /** a card with its colour taken toward cold white by {@code f} */
    private static BakedQuad whiten(BakedQuad q, int[] src, int stride, float f) {
        if (f <= 0.01F) return q;
        int[] v = src.clone();
        for (int i = 0; i < 4; i++) {
            int o = i * stride;
            int col = v[o + 3];
            int r = col & 255, g = col >> 8 & 255, b = col >> 16 & 255;
            // the card's own brightness (its light and shading) carried into the white
            float lum = Math.max(r, Math.max(g, b)) / 255.0F;
            float k = Math.min(1.0F, 0.55F + 0.45F * lum);
            r = Math.round(r + (225 * k - r) * f);
            g = Math.round(g + (232 * k - g) * f);
            b = Math.round(b + (245 * k - b) * f);
            v[o + 3] = col & 0xFF000000 | Math.min(255, b) << 16 | Math.min(255, g) << 8 | Math.min(255, r);
        }
        return new BakedQuad(v, q.getTintIndex(), q.getDirection(), q.getSprite(), q.isShade(), q.hasAmbientOcclusion());
    }

    /** a clump of snow on a card: the card's vertex layout and light, the clump's corners, colour and texture */
    private static BakedQuad pad(BakedQuad q, int[] src, int stride, TreeSnow.Pad p, TextureAtlasSprite snow) {
        int[] v = src.clone();
        int c = Math.min(255, Math.round(255 * p.shade));
        for (int i = 0; i < 4; i++) {
            int o = i * stride;
            v[o] = Float.floatToRawIntBits(p.xyz[i * 3]);
            v[o + 1] = Float.floatToRawIntBits(p.xyz[i * 3 + 1]);
            v[o + 2] = Float.floatToRawIntBits(p.xyz[i * 3 + 2]);
            v[o + 3] = 0xFF000000 | c << 16 | c << 8 | c;
            v[o + 4] = Float.floatToRawIntBits(snow.getU(p.uv[i * 2]));
            v[o + 5] = Float.floatToRawIntBits(snow.getV(p.uv[i * 2 + 1]));
            if (stride > 7) v[o + 7] = 0 | (127 << 8); // normal straight up
        }
        return new BakedQuad(v, -1, net.minecraft.core.Direction.UP, snow, q.isShade(), q.hasAmbientOcclusion());
    }

    private static boolean bare(FoliageSeason.Profile profile, float t, FoliageSeason.Look look) {
        FoliageSeason.look(profile, t, 0.5F, 0.5F, look);
        return look.density <= 0;
    }

    private static BakedQuad derive(BakedQuad q, int[] src, int stride, float cx, float cy, float cz, float size,
                                    float fr, float fg, float fb, TextureAtlasSprite to) {
        int[] v = src.clone();
        TextureAtlasSprite from = q.getSprite();
        for (int i = 0; i < 4; i++) {
            int o = i * stride;
            if (size < 1) {
                v[o] = Float.floatToRawIntBits(cx + (Float.intBitsToFloat(v[o]) - cx) * size);
                v[o + 1] = Float.floatToRawIntBits(cy + (Float.intBitsToFloat(v[o + 1]) - cy) * size);
                v[o + 2] = Float.floatToRawIntBits(cz + (Float.intBitsToFloat(v[o + 2]) - cz) * size);
            }
            int col = v[o + 3];
            int r = Math.min(255, Math.round((col & 255) * fr)), g = Math.min(255, Math.round((col >> 8 & 255) * fg)),
                b = Math.min(255, Math.round((col >> 16 & 255) * fb));
            v[o + 3] = col & 0xFF000000 | b << 16 | g << 8 | r;
            if (to != null) {
                float u = (Float.intBitsToFloat(v[o + 4]) - from.getU0()) / (from.getU1() - from.getU0());
                float w = (Float.intBitsToFloat(v[o + 5]) - from.getV0()) / (from.getV1() - from.getV0());
                v[o + 4] = Float.floatToRawIntBits(to.getU(u));
                v[o + 5] = Float.floatToRawIntBits(to.getV(w));
            }
        }
        return new BakedQuad(v, q.getTintIndex(), q.getDirection(), to != null ? to : from, q.isShade(), q.hasAmbientOcclusion());
    }

    // ------------------------------------------------------------------ API for other tree renderers
    /**
     * Share of the tree's foliage cards drawn right now (0 bare .. 1 full crown); multiply a distant card's coverage
     * (or skip it below ~0.1) with this. Seasons off or evergreen: 1.
     */
    public static float foliageKeep(TreeGrowth.Tree tree) {
        FoliageSeason.Profile profile = profile(tree.foliageSpecies);
        if (!profile.deciduous() || !SeasonView.on()) return 1;
        FoliageSeason.Look look = FoliageSeason.look(profile, treeTime(tree), FoliageSeason.hue(FoliageSeason.patch(tree.anchorX, tree.anchorZ)), 0.5F, LOOK.get());
        return look.density * (look.size < 1 ? look.size * look.size : 1);
    }

    /**
     * Tree-level seasonal colour multiplier {r, g, b} relative to the tree's canonical (summer) baked foliage colours -
     * multiply a distant card's vertex colours by it (channels may exceed 1; clamp after multiplying). For painted
     * textures (the mod's own leaves) this is only an approximation of the grey-variant swap the near geometry does.
     */
    public static float[] tint(TreeGrowth.Tree tree) {
        FoliageSeason.Profile profile = profile(tree.foliageSpecies);
        if (!profile.deciduous() || !SeasonView.on()) return new float[]{1, 1, 1};
        FoliageSeason.Look look = FoliageSeason.look(profile, treeTime(tree), FoliageSeason.hue(FoliageSeason.patch(tree.anchorX, tree.anchorZ)), 0.5F, LOOK.get());
        if (look.identity || look.blossom) return new float[]{1, 1, 1};
        int ref = TrunkModel.bakedTint(tree);
        float rr = ref == -1 ? 0.45F : Math.max(0.05F, (ref >> 16 & 255) / 255F), rg = ref == -1 ? 0.63F : Math.max(0.05F, (ref >> 8 & 255) / 255F),
            rb = ref == -1 ? 0.22F : Math.max(0.05F, (ref & 255) / 255F);
        float[] c = new float[3];
        look.compose(rr, rg, rb, c);
        return new float[]{c[0] / rr, c[1] / rg, c[2] / rb};
    }
}
