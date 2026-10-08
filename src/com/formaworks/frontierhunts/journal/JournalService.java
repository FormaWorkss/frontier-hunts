package com.formaworks.frontierhunts.journal;

import com.formaworks.frontierhunts.HunterLedger;
import com.formaworks.frontierhunts.expedition.CampaignProgress;
import com.formaworks.frontierhunts.expedition.ExpeditionLedger;
import com.formaworks.frontierhunts.expedition.HuntingCalendar;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import org.slf4j.Logger;

/**
 * [journal] Server side of the Hunter's Journal: counters, skill XP, ranks, perks, checklist completions, field notes,
 * season summaries and the sync to the owner. Everything here runs on the server thread.
 */
public final class JournalService {
   private static final Logger LOG = LogUtils.getLogger();
   private static final String[] MONTHS = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
   private static final String[] SEASONS = {"Spring", "Summer", "Fall", "Winter"};
   /** players with the journal open -> last data push (game time) */
   private static final Map<UUID, Long> VIEWING = new HashMap<>();
   private static final Map<UUID, Long> ASKS = new HashMap<>();
   /** counters snapshotted at the start of a reserve season for its summary */
   static final String[] SEASON_KEYS = {Stat.KILLS, Stat.CLEAN_KILLS, Stat.HARVESTS, Stat.RECOVERIES, Stat.LOST, Stat.SHOTS, Stat.HITS, Stat.WALK_M,
      Stat.PHOTOS, Stat.SIGNS, Stat.STALKS, Stat.CALLS};

   private JournalService() {
   }

   // ============================================================================================ access

   /** The hunter's record, or null when this player's actions are not counted (spectator, creative unless allowed). */
   public static HunterRecord record(ServerPlayer p) {
      if (p == null || p.isSpectator() || p.isCreative() && !JournalConfig.countCreative()) {
         return null;
      }
      if (com.formaworks.frontierhunts.academy.Academy.journalBlocked(p)) {
         return null; // [academy] nothing done in the training grounds counts (course rewards are paid once home)
      }
      return ProgressStore.get(p.server).record(p.getUUID());
   }

   static void dirty(MinecraftServer server, HunterRecord r) {
      r.changed = true;
      ProgressStore.get(server).setDirty();
   }

   // ============================================================================================ counters

   /** Adds {@code n} to a counter (or keeps the max for {@link Stat#MAX} counters) and checks the checklist. */
   public static void count(ServerPlayer p, String key, int n) {
      HunterRecord r = record(p);
      if (r != null && n != 0) {
         count(p.server, r, p, key, n);
      }
   }

   static void count(MinecraftServer server, HunterRecord r, ServerPlayer online, String key, int n) {
      if (key == null || key.isEmpty() || key.length() > 64 || r.counters.size() >= HunterRecord.MAX_COUNTERS && !r.counters.containsKey(key)) {
         return;
      }
      int old = r.get(key);
      int nv = Stat.MAX.contains(key) ? Math.max(old, n) : (int)Math.clamp((long)old + n, 0L, Integer.MAX_VALUE);
      if (nv == old) {
         return;
      }
      r.counters.put(key, nv);
      if (Stat.QUIET.contains(key)) {
         ProgressStore.get(server).setDirty(); // saved, but no resync to an open journal every second for these
      } else {
         dirty(server, r);
      }
      checklist(server, r, online, key, nv);
   }

   /** Sets a max-type counter to at least {@code v}. */
   public static void max(ServerPlayer p, String key, int v) {
      HunterRecord r = record(p);
      if (r != null && v > r.get(key)) {
         if (!Stat.MAX.contains(key)) {
            count(p.server, r, p, key, v - r.get(key));
         } else {
            count(p.server, r, p, key, v);
         }
      }
   }

   private static void checklist(MinecraftServer server, HunterRecord r, ServerPlayer online, String key, int value) {
      for (Checklist.Entry e : Checklist.watching(key)) {
         if (value >= e.target() && r.done.add(e.id())) {
            int xp = scaled(e.xp());
            if (xp > 0 && JournalConfig.skills()) {
               r.bonusXp = (int)Math.min(100_000_000L, (long)r.bonusXp + xp);
            }
            dirty(server, r);
            if (online != null) {
               r.note(stamp(online.serverLevel()), "@journal.frontierhunts.note.check|check:" + e.id(), (byte)1);
               JournalNet.send(online, new JournalNet.Notice(JournalNet.N_CHECK, e.id(), 0, JournalConfig.skills() ? xp : 0));
               online.serverLevel().playSound(null, online.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 0.9F);
               rank(online, r);
            }
         }
      }
   }

   // ============================================================================================ XP / levels / perks

