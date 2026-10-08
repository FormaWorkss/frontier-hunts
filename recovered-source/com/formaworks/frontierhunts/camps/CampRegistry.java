package com.formaworks.frontierhunts.camps;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Hunting camps (SavedData "frontierhunts_camps", overworld). Purely social: no claims, no block protection. */
public final class CampRegistry extends SavedData {
   public static final Pattern NAME = Pattern.compile("[A-Za-z0-9 '&.\\-]{3,24}");
   public final Map<UUID, CampRegistry.Camp> camps = new LinkedHashMap<>();
   private final Map<UUID, UUID> byMember = new HashMap<>();
   /** respawn spots set by a camp post for hunters who have since left the camp / lost the post (cleared on next login) */
   public final Map<UUID, Long> orphanRespawn = new HashMap<>();

   public static CampRegistry get(MinecraftServer server) {
      return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(CampRegistry::new, CampRegistry::load, null), "frontierhunts_camps");
   }

   public CampRegistry.Camp campOf(UUID player) {
      UUID id = player == null ? null : this.byMember.get(player);
      return id == null ? null : this.camps.get(id);
   }

   public CampRegistry.Camp byId(UUID id) {
      return id == null ? null : this.camps.get(id);
   }

   public CampRegistry.Camp byName(String name) {
      if (name == null) {
         return null;
      }
      String n = clean(name).toLowerCase(Locale.ROOT);
      for (CampRegistry.Camp c : this.camps.values()) {
         if (c.name.toLowerCase(Locale.ROOT).equals(n)) {
            return c;
         }
      }
      return null;
   }

   public static String clean(String raw) {
      return raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
   }

   /** Null if the name can be used, otherwise the reason. */
   public String nameProblem(String raw, CampRegistry.Camp self) {
      String n = clean(raw);
      if (!NAME.matcher(n).matches()) {
         return "Camp names are 3-24 letters, digits, spaces or ' & . -";
      }
      CampRegistry.Camp other = this.byName(n);
      return other != null && other != self ? "Another camp is already called " + other.name : null;
   }

   public CampRegistry.Camp create(UUID owner, String ownerName, String name, int color, long now) {
      CampRegistry.Camp c = new CampRegistry.Camp();
      c.id = UUID.randomUUID();
      c.name = clean(name);
      c.owner = owner;
      c.color = Math.floorMod(color, 16);
      c.founded = now;
      c.members.put(owner, ownerName);
      this.camps.put(c.id, c);
      this.byMember.put(owner, c.id);
      c.log("Camp founded by " + ownerName);
      this.setDirty();
      return c;
   }

   public void join(CampRegistry.Camp c, UUID player, String name) {
      c.members.put(player, name);
      c.invites.remove(player);
      this.byMember.put(player, c.id);
      c.log(name + " joined the camp");
      this.setDirty();
   }

   /** Removes a member; hands the camp to the longest-standing member or disbands it when empty. */
   public CampRegistry.Camp leave(UUID player) {
      CampRegistry.Camp c = this.campOf(player);
      if (c == null) {
         return null;
      }
      String name = c.members.remove(player);
      Long spot = c.respawn.remove(player);
      if (spot != null) {
         this.orphanRespawn.put(player, spot);
      }
      this.byMember.remove(player);
      if (c.members.isEmpty()) {
         this.camps.remove(c.id);
      } else {
         c.log((name == null ? "A hunter" : name) + " left the camp");
         if (player.equals(c.owner)) {
            c.owner = c.members.keySet().iterator().next();
            c.log(c.members.get(c.owner) + " now runs the camp");
         }
      }
      this.setDirty();
      return c;
   }

   public void disband(CampRegistry.Camp c) {
      c.members.keySet().forEach(this.byMember::remove);
      this.orphanRespawn.putAll(c.respawn);
      c.respawn.clear();
      this.camps.remove(c.id);
      this.setDirty();
   }

   @Override
   public CompoundTag save(CompoundTag tag, Provider provider) {
      tag.putInt("schema", 1);
      ListTag l = new ListTag();
      this.camps.values().forEach(c -> l.add(c.save()));
      tag.put("camps", l);
      ListTag o = new ListTag();
      this.orphanRespawn.forEach((k, v) -> {
         CompoundTag e = new CompoundTag();
         e.putUUID("id", k);
         e.putLong("at", v);
         o.add(e);
      });
      tag.put("orphan_respawn", o);
      return tag;
   }

   public static CampRegistry load(CompoundTag tag, Provider provider) {
      CampRegistry r = new CampRegistry();
      for (Tag t : tag.getList("camps", 10)) {
         CampRegistry.Camp c = CampRegistry.Camp.load((CompoundTag)t);
         if (c == null || c.members.isEmpty()) {
            continue;
         }
         // a hunter belongs to one camp: first camp wins if a save was ever edited by hand
         c.members.keySet().removeIf(r.byMember::containsKey);
         if (c.members.isEmpty()) {
            continue;
         }
         if (!c.members.containsKey(c.owner)) {
            c.owner = c.members.keySet().iterator().next();
         }
         r.camps.put(c.id, c);
         c.members.keySet().forEach(m -> r.byMember.put(m, c.id));
         c.respawn.keySet().retainAll(c.members.keySet());
      }
      for (Tag t : tag.getList("orphan_respawn", 10)) {
         CompoundTag e = (CompoundTag)t;
         if (e.hasUUID("id")) {
            r.orphanRespawn.put(e.getUUID("id"), e.getLong("at"));
         }
      }
      return r;
   }

   public static final class Camp {
      public UUID id;
      public String name = "Camp";
      public UUID owner;
      public final LinkedHashMap<UUID, String> members = new LinkedHashMap<>();
      /** pending invites: hunter -> expiry game time */
      public final Map<UUID, Long> invites = new HashMap<>();
      public int tier;
      public int color;
      public String postDim = "";
      public BlockPos post;
      public long founded;
      /** members who respawn at the post -> the respawn spot that was set for them */
      public final Map<UUID, Long> respawn = new HashMap<>();
      public int harvests;
      public String best = "";
      public double bestScore;
      public final Deque<String> log = new ArrayDeque<>();

      public void log(String line) {
         this.log.addFirst(line);
         while (this.log.size() > 24) {
            this.log.removeLast();
         }
      }

      public List<UUID> memberIds() {
         return new ArrayList<>(this.members.keySet());
      }

      CompoundTag save() {
         CompoundTag t = new CompoundTag();
         t.putUUID("id", this.id);
         t.putString("name", this.name);
         t.putUUID("owner", this.owner);
         ListTag m = new ListTag();
         this.members.forEach((k, v) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("id", k);
            e.putString("name", v);
            if (this.respawn.containsKey(k)) {
               e.putLong("respawn_at", this.respawn.get(k));
            }
            m.add(e);
         });
         t.put("members", m);
         ListTag inv = new ListTag();
         this.invites.forEach((k, v) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("id", k);
            e.putLong("until", v);
            inv.add(e);
         });
         t.put("invites", inv);
         t.putInt("tier", this.tier);
         t.putInt("color", this.color);
         if (this.post != null) {
            t.putString("post_dim", this.postDim);
            t.putLong("post", this.post.asLong());
         }
         t.putLong("founded", this.founded);
         t.putInt("harvests", this.harvests);
         t.putString("best", this.best);
         t.putDouble("best_score", this.bestScore);
         ListTag lg = new ListTag();
         this.log.forEach(s -> lg.add(StringTag.valueOf(s)));
         t.put("log", lg);
         return t;
      }

      static CampRegistry.Camp load(CompoundTag t) {
         if (!t.hasUUID("id") || !t.hasUUID("owner")) {
            return null;
         }
         CampRegistry.Camp c = new CampRegistry.Camp();
         c.id = t.getUUID("id");
         c.name = HarvestRecord.clip(clean(t.getString("name")), 24);
         if (c.name.length() < 3) {
            c.name = "Camp";
         }
         c.owner = t.getUUID("owner");
         for (Tag x : t.getList("members", 10)) {
            CompoundTag e = (CompoundTag)x;
            if (e.hasUUID("id") && c.members.size() < 32) {
               c.members.put(e.getUUID("id"), HarvestRecord.clip(e.getString("name"), 32));
               if (e.contains("respawn_at")) {
                  c.respawn.put(e.getUUID("id"), e.getLong("respawn_at"));
               }
            }
         }
         for (Tag x : t.getList("invites", 10)) {
            CompoundTag e = (CompoundTag)x;
            if (e.hasUUID("id")) {
               c.invites.put(e.getUUID("id"), e.getLong("until"));
            }
         }
         c.tier = Math.clamp(t.getInt("tier"), 0, 4);
         c.color = Math.floorMod(t.getInt("color"), 16);
         if (t.contains("post")) {
            c.post = BlockPos.of(t.getLong("post"));
            c.postDim = HarvestRecord.clip(t.getString("post_dim"), 96);
         }
         c.founded = t.getLong("founded");
         c.harvests = Math.max(0, t.getInt("harvests"));
         c.best = HarvestRecord.clip(t.getString("best"), 96);
         c.bestScore = HarvestRecord.finite(t.getDouble("best_score"), 0.0, 5000.0);
         for (Tag x : t.getList("log", 8)) {
            if (c.log.size() < 24) {
               c.log.addLast(HarvestRecord.clip(x.getAsString(), 160));
            }
         }
         return c;
      }
   }
}
