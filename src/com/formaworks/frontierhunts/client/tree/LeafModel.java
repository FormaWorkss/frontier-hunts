package com.formaworks.frontierhunts.client.tree;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;

/**
 * Living trees draw their share of the branch-attached crown. Decorative leaves retain unit cubes.
 */
public final class LeafModel implements IDynamicBakedModel {
   static final ModelProperty<List<BakedQuad>> QUADS = new ModelProperty<>();
   private static final ModelProperty<Boolean> BLOCK = new ModelProperty<>();
   private static final ChunkRenderTypeSet CUTOUT = ChunkRenderTypeSet.of(RenderType.cutoutMipped());

   private final BakedModel base;
   private final TextureAtlasSprite leaf;
   private final TextureAtlasSprite spray;
   private final boolean conifer;

   public LeafModel(BakedModel base, TextureAtlasSprite leaf, TextureAtlasSprite spray, boolean conifer) {
      this.base = base;
      this.leaf = leaf;
      this.spray = spray;
      this.conifer = conifer;
   }

   /** [1.2.2] does a grown realistic tree own these leaves (then its sprays, not a cube, are drawn here) */
   public static boolean grown(BlockAndTintGetter level, BlockPos pos) {
      try {
         return TreeGrowth.lookup(LiveWorld.INSTANCE, pos.getX(), pos.getY(), pos.getZ(), level, TreeLod.growNeed(pos.getX(), pos.getZ()), true) != null;
      } catch (RuntimeException e) {
         return false;
      }
   }

   /** Needle foliage (boughs, needles, and leaves of needle trees). */
   static boolean isConifer(String path) {
      return TrunkModel.isConifer(path) || path.contains("bough") || path.contains("needle");
   }

