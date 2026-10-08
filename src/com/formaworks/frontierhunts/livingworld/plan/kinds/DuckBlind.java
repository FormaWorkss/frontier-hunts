package com.formaworks.frontierhunts.livingworld.plan.kinds;

import com.formaworks.frontierhunts.livingworld.plan.Ctx;
import com.formaworks.frontierhunts.livingworld.plan.Dir;
import com.formaworks.frontierhunts.livingworld.plan.Kind;
import com.formaworks.frontierhunts.livingworld.plan.Kit;
import com.formaworks.frontierhunts.livingworld.plan.Plan;
import com.formaworks.frontierhunts.livingworld.plan.Rnd;

/**
 * [livingworld] Duck blind on a lake or marsh edge: a brushed-in plank blind on log stilts reaching over the water, a
 * spread of mallard decoys on the open water in front, reeds along the shore, a duck boat pulled up beside it, a shell
 * box inside and a sign. Needs open water next to the site (otherwise no structure). Variants: shore_blind, marsh_blind.
 */
public final class DuckBlind extends Kind {
   public DuckBlind() {
      super("duck_blind", 16, "shore_blind", "marsh_blind");
   }

   @Override
   public boolean quickCheck(Ctx c) {
      if (c.wet(0, 0)) {
         return false;
      }
      for (Dir d : Dir.values()) {
         if (c.wet(d.dx * 6, d.dz * 6) || c.wet(d.dx * 11, d.dz * 11)) {
            return true;
         }
      }
      return false;
   }

