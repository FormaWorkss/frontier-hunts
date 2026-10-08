package com.formaworks.frontierhunts.academy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * [academy] Academy SavedData on the overworld ({@code data/frontierhunts_academy.dat}): the backup copy of every
 * training hunter's home state ({@link TrainingFlow.Backup}), each hunter's course record, and the table of built plots
 * in the training grounds (a slot is built once for one course and reused).
 */
public final class TrainingStore extends SavedData implements TrainingFlow.Backup {
   public static final int MAX_SLOTS = 96;
   private static final int SCHEMA = 1;

   private final Map<UUID, CompoundTag> homes = new HashMap<>();
   private final Map<UUID, Record[]> records = new HashMap<>();
   private final List<Slot> slots = new ArrayList<>();

   public static TrainingStore get(MinecraftServer server) {
      return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(TrainingStore::new, TrainingStore::load, null), "frontierhunts_academy");
   }

   /** Per course, per hunter. */
   public static final class Record {
      public int passes;
      public int bestTicks;
      public int attempts;
      /** overworld game time of the last pass that paid a reward (-1 never) */
      public long lastReward = -1L;
   }

   /** One built plot. */
   static final class Slot {
      final int course;
      final int version;

      Slot(int course, int version) {
         this.course = course;
         this.version = version;
      }
   }

   // ------------------------------------------------------------------------------------------------ backup

   @Override
   public CompoundTag get(UUID id) {
      CompoundTag t = this.homes.get(id);
      return t == null ? null : t.copy();
   }

   @Override
   public void put(UUID id, CompoundTag home) {
      this.homes.put(id, home.copy());
      this.setDirty();
   }

   @Override
   public void remove(UUID id) {
      if (this.homes.remove(id) != null) {
         this.setDirty();
      }
   }

   public boolean hasHome(UUID id) {
      return this.homes.containsKey(id);
   }

   // ------------------------------------------------------------------------------------------------ records

   public Record record(UUID id, Course c) {
      Record[] r = this.records.computeIfAbsent(id, k -> {
         Record[] a = new Record[Course.count()];
         for (int i = 0; i < a.length; i++) {
            a[i] = new Record();
         }
         return a;
      });
      return r[c.ordinal()];
   }

   public Record peek(UUID id, Course c) {
      Record[] r = this.records.get(id);
      return r == null ? null : r[c.ordinal()];
   }

   public void reset(UUID id) {
      if (this.records.remove(id) != null) {
         this.setDirty();
      }
   }

   // ------------------------------------------------------------------------------------------------ slots

   /** A built slot of this course that is not busy, or a new slot index (not yet built), or -1 when full. */
   int pick(Course c, int version, Set<Integer> busy) {
      for (int i = 0; i < this.slots.size(); i++) {
         Slot s = this.slots.get(i);
         if (s != null && s.course == c.ordinal() && s.version == version && !busy.contains(i)) {
            return i;
         }
      }
      for (int i = 0; i < this.slots.size(); i++) {
         if (this.slots.get(i) == null && !busy.contains(i)) {
            return i;
         }
      }
      return this.slots.size() < MAX_SLOTS ? this.slots.size() : -1;
   }

   boolean built(int slot, Course c, int version) {
      Slot s = slot >= 0 && slot < this.slots.size() ? this.slots.get(slot) : null;
      return s != null && s.course == c.ordinal() && s.version == version;
   }

   /** Slot is taken by some other course/version: it can never be used for this one. */
   boolean occupiedByOther(int slot, Course c, int version) {
      Slot s = slot >= 0 && slot < this.slots.size() ? this.slots.get(slot) : null;
      return s != null && (s.course != c.ordinal() || s.version != version);
   }

   void markBuilt(int slot, Course c, int version) {
      while (this.slots.size() <= slot) {
         this.slots.add(null);
      }
      this.slots.set(slot, new Slot(c.ordinal(), version));
      this.setDirty();
   }

   int slotCount() {
      return this.slots.size();
   }

   // ------------------------------------------------------------------------------------------------ NBT

   @Override
   public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
      tag.putInt("schema", SCHEMA);
      ListTag h = new ListTag();
      this.homes.forEach((id, home) -> {
         CompoundTag e = new CompoundTag();
         e.putUUID("id", id);
         e.put("home", home);
         h.add(e);
      });
      tag.put("homes", h);
      ListTag r = new ListTag();
      this.records.forEach((id, recs) -> {
         CompoundTag e = new CompoundTag();
         e.putUUID("id", id);
         ListTag list = new ListTag();
         for (int i = 0; i < recs.length; i++) {
            Record x = recs[i];
            CompoundTag c = new CompoundTag();
            c.putString("course", Course.byId(i).key);
            c.putInt("passes", x.passes);
            c.putInt("best", x.bestTicks);
            c.putInt("attempts", x.attempts);
            c.putLong("reward", x.lastReward);
            list.add(c);
         }
         e.put("courses", list);
         r.add(e);
      });
      tag.put("records", r);
      ListTag s = new ListTag();
      for (Slot slot : this.slots) {
         CompoundTag c = new CompoundTag();
         if (slot != null) {
            c.putString("course", Course.byId(slot.course).key);
            c.putInt("version", slot.version);
         }
         s.add(c);
      }
      tag.put("slots", s);
      return tag;
   }

   public static TrainingStore load(CompoundTag tag, HolderLookup.Provider provider) {
      TrainingStore st = new TrainingStore();
      ListTag h = tag.getList("homes", Tag.TAG_COMPOUND);
      for (int i = 0; i < h.size() && i < 4096; i++) {
         CompoundTag e = h.getCompound(i);
         if (e.hasUUID("id") && e.contains("home", Tag.TAG_COMPOUND)) {
            st.homes.put(e.getUUID("id"), e.getCompound("home"));
         }
      }
      ListTag r = tag.getList("records", Tag.TAG_COMPOUND);
      for (int i = 0; i < r.size() && i < 65536; i++) {
         CompoundTag e = r.getCompound(i);
         if (!e.hasUUID("id")) {
            continue;
         }
         UUID id = e.getUUID("id");
         ListTag list = e.getList("courses", Tag.TAG_COMPOUND);
         for (int k = 0; k < list.size(); k++) {
            CompoundTag c = list.getCompound(k);
            Course course = Course.byKey(c.getString("course"));
            if (course == null) {
               continue;
            }
            Record x = st.record(id, course);
            x.passes = Math.max(0, c.getInt("passes"));
            x.bestTicks = Math.max(0, c.getInt("best"));
            x.attempts = Math.max(0, c.getInt("attempts"));
            x.lastReward = c.contains("reward") ? c.getLong("reward") : -1L;
         }
      }
      ListTag s = tag.getList("slots", Tag.TAG_COMPOUND);
      for (int i = 0; i < s.size() && i < MAX_SLOTS; i++) {
         CompoundTag c = s.getCompound(i);
         Course course = Course.byKey(c.getString("course"));
         st.slots.add(course == null ? null : new Slot(course.ordinal(), c.getInt("version")));
      }
      return st;
   }
}
