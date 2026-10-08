package com.formaworks.frontierhunts.tracking.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MipmapGenerator;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;

/** [tracking] copy of WildlifeTexture (package-private there). Mipmapped, trilinear (+anisotropic) fur texture so the photographic coats don't sparkle at a distance. */
final class HoundTexture extends SimpleTexture {
   private boolean mipReady;
   private float anisotropy = 1.0F;
   private boolean hasAnisotropy;

   static void ensure(ResourceLocation loc) {
      TextureManager tm = Minecraft.getInstance().getTextureManager();
      AbstractTexture current = tm.getTexture(loc, null);
      // a failed load leaves the shared missing texture registered at loc: don't retry (and log) every frame;
      // TextureManager drops those entries on the next resource reload, so a fixed pack is picked up then
      if (!(current instanceof HoundTexture) && current != MissingTextureAtlasSprite.getTexture()) {
         tm.register(loc, new HoundTexture(loc));
      }
   }

   private HoundTexture(ResourceLocation loc) {
      super(loc);
   }

   @Override
   public void load(ResourceManager rm) throws IOException {
      NativeImage img;
      try (InputStream in = rm.open(this.location)) {
         img = NativeImage.read(in);
      }
      int levels = Math.min(4, 31 - Integer.numberOfLeadingZeros(Math.min(img.getWidth(), img.getHeight())));
      NativeImage[] mips;
      try {
         mips = MipmapGenerator.generateMipLevels(new NativeImage[]{img}, levels);
      } catch (RuntimeException e) {
         img.close();
         throw e;
      }
      Runnable upload = () -> {
         try {
            TextureUtil.prepareImage(this.getId(), levels, img.getWidth(), img.getHeight());
            for (int i = 0; i < mips.length; i++) {
               mips[i].upload(i, 0, 0, 0, 0, mips[i].getWidth(), mips[i].getHeight(), true, false, true, false);
            }
            this.mipReady = true;
            this.hasAnisotropy = GL.getCapabilities().GL_EXT_texture_filter_anisotropic;
            if (this.hasAnisotropy) {
               this.anisotropy = Math.min(8.0F, GL11.glGetFloat(34047));
            }
            this.setFilter(true, true);
         } finally {
            for (NativeImage n : mips) {
               n.close();
            }
         }
      };
      if (RenderSystem.isOnRenderThreadOrInit()) {
         upload.run();
      } else {
         RenderSystem.recordRenderCall(upload::run);
      }
   }

   @Override
   public void setFilter(boolean blur, boolean mipmap) {
      super.setFilter(true, this.mipReady);
      if (this.hasAnisotropy) {
         GL11.glTexParameterf(3553, 34046, this.anisotropy);
      }
   }
}
