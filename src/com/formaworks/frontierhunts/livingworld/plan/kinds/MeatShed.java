package com.formaworks.frontierhunts.livingworld.plan.kinds;

import com.formaworks.frontierhunts.livingworld.plan.Ctx;
import com.formaworks.frontierhunts.livingworld.plan.Dir;
import com.formaworks.frontierhunts.livingworld.plan.Kind;
import com.formaworks.frontierhunts.livingworld.plan.Kit;
import com.formaworks.frontierhunts.livingworld.plan.Rnd;

/**
 * [livingworld] Meat processing shed: an open-sided pole barn (log posts, shingle gable roof, gravel floor) with game poles
 * hung with quarters, a butchering table, a drying rack of jerky, hanging lanterns and hooks, salt barrels and a water
 * kettle; a smokehouse working next to it with its woodpile. Variants: skinning_shed, smokehouse_yard (two smokers, a
 * tanning rack).
 */
public final class MeatShed extends Kind {
   public MeatShed() {
      super("meat_shed", 13, "skinning_shed", "smokehouse_yard");
   }

   @Override
   public boolean build(Ctx c) {
      int v = c.variant;
      Rnd r = c.rnd;
      int[] sv = c.survey(-8, -7, 8, 7, 3);
      if (sv[3] > 0 || sv[1] - sv[0] > 6) {
         return false;
      }
      int y = sv[2];
      c.plan.title = this.id + " / " + this.variants[v];
      Kit.pad(c, 0, 0, 9.0, 8.0, y, 4, 9);
      // shed: 7 x 5 posts, roof ridge along x
      int x0 = -3, x1 = 3, z0 = -2, z1 = 2;
      for (int x = x0; x <= x1; x++) {
         for (int z = z0; z <= z1; z++) {
            Kit.ground(c, x, z, y, 7);
            Kit.surface(c, x, z, r.chance(0.7) ? "minecraft:gravel" : "minecraft:coarse_dirt");
            boolean post = (x == x0 || x == x1 || x == 0) && (z == z0 || z == z1);
            if (post) {
               // [structures2] posts stand on stone footings
               Kit.surface(c, x, z, r.chance(0.5) ? "minecraft:mossy_cobblestone" : "minecraft:cobblestone");
               for (int h = 0; h < 4; h++) {
                  c.set(x, y + h, z, "minecraft:spruce_log[axis=y]");
               }
            }
            if (z == z0 || z == z1) {
               c.set(x, y + 4, z, "minecraft:stripped_spruce_log[axis=x]");
            }
            if (x == x0 || x == x1) {
               c.set(x, y + 4, z, "minecraft:stripped_spruce_log[axis=z]");
            }
         }
      }
      // tie beams with hooks and lanterns
      for (int x : new int[]{-2, 0, 2}) {
         c.set(x, y + 4, 0, "minecraft:stripped_spruce_log[axis=z]");
         c.set(x, y + 4, -1, "minecraft:stripped_spruce_log[axis=z]");
         c.set(x, y + 4, 1, "minecraft:stripped_spruce_log[axis=z]");
      }
      c.set(-2, y + 3, 0, "minecraft:lantern[hanging=true,waterlogged=false]");
      c.set(2, y + 3, 0, "minecraft:lantern[hanging=true,waterlogged=false]");
      c.set(0, y + 3, -1, "minecraft:chain[axis=y,waterlogged=false]");
      c.set(0, y + 3, 1, "minecraft:chain[axis=y,waterlogged=false]");
      // roof
      for (int k = 0; k <= 3; k++) {
         int zf = z0 - 1 + k;
         int zb = z1 + 1 - k;
         for (int x = x0 - 1; x <= x1 + 1; x++) {
            if (zf == zb) {
               c.set(x, y + 4 + k, zf, "frontierhunts:roof_slab[type=bottom,waterlogged=false]");
            } else if (zf < zb) {
               c.set(x, y + 4 + k, zf, "frontierhunts:roof_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]");
               c.set(x, y + 4 + k, zb, "frontierhunts:roof_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]");
            }
         }
         if (zf + 1 < zb) {
            for (int z = zf + 1; z <= zb - 1; z++) {
               c.set(x0, y + 4 + k, z, "minecraft:spruce_planks");
               c.set(x1, y + 4 + k, z, "minecraft:spruce_planks");
            }
         }
      }
      // inside: poles with quarters along the back, butcher table, drying rack, salt and water
      for (int x = -2; x <= 2; x += 2) {
         Kit.gamePole(c, x, y, 1, Dir.EAST, r.range(1, 4), r.chance(0.6) ? 1 : 0);
      }
      Kit.gamePole(c, -1, y, 1, Dir.EAST, r.range(0, 3), 0);
      c.set(-2, y, -1, Kit.FH + "lit_lodge_table[facing=north]");
      c.set(-1, y, -1, Kit.FH + "lodge_table[facing=north]");
      Kit.dryingRack(c, 1, y, -1, Dir.EAST, 4, r.chance(0.6) ? 2 : 1);
      Kit.dryingRack(c, 2, y, -1, Dir.EAST, r.range(2, 4), 1);
      Kit.barrel(c, -3, y, 3, "up", "meat_shed");
      c.set(-2, y, 3, "minecraft:barrel[facing=up,open=false]");
      c.set(3, y, 3, "minecraft:water_cauldron[level=" + r.range(1, 3) + "]");
      Kit.ground(c, -3, 3, y, 4);
      Kit.ground(c, -2, 3, y, 4);
      Kit.ground(c, 3, 3, y, 4);
      // smokehouse(s) beside the shed with wood
      Kit.groundRect(c, 5, -2, 6, 3, y, 4);
      Kit.wide(c, "smokehouse", 5, y, -1, Dir.WEST, "lit=true");
      if (v == 1) {
         Kit.wide(c, "smokehouse", 5, y, 1, Dir.WEST, "lit=true");
         Kit.groundRect(c, -7, -2, -6, 2, y, 4);
         Kit.wide(c, "tanning_rack", -6, y, 1, Dir.EAST, null);
      }
      Kit.firewood(c, 6, y, 3, Dir.NORTH, 1, 2, r);
      Kit.firewood(c, 5, y, 3, Dir.NORTH, 1, 2, r);
      Kit.choppingBlock(c, 4, y, 4, r);
      Kit.signPost(c, -4, y, -4, Dir.NORTH, "meat_shed_sign", 3);
      Kit.overgrowth(c, 0, 0, 9.5, 8.5, 0.08, r, null);
      c.plan.cy = y;
      return true;
   }
}
