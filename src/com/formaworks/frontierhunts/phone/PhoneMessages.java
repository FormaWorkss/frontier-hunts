package com.formaworks.frontierhunts.phone;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * [phone] Texts between hunters on a server. The server checks everything: the sender carries a charged phone with
 * signal ({@link PhoneServer}), the recipient is a real hunter on this world who is not the sender, the text is
 * cleaned (no formatting codes or control characters, 200 characters at most), and a sender gets one text every
 * 1.5 seconds and 12 a minute. Texts to a hunter who is offline wait in {@link PhoneStore} until they come back.
 */
@EventBusSubscriber(modid = com.formaworks.frontierhunts.FrontierHunts.ID)
public final class PhoneMessages {
   public static final int MAX_TEXT = 200;
   public static final long GAP_MS = 1500L;
   public static final int PER_MINUTE = 12;
   private static final Map<UUID, Deque<Long>> SENT = new HashMap<>();
   /** for tests: the clock the rate limit uses */
   public static java.util.function.LongSupplier clock = System::currentTimeMillis;

   private PhoneMessages() {
   }

   /** May {@code sender} text now (rate limit)? Records the send when it may. */
   public static boolean allow(UUID sender) {
      long now = clock.getAsLong();
      Deque<Long> q = SENT.computeIfAbsent(sender, k -> new ArrayDeque<>());
      while (!q.isEmpty() && now - q.peekFirst() > 60000L) {
         q.pollFirst();
      }
      if (!q.isEmpty() && now - q.peekLast() < GAP_MS || q.size() >= PER_MINUTE) {
         return false;
      }
      q.addLast(now);
      return true;
   }

   public static void send(ServerPlayer from, String raw) {
      if (raw == null) {
         return;
      }
      int nl = raw.indexOf('\n');
      if (nl <= 0) {
         return;
      }
      String toName = PhonePlaces.clean(raw.substring(0, nl), 16);
      String text = PhonePlaces.clean(raw.substring(nl + 1), MAX_TEXT);
      if (toName.isEmpty() || text.isEmpty()) {
         return;
      }
      PhoneStore store = PhoneStore.get(from.server);
      ServerPlayer online = from.server.getPlayerList().getPlayerByName(toName);
      UUID to = online != null ? online.getUUID() : store.byName(toName);
      if (to == null) {
         PhoneServer.toast(from, toName + " has never used a Field Phone here");
         return;
      }
      if (to.equals(from.getUUID())) {
         return;
      }
      if (text.contains("[photo:")) {
         // [1.4.0] a shared photo: it must be on the server already (its parts come first)
         long photo = PhonePhotos.photoIn(text);
         if (photo == 0L || !PhonePhotos.exists(from.server, photo)) {
            PhoneServer.toast(from, "That photo didn't reach the server, try again");
            return;
         }
      }
      if (!allow(from.getUUID())) {
         PhoneServer.toast(from, "Slow down: one text at a time");
         return;
      }
      String fromName = from.getScoreboardName();
      PhoneStore.Hunter mine = store.hunter(from.getUUID(), fromName);
      PhoneStore.Hunter theirs = store.hunter(to, online != null ? online.getScoreboardName() : null);
      long now = System.currentTimeMillis();
      PhoneStore.Message out = new PhoneStore.Message();
      out.peer = to;
      out.peerName = theirs.name.isEmpty() ? toName : theirs.name;
      out.mine = true;
      out.text = text;
      out.at = now;
      out.read = true;
      store.addMessage(mine, out);
      PhoneStore.Message in = new PhoneStore.Message();
      in.peer = from.getUUID();
      in.peerName = fromName;
      in.mine = false;
      in.text = text;
      in.at = now;
      in.read = false;
      store.addMessage(theirs, in);
      sendAll(from);
      if (online != null) {
         sendAll(online);
         CompoundTag n = new CompoundTag();
         n.putString("app", "messages");
         n.putString("title", fromName);
         n.putString("body", PhonePhotos.photoIn(text) != 0L ? "Sent you a photo" : text);
         PhoneNet.send(online, PhoneNet.K_NOTICE, n);
      }
   }

   static void read(ServerPlayer player, String peerName) {
      PhoneStore store = PhoneStore.get(player.server);
      PhoneStore.Hunter h = store.hunter(player.getUUID(), player.getScoreboardName());
      boolean changed = false;
      for (PhoneStore.Message m : h.messages) {
         if (!m.mine && !m.read && m.peerName.equalsIgnoreCase(peerName)) {
            m.read = true;
            changed = true;
         }
      }
      if (changed) {
         store.setDirty();
         sendAll(player);
      }
   }

   static void sendAll(ServerPlayer player) {
      PhoneStore store = PhoneStore.get(player.server);
      PhoneStore.Hunter h = store.hunter(player.getUUID(), player.getScoreboardName());
      ListTag list = new ListTag();
      for (PhoneStore.Message m : h.messages) {
         CompoundTag t = new CompoundTag();
         t.putString("peer", m.peerName);
         t.putBoolean("mine", m.mine);
         t.putString("text", m.text);
         t.putLong("at", m.at);
         t.putBoolean("read", m.read);
         list.add(t);
      }
      CompoundTag tag = new CompoundTag();
      tag.put("messages", list);
      ListTag contacts = new ListTag();
      for (PhoneStore.Hunter other : store.hunters.values()) {
         if (other != h && !other.name.isEmpty() && contacts.size() < 64) {
            contacts.add(StringTag.valueOf(other.name));
         }
      }
      tag.put("contacts", contacts);
      tag.put("online", PhoneGames.onlineNames(player));
      PhoneNet.send(player, PhoneNet.K_MESSAGES, tag);
   }

   @SubscribeEvent
   public static void login(PlayerEvent.PlayerLoggedInEvent e) {
      if (e.getEntity() instanceof ServerPlayer p) {
         PhoneStore store = PhoneStore.get(p.server);
         PhoneStore.Hunter h = store.hunters.get(p.getUUID());
         if (h == null) {
            return;
         }
         store.hunter(p.getUUID(), p.getScoreboardName());
         int unread = 0;
         for (PhoneStore.Message m : h.messages) {
            unread += !m.mine && !m.read ? 1 : 0;
         }
         if (unread > 0) {
            sendAll(p);
            CompoundTag n = new CompoundTag();
            n.putString("app", "messages");
            n.putString("title", "Messages");
            n.putString("body", unread == 1 ? "1 unread text" : unread + " unread texts");
            PhoneNet.send(p, PhoneNet.K_NOTICE, n);
         }
      }
   }

   /** Forgets a sender's recent texts (QA). */
   public static void resetRate(UUID sender) {
      SENT.remove(sender);
   }

   @SubscribeEvent
   public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
      SENT.remove(e.getEntity().getUUID());
   }

   @SubscribeEvent
   public static void stopped(ServerStoppedEvent e) {
      SENT.clear();
   }
}
