package com.formaworks.frontierhunts.hunting.routine;

import com.formaworks.frontierhunts.hunting.GameSpecies;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Per-dimension store of herd home ranges (anchors + game trails), keyed by herd id. */
public final class RoutineStore extends SavedData {
   private static final String NAME = "frontierhunts_routines";
   private static final int MAX_RANGES = 4096;
   /** ranges no member has visited for this long are forgotten (20 in-game days) */
   private static final long FORGET_TICKS = 480000L;
   private final Map<UUID, HomeRange> ranges = new HashMap<>();

   public static RoutineStore of(ServerLevel level) {
      return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(RoutineStore::new, RoutineStore::load, null), NAME);
   }

   public HomeRange get(UUID id) {
      return id == null ? null : this.ranges.get(id);
   }

   public HomeRange create(GameSpecies species, BlockPos origin, long now) {
      if (this.ranges.size() >= MAX_RANGES) {
         this.evictOldest();
      }

      HomeRange r = new HomeRange(UUID.randomUUID(), species, origin);
      r.lastSeen = now;
      r.establishedAt = now;
      this.ranges.put(r.id, r);
      this.setDirty();
      return r;
   }

   public void remove(UUID id) {
      if (this.ranges.remove(id) != null) {
         this.setDirty();
      }
   }

   public List<HomeRange> near(GameSpecies species, BlockPos at, double radius) {
      List<HomeRange> out = new ArrayList<>();
      double r2 = radius * radius;

      for (HomeRange r : this.ranges.values()) {
         if (r.species == species && r.ready() && r.anchors[HomeRange.BED].distSqr(at) <= r2) {
            out.add(r);
         }
      }

      return out;
   }

   public int size() {
      return this.ranges.size();
   }

   private void evictOldest() {
      HomeRange oldest = null;

      for (HomeRange r : this.ranges.values()) {
         if (oldest == null || r.lastSeen < oldest.lastSeen) {
            oldest = r;
         }
      }

      if (oldest != null) {
         this.ranges.remove(oldest.id);
      }
   }

   void housekeeping(long now) {
      Iterator<HomeRange> it = this.ranges.values().iterator();

      while (it.hasNext()) {
         HomeRange r = it.next();
         if (now - r.lastSeen > FORGET_TICKS || now < r.lastSeen - FORGET_TICKS) {
            it.remove();
         }
      }

      // trails and anchors change in place; save them with the periodic housekeeping
      if (!this.ranges.isEmpty()) {
         this.setDirty();
      }
   }

   public static RoutineStore load(CompoundTag tag, HolderLookup.Provider provider) {
      RoutineStore s = new RoutineStore();
      ListTag list = tag.getList("ranges", Tag.TAG_COMPOUND);

      for (int i = 0; i < list.size() && s.ranges.size() < MAX_RANGES; i++) {
         HomeRange r = HomeRange.load(list.getCompound(i));
         if (r != null) {
            s.ranges.put(r.id, r);
         }
      }

      return s;
   }

   @Override
   public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
      ListTag list = new ListTag();

      for (HomeRange r : this.ranges.values()) {
         list.add(r.save());
      }

      tag.put("ranges", list);
      return tag;
   }
}
