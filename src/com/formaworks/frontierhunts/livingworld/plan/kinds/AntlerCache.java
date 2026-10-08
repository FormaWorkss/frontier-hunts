package com.formaworks.frontierhunts.livingworld.plan.kinds;

import com.formaworks.frontierhunts.livingworld.plan.Ctx;
import com.formaworks.frontierhunts.livingworld.plan.Dir;
import com.formaworks.frontierhunts.livingworld.plan.Kind;
import com.formaworks.frontierhunts.livingworld.plan.Kit;
import com.formaworks.frontierhunts.livingworld.plan.Plan;
import com.formaworks.frontierhunts.livingworld.plan.Rnd;

/**
 * [livingworld] Shed hunters' and old-timers' marks: a pile of shed antlers around a big stump with a skull on top and a
 * take-one-leave-one sign (shed_pile), an antler arch over a game trail with a skull post (skull_post), or a winter-kill
 * boneyard of old bones and skulls among the brush (boneyard).
 */
public final class AntlerCache extends Kind {
   public AntlerCache() {
      super("antler_cache", 8, "shed_pile", "skull_post", "boneyard");
   }

   @Override
   public boolean build(Ctx c) {
      int v = c.variant;
      Rnd r = c.rnd;
      int[] sv = c.survey(-4, -4, 4, 4, 2);
      if (sv[3] > 0 || sv[1] - sv[0] > 4) {
         return false;
      }
      int y = sv[2];
      c.plan.title = this.id + " / " + this.variants[v];
      Kit.pad(c, 0, 0, 3.0, 3.0, y, 2, 6);
      if (v == 0) {
         Kit.ground(c, 0, 0, y, 4);
         c.set(0, y, 0, "minecraft:stripped_spruce_log[axis=y]");
         c.set(0, y + 1, 0, Kit.FH + r.pick("whitetail_skull", "elk_skull") + "[antlers=true,facing=" + r.pick(Dir.values()).id() + ",mossy=false,natural=true]");
         for (int i = 0; i < 9; i++) {
            int x = r.range(-2, 2);
            int z = r.range(-2, 2);
            if (x == 0 && z == 0) {
               continue;
            }
            c.set(x, y, z, Kit.FH + "shed_antler[facing=" + r.pick(Dir.values()).id() + ",mossy=" + r.chance(0.3) + ",natural=true]", Plan.IF_FREE | Plan.SUPPORTED);
         }
         Kit.chest(c, 1, y, 1, Dir.SOUTH, "cache");
         Kit.ground(c, -2, 3, y, 3);
         Kit.sign(c, -2, y, 3, Dir.SOUTH, "spruce", "cache_sign", 3);
      } else if (v == 1) {
         // antler arch over the trail: two posts, a cross pole, sheds lashed on
         for (int x : new int[]{-2, 2}) {
            Kit.ground(c, x, 0, y, 5);
            for (int h = 0; h < 3; h++) {
               c.set(x, y + h, 0, "minecraft:stripped_spruce_log[axis=y]");
            }
         }
         for (int x = -2; x <= 2; x++) {
            c.set(x, y + 3, 0, "minecraft:stripped_spruce_log[axis=x]");
         }
         c.set(-1, y + 4, 0, Kit.FH + "shed_antler[facing=north,mossy=false,natural=true]");
         c.set(1, y + 4, 0, Kit.FH + "shed_antler[facing=south,mossy=false,natural=true]");
         c.set(0, y + 4, 0, Kit.FH + "elk_skull[antlers=true,facing=south,mossy=false,natural=true]");
         for (int z = -4; z <= 4; z++) {
            Kit.surface(c, 0, z, "minecraft:coarse_dirt");
            Kit.ground(c, 0, z, y, 4);
         }
         Kit.ground(c, 3, 2, y, 3);
         c.set(3, y, 2, "minecraft:stripped_spruce_log[axis=y]");
         c.set(3, y + 1, 2, Kit.FH + "moose_skull[antlers=true,facing=west,mossy=true,natural=true]");
      } else {
         for (int i = 0; i < 10; i++) {
            int x = r.range(-4, 4);
            int z = r.range(-4, 4);
            Dir d = r.pick(Dir.values());
            String spec = i < 3
               ? Kit.FH + r.pick("whitetail_skull", "elk_skull", "bison_skull", "moose_skull") + "[antlers=" + r.chance(0.5) + ",facing=" + d.id() + ",mossy=" + r.chance(0.6) + ",natural=true]"
               : Kit.FH + "scattered_bones[facing=" + d.id() + ",mossy=" + r.chance(0.6) + ",natural=true,part=" + r.pick("ribs", "spine", "legs") + "]";
            c.drop(x, y, z, spec);
         }
         Kit.overgrowth(c, 0, 0, 5, 5, 0.35, r, null);
      }
      c.plan.cy = y;
      return true;
   }
}
