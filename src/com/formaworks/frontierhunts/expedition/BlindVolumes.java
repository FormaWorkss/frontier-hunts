package com.formaworks.frontierhunts.expedition;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.GrowingPlantBlock;
import net.minecraft.world.level.block.HangingRootsBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * Per-level registry of deployed ground blinds ({@link HubGroundBlind}) and what they do to the blocks standing
 * inside them. Both sides keep one: the server and client for collision, the client also for rendering.
 *
 * <p>The registry is fed straight from the blind's part blocks: every part state encodes its base, facing and
 * door state, so a hook on {@code LevelChunk.setBlockState} (BlindChunkMixin) plus a scan of loading chunks keeps
 * it current on both sides without a block entity, a packet or any change to saved data.
 *
 * <p>Inside a blind's volume (its frame box: 5 x 3 x 5 around the base, 3 x 3 x 3 for legacy small blinds):
 * <ul>
 *   <li>plants (grass, flowers, ferns, bushes, saplings, vines, snow layers, modded plants) and leaves are hidden;
 *       where one occupies a frame cell the frame piece is drawn in its place. They get no collision in the
 *       interior and the frame's collision in a frame cell.</li>
 *   <li>logs keep their collision; interior logs are hidden, logs in wall/roof cells stay visible with the frame
 *       drawn through them (a trunk appears to pass through the roof).</li>
 *   <li>ground (dirt, grass blocks, stone...) and everything else is untouched.</li>
 * </ul>
 * Nothing is ever broken; packing the blind up removes the volume and everything renders and collides as before.
 */
@EventBusSubscriber(modid = "frontierhunts")
public final class BlindVolumes {
   public static final byte NONE = 0;
   public static final byte PLANT = 1;
   public static final byte LEAF = 2;
   public static final byte LOG = 3;
   /** Render verdicts. */
   public static final int KEEP = 0;
   public static final int HIDE = 1;
   public static final int SUBSTITUTE = 2;
   public static final int OVERLAY = 3;

   private static final Volume[] NO_VOLUMES = new Volume[0];
   private static final Map<Level, Blinds> LEVELS = new ConcurrentHashMap<>();
   private static final Map<BlockState, Byte> KINDS = new ConcurrentHashMap<>();
   private static final Predicate<BlockState> IS_PART = s -> s.getBlock() instanceof HubGroundBlind;
   private static final Set<SoundType> PLANT_SOUNDS = Set.of(
      SoundType.GRASS, SoundType.CROP, SoundType.HARD_CROP, SoundType.SWEET_BERRY_BUSH, SoundType.VINE, SoundType.CAVE_VINES,
      SoundType.HANGING_ROOTS, SoundType.AZALEA, SoundType.FLOWERING_AZALEA, SoundType.SPORE_BLOSSOM, SoundType.SMALL_DRIPLEAF,
      SoundType.BIG_DRIPLEAF, SoundType.WET_GRASS, SoundType.ROOTS, SoundType.NETHER_SPROUTS, SoundType.FUNGUS, SoundType.PINK_PETALS,
      SoundType.CHERRY_SAPLING, SoundType.BAMBOO_SAPLING, SoundType.MOSS_CARPET, SoundType.AZALEA_LEAVES, SoundType.CHERRY_LEAVES,
      SoundType.WEEPING_VINES, SoundType.TWISTING_VINES, SoundType.SNOW
   );
   /** Total volumes over all levels: the collision fast path. */
   private static volatile int total;
   /** Blinds of the current client level (render side), or null. */
   private static volatile Blinds client;
   /** Set by the client: rebuild the chunk sections around a volume that appeared, changed or went away. */
   public static volatile Consumer<Volume> clientChanged;

   private BlindVolumes() {
   }

   public static final class Volume {
      public final BlockPos base;
      public final Direction facing;
      public final boolean big;
      public final int minX;
      public final int minY;
      public final int minZ;
      public final int maxX;
      public final int maxY;
      public final int maxZ;
      volatile boolean open;
      private final BlockState[][] states = new BlockState[2][];

      Volume(BlockPos base, Direction facing, boolean big, boolean open) {
         this.base = base.immutable();
         this.facing = facing;
         this.big = big;
         this.open = open;
         int r = big ? 2 : 1;
         this.minX = base.getX() - r;
         this.minY = base.getY();
         this.minZ = base.getZ() - r;
         this.maxX = base.getX() + r;
         this.maxY = base.getY() + 2;
         this.maxZ = base.getZ() + r;
      }

