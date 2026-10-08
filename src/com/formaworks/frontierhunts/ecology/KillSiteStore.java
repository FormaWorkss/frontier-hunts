package com.formaworks.frontierhunts.ecology;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/**
 * [ecology] Per-dimension memory of predation ({@code frontierhunts_kill_sites}): kills per 128x128 area per in-game
 * day (the cap that keeps herds from being wiped out), when the last hunt in an area started, and the open carcasses
 * scavengers can find. Everything is bounded: at most {@link #MAX_SITES} sites, counters older than two days dropped.
 */
public final class KillSiteStore extends SavedData {
   public static final String NAME = "frontierhunts_kill_sites";
   public static final int MAX_SITES = 64;
   private static final int AREA_SHIFT = 7;

   public static final class Site {
      public final UUID carcass;
      public final double x, y, z;
      public final long killedAt;
      public final String predator;
      public final String prey;
      /** game time until which a feeder holds the carcass */
      public long busyUntil;

      Site(UUID carcass, double x, double y, double z, long killedAt, String predator, String prey) {
         this.carcass = carcass;
         this.x = x;
         this.y = y;
         this.z = z;
         this.killedAt = killedAt;
         this.predator = predator;
         this.prey = prey;
      }

      public Vec3 position() {
         return new Vec3(this.x, this.y, this.z);
      }
   }

   private final List<Site> sites = new ArrayList<>();
   private final Long2IntOpenHashMap kills = new Long2IntOpenHashMap();
   private final Long2LongOpenHashMap huntStarts = new Long2LongOpenHashMap();

