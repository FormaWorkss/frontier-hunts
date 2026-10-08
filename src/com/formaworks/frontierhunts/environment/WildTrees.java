package com.formaworks.frontierhunts.environment;

import com.formaworks.frontierhunts.expedition.ExpeditionContent;
import com.formaworks.frontierhunts.expedition.ReserveArchitecture;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.Plane;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class WildTrees {
   private static final float EMERGENT = 1.42F;
   // [wood] ids the reserve does not register (alpine_maple_log) come from the block registry
   public static volatile Function<String, BlockState> woodPalette = id -> {
      net.neoforged.neoforge.registries.DeferredBlock<? extends Block> reserve = ReserveArchitecture.BLOCKS.get(id);
      return reserve != null
         ? ((Block)reserve.get()).defaultBlockState()
         : net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("frontierhunts", id)).defaultBlockState();
   };
   public static volatile Function<String, BlockState> leafPalette = id -> (BlockState)((LeavesBlock)ExpeditionContent.FOLIAGE.get(id).get())
         .defaultBlockState()
         .setValue(LeavesBlock.PERSISTENT, false);
   public static volatile Supplier<BlockState> rootPalette = () -> ((ForestFloor.Boulder)ExpeditionContent.MOSSY_STONE.get()).defaultBlockState();

   private static BlockState log(String id) {
      return woodPalette.apply(id);
   }

   private static BlockState leaf(String id) {
      return leafPalette.apply(id);
   }

   public static WildTrees.Plan of(WildTrees.Kind kind, RandomSource random, float age) {
      return of(kind, random, age, false);
   }

   public static WildTrees.Plan of(WildTrees.Kind kind, RandomSource random, float age, boolean emergent) {
      float scale = emergent ? 1.42F : 1.0F;
      // [1.1.1] stands of very different heights: each tree draws its own size (0.78x .. 1.63x, skewed so most are a
      // little above average and a few tower over the canopy), so a forest reads as a ragged real skyline from afar
      if (kind != WildTrees.Kind.SNAG && kind != WildTrees.Kind.LEANER && kind != WildTrees.Kind.STUMP) {
         float r = random.nextFloat();
         scale *= 0.78F + 0.85F * (float)Math.pow(r, 1.5);
         // and many stands are older than the land's base age: a share of trees are mature or old growth
         age = Mth.clamp(age + (1.0F - age) * (float)Math.pow(random.nextFloat(), 1.8) * 0.85F, 0.0F, 1.0F);
      }

      return switch (kind) {
         case PINE -> conifer(random, age, true, scale);
         case FIR -> conifer(random, age, false, scale);
         case ASPEN -> broadleaf(random, age, scale, WildTrees.Broadleaf.ASPEN);
         case BIRCH -> broadleaf(random, age, scale, WildTrees.Broadleaf.BIRCH);
         case MAPLE -> broadleaf(random, age, scale, WildTrees.Broadleaf.MAPLE);
         case WILLOW -> broadleaf(random, age, scale, WildTrees.Broadleaf.WILLOW);
         case SNAG -> snag(random, age);
         case LEANER -> leaner(random, age);
         case STUMP -> stump(random);
      };
   }

   private static WildTrees.Plan conifer(RandomSource random, float age, boolean pine, float scale) {
      LinkedHashMap<BlockPos, BlockState> wood = new LinkedHashMap<>();
      LinkedHashSet<BlockPos> foliage = new LinkedHashSet<>();
      LinkedHashMap<BlockPos, BlockState> dressing = new LinkedHashMap<>();
      int height = Math.round(
         scale * (float)(pine ? Math.round(Mth.lerp(age, 18.0F, 46.0F)) + random.nextInt(8) : Math.round(Mth.lerp(age, 15.0F, 34.0F)) + random.nextInt(7))
      );
      height = Math.min(height, pine ? 62 : 52); // [1.1.1] even the giants stay believable
      BlockState trunk = log(pine ? "pine_log" : "cedar_log");
      BlockState needles = leaf(pine ? "pine_needles" : "fir_needles");
      int live = pine ? (int)((float)height * Mth.lerp(age, 0.3F, 0.52F)) : (int)((float)height * Mth.lerp(age, 0.14F, 0.28F));
      int maxSpread = Math.round(scale * (pine ? Mth.lerp(age, 3.0F, 5.8F) : Mth.lerp(age, 2.6F, 4.4F)));

      for (int y = 0; y < height; y++) {
         wood.put(new BlockPos(0, y, 0), trunk);
      }

      if (age > 0.6F) {
         buttress(wood, trunk, random, age);
      }

      int radius = 1;

      for (int y = live; y < height; y++) {
         double up = (double)(y - live) / (double)Math.max(1, height - live);
         double profile = Math.pow(Math.max(0.0, 1.0 - up), 0.62);
         int spread = (int)Math.round((double)maxSpread * profile);
         if (spread <= 0) {
            cluster(foliage, new BlockPos(0, y, 0), y >= height - 2 ? 0 : 1, random, 0.0);
         } else {
            radius = Math.max(radius, spread + 1);
            boolean whorl = (y - live) % 2 == 0;

            for (Direction side : Plane.HORIZONTAL) {
               for (int diag = 0; diag < 2; diag++) {
                  if ((diag != 1 || whorl && spread >= 3) && (whorl || random.nextInt(3) == 0)) {
                     int dx = side.getStepX();
                     int dz = side.getStepZ();
                     if (diag == 1) {
                        dx += side.getClockWise().getStepX();
                        dz += side.getClockWise().getStepZ();
                     }

                     int length = Math.max(1, spread - random.nextInt(2));
                     BlockPos previous = new BlockPos(0, y, 0);

                     for (int d = 1; d <= length; d++) {
                        int drop = d >= 3 ? -1 : 0;
                        if (d == length && length >= 3) {
                           drop++;
                        }

                        BlockPos at = new BlockPos(dx * d, y + drop, dz * d);
                        BlockPos branch = new BlockPos(dx * d, y, dz * d);
                        connect(wood, previous, branch, trunk);
                        previous = branch;
                        if (drop != 0) {
                           wood.put(at, trunk);
                        }

                        if (d >= Math.max(1, length - 2)) {
                           cluster(foliage, at, 1 + (d == length ? 0 : 1), random, 0.18);
                        }
                     }
                  }
               }
            }

            cluster(foliage, new BlockPos(0, y, 0), Math.max(1, spread - 1), random, 0.12);
         }
      }

      cluster(foliage, new BlockPos(0, height, 0), 1, random, 0.0);
      if (age > 0.55F) {
         for (int n = 0; n < 2 + random.nextInt(3); n++) {
            Direction side = Plane.HORIZONTAL.getRandomDirection(random);
            int yx = 2 + random.nextInt(Math.max(2, live - 1));
            wood.put(new BlockPos(side.getStepX(), yx, side.getStepZ()), (BlockState)trunk.setValue(RotatedPillarBlock.AXIS, side.getAxis()));
         }
      }

      foliage.removeAll(wood.keySet());
      return new WildTrees.Plan(Map.copyOf(wood), decay(wood.keySet(), foliage, needles), Map.copyOf(dressing), height, radius);
   }

   private static WildTrees.Plan broadleaf(RandomSource random, float age, float scale, WildTrees.Broadleaf kind) {
      LinkedHashMap<BlockPos, BlockState> wood = new LinkedHashMap<>();
      LinkedHashSet<BlockPos> foliage = new LinkedHashSet<>();
      int height = Math.min(44, Math.round(scale * (float)(Math.round(Mth.lerp(age, (float)kind.low, (float)kind.high)) + random.nextInt(4))));
      BlockState trunk = log(kind.log);
      BlockState leaves = leaf(kind.leaf);
      int fork = Math.max(2, (int)((float)height * (kind.fork + random.nextFloat() * 0.1F)));
      int rise = Math.max(3, height - fork);

      for (int y = 0; y < fork; y++) {
         wood.put(new BlockPos(0, y, 0), trunk);
      }

      int leader = Math.min(height - 1, fork + (int)((float)rise * (0.74F + random.nextFloat() * 0.14F)));

      for (int y = fork; y <= leader; y++) {
         wood.put(new BlockPos(0, y, 0), trunk);
      }

      if (age > 0.5F) {
         buttress(wood, trunk, random, age);
      }

      int limbs = kind.limbs + random.nextInt(age > 0.6F ? 3 : 2);
      double start = random.nextDouble() * Math.PI * 2.0;
      int spread = 2;
      ArrayList<BlockPos> tips = new ArrayList<>();

      for (int i = 0; i < limbs; i++) {
         double a = start + (double)i * ((Math.PI * 2) / (double)limbs) + random.nextGaussian() * 0.26;
         int reach = Mth.clamp(Math.round(kind.reach * scale * (Mth.lerp(age, 3.0F, 7.0F) + (float)random.nextInt(2))), 2, 8);
         int x = 0;
         int z = 0;
         int y = fork;
         BlockPos previous = new BlockPos(0, fork, 0);

         for (int d = 1; d <= reach; d++) {
            double t = (double)d / (double)reach;
            x = (int)Math.round(Math.cos(a) * (double)d);
            z = (int)Math.round(Math.sin(a) * (double)d);
            y = fork + (int)Math.round((double)rise * Math.sqrt(t) * 0.92 - (double)(kind.droop * (float)rise) * t * t);
            Axis axis = (double)d < (double)reach * 0.4 ? Axis.Y : (Math.abs(Math.cos(a)) >= Math.abs(Math.sin(a)) ? Axis.X : Axis.Z);
            BlockPos branch = new BlockPos(x, y, z);
            connect(wood, previous, branch, trunk);
            previous = branch;
            wood.put(branch, (BlockState)trunk.setValue(RotatedPillarBlock.AXIS, axis));
            wood.putIfAbsent(new BlockPos(x, y - 1, z), trunk);
            if (d == Math.max(2, reach / 2) && random.nextFloat() < 0.75F) {
               double b = a + (double)(random.nextBoolean() ? 1 : -1) * (0.7 + (double)random.nextFloat() * 0.5);
               BlockPos secondary = branch;

               for (int e = 1; e <= 1 + random.nextInt(3); e++) {
                  int sx = (int)Math.round((double)x + Math.cos(b) * (double)e);
                  int sz = (int)Math.round((double)z + Math.sin(b) * (double)e);
                  BlockPos tip = new BlockPos(sx, y + (e > 1 ? 1 : 0), sz);
                  connect(wood, secondary, tip, trunk);
                  secondary = tip;
                  wood.put(tip, (BlockState)trunk.setValue(RotatedPillarBlock.AXIS, Math.abs(Math.cos(b)) >= Math.abs(Math.sin(b)) ? Axis.X : Axis.Z));
                  spread = Math.max(spread, Math.max(Math.abs(sx), Math.abs(sz)));
               }
            }

            spread = Math.max(spread, d);
         }

         tips.add(new BlockPos(x, y, z));
      }

      double crownY = (double)fork + (double)rise * 0.54;
      double rx = Math.min((double)spread + 0.8, 8.0);
      double ry = Mth.clamp((double)((float)rise * kind.dome + 1.0F), 2.5, (double)(height + 1) - crownY);
      int ir = (int)Math.ceil(rx);
      int iy = (int)Math.ceil(ry);

      for (int x = -ir; x <= ir; x++) {
         for (int z = -ir; z <= ir; z++) {
            for (int y = (int)(crownY - (double)iy); (double)y <= crownY + (double)iy; y++) {
               double dx = (double)x / rx;
               double dz = (double)z / rx;
               double dy = ((double)y - crownY) / ry;
               double d = dx * dx + dy * dy + dz * dz;
               if (!(d > 1.0)
                  && (!(d > 0.55) || !((double)random.nextFloat() < (d - 0.55) * 1.9))
                  && (!((double)y < crownY - ry * 0.45) || !((double)random.nextFloat() < 0.45))) {
                  foliage.add(new BlockPos(x, y, z));
               }
            }
         }
      }

      if (kind.droop > 0.4F) {
         for (BlockPos tip : tips) {
            int fallTo = 1 + random.nextInt(3);

            for (int drop = 1; drop <= fallTo; drop++) {
               cluster(foliage, tip.below(drop), drop < 2 ? 2 : 1, random, 0.3);
            }
         }
      }

      foliage.removeAll(wood.keySet());
      return new WildTrees.Plan(Map.copyOf(wood), decay(wood.keySet(), foliage, leaves), Map.of(), height, spread + 2);
   }

   private static void connect(Map<BlockPos, BlockState> wood, BlockPos from, BlockPos to, BlockState trunk) {
      int steps = Math.max(Math.abs(to.getX() - from.getX()), Math.max(Math.abs(to.getY() - from.getY()), Math.abs(to.getZ() - from.getZ())));
      int x = from.getX();
      int y = from.getY();
      int z = from.getZ();

      for (int i = 1; i <= steps; i++) {
         int nx = from.getX() + Math.round((float)((to.getX() - from.getX()) * i) / (float)steps);
         int ny = from.getY() + Math.round((float)((to.getY() - from.getY()) * i) / (float)steps);
         int nz = from.getZ() + Math.round((float)((to.getZ() - from.getZ()) * i) / (float)steps);

         while (y != ny) {
            y += Integer.signum(ny - y);
            wood.putIfAbsent(new BlockPos(x, y, z), (BlockState)trunk.setValue(RotatedPillarBlock.AXIS, Axis.Y));
         }

         while (x != nx) {
            x += Integer.signum(nx - x);
            wood.putIfAbsent(new BlockPos(x, y, z), (BlockState)trunk.setValue(RotatedPillarBlock.AXIS, Axis.X));
         }

         while (z != nz) {
            z += Integer.signum(nz - z);
            wood.putIfAbsent(new BlockPos(x, y, z), (BlockState)trunk.setValue(RotatedPillarBlock.AXIS, Axis.Z));
         }
      }
   }

   private static WildTrees.Plan snag(RandomSource random, float age) {
      LinkedHashMap<BlockPos, BlockState> wood = new LinkedHashMap<>();
      int height = Math.round(Mth.lerp(age, 5.0F, 17.0F)) + random.nextInt(4);
      BlockState trunk = log(random.nextBoolean() ? "pine_log" : "cedar_log");

      for (int y = 0; y < height; y++) {
         wood.put(new BlockPos(0, y, 0), trunk);
      }

      for (int n = 0; n < 2 + random.nextInt(4); n++) {
         Direction side = Plane.HORIZONTAL.getRandomDirection(random);
         int y = 2 + random.nextInt(Math.max(2, height - 2));
         int length = 1 + random.nextInt(2);

         for (int d = 1; d <= length; d++) {
            wood.put(
               new BlockPos(side.getStepX() * d, y + (d == length && random.nextBoolean() ? 1 : 0), side.getStepZ() * d),
               (BlockState)trunk.setValue(RotatedPillarBlock.AXIS, side.getAxis())
            );
         }
      }

      if (age > 0.5F) {
         buttress(wood, trunk, random, age);
      }

      return new WildTrees.Plan(Map.copyOf(wood), Map.of(), Map.of(), height, 2);
   }

   private static WildTrees.Plan leaner(RandomSource random, float age) {
      LinkedHashMap<BlockPos, BlockState> wood = new LinkedHashMap<>();
      LinkedHashMap<BlockPos, BlockState> dressing = new LinkedHashMap<>();
      LinkedHashSet<BlockPos> foliage = new LinkedHashSet<>();
      int length = Math.round(Mth.lerp(age, 6.0F, 15.0F)) + random.nextInt(4);
      boolean conifer = random.nextBoolean();
      BlockState trunk = log(conifer ? "pine_log" : (random.nextBoolean() ? "aspen_log" : "birch_log"));
      BlockState needles = leaf(conifer ? "pine_needles" : "aspen_leaves");
      Direction side = Plane.HORIZONTAL.getRandomDirection(random);
      Axis axis = side.getAxis();
      BlockState roots = rootPalette.get();

      for (int a = -1; a <= 1; a++) {
         for (int y = 0; y <= 2; y++) {
            if (y != 2 || a == 0) {
               BlockPos at = new BlockPos(a * side.getClockWise().getStepX(), y, a * side.getClockWise().getStepZ());
               dressing.put(at, y == 0 ? Blocks.ROOTED_DIRT.defaultBlockState() : (y == 1 ? Blocks.COARSE_DIRT.defaultBlockState() : roots));
            }
         }
      }

      boolean hung = random.nextFloat() < 0.45F;

      for (int d = 1; d <= length; d++) {
         double t = (double)d / (double)length;
         int yx = hung ? (int)Math.round(3.0 * Math.sin(Math.PI * Math.min(1.0, t * 1.15))) : Math.max(0, 2 - d);
         wood.put(new BlockPos(side.getStepX() * d, yx, side.getStepZ() * d), (BlockState)trunk.setValue(RotatedPillarBlock.AXIS, axis));
         if ((double)d > (double)length * 0.55 && random.nextFloat() < 0.5F) {
            Direction out = side.getClockWise();
            int reach = 1 + random.nextInt(2);

            for (int e = 1; e <= reach; e++) {
               wood.put(
                  new BlockPos(side.getStepX() * d + out.getStepX() * e, yx, side.getStepZ() * d + out.getStepZ() * e),
                  (BlockState)trunk.setValue(RotatedPillarBlock.AXIS, out.getAxis())
               );
            }

            if (conifer || random.nextBoolean()) {
               cluster(foliage, new BlockPos(side.getStepX() * d + out.getStepX() * reach, yx, side.getStepZ() * d + out.getStepZ() * reach), 1, random, 0.3);
            }
         }
      }

      foliage.removeAll(wood.keySet());
      return new WildTrees.Plan(Map.copyOf(wood), decay(wood.keySet(), foliage, needles), Map.copyOf(dressing), 3, length);
   }

   private static WildTrees.Plan stump(RandomSource random) {
      LinkedHashMap<BlockPos, BlockState> wood = new LinkedHashMap<>();
      BlockState trunk = log(random.nextInt(3) == 0 ? "birch_log" : (random.nextBoolean() ? "pine_log" : "aspen_log"));
      int height = 1 + random.nextInt(2);

      for (int y = 0; y < height; y++) {
         wood.put(new BlockPos(0, y, 0), trunk);
      }

      for (Direction side : Plane.HORIZONTAL) {
         if (random.nextFloat() < 0.7F) {
            wood.put(new BlockPos(side.getStepX(), 0, side.getStepZ()), (BlockState)trunk.setValue(RotatedPillarBlock.AXIS, side.getAxis()));
         }
      }

      return new WildTrees.Plan(Map.copyOf(wood), Map.of(), Map.of(), height, 1);
   }

   private static void buttress(Map<BlockPos, BlockState> wood, BlockState trunk, RandomSource random, float age) {
      for (Direction side : Plane.HORIZONTAL) {
         if (!(random.nextFloat() > Mth.lerp(age, 0.3F, 0.9F))) {
            wood.put(new BlockPos(side.getStepX(), 0, side.getStepZ()), (BlockState)trunk.setValue(RotatedPillarBlock.AXIS, side.getAxis()));
            if (age > 0.8F && random.nextBoolean()) {
               wood.putIfAbsent(new BlockPos(side.getStepX() * 2, 0, side.getStepZ() * 2), (BlockState)trunk.setValue(RotatedPillarBlock.AXIS, side.getAxis()));
            }
         }
      }
   }

   private static void cluster(Set<BlockPos> leaves, BlockPos at, int radius, RandomSource random, double ragged) {
      radius = Math.min(radius, 3);

      for (int x = -radius; x <= radius; x++) {
         for (int z = -radius; z <= radius; z++) {
            for (int y = -1; y <= 1; y++) {
               double edge = (double)(x * x + z * z) / ((double)(radius * radius) + 0.5) + (double)Math.abs(y) * 0.62;
               if (!(edge > 1.15) && (!(edge > 0.62) || !(random.nextDouble() < 0.5 + ragged))) {
                  leaves.add(at.offset(x, y, z));
               }
            }
         }
      }
   }

   private static Map<BlockPos, BlockState> decay(Set<BlockPos> wood, Set<BlockPos> foliage, BlockState leaf) {
      HashMap<BlockPos, Integer> distance = new HashMap<>();
      ArrayDeque<BlockPos> queue = new ArrayDeque<>();

      for (BlockPos p : wood) {
         for (Direction dir : Direction.values()) {
            BlockPos n = p.relative(dir);
            if (foliage.contains(n) && distance.putIfAbsent(n, 1) == null) {
               queue.add(n);
            }
         }
      }

      while (!queue.isEmpty()) {
         BlockPos at = queue.remove();
         int next = distance.get(at) + 1;
         if (next <= 6) {
            for (Direction dirx : Direction.values()) {
               BlockPos n = at.relative(dirx);
               if (foliage.contains(n) && distance.putIfAbsent(n, next) == null) {
                  queue.add(n);
               }
            }
         }
      }

      LinkedHashMap<BlockPos, BlockState> out = new LinkedHashMap<>();

      for (BlockPos p : foliage) {
         Integer d = distance.get(p);
         if (d != null) {
            out.put(p, (BlockState)leaf.setValue(LeavesBlock.DISTANCE, d));
         }
      }

      return Map.copyOf(out);
   }

   public static boolean soft(BlockState state) {
      return state.isAir() || state.is(BlockTags.LEAVES) || state.is(BlockTags.REPLACEABLE_BY_TREES) || state.getBlock() instanceof ForestFloor;
   }

   public static boolean rooted(WorldGenLevel level, BlockPos origin) {
      return level.getBlockState(origin.below()).is(BlockTags.DIRT) && soft(level.getBlockState(origin));
   }

   private WildTrees() {
   }

   private static enum Broadleaf {
      ASPEN("aspen_log", "aspen_leaves", 11, 27, 0.46F, 1.0F, 0.0F, 0.58F, 3),
      BIRCH("birch_log", "birch_leaves", 13, 29, 0.6F, 0.78F, 0.0F, 0.5F, 3),
      // [wood] maples and willows are built of the mod's maple log (as in the alpine generator), not aspen: every log
      // of a tree is its own species and drops that species' log
      MAPLE("alpine_maple_log", "maple_leaves", 10, 24, 0.36F, 1.3F, 0.1F, 0.72F, 4),
      WILLOW("alpine_maple_log", "willow_leaves", 11, 21, 0.42F, 1.4F, 0.66F, 0.48F, 4);

      final String log;
      final String leaf;
      final int low;
      final int high;
      final float fork;
      final float reach;
      final float droop;
      final float dome;
      final int limbs;

      private Broadleaf(String log, String leaf, int low, int high, float fork, float reach, float droop, float dome, int limbs) {
         this.log = log;
         this.leaf = leaf;
         this.low = low;
         this.high = high;
         this.fork = fork;
         this.reach = reach;
         this.droop = droop;
         this.dome = dome;
         this.limbs = limbs;
      }
   }

   public static enum Kind {
      PINE,
      FIR,
      ASPEN,
      BIRCH,
      MAPLE,
      WILLOW,
      SNAG,
      LEANER,
      STUMP;
   }

   public static record Plan(Map<BlockPos, BlockState> wood, Map<BlockPos, BlockState> leaves, Map<BlockPos, BlockState> dressing, int height, int radius) {
   }
}
