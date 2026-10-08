package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.HuntConfig;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;

public final class RealisticWorld {
   public static final String PACK_PATH = "resourcepacks/realistic_world";
   static final ResourceLocation PACK_LOCATION = ResourceLocation.fromNamespaceAndPath("frontierhunts", "resourcepacks/realistic_world");
   static final ResourceLocation ROUND_LIST = ResourceLocation.fromNamespaceAndPath("frontierhunts", "realistic_world/round_blocks.txt");
   private static volatile Set<Block> roundBlocks = Collections.emptySet();
   public static volatile boolean round;
   private static boolean pending = true;

   private RealisticWorld() {
   }

   public static boolean isRound(Block var0) {
      return roundBlocks.contains(var0);
   }

   static void requestSync() {
      pending = true;
   }

   static boolean wanted() {
      try {
         return HuntConfig.WORLD_LOOK.get() == HuntConfig.WorldLook.REALISTIC;
      } catch (RuntimeException var1) {
         return false;
      }
   }

   static String packId(PackRepository var0) {
      for (String var2 : var0.getAvailableIds()) {
         if (var2.endsWith("resourcepacks/realistic_world")) {
            return var2;
         }
      }

      return null;
   }

   static boolean adjust(PackRepository var0, List<String> var1) {
      String var2 = packId(var0);
      if (var2 == null) {
         return false;
      } else {
         boolean var3 = var1.contains(var2);
         boolean var4 = wanted();
         if (var3 == var4) {
            return false;
         } else {
            if (var4) {
               var1.add(var2);
            } else {
               var1.remove(var2);
            }

            return true;
         }
      }
   }

   static void tick() {
      if (pending) {
         Minecraft var0 = Minecraft.getInstance();
         if (var0.getOverlay() == null) {
            pending = false;
            FrontierGraphics.migrate();

            try {
               PackRepository var1 = var0.getResourcePackRepository();
               ArrayList var2 = new ArrayList(var1.getSelectedIds());
               if (adjust(var1, var2)) {
                  var1.setSelected(var2);
                  var0.options.updateResourcePacks(var1);
               }
            } catch (RuntimeException var3) {
            }
         }
      }
   }

   static void reloaded(ResourceManager var0) {
      Set var1 = Collections.newSetFromMap(new IdentityHashMap());

      try {
         Resource var2 = (Resource)var0.getResource(ROUND_LIST).orElse(null);
         if (var2 != null) {
            String var4;
            try (BufferedReader var3 = var2.openAsReader()) {
               while ((var4 = var3.readLine()) != null) {
                  var4 = var4.trim();
                  if (!var4.isEmpty() && !var4.startsWith("#")) {
                     ResourceLocation var5 = ResourceLocation.tryParse(var4);
                     if (var5 != null) {
                        BuiltInRegistries.BLOCK.getOptional(var5).ifPresent(var1::add);
                     }
                  }
               }
            }
         }
      } catch (Exception var8) {
      }

      roundBlocks = var1;
      round = !var1.isEmpty();
   }
}
