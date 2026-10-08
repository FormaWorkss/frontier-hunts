package com.formaworks.frontierhunts.guide.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.client.FrontierUi;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * [guide] The Field School plates (original ink-and-watercolour art from tools/guide/gen_art.py) and their labels. The art carries no
 * text: labels come from the lang file and are drawn here at the anchors the generator printed, so every language
 * gets a readable plate.
 */
enum GuideArt {
   // [fieldbook] 1024 x 512 ink-and-watercolour plates (tools/guide/gen_art.py); label anchors are in 0..1 plate space
   // [1.1.6] painted plates (1280 x 640 art, drawn in 1024 x 512 plate units); anchors re-measured on the new art
   WIND("wind", 1024, 512, List.of(
      L.at("wind", 0.155F, 0.13F, 0.155F, 0.13F, 0, GuideUi.BLUE, 0.16F),
      L.lead("you", 0.28F, 0.47F, 0.21F, 0.36F, 0, GuideUi.INK_BROWN, 0.12F),
      L.lead("cone", 0.45F, 0.555F, 0.45F, 0.86F, 0, 0xFF8A5A14, 0.26F),
      L.lead("winded", 0.585F, 0.425F, 0.56F, 0.24F, 0, GuideUi.RED, 0.20F),
      L.lead("upwind", 0.135F, 0.41F, 0.12F, 0.62F, 0, GuideUi.GREEN, 0.19F),
      L.at("thermal_up", 0.84F, 0.0F, 0.84F, 0.0F, 0, 0xFF9A4E1E, 0.30F),
      L.at("thermal_down", 0.84F, 0.475F, 0.84F, 0.475F, 0, GuideUi.BLUE, 0.30F))),
   TRACKS("tracks", 1024, 512, List.of(
      L.at("whitetail", 0.1F, 0.415F, 0.1F, 0.415F, 0, GuideUi.INK_BROWN, 0.19F),
      L.at("elk", 0.3F, 0.415F, 0.3F, 0.415F, 0, GuideUi.INK_BROWN, 0.19F),
      L.at("moose", 0.5F, 0.415F, 0.5F, 0.415F, 0, GuideUi.INK_BROWN, 0.19F),
      L.at("bison", 0.7F, 0.415F, 0.7F, 0.415F, 0, GuideUi.INK_BROWN, 0.19F),
      L.at("boar", 0.9F, 0.415F, 0.9F, 0.415F, 0, GuideUi.INK_BROWN, 0.19F),
      L.at("canine", 0.1F, 0.89F, 0.1F, 0.89F, 0, GuideUi.INK_BROWN, 0.19F),
      L.at("feline", 0.3F, 0.89F, 0.3F, 0.89F, 0, GuideUi.INK_BROWN, 0.19F),
      L.at("bear", 0.5F, 0.89F, 0.5F, 0.89F, 0, GuideUi.INK_BROWN, 0.19F),
      L.at("grouse", 0.7F, 0.89F, 0.7F, 0.89F, 0, GuideUi.INK_BROWN, 0.19F),
      L.at("duck", 0.9F, 0.89F, 0.9F, 0.89F, 0, GuideUi.INK_BROWN, 0.19F),
      L.at("scale", 0.115F, 0.005F, 0.115F, 0.005F, -1, GuideUi.INK_BROWN, 0.40F))),
   SIGN("sign", 1024, 512, List.of(
      L.lead("rub", 0.287F, 0.50F, 0.33F, 0.60F, -1, GuideUi.INK_BROWN, 0.17F),
      L.lead("licking", 0.755F, 0.38F, 0.62F, 0.06F, 0, GuideUi.INK_BROWN, 0.24F),
      L.lead("scrape", 0.72F, 0.68F, 0.72F, 0.89F, 0, GuideUi.INK_BROWN, 0.28F),
      L.at("where", 0.47F, 0.93F, 0.25F, 0.91F, 0, GuideUi.GREEN, 0.30F))),
   GLASS("glass", 1024, 512, List.of(
      L.at("edge", 0.27F, 0.90F, 0.27F, 0.90F, 0, GuideUi.INK_BROWN, 0.40F),
      L.lead("camera", 0.875F, 0.50F, 0.72F, 0.04F, 0, GuideUi.INK_BROWN, 0.30F))),
   STALK("stalk", 1024, 512, List.of(
      L.at("wind", 0.53F, 0.0F, 0.53F, 0.0F, 0, GuideUi.BLUE, 0.40F),
      L.lead("crouch", 0.20F, 0.45F, 0.12F, 0.17F, 0, GuideUi.INK_BROWN, 0.20F),
      L.lead("cover", 0.45F, 0.62F, 0.46F, 0.88F, 0, GuideUi.INK_BROWN, 0.30F),
      L.lead("deer", 0.80F, 0.55F, 0.78F, 0.86F, 0, GuideUi.INK_BROWN, 0.24F))),
   VITALS("vitals", 1024, 512, List.of(
      L.lead("lungs", 0.32F, 0.44F, 0.30F, 0.21F, -1, 0xFF9C3A3A, 0.21F),
      L.lead("heart", 0.29F, 0.56F, 0.36F, 0.80F, 0, GuideUi.RED, 0.19F),
      L.lead("liver", 0.405F, 0.45F, 0.47F, 0.14F, -1, 0xFF5E241C, 0.22F),
      L.lead("gut", 0.47F, 0.48F, 0.60F, 0.25F, -1, 0xFF5E5A24, 0.22F),
      L.lead("aim", 0.292F, 0.535F, 0.01F, 0.62F, -1, GuideUi.RED, 0.13F),
      L.at("quarter", 0.87F, 0.86F, 0.87F, 0.86F, 0, GuideUi.RED, 0.26F))),
   BLOOD("blood", 1024, 512, List.of(
      L.at("heart", 0.115F, 0.405F, 0.115F, 0.405F, 0, GuideUi.RED, 0.19F),
      L.at("lungs", 0.31F, 0.405F, 0.31F, 0.405F, 0, 0xFF9C3A3A, 0.19F),
      L.at("liver", 0.505F, 0.405F, 0.505F, 0.405F, 0, 0xFF5E241C, 0.19F),
      L.at("gut", 0.70F, 0.405F, 0.70F, 0.405F, 0, 0xFF5E5A24, 0.19F),
      L.at("muscle", 0.885F, 0.405F, 0.885F, 0.405F, 0, GuideUi.RED, 0.19F),
      L.lead("hit", 0.18F, 0.81F, 0.27F, 0.91F, 0, GuideUi.INK_BROWN, 0.22F),
      L.lead("flag", 0.585F, 0.68F, 0.46F, 0.50F, 0, GuideUi.INK_BROWN, 0.22F),
      L.lead("bed", 0.85F, 0.76F, 0.86F, 0.91F, 0, GuideUi.INK_BROWN, 0.20F))),
   HARVEST("harvest", 1024, 512, List.of(
      L.lead("knife", 0.31F, 0.61F, 0.20F, 0.90F, 0, GuideUi.INK_BROWN, 0.30F),
      L.at("trophy", 0.83F, 0.605F, 0.83F, 0.605F, 0, GuideUi.INK_BROWN, 0.30F))),
   TIPS("tips", 1024, 512, List.of()),
   WELCOME("welcome", 1152, 384, List.of());

