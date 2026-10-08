import com.formaworks.frontierhunts.phone.client.ui.Canvas;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/**
 * [phone] Offline Java2D implementation of the phone Canvas: renders the real phone UI code to a PNG with the mod's
 * Inter fonts (placed like Minecraft's TTF provider: em size = font size, baseline at y + 7 + shift).
 */
public final class MockCanvas implements Canvas {
   final BufferedImage img;
   final Graphics2D g;
   private final Deque<AffineTransform> tstack = new ArrayDeque<>();
   private final Deque<Float> astack = new ArrayDeque<>();
   private final Deque<Shape> cstack = new ArrayDeque<>();
   private float alpha = 1.0F;
   /** warm-up frames: measure text, draw nothing */
   public boolean dry;
   private final Map<Font, java.awt.Font> fonts = new HashMap<>();
   private final Map<Font, Float> shift = new HashMap<>();
   private final Map<String, BufferedImage> sheets = new HashMap<>();
   private final Map<String, BufferedImage> tinted = new HashMap<>();
   private final String patch;
   private final ZipFile jar;

   public MockCanvas(int w, int h, double guiScale, String patchDir, String jarPath) throws Exception {
      this.img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
      this.g = this.img.createGraphics();
      this.g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      this.g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      this.g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
      this.g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      this.g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      this.g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
      this.g.scale(guiScale, guiScale);
      this.patch = patchDir;
      this.jar = new ZipFile(jarPath);
      font(Font.SMALL, "inter_semibold.ttf", 6.4F, -1.34F);
      font(Font.BODY, "inter_medium.ttf", 8.0F, -0.18F);
      font(Font.STRONG, "inter_semibold.ttf", 8.2F, -0.03F);
      font(Font.MEDIUM, "inter_semibold.ttf", 11.0F, 2.6F);
      font(Font.TITLE, "inter_bold.ttf", 12.0F, 2.74F);
      font(Font.LARGE, "inter_bold.ttf", 17.0F, 6.8F);
      font(Font.DISPLAY, "inter_semibold.ttf", 26.0F, 13.3F);
      font(Font.HUGE, "inter_medium.ttf", 42.0F, 25.0F);
   }

