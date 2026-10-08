package com.formaworks.frontierhunts.journal;

import com.formaworks.frontierhunts.hunts.HuntTracker;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * [hunts] The species hunts' door into the journal (a new file in the journal package, no edits to journal logic):
 * applies milestone counter updates to a hunter's record - online or offline (trail cameras photograph for absent
 * owners) - through the journal's own counting, so checklist completion, XP, toasts, notes and sync all happen as for
 * any other entry, and reports which entries were completed by the update so the hunt rewards can be paid.
 */
public final class HuntBridge {
   /** Persistent-data tags the journal writes on animals (read only here). */
   public static final String HIT = JournalHooks.HIT;
   public static final String CALL = JournalHooks.CALL;

   private HuntBridge() {
   }

   /** The online hunter's record, or null when their actions are not counted (spectator, creative, training grounds). */
   public static HunterRecord record(ServerPlayer p) {
      return JournalService.record(p);
   }

   /** Any hunter's stored record (creates an empty one). Use only for offline hunters. */
   public static HunterRecord stored(MinecraftServer server, UUID id) {
      return ProgressStore.get(server).record(id);
   }

   /** Read-only lookup, null when the hunter has no record yet. */
   public static HunterRecord find(MinecraftServer server, UUID id) {
      return ProgressStore.get(server).find(id);
   }

   /**
    * Sets each updated counter to its new value (as a delta on the record, so ordinary counters keep adding) and returns
    * the checklist entries this completed. {@code online} may be null for an offline hunter (no toast, no note; the
    * completion and its XP are kept).
    */
   public static List<Checklist.Entry> apply(MinecraftServer server, HunterRecord r, ServerPlayer online, List<HuntTracker.Update> updates) {
      List<Checklist.Entry> completed = new ArrayList<>();
      if (r == null || updates == null) {
         return completed;
      }
      for (HuntTracker.Update u : updates) {
         int cur = r.get(u.key());
         if (u.value() == cur) {
            continue;
         }
         List<Checklist.Entry> watch = Checklist.watching(u.key());
         boolean[] was = new boolean[watch.size()];
         for (int i = 0; i < was.length; i++) {
            was[i] = r.done.contains(watch.get(i).id());
         }
         JournalService.count(server, r, online, u.key(), u.value() - cur);
         for (int i = 0; i < was.length; i++) {
            if (!was[i] && r.done.contains(watch.get(i).id())) {
               completed.add(watch.get(i));
            }
         }
      }
      return completed;
   }

   public static void dirty(MinecraftServer server, HunterRecord r) {
      JournalService.dirty(server, r);
   }

   /** A field note in the hunter's journal (kind 0 harvest-style, 1 checklist, 4 discovery, 6 other). */
   public static void note(ServerPlayer p, String text, byte kind) {
      JournalService.note(p, text, kind);
   }
}
