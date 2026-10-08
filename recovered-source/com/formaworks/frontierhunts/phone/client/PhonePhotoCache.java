package com.formaworks.frontierhunts.phone.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.phone.PhoneNet;
import com.formaworks.frontierhunts.phone.PhonePhotos;
import com.formaworks.frontierhunts.phone.client.ui.PhoneModel;
import com.mojang.blaze3d.platform.NativeImage;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * [1.4.0] Photos shared in texts, on this client: asked from the server the first time a thread shows one (once; again
 * after 30 s if no answer came), decoded off the game thread with a size check, and kept as textures (the 24 most
 * recently shown). The sender's own copy is put here straight away, so a sent photo never waits on the network.
 */
public final class PhonePhotoCache {
   private static final int MAX = 24;
   private static final Map<Long, PhoneGallery.Tex> TEX = new LinkedHashMap<>(16, 0.75F, true);
   private static final Map<Long, Long> ASKED = new HashMap<>();
   private static final Set<Long> GONE = new HashSet<>();
   private static int counter;
   private static int generation;

   private PhonePhotoCache() {
   }

   static void install(PhoneModel m) {
      m.photos.shared = PhonePhotoCache::get;
      m.photos.sharedGone = GONE::contains;
   }

   static PhoneModel.Pic get(long id) {
      PhoneGallery.Tex t = TEX.get(id);
      if (t != null) {
         return t.pic;
      }
      if (GONE.contains(id) || id == 0L) {
         return null;
      }
      long now = System.currentTimeMillis();
      Long at = ASKED.get(id);
      if (at == null || now - at > 30_000L) {
         ASKED.put(id, now);
         ClientActions.send(PhoneNet.Ask.of(PhoneNet.OP_PHOTO_GET, id, 0L));
      }
      return null;
   }

   /** The server's answer (PhoneNet.K_PHOTO). */
   static void receive(CompoundTag t) {
      long id = t.getLong("id");
      if (id == 0L) {
         return;
      }
      if (t.getBoolean("missing")) {
         GONE.add(id);
         return;
      }
      byte[] jpg = t.getByteArray("jpg");
      put(id, jpg);
   }

   /** Decodes a shared photo's JPEG (off the game thread) and keeps it. */
   static void put(long id, byte[] jpg) {
      if (jpg == null || jpg.length == 0 || jpg.length > PhonePhotos.PART * PhonePhotos.MAX_PARTS) {
         return;
      }
      int gen = generation;
      PhoneGallery.IO.execute(() -> {
         NativeImage img = decode(jpg);
         Minecraft.getInstance().execute(() -> {
            if (img == null) {
               GONE.add(id);
               return;
            }
            if (gen != generation) {
               img.close();
               return;
            }
            ResourceLocation rl = FrontierHunts.id("phone/shared/" + (counter++));
            DynamicTexture tex = new DynamicTexture(img);
            Minecraft.getInstance().getTextureManager().register(rl, tex);
            PhoneGallery.Tex old = TEX.put(id, new PhoneGallery.Tex(rl, tex, img.getWidth(), img.getHeight()));
            if (old != null) {
               old.release();
            }
            Iterator<Map.Entry<Long, PhoneGallery.Tex>> it = TEX.entrySet().iterator();
            while (TEX.size() > MAX && it.hasNext()) {
               Map.Entry<Long, PhoneGallery.Tex> e = it.next();
               e.getValue().release();
               ASKED.remove(e.getKey());
               it.remove();
            }
            PhoneFeed.MODEL.version++;
         });
      });
   }

   /** A JPEG into a NativeImage, refusing anything over 1024 pixels a side. */
   static NativeImage decode(byte[] jpg) {
      try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(jpg))) {
         Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
         if (!readers.hasNext()) {
            return null;
         }
         ImageReader r = readers.next();
         try {
            r.setInput(in, true, true);
            int w = r.getWidth(0), h = r.getHeight(0);
            if (w <= 0 || h <= 0 || w > PhonePhotos.MAX_SIDE || h > PhonePhotos.MAX_SIDE) {
               return null;
            }
            BufferedImage b = r.read(0);
            NativeImage img = new NativeImage(w, h, false);
            for (int y = 0; y < h; y++) {
               for (int x = 0; x < w; x++) {
                  int argb = b.getRGB(x, y);
                  img.setPixelRGBA(x, y, 0xFF000000 | (argb & 0xFF) << 16 | (argb & 0xFF00) | (argb >>> 16 & 0xFF));
               }
            }
            return img;
         } finally {
            r.dispose();
         }
      } catch (Throwable e) {
         return null;
      }
   }

   static void release() {
      generation++;
      for (PhoneGallery.Tex t : new ArrayList<>(TEX.values())) {
         t.release();
      }
      TEX.clear();
      ASKED.clear();
      GONE.clear();
   }
}
