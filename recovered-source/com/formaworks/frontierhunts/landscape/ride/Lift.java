package com.formaworks.frontierhunts.landscape.ride;

import com.formaworks.frontierhunts.Wilderness;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;

public final class Lift {
   static final int CELL = 112;
   public static final double MAX_THERMAL = 0.13;
   public static final double MAX_TOTAL = 0.2;

   private Lift() {
   }

   static int ground(Level var0, int var1, int var2) {
      return !var0.hasChunk(var1 >> 4, var2 >> 4) ? Integer.MIN_VALUE : var0.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, var1, var2);
   }

   public static double at(Level var0, double var1, double var3, double var5, double var7, double var9) {
      double var11 = thermals(var0, var1, var3, var5, var7, var9) + ridge(var0, var1, var3, var5, var7, var9);
      return Math.max(-0.08, Math.min(0.2, var11));
   }

   static double thermals(Level var0, double var1, double var3, double var5, double var7, double var9) {
      double var11 = Wilderness.thermal(var0.getDayTime(), var0.isRaining(), var0.getRainLevel(1.0F));
      if (var11 <= 0.05) {
         return 0.0;
      } else {
         int var13 = ground(var0, (int)Math.floor(var1), (int)Math.floor(var5));
         if (var13 == Integer.MIN_VALUE) {
            return 0.0;
         } else {
            double var14 = var3 - (double)var13;
            if (var14 < 3.0) {
               return 0.0;
            } else {
               double var16 = Math.hypot(var7, var9) * 20.0;
               long var18 = var0.getGameTime();
               double var20 = 0.0;
               int var22 = Math.floorDiv((int)var1, 112);
               int var23 = Math.floorDiv((int)var5, 112);

               for (int var24 = -1; var24 <= 1; var24++) {
                  for (int var25 = -1; var25 <= 1; var25++) {
                     int var26 = var22 + var24;
                     int var27 = var23 + var25;
                     long var28 = mix((long)var26 * -7046029254386353131L ^ (long)var27 * -4417276706812531889L ^ (long)var0.dimension().location().hashCode());
                     double var30 = (double)(var28 >>> 11 & 65535L) / 65535.0;
                     double var32 = (double)(var28 >>> 27 & 65535L) / 65535.0;
                     double var34 = (double)(var28 >>> 43 & 65535L) / 65535.0;
                     double var36 = ((double)var26 + 0.15 + 0.7 * var30) * 112.0;
                     double var38 = ((double)var27 + 0.15 + 0.7 * var32) * 112.0;
                     double var40 = 1200.0 * (2.5 + 2.0 * var34);
                     double var42 = ((double)var18 / var40 + var30 * 7.0) % 1.0;
                     double var44 = Math.sin(Math.PI * var42);
                     if (!(var44 <= 0.05)) {
                        int var46 = ground(var0, (int)var36, (int)var38);
                        if (var46 != Integer.MIN_VALUE) {
                           double var47 = heating(var0, (int)var36, var46, (int)var38);
                           if (!(var47 <= 0.0)) {
                              double var49 = Math.hypot(var7, var9);
                              double var51 = var36;
                              double var53 = var38;
                              if (var49 > 1.0E-4) {
                                 double var55 = Math.max(0.0, var3 - (double)var46) * 0.4 * Math.min(2.5, var16 / 2.0);
                                 var51 = var36 + var7 / var49 * var55;
                                 var53 = var38 + var9 / var49 * var55;
                              }

                              double var71 = 14.0 + var32 * 14.0;
                              double var57 = Math.hypot(var1 - var51, var5 - var53);
                              double var59 = Math.max(120.0, 170.0 + var34 * 110.0) * var11;
                              double var61 = Math.min(1.0, Math.max(0.0, (var59 - (var3 - (double)var46)) / 25.0));
                              double var63 = Math.min(1.0, Math.max(0.35, (var3 - (double)var46) / 12.0));
                              double var65 = (0.45 + 0.55 * var30) * var44 * var47 * var11;
                              double var67 = Math.exp(-(var57 / var71) * (var57 / var71));
                              double var69 = -0.18 * Math.exp(-Math.pow((var57 - 1.7 * var71) / (0.5 * var71), 2.0));
                              var20 += 0.13 * var65 * var61 * var63 * (var67 + var69);
                           }
                        }
                     }
                  }
               }

               return var20;
            }
         }
      }
   }

   static double heating(Level var0, int var1, int var2, int var3) {
      BlockState var4 = var0.getBlockState(new BlockPos(var1, var2 - 1, var3));
      BlockState var5 = var0.getBlockState(new BlockPos(var1, var2, var3));
      if (!var4.getFluidState().isEmpty() || !var5.getFluidState().isEmpty()) {
         return 0.0;
      } else if (var4.is(BlockTags.SNOW) || var5.is(Blocks.SNOW) || var4.is(BlockTags.ICE)) {
         return 0.35;
      } else if (var4.is(BlockTags.LEAVES)) {
         return 0.55;
      } else {
         return !var4.is(BlockTags.BASE_STONE_OVERWORLD) && !var4.is(BlockTags.SAND) && !var4.is(Blocks.GRAVEL) ? 1.0 : 1.2;
      }
   }

   static double ridge(Level var0, double var1, double var3, double var5, double var7, double var9) {
      double var11 = Math.hypot(var7, var9);
      if (var11 < 0.02) {
         return 0.0;
      } else {
         double var13 = var7 / var11;
         double var15 = var9 / var11;
         int var17 = ground(var0, (int)Math.floor(var1), (int)Math.floor(var5));
         if (var17 == Integer.MIN_VALUE) {
            return 0.0;
         } else {
            int var18 = ground(var0, (int)Math.floor(var1 - var13 * 10.0), (int)Math.floor(var5 - var15 * 10.0));
            int var19 = ground(var0, (int)Math.floor(var1 + var13 * 10.0), (int)Math.floor(var5 + var15 * 10.0));
            if (var18 != Integer.MIN_VALUE && var19 != Integer.MIN_VALUE) {
               double var20 = (double)(var19 - var18) / 20.0;
               int var22 = var17;

               for (byte var23 = 4; var23 <= 40; var23 += 4) {
                  int var24 = ground(var0, (int)Math.floor(var1 + var13 * (double)var23), (int)Math.floor(var5 + var15 * (double)var23));
                  if (var24 != Integer.MIN_VALUE) {
                     var22 = Math.max(var22, var24);
                  }
               }

               double var29 = var3 - (double)var17;
               double var25 = var3 - (double)var22;
               if (var20 > 0.0) {
                  double var27 = Math.exp(-Math.max(0.0, var25 - 10.0) / 45.0) * Math.min(1.0, Math.max(0.3, var29 / 6.0));
                  return var11 * Math.min(1.4, var20) * 0.85 * var27;
               } else {
                  return var29 < 90.0 ? var11 * Math.max(-1.2, var20) * 0.55 * (1.0 - var29 / 90.0) : 0.0;
               }
            } else {
               return 0.0;
            }
         }
      }
   }

   private static long mix(long var0) {
      var0 = (var0 ^ var0 >>> 30) * -4658895280553007687L;
      var0 = (var0 ^ var0 >>> 27) * -7723592293110705685L;
      return var0 ^ var0 >>> 31;
   }
}
