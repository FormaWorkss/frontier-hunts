package com.formaworks.frontierhunts.expedition;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class TrailCameraBlock extends Block implements EntityBlock {
   public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
   public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

   public TrailCameraBlock(Properties var1) {
      super(var1);
      this.registerDefaultState((BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(FACING, Direction.NORTH)).setValue(ACTIVE, true));
   }

   protected MapCodec<TrailCameraBlock> codec() {
      return simpleCodec(TrailCameraBlock::new);
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> var1) {
      var1.add(new Property[]{FACING, ACTIVE});
   }

   protected BlockState rotate(BlockState var1, Rotation var2) {
      return (BlockState)var1.setValue(FACING, var2.rotate((Direction)var1.getValue(FACING)));
   }

   protected BlockState mirror(BlockState var1, Mirror var2) {
      return var1.rotate(var2.getRotation((Direction)var1.getValue(FACING)));
   }

   public BlockEntity newBlockEntity(BlockPos var1, BlockState var2) {
      return new TrailCamera(var1, var2);
   }

   public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level var1, BlockState var2, BlockEntityType<T> var3) {
      return !var1.isClientSide && var3 == ExpeditionContent.CAMERA.get()
         ? (var0, var1x, var2x, var3x) -> TrailCamera.tick(var0, var1x, var2x, (TrailCamera)var3x)
         : null;
   }

   protected VoxelShape getShape(BlockState var1, BlockGetter var2, BlockPos var3, CollisionContext var4) {
      return switch ((Direction)var1.getValue(FACING)) {
         case NORTH -> box(4.0, 3.0, 10.0, 12.0, 14.0, 16.0);
         case SOUTH -> box(4.0, 3.0, 0.0, 12.0, 14.0, 6.0);
         case WEST -> box(10.0, 3.0, 4.0, 16.0, 14.0, 12.0);
         default -> box(0.0, 3.0, 4.0, 6.0, 14.0, 12.0);
      };
   }

   protected boolean canSurvive(BlockState var1, LevelReader var2, BlockPos var3) {
      BlockPos var4 = var3.relative(((Direction)var1.getValue(FACING)).getOpposite());
      return var2.getBlockState(var4).isFaceSturdy(var2, var4, (Direction)var1.getValue(FACING));
   }

   protected BlockState updateShape(BlockState var1, Direction var2, BlockState var3, LevelAccessor var4, BlockPos var5, BlockPos var6) {
      return this.canSurvive(var1, var4, var5) ? var1 : Blocks.AIR.defaultBlockState();
   }

   protected void onRemove(BlockState var1, Level var2, BlockPos var3, BlockState var4, boolean var5) {
      if (!var1.is(var4.getBlock()) && var2 instanceof ServerLevel var6) {
         CameraRegistry.get(var6).remove(var6, var3);
      }

      super.onRemove(var1, var2, var3, var4, var5);
   }

   public BlockState getStateForPlacement(BlockPlaceContext var1) {
      for (Direction var5 : var1.getNearestLookingDirections()) {
         if (var5.getAxis().isHorizontal()) {
            BlockState var6 = (BlockState)this.defaultBlockState().setValue(FACING, var5.getOpposite());
            if (var6.canSurvive(var1.getLevel(), var1.getClickedPos())) {
               return var6;
            }
         }
      }

      return null;
   }

   public void setPlacedBy(Level var1, BlockPos var2, BlockState var3, LivingEntity var4, ItemStack var5) {
      if (var1 instanceof ServerLevel var6 && var1.getBlockEntity(var2) instanceof TrailCamera var7) {
         CompoundTag var10 = ExpeditionWeapon.data(var5);
         var7.restore(var10);
         CameraRegistry.Station var9 = var7.station(var6);
         if (var4 != null) {
            var9.owner = var4.getUUID();
         }

         if (var10.contains("camera_name")) {
            var9.name = var10.getString("camera_name");
         }

         CameraRegistry.get(var6).touch();
         var7.setChanged();
         var1.setBlock(var2, (BlockState)var3.setValue(ACTIVE, var9.charge > 0), 2);
         return;
      }
   }

   protected List<ItemStack> getDrops(BlockState var1, net.minecraft.world.level.storage.loot.LootParams.Builder var2) {
      List var3 = super.getDrops(var1, var2);
      if (var2.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof TrailCamera var4 && var4.getLevel() instanceof ServerLevel var5) {
         CameraRegistry.Station var11 = CameraRegistry.get(var5).remove(var5, var4.getBlockPos());
         if (var11 != null) {
            for (ItemStack var8 : var3) {
               if (var8.is(this.asItem())) {
                  CompoundTag var9 = ExpeditionWeapon.data(var8);
                  var9.putInt("field_charge", var11.charge);
                  if (!var11.name.isEmpty()) {
                     var9.putString("camera_name", var11.name);
                  }

                  var9.put("camera_log", CameraRegistry.saveRoll(var11.roll));
                  ExpeditionWeapon.save(var8, var9);
               }
            }
         }
      }

      return var3;
   }

   protected InteractionResult useWithoutItem(BlockState var1, Level var2, BlockPos var3, Player var4, BlockHitResult var5) {
      if (var2 instanceof ServerLevel var6 && var4 instanceof ServerPlayer var7 && var2.getBlockEntity(var3) instanceof TrailCamera var8) {
         ScoutingNetwork.sendRoll(var7, var8.station(var6));
      }

      return InteractionResult.sidedSuccess(var2.isClientSide);
   }

   protected ItemInteractionResult useItemOn(ItemStack var1, BlockState var2, Level var3, BlockPos var4, Player var5, InteractionHand var6, BlockHitResult var7) {
      if (!(var1.getItem() instanceof FieldElectronics.Battery)) {
         return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
      } else {
         if (var3 instanceof ServerLevel var8 && var3.getBlockEntity(var4) instanceof TrailCamera var9) {
            CameraRegistry.Station var12 = var9.station(var8);
            if (var12.charge >= 2400) {
               return ItemInteractionResult.CONSUME;
            }

            var12.charge = 2400;
            CameraRegistry.get(var8).touch();
            var3.setBlock(var4, (BlockState)var2.setValue(ACTIVE, true), 2);
            if (!var5.hasInfiniteMaterials()) {
               var1.shrink(1);
            }

            if (var5 instanceof ServerPlayer var11) {
               ExpeditionService.message(var11, "Camera battery replaced · 100%");
            }
         }

         return ItemInteractionResult.sidedSuccess(var3.isClientSide);
      }
   }
}
