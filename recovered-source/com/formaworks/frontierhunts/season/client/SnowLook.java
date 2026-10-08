package com.formaworks.frontierhunts.season.client;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.client.FrontierGraphics;
import net.minecraft.client.Minecraft;

/**
 * [1.1.2] Which snow is drawn: the realistic Frontier snowpack ({@link SmoothSnowModel}, {@link SnowPrints},
 * {@link SnowGlints}) or plain Minecraft layers. Read on the chunk-build threads, so it is cached in volatile fields and
 * refreshed from the config by {@link #apply()} (settings screen, preset change) and once a second on the client tick.
 */
public final class SnowLook {
   private SnowLook() {
   }

   private static volatile boolean frontier = true;
   private static volatile boolean shaders;
   private static boolean read;

   /** the realistic snowpack is on */
   public static boolean frontier() {
      if (!read) {
         refresh();
      }
      return frontier;
   }

   /** a shader pack lights the snow itself (then no light is baked into the snow's vertex colours) */
   public static boolean shaders() {
      return shaders;
   }

   private static boolean refresh() {
      boolean f, s;
      try {
         // [1.2.2] once, move games that were on Minecraft snow layers to the smooth snowpack
         if (!HuntConfig.SNOW_STYLE_122.get()) {
            HuntConfig.SNOW_STYLE_122.set(true);
            HuntConfig.SNOW_STYLE.set(HuntConfig.SnowStyle.FRONTIER);
            try {
               HuntConfig.CLIENT.save();
            } catch (RuntimeException e) {
               // saved with the next settings change
            }
         }
         f = HuntConfig.SNOW_STYLE.get() == HuntConfig.SnowStyle.FRONTIER;
      } catch (RuntimeException e) {
         f = true;
      }
      try {
         s = FrontierGraphics.shaderPackActive();
      } catch (RuntimeException | LinkageError e) {
         s = false;
      }
      boolean changed = read && (f != frontier || s != shaders);
      frontier = f;
      shaders = s;
      read = true;
      return changed;
   }

   /** re-reads the setting; when the snow style (or the shader pack) changed, the chunks are rebuilt */
   public static void apply() {
      if (refresh()) {
         Minecraft mc = Minecraft.getInstance();
         if (mc != null && mc.levelRenderer != null && mc.level != null) {
            mc.levelRenderer.allChanged();
         }
         if (!frontier) {
            SnowPrints.DENTS.clear();
         }
      }
   }
}
