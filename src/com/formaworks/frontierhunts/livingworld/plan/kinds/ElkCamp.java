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
 * [livingworld] High-country elk camp: canvas wall tents with stove pipes, a hitch rail with pack mules (chested, tied)
 * and a saddle horse, hay, a water trough, elk quarters on the meat pole, a bull's skull on a stump, firewood, a camp
 * table. Variants: wall_tent_camp (two or three wall tents), outfitter_camp (bigger, cook tent with a kitchen fly).
 */
public final class ElkCamp extends Kind {
   public ElkCamp() {
      super("elk_camp", 18, "wall_tent_camp", "outfitter_camp");
   }

   @Override
   public boolean build(Ctx c) {
      int v = c.variant;
      int[] sv = c.survey(-12, -10, 12, 10, 4);
      if (sv[3] > 0 || sv[1] - sv[0] > 7) {
         return false;
      }
      int y = sv[2];
      Rnd r = c.rnd;
      double rx = 11.5;
      double rz = 10.0;
      Kit.pad(c, 0, 0, rx, rz, y, 5, 9);
      Layout l = new Layout();
      var in = Place.ellipse(rx - 0.5, rz - 0.5);
      c.plan.title = this.id + " / " + this.variants[v];

      l.reserve(-3, -3, 3, 3);
      Kit.fireRing(c, 0, y, 0, true, 3, r.fork(1), true);
      // hitch rail along the south edge, mules and the saddle horse tied to it
      {
         Dir along = Dir.EAST;
         Dir front = Dir.SOUTH;
         int x0 = -2;
         int z0 = (int)rz - 4;
         l.reserve(-5, z0 - 1, 6, z0 + 2);
         Kit.hitchRail(c, x0, y, z0, along, 4);
         int animals = v == 1 ? 3 : r.range(2, 3);
         for (int i = 0; i < animals; i++) {
            int ax = x0 + 1 + i * 2;
            int az = z0 + 1;
            int px = i % 2 == 0 ? x0 : x0 + 5;
            String leash = "leash:[I;" + c.wx(px, z0) + "," + y + "," + c.wz(px, z0) + "]";
            boolean horse = i == animals - 1 && r.chance(0.6);
            String snbt = horse
               ? "{id:\"minecraft:horse\",Tame:1b,Variant:" + r.pick(0, 1, 2, 3, 257, 513) + ",SaddleItem:{id:\"minecraft:saddle\",count:1},PersistenceRequired:1b," + leash + "}"
               : "{id:\"minecraft:mule\",Tame:1b,ChestedHorse:1b,PersistenceRequired:1b," + leash + ",Items:[{Slot:1b,id:\"frontierhunts:jerky\",count:"
                  + r.range(2, 6) + "},{Slot:2b,id:\"frontierhunts:salt\",count:" + r.range(1, 4) + "},{Slot:3b,id:\"minecraft:hay_block\",count:"
                  + r.range(1, 3) + "}" + (r.chance(0.4) ? ",{Slot:4b,id:\"frontierhunts:game_meat\",count:2}" : "") + "]}";
            c.entity(ax + 0.5, y, az + 0.5, front.opposite().yaw(), snbt);
         }
         Kit.ground(c, x0 - 2, z0, y, 3);
         c.set(x0 - 2, y, z0, "minecraft:hay_block[axis=y]");
         if (r.chance(0.6)) {
            c.set(x0 - 2, y + 1, z0, "minecraft:hay_block[axis=x]");
         }
         Kit.ground(c, x0 - 3, z0, y, 3);
         c.set(x0 - 3, y, z0, "minecraft:hay_block[axis=x]");
         Kit.ground(c, x0 + 7, z0, y, 3);
         c.set(x0 + 7, y, z0, "minecraft:water_cauldron[level=3]");
         for (int x = x0 - 1; x <= x0 + 6; x++) {
            Kit.ground(c, x, z0 + 1, y, 3);
         }
      }

      int tents = v == 1 ? 3 : r.range(2, 3);
      for (int i = 0; i < tents; i++) {
         Place p = Place.ring(l, r, 0, 0, 6.5, rz - 2.0, -110, 110, -2, -2, 2, 2, 1, null, true, in);
         if (p == null) {
            continue;
         }
         String design = i == 0 || r.chance(0.7) ? "canvas_wall_tent" : "bell_tent";
         Kit.walkInTent(c, design, p.x, y, p.z, p.f);
         HuntingCamp.furnishWalkIn(c, p.x, y, p.z, p.f, r.fork(40 + i), design);
         int[] door = Kit.off(0, -3, p.f);
         l.reserve(p.x + door[0], p.z + door[1], p.x + door[0], p.z + door[1]);
      }

      // elk quarters on the meat pole
      Place pole = Place.ring(l, r, 0, 0, 5.5, rx - 1.5, 0, 360, -1, 0, 1, 0, 1, null, true, in);
      if (pole != null) {
         Dir along = pole.f.cw();
         Kit.gamePole(c, pole.x - along.dx, y, pole.z - along.dz, along, 4, 1);
         Kit.gamePole(c, pole.x, y, pole.z, along, r.range(2, 4), r.chance(0.5) ? 1 : 0);
         Kit.gamePole(c, pole.x + along.dx, y, pole.z + along.dz, along, r.range(0, 2), 0);
         for (int k = -1; k <= 1; k++) {
            Kit.ground(c, pole.x + along.dx * k, pole.z + along.dz * k, y, 4);
         }
      }
      // the bull's skull on a stump at the camp edge
      Place sk = Place.ring(l, r, 0, 0, rx - 2.5, rx - 1.0, 140, 220, 0, 0, 0, 0, 1, null, false, in);
      if (sk != null) {
         Kit.ground(c, sk.x, sk.z, y, 4);
         c.set(sk.x, y, sk.z, "minecraft:spruce_log[axis=y]");
         c.set(sk.x, y + 1, sk.z, Kit.FH + "elk_skull[antlers=true,facing=" + sk.f.id() + ",mossy=false,natural=true]");
      }
      Kit.signPost(c, -7, y, (int)rz - 3, Dir.SOUTH, "elk_sign", 3);
      // [gear20] the camp post (flag) at the way in, beside the sign
      Kit.ground(c, -8, (int)rz - 2, y, 3);
      Kit.campPost(c, -8, y, (int)rz - 2, Dir.SOUTH);
      l.reserve(-8, (int)rz - 3, -6, (int)rz - 2);

      // firewood (a lot of it) and the cook area
      Place wood = Place.ring(l, r, 0, 0, 5.0, rx - 1.0, 0, 360, -3, 0, 2, 1, 1, null, false, in);
      if (wood != null) {
         Dir along = wood.f.cw();
         Kit.firewood(c, wood.x - along.dx * 3, y, wood.z - along.dz * 3, along, 4, 2, r);
         Kit.choppingBlock(c, wood.x + along.dx * 2, y, wood.z + along.dz * 2, r);
      }
      if (v == 1) {
         Place fly = Place.ring(l, r, 0, 0, 5.0, 8.0, 0, 360, -1, 0, 1, 3, 1, null, true, in);
         if (fly != null) {
            Dir along = fly.f.opposite();
            Kit.kitchenFly(c, fly.x, y, fly.z, along, 4, r);
            Dir side = along.cw();
            c.set(fly.x + along.dx, y, fly.z + along.dz, Kit.FH + "lit_lodge_table[facing=" + side.id() + "]");
            c.set(fly.x + along.dx * 2, y, fly.z + along.dz * 2, Kit.FH + "lodge_table[facing=" + side.id() + "]");
            c.set(fly.x + along.dx + side.dx, y, fly.z + along.dz + side.dz, Kit.FH + "camp_chair[facing=" + side.opposite().id() + "]");
            c.set(fly.x + along.dx * 2 - side.dx, y, fly.z + along.dz * 2 - side.dz, Kit.FH + "camp_chair[facing=" + side.id() + "]");
            c.set(fly.x + along.dx * 3, y, fly.z + along.dz * 3, "minecraft:smoker[facing=" + side.id() + ",lit=false]");
            Kit.barrel(c, fly.x, y, fly.z, "up", "elk_camp");
         }
      } else {
         Place t = Place.ring(l, r, 0, 0, 4.5, 7.5, 0, 360, -1, -1, 1, 1, 0, null, true, in);
         if (t != null) {
            Dir along = t.f.cw();
            c.set(t.x, y, t.z, Kit.FH + "lit_lodge_table[facing=" + t.f.id() + "]");
            c.set(t.x + along.dx, y, t.z + along.dz, Kit.FH + "lodge_table[facing=" + t.f.id() + "]");
            c.set(t.x + t.f.dx, y, t.z + t.f.dz, Kit.FH + "camp_chair[facing=" + t.f.opposite().id() + "]");
         }
      }
      Place st = Place.ring(l, r, 0, 0, 5.0, rx - 1.0, 0, 360, 0, 0, 1, 0, 1, null, true, in);
      if (st != null) {
         Kit.stores(c, st.x, y, st.z, st.f, "elk_camp");
         Kit.chest(c, st.x + st.f.cw().dx, y, st.z + st.f.cw().dz, st.f, "camp_cooler");
      }
      // saddles and panniers resting on a rail
      Place rack = Place.ring(l, r, 0, 0, 5.0, rx - 1.5, 0, 360, -1, 0, 1, 0, 1, null, true, in);
      if (rack != null) {
         Dir along = rack.f.cw();
         for (int k = -1; k <= 1; k++) {
            Kit.ground(c, rack.x + along.dx * k, rack.z + along.dz * k, y, 3);
            c.set(rack.x + along.dx * k, y, rack.z + along.dz * k, "minecraft:spruce_fence");
         }
         c.set(rack.x - along.dx, y + 1, rack.z - along.dz, "minecraft:barrel[facing=" + rack.f.id() + ",open=false]");
         c.set(rack.x + along.dx, y + 1, rack.z + along.dz, "minecraft:barrel[facing=" + rack.f.id() + ",open=false]");
         c.set(rack.x, y + 1, rack.z, "minecraft:brown_carpet");
      }
      for (int i = 0; i < 2; i++) {
         Place lp = Place.ring(l, r, 0, 0, rx - 2.5, rx - 1.0, 0, 360, 0, 0, 0, 0, 1, null, true, in);
         if (lp != null) {
            Kit.lanternPost(c, lp.x, y, lp.z, lp.f, "minecraft:spruce_fence");
         }
      }
      if (r.chance(0.5)) {
         Place hs = Place.ring(l, r, 0, 0, 4.5, 8.0, 0, 360, 0, 0, 0, 0, 1, null, true, in);
         if (hs != null) {
            Kit.hunterStand(c, hs.x, y, hs.z, hs.f, r);
         }
      }
      HuntingCamp.clutter(c, l, r, y, rx, rz, 16);
      c.plan.cy = y;
      return true;
   }
}
