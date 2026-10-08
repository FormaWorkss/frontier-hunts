package com.formaworks.frontierhunts.expedition;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.material.MapColor;

public final class Skyline {
   public static final int COLUMNS = 64;
   public static final int STRIDE = 4;
   public static final int BYTES = 256;
   public static final float FOV_H = 110.0F;
   public static final float FOV_V = 74.0F;
   private static final int RANGE = 52;

   private Skyline() {
   }

   public static boolean valid(byte[] var0) {
      return var0 != null && var0.length == 256;
   }

   public static byte[] sample(Level var0, BlockPos var1, Direction var2) {
      if (!var0.hasChunkAt(var1)) {
         return new byte[0];
      } else {
         byte[] var3 = new byte[256];
         double var4 = (double)var1.getX() + 0.5;
         double var6 = (double)var1.getZ() + 0.5;
         double var8 = (double)var1.getY() + 0.6;
         float var10 = var2.toYRot();
         float var11 = (float)Math.toRadians(37.0);
         MutableBlockPos var12 = new MutableBlockPos();

         for (int var13 = 0; var13 < 64; var13++) {
            double var14 = Math.toRadians((double)var10 + (((double)var13 + 0.5) / 64.0 - 0.5) * 110.0);
            double var16 = -Math.sin(var14);
            double var18 = Math.cos(var14);
            float var20 = 1.0F;
            float var21 = 1.0F;
            int var22 = 0;
            int var23 = 0;

            for (int var24 = 2; var24 <= 52; var24++) {
               int var25 = Mth.floor(var4 + var16 * (double)var24);
               int var26 = Mth.floor(var6 + var18 * (double)var24);
               var12.set(var25, var1.getY(), var26);
               if (!var0.hasChunkAt(var12)) {
                  break;
               }

               int var27 = var0.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, var25, var26);
               int var28 = var0.getHeight(Types.MOTION_BLOCKING, var25, var26);
               float var29 = row((double)var27 - var8, (double)var24, var11);
               if (var29 < var20) {
                  var20 = var29;
                  var12.set(var25, var27 - 1, var26);
                  var22 = var0.getBlockState(var12).getMapColor(var0, var12).id;
               }

               float var30 = row((double)var28 - var8, (double)var24, var11);
               if (var28 > var27 && var30 < var21) {
                  var21 = var30;
                  var12.set(var25, var28 - 1, var26);
                  var23 = var0.getBlockState(var12).getMapColor(var0, var12).id;
               }
            }

            if (var21 > var20) {
               var21 = var20;
               var23 = var22;
            }

            int var31 = var13 * 4;
            var3[var31] = (byte)Mth.clamp(Math.round(var20 * 255.0F), 0, 255);
            var3[var31 + 1] = (byte)Mth.clamp(Math.round(var21 * 255.0F), 0, 255);
            var3[var31 + 2] = (byte)Mth.clamp(var22, 0, 63);
            var3[var31 + 3] = (byte)Mth.clamp(var23, 0, 63);
         }

         return var3;
      }
   }

   private static float row(double var0, double var2, float var4) {
      double var5 = Math.atan2(var0, var2);
      return (float)Mth.clamp(0.5 - var5 / (double)var4 * 0.5, 0.0, 1.0);
   }

   public static float groundRow(byte[] var0, int var1) {
      return (float)(var0[var1 * 4] & 255) / 255.0F;
   }

   public static float canopyRow(byte[] var0, int var1) {
      return (float)(var0[var1 * 4 + 1] & 255) / 255.0F;
   }

   public static int groundColour(byte[] var0, int var1) {
      return colour(var0[var1 * 4 + 2] & 0xFF);
   }

   public static int canopyColour(byte[] var0, int var1) {
      return colour(var0[var1 * 4 + 3] & 0xFF);
   }

   private static int colour(int var0) {
      MapColor var1 = MapColor.byId(var0);
      return var1 != null && var1.col != 0 ? var1.col : 4870720;
   }
}
