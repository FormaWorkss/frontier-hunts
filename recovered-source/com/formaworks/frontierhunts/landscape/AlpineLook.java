package com.formaworks.frontierhunts.landscape;

import com.mojang.logging.LogUtils;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;

@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
public final class AlpineLook {
   private static final MethodHandle IN_USE = find();
   private static volatile boolean shaders;
   private static volatile boolean alpine;
   private static ClientLevel level;
   private static int checks;

   private static MethodHandle find() {
      try {
         if (!ModList.get().isLoaded("iris")) {
            return null;
         } else {
            Class var0 = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object var1 = var0.getMethod("getInstance").invoke(null);
            return MethodHandles.publicLookup().unreflect(var0.getMethod("isShaderPackInUse")).bindTo(var1).asType(MethodType.methodType(boolean.class));
         }
      } catch (Throwable var2) {
         LogUtils.getLogger().info("Frontier Hunts: no Iris shader state; Alpine colours use the unshaded grade when needed");
         return null;
      }
   }

   private static boolean query() {
      if (IN_USE == null) {
         return false;
      } else {
         try {
            return (boolean)IN_USE.invokeExact();
         } catch (Throwable var1) {
            return false;
         }
      }
   }

   @SubscribeEvent
   public static void tick(Post var0) {
      Minecraft var1 = Minecraft.getInstance();
      shaders = query();
      if (var1.level != level) {
         level = var1.level;
         alpine = false;
         checks = 0;
      }

      if (level != null && !alpine && var1.player != null && checks < 40 && level.getGameTime() % 10L == 0L) {
         checks++;
         Optional var2 = level.getBiome(var1.player.blockPosition()).unwrapKey();
         if (var2.isPresent() && ((ResourceKey)var2.get()).location().getNamespace().equals("frontierhunts")) {
            alpine = true;
            if (!shaders) {
               var1.levelRenderer.allChanged();
            }
         }
      }
   }

   public static boolean shaders() {
      return shaders;
   }

   public static boolean muted() {
      return alpine && !shaders;
   }

   public static int biome(int var0) {
      if (!muted()) {
         return var0;
      } else {
         double var1 = (double)(var0 >> 16 & 0xFF) / 255.0;
         double var3 = (double)(var0 >> 8 & 0xFF) / 255.0;
         double var5 = (double)(var0 & 0xFF) / 255.0;
         double var7 = 0.3 * var1 + 0.55 * var3 + 0.15 * var5;
         var1 = (var1 + (var7 - var1) * 0.34) * 1.01;
         var3 = (var3 + (var7 - var3) * 0.34) * 0.93;
         var5 = (var5 + (var7 - var5) * 0.34) * 0.9;
         return pack(var1, var3, var5);
      }
   }

   public static int paint() {
      return muted() ? 15850207 : 16777215;
   }

   public static int times(int var0, int var1) {
      int var2 = (var0 >> 16 & 0xFF) * (var1 >> 16 & 0xFF) / 255;
      int var3 = (var0 >> 8 & 0xFF) * (var1 >> 8 & 0xFF) / 255;
      int var4 = (var0 & 0xFF) * (var1 & 0xFF) / 255;
      return var2 << 16 | var3 << 8 | var4;
   }

   private static int pack(double var0, double var2, double var4) {
      int var6 = (int)Math.round(Math.clamp(var0, 0.0, 1.0) * 255.0);
      int var7 = (int)Math.round(Math.clamp(var2, 0.0, 1.0) * 255.0);
      int var8 = (int)Math.round(Math.clamp(var4, 0.0, 1.0) * 255.0);
      return var6 << 16 | var7 << 8 | var8;
   }

   public static float[] overlayGrade() {
      return shaders ? new float[]{0.82F, 0.9F} : new float[]{1.0F, 0.97F};
   }
}
