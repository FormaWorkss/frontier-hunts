package com.formaworks.frontierhunts.hunts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * [hunts] Saved state of the species hunts ({@code data/frontierhunts_hunts.dat} on the overworld): gear rewards
 * waiting for hunters who were offline when they earned them, bait sites, and where duck decoys float. Server only.
 */
public final class HuntStore extends SavedData {
   public static final int MAX_PENDING = 32;
   public static final int MAX_BAITS_PER_HUNTER = 3;
   public static final int MAX_DECOYS = 4096;

   /** A bait pile: who set it, where, until when, and how many feedings are left in it. */
   public static final class Bait {
      public UUID owner;
      public String dim = "minecraft:overworld";
      public BlockPos pos = BlockPos.ZERO;
      public long until;
      public int left;

      CompoundTag save() {
         CompoundTag t = new CompoundTag();
         t.putUUID("o", this.owner);
         t.putString("d", this.dim);
         t.putLong("p", this.pos.asLong());
         t.putLong("u", this.until);
         t.putInt("l", this.left);
         return t;
      }

      static Bait load(CompoundTag t) {
         if (!t.hasUUID("o")) {
            return null;
         }
         Bait b = new Bait();
         b.owner = t.getUUID("o");
         b.dim = t.getString("d");
         b.pos = BlockPos.of(t.getLong("p"));
         b.until = t.getLong("u");
         b.left = Math.clamp(t.getInt("l"), 0, 16);
         return b;
      }
   }

   /** item id + count + why (milestone id) */
   public record Pending(String item, int count, String milestone) {
   }

   private final Map<UUID, List<Pending>> pending = new HashMap<>();
   private final List<Bait> baits = new ArrayList<>();
   private final Map<String, Set<Long>> decoys = new HashMap<>();
   private static HuntStore cached;
   private static MinecraftServer cachedFor;

   public static HuntStore get(MinecraftServer server) {
      if (cached != null && cachedFor == server) {
         return cached;
      }
      HuntStore s = server.overworld().getDataStorage().computeIfAbsent(new Factory<>(HuntStore::new, HuntStore::load, null), "frontierhunts_hunts");
      cached = s;
      cachedFor = server;
      return s;
   }

   static void forget() {
      cached = null;
      cachedFor = null;
   }

   // ---------------------------------------------------------------------------------------------- pending rewards

   public void queue(UUID id, Pending p) {
      List<Pending> l = this.pending.computeIfAbsent(id, k -> new ArrayList<>());
      if (l.size() < MAX_PENDING) {
         l.add(p);
         this.setDirty();
      }
   }

   public List<Pending> take(UUID id) {
      List<Pending> l = this.pending.remove(id);
      if (l != null) {
         this.setDirty();
      }
      return l == null ? List.of() : l;
   }

   // ---------------------------------------------------------------------------------------------- bait

   /** Adds a bait site (replacing the hunter's oldest when they already have {@link #MAX_BAITS_PER_HUNTER}). */
   public void bait(UUID owner, String dim, BlockPos pos, long until, int feedings) {
      int mine = 0;
      Bait oldest = null;
      for (Bait b : this.baits) {
         if (b.owner.equals(owner)) {
            mine++;
            if (oldest == null || b.until < oldest.until) {
               oldest = b;
            }
            if (b.dim.equals(dim) && b.pos.distSqr(pos) < 9.0) {
               // topping up the same pile
               b.until = Math.max(b.until, until);
               b.left = Math.min(16, b.left + feedings);
               this.setDirty();
               return;
            }
         }
      }
      if (mine >= MAX_BAITS_PER_HUNTER && oldest != null) {
         this.baits.remove(oldest);
      }
      Bait b = new Bait();
      b.owner = owner;
      b.dim = dim;
      b.pos = pos.immutable();
      b.until = until;
      b.left = feedings;
      this.baits.add(b);
      this.setDirty();
   }

   public List<Bait> baits() {
      return this.baits;
   }

   /** Live bait sites in a dimension within {@code r} blocks of a position. */
   public List<Bait> baitsNear(String dim, double x, double z, double r, long now) {
      List<Bait> out = new ArrayList<>();
      for (Bait b : this.baits) {
         if (b.left > 0 && b.until > now && b.dim.equals(dim)) {
            double dx = b.pos.getX() + 0.5 - x, dz = b.pos.getZ() + 0.5 - z;
            if (dx * dx + dz * dz <= r * r) {
               out.add(b);
            }
         }
      }
      return out;
   }

   public boolean anyBait() {
      return !this.baits.isEmpty();
   }

   public void expireBaits(long now) {
      if (this.baits.removeIf(b -> b.until <= now || b.left <= 0)) {
         this.setDirty();
      }
   }

