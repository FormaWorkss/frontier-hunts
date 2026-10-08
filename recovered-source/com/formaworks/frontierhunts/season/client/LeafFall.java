package com.formaworks.frontierhunts.season.client;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.season.FoliageSeason;
import com.formaworks.frontierhunts.season.SeasonState;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Leaves falling from deciduous crowns around the player in the fall. Each tick a few random columns around the
 * camera are sampled (bounded by the quality setting); where a natural deciduous crown tops the column and its tree is
 * dropping leaves, a leaf is released under the crown in that tree's own colour (same patch, timing and palette as
 * the tree), more on windy days. Late fall is the heaviest; a few dry oak leaves still tumble in winter.
 *
 * <p>Budget: PERFORMANCE 3 samples/tick, at most 50 leaves; BALANCED 6 / 140; CINEMATIC 12 / 320. Vanilla's
 * Particles option (Decreased / Minimal) scales it further. Work per tick is a few block lookups; no per-frame work.
 */
final class LeafFall {
    private LeafFall() {}

    private static final RandomSource RANDOM = RandomSource.create();
    private static final Map<Block, FoliageSeason.Profile> PROFILES = new ConcurrentHashMap<>();
    private static final FoliageSeason.Look LOOK = new FoliageSeason.Look();
    private static final float[] RGB = new float[3];
    private static final String[] SHAPES = {"leaf_maple", "leaf_oak", "leaf_oval", "leaf_narrow"};

    static void tick(Minecraft mc, ClientLevel level) {
        if (!SeasonView.on() || mc.isPaused() || !level.dimensionType().hasSkyLight() || mc.gameRenderer == null) return;
        HuntConfig.Quality quality;
        try {
            quality = HuntConfig.LEAF_FALL.get();
        } catch (RuntimeException e) {
            quality = HuntConfig.Quality.BALANCED;
        }
        int samples = quality == HuntConfig.Quality.PERFORMANCE ? 3 : quality == HuntConfig.Quality.CINEMATIC ? 12 : 6;
        int cap = quality == HuntConfig.Quality.PERFORMANCE ? 50 : quality == HuntConfig.Quality.CINEMATIC ? 320 : 140;
        float radius = quality == HuntConfig.Quality.PERFORMANCE ? 16 : quality == HuntConfig.Quality.CINEMATIC ? 30 : 22;
        ParticleStatus status = mc.options.particles().get();
        if (status == ParticleStatus.MINIMAL) { samples = Math.max(1, samples / 4); cap /= 5; }
        else if (status == ParticleStatus.DECREASED) { samples = Math.max(1, samples / 2); cap /= 2; }

        var camera = mc.gameRenderer.getMainCamera();
        if (!camera.isInitialized()) return;
        Vec3 at = camera.getPosition();
        // the mod's wind (m/s, from the server's hunt snapshot) in blocks per tick, gusting
        double we = 0, ws = 0;
        var state = com.formaworks.frontierhunts.client.FrontierClient.state;
        if (state != null) { we = state.windEast() / 20.0; ws = state.windSouth() / 20.0; }
        double t = level.getGameTime() / 20.0;
        double gust = 0.7 + 0.22 * Math.sin(t / 7.3) + 0.14 * Math.sin(t / 2.1 + 1.3);
        double windSpeed = Math.hypot(we, ws) * 20 * gust; // m/s
        float windy = (float) (0.55 + 0.45 * Math.min(1.0, windSpeed / 6.0));
        float year = (float) SeasonState.yearPos();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 0; i < samples && LeafParticle.LIVE.get() < cap; i++) {
            double x = at.x + (RANDOM.nextDouble() * 2 - 1) * radius, z = at.z + (RANDOM.nextDouble() * 2 - 1) * radius;
            int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
            if (!level.hasChunk(bx >> 4, bz >> 4)) continue;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
            BlockState crown = level.getBlockState(m.set(bx, top - 1, bz));
            if (!(crown.getBlock() instanceof LeavesBlock) || !SeasonalColors.natural(crown)) continue;
            FoliageSeason.Profile profile = PROFILES.computeIfAbsent(crown.getBlock(),
                b -> FoliageSeason.profile(BuiltInRegistries.BLOCK.getKey(b).getPath()));
            if (!profile.deciduous()) continue;
            int patch = FoliageSeason.patch(bx + 0.5, bz + 0.5);
            float tt = year + FoliageSeason.shift(patch, year);
            float intensity = FoliageSeason.leafFall(profile, tt);
            if (intensity <= 0 || RANDOM.nextFloat() >= intensity * 0.5F * windy) continue;
            // release from the underside of the crown
            int y = top - 1, floor = top - 16;
            while (y > floor && level.getBlockState(m.set(bx, y, bz)).getBlock() instanceof LeavesBlock) y--;
            if (y <= floor || !level.getBlockState(m.set(bx, y, bz)).getCollisionShape(level, m).isEmpty()) continue;
            spawn(mc, level, profile, x, y + 0.85 + RANDOM.nextDouble() * 0.1, z, tt, patch, we * 0.6 * gust, ws * 0.6 * gust);
        }
    }

    private static void spawn(Minecraft mc, ClientLevel level, FoliageSeason.Profile profile, double x, double y, double z, float t, int patch,
                              double windX, double windZ) {
        FoliageSeason.look(profile, t + 0.12F * (RANDOM.nextFloat() - 0.5F), FoliageSeason.hue(patch), RANDOM.nextFloat(), LOOK);
        if (LOOK.blossom) { RGB[0] = 0.95F; RGB[1] = 0.72F; RGB[2] = 0.78F; }
        else LOOK.compose(FoliageSeason.GREEN[0], FoliageSeason.GREEN[1], FoliageSeason.GREEN[2], RGB);
        // the sprite is light grey (~0.9): lift the colour a little so the leaf matches its crown
        float k = 1.08F * (0.9F + RANDOM.nextFloat() * 0.2F);
        TextureAtlasSprite sprite = sprite(shape(profile));
        if (sprite == null) return;
        float size = profile == FoliageSeason.Profile.LARCH ? 0.035F : profile == FoliageSeason.Profile.MAPLE ? 0.085F : 0.07F;
        size *= 0.85F + RANDOM.nextFloat() * 0.3F;
        mc.particleEngine.add(new LeafParticle(level, x, y, z, sprite, Math.min(1, RGB[0] * k), Math.min(1, RGB[1] * k), Math.min(1, RGB[2] * k),
            windX, windZ, size));
    }

    private static int shape(FoliageSeason.Profile p) {
        return switch (p) {
            case MAPLE -> 0;
            case OAK, DARK_OAK -> 1;
            case WILLOW, LARCH, ROWAN -> 3;
            default -> 2;
        };
    }

    private static final TextureAtlasSprite[] SPRITES = new TextureAtlasSprite[SHAPES.length];

    private static TextureAtlasSprite sprite(int shape) {
        if (SPRITES[shape] == null) {
            TextureAtlas atlas = Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("frontierhunts", "block/seasonal/" + SHAPES[shape]);
            TextureAtlasSprite s = atlas.getSprite(id);
            SPRITES[shape] = s.contents().name().equals(id) ? s : null;
        }
        return SPRITES[shape];
    }

    /** Forget cached sprites (resource reload). */
    static void reset() {
        java.util.Arrays.fill(SPRITES, null);
    }
}
