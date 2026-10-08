package com.formaworks.frontierhunts.landscape;

import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

/**
 * [1.1.9] Snow country: around and below the big snow-capped massifs the land lies under snow - not only the summits,
 * the foothills, the benches and the timbered slopes under the cliffs, as far out as the mountain is big.
 *
 * <p>How far the snow reaches is a cone hung off every high point: a summit that stands H blocks above the snow line
 * carries snow country about H / {@link #FALL} blocks out from it, so a lone peak barely brushed with snow brings
 * nothing and a great massif whitens the country for a kilometre or more around. The edge is never a line: it follows
 * the ground (ridges hold snow further out than valley floors), wanders with a slow noise, and breaks up into patches of
 * snow and bare ground for a stretch before the green country begins. Inside, it isn't all white either: rivers and lakes
 * stay open, the gorges, falls and grottos keep their own country, and here and there a sheltered pocket has melted out.
 *
 * <p>Only the biomes change (to the reserve's snowy pine forest where there's timber, snowy foothills where it's open);
 * the terrain itself is untouched. Snow then comes the way it does in every cold biome of the reserve: the first layer
 * when the land is made, built up into a real snowpack ({@code season.SnowCover}), deeper with every snowfall.
 *
 * <p>Only for worlds created with it ({@code "snow_country": true} in the world preset); a world made before keeps its
 * land as it was.
 */
public final class SnowCountry {
   /** lattice spacing (blocks) for the massif field, and how far a massif can reach */
   static final int CELL = 256;
   /** [1.2.0] the current shape of snow country (worlds keep the version they were made with) */
   static final int VERSION = 2;
   /** how far a massif can reach; snow country falls away from a summit by FALL height per block of distance */
   private final int reach;
   private final double fall, lift;
   private final int version;
   private static final ResourceKey<Biome> FOREST = key("snowy_pine_forest"), OPEN = key("snowy_foothills");
   /** biomes that keep their own country inside snow country (water, and the scenic features) */
   private static final java.util.Set<String> KEEP = java.util.Set.of("alpine_river", "coastal_ocean", "deep_ocean", "wild_coast", "cascade_gorge", "misty_falls",
      "hidden_grotto", "glacial_peaks", "snowy_pine_forest", "snowy_foothills");
   /** biomes that are timber: they become snowy pine forest, the rest snowy foothills */
   private static final java.util.Set<String> TIMBER = java.util.Set.of("cedar_valley", "pine_highlands", "birch_grove", "maple_woodland", "aspen_woodland",
      "riverwood", "mosswood", "woodland_lakeshore", "golden_aspen_grove", "autumn_maple_hollow", "larch_highlands", "alder_carr", "rowan_slope",
      "cottonwood_bottom", "verdant_karst");

   private final AlpineLayout layout;
   private final Holder<Biome> forest, open;
   private final ConcurrentHashMap<Long, Float> nodes = new ConcurrentHashMap<>();
   private final ConcurrentHashMap<Long, Float> massif = new ConcurrentHashMap<>();
   private final long seed;

   private SnowCountry(AlpineLayout layout, Holder<Biome> forest, Holder<Biome> open, int version) {
      this.layout = layout;
      this.forest = forest;
      this.open = open;
      this.version = version;
      // 1 (1.1.9): wide aprons under every snowy range. 2 (1.2.0): tighter, and some ranges carry none at all
      this.reach = version >= 2 ? 640 : 900;
      this.fall = version >= 2 ? 0.4 : 0.3;
      this.lift = version >= 2 ? 15.0 : 40.0;
      this.seed = AlpineLayout.mix(layout.seed() ^ 0x5A0C0FFEEL);
   }

   private static ResourceKey<Biome> key(String path) {
      return ResourceKey.create(net.minecraft.core.registries.Registries.BIOME, ResourceLocation.fromNamespaceAndPath("frontierhunts", path));
   }

   /** snow country for a world, or null if the world's biome list doesn't have the snowy biomes (an older preset) */
   static SnowCountry of(AlpineLayout layout, java.util.Collection<Holder<Biome>> biomes) {
      return of(layout, biomes, 1);
   }

   static SnowCountry of(AlpineLayout layout, java.util.Collection<Holder<Biome>> biomes, int version) {
      Holder<Biome> f = null, o = null;
      for (Holder<Biome> h : biomes) {
         if (h.is(FOREST)) {
            f = h;
         } else if (h.is(OPEN)) {
            o = h;
         }
      }
      return f == null || o == null ? null : new SnowCountry(layout, f, o, version);
   }

   // ============================================================================================ the massif field

   /** height of the land above the snow line at a lattice node (very low over water) */
   private float node(int i, int j) {
      long k = (long)i << 32 ^ j & 0xFFFFFFFFL;
      Float v = this.nodes.get(k);
      if (v == null) {
         AlpineLayout.Sample s = this.layout.sample((double)i * CELL, (double)j * CELL);
         v = s.water() > s.floor() ? -999.0F : (float)(s.ground() - s.snowLine());
         if (this.nodes.size() > 400000) {
            this.nodes.clear();
         }
         this.nodes.put(k, v);
      }
      return v;
   }

