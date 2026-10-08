package com.formaworks.frontierhunts.landscape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class AlpineImpactCurrent {
   private static final Map<ServerLevel, Map<BlockPos, AlpineImpactCurrent.Cached>> CACHE = new WeakHashMap<>();

   public static Vec3 impulse(ServerLevel var0, double var1, double var3, double var5) {
      BlockPos var7 = new BlockPos((int)Math.floor(var1 / 4.0), (int)Math.floor(var3 / 4.0), (int)Math.floor(var5 / 4.0));
      Map var8 = CACHE.computeIfAbsent(var0, var0x -> new HashMap<>());
      AlpineImpactCurrent.Cached var9 = (AlpineImpactCurrent.Cached)var8.get(var7);
      if (var9 == null || var0.getGameTime() - var9.tick > 20L) {
         if (var8.size() > 512) {
            var8.clear();
         }

         ArrayList var10 = new ArrayList();

         for (int var11 = -3; var11 <= 6; var11++) {
            for (int var12 = -3; var12 <= 6; var12++) {
               for (int var13 = 0; var13 <= 9; var13++) {
                  BlockPos var14 = new BlockPos(var7.getX() * 4 + var12, var7.getY() * 4 + var13, var7.getZ() * 4 + var11);
                  if (var0.hasChunkAt(var14)) {
                     BlockState var15 = var0.getBlockState(var14);
                     if (var15.is((Block)AlpineRegistration.SPRAY.get()) && (Integer)var15.getValue(AlpineSpray.KIND) == 2) {
                        var10.add(new AlpineImpactCurrent.Source(var14, (Integer)var15.getValue(AlpineSpray.POWER)));
                     }
                  }
               }
            }
         }

         var9 = new AlpineImpactCurrent.Cached(var0.getGameTime(), List.copyOf(var10));
         var8.put(var7, var9);
      }

      AlpineImpactCurrent.Source var27 = null;
      double var28 = 16.0;

      for (AlpineImpactCurrent.Source var31 : var9.sources) {
         double var32 = (double)var31.at.getY() - 0.1 - var3;
         if (!(var32 < 0.0) && !(var32 > 6.0)) {
            double var17 = var1 - (double)var31.at.getX() - 0.5;
            double var19 = var5 - (double)var31.at.getZ() - 0.5;
            double var21 = var17 * var17 + var19 * var19;
            if (var21 < var28) {
               var28 = var21;
               var27 = var31;
            }
         }
      }

      if (var27 == null) {
         return Vec3.ZERO;
      } else {
         double var30 = var1 - (double)var27.at.getX() - 0.5;
         double var33 = var5 - (double)var27.at.getZ() - 0.5;
         double var34 = Math.sqrt(var28);
         double var35 = (double)var27.at.getY() - 0.1 - var3;
         double var36 = (0.35 + 0.325 * (double)var27.power) * Math.exp(-var28 / 6.0) * Math.clamp(1.0 - var35 / 6.0, 0.0, 1.0);
         double var23 = 0.085 * var36 / Math.max(0.6, var34);
         double var25 = -0.12 * var36 * Math.exp(-var28 / 2.0) + 0.025 * var36 * Math.exp(-(var34 - 2.5) * (var34 - 2.5));
         return new Vec3(var30 * var23, var25, var33 * var23);
      }
   }

   private static record Cached(long tick, List<AlpineImpactCurrent.Source> sources) {
   }

   private static record Source(BlockPos at, int power) {
   }
}
