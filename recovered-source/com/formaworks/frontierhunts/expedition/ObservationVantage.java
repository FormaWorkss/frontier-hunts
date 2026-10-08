package com.formaworks.frontierhunts.expedition;

import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class ObservationVantage {
   public static double elevation(Player var0) {
      Level var1 = var0.level();
      BlockPos var2 = var0.blockPosition();
      boolean var3 = false;

      for (BlockPos var5 : BlockPos.betweenClosed(var2.offset(-3, -2, -3), var2.offset(3, 2, 3))) {
         if (var1.hasChunkAt(var5)) {
            BlockState var6 = var1.getBlockState(var5);
            if (var6.getBlock() instanceof TowerBlind) {
               BlockPos var7 = TowerBlind.base(var5, var6);
               double var8 = var0.getY() - (double)var7.getY();
               if (var8 >= 3.5
                  && var8 <= 7.5
                  && Math.abs(var0.getX() - ((double)var7.getX() + 0.5)) <= 2.1
                  && Math.abs(var0.getZ() - ((double)var7.getZ() + 0.5)) <= 2.1) {
                  return Math.min(20.0, var8);
               }
            }

            if (var6.getBlock() instanceof MountedTreeStand) {
               BlockPos var19 = MountedTreeStand.base(var5, var6);
               double var21 = var0.getY() - (double)var19.getY();
               if (var21 >= 2.5 && var21 <= (double)((Integer)var6.getValue(MountedTreeStand.HEIGHT) + 2) && var0.distanceToSqr(var19.getCenter()) < 20.0) {
                  return Math.min(20.0, var21);
               }
            }
         }
      }

      for (int var12 = 0; var12 <= 2; var12++) {
         BlockPos var14 = var2.below(var12);
         BlockState var16 = var1.getBlockState(var14);
         if (var16.is(BlockTags.PLANKS) || var16.is(BlockTags.WOODEN_SLABS)) {
            var3 = true;
         }
      }

      if (!var3) {
         return 0.0;
      } else {
         double[] var13 = new double[4];
         int var15 = 0;

         for (Direction var20 : Plane.HORIZONTAL) {
            BlockPos var22 = var2.relative(var20, 7);
            if (var1.hasChunkAt(var22)) {
               for (int var9 = 1; var9 <= 24; var9++) {
                  BlockPos var10 = var22.below(var9);
                  if (!var1.isInWorldBounds(var10)) {
                     break;
                  }

                  BlockState var11 = var1.getBlockState(var10);
                  if (!var11.is(BlockTags.LEAVES)
                     && !var11.is(BlockTags.LOGS)
                     && (!var11.getCollisionShape(var1, var10).isEmpty() || !var11.getFluidState().isEmpty())) {
                     var13[var15++] = var0.getY() - (double)(var10.getY() + 1);
                     break;
                  }
               }
            }
         }

         if (var15 < 2) {
            return 0.0;
         } else {
            Arrays.sort(var13, 0, var15);
            double var18 = var13[(var15 - 1) / 2];
            return var18 >= 3.0 ? Math.min(20.0, var18) : 0.0;
         }
      }
   }

   public static double range(double var0) {
      return 192.0 + Math.min(64.0, Math.max(0.0, var0) * 4.0);
   }

   private ObservationVantage() {
   }
}
