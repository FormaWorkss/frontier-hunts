package com.formaworks.frontierhunts.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.MipmapGenerator;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;

final class FilteredFieldTexture extends SimpleTexture {
   private float anisotropy = 1.0F;
   private boolean hasAnisotropy;
   private boolean mipReady;

   static void ensure(ResourceLocation var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (var1 != null) {
         TextureManager var2 = var1.getTextureManager();
         if (!(var2.getTexture(var0, null) instanceof FilteredFieldTexture)) {
            var2.register(var0, new FilteredFieldTexture(var0));
         }
      }
   }

   FilteredFieldTexture(ResourceLocation var1) {
      super(var1);
   }

   public void load(ResourceManager var1) throws IOException {
      NativeImage var2 = LosslessFieldTexture.read(var1, this.location);
      if (var2.getWidth() <= 4096 && var2.getHeight() <= 4096) {
         int var3 = Math.min(4, 31 - Integer.numberOfLeadingZeros(Math.min(var2.getWidth(), var2.getHeight())));

         NativeImage[] var4;
         try {
            var4 = MipmapGenerator.generateMipLevels(new NativeImage[]{var2}, var3);
         } catch (RuntimeException var6) {
            var2.close();
            throw var6;
         }

         Runnable var5 = () -> {
            boolean var14 = false /* VF: Semaphore variable */;

            try {
               var14 = true;
               TextureUtil.prepareImage(this.getId(), var3, var2.getWidth(), var2.getHeight());

               for (int var4x = 0; var4x < var4.length; var4x++) {
                  NativeImage var5x = var4[var4x];
                  var5x.upload(var4x, 0, 0, 0, 0, var5x.getWidth(), var5x.getHeight(), true, false, true, false);
               }

               this.mipReady = true;
               this.filterCapabilities();
               this.setFilter(true, true);
               var14 = false;
            } finally {
               if (var14) {
                  for (NativeImage var12 : var4) {
                     var12.close();
                  }
               }
            }

            for (NativeImage var7 : var4) {
               var7.close();
            }
         };
         if (RenderSystem.isOnRenderThreadOrInit()) {
            var5.run();
         } else {
            RenderSystem.recordRenderCall(var5::run);
         }
      } else {
         var2.close();
         throw new IOException("Field texture exceeds 4096px budget");
      }
   }

   public void setFilter(boolean var1, boolean var2) {
      super.setFilter(true, this.mipReady);
      if (this.hasAnisotropy) {
         GL11.glTexParameterf(3553, 34046, this.anisotropy);
      }
   }

   private void filterCapabilities() {
      this.hasAnisotropy = GL.getCapabilities().GL_EXT_texture_filter_anisotropic;
      if (this.hasAnisotropy) {
         this.anisotropy = Math.min(8.0F, GL11.glGetFloat(34047));
      }
   }
}
