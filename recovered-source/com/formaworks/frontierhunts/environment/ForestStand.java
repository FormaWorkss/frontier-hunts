package com.formaworks.frontierhunts.environment;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.expedition.ExpeditionContent;
import com.formaworks.frontierhunts.expedition.SettlementTreeSpace;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction.Plane;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public final class ForestStand extends Feature<NoneFeatureConfiguration> {
   private static final WildTrees.Kind[] GROVES = new WildTrees.Kind[]{
      WildTrees.Kind.PINE, WildTrees.Kind.FIR, WildTrees.Kind.ASPEN, WildTrees.Kind.BIRCH, WildTrees.Kind.MAPLE
   };

   public ForestStand() {
      super(NoneFeatureConfiguration.CODEC);
   }

   public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
      WorldGenLevel level = context.level();
      RandomSource random = context.random();
      BlockPos origin = context.origin();
      if (!(Boolean)HuntConfig.GENERATE_HUNTING_FORESTS.get()) {
         return false;
      } else {
         float thickness = Wildness.thickness(level, origin.getX(), origin.getZ());
         int trees = 0;
         int wanted = 2 + Math.round(thickness * 4.0F) + random.nextInt(2);
         int spread = 5 + random.nextInt(5);
         int spacing = Wildness.spacing(thickness);
         ArrayList<BlockPos> trunks = new ArrayList<>();
         if (plant(level, origin, pick(level, origin, random, 1.0F), random, 0.75F + random.nextFloat() * 0.25F, random.nextFloat() < 0.3F)) {
            trees++;
            trunks.add(origin);
         }

         for (int i = 0; i < wanted * 4 && trees < wanted; i++) {
            int dx = random.nextInt(spread * 2 + 1) - spread;
            int dz = random.nextInt(spread * 2 + 1) - spread;
            BlockPos at = ground(level, origin.offset(dx, 0, dz));
            if (at != null && !crowded(trunks, at, spacing)) {
               double edge = Math.sqrt((double)(dx * dx + dz * dz)) / (double)Math.max(1, spread);
               float age = (float)Mth.clamp(0.85 - edge * 0.62 + random.nextGaussian() * 0.12, 0.05, 1.0);
               WildTrees.Kind kind = pick(level, at, random, age);
               boolean emergent = age > 0.6F && random.nextFloat() < 0.18F;
               if (plant(level, at, kind, random, age, emergent)) {
                  trees++;
                  trunks.add(at);
               }
            }
         }

         if (random.nextFloat() < 0.55F) {
            BlockPos at = ground(level, origin.offset(random.nextInt(9) - 4, 0, random.nextInt(9) - 4));
            if (at != null) {
               plant(level, at, WildTrees.Kind.SNAG, random, random.nextFloat());
            }
         }

         if (random.nextFloat() < 0.45F) {
            BlockPos at = ground(level, origin.offset(random.nextInt(11) - 5, 0, random.nextInt(11) - 5));
            if (at != null) {
               plant(level, at, WildTrees.Kind.LEANER, random, random.nextFloat());
            }
         }

         if (random.nextFloat() < 0.3F) {
            BlockPos at = ground(level, origin.offset(random.nextInt(11) - 5, 0, random.nextInt(11) - 5));
            if (at != null) {
               plant(level, at, WildTrees.Kind.STUMP, random, 0.0F);
            }
         }

         return trees > 0;
      }
   }

   private static boolean crowded(List<BlockPos> trunks, BlockPos at, int spacing) {
      int min = spacing * spacing;

      for (BlockPos other : trunks) {
         int dx = other.getX() - at.getX();
         int dz = other.getZ() - at.getZ();
         if (dx * dx + dz * dz < min) {
            return true;
         }
      }

      return false;
   }

   private static WildTrees.Kind pick(WorldGenLevel level, BlockPos at, RandomSource random, float age) {
      if (wet(level, at) && random.nextFloat() < 0.55F) {
         return WildTrees.Kind.WILLOW;
      } else {
         WildTrees.Kind grove = GROVES[Wildness.grove(level, at.getX(), at.getZ(), GROVES.length)];
         float purity = Wildness.purity(level, at.getX(), at.getZ());
         if (random.nextFloat() < 0.3F + purity * 0.52F) {
            return grove;
         } else {
            float roll = random.nextFloat();
            if (age < 0.3F) {
               return roll < 0.32F ? WildTrees.Kind.BIRCH : (roll < 0.58F ? WildTrees.Kind.FIR : (roll < 0.82F ? WildTrees.Kind.PINE : WildTrees.Kind.MAPLE));
            } else {
               return roll < 0.4F
                  ? WildTrees.Kind.PINE
                  : (roll < 0.64F ? WildTrees.Kind.FIR : (roll < 0.78F ? WildTrees.Kind.ASPEN : (roll < 0.9F ? WildTrees.Kind.BIRCH : WildTrees.Kind.MAPLE)));
            }
         }
      }
   }

   private static boolean wet(WorldGenLevel level, BlockPos at) {
      for (int dx = -3; dx <= 3; dx += 3) {
         for (int dz = -3; dz <= 3; dz += 3) {
            BlockPos probe = at.offset(dx, -1, dz);
            if (level.hasChunkAt(probe) && Terrain.isWater(level, probe)) {
               return true;
            }
         }
      }

      return false;
   }

   private static BlockPos ground(WorldGenLevel level, BlockPos near) {
      return Terrain.soil(level, near);
   }

   static boolean writable(WorldGenLevel level, BlockPos at) {
      return Terrain.writable(level, at);
   }

   public static boolean plant(WorldGenLevel level, BlockPos origin, WildTrees.Kind kind, RandomSource random, float age) {
      return plant(level, origin, kind, random, age, false);
   }

   public static boolean plant(WorldGenLevel level, BlockPos origin, WildTrees.Kind kind, RandomSource random, float age, boolean emergent) {
      if (SettlementTreeSpace.reserved(level, origin)) {
         return false;
      } else if (!WildTrees.rooted(level, origin)) {
         return false;
      } else {
         boolean standing = kind != WildTrees.Kind.STUMP && kind != WildTrees.Kind.LEANER;
         if (standing) {
            int footing = 0;

            for (Direction side : Plane.HORIZONTAL) {
               if (level.getBlockState(origin.offset(side.getStepX(), -1, side.getStepZ())).isSolid()) {
                  footing++;
               }
            }

            if (footing < 2) {
               return false;
            }
         }

         for (int y = 1; y <= (standing ? 10 : 3); y++) {
            BlockPos above = origin.above(y);
            if (!writable(level, above) || !WildTrees.soft(level.getBlockState(above))) {
               return false;
            }
         }

         if (standing) {
            for (int sx = -1; sx <= 1; sx += 2) {
               for (int sz = -1; sz <= 1; sz += 2) {
                  if (!writable(level, origin.offset(sx * 7, 1, sz * 7))) {
                     return false;
                  }
               }
            }
         }

         WildTrees.Plan plan = WildTrees.of(kind, random, Mth.clamp(age, 0.0F, 1.0F), emergent);

         for (BlockPos at : plan.wood().keySet()) {
            BlockPos world = origin.offset(at);
            if (!writable(level, world)) {
               return false;
            }

            BlockState state = level.getBlockState(world);
            if (!WildTrees.soft(state) && !state.is(Blocks.WATER)) {
               return false;
            }
         }

         for (BlockPos at : plan.dressing().keySet()) {
            if (!writable(level, origin.offset(at))) {
               return false;
            }
         }

         for (Entry<BlockPos, BlockState> e : plan.dressing().entrySet()) {
            set(level, origin.offset((Vec3i)e.getKey()), e.getValue());
         }

         for (Entry<BlockPos, BlockState> e : plan.wood().entrySet()) {
            set(level, origin.offset((Vec3i)e.getKey()), e.getValue());
         }

         MutableBlockPos cursor = new MutableBlockPos();

         for (Entry<BlockPos, BlockState> e : plan.leaves().entrySet()) {
            BlockPos worldx = origin.offset((Vec3i)e.getKey());
            if (writable(level, worldx) && WildTrees.soft(level.getBlockState(worldx))) {
               BlockState state = e.getValue();
               if (state.hasProperty(WildLeaves.EDGE)) {
                  boolean edge = false;

                  for (Direction sidex : Direction.values()) {
                     if (sidex != Direction.DOWN) {
                        cursor.setWithOffset((Vec3i)e.getKey(), sidex);
                        if (!plan.leaves().containsKey(cursor) && !plan.wood().containsKey(cursor)) {
                           edge = true;
                           break;
                        }
                     }
                  }

                  state = (BlockState)state.setValue(WildLeaves.EDGE, edge);
               }

               set(level, worldx, state);
            }
         }

         needleDrop(level, origin, plan, random);
         return true;
      }
   }

   private static void needleDrop(WorldGenLevel level, BlockPos origin, WildTrees.Plan plan, RandomSource random) {
      int reach = Math.clamp((long)plan.radius(), 2, 6);
      ForestFloor.Litter litter = (ForestFloor.Litter)ExpeditionContent.FOREST_LITTER.get();

      for (int i = 0; i < reach * reach; i++) {
         int dx = random.nextInt(reach * 2 + 1) - reach;
         int dz = random.nextInt(reach * 2 + 1) - reach;
         if (dx * dx + dz * dz <= reach * reach) {
            BlockPos at = Terrain.soil(level, origin.offset(dx, 0, dz));
            if (at != null && level.getBlockState(at).isAir()) {
               set(level, at, (BlockState)litter.defaultBlockState().setValue(ForestFloor.Litter.VARIANT, random.nextInt(3)));
            }
         }
      }
   }

   private static void set(WorldGenLevel level, BlockPos at, BlockState state) {
      if (writable(level, at)) {
         level.setBlock(at, state, 4);
      }
   }
}
