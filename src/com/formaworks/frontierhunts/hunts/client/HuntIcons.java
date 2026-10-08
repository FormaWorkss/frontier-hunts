package com.formaworks.frontierhunts.hunts.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.journal.client.JournalIcons;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * [hunts] Species-hunt icons in the journal's pixel-art style (tools/hunts/icons.py, drawn with the ledger's px engine):
 * one 256 x 128 atlas, textures/gui/journal/hunts_icons.png, 8 x 2 cells of 32 px on top and the brightened copies
 * below. Names not in this atlas fall back to the journal's own atlas (reticle, glass, antlers...). Same crisp/bilinear
 * rule as {@link JournalIcons}. References are {@code "hunt:<name>"} (JournalIcons.drawRef hands them here).
 */
public final class HuntIcons {
   public static final String PREFIX = "hunt:";
   public static final ResourceLocation ATLAS = FrontierHunts.id("textures/gui/journal/hunts_icons.png");
   /** [integ6] the same atlas upscaled 2x (nearest), 512 x 256, for sharp in-between scales (as JournalIcons.ATLAS_X2). */
   static final ResourceLocation ATLAS_X2 = FrontierHunts.id("textures/gui/journal/hunts_icons_x2.png");
   private static final int CELL = 32;
   private static final int W = 256;
   private static final int H = 128;
   /** Atlas order (row-major, 8 per row) - rewritten by tools/hunts/icons.py, do not edit by hand. */
   private static final String[] NAMES = {
      // GENERATED-NAMES-BEGIN
      "camera", "call", "medal", "blind", "scale", "moon", "bait", "camo",
      "howl", "pelt", "hound", "flush", "wing", "limit", "decoy", "hunts",
      // GENERATED-NAMES-END
   };
   /** hunt icon names drawn from the journal atlas */
   private static final Map<String, String> JOURNAL = Map.of("reticle", "marksmanship", "glass", "glass", "antlers", "antlers", "boot", "boot", "eye",
      "stalking", "stalk", "stalking");
   private static final Map<String, Integer> INDEX = new HashMap<>();

   static {
      for (int i = 0; i < NAMES.length; i++) {
         INDEX.put(NAMES[i], i);
      }
   }

   private HuntIcons() {
   }

   public static String ref(String name) {
      return PREFIX + name;
   }

   public static boolean has(String name) {
      return name != null && (INDEX.containsKey(name) || JOURNAL.containsKey(name));
   }

   public static void draw(GuiGraphics g, String name, float x, float y, int size, boolean bright, float alpha) {
      String j = JOURNAL.get(name);
      if (j != null) {
         JournalIcons.draw(g, j, x, y, size, bright, alpha);
         return;
      }
      Integer i = name == null ? null : INDEX.get(name);
      if (i == null || alpha <= 0.01F || size <= 0) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      double px = size * mc.getWindow().getGuiScale();
      boolean crisp = Math.abs(px / CELL - Math.rint(px / CELL)) < 0.01 && Math.rint(px / CELL) >= 1;
      // [integ6] same sampling as JournalIcons ([artqa]): nearest at whole multiples, the 2x nearest copy sampled
      // bilinear at larger in-between sizes, the mipmapped atlas below 32 px
      boolean x2 = !crisp && px > CELL + 0.5;
      ResourceLocation atlas = x2 ? ATLAS_X2 : ATLAS;
      int cell = x2 ? CELL * 2 : CELL, w = x2 ? W * 2 : W, h = x2 ? H * 2 : H;
      if (!com.formaworks.frontierhunts.artqa.client.GuiArtTexture.bind(atlas, !crisp)) {
         atlas = ATLAS;
         cell = CELL;
         w = W;
         h = H;
         AbstractTexture tex = mc.getTextureManager().getTexture(ATLAS);
         tex.setFilter(!crisp, false);
      }
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      if (alpha < 0.999F) {
         g.setColor(1.0F, 1.0F, 1.0F, alpha);
      }
      g.pose().pushPose();
      g.pose().translate(x, y, 0.0F);
      g.blit(atlas, 0, 0, size, size, (i % 8) * cell, (i / 8) * cell + (bright ? h / 2 : 0), cell, cell, w, h);
      g.pose().popPose();
      if (alpha < 0.999F) {
         g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
      }
      RenderSystem.disableBlend();
   }
}
