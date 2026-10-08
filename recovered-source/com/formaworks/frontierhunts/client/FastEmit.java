package com.formaworks.frontierhunts.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.neoforged.fml.ModList;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

final class FastEmit {
   static final int STRIDE = 9;
   static float[] data = new float[0];
   private static int count;
   private static Boolean fast;

   static void ensure(int verts) {
      if (data.length < verts * 9) {
         data = new float[verts * 9];
      }

      count = verts;
   }

   static void transform(Matrix4f m, Matrix3f n, float[] pos, float[] nrm, int verts) {
      float m00 = m.m00();
      float m01 = m.m01();
      float m02 = m.m02();
      float m10 = m.m10();
      float m11 = m.m11();
      float m12 = m.m12();
      float m20 = m.m20();
      float m21 = m.m21();
      float m22 = m.m22();
      float m30 = m.m30();
      float m31 = m.m31();
      float m32 = m.m32();
      float n00 = n.m00();
      float n01 = n.m01();
      float n02 = n.m02();
      float n10 = n.m10();
      float n11 = n.m11();
      float n12 = n.m12();
      float n20 = n.m20();
      float n21 = n.m21();
      float n22 = n.m22();
      float[] out = data;

      for (int i = 0; i < verts; i++) {
         int k = i * 3;
         int o = i * 9;
         float x = pos[k];
         float y = pos[k + 1];
         float z = pos[k + 2];
         out[o] = m00 * x + m10 * y + m20 * z + m30;
         out[o + 1] = m01 * x + m11 * y + m21 * z + m31;
         out[o + 2] = m02 * x + m12 * y + m22 * z + m32;
         float sx = nrm[k];
         float sy = nrm[k + 1];
         float sz = nrm[k + 2];
         float tx = n00 * sx + n10 * sy + n20 * sz;
         float ty = n01 * sx + n11 * sy + n21 * sz;
         float tz = n02 * sx + n12 * sy + n22 * sz;
         float len = (float)Math.sqrt((double)(tx * tx + ty * ty + tz * tz));
         if (len > 1.0E-6F) {
            float inv = 1.0F / len;
            tx *= inv;
            ty *= inv;
            tz *= inv;
         } else {
            tx = 0.0F;
            ty = 1.0F;
            tz = 0.0F;
         }

         out[o + 5] = tx;
         out[o + 6] = ty;
         out[o + 7] = tz;
      }
   }

   static void uv(int slot, float u, float v) {
      data[slot * 9 + 3] = u;
      data[slot * 9 + 4] = v;
   }

   static void colour(int slot, int argb) {
      data[slot * 9 + 8] = Float.intBitsToFloat(argb);
   }

   static void fillColour(int argb, int verts) {
      float bits = Float.intBitsToFloat(argb);

      for (int i = 0; i < verts; i++) {
         data[i * 9 + 8] = bits;
      }
   }

   static void fillUv(float u, float v, int verts) {
      for (int i = 0; i < verts; i++) {
         data[i * 9 + 3] = u;
         data[i * 9 + 4] = v;
      }
   }

   static int pack(float x, float y, float z) {
      int b = (int)(Math.clamp(z, -1.0F, 1.0F) * 127.0F) & 0xFF;
      int g = (int)(Math.clamp(y, -1.0F, 1.0F) * 127.0F) & 0xFF;
      int r = (int)(Math.clamp(x, -1.0F, 1.0F) * 127.0F) & 0xFF;
      return b << 16 | g << 8 | r;
   }

   static int argb(float r, float g, float b) {
      return 0xFF000000 | (int)(Math.min(1.0F, r) * 255.0F) << 16 | (int)(Math.min(1.0F, g) * 255.0F) << 8 | (int)(Math.min(1.0F, b) * 255.0F);
   }

   private static boolean fastAvailable() {
      if (fast == null) {
         boolean ok = false;

         try {
            ModList mods = ModList.get();
            if (!mods.isLoaded("iris") && (mods.isLoaded("sodium") || mods.isLoaded("embeddium"))) {
               ok = SodiumEmit.available();
            }
         } catch (Throwable var2) {
            ok = false;
         }

         fast = ok;
      }

      return fast;
   }

   static void tris(VertexConsumer out, int[] faces, int light) {
      if (fastAvailable()) {
         try {
            if (SodiumEmit.tris(out, faces, light, data)) {
               return;
            }
         } catch (Throwable var6) {
            fast = false;
            LogUtils.getLogger().warn("Frontier Hunts: bulk vertex writer unavailable; using the ordinary path", var6);
         }
      }

      float[] d = data;

      for (int i = 0; i < faces.length; i++) {
         int o = faces[i] * 9;
         out.addVertex(
            d[o], d[o + 1], d[o + 2], Float.floatToRawIntBits(d[o + 8]), d[o + 3], d[o + 4], OverlayTexture.NO_OVERLAY, light, d[o + 5], d[o + 6], d[o + 7]
         );
      }
   }

   static int vertices() {
      return count;
   }

   private FastEmit() {
   }
}
