package com.formaworks.frontierhunts.landscape;

public final class AlpineTile {
   private final AlpineLayout.Sample[] columns = new AlpineLayout.Sample[256];
   private final double[] heights = new double[576];
   private final boolean conservativeSlope;

   public AlpineTile(AlpineLayout var1, int var2, int var3) {
      this.conservativeSlope = var1.version() >= 2;

      for (int var4 = -4; var4 < 20; var4++) {
         for (int var5 = -4; var5 < 20; var5++) {
            boolean var6 = var4 >= 0 && var4 < 16;
            boolean var7 = var5 >= 0 && var5 < 16;
            if (var6 || var7) {
               AlpineLayout.Sample var8 = var1.sample((double)(var2 + var4), (double)(var3 + var5));
               this.heights[index(var4, var5)] = var8.ground();
               if (var6 && var7) {
                  this.columns[var4 * 16 + var5] = var8;
               }
            }
         }
      }
   }

   private static int index(int var0, int var1) {
      return (var0 + 4) * 24 + var1 + 4;
   }

   public AlpineLayout.Sample sample(int var1, int var2) {
      return this.columns[var1 * 16 + var2];
   }

   public double lowestNeighbour(int var1, int var2) {
      return Math.min(
         Math.min(this.heights[index(var1 + 4, var2)], this.heights[index(var1 - 4, var2)]),
         Math.min(this.heights[index(var1, var2 + 4)], this.heights[index(var1, var2 - 4)])
      );
   }

   public double slope(int var1, int var2) {
      if (this.conservativeSlope) {
         double var3 = this.columns[var1 * 16 + var2].ground();
         return Math.max(
               Math.max(Math.abs(this.heights[index(var1 + 4, var2)] - var3), Math.abs(this.heights[index(var1 - 4, var2)] - var3)),
               Math.max(Math.abs(this.heights[index(var1, var2 + 4)] - var3), Math.abs(this.heights[index(var1, var2 - 4)] - var3))
            )
            / 4.0;
      } else {
         return Math.max(
               Math.abs(this.heights[index(var1 + 4, var2)] - this.heights[index(var1 - 4, var2)]),
               Math.abs(this.heights[index(var1, var2 + 4)] - this.heights[index(var1, var2 - 4)])
            )
            / 8.0;
      }
   }
}