   final ResourceLocation texture;
   final int width;
   final int height;
   final List<L> labels;

   GuideArt(String file, int width, int height, List<L> labels) {
      this.texture = FrontierHunts.id("textures/gui/field_school/" + file + ".png");
      this.width = width;
      this.height = height;
      this.labels = labels;
   }

   String key() {
      return this.name().toLowerCase(java.util.Locale.ROOT);
   }

   /**
    * Smooth (bilinear) sampling: the plates are drawn smaller than their pixels at most GUI scales. Set on every draw
    * (two texture parameters) because a resource reload re-applies the default nearest filter.
    */
   private void filtered() {
      com.formaworks.frontierhunts.artqa.client.GuiArtTexture.bind(this.texture, true); // [artqa] mipmapped: no aliasing when drawn small
   }

   /** Draws the plate at (x, y, w, h) (any aspect is cropped by the caller) plus its labels. */
   void draw(GuiGraphics g, int x, int y, int w, int h, float alpha) {
      this.blit(g, x, y, w, h, 0, 0, this.width, this.height, alpha);
      PlateMarks.field(g, this.key(), x, y, w, h); // [1.1.6] arrows, scent cone, aim ring
      for (L label : this.labels) {
         label.draw(g, this.key(), x, y, w, h);
      }
   }

