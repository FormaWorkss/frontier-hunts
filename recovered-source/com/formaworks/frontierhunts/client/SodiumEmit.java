package com.formaworks.frontierhunts.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.api.vertex.format.common.EntityVertex;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.lwjgl.system.MemoryStack;

final class SodiumEmit {
   private static final int CHUNK = 720;

   static boolean available() {
      return EntityVertex.FORMAT != null;
   }

   static boolean tris(VertexConsumer var0, int[] var1, int var2, float[] var3) {
      VertexBufferWriter var4 = VertexBufferWriter.tryOf(var0);
      if (var4 == null) {
         return false;
      } else {
         byte var5 = 36;
         boolean var6 = false;

         try {
            for (short var7 = 0; var7 < var1.length; var7 += 720) {
               int var8 = Math.min(720, var1.length - var7);
               MemoryStack var9 = MemoryStack.stackPush();

               try {
                  long var10 = var9.nmalloc(4, var8 * var5);
                  long var12 = var10;

                  for (int var14 = 0; var14 < var8; var14++) {
                     int var15 = var1[var7 + var14] * 9;
                     int var16 = Float.floatToRawIntBits(var3[var15 + 8]);
                     int var17 = var16 & -16711936 | var16 >> 16 & 0xFF | (var16 & 0xFF) << 16;
                     EntityVertex.write(
                        var12,
                        var3[var15],
                        var3[var15 + 1],
                        var3[var15 + 2],
                        var17,
                        var3[var15 + 3],
                        var3[var15 + 4],
                        OverlayTexture.NO_OVERLAY,
                        var2,
                        FastEmit.pack(var3[var15 + 5], var3[var15 + 6], var3[var15 + 7])
                     );
                     var12 += (long)var5;
                  }

                  var4.push(var9, var10, var8, EntityVertex.FORMAT);
                  var6 = true;
               } catch (Throwable var19) {
                  if (var9 != null) {
                     try {
                        var9.close();
                     } catch (Throwable var18) {
                        var19.addSuppressed(var18);
                     }
                  }

                  throw var19;
               }

               if (var9 != null) {
                  var9.close();
               }
            }
         } catch (LinkageError | RuntimeException var20) {
            if (!var6) {
               throw var20;
            }

            LogUtils.getLogger().warn("Frontier Hunts: bulk vertex push failed part way through", var20);
         }

         return true;
      }
   }

   private SodiumEmit() {
   }
}
