package com.formaworks.frontierhunts.regions.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * [regions] One arrival title card, drawn in the style of the mod's join card (FrontierClient "F R O N T I E R   H U N T S"
 * card): cinematic letterbox bars that grow in with the fade, a letter-spaced cream headline and a gold name, with the same
 * colours, the same 16-tick fade-in and 22-tick fade-out. It never touches the camera or input; the bars are drawn below the
 * vanilla HUD so the hotbar, health and crosshair stay readable while you keep walking.
 *
 * <ul>
 * <li>{@link Style#REGION}: new region (or joining a world): full letterbox, headline tracking opens up, gold hairlines draw out.</li>
 * <li>{@link Style#BIOME}: new biome inside the same region: thinner bars, otherwise identical.</li>
 * <li>{@link Style#COMPACT}: riding fast or flying: no bars, a small two-line card high on the screen, shorter.</li>
 * </ul>
 */
final class ArrivalCard {
   enum Style {
      REGION,
      BIOME,
      COMPACT
   }

   /** Join-card palette (FrontierClient): cream headline, gold name, letterbox black. */
   static final int CREAM = 0xE9E4D5;
   static final int GOLD = 0xD9B878;
   static final int MUTED = 0xB3AA98;
   static final int BAR = 0xD5000000;
   static final float FADE_IN = 16.0F;
   static final float FADE_OUT = 22.0F;
   private static final float ABORT = 8.0F;

   final Style style;
   final String headline;
   final String name;
   final String subtitle;
   final int length;
   private int age;
   private float abortAt = -1.0F;

   ArrivalCard(Style style, String headline, String name, String subtitle, int length) {
      this.style = style;
      this.headline = headline == null ? "" : headline;
      this.name = name == null ? "" : name;
      this.subtitle = subtitle == null ? "" : subtitle;
      this.length = Math.max(40, style == Style.COMPACT ? Math.round(length * 0.75F) : length);
   }

   /** One client tick (not called while the game is paused, like the join card). */
   void tick() {
      this.age++;
   }

   /** Fade out quickly from wherever it is (combat, aiming, a cinematic took over). */
   void abort() {
      if (this.abortAt < 0.0F) {
         this.abortAt = this.age;
      }
   }

   boolean aborting() {
      return this.abortAt >= 0.0F;
   }

   boolean done() {
      return this.age >= this.length || this.abortAt >= 0.0F && this.age - this.abortAt >= ABORT;
   }

   int age() {
      return this.age;
   }

   /** Envelope 0..1 at time {@code t} (ticks, with partial tick). */
   float alpha(float t) {
      float a = smooth(t / FADE_IN) * smooth((this.length - t) / FADE_OUT);
      if (this.abortAt >= 0.0F) {
         a *= 1.0F - clamp((t - this.abortAt) / ABORT);
      }
      return clamp(a);
   }

   void render(GuiGraphics g, float partial, boolean reducedMotion) {
      Minecraft mc = Minecraft.getInstance();
      Font font = mc.font;
      float t = this.age + partial;
      float a = this.alpha(t);
      if (a <= 0.004F) {
         return;
      }
      int w = g.guiWidth();
      int h = g.guiHeight();
      float cx = w / 2.0F;
      float in = reducedMotion ? 1.0F : easeOut(clamp(t / (FADE_IN * 1.6F)));
      if (this.style == Style.COMPACT) {
         float y = Math.max(28.0F, h * 0.14F);
         spaced(g, font, this.headline, cx, y, 3.0F, CREAM, a * 0.9F);
         title(g, font, this.name, cx, y + 12.0F, 1.0F, w, GOLD, a);
         return;
      }
      if (!reducedMotion) {
         float bar = h * (this.style == Style.REGION ? 0.075F : 0.05F) * a;
         letterbox(g, w, h, bar, a);
      }
      float top = h / 3.0F - 4.0F;
      // soft dark band behind the text block so it reads over snow and bright sky (shader packs included)
      int band = (int) (0x38 * a) << 24;
      g.fillGradient(0, (int) top - 26, w, (int) top + 10, 0, band);
      g.fillGradient(0, (int) top + 10, w, (int) top + 58, band, 0);
      float track = 1.5F + 2.5F * in;
      float half = spaced(g, font, this.headline, cx, top, track, CREAM, this.style == Style.BIOME ? a * 0.85F : a);
      hairlines(g, cx, top + 3.0F, half + 9.0F, (reducedMotion ? 34.0F : 34.0F * in), a);
      float rise = reducedMotion ? 0.0F : (1.0F - easeOut(clamp(t / 24.0F))) * 6.0F;
      title(g, font, this.name, cx, top + 14.0F + rise, 2.0F, w, GOLD, a);
      if (!this.subtitle.isEmpty()) {
         float sa = a * clamp((t - 8.0F) / 16.0F);
         line(g, font, this.subtitle, cx, top + 37.0F, w, MUTED, sa);
      }
   }

   // ------------------------------------------------------------------------------------------------ drawing helpers
   /** Letterbox bars of a fractional height (scaled 1-pixel fills, so the slide is smooth at every GUI scale). */
   static void letterbox(GuiGraphics g, int w, int h, float bar, float a) {
      if (bar <= 0.05F) {
         return;
      }
      PoseStack ps = g.pose();
      ps.pushPose();
      ps.scale(1.0F, bar, 1.0F);
      g.fill(0, 0, w, 1, BAR);
      ps.popPose();
      ps.pushPose();
      ps.translate(0.0F, h - bar, 0.0F);
      ps.scale(1.0F, bar, 1.0F);
      g.fill(0, 0, w, 1, BAR);
      ps.popPose();
      // feathered inner edges
      int edge = (int) (0x40 * a) << 24;
      ps.pushPose();
      ps.translate(0.0F, bar, 0.0F);
      g.fillGradient(0, 0, w, 4, edge, 0);
      ps.popPose();
      ps.pushPose();
      ps.translate(0.0F, h - bar - 4.0F, 0.0F);
      g.fillGradient(0, 0, w, 4, 0, edge);
      ps.popPose();
   }

   /** Upper-case letter-spaced line centred on {@code cx}; returns its half width. */
   static float spaced(GuiGraphics g, Font font, String text, float cx, float y, float track, int rgb, float a) {
      int argb = argb(rgb, a);
      String s = text.toUpperCase(Locale.ROOT);
      int[] cps = s.codePoints().toArray();
      float total = 0.0F;
      float[] adv = new float[cps.length];
      for (int i = 0; i < cps.length; i++) {
         String c = new String(Character.toChars(cps[i]));
         adv[i] = cps[i] == ' ' ? font.width(" ") + track * 2.0F : font.width(c) + track;
         total += adv[i];
      }
      if (cps.length > 0) {
         total -= track;
      }
      if (argb == 0) {
         return total / 2.0F;
      }
      float x = cx - total / 2.0F;
      PoseStack ps = g.pose();
      for (int i = 0; i < cps.length; i++) {
         if (cps[i] != ' ') {
            ps.pushPose();
            ps.translate(x, y, 0.0F);
            g.drawString(font, new String(Character.toChars(cps[i])), 0, 0, argb, true);
            ps.popPose();
         }
         x += adv[i];
      }
      return total / 2.0F;
   }

   /** Gold hairlines either side of the headline, fading outward. */
   static void hairlines(GuiGraphics g, float cx, float y, float inner, float len, float a) {
      if (len < 1.0F || a <= 0.01F) {
         return;
      }
      int segs = 10;
      float seg = len / segs;
      PoseStack ps = g.pose();
      for (int side = -1; side <= 1; side += 2) {
         for (int i = 0; i < segs; i++) {
            float fa = a * 0.85F * (1.0F - (float) i / segs);
            int c = argb(GOLD, fa);
            if (c == 0) {
               continue;
            }
            float x0 = side < 0 ? cx - inner - (i + 1) * seg : cx + inner + i * seg;
            ps.pushPose();
            ps.translate(x0, y, 0.0F);
            ps.scale(seg, 1.0F, 1.0F);
            g.fill(0, 0, 1, 1, c);
            ps.popPose();
         }
      }
   }

   /** Centred title at {@code scale}, shrunk to fit the screen. */
   static void title(GuiGraphics g, Font font, String text, float cx, float y, float scale, int screenW, int rgb, float a) {
      int argb = argb(rgb, a);
      if (argb == 0 || text.isEmpty()) {
         return;
      }
      int tw = font.width(text);
      float s = Math.min(scale, (screenW - 24.0F) / Math.max(1, tw));
      PoseStack ps = g.pose();
      ps.pushPose();
      ps.translate(cx, y, 0.0F);
      ps.scale(s, s, 1.0F);
      g.drawString(font, text, -tw / 2, 0, argb, true);
      ps.popPose();
   }

   /** Centred single line trimmed to the screen. */
   static void line(GuiGraphics g, Font font, String text, float cx, float y, int screenW, int rgb, float a) {
      int argb = argb(rgb, a);
      if (argb == 0 || text.isEmpty()) {
         return;
      }
      String s = font.width(text) > screenW - 24 ? font.plainSubstrByWidth(text, screenW - 30) + "…" : text;
      PoseStack ps = g.pose();
      ps.pushPose();
      ps.translate(cx, y, 0.0F);
      g.drawString(font, s, -font.width(s) / 2, 0, argb, true);
      ps.popPose();
   }

   /** ARGB with alpha; 0 when too faint (the font treats alpha below 4 as opaque, so never pass that). */
   static int argb(int rgb, float a) {
      int al = Math.round(clamp(a) * 255.0F);
      return al < 6 ? 0 : al << 24 | rgb & 0xFFFFFF;
   }

   static float clamp(float v) {
      return v < 0.0F ? 0.0F : v > 1.0F ? 1.0F : v;
   }

   static float smooth(float v) {
      v = clamp(v);
      return v * v * (3.0F - 2.0F * v);
   }

   static float easeOut(float v) {
      float u = 1.0F - clamp(v);
      return 1.0F - u * u * u;
   }
}
