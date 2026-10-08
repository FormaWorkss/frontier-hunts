package com.formaworks.frontierhunts.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.client.renderer.texture.MipmapGenerator;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;

final class FilteredRifleTexture extends SimpleTexture {
   private float anisotropy = 1.0F;
   private boolean hasAnisotropy;

   FilteredRifleTexture(ResourceLocation var1) {
      super(var1);
   }

   public void load(ResourceManager var1) throws IOException {
      NativeImage var2;
      try (InputStream var3 = var1.getResourceOrThrow(this.location).open()) {
         var2 = NativeImage.read(var3);
      }

      if (var2.getWidth() <= 4096 && var2.getHeight() <= 4096) {
         int var9 = 31 - Integer.numberOfLeadingZeros(Math.min(var2.getWidth(), var2.getHeight()));

         NativeImage[] var4;
         try {
            var4 = MipmapGenerator.generateMipLevels(new NativeImage[]{var2}, var9);
         } catch (RuntimeException var7) {
            var2.close();
            throw var7;
         }

         Runnable var5 = () -> {
            boolean var14 = false /* VF: Semaphore variable */;

            try {
               var14 = true;
               TextureUtil.prepareImage(this.getId(), var9, var2.getWidth(), var2.getHeight());

               for (int var4x = 0; var4x < var4.length; var4x++) {
                  NativeImage var5x = var4[var4x];
                  var5x.upload(var4x, 0, 0, 0, 0, var5x.getWidth(), var5x.getHeight(), true, false, true, false);
               }

               this.hasAnisotropy = GL.getCapabilities().GL_EXT_texture_filter_anisotropic;
               if (this.hasAnisotropy) {
                  this.anisotropy = Math.min(8.0F, GL11.glGetFloat(34047));
               }

               this.setFilter(true, true);
               var14 = false;
            } finally {
               if (var14) {
                  for (NativeImage var12 : var4) {
                     var12.close();
                  }
               }
            }

            for (NativeImage var7x : var4) {
               var7x.close();
            }
         };
         if (RenderSystem.isOnRenderThreadOrInit()) {
            var5.run();
         } else {
            RenderSystem.recordRenderCall(var5::run);
         }
      } else {
         var2.close();
         throw new IOException("Rifle texture exceeds 4096px budget");
      }
   }

   public void setFilter(boolean var1, boolean var2) {
      super.setFilter(true, true);
      if (this.hasAnisotropy) {
         GL11.glTexParameterf(3553, 34046, this.anisotropy);
      }
   }
}
