package com.formaworks.frontierhunts.outfitter;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.HuntConfig;
import net.minecraft.resources.ResourceLocation;

/**
 * [outfitter] Texture of a worn outfit: {@code textures/entity/outfitter/<group>.png} (1 texel per model pixel, the
 * Minecraft look of the Vanilla graphics preset) or {@code <group>_hd.png} (4 texels per pixel, Ultra / Custom).
 * Common class: the armour items ask for it from {@code getArmorTexture}, which only runs on the client; on a dedicated
 * server the client config is never read.
 */
public final class OutfitTextures {
   private OutfitTextures() {
   }

   /** True on the Vanilla preset (stored as CLASSIC). Any config trouble picks the detailed textures. */
   public static boolean vanillaLook() {
      try {
         return HuntConfig.GRAPHICS_PRESET.get() == HuntConfig.GraphicsPreset.CLASSIC;
      } catch (RuntimeException | LinkageError e) {
         return false;
      }
   }

   private static final java.util.Map<String, ResourceLocation> VANILLA = new java.util.concurrent.ConcurrentHashMap<>();
   private static final java.util.Map<String, ResourceLocation> HD = new java.util.concurrent.ConcurrentHashMap<>();

   public static ResourceLocation of(String group) {
      boolean v = vanillaLook();
      return (v ? VANILLA : HD).computeIfAbsent(group, g -> FrontierHunts.id("textures/entity/outfitter/" + g + (v ? ".png" : "_hd.png")));
   }
}
