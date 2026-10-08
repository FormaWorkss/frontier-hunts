package com.formaworks.frontierhunts.expedition;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.CollisionContext;

public final class FieldSpotlight extends DirectionalBlock {
   public static final BooleanProperty LIT = BlockStateProperties.LIT;
   private static final MapCodec<FieldSpotlight> CODEC = simpleCodec(FieldSpotlight::new);

   public FieldSpotlight(Properties var1) {
      super(var1);
      this.registerDefaultState((BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(FACING, Direction.NORTH)).setValue(LIT, true));
   }

   protected MapCodec<? extends DirectionalBlock> codec() {
      return CODEC;
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> var1) {
      var1.add(new Property[]{FACING, LIT});
   }

   protected void onPlace(BlockState var1, Level var2, BlockPos var3, BlockState var4, boolean var5) {
      if (!var2.isClientSide) {
         var2.scheduleTick(var3, this, 2);
      }
   }

   protected InteractionResult useWithoutItem(BlockState var1, Level var2, BlockPos var3, Player var4, BlockHitResult var5) {
      var2.setBlock(var3, (BlockState)var1.cycle(LIT), 3);
      return InteractionResult.sidedSuccess(var2.isClientSide);
   }

   protected void tick(BlockState var1, ServerLevel var2, BlockPos var3, RandomSource var4) {
      if ((Boolean)var1.getValue(LIT)) {
         Direction var5 = (Direction)var1.getValue(FACING);

         for (byte var6 = 2; var6 <= 22; var6 += 2) {
            BlockPos var7 = var3.relative(var5, var6);
            if (!var2.hasChunkAt(var7) || !clear(var2, var3, var7)) {
               break;
            }

            BlockState var8 = var2.getBlockState(var7);
            if (var8.isAir() || var8.is((Block)ExpeditionContent.BEAM.get())) {
               var2.setBlock(
                  var7,
                  (BlockState)((FieldSpotlight.Beam)ExpeditionContent.BEAM.get())
                     .defaultBlockState()
                     .setValue(FieldSpotlight.Beam.POWER, Math.clamp((long)(16 - var6 / 9), 13, 15)),
                  3
               );
               var2.scheduleTick(var7, (Block)ExpeditionContent.BEAM.get(), 30);
            }
         }
      }

      var2.scheduleTick(var3, this, 20);
   }

   private static boolean clear(Level var0, BlockPos var1, BlockPos var2) {
      Vec3 var3 = Vec3.atCenterOf(var1).add(Vec3.atCenterOf(var2).subtract(Vec3.atCenterOf(var1)).normalize().scale(0.65));
      return var0.clip(new ClipContext(var3, Vec3.atCenterOf(var2), net.minecraft.world.level.ClipContext.Block.COLLIDER, Fluid.NONE, CollisionContext.empty()))
            .getType()
         == Type.MISS;
   }

   public static final class Beam extends Block {
      public static final IntegerProperty POWER = BlockStateProperties.LEVEL;

      public Beam(Properties var1) {
         super(var1);
         this.registerDefaultState((BlockState)((BlockState)this.stateDefinition.any()).setValue(POWER, 12));
      }

      protected void createBlockStateDefinition(Builder<Block, BlockState> var1) {
         var1.add(new Property[]{POWER});
      }

      protected RenderShape getRenderShape(BlockState var1) {
         return RenderShape.INVISIBLE;
      }

      protected void tick(BlockState var1, ServerLevel var2, BlockPos var3, RandomSource var4) {
         if (FieldFlashlight.holds(var2, var3)) {
            var2.scheduleTick(var3, this, 6);
         } else {
            for (Direction var8 : Direction.values()) {
               for (byte var9 = 2; var9 <= 22; var9 += 2) {
                  BlockPos var10 = var3.relative(var8, var9);
                  if (var2.hasChunkAt(var10)) {
                     BlockState var11 = var2.getBlockState(var10);
                     if (var11.is((Block)ExpeditionContent.SPOTLIGHT.get())
                        && (Boolean)var11.getValue(FieldSpotlight.LIT)
                        && var11.getValue(DirectionalBlock.FACING) == var8.getOpposite()
                        && FieldSpotlight.clear(var2, var10, var3)) {
                        var2.scheduleTick(var3, this, 30);
                        return;
                     }
                  }
               }
            }

            var2.removeBlock(var3, false);
         }
      }
   }
}
