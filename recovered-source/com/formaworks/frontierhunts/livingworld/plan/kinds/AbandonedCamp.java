package com.formaworks.frontierhunts.livingworld.plan.kinds;

import com.formaworks.frontierhunts.livingworld.plan.Ctx;
import com.formaworks.frontierhunts.livingworld.plan.Dir;
import com.formaworks.frontierhunts.livingworld.plan.Kind;
import com.formaworks.frontierhunts.livingworld.plan.Kit;
import com.formaworks.frontierhunts.livingworld.plan.Layout;
import com.formaworks.frontierhunts.livingworld.plan.Place;
import com.formaworks.frontierhunts.livingworld.plan.Plan;
import com.formaworks.frontierhunts.livingworld.plan.Rnd;

/**
 * [livingworld] A camp somebody left in a hurry: cold fire ring, a flattened tent, one tent still standing with its door
 * open, a meat pole with spoiled quarters, gear strewn about, bones, grass and brush growing back, and notes/a journal
 * that tell what happened. Variants: storm_wrecked (fallen tree across camp), bear_visited (torn-up stores and bones),
 * old_outfit (long abandoned, mossy and overgrown).
 */
public final class AbandonedCamp extends Kind {
   public AbandonedCamp() {
      super("abandoned_camp", 14, "storm_wrecked", "bear_visited", "old_outfit");
   }

