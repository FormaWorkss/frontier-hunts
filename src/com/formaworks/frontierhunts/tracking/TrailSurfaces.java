package com.formaworks.frontierhunts.tracking;

import com.formaworks.frontierhunts.environment.ForestFloor;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.landscape.AlpinePasture;
import com.formaworks.frontierhunts.landscape.AlpineRegistration;
import com.formaworks.frontierhunts.landscape.AlpineThicket;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public final class TrailSurfaces {
   public static final TagKey<Block> FOLIAGE = BlockTags.create(ResourceLocation.fromNamespaceAndPath("frontierhunts", "blood_foliage"));
   private static final double[] SWARD = new double[]{13.0, 19.0, 26.0};

   public static TrailMark fit(LivingEntity var0, BlockHitResult var1, int var2, boolean var3) {
      if (var1.getType() != Type.BLOCK) {
         return null;
      } else {
         Level var4 = var0.level();
         BlockPos var5 = var1.getBlockPos();
         Direction var6 = var1.getDirection();
         if (var4.hasChunkAt(var5) && var4.getFluidState(var5).isEmpty()) {
            Vec3 var7 = var1.getLocation();
            Vec3 var8 = var7.subtract(Vec3.atLowerCornerOf(var5));
            AABB var9 = null;

            for (AABB var11 : var4.getBlockState(var5).getCollisionShape(var4, var5).toAabbs()) {
               double var12 = coordinate(var8, var6.getAxis());
               double var14 = var6.getAxisDirection() == AxisDirection.POSITIVE ? max(var11, var6.getAxis()) : min(var11, var6.getAxis());
               if (Math.abs(var12 - var14) < 0.002 && var11.inflate(0.002).contains(var8)) {
                  var9 = var11;
                  break;
               }
            }

            if (var9 == null) {
               return null;
            } else {
               float var17 = var3
                  ? (var2 == 4 ? 0.44F : (var2 == 3 ? 0.39F : (var2 == 1 ? 0.38F : (var2 == 2 ? 0.24F : 0.24F + var0.getRandom().nextFloat() * 0.055F))))
                  : 0.24F;
               if (var3) {
                  var17 *= bloodScale(var0);
               }

               for (Axis var25 : Axis.values()) {
                  if (var25 != var6.getAxis()) {
                     var17 = Math.min(var17, (float)((max(var9, var25) - min(var9, var25)) / 2.0 - 0.003));
                  }
               }

               if (var17 < 0.01F) {
                  return null;
               } else {
                  double[] var19 = new double[]{var8.x, var8.y, var8.z};

                  for (Axis var15 : Axis.values()) {
                     if (var15 != var6.getAxis()) {
                        var19[var15.ordinal()] = Math.clamp(var19[var15.ordinal()], min(var9, var15) + (double)var17, max(var9, var15) - (double)var17);
                     }
                  }

                  Vec3 var22 = Vec3.atLowerCornerOf(var5).add(var19[0], var19[1], var19[2]).add(Vec3.atLowerCornerOf(var6.getNormal()).scale(0.012));
                  Vec3 var24 = var0.getDeltaMovement();
                  float var27 = var24.horizontalDistanceSqr() > 0.0025 ? (float)Math.toDegrees(Math.atan2(-var24.x, var24.z)) : var0.getYRot();
                  String var28 = TrackPrints.describe(var0);
                  return new TrailMark(
                     UUID.randomUUID(),
                     var0.getUUID(),
                     var22,
                     var5,
                     var27,
                     var4.getGameTime(),
                     0,
                     var3,
                     var28,
                     var24.horizontalDistanceSqr() > 0.04 ? 2 : (var24.horizontalDistanceSqr() > 1.0E-4 ? 1 : 0),
                     var6,
                     var2,
                     var17
                  );
               }
            }
         } else {
            return null;
         }
      }
   }

   public static double foliageBase(BlockState var0) {
      return var0.getBlock() == AlpineRegistration.PASTURE.get() ? (double)(-(Integer)var0.getValue(AlpinePasture.SINK) * 2) / 16.0 : 0.0;
   }

   public static boolean hasFoliage(BlockState var0) {
      return foliageTop(var0) - foliageBase(var0) > 0.02;
   }

   public static double foliageTop(BlockState var0) {
      if (var0.getBlock() == AlpineRegistration.PASTURE.get()) {
         int var1 = (Integer)var0.getValue(AlpinePasture.HEIGHT);
         int var2 = (Integer)var0.getValue(AlpinePasture.SINK);
         return (SWARD[var1] * 0.6 - (double)(var2 * 2)) / 16.0;
      } else if (var0.hasProperty(AlpineThicket.SIZE)) {
         return new double[]{0.34, 0.55, 0.82}[var0.getValue(AlpineThicket.SIZE)];
      } else if (var0.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
         return var0.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? 0.94 : -1.0;
      } else if (var0.is(Blocks.PINK_PETALS)) {
         return 0.1;
      } else if (var0.is(Blocks.SNOW)) {
         return (double)((Integer)var0.getValue(SnowLayerBlock.LAYERS) * 2) / 16.0;
      } else {
         return var0.getBlock() instanceof ForestFloor.Litter ? 0.03 : 0.45;
      }
   }

   /** [1.1.6] can blood or a print sit on top of this block? not fences, walls, gates or leaves (see-through tops) */
   public static boolean groundSupport(BlockState s) {
      return !s.is(BlockTags.FENCES) && !s.is(BlockTags.WALLS) && !s.is(BlockTags.FENCE_GATES) && !s.is(BlockTags.LEAVES);
   }

   /**
    * [1.1.6] the drawn (outline) top of a block under (x, z), block-relative; -1 when it has no shape there. Used where it
    * lies above the collision top: mud and soul sand collide below their surface, Frontier snow carries you lower than
    * it is drawn.
    */
   public static double visualTop(net.minecraft.world.level.BlockGetter level, BlockPos pos, double x, double z) {
      BlockState s = level.getBlockState(pos);
      net.minecraft.world.phys.shapes.VoxelShape shape = s.getShape(level, pos);
      if (shape.isEmpty()) {
         return -1.0;
      }
      double lx = x - pos.getX(), lz = z - pos.getZ(), top = -1.0;
      for (AABB b : shape.toAabbs()) {
         if (lx >= b.minX - 0.01 && lx <= b.maxX + 0.01 && lz >= b.minZ - 0.01 && lz <= b.maxZ + 0.01) {
            top = Math.max(top, b.maxY);
         }
      }
      return Math.min(top, 1.0);
   }

   /**
    * [1.1.6] client guard: an UP mark whose ground has gone (grass cut, snow melted, block broken) or now lies well below
    * it would hang in the air until the server's sweep removes it - such marks are not drawn.
    */
   public static boolean floating(net.minecraft.world.level.BlockGetter level, TrailMark m) {
      if (m.face() != Direction.UP || m.plantSign()) {
         return false;
      }
      BlockPos p = m.support();
      BlockState s = level.getBlockState(p);
      double top = Math.max(visualTop(level, p, m.position().x, m.position().z), s.getCollisionShape(level, p).max(Axis.Y));
      if (top <= 0.0 && s.is(FOLIAGE)) {
         top = Math.max(0.0, foliageTop(s));
      }
      if (top <= 0.0 && s.isAir()) {
         return true;
      }
      return m.position().y - (p.getY() + Math.max(0.0, top)) > 0.09;
   }

   public static double snowTop(BlockState var0) {
      return var0.is(Blocks.SNOW) ? (double)((Integer)var0.getValue(SnowLayerBlock.LAYERS) * 2) / 16.0 : -1.0;
   }

   public static TrailMark onFoliage(LivingEntity var0, BlockPos var1, BlockState var2, Vec3 var3, Direction var4, int var5) {
      double var6 = foliageTop(var2);
      double var8 = foliageBase(var2);
      if (var6 - var8 < 0.02) {
         return null;
      } else {
         Level var10 = var0.level();
         float var11 = (var5 == 4 ? 0.34F : (var5 == 3 ? 0.3F : (var5 == 1 ? 0.28F : 0.2F))) * bloodScale(var0);
         var11 = Math.min(var11, 0.46F);
         double var12 = Math.clamp(var3.x, (double)((float)var1.getX() + var11), (double)((float)(var1.getX() + 1) - var11));
         double var14 = Math.clamp(var3.z, (double)((float)var1.getZ() + var11), (double)((float)(var1.getZ() + 1) - var11));
         double var16;
         if (var4 == Direction.UP) {
            var16 = (double)var1.getY() + var6;
         } else {
            if (var6 - var8 - 0.1 < (double)(2.0F * var11)) {
               return null;
            }

            var16 = Math.clamp(var3.y, (double)var1.getY() + var8 + (double)var11 + 0.04, (double)var1.getY() + var6 - (double)var11);
            if (var4 == Direction.WEST) {
               var12 = (double)var1.getX();
            } else if (var4 == Direction.EAST) {
               var12 = (double)(var1.getX() + 1);
            } else if (var4 == Direction.NORTH) {
               var14 = (double)var1.getZ();
            } else {
               var14 = (double)(var1.getZ() + 1);
            }
         }

         Vec3 var18 = new Vec3(var12, var16, var14).add(Vec3.atLowerCornerOf(var4.getNormal()).scale(0.012));
         Vec3 var19 = var0.getDeltaMovement();
         float var20 = var19.horizontalDistanceSqr() > 0.0025 ? (float)Math.toDegrees(Math.atan2(-var19.x, var19.z)) : var0.getYRot();
         String var21 = TrackPrints.describe(var0);
         return new TrailMark(
            UUID.randomUUID(),
            var0.getUUID(),
            var18,
            var1,
            var20,
            var10.getGameTime(),
            0,
            true,
            var21,
            var19.horizontalDistanceSqr() > 0.04 ? 2 : (var19.horizontalDistanceSqr() > 1.0E-4 ? 1 : 0),
            var4,
            var5,
            var11
         );
      }
   }

   // ---------------------------------------------------------------- [tracking] print-taking ground
   public static final int NONE = -1;
   public static final int SNOW = 0;
   public static final int MUD = 1;
   public static final int SOIL = 2;
   public static final int SAND = 3;
   public static final int GRAVEL = 4;
   public static final int GRASS = 5;
   private static Block duff;

   private static Block duff() {
      Block b = duff;
      if (b == null) {
         b = AlpineRegistration.prop("forest_duff");
         duff = b;
      }
      return b;
   }

   /** What kind of ground a print is pressed into (NONE: rock, wood, ice ... nothing readable). */
   public static int printSurface(BlockState s) {
      if (s.is(Blocks.SNOW) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.POWDER_SNOW)) {
         return SNOW;
      } else if (s.is(Blocks.MUD) || s.is(Blocks.CLAY) || s.is(Blocks.MUDDY_MANGROVE_ROOTS) || s.is(Blocks.FARMLAND)) {
         return MUD;
      } else if (s.is(Blocks.DIRT) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.ROOTED_DIRT) || s.is(Blocks.PODZOL) || s.is(Blocks.DIRT_PATH)
         || s.is(Blocks.MYCELIUM)) {
         return SOIL;
      } else if (s.is(BlockTags.SAND)) {
         return SAND;
      } else if (s.is(Blocks.GRAVEL) || s.is(Blocks.SUSPICIOUS_GRAVEL)) {
         return GRAVEL;
      } else if (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.MOSS_BLOCK)) {
         return GRASS;
      } else {
         Block d = duff();
         return d != Blocks.AIR && s.is(d) ? SOIL : NONE;
      }
   }

   public static float bloodScale(LivingEntity var0) {
      return var0 instanceof Whitetail ? 1.0F : (float)Math.clamp(Math.sqrt((double)(var0.getBbWidth() * var0.getBbHeight()) / 1.62), 0.2, 1.2);
   }

   private static double coordinate(Vec3 var0, Axis var1) {
      return var1 == Axis.X ? var0.x : (var1 == Axis.Y ? var0.y : var0.z);
   }

   private static double min(AABB var0, Axis var1) {
      return var1 == Axis.X ? var0.minX : (var1 == Axis.Y ? var0.minY : var0.minZ);
   }

   private static double max(AABB var0, Axis var1) {
      return var1 == Axis.X ? var0.maxX : (var1 == Axis.Y ? var0.maxY : var0.maxZ);
   }

   private TrailSurfaces() {
   }
}
