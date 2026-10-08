package com.formaworks.frontierhunts.landscape;

import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * [gear20] Vanilla caves for the alpine terrain. The alpine generator builds its ground column by column from its own
 * height model and had no caves at all. This carves the same cave systems a vanilla overworld has, from the same
 * vanilla noises and the same formulas as vanilla's noise router (cheese caverns with cave layers, 3D spaghetti tunnels,
 * the deep 2D spaghetti, cave entrances, noodle caves; lava below y -55), sampled on vanilla's 4 x 8 x 4 cell grid and
 * interpolated in between.
 *
 * <p>What is different is where caves may go, because this terrain has big rivers, lakes, waterfalls and scenic sites
 * that must never drain: big caverns stay 12 blocks under the ground; tunnels and noodles may come up to 4 blocks under
 * it; only dry hillsides away from any water may open to the surface (cave entrances, like vanilla); nothing comes within
 * 7 blocks of the bed of water within 6 blocks; waterfall grottos and scenic sites keep a thick roof.
 */
final class AlpineCaves {
   private static final BlockState AIR = Blocks.AIR.defaultBlockState();
   private static final BlockState LAVA = Blocks.LAVA.defaultBlockState();
   /** no caves above this (vanilla noodles stop at 320 too) */
   private static final int MAX_TOP = 320;
   private static final int BOTTOM = -58;
   private static final int LAVA_LEVEL = -55;
   private static final int DEEP_ROOF = 12;
   private static final int TUNNEL_ROOF = 4;
   private static final int WET_REACH = 6;
   private static final int WET_ROOF = 7;
   private static final int NONE = Integer.MAX_VALUE;
   /** [1.1.1] within this depth of the surface only narrow spaghetti tunnels are carved (no pits) */
   private static final int SURFACE_SKIN = 18;
   /** [1.1.2] solid ground kept over every tunnel */
   private static final int SURFACE_ROOF = 7;

   private AlpineCaves() {
   }

   /** the vanilla noises for this world */
   private record N(NormalNoise cheese, NormalNoise layer, NormalNoise entrance, NormalNoise sp3a, NormalNoise sp3b, NormalNoise sp3rarity,
      NormalNoise sp3thick, NormalNoise rough, NormalNoise roughMod, NormalNoise sp2d, NormalNoise sp2dMod, NormalNoise sp2dElev,
      NormalNoise sp2dThick, NormalNoise noodle, NormalNoise noodleThick, NormalNoise ridgeA, NormalNoise ridgeB) {
      static N of(RandomState r) {
         return new N(r.getOrCreateNoise(Noises.CAVE_CHEESE), r.getOrCreateNoise(Noises.CAVE_LAYER), r.getOrCreateNoise(Noises.CAVE_ENTRANCE),
            r.getOrCreateNoise(Noises.SPAGHETTI_3D_1), r.getOrCreateNoise(Noises.SPAGHETTI_3D_2), r.getOrCreateNoise(Noises.SPAGHETTI_3D_RARITY),
            r.getOrCreateNoise(Noises.SPAGHETTI_3D_THICKNESS), r.getOrCreateNoise(Noises.SPAGHETTI_ROUGHNESS),
            r.getOrCreateNoise(Noises.SPAGHETTI_ROUGHNESS_MODULATOR), r.getOrCreateNoise(Noises.SPAGHETTI_2D),
            r.getOrCreateNoise(Noises.SPAGHETTI_2D_MODULATOR), r.getOrCreateNoise(Noises.SPAGHETTI_2D_ELEVATION),
            r.getOrCreateNoise(Noises.SPAGHETTI_2D_THICKNESS), r.getOrCreateNoise(Noises.NOODLE), r.getOrCreateNoise(Noises.NOODLE_THICKNESS),
            r.getOrCreateNoise(Noises.NOODLE_RIDGE_A), r.getOrCreateNoise(Noises.NOODLE_RIDGE_B));
      }
   }

