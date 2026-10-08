package com.formaworks.frontierhunts.expedition;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

final class GunRackShape {
   private static final VoxelShape NORTH = Shapes.or(Block.box(0.0, 0.0, 11.5, 16.0, 16.0, 16.0), Block.box(1.0, 2.0, 8.0, 15.0, 15.0, 11.5));
   private static final VoxelShape SOUTH = Shapes.or(Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 4.5), Block.box(1.0, 2.0, 4.5, 15.0, 15.0, 8.0));
   private static final VoxelShape WEST = Shapes.or(Block.box(11.5, 0.0, 0.0, 16.0, 16.0, 16.0), Block.box(8.0, 2.0, 1.0, 11.5, 15.0, 15.0));
   private static final VoxelShape EAST = Shapes.or(Block.box(0.0, 0.0, 0.0, 4.5, 16.0, 16.0), Block.box(4.5, 2.0, 1.0, 8.0, 15.0, 15.0));

   static VoxelShape get(BlockState var0) {
      if (!var0.hasProperty(HorizontalDirectionalBlock.FACING)) {
         return Shapes.block();
      } else {
         return switch ((Direction)var0.getValue(HorizontalDirectionalBlock.FACING)) {
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
            default -> NORTH;
         };
      }
   }

   private GunRackShape() {
   }
}
