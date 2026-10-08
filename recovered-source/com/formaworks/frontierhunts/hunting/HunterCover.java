package com.formaworks.frontierhunts.hunting;

import com.formaworks.frontierhunts.expedition.HubGroundBlind;
import com.formaworks.frontierhunts.expedition.ObservationVantage;
import com.formaworks.frontierhunts.expedition.TowerBlind;
import com.formaworks.frontierhunts.expedition.TreeStandSeat;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class HunterCover {
   public static final int OPEN = 0;
   public static final int GROUND_BLIND = 1;
   public static final int ELEVATED = 2;
   private static final WeakHashMap<Player, long[]> CACHE = new WeakHashMap<>();

   public static int of(Player var0) {
      long var1 = var0.level().getGameTime();
      synchronized (CACHE) {
         long[] var4 = CACHE.get(var0);
         if (var4 != null && var4[0] == var1) {
            return (int)var4[1];
         } else {
            int var5 = compute(var0);
            CACHE.put(var0, new long[]{var1, (long)var5});
            return var5;
         }
      }
   }

   private static int compute(Player var0) {
      if (var0.getVehicle() instanceof TreeStandSeat) {
         return ObservationVantage.elevation(var0) >= 2.0 ? 2 : 1;
      } else {
         Level var1 = var0.level();
         BlockPos var2 = var0.blockPosition();

         for (BlockPos var4 : BlockPos.betweenClosed(var2.offset(-2, -1, -2), var2.offset(2, 2, 2))) {
            if (var1.hasChunkAt(var4)) {
               BlockState var5 = var1.getBlockState(var4);
               if (var5.getBlock() instanceof HubGroundBlind) {
                  BlockPos var6 = HubGroundBlind.base(var4, var5);
                  if (Math.abs(var0.getX() - ((double)var6.getX() + 0.5)) <= 1.9
                     && Math.abs(var0.getZ() - ((double)var6.getZ() + 0.5)) <= 1.9
                     && var0.getY() >= (double)var6.getY() - 0.2
                     && var0.getY() <= (double)var6.getY() + 2.2) {
                     return 1;
                  }
               }

               if (var5.getBlock() instanceof TowerBlind) {
                  break;
               }
            }
         }

         return ObservationVantage.elevation(var0) >= 2.5 ? 2 : 0;
      }
   }

   private HunterCover() {
   }
}
