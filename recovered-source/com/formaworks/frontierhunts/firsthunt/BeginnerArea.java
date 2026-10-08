package com.formaworks.frontierhunts.firsthunt;

import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.hunting.routine.HomeRange;
import com.formaworks.frontierhunts.hunting.routine.RoutineStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.Tags;

/**
 * [fharea] Where a new hunter is sent on the first hunt: an easy, open, gentle circle with deer in it, never a snowy
 * mountain, a cliff, a bog or the far side of a lake.
 *
 * <p><b>Biomes</b> ({@link #tier}): open grass first (plains, meadows, parkland, glades), then easy open woods (birch,
 * maple, aspen, oak, flower forest), then fair ground (taiga, highland pine, moor) only when nothing better has deer.
 * Snowy, icy, peaks, slopes, mountains, windswept, badlands, swamps and bogs, oceans, rivers, beaches, jungles, dark and
 * old-growth forests, caves and karst are never used. The mod's own biomes are listed by name; anything else (vanilla,
 * other mods) is judged by the common {@code c:} and {@code minecraft:} tags and by its name, so a modded "frozen_cliffs"
 * or "jungle_mountains" is turned down as well.
 *
 * <p><b>Ground</b> ({@link #judge}): a 16-block grid over the circle (49 points). Where the land is loaded the surface
 * height (trees stepped through), water and canopy are read from the chunk's heightmaps; the circle must be gentle
 * (small height range, no cliff steps between neighbours), mostly dry, and not one unbroken dark canopy. Land that isn't
 * loaded is never loaded or generated for this: its biome comes from the biome source (pure maths) and the ground is
 * judged when the hunter gets there ({@link FirstHunt} re-checks the area on arrival and moves it if it turns out rough).
 *
 * <p><b>The way there</b> ({@link #path}): a sample every 16 blocks on the straight line from the hunter: no long water
 * crossing (a lake or the sea), no ocean, and not far above the hunter.
 *
 * <p>Everything runs on the server thread, a few times a minute at most (the first-hunt rate limits), inside a time
 * budget; it reuses its arrays and allocates almost nothing per sample.
 */
public final class BeginnerArea {
   /** biome tiers: 0 never, 1 open grass (best), 2 easy open woods, 3 fair (only if nothing better) */
   public static final int NEVER = 0, OPEN = 1, WOODS = 2, FAIR = 3;
   public static final int RADIUS = 64;
   /** the grid: 16 blocks between points, 9 x 9 cut to the circle */
   static final int STEP = 16, HALF = 4;
   /** the circle must be gentle: height range across it, a cliff between neighbours, the share of steep neighbours */
   public static final int MAX_RANGE = 30, MAX_STEP = 10, STEEP = 5;
   static final double MAX_STEEP_SHARE = 0.22, MAX_WATER = 0.3, MAX_BAD = 0.2;
   /** how far: the ideal walk, the most we prefer, the most at all; the most the area may sit above the hunter */
   public static final double IDEAL = 130.0, PREFER = 250.0, FARTHEST = 340.0;
   public static final int MAX_CLIMB = 36;
   /** time budget for one choice (ns) */
   static final long BUDGET = 25_000_000L; // [1.3.0] a one-off look when an area is assigned (rate-limited): enough to see past a few rough groups

