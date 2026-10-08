package com.formaworks.frontierhunts.livingworld.plan.kinds;

import com.formaworks.frontierhunts.livingworld.plan.Ctx;
import com.formaworks.frontierhunts.livingworld.plan.Dir;
import com.formaworks.frontierhunts.livingworld.plan.Kind;
import com.formaworks.frontierhunts.livingworld.plan.Kit;
import com.formaworks.frontierhunts.livingworld.plan.Rnd;

/**
 * [livingworld] Glassing point on a knob with a view: a tower blind looking over the basin below (tower variant) or a
 * log bench by a cairn with a spotting tripod left behind (rock_seat), and a sign naming what you can see from there.
 */
public final class GlassingPoint extends Kind {
   public GlassingPoint() {
      super("glassing_point", 12, "tower", "rock_seat");
   }

   @Override
   public boolean quickCheck(Ctx c) {
      if (c.wet(0, 0)) {
         return false;
      }
      int h0 = c.floor(0, 0);
      for (Dir d : Dir.values()) {
         if (h0 - c.floor(d.dx * 20, d.dz * 20) >= 5) {
            return true;
         }
      }
      return false;
   }

   @Override
   public boolean build(Ctx c) {
      int v = c.variant;
      Rnd r = c.rnd;
      if (c.wet(0, 0)) {
         return false;
      }
      int h0 = c.floor(0, 0);
      // the view: the direction where the ground drops away the most within 24 blocks
      Dir view = null;
      int drop = 0;
      for (Dir d : Dir.values()) {
         int dd = h0 - Math.min(c.floor(d.dx * 16, d.dz * 16), c.floor(d.dx * 24, d.dz * 24));
         if (dd > drop) {
            drop = dd;
            view = d;
         }
      }
      if (view == null || drop < 5) {
         return false;
      }
      int[] sv = c.survey(-4, -4, 4, 4, 2);
      if (sv[3] > 0 || sv[1] - sv[0] > 4) {
         return false;
      }
      int y = sv[1];
      c.plan.title = this.id + " / " + this.variants[v];
      Kit.pad(c, 0, 0, 5.5, 5.5, y, 3, 6);
      Dir side = view.cw();
      if (v == 0) {
         Kit.towerBlind(c, 0, y, 0, view);
         Kit.ground(c, side.dx * 4, side.dz * 4, y, 3);
         Kit.sign(c, side.dx * 4, y, side.dz * 4, view.opposite(), "spruce", "glass_sign", 2);
      } else {
         // bench of split logs looking out, a cairn, the tripod
         for (int a = -1; a <= 1; a++) {
            Kit.ground(c, side.dx * a, side.dz * a, y, 3);
            c.set(side.dx * a, y, side.dz * a, "minecraft:stripped_spruce_log[axis=" + (side.dx != 0 ? "x" : "z") + "]");
         }
         int cx = side.dx * 3 + view.dx;
         int cz = side.dz * 3 + view.dz;
         Kit.ground(c, cx, cz, y, 4);
         c.set(cx, y, cz, "minecraft:mossy_cobblestone");
         c.set(cx, y + 1, cz, "minecraft:cobblestone_wall");
         c.set(cx, y + 2, cz, Kit.FH + "river_stone[form=3,waterlogged=false]");
         int tx = view.dx * 2;
         int tz = view.dz * 2;
         Kit.ground(c, tx, tz, y, 3);
         c.entity(tx + 0.5, y, tz + 0.5, view.yaw(),
            "{id:\"minecraft:armor_stand\",Invisible:1b,NoBasePlate:1b,ShowArms:1b,HandItems:[{id:\"frontierhunts:binoculars\",count:1},{}],Pose:{RightArm:[-85f,0f,0f]}}");
         c.set(tx + side.dx, y, tz + side.dz, "minecraft:spruce_fence");
         Kit.ground(c, tx + side.dx, tz + side.dz, y, 3);
         int sx = -side.dx * 3 - view.dx;
         int sz = -side.dz * 3 - view.dz;
         Kit.ground(c, sx, sz, y, 3);
         Kit.sign(c, sx, y, sz, view.opposite(), "spruce", "glass_view", 4);
      }
      Kit.ground(c, -side.dx * 3, -side.dz * 3, y, 3);
      Kit.sign(c, -side.dx * 3, y, -side.dz * 3, view.opposite(), "spruce", v == 0 ? "glass_view" : "glass_sign", v == 0 ? 4 : 2);
      Kit.overgrowth(c, 0, 0, 6.5, 6.5, 0.15, r, null);
      c.plan.cy = y;
      return true;
   }
}
