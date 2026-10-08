package com.formaworks.frontierhunts.phone.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * [phone] The phone's art sheets ({@code textures/gui/phone/*.png}: app icons, glyphs, weather icons, chess pieces, the
 * Flush! birds). They are drawn much smaller than painted (a 96 px glyph at 9 GUI pixels), so each sheet is uploaded
 * with its own mip chain (premultiplied box filter) and always sampled with trilinear filtering: icons stay smooth and
 * clean at every GUI scale instead of shimmering.
 */
public final class PhoneTextures {
   private static final Map<String, Sheet> SHEETS = new HashMap<>();

   public static final class Sheet {
      public final ResourceLocation location;

      Sheet(ResourceLocation location) {
         this.location = location;
      }
   }

   private PhoneTextures() {
   }

   public static Sheet sheet(String name) {
      Sheet s = SHEETS.get(name);
      if (s == null) {
         ResourceLocation rl = FrontierHunts.id("textures/gui/phone/" + name + ".png");
         Minecraft.getInstance().getTextureManager().register(rl, new MipTexture(rl));
         s = new Sheet(rl);
         SHEETS.put(name, s);
      }
      return s;
   }

   /** A PNG with a premultiplied mip chain, always filtered trilinearly. */
   static final class MipTexture extends AbstractTexture {
      private static final int LEVELS = 4;
      private final ResourceLocation location;

      MipTexture(ResourceLocation location) {
         this.location = location;
      }

      @Override
      public void load(ResourceManager rm) throws IOException {
         Resource res = rm.getResourceOrThrow(this.location);
         NativeImage base;
         try (InputStream in = res.open()) {
            base = NativeImage.read(in);
         }
         NativeImage[] levels = new NativeImage[LEVELS + 1];
         levels[0] = base;
         int n = 0;
         for (int i = 1; i <= LEVELS; i++) {
            NativeImage prev = levels[i - 1];
            if (prev.getWidth() < 2 || prev.getHeight() < 2) {
               break;
            }
            levels[i] = half(prev);
            n = i;
         }
         final int count = n;
         if (!RenderSystem.isOnRenderThreadOrInit()) {
            RenderSystem.recordRenderCall(() -> this.upload(levels, count));
         } else {
            this.upload(levels, count);
         }
      }

      private void upload(NativeImage[] levels, int count) {
         TextureUtil.prepareImage(this.getId(), count, levels[0].getWidth(), levels[0].getHeight());
         for (int i = 0; i <= count; i++) {
            levels[i].upload(i, 0, 0, 0, 0, levels[i].getWidth(), levels[i].getHeight(), true, true, count > 0, true);
         }
         super.setFilter(true, count > 0);
      }

      @Override
      public void setFilter(boolean blur, boolean mipmap) {
         // a render type asks for nearest sampling of every texture it binds: these sheets keep their smooth mips
         super.setFilter(true, true);
      }

      /** Half size, averaging 2x2 texels with premultiplied alpha (no dark fringes round the icons). */
      private static NativeImage half(NativeImage src) {
         int w = Math.max(1, src.getWidth() / 2), h = Math.max(1, src.getHeight() / 2);
         NativeImage out = new NativeImage(w, h, false);
         for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
               long a = 0L, c0 = 0L, c1 = 0L, c2 = 0L;
               for (int dy = 0; dy < 2; dy++) {
                  for (int dx = 0; dx < 2; dx++) {
                     int p = src.getPixelRGBA(Math.min(src.getWidth() - 1, x * 2 + dx), Math.min(src.getHeight() - 1, y * 2 + dy));
                     int al = p >>> 24;
                     a += al;
                     c0 += (long)(p & 255) * al;
                     c1 += (long)(p >> 8 & 255) * al;
                     c2 += (long)(p >> 16 & 255) * al;
                  }
               }
               int oa = (int)(a / 4L);
               int r0 = a == 0L ? 0 : (int)Math.min(255L, c0 / a);
               int r1 = a == 0L ? 0 : (int)Math.min(255L, c1 / a);
               int r2 = a == 0L ? 0 : (int)Math.min(255L, c2 / a);
               out.setPixelRGBA(x, y, oa << 24 | r2 << 16 | r1 << 8 | r0);
            }
         }
         return out;
      }
   }
}
