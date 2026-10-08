package com.formaworks.frontierhunts.phone.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.phone.client.ui.Canvas;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/**
 * [phone] The phone's {@link Canvas} in the game. Shapes are built straight into the GUI vertex buffer at the
 * window's own pixel grid: rectangles snap to physical pixels and every curved or slanted edge gets a one-pixel
 * anti-aliased fringe, so the phone is crisp at any GUI scale (4K at scale 6 included). Everything is drawn in one
 * managed batch: consecutive shapes, consecutive text and consecutive sprites of one sheet go to the GPU together, and the
 * buffer only flushes when the kind of draw changes (which keeps the painter's order) or the clip changes.
 *
 * <p>No allocation per frame: the transform and clip stacks are arrays, text layouts are cached per string.
 */
public final class GuiCanvas implements Canvas {
   private static final int DEPTH = 40;
   private GuiGraphics g;
   private Matrix4f pose;
   private float px = 1.0F;
   private final float[] tx = new float[DEPTH], ty = new float[DEPTH], ts = new float[DEPTH], ta = new float[DEPTH];
   private int top;
   // clip stack in window pixels (x0, y0, x1, y1, top-left origin)
   private final int[] cx0 = new int[DEPTH], cy0 = new int[DEPTH], cx1 = new int[DEPTH], cy1 = new int[DEPTH];
   private int clips;
   private final ResourceLocation[] fontIds = new ResourceLocation[Font.values().length];
   private final Style[] styles = new Style[Font.values().length];
   @SuppressWarnings("unchecked")
   private final Map<String, Laid>[] laid = new Map[Font.values().length];
   private final Map<Object, RenderType> imageTypes = new HashMap<>();
   private final Map<String, ItemStack> items = new HashMap<>();

   private static final class Laid {
      FormattedCharSequence seq;
      float width;
   }

   public GuiCanvas() {
      String[] files = {"ui_small", "ui", "ui_bold", "phone_medium", "ui_title", "phone_large", "phone_display", "phone_huge"};
      for (Font f : Font.values()) {
         this.fontIds[f.ordinal()] = FrontierHunts.id(files[f.ordinal()]);
         this.styles[f.ordinal()] = Style.EMPTY.withFont(this.fontIds[f.ordinal()]);
         this.laid[f.ordinal()] = new HashMap<>();
      }
   }

   /** Starts a frame on the screen's graphics. */
   public void begin(GuiGraphics g) {
      this.g = g;
      this.pose = g.pose().last().pose();
      this.px = 1.0F / (float)Math.max(1.0, Minecraft.getInstance().getWindow().getGuiScale());
      this.top = 0;
      this.tx[0] = 0.0F;
      this.ty[0] = 0.0F;
      this.ts[0] = 1.0F;
      this.ta[0] = 1.0F;
      this.clips = 0;
   }

   /** Ends the frame: draws what is pending and drops any clip left open. */
   public void end() {
      this.g.flush();
      if (this.clips > 0) {
         this.clips = 0;
         RenderSystem.disableScissor();
      }
   }

   /** Forget cached text layouts (language or resource reload). */
   public void clearCaches() {
      for (Map<String, Laid> m : this.laid) {
         m.clear();
      }
      this.imageTypes.clear();
   }

   // ------------------------------------------------------------------------------------------------ transform

   @Override
   public void push() {
      if (this.top + 1 >= DEPTH) {
         return;
      }
      this.top++;
      this.tx[this.top] = this.tx[this.top - 1];
      this.ty[this.top] = this.ty[this.top - 1];
      this.ts[this.top] = this.ts[this.top - 1];
      this.ta[this.top] = this.ta[this.top - 1];
   }

   @Override
   public void pop() {
      if (this.top > 0) {
         this.top--;
      }
   }

   @Override
   public void translate(float x, float y) {
      this.tx[this.top] += x * this.ts[this.top];
      this.ty[this.top] += y * this.ts[this.top];
   }

