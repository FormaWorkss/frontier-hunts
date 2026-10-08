package com.formaworks.frontierhunts.landscape;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class AlpineTracks extends Block {
   public static final IntegerProperty KIND = IntegerProperty.create("kind", 0, 5);
   public static final BooleanProperty SNOW = BooleanProperty.create("snow");
   public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 3);
   public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
   public static final int DEER = 0;
   public static final int ELK = 1;
   public static final int MOOSE = 2;
   public static final int BOOT = 3;
   public static final int RABBIT = 4;
   public static final int FOX = 5;

   public AlpineTracks(Properties var1) {
      super(var1);
      this.registerDefaultState(
         (BlockState)((BlockState)((BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(KIND, 0)).setValue(SNOW, false)).setValue(AGE, 0))
            .setValue(FACING, Direction.NORTH)
      );
   }

   protected MapCodec<? extends Block> codec() {
      return simpleCodec(AlpineTracks::new);
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> var1) {
      var1.add(new Property[]{KIND, SNOW, AGE, FACING});
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

   protected boolean isRandomlyTicking(BlockState var1) {
      return true;
   }

   protected void randomTick(BlockState var1, ServerLevel var2, BlockPos var3, RandomSource var4) {
      boolean var5 = var2.isRainingAt(var3.above()) || var2.isRaining() && (Boolean)var1.getValue(SNOW) && var2.canSeeSky(var3);
      if (var5 || !(var4.nextFloat() > 0.45F)) {
         int var6 = (Integer)var1.getValue(AGE);
         if (var6 >= 3) {
            var2.removeBlock(var3, false);
         } else {
            var2.setBlock(var3, (BlockState)var1.setValue(AGE, var6 + 1), 2);
         }
      }
   }

   protected boolean canSurvive(BlockState var1, LevelReader var2, BlockPos var3) {
      BlockPos var4 = var3.below();
      return var2.getBlockState(var4).isFaceSturdy(var2, var4, Direction.UP);
   }

   protected BlockState updateShape(BlockState var1, Direction var2, BlockState var3, LevelAccessor var4, BlockPos var5, BlockPos var6) {
      return var2 == Direction.DOWN && !this.canSurvive(var1, var4, var5)
         ? Blocks.AIR.defaultBlockState()
         : super.updateShape(var1, var2, var3, var4, var5, var6);
   }

   protected BlockState rotate(BlockState var1, Rotation var2) {
      return (BlockState)var1.setValue(FACING, var2.rotate((Direction)var1.getValue(FACING)));
   }

   protected BlockState mirror(BlockState var1, Mirror var2) {
      return var1.rotate(var2.getRotation((Direction)var1.getValue(FACING)));
   }
}
