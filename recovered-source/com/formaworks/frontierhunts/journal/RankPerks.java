package com.formaworks.frontierhunts.journal;

import net.minecraft.server.level.ServerPlayer;

/**
 * [1.1.6] What a hunter rank is worth. Ranks used to be a title and a toast; each now opens something real at the
 * licence counter / ranger office:
 * <ul>
 * <li>Woodsman: the ranger's game report (where the herds are, the rut, the wind).</li>
 * <li>Tracker: the report counts each group and tells bucks from does.</li>
 * <li>Guide: 10% off licences, tags, stamps and ranger services.</li>
 * <li>Master Hunter: 20% off, and one more deer tag a season.</li>
 * <li>Legend: the Hunting Licence and game reports are free.</li>
 * </ul>
 */
public final class RankPerks {
   private RankPerks() {
   }

   public static Rank rank(ServerPlayer p) {
      try {
         return Rank.of(JournalService.record(p).totalXp());
      } catch (RuntimeException e) {
         return Rank.GREENHORN;
      }
   }

   public static boolean atLeast(ServerPlayer p, Rank r) {
      return rank(p).ordinal() >= r.ordinal();
   }

   /** percent off at the counter */
   public static int discount(Rank r) {
      return r.ordinal() >= Rank.MASTER_HUNTER.ordinal() ? 20 : (r.ordinal() >= Rank.GUIDE.ordinal() ? 10 : 0);
   }

   public static int price(ServerPlayer p, int tokens) {
      if (tokens <= 0) {
         return tokens;
      }
      // [1.2.0] a Master of the Reserve pays nothing at the licence counter; each legend taken is 5% off for good
      if (com.formaworks.frontierhunts.freak.LegendRewards.master(p)) {
         return 0;
      }
      int d = Math.min(40, discount(rank(p)) + com.formaworks.frontierhunts.freak.LegendRewards.discount(p));
      return Math.max(1, Math.round(tokens * (100 - d) / 100.0F));
   }

   public static int extraDeerTags(ServerPlayer p) {
      return atLeast(p, Rank.MASTER_HUNTER) ? 1 : 0;
   }

   /** lang key of the perk a rank brings (shown with the rank-up notice and on the journal's rank card) */
   public static String perkKey(Rank r) {
      return "journal.frontierhunts.rank_perk." + r.key;
   }
}
