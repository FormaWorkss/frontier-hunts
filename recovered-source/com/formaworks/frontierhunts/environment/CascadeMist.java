package com.formaworks.frontierhunts.environment;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class CascadeMist extends Block {
   public static final int LIP = 0;
   public static final int FACE = 1;
   public static final int FOOT = 2;
   public static final IntegerProperty WHERE = IntegerProperty.create("where", 0, 2);
   public static final IntegerProperty SIZE = IntegerProperty.create("size", 0, 2);
   public static final MapCodec<CascadeMist> CODEC = simpleCodec(CascadeMist::new);

   public CascadeMist(Properties var1) {
      super(var1);
      this.registerDefaultState((BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(WHERE, 1)).setValue(SIZE, 1));
   }

   protected MapCodec<CascadeMist> codec() {
      return CODEC;
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> var1) {
      var1.add(new Property[]{WHERE, SIZE});
   }

   protected RenderShape getRenderShape(BlockState var1) {
      return RenderShape.INVISIBLE;
   }

   protected VoxelShape getShape(BlockState var1, BlockGetter var2, BlockPos var3, CollisionContext var4) {
      return Shapes.empty();
   }

   protected VoxelShape getCollisionShape(BlockState var1, BlockGetter var2, BlockPos var3, CollisionContext var4) {
      return Shapes.empty();
   }

   protected boolean propagatesSkylightDown(BlockState var1, BlockGetter var2, BlockPos var3) {
      return true;
   }

   protected float getShadeBrightness(BlockState var1, BlockGetter var2, BlockPos var3) {
      return 1.0F;
   }

   public void animateTick(BlockState var1, Level var2, BlockPos var3, RandomSource var4) {
      double var5 = (double)var3.getX();
      double var7 = (double)var3.getY();
      double var9 = (double)var3.getZ();
      int var11 = (Integer)var1.getValue(SIZE);
      switch (var1.getValue(WHERE)) {
         case 0:
            for (int var15 = 0; var15 < 2; var15++) {
               var2.addParticle(
                  ParticleTypes.SPLASH,
                  var5 + var4.nextDouble(),
                  var7 + 0.2 + var4.nextDouble() * 0.6,
                  var9 + var4.nextDouble(),
                  (var4.nextDouble() - 0.5) * 0.1,
                  -0.02,
                  (var4.nextDouble() - 0.5) * 0.1
               );
            }

            if (var4.nextInt(3) == 0) {
               var2.addParticle(ParticleTypes.FALLING_WATER, var5 + var4.nextDouble(), var7 + 0.9, var9 + var4.nextDouble(), 0.0, 0.0, 0.0);
            }
            break;
         case 2:
            for (int var13 = 0; var13 < 3 + var11 * 2; var13++) {
               var2.addParticle(
                  ParticleTypes.CLOUD,
                  var5 + var4.nextDouble() * 2.0 - 0.5,
                  var7 + var4.nextDouble() * 1.8,
                  var9 + var4.nextDouble() * 2.0 - 0.5,
                  (var4.nextDouble() - 0.5) * 0.06,
                  0.035 + var4.nextDouble() * 0.07,
                  (var4.nextDouble() - 0.5) * 0.06
               );
            }

            for (int var14 = 0; var14 < 3; var14++) {
               var2.addParticle(
                  ParticleTypes.SPLASH,
                  var5 + var4.nextDouble(),
                  var7 + var4.nextDouble() * 0.8,
                  var9 + var4.nextDouble(),
                  (var4.nextDouble() - 0.5) * 0.22,
                  0.1 + var4.nextDouble() * 0.12,
                  (var4.nextDouble() - 0.5) * 0.22
               );
            }

            if (var4.nextInt(2) == 0) {
               var2.addParticle(
                  ParticleTypes.RAIN, var5 + var4.nextDouble() * 3.0 - 1.0, var7 + 1.0 + var4.nextDouble(), var9 + var4.nextDouble() * 3.0 - 1.0, 0.0, 0.0, 0.0
               );
            }
            break;
         default:
            for (int var12 = 0; var12 < 2 + var11; var12++) {
               var2.addParticle(
                  ParticleTypes.CLOUD,
                  var5 + var4.nextDouble() * 1.6 - 0.3,
                  var7 + var4.nextDouble() * 1.4,
                  var9 + var4.nextDouble() * 1.6 - 0.3,
                  (var4.nextDouble() - 0.5) * 0.04,
                  0.012 + var4.nextDouble() * 0.04,
                  (var4.nextDouble() - 0.5) * 0.04
               );
            }

            if (var4.nextInt(2) == 0) {
               var2.addParticle(ParticleTypes.FALLING_WATER, var5 + var4.nextDouble(), var7 + var4.nextDouble(), var9 + var4.nextDouble(), 0.0, 0.0, 0.0);
            }

            if (var4.nextInt(5) == 0) {
               var2.addParticle(
                  ParticleTypes.SPLASH,
                  var5 + var4.nextDouble(),
                  var7 + var4.nextDouble(),
                  var9 + var4.nextDouble(),
                  (var4.nextDouble() - 0.5) * 0.14,
                  -0.06,
                  (var4.nextDouble() - 0.5) * 0.14
               );
            }
      }
   }
}
