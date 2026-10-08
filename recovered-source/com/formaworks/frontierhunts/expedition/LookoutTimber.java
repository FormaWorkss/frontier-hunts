package com.formaworks.frontierhunts.expedition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.Plane;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class LookoutTimber extends HorizontalDirectionalBlock {
   private static final MapCodec<LookoutTimber> CODEC = RecordCodecBuilder.mapCodec(
      i -> i.group(Codec.STRING.fieldOf("timber").forGetter(b -> b.kind), propertiesCodec()).apply(i, LookoutTimber::new)
   );
   private final String kind;
   private final VoxelShape[] shapes = new VoxelShape[4];

   LookoutTimber(String kind, Properties properties) {
      super(properties);
      this.kind = kind;
      this.registerDefaultState((BlockState)((BlockState)this.stateDefinition.any()).setValue(FACING, Direction.NORTH));

      for (Direction d : Plane.HORIZONTAL) {
         this.shapes[d.get2DDataValue()] = shape(kind, d);
      }
   }

   protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
      return CODEC;
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> b) {
      b.add(new Property[]{FACING});
   }

   public BlockState getStateForPlacement(BlockPlaceContext c) {
      return (BlockState)this.defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite());
   }

   protected BlockState rotate(BlockState state, Rotation rotation) {
      return (BlockState)state.setValue(FACING, rotation.rotate((Direction)state.getValue(FACING)));
   }

   protected BlockState mirror(BlockState state, Mirror mirror) {
      return state.rotate(mirror.getRotation((Direction)state.getValue(FACING)));
   }

   protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return this.shapes[((Direction)state.getValue(FACING)).get2DDataValue()];
   }

   private static VoxelShape shape(String kind, Direction d) {
      boolean z = d.getAxis() == Axis.Z;
      boolean reverse = d == Direction.NORTH || d == Direction.WEST;
      if (kind.equals("lookout_deck_rail")) {
         return z ? Block.box(6.0, 0.0, 0.0, 10.0, 24.0, 16.0) : Block.box(0.0, 0.0, 6.0, 16.0, 24.0, 10.0);
      } else {
         VoxelShape shape = Shapes.empty();

         for (int i = 0; i < 16; i++) {
            double h = reverse ? (double)(15 - i) : (double)i;
            double low = kind.equals("lookout_stair_rail") ? Math.max(0.0, h - 2.0) : Math.max(0.0, h - 2.0);
            double high = kind.equals("lookout_stair_rail") ? h + 18.0 : Math.min(16.0, h + 3.0);
            shape = Shapes.or(shape, Block.box(z ? 6.0 : (double)i, low, z ? (double)i : 6.0, z ? 10.0 : (double)(i + 1), high, z ? (double)(i + 1) : 10.0));
            if (kind.equals("lookout_cross_brace")) {
               shape = Shapes.or(
                  shape,
                  Block.box(
                     z ? 6.0 : (double)i,
                     Math.max(0.0, 13.0 - h),
                     z ? (double)i : 6.0,
                     z ? 10.0 : (double)(i + 1),
                     Math.min(16.0, 18.0 - h),
                     z ? (double)(i + 1) : 10.0
                  )
               );
            }
         }

         return shape;
      }
   }
}
