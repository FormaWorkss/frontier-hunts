package com.formaworks.frontierhunts.phone;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * [phone] The Field Phone's signal, 0..4 bars. The same rule runs on the client (status bar) and the server (online
 * features need a bar), from what both sides know: how deep under the ground the phone is, how far from the reserve's
 * settled heart (the world spawn, where the lodge and the ranger's mast are) and how high up. Ridges and open sky
 * help, valleys and caves hurt, a thunderstorm costs a bar. Out in the far backcountry a phone keeps one bar on the
 * surface: enough for texts and games, never the full four.
 */
public final class PhoneSignal {
   private PhoneSignal() {
   }

   public static int bars(Level level, double x, double y, double z) {
      if (level == null || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
         return 0;
      }
      if (!level.dimension().equals(Level.OVERWORLD)) {
         // the Nether and the End have no masts
         return 0;
      }
      BlockPos pos = BlockPos.containing(x, y + 1.0, z);
      int surface;
      if (level.hasChunkAt(pos)) {
         surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
      } else {
         surface = (int)y;
      }
      double depth = surface - (y + 1.0);
      BlockPos spawn = level.getSharedSpawnPos();
      double dx = x - spawn.getX(), dz = z - spawn.getZ();
      double far = Math.sqrt(dx * dx + dz * dz);
      int bars = far < 1400.0 ? 4 : (far < 2800.0 ? 3 : (far < 5200.0 ? 2 : 1));
      if (y > 112.0) {
         bars++;
      } else if (y < 56.0 && depth < 4.0) {
         bars--;
      }
      if (level.isThundering()) {
         bars--;
      }
      // under the ground: every few blocks of rock takes a bar
      if (depth > 3.0) {
         bars -= 1 + (int)((depth - 3.0) / 5.0);
      }
      bars = Math.max(0, Math.min(4, bars));
      if (bars == 0 && depth <= 3.0) {
         bars = 1;
      }
      return bars;
   }
}
