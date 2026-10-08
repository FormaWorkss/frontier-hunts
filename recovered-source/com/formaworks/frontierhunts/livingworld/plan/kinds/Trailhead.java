package com.formaworks.frontierhunts.livingworld.plan.kinds;

import com.formaworks.frontierhunts.livingworld.plan.Ctx;
import com.formaworks.frontierhunts.livingworld.plan.Dir;
import com.formaworks.frontierhunts.livingworld.plan.Kind;
import com.formaworks.frontierhunts.livingworld.plan.Kit;
import com.formaworks.frontierhunts.livingworld.plan.Plan;
import com.formaworks.frontierhunts.livingworld.plan.Rnd;

/**
 * [livingworld] Trailhead: a gravel pull-off, a roofed kiosk with the map board (expedition board) and the trail register
 * box, trail signs, a bench, a lantern post, sometimes a parked ATV, and a worn foot trail leading off into the country.
 * Variants: forest_trailhead, meadow_trailhead (rail fence along the pull-off).
 */
public final class Trailhead extends Kind {
   public Trailhead() {
      super("trailhead", 16, "forest_trailhead", "meadow_trailhead");
   }

   @Override
   public boolean build(Ctx c) {
      int v = c.variant;
      Rnd r = c.rnd;
      int[] sv = c.survey(-6, -5, 6, 5, 2);
      if (sv[3] > 0 || sv[1] - sv[0] > 5) {
         return false;
      }
      int y = sv[2];
      c.plan.title = this.id + " / " + this.variants[v];
      Kit.pad(c, 0, 0, 7.5, 6.0, y, 4, 7);
      // gravel pull-off
      for (int x = -5; x <= 5; x++) {
         for (int z = -1; z <= 4; z++) {
            Kit.surface(c, x, z, r.chance(0.8) ? "minecraft:gravel" : "minecraft:coarse_dirt");
         }
      }
      // kiosk: two log posts, roof, map board, register box, sign
      for (int x : new int[]{-2, 2}) {
         for (int h = 0; h < 3; h++) {
            c.set(x, y + h, -3, "minecraft:spruce_log[axis=y]");
         }
      }
      for (int x = -3; x <= 3; x++) {
         c.set(x, y + 3, -3, "frontierhunts:roof_slab[type=bottom,waterlogged=false]");
         c.set(x, y + 3, -4, "frontierhunts:roof_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]");
         c.set(x, y + 3, -2, "frontierhunts:roof_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]");
         Kit.ground(c, x, -3, y, 4);
         Kit.ground(c, x, -4, y, 4);
      }
      Kit.wide(c, "expedition_board", -1, y, -3, Dir.SOUTH, null);
      c.set(1, y, -3, "minecraft:spruce_fence");
      Kit.barrel(c, 1, y + 1, -3, "south", "trailhead");
      Kit.hangingSign(c, 0, y + 2, -3, Dir.SOUTH, "spruce", "trail_sign", 1);
      Kit.wallSign(c, 2, y + 1, -2, Dir.SOUTH, "spruce", "trail_board", 4);
      // bench and lantern
      c.set(-4, y, -1, "minecraft:spruce_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]");
      c.set(-5, y, -1, "minecraft:spruce_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]");
      Kit.lanternPost(c, 4, y, -1, Dir.SOUTH, "minecraft:spruce_fence");
      // the foot trail heading off north-ish, with mod trail signs
      Dir go = Dir.NORTH;
      int tx = 3;
      int tz = -5;
      for (int i = 0; i < 26; i++) {
         if (i > 0 && i % 7 == 0) {
            tx += r.chance(0.5) ? 1 : -1;
         }
         Plan.Shape s = c.shapeAt(tx, tz);
         if (s == null) {
            s = c.shape(tx, tz, c.floor(tx, tz), 0.0);
         }
         s.surface = r.chance(0.7) ? "minecraft:dirt_path" : "minecraft:coarse_dirt";
         s.clear = Math.max(s.clear, 3);
         s.canopy = Math.max(s.canopy, 4);
         if (i == 3 || i == 18) {
            int px = tx + 1;
            int pz = tz;
            int py = c.floor(px, pz);
            Kit.ground(c, px, pz, py, 3);
            c.set(px, py, pz, Kit.FH + "trail_sign[facing=" + go.id() + "]");
            if (i == 3) {
               Kit.ground(c, px - 2, pz, py, 3);
               Kit.sign(c, px - 2, py, pz, Dir.SOUTH, "spruce", "trail_dirs", 3);
            }
         }
         tz += go.dz;
         tx += go.dx;
      }
      if (v == 1) {
         for (int x = -6; x <= 6; x++) {
            int g = c.floor(x, 6);
            Kit.ground(c, x, 6, g, 2);
            c.set(x, g, 6, x % 4 == 0 ? "minecraft:stripped_spruce_log[axis=y]" : "frontierhunts:pine_fence");
         }
      }
      if (r.chance(0.35)) {
         Kit.atv(c, -2.5, y, 2.5, r.chance(0.5) ? Dir.EAST : Dir.WEST, r, false);
      }
      Kit.overgrowth(c, 0, 0, 8.5, 7, 0.08, r, null);
      c.plan.cy = y;
      return true;
   }
}