   // ---------------------------------------------------------------------------------------------- decoys

   public void decoyPlaced(String dim, BlockPos pos) {
      Set<Long> s = this.decoys.computeIfAbsent(dim, k -> new LinkedHashSet<>());
      if (s.size() < MAX_DECOYS && s.add(pos.asLong())) {
         this.setDirty();
      }
   }

   public void decoyRemoved(String dim, BlockPos pos) {
      Set<Long> s = this.decoys.get(dim);
      if (s != null && s.remove(pos.asLong())) {
         this.setDirty();
      }
   }

   public boolean anyDecoys(String dim) {
      Set<Long> s = this.decoys.get(dim);
      return s != null && !s.isEmpty();
   }

   /** Decoy positions within {@code r} blocks (horizontal) of x/z; at most {@code max}. */
   public List<BlockPos> decoysNear(String dim, double x, double y, double z, double r, int max) {
      List<BlockPos> out = new ArrayList<>();
      Set<Long> s = this.decoys.get(dim);
      if (s == null) {
         return out;
      }
      for (long l : s) {
         BlockPos p = BlockPos.of(l);
         double dx = p.getX() + 0.5 - x, dz = p.getZ() + 0.5 - z, dy = p.getY() - y;
         if (dx * dx + dz * dz <= r * r && Math.abs(dy) < 24.0) {
            out.add(p);
            if (out.size() >= max) {
               break;
            }
         }
      }
      return out;
   }

   /** Drops a decoy position that no longer holds a decoy (checked lazily by the callers). */
   public void forgetDecoy(String dim, long pos) {
      Set<Long> s = this.decoys.get(dim);
      if (s != null && s.remove(pos)) {
         this.setDirty();
      }
   }

   // ---------------------------------------------------------------------------------------------- NBT

   @Override
   public CompoundTag save(CompoundTag tag, Provider provider) {
      tag.putInt("schema", 1);
      CompoundTag p = new CompoundTag();
      this.pending.forEach((id, l) -> {
         ListTag lt = new ListTag();
         for (Pending x : l) {
            CompoundTag t = new CompoundTag();
            t.putString("i", x.item());
            t.putInt("n", x.count());
            t.putString("m", x.milestone());
            lt.add(t);
         }
         p.put(id.toString(), lt);
      });
      tag.put("pending", p);
      ListTag b = new ListTag();
      for (Bait x : this.baits) {
         b.add(x.save());
      }
      tag.put("baits", b);
      CompoundTag d = new CompoundTag();
      this.decoys.forEach((dim, set) -> d.put(dim, new LongArrayTag(set.stream().mapToLong(Long::longValue).toArray())));
      tag.put("decoys", d);
      return tag;
   }

   public static HuntStore load(CompoundTag tag, Provider provider) {
      HuntStore s = new HuntStore();
      CompoundTag p = tag.getCompound("pending");
      for (String k : p.getAllKeys()) {
         try {
            UUID id = UUID.fromString(k);
            ListTag lt = p.getList(k, Tag.TAG_COMPOUND);
            List<Pending> l = new ArrayList<>();
            for (int i = 0; i < Math.min(MAX_PENDING, lt.size()); i++) {
               CompoundTag t = lt.getCompound(i);
               int n = Math.clamp(t.getInt("n"), 1, 64);
               String item = t.getString("i");
               if (!item.isEmpty() && item.length() < 128) {
                  l.add(new Pending(item, n, t.getString("m")));
               }
            }
            if (!l.isEmpty()) {
               s.pending.put(id, l);
            }
         } catch (IllegalArgumentException ignored) {
         }
      }
      ListTag b = tag.getList("baits", Tag.TAG_COMPOUND);
      for (int i = 0; i < Math.min(b.size(), 512); i++) {
         Bait x = Bait.load(b.getCompound(i));
         if (x != null) {
            s.baits.add(x);
         }
      }
      CompoundTag d = tag.getCompound("decoys");
      for (String dim : d.getAllKeys()) {
         long[] arr = d.getLongArray(dim);
         Set<Long> set = new LinkedHashSet<>();
         for (int i = 0; i < Math.min(arr.length, MAX_DECOYS); i++) {
            set.add(arr[i]);
         }
         s.decoys.put(dim, set);
      }
      return s;
   }

   /** Removes bait sites of the given owner (admin reset). */
   public void clearBaits(UUID owner) {
      Iterator<Bait> it = this.baits.iterator();
      while (it.hasNext()) {
         if (it.next().owner.equals(owner)) {
            it.remove();
            this.setDirty();
         }
      }
   }
}
