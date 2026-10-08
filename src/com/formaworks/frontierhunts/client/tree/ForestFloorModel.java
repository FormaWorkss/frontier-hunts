package com.formaworks.frontierhunts.client.tree;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;

/**
 * Realistic world forest floor. Natural ground under a tree canopy that has nothing on it (or only
 * the forest-litter layer, which the realistic pack draws as nothing because its square plate read
 * as blotches) is dressed with a few small, loose pieces instead of a block-sized overlay:
 * rotated leaf-litter or fallen-needle decals that may spill onto level neighbours (no square edges),
 * damp moss patches in deep shade, fallen twigs, pine cones under conifers, the odd mushroom and
 * small mossy stone. Deterministic per position, client-only (existing worlds benefit), about three
 * quads per dressed block, and nothing at all outside a canopy or in the other presets.
 */
public final class ForestFloorModel extends BakedModelWrapper<BakedModel> {
   static final ModelProperty<List<BakedQuad>> EXTRA = new ModelProperty<>();
   /** Canopy samples around the block (x, z offsets): the centre and a ring about three blocks out. */
   private static final int[] SAMPLES = {0, 0, 3, 0, -3, 0, 0, 3, 0, -3, 2, 2, 2, -2, -2, 2, -2, -2};
   private static final ConcurrentHashMap<Block, Boolean> NEEDLES = new ConcurrentHashMap<>();

   /** Sprites the dressing uses; any may be null when a pack lacks it (that piece is skipped). */
   public record Sprites(TextureAtlasSprite needles, TextureAtlasSprite[] leafLitter, TextureAtlasSprite moss,
                         TextureAtlasSprite twig, TextureAtlasSprite cone, TextureAtlasSprite brownMushroom,
                         TextureAtlasSprite redMushroom, TextureAtlasSprite stone, Block litterBlock) {
   }

   private final Sprites sprites;

   public ForestFloorModel(BakedModel base, Sprites sprites) {
      super(base);
      this.sprites = sprites;
   }