   /** Draws a sub-rectangle of the plate (texture pixels u, v, uw, vh) into (x, y, w, h). */
   void blit(GuiGraphics g, int x, int y, int w, int h, int u, int v, int uw, int vh, float alpha) {
      this.filtered();
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      g.setColor(1.0F, 1.0F, 1.0F, alpha);
      g.blit(this.texture, x, y, w, h, u, v, uw, vh, this.width, this.height);
      g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
      RenderSystem.disableBlend();
   }

   /**
    * One label: text at (tx, ty) (0..1 of the plate; ty is the top of the text block), aligned -1 left / 0 centre / 1
    * right, wrapped to maxW (fraction of the plate width), and optionally a leader line to the anchor (ax, ay).
    */
   record L(String id, float ax, float ay, float tx, float ty, int align, int color, float maxW, boolean leader) {
      static L at(String id, float ax, float ay, float tx, float ty, int align, int color) {
         return new L(id, ax, ay, tx, ty, align, color, 0.36F, false);
      }

      static L at(String id, float ax, float ay, float tx, float ty, int align, int color, float maxW) {
         return new L(id, ax, ay, tx, ty, align, color, maxW, false);
      }

      static L lead(String id, float ax, float ay, float tx, float ty, int align, int color, float maxW) {
         return new L(id, ax, ay, tx, ty, align, color, maxW, true);
      }

      void draw(GuiGraphics g, String art, int x, int y, int w, int h) {
         String text = GuideUi.tr("guide.frontierhunts.art." + art + "." + this.id);
         FrontierUi.Size size = FrontierUi.Size.SMALL;
         List<FormattedCharSequence> lines = GuideUi.wrap(text, Math.max(40, (int)(this.maxW * w)), size);
         int lh = FrontierUi.lineHeight(size) + 1;
         int blockW = 0;
         for (FormattedCharSequence l : lines) {
            blockW = Math.max(blockW, GuideUi.font().width(l));
         }
         float px = x + this.tx * w;
         float py = y + this.ty * h;
         float left = this.align < 0 ? px : (this.align > 0 ? px - blockW : px - blockW / 2.0F);
         // keep the block on the plate
         left = Math.max(x + 2, Math.min(x + w - blockW - 2, left));
         float top = Math.max(y + 1, Math.min(y + h - lines.size() * lh - 1, py));
         if (this.leader) {
            float axp = x + this.ax * w;
            float ayp = y + this.ay * h;
            float sx = Math.max(left, Math.min(left + blockW, axp));
            float sy = ayp < top ? top - 1 : (ayp > top + lines.size() * lh ? top + lines.size() * lh : top + lh / 2.0F);
            if (axp < left - 2) {
               sx = left - 2;
               sy = top + lh / 2.0F;
            } else if (axp > left + blockW + 2) {
               sx = left + blockW + 2;
               sy = top + lh / 2.0F;
            }
            GuideUi.line(g, sx, sy, axp, ayp, 0.75F, (this.color & 0x00FFFFFF) | 0xC0000000);
            FrontierUi.circle(g, axp, ayp, 1.6F, this.color);
            FrontierUi.circle(g, axp, ayp, 0.8F, GuideUi.PAPER);
         }
         // paper halo for legibility over the drawing
         FrontierUi.rect(g, left - 2, top - 1, blockW + 4, lines.size() * lh + 1, 2.0F, 0x99EAE3D0);
         int yy = (int)top;
         for (FormattedCharSequence l : lines) {
            int lw = GuideUi.font().width(l);
            int lx = (int)(this.align < 0 ? left : (this.align > 0 ? left + blockW - lw : left + (blockW - lw) / 2.0F));
            g.drawString(GuideUi.font(), l, lx, yy, this.color, false);
            yy += lh;
         }
      }
   }
}
