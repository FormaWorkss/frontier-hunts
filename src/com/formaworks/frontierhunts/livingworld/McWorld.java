package com.formaworks.frontierhunts.livingworld;

import com.formaworks.frontierhunts.livingworld.plan.Plan;
import com.formaworks.frontierhunts.livingworld.plan.World;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/** [livingworld] {@link World} over a world-generation region (or a live server level for /frontierhunts sites place). */
public final class McWorld implements World {
   private static final Map<String, Optional<BlockState>> STATES = new ConcurrentHashMap<>();
   private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
   private final WorldGenLevel level;
   private final BoundingBox box;
   private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

   public McWorld(WorldGenLevel level, BoundingBox box) {
      this.level = level;
      this.box = box;
   }

   public static BlockState state(String spec) {
      return STATES.computeIfAbsent(spec, s -> {
         try {
            return Optional.of(BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), s, false).blockState());
         } catch (Exception ex) {
            if (WARNED.add(s)) {
               LivingWorld.LOG.warn("[Frontier Hunts] living-world: unknown block state '{}' ({})", s, ex.getMessage());
            }
            return Optional.empty();
         }
      }).orElse(null);
   }

   private BlockPos at(int x, int y, int z) {
      return this.cursor.set(x, y, z);
   }

   public static int classify(BlockState s, FluidState fluid, boolean fullCube) {
      if (s.isAir()) {
         return AIR;
      }
      if (!fluid.isEmpty() && (s.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock || !s.blocksMotion())) {
         return fluid.is(FluidTags.LAVA) ? LAVA : WATER;
      }
      if (s.is(BlockTags.LOGS)) {
         return LOG;
      }
      if (s.is(BlockTags.LEAVES) || s.getBlock() instanceof LeavesBlock) {
         return LEAVES;
      }
      if (s.canBeReplaced() || !s.blocksMotion()) {
         return PLANT;
      }
      if (fullCube && !s.hasBlockEntity()) {
         return GROUND;
      }
      return OTHER;
   }

   @Override
   public int kind(int x, int y, int z) {
      BlockPos p = this.at(x, y, z);
      BlockState s = this.level.getBlockState(p);
      return classify(s, s.getFluidState(), !s.isAir() && s.isCollisionShapeFullBlock(this.level, p));
   }

   private void put(BlockPos p, BlockState s) {
      if (s.hasProperty(BlockStateProperties.WATERLOGGED)) {
         FluidState here = this.level.getFluidState(p);
         s = s.setValue(BlockStateProperties.WATERLOGGED, here.getType() == Fluids.WATER && here.isSource());
      }
      this.level.setBlock(p, s, 2);
      // Multi-part blocks (tents, wide stations, posts) are EntityBlocks whose secondary parts have no block entity.
      // During world generation the proto chunk still queues a placeholder for them, which logs "Tried to load a block
      // entity ... but failed" when the chunk is promoted. Drop the placeholder for parts that never get one.
      if (s.hasBlockEntity() && s.getBlock() instanceof net.minecraft.world.level.block.EntityBlock eb && eb.newBlockEntity(p, s) == null) {
         this.level.getChunk(p).removeBlockEntity(p);
      }
   }

   @Override
   public void set(int x, int y, int z, String spec) {
      BlockState s = state(spec);
      if (s != null) {
         this.put(this.at(x, y, z).immutable(), s);
      }
   }

   @Override
   public void copy(int x, int fromY, int z, int toY) {
      BlockState s = this.level.getBlockState(this.at(x, fromY, z));
      if (s.hasBlockEntity()) {
         s = Blocks.DIRT.defaultBlockState();
      }
      this.level.setBlock(new BlockPos(x, toY, z), s, 2);
   }

   @Override
   public void setAir(int x, int y, int z) {
      this.level.setBlock(this.at(x, y, z).immutable(), Blocks.AIR.defaultBlockState(), 2);
   }

   @Override
   public void nbt(int x, int y, int z, String snbt) {
      BlockPos p = new BlockPos(x, y, z);
      BlockEntity be = this.level.getBlockEntity(p);
      if (be == null) {
         return;
      }
      try {
         CompoundTag tag = TagParser.parseTag(snbt);
         be.loadWithComponents(tag, this.level.registryAccess());
         be.setChanged();
      } catch (Exception ex) {
         if (WARNED.add("nbt:" + snbt)) {
            LivingWorld.LOG.warn("[Frontier Hunts] living-world: bad block data {} ({})", snbt, ex.getMessage());
         }
      }
   }

   @Override
   public void entity(double x, double y, double z, float yaw, String snbt) {
      try {
         CompoundTag tag = TagParser.parseTag(snbt);
         ListTag pos = new ListTag();
         pos.add(DoubleTag.valueOf(x));
         pos.add(DoubleTag.valueOf(y));
         pos.add(DoubleTag.valueOf(z));
         tag.put("Pos", pos);
         ListTag rot = new ListTag();
         rot.add(FloatTag.valueOf(yaw));
         rot.add(FloatTag.valueOf(0.0F));
         tag.put("Rotation", rot);
         if (tag.contains("Facing")) {
            tag.putInt("TileX", (int)Math.floor(x));
            tag.putInt("TileY", (int)Math.floor(y));
            tag.putInt("TileZ", (int)Math.floor(z));
         }
         Optional<Entity> made = EntityType.create(tag, this.level.getLevel());
         if (made.isEmpty()) {
            return;
         }
         Entity e = made.get();
         e.moveTo(x, y, z, yaw, 0.0F);
         if (e instanceof Mob mob) {
            mob.setPersistenceRequired();
         }
         this.level.addFreshEntityWithPassengers(e);
      } catch (Exception ex) {
         if (WARNED.add("entity:" + snbt)) {
            LivingWorld.LOG.warn("[Frontier Hunts] living-world: could not add entity {} ({})", snbt, ex.getMessage());
         }
      }
   }

   @Override
   public int minY() {
      return this.level.getMinBuildHeight();
   }

   @Override
   public int maxY() {
      return this.level.getMaxBuildHeight();
   }
}