      public boolean contains(int x, int y, int z) {
         return x >= this.minX && x <= this.maxX && y >= this.minY && y <= this.maxY && z >= this.minZ && z <= this.maxZ;
      }

      public boolean open() {
         return this.open;
      }

      /** Frame part index at this position, or -1 for an interior cell. */
      public int part(int x, int y, int z) {
         return HubGroundBlind.partAt(this.big, this.facing, x - this.base.getX(), y - this.base.getY(), z - this.base.getZ());
      }

      /** The frame part's block state (current door state) - what a hidden block in a frame cell is drawn and collides as. */
      public BlockState partState(int part) {
         int o = this.open ? 1 : 0;
         BlockState[] row = this.states[o];
         if (row == null) {
            row = new BlockState[82];
            this.states[o] = row;
         }

         BlockState s = row[part];
         if (s == null) {
            s = HubGroundBlind.partState(part, this.facing, this.open);
            row[part] = s;
         }

         return s;
      }

      boolean touches(ChunkPos c) {
         return (this.minX >> 4) <= c.x && (this.maxX >> 4) >= c.x && (this.minZ >> 4) <= c.z && (this.maxZ >> 4) >= c.z;
      }
   }

   static final class Blinds {
      volatile Volume[] volumes = NO_VOLUMES;
   }

   // ------------------------------------------------------------------ classification

   /** What a block is to a blind: {@link #NONE}, {@link #PLANT}, {@link #LEAF} or {@link #LOG}. Cached per state. */
   public static byte kind(BlockState s) {
      Byte k = KINDS.get(s);
      if (k == null) {
         k = compute(s);
         KINDS.put(s, k);
      }

      return k;
   }

   private static byte compute(BlockState s) {
      try {
         if (s.isAir()) {
            return NONE;
         }

         Block b = s.getBlock();
         if (b instanceof HubGroundBlind || b instanceof TowerBlind || b instanceof BaseFireBlock || s.hasBlockEntity()) {
            return NONE;
         }

         if (s.is(BlockTags.LOGS)) {
            return LOG;
         }

         if (b instanceof LeavesBlock || s.is(BlockTags.LEAVES)) {
            return LEAF;
         }

         if (!s.getFluidState().isEmpty()) {
            return NONE;
         }

         if (plantLike(s)
            || s.is(BlockTags.REPLACEABLE)
            || s.is(BlockTags.REPLACEABLE_BY_TREES)
            || s.is(BlockTags.FLOWERS)
            || s.is(BlockTags.SAPLINGS)
            || s.is(BlockTags.CROPS)) {
            return PLANT;
         }
      } catch (RuntimeException e) {
         return NONE;
      }

      return NONE;
   }

   /** Tag-free plant test (also used when wrapping models, before tags are bound). */
   public static boolean plantLike(BlockState s) {
      Block b = s.getBlock();
      if (b instanceof BushBlock
         || b instanceof SnowLayerBlock
         || b instanceof VineBlock
         || b instanceof MultifaceBlock
         || b instanceof GrowingPlantBlock
         || b instanceof BambooStalkBlock
         || b instanceof SugarCaneBlock
         || b instanceof HangingRootsBlock) {
         return true;
      } else if (s.canBeReplaced()) {
         return true;
      } else {
         return !s.blocksMotion() && !s.canOcclude() && PLANT_SOUNDS.contains(s.getSoundType());
      }
   }

   @SubscribeEvent
   public static void tags(TagsUpdatedEvent event) {
      KINDS.clear();
   }

   // ------------------------------------------------------------------ queries

   public static boolean active() {
      return total > 0;
   }

   public static boolean clientActive() {
      Blinds c = client;
      return c != null && c.volumes.length > 0;
   }

   private static Level levelOf(BlockGetter g) {
      if (g instanceof Level l) {
         return l;
      } else {
         return g instanceof LevelChunk c ? c.getLevel() : null;
      }
   }

   private static Volume find(Volume[] vs, int x, int y, int z) {
      for (Volume v : vs) {
         if (v.contains(x, y, z)) {
            return v;
         }
      }

      return null;
   }

   /** Volume of the current client level containing this position, or null. Safe on chunk-build threads. */
   public static Volume clientVolumeAt(BlockPos pos) {
      Blinds c = client;
      return c == null ? null : find(c.volumes, pos.getX(), pos.getY(), pos.getZ());
   }