   @Override
   public void scale(float s) {
      this.ts[this.top] *= s;
   }

   @Override
   public void alpha(float a) {
      this.ta[this.top] *= a;
   }

   private float X(float x) {
      return this.tx[this.top] + x * this.ts[this.top];
   }

   private float Y(float y) {
      return this.ty[this.top] + y * this.ts[this.top];
   }

   private float snap(float v) {
      return Math.round(v / this.px) * this.px;
   }

   private int col(int argb) {
      float a = this.ta[this.top];
      if (a >= 0.999F) {
         return argb;
      }
      int al = Math.round((argb >>> 24) * Math.max(0.0F, a));
      return al << 24 | argb & 0xFFFFFF;
   }

   private static int fade(int argb) {
      return argb & 0xFFFFFF;
   }

   private static RenderType shapeType;
   private static final RenderStateShard.ShaderStateShard TEX_COLOR = new RenderStateShard.ShaderStateShard(
      net.minecraft.client.renderer.GameRenderer::getPositionTexColorShader);

   private VertexConsumer shapes() {
      if (shapeType == null) {
         // the GUI's own shape type, but double-sided: fans and fringes are wound either way
         shapeType = RenderType.create("frontierhunts_phone_shapes", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 262144, false, false,
            RenderType.CompositeState.builder()
               .setShaderState(RenderStateShard.RENDERTYPE_GUI_SHADER)
               .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
               .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
               .setCullState(RenderStateShard.NO_CULL)
               .createCompositeState(false));
      }
      return this.g.bufferSource().getBuffer(shapeType);
   }

   private void v(VertexConsumer b, float x, float y, int argb) {
      b.addVertex(this.pose, x, y, 0.0F).setColor(argb);
   }

   private void quadRaw(VertexConsumer b, float x0, float y0, int c0, float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3) {
      this.v(b, x0, y0, c0);
      this.v(b, x1, y1, c1);
      this.v(b, x2, y2, c2);
      this.v(b, x3, y3, c3);
   }

   // ------------------------------------------------------------------------------------------------ shapes

   @Override
   public void fill(float x, float y, float w, float h, int argb) {
      int c = this.col(argb);
      if (w <= 0.0F || h <= 0.0F || c >>> 24 == 0) {
         return;
      }
      float x0 = this.snap(this.X(x)), y0 = this.snap(this.Y(y)), x1 = this.snap(this.X(x + w)), y1 = this.snap(this.Y(y + h));
      if (x1 <= x0 || y1 <= y0) {
         return;
      }
      VertexConsumer b = this.shapes();
      this.quadRaw(b, x0, y0, c, x0, y1, c, x1, y1, c, x1, y0, c);
   }

   @Override
   public void gradient(float x, float y, float w, float h, int top, int bottom) {
      int a = this.col(top), z = this.col(bottom);
      if (w <= 0.0F || h <= 0.0F || (a | z) >>> 24 == 0) {
         return;
      }
      float x0 = this.snap(this.X(x)), y0 = this.snap(this.Y(y)), x1 = this.snap(this.X(x + w)), y1 = this.snap(this.Y(y + h));
      VertexConsumer b = this.shapes();
      this.quadRaw(b, x0, y0, a, x0, y1, z, x1, y1, z, x1, y0, a);
   }

   @Override
   public void hgradient(float x, float y, float w, float h, int left, int right) {
      int a = this.col(left), z = this.col(right);
      if (w <= 0.0F || h <= 0.0F || (a | z) >>> 24 == 0) {
         return;
      }
      float x0 = this.snap(this.X(x)), y0 = this.snap(this.Y(y)), x1 = this.snap(this.X(x + w)), y1 = this.snap(this.Y(y + h));
      VertexConsumer b = this.shapes();
      this.quadRaw(b, x0, y0, a, x0, y1, a, x1, y1, z, x1, y0, z);
   }

