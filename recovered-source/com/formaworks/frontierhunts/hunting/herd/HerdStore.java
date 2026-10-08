package com.formaworks.frontierhunts.hunting.herd;

import com.formaworks.frontierhunts.hunting.GameSpecies;
import java.util.ArrayList;
import java.util.Collection;
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

/** [herds] Per-dimension store of social groups (SavedData {@code frontierhunts_herds}), with a member -> group index. */
public final class HerdStore extends SavedData {
   private static final String NAME = "frontierhunts_herds";
   private static final int MAX_GROUPS = 8192;
   private final Map<UUID, HerdGroup> groups = new HashMap<>();
   private final Map<UUID, UUID> byMember = new HashMap<>();
   /** merged groups: old id -> the group its members went to (for members that were not loaded at the merge) */
   private final Map<UUID, UUID> redirects = new java.util.LinkedHashMap<>();

   public static HerdStore of(ServerLevel level) {
      return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(HerdStore::new, HerdStore::load, null), NAME);
   }

   public HerdGroup get(UUID id) {
      return id == null ? null : this.groups.get(id);
   }

   public HerdGroup groupOf(UUID member) {
      UUID g = member == null ? null : this.byMember.get(member);
      return g == null ? null : this.groups.get(g);
   }

   void redirect(UUID from, UUID to) {
      this.redirects.remove(from);
      this.redirects.put(from, to);
      while (this.redirects.size() > 2048) {
         this.redirects.remove(this.redirects.keySet().iterator().next());
      }

      this.setDirty();
   }

   /** Where a merged group's members went (following up to four merges), or null. */
   UUID redirected(UUID from) {
      UUID at = from;
      for (int i = 0; i < 4 && at != null; i++) {
         UUID next = this.redirects.get(at);
         if (next == null) {
            return at == from ? null : at;
         }

         at = next;
      }

      return at == from ? null : at;
   }

   public Collection<HerdGroup> all() {
      return this.groups.values();
   }

   public int size() {
      return this.groups.size();
   }

   HerdGroup create(UUID id, GameSpecies species, HerdKind kind, BlockPos at, long now) {
      if (this.groups.size() >= MAX_GROUPS) {
         this.evictOldest();
      }

      HerdGroup g = new HerdGroup(id, species, kind);
      g.formedAt = now;
      g.lastSeen = now;
      g.centre = at.immutable();
      this.groups.put(id, g);
      this.setDirty();
      return g;
   }

   /** Adds the member (leaving any other group first). */
   HerdGroup.Member add(HerdGroup g, UUID id, HerdRole role, boolean male, int age, long now) {
      HerdGroup old = this.groupOf(id);
      if (old != null && old != g) {
         this.remove(old, id);
      }

      HerdGroup.Member m = g.member(id);
      if (m == null) {
         m = new HerdGroup.Member(id, role, male, age);
         g.members.add(m);
      } else {
         m.role = role;
         m.male = male;
         m.age = age;
      }

      m.lastSeen = now;
      this.byMember.put(id, g.id);
      g.rank();
      g.nextUpdate = 0L;
      this.setDirty();
      return m;
   }

   /** Removes a member; an emptied group is deleted, and young that lose their mother keep following the leader. */
   void remove(HerdGroup g, UUID id) {
      int i = g.indexOf(id);
      if (i >= 0) {
         g.members.remove(i);
         for (HerdGroup.Member m : g.members) {
            if (id.equals(m.mother)) {
               m.mother = null;
            }
         }

         if (!g.members.isEmpty()) {
            g.rank();
         }
      }

      if (g.id.equals(this.byMember.get(id))) {
         this.byMember.remove(id);
      }

      if (g.members.isEmpty()) {
         this.drop(g);
      }

      g.nextUpdate = 0L;
      this.setDirty();
   }

   void drop(HerdGroup g) {
      this.groups.remove(g.id);
      for (HerdGroup.Member m : g.members) {
         if (g.id.equals(this.byMember.get(m.id))) {
            this.byMember.remove(m.id);
         }
      }

      // a yard loses its root: the families gathered under it stand on their own again
      for (HerdGroup other : this.groups.values()) {
         if (g.id.equals(other.parent)) {
            other.parent = null;
         }
      }

      this.setDirty();
   }

   /** Root groups (not inside a yard) of a species whose centre is within the radius. */
   List<HerdGroup> near(GameSpecies species, BlockPos at, double radius, HerdGroup except) {
      List<HerdGroup> out = new ArrayList<>();
      double r2 = radius * radius;
      for (HerdGroup g : this.groups.values()) {
         if (g != except && g.species == species && !g.members.isEmpty() && horizontal(g.centre, at) <= r2) {
            out.add(g);
         }
      }

      return out;
   }

   /** The groups that have joined this yard root. */
   List<HerdGroup> children(HerdGroup root) {
      List<HerdGroup> out = new ArrayList<>();
      for (HerdGroup g : this.groups.values()) {
         if (root.id.equals(g.parent)) {
            out.add(g);
         }
      }

      return out;
   }

   static double horizontal(BlockPos a, BlockPos b) {
      double dx = a.getX() - b.getX();
      double dz = a.getZ() - b.getZ();
      return dx * dx + dz * dz;
   }

   private void evictOldest() {
      HerdGroup oldest = null;
      for (HerdGroup g : this.groups.values()) {
         if (oldest == null || g.lastSeen < oldest.lastSeen) {
            oldest = g;
         }
      }

      if (oldest != null) {
         this.drop(oldest);
      }
   }

   /** Groups nobody has ticked for a while drop their references to (unloaded) entities. */
   void releaseUnloaded(long now) {
      for (HerdGroup g : this.groups.values()) {
         if (g.presentCount > 0 && now - g.nextUpdate > 400L) {
            java.util.Arrays.fill(g.present, null);
            g.presentCount = 0;
         }
      }
   }

   void housekeeping(long now) {
      List<HerdGroup> gone = new ArrayList<>();
      for (HerdGroup g : this.groups.values()) {
         if (now - g.lastSeen > HerdTuning.GROUP_FORGET || now < g.lastSeen - HerdTuning.GROUP_FORGET) {
            gone.add(g);
         } else if (g.parent != null && !this.groups.containsKey(g.parent)) {
            g.parent = null;
         }
      }

      for (HerdGroup g : gone) {
         this.drop(g);
      }

      if (!this.groups.isEmpty()) {
         this.setDirty();
      }
   }

   public static HerdStore load(CompoundTag tag, HolderLookup.Provider provider) {
      HerdStore s = new HerdStore();
      ListTag list = tag.getList("groups", Tag.TAG_COMPOUND);
      for (int i = 0; i < list.size() && s.groups.size() < MAX_GROUPS; i++) {
         HerdGroup g = HerdGroup.load(list.getCompound(i));
         if (g != null && !g.members.isEmpty()) {
            s.groups.put(g.id, g);
         }
      }

      // the member index; an animal listed twice (a crash between saves) keeps the group seen last
      for (HerdGroup g : s.groups.values()) {
         Iterator<HerdGroup.Member> it = g.members.iterator();
         while (it.hasNext()) {
            HerdGroup.Member m = it.next();
            UUID prev = s.byMember.get(m.id);
            HerdGroup other = prev == null ? null : s.groups.get(prev);
            if (other == null || other.lastSeen < g.lastSeen) {
               if (other != null) {
                  other.members.removeIf(x -> x.id.equals(m.id));
               }

               s.byMember.put(m.id, g.id);
            } else {
               it.remove();
            }
         }
      }

      s.groups.values().removeIf(g -> g.members.isEmpty());
      ListTag red = tag.getList("redirects", Tag.TAG_COMPOUND);
      for (int i = 0; i < red.size() && i < 2048; i++) {
         CompoundTag c = red.getCompound(i);
         if (c.hasUUID("from") && c.hasUUID("to")) {
            s.redirects.put(c.getUUID("from"), c.getUUID("to"));
         }
      }

      for (HerdGroup g : s.groups.values()) {
         g.rank();
      }

      return s;
   }

   @Override
   public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
      ListTag list = new ListTag();
      for (HerdGroup g : this.groups.values()) {
         list.add(g.save());
      }

      tag.put("groups", list);
      ListTag red = new ListTag();
      for (Map.Entry<UUID, UUID> e : this.redirects.entrySet()) {
         CompoundTag c = new CompoundTag();
         c.putUUID("from", e.getKey());
         c.putUUID("to", e.getValue());
         red.add(c);
      }

      tag.put("redirects", red);
      return tag;
   }
}
