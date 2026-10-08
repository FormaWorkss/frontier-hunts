package com.formaworks.frontierhunts.workshop;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * [fishing2] Fish that simply live in the water. Vanilla only spawns fish in oceans and rivers near sea level, so the
 * Frontier's lakes, ponds and mountain streams were empty unless you threw chum. Around each player a few fish are kept
 * in suitable water: salmon (stream trout) in flowing water and shallow streams, cod (lake fish) in still, deeper
 * water, in small loose groups. Capped per area, never in the open ocean (vanilla handles that), only in loaded,
 * ticking water, and they despawn with distance like any vanilla fish. Vanilla fish already flee from players and come
 * back, and the mod's float and bowfishing arrow already work with them.
 */
@EventBusSubscriber(modid = "frontierhunts")
public final class AmbientFish {
   /** fish within {@link #AREA} blocks of a player before more are added */
   private static final int CAP = 7;
   private static final double AREA = 40.0;
   private static final int EVERY = 120;

   private AmbientFish() {
   }

   @SubscribeEvent
   public static void tick(PlayerTickEvent.Post event) {
      if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator() || (player.tickCount + player.getId()) % EVERY != 0) {
         return;
      }
      ServerLevel level = player.serverLevel();
      if (!level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING)) {
         return;
      }
      int have = level.getEntitiesOfClass(AbstractFish.class, player.getBoundingBox().inflate(AREA, 24.0, AREA)).size();
      if (have >= CAP) {
         return;
      }
      RandomSource r = level.random;
      for (int attempt = 0; attempt < 6 && have < CAP; attempt++) {
         double a = r.nextDouble() * Math.PI * 2.0;
         double d = 12.0 + r.nextDouble() * 26.0;
         int x = (int)Math.floor(player.getX() + Math.cos(a) * d);
         int z = (int)Math.floor(player.getZ() + Math.sin(a) * d);
         BlockPos column = new BlockPos(x, 0, z);
         if (!level.hasChunkAt(column) || !level.isPositionEntityTicking(column)) {
            continue;
         }
         int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
         BlockPos surface = new BlockPos(x, top, z);
         FluidState fs = level.getFluidState(surface);
         if (!fs.is(FluidTags.WATER)) {
            continue;
         }
         Holder<Biome> biome = level.getBiome(surface);
         if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN)) {
            continue;
         }
         int depth = 0;
         boolean flowing = !fs.isSource();
         while (depth < 12 && level.getFluidState(surface.below(depth + 1)).is(FluidTags.WATER)) {
            depth++;
            if (!level.getFluidState(surface.below(depth)).isSource()) {
               flowing = true;
            }
         }
         depth++; // the surface block itself
         if (depth < 2) {
            continue;
         }
         boolean stream = flowing || depth <= 3 || biome.is(BiomeTags.IS_RIVER);
         EntityType<? extends AbstractFish> type = stream ? EntityType.SALMON : EntityType.COD;
         int group = stream ? 1 + r.nextInt(2) : 1 + r.nextInt(3);
         for (int i = 0; i < group && have < CAP; i++) {
            BlockPos at = new BlockPos(x + r.nextInt(3) - 1, top - 1 - r.nextInt(Math.max(1, depth - 1)), z + r.nextInt(3) - 1);
            if (!level.getFluidState(at).is(FluidTags.WATER) || !level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
               continue;
            }
            if (type.spawn(level, at, MobSpawnType.NATURAL) != null) {
               have++;
            }
         }
      }
   }

   /** fish within reach of a point (used by tests and the debug command) */
   public static int around(ServerLevel level, BlockPos pos, double r) {
      return level.getEntitiesOfClass(AbstractFish.class, new AABB(pos).inflate(r)).size();
   }
}