   static int scaled(int xp) {
      return (int)Math.round(xp * JournalConfig.xpMultiplier());
   }

   /** Awards skill XP (scaled by the server multiplier), announces level-ups, perk unlocks and ranks. */
   public static void xp(ServerPlayer p, Skill skill, int amount) {
      if (!JournalConfig.skills() || amount <= 0 || skill == null) {
         return;
      }
      HunterRecord r = record(p);
      if (r == null) {
         return;
      }
      int add = scaled(amount);
      if (add <= 0) {
         return;
      }
      int i = skill.ordinal();
      int before = Skill.level(r.skillXp[i]);
      r.skillXp[i] = (int)Math.min(10_000_000L, (long)r.skillXp[i] + add);
      int after = Skill.level(r.skillXp[i]);
      dirty(p.server, r);
      JournalNet.send(p, new JournalNet.Notice(JournalNet.N_XP, "", i, add));
      if (after > before) {
         boolean perk = false;
         for (Perk k : Perk.values()) {
            if (k.skill == skill && k.level > before && k.level <= after) {
               perk = true;
               JournalNet.send(p, new JournalNet.Notice(JournalNet.N_PERK, k.key, k.ordinal(), after));
               r.note(stamp(p.serverLevel()), "@journal.frontierhunts.note.perk|key:" + k.lang("name") + "|key:" + skill.lang("name") + "|" + after, (byte)3);
            }
         }
         if (!perk) {
            JournalNet.send(p, new JournalNet.Notice(JournalNet.N_LEVEL, skill.key, i, after));
         }
         p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.35F, 1.35F);
         perks(p, r);
      }
      rank(p, r);
   }

   /** Recomputes the active perk mask, stores it for the hook queries and tells the client. */
   static void perks(ServerPlayer p, HunterRecord r) {
      int mask = JournalConfig.skills() ? Perk.unlocked(r.levels()) & ~JournalConfig.disabledMask() : 0;
      float s = (float)JournalConfig.perkStrength();
      HunterSkills.strength(s);
      HunterSkills.set(p.getUUID(), mask);
      r.perksShown = mask;
      JournalNet.send(p, new JournalNet.Perks(mask, s));
   }

   static void rank(ServerPlayer p, HunterRecord r) {
      Rank now = Rank.of(r.totalXp());
      if (now.ordinal() > r.rankShown) {
         r.rankShown = now.ordinal();
         dirty(p.server, r);
         JournalNet.send(p, new JournalNet.Notice(JournalNet.N_RANK, now.key, now.ordinal(), 0));
         r.note(stamp(p.serverLevel()), "@journal.frontierhunts.note.rank|key:" + now.lang(), (byte)2);
         p.sendSystemMessage(Component.translatable(RankPerks.perkKey(now)).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xD8B25A)))); // [1.1.6]
         p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.45F, 1.0F); // [integ4] quieter fanfare
         if (JournalConfig.announceRanks()) {
            MutableComponent m = Component.literal("[Journal] ").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xD8B25A)))
               .append(Component.translatable("journal.frontierhunts.chat.rank", p.getDisplayName(), Component.translatable(now.lang()))
                  .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xE9DFC6))));
            p.server.getPlayerList().broadcastSystemMessage(m, false);
         }
      }
   }

   // ============================================================================================ notes

   /** A field note in this hunter's journal, stamped with the reserve date and time of day. */
   public static void note(ServerPlayer p, String text, byte kind) {
      HunterRecord r = record(p);
      if (r != null) {
         r.note(stamp(p.serverLevel()), text, kind);
         dirty(p.server, r);
      }
   }

   /** "Oct 12, 6:42 AM" on the reserve calendar. */
   public static String stamp(ServerLevel level) {
      try {
         HuntingCalendar.Date d = HuntingCalendar.date(level.getServer().overworld());
         long t = Math.floorMod(level.getDayTime(), 24000L);
         int h = (int)((t / 1000L + 6L) % 24L);
         int m = (int)(t % 1000L * 60L / 1000L);
         int h12 = h % 12 == 0 ? 12 : h % 12;
         return MONTHS[Math.floorMod(d.month(), 12)] + " " + d.day() + ", " + h12 + ":" + (m < 10 ? "0" : "") + m + (h < 12 ? " AM" : " PM");
      } catch (RuntimeException e) {
         return "";
      }
   }

   /** Date only, "Oct 12 · Yr 1". */
   public static String date(ServerLevel level) {
      try {
         HuntingCalendar.Date d = HuntingCalendar.date(level.getServer().overworld());
         return MONTHS[Math.floorMod(d.month(), 12)] + " " + d.day() + " · Yr " + (d.serial() / 12L + 1L);
      } catch (RuntimeException e) {
         return "";
      }
   }

   // ============================================================================================ seasons

   /** Reserve calendar season serial: floor((monthSerial - 2) / 3); serial % 4 -> 0 spring .. 3 winter. */
   static int seasonSerial(MinecraftServer server) {
      HuntingCalendar.Date d = HuntingCalendar.date(server.overworld());
      return (int)Math.floorDiv(d.serial() - 2L, 3L);
   }

   static String seasonTitle(int serial) {
      long firstMonth = serial * 3L + 2L;
      return SEASONS[Math.floorMod(serial, 4)] + " · Year " + (firstMonth / 12L + 1L);
   }

   static void snapshot(HunterRecord r, int serial) {
      r.seasonKey = serial;
      r.seasonStart.clear();
      for (String k : SEASON_KEYS) {
         r.seasonStart.put(k, r.get(k));
      }
      r.seasonStart.put("~xp", (int)Math.min(Integer.MAX_VALUE, r.totalXp()));
   }

   /** Called every few seconds per online hunter and at login. */
   static void seasonCheck(ServerPlayer p, HunterRecord r) {
      int serial;
      try {
         serial = seasonSerial(p.server);
      } catch (RuntimeException e) {
         return;
      }
      if (r.seasonKey == -1) {
         snapshot(r, serial);
         dirty(p.server, r);
         return;
      }
      if (r.seasonKey == serial) {
         return;
      }
      rollover(p, r, r.seasonKey, serial);
   }

   /** Writes the summary of season {@code old} (if the hunter did anything) and starts season {@code serial}. */
   static void rollover(ServerPlayer p, HunterRecord r, int old, int serial) {
      if (JournalConfig.seasonSummaries()) {
         HunterRecord.Summary s = summary(r, old);
         if (s != null) {
            r.summaries.addFirst(s);
            while (r.summaries.size() > HunterRecord.MAX_SUMMARIES) {
               r.summaries.removeLast();
            }
            r.note(stamp(p.serverLevel()), "@journal.frontierhunts.note.summary|" + s.title(), (byte)5);
            JournalNet.send(p, new JournalNet.Notice(JournalNet.N_SUMMARY, s.title(), 0, 0));
         }
      }
      snapshot(r, serial);
      dirty(p.server, r);
   }

   private static int delta(HunterRecord r, String k) {
      return Math.max(0, r.get(k) - r.seasonStart.getOrDefault(k, 0));
   }

   static HunterRecord.Summary summary(HunterRecord r, int serial) {
      int kills = delta(r, Stat.KILLS);
      int clean = delta(r, Stat.CLEAN_KILLS);
      int harvests = delta(r, Stat.HARVESTS);
      int rec = delta(r, Stat.RECOVERIES);
      int lost = delta(r, Stat.LOST);
      int shots = delta(r, Stat.SHOTS);
      int hits = delta(r, Stat.HITS);
      int walk = delta(r, Stat.WALK_M);
      int photos = delta(r, Stat.PHOTOS);
      int signs = delta(r, Stat.SIGNS);
      int xp = (int)Math.max(0L, Math.min(Integer.MAX_VALUE, r.totalXp()) - r.seasonStart.getOrDefault("~xp", 0));
      int best = r.seasonStart.getOrDefault("~shot", 0);
      int score = r.seasonStart.getOrDefault("~score", 0);
      int kg10 = r.seasonStart.getOrDefault("~kg10", 0);
      if (kills + harvests + shots + photos + signs == 0 && walk < 500 && xp == 0) {
         return null;
      }
      List<String> l = new ArrayList<>();
      l.add("Animals taken: " + Math.max(kills, harvests) + (clean > 0 ? " (" + clean + " clean)" : ""));
      l.add("Tracked recoveries: " + rec + " · lost: " + lost);
      l.add("Shots: " + shots + " · hits: " + hits + (shots > 0 ? " (" + Math.round(100.0 * Math.min(hits, shots) / shots) + "%)" : ""));
      if (best > 0) {
         l.add("Longest shot: " + best + " m");
      }
      if (score > 0) {
         l.add("Best whitetail rack: " + score + "\"");
      }
      if (kg10 > 0) {
         l.add("Heaviest animal: " + (kg10 >= 100 ? Integer.toString(Math.round(kg10 / 10.0F)) : String.format(Locale.ROOT, "%.1f", kg10 / 10.0)) + " kg");
      }
      l.add("On foot: " + String.format(Locale.ROOT, "%.1f", walk / 1000.0) + " km · sign read: " + signs);
      if (photos > 0) {
         l.add("Trail camera photos: " + photos);
      }
      l.add("Journal XP earned: " + xp + " · rank " + Rank.of(r.totalXp()).title());
      return new HunterRecord.Summary(seasonTitle(serial), List.copyOf(l));
   }

   /** Season-best values for the summary (kept next to the snapshot). */
   static void seasonBest(HunterRecord r, String key, int v) {
      if (v > r.seasonStart.getOrDefault(key, 0)) {
         r.seasonStart.put(key, v);
      }
   }

   // ============================================================================================ sync

   static void ask(ServerPlayer p, byte action, int arg) {
      long now = p.serverLevel().getGameTime();
      Long last = ASKS.put(p.getUUID(), now);
      boolean spam = last != null && now >= last && now - last < 4L;
      switch (action) {
         case JournalNet.A_OPEN -> {
            if (spam) {
               return;
            }
            VIEWING.put(p.getUUID(), now);
            sendData(p);
         }
         case JournalNet.A_CLOSE -> VIEWING.remove(p.getUUID());
         case JournalNet.A_PRINT -> {
            if (!spam) {
               print(p, arg);
            }
         }
         default -> {
         }
      }
   }

   static void sendData(ServerPlayer p) {
      MinecraftServer server = p.server;
      HunterRecord r = ProgressStore.get(server).record(p.getUUID());
      int stage = 0, count = 0, contract = -1, cc = 0, cs = 0, tokens = 0;
      try {
         ExpeditionLedger.Hunter h = ExpeditionLedger.get(p.serverLevel()).hunter(p.getUUID());
         stage = Math.clamp(h.stage, 0, 64);
         count = Math.max(0, h.count);
         contract = h.contract;
         cc = Math.max(0, h.contractCount);
         String st = CampaignProgress.contractStatus(h, p.serverLevel().getGameTime());
         cs = switch (st) {
            case "active" -> 1;
            case "ready" -> 2;
            case "expired" -> 3;
            default -> 0;
         };
         tokens = Math.max(0, HunterLedger.get(p.serverLevel()).hunter(p.getUUID()).tokens());
      } catch (RuntimeException e) {
         LOG.debug("Frontier Hunts journal: expedition ledger unavailable", e);
      }
      List<JournalNet.PageData> pages = JournalPages.data(p);
      r.changed = false;
      JournalNet.send(p, new JournalNet.Data(r.syncCopy(), stage, count, contract, cc, cs, tokens, pages));
   }

   /** Pushes changed records to hunters who have the journal open (at most once a second each). */
   static void flush(MinecraftServer server) {
      if (VIEWING.isEmpty()) {
         return;
      }
      ProgressStore store = ProgressStore.get(server);
      VIEWING.entrySet().removeIf(e -> {
         ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
         if (p == null) {
            return true;
         }
         HunterRecord r = store.find(e.getKey());
         if (r != null && r.changed) {
            sendData(p);
         }
         return false;
      });
   }

   static void forget(UUID id) {
      VIEWING.remove(id);
      ASKS.remove(id);
   }

   static void clear() {
      VIEWING.clear();
      ASKS.clear();
   }

   // ============================================================================================ printing

   private static void print(ServerPlayer p, int index) {
      HunterRecord r = ProgressStore.get(p.server).find(p.getUUID());
      if (r == null || index < 0 || index >= r.summaries.size()) {
         return;
      }
      HunterRecord.Summary s = new ArrayList<>(r.summaries).get(index);
      if (!p.isCreative()) {
         int slot = p.getInventory().findSlotMatchingItem(new ItemStack(Items.PAPER));
         if (slot < 0) {
            p.displayClientMessage(Component.translatable("journal.frontierhunts.print.paper"), true);
            return;
         }
         p.getInventory().removeItem(slot, 1);
      }
      List<Filterable<Component>> pages = new ArrayList<>();
      StringBuilder page = new StringBuilder(s.title()).append("\n\n");
      int lines = 2;
      for (String line : s.lines()) {
         if (lines >= 10) {
            pages.add(Filterable.passThrough(Component.literal(page.toString())));
            page.setLength(0);
            lines = 0;
         }
         page.append(line).append('\n');
         lines += line.length() > 38 ? 2 : 1;
      }
      page.append("\n— ").append(p.getGameProfile().getName());
      pages.add(Filterable.passThrough(Component.literal(page.toString())));
      ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
      String title = HunterRecord.clip(s.title(), 32);
      book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(title), p.getGameProfile().getName(), 0, pages, true));
      if (!p.getInventory().add(book)) {
         p.drop(book, false);
      }
      p.displayClientMessage(Component.translatable("journal.frontierhunts.print.done", title), true);
      p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BOOK_PUT, SoundSource.PLAYERS, 0.8F, 1.0F);
   }
}
