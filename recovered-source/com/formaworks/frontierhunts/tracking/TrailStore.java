package com.formaworks.frontierhunts.tracking;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import net.minecraft.world.phys.Vec3;

public final class TrailStore extends SavedData {
   public static final int WORLD_LIMIT = 8192;
   public static final int CELL_LIMIT = 64;
   public static final int CLIENT_LIMIT = 192;
   public static final double VIEW_RADIUS = 32.0;
   private final NavigableMap<UUID, TrailMark> marks = new TreeMap<>();
   private final LinkedHashSet<UUID> arrival = new LinkedHashSet<>();
   private final Map<Long, LinkedHashSet<UUID>> cells = new HashMap<>();
   private UUID cursor;
   private long previousWeatherTime = -1L;
   private long rainClock;
   private final Map<UUID, Long> weatherSeen = new HashMap<>();

   public static TrailStore get(ServerLevel var0) {
      return (TrailStore)var0.getDataStorage().computeIfAbsent(new Factory<>(TrailStore::new, TrailStore::load, null), "frontierhunts_trails");
   }

   private static long cell(Vec3 var0) {
      return ChunkPos.asLong((int)Math.floor(var0.x) >> 4, (int)Math.floor(var0.z) >> 4);
   }

   public int size() {
      return this.marks.size();
   }

   public TrailMark find(UUID var1) {
      return this.marks.get(var1);
   }

   public boolean add(TrailMark var1, long var2) {
      if (!var1.expired(var2) && !this.marks.containsKey(var1.id())) {
         long var4 = cell(var1.position());

         for (LinkedHashSet var6 = this.cells.computeIfAbsent(var4, var0 -> new LinkedHashSet<>());
            var6.size() >= CELL_LIMIT;
            var6 = this.cells.computeIfAbsent(var4, var0 -> new LinkedHashSet<>())
         ) {
            this.remove(this.eviction(var6));
         }

         while (this.marks.size() >= WORLD_LIMIT) {
            this.remove(this.eviction(this.arrival));
         }

         this.cells.computeIfAbsent(var4, var0 -> new LinkedHashSet<>()).add(var1.id());
         this.marks.put(var1.id(), var1);
         this.arrival.add(var1.id());
         this.weatherSeen.put(var1.id(), this.rainClock);
         this.setDirty();
         return true;
      } else {
         return false;
      }
   }

   private UUID eviction(LinkedHashSet<UUID> var1) {
      for (UUID var3 : var1) {
         TrailMark p = this.marks.get(var3);
         if (p != null && p.style() == TrailMark.PASSAGE && !p.blood()) {
            return var3; // [1.1.6] bent grass is the cheapest sign to lose
         }
      }
      for (UUID var3 : var1) {
         TrailMark p = this.marks.get(var3);
         if (p != null && p.print()) {
            return var3;
         }
      }
      for (UUID var3 : var1) {
         TrailMark var4 = this.marks.get(var3);
         if (!var4.blood()) {
            return var3;
         }

         if (var4.style() == 0 && this.hasOtherDrip(var1, var3, var4.animal())) {
            return var3;
         }

         if (var4.style() != 1 && var4.style() != 4 && var4.style() != 0) {
            return var3;
         }
      }

      return (UUID)var1.getFirst();
   }

   private boolean hasOtherDrip(LinkedHashSet<UUID> var1, UUID var2, UUID var3) {
      for (UUID var5 : var1) {
         TrailMark var6 = this.marks.get(var5);
         if (!var5.equals(var2) && var6 != null && var6.animal().equals(var3) && var6.style() == 0) {
            return true;
         }
      }

      return false;
   }

   public void remove(UUID var1) {
      TrailMark var2 = this.marks.remove(var1);
      if (var2 != null) {
         this.arrival.remove(var1);
         this.weatherSeen.remove(var1);
         long var3 = cell(var2.position());
         LinkedHashSet var5 = this.cells.get(var3);
         var5.remove(var1);
         if (var5.isEmpty()) {
            this.cells.remove(var3);
         }

         this.setDirty();
      }
   }

   public List<TrailMark> nearby(Vec3 var1, double var2, int var4, long var5) {
      var2 = Math.clamp(var2, 0.0, 64.0);
      var4 = Math.clamp((long)var4, 0, 8192);
      ArrayList var7 = new ArrayList();
      int var8 = (int)Math.floor(var1.x - var2) >> 4;
      int var9 = (int)Math.floor(var1.x + var2) >> 4;
      int var10 = (int)Math.floor(var1.z - var2) >> 4;
      int var11 = (int)Math.floor(var1.z + var2) >> 4;

      for (int var12 = var8; var12 <= var9; var12++) {
         for (int var13 = var10; var13 <= var11; var13++) {
            LinkedHashSet<UUID> var14 = this.cells.get(ChunkPos.asLong(var12, var13));
            if (var14 != null) {
               for (UUID var16 : var14) {
                  TrailMark var17 = this.marks.get(var16);
                  if (!var17.expired(var5) && var17.position().distanceToSqr(var1) <= var2 * var2) {
                     var7.add(var17);
                  }
               }
            }
         }
      }

      var7.sort(Comparator.<TrailMark>comparingDouble(var1x -> var1x.position().distanceToSqr(var1)).thenComparing(TrailMark::id));
      return List.copyOf(var7.subList(0, Math.min(var4, var7.size())));
   }

