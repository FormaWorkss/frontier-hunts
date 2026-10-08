package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.HuntConfig;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue;

public final class PresetPacks {
   public static final String KEEP = "";
   public static final String SHADERS_OFF = "OFF";
   private static String lastMessage = "";

   private PresetPacks() {
   }

   static int slot(HuntConfig.GraphicsPreset var0) {
      return switch (var0) {
         case CLASSIC -> 0;
         case BALANCED -> 1;
         case HIGH -> 2;
         case ULTRA -> 3;
         case CUSTOM -> -1;
      };
   }

   public static ConfigValue<String> resourceValue(HuntConfig.GraphicsPreset var0) {
      int var1 = slot(var0);
      return var1 < 0 ? null : HuntConfig.PRESET_RESOURCE_PACK[var1];
   }

   public static ConfigValue<String> shaderValue(HuntConfig.GraphicsPreset var0) {
      int var1 = slot(var0);
      return var1 < 0 ? null : HuntConfig.PRESET_SHADER_PACK[var1];
   }

   public static String lastMessage() {
      return lastMessage;
   }

   public static List<String> resourcePackIds() {
      ArrayList var0 = new ArrayList();
      var0.add("");
      PackRepository var1 = Minecraft.getInstance().getResourcePackRepository();

      for (Pack var3 : var1.getAvailablePacks()) {
         if (var3.getId().startsWith("file/") && !var3.isRequired() && !var3.isHidden()) {
            var0.add(var3.getId());
         }
      }

      return var0;
   }

   public static String resourcePackName(String var0) {
      if (var0 != null && !var0.isEmpty()) {
         Pack var1 = Minecraft.getInstance().getResourcePackRepository().getPack(var0);
         String var2 = var1 != null ? var1.getTitle().getString() : var0.substring(var0.indexOf(47) + 1);
         return var1 == null ? var2 + " (missing)" : var2;
      } else {
         return "Don't change";
      }
   }

   private static void applyResourcePack(HuntConfig.GraphicsPreset var0) {
      ConfigValue var1 = resourceValue(var0);
      if (var1 != null) {
         String var2 = (String)var1.get();
         Minecraft var3 = Minecraft.getInstance();
         PackRepository var4 = var3.getResourcePackRepository();
         LinkedHashSet var5 = new LinkedHashSet();

         for (ConfigValue var9 : HuntConfig.PRESET_RESOURCE_PACK) {
            String var10 = (String)var9.get();
            if (var10 != null && !var10.isEmpty()) {
               var5.add(var10);
            }
         }

         ArrayList var11 = new ArrayList(var4.getSelectedIds());
         ArrayList var12 = new ArrayList();

         for (String var14 : var11) {
            if (!var5.contains(var14)) {
               var12.add(var14);
            }
         }

         if (!var2.isEmpty()) {
            if (var4.isAvailable(var2)) {
               var12.add(var2);
            } else {
               lastMessage = "Texture pack not found: " + var2;
            }
         }

         RealisticWorld.adjust(var4, var12);
         if (!var12.equals(var11)) {
            var4.setSelected(var12);
            var3.options.updateResourcePacks(var4);
         }
      }
   }

   public static boolean irisPresent() {
      return ModList.get().isLoaded("iris") || ModList.get().isLoaded("oculus");
   }

   public static List<String> shaderPackNames() {
      ArrayList var0 = new ArrayList();
      var0.add("");
      var0.add("OFF");
      Path var1 = Minecraft.getInstance().gameDirectory.toPath().resolve("shaderpacks");
      if (Files.isDirectory(var1)) {
         try (Stream var2 = Files.list(var1)) {
            var2.<String>map(var0x -> var0x.getFileName().toString())
               .filter(var1x -> var1x.toLowerCase(Locale.ROOT).endsWith(".zip") || Files.isDirectory(var1.resolve(var1x)))
               .sorted(String.CASE_INSENSITIVE_ORDER)
               .forEach(var0::add);
         } catch (IOException var7) {
         }
      }

      return var0;
   }

   public static String shaderPackName(String var0) {
      if (var0 == null || var0.isEmpty()) {
         return "Don't change";
      } else if ("OFF".equals(var0)) {
         return "Shaders off";
      } else {
         return var0.toLowerCase(Locale.ROOT).endsWith(".zip") ? var0.substring(0, var0.length() - 4) : var0;
      }
   }

   private static void applyShaderPack(HuntConfig.GraphicsPreset var0) {
      ConfigValue var1 = shaderValue(var0);
      if (var1 != null && irisPresent()) {
         String var2 = (String)var1.get();
         if (var2 != null && !var2.isEmpty()) {
            try {
               if ("OFF".equals(var2)) {
                  setShadersEnabled(false);
                  return;
               }

               Class var3 = Class.forName("net.irisshaders.iris.Iris");
               Object var4 = var3.getMethod("getIrisConfig").invoke(null);
               String var6 = var4.getClass().getMethod("getShaderPackName").invoke(var4) instanceof Optional var7 ? (String)var7.orElse(null) : null;
               boolean var11 = (Boolean)var4.getClass().getMethod("areShadersEnabled").invoke(var4);
               if (var2.equals(var6) && var11) {
                  return;
               }

               var4.getClass().getMethod("setShaderPackName", String.class).invoke(var4, var2);
               var4.getClass().getMethod("setShadersEnabled", boolean.class).invoke(var4, true);
               var4.getClass().getMethod("save").invoke(var4);
               Method var8 = var3.getMethod("reload");
               var8.invoke(null);
            } catch (Throwable var10) {
               lastMessage = "Couldn't switch shader pack automatically - pick it in Iris' Shader Packs screen.";

               try {
                  setShadersEnabled(true);
               } catch (Throwable var9) {
               }
            }
         }
      }
   }

   private static void setShadersEnabled(boolean var0) throws ReflectiveOperationException {
      Class var1 = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
      Object var2 = var1.getMethod("getInstance").invoke(null);
      Object var3 = var1.getMethod("getConfig").invoke(var2);
      Class var4 = Class.forName("net.irisshaders.iris.api.v0.IrisApiConfig");
      var4.getMethod("setShadersEnabledAndApply", boolean.class).invoke(var3, var0);
   }

   public static void apply(HuntConfig.GraphicsPreset var0) {
      lastMessage = "";

      try {
         applyShaderPack(var0);
      } catch (Throwable var3) {
         lastMessage = "Shader pack switch failed: " + var3.getClass().getSimpleName();
      }

      try {
         applyResourcePack(var0);
      } catch (Throwable var2) {
         lastMessage = "Texture pack switch failed: " + var2.getClass().getSimpleName();
      }

      if (!lastMessage.isEmpty() && Minecraft.getInstance().player != null) {
         Minecraft.getInstance().player.displayClientMessage(Component.literal(lastMessage), true);
      }
   }
}
