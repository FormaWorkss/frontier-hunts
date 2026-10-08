package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.expedition.FieldFlashlight;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

final class FieldEntityLight {
   private static long tick = Long.MIN_VALUE;
   private static Object level;
   private static final Map<Integer, Integer> CACHE = new HashMap<>();
   private static final Map<Long, Integer> POINTS = new HashMap<>();
   private static long pointTick = Long.MIN_VALUE;

   static void clear() {
      CACHE.clear();
      level = null;
      tick = Long.MIN_VALUE;
   }

   static int apply(Entity var0, int var1) {
      Minecraft var2 = Minecraft.getInstance();
      if (var2.level != null && !HuntShaderCompat.shadowPass()) {
         long var3 = var2.level.getGameTime();
         if (level != var2.level || tick != var3) {
            CACHE.clear();
            level = var2.level;
            tick = var3;
         }

         int var5 = CACHE.computeIfAbsent(
            var0.getId(),
            var2x -> {
               int var3x = 0;
               Vec3 var4 = var0.getBoundingBox().getCenter();

               for (AbstractClientPlayer var6 : var2.level.players()) {
                  if (var6.isAlive()
                     && !var6.isSpectator()
                     && (FieldFlashlight.enabled(var6.getMainHandItem()) || FieldFlashlight.enabled(var6.getOffhandItem()))) {
                     Vec3 var7 = var6.getEyePosition();
                     Vec3 var8 = var4.subtract(var7);
                     double var9 = var8.length();
                     if (!(var9 > 42.0) && !(var9 < 0.1)) {
                        double var11 = var8.dot(var6.getLookAngle()) / var9;
                        if (!(var11 < 0.72) && var2.level.clip(new ClipContext(var7, var4, Block.COLLIDER, Fluid.NONE, var6)).getType() == Type.MISS) {
                           var3x = Math.max(var3x, (int)Math.clamp((1.0 - var9 / 54.0) * 16.0 * Math.clamp((var11 - 0.72) / 0.18, 0.0, 1.0), 0.0, 15.0));
                        }
                     }
                  }
               }

               return var3x;
            }
         );
         return LightTexture.pack(Math.max(LightTexture.block(var1), var5), LightTexture.sky(var1));
      } else {
         return var1;
      }
   }

   static int point(Vec3 var0, int var1, boolean var2) {
      Minecraft var3 = Minecraft.getInstance();
      if (var3.level != null && !HuntShaderCompat.shadowPass()) {
         long var4 = var3.level.getGameTime();
         if (pointTick != var4 || level != var3.level) {
            POINTS.clear();
            pointTick = var4;
         }

         long var6 = Double.doubleToLongBits(var0.x * 31.0 + var0.y) * 31L + Double.doubleToLongBits(var0.z) + (long)(var2 ? 1 : 0);
         int var8 = POINTS.computeIfAbsent(
            var6,
            var3x -> {
               int var4x = 0;

               for (AbstractClientPlayer var6x : var3.level.players()) {
                  if (var6x.isAlive()
                     && !var6x.isSpectator()
                     && (FieldFlashlight.enabled(var6x.getMainHandItem()) || FieldFlashlight.enabled(var6x.getOffhandItem()))) {
                     Vec3 var7 = var6x.getEyePosition();
                     Vec3 var8x = var0.subtract(var7);
                     double var9 = var8x.length();
                     if (!(var9 > 40.0) && !(var9 < 0.05)) {
                        double var11 = var8x.dot(var6x.getLookAngle()) / var9;
                        if (!(var11 < 0.74)
                           && (!var2 || var3.level.clip(new ClipContext(var7, var0, Block.COLLIDER, Fluid.NONE, var6x)).getType() == Type.MISS)) {
                           double var13 = Math.acos(Math.min(1.0, var11));
                           double var15 = 0.62 * Math.exp(-Math.pow(var13 / 0.37, 2.0)) + 0.38 * Math.exp(-Math.pow(var13 / 0.17, 2.0));
                           double var17 = 1.0 - Math.clamp((var9 - 23.0) / 20.0, 0.0, 1.0);
                           var4x = Math.max(var4x, (int)Math.round(Math.clamp(var15 * var17 * 1.35, 0.0, 1.0) * 15.0));
                        }
                     }
                  }
               }

               return var4x;
            }
         );
         return LightTexture.pack(Math.max(LightTexture.block(var1), var8), LightTexture.sky(var1));
      } else {
         return var1;
      }
   }

   private FieldEntityLight() {
   }
}
