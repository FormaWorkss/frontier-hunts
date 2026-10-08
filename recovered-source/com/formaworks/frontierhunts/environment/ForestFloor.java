package com.formaworks.frontierhunts.environment;

import com.formaworks.frontierhunts.hunting.HuntSounds;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ForestFloor extends Block {
   public final int loudness;
   private final VoxelShape shape;

   public ForestFloor(Properties var1, int var2, double var3) {
      super(var1);
      this.loudness = var2;
      this.shape = box(2.0, 0.0, 2.0, 14.0, Math.max(1.0, var3 * 16.0), 14.0);
   }

   protected MapCodec<? extends Block> codec() {
      return simpleCodec(var1 -> new ForestFloor(var1, this.loudness, 1.0));
   }

   protected VoxelShape getShape(BlockState var1, BlockGetter var2, BlockPos var3, CollisionContext var4) {
      Vec3 var5 = var1.getOffset(var2, var3);
      return this.shape.move(var5.x, var5.y, var5.z);
   }

   protected VoxelShape getCollisionShape(BlockState var1, BlockGetter var2, BlockPos var3, CollisionContext var4) {
      return Shapes.empty();
   }

   protected boolean isPathfindable(BlockState var1, PathComputationType var2) {
      return true;
   }

   public static boolean support(LevelReader var0, BlockPos var1) {
      BlockState var2 = var0.getBlockState(var1.below());
      return var2.is(BlockTags.DIRT)
         || var2.is(Blocks.FARMLAND)
         || var2.is(BlockTags.SAND)
         || var2.is(Blocks.MOSS_BLOCK)
         || var2.is(Blocks.MUD)
         || var2.is(Blocks.CLAY)
         || var2.is(BlockTags.TERRACOTTA)
         || var2.is(Blocks.SNOW_BLOCK)
         || var2.is(Blocks.POWDER_SNOW);
   }

   protected boolean canSurvive(BlockState var1, LevelReader var2, BlockPos var3) {
      return support(var2, var3);
   }

   protected BlockState updateShape(BlockState var1, Direction var2, BlockState var3, LevelAccessor var4, BlockPos var5, BlockPos var6) {
      return var2 == Direction.DOWN && !this.canSurvive(var1, var4, var5) ? Blocks.AIR.defaultBlockState() : var1;
   }

   public static Vec3 dust(RandomSource var0) {
      return new Vec3(0.42 + (double)var0.nextFloat() * 0.16, 0.32 + (double)var0.nextFloat() * 0.12, 0.17 + (double)var0.nextFloat() * 0.08);
   }

   public static final class Boulder extends Block {
      private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 9.0, 15.0);

      public Boulder(Properties var1) {
         super(var1);
      }

      protected MapCodec<ForestFloor.Boulder> codec() {
         return simpleCodec(ForestFloor.Boulder::new);
      }

      protected VoxelShape getShape(BlockState var1, BlockGetter var2, BlockPos var3, CollisionContext var4) {
         return SHAPE;
      }

      protected boolean canSurvive(BlockState var1, LevelReader var2, BlockPos var3) {
         return var2.getBlockState(var3.below()).isFaceSturdy(var2, var3.below(), Direction.UP);
      }
   }

   public static final class Litter extends ForestFloor {
      public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 2);

      public Litter(Properties var1) {
         super(var1, 1, 0.125);
         this.registerDefaultState((BlockState)((BlockState)this.stateDefinition.any()).setValue(VARIANT, 0));
      }

      @Override
      protected MapCodec<ForestFloor.Litter> codec() {
         return simpleCodec(ForestFloor.Litter::new);
      }

      protected void createBlockStateDefinition(Builder<Block, BlockState> var1) {
         var1.add(new Property[]{VARIANT});
      }

      public BlockState getStateForPlacement(BlockPlaceContext var1) {
         return (BlockState)this.defaultBlockState().setValue(VARIANT, var1.getLevel().getRandom().nextInt(3));
      }
   }

   public static final class Undergrowth extends ForestFloor {
      public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 6);
      private static final float[] DRAG = new float[]{1.0F, 1.0F, 0.93F, 0.84F, 0.96F, 1.0F, 1.0F};
      private static final float[] TALL = new float[]{0.95F, 1.05F, 0.8F, 0.75F, 0.75F, 1.5F, 0.85F};

      public Undergrowth(Properties var1) {
         super(var1, 2, 0.8);
         this.registerDefaultState((BlockState)((BlockState)this.stateDefinition.any()).setValue(VARIANT, 0));
      }

      @Override
      protected MapCodec<ForestFloor.Undergrowth> codec() {
         return simpleCodec(ForestFloor.Undergrowth::new);
      }

      protected void createBlockStateDefinition(Builder<Block, BlockState> var1) {
         var1.add(new Property[]{VARIANT});
      }

      public BlockState getStateForPlacement(BlockPlaceContext var1) {
         return (BlockState)this.defaultBlockState().setValue(VARIANT, var1.getLevel().getRandom().nextInt(7));
      }

      protected void entityInside(BlockState var1, Level var2, BlockPos var3, Entity var4) {
         if (var4 instanceof LivingEntity var5 && !var5.isSpectator()) {
            int var6 = (Integer)var1.getValue(VARIANT);
            Vec3 var7 = var4.getDeltaMovement();
            float var8 = DRAG[var6];
            if (var8 < 0.99F) {
               var4.setDeltaMovement(var7.x * (double)var8, var7.y, var7.z * (double)var8);
            }

            double var9 = var7.horizontalDistance();
            if (!(var9 < 0.045) && !var4.isCrouching() && var4.onGround()) {
               if (var2.isClientSide) {
                  int var11 = var9 > 0.17 ? 3 : 1;
                  RandomSource var12 = var2.getRandom();

                  for (int var13 = 0; var13 < var11; var13++) {
                     var2.addParticle(
                        new BlockParticleOption(ParticleTypes.BLOCK, var1),
                        var4.getX() + (var12.nextDouble() - 0.5) * 0.8,
                        (double)var3.getY() + 0.15 + var12.nextDouble() * (double)TALL[var6],
                        var4.getZ() + (var12.nextDouble() - 0.5) * 0.8,
                        -var7.x * 0.45 + (var12.nextDouble() - 0.5) * 0.09,
                        0.02 + var12.nextDouble() * 0.06,
                        -var7.z * 0.45 + (var12.nextDouble() - 0.5) * 0.09
                     );
                  }
               } else {
                  boolean var14 = var4 instanceof Player;
                  int var15 = var14 ? 8 : 17;
                  if (var4.tickCount % var15 != 0) {
                     return;
                  }

                  SoundEvent var16 = (SoundEvent)HuntSounds.BRUSH_RUSTLE.get();
                  var2.playSound(
                     null,
                     var4.getX(),
                     var4.getY(),
                     var4.getZ(),
                     var16,
                     SoundSource.BLOCKS,
                     (float)Math.min(var14 ? 0.46 : 0.3, (var14 ? 0.12 : 0.08) + var9 * 1.1),
                     0.72F + var2.getRandom().nextFloat() * 0.34F
                  );
               }

               return;
            }

            return;
         }
      }
   }
}
