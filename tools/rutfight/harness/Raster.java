import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** [rutfight] Tiny orthographic z-buffer rasterizer for harness previews. */
public final class Raster {
   final int w, h;
   final float[] z;
   final BufferedImage img;

   Raster(int w, int h, int bg) {
      this.w = w; this.h = h; this.z = new float[w * h];
      java.util.Arrays.fill(this.z, Float.NEGATIVE_INFINITY);
      this.img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
      for (int i = 0; i < w * h; i++) img.setRGB(i % w, i / w, bg);
   }

   /** sx,sy screen coordinates (pixels), depth larger = closer. */
   void tri(float[] a, float[] b, float[] c, int rgb, float shade) {
      float minx = Math.max(0, (float)Math.floor(Math.min(a[0], Math.min(b[0], c[0]))));
      float maxx = Math.min(w - 1, (float)Math.ceil(Math.max(a[0], Math.max(b[0], c[0]))));
      float miny = Math.max(0, (float)Math.floor(Math.min(a[1], Math.min(b[1], c[1]))));
      float maxy = Math.min(h - 1, (float)Math.ceil(Math.max(a[1], Math.max(b[1], c[1]))));
      float area = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
      if (Math.abs(area) < 1e-9f) return;
      int r = (int)Math.min(255, ((rgb >> 16) & 255) * shade), g = (int)Math.min(255, ((rgb >> 8) & 255) * shade), bl = (int)Math.min(255, (rgb & 255) * shade);
      int col = r << 16 | g << 8 | bl;
      for (int y = (int)miny; y <= maxy; y++) {
         for (int x = (int)minx; x <= maxx; x++) {
            float px = x + 0.5f, py = y + 0.5f;
            float w0 = ((b[0] - px) * (c[1] - py) - (b[1] - py) * (c[0] - px)) / area;
            float w1 = ((c[0] - px) * (a[1] - py) - (c[1] - py) * (a[0] - px)) / area;
            float w2 = 1 - w0 - w1;
            if (w0 < -1e-4f || w1 < -1e-4f || w2 < -1e-4f) continue;
            float d = w0 * a[2] + w1 * b[2] + w2 * c[2];
            int i = y * w + x;
            if (d > z[i]) { z[i] = d; img.setRGB(x, y, col); }
         }
      }
   }

   void dot(float x, float y, int r, int rgb) {
      for (int dy = -r; dy <= r; dy++) for (int dx = -r; dx <= r; dx++) {
         int px = (int)x + dx, py = (int)y + dy;
         if (px >= 0 && py >= 0 && px < w && py < h && dx * dx + dy * dy <= r * r) img.setRGB(px, py, rgb);
      }
   }

   void hline(int y, int rgb) { if (y >= 0 && y < h) for (int x = 0; x < w; x++) img.setRGB(x, y, rgb); }
   void vline(int x, int rgb) { if (x >= 0 && x < w) for (int y = 0; y < h; y++) img.setRGB(x, y, rgb); }

   void text(String s, int x, int y) {
      java.awt.Graphics2D g = img.createGraphics();
      g.setColor(java.awt.Color.BLACK);
      g.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 13));
      g.drawString(s, x, y);
      g.dispose();
   }

   void save(String path) throws Exception { ImageIO.write(img, "png", new File(path)); }
}
