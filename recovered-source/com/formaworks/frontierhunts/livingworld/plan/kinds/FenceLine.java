package com.formaworks.frontierhunts.livingworld.plan.kinds;

import com.formaworks.frontierhunts.livingworld.plan.Ctx;
import com.formaworks.frontierhunts.livingworld.plan.Dir;
import com.formaworks.frontierhunts.livingworld.plan.Kind;
import com.formaworks.frontierhunts.livingworld.plan.Kit;
import com.formaworks.frontierhunts.livingworld.plan.Rnd;

/**
 * [livingworld] Old fence lines across open country: a weathered rail fence with log posts that follows the ground, broken
 * in places, a gate; the homestead variant encloses an old pasture with the stone footing of a long-gone cabin, a fallen
 * chimney and a well; the drift fence zig-zags (split rail). Variants: rail_fence, homestead, drift_fence.
 */
public final class FenceLine extends Kind {
   public FenceLine() {
      super("fence_line", 26, "rail_fence", "homestead", "drift_fence");
   }

   void post(Ctx c, int x, int z, String spec) {
      int g = c.floor(x, z);
      if (c.wet(x, z)) {
         return;
      }
      Kit.ground(c, x, z, g, 3);
      c.set(x, g, z, spec);
   }

   @Override
   public boolean build(Ctx c) {
      int v = c.variant;
      Rnd r = c.rnd;
      if (c.wet(0, 0)) {
         return false;
      }
      int[] sv = c.survey(-20, -12, 20, 12, 8);
      if (sv[3] > 2 || sv[1] - sv[0] > 12) {
         return false;
      }
      c.plan.title = this.id + " / " + this.variants[v];
      String fence = r.chance(0.5) ? "frontierhunts:pine_fence" : "minecraft:spruce_fence";
      if (v == 1) {
         // pasture enclosure 30 x 20 with gaps
         int hx = 15, hz = 10;
         for (int x = -hx; x <= hx; x++) {
            for (int z : new int[]{-hz, hz}) {
               if (r.chance(0.12)) {
                  continue;
               }
               this.post(c, x, z, Math.floorMod(x, 5) == 0 ? "minecraft:stripped_spruce_log[axis=y]" : fence);
            }
         }
         for (int z = -hz + 1; z < hz; z++) {
            for (int x : new int[]{-hx, hx}) {
               if (r.chance(0.12) || x == -hx && Math.abs(z) <= 1) {
                  continue;
               }
               this.post(c, x, z, Math.floorMod(z, 5) == 0 ? "minecraft:stripped_spruce_log[axis=y]" : fence);
            }
         }
         int gz = 0;
         int gy = c.floor(-hx, gz);
         Kit.ground(c, -hx, gz, gy, 3);
         c.set(-hx, gy, gz, "minecraft:spruce_fence_gate[facing=east,in_wall=false,open=true,powered=false]");
         // the cabin footing (cobble outline), fallen chimney, the well
         int fy = c.floor(6, -3);
         for (int x = 3; x <= 9; x++) {
            for (int z = -6; z <= -1; z++) {
               boolean edge = x == 3 || x == 9 || z == -6 || z == -1;
               Kit.ground(c, x, z, fy, 3);
               if (edge && r.chance(0.8)) {
                  c.set(x, fy, z, r.chance(0.6) ? "minecraft:mossy_cobblestone" : "minecraft:cobblestone");
               } else if (!edge && r.chance(0.3)) {
                  c.set(x, fy, z, r.chance(0.5) ? "minecraft:fern" : "minecraft:short_grass");
               }
            }
         }
         for (int h = 0; h < 3; h++) {
            c.set(9, fy + h, -6, h < 2 ? "minecraft:mossy_cobblestone" : "minecraft:cobblestone_wall");
         }
         for (int i = 1; i <= 4; i++) {
            c.drop(9 + i, fy, -6, i % 2 == 0 ? "minecraft:cobblestone_slab[type=bottom,waterlogged=false]" : "minecraft:mossy_cobblestone_slab[type=bottom,waterlogged=false]");
         }
         int wy = c.floor(-6, 4);
         Kit.groundRect(c, -7, 3, -5, 5, wy, 4);
         for (int x = -7; x <= -5; x++) {
            for (int z = 3; z <= 5; z++) {
               if (x == -6 && z == 4) {
                  c.set(x, wy - 1, z, "minecraft:water");
                  c.set(x, wy - 2, z, "minecraft:water");
               } else {
                  c.set(x, wy, z, "minecraft:mossy_cobblestone_wall");
               }
            }
         }
         c.set(-7, wy + 1, 3, "minecraft:spruce_fence");
         c.set(-5, wy + 1, 5, "minecraft:spruce_fence");
         Kit.ground(c, -hx - 2, 3, c.floor(-hx - 2, 3), 3);
         Kit.sign(c, -hx - 2, c.floor(-hx - 2, 3), 3, Dir.WEST, "spruce", "fence_sign", 3);
         // an old apple tree in the yard
         c.tree(0, c.floor(0, 0), 0, "maple", 6);
      } else {
         // a long line across the slope
         int len = r.range(20, 30);
         int z = 0;
         for (int x = -len; x <= len; x++) {
            if (r.chance(0.05)) {
               z += r.chance(0.5) ? 1 : -1;
            }
            if (r.chance(0.08) && Math.abs(x) > 3) {
               continue;
            }
            if (v == 2) {
               // zig-zag: the rails alternate sides every three posts
               int zz = z + (Math.floorMod(x, 6) < 3 ? 0 : 1);
               this.post(c, x, zz, Math.floorMod(x, 3) == 0 ? "minecraft:stripped_spruce_log[axis=y]" : fence);
               if (Math.floorMod(x, 3) == 0) {
                  this.post(c, x, zz + (Math.floorMod(x, 6) < 3 ? 1 : -1), fence);
               }
            } else {
               this.post(c, x, z, Math.floorMod(x, 4) == 0 ? "minecraft:stripped_spruce_log[axis=y]" : fence);
            }
         }
         int gy = c.floor(0, 0);
         Kit.ground(c, 0, 0, gy, 3);
         c.set(0, gy, 0, "minecraft:spruce_fence_gate[facing=north,in_wall=false,open=false,powered=false]");
      }
      c.plan.cy = c.floor(0, 0);
      return true;
   }
}