   private void font(Font f, String file, float size, float sh) throws Exception {
      try (InputStream in = this.jar.getInputStream(this.jar.getEntry("assets/frontierhunts/font/" + file))) {
         java.awt.Font base = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, in);
         this.fonts.put(f, base.deriveFont(size));
         this.shift.put(f, sh);
      }
   }

   private BufferedImage bgImage;

   public void background(String path, float dimAlpha) throws Exception {
      if (this.dry) {
         this.tstack.clear();
         this.astack.clear();
         this.cstack.clear();
         this.clips.clear();
         this.alpha = 1.0F;
         this.g.setClip(null);
         return;
      }
      if (this.bgImage == null) {
         this.bgImage = ImageIO.read(new File(path));
      }
      BufferedImage bg = this.bgImage;
      this.g.setClip(null);
      this.tstack.clear();
      this.astack.clear();
      this.cstack.clear();
      this.clips.clear();
      this.alpha = 1.0F;
      AffineTransform t = this.g.getTransform();
      this.g.setTransform(new AffineTransform());
      this.g.drawImage(bg, 0, 0, this.img.getWidth(), this.img.getHeight(), null);
      this.g.setColor(new Color(0, 0, 0, (int)(255 * dimAlpha)));
      this.g.fillRect(0, 0, this.img.getWidth(), this.img.getHeight());
      this.g.setTransform(t);
   }

   private Color col(int argb) {
      int a = (int)((argb >>> 24) * this.alpha);
      return new Color(argb >> 16 & 255, argb >> 8 & 255, argb & 255, Math.max(0, Math.min(255, a)));
   }

   // ------------------------------------------------------------------------------------------------ transform

   @Override
   public void push() {
      this.tstack.push(this.g.getTransform());
      this.astack.push(this.alpha);
      this.cstack.push(this.g.getClip() == null ? new Rectangle2D.Float(-1e6F, -1e6F, 2e6F, 2e6F) : this.g.getClip());
   }

   @Override
   public void pop() {
      this.g.setTransform(this.tstack.pop());
      this.alpha = this.astack.pop();
      Shape c = this.cstack.pop();
      this.g.setClip(c);
   }

   @Override
   public void translate(float x, float y) {
      this.g.translate(x, y);
   }

   @Override
   public void scale(float s) {
      this.g.scale(s, s);
   }

   @Override
   public void alpha(float a) {
      this.alpha *= a;
   }

   // ------------------------------------------------------------------------------------------------ shapes

   @Override
   public void fill(float x, float y, float w, float h, int argb) {
      if (this.dry) {
         return;
      }
      if (w <= 0 || h <= 0) {
         return;
      }
      this.g.setColor(this.col(argb));
      this.g.fill(new Rectangle2D.Float(x, y, w, h));
   }

   @Override
   public void round(float x, float y, float w, float h, float r, int argb) {
      if (this.dry) {
         return;
      }
      if (w <= 0 || h <= 0) {
         return;
      }
      r = Math.min(r, Math.min(w, h) / 2.0F);
      this.g.setColor(this.col(argb));
      this.g.fill(new RoundRectangle2D.Float(x, y, w, h, r * 2.0F, r * 2.0F));
   }

   @Override
   public void gradient(float x, float y, float w, float h, int top, int bottom) {
      if (this.dry) {
         return;
      }
      if (w <= 0 || h <= 0) {
         return;
      }
      this.g.setPaint(new GradientPaint(x, y, this.col(top), x, y + h, this.col(bottom)));
      this.g.fill(new Rectangle2D.Float(x, y, w, h));
   }

   @Override
   public void hgradient(float x, float y, float w, float h, int left, int right) {
      if (this.dry) {
         return;
      }
      if (w <= 0 || h <= 0) {
         return;
      }
      this.g.setPaint(new GradientPaint(x, y, this.col(left), x + w, y, this.col(right)));
      this.g.fill(new Rectangle2D.Float(x, y, w, h));
   }

   @Override
   public void quad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int argb) {
      if (this.dry) {
         return;
      }
      Path2D.Float p = new Path2D.Float();
      p.moveTo(x0, y0);
      p.lineTo(x1, y1);
      p.lineTo(x2, y2);
      p.lineTo(x3, y3);
      p.closePath();
      this.g.setColor(this.col(argb));
      this.g.fill(p);
   }

   @Override
   public void line(float x0, float y0, float x1, float y1, float width, int argb) {
      if (this.dry) {
         return;
      }
      this.g.setColor(this.col(argb));
      this.g.setStroke(new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
      this.g.draw(new Line2D.Float(x0, y0, x1, y1));
   }

   @Override
   public void ring(float cx, float cy, float r, float thickness, int argb) {
      if (this.dry) {
         return;
      }
      this.g.setColor(this.col(argb));
      this.g.setStroke(new BasicStroke(thickness));
      float rr = r - thickness / 2.0F;
      this.g.draw(new Ellipse2D.Float(cx - rr, cy - rr, rr * 2.0F, rr * 2.0F));
   }

   @Override
   public void arc(float cx, float cy, float r, float thickness, float a0, float a1, int argb) {
      if (this.dry) {
         return;
      }
      this.g.setColor(this.col(argb));
      this.g.setStroke(new BasicStroke(thickness, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
      float rr = r - thickness / 2.0F;
      // ours: clockwise from 12 o'clock; Java2D: degrees counter-clockwise from 3 o'clock
      double start = 90.0 - Math.toDegrees(a0);
      double extent = -Math.toDegrees(a1 - a0);
      this.g.draw(new Arc2D.Float(cx - rr, cy - rr, rr * 2.0F, rr * 2.0F, (float)start, (float)extent, Arc2D.OPEN));
   }

   // ------------------------------------------------------------------------------------------------ text

   @Override
   public float text(String s, float x, float y, int argb, Font f) {
      if (this.dry) {
         return x + this.width(s, f);
      }
      if (s == null || s.isEmpty()) {
         return x;
      }
      java.awt.Font jf = this.fonts.get(f);
      this.g.setFont(jf);
      this.g.setColor(this.col(argb));
      this.g.drawString(s, x, y + 7.0F + this.shift.get(f));
      return x + this.width(s, f);
   }

   @Override
   public float width(String s, Font f) {
      if (s == null || s.isEmpty()) {
         return 0.0F;
      }
      java.awt.Font jf = this.fonts.get(f);
      return (float)jf.getStringBounds(s, this.g.getFontRenderContext()).getWidth();
   }

   // ------------------------------------------------------------------------------------------------ images

   private BufferedImage sheet(String name) {
      return this.sheets.computeIfAbsent(name, n -> {
         try {
            File file = new File(this.patch, "assets/frontierhunts/textures/gui/phone/" + n + ".png");
            return file.exists() ? ImageIO.read(file) : null;
         } catch (Exception e) {
            return null;
         }
      });
   }

   private BufferedImage tint(String key, BufferedImage src, int tint) {
      if ((tint | 0xFF000000) == 0xFFFFFFFF) {
         return src;
      }
      return this.tinted.computeIfAbsent(key + "#" + Integer.toHexString(tint), k -> {
         BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
         float tr = (tint >> 16 & 255) / 255.0F, tg = (tint >> 8 & 255) / 255.0F, tb = (tint & 255) / 255.0F;
         for (int yy = 0; yy < src.getHeight(); yy++) {
            for (int xx = 0; xx < src.getWidth(); xx++) {
               int c = src.getRGB(xx, yy);
               int a = c >>> 24;
               int r = (int)((c >> 16 & 255) * tr), gg = (int)((c >> 8 & 255) * tg), b = (int)((c & 255) * tb);
               out.setRGB(xx, yy, a << 24 | r << 16 | gg << 8 | b);
            }
         }
         return out;
      });
   }

   private void drawPart(BufferedImage src, String key, float x, float y, float w, float h, float u0, float v0, float u1, float v1, int tint) {
      float a = (tint >>> 24) / 255.0F * this.alpha;
      if (a <= 0.0F) {
         return;
      }
      BufferedImage t = this.tint(key, src, tint);
      if (u1 < u0) {
         // mirrored horizontally
         AffineTransform at0 = this.g.getTransform();
         this.g.translate(x + w, y);
         this.g.scale(-1, 1);
         this.drawPart(src, key, 0, 0, w, h, u1, v0, u0, v1, tint);
         this.g.setTransform(at0);
         return;
      }
      int sx0 = Math.round(u0 * src.getWidth()), sy0 = Math.round(v0 * src.getHeight());
      int sx1 = Math.round(u1 * src.getWidth()), sy1 = Math.round(v1 * src.getHeight());
      java.awt.Composite old = this.g.getComposite();
      this.g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1.0F, a)));
      AffineTransform at = this.g.getTransform();
      this.g.translate(x, y);
      this.g.scale(w / (float)(sx1 - sx0), h / (float)(sy1 - sy0));
      this.g.drawImage(t, 0, 0, sx1 - sx0, sy1 - sy0, sx0, sy0, sx1, sy1, null);
      this.g.setTransform(at);
      this.g.setComposite(old);
   }

   @Override
   public void sprite(String sheet, float x, float y, float w, float h, float u0, float v0, float u1, float v1, int tint) {
      if (this.dry) {
         return;
      }
      BufferedImage s = this.sheet(sheet);
      if (s == null) {
         this.round(x, y, w, h, Math.min(w, h) * 0.22F, 0xFF7A3A80);
         return;
      }
      this.drawPart(s, sheet, x, y, w, h, u0, v0, u1, v1, tint);
   }

   @Override
   public void image(Object handle, float x, float y, float w, float h, float u0, float v0, float u1, float v1, int tint) {
      if (this.dry) {
         return;
      }
      if (handle instanceof BufferedImage bi) {
         this.drawPart(bi, "img" + System.identityHashCode(bi), x, y, w, h, u0, v0, u1, v1, tint);
      }
   }

   @Override
   public void item(String id, float x, float y, float size) {
      if (this.dry) {
         return;
      }
      String ns = id.contains(":") ? id.substring(0, id.indexOf(':')) : "minecraft";
      String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
      BufferedImage it = this.sheets.computeIfAbsent("item:" + id, k -> {
         try {
            var e = this.jar.getEntry("assets/" + ns + "/textures/item/" + path + ".png");
            if (e == null) {
               File f = new File(this.patch, "assets/" + ns + "/textures/item/" + path + ".png");
               return f.exists() ? ImageIO.read(f) : null;
            }
            try (InputStream in = this.jar.getInputStream(e)) {
               return ImageIO.read(in);
            }
         } catch (Exception ex) {
            return null;
         }
      });
      if (it == null) {
         this.round(x, y, size, size, 2.0F, 0xFF5A5A5A);
         return;
      }
      Object hint = this.g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
      this.g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
      this.drawPart(it, "item:" + id, x, y, size, size, 0.0F, 0.0F, 1.0F, Math.min(1.0F, it.getWidth() / (float)it.getHeight()), 0xFFFFFFFF);
      this.g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
   }

   @Override
   public void custom(Custom c, float x, float y, float w, float h) {
      if (this.dry) {
         return;
      }
      this.fill(x, y, w, h, 0xFF2A2A2A);
   }

   // ------------------------------------------------------------------------------------------------ clip

   private final Deque<Shape> clips = new ArrayDeque<>();

   @Override
   public void clip(float x, float y, float w, float h) {
      this.clips.push(this.g.getClip() == null ? new Rectangle2D.Float(-1e6F, -1e6F, 2e6F, 2e6F) : this.g.getClip());
      this.g.clip(new Rectangle2D.Float(x, y, w, h));
   }

   @Override
   public void unclip() {
      if (!this.clips.isEmpty()) {
         this.g.setClip(this.clips.pop());
      }
   }
}
