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
 * [livingworld] An active hunting camp: tents in an arc around a lit fire ring, meat pole with hanging quarters, jerky on
 * the drying rack, firewood, a camp table, stores, an unclaimed camp post, lanterns, an outhouse in the trees and
 * sometimes the camp ATV. Variants: deer camp (the full set incl. a Big-Buck Board), spike camp (two hunters, light),
 * family camp (walk-in tents), bowhunter camp (practice target, bow rack).
 */
public final class HuntingCamp extends Kind {
   public static final String[] COMPACT = {"backpacker_dome_tent", "hunters_canvas_tent", "solo_ridge_tent"};
   public static final String[] WALK_IN = {"woodland_camp_tent", "canvas_wall_tent", "bell_tent", "trail_dome_tent", "family_cabin_tent"};

   public HuntingCamp() {
      super("hunting_camp", 18, "deer_camp", "spike_camp", "family_camp", "bowhunter_camp");
   }

   @Override
   public int weight(int v) {
      return v == 0 ? 4 : v == 1 ? 3 : 2;
   }

   @Override
   public boolean build(Ctx c) {
      int v = c.variant;
      boolean spike = v == 1;
      int[] sv = c.survey(-11, -10, 11, 10, 4);
      if (sv[3] > 0 || sv[1] - sv[0] > 7) {
         return false;
      }
      int y = sv[2];
      Rnd r = c.rnd;
      double rx = spike ? 7.5 : 10.5;
      double rz = spike ? 7.0 : 9.5;
      Kit.pad(c, 0, 0, rx, rz, y, 5, 9);
      Layout l = new Layout();
      var in = Place.ellipse(rx - 0.5, rz - 0.5);
      c.plan.title = this.id + " / " + this.variants[v];

      // fire ring
      l.reserve(-3, -3, 3, 3);
      Kit.fireRing(c, 0, y, 0, true, spike ? 2 : 3, r.fork(1), true);

      // entrance path (south)
      for (int z = 3; z <= (int)rz + 4; z++) {
         int wob = (int)Math.round(Math.sin(z * 0.7 + r.nextDouble()) * 0.6);
         for (int x = -1; x <= 0; x++) {
            Kit.surface(c, x + wob, z, r.chance(0.65) ? "minecraft:dirt_path" : "minecraft:coarse_dirt");
         }
         l.reserve(-1 + wob, z, wob, z);
      }

      // tents
      int compact = spike ? 2 : v == 2 ? r.range(1, 2) : r.range(3, 4);
      int walkIn = v == 2 ? r.range(1, 2) : v == 0 && r.chance(0.5) ? 1 : 0;
      for (int i = 0; i < walkIn; i++) {
         Place p = Place.ring(l, r, 0, 0, 6.5, rz - 2.5, -125, 125, -2, -2, 2, 2, 1, null, true, in);
         if (p == null) {
            break;
         }
         int[] o = Kit.off(0, -2, p.f);
         String design = v == 2 ? r.pick("bell_tent", "family_cabin_tent", "canvas_wall_tent", "trail_dome_tent") : r.pick("canvas_wall_tent", "woodland_camp_tent");
         Kit.walkInTent(c, design, p.x, y, p.z, p.f);
         furnishWalkIn(c, p.x, y, p.z, p.f, r.fork(10 + i), design);
         l.reserve(p.x + o[0] - 1, p.z + o[1] - 1, p.x + o[0] + 1, p.z + o[1] + 1);
      }
      for (int i = 0; i < compact; i++) {
         String design = spike ? (i == 0 ? "solo_ridge_tent" : r.pick(COMPACT)) : r.pick(COMPACT);
         int[][] cells = Kit.compactCells(design);
         int x0 = 0, x1 = 0, z1 = 0;
         for (int[] cell : cells) {
            x0 = Math.min(x0, cell[0]);
            x1 = Math.max(x1, cell[0]);
            z1 = Math.max(z1, cell[2]);
         }
         Place p = Place.ring(l, r, 0, 0, 5.0, spike ? rz - 1.5 : rz - 2.0, -130, 130, x0, 0, x1, z1, 1, null, true, in);
         if (p == null) {
            continue;
         }
         Kit.compactTent(c, design, p.x, y, p.z, p.f, r.chance(0.4));
         // keep the doorway clear
         l.reserve(p.x + p.f.dx, p.z + p.f.dz, p.x + p.f.dx, p.z + p.f.dz);
         if (r.chance(0.35)) {
            Dir side = p.f.cw();
            int[] o = Kit.off(x1 + 1, 1, p.f);
            int bx = p.x + o[0];
            int bz = p.z + o[1];
            if (l.free(bx, bz, bx, bz)) {
               l.reserve(bx, bz, bx, bz);
               // [gear20] a seat or a lantern by the tent, not another loot barrel
               if (r.chance(0.5)) {
                  c.set(bx, y, bz, Kit.FH + "log_stump_seat[facing=" + p.f.id() + "]");
               } else {
                  c.set(bx, y, bz, Kit.FH + "cabin_lantern[facing=" + side.id() + "]");
               }
            }
         }
      }

      // meat pole + drying rack
      Place pole = Place.ring(l, r, 0, 0, 5.5, rx - 1.5, 60, 300, -1, 0, 1, 0, 1, null, true, in);
      if (pole != null) {
         Dir along = pole.f.cw();
         int state = r.chance(0.7) ? 1 : 0;
         int load = spike ? r.range(1, 2) : r.range(2, 4);
         Kit.gamePole(c, pole.x - along.dx, y, pole.z - along.dz, along, Math.min(4, load), state);
         Kit.gamePole(c, pole.x, y, pole.z, along, r.chance(0.5) ? Math.max(0, load - 2) : 0, state);
         Kit.dryingRack(c, pole.x + along.dx, y, pole.z + along.dz, along, r.range(2, 4), r.chance(0.7) ? 2 : 1);
         for (int k = -1; k <= 1; k++) {
            Kit.ground(c, pole.x + along.dx * k, pole.z + along.dz * k, y, 4);
         }
      }

      // firewood + chopping block
      Place wood = Place.ring(l, r, 0, 0, 5.0, rx - 1.0, 0, 360, -2, 0, 1, 1, 1, null, false, in);
      if (wood != null) {
         Dir along = wood.f.cw();
         Kit.firewood(c, wood.x - along.dx * 2, y, wood.z - along.dz * 2, along, spike ? 2 : 3, 2, r);
         Kit.choppingBlock(c, wood.x + along.dx * 2, y, wood.z + along.dz * 2, r);
      }

      // camp table with lantern and chairs
      if (!spike) {
         Place t = Place.ring(l, r, 0, 0, 4.5, 7.5, 0, 360, -1, -1, 1, 1, 0, null, true, in);
         if (t != null) {
            Dir along = t.f.cw();
            c.set(t.x, y, t.z, Kit.FH + "lit_lodge_table[facing=" + t.f.id() + "]");
            c.set(t.x + along.dx, y, t.z + along.dz, Kit.FH + "lodge_table[facing=" + t.f.id() + "]");
            c.set(t.x + t.f.dx, y, t.z + t.f.dz, Kit.FH + "camp_chair[facing=" + t.f.opposite().id() + "]");
            c.set(t.x - t.f.dx + along.dx, y, t.z - t.f.dz + along.dz, Kit.FH + "camp_chair[facing=" + t.f.id() + "]");
         }
      }

      // cook fly over the camp kitchen
      if (v == 0 && r.chance(0.5) || v == 2 && r.chance(0.7)) {
         Place fly = Place.ring(l, r, 0, 0, 5.0, rx - 2.0, 0, 360, -1, 0, 1, 3, 1, null, true, in);
         if (fly != null) {
            Dir along = fly.f.opposite();
            Dir side = along.cw();
            Kit.kitchenFly(c, fly.x, y, fly.z, along, 4, r);
            c.set(fly.x + along.dx, y, fly.z + along.dz, Kit.FH + "lit_lodge_table[facing=" + side.id() + "]");
            c.set(fly.x + along.dx * 2, y, fly.z + along.dz * 2, Kit.FH + "lodge_table[facing=" + side.id() + "]");
            c.set(fly.x + along.dx + side.dx, y, fly.z + along.dz + side.dz, Kit.FH + "lodge_chair[facing=" + side.opposite().id() + "]");
            c.set(fly.x + along.dx * 3, y, fly.z + along.dz * 3, "minecraft:smoker[facing=" + side.id() + ",lit=false]");
            c.set(fly.x, y, fly.z, Kit.FH + "stacked_firewood[facing=" + side.id() + "]");
         }
      }

      // stores and cooler
      Place st = Place.ring(l, r, 0, 0, 5.0, rx - 1.0, 0, 360, 0, 0, 1, 0, 1, null, true, in);
      if (st != null) {
         Dir along = st.f.cw();
         Kit.stores(c, st.x, y, st.z, st.f, "camp_stores");
         Kit.chest(c, st.x + along.dx, y, st.z + along.dz, st.f, "camp_cooler");
      }

      // camp post at the entrance, Big-Buck Board facing into camp
      int ez = (int)Math.round(rz) - 1;
      Kit.campPost(c, 3, y, ez - 1, Dir.SOUTH);
      l.reserve(3, ez - 1, 3, ez - 1);
      Kit.ground(c, -3, ez - 1, y, 3);
      Kit.sign(c, -3, y, ez - 1, Dir.SOUTH, "spruce", "camp_rules." + r.nextInt(3), 4);
      l.reserve(-3, ez - 1, -3, ez - 1);
      if (v == 0 || v == 2 && r.chance(0.5)) {
         Place b = Place.ring(l, r, 0, 0, 5.5, rx - 1.5, 30, 330, -1, 0, 1, 0, 1, null, true, in);
         if (b != null) {
            Kit.buckBoard(c, b.x, y, b.z, b.f);
         }
      }

      // bowhunters practise; family camps hang out
      if (v == 3) {
         Place tg = Place.ring(l, r, 0, 0, rx - 3, rx - 1, 45, 135, 0, 0, 0, 0, 1, null, true, in);
         if (tg != null) {
            c.set(tg.x, y, tg.z, Kit.FH + "shooting_target[facing=" + tg.f.id() + "]");
         }
         Place bs = Place.ring(l, r, 0, 0, 4.5, 7.5, 0, 360, 0, 0, 1, 0, 1, null, true, in);
         if (bs != null) {
            Kit.wide(c, "reloading_bench", bs.x, y, bs.z, bs.f, null); // [benches] was the bow tuning rack
         }
         Place st2 = Place.ring(l, r, 0, 0, 4.5, 8.0, 0, 360, 0, 0, 0, 0, 1, null, true, in);
         if (st2 != null) {
            c.set(st2.x, y, st2.z, Kit.FH + "bow_stand[facing=" + st2.f.id() + "]");
         }
      }
      if (v == 2 || v == 0 && r.chance(0.5)) {
         Place hs = Place.ring(l, r, 0, 0, 4.5, 8.0, 0, 360, 0, 0, 0, 0, 1, null, true, in);
         if (hs != null) {
            Kit.hunterStand(c, hs.x, y, hs.z, hs.f, r);
         }
      }
      if (v != 1 && r.chance(0.5)) {
         Place tb = Place.ring(l, r, 0, 0, 5.0, rx - 1.0, 0, 360, 0, 0, 1, 0, 1, null, true, in);
         if (tb != null) {
            Kit.wide(c, r.chance(0.5) ? "tanning_rack" : "frontier_workbench", tb.x, y, tb.z, tb.f, null); // [benches] was the tent bench
         }
      }

      // lanterns around the clearing
      int lamps = spike ? 1 : r.range(2, 3);
      for (int i = 0; i < lamps; i++) {
         Place lp = Place.ring(l, r, 0, 0, rx - 2.5, rx - 1.0, 0, 360, 0, 0, 0, 0, 1, null, true, in);
         if (lp != null) {
            Kit.lanternPost(c, lp.x, y, lp.z, lp.f, "minecraft:spruce_fence");
         }
      }

      // the camp ATV, parked by the entrance track
      if (r.chance(v == 0 ? 0.45 : v == 2 ? 0.35 : 0.2)) {
         int side = r.chance(0.5) ? 1 : -1;
         int ax = side * ((int)rx - 2);
         int az = (int)rz - 3;
         if (l.free(ax - 1, az - 2, ax + 1, az + 2)) {
            l.reserve(ax - 1, az - 2, ax + 1, az + 2);
            Kit.groundRect(c, ax - 1, az - 2, ax + 1, az + 2, y, 3);
            Kit.atv(c, ax + 0.5, y, az + 0.5, r.chance(0.5) ? Dir.NORTH : Dir.SOUTH, r, false);
         }
      }

      // outhouse back in the trees
      if (!spike || r.chance(0.4)) {
         double a = Math.toRadians(r.range(-150, 150));
         int ox = (int)Math.round(Math.sin(a) * (rx + 5));
         int oz = -(int)Math.round(Math.cos(a) * (rz + 5));
         int oy = c.floor(ox, oz);
         if (!c.wet(ox, oz) && Math.abs(oy - y) <= 3) {
            Kit.outhouse(c, ox, oy, oz, Layout.toward(ox, oz, 0, 0), "minecraft:spruce_planks");
         }
      }

      // lived-in clutter
      clutter(c, l, r, y, rx, rz, 10 + (spike ? 0 : 8));
      c.plan.cy = y;
      return true;
   }

