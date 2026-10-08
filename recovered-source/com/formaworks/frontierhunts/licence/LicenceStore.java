package com.formaworks.frontierhunts.licence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * [licence] Server record of every hunter's licences: what was issued per licence season (so a lost licence can be
 * replaced and bag limits hold), the daily bird bag, the warden record (strikes, fines, suspension) and the last 40
 * harvests (filled tags and violations) for the journal. SavedData {@code frontierhunts_licences} on the overworld.
 */
public final class LicenceStore extends SavedData {
   public static final int LOG = 40;
   private static final String NAME = "frontierhunts_licences";
   private final Map<UUID, Hunter> hunters = new HashMap<>();

   public static LicenceStore get(MinecraftServer server) {
      return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(LicenceStore::new, LicenceStore::load, null), NAME);
   }

   public Hunter hunter(UUID id) {
      return this.hunters.computeIfAbsent(id, k -> new Hunter());
   }

   public Hunter find(UUID id) {
      return this.hunters.get(id);
   }

   public void reset(UUID id) {
      this.hunters.remove(id);
      this.setDirty();
   }

   /** One hunter's licence season. */
   public static final class Season {
      public boolean licence;
      public final int[] tags = new int[Regulations.TagKind.values().length];
      /** bit = Stamp ordinal */
      public int stamps;
      public int strikes;
      public boolean warned;
      public boolean suspended;
      public int fines;
      public int filled;
      public int violations;

      CompoundTag save() {
         CompoundTag t = new CompoundTag();
         t.putBoolean("lic", this.licence);
         t.putIntArray("tags", this.tags);
         t.putInt("stamps", this.stamps);
         t.putInt("strikes", this.strikes);
         t.putBoolean("warned", this.warned);
         t.putBoolean("susp", this.suspended);
         t.putInt("fines", this.fines);
         t.putInt("filled", this.filled);
         t.putInt("viol", this.violations);
         return t;
      }

      static Season load(CompoundTag t) {
         Season s = new Season();
         s.licence = t.getBoolean("lic");
         int[] a = t.getIntArray("tags");
         for (int i = 0; i < Math.min(a.length, s.tags.length); i++) {
            s.tags[i] = Math.clamp(a[i], 0, 64);
         }
         s.stamps = t.getInt("stamps") & 0xFF;
         s.strikes = Math.clamp(t.getInt("strikes"), 0, 99);
         s.warned = t.getBoolean("warned");
         s.suspended = t.getBoolean("susp");
         s.fines = Math.max(0, t.getInt("fines"));
         s.filled = Math.max(0, t.getInt("filled"));
         s.violations = Math.max(0, t.getInt("viol"));
         return s;
      }
   }

   public static final class Hunter {
      /** licence season -> record; only the current and the previous season are kept */
      final TreeMap<Integer, Season> seasons = new TreeMap<>();
      public long birdDay = -1L;
      public final Map<String, Integer> birdsToday = new HashMap<>();
      public final List<FilledTag> log = new ArrayList<>();
      public int filledTotal;
      public int violationsTotal;
      public int finesTotal;
      /** the free licence for passing the Archery course was handed out */
      public boolean edIssued;
      /** [gear21] warden record: points on the record (one per violation, one wears off per clean licence season) */
      public int record;
      /** licence season of the last violation (for the clean-season wear-off), -1 never */
      public int lastViolation = -1;
      /** licence revoked while the current licence season is below this (a poaching charge), 0 = not revoked */
      public int revokedUntil;
      /** times charged with poaching */
      public int charges;

      /** [gear21] the record after clean seasons wore off, as of licence season {@code period} */
      public int record(int period) {
         if (this.lastViolation >= 0 && period - this.lastViolation > 1) {
            this.record = Math.max(0, this.record - (period - this.lastViolation - 1));
            this.lastViolation = period - 1;
         }
         return this.record;
      }

      public boolean revoked(int period) {
         return period < this.revokedUntil;
      }

      public Season season(int period) {
         Season s = this.seasons.computeIfAbsent(period, k -> new Season());
         while (this.seasons.size() > 2) {
            this.seasons.pollFirstEntry();
         }
         return s;
      }

      public Season peek(int period) {
         return this.seasons.get(period);
      }

      /** Birds of a species already taken on reserve day {@code day}. */
      public int birds(long day, String species) {
         if (this.birdDay != day) {
            return 0;
         }
         return this.birdsToday.getOrDefault(species, 0);
      }

      public void addBird(long day, String species) {
         if (this.birdDay != day) {
            this.birdDay = day;
            this.birdsToday.clear();
         }
         this.birdsToday.merge(species, 1, Integer::sum);
      }

      public void log(FilledTag t) {
         this.log.add(0, t);
         while (this.log.size() > LOG) {
            this.log.remove(this.log.size() - 1);
         }
      }

      CompoundTag save() {
         CompoundTag t = new CompoundTag();
         ListTag ss = new ListTag();
         this.seasons.forEach((p, s) -> {
            CompoundTag c = s.save();
            c.putInt("period", p);
            ss.add(c);
         });
         t.put("seasons", ss);
         t.putLong("birdDay", this.birdDay);
         CompoundTag b = new CompoundTag();
         this.birdsToday.forEach(b::putInt);
         t.put("birds", b);
         ListTag l = new ListTag();
         for (FilledTag f : this.log) {
            l.add(f.save());
         }
         t.put("log", l);
         t.putInt("filled", this.filledTotal);
         t.putInt("viol", this.violationsTotal);
         t.putInt("fines", this.finesTotal);
         t.putBoolean("ed", this.edIssued);
         t.putInt("record", this.record);
         t.putInt("lastViol", this.lastViolation);
         t.putInt("revoked", this.revokedUntil);
         t.putInt("charges", this.charges);
         return t;
      }

      static Hunter load(CompoundTag t) {
         Hunter h = new Hunter();
         for (Tag e : t.getList("seasons", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag)e;
            h.seasons.put(c.getInt("period"), Season.load(c));
         }
         while (h.seasons.size() > 2) {
            h.seasons.pollFirstEntry();
         }
         h.birdDay = t.getLong("birdDay");
         CompoundTag b = t.getCompound("birds");
         for (String k : b.getAllKeys()) {
            if (h.birdsToday.size() < 16) {
               h.birdsToday.put(k, Math.clamp(b.getInt(k), 0, 999));
            }
         }
         for (Tag e : t.getList("log", Tag.TAG_COMPOUND)) {
            if (h.log.size() < LOG) {
               h.log.add(FilledTag.load((CompoundTag)e));
            }
         }
         h.filledTotal = Math.max(0, t.getInt("filled"));
         h.violationsTotal = Math.max(0, t.getInt("viol"));
         h.finesTotal = Math.max(0, t.getInt("fines"));
         h.edIssued = t.getBoolean("ed");
         h.record = Math.clamp(t.getInt("record"), 0, 99);
         h.lastViolation = t.contains("lastViol") ? t.getInt("lastViol") : -1;
         h.revokedUntil = Math.max(0, t.getInt("revoked"));
         h.charges = Math.clamp(t.getInt("charges"), 0, 999);
         return h;
      }
   }

   @Override
   public CompoundTag save(CompoundTag tag, HolderLookup.Provider reg) {
      ListTag l = new ListTag();
      this.hunters.forEach((id, h) -> {
         CompoundTag c = h.save();
         c.putUUID("id", id);
         l.add(c);
      });
      tag.put("hunters", l);
      return tag;
   }

   public static LicenceStore load(CompoundTag tag, HolderLookup.Provider reg) {
      LicenceStore s = new LicenceStore();
      for (Tag e : tag.getList("hunters", Tag.TAG_COMPOUND)) {
         CompoundTag c = (CompoundTag)e;
         if (c.hasUUID("id")) {
            try {
               s.hunters.put(c.getUUID("id"), Hunter.load(c));
            } catch (RuntimeException ignored) {
               // a damaged record starts fresh
            }
         }
      }
      return s;
   }
}
