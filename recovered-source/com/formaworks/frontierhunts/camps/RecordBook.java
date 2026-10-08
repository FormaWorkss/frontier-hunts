package com.formaworks.frontierhunts.camps;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The reserve record book (SavedData "frontierhunts_records", overworld).
 * Every recorded harvest of the current reserve season is kept (capped, the least notable entries are trimmed first);
 * all-time top lists are maintained incrementally; finished seasons are archived with their champions and top tens.
 */
public final class RecordBook extends SavedData {
   public static final int SCHEMA = 1;
   static final int CURRENT_CAP = 2500;
   static final int ALL_TIME_CAP = 20;
   static final int ARCHIVE_TOP = 10;
   static final int ARCHIVE_CAP = 24;
   public int season = 1;
   public long nextId = 1L;
   public final List<HarvestRecord> current = new ArrayList<>();
   public final Map<RecordBook.Category, List<HarvestRecord>> allTime = new EnumMap<>(RecordBook.Category.class);
   public final Map<UUID, Integer> seasonCounts = new HashMap<>();
   public final Map<UUID, Integer> allTimeCounts = new HashMap<>();
   public final Map<UUID, String> names = new HashMap<>();
   public final List<RecordBook.Archive> archive = new ArrayList<>();
   private final Set<UUID> animals = new HashSet<>();
   private final Map<RecordBook.Category, List<HarvestRecord>> seasonCache = new EnumMap<>(RecordBook.Category.class);

