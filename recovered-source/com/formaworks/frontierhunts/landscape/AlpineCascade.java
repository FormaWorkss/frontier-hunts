package com.formaworks.frontierhunts.landscape;

import com.formaworks.frontierhunts.environment.Terrain;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class AlpineCascade extends Block implements LiquidBlockContainer {
   public static final MapCodec<AlpineCascade> CODEC = simpleCodec(AlpineCascade::new);
   public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
   public static final EnumProperty<AlpineCascade.Part> PART = EnumProperty.create("part", AlpineCascade.Part.class);
   public static final EnumProperty<AlpineCascade.Edge> EDGE = EnumProperty.create("edge", AlpineCascade.Edge.class);
   public static final IntegerProperty BREAK = IntegerProperty.create("break", 0, 2);
   public static final IntegerProperty VPHASE = IntegerProperty.create("vphase", 0, 2);
   public static final IntegerProperty HPHASE = IntegerProperty.create("hphase", 0, 1);
   private static final Direction[] SIDES = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

   public AlpineCascade(Properties var1) {
      super(var1);
      this.registerDefaultState(
         (BlockState)((BlockState)((BlockState)((BlockState)((BlockState)((BlockState)((BlockState)this.stateDefinition.any())
                           .setValue(FACING, Direction.NORTH))
                        .setValue(PART, AlpineCascade.Part.FALL))
                     .setValue(EDGE, AlpineCascade.Edge.NONE))
                  .setValue(BREAK, 0))
               .setValue(VPHASE, 0))
            .setValue(HPHASE, 0)
      );
   }

   protected MapCodec<? extends Block> codec() {
      return CODEC;
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> var1) {
      var1.add(new Property[]{FACING, PART, EDGE, BREAK, VPHASE, HPHASE});
   }

   protected VoxelShape getShape(BlockState var1, BlockGetter var2, BlockPos var3, CollisionContext var4) {
      return Shapes.empty();
   }

   protected VoxelShape getCollisionShape(BlockState var1, BlockGetter var2, BlockPos var3, CollisionContext var4) {
      return Shapes.empty();
   }

   protected boolean propagatesSkylightDown(BlockState var1, BlockGetter var2, BlockPos var3) {
      return true;
   }

   protected boolean isPathfindable(BlockState var1, PathComputationType var2) {
      return true;
   }

   protected boolean canBeReplaced(BlockState var1, Fluid var2) {
      return false;
   }

   public boolean canPlaceLiquid(Player var1, BlockGetter var2, BlockPos var3, BlockState var4, Fluid var5) {
      return false;
   }

   public boolean placeLiquid(LevelAccessor var1, BlockPos var2, BlockState var3, FluidState var4) {
      return false;
   }

   protected BlockState rotate(BlockState var1, Rotation var2) {
      return (BlockState)var1.setValue(FACING, var2.rotate((Direction)var1.getValue(FACING)));
   }

   protected BlockState mirror(BlockState var1, Mirror var2) {
      return var1.rotate(var2.getRotation((Direction)var1.getValue(FACING)));
   }

   protected void entityInside(BlockState var1, Level var2, BlockPos var3, Entity var4) {
      if (var4.isOnFire()) {
         var4.clearFire();
      }

      Vec3 var5 = var4.getDeltaMovement();
      if (var5.y > -0.6) {
         var4.setDeltaMovement(var5.x * 0.92, Math.max(-0.6, var5.y - 0.035), var5.z * 0.92);
      }
   }

   private static AlpineCascade.Feed feed(AlpineLayout var0, int var1, int var2) {
      AlpineLayout.Sample var3 = var0.sample((double)var1, (double)var2);
      if (var3.scenic() && var3.wet()) {
         AlpineCascade.Feed var4 = null;

         for (Direction var8 : SIDES) {
            AlpineLayout.Sample var9 = var0.sample((double)(var1 - var8.getStepX()), (double)(var2 - var8.getStepZ()));
            if (var9.scenic()
               && AlpineFalls.spill(var9.site())
               && var9.water() > var9.floor()
               && var9.water() > var3.water()
               && (var4 == null || var9.water() > var4.top)) {
               var4 = new AlpineCascade.Feed(var9.water(), var8);
            }
         }

         return var4;
      } else {
         return null;
      }
   }

   public static void place(WorldGenLevel var0, AlpineLayout var1, int var2, int var3) {
      AlpineFalls var4 = var1.falls();
      if (var4 != null) {
         AlpineFalls.Site var5 = var4.site(Math.floorDiv(var2 + 8, 384), Math.floorDiv(var3 + 8, 384));
         if (var5 != null
            && (var5.contains(var2, var3) || var5.contains(var2 + 15, var3 + 15) || var5.contains(var2, var3 + 15) || var5.contains(var2 + 15, var3))) {
            MutableBlockPos var6 = new MutableBlockPos();
            AlpineCascade var7 = (AlpineCascade)AlpineRegistration.CASCADE.get();

            for (int var8 = 0; var8 < 16; var8++) {
               for (int var9 = 0; var9 < 16; var9++) {
                  int var10 = var2 + var8;
                  int var11 = var3 + var9;
                  AlpineCascade.Feed var12 = feed(var1, var10, var11);
                  if (var12 != null) {
                     AlpineLayout.Sample var13 = var1.sample((double)var10, (double)var11);
                     int var14 = var13.water() + 1;
                     int var15 = var12.top;
                     if (var15 >= var14) {
                        Direction var16 = var12.facing.getCounterClockWise();
                        Direction var17 = var12.facing.getClockWise();
                        boolean var18 = feed(var1, var10 + var16.getStepX(), var11 + var16.getStepZ()) != null;
                        boolean var19 = feed(var1, var10 + var17.getStepX(), var11 + var17.getStepZ()) != null;
                        AlpineCascade.Edge var20 = var18 && var19
                           ? AlpineCascade.Edge.NONE
                           : (!var18 && !var19 ? AlpineCascade.Edge.BOTH : (!var18 ? AlpineCascade.Edge.LEFT : AlpineCascade.Edge.RIGHT));

                        for (int var21 = var15; var21 >= var14; var21--) {
                           var6.set(var10, var21, var11);
                           if (var0.isEmptyBlock(var6)) {
                              AlpineCascade.Part var22 = var15 == var14
                                 ? AlpineCascade.Part.SHORT
                                 : (var21 == var15 ? AlpineCascade.Part.LIP : (var21 == var14 ? AlpineCascade.Part.FOOT : AlpineCascade.Part.FALL));
                              int var23 = var15 - var21;
                              Terrain.set(
                                 var0,
                                 var6,
                                 (BlockState)((BlockState)((BlockState)((BlockState)((BlockState)((BlockState)var7.defaultBlockState()
                                                   .setValue(FACING, var12.facing))
                                                .setValue(PART, var22))
                                             .setValue(EDGE, var20))
                                          .setValue(BREAK, var23 < 5 ? 0 : (var23 < 14 ? 1 : 2)))
                                       .setValue(VPHASE, Math.floorMod(-var21, 3)))
                                    .setValue(HPHASE, var10 + var11 & 1)
                              );
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public static boolean needsSpill(AlpineLayout var0, int var1, int var2) {
      AlpineLayout.Sample var3 = var0.sample((double)var1, (double)var2);

      for (Direction var7 : SIDES) {
         AlpineLayout.Sample var8 = var0.sample((double)(var1 + var7.getStepX()), (double)(var2 + var7.getStepZ()));
         if (!var8.wet() && var8.scenic() && AlpineFalls.kind(var8.site()) == 12 && var8.floor() < var3.water()) {
            return true;
         }
      }

      return false;
   }

   public static enum Edge implements StringRepresentable {
      NONE("none"),
      LEFT("left"),
      RIGHT("right"),
      BOTH("both");

      private final String id;

      private Edge(String nullxx) {
         this.id = nullxx;
      }

      public String getSerializedName() {
         return this.id;
      }
   }

   private static record Feed(int top, Direction facing) {
   }

   public static enum Part implements StringRepresentable {
      LIP("lip"),
      FALL("fall"),
      FOOT("foot"),
      SHORT("short");

      private final String id;

      private Part(String nullxx) {
         this.id = nullxx;
      }

      public String getSerializedName() {
         return this.id;
      }
   }
}
