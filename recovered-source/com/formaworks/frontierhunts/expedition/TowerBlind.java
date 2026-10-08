package com.formaworks.frontierhunts.expedition;

import com.formaworks.frontierhunts.landscape.tent.ShelterSeal;
import com.formaworks.frontierhunts.landscape.tent.TentPitch;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class TowerBlind extends HorizontalDirectionalBlock {
   public static final MapCodec<TowerBlind> CODEC = simpleCodec(TowerBlind::new);
   public static final IntegerProperty PART = IntegerProperty.create("part", 0, TowerBlindShapeData.PARTS.length - 1);
   public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
   private static final List<BlockPos> PARTS = parts();
   private final VoxelShape[][][] shapes = new VoxelShape[TowerBlindShapeData.PARTS.length][2][4];
   private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);

   public TowerBlind(Properties var1) {
      super(var1.forceSolidOn());
      this.registerDefaultState(
         (BlockState)((BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(FACING, Direction.NORTH)).setValue(PART, 0))
            .setValue(OPEN, true)
      );
      double[][][][] var2 = TowerBlindShapeData.boxes();

      for (int var3 = 0; var3 < PARTS.size(); var3++) {
         for (int var4 = 0; var4 < 2; var4++) {
            if (var4 == 1 && Arrays.deepEquals(var2[var3][0], var2[var3][1])) {
               this.shapes[var3][1] = this.shapes[var3][0];
            } else {
               VoxelShape var5 = Shapes.empty();

               for (double[] var9 : var2[var3][var4]) {
                  var5 = Shapes.or(var5, Shapes.box(var9[0], var9[1], var9[2], var9[3], var9[4], var9[5]));
               }

               var5 = var5.optimize();

               for (Direction var12 : Plane.HORIZONTAL) {
                  this.shapes[var3][var4][var12.get2DDataValue()] = rotateShape(var5, var12);
               }
            }
         }
      }
   }

   private static List<BlockPos> parts() {
      return Arrays.stream(TowerBlindShapeData.PARTS).map(var0 -> new BlockPos(var0[0], var0[1], var0[2])).toList();
   }

   public static int partCount() {
      return PARTS.size();
   }

   public static int firstPart() {
      return 68;
   }

   private static int start(BlockState var0) {
      return var0.getValue(PART) < 68 ? 0 : 68;
   }

   private static int end(BlockState var0) {
      return var0.getValue(PART) < 68 ? 68 : PARTS.size();
   }

   protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
      return CODEC;
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> var1) {
      var1.add(new Property[]{FACING, PART, OPEN});
   }

   protected BlockState rotate(BlockState var1, Rotation var2) {
      return (BlockState)var1.setValue(FACING, var2.rotate((Direction)var1.getValue(FACING)));
   }

   protected BlockState mirror(BlockState var1, Mirror var2) {
      return var1.rotate(var2.getRotation((Direction)var1.getValue(FACING)));
   }

   public static BlockPos offset(int var0, Direction var1) {
      BlockPos var2 = PARTS.get(var0);

      return switch (var1) {
         case EAST -> new BlockPos(-var2.getZ(), var2.getY(), var2.getX());
         case SOUTH -> new BlockPos(-var2.getX(), var2.getY(), -var2.getZ());
         case WEST -> new BlockPos(var2.getZ(), var2.getY(), -var2.getX());
         default -> var2;
      };
   }

   public static BlockPos base(BlockPos var0, BlockState var1) {
      return var0.subtract(offset((Integer)var1.getValue(PART), (Direction)var1.getValue(FACING)));
   }

   public static boolean place(UseOnContext var0) {
      if (var0.getPlayer() instanceof ServerPlayer var1) {
         if (var0.getClickedFace() != Direction.UP) {
            ExpeditionService.message(var1, "Set the tower on level ground with room for its cabin, legs and inclined ladder.");
            return false;
         } else {
            return assemble(var1, var0.getClickedPos().above(), var1.getDirection().getOpposite(), var0.getItemInHand());
         }
      } else {
         return false;
      }
   }

   public static boolean assemble(ServerPlayer var0, BlockPos var1, Direction var2, ItemStack var3) {
      if (!var2.getAxis().isHorizontal()) {
         return false;
      } else {
         ServerLevel var4 = var0.serverLevel();
         TowerBlind var5 = (TowerBlind)ExpeditionContent.TOWER_BLIND.get();
         ArrayList var6 = new ArrayList();

         for (BlockPos var10 : new BlockPos[]{
            new BlockPos(-2, 0, -2), new BlockPos(2, 0, -2), new BlockPos(-2, 0, 2), new BlockPos(2, 0, 2), new BlockPos(0, 0, -6)
         }) {
            var6.add(var1.offset(rotateOffset(var10, var2)));
         }

         ArrayList var25 = new ArrayList();

         for (int var26 = 68; var26 < PARTS.size(); var26++) {
            var25.add(var1.offset(offset(var26, var2)));
         }

         TentPitch.Plan var27 = TentPitch.plan(var4, var6, var25, var1.getY());
         if (var27 != null && !TentPitch.occupied(var4, var27)) {
            for (TentPitch.Change var30 : var27.changes()) {
               if (!var4.hasChunkAt(var30.pos())
                  || !var4.isInWorldBounds(var30.pos())
                  || !var4.getWorldBorder().isWithinBounds(var30.pos())
                  || !var4.mayInteract(var0, var30.pos())
                  || !var0.mayUseItemAt(var30.pos(), Direction.UP, var3)) {
                  ExpeditionService.message(var0, "You cannot level this tower pitch here.");
                  return false;
               }
            }

            var1 = new BlockPos(var1.getX(), var27.floorY(), var1.getZ());
            TentPitch.apply(var4, var27);
            if (!supported(var4, var1, var2, true)) {
               ExpeditionService.message(var0, "The tower pitch could not be made firm and level.");
               return false;
            } else {
               int var29 = 0;
               int var31 = 0;
               int var11 = 0;
               int var12 = 0;

               for (int var13 = 68; var13 < PARTS.size(); var13++) {
                  BlockPos var14 = offset(var13, var2);
                  var29 = Math.min(var29, var14.getX());
                  var31 = Math.max(var31, var14.getX());
                  var11 = Math.min(var11, var14.getZ());
                  var12 = Math.max(var12, var14.getZ());
               }

               for (BlockPos var34 : BlockPos.betweenClosed(var1.offset(var29, 0, var11), var1.offset(var31, 6, var12))) {
                  if (!var4.hasChunkAt(var34)
                     || !var4.isInWorldBounds(var34)
                     || !var4.getWorldBorder().isWithinBounds(var34)
                     || !var4.mayInteract(var0, var34)
                     || !var0.mayUseItemAt(var34, Direction.UP, var3)
                     || !var4.getBlockState(var34).canBeReplaced()
                     || !var4.getFluidState(var34).isEmpty()) {
                     ExpeditionService.message(var0, "Clear space for the tower, raised cabin and inclined ladder.");
                     return false;
                  }
               }

               List var33 = var4.getEntitiesOfClass(
                     LivingEntity.class,
                     new AABB(Vec3.atLowerCornerOf(var1.offset(var29, 0, var11)), Vec3.atLowerCornerOf(var1.offset(var31 + 1, 7, var12 + 1)))
                  )
                  .stream()
                  .map(Entity::getBoundingBox)
                  .toList();

               for (int var35 = 68; var35 < PARTS.size(); var35++) {
                  BlockPos var15 = var1.offset(offset(var35, var2));
                  BlockState var16 = (BlockState)((BlockState)var5.defaultBlockState().setValue(PART, var35)).setValue(FACING, var2);

                  for (AABB var18 : var16.getCollisionShape(var4, var15).toAabbs()) {
                     for (AABB var20 : var33) {
                        if (var18.move(var15).intersects(var20)) {
                           ExpeditionService.message(var0, "Step clear of the shelter frame before placing it.");
                           return false;
                        }
                     }
                  }
               }

               BlockPos var36 = var1.relative(var2.getOpposite()).above(4);
               ShelterSeal.clear(var4, BlockPos.betweenClosed(var36.offset(-1, 0, -1), var36.offset(1, 1, 1)));
               LinkedHashMap var37 = new LinkedHashMap();

               for (int var38 = 68; var38 < PARTS.size(); var38++) {
                  BlockPos var40 = var1.offset(offset(var38, var2));
                  var37.put(var40, var4.getBlockState(var40));
               }

               for (int var39 = 68; var39 < PARTS.size(); var39++) {
                  if (!var4.setBlock(
                     var1.offset(offset(var39, var2)), (BlockState)((BlockState)var5.defaultBlockState().setValue(PART, var39)).setValue(FACING, var2), 18
                  )) {
                     REMOVING.set(true);

                     try {
                        var37.forEach((var1x, var2x) -> var4.setBlock(var1x, var2x, 18));
                     } finally {
                        REMOVING.set(false);
                     }

                     return false;
                  }
               }

               var37.keySet().forEach(var2x -> var4.updateNeighborsAt(var2x, var5));
               return true;
            }
         } else {
            ExpeditionService.message(var0, "The tower feet need natural ground that can be levelled by one block.");
            return false;
         }
      }
   }

   private static boolean supported(LevelReader var0, BlockPos var1, Direction var2, boolean var3) {
      int var4 = var3 ? 2 : 1;

      for (BlockPos var8 : new BlockPos[]{
         new BlockPos(-var4, -1, -var4),
         new BlockPos(var4, -1, -var4),
         new BlockPos(-var4, -1, var4),
         new BlockPos(var4, -1, var4),
         new BlockPos(0, -1, var3 ? -6 : -4)
      }) {
         BlockPos var9 = var1.offset(rotateOffset(var8, var2));
         if (!var0.hasChunkAt(var9) || !var0.getBlockState(var9).isFaceSturdy(var0, var9, Direction.UP)) {
            return false;
         }
      }

      return true;
   }

   private static BlockPos rotateOffset(BlockPos var0, Direction var1) {
      return switch (var1) {
         case EAST -> new BlockPos(-var0.getZ(), var0.getY(), var0.getX());
         case SOUTH -> new BlockPos(-var0.getX(), var0.getY(), -var0.getZ());
         case WEST -> new BlockPos(var0.getZ(), var0.getY(), -var0.getX());
         default -> var0;
      };
   }

   public boolean isLadder(BlockState var1, LevelReader var2, BlockPos var3, LivingEntity var4) {
      return TowerBlindShapeData.LADDER[var1.getValue(PART)];
   }

   protected VoxelShape getShape(BlockState var1, BlockGetter var2, BlockPos var3, CollisionContext var4) {
      return this.shapes[var1.getValue(PART)][var1.getValue(OPEN) ? 1 : 0][((Direction)var1.getValue(FACING)).get2DDataValue()];
   }

   protected VoxelShape getOcclusionShape(BlockState var1, BlockGetter var2, BlockPos var3) {
      return Shapes.empty();
   }

   protected boolean isPathfindable(BlockState var1, PathComputationType var2) {
      return false;
   }

   private static boolean matches(BlockState var0, Block var1, int var2, Direction var3) {
      return var0.is(var1) && (Integer)var0.getValue(PART) == var2 && var0.getValue(FACING) == var3;
   }

   protected BlockState updateShape(BlockState var1, Direction var2, BlockState var3, LevelAccessor var4, BlockPos var5, BlockPos var6) {
      if (!var4.isClientSide()) {
         var4.scheduleTick(base(var5, var1).offset(offset(start(var1), (Direction)var1.getValue(FACING))), this, 1);
      }

      return var1;
   }

   protected void tick(BlockState var1, ServerLevel var2, BlockPos var3, RandomSource var4) {
      BlockPos var5 = base(var3, var1);
      Direction var6 = (Direction)var1.getValue(FACING);

      for (int var7 = start(var1); var7 < end(var1); var7++) {
         if (!var2.hasChunkAt(var5.offset(offset(var7, var6)))) {
            return;
         }
      }

      boolean var9 = supported(var2, var5, var6, start(var1) != 0);

      for (int var8 = start(var1); var8 < end(var1); var8++) {
         var9 &= matches(var2.getBlockState(var5.offset(offset(var8, var6))), this, var8, var6);
      }

      if (!var9) {
         dismantle(var2, var5, var6, var1, true);
      } else {
         BlockPos var10 = var5.relative(var6.getOpposite()).above(4);
         ShelterSeal.clear(var2, BlockPos.betweenClosed(var10.offset(-1, 0, -1), var10.offset(1, 1, 1)));
         var2.scheduleTick(var3, this, 20);
      }
   }

   protected void onRemove(BlockState var1, Level var2, BlockPos var3, BlockState var4, boolean var5) {
      super.onRemove(var1, var2, var3, var4, var5);
      if (!var2.isClientSide && !var4.is(this) && !REMOVING.get()) {
         dismantle(var2, base(var3, var1), (Direction)var1.getValue(FACING), var1, false);
      }
   }

   private static void dismantle(Level var0, BlockPos var1, Direction var2, BlockState var3, boolean var4) {
      if (!REMOVING.get()) {
         REMOVING.set(true);
         boolean var5 = false;

         try {
            for (int var6 = start(var3); var6 < end(var3); var6++) {
               BlockPos var7 = var1.offset(offset(var6, var2));
               if (var0.hasChunkAt(var7) && matches(var0.getBlockState(var7), (Block)ExpeditionContent.TOWER_BLIND.get(), var6, var2)) {
                  var5 = true;
                  var0.setBlock(var7, Blocks.AIR.defaultBlockState(), 18);
               }
            }

            if (var4 && var5) {
               popResource(var0, var1, new ItemStack(ExpeditionContent.item("tower_blind")));
            }
         } finally {
            REMOVING.set(false);
         }

         for (int var11 = start(var3); var11 < end(var3); var11++) {
            var0.updateNeighborsAt(var1.offset(offset(var11, var2)), Blocks.AIR);
         }
      }
   }

   protected InteractionResult useWithoutItem(BlockState var1, Level var2, BlockPos var3, Player var4, BlockHitResult var5) {
      if (var2.isClientSide) {
         return InteractionResult.SUCCESS;
      } else {
         BlockPos var6 = base(var3, var1);
         Direction var7 = (Direction)var1.getValue(FACING);
         boolean var8 = !(Boolean)var1.getValue(OPEN);

         for (int var9 = start(var1); var9 < end(var1); var9++) {
            BlockPos var10 = var6.offset(offset(var9, var7));
            BlockState var11 = var2.getBlockState(var10);
            if (!matches(var11, this, var9, var7)) {
               return InteractionResult.CONSUME;
            }

            VoxelShape var12 = Shapes.joinUnoptimized(
               ((BlockState)var11.setValue(OPEN, var8)).getCollisionShape(var2, var10), var11.getCollisionShape(var2, var10), BooleanOp.ONLY_FIRST
            );

            for (AABB var14 : var12.toAabbs()) {
               if (!var2.getEntitiesOfClass(LivingEntity.class, var14.move(var10)).isEmpty()) {
                  if (var4 instanceof ServerPlayer var15) {
                     ExpeditionService.message(var15, "Step clear of the door before moving it.");
                  }

                  return InteractionResult.CONSUME;
               }
            }
         }

         for (int var16 = start(var1); var16 < end(var1); var16++) {
            BlockPos var18 = var6.offset(offset(var16, var7));
            var2.setBlock(var18, (BlockState)var2.getBlockState(var18).setValue(OPEN, var8), 2);
         }

         if (var4 instanceof ServerPlayer var17) {
            ExpeditionService.message(var17, var8 ? "Tower door open" : "Tower door closed · Use the frame to reopen");
         }

         return InteractionResult.CONSUME;
      }
   }

   private static VoxelShape rotateShape(VoxelShape var0, Direction var1) {
      if (var1 == Direction.NORTH) {
         return var0;
      } else {
         ArrayList var2 = new ArrayList();
         var0.forAllBoxes((var2x, var4, var6, var8, var10, var12) -> {
            var2.add(switch (var1) {
               case EAST -> Shapes.box(1.0 - var12, var4, var2x, 1.0 - var6, var10, var8);
               case SOUTH -> Shapes.box(1.0 - var8, var4, 1.0 - var12, 1.0 - var2x, var10, 1.0 - var6);
               default -> Shapes.box(var6, var4, 1.0 - var8, var12, var10, 1.0 - var2x);
            });
         });
         return var2.stream().reduce(Shapes.empty(), Shapes::or).optimize();
      }
   }
}
