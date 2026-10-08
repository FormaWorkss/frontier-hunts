package com.formaworks.frontierhunts.hunting;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;

public final class HuntResources {
   public static byte[] read(String var0) throws IOException {
      try (InputStream var1 = HuntResources.class.getResourceAsStream(var0)) {
         if (var1 != null) {
            return var1.readAllBytes();
         }
      }

      String var12 = var0.startsWith("/") ? var0.substring(1) : var0;

      try {
         ModList var2 = ModList.get();
         if (var2 != null) {
            for (IModFileInfo var4 : var2.getModFiles()) {
               boolean var5 = false;

               for (IModInfo var7 : var4.getMods()) {
                  if (var7.getModId().startsWith("frontierhunts")) {
                     var5 = true;
                     break;
                  }
               }

               if (var5) {
                  Path var14 = var4.getFile().findResource(var12.split("/"));
                  if (var14 != null && Files.isRegularFile(var14)) {
                     return Files.readAllBytes(var14);
                  }
               }
            }
         }
      } catch (IOException var9) {
         throw var9;
      } catch (Throwable var10) {
      }

      throw new IOException("Missing " + var0);
   }

   private HuntResources() {
   }
}
