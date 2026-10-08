package com.formaworks.frontierhunts.phone;

import com.formaworks.frontierhunts.HunterLedger;
import com.formaworks.frontierhunts.expedition.Campaign;
import com.formaworks.frontierhunts.expedition.CampaignProgress;
import com.formaworks.frontierhunts.expedition.ExpeditionLedger;
import com.formaworks.frontierhunts.expedition.ExpeditionService;
import com.formaworks.frontierhunts.expedition.HuntingCalendar;
import com.formaworks.frontierhunts.hunts.HuntContracts;
import com.formaworks.frontierhunts.progression.Assignment;
import com.formaworks.frontierhunts.progression.AssignmentNetwork;
import com.formaworks.frontierhunts.progression.AssignmentService;
import java.util.List;
import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * [phone] Contracts on the server: Ranger Mara's story missions, the contract board's expedition contracts and the
 * ranger assignments, with the very same rules the journal and the board use (they are called, not copied):
 * {@code CampaignProgress.accept} only takes a contract that is on this half-month's board and only when none is
 * running, a hand-in pays only a contract that is really complete, ranger assignments go through
 * {@code AssignmentService.request} (eligibility, cooldowns, one at a time). The phone only asks.
 */
public final class PhoneContracts {
   public static final int C_MISSION_CLAIM = 0;
   public static final int C_CONTRACT_ACCEPT = 1;
   public static final int C_CONTRACT_CLAIM = 2;
   public static final int C_CONTRACT_ABANDON = 3;
   public static final int C_ASSIGN_ACCEPT = 4;
   public static final int C_ASSIGN_CLAIM = 5;
   public static final int C_ASSIGN_CANCEL = 6;
   private static final String[] MONTHS = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November",
      "December"};

   private PhoneContracts() {
   }

   public static void op(ServerPlayer player, int op, int index, String arg) {
      if (player.hasDisconnected() || !player.isAlive() || player.isSpectator()) {
         return;
      }
      String s = arg == null ? "" : (arg.length() > 128 ? arg.substring(0, 128) : arg);
      ExpeditionLedger ledger = ExpeditionLedger.get(player.serverLevel());
      ExpeditionLedger.Hunter h = ledger.hunter(player.getUUID());
      h.name = player.getScoreboardName();
      long now = player.level().getGameTime();
      boolean ok = false;
      switch (op) {
         case C_MISSION_CLAIM -> ok = CampaignProgress.claimMission(player, s);
         case C_CONTRACT_ACCEPT -> {
            ok = CampaignProgress.accept(h, index, now);
            if (!ok) {
               PhoneServer.toast(player, h.contract >= 0 && !CampaignProgress.contractStatus(h, now).equals("expired") ? "Finish or abandon your contract first"
                  : "That contract is no longer on the board");
            }
         }
         case C_CONTRACT_CLAIM -> {
            ok = CampaignProgress.claimContract(player, s);
            if (!ok) {
               PhoneServer.toast(player, "That contract is not complete yet");
            }
         }
         case C_CONTRACT_ABANDON -> {
            if (h.contract >= 0) {
               h.contract = -1;
               h.contractCount = 0;
               h.contractReadyAt = 0L;
               ok = true;
            }
         }
         case C_ASSIGN_ACCEPT, C_ASSIGN_CLAIM, C_ASSIGN_CANCEL -> {
            int action = op == C_ASSIGN_ACCEPT ? AssignmentNetwork.Request.ACCEPT : (op == C_ASSIGN_CLAIM ? AssignmentNetwork.Request.CLAIM
               : AssignmentNetwork.Request.CANCEL);
            AssignmentService.request(player, new AssignmentNetwork.Request(action, s, 0));
            ok = true;
         }
         default -> {
         }
      }
      if (ok) {
         ledger.setDirty();
         try {
            ExpeditionService.send(player, false);
         } catch (RuntimeException e) {
            // the journal's copy refreshes on its own next time
         }
      }
      send(player);
   }

   static void send(ServerPlayer player) {
      CompoundTag t = new CompoundTag();
      long now = player.level().getGameTime();
      ExpeditionLedger.Hunter h = ExpeditionLedger.get(player.serverLevel()).hunter(player.getUUID());
      t.putInt("tokens", Math.max(0, HunterLedger.get(player.serverLevel()).hunter(player.getUUID()).tokens()));
      // the story
      boolean done = h.stage >= Campaign.MISSIONS.size();
      t.putBoolean("storyDone", done);
      t.putString("storyId", Campaign.id(h.stage));
      if (!done) {
         Campaign.Mission m = Campaign.MISSIONS.get(Math.max(0, h.stage));
         t.putString("storyTitle", m.title());
         t.putString("storyText", m.story());
         t.putInt("storyCount", Math.min(h.count, m.amount()));
         t.putInt("storyAmount", m.amount());
         t.putInt("storyTokens", m.tokens());
      }
      t.putInt("storyChapter", Campaign.chapter(h.stage));
      // the running contract
      t.putInt("contract", h.contract);
      t.putString("contractStatus", CampaignProgress.contractStatus(h, now));
      if (h.contract >= 0 && h.contract < Campaign.CONTRACTS.size()) {
         Campaign.Mission m = Campaign.CONTRACTS.get(h.contract);
         t.putString("contractTitle", m.title());
         t.putString("contractText", m.story());
         t.putInt("contractCount", Math.min(h.contractCount, m.amount()));
         t.putInt("contractAmount", m.amount());
         t.putInt("contractTokens", m.tokens());
         t.putLong("contractSeconds", Math.max(0L, (h.deadline - now) / 20L));
      }
      t.putString("contractSerial", Long.toString(h.contractSerial));
      // the board for this half of the month
      HuntingCalendar.Date d = HuntingCalendar.date(player.serverLevel());
      int half = HuntContracts.half(d.day(), d.daysPerMonth());
      ListTag board = new ListTag();
      List<Integer> posted = HuntContracts.board(d.month(), half);
      for (int i : posted) {
         if (i < 0 || i >= Campaign.CONTRACTS.size() || board.size() >= 24) {
            continue;
         }
         Campaign.Mission m = Campaign.CONTRACTS.get(i);
         CompoundTag o = new CompoundTag();
         o.putInt("index", i);
         o.putString("title", m.title());
         o.putString("text", m.story());
         o.putInt("tokens", m.tokens());
         o.putInt("amount", m.amount());
         o.putString("species", species(m.species()));
         board.add(o);
      }
      t.put("board", board);
      t.putString("boardNote", "Posted for the " + (half == 0 ? "first" : "second") + " half of " + MONTHS[Math.floorMod(d.month(), 12)]
         + ". New species contracts go up mid-month.");
      // ranger assignments
      AssignmentNetwork.Snapshot snap = AssignmentService.snapshot(player, false);
      t.putBoolean("eligible", snap.eligible());
      t.putInt("assignState", snap.state());
      t.putString("assignId", snap.id());
      Assignment terms = snap.terms();
      if (terms != null) {
         t.putString("assignTitle", terms.title());
         t.putString("assignText", terms.instruction());
         t.putInt("assignCount", snap.count());
         t.putInt("assignTarget", terms.target());
         t.putInt("assignTokens", terms.tokens());
         t.putInt("assignXp", terms.experience());
         t.putLong("assignTicks", snap.remaining());
      }
      ListTag offers = new ListTag();
      for (AssignmentNetwork.Offer o : snap.offers()) {
         Assignment a = o.terms();
         CompoundTag c = new CompoundTag();
         c.putString("id", o.id());
         c.putString("title", a.title());
         c.putString("text", a.instruction());
         c.putInt("tokens", a.tokens());
         c.putInt("xp", a.experience());
         c.putInt("target", a.target());
         c.putInt("cooldown", o.cooldown());
         c.putString("kind", !a.hunt().isEmpty() ? "Hunt" : (a.duration() > 0 ? "Timed" : switch (a.objective()) {
            case TRACK -> "Tracking";
            case DELIVERY -> "Delivery";
            default -> "Field";
         }));
         c.putInt("duration", a.duration());
         offers.add(c);
      }
      t.put("offers", offers);
      PhoneNet.send(player, PhoneNet.K_CONTRACTS, t);
   }

   private static String species(String id) {
      if (id == null || id.isEmpty() || id.equals("*") || id.equals("whitetail")) {
         return "";
      }
      String s = id.replace('_', ' ');
      return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
   }
}
