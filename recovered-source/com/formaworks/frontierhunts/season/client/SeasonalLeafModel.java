package com.formaworks.frontierhunts.season.client;

import com.formaworks.frontierhunts.season.FoliageSeason;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;

/**
 * Minecraft-style (cube) deciduous leaves through the year, for the Vanilla look (and Custom setups with the Minecraft world) (the realistic world
 * pack draws its own trees - see client.tree.SeasonalFoliage). Late in the fall the leaf clusters thin out block by
 * block (dithered across the crown) until only twigs remain; in spring they return sparse, then full. Painted leaf
 * textures (the mod's maple/birch/aspen..., cherry) swap to their grey variant while their colour is seasonal, so the
 * colour handler can paint any autumn colour onto them ({@link SeasonalColors#SEASON_TINT}). Summer leaves are the
 * untouched original model.
 */
@EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class SeasonalLeafModel extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<Integer> STAGE = new ModelProperty<>();
    private static final ChunkRenderTypeSet CUTOUT = ChunkRenderTypeSet.of(RenderType.cutoutMipped());
    static final ResourceLocation TWIGS = ResourceLocation.fromNamespaceAndPath("frontierhunts", "block/seasonal/twigs");
    /** Leaf share of the full, thin1..3 and bare states. */
    private static final float[] KEEP = {1F, 0.65F, 0.35F, 0.15F, 0F};
    private static final int BARE = 4, SWAP = 8;

    private final Block block;
    private final FoliageSeason.Profile profile;

    private SeasonalLeafModel(BakedModel original, Block block, FoliageSeason.Profile profile) {
        super(original);
        this.block = block;
        this.profile = profile;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void wrap(ModelEvent.ModifyBakingResult event) {
        TRANSFORMED.clear();
        twigQuads = null;
        // the realistic world pack turns natural leaves into tree crowns (client.tree); only cube leaves are ours
        if (Minecraft.getInstance().getResourceManager().getResource(SeasonalLeafSprites.ROUND_LIST).isPresent()) return;
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!(block instanceof LeavesBlock)) continue;
            FoliageSeason.Profile profile = FoliageSeason.profile(BuiltInRegistries.BLOCK.getKey(block).getPath());
            if (!profile.deciduous()) continue;
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                if (!SeasonalColors.natural(state)) continue;
                ModelResourceLocation key = BlockModelShaper.stateToModelLocation(state);
                BakedModel model = models.get(key);
                if (model != null && !(model instanceof SeasonalLeafModel)) models.put(key, new SeasonalLeafModel(model, block, profile));
            }
        }
    }

    @SubscribeEvent
    public static void baked(ModelEvent.BakingCompleted event) {
        TRANSFORMED.clear(); // the new atlas has new UVs
        twigQuads = null;
    }

    // ------------------------------------------------------------------ per block
    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
        data = super.getModelData(level, pos, state, data);
        try {
            SeasonView.track(pos.getX(), pos.getY(), pos.getZ());
            if (!SeasonView.on()) return data;
            FoliageSeason.Look look = SeasonalColors.leafLook(this.profile, pos.getX(), pos.getY(), pos.getZ());
            if (look.identity) return data;
            // bud-burst sprays are small: a block of small sprays reads as a thinner one
            float d = look.density * (look.size < 1 ? 0.55F + 0.45F * look.size : 1F);
            int thin = thin(d, FoliageSeason.unit(FoliageSeason.hash(pos.getX() * 3 + 1, pos.getY(), pos.getZ() * 7)));
            boolean painted = SeasonalLeafSprites.blockGreen(this.block) != null;
            int stage = thin | (painted ? SWAP : 0);
            if (stage == 0) return data;
            return data.derive().with(STAGE, stage).build();
        } catch (RuntimeException e) {
            return data;
        }
    }

    /** Dithered choice between the two thin levels around density d. */
    static int thin(float d, float dither) {
        for (int i = 0; i < 4; i++) {
            if (d >= KEEP[i + 1]) {
                float frac = (KEEP[i] - d) / (KEEP[i] - KEEP[i + 1]);
                return dither < frac ? i + 1 : i;
            }
        }
        return BARE;
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData data, RenderType type) {
        Integer stage = data.get(STAGE);
        if (stage == null) return super.getQuads(state, side, rand, data, type);
        int thin = stage & 7;
        boolean painted = (stage & SWAP) != 0;
        if (thin == BARE) return side == null ? twigs() : List.of();
        List<BakedQuad> base = super.getQuads(state, side, rand, data, type);
        List<BakedQuad> out = new ArrayList<>(base.size() + 4);
        int kind = thin > 0 ? thin : SeasonalLeafSprites.GREY;
        for (BakedQuad q : base) out.add(transformed(q, kind, painted));
        if (side == null && thin >= 2) out.addAll(twigs());
        return out;
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        // thinned textures need cutout even with Fast leaves (which draw leaves opaque)
        return data.get(STAGE) != null ? CUTOUT : super.getRenderTypes(state, rand, data);
    }

    // ------------------------------------------------------------------ quads
    private static final Map<BakedQuad, BakedQuad[]> TRANSFORMED = new ConcurrentHashMap<>();

    private static BakedQuad transformed(BakedQuad q, int kind, boolean painted) {
        BakedQuad[] slot = TRANSFORMED.computeIfAbsent(q, k -> new BakedQuad[4]);
        BakedQuad t = slot[kind];
        if (t == null) {
            TextureAtlasSprite from = q.getSprite(), to = SeasonalLeafSprites.variant(from, kind);
            if (to == null && painted && kind != SeasonalLeafSprites.GREY) to = SeasonalLeafSprites.variant(from, SeasonalLeafSprites.GREY);
            if (to == null) {
                t = q; // no variant for this texture: keep it as it is
            } else {
                int[] v = q.getVertices().clone();
                int stride = v.length / 4;
                for (int i = 0; i < 4; i++) {
                    float u = (Float.intBitsToFloat(v[i * stride + 4]) - from.getU0()) / (from.getU1() - from.getU0());
                    float w = (Float.intBitsToFloat(v[i * stride + 5]) - from.getV0()) / (from.getV1() - from.getV0());
                    v[i * stride + 4] = Float.floatToRawIntBits(to.getU(u));
                    v[i * stride + 5] = Float.floatToRawIntBits(to.getV(w));
                }
                t = new BakedQuad(v, painted ? SeasonalColors.SEASON_TINT : q.getTintIndex(), q.getDirection(), to, q.isShade(), q.hasAmbientOcclusion());
            }
            slot[kind] = t;
        }
        return t;
    }

    private static volatile List<BakedQuad> twigQuads;

    /** Two crossed, double-sided twig planes through the block: a bare crown's fine branches. */
    static List<BakedQuad> twigs() {
        List<BakedQuad> quads = twigQuads;
        if (quads != null) return quads;
        TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS).getSprite(TWIGS);
        float[][][] planes = {
            {{0.05F, 0, 0.05F}, {0.95F, 0, 0.95F}, {0.95F, 1, 0.95F}, {0.05F, 1, 0.05F}},
            {{0.95F, 0, 0.05F}, {0.05F, 0, 0.95F}, {0.05F, 1, 0.95F}, {0.95F, 1, 0.05F}}};
        quads = new ArrayList<>(4);
        for (int p = 0; p < 2; p++) {
            float[][] c = planes[p];
            float ex = c[1][0] - c[0][0], ez = c[1][2] - c[0][2];
            for (int side = 0; side < 2; side++) {
                float nx = side == 0 ? -ez : ez, nz = side == 0 ? ex : -ex;
                float len = (float) Math.sqrt(nx * nx + nz * nz);
                nx /= len;
                nz /= len;
                QuadBakingVertexConsumer b = new QuadBakingVertexConsumer();
                b.setSprite(sprite);
                b.setDirection(Direction.getNearest(nx, 0, nz));
                b.setShade(true);
                b.setHasAmbientOcclusion(true);
                b.setTintIndex(-1);
                for (int i = 0; i < 4; i++) {
                    int k = side == 0 ? i : 3 - i;
                    float[] v = c[k];
                    b.addVertex(v[0], v[1], v[2]);
                    b.setColor(255, 255, 255, 255);
                    float u = k == 0 || k == 3 ? 0 : 1, w = k < 2 ? 1 : 0;
                    b.setUv(sprite.getU(u), sprite.getV(w));
                    b.setNormal(nx, 0, nz);
                }
                quads.add(b.bakeQuad());
            }
        }
        quads = List.copyOf(quads);
        twigQuads = quads;
        return quads;
    }
}
