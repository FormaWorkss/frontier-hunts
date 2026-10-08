package com.formaworks.frontierhunts.client.tree;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * [sign] Read-only query for decals laid on a realistic round trunk (buck rubs, licking branches): the drawn stem at a log,
 * from the grown tree already cached for rendering. Never grows a tree (safe every frame on the render thread).
 */
public final class SignTrunkProbe {
   private SignTrunkProbe() {
   }

   /**
    * The drawn stem at a log block: {centre x, centre y (the log's mid height), centre z, radius} in world coordinates,
    * or null when the log is drawn as a plain block (Vanilla or a Minecraft-world setup, building logs, a tree not grown yet).
    */
   public static float[] stemAt(BlockPos log, BlockState state) {
      try {
         if (!(com.formaworks.frontierhunts.client.BlindCutouts.unwrap(Minecraft.getInstance().getBlockRenderer().getBlockModel(state)) instanceof TrunkModel)) {
            return null;
         }
         float[] socket = TreeGrowth.cachedSocket(log.getX(), log.getY(), log.getZ());
         if (socket == null) {
            return null;
         }
         for (int i = 0; i < 4; i++) {
            if (!Float.isFinite(socket[i])) {
               return null;
            }
         }
         // the socket is the stem axis at this log; a far-off or degenerate one is not this trunk
         if (Math.abs(socket[0] - log.getX() - 0.5F) > 1.5F || Math.abs(socket[2] - log.getZ() - 0.5F) > 1.5F || socket[3] < 0.02F || socket[3] > 1.6F) {
            return null;
         }
         return socket;
      } catch (RuntimeException e) {
         return null;
      }
   }

   /**
    * Trunk flare factor the realistic trunks use near the ground (TreeGrowth.radius), h metres above the trunk base.
    * [trees2] The flare is 1.38x over 0.4 blocks now, plus buttress lobes of up to 10% near the ground (counted here as
    * an upper bound, so a decal never sinks into a lobe).
    */
   public static float flare(float h) {
      h = Math.max(0.0F, h);
      return 1.0F + TreeGrowth.FLARE * (float)Math.exp(-h / TreeGrowth.FLARE_LEN) + TreeGrowth.LOBE * (float)Math.exp(-h / TreeGrowth.LOBE_LEN);
   }
}
