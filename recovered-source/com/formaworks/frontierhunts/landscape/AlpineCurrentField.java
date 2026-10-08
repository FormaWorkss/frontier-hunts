package com.formaworks.frontierhunts.landscape;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class AlpineCurrentField {
   public static Vec3 velocity(Level var0, AlpineHydraulics.Reach var1, double var2, double var4, double var6) {
      double var8 = var1.dx();
      double var10 = var1.dz();
      double var12 = -Double.MAX_VALUE;
      double var14 = var8;
      double var16 = var10;
      double var18 = 0.0;

      for (int var20 = -3; var20 <= 3; var20++) {
         double var21 = (double)var20 * Math.PI / 9.0;
         double var23 = Math.cos(var21);
         double var25 = Math.sin(var21);
         double var27 = var8 * var23 - var10 * var25;
         double var29 = var10 * var23 + var8 * var25;
         double var31 = 0.0;

         for (int var33 = 1; var33 <= 5 && !blocked(var0, var2 + var27 * (double)var33 * 0.5, var4 + 0.25, var6 + var29 * (double)var33 * 0.5); var33++) {
            var31 += 0.5;
         }

         double var37 = var31 + var23 * 0.7;
         if (var37 > var12) {
            var12 = var37;
            var14 = var27;
            var16 = var29;
            var18 = var31;
         }
      }

      double var35 = 1.0;

      for (int var22 = 1; var22 <= 4; var22++) {
         if (blocked(var0, var2 - var8 * (double)var22 * 0.65, var4 + 0.25, var6 - var10 * (double)var22 * 0.65)) {
            var35 = 0.25 + 0.12 * (double)var22;
            break;
         }
      }

      double var36 = AlpineHydraulics.speed(var1.energy()) * var35 * Math.clamp(var18 / 1.5, 0.0, 1.0);
      return new Vec3(var14 * var36, 0.0, var16 * var36);
   }

   private static boolean blocked(Level var0, double var1, double var3, double var5) {
      BlockPos var7 = BlockPos.containing(var1, var3, var5);
      return !var0.hasChunkAt(var7) || !var0.getBlockState(var7).getCollisionShape(var0, var7).isEmpty();
   }
}
