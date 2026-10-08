package com.formaworks.frontierhunts.artqa.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * [artqa] GUI art (Field School / dossier plates, icon atlases, preset cards) drawn much smaller than its pixels.
 * Vanilla {@code SimpleTexture}s have no mip levels, so bilinear minification below ~1/2 skips texels: fine ink lines
 * and small icons alias (jaggies, sparkle) at GUI scale 1-2 and in small windows. This texture uploads a full,
 * alpha-weighted box-filtered mip chain and samples trilinear, so the art stays clean at every scale. It replaces the
 * plain texture under the same {@link ResourceLocation}; a resource reload re-runs {@link #load} (vanilla reset ->
 * register). Client only.
 */
public final class GuiArtTexture extends AbstractTexture {
   private static final int MAX_LEVELS = 5;
   private static final Set<ResourceLocation> FAILED = new HashSet<>();
   private final ResourceLocation location;

   private GuiArtTexture(ResourceLocation location) {
      this.location = location;
   }

   /**
    * Makes sure {@code loc} is a mipmapped GUI texture and sets its sampling: smooth = trilinear (bilinear between
    * mip levels), otherwise nearest (crisp pixel art at whole-number scales). Returns false when the texture could not
    * be loaded (it is then left to vanilla, e.g. the missing-texture checkerboard). Render thread only.
    */
   public static boolean bind(ResourceLocation loc, boolean smooth) {
      TextureManager tm = Minecraft.getInstance().getTextureManager();
      AbstractTexture t = tm.getTexture(loc, null);
      if (!(t instanceof GuiArtTexture)) {
         if (FAILED.contains(loc)) {
            return false;
         }
         try {
            tm.register(loc, new GuiArtTexture(loc));
         } catch (RuntimeException ex) {
            FAILED.add(loc);
            return false;
         }
         t = tm.getTexture(loc, null);
         if (!(t instanceof GuiArtTexture)) {   // load failed -> vanilla put the missing texture there
            FAILED.add(loc);
            return false;
         }
      }
      GuiArtTexture g = (GuiArtTexture)t;
      if (g.blur != smooth || g.mipmap != (smooth && g.levels > 1)) {
         g.setFilter(smooth, smooth && g.levels > 1);
      }
      return true;
   }

   private int levels = 1;

   @Override
   public void load(ResourceManager rm) throws IOException {
      NativeImage base;
      try (InputStream in = rm.open(this.location)) {
         // force RGBA: several plates / preset cards are RGB PNGs and getPixelRGBA only works on RGBA images
         base = NativeImage.read(NativeImage.Format.RGBA, in);
      }
      NativeImage[] chain;
      try {
         chain = mips(base);
      } catch (RuntimeException ex) {   // never let a bad image take the client down: vanilla falls back to missing
         base.close();
         throw new IOException("mip chain for " + this.location, ex);
      }
      if (!RenderSystem.isOnRenderThreadOrInit()) {
         RenderSystem.recordRenderCall(() -> this.upload(chain));
      } else {
         this.upload(chain);
      }
   }

   private void upload(NativeImage[] chain) {
      this.levels = chain.length;
      TextureUtil.prepareImage(this.getId(), chain.length - 1, chain[0].getWidth(), chain[0].getHeight());
      for (int i = 0; i < chain.length; i++) {
         // blur on, clamp on, mipmap filter when there is a chain, auto-close the level image
         chain[i].upload(i, 0, 0, 0, 0, chain[i].getWidth(), chain[i].getHeight(), true, true, chain.length > 1, true);
      }
      this.blur = true;
      this.mipmap = chain.length > 1;
      if (chain.length > 1) {
         // slight negative LOD bias: GUI art stays crisp (one mip level sharper) without the aliasing of no mips at all
         this.bind();
         com.mojang.blaze3d.platform.GlStateManager._texParameter(3553, 34049, -0.5F); // GL_TEXTURE_2D, GL_TEXTURE_LOD_BIAS
      }
   }

   /** Level 0 = the image; each next level halves both sides (stops at odd sizes / 8 px). */
   static NativeImage[] mips(NativeImage base) {
      int n = 1;
      int w = base.getWidth(), h = base.getHeight();
      while (n < MAX_LEVELS && w % 2 == 0 && h % 2 == 0 && w / 2 >= 8 && h / 2 >= 8) {
         w /= 2;
         h /= 2;
         n++;
      }
      NativeImage[] out = new NativeImage[n];
      out[0] = base;
      for (int l = 1; l < n; l++) {
         NativeImage src = out[l - 1];
         NativeImage dst = new NativeImage(src.getWidth() / 2, src.getHeight() / 2, false);
         for (int y = 0; y < dst.getHeight(); y++) {
            for (int x = 0; x < dst.getWidth(); x++) {
               dst.setPixelRGBA(x, y, average(src.getPixelRGBA(2 * x, 2 * y), src.getPixelRGBA(2 * x + 1, 2 * y),
                  src.getPixelRGBA(2 * x, 2 * y + 1), src.getPixelRGBA(2 * x + 1, 2 * y + 1)));
            }
         }
         out[l] = dst;
      }
      return out;
   }

   /** Alpha-weighted average of four packed pixels (ABGR, as NativeImage stores them): no dark fringes. */
   private static int average(int a, int b, int c, int d) {
      int[] px = {a, b, c, d};
      int sa = 0;
      long s0 = 0, s1 = 0, s2 = 0;
      for (int p : px) {
         int al = p >>> 24;
         sa += al;
         s0 += (long)(p & 255) * al;
         s1 += (long)(p >> 8 & 255) * al;
         s2 += (long)(p >> 16 & 255) * al;
      }
      if (sa == 0) {
         // fully transparent: keep the plain colour average so bilinear edges stay neutral
         int r0 = 0, r1 = 0, r2 = 0;
         for (int p : px) {
            r0 += p & 255;
            r1 += p >> 8 & 255;
            r2 += p >> 16 & 255;
         }
         return (r2 / 4) << 16 | (r1 / 4) << 8 | (r0 / 4);
      }
      int c0 = (int)(s0 / sa), c1 = (int)(s1 / sa), c2 = (int)(s2 / sa);
      int alpha = (sa + 2) / 4;
      return alpha << 24 | c2 << 16 | c1 << 8 | c0;
   }
}
