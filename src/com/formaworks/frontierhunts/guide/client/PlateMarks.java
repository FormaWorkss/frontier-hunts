package com.formaworks.frontierhunts.guide.client;

import com.formaworks.frontierhunts.client.FrontierUi;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

/**
 * [1.1.6] Teaching marks drawn by the game on top of the painted Handbook plates: wind arrows, the scent cone, aim
 * rings, and short callouts on the Ranger Academy plates. Drawn in code (not painted into the art) so the text stays
 * sharp at every GUI scale and can be translated ({@code academy.frontierhunts.plate.<plate>.<id>}).
 */
public final class PlateMarks {
   private PlateMarks() {
   }

   static final int WIND = 0xE02E5E8C, WARM = 0xE0C8641E, COOL = 0xE03A6AA8, AIM = 0xFFC0281E, SCENT = 0xFFE08A2A;

   // ============================================================================================ primitives

   /** filled triangles: xs/ys of 3n corners, one colour per corner (ARGB) */
   static void tris(GuiGraphics g, float[] xy, int[] col) {
      g.flush();
      Matrix4f m = g.pose().last().pose();
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
      for (int i = 0; i < col.length; i++) {
         b.addVertex(m, xy[i * 2], xy[i * 2 + 1], 0.0F).setColor(col[i]);
      }
      MeshData mesh = b.build();
      if (mesh != null) {
         BufferUploader.drawWithShader(mesh);
      }
      RenderSystem.disableBlend();
   }

   static void quadLine(GuiGraphics g, float x0, float y0, float x1, float y1, float w, int c0, int c1) {
      float dx = x1 - x0, dy = y1 - y0, l = (float)Math.sqrt(dx * dx + dy * dy);
      if (l < 0.01F) {
         return;
      }
      float nx = -dy / l * w / 2, ny = dx / l * w / 2;
      tris(g, new float[]{x0 + nx, y0 + ny, x0 - nx, y0 - ny, x1 + nx, y1 + ny, x1 + nx, y1 + ny, x0 - nx, y0 - ny, x1 - nx, y1 - ny},
         new int[]{c0, c0, c1, c1, c0, c1});
   }

   /** an arrow from (x0, y0) to (x1, y1), with a light halo so it reads on any painting */
   public static void arrow(GuiGraphics g, float x0, float y0, float x1, float y1, float w, int color) {
      float dx = x1 - x0, dy = y1 - y0, l = (float)Math.sqrt(dx * dx + dy * dy);
      if (l < 1.0F) {
         return;
      }
      float ux = dx / l, uy = dy / l, head = Math.max(4.0F, w * 3.2F);
      float bx = x1 - ux * head, by = y1 - uy * head;
      int halo = 0x90F4ECD8;
      quadLine(g, x0, y0, bx, by, w + 2.0F, halo, halo);
      tris(g, new float[]{x1 + ux * 1.5F, y1 + uy * 1.5F, bx - uy * (head * 0.65F + 1.2F), by + ux * (head * 0.65F + 1.2F), bx + uy * (head * 0.65F + 1.2F),
         by - ux * (head * 0.65F + 1.2F)}, new int[]{halo, halo, halo});
      quadLine(g, x0, y0, bx, by, w, color & 0x00FFFFFF | 0x40000000, color);
      tris(g, new float[]{x1, y1, bx - uy * head * 0.6F, by + ux * head * 0.6F, bx + uy * head * 0.6F, by - ux * head * 0.6F}, new int[]{color, color, color});
   }

   /** a wedge from an apex widening toward (x1, y1): the scent cone, fading out downwind */
   public static void cone(GuiGraphics g, float ax, float ay, float x1, float y1, float half, int color) {
      float dx = x1 - ax, dy = y1 - ay, l = (float)Math.sqrt(dx * dx + dy * dy);
      if (l < 1.0F) {
         return;
      }
      float nx = -dy / l * half, ny = dx / l * half;
      int c0 = color & 0x00FFFFFF | 0x90000000, c1 = color & 0x00FFFFFF;
      tris(g, new float[]{ax, ay, x1 + nx, y1 + ny, x1 - nx, y1 - ny}, new int[]{c0, c1, c1});
   }

   /** an aim ring with a centre dot */
   public static void ring(GuiGraphics g, float cx, float cy, float r, float w, int color) {
      int n = 28;
      for (int i = 0; i < n; i++) {
         double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
         quadLine(g, cx + (float)Math.cos(a0) * r, cy + (float)Math.sin(a0) * r, cx + (float)Math.cos(a1) * r, cy + (float)Math.sin(a1) * r, w + 1.6F,
            0x80F4ECD8, 0x80F4ECD8);
      }
      for (int i = 0; i < n; i++) {
         double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
         quadLine(g, cx + (float)Math.cos(a0) * r, cy + (float)Math.sin(a0) * r, cx + (float)Math.cos(a1) * r, cy + (float)Math.sin(a1) * r, w, color, color);
      }
      FrontierUi.circle(g, cx, cy, Math.max(1.0F, w * 0.8F), color);
   }

   // ============================================================================================ Field School plates

