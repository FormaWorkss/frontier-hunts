package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.FrontierHunts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/**
 * [1.1.6] Grass and brush that an animal pushed through lean over in the direction it went, and grass where an animal
 * went down lies pressed flat ({@link PassageField}). The plant's own quads are rotated about its base - nothing is
 * removed or replaced, so the block, its drops and its cover value are unchanged, and it slowly stands back up.
 * Woody shrubs only give a little. Wraps the baked models last, on top of any other Frontier model wrapper.
 */
public final class PassageModel extends BakedModelWrapper<BakedModel> {
   static final ModelProperty<float[]> BEND = new ModelProperty<>();
   /** grasses, ferns and soft plants: lie right over */
   static final Set<String> SOFT = Set.of("reeds", "alpine_pasture", "undergrowth", "spreading_fern", "arching_fern", "fireweed", "heather", "cottongrass");
   /** woody shrubs: only give a little */
   static final Set<String> WOODY = Set.of("woodland_bush", "river_brush", "flowering_bramble", "broadleaf_thicket", "huckleberry_shrub", "juniper_shrub",
      "spruce_seedling", "sagebrush");

   private final float maxAngle;
   private final float base;

   PassageModel(BakedModel original, float maxAngle, float base) {
      super(original);
      this.maxAngle = maxAngle;
      this.base = base;
   }

   @EventBusSubscriber(modid = FrontierHunts.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
   public static final class Registration {
      private Registration() {
      }

      @SubscribeEvent(priority = EventPriority.LOWEST)
      public static void wrap(ModelEvent.ModifyBakingResult event) {
         Map<ModelResourceLocation, BakedModel> models = event.getModels();
         float soft = (float)Math.toRadians(85.0), woody = (float)Math.toRadians(22.0);
         for (Block b : new Block[]{Blocks.SHORT_GRASS, Blocks.TALL_GRASS, Blocks.FERN, Blocks.LARGE_FERN}) {
            add(models, b, soft);
         }
         for (String n : SOFT) {
            add(models, BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(FrontierHunts.ID, n)), soft);
         }
         for (String n : WOODY) {
            add(models, BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(FrontierHunts.ID, n)), woody);
         }
      }

      private static void add(Map<ModelResourceLocation, BakedModel> models, Block block, float max) {
         if (block == null || block == Blocks.AIR) {
            return;
         }
         for (BlockState s : block.getStateDefinition().getPossibleStates()) {
            ModelResourceLocation key = BlockModelShaper.stateToModelLocation(s);
            BakedModel m = models.get(key);
            if (m != null && !(m instanceof PassageModel)) {
               boolean upper = s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER;
               models.put(key, new PassageModel(m, max, upper ? 1.0F : 0.0F));
            }
         }
      }
   }

   @Override
   public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
      data = this.originalModel.getModelData(level, pos, state, data);
      float[] bend = this.bendAt(pos);
      return bend == null ? data : data.derive().with(BEND, bend).build();
   }

   /** lean direction and angle for the plant at {@code pos}, with a little variety per plant; null if it stands straight */
   float[] bendAt(BlockPos pos) {
      PassageField.Bend b = PassageField.at(pos);
      if (b == null) {
         return null;
      }
      // a little variety per plant: lean and direction
      long h = pos.getX() * 3129871L ^ pos.getZ() * 116129781L;
      h = h * h * 42317861L + h * 11L;
      float j1 = ((h >> 16) & 255) / 255.0F, j2 = ((h >> 24) & 255) / 255.0F;
      float angle = Math.min(this.maxAngle, b.angle() * (0.85F + 0.3F * j1));
      double rot = Math.toRadians((j2 - 0.5) * 24.0);
      float dx = (float)(b.dx() * Math.cos(rot) - b.dz() * Math.sin(rot)), dz = (float)(b.dx() * Math.sin(rot) + b.dz() * Math.cos(rot));
      return new float[]{dx, dz, angle};
   }

   @Override
   public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData data, RenderType type) {
      // [1.1.8] renderers that skip ModelData (Sodium): find the plant from the random it was seeded with, before the
      // wrapped model draws from it
      float[] bend = data.get(BEND);
      if (bend == null && rand != null) {
         BlockPos p = PassageField.posFor(rand);
         if (p != null) {
            bend = this.bendAt(p);
         }
      }
      List<BakedQuad> quads = this.originalModel.getQuads(state, side, rand, data, type);
      if (bend == null || bend[2] < 0.01F || quads.isEmpty()) {
         return quads;
      }
      try {
         List<BakedQuad> out = new ArrayList<>(quads.size());
         for (BakedQuad q : quads) {
            out.add(bend(q, bend[0], bend[1], bend[2], this.base));
         }
         return out;
      } catch (RuntimeException e) {
         return quads;
      }
   }

   /** [1.1.8] the plain three-argument call (an outer wrapper may use it): the same bend, found from the random */
   @Override
   public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand) {
      return this.getQuads(state, side, rand, ModelData.EMPTY, null);
   }

   /** rotate a quad about the plant's base, leaning it towards (dx, dz) by angle */
   static BakedQuad bend(BakedQuad q, float dx, float dz, float angle, float base) {
      int[] v = q.getVertices().clone();
      int stride = v.length / 4;
      float c = (float)Math.cos(angle), s = (float)Math.sin(angle);
      for (int i = 0; i < 4; i++) {
         int o = i * stride;
         float x = Float.intBitsToFloat(v[o]) - 0.5F, y = Float.intBitsToFloat(v[o + 1]) + base, z = Float.intBitsToFloat(v[o + 2]) - 0.5F;
         float along = x * dx + z * dz;
         float px = x - along * dx, pz = z - along * dz; // part across the lean, unchanged
         float a2 = along * c + y * s;
         float y2 = Math.max(0.02F + base * 0.0F, -along * s + y * c);
         v[o] = Float.floatToRawIntBits(0.5F + px + a2 * dx);
         v[o + 1] = Float.floatToRawIntBits(y2 - base);
         v[o + 2] = Float.floatToRawIntBits(0.5F + pz + a2 * dz);
      }
      return new BakedQuad(v, q.getTintIndex(), q.getDirection(), q.getSprite(), q.isShade(), q.hasAmbientOcclusion());
   }
}
