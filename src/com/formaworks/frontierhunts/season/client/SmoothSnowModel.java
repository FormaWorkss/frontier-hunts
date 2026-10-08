package com.formaworks.frontierhunts.season.client;

import com.formaworks.frontierhunts.FrontierHunts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;

/**
 * [1.1.2] The Frontier snowpack in the chunk mesh. Every snow block is drawn from {@link SnowField}: one smooth, deep
 * blanket across blocks that buries steps, banks against walls, carries wind ripples and the prints of
 * {@link SnowPrints}, in a fine-grained 4 x 4 block snow texture ({@code frontierhunts:block/frontier_snow}, with
 * labPBR normal and specular maps so shader packs make it glint). Being part of the chunk mesh it works with Sodium and
 * every shader pack. With the snow style set to Minecraft (the Vanilla preset), or wherever the renderer gives no level
 * access, the plain vanilla model is drawn.
 */
public final class SmoothSnowModel extends BakedModelWrapper<BakedModel> {
   static final ModelProperty<List<BakedQuad>> QUADS = new ModelProperty<>();
   static final ModelProperty<Boolean> CROWN = new ModelProperty<>();
   static final ResourceLocation TEXTURE = FrontierHunts.id("block/frontier_snow");
   private final int layers;

   SmoothSnowModel(BakedModel original, int layers) {
      super(original);
      this.layers = layers;
   }

   @EventBusSubscriber(modid = FrontierHunts.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
   public static final class Registration {
      private Registration() {
      }

      @SubscribeEvent
      public static void wrap(ModelEvent.ModifyBakingResult event) {
         Map<ModelResourceLocation, BakedModel> models = event.getModels();
         for (BlockState s : Blocks.SNOW.getStateDefinition().getPossibleStates()) {
            ModelResourceLocation key = BlockModelShaper.stateToModelLocation(s);
            BakedModel m = models.get(key);
            if (m != null && !(m instanceof SmoothSnowModel)) {
               models.put(key, new SmoothSnowModel(m, s.getValue(SnowLayerBlock.LAYERS)));
            }
         }
      }
   }

   /** the world as {@link SnowField} sees it */
   static final class LevelProbe implements SnowField.Probe {
      final BlockAndTintGetter level;
      final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

      LevelProbe(BlockAndTintGetter level) {
         this.level = level;
      }

      @Override
      public int snow(int x, int y, int z) {
         BlockState s = this.level.getBlockState(this.m.set(x, y, z));
         return s.getBlock() instanceof SnowLayerBlock ? s.getValue(SnowLayerBlock.LAYERS) : 0;
      }

      @Override
      public boolean open(int x, int y, int z) {
         BlockState s = this.level.getBlockState(this.m.set(x, y, z));
         return s.isAir() || !(s.getBlock() instanceof SnowLayerBlock) && s.canBeReplaced() && s.getCollisionShape(this.level, this.m).isEmpty();
      }

      @Override
      public boolean full(int x, int y, int z) {
         BlockState s = this.level.getBlockState(this.m.set(x, y, z));
         return !s.isAir() && s.isCollisionShapeFullBlock(this.level, this.m);
      }
   }

   @Override
   public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
      data = this.originalModel.getModelData(level, pos, state, data);
      // [1.2.2] snow on a tree crown: a realistic tree carries its own snow on its sprays (never a slab or a tent);
      // a Minecraft-style crown wears a soft pillow of snow that rounds over its edges (both snow looks)
      if (onCrown(level, pos)) {
         try {
            if (grownTree(level, pos.below())) {
               return data.derive().with(QUADS, List.of()).with(CROWN, true).build();
            }
            List<SnowField.Quad> quads = CrownSnow.build(new CrownProbe(level), pos.getX(), pos.getY(), pos.getZ(), this.layers);
            List<BakedQuad> baked = bake(quads, false);
            SnowField.release(quads); // [perf3] baked: the snow quads go back to this thread's pool
            return data.derive().with(QUADS, baked).with(CROWN, true).build();
         } catch (RuntimeException e) {
            return data;
         }
      }
      if (!SnowLook.frontier()) {
         return data;
      }
      try {
         boolean lit = !SnowLook.shaders();
         List<SnowField.Quad> quads = SnowField.build(new LevelProbe(level), pos.getX(), pos.getY(), pos.getZ(), this.layers, SnowPrints.FIELD, lit);
         List<BakedQuad> baked = bake(quads, lit);
         SnowField.release(quads); // [perf3] baked: the snow quads go back to this thread's pool
         return data.derive().with(QUADS, baked).build();
      } catch (RuntimeException e) {
         return data;
      }
   }

