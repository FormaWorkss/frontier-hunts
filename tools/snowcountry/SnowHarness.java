package com.formaworks.frontierhunts.landscape;

import java.io.*;

/**
 * [1.1.9] Offline check of snow country on the real alpine terrain: writes, per sampled point, the terrain biome index,
 * ground, snow line, water, the snow-country weight and the decision, for tools/snowcountry/view.py.
 *   java ... com.formaworks.frontierhunts.landscape.SnowHarness <seed> <step> <n> <out.bin>
 */
public class SnowHarness {
   static final String[] NAMES = {"alpine_river", "valley_meadow", "cedar_valley", "pine_highlands", "alpine_meadow", "glacial_peaks", "birch_grove",
      "maple_woodland", "aspen_woodland", "snowy_pine_forest", "marsh_meadow", "rocky_foothills", "riverwood", "mosswood", "verdant_karst", "wildflower_glade",
      "fern_wetland", "woodland_lakeshore", "coastal_ocean", "deep_ocean", "wild_coast", "cascade_gorge", "misty_falls", "hidden_grotto", "golden_aspen_grove",
      "autumn_maple_hollow", "larch_highlands", "fireweed_burn", "heather_moor", "sagebrush_bench", "boulder_talus", "muskeg_bog", "alpine_fellfield",
      "aspen_parkland", "limestone_bluff", "alder_carr", "rowan_slope", "cottonwood_bottom", "birch_heath"};

   public static void main(String[] a) throws Exception {
      long seed = Long.parseLong(a[0]);
      int step = Integer.parseInt(a[1]), n = Integer.parseInt(a[2]);
      AlpineLayout L = new AlpineLayout(seed, 21);
      SnowCountry sc = SnowCountry.forTest(L, a.length > 4 ? Integer.parseInt(a[4]) : SnowCountry.VERSION);
      long t0 = System.nanoTime();
      int snow = 0, land = 0;
      try (DataOutputStream d = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(a[3])))) {
         d.writeInt(n);
         d.writeInt(step);
         for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
               int x = (i - n / 2) * step, z = (j - n / 2) * step;
               AlpineLayout.Sample s = L.sample(x + 2, z + 2);
               int b = s.biome();
               String path = b >= 0 && b < NAMES.length ? NAMES[b] : "";
               int dec = sc.decide(path, Math.floorDiv(x, 4), Math.floorDiv(z, 4));
               double w = sc.weight(x + 2, z + 2, s);
               d.writeShort(b);
               d.writeFloat((float)s.ground());
               d.writeFloat((float)s.snowLine());
               d.writeInt(s.water());
               d.writeFloat((float)w);
               d.writeByte(dec);
               if (s.water() <= s.floor()) {
                  land++;
                  if (dec != 0 || b == 5) {
                     snow++;
                  }
               }
            }
         }
      }
      System.out.printf("seed %d: %d points, land %d, snowy biome on %.1f%% of land, %.1f s%n", seed, n * n, land, 100.0 * snow / Math.max(1, land),
         (System.nanoTime() - t0) / 1e9);
   }
}
