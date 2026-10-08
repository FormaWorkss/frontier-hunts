package com.formaworks.frontierhunts.phone;

import com.formaworks.frontierhunts.journal.Checklist;
import com.formaworks.frontierhunts.journal.HunterRecord;
import com.formaworks.frontierhunts.journal.JournalService;
import com.formaworks.frontierhunts.journal.Rank;
import com.formaworks.frontierhunts.journal.Stat;
import com.formaworks.frontierhunts.licence.LicencePhone;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * [phone] Licence & Tags wallet and the hunter's record for the Trophies app: the licence page's facts
 * ({@code licence.LicencePhone}), the journal rank and its progress, and the headline counters.
 */
public final class PhoneWallet {
   private PhoneWallet() {
   }

   /**
    * The journal's checklist for the Journal app: how many are done, and the six open ones closest to done (by
    * fraction), as lang keys the phone reads in the player's language.
    */
   static void goals(CompoundTag t, HunterRecord r) {
      List<Checklist.Entry> all = Checklist.visible();
      int done = 0;
      List<Checklist.Entry> open = new ArrayList<>();
      for (Checklist.Entry e : all) {
         if (r.done.contains(e.id()) || e.done(r.get(e.counter()))) {
            done++;
         } else {
            open.add(e);
         }
      }
      open.sort(Comparator.comparingDouble((Checklist.Entry e) -> -(double)Math.min(r.get(e.counter()), e.target()) / e.target())
         .thenComparingInt(Checklist.Entry::target));
      ListTag list = new ListTag();
      for (int i = 0; i < Math.min(6, open.size()); i++) {
         Checklist.Entry e = open.get(i);
         CompoundTag c = new CompoundTag();
         c.putString("id", e.id());
         c.putString("key", e.titleKey());
         c.putInt("value", Math.max(0, r.get(e.counter())));
         c.putInt("target", e.target());
         c.putInt("xp", e.xp());
         c.putInt("unit", e.unit() == null ? 0 : e.unit().ordinal());
         list.add(c);
      }
      t.putInt("achDone", done);
      t.putInt("achTotal", all.size());
      t.put("goals", list);
   }

   static void send(ServerPlayer player) {
      CompoundTag t = LicencePhone.wallet(player);
      try {
         HunterRecord r = JournalService.record(player);
         long xp = r.totalXp();
         Rank rank = Rank.of(xp);
         Rank next = rank.next();
         t.putString("rank", rank.title());
         t.putLong("xp", xp);
         t.putLong("rankFrom", rank.xp);
         t.putLong("rankTo", next == null ? rank.xp : next.xp);
         t.putString("nextRank", next == null ? "" : next.title());
         t.putInt("harvests", r.get(Stat.HARVESTS));
         t.putInt("recoveries", r.get(Stat.RECOVERIES));
         t.putInt("clean", r.get(Stat.CLEAN_KILLS));
         t.putInt("longest", r.get(Stat.LONGEST_SHOT));
         t.putInt("bestScore", r.get(Stat.BEST_WT_SCORE));
         t.putInt("species", r.get(Stat.SPECIES_TAKEN));
         t.putInt("photos", r.get(Stat.PHOTOS));
         goals(t, r);
      } catch (RuntimeException e) {
         // the journal is optional here: the wallet still goes out
      }
      PhoneNet.send(player, PhoneNet.K_WALLET, t);
   }
}
