package com.formaworks.frontierhunts.season.client;

import com.formaworks.frontierhunts.season.FoliageSeason;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.serialization.MapCodec;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterSpriteSourceTypesEvent;
import net.neoforged.neoforge.client.event.TextureAtlasStitchedEvent;

/**
 * Block-atlas sprite source ({@code frontierhunts:seasonal_leaves}, added to the block atlas by the mod's
 * atlases/blocks.json) that derives seasonal variants of the leaf textures actually loaded - vanilla, the mod's,
 * the realistic world pack's or any resource pack's. Nothing is shipped copied from those textures.
 *
 * <ul>
 * <li>{@code grey}: a luminance-only copy of a pre-coloured leaf texture (the mod's green maple/birch/aspen...
 * leaves, cherry blossom), so fall and spring colours can be tinted onto it like vanilla's greyscale leaves.
 * Its {@link #green} factor is recorded: grey x green = the original colour, so the swap is invisible.</li>
 * <li>{@code thin1..3}: the same texture with 65 / 35 / 15 % of its leaf clusters left (nested), for thinning
 * crowns of Minecraft-style (cube) leaves in late fall and bud-burst.</li>
 * </ul>
 * With the realistic world pack loaded only the fringe (tree spray) greys are needed; otherwise the cube and fringe
 * textures of deciduous leaves get greys and thin variants. Variants live at
 * {@code frontierhunts:block/seasonal/<namespace>/<path>/<kind>}.
 */
@EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class SeasonalLeafSprites implements SpriteSource {
    public static final MapCodec<SeasonalLeafSprites> CODEC = MapCodec.unit(SeasonalLeafSprites::new);
    public static final SpriteSourceType TYPE = new SpriteSourceType(CODEC);
    static final ResourceLocation ROUND_LIST = ResourceLocation.fromNamespaceAndPath("frontierhunts", "realistic_world/round_blocks.txt");
    public static final int GREY = 0, THIN1 = 1, THIN2 = 2, THIN3 = 3;
    private static final String[] KINDS = {"grey", "thin1", "thin2", "thin3"};
    /** Share of leaf clusters each thin variant keeps. */
    static final float[] THIN_KEEP = {1F, 0.65F, 0.35F, 0.15F};

    /** Original texture -> factor that turns its grey variant back into its colours. */
    private static final Map<ResourceLocation, float[]> GREEN = new ConcurrentHashMap<>();
    /** Leaves block -> green factor of its cube texture (for tint handlers of swapped quads). */
    private static final Map<Block, float[]> BLOCK_GREEN = new ConcurrentHashMap<>();
    /** Original sprite name -> resolved variant sprites (index by kind; null = none). */
    private static final Map<ResourceLocation, TextureAtlasSprite[]> RESOLVED = new ConcurrentHashMap<>();
    private static final TextureAtlasSprite[] NONE = new TextureAtlasSprite[4];

    @SubscribeEvent
    public static void register(RegisterSpriteSourceTypesEvent event) {
        event.register(ResourceLocation.fromNamespaceAndPath("frontierhunts", "seasonal_leaves"), TYPE);
    }

    @SubscribeEvent
    public static void stitched(TextureAtlasStitchedEvent event) {
        if (event.getAtlas().location().equals(TextureAtlas.LOCATION_BLOCKS)) {
            RESOLVED.clear();
            LeafFall.reset();
        }
    }

    @Override
    public SpriteSourceType type() {
        return TYPE;
    }

    public static ResourceLocation variantId(ResourceLocation texture, int kind) {
        return ResourceLocation.fromNamespaceAndPath("frontierhunts", "block/seasonal/" + texture.getNamespace() + "/" + texture.getPath() + "/" + KINDS[kind]);
    }

    /** The variant of an atlas sprite, or null when none was made for it. Any thread; cached per stitch. */
    public static TextureAtlasSprite variant(TextureAtlasSprite sprite, int kind) {
        if (sprite == null) return null;
        ResourceLocation name = sprite.contents().name();
        TextureAtlasSprite[] v = RESOLVED.get(name);
        if (v == null) {
            v = new TextureAtlasSprite[4];
            boolean any = false;
            try {
                var atlas = Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
                for (int k = 0; k < 4; k++) {
                    ResourceLocation id = variantId(name, k);
                    TextureAtlasSprite s = atlas.getSprite(id);
                    if (s != null && s.contents().name().equals(id)) { v[k] = s; any = true; }
                }
            } catch (RuntimeException e) {
                return null;
            }
            if (!any) v = NONE;
            RESOLVED.put(name, v);
        }
        return v[kind];
    }

    /** grey x green = original colours (null when the texture was not pre-coloured, i.e. already greyscale). */
    public static float[] green(TextureAtlasSprite original) {
        return original == null ? null : GREEN.get(original.contents().name());
    }

    public static float[] blockGreen(Block block) {
        return BLOCK_GREEN.get(block);
    }

    // ------------------------------------------------------------------ generation
    private record Target(ResourceLocation texture, Block block, boolean fringe) {}

    @Override
    public void run(ResourceManager manager, SpriteSource.Output output) {
        boolean realistic = manager.getResource(ROUND_LIST).isPresent();
        GREEN.clear();
        BLOCK_GREEN.clear();
        Map<ResourceLocation, Target> targets = new LinkedHashMap<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!(block instanceof LeavesBlock)) continue;
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (!FoliageSeason.profile(id.getPath()).deciduous()) continue;
            String[] fringes = {id.getNamespace() + ":block/" + id.getPath() + "_fringe", "frontierhunts:block/rw/" + id.getPath() + "_fringe",
                "frontierhunts:block/" + id.getPath() + "_fringe"};
            if (realistic) {
                for (String f : fringes) {
                    ResourceLocation t = ResourceLocation.parse(f);
                    if (exists(manager, t)) { targets.putIfAbsent(t, new Target(t, block, true)); break; }
                }
            } else {
                ResourceLocation cube = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "block/" + id.getPath());
                if (exists(manager, cube)) targets.putIfAbsent(cube, new Target(cube, block, false));
                for (String f : new String[]{fringes[0], fringes[2]}) {
                    ResourceLocation t = ResourceLocation.parse(f);
                    if (exists(manager, t)) { targets.putIfAbsent(t, new Target(t, block, true)); break; }
                }
            }
        }
        for (Target target : targets.values()) {
            Optional<Resource> res = manager.getResource(TEXTURE_ID_CONVERTER.idToFile(target.texture()));
            if (res.isEmpty()) continue;
            float[] stats;
            try (NativeImage img = read(res.get())) {
                stats = stats(img);
            } catch (Exception e) {
                continue;
            }
            boolean coloured = stats[3] > 0.30F; // mean saturation: greyscale (tinted) leaves ~0.1-0.2, painted ones 0.35+
            boolean grey = coloured;
            if (grey) {
                float[] g = {stats[0] / GREY_MEAN, stats[1] / GREY_MEAN, stats[2] / GREY_MEAN};
                GREEN.put(target.texture(), g);
                if (!target.fringe() || !BLOCK_GREEN.containsKey(target.block())) BLOCK_GREEN.put(target.block(), g);
                add(output, res.get(), target.texture(), GREY, true, 1F);
            }
            if (!realistic) {
                for (int k = THIN1; k <= THIN3; k++) add(output, res.get(), target.texture(), k, grey, THIN_KEEP[k]);
            }
        }
    }

    /** Mean luminance (0..1) of every grey variant. */
    static final float GREY_MEAN = 0.72F;

    private static boolean exists(ResourceManager manager, ResourceLocation texture) {
        return manager.getResource(TEXTURE_ID_CONVERTER.idToFile(texture)).isPresent();
    }

    private static NativeImage read(Resource resource) throws java.io.IOException {
        try (var in = resource.open()) {
            return NativeImage.read(in);
        }
    }

    /** {mean r, g, b of opaque pixels (0..1), mean saturation (max-min)/max}. */
    static float[] stats(NativeImage img) {
        int w = img.getWidth(), h = Math.min(img.getHeight(), img.getWidth());
        double r = 0, g = 0, b = 0, sat = 0;
        int n = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int abgr = img.getPixelRGBA(x, y);
                if ((abgr >>> 24) < 128) continue;
                int R = abgr & 255, G = abgr >> 8 & 255, B = abgr >> 16 & 255;
                int max = Math.max(R, Math.max(G, B)), min = Math.min(R, Math.min(G, B));
                r += R; g += G; b += B;
                sat += (max - min) / (double) Math.max(1, max);
                n++;
            }
        }
        if (n == 0) return new float[]{1, 1, 1, 0};
        return new float[]{(float) (r / n / 255), (float) (g / n / 255), (float) (b / n / 255), (float) (sat / n)};
    }

    private static void add(SpriteSource.Output output, Resource resource, ResourceLocation texture, int kind, boolean grey, float keep) {
        ResourceLocation id = variantId(texture, kind);
        output.add(id, loader -> {
            try (NativeImage src = read(resource)) {
                NativeImage out = derive(src, grey, keep);
                return new SpriteContents(id, new FrameSize(out.getWidth(), out.getHeight()), out, ResourceMetadata.EMPTY);
            } catch (Exception e) {
                com.mojang.logging.LogUtils.getLogger().warn("Frontier seasons: cannot derive {} from {}", KINDS[kind], texture, e);
                return null;
            }
        });
    }

    /** Grey and/or thinned copy (first animation frame only). */
    static NativeImage derive(NativeImage src, boolean grey, float keep) {
        int w = src.getWidth(), h = Math.min(src.getHeight(), w);
        NativeImage out = new NativeImage(w, h, true);
        double lumSum = 0;
        int n = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = src.getPixelRGBA(x, y);
                if ((p >>> 24) < 128) continue;
                lumSum += lum(p);
                n++;
            }
        }
        float scale = n == 0 ? 1F : (float) (GREY_MEAN * 255 / (lumSum / n));
        // nested cluster mask: value noise at leaf-cluster scale plus a little per-pixel grain; the kept share is
        // chosen by quantile so every level keeps exactly its share of the opaque pixels
        float[] noise = keep < 1F ? clusterNoise(w, h) : null;
        float threshold = keep < 1F ? quantile(noise, src, w, h, 1F - keep) : -1F;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = src.getPixelRGBA(x, y);
                int a = p >>> 24;
                if (noise != null && a > 0 && noise[y * w + x] < threshold) a = 0;
                int r = p & 255, g = p >> 8 & 255, b = p >> 16 & 255;
                if (grey) {
                    int v = Math.min(255, Math.round(lum(p) * scale));
                    r = g = b = v;
                }
                out.setPixelRGBA(x, y, a << 24 | b << 16 | g << 8 | r);
            }
        }
        return out;
    }

    private static float lum(int abgr) {
        return 0.30F * (abgr & 255) + 0.59F * (abgr >> 8 & 255) + 0.11F * (abgr >> 16 & 255);
    }

    private static float[] clusterNoise(int w, int h) {
        float[] n = new float[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float big = value(x / (float) w, y / (float) h, 5, 17), small = value(x / (float) w, y / (float) h, 13, 91);
                float grain = FoliageSeason.unit(FoliageSeason.hash(x, y, 5));
                n[y * w + x] = 0.62F * big + 0.34F * small + 0.04F * grain;
            }
        }
        return n;
    }

    /** Tileable smooth value noise over the unit square with {@code cells} cells across (textures repeat per block). */
    private static float value(float u, float v, int cells, int salt) {
        float x = u * cells, y = v * cells;
        int gx = (int) Math.floor(x), gy = (int) Math.floor(y);
        float fx = x - gx, fy = y - gy;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        float result = 0;
        for (int k = 0; k < 4; k++) {
            int dx = k & 1, dy = k >> 1;
            float wgt = (dx == 1 ? fx : 1 - fx) * (dy == 1 ? fy : 1 - fy);
            result += wgt * FoliageSeason.unit(FoliageSeason.hash(Math.floorMod(gx + dx, cells), Math.floorMod(gy + dy, cells), salt));
        }
        return result;
    }

    private static float quantile(float[] noise, NativeImage src, int w, int h, float q) {
        float[] vals = new float[w * h];
        int n = 0;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if ((src.getPixelRGBA(x, y) >>> 24) > 0) vals[n++] = noise[y * w + x];
        if (n == 0) return 0;
        java.util.Arrays.sort(vals, 0, n);
        return vals[Math.min(n - 1, Math.max(0, (int) (q * n)))];
    }

    /** The missing sprite check used by callers that resolve their own ids. */
    static boolean missing(TextureAtlasSprite s) {
        return s == null || s.contents().name().equals(MissingTextureAtlasSprite.getLocation());
    }
}
