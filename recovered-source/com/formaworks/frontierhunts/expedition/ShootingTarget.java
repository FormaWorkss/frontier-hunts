package com.formaworks.frontierhunts.expedition;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class ShootingTarget extends Block implements EntityBlock {
   public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

   public ShootingTarget(Properties var1) {
      super(var1);
      this.registerDefaultState((BlockState)((BlockState)this.stateDefinition.any()).setValue(FACING, Direction.NORTH));
   }

   protected MapCodec<ShootingTarget> codec() {
      return simpleCodec(ShootingTarget::new);
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> var1) {
      var1.add(new Property[]{FACING});
   }

   public BlockState getStateForPlacement(BlockPlaceContext var1) {
      return (BlockState)this.defaultBlockState().setValue(FACING, var1.getHorizontalDirection().getOpposite());
   }

   protected BlockState rotate(BlockState var1, Rotation var2) {
      return (BlockState)var1.setValue(FACING, var2.rotate((Direction)var1.getValue(FACING)));
   }

   protected BlockState mirror(BlockState var1, Mirror var2) {
      return var1.rotate(var2.getRotation((Direction)var1.getValue(FACING)));
   }

   public BlockEntity newBlockEntity(BlockPos var1, BlockState var2) {
      return new TargetFace(var1, var2);
   }

   protected VoxelShape getShape(BlockState var1, BlockGetter var2, BlockPos var3, CollisionContext var4) {
      VoxelShape var5 = switch ((Direction)var1.getValue(FACING)) {
         case NORTH -> box(1.0, 3.0, 1.0, 15.0, 16.0, 5.0);
         case SOUTH -> box(1.0, 3.0, 11.0, 15.0, 16.0, 15.0);
         case WEST -> box(1.0, 3.0, 1.0, 5.0, 16.0, 15.0);
         default -> box(11.0, 3.0, 1.0, 15.0, 16.0, 15.0);
      };

      VoxelShape var6 = switch ((Direction)var1.getValue(FACING)) {
         case NORTH -> Shapes.or(box(2.0, 0.0, 1.0, 5.0, 3.0, 5.0), box(11.0, 0.0, 1.0, 14.0, 3.0, 5.0));
         case SOUTH -> Shapes.or(box(2.0, 0.0, 11.0, 5.0, 3.0, 15.0), box(11.0, 0.0, 11.0, 14.0, 3.0, 15.0));
         case WEST -> Shapes.or(box(1.0, 0.0, 2.0, 5.0, 3.0, 5.0), box(1.0, 0.0, 11.0, 5.0, 3.0, 14.0));
         default -> Shapes.or(box(11.0, 0.0, 2.0, 15.0, 3.0, 5.0), box(11.0, 0.0, 11.0, 15.0, 3.0, 14.0));
      };
      return Shapes.or(var5, var6);
   }

   protected boolean canSurvive(BlockState var1, LevelReader var2, BlockPos var3) {
      BlockPos var4 = var3.below();
      return var2.getBlockState(var4).isFaceSturdy(var2, var4, Direction.UP);
   }

   protected BlockState updateShape(BlockState var1, Direction var2, BlockState var3, LevelAccessor var4, BlockPos var5, BlockPos var6) {
      return this.canSurvive(var1, var4, var5) ? var1 : Blocks.AIR.defaultBlockState();
   }

   protected void onRemove(BlockState var1, Level var2, BlockPos var3, BlockState var4, boolean var5) {
      if (!var2.isClientSide && !var1.is(var4.getBlock()) && var2.getBlockEntity(var3) instanceof TargetFace var6) {
         for (ItemStack var8 : var6.pull()) {
            Containers.dropItemStack(var2, (double)var3.getX() + 0.5, (double)var3.getY() + 0.6, (double)var3.getZ() + 0.5, var8);
         }
      }

      super.onRemove(var1, var2, var3, var4, var5);
   }

   protected InteractionResult useWithoutItem(BlockState var1, Level var2, BlockPos var3, Player var4, BlockHitResult var5) {
      if (var2.isClientSide) {
         return InteractionResult.SUCCESS;
      } else {
         if (!(var2.getBlockEntity(var3) instanceof TargetFace var6) || !(var4 instanceof ServerPlayer var7)) {
            return InteractionResult.CONSUME;
         }

         if (var4.isShiftKeyDown()) {
            for (ItemStack var15 : var6.pull()) {
               ExpeditionService.give(var7, var15);
            }

            var6.wipe();
            var2.sendBlockUpdated(var3, var1, var1, 3);
            ExpeditionService.message(var7, "Face cleared");
            return InteractionResult.CONSUME;
         } else {
            float[] var13 = TargetFace.local(var3, (Direction)var1.getValue(FACING), var5.getLocation());
            Optional var9 = var6.pullNearest(var13[0], var13[1], 0.16);
            if (var9.isPresent()) {
               ExpeditionService.give(var7, (ItemStack)var9.get());
               var6.tell(var7, 1);
               var2.playSound(null, var3, SoundEvents.ARROW_HIT_PLAYER, SoundSource.BLOCKS, 0.5F, 0.78F + var2.random.nextFloat() * 0.24F);
               var2.sendBlockUpdated(var3, var1, var1, 3);
               return InteractionResult.CONSUME;
            } else {
               List var10 = var6.pull();

               for (ItemStack var12 : var10) {
                  ExpeditionService.give(var7, var12);
               }

               var6.tell(var7, var10.size());
               if (!var10.isEmpty()) {
                  var2.playSound(null, var3, SoundEvents.ARROW_HIT_PLAYER, SoundSource.BLOCKS, 0.5F, 0.8F);
                  var2.sendBlockUpdated(var3, var1, var1, 3);
               }

               return InteractionResult.CONSUME;
            }
         }
      }
   }
}
