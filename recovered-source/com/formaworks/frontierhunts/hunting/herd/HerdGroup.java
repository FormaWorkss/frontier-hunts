package com.formaworks.frontierhunts.hunting.herd;

import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.formaworks.frontierhunts.hunting.Whitetail;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * [herds] One social group: who is in it (leader first, then the order they walk in), what kind of group it is, the
 * yard it has joined for the winter, the home range it uses, and the short-lived shared state of an alarm (the flight
 * heading the group takes). Saved in {@link HerdStore}; every member also carries the group id in its own data.
 */
public final class HerdGroup {
   public final UUID id;
   public GameSpecies species;
   public HerdKind kind;
   final List<Member> members = new ArrayList<>();
   /** the yard (winter gathering) this group has joined: the id of the yard's root group, or null */
   UUID parent;
   /** home range (RoutineStore) the group uses: the leader's */
   UUID rangeId;
   long formedAt;
   long lastSeen;
   BlockPos centre = BlockPos.ZERO;

   // ---------------------------------------------------------------- transient (not saved)
   long nextUpdate;
   long nextSocial;
   /** members found loaded at the last update, same order as {@link #members} (null = not loaded) */
   Whitetail[] present = new Whitetail[0];
   int presentCount;
   /** the travelling leader should stop and let a straggler catch up */
   boolean wait;
   long waitedSince = Long.MIN_VALUE;
   long waitedTotal;
   long flightAt = Long.MIN_VALUE / 4;
   double flightX;
   double flightZ;
   UUID flightBy;
   long alertAt = Long.MIN_VALUE / 4;
   /** a yard child: its place in the yard's walking order (set by the yard root) */
   int yardRank;
   /** a yard root: how many animals the yard holds (root + children), refreshed by the root's social update */
   int yardTotal;

   /** One member as the group remembers it (enough to rank and to reason about without the entity loaded). */
   static final class Member {
      final UUID id;
      HerdRole role;
      UUID mother;
      boolean male;
      int age;
      long lastSeen;
      /** transient: not seen for a long while (can't lead until it is back) */
      boolean away;

      Member(UUID id, HerdRole role, boolean male, int age) {
         this.id = id;
         this.role = role;
         this.male = male;
         this.age = age;
      }
   }

   HerdGroup(UUID id, GameSpecies species, HerdKind kind) {
      this.id = id;
      this.species = species;
      this.kind = kind;
   }

   public int size() {
      return this.members.size();
   }

   public UUID leader() {
      return this.members.isEmpty() ? null : this.members.get(0).id;
   }

   Member member(UUID id) {
      for (Member m : this.members) {
         if (m.id.equals(id)) {
            return m;
         }
      }

      return null;
   }

   int indexOf(UUID id) {
      for (int i = 0; i < this.members.size(); i++) {
         if (this.members.get(i).id.equals(id)) {
            return i;
         }
      }

      return -1;
   }

   boolean hasRole(HerdRole role) {
      for (Member m : this.members) {
         if (m.role == role) {
            return true;
         }
      }

      return false;
   }

   int count(boolean male) {
      int n = 0;
      for (Member m : this.members) {
         if (m.male == male) {
            n++;
         }
      }

      return n;
   }

   /**
    * Puts the best leader first and the rest in walking order: the leader (oldest adult female of a family, oldest male
    * of a bachelor group), adult females by age, adult males, the young (they follow their mothers anyway), a herd bull
    * last. Roles are rewritten to match.
    */
   void rank() {
      if (this.members.isEmpty()) {
         return;
      }

      Member lead = null;
      for (int pass = 0; pass < 2 && lead == null; pass++) {
         for (Member m : this.members) {
            if (m.role == HerdRole.HERD_BULL || pass == 0 && m.away) {
               continue;
            }

            if (lead == null || better(m, lead)) {
               lead = m;
            }
         }
      }

      if (lead == null) {
         lead = this.members.get(0);
      }

      final Member leader = lead;
      this.members.sort(Comparator.comparingInt((Member m) -> m == leader ? 0 : order(m)).thenComparingInt(m -> -m.age));
      for (Member m : this.members) {
         if (m == leader) {
            m.role = HerdRole.LEADER;
         } else if (m.role == HerdRole.LEADER) {
            m.role = m.age < HerdTuning.YOUNG_MONTHS ? HerdRole.YOUNG : HerdRole.ADULT;
         }
      }
   }

   private boolean better(Member a, Member b) {
      if (this.kind == HerdKind.FAMILY) {
         // a family is led by its oldest adult female
         boolean fa = !a.male && a.age >= HerdTuning.YOUNG_MONTHS;
         boolean fb = !b.male && b.age >= HerdTuning.YOUNG_MONTHS;
         if (fa != fb) {
            return fa;
         }
      }

      return a.age > b.age;
   }

   private static int order(Member m) {
      return switch (m.role) {
         case LEADER, ADULT -> m.male ? 2 : 1;
         case YOUNG -> 3;
         case HERD_BULL -> 4;
      };
   }

   // ---------------------------------------------------------------- persistence

   CompoundTag save() {
      CompoundTag t = new CompoundTag();
      t.putUUID("id", this.id);
      t.putString("species", this.species.id);
      t.putByte("kind", (byte)this.kind.ordinal());
      if (this.parent != null) {
         t.putUUID("parent", this.parent);
      }

      if (this.rangeId != null) {
         t.putUUID("range", this.rangeId);
      }

      t.putLong("formed", this.formedAt);
      t.putLong("seen", this.lastSeen);
      t.putLong("centre", this.centre.asLong());
      ListTag list = new ListTag();
      for (Member m : this.members) {
         CompoundTag c = new CompoundTag();
         c.putUUID("id", m.id);
         c.putByte("role", (byte)m.role.ordinal());
         if (m.mother != null) {
            c.putUUID("mother", m.mother);
         }

         c.putBoolean("male", m.male);
         c.putShort("age", (short)m.age);
         c.putLong("seen", m.lastSeen);
         list.add(c);
      }

      t.put("members", list);
      return t;
   }

   static HerdGroup load(CompoundTag t) {
      if (!t.hasUUID("id")) {
         return null;
      }

      GameSpecies sp = GameSpecies.byId(t.getString("species"));
      HerdGroup g = new HerdGroup(t.getUUID("id"), sp == null ? GameSpecies.WHITETAIL : sp, HerdKind.byOrdinal(t.getByte("kind")));
      g.parent = t.hasUUID("parent") ? t.getUUID("parent") : null;
      g.rangeId = t.hasUUID("range") ? t.getUUID("range") : null;
      g.formedAt = t.getLong("formed");
      g.lastSeen = t.getLong("seen");
      g.centre = BlockPos.of(t.getLong("centre"));
      ListTag list = t.getList("members", Tag.TAG_COMPOUND);
      for (int i = 0; i < list.size() && i < 128; i++) {
         CompoundTag c = list.getCompound(i);
         if (c.hasUUID("id") && g.member(c.getUUID("id")) == null) {
            Member m = new Member(c.getUUID("id"), HerdRole.byOrdinal(c.getByte("role")), c.getBoolean("male"), c.getShort("age"));
            m.mother = c.hasUUID("mother") ? c.getUUID("mother") : null;
            m.lastSeen = c.getLong("seen");
            g.members.add(m);
         }
      }

      g.rank();
      return g;
   }

   List<UUID> memberIds() {
      List<UUID> out = new ArrayList<>(this.members.size());
      for (Member m : this.members) {
         out.add(m.id);
      }

      return out;
   }
}
