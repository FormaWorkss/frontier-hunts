package com.formaworks.frontierhunts.landscape;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * [1.1.9] The reserve's biome source with the snow-country biomes added to the set of biomes the world can hold (so
 * the land's features - its timber, its first snow - are placed in them too). Every biome lookup goes to the reserve's
 * own source unchanged; snow country is laid over the chunks as they're made ({@link AlpineGenerator#createBiomes}).
 * The world's settings still save the reserve's own source (the generator's codec), never this.
 *
 * <p>[1.2.5] Every Frontier world uses it now (with no extra biomes when snow country is off), because it also answers
 * {@code /locate biome}. Vanilla's search tries up to sixteen heights in every column of a 6400-block radius (millions of
 * lookups when a biome is rare or missing, long enough for the server watchdog to stop the server), and it never
 * found the snow-country biomes at all, because those are laid over the chunks afterwards. Frontier chunks take one
 * biome per column (at height 0, see createBiomes), so this search asks exactly that once per column, with the snow
 * overlay applied: the same answer the chunks will hold, sixteen times fewer lookups.
 *
 * <p>Even so the land has to be worked out over the whole radius (seconds per 1600-block square, more far from the
 * world's centre), so the search runs on the spare cores, remembers what it has worked out, and gives up after ten
 * seconds ("not found within reasonable distance") instead of holding the server for minutes.
 */
final class SnowBiomeSource extends BiomeSource {
   static final MapCodec<SnowBiomeSource> CODEC = AlpineBiomes.CODEC.xmap(b -> new SnowBiomeSource(b, List.of()), s -> s.base);
   final AlpineBiomes base;
   private final List<Holder<Biome>> extra;
   /** the snow overlay the generator lays over its chunks (null when snow country is off) */
   volatile SnowCountry snow;

   SnowBiomeSource(AlpineBiomes base, List<Holder<Biome>> extra) {
      this.base = base;
      this.extra = extra;
   }

   @Override
   protected MapCodec<? extends BiomeSource> codec() {
      return CODEC;
   }

   @Override
   protected Stream<Holder<Biome>> collectPossibleBiomes() {
      return Stream.concat(this.base.possibleBiomes().stream(), this.extra.stream()).distinct();
   }

   @Override
   public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
      return this.base.getNoiseBiome(x, y, z, sampler);
   }

   /** [1.2.5] the biome a chunk holds at this column (quart coordinates) */
   Holder<Biome> chunkBiome(int qx, int qz, Climate.Sampler sampler) {
      Holder<Biome> h = this.base.getNoiseBiome(qx, 0, qz, sampler);
      SnowCountry s = this.snow;
      return s == null ? h : s.biome(h, qx, qz);
   }

   /** [1.2.5] how long one /locate may search before it gives up (well inside the server watchdog's 60 s) */
   static final long BUDGET_NS = 10_000_000_000L;
   static final int BATCH = 256;
   /** the cores /locate may use (one stays with the server) */
   private static final java.util.concurrent.ExecutorService POOL = java.util.concurrent.Executors.newFixedThreadPool(
      Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1)), r -> {
         Thread t = new Thread(r, "frontier-locate");
         t.setDaemon(true);
         t.setPriority(Thread.NORM_PRIORITY - 1);
         return t;
      });
   private static final int THREADS = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1));
   /** columns already worked out (a second /locate nearby is instant) */
   private final java.util.concurrent.ConcurrentHashMap<Long, Holder<Biome>> seen = new java.util.concurrent.ConcurrentHashMap<>();

   private Holder<Biome> cached(int qx, int qz, Climate.Sampler sampler) {
      long k = (long)qx << 32 ^ qz & 0xFFFFFFFFL;
      Holder<Biome> h = this.seen.get(k);
      if (h == null) {
         h = this.chunkBiome(qx, qz, sampler);
         if (this.seen.size() > 600_000) {
            this.seen.clear();
         }
         this.seen.put(k, h);
      }
      return h;
   }

   @Override
   public Pair<BlockPos, Holder<Biome>> findClosestBiome3d(BlockPos pos, int radius, int horizontalStep, int verticalStep,
      Predicate<Holder<Biome>> wanted, Climate.Sampler sampler, LevelReader level) {
      Set<Holder<Biome>> set = this.possibleBiomes().stream().filter(wanted).collect(Collectors.toUnmodifiableSet());
      if (set.isEmpty()) {
         return null;
      }
      long t0 = System.nanoTime();
      int rings = Math.floorDiv(radius, horizontalStep);
      int[] xs = new int[BATCH], zs = new int[BATCH];
      int n = 0, done = 0;
      for (BlockPos.MutableBlockPos m : BlockPos.spiralAround(BlockPos.ZERO, rings, Direction.EAST, Direction.SOUTH)) {
         xs[n] = pos.getX() + m.getX() * horizontalStep;
         zs[n] = pos.getZ() + m.getZ() * horizontalStep;
         if (++n == BATCH) {
            Pair<BlockPos, Holder<Biome>> hit = this.batch(xs, zs, n, pos.getY(), set, sampler);
            if (hit != null) {
               return hit;
            }
            done += n;
            n = 0;
            if (System.nanoTime() - t0 > BUDGET_NS) {
               com.mojang.logging.LogUtils.getLogger().info("[FrontierHunts] /locate biome stopped after {} s: searched {} columns, out to about {} blocks",
                  BUDGET_NS / 1_000_000_000L, done, (int)Math.sqrt(done) * horizontalStep / 2);
               return null;
            }
         }
      }
      return n > 0 ? this.batch(xs, zs, n, pos.getY(), set, sampler) : null;
   }

   /** work out a batch of columns (in parallel), then take the first match in spiral order: the nearest */
   private Pair<BlockPos, Holder<Biome>> batch(int[] xs, int[] zs, int n, int y, Set<Holder<Biome>> set, Climate.Sampler sampler) {
      @SuppressWarnings("unchecked")
      Holder<Biome>[] out = new Holder[n];
      if (THREADS <= 1) {
         for (int i = 0; i < n; i++) {
            out[i] = this.cached(QuartPos.fromBlock(xs[i]), QuartPos.fromBlock(zs[i]), sampler);
         }
      } else {
         java.util.List<java.util.concurrent.Future<?>> jobs = new java.util.ArrayList<>(THREADS);
         for (int t = 0; t < THREADS; t++) {
            final int from = t;
            jobs.add(POOL.submit(() -> {
               for (int i = from; i < n; i += THREADS) {
                  out[i] = this.cached(QuartPos.fromBlock(xs[i]), QuartPos.fromBlock(zs[i]), sampler);
               }
            }));
         }
         for (java.util.concurrent.Future<?> f : jobs) {
            try {
               f.get();
            } catch (InterruptedException e) {
               Thread.currentThread().interrupt();
               return null;
            } catch (java.util.concurrent.ExecutionException e) {
               throw new RuntimeException(e.getCause());
            }
         }
      }
      for (int i = 0; i < n; i++) {
         if (set.contains(out[i])) {
            return Pair.of(new BlockPos(xs[i], y, zs[i]), out[i]);
         }
      }
      return null;
   }
}
