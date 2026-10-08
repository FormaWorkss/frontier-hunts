package com.formaworks.frontierhunts.client;

import com.mojang.logging.LogUtils;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.neoforged.fml.ModList;

public final class HuntShaderCompat {
   private static MethodHandle shadowPass = findShadowPass();

   private static MethodHandle findShadowPass() {
      if (!ModList.get().isLoaded("iris")) {
         return null;
      } else {
         try {
            Class var0 = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object var1 = var0.getMethod("getInstance").invoke(null);
            MethodHandle var2 = MethodHandles.publicLookup()
               .unreflect(var0.getMethod("isRenderingShadowPass"))
               .bindTo(var1)
               .asType(MethodType.methodType(boolean.class));
            LogUtils.getLogger().info("Frontier Hunts: Iris public API v0 shadow detail available");
            return var2;
         } catch (LinkageError | ReflectiveOperationException var3) {
            LogUtils.getLogger().warn("Frontier Hunts: Iris shadow detail unavailable; retaining ordinary mesh selection", var3);
            return null;
         }
      }
   }

   public static boolean shadowPass() {
      if (shadowPass == null) {
         return false;
      } else {
         try {
            return (boolean)shadowPass.invokeExact();
         } catch (Throwable var1) {
            shadowPass = null;
            LogUtils.getLogger().warn("Frontier Hunts: Iris shadow query failed; retaining ordinary mesh selection", var1);
            return false;
         }
      }
   }

   private HuntShaderCompat() {
   }
}
