package com.formaworks.frontierhunts.client.tree;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

public final class BranchStubModel extends BakedModelWrapper<BakedModel> {
   private static final ModelProperty<List<BakedQuad>> QUADS = new ModelProperty();
   private static final ChunkRenderTypeSet SOLID = ChunkRenderTypeSet.of(new RenderType[]{RenderType.solid()});

   public BranchStubModel(BakedModel base) {
      super(base);
   }

   public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
      data = this.originalModel.getModelData(level, pos, state, data);
      if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
         return data;
      } else {
         Direction direction = (Direction)state.getValue(BlockStateProperties.HORIZONTAL_FACING);
         BlockPos parent = pos.relative(direction.getOpposite());
         BlockState trunk = level.getBlockState(parent);
         TextureAtlasSprite bark = Minecraft.getInstance().getBlockRenderer().getBlockModel(trunk).getParticleIcon();
         TreeGrowth.Tree tree = TreeGrowth.lookup(LiveWorld.INSTANCE, parent.getX(), parent.getY(), parent.getZ());
         float[] socket = tree == null ? null : tree.sockets.get(parent.asLong());
         float dx = (float)direction.getStepX();
         float dz = (float)direction.getStepZ();
         float x = socket == null ? 0.5F - dx : socket[0] - (float)pos.getX();
         float y = socket == null ? 0.5F : socket[1] - (float)pos.getY();
         float z = socket == null ? 0.5F - dz : socket[2] - (float)pos.getZ();
         float trunkRadius = socket == null ? 0.4F : socket[3];
         long seed = TreeShape.mix(pos.asLong());
         float reach = trunkRadius + 0.55F + (float)(seed & 255L) / 255.0F * 0.45F;
         float radius = Math.min(0.105F, trunkRadius * 0.27F);
         int light = LiveWorld.INSTANCE.light(pos.getX(), pos.getY(), pos.getZ());
         List<BakedQuad> quads = new ArrayList<>();
         float[][] previous = null;
         int sides = 7;
         int steps = 4;

         for (int s = 0; s <= 4; s++) {
            float t = (float)s / 4.0F;
            float r = radius * (1.0F - 0.91F * t);
            float cx = x + dx * reach * t;
            float cy = y + 0.16F * (float)Math.sin((double)t * Math.PI) - 0.15F * t;
            float cz = z + dz * reach * t;
            float[][] ring = new float[7][3];

            for (int k = 0; k < 7; k++) {
               float a = (float)((double)k * Math.PI * 2.0 / 7.0);
               float u = (float)Math.cos((double)a);
               float v = (float)Math.sin((double)a);
               ring[k] = new float[]{cx - dz * r * v, cy + r * u, cz + dx * r * v};
            }

            if (previous != null) {
               for (int k = 0; k < 7; k++) {
                  int j = (k + 1) % 7;
                  float a = (float)(((double)k + 0.5) * Math.PI * 2.0 / 7.0);
                  float[] normal = new float[]{-dz * (float)Math.sin((double)a), (float)Math.cos((double)a), dx * (float)Math.sin((double)a)};
                  TreeShape.Quad quad = quad(previous[k], ring[k], ring[j], previous[j], normal, light);
                  quads.add(TrunkModel.bakeBark(quad, bark));
               }
            }

            if (s == 4) {
               for (int k = 0; k < 7; k++) {
                  float[] centre = new float[]{cx, cy, cz};
                  TreeShape.Quad cap = quad(centre, ring[k], ring[(k + 1) % 7], centre, new float[]{dx, 0.0F, dz}, light);
                  quads.add(TrunkModel.bakeBark(cap, bark));
               }
            }

            previous = ring;
         }

         return data.derive().with(QUADS, List.copyOf(quads)).build();
      }
   }

   static TreeShape.Quad quad(float[] a, float[] b, float[] c, float[] d, float[] normal, int light) {
      TreeShape.Quad q = new TreeShape.Quad();
      float[] ab = new float[]{b[0] - a[0], b[1] - a[1], b[2] - a[2]};
      float[] ac = new float[]{c[0] - a[0], c[1] - a[1], c[2] - a[2]};
      float dot = (ab[1] * ac[2] - ab[2] * ac[1]) * normal[0] + (ab[2] * ac[0] - ab[0] * ac[2]) * normal[1] + (ab[0] * ac[1] - ab[1] * ac[0]) * normal[2];
      if (dot < 0.0F) {
         float[] swap = b;
         b = d;
         d = swap;
      }

      float[][] points = new float[][]{a, b, c, d};

      for (int i = 0; i < 4; i++) {
         int offset = i * 8;
         System.arraycopy(points[i], 0, q.v, offset, 3);
         q.v[offset + 3] = i != 2 && i != 3 ? 0.0F : 0.35F;
         q.v[offset + 4] = i != 1 && i != 2 ? 0.0F : 0.4F;
         System.arraycopy(normal, 0, q.v, offset + 5, 3);
      }

      q.nx = normal[0];
      q.ny = normal[1];
      q.nz = normal[2];
      q.light = light;
      return q;
   }

   public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data, RenderType type) {
      List<BakedQuad> quads = (List<BakedQuad>)data.get(QUADS);
      return quads == null
         ? this.originalModel.getQuads(state, side, random, data, type)
         : (side != null || type != null && type != RenderType.solid() ? List.of() : quads);
   }

   public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
      return data.get(QUADS) == null ? this.originalModel.getRenderTypes(state, random, data) : SOLID;
   }
}
