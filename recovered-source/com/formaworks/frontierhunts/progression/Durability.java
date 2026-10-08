package com.formaworks.frontierhunts.progression;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * [1.2.7] Progress that can't be lost or doubled by a crash.
 *
 * <p>A reward or a purchase touches two kinds of save at once: the hunter's inventory (the player's .dat file, written
 * when they log out and at autosave) and the mod's books (licences, tokens, contracts, course passes: world SavedData,
 * written only at autosave and shutdown). If the server dies between the two, one rolls back and the other doesn't:
 * the hunter keeps a licence whose purchase never happened, or gets a reward a second time.
 *
 * <p>So every milestone (licence or tag issued, course passed, deer harvested, contract or assignment paid, a hunt
 * milestone reward, the starter kit, the first-hunt reward) asks for a commit: at the end of that server tick the game
 * does exactly what its own five-minute autosave does (players, chunks queued, SavedData), so both sides reach the
 * disk together. Commits are merged: at most one every few seconds, and never more than that late.
 */
@EventBusSubscriber(modid = "frontierhunts")
public final class Durability {
   private static final Logger LOG = LogUtils.getLogger();
   /** shortest time between two commit saves */
   static final long MIN_GAP_MS = 4000L;
   private static boolean pending;
   private static String reason = "";
   private static long lastSave;
   /** commit saves done since the server started (tests) */
   public static int saves;
   /** test only: switch the commit saves off to show what they prevent (the QA crash test's control run) */
   public static boolean disabled;

   private Durability() {
   }

   /** a commit is waiting for its save (tests) */
   public static boolean pending() {
      return pending;
   }

   /** Ask for a save at the end of this tick (or as soon as the gap allows). Main thread only. */
   public static void commit(MinecraftServer server, String why) {
      if (server == null || disabled) {
         return;
      }
      if (!pending) {
         reason = why;
      } else if (!reason.contains(why)) {
         reason = reason + ", " + why;
      }
      pending = true;
   }

   @SubscribeEvent
   public static void tick(ServerTickEvent.Post e) {
      if (!pending) {
         return;
      }
      long now = System.currentTimeMillis();
      if (now - lastSave < MIN_GAP_MS && now >= lastSave) {
         return;
      }
      pending = false;
      lastSave = now;
      MinecraftServer server = e.getServer();
      try {
         long t0 = System.nanoTime();
         server.saveEverything(true, false, false);
         saves++;
         LOG.debug("[FrontierHunts] progress saved ({}) in {} ms", reason, (System.nanoTime() - t0) / 1_000_000L);
      } catch (RuntimeException ex) {
         LOG.warn("[FrontierHunts] could not save progress after {}", reason, ex);
      }
   }

   @SubscribeEvent
   public static void starting(net.neoforged.neoforge.event.server.ServerStartingEvent e) {
      reset();
   }

   /** a fresh server: nothing pending from the last one */
   public static void reset() {
      pending = false;
      lastSave = 0L;
      saves = 0;
      disabled = false;
   }
}
