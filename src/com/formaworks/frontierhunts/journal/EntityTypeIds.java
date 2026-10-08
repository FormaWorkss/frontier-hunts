package com.formaworks.frontierhunts.journal;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/** [journal] Cached "namespace:path" ids of entity types (no class dependency on other workstreams' entities). */
final class EntityTypeIds {
   private static final Map<EntityType<?>, String> IDS = new IdentityHashMap<>();

   private EntityTypeIds() {
   }

   static synchronized String id(Entity e) {
      return IDS.computeIfAbsent(e.getType(), t -> BuiltInRegistries.ENTITY_TYPE.getKey(t).toString());
   }
}
