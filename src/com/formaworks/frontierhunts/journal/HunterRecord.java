package com.formaworks.frontierhunts.journal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * [journal] One hunter's journal record: counters, skill XP, checklist completions, species log, field notes, season
 * summaries and (server only) pending wounds and the season-start snapshot. Saved in {@link ProgressStore}; a compact
 * copy (no server-only parts) is synced to the owner when the journal is open.
 */
public final class HunterRecord {
   public static final int MAX_NOTES = 48;
   public static final int MAX_SUMMARIES = 8;
   public static final int MAX_WOUNDS = 24;
   public static final int MAX_COUNTERS = 512;
   public static final int MAX_SPECIES = 64;

   public final Map<String, Integer> counters = new HashMap<>();
   public final int[] skillXp = new int[Skill.count()];
   public int bonusXp;
   public final Set<String> done = new HashSet<>();
   public final Map<String, SpeciesLog> species = new LinkedHashMap<>();
   public final ArrayDeque<Note> notes = new ArrayDeque<>();
   public final ArrayDeque<Summary> summaries = new ArrayDeque<>();
   // server only
   public final Map<UUID, Wound> wounds = new LinkedHashMap<>();
   public int seasonKey = -1;
   public final Map<String, Integer> seasonStart = new HashMap<>();
   public int rankShown;
   public int perksShown;
   public long blizzardDay = -1L;
   /** set when anything changed since the last sync to the owner */
   public transient boolean changed = true;

   public static final class SpeciesLog {
      public int seen;
      public int photos;
      public int harvested;
      public int bestScore;
      /** kg x 10 */
      public int bestWeight;
      /** metres x 10 */
      public int longestShot;
      public String firstDate = "";

      CompoundTag save() {
         CompoundTag t = new CompoundTag();
         t.putInt("seen", this.seen);
         t.putInt("photos", this.photos);
         t.putInt("h", this.harvested);
         t.putInt("score", this.bestScore);
         t.putInt("kg10", this.bestWeight);
         t.putInt("shot10", this.longestShot);
         t.putString("first", this.firstDate);
         return t;
      }

      static SpeciesLog load(CompoundTag t) {
         SpeciesLog s = new SpeciesLog();
         s.seen = Math.max(0, t.getInt("seen"));
         s.photos = Math.max(0, t.getInt("photos"));
         s.harvested = Math.max(0, t.getInt("h"));
         s.bestScore = Math.max(0, t.getInt("score"));
         s.bestWeight = Math.max(0, t.getInt("kg10"));
         s.longestShot = Math.max(0, t.getInt("shot10"));
         s.firstDate = clip(t.getString("first"), 40);
         return s;
      }
   }

   /** kind: 0 harvest, 1 checklist, 2 rank, 3 skill/perk, 4 discovery, 5 season, 6 other */
   public record Note(String stamp, String text, byte kind) {
   }

   public record Summary(String title, List<String> lines) {
   }

   /** A wounded animal this hunter hit that ran: where it was hit, when, and whether it was found. */
   public static final class Wound {
      public double x;
      public double y;
      public double z;
      public long time;
      public String species = "";
   }

   // ------------------------------------------------------------------------------------------------ access

   public int get(String key) {
      Integer v = this.counters.get(key);
      return v == null ? 0 : v;
   }

   public long totalXp() {
      long t = this.bonusXp;
      for (int x : this.skillXp) {
         t += x;
      }
      return t;
   }

   public int[] levels() {
      int[] l = new int[this.skillXp.length];
      for (int i = 0; i < l.length; i++) {
         l[i] = Skill.level(this.skillXp[i]);
      }
      return l;
   }

   public SpeciesLog species(String id) {
      return this.species.computeIfAbsent(id, k -> new SpeciesLog());
   }

   public void note(String stamp, String text, byte kind) {
      this.notes.addFirst(new Note(clip(stamp, 40), clip(text, 240), kind));
      while (this.notes.size() > MAX_NOTES) {
         this.notes.removeLast();
      }
      this.changed = true;
   }

   static String clip(String s, int n) {
      if (s == null) {
         return "";
      }
      return s.length() > n ? s.substring(0, n) : s;
   }

   // ------------------------------------------------------------------------------------------------ NBT

