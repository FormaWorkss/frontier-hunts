package com.formaworks.frontierhunts.client.tree;

import com.formaworks.frontierhunts.client.ShaderState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

public final class GroundWoodModel extends BakedModelWrapper<BakedModel> {
   private static final ChunkRenderTypeSet SOLID = ChunkRenderTypeSet.of(new RenderType[]{RenderType.solid()});
   private final List<BakedQuad> quads;
   private final List<BakedQuad> shaderQuads;

   public GroundWoodModel(BakedModel base, BlockState state, boolean fallen, TextureAtlasSprite bark, TextureAtlasSprite end) {
      super(base);
      int variant = 0;
      boolean snapped = false;

      for (Property<?> property : state.getProperties()) {
         if (property.getName().equals("variant")) {
            variant = ((Number)state.getValue(property)).intValue();
         }

         if (property.getName().equals("snapped")) {
            snapped = Boolean.TRUE.equals(state.getValue(property));
         }
      }

      Direction facing = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
         ? (Direction)state.getValue(BlockStateProperties.HORIZONTAL_FACING)
         : Direction.NORTH;
      float angle = (float)Math.toRadians((double)facing.toYRot());
      RandomSource random = RandomSource.create(9031L + (long)variant * 217L + (long)(fallen ? 771 : 0));
      List<BakedQuad> mesh = new ArrayList<>();
      int count = fallen ? 1 : 2 + variant % 2;

      for (int i = 0; i < count; i++) {
         float a = angle + (random.nextFloat() - 0.5F) * (fallen ? 0.35F : 1.8F);
         float dx = (float)Math.sin((double)a);
         float dz = (float)Math.cos((double)a);
         float length = fallen ? (snapped ? 0.69F : 0.93F) : 0.48F + random.nextFloat() * 0.34F;
         float radius = fallen ? 0.043F : 0.019F + random.nextFloat() * 0.009F;
         float cx = 0.5F + (random.nextFloat() - 0.5F) * 0.18F;
         float cz = 0.5F + (random.nextFloat() - 0.5F) * 0.18F;
         float x = cx - dx * length * 0.5F;
         float z = cz - dz * length * 0.5F;
         limb(mesh, x, z, x + dx * length, z + dz * length, radius, snapped ? 0.63F : 0.16F, bark, end);
         if (fallen || i == 0) {
            float fork = a + (variant % 2 == 0 ? 0.68F : -0.72F);
            float fx = x + dx * length * 0.54F;
            float fz = z + dz * length * 0.54F;
            limb(
               mesh,
               fx,
               fz,
               fx + (float)Math.sin((double)fork) * length * 0.34F,
               fz + (float)Math.cos((double)fork) * length * 0.34F,
               radius * 0.55F,
               0.1F,
               bark,
               end
            );
         }
      }

      this.quads = List.copyOf(mesh);
      this.shaderQuads = this.quads.stream().map(q -> {
         int[] vertices = (int[])q.getVertices().clone();

         for (int v = 0; v < 4; v++) {
            vertices[v * (vertices.length / 4) + 3] = -1;
         }

         return new BakedQuad(vertices, q.getTintIndex(), q.getDirection(), q.getSprite(), false, false);
      }).toList();
   }

   private static void limb(
      List<BakedQuad> out, float x0, float z0, float x1, float z1, float radius, float tip, TextureAtlasSprite bark, TextureAtlasSprite end
   ) {
      float dx = x1 - x0;
      float dz = z1 - z0;
      float length = (float)Math.sqrt((double)(dx * dx + dz * dz));
      dx /= length;
      dz /= length;
      float[][] previous = null;
      int sides = 7;
      int bands = 3;

      for (int band = 0; band <= 3; band++) {
         float t = (float)band / 3.0F;
         float r = radius * (1.0F - t * (1.0F - tip));
         float cx = x0 + (x1 - x0) * t - dz * 0.018F * (float)Math.sin(Math.PI * (double)t);
         float cz = z0 + (z1 - z0) * t + dx * 0.018F * (float)Math.sin(Math.PI * (double)t);
         float cy = r - 0.003F;
         float[][] ring = new float[7][3];

         for (int k = 0; k < 7; k++) {
            float a = (float)((double)k * Math.PI * 2.0 / 7.0);
            ring[k] = new float[]{cx - dz * r * (float)Math.sin((double)a), cy + r * (float)Math.cos((double)a), cz + dx * r * (float)Math.sin((double)a)};
         }

         if (previous != null) {
            for (int k = 0; k < 7; k++) {
               float a = (float)(((double)k + 0.5) * Math.PI * 2.0 / 7.0);
               out.add(
                  TrunkModel.bakeBark(
                     BranchStubModel.quad(
                        previous[k],
                        ring[k],
                        ring[(k + 1) % 7],
                        previous[(k + 1) % 7],
                        new float[]{-dz * (float)Math.sin((double)a), (float)Math.cos((double)a), dx * (float)Math.sin((double)a)},
                        0
                     ),
                     bark
                  )
               );
            }
         }

         if (band == 0 || band == 3) {
            for (int k = 0; k < 7; k++) {
               float sign = band == 0 ? -1.0F : 1.0F;
               float[] centre = new float[]{cx, cy, cz};
               out.add(TrunkModel.bakeBark(BranchStubModel.quad(centre, ring[k], ring[(k + 1) % 7], centre, new float[]{dx * sign, 0.0F, dz * sign}, 0), end));
            }
         }

         previous = ring;
      }
   }

   public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data, RenderType type) {
      if (state == null) {
         return this.originalModel.getQuads(state, side, random, data, type);
      } else {
         return side == null && (type == null || type == RenderType.solid()) ? (ShaderState.on ? this.shaderQuads : this.quads) : List.of();
      }
   }

   public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
      return SOLID;
   }
}
