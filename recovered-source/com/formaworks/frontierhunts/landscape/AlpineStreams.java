package com.formaworks.frontierhunts.landscape;

import com.mojang.logging.LogUtils;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MethodHandles.Lookup;
import net.minecraft.world.level.WorldGenLevel;
import net.neoforged.fml.ModList;

final class AlpineStreams {
   private static final MethodHandle ENGINE;
   private static final MethodHandle LAKE_NEAR;
   private static final MethodHandle LEVEL;
   private static volatile boolean failed;

   private AlpineStreams() {
   }

   static Object engine(WorldGenLevel var0) {
      if (ENGINE != null && !failed) {
         try {
            return (Object)ENGINE.invokeExact((WorldGenLevel)var0);
         } catch (Throwable var2) {
            disable(var2);
            return null;
         }
      } else {
         return null;
      }
   }

   static int lake(Object var0, int var1, int var2, int var3) {
      if (var0 != null && !failed) {
         try {
            Object var4 = (Object)LAKE_NEAR.invokeExact((Object)var0, (int)var1, (int)var2, (int)var3);
            return var4 == null ? Integer.MIN_VALUE : (int)LEVEL.invokeExact((Object)var4);
         } catch (Throwable var5) {
            disable(var5);
            return Integer.MIN_VALUE;
         }
      } else {
         return Integer.MIN_VALUE;
      }
   }

   private static void disable(Throwable var0) {
      if (!failed) {
         failed = true;
         LogUtils.getLogger().warn("Frontier Hunts: Streams Reflowing lake query failed; no further queries", var0);
      }
   }

   static {
      MethodHandle var0 = null;
      MethodHandle var1 = null;
      MethodHandle var2 = null;

      try {
         if (ModList.get() != null && ModList.get().isLoaded("streamsreflowing")) {
            Lookup var3 = MethodHandles.publicLookup();
            Class var4 = Class.forName("dev.streamsreflowing.worldgen.StreamsWorldgen");
            Class var5 = Class.forName("dev.streamsreflowing.core.RiverEngine");
            Class var6 = Class.forName("dev.streamsreflowing.core.river.Lake");
            var0 = var3.findStatic(var4, "engine", MethodType.methodType(var5, WorldGenLevel.class))
               .asType(MethodType.methodType(Object.class, WorldGenLevel.class));
            var1 = var3.findVirtual(var5, "lakeNear", MethodType.methodType(var6, int.class, int.class, int.class))
               .asType(MethodType.methodType(Object.class, Object.class, int.class, int.class, int.class));
            var2 = var3.findVirtual(var6, "level", MethodType.methodType(int.class)).asType(MethodType.methodType(int.class, Object.class));
            LogUtils.getLogger().info("Frontier Hunts: Streams Reflowing lakes are joined by scenic waterfall pools");
         }
      } catch (Throwable var7) {
         var2 = null;
         var1 = null;
         var0 = null;
         LogUtils.getLogger().warn("Frontier Hunts: Streams Reflowing lake levels unavailable; falls keep their own pools", var7);
      }

      ENGINE = var0;
      LAKE_NEAR = var1;
      LEVEL = var2;
   }
}