   /** Render verdict for a block at a position inside a volume. */
   public static int verdict(Volume v, BlockPos pos, byte kind) {
      if (kind == NONE) {
         return KEEP;
      } else if (v.part(pos.getX(), pos.getY(), pos.getZ()) < 0) {
         return HIDE;
      } else {
         return kind == LOG ? OVERLAY : SUBSTITUTE;
      }
   }

   /** Client render side: is this block's own model hidden (so it must not cull its neighbours' faces)? */
   public static boolean clientHides(BlockState state, BlockPos pos) {
      Volume v = clientVolumeAt(pos);
      if (v == null) {
         return false;
      } else {
         int r = verdict(v, pos, kind(state));
         return r == HIDE || r == SUBSTITUTE;
      }
   }

   /**
    * Collision replacement for a block inside a blind, or null to keep its own. Leaves and plants have none in the
    * interior and the frame's in a frame cell; logs and everything else keep theirs.
    */
   public static VoxelShape collision(BlockState state, BlockGetter getter, BlockPos pos, CollisionContext ctx) {
      Level level = levelOf(getter);
      if (level == null) {
         return null;
      }

      Blinds b = LEVELS.get(level);
      if (b == null) {
         return null;
      }

      Volume v = find(b.volumes, pos.getX(), pos.getY(), pos.getZ());
      if (v == null) {
         return null;
      }

      byte k = kind(state);
      if (k != PLANT && k != LEAF) {
         return null;
      }

      int part = v.part(pos.getX(), pos.getY(), pos.getZ());
      // the frame state is never a plant, so this cannot come back here
      return part < 0 ? Shapes.empty() : v.partState(part).getCollisionShape(getter, pos, ctx);
   }

   /** Client-only outline replacement: hidden blocks cannot be targeted (or broken) from inside the blind. */
   public static VoxelShape outline(BlockState state, BlockGetter getter, BlockPos pos) {
      Level level = levelOf(getter);
      if (level == null || !level.isClientSide()) {
         return null;
      }

      Blinds b = LEVELS.get(level);
      if (b == null) {
         return null;
      }

      Volume v = find(b.volumes, pos.getX(), pos.getY(), pos.getZ());
      if (v == null) {
         return null;
      }

      int r = verdict(v, pos, kind(state));
      return r != HIDE && r != SUBSTITUTE ? null : Shapes.empty();
   }

   /** Does any deployed blind's volume overlap this box? */
   public static boolean overlaps(Level level, BlockPos min, BlockPos max) {
      Blinds b = LEVELS.get(level);
      if (b == null) {
         return false;
      }

      for (Volume v : b.volumes) {
         if (v.minX <= max.getX() && v.maxX >= min.getX() && v.minY <= max.getY() && v.maxY >= min.getY() && v.minZ <= max.getZ() && v.maxZ >= min.getZ()) {
            return true;
         }
      }

      return false;
   }

   // ------------------------------------------------------------------ bookkeeping

   private static Blinds blinds(Level level) {
      Blinds b = LEVELS.get(level);
      if (b == null) {
         b = LEVELS.computeIfAbsent(level, l -> new Blinds());
         if (level.isClientSide()) {
            client = b;
         }
      }

      return b;
   }

   private static final Object COUNT_LOCK = new Object();

   /**
    * Recomputed under one lock after every change (server and client threads both call this): the last recount
    * to run sees every volume array published before it, so a stale 0 can't overwrite a live count.
    */
   private static void recount() {
      synchronized (COUNT_LOCK) {
         int n = 0;

         for (Blinds b : LEVELS.values()) {
            n += b.volumes.length;
         }

         total = n;
      }
   }

   private static void changed(Level level, Volume v) {
      Consumer<Volume> c = clientChanged;
      if (level.isClientSide() && c != null) {
         c.accept(v);
      }
   }

   /** A part block now stands at pos: make sure its blind is registered and its door state is current. */
   static void upsert(Level level, BlockPos pos, BlockState state) {
      BlockPos base = HubGroundBlind.base(pos, state);
      Direction facing = state.getValue(HubGroundBlind.FACING);
      boolean big = HubGroundBlind.big(state);
      boolean open = HubGroundBlind.open(state);
      Blinds b = blinds(level);
      Volume made;
      synchronized (b) {
         for (Volume v : b.volumes) {
            if (v.base.equals(base) && v.facing == facing && v.big == big) {
               if (v.open != open) {
                  v.open = open;
                  made = v;
               } else {
                  made = null;
               }

               if (made != null) {
                  changed(level, made);
               }

               return;
            }
         }

         Volume[] old = b.volumes;
         int keep = 0;
         Volume[] next = new Volume[old.length + 1];

         for (Volume v : old) {
            if (!v.base.equals(base)) {
               next[keep++] = v;
            }
         }

         made = new Volume(base, facing, big, open);
         next[keep++] = made;
         b.volumes = keep == next.length ? next : java.util.Arrays.copyOf(next, keep);
      }

      recount();
      changed(level, made);
   }

