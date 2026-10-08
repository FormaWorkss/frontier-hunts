package com.formaworks.frontierhunts.hunting;

import com.formaworks.frontierhunts.expedition.ExpeditionContent;
import com.formaworks.frontierhunts.expedition.ExpeditionService;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Deer sign blocks: a buck's RUB (bark stripped off a small trunk), a SCRAPE (pawed ground under a licking branch at a
 * tree) and a BED. The block holds the sign's state (who made it, when, how worn) and ages it; rubs and scrapes are drawn
 * by the client {@code sign.client.DeerSignRenderer} (a decal on the real trunk surface / ground, visible to everyone,
 * no tool needed), beds keep their block model.
 *
 * <p>[sign] reworked: natural-tree site search (small vertical trunks with natural leaves, never stripped logs or
 * timber beside other logs/planks), the trunk/tree a sign belongs to is remembered and synced, the mock scrape kit is
 * gone, and the block entity syncs its visual state to clients.
 */
public final class DeerSign extends Block implements EntityBlock {
   public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 3);
   public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
   private static final int CHECK = 600;
   public final DeerSign.Kind kind;

   public DeerSign(DeerSign.Kind kind, Properties props) {
      super(props);
      this.kind = kind;
      this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(AGE, 0));
   }

   public static DeerSign create(String id, Properties props) {
      for (DeerSign.Kind k : DeerSign.Kind.values()) {
         if (k.id.equals(id)) {
            return new DeerSign(k, props);
         }
      }

      return new DeerSign(DeerSign.Kind.BED, props);
   }

   @Override
   protected MapCodec<DeerSign> codec() {
      return RecordCodecBuilder.mapCodec(i -> i.group(Codec.STRING.fieldOf("sign").forGetter(s -> s.kind.id), propertiesCodec()).apply(i, DeerSign::create));
   }

   @Override
   protected void createBlockStateDefinition(Builder<Block, BlockState> b) {
      b.add(new Property[]{FACING, AGE});
   }

   @Override
   protected BlockState rotate(BlockState s, Rotation r) {
      return s.setValue(FACING, r.rotate(s.getValue(FACING)));
   }

   @Override
   protected BlockState mirror(BlockState s, Mirror m) {
      return s.rotate(m.getRotation(s.getValue(FACING)));
   }

   @Override
   public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return new DeerSign.Mark(pos, state);
   }

   @Override
   protected RenderShape getRenderShape(BlockState state) {
      // [sign] rubs and scrapes are drawn by the block entity renderer (trunk-following / ground-following decal)
      return this.kind == DeerSign.Kind.BED ? RenderShape.MODEL : RenderShape.INVISIBLE;
   }

   @Override
   protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
      return true;
   }

   @Override
   protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
      if (this.kind != DeerSign.Kind.RUB) {
         return box(1.0, 0.0, 1.0, 15.0, 0.5, 15.0);
      } else {
         // the trunk side of this cell, knee to chest height (a round trunk's surface lies behind it)
         return switch (state.getValue(FACING)) {
            case NORTH -> box(3.0, 2.0, 13.0, 13.0, 16.0, 16.0);
            case SOUTH -> box(3.0, 2.0, 0.0, 13.0, 16.0, 3.0);
            case WEST -> box(13.0, 2.0, 3.0, 16.0, 16.0, 13.0);
            default -> box(0.0, 2.0, 3.0, 3.0, 16.0, 13.0);
         };
      }
   }

   @Override
   protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
      return Shapes.empty();
   }

   @Override
   protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
      if (this.kind == DeerSign.Kind.RUB) {
         BlockPos log = pos.relative(state.getValue(FACING).getOpposite());
         return level.getBlockState(log).isFaceSturdy(level, log, state.getValue(FACING));
      } else {
         BlockPos below = pos.below();
         return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
      }
   }

   @Override
   protected BlockState updateShape(BlockState state, Direction dir, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos) {
      return this.canSurvive(state, level, pos) ? state : Blocks.AIR.defaultBlockState();
   }

   @Override
   protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
      if (!level.isClientSide && !old.is(this)) {
         level.scheduleTick(pos, this, CHECK);
      }
   }

   @Override
   protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
      if (level.getBlockEntity(pos) instanceof DeerSign.Mark mark) {
         boolean wet = false;
         if (level.isRaining() && level.canSeeSky(pos)) {
            mark.wear = mark.wear + CHECK * (this.kind == DeerSign.Kind.BED ? 3 : 2);
            wet = true;
         }

         long age = mark.age(level.getGameTime());
         if (age > (long)this.kind.lifetime || mark.cap < 0.08F && mark.workTo <= level.getGameTime()) { // [deersign] or a sign never really begun
            level.removeBlock(pos, false);
         } else {
            int stage = Math.clamp((long)((int)(age * 4L / (long)Math.max(1, this.kind.lifetime))), 0, 3);
            mark.setChanged();
            if (state.getValue(AGE) != stage) {
               level.setBlock(pos, state.setValue(AGE, stage), 2); // also re-sends the block entity
            } else if (wet) {
               level.sendBlockUpdated(pos, state, state, 2); // [sign] rain wear: clients age the decal too
            }

            level.scheduleTick(pos, this, CHECK);
         }
      } else {
         level.removeBlock(pos, false);
      }
   }

   @Override
   protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
      if (level.getBlockEntity(pos) instanceof DeerSign.Mark mark && player instanceof ServerPlayer sp) {
         ExpeditionService.message(sp, mark.report(level.getGameTime(), this.kind));
         ExpeditionService.record(sp, "clue", mark.species, 1, 0.0);
      }

      return InteractionResult.sidedSuccess(level.isClientSide);
   }

   @Override
   protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
      if (this.kind == DeerSign.Kind.SCRAPE && stack.is(ExpeditionContent.item("bait"))) {
         if (!level.isClientSide && level.getBlockEntity(pos) instanceof DeerSign.Mark mark) {
            if (mark.age(level.getGameTime()) < 1200L) {
               return ItemInteractionResult.CONSUME;
            }

            mark.freshened = level.getGameTime();
            mark.wear = 0;
            mark.setChanged();
            level.setBlock(pos, state.setValue(AGE, 0), 2);
            level.sendBlockUpdated(pos, state, state.setValue(AGE, 0), 2);
            level.scheduleTick(pos, this, CHECK);
            if (!player.hasInfiniteMaterials()) {
               stack.shrink(1);
            }

            if (player instanceof ServerPlayer sp) {
               ExpeditionService.message(sp, "Scrape freshened");
            }
         }

         return ItemInteractionResult.sidedSuccess(level.isClientSide);
      } else {
         return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
      }
   }

   private static DeerSign block(DeerSign.Kind kind) {
      return (DeerSign)ExpeditionContent.SIGNS.get(kind.id).get();
   }

   // ------------------------------------------------------------------------------------------------ site rules

   /** Natural ground a tree grows from / a deer paws (never a floor someone built). */
   public static boolean naturalGround(BlockState s) {
      return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(Blocks.MOSS_BLOCK) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.GRAVEL)
         || s.is(Blocks.CLAY) || s.is(Blocks.PACKED_MUD);
   }

   private static boolean groundSpot(Level level, BlockPos pos) {
      BlockState here = level.getBlockState(pos);
      if ((here.isAir() || here.canBeReplaced()) && !(here.getBlock() instanceof DeerSign) && !here.hasBlockEntity()) {
         if (!level.getFluidState(pos).isEmpty()) {
            return false;
         } else {
            BlockPos below = pos.below();
            BlockState ground = level.getBlockState(below);
            return ground.isFaceSturdy(level, below, Direction.UP)
               && (naturalGround(ground) || ground.is(BlockTags.SNOW) || ground.is(BlockTags.TERRACOTTA) || ground.is(BlockTags.BASE_STONE_OVERWORLD));
         }
      } else {
         return false;
      }
   }

   /** An open cell a rub or scrape may take (air, a plant, a thin snow layer, forest litter). */
   private static boolean open(Level level, BlockPos pos) {
      BlockState s = level.getBlockState(pos);
      return (s.isAir() || s.canBeReplaced()) && !(s.getBlock() instanceof DeerSign) && !s.hasBlockEntity() && level.getFluidState(pos).isEmpty();
   }

   private static boolean log(BlockState s) {
      if (!s.is(BlockTags.LOGS)) {
         return false;
      }
      if (s.hasProperty(BlockStateProperties.AXIS) && s.getValue(BlockStateProperties.AXIS) != Direction.Axis.Y) {
         return false; // a log lying on its side is timber / a fallen tree
      }
      return !BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath().contains("stripped");
   }

   private static boolean naturalLeaves(BlockState s) {
      return s.is(BlockTags.LEAVES) && (!s.hasProperty(BlockStateProperties.PERSISTENT) || !s.getValue(BlockStateProperties.PERSISTENT));
   }

   /**
    * The bottom log of a growing tree's trunk: an upright, unstripped log standing on natural ground, a column of
    * logs that ends in natural (not player-placed) leaves, and no planks / other logs beside its foot (a cabin wall or
    * a fence post never counts). Returns the number of logs in the trunk, or 0 when this is not a living tree's base.
    */
   static int treeBase(Level level, BlockPos base, boolean small) {
      if (!level.hasChunkAt(base) || !log(level.getBlockState(base)) || !naturalGround(level.getBlockState(base.below()))) {
         return 0;
      }
      for (int dy = 0; dy <= 1; dy++) {
         for (Direction d : Plane.HORIZONTAL) {
            BlockState side = level.getBlockState(base.above(dy).relative(d));
            if (side.is(BlockTags.PLANKS) || side.is(BlockTags.WOODEN_STAIRS) || side.is(BlockTags.WOODEN_SLABS) || side.is(BlockTags.DOORS)) {
               return 0;
            }
            if (small && side.is(BlockTags.LOGS)) {
               return 0; // 2x2 giant trunk: bucks rub saplings and small trees
            }
         }
      }
      int logs = 1;
      BlockPos.MutableBlockPos m = base.mutable();
      while (logs < 40) {
         m.move(Direction.UP);
         if (!level.hasChunkAt(m) || !level.getBlockState(m).is(BlockTags.LOGS)) {
            break;
         }
         logs++;
      }
      if (logs < 2 || small && logs > 16) {
         return 0;
      }
      // natural leaves around the top of the column
      int top = base.getY() + logs - 1;
      for (BlockPos p : BlockPos.betweenClosed(base.getX() - 2, top - 2, base.getZ() - 2, base.getX() + 2, top + 2, base.getZ() + 2)) {
         if (level.hasChunkAt(p) && naturalLeaves(level.getBlockState(p))) {
            return logs;
         }
      }
      return 0;
   }

   /** Overhanging canopy above a scrape cell: leaves 2-7 blocks up in its column or just beside it. */
   private static boolean overhang(Level level, BlockPos cell) {
      for (int dy = 2; dy <= 7; dy++) {
         for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
               if ((dx != 0 || dz != 0) && dy < 3) {
                  continue;
               }
               BlockPos p = cell.offset(dx, dy, dz);
               if (level.hasChunkAt(p) && level.getBlockState(p).is(BlockTags.LEAVES)) {
                  return true;
               }
            }
         }
      }
      return false;
   }

   /** A spot a buck can mark: the sign's cell, its facing, the tree it belongs to and where the deer stands. */
   public record Site(DeerSign.Kind kind, BlockPos cell, Direction facing, BlockPos tree, double standX, double standZ) {
   }

   /**
    * Finds a rub or scrape site within {@code radius} blocks of the deer. Rubs: on a small natural trunk at knee to
    * chest height, the side facing the deer preferred. Scrapes: on natural ground one or two blocks from a tree,
    * under its canopy (the licking branch reaches over it). Nearest first, null when there is none.
    */
   public static Site findSite(Whitetail deer, DeerSign.Kind kind, int radius) {
      return deer.level() instanceof ServerLevel level ? findSite(level, deer.position(), deer.getRandom(), kind, radius) : null;
   }

   /** {@link #findSite(Whitetail, DeerSign.Kind, int)} around any point (the side facing that point preferred). */
   public static Site findSite(ServerLevel level, net.minecraft.world.phys.Vec3 from, RandomSource random, DeerSign.Kind kind, int radius) {
      List<Site> sites = findSites(level, from, random, kind, radius, 1, 16);
      return sites.isEmpty() ? null : sites.get(0);
   }

   /**
    * [deersign] Up to {@code limit} rub / scrape sites within {@code radius}, nearest first (one per tree). Rub trees
    * are small natural trunks of 2..{@code maxLogs} logs (a young buck works saplings, a mature buck heavier stems too).
    */
   public static List<Site> findSites(ServerLevel level, net.minecraft.world.phys.Vec3 from, RandomSource random, DeerSign.Kind kind, int radius, int limit,
      int maxLogs) {
      List<Site> found = new ArrayList<>();
      if (kind == DeerSign.Kind.BED) {
         return found;
      }
      BlockPos at = BlockPos.containing(from);
      List<BlockPos> bases = new ArrayList<>();
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      for (int dx = -radius; dx <= radius; dx++) {
         for (int dz = -radius; dz <= radius; dz++) {
            if (dx * dx + dz * dz > radius * radius) {
               continue;
            }
            for (int dy = -3; dy <= 3; dy++) {
               m.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
               if (level.hasChunkAt(m) && level.getBlockState(m).is(BlockTags.LOGS) && !level.getBlockState(m.below()).is(BlockTags.LOGS)) {
                  bases.add(m.immutable());
                  break;
               }
            }
         }
      }
      bases.sort((a, b) -> Double.compare(a.distToCenterSqr(from), b.distToCenterSqr(from)));
      int checked = 0;
      for (BlockPos base : bases) {
         if (++checked > 24 || found.size() >= limit) {
            break;
         }
         int logs = treeBase(level, base, kind == DeerSign.Kind.RUB);
         if (logs == 0 || kind == DeerSign.Kind.RUB && logs > maxLogs) {
            continue;
         }
         if (kind == DeerSign.Kind.RUB) {
            if (hasRub(level, base)) {
               continue;
            }
            // the side facing the buck first, then the others
            Direction toward = Direction.getNearest(from.x - base.getX() - 0.5, 0.0, from.z - base.getZ() - 0.5);
            List<Direction> sides = new ArrayList<>(List.of(toward, toward.getClockWise(), toward.getCounterClockWise(), toward.getOpposite()));
            if (random.nextFloat() < 0.35F) {
               java.util.Collections.swap(sides, 0, 1 + random.nextInt(2));
            }
            for (Direction d : sides) {
               BlockPos cell = base.relative(d);
               if (open(level, cell) && open(level, cell.above()) && level.getBlockState(cell.below()).isFaceSturdy(level, cell.below(), Direction.UP)
                  && !nearby(level, cell, DeerSign.Kind.RUB, 2)) {
                  found.add(new Site(kind, cell, d, base, cell.getX() + 0.5 + d.getStepX() * 0.55, cell.getZ() + 0.5 + d.getStepZ() * 0.55));
                  if (found.size() >= limit) {
                     break; // [deersign] every open side is a candidate (the buck may only fit on one)
                  }
               }
            }
         } else {
            List<BlockPos> ring = new ArrayList<>();
            for (int dx = -2; dx <= 2; dx++) {
               for (int dz = -2; dz <= 2; dz++) {
                  if ((dx != 0 || dz != 0) && Math.abs(dx) + Math.abs(dz) <= 3) {
                     for (int dy = -1; dy <= 1; dy++) {
                        ring.add(base.offset(dx, dy, dz));
                     }
                  }
               }
            }
            java.util.Collections.shuffle(ring, new java.util.Random(random.nextLong()));
            for (BlockPos cell : ring) {
               if (groundSpot(level, cell) && naturalGround(level.getBlockState(cell.below())) && overhang(level, cell)
                  && !nearby(level, cell, DeerSign.Kind.SCRAPE, 6)) {
                  Direction toTree = Direction.getNearest(base.getX() - cell.getX(), 0.0, base.getZ() - cell.getZ());
                  found.add(new Site(kind, cell, toTree, base, cell.getX() + 0.5, cell.getZ() + 0.5));
                  break;
               }
            }
         }
      }
      return found;
   }

   private static boolean hasRub(Level level, BlockPos base) {
      for (int dy = 0; dy <= 1; dy++) {
         for (Direction d : Plane.HORIZONTAL) {
            if (level.getBlockState(base.above(dy).relative(d)).getBlock() instanceof DeerSign s && s.kind == DeerSign.Kind.RUB) {
               return true;
            }
         }
      }
      return false;
   }

   /** Is the site still free and valid (the deer walked there)? */
   public static boolean stillFree(Level level, Site site) {
      if (!level.hasChunkAt(site.cell())) {
         return false;
      }
      return site.kind() == DeerSign.Kind.RUB
         ? open(level, site.cell()) && log(level.getBlockState(site.cell().relative(site.facing().getOpposite())))
         : groundSpot(level, site.cell());
   }

   /** Opens the sign at a site for this deer. */
   public static boolean make(Whitetail deer, Site site) {
      if (!(deer.level() instanceof ServerLevel level) || deer.downed() || !stillFree(level, site)) {
         return false;
      }
      BlockState state = block(site.kind()).defaultBlockState().setValue(FACING, site.facing());
      if (!state.canSurvive(level, site.cell())) {
         return false;
      }
      level.setBlock(site.cell(), state, 3);
      if (level.getBlockEntity(site.cell()) instanceof DeerSign.Mark mark) {
         mark.from(deer, level.getGameTime());
         mark.tree = site.tree();
         mark.seed = deer.getRandom().nextInt();
         mark.setChanged();
         level.sendBlockUpdated(site.cell(), state, state, 2);
      }
      return true;
   }

   /**
    * Leaves a bed where the deer lies. [deersign] Rubs and scrapes are never dropped like this: they only come from a
    * buck working a tree or the ground (sign.work.SignWork).
    */
   public static BlockPos leave(Whitetail deer, DeerSign.Kind kind) {
      if (deer.level() instanceof ServerLevel level && !deer.downed()) {
         if (kind != DeerSign.Kind.BED) {
            return null;
         }
         RandomSource random = deer.getRandom();
         BlockPos at = deer.blockPosition();

         for (int i = 0; i < 8; i++) {
            BlockPos p = at.offset(random.nextInt(9) - 4, random.nextInt(3) - 1, random.nextInt(9) - 4);
            if (level.hasChunkAt(p) && groundSpot(level, p) && !nearby(level, p, kind, 3)) {
               BlockState state = block(kind).defaultBlockState().setValue(FACING, Plane.HORIZONTAL.getRandomDirection(random));
               if (state.canSurvive(level, p)) {
                  level.setBlock(p, state, 3);
                  if (level.getBlockEntity(p) instanceof DeerSign.Mark mark) {
                     mark.from(deer, level.getGameTime());
                     mark.seed = random.nextInt();
                     mark.setChanged();
                  }

                  return p;
               }
            }
         }
      }
      return null;
   }

   private static boolean nearby(Level level, BlockPos pos, DeerSign.Kind kind, int r) {
      for (BlockPos p : BlockPos.betweenClosed(pos.offset(-r, -1, -r), pos.offset(r, 1, r))) {
         if (level.hasChunkAt(p) && level.getBlockState(p).getBlock() instanceof DeerSign s && s.kind == kind) {
            return true;
         }
      }

      return false;
   }

   public static BlockPos findNearby(Level level, BlockPos pos, DeerSign.Kind kind, int r) {
      BlockPos best = null;
      double bestD = Double.MAX_VALUE;

      for (BlockPos p : BlockPos.betweenClosed(pos.offset(-r, -2, -r), pos.offset(r, 2, r))) {
         if (level.hasChunkAt(p) && level.getBlockState(p).getBlock() instanceof DeerSign s && s.kind == kind) {
            double d = p.distSqr(pos);
            if (d < bestD) {
               bestD = d;
               best = p.immutable();
            }
         }
      }

      return best;
   }

   /** A buck works an existing scrape (or freshens a rub): it is fresh again. */
   public static void visit(ServerLevel level, BlockPos pos, Whitetail deer) {
      if (level.getBlockEntity(pos) instanceof DeerSign.Mark mark) {
         BlockState state = level.getBlockState(pos);
         if (state.getBlock() instanceof DeerSign) {
            mark.freshened = level.getGameTime();
            mark.wear = 0;
            mark.visits = Math.min(999, mark.visits + 1);
            if (deer != null && mark.maker.equals(DeerSign.Mark.NOBODY)) {
               mark.from(deer, level.getGameTime());
            }

            mark.setChanged();
            if (state.getValue(AGE) != 0) {
               level.setBlock(pos, state.setValue(AGE, 0), 2);
            } else {
               level.sendBlockUpdated(pos, state, state, 2);
            }

            level.scheduleTick(pos, state.getBlock(), CHECK);
         }
      }
   }

   public static enum Kind {
      RUB("rub", 168000),
      SCRAPE("scrape", 144000),
      BED("bed", 48000);

      public final String id;
      public final int lifetime;

      private Kind(String id, int lifetime) {
         this.id = id;
         this.lifetime = lifetime;
      }

      public String blockId() {
         return "deer_" + this.id;
      }
   }

   public static final class Mark extends BlockEntity {
      public static final UUID NOBODY = new UUID(0L, 0L);
      public UUID maker = NOBODY;
      public String species = "whitetail";
      public boolean buck = true;
      public int score;
      public int massKg;
      public int points;
      public long created;
      public long freshened;
      public int visits;
      public int wear;
      /** legacy: a scrape a hunter opened with the (removed) mock scrape kit */
      public boolean hunterMade;
      public String made = "";
      /** [sign] the trunk base log this rub is on / the tree whose licking branch hangs over this scrape (null: unknown) */
      public BlockPos tree;
      /** [sign] shape variety of the decal */
      public int seed;
      /** [deersign] rub: bottom / top of the band the antlers worked, metres above the trunk base (0/0: legacy sign) */
      public float lo;
      public float hi;
      /** [deersign] the buck works the sign from workFrom to workTo (the rub / pawed patch grows); 0: complete */
      public long workFrom;
      public long workTo;
      /** [deersign] how far the work got (a buck spooked off a half-done rub leaves a part rub) */
      public float cap = 1.0F;
      /** [deersign] scrape: licking-branch tip height above the cell and its distance from the cell centre toward the tree (0: legacy) */
      public float lickY;
      public float lickF;
      /** [deersign] scrape: the branch tip is chewed and broken from this game time on (0: legacy, chewed) */
      public long lickAt;

      /** [deersign] 0..1: how much of the sign the buck has made so far. */
      public float progress(long now) {
         if (this.workTo <= this.workFrom) {
            return this.cap;
         }
         float p = (float)(now - this.workFrom) / (float)(this.workTo - this.workFrom);
         return Math.min(this.cap, Math.clamp(p, 0.0F, 1.0F));
      }

      /** [deersign] made by a buck the deer-sign work tracked (exact band / branch); legacy marks use the size rules */
      public boolean worked() {
         return this.hi > this.lo + 0.05F;
      }

      public Mark(BlockPos pos, BlockState state) {
         super(ExpeditionContent.SIGN_MARK.get(), pos, state);
      }

      public long age(long now) {
         return Math.max(0L, now - this.freshened) + (long)Math.max(0, this.wear);
      }

      /** Visual size 0..1 of the animal that made it (a big-bodied, heavy-antlered buck rubs higher and wider). */
      public float size() {
         if (!"whitetail".equals(this.species) && !this.species.isEmpty()) {
            return 1.0F;
         }
         float mass = this.massKg <= 0 ? 0.5F : Mth.clamp((this.massKg - 45.0F) / 85.0F, 0.0F, 1.0F);
         float rack = Mth.clamp(this.points / 12.0F, 0.0F, 1.0F);
         return Mth.clamp(mass * 0.65F + rack * 0.35F, 0.0F, 1.0F);
      }

      public void from(Whitetail deer, long now) {
         DeerTraits t = deer.traits();
         this.maker = deer.getUUID();
         this.species = deer.species().id;
         this.buck = t.buck();
         this.score = t.trophyScore();
         this.massKg = deer.massKg();
         this.points = t.totalPoints();
         this.created = now;
         this.freshened = now;
         this.wear = 0;
         this.made = t.description();
      }

      public String report(long now, DeerSign.Kind kind) {
         long age = this.age(now);
         String when = age < 2400L
            ? "made within the hour"
            : (age < 24000L ? "made today" : (age < 72000L ? age / 24000L + " days old" : (age < 168000L ? "about a week old" : "old and weathered")));
         String name = switch (kind) {
            case RUB -> "Rub";
            case SCRAPE -> "Scrape";
            case BED -> "Bed";
         };
         if (this.hunterMade && this.visits == 0) {
            return name + " · opened by a hunter · " + when + " · no deer has worked it";
         }
         String who = this.made.isEmpty() ? (this.buck ? "a buck" : "a doe") : this.made.toLowerCase(Locale.ROOT);
         String size = this.buck && this.score > 0 ? " · " + this.points + " points, score " + this.score : (this.massKg > 0 ? " · about " + this.massKg + " kg" : "");
         if (kind == DeerSign.Kind.RUB && this.buck) {
            size += this.size() > 0.7F ? " · rubbed high on the trunk: a big-bodied buck" : (this.size() < 0.3F ? " · low and narrow: a young buck" : "");
         }
         long since = Math.max(0L, now - this.created);
         String worked = this.visits > 1 ? " · worked " + this.visits + " times" : "";
         if (since > 48000L && since - age > 24000L) {
            worked = worked + " · first opened " + since / 24000L + " days ago";
         }

         if (this.cap < 1.0F) {
            worked = worked + (kind == DeerSign.Kind.RUB ? " · only partly rubbed: he was disturbed" : " · only partly pawed: he was disturbed"); // [deersign]
         }
         return name + " · " + who + size + " · " + when + worked;
      }

      @Override
      protected void saveAdditional(CompoundTag tag, Provider provider) {
         super.saveAdditional(tag, provider);
         tag.putUUID("maker", this.maker);
         tag.putString("made", this.made);
         this.saveVisual(tag);
      }

      /** The part of the state clients need to draw the sign. */
      private void saveVisual(CompoundTag tag) {
         tag.putString("species", this.species);
         tag.putBoolean("buck", this.buck);
         tag.putInt("score", this.score);
         tag.putInt("mass", this.massKg);
         tag.putInt("points", this.points);
         tag.putLong("created", this.created);
         tag.putLong("freshened", this.freshened);
         tag.putInt("visits", this.visits);
         tag.putInt("wear", this.wear);
         tag.putBoolean("hunter", this.hunterMade);
         tag.putInt("seed", this.seed);
         if (this.hi > this.lo) { // [deersign]
            tag.putFloat("lo", this.lo);
            tag.putFloat("hi", this.hi);
         }
         if (this.workTo > this.workFrom) {
            tag.putLong("wf", this.workFrom);
            tag.putLong("wt", this.workTo);
         }
         if (this.cap < 1.0F) {
            tag.putFloat("cap", this.cap);
         }
         if (this.lickY > 0.0F) {
            tag.putFloat("ly", this.lickY);
            tag.putFloat("lf", this.lickF);
            tag.putLong("la", this.lickAt);
         }
         if (this.tree != null) {
            tag.putLong("tree", this.tree.asLong());
         }
      }

      @Override
      protected void loadAdditional(CompoundTag tag, Provider provider) {
         super.loadAdditional(tag, provider);
         this.maker = tag.hasUUID("maker") ? tag.getUUID("maker") : NOBODY;
         this.species = tag.getString("species").isEmpty() ? "whitetail" : tag.getString("species");
         if (this.species.length() > 32) {
            this.species = "whitetail";
         }
         this.buck = tag.getBoolean("buck");
         this.score = Math.clamp((long)tag.getInt("score"), 0, 10000);
         this.massKg = Math.clamp((long)tag.getInt("mass"), 0, 5000);
         this.points = Math.clamp((long)tag.getInt("points"), 0, 99);
         this.created = Math.max(0L, tag.getLong("created"));
         this.freshened = Math.max(0L, tag.getLong("freshened"));
         this.visits = Math.clamp((long)tag.getInt("visits"), 0, 999);
         this.wear = Math.clamp((long)tag.getInt("wear"), 0, 2000000);
         this.hunterMade = tag.getBoolean("hunter");
         this.made = tag.getString("made").length() > 64 ? "" : tag.getString("made");
         this.seed = tag.contains("seed") ? tag.getInt("seed") : (int)this.worldPosition.asLong() * 0x9E3779B9;
         // [deersign] worked band / work window / licking branch (clamped: a bad tag never draws a giant decal)
         float lo = tag.getFloat("lo");
         float hi = tag.getFloat("hi");
         if (Float.isFinite(lo) && Float.isFinite(hi) && lo >= 0.0F && hi > lo && hi - lo < 2.2F && hi < 3.2F) {
            this.lo = lo;
            this.hi = hi;
         } else {
            this.lo = 0.0F;
            this.hi = 0.0F;
         }
         this.workFrom = tag.getLong("wf");
         this.workTo = tag.getLong("wt");
         if (this.workTo < this.workFrom || this.workTo - this.workFrom > 2400L) {
            this.workFrom = 0L;
            this.workTo = 0L;
         }
         float cap = tag.contains("cap") ? tag.getFloat("cap") : 1.0F;
         this.cap = Float.isFinite(cap) ? Math.clamp(cap, 0.0F, 1.0F) : 1.0F;
         float ly = tag.getFloat("ly");
         float lf = tag.getFloat("lf");
         if (Float.isFinite(ly) && Float.isFinite(lf) && ly > 0.0F && ly < 3.0F && Math.abs(lf) < 1.5F) {
            this.lickY = ly;
            this.lickF = lf;
         } else {
            this.lickY = 0.0F;
            this.lickF = 0.0F;
         }
         this.lickAt = tag.getLong("la");
         BlockPos t = tag.contains("tree") ? BlockPos.of(tag.getLong("tree")) : null;
         // a remembered tree must be the one beside this sign (guards a bad tag)
         this.tree = t != null && Math.abs(t.getX() - this.worldPosition.getX()) <= 4 && Math.abs(t.getY() - this.worldPosition.getY()) <= 4
               && Math.abs(t.getZ() - this.worldPosition.getZ()) <= 4 ? t : null;
      }

      @Override
      public CompoundTag getUpdateTag(Provider provider) {
         CompoundTag tag = new CompoundTag();
         this.saveVisual(tag);
         return tag;
      }

      @Override
      public Packet<ClientGamePacketListener> getUpdatePacket() {
         return ClientboundBlockEntityDataPacket.create(this);
      }

      @Override
      public void onLoad() {
         super.onLoad();
         if (this.level != null && !this.level.isClientSide) {
            if (this.freshened == 0L) {
               this.freshened = this.level.getGameTime();
               this.created = this.freshened;
               this.setChanged();
            }
            if (this.workTo > this.level.getGameTime()) {
               // [deersign] loaded mid-work (the buck's work is not saved): keep what he had done
               this.cap = this.progress(this.level.getGameTime());
               this.workFrom = 0L;
               this.workTo = 0L;
               if (this.lickAt > this.level.getGameTime()) {
                  this.lickAt = Long.MAX_VALUE / 4;
               }
               this.setChanged();
            }

            this.level.scheduleTick(this.worldPosition, this.getBlockState().getBlock(), 20);
         }
      }
   }
}
