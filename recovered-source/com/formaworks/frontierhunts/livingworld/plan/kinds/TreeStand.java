package com.formaworks.frontierhunts.livingworld.plan.kinds;

import com.formaworks.frontierhunts.livingworld.plan.Ctx;
import com.formaworks.frontierhunts.livingworld.plan.Dir;
import com.formaworks.frontierhunts.livingworld.plan.Kind;
import com.formaworks.frontierhunts.livingworld.plan.Kit;
import com.formaworks.frontierhunts.livingworld.plan.Rnd;

/**
 * [structures2] One tree stand you stumble across in the woods: a hang-on stand (single or two-seat ladder stand) on a
 * big tree overlooking a faint game trail, a couple of scrapes pawed into the trail, a few old sticks and, sometimes, a
 * trail camera strapped to a neighbouring trunk or a weathered stand sign. No clearing, no plot - it sits in the forest.
 * Replaces the multi-stand stand_line in world generation.
 */
public final class TreeStand extends Kind {
   public TreeStand() {
      super("tree_stand", 8, "hang_on", "ladder_stand");
   }

   @Override
   public int weight(int variant) {
      return variant == 0 ? 3 : 2;
   }

   /** trail ground: the natural height kept, the plants on it trampled; the canopy above is left alone */
   static void trodden(Ctx c, int x, int z) {
      com.formaworks.frontierhunts.livingworld.plan.Plan.Shape s = c.shape(x, z, c.floor(x, z), 1.0);
      s.clear = Math.max(s.clear, 1);
   }

   @Override
   public boolean build(Ctx c) {
      int v = c.variant;
      Rnd r = c.rnd;
      int[] sv = c.survey(-4, -7, 4, 4, 2);
      if (sv[3] > 0 || sv[1] - sv[0] > 4) {
         return false;
      }
      int y = sv[2];
      c.plan.title = this.id + " / " + this.variants[v];
      // the stand: ladder at (0, 0), its tree 2 blocks behind (south), the hunter looks north over the trail
      int h = v == 0 ? r.range(4, 5) : 4;
      Kit.treeStand(c, 0, y, 0, Dir.NORTH, h, v == 1 ? 2 : 1, StandLine.treeFor(c, 0, 2));
      // shooting window: no foliage of the stand tree around the ladder, platform and seat
      for (int lx = -2; lx <= 2; lx++) {
         for (int lz = -2; lz <= 1; lz++) {
            for (int yy = y; yy <= y + h + 2; yy++) {
               com.formaworks.frontierhunts.livingworld.plan.Plan.Block b = c.at(lx, yy, lz);
               if (b != null && b.spec.contains("leaves")) {
                  c.plan.remove(c.wx(lx, lz), yy, c.wz(lx, lz));
               }
            }
         }
      }
      // the game trail: a faint, wandering strip of trodden ground about 6 blocks in front, running east-west
      int tz = -6;
      for (int x = -8; x <= 8; x++) {
         int z = tz + (int)Math.round(Math.sin((x + r.nextDouble()) * 0.45) * 1.2);
         if (c.wet(x, z) || Math.abs(c.floor(x, z) - y) > 3) {
            continue;
         }
         if (r.chance(0.8)) {
            trodden(c, x, z);
            Kit.surface(c, x, z, r.chance(0.6) ? "minecraft:coarse_dirt" : r.chance(0.5) ? "minecraft:podzol[snowy=false]" : "minecraft:rooted_dirt");
         }
         // scrapes: a pawed-up patch at two spots along the trail
         if (x == -4 || x == 5) {
            for (int dz = -1; dz <= 1; dz++) {
               trodden(c, x, z + dz);
               Kit.surface(c, x, z + dz, "minecraft:rooted_dirt");
            }
            c.drop(x + 1, c.floor(x + 1, z - 1), z - 1, Kit.FH + "forest_sticks[facing=" + r.pick(Dir.values()).id() + ",variant=" + r.nextInt(6) + "]");
         }
      }
      // a few old branches under the stand (trimmed shooting lanes)
      for (int i = 0; i < 3; i++) {
         int x = r.range(-3, 3);
         int z = r.range(-4, -1);
         c.drop(x, c.floor(x, z), z, Kit.FH + "forest_sticks[facing=" + r.pick(Dir.values()).id() + ",variant=" + r.nextInt(6) + "]");
      }
      if (r.chance(0.35)) {
         // a trail camera on its own tree further along, watching the trail
         int cx = r.chance(0.5) ? -6 : 6;
         int cz = -2;
         Kit.ground(c, cx, cz, c.floor(cx, cz), 2);
         c.tree(cx, c.floor(cx, cz), cz, StandLine.treeFor(c, cx, cz), 8 + r.nextInt(4));
         Kit.trailCamera(c, cx, c.floor(cx, cz) + 1, cz - 1, Dir.NORTH);
      } else if (r.chance(0.4)) {
         Kit.ground(c, 2, -1, y, 3);
         Kit.sign(c, 2, y, -1, Dir.NORTH, "spruce", "stand_sign", 4);
      }
      c.plan.cy = y;
      return true;
   }
}
