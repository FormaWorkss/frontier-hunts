package com.formaworks.frontierhunts.client.tree;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import java.util.Set;
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
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;

/**
 * Realistic-world trunk: wraps a log's round model and, in the world, replaces it with the
 * connected tree geometry from {@link TreeShape} (built once per chunk rebuild in getModelData).
 * Light is sampled from the open air beside each face and baked into the quads, because a log
 * block is opaque to light and its own position always reads dark.
 */
public final class TrunkModel implements IDynamicBakedModel {
   static final ModelProperty<List<BakedQuad>> QUADS = new ModelProperty<>();
   static final ModelProperty<List<BakedQuad>> LEAF_QUADS = new ModelProperty<>();
   private static final ChunkRenderTypeSet SOLID = ChunkRenderTypeSet.of(RenderType.solid());
   private static final ChunkRenderTypeSet SOLID_AND_LEAVES = ChunkRenderTypeSet.of(RenderType.solid(), RenderType.cutoutMipped());
   /** Leaf-spray texture for each leaves block (its fringe), filled when models bake. */
   static volatile Map<Block, TextureAtlasSprite> FRINGE = Map.of();

   private final BakedModel base;
   private final TextureAtlasSprite bark;
   private final TextureAtlasSprite end;
   private final Set<Block> logs;
   private final boolean conifer;

   public TrunkModel(BakedModel base, TextureAtlasSprite bark, TextureAtlasSprite end, Set<Block> logs, boolean conifer) {
      this.base = base;
      this.bark = bark;
      this.end = end;
      this.logs = logs;
      this.conifer = conifer;
   }

   /** Needle trees get conifer branching. */
   static boolean isConifer(String path) {
      path = path.replace("alpine", ""); // "alpine_maple" is no pine
      return path.contains("pine") || path.contains("spruce") || path.contains("cedar") || path.contains("fir")
         || path.contains("hemlock") || path.contains("larch") || path.contains("juniper") || path.contains("redwood");
   }

