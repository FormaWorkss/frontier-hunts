package com.formaworks.frontierhunts.landscape;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class AlpinePole extends Block {
   public static final IntegerProperty KIND = IntegerProperty.create("kind", 0, 2);
   public static final BooleanProperty TOP = BooleanProperty.create("top");
   private static final VoxelShape STEM = Block.box(6.5, 0.0, 6.5, 9.5, 16.0, 9.5);
   private static final VoxelShape OUTLINE = Block.box(6.0, 0.0, 6.0, 10.0, 16.0, 10.0);

   public AlpinePole(Properties var1) {
      super(var1);
      this.registerDefaultState((BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(KIND, 0)).setValue(TOP, true));
   }

   protected MapCodec<? extends Block> codec() {
      return simpleCodec(AlpinePole::new);
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> var1) {
      var1.add(new Property[]{KIND, TOP});
   }

   protected VoxelShape getShape(BlockState var1, BlockGetter var2, BlockPos var3, CollisionContext var4) {
      return OUTLINE;
   }

   protected VoxelShape getCollisionShape(BlockState var1, BlockGetter var2, BlockPos var3, CollisionContext var4) {
      return STEM;
   }

   protected boolean propagatesSkylightDown(BlockState var1, BlockGetter var2, BlockPos var3) {
      return true;
   }

   protected boolean canSurvive(BlockState var1, LevelReader var2, BlockPos var3) {
      BlockState var4 = var2.getBlockState(var3.below());
      return var4.getBlock() == this && !(Boolean)var4.getValue(TOP)
         || var4.is(BlockTags.DIRT)
         || var4.is(Blocks.MOSS_BLOCK)
         || var4.isFaceSturdy(var2, var3.below(), Direction.UP);
   }

   protected BlockState updateShape(BlockState var1, Direction var2, BlockState var3, LevelAccessor var4, BlockPos var5, BlockPos var6) {
      if (var2 == Direction.DOWN && !this.canSurvive(var1, var4, var5)) {
         var4.scheduleTick(var5, this, 1);
         return var1;
      } else {
         return var2 == Direction.UP ? (BlockState)var1.setValue(TOP, var3.getBlock() != this) : var1;
      }
   }

   protected void tick(BlockState var1, ServerLevel var2, BlockPos var3, RandomSource var4) {
      if (!this.canSurvive(var1, var2, var3)) {
         var2.destroyBlock(var3, true);
      }
   }

   public BlockState getStateForPlacement(BlockPlaceContext var1) {
      Level var2 = var1.getLevel();
      BlockPos var3 = var1.getClickedPos();
      BlockState var4 = var2.getBlockState(var3.below());
      int var5 = var4.getBlock() == this ? (Integer)var4.getValue(KIND) : Math.floorMod(Mth.getSeed(var3.getX(), 0, var3.getZ()) >>> 5, 2);
      return (BlockState)((BlockState)this.defaultBlockState().setValue(KIND, var5)).setValue(TOP, var2.getBlockState(var3.above()).getBlock() != this);
   }
}
