package com.formaworks.frontierhunts.range;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * [1.1.7] Range targets for building a shooting range. Each is a placed block whose collision shape is the target itself, so
 * a bullet or arrow (every projectile in the mod fires NeoForge's impact event, see {@link RangeHits}) lands on the steel
 * or foam and the block decides what the hit was worth.
 *
 * <p>Geometry lives here in model pixels for a target facing north (the side a shooter looks at faces -Z); the JSON models
 * in {@code assets/frontierhunts/models/block/range_*} are built from the same boxes by {@code tools/art/range_art.py}.
 */
public abstract class RangeTargetBlock extends HorizontalDirectionalBlock {
   protected RangeTargetBlock(Properties p) {
      super(p);
   }

   // ============================================================================================ shared plumbing

   /** what a hit did: a line for the shooter's action bar, a sound at the target and its pitch, and whether it was steel */
   public record Result(Component line, SoundEvent sound, float volume, float pitch, boolean steel) {
   }

   /**
    * A hit at {@code px} (model pixels, north frame: x 0..16 across, y up, z 0..16 from the face side), or null when the
    * shot struck nothing that scores (a post, a fallen popper).
    */
   public abstract Result hit(ServerLevel level, BlockPos pos, BlockState state, Vec3 px, double metres, boolean arrow);

   @Override
   public BlockState getStateForPlacement(BlockPlaceContext ctx) {
      return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
   }

   @Override
   protected boolean propagatesSkylightDown(BlockState s, BlockGetter g, BlockPos p) {
      return true;
   }

   /** world hit point -> model pixels in the north frame of a block facing {@code f} */
   public static Vec3 local(BlockPos pos, Direction f, Vec3 hit) {
      double x = hit.x - pos.getX() - 0.5, z = hit.z - pos.getZ() - 0.5;
      for (int i = 0, n = steps(f); i < n; i++) {
         double t = x;
         x = z;
         z = -t;
      }
      return new Vec3((x + 0.5) * 16.0, (hit.y - pos.getY()) * 16.0, (z + 0.5) * 16.0);
   }

   /** quarter turns clockwise (seen from above) from the north-facing model to {@code f}; same as the blockstate y */
   static int steps(Direction f) {
      return switch (f) {
         case EAST -> 1;
         case SOUTH -> 2;
         case WEST -> 3;
         default -> 0;
      };
   }

   /** a shape from pixel boxes in the north frame, one per horizontal facing */
   static Map<Direction, VoxelShape> shapes(double[]... boxes) {
      Map<Direction, VoxelShape> m = new EnumMap<>(Direction.class);
      for (Direction f : Direction.Plane.HORIZONTAL) {
         VoxelShape s = Shapes.empty();
         for (double[] b : boxes) {
            double x1 = b[0], z1 = b[2], x2 = b[3], z2 = b[5];
            for (int i = 0, n = steps(f); i < n; i++) {
               double nx1 = 16 - z2, nx2 = 16 - z1;
               z1 = x1;
               z2 = x2;
               x1 = nx1;
               x2 = nx2;
            }
            s = Shapes.or(s, Block.box(clamp(x1), clamp(b[1]), clamp(z1), clamp(x2), Math.min(b[4], 16), clamp(z2)));
         }
         m.put(f, s.optimize());
      }
      return m;
   }

   private static double clamp(double v) {
      return Math.max(0.0, Math.min(16.0, v));
   }

   static boolean in(Vec3 p, double[] b, double tol) {
      return p.x >= b[0] - tol && p.x <= b[3] + tol && p.y >= b[1] - tol && p.y <= b[4] + tol && p.z >= b[2] - tol && p.z <= b[5] + tol;
   }

   static String m(double metres) {
      return Math.round(metres) + " m";
   }

   static Component line(String what, String result, double metres, ChatFormatting colour) {
      return Component.literal(what + " · ").withStyle(ChatFormatting.GRAY)
         .append(Component.literal(result).withStyle(colour, ChatFormatting.BOLD))
         .append(Component.literal(" · " + m(metres)).withStyle(ChatFormatting.GRAY));
   }

   static double[] b(double x1, double y1, double z1, double x2, double y2, double z2) {
      return new double[]{x1, y1, z1, x2, y2, z2};
   }

   // ============================================================================================ steel gong

   /** A round steel plate hung on chains from a timber frame. Rings when hit and swings a moment. */
   public static final class Gong extends RangeTargetBlock {
      public static final BooleanProperty SWING = BooleanProperty.create("swing");
            static final Map<Direction, VoxelShape> SHAPE = shapes(b(1, 0, 7, 3, 14, 9), b(13, 0, 7, 15, 14, 9), b(0, 14, 6.5, 16, 16, 9.5),
         b(3, 1, 7.5, 13, 11, 8.5));
      public static final MapCodec<Gong> CODEC = simpleCodec(Gong::new);

      public Gong(Properties p) {
         super(p);
         this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SWING, false));
      }

      @Override
      protected MapCodec<Gong> codec() {
         return CODEC;
      }

      @Override
      protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
         b.add(FACING, SWING);
      }

      @Override
      protected VoxelShape getShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) {
         return SHAPE.get(s.getValue(FACING));
      }

      @Override
      public Result hit(ServerLevel level, BlockPos pos, BlockState state, Vec3 px, double metres, boolean arrow) {
         double dx = px.x - 8.0, dy = px.y - 6.0;
         if (dx * dx + dy * dy > 5.0 * 5.0 || px.z > 9.2) {
            return null;
         }
         level.setBlock(pos, state.setValue(SWING, true), 3);
         level.scheduleTick(pos, this, 7);
         return new Result(line("Steel gong", "DING", metres, ChatFormatting.GOLD), SoundEvents.BELL_BLOCK, 2.0F, 1.75F, true);
      }

      @Override
      protected void tick(BlockState s, ServerLevel level, BlockPos pos, RandomSource r) {
         if (s.getValue(SWING)) {
            level.setBlock(pos, s.setValue(SWING, false), 3);
         }
      }
   }

   // ============================================================================================ popper

   /** A hinged steel popper. A hit knocks it flat; it stands itself back up after three seconds. */
   public static final class Popper extends RangeTargetBlock {
      public static final BooleanProperty DOWN = BooleanProperty.create("down");
      static final double[] BASE = b(3, 0, 4, 13, 1.5, 12), HINGE = b(5, 1.5, 2.5, 11, 2.5, 4.5);
      static final double[] UP = b(4.5, 2, 3, 11.5, 15, 4), FLAT = b(4.5, 1.5, 3, 11.5, 3.5, 16);
      static final Map<Direction, VoxelShape> SHAPE_UP = shapes(BASE, HINGE, UP), SHAPE_DOWN = shapes(BASE, HINGE, FLAT);
      public static final MapCodec<Popper> CODEC = simpleCodec(Popper::new);

      public Popper(Properties p) {
         super(p);
         this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(DOWN, false));
      }

      @Override
      protected MapCodec<Popper> codec() {
         return CODEC;
      }

      @Override
      protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
         b.add(FACING, DOWN);
      }

      @Override
      protected VoxelShape getShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) {
         return (s.getValue(DOWN) ? SHAPE_DOWN : SHAPE_UP).get(s.getValue(FACING));
      }

      @Override
      public Result hit(ServerLevel level, BlockPos pos, BlockState state, Vec3 px, double metres, boolean arrow) {
         if (state.getValue(DOWN) || !in(px, UP, 0.3)) {
            return null;
         }
         level.setBlock(pos, state.setValue(DOWN, true), 3);
         level.scheduleTick(pos, this, 60);
         return new Result(line("Popper", "DOWN", metres, ChatFormatting.GOLD), SoundEvents.ANVIL_LAND, 0.9F, 1.9F, true);
      }

      @Override
      protected void tick(BlockState s, ServerLevel level, BlockPos pos, RandomSource r) {
         if (s.getValue(DOWN)) {
            level.setBlock(pos, s.setValue(DOWN, false), 3);
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.7F, 1.3F);
         }
      }
   }

   // ============================================================================================ steel deer silhouette

   /** A broadside whitetail cut from steel with the heart-lung zone painted on. Practice where to hold. */
   public static final class Silhouette extends RangeTargetBlock {
      static final double[] VITALS = b(9.5, 6.75, 7.5, 11.75, 9.25, 8.5);
      static final double[][] BODY = {b(3.5, 2, 7.6, 4.5, 6.5, 8.4), b(5.25, 2, 7.6, 6.25, 6.5, 8.4), b(10, 2, 7.6, 11, 6.5, 8.4),
         b(11.5, 2, 7.6, 12.5, 6.5, 8.4), b(3, 6, 7.6, 12.5, 10, 8.4), b(11.25, 9, 7.6, 13.5, 12.5, 8.4), b(12, 11.5, 7.6, 15.5, 13.5, 8.4),
         b(12.5, 13.5, 7.65, 13, 16, 8.35)};
      static final Map<Direction, VoxelShape> SHAPE;
      public static final MapCodec<Silhouette> CODEC = simpleCodec(Silhouette::new);

      static {
         List<double[]> all = new ArrayList<>(List.of(BODY));
         all.add(b(2, 0, 6.5, 14, 1, 9.5));
         all.add(VITALS);
         SHAPE = shapes(all.toArray(new double[0][]));
      }

      public Silhouette(Properties p) {
         super(p);
         this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
      }

      @Override
      protected MapCodec<Silhouette> codec() {
         return CODEC;
      }

      @Override
      protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
         b.add(FACING);
      }

      @Override
      protected VoxelShape getShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) {
         return SHAPE.get(s.getValue(FACING));
      }

      @Override
      public Result hit(ServerLevel level, BlockPos pos, BlockState state, Vec3 px, double metres, boolean arrow) {
         if (in(px, VITALS, 0.15)) {
            return new Result(line("Steel deer", "VITALS", metres, ChatFormatting.GREEN), SoundEvents.BELL_BLOCK, 2.0F, 1.55F, true);
         }
         for (double[] bx : BODY) {
            if (in(px, bx, 0.15)) {
               return new Result(line("Steel deer", "hit, not vitals", metres, ChatFormatting.YELLOW), SoundEvents.ANVIL_LAND, 0.8F, 1.7F, true);
            }
         }
         return null;
      }
   }

   // ============================================================================================ spinner

   /** A paddle on an axle: hit the big plate and it spins over the top and comes back round. */
   public static final class Spinner extends RangeTargetBlock {
      public static final IntegerProperty SPIN = IntegerProperty.create("spin", 0, 3);
      static final double[] PADDLE = b(5, 1.5, 7.5, 11, 6.5, 8.5);
      static final Map<Direction, VoxelShape> SHAPE = shapes(b(1, 0, 5, 15, 1, 11), b(1, 1, 7.25, 2.5, 11, 8.75), b(13.5, 1, 7.25, 15, 11, 8.75),
         b(2.5, 9.5, 7.6, 13.5, 10.5, 8.4), PADDLE);
      public static final MapCodec<Spinner> CODEC = simpleCodec(Spinner::new);

      public Spinner(Properties p) {
         super(p);
         this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SPIN, 0));
      }

      @Override
      protected MapCodec<Spinner> codec() {
         return CODEC;
      }

      @Override
      protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
         b.add(FACING, SPIN);
      }

      @Override
      protected VoxelShape getShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) {
         return SHAPE.get(s.getValue(FACING));
      }

      @Override
      public Result hit(ServerLevel level, BlockPos pos, BlockState state, Vec3 px, double metres, boolean arrow) {
         if (state.getValue(SPIN) != 0 || !in(px, PADDLE, 0.3)) {
            return null;
         }
         level.setBlock(pos, state.setValue(SPIN, 1), 3);
         level.scheduleTick(pos, this, 2);
         return new Result(line("Spinner", "SPIN", metres, ChatFormatting.GOLD), SoundEvents.BELL_BLOCK, 1.6F, 2.0F, true);
      }

      @Override
      protected void tick(BlockState s, ServerLevel level, BlockPos pos, RandomSource r) {
         int spin = s.getValue(SPIN);
         if (spin == 0) {
            return;
         }
         int next = (spin + 1) % 4;
         level.setBlock(pos, s.setValue(SPIN, next), 3);
         if (next != 0) {
            level.scheduleTick(pos, this, 2);
         } else {
            level.playSound(null, pos, SoundEvents.CHAIN_STEP, SoundSource.BLOCKS, 0.6F, 1.4F);
         }
      }
   }

   // ============================================================================================ 3D foam deer

   public enum Half implements StringRepresentable {
      FRONT, BACK;

      @Override
      public String getSerializedName() {
         return this == FRONT ? "front" : "back";
      }
   }

   /**
    * A life-size foam whitetail for archery and rifle practice, two blocks long. Scored like a 3D archery course: the inner
    * 12 ring, the 10 ring, the 8 (heart-lung) ring, and 5 anywhere else on the foam. {@code FACING} is where the head
    * points; it is set to stand broadside to whoever places it.
    */
   public static final class FoamDeer extends RangeTargetBlock {
      public static final EnumProperty<Half> HALF = EnumProperty.create("half", Half.class);
      /** ring centre on the chest side (front half, model z/y) and the ring radii in pixels */
      static final double CZ = 11.0, CY = 10.75, R12 = 0.6, R10 = 1.5, R8 = 2.4;
      static final double[][] FRONT = {b(5, 8, 5, 11, 14, 16), b(5.5, 7, 6, 10.5, 8, 13), b(5.5, 0, 6, 7, 8, 7.5), b(9, 0, 6, 10.5, 8, 7.5),
         b(6, 12, 1.5, 10, 16, 6)};
      static final double[][] BACK = {b(5, 8, 0, 11, 14, 9), b(5.25, 8.5, 9, 10.75, 13.75, 11.5), b(5, 6.5, 6, 11, 10, 11),
         b(5.5, 0, 8, 7, 6.5, 9.5), b(9, 0, 8, 10.5, 6.5, 9.5), b(7.25, 12, 11.5, 8.75, 14, 12.75)};
      static final Map<Direction, VoxelShape> SHAPE_FRONT = shapes(FRONT), SHAPE_BACK = shapes(BACK);
      public static final MapCodec<FoamDeer> CODEC = simpleCodec(FoamDeer::new);

      public FoamDeer(Properties p) {
         super(p);
         this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, Half.FRONT));
      }

      @Override
      protected MapCodec<FoamDeer> codec() {
         return CODEC;
      }

      @Override
      protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
         b.add(FACING, HALF);
      }

      @Override
      protected VoxelShape getShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) {
         return (s.getValue(HALF) == Half.FRONT ? SHAPE_FRONT : SHAPE_BACK).get(s.getValue(FACING));
      }

      static Direction toPartner(BlockState s) {
         return s.getValue(HALF) == Half.FRONT ? s.getValue(FACING).getOpposite() : s.getValue(FACING);
      }

      @Override
      public BlockState getStateForPlacement(BlockPlaceContext ctx) {
         Direction head = ctx.getHorizontalDirection().getClockWise(); // broadside to the placer, head to their right
         BlockPos back = ctx.getClickedPos().relative(head.getOpposite());
         Level level = ctx.getLevel();
         if (!level.getBlockState(back).canBeReplaced(ctx) || !level.getWorldBorder().isWithinBounds(back)) {
            return null;
         }
         return this.defaultBlockState().setValue(FACING, head).setValue(HALF, Half.FRONT);
      }

      @Override
      public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity by, ItemStack stack) {
         super.setPlacedBy(level, pos, state, by, stack);
         if (!level.isClientSide) {
            BlockPos back = pos.relative(state.getValue(FACING).getOpposite());
            level.setBlock(back, state.setValue(HALF, Half.BACK), 3);
            level.blockUpdated(pos, Blocks.AIR);
            state.updateNeighbourShapes(level, pos, 3);
         }
      }

      @Override
      protected BlockState updateShape(BlockState s, Direction dir, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos) {
         if (dir == toPartner(s) && !(other.is(this) && other.getValue(HALF) != s.getValue(HALF))) {
            return Blocks.AIR.defaultBlockState();
         }
         return super.updateShape(s, dir, other, level, pos, otherPos);
      }

      @Override
      protected boolean canSurvive(BlockState s, LevelReader level, BlockPos pos) {
         return true;
      }

      @Override
      public Result hit(ServerLevel level, BlockPos pos, BlockState state, Vec3 px, double metres, boolean arrow) {
         double[][] boxes = state.getValue(HALF) == Half.FRONT ? FRONT : BACK;
         boolean on = false;
         for (double[] bx : boxes) {
            on |= in(px, bx, 0.25);
         }
         if (!on) {
            return null;
         }
         int score = 5;
         boolean side = px.x <= 5.3 || px.x >= 10.7;
         if (state.getValue(HALF) == Half.FRONT && side) {
            double dz = px.z - CZ, dy = px.y - CY, r = Math.sqrt(dz * dz + dy * dy);
            score = r <= R12 ? 12 : r <= R10 ? 10 : r <= R8 ? 8 : 5;
         }
         ChatFormatting c = score >= 10 ? ChatFormatting.GREEN : score == 8 ? ChatFormatting.YELLOW : ChatFormatting.GRAY;
         String what = score == 12 ? "12 · X ring" : score == 10 ? "10" : score == 8 ? "8 · vitals" : "5 · body";
         return new Result(line("3D deer", what, metres, c), SoundEvents.WOOL_HIT, 1.0F, 0.6F, false);
      }
   }

   // ============================================================================================ distance marker

   /** A painted yardage board: right-click to change the distance it shows. */
   public static final class Marker extends RangeTargetBlock {
      public static final int[] DISTANCES = {25, 50, 75, 100, 150, 200, 300, 400, 500};
      public static final IntegerProperty DIST = IntegerProperty.create("dist", 0, DISTANCES.length - 1);
      static final Map<Direction, VoxelShape> SHAPE = shapes(b(7, 0, 7, 9, 8, 9), b(1.5, 7, 7.25, 14.5, 15, 8.75), b(1, 15, 7, 15, 16, 9));
      public static final MapCodec<Marker> CODEC = simpleCodec(Marker::new);

      public Marker(Properties p) {
         super(p);
         this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(DIST, 3));
      }

      @Override
      protected MapCodec<Marker> codec() {
         return CODEC;
      }

      @Override
      protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
         b.add(FACING, DIST);
      }

      @Override
      protected VoxelShape getShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) {
         return SHAPE.get(s.getValue(FACING));
      }

      @Override
      protected InteractionResult useWithoutItem(BlockState s, Level level, BlockPos pos, Player player, BlockHitResult hit) {
         int n = DISTANCES.length;
         int d = (s.getValue(DIST) + (player.isShiftKeyDown() ? n - 1 : 1)) % n;
         if (!level.isClientSide) {
            level.setBlock(pos, s.setValue(DIST, d), 3);
            level.playSound(null, pos, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.6F, 1.4F);
            if (player instanceof ServerPlayer sp) {
               sp.displayClientMessage(Component.literal("Distance marker · " + DISTANCES[d] + " m · sneak to go back").withStyle(ChatFormatting.GOLD), true);
            }
         }
         return InteractionResult.sidedSuccess(level.isClientSide);
      }

      @Override
      public Result hit(ServerLevel level, BlockPos pos, BlockState state, Vec3 px, double metres, boolean arrow) {
         return null;
      }
   }
}
