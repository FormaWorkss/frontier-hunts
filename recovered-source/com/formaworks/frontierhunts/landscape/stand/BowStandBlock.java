package com.formaworks.frontierhunts.landscape.stand;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class BowStandBlock extends HorizontalDirectionalBlock implements EntityBlock {
   public static final MapCodec<BowStandBlock> CODEC = simpleCodec(BowStandBlock::new);
   private static final VoxelShape[] SHAPES = new VoxelShape[4];

   public BowStandBlock(Properties var1) {
      super(var1);
      this.registerDefaultState((BlockState)((BlockState)this.stateDefinition.any()).setValue(FACING, Direction.NORTH));
   }

   protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
      return CODEC;
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

   static VoxelShape rotate(VoxelShape var0, Direction var1) {
      if (var1 == Direction.NORTH) {
         return var0;
      } else {
         VoxelShape[] var2 = new VoxelShape[]{Shapes.empty()};
         var0.forAllBoxes((var2x, var4, var6, var8, var10, var12) -> {
            var2[0] = Shapes.or(var2[0], switch (var1) {
               case EAST -> Shapes.box(1.0 - var12, var4, var2x, 1.0 - var6, var10, var8);
               case SOUTH -> Shapes.box(1.0 - var8, var4, 1.0 - var12, 1.0 - var2x, var10, 1.0 - var6);
               default -> Shapes.box(var6, var4, 1.0 - var8, var12, var10, 1.0 - var2x);
            });
         });
         return var2[0].optimize();
      }
   }

   protected VoxelShape getShape(BlockState var1, BlockGetter var2, BlockPos var3, CollisionContext var4) {
      return SHAPES[((Direction)var1.getValue(FACING)).get2DDataValue()];
   }

   public BlockEntity newBlockEntity(BlockPos var1, BlockState var2) {
      return new BowStandBlockEntity(var1, var2);
   }

   protected ItemInteractionResult useItemOn(ItemStack var1, BlockState var2, Level var3, BlockPos var4, Player var5, InteractionHand var6, BlockHitResult var7) {
      if (var1.isEmpty()) {
         return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
      } else if (var3.getBlockEntity(var4) instanceof BowStandBlockEntity var8) {
         int var10 = BowStandBlockEntity.slotFor(var1);
         if (var10 >= 0 && var8.get(var10).isEmpty()) {
            if (!var3.isClientSide) {
               var8.set(var10, var1.copyWithCount(1));
               if (!var5.getAbilities().instabuild) {
                  var1.shrink(1);
               }

               var3.playSound(
                  null,
                  var4,
                  var10 == 0 ? (SoundEvent)SoundEvents.ARMOR_EQUIP_GENERIC.value() : (SoundEvent)SoundEvents.ARMOR_EQUIP_LEATHER.value(),
                  SoundSource.BLOCKS,
                  0.7F,
                  1.1F
               );
            }

            return ItemInteractionResult.sidedSuccess(var3.isClientSide);
         } else {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
         }
      } else {
         return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
      }
   }

   protected InteractionResult useWithoutItem(BlockState var1, Level var2, BlockPos var3, Player var4, BlockHitResult var5) {
      if (var2.getBlockEntity(var3) instanceof BowStandBlockEntity var6) {
         int var9 = var4.isShiftKeyDown() ? 1 : 0;
         if (var6.get(var9).isEmpty()) {
            var9 = 1 - var9;
         }

         if (var6.get(var9).isEmpty()) {
            return InteractionResult.PASS;
         } else {
            if (!var2.isClientSide) {
               ItemStack var8 = var6.get(var9).copy();
               var6.set(var9, ItemStack.EMPTY);
               if (!var4.getInventory().add(var8)) {
                  var4.drop(var8, false);
               }

               var2.playSound(null, var3, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5F, 1.0F);
            }

            return InteractionResult.sidedSuccess(var2.isClientSide);
         }
      } else {
         return InteractionResult.PASS;
      }
   }

   protected void onRemove(BlockState var1, Level var2, BlockPos var3, BlockState var4, boolean var5) {
      if (!var1.is(var4.getBlock()) && var2.getBlockEntity(var3) instanceof BowStandBlockEntity var6) {
         for (int var8 = 0; var8 < 2; var8++) {
            if (!var6.get(var8).isEmpty()) {
               Containers.dropItemStack(var2, (double)var3.getX() + 0.5, (double)var3.getY() + 0.6, (double)var3.getZ() + 0.5, var6.get(var8));
            }
         }
      }

      super.onRemove(var1, var2, var3, var4, var5);
   }

   static {
      for (Direction var1 : Plane.HORIZONTAL) {
         VoxelShape var2 = box(0.5, 0.0, 2.0, 15.5, 2.6, 14.0);
         VoxelShape var3 = box(3.4, 2.6, 5.2, 12.6, 8.2, 11.8);
         VoxelShape var4 = box(0.8, 8.2, 9.4, 15.2, 27.4, 12.8);
         VoxelShape var5 = Shapes.or(var2, new VoxelShape[]{var3, var4});
         SHAPES[var1.get2DDataValue()] = rotate(var5, var1);
      }
   }
}
