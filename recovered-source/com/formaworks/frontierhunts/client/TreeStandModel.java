package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.client.tree.StandTrunkProbe;
import com.formaworks.frontierhunts.expedition.ExpeditionContent;
import com.formaworks.frontierhunts.expedition.MountedTreeStand;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;

/**
 * Tree stand parts that touch the trunk, fitted to the realistic preset's round trunks. The stand's
 * blocks sit on the block grid (flush with a full log face); a realistic trunk is round, thinner,
 * sometimes off-centre or leaning. On such a trunk the stand's straps, which wrap a square log, are
 * replaced by straps around the drawn stem, and steel standoff arms reach from the platform and the
 * lower bracket to the bark. Nothing changes on block-shaped trunks. Everything here is baked with
 * the chunk section; any failure (tree changed or felled, realistic trees off) leaves the plain model.
 */
public final class TreeStandModel extends BakedModelWrapper<BakedModel> {
   private static final ModelProperty<Hug> HUG = new ModelProperty<>();
   /** Palette cells of the stand's material texture (in 1/16 of the sprite). */
   private static final float STEEL_U = 1.0F, STEEL_V = 1.0F, STRAP_U = 10.0F, STRAP_V = 6.0F;

   private final int part;
   private volatile TextureAtlasSprite sprite;

   private TreeStandModel(BakedModel base, int part) {
      super(base);
      this.part = part;
   }

   /** Extra quads for one stand block on a round trunk (its square-log straps are then hidden). */
   private record Hug(List<BakedQuad> quads) {
   }

   @EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
   public static final class Registration {
      private Registration() {
      }

      @SubscribeEvent
      public static void models(ModelEvent.ModifyBakingResult event) {
         try {
            Map<ModelResourceLocation, BakedModel> models = event.getModels();
            for (BlockState state : ExpeditionContent.TREE_STAND.get().getStateDefinition().getPossibleStates()) {
               int part = state.getValue(MountedTreeStand.PART);
               if (!touchesTrunk(part)) {
                  continue;
               }
               ModelResourceLocation key = BlockModelShaper.stateToModelLocation(state);
               BakedModel model = models.get(key);
               if (model != null && !(model instanceof TreeStandModel)) {
                  models.put(key, new TreeStandModel(model, part));
               }
            }
         } catch (RuntimeException e) {
            com.mojang.logging.LogUtils.getLogger().warn("Frontier tree stand: could not fit stand models to realistic trunks", e);
         }
      }
   }

   /** Centre-column parts beside the trunk (bracket, platform, seat) and the wide seat's strap ends. */
   private static boolean touchesTrunk(int part) {
      return part >= 4 && part <= 6 || part == 11 || part == 13 || part == 15 || part == 17;
   }

   private static boolean sideColumn(int part) {
      return part >= 11;
   }

   // ------------------------------------------------------------------ world data

