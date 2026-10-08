package com.formaworks.frontierhunts.academy.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * [academy] The dossier's original painted art (tools/academy/gen_art.py): one 768x384 illustration per assignment
 * type / course and a 512x512 icon atlas (8 x 8 cells of 64 px, drawn small and sampled smoothly).
 */
public final class DossierArt {
   public static final int PLATE_W = 768;
   public static final int PLATE_H = 384;
   static final ResourceLocation ICONS = FrontierHunts.id("textures/gui/academy/icons.png");
   private static final Set<ResourceLocation> MISSING = new HashSet<>();

   /** Icon cells, row-major in the atlas (keep in sync with tools/academy/gen_art.py ICONS). */
   public enum Icon {
      GLASSING, STALK, RANGE, TRACKING, DRESSING, HARVEST, TRACK, TIMED,
      CLEAN, TROPHY, SUPPLY, CHECK, CLOCK, LOCK, MEDAL, WIND,
      FLAG, CROSS, ACADEMY, TOKEN, STAR, ALERT, BOOK, LEAVE,
      ARCHERY, // [onboard] cell 24: bow and arrow for the Archery Range
      UPLAND, WATERFOWL; // [1.1.7] cells 25-26: grouse in flight, drake mallard
   }

   private DossierArt() {
   }

   static ResourceLocation plate(String key) {
      return FrontierHunts.id("textures/gui/academy/plate_" + key + ".png");
   }

   private static boolean smooth(ResourceLocation tex) {
      if (MISSING.contains(tex)) {
         return false;
      }
      try {
         if (!com.formaworks.frontierhunts.artqa.client.GuiArtTexture.bind(tex, true)) { // [artqa] mipmapped: 64 px icons drawn at 9-16 GUI px stay clean
            MISSING.add(tex);
            return false;
         }
         return true;
      } catch (RuntimeException ex) {
         MISSING.add(tex);
         return false;
      }
   }

   /** [1.1.6] where each painted plate's crop band sits (0 = top, 1 = bottom), so antlers and heads stay in frame */
   private static final java.util.Map<String, Float> FOCUS = java.util.Map.of("clean", 0.42F, "archery", 0.36F, "dressing", 0.6F);

   /** Draws an illustration filling (x, y, w, h), cropped to keep its aspect (centred, biased to the lower half). */
   public static void plate(GuiGraphics g, String key, int x, int y, int w, int h, float alpha) {
      if (w <= 0 || h <= 0) {
         return;
      }
      ResourceLocation tex = plate(key);
      if (!smooth(tex)) {
         g.fill(x, y, x + w, y + h, 0xFF7A6A50);
         return;
      }
      float target = (float)w / h;
      float src = (float)PLATE_W / PLATE_H;
      int u = 0, v = 0, uw = PLATE_W, vh = PLATE_H;
      if (target > src) {
         vh = Math.round(PLATE_W / target);
         v = (int)((PLATE_H - vh) * FOCUS.getOrDefault(key, 0.55F));
      } else {
         uw = Math.round(PLATE_H * target);
         u = (PLATE_W - uw) / 2;
      }
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      g.setColor(1.0F, 1.0F, 1.0F, alpha);
      g.blit(tex, x, y, w, h, u, v, uw, vh, PLATE_W, PLATE_H);
      g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
      RenderSystem.disableBlend();
      com.formaworks.frontierhunts.guide.client.PlateMarks.academy(g, key, x, y, w, h, u, v, uw, vh, PLATE_W, PLATE_H, alpha); // [1.1.6]
   }

   /** Draws an icon (size in GUI px) at (x, y), tinted (ARGB; white = as painted). */
   public static void icon(GuiGraphics g, Icon icon, float x, float y, float size, int tint) {
      if (icon == null || size <= 0.0F || (tint >>> 24) == 0) {
         return;
      }
      if (!smooth(ICONS)) {
         return;
      }
      int i = icon.ordinal();
      int u = (i % 8) * 64, v = (i / 8) * 64;
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      g.setColor((tint >> 16 & 255) / 255.0F, (tint >> 8 & 255) / 255.0F, (tint & 255) / 255.0F, (tint >>> 24) / 255.0F);
      g.pose().pushPose();
      g.pose().translate(x, y, 0.0F);
      float k = size / 64.0F;
      g.pose().scale(k, k, 1.0F);
      g.blit(ICONS, 0, 0, 64, 64, u, v, 64, 64, 512, 512);
      g.pose().popPose();
      g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
      RenderSystem.disableBlend();
   }
}
