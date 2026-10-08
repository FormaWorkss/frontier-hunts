package com.formaworks.frontierhunts.landscape;

import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class AlpineBranchStub extends Block {
   public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
   public static final IntegerProperty WOOD = IntegerProperty.create("wood", 0, 3);
   public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 2);
   private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

   public AlpineBranchStub(Properties var1) {
      super(var1);
      this.registerDefaultState(
         (BlockState)((BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(FACING, Direction.NORTH)).setValue(WOOD, 0))
            .setValue(VARIANT, 0)
      );
   }

   protected MapCodec<? extends Block> codec() {
      return simpleCodec(AlpineBranchStub::new);
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> var1) {
      var1.add(new Property[]{FACING, WOOD, VARIANT});
   }

   protected VoxelShape getShape(BlockState var1, BlockGetter var2, BlockPos var3, CollisionContext var4) {
      return SHAPES.get(var1.getValue(FACING));
   }

   protected VoxelShape getCollisionShape(BlockState var1, BlockGetter var2, BlockPos var3, CollisionContext var4) {
      return Shapes.empty();
   }

   protected boolean canSurvive(BlockState var1, LevelReader var2, BlockPos var3) {
      BlockState var4 = var2.getBlockState(var3.relative(((Direction)var1.getValue(FACING)).getOpposite()));
      return var4.is(BlockTags.LOGS);
   }

   protected BlockState updateShape(BlockState var1, Direction var2, BlockState var3, LevelAccessor var4, BlockPos var5, BlockPos var6) {
      return var2 == ((Direction)var1.getValue(FACING)).getOpposite() && !this.canSurvive(var1, var4, var5)
         ? Blocks.AIR.defaultBlockState()
         : super.updateShape(var1, var2, var3, var4, var5, var6);
   }

   public static int woodOf(BlockState var0) {
      String var1 = BuiltInRegistries.BLOCK.getKey(var0.getBlock()).getPath();
      if (var1.contains("pine") || var1.contains("larch") || var1.contains("jungle") || var1.contains("acacia")) {
         return 1;
      } else if (var1.contains("birch") || var1.contains("aspen") || var1.contains("rowan") || var1.contains("alder")) {
         return 2;
      } else {
         return !var1.contains("spruce") && !var1.contains("fir") && !var1.contains("dark_oak") && !var1.contains("mangrove") ? 3 : 0;
      }
   }

   public BlockState getStateForPlacement(BlockPlaceContext var1) {
      Direction var2 = var1.getClickedFace();
      if (var2.getAxis().isVertical()) {
         return null;
      } else {
         BlockState var3 = var1.getLevel().getBlockState(var1.getClickedPos().relative(var2.getOpposite()));
         if (!var3.is(BlockTags.LOGS)) {
            return null;
         } else {
            BlockPos var4 = var1.getClickedPos();
            return (BlockState)((BlockState)((BlockState)this.defaultBlockState().setValue(FACING, var2)).setValue(WOOD, woodOf(var3)))
               .setValue(VARIANT, Math.floorMod(Mth.getSeed(var4.getX(), var4.getY(), var4.getZ()) >>> 9, 3));
         }
      }
   }

   protected BlockState rotate(BlockState var1, Rotation var2) {
      return (BlockState)var1.setValue(FACING, var2.rotate((Direction)var1.getValue(FACING)));
   }

   protected BlockState mirror(BlockState var1, Mirror var2) {
      return var1.rotate(var2.getRotation((Direction)var1.getValue(FACING)));
   }

   static {
      SHAPES.put(Direction.NORTH, Block.box(5.0, 5.0, 6.0, 11.0, 12.0, 16.0));
      SHAPES.put(Direction.SOUTH, Block.box(5.0, 5.0, 0.0, 11.0, 12.0, 10.0));
      SHAPES.put(Direction.WEST, Block.box(6.0, 5.0, 5.0, 16.0, 12.0, 11.0));
      SHAPES.put(Direction.EAST, Block.box(0.0, 5.0, 5.0, 10.0, 12.0, 11.0));
   }
}
