package com.formaworks.frontierhunts.livingworld.plan.kinds;

import com.formaworks.frontierhunts.livingworld.plan.Ctx;
import com.formaworks.frontierhunts.livingworld.plan.Dir;
import com.formaworks.frontierhunts.livingworld.plan.Kind;
import com.formaworks.frontierhunts.livingworld.plan.Kit;
import com.formaworks.frontierhunts.livingworld.plan.Rnd;

/**
 * [livingworld] A hub ground blind brushed in at the edge of a small food plot with a feeder, a trail camera on a post
 * and a chair and shell box inside. Variants: plot_blind, feeder_blind (no plot, a feeder and mineral lick).
 */
public final class GroundBlindPlot extends Kind {
   public GroundBlindPlot() {
      super("ground_blind_plot", 14, "plot_blind", "feeder_blind");
   }

   @Override
   public boolean build(Ctx c) {
      int v = c.variant;
      Rnd r = c.rnd;
      int[] sv = c.survey(-7, -9, 7, 6, 3);
      if (sv[3] > 0 || sv[1] - sv[0] > 5) {
         return false;
      }
      int y = sv[2];
      c.plan.title = this.id + " / " + this.variants[v];
      Kit.pad(c, 0, -1, 7.5, 7.5, y, 3, 8);
      if (v == 0) {
         Kit.foodPlot(c, -5, 0, 5, 4, y, r);
      } else {
         for (int x = -2; x <= 2; x++) {
            for (int z = 1; z <= 3; z++) {
               Kit.ground(c, x, z, y, 4);
               Kit.surface(c, x, z, r.chance(0.5) ? "minecraft:coarse_dirt" : "minecraft:mud");
            }
         }
         // mineral lick: a salt block worked into the mud
         c.set(1, y - 1, 2, "minecraft:calcite");
      }
      Kit.feeder(c, v == 0 ? 6 : 0, y, v == 0 ? 2 : 0);
      // the blind looks at the plot from the north
      Kit.groundBlind(c, 0, y, -7, Dir.SOUTH);
      c.set(0, y, -8, Kit.FH + "lodge_chair[facing=south]");
      Kit.barrel(c, -1, y, -8, "up", "blind");
      Kit.cameraPost(c, -6, y, 6, Dir.NORTH);
      Kit.ground(c, 4, -6, y, 3);
      Kit.sign(c, 4, y, -6, Dir.SOUTH, "spruce", "blind_sign", 3);
      Kit.overgrowth(c, 0, -1, 9, 9, 0.1, r, null);
      c.plan.cy = y;
      return true;
   }
}
