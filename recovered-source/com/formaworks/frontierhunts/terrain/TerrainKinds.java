package com.formaworks.frontierhunts.terrain;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class TerrainKinds {
   private static volatile Map<Block, Integer> natural;
   private static volatile byte[] cache = new byte[0];
   private static final Set<String> SOFT = Set.of(
      "grass_block",
      "dirt",
      "coarse_dirt",
      "podzol",
      "rooted_dirt",
      "mycelium",
      "moss_block",
      "mud",
      "clay",
      "gravel",
      "sand",
      "red_sand",
      "snow_block",
      "soul_soil",
      "soul_sand",
      "suspicious_sand",
      "suspicious_gravel",
      "forest_duff",
      "forest_loam",
      "moss_floor",
      "alpine_turf",
      "alpine_pasture"
   );
   private static final Set<String> ROCK = Set.of(
      "stone",
      "granite",
      "diorite",
      "andesite",
      "deepslate",
      "tuff",
      "calcite",
      "dripstone_block",
      "sandstone",
      "red_sandstone",
      "terracotta",
      "smooth_basalt",
      "netherrack",
      "end_stone",
      "infested_stone",
      "infested_deepslate",
      "bedrock",
      "alpine_rock",
      "mossy_stone",
      "reserve_granite",
      "mossy_outcrop",
      "river_outcrop",
      "weathered_river_rock",
      "fieldstone"
   );

   private TerrainKinds() {
   }

   public static int naturalKind(Block b) {
      Map<Block, Integer> m = natural;
      if (m == null) {
         m = build();
      }

      Integer k = m.get(b);
      return k == null ? 0 : k;
   }

   private static synchronized Map<Block, Integer> build() {
      if (natural != null) {
         return natural;
      } else {
         Map<Block, Integer> m = new IdentityHashMap<>();

         for (Block b : BuiltInRegistries.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(b);
            int k = kindOf(id.getPath());
            if (k != 0) {
               m.put(b, k);
            }
         }

         natural = m;
         return m;
      }
   }

   public static int kindOf(String p) {
      if (SOFT.contains(p)) {
         return 1;
      } else if (ROCK.contains(p)) {
         return 2;
      } else {
         String[] built = new String[]{
            "brick",
            "slab",
            "stairs",
            "wall",
            "polished",
            "chiseled",
            "cut_",
            "tile",
            "pillar",
            "planks",
            "glazed",
            "concrete",
            "path",
            "farmland",
            "button",
            "pressure",
            "carved",
            "cobble",
            "smooth_",
            "fence",
            "door",
            "lamp",
            "glass",
            "carpet",
            "powder",
            "block_of"
         };

         for (String w : built) {
            if (p.contains(w)) {
               return 0;
            }
         }

         if (!p.endsWith("_ore") && !p.endsWith("_terracotta")) {
            return !p.endsWith("_dirt")
                  && !p.endsWith("_soil")
                  && !p.endsWith("_loam")
                  && !p.endsWith("_turf")
                  && !p.endsWith("_duff")
                  && !p.endsWith("_gravel")
                  && (!p.endsWith("_sand") || p.contains("sandstone"))
               ? 0
               : 1;
         } else {
            return 2;
         }
      }
   }

   public static boolean low(BlockState s) {
      try {
         if (s.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
            return false;
         } else {
            VoxelShape shape = s.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            return shape.isEmpty() || shape.max(Axis.Y) <= 0.5;
         }
      } catch (RuntimeException var2) {
         return false;
      }
   }

   public static int classify(BlockState s) {
      int id = Block.getId(s);
      byte[] c = cache;
      if (id >= 0 && id < c.length && c[id] != 0) {
         return c[id] - 1;
      } else {
         int k = slow(s);
         if (id >= 0) {
            if (id >= c.length) {
               byte[] n = new byte[Math.max(id + 1, Block.BLOCK_STATE_REGISTRY.size())];
               System.arraycopy(c, 0, n, 0, c.length);
               c = n;
               cache = n;
            }

            c[id] = (byte)(k + 1);
         }

         return k;
      }
   }

   private static int slow(BlockState s) {
      if (s.isAir()) {
         return 0;
      } else {
         Block b = s.getBlock();
         if (b instanceof LiquidBlock) {
            return 4;
         } else if (b instanceof SnowLayerBlock) {
            return 0;
         } else {
            int nat = naturalKind(b);
            if (nat != 0 && s.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
               return nat;
            } else if (b instanceof LeavesBlock) {
               return 3;
            } else if (low(s) || nat != 0) {
               return 0;
            } else if (s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()) {
               return s.getFluidState().isEmpty() ? 0 : 4;
            } else {
               return 3;
            }
         }
      }
   }
}