   public void maintain(ServerLevel var1) {
      long var2 = var1.getGameTime();
      if (this.previousWeatherTime >= 0L && var2 > this.previousWeatherTime && var1.isRaining()) {
         this.rainClock = this.rainClock + Math.min(20L, var2 - this.previousWeatherTime);
      }

      this.previousWeatherTime = var2;
      ArrayList<UUID> var4 = new ArrayList<>(256);
      NavigableMap<UUID, TrailMark> var5 = this.cursor == null ? this.marks : this.marks.tailMap(this.cursor, false);

      for (UUID var7 : var5.keySet()) {
         var4.add(var7);
         if (var4.size() == 256) {
            break;
         }
      }

      if (var4.isEmpty()) {
         this.cursor = null;
      } else {
         this.cursor = (UUID)var4.getLast();

         for (UUID var12 : var4) {
            TrailMark var8 = this.marks.get(var12);
            long var9 = this.rainClock - this.weatherSeen.getOrDefault(var12, this.rainClock);
            this.weatherSeen.put(var12, this.rainClock);
            if (var1.hasChunkAt(var8.support())) {
               if (var9 > 0L) {
                  int wear = weathering(var1, var8, var9);
                  if (wear > 0) {
                     var8 = var8.weather(wear);
                     this.marks.put(var12, var8);
                     this.setDirty();
                  }
               }

               if (!TrailService.supported(var1, var8)) {
                  this.remove(var12);
                  continue;
               }
            }

            if (var8.expired(var2)) {
               this.remove(var12);
            }
         }
      }
   }

   /**
    * [tracking] Extra age from precipitation on an exposed mark: falling snow fills prints and covers blood, rain
    * washes blood away fast and slumps mud and soil prints; prints pressed in snow soften in rain.
    */
   static int weathering(ServerLevel level, TrailMark m, long wetTicks) {
      BlockPos above = m.face() == net.minecraft.core.Direction.UP ? m.support().above() : BlockPos.containing(m.position());
      if (!level.canSeeSky(above)) {
         return 0;
      }
      Biome.Precipitation p = ((Biome)level.getBiome(m.support()).value()).getPrecipitationAt(m.support());
      if (p == Biome.Precipitation.NONE) {
         return 0;
      }
      boolean snowing = p == Biome.Precipitation.SNOW;
      double k;
      if (m.blood()) {
         k = snowing ? 5.0 : (m.face() == net.minecraft.core.Direction.UP ? 7.0 : 4.0);
      } else if (m.style() == 0) {
         int surface = TrailSurfaces.printSurface(level.getBlockState(m.support()));
         k = snowing ? (surface == TrailSurfaces.SNOW ? 6.0 : 4.0)
            : switch (surface) {
               case TrailSurfaces.SNOW -> 3.0;
               case TrailSurfaces.MUD, TrailSurfaces.SAND -> 3.5;
               default -> 2.5;
            };
      } else {
         k = 2.0;
      }
      return (int)Math.min(TrailMark.MAX_WEAR, wetTicks * k);
   }

   public CompoundTag save(CompoundTag var1, Provider var2) {
      var1.putInt("schema", 4);
      ListTag var3 = new ListTag();

      for (UUID var5 : this.arrival) {
         var3.add(this.marks.get(var5).save());
      }

      var1.put("marks", var3);
      return var1;
   }

   public static TrailStore load(CompoundTag var0, Provider var1) {
      if (var0.getInt("schema") >= 1 && var0.getInt("schema") <= 4) {
         TrailStore var2 = new TrailStore();
         ListTag var3 = var0.getList("marks", 10);
         if (var3.size() > WORLD_LIMIT * 2) {
            throw new IllegalStateException("Frontier trail save exceeds its capacity");
         } else {
            for (int var4 = 0; var4 < var3.size(); var4++) {
               TrailMark var5 = TrailMark.load(var3.getCompound(var4));
               var2.add(var5, var5.created());
            }

            return var2;
         }
      } else {
         throw new IllegalStateException("Unsupported Frontier trail schema: " + var0.getInt("schema"));
      }
   }
}