   @Override
   public boolean build(Ctx c) {
      int v = c.variant;
      int[] sv = c.survey(-9, -8, 9, 8, 3);
      if (sv[3] > 0 || sv[1] - sv[0] > 6) {
         return false;
      }
      int y = sv[2];
      Rnd r = c.rnd;
      double rx = 8.5;
      double rz = 7.5;
      Kit.pad(c, 0, 0, rx, rz, y, 4, 8);
      Layout l = new Layout();
      var in = Place.ellipse(rx - 0.5, rz - 0.5);
      c.plan.title = this.id + " / " + this.variants[v];

      // cold fire ring with stones knocked out of place
      l.reserve(-3, -3, 3, 3);
      Kit.fireRing(c, 0, y, 0, false, 2, r.fork(1), false);
      for (int i = 0; i < 3; i++) {
         Dir d = r.pick(Dir.values());
         c.air(d.dx + (d.dz != 0 ? r.range(-1, 1) : 0), y, d.dz + (d.dx != 0 ? r.range(-1, 1) : 0));
      }
      c.set(r.range(-3, 3), y, r.range(-3, 3), Kit.FH + "river_stone[form=" + r.range(1, 3) + ",waterlogged=false]", Plan.IF_FREE | Plan.SUPPORTED);

      // flattened tent(s)
      int flat = v == 0 ? 2 : 1;
      for (int i = 0; i < flat; i++) {
         Place p = Place.ring(l, r, 0, 0, 4.0, rz - 2.0, -150, 150, -1, -1, 1, 3, 0, null, false, in);
         if (p != null) {
            Kit.collapsedTent(c, p.x, y, p.z, p.f, r.fork(20 + i));
         }
      }
      // one tent still standing, door open
      String design = r.pick(HuntingCamp.COMPACT);
      int[][] cells = Kit.compactCells(design);
      int x0 = 0, x1 = 0, z1 = 0;
      for (int[] cell : cells) {
         x0 = Math.min(x0, cell[0]);
         x1 = Math.max(x1, cell[0]);
         z1 = Math.max(z1, cell[2]);
      }
      Place t = Place.ring(l, r, 0, 0, 4.5, rz - 1.5, -140, 140, x0, 0, x1, z1, 1, null, true, in);
      if (t != null) {
         Kit.compactTent(c, design, t.x, y, t.z, t.f, true);
         l.reserve(t.x + t.f.dx, t.z + t.f.dz, t.x + t.f.dx, t.z + t.f.dz);
      }

      // meat pole: spoiled quarters (or bare in an old camp)
      Place pole = Place.ring(l, r, 0, 0, 4.5, rx - 1.5, 0, 360, -1, 0, 1, 0, 1, null, true, in);
      if (pole != null) {
         Dir along = pole.f.cw();
         Kit.gamePole(c, pole.x, y, pole.z, along, v == 2 ? 0 : r.range(1, 3), 2);
         Kit.dryingRack(c, pole.x + along.dx, y, pole.z + along.dz, along, 0, 0);
         Kit.ground(c, pole.x, pole.z, y, 4);
         Kit.ground(c, pole.x + along.dx, pole.z + along.dz, y, 4);
      }

      // the stores: tipped barrel, an open-to-the-sky chest with what is left
      Place st = Place.ring(l, r, 0, 0, 3.5, rx - 1.0, 0, 360, 0, 0, 1, 0, 1, null, true, in);
      if (st != null) {
         Kit.chest(c, st.x, y, st.z, st.f, "abandoned");
         Dir along = st.f.cw();
         Kit.barrel(c, st.x + along.dx, y, st.z + along.dz, along.id(), null);
      }
      // a sign left behind, leaning
      Place sg = Place.ring(l, r, 0, 0, rx - 2.0, rx - 1.0, 120, 240, 0, 0, 0, 0, 1, null, true, in);
      if (sg != null) {
         Plan.Block b = c.set(sg.x, y, sg.z, "minecraft:spruce_sign[rotation=" + r.range(0, 15) + ",waterlogged=false]");
         b.nbt = com.formaworks.frontierhunts.livingworld.plan.Text.sign("abandoned_sign." + (v == 1 ? 0 : 1), 4, "black", false);
      }
      // scattered gear
      String[] gear = {Kit.FH + "hide_bedroll", "minecraft:white_carpet", Kit.FH + "stacked_firewood", Kit.FH + "cabin_lantern", "minecraft:cauldron",
         Kit.FH + "lodge_chair", "minecraft:barrel"};
      int n = r.range(4, 7);
      for (int i = 0; i < n; i++) {
         Place g = Place.ring(l, r, 0, 0, 2.5, rx - 1.0, 0, 360, 0, 0, 0, 0, 0, null, true, in);
         if (g == null) {
            continue;
         }
         String what = r.pick(gear);
         Dir d = r.pick(Dir.values());
         switch (what) {
            case Kit.FH + "hide_bedroll" -> {
               if (l.free(g.x + d.dx, g.z + d.dz, g.x + d.dx, g.z + d.dz)) {
                  l.reserve(g.x + d.dx, g.z + d.dz, g.x + d.dx, g.z + d.dz);
                  Kit.cot(c, what, g.x, y, g.z, d);
               }
            }
            case "minecraft:barrel" -> c.set(g.x, y, g.z, "minecraft:barrel[facing=" + d.id() + ",open=true]");
            case "minecraft:white_carpet" -> c.set(g.x, y, g.z, r.chance(0.5) ? "minecraft:white_carpet" : "minecraft:light_gray_carpet");
            case Kit.FH + "lodge_chair" -> c.set(g.x, y, g.z, what + "[facing=" + d.id() + "]");
            case "minecraft:cauldron" -> c.set(g.x, y, g.z, what);
            default -> c.set(g.x, y, g.z, what + "[facing=" + d.id() + "]");
         }
      }
      // bones: the bear helped itself / old kills
      int bones = v == 1 ? r.range(4, 6) : r.range(1, 3);
      for (int i = 0; i < bones; i++) {
         Place b = Place.ring(l, r, 0, 0, 2.0, rx + 1.0, 0, 360, 0, 0, 0, 0, 0, null, true, null);
         if (b == null) {
            continue;
         }
         Dir d = r.pick(Dir.values());
         boolean mossy = v == 2 || r.chance(0.3);
         String spec = r.chance(0.25)
            ? Kit.FH + "whitetail_skull[antlers=" + r.chance(0.4) + ",facing=" + d.id() + ",mossy=" + mossy + ",natural=true]"
            : Kit.FH + "scattered_bones[facing=" + d.id() + ",mossy=" + mossy + ",natural=true,part=" + r.pick("ribs", "spine", "legs") + "]";
         c.drop(b.x, y, b.z, spec);
      }
      if (v == 0) {
         // the tree that came down in the storm, right through camp
         Dir along = r.pick(Dir.values());
         int sx = -along.dx * 6 + along.cw().dx * r.range(-3, 3);
         int sz = -along.dz * 6 + along.cw().dz * r.range(-3, 3);
         Kit.fallenTree(c, sx, y, sz, along, r.range(9, 12), r, r.chance(0.5) ? "spruce" : "birch");
      }
      if (v == 1) {
         // ripped open: wool tufts and a smashed crate
         for (int i = 0; i < 4; i++) {
            c.drop(r.range(-6, 6), y, r.range(-5, 5), r.chance(0.5) ? "minecraft:white_carpet" : Kit.FH + "forest_sticks[facing=north,variant=" + r.nextInt(6) + "]");
         }
      }
      // nature taking it back
      Kit.overgrowth(c, 0, 0, rx + 1.5, rz + 1.5, v == 2 ? 0.45 : 0.22, r, l);
      if (v == 2) {
         for (int i = 0; i < 10; i++) {
            Kit.surface(c, r.range(-6, 6), r.range(-5, 5), r.chance(0.5) ? "minecraft:moss_block" : "minecraft:podzol");
         }
      }
      c.plan.cy = y;
      return true;
   }
}