   @Override
   public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
      data = this.originalModel.getModelData(level, pos, state, data);
      try {
         BlockPos up = pos.above();
         BlockState above = level.getBlockState(up);
         boolean litter = this.sprites.litterBlock() != null && above.is(this.sprites.litterBlock());
         if (!above.isAir() && !litter) {
            return data; // plants, snow, blocks: the ground is not bare
         }
         var live = Minecraft.getInstance().level;
         if (live == null) {
            return data;
         }
         int x = pos.getX(), y = pos.getY(), z = pos.getZ();
         // Canopy cover from the client heightmap (leaves block motion) and the first leaves found
         // under each covered column's top; Sodium's section copy does not reach that high.
         int covered = 0, needles = 0;
         BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
         for (int i = 0; i < SAMPLES.length; i += 2) {
            int hit = column(live, x + SAMPLES[i], z + SAMPLES[i + 1], m);
            if (hit != NO_HIT && (hit & 2) != 0 && (hit >> 2) - 2048 >= y + 2) {
               covered++;
               if ((hit & 1) != 0) {
                  needles++;
               }
            }
         }
         if (covered == 0 && !litter) {
            return data; // open ground stays as it is (clearings, meadows)
         }
         // Level neighbours the decals may spill onto (same ground height, open above).
         float[] bounds = {-.03F, 1.03F, -.03F, 1.03F};
         Direction[] sides = {Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH};
         for (int i = 0; i < 4; i++) {
            BlockPos n = pos.relative(sides[i]);
            BlockState ground = level.getBlockState(n), over = level.getBlockState(n.above());
            boolean level_ = ground.isSolidRender(level, n) && !over.isSolidRender(level, n.above());
            float spill = level_ ? .28F : -.03F;
            bounds[i] = (i % 2 == 0) ? -spill : 1 + spill;
         }
         int light = level.getBrightness(LightLayer.BLOCK, up) << 4 | level.getBrightness(LightLayer.SKY, up) << 20;
         int floor = TreeLod.floorLod(x, y, z);
         // [perf] beyond the impostor distance the crowns are flat cards and the floor under them is
         // hidden by them and a few pixels wide: plain ground (the layer's biggest share there)
         if (floor == TreeLod.IMPOSTOR) return data;
         boolean far = floor == TreeLod.FAR;
         List<BakedQuad> quads = this.dress(TreeShape.mix(pos.asLong() * 0x9E3779B97F4A7C15L + 0x51ED27L), covered,
            needles * 2 > covered, litter, bounds, light, far);
         return quads.isEmpty() ? data : data.derive().with(EXTRA, quads).build();
      } catch (RuntimeException e) {
         return data;
      }
   }

   private static final int NO_HIT = -1;
   private static final ThreadLocal<Columns> COLUMNS = ThreadLocal.withInitial(Columns::new);

   /** Per builder thread: the first leaves or solid block under each column's top, reused by neighbouring blocks. */
   private static final class Columns {
      final java.util.HashMap<Long, Integer> hits = new java.util.HashMap<>();
      java.lang.ref.WeakReference<Object> level = new java.lang.ref.WeakReference<>(null);
      long stamp;
   }

   /**
    * Scans one column down from its motion-blocking top (at most 24 blocks) to the first leaves or other
    * non-air, non-log block. Returns {@link #NO_HIT} or (y + 2048) << 2 | leaves << 1 | needles.
    */
   private static int column(net.minecraft.client.multiplayer.ClientLevel live, int cx, int cz, BlockPos.MutableBlockPos m) {
      Columns c = COLUMNS.get();
      long now = System.nanoTime();
      if (c.level.get() != live || now - c.stamp > 2_000_000_000L || c.hits.size() > 8192) {
         c.hits.clear();
         c.level = new java.lang.ref.WeakReference<>(live);
         c.stamp = now;
      }
      long key = (long)cx << 32 ^ (cz & 0xFFFFFFFFL);
      Integer known = c.hits.get(key);
      if (known != null) {
         return known;
      }
      int result = NO_HIT;
      int top = live.getHeight(Heightmap.Types.MOTION_BLOCKING, cx, cz);
      int floor = live.getMinBuildHeight();
      for (int yy = top - 1, n = 0; yy >= floor && n < 24; yy--, n++) {
         BlockState s = live.getBlockState(m.set(cx, yy, cz));
         if (s.is(BlockTags.LEAVES)) {
            boolean needle = NEEDLES.computeIfAbsent(s.getBlock(), b -> LeafModel.isConifer(BuiltInRegistries.BLOCK.getKey(b).getPath()));
            result = (yy + 2048) << 2 | 2 | (needle ? 1 : 0);
            break;
         }
         if (!s.isAir() && !s.is(BlockTags.LOGS)) {
            result = (yy + 2048) << 2; // higher terrain, a roof or a plant: not a canopy
            break;
         }
      }
      c.hits.put(key, result);
      return result;
   }

   /**
    * The pieces for one block. {@code seed} is per position; the same block always dresses the same.
    * At distance ({@code far}) the overlapping litter decals become one larger decal (the same litter
    * cover) and the small solid pieces (twigs, cones, mushrooms, stones: well under a pixel there) are
    * left out, about a third of the quads.
    */
   private List<BakedQuad> dress(long seed, int covered, boolean conifer, boolean litter, float[] bounds, int light, boolean far) {
      Rng r = new Rng(seed);
      float shade = covered / 9.0F;
      List<BakedQuad> out = new ArrayList<>(6);
      // 1. litter decals: deeper cover, more of them; sunlit edges get patchy, not every block
      int decals = litter ? 3 : covered >= 6 ? 2 : covered >= 3 ? 1 : r.next() < .55F ? 1 : 0;
      float grow = 1;
      if (far && decals > 1) {
         grow = (float)Math.sqrt(decals);
         decals = 1;
      }
      float lift = 1.006F;
      for (int i = 0; i < decals; i++) {
         TextureAtlasSprite sprite;
         if (conifer) {
            sprite = r.next() < .8F ? this.sprites.needles() : this.leafLitter(r, 2);
         } else {
            sprite = this.leafLitter(r, -1);
         }
         if (sprite == null) continue;
         this.decal(out, sprite, r.range(.55F, .95F) * grow, r, bounds, lift, light);
         lift += .004F;
      }
      // 2. damp moss in deep shade
      if (this.sprites.moss() != null && r.next() < .06F + .16F * shade) {
         this.decal(out, this.sprites.moss(), r.range(.7F, 1.15F), r, bounds, lift, light);
         lift += .004F;
      }
      if (far) return out;
      // 3. a fallen twig
      if (this.sprites.twig() != null && r.next() < .12F) {
         float yaw = r.range(0, 6.2832F), length = r.range(.45F, .85F);
         float cx = r.range(.3F, .7F), cz = r.range(.3F, .7F);
         this.box(out, this.sprites.twig(), cx, cz, yaw, length, .045F, .04F, 1.0F, light, true);
      }
      // 4. pine cones under needles
      if (conifer && this.sprites.cone() != null && r.next() < .1F) {
         int n = r.next() < .3F ? 2 : 1;
         for (int i = 0; i < n; i++) {
            this.box(out, this.sprites.cone(), r.range(.2F, .8F), r.range(.2F, .8F), r.range(0, 6.2832F), .17F, .1F, .09F, 1.0F, light, false);
         }
      }
      // 5. the odd mushroom in deep shade
      if (r.next() < .035F * shade) {
         TextureAtlasSprite mushroom = r.next() < .8F ? this.sprites.brownMushroom() : this.sprites.redMushroom();
         if (mushroom != null) this.cross(out, mushroom, r.range(.25F, .75F), r.range(.25F, .75F), r.range(0, 1.57F), r.range(.3F, .42F), light);
      }
      // 6. a small mossy stone
      if (this.sprites.stone() != null && r.next() < .025F) {
         this.box(out, this.sprites.stone(), r.range(.3F, .7F), r.range(.3F, .7F), r.range(0, 6.2832F), r.range(.2F, .32F), r.range(.16F, .26F), r.range(.08F, .14F), 1.0F, light, false);
      }
      return out;
   }

   private TextureAtlasSprite leafLitter(Rng r, int only) {
      TextureAtlasSprite[] all = this.sprites.leafLitter();
      if (all == null || all.length == 0) return null;
      if (only >= 0 && only < all.length && all[only] != null) return all[only];
      TextureAtlasSprite pick = all[Math.min(all.length - 1, (int)(r.next() * all.length))];
      return pick != null ? pick : all[0];
   }

   /** A flat piece lying on the ground at any angle, kept within the level area around the block. */
   private void decal(List<BakedQuad> out, TextureAtlasSprite sprite, float size, Rng r, float[] bounds, float y, int light) {
      float angle = r.range(0, 6.2832F), c = (float)Math.cos(angle), s = (float)Math.sin(angle);
      float half = size * .5F * (Math.abs(c) + Math.abs(s));
      float spanX = bounds[1] - bounds[0], spanZ = bounds[3] - bounds[2];
      float fit = Math.min(1, Math.min(spanX, spanZ) / (2 * half));
      if (fit < 1) {
         size *= fit;
         half *= fit;
      }
      float cx = bounds[0] + half + (spanX - 2 * half) * r.next();
      float cz = bounds[2] + half + (spanZ - 2 * half) * r.next();
      float[][] corner = {{-.5F, -.5F}, {-.5F, .5F}, {.5F, .5F}, {.5F, -.5F}};
      QuadBakingVertexConsumer b = consumer(sprite, Direction.UP);
      boolean flip = r.next() < .5F;
      for (float[] k : corner) {
         float px = cx + (k[0] * c - k[1] * s) * size, pz = cz + (k[0] * s + k[1] * c) * size;
         b.addVertex(px, y, pz);
         b.setColor(255, 255, 255, 255);
         float u = k[0] + .5F, v = k[1] + .5F;
         b.setUv(sprite.getU(flip ? 1 - u : u), sprite.getV(v));
         b.setUv2(light & 0xFFFF, light >>> 16 & 0xFFFF);
         b.setNormal(0, 1, 0);
      }
      out.add(com.formaworks.frontierhunts.perf.client.FastBake.bake(b)); // [perf3]
   }

   /**
    * A small box lying on the ground (twig, cone, stone): top and four sides, no bottom.
    * With {@code grain} the texture runs along the length (bark); otherwise each face shows a
    * proportional window of the sprite.
    */
   private void box(List<BakedQuad> out, TextureAtlasSprite sprite, float cx, float cz, float yaw, float length, float width,
                    float height, float ground, int light, boolean grain) {
      float c = (float)Math.cos(yaw), s = (float)Math.sin(yaw);
      float hl = length * .5F, hw = width * .5F;
      // local (along, across) -> world
      float[][] base = {{-hl, -hw}, {hl, -hw}, {hl, hw}, {-hl, hw}};
      float[][] p = new float[4][];
      for (int i = 0; i < 4; i++) {
         p[i] = new float[]{cx + base[i][0] * c - base[i][1] * s, cz + base[i][0] * s + base[i][1] * c};
      }
      float y0 = ground + .002F, y1 = ground + height;
      this.face(out, sprite, new float[][]{{p[0][0], y1, p[0][1]}, {p[3][0], y1, p[3][1]}, {p[2][0], y1, p[2][1]}, {p[1][0], y1, p[1][1]}},
         new float[]{0, 0, width, length}, light, new float[]{0, 1, 0});
      for (int i = 0; i < 4; i++) {
         float[] a = p[i], d = p[(i + 1) % 4];
         float edge = i % 2 == 0 ? length : width;
         float[] outward = {(a[0] + d[0]) * .5F - cx, 0, (a[1] + d[1]) * .5F - cz};
         this.face(out, sprite, new float[][]{{a[0], y0, a[1]}, {d[0], y0, d[1]}, {d[0], y1, d[1]}, {a[0], y1, a[1]}},
            grain ? new float[]{.3F, 0, .3F + height, edge} : new float[]{0, 0, edge, height}, light, outward);
      }
   }

   /** Two crossed double-sided cards (a tiny plant), like vanilla's cross model. */
   private void cross(List<BakedQuad> out, TextureAtlasSprite sprite, float cx, float cz, float yaw, float size, int light) {
      for (int k = 0; k < 2; k++) {
         float a = yaw + k * 1.5708F, dx = (float)Math.cos(a) * size * .5F, dz = (float)Math.sin(a) * size * .5F;
         float[][] front = {{cx - dx, 1.0F, cz - dz}, {cx + dx, 1.0F, cz + dz}, {cx + dx, 1.0F + size, cz + dz}, {cx - dx, 1.0F + size, cz - dz}};
         float[][] back = {front[1], front[0], front[3], front[2]};
         this.face(out, sprite, front, new float[]{0, 0, 1, 1}, light, null);
         this.face(out, sprite, back, new float[]{0, 0, 1, 1}, light, null);
      }
   }

   /**
    * One quad through four corners, turned to face {@code outward} when given. {@code uv} is
    * {u0, v0, u1, v1} in sprite units (clamped into the sprite, never tiling).
    */
   private void face(List<BakedQuad> out, TextureAtlasSprite sprite, float[][] pts, float[] uv, int light, float[] outward) {
      float ex = pts[2][0] - pts[0][0], ey = pts[2][1] - pts[0][1], ez = pts[2][2] - pts[0][2];
      float fx = pts[3][0] - pts[1][0], fy = pts[3][1] - pts[1][1], fz = pts[3][2] - pts[1][2];
      float nx = ey * fz - ez * fy, ny = ez * fx - ex * fz, nz = ex * fy - ey * fx;
      float l = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
      if (l < 1e-9F) return;
      if (outward != null && nx * outward[0] + ny * outward[1] + nz * outward[2] < 0) {
         // wind the other way so the face points out of the box (chunk layers cull back faces)
         pts = new float[][]{pts[0], pts[3], pts[2], pts[1]};
         nx = -nx;
         ny = -ny;
         nz = -nz;
      }
      nx /= l;
      ny /= l;
      nz /= l;
      float du = Math.min(1, uv[2] - uv[0]), dv = Math.min(1, uv[3] - uv[1]);
      float u0 = Math.max(0, Math.min(1 - du, uv[0])), v0 = Math.max(0, Math.min(1 - dv, uv[1]));
      float[][] tex = {{u0, v0 + dv}, {u0 + du, v0 + dv}, {u0 + du, v0}, {u0, v0}};
      QuadBakingVertexConsumer b = consumer(sprite, Direction.getNearest(nx, ny, nz));
      for (int i = 0; i < 4; i++) {
         b.addVertex(pts[i][0], pts[i][1], pts[i][2]);
         b.setColor(255, 255, 255, 255);
         b.setUv(sprite.getU(tex[i][0]), sprite.getV(tex[i][1]));
         b.setUv2(light & 0xFFFF, light >>> 16 & 0xFFFF);
         b.setNormal(nx, ny, nz);
      }
      out.add(com.formaworks.frontierhunts.perf.client.FastBake.bake(b)); // [perf3]
   }

   private static QuadBakingVertexConsumer consumer(TextureAtlasSprite sprite, Direction direction) {
      QuadBakingVertexConsumer b = com.formaworks.frontierhunts.perf.client.FastBake.begin(); // [perf3] reused per thread
      b.setSprite(sprite);
      b.setDirection(direction);
      b.setShade(true);
      b.setHasAmbientOcclusion(false);
      b.setTintIndex(-1);
      return b;
   }

   /** SplitMix64 stream from the position seed. */
   private static final class Rng {
      private long state;

      Rng(long seed) {
         this.state = seed;
      }

      float next() {
         long z = (this.state += 0x9E3779B97F4A7C15L);
         z = (z ^ z >>> 30) * 0xBF58476D1CE4E5B9L;
         z = (z ^ z >>> 27) * 0x94D049BB133111EBL;
         z ^= z >>> 31;
         return (z >>> 40) / (float)(1L << 24);
      }

      float range(float low, float high) {
         return low + (high - low) * this.next();
      }
   }

   // ------------------------------------------------------------------ BakedModel
   @Override
   public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData data, RenderType type) {
      List<BakedQuad> extra = data.get(EXTRA);
      if (extra == null || side != null || type != null && type != RenderType.cutoutMipped()) {
         return this.originalModel.getQuads(state, side, rand, data, type);
      }
      boolean baseType = type == null || this.originalModel.getRenderTypes(state, rand, data).contains(type);
      List<BakedQuad> own = baseType ? this.originalModel.getQuads(state, side, rand, data, type) : List.of();
      if (own.isEmpty()) {
         return extra;
      }
      List<BakedQuad> all = new ArrayList<>(own.size() + extra.size());
      all.addAll(own);
      all.addAll(extra);
      return all;
   }

   @Override
   public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
      ChunkRenderTypeSet own = this.originalModel.getRenderTypes(state, rand, data);
      return data.get(EXTRA) != null ? ChunkRenderTypeSet.union(own, Layer.CUTOUT) : own;
   }

   /** Created on first use (render types must not be touched while this class loads). */
   private static final class Layer {
      static final ChunkRenderTypeSet CUTOUT = ChunkRenderTypeSet.of(RenderType.cutoutMipped());
   }
}
