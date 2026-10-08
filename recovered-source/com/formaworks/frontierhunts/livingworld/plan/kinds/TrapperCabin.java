package com.formaworks.frontierhunts.livingworld.plan.kinds;

import com.formaworks.frontierhunts.livingworld.plan.Cabin;
import com.formaworks.frontierhunts.livingworld.plan.Ctx;
import com.formaworks.frontierhunts.livingworld.plan.Dir;
import com.formaworks.frontierhunts.livingworld.plan.Kind;
import com.formaworks.frontierhunts.livingworld.plan.Kit;
import com.formaworks.frontierhunts.livingworld.plan.Rnd;

/**
 * [livingworld] Trapper's cabin: a small, low log cabin with pelts stretched on the outside walls, a tanning rack and a
 * smokehouse in the yard, a woodpile and chopping block, skulls on stumps and a fur-trade sign. Inside a bunk with a hide
 * bedroll, table, stove and the trapper's stores. Variants: creek_cabin, line_shack (tiny, no smokehouse).
 */
public final class TrapperCabin extends Kind {
   public TrapperCabin() {
      super("trapper_cabin", 13, "creek_cabin", "line_shack");
   }

   @Override
   public boolean build(Ctx c) {
      int v = c.variant;
      Rnd r = c.rnd;
      int[] sv = c.survey(-7, -6, 7, 7, 3);
      if (sv[3] > 0 || sv[1] - sv[0] > 7) {
         return false;
      }
      int y = sv[2] + 1;
      c.plan.title = this.id + " / " + this.variants[v];
      Kit.pad(c, 0, 1, 9.0, 8.5, y - 1, 4, 8);
      int w = v == 0 ? 6 : 5;
      int d = v == 0 ? 5 : 4;
      Cabin cab = new Cabin(c, 0, 0, Dir.NORTH, w, d, y, 3);
      cab.log = "minecraft:spruce_log";
      cab.corner = "minecraft:spruce_log";
      cab.foundation = "minecraft:mossy_cobblestone";
      cab.gable = "minecraft:spruce_planks";
      cab.window = "frontierstructures:dark_oak_lattice_window";
      cab.shell();
      Dir out = cab.dir(Dir.NORTH);
      Dir in = cab.dir(Dir.SOUTH);
      Dir east = cab.dir(Dir.EAST);
      Dir west = cab.dir(Dir.WEST);
      cab.window(0, d / 2, Dir.WEST);
      cab.window(w - 1, d / 2, Dir.EAST);
      // inside
      int[] bunk = cab.at(w - 2, 1);
      Kit.cot(c, Kit.FH + "hide_bedroll", bunk[0], y, bunk[1], in);
      cab.set(1, y, d - 2, Kit.FH + "lit_lodge_table[facing=" + east.id() + "]");
      cab.set(2, y, d - 2, Kit.FH + "lodge_chair[facing=" + west.id() + "]");
      int[] st = cab.at(w - 2, d - 2);
      if (v == 0) {
         Kit.stores(c, st[0], y, st[1], west, "trapper");
      } else {
         Kit.chest(c, st[0], y, st[1], west, "trapper");
      }
      cab.set(w / 2, y, 1, "minecraft:brown_carpet");
      cab.stove(1, 1, east);
      cab.roof();
      // pelts stretched on the outside of the front wall, a hide on the side
      String[] pelts = {"{id:\"frontierhunts:fur_pelt\",count:1}", "{id:\"minecraft:rabbit_hide\",count:1}", "{id:\"frontierhunts:deer_hide\",count:1}",
         "{id:\"frontierhunts:tanned_hide\",count:1}"};
      for (int x : new int[]{1, w - 2}) {
         int[] p = cab.at(x, -1);
         Kit.itemFrame(c, p[0], y + 1, p[1], out, r.pick(pelts));
      }
      int[] sidePelt = cab.at(-1, 1);
      Kit.itemFrame(c, sidePelt[0], y + 1, sidePelt[1], west, r.pick(pelts));
      int[] lamp = cab.at(w / 2 + 1, -1);
      c.set(lamp[0], y, lamp[1], Kit.FH + "cabin_lantern[facing=" + out.id() + "]");
      // yard: tanning rack, smokehouse, firewood, chopping block, skulls on stumps, sign
      int[] tr = cab.at(-4, 0);
      Kit.groundRect(c, tr[0] - 1, tr[1] - 1, tr[0] + 1, tr[1] + 1, y - 1, 4);
      Kit.wide(c, "tanning_rack", tr[0], y - 1, tr[1], out, null);
      if (v == 0) {
         int[] sm = cab.at(w + 2, 1);
         Kit.groundRect(c, sm[0] - 1, sm[1] - 1, sm[0] + 1, sm[1] + 1, y - 1, 4);
         Kit.wide(c, "smokehouse", sm[0], y - 1, sm[1], east, "lit=true");
         int[] wood = cab.at(w + 2, 4);
         Kit.firewood(c, wood[0], y - 1, wood[1], in, 3, 2, r);
      } else {
         int[] wood = cab.at(w + 1, 1);
         Kit.firewood(c, wood[0], y - 1, wood[1], in, 2, 2, r);
      }
      int[] chop = cab.at(-3, 3);
      Kit.choppingBlock(c, chop[0], y - 1, chop[1], r);
      int[] dr = cab.at(-4, -3);
      Kit.dryingRack(c, dr[0], y - 1, dr[1], east, r.range(1, 4), r.chance(0.5) ? 2 : 1);
      Kit.ground(c, dr[0], dr[1], y - 1, 4);
      for (int i = 0; i < 2; i++) {
         int[] sk = cab.at(i == 0 ? -2 : w + 1, -4);
         Kit.ground(c, sk[0], sk[1], y - 1, 4);
         c.set(sk[0], y - 1, sk[1], "minecraft:stripped_spruce_log[axis=y]");
         String skull = r.pick("bison_skull[antlers=false", "whitetail_skull[antlers=" + r.chance(0.6), "moose_skull[antlers=" + r.chance(0.5));
         c.set(sk[0], y, sk[1], Kit.FH + skull + ",facing=" + out.id() + ",mossy=" + r.chance(0.3) + ",natural=true]");
      }
      int[] sg = cab.at(w + 2, -3);
      Kit.signPost(c, sg[0], y - 1, sg[1], out, "trapper_sign", 3);
      Kit.overgrowth(c, 0, 1, 10, 9.5, 0.12, r, null);
      c.plan.cy = y;
      return true;
   }
}
