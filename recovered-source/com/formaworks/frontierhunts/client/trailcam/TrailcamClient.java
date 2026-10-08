package com.formaworks.frontierhunts.client.trailcam;

import com.formaworks.frontierhunts.expedition.LensMonitorScreen;
import com.formaworks.frontierhunts.expedition.LensView;
import com.formaworks.frontierhunts.trailcam.TrailcamNet;
import com.mojang.blaze3d.platform.NativeImage;
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
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.LoggerFactory;

/**
 * Client side of the trail camera photos: the photo library (scenes, developed textures, disk cache), requests to the
 * server, the darkroom queue and the lens uplink used when a photo's ground is not loaded on this client.
 */
@EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT)
public final class TrailcamClient {
   public static final int THUMB_W = 192;
   public static final int THUMB_H = 108;
   private static final int MAX_FULL = 6;
   private static final Map<Long, Photo> PHOTOS = new HashMap<>();
   /** server order (newest first) of each camera's roll */
   private static final Map<Long, List<Long>> ROLLS = new HashMap<>();
   /** latest photo per camera, from the hub */
   private static final Map<Long, Long> HUB = new LinkedHashMap<>();
   private static final Deque<Photo> QUEUE = new ArrayDeque<>();
   private static final Deque<Photo> FULL_LRU = new ArrayDeque<>();
   private static int thumbCount;
   /** a re-request for the rest of a roll that did not fit in one packet */
   private static int moreIn = -1;
   private static int moreCount;
   // uplink
   private static long uplinkPhoto;
   private static Vec3 uplinkLens;
   private static long uplinkAsked;
   private static long uplinkNextAllowed;
   private static boolean uplinkOpen;
   private static long uplinkOpenedAt;
   private static int uplinkAttempts;
   public static int version;

   static {
      TrailcamNet.receiver = TrailcamClient::receive;
   }

   private TrailcamClient() {
   }

   // ------------------------------------------------------------------------------------------------ lifecycle

   @SubscribeEvent
   public static void login(ClientPlayerNetworkEvent.LoggingIn event) {
      TrailcamNet.receiver = TrailcamClient::receive;
      reset();
      PhotoCache.open(Minecraft.getInstance());
   }

