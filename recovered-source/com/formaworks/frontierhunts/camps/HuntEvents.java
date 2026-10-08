package com.formaworks.frontierhunts.camps;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Weekend event state (SavedData "frontierhunts_events"): the running event, its leaderboard, history, unclaimed prizes. */
public final class HuntEvents extends SavedData {
   public HuntEvents.Active active;
   /** start epoch-second of the last scheduled window that was run (or stopped by an admin) */
   public long lastWindow;
   public final List<HuntEvents.Past> history = new ArrayList<>();
   public final Map<UUID, List<String>> pendingItems = new HashMap<>();

   public static HuntEvents get(MinecraftServer server) {
      return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(HuntEvents::new, HuntEvents::load, null), "frontierhunts_events");
   }

   @Override
   public CompoundTag save(CompoundTag tag, Provider provider) {
      if (this.active != null) {
         tag.put("active", this.active.save());
      }
      tag.putLong("last_window", this.lastWindow);
      ListTag h = new ListTag();
      this.history.forEach(p -> h.add(p.save()));
      tag.put("history", h);
      ListTag pend = new ListTag();
      this.pendingItems.forEach((k, v) -> {
         CompoundTag e = new CompoundTag();
         e.putUUID("id", k);
         ListTag items = new ListTag();
         v.forEach(s -> items.add(StringTag.valueOf(s)));
         e.put("items", items);
         pend.add(e);
      });
      tag.put("pending", pend);
      return tag;
   }

   public static HuntEvents load(CompoundTag tag, Provider provider) {
      HuntEvents e = new HuntEvents();
      if (tag.contains("active", 10)) {
         e.active = HuntEvents.Active.load(tag.getCompound("active"));
      }
      e.lastWindow = tag.getLong("last_window");
      for (Tag t : tag.getList("history", 10)) {
         HuntEvents.Past p = HuntEvents.Past.load((CompoundTag)t);
         if (p != null && e.history.size() < 12) {
            e.history.add(p);
         }
      }
      for (Tag t : tag.getList("pending", 10)) {
         CompoundTag c = (CompoundTag)t;
         if (c.hasUUID("id")) {
            List<String> items = new ArrayList<>();
            for (Tag s : c.getList("items", 8)) {
               if (items.size() < 16) {
                  items.add(s.getAsString());
               }
            }
            e.pendingItems.put(c.getUUID("id"), items);
         }
      }
      return e;
   }

   public enum Type {
      BIG_BUCK("Big Buck Weekend", "Biggest whitetail rack wins", "The highest-scoring whitetail buck recovered during the weekend takes it. Only your best rack counts."),
      PREDATOR("Predator Weekend", "Coyotes · wolves · cougars", "Points for every predator taken: coyote 1, wolf 2, cougar 3. Most points wins."),
      WATERFOWL("Waterfowl Weekend", "Ducks & grouse", "One point for every duck or grouse taken. Most birds wins; the earlier tally breaks ties."),
      RUT_RALLY("Rut Rally", "Call them in", "One point for every whitetail, elk or moose that answered your call before the shot. Best called-in rack breaks ties.");

      public final String title;
      public final String tagline;
      public final String rules;

      Type(String title, String tagline, String rules) {
         this.title = title;
         this.tagline = tagline;
         this.rules = rules;
      }

      /** Best-single events rank by one harvest; the others add points up. */
      public boolean single() {
         return this == BIG_BUCK;
      }

      /** Points this harvest is worth in the event (0 = not eligible). */
      public double points(HarvestRecord r) {
         Quarry q = r.quarry();
         return switch (this) {
            case BIG_BUCK -> q == Quarry.WHITETAIL && r.score > 0.0 ? r.score : 0.0;
            case PREDATOR -> q == Quarry.COYOTE ? 1.0 : (q == Quarry.WOLF ? 2.0 : (q == Quarry.COUGAR ? 3.0 : 0.0));
            case WATERFOWL -> q == Quarry.DUCK || q == Quarry.GROUSE ? 1.0 : 0.0;
            case RUT_RALLY -> q.rack() && r.called ? 1.0 : 0.0;
         };
      }

      public String valueText(HuntEvents.Entry e) {
         return switch (this) {
            case BIG_BUCK -> Fmt.inches(e.points);
            case PREDATOR -> (int)e.points + " pts · " + e.count + (e.count == 1 ? " predator" : " predators");
            case WATERFOWL -> e.count + (e.count == 1 ? " bird" : " birds");
            case RUT_RALLY -> e.count + " called in" + (e.best > 0.0 ? " · best " + Fmt.inches(e.best) : "");
         };
      }

      public static HuntEvents.Type find(String s) {
         if (s == null) {
            return null;
         }
         String u = s.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
         for (HuntEvents.Type t : values()) {
            if (t.name().equals(u) || t.name().replace("_", "").equals(u.replace("_", ""))) {
               return t;
            }
         }
         return null;
      }
   }

   public static final class Entry {
      public UUID id;
      public String name = "Hunter";
      public double points;
      public double best;
      public int count;
      public long last;
   }

   public static final class Active {
      public HuntEvents.Type type;
      public long startMs;
      public long endMs;
      public boolean forced;
      public long window;
      public UUID leader;
      public final Map<UUID, HuntEvents.Entry> entries = new HashMap<>();

      public List<HuntEvents.Entry> ranking() {
         List<HuntEvents.Entry> l = new ArrayList<>(this.entries.values());
         l.removeIf(e -> e.points <= 0.0);
         l.sort(Comparator.<HuntEvents.Entry>comparingDouble(e -> e.points).reversed().thenComparing(Comparator.<HuntEvents.Entry>comparingDouble(e -> e.best).reversed()).thenComparingLong(e -> e.last));
         return l;
      }

      CompoundTag save() {
         CompoundTag t = new CompoundTag();
         t.putString("type", this.type.name());
         t.putLong("start", this.startMs);
         t.putLong("end", this.endMs);
         t.putBoolean("forced", this.forced);
         t.putLong("window", this.window);
         if (this.leader != null) {
            t.putUUID("leader", this.leader);
         }
         ListTag l = new ListTag();
         this.entries.values().forEach(e -> {
            CompoundTag x = new CompoundTag();
            x.putUUID("id", e.id);
            x.putString("name", e.name);
            x.putDouble("points", e.points);
            x.putDouble("best", e.best);
            x.putInt("count", e.count);
            x.putLong("last", e.last);
            l.add(x);
         });
         t.put("entries", l);
         return t;
      }

      static HuntEvents.Active load(CompoundTag t) {
         HuntEvents.Type type = HuntEvents.Type.find(t.getString("type"));
         if (type == null) {
            return null;
         }
         HuntEvents.Active a = new HuntEvents.Active();
         a.type = type;
         a.startMs = t.getLong("start");
         a.endMs = t.getLong("end");
         a.forced = t.getBoolean("forced");
         a.window = t.getLong("window");
         a.leader = t.hasUUID("leader") ? t.getUUID("leader") : null;
         for (Tag x : t.getList("entries", 10)) {
            CompoundTag c = (CompoundTag)x;
            if (c.hasUUID("id")) {
               HuntEvents.Entry e = new HuntEvents.Entry();
               e.id = c.getUUID("id");
               e.name = HarvestRecord.clip(c.getString("name"), 32);
               e.points = HarvestRecord.finite(c.getDouble("points"), 0.0, 100000.0);
               e.best = HarvestRecord.finite(c.getDouble("best"), 0.0, 100000.0);
               e.count = Math.max(0, c.getInt("count"));
               e.last = c.getLong("last");
               a.entries.put(e.id, e);
            }
         }
         return a;
      }
   }

   public static final class Past {
      public HuntEvents.Type type;
      public long startMs;
      public long endMs;
      public final List<String> podium = new ArrayList<>();

      CompoundTag save() {
         CompoundTag t = new CompoundTag();
         t.putString("type", this.type.name());
         t.putLong("start", this.startMs);
         t.putLong("end", this.endMs);
         ListTag l = new ListTag();
         this.podium.forEach(s -> l.add(StringTag.valueOf(s)));
         t.put("podium", l);
         return t;
      }

      static HuntEvents.Past load(CompoundTag t) {
         HuntEvents.Type type = HuntEvents.Type.find(t.getString("type"));
         if (type == null) {
            return null;
         }
         HuntEvents.Past p = new HuntEvents.Past();
         p.type = type;
         p.startMs = t.getLong("start");
         p.endMs = t.getLong("end");
         for (Tag x : t.getList("podium", 8)) {
            if (p.podium.size() < 3) {
               p.podium.add(HarvestRecord.clip(x.getAsString(), 96));
            }
         }
         return p;
      }
   }
}