   // ------------------------------------------------------------------ world data
   @Override
   public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
      try {
         // a living tree is grown whole (TreeGrowth); this log draws its share of it
         List<BakedQuad>[] cell = treeCell(level, pos, this.bark, null);
         if (cell != null) {
            return data.derive().with(QUADS, cell[0]).with(LEAF_QUADS, cell[1]).build();
         }
         return data.derive().with(QUADS, buildingCube(state)).build();
      } catch (RuntimeException | StackOverflowError e) {
         if (!logged) {
            logged = true;
            com.mojang.logging.LogUtils.getLogger().warn("Frontier realistic trees: could not shape the log at " + pos, e);
         }
         return data;
      }
   }

   private static volatile boolean logged;

   /**
    * The baked quads the grown tree owning this log or leaves block draws there, at the tree's distance
    * level of detail: [0] wood, [1] foliage. Null when no grown tree owns the block (building timber,
    * stumps, loose leaves). A tree needed only at distance is grown with its distant geometry alone.
    */
   static List<BakedQuad>[] treeCell(BlockAndTintGetter level, BlockPos pos, TextureAtlasSprite barkFallback, TextureAtlasSprite sprayFallback) {
      int x = pos.getX(), y = pos.getY(), z = pos.getZ();
      // [perf2] any valid cached growth will do (the level actually drawn is checked below); the distance guess only
      // shapes a new growth. It used to drop cached trees whose sections did not need them regrown.
      TreeGrowth.Tree tree = TreeGrowth.lookup(LiveWorld.INSTANCE, x, y, z, level, TreeLod.growNeed(x, z), true);
      for (int attempt = 0; tree != null; attempt++) {
         int lod = TreeLod.treeLod(tree, x, y, z);
         TextureAtlasSprite bark = tree.species() == null ? barkFallback : BARK.getOrDefault(tree.species(), barkFallback);
         TextureAtlasSprite spray = tree.foliageSpecies == null ? sprayFallback : FRINGE.getOrDefault(tree.foliageSpecies, sprayFallback);
         List<BakedQuad>[] cell = bakedCell(tree, pos, bark, spray, lod);
         if (cell != null) return SeasonalFoliage.apply(tree, pos, lod, cell); // [seasons] fall colour, leaf drop, bare winter, spring leaf-out
         if (attempt > 0) return null;
         // baked for the other shader state with its float quads released, or grown for distance only
         // and now drawn near: grow it again
         TreeGrowth.forget(tree);
         com.formaworks.frontierhunts.perf.client.PerfStats.inc(com.formaworks.frontierhunts.perf.client.PerfStats.TREES_REGROWN_LEVEL); // [perf2]
         tree = TreeGrowth.lookup(LiveWorld.INSTANCE, x, y, z, level, TreeLod.bits(lod) | TreeLod.growNeed(x, z)); // [perf] three levels
      }
      return null;
   }

   /**
    * Baked quads of one holder block of a grown tree at one level of detail: [0] wood (solid), [1]
    * foliage (cutout). A tree is baked once per growth, not on every chunk-section rebuild: a cell is
    * baked at every level the growth holds at once (quads both levels share are baked once), so a
    * section that switches level reuses them. Each cell's float quads are released once baked, so a
    * cached tree holds one copy of its geometry. Returns null when the cell must be grown again: it
    * must be re-baked (shader switch) but its float quads were released, or the growth lacks the level.
    */
   @SuppressWarnings("unchecked")
   static List<BakedQuad>[] bakedCell(TreeGrowth.Tree tree, BlockPos pos, TextureAtlasSprite bark, TextureAtlasSprite spray, int lod) {
      if (!tree.has(TreeLod.bits(lod))) return null; // [perf] three levels
      boolean shaders = com.formaworks.frontierhunts.client.ShaderState.on;
      BakedTree baked = (BakedTree)tree.baked;
      int generation = GENERATION;
      if (baked == null || baked.shaders != shaders || baked.generation != generation) {
         synchronized (tree) {
            baked = (BakedTree)tree.baked;
            if (baked == null || baked.shaders != shaders || baked.generation != generation) {
               if (tree.released) return null;
               baked = new BakedTree(shaders, foliageTint(tree), generation);
               tree.baked = baked;
            }
         }
      }
      long key = TreeGrowth.pack(pos.getX(), pos.getY(), pos.getZ());
      List<BakedQuad>[][] levels = baked.cells.get(key);
      if (levels != null) return levels[lod];
      List<TreeShape.Quad> near = tree.quadsAt(pos.getX(), pos.getY(), pos.getZ());
      List<TreeShape.Quad> far = tree.farAt(pos.getX(), pos.getY(), pos.getZ());
      List<TreeShape.Quad> impostor = tree.impostorAt(pos.getX(), pos.getY(), pos.getZ()); // [perf]
      if (near.isEmpty() && far.isEmpty() && impostor.isEmpty()) {
         // most member blocks draw nothing; a cell whose quads were released must be regrown instead
         return tree.releasedCells.contains(key) ? null : EMPTY_CELL;
      }
      // one lock per cell, the same for every thread (a cell's lists never change identity)
      synchronized (near instanceof ArrayList ? near : far instanceof ArrayList ? far : impostor) {
         levels = baked.cells.get(key);
         if (levels != null) return levels[lod];
         if (near.isEmpty() && far.isEmpty() && impostor.isEmpty()) {
            return tree.releasedCells.contains(key) ? null : EMPTY_CELL;
         }
         java.util.IdentityHashMap<TreeShape.Quad, BakedQuad> shared = new java.util.IdentityHashMap<>();
         levels = new List[3][]; // [perf] NEAR, FAR, IMPOSTOR
         if (tree.has(TreeGrowth.LOD_NEAR)) levels[TreeLod.NEAR] = split(near, bark, spray, baked.tint, shared);
         if (tree.has(TreeGrowth.LOD_FAR)) levels[TreeLod.FAR] = split(far, bark, spray, baked.tint, shared);
         if (tree.has(TreeGrowth.LOD_IMPOSTOR)) levels[TreeLod.IMPOSTOR] = split(impostor, bark, spray, baked.tint, shared); // [perf]
         baked.cells.put(key, levels);
         tree.releasedCells.add(key);
         tree.released = true;
         TreeGrowth.recycle(near, far, impostor); // [perf3] the float quads feed this thread's next growths
         for (List<TreeShape.Quad> raw : List.of(near, far, impostor)) {
            if (raw instanceof ArrayList<TreeShape.Quad> list) {
               list.clear();
               list.trimToSize();
            }
         }
         return levels[lod];
      }
   }

   @SuppressWarnings("unchecked")
   private static List<BakedQuad>[] split(List<TreeShape.Quad> raw, TextureAtlasSprite bark, TextureAtlasSprite spray, int tint,
                                          java.util.IdentityHashMap<TreeShape.Quad, BakedQuad> shared) {
      if (raw.isEmpty()) return EMPTY_CELL;
      List<BakedQuad> wood = new ArrayList<>(), foliage = new ArrayList<>();
      for (TreeShape.Quad q : raw) {
         // [perf3] get/put instead of computeIfAbsent with a capturing lambda per quad (the same quads, baked once)
         if (q.texture == 2) {
            if (spray != null) {
               BakedQuad b = shared.get(q);
               if (b == null) shared.put(q, b = bakeWith(q, spray, tint));
               foliage.add(b);
            }
         } else if (bark != null) {
            BakedQuad b = shared.get(q);
            if (b == null) shared.put(q, b = bakeBark(q, bark));
            wood.add(b);
         }
      }
      return new List[]{wood.isEmpty() ? List.of() : wood, foliage.isEmpty() ? List.of() : foliage};
   }

   @SuppressWarnings("unchecked")
   private static final List<BakedQuad>[] EMPTY_CELL = new List[]{List.of(), List.of()};

   /** Bumped whenever block models (and so the atlas) are rebaked; baked trees from before are stale. */
   static volatile int GENERATION;

   private static final class BakedTree {
      final boolean shaders;
      final int tint;
      final int generation;
      /** Per holder block: per level of detail ({@link TreeLod#NEAR}, {@link TreeLod#FAR}, [perf] {@link TreeLod#IMPOSTOR}) [wood, foliage], null for a level not grown. */
      final java.util.concurrent.ConcurrentHashMap<Long, List<BakedQuad>[][]> cells = new java.util.concurrent.ConcurrentHashMap<>();

      BakedTree(boolean shaders, int tint, int generation) {
         this.shaders = shaders;
         this.tint = tint;
         this.generation = generation;
      }
   }

   static int foliageTint(TreeGrowth.Tree tree) {
      var world=Minecraft.getInstance().level;
      if(world==null)return -1;
      BlockPos pos=BlockPos.of(tree.foliagePosition);
      // [seasons] the canonical (summer) colour: SeasonalFoliage applies the season on top of the baked tree
      return com.formaworks.frontierhunts.season.client.SeasonalColors.baseColor(LiveWorld.state(world,pos),world,pos,0); // [qa] race-safe read
   }

   /** [seasons] The foliage tint a tree was baked with (-1 = none / not baked yet). */
   static int bakedTint(TreeGrowth.Tree tree) {
      return tree.baked instanceof BakedTree b ? b.tint : -1;
   }

   /** Ultra must not round construction timber, including walls, beams and stored logs. */
   private List<BakedQuad> buildingCube(BlockState state) {
      return this.cubes.computeIfAbsent(state, s -> cubeQuads(s, this.bark, this.end, -1)); // [perf2] baked once per state
   }

   /** [perf2] Cube models of this model's block states (timber, loose leaves): the same for every block of a state. */
   private final java.util.concurrent.ConcurrentHashMap<BlockState, List<BakedQuad>> cubes = new java.util.concurrent.ConcurrentHashMap<>();

   /**
    * [perf2] A baked cube (DOWN, UP, NORTH, SOUTH, WEST, EAST: Direction order) that also hands out each face as a ready
    * list, so getQuads(side) no longer streams and filters six quads for every face of every timber / loose-leaf block.
    */
   static final class CubeQuads extends java.util.AbstractList<BakedQuad> implements java.util.RandomAccess {
      private final List<BakedQuad> all;
      @SuppressWarnings("unchecked")
      private final List<BakedQuad>[] sides = new List[6];

      CubeQuads(List<BakedQuad> all) {
         this.all = List.copyOf(all);
         for (int i = 0; i < 6; i++) {
            Direction d = Direction.from3DDataValue(i);
            List<BakedQuad> face = new ArrayList<>(1);
            for (BakedQuad q : this.all) if (q.getDirection() == d) face.add(q);
            this.sides[i] = List.copyOf(face);
         }
      }

      List<BakedQuad> side(Direction d) {
         return this.sides[d.get3DDataValue()];
      }

      @Override public BakedQuad get(int index) {
         return this.all.get(index);
      }

      @Override public int size() {
         return this.all.size();
      }
   }

   /** [perf2] {@link #cube} wrapped for per-face lookups (cache it per block state). */
   static CubeQuads cubeQuads(BlockState state, TextureAtlasSprite bark, TextureAtlasSprite end, int tint) {
      com.formaworks.frontierhunts.perf.client.PerfStats.inc(com.formaworks.frontierhunts.perf.client.PerfStats.CUBES_BAKED);
      return new CubeQuads(cube(state, bark, end, tint));
   }

   /** [perf2] The quads of one face: a cached cube's ready list, else filtered (as before). */
   static List<BakedQuad> face(List<BakedQuad> quads, Direction side) {
      if (quads instanceof CubeQuads c) return c.side(side);
      return quads.stream().filter(face -> face.getDirection() == side).toList();
   }

   static List<BakedQuad> cube(BlockState state,TextureAtlasSprite bark,TextureAtlasSprite end,int tint) {
      List<BakedQuad> result=new ArrayList<>(6);
      int[][][] faces={{{0,0,0},{1,0,0},{1,0,1},{0,0,1}},{{0,1,0},{0,1,1},{1,1,1},{1,1,0}},
         {{0,0,0},{0,1,0},{1,1,0},{1,0,0}},{{0,0,1},{1,0,1},{1,1,1},{0,1,1}},
         {{0,0,0},{0,0,1},{0,1,1},{0,1,0}},{{1,0,0},{1,1,0},{1,1,1},{1,0,1}}};
      Direction.Axis axis=state.hasProperty(BlockStateProperties.AXIS)?state.getValue(BlockStateProperties.AXIS):Direction.Axis.Y;
      Direction[] directions={Direction.DOWN,Direction.UP,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST};
      for(int face=0;face<6;face++) {
         Direction direction=directions[face];
         TextureAtlasSprite sprite=com.formaworks.frontierhunts.client.terrain.BlendSpriteSource.original(direction.getAxis()==axis?end:bark);
         var b=new QuadBakingVertexConsumer();b.setSprite(sprite);b.setDirection(direction);
         b.setShade(true);b.setHasAmbientOcclusion(true);b.setTintIndex(tint);
         for(int i=0;i<4;i++) {
            int[] p=faces[face][i];b.addVertex(p[0],p[1],p[2]);b.setColor(255,255,255,255);
            float u=i<2?0:1,v=i==0||i==3?1:0;
            if(direction.getAxis()!=axis) {
               int along=axis==Direction.Axis.X?0:axis==Direction.Axis.Y?1:2;
               int normal=direction.getAxis()==Direction.Axis.X?0:direction.getAxis()==Direction.Axis.Y?1:2;
               int across=3-along-normal;
               u=p[across];v=1-p[along]; // bark grain follows every placed log's axis
            }
            b.setUv(sprite.getU(u),sprite.getV(v));
            b.setNormal(direction.getStepX(),direction.getStepY(),direction.getStepZ());
         }
         result.add(b.bakeQuad());
      }
      return result;
   }

   /** Bark sprite of each log block (filled when models bake), for tree parts drawn by leaves blocks. */
   static volatile Map<Object, TextureAtlasSprite> BARK = Map.of();

   static BakedQuad bakeBark(TreeShape.Quad q, TextureAtlasSprite sprite) {
      return bakeWith(q, sprite, -1);
   }

   private BakedQuad bake(TreeShape.Quad q, TextureAtlasSprite sprite, int tint) {
      return bakeWith(q, sprite, tint);
   }

   static BakedQuad bakeWith(TreeShape.Quad q, TextureAtlasSprite sprite, int tint) {
      float tr = tint == -1 ? 1.0F : (tint >> 16 & 255) / 255.0F;
      float tg = tint == -1 ? 1.0F : (tint >> 8 & 255) / 255.0F;
      float tb = tint == -1 ? 1.0F : (tint & 255) / 255.0F;
      QuadBakingVertexConsumer b = com.formaworks.frontierhunts.perf.client.FastBake.begin(); // [perf3] reused per thread (~500 B garbage per quad before)
      b.setSprite(sprite);
      b.setDirection(Direction.getNearest(q.nx, q.ny, q.nz));
      b.setShade(false);
      b.setHasAmbientOcclusion(false);
      b.setTintIndex(-1);
      int block = (q.light >> 4) & 0xF, sky = (q.light >> 20) & 0xF;
      for (int k = 0; k < 4; k++) {
         int o = k * 8;
         float nx = q.v[o + 5], ny = q.v[o + 6], nz = q.v[o + 7];
         // Minecraft's directional shading (up 1.0, N/S 0.8, E/W 0.6, down 0.5), blended smoothly for round faces
         float shade = com.formaworks.frontierhunts.client.ShaderState.on ? 1.0F : 0.6F * nx * nx + 0.8F * nz * nz + (ny > 0 ? 1.0F : 0.5F) * ny * ny;
         if (q.texture == 2) {
            shade = 0.72F + 0.28F * shade; // leaves let light through, so they shade less
            if(tint!=-1)shade*=.88F;
         }
         b.addVertex(q.v[o], q.v[o + 1], q.v[o + 2]);
         b.setColor(c255(shade * tr), c255(shade * tg), c255(shade * tb), 255);
         float u=q.v[o+3],v=q.v[o+4];
         if(q.texture==2){u=Math.clamp(u,.0001F,.9999F);v=Math.clamp(v,.0001F,.9999F);}
         b.setUv(sprite.getU(u), sprite.getV(v));
         b.setUv2(block << 4, sky << 4);
         b.setNormal(nx, ny, nz);
      }
      return com.formaworks.frontierhunts.perf.client.FastBake.bake(b); // [perf3]
   }

   private static int c255(float f) {
      return Math.max(0, Math.min(255, (int)(f * 255)));
   }

   /** Reads the world around the log for TreeShape. */
   private static final class Probe implements TreeShape.Probe {
      private final BlockAndTintGetter level;
      private final BlockPos origin;
      private final Set<Block> logs;
      private final boolean conifer;
      private final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      BlockState leafState;
      BlockPos leafPos;

      Probe(BlockAndTintGetter level, BlockPos origin, Set<Block> logs, boolean conifer) {
         this.level = level;
         this.origin = origin;
         this.logs = logs;
         this.conifer = conifer;
      }

      @Override
      public long hash(int dx, int dy, int dz) {
         return BlockPos.asLong(this.origin.getX() + dx, this.origin.getY() + dy, this.origin.getZ() + dz) * 0x9E3779B97F4A7C15L;
      }

      @Override
      public int worldY() {
         return this.origin.getY();
      }

      @Override
      public boolean conifer() {
         return this.conifer;
      }

      /**
       * The chunk mesher only hands us a couple of blocks around the section (Sodium: 2), so anything
       * further away is read from the live client world - otherwise trees break at section edges.
       */
      private BlockAndTintGetter source(int dx, int dy, int dz) {
         if (Math.abs(dx) <= 2 && Math.abs(dy) <= 2 && Math.abs(dz) <= 2) {
            return this.level;
         }
         BlockAndTintGetter live = Minecraft.getInstance().level;
         return live != null ? live : this.level;
      }

      boolean near(BlockPos p) {
         return Math.abs(p.getX() - this.origin.getX()) <= 2 && Math.abs(p.getY() - this.origin.getY()) <= 2 && Math.abs(p.getZ() - this.origin.getZ()) <= 2;
      }

      private BlockState at(int dx, int dy, int dz) {
         this.m.set(this.origin.getX() + dx, this.origin.getY() + dy, this.origin.getZ() + dz);
         return LiveWorld.state(this.source(dx, dy, dz), this.m); // [qa] race-safe read of the live world
      }

      @Override
      public int kind(int dx, int dy, int dz) {
         BlockState s = this.at(dx, dy, dz);
         if (this.logs.contains(s.getBlock()) || s.is(BlockTags.LOGS)) {
            return TreeShape.LOG; // other logs (wood blocks, other species) still join the tree
         }
         if (s.is(BlockTags.LEAVES)) {
            if (this.leafState == null) {
               this.leafState = s;
               this.leafPos = this.m.immutable();
            }
            return TreeShape.LEAVES;
         }
         if (s.isAir() || !s.getFluidState().isEmpty() && !s.isSolid()) {
            return TreeShape.AIR;
         }
         if (s.is(BlockTags.DIRT) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.SAND) || s.is(BlockTags.SNOW)) {
            return TreeShape.GROUND;
         }
         return s.isSolidRender(this.level, this.m) ? TreeShape.SOLID : TreeShape.AIR;
      }

      @Override
      public int axis(int dx, int dy, int dz) {
         BlockState s = this.at(dx, dy, dz);
         if (s.hasProperty(BlockStateProperties.AXIS)) {
            return s.getValue(BlockStateProperties.AXIS).ordinal();
         }
         return 1;
      }

      @Override
      public int light(int dx, int dy, int dz) {
         this.m.set(this.origin.getX() + dx, this.origin.getY() + dy, this.origin.getZ() + dz);
         BlockAndTintGetter src = this.source(dx, dy, dz);
         int sky = src.getBrightness(LightLayer.SKY, this.m);
         int block = src.getBrightness(LightLayer.BLOCK, this.m);
         return sky << 20 | block << 4;
      }

      @Override
      public long seed() {
         return BlockPos.asLong(this.origin.getX(), 0, this.origin.getZ()) * 0x9E3779B97F4A7C15L ^ this.origin.getY();
      }
   }

   // ------------------------------------------------------------------ BakedModel
   @Override
   public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData data, RenderType type) {
      List<BakedQuad> q = data.get(QUADS);
      if (q != null) {
         if(data.get(LEAF_QUADS)==null) {
            if(side==null || type!=null && type!=RenderType.solid())return List.of();
            return face(q, side); // [perf2]
         }
         if (side != null) {
            return List.of();
         }
         List<BakedQuad> leaves = data.get(LEAF_QUADS);
         if (type == null) {
            if (leaves == null) {
               return q;
            }
            List<BakedQuad> all = new ArrayList<>(q);
            all.addAll(leaves);
            return all;
         }
         if (type == RenderType.cutoutMipped()) {
            return leaves == null ? List.of() : leaves;
         }
         return type == RenderType.solid() ? q : List.of();
      }
      return this.base.getQuads(state, side, rand, data, type);
   }

   @Override
   public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
      return data.get(LEAF_QUADS) != null ? SOLID_AND_LEAVES : SOLID;
   }

   @Override
   public boolean useAmbientOcclusion() {
      return false;
   }

   @Override public net.neoforged.neoforge.common.util.TriState useAmbientOcclusion(BlockState state,ModelData data,RenderType type) {
      return data.get(QUADS)!=null && data.get(LEAF_QUADS)==null
         ?net.neoforged.neoforge.common.util.TriState.TRUE:net.neoforged.neoforge.common.util.TriState.FALSE;
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

   static boolean isLogBlock(Block b) {
      return b instanceof RotatedPillarBlock;
   }
}