   @SubscribeEvent
   public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
      Darkroom.abort();
      reset();
      PhotoCache.close();
   }

   private static void reset() {
      for (Photo p : PHOTOS.values()) {
         releaseAll(p);
      }
      PHOTOS.clear();
      ROLLS.clear();
      HUB.clear();
      QUEUE.clear();
      FULL_LRU.clear();
      thumbCount = 0;
      uplinkPhoto = 0L;
      uplinkLens = null;
      uplinkOpen = false;
      version++;
   }

   // ------------------------------------------------------------------------------------------------ requests

   public static void requestRoll(DarkroomHost host, BlockPos camera) {
      send(host.console(), camera, TrailcamNet.ROLL, 0L, haveFor(camera));
   }

   public static void requestHub(DarkroomHost host) {
      send(host.console(), host.console(), TrailcamNet.HUB, 0L, PhotoCache.all(128));
   }

   public static void delete(DarkroomHost host, Photo photo) {
      send(host.console(), photo.camera, TrailcamNet.DELETE, photo.id, haveFor(photo.camera));
      List<Long> roll = ROLLS.get(photo.camera.asLong());
      if (roll != null) {
         roll.remove(photo.id);
      }
      QUEUE.remove(photo);
      PhotoCache.delete(photo.id);
      releaseAll(photo);
      PHOTOS.remove(photo.id);
      version++;
   }

   private static long[] haveFor(BlockPos camera) {
      Set<Long> have = new HashSet<>();
      for (long id : PhotoCache.have(camera, 128)) {
         have.add(id);
      }
      for (Photo p : PHOTOS.values()) {
         if (p.camera.equals(camera) && p.scene != null && have.size() < 128) {
            have.add(p.id);
         }
      }
      long[] out = new long[have.size()];
      int i = 0;
      for (long id : have) {
         out[i++] = id;
      }
      return out;
   }

   private static void send(BlockPos console, BlockPos camera, int action, long arg, long[] have) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.getConnection() != null && mc.getConnection().hasChannel(TrailcamNet.Request.TYPE)) {
         PacketDistributor.sendToServer(new TrailcamNet.Request(console, camera, action, arg, have));
      }
   }

   // ------------------------------------------------------------------------------------------------ receiving

   private static void receive(TrailcamNet.Photos payload) {
      boolean hub = payload.purpose() == TrailcamNet.PURPOSE_HUB;
      List<Long> ids = new ArrayList<>();
      for (TrailcamNet.Entry e : payload.entries()) {
         Photo p = PHOTOS.computeIfAbsent(e.id(), id -> new Photo(id, e.camera()));
         p.legacy = e.legacy();
         if (e.scene() != null && p.scene == null) {
            p.scene = e.scene();
            p.labelsDirty();
         }
         ids.add(e.id());
         if (hub) {
            HUB.put(e.camera().asLong(), e.id());
         }
         ensure(p);
      }
      if (!hub) {
         long key = payload.pos().asLong();
         List<Long> old = ROLLS.put(key, ids);
         if (old != null) {
            for (Long id : old) {
               if (!ids.contains(id)) {
                  Photo gone = PHOTOS.remove(id);
                  if (gone != null) {
                     QUEUE.remove(gone);
                     releaseAll(gone);
                  }
               }
            }
         }
      }
      if (payload.more() && moreCount < 12) {
         moreIn = 3;
         moreCount++;
      } else if (!payload.more()) {
         moreCount = 0;
      }
      version++;
   }

   /** Moves a photo toward READY: from the disk cache if it was developed before, else into the darkroom. */
   static void ensure(Photo p) {
      switch (p.state) {
         case READY, LOADING, DEVELOPING, QUEUED, FAILED -> {
            return;
         }
         default -> {
         }
      }
      if (PhotoCache.has(p.id)) {
         p.state = Photo.State.LOADING;
         load(p, true);
      } else if (p.scene != null) {
         p.state = Photo.State.QUEUED;
         QUEUE.addLast(p);
      } else {
         p.state = Photo.State.WAITING;
      }
   }

   private static void load(Photo p, boolean thumbOnly) {
      int ver = version;
      Util.ioPool().execute(() -> {
         PhotoCache.Loaded loaded = PhotoCache.read(p.id);
         Minecraft.getInstance().execute(() -> {
            if (!PHOTOS.containsKey(p.id)) {
               return;
            }
            if (loaded == null) {
               if (p.state == Photo.State.LOADING) {
                  p.state = Photo.State.WAITING;
                  ensure(p);
               }
               return;
            }
            if (p.scene == null && loaded.meta().contains("scene", 10)) {
               p.scene = loaded.meta().getCompound("scene");
               p.labelsDirty();
            }
            upload(p, loaded.argb(), loaded.w(), loaded.h(), thumbOnly);
            p.state = Photo.State.READY;
            version++;
         });
      });
   }

   /** Called on the render thread when the darkroom finishes a photo. */
   static void developed(Photo p, int[] argb, int w, int h) {
      if (!PHOTOS.containsKey(p.id)) {
         return;
      }
      CompoundTag meta = new CompoundTag();
      if (p.scene != null) {
         meta.put("scene", p.scene);
      }
      meta.put("legacy", p.legacy);
      PhotoCache.store(p.id, p.camera, argb, w, h, meta);
      upload(p, argb, w, h, false);
      p.state = Photo.State.READY;
      version++;
   }

   static void failed(Photo p, String why) {
      p.state = Photo.State.FAILED;
      p.note = why;
      version++;
   }

   /** Puts a photo back in line (e.g. the screen closed mid-exposure). */
   static void requeue(Photo p) {
      if (PHOTOS.containsKey(p.id) && p.scene != null) {
         p.state = Photo.State.QUEUED;
         QUEUE.addFirst(p);
      }
   }

   // ------------------------------------------------------------------------------------------------ textures

   private static void upload(Photo p, int[] argb, int w, int h, boolean thumbOnly) {
      Minecraft mc = Minecraft.getInstance();
      if (p.thumb == null) {
         NativeImage t = new NativeImage(THUMB_W, THUMB_H, false);
         double sx = (double)w / THUMB_W;
         double sy = (double)h / THUMB_H;
         for (int y = 0; y < THUMB_H; y++) {
            int y0 = (int)(y * sy);
            int y1 = Math.max(y0 + 1, (int)((y + 1) * sy));
            for (int x = 0; x < THUMB_W; x++) {
               int x0 = (int)(x * sx);
               int x1 = Math.max(x0 + 1, (int)((x + 1) * sx));
               int r = 0;
               int g = 0;
               int b = 0;
               int n = 0;
               for (int yy = y0; yy < y1 && yy < h; yy++) {
                  for (int xx = x0; xx < x1 && xx < w; xx++) {
                     int c = argb[yy * w + xx];
                     r += c >> 16 & 255;
                     g += c >> 8 & 255;
                     b += c & 255;
                     n++;
                  }
               }
               n = Math.max(1, n);
               t.setPixelRGBA(x, y, 0xFF000000 | (b / n) << 16 | (g / n) << 8 | r / n);
            }
         }
         p.thumb = new DynamicTexture(t);
         p.thumb.setFilter(true, false);
         p.thumbLoc = ResourceLocation.fromNamespaceAndPath("frontierhunts", "trailcam/thumb_" + Long.toHexString(p.id));
         mc.getTextureManager().register(p.thumbLoc, p.thumb);
         thumbCount++;
         trimThumbs();
      }
      if (!thumbOnly && p.full == null) {
         NativeImage img = new NativeImage(w, h, false);
         for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
               int c = argb[y * w + x];
               img.setPixelRGBA(x, y, 0xFF000000 | (c & 255) << 16 | (c >> 8 & 255) << 8 | (c >> 16 & 255));
            }
         }
         p.full = new DynamicTexture(img);
         p.full.setFilter(true, false);
         p.fullLoc = ResourceLocation.fromNamespaceAndPath("frontierhunts", "trailcam/full_" + Long.toHexString(p.id));
         mc.getTextureManager().register(p.fullLoc, p.full);
         FULL_LRU.remove(p);
         FULL_LRU.addLast(p);
         while (FULL_LRU.size() > MAX_FULL) {
            releaseFull(FULL_LRU.removeFirst());
         }
      }
   }

   /** The full-size texture for the photo viewer, loading it back from the cache if it was evicted. */
   public static ResourceLocation full(Photo p) {
      if (p.fullLoc != null) {
         FULL_LRU.remove(p);
         FULL_LRU.addLast(p);
         return p.fullLoc;
      }
      if (p.state == Photo.State.READY && p.attempts++ < 3) {
         load(p, false);
      }
      return null;
   }

   /** A copy of the developed photo, for saving. Null if not in memory yet. */
   public static NativeImage copyFull(Photo p) {
      if (p.full == null || p.full.getPixels() == null) {
         full(p);
         return null;
      }
      NativeImage src = p.full.getPixels();
      NativeImage copy = new NativeImage(src.getWidth(), src.getHeight(), false);
      copy.copyFrom(src);
      return copy;
   }

   private static void trimThumbs() {
      if (thumbCount <= 480) {
         return;
      }
      Iterator<Photo> it = PHOTOS.values().iterator();
      while (it.hasNext() && thumbCount > 400) {
         Photo p = it.next();
         if (p.thumb != null && p.full == null) {
            Minecraft.getInstance().getTextureManager().release(p.thumbLoc);
            p.thumb = null;
            p.thumbLoc = null;
            thumbCount--;
            if (p.state == Photo.State.READY) {
               p.state = Photo.State.WAITING;
            }
         }
      }
   }

   private static void releaseFull(Photo p) {
      if (p.fullLoc != null) {
         Minecraft.getInstance().getTextureManager().release(p.fullLoc);
      }
      p.full = null;
      p.fullLoc = null;
      p.attempts = 0;
   }

   private static void releaseAll(Photo p) {
      releaseFull(p);
      FULL_LRU.remove(p);
      if (p.thumbLoc != null) {
         Minecraft.getInstance().getTextureManager().release(p.thumbLoc);
         thumbCount--;
      }
      p.thumb = null;
      p.thumbLoc = null;
   }

   // ------------------------------------------------------------------------------------------------ views

   public static List<Photo> roll(BlockPos camera) {
      List<Long> ids = ROLLS.get(camera.asLong());
      List<Photo> out = new ArrayList<>();
      if (ids != null) {
         for (Long id : ids) {
            Photo p = PHOTOS.get(id);
            if (p != null) {
               if (p.state == Photo.State.WAITING) {
                  ensure(p);
               }
               out.add(p);
            }
         }
      }
      return out;
   }

   public static boolean hasRoll(BlockPos camera) {
      return ROLLS.containsKey(camera.asLong());
   }

   public static Photo latest(BlockPos camera) {
      Long id = HUB.get(camera.asLong());
      Photo p = id == null ? null : PHOTOS.get(id);
      if (p != null && p.state == Photo.State.WAITING) {
         ensure(p);
      }
      return p;
   }

   public static int pending(BlockPos camera) {
      int n = 0;
      for (Photo p : roll(camera)) {
         if (p.state == Photo.State.QUEUED || p.state == Photo.State.DEVELOPING || p.state == Photo.State.WAITING || p.state == Photo.State.REMOTE) {
            n++;
         }
      }
      return n;
   }

   /** Moves a photo to the front of the darkroom queue (the one the player is looking at). */
   public static void prioritise(Photo p) {
      if (p.state == Photo.State.QUEUED && QUEUE.remove(p)) {
         QUEUE.addFirst(p);
      }
   }

   /** Next photo the darkroom can develop right now, or null. REMOTE photos wait for the uplink. */
   static Photo nextJob(java.util.function.Predicate<Photo> loadable, DarkroomHost host) {
      int n = QUEUE.size();
      for (int i = 0; i < n; i++) {
         Photo p = QUEUE.pollFirst();
         if (p == null || !PHOTOS.containsKey(p.id) || p.state != Photo.State.QUEUED && p.state != Photo.State.REMOTE) {
            continue;
         }
         if (loadable.test(p)) {
            return p;
         }
         if (p.state != Photo.State.FAILED) {
            p.state = Photo.State.REMOTE;
            QUEUE.addLast(p);
         }
      }
      return null;
   }

   static boolean queueEmpty() {
      return QUEUE.isEmpty();
   }

   // ------------------------------------------------------------------------------------------------ uplink

   static boolean uplinkActive() {
      return Minecraft.getInstance().getCameraEntity() instanceof LensView;
   }

   static Vec3 uplinkLens() {
      return uplinkOpen ? uplinkLens : null;
   }

   /** Asks the server to open the lens link where a waiting photo was taken, so its ground streams to this client. */
   static void maybeUplink(DarkroomHost host, long gameTime) {
      if (host.uplinkCamera() == null) {
         return;
      }
      if (uplinkAsked != 0L && !uplinkOpen) {
         if (uplinkActive()) {
            uplinkOpen = true;
            uplinkOpenedAt = gameTime;
         } else if (gameTime - uplinkAsked > 120L || gameTime < uplinkAsked) {
            // no answer: the camera is out of reach for the server too (other dimension, world border...)
            uplinkAsked = 0L;
            if (++uplinkAttempts >= 2) {
               for (Photo p : QUEUE) {
                  if (p.state == Photo.State.REMOTE && p.camera.equals(host.uplinkCamera())) {
                     p.state = Photo.State.FAILED;
                     p.note = "No signal from the camera";
                  }
               }
               QUEUE.removeIf(p -> p.state == Photo.State.FAILED);
               version++;
            }
         }
         return;
      }
      if (uplinkOpen) {
         if (!uplinkActive()) {
            // the server closed the link (moved away, timeout)
            uplinkOpen = false;
            uplinkAsked = 0L;
            uplinkNextAllowed = gameTime + 70L;
            return;
         }
         boolean left = false;
         boolean late = gameTime - uplinkOpenedAt > 600L || gameTime < uplinkOpenedAt;
         for (Photo p : QUEUE) {
            if (p.state == Photo.State.REMOTE && near(p, uplinkLens)) {
               if (late && !Darkroom.busy()) {
                  // the ground never finished streaming: give up on these rather than holding the link open
                  p.state = Photo.State.FAILED;
                  p.note = "No signal from the camera";
               } else {
                  left = true;
               }
            }
         }
         if (late) {
            QUEUE.removeIf(p -> p.state == Photo.State.FAILED);
         }
         if (!left && !Darkroom.busy()) {
            closeUplink(gameTime);
         }
         return;
      }
      if (gameTime < uplinkNextAllowed) {
         return;
      }
      for (Photo p : QUEUE) {
         if (p.state == Photo.State.REMOTE && p.camera.equals(host.uplinkCamera()) && p.scene != null) {
            uplinkPhoto = p.id;
            uplinkLens = Darkroom.lensOf(p.scene);
            uplinkAsked = gameTime;
            send(host.console(), p.camera, TrailcamNet.UPLINK, p.id, new long[0]);
            version++;
            return;
         }
      }
   }

   static boolean near(Photo p, Vec3 lens) {
      if (lens == null || p.scene == null) {
         return false;
      }
      return Darkroom.lensOf(p.scene).distanceToSqr(lens) < 24.0 * 24.0;
   }

   static void closeUplink(long gameTime) {
      if (uplinkOpen || uplinkAsked != 0L) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.getConnection() != null) {
            PacketDistributor.sendToServer(new LensView.CloseRequest());
         }
      }
      uplinkOpen = false;
      uplinkAsked = 0L;
      uplinkPhoto = 0L;
      uplinkLens = null;
      uplinkAttempts = 0;
      uplinkNextAllowed = gameTime + 70L;
      version++;
   }

   public static String uplinkStatus() {
      if (uplinkOpen) {
         return "Uplink open · receiving the ground around the camera";
      }
      return uplinkAsked != 0L ? "Calling the camera over the uplink…" : null;
   }

   /** Called when a darkroom screen closes. */
   public static void hostClosed() {
      Minecraft mc = Minecraft.getInstance();
      Darkroom.abort();
      if (uplinkOpen || uplinkAsked != 0L) {
         closeUplink(mc.level == null ? 0L : mc.level.getGameTime());
      }
      for (Photo p : PHOTOS.values()) {
         if (p.state == Photo.State.REMOTE) {
            p.state = Photo.State.QUEUED;
         }
      }
   }

   @SubscribeEvent
   public static void screenOpening(ScreenEvent.Opening event) {
      // the uplink reuses the live lens link; keep the photo screen up instead of the live monitor
      if (event.getNewScreen() instanceof LensMonitorScreen && Minecraft.getInstance().screen instanceof DarkroomHost && (uplinkAsked != 0L || uplinkOpen)) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void tick(ClientTickEvent.Post event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return;
      }
      if (moreIn > 0 && --moreIn == 0) {
         moreIn = -1;
         if (mc.screen instanceof DarkroomHost host) {
            if (host.uplinkCamera() != null) {
               requestRoll(host, host.uplinkCamera());
            } else {
               requestHub(host);
            }
         }
      }
      if (mc.screen instanceof DarkroomHost host && host.develops()) { // [phone] the phone only while its gallery is up
         maybeUplink(host, mc.level.getGameTime());
      } else if (uplinkOpen || uplinkAsked != 0L) {
         closeUplink(mc.level.getGameTime());
      }
   }

   static void log(String msg, Throwable t) {
      LoggerFactory.getLogger(TrailcamClient.class).warn(msg, t);
   }
}
