package com.formaworks.frontierhunts.environment;

import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;

public final class Wildness {
   private static final double REGION = 208.0;
   private static final double PATCH = 58.0;
   private static final double GROVE = 340.0;

   private static float hash(long var0, int var2, int var3) {
      long var4 = var0 ^ (long)var2 * -7046029254386353131L ^ (long)var3 * -4417276706812531889L;
      var4 ^= var4 >>> 29;
      var4 *= -4658895280553007687L;
      var4 ^= var4 >>> 32;
      var4 *= -7723592293110705685L;
      var4 ^= var4 >>> 31;
      return (float)(var4 >>> 40) / 1.6777216E7F;
   }

   private static float value(long var0, double var2, double var4) {
      int var6 = Mth.floor(var2);
      int var7 = Mth.floor(var4);
      float var8 = (float)(var2 - (double)var6);
      float var9 = (float)(var4 - (double)var7);
      float var10 = var8 * var8 * (3.0F - 2.0F * var8);
      float var11 = var9 * var9 * (3.0F - 2.0F * var9);
      float var12 = Mth.lerp(var10, hash(var0, var6, var7), hash(var0, var6 + 1, var7));
      float var13 = Mth.lerp(var10, hash(var0, var6, var7 + 1), hash(var0, var6 + 1, var7 + 1));
      return Mth.lerp(var11, var12, var13);
   }

   public static float thickness(WorldGenLevel var0, int var1, int var2) {
      long var3 = var0.getSeed();
      float var5 = value(var3, (double)var1 / 208.0, (double)var2 / 208.0);
      float var6 = value(var3 ^ 1592609367L, (double)var1 / 58.0, (double)var2 / 58.0);
      float var7 = var5 * 0.66F + var6 * 0.34F;
      return Mth.clamp((float)(0.5 + Math.tanh(((double)var7 - 0.5) * 3.6) * 0.64), 0.0F, 1.0F);
   }

   public static int spacing(float var0) {
      return var0 > 0.82F ? 3 : (var0 > 0.5F ? 4 : (var0 > 0.25F ? 5 : 7));
   }

   public static int grove(WorldGenLevel var0, int var1, int var2, int var3) {
      float var4 = value(var0.getSeed() ^ 1779392077L, (double)var1 / 340.0, (double)var2 / 340.0);
      return Mth.clamp((int)(var4 * (float)var3), 0, var3 - 1);
   }

   public static float purity(WorldGenLevel var0, int var1, int var2) {
      return value(var0.getSeed() ^ 2585259282L, (double)var1 / 153.0, (double)var2 / 153.0);
   }

   public static float lush(WorldGenLevel var0, int var1, int var2) {
      long var3 = var0.getSeed();
      float var5 = value(var3 ^ 508516915L, (double)var1 / 172.0, (double)var2 / 172.0);
      float var6 = value(var3 ^ 2083230165L, (double)var1 / 41.0, (double)var2 / 41.0);
      float var7 = var5 * 0.74F + var6 * 0.26F;
      return Mth.clamp((float)(0.5 + Math.tanh(((double)var7 - 0.46) * 4.6) * 0.62), 0.0F, 1.0F);
   }

   private Wildness() {
   }
}