   @Override
   public boolean build(Ctx c) {
      Rnd r = c.rnd;
      int v = c.variant;
      if (c.wet(0, 0)) {
         return false;
      }
      // find the closest open water along the four axes
      Dir best = null;
      int bestDist = 99;
      for (Dir d : Dir.values()) {
         for (int k = 2; k <= 14; k++) {
            if (c.wet(d.dx * k, d.dz * k)) {
               boolean open = true;
               for (int m = 2; m <= 6 && open; m += 2) {
                  open = c.wet(d.dx * (k + m), d.dz * (k + m))
                     && c.surface(d.dx * (k + m), d.dz * (k + m)) - c.floor(d.dx * (k + m), d.dz * (k + m)) >= 1;
               }
               if (open && k < bestDist) {
                  bestDist = k;
                  best = d;
               }
               break;
            }
         }
      }
      if (best == null) {
         return false;
      }
      Dir f = best;
      Dir side = f.cw();
      // shore cell = last dry column
      int sx = f.dx * (bestDist - 1);
      int sz = f.dz * (bestDist - 1);
      int g = c.floor(sx, sz);
      int ws = c.surface(f.dx * bestDist, f.dz * bestDist);
      if (Math.abs(g - ws) > 3) {
         return false;
      }
      int floorY = Math.max(g, ws) + 1;
      c.plan.title = this.id + " / " + this.variants[v];
      // approach on the land side
      Kit.pad(c, sx - f.dx * 4, sz - f.dz * 4, 3.5, 3.5, floorY - 1, 3, 6);
      for (int k = 1; k <= 6; k++) {
         int px = sx - f.dx * k;
         int pz = sz - f.dz * k;
         Kit.surface(c, px, pz, r.chance(0.6) ? "minecraft:dirt_path" : "minecraft:coarse_dirt");
      }
      // deck: 3 wide, from the shore cell 3 out over the water
      for (int a = -1; a <= 1; a++) {
         for (int b = 0; b <= 2; b++) {
            int px = sx + side.dx * a + f.dx * b;
            int pz = sz + side.dz * a + f.dz * b;
            boolean post = a != 0 && (b == 0 || b == 2);
            Plan.Shape s = c.shape(px, pz, b == 0 ? floorY - 1 : Math.min(c.floor(px, pz), floorY - 1), b == 0 ? 1.0 : 0.0);
            s.dryOnly = false;
            s.clear = 0;
            s.canopy = 20;
            if (post) {
               s.top = floorY - 1;
               s.weight = 1.0;
               s.fill = "minecraft:spruce_log[axis=y]";
               s.surface = "minecraft:spruce_log[axis=y]";
               s.maxFill = 12;
            }
            c.set(px, floorY - 1, pz, "minecraft:spruce_planks");
            for (int h = 0; h < 3; h++) {
               c.air(px, floorY + h, pz);
            }
            if (post) {
               c.set(px, floorY, pz, "minecraft:spruce_fence");
               c.set(px, floorY + 1, pz, "minecraft:spruce_fence");
            }
         }
      }
      // walls: brushy front and sides (fence + persistent leaves), open back with the doorway
      String leaf = v == 1 ? "minecraft:birch_leaves[distance=1,persistent=true,waterlogged=false]" : "minecraft:spruce_leaves[distance=1,persistent=true,waterlogged=false]";
      for (int a = -1; a <= 1; a++) {
         int fx = sx + side.dx * a + f.dx * 2;
         int fz = sz + side.dz * a + f.dz * 2;
         if (a == 0) {
            c.set(fx, floorY, fz, "minecraft:spruce_fence");
         }
         c.set(fx, floorY + 2, fz, leaf);
      }
      for (int b = 0; b <= 2; b++) {
         for (int a : new int[]{-1, 1}) {
            int px = sx + side.dx * a + f.dx * b;
            int pz = sz + side.dz * a + f.dz * b;
            if (b == 1) {
               c.set(px, floorY, pz, "minecraft:spruce_fence");
               c.set(px, floorY + 1, pz, leaf);
            }
            c.set(px, floorY + 2, pz, leaf);
         }
         int px = sx + f.dx * b;
         int pz = sz + f.dz * b;
         if (b < 2) {
            c.set(px, floorY + 2, pz, "minecraft:spruce_slab[type=top,waterlogged=false]");
         }
      }
      // roof leaves with a shooting gap over the front
      c.set(sx + f.dx * 2, floorY + 2, sz + f.dz * 2, "minecraft:air");
      // bench and shell box
      c.set(sx + side.dx, floorY, sz + side.dz, "minecraft:spruce_stairs[facing=" + f.opposite().id() + ",half=bottom,shape=straight,waterlogged=false]");
      c.set(sx - side.dx, floorY, sz - side.dz, "minecraft:spruce_stairs[facing=" + f.opposite().id() + ",half=bottom,shape=straight,waterlogged=false]");
      Kit.barrel(c, sx + side.dx + f.dx, floorY, sz + side.dz + f.dz, "up", "blind");
      // decoy spread on the open water
      int decoys = r.range(6, 11);
      for (int i = 0; i < decoys * 3 && decoys > 0; i++) {
         int k = r.range(3, 9);
         int lat = r.range(-6, 6);
         int px = sx + f.dx * (k + 2) + side.dx * lat;
         int pz = sz + f.dz * (k + 2) + side.dz * lat;
         if (!c.wet(px, pz) || c.at(px, c.surface(px, pz), pz) != null) {
            continue;
         }
         c.set(px, c.surface(px, pz), pz, Kit.FH + "mallard_decoy[facing=" + r.pick(Dir.values()).id() + "]", Plan.IF_FREE);
         decoys--;
      }
      // reeds along the shoreline either side of the blind
      for (int lat = -7; lat <= 7; lat++) {
         if (Math.abs(lat) <= 2 || !r.chance(0.55)) {
            continue;
         }
         for (int k = 0; k <= 3; k++) {
            int px = sx + side.dx * lat + f.dx * k;
            int pz = sz + side.dz * lat + f.dz * k;
            if (c.wet(px, pz) && c.surface(px, pz) - c.floor(px, pz) <= 2) {
               c.set(px, c.floor(px, pz), pz, Kit.FH + "reeds[size=" + r.range(0, 2) + ",waterlogged=true]", Plan.REPLACE);
               break;
            }
         }
      }
      // the duck boat pulled up on the bank, and the dog stand
      int bx = sx - f.dx * 2 + side.dx * 3;
      int bz = sz - f.dz * 2 + side.dz * 3;
      if (!c.wet(bx, bz)) {
         Kit.ground(c, bx, bz, c.floor(bx, bz), 2);
         c.entity(bx + 0.5, c.floor(bx, bz), bz + 0.5, side.yaw(), "{id:\"minecraft:boat\",Type:\"" + (v == 1 ? "birch" : "spruce") + "\"}");
      }
      int dx = sx + side.dx * 2 + f.dx;
      int dz = sz + side.dz * 2 + f.dz;
      c.set(dx, floorY - 1, dz, "minecraft:spruce_slab[type=top,waterlogged=false]");
      int gx = sx - f.dx * 5 - side.dx * 2;
      int gz = sz - f.dz * 5 - side.dz * 2;
      Kit.ground(c, gx, gz, floorY - 1, 3);
      Kit.sign(c, gx, floorY - 1, gz, f.opposite(), "spruce", "duck_sign", 3);
      c.plan.cy = floorY;
      return true;
   }
}
