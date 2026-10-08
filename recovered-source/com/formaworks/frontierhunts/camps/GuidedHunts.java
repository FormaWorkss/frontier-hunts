package com.formaworks.frontierhunts.camps;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Guided-hunt contracts (SavedData "frontierhunts_guided").
 * Player contracts: a guide offers terms to a client; on acceptance the client's fee is held in escrow here until the
 * hunt succeeds (paid to the guide), expires or is called off (refunded). Outfitter contracts are posted by NPC
 * outfitters on the Contract Board: the hunter pays a booking deposit, and earns the purse plus the deposit back
 * on success.
 */
public final class GuidedHunts extends SavedData {
   public static final long OFFER_TICKS = 6000L;
   public final List<GuidedHunts.Hunt> open = new ArrayList<>();
   public final List<GuidedHunts.Hunt> history = new ArrayList<>();
   /** Outfitter contracts each hunter already booked (key = day * 8 + slot). */
   public final Map<UUID, Set<Long>> booked = new HashMap<>();
   public long nextId = 1L;

   public static GuidedHunts get(MinecraftServer server) {
      return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(GuidedHunts::new, GuidedHunts::load, null), "frontierhunts_guided");
   }

   public GuidedHunts.Hunt activeFor(UUID client) {
      for (GuidedHunts.Hunt h : this.open) {
         if (h.state == GuidedHunts.State.ACTIVE && client.equals(h.client)) {
            return h;
         }
      }
      return null;
   }

   public List<GuidedHunts.Hunt> involving(UUID player) {
      List<GuidedHunts.Hunt> out = new ArrayList<>();
      for (GuidedHunts.Hunt h : this.open) {
         if (player.equals(h.client) || player.equals(h.guide)) {
            out.add(h);
         }
      }
      return out;
   }

   public GuidedHunts.Hunt byId(long id) {
      for (GuidedHunts.Hunt h : this.open) {
         if (h.id == id) {
            return h;
         }
      }
      return null;
   }

   public void close(GuidedHunts.Hunt h, GuidedHunts.State state, String result, long now) {
      h.state = state;
      h.result = result;
      h.finished = now;
      this.open.remove(h);
      this.history.addFirst(h);
      while (this.history.size() > 60) {
         this.history.removeLast();
      }
      this.setDirty();
   }

   public void markBooked(UUID player, long key) {
      Set<Long> s = this.booked.computeIfAbsent(player, k -> new HashSet<>());
      s.add(key);
      // keep only the last few days of bookings
      s.removeIf(k -> k / 8L < key / 8L - 6L);
      this.setDirty();
   }

   public boolean isBooked(UUID player, long key) {
      return this.booked.getOrDefault(player, Set.of()).contains(key);
   }

   // ------------------------------------------------------------------ outfitter board
   private static final String[][] OUTFITTERS = new String[][]{
      {"Hank Delaney", "Delaney Outfitters"},
      {"Rosa Quill", "Quill Creek Guides"},
      {"Tom Barrow", "Barrow Ridge Lodge"},
      {"June Whitlock", "Cedar Fork Outfitting"},
      {"Eli Marchetti", "High Timber Hunts"},
      {"Nell Okafor", "Red Willow Station"}
   };
   private static final String[] CLIENTS = new String[]{
      "a surgeon from Denver", "a retired lineman", "a banker from Chicago", "a client flying in from Atlanta", "two brothers from Ohio",
      "a first-time hunter", "a taxidermist's regular", "a lodge member from Boise"
   };

   /** The three outfitter contracts posted for a reserve day. Deterministic per world seed and day. */
   public static List<GuidedHunts.Hunt> outfitterOffers(long worldSeed, long day) {
      List<GuidedHunts.Hunt> out = new ArrayList<>();
      for (int slot = 0; slot < 3; slot++) {
         long h = mix(worldSeed ^ day * 0x632BE59BD9B4E019L ^ slot * 0x9E3779B97F4A7C15L);
         GuidedHunts.Hunt x = new GuidedHunts.Hunt();
         x.npc = true;
         x.npcKey = day * 8L + slot;
         int roll = (int)Math.floorMod(h, 100L);
         String[] o = OUTFITTERS[(int)Math.floorMod(h >>> 8, (long)OUTFITTERS.length)];
         x.guideName = o[0];
         x.outfitter = o[1];
         String client = CLIENTS[(int)Math.floorMod(h >>> 16, (long)CLIENTS.length)];
         int tierRoll = (int)Math.floorMod(h >>> 24, 4L);
         x.days = 2 + (int)Math.floorMod(h >>> 32, 4L);
         if (slot == 0 || roll < 45) {
            x.species = "whitetail";
            x.minScore = 100 + tierRoll * 12;
            x.story = x.outfitter + " has " + client + " who wants a mature whitetail for the den wall.";
         } else if (roll < 65) {
            x.species = "elk";
            x.minScore = 210 + tierRoll * 20;
            x.story = x.outfitter + " is short a guide: " + client + " wants a bull elk.";
         } else if (roll < 80) {
            x.species = "moose";
            x.minScore = 210 + tierRoll * 20;
            x.story = x.outfitter + " promised " + client + " a bull moose from the reserve.";
         } else if (roll < 92) {
            // [1.1.6] bear and boar are not on the reserve yet: no postings for them
            x.species = "whitetail";
            x.minScore = 130 + tierRoll * 10;
            x.story = x.outfitter + " has " + client + " after a heavy-racked whitetail - a long sit, not a quick hunt.";
         } else {
            x.species = "elk";
            x.minScore = 240 + tierRoll * 20;
            x.story = x.outfitter + "'s best client, " + client + ", will only take a herd bull.";
         }
         x.deposit = 8 + tierRoll * 4;
         x.reward = 40 + tierRoll * 22 + (x.species.equals("whitetail") ? 0 : 20) - (x.days - 2) * 4;
         x.fee = 0;
         out.add(x);
      }
      return out;
   }

   private static long mix(long z) {
      z = (z ^ z >>> 30) * 0xBF58476D1CE4E5B9L;
      z = (z ^ z >>> 27) * 0x94D049BB133111EBL;
      return z ^ z >>> 31;
   }

   @Override
   public CompoundTag save(CompoundTag tag, Provider provider) {
      tag.putLong("next_id", this.nextId);
      ListTag o = new ListTag();
      this.open.forEach(h -> o.add(h.save()));
      tag.put("open", o);
      ListTag hi = new ListTag();
      this.history.forEach(h -> hi.add(h.save()));
      tag.put("history", hi);
      ListTag b = new ListTag();
      this.booked.forEach((k, v) -> {
         CompoundTag e = new CompoundTag();
         e.putUUID("id", k);
         ListTag keys = new ListTag();
         v.forEach(x -> keys.add(LongTag.valueOf(x)));
         e.put("keys", keys);
         b.add(e);
      });
      tag.put("booked", b);
      return tag;
   }

   public static GuidedHunts load(CompoundTag tag, Provider provider) {
      GuidedHunts g = new GuidedHunts();
      g.nextId = Math.max(1L, tag.getLong("next_id"));
      for (Tag t : tag.getList("open", 10)) {
         GuidedHunts.Hunt h = GuidedHunts.Hunt.load((CompoundTag)t);
         if (h != null) {
            g.open.add(h);
            g.nextId = Math.max(g.nextId, h.id + 1L);
         }
      }
      for (Tag t : tag.getList("history", 10)) {
         GuidedHunts.Hunt h = GuidedHunts.Hunt.load((CompoundTag)t);
         if (h != null && g.history.size() < 60) {
            g.history.add(h);
         }
      }
      for (Tag t : tag.getList("booked", 10)) {
         CompoundTag e = (CompoundTag)t;
         if (e.hasUUID("id")) {
            Set<Long> s = new HashSet<>();
            for (Tag k : e.getList("keys", 4)) {
               s.add(((LongTag)k).getAsLong());
            }
            g.booked.put(e.getUUID("id"), s);
         }
      }
      return g;
   }

   public enum State {
      OFFERED,
      ACTIVE,
      SUCCESS,
      FAILED,
      EXPIRED,
      DECLINED,
      CANCELLED
   }

   public static final class Hunt {
      public long id;
      public boolean npc;
      public long npcKey;
      public UUID guide;
      public String guideName = "";
      public String outfitter = "";
      public String story = "";
      public UUID client;
      public String clientName = "";
      public String species = "whitetail";
      public int minScore;
      public int minWeight;
      public int days = 3;
      public int fee;
      public int reward;
      public int deposit;
      public int escrow;
      public GuidedHunts.State state = GuidedHunts.State.OFFERED;
      public long created;
      public long offerUntil;
      public long deadline;
      public long finished;
      public String result = "";

      public Quarry quarry() {
         Quarry q = Quarry.find(this.species);
         return q == null ? Quarry.WHITETAIL : q;
      }

      /** "Whitetail buck ≥ 130\" · ≥ 80 kg" */
      public String terms() {
         Quarry q = this.quarry();
         StringBuilder b = new StringBuilder(q.title);
         if (q.rack()) {
            b.append(' ').append(q.male);
         }
         if (this.minScore > 0) {
            b.append(" ≥ ").append(this.minScore).append('"');
         }
         if (this.minWeight > 0) {
            b.append(this.minScore > 0 ? " · " : " ").append("≥ ").append(this.minWeight).append(" kg");
         }
         return b.toString();
      }

      public boolean qualifies(HarvestRecord r) {
         Quarry q = this.quarry();
         if (r.quarry() != q) {
            return false;
         }
         if (q.rack() && this.minScore > 0 && r.score + 1.0E-6 < this.minScore) {
            return false;
         }
         return this.minWeight <= 0 || r.weightKg + 1.0E-6 >= this.minWeight;
      }

      CompoundTag save() {
         CompoundTag t = new CompoundTag();
         t.putLong("id", this.id);
         t.putBoolean("npc", this.npc);
         t.putLong("npc_key", this.npcKey);
         if (this.guide != null) {
            t.putUUID("guide", this.guide);
         }
         t.putString("guide_name", this.guideName);
         t.putString("outfitter", this.outfitter);
         t.putString("story", this.story);
         if (this.client != null) {
            t.putUUID("client", this.client);
         }
         t.putString("client_name", this.clientName);
         t.putString("species", this.species);
         t.putInt("min_score", this.minScore);
         t.putInt("min_weight", this.minWeight);
         t.putInt("days", this.days);
         t.putInt("fee", this.fee);
         t.putInt("reward", this.reward);
         t.putInt("deposit", this.deposit);
         t.putInt("escrow", this.escrow);
         t.putString("state", this.state.name());
         t.putLong("created", this.created);
         t.putLong("offer_until", this.offerUntil);
         t.putLong("deadline", this.deadline);
         t.putLong("finished", this.finished);
         t.putString("result", this.result);
         return t;
      }

      static GuidedHunts.Hunt load(CompoundTag t) {
         GuidedHunts.Hunt h = new GuidedHunts.Hunt();
         h.id = t.getLong("id");
         h.npc = t.getBoolean("npc");
         h.npcKey = t.getLong("npc_key");
         h.guide = t.hasUUID("guide") ? t.getUUID("guide") : null;
         h.guideName = HarvestRecord.clip(t.getString("guide_name"), 32);
         h.outfitter = HarvestRecord.clip(t.getString("outfitter"), 48);
         h.story = HarvestRecord.clip(t.getString("story"), 200);
         h.client = t.hasUUID("client") ? t.getUUID("client") : null;
         h.clientName = HarvestRecord.clip(t.getString("client_name"), 32);
         h.species = Quarry.find(t.getString("species")) == null ? "whitetail" : Quarry.find(t.getString("species")).id;
         h.minScore = Math.clamp(t.getInt("min_score"), 0, 600);
         h.minWeight = Math.clamp(t.getInt("min_weight"), 0, 2000);
         h.days = Math.clamp(t.getInt("days"), 1, 14);
         h.fee = Math.clamp(t.getInt("fee"), 0, 1000);
         h.reward = Math.clamp(t.getInt("reward"), 0, 1000);
         h.deposit = Math.clamp(t.getInt("deposit"), 0, 1000);
         h.escrow = Math.clamp(t.getInt("escrow"), 0, 1000);
         try {
            h.state = GuidedHunts.State.valueOf(t.getString("state"));
         } catch (Exception e) {
            h.state = GuidedHunts.State.CANCELLED;
         }
         h.created = t.getLong("created");
         h.offerUntil = t.getLong("offer_until");
         h.deadline = t.getLong("deadline");
         h.finished = t.getLong("finished");
         h.result = HarvestRecord.clip(t.getString("result"), 160);
         return h.client == null ? null : h;
      }
   }
}
