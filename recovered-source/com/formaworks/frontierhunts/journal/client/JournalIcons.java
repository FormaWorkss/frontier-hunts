package com.formaworks.frontierhunts.journal.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * [ledger] The Hunter's Journal / Expedition journal icon set: original 32 x 32 pixel art (tools/ledger/icons.py) in one
 * 512 x 512 atlas, textures/gui/journal/icons.png. Top half = normal icons, bottom half = the same icons brightened for
 * hover / selected. Drawn with nearest filtering whenever the on-screen size is a whole multiple of the 32 px art (GUI
 * scale 2 / 4 at 16 px, every scale at 32 px) so the pixels stay crisp; bilinear otherwise.
 *
 * <p>Icon references used across the journal are strings: {@code "icon:<name>"} for an atlas icon, anything else is an
 * item id rendered as an item (gear entries show the actual item).
 */
public final class JournalIcons {
   public static final ResourceLocation ATLAS = FrontierHunts.id("textures/gui/journal/icons.png");
   /** [artqa] the same atlas upscaled 2x (nearest), 1024 x 1024, for sharp in-between scales. */
   static final ResourceLocation ATLAS_X2 = FrontierHunts.id("textures/gui/journal/icons_x2.png");
   public static final String PREFIX = "icon:";
   private static final int CELL = 32;
   private static final int SIZE = 512;
   /** Atlas order (row-major, 16 per row) - rewritten by tools/ledger/icons.py, do not edit by hand. */
   private static final String[] NAMES = {
      // GENERATED-NAMES-BEGIN
      "home", "checklist", "skills", "species", "records", "notes", "reserve", "survival",
      "campaign", "contracts", "equipment", "training", "lodge", "ledger", "seasons", "school",
      "token", "next", "lock", "star", "party", "marker", "clock", "assign",
      "guide", "marksmanship", "stalking", "tracking", "butchery", "woodcraft", "rank_greenhorn", "rank_woodsman",
      "rank_tracker", "rank_guide", "rank_master", "rank_legend", "hunting", "world", "camp", "blood",
      "rub", "snowflake", "winter", "boot", "podium", "predator", "skull", "antlers",
      "leaf", "quill", "sp_whitetail", "sp_elk", "sp_moose", "sp_pronghorn", "sp_bison", "sp_boar",
      "sp_coyote", "sp_wolf", "sp_cougar", "sp_panther", "sp_cheetah", "sp_lion", "sp_black_bear", "sp_grizzly",
      "sp_polar_bear", "sp_grouse", "sp_duck", "glass", "wind", "fish", "soon", "path",
      // GENERATED-NAMES-END
   };
   private static final Map<String, Integer> INDEX = new HashMap<>();

   static {
      for (int i = 0; i < NAMES.length; i++) {
         INDEX.put(NAMES[i], i);
      }
   }

   private JournalIcons() {
   }

   public static boolean has(String name) {
      return name != null && INDEX.containsKey(name);
   }

   public static String ref(String name) {
      return PREFIX + name;
   }

   /** Atlas name of a reference ({@code "icon:x"} -> x), or null when it is an item id / unknown. */
   public static String atlasName(String ref) {
      if (ref != null && ref.startsWith(PREFIX)) {
         String n = ref.substring(PREFIX.length());
         return INDEX.containsKey(n) ? n : null;
      }
      return null;
   }

   /** Draws atlas icon {@code name} at (x, y), size x size GUI px. Unknown names draw nothing. */
   public static void draw(GuiGraphics g, String name, float x, float y, int size, boolean bright, float alpha) {
      Integer i = name == null ? null : INDEX.get(name);
      if (i == null || alpha <= 0.01F || size <= 0) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      double px = size * mc.getWindow().getGuiScale();
      boolean crisp = Math.abs(px / CELL - Math.rint(px / CELL)) < 0.01 && Math.rint(px / CELL) >= 1;
      // [artqa] whole multiples of the 32 px art: nearest. Larger in-between sizes (16 px icons at GUI scale 3 = 48 px)
      // sample a 2x nearest-upscaled copy of the atlas bilinearly ("sharp bilinear": crisp pixels, only the pixel
      // edges blend) instead of smearing the 32 px art; smaller sizes use the mipmapped atlas.
      boolean x2 = !crisp && px > CELL + 0.5;
      ResourceLocation atlas = x2 ? ATLAS_X2 : ATLAS;
      int cell = x2 ? CELL * 2 : CELL, sz = x2 ? SIZE * 2 : SIZE;
      if (!com.formaworks.frontierhunts.artqa.client.GuiArtTexture.bind(atlas, !crisp)) {
         atlas = ATLAS;
         cell = CELL;
         sz = SIZE;
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
      g.blit(atlas, 0, 0, size, size, (i % 16) * cell, (i / 16) * cell + (bright ? sz / 2 : 0), cell, cell, sz, sz);
      g.pose().popPose();
      if (alpha < 0.999F) {
         g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
      }
      RenderSystem.disableBlend();
   }

   /** Silhouette shadow (the icon tinted black). */
   public static void shadow(GuiGraphics g, String name, float x, float y, int size, float alpha) {
      g.setColor(0.0F, 0.0F, 0.0F, alpha);
      drawRaw(g, name, x, y, size);
      g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
   }

   private static void drawRaw(GuiGraphics g, String name, float x, float y, int size) {
      Integer i = name == null ? null : INDEX.get(name);
      if (i == null) {
         return;
      }
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      g.pose().pushPose();
      g.pose().translate(x, y, 0.0F);
      g.blit(ATLAS, 0, 0, size, size, (i % 16) * CELL, (i / 16) * CELL, CELL, CELL, SIZE, SIZE);
      g.pose().popPose();
      RenderSystem.disableBlend();
   }

   /**
    * Tab / button icon: hover or selected lifts it 1 px with a soft drop shadow and swaps in the brightened art.
    */
   public static void drawLifted(GuiGraphics g, String name, float x, float y, int size, boolean lifted) {
      if (lifted) {
         shadow(g, name, x + 0.5F, y + 1.0F, size, 0.35F);
         draw(g, name, x, y - 1.0F, size, true, 1.0F);
      } else {
         draw(g, name, x, y, size, false, 1.0F);
      }
   }

   /** Draws an icon reference: atlas icon or item (items are 16 px, scaled to {@code size}). */
   public static void drawRef(GuiGraphics g, String ref, float x, float y, int size, boolean bright, float alpha) {
      if (ref != null && ref.startsWith(com.formaworks.frontierhunts.hunts.client.HuntIcons.PREFIX)) {
         com.formaworks.frontierhunts.hunts.client.HuntIcons.draw(g, ref.substring(com.formaworks.frontierhunts.hunts.client.HuntIcons.PREFIX.length()), x, y, size, bright, alpha); // [hunts] hunt icon atlas
         return;
      }
      String n = atlasName(ref);
      if (n != null) {
         draw(g, n, x, y, size, bright, alpha);
         return;
      }
      ItemStack stack = JournalUi.icon(ref);
      if (stack.isEmpty()) {
         return;
      }
      g.pose().pushPose();
      g.pose().translate(x, y, 0.0F);
      if (size != 16) {
         g.pose().scale(size / 16.0F, size / 16.0F, 1.0F);
      }
      g.renderItem(stack, 0, 0);
      g.pose().popPose();
   }
}
