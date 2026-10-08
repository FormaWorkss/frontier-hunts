package com.formaworks.frontierhunts.livingworld;

import com.formaworks.frontierhunts.livingworld.plan.Terrain;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * [livingworld] Natural terrain heights from the chunk generator (pure functions of the seed and position, so a
 * structure sees the same terrain in every chunk it touches). Every value used while planning is cached and saved with
 * the structure piece, so the plan is rebuilt bit-for-bit later even after a restart.
 */
public final class McTerrain implements Terrain {
   private final ChunkGenerator gen;
   private final LevelHeightAccessor height;
   private final RandomState random;
   private final Map<Long, int[]> cache = new HashMap<>();

   public McTerrain(ChunkGenerator gen, LevelHeightAccessor height, RandomState random) {
      this.gen = gen;
      this.height = height;
      this.random = random;
   }

   static long key(int x, int z) {
      return ((long)x << 32) ^ (z & 0xFFFFFFFFL);
   }

   private int[] sample(int x, int z) {
      return this.cache.computeIfAbsent(key(x, z), k -> {
         if (this.gen == null) {
            return new int[]{64, 64};
         }
         if (this.gen instanceof com.formaworks.frontierhunts.landscape.AlpineGenerator alpine) {
            // one layout sample gives both heights (getBaseHeight would sample twice; the alpine layout is costly)
            com.formaworks.frontierhunts.landscape.AlpineLayout.Sample a = alpine.layout().sample(x, z);
            int lo = this.height.getMinBuildHeight();
            int hi = this.height.getMaxBuildHeight();
            int f = Math.max(lo, Math.min(hi, a.floor() + 1));
            int w = Math.max(lo, Math.min(hi, Math.max(a.floor(), a.water()) + 1));
            return new int[]{f, w};
         }
         int floor = this.gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, this.height, this.random);
         int surface = this.gen.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, this.height, this.random);
         return new int[]{floor, Math.max(floor, surface)};
      });
   }

   @Override
   public int floor(int x, int z) {
      return this.sample(x, z)[0];
   }

   @Override
   public int surface(int x, int z) {
      return this.sample(x, z)[1];
   }

   @Override
   public String biome(int x, int z) {
      if (this.gen == null) {
         return "";
      }
      try {
         Holder<Biome> b = this.gen.getBiomeSource()
            .getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(this.floor(x, z)), QuartPos.fromBlock(z), this.random.sampler());
         return b.unwrapKey().map(k -> k.location().toString()).orElse("");
      } catch (RuntimeException ex) {
         return "";
      }
   }

   public CompoundTag save() {
      int[] data = new int[this.cache.size() * 4];
      int i = 0;
      for (Map.Entry<Long, int[]> e : this.cache.entrySet()) {
         data[i++] = (int)(e.getKey() >> 32);
         data[i++] = (int)(long)e.getKey();
         data[i++] = e.getValue()[0];
         data[i++] = e.getValue()[1];
      }
      CompoundTag t = new CompoundTag();
      t.putIntArray("h", data);
      return t;
   }

   public void load(CompoundTag t) {
      int[] data = t.getIntArray("h");
      for (int i = 0; i + 3 < data.length; i += 4) {
         this.cache.put(key(data[i], data[i + 1]), new int[]{data[i + 2], data[i + 3]});
      }
   }

   public int cached() {
      return this.cache.size();
   }
}
