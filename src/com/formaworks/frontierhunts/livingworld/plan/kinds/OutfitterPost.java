package com.formaworks.frontierhunts.livingworld.plan.kinds;

import com.formaworks.frontierhunts.livingworld.plan.Cabin;
import com.formaworks.frontierhunts.livingworld.plan.Ctx;
import com.formaworks.frontierhunts.livingworld.plan.Dir;
import com.formaworks.frontierhunts.livingworld.plan.Kind;
import com.formaworks.frontierhunts.livingworld.plan.Kit;
import com.formaworks.frontierhunts.livingworld.plan.Rnd;

/**
 * [livingworld] Outfitter's trading post: a log store with a covered porch. Contract board and hanging sign on the
 * porch, inside a counter with a lamp, the clothing table, bow tuning rack, gun workbench, gun racks, stores, and
 * mannequins in blaze orange; outside a hitch rail, firewood and the Big-Buck Board. Variants: log_store, plank_store.
 */
public final class OutfitterPost extends Kind {
   public OutfitterPost() {
      super("outfitter_post", 16, "log_store", "plank_store");
   }

   @Override
   public boolean build(Ctx c) {
      int v = c.variant;
      Rnd r = c.rnd;
      int[] sv = c.survey(-7, -6, 7, 9, 3);
      if (sv[3] > 0 || sv[1] - sv[0] > 8) {
         return false;
      }
      int y = sv[2] + 1;
      c.plan.title = this.id + " / " + this.variants[v];
      Kit.pad(c, 0, 2, 10.0, 9.5, y - 1, 4, 8);
      // front faces local north; the door cell is (0, 0); building runs back to +z
      Cabin cab = new Cabin(c, 0, 0, Dir.NORTH, 9, 7, y, 4);
      if (v == 1) {
         cab.log = "minecraft:stripped_spruce_log";
         cab.corner = "minecraft:spruce_log";
         cab.gable = "frontierhunts:weathered_planks";
      }
      cab.stilts = sv[1] - sv[0] > 5;
      cab.shell();
      cab.windows();
      cab.porch(3, true);

      // interior (cabin-local x 1..7, z 1..5)
      Dir in = cab.dir(Dir.SOUTH);
      Dir out = cab.dir(Dir.NORTH);
      Dir east = cab.dir(Dir.EAST);
      Dir west = cab.dir(Dir.WEST);
      // counter across the room with the lamp and the till
      for (int x = 3; x <= 5; x++) {
         cab.set(x, y, 3, Kit.FH + (x == 4 ? "lit_lodge_table" : "lodge_table") + "[facing=" + out.id() + "]");
      }
      cab.set(5, y, 4, Kit.FH + "lodge_chair[facing=" + out.id() + "]");
      // back wall: the Gunsmith's Bench and the Reloading Bench [benches] (were the weapons workbench and the bow rack)
      int[] a = cab.at(1, 5);
      Kit.wide(c, "gunsmith_bench", a[0], y, a[1], out, null);
      int[] b = cab.at(6, 5);
      Kit.wide(c, "reloading_bench", b[0], y, b[1], west, null);
      // left wall: the Frontier Workbench [benches] (was the clothing table); right wall: gun racks and stores
      int[] ct = cab.at(1, 2);
      Kit.wide(c, "frontier_workbench", ct[0], y, ct[1], east, null);
      cab.set(7, y, 1, Kit.FH + "gun_rack[bench_part=single,facing=" + west.id() + "]");
      cab.set(7, y, 2, Kit.FH + "gun_rack[bench_part=single,facing=" + west.id() + "]");
      int[] s = cab.at(7, 3);
      Kit.stores(c, s[0], y, s[1], west, "outfitter");
      cab.set(7, y, 4, "minecraft:barrel[facing=up,open=false]");
      cab.set(4, y, 5, Kit.FH + "trophy_plinth[bench_part=single,facing=" + out.id() + "]");
      // hanging lanterns from a tie beam
      // wares on the walls
      String[] wares = {"frontierhunts:grunt_tube", "frontierhunts:duck_call", "frontierhunts:wind_checker", "frontierhunts:bleat_call",
         "frontierhunts:field_arrow", "minecraft:lead", "frontierhunts:scent_cover"};
      int[][] frames = {{1, 4}, {3, 5}, {5, 5}};
      Dir[] faces = {east, out, out};
      for (int i = 0; i < frames.length; i++) {
         int[] p = cab.at(frames[i][0], frames[i][1]);
         Kit.itemFrame(c, p[0], y + 1, p[1], faces[i], "{id:\"" + r.pick(wares) + "\",count:1}");
      }
      // mannequin in orange by the door
      int[] m = cab.at(2, 1);
      Kit.hunterStand(c, m[0], y, m[1], in, r);

      // porch: contract board, hanging sign, bench
      int[] cb = cab.at(1, -2);
      Kit.wide(c, "contract_board", cb[0], y, cb[1], out, null);
      int[] hs = cab.at(4, -3);
      Kit.hangingSign(c, hs[0], y + 3, hs[1], out, "spruce", "outfitter_sign", 3);
      int[] bench = cab.at(6, -1);
      c.set(bench[0], y, bench[1], "minecraft:spruce_stairs[facing=" + in.id() + ",half=bottom,shape=straight,waterlogged=false]");
      int[] bench2 = cab.at(7, -1);
      c.set(bench2[0], y, bench2[1], "minecraft:spruce_stairs[facing=" + in.id() + ",half=bottom,shape=straight,waterlogged=false]");
      int[] lamp = cab.at(-1, -3);
      c.set(lamp[0], y + 1, lamp[1], Kit.FH + "cabin_lantern[facing=" + out.id() + "]");
      int[] hours = cab.at(5, -1);
      Kit.wallSign(c, hours[0], y + 1, hours[1], out, "spruce", "outfitter_hours", 4);
      cab.stove(7, 5, west);
      cab.roof();
      cab.tieBeam(3);
      cab.hangingLantern(2, 3);
      cab.hangingLantern(6, 3);

      // yard: hitch rail, firewood, the board
      int[] hr = cab.at(-3, -6);
      Kit.hitchRail(c, hr[0], y - 1 + 0, hr[1], east, 3);
      int[] fw = cab.at(9, 2);
      Kit.firewood(c, fw[0], y - 1 + 0, fw[1], in, 4, 2, r);
      if (r.chance(0.7)) {
         int[] bb = cab.at(-4, 2);
         Kit.buckBoard(c, bb[0], y - 1 + 0, bb[1], west);
      }
      c.plan.cy = y;
      return true;
   }
}
