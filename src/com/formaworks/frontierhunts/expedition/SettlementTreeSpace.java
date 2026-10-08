package com.formaworks.frontierhunts.expedition;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

public final class SettlementTreeSpace {
   private static final ThreadLocal<SettlementTreeSpace.Cache> CACHE = new ThreadLocal<>();
   private static final int CROWN_MARGIN = 16;
   /** [1.1.0] Frontier Structures' villages: is this trunk spot kept clear? (set by that mod; see VillageTrees) */
   public static volatile java.util.function.BiPredicate<WorldGenLevel, BlockPos> EXTRA = (l, p) -> false;

   public static boolean reserved(WorldGenLevel level, BlockPos origin) {
      if (EXTRA.test(level, origin)) {
         return true;
      }
      if (!(level instanceof WorldGenRegion region)) {
         return false;
      } else {
         SettlementTreeSpace.Cache cached = CACHE.get();
         if (cached == null || cached.region().get() != region) {
            ArrayList<BoundingBox> boxes = new ArrayList<>();
            Set<StructureStart> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            ChunkPos center = region.getCenter();
            Registry<Structure> registry = region.registryAccess().registryOrThrow(Registries.STRUCTURE);

            for (int x = center.x - 1; x <= center.x + 1; x++) {
               for (int z = center.z - 1; z <= center.z + 1; z++) {
                  if (region.hasChunk(x, z)) {
                     ChunkAccess chunk = region.getChunk(x, z, ChunkStatus.STRUCTURE_REFERENCES, false);
                     if (chunk != null) {
                        for (Entry<Structure, LongSet> entry : chunk.getAllReferences().entrySet()) {
                           ResourceLocation id = registry.getKey(entry.getKey());
                           if (id != null && id.getNamespace().equals("frontierhunts") && id.getPath().startsWith("settlement_")) {
                              LongIterator var14 = entry.getValue().iterator();

                              while (var14.hasNext()) {
                                 long reference = (Long)var14.next();
                                 ChunkPos at = new ChunkPos(reference);
                                 if (region.hasChunk(at.x, at.z)) {
                                    ChunkAccess source = region.getChunk(at.x, at.z, ChunkStatus.STRUCTURE_STARTS, false);
                                    if (source != null) {
                                       StructureStart start = source.getStartForStructure(entry.getKey());
                                       if (start != null && start.isValid() && seen.add(start)) {
                                          boxes.add(start.getBoundingBox());
                                       }
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }

            cached = new SettlementTreeSpace.Cache(new WeakReference<>(region), List.copyOf(boxes));
            CACHE.set(cached);
         }

         for (BoundingBox box : cached.sites()) {
            if (origin.getX() >= box.minX() - 16 && origin.getX() <= box.maxX() + 16 && origin.getZ() >= box.minZ() - 16 && origin.getZ() <= box.maxZ() + 16) {
               return true;
            }
         }

         return false;
      }
   }

   private SettlementTreeSpace() {
   }

   private static record Cache(WeakReference<WorldGenRegion> region, List<BoundingBox> sites) {
   }
}