   static void carve(ChunkAccess chunk, AlpineTile tile, AlpineLayout layout, RandomState random) {
      int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
      // ---- where water is: interior columns from the tile, a coarse ring around the chunk from the layout
      int[] wetFloor = new int[32 * 32];
      java.util.Arrays.fill(wetFloor, NONE);
      for (int lx = 0; lx < 16; lx++) {
         for (int lz = 0; lz < 16; lz++) {
            AlpineLayout.Sample s = tile.sample(lx, lz);
            if (s.wet()) {
               wetFloor[(lx + 8) * 32 + lz + 8] = s.floor();
            }
         }
      }
      for (int gx = -8; gx < 24; gx += 4) {
         for (int gz = -8; gz < 24; gz += 4) {
            if (gx >= 0 && gx < 16 && gz >= 0 && gz < 16) {
               continue;
            }
            AlpineLayout.Sample s = layout.sample(x0 + gx, z0 + gz);
            if (s.wet()) {
               // a coarse sample stands for its 4 x 4 cell
               for (int a = 0; a < 4; a++) {
                  for (int b = 0; b < 4; b++) {
                     wetFloor[(gx + a + 8) * 32 + gz + b + 8] = s.floor();
                  }
               }
            }
         }
      }
      // ---- per column: how high big caverns and tunnels may reach
      int[] deepTop = new int[256];
      int[] floors = new int[256];
      int[] tunnelTop = new int[256];
      int top = BOTTOM;
      for (int lx = 0; lx < 16; lx++) {
         for (int lz = 0; lz < 16; lz++) {
            AlpineLayout.Sample s = tile.sample(lx, lz);
            int floor = s.floor();
            int wetLimit = NONE;
            for (int dx = -WET_REACH; dx <= WET_REACH; dx++) {
               for (int dz = -WET_REACH; dz <= WET_REACH; dz++) {
                  int w = wetFloor[(lx + dx + 8) * 32 + lz + dz + 8];
                  if (w != NONE) {
                     wetLimit = Math.min(wetLimit, w - WET_ROOF);
                  }
               }
            }
            int limit = Math.min(MAX_TOP, wetLimit);
            if (s.scenic()) {
               limit = Math.min(limit, floor - 20);
            }
            if (AlpineFalls.hasCavity(s.cavity())) {
               limit = Math.min(limit, AlpineFalls.cavityBottom(s.cavity()) - 10);
            }
            // [1.1.2] no cave ever opens to the sky: every cave keeps a solid roof under the LOWEST ground nearby, so
            // tunnels cannot cut out through a hillside below either (no holes, shafts or pits anywhere in the ground)
            double slope = Math.min(6.0, tile.slope(lx, lz));
            int fall = (int)Math.ceil(slope * 4.0);
            int i = lx * 16 + lz;
            deepTop[i] = Math.min(limit, floor - DEEP_ROOF - fall);
            floors[i] = floor;
            tunnelTop[i] = Math.min(limit, floor - SURFACE_ROOF - fall);
            top = Math.max(top, Math.max(deepTop[i], tunnelTop[i]));
         }
      }
      if (top < BOTTOM) {
         return;
      }
      // ---- vanilla densities on the cell grid (x, z every 4, y every 8), three channels: underground, entrances, noodles
      N n = N.of(random);
      int ny = Math.floorDiv(top - BOTTOM, 8) + 2;
      int y0 = BOTTOM;
      double[] under = new double[5 * 5 * ny];
      double[] entr = new double[5 * 5 * ny];
      double[] noodle = new double[5 * 5 * ny];
      double[] narrow = new double[5 * 5 * ny];
      for (int cx = 0; cx < 5; cx++) {
         for (int cz = 0; cz < 5; cz++) {
            int wx = x0 + cx * 4, wz = z0 + cz * 4;
            for (int cy = 0; cy < ny; cy++) {
               int wy = y0 + cy * 8;
               int k = (cx * 5 + cz) * ny + cy;
               double rough = roughness(n, wx, wy, wz);
               double e = entrances(n, wx, wy, wz, rough);
               entr[k] = e;
               narrow[k] = spaghetti3d(n, wx, wy, wz, rough);
               // the 2D spaghetti lies between y -64 and about 64 (its band follows a gradient from 8 to -40): skip above
               double sp2 = wy > 88 ? 1.0 : spaghetti2d(n, wx, wy, wz) + rough;
               under[k] = Math.min(Math.min(cheese(n, wx, wy, wz), e), sp2);
               noodle[k] = noodle(n, wx, wy, wz);
            }
         }
      }
      // ---- carve
      LevelChunkSection[] sections = chunk.getSections();
      int minSection = chunk.getMinSection();
      for (int lx = 0; lx < 16; lx++) {
         int cx = lx >> 2;
         double fx = (lx & 3) / 4.0;
         for (int lz = 0; lz < 16; lz++) {
            int cz = lz >> 2;
            double fz = (lz & 3) / 4.0;
            int i = lx * 16 + lz;
            int colTop = Math.max(deepTop[i], tunnelTop[i]);
            for (int y = BOTTOM; y <= colTop; y++) {
               int cy = (y - y0) >> 3;
               double fy = ((y - y0) & 7) / 8.0;
               boolean air;
               if (y <= deepTop[i] && lerp3(under, ny, cx, cy, cz, fx, fy, fz) < 0.0) {
                  air = true;
               } else if (y <= tunnelTop[i] && ((y > floors[i] - SURFACE_SKIN ? lerp3(narrow, ny, cx, cy, cz, fx, fy, fz) : lerp3(entr, ny, cx, cy, cz, fx, fy, fz)) < 0.0
                  || lerp3(noodle, ny, cx, cy, cz, fx, fy, fz) < 0.0)) {
                  // [1.1.1] near the surface only narrow tunnels may open out: the big cave_entrance blobs stay underground
                  // (they tore huge pits into hillsides)
                  air = true;
               } else {
                  air = false;
               }
               if (!air) {
                  continue;
               }
               int si = (y >> 4) - minSection;
               if (si < 0 || si >= sections.length) {
                  continue;
               }
               LevelChunkSection sec = sections[si];
               BlockState old = sec.getBlockState(lx, y & 15, lz);
               if (old.isAir() || !old.getFluidState().isEmpty() || old.is(Blocks.BEDROCK)) {
                  continue;
               }
               sec.setBlockState(lx, y & 15, lz, y <= LAVA_LEVEL ? LAVA : AIR, false);
            }
         }
      }
   }

