package com.formaworks.frontierhunts.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

public final class LosslessFieldTexture {
   public static NativeImage read(ResourceManager var0, ResourceLocation var1) throws IOException {
      Optional var2 = var0.getResource(var1);
      if (var2.isPresent()) {
         NativeImage var4;
         try (InputStream var3 = ((Resource)var2.get()).open()) {
            var4 = NativeImage.read(var3);
         }

         return var4;
      } else {
         throw new IOException("Missing texture resource: " + var1);
      }
   }

   private LosslessFieldTexture() {
   }
}