   public CompoundTag save() {
      CompoundTag t = new CompoundTag();
      CompoundTag c = new CompoundTag();
      this.counters.forEach(c::putInt);
      t.put("counters", c);
      t.putIntArray("skills", this.skillXp);
      t.putInt("bonus", this.bonusXp);
      ListTag d = new ListTag();
      this.done.stream().sorted().forEach(s -> d.add(StringTag.valueOf(s)));
      t.put("done", d);
      CompoundTag sp = new CompoundTag();
      this.species.forEach((k, v) -> sp.put(k, v.save()));
      t.put("species", sp);
      ListTag n = new ListTag();
      for (Note note : this.notes) {
         CompoundTag nt = new CompoundTag();
         nt.putString("s", note.stamp());
         nt.putString("t", note.text());
         nt.putByte("k", note.kind());
         n.add(nt);
      }
      t.put("notes", n);
      ListTag su = new ListTag();
      for (Summary s : this.summaries) {
         CompoundTag st = new CompoundTag();
         st.putString("title", s.title());
         ListTag lines = new ListTag();
         s.lines().forEach(l -> lines.add(StringTag.valueOf(l)));
         st.put("lines", lines);
         su.add(st);
      }
      t.put("summaries", su);
      ListTag w = new ListTag();
      this.wounds.forEach((id, wd) -> {
         CompoundTag wt = new CompoundTag();
         wt.putUUID("id", id);
         wt.putDouble("x", wd.x);
         wt.putDouble("y", wd.y);
         wt.putDouble("z", wd.z);
         wt.putLong("t", wd.time);
         wt.putString("sp", wd.species);
         w.add(wt);
      });
      t.put("wounds", w);
      t.putInt("season_key", this.seasonKey);
      CompoundTag ss = new CompoundTag();
      this.seasonStart.forEach(ss::putInt);
      t.put("season_start", ss);
      t.putInt("rank_shown", this.rankShown);
      t.putInt("perks_shown", this.perksShown);
      t.putLong("blizzard_day", this.blizzardDay);
      return t;
   }

   public static HunterRecord load(CompoundTag t) {
      HunterRecord r = new HunterRecord();
      CompoundTag c = t.getCompound("counters");
      for (String k : c.getAllKeys()) {
         if (r.counters.size() < MAX_COUNTERS && k.length() <= 64) {
            r.counters.put(k, Math.max(0, c.getInt(k)));
         }
      }
      int[] sk = t.getIntArray("skills");
      for (int i = 0; i < Math.min(sk.length, r.skillXp.length); i++) {
         r.skillXp[i] = Math.clamp(sk[i], 0, 10_000_000);
      }
      r.bonusXp = Math.clamp(t.getInt("bonus"), 0, 100_000_000);
      ListTag d = t.getList("done", Tag.TAG_STRING);
      for (int i = 0; i < d.size(); i++) {
         r.done.add(clip(d.getString(i), 48));
      }
      CompoundTag sp = t.getCompound("species");
      for (String k : sp.getAllKeys()) {
         if (r.species.size() < MAX_SPECIES) {
            r.species.put(clip(k, 32), SpeciesLog.load(sp.getCompound(k)));
         }
      }
      ListTag n = t.getList("notes", Tag.TAG_COMPOUND);
      for (int i = 0; i < Math.min(n.size(), MAX_NOTES); i++) {
         CompoundTag nt = n.getCompound(i);
         r.notes.addLast(new Note(clip(nt.getString("s"), 40), clip(nt.getString("t"), 240), nt.getByte("k")));
      }
      ListTag su = t.getList("summaries", Tag.TAG_COMPOUND);
      for (int i = 0; i < Math.min(su.size(), MAX_SUMMARIES); i++) {
         CompoundTag st = su.getCompound(i);
         ListTag lines = st.getList("lines", Tag.TAG_STRING);
         List<String> l = new ArrayList<>();
         for (int j = 0; j < Math.min(24, lines.size()); j++) {
            l.add(clip(lines.getString(j), 120));
         }
         r.summaries.addLast(new Summary(clip(st.getString("title"), 48), List.copyOf(l)));
      }
      ListTag w = t.getList("wounds", Tag.TAG_COMPOUND);
      for (int i = 0; i < Math.min(w.size(), MAX_WOUNDS); i++) {
         CompoundTag wt = w.getCompound(i);
         if (!wt.hasUUID("id")) {
            continue;
         }
         Wound wd = new Wound();
         wd.x = wt.getDouble("x");
         wd.y = wt.getDouble("y");
         wd.z = wt.getDouble("z");
         wd.time = wt.getLong("t");
         wd.species = clip(wt.getString("sp"), 32);
         if (Double.isFinite(wd.x + wd.y + wd.z)) {
            r.wounds.put(wt.getUUID("id"), wd);
         }
      }
      r.seasonKey = t.contains("season_key") ? t.getInt("season_key") : -1;
      CompoundTag ss = t.getCompound("season_start");
      for (String k : ss.getAllKeys()) {
         if (r.seasonStart.size() < 64) {
            r.seasonStart.put(k, ss.getInt(k));
         }
      }
      r.rankShown = Math.clamp(t.getInt("rank_shown"), 0, Rank.values().length - 1);
      r.perksShown = t.getInt("perks_shown");
      r.blizzardDay = t.contains("blizzard_day") ? t.getLong("blizzard_day") : -1L;
      return r;
   }

