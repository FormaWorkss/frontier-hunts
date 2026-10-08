package com.formaworks.frontierhunts.outfitter.client;

import com.formaworks.frontierhunts.FrontierHunts;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * [outfitter] The Frontier Handbook's leather cover plate at the top of the Handbook screen's sidebar: tooled leather,
 * gold frame, embossed antler-and-compass emblem, brass corners and the strap (texture by tools/outfitter/handbook_art.py).
 * The title text is drawn by the screen on top of it, so it stays translatable.
 */
public final class HandbookCover {
   private static final ResourceLocation PLATE = FrontierHunts.id("textures/gui/handbook/cover_plate.png");
   private static final int TW = 248;
   private static final int TH = 60;

   private HandbookCover() {
   }

   /** Draws the plate into (x, y, w, h); returns the x where the title text should start. */
   public static int plate(GuiGraphics g, int x, int y, int w, int h) {
      g.blit(PLATE, x, y, w, h, 0.0F, 0.0F, TW, TH, TW, TH);
      return x + Math.round(w * 58.0F / TW);
   }

   /** Right edge for the title text (left of the strap). */
   public static int textRight(int x, int w) {
      return x + Math.round(w * 205.0F / TW);
   }
}
