package com.formaworks.frontierhunts.benches;

import com.formaworks.frontierhunts.workshop.WideStationBlock;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * [benches] One of the three workbenches: a two-block-wide station (left half where it was placed, right half to the
 * placer's left, like a bed; see {@link WideStationBlock}). Using either half opens the bench screen ({@link BenchMenu})
 * at the left half.
 *
 * <p>Shape per half: the 15 px work surface over the full footprint and the thin back wall with its cap rail (the
 * models' back wall stands at z 14.5..16 up to y 26, facing north).
 */
public final class BenchBlock extends WideStationBlock {
   public static final MapCodec<BenchBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
         Codec.STRING.fieldOf("bench").forGetter((BenchBlock b) -> b.bench.id),
         propertiesCodec()
      ).apply(i, (id, props) -> new BenchBlock(benchById(id), props)));
   private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

   static {
      VoxelShape body = Block.box(0.0, 0.0, 0.0, 16.0, 15.0, 16.0);
      SHAPES.put(Direction.NORTH, Shapes.or(body, Block.box(0.0, 15.0, 14.5, 16.0, 26.0, 16.0)));
      SHAPES.put(Direction.SOUTH, Shapes.or(body, Block.box(0.0, 15.0, 0.0, 16.0, 26.0, 1.5)));
      SHAPES.put(Direction.EAST, Shapes.or(body, Block.box(0.0, 15.0, 0.0, 1.5, 26.0, 16.0)));
      SHAPES.put(Direction.WEST, Shapes.or(body, Block.box(14.5, 15.0, 0.0, 16.0, 26.0, 16.0)));
   }

   public final Bench bench;

   public BenchBlock(Bench bench, Properties props) {
      super(props);
      this.bench = bench;
   }

   private static Bench benchById(String id) {
      for (Bench b : Bench.values()) {
         if (b.id.equals(id)) {
            return b;
         }
      }
      return Bench.FRONTIER;
   }

   @Override
   protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
      return CODEC;
   }

   @Override
   protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
      VoxelShape s = SHAPES.get(state.getValue(FACING));
      return s == null ? Shapes.block() : s;
   }

   @Override
   protected RenderShape getRenderShape(BlockState state) {
      return RenderShape.MODEL;
   }

   @Override
   protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
      BlockPos origin = origin(pos, state);
      Bench b = this.bench;
      return new SimpleMenuProvider((id, inv, p) -> new BenchMenu(id, inv, level, origin, b), Component.translatable(b.nameKey()));
   }

   @Override
   protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
      if (!level.isClientSide && player instanceof ServerPlayer sp && !sp.isSpectator()) {
         sp.openMenu(this.getMenuProvider(state, level, pos));
         BenchProgress.opened(sp, this.bench);
      }
      return InteractionResult.sidedSuccess(level.isClientSide);
   }
}
