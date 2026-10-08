package com.formaworks.frontierhunts.client.tree;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Read-only query for gear strapped to a realistic trunk (tree stands): where the drawn stem is and
 * how thick it is at a log. Uses only the grown tree's attachment sockets, never changes a tree.
 */
public final class StandTrunkProbe {
   private StandTrunkProbe() {
   }

   /**
    * The drawn stem at a log block: {centre x, centre y, centre z, radius} in world coordinates
    * (the stem axis at the log's mid height), or null when that log is not drawn as part of a grown
    * round trunk (realistic trees off, a building log, a snag, or no stem through this log).
    *
    * @param build the block getter of the chunk-section build asking (keeps TreeGrowth's per-build checks)
    */
   public static float[] stemAt(Object build, BlockPos log, BlockState state) {
      try {
         if (!(com.formaworks.frontierhunts.client.BlindCutouts.unwrap(Minecraft.getInstance().getBlockRenderer().getBlockModel(state)) instanceof TrunkModel)) {
            return null;
         }
         TreeGrowth.Tree tree = TreeGrowth.lookup(LiveWorld.INSTANCE, log.getX(), log.getY(), log.getZ(), build);
         if (tree == null) {
            return null;
         }
         float[] socket = tree.sockets.get(TreeGrowth.pack(log.getX(), log.getY(), log.getZ()));
         if (socket == null || socket.length < 4) {
            return null;
         }
         for (int i = 0; i < 4; i++) {
            if (!Float.isFinite(socket[i])) {
               return null;
            }
         }
         return socket.clone();
      } catch (RuntimeException e) {
         return null;
      }
   }
}
