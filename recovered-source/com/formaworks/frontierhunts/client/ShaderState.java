package com.formaworks.frontierhunts.client;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.client.Minecraft;

/** Whether an Iris shader pack is on (Iris public API by reflection; false without Iris). [shaderperf] copied from the base jar. */
public final class ShaderState {
   public static volatile boolean on;
   private static MethodHandle inUse;
   private static Object api;
   private static boolean missing;

   private ShaderState() {
   }

   public static void tick() {
      boolean var0 = query();
      if (var0 != on) {
         on = var0;
         com.formaworks.frontierhunts.perf.client.ShaderPerf.shaderPackChanged(); // [shaderperf] lighter trees/grass before the re-mesh
         Minecraft var1 = Minecraft.getInstance();
         if (var1.level != null && var1.levelRenderer != null) {
            var1.levelRenderer.allChanged();
         }
      }
   }

   private static boolean query() {
      if (missing) {
         return false;
      } else {
         try {
            if (inUse == null) {
               Class var0 = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
               api = var0.getMethod("getInstance").invoke(null);
               inUse = MethodHandles.publicLookup().findVirtual(var0, "isShaderPackInUse", MethodType.methodType(boolean.class));
            }

            return (boolean)inUse.invoke((Object)api);
         } catch (Throwable var1) {
            missing = true;
            return false;
         }
      }
   }
}