   public static KillSiteStore of(ServerLevel level) {
      return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(KillSiteStore::new, KillSiteStore::load, null), NAME);
   }

   public static long area(double x, double z) {
      int ax = (int)Math.floor(x) >> AREA_SHIFT;
      int az = (int)Math.floor(z) >> AREA_SHIFT;
      return (long)ax & 0xFFFFFFFFL | ((long)az & 0xFFFFFFFFL) << 32;
   }

   private static long areaDay(double x, double z, long now) {
      long day = now / 24000L;
      // mix the day into the area key (areas are 32-bit each; days are small)
      return area(x, z) * 31L + day * 0x9E3779B97F4A7C15L;
   }

   public int killsToday(Vec3 at, long now) {
      return this.kills.get(areaDay(at.x, at.z, now));
   }

   public boolean underCap(Vec3 at, long now) {
      return this.killsToday(at, now) < EcologyConfig.killsPerAreaPerDay();
   }

   public void countKill(Vec3 at, long now) {
      long k = areaDay(at.x, at.z, now);
      this.kills.put(k, this.kills.get(k) + 1);
      if (this.kills.size() > 512) {
         // old days are useless: start over (worst case one area gets one extra kill today)
         this.kills.clear();
         this.kills.put(k, 1);
      }
      this.setDirty();
   }

   /** true when no hunt began in this area for {@code gap} ticks; records the new start */
   public boolean startHunt(Vec3 at, long now, long gap) {
      long a = area(at.x, at.z);
      long last = this.huntStarts.getOrDefault(a, Long.MIN_VALUE);
      if (last != Long.MIN_VALUE && now - last < gap && now >= last) {
         return false;
      }
      this.huntStarts.put(a, now);
      if (this.huntStarts.size() > 512) {
         this.huntStarts.long2LongEntrySet().removeIf(e -> now - e.getLongValue() > 24000L);
      }
      return true;
   }

   public void addSite(UUID carcass, Vec3 pos, long killedAt, String predator, String prey) {
      if (carcass == null || pos == null || !Double.isFinite(pos.lengthSqr())) {
         return;
      }
      this.removeSite(carcass);
      if (this.sites.size() >= MAX_SITES) {
         this.sites.remove(0);
      }
      Site s = new Site(carcass, pos.x, pos.y, pos.z, killedAt, predator, prey);
      this.sites.add(s);
      this.setDirty();
   }

   public void removeSite(UUID carcass) {
      if (this.sites.removeIf(s -> s.carcass.equals(carcass))) {
         this.setDirty();
      }
   }

   public Site site(UUID carcass) {
      for (Site s : this.sites) {
         if (s.carcass.equals(carcass)) {
            return s;
         }
      }
      return null;
   }

   /** nearest carcass nobody is feeding on, killed at least {@code minAge} ticks ago, within {@code radius} */
   public Site nearestOpen(Vec3 at, double radius, long now, long minAge) {
      Site best = null;
      double bd = radius * radius;
      for (Site s : this.sites) {
         if (s.busyUntil > now || now - s.killedAt < minAge) {
            continue;
         }
         double dx = s.x - at.x, dy = s.y - at.y, dz = s.z - at.z;
         double d = dx * dx + dy * dy * 4.0 + dz * dz;
         if (d < bd) {
            bd = d;
            best = s;
         }
      }
      return best;
   }

   public List<Site> near(Vec3 at, double radius) {
      List<Site> out = new ArrayList<>();
      for (Site s : this.sites) {
         if (s.position().distanceToSqr(at) < radius * radius) {
            out.add(s);
         }
      }
      return out;
   }

   /** forget sites whose carcass must be long gone (safety net if a carcass vanished without telling us) */
   public void prune(long now, long carcassTicks) {
      Iterator<Site> it = this.sites.iterator();
      boolean changed = false;
      while (it.hasNext()) {
         Site s = it.next();
         if (now - s.killedAt > carcassTicks + 48000L || s.killedAt > now + 24000L) {
            it.remove();
            changed = true;
         }
      }
      if (changed) {
         this.setDirty();
      }
   }

   @Override
   public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
      ListTag list = new ListTag();
      for (Site s : this.sites) {
         CompoundTag t = new CompoundTag();
         t.putUUID("carcass", s.carcass);
         t.putDouble("x", s.x);
         t.putDouble("y", s.y);
         t.putDouble("z", s.z);
         t.putLong("at", s.killedAt);
         t.putString("predator", s.predator);
         t.putString("prey", s.prey);
         list.add(t);
      }
      tag.put("sites", list);
      CompoundTag k = new CompoundTag();
      this.kills.long2IntEntrySet().forEach(e -> k.putInt(Long.toString(e.getLongKey()), e.getIntValue()));
      tag.put("kills", k);
      CompoundTag h = new CompoundTag();
      this.huntStarts.long2LongEntrySet().forEach(e -> h.putLong(Long.toString(e.getLongKey()), e.getLongValue()));
      tag.put("hunts", h);
      return tag;
   }

   public static KillSiteStore load(CompoundTag tag, HolderLookup.Provider provider) {
      KillSiteStore s = new KillSiteStore();
      ListTag list = tag.getList("sites", Tag.TAG_COMPOUND);
      for (int i = 0; i < list.size() && s.sites.size() < MAX_SITES; i++) {
         CompoundTag t = list.getCompound(i);
         if (!t.hasUUID("carcass")) {
            continue;
         }
         double x = t.getDouble("x"), y = t.getDouble("y"), z = t.getDouble("z");
         if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            continue;
         }
         s.sites.add(new Site(t.getUUID("carcass"), x, y, z, t.getLong("at"), t.getString("predator"), t.getString("prey")));
      }
      CompoundTag k = tag.getCompound("kills");
      for (String key : k.getAllKeys()) {
         try {
            s.kills.put(Long.parseLong(key), k.getInt(key));
         } catch (NumberFormatException ignored) {
         }
      }
      CompoundTag h = tag.getCompound("hunts");
      for (String key : h.getAllKeys()) {
         try {
            s.huntStarts.put(Long.parseLong(key), h.getLong(key));
         } catch (NumberFormatException ignored) {
         }
      }
      return s;
   }
}