   @Override
   public void round(float x, float y, float w, float h, float r, int argb) {
      int c = this.col(argb);
      if (w <= 0.0F || h <= 0.0F || c >>> 24 == 0) {
         return;
      }
      float s = this.ts[this.top];
      float x0 = this.X(x), y0 = this.Y(y), x1 = this.X(x + w), y1 = this.Y(y + h);
      float rad = Math.min(r * s, Math.min(x1 - x0, y1 - y0) / 2.0F);
      if (rad < this.px * 0.75F) {
         this.fill(x, y, w, h, argb);
         return;
      }
      VertexConsumer b = this.shapes();
      float half = this.px * 0.5F;
      int clear = fade(c);
      // the body without the corner squares, inset half a pixel so the fringe straddles the true edge
      float ix0 = x0 + rad, ix1 = x1 - rad, iy0 = y0 + rad, iy1 = y1 - rad;
      this.quadRaw(b, ix0, y0 + half, c, ix0, y1 - half, c, ix1, y1 - half, c, ix1, y0 + half, c);
      this.quadRaw(b, x0 + half, iy0, c, x0 + half, iy1, c, ix0, iy1, c, ix0, iy0, c);
      this.quadRaw(b, ix1, iy0, c, ix1, iy1, c, x1 - half, iy1, c, x1 - half, iy0, c);
      // straight edges' fringes
      this.quadRaw(b, ix0, y0 - half, clear, ix0, y0 + half, c, ix1, y0 + half, c, ix1, y0 - half, clear);
      this.quadRaw(b, ix0, y1 - half, c, ix0, y1 + half, clear, ix1, y1 + half, clear, ix1, y1 - half, c);
      this.quadRaw(b, x0 - half, iy0, clear, x0 - half, iy1, clear, x0 + half, iy1, c, x0 + half, iy0, c);
      this.quadRaw(b, x1 - half, iy0, c, x1 - half, iy1, c, x1 + half, iy1, clear, x1 + half, iy0, clear);
      // corners: fans with a fringe
      int seg = Math.max(3, Math.min(16, Math.round(rad / this.px / 2.5F)));
      this.corner(b, ix0, iy0, rad, (float)Math.PI, seg, c, clear, half);
      this.corner(b, ix1, iy0, rad, (float)(Math.PI * 1.5), seg, c, clear, half);
      this.corner(b, ix1, iy1, rad, 0.0F, seg, c, clear, half);
      this.corner(b, ix0, iy1, rad, (float)(Math.PI * 0.5), seg, c, clear, half);
   }

   /** A quarter disc centred at (cx, cy) from angle a0 (screen angles: 0 = +x, PI/2 = +y), radius r. */
   private void corner(VertexConsumer b, float cx, float cy, float r, float a0, int seg, int c, int clear, float half) {
      float step = (float)(Math.PI / 2.0) / seg;
      float ri = r - half, ro = r + half;
      float pc = (float)Math.cos(a0), ps = (float)Math.sin(a0);
      for (int i = 1; i <= seg; i++) {
         float a = a0 + step * i;
         float nc = (float)Math.cos(a), ns = (float)Math.sin(a);
         this.quadRaw(b, cx, cy, c, cx + pc * ri, cy + ps * ri, c, cx + nc * ri, cy + ns * ri, c, cx + nc * ri, cy + ns * ri, c);
         this.quadRaw(b, cx + pc * ri, cy + ps * ri, c, cx + pc * ro, cy + ps * ro, clear, cx + nc * ro, cy + ns * ro, clear, cx + nc * ri, cy + ns * ri, c);
         pc = nc;
         ps = ns;
      }
   }

