package com.formaworks.frontierhunts.phone;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * [phone] What the Field Phone keeps on the server, per world: each hunter's map pins, game statistics and best
 * scores, solved chess puzzles and text messages, plus the Flush! leaderboard. Bounded everywhere (pins, messages per
 * hunter, leaderboard rows) so a busy server never grows it without end.
 */
public final class PhoneStore extends SavedData {
   public static final int MAX_PINS = 32;
   public static final int MAX_MESSAGES = 300;
   public static final int MAX_PER_THREAD = 60;
   public static final int BOARD = 10;
   private static final String NAME = "frontierhunts_phone";

   public final Map<UUID, Hunter> hunters = new HashMap<>();
   public final List<Score> flushBoard = new ArrayList<>();

   public static final class Pin {
      public int id;
      public String name = "";
      public int icon;
      public int x, y, z;
      public String dim = "minecraft:overworld";
   }

   public static final class Message {
      public UUID peer;
      public String peerName = "";
      public boolean mine;
      public String text = "";
      /** real time (epoch ms) */
      public long at;
      public boolean read;
   }

   public static final class Hunter {
      public String name = "";
      public final List<Pin> pins = new ArrayList<>();
      public int nextPin = 1;
      public final int[] stats = new int[18];
      public int flushBest;
      public int diceBest;
      public long puzzles;
      public final List<Message> messages = new ArrayList<>();
      /** where the hunter last took an animal (BlockPos.asLong, dimension, what), for the Maps app */
      public long killPos;
      public String killDim = "";
      public String killWhat = "";
   }

   public record Score(UUID id, String name, int score, long at) {
   }

