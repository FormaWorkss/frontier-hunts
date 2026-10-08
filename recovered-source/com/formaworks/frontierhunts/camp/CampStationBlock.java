package com.formaworks.frontierhunts.camp;

import com.formaworks.frontierhunts.expedition.ReserveArchitecture;
import com.formaworks.frontierhunts.workshop.StationShapes;
import com.formaworks.frontierhunts.workshop.WideStationBlock;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class CampStationBlock extends WideStationBlock implements EntityBlock {
   public static final BooleanProperty WORKING = BlockStateProperties.LIT;
   private static final MapCodec<CampStationBlock> CODEC = RecordCodecBuilder.mapCodec(
      var0 -> var0.group(Codec.BOOL.fieldOf("smoke").forGetter(var0x -> var0x.smoke), propertiesCodec()).apply(var0, CampStationBlock::new)
   );
   public final boolean smoke;

   public CampStationBlock(boolean var1, Properties var2) {
      super(var2);
      this.smoke = var1;
      this.registerDefaultState((BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(FACING, Direction.NORTH)).setValue(WORKING, false));
   }

   protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
      return CODEC;
   }

   @Override
   protected void createBlockStateDefinition(Builder<Block, BlockState> var1) {
      super.createBlockStateDefinition(var1);
      var1.add(new Property[]{WORKING});
   }

   @Override
   protected boolean widePlacement() {
      return !BuiltInRegistries.BLOCK.getKey(this).getPath().equals("lodge_stove");
   }

   @Override
   public BlockState getStateForPlacement(BlockPlaceContext var1) {
      return super.getStateForPlacement(var1);
   }

   @Override
   protected BlockState rotate(BlockState var1, Rotation var2) {
      return (BlockState)var1.setValue(FACING, var2.rotate((Direction)var1.getValue(FACING)));
   }

   @Override
   protected BlockState mirror(BlockState var1, Mirror var2) {
      return super.mirror(var1, var2);
   }

   protected VoxelShape getShape(BlockState var1, BlockGetter var2, BlockPos var3, CollisionContext var4) {
      if (this.widePlacement()) {
         return StationShapes.get(var1);
      } else if (this.smoke) {
         VoxelShape var5 = switch ((Direction)var1.getValue(FACING)) {
            case EAST -> box(3.0, 14.0, 9.0, 7.0, 16.0, 13.0);
            case SOUTH -> box(3.0, 14.0, 3.0, 7.0, 16.0, 7.0);
            case WEST -> box(9.0, 14.0, 3.0, 13.0, 16.0, 7.0);
            default -> box(9.0, 14.0, 9.0, 13.0, 16.0, 13.0);
         };
         return Shapes.or(box(2.0, 0.0, 2.0, 14.0, 14.0, 14.0), var5);
      } else {
         return ((Direction)var1.getValue(FACING)).getAxis() == Axis.Z ? box(1.0, 0.0, 5.0, 15.0, 16.0, 11.0) : box(5.0, 0.0, 1.0, 11.0, 16.0, 15.0);
      }
   }

   public BlockEntity newBlockEntity(BlockPos var1, BlockState var2) {
      return secondary(var2) ? null : new CampStation(var1, var2);
   }

   public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level var1, BlockState var2, BlockEntityType<T> var3) {
      return !var1.isClientSide && var3 == CampContent.STATION.get()
         ? (var0, var1x, var2x, var3x) -> CampStation.tick(var0, var1x, var2x, (CampStation)var3x)
         : null;
   }

   protected MenuProvider getMenuProvider(BlockState var1, Level var2, BlockPos var3) {
      return var2.getBlockEntity(origin(var3, var1)) instanceof CampStation var4 ? var4 : null;
   }

   protected InteractionResult useWithoutItem(BlockState var1, Level var2, BlockPos var3, Player var4, BlockHitResult var5) {
      if (!var2.isClientSide && var2.getBlockEntity(origin(var3, var1)) instanceof CampStation var6) {
         var4.openMenu(var6);
      }

      return InteractionResult.sidedSuccess(var2.isClientSide);
   }

   @Override
   protected void onRemove(BlockState var1, Level var2, BlockPos var3, BlockState var4, boolean var5) {
      if (!var1.is(var4.getBlock()) && var2.getBlockEntity(var3) instanceof CampStation var6) {
         Containers.dropContents(var2, var3, var6);
         var2.updateNeighbourForOutputSignal(var3, this);
      }

      super.onRemove(var1, var2, var3, var4, var5);
   }

   public void animateTick(BlockState var1, Level var2, BlockPos var3, RandomSource var4) {
      if (!secondary(var1) && this.smoke && (Boolean)var1.getValue(WORKING) && var4.nextInt(5) == 0) {
         double var5 = var1.getValue(PART) == WideStationBlock.Part.SINGLE ? 0.305 : 1.11;
         double var7 = 0.25;
         switch ((Direction)var1.getValue(FACING)) {
            case EAST:
               double var13 = var5;
               var5 = -var7;
               var7 = var13;
               break;
            case SOUTH:
               var5 = -var5;
               var7 = -var7;
               break;
            case WEST:
               double var9 = var5;
               var5 = var7;
               var7 = -var9;
         }

         double var14 = (double)var3.getY() + 1.51;
         if (!this.widePlacement()) {
            var7 = 0.0;
            var5 = 0.0;
            var14 = (double)var3.getY() + 1.01;
            Block var11 = (Block)ReserveArchitecture.BLOCKS.get("stove_flue").get();

            for (int var12 = 1; var12 <= 12 && var2.getBlockState(var3.above(var12)).is(var11); var12++) {
               var14 = (double)(var3.getY() + var12) + 1.01;
            }
         }

         var2.addParticle(ParticleTypes.SMOKE, (double)var3.getX() + 0.5 + var5, var14, (double)var3.getZ() + 0.5 + var7, 0.0, 0.015, 0.0);
      }
   }
}
