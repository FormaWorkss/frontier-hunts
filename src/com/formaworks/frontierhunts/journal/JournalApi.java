package com.formaworks.frontierhunts.journal;

import net.minecraft.server.level.ServerPlayer;

/**
 * [journal] Public, dependency-free API for other workstreams (server side; all calls are no-ops for spectators and,
 * unless configured, creative players):
 * <ul>
 * <li>{@link #count} bumps a journal counter (pair with {@link Checklist#register} for a checklist entry);</li>
 * <li>{@link #xp} awards skill XP (the journal's multiplier, level-ups, perks and ranks apply);</li>
 * <li>{@link #note} writes a dated field note;</li>
 * <li>perk effects for survival: {@link HunterSkills#coldExposure} and {@link HunterSkills#nutrition}.</li>
 * </ul>
 */
public final class JournalApi {
   private JournalApi() {
   }

   public static void count(ServerPlayer p, String counter, int amount) {
      JournalService.count(p, counter, amount);
   }

   public static void xp(ServerPlayer p, Skill skill, int amount) {
      JournalService.xp(p, skill, amount);
   }

   public static void note(ServerPlayer p, String text) {
      JournalService.note(p, text, (byte)6);
   }

   public static int counter(ServerPlayer p, String counter) {
      HunterRecord r = ProgressStore.get(p.server).find(p.getUUID());
      return r == null ? 0 : r.get(counter);
   }
}