   public static RecordBook get(MinecraftServer server) {
      return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(RecordBook::new, RecordBook::load, null), "frontierhunts_records");
   }

   public boolean seen(UUID animal) {
      return animal != null && this.animals.contains(animal);
   }

   /** Adds a harvest; returns its season rank in each category it entered (1-based), for announcements. */
   public Map<RecordBook.Category, Integer> add(HarvestRecord r) {
      r.id = this.nextId++;
      this.current.add(r);
      if (r.animal != null) {
         this.animals.add(r.animal);
      }
      if (r.hunter != null) {
         this.names.put(r.hunter, r.name);
         this.seasonCounts.merge(r.hunter, 1, Integer::sum);
         this.allTimeCounts.merge(r.hunter, 1, Integer::sum);
      }
      this.seasonCache.clear();
      Map<RecordBook.Category, Integer> ranks = new LinkedHashMap<>();
      for (RecordBook.Category c : RecordBook.Category.values()) {
         if (c == RecordBook.Category.HARVESTS || !c.accepts(r)) {
            continue;
         }
         List<HarvestRecord> all = this.allTime.computeIfAbsent(c, k -> new ArrayList<>());
         all.add(r);
         all.sort(c.order());
         while (all.size() > ALL_TIME_CAP) {
            all.removeLast();
         }
         List<HarvestRecord> top = this.season(c);
         int idx = top.indexOf(r);
         if (idx >= 0) {
            ranks.put(c, idx + 1);
         }
      }
      this.trim();
      this.setDirty();
      return ranks;
   }

   public int allTimeRank(RecordBook.Category c, HarvestRecord r) {
      List<HarvestRecord> all = this.allTime.getOrDefault(c, List.of());
      return all.indexOf(r) + 1;
   }

   /** Season ranking for a category (cached until the book changes). */
   public List<HarvestRecord> season(RecordBook.Category c) {
      return this.seasonCache.computeIfAbsent(c, k -> this.current.stream().filter(k::accepts).sorted(k.order()).toList());
   }

   public List<HarvestRecord> top(RecordBook.Category c, boolean allTime, int n) {
      List<HarvestRecord> src = allTime ? this.allTime.getOrDefault(c, List.of()) : this.season(c);
      return src.subList(0, Math.min(n, src.size()));
   }

   public List<Map.Entry<UUID, Integer>> counts(boolean allTime, int n) {
      Map<UUID, Integer> m = allTime ? this.allTimeCounts : this.seasonCounts;
      return m.entrySet()
         .stream()
         .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed().thenComparing(e -> this.names.getOrDefault(e.getKey(), "")))
         .limit(n)
         .toList();
   }

   public List<HarvestRecord> recent(int n, Predicate<HarvestRecord> filter) {
      List<HarvestRecord> out = new ArrayList<>();
      for (int i = this.current.size() - 1; i >= 0 && out.size() < n; i--) {
         if (filter.test(this.current.get(i))) {
            out.add(this.current.get(i));
         }
      }
      return out;
   }

   public HarvestRecord byId(long id) {
      for (HarvestRecord r : this.current) {
         if (r.id == id) {
            return r;
         }
      }
      for (List<HarvestRecord> l : this.allTime.values()) {
         for (HarvestRecord r : l) {
            if (r.id == id) {
               return r;
            }
         }
      }
      return null;
   }

   /** Admin removal of a bad entry (e.g. a test kill). */
   public boolean remove(long id) {
      boolean hit = false;
      for (int i = this.current.size() - 1; i >= 0; i--) {
         HarvestRecord r = this.current.get(i);
         if (r.id == id) {
            this.current.remove(i);
            if (r.hunter != null) {
               this.seasonCounts.computeIfPresent(r.hunter, (k, v) -> v > 1 ? v - 1 : null);
               this.allTimeCounts.computeIfPresent(r.hunter, (k, v) -> v > 1 ? v - 1 : null);
            }
            hit = true;
         }
      }
      for (List<HarvestRecord> l : this.allTime.values()) {
         hit |= l.removeIf(r -> r.id == id);
      }
      if (hit) {
         this.seasonCache.clear();
         this.setDirty();
      }
      return hit;
   }

   /** Closes the current season: stores champions and top tens, then clears the season lists. */
   public RecordBook.Archive archiveSeason() {
      RecordBook.Archive a = new RecordBook.Archive();
      a.season = this.season;
      for (RecordBook.Category c : RecordBook.Category.values()) {
         if (c == RecordBook.Category.HARVESTS) {
            continue;
         }
         List<HarvestRecord> top = this.top(c, false, ARCHIVE_TOP);
         if (!top.isEmpty()) {
            a.tops.put(c, new ArrayList<>(top));
         }
      }
      for (Map.Entry<UUID, Integer> e : this.counts(false, ARCHIVE_TOP)) {
         a.counts.put(this.names.getOrDefault(e.getKey(), "Hunter"), e.getValue());
      }
      a.harvests = this.current.size();
      this.archive.addFirst(a);
      while (this.archive.size() > ARCHIVE_CAP) {
         this.archive.removeLast();
      }
      this.current.clear();
      this.seasonCounts.clear();
      this.seasonCache.clear();
      this.setDirty();
      return a;
   }

   /** Keep the season list bounded: drop the oldest entries that are not in any season top ten. */
   private void trim() {
      if (this.current.size() <= CURRENT_CAP) {
         return;
      }
      Set<HarvestRecord> keep = new HashSet<>();
      for (RecordBook.Category c : RecordBook.Category.values()) {
         if (c != RecordBook.Category.HARVESTS) {
            keep.addAll(this.top(c, false, ARCHIVE_TOP));
         }
      }
      int i = 0;
      while (this.current.size() > CURRENT_CAP && i < this.current.size()) {
         if (!keep.contains(this.current.get(i))) {
            this.current.remove(i);
         } else {
            i++;
         }
      }
      this.seasonCache.clear();
   }

   @Override
   public CompoundTag save(CompoundTag tag, Provider provider) {
      tag.putInt("schema", SCHEMA);
      tag.putInt("season", this.season);
      tag.putLong("next_id", this.nextId);
      ListTag cur = new ListTag();
      for (HarvestRecord r : this.current) {
         cur.add(r.save(r.quarry().rack() && r.score > 0.0));
      }
      tag.put("current", cur);
      CompoundTag all = new CompoundTag();
      this.allTime.forEach((c, l) -> {
         ListTag lt = new ListTag();
         l.forEach(r -> lt.add(r.save(c.rack)));
         all.put(c.name(), lt);
      });
      tag.put("all_time", all);
      tag.put("season_counts", counts(this.seasonCounts));
      tag.put("all_counts", counts(this.allTimeCounts));
      ListTag nm = new ListTag();
      this.names.forEach((k, v) -> {
         CompoundTag e = new CompoundTag();
         e.putUUID("id", k);
         e.putString("name", v);
         nm.add(e);
      });
      tag.put("names", nm);
      ListTag ar = new ListTag();
      this.archive.forEach(a -> ar.add(a.save()));
      tag.put("archive", ar);
      return tag;
   }

   private static ListTag counts(Map<UUID, Integer> m) {
      ListTag l = new ListTag();
      m.forEach((k, v) -> {
         CompoundTag e = new CompoundTag();
         e.putUUID("id", k);
         e.putInt("n", v);
         l.add(e);
      });
      return l;
   }

   public static RecordBook load(CompoundTag tag, Provider provider) {
      RecordBook b = new RecordBook();
      b.season = Math.max(1, tag.getInt("season"));
      b.nextId = Math.max(1L, tag.getLong("next_id"));
      for (Tag t : tag.getList("current", 10)) {
         HarvestRecord r = HarvestRecord.load((CompoundTag)t);
         b.current.add(r);
         if (r.animal != null) {
            b.animals.add(r.animal);
         }
         b.nextId = Math.max(b.nextId, r.id + 1L);
      }
      CompoundTag all = tag.getCompound("all_time");
      Map<Long, HarvestRecord> byId = new HashMap<>();
      b.current.forEach(r -> byId.put(r.id, r));
      for (RecordBook.Category c : RecordBook.Category.values()) {
         if (all.contains(c.name(), 9)) {
            List<HarvestRecord> l = new ArrayList<>();
            for (Tag t : all.getList(c.name(), 10)) {
               HarvestRecord r = HarvestRecord.load((CompoundTag)t);
               // share the instance with the season list so rank lookups by identity work
               HarvestRecord same = byId.get(r.id);
               l.add(same != null ? same : r);
               b.nextId = Math.max(b.nextId, r.id + 1L);
            }
            l.sort(c.order());
            b.allTime.put(c, l);
         }
      }
      readCounts(tag.getList("season_counts", 10), b.seasonCounts);
      readCounts(tag.getList("all_counts", 10), b.allTimeCounts);
      for (Tag t : tag.getList("names", 10)) {
         CompoundTag e = (CompoundTag)t;
         if (e.hasUUID("id")) {
            b.names.put(e.getUUID("id"), HarvestRecord.clip(e.getString("name"), 32));
         }
      }
      for (Tag t : tag.getList("archive", 10)) {
         if (b.archive.size() < ARCHIVE_CAP) {
            b.archive.add(RecordBook.Archive.load((CompoundTag)t));
         }
      }
      return b;
   }

   private static void readCounts(ListTag l, Map<UUID, Integer> into) {
      for (Tag t : l) {
         CompoundTag e = (CompoundTag)t;
         if (e.hasUUID("id")) {
            into.put(e.getUUID("id"), Math.max(0, e.getInt("n")));
         }
      }
   }

   public static final class Archive {
      public int season;
      public int harvests;
      public final Map<RecordBook.Category, List<HarvestRecord>> tops = new EnumMap<>(RecordBook.Category.class);
      public final Map<String, Integer> counts = new LinkedHashMap<>();

      CompoundTag save() {
         CompoundTag t = new CompoundTag();
         t.putInt("season", this.season);
         t.putInt("harvests", this.harvests);
         CompoundTag tops = new CompoundTag();
         this.tops.forEach((c, l) -> {
            ListTag lt = new ListTag();
            for (int i = 0; i < l.size(); i++) {
               lt.add(l.get(i).save(c.rack && i < 5));
            }
            tops.put(c.name(), lt);
         });
         t.put("tops", tops);
         ListTag cl = new ListTag();
         this.counts.forEach((k, v) -> {
            CompoundTag e = new CompoundTag();
            e.putString("name", k);
            e.putInt("n", v);
            cl.add(e);
         });
         t.put("counts", cl);
         return t;
      }

      static RecordBook.Archive load(CompoundTag t) {
         RecordBook.Archive a = new RecordBook.Archive();
         a.season = t.getInt("season");
         a.harvests = t.getInt("harvests");
         CompoundTag tops = t.getCompound("tops");
         for (RecordBook.Category c : RecordBook.Category.values()) {
            if (tops.contains(c.name(), 9)) {
               List<HarvestRecord> l = new ArrayList<>();
               for (Tag x : tops.getList(c.name(), 10)) {
                  l.add(HarvestRecord.load((CompoundTag)x));
               }
               a.tops.put(c, l);
            }
         }
         for (Tag x : t.getList("counts", 10)) {
            CompoundTag e = (CompoundTag)x;
            a.counts.put(HarvestRecord.clip(e.getString("name"), 32), e.getInt("n"));
         }
         return a;
      }
   }

   public enum Category {
      WHITETAIL("Whitetail", "Top whitetail racks", true),
      ELK("Elk", "Top bull elk", true),
      MOOSE("Moose", "Top bull moose", true),
      BEAR("Bears", "Heaviest bears", false),
      BOAR("Boar", "Heaviest wild boar", false),
      BISON("Bison", "Heaviest bison", false),
      LONGEST("Long shots", "Longest recovered shots", false),
      HARVESTS("Harvests", "Most harvests", false);

      public final String tab;
      public final String title;
      public final boolean rack;

      Category(String tab, String title, boolean rack) {
         this.tab = tab;
         this.title = title;
         this.rack = rack;
      }

      public boolean accepts(HarvestRecord r) {
         Quarry q = r.quarry();
         return switch (this) {
            case WHITETAIL -> q == Quarry.WHITETAIL && r.score > 0.0;
            case ELK -> q == Quarry.ELK && r.score > 0.0;
            case MOOSE -> q == Quarry.MOOSE && r.score > 0.0;
            case BEAR -> q.kind == Quarry.Kind.BEAR && r.weightKg > 0.0;
            case BOAR -> q == Quarry.BOAR && r.weightKg > 0.0;
            case BISON -> q == Quarry.BISON && r.weightKg > 0.0;
            case LONGEST -> r.shotM >= 1.0;
            case HARVESTS -> false;
         };
      }

      public double value(HarvestRecord r) {
         return switch (this) {
            case WHITETAIL, ELK, MOOSE -> r.score;
            case BEAR, BOAR, BISON -> r.weightKg;
            case LONGEST -> r.shotM;
            case HARVESTS -> 0.0;
         };
      }

      public String valueText(HarvestRecord r) {
         return switch (this) {
            case WHITETAIL, ELK, MOOSE -> r.scoreText();
            case BEAR, BOAR, BISON -> Fmt.kg(r.weightKg);
            case LONGEST -> Fmt.metres(r.shotM);
            case HARVESTS -> "";
         };
      }

      public Comparator<HarvestRecord> order() {
         return Comparator.<HarvestRecord>comparingDouble(this::value).reversed().thenComparingLong(r -> r.epochMs).thenComparingLong(r -> r.id);
      }

      public static RecordBook.Category find(String s) {
         for (RecordBook.Category c : values()) {
            if (c.name().equalsIgnoreCase(s) || c.tab.equalsIgnoreCase(s)) {
               return c;
            }
         }
         return null;
      }

      /** Category an animal's harvest is announced/boarded under (null = none). */
      public static RecordBook.Category of(HarvestRecord r) {
         for (RecordBook.Category c : values()) {
            if (c.rack || c == BEAR || c == BOAR || c == BISON) {
               if (c.accepts(r)) {
                  return c;
               }
            }
         }
         return null;
      }
   }
}
