package com.formaworks.frontierhunts.workshop;

import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;

public abstract class WideStationBlock extends HorizontalDirectionalBlock {
   public static final EnumProperty<WideStationBlock.Part> PART = EnumProperty.create("bench_part", WideStationBlock.Part.class);

   protected WideStationBlock(Properties var1) {
      super(var1);
      this.registerDefaultState(
         (BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(FACING, Direction.NORTH)).setValue(PART, WideStationBlock.Part.SINGLE)
      );
   }

   protected boolean widePlacement() {
      return true;
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> var1) {
      var1.add(new Property[]{FACING, PART});
   }

   public static boolean secondary(BlockState var0) {
      return var0.hasProperty(PART) && var0.getValue(PART) == WideStationBlock.Part.RIGHT;
   }

   public static BlockPos origin(BlockPos var0, BlockState var1) {
      return secondary(var1) ? var0.relative(((Direction)var1.getValue(FACING)).getCounterClockWise()) : var0;
   }

   public static BlockPos partner(BlockPos var0, BlockState var1) {
      return var0.relative(secondary(var1) ? ((Direction)var1.getValue(FACING)).getCounterClockWise() : ((Direction)var1.getValue(FACING)).getClockWise());
   }

   private static boolean paired(BlockState var0, BlockState var1) {
      return var0.is(var1.getBlock())
         && var1.hasProperty(PART)
         && var0.getValue(FACING) == var1.getValue(FACING)
         && var0.getValue(PART) != WideStationBlock.Part.SINGLE
         && var1.getValue(PART) == (secondary(var0) ? WideStationBlock.Part.LEFT : WideStationBlock.Part.RIGHT);
   }

   public BlockState getStateForPlacement(BlockPlaceContext var1) {
      BlockState var2 = (BlockState)this.defaultBlockState().setValue(FACING, var1.getHorizontalDirection().getOpposite());
      if (!this.widePlacement()) {
         return var2;
      } else {
         BlockPos var3 = partner(var1.getClickedPos(), var2);
         return var1.getLevel().hasChunkAt(var3)
               && var1.getLevel().getWorldBorder().isWithinBounds(var3)
               && var1.getLevel().getBlockState(var3).canBeReplaced(var1)
            ? (BlockState)var2.setValue(PART, WideStationBlock.Part.LEFT)
            : null;
      }
   }

   public void setPlacedBy(Level var1, BlockPos var2, BlockState var3, LivingEntity var4, ItemStack var5) {
      super.setPlacedBy(var1, var2, var3, var4, var5);
      if (!var1.isClientSide && var3.getValue(PART) == WideStationBlock.Part.LEFT) {
         var1.setBlock(partner(var2, var3), (BlockState)var3.setValue(PART, WideStationBlock.Part.RIGHT), 3);
      }
   }

   protected BlockState rotate(BlockState var1, Rotation var2) {
      return (BlockState)var1.setValue(FACING, var2.rotate((Direction)var1.getValue(FACING)));
   }

   protected BlockState mirror(BlockState var1, Mirror var2) {
      BlockState var3 = var1.rotate(var2.getRotation((Direction)var1.getValue(FACING)));
      return var2 != Mirror.NONE && var1.getValue(PART) != WideStationBlock.Part.SINGLE
         ? (BlockState)var3.setValue(PART, secondary(var1) ? WideStationBlock.Part.LEFT : WideStationBlock.Part.RIGHT)
         : var3;
   }

   protected BlockState updateShape(BlockState var1, Direction var2, BlockState var3, LevelAccessor var4, BlockPos var5, BlockPos var6) {
      if (!var4.isClientSide() && var1.getValue(PART) != WideStationBlock.Part.SINGLE) {
         var4.scheduleTick(var5, this, 1);
      }

      return var1;
   }

   protected void tick(BlockState var1, ServerLevel var2, BlockPos var3, RandomSource var4) {
      if (var1.getValue(PART) != WideStationBlock.Part.SINGLE) {
         BlockPos var5 = partner(var3, var1);
         if (var2.hasChunkAt(var5) && !paired(var1, var2.getBlockState(var5))) {
            var2.destroyBlock(var3, true);
         }
      }
   }

   protected void onRemove(BlockState var1, Level var2, BlockPos var3, BlockState var4, boolean var5) {
      super.onRemove(var1, var2, var3, var4, var5);
      if (!var2.isClientSide && !var4.is(this) && var1.getValue(PART) != WideStationBlock.Part.SINGLE) {
         BlockPos var6 = partner(var3, var1);
         if (var2.hasChunkAt(var6) && paired(var1, var2.getBlockState(var6))) {
            var2.setBlock(var6, Blocks.AIR.defaultBlockState(), 3);
         }
      }
   }

   public static enum Part implements StringRepresentable {
      SINGLE,
      LEFT,
      RIGHT;

      public String getSerializedName() {
         return this.name().toLowerCase(Locale.ROOT);
      }
   }
}
