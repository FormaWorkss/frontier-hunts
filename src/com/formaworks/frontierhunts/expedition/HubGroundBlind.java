package com.formaworks.frontierhunts.expedition;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class HubGroundBlind extends HorizontalDirectionalBlock {
   public static final MapCodec<HubGroundBlind> CODEC = simpleCodec(HubGroundBlind::new);
   public static final IntegerProperty PART = IntegerProperty.create("part", 0, 81);
   public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
   private static final List<BlockPos> PARTS = parts();
   private final VoxelShape[][][] shapes = new VoxelShape[82][2][4];
   private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);
   private static final int[][][][] LOCAL = localIndex();

   public HubGroundBlind(Properties var1) {
      super(var1.forceSolidOn());
      this.registerDefaultState(
         this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, Integer.valueOf(0)).setValue(OPEN, Boolean.valueOf(true))
      );

      for (int var2 = 0; var2 < 2; var2++) {
         List<AABB> var3 = construction(var2 == 1);

         for (int var4 = 0; var4 < 82; var4++) {
            BlockPos var5 = PARTS.get(var4);
            VoxelShape var6 = Shapes.empty();

            for (AABB var8 : var3) {
               AABB var9 = var4 < 25
                  ? var8
                  : new AABB(
                     0.5 + (var8.minX - 0.5) * 1.5,
                     var8.minY,
                     0.5 + (var8.minZ - 0.5) * 1.5,
                     0.5 + (var8.maxX - 0.5) * 1.5,
                     var8.maxY,
                     0.5 + (var8.maxZ - 0.5) * 1.5
                  );
               double var10 = Math.max(var9.minX, (double)var5.getX());
               double var12 = Math.max(var9.minY, (double)var5.getY());
               double var14 = Math.max(var9.minZ, (double)var5.getZ());
               double var16 = Math.min(var9.maxX, (double)(var5.getX() + 1));
               double var18 = Math.min(var9.maxY, (double)(var5.getY() + 1));
               double var20 = Math.min(var9.maxZ, (double)(var5.getZ() + 1));
               if (var16 - var10 > 1.0E-7 && var18 - var12 > 1.0E-7 && var20 - var14 > 1.0E-7) {
                  var6 = Shapes.or(
                     var6,
                     Shapes.box(
                        var10 - (double)var5.getX(),
                        var12 - (double)var5.getY(),
                        var14 - (double)var5.getZ(),
                        var16 - (double)var5.getX(),
                        var18 - (double)var5.getY(),
                        var20 - (double)var5.getZ()
                     )
                  );
               }
            }

            var6 = var6.optimize();

            for (Direction var24 : Plane.HORIZONTAL) {
               this.shapes[var4][var2][var24.get2DDataValue()] = rotateShape(var6, var24);
            }
         }
      }
   }

   private static List<BlockPos> parts() {
      ArrayList<BlockPos> var0 = new ArrayList<>();

      for (int var1 = 1; var1 <= 2; var1++) {
         for (int var2 = 0; var2 < 3; var2++) {
            for (int var3 = -var1; var3 <= var1; var3++) {
               for (int var4 = -var1; var4 <= var1; var4++) {
                  if (var2 == 2 || Math.abs(var4) == var1 || Math.abs(var3) == var1) {
                     var0.add(new BlockPos(var4, var2, var3));
                  }
               }
            }
         }
      }

      return List.copyOf(var0);
   }

   public static int firstPart() {
      return 25;
   }

   public static int partCount() {
      return PARTS.size();
   }

   private static int start(BlockState var0) {
      return var0.getValue(PART) < 25 ? 0 : 25;
   }

   private static int end(BlockState var0) {
      return var0.getValue(PART) < 25 ? 25 : 82;
   }

   @Override
   protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
      return CODEC;
   }

   @Override
   protected void createBlockStateDefinition(Builder<Block, BlockState> var1) {
      var1.add(FACING, PART, OPEN);
   }

   @Override
   protected BlockState rotate(BlockState var1, Rotation var2) {
      return var1.setValue(FACING, var2.rotate(var1.getValue(FACING)));
   }

   @Override
   protected BlockState mirror(BlockState var1, Mirror var2) {
      return var1.rotate(var2.getRotation(var1.getValue(FACING)));
   }

   public static BlockPos offset(int var0, Direction var1) {
      BlockPos var2 = PARTS.get(var0);

      return switch (var1) {
         case EAST -> new BlockPos(-var2.getZ(), var2.getY(), var2.getX());
         case SOUTH -> new BlockPos(-var2.getX(), var2.getY(), -var2.getZ());
         case WEST -> new BlockPos(var2.getZ(), var2.getY(), -var2.getX());
         default -> var2;
      };
   }

   public static BlockPos base(BlockPos var0, BlockState var1) {
      return var0.subtract(offset(var1.getValue(PART), var1.getValue(FACING)));
   }

   /**
    * dev.61 rework: the blind needs only a 1 x 1 spot of firm ground with two blocks of headroom. It still deploys
    * as the full 3 x 3 blind (all 57 frame parts), but a frame part is only set where the cell is empty air;
    * grass, flowers, leaves, trunks and anything else already there are left untouched. Plants, leaves and trunks
    * inside the blind's volume are hidden client side and leaves/plants lose their collision there
    * (see {@link BlindVolumes}), so nothing is ever broken, levelled or replaced - packing the blind up simply
    * removes its own parts and everything reappears. (The old pitch levelled a 5 x 5 pad with TentPitch and
    * cleared the interior, which is what dug holes in the floor when placing with right click.)
    */
   /** Where the blind would stand for this click, or null (with the reason in why[0]). Pure: runs on both sides. */
   private static BlockPos target(Level level, UseOnContext ctx, String[] why) {
      BlockPos pos = ctx.getClickedPos();
      byte kind = BlindVolumes.kind(level.getBlockState(pos));
      if (kind == BlindVolumes.PLANT || kind == BlindVolumes.LEAF) {
         // aimed at grass, a flower or a low bush: stand the blind on the ground underneath it
         for (int i = 0; i < 4; i++) {
            byte k = BlindVolumes.kind(level.getBlockState(pos));
            if (k != BlindVolumes.PLANT && k != BlindVolumes.LEAF) {
               break;
            }

            pos = pos.below();
         }
      } else if (ctx.getClickedFace() != Direction.UP) {
         why[0] = "Aim at the top of the ground where the blind should stand.";
         return null;
      }

      return pos.above();
   }

   /** Server: build the blind. */
   public static boolean place(UseOnContext var0) {
      if (!(var0.getPlayer() instanceof ServerPlayer var1)) {
         return false;
      }

      String[] why = new String[1];
      BlockPos at = target(var1.serverLevel(), var0, why);
      if (at == null) {
         ExpeditionService.message(var1, why[0]);
         return false;
      }

      return assemble(var1, at, var1.getDirection().getOpposite(), var0.getItemInHand());
   }

   /**
    * Client: show the finished blind the moment the player clicks, using exactly the server's plan. The use runs
    * inside the client's block prediction, so if the server decides otherwise its answer replaces this.
    */
   public static void predict(UseOnContext ctx) {
      Level level = ctx.getLevel();
      Player player = ctx.getPlayer();
      if (!level.isClientSide || player == null) {
         return;
      }

      try {
         String[] why = new String[1];
         BlockPos at = target(level, ctx, why);
         if (at == null) {
            return;
         }

         List<BlockPos> cells = new ArrayList<>();
         List<BlockState> states = new ArrayList<>();
         if (plan(level, player, at, player.getDirection().getOpposite(), ctx.getItemInHand(), why, cells, states)) {
            for (int i = 0; i < cells.size(); i++) {
               level.setBlock(cells.get(i), states.get(i), 26); // 8: rebuild the sections right away
            }
         }
      } catch (RuntimeException e) {
         // prediction is cosmetic; the server still builds the blind
      }
   }

   private static boolean room(Level var0, BlockPos var1) {
      BlockState var2 = var0.getBlockState(var1);
      if (!var0.getFluidState(var1).isEmpty()) {
         return false;
      } else if (var2.isAir()) {
         return true;
      } else {
         byte var3 = BlindVolumes.kind(var2);
         return var3 == BlindVolumes.PLANT || var3 == BlindVolumes.LEAF;
      }
   }

   /**
    * Which frame parts go where (only into open air; plants, leaves and trunks already there stay and are hidden).
    * Shared by the server build and the client prediction so both always agree.
    */
   private static boolean plan(Level var4, Player var0, BlockPos var1, Direction var2, ItemStack var3, String[] why, List<BlockPos> cells, List<BlockState> states) {
      if (!var2.getAxis().isHorizontal()) {
         return false;
      }

      HubGroundBlind var5 = ExpeditionContent.GROUND_BLIND.get();
      BlockPos var6 = var1.below();
      if (!var4.hasChunkAt(var6) || !var4.getBlockState(var6).isFaceSturdy(var4, var6, Direction.UP)) {
         why[0] = "Set the blind on firm ground: it needs one solid block to stand on.";
         return false;
      }

      if (!room(var4, var1) || !room(var4, var1.above())) {
         why[0] = "The blind needs two blocks of headroom above that spot (grass and leaves are fine).";
         return false;
      }

      BlockPos var7 = var1.offset(-2, 0, -2);
      BlockPos var8 = var1.offset(2, 2, 2);
      for (BlockPos var9 : BlockPos.betweenClosed(var7, var8)) {
         if (!var4.hasChunkAt(var9) || !var4.isInWorldBounds(var9) || !var4.getWorldBorder().isWithinBounds(var9)) {
            why[0] = "You cannot set the blind up here.";
            return false;
         }

         Block var10 = var4.getBlockState(var9).getBlock();
         if (var10 instanceof HubGroundBlind || var10 instanceof TowerBlind) {
            why[0] = "Another blind is already set up here.";
            return false;
         }
      }

      if (BlindVolumes.overlaps(var4, var7, var8)) {
         why[0] = "Another blind is already set up here.";
         return false;
      }

      if (!var4.mayInteract(var0, var1) || !var0.mayUseItemAt(var1, Direction.UP, var3)) {
         why[0] = "You cannot set the blind up here.";
         return false;
      }

      for (int var12 = 25; var12 < 82; var12++) {
         BlockPos var13 = var1.offset(offset(var12, var2));
         BlockState var14 = var4.getBlockState(var13);
         BlockState var15 = var5.defaultBlockState().setValue(PART, Integer.valueOf(var12)).setValue(FACING, var2);
         boolean var16 = var14.isAir() && var4.getFluidState(var13).isEmpty();
         byte var17 = BlindVolumes.kind(var14);
         if (var16 || var17 == BlindVolumes.PLANT || var17 == BlindVolumes.LEAF) {
            // real or stand-in frame here: nobody may be standing in it
            if (blocked(var4, var13, var15)) {
               why[0] = "Step clear of the shelter frame before placing it.";
               return false;
            }
         }

         if (var16 && var4.mayInteract(var0, var13)) {
            cells.add(var13.immutable());
            states.add(var15);
         }
      }

      if (cells.isEmpty()) {
         why[0] = "There is no open air around this spot for the blind's frame.";
         return false;
      }

      return true;
   }

   public static boolean assemble(ServerPlayer var0, BlockPos var1, Direction var2, ItemStack var3) {
      ServerLevel var4 = var0.serverLevel();
      HubGroundBlind var5 = ExpeditionContent.GROUND_BLIND.get();
      String[] why = new String[1];
      List<BlockPos> cells = new ArrayList<>();
      List<BlockState> states = new ArrayList<>();
      if (!plan(var4, var0, var1, var2, var3, why, cells, states)) {
         if (why[0] != null) {
            ExpeditionService.message(var0, why[0]);
         }

         return false;
      }

      ArrayList<BlockPos> var18 = new ArrayList<>();
      for (int i = 0; i < cells.size(); i++) {
         BlockPos var20 = cells.get(i);
         if (!var4.setBlock(var20, states.get(i), 18)) {
            REMOVING.set(true);

            try {
               for (BlockPos var21 : var18) {
                  var4.setBlock(var21, Blocks.AIR.defaultBlockState(), 18);
               }
            } finally {
               REMOVING.set(false);
            }

            return false;
         }

         var18.add(var20);
      }

      var18.forEach(var2x -> var4.updateNeighborsAt(var2x, var5));
      var4.scheduleTick(var18.get(0), var5, 20);
      return true;
   }

   private static boolean blocked(Level var0, BlockPos var1, BlockState var2) {
      for (AABB var4 : var2.getCollisionShape(var0, var1).toAabbs()) {
         if (!var0.getEntitiesOfClass(LivingEntity.class, var4.move(var1)).isEmpty()) {
            return true;
         }
      }

      return false;
   }

   private static boolean supported(LevelReader var0, BlockPos var1, int var2) {
      for (BlockPos var4 : BlockPos.betweenClosed(var1.offset(-var2, -1, -var2), var1.offset(var2, -1, var2))) {
         if (!var0.hasChunkAt(var4) || !var0.getBlockState(var4).isFaceSturdy(var0, var4, Direction.UP)) {
            return false;
         }
      }

      return true;
   }

   /** Big (current) blinds only need their 1 x 1 floor; legacy small blinds keep their 3 x 3 rule. */
   private static boolean supported(LevelReader var0, BlockPos var1, BlockState var2) {
      if (start(var2) == 0) {
         return supported(var0, var1, 1);
      } else {
         BlockPos var3 = var1.below();
         return var0.hasChunkAt(var3) && var0.getBlockState(var3).isFaceSturdy(var0, var3, Direction.UP);
      }
   }

   /** The part index at world offset (dx, dy, dz) from the base, or -1 for an interior cell or outside the frame. */
   public static int partAt(boolean var0, Direction var1, int var2, int var3, int var4) {
      int var5;
      int var6;
      switch (var1) {
         case EAST:
            var5 = var4;
            var6 = -var2;
            break;
         case SOUTH:
            var5 = -var2;
            var6 = -var4;
            break;
         case WEST:
            var5 = -var4;
            var6 = var2;
            break;
         default:
            var5 = var2;
            var6 = var4;
      }

      int var7 = var0 ? 2 : 1;
      return var3 >= 0 && var3 <= 2 && Math.abs(var5) <= var7 && Math.abs(var6) <= var7 ? LOCAL[var0 ? 1 : 0][var3][var6 + 2][var5 + 2] : -1;
   }

   private static int[][][][] localIndex() {
      int[][][][] var0 = new int[2][3][5][5];

      for (int[][][] var4 : var0) {
         for (int[][] var6 : var4) {
            for (int[] var8 : var6) {
               Arrays.fill(var8, -1);
            }
         }
      }

      for (int var9 = 0; var9 < PARTS.size(); var9++) {
         BlockPos var10 = PARTS.get(var9);
         var0[var9 < 25 ? 0 : 1][var10.getY()][var10.getZ() + 2][var10.getX() + 2] = var9;
      }

      return var0;
   }

   public static boolean big(BlockState var0) {
      return var0.getValue(PART) >= 25;
   }

   public static boolean open(BlockState var0) {
      return var0.getValue(OPEN);
   }

   public static int firstIndex(boolean var0) {
      return var0 ? 25 : 0;
   }

   public static int endIndex(boolean var0) {
      return var0 ? 82 : 25;
   }

   public static BlockState partState(int var0, Direction var1, boolean var2) {
      return ExpeditionContent.GROUND_BLIND.get().defaultBlockState().setValue(PART, Integer.valueOf(var0)).setValue(FACING, var1).setValue(OPEN, Boolean.valueOf(var2));
   }

   @Override
   protected VoxelShape getShape(BlockState var1, BlockGetter var2, BlockPos var3, CollisionContext var4) {
      return this.shapes[var1.getValue(PART)][var1.getValue(OPEN) ? 1 : 0][var1.getValue(FACING).get2DDataValue()];
   }

   @Override
   protected VoxelShape getOcclusionShape(BlockState var1, BlockGetter var2, BlockPos var3) {
      return Shapes.empty();
   }

   @Override
   protected boolean isPathfindable(BlockState var1, PathComputationType var2) {
      return false;
   }

   private static boolean matches(BlockState var0, Block var1, int var2, Direction var3) {
      return var0.is(var1) && var0.getValue(PART) == var2 && var0.getValue(FACING) == var3;
   }

   @Override
   protected BlockState updateShape(BlockState var1, Direction var2, BlockState var3, LevelAccessor var4, BlockPos var5, BlockPos var6) {
      if (!var4.isClientSide()) {
         // the tick hands itself on to the blind's first standing part, which runs the upkeep loop
         var4.scheduleTick(var5, this, 1);
      }

      return var1;
   }

   @Override
   protected void tick(BlockState var1, ServerLevel var2, BlockPos var3, RandomSource var4) {
      BlockPos var5 = base(var3, var1);
      Direction var6 = var1.getValue(FACING);

      for (int var7 = start(var1); var7 < end(var1); var7++) {
         if (!var2.hasChunkAt(var5.offset(offset(var7, var6)))) {
            var2.scheduleTick(var3, this, 40);
            return;
         }
      }

      BlockPos var8 = null;

      for (int var9 = start(var1); var9 < end(var1); var9++) {
         BlockPos var10 = var5.offset(offset(var9, var6));
         if (matches(var2.getBlockState(var10), this, var9, var6)) {
            var8 = var10;
            break;
         }
      }

      if (var8 != null && !var8.equals(var3)) {
         var2.scheduleTick(var8, this, 20);
      } else if (!supported(var2, var5, var1)) {
         dismantle(var2, var5, var6, var1, true);
      } else {
         refill(var2, var5, var6, var1);
         var2.scheduleTick(var3, this, 20);
      }
   }

   /**
    * Put the frame back into cells that have become empty air since deploying (a hidden bush was trampled, leaves
    * decayed...), so the blind stays whole. Occupied cells are never touched.
    */
   private void refill(ServerLevel var1, BlockPos var2, Direction var3, BlockState var4) {
      boolean var5 = var4.getValue(OPEN);

      for (int var6 = start(var4); var6 < end(var4); var6++) {
         BlockPos var7 = var2.offset(offset(var6, var3));
         if (var1.getBlockState(var7).isAir() && var1.getFluidState(var7).isEmpty()) {
            BlockState var8 = this.defaultBlockState().setValue(PART, Integer.valueOf(var6)).setValue(FACING, var3).setValue(OPEN, Boolean.valueOf(var5));
            if (!blocked(var1, var7, var8) && var1.setBlock(var7, var8, 18)) {
               var1.updateNeighborsAt(var7, this);
            }
         }
      }
   }

   /**
    * Client: when the player breaks any part, take the whole blind down on screen immediately instead of waiting for
    * the server's block updates. This runs inside the client's break prediction, so it is rolled back if the server
    * refuses the break.
    */
   @Override
   public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
      BlockState result = super.playerWillDestroy(level, pos, state, player);
      if (level.isClientSide) {
         try {
            BlockPos base = base(pos, state);
            Direction facing = state.getValue(FACING);
            for (int i = start(state); i < end(state); i++) {
               BlockPos p = base.offset(offset(i, facing));
               if (!p.equals(pos) && matches(level.getBlockState(p), this, i, facing)) {
                  level.setBlock(p, Blocks.AIR.defaultBlockState(), 26);
               }
            }
         } catch (RuntimeException e) {
         }
      }

      return result;
   }

   @Override
   protected void onRemove(BlockState var1, Level var2, BlockPos var3, BlockState var4, boolean var5) {
      super.onRemove(var1, var2, var3, var4, var5);
      if (!var2.isClientSide && !var4.is(this) && !REMOVING.get()) {
         dismantle(var2, base(var3, var1), var1.getValue(FACING), var1, false);
      }
   }

   private static void dismantle(Level var0, BlockPos var1, Direction var2, BlockState var3, boolean var4) {
      if (!REMOVING.get()) {
         REMOVING.set(true);
         boolean var5 = false;

         try {
            for (int var6 = start(var3); var6 < end(var3); var6++) {
               BlockPos var7 = var1.offset(offset(var6, var2));
               if (var0.hasChunkAt(var7) && matches(var0.getBlockState(var7), ExpeditionContent.GROUND_BLIND.get(), var6, var2)) {
                  var5 = true;
                  var0.setBlock(var7, Blocks.AIR.defaultBlockState(), 18);
               }
            }

            if (var4 && var5) {
               popResource(var0, var1, new ItemStack(ExpeditionContent.item("field_blind")));
            }
         } finally {
            REMOVING.set(false);
         }

         for (int var11 = start(var3); var11 < end(var3); var11++) {
            var0.updateNeighborsAt(var1.offset(offset(var11, var2)), Blocks.AIR);
         }
      }
   }

   @Override
   protected InteractionResult useWithoutItem(BlockState var1, Level var2, BlockPos var3, Player var4, BlockHitResult var5) {
      {
         // runs on both sides: the client flips the door at once (inside its block prediction), the server confirms
         BlockPos var6 = base(var3, var1);
         Direction var7 = var1.getValue(FACING);
         boolean var8 = !var1.getValue(OPEN);

         for (int var9 = start(var1); var9 < end(var1); var9++) {
            BlockPos var10 = var6.offset(offset(var9, var7));
            BlockState var11 = var2.getBlockState(var10);
            if (!matches(var11, this, var9, var7)) {
               // a plant or leaf block stands in for this part (its collision is the frame's): check it too
               byte var19 = BlindVolumes.kind(var11);
               if (var19 != BlindVolumes.PLANT && var19 != BlindVolumes.LEAF) {
                  continue;
               }

               var11 = this.defaultBlockState().setValue(PART, Integer.valueOf(var9)).setValue(FACING, var7).setValue(OPEN, var1.getValue(OPEN));
            }

            VoxelShape var12 = Shapes.joinUnoptimized(
               var11.setValue(OPEN, Boolean.valueOf(var8)).getCollisionShape(var2, var10), var11.getCollisionShape(var2, var10), BooleanOp.ONLY_FIRST
            );

            for (AABB var14 : var12.toAabbs()) {
               if (!var2.getEntitiesOfClass(LivingEntity.class, var14.move(var10)).isEmpty()) {
                  if (var4 instanceof ServerPlayer var15) {
                     ExpeditionService.message(var15, "Step clear of the entrance before closing it.");
                  }

                  return var2.isClientSide ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
               }
            }
         }

         for (int var16 = start(var1); var16 < end(var1); var16++) {
            BlockPos var18 = var6.offset(offset(var16, var7));
            BlockState var20 = var2.getBlockState(var18);
            if (matches(var20, this, var16, var7)) {
               var2.setBlock(var18, var20.setValue(OPEN, Boolean.valueOf(var8)), var2.isClientSide ? 10 : 2);
            }
         }

         if (var4 instanceof ServerPlayer var17) {
            ExpeditionService.message(var17, var8 ? "Entrance rolled open" : "Entrance closed · Use the fabric to reopen");
         }

         return InteractionResult.sidedSuccess(var2.isClientSide);
      }
   }

   private static Vec3 wall(double var0, double var2, Direction var4) {
      return TreeStandSeat.point(
         BlockPos.ZERO, var4, -0.64 + 2.28 * var0, var2, -0.64 - 0.14 * Math.sin(Math.PI * var0) * Math.sin(Math.PI * Math.min(var2, 2.04) / 2.04)
      );
   }

   private static List<AABB> construction(boolean var0) {
      ArrayList<AABB> var1 = new ArrayList<>();
      double[] var2 = new double[]{0.0, 0.12, 0.315789, 0.5, 0.684211, 0.88, 1.0};
      double[] var3 = new double[]{0.0, 1.2, 1.8, 1.93, 2.04};

      for (Direction var5 : Plane.HORIZONTAL) {
         for (int var6 = 0; var6 < var2.length - 1; var6++) {
            for (int var7 = 0; var7 < var3.length - 1; var7++) {
               double var8 = (var2[var6] + var2[var6 + 1]) * 0.5;
               double var10 = (var3[var7] + var3[var7 + 1]) * 0.5;
               boolean var12 = var10 > 1.2
                  && var10 < 1.8
                  && (var5 == Direction.NORTH ? var8 > 0.12 && var8 < 0.315789 || var8 > 0.684211 && var8 < 0.88 : var8 > 0.12 && var8 < 0.88);
               if (!var12 && (!var0 || var5 != Direction.NORTH || !(var8 > 0.315789) || !(var8 < 0.684211) || !(var10 < 1.93))) {
                  Vec3 var13 = wall(var2[var6], var3[var7], var5);
                  Vec3 var14 = wall(var2[var6 + 1], var3[var7 + 1], var5);
                  Vec3 var15 = wall(var8, var10, var5);
                  var1.add(
                     new AABB(
                        Math.min(var15.x, Math.min(var13.x, var14.x)) - 0.012,
                        var3[var7],
                        Math.min(var15.z, Math.min(var13.z, var14.z)) - 0.012,
                        Math.max(var15.x, Math.max(var13.x, var14.x)) + 0.012,
                        var3[var7 + 1],
                        Math.max(var15.z, Math.max(var13.z, var14.z)) + 0.012
                     )
                  );
               }
            }
         }
      }

      for (int var16 = 0; var16 < 8; var16++) {
         for (int var17 = 0; var17 < 8; var17++) {
            double var18 = -0.68 + (double)var16 * 0.295;
            double var19 = -0.68 + (double)var17 * 0.295;
            double var20 = Math.max(Math.abs(var18 + 0.1475 - 0.5), Math.abs(var19 + 0.1475 - 0.5)) / 1.18;
            double var21 = 2.04 + (1.0 - var20) * 0.38;
            var1.add(new AABB(var18, var21 - 0.022, var19, var18 + 0.295, var21 + 0.022, var19 + 0.295));
         }
      }

      return var1;
   }

   private static VoxelShape rotateShape(VoxelShape var0, Direction var1) {
      if (var1 == Direction.NORTH) {
         return var0;
      } else {
         ArrayList<VoxelShape> var2 = new ArrayList<>();
         var0.forAllBoxes((var2x, var4, var6, var8, var10, var12) -> {
            var2.add(switch (var1) {
               case EAST -> Shapes.box(1.0 - var12, var4, var2x, 1.0 - var6, var10, var8);
               case SOUTH -> Shapes.box(1.0 - var8, var4, 1.0 - var12, 1.0 - var2x, var10, 1.0 - var6);
               default -> Shapes.box(var6, var4, 1.0 - var8, var12, var10, 1.0 - var2x);
            });
         });
         return var2.stream().reduce(Shapes.empty(), Shapes::or).optimize();
      }
   }
}
