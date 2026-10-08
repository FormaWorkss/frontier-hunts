package com.formaworks.frontierhunts.tracking.hound;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;

/** [tracking] One hound per player: owner -> hound, with where and when it was last seen (overworld saved data). */
public final class HoundRegistry extends SavedData {
   public static final class Entry {
      public UUID hound;
      public long seen;
      public String dimension = "minecraft:overworld";
      public double x, y, z;
      /** [hound4] sent home (dismissed): no hound in the world; the Hound Lead brings this one back */
      public boolean home;
      public String name = "";
      public int variant;
   }

   private final Map<UUID, Entry> owners = new HashMap<>();

   public static HoundRegistry get(MinecraftServer server) {
      return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(HoundRegistry::new, HoundRegistry::load, null), "frontierhunts_hounds");
   }

   public Entry get(UUID owner) {
      return this.owners.get(owner);
   }

   public void put(UUID owner, TrackingHound hound) {
      Entry e = new Entry();
      e.hound = hound.getUUID();
      this.owners.put(owner, e);
      this.seen(owner, hound);
   }

   /** [hound4] the hound was sent home: keep his name and coat so the lead brings the same dog back */
   public void kennel(UUID owner, TrackingHound hound) {
      Entry e = this.owners.get(owner);
      if (e != null && e.hound.equals(hound.getUUID())) {
         e.home = true;
         e.name = hound.hasCustomName() ? hound.getName().getString() : "";
         e.variant = hound.variant();
         this.setDirty();
      }
   }

   /** [hound4] sent home while not loaded anywhere: he stays where he is until his chunk loads, then leaves (tick check) */
   public void kennel(UUID owner) {
      Entry e = this.owners.get(owner);
      if (e != null && !e.home) {
         e.home = true;
         this.setDirty();
      }
   }

   /** called by the hound itself every few seconds */
   public void seen(UUID owner, Entity hound) {
      Entry e = this.owners.get(owner);
      if (e != null && !e.home && e.hound.equals(hound.getUUID())) {
         e.seen = hound.level().getGameTime();
         e.dimension = hound.level().dimension().location().toString();
         e.x = hound.getX();
         e.y = hound.getY();
         e.z = hound.getZ();
         this.setDirty();
      }
   }

   public void remove(UUID owner, UUID hound) {
      Entry e = this.owners.get(owner);
      if (e != null && e.hound.equals(hound)) {
         this.owners.remove(owner);
         this.setDirty();
      }
   }

   /** the owner's hound if it is loaded anywhere on the server */
   public static TrackingHound find(MinecraftServer server, UUID owner) {
      Entry e = get(server).get(owner);
      if (e == null || e.home) {
         return null;
      }
      for (ServerLevel level : server.getAllLevels()) {
         if (level.getEntity(e.hound) instanceof TrackingHound h && h.isAlive()) {
            return h;
         }
      }
      return null;
   }

   @Override
   public CompoundTag save(CompoundTag tag, Provider provider) {
      ListTag list = new ListTag();
      this.owners.forEach((owner, e) -> {
         CompoundTag t = new CompoundTag();
         t.putUUID("owner", owner);
         t.putUUID("hound", e.hound);
         t.putLong("seen", e.seen);
         t.putString("dim", e.dimension);
         t.putDouble("x", e.x);
         t.putDouble("y", e.y);
         t.putDouble("z", e.z);
         if (e.home) { // [hound4]
            t.putBoolean("home", true);
            t.putString("name", e.name);
            t.putInt("variant", e.variant);
         }
         list.add(t);
      });
      tag.put("hounds", list);
      return tag;
   }

   public static HoundRegistry load(CompoundTag tag, Provider provider) {
      HoundRegistry r = new HoundRegistry();
      ListTag list = tag.getList("hounds", 10);
      for (int i = 0; i < list.size() && i < 4096; i++) {
         CompoundTag t = list.getCompound(i);
         if (t.hasUUID("owner") && t.hasUUID("hound")) {
            Entry e = new Entry();
            e.hound = t.getUUID("hound");
            e.seen = t.getLong("seen");
            e.dimension = t.getString("dim");
            e.x = t.getDouble("x");
            e.y = t.getDouble("y");
            e.z = t.getDouble("z");
            e.home = t.getBoolean("home"); // [hound4]
            e.name = t.getString("name");
            e.variant = Math.clamp(t.getInt("variant"), 0, 1);
            r.owners.put(t.getUUID("owner"), e);
         }
      }
      return r;
   }
}
