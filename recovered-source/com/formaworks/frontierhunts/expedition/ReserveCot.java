package com.formaworks.frontierhunts.expedition;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

final class ReserveCot extends BedBlock {
   ReserveCot(Properties var1) {
      super(DyeColor.GRAY, var1);
   }

   public RenderShape getRenderShape(BlockState var1) {
      return RenderShape.MODEL;
   }

   public BlockEntity newBlockEntity(BlockPos var1, BlockState var2) {
      return new ReserveCot.Anchor(var1, var2);
   }

   static final class Anchor extends BlockEntity {
      Anchor(BlockPos var1, BlockState var2) {
         super((BlockEntityType)ExpeditionContent.COT_ANCHOR.get(), var1, var2);
      }
   }
}