   @Override
   public void quad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int argb) {
      int c = this.col(argb);
      if (c >>> 24 == 0) {
         return;
      }
      VertexConsumer b = this.shapes();
      float ax = this.X(x0), ay = this.Y(y0), bx = this.X(x1), by = this.Y(y1), qx = this.X(x2), qy = this.Y(y2), dx = this.X(x3), dy = this.Y(y3);
      // QUADS draw (0,1,2) and (2,3,0): wind them the same way so neither half is culled away
      float cross = (bx - ax) * (qy - ay) - (by - ay) * (qx - ax);
      if (cross < 0.0F) {
         this.quadRaw(b, ax, ay, c, dx, dy, c, qx, qy, c, bx, by, c);
      } else {
         this.quadRaw(b, ax, ay, c, bx, by, c, qx, qy, c, dx, dy, c);
      }
      // a soft edge along each side
      this.edge(b, ax, ay, bx, by, c);
      this.edge(b, bx, by, qx, qy, c);
      this.edge(b, qx, qy, dx, dy, c);
      this.edge(b, dx, dy, ax, ay, c);
   }

   /** A one-pixel fringe outward-and-inward across a polygon edge (screen coords). */
   private void edge(VertexConsumer b, float x0, float y0, float x1, float y1, int c) {
      float dx = x1 - x0, dy = y1 - y0;
      float len = (float)Math.sqrt(dx * dx + dy * dy);
      if (len < 1.0E-4F) {
         return;
      }
      // axis-aligned edges of shapes are already crisp
      if (Math.abs(dx) < 1.0E-4F || Math.abs(dy) < 1.0E-4F) {
         return;
      }
      float nx = -dy / len * this.px * 0.5F, ny = dx / len * this.px * 0.5F;
      int clear = fade(c);
      int half = (c >>> 24) / 2 << 24 | c & 0xFFFFFF;
      this.quadRaw(b, x0 - nx, y0 - ny, clear, x0 + nx, y0 + ny, half, x1 + nx, y1 + ny, half, x1 - nx, y1 - ny, clear);
   }

   @Override
   public void line(float x0, float y0, float x1, float y1, float width, int argb) {
      int c = this.col(argb);
      if (c >>> 24 == 0) {
         return;
      }
      float ax = this.X(x0), ay = this.Y(y0), bx = this.X(x1), by = this.Y(y1);
      float dx = bx - ax, dy = by - ay;
      float len = (float)Math.sqrt(dx * dx + dy * dy);
      if (len < 1.0E-4F) {
         return;
      }
      float w = Math.max(this.px, width * this.ts[this.top]);
      float hw = Math.max(0.0F, w / 2.0F - this.px * 0.5F);
      float nx = -dy / len, ny = dx / len;
      float ox = nx * hw, oy = ny * hw;
      float fx = nx * this.px, fy = ny * this.px;
      int clear = fade(c);
      VertexConsumer b = this.shapes();
      this.quadRaw(b, ax + ox, ay + oy, c, ax - ox, ay - oy, c, bx - ox, by - oy, c, bx + ox, by + oy, c);
      this.quadRaw(b, ax + ox, ay + oy, c, bx + ox, by + oy, c, bx + ox + fx, by + oy + fy, clear, ax + ox + fx, ay + oy + fy, clear);
      this.quadRaw(b, ax - ox, ay - oy, c, ax - ox - fx, ay - oy - fy, clear, bx - ox - fx, by - oy - fy, clear, bx - ox, by - oy, c);
   }

   @Override
   public void ring(float cx, float cy, float r, float thickness, int argb) {
      this.arc(cx, cy, r, thickness, 0.0F, (float)(Math.PI * 2.0), argb);
   }

   @Override
   public void arc(float cx, float cy, float r, float thickness, float a0, float a1, int argb) {
      int c = this.col(argb);
      if (c >>> 24 == 0 || r <= 0.0F || a1 == a0) {
         return;
      }
      float s = this.ts[this.top];
      float x = this.X(cx), y = this.Y(cy);
      float ro = r * s, ri = Math.max(0.0F, (r - thickness) * s);
      float span = a1 - a0;
      int seg = Math.max(6, Math.min(160, Math.round(Math.abs(span) * ro / this.px / 3.0F)));
      float step = span / seg;
      float half = this.px * 0.5F;
      int clear = fade(c);
      VertexConsumer b = this.shapes();
      for (int i = 0; i < seg; i++) {
         // our angles: clockwise from 12 o'clock -> screen: x = sin, y = -cos
         float t0 = a0 + step * i, t1 = t0 + step;
         float s0 = (float)Math.sin(t0), c0 = (float)-Math.cos(t0), s1 = (float)Math.sin(t1), c1 = (float)-Math.cos(t1);
         float oi = ro - half, ii = ri + half;
         if (oi > ii) {
            this.quadRaw(b, x + s0 * ii, y + c0 * ii, c, x + s0 * oi, y + c0 * oi, c, x + s1 * oi, y + c1 * oi, c, x + s1 * ii, y + c1 * ii, c);
         }
         this.quadRaw(b, x + s0 * oi, y + c0 * oi, c, x + s0 * (ro + half), y + c0 * (ro + half), clear, x + s1 * (ro + half), y + c1 * (ro + half), clear,
            x + s1 * oi, y + c1 * oi, c);
         if (ri > half) {
            this.quadRaw(b, x + s0 * (ri - half), y + c0 * (ri - half), clear, x + s0 * ii, y + c0 * ii, c, x + s1 * ii, y + c1 * ii, c, x + s1 * (ri - half),
               y + c1 * (ri - half), clear);
         }
      }
   }

   // ------------------------------------------------------------------------------------------------ text

   private Laid laid(String s, Font f) {
      Map<String, Laid> m = this.laid[f.ordinal()];
      Laid l = m.get(s);
      if (l == null) {
         if (m.size() > 3000) {
            m.clear();
         }
         l = new Laid();
         l.seq = Component.literal(s).withStyle(this.styles[f.ordinal()]).getVisualOrderText();
         l.width = Minecraft.getInstance().font.width(l.seq);
         m.put(s, l);
      }
      return l;
   }

   @Override
   public float text(String s, float x, float y, int argb, Font f) {
      if (s == null || s.isEmpty()) {
         return x;
      }
      Laid l = this.laid(s, f);
      int c = this.col(argb);
      // Minecraft draws an alpha below 4 as opaque: skip what is (almost) invisible
      if (c >>> 24 >= 4) {
         PoseStack ps = this.g.pose();
         ps.pushPose();
         ps.translate(this.X(0.0F), this.Y(0.0F), 0.0F);
         float sc = this.ts[this.top];
         ps.scale(sc, sc, 1.0F);
         this.g.drawString(Minecraft.getInstance().font, l.seq, x, y, c, false);
         ps.popPose();
      }
      return x + l.width;
   }

   @Override
   public float width(String s, Font f) {
      return s == null || s.isEmpty() ? 0.0F : this.laid(s, f).width;
   }

   // ------------------------------------------------------------------------------------------------ images

   /** A textured, translucent, full-bright GUI quad type for one texture. */
   private RenderType texType(Object key, ResourceLocation rl, boolean mip) {
      RenderType t = this.imageTypes.get(key);
      if (t == null) {
         if (this.imageTypes.size() > 600) {
            this.imageTypes.clear();
         }
         t = RenderType.create("frontierhunts_phone_tex", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 4096, false, false,
            RenderType.CompositeState.builder()
               .setShaderState(TEX_COLOR)
               .setTextureState(new RenderStateShard.TextureStateShard(rl, true, mip))
               .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
               .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
               .setCullState(RenderStateShard.NO_CULL)
               .createCompositeState(false));
         this.imageTypes.put(key, t);
      }
      return t;
   }

   private void texQuad(RenderType type, float x, float y, float w, float h, float u0, float v0, float u1, float v1, int tint) {
      int c = this.col(tint);
      if (c >>> 24 == 0 || w <= 0.0F || h <= 0.0F) {
         return;
      }
      float x0 = this.X(x), y0 = this.Y(y), x1 = this.X(x + w), y1 = this.Y(y + h);
      VertexConsumer b = this.g.bufferSource().getBuffer(type);
      b.addVertex(this.pose, x0, y0, 0.0F).setUv(u0, v0).setColor(c);
      b.addVertex(this.pose, x0, y1, 0.0F).setUv(u0, v1).setColor(c);
      b.addVertex(this.pose, x1, y1, 0.0F).setUv(u1, v1).setColor(c);
      b.addVertex(this.pose, x1, y0, 0.0F).setUv(u1, v0).setColor(c);
   }

   @Override
   public void sprite(String sheet, float x, float y, float w, float h, float u0, float v0, float u1, float v1, int tint) {
      PhoneTextures.Sheet s = PhoneTextures.sheet(sheet);
      if (s == null) {
         return;
      }
      this.texQuad(this.texType(s, s.location, true), x, y, w, h, u0, v0, u1, v1, tint);
   }

   @Override
   public void image(Object handle, float x, float y, float w, float h, float u0, float v0, float u1, float v1, int tint) {
      if (handle instanceof ResourceLocation rl) {
         this.texQuad(this.texType(rl, rl, false), x, y, w, h, u0, v0, u1, v1, tint);
      }
   }

   @Override
   public void item(String id, float x, float y, float size) {
      ItemStack st = this.items.computeIfAbsent(id, k -> {
         ResourceLocation rl = ResourceLocation.tryParse(k);
         return rl == null ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.get(rl));
      });
      if (st.isEmpty()) {
         return;
      }
      this.g.flush();
      PoseStack ps = this.g.pose();
      ps.pushPose();
      ps.translate(this.X(x), this.Y(y), 0.0F);
      float sc = size * this.ts[this.top] / 16.0F;
      ps.scale(sc, sc, 1.0F);
      this.g.renderItem(st, 0, 0);
      ps.popPose();
   }

   @Override
   public void custom(Custom c, float x, float y, float w, float h) {
      this.g.flush();
      PoseStack ps = this.g.pose();
      ps.pushPose();
      ps.translate(this.X(0.0F), this.Y(0.0F), 0.0F);
      float sc = this.ts[this.top];
      ps.scale(sc, sc, 1.0F);
      c.draw(this.g, x, y, w, h);
      ps.popPose();
      this.g.flush();
   }

   // ------------------------------------------------------------------------------------------------ clipping

   @Override
   public void clip(float x, float y, float w, float h) {
      double gs = Minecraft.getInstance().getWindow().getGuiScale();
      int x0 = (int)Math.floor(this.X(x) * gs), y0 = (int)Math.floor(this.Y(y) * gs);
      int x1 = (int)Math.ceil(this.X(x + w) * gs), y1 = (int)Math.ceil(this.Y(y + h) * gs);
      if (this.clips > 0) {
         int c = this.clips - 1;
         x0 = Math.max(x0, this.cx0[c]);
         y0 = Math.max(y0, this.cy0[c]);
         x1 = Math.min(x1, this.cx1[c]);
         y1 = Math.min(y1, this.cy1[c]);
      }
      if (this.clips >= DEPTH) {
         return;
      }
      this.cx0[this.clips] = x0;
      this.cy0[this.clips] = y0;
      this.cx1[this.clips] = Math.max(x0, x1);
      this.cy1[this.clips] = Math.max(y0, y1);
      this.clips++;
      this.applyClip();
   }

   @Override
   public void unclip() {
      if (this.clips <= 0) {
         return;
      }
      this.clips--;
      this.applyClip();
   }

   private void applyClip() {
      this.g.flush();
      if (this.clips == 0) {
         RenderSystem.disableScissor();
         return;
      }
      int c = this.clips - 1;
      int wh = Minecraft.getInstance().getWindow().getHeight();
      int w = Math.max(0, this.cx1[c] - this.cx0[c]), h = Math.max(0, this.cy1[c] - this.cy0[c]);
      RenderSystem.enableScissor(this.cx0[c], wh - this.cy0[c] - h, w, h);
   }
}