   /** marks under the Field School labels; (x, y, w, h) is where the whole plate is drawn */
   static void field(GuiGraphics g, String art, int x, int y, int w, int h) {
      float s = Math.max(1.0F, w / 380.0F);
      switch (art) {
         case "wind" -> {
            // wind blowing from the doe (upwind, left) past the hunter to the buck (downwind)
            for (float yy : new float[]{0.215F, 0.27F}) {
               arrow(g, x + 0.06F * w, y + yy * h, x + 0.25F * w, y + yy * h, 1.4F * s, WIND);
            }
            cone(g, x + 0.31F * w, y + 0.53F * h, x + 0.57F * w, y + 0.56F * h, 0.09F * h, SCENT);
            // thermals: warm air up the sunny slope, cool air down the evening slope
            arrow(g, x + 0.74F * w, y + 0.40F * h, x + 0.92F * w, y + 0.22F * h, 1.4F * s, WARM);
            arrow(g, x + 0.93F * w, y + 0.66F * h, x + 0.75F * w, y + 0.86F * h, 1.4F * s, COOL);
         }
         case "stalk" -> {
            // wind in the hunter's face: from the deer toward him
            for (float yy : new float[]{0.085F, 0.135F}) {
               arrow(g, x + 0.66F * w, y + yy * h, x + 0.40F * w, y + yy * h, 1.4F * s, WIND);
            }
         }
         case "vitals" -> ring(g, x + 0.292F * w, y + 0.535F * h, 0.04F * h, 1.2F * s, AIM);
         default -> {
         }
      }
   }

   // ============================================================================================ Ranger Academy plates

   /** one callout on an Academy plate: anchor (plate 0..1), text position, optional ring / arrow */
   record Callout(String id, float ax, float ay, float tx, float ty, int kind, float bx, float by) {
      static final int DOT = 0, RING = 1, ARROW = 2;
   }

   static final Map<String, List<Callout>> ACADEMY = Map.of(
      "clean", List.of(new Callout("aim", 0.497F, 0.53F, 0.60F, 0.40F, Callout.RING, 0, 0)),
      "archery", List.of(new Callout("target", 0.585F, 0.52F, 0.585F, 0.70F, Callout.DOT, 0, 0)),
      "glassing", List.of(new Callout("edge", 0.80F, 0.62F, 0.80F, 0.73F, Callout.DOT, 0, 0)),
      "stalk", List.of(new Callout("wind", 0.92F, 0.30F, 0.80F, 0.25F, Callout.ARROW, 0.66F, 0.30F),
         new Callout("head", 0.80F, 0.55F, 0.80F, 0.67F, Callout.DOT, 0, 0)),
      "harvest", List.of(new Callout("shade", 0.25F, 0.42F, 0.25F, 0.66F, Callout.DOT, 0, 0)),
      "range", List.of(new Callout("zero", 0.49F, 0.47F, 0.49F, 0.30F, Callout.DOT, 0, 0)),
      "dressing", List.of(new Callout("tag", 0.33F, 0.66F, 0.24F, 0.76F, Callout.DOT, 0, 0),
         new Callout("bags", 0.77F, 0.33F, 0.77F, 0.48F, Callout.DOT, 0, 0)),
      "track", List.of(new Callout("print", 0.47F, 0.62F, 0.47F, 0.74F, Callout.DOT, 0, 0)),
      "tracking", List.of(new Callout("drops", 0.585F, 0.70F, 0.38F, 0.58F, Callout.DOT, 0, 0),
         new Callout("flag", 0.79F, 0.32F, 0.88F, 0.46F, Callout.DOT, 0, 0))
   );

   /**
    * Callouts over an Academy plate drawn at (x, y, w, h) from the texture window (u, v, uw, vh) of a pw x ph plate.
    * Anchors that fall outside the visible window are skipped.
    */
   public static void academy(GuiGraphics g, String key, int x, int y, int w, int h, int u, int v, int uw, int vh, int pw, int ph, float alpha) {
      List<Callout> list = ACADEMY.get(key);
      if (list == null || alpha < 0.6F || w < 120) {
         return;
      }
      float s = Math.max(1.0F, w / 400.0F);
      for (Callout c : list) {
         float ax = x + (c.ax * pw - u) / uw * w, ay = y + (c.ay * ph - v) / vh * h;
         float tx = x + (c.tx * pw - u) / uw * w, ty = y + (c.ty * ph - v) / vh * h;
         if (ax < x || ax > x + w || ay < y || ay > y + h) {
            continue;
         }
         String text = Component.translatable("academy.frontierhunts.plate." + key + "." + c.id).getString();
         int tw = Math.min(FrontierUi.width(text, FrontierUi.Size.SMALL), (int)(w * 0.42F));
         text = FrontierUi.fit(text, tw, FrontierUi.Size.SMALL);
         int lh = FrontierUi.lineHeight(FrontierUi.Size.SMALL);
         float left = Math.max(x + 3, Math.min(x + w - tw - 7, tx - tw / 2.0F - 2));
         float top = Math.max(y + 2, Math.min(y + h - lh - 5, ty));
         switch (c.kind) {
            case Callout.RING -> ring(g, ax, ay, Math.max(4.0F, h * 0.06F), 1.1F * s, AIM);
            case Callout.ARROW -> arrow(g, ax, ay, x + (c.bx * pw - u) / uw * w, y + (c.by * ph - v) / vh * h, 1.5F * s, WIND);
            default -> {
               FrontierUi.circle(g, ax, ay, 2.2F, 0xE0F4ECD8);
               FrontierUi.circle(g, ax, ay, 1.4F, 0xFF2A1E14);
            }
         }
         if (c.kind != Callout.ARROW) {
            // leader from the label to the anchor
            float lx = Math.max(left, Math.min(left + tw + 4, ax)), ly = ay < top ? top : top + lh + 2;
            quadLine(g, lx, ly, ax, ay, 1.0F, 0xC0F4ECD8, 0xC0F4ECD8);
         }
         FrontierUi.rect(g, left, top, tw + 4, lh + 2, 2.0F, 0xC8241A12);
         FrontierUi.text(g, text, left + 2, top + 1, 0xFFF4ECD8, FrontierUi.Size.SMALL);
      }
   }
}