   private static double lerp3(double[] g, int ny, int cx, int cy, int cz, double fx, double fy, double fz) {
      int b00 = (cx * 5 + cz) * ny + cy, b01 = (cx * 5 + cz + 1) * ny + cy, b10 = ((cx + 1) * 5 + cz) * ny + cy, b11 = ((cx + 1) * 5 + cz + 1) * ny + cy;
      return Mth.lerp3(fx, fy, fz, g[b00], g[b10], g[b00 + 1], g[b10 + 1], g[b01], g[b11], g[b01 + 1], g[b11 + 1]);
   }

   // ---------------------------------------------------------------------------------------- vanilla NoiseRouterData

   /** cheese caverns: cave_cheese (y scale 2/3) plus 4 x cave_layer^2 (y scale 8) */
   private static double cheese(N n, double x, double y, double z) {
      double layer = n.layer.getValue(x, y * 8.0, z);
      return 4.0 * layer * layer + Mth.clamp(0.27 + n.cheese.getValue(x, y * 0.6666666666666666, z), -1.0, 1.0);
   }

   /** cave entrances: 3D spaghetti and the cave_entrance noise */
   /** the 3D spaghetti alone (narrow tunnels): the only thing allowed to break the surface */
   private static double spaghetti3d(N n, double x, double y, double z, double rough) {
      double rarity = rarity3d(n.sp3rarity.getValue(x * 2.0, y, z * 2.0));
      double a = rarity * Math.abs(n.sp3a.getValue(x / rarity, y / rarity, z / rarity));
      double b = rarity * Math.abs(n.sp3b.getValue(x / rarity, y / rarity, z / rarity));
      double thick = mapped(n.sp3thick.getValue(x, y, z), -0.065, -0.088);
      return rough + Mth.clamp(Math.max(a, b) + thick, -1.0, 1.0);
   }

   private static double entrances(N n, double x, double y, double z, double rough) {
      double rarity = rarity3d(n.sp3rarity.getValue(x * 2.0, y, z * 2.0));
      double a = rarity * Math.abs(n.sp3a.getValue(x / rarity, y / rarity, z / rarity));
      double b = rarity * Math.abs(n.sp3b.getValue(x / rarity, y / rarity, z / rarity));
      double thick = mapped(n.sp3thick.getValue(x, y, z), -0.065, -0.088);
      double sp3 = Mth.clamp(Math.max(a, b) + thick, -1.0, 1.0);
      double ent = n.entrance.getValue(x * 0.75, y * 0.5, z * 0.75) + 0.37 + Mth.clampedMap(y, -10.0, 30.0, 0.3, 0.0);
      return Math.min(ent, rough + sp3);
   }

   private static double roughness(N n, double x, double y, double z) {
      return mapped(n.roughMod.getValue(x, y, z), 0.0, -0.1) * (Math.abs(n.rough.getValue(x, y, z)) - 0.4);
   }

   /** the deep horizontal tunnels (y -64 .. ~64) */
   private static double spaghetti2d(N n, double x, double y, double z) {
      double rarity = rarity2d(n.sp2dMod.getValue(x * 2.0, y, z * 2.0));
      double tunnel = rarity * Math.abs(n.sp2d.getValue(x / rarity, y / rarity, z / rarity));
      double elevation = mapped(n.sp2dElev.getValue(x, 0.0, z), -8.0, 8.0);
      double thick = mapped(n.sp2dThick.getValue(x * 2.0, y, z * 2.0), -0.6, -1.3);
      double band = Math.abs(elevation + Mth.clampedMap(y, -64.0, 320.0, 8.0, -40.0));
      double shell = band + thick;
      return Mth.clamp(Math.max(tunnel + 0.083 * thick, shell * shell * shell), -1.0, 1.0);
   }

   /** noodle caves: thin winding tunnels where the noodle noise is positive (y -60 .. 320) */
   private static double noodle(N n, double x, double y, double z) {
      if (y < -60 || y > 320 || n.noodle.getValue(x, y, z) < 0.0) {
         return 64.0;
      }
      double s = 2.6666666666666665;
      double thick = mapped(n.noodleThick.getValue(x, y, z), -0.05, -0.1);
      return thick + 1.5 * Math.max(Math.abs(n.ridgeA.getValue(x * s, y * s, z * s)), Math.abs(n.ridgeB.getValue(x * s, y * s, z * s)));
   }

   private static double mapped(double v, double from, double to) {
      return (from + to) * 0.5 + (to - from) * 0.5 * v;
   }

   private static double rarity3d(double v) {
      return v < -0.5 ? 0.75 : v < 0.0 ? 1.0 : v < 0.5 ? 1.5 : 2.0;
   }

   private static double rarity2d(double v) {
      return v < -0.75 ? 0.5 : v < -0.5 ? 0.75 : v < 0.5 ? 1.0 : v < 0.75 ? 2.0 : 3.0;
   }
}
