package com.formaworks.frontierhunts.client.tree;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The client world as TreeGrowth sees it. A whole tree reaches far past the few blocks the chunk
 * mesher hands a model, so trees are always read from the live client world (every block of a tree
 * then sees the same tree, whichever section is being rebuilt).
 */
final class LiveWorld implements TreeGrowth.World {
   static final LiveWorld INSTANCE = new LiveWorld();
   private final ThreadLocal<BlockPos.MutableBlockPos> pos = ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

   private LiveWorld() {
   }

   @Override
   public Object cacheIdentity() {
      return Minecraft.getInstance().level;
   }

   /** Trees first grown for a block far from the camera are grown for distance only (TreeLod). */
   @Override
   public boolean distant(int x, int z) {
      return TreeLod.growNeed(x, z) == TreeGrowth.LOD_FAR;
   }

   @Override
   public float groundHeight(float x, float z, float nearY) {
      var level = Minecraft.getInstance().level;
      if (level == null) return nearY;
      int bx = (int)Math.floor(x), bz = (int)Math.floor(z);
      for (int by = (int)Math.floor(nearY) + 2; by >= (int)Math.floor(nearY) - 5; by--) {
         BlockState state = state(level, this.pos.get().set(bx, by, bz)); // [qa] race-safe read
         // solid natural ground (soil or rock), not ground cover lying on it
         if (com.formaworks.frontierhunts.terrain.TerrainKinds.naturalKind(state.getBlock()) == 0
             || !state.isSolidRender(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) continue;
         return by + 1;
      }
      return nearY;
   }

   private BlockState at(int x, int y, int z) {
      ClientLevel l = Minecraft.getInstance().level;
      if (l == null) {
         return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
      }
      return state(l, this.pos.get().set(x, y, z));
   }

   /**
    * [qa] Reads a block of the live client world from a chunk-mesher thread. A chunk packet applied on the render thread
    * refills a section's palette in place, so a read racing it can throw MissingPaletteEntryException ("Missing Palette
    * entry for index N", seen in the gear.13 client log at a leaves block). The refill takes microseconds: retry a few
    * times, then let the caller's existing fallback handle it (the section is re-meshed after that packet anyway).
    */
   static BlockState state(net.minecraft.world.level.BlockGetter level, BlockPos p) {
      for (int attempt = 0; ; attempt++) {
         try {
            return level.getBlockState(p);
         } catch (RuntimeException e) {
            if (attempt >= 4) {
               throw e;
            }
            Thread.onSpinWait();
         }
      }
   }

   @Override
   public int kind(int x, int y, int z) {
      BlockState s = this.at(x, y, z);
      if (s.is(BlockTags.LOGS)) {
         return TreeShape.LOG;
      }
      if (s.is(BlockTags.LEAVES)) {
         return TreeShape.LEAVES;
      }
      if (s.isAir() || !s.getFluidState().isEmpty() && !s.isSolid()) {
         return TreeShape.AIR;
      }
      if (com.formaworks.frontierhunts.terrain.TerrainKinds.naturalKind(s.getBlock()) != 0) {
         return TreeShape.GROUND;
      }
      ClientLevel l = Minecraft.getInstance().level;
      return l != null && s.isSolidRender(l, this.pos.get()) ? TreeShape.SOLID : TreeShape.AIR;
   }

   @Override
   public int axis(int x, int y, int z) {
      BlockState s = this.at(x, y, z);
      return s.hasProperty(BlockStateProperties.AXIS) ? s.getValue(BlockStateProperties.AXIS).ordinal() : 1;
   }

   @Override
   public int light(int x, int y, int z) {
      ClientLevel l = Minecraft.getInstance().level;
      if (l == null) {
         return 15 << 20;
      }
      BlockPos p = this.pos.get().set(x, y, z);
      return l.getBrightness(LightLayer.SKY, p) << 20 | l.getBrightness(LightLayer.BLOCK, p) << 4;
   }

   @Override
   public boolean conifer(int x, int y, int z) {
      return TrunkModel.isConifer(BuiltInRegistries.BLOCK.getKey(this.at(x, y, z).getBlock()).getPath());
   }

   @Override
   public Object species(int x, int y, int z) {
      return this.at(x, y, z).getBlock();
   }
   @Override
   public int crownForm(int x,int y,int z) {
      String path=BuiltInRegistries.BLOCK.getKey(this.at(x,y,z).getBlock()).getPath();
      return path.contains("cherry")||path.contains("azalea")?2:path.contains("birch")||path.contains("aspen")?1:0;
   }
   /** [trees2] Leaves a tree grew (vanilla's persistent flag is set on leaves a player placed). */
   @Override
   public boolean natural(int x, int y, int z) {
      BlockState s = this.at(x, y, z);
      return !s.hasProperty(net.minecraft.world.level.block.LeavesBlock.PERSISTENT) || !s.getValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT);
   }

   /** [perf3] Direction.values() clones its array on every call. */
   private static final net.minecraft.core.Direction[] DIRECTIONS = net.minecraft.core.Direction.values();
   /** [perf3] construction() probes six neighbours of every log of a growing tree: one mutable position per thread. */
   private final ThreadLocal<BlockPos.MutableBlockPos> side = ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

   @Override
   public boolean construction(int x,int y,int z) {
      var level=Minecraft.getInstance().level;if(level==null)return false;
      BlockPos.MutableBlockPos at=this.side.get(); // [perf3] was a new BlockPos per neighbour
      for(var direction:DIRECTIONS) {
         at.set(x+direction.getStepX(),y+direction.getStepY(),z+direction.getStepZ());
         var state=state(level,at); // [qa] race-safe read
         if(!state.is(BlockTags.LOGS)&&!state.is(BlockTags.LEAVES)
             &&com.formaworks.frontierhunts.terrain.TerrainKinds.naturalKind(state.getBlock())==0
             &&state.isSolidRender(level,at))return true;
      }
      return false;
   }
}

