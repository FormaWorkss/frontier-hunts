package com.formaworks.frontierhunts.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Arrays;

public final class VertexRecorder implements VertexConsumer {
   private static final int F = 11;
   private static final VertexRecorder SHARED = new VertexRecorder();
   private float[] v = new float[11264];
   private int n = -1;
   private int count;

   public static VertexRecorder shared() {
      return SHARED;
   }

   public void clear() {
      this.count = 0;
      this.n = -1;
   }

   private int base() {
      return this.n * 11;
   }

   public VertexConsumer addVertex(float var1, float var2, float var3) {
      this.n = this.count++;
      if ((this.n + 1) * 11 > this.v.length) {
         this.v = Arrays.copyOf(this.v, this.v.length * 2);
      }

      int var4 = this.base();
      Arrays.fill(this.v, var4, var4 + 11, 0.0F);
      this.v[var4] = var1;
      this.v[var4 + 1] = var2;
      this.v[var4 + 2] = var3;
      this.v[var4 + 3] = Float.intBitsToFloat(-1);
      return this;
   }

   public VertexConsumer setColor(int var1, int var2, int var3, int var4) {
      if (this.n >= 0) {
         this.v[this.base() + 3] = Float.intBitsToFloat((var4 & 0xFF) << 24 | (var1 & 0xFF) << 16 | (var2 & 0xFF) << 8 | var3 & 0xFF);
      }

      return this;
   }

   public VertexConsumer setUv(float var1, float var2) {
      if (this.n >= 0) {
         this.v[this.base() + 4] = var1;
         this.v[this.base() + 5] = var2;
      }

      return this;
   }

   public VertexConsumer setUv1(int var1, int var2) {
      if (this.n >= 0) {
         this.v[this.base() + 6] = Float.intBitsToFloat(var1 & 65535 | var2 << 16);
      }

      return this;
   }

   public VertexConsumer setUv2(int var1, int var2) {
      if (this.n >= 0) {
         this.v[this.base() + 7] = Float.intBitsToFloat(var1 & 65535 | var2 << 16);
      }

      return this;
   }

   public VertexConsumer setNormal(float var1, float var2, float var3) {
      if (this.n >= 0) {
         int var4 = this.base();
         this.v[var4 + 8] = var1;
         this.v[var4 + 9] = var2;
         this.v[var4 + 10] = var3;
      }

      return this;
   }

   public void replay(VertexConsumer var1) {
      for (int var2 = 0; var2 < this.count; var2++) {
         int var3 = var2 * 11;
         var1.addVertex(
            this.v[var3],
            this.v[var3 + 1],
            this.v[var3 + 2],
            Float.floatToRawIntBits(this.v[var3 + 3]),
            this.v[var3 + 4],
            this.v[var3 + 5],
            Float.floatToRawIntBits(this.v[var3 + 6]),
            Float.floatToRawIntBits(this.v[var3 + 7]),
            this.v[var3 + 8],
            this.v[var3 + 9],
            this.v[var3 + 10]
         );
      }

      this.clear();
   }
}
