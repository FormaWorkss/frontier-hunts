package com.formaworks.frontierhunts.client.trailcam;

import com.formaworks.frontierhunts.trailcam.TrailcamConfig;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Developed photos on the client: {@code <game dir>/frontierhunts/trailcam/<world or server>/<camera>_<photo>.jpg} plus a
 * small {@code .nbt} with the scene (labels, data strip). Real trail cams write JPEGs; this keeps a full roll to a few MB.
 * The camera position in the file name lets the index be built from a directory listing.
 */
public final class PhotoCache {
   private static final Logger LOG = LoggerFactory.getLogger(PhotoCache.class);
   /** photo id -> camera BlockPos.asLong */
   private static final Map<Long, Long> INDEX = new ConcurrentHashMap<>();
   private static volatile Path root;
   private static volatile boolean scanned;

   private PhotoCache() {
   }

   /** Called when joining a world/server: points the cache at that world's folder and indexes it off-thread. */
   static void open(Minecraft mc) {
      INDEX.clear();
      scanned = false;
      Path r = mc.gameDirectory.toPath().resolve("frontierhunts").resolve("trailcam").resolve(worldKey(mc));
      root = r;
      Util.ioPool().execute(() -> {
         try {
            if (Files.isDirectory(r)) {
               try (DirectoryStream<Path> dir = Files.newDirectoryStream(r, "*.jpg")) {
                  for (Path p : dir) {
                     long[] k = parse(p.getFileName().toString());
                     if (k != null && Files.isRegularFile(sidecar(p))) {
                        INDEX.put(k[1], k[0]);
                     }
                  }
               }
            }
         } catch (Exception e) {
            LOG.debug("Trail camera photo cache unreadable", e);
         }
         scanned = true;
      });
   }

   static void close() {
      root = null;
      INDEX.clear();
   }

   private static String worldKey(Minecraft mc) {
      MinecraftServer sp = mc.getSingleplayerServer();
      String key;
      if (sp != null) {
         Path p = sp.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
         key = "sp-" + (p.getFileName() == null ? "world" : p.getFileName().toString());
      } else {
         ServerData data = mc.getCurrentServer();
         key = "mp-" + (data == null ? "unknown" : data.ip);
      }
      key = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
      return key.length() > 64 ? key.substring(0, 64) : key;
   }

   private static long[] parse(String name) {
      try {
         String base = name.substring(0, name.lastIndexOf('.'));
         int u = base.indexOf('_');
         return new long[]{Long.parseUnsignedLong(base.substring(0, u), 16), Long.parseUnsignedLong(base.substring(u + 1), 16)};
      } catch (RuntimeException e) {
         return null;
      }
   }

   private static Path image(Path r, long camera, long id) {
      return r.resolve(Long.toHexString(camera) + "_" + Long.toHexString(id) + ".jpg");
   }

   private static Path sidecar(Path image) {
      String n = image.getFileName().toString();
      return image.resolveSibling(n.substring(0, n.length() - 4) + ".nbt");
   }

   public static boolean has(long id) {
      return INDEX.containsKey(id);
   }

   static boolean ready() {
      return scanned;
   }

   /** Up to {@code max} cached photo ids for a camera, so the server only sends scenes the client lacks. */
   static long[] have(BlockPos camera, int max) {
      long key = camera.asLong();
      List<Long> ids = new ArrayList<>();
      for (Map.Entry<Long, Long> e : INDEX.entrySet()) {
         if (e.getValue() == key) {
            ids.add(e.getKey());
            if (ids.size() >= max) {
               break;
            }
         }
      }
      long[] out = new long[ids.size()];
      for (int i = 0; i < out.length; i++) {
         out[i] = ids.get(i);
      }
      return out;
   }

   static long[] all(int max) {
      long[] out = new long[Math.min(max, INDEX.size())];
      Iterator<Long> it = INDEX.keySet().iterator();
      for (int i = 0; i < out.length && it.hasNext(); i++) {
         out[i] = it.next();
      }
      return out;
   }

   /** A cached photo read back: pixels (0xAARRGGBB) and its scene sidecar. */
   record Loaded(int[] argb, int w, int h, CompoundTag meta) {
   }

