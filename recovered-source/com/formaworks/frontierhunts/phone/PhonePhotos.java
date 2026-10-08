package com.formaworks.frontierhunts.phone;

import com.formaworks.frontierhunts.FrontierHunts;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * [1.4.0] Photos shared in texts. The phone takes the picture on the hunter's own computer; to text it, the phone sends
 * a small JPEG (at most 640 pixels wide, under 90 KB) in parts ({@link PhoneNet.PhotoUp}), then a text that names it
 * ({@code [photo:<id>]}). Everything is checked here:
 * <ul>
 * <li>the sender is alive and carries a charged phone with a bar of signal;</li>
 * <li>a photo is at most {@link #MAX_PARTS} parts of {@link #PART} bytes, sent in order, and looks like a JPEG (its
 * start and end markers, and a frame size the client may decode: at most 1024 pixels a side);</li>
 * <li>a hunter shares at most one photo every 6 seconds and 20 an hour;</li>
 * <li>photos are files in the world folder ({@code data/frontierhunts_phone_photos/<id>.jpg}), the oldest dropped past
 * {@link #KEEP}; a photo is only sent to a hunter who has it in a text (sent or received).</li>
 * </ul>
 */
@EventBusSubscriber(modid = FrontierHunts.ID)
public final class PhonePhotos {
   public static final int PART = 30000;
   public static final int MAX_PARTS = 3;
   public static final int MAX_SIDE = 1024;
   public static final int KEEP = 600;
   public static final long GAP_MS = 6000L;
   public static final int PER_HOUR = 20;
   private static final Pattern TOKEN = Pattern.compile("\\[photo:([0-9a-f]{16})\\]");
   private static final Map<UUID, Upload> UPLOADS = new HashMap<>();
   private static final Map<UUID, Deque<Long>> SHARED = new HashMap<>();
   /** for tests: the clock the rate limit uses */
   public static java.util.function.LongSupplier clock = System::currentTimeMillis;

   private static final class Upload {
      long id;
      int parts;
      int next;
      final List<byte[]> data = new ArrayList<>();
   }

   private PhonePhotos() {
   }

   // ------------------------------------------------------------------------------------------------ tokens

   /** The text of a photo message for this id. */
   public static String token(long id) {
      return "[photo:" + hex(id) + "]";
   }

   public static String hex(long id) {
      return String.format(Locale.ROOT, "%016x", id);
   }

   /** The photo a text shows, or 0. */
   public static long photoIn(String text) {
      if (text == null || text.length() < 23) {
         return 0L;
      }
      Matcher m = TOKEN.matcher(text);
      if (!m.find()) {
         return 0L;
      }
      try {
         return Long.parseUnsignedLong(m.group(1), 16);
      } catch (NumberFormatException e) {
         return 0L;
      }
   }

   // ------------------------------------------------------------------------------------------------ files

   static Path folder(MinecraftServer server) {
      return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("frontierhunts_phone_photos");
   }

   static Path file(MinecraftServer server, long id) {
      return folder(server).resolve(hex(id) + ".jpg");
   }

   public static boolean exists(MinecraftServer server, long id) {
      return id != 0L && Files.isRegularFile(file(server, id));
   }

   // ------------------------------------------------------------------------------------------------ upload

   public static void upload(ServerPlayer player, PhoneNet.PhotoUp p) {
      if (player.hasDisconnected() || !player.isAlive() || player.isSpectator() || !PhoneServer.hasCharged(player) || PhoneServer.signal(player) <= 0) {
         UPLOADS.remove(player.getUUID());
         return;
      }
      if (p.id() == 0L || p.parts() < 1 || p.parts() > MAX_PARTS || p.part() < 0 || p.part() >= p.parts() || p.data().length == 0 || p.data().length > PART) {
         UPLOADS.remove(player.getUUID());
         return;
      }
      Upload u = UPLOADS.get(player.getUUID());
      if (p.part() == 0) {
         if (!allow(player.getUUID())) {
            PhoneServer.toast(player, "Slow down: one photo at a time");
            UPLOADS.remove(player.getUUID());
            return;
         }
         u = new Upload();
         u.id = p.id();
         u.parts = p.parts();
         UPLOADS.put(player.getUUID(), u);
      }
      if (u == null || u.id != p.id() || u.parts != p.parts() || u.next != p.part()) {
         UPLOADS.remove(player.getUUID());
         return;
      }
      u.data.add(p.data());
      u.next++;
      if (u.next < u.parts) {
         return;
      }
      UPLOADS.remove(player.getUUID());
      int size = 0;
      for (byte[] b : u.data) {
         size += b.length;
      }
      byte[] all = new byte[size];
      int at = 0;
      for (byte[] b : u.data) {
         System.arraycopy(b, 0, all, at, b.length);
         at += b.length;
      }
      if (!looksLikeJpeg(all)) {
         PhoneServer.toast(player, "That photo could not be sent");
         return;
      }
      MinecraftServer server = player.server;
      Path f = file(server, u.id);
      try {
         if (Files.exists(f)) {
            return; // ids are random 64-bit numbers: a clash is a resend, keep the first
         }
         Files.createDirectories(f.getParent());
         Path tmp = f.resolveSibling(f.getFileName() + ".part");
         Files.write(tmp, all);
         Files.move(tmp, f, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
         trim(server);
      } catch (IOException e) {
         PhoneServer.LOG.debug("Field Phone: could not store a photo", e);
         PhoneServer.toast(player, "That photo could not be sent");
      }
   }

   static boolean allow(UUID sender) {
      long now = clock.getAsLong();
      Deque<Long> q = SHARED.computeIfAbsent(sender, k -> new ArrayDeque<>());
      while (!q.isEmpty() && now - q.peekFirst() > 3600_000L) {
         q.pollFirst();
      }
      if (!q.isEmpty() && now - q.peekLast() < GAP_MS || q.size() >= PER_HOUR) {
         return false;
      }
      q.addLast(now);
      return true;
   }

   /** JPEG start and end markers, and a frame header with a sane size before the image data. */
   public static boolean looksLikeJpeg(byte[] b) {
      if (b.length < 128 || (b[0] & 255) != 0xFF || (b[1] & 255) != 0xD8 || (b[b.length - 2] & 255) != 0xFF || (b[b.length - 1] & 255) != 0xD9) {
         return false;
      }
      int i = 2;
      while (i + 9 < b.length) {
         if ((b[i] & 255) != 0xFF) {
            return false;
         }
         int marker = b[i + 1] & 255;
         int len = ((b[i + 2] & 255) << 8) | (b[i + 3] & 255);
         if (len < 2) {
            return false;
         }
         if (marker == 0xC0 || marker == 0xC1 || marker == 0xC2) {
            int h = ((b[i + 5] & 255) << 8) | (b[i + 6] & 255);
            int w = ((b[i + 7] & 255) << 8) | (b[i + 8] & 255);
            return w > 0 && h > 0 && w <= MAX_SIDE && h <= MAX_SIDE;
         }
         if (marker == 0xDA) {
            return false; // image data before any frame header
         }
         i += 2 + len;
      }
      return false;
   }

   private static void trim(MinecraftServer server) {
      List<Path> all = new ArrayList<>();
      try (DirectoryStream<Path> ds = Files.newDirectoryStream(folder(server), "*.jpg")) {
         for (Path p : ds) {
            all.add(p);
         }
      } catch (IOException e) {
         return;
      }
      if (all.size() <= KEEP) {
         return;
      }
      all.sort((a, b) -> Long.compare(modified(a), modified(b)));
      for (int i = 0; i < all.size() - KEEP; i++) {
         try {
            Files.deleteIfExists(all.get(i));
         } catch (IOException ignored) {
         }
      }
   }

   private static long modified(Path p) {
      try {
         return Files.getLastModifiedTime(p).toMillis();
      } catch (IOException e) {
         return 0L;
      }
   }

   // ------------------------------------------------------------------------------------------------ download

   /** Does this hunter have the photo in a text (sent or received)? */
   static boolean mayView(ServerPlayer player, long id) {
      PhoneStore store = PhoneStore.get(player.server);
      PhoneStore.Hunter h = store.hunters.get(player.getUUID());
      if (h == null) {
         return false;
      }
      String t = token(id);
      for (PhoneStore.Message m : h.messages) {
         if (m.text != null && m.text.contains(t)) {
            return true;
         }
      }
      return false;
   }

   static void get(ServerPlayer player, long id) {
      CompoundTag t = new CompoundTag();
      t.putLong("id", id);
      byte[] data = null;
      if (id != 0L && mayView(player, id)) {
         try {
            Path f = file(player.server, id);
            if (Files.isRegularFile(f) && Files.size(f) <= (long)PART * MAX_PARTS) {
               data = Files.readAllBytes(f);
            }
         } catch (IOException e) {
            data = null;
         }
      }
      if (data == null) {
         t.putBoolean("missing", true);
      } else {
         t.putByteArray("jpg", data);
      }
      PhoneNet.send(player, PhoneNet.K_PHOTO, t);
   }

   // ------------------------------------------------------------------------------------------------ poses

   /** A selfie pose: everyone near enough to see the hunter sees it (the client drops it after a few seconds). */
   static void pose(ServerPlayer player, int pose) {
      if (pose < 0 || pose > 64 || !player.isAlive() || player.isSpectator()) {
         return;
      }
      CompoundTag t = new CompoundTag();
      t.putInt("e", player.getId());
      t.putInt("p", pose);
      for (ServerPlayer other : player.serverLevel().players()) {
         if (other != player && other.distanceToSqr(player) < 96.0 * 96.0) {
            PhoneNet.send(other, PhoneNet.K_POSE, t);
         }
      }
   }

   /** Forget a hunter's upload and share pace (QA). */
   public static void reset(UUID id) {
      UPLOADS.remove(id);
      SHARED.remove(id);
   }

   @SubscribeEvent
   public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
      UPLOADS.remove(e.getEntity().getUUID());
   }

   @SubscribeEvent
   public static void stopped(ServerStoppedEvent e) {
      UPLOADS.clear();
      SHARED.clear();
   }
}
