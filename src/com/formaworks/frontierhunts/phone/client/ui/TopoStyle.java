package com.formaworks.frontierhunts.phone.client.ui;

/**
 * [phone] Turns surveyed terrain (a height and a surface colour per block column) into the Maps app's topographic
 * look: softened ground colours, hill shading lit from the north-west, contour lines every 4 blocks (a heavier index
 * line every 20), and water with a darker deep and a lighter shore. Pure maths, shared by the game's map builder and the
 * offline mocks. Rows can be styled a slice at a time.
 */
public final class TopoStyle {
   public static final int WATER = 1;
   public static final int FOREST = 2;
   public static final int SNOW = 3;
   public static final int NONE = 255;

   private TopoStyle() {
   }

   /**
    * Styles rows {@code y0..y1-1} of a {@code size}-square map. {@code height} is the ground (or water surface) height,
    * {@code color} the block's map colour (RGB), {@code kind} one of the constants above (0 = open ground, NONE =
    * unknown/unloaded), {@code depth} the water depth in blocks. Writes ARGB into {@code out}.
    */
   public static void style(int size, int[] height, int[] color, byte[] kind, byte[] depth, int[] out, int y0, int y1) {
      for (int y = y0; y < y1; y++) {
         for (int x = 0; x < size; x++) {
            int i = y * size + x;
            int k = kind[i] & 255;
            if (k == NONE) {
               // unexplored: a faint survey grid on dark
               boolean grid = (x & 15) == 0 || (y & 15) == 0;
               out[i] = grid ? 0xFF242B26 : 0xFF1A1F1B;
               continue;
            }
            int h = height[i];
            int hl = height[y * size + Math.max(0, x - 1)];
            int hr = height[y * size + Math.min(size - 1, x + 1)];
            int hu = height[Math.max(0, y - 1) * size + x];
            int hd = height[Math.min(size - 1, y + 1) * size + x];
            int r, g, b;
            if (k == WATER) {
               int d = depth[i] & 255;
               float t = Math.min(1.0F, d / 9.0F);
               r = (int)(118 - 62 * t);
               g = (int)(172 - 70 * t);
               b = (int)(214 - 56 * t);
               // shore highlight
               boolean shore = (kind[y * size + Math.max(0, x - 1)] & 255) != WATER || (kind[y * size + Math.min(size - 1, x + 1)] & 255) != WATER
                  || (kind[Math.max(0, y - 1) * size + x] & 255) != WATER || (kind[Math.min(size - 1, y + 1) * size + x] & 255) != WATER;
               if (shore) {
                  r = Math.min(255, r + 30);
                  g = Math.min(255, g + 28);
                  b = Math.min(255, b + 20);
               }
               out[i] = 0xFF000000 | r << 16 | g << 8 | b;
               continue;
            }
            int c = color[i];
            r = c >> 16 & 255;
            g = c >> 8 & 255;
            b = c & 255;
            // soften: pull toward a warm paper tone, a little less saturated than Minecraft's map colours
            int lum = (r * 3 + g * 6 + b) / 10;
            r = (r * 6 + lum * 2 + 214 * 2) / 10;
            g = (g * 6 + lum * 2 + 206 * 2) / 10;
            b = (b * 6 + lum * 2 + 178 * 2) / 10;
            if (k == FOREST) {
               r = r * 82 / 100;
               g = g * 92 / 100;
               b = b * 80 / 100;
            } else if (k == SNOW) {
               r = (r + 240) / 2;
               g = (g + 244) / 2;
               b = (b + 248) / 2;
            }
            // hill shading, light from the north-west
            float dx = (hr - hl) * 0.5F;
            float dz = (hd - hu) * 0.5F;
            float nx = -dx, nz = -dz, ny = 1.6F;
            float len = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
            float lit = (nx * -0.55F + ny * 0.62F + nz * -0.55F) / len;
            float k2 = 0.62F + 0.62F * Math.max(0.0F, lit);
            r = Math.min(255, (int)(r * k2));
            g = Math.min(255, (int)(g * k2));
            b = Math.min(255, (int)(b * k2));
            // contour lines where the 4-block band changes toward a neighbour
            int band = Math.floorDiv(h, 4);
            boolean line = Math.floorDiv(hr, 4) != band || Math.floorDiv(hd, 4) != band;
            if (line) {
               int lo = Math.min(Math.min(h, hr), hd);
               int hi = Math.max(Math.max(h, hr), hd);
               boolean index = Math.floorDiv(lo, 20) != Math.floorDiv(hi, 20);
               float a = index ? 0.42F : 0.20F;
               r = (int)(r * (1.0F - a) + 92 * a);
               g = (int)(g * (1.0F - a) + 64 * a);
               b = (int)(b * (1.0F - a) + 38 * a);
            }
            out[i] = 0xFF000000 | r << 16 | g << 8 | b;
         }
      }
   }
}
