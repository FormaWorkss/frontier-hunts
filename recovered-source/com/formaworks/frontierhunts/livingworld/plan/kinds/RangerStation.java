package com.formaworks.frontierhunts.livingworld.plan.kinds;

import com.formaworks.frontierhunts.livingworld.plan.Cabin;
import com.formaworks.frontierhunts.livingworld.plan.Ctx;
import com.formaworks.frontierhunts.livingworld.plan.Dir;
import com.formaworks.frontierhunts.livingworld.plan.Kind;
import com.formaworks.frontierhunts.livingworld.plan.Kit;
import com.formaworks.frontierhunts.livingworld.plan.Rnd;
import com.formaworks.frontierhunts.livingworld.plan.Text;

/**
 * [livingworld] Reserve ranger / game check station: a small log office with a porch, flag pole, a game-check board and a
 * hanging scale out front, the Big-Buck Board, reserve rules. Inside: a lectern with the station logbook, a Frontier
 * Handbook on the wall and a box of handbooks for new hunters, the desk, a bunk and the stove. Always placed once near
 * world spawn in a new world. Variants: check_station, patrol_cabin (smaller, no board).
 */
public final class RangerStation extends Kind {
   public RangerStation() {
      super("ranger_station", 15, "check_station", "patrol_cabin");
   }

   @Override
   public int weight(int v) {
      return v == 0 ? 3 : 2;
   }

   @Override
   public boolean build(Ctx c) {
      int v = c.variant;
      Rnd r = c.rnd;
      int[] sv = c.survey(-7, -7, 7, 8, 3);
      if (sv[3] > 0 || sv[1] - sv[0] > 8) {
         return false;
      }
      int y = sv[2] + 1;
      c.plan.title = this.id + " / " + this.variants[v];
      Kit.pad(c, 0, 1, 10.0, 9.5, y - 1, 4, 8);
      int w = v == 0 ? 7 : 5;
      Cabin cab = new Cabin(c, 0, 0, Dir.NORTH, w, 6, y, 3);
      cab.log = "minecraft:spruce_log";
      cab.corner = "minecraft:stripped_spruce_log";
      cab.shell();
      cab.windows();
      cab.porch(2, true);
      Dir out = cab.dir(Dir.NORTH);
      Dir in = cab.dir(Dir.SOUTH);
      Dir east = cab.dir(Dir.EAST);
      Dir west = cab.dir(Dir.WEST);
      int dx = w / 2;

      // inside: stove, lectern with the logbook, desk with the lamp, the handbook box under a sign, a bunk
      int last = w - 2;
      int[] lec = cab.at(1, 2);
      Kit.lectern(c, lec[0], y, lec[1], east, Text.book("book.rangerlog", 4, "Ranger Station Logbook", "Reserve Ranger"));
      cab.set(1, y, 4, Kit.FH + "lit_lodge_table[facing=" + east.id() + "]");
      cab.set(2, y, 4, Kit.FH + "lodge_chair[facing=" + west.id() + "]");
      int[] map = cab.at(1, 3);
      Kit.itemFrame(c, map[0], y + 1, map[1], east, "{id:\"minecraft:map\",count:1}");
      int[] hb = cab.at(last, 1);
      Kit.chest(c, hb[0], y, hb[1], west, "ranger_handbook");
      int[] wall = cab.at(last, 2);
      c.set(wall[0], y, wall[1], "minecraft:barrel[facing=up,open=false]");
      Kit.wallSign(c, wall[0], y + 1, wall[1], west, "spruce", "ranger_handbook", 4);
      int[] frame = cab.at(dx, 4);
      Kit.itemFrame(c, frame[0], y + 1, frame[1], out, "{id:\"frontierhunts:frontier_handbook\",count:1}");
      if (v == 0) {
         int[] bunk = cab.at(last, 3);
         Kit.cot(c, Kit.FH + "camp_cot", bunk[0], y, bunk[1], in);
         int[] box = cab.at(last - 1, 4);
         Kit.barrel(c, box[0], y, box[1], "up", "ranger");
      } else {
         int[] st = cab.at(last, 3);
         Kit.stores(c, st[0], y, st[1], west, "ranger");
      }
      cab.stove(1, 1, east);
      cab.roof();

      // porch and yard
      int[] hs = cab.at(dx, -2);
      Kit.hangingSign(c, hs[0], y + 2, hs[1], out, "spruce", "ranger_sign", 2);
      int[] pl = cab.at(-1, -2);
      c.set(pl[0], y + 1, pl[1], Kit.FH + "cabin_lantern[facing=" + out.id() + "]");
      int[] fp = cab.at(-3, -4);
      Kit.flagPole(c, fp[0], y - 1, fp[1], out, "green", 7);
      int[] sc = cab.at(w + 1, -4);
      Kit.weighScale(c, sc[0], y - 1, sc[1], east);
      int[] gp = cab.at(w + 1, -1);
      Kit.gamePole(c, gp[0], y - 1, gp[1], east, r.range(0, 2), 0);
      Kit.ground(c, gp[0], gp[1], y - 1, 4);
      int[] chk = cab.at(w + 1, -6);
      Kit.ground(c, chk[0], chk[1], y - 1, 3);
      Kit.sign(c, chk[0], y - 1, chk[1], out, "spruce", "ranger_check", 4);
      int[] rules = cab.at(-3, -6);
      Kit.ground(c, rules[0], rules[1], y - 1, 3);
      Kit.sign(c, rules[0], y - 1, rules[1], out, "spruce", "ranger_rules", 4);
      if (v == 0) {
         int[] bb = cab.at(dx, -7);
         Kit.buckBoard(c, bb[0], y - 1, bb[1], in);
         int[] cb = cab.at(w + 2, 2);
         Kit.wide(c, "contract_board", cb[0], y - 1, cb[1], east, null);
         Kit.ground(c, cb[0], cb[1], y - 1, 4);
         int[] cb2 = cab.at(w + 2, 3);
         Kit.ground(c, cb2[0], cb2[1], y - 1, 4);
      }
      int[] bench = cab.at(-3, 1);
      Kit.ground(c, bench[0], bench[1], y - 1, 3);
      c.set(bench[0], y - 1, bench[1], "minecraft:spruce_stairs[facing=" + west.id() + ",half=bottom,shape=straight,waterlogged=false]");
      int[] wood = cab.at(-2, 4);
      Kit.firewood(c, wood[0], y - 1, wood[1], in, 2, 2, r);
      c.plan.cy = y;
      return true;
   }
}
