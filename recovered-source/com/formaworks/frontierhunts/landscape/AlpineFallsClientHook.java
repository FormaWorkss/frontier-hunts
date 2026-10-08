package com.formaworks.frontierhunts.landscape;

import com.mojang.logging.LogUtils;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;

@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
public final class AlpineFallsClientHook {
   private static List<AlpineFallsPayload.Fall> falls = List.of();
   private static Object level;
   private static Boolean dh;
   private static boolean dhFailed;
   private static final Map<String, MethodHandle> HANDLES = new HashMap<>();

   static void receive(AlpineFallsPayload var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (var1.level != null && var1.level.dimension().location().toString().equals(var0.dimension())) {
         falls = var0.falls();
         level = var1.level;
         if (distantHorizons()) {
            guard(() -> call("sync", falls));
         }
      }
   }

   public static List<AlpineFallsPayload.Fall> falls() {
      return falls;
   }

   @SubscribeEvent
   public static void tick(Post var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (var1.level != level) {
         falls = List.of();
         level = var1.level;
         if (distantHorizons()) {
            guard(() -> call("clear", null));
         }
      }

      if (var1.level != null && !var1.isPaused() && distantHorizons()) {
         guard(() -> call("tick", var1));
      }
   }

   private static boolean distantHorizons() {
      if (dh == null) {
         dh = ModList.get().isLoaded("distanthorizons");
      }

      return dh && !dhFailed && AlpineWaterfallOptions.visuals();
   }

   private static void call(String var0, Object var1) {
      try {
         MethodHandle var2 = HANDLES.get(var0);
         if (var2 == null) {
            Class var3 = Class.forName("com.formaworks.frontierhunts.landscape.AlpineDhFalls");
            Method var4 = null;

            for (Method var8 : var3.getDeclaredMethods()) {
               if (var8.getName().equals(var0) && Modifier.isStatic(var8.getModifiers())) {
                  var4 = var8;
               }
            }

            if (var4 == null) {
               throw new NoSuchMethodException(var0);
            }

            var4.setAccessible(true);
            var2 = MethodHandles.lookup().unreflect(var4);
            HANDLES.put(var0, var2);
         }

         if (var2.type().parameterCount() == 0) {
            var2.invoke();
         } else {
            var2.invoke((Object)var1);
         }
      } catch (Error | RuntimeException var9) {
         throw var9;
      } catch (Throwable var10) {
         throw new RuntimeException(var10);
      }
   }

   private static void guard(Runnable var0) {
      try {
         var0.run();
      } catch (Throwable var2) {
         dhFailed = true;
         LogUtils.getLogger().warn("Frontier waterfalls: Distant Horizons integration disabled ({})", var2.toString());
      }
   }

   private AlpineFallsClientHook() {
   }
}
