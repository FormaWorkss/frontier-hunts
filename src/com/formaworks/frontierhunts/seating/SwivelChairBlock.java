package com.formaworks.frontierhunts.seating;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * [onboard2] Swivel chair (ground blinds, tower blinds). While someone sits, OCCUPIED makes the block model drop the
 * seat top and the {@link SeatEntity}'s renderer draws it turned with the sitter's body. OCCUPIED is kept honest by a
 * once-a-second block tick (a crash, a logout on the seat or a removed seat entity can never leave a chair topless).
 */
public class SwivelChairBlock extends SeatBlock {
   public static final MapCodec<SwivelChairBlock> CODEC = simpleCodec(p -> new SwivelChairBlock(SeatKind.BLIND_CHAIR, p));
   public static final BooleanProperty OCCUPIED = BooleanProperty.create("occupied");

   public SwivelChairBlock(SeatKind kind, Properties props) {
      super(kind, props);
      this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OCCUPIED, false));
   }

   @Override
   protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
      return CODEC;
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
      b.add(FACING, OCCUPIED);
   }

   @Override
   protected void onPlace(BlockState s, Level level, BlockPos pos, BlockState old, boolean moved) {
      super.onPlace(s, level, pos, old, moved);
      if (!level.isClientSide && s.getValue(OCCUPIED)) {
         level.scheduleTick(pos, this, 20);
      }
   }

   @Override
   protected void tick(BlockState s, ServerLevel level, BlockPos pos, RandomSource random) {
      if (!s.getValue(OCCUPIED)) {
         return;
      }
      if (SeatEntity.occupied(level, pos)) {
         level.scheduleTick(pos, this, 20);
      } else {
         level.setBlock(pos, s.setValue(OCCUPIED, false), Block.UPDATE_ALL);
      }
   }
}
