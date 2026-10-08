package com.formaworks.frontierhunts.expedition;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
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

public final class MountedTreeStand extends HorizontalDirectionalBlock {
   public static final MapCodec<MountedTreeStand> CODEC = simpleCodec(MountedTreeStand::new);
   public static final IntegerProperty PART = IntegerProperty.create("part", 0, 18);
   public static final IntegerProperty HEIGHT = IntegerProperty.create("height", 3, 7);
   public static final IntegerProperty SEATS = IntegerProperty.create("seats", 1, 2);
   public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
   private final VoxelShape[][][][] shapes = new VoxelShape[19][2][2][4];
   private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);

   static boolean removing() {
      return REMOVING.get();
   }

   static void clearLadderBelow(Level var0, BlockPos var1, Direction var2) {
      boolean var3 = REMOVING.get();
      REMOVING.set(true);

      try {
         StandLadder.clearBelow(var0, var1, var2);
      } finally {
         REMOVING.set(var3);
      }
   }

   public MountedTreeStand(Properties var1) {
      super(var1);
      this.registerDefaultState(
         this.stateDefinition
            .any()
            .setValue(FACING, Direction.NORTH)
            .setValue(PART, Integer.valueOf(0))
            .setValue(OPEN, Boolean.valueOf(true))
            .setValue(HEIGHT, Integer.valueOf(4))
            .setValue(SEATS, Integer.valueOf(1))
      );

      for (int var2 = 0; var2 < 19; var2++) {
         for (int var3 = 1; var3 <= 2; var3++) {
            for (int var4 = 0; var4 < 2; var4++) {
               for (Direction var6 : Plane.HORIZONTAL) {
                  this.shapes[var2][var3 - 1][var4][var6.get2DDataValue()] = rotateShape(shape(var2, var3, var4 == 1), var6);
               }
            }
         }
      }
   }

   @Override
   protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
      return CODEC;
   }

   @Override
   protected void createBlockStateDefinition(Builder<Block, BlockState> var1) {
      var1.add(FACING, PART, OPEN, HEIGHT, SEATS);
   }

   @Override
   protected BlockState rotate(BlockState var1, Rotation var2) {
      return var1.setValue(FACING, var2.rotate(var1.getValue(FACING)));
   }

   @Override
   protected BlockState mirror(BlockState var1, Mirror var2) {
      return var1.rotate(var2.getRotation(var1.getValue(FACING)));
   }

   public static int height(ItemStack var0) {
      CompoundTag var1 = ExpeditionWeapon.data(var0);
      return var1.contains("stand_height") ? Math.clamp((long)var1.getInt("stand_height"), 3, 7) : 4;
   }

   public static int seats(ItemStack var0) {
      return Math.clamp((long)ExpeditionWeapon.data(var0).getInt("stand_seats"), 1, 2);
   }

   public static void configure(ItemStack var0, int var1, int var2) {
      CompoundTag var3 = ExpeditionWeapon.data(var0);
      var3.putInt("stand_height", Math.clamp((long)var1, 3, 7));
      var3.putInt("stand_seats", Math.clamp((long)var2, 1, 2));
      ExpeditionWeapon.save(var0, var3);
   }

   public static String configuration(ItemStack var0) {
      return String.format(Locale.ROOT, "%.2f m platform · %d seat%s", (double)height(var0) - 0.36, seats(var0), seats(var0) == 1 ? "" : "s");
   }

   public static void adjust(ServerPlayer var0, ItemStack var1) {
      configure(var1, var0.isShiftKeyDown() ? height(var1) : (height(var1) == 7 ? 3 : height(var1) + 1), var0.isShiftKeyDown() ? 3 - seats(var1) : seats(var1));
      var0.getCooldowns().addCooldown(var1.getItem(), 8);
      ExpeditionService.message(var0, configuration(var1));
   }

   public static int[] parts(int var0, int var1) {
      List<Integer> var2 = new ArrayList<>();

      for (int var3 = 0; var3 < var0 - 1; var3++) {
         var2.add(var3 < 3 ? var3 : var3 + 5);
      }

      var2.add(3);

      for (int var4 = 4; var4 < 8; var4++) {
         var2.add(var4);
      }

      if (var1 == 2) {
         for (int var5 = 11; var5 < 19; var5++) {
            var2.add(var5);
         }
      }

      return var2.stream().mapToInt(Integer::intValue).toArray();
   }

   public static BlockPos offset(int var0, Direction var1) {
      return offset(var0, var1, 4);
   }

   public static BlockPos offset(int var0, Direction var1, int var2) {
      int var3 = 0;
      int var4;
      byte var5;
      if (var0 < 4) {
         var4 = var0 == 3 ? var2 - 1 : var0;
         var5 = 0;
      } else if (var0 < 8) {
         var4 = var2 - 2 + var0 - 4;
         var5 = 1;
      } else if (var0 < 11) {
         var4 = var0 - 5;
         var5 = 0;
      } else {
         var3 = var0 < 15 ? -1 : 1;
         var4 = var2 - 2 + (var0 - 11) % 4;
         var5 = 1;
      }

      return rotateOffset(new BlockPos(var3, var4, var5), var1);
   }

   private static BlockPos rotateOffset(BlockPos var0, Direction var1) {
      return switch (var1) {
         case EAST -> new BlockPos(-var0.getZ(), var0.getY(), var0.getX());
         case SOUTH -> new BlockPos(-var0.getX(), var0.getY(), -var0.getZ());
         case WEST -> new BlockPos(var0.getZ(), var0.getY(), -var0.getX());
         default -> var0;
      };
   }

   public static BlockPos base(BlockPos var0, BlockState var1) {
      return var0.subtract(offset(var1.getValue(PART), var1.getValue(FACING), var1.getValue(HEIGHT)));
   }

   // ------------------------------------------------------------------ placement (auto-attach)

   /** Why a side of a tree cannot take the stand (lang key suffix), in order of how specific it is. */
   private static final String NO_TRUNK = "no_trunk", THIN_POST = "thin_post", TOO_SMALL = "too_small", TOO_THIN = "too_thin",
      NO_GROUND = "no_ground", NO_ROOM = "no_room";

   /** A fitted mount on one side of the trunk. */
   private record Plan(Direction facing, BlockPos column, int ground, int total, String lowered) {
      int height() {
         return Math.min(4, this.total);
      }

      BlockPos base() {
         return new BlockPos(this.column.getX(), this.ground + 1 + this.total - this.height(), this.column.getZ()).relative(this.facing, 2);
      }

      BlockPos ladder() {
         return new BlockPos(this.column.getX(), this.ground + 1, this.column.getZ()).relative(this.facing, 2);
      }
   }

   static void say(ServerPlayer player, String key, String fallback, Object... args) {
      player.displayClientMessage(Component.translatableWithFallback("message.frontierhunts.tree_stand." + key, fallback, args), true);
   }

   private static void refuse(ServerPlayer player, String why) {
      switch (why) {
         case THIN_POST -> say(player, why, "That is far too thin to hold a tree stand - use it on a tree trunk.");
         case TOO_SMALL -> say(player, why, "This tree is too small for a tree stand - it needs at least 4 straight trunk logs above the ladder's footing.");
         case TOO_THIN -> say(player, why, "This trunk is too thin at stand height to carry a tree stand - pick a thicker tree.");
         case NO_GROUND -> say(player, why, "The ladder needs firm, open ground beside the trunk.");
         case NO_ROOM -> say(player, why, "No room here - clear space for the ladder, platform and rail beside the trunk.");
         default -> say(player, NO_TRUNK, "Use the tree stand on or right next to a standing tree trunk.");
      }
   }

   /**
    * Uses the stand on or near a tree: finds the trunk (the upright log column, or the column group of
    * a 2x2 trunk), picks the side facing the player (or the clicked bark face), and fits the stand
    * flush against that side at the selected height with the ladder down to firm ground - lower when
    * the trunk is too short or too thin further up. Other sides are tried when that side has no room.
    */
   public static boolean place(UseOnContext context) {
      if (!(context.getPlayer() instanceof ServerPlayer player)) {
         return false;
      }
      ServerLevel level = player.serverLevel();
      BlockPos clicked = context.getClickedPos();
      Vec3 hit = context.getClickLocation();
      BlockPos log = StandTrunk.find(level, clicked, hit);
      if (log == null) {
         boolean post = StandTrunk.thinPost(level, clicked) || StandTrunk.thinPost(level, clicked.relative(context.getClickedFace()));
         refuse(player, post ? THIN_POST : NO_TRUNK);
         return false;
      }
      int footY = StandTrunk.foot(level, log);
      BlockPos square = StandTrunk.square(level, log, Math.min(log.getY(), footY + 1));
      if (square == null) {
         square = StandTrunk.square(level, log, footY);
      }
      double cx = square != null ? square.getX() + 1.0 : log.getX() + 0.5;
      double cz = square != null ? square.getZ() + 1.0 : log.getZ() + 0.5;
      // the side facing the player; a click on an outer bark face picks that face
      Direction preferred = null;
      Direction face = context.getClickedFace();
      if (clicked.equals(log) && face.getAxis().isHorizontal() && !StandTrunk.upright(level, clicked.relative(face))) {
         preferred = face;
      }
      double px = player.getX() - cx, pz = player.getZ() - cz;
      if (preferred == null) {
         preferred = Math.abs(px) < 1.0E-4 && Math.abs(pz) < 1.0E-4 ? player.getDirection().getOpposite() : Direction.getNearest(px, 0.0, pz);
         if (!preferred.getAxis().isHorizontal()) {
            preferred = player.getDirection().getOpposite();
         }
      }
      List<Direction> sides = new ArrayList<>();
      sides.add(preferred);
      List<Direction> rest = new ArrayList<>();
      for (Direction d : Plane.HORIZONTAL) {
         if (d != preferred) {
            rest.add(d);
         }
      }
      rest.sort((a, b) -> Double.compare(b.getStepX() * px + b.getStepZ() * pz, a.getStepX() * px + a.getStepZ() * pz));
      sides.addAll(rest);
      ItemStack stack = context.getItemInHand();
      int requested = height(stack);
      boolean clickedTrunk = clicked.equals(log);
      String first = null;
      for (Direction side : sides) {
         Object result = plan(level, log, square, side, player.position(), requested, clickedTrunk ? clicked.getY() : Integer.MIN_VALUE);
         if (result instanceof Plan plan) {
            Map<BlockPos, BlockState> blocks = blocks(plan.base(), plan.facing(), stack, plan.height(), plan.ladder());
            if (clear(level, player, stack, plan.facing(), blocks)) {
               if (assemble(player, plan.base(), plan.facing(), stack, plan.height(), plan.ladder())) {
                  if (plan.lowered() != null) {
                     String metres = String.format(Locale.ROOT, "%.2f", plan.total() - 0.36);
                     if (plan.lowered().equals(TOO_THIN)) {
                        say(player, "lowered_thin", "The trunk is too thin higher up - stand mounted lower, at %s m.", metres);
                     } else {
                        say(player, "lowered_short", "The trunk is too short for the selected height - stand mounted at %s m.", metres);
                     }
                  }
                  return true;
               }
               return false;
            }
            result = NO_ROOM;
         }
         if (first == null) {
            first = (String)result;
         }
      }
      refuse(player, first);
      return false;
   }

   /** The fit on one side of the trunk: a Plan, or the refusal reason. */
   private static Object plan(ServerLevel level, BlockPos log, BlockPos square, Direction facing, Vec3 player, int requested, int clickedY) {
      BlockPos column = log;
      if (square != null) {
         // of the 2x2 group: the column on this face nearest the player
         double best = Double.MAX_VALUE;
         for (int i = 0; i <= 1; i++) {
            for (int k = 0; k <= 1; k++) {
               BlockPos c = new BlockPos(square.getX() + i, log.getY(), square.getZ() + k);
               BlockPos out = c.relative(facing);
               if (out.getX() >= square.getX() && out.getX() <= square.getX() + 1 && out.getZ() >= square.getZ() && out.getZ() <= square.getZ() + 1) {
                  continue;
               }
               double dx = c.getX() + 0.5 - player.x, dz = c.getZ() + 0.5 - player.z;
               if (dx * dx + dz * dz < best && StandTrunk.upright(level, c)) {
                  best = dx * dx + dz * dz;
                  column = c;
               }
            }
         }
      }
      int foot = StandTrunk.foot(level, column);
      int top = StandTrunk.top(level, column);
      if (top - foot + 1 < StandTrunk.MIN_LOGS) {
         return TOO_SMALL;
      }
      BlockPos ladder = column.relative(facing, 2);
      int ground = StandTrunk.ground(level, ladder.getX(), ladder.getZ(), foot);
      if (ground == Integer.MIN_VALUE) {
         return NO_GROUND;
      }
      // trunk logs are needed from the bracket (ground + total - 1) up to the rail (ground + total + 1)
      int min = Math.max(3, foot - ground + 1);
      int max = Math.min(24, top - ground - 1);
      if (max < min) {
         return TOO_SMALL;
      }
      int wanted = clickedY == Integer.MIN_VALUE ? requested : Math.max(requested, clickedY - ground);
      int total = Math.max(min, Math.min(max, wanted));
      String lowered = wanted > max ? TOO_SMALL : null;
      StandTrunk.Stem stem = StandTrunk.stem(level, column);
      if (stem != null) {
         int fitted = total;
         // the platform's strap level must meet a trunk the realistic preset draws thick enough
         while (fitted >= min && stem.radius(ground + fitted + 0.64) < StandTrunk.MIN_RADIUS) {
            fitted--;
         }
         if (fitted < min) {
            return TOO_THIN;
         }
         if (fitted < total) {
            lowered = TOO_THIN;
            total = fitted;
         }
      }
      return new Plan(facing, new BlockPos(column.getX(), log.getY(), column.getZ()), ground, total, lowered);
   }

   public static boolean assemble(ServerPlayer var0, BlockPos var1, Direction var2, ItemStack var3) {
      return assemble(var0, var1, var2, var3, height(var3), var1);
   }

   private static Map<BlockPos, BlockState> blocks(BlockPos base, Direction facing, ItemStack stack, int height, BlockPos ladder) {
      MountedTreeStand block = ExpeditionContent.TREE_STAND.get();
      int seats = seats(stack);
      Map<BlockPos, BlockState> out = new LinkedHashMap<>();
      for (BlockPos p = ladder; p.getY() < base.getY(); p = p.above()) {
         out.put(p, ExpeditionContent.STAND_LADDER.get().defaultBlockState().setValue(FACING, facing));
      }
      for (int part : parts(height, seats)) {
         out.put(
            base.offset(offset(part, facing, height)),
            block.defaultBlockState()
               .setValue(PART, Integer.valueOf(part))
               .setValue(FACING, facing)
               .setValue(HEIGHT, Integer.valueOf(height))
               .setValue(SEATS, Integer.valueOf(seats))
         );
      }
      return out;
   }

   private static boolean clear(ServerLevel level, ServerPlayer player, ItemStack stack, Direction facing, Map<BlockPos, BlockState> blocks) {
      for (BlockPos p : blocks.keySet()) {
         if (!level.hasChunkAt(p)
            || !level.isInWorldBounds(p)
            || !level.getWorldBorder().isWithinBounds(p)
            || !level.mayInteract(player, p)
            || !player.mayUseItemAt(p, facing, stack)
            || !level.getBlockState(p).canBeReplaced()
            || !level.getFluidState(p).isEmpty()) {
            return false;
         }
      }
      return true;
   }

   private static boolean assemble(ServerPlayer var0, BlockPos var1, Direction var2, ItemStack var3, int var4, BlockPos var5) {
      if (!var2.getAxis().isHorizontal()) {
         return false;
      }
      ServerLevel var6 = var0.serverLevel();
      MountedTreeStand var7 = ExpeditionContent.TREE_STAND.get();
      if (!trunkSupported(var6, var1, var2, var4) || !var6.getBlockState(var5.below()).isFaceSturdy(var6, var5.below(), Direction.UP)) {
         say(var0, "unsupported", "The ladder needs firm ground and the selected height needs a tall supporting trunk.");
         return false;
      }
      Map<BlockPos, BlockState> var11 = blocks(var1, var2, var3, var4, var5);
      if (!clear(var6, var0, var3, var2, var11)) {
         refuse(var0, NO_ROOM);
         return false;
      }
      Map<BlockPos, BlockState> var10 = new LinkedHashMap<>();
      for (BlockPos p : var11.keySet()) {
         var10.put(p, var6.getBlockState(p));
      }
      for (Entry<BlockPos, BlockState> var24 : var11.entrySet()) {
         if (!var6.setBlock(var24.getKey(), var24.getValue(), 18)) {
            REMOVING.set(true);
            try {
               var10.forEach((p, s) -> var6.setBlock(p, s, 18));
            } finally {
               REMOVING.set(false);
            }
            return false;
         }
      }
      for (BlockPos var25 : var10.keySet()) {
         var6.updateNeighborsAt(var25, var7);
      }
      return true;
   }

   private static boolean supported(LevelReader var0, BlockPos var1, Direction var2, int var3) {
      BlockPos var4 = var1.below();

      while (var4.getY() >= var0.getMinBuildHeight() && var0.hasChunkAt(var4) && StandLadder.matches(var0.getBlockState(var4), var2)) {
         var4 = var4.below();
      }

      return var0.hasChunkAt(var4) && var0.getBlockState(var4).isFaceSturdy(var0, var4, Direction.UP) ? trunkSupported(var0, var1, var2, var3) : false;
   }

   private static boolean trunkSupported(LevelReader var0, BlockPos var1, Direction var2, int var3) {
      BlockPos var4 = var1.relative(var2.getOpposite(), 2);

      for (int var5 = var3 - 2; var5 <= var3; var5++) {
         if (!var0.hasChunkAt(var4.above(var5)) || !var0.getBlockState(var4.above(var5)).is(BlockTags.LOGS)) {
            return false;
         }
      }

      return true;
   }

   @Override
   protected VoxelShape getShape(BlockState var1, BlockGetter var2, BlockPos var3, CollisionContext var4) {
      return this.shapes[var1.getValue(PART)][var1.getValue(SEATS) - 1][var1.getValue(OPEN) ? 1 : 0][var1.getValue(FACING).get2DDataValue()];
   }

   @Override
   protected VoxelShape getOcclusionShape(BlockState var1, BlockGetter var2, BlockPos var3) {
      return Shapes.empty();
   }

   @Override
   protected boolean isPathfindable(BlockState var1, PathComputationType var2) {
      return false;
   }

   @Override
   public boolean isLadder(BlockState var1, LevelReader var2, BlockPos var3, LivingEntity var4) {
      int var5 = var1.getValue(PART);
      return var5 < 4 || var5 >= 8 && var5 < 11;
   }

   @Override
   protected BlockState updateShape(BlockState var1, Direction var2, BlockState var3, LevelAccessor var4, BlockPos var5, BlockPos var6) {
      if (!var4.isClientSide()) {
         var4.scheduleTick(var5, this, 1);
      }

      return var1;
   }

   private static boolean matches(BlockState var0, Block var1, int var2, Direction var3, int var4, int var5) {
      return var0.is(var1) && var0.getValue(PART) == var2 && var0.getValue(FACING) == var3 && var0.getValue(HEIGHT) == var4 && var0.getValue(SEATS) == var5;
   }

   @Override
   protected void tick(BlockState var1, ServerLevel var2, BlockPos var3, RandomSource var4) {
      BlockPos var5 = base(var3, var1);
      Direction var6 = var1.getValue(FACING);
      int var7 = var1.getValue(HEIGHT);
      int var8 = var1.getValue(SEATS);
      int[] var9 = parts(var7, var8);

      for (int var13 : var9) {
         if (!var2.hasChunkAt(var5.offset(offset(var13, var6, var7)))) {
            return;
         }
      }

      if (var2.hasChunkAt(var5.relative(var6.getOpposite(), 2))) {
         boolean var15 = supported(var2, var5, var6, var7);

         for (int var14 : var9) {
            var15 &= matches(var2.getBlockState(var5.offset(offset(var14, var6, var7))), this, var14, var6, var7, var8);
         }

         if (!var15) {
            dismantle(var2, var5, var6, var7, var8, true);
         }
      }
   }

   @Override
   protected void onRemove(BlockState var1, Level var2, BlockPos var3, BlockState var4, boolean var5) {
      super.onRemove(var1, var2, var3, var4, var5);
      if (!var2.isClientSide && !var4.is(this) && !REMOVING.get()) {
         dismantle(var2, base(var3, var1), var1.getValue(FACING), var1.getValue(HEIGHT), var1.getValue(SEATS), false);
      }
   }

   @Override
   protected List<ItemStack> getDrops(BlockState var1, net.minecraft.world.level.storage.loot.LootParams.Builder var2) {
      List<ItemStack> var3 = super.getDrops(var1, var2);

      for (ItemStack var5 : var3) {
         if (var5.is(ExpeditionContent.item("tree_stand"))) {
            configure(var5, var1.getValue(HEIGHT), var1.getValue(SEATS));
         }
      }

      return var3;
   }

   static void dismantle(Level var0, BlockPos var1, Direction var2, int var3, int var4, boolean var5) {
      if (!REMOVING.get()) {
         REMOVING.set(true);
         boolean var6 = false;
         int[] var7 = parts(var3, var4);

         try {
            StandLadder.clearBelow(var0, var1, var2);

            for (int var11 : var7) {
               BlockPos var12 = var1.offset(offset(var11, var2, var3));
               if (var0.hasChunkAt(var12)) {
                  BlockState var13 = var0.getBlockState(var12);
                  if (matches(var13, ExpeditionContent.TREE_STAND.get(), var11, var2, var3, var4)) {
                     var6 = true;
                     var0.setBlock(var12, Blocks.AIR.defaultBlockState(), 18);
                  }
               }
            }

            if (var5 && var6) {
               ItemStack var17 = new ItemStack(ExpeditionContent.item("tree_stand"));
               configure(var17, var3, var4);
               popResource(var0, var1, var17);
            }
         } finally {
            REMOVING.set(false);
         }

         for (int var21 : var7) {
            var0.updateNeighborsAt(var1.offset(offset(var21, var2, var3)), Blocks.AIR);
         }
      }
   }

   @Override
   protected InteractionResult useWithoutItem(BlockState var1, Level var2, BlockPos var3, Player var4, BlockHitResult var5) {
      if (var2.isClientSide) {
         return InteractionResult.SUCCESS;
      } else {
         BlockPos var6 = base(var3, var1);
         boolean var7 = !var1.getValue(OPEN);
         Direction var8 = var1.getValue(FACING);
         int var9 = var1.getValue(HEIGHT);
         int var10 = var1.getValue(SEATS);
         int[] var11 = parts(var9, var10);
         Vec3 var12 = TreeStandSeat.local(var6, var8, var5.getLocation());
         if (var4 instanceof ServerPlayer var13 && !var4.isShiftKeyDown() && var12.y >= (double)var9 + 0.02 && var12.y < (double)var9 + 0.17 && var12.z > 1.45) {
            TreeStandSeat.sit(var13, var6, var8, var9, var10, var10 == 1 ? 0 : (var12.x < 0.5 ? 0 : 1));
            return InteractionResult.CONSUME;
         }

         for (int var16 : var11) {
            int var17 = var16 < 8 ? var16 : (var16 < 11 ? 1 : 4 + (var16 - 11) % 4);
            if (var17 >= 6) {
               BlockPos var18 = var6.offset(offset(var16, var8, var9));
               BlockState var19 = var2.getBlockState(var18);
               if (!matches(var19, this, var16, var8, var9, var10)) {
                  return InteractionResult.CONSUME;
               }

               VoxelShape var20 = Shapes.joinUnoptimized(
                  var19.setValue(OPEN, Boolean.valueOf(var7)).getCollisionShape(var2, var18), var19.getCollisionShape(var2, var18), BooleanOp.ONLY_FIRST
               );

               for (AABB var22 : var20.toAabbs()) {
                  if (!var2.getEntitiesOfClass(LivingEntity.class, var22.move(var18)).isEmpty()) {
                     if (var4 instanceof ServerPlayer var23) {
                        ExpeditionService.message(var23, "Step clear of the shooting rail before moving it.");
                     }

                     return InteractionResult.CONSUME;
                  }
               }
            }
         }

         for (int var29 : var11) {
            BlockPos var30 = var6.offset(offset(var29, var8, var9));
            BlockState var31 = var2.getBlockState(var30);
            if (matches(var31, this, var29, var8, var9, var10)) {
               var2.setBlock(var30, var31.setValue(OPEN, Boolean.valueOf(var7)), 2);
            }
         }

         var2.playSound(null, var3, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.3F, var7 ? 1.1F : 0.9F);
         if (var4 instanceof ServerPlayer var26) {
            ExpeditionService.message(var26, var7 ? "Shooting rail raised · climb onto the platform" : "Shooting rail lowered");
         }

         return InteractionResult.CONSUME;
      }
   }

   private static VoxelShape shape(int var0, int var1, boolean var2) {
      if (var0 >= 4 && (var0 < 8 || var0 >= 11)) {
         int var3 = var0 < 8 ? var0 : 4 + (var0 - 11) % 4;
         double var4 = var0 < 11 ? 0.0 : (var0 < 15 ? -1.0 : 1.0);
         double var6 = var1 == 2 ? 6.4 : 0.0;

         VoxelShape var8 = switch (var3) {
            case 4 -> Shapes.or(Block.box(1.5 - var6, 10.0, 11.5, 2.4 - var6, 16.0, 16.0), Block.box(13.6 + var6, 10.0, 11.5, 14.5 + var6, 16.0, 16.0));
            case 5 -> Shapes.or(
            Block.box(0.8 - var6, 9.5, 1.8, 15.2 + var6, 10.3, 16.0),
            Block.box(1.0 - var6, 0.0, 12.0, 2.0 - var6, 9.5, 16.0),
            Block.box(14.0 + var6, 0.0, 12.0, 15.0 + var6, 9.5, 16.0)
         );
            case 6 -> {
               VoxelShape var9 = Shapes.or(Block.box(0.8 - var6, 0.0, 2.0, 1.5 - var6, 9.3, 15.4), Block.box(14.5 + var6, 0.0, 2.0, 15.2 + var6, 9.3, 15.4));

               for (double var13 : var1 == 2 ? new double[]{-6.4, 6.4} : new double[]{0.0}) {
                  var9 = Shapes.or(var9, Block.box(2.0 + var13, 0.5, 7.5, 14.0 + var13, 2.0, 15.5), Block.box(2.0 + var13, 2.0, 14.7, 14.0 + var13, 10.5, 16.0));
               }

               yield var2 ? var9 : Shapes.or(var9, Block.box(0.8 - var6, 8.6, 1.3, 15.2 + var6, 9.4, 2.2));
            }
            default -> var2 ? Block.box(0.8 - var6, 0.0, 14.5, 15.2 + var6, 5.0, 15.5) : Shapes.empty();
         };
         if (var1 == 1) {
            return var0 >= 11 ? Shapes.empty() : var8;
         } else {
            return Shapes.joinUnoptimized(var8, Shapes.box(var4, -1.0, 0.0, var4 + 1.0, 2.0, 2.0), BooleanOp.AND).move(-var4, 0.0, 0.0).optimize();
         }
      } else {
         return Block.box(1.6, 0.0, 10.7, 14.4, var0 == 3 ? 11.9 : 16.0, 12.2);
      }
   }

   private static VoxelShape rotateShape(VoxelShape var0, Direction var1) {
      if (var1 == Direction.NORTH) {
         return var0;
      } else {
         List<VoxelShape> var2 = new ArrayList<>();
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