   /** Blocking read; call off-thread. Null when missing or unreadable. */
   static Loaded read(long id) {
      Path r = root;
      Long camera = INDEX.get(id);
      if (r == null || camera == null) {
         return null;
      }
      Path img = image(r, camera, id);
      try {
         BufferedImage b = ImageIO.read(img.toFile());
         if (b == null) {
            return null;
         }
         CompoundTag meta = NbtIo.readCompressed(sidecar(img), NbtAccounter.create(1048576L));
         int w = b.getWidth();
         int h = b.getHeight();
         int[] px = b.getRGB(0, 0, w, h, null, 0, w);
         try {
            Files.setLastModifiedTime(img, FileTime.fromMillis(System.currentTimeMillis()));
         } catch (IOException ignored) {
         }
         return new Loaded(px, w, h, meta);
      } catch (Exception e) {
         LOG.debug("Trail camera photo {} unreadable, it will be developed again", Long.toHexString(id), e);
         INDEX.remove(id);
         return null;
      }
   }

   /** Writes a developed photo (JPEG + scene sidecar) off-thread. */
   static void store(long id, BlockPos camera, int[] argb, int w, int h, CompoundTag meta) {
      Path r = root;
      if (r == null) {
         return;
      }
      long cam = camera.asLong();
      Util.ioPool().execute(() -> {
         Path img = image(r, cam, id);
         Path tmp = img.resolveSibling(img.getFileName() + ".part");
         try {
            Files.createDirectories(r);
            BufferedImage b = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            b.setRGB(0, 0, w, h, argb, 0, w);
            writeJpeg(b, tmp, TrailcamConfig.jpegQuality());
            NbtIo.writeCompressed(meta, sidecar(img));
            Files.move(tmp, img, StandardCopyOption.REPLACE_EXISTING);
            INDEX.put(id, cam);
            prune(r);
         } catch (Exception e) {
            LOG.warn("Trail camera photo could not be cached", e);
            try {
               Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
            }
         }
      });
   }

   private static void writeJpeg(BufferedImage b, Path out, float quality) throws IOException {
      Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
      if (!writers.hasNext()) {
         // no JPEG codec in this runtime: keep a PNG under the same name (the reader sniffs the format)
         if (!ImageIO.write(b, "png", out.toFile())) {
            throw new IOException("No image writer");
         }
         return;
      }
      ImageWriter writer = writers.next();
      try (OutputStream os = Files.newOutputStream(out); ImageOutputStream ios = ImageIO.createImageOutputStream(os)) {
         writer.setOutput(ios);
         ImageWriteParam param = writer.getDefaultWriteParam();
         param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
         param.setCompressionQuality(quality);
         writer.write(null, new IIOImage(b, null, null), param);
      } finally {
         writer.dispose();
      }
   }

   static void delete(long id) {
      Path r = root;
      Long cam = INDEX.remove(id);
      if (r == null || cam == null) {
         return;
      }
      Util.ioPool().execute(() -> {
         Path img = image(r, cam, id);
         try {
            Files.deleteIfExists(img);
            Files.deleteIfExists(sidecar(img));
         } catch (IOException e) {
            LOG.debug("Trail camera photo could not be deleted", e);
         }
      });
   }

   /** Oldest photos go first once the folder passes the configured size. */
   private static void prune(Path r) {
      long limit = TrailcamConfig.cacheLimitBytes();
      List<Path> files = new ArrayList<>();
      long total = 0L;
      try (DirectoryStream<Path> dir = Files.newDirectoryStream(r, "*.jpg")) {
         for (Path p : dir) {
            files.add(p);
            total += Files.size(p) + 2048L;
         }
      } catch (IOException e) {
         return;
      }
      if (total <= limit) {
         return;
      }
      files.sort((a, b) -> {
         try {
            return Files.getLastModifiedTime(a).compareTo(Files.getLastModifiedTime(b));
         } catch (IOException e) {
            return 0;
         }
      });
      for (Path p : files) {
         if (total <= limit * 9 / 10) {
            break;
         }
         try {
            long size = Files.size(p) + 2048L;
            long[] k = parse(p.getFileName().toString());
            Files.deleteIfExists(p);
            Files.deleteIfExists(sidecar(p));
            if (k != null) {
               INDEX.remove(k[1]);
            }
            total -= size;
         } catch (IOException ignored) {
         }
      }
   }
}
