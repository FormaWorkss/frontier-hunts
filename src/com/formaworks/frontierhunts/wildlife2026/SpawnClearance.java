package com.formaworks.frontierhunts.wildlife2026;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.camps.CampRegistry;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BedBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/**
 * [1.1.0] Wild animals do not spawn where people live: not in or near villages, the hunting / elk / trapper camps and
 * other inhabited sites, a hunting camp's camp post, a player's bed, or a player's base (chests, barrels, furnaces,
 * beds, campfires close together). Only natural and world-generation spawns are refused - spawn eggs, commands and
 * hunting events still work - and only for land animals; fish and monsters are left to their own rules. Animals that
 * spawn further out still wander in, as they would.
 */
@EventBusSubscriber(modid = FrontierHunts.ID)
public final class SpawnClearance {
   private SpawnClearance() {
   }

   /** villages (Frontier Structures registers its own check here) */
   public static volatile BiPredicate<LevelAccessor, BlockPos> EXTRA = (l, p) -> false;
   static final int CAMP_POST = 48, BED = 32, BASE = 28;
   /** inhabited Frontier sites; hunting stands and blinds are left out on purpose - game is meant to come by those */
   static final Set<String> CAMPS = Set.of("hunting_camp", "elk_camp", "trapper_cabin", "outfitter_post", "ranger_station", "meat_shed", "trailhead",
      "abandoned_camp");

   @SubscribeEvent(priority = EventPriority.LOW)
   public static void check(MobSpawnEvent.PositionCheck e) {
      MobSpawnType type = e.getSpawnType();
      if (type != MobSpawnType.NATURAL && type != MobSpawnType.CHUNK_GENERATION) {
         return;
      }
      Mob mob = e.getEntity();
      MobCategory cat = mob.getType().getCategory();
      if (cat != MobCategory.CREATURE && cat != MobCategory.AMBIENT) {
         return;
      }
      if (inhabited(e.getLevel(), mob.blockPosition(), type == MobSpawnType.NATURAL)) {
         e.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
      }
   }

   public static boolean inhabited(ServerLevelAccessor level, BlockPos pos, boolean runtime) {
      if (EXTRA.test(level, pos)) {
         return true;
      }
      if (nearSite(level, pos)) {
         return true;
      }
      ServerLevel sl = level.getLevel();
      // camp posts
      try {
         String dim = sl.dimension().location().toString();
         for (CampRegistry.Camp c : CampRegistry.get(sl.getServer()).camps.values()) {
            if (c.post != null && dim.equals(c.postDim) && horiz(c.post, pos) < CAMP_POST * CAMP_POST) {
               return true;
            }
         }
      } catch (RuntimeException ignored) {
      }
      if (!runtime) {
         return false;
      }
      // where players sleep
      for (ServerPlayer p : sl.players()) {
         BlockPos bed = p.getRespawnPosition();
         if (bed != null && sl.dimension().equals(p.getRespawnDimension()) && horiz(bed, pos) < BED * BED) {
            return true;
         }
      }
      return base(sl, pos);
   }

   /** a chunk around the spot overlaps one of the inhabited Frontier sites */
   static boolean nearSite(LevelAccessor level, BlockPos pos) {
      int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
      var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
      for (int x = cx - 1; x <= cx + 1; x++) {
         for (int z = cz - 1; z <= cz + 1; z++) {
            ChunkAccess chunk = chunk(level, x, z);
            if (chunk == null) {
               continue;
            }
            for (Map.Entry<Structure, it.unimi.dsi.fastutil.longs.LongSet> en : chunk.getAllReferences().entrySet()) {
               if (en.getValue().isEmpty()) {
                  continue;
               }
               ResourceLocation id = registry.getKey(en.getKey());
               if (id == null) {
                  continue;
               }
               String ns = id.getNamespace(), path = id.getPath();
               if (ns.equals("frontierstructures") && path.startsWith("village")
                  || ns.equals("frontierhunts") && (CAMPS.contains(path) || path.startsWith("settlement_"))) {
                  return true;
               }
            }
         }
      }
      return false;
   }

   private static ChunkAccess chunk(LevelAccessor level, int x, int z) {
      if (level instanceof ServerLevel sl) {
         return sl.getChunkSource().getChunkNow(x, z);
      }
      return level.hasChunk(x, z) ? level.getChunk(x, z) : null;
   }

   /** chests, barrels, furnaces, beds and campfires near together: somebody lives here */
   static boolean base(ServerLevel level, BlockPos pos) {
      int score = 0;
      int r = (BASE >> 4) + 1;
      for (int x = (pos.getX() >> 4) - r; x <= (pos.getX() >> 4) + r; x++) {
         for (int z = (pos.getZ() >> 4) - r; z <= (pos.getZ() >> 4) + r; z++) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
            if (chunk == null) {
               continue;
            }
            for (BlockEntity be : chunk.getBlockEntities().values()) {
               BlockPos at = be.getBlockPos();
               if (Math.abs(at.getY() - pos.getY()) > 20 || horiz(at, pos) > BASE * BASE) {
                  continue;
               }
               if (be instanceof BedBlockEntity || be instanceof CampfireBlockEntity) {
                  score += 2;
               } else if (be instanceof BaseContainerBlockEntity) {
                  score++;
               } else if (BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()) instanceof ResourceLocation id && id.getNamespace().equals(FrontierHunts.ID)) {
                  score++;
               }
               if (score >= 2) {
                  return true;
               }
            }
         }
      }
      return false;
   }

   private static long horiz(BlockPos a, BlockPos b) {
      long dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
      return dx * dx + dz * dz;
   }
}
