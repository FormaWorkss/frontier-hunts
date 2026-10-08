package com.formaworks.frontierhunts.onboard;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * [onboard] The little the Handbook keeps of its own (everything else is read from the journal, Field School, academy
 * and vanilla stats): which Handbook-only tasks a hunter chose to skip. {@code data/frontierhunts_handbook.dat}.
 */
public final class OnboardData extends SavedData {
   private static final int SCHEMA = 1;
   private final Map<UUID, Integer> skipped = new HashMap<>();

   public static OnboardData get(MinecraftServer server) {
      return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(OnboardData::new, OnboardData::load, null), "frontierhunts_handbook");
   }

   public int skipped(UUID id) {
      return this.skipped.getOrDefault(id, 0) & Handbook.ALL;
   }

   public void setSkipped(UUID id, int mask) {
      int m = mask & Handbook.ALL;
      Integer old = m == 0 ? this.skipped.remove(id) : this.skipped.put(id, m);
      if (old == null ? m != 0 : old != m) {
         this.setDirty();
      }
   }

   @Override
   public CompoundTag save(CompoundTag tag, Provider registries) {
      tag.putInt("schema", SCHEMA);
      CompoundTag all = new CompoundTag();
      this.skipped.forEach((id, m) -> all.putInt(id.toString(), m));
      tag.put("skipped", all);
      return tag;
   }

   public static OnboardData load(CompoundTag tag, Provider registries) {
      OnboardData d = new OnboardData();
      CompoundTag all = tag.getCompound("skipped");
      for (String key : all.getAllKeys()) {
         try {
            int m = all.getInt(key) & Handbook.ALL;
            if (m != 0) {
               d.skipped.put(UUID.fromString(key), m);
            }
         } catch (IllegalArgumentException ignored) {
            // a damaged key: skip that hunter only
         }
      }
      return d;
   }
}
