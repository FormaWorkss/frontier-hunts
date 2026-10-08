package com.formaworks.frontierhunts.season;

import com.formaworks.frontierhunts.FrontierHunts;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

/**
 * [1.1.2] Snowy land is generated under a real snowpack. Vanilla lays a single 2-pixel layer of snow on cold ground;
 * when a new chunk is finished, that layer is built up to a lasting snowpack instead: about half a block on open
 * ground, deeper higher up, with a natural unevenness (snowfall then builds it further, {@link DeepSnow}). Only new
 * land, only where vanilla already put snow; nothing under a canopy (the top block there is leaves).
 */
@EventBusSubscriber(modid = FrontierHunts.ID)
public final class SnowCover {
   private SnowCover() {
   }

   @SubscribeEvent
   public static void load(ChunkEvent.Load event) {
      if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD || !(event.getChunk() instanceof LevelChunk chunk)) {
         return;
      }
      // [1.2.1] snow piled on the crowns of trees (1.1.x laid up to three layers there): back to one thin layer
      clearCrowns(chunk);
      if (!event.isNewChunk()) {
         return;
      }
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
      for (int lx = 0; lx < 16; lx++) {
         for (int lz = 0; lz < 16; lz++) {
            int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
            BlockState s = chunk.getBlockState(m.set(x0 + lx, y, z0 + lz));
            boolean underCrown = SeasonalSnow.naturalCanopy(s);
            if (s.getBlock() instanceof SnowLayerBlock && SeasonalSnow.naturalCanopy(chunk.getBlockState(m.set(x0 + lx, y - 1, z0 + lz)))) {
               // a crown with its thin layer: the forest floor under it gets its snow too
               BlockState leaf = chunk.getBlockState(m.set(x0 + lx, y - 1, z0 + lz));
               int g = snowCountry(chunk, m, x0 + lx, y - 1, z0 + lz, leaf);
               if (g == Integer.MIN_VALUE) {
                  continue;
               }
               y = g;
               s = chunk.getBlockState(m.set(x0 + lx, y, z0 + lz));
               underCrown = true;
            }
            if (!(s.getBlock() instanceof SnowLayerBlock)) {
               // [1.1.9] snow country: the snow lies over the grasses too (vanilla's first snow stops at a plant)
               int at = snowCountry(chunk, m, x0 + lx, y, z0 + lz, s);
               if (at == Integer.MIN_VALUE) {
                  continue;
               }
               y = at;
               s = chunk.getBlockState(m.set(x0 + lx, y, z0 + lz));
            }
            if (!(s.getBlock() instanceof SnowLayerBlock) || s.getValue(SnowLayerBlock.LAYERS) != 1) {
               continue;
            }
            int wx = x0 + lx, wz = z0 + lz;
            // a smooth unevenness (drifts a few blocks across) plus a little grain
            double drift = Math.sin(wx * 0.21 + Math.sin(wz * 0.13) * 1.7) * 0.9 + Math.cos(wz * 0.17 - wx * 0.07) * 0.8;
            long h = wx * 341873128712L ^ wz * 132897987541L;
            double depth = 3.6 + Mth.clamp((y - 90) / 32.0, 0.0, 3.0) + drift + (((h >>> 17) & 3) - 1.5) * 0.35;
            int layers = Mth.clamp((int)Math.round(depth), 2, 8);
            if (underCrown) {
               layers = Math.min(layers, 2 + (int)((h >>> 21) & 1)); // the crown held most of it
            }
            if (SeasonalSnow.naturalCanopy(chunk.getBlockState(m.set(wx, y - 1, wz)))) {
               continue; // a crown keeps its one thin layer
            }
            m.set(wx, y, wz);
            chunk.setBlockState(m, s.setValue(SnowLayerBlock.LAYERS, layers), false);
         }
      }
   }

   /** [1.2.1] snow on the tops of tree crowns in a chunk back to one thin layer */
   static void clearCrowns(LevelChunk chunk) {
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
      for (int lx = 0; lx < 16; lx++) {
         for (int lz = 0; lz < 16; lz++) {
            int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
            BlockState s = chunk.getBlockState(m.set(x0 + lx, y, z0 + lz));
            if (!(s.getBlock() instanceof SnowLayerBlock)) {
               continue;
            }
            if (s.getValue(SnowLayerBlock.LAYERS) > 1 && SeasonalSnow.naturalCanopy(chunk.getBlockState(m.set(x0 + lx, y - 1, z0 + lz)))) {
               chunk.setBlockState(m.set(x0 + lx, y, z0 + lz), s.setValue(SnowLayerBlock.LAYERS, 1), false);
            }
         }
      }
   }

   private static final java.util.Set<String> SNOW_COUNTRY = java.util.Set.of("snowy_foothills", "snowy_pine_forest");

   /** the reserve's partial ground covers (turf, overgrowth, litter) */
   private static boolean turf(BlockState s) {
      var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock());
      if (!FrontierHunts.ID.equals(id.getNamespace())) {
         return false;
      }
      String p = id.getPath();
      return p.contains("turf") || p.contains("overgrowth") || p.contains("litter") || p.contains("sod");
   }

   /**
    * [1.1.9] In the snow-country biomes, lay the first snow where vanilla couldn't: over grasses and low plants (they go
    * under it) and on bare tops. Returns the height of the new snow layer, or MIN_VALUE if none was laid.
    */
   private static int snowCountry(LevelChunk chunk, BlockPos.MutableBlockPos m, int x, int y, int z, BlockState top) {
      var biome = chunk.getNoiseBiome(net.minecraft.core.QuartPos.fromBlock(x), net.minecraft.core.QuartPos.fromBlock(y),
         net.minecraft.core.QuartPos.fromBlock(z));
      String path = biome.unwrapKey().map(k -> k.location().getNamespace().equals(FrontierHunts.ID) ? k.location().getPath() : "").orElse("");
      boolean country = SNOW_COUNTRY.contains(path);
      if (SeasonalSnow.naturalCanopy(top)) {
         // [1.2.0] under a crown: the snow lies on the forest floor below it (in any land where it snows)
         if (!country && !biome.value().coldEnoughToSnow(m.set(x, y, z))) {
            return Integer.MIN_VALUE;
         }
         int gy = y - 1;
         for (int floor = Math.max(chunk.getMinBuildHeight(), y - 48); gy > floor; gy--) {
            BlockState b = chunk.getBlockState(m.set(x, gy, z));
            if (b.isAir() || SeasonalSnow.naturalCanopy(b)) {
               continue;
            }
            break;
         }
         BlockState g = chunk.getBlockState(m.set(x, gy, z));
         if (g.is(net.minecraft.tags.BlockTags.LOGS) || !g.getFluidState().isEmpty() || g.isAir()) {
            return Integer.MIN_VALUE; // a trunk, or water under the tree
         }
         return lay(chunk, m, x, gy, z, g);
      }
      if (!country) {
         return Integer.MIN_VALUE;
      }
      return lay(chunk, m, x, y, z, top);
   }

   /** lay a first layer of snow on (or in place of a plant at) the column top at y; its height, or MIN_VALUE */
   private static int lay(LevelChunk chunk, BlockPos.MutableBlockPos m, int x, int y, int z, BlockState top) {
      if (!top.getFluidState().isEmpty() || SeasonalSnow.naturalCanopy(top) || top.getBlock() instanceof SnowLayerBlock) {
         return top.getBlock() instanceof SnowLayerBlock ? y : Integer.MIN_VALUE;
      }
      int at;
      boolean plant = !top.isAir() && (top.canBeReplaced() || top.getCollisionShape(chunk, m.set(x, y, z)).isEmpty() && !top.hasBlockEntity());
      if (plant) {
         // a plant: it goes under the snow (both halves of a tall one)
         at = y;
         if (top.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
            && top.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
               == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) {
            BlockState lower = chunk.getBlockState(m.set(x, y - 1, z));
            if (lower.getCollisionShape(chunk, m.set(x, y - 1, z)).isEmpty() && lower.getFluidState().isEmpty()) {
               chunk.setBlockState(m.set(x, y, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), false);
               at = y - 1;
            }
         }
      } else if (top.isAir()) {
         return Integer.MIN_VALUE;
      } else {
         // a bare top: the snow goes on it
         at = y + 1;
         if (at >= chunk.getMaxBuildHeight() || !chunk.getBlockState(m.set(x, at, z)).isAir()) {
            return Integer.MIN_VALUE;
         }
      }
      BlockState below = chunk.getBlockState(m.set(x, at - 1, z));
      if (!below.isFaceSturdy(chunk, m, net.minecraft.core.Direction.UP) && turf(below)) {
         // the reserve's turf is a little sunk (its grasses stand in it); under snow it's a plain snowy sod
         below = net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState();
         chunk.setBlockState(m.set(x, at - 1, z), below, false);
         m.set(x, at - 1, z);
      }
      boolean holds = !SeasonalSnow.naturalCanopy(below) && below.isFaceSturdy(chunk, m, net.minecraft.core.Direction.UP) && !below.is(net.minecraft.world.level.block.Blocks.ICE)
            && !below.is(net.minecraft.world.level.block.Blocks.PACKED_ICE) && !below.is(net.minecraft.world.level.block.Blocks.BARRIER);
      if (!holds || !below.getFluidState().isEmpty()) {
         return Integer.MIN_VALUE;
      }
      if (below.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.SNOWY)) {
         chunk.setBlockState(m.set(x, at - 1, z), below.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SNOWY, true), false);
      }
      chunk.setBlockState(m.set(x, at, z), net.minecraft.world.level.block.Blocks.SNOW.defaultBlockState(), false);
      return at;
   }
}
