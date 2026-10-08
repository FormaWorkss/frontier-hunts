package com.formaworks.frontierhunts.guide.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * [fieldbook] Field School medallion icons (objective card and toasts): ink-and-watercolour drawings on a parchment
 * disc, 64 px cells in a 4 x 4 atlas (textures/gui/field_school/icons.png, painted by tools/guide/gen_art.py) so they
 * match the guide plates and read on the dark card and toast backgrounds. Drawn with bilinear filtering.
 */
final class GuideIcons {
   static final ResourceLocation ATLAS = FrontierHunts.id("textures/gui/field_school/icons.png");
   /** Atlas order (row-major) - keep in sync with ICONS in tools/guide/gen_art.py. */
   private static final String[] NAMES = {
      "wind", "sign", "glass", "stalk", "shot", "trail", "harvest", "tips",
      "deer", "blizzard", "season", "predator", "winded", "spotted", "trophy", "journal"
   };
   private static final int CELL = 64;
   private static final int SIZE = 256;

   private GuideIcons() {
   }

   static int index(String name) {
      if (name != null) {
         for (int i = 0; i < NAMES.length; i++) {
            if (NAMES[i].equals(name)) {
               return i;
            }
         }
      }
      return -1;
   }

   /** Icon for a field note (Tip key). */
   static String forTip(String tipKey) {
      return switch (tipKey) {
         case "blood" -> "trail";
         case "deer", "winded", "blizzard", "season", "predator" -> tipKey;
         // [integ4] Frontier Survival field notes
         case "cold" -> "blizzard";
         case "stockup" -> "season";
         case "hunger", "spoiled" -> "harvest";
         default -> "journal";
      };
   }

   /** Icon for a coaching hint key (winded / spotted / dropped / ran / dressed / blood). */
   static String forHint(String hintKey) {
      return switch (hintKey) {
         case "winded" -> "winded";
         case "spotted" -> "spotted";
         case "dropped" -> "shot";
         case "dressed" -> "trophy";
         case "ran", "blood" -> "trail";
         default -> "journal";
      };
   }

   /** Draws the named icon at (x, y), size x size GUI pixels. Unknown names draw nothing. */
   static void draw(GuiGraphics g, String name, int x, int y, int size, float alpha) {
      if (name != null && name.startsWith("item:")) { // [onboard] Handbook tasks show their real item
         if (alpha > 0.35F) {
            HandbookUi.item(g, name.substring(5), x + (size - 16) / 2.0F, y + (size - 16) / 2.0F, Math.min(1.25F, size / 16.0F));
         }
         return;
      }
      int i = index(name);
      if (i < 0 || alpha <= 0.01F) {
         return;
      }
      com.formaworks.frontierhunts.artqa.client.GuiArtTexture.bind(ATLAS, true); // [artqa] mipmapped: clean at every GUI scale
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      g.setColor(1.0F, 1.0F, 1.0F, alpha);
      g.blit(ATLAS, x, y, size, size, (i % 4) * CELL, (i / 4) * CELL, CELL, CELL, SIZE, SIZE);
      g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
      RenderSystem.disableBlend();
   }
}
