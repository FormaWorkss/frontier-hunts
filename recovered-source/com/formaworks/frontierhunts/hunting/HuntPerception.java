package com.formaworks.frontierhunts.hunting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class HuntPerception {
   private static final Map<UUID, HuntPerception.Sample> MOVEMENT = new HashMap<>();

   public static void observe(ServerPlayer var0) {
      long var1 = var0.level().getGameTime();
      String var3 = var0.level().dimension().location().toString();
      HuntPerception.Sample var4 = MOVEMENT.get(var0.getUUID());
      double var5 = 0.0;
      if (var4 != null && var4.dimension.equals(var3) && var1 > var4.tick && var1 - var4.tick < 20L) {
         double var7 = var0.position().subtract(var4.position).horizontalDistance();
         if (var7 < 2.0) {
            var5 = var7 * 20.0 / (double)(var1 - var4.tick);
         }
      }

      MOVEMENT.put(var0.getUUID(), new HuntPerception.Sample(var0.position(), var3, var1, var5));
   }

   public static double speed(UUID var0) {
      HuntPerception.Sample var1 = MOVEMENT.get(var0);
      return var1 == null ? 0.0 : var1.speed;
   }

   public static void forget(UUID var0) {
      MOVEMENT.remove(var0);
   }

   public static void clear() {
      MOVEMENT.clear();
   }

   private HuntPerception() {
   }

   private static record Sample(Vec3 position, String dimension, long tick, double speed) {
   }
}
