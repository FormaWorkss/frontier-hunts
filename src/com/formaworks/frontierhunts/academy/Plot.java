package com.formaworks.frontierhunts.academy;

import com.formaworks.frontierhunts.environment.WildLeaves;
import com.formaworks.frontierhunts.environment.WildTrees;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * [academy] Block-placing helper for building a course plot. Coordinates are local to the plot origin: x/z offsets,
 * {@code dy = 0} is the first block above the flat ground (the ground block itself is {@code dy = -1}). Writes go
 * straight to the level with client updates only (no neighbour cascades); fences and walls get a second pass so they
 * connect. Run once per slot; a slot stays built.
 */
final class Plot {
   final ServerLevel level;
   final BlockPos origin;
   final RandomSource random;
   private final List<BlockPos> connect = new ArrayList<>();
   int writes;

   Plot(ServerLevel level, BlockPos origin, long seed) {
      this.level = level;
      this.origin = origin;
      this.random = RandomSource.create(seed);
   }

   // ------------------------------------------------------------------------------------------------ block lookup

   static BlockState block(String id) {
      ResourceLocation rl = ResourceLocation.tryParse(id.contains(":") ? id : "frontierhunts:" + id);
      Block b = rl == null ? Blocks.AIR : BuiltInRegistries.BLOCK.get(rl);
      return b == Blocks.AIR && !id.endsWith("air") ? Blocks.AIR.defaultBlockState() : b.defaultBlockState();
   }

   /** The state, or the vanilla fallback when the mod block is missing. */
   static BlockState block(String id, BlockState fallback) {
      BlockState s = block(id);
      return s.isAir() ? fallback : s;
   }

   static BlockState withInt(BlockState s, String name, int value) {
      for (Property<?> p : s.getProperties()) {
         if (p.getName().equals(name) && p instanceof IntegerProperty ip) {
            int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
            for (int v : ip.getPossibleValues()) {
               lo = Math.min(lo, v);
               hi = Math.max(hi, v);
            }
            return s.setValue(ip, Mth.clamp(value, lo, hi));
         }
      }
      return s;
   }

   static BlockState facing(BlockState s, Direction d) {
      if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && d.getAxis().isHorizontal()) {
         return s.setValue(BlockStateProperties.HORIZONTAL_FACING, d);
      }
      if (s.hasProperty(BlockStateProperties.FACING)) {
         return s.setValue(BlockStateProperties.FACING, d);
      }
      return s;
   }

   static BlockState axis(BlockState s, Direction.Axis a) {
      return s.hasProperty(BlockStateProperties.AXIS) ? s.setValue(BlockStateProperties.AXIS, a) : s;
   }

   static BlockState persistent(BlockState s) {
      return s.hasProperty(LeavesBlock.PERSISTENT) ? s.setValue(LeavesBlock.PERSISTENT, true) : s;
   }

   // ------------------------------------------------------------------------------------------------ writes

   BlockPos at(int x, int dy, int z) {
      return this.origin.offset(x, dy, z);
   }

   void put(int x, int dy, int z, BlockState s) {
      this.set(this.at(x, dy, z), s);
   }

   void set(BlockPos pos, BlockState s) {
      if (s == null || pos.getY() <= this.level.getMinBuildHeight() || pos.getY() >= this.level.getMaxBuildHeight()) {
         return;
      }
      this.level.setBlock(pos, s, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
      this.writes++;
      if (s.getBlock() instanceof net.minecraft.world.level.block.FenceBlock || s.getBlock() instanceof net.minecraft.world.level.block.WallBlock
         || s.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock) {
         this.connect.add(pos.immutable());
      }
   }

   /** Replaces the ground block (dy -1) at a column. */
   void ground(int x, int z, BlockState s) {
      this.put(x, -1, z, s);
   }

   void fill(int x0, int dy0, int z0, int x1, int dy1, int z1, BlockState s) {
      for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
         for (int y = Math.min(dy0, dy1); y <= Math.max(dy0, dy1); y++) {
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
               this.put(x, y, z, s);
            }
         }
      }
   }

   /** Plant / small decoration on the ground, only where the spot is still free. */
   void plant(int x, int z, BlockState s) {
      BlockPos p = this.at(x, 0, z);
      if (this.level.getBlockState(p).isAir()) {
         this.set(p, s);
      }
   }

   boolean free(int x, int dy, int z) {
      return this.level.getBlockState(this.at(x, dy, z)).isAir();
   }

   /** A natural tree from the mod's own tree generator (the same look as the reserve's forests). */
   void tree(int x, int z, WildTrees.Kind kind, float age) {
      WildTrees.Plan plan;
      try {
         plan = WildTrees.of(kind, this.random, Mth.clamp(age, 0.0F, 1.0F));
      } catch (RuntimeException ex) {
         return;
      }
      BlockPos base = this.at(x, 0, z);
      for (Entry<BlockPos, BlockState> e : plan.dressing().entrySet()) {
         BlockPos w = base.offset(e.getKey());
         if (w.getY() >= this.origin.getY() && this.level.getBlockState(w).isAir()) {
            this.set(w, e.getValue());
         }
      }
      for (Entry<BlockPos, BlockState> e : plan.wood().entrySet()) {
         BlockPos w = base.offset(e.getKey());
         if (w.getY() >= this.origin.getY() - 1) {
            this.set(w, e.getValue());
         }
      }
      BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
      for (Entry<BlockPos, BlockState> e : plan.leaves().entrySet()) {
         BlockPos w = base.offset(e.getKey());
         if (!this.level.getBlockState(w).isAir()) {
            continue;
         }
         BlockState s = persistent(e.getValue());
         if (s.hasProperty(WildLeaves.EDGE)) {
            boolean edge = false;
            for (Direction side : Direction.values()) {
               if (side != Direction.DOWN) {
                  cursor.setWithOffset(e.getKey(), side);
                  if (!plan.leaves().containsKey(cursor) && !plan.wood().containsKey(cursor)) {
                     edge = true;
                     break;
                  }
               }
            }
            s = s.setValue(WildLeaves.EDGE, edge);
         }
         this.set(w, s);
      }
   }

   /** Rail fence with an invisible barrier wall above it (people and deer stay inside the plot). */
   void boundary(int minX, int maxX, int minZ, int maxZ, int height) {
      BlockState fence = block("pine_fence", Blocks.SPRUCE_FENCE.defaultBlockState());
      BlockState barrier = Blocks.BARRIER.defaultBlockState();
      for (int x = minX; x <= maxX; x++) {
         this.column(x, minZ, fence, barrier, height);
         this.column(x, maxZ, fence, barrier, height);
      }
      for (int z = minZ + 1; z < maxZ; z++) {
         this.column(minX, z, fence, barrier, height);
         this.column(maxX, z, fence, barrier, height);
      }
   }

   private void column(int x, int z, BlockState fence, BlockState barrier, int height) {
      this.put(x, 0, z, fence);
      for (int y = 1; y <= height; y++) {
         this.put(x, y, z, barrier);
      }
   }

   /** Second pass: fences, walls and bars take their connected shapes. */
   void finish() {
      for (BlockPos p : this.connect) {
         BlockState s = this.level.getBlockState(p);
         BlockState u = Block.updateFromNeighbourShapes(s, this.level, p);
         if (u != s) {
            this.level.setBlock(p, u, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
         }
      }
      this.connect.clear();
   }

   int rnd(int lo, int hi) {
      return lo + this.random.nextInt(Math.max(1, hi - lo + 1));
   }

   float rndf() {
      return this.random.nextFloat();
   }
}
