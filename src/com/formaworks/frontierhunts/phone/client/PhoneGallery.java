package com.formaworks.frontierhunts.phone.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.client.FieldPhotoStore;
import com.formaworks.frontierhunts.phone.client.ui.PhoneModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.LoggerFactory;

/**
 * [1.4.0] The Camera app's photos: the phone camera's (and the retired Field Camera's) files in the game's
 * screenshots folder, newest first, with thumbnails and a viewing-size copy made off the game thread one at a time and
 * kept as textures while they're used (at most {@link #MAX_THUMBS} thumbnails and {@link #MAX_FULL} large ones; the
 * oldest go first). Nothing is decoded until the app shows it.
 */
public final class PhoneGallery {
   private static final int THUMB = 320;
   private static final int FULL = 1280;
   private static final int MAX_THUMBS = 48;
   private static final int MAX_FULL = 3;
   /** one worker: a big screenshot takes a moment to read, and the game should never feel it */
   static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "Frontier phone photos");
      t.setDaemon(true);
      t.setPriority(Thread.MIN_PRIORITY);
      return t;
   });
   private static final Map<String, Tex> THUMBS = new LinkedHashMap<>(16, 0.75F, true);
   private static final Map<String, Tex> FULLS = new LinkedHashMap<>(4, 0.75F, true);
   private static final Set<String> ASKED = new HashSet<>();
   private static final Deque<Runnable> QUEUE = new ArrayDeque<>();
   private static final Map<String, Path> PATHS = new HashMap<>();
   private static boolean busy;
   private static boolean listing;
   private static int counter;
   private static int generation;

   /** A photo in GPU memory. */
   static final class Tex {
      final ResourceLocation id;
      final DynamicTexture tex;
      final PhoneModel.Pic pic;

      Tex(ResourceLocation id, DynamicTexture tex, int w, int h) {
         this.id = id;
         this.tex = tex;
         this.pic = new PhoneModel.Pic(id, w, h);
      }

      void release() {
         Minecraft.getInstance().getTextureManager().release(this.id);
      }
   }

   private PhoneGallery() {
   }

   static void install(PhoneModel m) {
      m.photos.thumb = s -> pic(s.key(), false);
      m.photos.full = s -> pic(s.key(), true);
   }

   /** The thumbnail of a photo file (asked for when missing), for the camera's own overlay. */
   static PhoneModel.Pic thumbOf(Path p) {
      String key = p.toAbsolutePath().normalize().toString();
      PATHS.putIfAbsent(key, p);
      return pic(key, false);
   }

   // ------------------------------------------------------------------------------------------------ the list

   /** Lists the photos again (off the game thread). */
   static void refresh() {
      if (listing) {
         return;
      }
      listing = true;
      Minecraft mc = Minecraft.getInstance();
      int gen = generation;
      Util.ioPool().execute(() -> {
         FieldPhotoStore.Listing l;
         try {
            l = FieldPhotoStore.list(mc);
         } catch (Throwable e) {
            l = new FieldPhotoStore.Listing(List.of(), true);
         }
         FieldPhotoStore.Listing done = l;
         mc.execute(() -> {
            listing = false;
            if (gen != generation) {
               return;
            }
            PhoneModel.Photos ph = PhoneFeed.MODEL.photos;
            ph.shots.clear();
            for (FieldPhotoStore.Photo p : done.photos()) {
               if (p.pending()) {
                  continue;
               }
               String key = p.path().toAbsolutePath().normalize().toString();
               PATHS.put(key, p.path());
               ph.shots.add(new PhoneModel.Shot(key, p.time()));
            }
            ph.folder = FieldPhotoStore.folder(mc).toString();
            ph.loaded = true;
            PhoneFeed.MODEL.version++;
         });
      });
   }

   static void delete(PhoneModel.Shot shot) {
      Path p = PATHS.get(shot.key());
      if (p == null) {
         return;
      }
      forget(shot.key());
      PhoneModel.Photos ph = PhoneFeed.MODEL.photos;
      ph.shots.removeIf(s -> s.key().equals(shot.key()));
      Util.ioPool().execute(() -> {
         try {
            Files.deleteIfExists(p);
         } catch (Exception e) {
            LoggerFactory.getLogger(PhoneGallery.class).debug("Cannot delete {}", p, e);
         }
         Minecraft.getInstance().execute(PhoneGallery::refresh);
      });
   }

   static Path path(PhoneModel.Shot shot) {
      return PATHS.get(shot.key());
   }

   static void openFolder() {
      Minecraft mc = Minecraft.getInstance();
      Path dir = FieldPhotoStore.folder(mc);
      try {
         Files.createDirectories(dir);
         Util.getPlatform().openFile(dir.toFile());
      } catch (Exception e) {
         PhoneClient.toast("Photos are in " + dir);
      }
   }

   // ------------------------------------------------------------------------------------------------ textures

   private static PhoneModel.Pic pic(String key, boolean full) {
      Map<String, Tex> cache = full ? FULLS : THUMBS;
      Tex t = cache.get(key);
      if (t != null) {
         return t.pic;
      }
      String ask = (full ? "F" : "T") + key;
      if (ASKED.add(ask)) {
         Path p = PATHS.get(key);
         if (p != null) {
            int gen = generation;
            // the large copy jumps the queue: the hunter is looking at it
            Runnable job = () -> decode(key, p, full, gen);
            if (full) {
               QUEUE.addFirst(job);
            } else {
               QUEUE.addLast(job);
            }
            pump();
         }
      }
      return null;
   }

   private static void pump() {
      if (busy || QUEUE.isEmpty()) {
         return;
      }
      busy = true;
      Runnable job = QUEUE.pollFirst();
      IO.execute(job);
   }

   private static void decode(String key, Path p, boolean full, int gen) {
      FieldPhotoStore.Decoded d = null;
      try {
         d = FieldPhotoStore.decode(p, full ? FULL : THUMB);
      } catch (Throwable e) {
         d = null;
      }
      FieldPhotoStore.Decoded done = d;
      Minecraft.getInstance().execute(() -> {
         busy = false;
         try {
            if (done == null || done.image == null || gen != generation) {
               if (done != null) {
                  done.close();
               }
               return;
            }
            Map<String, Tex> cache = full ? FULLS : THUMBS;
            ResourceLocation id = FrontierHunts.id("phone/photo/" + (full ? "f" : "t") + (counter++));
            DynamicTexture tex = new DynamicTexture(done.image);
            done.image = null;
            Minecraft.getInstance().getTextureManager().register(id, tex);
            Tex old = cache.put(key, new Tex(id, tex, tex.getPixels() == null ? 1 : tex.getPixels().getWidth(),
               tex.getPixels() == null ? 1 : tex.getPixels().getHeight()));
            if (old != null) {
               old.release();
            }
            int max = full ? MAX_FULL : MAX_THUMBS;
            Iterator<Map.Entry<String, Tex>> it = cache.entrySet().iterator();
            while (cache.size() > max && it.hasNext()) {
               Map.Entry<String, Tex> e = it.next();
               e.getValue().release();
               ASKED.remove((full ? "F" : "T") + e.getKey());
               it.remove();
            }
            PhoneFeed.MODEL.version++;
         } finally {
            pump();
         }
      });
   }

   private static void forget(String key) {
      for (Map<String, Tex> cache : List.of(THUMBS, FULLS)) {
         Tex t = cache.remove(key);
         if (t != null) {
            t.release();
         }
      }
      ASKED.remove("T" + key);
      ASKED.remove("F" + key);
   }

   /** Frees every texture (logout). */
   static void release() {
      generation++;
      for (Map<String, Tex> cache : List.of(THUMBS, FULLS)) {
         for (Tex t : new ArrayList<>(cache.values())) {
            t.release();
         }
         cache.clear();
      }
      ASKED.clear();
      QUEUE.clear();
      PATHS.clear();
      PhoneModel.Photos ph = PhoneFeed.MODEL.photos;
      ph.shots.clear();
      ph.loaded = false;
   }
}
