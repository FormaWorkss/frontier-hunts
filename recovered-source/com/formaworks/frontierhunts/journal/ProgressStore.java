package com.formaworks.frontierhunts.journal;

import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * [journal] Per-player journal records ({@code data/frontierhunts_journal.dat} on the overworld). Server only.
 * The live instance is cached for the hot perk queries and dropped at server stop.
 */
public final class ProgressStore extends SavedData {
   public static final int SCHEMA = 1;
   private final Map<UUID, HunterRecord> hunters = new HashMap<>();
   private static ProgressStore cached;
   private static MinecraftServer cachedFor;

   public static ProgressStore get(MinecraftServer server) {
      if (cached != null && cachedFor == server) {
         return cached;
      }
      ProgressStore s = server.overworld().getDataStorage().computeIfAbsent(new Factory<>(ProgressStore::new, ProgressStore::load, null), "frontierhunts_journal");
      cached = s;
      cachedFor = server;
      return s;
   }

   static void forget() {
      cached = null;
      cachedFor = null;
   }

   public HunterRecord find(UUID id) {
      return this.hunters.get(id);
   }

   public HunterRecord record(UUID id) {
      return this.hunters.computeIfAbsent(id, k -> {
         this.setDirty();
         return new HunterRecord();
      });
   }

   public void remove(UUID id) {
      if (this.hunters.remove(id) != null) {
         this.setDirty();
      }
   }

   @Override
   public CompoundTag save(CompoundTag tag, Provider provider) {
      tag.putInt("schema", SCHEMA);
      CompoundTag all = new CompoundTag();
      this.hunters.forEach((id, r) -> all.put(id.toString(), r.save()));
      tag.put("hunters", all);
      return tag;
   }

   public static ProgressStore load(CompoundTag tag, Provider provider) {
      ProgressStore s = new ProgressStore();
      CompoundTag all = tag.getCompound("hunters");
      for (String k : all.getAllKeys()) {
         try {
            s.hunters.put(UUID.fromString(k), HunterRecord.load(all.getCompound(k)));
         } catch (RuntimeException e) {
            LogUtils.getLogger().warn("Frontier Hunts journal: skipped a damaged hunter record {}", k, e);
         }
      }
      return s;
   }
}