   private static TextureAtlasSprite sprite() {
      return Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(TEXTURE);
   }

   static List<BakedQuad> bake(List<SnowField.Quad> quads, boolean lit) {
      TextureAtlasSprite sprite = sprite();
      List<BakedQuad> out = new ArrayList<>(quads.size());
      for (SnowField.Quad q : quads) {
         QuadBakingVertexConsumer b = com.formaworks.frontierhunts.perf.client.FastBake.begin(); // [perf3] reused per thread
         b.setSprite(sprite);
         b.setDirection(Direction.from3DDataValue(q.face == 0 ? 1 : q.face));
         // the light is in the colours when no shader pack runs: no second, per-face darkening
         b.setShade(!lit);
         b.setHasAmbientOcclusion(true);
         b.setTintIndex(-1);
         for (int v = 0; v < 4; v++) {
            b.addVertex(q.x[v], q.y[v], q.z[v]);
            int c = q.rgb[v];
            b.setColor(c >> 16 & 255, c >> 8 & 255, c & 255, 255);
            b.setUv(sprite.getU(q.u[v]), sprite.getV(q.v[v]));
            b.setUv2(0, 0);
            b.setNormal(q.nx[v], q.ny[v], q.nz[v]);
         }
         out.add(com.formaworks.frontierhunts.perf.client.FastBake.bake(b)); // [perf3]
      }
      return out;
   }

   /** [1.2.2] leaves drawn by a grown realistic tree (its sprays carry the snow), not a Minecraft-style cube */
   static boolean grownTree(BlockAndTintGetter level, BlockPos leaves) {
      BlockState s = level.getBlockState(leaves);
      if (!(Minecraft.getInstance().getBlockRenderer().getBlockModel(s) instanceof com.formaworks.frontierhunts.client.tree.LeafModel)) {
         return false;
      }
      return com.formaworks.frontierhunts.client.tree.LeafModel.grown(level, leaves);
   }

   /** the neighbours of a crown's snow */
   static final class CrownProbe implements CrownSnow.Probe {
      final BlockAndTintGetter level;
      final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

      CrownProbe(BlockAndTintGetter level) {
         this.level = level;
      }

      @Override
      public boolean snowyCrown(int x, int y, int z) {
         BlockState s = this.level.getBlockState(this.m.set(x, y, z));
         return s.getBlock() instanceof SnowLayerBlock && onCrown(this.level, this.m.immutable());
      }

      @Override
      public boolean filled(int x, int y, int z) {
         BlockState s = this.level.getBlockState(this.m.set(x, y, z));
         return !s.isAir() && (s.is(net.minecraft.tags.BlockTags.LEAVES) || s.isSolidRender(this.level, this.m));
      }
   }

   /** snow resting on leaves */
   static boolean onCrown(BlockAndTintGetter level, BlockPos pos) {
      try {
         return com.formaworks.frontierhunts.season.SeasonalSnow.naturalCanopy(level.getBlockState(pos.below()));
      } catch (RuntimeException e) {
         return false;
      }
   }

   @Override
   public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData data, RenderType type) {
      List<BakedQuad> own = data.get(QUADS);
      if (own == null && SnowLook.frontier() && rand != null) {
         // [1.2.1] renderers that skip the model data (Sodium): find the block from the render seed, hide a crown's slab
         BlockPos at = com.formaworks.frontierhunts.client.PassageField.posFor(rand);
         net.minecraft.client.multiplayer.ClientLevel lvl = Minecraft.getInstance().level;
         if (at != null && lvl != null && onCrown(lvl, at)) {
            return List.of();
         }
      }
      if (own != null && Boolean.TRUE.equals(data.get(CROWN))) {
         return side == null ? own : List.of();
      }
      if (own == null || side == Direction.DOWN) {
         return this.originalModel.getQuads(state, side, rand, data, type);
      }
      if (own.isEmpty()) {
         return List.of();
      }
      return side == null ? own : List.of();
   }

   @Override
   public TextureAtlasSprite getParticleIcon(ModelData data) {
      return this.originalModel.getParticleIcon(data);
   }
}
