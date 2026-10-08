package com.formaworks.frontierhunts.livingworld.plan.kinds;

import com.formaworks.frontierhunts.livingworld.plan.Ctx;
import com.formaworks.frontierhunts.livingworld.plan.Dir;
import com.formaworks.frontierhunts.livingworld.plan.Kind;
import com.formaworks.frontierhunts.livingworld.plan.Kit;
import com.formaworks.frontierhunts.livingworld.plan.Rnd;

/**
 * [livingworld] Field-edge setup: a food plot worked into a clearing with hang-on tree stands in the edge trees facing
 * it, a trail camera on another tree watching the plot, a stand sign. Variants: field_edge (two stands), creek_bottom (one
 * double-seat stand and a camera, smaller plot).
 */
public final class StandLine extends Kind {
   public StandLine() {
      super("stand_line", 16, "field_edge", "creek_bottom");
   }

   static String treeFor(Ctx c, int x, int z) {
      String b = c.biome(x, z);
      if (b.contains("aspen") || b.contains("birch")) {
         return "aspen";
      }
      if (b.contains("maple")) {
         return "maple";
      }
      return c.rnd.chance(0.5) ? "pine" : "fir";
   }

   @Override
   public boolean build(Ctx c) {
      int v = c.variant;
      Rnd r = c.rnd;
      int half = v == 0 ? 9 : 6;
      int[] sv = c.survey(-half, -9, half, 4, 3);
      if (sv[3] > 0 || sv[1] - sv[0] > 5) {
         return false;
      }
      int y = sv[2];
      c.plan.title = this.id + " / " + this.variants[v];
      Kit.pad(c, 0, -1, half + 1.5, 5.0, y, 3, 8);
      Kit.foodPlot(c, -half, -2, half, 3, y, r);
      // the tree line on the north edge: stands look south over the plot
      int stands = v == 0 ? 2 : 1;
      int[] xs = v == 0 ? new int[]{-half + 3, half - 3} : new int[]{-2};
      for (int i = 0; i < stands; i++) {
         int bx = xs[i] + r.range(-1, 1);
         Kit.treeStand(c, bx, y, -5, Dir.SOUTH, r.range(4, 6), v == 1 ? 2 : 1, treeFor(c, bx, -7));
      }
      // camera tree
      int cx = v == 0 ? 0 : 3;
      Kit.ground(c, cx, -8, y, 2);
      c.tree(cx, y, -8, treeFor(c, cx, -8), 9 + r.nextInt(4));
      Kit.trailCamera(c, cx, y + 1, -7, Dir.SOUTH);
      // a couple more edge trees so it reads as a tree line
      for (int x : new int[]{-half - 2, half + 2}) {
         Kit.ground(c, x, -6, y, 2);
         c.tree(x, y, -6, treeFor(c, x, -6), 8 + r.nextInt(5));
      }
      Kit.ground(c, half + 1, 4, y, 3);
      Kit.sign(c, half + 1, y, 4, Dir.SOUTH, "spruce", "plot_sign", 4);
      int sx = xs[0] + 2;
      Kit.ground(c, sx, -4, y, 3);
      Kit.sign(c, sx, y, -4, Dir.SOUTH, "spruce", "stand_sign", 4);
      c.plan.cy = y;
      return true;
   }
}
