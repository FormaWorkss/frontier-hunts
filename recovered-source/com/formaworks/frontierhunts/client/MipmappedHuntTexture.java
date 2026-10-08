package com.formaworks.frontierhunts.client;

import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

final class MipmappedHuntTexture extends SimpleTexture {
   private boolean ready;

   MipmappedHuntTexture(ResourceLocation var1) {
      super(var1);
   }

   public void load(ResourceManager var1) throws IOException {
      this.ready = false;
      super.load(var1);
      if (RenderSystem.isOnRenderThreadOrInit()) {
         this.mipmaps();
      } else {
         RenderSystem.recordRenderCall(this::mipmaps);
      }
   }

   private void mipmaps() {
      this.bind();
      int var1 = Math.max(GL11.glGetTexLevelParameteri(3553, 0, 4096), GL11.glGetTexLevelParameteri(3553, 0, 4097));
      int var2 = var1 > 0 ? 31 - Integer.numberOfLeadingZeros(var1) : 0;
      GL11.glTexParameteri(3553, 33085, var2);
      GL30.glGenerateMipmap(3553);
      this.ready = true;
      this.setFilter(true, true);
   }

   public void setFilter(boolean var1, boolean var2) {
      super.setFilter(true, this.ready);
   }
}
