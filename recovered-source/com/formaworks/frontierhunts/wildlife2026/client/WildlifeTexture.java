package com.formaworks.frontierhunts.wildlife2026.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.Util;
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

/**
 * Photographic wildlife coat: mipmapped (up to 4 levels) with anisotropic filtering.
 *
 * <p>[perf2] (copied from the dev.62 decompile) The coats are 1024x1024 PNGs: decoding one and building its mip chain
 * took 20-40 ms on the render thread the first time a species came close, a hitch every time a new kind of animal
 * walked up. They are now decoded and mipmapped on a background thread ({@link #ensure} starts it and says whether
 * the texture is ready); the render thread only uploads the finished chain. Resource reloads still load it in place
 * (on the loading screen), as before.
 */
final class WildlifeTexture extends SimpleTexture {
   private boolean mipReady;
   private float anisotropy = 1.0F;
   private boolean hasAnisotropy;
   /** [perf2] A mip chain decoded off the render thread, uploaded by the first {@link #load} (then null). */
   private NativeImage[] preloaded;

   /** [perf2] Background decodes in progress (or finished, not yet registered), by texture. */
   private static final ConcurrentHashMap<ResourceLocation, CompletableFuture<NativeImage[]>> DECODING = new ConcurrentHashMap<>();

   /**
    * Whether the texture can be drawn now. If it is not registered yet, its decode is started (or collected when
    * finished, then uploaded and registered) and false is returned until then: draw something already loaded meanwhile.
    */
   static boolean ensure(ResourceLocation location) {
      TextureManager manager = Minecraft.getInstance().getTextureManager();
      AbstractTexture current = manager.getTexture(location, null);
      // a failed load leaves the shared missing texture registered at the location: don't retry (and log) every frame;
      // TextureManager drops those entries on the next resource reload, so a fixed pack is picked up then
      if (current instanceof WildlifeTexture || current == MissingTextureAtlasSprite.getTexture()) {
         return true;
      }
      CompletableFuture<NativeImage[]> decoding = DECODING.computeIfAbsent(location, WildlifeTexture::decodeAsync);
      if (!decoding.isDone()) {
         return false;
      }
      DECODING.remove(location, decoding);
      NativeImage[] mips;
      try {
         mips = decoding.join();
      } catch (RuntimeException e) {
         mips = null; // the decode failed: load() tries again in place and reports it like before
      }
      WildlifeTexture texture = new WildlifeTexture(location);
      texture.preloaded = mips;
      long started = System.nanoTime();
      manager.register(location, texture);
      com.formaworks.frontierhunts.perf.client.PerfStats.since(com.formaworks.frontierhunts.perf.client.PerfStats.TEXTURE_UPLOAD_US, started);
      return true;
   }

   /** [perf2] The old synchronous path, for the small textures (the distant coats): registered and loaded right now. */
   static void ensureNow(ResourceLocation location) {
      TextureManager manager = Minecraft.getInstance().getTextureManager();
      AbstractTexture current = manager.getTexture(location, null);
      if (!(current instanceof WildlifeTexture) && current != MissingTextureAtlasSprite.getTexture()) {
         manager.register(location, new WildlifeTexture(location));
      }
   }

   private static CompletableFuture<NativeImage[]> decodeAsync(ResourceLocation location) {
      ResourceManager resources = Minecraft.getInstance().getResourceManager();
      return CompletableFuture.supplyAsync(() -> {
         try {
            NativeImage[] mips = decode(resources, location);
            com.formaworks.frontierhunts.perf.client.PerfStats.inc(com.formaworks.frontierhunts.perf.client.PerfStats.TEXTURES_DECODED);
            return mips;
         } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
         }
      }, Util.backgroundExecutor());
   }

   /** Reads the PNG and builds its mip chain (up to 4 levels). Any thread. */
   private static NativeImage[] decode(ResourceManager resources, ResourceLocation location) throws IOException {
      NativeImage image;
      try (InputStream in = resources.open(location)) {
         image = NativeImage.read(in);
      }
      int levels = Math.min(4, 31 - Integer.numberOfLeadingZeros(Math.min(image.getWidth(), image.getHeight())));
      try {
         return MipmapGenerator.generateMipLevels(new NativeImage[]{image}, levels);
      } catch (RuntimeException e) {
         image.close();
         throw e;
      }
   }

   private WildlifeTexture(ResourceLocation location) {
      super(location);
   }

   @Override
   public void load(ResourceManager resources) throws IOException {
      NativeImage[] mips = this.preloaded;
      this.preloaded = null;
      if (mips == null) {
         mips = decode(resources, this.location);
      }
      NativeImage[] chain = mips;
      int levels = chain.length - 1;
      Runnable upload = () -> {
         try {
            TextureUtil.prepareImage(this.getId(), levels, chain[0].getWidth(), chain[0].getHeight());
            for (int level = 0; level < chain.length; level++) {
               chain[level].upload(level, 0, 0, 0, 0, chain[level].getWidth(), chain[level].getHeight(), true, false, true, false);
            }
            this.mipReady = true;
            this.hasAnisotropy = GL.getCapabilities().GL_EXT_texture_filter_anisotropic;
            if (this.hasAnisotropy) {
               this.anisotropy = Math.min(8.0F, GL11.glGetFloat(34047));
            }
            this.setFilter(true, true);
         } finally {
            for (NativeImage image : chain) image.close();
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