   static void furnishWalkIn(Ctx c, int x, int y, int z, Dir f, Rnd r, String design) {
      Dir head = Kit.rel(Dir.SOUTH, f);
      int[] a = Kit.inTent(x, z, f, -1, 0);
      Kit.cot(c, Kit.FH + "camp_cot", a[0], y, a[1], head);
      int[] b = Kit.inTent(x, z, f, 1, 0);
      if (r.chance(0.6)) {
         Kit.cot(c, Kit.FH + (r.chance(0.5) ? "hide_bedroll" : "camp_cot"), b[0], y, b[1], head);
      } else {
         // [gear20] a bedroll, not one more chest
         Kit.cot(c, Kit.FH + "hide_bedroll", b[0], y, b[1], head);
      }
      int[] m = Kit.inTent(x, z, f, 0, 1);
      c.set(m[0], y, m[1], Kit.FH + "cabin_lantern[facing=" + Kit.rel(Dir.NORTH, f).id() + "]");
      if (design.equals("canvas_wall_tent") || design.equals("bell_tent")) {
         int[] s = Kit.inTent(x, z, f, 1, 1);
         if (c.at(s[0], y, s[1]) == null || c.at(s[0], y, s[1]).spec.equals("minecraft:air")) {
            c.set(s[0], y, s[1], Kit.FH + "lodge_stores[bench_part=single,facing=" + Kit.rel(Dir.NORTH, f).id() + "]").nbt = null;
         }
      }
   }