   // ------------------------------------------------------------------------------------------------ sync (owner only)

   public void writeSync(FriendlyByteBuf b) {
      for (int x : this.skillXp) {
         b.writeVarInt(x);
      }
      b.writeVarInt(this.bonusXp);
      int n = Math.min(MAX_COUNTERS, this.counters.size());
      b.writeVarInt(n);
      Iterator<Map.Entry<String, Integer>> it = this.counters.entrySet().iterator();
      for (int i = 0; i < n; i++) {
         Map.Entry<String, Integer> e = it.next();
         b.writeUtf(e.getKey(), 64);
         b.writeVarInt(Math.max(0, e.getValue()));
      }
      int dn = Math.min(1024, this.done.size());
      b.writeVarInt(dn);
      Iterator<String> di = this.done.iterator();
      for (int i = 0; i < dn; i++) {
         b.writeUtf(di.next(), 48);
      }
      int sn = Math.min(MAX_SPECIES, this.species.size());
      b.writeVarInt(sn);
      Iterator<Map.Entry<String, SpeciesLog>> si = this.species.entrySet().iterator();
      for (int i = 0; i < sn; i++) {
         Map.Entry<String, SpeciesLog> e = si.next();
         SpeciesLog s = e.getValue();
         b.writeUtf(e.getKey(), 32);
         b.writeVarInt(s.seen);
         b.writeVarInt(s.photos);
         b.writeVarInt(s.harvested);
         b.writeVarInt(s.bestScore);
         b.writeVarInt(s.bestWeight);
         b.writeVarInt(s.longestShot);
         b.writeUtf(s.firstDate, 40);
      }
      b.writeVarInt(this.notes.size());
      for (Note note : this.notes) {
         b.writeUtf(note.stamp(), 40);
         b.writeUtf(note.text(), 240);
         b.writeByte(note.kind());
      }
      b.writeVarInt(this.summaries.size());
      for (Summary s : this.summaries) {
         b.writeUtf(s.title(), 48);
         b.writeVarInt(s.lines().size());
         for (String l : s.lines()) {
            b.writeUtf(l, 120);
         }
      }
   }

   public static HunterRecord readSync(FriendlyByteBuf b) {
      HunterRecord r = new HunterRecord();
      for (int i = 0; i < r.skillXp.length; i++) {
         r.skillXp[i] = Math.clamp(b.readVarInt(), 0, 10_000_000);
      }
      r.bonusXp = Math.clamp(b.readVarInt(), 0, 100_000_000);
      int n = b.readVarInt();
      check(n, MAX_COUNTERS);
      for (int i = 0; i < n; i++) {
         r.counters.put(b.readUtf(64), Math.max(0, b.readVarInt()));
      }
      int dn = b.readVarInt();
      check(dn, 1024);
      for (int i = 0; i < dn; i++) {
         r.done.add(b.readUtf(48));
      }
      int sn = b.readVarInt();
      check(sn, MAX_SPECIES);
      for (int i = 0; i < sn; i++) {
         String id = b.readUtf(32);
         SpeciesLog s = new SpeciesLog();
         s.seen = b.readVarInt();
         s.photos = b.readVarInt();
         s.harvested = b.readVarInt();
         s.bestScore = b.readVarInt();
         s.bestWeight = b.readVarInt();
         s.longestShot = b.readVarInt();
         s.firstDate = b.readUtf(40);
         r.species.put(id, s);
      }
      int nn = b.readVarInt();
      check(nn, MAX_NOTES);
      for (int i = 0; i < nn; i++) {
         r.notes.addLast(new Note(b.readUtf(40), b.readUtf(240), b.readByte()));
      }
      int su = b.readVarInt();
      check(su, MAX_SUMMARIES);
      for (int i = 0; i < su; i++) {
         String title = b.readUtf(48);
         int ln = b.readVarInt();
         check(ln, 24);
         List<String> lines = new ArrayList<>(ln);
         for (int j = 0; j < ln; j++) {
            lines.add(b.readUtf(120));
         }
         r.summaries.addLast(new Summary(title, List.copyOf(lines)));
      }
      return r;
   }

   /**
    * A detached copy of the synced part (what the owner's client gets). Singleplayer passes payload objects to the client
    * without encoding them, so the server must never hand its live record to a payload.
    */
   public HunterRecord syncCopy() {
      FriendlyByteBuf b = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer(2048));
      try {
         this.writeSync(b);
         return readSync(b);
      } finally {
         b.release();
      }
   }

   private static void check(int n, int max) {
      if (n < 0 || n > max) {
         throw new IllegalArgumentException("Invalid journal record size " + n);
      }
   }
}