   @Override
   public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
      try {
         if (!state.hasProperty(MountedTreeStand.FACING)) {
            return data;
         }
         Direction facing = state.getValue(MountedTreeStand.FACING);
         BlockPos centre = pos;
         if (sideColumn(this.part)) {
            int side = this.part < 15 ? -1 : 1;
            centre = pos.subtract(lateral(facing, side));
         }
         BlockPos trunk = centre.relative(facing.getOpposite());
         BlockState log = level.getBlockState(trunk);
         if (!log.is(BlockTags.LOGS)) {
            return data;
         }
         float[] stem = StandTrunkProbe.stemAt(level, trunk, log);
         if (stem == null) {
            return data;
         }
         List<BakedQuad> quads = sideColumn(this.part) ? List.of() : this.hug(state, facing, pos, stem);
         if (quads == null) {
            return data;
         }
         return data.derive().with(HUG, new Hug(quads)).build();
      } catch (RuntimeException e) {
         return data;
      }
   }

   @Override
   public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData data, RenderType renderType) {
      List<BakedQuad> quads = super.getQuads(state, side, rand, data, renderType);
      Hug hug = data.get(HUG);
      if (hug == null || state == null) {
         return quads;
      }
      try {
         Direction facing = state.getValue(MountedTreeStand.FACING);
         List<BakedQuad> out = new ArrayList<>(quads.size() + (side == null ? hug.quads().size() : 0));
         for (BakedQuad q : quads) {
            if (!squareStrap(q, facing)) {
               out.add(q);
            }
         }
         if (side == null && !hug.quads().isEmpty() && this.mainLayer(state, rand, data, renderType)) {
            out.addAll(hug.quads());
         }
         return out;
      } catch (RuntimeException e) {
         return quads;
      }
   }

   private boolean mainLayer(BlockState state, RandomSource rand, ModelData data, RenderType renderType) {
      if (renderType == null) {
         return true;
      }
      ChunkRenderTypeSet types = super.getRenderTypes(state, rand, data);
      var it = types.iterator();
      return !it.hasNext() || it.next() == renderType;
   }

   /** The stand's strap pieces that wrap a square log face (strap colour, at the trunk-side face or beyond). */
   private static boolean squareStrap(BakedQuad q, Direction facing) {
      TextureAtlasSprite sp = q.getSprite();
      int[] v = q.getVertices();
      if (sp == null || v.length < 32) {
         return false;
      }
      float du = sp.getU1() - sp.getU0(), dv = sp.getV1() - sp.getV0();
      if (du == 0 || dv == 0) {
         return false;
      }
      float u = 0, w = 0, x = 0, z = 0;
      for (int i = 0; i < 4; i++) {
         u += Float.intBitsToFloat(v[i * 8 + 4]);
         w += Float.intBitsToFloat(v[i * 8 + 5]);
         x += Float.intBitsToFloat(v[i * 8]);
         z += Float.intBitsToFloat(v[i * 8 + 2]);
      }
      float pu = (u / 4 - sp.getU0()) / du * 16, pv = (w / 4 - sp.getV0()) / dv * 16;
      if (Math.abs(pu - STRAP_U) > 0.35F || Math.abs(pv - STRAP_V) > 0.35F) {
         return false;
      }
      float[] local = toLocal(facing, x / 4, z / 4);
      return local[1] >= 0.975F;
   }

   // ------------------------------------------------------------------ geometry

   /**
    * Quads fitting this part to the drawn stem, in the part block's coordinates; null when the stem
    * is not where a stand on this block could reach (then the plain model is kept).
    */
   private List<BakedQuad> hug(BlockState state, Direction facing, BlockPos pos, float[] stem) {
      float[] c = toLocal(facing, stem[0] - pos.getX(), stem[2] - pos.getZ());
      float cx = c[0], cz = c[1], r = stem[3];
      if (!(r > 0.02F) || r > 1.6F || cz < 0.8F || cz > 3.4F || Math.abs(cx - 0.5F) > 1.8F) {
         return null;
      }
      TextureAtlasSprite sp = this.sprite(state);
      if (sp == null) {
         return null;
      }
      Builder b = new Builder(facing, sp);
      // contact points on the bark facing the stand
      float mx = 0.5F - cx, mz = 0.975F - cz;
      float ml = (float)Math.sqrt(mx * mx + mz * mz);
      if (ml < 1.0E-3F) {
         return null;
      }
      mx /= ml;
      mz /= ml;
      float phi = 0.25F / r >= 0.643F ? (float)Math.toRadians(40.0) : (float)Math.asin(0.25F / r);
      switch (this.part) {
         case 4 -> {
            this.arms(b, cx, cz, r, mx, mz, phi, 0.125F, 0.72F, 0.985F);
            this.strap(b, cx, cz, r, 12.48F / 16, 13.216F / 16);
         }
         case 5 -> this.arms(b, cx, cz, r, mx, mz, phi, 0.06F, 0.62F, 0.975F);
         case 6 -> this.strap(b, cx, cz, r, 8.64F / 16, 9.504F / 16);
         default -> {
         }
      }
      return b.quads;
   }

   /** Two steel standoffs from the stand's frame (inset from its sides) to the bark. */
   private void arms(Builder b, float cx, float cz, float r, float mx, float mz, float phi, float inset, float y, float z) {
      float cos = (float)Math.cos(phi), sin = (float)Math.sin(phi);
      float[][] contact = new float[2][];
      for (int s = 0; s < 2; s++) {
         float sign = s == 0 ? -1.0F : 1.0F;
         // the stand-facing direction turned by -phi / +phi
         float dx = mx * cos - mz * sin * sign, dz = mx * sin * sign + mz * cos;
         contact[s] = new float[]{cx + dx * (r + 0.005F), cz + dz * (r + 0.005F), dx, dz};
      }
      if (contact[0][0] > contact[1][0]) {
         float[] t = contact[0];
         contact[0] = contact[1];
         contact[1] = t;
      }
      for (int s = 0; s < 2; s++) {
         float px = contact[s][0], pz = contact[s][1], dx = contact[s][2], dz = contact[s][3];
         float ax = s == 0 ? inset : 1.0F - inset;
         float len = (float)Math.sqrt((px - ax) * (px - ax) + (pz - z) * (pz - z));
         if (pz < z + 0.01F || len < 0.02F || len > 2.0F) {
            continue; // flush (or the bark already reaches the frame)
         }
         b.bar(ax, y, z, px, y, pz, 0.04F, 0.04F, STEEL_U, STEEL_V);
         // a small pad against the bark
         b.bar(px - dx * 0.01F, y - 0.05F, pz - dz * 0.01F, px - dx * 0.01F, y + 0.05F, pz - dz * 0.01F, 0.05F, 0.02F, STEEL_U, STEEL_V);
      }
   }

   /** A strap band around the drawn stem, with its two tails running back to the stand's sides. */
   private void strap(Builder b, float cx, float cz, float r, float y0, float y1) {
      float inner = r + 0.01F, outer = r + 0.03F;
      int n = Math.max(12, Math.min(40, Math.round((float)(Math.PI * 2 * outer / 0.08))));
      for (int i = 0; i < n; i++) {
         double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
         float c0 = (float)Math.cos(a0), s0 = (float)Math.sin(a0), c1 = (float)Math.cos(a1), s1 = (float)Math.sin(a1);
         float ox0 = cx + c0 * outer, oz0 = cz + s0 * outer, ox1 = cx + c1 * outer, oz1 = cz + s1 * outer;
         float ix0 = cx + c0 * inner, iz0 = cz + s0 * inner, ix1 = cx + c1 * inner, iz1 = cz + s1 * inner;
         float nx = (c0 + c1) * 0.5F, nz = (s0 + s1) * 0.5F;
         b.quad(new float[][]{{ox0, y0, oz0}, {ox1, y0, oz1}, {ox1, y1, oz1}, {ox0, y1, oz0}}, nx, 0, nz, STRAP_U, STRAP_V);
         b.quad(new float[][]{{ix0, y1, iz0}, {ox0, y1, oz0}, {ox1, y1, oz1}, {ix1, y1, iz1}}, 0, 1, 0, STRAP_U, STRAP_V);
         b.quad(new float[][]{{ix0, y0, iz0}, {ix1, y0, iz1}, {ox1, y0, oz1}, {ox0, y0, oz0}}, 0, -1, 0, STRAP_U, STRAP_V);
      }
      float ym = (y0 + y1) * 0.5F, h = y1 - y0;
      for (float qx : new float[]{0.0F, 1.0F}) {
         float qz = 0.99F;
         float dx = qx - cx, dz = qz - cz;
         float d = (float)Math.sqrt(dx * dx + dz * dz);
         if (d <= outer + 0.02F) {
            continue;
         }
         // tangent point on the far side of the stem from this corner of the stand
         float base = (float)Math.atan2(dz, dx), off = (float)Math.acos(Math.min(1.0F, outer / d));
         float best = Float.NaN, tx = 0, tz = 0;
         for (int s = -1; s <= 1; s += 2) {
            float a = base + off * s;
            float px = cx + (float)Math.cos(a) * outer, pz = cz + (float)Math.sin(a) * outer;
            if (Float.isNaN(best) || pz > best) {
               best = pz;
               tx = px;
               tz = pz;
            }
         }
         b.bar(qx, ym, qz, tx, ym, tz, 0.02F, h, STRAP_U, STRAP_V);
      }
   }

   private TextureAtlasSprite sprite(BlockState state) {
      TextureAtlasSprite sp = this.sprite;
      if (sp == null) {
         for (BakedQuad q : super.getQuads(state, null, RandomSource.create(42L), ModelData.EMPTY, null)) {
            if (q.getSprite() != null) {
               sp = q.getSprite();
               break;
            }
         }
         if (sp == null) {
            sp = super.getParticleIcon(ModelData.EMPTY);
         }
         this.sprite = sp;
      }
      return sp;
   }

   // ------------------------------------------------------------------ frames

   /** World-oriented block-local x, z to the stand's own frame (trunk towards +z, as facing north). */
   static float[] toLocal(Direction facing, float wx, float wz) {
      return switch (facing) {
         case EAST -> new float[]{wz, 1.0F - wx};
         case SOUTH -> new float[]{1.0F - wx, 1.0F - wz};
         case WEST -> new float[]{1.0F - wz, wx};
         default -> new float[]{wx, wz};
      };
   }

   /** The stand's own frame to world-oriented block-local x, z (MountedTreeStand#rotateShape). */
   static float[] toWorld(Direction facing, float lx, float lz) {
      return switch (facing) {
         case EAST -> new float[]{1.0F - lz, lx};
         case SOUTH -> new float[]{1.0F - lx, 1.0F - lz};
         case WEST -> new float[]{lz, 1.0F - lx};
         default -> new float[]{lx, lz};
      };
   }

   private static float[] turn(Direction facing, float nx, float nz) {
      return switch (facing) {
         case EAST -> new float[]{-nz, nx};
         case SOUTH -> new float[]{-nx, -nz};
         case WEST -> new float[]{nz, -nx};
         default -> new float[]{nx, nz};
      };
   }

   /** A column's offset from the stand's centre column (MountedTreeStand#rotateOffset of x = side). */
   private static BlockPos lateral(Direction facing, int side) {
      return switch (facing) {
         case EAST -> new BlockPos(0, 0, side);
         case SOUTH -> new BlockPos(-side, 0, 0);
         case WEST -> new BlockPos(0, 0, -side);
         default -> new BlockPos(side, 0, 0);
      };
   }

   /** Bakes quads given in the stand's frame. */
   private static final class Builder {
      final Direction facing;
      final TextureAtlasSprite sprite;
      final List<BakedQuad> quads = new ArrayList<>();

      Builder(Direction facing, TextureAtlasSprite sprite) {
         this.facing = facing;
         this.sprite = sprite;
      }

      /** A square-section bar from a to b (width across, height up). */
      void bar(float ax, float ay, float az, float bx, float by, float bz, float width, float height, float u, float v) {
         float dx = bx - ax, dy = by - ay, dz = bz - az;
         float len = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
         if (len < 1.0E-4F) {
            return;
         }
         dx /= len;
         dy /= len;
         dz /= len;
         // side = d x up (horizontal), up' = side x d
         float sx = -dz, sy = 0, sz = dx;
         float sl = (float)Math.sqrt(sx * sx + sz * sz);
         if (sl < 1.0E-4F) {
            sx = 1;
            sz = 0;
            sl = 1;
         }
         sx /= sl;
         sz /= sl;
         float ux = sy * dz - sz * dy, uy = sz * dx - sx * dz, uz = sx * dy - sy * dx;
         float hw = width * 0.5F, hh = height * 0.5F;
         float[][] a = new float[4][], e = new float[4][];
         float[][] k = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
         for (int i = 0; i < 4; i++) {
            float ox = sx * hw * k[i][0] + ux * hh * k[i][1], oy = sy * hw * k[i][0] + uy * hh * k[i][1], oz = sz * hw * k[i][0] + uz * hh * k[i][1];
            a[i] = new float[]{ax + ox, ay + oy, az + oz};
            e[i] = new float[]{bx + ox, by + oy, bz + oz};
         }
         for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            float nx = (a[i][0] + a[j][0]) * 0.5F - ax, ny = (a[i][1] + a[j][1]) * 0.5F - ay, nz = (a[i][2] + a[j][2]) * 0.5F - az;
            this.quad(new float[][]{a[i], a[j], e[j], e[i]}, nx, ny, nz, u, v);
         }
         this.quad(new float[][]{a[0], a[1], a[2], a[3]}, -dx, -dy, -dz, u, v);
         this.quad(new float[][]{e[0], e[1], e[2], e[3]}, dx, dy, dz, u, v);
      }

      /** One quad (stand frame), wound to face along the given normal. */
      void quad(float[][] p, float nx, float ny, float nz, float u, float v) {
         float nl = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
         if (nl < 1.0E-6F) {
            return;
         }
         nx /= nl;
         ny /= nl;
         nz /= nl;
         float e1x = p[1][0] - p[0][0], e1y = p[1][1] - p[0][1], e1z = p[1][2] - p[0][2];
         float e2x = p[2][0] - p[0][0], e2y = p[2][1] - p[0][1], e2z = p[2][2] - p[0][2];
         float cx = e1y * e2z - e1z * e2y, cy = e1z * e2x - e1x * e2z, cz = e1x * e2y - e1y * e2x;
         if (cx * nx + cy * ny + cz * nz < 0) {
            p = new float[][]{p[0], p[3], p[2], p[1]};
         }
         float[] wn = turn(this.facing, nx, nz);
         QuadBakingVertexConsumer q = new QuadBakingVertexConsumer();
         q.setSprite(this.sprite);
         q.setDirection(Direction.getNearest(wn[0], ny, wn[1]));
         q.setShade(true);
         q.setHasAmbientOcclusion(true);
         q.setTintIndex(-1);
         float[][] corner = {{-0.08F, -0.08F}, {0.08F, -0.08F}, {0.08F, 0.08F}, {-0.08F, 0.08F}};
         for (int i = 0; i < 4; i++) {
            float[] w = toWorld(this.facing, p[i][0], p[i][2]);
            q.addVertex(w[0], p[i][1], w[1]);
            q.setColor(255, 255, 255, 255);
            q.setUv(this.sprite.getU((u + corner[i][0]) / 16.0F), this.sprite.getV((v + corner[i][1]) / 16.0F));
            q.setNormal(wn[0], ny, wn[1]);
         }
         this.quads.add(q.bakeQuad());
      }
   }
}
