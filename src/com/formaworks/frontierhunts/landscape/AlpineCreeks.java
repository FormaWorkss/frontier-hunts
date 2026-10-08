package com.formaworks.frontierhunts.landscape;

import com.formaworks.frontierhunts.FrontierHunts;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * [streams] The Frontier's own mountain creeks: small streams that rise on the slopes and run down the fall line,
 * meandering, gathering, stepping down in little falls, until they reach a river, a lake or a hollow where they pool.
 * The big rivers and lakes come from {@link AlpineDrainage}; these are the many small waters between them, so a world
 * reads like one with a streams mod installed, without needing one (and when Streams Reflowing is installed it is
 * left to do this itself).
 *
 * <p>Our own design: springs sit on a jittered 88-block grid (about half are wet, more in moist country); from each
 * spring a path is traced down the analytic terrain ({@link AlpineLayout#sample}) with momentum and a gentle meander,
 * and its water level never rises downstream. Each chunk then cuts the stretches that cross it: a channel one to four
 * blocks wide with a gravel bed, still water on the flats, a small spill wherever the level steps down (vanilla water
 * carries it, so the creek visibly runs), firm banks so water never leaks down a hillside, and the river current
 * marker on the runs so the Frontier's flowing-water surface and current apply. The network is the same whichever
 * chunk asks, so creeks join seamlessly across chunk borders.
 */
@EventBusSubscriber(modid = "frontierhunts", bus = EventBusSubscriber.Bus.MOD)
public final class AlpineCreeks extends Feature<NoneFeatureConfiguration> {
   static final int CELL = 88;
   private static final double STEP = 3.0;
   private static final int MAX_STEPS = 130;
   private static final int REACH_CELLS = (int)Math.ceil((MAX_STEPS * STEP + 24.0) / CELL);
   private static final Map<Long, Creek> CACHE = new ConcurrentHashMap<>();
   private static volatile long cacheSeed = Long.MIN_VALUE;
   private static final AtomicLong CARVED = new AtomicLong();
   private static final AtomicLong CHUNKS = new AtomicLong();
   private static final AtomicLong NANOS = new AtomicLong();
   private static Boolean streamsMod;

   public AlpineCreeks() {
      super(NoneFeatureConfiguration.CODEC);
   }

   @SubscribeEvent
   public static void register(RegisterEvent event) {
      event.register(Registries.FEATURE, FrontierHunts.id("alpine_creeks"), AlpineCreeks::new);
   }

   /** Creek columns cut since start (tests and the debug line). */
   public static long carved() {
      return CARVED.get();
   }

   private static boolean streamsMod() {
      Boolean b = streamsMod;
      if (b == null) {
         b = ModList.get() != null && ModList.get().isLoaded("streamsreflowing");
         streamsMod = b;
         if (b) {
            LogUtils.getLogger().info("Frontier creeks: Streams Reflowing is installed, it makes the streams");
         }
      }
      return b;
   }

   // ------------------------------------------------------------------ network

   /** One traced creek: points every ~3 blocks, water surface per point (never rising), and its bounds. */
   record Creek(float[] x, float[] z, int[] water, float[] radius, int count, float minX, float minZ, float maxX, float maxZ) {
      static final Creek NONE = new Creek(new float[0], new float[0], new int[0], new float[0], 0, 0, 0, 0, 0);
   }

   static Creek creek(AlpineLayout layout, int cx, int cz) {
      if (cacheSeed != layout.seed() * 31 + layout.version()) {
         CACHE.clear();
         cacheSeed = layout.seed() * 31 + layout.version();
      }
      long key = (long)cx << 32 ^ (long)cz & 0xFFFFFFFFL;
      Creek c = CACHE.get(key);
      if (c == null) {
         if (CACHE.size() > 20000) {
            CACHE.clear();
         }
         c = trace(layout, cx, cz);
         CACHE.put(key, c);
      }
      return c;
   }

   private static Creek trace(AlpineLayout layout, int cx, int cz) {
      double u = layout.variation(cx, cz, 9801L);
      double sx = cx * CELL + 14 + layout.variation(cx, cz, 9803L) * (CELL - 28);
      double sz = cz * CELL + 14 + layout.variation(cx, cz, 9805L) * (CELL - 28);
      AlpineLayout.Sample s = layout.sample(sx, sz);
      // a spring: dry ground above the sea, away from the big rivers, below the bare rock and snow, wet enough
      if (s.wet() || AlpineLayout.sea(s.biome()) || s.floor() < 72 || s.ground() > s.snowLine() - 6.0
         || s.distance() < s.width() + 28.0 || s.scenic()) {
         return Creek.NONE;
      }
      double wetness = Math.max(0.0, Math.min(1.0, s.moisture()));
      if (u > 0.36 + 0.44 * wetness) {
         return Creek.NONE;
      }
      float[] xs = new float[MAX_STEPS + 2], zs = new float[MAX_STEPS + 2], rs = new float[MAX_STEPS + 2];
      int[] ws = new int[MAX_STEPS + 2];
      int n = 0;
      double x = sx, z = sz;
      int water = s.floor() - 1;
      double dx = 0.0, dz = 0.0;
      int flat = 0;
      boolean joined = false;
      xs[n] = (float)x;
      zs[n] = (float)z;
      ws[n] = water;
      rs[n] = 0.6F;
      n++;
      for (int i = 0; i < MAX_STEPS; i++) {
         double gx = layout.sample(x + 2.0, z).ground() - layout.sample(x - 2.0, z).ground();
         double gz = layout.sample(x, z + 2.0).ground() - layout.sample(x, z - 2.0).ground();
         double g = Math.sqrt(gx * gx + gz * gz);
         double ddx, ddz;
         if (g < 0.08) {
            flat++;
            if (flat > 18 || dx == 0.0 && dz == 0.0) {
               break; // a hollow: the creek pools here
            }
            ddx = dx;
            ddz = dz;
         } else {
            flat = 0;
            ddx = -gx / g;
            ddz = -gz / g;
         }
         // momentum and a slow meander across the fall line
         double m = layout.noise(x / 37.0, z / 37.0, 9807L) * 0.55;
         double nx = (dx == 0.0 && dz == 0.0 ? ddx : 0.5 * dx + 0.5 * ddx) - ddz * m;
         double nz = (dx == 0.0 && dz == 0.0 ? ddz : 0.5 * dz + 0.5 * ddz) + ddx * m;
         double nl = Math.sqrt(nx * nx + nz * nz);
         if (nl < 1.0E-6) {
            break;
         }
         dx = nx / nl;
         dz = nz / nl;
         x += dx * STEP;
         z += dz * STEP;
         AlpineLayout.Sample t = layout.sample(x, z);
         if (t.wet() || t.distance() < t.width() + 1.5) {
            // reached a river, lake or the sea: run in at its level and end
            int join = t.wet() ? t.water() : Math.min(water, t.floor() - 1);
            xs[n] = (float)x;
            zs[n] = (float)z;
            ws[n] = Math.min(water, Math.max(join, water - 6));
            rs[n] = radius(layout, n, x, z);
            n++;
            joined = true;
            break;
         }
         int floor = t.floor();
         if (floor - 1 > water + 4) {
            break; // the ground rises ahead (a saddle): the creek ends in a pool
         }
         water = Math.min(water, floor - 1);
         xs[n] = (float)x;
         zs[n] = (float)z;
         ws[n] = water;
         rs[n] = radius(layout, n, x, z);
         n++;
      }
      if (n < (joined ? 5 : 10)) {
         return Creek.NONE;
      }
      float minX = Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
      for (int i = 0; i < n; i++) {
         minX = Math.min(minX, xs[i]);
         maxX = Math.max(maxX, xs[i]);
         minZ = Math.min(minZ, zs[i]);
         maxZ = Math.max(maxZ, zs[i]);
      }
      return new Creek(xs, zs, ws, rs, n, minX, minZ, maxX, maxZ);
   }

   /** Half width: a trickle at the spring, up to ~2 blocks lower down, varying along the way. */
   private static float radius(AlpineLayout layout, int index, double x, double z) {
      double grow = AlpineLayout.smooth(0.0, 70.0, index);
      return (float)(0.75 + 1.0 * grow + 0.3 * layout.noise(x / 23.0, z / 23.0, 9809L));
   }

   // ------------------------------------------------------------------ carving

   @Override
   public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
      ChunkGenerator gen = context.chunkGenerator();
      if (!(gen instanceof AlpineGenerator alpine) || streamsMod()) {
         return false;
      }
      WorldGenLevel level = context.level();
      ChunkPos cp = new ChunkPos(context.origin());
      ChunkAccess chunk = level.getChunk(cp.x, cp.z);
      // structures (villages, lookouts, camps) keep their ground
      for (var refs : chunk.getAllReferences().values()) {
         if (!refs.isEmpty()) {
            return false;
         }
      }
      long started = System.nanoTime();
      try {
         return carve(level, alpine.layout(), cp);
      } finally {
         NANOS.addAndGet(System.nanoTime() - started);
         if (CHUNKS.incrementAndGet() % 4096 == 0) {
            LogUtils.getLogger().debug("Frontier creeks: {} chunks, {} ms total, {} creek columns", CHUNKS.get(), NANOS.get() / 1000000L, CARVED.get());
         }
      }
   }

   private boolean carve(WorldGenLevel level, AlpineLayout layout, ChunkPos cp) {
      int x0 = cp.getMinBlockX(), z0 = cp.getMinBlockZ();
      int ccx = Math.floorDiv(x0 + 8, CELL), ccz = Math.floorDiv(z0 + 8, CELL);
      List<Creek> near = new ArrayList<>();
      for (int i = -REACH_CELLS; i <= REACH_CELLS; i++) {
         for (int j = -REACH_CELLS; j <= REACH_CELLS; j++) {
            Creek c = creek(layout, ccx + i, ccz + j);
            if (c.count() > 0 && c.maxX() + 6 >= x0 && c.minX() - 6 <= x0 + 15 && c.maxZ() + 6 >= z0 && c.minZ() - 6 <= z0 + 15) {
               near.add(c);
            }
         }
      }
      if (near.isEmpty()) {
         return false;
      }
      // per column: the nearest creek point (distance, water level, flow direction, how far the level steps around)
      float[] dist = new float[256];
      float[] rad = new float[256];
      int[] wat = new int[256];
      byte[] dir = new byte[256];
      byte[] run = new byte[256];
      java.util.Arrays.fill(dist, Float.MAX_VALUE);
      for (Creek c : near) {
         for (int k = 0; k + 1 < c.count(); k++) {
            float ax = c.x()[k], az = c.z()[k], bx = c.x()[k + 1], bz = c.z()[k + 1];
            float r = Math.max(c.radius()[k], c.radius()[k + 1]) + 3.0F;
            if (Math.max(ax, bx) + r < x0 || Math.min(ax, bx) - r > x0 + 15 || Math.max(az, bz) + r < z0 || Math.min(az, bz) - r > z0 + 15) {
               continue;
            }
            float sx = bx - ax, sz = bz - az, len2 = sx * sx + sz * sz;
            for (int lx = 0; lx < 16; lx++) {
               for (int lz = 0; lz < 16; lz++) {
                  float px = x0 + lx + 0.5F, pz = z0 + lz + 0.5F;
                  float t = len2 < 1.0E-4F ? 0.0F : Math.max(0.0F, Math.min(1.0F, ((px - ax) * sx + (pz - az) * sz) / len2));
                  float qx = px - (ax + sx * t), qz = pz - (az + sz * t);
                  float d = (float)Math.sqrt(qx * qx + qz * qz);
                  int idx = lx * 16 + lz;
                  if (d < dist[idx]) {
                     int at = t < 0.5F ? k : k + 1;
                     dist[idx] = d;
                     rad[idx] = c.radius()[k] + (c.radius()[k + 1] - c.radius()[k]) * t;
                     wat[idx] = c.water()[at];
                     dir[idx] = (byte)(Math.abs(sx) > Math.abs(sz) ? (sx > 0 ? 3 : 2) : (sz > 0 ? 1 : 0));
                     int up = c.water()[Math.max(0, at - 3)] - c.water()[at];
                     int down = c.water()[at] - c.water()[Math.min(c.count() - 1, at + 2)];
                     run[idx] = (byte)Math.min(9, up * 2 + down);
                  }
               }
            }
         }
      }
      BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
      BlockState air = Blocks.AIR.defaultBlockState();
      BlockState waterState = Blocks.WATER.defaultBlockState();
      boolean any = false;
      // banks first (so channels cut into them stay open), then channels
      for (int pass = 0; pass < 2; pass++) {
         for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
               int idx = lx * 16 + lz;
               float d = dist[idx], r = rad[idx];
               if (d > r + 2.5F) {
                  continue;
               }
               int x = x0 + lx, z = z0 + lz;
               int w = wat[idx];
               int top = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
               if (pass == 0) {
                  if (d <= r) {
                     continue;
                  }
                  // bank: solid up to one above the water (so a spill can't leave the channel), lowered gently
                  // where it stands high right next to the water
                  BlockState fill = bankBlock(level, p.set(x, top, z));
                  for (int y = top + 1; y <= w + 1; y++) {
                     setIfOpen(level, p.set(x, y, z), fill);
                  }
                  if (d <= r + 1.3F && top > w + 1 && top <= w + 3) {
                     for (int y = top; y > w + 1; y--) {
                        level.setBlock(p.set(x, y, z), air, 2);
                     }
                     BlockState cap = bankBlock(level, p.set(x, w + 1, z));
                     level.setBlock(p.set(x, w + 1, z), cap.is(Blocks.DIRT) ? Blocks.GRASS_BLOCK.defaultBlockState() : cap, 2);
                  }
                  continue;
               }
               if (d > r) {
                  continue;
               }
               if (top > w + 7 || top < w - 6) {
                  continue; // never trench a mountain or wall up a ravine: the water finds its own way there
               }
               int depth = d < r * 0.45F && r > 1.4F ? 2 : 1;
               int bed = w - depth;
               // open the channel above the water
               for (int y = top; y > w; y--) {
                  level.setBlock(p.set(x, y, z), air, 2);
               }
               // bed
               double v = layout.variation(x, z, 9811L);
               BlockState bedState = v < 0.62 ? Blocks.GRAVEL.defaultBlockState()
                  : v < 0.80 ? Blocks.COBBLESTONE.defaultBlockState()
                  : v < 0.92 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
               level.setBlock(p.set(x, bed, z), bedState, 2);
               if (top < bed - 1) {
                  for (int y = top + 1; y < bed; y++) {
                     level.setBlock(p.set(x, y, z), Blocks.DIRT.defaultBlockState(), 2);
                  }
               }
               for (int y = bed + 1; y <= w; y++) {
                  level.setBlock(p.set(x, y, z), waterState, 2);
               }
               // a source beside open air (where the level steps down) spills: let vanilla water carry it
               if (spills(level, p.set(x, w, z))) {
                  level.scheduleTick(p.set(x, w, z), Fluids.WATER, 0);
               }
               // the Frontier's flowing-water surface and current on the runs
               if (run[idx] > 0 && level.getBlockState(p.set(x, w + 1, z)).isAir()) {
                  Direction f = switch (dir[idx]) {
                     case 0 -> Direction.NORTH;
                     case 1 -> Direction.SOUTH;
                     case 2 -> Direction.WEST;
                     default -> Direction.EAST;
                  };
                  level.setBlock(p.set(x, w + 1, z), flow(f, run[idx], x, z), 2);
               }
               CARVED.incrementAndGet();
               any = true;
            }
         }
      }
      return any;
   }

   private static void setIfOpen(WorldGenLevel level, BlockPos p, BlockState fill) {
      BlockState s = level.getBlockState(p);
      if (s.isAir() || s.canBeReplaced() && s.getFluidState().isEmpty()) {
         level.setBlock(p, fill, 2);
      }
   }

   private static BlockState bankBlock(WorldGenLevel level, BlockPos top) {
      BlockState s = level.getBlockState(top);
      if (s.is(Blocks.STONE) || s.is(Blocks.ANDESITE) || s.is(Blocks.DIORITE) || s.is(Blocks.GRANITE) || s.is(Blocks.TUFF) || s.is(Blocks.DEEPSLATE)) {
         return Blocks.STONE.defaultBlockState();
      }
      if (s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.POWDER_SNOW)) {
         return Blocks.SNOW_BLOCK.defaultBlockState();
      }
      return Blocks.DIRT.defaultBlockState();
   }

   private static boolean spills(WorldGenLevel level, BlockPos.MutableBlockPos at) {
      int x = at.getX(), y = at.getY(), z = at.getZ();
      for (Direction d : Direction.Plane.HORIZONTAL) {
         if (level.getBlockState(at.set(x + d.getStepX(), y, z + d.getStepZ())).isAir()) {
            at.set(x, y, z);
            return true;
         }
      }
      at.set(x, y, z);
      return false;
   }

   private static BlockState flow(Direction f, int run, int x, int z) {
      AlpineFlow block = (AlpineFlow)AlpineRegistration.FLOW.get();
      int strength = run >= 4 ? 2 : run >= 2 ? 1 : 0;
      int tx = Math.floorMod(f == Direction.NORTH ? x : f == Direction.SOUTH ? -x - 1 : f == Direction.EAST ? z : -z - 1, 4);
      int tz = Math.floorMod(f == Direction.NORTH ? z : f == Direction.SOUTH ? -z - 1 : f == Direction.EAST ? -x - 1 : x, 4);
      return block.defaultBlockState().setValue(AlpineFlow.FACING, f).setValue(AlpineFlow.STRENGTH, strength)
         .setValue(AlpineFlow.X, tx).setValue(AlpineFlow.Z, tz);
   }
}
