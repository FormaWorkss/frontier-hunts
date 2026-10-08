package com.formaworks.frontierhunts.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Field camera photo files. Photos live on the client in {@code <game dir>/screenshots/frontier-photo-yyyyMMdd-HHmmss-SSS.png}
 * (the same place and name every earlier build used), so nothing ever needs migrating. Writes go to a temporary
 * {@code .part} file that is renamed into place only once complete, so the gallery never sees a half-written PNG.
 * Existing photo files are never deleted or overwritten.
 */
public final class FieldPhotoStore {
   static final String PREFIX = "frontier-photo-";
   private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS", Locale.ROOT);
   private static final Logger LOG = LoggerFactory.getLogger(FieldPhotoStore.class);
   /**
    * [bugs] Files that could not be decoded, keyed by normalized path, valued by a size/mtime signature. A damaged file is
    * logged once, then skipped by the gallery listing and never decoded again until it changes on disk.
    */
   private static final Map<Path, Long> BAD = new ConcurrentHashMap<>();
   private static final Set<Path> WARNED = ConcurrentHashMap.newKeySet();
   /** Final paths of photos whose file is still being written. */
   private static final Set<Path> PENDING = ConcurrentHashMap.newKeySet();
   public static final int MAX_PENDING = 3;

   public record Photo(Path path, long time, boolean pending) {
      String name() {
         return this.path.getFileName().toString();
      }
   }

   public record Listing(List<Photo> photos, boolean folderError) {
   }

   /** {@code requested} is the name shown while developing; {@code path} is where the file really ended up. */
   public record Saved(Path requested, Path path, boolean ok, String error) {
   }

   public static final class Decoded implements AutoCloseable {
      public NativeImage image;
      boolean partial;
      boolean scaled;
      public int fileWidth;
      public int fileHeight;
      public String error = "";

      @Override
      public void close() {
         if (this.image != null) {
            this.image.close();
            this.image = null;
         }
      }
   }

   public static Path folder(Minecraft mc) {
      return mc.gameDirectory.toPath().resolve("screenshots");
   }

   public static int pending() {
      return PENDING.size();
   }

