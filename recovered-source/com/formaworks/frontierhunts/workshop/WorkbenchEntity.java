package com.formaworks.frontierhunts.workshop;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public final class WorkbenchEntity extends BlockEntity {
   public WorkbenchEntity(BlockPos var1, BlockState var2) {
      super((BlockEntityType)WorkshopContent.ENTITY.get(), var1, var2);
   }
}