   /** sticks, pebbles, ferns and odds and ends on the camp ground */
   static void clutter(Ctx c, Layout l, Rnd r, int y, double rx, double rz, int n) {
      String[] bits = {"forest_sticks", "river_pebbles", "river_pebbles", "forest_sticks", "fallen_branch"};
      for (int i = 0; i < n; i++) {
         int x = r.range(-(int)rx, (int)rx);
         int z = r.range(-(int)rz, (int)rz);
         if (l.taken(x, z) || (x / rx) * (x / rx) + (z / rz) * (z / rz) > 1.1) {
            continue;
         }
         String b = r.pick(bits);
         Dir d = r.pick(Dir.values());
         String spec = switch (b) {
            case "forest_sticks" -> Kit.FH + "forest_sticks[facing=" + d.id() + ",variant=" + r.nextInt(6) + "]";
            case "river_pebbles" -> Kit.FH + "river_pebbles[form=" + r.nextInt(4) + ",waterlogged=false]";
            default -> Kit.FH + "fallen_branch[facing=" + d.id() + ",snapped=" + r.chance(0.5) + "]";
         };
         c.decor(x, y, z, spec);
         l.reserve(x, z, x, z);
      }
      // tufts back at the edge of the clearing
      for (int i = 0; i < n; i++) {
         double a = r.range(0, Math.PI * 2);
         double k = r.range(0.8, 1.15);
         int x = (int)Math.round(Math.sin(a) * rx * k);
         int z = (int)Math.round(Math.cos(a) * rz * k);
         if (l.taken(x, z)) {
            continue;
         }
         c.drop(x, y, z, r.chance(0.6) ? "minecraft:short_grass" : "minecraft:fern");
      }
   }
}
