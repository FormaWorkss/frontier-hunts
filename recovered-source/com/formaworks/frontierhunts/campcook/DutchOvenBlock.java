package com.formaworks.frontierhunts.campcook;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * [licence] Camp Dutch Oven: a cast-iron pot with a lid. Cooks camp dishes over heat: set it on a campfire (it sits on
 * an iron grate over the flames; {@link #GRATE}), over a lit furnace, smoker or stove, or burn coals under and on the
 * lid (fuel slot). {@link #LIT} = cooking now (glowing coals, steam from the lid, simmering).
 */
public class DutchOvenBlock extends BaseEntityBlock {
   public static final MapCodec<DutchOvenBlock> CODEC = simpleCodec(DutchOvenBlock::new);
   public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
   public static final BooleanProperty LIT = BlockStateProperties.LIT;
   public static final BooleanProperty GRATE = BooleanProperty.create("grate");
   private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 13.0, 14.0);

   public DutchOvenBlock(Properties p) {
      super(p);
      this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false).setValue(GRATE, false));
   }

   @Override
   protected MapCodec<? extends BaseEntityBlock> codec() {
      return CODEC;
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
      b.add(FACING, LIT, GRATE);
   }

   static boolean overFire(BlockGetter level, BlockPos pos) {
      return level.getBlockState(pos.below()).getBlock() instanceof CampfireBlock;
   }

   @Override
   public BlockState getStateForPlacement(BlockPlaceContext ctx) {
      return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite())
         .setValue(GRATE, overFire(ctx.getLevel(), ctx.getClickedPos()));
   }

   @Override
   protected BlockState updateShape(BlockState s, Direction dir, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos) {
      if (dir == Direction.DOWN) {
         return s.setValue(GRATE, other.getBlock() instanceof CampfireBlock);
      }
      return s;
   }

   @Override
   protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
      return SHAPE;
   }

   @Override
   protected RenderShape getRenderShape(BlockState s) {
      return RenderShape.MODEL;
   }

   @Override
   public BlockEntity newBlockEntity(BlockPos pos, BlockState s) {
      return new DutchOvenEntity(pos, s);
   }

   @Override
   public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState s, BlockEntityType<T> type) {
      return createTickerHelper(type, CampCookContent.DUTCH_OVEN_ENTITY.get(), level.isClientSide ? DutchOvenEntity::clientTick : DutchOvenEntity::serverTick);
   }

   @Override
   protected InteractionResult useWithoutItem(BlockState s, Level level, BlockPos pos, Player player, BlockHitResult hit) {
      if (!level.isClientSide && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof DutchOvenEntity be) {
         sp.openMenu(be, pos);
         level.playSound(null, pos, CampCookContent.SND_LID.get(), SoundSource.BLOCKS, 0.6F, 0.95F + level.random.nextFloat() * 0.1F);
      }
      return InteractionResult.sidedSuccess(level.isClientSide);
   }

   @Override
   protected void onRemove(BlockState s, Level level, BlockPos pos, BlockState now, boolean moved) {
      if (!s.is(now.getBlock())) {
         if (level.getBlockEntity(pos) instanceof DutchOvenEntity be) {
            Containers.dropContents(level, pos, be);
         }
      }
      super.onRemove(s, level, pos, now, moved);
   }

   @Override
   protected boolean hasAnalogOutputSignal(BlockState s) {
      return true;
   }

   @Override
   protected int getAnalogOutputSignal(BlockState s, Level level, BlockPos pos) {
      return level.getBlockEntity(pos) instanceof DutchOvenEntity be ? be.signal() : 0;
   }

   @Override
   public void animateTick(BlockState s, Level level, BlockPos pos, RandomSource r) {
      if (!s.getValue(LIT)) {
         return;
      }
      boolean grate = s.getValue(GRATE);
      if (!grate && r.nextFloat() < 0.3F) {
         // embers from the coals under the pot and on the lid
         boolean top = r.nextBoolean();
         level.addParticle(ParticleTypes.SMALL_FLAME, pos.getX() + 0.5 + (r.nextDouble() - 0.5) * 0.6, pos.getY() + (top ? 10.8 : 0.6) / 16.0,
            pos.getZ() + 0.5 + (r.nextDouble() - 0.5) * 0.6, 0.0, 0.004, 0.0);
      }
      if (r.nextFloat() < 0.45F) { // animateTick reaches a given block only every ~25 ticks
         level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, CampCookContent.SND_SIMMER.get(), SoundSource.BLOCKS,
            0.35F + r.nextFloat() * 0.15F, 0.9F + r.nextFloat() * 0.2F, false);
      }
   }
}