   private static void remove(Level level, Blinds b, Volume gone) {
      synchronized (b) {
         Volume[] old = b.volumes;
         int n = 0;
         Volume[] next = new Volume[old.length];

         for (Volume v : old) {
            if (v != gone) {
               next[n++] = v;
            }
         }

         if (n == old.length) {
            return;
         }

         b.volumes = n == 0 ? NO_VOLUMES : java.util.Arrays.copyOf(next, n);
      }

      recount();
      changed(level, gone);
   }

   /** Drop the volume if none of its parts stand any more (ignoring the chunk being unloaded, if any). */
   private static void validate(Level level, Blinds b, Volume v, ChunkPos skip) {
      int start = HubGroundBlind.firstIndex(v.big);
      int end = HubGroundBlind.endIndex(v.big);

      for (int i = start; i < end; i++) {
         BlockPos p = v.base.offset(HubGroundBlind.offset(i, v.facing));
         if ((skip == null || SectionPos.blockToSectionCoord(p.getX()) != skip.x || SectionPos.blockToSectionCoord(p.getZ()) != skip.z) && level.hasChunkAt(p)) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof HubGroundBlind && s.getValue(HubGroundBlind.PART) == i && s.getValue(HubGroundBlind.FACING) == v.facing) {
               if (v.open != HubGroundBlind.open(s)) {
                  v.open = HubGroundBlind.open(s);
                  changed(level, v);
               }

               return;
            }
         }
      }

      remove(level, b, v);
   }

   /** Called from BlindChunkMixin whenever a part block is set or replaced, on either side. */
   public static void partChanged(LevelChunk chunk, BlockPos pos, BlockState old, BlockState now) {
      Level level = chunk.getLevel();
      if (level == null) {
         return;
      }

      try {
         if (now.getBlock() instanceof HubGroundBlind && chunk.getBlockState(pos) == now) {
            upsert(level, pos, now);
            if (old != null && old.getBlock() instanceof HubGroundBlind && HubGroundBlind.base(pos, old).equals(HubGroundBlind.base(pos, now))) {
               return;
            }
         }

         if (old != null && old.getBlock() instanceof HubGroundBlind) {
            Blinds b = LEVELS.get(level);
            if (b != null) {
               BlockPos base = HubGroundBlind.base(pos, old);

               for (Volume v : b.volumes) {
                  if (v.base.equals(base)) {
                     validate(level, b, v, null);
                  }
               }
            }
         }
      } catch (RuntimeException e) {
         // bookkeeping must never break a block update
      }
   }

   @SubscribeEvent
   public static void chunkLoad(ChunkEvent.Load event) {
      if (event.getChunk() instanceof LevelChunk chunk && chunk.getLevel() != null) {
         Level level = chunk.getLevel();
         LevelChunkSection[] sections = chunk.getSections();
         int x0 = chunk.getPos().getMinBlockX();
         int z0 = chunk.getPos().getMinBlockZ();

         for (int si = 0; si < sections.length; si++) {
            LevelChunkSection sec = sections[si];
            if (sec != null && !sec.hasOnlyAir() && sec.maybeHas(IS_PART)) {
               int y0 = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(si));

               for (int y = 0; y < 16; y++) {
                  for (int z = 0; z < 16; z++) {
                     for (int x = 0; x < 16; x++) {
                        BlockState s = sec.getBlockState(x, y, z);
                        if (s.getBlock() instanceof HubGroundBlind) {
                           upsert(level, new BlockPos(x0 + x, y0 + y, z0 + z), s);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void chunkUnload(ChunkEvent.Unload event) {
      if (event.getChunk() instanceof LevelChunk chunk && chunk.getLevel() != null) {
         Level level = chunk.getLevel();
         Blinds b = LEVELS.get(level);
         if (b != null) {
            ChunkPos cp = chunk.getPos();

            for (Volume v : b.volumes) {
               if (v.touches(cp)) {
                  validate(level, b, v, cp);
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void levelUnload(LevelEvent.Unload event) {
      if (event.getLevel() instanceof Level level) {
         Blinds b = LEVELS.remove(level);
         if (b != null && client == b) {
            client = null;
         }

         recount();
      }
   }
}
