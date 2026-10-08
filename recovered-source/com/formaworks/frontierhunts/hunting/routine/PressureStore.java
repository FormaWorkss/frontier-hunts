package com.formaworks.frontierhunts.hunting.routine;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/**
 * Hunting pressure per 64x64 block cell of a dimension. Pressure is a plain number of "units" (a rifle shot is one unit)
 * that halves every {@link RoutineConfig#halfLifeDays()} in-game days. Values are decayed lazily when read, so nothing
 * ticks. {@link #level01} maps units to the 0..1 behaviour level animals react to.
 */
public final class PressureStore extends SavedData {
   public static final int CELL_SHIFT = 6;
   public static final int CELL_SIZE = 1 << CELL_SHIFT;
   private static final String NAME = "frontierhunts_pressure";
   private static final int MAX_CELLS = 16384;
   private final Long2ObjectOpenHashMap<Cell> cells = new Long2ObjectOpenHashMap<>();

   static final class Cell {
      float value;
      long time;
      float total;

      Cell(float value, long time) {
         this.value = value;
         this.time = time;
      }
   }

   public static PressureStore of(ServerLevel level) {
      return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(PressureStore::new, PressureStore::load, null), NAME);
   }

   public static long key(int cx, int cz) {
      return (long)cx & 0xFFFFFFFFL | ((long)cz & 0xFFFFFFFFL) << 32;
   }

   public static int cellX(long key) {
      return (int)key;
   }

   public static int cellZ(long key) {
      return (int)(key >> 32);
   }

   public static int cell(double coord) {
      return (int)Math.floor(coord) >> CELL_SHIFT;
   }

   /** Units decay by half every half-life: {@code v * 0.5^(dt / halfLife)}. */
   public static float decay(float value, long dt, double halfLifeTicks) {
      if (dt <= 0L || value <= 0.0F) {
         return Math.max(0.0F, value);
      } else {
         return (float)(value * Math.pow(0.5, (double)dt / Math.max(1.0, halfLifeTicks)));
      }
   }

   /** 0..1 behaviour level: one day of a few shots is ~0.5, repeated daily shooting saturates near 1. */
   public static float level01(float units) {
      return units <= 0.0F ? 0.0F : (float)(1.0 - Math.exp(-units / 4.0));
   }

   static double halfLifeTicks() {
      return RoutineConfig.halfLifeDays() * 24000.0;
   }

   public float value(long now, int cx, int cz) {
      Cell c = this.cells.get(key(cx, cz));
      return c == null ? 0.0F : decay(c.value, now - c.time, halfLifeTicks());
   }

   public float value(long now, BlockPos pos) {
      return this.value(now, pos.getX() >> CELL_SHIFT, pos.getZ() >> CELL_SHIFT);
   }

   /** Lifetime units ever added to a cell (not decayed) - for the debug command. */
   public float total(int cx, int cz) {
      Cell c = this.cells.get(key(cx, cz));
      return c == null ? 0.0F : c.total;
   }

   private void addCell(long now, int cx, int cz, float amount) {
      long k = key(cx, cz);
      Cell c = this.cells.get(k);
      if (c == null) {
         if (amount <= 0.0F) {
            return;
         }

         c = new Cell(0.0F, now);
         this.cells.put(k, c);
      }

      c.value = Math.max(0.0F, Math.min(200.0F, decay(c.value, now - c.time, halfLifeTicks()) + amount));
      c.time = now;
      if (amount > 0.0F) {
         c.total += amount;
      }
   }

   /** Adds pressure at a position; a fifth spills into the four edge neighbours and a tenth into the diagonals. */
   public void add(long now, Vec3 at, float amount) {
      if (!(amount == 0.0F) && Float.isFinite(amount) && at != null && Double.isFinite(at.x) && Double.isFinite(at.z)) {
         int cx = cell(at.x);
         int cz = cell(at.z);
         this.addCell(now, cx, cz, amount);
         if (amount > 0.0F) {
            for (int dx = -1; dx <= 1; dx++) {
               for (int dz = -1; dz <= 1; dz++) {
                  if (dx != 0 || dz != 0) {
                     this.addCell(now, cx + dx, cz + dz, amount * (dx != 0 && dz != 0 ? 0.1F : 0.2F));
                  }
               }
            }
         }

         if (this.cells.size() > MAX_CELLS) {
            this.prune(now, 0.2F);
         }

         this.setDirty();
      }
   }

   public void set(long now, int cx, int cz, float units) {
      long k = key(cx, cz);
      if (units <= 0.0F) {
         this.cells.remove(k);
      } else {
         Cell c = this.cells.computeIfAbsent(k, x -> new Cell(0.0F, now));
         c.value = Math.min(200.0F, units);
         c.time = now;
      }

      this.setDirty();
   }

   public int clear(int cx, int cz, int radius) {
      int n = 0;

      for (int dx = -radius; dx <= radius; dx++) {
         for (int dz = -radius; dz <= radius; dz++) {
            if (this.cells.remove(key(cx + dx, cz + dz)) != null) {
               n++;
            }
         }
      }

      this.setDirty();
      return n;
   }

   private void prune(long now, float below) {
      double hl = halfLifeTicks();
      ObjectIterator<Long2ObjectMap.Entry<Cell>> it = this.cells.long2ObjectEntrySet().fastIterator();

      while (it.hasNext()) {
         Cell c = it.next().getValue();
         if (decay(c.value, now - c.time, hl) < below) {
            it.remove();
         }
      }
   }

   public static PressureStore load(CompoundTag tag, HolderLookup.Provider provider) {
      PressureStore s = new PressureStore();
      ListTag list = tag.getList("cells", Tag.TAG_COMPOUND);

      for (int i = 0; i < list.size() && i < MAX_CELLS; i++) {
         CompoundTag c = list.getCompound(i);
         float v = c.getFloat("v");
         if (Float.isFinite(v) && v > 0.0F) {
            Cell cell = new Cell(Math.min(200.0F, v), c.getLong("t"));
            cell.total = c.getFloat("total");
            s.cells.put(key(c.getInt("x"), c.getInt("z")), cell);
         }
      }

      return s;
   }

   @Override
   public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
      ListTag list = new ListTag();
      ObjectIterator<Long2ObjectMap.Entry<Cell>> it = this.cells.long2ObjectEntrySet().fastIterator();

      while (it.hasNext()) {
         Long2ObjectMap.Entry<Cell> e = it.next();
         Cell c = e.getValue();
         // drop cells that have decayed to nothing (relative to their own timestamp; the caller's clock isn't known here)
         if (c.value >= 0.02F) {
            CompoundTag t = new CompoundTag();
            t.putInt("x", cellX(e.getLongKey()));
            t.putInt("z", cellZ(e.getLongKey()));
            t.putFloat("v", c.value);
            t.putLong("t", c.time);
            t.putFloat("total", c.total);
            list.add(t);
         }
      }

      tag.put("cells", list);
      return tag;
   }

   /** Periodic housekeeping from {@link PressureEvents}: forget cells that have decayed away. */
   void housekeeping(long now) {
      int before = this.cells.size();
      this.prune(now, 0.02F);
      if (this.cells.size() != before) {
         this.setDirty();
      }
   }
}