   @Override
   public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
      try {
         List<BakedQuad> quads = new ArrayList<>();
         // the foliage of the grown tree hosted by this leaves block, at the tree's distance level
         // (wood is never hosted by leaves: shader wind would tear it)
         List<BakedQuad>[] cell = TrunkModel.treeCell(level, pos, null, this.spray);
         // [perf2] loose leaves (bushes, hedges, builds): the cube is baked once per state, not per block and rebuild
         if (cell == null) return data.derive().with(QUADS,this.cubes.computeIfAbsent(state,s->TrunkModel.cubeQuads(s,leaf,leaf,0))).with(BLOCK,true).build();
         if (cell[0].isEmpty()) {
            quads = cell[1];
         } else {
            quads.addAll(cell[0]);
            quads.addAll(cell[1]);
         }
         return data.derive().with(QUADS, quads).build();
      } catch (RuntimeException e) {
         if (!logged) {
            logged = true;
            com.mojang.logging.LogUtils.getLogger().warn("Frontier realistic leaves: could not shape the leaves at " + pos, e);
         }
         return data;
      }
   }

   private static volatile boolean logged;
   /** [perf2] Cube models of loose leaves per block state. */
   private final java.util.concurrent.ConcurrentHashMap<BlockState, List<BakedQuad>> cubes = new java.util.concurrent.ConcurrentHashMap<>();

   private BakedQuad bake(LeafShape.Quad q) {
      TextureAtlasSprite sprite = q.texture == 1 ? this.spray : this.leaf;
      float nx = 0, ny = 0, nz = 0;
      for (int k = 0; k < 4; k++) {
         nx += q.v[k * 8 + 5];
         ny += q.v[k * 8 + 6];
         nz += q.v[k * 8 + 7];
      }
      QuadBakingVertexConsumer b = com.formaworks.frontierhunts.perf.client.FastBake.begin(); // [perf3] reused per thread
      b.setSprite(sprite);
      b.setDirection(Direction.getNearest(nx, ny, nz));
      b.setShade(false);
      b.setHasAmbientOcclusion(true);
      b.setTintIndex(0);
      boolean shaders = com.formaworks.frontierhunts.client.ShaderState.on;
      for (int k = 0; k < 4; k++) {
         int o = k * 8;
         float vx = q.v[o + 5], vy = q.v[o + 6], vz = q.v[o + 7];
         // soft foliage shading from the rounded normals (light passes through leaves, so it is gentle);
         // with a shader pack on, the pack lights them
         float shade = shaders ? 1.0F : 0.78F + 0.22F * (0.6F * vx * vx + 0.8F * vz * vz + (vy > 0 ? 1.0F : 0.5F) * vy * vy);
         b.addVertex(q.v[o], q.v[o + 1], q.v[o + 2]);
         b.setColor(shade, shade, shade, 1.0F);
         // Rounded face positions can extend outside their original block. Those
         // positions are useful for geometry but must not sample neighbouring atlas
         // sprites (the coloured chevrons visible inside natural conifers).
         float u = Math.max(0.0001F, Math.min(0.9999F, q.v[o + 3]));
         float v = Math.max(0.0001F, Math.min(0.9999F, q.v[o + 4]));
         b.setUv(sprite.getU(u), sprite.getV(v));
         b.setNormal(vx, vy, vz);
      }
      return com.formaworks.frontierhunts.perf.client.FastBake.bake(b); // [perf3]
   }

   private static final class Probe implements LeafShape.Probe {
      private final BlockAndTintGetter level;
      private final BlockPos origin;
      private final boolean conifer;
      private final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

      Probe(BlockAndTintGetter level, BlockPos origin, boolean conifer) {
         this.level = level;
         this.origin = origin;
         this.conifer = conifer;
      }

      @Override
      public int kind(int dx, int dy, int dz) {
         this.m.set(this.origin.getX() + dx, this.origin.getY() + dy, this.origin.getZ() + dz);
         BlockState s = this.level.getBlockState(this.m);
         if (s.is(BlockTags.LEAVES)) {
            return TreeShape.LEAVES;
         }
         if (s.is(BlockTags.LOGS)) {
            return TreeShape.LOG;
         }
         if (s.isAir() || !s.getFluidState().isEmpty() && !s.isSolid()) {
            return TreeShape.AIR;
         }
         return s.isSolidRender(this.level, this.m) ? TreeShape.SOLID : TreeShape.AIR;
      }

      @Override
      public long hash(int dx, int dy, int dz) {
         long x = this.origin.getX() + dx, y = this.origin.getY() + dy, z = this.origin.getZ() + dz;
         return ((x & 0x3FFFFFF) << 38 | (z & 0x3FFFFFF) << 12 | (y & 0xFFF)) * 0x9E3779B97F4A7C15L;
      }

      @Override
      public boolean conifer() {
         return this.conifer;
      }
   }

   // ------------------------------------------------------------------ BakedModel
   @Override
   public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData data, RenderType type) {
      List<BakedQuad> q = data.get(QUADS);
      if (q != null) {
         if(Boolean.TRUE.equals(data.get(BLOCK))) {
            if(side==null || type!=null && type!=RenderType.cutoutMipped())return List.of();
            return TrunkModel.face(q, side); // [perf2]
         }
         return side == null && (type == null || type == RenderType.cutoutMipped()) ? q : List.of();
      }
      // leaves placed in builds keep the vanilla texture under the realistic pack
      return com.formaworks.frontierhunts.client.terrain.BlendSpriteSource.originalQuads(this.base.getQuads(state, side, rand, data, type));
   }

   @Override
   public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
      return data.get(QUADS) != null ? CUTOUT : this.base.getRenderTypes(state, rand, data);
   }

   @Override
   public boolean useAmbientOcclusion() {
      return true;
   }

   @Override public net.neoforged.neoforge.common.util.TriState useAmbientOcclusion(BlockState state,ModelData data,RenderType type) {
      return Boolean.TRUE.equals(data.get(BLOCK))?net.neoforged.neoforge.common.util.TriState.TRUE
         :net.neoforged.neoforge.common.util.TriState.FALSE;
   }

   @Override
   public boolean isGui3d() {
      return this.base.isGui3d();
   }

   @Override
   public boolean usesBlockLight() {
      return this.base.usesBlockLight();
   }

   @Override
   public boolean isCustomRenderer() {
      return false;
   }

   @Override
   public TextureAtlasSprite getParticleIcon() {
      return this.base.getParticleIcon();
   }

   @Override
   public ItemTransforms getTransforms() {
      return this.base.getTransforms();
   }

   @Override
   public ItemOverrides getOverrides() {
      return this.base.getOverrides();
   }

   static Minecraft mc() {
      return Minecraft.getInstance();
   }
}