   static final TagKey<Biome> C_SLOPE = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("c", "is_slope"));
   static final TagKey<Biome> C_PEAK = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("c", "is_peak"));
   static final TagKey<Biome> C_PLAINS_FH = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("frontierhunts", "regions/plains"));
   static final TagKey<Biome> C_TUNDRA_FH = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("frontierhunts", "regions/tundra"));
   static final TagKey<Biome> C_HIGHLANDS_FH = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("frontierhunts", "regions/highlands"));
   static final TagKey<Biome> C_WETLANDS_FH = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("frontierhunts", "regions/wetlands"));
   static final TagKey<Biome> C_SHADOW_FH = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("frontierhunts", "regions/shadow_woods"));

   /** the mod's own biomes, by name (the Alpine world is made of only these) */
   static final Map<String, Integer> OWN = new HashMap<>();
   /** vanilla biomes where the tags alone would get it wrong */
   static final Map<String, Integer> VANILLA = new HashMap<>();

   static {
      for (String s : new String[]{"valley_meadow", "wildflower_glade", "aspen_parkland", "sagebrush_bench"}) {
         OWN.put(s, OPEN);
      }
      for (String s : new String[]{"birch_grove", "maple_woodland", "aspen_woodland", "golden_aspen_grove", "autumn_maple_hollow", "cedar_valley",
         "riverwood", "fireweed_burn", "birch_heath", "woodland_lakeshore", "cottonwood_bottom"}) {
         OWN.put(s, WOODS);
      }
      for (String s : new String[]{"alpine_meadow", "heather_moor", "pine_highlands", "larch_highlands"}) {
         OWN.put(s, FAIR);
      }
      for (String s : new String[]{"snowy_foothills", "snowy_pine_forest", "glacial_peaks", "alpine_fellfield", "boulder_talus", "limestone_bluff",
         "rocky_foothills", "rowan_slope", "cascade_gorge", "misty_falls", "hidden_grotto", "mosswood", "verdant_karst", "alpine_river",
         "marsh_meadow", "fern_wetland", "muskeg_bog", "alder_carr", "coastal_ocean", "deep_ocean", "wild_coast", "training_grounds"}) {
         OWN.put(s, NEVER);
      }
      for (String s : new String[]{"plains", "sunflower_plains"}) {
         VANILLA.put(s, OPEN);
      }
      for (String s : new String[]{"forest", "birch_forest", "flower_forest", "old_growth_birch_forest", "meadow", "savanna"}) {
         VANILLA.put(s, WOODS);
      }
      for (String s : new String[]{"taiga", "cherry_grove", "sparse_jungle"}) {
         VANILLA.put(s, FAIR);
      }
      for (String s : new String[]{"dark_forest", "old_growth_pine_taiga", "old_growth_spruce_taiga", "grove", "snowy_slopes", "jagged_peaks",
         "frozen_peaks", "stony_peaks", "windswept_hills", "windswept_forest", "windswept_gravelly_hills", "windswept_savanna", "savanna_plateau",
         "swamp", "mangrove_swamp", "jungle", "bamboo_jungle", "badlands", "wooded_badlands", "eroded_badlands", "desert", "snowy_plains",
         "ice_spikes", "snowy_taiga", "snowy_beach", "stony_shore", "beach", "river", "frozen_river", "mushroom_fields", "deep_dark",
         "dripstone_caves", "lush_caves"}) {
         VANILLA.put(s, NEVER);
      }
   }

   /** name parts that rule a biome out (modded biomes without good tags) */
   static final String[] BAD_WORDS = {"snow", "frozen", "frost", "ice_", "_ice", "icy", "glacier", "glacial", "tundra", "polar", "peak", "summit",
      "mountain", "slope", "cliff", "crag", "spire", "canyon", "gorge", "ravine", "bluff", "talus", "scree", "volcan", "badland", "mesa", "desert",
      "dune", "swamp", "marsh", "bog", "mire", "wetland", "ocean", "river", "beach", "coast", "shore", "reef", "lake", "jungle",
      "dark", "shadow", "dead", "spooky", "haunt", "cave", "grotto", "deep", "karst", "old_growth", "mushroom", "waste", "lava"};
   static final String[] OPEN_WORDS = {"plain", "meadow", "prairie", "grassland", "steppe", "field", "pasture", "glade", "clearing", "parkland",
      "savanna", "shrubland", "heath"};
   static final String[] WOOD_WORDS = {"forest", "wood", "grove", "orchard", "copse", "birch", "maple", "aspen", "oak"};

   /** tier + traits by biome key, rebuilt when tags change (cleared from {@link FirstHunt}) */
   static final Map<ResourceKey<Biome>, Integer> CACHE = new HashMap<>();

   private BeginnerArea() {
   }

   public static void clearCache() {
      synchronized (CACHE) {
         CACHE.clear();
      }
   }

   public static int tier(Holder<Biome> b) {
      return traits(b) & 15;
   }

   /** the biome's tier (low 4 bits), snowy (16) and river (32), worked out once per biome */
   static int traits(Holder<Biome> b) {
      ResourceKey<Biome> key = b.unwrapKey().orElse(null);
      if (key == null) {
         return traits0(b, "");
      }
      synchronized (CACHE) {
         Integer t = CACHE.get(key);
         if (t == null) {
            t = traits0(b, key.location().toString());
            CACHE.put(key, t);
         }
         return t;
      }
   }

   static int traits0(Holder<Biome> b, String id) {
      return tier0(b, id) | (snowy(b) ? 16 : 0) | (river(b) ? 32 : 0);
   }

   /** true for biomes with snow or ice on the ground in any season (not the seasonal snow: the biome itself) */
   public static boolean snowy(Holder<Biome> b) {
      String id = b.unwrapKey().map(k -> k.location().getPath()).orElse("");
      return b.is(Tags.Biomes.IS_SNOWY) || b.is(Tags.Biomes.IS_ICY) || b.is(Tags.Biomes.IS_AQUATIC_ICY) || b.is(C_TUNDRA_FH) || b.value().getBaseTemperature() < 0.15F
         || id.contains("snow") || id.contains("frozen") || id.contains("ice_") || id.endsWith("_ice") || id.contains("glacia") || id.contains("tundra");
   }

   static boolean river(Holder<Biome> b) {
      return b.is(BiomeTags.IS_RIVER) || b.is(Tags.Biomes.IS_RIVER) || b.unwrapKey().map(k -> k.location().getPath().contains("river")).orElse(false);
   }

   static int tier0(Holder<Biome> b, String id) {
      int colon = id.indexOf(':');
      String ns = colon < 0 ? "" : id.substring(0, colon), path = colon < 0 ? id : id.substring(colon + 1);
      path = path.toLowerCase(Locale.ROOT);
      if (ns.equals("frontierhunts") && OWN.containsKey(path)) {
         return OWN.get(path);
      }
      if (ns.equals("minecraft") && VANILLA.containsKey(path)) {
         return VANILLA.get(path);
      }
      // anything else: never where the tags or the name say rough, cold, wet or dark ...
      if (snowy(b) || b.is(Tags.Biomes.IS_MOUNTAIN) || b.is(Tags.Biomes.IS_MOUNTAIN_PEAK) || b.is(Tags.Biomes.IS_MOUNTAIN_SLOPE) || b.is(C_SLOPE)
         || b.is(C_PEAK) || b.is(BiomeTags.IS_MOUNTAIN) || b.is(BiomeTags.IS_HILL) || b.is(Tags.Biomes.IS_WINDSWEPT) || b.is(Tags.Biomes.IS_BADLANDS)
         || b.is(BiomeTags.IS_BADLANDS) || b.is(Tags.Biomes.IS_SWAMP) || b.is(Tags.Biomes.IS_OCEAN) || b.is(BiomeTags.IS_OCEAN) || b.is(BiomeTags.IS_DEEP_OCEAN)
         || b.is(Tags.Biomes.IS_RIVER) || b.is(BiomeTags.IS_RIVER) || b.is(Tags.Biomes.IS_BEACH) || b.is(BiomeTags.IS_BEACH) || b.is(Tags.Biomes.IS_STONY_SHORES)
         || b.is(Tags.Biomes.IS_JUNGLE) || b.is(BiomeTags.IS_JUNGLE) || b.is(Tags.Biomes.IS_OLD_GROWTH) || b.is(Tags.Biomes.IS_DENSE_VEGETATION_OVERWORLD)
         || b.is(Tags.Biomes.IS_CAVE) || b.is(Tags.Biomes.IS_UNDERGROUND) || b.is(Tags.Biomes.IS_WASTELAND) || b.is(Tags.Biomes.IS_DEAD)
         || b.is(Tags.Biomes.IS_SPOOKY) || b.is(Tags.Biomes.IS_MUSHROOM) || b.is(Tags.Biomes.IS_DESERT) || b.is(Tags.Biomes.IS_VOID)
         || b.is(Tags.Biomes.IS_AQUATIC) || b.is(Tags.Biomes.IS_NETHER) || b.is(Tags.Biomes.IS_END) || b.is(C_WETLANDS_FH) || b.is(C_SHADOW_FH)) {
         return NEVER;
      }
      for (String w : BAD_WORDS) {
         if (path.contains(w)) {
            return NEVER;
         }
      }
      // ... then open grass, easy woods, fair ground
      boolean highland = b.is(C_HIGHLANDS_FH) || b.is(Tags.Biomes.IS_PLATEAU) || path.contains("highland") || path.contains("upland");
      if (b.is(Tags.Biomes.IS_PLAINS) || b.is(C_PLAINS_FH) || b.is(Tags.Biomes.IS_SAVANNA)) {
         return highland ? FAIR : OPEN;
      }
      for (String w : OPEN_WORDS) {
         if (path.contains(w)) {
            return highland ? FAIR : OPEN;
         }
      }
      if (b.is(Tags.Biomes.IS_BIRCH_FOREST) || b.is(Tags.Biomes.IS_FLOWER_FOREST) || b.is(Tags.Biomes.IS_FOREST) || b.is(BiomeTags.IS_FOREST)
         || b.is(Tags.Biomes.IS_DECIDUOUS_TREE)) {
         return highland ? FAIR : WOODS;
      }
      for (String w : WOOD_WORDS) {
         if (path.contains(w)) {
            return highland ? FAIR : WOODS;
         }
      }
      if (b.is(Tags.Biomes.IS_TAIGA) || b.is(BiomeTags.IS_TAIGA) || b.is(Tags.Biomes.IS_CONIFEROUS_TREE) || b.is(FirstHunt.HABITAT)) {
         return FAIR;
      }
      return NEVER;
   }

   // ============================================================================================ sampling

   /** One look at a circle. Counts are over the grid points; heights only where the land is loaded. */
   public static final class Site {
      public int x, z, y = Integer.MIN_VALUE;
      public int centreTier, samples, known, bad, snowy, river, water, canopy, pairs, steep, maxStep, range, tierSum;
      /** [1.3.0] ground heights estimated from the world generator where the land isn't loaded (range check only) */
      public int estimated;
      public boolean ok;
      /** why it was turned down (for the debug line / tests) */
      public String why = "";
      public double score;
      public String centreBiome = "";

      public boolean verified() {
         return this.known >= this.samples * 4 / 5;
      }

      public String describe() {
         return this.centreBiome + " (tier " + this.centreTier + "), height range " + (this.known > 0 ? this.range : -1) + ", biggest step " + this.maxStep
            + ", steep " + this.steep + "/" + this.pairs + ", water " + this.water + "/" + this.known + ", canopy " + this.canopy + "/" + this.known
            + ", rough biomes " + this.bad + "/" + this.samples + ", river " + this.river + ", snowy " + this.snowy + ", ground known " + this.known + "/" + this.samples
            + (this.estimated > 0 ? " (+" + this.estimated + " estimated)" : "")
            + (this.ok ? ", easy" : ", turned down: " + this.why);
      }
   }

   private static final int[][] H = new int[2 * HALF + 1][2 * HALF + 1];
   private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();
   private static final int UNKNOWN = Integer.MIN_VALUE;

   /** the biome at a block without loading anything: the loaded chunk's own, else the biome source's (maths, no chunk) */
   static Holder<Biome> biome(ServerLevel level, LevelChunk chunk, int x, int y, int z) {
      int qx = QuartPos.fromBlock(x), qy = QuartPos.fromBlock(y), qz = QuartPos.fromBlock(z);
      if (chunk != null) {
         return chunk.getNoiseBiome(qx, qy, qz);
      }
      return level.getChunkSource().getGenerator().getBiomeSource().getNoiseBiome(qx, qy, qz, level.getChunkSource().randomState().sampler());
   }

   /** the ground height at x,z in a loaded chunk (stepping down through trunks and leaves), {@link #UNKNOWN} if not loaded */
   static int ground(LevelChunk chunk, int x, int z, int[] flags) {
      if (chunk == null) {
         return UNKNOWN;
      }
      int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
      int crown = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15);
      int y = top;
      int min = chunk.getMinBuildHeight();
      for (int i = 0; i < 40 && y > min; i++) {
         BlockState s = chunk.getBlockState(POS.set(x, y, z));
         if (s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.isAir()) {
            y--;
         } else {
            break;
         }
      }
      BlockState s = chunk.getBlockState(POS.set(x, y, z));
      flags[0] = s.getFluidState().is(FluidTags.WATER) || s.is(BlockTags.ICE) ? 1 : 0;
      flags[1] = crown > y + 3 ? 1 : 0; // leaves overhead: under trees
      return y;
   }

   private static final int[] FLAGS = new int[2];

   /** [1.3.0] estimated surface heights (x,z packed), bounded */
   private static final java.util.HashMap<Long, Integer> ESTIMATES = new java.util.HashMap<>();

   /** the world generator's surface height at x,z (no chunk is loaded or generated), {@link #UNKNOWN} if it can't say */
   static int estimate(ServerLevel level, int x, int z) {
      long key = (long)x << 32 ^ z & 0xFFFFFFFFL;
      Integer e = ESTIMATES.get(key);
      if (e != null) {
         return e;
      }
      int y;
      try {
         y = level.getChunkSource().getGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, level.getChunkSource().randomState()) - 1;
      } catch (RuntimeException ex) {
         y = UNKNOWN;
      }
      if (ESTIMATES.size() > 8192) {
         ESTIMATES.clear();
      }
      ESTIMATES.put(key, y);
      return y;
   }

   static LevelChunk chunk(ServerLevel level, int x, int z) {
      return level.getChunkSource().getChunkNow(x >> 4, z >> 4);
   }

   /** Looks at the circle of radius {@code r} around x,z: biomes everywhere, ground where it is loaded. */
   public static Site judge(ServerLevel level, int cx, int cz, int r) {
      Site s = new Site();
      s.x = cx;
      s.z = cz;
      int seaY = level.getSeaLevel() + 4;
      int half = Math.min(HALF, r / STEP);
      int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
      for (int i = -HALF; i <= HALF; i++) {
         for (int j = -HALF; j <= HALF; j++) {
            H[i + HALF][j + HALF] = UNKNOWN;
            if (Math.abs(i) > half || Math.abs(j) > half || (i * i + j * j) * STEP * STEP > r * r) {
               continue;
            }
            int x = cx + i * STEP, z = cz + j * STEP;
            LevelChunk c = chunk(level, x, z);
            int g = ground(c, x, z, FLAGS);
            Holder<Biome> b = biome(level, c, x, g == UNKNOWN ? seaY : g, z);
            int tr = traits(b), t = tr & 15;
            boolean cold = (tr & 16) != 0;
            s.samples++;
            if (t != NEVER) {
               s.tierSum += t;
            } else if ((tr & 32) != 0 && !cold) {
               s.river++; // a stream through the circle is fine (deer drink there), just not the middle of it
            } else {
               s.bad++;
            }
            if (cold) {
               s.snowy++;
            }
            if (i == 0 && j == 0) {
               s.centreTier = t;
               s.centreBiome = b.unwrapKey().map(k -> k.location().toString()).orElse("?");
               s.y = g;
            }
            if (g != UNKNOWN) {
               s.known++;
               H[i + HALF][j + HALF] = g;
               lo = Math.min(lo, g);
               hi = Math.max(hi, g);
               s.water += FLAGS[0];
               s.canopy += FLAGS[1];
            } else if ((i & 1) == 0 && (j & 1) == 0) {
               // [1.3.0] land not loaded (far from any player): the generator's own surface height, so a hilly spot is never
               // chosen blind; every other point only, cached, and no chunk is loaded or generated
               int e = estimate(level, x, z);
               if (e != UNKNOWN) {
                  s.estimated++;
                  lo = Math.min(lo, e);
                  hi = Math.max(hi, e);
               }
            }
         }
      }
      // neighbour steps (right and down)
      for (int i = 0; i <= 2 * HALF; i++) {
         for (int j = 0; j <= 2 * HALF; j++) {
            int h = H[i][j];
            if (h == UNKNOWN) {
               continue;
            }
            if (i < 2 * HALF && H[i + 1][j] != UNKNOWN) {
               step(s, Math.abs(H[i + 1][j] - h));
            }
            if (j < 2 * HALF && H[i][j + 1] != UNKNOWN) {
               step(s, Math.abs(H[i][j + 1] - h));
            }
         }
      }
      s.range = s.known + s.estimated > 0 ? hi - lo : 0;
      s.ok = verdict(s);
      return s;
   }

   private static void step(Site s, int dh) {
      s.pairs++;
      s.maxStep = Math.max(s.maxStep, dh);
      if (dh > STEEP) {
         s.steep++;
      }
   }

   static boolean verdict(Site s) {
      if (s.centreTier == NEVER) {
         s.why = "rough or wet biome at the centre (" + s.centreBiome + ")";
         return false;
      }
      if (s.snowy > 0) {
         s.why = "snowy ground in the circle";
         return false;
      }
      if (s.river > s.samples * MAX_WATER) {
         s.why = "mostly river";
         return false;
      }
      if (s.bad > s.samples * MAX_BAD) {
         s.why = "too much rough, wet or dark land in the circle";
         return false;
      }
      if (s.known + s.estimated >= 8 && s.range > MAX_RANGE) { // [1.3.0] estimated heights count for the overall range
         s.why = "too steep: " + s.range + " blocks from the lowest to the highest point" + (s.estimated > 0 ? " (partly estimated)" : "");
         return false;
      }
      if (s.known >= 8) {
         if (s.maxStep > MAX_STEP) {
            s.why = "a cliff or a steep bank (" + s.maxStep + " blocks in 16)";
            return false;
         }
         if (s.pairs > 0 && s.steep > s.pairs * MAX_STEEP_SHARE) {
            s.why = "broken, hilly ground";
            return false;
         }
         if (s.water > s.known * MAX_WATER) {
            s.why = "mostly water";
            return false;
         }
         if (s.canopy > s.known * 0.92 && s.centreTier >= WOODS) {
            s.why = "unbroken dark canopy: hard to see deer";
            return false;
         }
      }
      return true;
   }

   /** lower is better: open over woods over fair, gentle, dry, a forest edge rather than solid canopy, judged ground over guessed */
   static double terrainScore(Site s) {
      double score = switch (s.centreTier) {
         case OPEN -> 0.0;
         case WOODS -> 14.0;
         default -> 45.0;
      };
      int good = Math.max(1, s.samples - s.bad - s.river);
      score += (s.tierSum / (double)good - 1.0) * 18.0;
      score += s.bad * 6.0 + s.river * 2.0;
      if (s.known > 0) {
         double canopy = s.canopy / (double)s.known;
         score += s.range * 1.3 + s.steep * 5.0 + s.water * 60.0 / s.known;
         score += canopy > 0.75 ? 40.0 * (canopy - 0.75) / 0.25 + 10.0 : canopy >= 0.12 && canopy <= 0.6 ? -8.0 : 0.0; // a forest edge is ideal
      }
      score += (1.0 - s.known / (double)Math.max(1, s.samples)) * 30.0; // ground not seen yet: a little less sure
      return score;
   }

   // ============================================================================================ the way there

   /** penalty for the walk from x0,z0 (ground y0) to the area; NaN = no (across water or the sea, or a climb) */
   public static double path(ServerLevel level, int x0, int y0, int z0, Site s) {
      double dx = s.x - x0, dz = s.z - z0;
      double dist = Math.hypot(dx, dz);
      int n = (int)Math.min(30, dist / STEP);
      int seaY = level.getSeaLevel() + 4;
      int run = 0, oceanRun = 0, worstRun = 0;
      int prev = y0;
      double ascent = 0.0;
      for (int k = 1; k < n; k++) {
         double f = k / (double)n;
         int x = x0 + (int)Math.round(dx * f), z = z0 + (int)Math.round(dz * f);
         LevelChunk c = chunk(level, x, z);
         int g = ground(c, x, z, FLAGS);
         Holder<Biome> b = biome(level, c, x, g == UNKNOWN ? seaY : g, z);
         boolean ocean = b.is(BiomeTags.IS_OCEAN) || b.is(Tags.Biomes.IS_OCEAN) || b.is(BiomeTags.IS_DEEP_OCEAN);
         boolean wet = g != UNKNOWN ? FLAGS[0] == 1 : ocean;
         run = wet ? run + 1 : 0;
         oceanRun = ocean ? oceanRun + 1 : 0;
         worstRun = Math.max(worstRun, run);
         if (oceanRun >= 3 || run >= 4) {
            return Double.NaN; // a lake or the sea in the way
         }
         if (g != UNKNOWN && prev != UNKNOWN && !wet) {
            ascent += Math.max(0, g - prev);
            prev = g;
         } else if (g != UNKNOWN) {
            prev = g;
         }
      }
      if (s.y != UNKNOWN && y0 != UNKNOWN && s.y - y0 > MAX_CLIMB) {
         return Double.NaN; // up a mountain
      }
      double climb = s.y != UNKNOWN && y0 != UNKNOWN ? Math.max(0, s.y - y0) : 0;
      return ascent * 0.6 + climb * 1.2 + worstRun * 12.0;
   }

   static double distanceScore(double dist) {
      double score = Math.abs(dist - IDEAL) * 0.5;
      if (dist < 40.0) {
         score += 90.0; // right on top of the hunter: a short walk teaches more
      }
      if (dist > PREFER) {
         score += (dist - PREFER) * 2.5;
      }
      return score;
   }

   // ============================================================================================ choosing

   /** The chosen area: centre, the advice line and whether the ground has been seen. {@code rough}: deer known, but only in hard country. */
   public record Choice(int x, int z, int advice, boolean verified, Site site) {
   }

   /** what {@link #choose} found when it chose nothing */
   public static boolean lastRough;

   /**
    * Picks the best easy circle that has whitetails in it: around live whitetails in loaded land and around established
    * home ranges, a few centres per group 26 blocks off the animals (the circle is never centred on them). Null if there
    * is none (and {@link #lastRough} says whether deer were known but only in hard country).
    */
   public static Choice choose(ServerLevel level, BlockPos hunter, boolean avoidOld, int oldX, int oldZ) {
      long t0 = System.nanoTime();
      lastRough = false;
      List<int[]> spots = new ArrayList<>(); // x, z, anchor kind (-1 live deer)
      Map<Long, Boolean> cells = new HashMap<>();
      for (Whitetail deer : level.getEntitiesOfClass(Whitetail.class, new AABB(hunter).inflate(FARTHEST, 96.0, FARTHEST),
         x -> x.isAlive() && !x.downed() && x.species() == GameSpecies.WHITETAIL)) {
         BlockPos dp = deer.blockPosition();
         if (cells.put(cell(dp.getX(), dp.getZ()), Boolean.TRUE) == null) {
            spots.add(new int[]{dp.getX(), dp.getZ(), -1});
         }
      }
      long now = level.getGameTime();
      for (HomeRange h : RoutineStore.of(level).near(GameSpecies.WHITETAIL, hunter, FARTHEST + 60.0)) {
         if (!h.ready() || now - h.lastSeen > 96000L && now >= h.lastSeen) {
            continue;
         }
         int which = h.anchor(HomeRange.FEED) != null ? HomeRange.FEED : HomeRange.BED;
         BlockPos ap = h.anchor(which);
         if (ap != null && cells.put(cell(ap.getX(), ap.getZ()), Boolean.TRUE) == null) {
            spots.add(new int[]{ap.getX(), ap.getZ(), which});
         }
      }
      if (spots.isEmpty()) {
         return null;
      }
      // nearest to the ideal walk first, at most 16 groups
      spots.sort((a, b) -> Double.compare(distanceScore(Math.hypot(a[0] - hunter.getX(), a[1] - hunter.getZ())),
         distanceScore(Math.hypot(b[0] - hunter.getX(), b[1] - hunter.getZ()))));
      if (spots.size() > 16) {
         spots = spots.subList(0, 16);
      }
      int y0 = groundAt(level, hunter.getX(), hunter.getZ(), hunter.getY());
      Choice best = null;
      double bestScore = Double.MAX_VALUE;
      double phase = level.random.nextDouble() * Math.PI * 2.0;
      int judged = 0, turnedDown = 0;
      for (int[] spot : spots) {
         if (judged >= 12 && System.nanoTime() - t0 > BUDGET) {
            break; // within budget: what's been seen so far will do
         }
         for (int k = 0; k < 6; k++) {
            double a = phase + k * Math.PI / 3.0;
            int cx = spot[0] + (int)Math.round(Math.cos(a) * 26.0), cz = spot[1] + (int)Math.round(Math.sin(a) * 26.0);
            double dist = Math.hypot(cx - hunter.getX(), cz - hunter.getZ());
            if (dist > FARTHEST) {
               continue;
            }
            // cheap first look: the centre's biome
            LevelChunk c = chunk(level, cx, cz);
            int g = ground(c, cx, cz, FLAGS);
            if (tier(biome(level, c, cx, g == UNKNOWN ? level.getSeaLevel() + 4 : g, cz)) == NEVER) {
               turnedDown++;
               continue;
            }
            Site s = judge(level, cx, cz, RADIUS);
            judged++;
            if (!s.ok) {
               turnedDown++;
               continue;
            }
            double walk = path(level, hunter.getX(), y0, hunter.getZ(), s);
            if (Double.isNaN(walk)) {
               turnedDown++;
               continue;
            }
            double score = distanceScore(dist) + terrainScore(s) + walk;
            if (avoidOld && Math.hypot(cx - oldX, cz - oldZ) < 128.0) {
               score += 10000.0;
            }
            s.score = score;
            if (score < bestScore) {
               bestScore = score;
               int advice = FirstHunt.adviceFor(s.centreBiome, s.centreTier, spot[2]);
               best = new Choice(cx, cz, advice, s.verified(), s);
            }
         }
      }
      lastRough = best == null && (turnedDown > 0 || !spots.isEmpty()); // deer known, but only where a beginner shouldn't be sent
      return best;
   }

   static long cell(int x, int z) {
      return (long)Math.floorDiv(x, 32) << 32 ^ Math.floorDiv(z, 32) & 0xFFFFFFFFL;
   }

   static int groundAt(ServerLevel level, int x, int z, int fallback) {
      int g = ground(chunk(level, x, z), x, z, FLAGS);
      return g == UNKNOWN ? fallback : g;
   }

   /** debug / tests: the judge's one-line verdict on a circle */
   public static String explain(ServerLevel level, int x, int z) {
      return judge(level, x, z, RADIUS).describe();
   }
}