   /** the snow cone at a lattice node: the most any summit within reach lifts it, less the fall with distance */
   private float massifAt(int i, int j) {
      long k = (long)i << 32 ^ j & 0xFFFFFFFFL;
      Float v = this.massif.get(k);
      if (v == null) {
         int r = this.reach / CELL;
         float best = -999.0F;
         for (int dj = -r; dj <= r; dj++) {
            for (int di = -r; di <= r; di++) {
               double d = Math.sqrt(di * di + dj * dj) * CELL;
               if (d > this.reach) {
                  continue;
               }
               float h = this.node(i + di, j + dj);
               if (h > -900.0F) {
                  best = (float)Math.max(best, h - this.fall * d);
               }
            }
         }
         v = best;
         if (this.massif.size() > 200000) {
            this.massif.clear();
         }
         this.massif.put(k, v);
      }
      return v;
   }

   /** the massif field at a block, smoothly between lattice nodes */
   double massif(double x, double z) {
      double fx = x / CELL, fz = z / CELL;
      int i = (int)Math.floor(fx), j = (int)Math.floor(fz);
      double tx = fx - i, tz = fz - j;
      double a = this.massifAt(i, j), b = this.massifAt(i + 1, j), c = this.massifAt(i, j + 1), d = this.massifAt(i + 1, j + 1);
      return lerp(tz, lerp(tx, a, b), lerp(tx, c, d));
   }

   // ============================================================================================ noise

   private static double lerp(double t, double a, double b) {
      return a + (b - a) * t;
   }

   private double hash01(long i, long j, long salt) {
      long h = AlpineLayout.mix(this.seed ^ i * 0x9E3779B97F4A7C15L ^ j * 0xC2B2AE3D27D4EB4FL ^ salt);
      return (h >>> 11) * 0x1.0p-53;
   }

   /** smooth value noise in 0..1 with features about {@code scale} blocks across */
   private double value(double x, double z, double scale, long salt) {
      double fx = x / scale, fz = z / scale;
      long i = (long)Math.floor(fx), j = (long)Math.floor(fz);
      double tx = fx - i, tz = fz - j;
      tx = tx * tx * (3.0 - 2.0 * tx);
      tz = tz * tz * (3.0 - 2.0 * tz);
      double a = this.hash01(i, j, salt), b = this.hash01(i + 1, j, salt), c = this.hash01(i, j + 1, salt), d = this.hash01(i + 1, j + 1, salt);
      return lerp(tz, lerp(tx, a, b), lerp(tx, c, d));
   }

   // ============================================================================================ deciding

   /** 0 green country .. 1 deep in snow country, at a block whose terrain sample is {@code s} */
   double weight(double x, double z, AlpineLayout.Sample s) {
      double m = this.massif(x, z);
      if (m < -400.0) {
         return 0.0;
      }
      double wander = (this.value(x, z, 420.0, 1L) - 0.5) * 70.0 + (this.value(x, z, 130.0, 2L) - 0.5) * 30.0;
      double ground = 0.25 * Math.max(-260.0, Math.min(160.0, s.ground() - s.snowLine() + 200.0));
      double v = m + this.lift + wander + ground;
      if (this.version >= 2) {
         // not every range: about a third of the snowy ranges carry no snow country below them at all
         double gate = this.value(x, z, 1900.0, 7L);
         double keep = Math.max(0.0, Math.min(1.0, (gate - 0.26) / 0.14));
         v -= (1.0 - keep * keep * (3.0 - 2.0 * keep)) * 220.0;
      }
      return Math.max(0.0, Math.min(1.0, (v + 40.0) / 80.0));
   }

   /** the biome for a biome cell (quart coordinates), given the one the reserve's terrain chose */
   Holder<Biome> biome(Holder<Biome> chosen, int qx, int qz) {
      String path = chosen.unwrapKey().map(k -> k.location().getPath()).orElse("");
      int d = this.decide(path, qx, qz);
      return d == 0 ? chosen : d == 1 ? this.forest : this.open;
   }

   /** 0 keep the reserve's biome, 1 snowy pine forest, 2 snowy foothills */
   int decide(String path, int qx, int qz) {
      if (KEEP.contains(path)) {
         return 0;
      }
      double x = qx * 4 + 2, z = qz * 4 + 2;
      AlpineLayout.Sample s = this.layout.sample(x, z);
      if (s.water() > s.floor()) {
         return 0; // open water stays open
      }
      double w = this.weight(x, z, s);
      if (w <= 0.0) {
         return 0;
      }
      // the edge breaks up into patches of snow and bare ground; deep inside, a few sheltered pockets melt out
      double patch = this.value(x, z, 22.0, 3L) * 0.75 + this.value(x, z, 7.0, 4L) * 0.25;
      if (w < 1.0 && patch > w) {
         return 0;
      }
      if (this.value(x, z, 60.0, 5L) > 0.86 && s.ground() < s.snowLine() - 120.0) {
         return 0;
      }
      return TIMBER.contains(path) || s.forest() > 0.55 ? 1 : 2;
   }

   /** for the offline harness */
   static SnowCountry forTest(AlpineLayout layout, int version) {
      return new SnowCountry(layout, null, null, version);
   }
}