   public static PhoneStore get(MinecraftServer server) {
      return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(PhoneStore::new, PhoneStore::load, null), NAME);
   }

   public Hunter hunter(UUID id, String name) {
      Hunter h = this.hunters.computeIfAbsent(id, k -> new Hunter());
      if (name != null && !name.isEmpty() && !name.equals(h.name)) {
         h.name = name;
         this.setDirty();
      }
      return h;
   }

   /** A hunter who has used a Field Phone on this world, by name (case-insensitive), or null. */
   public UUID byName(String name) {
      for (Map.Entry<UUID, Hunter> e : this.hunters.entrySet()) {
         if (e.getValue().name.equalsIgnoreCase(name)) {
            return e.getKey();
         }
      }
      return null;
   }

   /** Records a Flush! score; true when it made the leaderboard. */
   public boolean flushScore(UUID id, String name, int score, long at) {
      Hunter h = this.hunter(id, name);
      if (score > h.flushBest) {
         h.flushBest = score;
      }
      boolean made = false;
      int mine = -1;
      for (int i = 0; i < this.flushBoard.size(); i++) {
         if (this.flushBoard.get(i).id().equals(id)) {
            mine = i;
         }
      }
      if (mine >= 0) {
         if (this.flushBoard.get(mine).score() < score) {
            this.flushBoard.set(mine, new Score(id, name, score, at));
            made = true;
         }
      } else {
         this.flushBoard.add(new Score(id, name, score, at));
         made = true;
      }
      this.flushBoard.sort((a, b) -> Integer.compare(b.score(), a.score()));
      while (this.flushBoard.size() > BOARD) {
         Score gone = this.flushBoard.remove(this.flushBoard.size() - 1);
         if (gone.id().equals(id)) {
            made = false;
         }
      }
      this.setDirty();
      return made;
   }

   public void addMessage(Hunter h, Message m) {
      h.messages.add(m);
      int inThread = 0;
      for (Message x : h.messages) {
         if (x.peer.equals(m.peer)) {
            inThread++;
         }
      }
      if (inThread > MAX_PER_THREAD) {
         for (int i = 0; i < h.messages.size(); i++) {
            if (h.messages.get(i).peer.equals(m.peer)) {
               h.messages.remove(i);
               break;
            }
         }
      }
      while (h.messages.size() > MAX_MESSAGES) {
         h.messages.remove(0);
      }
      this.setDirty();
   }

   // ------------------------------------------------------------------------------------------------ saving

   @Override
   public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
      ListTag list = new ListTag();
      for (Map.Entry<UUID, Hunter> e : this.hunters.entrySet()) {
         Hunter h = e.getValue();
         CompoundTag t = new CompoundTag();
         t.putUUID("id", e.getKey());
         t.putString("name", h.name);
         t.putInt("next_pin", h.nextPin);
         t.putIntArray("stats", h.stats);
         t.putInt("flush", h.flushBest);
         t.putInt("dice", h.diceBest);
         t.putLong("puzzles", h.puzzles);
         if (!h.killDim.isEmpty()) {
            t.putLong("kill_pos", h.killPos);
            t.putString("kill_dim", h.killDim);
            t.putString("kill_what", h.killWhat);
         }
         ListTag pins = new ListTag();
         for (Pin p : h.pins) {
            CompoundTag pt = new CompoundTag();
            pt.putInt("id", p.id);
            pt.putString("name", p.name);
            pt.putInt("icon", p.icon);
            pt.putInt("x", p.x);
            pt.putInt("y", p.y);
            pt.putInt("z", p.z);
            pt.putString("dim", p.dim);
            pins.add(pt);
         }
         t.put("pins", pins);
         ListTag msgs = new ListTag();
         for (Message m : h.messages) {
            CompoundTag mt = new CompoundTag();
            mt.putUUID("peer", m.peer);
            mt.putString("peer_name", m.peerName);
            mt.putBoolean("mine", m.mine);
            mt.putString("text", m.text);
            mt.putLong("at", m.at);
            mt.putBoolean("read", m.read);
            msgs.add(mt);
         }
         t.put("messages", msgs);
         list.add(t);
      }
      tag.put("hunters", list);
      ListTag board = new ListTag();
      for (Score s : this.flushBoard) {
         CompoundTag t = new CompoundTag();
         t.putUUID("id", s.id());
         t.putString("name", s.name());
         t.putInt("score", s.score());
         t.putLong("at", s.at());
         board.add(t);
      }
      tag.put("flush_board", board);
      return tag;
   }

   public static PhoneStore load(CompoundTag tag, HolderLookup.Provider registries) {
      PhoneStore s = new PhoneStore();
      ListTag list = tag.getList("hunters", Tag.TAG_COMPOUND);
      for (int i = 0; i < list.size(); i++) {
         CompoundTag t = list.getCompound(i);
         if (!t.hasUUID("id")) {
            continue;
         }
         Hunter h = new Hunter();
         h.name = t.getString("name");
         h.nextPin = Math.max(1, t.getInt("next_pin"));
         int[] st = t.getIntArray("stats");
         System.arraycopy(st, 0, h.stats, 0, Math.min(st.length, h.stats.length));
         h.flushBest = Math.max(0, t.getInt("flush"));
         h.diceBest = Math.max(0, t.getInt("dice"));
         h.puzzles = t.getLong("puzzles");
         h.killPos = t.getLong("kill_pos");
         h.killDim = t.getString("kill_dim");
         h.killWhat = t.getString("kill_what");
         ListTag pins = t.getList("pins", Tag.TAG_COMPOUND);
         for (int j = 0; j < pins.size() && h.pins.size() < MAX_PINS; j++) {
            CompoundTag pt = pins.getCompound(j);
            Pin p = new Pin();
            p.id = pt.getInt("id");
            p.name = pt.getString("name");
            p.icon = pt.getInt("icon");
            p.x = pt.getInt("x");
            p.y = pt.getInt("y");
            p.z = pt.getInt("z");
            p.dim = pt.getString("dim");
            h.pins.add(p);
         }
         ListTag msgs = t.getList("messages", Tag.TAG_COMPOUND);
         for (int j = 0; j < msgs.size() && h.messages.size() < MAX_MESSAGES; j++) {
            CompoundTag mt = msgs.getCompound(j);
            if (!mt.hasUUID("peer")) {
               continue;
            }
            Message m = new Message();
            m.peer = mt.getUUID("peer");
            m.peerName = mt.getString("peer_name");
            m.mine = mt.getBoolean("mine");
            m.text = mt.getString("text");
            m.at = mt.getLong("at");
            m.read = mt.getBoolean("read");
            h.messages.add(m);
         }
         s.hunters.put(t.getUUID("id"), h);
      }
      ListTag board = tag.getList("flush_board", Tag.TAG_COMPOUND);
      for (int i = 0; i < board.size() && s.flushBoard.size() < BOARD; i++) {
         CompoundTag t = board.getCompound(i);
         if (t.hasUUID("id")) {
            s.flushBoard.add(new Score(t.getUUID("id"), t.getString("name"), t.getInt("score"), t.getLong("at")));
         }
      }
      return s;
   }
}
