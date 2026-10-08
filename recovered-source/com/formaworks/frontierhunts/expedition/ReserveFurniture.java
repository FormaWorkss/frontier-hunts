package com.formaworks.frontierhunts.expedition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.Plane;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class ReserveFurniture extends HorizontalDirectionalBlock {
   private static final MapCodec<ReserveFurniture> CODEC = RecordCodecBuilder.mapCodec(
      var0 -> var0.group(Codec.STRING.fieldOf("furniture").forGetter(var0x -> var0x.id), propertiesCodec()).apply(var0, ReserveFurniture::new)
   );
   public final String id;
   private final VoxelShape[] facingShapes = new VoxelShape[4];

   ReserveFurniture(String var1, Properties var2) {
      super(var2);
      this.id = var1;
      this.registerDefaultState((BlockState)((BlockState)this.stateDefinition.any()).setValue(FACING, Direction.NORTH));

      for (Direction var4 : Plane.HORIZONTAL) {
         this.facingShapes[var4.get2DDataValue()] = this.buildShape((BlockState)this.defaultBlockState().setValue(FACING, var4));
      }
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

   protected VoxelShape getShape(BlockState var1, BlockGetter var2, BlockPos var3, CollisionContext var4) {
      return this.facingShapes[((Direction)var1.getValue(FACING)).get2DDataValue()];
   }

   protected VoxelShape getVisualShape(BlockState var1, BlockGetter var2, BlockPos var3, CollisionContext var4) {
      return this.id.equals("lodge_chair") ? Shapes.empty() : super.getVisualShape(var1, var2, var3, var4);
   }

   private VoxelShape buildShape(BlockState var1) {
      String var2 = this.id;

      return switch (var2) {
         case "roof_slope_low", "roof_slope_high", "roof_gable_low", "roof_gable_high", "roof_ridge_low", "roof_ridge_high", "tent_slope", "tent_gable", "tent_ridge", "tent_end_ridge" -> {
            boolean var15 = this.id.endsWith("high");
            boolean var16 = this.id.contains("ridge");
            boolean var17 = this.id.contains("gable") || this.id.equals("tent_end_ridge");
            boolean var18 = this.id.startsWith("tent");
            VoxelShape var20 = Shapes.empty();

            for (int var9 = 0; var9 < 16; var9++) {
               double var10 = (double)(var15 ? 8 : 0) + (double)(var16 ? Math.min(var9 + 1, 16 - var9) : var9 + 1) * (var18 ? 1.0 : 0.5);
               int var12 = var9;
               int var13 = var9 + 1;
               if (var1.getValue(FACING) == Direction.WEST || var1.getValue(FACING) == Direction.NORTH) {
                  var12 = 15 - var9;
                  var13 = 16 - var9;
               }

               boolean var14 = ((Direction)var1.getValue(FACING)).getAxis() == Axis.X;
               var20 = Shapes.or(
                  var20,
                  Block.box(
                     var14 ? (double)var12 : 0.0,
                     var17 ? 0.0 : Math.max(0.0, var10 - (var18 ? 1.0 : 0.5)),
                     var14 ? 0.0 : (double)var12,
                     var14 ? (double)var13 : 16.0,
                     var10 + 1.2,
                     var14 ? 16.0 : (double)var13
                  )
               );
            }

            yield var20;
         }
         case "stove_flue" -> Block.box(6.0, 0.0, 6.0, 10.0, 16.0, 10.0);
         case "stove_roof_flashing" -> Shapes.or(Block.box(6.0, 0.0, 6.0, 10.0, 16.0, 10.0), Block.box(0.0, 0.0, 0.0, 16.0, 1.2, 16.0));
         case "camp_cot" -> Block.box(0.0, 0.0, 0.0, 16.0, 7.0, 16.0);
         case "cabin_lantern" -> Block.box(5.5, 0.0, 5.5, 10.5, 8.0, 10.5);
         case "lodge_chair" -> Block.box(3.0, 0.0, 3.0, 13.0, 15.0, 13.0);
         case "lodge_table" -> Block.box(0.0, 0.0, 0.0, 16.0, 13.0, 16.0);
         case "lit_lodge_table" -> Shapes.or(Block.box(0.0, 0.0, 0.0, 16.0, 13.0, 16.0), Block.box(5.5, 13.0, 5.5, 10.5, 21.0, 10.5));
         case "timber_brace", "timber_cross_brace", "timber_stair_guard" -> {
            VoxelShape var4 = Shapes.empty();
            boolean var5 = var1.getValue(FACING) == Direction.SOUTH || var1.getValue(FACING) == Direction.WEST;
            boolean var6 = ((Direction)var1.getValue(FACING)).getAxis() == Axis.Z;

            for (int var7 = 0; var7 < 16; var7++) {
               int var8 = var5 ? 15 - var7 : var7;
               var4 = Shapes.or(
                  var4,
                  Block.box(
                     var6 ? (double)var8 : 6.0,
                     (double)Math.max(0, var7 - 2),
                     var6 ? 6.0 : (double)var8,
                     var6 ? (double)(var8 + 1) : 10.0,
                     (double)Math.min(16, var7 + 3),
                     var6 ? 10.0 : (double)(var8 + 1)
                  )
               );
               if (this.id.equals("timber_stair_guard")) {
                  var4 = Shapes.or(
                     var4,
                     new VoxelShape[]{
                        Block.box(
                           var6 ? (double)var8 : 6.0,
                           (double)(14 + Math.max(0, var7 - 2)),
                           var6 ? 6.0 : (double)var8,
                           var6 ? (double)(var8 + 1) : 10.0,
                           (double)(14 + Math.min(16, var7 + 3)),
                           var6 ? 10.0 : (double)(var8 + 1)
                        ),
                        Block.box(6.0, 0.0, 6.0, 10.0, 25.0, 10.0)
                     }
                  );
               }

               if (this.id.equals("timber_cross_brace")) {
                  var8 = 15 - var8;
                  var4 = Shapes.or(
                     var4,
                     Block.box(
                        var6 ? (double)var8 : 6.0,
                        (double)Math.max(0, var7 - 2),
                        var6 ? 6.0 : (double)var8,
                        var6 ? (double)(var8 + 1) : 10.0,
                        (double)Math.min(16, var7 + 3),
                        var6 ? 10.0 : (double)(var8 + 1)
                     )
                  );
               }
            }

            yield var4;
         }
         case "ranger_window" -> ((Direction)var1.getValue(FACING)).getAxis() == Axis.Z
         ? Block.box(0.0, 0.0, 6.0, 16.0, 16.0, 10.0)
         : Block.box(6.0, 0.0, 0.0, 10.0, 16.0, 16.0);
         default -> Block.box(1.0, 0.0, 1.0, 15.0, 16.0, 15.0);
      };
   }

   protected InteractionResult useWithoutItem(BlockState var1, Level var2, BlockPos var3, Player var4, BlockHitResult var5) {
      if (var4 instanceof ServerPlayer var6) {
         if (this.id.equals("lodge_chair")) {
            if (var4.isShiftKeyDown()) {
               return InteractionResult.PASS;
            } else {
               TreeStandSeat.sitChair(var6, var3, (Direction)var1.getValue(FACING));
               return InteractionResult.CONSUME;
            }
         } else if (this.id.equals("camp_cot")) {
            if (var6.getCooldowns().isOnCooldown(var1.getBlock().asItem())) {
               return InteractionResult.CONSUME;
            } else if (!var2.dimensionType().bedWorks()) {
               ExpeditionService.message(var6, "This reserve cot needs an Overworld campsite.");
               return InteractionResult.CONSUME;
            } else if (!var2.getEntitiesOfClass(Monster.class, new AABB(var3).inflate(8.0, 5.0, 8.0)).isEmpty()) {
               ExpeditionService.message(var6, "Make the campsite safe before resting.");
               return InteractionResult.CONSUME;
            } else {
               var6.setRespawnPosition(var2.dimension(), var3.above(), var6.getYRot(), true, true);
               var6.heal(2.0F);
               var6.getCooldowns().addCooldown(var1.getBlock().asItem(), 100);
               ExpeditionService.message(var6, "Campsite respawn set · rest restores a little health");
               return InteractionResult.CONSUME;
            }
         } else if (this.id.equals("trail_sign")) {
            ExpeditionService.send(var6, true);
            return InteractionResult.CONSUME;
         } else {
            return InteractionResult.PASS;
         }
      } else {
         return InteractionResult.SUCCESS;
      }
   }
}