   static boolean isPhotoName(String name) {
      String lower = name.toLowerCase(Locale.ROOT);
      return lower.startsWith(PREFIX) && (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg"));
   }

   /** Every field photo on disk, newest first. Looks in screenshots/ and one level of sub-folders (in case photos were sorted by hand). */
   public static Listing list(Minecraft mc) {
      return list(folder(mc));
   }

   static Listing list(Path root) {
      // Snapshot the in-flight saves before scanning: a save that finishes mid-scan is then either found on disk
      // or still listed as pending, never lost between the two.
      List<Path> pending = new ArrayList<>(PENDING);
      Map<Path, Photo> found = new LinkedHashMap<>();
      boolean error = false;
      if (Files.isDirectory(root)) {
         List<Path> subfolders = new ArrayList<>();
         try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path p : stream) {
               if (Files.isDirectory(p)) {
                  subfolders.add(p);
               } else {
                  add(found, p);
               }
            }
         } catch (Exception e) {
            error = true;
            LOG.warn("Cannot list field photographs in {}", root, e);
         }

         for (Path sub : subfolders) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(sub)) {
               for (Path p : stream) {
                  add(found, p);
               }
            } catch (Exception e) {
               LOG.debug("Skipping unreadable folder {}", sub, e);
            }
         }
      }

      for (Path p : pending) {
         found.putIfAbsent(key(p), new Photo(p, time(p), true));
      }

      List<Photo> photos = new ArrayList<>(found.values());
      photos.sort(Comparator.comparingLong(Photo::time).thenComparingInt((Photo p) -> p.name().length()).thenComparing(Photo::name).reversed());
      return new Listing(photos, error && photos.isEmpty());
   }

   private static void add(Map<Path, Photo> found, Path p) {
      if (isPhotoName(p.getFileName().toString()) && Files.isRegularFile(p) && !knownBad(p)) {
         found.putIfAbsent(key(p), new Photo(p, time(p), false));
      }
   }

   /** Size/mtime signature: a file that is replaced or repaired on disk gets a fresh chance. -1 when unreadable. */
   private static long signature(Path p) {
      try {
         return Files.size(p) * 31L + Files.getLastModifiedTime(p).toMillis();
      } catch (Exception e) {
         return -1L;
      }
   }

   /** True for a file that already failed to decode (and has not changed since), or that is too small to be an image. */
   static boolean knownBad(Path p) {
      Long sig = BAD.get(key(p));
      if (sig != null) {
         if (sig == signature(p)) {
            return true;
         }
         BAD.remove(key(p));
      }
      try {
         if (Files.size(p) < 8L) {
            markBad(p, "empty or truncated file");
            return true;
         }
      } catch (Exception ignored) {
      }
      return false;
   }

   private static void markBad(Path p, String why) {
      if (BAD.put(key(p), signature(p)) == null) {
         LOG.warn("Skipping damaged field photograph {} ({}); the file was left untouched", p, why);
      }
   }

   static final int UNKNOWN = 0, PNG = 1, JPEG = 2, OTHER_IMAGE = 3;

   /** Detects the real format from the first bytes, whatever the extension says. */
   static int sniff(byte[] h, int n) {
      if (n >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G' && h[4] == 0x0D && h[5] == 0x0A && h[6] == 0x1A && h[7] == 0x0A) {
         return PNG;
      }
      if (n >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) {
         return JPEG;
      }
      if (n >= 4 && (h[0] == 'G' && h[1] == 'I' && h[2] == 'F' || h[0] == 'B' && h[1] == 'M' && n >= 8)) {
         return OTHER_IMAGE;
      }
      return UNKNOWN;
   }

   private static int sniff(Path p) throws IOException {
      byte[] h = new byte[12];
      int n = 0;
      try (InputStream in = Files.newInputStream(p)) {
         while (n < h.length) {
            int r = in.read(h, n, h.length - n);
            if (r < 0) {
               break;
            }
            n += r;
         }
      }
      return sniff(h, n);
   }

   private static Path key(Path p) {
      return p.toAbsolutePath().normalize();
   }

   private static long time(Path p) {
      String name = p.getFileName().toString();
      if (name.length() >= PREFIX.length() + 19) {
         try {
            return LocalDateTime.parse(name.substring(PREFIX.length(), PREFIX.length() + 19), STAMP).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
         } catch (Exception ignored) {
         }
      }

      try {
         return Files.getLastModifiedTime(p).toMillis();
      } catch (Exception e) {
         return 0L;
      }
   }

   /** Render-thread safe: unique among in-flight saves, no disk access. The IO thread re-checks the disk. */
   private static Path pendingPath(Path root) {
      String base = PREFIX + STAMP.format(LocalDateTime.now());
      Path p = root.resolve(base + ".png");
      for (int i = 2; PENDING.contains(p); i++) {
         p = root.resolve(base + "-" + i + ".png");
      }

      return p;
   }

   /** IO thread only. Picks a name that no existing or pending file uses. Never returns a path that already exists. */
   private static Path freshPath(Path root) {
      String base = PREFIX + STAMP.format(LocalDateTime.now());
      Path p = root.resolve(base + ".png");
      for (int i = 2; Files.exists(p) || Files.exists(part(p)) || PENDING.contains(p); i++) {
         p = root.resolve(base + "-" + i + ".png");
      }

      return p;
   }

   private static Path part(Path p) {
      return p.resolveSibling(p.getFileName().toString() + ".part");
   }

   /** Takes ownership of {@code image}: applies the filter and saves it off-thread. {@code done} runs on the client thread. */
   public static Path save(Minecraft mc, NativeImage image, int filter, Consumer<Saved> done) {
      Path root = folder(mc);
      Path target = pendingPath(root);
      PENDING.add(target);
      Util.ioPool().execute(() -> {
         Saved result;
         Path temp = part(target);
         try (NativeImage img = image) {
            applyFilter(img, filter);
            Files.createDirectories(root);
            Path finalPath = target;
            if (Files.exists(finalPath) || Files.exists(temp)) {
               finalPath = freshPath(root);
               temp = part(finalPath);
            }

            img.writeToFile(temp);
            if (Files.exists(finalPath)) {
               finalPath = freshPath(root);
            }

            try {
               Files.move(temp, finalPath, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
               Files.move(temp, finalPath);
            }

            result = new Saved(target, finalPath, true, "");
         } catch (Throwable e) {
            LOG.error("Cannot save field photograph {}", target, e);
            try {
               Files.deleteIfExists(temp);
            } catch (Exception ignored) {
            }

            result = new Saved(target, target, false, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
         } finally {
            PENDING.remove(target);
         }

         Saved finished = result;
         mc.execute(() -> done.accept(finished));
      });
      return target;
   }

   /** [1.4.0] the filters' names, in order (the phone camera shows and cycles them) */
   public static final String[] FILTERS = {"Natural", "B&W", "Warm print", "Vivid", "Trail cam", "Vintage", "Golden hour"};

   /**
    * 0 natural, 1 monochrome, 2 warm print, [1.4.0] 3 vivid, 4 trail cam (infrared grey-green, grain, dark corners), 5 vintage
    * (faded, warm, soft corners), 6 golden hour (warm light, lifted shadows). NativeImage pixels are ABGR.
    */
   public static void applyFilter(NativeImage img, int filter) {
      if (filter <= 0 || filter >= FILTERS.length) {
         return;
      }

      int w = img.getWidth(), h = img.getHeight();
      double cx = w / 2.0, cy = h / 2.0, rr = cx * cx + cy * cy;
      long seed = 0x9E3779B97F4A7C15L;
      for (int y = 0; y < h; y++) {
         for (int x = 0; x < w; x++) {
            int c = img.getPixelRGBA(x, y);
            int r = c & 0xFF;
            int g = c >>> 8 & 0xFF;
            int b = c >>> 16 & 0xFF;
            int lum = (int)(0.2126 * r + 0.7152 * g + 0.0722 * b);
            double nr, ng, nb;
            switch (filter) {
               case 1 -> {
                  nr = lum;
                  ng = lum;
                  nb = lum;
               }
               case 2 -> {
                  nr = lum * 1.08 + 10.0;
                  ng = lum * 0.97 + 5.0;
                  nb = lum * 0.78;
               }
               case 3 -> {
                  double sat = 1.38, con = 1.12;
                  nr = ((lum + (r - lum) * sat) - 128.0) * con + 128.0;
                  ng = ((lum + (g - lum) * sat) - 128.0) * con + 128.0;
                  nb = ((lum + (b - lum) * sat) - 128.0) * con + 128.0;
               }
               case 4 -> {
                  // the reserve's trail cameras at night: flat grey-green, grain, darker corners
                  seed = seed * 6364136223846793005L + 1442695040888963407L;
                  double grain = ((seed >>> 40) & 255) / 255.0 * 34.0 - 17.0;
                  double l = Math.pow(lum / 255.0, 0.8) * 235.0 + grain;
                  nr = l * 0.86;
                  ng = l * 0.98;
                  nb = l * 0.84;
               }
               case 5 -> {
                  nr = 40.0 + (lum * 0.35 + r * 0.65) * 0.78;
                  ng = 34.0 + (lum * 0.35 + g * 0.65) * 0.72;
                  nb = 30.0 + (lum * 0.35 + b * 0.65) * 0.58;
               }
               default -> {
                  nr = r * 1.10 + 14.0;
                  ng = g * 1.0 + 6.0;
                  nb = b * 0.82;
               }
            }
            if (filter >= 4) {
               double dx = x - cx, dy = y - cy;
               double v = 1.0 - (filter == 4 ? 0.55 : 0.38) * Math.pow((dx * dx + dy * dy) / rr, 1.4);
               nr *= v;
               ng *= v;
               nb *= v;
            }
            int ir = Math.max(0, Math.min(255, (int)nr)), ig = Math.max(0, Math.min(255, (int)ng)), ib = Math.max(0, Math.min(255, (int)nb));
            img.setPixelRGBA(x, y, 0xFF000000 | ib << 16 | ig << 8 | ir);
         }
      }
   }

   /**
    * [1.4.0] A small JPEG of a photo for sharing in a text: at most {@code maxW} x {@code maxH}, quality stepped down until
    * it fits {@code maxBytes}. Off the client thread only. Null when the photo can't be read.
    */
   public static byte[] shareJpeg(Path path, int maxW, int maxH, int maxBytes) {
      try {
         BufferedImage src = ImageIO.read(path.toFile());
         if (src == null) {
            return null;
         }
         double s = Math.min(1.0, Math.min((double)maxW / src.getWidth(), (double)maxH / src.getHeight()));
         int w = Math.max(1, (int)Math.round(src.getWidth() * s)), h = Math.max(1, (int)Math.round(src.getHeight() * s));
         BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
         java.awt.Graphics2D g = dst.createGraphics();
         g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
         g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
         // two steps down from a large screenshot keep it sharp without aliasing
         if (src.getWidth() > w * 2) {
            int mw = w * 2, mh = h * 2;
            BufferedImage mid = new BufferedImage(mw, mh, BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D gm = mid.createGraphics();
            gm.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            gm.drawImage(src, 0, 0, mw, mh, null);
            gm.dispose();
            src = mid;
         }
         g.drawImage(src, 0, 0, w, h, null);
         g.dispose();
         javax.imageio.ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
         try {
            for (float q : new float[]{0.86F, 0.78F, 0.68F, 0.56F, 0.44F}) {
               java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
               try (javax.imageio.stream.ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
                  writer.setOutput(ios);
                  javax.imageio.ImageWriteParam p = writer.getDefaultWriteParam();
                  p.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                  p.setCompressionQuality(q);
                  writer.write(null, new javax.imageio.IIOImage(dst, null, null), p);
               }
               if (out.size() <= maxBytes) {
                  return out.toByteArray();
               }
            }
         } finally {
            writer.dispose();
         }
         return null;
      } catch (Throwable e) {
         LOG.debug("Cannot make a shareable copy of {}", path, e);
         return null;
      }
   }

   /** True when the frame read back is (almost) pure black, which usually means the framebuffer was not ready. */
   public static boolean looksBlank(NativeImage img) {
      int w = img.getWidth();
      int h = img.getHeight();
      for (int y = 0; y < 12; y++) {
         for (int x = 0; x < 12; x++) {
            int c = img.getPixelRGBA((x * 2 + 1) * w / 24, (y * 2 + 1) * h / 24);
            if ((c & 0xFF) > 6 || (c >>> 8 & 0xFF) > 6 || (c >>> 16 & 0xFF) > 6) {
               return false;
            }
         }
      }

      return true;
   }

   /**
    * Reads a photo for display. Safe off-thread. Never modifies the file. The format comes from the file's magic bytes
    * (PNG via stb with an ImageIO fallback for damaged rows, JPEG/GIF/BMP via ImageIO); a file that cannot be read is
    * logged once and remembered, so it is never retried or re-logged while it stays unchanged.
    */
   public static Decoded decode(Path path, int maxSize) {
      Decoded d = new Decoded();
      if (knownBad(path)) {
         d.error = "damaged file";
         return d;
      }
      int format;
      try {
         format = sniff(path);
      } catch (Throwable e) {
         // unreadable right now (locked, being synced): not remembered as damaged, the next visit tries again
         d.error = String.valueOf(e.getMessage());
         LOG.debug("Cannot read field photograph {}", path, e);
         return d;
      }
      if (format == UNKNOWN) {
         d.error = "not an image file";
         markBad(path, "not an image file");
         return d;
      }

      Throwable first = null;
      if (format == PNG) {
         try (InputStream in = Files.newInputStream(path)) {
            d.image = NativeImage.read(NativeImage.Format.RGBA, in);
         } catch (Throwable e) {
            first = e;
         }
      }

      if (d.image == null) {
         try {
            AwtFallback.read(path, d);
         } catch (Throwable e) {
            if (first != null) {
               e.addSuppressed(first);
            }

            first = e;
         }
      }

      if (d.image == null) {
         d.error = first == null ? "unknown format" : String.valueOf(first.getMessage());
         if (first == null || first instanceof IOException) {
            markBad(path, d.error); // the file itself is unreadable: remembered, logged once
            LOG.debug("Field photograph decode failure for {}", path, first);
         } else if (WARNED.add(key(path))) {
            // not the file's fault (out of memory, native library trouble): log once, try again on a later visit
            LOG.warn("Cannot open field photograph {}", path, first);
         }
         return d;
      }

      d.fileWidth = d.image.getWidth();
      d.fileHeight = d.image.getHeight();
      int max = Math.max(256, maxSize);
      if (d.fileWidth > max || d.fileHeight > max) {
         double s = Math.min((double)max / d.fileWidth, (double)max / d.fileHeight);
         int nw = Math.max(1, (int)(d.fileWidth * s));
         int nh = Math.max(1, (int)(d.fileHeight * s));
         NativeImage small = new NativeImage(nw, nh, false);
         d.image.resizeSubRectTo(0, 0, d.fileWidth, d.fileHeight, small);
         d.image.close();
         d.image = small;
         d.scaled = true;
      }

      return d;
   }

   /** Kept in its own class so the AWT/ImageIO types are only loaded if this fallback is ever needed. */
   private static final class AwtFallback {
      /** Fallback for files stb cannot read (JPEG, unusual PNG variants, or a damaged file: recovers what rows it can). */
      static void read(Path path, Decoded d) throws IOException {
         try (ImageInputStream in = new FileImageInputStream(path.toFile())) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
               throw new IOException("Not an image file");
            }

            ImageReader reader = readers.next();
            try {
               reader.setInput(in, true, true);
               int w = reader.getWidth(0);
               int h = reader.getHeight(0);
               if (w <= 0 || h <= 0 || (long)w * h > 16384L * 16384L) {
                  throw new IOException("Bad image size " + w + "x" + h);
               }

               Iterator<ImageTypeSpecifier> types = reader.getImageTypes(0);
               BufferedImage dest = types.hasNext() ? types.next().createBufferedImage(w, h) : new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
               ImageReadParam param = reader.getDefaultReadParam();
               param.setDestination(dest);
               try {
                  reader.read(0, param);
               } catch (IOException | RuntimeException e) {
                  d.partial = true;
               }

               NativeImage img = new NativeImage(w, h, false);
               for (int y = 0; y < h; y++) {
                  for (int x = 0; x < w; x++) {
                     int argb = dest.getRGB(x, y);
                     img.setPixelRGBA(x, y, 0xFF000000 | (argb & 0xFF) << 16 | (argb & 0xFF00) | (argb >>> 16 & 0xFF));
                  }
               }

               d.image = img;
            } finally {
               reader.dispose();
            }
         }
      }
   }

   private FieldPhotoStore() {
   }
}
