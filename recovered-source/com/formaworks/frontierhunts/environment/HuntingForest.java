package com.formaworks.frontierhunts.environment;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.HuntRules;
import com.formaworks.frontierhunts.expedition.ReserveArchitecture;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.Direction.Plane;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class HuntingForest extends Feature<NoneFeatureConfiguration> {
   private static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, "frontierhunts");
   public static final DeferredHolder<Feature<?>, HuntingForest> TREE = FEATURES.register("hunting_tree", HuntingForest::new);
   public static final DeferredHolder<Feature<?>, ForestStand> STAND = FEATURES.register("forest_stand", ForestStand::new);
   public static final DeferredHolder<Feature<?>, Cascade> CASCADE = FEATURES.register("cascade", Cascade::new);
   public static final DeferredHolder<Feature<?>, CreekEdge> CREEK = FEATURES.register("creek_edge", CreekEdge::new);
   public static final DeferredHolder<Feature<?>, Landmark> LANDMARK = FEATURES.register("landmark", Landmark::new);
   public static final DeferredHolder<Feature<?>, GroundCover> COVER = FEATURES.register("ground_cover", GroundCover::new);
   public static final DeferredHolder<Feature<?>, MeadowGrass> MEADOW = FEATURES.register("meadow_grass", MeadowGrass::new);

   public static void register(IEventBus var0) {
      FEATURES.register(var0);
   }

   public HuntingForest() {
      super(NoneFeatureConfiguration.CODEC);
   }

   public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> var1) {
      WorldGenLevel var2 = var1.level();
      ServerLevel var3 = var2.getLevel();
      RandomSource var4 = var1.random();
      if (!(Boolean)HuntConfig.GENERATE_HUNTING_FORESTS.get() || !var3.dimension().equals(Level.OVERWORLD)) {
         return false;
      } else if (!(Boolean)HuntConfig.FORESTS_OUTSIDE_RESERVE.get() && !var3.dimensionTypeRegistration().is(HuntRules.RESERVE_TYPE) && !HuntRules.active(var3)) {
         return false;
      } else {
         return var4.nextInt(6) >= HuntConfig.FOREST_DENSITY.get() ? false : plant(var2, var1.origin(), plan(var4));
      }
   }

   public static HuntingForest.Plan plan(RandomSource var0) {
      boolean var1 = var0.nextInt(4) == 0;
      int var2 = var1 ? 9 + var0.nextInt(7) : 17 + var0.nextInt(11);
      int var3 = var1 ? 3 + var0.nextInt(3) : 8 + var0.nextInt(4);
      BlockState var4 = ((Block)ReserveArchitecture.BLOCKS.get(var1 ? "cedar_log" : "pine_log").get()).defaultBlockState();
      LinkedHashMap var5 = new LinkedHashMap();
      LinkedHashSet var6 = new LinkedHashSet();

      for (int var7 = 0; var7 < var2; var7++) {
         var5.put(new BlockPos(0, var7, 0), var4);
      }

      for (int var17 = var3; var17 < var2; var17 += 2) {
         double var8 = (double)(var2 - var17) / (double)(var2 - var3);
         int var10 = Math.max(1, (int)Math.round((var1 ? 3.0 : 4.0) * Math.sqrt(var8)));

         for (Direction var12 : Plane.HORIZONTAL) {
            int var13 = Math.max(1, var10 - var0.nextInt(2));
            if (var17 <= var3 || var0.nextInt(8) != 0) {
               for (int var14 = 1; var14 <= var13; var14++) {
                  BlockPos var15 = new BlockPos(var12.getStepX() * var14, var17 + (var14 == var13 && var0.nextBoolean() ? 1 : 0), var12.getStepZ() * var14);
                  BlockPos var16 = new BlockPos(var15.getX(), var17, var15.getZ());
                  var5.put(var16, (BlockState)var4.setValue(RotatedPillarBlock.AXIS, var12.getAxis()));
                  if (var15.getY() != var17) {
                     var5.put(var15, var4);
                  }

                  if (var14 >= Math.max(1, var13 - 1)) {
                     cluster(var6, var15, 1 + var0.nextInt(2), var0);
                  }
               }
            }
         }

         cluster(var6, new BlockPos(0, var17 + 1, 0), Math.max(1, var10 - 1), var0);
      }

      cluster(var6, new BlockPos(0, var2, 0), 1, var0);
      var6.removeAll(var5.keySet());
      HashMap var18 = new HashMap();
      ArrayDeque var19 = new ArrayDeque();

      for (BlockPos var22 : var5.keySet()) {
         for (Direction var33 : Direction.values()) {
            BlockPos var35 = var22.relative(var33);
            if (var6.contains(var35) && var18.putIfAbsent(var35, 1) == null) {
               var19.add(var35);
            }
         }
      }

      while (!var19.isEmpty()) {
         BlockPos var20 = (BlockPos)var19.remove();
         int var23 = (Integer)var18.get(var20) + 1;
         if (var23 <= 6) {
            for (Direction var34 : Direction.values()) {
               BlockPos var36 = var20.relative(var34);
               if (var6.contains(var36) && var18.putIfAbsent(var36, var23) == null) {
                  var19.add(var36);
               }
            }
         }
      }

      LinkedHashMap var21 = new LinkedHashMap();
      BlockState var24 = Blocks.SPRUCE_LEAVES.defaultBlockState();

      for (BlockPos var30 : var6) {
         if (var18.containsKey(var30)) {
            var21.put(var30, (BlockState)var24.setValue(LeavesBlock.DISTANCE, (Integer)var18.get(var30)));
         }
      }

      return new HuntingForest.Plan(Collections.unmodifiableMap(var5), Collections.unmodifiableMap(var21), var2, var1);
   }

   private static void cluster(Set<BlockPos> var0, BlockPos var1, int var2, RandomSource var3) {
      for (int var4 = -var2; var4 <= var2; var4++) {
         for (int var5 = -var2; var5 <= var2; var5++) {
            for (int var6 = -1; var6 <= 1; var6++) {
               double var7 = (double)(var4 * var4 + var5 * var5) / ((double)(var2 * var2) + 0.5) + (double)Math.abs(var6) * 0.65;
               if (!(var7 > 1.5) && (!(var7 > 1.0) || var3.nextInt(3) != 0)) {
                  var0.add(var1.offset(var4, var6, var5));
               }
            }
         }
      }
   }

   private static boolean available(WorldGenLevel var0, BlockPos var1) {
      if (!var0.isOutsideBuildHeight(var1) && var0.getLevel().getWorldBorder().isWithinBounds(var1)) {
         if (var0 instanceof WorldGenRegion var2) {
            ChunkPos var3 = var2.getCenter();
            if (Math.abs((var1.getX() >> 4) - var3.x) > 1 || Math.abs((var1.getZ() >> 4) - var3.z) > 1) {
               return false;
            }
         }

         return var0.hasChunkAt(var1) && var0.ensureCanWrite(var1);
      } else {
         return false;
      }
   }

   private static boolean soft(BlockState var0) {
      return var0.isAir() || var0.is(BlockTags.LEAVES) || var0.is(BlockTags.REPLACEABLE_BY_TREES);
   }

   public static boolean plant(WorldGenLevel var0, BlockPos var1, HuntingForest.Plan var2) {
      if (available(var0, var1.below()) && var0.getBlockState(var1.below()).is(BlockTags.DIRT)) {
         for (int var3 = -1; var3 <= 1; var3++) {
            for (int var4 = -1; var4 <= 1; var4++) {
               BlockPos var5 = var1.offset(var3, -1, var4);
               if (!available(var0, var5) || !var0.getBlockState(var5).is(BlockTags.DIRT)) {
                  return false;
               }

               for (int var6 = 0; var6 < 3; var6++) {
                  BlockPos var7 = var1.offset(var3, var6, var4);
                  if (!available(var0, var7) || !soft(var0.getBlockState(var7)) || !var0.getFluidState(var7).isEmpty()) {
                     return false;
                  }
               }
            }
         }

         LinkedHashMap var8 = new LinkedHashMap();

         for (Entry var12 : var2.wood.entrySet()) {
            BlockPos var15 = var1.offset((Vec3i)var12.getKey());
            if (!available(var0, var15) || !soft(var0.getBlockState(var15)) || !var0.getFluidState(var15).isEmpty()) {
               return false;
            }

            var8.put(var15, (BlockState)var12.getValue());
         }

         for (Entry var13 : var2.leaves.entrySet()) {
            BlockPos var16 = var1.offset((Vec3i)var13.getKey());
            if (!available(var0, var16) || !var0.getFluidState(var16).isEmpty()) {
               return false;
            }

            BlockState var17 = var0.getBlockState(var16);
            if (!var17.is(BlockTags.LEAVES)) {
               if (!soft(var17)) {
                  return false;
               }

               var8.put(var16, (BlockState)var13.getValue());
            }
         }

         for (Entry var14 : var8.entrySet()) {
            var0.setBlock((BlockPos)var14.getKey(), (BlockState)var14.getValue(), 18);
         }

         return true;
      } else {
         return false;
      }
   }

   public static record Plan(Map<BlockPos, BlockState> wood, Map<BlockPos, BlockState> leaves, int height, boolean cedar) {
   }
}
