package com.formaworks.frontierhunts.sign.work;

import com.formaworks.frontierhunts.hunting.DeerSign;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * [deersign] Shared placement of a sign's parts (common code): the renderer draws them here, the working buck's head
 * goes here, the particles come from here.
 */
public final class SignShapes {
   private SignShapes() {
   }

   /** The licking branch's tip joint over a scrape, world {x, y, z} (its chewed end hangs ~0.09 below). */
   public static float[] lickTip(DeerSign.Mark mark, BlockPos cell, Direction toTree) {
      if (mark.lickY > 0.0F) {
         return new float[]{cell.getX() + 0.5F + toTree.getStepX() * mark.lickF, cell.getY() + mark.lickY, cell.getZ() + 0.5F + toTree.getStepZ() * mark.lickF};
      }
      int seed = mark.seed;
      float s = mark.size();
      boolean big = "elk".equals(mark.species) || "moose".equals(mark.species);
      float cx = cell.getX() + 0.5F + toTree.getStepX() * 0.08F;
      float cz = cell.getZ() + 0.5F + toTree.getStepZ() * 0.08F;
      float tipY = cell.getY() + (big ? 1.95F : 1.32F + 0.26F * s) + (SignMotion.rnd(seed, 30) - 0.5F) * 0.08F;
      float tipX = cx + toTree.getStepX() * 0.10F + (SignMotion.rnd(seed, 31) - 0.5F) * 0.12F;
      float tipZ = cz + toTree.getStepZ() * 0.10F + (SignMotion.rnd(seed, 32) - 0.5F) * 0.12F;
      return new float[]{tipX, tipY, tipZ};
   }

   /** Is the licking branch tip chewed yet (the buck has worked it). */
   public static boolean chewed(DeerSign.Mark mark, long now) {
      return mark.lickAt == 0L || now >= mark.lickAt;
   }
}
