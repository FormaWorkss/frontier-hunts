package com.formaworks.frontierhunts.hunts.client;

import com.formaworks.frontierhunts.expedition.Campaign;
import com.formaworks.frontierhunts.hunts.HuntBook;
import com.formaworks.frontierhunts.hunts.HuntContracts;
import com.formaworks.frontierhunts.journal.client.JournalUi;
import java.util.ArrayList;
import java.util.List;

/** [hunts] Expedition screen text for the rotating species contracts (client). */
public final class HuntContractText {
   private HuntContractText() {
   }

   /** "How it counts" for a species contract: the condition, from the lang fragment. */
   public static String how(Campaign.Mission m) {
      String[] p = m.event().split(":");
      String tag = p.length == 3 ? p[2] : "take";
      String name = JournalUi.tr("entity.frontierhunts." + m.species());
      return JournalUi.tr("hunts.frontierhunts.contract.how." + tag, name, m.amount());
   }

   /** The line above the board: which species contracts are posted this month and that they rotate. */
   public static String boardNote(int month, int half) {
      List<String> names = new ArrayList<>();
      for (int i : HuntContracts.seasonal(month, half)) {
         HuntBook.Contract c = HuntContracts.at(i);
         if (c != null) {
            names.add(JournalUi.tr("entity.frontierhunts." + c.species()));
         }
      }
      String mo = JournalUi.tr("journal.frontierhunts.month." + Math.floorMod(month, 12));
      return names.isEmpty() ? JournalUi.tr("hunts.frontierhunts.contract.board_none", mo)
         : JournalUi.tr("hunts.frontierhunts.contract.board", mo, String.join(", ", names));
   }
}
