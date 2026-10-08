package com.formaworks.frontierstructures;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * [gear.19] No half buildings in worlds that were explored on an older version.
 *
 * <p>A structure is decided when its start chunk generates, and each chunk it overlaps builds its own part when that
 * chunk is decorated. In a fresh world that always lines up. But when an update changes where structures go (gear.18
 * made villages and lookouts more common), a new village can be decided right next to land that was already generated
 * on the older version: those chunks are finished and never build their part, so the village (or a single building)
 * is cut off at the old border, e.g. only its front standing.
 *
 * <p>So before a village or building is placed, the chunks it covers are checked on disk: if any of them was already
 * generated past the structure stage (by an older version), the site is skipped. Chunks that are only at the very
 * first generation step are fine, they still learn about the structure. Reads go through the world's own chunk IO
 * (the same cached region files the game uses), only for sites that passed every other check, so it is cheap.
 */
final class OldLand {
   private OldLand() {
   }

   /** True when any chunk overlapping the block box (plus margin) was generated on an older layout. */
   static boolean touches(int minX, int minZ, int maxX, int maxZ, int margin) {
      MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
      if (server == null) {
         return false;
      }
      ServerLevel level = server.overworld();
      if (level == null) {
         return false;
      }
      int cx0 = (minX - margin) >> 4, cz0 = (minZ - margin) >> 4, cx1 = (maxX + margin) >> 4, cz1 = (maxZ + margin) >> 4;
      if ((long)(cx1 - cx0 + 1) * (cz1 - cz0 + 1) > 400) {
         return false; // nonsense box: don't stall worldgen over it
      }
      for (int cx = cx0; cx <= cx1; cx++) {
         for (int cz = cz0; cz <= cz1; cz++) {
            try {
               Optional<CompoundTag> tag = level.getChunkSource().chunkMap.read(new ChunkPos(cx, cz)).get(5, TimeUnit.SECONDS);
               if (tag.isPresent() && pastStructures(tag.get().getString("Status"))) {
                  return true;
               }
            } catch (Exception e) {
               // unreadable: treat as fresh (the old behaviour)
            }
         }
      }
      return false;
   }

   private static boolean pastStructures(String status) {
      String s = status.startsWith("minecraft:") ? status.substring(10) : status;
      return !(s.isEmpty() || s.equals("empty") || s.equals("structure_starts"));
   }
}
