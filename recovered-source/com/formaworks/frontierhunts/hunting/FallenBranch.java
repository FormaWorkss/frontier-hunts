package com.formaworks.frontierhunts.hunting;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class FallenBranch extends HorizontalDirectionalBlock {
   public static final MapCodec<FallenBranch> CODEC = simpleCodec(FallenBranch::new);
   public static final BooleanProperty SNAPPED = BooleanProperty.create("snapped");
   private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 2.5, 15.0);
   private static final Map<Long, Long> LAST = new ConcurrentHashMap<>();
   public static final double SNAP_RANGE = 26.0;
   public static final double CRACKLE_RANGE = 10.0;

   public FallenBranch(Properties var1) {
      super(var1);
      this.registerDefaultState((BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(FACING, Direction.NORTH)).setValue(SNAPPED, false));
   }

   protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
      return CODEC;
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> var1) {
      var1.add(new Property[]{FACING, SNAPPED});
   }

   public BlockState getStateForPlacement(BlockPlaceContext var1) {
      return (BlockState)this.defaultBlockState().setValue(FACING, var1.getHorizontalDirection());
   }

   protected VoxelShape getShape(BlockState var1, BlockGetter var2, BlockPos var3, CollisionContext var4) {
      return SHAPE;
   }

   protected VoxelShape getCollisionShape(BlockState var1, BlockGetter var2, BlockPos var3, CollisionContext var4) {
      return Shapes.empty();
   }

   protected boolean canSurvive(BlockState var1, LevelReader var2, BlockPos var3) {
      return var2.getBlockState(var3.below()).isFaceSturdy(var2, var3.below(), Direction.UP);
   }

   protected BlockState updateShape(BlockState var1, Direction var2, BlockState var3, LevelAccessor var4, BlockPos var5, BlockPos var6) {
      return var2 == Direction.DOWN && !this.canSurvive(var1, var4, var5)
         ? Blocks.AIR.defaultBlockState()
         : super.updateShape(var1, var2, var3, var4, var5, var6);
   }

   protected void entityInside(BlockState var1, Level var2, BlockPos var3, Entity var4) {
      if (!(var2 instanceof ServerLevel var5) || !(var4 instanceof Player var6) || var6.isSpectator()) {
         return;
      }

      if (!var6.isCrouching()) {
         Vec3 var7 = var6.getDeltaMovement();
         if (!(var7.x * var7.x + var7.z * var7.z < 1.0E-4) || !var6.onGround()) {
            long var8 = var5.getGameTime();
            Long var10 = LAST.get(var3.asLong());
            if (var10 == null || var8 - var10 >= 12L) {
               LAST.put(var3.asLong(), var8);
               if (LAST.size() > 512) {
                  LAST.entrySet().removeIf(var2x -> var8 - var2x.getValue() > 200L);
               }

               boolean var11 = !(Boolean)var1.getValue(SNAPPED);
               if (var11) {
                  var5.setBlock(var3, (BlockState)var1.setValue(SNAPPED, true), 3);
                  var5.playSound(
                     null,
                     var3,
                     (SoundEvent)HuntSounds.BRANCH_SNAP.get(),
                     SoundSource.BLOCKS,
                     var6.isSprinting() ? 1.2F : 1.0F,
                     0.85F + var5.random.nextFloat() * 0.3F
                  );
                  WhitetailHearing.snap(var5, var3.getBottomCenter(), var6.isSprinting() ? 32.5 : 26.0);
               } else {
                  var5.playSound(null, var3, (SoundEvent)HuntSounds.BRANCH_CRACKLE.get(), SoundSource.BLOCKS, 0.6F, 0.9F + var5.random.nextFloat() * 0.25F);
                  WhitetailHearing.snap(var5, var3.getBottomCenter(), 10.0);
               }
            }
         }
      }
   }
}
