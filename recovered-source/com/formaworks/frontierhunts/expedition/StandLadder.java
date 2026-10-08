package com.formaworks.frontierhunts.expedition;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class StandLadder extends HorizontalDirectionalBlock {
   public static final MapCodec<StandLadder> CODEC = simpleCodec(StandLadder::new);
   private final VoxelShape[] shapes = new VoxelShape[4];

   public StandLadder(Properties var1) {
      super(var1);
      this.registerDefaultState((BlockState)((BlockState)this.stateDefinition.any()).setValue(FACING, Direction.NORTH));

      for (Direction var3 : Plane.HORIZONTAL) {
         this.shapes[var3.get2DDataValue()] = switch (var3) {
            case EAST -> Block.box(3.8, 0.0, 1.6, 5.3, 16.0, 14.4);
            case SOUTH -> Block.box(1.6, 0.0, 3.8, 14.4, 16.0, 5.3);
            case WEST -> Block.box(10.7, 0.0, 1.6, 12.2, 16.0, 14.4);
            default -> Block.box(1.6, 0.0, 10.7, 14.4, 16.0, 12.2);
         };
      }
   }

   protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
      return CODEC;
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> var1) {
      var1.add(new Property[]{FACING});
   }

   protected BlockState rotate(BlockState var1, Rotation var2) {
      return (BlockState)var1.setValue(FACING, var2.rotate((Direction)var1.getValue(FACING)));
   }

   protected BlockState mirror(BlockState var1, Mirror var2) {
      return var1.rotate(var2.getRotation((Direction)var1.getValue(FACING)));
   }

   protected VoxelShape getShape(BlockState var1, BlockGetter var2, BlockPos var3, CollisionContext var4) {
      return this.shapes[((Direction)var1.getValue(FACING)).get2DDataValue()];
   }

   protected VoxelShape getOcclusionShape(BlockState var1, BlockGetter var2, BlockPos var3) {
      return Shapes.empty();
   }

   public boolean isLadder(BlockState var1, LevelReader var2, BlockPos var3, LivingEntity var4) {
      return true;
   }

   static boolean matches(BlockState var0, Direction var1) {
      return var0.is((Block)ExpeditionContent.STAND_LADDER.get()) && var0.getValue(FACING) == var1;
   }

   static BlockPos top(LevelReader var0, BlockPos var1, Direction var2) {
      BlockPos var3 = var1.above();

      while (!var0.isOutsideBuildHeight(var3) && var0.hasChunkAt(var3) && matches(var0.getBlockState(var3), var2)) {
         var3 = var3.above();
      }

      BlockState var4 = var0.getBlockState(var3);
      return var4.is((Block)ExpeditionContent.TREE_STAND.get()) && var4.getValue(MountedTreeStand.PART) == 0 && var4.getValue(FACING) == var2 ? var3 : null;
   }

   static void clearBelow(Level var0, BlockPos var1, Direction var2) {
      for (BlockPos var3 = var1.below();
         !var0.isOutsideBuildHeight(var3) && var0.hasChunkAt(var3) && matches(var0.getBlockState(var3), var2);
         var3 = var3.below()
      ) {
         var0.setBlock(var3, Blocks.AIR.defaultBlockState(), 18);
      }
   }

   protected BlockState updateShape(BlockState var1, Direction var2, BlockState var3, LevelAccessor var4, BlockPos var5, BlockPos var6) {
      if (!var4.isClientSide()) {
         var4.scheduleTick(var5, this, 1);
      }

      return var1;
   }

   protected void tick(BlockState var1, ServerLevel var2, BlockPos var3, RandomSource var4) {
      BlockPos var5 = top(var2, var3, (Direction)var1.getValue(FACING));
      if (var5 != null) {
         var2.scheduleTick(var5, (Block)ExpeditionContent.TREE_STAND.get(), 1);
      } else {
         var2.removeBlock(var3, false);
      }
   }

   protected List<ItemStack> getDrops(BlockState var1, net.minecraft.world.level.storage.loot.LootParams.Builder var2) {
      Vec3 var3 = (Vec3)var2.getOptionalParameter(LootContextParams.ORIGIN);
      BlockPos var4 = var3 == null ? null : top(var2.getLevel(), BlockPos.containing(var3), (Direction)var1.getValue(FACING));
      if (var4 == null) {
         return List.of();
      } else {
         BlockState var5 = var2.getLevel().getBlockState(var4);
         ItemStack var6 = new ItemStack(ExpeditionContent.item("tree_stand"));
         MountedTreeStand.configure(var6, (Integer)var5.getValue(MountedTreeStand.HEIGHT), (Integer)var5.getValue(MountedTreeStand.SEATS));
         return List.of(var6);
      }
   }

   protected void onRemove(BlockState var1, Level var2, BlockPos var3, BlockState var4, boolean var5) {
      if (!var2.isClientSide && !var4.is(this) && !MountedTreeStand.removing()) {
         Direction var6 = (Direction)var1.getValue(FACING);
         BlockPos var7 = top(var2, var3, var6);
         if (var7 != null) {
            BlockState var8 = var2.getBlockState(var7);
            MountedTreeStand.dismantle(var2, var7, var6, (Integer)var8.getValue(MountedTreeStand.HEIGHT), (Integer)var8.getValue(MountedTreeStand.SEATS), false);
         }

         MountedTreeStand.clearLadderBelow(var2, var3, var6);
      }

      super.onRemove(var1, var2, var3, var4, var5);
   }
}
