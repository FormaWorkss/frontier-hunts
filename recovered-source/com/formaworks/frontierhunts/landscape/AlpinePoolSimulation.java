package com.formaworks.frontierhunts.landscape;

public final class AlpinePoolSimulation {
   public static final int N = 41;
   public static final double CELL = 0.5;
   public static final double DT = 0.025;
   public final float[] depth = new float[1681];
   public final float[] height = new float[1681];
   public final float[] previous = new float[1681];
   public final float[] foam = new float[1681];
   private final float[] velocity = new float[1681];
   private final float[] nextHeight = new float[1681];
   private final float[] nextVelocity = new float[1681];
   private final float[] nextFoam = new float[1681];
   public double time;

   public void tick(double var1) {
      System.arraycopy(this.height, 0, this.previous, 0, this.height.length);
      this.step(var1);
      this.step(var1);
   }

   private float neighbor(int var1, int var2, float var3) {
      return var1 >= 0 && var2 >= 0 && var1 < 41 && var2 < 41 && !(this.depth[var2 * 41 + var1] <= 0.0F) ? this.height[var2 * 41 + var1] : var3;
   }

   private void step(double var1) {
      this.time += 0.025;

      for (int var3 = 0; var3 < 41; var3++) {
         for (int var4 = 0; var4 < 41; var4++) {
            int var5 = var3 * 41 + var4;
            if (this.depth[var5] <= 0.0F) {
               this.nextHeight[var5] = this.nextVelocity[var5] = this.nextFoam[var5] = 0.0F;
            } else {
               double var6 = (double)this.height[var5];
               double var8 = (
                     (double)(
                           this.neighbor(var4 - 1, var3, (float)var6)
                              + this.neighbor(var4 + 1, var3, (float)var6)
                              + this.neighbor(var4, var3 - 1, (float)var6)
                              + this.neighbor(var4, var3 + 1, (float)var6)
                        )
                        - 4.0 * var6
                  )
                  / 0.25;
               double var10 = (double)(var4 - 20) * 0.5;
               double var12 = (double)(var3 - 20) * 0.5;
               double var14 = var10 * var10 + var12 * var12;
               double var16 = (Math.exp(-var14 / 1.4) - 0.25 * Math.exp(-var14 / 5.6))
                  * (Math.sin(this.time * 7.1) + 0.55 * Math.sin(this.time * 11.7))
                  * var1
                  * 18.0;
               double var18 = (double)Math.min(Math.min(var4, var3), Math.min(40 - var4, 40 - var3));
               double var20 = 1.1 + Math.max(0.0, 4.0 - var18) * 0.9;
               double var22 = ((double)this.velocity[var5] + 0.025 * (9.81 * (double)Math.min(4.0F, this.depth[var5]) * var8 + var16))
                  * Math.exp(-var20 * 0.025);
               this.nextVelocity[var5] = (float)Math.clamp(var22, -3.0, 3.0);
               this.nextHeight[var5] = (float)Math.clamp(var6 + 0.025 * var22, -0.42, 0.65);
               double var24 = var1 * 2.2 * Math.exp(-var14 / 40.0) / Math.sqrt(var14 + 0.5);
               double var26 = (double)var4 - var10 * var24 * 0.025 / 0.5;
               double var28 = (double)var3 - var12 * var24 * 0.025 / 0.5;
               int var30 = Math.clamp((long)((int)Math.round(var26)), 0, 40);
               int var31 = Math.clamp((long)((int)Math.round(var28)), 0, 40);
               float var32 = this.depth[var31 * 41 + var30] > 0.0F ? this.sampleFoam(var26, var28) : this.foam[var5];
               double var33 = var1 * Math.exp(-var14 / 10.0) * 2.0 + Math.max(0.0, Math.abs(var22) - 0.2) * 0.25;
               this.nextFoam[var5] = (float)Math.clamp((double)var32 * Math.exp(-0.00825) + var33 * 0.025, 0.0, 1.0);
            }
         }
      }

      System.arraycopy(this.nextHeight, 0, this.height, 0, this.height.length);
      System.arraycopy(this.nextVelocity, 0, this.velocity, 0, this.height.length);
      System.arraycopy(this.nextFoam, 0, this.foam, 0, this.height.length);
   }

   private float sampleFoam(double var1, double var3) {
      var1 = Math.clamp(var1, 0.0, 39.999);
      var3 = Math.clamp(var3, 0.0, 39.999);
      int var5 = (int)var1;
      int var6 = (int)var3;
      double var7 = var1 - (double)var5;
      double var9 = var3 - (double)var6;
      return (float)(
         ((double)this.foam[var6 * 41 + var5] * (1.0 - var7) + (double)this.foam[var6 * 41 + var5 + 1] * var7) * (1.0 - var9)
            + ((double)this.foam[(var6 + 1) * 41 + var5] * (1.0 - var7) + (double)this.foam[(var6 + 1) * 41 + var5 + 1] * var7) * var9
      );
   }
}
