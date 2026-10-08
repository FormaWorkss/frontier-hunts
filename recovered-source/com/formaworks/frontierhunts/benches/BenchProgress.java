package com.formaworks.frontierhunts.benches;

import com.formaworks.frontierhunts.journal.JournalApi;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;

/**
 * [benches] The Handbook's step 1 task "make your three benches": a bench counts once the hunter has crafted it, placed
 * it, or worked at one (a village's or a campmate's bench counts too). Server side.
 */
public final class BenchProgress {
   private BenchProgress() {
   }

   private static String counter(Bench b) {
      return "bench.opened." + b.id;
   }

   /** A bench screen was opened (journal counter, read by {@link #has}). */
   public static void opened(ServerPlayer p, Bench b) {
      try {
         if (JournalApi.counter(p, counter(b)) == 0) {
            JournalApi.count(p, counter(b), 1);
         }
      } catch (RuntimeException ignored) {
         // the journal is optional for this
      }
   }

   public static boolean has(ServerPlayer p, ServerStatsCounter st, Bench b) {
      if (st.getValue(Stats.ITEM_CRAFTED.get(b.item())) > 0 || st.getValue(Stats.ITEM_USED.get(b.item())) > 0) {
         return true;
      }
      try {
         return JournalApi.counter(p, counter(b)) > 0;
      } catch (RuntimeException e) {
         return false;
      }
   }

   public static boolean allThree(ServerPlayer p, ServerStatsCounter st) {
      for (Bench b : Bench.values()) {
         if (!has(p, st, b)) {
            return false;
         }
      }
      return true;
   }
}
